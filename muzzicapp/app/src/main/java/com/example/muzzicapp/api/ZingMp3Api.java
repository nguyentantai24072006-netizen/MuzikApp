package com.example.muzzicapp.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ZingMp3Api {
    private static final String SECRET = "2aa2d1c561e809b267f3638c4bbe4afa";
    public static final String API_KEY = "88265e23d4284f25963e6eedac8fbfa3";
    public static final String BASE_URL = "https://zingmp3.vn";

    public static String getHash256(String str) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(str.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    public static String getHmac512(String str, String key) throws Exception {
        Mac hmac512 = Mac.getInstance("HmacSHA512");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        hmac512.init(secretKeySpec);
        byte[] hash = hmac512.doFinal(str.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static String getSignature(String path, String id, long ctime) throws Exception {
        String sigParams = id != null ? "ctime=" + ctime + "id=" + id : "ctime=" + ctime;
        String hash256 = getHash256(sigParams);
        return getHmac512(path + hash256, SECRET);
    }
}
