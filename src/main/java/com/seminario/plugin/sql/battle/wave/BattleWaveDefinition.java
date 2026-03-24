package com.seminario.plugin.sql.battle.wave;

import java.util.Collections;
import java.util.List;

/**
 * Immutable definition of a single wave in the SQL BATTLE mode.
 *
 * Fields:
 *   waveId        – unique sequential id (1..N, matches position in BattleWaveBank)
 *   level         – difficulty level (1=Novato, 5=Maestro)
 *   name          – display name shown to the player
 *   description   – short flavour text
 *   enemies       – ordered list of enemy spawns; all stages represented
 *   almacenGrants – items granted to the player's almacen when this wave loads
 */
public class BattleWaveDefinition {

    private final int waveId;
    private final int level;
    private final String name;
    private final String description;
    private final List<EnemySpawn> enemies;
    private final List<AlmacenGrant> almacenGrants;

    public BattleWaveDefinition(
            int waveId,
            int level,
            String name,
            String description,
            List<EnemySpawn> enemies,
            List<AlmacenGrant> almacenGrants) {

        this.waveId        = waveId;
        this.level         = level;
        this.name          = name;
        this.description   = description;
        this.enemies       = Collections.unmodifiableList(enemies);
        this.almacenGrants = almacenGrants != null
                ? Collections.unmodifiableList(almacenGrants)
                : Collections.emptyList();
    }

    public int getWaveId()                       { return waveId; }
    public int getLevel()                        { return level; }
    public String getName()                      { return name; }
    public String getDescription()               { return description; }
    public List<EnemySpawn> getEnemies()         { return enemies; }
    public List<AlmacenGrant> getAlmacenGrants() { return almacenGrants; }
}
