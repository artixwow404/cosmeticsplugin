package com.cosmetics.data;

import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.util.MessageUtil;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class DatabaseManager {

    private final CosmeticsPlugin plugin;
    private Connection connection;
    private final ConcurrentHashMap<UUID, PlayerCosmeticData> cache = new ConcurrentHashMap<>();

    public DatabaseManager(CosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dbFile = new File(dataFolder, "data.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            connection.setAutoCommit(true);
            createTables();
            plugin.getLogger().info("Database connected successfully!");
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    private void createTables() throws SQLException {
        String[] statements = {
            "CREATE TABLE IF NOT EXISTS player_cosmetics (" +
            "uuid TEXT PRIMARY KEY, " +
            "owned_auras TEXT DEFAULT '', " +
            "owned_trails TEXT DEFAULT '', " +
            "owned_wings TEXT DEFAULT '', " +
            "owned_hats TEXT DEFAULT '', " +
            "owned_pets TEXT DEFAULT '', " +
            "owned_killsounds TEXT DEFAULT '', " +
            "owned_companions TEXT DEFAULT '', " +
            "active_aura TEXT DEFAULT '', " +
            "active_trail TEXT DEFAULT '', " +
            "active_wings TEXT DEFAULT '', " +
            "active_hat TEXT DEFAULT '', " +
            "active_pet TEXT DEFAULT '', " +
            "active_killsound TEXT DEFAULT '', " +
            "active_companion TEXT DEFAULT '', " +
            "settings TEXT DEFAULT '{}'" +
            ")"
        };

        try (Statement stmt = connection.createStatement()) {
            for (String sql : statements) {
                stmt.execute(sql);
            }
        }
    }

    public CompletableFuture<PlayerCosmeticData> loadPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerCosmeticData cached = cache.get(uuid);
            if (cached != null) {
                return cached;
            }

            PlayerCosmeticData data = new PlayerCosmeticData(uuid);
            String sql = "SELECT * FROM player_cosmetics WHERE uuid = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        data.ownedAuras = parseStringList(rs.getString("owned_auras"));
                        data.ownedTrails = parseStringList(rs.getString("owned_trails"));
                        data.ownedWings = parseStringList(rs.getString("owned_wings"));
                        data.ownedHats = parseStringList(rs.getString("owned_hats"));
                        data.ownedPets = parseStringList(rs.getString("owned_pets"));
                        data.ownedKillSounds = parseStringList(rs.getString("owned_killsounds"));
                        data.ownedCompanions = parseStringList(rs.getString("owned_companions"));
                        data.activeAura = rs.getString("active_aura");
                        data.activeTrail = rs.getString("active_trail");
                        data.activeWings = rs.getString("active_wings");
                        data.activeHat = rs.getString("active_hat");
                        data.activePet = rs.getString("active_pet");
                        data.activeKillSound = rs.getString("active_killsound");
                        data.activeCompanion = rs.getString("active_companion");
                        data.settings = rs.getString("settings");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load player data for " + uuid, e);
            }

            cache.put(uuid, data);
            return data;
        });
    }

    public CompletableFuture<Void> savePlayerData(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            PlayerCosmeticData data = cache.get(uuid);
            if (data == null) return;

            String sql = "INSERT OR REPLACE INTO player_cosmetics (" +
                    "uuid, owned_auras, owned_trails, owned_wings, owned_hats, " +
                    "owned_pets, owned_killsounds, owned_companions, " +
                    "active_aura, active_trail, active_wings, active_hat, " +
                    "active_pet, active_killsound, active_companion, settings) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, joinStringList(data.ownedAuras));
                stmt.setString(3, joinStringList(data.ownedTrails));
                stmt.setString(4, joinStringList(data.ownedWings));
                stmt.setString(5, joinStringList(data.ownedHats));
                stmt.setString(6, joinStringList(data.ownedPets));
                stmt.setString(7, joinStringList(data.ownedKillSounds));
                stmt.setString(8, joinStringList(data.ownedCompanions));
                stmt.setString(9, data.activeAura);
                stmt.setString(10, data.activeTrail);
                stmt.setString(11, data.activeWings);
                stmt.setString(12, data.activeHat);
                stmt.setString(13, data.activePet);
                stmt.setString(14, data.activeKillSound);
                stmt.setString(15, data.activeCompanion);
                stmt.setString(16, data.settings);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save player data for " + uuid, e);
            }
        });
    }

    public void saveAllPlayerData() {
        for (UUID uuid : cache.keySet()) {
            savePlayerData(uuid);
        }
    }

    public PlayerCosmeticData getCachedData(UUID uuid) {
        return cache.get(uuid);
    }

    public void invalidateCache(UUID uuid) {
        cache.remove(uuid);
    }

    public void close() {
        saveAllPlayerData();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing database", e);
        }
    }

    private java.util.List<String> parseStringList(String str) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (str != null && !str.isEmpty()) {
            for (String s : str.split(",")) {
                if (!s.isEmpty()) list.add(s);
            }
        }
        return list;
    }

    private String joinStringList(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return String.join(",", list);
    }

    public static class PlayerCosmeticData {
        public final UUID uuid;
        public java.util.List<String> ownedAuras = new java.util.ArrayList<>();
        public java.util.List<String> ownedTrails = new java.util.ArrayList<>();
        public java.util.List<String> ownedWings = new java.util.ArrayList<>();
        public java.util.List<String> ownedHats = new java.util.ArrayList<>();
        public java.util.List<String> ownedPets = new java.util.ArrayList<>();
        public java.util.List<String> ownedKillSounds = new java.util.ArrayList<>();
        public java.util.List<String> ownedCompanions = new java.util.ArrayList<>();
        public String activeAura = "";
        public String activeTrail = "";
        public String activeWings = "";
        public String activeHat = "";
        public String activePet = "";
        public String activeKillSound = "";
        public String activeCompanion = "";
        public String settings = "{}";

        public PlayerCosmeticData(UUID uuid) {
            this.uuid = uuid;
        }

        public boolean owns(String category, String id) {
            return switch (category.toLowerCase()) {
                case "auras" -> ownedAuras.contains(id);
                case "trails" -> ownedTrails.contains(id);
                case "wings" -> ownedWings.contains(id);
                case "hats" -> ownedHats.contains(id);
                case "pets" -> ownedPets.contains(id);
                case "killsounds" -> ownedKillSounds.contains(id);
                case "companions" -> ownedCompanions.contains(id);
                default -> false;
            };
        }

        public void addOwned(String category, String id) {
            switch (category.toLowerCase()) {
                case "auras" -> { if (!ownedAuras.contains(id)) ownedAuras.add(id); }
                case "trails" -> { if (!ownedTrails.contains(id)) ownedTrails.add(id); }
                case "wings" -> { if (!ownedWings.contains(id)) ownedWings.add(id); }
                case "hats" -> { if (!ownedHats.contains(id)) ownedHats.add(id); }
                case "pets" -> { if (!ownedPets.contains(id)) ownedPets.add(id); }
                case "killsounds" -> { if (!ownedKillSounds.contains(id)) ownedKillSounds.add(id); }
                case "companions" -> { if (!ownedCompanions.contains(id)) ownedCompanions.add(id); }
            }
        }

        public void removeOwned(String category, String id) {
            switch (category.toLowerCase()) {
                case "auras" -> ownedAuras.remove(id);
                case "trails" -> ownedTrails.remove(id);
                case "wings" -> ownedWings.remove(id);
                case "hats" -> ownedHats.remove(id);
                case "pets" -> ownedPets.remove(id);
                case "killsounds" -> ownedKillSounds.remove(id);
                case "companions" -> ownedCompanions.remove(id);
            }
        }

        public String getActive(String category) {
            return switch (category.toLowerCase()) {
                case "auras" -> activeAura;
                case "trails" -> activeTrail;
                case "wings" -> activeWings;
                case "hats" -> activeHat;
                case "pets" -> activePet;
                case "killsounds" -> activeKillSound;
                case "companions" -> activeCompanion;
                default -> "";
            };
        }

        public void setActive(String category, String id) {
            switch (category.toLowerCase()) {
                case "auras" -> activeAura = id;
                case "trails" -> activeTrail = id;
                case "wings" -> activeWings = id;
                case "hats" -> activeHat = id;
                case "pets" -> activePet = id;
                case "killsounds" -> activeKillSound = id;
                case "companions" -> activeCompanion = id;
            }
        }

        public void clearActive(String category) {
            setActive(category, "");
        }
    }
}