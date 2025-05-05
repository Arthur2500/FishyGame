package aqua.blatt2.broker;

import aqua.blatt1.broker.ClientCollection;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;

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
                } finally {
                    lock.writeLock().unlock();
                }
                endpoint.send(message.getSender(), new RegisterResponse(id));

            } else if (payload instanceof DeregisterRequest) {
                lock.writeLock().lock();
                try {
                    clients.remove(clients.indexOf(message.getSender()));
                } finally {
                    lock.writeLock().unlock();
                }

            } else if (payload instanceof HandoffRequest) {
                FishModel fish = ((HandoffRequest) message.getPayload()).getFish();
                int index;
                InetSocketAddress neighbor;

                lock.readLock().lock();
                try {
                    index = clients.indexOf(message.getSender());
                    if (fish.getDirection() == Direction.LEFT) {
                        neighbor = clients.getLeftNeighorOf(index);
                    } else {
                        neighbor = clients.getRightNeighorOf(index);
                    }
                } finally {
                    lock.readLock().unlock();
                }

                endpoint.send(neighbor, new HandoffRequest(fish));
            } else if (payload instanceof PoisonPill) {
                System.out.println("Poison Pill empfangen – Broker fährt herunter.");
                stopBroker();
            }
        }
    }
}