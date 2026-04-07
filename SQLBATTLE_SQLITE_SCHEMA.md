# SQL Battle - Esquema para erd.dbdesigner.net

Este documento deja el esquema actual de SQL Battle en la sintaxis que usa `erd.dbdesigner.net`.

## Esquema en formato ERD

```text
Jugadores {
	jugador_pk int pk
	nombre varchar
	hp int
	mana int
	puntos_accion int
	oleada_actual int
	etapa_actual int
}

TiposItem {
	tipo_item_pk int pk
	nombre varchar
	categoria varchar
	costo_mana int
	etapa_activacion int
}

Almacen {
	almacen_pk int pk
	tipo_item_fk int > TiposItem.tipo_item_pk
	cantidad int
}

Inventario {
	inventario_pk int pk
	tipo_item_fk int > TiposItem.tipo_item_pk
	cantidad int
	activo_en_etapa int
}

TiposEnemigo {
	tipo_enemigo_pk int pk
	nombre varchar
	debilidad varchar
	descripcion text
}

Enemigos {
	enemigo_pk int pk
	tipo_enemigo_fk int > TiposEnemigo.tipo_enemigo_pk
	hp int
	hp_max int
	estado varchar
	etapa_aparicion int
}
```

## Equivalencia con el modelo actual del plugin

- `Jugadores` corresponde a la tabla `jugador`.
- `TiposItem` corresponde a la tabla `tipos_item`.
- `Almacen` corresponde a la tabla `almacen`.
- `Inventario` corresponde a la tabla `inventario`.
- `TiposEnemigo` corresponde a la tabla `tipos_enemigo`.
- `Enemigos` corresponde a la tabla `enemigos`.

## Relaciones

- `Almacen.tipo_item_fk > TiposItem.tipo_item_pk`
- `Inventario.tipo_item_fk > TiposItem.tipo_item_pk`
- `Enemigos.tipo_enemigo_fk > TiposEnemigo.tipo_enemigo_pk`

## Lectura del modelo

- `Jugadores`: estado actual de la sesion o partida, con HP, mana, puntos de accion, oleada y etapa.
- `TiposItem`: catalogo maestro de armas, hechizos, invocaciones, armaduras y consumibles.
- `Almacen`: recursos disponibles antes de preparar la oleada.
- `Inventario`: recursos comprometidos o activos para la oleada actual.
- `TiposEnemigo`: catalogo maestro de tipos de enemigo.
- `Enemigos`: enemigos concretos de la oleada actual.

## Valores esperados por campo

- `TiposItem.categoria`: `arma`, `hechizo`, `invocacion`, `armadura`, `consumible`
- `Jugadores.etapa_actual`: valores entre `0` y `3`
- `Inventario.activo_en_etapa`: valores entre `1` y `3`
- `Enemigos.estado`: `vivo`, `derrotado`, `aturdido`
- `Enemigos.etapa_aparicion`: valores entre `1` y `3`

## Nota de diseno

Este esquema refleja el modelo actual implementado en SQL Battle. Todavia no incluye tablas para zonas del mundo, invocaciones fisicas en Minecraft, ni una separacion adicional entre la fase `prewave` y la fase de combate fuera de los campos `oleada_actual` y `etapa_actual` del jugador.