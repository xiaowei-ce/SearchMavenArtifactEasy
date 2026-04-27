package cc.xiaowei.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FetchUtils {

    public static final String SEARCH_API_URL = "https://central.sonatype.com/api/internal/browse/components";
    public static final String VERSION_API_URL = "https://search.maven.org/solrsearch/select";
    private static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private static final ExecutorService HTTP_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "maven-search-http");
        t.setDaemon(true);
        return t;
    });

    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .executor(HTTP_EXECUTOR)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // ========================================================================
    //  HTTP Request Utilities
    // ========================================================================

    public static HttpRequest createSearchRequest(String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(SEARCH_API_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    public static HttpRequest createGetRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
    }

    public static HttpResponse<String> execute(HttpRequest request) throws Exception {
        CompletableFuture<HttpResponse<String>> future = HTTP_CLIENT
                .sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            return future.get(REQUEST_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new java.net.http.HttpTimeoutException("Request timed out after " + REQUEST_TIMEOUT.getSeconds() + "s");
        }
    }

    // ========================================================================
    //  JSON Safe Accessors
    // ========================================================================

    public static String getSafeString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }

    public static int getSafeInt(JsonObject obj, String key, int defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsInt();
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    // ========================================================================
    //  Request Data Builders
    // ========================================================================

    public static String buildSearchBody(String query, int page) {
        JsonObject body = new JsonObject();
        body.addProperty("page", page);
        body.addProperty("size", 20);
        body.addProperty("searchTerm", query);
        body.add("filter", new JsonArray());
        return body.toString();
    }

    public static String buildVersionUrl(String groupId, String artifactId) {
        String solrQuery = "g:\"" + StringUtils.escapeSolrParam(groupId)
                + "\" AND a:\"" + StringUtils.escapeSolrParam(artifactId) + "\"";
        String encodedQuery = java.net.URLEncoder.encode(solrQuery, StandardCharsets.UTF_8);
        return VERSION_API_URL + "?q=" + encodedQuery + "&core=gav&rows=100&wt=json";
    }
}
