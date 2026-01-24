package com.viaupdater.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.viaupdater.ViaUpdatePlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

public class GitHubService {

    private final ViaUpdatePlugin plugin;
    private final Gson gson;
    private final String apiUrl;
    private final int timeout;

    public GitHubService(ViaUpdatePlugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.apiUrl = plugin.getConfig().getString("github.api-url", "https://api.github.com");
        this.timeout = plugin.getConfig().getInt("github.timeout", 30) * 1000;
    }

    public CompletableFuture<ReleaseInfo> getLatestRelease(String owner, String repo) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String urlStr = apiUrl + "/repos/" + owner + "/" + repo + "/releases/latest";
                URL url = new URL(urlStr);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setConnectTimeout(timeout);
                connection.setReadTimeout(timeout);

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    throw new Exception("GitHub API返回错误: " + responseCode);
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JsonObject json = gson.fromJson(response.toString(), JsonObject.class);
                String tagName = json.get("tag_name").getAsString();
                String htmlUrl = json.get("html_url").getAsString();

                JsonArray assets = json.getAsJsonArray("assets");
                if (assets.size() == 0) {
                    throw new Exception("未找到发布文件");
                }

                JsonObject asset = assets.get(0).getAsJsonObject();
                String downloadUrl = asset.get("browser_download_url").getAsString();
                String fileName = asset.get("name").getAsString();

                return new ReleaseInfo(tagName, htmlUrl, downloadUrl, fileName);

            } catch (Exception e) {
                plugin.getLogger().severe("获取GitHub发布信息失败: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Path> downloadFile(String downloadUrl, Path targetPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(downloadUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(timeout);
                connection.setReadTimeout(timeout);

                Files.createDirectories(targetPath.getParent());

                try (var in = connection.getInputStream()) {
                    Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }

                plugin.getLogger().info("文件下载成功: " + targetPath);
                return targetPath;

            } catch (Exception e) {
                plugin.getLogger().severe("下载文件失败: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    public record ReleaseInfo(String version, String htmlUrl, String downloadUrl, String fileName) {}
}