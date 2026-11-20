package com.cosmetics.listener;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.cosmetic.CosmeticCategory;
import com.cosmetics.cosmetic.KillSoundCosmetic;
import com.cosmetics.data.DatabaseManager;
import com.cosmetics.manager.PetManager;
import com.cosmetics.manager.SoundManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

public class PlayerListener implements Listener {

    private final CosmeticsPlugin plugin;

    public PlayerListener(CosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getDatabaseManager().loadPlayerData(player.getUniqueId()).thenAccept(data -> {
            if (!player.isOnline()) return;

            for (CosmeticCategory category : CosmeticCategory.values()) {
                String activeId = data.getActive(category.name().toLowerCase());
                if (activeId != null && !activeId.isEmpty()) {
                    var cosmetic = plugin.getCosmeticsManager().getCosmetic(category, activeId);
                    if (cosmetic != null) {
                        cosmetic.onEquip(player.getUniqueId());
                    }
                }
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        for (CosmeticCategory category : CosmeticCategory.values()) {
            String activeId = plugin.getDatabaseManager().getCachedData(uuid).getActive(category.name().toLowerCase());
            if (activeId != null && !activeId.isEmpty()) {
                var cosmetic = plugin.getCosmeticsManager().getCosmetic(category, activeId);
                if (cosmetic != null) {
                    cosmetic.onUnequip(uuid);
                }
            }
        }

        plugin.getPetManager().cleanup();
        plugin.getDatabaseManager().savePlayerData(uuid);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        DatabaseManager.PlayerCosmeticData data = plugin.getDatabaseManager().getCachedData(uuid);
        if (data == null) return;

        for (CosmeticCategory category : CosmeticCategory.values()) {
            String activeId = data.getActive(category.name().toLowerCase());
            if (activeId != null && !activeId.isEmpty()) {
                var cosmetic = plugin.getCosmeticsManager().getCosmetic(category, activeId);
                if (cosmetic != null) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> cosmetic.onEquip(uuid), 5L);
                }
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        DatabaseManager.PlayerCosmeticData data = plugin.getDatabaseManager().getCachedData(uuid);
        if (data == null) return;

        for (CosmeticCategory category : CosmeticCategory.values()) {
            String activeId = data.getActive(category.name().toLowerCase());
            if (activeId != null && !activeId.isEmpty()) {
                var cosmetic = plugin.getCosmeticsManager().getCosmetic(category, activeId);
                if (cosmetic != null) {
                    cosmetic.onUnequip(uuid);
                    cosmetic.onEquip(uuid);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        // Clean up pets/companions on death
        plugin.getPetManager().despawnPet(player);
        plugin.getPetManager().despawnCompanion(uuid);

        // Clean up any active tasks for this player
        cleanupCosmeticTasks(uuid);
    }

    private void cleanupCosmeticTasks(UUID uuid) {
        com.cosmetics.cosmetic.AuraCosmetic.cleanup(uuid);
        com.cosmetics.cosmetic.TrailCosmetic.cleanup(uuid);
        com.cosmetics.cosmetic.WingCosmetic.cleanup(uuid);
        com.cosmetics.cosmetic.HatCosmetic.cleanup(uuid);
        com.cosmetics.cosmetic.CompanionCosmetic.cleanup(uuid);
    }
}