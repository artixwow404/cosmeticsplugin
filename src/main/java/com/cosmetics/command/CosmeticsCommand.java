package com.cosmetics.command;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.cosmetic.Cosmetic;
import com.cosmetics.cosmetic.CosmeticCategory;
import com.cosmetics.data.DatabaseManager;
import com.cosmetics.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class CosmeticsCommand implements CommandExecutor, TabCompleter {

    private final CosmeticsPlugin plugin;

    public CosmeticsCommand(CosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.getMessage("general.player-only"));
                return true;
            }
            plugin.getGuiManager().openMainMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "menu" -> handleMenu(sender, args);
            case "give" -> handleGive(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "reload" -> handleReload(sender);
            case "clear" -> handleClear(sender, args);
            case "unlockall" -> handleUnlockAll(sender, args);
            default -> sender.sendMessage(MessageUtil.getMessage("general.no-permission"));
        }
        return true;
    }

    private void handleMenu(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cosmetics.admin")) {
            sender.sendMessage(MessageUtil.getMessage("general.no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.getMessage("general.usage-menu"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.getMessage("general.player-not-found", Map.of("player", args[1])));
            return;
        }

        if (sender instanceof Player player && player != target) {
            plugin.getGuiManager().openMainMenu(target);
            sender.sendMessage(MessageUtil.color("&aOpened cosmetics menu for &e" + target.getName()));
        } else if (sender instanceof Player) {
            plugin.getGuiManager().openMainMenu((Player) sender);
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cosmetics.give")) {
            sender.sendMessage(MessageUtil.getMessage("general.no-permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(MessageUtil.getMessage("admin.usage-give"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.getMessage("general.player-not-found", Map.of("player", args[1])));
            return;
        }

        String cosmeticId = args[2];
        Cosmetic cosmetic = plugin.getCosmeticsManager().getCosmetic(cosmeticId);
        if (cosmetic == null) {
            sender.sendMessage(MessageUtil.getMessage("general.cosmetic-not-found", Map.of("cosmetic", cosmeticId)));
            return;
        }

        plugin.getCosmeticsManager().giveCosmetic(target.getUniqueId(), cosmetic);
        sender.sendMessage(MessageUtil.getMessage("admin.give-success", Map.of(
            "cosmetic", cosmetic.getName(),
            "player", target.getName()
        )));
        target.sendMessage(MessageUtil.getMessage("purchase.success", Map.of(
            "name", cosmetic.getName(),
            "price", "0",
            "currency", "FREE"
        )));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cosmetics.remove")) {
            sender.sendMessage(MessageUtil.getMessage("general.no-permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(MessageUtil.getMessage("admin.usage-remove"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.getMessage("general.player-not-found", Map.of("player", args[1])));
            return;
        }

        String cosmeticId = args[2];
        Cosmetic cosmetic = plugin.getCosmeticsManager().getCosmetic(cosmeticId);
        if (cosmetic == null) {
            sender.sendMessage(MessageUtil.getMessage("general.cosmetic-not-found", Map.of("cosmetic", cosmeticId)));
            return;
        }

        plugin.getCosmeticsManager().removeCosmetic(target.getUniqueId(), cosmetic);
        sender.sendMessage(MessageUtil.getMessage("admin.remove-success", Map.of(
            "cosmetic", cosmetic.getName(),
            "player", target.getName()
        )));
        target.sendMessage(MessageUtil.color("&cCosmetic &e" + cosmetic.getName() + " &chas been removed from you."));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("cosmetics.reload")) {
            sender.sendMessage(MessageUtil.getMessage("general.no-permission"));
            return;
        }

        plugin.getConfigManager().reloadAll();
        plugin.getCosmeticsManager().loadCosmetics();
        plugin.getSoundManager().reload();
        sender.sendMessage(MessageUtil.getMessage("general.reload-complete"));
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cosmetics.clear")) {
            sender.sendMessage(MessageUtil.getMessage("general.no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.getMessage("admin.usage-clear"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.getMessage("general.player-not-found", Map.of("player", args[1])));
            return;
        }

        plugin.getCosmeticsManager().clearAllCosmetics(target.getUniqueId());
        sender.sendMessage(MessageUtil.getMessage("admin.clear-success", Map.of("player", target.getName())));
        target.sendMessage(MessageUtil.getMessage("clear.success"));
    }

    private void handleUnlockAll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cosmetics.unlockall")) {
            sender.sendMessage(MessageUtil.getMessage("general.no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.getMessage("admin.usage-unlockall"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.getMessage("general.player-not-found", Map.of("player", args[1])));
            return;
        }

        plugin.getCosmeticsManager().unlockAll(target.getUniqueId());
        sender.sendMessage(MessageUtil.getMessage("admin.unlockall-success", Map.of("player", target.getName())));
        target.sendMessage(MessageUtil.color("&aAll cosmetics have been unlocked for you!"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("menu", "give", "remove", "reload", "clear", "unlockall");
            return subCommands.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .filter(s -> hasPermissionForSubCommand(sender, s))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("menu") || subCommand.equals("give") || subCommand.equals("remove") ||
                subCommand.equals("clear") || subCommand.equals("unlockall")) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("remove"))) {
            return plugin.getCosmeticsManager().getAllCosmetics().stream()
                .map(Cosmetic::getFullId)
                .filter(id -> id.toLowerCase().startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }

        return completions;
    }

    private boolean hasPermissionForSubCommand(CommandSender sender, String subCommand) {
        return switch (subCommand) {
            case "menu", "give", "remove", "clear", "unlockall" -> sender.hasPermission("cosmetics.admin");
            case "reload" -> sender.hasPermission("cosmetics.reload");
            default -> false;
        };
    }
}