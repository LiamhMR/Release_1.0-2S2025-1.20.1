package com.seminario.plugin.config;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.model.MenuType;
import com.seminario.plugin.model.MenuZone;
import com.seminario.plugin.model.SQLBattleWorld;
import com.seminario.plugin.model.SQLDungeonWorld;

/**
 * Manages the storage and retrieval of menu zones in YAML format
 */
public class ConfigManager {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final File configFile;
    private final File sqlBattlesFile;
    private final File sqlDungeonsFile;
    private FileConfiguration config;
    private FileConfiguration sqlBattlesConfig;
    private FileConfiguration sqlDungeonsConfig;
    private Map<String, MenuZone> menuZones;
    private Map<String, SQLBattleWorld> sqlBattles;
    private Map<String, SQLDungeonWorld> sqlDungeons;
    
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFile = new File(plugin.getDataFolder(), "menuzones.yml");
        this.sqlBattlesFile = new File(plugin.getDataFolder(), "sqlbattle.yml");
        this.sqlDungeonsFile = new File(plugin.getDataFolder(), "sqldungeons.yml");
        this.menuZones = new HashMap<>();
        this.sqlBattles = new HashMap<>();
        this.sqlDungeons = new HashMap<>();
        
        loadConfig();
        loadSQLBattles();
        loadSQLDungeons();
    }
    
    /**
     * Load configuration from file
     */
    private void loadConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                logger.info("Created new menuzones.yml file");
            } catch (IOException e) {
                logger.severe("Could not create menuzones.yml file: " + e.getMessage());
                return;
            }
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        loadMenuZones();
    }
    
    /**
     * Load all menu zones from configuration
     */
    private void loadMenuZones() {
        menuZones.clear();
        
        ConfigurationSection zonesSection = config.getConfigurationSection("menuzones");
        if (zonesSection == null) {
            logger.info("No menu zones found in configuration");
            return;
        }
        
        for (String zoneName : zonesSection.getKeys(false)) {
            try {
                ConfigurationSection zoneSection = zonesSection.getConfigurationSection(zoneName);
                if (zoneSection != null) {
                    Map<String, Object> zoneData = new HashMap<>();
                    for (String key : zoneSection.getKeys(true)) {
                        zoneData.put(key, zoneSection.get(key));
                    }
                    zoneData.put("name", zoneName);
                    
                    MenuZone zone = MenuZone.deserialize(zoneData);
                    menuZones.put(zoneName, zone);
                    logger.info("Loaded menu zone: " + zoneName);
                }
            } catch (Exception e) {
                logger.warning("Failed to load menu zone '" + zoneName + "': " + e.getMessage());
            }
        }
        
        logger.info("Loaded " + menuZones.size() + " menu zones");
    }
    
    /**
     * Save configuration to file
     */
    public void saveConfig() {
        try {
            // Clear existing zones section
            config.set("menuzones", null);
            
            // Save all menu zones
            for (MenuZone zone : menuZones.values()) {
                String path = "menuzones." + zone.getName();
                Map<String, Object> zoneData = zone.serialize();
                
                for (Map.Entry<String, Object> entry : zoneData.entrySet()) {
                    if (!entry.getKey().equals("name")) { // Don't save name as it's the key
                        config.set(path + "." + entry.getKey(), entry.getValue());
                    }
                }
            }
            
            config.save(configFile);
            logger.info("Saved " + menuZones.size() + " menu zones to configuration");
        } catch (IOException e) {
            logger.severe("Could not save menuzones.yml: " + e.getMessage());
        }
    }
    
    /**
     * Add a new menu zone
     * @param zone The menu zone to add
     * @return true if added successfully, false if name already exists
     */
    public boolean addMenuZone(MenuZone zone) {
        if (menuZones.containsKey(zone.getName())) {
            return false;
        }
        
        menuZones.put(zone.getName(), zone);
        saveConfig();
        logger.info("Added new menu zone: " + zone.getName());
        return true;
    }
    
    /**
     * Remove a menu zone
     * @param name The name of the zone to remove
     * @return true if removed successfully, false if not found
     */
    public boolean removeMenuZone(String name) {
        MenuZone removed = menuZones.remove(name);
        if (removed != null) {
            saveConfig();
            logger.info("Removed menu zone: " + name);
            return true;
        }
        return false;
    }
    
    /**
     * Get a menu zone by name
     * @param name The name of the zone
     * @return The menu zone or null if not found
     */
    public MenuZone getMenuZone(String name) {
        return menuZones.get(name);
    }
    
    /**
     * Get all menu zones
     * @return Map of all menu zones
     */
    public Map<String, MenuZone> getAllMenuZones() {
        return new HashMap<>(menuZones);
    }
    
    /**
     * Check if a zone name exists
     * @param name The name to check
     * @return true if exists
     */
    public boolean hasMenuZone(String name) {
        return menuZones.containsKey(name);
    }
    
    /**
     * Update the menu type of an existing zone
     * @param zoneName The name of the zone to update
     * @param menuType The new menu type
     * @return true if updated successfully, false if zone not found
     */
    public boolean updateMenuType(String zoneName, MenuType menuType) {
        MenuZone zone = menuZones.get(zoneName);
        if (zone == null) {
            return false;
        }
        
        zone.setMenuType(menuType);
        saveConfig();
        logger.info("Updated menu type for zone '" + zoneName + "' to: " + 
            (menuType != null ? menuType.getName() : "none"));
        return true;
    }
    
    /**
     * Reload configuration from file
     */
    public void reload() {
        loadConfig();
        loadSQLBattles();
        loadSQLDungeons();
    }

    // ===== SQL BATTLE MANAGEMENT =====

    private void loadSQLBattles() {
        sqlBattles.clear();

        if (!sqlBattlesFile.exists()) {
            try {
                sqlBattlesFile.createNewFile();
                logger.info("Created new sqlbattle.yml file");
            } catch (IOException e) {
                logger.warning("Could not create sqlbattle.yml file: " + e.getMessage());
                return;
            }
        }

        sqlBattlesConfig = YamlConfiguration.loadConfiguration(sqlBattlesFile);

        ConfigurationSection battlesSection = sqlBattlesConfig.getConfigurationSection("sqlbattles");
        if (battlesSection == null) {
            logger.info("No SQL battles found in configuration");
            return;
        }

        for (String worldName : battlesSection.getKeys(false)) {
            try {
                ConfigurationSection battleSection = battlesSection.getConfigurationSection(worldName);
                if (battleSection != null) {
                    Map<String, Object> battleData = convertConfigurationSectionToMap(battleSection);
                    SQLBattleWorld battleWorld = SQLBattleWorld.deserialize(battleData);
                    sqlBattles.put(worldName, battleWorld);
                    logger.info("Loaded SQL battle: " + worldName);
                }
            } catch (Exception e) {
                logger.warning("Failed to load SQL battle '" + worldName + "': " + e.getMessage());
            }
        }

        logger.info("Loaded " + sqlBattles.size() + " SQL battles");
    }

    public void saveSQLBattles() {
        try {
            sqlBattlesConfig.set("sqlbattles", null);

            for (Map.Entry<String, SQLBattleWorld> entry : sqlBattles.entrySet()) {
                String worldName = entry.getKey();
                SQLBattleWorld battleWorld = entry.getValue();

                Map<String, Object> serializedData = battleWorld.serialize();
                for (Map.Entry<String, Object> dataEntry : serializedData.entrySet()) {
                    sqlBattlesConfig.set("sqlbattles." + worldName + "." + dataEntry.getKey(), dataEntry.getValue());
                }
            }

            sqlBattlesConfig.save(sqlBattlesFile);
            logger.info("Saved " + sqlBattles.size() + " SQL battles to configuration");
        } catch (IOException e) {
            logger.severe("Could not save sqlbattle.yml: " + e.getMessage());
        }
    }

    public boolean addSQLBattle(SQLBattleWorld battleWorld) {
        if (sqlBattles.containsKey(battleWorld.getWorldName())) {
            return false;
        }

        sqlBattles.put(battleWorld.getWorldName(), battleWorld);
        saveSQLBattles();
        logger.info("Added new SQL battle: " + battleWorld.getWorldName());
        return true;
    }

    public boolean removeSQLBattle(String worldName) {
        if (sqlBattles.remove(worldName) != null) {
            saveSQLBattles();
            logger.info("Removed SQL battle: " + worldName);
            return true;
        }
        return false;
    }

    public SQLBattleWorld getSQLBattle(String worldName) {
        return sqlBattles.get(worldName);
    }

    public Map<String, SQLBattleWorld> getAllSQLBattles() {
        return new HashMap<>(sqlBattles);
    }

    public boolean isSQLBattle(String worldName) {
        return sqlBattles.containsKey(worldName);
    }

    public void updateSQLBattle(SQLBattleWorld battleWorld) {
        sqlBattles.put(battleWorld.getWorldName(), battleWorld);
        saveSQLBattles();
    }
    
    // ===== SQL DUNGEONS MANAGEMENT =====
    
    /**
     * Load SQL dungeons from configuration
     */
    private void loadSQLDungeons() {
        sqlDungeons.clear();
        
        if (!sqlDungeonsFile.exists()) {
            try {
                sqlDungeonsFile.createNewFile();
                logger.info("Created new sqldungeons.yml file");
            } catch (IOException e) {
                logger.warning("Could not create sqldungeons.yml file: " + e.getMessage());
                return;
            }
        }
        
        sqlDungeonsConfig = YamlConfiguration.loadConfiguration(sqlDungeonsFile);
        
        ConfigurationSection dungeonsSection = sqlDungeonsConfig.getConfigurationSection("sqldungeons");
        if (dungeonsSection == null) {
            logger.info("No SQL dungeons found in configuration");
            return;
        }
        
        for (String worldName : dungeonsSection.getKeys(false)) {
            try {
                ConfigurationSection dungeonSection = dungeonsSection.getConfigurationSection(worldName);
                if (dungeonSection != null) {
                    Map<String, Object> dungeonData = convertConfigurationSectionToMap(dungeonSection);
                    
                    SQLDungeonWorld sqlWorld = SQLDungeonWorld.deserialize(dungeonData);
                    sqlDungeons.put(worldName, sqlWorld);
                    logger.info("Loaded SQL dungeon: " + worldName);
                }
            } catch (Exception e) {
                logger.warning("Failed to load SQL dungeon '" + worldName + "': " + e.getMessage());
            }
        }
        
        logger.info("Loaded " + sqlDungeons.size() + " SQL dungeons");
    }
    
    /**
     * Save SQL dungeons to configuration
     */
    public void saveSQLDungeons() {
        try {
            // Clear existing dungeons section
            sqlDungeonsConfig.set("sqldungeons", null);
            
            // Save all SQL dungeons
            for (Map.Entry<String, SQLDungeonWorld> entry : sqlDungeons.entrySet()) {
                String worldName = entry.getKey();
                SQLDungeonWorld sqlWorld = entry.getValue();
                
                Map<String, Object> serializedData = sqlWorld.serialize();
                for (Map.Entry<String, Object> dataEntry : serializedData.entrySet()) {
                    sqlDungeonsConfig.set("sqldungeons." + worldName + "." + dataEntry.getKey(), dataEntry.getValue());
                }
            }
            
            sqlDungeonsConfig.save(sqlDungeonsFile);
            logger.info("Saved " + sqlDungeons.size() + " SQL dungeons to configuration");
        } catch (IOException e) {
            logger.severe("Could not save sqldungeons.yml: " + e.getMessage());
        }
    }
    
    /**
     * Add a new SQL dungeon
     * @param sqlWorld The SQL dungeon world to add
     * @return true if added successfully
     */
    public boolean addSQLDungeon(SQLDungeonWorld sqlWorld) {
        if (sqlDungeons.containsKey(sqlWorld.getWorldName())) {
            return false;
        }
        
        sqlDungeons.put(sqlWorld.getWorldName(), sqlWorld);
        saveSQLDungeons();
        logger.info("Added new SQL dungeon: " + sqlWorld.getWorldName());
        return true;
    }
    
    /**
     * Remove a SQL dungeon
     * @param worldName The world name
     * @return true if removed successfully
     */
    public boolean removeSQLDungeon(String worldName) {
        if (sqlDungeons.remove(worldName) != null) {
            saveSQLDungeons();
            logger.info("Removed SQL dungeon: " + worldName);
            return true;
        }
        return false;
    }
    
    /**
     * Get a SQL dungeon by world name
     * @param worldName The world name
     * @return SQLDungeonWorld or null if not found
     */
    public SQLDungeonWorld getSQLDungeon(String worldName) {
        return sqlDungeons.get(worldName);
    }
    
    /**
     * Get all SQL dungeons
     * @return Map of world names to SQL dungeon worlds
     */
    public Map<String, SQLDungeonWorld> getAllSQLDungeons() {
        return new HashMap<>(sqlDungeons);
    }
    
    /**
     * Check if a world is a SQL dungeon
     * @param worldName The world name
     * @return true if it's a SQL dungeon
     */
    public boolean isSQLDungeon(String worldName) {
        return sqlDungeons.containsKey(worldName);
    }
    
    /**
     * Update a SQL dungeon (save changes)
     * @param sqlWorld The updated SQL dungeon world  
     */
    public void updateSQLDungeon(SQLDungeonWorld sqlWorld) {
        sqlDungeons.put(sqlWorld.getWorldName(), sqlWorld);
        saveSQLDungeons();
    }
    
    /**
     * Convert a ConfigurationSection to a Map, handling nested sections properly
     * @param section The ConfigurationSection to convert
     * @return Map representation of the section
     */
    private Map<String, Object> convertConfigurationSectionToMap(ConfigurationSection section) {
        Map<String, Object> map = new HashMap<>();
        
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            
            if (value instanceof ConfigurationSection) {
                // Recursively convert nested sections
                map.put(key, convertConfigurationSectionToMap((ConfigurationSection) value));
            } else {
                map.put(key, value);
            }
        }
        
        return map;
    }
    
    /**
     * Set the server spawnpoint
     * @param location The location to set as spawnpoint
     */
    public void setServerSpawnpoint(Location location) {
        if (location == null || location.getWorld() == null) {
            logger.warning("Attempted to set server spawnpoint with null location or world");
            return;
        }
        
        try {
            // Save spawnpoint data
            config.set("server.spawnpoint.world", location.getWorld().getName());
            config.set("server.spawnpoint.x", location.getX());
            config.set("server.spawnpoint.y", location.getY());
            config.set("server.spawnpoint.z", location.getZ());
            config.set("server.spawnpoint.yaw", location.getYaw());
            config.set("server.spawnpoint.pitch", location.getPitch());
            
            config.save(configFile);
            logger.info("Server spawnpoint saved to configuration");
        } catch (IOException e) {
            logger.severe("Could not save server spawnpoint: " + e.getMessage());
        }
    }
    
    /**
     * Get the server spawnpoint
     * @return The server spawnpoint location, or null if not set or world not found
     */
    public Location getServerSpawnpoint() {
        if (!config.contains("server.spawnpoint")) {
            return null;
        }
        
        try {
            String worldName = config.getString("server.spawnpoint.world");
            double x = config.getDouble("server.spawnpoint.x");
            double y = config.getDouble("server.spawnpoint.y");
            double z = config.getDouble("server.spawnpoint.z");
            float yaw = (float) config.getDouble("server.spawnpoint.yaw");
            float pitch = (float) config.getDouble("server.spawnpoint.pitch");
            
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                logger.warning("Server spawnpoint world '" + worldName + "' not found");
                return null;
            }
            
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            logger.warning("Error loading server spawnpoint: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if server spawnpoint is set
     * @return true if spawnpoint is configured
     */
    public boolean hasServerSpawnpoint() {
        return config.contains("server.spawnpoint.world");
    }
}