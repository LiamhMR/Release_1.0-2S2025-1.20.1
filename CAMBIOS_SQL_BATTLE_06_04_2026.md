# Cambios SQL Battle - 06 de abril de 2026

## Resumen
Integración completa del sistema de entry zone, transferencia automática de items a inventario Minecraft, visualización de HP de enemigos en tiempo real, y diagnósticos mejorados.

---

## 1. Entry Zone con WorldEdit Wand (Región)

### Cambios en `SQLBattleWorld.java`
- **Nuevos campos**: `entryZonePos1`, `entryZonePos2`
- **Nuevos métodos**:
  - `setEntryZonePos1(Location)` / `getEntryZonePos1()`
  - `setEntryZonePos2(Location)` / `getEntryZonePos2()`
  - `hasEntryZone()` – valida que ambos puntos existan

- **Actualización de serialización**: Los campos se guardan/cargan en `serialize()` y `deserialize()`

- **Cambio en `isExpandedConfigured()`**:
  ```java
  // Antes: requería worldEntryLocation
  // Ahora: acepta EITHER hasWorldEntryLocation() OR hasEntryZone()
  return isConfigured() && (hasWorldEntryLocation() || hasEntryZone()) && hasSummonZone();
  ```

### Cambios en `SeminarioCommand.java`
- **Caso `entry`/`worldentry` en `/sm sqlbattle here set`**:
  - Ahora usa `//wand` para seleccionar región (2 puntos con WorldEdit)
  - Llama a `sqlBattleManager.setEntryZone(worldName, pos1, pos2)`
  - Mensaje de feedback mejorado con las dos posiciones

### Cambios en `SQLBattleManager.java`
- **Nuevo método**: `setEntryZone(String worldName, Location pos1, Location pos2)`
- **Tolerancia vertical**: `ENTRY_ZONE_VERTICAL_TOLERANCE = 2.0D`
  - El eje Y se extiende ±2 bloques automáticamente
  - Permite escribir comandos hasta 2 bloques **arriba** o **abajo** de la región seleccionada

- **Método `isInsideRegion(Location pos1, Location pos2, Location check)`**:
  ```java
  double minY = Math.min(pos1.getY(), pos2.getY()) - ENTRY_ZONE_VERTICAL_TOLERANCE;
  double maxY = Math.max(pos1.getY(), pos2.getY()) + ENTRY_ZONE_VERTICAL_TOLERANCE;
  // Los ejes X y Z no tienen tolerancia
  ```

- **Método `isPlayerInPreparationZone(Player)`**:
  - Prioridad: si existe `entryZone` → usa región
  - Fallback: si existe `preparationLocation` → usa radio de 4.5 bloques
  - **Ya está integrado en `shouldCapturePreparationChat()`**, así que el chat SQL se captura automáticamente

### Debug
- Comando `/sm sqlbattle debug` ahora dibuja partículas `END_ROD` en toda la región del entry zone (no solo un punto)
- Diagnóstico mejorado para indicar qué falta (entry zone O punto entrada)

---

## 2. Transferencia de Items: Inventario SQL → Minecraft

### Cambios en `BattleSQLDatabase.java`
- **Nueva clase interna**: `InventoryItemRow`
  - Campos: `itemId`, `cantidad`, `nombre`, `categoria`
  - Getters públicos para acceso

- **Nuevo método**: `getInventoryItemsForExactStage(int stage)`
  - Query SQL: obtiene items con `activo_en_etapa = stage`
  - Excluye `categoria = 'invocacion'` (esas spawned como entidades)
  - Retorna `List<InventoryItemRow>`

### Cambios en `SQLBattleManager.java`
- **Nuevo método**: `giveInventoryItemsToPlayer(Player, BattlePlayerSession, int stage)`
  - Se llama en:
    - `beginWavePhase()` – al iniciar la oleada (etapa 1)
    - `advanceStageOrCompleteWave()` – al avanzar a nueva etapa
  - Convierte `itemId` → `Material` usando `mapItemIdToMaterial()`
  - Agrega items al inventario del jugador con cantidad correcta
  - Manejo especial para pociones (aplica `PotionMeta`)
  - Chat feedback: `+X Nombre (etapa N)`

- **Mapeo de items SQL a Minecraft**:
  | ID | Nombre | Material |
  |---|---|---|
  | 1 | Espada de Diamante | DIAMOND_SWORD |
  | 2 | Espada de Hierro | IRON_SWORD |
  | 3 | Hacha de Madera | WOODEN_AXE |
  | 4 | Arco Elfico | BOW |
  | 5 | Armadura de Hierro | IRON_CHESTPLATE |
  | 6 | Armadura de Diamante | DIAMOND_CHESTPLATE |
  | 7 | Hechizo de Fuego | FIRE_CHARGE |
  | 8 | Hechizo de Hielo | SNOWBALL |
  | 9 | Pocion de Vida | POTION (type: INSTANT_HEAL) |
  | 10 | Invocacion de Golem | (spawned como entity, no se da item) |

---

## 3. Visualización de HP de Enemigos

### Cambios en `SQLBattleWaveListener.java`
- **Nuevo event listener**: `onEntityDamage(EntityDamageEvent)`
  - Priority: MONITOR (después de que el daño se haya calculado)
  - Llama a `sqlBattleManager.handleBattleEntityDamage(entity, finalDamage)`

### Cambios en `SQLBattleManager.java`
- **Nuevo método**: `handleBattleEntityDamage(LivingEntity entity, double finalDamage)`
  - Verifica que sea un enemy (tag `sqlBattleRoleKey = "enemy"`)
  - Extrae nombre actual y busca el marker ` [SQL]`
  - Calcula nuevo HP: `Math.max(0.0, entity.getHealth() - finalDamage)`
  - Actualiza nombre mostrado:
    ```
    [Nombre Original] [SQL] 45/100❤
    ```
  - Formato: `ChatColor.RED + (int)newHp + "/" + (int)maxHp + "❤"`
  - Se actualiza **en tiempo real** cada vez que recibe daño

---

## 4. Diagnósticos Mejorados en `sqlbattle start`

### Cambios en `SeminarioCommand.java` – `handleSQLBattleStartCommand()`

**Antes**: Reportaba "incompleto" tanto por config faltante como por error interno

**Ahora**: Diferencia dos casos
1. **Validación previa de config**:
   ```java
   if (battleWorld == null || !battleWorld.isConfigured()) {
       // Indica exactamente qué falta
       sender.sendMessage("SQL Battle incompleto. Debes configurar wavestart, checkpoint, prewave y enemyspawn.");
   }
   ```

2. **Error interno en arranque de sesión**:
   ```java
   if (!sqlBattleManager.startForPlayer(player)) {
       // Diferente mensaje, con hint sobre entry zone
       player.sendMessage("No se pudo iniciar la sesión SQL Battle por un error interno.");
       player.sendMessage("Si usas entry zone, recuerda: el chat SQL se captura dentro de esa región (con margen vertical de +/-2 bloques).");
   }
   ```

---

## 5. Actualización de `debugShowConfiguration`

El comando `/sm sqlbattle debug` ahora:
- Dibuja **región** de entry zone con partículas `END_ROD` (si existe)
- Dibuja **punto** de entrada clásico solo si no hay region
- Diagnóstico mejorado: indica "Zona de entrada SQL (//wand + 'entry')" como faltante

---

## Workflow Recomendado para Setup SQL Battle

```
1. /sm sqlbattle create <mundo>           – Crea la config
2. /sm sqlbattle here set wavestart       – Punto donde inicia oleada
3. /sm sqlbattle here set checkpoint      – Respawn en pruebas
4. /sm sqlbattle here set prewave         – Punto teleport inicio prep
5. //wand                                 – Seleccionar zona summon
6. /sm sqlbattle here set summonzone      – Guardar zona golems
7. //wand                                 – Seleccionar zona spam enemigos
8. /sm sqlbattle here set enemyspawn      – Guardar zona enemigos
9. //wand                                 – Seleccionar zona comandos SQL
10. /sm sqlbattle here set entry          – Guardar zone entrada (±2Y)
11. /sm sqlbattle debug                   – Visualizar con partículas
12. /sm sqlbattle start                   – ¡Listo!
```

---

## Testing Checklist

- [ ] Entry zone con //wand acepta comandos SQL dentro + ±2 bloques Y
- [ ] Items de inventario aparecen en mochila al iniciar oleada
- [ ] HP enemigos se muestra en nombre y actualiza con cada daño
- [ ] Debug particles muestran correctamente todas las zonas
- [ ] `/sm sqlbattle start` diferencia error config vs error interno
- [ ] Items con múltiples etapas se dan solo en su etapa (ej: Golem en etapa 3)

---

## Archivos Modificados

1. `src/main/java/com/seminario/plugin/model/SQLBattleWorld.java`
2. `src/main/java/com/seminario/plugin/sql/battle/BattleSQLDatabase.java`
3. `src/main/java/com/seminario/plugin/manager/SQLBattleManager.java`
4. `src/main/java/com/seminario/plugin/listener/SQLBattleWaveListener.java`
5. `src/main/java/com/seminario/plugin/commands/SeminarioCommand.java`

**Build**: ✅ BUILD SUCCESS (06-04-2026 23:09:57)
