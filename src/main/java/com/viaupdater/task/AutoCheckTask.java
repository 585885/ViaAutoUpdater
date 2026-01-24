package com.viaupdater.task;

import com.viaupdater.ViaUpdatePlugin;
import com.viaupdater.config.Messages;
import org.bukkit.Bukkit;

public class AutoCheckTask implements Runnable {

    private final ViaUpdatePlugin plugin;
    private final Messages messages;

    public AutoCheckTask(ViaUpdatePlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
    }

    @Override
    public void run() {
        plugin.getLogger().info("执行自动检查更新任务...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getUpdateManager().checkAllUpdates();
        });
    }
}