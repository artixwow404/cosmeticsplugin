package com.cosmetics.cosmetic;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.manager.ParticleManager;
import com.cosmetics.util.ItemUtil;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HatCosmetic extends Cosmetic {

    private static final ConcurrentHashMap<UUID, Integer> activeTasks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ItemStack> previousHelmets = new ConcurrentHashMap<>();

    public HatCosmetic(String id, String name, String description, CosmeticRarity rarity,
                       String currency, double price, JsonObject properties) {
        super(CosmeticCategory.HATS, id, name, description, rarity, currency, price, properties);
    }

    @Override
    protected void applyEffect(Player player) {
        playActivationSound(player);

        // Try to equip ItemsAdder/Oraxen hat item on helmet slot
        String itemString = getProperty("item", "");
        if (!itemString.isEmpty()) {
            ItemStack hatItem = ItemUtil.parseItem(itemString, Material.PLAYER_HEAD);
            if (hatItem != null && hatItem.getType() != Material.AIR) {
                // Store previous helmet
                ItemStack previous = player.getInventory().getHelmet();
                if (previous != null && previous.getType() != Material.AIR) {
                    previousHelmets.put(player.getUniqueId(), previous.clone());
                }
                // Equip the hat
                player.getInventory().setHelmet(hatItem.clone());
            }
        }

        int taskId = Bukkit.getScheduler().runTaskTimer(CosmeticsPlugin.getInstance(), () -> {
            if (!player.isOnline() || player.isDead()) {
                removeEffect(player);
                return;
            }
            spawnHatParticles(player);
        }, 0L, 3L).getTaskId();

        activeTasks.put(player.getUniqueId(), taskId);
    }

    @Override
    protected void removeEffect(Player player) {
        Integer taskId = activeTasks.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        // Restore previous helmet
        ItemStack previous = previousHelmets.remove(player.getUniqueId());
        if (previous != null) {
            player.getInventory().setHelmet(previous);
        } else {
            player.getInventory().setHelmet(null);
        }

        playDeactivationSound(player);
    }

    public static void cleanup(UUID playerId) {
        Integer taskId = activeTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        previousHelmets.remove(playerId);
    }

    private void spawnHatParticles(Player player) {
        Particle particle = getParticle(getProperty("particle", "END_ROD"));
        Color color = getColor(getProperty("color", "FFFFFF"));
        String model = getProperty("model", "");

        Location loc = player.getLocation().add(0, player.getHeight() + 0.2, 0);
        ParticleManager pm = getParticleManager();

        switch (model.toLowerCase()) {
            case "crown", "king_crown", "emperor_crown", "gold_crown", "ice_crown", "fire_crown", "crown_thorns" -> {
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    Vector offset = new Vector(Math.cos(angle) * 0.3, 0.1 + Math.sin(System.currentTimeMillis() * 0.005 + i) * 0.05, Math.sin(angle) * 0.3);
                    pm.spawnParticle(particle, loc.clone().add(offset), 1, 0, 0, 0, 0, color);
                }
            }
            case "halo" -> {
                double angle = System.currentTimeMillis() * 0.002;
                double radius = 0.4;
                Vector offset = new Vector(Math.cos(angle) * radius, 0.3, Math.sin(angle) * radius);
                pm.spawnParticle(Particle.END_ROD, loc.clone().add(offset), 2, 0.1, 0.1, 0.1, 0, color);
            }
            case "wizard", "witch" -> {
                pm.spawnParticle(Particle.ENCHANT, loc.clone().add(0, 0.2, 0), 5, 0.2, 0.3, 0.2, 0.1, color);
            }
            default -> {
                pm.spawnParticle(particle, loc, 3, 0.2, 0.1, 0.2, 0.05, color);
            }
        }
    }

    @Override
    protected Material getDefaultDisplayMaterial() {
        return Material.CARVED_PUMPKIN;
    }
}