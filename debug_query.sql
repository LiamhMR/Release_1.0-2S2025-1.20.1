-- Script de depuración para verificar jugadores con más de 100 diamantes
-- Ejecuta este script en tu base de datos para ver exactamente qué jugadores tienes

SELECT 'Todos los jugadores:' as info;
SELECT id, nombre, diamantes, oro, esmeraldas, mundo FROM Jugadores ORDER BY diamantes DESC;

SELECT '' as separator;
SELECT 'Jugadores con más de 100 diamantes:' as info;
SELECT nombre FROM Jugadores WHERE diamantes > 100 ORDER BY nombre;

SELECT '' as separator;
SELECT 'Conteo de jugadores con más de 100 diamantes:' as info;
SELECT COUNT(*) as total FROM Jugadores WHERE diamantes > 100;

SELECT '' as separator;
SELECT 'Verificación de EnderExplorer:' as info;
SELECT nombre, diamantes FROM Jugadores WHERE nombre LIKE '%EnderExplorer%';