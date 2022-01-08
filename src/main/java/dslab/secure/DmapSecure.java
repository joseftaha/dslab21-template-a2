package dslab.secure;

import dslab.util.Keys;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;

public class DmapSecure {

    private static String binaryToAscii(byte[] input) {
        return Base64.getEncoder().encodeToString(input);
    }

    public static String getRandomNumber(int length) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] output = new byte[length];
        secureRandom.nextBytes(output);
        return binaryToAscii(output);
    }

    public static PublicKey getServerPublicKey(String serverName) throws IOException {
        File file = new File("keys/client/" + serverName + "_pub.der");
        return Keys.readPublicKey(file);
    }

    public static SecretKey generateSecretKey(String algorithm, int keySize) throws NoSuchAlgorithmException {
        KeyGenerator generator = KeyGenerator.getInstance(algorithm);
        generator.init(keySize);
        return generator.generateKey();
    }
}
