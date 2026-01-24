package com.viaupdater.config;

import com.viaupdater.ViaUpdatePlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;

public class Messages {

    private final ViaUpdatePlugin plugin;
    private FileConfiguration messagesConfig;

    public Messages(ViaUpdatePlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages_zh_CN.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages_zh_CN.yml", false);
        }

        try {
            messagesConfig = new org.bukkit.configuration.file.YamlConfiguration();
            messagesConfig.load(messagesFile);
        } catch (Exception e) {
            plugin.getLogger().warning("无法加载消息文件: " + e.getMessage());
        }
    }

    public void reload() {
        loadMessages();
    }

    public String getMessage(String key) {
        String message = messagesConfig.getString(key);
        if (message == null) {
            return ChatColor.RED + "未找到消息: " + key;
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getMessage(String key, String... replacements) {
        String message = getMessage(key);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        return message;
    }

    public String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("prefix", "&6[ViaAutoUpdater] &f"));
    }
}