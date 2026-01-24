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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class UpdateManager {

    private final ViaUpdatePlugin plugin;
    private final GitHubService gitHubService;
    private final Messages messages;

    public UpdateManager(ViaUpdatePlugin plugin) {
        this.plugin = plugin;
        this.gitHubService = new GitHubService(plugin);
        this.messages = plugin.getMessages();
    }

    public void checkAllUpdates() {
        checkAllUpdates(null);
    }

    public void checkAllUpdates(CommandSender sender) {
        List<String> enabledPlugins = plugin.getConfig().getStringList("plugins");
        List<PluginUpdateInfo> updates = new ArrayList<>();

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
        });
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
        });
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
        try {
            return Files.list(pluginsDir)
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

    public record PluginUpdateInfo(
            String pluginName,
            String currentVersion,
            String latestVersion,
            String downloadUrl,
            String fileName
    ) {
        public boolean hasUpdate() {
            return !currentVersion.equals(latestVersion);
        }
    }
}