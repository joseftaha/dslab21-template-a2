package dslab.secure;

public interface IDmtpSecure {

    /**
     * calculate hash
     * @param message calculate message hash and attach it to the String
     * @return String representation with the newly calculated hash added
     */
    String signMessage(String message);

    /**
     * evaluates if the two hashed are the same
     * @param computedHash hash which has been computed
     * @param receivedHash hash which has been received from the message
     * @return true if the Hashes are equal false otherwise
     */
    boolean validateHash(byte[] computedHash, byte[] receivedHash);
}
