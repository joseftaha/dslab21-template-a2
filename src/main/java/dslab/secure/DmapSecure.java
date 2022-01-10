package dslab.secure;

import dslab.util.Keys;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;

public class DmapSecure {

    private final BufferedReader reader;
    private final PrintWriter writer;
    private final SecureRandom secureRandom;
    private String componentId;

    private String iv;

    public DmapSecure(BufferedReader reader, PrintWriter writer, String componentId) {
        this.reader = reader;
        this.writer = writer;
        this.componentId = componentId;
        this.secureRandom = new SecureRandom();
    }

    private static String binaryToAscii(byte[] input) {
        return Base64.getEncoder().encodeToString(input);
    }

    private static byte[] asciiToBinary(String input) {
        return Base64.getDecoder().decode(input);
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

    public static PrivateKey getServerPrivateKey(String serverName) throws IOException {
        File file = new File("key/server/" + serverName + ".der");
        return Keys.readPrivateKey(file);
    }

    public static SecretKey generateSecretKey(String algorithm, int keySize) throws NoSuchAlgorithmException {
        KeyGenerator generator = KeyGenerator.getInstance(algorithm);
        generator.init(keySize);
        return generator.generateKey();
    }

    private String[] getChallengeAndIv(String input) {
        String[] inputSplit = input.split(" ");
        if (inputSplit.length != 4) {
            return null;
        }
        return inputSplit;
    }

    private void sendMessageClient(String message) throws NoSuchPaddingException, NoSuchAlgorithmException, IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        PublicKey publicKeyServer = getServerPublicKey(componentId);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKeyServer);

        byte[] messageBytes = asciiToBinary(message);
        cipher.doFinal(messageBytes);

        writer.println(binaryToAscii(messageBytes));
        writer.flush();
        System.out.println(binaryToAscii(messageBytes));
    }

    private String readResponseClient() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        String response = reader.readLine();

        PrivateKey privateKey = getServerPrivateKey(componentId);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);

        byte[] decryptedMessage = cipher.doFinal(asciiToBinary(response));
        System.out.println(binaryToAscii(decryptedMessage));
        return binaryToAscii(decryptedMessage);
    }

    private void performHandshakeClient() throws NoSuchAlgorithmException {

        try {
            String response = reader.readLine();

            String[] responseSplit = response.split(" ");
            if (responseSplit.length != 2) {
                throw new RuntimeException("Error while performing Handshake");
            }
            this.componentId = responseSplit[1];
            String challenge = getRandomNumber(32);
            String iv = getRandomNumber(18);
            String secretKey = binaryToAscii(generateSecretKey("AES", 256).getEncoded());

            String message = "ok " + challenge + " " + secretKey + " " + iv;
            sendMessageClient(message);

        } catch (IOException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("error while performing handshake" + e.getMessage());
        }
    }

    private void performHandshakeServer() {
        String response;
        try {
            writer.println("ok " + componentId);

            response = readResponseClient();
            String[] responseSplit = getChallengeAndIv(response);
            if (responseSplit == null){
                return;
            }
            String clientChallenge = responseSplit[1];
            String secretKey = responseSplit[2];
            this.iv = responseSplit[3];

            System.out.println(response);
        } catch (IOException e) {
            throw new RuntimeException("unable to perform Handshake: " + e.getMessage());
        } catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }
}
