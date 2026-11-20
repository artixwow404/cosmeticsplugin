package com.cosmetics.manager;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.cosmetic.KillSoundCosmetic;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SoundManager implements Listener {

    private final CosmeticsPlugin plugin;
    private final Map<UUID, KillSoundCosmetic> playerKillSounds = new ConcurrentHashMap<>();
    private float masterVolume = 1.0f;

    public SoundManager(CosmeticsPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfigManager().getConfig("sounds");
        masterVolume = (float) config.getDouble("global.master-volume", 1.0);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void playActivationSound(Player player, com.cosmetics.cosmetic.CosmeticCategory category) {
        FileConfiguration config = plugin.getConfigManager().getConfig("sounds");
        String path = "activation." + category.name().toLowerCase();
        String soundName = config.getString(path + ".sound", "UI_BUTTON_CLICK");
        float volume = (float) config.getDouble(path + ".volume", 0.5) * masterVolume;
        float pitch = (float) config.getDouble(path + ".pitch", 1.0);

        playSound(player, soundName, volume, pitch);
    }

    public void playDeactivationSound(Player player) {
        FileConfiguration config = plugin.getConfigManager().getConfig("sounds");
        String soundName = config.getString("deactivation.sound", "UI_BUTTON_CLICK");
        float volume = (float) config.getDouble("deactivation.volume", 0.4) * masterVolume;
        float pitch = (float) config.getDouble("deactivation.pitch", 1.0);

        playSound(player, soundName, volume, pitch);
    }

    public void playPurchaseSuccessSound(Player player) {
        FileConfiguration config = plugin.getConfigManager().getConfig("sounds");
        String soundName = config.getString("purchase.success.sound", "ENTITY_PLAYER_LEVELUP");
        float volume = (float) config.getDouble("purchase.success.volume", 0.6) * masterVolume;
        float pitch = (float) config.getDouble("purchase.success.pitch", 1.2);

        playSound(player, soundName, volume, pitch);
    }

    public void playPurchaseFailSound(Player player) {
        FileConfiguration config = plugin.getConfigManager().getConfig("sounds");
        String soundName = config.getString("purchase.fail.sound", "BLOCK_ANVIL_LAND");
        float volume = (float) config.getDouble("purchase.fail.volume", 0.5) * masterVolume;
        float pitch = (float) config.getDouble("purchase.fail.pitch", 0.8);

        playSound(player, soundName, volume, pitch);
    }

    public void playPreviewSound(Player player) {
        FileConfiguration config = plugin.getConfigManager().getConfig("sounds");
        String soundName = config.getString("preview.sound", "UI_BUTTON_CLICK");
        float volume = (float) config.getDouble("preview.volume", 0.4) * masterVolume;
        float pitch = (float) config.getDouble("preview.pitch", 1.1);

        playSound(player, soundName, volume, pitch);
    }

    public void setPlayerKillSound(UUID playerId, KillSoundCosmetic cosmetic) {
        playerKillSounds.put(playerId, cosmetic);
    }

    public void removePlayerKillSound(UUID playerId) {
        playerKillSounds.remove(playerId);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) return;

        KillSoundCosmetic cosmetic = playerKillSounds.get(killer.getUniqueId());
        if (cosmetic == null) return;

        String soundName = cosmetic.getSound();
        float volume = cosmetic.getVolume() * masterVolume;
        float pitch = cosmetic.getPitch();
        int radius = cosmetic.getRadius();

        Location loc = victim.getLocation();
        for (Player nearby : loc.getWorld().getPlayers()) {
            if (nearby.getLocation().distance(loc) <= radius) {
                playSound(nearby, soundName, volume, pitch);
            }
        }
    }

    private void playSound(Player player, String soundName, float volume, float pitch) {
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound: " + soundName);
        }
    }

    public void reload() {
        FileConfiguration config = plugin.getConfigManager().getConfig("sounds");
        masterVolume = (float) config.getDouble("global.master-volume", 1.0);
    }
}