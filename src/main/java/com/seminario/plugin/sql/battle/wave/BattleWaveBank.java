package com.seminario.plugin.sql.battle.wave;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Static catalog of all wave definitions for SQL BATTLE.
 *
 * Five difficulty levels — three waves each = 15 total.
 *
 * Level 1 – Vanguardia (Novato)
 *   Enemies: Zombis, Esqueletos, Arañas. Low HP. No grants (starting stockpile is enough).
 *
 * Level 2 – Tormenta (Aprendiz)
 *   Enemies: adds Creepers and Golem de Hierro.
 *   Grants: Hechizo de Fuego x2 (id=7), Arco Élfico x2 (id=4).
 *
 * Level 3 – Sombra (Veterano)
 *   Enemies: Endermans e Brujas lead. Golems hit harder.
 *   Grants: Armadura de Diamante x1 (id=6), Hechizo de Hielo +2 (id=8).
 *
 * Level 4 – Caos (Experto)
 *   Enemies: elite mix — all types, high HP.
 *   Grants: Hechizo de Fuego +3 (id=7), Poción de Vida +3 (id=9).
 *
 * Level 5 – Apocalipsis (Maestro)
 *   Enemies: Dragón boss + elite escorts.
 *   Grants: arsenal completo — Fuego +3, Hielo +3, Golem +1, Pociones +5.
 *
 * Usage:
 *   BattleWaveBank.getByWaveNumber(1)            → wave 1 (index 1-based)
 *   BattleWaveBank.getWavesForLevel(3)           → 3 waves of level 3
 *   BattleWaveBank.getByWaveNumber(16)           → wraps around to wave 1
 */
public class BattleWaveBank {

    // tipo_id constants (map to tipos_enemigo.id)
    private static final int ZOMBI        = 1;
    private static final int ESQUELETO    = 2;
    private static final int ARANA        = 3;
    private static final int CREEPER      = 4;
    private static final int ENDERMAN     = 5;
    private static final int GOLEM_HIERRO = 6;
    private static final int BRUJA        = 7;
    private static final int DRAGON       = 8;

    // item_id constants (map to tipos_item.id)
    private static final int ITEM_ARCO_ELFICO      = 4;
    private static final int ITEM_ARMADURA_DIAMANTE = 6;
    private static final int ITEM_HECHIZO_FUEGO    = 7;
    private static final int ITEM_HECHIZO_HIELO    = 8;
    private static final int ITEM_POCION_VIDA      = 9;
    private static final int ITEM_GOLEM            = 10;

    private static final List<BattleWaveDefinition> ALL_WAVES;

    static {
        List<BattleWaveDefinition> waves = new ArrayList<>();

        // =====================================================================
        // NIVEL 1 — Vanguardia (HP: 20-35, sin grants)
        // =====================================================================

        waves.add(new BattleWaveDefinition(
            1, 1,
            "Vanguardia: Horda Inicial",
            "Los primeros muertos vivientes se acercan. Usa lo que tienes.",
            List.of(
                new EnemySpawn(ZOMBI,     30, 1),
                new EnemySpawn(ZOMBI,     30, 1),
                new EnemySpawn(ESQUELETO, 25, 2),
                new EnemySpawn(ARANA,     20, 3)
            ),
            null
        ));

        waves.add(new BattleWaveDefinition(
            2, 1,
            "Vanguardia: Patrulla Nocturna",
            "Tres zombis abren camino y dos esqueletos cubren desde lejos.",
            List.of(
                new EnemySpawn(ZOMBI,     35, 1),
                new EnemySpawn(ZOMBI,     35, 1),
                new EnemySpawn(ZOMBI,     35, 2),
                new EnemySpawn(ESQUELETO, 25, 2),
                new EnemySpawn(ESQUELETO, 25, 3)
            ),
            null
        ));

        waves.add(new BattleWaveDefinition(
            3, 1,
            "Vanguardia: Nido de Arañas",
            "Un zombi distrae mientras las arañas flanquean por los lados.",
            List.of(
                new EnemySpawn(ZOMBI,  30, 1),
                new EnemySpawn(ARANA,  20, 1),
                new EnemySpawn(ARANA,  20, 2),
                new EnemySpawn(ARANA,  20, 3)
            ),
            null
        ));

        // =====================================================================
        // NIVEL 2 — Tormenta (HP: 25-60, grants: Fuego x2 + Arco x2)
        // =====================================================================

        List<AlmacenGrant> grantsN2 = List.of(
            new AlmacenGrant(ITEM_HECHIZO_FUEGO, 2),
            new AlmacenGrant(ITEM_ARCO_ELFICO,   2)
        );

        waves.add(new BattleWaveDefinition(
            4, 2,
            "Tormenta: Vanguardia Explosiva",
            "Creeepers mezclados entre zombis — un solo error y explotan.",
            List.of(
                new EnemySpawn(ZOMBI,     40, 1),
                new EnemySpawn(ZOMBI,     40, 1),
                new EnemySpawn(CREEPER,   35, 2),
                new EnemySpawn(ESQUELETO, 30, 2),
                new EnemySpawn(ARANA,     25, 3)
            ),
            grantsN2
        ));

        waves.add(new BattleWaveDefinition(
            5, 2,
            "Tormenta: Guardia de Hierro",
            "Un Golem de Hierro custodiado por esqueletos. Necesitas el hacha.",
            List.of(
                new EnemySpawn(ESQUELETO,    30, 1),
                new EnemySpawn(ESQUELETO,    30, 1),
                new EnemySpawn(GOLEM_HIERRO, 60, 2),
                new EnemySpawn(ARANA,        25, 3)
            ),
            grantsN2
        ));

        waves.add(new BattleWaveDefinition(
            6, 2,
            "Tormenta: Cueva Infestada",
            "La cueva está llena de arañas. Un golem bloquea la salida.",
            List.of(
                new EnemySpawn(CREEPER,      35, 1),
                new EnemySpawn(ARANA,        25, 1),
                new EnemySpawn(ARANA,        25, 2),
                new EnemySpawn(ARANA,        25, 3),
                new EnemySpawn(GOLEM_HIERRO, 60, 3)
            ),
            grantsN2
        ));

        // =====================================================================
        // NIVEL 3 — Sombra (HP: 30-75, grants: Armadura Diamante x1 + Hielo +2)
        // =====================================================================

        List<AlmacenGrant> grantsN3 = List.of(
            new AlmacenGrant(ITEM_ARMADURA_DIAMANTE, 1),
            new AlmacenGrant(ITEM_HECHIZO_HIELO,     2)
        );

        waves.add(new BattleWaveDefinition(
            7, 3,
            "Sombra: Oscuridad Total",
            "Los Endermans atacan primero. La Bruja lanza pociones envenenadas.",
            List.of(
                new EnemySpawn(ENDERMAN,     45, 1),
                new EnemySpawn(ENDERMAN,     45, 1),
                new EnemySpawn(BRUJA,        40, 2),
                new EnemySpawn(GOLEM_HIERRO, 75, 3),
                new EnemySpawn(GOLEM_HIERRO, 75, 3)
            ),
            grantsN3
        ));

        waves.add(new BattleWaveDefinition(
            8, 3,
            "Sombra: Alquimia Oscura",
            "Dos brujas controlan el campo desde el comienzo. Cuidado con sus efectos.",
            List.of(
                new EnemySpawn(BRUJA,        40, 1),
                new EnemySpawn(BRUJA,        40, 1),
                new EnemySpawn(ENDERMAN,     45, 2),
                new EnemySpawn(GOLEM_HIERRO, 75, 2),
                new EnemySpawn(CREEPER,      45, 3)
            ),
            grantsN3
        ));

        waves.add(new BattleWaveDefinition(
            9, 3,
            "Sombra: Asalto Coordinado",
            "Un golem rompe la defensa frontal mientras el Enderman flanquea.",
            List.of(
                new EnemySpawn(GOLEM_HIERRO, 75, 1),
                new EnemySpawn(ESQUELETO,    40, 1),
                new EnemySpawn(ESQUELETO,    40, 1),
                new EnemySpawn(ENDERMAN,     45, 2),
                new EnemySpawn(ARANA,        30, 3)
            ),
            grantsN3
        ));

        // =====================================================================
        // NIVEL 4 — Caos (HP: 45-85, grants: Fuego +3 + Pociones +3)
        // =====================================================================

        List<AlmacenGrant> grantsN4 = List.of(
            new AlmacenGrant(ITEM_HECHIZO_FUEGO, 3),
            new AlmacenGrant(ITEM_POCION_VIDA,   3)
        );

        waves.add(new BattleWaveDefinition(
            10, 4,
            "Caos: Ejército de Sombras",
            "Golems aplastan a la vanguardia mientras las brujas debilitan desde atrás.",
            List.of(
                new EnemySpawn(GOLEM_HIERRO, 80, 1),
                new EnemySpawn(GOLEM_HIERRO, 80, 1),
                new EnemySpawn(BRUJA,        50, 2),
                new EnemySpawn(BRUJA,        50, 2),
                new EnemySpawn(ENDERMAN,     55, 3),
                new EnemySpawn(ENDERMAN,     55, 3)
            ),
            grantsN4
        ));

        waves.add(new BattleWaveDefinition(
            11, 4,
            "Caos: Lluvia de Flechas",
            "Tres esqueletos abren fuego coordinado. Los creepers esperan el momento exacto.",
            List.of(
                new EnemySpawn(ESQUELETO, 45, 1),
                new EnemySpawn(ESQUELETO, 45, 1),
                new EnemySpawn(ESQUELETO, 45, 1),
                new EnemySpawn(BRUJA,     50, 2),
                new EnemySpawn(CREEPER,   50, 2),
                new EnemySpawn(CREEPER,   50, 3)
            ),
            grantsN4
        ));

        waves.add(new BattleWaveDefinition(
            12, 4,
            "Caos: Torbellino",
            "Todo a la vez: explosivos, evasores y un golem que no para.",
            List.of(
                new EnemySpawn(CREEPER,      50, 1),
                new EnemySpawn(CREEPER,      50, 1),
                new EnemySpawn(ENDERMAN,     55, 2),
                new EnemySpawn(ENDERMAN,     55, 2),
                new EnemySpawn(GOLEM_HIERRO, 80, 2),
                new EnemySpawn(BRUJA,        50, 3)
            ),
            grantsN4
        ));

        // =====================================================================
        // NIVEL 5 — Apocalipsis (JEFE: Dragón, grants: arsenal completo)
        // =====================================================================

        List<AlmacenGrant> grantsN5 = List.of(
            new AlmacenGrant(ITEM_HECHIZO_FUEGO,     3),
            new AlmacenGrant(ITEM_HECHIZO_HIELO,     3),
            new AlmacenGrant(ITEM_GOLEM,             1),
            new AlmacenGrant(ITEM_POCION_VIDA,       5)
        );

        waves.add(new BattleWaveDefinition(
            13, 5,
            "Apocalipsis: El Despertar",
            "Los golems lo custodian. El Dragón despierta en la etapa final.",
            List.of(
                new EnemySpawn(GOLEM_HIERRO, 90,  1),
                new EnemySpawn(GOLEM_HIERRO, 90,  1),
                new EnemySpawn(BRUJA,        60,  2),
                new EnemySpawn(DRAGON,       150, 3)
            ),
            grantsN5
        ));

        waves.add(new BattleWaveDefinition(
            14, 5,
            "Apocalipsis: Tormenta Final",
            "Tres golems y dos Endermans. El Dragón cierra con todo.",
            List.of(
                new EnemySpawn(GOLEM_HIERRO, 90,  1),
                new EnemySpawn(GOLEM_HIERRO, 90,  1),
                new EnemySpawn(GOLEM_HIERRO, 90,  1),
                new EnemySpawn(ENDERMAN,     65,  2),
                new EnemySpawn(ENDERMAN,     65,  2),
                new EnemySpawn(DRAGON,       200, 3)
            ),
            grantsN5
        ));

        waves.add(new BattleWaveDefinition(
            15, 5,
            "Apocalipsis: Última Defensa",
            "Sin piedad. El Dragón viene con refuerzos de élite en cada etapa.",
            List.of(
                new EnemySpawn(GOLEM_HIERRO, 90,  1),
                new EnemySpawn(BRUJA,        60,  1),
                new EnemySpawn(BRUJA,        60,  1),
                new EnemySpawn(ENDERMAN,     65,  2),
                new EnemySpawn(ENDERMAN,     65,  2),
                new EnemySpawn(DRAGON,       250, 3)
            ),
            grantsN5
        ));

        ALL_WAVES = Collections.unmodifiableList(waves);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the wave for a given 1-based sequential number.
     * Wraps around cyclically if n > total waves.
     */
    public static BattleWaveDefinition getByWaveNumber(int n) {
        if (n < 1) n = 1;
        int idx = (n - 1) % ALL_WAVES.size();
        return ALL_WAVES.get(idx);
    }

    /**
     * Returns all waves belonging to a specific difficulty level (1–5).
     */
    public static List<BattleWaveDefinition> getWavesForLevel(int level) {
        return ALL_WAVES.stream()
                .filter(w -> w.getLevel() == level)
                .collect(Collectors.toList());
    }

    /**
     * Returns all registered wave definitions (15 total, in order).
     */
    public static List<BattleWaveDefinition> getAll() {
        return ALL_WAVES;
    }

    public static int getTotalWaves() {
        return ALL_WAVES.size();
    }
}
