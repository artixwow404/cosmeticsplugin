package com.cosmetics.cosmetic;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.manager.ParticleManager;
import com.google.gson.JsonObject;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TrailCosmetic extends Cosmetic implements Listener {

    private static final ConcurrentHashMap<UUID, TrailData> activeTrails = new ConcurrentHashMap<>();

    public TrailCosmetic(String id, String name, String description, CosmeticRarity rarity,
                         String currency, double price, JsonObject properties) {
        super(CosmeticCategory.TRAILS, id, name, description, rarity, currency, price, properties);
    }

    @Override
    protected void applyEffect(Player player) {
        playActivationSound(player);
        activeTrails.put(player.getUniqueId(), new TrailData(player.getLocation()));
        CosmeticsPlugin.getInstance().getServer().getPluginManager().registerEvents(this, CosmeticsPlugin.getInstance());
    }

    @Override
    protected void removeEffect(Player player) {
        activeTrails.remove(player.getUniqueId());
        playDeactivationSound(player);
    }

    public static void cleanup(UUID playerId) {
        activeTrails.remove(playerId);
    }

    @Override
    protected Material getDefaultDisplayMaterial() {
        return Material.FEATHER;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        TrailData data = activeTrails.get(player.getUniqueId());
        if (data == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        data.ticksSinceLastSpawn++;
        int interval = getPropertyInt("interval", 2);
        if (data.ticksSinceLastSpawn >= interval) {
            data.ticksSinceLastSpawn = 0;
            spawnTrailParticles(player);
        }
    }

    private void spawnTrailParticles(Player player) {
        Particle particle = getParticle(getProperty("particle", "FLAME"));
        int count = getPropertyInt("particle-count", 5);
        Color color = getColor(getProperty("color", "FFFFFF"));

        Location loc = player.getLocation().subtract(0, 0.5, 0);
        ParticleManager pm = getParticleManager();

        for (int i = 0; i < count; i++) {
            Vector offset = new Vector(
                (Math.random() - 0.5) * 0.5,
                Math.random() * 0.2,
                (Math.random() - 0.5) * 0.5
            );
            Location particleLoc = loc.clone().add(offset);
            pm.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0.1, color);
        }
    }

    private static class TrailData {
        Location lastLocation;
        int ticksSinceLastSpawn = 0;

        TrailData(Location loc) {
            this.lastLocation = loc.clone();
        }
    }
}