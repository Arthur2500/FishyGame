package aqua.blatt7.crypto;

import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt4.common.msgtypes.SnapshotMarker;
import aqua.blatt7.common.msgtypes.*;
import aqua.blatt4.common.msgtypes.NeighborUpdate;
import messaging.Endpoint;
import messaging.Message;

import javax.crypto.Cipher;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A drop-in replacement for {@link messaging.Endpoint} that transparently secures all
 * application-level traffic with RSA encryption. The first time two peers talk to
 * each other their public keys are exchanged automatically via {@link KeyExchangeMessage}s.
 * These internal handshake messages are absorbed within {@code SecureEndpoint} and will never
 * reach the application layer.
 */
public class SecureEndpoint {

    /* Wrapped (plain) endpoint doing the actual UDP/TCP work */
    private final Endpoint internal;

    /* My asymmetric key pair */
    private final PrivateKey privateKey;
    private final PublicKey  publicKey;

    /* Books which remote address owns which public key */
    private final Map<InetSocketAddress, PublicKey> remoteKeys = new ConcurrentHashMap<>();

    /* Messages that should be delivered as soon as the peer key has been learnt */
    private final Map<InetSocketAddress, Queue<Serializable>> pending = new ConcurrentHashMap<>();

    /* Keep track whether we have already sent *our* key to a certain peer */
    private final Set<InetSocketAddress> keySent = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // ---------------------------------------------------------------------
    //  Construction helpers
    // ---------------------------------------------------------------------

    public SecureEndpoint() {
        this.internal = new Endpoint();
        KeyPair pair = generateKeyPair();
        this.privateKey = pair.getPrivate();
        this.publicKey  = pair.getPublic();
    }

    public SecureEndpoint(int port) {
        this.internal = new Endpoint(port);
        KeyPair pair = generateKeyPair();
        this.privateKey = pair.getPrivate();
        this.publicKey  = pair.getPublic();
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048); // 2048-bit RSA keys
            return kpg.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("RSA key-pair generation failed", e);
        }
    }

    // ---------------------------------------------------------------------
    //  Public API – matches Endpoint
    // ---------------------------------------------------------------------

    /**
     * Transparently encrypts and dispatches a payload. If the peer's public key
     * is not yet known a {@link KeyExchangeMessage} is sent first and the payload
     * is queued until the handshake completes.
     */
    public void send(InetSocketAddress receiver, Serializable payload) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(payload,   "payload");

        /* Key-exchange messages are control traffic – never encrypt them. */
        // Whitelist an Nachrichtentypen, die UNVERSCHLÜSSELT gesendet werden
        if (payload instanceof KeyExchangeMessage
                || payload instanceof RegisterRequest
                || payload instanceof RegisterResponse
                || payload instanceof DeregisterRequest
                || payload instanceof SnapshotMarker
                || payload instanceof SnapshotTokenMessage
                || payload instanceof TokenMessage
                || payload instanceof aqua.blatt4.common.msgtypes.NeighborUpdate
                || payload instanceof NameResolutionRequest
                || payload instanceof aqua.blatt5.common.msgtypes.NameResolutionResponse
                || payload instanceof aqua.blatt5.common.msgtypes.LocationUpdate
                || payload instanceof aqua.blatt5.common.msgtypes.LocationRequest
                || payload instanceof HandoffRequest) {
            internal.send(receiver, payload);
            return;
        }

        PublicKey peerKey = remoteKeys.get(receiver);
        if (peerKey == null) {
            // We do not yet know the peer's key → start handshake and queue message.
            queuePending(receiver, payload);
            initiateKeyExchange(receiver);
            return;
        }

        /* Encrypt application data with the peer's public key */
        try {
            byte[] data       = serialize(payload);
            Cipher cipher     = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, peerKey);
            byte[] encrypted  = cipher.doFinal(data);
            internal.send(receiver, encrypted);
        } catch (Exception e) {
            System.err.println("[DEBUG] ERROR while encrypting payload of type: " + payload.getClass());
            throw new RuntimeException("Sending encrypted message failed", e);
        }
    }

    /**
     * Blocks until a *decrypted* application message becomes available. Control
     * messages needed for the handshake are absorbed internally and therefore
     * invisible to callers.
     */
    public Message blockingReceive() {
        while (true) {
            Message incoming = internal.blockingReceive();
            InetSocketAddress sender = incoming.getSender();
            Object rawPayload        = incoming.getPayload();

            /* 1) Handle handshake traffic */
            if (rawPayload instanceof KeyExchangeMessage) {
                handleKeyExchange(sender, (KeyExchangeMessage) rawPayload);
                continue; // not for the application
            }

            /* 2) Handle encrypted application data */
            if (rawPayload instanceof byte[]) {
                try {
                    Cipher cipher = Cipher.getInstance("RSA");
                    cipher.init(Cipher.DECRYPT_MODE, privateKey);
                    byte[] decrypted = cipher.doFinal((byte[]) rawPayload);
                    Object payload  = deserialize(decrypted);
                    return new Message((Serializable) payload, sender);
                } catch (Exception e) {
                    throw new RuntimeException("Decryption of incoming message failed", e);
                }
            }

            /* 3) Fallback – message was sent unencrypted (should never happen) */
            return incoming;
        }
    }

    // ---------------------------------------------------------------------
    //  Handshake helpers
    // ---------------------------------------------------------------------

    private void initiateKeyExchange(InetSocketAddress receiver) {
        // Send our public key once (idempotent)
        if (keySent.add(receiver)) {
            internal.send(receiver, new KeyExchangeMessage(publicKey));
        }
    }

    private void handleKeyExchange(InetSocketAddress sender, KeyExchangeMessage msg) {
        PublicKey theirKey = msg.getPublicKey();
        remoteKeys.put(sender, theirKey);

        /* Send our key back if this is the first time we hear from that peer */
        if (keySent.add(sender)) {
            internal.send(sender, new KeyExchangeMessage(publicKey));
        }

        /* Any queued payloads can now be transmitted */
        Queue<Serializable> q = pending.remove(sender);
        if (q != null) {
            for (Serializable p : q) send(sender, p);
        }
    }

    private void queuePending(InetSocketAddress receiver, Serializable payload) {
        pending.computeIfAbsent(receiver, r -> new ArrayDeque<>()).add(payload);
    }

    // ---------------------------------------------------------------------
    //  (De)serialization helpers – identical to task 1
    // ---------------------------------------------------------------------

    private byte[] serialize(Object obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(obj);
            return bos.toByteArray();
        }
    }

    private Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream in = new ObjectInputStream(bis)) {
            return in.readObject();
        }
    }
}