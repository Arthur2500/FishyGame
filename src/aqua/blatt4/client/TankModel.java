// TankModel.java – endgültige Snapshot‑Implementierung

package aqua.blatt4.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.swing.*;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt4.common.msgtypes.SnapshotMarker;
import aqua.blatt4.common.msgtypes.SnapshotTokenMessage;
import aqua.blatt4.common.msgtypes.TokenMessage;

public class TankModel extends Observable implements Iterable<FishModel> {

    /* ---------- statische Konstanten ---------- */
    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    private static final int MAX_FISHIES = 5;
    private static final Random RAND = new Random();

    /* ---------- Enum für Aufzeichnungsmodus ---------- */
    private enum RecordState { IDLE, LEFT, RIGHT, BOTH }

    /* ---------- Aquarium‑Daten ---------- */
    private final Set<FishModel> fishies;
    private final ClientCommunicator.ClientForwarder forwarder;

    private volatile String id;
    private int fishCounter = 0;

    private InetSocketAddress leftNeighbor;
    private InetSocketAddress rightNeighbor;

    /* ---------- Token (Ring‑Synchronisation) ---------- */
    private boolean hasToken;
    private Timer tokenTimer;

    /* ---------- Snapshot‑Zustand ---------- */
    private boolean snapshotActive       = false;   // Marker schon gesehen / selbst initiiert?
    private boolean snapshotFinished     = false;   // lokaler Snapshot abgeschlossen?
    private boolean snapshotAdded        = false;   // lokale Zahl schon in Token addiert?

    private boolean isSnapshotInitiator  = false;   // hat diesen Durchlauf gestartet?

    private RecordState recordState      = RecordState.IDLE;

    private int  localSnapshot           = 0;       // Fische + Kanalpuffer
    private final List<FishModel> leftBuffer  = new ArrayList<>();
    private final List<FishModel> rightBuffer = new ArrayList<>();

    private SnapshotTokenMessage storedToken = null; // Token geparkt, bis Snapshot fertig

    /* ---------- Konstruktor ---------- */
    public TankModel(ClientCommunicator communicator) {
        this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
        this.forwarder = communicator.newClientForwarder(this);
    }

    /* -------------------------------------------------------------------- */
    /*  Registrierung & normale Aquarium‑Funktionen                         */
    /* -------------------------------------------------------------------- */

    public void onRegistration(String id) {
        this.id = id;
        newFish(WIDTH - FishModel.getXSize(), RAND.nextInt(HEIGHT - FishModel.getYSize()));
    }

    public synchronized void newFish(int x, int y) {
        if (fishies.size() < MAX_FISHIES) {
            x = Math.min(x, WIDTH - FishModel.getXSize() - 1);
            y = Math.min(y, HEIGHT - FishModel.getYSize());
            fishies.add(new FishModel("fish" + (++fishCounter) + "@" + id,
                    x, y,
                    RAND.nextBoolean() ? Direction.LEFT : Direction.RIGHT));
        }
    }

    /* -------------------------------------------------------------------- */
    /*  SNAPSHOT – Initiator                                                */
    /* -------------------------------------------------------------------- */

    public synchronized void initiateSnapshot() {
        if (snapshotActive) {
            System.out.println("[Snapshot] Läuft schon – ignoriert");
            return;
        }

        /* lokalen Zustand sichern */
        localSnapshot   = countStableFish();
        snapshotActive  = true;
        snapshotFinished= false;
        snapshotAdded   = false;
        recordState     = RecordState.BOTH;
        isSnapshotInitiator = true;

        leftBuffer.clear();
        rightBuffer.clear();

        /* Marker in beide Ausgänge schicken */
        forwarder.sendSnapshotMarker();

        if (leftNeighbor != null && leftNeighbor.equals(rightNeighbor)) {
            System.out.println("[Snapshot] " + id + " – Nur ein Nachbar → Marker doppelt lokal verarbeiten");
            onSnapshotMarker(leftNeighbor);
            onSnapshotMarker(rightNeighbor);
        }

        System.out.println("[Snapshot] *** Initiator " + id + " hat gestartet – lokale Fische: "
                + localSnapshot + " ***");
    }

    /* -------------------------------------------------------------------- */
    /*  Marker‑Empfang / Aufzeichnungsmodus                                 */
    /* -------------------------------------------------------------------- */

    public synchronized void onSnapshotMarker(InetSocketAddress sender) {
        boolean fromLeft = sender.equals(leftNeighbor);

        if (leftNeighbor != null && leftNeighbor.equals(rightNeighbor)) {
            if (!snapshotFinished) {
                finishLocalSnapshot();
            }
            return;
        }

        /* Erster Marker überhaupt? */
        if (!snapshotActive) {
            snapshotActive   = true;
            localSnapshot    = countStableFish();
            snapshotFinished = false;
            snapshotAdded    = false;
            recordState      = fromLeft ? RecordState.RIGHT : RecordState.LEFT;
            isSnapshotInitiator = false;

            forwarder.sendSnapshotMarker();
            System.out.println("[Snapshot] " + id + " – erster Marker von "
                    + (fromLeft ? "links" : "rechts")
                    + ", lokale Fische: " + localSnapshot);
        } else {
            /* zweiter Marker */
            switch (recordState) {
                case BOTH:
                    // erster Marker angekommen → gegenüberliegende Seite noch offen
                    recordState = fromLeft ? RecordState.RIGHT : RecordState.LEFT;
                    break;

                case LEFT:
                    // OFFEN ist nur noch die linke Seite; kommt Marker von links → fertig
                    if (fromLeft) recordState = RecordState.IDLE;
                    break;

                case RIGHT:
                    // OFFEN ist nur noch die rechte Seite; kommt Marker von rechts → fertig
                    if (!fromLeft) recordState = RecordState.IDLE;
                    break;

                default: // IDLE: ignorieren
                    break;
            }
        }

        if (recordState == RecordState.IDLE && !snapshotFinished) {
            finishLocalSnapshot();
        }
    }

    /* -------------------------------------------------------------------- */
    /*  Fischempfang – evtl. in Kanalpuffer legen                           */
    /* -------------------------------------------------------------------- */

    public synchronized void receiveFish(InetSocketAddress sender, FishModel fish) {
        boolean fromLeft = sender.equals(leftNeighbor);

        /* Falls Kanal noch aufgezeichnet wird → puffern! */
        if (snapshotActive) {
            if ( fromLeft && (recordState == RecordState.LEFT || recordState == RecordState.BOTH)) {
                leftBuffer.add(fish);
                return;
            }
            if (!fromLeft && (recordState == RecordState.RIGHT || recordState == RecordState.BOTH)) {
                rightBuffer.add(fish);
                return;
            }
        }

        fish.setToStart();
        fishies.add(fish);
    }

    /* -------------------------------------------------------------------- */
    /*  Token‑Empfang                                                      */
    /* -------------------------------------------------------------------- */

    public synchronized void onSnapshotToken(SnapshotTokenMessage token) {

        /* Snapshot schon fertig? */
        if (snapshotFinished) {
            handleToken(token);
        } else {
            /* Snapshot noch nicht fertig – Token zwischenparken */
            storedToken = token;
        }
    }

    /* -------------------------------------------------------------------- */
    /*  Lokaler Snapshot fertig                                             */
    /* -------------------------------------------------------------------- */

    private synchronized void finishLocalSnapshot() {
        snapshotFinished = true;
        /* Kanalpuffer zum lokalen Zustand addieren */
        localSnapshot += leftBuffer.size() + rightBuffer.size();
        leftBuffer.clear();
        rightBuffer.clear();

        System.out.println("[Snapshot] " + id + " – lokaler Snapshot komplett ("
                + localSnapshot + " Fische)");

        /* Initiator erzeugt jetzt erst den Token */
        if (isSnapshotInitiator) {
            SnapshotTokenMessage token =
                    new SnapshotTokenMessage(id, localSnapshot);
            snapshotAdded = true;
            forwarder.sendSnapshotToken(token);
            System.out.println("[Snapshot] Initiator sendet Token mit Summe "
                    + token.getSum());
        }

        /* Falls Token bereits wartet, jetzt weitergeben */
        if (storedToken != null) {
            handleToken(storedToken);
            storedToken = null;
        }
    }

    /* -------------------------------------------------------------------- */
    /*  Token‑Weiterverarbeitung (einmalige Addition)                      */
    /* -------------------------------------------------------------------- */

    private synchronized void handleToken(SnapshotTokenMessage token) {
        SnapshotTokenMessage resultToken = token;

        if (!snapshotAdded) {
            int newSum = token.getSum() + localSnapshot;
            resultToken = new SnapshotTokenMessage(token.getInitiatorId(), newSum);
            snapshotAdded = true;
            System.out.println("[Snapshot] " + id + " addiert ⇒ Zwischensumme " + newSum);
        }

        if (resultToken.getInitiatorId().equals(id)) {
            int finalSum = resultToken.getSum();
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                        "🐟 Global Snapshot abgeschlossen!\n\nGesamtpopulation: " + finalSum,
                        "Global Snapshot", JOptionPane.INFORMATION_MESSAGE);
            });
        } else {
            forwarder.sendSnapshotToken(resultToken);
        }

        resetSnapshotState();
    }

    /* -------------------------------------------------------------------- */
    /*  Hilfsroutinen                                                       */
    /* -------------------------------------------------------------------- */

    private int countStableFish() {
        /* in unserem Aqualife verschwinden Fische sofort nach Handoff – daher einfach size() */
        return fishies.size();
    }

    private void resetSnapshotState() {
        snapshotActive      = false;
        snapshotFinished    = false;
        snapshotAdded       = false;
        isSnapshotInitiator = false;
        recordState         = RecordState.IDLE;
        storedToken         = null;
        localSnapshot       = 0;
        leftBuffer.clear();
        rightBuffer.clear();
        System.out.println("[Snapshot] " + id + " – Zustand zurückgesetzt");
    }

    /* -------------------------------------------------------------------- */
    /*  Ring‑Token (Animation)                                             */
    /* -------------------------------------------------------------------- */

    public synchronized void receiveToken() {
        hasToken = true;
        setChanged(); notifyObservers();

        tokenTimer = new Timer();
        tokenTimer.schedule(new TimerTask() {
            public void run() {
                hasToken = false;
                forwarder.sendRingToken();
                setChanged(); notifyObservers();
            }
        }, 2000);
    }

    /* -------------------------------------------------------------------- */
    /*  Getter / Boilerplate                                                */
    /* -------------------------------------------------------------------- */

    public boolean hasToken() { return hasToken; }

    public void setLeftNeighbor(InetSocketAddress neighbor) {
        this.leftNeighbor = neighbor;
    }

    public void setRightNeighbor(InetSocketAddress neighbor) {
        this.rightNeighbor = neighbor;
    }

    public InetSocketAddress getLeftNeighbor()  { return leftNeighbor; }
    public InetSocketAddress getRightNeighbor() { return rightNeighbor; }
    public String getId()                       { return id; }

    public synchronized int getFishCounter()    { return fishCounter; }

    @Override
    public synchronized Iterator<FishModel> iterator() { return fishies.iterator(); }

    /* -------------------------------------------------------------------- */
    /*  Aquarium‑Update‑Schleife                                            */
    /* -------------------------------------------------------------------- */

    private synchronized void updateFishies() {
        for (Iterator<FishModel> it = fishies.iterator(); it.hasNext();) {
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
            if (fish.disappears()) it.remove();
        }
    }

    private synchronized void update() {
        updateFishies();
        setChanged(); notifyObservers();
    }

    public void run() {
        forwarder.register();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                update();
                TimeUnit.MILLISECONDS.sleep(10);
            }
        } catch (InterruptedException ignored) {}
    }

    public synchronized void finish() {
        forwarder.deregister(id);
    }
}