package dslab.secure;

import dslab.entity.Mail;
import dslab.util.Keys;

import javax.crypto.Mac;
import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DmtpSecure implements IDmtpSecure{

    private Mac hMac;

    public DmtpSecure() {

        setUpMac();

    }

    private void setUpMac() {
        Key secretKey = getSharedKey();

        try {
            hMac = Mac.getInstance("HmacSHA256");
            hMac.init(secretKey);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Error while creating Mac instance: " + e.getMessage());
        }
    }

    private Key getSharedKey() {
        try {
            File file = new File("keys/hmac.key");
            return Keys.readSecretKey(file);
        } catch (IOException e) {
            throw new RuntimeException("Error while reading secret key: " + e.getMessage());
        }
    }

    @Override
    public String signMessage(String message) {

        hMac.update(message.getBytes());
        byte[] hash = hMac.doFinal();

        return DmapSecure.binaryToBase64(hash);
    }

    public String signMessage(Mail mail) {

        String mailString = String.join("\n", mail.getFrom(), mail.getTo(), mail.getSubject(), mail.getData());

        return signMessage(mailString);
    }

    @Override
    public boolean validateHash(byte[] computedHash, byte[] receivedHash) {
        return MessageDigest.isEqual(computedHash, receivedHash);
    }

    @Override
    public boolean validateHash(String computedHash, String receivedHash) {
        return validateHash(computedHash.getBytes(), receivedHash.getBytes());
    }
}
