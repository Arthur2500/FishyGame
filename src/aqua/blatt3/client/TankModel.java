// === TankModel.java angepasst für Blatt 3 ===
package aqua.blatt3.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt3.common.msgtypes.TokenMessage;
import messaging.Endpoint;

public class TankModel extends Observable implements Iterable<FishModel> {
    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    private static final int MAX_FISHIES = 5;
    private static final Random rand = new Random();

    private final Set<FishModel> fishies;
    private final ClientCommunicator.ClientForwarder forwarder;
    private final Endpoint endpoint;

    private volatile String id;
    private int fishCounter = 0;

    private InetSocketAddress leftNeighbor;
    private InetSocketAddress rightNeighbor;

    private boolean hasToken;
    private Timer tokenTimer;

    public TankModel(ClientCommunicator communicator) {
        this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
        this.endpoint = new Endpoint();
        this.forwarder = communicator.newClientForwarder(this);
    }

    public void onRegistration(String id) {
        this.id = id;
        newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
    }

    public synchronized void newFish(int x, int y) {
        if (fishies.size() < MAX_FISHIES) {
            x = x > WIDTH - FishModel.getXSize() - 1 ? WIDTH - FishModel.getXSize() - 1 : x;
            y = y > HEIGHT - FishModel.getYSize() ? HEIGHT - FishModel.getYSize() : y;

            FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
                    rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

            fishies.add(fish);
        }
    }

    public synchronized void receiveFish(FishModel fish) {
        fish.setToStart();
        fishies.add(fish);
    }

    public synchronized void receiveToken() {
        this.hasToken = true;
        setChanged(); notifyObservers();

        tokenTimer = new Timer();
        tokenTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                hasToken = false;
                sendToken();
                setChanged(); notifyObservers();
            }
        }, 2000);
    }

    private synchronized void sendToken() {
        if (leftNeighbor != null) {
            endpoint.send(leftNeighbor, new TokenMessage());
        }
    }

    public boolean hasToken() {
        return hasToken;
    }

    public InetSocketAddress getLeftNeighbor() {
        return leftNeighbor;
    }

    public InetSocketAddress getRightNeighbor() {
        return rightNeighbor;
    }

    public void setLeftNeighbor(InetSocketAddress left) {
        this.leftNeighbor = left;
    }

    public void setRightNeighbor(InetSocketAddress right) {
        this.rightNeighbor = right;
    }

    public String getId() {
        return id;
    }

    public synchronized int getFishCounter() {
        return fishCounter;
    }

    @Override
    public synchronized Iterator<FishModel> iterator() {
        return fishies.iterator();
    }

    private synchronized void updateFishies() {
        for (Iterator<FishModel> it = iterator(); it.hasNext();) {
            FishModel fish = it.next();
            fish.update();

            if (fish.hitsEdge()) {
                if (hasToken) {
                    forwarder.handOff(fish);
                    it.remove();
                } else {
                    fish.reverse();
                }
            }

            if (fish.disappears())
                it.remove();
        }
    }

    private synchronized void update() {
        updateFishies();
        setChanged();
        notifyObservers();
    }

    public void run() {
        forwarder.register();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                update();
                TimeUnit.MILLISECONDS.sleep(10);
            }
        } catch (InterruptedException ignored) {
        }
    }

    public synchronized void finish() {
        forwarder.deregister(id);
    }
}