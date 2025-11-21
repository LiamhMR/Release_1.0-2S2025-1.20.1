# DOCUMENTACIÓN COMANDOS SQL

## RESUMEN GENERAL

El sistema SQL Dungeon permite crear mundos interactivos donde los jugadores resuelven consultas SQL de diferentes dificultades. Los comandos se organizan bajo el comando principal `/sm sql`.

En la sintaxis del comando, los argumentos se indican entre signos < >.
Los argumentos opcionales se indican entre los signos [ ].

---

## 11.6.5. Comandos SQL

### /sm sql schema

Permite visualizar la estructura de tablas de la base de datos a través de un mensaje de chat. Muestra el esquema completo con todas las tablas disponibles y sus campos, sirviendo de referencia para que los jugadores entiendan qué consultas pueden realizar. Se divide en múltiples mensajes para evitar límites del chat.

### /sm sql here lvl add <número> <dificultad>

Los comandos 'here' se refieren a que son válidos para el mundo actual, como prerrequisito el mundo en que se utilice el comando debe estar creado como un SQL DUNGEON usando `/sm create SQLDUNGEON <mundo>`. 

- `número` corresponde al número del nivel (entero positivo).
- `dificultad` es un valor de 1 a 5 (1=básico, 5=maestro). Según la dificultad indicada es el grupo de desafíos que se seleccionarán del banco de datos.

Crea un nuevo nivel en la ubicación actual del jugador y asigna automáticamente un desafío SQL de la dificultad especificada. Después de crear un nivel, se debe configurar su punto de entrada.

### /sm sql here lvl delete <número>

Elimina un nivel en específico. Remueve completamente el nivel existente junto con toda su configuración asociada (challenge, entrada, etc.). Los jugadores ya no podrán acceder a este nivel eliminado.

### /sm sql here set entry <número_nivel>

Este comando configura la posición en la que se utiliza como una posición interactiva. El número del nivel debe ser un nivel añadido previamente. Tras realizar el comando se debe poner un bloque en la posición donde se realizó. Cuando el jugador interactúe con ese ítem podrá insertar la consulta SQL correspondiente al desafío del nivel explicitado en el chat. Por estética en nuestro proyecto utilizamos bloques de comando para esto.

Establece la ubicación actual como punto de entrada interactivo donde los jugadores pueden hacer clic o acercarse para iniciar el desafío SQL. Es obligatorio que cada nivel tenga su entrada configurada para ser jugable.

### /sm sql info

Detalla el progreso y estado dentro de los niveles. Como prerrequisito se debe estar en un mundo SQL Dungeon.

Proporciona información completa sobre:
- Nombre del mundo actual y cantidad total de niveles configurados
- Estado de jugabilidad (si todos los niveles tienen entrada configurada)
- Progreso del jugador: niveles completados vs. niveles totales
- Nivel actual si hay sesión activa y próximo nivel a completar
- Lista detallada de todos los niveles con su dificultad e indicadores de entrada configurada y nivel completado

### /sm sql bank info

Muestra información del banco de desafíos, la cantidad total de estos desglosados por nivel de dificultad y alguna información extra.

Proporciona detalles sobre:
- Total de challenges disponibles en el sistema
- Cantidad de challenges por cada nivel de dificultad (BASIC, INTERMEDIATE, ADVANCED, EXPERT, MASTER)
- Explicación del sistema de asignación automática de desafíos

### /sm sql bank regenerate <mundo_SQL_DUNGEON> <nivel>

**Nota:** Este comando se encuentra listado como deprecado en documentación previa, sin embargo según análisis del código actual sigue siendo funcional.

- `mundo_SQL_DUNGEON` es el nombre exacto del mundo SQL Dungeon
- `nivel` es el número del nivel a regenerar

Asigna un nuevo challenge aleatorio de la misma dificultad al nivel especificado. Mantiene la dificultad original del nivel pero permite cambiar el desafío actual, útil si el challenge actual es demasiado fácil o difícil.

### /sm sql repair

**Nota:** Este comando se encuentra listado como deprecado en documentación previa, sin embargo según análisis del código actual sigue siendo funcional.

Como prerrequisito se debe estar en un mundo SQL Dungeon. 

Repara automáticamente niveles con problemas:
- Detecta niveles sin challenge asignado o con consultas esperadas faltantes
- Regenera challenges para niveles dañados automáticamente
- Reporta qué niveles fueron reparados y cuáles ya estaban correctos

Es útil después de actualizaciones del plugin, cuando los levels fueron creados antes de implementar el sistema de challenges, o para solucionar problemas de corrupción de datos.

---

## SISTEMA DE DIFICULTADES

Los niveles de dificultad disponibles son:

- **Nivel 1 (BASIC):** Consultas SELECT básicas
- **Nivel 2 (INTERMEDIATE):** WHERE, ORDER BY, COUNT  
- **Nivel 3 (ADVANCED):** JOINs, GROUP BY, agregaciones
- **Nivel 4 (EXPERT):** Subconsultas, HAVING, CASE
- **Nivel 5 (MASTER):** Consultas complejas, múltiples JOINs

---

## FLUJO DE TRABAJO RECOMENDADO

### Crear un SQL Dungeon desde cero:

1. Crear el mundo como SQL Dungeon: `/sm create SQLDUNGEON <mundo>`
2. Añadir niveles progresivos: `/sm sql here lvl add <número> <dificultad>`
3. Configurar puntos de entrada para cada nivel: `/sm sql here set entry <número>`
4. Verificar configuración: `/sm sql info`
5. Si hay problemas, reparar: `/sm sql repair`

### Administrar un SQL Dungeon existente:

1. Ver estado general: `/sm sql info`
2. Ver challenges disponibles: `/sm sql bank info`  
3. Cambiar challenge de un nivel: `/sm sql bank regenerate <mundo> <nivel>`
4. Reparar inconsistencias: `/sm sql repair`

---

## CONSIDERACIONES IMPORTANTES

### Errores comunes:

- **"Este mundo no es un SQL Dungeon":** No se ha ejecutado `/sm create SQLDUNGEON <mundo>` primero
- **"El nivel X no existe":** Se intenta configurar entrada o regenerar un nivel no creado
- **"¿Es jugable?: No":** Algunos niveles no tienen punto de entrada configurado

### Mejores prácticas:

- Crear niveles en orden progresivo de dificultad
- Configurar entrada inmediatamente después de crear cada nivel
- Verificar estado regularmente con `/sm sql info`
- Ejecutar `/sm sql repair` después de cambios importantes
- Documentar coordenadas de cada nivel para referencia futura