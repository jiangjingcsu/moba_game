---
name: "game-art-generator"
description: "Generates MOBA game art assets via Leonardo.ai API (hero artwork, map backgrounds, skill icons, skins, UI illustrations). Invoke when user asks to generate any game visual asset."
---

# Game Art Asset Generator

Generates game art assets for MOBA/3v3v3 game using **Leonardo.ai API**.

## Setup

Requires `LEONARDO_API_KEY` environment variable. Get API key from https://leonardo.ai

Set in system environment or use `.env` file in project root:
```
LEONARDO_API_KEY=your_api_key_here
```

## Asset Types & Prompts

### 1. Hero Character Artwork (英雄立绘)
- Style: Fantasy MOBA hero portrait, detailed armor, glowing effects
- Prompt template: `"Epic fantasy hero character portrait for [hero_name], [vivid description of appearance, armor, weapons], dramatic lighting, high detail, digital art, 4K, MOBA game style"`
- Use `leonardo-ultimate` or `dreamarchitect` model

### 2. Map / Scene Backgrounds (地图/场景背景)
- Style: Fantasy battleground, mystical arena, dark fantasy landscape
- Prompt template: `" breathtaking [3v3v3/moba] battle arena map background, [specific terrain description], atmospheric fog, volumetric lighting, game art style, wide angle view, 16:9 aspect ratio"`
- Use `dynamic Freddy` or `pixel art` for pixel style maps

### 3. Skill Icons (技能图标)
- Style: Clean, readable at small size, magical effects, high contrast
- Prompt template: `"clean flat design skill icon for [skill_name] ability, [element type: fire/ice/lightning/shadow], glowing magical effect, transparent background, circular icon, game UI style, 512x512"`
- Use `Phoenix style` or `iconGen` model for icons

### 4. Skins / Equipment Art (皮肤/装备原画)
- Style: Detailed equipment/item illustration, fantasy RPG style
- Prompt template: `"detailed [skin name] equipment/skin for [hero name], [specific costume/weapon description], intricate patterns, glowing runes, fantasy RPG art style, full body or item showcase"`
- Use `RPG XL` or `dreamarchitect` model

### 5. UI Illustrations (UI配图)
- Style: Clean, minimal, matches game UI theme
- Prompt template: `"clean [UI illustration type: loading screen/match found screen/victory screen] illustration for MOBA game, [description], modern game UI style, muted colors with accent highlights, 1920x1080"`
- Use `game character / asset generation` model

## API Usage

### Python Example

```python
import os
import requests

LEONARDO_API_KEY = os.environ.get("LEONARDO_API_KEY")
LEONARDO_URL = "https://cloud.leonardo.ai/api/rest/v1/generations"

def generate_game_asset(prompt: str, model_id: str = "8a7d7e2b-4a2f-4d1e-9c5b-3f8e2a6b1c4d",
                       width: int = 1024, height: int = 1024,
                       num_images: int = 1) -> list[str]:
    """Generate game asset using Leonardo.ai API."""
    headers = {
        "Authorization": f"Bearer {LEONARDO_API_KEY}",
        "Content-Type": "application/json"
    }
    payload = {
        "prompt": prompt,
        "modelId": model_id,
        "width": width,
        "height": height,
        "num_images": num_images,
        "guidance_scale": 7.5,
        "prompt_magic": True,
        "negative_prompt": "low quality, blurry, watermark, text, deformed, ugly"
    }

    # Create generation
    response = requests.post(LEONARDO_URL, json=payload, headers=headers)
    response.raise_for_status()
    generation_id = response.json()["generations_by_pk"]["id"]

    # Poll for completion
    status_url = f"{LEONARDO_URL}/{generation_id}"
    while True:
        result = requests.get(status_url, headers=headers).json()
        status = result["generations_by_pk"]["status"]
        if status == "COMPLETE":
            images = result["generations_by_pk"]["generated_images"]
            return [img["url"] for img in images]
        elif status == "FAILED":
            raise Exception(f"Generation failed: {result}")
        import time; time.sleep(5)

# Usage examples
if __name__ == "__main__":
    # Hero character
    hero_url = generate_game_asset(
        "Epic fantasy hero character portrait for Ember Mage, "
        "fiery red robes, glowing orange eyes, flame-enchanted staff, "
        "dramatic lighting, high detail, digital art, MOBA game style",
        model_id="dreamarchitect",
        width=1024, height=1024
    )

    # Skill icon
    icon_url = generate_game_asset(
        "clean flat design skill icon for Flame Burst ability, fire element, "
        "glowing magical effect, transparent background, circular icon, game UI style",
        model_id="iconGen_v2",
        width=512, height=512
    )
```

### Alternative: Use WebFetch to call Leonardo API

```python
# Via requests library - requires python-dotenv
pip install requests python-dotenv
```

## Model Reference for Leonardo.ai

| Asset Type | Recommended Model | Aspect Ratio |
|-----------|-------------------|--------------|
| Hero Character | `dreamarchitect` | 1:1 (1024x1024) |
| Map Background | `dynamic Freddy` | 16:9 (1344x768) |
| Skill Icon | `Phoenix style` / `iconGen_v2` | 1:1 (512x512) |
| Skin/Equipment | `RPG XL` / `dreamarchitect` | 1:1 (1024x1024) |
| UI Illustration | `game character / asset generation` | 16:9 (1920x1080) |
| Pixel Art Map | `pixel art` | 16:9 (1024x576) |

## Best Practices

1. **Be specific**: Include hero name, element type, color palette in prompts
2. **Add negative prompts**: Always exclude "low quality, blurry, watermark"
3. **Use prompt magic**: Leonardo's enhanced prompt generation helps
4. **Match aspect ratio**: Use correct ratio for asset type (icons=1:1, maps=16:9)
5. **Regenerate and iterate**: Save good seeds, iterate with variations
6. **Post-process**: Upscale with Real-ESRGAN or Leonardo's Ultra for final assets

## Workflow

1. User asks to generate specific asset
2. Identify asset type and select appropriate model/aspect ratio
3. Build detailed prompt with style keywords
4. Call Leonardo API or provide prompt for user to paste into Leonardo UI
5. Suggest filename convention: `{type}_{hero_name}_{variant}.png`
