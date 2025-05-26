package aqua.blatt7.client;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt4.common.msgtypes.NeighborUpdate;
import aqua.blatt4.common.msgtypes.SnapshotMarker;
import aqua.blatt5.common.msgtypes.*;
import aqua.blatt7.common.msgtypes.RegisterResponse;
import aqua.blatt7.crypto.SecureEndpoint;
import messaging.Endpoint;
import messaging.Message;

import java.net.InetSocketAddress;

/**
 * Handles sending and receiving messages between client and broker or peers.
 */
public class ClientCommunicator {
    private final SecureEndpoint endpoint;

    public ClientCommunicator() {
        endpoint = new SecureEndpoint();
    }

    public class ClientForwarder {
        private final InetSocketAddress broker;
        private final TankModel tankModel;

        private ClientForwarder(TankModel tankModel) {
            this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
            this.tankModel = tankModel;
        }

        public void sendLocationRequest(InetSocketAddress neighbor, String fishId) {
            endpoint.send(neighbor, new LocationRequest(fishId));
        }

        public void register() {
            endpoint.send(broker, new RegisterRequest());
        }

        public void deregister(String id) {
            endpoint.send(broker, new DeregisterRequest(id));
        }

        public void handOff(FishModel fish) {
            InetSocketAddress target = fish.getDirection() == Direction.LEFT ?
                    tankModel.getLeftNeighbor() : tankModel.getRightNeighbor();
            endpoint.send(target, new HandoffRequest(fish));
        }

        /**
         * Send snapshot marker messages to both neighbors.
         */
        public void sendSnapshotMarker() {
            if (tankModel.getLeftNeighbor() != null) {
                endpoint.send(tankModel.getLeftNeighbor(), new SnapshotMarker());
            }
            if (tankModel.getRightNeighbor() != null) {
                endpoint.send(tankModel.getRightNeighbor(), new SnapshotMarker());
            }
        }

        /**
         * Forward the snapshot token carrying the sum so far to the left neighbor.
         */
        public void sendSnapshotToken(SnapshotTokenMessage token) {
            if (tankModel.getLeftNeighbor() != null) {
                // normaler Fall: Token an linken Nachbarn
                endpoint.send(tankModel.getLeftNeighbor(), token);
            } else {
                // Ein‑Knoten‑Ring: Token direkt selbst verarbeiten
                tankModel.onSnapshotToken(token);
            }
        }

        /**
         * Forward the ring TokenMessage to the left neighbor via the same Endpoint.
         */
        public void sendRingToken() {
            if (tankModel.getLeftNeighbor() != null) {
                endpoint.send(tankModel.getLeftNeighbor(), new TokenMessage());
            }
        }

        public void sendNameResolutionRequest(String tankId, String fishId) {
            endpoint.send(broker, new NameResolutionRequest(tankId, fishId));
        }

        public void sendLocationUpdate(InetSocketAddress home, String fishId) {
            endpoint.send(home, new LocationUpdate(fishId, tankModel.getMyAddress()));
        }
    }

    public class ClientReceiver extends Thread {
        private final TankModel tankModel;

        private ClientReceiver(TankModel tankModel) {
            this.tankModel = tankModel;
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                Message msg = endpoint.blockingReceive();

                if (msg.getPayload() instanceof RegisterResponse) {
                    RegisterResponse response = (RegisterResponse) msg.getPayload();
                    tankModel.setMyAddress(msg.getSender());
                    tankModel.onRegistration(response.getId(), response.getLeaseDuration());
                }
                else if (msg.getPayload() instanceof HandoffRequest) {
                    HandoffRequest ho = (HandoffRequest) msg.getPayload();
                    // Pass sender so we can record channel state
                    tankModel.receiveFish(msg.getSender(), ho.getFish());
                }
                else if (msg.getPayload() instanceof NeighborUpdate) {
                    NeighborUpdate update = (NeighborUpdate) msg.getPayload();
                    if (update.getDirection() == Direction.LEFT)
                        tankModel.setLeftNeighbor(update.getNeighbor());
                    else
                        tankModel.setRightNeighbor(update.getNeighbor());
                }
                else if (msg.getPayload() instanceof TokenMessage) {
                    tankModel.receiveToken();
                }
                else if (msg.getPayload() instanceof SnapshotMarker) {
                    tankModel.onSnapshotMarker(msg.getSender());
                }
                else if (msg.getPayload() instanceof SnapshotTokenMessage) {
                    tankModel.onSnapshotToken((SnapshotTokenMessage) msg.getPayload());
                }
                else if (msg.getPayload() instanceof LocationRequest) {
                    String fishId = ((LocationRequest) msg.getPayload()).getFishId();
                    tankModel.locateFishGlobally(fishId);
                }
                else if (msg.getPayload() instanceof NameResolutionResponse) {
                    NameResolutionResponse resp = (NameResolutionResponse) msg.getPayload();
                    tankModel.handleNameResolutionResponse(resp.getRequestId(), resp.getAddress());
                }
                else if (msg.getPayload() instanceof LocationUpdate) {
                    LocationUpdate update = (LocationUpdate) msg.getPayload();
                    tankModel.receiveLocationUpdate(update.getFishId(), update.getLocation());
                }
            }
            System.out.println("Receiver stopped.");
        }
    }

    public ClientForwarder newClientForwarder(TankModel tankModel) {
        return new ClientForwarder(tankModel);
    }
    public ClientReceiver newClientReceiver(TankModel tankModel) {
        return new ClientReceiver(tankModel);
    }
}