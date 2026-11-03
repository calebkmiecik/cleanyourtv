package com.gathin.tvhome;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * Runs one shell command through the projector's own network adb (127.0.0.1:5555), as the shell user. Some
 * projector controls exist only there: XGIMI's hardware service answers shell and system apps, never a normal
 * app (MoGo 4: auto keystone and auto focus). The first time, Android asks on screen to allow this app's key;
 * "Always allow" makes later calls silent. Needs network adb on (caleb/adb-on-boot keeps it on).
 *
 * The adb protocol (system/core/adb/protocol.txt): 24-byte little-endian headers, CNXN, AUTH with an RSA
 * signature of the device's token (or our public key in Android's own format), then OPEN "shell:..." and read WRTE
 * until CLSE.
 */
final class LocalAdb {
    private LocalAdb() {
    }

    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257;
    private static final int VERSION = 0x01000001, MAX_DATA = 256 * 1024;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    // DER prefix of a SHA-1 DigestInfo: adbd checks the signature as RSA_sign(NID_sha1, token)
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a,
            0x05, 0x00, 0x04, 0x14};

    /** The command's output. Blocks (network and, the first time, Caleb's answer): never on the main thread. */
    static String shell(Context ctx, String command) throws IOException {
        KeyPair keys = keys(ctx);
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 5555), 2000);
            s.setSoTimeout(10_000);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            send(out, CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.UTF_8));
            boolean signed = false;
            while (true) {
                Msg m = read(in);
                if (m.cmd == CNXN) break;
                if (m.cmd != AUTH || m.arg0 != AUTH_TOKEN) throw new IOException("adb: unexpected " + m.cmd);
                if (!signed) {   // try our key first; adbd sends another token if it does not know the key
                    send(out, AUTH, AUTH_SIGNATURE, 0, sign(keys, m.data));
                    signed = true;
                } else {         // unknown key: offer it, and wait while the projector asks Caleb to allow it
                    byte[] pub = publicKey((RSAPublicKey) keys.getPublic());
                    // public, so safe to log: lets an adb session approve it when the on-screen prompt never shows
                    android.util.Log.i("LocalAdb", "offering key " + new String(pub, 0, pub.length - 1, StandardCharsets.UTF_8));
                    send(out, AUTH, AUTH_RSAPUBLICKEY, 0, pub);
                    s.setSoTimeout(60_000);
                }
            }
            s.setSoTimeout(30_000);
            send(out, OPEN, 1, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            while (true) {
                Msg m = read(in);
                if (m.cmd == WRTE) {
                    result.write(m.data);
                    send(out, OKAY, 1, m.arg0, new byte[0]);
                } else if (m.cmd == CLSE) {
                    return result.toString("UTF-8");
                }   // OKAY: the stream opened, or our OKAY was seen
            }
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static final class Msg {
        int cmd, arg0;
        byte[] data;
    }

    private static void send(OutputStream out, int cmd, int arg0, int arg1, byte[] data) throws IOException {
        int sum = 0;
        for (byte b : data) sum += b & 0xFF;
        ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~cmd);
        out.write(h.array());
        out.write(data);
        out.flush();
    }

    private static Msg read(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(in);
        byte[] head = new byte[24];
        d.readFully(head);
        ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = h.getInt();
        m.arg0 = h.getInt();
        h.getInt();
        int len = h.getInt();
        if (len < 0 || len > MAX_DATA) throw new IOException("adb: bad length " + len);
        m.data = new byte[len];
        d.readFully(m.data);
        return m;
    }

    private static byte[] sign(KeyPair keys, byte[] token) throws java.security.GeneralSecurityException {
        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        c.init(Cipher.ENCRYPT_MODE, keys.getPrivate());
        byte[] block = new byte[SHA1_PREFIX.length + token.length];
        System.arraycopy(SHA1_PREFIX, 0, block, 0, SHA1_PREFIX.length);
        System.arraycopy(token, 0, block, SHA1_PREFIX.length, token.length);
        return c.doFinal(block);
    }

    /** Android's adb public key: base64 of {len, n0inv, n[64], rr[64], e} (little-endian words), then a name. */
    static byte[] publicKey(RSAPublicKey key) {
        int words = 64;   // 2048-bit
        BigInteger n = key.getModulus(), r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(words * 32 * 2).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words).putInt(n0inv.intValue());
        putWords(b, n, words);
        putWords(b, rr, words);
        b.putInt(key.getPublicExponent().intValue());
        String text = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " projector-home@tvhome\0";
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void putWords(ByteBuffer b, BigInteger v, int words) {
        for (int i = 0; i < words; i++) b.putInt(v.shiftRight(i * 32).intValue());
    }

    /** This app's own adb key, made once and kept in its files (private to the app). */
    private static synchronized KeyPair keys(Context ctx) throws IOException {
        File priv = new File(ctx.getFilesDir(), "adbkey"), pub = new File(ctx.getFilesDir(), "adbkey.pub");
        try {
            KeyFactory f = KeyFactory.getInstance("RSA");
            if (priv.isFile() && pub.isFile()) {
                return new KeyPair(f.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(pub.toPath()))),
                        f.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(priv.toPath()))));
            }
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair k = g.generateKeyPair();
            Files.write(priv.toPath(), k.getPrivate().getEncoded());
            Files.write(pub.toPath(), k.getPublic().getEncoded());
            return k;
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException(e);
        }
    }
}
