package com.gridstorage.logging;

import com.gridstorage.GridStorage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * 日志管理器
 * 提供文件日志输出功能，支持日志级别控制和文件滚动
 */
public class PluginLogger {

    private final GridStorage plugin;
    private final ReentrantLock logLock = new ReentrantLock();
    
    private boolean enabled;
    private LogLevel level;
    private boolean fileLogging;
    private File logFile;
    private long maxFileSize;
    private int maxFiles;
    
    // 日志级别枚举
    public enum LogLevel {
        DEBUG(0),
        INFO(1),
        WARN(2),
        ERROR(3);
        
        private final int value;
        
        LogLevel(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
        
        public static LogLevel fromString(String level) {
            try {
                return LogLevel.valueOf(level.toUpperCase());
            } catch (IllegalArgumentException e) {
                return INFO;
            }
        }
    }
    
    /**
     * 构造函数
     * @param plugin 插件实例
     */
    public PluginLogger(GridStorage plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    /**
     * 从配置文件加载日志设置
     */
    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("logging.enabled", true);
        level = LogLevel.fromString(plugin.getConfig().getString("logging.level", "INFO"));
        fileLogging = plugin.getConfig().getBoolean("logging.file-logging", true);
        
        String logPath = plugin.getConfig().getString("logging.log-file", "logs/gridstorage.log");
        maxFileSize = plugin.getConfig().getLong("logging.max-file-size", 10) * 1024 * 1024; // MB to bytes
        maxFiles = plugin.getConfig().getInt("logging.max-files", 5);
        
        if (fileLogging && enabled) {
            logFile = new File(plugin.getDataFolder(), logPath);
            ensureLogFileExists();
        }
    }
    
    /**
     * 确保日志文件和目录存在
     */
    private void ensureLogFileExists() {
        try {
            if (logFile.getParentFile() != null) {
                logFile.getParentFile().mkdirs();
            }
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("无法创建日志文件: " + e.getMessage());
            fileLogging = false;
        }
    }
    
    /**
     * 记录 DEBUG 日志
     */
    public void debug(String message) {
        log(LogLevel.DEBUG, message);
    }
    
    /**
     * 记录 INFO 日志
     */
    public void info(String message) {
        log(LogLevel.INFO, message);
    }
    
    /**
     * 记录 WARN 日志
     */
    public void warning(String message) {
        log(LogLevel.WARN, message);
    }
    
    /**
     * 记录 ERROR 日志
     */
    public void error(String message) {
        log(LogLevel.ERROR, message);
    }
    
    /**
     * 记录带异常的 ERROR 日志
     */
    public void error(String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder(message);
        sb.append("\n").append(getStackTrace(throwable));
        log(LogLevel.ERROR, sb.toString());
    }
    
    /**
     * 核心日志方法
     * @param logLevel 日志级别
     * @param message 日志消息
     */
    private void log(LogLevel logLevel, String message) {
        if (!enabled || logLevel.getValue() < level.getValue()) {
            return;
        }
        
        // 格式化日志消息
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        String formattedMessage = String.format("[%s] [%s] [%s] %s",
                timestamp,
                Thread.currentThread().getName(),
                logLevel.name(),
                message);
        
        // 写入文件
        if (fileLogging) {
            writeToFile(formattedMessage);
        }
        
        // 根据日志级别决定是否输出到控制台（仅重要日志）
        if (logLevel == LogLevel.ERROR || logLevel == LogLevel.WARN) {
            writeToConsole(logLevel, message);
        }
    }
    
    /**
     * 写入日志文件
     * @param message 日志消息
     */
    private void writeToFile(String message) {
        if (!fileLogging || logFile == null) {
            return;
        }
        
        // 检查文件大小，如果超过限制则滚动
        if (logFile.length() >= maxFileSize) {
            rotateLogFile();
        }
        
        logLock.lock();
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println(message);
            writer.flush();
        } catch (IOException e) {
            plugin.getLogger().warning("写入日志文件失败: " + e.getMessage());
        } finally {
            logLock.unlock();
        }
    }
    
    /**
     * 写入控制台
     * @param level 日志级别
     * @param message 日志消息
     */
    private void writeToConsole(LogLevel level, String message) {
        java.util.logging.Level bukkitLevel = switch (level) {
            case DEBUG -> Level.FINE;
            case INFO -> Level.INFO;
            case WARN -> Level.WARNING;
            case ERROR -> Level.SEVERE;
        };
        
        plugin.getLogger().log(bukkitLevel, message);
    }
    
    /**
     * 滚动日志文件
     */
    private void rotateLogFile() {
        logLock.lock();
        try {
            // 删除最旧的日志文件
            File oldestFile = new File(logFile.getPath() + "." + maxFiles);
            if (oldestFile.exists()) {
                oldestFile.delete();
            }
            
            // 重命名现有的日志文件
            for (int i = maxFiles - 1; i >= 1; i--) {
                File oldFile = new File(logFile.getPath() + "." + i);
                File newFile = new File(logFile.getPath() + "." + (i + 1));
                if (oldFile.exists()) {
                    oldFile.renameTo(newFile);
                }
            }
            
            // 当前日志文件重命名为 .1
            File currentFile = logFile;
            File newCurrentFile = new File(logFile.getPath() + ".1");
            currentFile.renameTo(newCurrentFile);
            
            // 创建新的日志文件
            logFile.createNewFile();
            
        } catch (IOException e) {
            plugin.getLogger().warning("滚动日志文件失败: " + e.getMessage());
        } finally {
            logLock.unlock();
        }
    }
    
    /**
     * 获取异常堆栈信息
     * @param throwable 异常对象
     * @return 堆栈信息字符串
     */
    private String getStackTrace(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");
        
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        
        // 包含cause
        Throwable cause = throwable.getCause();
        while (cause != null) {
            sb.append("Caused by: ").append(cause.toString()).append("\n");
            for (StackTraceElement element : cause.getStackTrace()) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }
            cause = cause.getCause();
        }
        
        return sb.toString();
    }
    
    /**
     * 刷新日志缓冲区
     */
    public void flush() {
        // PrintWriter 自动刷新，此方法为未来扩展保留
    }
    
    /**
     * 关闭日志管理器
     */
    public void close() {
        logLock.lock();
        try {
            // 清理资源
        } finally {
            logLock.unlock();
        }
    }
    
    // Getter 方法
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public LogLevel getLevel() {
        return level;
    }
    
    public boolean isFileLoggingEnabled() {
        return fileLogging;
    }
}
