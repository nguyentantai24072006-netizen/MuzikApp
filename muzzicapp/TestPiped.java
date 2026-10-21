import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStreamReader;
import java.io.BufferedReader;

public class TestPiped {
    public static void main(String[] args) throws Exception {
        String q = java.net.URLEncoder.encode("sơn tùng m-tp", "UTF-8");
        String urlString = "https://pipedapi.kavin.rocks/search?q=" + q + "&filter=music_songs";
        
        System.out.println("URL: " + urlString);
        
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        System.out.println("Response Code: " + conn.getResponseCode());
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line.substring(0, Math.min(line.length(), 300)));
        }
        reader.close();
    }
}
