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
import java.nio.charset.StandardCharsets;
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
        System.out.println("public key: " + file.getAbsolutePath());
        return Keys.readPublicKey(file);
    }

    public static PrivateKey getServerPrivateKey(String serverName) throws IOException {
        File file = new File("keys/server/" + serverName + ".der");
        System.out.println("private key: " + file.getAbsolutePath());
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

        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        cipher.doFinal(messageBytes);

        writer.println(binaryToAscii(messageBytes));
        writer.flush();
    }

    private String readResponseServer() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        PrivateKey privateKey = getServerPrivateKey(componentId);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);

        String response = reader.readLine();
        System.out.println("response: " + response);

        byte[] decryptedMessage = cipher.doFinal(response.getBytes());
        System.out.println("decripted: " + binaryToAscii(decryptedMessage));
        return binaryToAscii(decryptedMessage);
    }

    public void performHandshakeClient() {
        String response;
        String[] responseSplit;
        try {
            //send start secure to sever
            writer.println("startsecure");
            writer.flush();

            //read ok <component-id>
            response = reader.readLine();
            responseSplit = response.split(" ");
            if (responseSplit.length != 2) {
                throw new RuntimeException("Error while performing Handshake");
            }
            this.componentId = responseSplit[1];

            //send ok <client-challenge> <secret-key> <iv>
            //String challenge = getRandomNumber(32);
            //String iv = getRandomNumber(16);
            SecureRandom secureRandom = new SecureRandom();
            byte[] challengeb = new byte[32];
            byte[] ivb = new byte[16];
            secureRandom.nextBytes(challengeb);
            secureRandom.nextBytes(ivb);
            String iv = binaryToAscii(ivb);
            String challenge = binaryToAscii(challengeb);
            //secureRandom.nextBytes(output);
            String secretKey = binaryToAscii(generateSecretKey("AES", 256).getEncoded());
            System.out.println("Iv:" + iv);
            System.out.println("challenge: " + challenge);
            String message = "ok " + challenge + " " + secretKey + " " + iv;
            System.out.println("String: " + message);
            System.out.println("message in ascii" + message);
            sendMessageClient(message);

        } catch (IOException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | NoSuchAlgorithmException e) {
            throw new RuntimeException("error while performing handshake" + e.getMessage());
        }
    }

    public void performHandshakeServer() {
        String response;
        try {
            writer.println("ok " + componentId);
            writer.flush();

            response = readResponseServer();
            System.out.println("response: " + response);
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
