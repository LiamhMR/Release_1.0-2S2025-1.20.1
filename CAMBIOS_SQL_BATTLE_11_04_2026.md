# Cambios SQL Battle - 11 de abril de 2026

## Resumen
Durante esta sesión se ajustó el flujo de inicio del modo SQL Battle, el manejo de AP entre prewaves, el consumo real de recursos desde `almacen`, la limpieza de libros temporales, el horario de oleadas y la entrega de items de control para inicio de juego y de oleada.

---

## 1. Flujo de entrada al mundo SQL Battle

### Objetivo
Separar claramente:
1. **Entrar al mundo SQL Battle**
2. **Iniciar el juego SQL Battle**
3. **Entrar a la primera prewave**

### Comportamiento final
Al entrar a un mundo SQL Battle:
- Se limpia el inventario del jugador
- Se limpia armadura y offhand
- Se cambia el modo de juego a `ADVENTURE`
- Se entrega un item único para **iniciar el juego SQL Battle**

Ese item es:
- `Material.NETHER_STAR`
- Nombre: `Iniciar juego SQL Battle`
- Acción: click derecho

Cuando el jugador lo usa:
- Desaparece el item de entrada
- Se llama `startForPlayer(...)`
- Se crea la primera sesión SQL Battle
- El jugador entra a la **primera prewave**

### Archivo modificado
- `src/main/java/com/seminario/plugin/listener/WorldChangeListener.java`

### Detalles técnicos
- Se agregó `NamespacedKey sqlBattleEntryItemKey`
- Se implementaron:
  - `giveSqlBattleEntryItem(Player)`
  - `clearSqlBattleEntryItem(Player)`
  - `isSqlBattleEntryItem(ItemStack)`
- `onPlayerInteract(...)` ahora distingue:
  1. item de entrada al juego
  2. item de iniciar oleada dentro de prewave

---

## 2. Item de prewave para iniciar la oleada

### Objetivo
Permitir que el jugador comience la oleada sin necesidad de gastar todo el AP.

### Comportamiento final
Cuando inicia una prewave:
- Se entrega un item especial para **iniciar la oleada manualmente**

Ese item es:
- `Material.BLAZE_POWDER`
- Nombre: `Iniciar oleada`

Cuando el jugador lo usa:
- Solo funciona si la sesión está en fase `PREPARATION`
- Llama `forceStartWaveFromPreparation(...)`
- Se inicia la oleada inmediatamente

Cuando la oleada comienza:
- El item se elimina automáticamente del inventario

### Archivo modificado
- `src/main/java/com/seminario/plugin/manager/SQLBattleManager.java`
- `src/main/java/com/seminario/plugin/listener/WorldChangeListener.java`

### Métodos agregados/ajustados
En `SQLBattleManager.java`:
- `forceStartWaveFromPreparation(Player)`
- `givePrewaveStartItem(Player)`
- `clearPrewaveStartItem(Player)`
- `isPrewaveStartItem(ItemStack)`

Integrado en:
- `beginPreparationSession(...)`
- `startNextPreparationPhase(...)`
- `beginWavePhase(...)`
- `endPreparationSession(...)`

---

## 3. Reinicio y escalado de AP por prewave

### Problema detectado
Los AP no se estaban reiniciando correctamente entre oleadas y la cantidad inicial era demasiado alta para la primera prewave.

### Nueva curva de AP
- **Primera prewave**: `5 AP`
- **Segunda prewave**: `7 AP`
- **Tercera prewave en adelante**: aumenta de `1 en 1`

Ejemplo:
- Oleada 1 → `5 AP`
- Oleada 2 → `7 AP`
- Oleada 3 → `8 AP`
- Oleada 4 → `9 AP`
- Oleada 5 → `10 AP`

### Implementación
En `SQLBattleManager.java`:
- `BASE_PREWAVE_ACTION_POINTS = 5`
- `SECOND_PREWAVE_ACTION_POINTS = 7`
- `MAX_PREWAVE_ACTION_POINTS = 20`
- `calculatePreparationActionPoints(int waveNumber)` actualizado

Además:
- Al iniciar la primera sesión se setean AP según oleada 1
- Al terminar una oleada se carga la siguiente prewave con AP reiniciados

---

## 4. Transición automática a la siguiente prewave

### Objetivo
Después de terminar una oleada, no dejar al jugador en pausa indefinida.

### Comportamiento final
Al completar la oleada:
- Se limpian entidades activas
- Se pone el mundo en estado sin oleada activa
- Se carga automáticamente la siguiente oleada en la base SQL
- Se reinician los AP de la nueva prewave
- Se teletransporta al jugador a la zona de preparación
- Se entrega el item de `Iniciar oleada`

### Métodos relacionados
En `SQLBattleManager.java`:
- `finishWaveForPlayer(...)`
- `startNextPreparationPhase(...)`

---

## 5. Consumo real de `almacen` al hacer INSERT en `inventario`

### Problema detectado
Los `INSERT` a `inventario` agregaban items virtualmente, pero no descontaban stock del `almacen`.

### Solución implementada
Ahora cada `INSERT` exitoso en `inventario`:
1. toma snapshot del inventario antes del cambio
2. ejecuta la consulta dentro de transacción JDBC
3. calcula el delta positivo por `item_id`
4. descuenta ese delta desde `almacen`
5. si no hay stock suficiente, hace `ROLLBACK`

### Garantías
- Si no hay suficiente stock en `almacen`, la consulta falla
- No se consumen AP si la operación hace rollback
- No queda inconsistencia entre `almacen` e `inventario`

### Archivo modificado
- `src/main/java/com/seminario/plugin/sql/battle/BattleSQLDatabase.java`
- `src/main/java/com/seminario/plugin/manager/SQLBattleManager.java`

### Métodos agregados
En `BattleSQLDatabase.java`:
- `snapshotInventarioQuantities()`
- `consumeAlmacenForInventarioIncrease(Map<Integer, Integer>)`

### Integración
En `processBattleQuery(...)`:
- se abrió transacción manual (`setAutoCommit(false)`)
- se hace `commit` solo si SQL + consumo de almacen salen bien
- se hace `rollback` si cualquier paso falla

---

## 6. Expansión de items disponibles para combate

### Problema detectado
Había arco, pero no flechas. También faltaban otros consumibles y defensas básicas.

### Nuevos items agregados a `tipos_item`
- `11 -> Flechas`
- `12 -> Escudo`
- `13 -> Filete Cocido`

### Stock inicial agregado a `almacen`
- Arco Elfico x1
- Flechas x32
- Escudo x1
- Filete Cocido x8

### Mapeo a Minecraft agregado
En `SQLBattleManager.java`:
- `11 -> Material.ARROW`
- `12 -> Material.SHIELD`
- `13 -> Material.COOKED_BEEF`

### Archivos modificados
- `src/main/java/com/seminario/plugin/sql/battle/BattleSQLDatabase.java`
- `src/main/java/com/seminario/plugin/manager/SQLBattleManager.java`

---

## 7. Sugerencias clickeables en prewave

### Objetivo
Ayudar al jugador a entender la dinámica del modo sin depender de memoria previa.

### Comportamiento final
Durante prewave:
- se muestran sugerencias de queries en chat
- cada sugerencia es clickeable
- al hacer click se ejecuta la consulta real
- la consulta consume AP normalmente

### Soporte implementado
En `SQLBattleManager.java`:
- `showPreparationSuggestions(Player)`
- `executePreparationSuggestion(Player, int)`
- `getSuggestionTitleById(int)`
- `getSuggestedQueryById(int)`

En `SeminarioCommand.java`:
- subcomando `sqlbattle suggest <id>`

---

## 8. Limpieza de libros de resultados SQL Battle

### Objetivo
Evitar que el jugador acumule libros temporales entre prewaves y oleadas.

### Comportamiento final
Se limpian libros `Resultado SQL Battle`:
- al iniciar una nueva prewave
- al terminar una oleada
- al reiniciar la sesión de preparación

### Implementación
En `SQLBattleManager.java`:
- `clearQueryResultBooks(Player)`
- `isSqlBattleResultBook(ItemStack)`

---

## 9. Ajuste de horario para oleadas

### Objetivo
Hacer que las arañas entren agresivas y que los esqueletos no se quemen al comenzar la oleada.

### Cambio realizado
En `SQLBattleManager.java`:
- `ACTIVE_WAVE_TIME` cambió a `14000L`

### Resultado esperado
- comienzo de oleada más cercano a noche real
- arañas más consistentes en modo hostil
- esqueletos sin quemarse por luz diurna residual

---

## 10. Entrada a SQL Battle: limpieza y modo aventura

### Comportamiento final al entrar al mundo
- inventario limpio
- armadura limpia
- offhand limpio
- modo `ADVENTURE`

Esto sigue siendo parte del flujo base de entrada antes de entregar el item de inicio del juego.

---

## Archivos modificados hoy

1. `src/main/java/com/seminario/plugin/listener/WorldChangeListener.java`
2. `src/main/java/com/seminario/plugin/manager/SQLBattleManager.java`
3. `src/main/java/com/seminario/plugin/sql/battle/BattleSQLDatabase.java`
4. `src/main/java/com/seminario/plugin/commands/SeminarioCommand.java`
5. `src/main/java/com/seminario/plugin/App.java`

---

## Estado al cierre de la sesión

Flujo actual de juego:
1. El jugador entra al mundo SQL Battle
2. Recibe item `Iniciar juego SQL Battle`
3. Hace click y entra a la primera prewave
4. En prewave recibe item `Iniciar oleada`
5. Puede:
   - consultar SQL manualmente
   - usar sugerencias clickeables
   - iniciar la oleada manualmente con el item
6. Si usa `INSERT`, consume stock real desde `almacen`
7. Al terminar la oleada, entra automáticamente a la siguiente prewave

---

## Build

Compilación validada con:

```bash
mvn -DskipTests package
```

Resultado: **BUILD SUCCESS**