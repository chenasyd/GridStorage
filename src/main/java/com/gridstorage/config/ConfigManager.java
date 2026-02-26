package com.gridstorage.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.gridstorage.GridStorage;

import java.io.File;
import java.util.List;
import java.util.UUID;

/**
 * 配置管理器
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

        // 加载消息配置
        String lang = getLanguage();
        File messagesFile = new File(plugin.getDataFolder(), "messages_" + lang + ".yml");

        // 如果消息文件不存在，从jar复制
        if (!messagesFile.exists()) {
            plugin.saveResource("messages_" + lang + ".yml", false);
        }

        // 如果指定的语言文件不存在，使用中文作为默认
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
        return config.getInt("grid.max-storage-count", 100);
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

    /**
     * 获取数据存储类型
     * @return 数据库类型 (sqlite 或 mysql)
     */
    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    /**
     * 检查是否使用 MySQL
     */
    public boolean useMySQL() {
        return "mysql".equalsIgnoreCase(getDatabaseType());
    }

    /**
     * 获取玩家数据文件夹
     */
    public File getPlayerDataFolder() {
        return new File(plugin.getDataFolder(), "playerdata");
    }

    /**
     * 获取消息，支持参数替换和颜色代码
     * @param path 消息路径
     * @param replacements 替换参数 {0}, {1}, {2}...
     * @return 本地化消息
     */
    public String getMessage(String path, String... replacements) {
        String message = messages.getString(path);
        if (message == null) {
            return path;
        }
        // 替换占位符 {0}, {1}, {2}...
        for (int i = 0; i < replacements.length; i++) {
            message = message.replace("{" + i + "}", replacements[i]);
        }
        // 转换颜色代码
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * 获取消息列表（用于 lore 等），支持颜色代码
     * @param path 消息路径
     * @return 本地化消息列表
     */
    public List<String> getMessageList(String path) {
        List<String> list = messages.getStringList(path);
        // 转换每条消息的颜色代码
        for (int i = 0; i < list.size(); i++) {
            list.set(i, ChatColor.translateAlternateColorCodes('&', list.get(i)));
        }
        return list;
    }

    /**
     * 获取消息前缀
     * @return 消息前缀（已解析颜色代码）
     */
    public String getPrefix() {
        String prefix = messages.getString("prefix", "&6[网格存储] &r");
        return ChatColor.translateAlternateColorCodes('&', prefix);
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        plugin.reloadConfig();
        loadConfigurations();
    }
}

