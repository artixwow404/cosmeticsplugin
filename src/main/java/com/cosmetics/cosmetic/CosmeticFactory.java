package com.cosmetics.cosmetic;

import com.google.gson.JsonObject;

public class CosmeticFactory {

    public static Cosmetic createCosmetic(CosmeticCategory category, String id, String name, String description,
                                           CosmeticRarity rarity, String currency, double price, JsonObject properties) {
        return switch (category) {
            case AURAS -> new AuraCosmetic(id, name, description, rarity, currency, price, properties);
            case TRAILS -> new TrailCosmetic(id, name, description, rarity, currency, price, properties);
            case WINGS -> new WingCosmetic(id, name, description, rarity, currency, price, properties);
            case HATS -> new HatCosmetic(id, name, description, rarity, currency, price, properties);
            case PETS -> new PetCosmetic(id, name, description, rarity, currency, price, properties);
            case KILLSOUNDS -> new KillSoundCosmetic(id, name, description, rarity, currency, price, properties);
            case COMPANIONS -> new CompanionCosmetic(id, name, description, rarity, currency, price, properties);
        };
    }
}