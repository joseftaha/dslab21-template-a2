package dslab.secure;

import dslab.util.Keys;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;

public class DmapSecure implements IDmapSecure {

    private final BufferedReader reader;
    private final PrintWriter writer;
    private final SecureRandom secureRandom;
    private String componentId;

    private Cipher aesCipherEncrypt;
    private Cipher aesCipherDecrypt;

    private String iv;

    public DmapSecure(BufferedReader reader, PrintWriter writer, String componentId) {
        this.reader = reader;
        this.writer = writer;
        this.componentId = componentId;
        this.secureRandom = new SecureRandom();
    }

    private static String binaryToBase64(byte[] input) {
        return Base64.getEncoder().encodeToString(input);
    }

    private static byte[] base64ToBinary(String input) {
        return Base64.getDecoder().decode(input);
    }

    public static String getRandomNumber(int length) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] output = new byte[length];
        secureRandom.nextBytes(output);
        return binaryToBase64(output);
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

    private void initAesCipher(String secretKey, String iv) {

        byte[] decodedKey = base64ToBinary(secretKey);

        SecretKey originalKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

        try {
                this.aesCipherEncrypt = Cipher.getInstance("AES/CTR/NoPadding");
                this.aesCipherEncrypt.init(Cipher.ENCRYPT_MODE, originalKey, new IvParameterSpec(base64ToBinary(iv)));

                this.aesCipherDecrypt = Cipher.getInstance("AES/CTR/NoPadding");
                this.aesCipherDecrypt.init(Cipher.DECRYPT_MODE, originalKey, new IvParameterSpec(base64ToBinary(iv)));

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            throw new RuntimeException("error while creating AES cipher: " + e.getMessage());
        }
    }



    @Override
    public void sendMessage(String message) {
        try {

            byte[] encryptedMessage = this.aesCipherEncrypt.doFinal(message.getBytes());

            writer.println(binaryToBase64(encryptedMessage));
            writer.flush();

        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("error while reading public key: " + e.getMessage());
        }
    }

    @Override
    public String readMessage() {
        try {

            String message = reader.readLine();

            byte[] decryptedMessage = this.aesCipherDecrypt.doFinal(base64ToBinary(message));

            return new String(decryptedMessage);

        } catch(IOException e) {
            throw new RuntimeException("Error while reading message: " + e.getMessage());
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("Error while decrypting message: " + e.getMessage());
        }
    }

    private void sendChallenge(String challenge, String key, String iv) throws NoSuchPaddingException, NoSuchAlgorithmException, IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        PublicKey publicKeyServer = getServerPublicKey(componentId);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKeyServer);

        String message = "ok " + challenge + " " + key + " " + " " + iv;

        byte[] messageBytes = message.getBytes();
        messageBytes = cipher.doFinal(messageBytes);

        writer.println(binaryToBase64(messageBytes));
        writer.flush();

        System.out.println("send " + message);
    }

    private String readChallenge() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        PrivateKey privateKey = getServerPrivateKey(componentId);

        String response = reader.readLine();

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decryptedMessage = cipher.doFinal(base64ToBinary(response));

        return new String(decryptedMessage);
    }

    @Override
    public void performHandshakeClient() {
        String response;
        String[] responseSplit;
        try {
            // send start secure to sever
            writer.println("startsecure");
            writer.flush();

            // read ok <component-id>
            response = reader.readLine();
            responseSplit = response.split(" ");
            if (responseSplit.length != 2) {
                throw new RuntimeException("Error while performing Handshake");
            }
            this.componentId = responseSplit[1];
            System.out.println(response);

            // send ok <client-challenge> <secret-key> <iv>
            String challenge = getRandomNumber(32);
            String secretKey = binaryToBase64(generateSecretKey("AES", 256).getEncoded());
            String iv = getRandomNumber(16);
            System.out.println("sending challenge: " + iv);
            sendChallenge(challenge, secretKey, iv);

            System.out.println("initialising AES");
            initAesCipher(secretKey, iv);
            System.out.println("done initialising AES");

            // read ok <client-challenge>
            response = readMessage();
            System.out.println("client challenge: " + response);
            responseSplit = response.split(" ");
            if (responseSplit.length != 2) {
                throw new RuntimeException("Error while performing Handshake");
            }
            if (!responseSplit[1].equals(challenge)) {
                throw new RuntimeException("Error while performing Handshake");
            }

            // send ok
            sendMessage("ok");

            System.out.println("Handshake complete");

        } catch (IOException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | NoSuchAlgorithmException e) {
            throw new RuntimeException("error while performing handshake" + e.getMessage());
        }
    }

    @Override
    public void performHandshakeServer() {
        String response;
        try {
            // send ok <componentId>
            writer.println("ok " + componentId);
            writer.flush();
            System.out.println("send componentId: " + componentId);

            // read ok <client-challenge> <secret-key> <iv>
            response = readChallenge();
            String[] responseSplit = getChallengeAndIv(response);
            if (responseSplit == null){
                return;
            }
            String clientChallenge = responseSplit[1];
            String secretKey = responseSplit[2];
            String iv = responseSplit[3];
            System.out.println("iv: " + iv);
            System.out.flush();

            initAesCipher(secretKey, iv);

            // send ok <client-challenge>
            String message = "ok " + clientChallenge;
            System.out.println("sending: " + message);
            sendMessage(message);

            // read ok
            response = readMessage();
            if (!response.equals("ok")) {
                throw new RuntimeException("Error unable to perform Handshake");
            }

            System.out.println("Handshake complete!");

        } catch (IOException e) {
            throw new RuntimeException("unable to perform Handshake: " + e.getMessage());
        } catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }
}
