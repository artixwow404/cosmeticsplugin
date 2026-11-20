package com.cosmetics.manager;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.cosmetic.Cosmetic;
import com.cosmetics.cosmetic.CosmeticCategory;
import com.cosmetics.cosmetic.CosmeticRarity;
import com.cosmetics.data.DatabaseManager;
import com.cosmetics.util.ItemUtil;
import com.cosmetics.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GuiManager {

    private final CosmeticsPlugin plugin;
    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();

    public GuiManager(CosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player) {
        GuiSession session = new GuiSession(player.getUniqueId());
        sessions.put(player.getUniqueId(), session);
        openMainCategoryMenu(player, session);
    }

    public void openMainCategoryMenu(Player player, GuiSession session) {
        FileConfiguration guiConfig = plugin.getConfigManager().getConfig("gui");
        String title = MessageUtil.color(guiConfig.getString("main-menu.title", "&8&lCosmetics Menu"));
        int rows = guiConfig.getInt("main-menu.rows", 6);
        Inventory inv = Bukkit.createInventory(null, rows * 9, title);

        session.setCurrentCategory(null); // Main menu has no category
        session.setCurrentPage(1);

        fillBackground(inv, guiConfig);

        // Add 7 category icons
        CosmeticCategory[] categories = CosmeticCategory.values();
        for (int i = 0; i < categories.length; i++) {
            CosmeticCategory category = categories[i];
            int slot = 10 + i * 3; // Slots: 10, 13, 16, 19, 22, 25, 28
            if (slot > 34) slot = 10 + ((i - 7) * 3); // Second row if needed
            inv.setItem(slot, createCategoryIcon(category));
        }

        addClearAllButton(inv, guiConfig);

        player.openInventory(inv);
    }

    private ItemStack createCategoryIcon(CosmeticCategory category) {
        ItemStack item = getCategoryItem(category);
        item = item.clone();
        ItemMeta meta = item.getItemMeta();
        String name = MessageUtil.color("&" + getCategoryColor(category) + "&l" + category.getDisplayName());
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.color("&7Click to view " + category.getDisplayName().toLowerCase()));
        lore.add("");
        lore.add(MessageUtil.color("&e" + plugin.getCosmeticsManager().getCosmeticsByCategory(category).size() + " items available"));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(plugin, "category"),
            org.bukkit.persistence.PersistentDataType.STRING,
            category.name()
        );
        item.setItemMeta(meta);
        return item;
    }

    private String getCategoryColor(CosmeticCategory category) {
        return switch (category) {
            case AURAS -> "c";
            case TRAILS -> "b";
            case WINGS -> "d";
            case HATS -> "e";
            case PETS -> "a";
            case KILLSOUNDS -> "c";
            case COMPANIONS -> "b";
        };
    }

    public void openCategoryMenu(Player player, GuiSession session, CosmeticCategory category, int page) {
        FileConfiguration guiConfig = plugin.getConfigManager().getConfig("gui");
        String title = MessageUtil.color(guiConfig.getString("main-menu.title", "&8&lCosmetics Menu"));
        int rows = guiConfig.getInt("main-menu.rows", 6);
        Inventory inv = Bukkit.createInventory(null, rows * 9, title);

        session.setCurrentCategory(category);
        session.setCurrentPage(page);

        fillBackground(inv, guiConfig);

        Map<String, Cosmetic> cosmetics = plugin.getCosmeticsManager().getCosmeticsByCategory(category);
        List<Cosmetic> cosmeticList = new ArrayList<>(cosmetics.values());
        cosmeticList.sort(Comparator.comparing((Cosmetic c) -> c.getRarity().ordinal()).reversed()
                .thenComparing(Cosmetic::getName));

        int perPage = 28;
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, cosmeticList.size());

        DatabaseManager.PlayerCosmeticData playerData = plugin.getDatabaseManager().getCachedData(player.getUniqueId());

        int slot = 0;
        for (int i = start; i < end; i++) {
            Cosmetic cosmetic = cosmeticList.get(i);
            if (slot >= 44) break;
            if (isFillerSlot(slot)) {
                slot++;
                i--;
                continue;
            }
            inv.setItem(slot, createCosmeticItem(cosmetic, playerData));
            slot++;
        }

        addPaginationControls(inv, guiConfig, page, (int) Math.ceil((double) cosmeticList.size() / perPage));
        addBackButton(inv, guiConfig);
        addClearAllButton(inv, guiConfig);

        player.openInventory(inv);
    }

    public void openConfirmationMenu(Player player, Cosmetic cosmetic) {
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        FileConfiguration guiConfig = plugin.getConfigManager().getConfig("gui");
        String title = MessageUtil.color(guiConfig.getString("confirmation-menu.title", "&8&lConfirm Purchase"));
        Inventory inv = Bukkit.createInventory(null, guiConfig.getInt("confirmation-menu.rows", 3) * 9, title);

        fillBackground(inv, guiConfig);

        String confirmName = MessageUtil.color(guiConfig.getString("confirmation-menu.confirm.name", "&a&lConfirm Purchase")
                .replace("%name%", cosmetic.getName()));
        List<String> confirmLore = new ArrayList<>();
        for (String line : guiConfig.getStringList("confirmation-menu.confirm.lore")) {
            confirmLore.add(MessageUtil.color(line
                    .replace("%name%", cosmetic.getName())
                    .replace("%price%", String.valueOf((int) cosmetic.getPrice()))
                    .replace("%currency%", cosmetic.getCurrency())));
        }
        String confirmMaterial = guiConfig.getString("confirmation-menu.confirm.material", "LIME_DYE");
        inv.setItem(guiConfig.getInt("confirmation-menu.confirm.slot", 11), createActionItem(confirmMaterial, confirmName, confirmLore, "confirm_purchase"));

        String cancelName = MessageUtil.color(guiConfig.getString("confirmation-menu.cancel.name", "&c&lCancel"));
        List<String> cancelLore = new ArrayList<>();
        for (String line : guiConfig.getStringList("confirmation-menu.cancel.lore")) {
            cancelLore.add(MessageUtil.color(line));
        }
        String cancelMaterial = guiConfig.getString("confirmation-menu.cancel.material", "RED_DYE");
        inv.setItem(guiConfig.getInt("confirmation-menu.cancel.slot", 15), createActionItem(cancelMaterial, cancelName, cancelLore, "cancel_purchase"));

        session.setPendingPurchase(cosmetic.getFullId());
        player.openInventory(inv);
    }

    public void openPreviewMenu(Player player, Cosmetic cosmetic) {
        FileConfiguration guiConfig = plugin.getConfigManager().getConfig("gui");
        String title = MessageUtil.color(guiConfig.getString("preview-menu.title", "&8&lPreview: %name%")
                .replace("%name%", cosmetic.getName()));
        Inventory inv = Bukkit.createInventory(null, guiConfig.getInt("preview-menu.rows", 3) * 9, title);

        fillBackground(inv, guiConfig);

        String closeName = MessageUtil.color(guiConfig.getString("preview-menu.close.name", "&cClose Preview"));
        List<String> closeLore = new ArrayList<>();
        for (String line : guiConfig.getStringList("preview-menu.close.lore")) {
            closeLore.add(MessageUtil.color(line));
        }
        String closeMaterial = guiConfig.getString("preview-menu.close.material", "BARRIER");
        inv.setItem(guiConfig.getInt("preview-menu.close.slot", 13), createActionItem(closeMaterial, closeName, closeLore, "close_preview"));

        player.openInventory(inv);
    }

    private void fillBackground(Inventory inv, FileConfiguration guiConfig) {
        String fillerMaterialStr = guiConfig.getString("main-menu.filler.material", "GRAY_STAINED_GLASS_PANE");
        String fillerName = MessageUtil.color(guiConfig.getString("main-menu.filler.name", " "));
        List<String> fillerLore = new ArrayList<>();
        for (String line : guiConfig.getStringList("main-menu.filler.lore")) {
            fillerLore.add(MessageUtil.color(line));
        }
        ItemStack filler = ItemUtil.createItem(fillerMaterialStr, fillerName, fillerLore);

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler.clone());
            }
        }
    }

    private boolean isFillerSlot(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        return col == 0 || col == 8 || row == 0 || row == 5;
    }

    private ItemStack createCosmeticItem(Cosmetic cosmetic, DatabaseManager.PlayerCosmeticData playerData) {
        boolean owned = playerData != null && playerData.owns(cosmetic.getCategory().name().toLowerCase(), cosmetic.getId());
        boolean equipped = playerData != null && cosmetic.getId().equals(playerData.getActive(cosmetic.getCategory().name().toLowerCase()));

        FileConfiguration guiConfig = plugin.getConfigManager().getConfig("gui");
        String nameFormat = owned ? guiConfig.getString("cosmetic-item.purchased.name-format", "&a✓ %name%")
                : guiConfig.getString("cosmetic-item.not-purchased.name-format", "&c✗ %name%");
        List<String> loreLines = owned ? guiConfig.getStringList("cosmetic-item.purchased.lore")
                : guiConfig.getStringList("cosmetic-item.not-purchased.lore");
        if (equipped) {
            nameFormat = guiConfig.getString("cosmetic-item.equipped.name-format", "&a✓ %name% &7(&aEquipped&7)");
            loreLines = guiConfig.getStringList("cosmetic-item.equipped.lore");
        }

        String name = MessageUtil.color(nameFormat
                .replace("%name%", cosmetic.getName())
                .replace("%rarity_color%", cosmetic.getRarityColor())
                .replace("%rarity%", cosmetic.getRarityName())
                .replace("%price%", String.valueOf((int) cosmetic.getPrice()))
                .replace("%currency_symbol%", plugin.getEconomyManager().getCurrencySymbol(cosmetic.getCurrency()))
                .replace("%currency_color%", plugin.getEconomyManager().getCurrencyColor(cosmetic.getCurrency())));

        List<String> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(MessageUtil.color(line
                    .replace("%name%", cosmetic.getName())
                    .replace("%rarity_color%", cosmetic.getRarityColor())
                    .replace("%rarity%", cosmetic.getRarityName())
                    .replace("%price%", String.valueOf((int) cosmetic.getPrice()))
                    .replace("%currency_symbol%", plugin.getEconomyManager().getCurrencySymbol(cosmetic.getCurrency()))
                    .replace("%currency_color%", plugin.getEconomyManager().getCurrencyColor(cosmetic.getCurrency()))));
        }

        // Use cosmetic's own display item if configured, otherwise fall back to category icon
        ItemStack item = cosmetic.getDisplayItem();
        if (item == null) {
            item = getCategoryItem(cosmetic.getCategory());
        }
        item = item.clone(); // Clone to avoid modifying the original
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "cosmetic_id"), org.bukkit.persistence.PersistentDataType.STRING, cosmetic.getFullId());
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "owned"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) (owned ? 1 : 0));
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "equipped"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) (equipped ? 1 : 0));
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack getCategoryItem(CosmeticCategory category) {
        FileConfiguration guiConfig = plugin.getConfigManager().getConfig("gui");
        String categoryKey = category.name().toLowerCase();
        // Try new categories section first, then fall back to category-items
        String materialStr = guiConfig.getString("categories." + categoryKey + ".material");
        if (materialStr == null) {
            materialStr = guiConfig.getString("category-items." + categoryKey + ".material", "DIAMOND");
        }
        return ItemUtil.parseItem(materialStr, Material.DIAMOND);
    }

    private void addPaginationControls(Inventory inv, FileConfiguration guiConfig, int currentPage, int totalPages) {
        if (totalPages <= 1) return;

        if (currentPage > 1) {
            String prevName = MessageUtil.color(guiConfig.getString("pagination.previous.name", "&ePrevious Page")
                    .replace("%prev_page%", String.valueOf(currentPage - 1)));
            List<String> prevLore = new ArrayList<>();
            for (String line : guiConfig.getStringList("pagination.previous.lore")) {
                prevLore.add(MessageUtil.color(line.replace("%prev_page%", String.valueOf(currentPage - 1))));
            }
            String prevMaterial = guiConfig.getString("pagination.previous.material", "ARROW");
            inv.setItem(guiConfig.getInt("pagination.previous.slot", 45), createActionItem(prevMaterial, prevName, prevLore, "previous_page"));
        }

        if (currentPage < totalPages) {
            String nextName = MessageUtil.color(guiConfig.getString("pagination.next.name", "&eNext Page")
                    .replace("%next_page%", String.valueOf(currentPage + 1)));
            List<String> nextLore = new ArrayList<>();
            for (String line : guiConfig.getStringList("pagination.next.lore")) {
                nextLore.add(MessageUtil.color(line.replace("%next_page%", String.valueOf(currentPage + 1))));
            }
            String nextMaterial = guiConfig.getString("pagination.next.material", "ARROW");
            inv.setItem(guiConfig.getInt("pagination.next.slot", 53), createActionItem(nextMaterial, nextName, nextLore, "next_page"));
        }

        String pageName = MessageUtil.color(guiConfig.getString("pagination.page-indicator.name", "&7Page %current% of %total%")
                .replace("%current%", String.valueOf(currentPage))
                .replace("%total%", String.valueOf(totalPages)));
        List<String> pageLore = new ArrayList<>();
        for (String line : guiConfig.getStringList("pagination.page-indicator.lore")) {
            pageLore.add(MessageUtil.color(line));
        }
        String pageMaterial = guiConfig.getString("pagination.page-indicator.material", "PAPER");
        inv.setItem(guiConfig.getInt("pagination.page-indicator.slot", 49), ItemUtil.createItem(pageMaterial, pageName, pageLore));
    }

    private void addBackButton(Inventory inv, FileConfiguration guiConfig) {
        String backName = MessageUtil.color(guiConfig.getString("back-button.name", "&eBack"));
        List<String> backLore = new ArrayList<>();
        for (String line : guiConfig.getStringList("back-button.lore")) {
            backLore.add(MessageUtil.color(line));
        }
        String backMaterial = guiConfig.getString("back-button.material", "ARROW");
        inv.setItem(guiConfig.getInt("back-button.slot", 48), createActionItem(backMaterial, backName, backLore, "back"));
    }

    private void addClearAllButton(Inventory inv, FileConfiguration guiConfig) {
        if (!guiConfig.getBoolean("main-menu.clear-all.enabled", true)) return;

        String clearName = MessageUtil.color(guiConfig.getString("main-menu.clear-all.name", "&c&lRemove All Cosmetics"));
        List<String> clearLore = new ArrayList<>();
        for (String line : guiConfig.getStringList("main-menu.clear-all.lore")) {
            clearLore.add(MessageUtil.color(line));
        }
        String clearMaterial = guiConfig.getString("main-menu.clear-all.material", "BARRIER");
        inv.setItem(guiConfig.getInt("main-menu.clear-all.slot", 49), createActionItem(clearMaterial, clearName, clearLore, "clear_all"));
    }

    /**
     * Creates an item with a GUI action tag for click handling.
     */
    private ItemStack createActionItem(String material, String name, List<String> lore, String action) {
        ItemStack item = ItemUtil.createItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "gui_action"),
                org.bukkit.persistence.PersistentDataType.STRING,
                action
            );
            item.setItemMeta(meta);
        }
        return item;
    }


    public GuiSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public void removeSession(UUID playerId) {
        sessions.remove(playerId);
    }

    public static class GuiSession {
        private final UUID playerId;
        private CosmeticCategory currentCategory = CosmeticCategory.AURAS;
        private int currentPage = 1;
        private String pendingPurchase = null;

        public GuiSession(UUID playerId) {
            this.playerId = playerId;
        }

        public UUID getPlayerId() { return playerId; }
        public CosmeticCategory getCurrentCategory() { return currentCategory; }
        public void setCurrentCategory(CosmeticCategory category) { this.currentCategory = category; }
        public int getCurrentPage() { return currentPage; }
        public void setCurrentPage(int page) { this.currentPage = page; }
        public String getPendingPurchase() { return pendingPurchase; }
        public void setPendingPurchase(String id) { this.pendingPurchase = id; }
        public void clearPendingPurchase() { this.pendingPurchase = null; }
    }
}