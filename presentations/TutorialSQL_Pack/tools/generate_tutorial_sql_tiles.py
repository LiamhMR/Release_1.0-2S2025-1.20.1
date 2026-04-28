from __future__ import annotations

import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT.parent / "TutorialSQL"
ICON_TEXTURE_DIR = ROOT / "assets" / "slides" / "textures" / "item"
TEXTURE_DIR = ROOT / "assets" / "slides" / "textures" / "item" / "tiles"
MODEL_DIR = ROOT / "assets" / "slides" / "models" / "item" / "tiles"
PAPER_MODEL_PATH = ROOT / "assets" / "minecraft" / "models" / "item" / "paper.json"
FILLED_MAP_MODEL_PATH = ROOT / "assets" / "minecraft" / "models" / "item" / "filled_map.json"
ICON_MODEL_DIR = ROOT / "assets" / "slides" / "models" / "item"

TOTAL_SLIDES = 16
GRID_COLUMNS = 9
GRID_ROWS = 5
TILE_SIZE = 256
ICON_BASE_MODEL_DATA = 3001
TILE_BASE_MODEL_DATA = 10000


def crop_to_ratio(image: Image.Image, ratio: float) -> Image.Image:
    width, height = image.size
    current_ratio = width / height
    if abs(current_ratio - ratio) < 0.0001:
        return image

    if current_ratio > ratio:
        new_width = int(height * ratio)
        left = (width - new_width) // 2
        return image.crop((left, 0, left + new_width, height))

    new_height = int(width / ratio)
    top = (height - new_height) // 2
    return image.crop((0, top, width, top + new_height))


def clean_directory(directory: Path, suffix: str) -> None:
    for file in directory.glob(f"*.{suffix}"):
        file.unlink()


def build_tile_name(slide_number: int, row: int, column: int) -> str:
    return f"slide_{slide_number}_r{row}_c{column}"


def main() -> None:
    ICON_TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    ICON_MODEL_DIR.mkdir(parents=True, exist_ok=True)

    clean_directory(TEXTURE_DIR, "png")
    clean_directory(MODEL_DIR, "json")

    target_ratio = GRID_COLUMNS / GRID_ROWS
    target_size = (GRID_COLUMNS * TILE_SIZE, GRID_ROWS * TILE_SIZE)

    overrides: list[dict[str, object]] = []
    filled_map_overrides: list[dict[str, object]] = []

    for slide_number in range(1, TOTAL_SLIDES + 1):
        source_path = SOURCE_DIR / f"{slide_number}.png"
        icon_model_name = f"slide_{slide_number}"
        map_slide_model_name = f"map_slide_{slide_number}"
        overrides.append(
            {
                "predicate": {"custom_model_data": ICON_BASE_MODEL_DATA + slide_number - 1},
                "model": f"slides:item/{icon_model_name}",
            }
        )
        filled_map_overrides.append(
            {
                "predicate": {"custom_model_data": ICON_BASE_MODEL_DATA + slide_number - 1},
                "model": f"slides:item/{map_slide_model_name}",
            }
        )

        (ICON_MODEL_DIR / f"{map_slide_model_name}.json").write_text(
            json.dumps(
                {
                    "parent": "minecraft:item/generated",
                    "textures": {
                        "layer0": "minecraft:item/filled_map",
                        "layer1": f"slides:item/slide_{slide_number}",
                    },
                },
                indent=2,
            ) + "\n",
            encoding="utf-8",
        )

        with Image.open(source_path) as original:
            mirrored_icon = original.convert("RGBA").transpose(Image.Transpose.FLIP_LEFT_RIGHT)
            mirrored_icon.save(ICON_TEXTURE_DIR / f"slide_{slide_number}.png")

            prepared = crop_to_ratio(original.convert("RGBA"), target_ratio)
            prepared = prepared.resize(target_size, Image.Resampling.LANCZOS)

            for row in range(1, GRID_ROWS + 1):
                for column in range(1, GRID_COLUMNS + 1):
                    left = (column - 1) * TILE_SIZE
                    top = (row - 1) * TILE_SIZE
                    tile = prepared.crop((left, top, left + TILE_SIZE, top + TILE_SIZE))

                    tile_name = build_tile_name(slide_number, row, column)
                    texture_path = TEXTURE_DIR / f"{tile_name}.png"
                    model_path = MODEL_DIR / f"{tile_name}.json"
                    tile.save(texture_path)
                    model_path.write_text(
                        json.dumps(
                            {
                                "parent": "minecraft:item/generated",
                                "textures": {"layer0": f"slides:item/tiles/{tile_name}"},
                            },
                            indent=2,
                        ) + "\n",
                        encoding="utf-8",
                    )

                    linear_index = ((slide_number - 1) * GRID_ROWS * GRID_COLUMNS) + ((row - 1) * GRID_COLUMNS) + column
                    overrides.append(
                        {
                            "predicate": {"custom_model_data": TILE_BASE_MODEL_DATA + linear_index - 1},
                            "model": f"slides:item/tiles/{tile_name}",
                        }
                    )

    paper_model = {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "minecraft:item/paper"},
        "overrides": overrides,
    }
    PAPER_MODEL_PATH.write_text(json.dumps(paper_model, indent=2) + "\n", encoding="utf-8")

    filled_map_model = {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "minecraft:item/filled_map"},
        "overrides": filled_map_overrides,
    }
    FILLED_MAP_MODEL_PATH.write_text(json.dumps(filled_map_model, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()