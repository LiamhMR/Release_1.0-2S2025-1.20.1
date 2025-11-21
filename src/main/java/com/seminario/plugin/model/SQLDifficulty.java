package com.seminario.plugin.model;

/**
 * Represents SQL difficulty levels from 1 (basic) to 5 (very difficult)
 */
public enum SQLDifficulty {
    BASIC(1, "Básico", "SELECT simple, WHERE básico"),
    INTERMEDIATE(2, "Intermedio", "Filtros avanzados, ORDER BY"),
    ADVANCED(3, "Avanzado", "GROUP BY, funciones agregadas"),
    EXPERT(4, "Experto", "JOINs, subconsultas"),
    MASTER(5, "Maestro", "Consultas complejas, múltiples JOINs");
    
    private final int level;
    private final String displayName;
    private final String description;
    
    SQLDifficulty(int level, String displayName, String description) {
        this.level = level;
        this.displayName = displayName;
        this.description = description;
    }
    
    public int getLevel() {
        return level;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Get difficulty by numeric level
     * @param level The difficulty level (1-5)
     * @return SQLDifficulty or null if invalid
     */
    public static SQLDifficulty fromLevel(int level) {
        for (SQLDifficulty difficulty : values()) {
            if (difficulty.level == level) {
                return difficulty;
            }
        }
        return null;
    }
    
    /**
     * Get maximum time allowed for this difficulty (in seconds)
     * @return Time limit in seconds
     */
    public int getTimeLimit() {
        return switch (this) {
            case BASIC -> 300;        // 5 minutes
            case INTERMEDIATE -> 240; // 4 minutes
            case ADVANCED -> 180;     // 3 minutes
            case EXPERT -> 120;       // 2 minutes
            case MASTER -> 90;        // 1.5 minutes
        };
    }
    
    /**
     * Get hint availability for this difficulty
     * @return Number of hints available
     */
    public int getHintsAvailable() {
        return switch (this) {
            case BASIC -> 3;
            case INTERMEDIATE -> 2;
            case ADVANCED -> 2;
            case EXPERT -> 1;
            case MASTER -> 0;
        };
    }
}