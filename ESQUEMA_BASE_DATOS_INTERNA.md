# Esquema de Base de Datos SQL - Plugin Minecraft Seminario

## Descripción
Este archivo contiene el código SQL completo para crear la base de datos utilizada en el SQL Dungeon del plugin de Minecraft. Incluye todas las tablas, constraints, índices y datos de ejemplo con temática de Minecraft.

---

## ⚡ CÓDIGO PARA DBDESIGNER.NET

**Sintaxis específica para dbdesigner.net - Copia y pega este bloque:**

```
Jugadores {
  jugador_pk int pk
  nombre varchar
  nivel int
  diamantes int
  esmeraldas int
  oro int
  ubicacion_x int
  ubicacion_z int
  mundo varchar
  fecha_registro date
  ultimo_login timestamp
}

Inventarios {
  inventario_pk int pk
  jugador_fk int > Jugadores.jugador_pk
  item varchar
  cantidad int
  rareza varchar
  encantado boolean
  fecha_obtenido timestamp
}

Construcciones {
  construccion_pk int pk
  jugador_fk int > Jugadores.jugador_pk
  nombre varchar
  tipo varchar
  tamaño int
  bloques_usados int
  fecha_creacion date
  mundo varchar
  coordenada_x int
  coordenada_y int
  coordenada_z int
  activa boolean
}

Logros {
  logro_pk int pk
  jugador_fk int > Jugadores.jugador_pk
  nombre_logro varchar
  descripcion text
  puntos int
  fecha_obtenido timestamp
  categoria varchar
}

Comercio {
  comercio_pk int pk
  vendedor_fk int > Jugadores.jugador_pk
  comprador_fk int > Jugadores.jugador_pk
  item varchar
  cantidad int
  precio_diamantes int
  precio_esmeraldas int
  fecha_intercambio timestamp
  estado varchar
}
```

**Notas para dbdesigner.net:**
- `pk` = Primary Key
- `>` = Foreign Key (ejemplo: `jugador_fk int > Jugadores.jugador_pk`)
- No necesitas `AUTO_INCREMENT`, `NOT NULL`, ni `DEFAULT` en esta sintaxis
- Las relaciones se crean automáticamente con el símbolo `>`

---

## 💾 CÓDIGO SQL COMPLETO PARA MYSQL

**Para usar en MySQL Workbench, phpMyAdmin, o cualquier cliente MySQL:**

```sql
-- Tabla: Jugadores
CREATE TABLE Jugadores (
    jugador_pk INT PRIMARY KEY AUTO_INCREMENT,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    nivel INT DEFAULT 1,
    diamantes INT DEFAULT 0,
    esmeraldas INT DEFAULT 0,
    oro INT DEFAULT 0,
    ubicacion_x INT DEFAULT 0,
    ubicacion_z INT DEFAULT 0,
    mundo VARCHAR(30) DEFAULT 'overworld',
    fecha_registro DATE,
    ultimo_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabla: Inventarios
CREATE TABLE Inventarios (
    inventario_pk INT PRIMARY KEY AUTO_INCREMENT,
    jugador_fk INT NOT NULL,
    item VARCHAR(50) NOT NULL,
    cantidad INT DEFAULT 1,
    rareza VARCHAR(20) DEFAULT 'común',
    encantado BOOLEAN DEFAULT FALSE,
    fecha_obtenido TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (jugador_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE,
    INDEX idx_jugador_item (jugador_fk, item)
);

-- Tabla: Construcciones
CREATE TABLE Construcciones (
    construccion_pk INT PRIMARY KEY AUTO_INCREMENT,
    jugador_fk INT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    tamaño INT DEFAULT 1,
    bloques_usados INT DEFAULT 0,
    fecha_creacion DATE,
    mundo VARCHAR(30) DEFAULT 'overworld',
    coordenada_x INT DEFAULT 0,
    coordenada_y INT DEFAULT 64,
    coordenada_z INT DEFAULT 0,
    activa BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (jugador_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE,
    INDEX idx_jugador_construccion (jugador_fk, tipo),
    INDEX idx_ubicacion (mundo, coordenada_x, coordenada_z)
);

-- Tabla: Logros
CREATE TABLE Logros (
    logro_pk INT PRIMARY KEY AUTO_INCREMENT,
    jugador_fk INT NOT NULL,
    nombre_logro VARCHAR(100) NOT NULL,
    descripcion TEXT,
    puntos INT DEFAULT 10,
    fecha_obtenido TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    categoria VARCHAR(30) DEFAULT 'general',
    FOREIGN KEY (jugador_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE,
    UNIQUE INDEX uk_jugador_logro (jugador_fk, nombre_logro),
    INDEX idx_categoria (categoria),
    INDEX idx_fecha (fecha_obtenido)
);

-- Tabla: Comercio
CREATE TABLE Comercio (
    comercio_pk INT PRIMARY KEY AUTO_INCREMENT,
    vendedor_fk INT NOT NULL,
    comprador_fk INT NOT NULL,
    item VARCHAR(50) NOT NULL,
    cantidad INT DEFAULT 1,
    precio_diamantes INT DEFAULT 0,
    precio_esmeraldas INT DEFAULT 0,
    fecha_intercambio TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    estado VARCHAR(20) DEFAULT 'completado',
    FOREIGN KEY (vendedor_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE,
    FOREIGN KEY (comprador_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE,
    INDEX idx_vendedor (vendedor_fk),
    INDEX idx_comprador (comprador_fk),
    INDEX idx_fecha_comercio (fecha_intercambio),
    INDEX idx_item_comercio (item)
);
```

---

## Creación de Tablas

### Tabla: Jugadores (Players)
Tabla principal que almacena la información de los jugadores.

```sql
CREATE TABLE Jugadores (
    jugador_pk INT PRIMARY KEY AUTO_INCREMENT,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    nivel INT DEFAULT 1,
    diamantes INT DEFAULT 0,
    esmeraldas INT DEFAULT 0,
    oro INT DEFAULT 0,
    ubicacion_x INT DEFAULT 0,
    ubicacion_z INT DEFAULT 0,
    mundo VARCHAR(30) DEFAULT 'overworld',
    fecha_registro DATE,
    ultimo_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

### Tabla: Inventarios (Inventories)
Almacena los items que posee cada jugador.

```sql
CREATE TABLE Inventarios (
    inventario_pk INT PRIMARY KEY AUTO_INCREMENT,
    jugador_fk INT NOT NULL,
    item VARCHAR(50) NOT NULL,
    cantidad INT DEFAULT 1,
    rareza VARCHAR(20) DEFAULT 'común',
    encantado BOOLEAN DEFAULT FALSE,
    fecha_obtenido TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (jugador_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE
);
```

**Índices recomendados:**
```sql
CREATE INDEX idx_jugador_item ON Inventarios (jugador_fk, item);
```

---

### Tabla: Construcciones (Buildings)
Almacena las construcciones creadas por los jugadores.

```sql
CREATE TABLE Construcciones (
    construccion_pk INT PRIMARY KEY AUTO_INCREMENT,
    jugador_fk INT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    tamaño INT DEFAULT 1,
    bloques_usados INT DEFAULT 0,
    fecha_creacion DATE,
    mundo VARCHAR(30) DEFAULT 'overworld',
    coordenada_x INT DEFAULT 0,
    coordenada_y INT DEFAULT 64,
    coordenada_z INT DEFAULT 0,
    activa BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (jugador_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE
);
```

**Índices recomendados:**
```sql
CREATE INDEX idx_jugador_construccion ON Construcciones (jugador_fk, tipo);
CREATE INDEX idx_ubicacion ON Construcciones (mundo, coordenada_x, coordenada_z);
```

---

### Tabla: Logros (Achievements)
Almacena los logros obtenidos por los jugadores.

```sql
CREATE TABLE Logros (
    logro_pk INT PRIMARY KEY AUTO_INCREMENT,
    jugador_fk INT NOT NULL,
    nombre_logro VARCHAR(100) NOT NULL,
    descripcion TEXT,
    puntos INT DEFAULT 10,
    fecha_obtenido TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    categoria VARCHAR(30) DEFAULT 'general',
    FOREIGN KEY (jugador_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE
);
```

**Constraints e índices:**
```sql
CREATE UNIQUE INDEX uk_jugador_logro ON Logros (jugador_fk, nombre_logro);
CREATE INDEX idx_categoria ON Logros (categoria);
CREATE INDEX idx_fecha ON Logros (fecha_obtenido);
```

---

### Tabla: Comercio (Trading)
Almacena las transacciones comerciales entre jugadores.

```sql
CREATE TABLE Comercio (
    comercio_pk INT PRIMARY KEY AUTO_INCREMENT,
    vendedor_fk INT NOT NULL,
    comprador_fk INT NOT NULL,
    item VARCHAR(50) NOT NULL,
    cantidad INT DEFAULT 1,
    precio_diamantes INT DEFAULT 0,
    precio_esmeraldas INT DEFAULT 0,
    fecha_intercambio TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    estado VARCHAR(20) DEFAULT 'completado',
    FOREIGN KEY (vendedor_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE,
    FOREIGN KEY (comprador_fk) REFERENCES Jugadores(jugador_pk) ON DELETE CASCADE
);
```

**Índices recomendados:**
```sql
CREATE INDEX idx_vendedor ON Comercio (vendedor_fk);
CREATE INDEX idx_comprador ON Comercio (comprador_fk);
CREATE INDEX idx_fecha_comercio ON Comercio (fecha_intercambio);
CREATE INDEX idx_item_comercio ON Comercio (item);
```

---

## Datos de Ejemplo

### Inserción de Jugadores

```sql
INSERT INTO Jugadores (jugador_pk, nombre, nivel, diamantes, esmeraldas, oro, ubicacion_x, ubicacion_z, mundo) VALUES
(1, 'Steve', 25, 156, 23, 450, 100, 200, 'overworld'),
(2, 'Alex', 18, 89, 67, 234, -50, 150, 'overworld'),
(3, 'Notch', 50, 999, 500, 2000, 0, 0, 'overworld'),
(4, 'Herobrine', 45, 666, 13, 1337, -100, -100, 'nether'),
(5, 'CraftMaster', 32, 234, 145, 890, 250, -75, 'overworld'),
(6, 'MineBuilder', 28, 178, 98, 567, 75, 300, 'overworld'),
(7, 'RedstoneGuru', 35, 345, 234, 1200, -200, 400, 'overworld'),
(8, 'EnderExplorer', 40, 445, 67, 890, 500, -300, 'end');
```

---

### Inserción de Inventarios

```sql
INSERT INTO Inventarios (jugador_fk, item, cantidad, rareza, encantado) VALUES
(1, 'espada_diamante', 1, 'épico', TRUE),
(1, 'pico_hierro', 2, 'común', FALSE),
(1, 'armadura_cuero', 1, 'común', FALSE),
(2, 'arco', 1, 'raro', TRUE),
(2, 'flechas', 64, 'común', FALSE),
(2, 'poción_curación', 5, 'raro', FALSE),
(3, 'bloque_diamante', 64, 'legendario', FALSE),
(3, 'elytra', 1, 'legendario', TRUE),
(4, 'espada_netherite', 1, 'legendario', TRUE),
(4, 'tridente', 1, 'épico', TRUE),
(5, 'bloques_construcción', 128, 'común', FALSE),
(6, 'herramientas_construcción', 10, 'raro', FALSE),
(7, 'redstone', 64, 'común', FALSE),
(7, 'repetidores', 32, 'común', FALSE),
(8, 'perlas_ender', 16, 'raro', FALSE),
(1, 'escudo', 1, 'raro', TRUE),
(3, 'beacon', 2, 'legendario', FALSE),
(4, 'totem_inmortalidad', 3, 'épico', FALSE);
```

---

### Inserción de Construcciones

```sql
INSERT INTO Construcciones (jugador_fk, nombre, tipo, tamaño, bloques_usados, mundo, coordenada_x, coordenada_y, coordenada_z) VALUES
(1, 'Casa de Steve', 'casa', 100, 1500, 'overworld', 100, 64, 200),
(2, 'Torre de Alex', 'torre', 200, 3000, 'overworld', -50, 64, 150),
(3, 'Palacio de Notch', 'castillo', 1000, 50000, 'overworld', 0, 64, 0),
(4, 'Fortaleza Oscura', 'castillo', 800, 25000, 'nether', -100, 64, -100),
(5, 'Villa Craftmaster', 'casa', 300, 8000, 'overworld', 250, 64, -75),
(5, 'Granero', 'granja', 150, 2000, 'overworld', 275, 64, -50),
(6, 'Catedral', 'torre', 500, 15000, 'overworld', 75, 64, 300),
(7, 'Laboratorio Redstone', 'mina', 250, 5000, 'overworld', -200, 64, 400),
(8, 'Castillo del End', 'castillo', 600, 20000, 'end', 500, 64, -300),
(2, 'Puente Colgante', 'puente', 80, 1200, 'overworld', -25, 70, 175);
```

---

### Inserción de Logros

```sql
INSERT INTO Logros (jugador_fk, nombre_logro, descripcion, puntos, categoria) VALUES
(1, 'Primer Diamante', 'Minó su primer diamante', 50, 'exploración'),
(1, 'Constructor Novato', 'Construyó su primera casa', 25, 'construcción'),
(2, 'Arquero Experto', 'Mató 100 mobs con arco', 75, 'combate'),
(3, 'Maestro Constructor', 'Construyó un palacio', 200, 'construcción'),
(4, 'Señor del Nether', 'Dominó el Nether', 150, 'exploración'),
(5, 'Comerciante', 'Realizó 50 intercambios', 100, 'comercio'),
(7, 'Ingeniero Redstone', 'Creó 10 máquinas de redstone', 125, 'construcción'),
(8, 'Explorador del End', 'Visitó el End', 175, 'exploración'),
(3, 'Benefactor', 'Donó recursos a otros jugadores', 300, 'general'),
(6, 'Arquitecto', 'Construyó una catedral', 250, 'construcción');
```

---

### Inserción de Comercio

```sql
INSERT INTO Comercio (vendedor_fk, comprador_fk, item, cantidad, precio_diamantes, precio_esmeraldas, estado) VALUES
(1, 2, 'espada_hierro', 1, 5, 0, 'completado'),
(2, 1, 'arco', 1, 8, 0, 'completado'),
(3, 5, 'bloques_diamante', 10, 0, 50, 'completado'),
(5, 6, 'bloques_construcción', 64, 0, 10, 'completado'),
(7, 1, 'pistón', 4, 2, 0, 'completado'),
(6, 8, 'perlas_ender', 5, 15, 0, 'completado'),
(3, 4, 'beacon', 1, 100, 0, 'completado'),
(4, 8, 'totem_inmortalidad', 1, 0, 75, 'completado'),
(1, 7, 'redstone', 32, 1, 5, 'completado');
```

---


### Nomenclatura:
- **Primary Keys**: Sufijo `_pk` (ejemplo: `jugador_pk`)
- **Foreign Keys**: Sufijo `_fk` (ejemplo: `jugador_fk`)

---

## Queries de Verificación

### Verificar integridad de datos

```sql
-- Contar registros por tabla
SELECT 'Jugadores' AS tabla, COUNT(*) AS total FROM Jugadores
UNION ALL
SELECT 'Inventarios', COUNT(*) FROM Inventarios
UNION ALL
SELECT 'Construcciones', COUNT(*) FROM Construcciones
UNION ALL
SELECT 'Logros', COUNT(*) FROM Logros
UNION ALL
SELECT 'Comercio', COUNT(*) FROM Comercio;
```

### Verificar relaciones

```sql
-- Verificar que todos los inventarios tienen un jugador válido
SELECT COUNT(*) AS inventarios_validos 
FROM Inventarios i 
INNER JOIN Jugadores j ON i.jugador_fk = j.jugador_pk;

-- Verificar que todas las construcciones tienen un jugador válido
SELECT COUNT(*) AS construcciones_validas 
FROM Construcciones c 
INNER JOIN Jugadores j ON c.jugador_fk = j.jugador_pk;

-- Verificar que todos los logros tienen un jugador válido
SELECT COUNT(*) AS logros_validos 
FROM Logros l 
INNER JOIN Jugadores j ON l.jugador_fk = j.jugador_pk;

-- Verificar que todos los comercios tienen jugadores válidos
SELECT COUNT(*) AS comercios_validos 
FROM Comercio co
INNER JOIN Jugadores v ON co.vendedor_fk = v.jugador_pk
INNER JOIN Jugadores c ON co.comprador_fk = c.jugador_pk;
```

---


