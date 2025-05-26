package aqua.blatt7.crypto;

import messaging.Endpoint;
import messaging.Message;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.Arrays;

public class SecureEndpoint {
    private static final String KEY_STRING = "CAFEBABECAFEBABE";
    private static final String ALGORITHM = "AES";
    private final Endpoint internal;
    private final Cipher encryptCipher;
    private final Cipher decryptCipher;

    public SecureEndpoint() {
        this.internal = new Endpoint();
        this.encryptCipher = initCipher(Cipher.ENCRYPT_MODE);
        this.decryptCipher = initCipher(Cipher.DECRYPT_MODE);
    }

    public SecureEndpoint(int port) {
        this.internal = new Endpoint(port);
        this.encryptCipher = initCipher(Cipher.ENCRYPT_MODE);
        this.decryptCipher = initCipher(Cipher.DECRYPT_MODE);
    }

    private Cipher initCipher(int mode) {
        try {
            byte[] keyBytes = KEY_STRING.getBytes();
            SecretKeySpec key = new SecretKeySpec(Arrays.copyOf(keyBytes, 16), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(mode, key);
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Cipher init failed", e);
        }
    }

    public void send(InetSocketAddress receiver, Serializable payload) {
        try {
            byte[] serialized = serialize(payload);
            byte[] encrypted = encryptCipher.doFinal(serialized);
            internal.send(receiver, encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Sending encrypted message failed", e);
        }
    }

    public Message blockingReceive() {
        try {
            Message encryptedMessage = internal.blockingReceive();
            byte[] encryptedPayload = (byte[]) encryptedMessage.getPayload();
            byte[] decryptedPayload = decryptCipher.doFinal(encryptedPayload);
            Object payload = deserialize(decryptedPayload);
            return new Message((Serializable) payload, encryptedMessage.getSender());
        } catch (Exception e) {
            throw new RuntimeException("Receiving encrypted message failed", e);
        }
    }

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