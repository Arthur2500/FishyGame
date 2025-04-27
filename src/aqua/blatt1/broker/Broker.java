package aqua.blatt1.broker;

import aqua.blatt1.broker.*;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;
import messaging.Endpoint;
import messaging.Message;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class Broker {
    private Endpoint endpoint;
    private ClientCollection<InetSocketAddress> clients;
    private int clientCounter = 0;

    public static void main(String[] args) {
        new Broker().run();
    }

    public Broker() {
        this.endpoint = new Endpoint(4711);
        this.clients = new ClientCollection<InetSocketAddress>();
    }

    public void run() {
        broker();
    }

    private void broker() {
        while (true) {
            Message message = endpoint.blockingReceive();
            Serializable decodedMessage = message.getPayload();
            if (decodedMessage instanceof DeregisterRequest) {
                deregisterRequestHandler(message);
            } else if (decodedMessage instanceof HandoffRequest) {
                hanoffFish(message);
            } else if (decodedMessage instanceof RegisterRequest) {
                registerRequestHandler(message);
            } else if (decodedMessage instanceof RegisterResponse) {
                registerResponseHandler(message);
            }
        }
    }

    private void deregisterRequestHandler(Message message) {
        clients.remove(clients.indexOf(message.getSender()));
    }

    private void hanoffFish(Message message) {
        FishModel fish = ((HandoffRequest) message.getPayload()).getFish();
        int index = clients.indexOf(message.getSender());
        InetSocketAddress neighbor = null;
        if (fish.getDirection() == Direction.LEFT) {
            neighbor = clients.getLeftNeighorOf(index);
        } else {
            neighbor = clients.getRightNeighorOf(index);
        }
        endpoint.send(neighbor, new HandoffRequest(fish));
    }

    private void registerRequestHandler(Message message) {
        String name = "tank" + clientCounter;
        clientCounter++;
        clients.add(name, message.getSender());
        endpoint.send(message.getSender(), new RegisterResponse(name));
    }

    private void registerResponseHandler(Message message) {
    }
}