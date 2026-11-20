package com.cosmetics.cosmetic;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.manager.SoundManager;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

public class KillSoundCosmetic extends Cosmetic {

    public KillSoundCosmetic(String id, String name, String description, CosmeticRarity rarity,
                             String currency, double price, JsonObject properties) {
        super(CosmeticCategory.KILLSOUNDS, id, name, description, rarity, currency, price, properties);
    }

    @Override
    protected void applyEffect(Player player) {
        SoundManager soundManager = getSoundManager();
        soundManager.setPlayerKillSound(player.getUniqueId(), this);
        playActivationSound(player);
    }

    @Override
    protected void removeEffect(Player player) {
        SoundManager soundManager = getSoundManager();
        soundManager.removePlayerKillSound(player.getUniqueId());
        playDeactivationSound(player);
    }

    @Override
    protected Material getDefaultDisplayMaterial() {
        return Material.NOTE_BLOCK;
    }

    public String getSound() {
        return getProperty("sound", "ENTITY_PLAYER_LEVELUP");
    }

    public float getVolume() {
        return (float) getPropertyDouble("volume", 1.0);
    }

    public float getPitch() {
        return (float) getPropertyDouble("pitch", 1.0);
    }

    public int getRadius() {
        return getPropertyInt("radius", 50);
    }
}