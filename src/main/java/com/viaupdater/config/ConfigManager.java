package com.viaupdater.config;

import com.viaupdater.ViaUpdatePlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final ViaUpdatePlugin plugin;
    private FileConfiguration config;

    public ConfigManager(ViaUpdatePlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public boolean isAutoCheckEnabled() {
        return config.getBoolean("auto-check", true);
    }

    public long getCheckInterval() {
        return config.getLong("check-interval", 60);
    }

    public boolean isBackupEnabled() {
        return config.getBoolean("backup", true);
    }

    public boolean isNotifyAdminsEnabled() {
        return config.getBoolean("notify-admins", true);
    }

    public boolean isRestartReminderEnabled() {
        return config.getBoolean("restart-reminder", true);
    }

    public boolean isCheckOnStartupEnabled() {
        return config.getBoolean("check-on-startup", true);
    }
}