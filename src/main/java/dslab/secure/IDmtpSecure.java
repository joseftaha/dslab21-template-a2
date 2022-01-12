package dslab.secure;

public interface IDmtpSecure {

    String signMessage(String message);

    boolean validateHash(byte[] computedHash, byte[] receivedHash);

    boolean validateHash(String computedHash, String receivedHash);
}
