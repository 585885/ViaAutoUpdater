package com.viaupdater.manager;

import com.viaupdater.ViaUpdatePlugin;
import com.viaupdater.config.Messages;
import com.viaupdater.model.ViaPlugin;
import com.viaupdater.service.GitHubService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class UpdateManager {

    private final ViaUpdatePlugin plugin;
    private final GitHubService gitHubService;
    private final Messages messages;

    /**
     * 专用 IO 线程池。
     *
     * <p>原始实现用 {@code CompletableFuture.supplyAsync} 单参版本，跑在
     * {@code ForkJoinPool.commonPool()} 上；服务端其它异步任务共用该池，
     * 下载大 jar 会把它占满、拖慢别处。改用独立池，{@link #shutdown()} 回收。
     */
    private final ExecutorService ioExecutor;

    public UpdateManager(ViaUpdatePlugin plugin) {
        this.plugin = plugin;
        this.ioExecutor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                runnable -> {
                    Thread thread = new Thread(runnable, "ViaAutoUpdater-IO");
                    thread.setDaemon(true);
                    return thread;
                }
        );
        this.gitHubService = new GitHubService(plugin, ioExecutor);
        this.messages = plugin.getMessages();
    }

    public void checkAllUpdates() {
        checkAllUpdates(null);
    }

    public void checkAllUpdates(CommandSender sender) {
        List<String> enabledPlugins = plugin.getConfig().getStringList("plugins");
        // ⚠️ 必须是线程安全的：下面 each checkUpdate 的回调在各自线程里并发 add，
        // 用普通 ArrayList 会丢元素，运气差还会抛 ConcurrentModificationException。
        List<PluginUpdateInfo> updates = Collections.synchronizedList(new ArrayList<>());

        CompletableFuture.allOf(enabledPlugins.stream()
                .map(pluginName -> checkUpdate(pluginName)
                        .thenAccept(update -> {
                            if (update.hasUpdate()) {
                                updates.add(update);
                            }
                        }))
                .toArray(CompletableFuture[]::new))
                .thenRun(() -> {
                    if (sender != null) {
                        if (updates.isEmpty()) {
                            sender.sendMessage(messages.getMessage("no-updates-available"));
                        } else {
                            sender.sendMessage(messages.getMessage("update-available"));
                            updates.forEach(update -> {
                                sender.sendMessage(messages.getMessage("update-available-detail")
                                        .replace("{plugin}", update.pluginName())
                                        .replace("{current}", update.currentVersion())
                                        .replace("{latest}", update.latestVersion()));
                            });
                        }
                    }

                    if (!updates.isEmpty() && plugin.getConfig().getBoolean("notify-admins", true)) {
                        notifyAdmins(updates);
                    }
                })
                .exceptionally(e -> {
                    plugin.getLogger().severe("检查更新时出错: " + e.getMessage());
                    if (sender != null) {
                        sender.sendMessage(messages.getMessage("check-error").replace("{error}", e.getMessage()));
                    }
                    return null;
                });
    }

    public CompletableFuture<PluginUpdateInfo> checkUpdate(String pluginName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ViaPlugin viaPlugin = ViaPlugin.fromName(pluginName);
                Plugin installedPlugin = Bukkit.getPluginManager().getPlugin(viaPlugin.getName());

                String currentVersion = installedPlugin != null ? installedPlugin.getDescription().getVersion() : "未安装";

                GitHubService.ReleaseInfo release = gitHubService.getLatestRelease(viaPlugin.getOwner(), viaPlugin.getRepo()).join();

                return new PluginUpdateInfo(
                        viaPlugin.getName(),
                        currentVersion,
                        release.version(),
                        release.downloadUrl(),
                        release.fileName()
                );

            } catch (Exception e) {
                plugin.getLogger().severe("检查 " + pluginName + " 更新时出错: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ioExecutor);
    }

    public void updateAllPlugins(CommandSender sender) {
        List<String> enabledPlugins = plugin.getConfig().getStringList("plugins");

        sender.sendMessage(messages.getMessage("batch-update-start"));

        List<CompletableFuture<Boolean>> futures = enabledPlugins.stream()
                .map(pluginName -> updatePlugin(pluginName, sender))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    long successCount = futures.stream()
                            .filter(CompletableFuture::join)
                            .count();
                    long failedCount = futures.size() - successCount;

                    if (failedCount == 0) {
                        sender.sendMessage(messages.getMessage("batch-update-complete").replace("{count}", String.valueOf(successCount)));
                    } else {
                        sender.sendMessage(messages.getMessage("batch-update-failed")
                                .replace("{success}", String.valueOf(successCount))
                                .replace("{failed}", String.valueOf(failedCount)));
                    }

                    if (successCount > 0 && plugin.getConfig().getBoolean("restart-reminder", true)) {
                        sender.sendMessage(messages.getMessage("restart-reminder"));
                    }
                })
                .exceptionally(e -> {
                    sender.sendMessage(messages.getMessage("error-unknown").replace("{error}", e.getMessage()));
                    return null;
                });
    }

    public CompletableFuture<Boolean> updatePlugin(String pluginName, CommandSender sender) {
        return checkUpdate(pluginName)
                .thenCompose(update -> {
                    if (!update.hasUpdate()) {
                        if (sender != null) {
                            sender.sendMessage(messages.getMessage("plugin-uptodate")
                                    .replace("{plugin}", update.pluginName())
                                    .replace("{version}", update.currentVersion()));
                        }
                        return CompletableFuture.completedFuture(false);
                    }

                    return downloadUpdate(update, sender);
                })
                .exceptionally(e -> {
                    plugin.getLogger().severe("更新 " + pluginName + " 时出错: " + e.getMessage());
                    if (sender != null) {
                        sender.sendMessage(messages.getMessage("download-error")
                                .replace("{plugin}", pluginName)
                                .replace("{error}", e.getMessage()));
                    }
                    return false;
                });
    }

    private CompletableFuture<Boolean> downloadUpdate(PluginUpdateInfo update, CommandSender sender) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (sender != null) {
                    sender.sendMessage(messages.getMessage("downloading-update")
                            .replace("{plugin}", update.pluginName())
                            .replace("{version}", update.latestVersion()));
                }

                Path pluginsDir = plugin.getDataFolder().getParentFile().toPath();
                Path updateDir = pluginsDir.resolve("update");
                Path targetFile = updateDir.resolve(update.fileName());

                if (plugin.getConfig().getBoolean("backup", true)) {
                    backupPlugin(update.pluginName());
                }

                gitHubService.downloadFile(update.downloadUrl(), targetFile).join();

                if (sender != null) {
                    sender.sendMessage(messages.getMessage("download-success")
                            .replace("{plugin}", update.pluginName())
                            .replace("{version}", update.latestVersion()));
                }

                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("下载更新时出错: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ioExecutor);
    }

    private void backupPlugin(String pluginName) {
        try {
            Path pluginsDir = plugin.getDataFolder().getParentFile().toPath();
            Path backupFolder = pluginsDir.resolve(plugin.getConfig().getString("backup-folder", "ViaUpdateBackups"));
            Files.createDirectories(backupFolder);

            File pluginFile = findPluginFile(pluginName);
            if (pluginFile != null && pluginFile.exists()) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                Path backupFile = backupFolder.resolve(pluginFile.getName().replace(".jar", "_" + timestamp + ".jar"));
                Files.copy(pluginFile.toPath(), backupFile, StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("已备份 " + pluginName + " 到 " + backupFile);
            }

        } catch (IOException e) {
            plugin.getLogger().warning("备份 " + pluginName + " 时出错: " + e.getMessage());
        }
    }

    private File findPluginFile(String pluginName) {
        Path pluginsDir = plugin.getDataFolder().getParentFile().toPath();
        // try-with-resources：Files.list 返回的 Stream 持有目录句柄，不关会一直泄漏。
        try (var stream = Files.list(pluginsDir)) {
            return stream
                    .filter(path -> path.toString().toLowerCase().endsWith(".jar"))
                    .filter(path -> path.getFileName().toString().toLowerCase().contains(pluginName.toLowerCase()))
                    .map(Path::toFile)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private void notifyAdmins(List<PluginUpdateInfo> updates) {
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission("viaupdate.notify") || player.isOp())
                .forEach(player -> {
                    player.sendMessage(messages.getMessage("update-available"));
                    updates.forEach(update -> {
                        player.sendMessage(messages.getMessage("update-available-detail")
                                .replace("{plugin}", update.pluginName())
                                .replace("{current}", update.currentVersion())
                                .replace("{latest}", update.latestVersion()));
                    });
                });
    }

    /** 插件卸载时调用，回收 IO 线程池。 */
    public void shutdown() {
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public record PluginUpdateInfo(
            String pluginName,
            String currentVersion,
            String latestVersion,
            String downloadUrl,
            String fileName
    ) {
        public boolean hasUpdate() {
            // ⚠️ 不能直接字符串比较：'v1.0.0' 与 '1.0.0'、'1.0' 与 '1.0.0' 都会被误判成有更新。
            return compareVersions(currentVersion, latestVersion) > 0;
        }

        /**
         * 按版本号逐段数值比较，返回 &gt;0 表示 latest 更新。
         *
         * <p>规则：忽略前导 'v'/'V'；按 '.' 切分；非数字段按 0 处理；
         * 段数不足的一侧补 0（所以 1.0 == 1.0.0）。
         */
        private static int compareVersions(String current, String latest) {
            String a = stripPrefix(current);
            String b = stripPrefix(latest);
            String[] pa = a.split("\\.");
            String[] pb = b.split("\\.");
            int len = Math.max(pa.length, pb.length);
            for (int i = 0; i < len; i++) {
                int x = i < pa.length ? parseIntSafe(pa[i]) : 0;
                int y = i < pb.length ? parseIntSafe(pb[i]) : 0;
                if (x != y) return Integer.compare(y, x);
            }
            return 0;
        }

        private static String stripPrefix(String v) {
            if (v == null) return "0";
            String s = v.trim();
            if (!s.isEmpty() && (s.charAt(0) == 'v' || s.charAt(0) == 'V')) s = s.substring(1);
            return s.isEmpty() ? "0" : s;
        }

        private static int parseIntSafe(String segment) {
            try {
                int dash = segment.indexOf('-');
                String num = dash >= 0 ? segment.substring(0, dash) : segment;
                return num.isEmpty() ? 0 : Integer.parseInt(num);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}