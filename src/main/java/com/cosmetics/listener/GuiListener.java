package com.cosmetics.listener;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.cosmetic.Cosmetic;
import com.cosmetics.cosmetic.CosmeticCategory;
import com.cosmetics.data.DatabaseManager;
import com.cosmetics.manager.GuiManager;
import com.cosmetics.manager.SoundManager;
import com.cosmetics.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class GuiListener implements Listener {

    private final CosmeticsPlugin plugin;

    public GuiListener(CosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;

        String title = event.getView().getTitle();
        if (!MessageUtil.stripColor(title).contains("Cosmetics") &&
            !MessageUtil.stripColor(title).contains("Confirm") &&
            !MessageUtil.stripColor(title).contains("Preview")) {
            return;
        }

        // Cancel ALL click types in cosmetics menus
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String cosmeticId = meta.getPersistentDataContainer().get(
            new org.bukkit.NamespacedKey(plugin, "cosmetic_id"), PersistentDataType.STRING);

        if (cosmeticId != null) {
            handleCosmeticClick(player, cosmeticId, event.isLeftClick(), event.isRightClick());
            return;
        }

        handleSpecialClicks(player, meta, title);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (!MessageUtil.stripColor(title).contains("Cosmetics") &&
            !MessageUtil.stripColor(title).contains("Confirm") &&
            !MessageUtil.stripColor(title).contains("Preview")) {
            return;
        }

        // Cancel ALL drag operations in cosmetics menus
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (!MessageUtil.stripColor(title).contains("Cosmetics") &&
            !MessageUtil.stripColor(title).contains("Confirm") &&
            !MessageUtil.stripColor(title).contains("Preview")) {
            return;
        }

        // Cancel creative inventory interactions
        event.setCancelled(true);
    }

    private void handleCosmeticClick(Player player, String cosmeticId, boolean leftClick, boolean rightClick) {
        Cosmetic cosmetic = plugin.getCosmeticsManager().getCosmetic(cosmeticId);
        if (cosmetic == null) return;

        GuiManager.GuiSession session = plugin.getGuiManager().getSession(player.getUniqueId());
        DatabaseManager.PlayerCosmeticData data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        boolean owned = data.owns(cosmetic.getCategory().name().toLowerCase(), cosmetic.getId());
        boolean equipped = cosmetic.getId().equals(data.getActive(cosmetic.getCategory().name().toLowerCase()));

        if (rightClick) {
            plugin.getGuiManager().openPreviewMenu(player, cosmetic);
            plugin.getSoundManager().playPreviewSound(player);
            return;
        }

        if (!owned) {
            plugin.getGuiManager().openConfirmationMenu(player, cosmetic);
            return;
        }

        if (equipped) {
            plugin.getCosmeticsManager().unequipCosmetic(player.getUniqueId(), cosmetic.getCategory());
            plugin.getSoundManager().playDeactivationSound(player);
            plugin.getGuiManager().openCategoryMenu(player, session, cosmetic.getCategory(), session.getCurrentPage());
        } else {
            String currentActive = data.getActive(cosmetic.getCategory().name().toLowerCase());
            if (currentActive != null && !currentActive.isEmpty()) {
                Cosmetic current = plugin.getCosmeticsManager().getCosmetic(cosmetic.getCategory(), currentActive);
                if (current != null) {
                    current.onUnequip(player.getUniqueId());
                }
            }
            plugin.getCosmeticsManager().equipCosmetic(player.getUniqueId(), cosmetic);
            plugin.getSoundManager().playActivationSound(player, cosmetic.getCategory());
            plugin.getGuiManager().openCategoryMenu(player, session, cosmetic.getCategory(), session.getCurrentPage());
        }
    }

    private void handleSpecialClicks(Player player, ItemMeta meta, String title) {
        GuiManager.GuiSession session = plugin.getGuiManager().getSession(player.getUniqueId());
        if (session == null) return;

        // Check for category click (main menu)
        if (meta.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "category"), PersistentDataType.STRING)) {
            String categoryName = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "category"), PersistentDataType.STRING);
            try {
                CosmeticCategory category = CosmeticCategory.valueOf(categoryName);
                plugin.getGuiManager().openCategoryMenu(player, session, category, 1);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid category in GUI: " + categoryName);
            }
            return;
        }

        if (meta.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "gui_action"), PersistentDataType.STRING)) {
            String action = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "gui_action"), PersistentDataType.STRING);

            switch (action) {
                case "previous_page" -> {
                    if (session.getCurrentPage() > 1) {
                        plugin.getGuiManager().openCategoryMenu(player, session, session.getCurrentCategory(), session.getCurrentPage() - 1);
                    }
                }
                case "next_page" -> {
                    plugin.getGuiManager().openCategoryMenu(player, session, session.getCurrentCategory(), session.getCurrentPage() + 1);
                }
                case "back" -> {
                    plugin.getGuiManager().openMainMenu(player);
                }
                case "clear_all" -> {
                    plugin.getCosmeticsManager().clearAllCosmetics(player.getUniqueId());
                    player.sendMessage(MessageUtil.getMessage("clear.success"));
                    plugin.getSoundManager().playDeactivationSound(player);
                    plugin.getGuiManager().openMainMenu(player);
                }
                case "confirm_purchase" -> {
                    handlePurchaseConfirmation(player, session);
                }
                case "cancel_purchase" -> {
                    session.clearPendingPurchase();
                    plugin.getGuiManager().openCategoryMenu(player, session, session.getCurrentCategory(), session.getCurrentPage());
                }
                case "close_preview" -> {
                    plugin.getGuiManager().openCategoryMenu(player, session, session.getCurrentCategory(), session.getCurrentPage());
                }
            }
        }
    }

    private void handlePurchaseConfirmation(Player player, GuiManager.GuiSession session) {
        String cosmeticId = session.getPendingPurchase();
        if (cosmeticId == null) return;

        Cosmetic cosmetic = plugin.getCosmeticsManager().getCosmetic(cosmeticId);
        if (cosmetic == null) return;

        // Re-check balance at confirmation time (bulletproof)
        if (!plugin.getEconomyManager().hasBalance(player, cosmetic.getCurrency(), cosmetic.getPrice())) {
            double balance = plugin.getEconomyManager().getBalance(player, cosmetic.getCurrency());
            Map<String, String> placeholders = Map.of(
                "price", String.valueOf((int) cosmetic.getPrice()),
                "currency", cosmetic.getCurrency(),
                "balance", String.valueOf((int) balance)
            );
            player.sendMessage(MessageUtil.getMessage("purchase.insufficient-funds", placeholders));
            plugin.getSoundManager().playPurchaseFailSound(player);
            return;
        }

        boolean success = plugin.getEconomyManager().withdraw(player, cosmetic.getCurrency(), cosmetic.getPrice());
        if (!success) {
            player.sendMessage(MessageUtil.getMessage("purchase.purchase-failed"));
            plugin.getSoundManager().playPurchaseFailSound(player);
            return;
        }

        boolean purchased = plugin.getCosmeticsManager().purchaseCosmetic(player.getUniqueId(), cosmetic);
        if (!purchased) {
            plugin.getEconomyManager().depositCoins(player, cosmetic.getPrice());
            player.sendMessage(MessageUtil.getMessage("purchase.purchase-failed"));
            plugin.getSoundManager().playPurchaseFailSound(player);
            return;
        }

        plugin.getCosmeticsManager().equipCosmetic(player.getUniqueId(), cosmetic);
        plugin.getSoundManager().playPurchaseSuccessSound(player);

        Map<String, String> placeholders = Map.of(
            "name", cosmetic.getName(),
            "price", String.valueOf((int) cosmetic.getPrice()),
            "currency", cosmetic.getCurrency()
        );
        player.sendMessage(MessageUtil.getMessage("purchase.success", placeholders));

        session.clearPendingPurchase();
        plugin.getGuiManager().openCategoryMenu(player, session, session.getCurrentCategory(), session.getCurrentPage());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (MessageUtil.stripColor(title).contains("Cosmetics") ||
            MessageUtil.stripColor(title).contains("Confirm") ||
            MessageUtil.stripColor(title).contains("Preview")) {
            plugin.getGuiManager().removeSession(player.getUniqueId());
        }
    }
}
