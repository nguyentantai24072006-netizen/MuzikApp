import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestNCT {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://www.nhaccuatui.com/tim-kiem/bai-hat?q=S%C6%A1n%20T%C3%B9ng");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            FileWriter out = new FileWriter("nct_out.html");
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                out.write(inputLine + "\n");
            }
            in.close();
            out.close();
            System.out.println("DONE");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
