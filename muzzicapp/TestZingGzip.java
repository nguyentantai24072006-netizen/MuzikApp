import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TestZingGzip {
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

    public static void main(String[] args) {
        try {
            String id = "ZO8W7WC0"; // She Neva Knows JustaTee
            long ctime = System.currentTimeMillis() / 1000;
            String path = "/api/v2/song/get/streaming";
            String sigParams = "ctime=" + ctime + "id=" + id;
            String hash256 = getHash256(sigParams);
            String sig = getHmac512(path + hash256, SECRET);

            String urlString = BASE_URL + path + "?id=" + id + "&ctime=" + ctime + "&sig=" + sig + "&api_key=" + API_KEY;
            
            HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            
            BufferedReader reader;
            if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(connection.getInputStream()), "UTF-8"));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
