package com.cosmetics.cosmetic;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.manager.ParticleManager;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuraCosmetic extends Cosmetic {

    private static final ConcurrentHashMap<UUID, Integer> activeTasks = new ConcurrentHashMap<>();

    public AuraCosmetic(String id, String name, String description, CosmeticRarity rarity,
                        String currency, double price, JsonObject properties) {
        super(CosmeticCategory.AURAS, id, name, description, rarity, currency, price, properties);
    }

    @Override
    protected void applyEffect(Player player) {
        playActivationSound(player);
        int taskId = Bukkit.getScheduler().runTaskTimer(CosmeticsPlugin.getInstance(), () -> {
            if (!player.isOnline() || player.isDead()) {
                removeEffect(player);
                return;
            }
            spawnParticles(player);
        }, 0L, 1L).getTaskId();

        activeTasks.put(player.getUniqueId(), taskId);
    }

    @Override
    protected void removeEffect(Player player) {
        Integer taskId = activeTasks.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        playDeactivationSound(player);
    }

    public static void cleanup(UUID playerId) {
        Integer taskId = activeTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    @Override
    protected Material getDefaultDisplayMaterial() {
        return Material.BLAZE_ROD;
    }

    private void spawnParticles(Player player) {
        Particle particle = getParticle(getProperty("particle", "FLAME"));
        int count = getPropertyInt("particle-count", 20);
        double speed = getPropertyDouble("particle-speed", 0.2);
        Color color = getColor(getProperty("color", "FFFFFF"));
        double radius = CosmeticsPlugin.getInstance().getConfigManager().getConfig("config").getDouble("effects.aura-radius", 1.5);

        Location loc = player.getLocation().add(0, 1, 0);
        ParticleManager pm = getParticleManager();

        for (int i = 0; i < count; i++) {
            Vector offset = getRandomVector(radius);
            Location particleLoc = loc.clone().add(offset);
            pm.spawnParticle(particle, particleLoc, 1, 0, 0, 0, speed, color);
        }
    }
}