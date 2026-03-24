package com.seminario.plugin.sql.battle.wave;

/**
 * Describes a single enemy to spawn at a specific stage of a wave.
 *
 * tipoId maps to tipos_enemigo.id (1=Zombi, 2=Esqueleto, 3=Araña, 4=Creeper,
 *   5=Enderman, 6=Golem de Hierro, 7=Bruja, 8=Dragón).
 * etapaAparicion: 1=inicio, 2=media, 3=final.
 */
public class EnemySpawn {

    private final int tipoId;
    private final int hp;
    private final int etapaAparicion;

    public EnemySpawn(int tipoId, int hp, int etapaAparicion) {
        this.tipoId          = tipoId;
        this.hp              = hp;
        this.etapaAparicion  = etapaAparicion;
    }

    public int getTipoId()          { return tipoId; }
    public int getHp()              { return hp; }
    public int getEtapaAparicion()  { return etapaAparicion; }
}
