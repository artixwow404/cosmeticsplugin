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

public class WingCosmetic extends Cosmetic {

    private static final ConcurrentHashMap<UUID, Integer> activeTasks = new ConcurrentHashMap<>();

    public WingCosmetic(String id, String name, String description, CosmeticRarity rarity,
                        String currency, double price, JsonObject properties) {
        super(CosmeticCategory.WINGS, id, name, description, rarity, currency, price, properties);
    }

    @Override
    protected void applyEffect(Player player) {
        playActivationSound(player);
        int taskId = Bukkit.getScheduler().runTaskTimer(CosmeticsPlugin.getInstance(), () -> {
            if (!player.isOnline() || player.isDead()) {
                removeEffect(player);
                return;
            }
            spawnWingParticles(player);
        }, 0L, 2L).getTaskId();

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
        return Material.ELYTRA;
    }

    private void spawnWingParticles(Player player) {
        Particle particle = getParticle(getProperty("particle", "END_ROD"));
        Color color = getColor(getProperty("color", "FFFFFF"));
        double flapSpeed = CosmeticsPlugin.getInstance().getConfigManager().getConfig("config").getDouble("effects.wing-flap-speed", 0.3);
        double time = System.currentTimeMillis() * flapSpeed;

        Location loc = player.getLocation().add(0, 1.5, 0);
        Vector direction = player.getLocation().getDirection().normalize();
        Vector right = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

        ParticleManager pm = getParticleManager();

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 5; i++) {
                double wingOffset = i * 0.3;
                double flap = Math.sin(time + i * 0.5) * 0.4;
                Vector offset = right.clone().multiply(side * wingOffset)
                    .add(new Vector(0, flap, 0))
                    .add(direction.clone().multiply(-i * 0.2));
                Location particleLoc = loc.clone().add(offset);
                pm.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0, color);
            }
        }
    }
}