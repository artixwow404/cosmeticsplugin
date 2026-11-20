package com.cosmetics.config;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final Map<String, File> configFiles = new HashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        String[] configNames = {"config", "cosmetics", "prices", "gui", "messages", "sounds"};
        for (String name : configNames) {
            loadConfig(name);
        }
        MessageUtil.setMessagesConfig(getConfig("messages"));
    }

    public void loadConfig(String name) {
        File file = new File(plugin.getDataFolder(), name + ".yml");
        configFiles.put(name, file);

        if (!file.exists()) {
            plugin.saveResource(name + ".yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        configs.put(name, config);
    }

    public FileConfiguration getConfig(String name) {
        return configs.getOrDefault(name, new YamlConfiguration());
    }

    public void saveConfig(String name) {
        File file = configFiles.get(name);
        FileConfiguration config = configs.get(name);
        if (file != null && config != null) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save config: " + name);
                e.printStackTrace();
            }
        }
    }

    public void saveAll() {
        for (String name : configs.keySet()) {
            saveConfig(name);
        }
    }

    public void reloadAll() {
        configs.clear();
        configFiles.clear();
        loadAll();
    }
}