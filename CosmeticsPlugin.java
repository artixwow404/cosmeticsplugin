package com.cosmetics;

import com.cosmetics.command.CosmeticsCommand;
import com.cosmetics.config.ConfigManager;
import com.cosmetics.data.DatabaseManager;
import com.cosmetics.manager.CosmeticsManager;
import com.cosmetics.manager.EconomyManager;
import com.cosmetics.manager.GuiManager;
import com.cosmetics.manager.ParticleManager;
import com.cosmetics.manager.PetManager;
import com.cosmetics.manager.SoundManager;
import com.cosmetics.listener.PlayerListener;
import com.cosmetics.listener.GuiListener;
import com.cosmetics.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class CosmeticsPlugin extends JavaPlugin {

    private static CosmeticsPlugin instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private CosmeticsManager cosmeticsManager;
    private ParticleManager particleManager;
    private PetManager petManager;
    private SoundManager soundManager;
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResourceFiles();

        configManager = new ConfigManager(this);
        configManager.loadAll();

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        economyManager = new EconomyManager(this);
        economyManager.initialize();

        cosmeticsManager = new CosmeticsManager(this);
        cosmeticsManager.loadCosmetics();

        particleManager = new ParticleManager(this);
        petManager = new PetManager(this);
        soundManager = new SoundManager(this);
        guiManager = new GuiManager(this);

        getCommand("cosmetics").setExecutor(new CosmeticsCommand(this));

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        startTasks();

        getLogger().info("Cosmetics plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (petManager != null) {
            petManager.cleanup();
        }
        if (particleManager != null) {
            particleManager.cleanup();
        }
        getLogger().info("Cosmetics plugin disabled!");
    }

    private void saveResourceFiles() {
        String[] files = {"config.yml", "cosmetics.yml", "prices.yml", "gui.yml", "messages.yml", "sounds.yml"};
        for (String file : files) {
            saveResource(file, false);
        }
    }

    private void startTasks() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            particleManager.updateAll();
            petManager.updateAll();
        }, 1L, 1L);

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            cosmeticsManager.saveAllPlayerData();
        }, 20L * 60 * 5, 20L * 60 * 5);
    }

    public static CosmeticsPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public CosmeticsManager getCosmeticsManager() { return cosmeticsManager; }
    public ParticleManager getParticleManager() { return particleManager; }
    public PetManager getPetManager() { return petManager; }
    public SoundManager getSoundManager() { return soundManager; }
    public GuiManager getGuiManager() { return guiManager; }
}