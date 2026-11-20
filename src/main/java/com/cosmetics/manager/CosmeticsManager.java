package com.cosmetics.manager;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.cosmetic.Cosmetic;
import com.cosmetics.cosmetic.CosmeticCategory;
import com.cosmetics.cosmetic.CosmeticFactory;
import com.cosmetics.cosmetic.CosmeticRarity;
import com.cosmetics.data.DatabaseManager;
import com.google.gson.JsonObject;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CosmeticsManager {

    private final CosmeticsPlugin plugin;
    private final Map<String, Cosmetic> cosmetics = new ConcurrentHashMap<>();
    private final Map<CosmeticCategory, Map<String, Cosmetic>> cosmeticsByCategory = new EnumMap<>(CosmeticCategory.class);

    public CosmeticsManager(CosmeticsPlugin plugin) {
        this.plugin = plugin;
        for (CosmeticCategory category : CosmeticCategory.values()) {
            cosmeticsByCategory.put(category, new ConcurrentHashMap<>());
        }
    }

    public void loadCosmetics() {
        FileConfiguration config = plugin.getConfigManager().getConfig("cosmetics");
        FileConfiguration pricesConfig = plugin.getConfigManager().getConfig("prices");

        for (CosmeticCategory category : CosmeticCategory.values()) {
            String categoryKey = category.name().toLowerCase();
            if (!config.isConfigurationSection(categoryKey)) continue;

            for (String id : config.getConfigurationSection(categoryKey).getKeys(false)) {
                String basePath = categoryKey + "." + id;
                if (!config.getBoolean(basePath + ".enabled", true)) continue;

                Cosmetic cosmetic = parseCosmetic(category, id, basePath, config, pricesConfig);
                if (cosmetic != null) {
                    cosmetics.put(cosmetic.getFullId(), cosmetic);
                    cosmeticsByCategory.get(category).put(id, cosmetic);
                }
            }
        }

        plugin.getLogger().info("Loaded " + cosmetics.size() + " cosmetics across " + CosmeticCategory.values().length + " categories.");
    }

    private Cosmetic parseCosmetic(CosmeticCategory category, String id, String basePath, FileConfiguration config, FileConfiguration pricesConfig) {
        try {
            String name = config.getString(basePath + ".name", id);
            String description = config.getString(basePath + ".description", "");
            CosmeticRarity rarity = CosmeticRarity.valueOf(config.getString(basePath + ".rarity", "COMMON").toUpperCase());

            String pricePath = categoryKey(category) + "." + id;
            String currency = pricesConfig.getString(pricePath + ".currency", "COINS");
            double price = pricesConfig.getDouble(pricePath + ".price", 0);

            JsonObject properties = new JsonObject();
            for (String key : config.getConfigurationSection(basePath).getKeys(false)) {
                if (key.equals("name") || key.equals("description") || key.equals("category") || key.equals("rarity") || key.equals("enabled")) continue;
                Object value = config.get(basePath + "." + key);
                if (value != null) {
                    if (value instanceof String) properties.addProperty(key, (String) value);
                    else if (value instanceof Number) properties.addProperty(key, (Number) value);
                    else if (value instanceof Boolean) properties.addProperty(key, (Boolean) value);
                }
            }

            return CosmeticFactory.createCosmetic(category, id, name, description, rarity, currency, price, properties);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse cosmetic: " + categoryKey(category) + "." + id, e);
            return null;
        }
    }

    private String categoryKey(CosmeticCategory category) {
        return category.name().toLowerCase() + "s";
    }

    public Cosmetic getCosmetic(String fullId) {
        return cosmetics.get(fullId);
    }

    public Cosmetic getCosmetic(CosmeticCategory category, String id) {
        return cosmeticsByCategory.getOrDefault(category, Collections.emptyMap()).get(id);
    }

    public Map<String, Cosmetic> getCosmeticsByCategory(CosmeticCategory category) {
        return Collections.unmodifiableMap(cosmeticsByCategory.getOrDefault(category, Collections.emptyMap()));
    }

    public Collection<Cosmetic> getAllCosmetics() {
        return Collections.unmodifiableCollection(cosmetics.values());
    }

    public boolean purchaseCosmetic(UUID playerId, Cosmetic cosmetic) {
        DatabaseManager.PlayerCosmeticData data = plugin.getDatabaseManager().getCachedData(playerId);
        if (data == null) return false;

        if (data.owns(cosmetic.getCategory().name().toLowerCase(), cosmetic.getId())) {
            return false;
        }

        data.addOwned(cosmetic.getCategory().name().toLowerCase(), cosmetic.getId());
        plugin.getDatabaseManager().savePlayerData(playerId);
        return true;
    }

    public boolean equipCosmetic(UUID playerId, Cosmetic cosmetic) {
        DatabaseManager.PlayerCosmeticData data = plugin.getDatabaseManager().getCachedData(playerId);
        if (data == null) return false;

        if (!data.owns(cosmetic.getCategory().name().toLowerCase(), cosmetic.getId())) {
            return false;
        }

        String currentActive = data.getActive(cosmetic.getCategory().name().toLowerCase());
        if (currentActive != null && !currentActive.isEmpty() && !currentActive.equals(cosmetic.getId())) {
            Cosmetic current = getCosmetic(cosmetic.getCategory(), currentActive);
            if (current != null) {
                current.onUnequip(playerId);
            }
        }

        data.setActive(cosmetic.getCategory().name().toLowerCase(), cosmetic.getId());
        cosmetic.onEquip(playerId);
        plugin.getDatabaseManager().savePlayerData(playerId);
        return true;
    }

    public boolean unequipCosmetic(UUID playerId, CosmeticCategory category) {
        DatabaseManager.PlayerCosmeticData data = plugin.getDatabaseManager().getCachedData(playerId);
        if (data == null) return false;

        String currentActive = data.getActive(category.name().toLowerCase());
        if (currentActive == null || currentActive.isEmpty()) {
            return false;
        }

        Cosmetic current = getCosmetic(category, currentActive);
        if (current != null) {
            current.onUnequip(playerId);
        }

        data.clearActive(category.name().toLowerCase());
        plugin.getDatabaseManager().savePlayerData(playerId);
        return true;
    }

    public void clearAllCosmetics(UUID playerId) {
        DatabaseManager.PlayerCosmeticData data = plugin.getDatabaseManager().getCachedData(playerId);
        if (data == null) return;

        for (CosmeticCategory category : CosmeticCategory.values()) {
            String active = data.getActive(category.name().toLowerCase());
            if (active != null && !active.isEmpty()) {
                Cosmetic cosmetic = getCosmetic(category, active);
                if (cosmetic != null) {
                    cosmetic.onUnequip(playerId);
                }
            }
            data.clearActive(category.name().toLowerCase());
        }

        plugin.getDatabaseManager().savePlayerData(playerId);
    }

    public void saveAllPlayerData() {
        plugin.getDatabaseManager().saveAllPlayerData();
    }

    public void giveCosmetic(UUID playerId, Cosmetic cosmetic) {
        DatabaseManager.PlayerCosmeticData data = plugin.getDatabaseManager().getCachedData(playerId);
        if (data == null) return;

        data.addOwned(cosmetic.getCategory().name().toLowerCase(), cosmetic.getId());
        plugin.getDatabaseManager().savePlayerData(playerId);
    }

    public void removeCosmetic(UUID playerId, Cosmetic cosmetic) {
        DatabaseManager.PlayerCosmeticData data = plugin.getDatabaseManager().getCachedData(playerId);
        if (data == null) return;

        String active = data.getActive(cosmetic.getCategory().name().toLowerCase());
        if (active != null && active.equals(cosmetic.getId())) {
            cosmetic.onUnequip(playerId);
            data.clearActive(cosmetic.getCategory().name().toLowerCase());
        }

        data.removeOwned(cosmetic.getCategory().name().toLowerCase(), cosmetic.getId());
        plugin.getDatabaseManager().savePlayerData(playerId);
    }

    public void unlockAll(UUID playerId) {
        DatabaseManager.PlayerCosmeticData data = plugin.getDatabaseManager().getCachedData(playerId);
        if (data == null) return;

        for (Cosmetic cosmetic : cosmetics.values()) {
            data.addOwned(cosmetic.getCategory().name().toLowerCase(), cosmetic.getId());
        }

        plugin.getDatabaseManager().savePlayerData(playerId);
    }
}