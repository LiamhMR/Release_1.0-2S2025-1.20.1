TutorialSQL_Pack

Pack suplementario para las diapositivas del tutorial SQL.

Contenido:
- 16 texturas base de diapositivas copiadas desde presentations/TutorialSQL
- 720 tiles generados automaticamente en una cuadrícula 9x5
- Override de `minecraft:item/paper` mediante `CustomModelData`
- Override de `minecraft:item/filled_map` mediante `CustomModelData`
- Modelos individuales `slides:item/slide_N` para el icono general
- Modelos `slides:item/map_slide_N` para mostrar la diapositiva como mapa en mano
- Modelos `slides:item/tiles/slide_N_rX_cY` para renderizado experimental por tiles

Rangos de CustomModelData:
- `3001` -> slide 1
- `3002` -> slide 2
- `3003` -> slide 3
- `3004` -> slide 4
- `3005` -> slide 5
- `3006` -> slide 6
- `3007` -> slide 7
- `3008` -> slide 8
- `3009` -> slide 9
- `3010` -> slide 10
- `3011` -> slide 11
- `3012` -> slide 12
- `3013` -> slide 13
- `3014` -> slide 14
- `3015` -> slide 15
- `3016` -> slide 16
- `10000` en adelante -> tiles de la cuadrícula 9x5 usados por la GUI del plugin

Generación automática:
- Script: `tools/generate_tutorial_sql_tiles.py`
- Entrada: `../TutorialSQL/1.png` ... `16.png`
- Salida: texturas y modelos de tiles, modelos `map_slide_N`, y regeneración de `assets/minecraft/models/item/paper.json` y `assets/minecraft/models/item/filled_map.json`

Uso:
1. Comprimir el contenido de esta carpeta como `.zip` para distribuirlo como resource pack.
2. Si cambias una diapositiva, vuelve a ejecutar el script para regenerar todos los tiles.
3. El plugin usa un `PAPER` como item del HUB para iniciar el tutorial.
4. Al iniciar, el item se reemplaza por un `FILLED_MAP` con la diapositiva actual.
5. Navegación en mano: click derecho avanza, click izquierdo retrocede, shift + click derecho cierra.
6. Este pack no forma parte del build Maven del plugin porque está fuera de `src/main/resources`.

Nota:
- No necesitas cortar manualmente cada diapositiva para usar la presentación en mano.
- El render principal recomendado ahora es `FILLED_MAP` con `CustomModelData`, no una GUI de cofre.