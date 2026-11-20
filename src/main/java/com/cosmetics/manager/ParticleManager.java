package com.cosmetics.manager;

import com.cosmetics.CosmeticsPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Collection;

public class ParticleManager {

    private final CosmeticsPlugin plugin;
    private int maxParticlesPerTick;

    public ParticleManager(CosmeticsPlugin plugin) {
        this.plugin = plugin;
        this.maxParticlesPerTick = plugin.getConfigManager().getConfig("config").getInt("general.max-particles-per-tick", 50);
    }

    public void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double speed, Color color) {
        if (count <= 0) return;

        try {
            if (color != null && (particle == Particle.DUST || particle == Particle.END_ROD || particle == Particle.ELECTRIC_SPARK)) {
                Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0f);
                location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed, dustOptions);
            } else {
                location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn particle: " + e.getMessage());
        }
    }

    public void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed, null);
    }

    public void updateAll() {
    }

    public void cleanup() {
    }

    public int getMaxParticlesPerTick() {
        return maxParticlesPerTick;
    }
}