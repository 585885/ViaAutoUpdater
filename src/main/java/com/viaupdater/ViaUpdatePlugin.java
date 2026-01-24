package com.viaupdater;

import com.viaupdater.commands.ViaUpdateCommand;
import com.viaupdater.config.ConfigManager;
import com.viaupdater.config.Messages;
import com.viaupdater.manager.UpdateManager;
import com.viaupdater.task.AutoCheckTask;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public class ViaUpdatePlugin extends JavaPlugin {

    private static ViaUpdatePlugin instance;
    private ConfigManager configManager;
    private Messages messages;
    private UpdateManager updateManager;
    private ScheduledTask autoCheckTask;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        configManager = new ConfigManager(this);
        messages = new Messages(this);
        updateManager = new UpdateManager(this);

        getCommand("viaupdate").setExecutor(new ViaUpdateCommand(this));

        if (getConfig().getBoolean("check-on-startup", true)) {
            getServer().getAsyncScheduler().runNow(this, (task) -> {
                updateManager.checkAllUpdates();
            });
        }

        if (getConfig().getBoolean("auto-check", true)) {
            startAutoCheckTask();
        }

        getLogger().info("ViaAutoUpdater 已启用！");
    }

    @Override
    public void onDisable() {
        if (autoCheckTask != null) {
            autoCheckTask.cancel();
        }
        getLogger().info("ViaAutoUpdater 已禁用！");
    }

    private void startAutoCheckTask() {
        long intervalSeconds = getConfig().getLong("check-interval", 60) * 60L;

        autoCheckTask = getServer().getGlobalRegionScheduler().runAtFixedRate(this, (task) -> {
            getServer().getAsyncScheduler().runNow(this, (asyncTask) -> {
                updateManager.checkAllUpdates();
            });
        }, 1L, intervalSeconds * 20L);

        getLogger().info("自动检查更新任务已启动，间隔: " + getConfig().getLong("check-interval", 60) + " 分钟");
    }

    public static ViaUpdatePlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public UpdateManager getUpdateManager() {
        return updateManager;
    }

    public void reloadPlugin() {
        reloadConfig();
        configManager.reload();
        messages.reload();

        if (autoCheckTask != null) {
            autoCheckTask.cancel();
        }

        if (getConfig().getBoolean("auto-check", true)) {
            startAutoCheckTask();
        }
    }
}