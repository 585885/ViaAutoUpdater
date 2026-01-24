package com.viaupdater.commands;

import com.viaupdater.ViaUpdatePlugin;
import com.viaupdater.config.Messages;
import com.viaupdater.model.ViaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ViaUpdateCommand implements CommandExecutor, TabCompleter {

    private final ViaUpdatePlugin plugin;
    private final Messages messages;

    public ViaUpdateCommand(ViaUpdatePlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("viaupdate.admin")) {
            sender.sendMessage(messages.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "check":
                handleCheck(sender, args);
                break;
            case "update":
                handleUpdate(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "help":
                sendHelp(sender);
                break;
            default:
                sender.sendMessage(messages.getMessage("unknown-command"));
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (!sender.hasPermission("viaupdate.check")) {
            sender.sendMessage(messages.getMessage("no-permission"));
            return;
        }

        sender.sendMessage(messages.getMessage("checking-updates"));

        if (args.length > 1) {
            String pluginName = args[1];
            if (!ViaPlugin.isSupported(pluginName)) {
                sender.sendMessage(messages.getMessage("plugin-not-found").replace("{plugin}", pluginName));
                return;
            }

            plugin.getUpdateManager().checkUpdate(pluginName)
                    .thenAccept(update -> {
                        if (update.hasUpdate()) {
                            sender.sendMessage(messages.getMessage("update-available"));
                            sender.sendMessage(messages.getMessage("update-available-detail")
                                    .replace("{plugin}", update.pluginName())
                                    .replace("{current}", update.currentVersion())
                                    .replace("{latest}", update.latestVersion()));
                        } else {
                            sender.sendMessage(messages.getMessage("plugin-uptodate")
                                    .replace("{plugin}", update.pluginName())
                                    .replace("{version}", update.currentVersion()));
                        }
                    })
                    .exceptionally(e -> {
                        sender.sendMessage(messages.getMessage("check-error").replace("{error}", e.getMessage()));
                        return null;
                    });
        } else {
            plugin.getUpdateManager().checkAllUpdates(sender);
        }
    }

    private void handleUpdate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("viaupdate.update")) {
            sender.sendMessage(messages.getMessage("no-permission"));
            return;
        }

        if (args.length > 1) {
            String pluginName = args[1];
            if (!ViaPlugin.isSupported(pluginName)) {
                sender.sendMessage(messages.getMessage("plugin-not-found").replace("{plugin}", pluginName));
                return;
            }

            plugin.getUpdateManager().updatePlugin(pluginName, sender);
        } else {
            plugin.getUpdateManager().updateAllPlugins(sender);
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("viaupdate.reload")) {
            sender.sendMessage(messages.getMessage("no-permission"));
            return;
        }

        plugin.reloadPlugin();
        sender.sendMessage(messages.getMessage("plugin-reloaded"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(messages.getMessage("help-header"));
        sender.sendMessage(messages.getMessage("help-command-check"));
        sender.sendMessage(messages.getMessage("help-command-update"));
        sender.sendMessage(messages.getMessage("help-command-reload"));
        sender.sendMessage(messages.getMessage("help-footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("check", "update", "reload", "help"));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("check") || subCommand.equals("update")) {
                completions.addAll(plugin.getConfig().getStringList("plugins"));
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}