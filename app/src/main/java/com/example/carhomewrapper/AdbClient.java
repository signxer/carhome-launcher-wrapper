package com.example.carhomewrapper;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/** Minimal ADB host client used only for granting this helper's secure setting permission. */
public final class AdbClient {
    private static final String PREFS = "adb_key";
    private static final String PRIVATE_KEY = "private";
    private static final String PUBLIC_KEY = "public";
    private static final int MAX_DATA = 4096;
    private static final int CNXN = 0x4e584e43;
    private static final int AUTH = 0x48545541;
    private static final int OPEN = 0x4e45504f;
    private static final int OKAY = 0x59414b4f;
    private static final int WRTE = 0x45545257;
    private static final int CLSE = 0x45534c43;
    private static final int AUTH_TOKEN = 1;
    private static final int AUTH_SIGNATURE = 2;
    private static final int AUTH_PUBLIC_KEY = 3;
    private static final String ACCESSIBILITY_CLASS =
            "com.example.carhomewrapper.AdbAccessibilityService";

    private final Context context;

    public AdbClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public String grant(String host, int port) throws Exception {
        KeyPair keyPair = loadOrCreateKeyPair();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(8000);
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());

            send(output, CNXN, 0x01000000, MAX_DATA,
                    bytes("host::features=stat_v2,ls_v2,shell_v2\0"));
            authenticate(input, output, keyPair);
            StringBuilder result = new StringBuilder();

            result.append(executeShell(input, output,
                    "pm grant " + context.getPackageName()
                            + " android.permission.WRITE_SECURE_SETTINGS"));

            String component = context.getPackageName()
                    + "/" + ACCESSIBILITY_CLASS;
            String existing = executeShell(input, output,
                    "settings get secure enabled_accessibility_services").trim();
            if (!existing.contains(component)) {
                String merged = existing.length() == 0 || "null".equals(existing)
                        ? component : existing + ":" + component;
                result.append('\n').append(executeShell(input, output,
                        "settings put secure enabled_accessibility_services " + merged));
            }
            result.append('\n').append(executeShell(input, output,
                    "settings put secure accessibility_enabled 1"));
            return result.toString().trim();
        }
    }

    private String executeShell(DataInputStream input, DataOutputStream output, String command)
            throws Exception {
        int localId = 1;
        send(output, OPEN, localId, 0, bytes("shell:" + command + "\0"));
        StringBuilder result = new StringBuilder();
        int remoteId = -1;
        while (true) {
            Message message = read(input);
            if (message.command == OKAY) {
                remoteId = message.arg0;
            } else if (message.command == WRTE) {
                result.append(new String(message.payload, "UTF-8"));
                send(output, OKAY, localId, message.arg0, new byte[0]);
            } else if (message.command == CLSE) {
                if (remoteId >= 0) send(output, CLSE, localId, remoteId, new byte[0]);
                break;
            } else {
                throw new IOException("ADB unexpected command: " + commandName(message.command));
            }
        }
        return result.toString().trim();
    }

    private void authenticate(DataInputStream input, DataOutputStream output, KeyPair keyPair)
            throws Exception {
        while (true) {
            Message message = read(input);
            if (message.command == CNXN) return;
            if (message.command != AUTH || message.arg0 != AUTH_TOKEN) {
                throw new IOException("ADB handshake failed: " + commandName(message.command));
            }

            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(message.payload);
            send(output, AUTH, AUTH_SIGNATURE, 0, signature.sign());

            Message next = read(input);
            if (next.command == CNXN) return;
            if (next.command != AUTH || next.arg0 != AUTH_TOKEN) {
                throw new IOException("ADB authentication failed: " + commandName(next.command));
            }
            send(output, AUTH, AUTH_PUBLIC_KEY, 0, adbPublicKey(keyPair.getPublic()));
        }
    }

    private KeyPair loadOrCreateKeyPair() throws Exception {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String privateText = prefs.getString(PRIVATE_KEY, null);
        String publicText = prefs.getString(PUBLIC_KEY, null);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        if (privateText != null && publicText != null) {
            PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(
                    Base64.decode(privateText, Base64.DEFAULT)));
            PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(
                    Base64.decode(publicText, Base64.DEFAULT)));
            return new KeyPair(publicKey, privateKey);
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        prefs.edit()
                .putString(PRIVATE_KEY, Base64.encodeToString(
                        keyPair.getPrivate().getEncoded(), Base64.NO_WRAP))
                .putString(PUBLIC_KEY, Base64.encodeToString(
                        keyPair.getPublic().getEncoded(), Base64.NO_WRAP))
                .apply();
        return keyPair;
    }

    private byte[] adbPublicKey(PublicKey publicKey) throws Exception {
        if (!(publicKey instanceof java.security.interfaces.RSAPublicKey)) {
            throw new IOException("Not an RSA public key");
        }
        java.security.interfaces.RSAPublicKey rsa =
                (java.security.interfaces.RSAPublicKey) publicKey;
        BigInteger modulus = rsa.getModulus();
        int bitLength = 2048;
        BigInteger twoTo32 = BigInteger.ONE.shiftLeft(32);
        BigInteger inverse = modulus.mod(twoTo32).modInverse(twoTo32);
        long n0inv = twoTo32.subtract(inverse).longValue() & 0xffffffffL;
        BigInteger r = BigInteger.ONE.shiftLeft(bitLength);
        BigInteger rr = r.multiply(r).mod(modulus);

        ByteArrayOutputStream result = new ByteArrayOutputStream(524 + 32);
        writeLe32(result, bitLength);
        writeLe32(result, n0inv);
        writeLeBigInteger(result, modulus, 256);
        writeLeBigInteger(result, rr, 256);
        writeLe32(result, rsa.getPublicExponent().longValue());
        String encodedKey = Base64.encodeToString(result.toByteArray(), Base64.NO_WRAP);
        return bytes(encodedKey + " carhome-wrapper\0");
    }

    private static void send(DataOutputStream output, int command, int arg0, int arg1,
                             byte[] payload) throws IOException {
        output.writeInt(Integer.reverseBytes(command));
        output.writeInt(Integer.reverseBytes(arg0));
        output.writeInt(Integer.reverseBytes(arg1));
        output.writeInt(Integer.reverseBytes(payload.length));
        output.writeInt(Integer.reverseBytes(checksum(payload)));
        output.writeInt(Integer.reverseBytes(command ^ 0xffffffff));
        output.write(payload);
        output.flush();
    }

    private static Message read(DataInputStream input) throws IOException {
        int command = Integer.reverseBytes(input.readInt());
        int arg0 = Integer.reverseBytes(input.readInt());
        int arg1 = Integer.reverseBytes(input.readInt());
        int length = Integer.reverseBytes(input.readInt());
        int checksum = Integer.reverseBytes(input.readInt());
        int magic = Integer.reverseBytes(input.readInt());
        if ((command ^ 0xffffffff) != magic || length < 0 || length > 1024 * 1024) {
            throw new IOException("Invalid ADB message header");
        }
        byte[] payload = new byte[length];
        input.readFully(payload);
        if (checksum(payload) != checksum) throw new IOException("ADB checksum mismatch");
        return new Message(command, arg0, arg1, payload);
    }

    private static int checksum(byte[] data) {
        int result = 0;
        for (byte value : data) result += value & 0xff;
        return result;
    }

    private static void writeLe32(ByteArrayOutputStream output, long value) {
        output.write((int) value & 0xff);
        output.write((int) (value >>> 8) & 0xff);
        output.write((int) (value >>> 16) & 0xff);
        output.write((int) (value >>> 24) & 0xff);
    }

    private static void writeLeBigInteger(ByteArrayOutputStream output, BigInteger value, int length) {
        byte[] source = value.toByteArray();
        for (int i = 0; i < length; i++) {
            int sourceIndex = source.length - 1 - i;
            output.write(sourceIndex >= 0 ? source[sourceIndex] : 0);
        }
    }

    private static byte[] bytes(String value) {
        try {
            return value.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String commandName(int command) {
        return new String(new byte[]{
                (byte) command, (byte) (command >>> 8),
                (byte) (command >>> 16), (byte) (command >>> 24)});
    }

    private static final class Message {
        final int command;
        final int arg0;
        final int arg1;
        final byte[] payload;

        Message(int command, int arg0, int arg1, byte[] payload) {
            this.command = command;
            this.arg0 = arg0;
            this.arg1 = arg1;
            this.payload = payload;
        }
    }
}
