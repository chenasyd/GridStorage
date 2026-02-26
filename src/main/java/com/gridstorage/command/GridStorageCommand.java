package com.gridstorage.command;

import com.gridstorage.GridStorage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * GridStorage 命令处理器
 */
public class GridStorageCommand implements CommandExecutor {

    private final GridStorage plugin;

    public GridStorageCommand(GridStorage plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getPrefix() + plugin.getConfigManager().getMessage("commands.messages.player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("gridstorage")) {
            return handleGridStorageCommand(player, args);
        } else if (command.getName().equalsIgnoreCase("gridstorageadmin")) {
            return handleAdminCommand(player, args);
        }

        return false;
    }

    private boolean handleGridStorageCommand(Player player, String[] args) {
        if (args.length == 0) {
            // 打开仓库
            plugin.getStorageManager().openStorage(player);
            player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage("storage.messages.open-storage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                sendHelp(player);
                return true;
            case "open":
                if (args.length >= 2) {
                    try {
                        int slotId = Integer.parseInt(args[1]);
                        if (slotId < 1 || slotId > plugin.getConfigManager().getMaxStorageCount()) {
                            player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage("storage.messages.invalid-slot"));
                        } else {
                            plugin.getStorageManager().openSlot(player, slotId);
                            player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage("storage.messages.open-slot", String.valueOf(slotId)));
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage("storage.messages.invalid-slot"));
                    }
                } else {
                    plugin.getStorageManager().openStorage(player);
                    player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage("storage.messages.open-storage"));
                }
                return true;
            case "reload":
                if (player.hasPermission("gridstorage.admin")) {
                    plugin.getConfigManager().reload();
                    player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage("commands.messages.config-reloaded"));
                } else {
                    player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage("commands.messages.no-permission"));
                }
                return true;
            default:
                sendHelp(player);
                return true;
        }
    }

    private boolean handleAdminCommand(Player player, String[] args) {
        if (!player.hasPermission("gridstorage.admin")) {
            player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage("commands.messages.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.getConfigManager().reload();
                player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage("commands.messages.config-reloaded"));
                return true;
            case "help":
                sendAdminHelp(player);
                return true;
            default:
                sendAdminHelp(player);
                return true;
        }
    }

    private String getPrefix() {
        return plugin.getConfigManager().getPrefix();
    }

    private void sendHelp(Player player) {
        player.sendMessage(plugin.getConfigManager().getMessage("commands.help.header"));
        player.sendMessage(plugin.getConfigManager().getMessage("commands.help.main"));
        player.sendMessage(plugin.getConfigManager().getMessage("commands.help.open-slot"));
        player.sendMessage(plugin.getConfigManager().getMessage("commands.help.help"));
        player.sendMessage(plugin.getConfigManager().getMessage("commands.help.reload"));
        player.sendMessage(plugin.getConfigManager().getMessage("commands.help.footer"));
    }

    private void sendAdminHelp(Player player) {
        player.sendMessage(plugin.getConfigManager().getMessage("commands.help.header"));
        player.sendMessage(plugin.getConfigManager().getMessage("commands.help.admin-reload"));
        player.sendMessage(plugin.getConfigManager().getMessage("commands.help.admin-help"));
        player.sendMessage(plugin.getConfigManager().getMessage("commands.help.footer"));
    }
}
