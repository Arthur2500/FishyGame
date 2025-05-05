package aqua.blatt5.broker;

import aqua.blatt1.broker.ClientCollection;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;
import aqua.blatt5.common.msgtypes.NameResolutionRequest;
import aqua.blatt5.common.msgtypes.NameResolutionResponse;
import aqua.blatt5.common.msgtypes.NeighborUpdate;
import aqua.blatt5.common.msgtypes.TokenMessage;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.net.InetSocketAddress;
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

    public Broker() {
        this.endpoint = new Endpoint(PORT);
        this.clients = new ClientCollection<>();
        this.clientCounter = new AtomicInteger(1);
        this.executor = Executors.newFixedThreadPool(10);
        this.lock = new ReentrantReadWriteLock();
        this.stopRequested = false;
    }

    public static void main(String[] args) {
        new Broker().broker();
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
                String id = "tank" + clientCounter.getAndIncrement();

                lock.writeLock().lock();
                try {
                    clients.add(id, message.getSender());
                    endpoint.send(message.getSender(), new RegisterResponse(id));

                    int newIndex = clients.indexOf(id);
                    InetSocketAddress left = clients.getLeftNeighorOf(newIndex);
                    InetSocketAddress right = clients.getRightNeighorOf(newIndex);

                    // Dem neuen Client seine Nachbarn schicken
                    endpoint.send(message.getSender(), new NeighborUpdate(left, right));

                    // Den Nachbarn ihre neuen Nachbarn schicken
                    int leftIndex = clients.indexOf(left);
                    int rightIndex = clients.indexOf(right);

                    endpoint.send(left, new NeighborUpdate(
                            clients.getLeftNeighorOf(leftIndex), message.getSender()));

                    endpoint.send(right, new NeighborUpdate(
                            message.getSender(), clients.getRightNeighorOf(rightIndex)));

                    // Token nur beim allerersten vergeben
                    if (clients.size() == 1) {
                        endpoint.send(message.getSender(), new TokenMessage());
                    }
                } finally {
                    lock.writeLock().unlock();
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

                    // Nachbarn neu verbinden
                    int leftIndex = clients.indexOf(left);
                    int rightIndex = clients.indexOf(right);

                    endpoint.send(left, new NeighborUpdate(
                            clients.getLeftNeighorOf(leftIndex), right));

                    endpoint.send(right, new NeighborUpdate(
                            left, clients.getRightNeighorOf(rightIndex)));
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