package com.cosmetics.cosmetic;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.manager.PetManager;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PetCosmetic extends Cosmetic {

    public PetCosmetic(String id, String name, String description, CosmeticRarity rarity,
                       String currency, double price, JsonObject properties) {
        super(CosmeticCategory.PETS, id, name, description, rarity, currency, price, properties);
    }

    @Override
    protected void applyEffect(Player player) {
        PetManager petManager = getPetManager();
        petManager.spawnPet(player, this);
        playActivationSound(player);
    }

    @Override
    protected void removeEffect(Player player) {
        PetManager petManager = getPetManager();
        petManager.despawnPet(player);
        playDeactivationSound(player);
    }

    @Override
    protected Material getDefaultDisplayMaterial() {
        return Material.WOLF_SPAWN_EGG;
    }

    public EntityType getEntityType() {
        try {
            return EntityType.valueOf(getProperty("entity-type", "WOLF").toUpperCase());
        } catch (IllegalArgumentException e) {
            return EntityType.WOLF;
        }
    }

    public String getVariant() {
        return getProperty("variant", "DEFAULT");
    }

    public double getScale() {
        return getPropertyDouble("scale", 1.0);
    }
}