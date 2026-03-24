package com.seminario.plugin.sql.battle.wave;

/**
 * Represents an item quantity added to the player's almacen when a wave is loaded.
 *
 * If the item already exists in almacen, addCantidad is accumulated (UPDATE).
 * If it doesn't exist yet, a new row is inserted.
 */
public class AlmacenGrant {

    private final int itemId;
    private final int addCantidad;

    public AlmacenGrant(int itemId, int addCantidad) {
        this.itemId      = itemId;
        this.addCantidad = addCantidad;
    }

    public int getItemId()      { return itemId; }
    public int getAddCantidad() { return addCantidad; }
}
