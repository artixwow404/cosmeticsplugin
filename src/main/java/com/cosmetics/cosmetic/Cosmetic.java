package com.cosmetics.cosmetic;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.manager.ParticleManager;
import com.cosmetics.manager.PetManager;
import com.cosmetics.manager.SoundManager;
import com.cosmetics.util.ItemUtil;
import com.cosmetics.util.MessageUtil;
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

public abstract class Cosmetic {

    private final CosmeticCategory category;
    private final String id;
    private final String name;
    private final String description;
    private final CosmeticRarity rarity;
    private final String currency;
    private final double price;
    private final JsonObject properties;

    protected Cosmetic(CosmeticCategory category, String id, String name, String description,
                       CosmeticRarity rarity, String currency, double price, JsonObject properties) {
        this.category = category;
        this.id = id;
        this.name = name;
        this.description = description;
        this.rarity = rarity;
        this.currency = currency;
        this.price = price;
        this.properties = properties;
    }

    public CosmeticCategory getCategory() { return category; }
    public String getId() { return id; }
    public String getFullId() { return category.name().toLowerCase() + "." + id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CosmeticRarity getRarity() { return rarity; }
    public String getCurrency() { return currency; }
    public double getPrice() { return price; }
    public JsonObject getProperties() { return properties; }

    public String getProperty(String key, String defaultValue) {
        return properties.has(key) ? properties.get(key).getAsString() : defaultValue;
    }

    public int getPropertyInt(String key, int defaultValue) {
        return properties.has(key) ? properties.get(key).getAsInt() : defaultValue;
    }

    public double getPropertyDouble(String key, double defaultValue) {
        return properties.has(key) ? properties.get(key).getAsDouble() : defaultValue;
    }

    public boolean getPropertyBoolean(String key, boolean defaultValue) {
        return properties.has(key) ? properties.get(key).getAsBoolean() : defaultValue;
    }

    public void onEquip(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;
        applyEffect(player);
    }

    public void onUnequip(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;
        removeEffect(player);
    }

    protected abstract void applyEffect(Player player);
    protected abstract void removeEffect(Player player);

    /**
     * Gets the item string for this cosmetic's display item (itemsadder:, oraxen:, base64:, or vanilla material).
     * Returns null if no custom item is configured.
     */
    public String getItemString() {
        String itemStr = getProperty("item", "");
        return itemStr.isEmpty() ? null : itemStr;
    }

    /**
     * Gets the parsed ItemStack for this cosmetic's display item.
     * Returns a fallback material if no custom item is configured or parsing fails.
     */
    public ItemStack getDisplayItem() {
        String itemString = getItemString();
        if (itemString == null) return null;
        return ItemUtil.parseItem(itemString, getDefaultDisplayMaterial());
    }

    /**
     * Override in subclasses to provide a category-appropriate default material.
     */
    protected Material getDefaultDisplayMaterial() {
        return Material.PAPER;
    }

    public String getRarityColor() {
        return switch (rarity) {
            case COMMON -> "&7";
            case RARE -> "&9";
            case EPIC -> "&5";
            case LEGENDARY -> "&6";
            case MYTHIC -> "&c&l";
        };
    }

    public String getRarityName() {
        return rarity.name();
    }

    protected ParticleManager getParticleManager() {
        return CosmeticsPlugin.getInstance().getParticleManager();
    }

    protected PetManager getPetManager() {
        return CosmeticsPlugin.getInstance().getPetManager();
    }

    protected SoundManager getSoundManager() {
        return CosmeticsPlugin.getInstance().getSoundManager();
    }

    protected void playActivationSound(Player player) {
        getSoundManager().playActivationSound(player, category);
    }

    protected void playDeactivationSound(Player player) {
        getSoundManager().playDeactivationSound(player);
    }

    protected Particle getParticle(String particleName) {
        try {
            return Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Particle.HAPPY_VILLAGER;
        }
    }

    protected Color getColor(String hexColor) {
        try {
            String hex = hexColor.replace("#", "");
            if (hex.length() == 6) {
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                return Color.fromRGB(r, g, b);
            }
        } catch (Exception ignored) {}
        return Color.WHITE;
    }

    protected Vector getRandomVector(double radius) {
        double angle = Math.random() * 2 * Math.PI;
        double r = Math.random() * radius;
        return new Vector(r * Math.cos(angle), Math.random() * radius, r * Math.sin(angle));
    }
}