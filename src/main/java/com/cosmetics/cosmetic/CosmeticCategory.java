package com.cosmetics.cosmetic;

public enum CosmeticCategory {
    AURAS("Auras"),
    TRAILS("Trails"),
    WINGS("Wings"),
    HATS("Hats"),
    PETS("Pets"),
    KILLSOUNDS("Kill Sounds"),
    COMPANIONS("Companions");

    private final String displayName;

    CosmeticCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}