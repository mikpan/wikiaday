import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;

public class HttpUtils {
    public static Object getJSONResource(String url) throws IOException, ParseException {
        CloseableHttpClient httpClient = HttpClientBuilder.create().disableCookieManagement().build();
        return getJSONResource(httpClient, url);
    }

    private static Object getJSONResource(CloseableHttpClient httpClient, String url) throws IOException, ParseException {
        HttpGet request = new HttpGet(url);
        request.addHeader("content-type", "application/json");
        HttpResponse result = httpClient.execute(request);
        String json = EntityUtils.toString(result.getEntity(), "UTF-8");
        JSONParser parser = new JSONParser();
        return parser.parse(json);
    }
}
