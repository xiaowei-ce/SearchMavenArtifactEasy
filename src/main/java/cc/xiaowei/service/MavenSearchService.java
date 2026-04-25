package cc.xiaowei.service;

import cc.xiaowei.utils.FetchUtils;
import cc.xiaowei.utils.StringUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class MavenSearchService {

    private static final Logger LOG = Logger.getInstance(MavenSearchService.class);

    public record ArtifactEntry(String groupId, String artifactId, String latestVersion) {
    }

    public record SearchResult(List<ArtifactEntry> entries, int pageCount, int totalCount) {
    }

    // ========================================================================
    //  Search with Pagination
    // ========================================================================

    public static void search(
            @Nullable Project project,
            @NotNull String query,
            int page,
            @NotNull Consumer<SearchResult> onSuccess,
            @NotNull Consumer<String> onError,
            @Nullable Runnable onFinished
    ) {
        Task.Backgroundable task = new Task.Backgroundable(project, "Searching Maven Central...") {
            private final List<ArtifactEntry> results = new ArrayList<>();
            private @Nullable String errorMessage;
            private int respPageCount;
            private int respTotalCount;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Searching for \"" + query + "\" (page " + (page + 1) + ")...");

                String requestBody = FetchUtils.buildSearchBody(query, page);
                logApiRequest("Search API Request", "POST", FetchUtils.SEARCH_API_URL, requestBody);

                try {
                    long startTime = System.currentTimeMillis();
                    HttpResponse<String> response = FetchUtils.execute(FetchUtils.createSearchRequest(requestBody));
                    long elapsed = System.currentTimeMillis() - startTime;

                    logApiResponse("Search API Response", response, elapsed, null);

                    if (response.statusCode() != 200) {
                        errorMessage = "Search API returned status " + response.statusCode();
                        return;
                    }

                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonArray components = root.getAsJsonArray("components");
                    if (components == null || components.isEmpty()) {
                        LOG.info("Search returned 0 results for query: \"" + query + "\"");
                        return;
                    }

                    for (JsonElement elem : components) {
                        JsonObject comp = elem.getAsJsonObject();
                        String groupId = FetchUtils.getSafeString(comp, "namespace");
                        String artifactId = FetchUtils.getSafeString(comp, "name");
                        String version = "";
                        if (comp.has("latestVersionInfo") && !comp.get("latestVersionInfo").isJsonNull()) {
                            JsonObject lvi = comp.getAsJsonObject("latestVersionInfo");
                            version = FetchUtils.getSafeString(lvi, "version");
                        }
                        if (!groupId.isEmpty() && !artifactId.isEmpty()) {
                            results.add(new ArtifactEntry(groupId, artifactId, version));
                        }
                    }

                    respPageCount = FetchUtils.getSafeInt(root, "pageCount", 1);
                    respTotalCount = FetchUtils.getSafeInt(root, "totalResultCount", results.size());
                    LOG.info("Search parsed " + results.size() + " artifacts, page "
                            + page + "/" + respPageCount + ", total=" + respTotalCount);
                } catch (Exception ex) {
                    LOG.error("Search API request failed for query: \"" + query + "\"", ex);
                    errorMessage = "Network error: " + ex.getMessage();
                }
            }

            @Override
            public void onSuccess() {
                if (errorMessage != null) {
                    onError.accept(errorMessage);
                    return;
                }
                onSuccess.accept(new SearchResult(results, respPageCount, respTotalCount));
            }

            @Override
            public void onFinished() {
                if (onFinished != null) onFinished.run();
            }
        };
        task.queue();
    }

    // ========================================================================
    //  Version Fetching
    // ========================================================================

    public static void fetchVersions(
            @Nullable Project project,
            @NotNull String groupId,
            @NotNull String artifactId,
            @NotNull Consumer<List<String>> onSuccess,
            @NotNull Consumer<String> onError,
            @Nullable Runnable onFinished
    ) {
        Task.Backgroundable task = new Task.Backgroundable(project, "Loading versions...") {
            private final List<String> versions = new ArrayList<>();
            private @Nullable String errorMessage;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Loading versions for " + groupId + ":" + artifactId + "...");

                String url = FetchUtils.buildVersionUrl(groupId, artifactId);
                logApiRequest("Version API Request", "GET", url, null);

                try {
                    long startTime = System.currentTimeMillis();
                    HttpResponse<String> response = FetchUtils.execute(FetchUtils.createGetRequest(url));
                    long elapsed = System.currentTimeMillis() - startTime;

                    logApiResponse("Version API Response", response, elapsed,
                            "for " + groupId + ":" + artifactId);

                    if (response.statusCode() != 200) {
                        errorMessage = "Version API returned status " + response.statusCode();
                        return;
                    }

                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonObject responseObj = root.getAsJsonObject("response");
                    if (responseObj == null) {
                        LOG.warn("Version API response missing 'response' field for "
                                + groupId + ":" + artifactId);
                        return;
                    }
                    JsonArray docs = responseObj.getAsJsonArray("docs");
                    if (docs == null) {
                        LOG.warn("Version API response missing 'docs' field for "
                                + groupId + ":" + artifactId);
                        return;
                    }

                    for (JsonElement elem : docs) {
                        String v = FetchUtils.getSafeString(elem.getAsJsonObject(), "v");
                        if (!v.isEmpty()) {
                            versions.add(v);
                        }
                    }
                    versions.sort(Comparator.reverseOrder());
                    LOG.info("Version API: found " + versions.size() + " versions for "
                            + groupId + ":" + artifactId);
                } catch (Exception ex) {
                    LOG.error("Version API request failed for " + groupId + ":" + artifactId, ex);
                    errorMessage = "Network error: " + ex.getMessage();
                }
            }

            @Override
            public void onSuccess() {
                if (errorMessage != null) {
                    onError.accept(errorMessage);
                    return;
                }
                onSuccess.accept(versions);
            }

            @Override
            public void onFinished() {
                if (onFinished != null) onFinished.run();
            }
        };
        task.queue();
    }

    // ========================================================================
    //  Logging Helpers
    // ========================================================================

    static void logApiRequest(String apiName, String method, String url, String body) {
        LOG.info("=== " + apiName + " ===");
        LOG.info("URL: " + method + " " + url);
        if (body != null) LOG.info("Body: " + body);
    }

    static void logApiResponse(String apiName, HttpResponse<String> response,
                               long elapsed, String details) {
        LOG.info("=== " + apiName + " ===");
        LOG.info("Status: " + response.statusCode() + " (" + elapsed + "ms)"
                + (details != null ? " " + details : ""));
        LOG.info("Body (truncated): " + StringUtils.truncateBody(response.body(), 500));
    }
}
