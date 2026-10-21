import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.zip.GZIPInputStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class TestZingChart {
    private static final String SECRET = "10a01dcf33762d3a204cb96429918ff6";
    public static final String API_KEY = "38e8643fb0dc04e8d65b0f9f400fd9f1";
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
    
    public static void main(String[] args) throws Exception {
        long ctime = System.currentTimeMillis() / 1000;
        String path = "/api/v2/page/get/chart-home";
        String sig = getSignature(path, null, ctime);
        
        String urlString = BASE_URL + path + "?ctime=" + ctime + "&sig=" + sig + "&api_key=" + API_KEY;
        
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        conn.setRequestProperty("Accept-Encoding", "gzip");
        System.out.println("Response Code: " + conn.getResponseCode());
        
        BufferedReader reader;
        if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) {
            reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(conn.getInputStream()), "UTF-8"));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        }
        
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line.substring(0, Math.min(line.length(), 200)));
        }
        reader.close();
    }
}
