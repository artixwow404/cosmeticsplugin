package com.cosmetics.cosmetic;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.manager.PetManager;
import com.cosmetics.util.ItemUtil;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CompanionCosmetic extends Cosmetic {

    private static final ConcurrentHashMap<UUID, Integer> activeTasks = new ConcurrentHashMap<>();

    public CompanionCosmetic(String id, String name, String description, CosmeticRarity rarity,
                             String currency, double price, JsonObject properties) {
        super(CosmeticCategory.COMPANIONS, id, name, description, rarity, currency, price, properties);
    }

    @Override
    protected void applyEffect(Player player) {
        playActivationSound(player);
        int taskId = Bukkit.getScheduler().runTaskTimer(CosmeticsPlugin.getInstance(), () -> {
            if (!player.isOnline() || player.isDead()) {
                removeEffect(player);
                return;
            }
            updateCompanion(player);
        }, 0L, 1L).getTaskId();

        activeTasks.put(player.getUniqueId(), taskId);
    }

    @Override
    protected void removeEffect(Player player) {
        Integer taskId = activeTasks.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        PetManager petManager = CosmeticsPlugin.getInstance().getPetManager();
        petManager.despawnCompanion(player.getUniqueId());
        playDeactivationSound(player);
    }

    public static void cleanup(UUID playerId) {
        Integer taskId = activeTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void updateCompanion(Player player) {
        Particle particle = getParticle(getProperty("particle", "END_ROD"));
        Color color = getColor(getProperty("color", "FFFFFF"));
        double orbitRadius = getPropertyDouble("orbit-radius", 2.0);
        double orbitSpeed = getPropertyDouble("orbit-speed", 0.05);
        double scale = getPropertyDouble("scale", 0.3);

        double time = System.currentTimeMillis() * orbitSpeed;
        double angle = time + player.getUniqueId().hashCode() * 0.1;

        Location loc = player.getLocation().add(0, 1.5, 0);
        Vector offset = new Vector(
            Math.cos(angle) * orbitRadius,
            Math.sin(time * 0.5) * 0.5 + 0.5,
            Math.sin(angle) * orbitRadius
        );

        Location companionLoc = loc.clone().add(offset);
        PetManager petManager = CosmeticsPlugin.getInstance().getPetManager();
        petManager.updateCompanionLocation(player.getUniqueId(), companionLoc);

        getParticleManager().spawnParticle(particle, companionLoc, 2, 0.1, 0.1, 0.1, 0, color);
    }

    /**
     * Gets the ItemsAdder item string for this companion (if configured).
     * Format: "itemsadder:namespace:item_id" or "base64:..." or a regular material.
     * Returns null if no custom item is configured.
     */
    public String getItemString() {
        String itemStr = getProperty("item", "");
        return itemStr.isEmpty() ? null : itemStr;
    }

    /**
     * Gets the parsed ItemStack for this companion's display item.
     * Returns null if no custom item is configured or parsing fails.
     */
    public ItemStack getDisplayItem() {
        String itemString = getItemString();
        if (itemString == null) return null;
        return ItemUtil.parseItem(itemString, Material.END_ROD);
    }

    public EntityType getEntityType() {
        try {
            return EntityType.valueOf(getProperty("entity-type", "ITEM_DISPLAY").toUpperCase());
        } catch (IllegalArgumentException e) {
            return EntityType.ITEM_DISPLAY;
        }
    }

    public double getScale() {
        return getPropertyDouble("scale", 0.3);
    }

    /**
     * Checks if this companion uses a custom ItemsAdder item (ItemDisplay).
     */
    public boolean usesCustomItemDisplay() {
        return getItemString() != null;
    }

    @Override
    protected Material getDefaultDisplayMaterial() {
        return Material.PHANTOM_MEMBRANE;
    }
}