package aqua.blatt3.client;

import java.net.InetSocketAddress;

import aqua.blatt1.common.Direction;
import aqua.blatt3.common.msgtypes.NeighborUpdate;
import aqua.blatt3.common.msgtypes.TokenMessage;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;

public class ClientCommunicator {
    private final Endpoint endpoint;

    public ClientCommunicator() {
        endpoint = new Endpoint();
    }

    public class ClientForwarder {
        private final InetSocketAddress broker;
        private final TankModel tankModel;

        private ClientForwarder(TankModel tankModel) {
            this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
            this.tankModel = tankModel;
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

                if (msg.getPayload() instanceof RegisterResponse)
                    tankModel.onRegistration(((RegisterResponse) msg.getPayload()).getId());

                if (msg.getPayload() instanceof HandoffRequest)
                    tankModel.receiveFish(((HandoffRequest) msg.getPayload()).getFish());

                if (msg.getPayload() instanceof NeighborUpdate) {
                    NeighborUpdate update = (NeighborUpdate) msg.getPayload();
                    if (update.getDirection() == Direction.LEFT) {
                        tankModel.setLeftNeighbor(update.getNeighbor());
                    } else {
                        tankModel.setRightNeighbor(update.getNeighbor());
                    }
                }

                if (msg.getPayload() instanceof TokenMessage) {
                    tankModel.receiveToken();
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
