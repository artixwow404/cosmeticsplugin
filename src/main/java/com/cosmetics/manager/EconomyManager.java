package com.cosmetics.manager;

import com.cosmetics.CosmeticsPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {

    private final CosmeticsPlugin plugin;
    private Economy vaultEconomy;
    private boolean playerPointsEnabled = false;
    private Object playerPointsAPI = null;

    public EconomyManager(CosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        setupVault();
        setupPlayerPoints();
    }

    private void setupVault() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            vaultEconomy = rsp.getProvider();
            plugin.getLogger().info("Vault economy hooked successfully!");
        } else {
            plugin.getLogger().warning("Vault not found! Coins economy will not work.");
        }
    }

    private void setupPlayerPoints() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")) {
            try {
                Class<?> apiClass = Class.forName("com.wasteofplastic.playerpoints.PlayerPoints");
                java.lang.reflect.Method getAPI = apiClass.getMethod("getAPI");
                playerPointsAPI = getAPI.invoke(null);
                playerPointsEnabled = true;
                plugin.getLogger().info("PlayerPoints API hooked successfully!");
            } catch (Exception e) {
                plugin.getLogger().warning("PlayerPoints found but API access failed: " + e.getMessage());
            }
        } else {
            plugin.getLogger().warning("PlayerPoints not found! DonateCoins economy will not work.");
        }
    }

    public double getCoinsBalance(Player player) {
        if (vaultEconomy == null) return 0;
        return vaultEconomy.getBalance(player);
    }

    public double getDonateCoinsBalance(Player player) {
        if (!playerPointsEnabled || playerPointsAPI == null) return 0;
        try {
            java.lang.reflect.Method getPoints = playerPointsAPI.getClass().getMethod("getPoints", String.class);
            Object result = getPoints.invoke(playerPointsAPI, player.getName());
            if (result instanceof Number) {
                return ((Number) result).doubleValue();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get PlayerPoints balance: " + e.getMessage());
        }
        return 0;
    }

    public boolean hasCoins(Player player, double amount) {
        return getCoinsBalance(player) >= amount;
    }

    public boolean hasDonateCoins(Player player, double amount) {
        return getDonateCoinsBalance(player) >= amount;
    }

    public boolean withdrawCoins(Player player, double amount) {
        if (vaultEconomy == null) return false;
        return vaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean withdrawDonateCoins(Player player, double amount) {
        if (!playerPointsEnabled || playerPointsAPI == null) return false;
        try {
            java.lang.reflect.Method takePoints = playerPointsAPI.getClass().getMethod("takePoints", String.class, int.class);
            Object result = takePoints.invoke(playerPointsAPI, player.getName(), (int) amount);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to withdraw PlayerPoints: " + e.getMessage());
        }
        return false;
    }

    public boolean depositCoins(Player player, double amount) {
        if (vaultEconomy == null) return false;
        return vaultEconomy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean depositDonateCoins(Player player, double amount) {
        if (!playerPointsEnabled || playerPointsAPI == null) return false;
        try {
            java.lang.reflect.Method givePoints = playerPointsAPI.getClass().getMethod("givePoints", String.class, int.class);
            Object result = givePoints.invoke(playerPointsAPI, player.getName(), (int) amount);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deposit PlayerPoints: " + e.getMessage());
        }
        return false;
    }

    public boolean withdraw(Player player, String currency, double amount) {
        return switch (currency.toUpperCase()) {
            case "COINS" -> withdrawCoins(player, amount);
            case "DONATECOINS" -> withdrawDonateCoins(player, amount);
            default -> false;
        };
    }

    public double getBalance(Player player, String currency) {
        return switch (currency.toUpperCase()) {
            case "COINS" -> getCoinsBalance(player);
            case "DONATECOINS" -> getDonateCoinsBalance(player);
            default -> 0;
        };
    }

    public boolean hasBalance(Player player, String currency, double amount) {
        return switch (currency.toUpperCase()) {
            case "COINS" -> hasCoins(player, amount);
            case "DONATECOINS" -> hasDonateCoins(player, amount);
            default -> false;
        };
    }

    public String getCurrencySymbol(String currency) {
        return switch (currency.toUpperCase()) {
            case "COINS" -> plugin.getConfigManager().getConfig("messages").getString("currency.coins-symbol", "Coins");
            case "DONATECOINS" -> plugin.getConfigManager().getConfig("messages").getString("currency.donatecoins-symbol", "DonateCoins");
            default -> currency;
        };
    }

    public String getCurrencyColor(String currency) {
        return switch (currency.toUpperCase()) {
            case "COINS" -> "&e";
            case "DONATECOINS" -> "&d";
            default -> "&7";
        };
    }

    public boolean isVaultAvailable() {
        return vaultEconomy != null;
    }

    public boolean isPlayerPointsAvailable() {
        return playerPointsEnabled;
    }
}