package com.cosmetics.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageUtil {

    private static FileConfiguration messagesConfig;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static void setMessagesConfig(FileConfiguration config) {
        messagesConfig = config;
    }

    public static String getMessage(String path) {
        if (messagesConfig == null) return path;
        String message = messagesConfig.getString(path, path);
        return color(message);
    }

    public static String getMessage(String path, Map<String, String> placeholders) {
        String message = getMessage(path);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return message;
    }

    public static void sendMessage(Player player, String path) {
        player.sendMessage(getMessage(path));
    }

    public static void sendMessage(Player player, String path, Map<String, String> placeholders) {
        player.sendMessage(getMessage(path, placeholders));
    }

    public static String color(String text) {
        if (text == null) return "";
        text = HEX_PATTERN.matcher(text).replaceAll(match -> "§x" +
            String.join("§", match.group(1).split("")));
        return text.replace('&', '§');
    }

    public static String stripColor(String text) {
        if (text == null) return "";
        return text.replaceAll("§[0-9A-Fa-fK-Ok-or]", "")
                   .replaceAll("&[0-9A-Fa-fK-Ok-or]", "");
    }

    public static String format(String template, Object... args) {
        String result = color(template);
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }
}