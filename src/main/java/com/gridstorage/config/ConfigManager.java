package com.gridstorage.config;

import java.io.File;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.gridstorage.GridStorage;

/**
 * Config and message loader.
 */
public class ConfigManager {

    private final GridStorage plugin;
    private FileConfiguration config;
    private FileConfiguration messages;

    public ConfigManager(GridStorage plugin) {
        this.plugin = plugin;
        loadConfigurations();
    }

    private void loadConfigurations() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();

        String lang = getLanguage();
        File messagesFile = new File(plugin.getDataFolder(), "messages_" + lang + ".yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages_" + lang + ".yml", false);
        }
        if (!messagesFile.exists()) {
            plugin.saveResource("messages_zh.yml", false);
            messagesFile = new File(plugin.getDataFolder(), "messages_zh.yml");
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public String getLanguage() {
        return config.getString("language.default", "zh");
    }

    public int getMaxStorageCount() {
        return Math.max(1, config.getInt("grid.max-storage-count", 100));
    }

    public String getGridName() {
        return config.getString("grid.name", "网格仓库");
    }

    public int getPageSize() {
        return config.getInt("grid.page-size", 45);
    }

    public int getGuiSize() {
        return config.getInt("grid.gui-size", 54);
    }

    public String getMessage(String path, String... replacements) {
        String message = messages.getString(path);
        if (message == null) {
            return path;
        }
        for (int i = 0; i < replacements.length; i++) {
            message = message.replace("{" + i + "}", replacements[i]);
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public List<String> getMessageList(String path) {
        List<String> list = messages.getStringList(path);
        for (int i = 0; i < list.size(); i++) {
            list.set(i, ChatColor.translateAlternateColorCodes('&', list.get(i)));
        }
        return list;
    }

    public String getPrefix() {
        String prefix = messages.getString("prefix", "&6[网格存储] &r");
        return ChatColor.translateAlternateColorCodes('&', prefix);
    }

    public void reload() {
        plugin.reloadConfig();
        loadConfigurations();
    }
}
