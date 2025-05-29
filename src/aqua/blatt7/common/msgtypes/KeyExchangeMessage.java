package aqua.blatt7.common.msgtypes;

import java.io.Serializable;
import java.security.PublicKey;

/** Control message used internally by {@code SecureEndpoint} to exchange RSA keys. */
public class KeyExchangeMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final PublicKey publicKey;

    public KeyExchangeMessage(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }
}