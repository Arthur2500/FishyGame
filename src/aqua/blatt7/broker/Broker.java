package aqua.blatt7.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt4.common.msgtypes.NeighborUpdate;
import aqua.blatt5.common.msgtypes.NameResolutionRequest;
import aqua.blatt5.common.msgtypes.NameResolutionResponse;
import aqua.blatt5.common.msgtypes.TokenMessage;
import aqua.blatt7.common.msgtypes.RegisterResponse;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker {
    private static final int PORT = 4711;
    private final Endpoint endpoint;
    private final ClientCollection<InetSocketAddress> clients;
    private final AtomicInteger clientCounter;
    private final ExecutorService executor;
    private final ReadWriteLock lock;
    private volatile boolean stopRequested;
    private static final int LEASE_DURATION_MS = 10000; // z.B. 10 Sekunden

    public Broker() {
        this.endpoint = new Endpoint(PORT);
        this.clients = new ClientCollection<>();
        this.clientCounter = new AtomicInteger(1);
        this.executor = Executors.newFixedThreadPool(10);
        this.lock = new ReentrantReadWriteLock();
        this.stopRequested = false;
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                removeExpiredClients();
            }
        }, 0, 2000); // Alle 2 Sekunden prüfen
    }

    public static void main(String[] args) {
        new Broker().broker();
    }

    private String findIdByAddress(InetSocketAddress addr) {
        for (String id : clients.getIds()) {
            if (clients.get(id).equals(addr)) return id;
        }
        return null;
    }

    private void removeExpiredClients() {
        lock.writeLock().lock();
        try {
            long now = System.currentTimeMillis();
            for (String id : new HashSet<>(clients.getIds())) {
                if (now - clients.getTimestamp(id) > LEASE_DURATION_MS) {
                    int index = clients.indexOf(id);
                    InetSocketAddress left = clients.getLeftNeighorOf(index);
                    InetSocketAddress right = clients.getRightNeighorOf(index);
                    clients.removeById(id);
                    endpoint.send(left, new NeighborUpdate(Direction.RIGHT, right));
                    endpoint.send(right, new NeighborUpdate(Direction.LEFT, left));
                    System.out.println("[Lease] Client " + id + " entfernt (Lease abgelaufen)");
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void broker() {
        // Starte GUI-Thread für Beenden
        new Thread(() -> {
            JOptionPane.showMessageDialog(null, "OK beendet den Broker (manuell).");
            stopBroker();
        }).start();

        while (!stopRequested) {
            try {
                Message message = endpoint.blockingReceive();
                if (message != null) {
                    executor.execute(new BrokerTask(message));
                }
            } catch (Exception e) {
                if (!stopRequested) e.printStackTrace();
            }
        }
        System.out.println("Broker beendet.");
    }

    private void stopBroker() {
        stopRequested = true;
        executor.shutdown();
    }

    private class BrokerTask implements Runnable {
        private final Message message;

        BrokerTask(Message message) {
            this.message = message;
        }

        @Override
        public void run() {
            Object payload = message.getPayload();

            if (payload instanceof RegisterRequest) {
                InetSocketAddress sender = message.getSender();
                String id;
                boolean isNewClient = clients.indexOf(sender) == -1;

                if (isNewClient) {
                    id = "tank" + clientCounter.getAndIncrement();
                    clients.add(id, sender); // auch Zeitstempel wird hier aktualisiert
                } else {
                    // Client existiert bereits, nur Zeitstempel aktualisieren
                    id = findIdByAddress(sender);
                    clients.updateTimestamp(id);
                }

                // Sende Antwort mit ID und Lease-Dauer
                endpoint.send(sender, new RegisterResponse(id, LEASE_DURATION_MS));

                if (isNewClient) {
                    int newIndex = clients.indexOf(id);
                    InetSocketAddress left, right;

                    if (clients.size() == 1) {
                        // Nur ein Client im Ring → er selbst ist linker und rechter Nachbar
                        left = right = sender;
                    } else {
                        left = clients.getLeftNeighorOf(newIndex);
                        right = clients.getRightNeighorOf(newIndex);
                    }

                    // Nachbarn setzen
                    endpoint.send(sender, new NeighborUpdate(Direction.LEFT, left));
                    endpoint.send(sender, new NeighborUpdate(Direction.RIGHT, right));
                    endpoint.send(left, new NeighborUpdate(Direction.RIGHT, sender));
                    endpoint.send(right, new NeighborUpdate(Direction.LEFT, sender));

                    if (clients.size() == 1) {
                        endpoint.send(sender, new TokenMessage());
                    }

                    System.out.println("[Broker] Neuer Client registriert: " + id);
                } else {
                    System.out.println("[Broker] Client " + id + " re-registriert");
                }
            }

// In DeregisterRequest Verarbeitung:
            else if (payload instanceof DeregisterRequest) {
                lock.writeLock().lock();
                try {
                    int index = clients.indexOf(message.getSender());

                    InetSocketAddress left = clients.getLeftNeighorOf(index);
                    InetSocketAddress right = clients.getRightNeighorOf(index);

                    clients.remove(index);

                    endpoint.send(left, new NeighborUpdate(Direction.RIGHT, right));
                    endpoint.send(right, new NeighborUpdate(Direction.LEFT, left));
                } finally {
                    lock.writeLock().unlock();
                }
            }
            else if (payload instanceof NameResolutionRequest) {
                NameResolutionRequest req = (NameResolutionRequest) payload;
                InetSocketAddress address = clients.get(req.getTankId());
                endpoint.send(message.getSender(), new NameResolutionResponse(req.getRequestId(), address));
            }
        }
    }
}