---
name: "sd-art-generator"
description: "Generates game art via Stable Diffusion (local or cloud). Use when user needs free/self-hosted image generation, or for batch asset production."
---

# Stable Diffusion Art Generator

Generates game art assets using **Stable Diffusion** — free, self-deployable, no API costs.

## Deployment Options

### Option 1:本地部署 (Recommended for privacy/batch)
```bash
# Using ComfyUI (more control)
# Download from https://github.com/comfyanonymous/ComfyUI

# Or using AUTOMATIC1111 WebUI
git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui.git
cd stable-diffusion-webui
# Place your model checkpoints in models/Stable-diffusion/

# Run
./webui-user.bat (Windows)
./webui-user.sh (Linux)
```

### Option 2: 云端 API (Replicate/RunPod)
```bash
pip install replicate
export REPLICATE_API_TOKEN=your_token
```

### Option 3: OpenRouter (Unified API, supports SDXL)
```bash
pip install openai
export OPENAI_API_KEY=your_key
```

## Asset Prompts

### Hero Character
```
(masterpiece, best quality, 8k, high detail), hero portrait, [hero name],
[vivid description], glowing [element] effects, dramatic lighting,
[intimate background: arena/battlefield/temple], digital art,
(moe style:1.3), perfect face, detailed eyes, fantasy RPG,
MOBA game character, full body standing pose
Negative: (worst quality, low quality:1.4), blurry, bad anatomy,
deformed, bad hands, missing fingers, watermark, text
```

### Skill Icon
```
(clean flat design:1.2), [skill name] skill icon for MOBA game,
[intense/glowing/electric/fiery/icy]:[effect description],
magical circle background, minimal details, high contrast,
game UI icon style, transparent PNG, centered composition,
clean lineart, vibrant colors, 512x512 resolution
Negative: (worst quality:1.4), watermark, text, noise, jpeg artifacts
```

### Map Background
```
(wide shot, epic composition:1.3), top-down oblique game map view,
[3v3v3/5v5] MOBA battleground arena, [terrain: forest/desert/volcano/city ruins],
[dynamic elements: lava rivers/crystals/towers], volumetric fog,
[time of day: sunset/dusk/night with bioluminescence], 16:9 aspect,
game environment concept art, atmospheric perspective, ultra detailed
Negative: (worst quality:1.4), low resolution, blurry, text, UI elements
```

### Equipment / Skin
```
(equipment showcase:1.2), detailed [skin name] armor set for [hero name],
[fantasy RPG style], [specific materials: golden armor with glowing runes],
intricate engravings, [dynamic pose or item presentation], dramatic lighting,
[item glow/aura effects], high detail texture, 8k resolution,
professional game asset art
Negative: (worst quality, low quality:1.4), deformed, bad anatomy, watermark
```

### UI Illustration
```
(game UI scene:1.3), [screen type: loading/match found/victory defeat/pick ban],
[MOBA game theme], [specific scene description], [mood: intense/epic/celebratory],
[color palette: dark with gold accents], cinematic composition,
modern game UI illustration style, 1920x1080, ultra detailed
Negative: (worst quality:1.4), blurry, text, deformed, low quality
```

## Python Script (via Replicate)

```python
import os
import replicate

def generate_sd_asset(prompt: str,
                      width: int = 1024,
                      height: int = 1024,
                      num_outputs: int = 1) -> list[str]:
    """Generate asset using SDXL via Replicate."""
    output = replicate.run(
        "stability-ai/sdxl:39ed52f2a78e934b3ba6e2a568f7a335b407f50b86dae8131d67a",
        input={
            "prompt": prompt,
            "negative_prompt": "worst quality, low quality, blurry, "
                             "watermark, text, deformed, bad anatomy",
            "width": width,
            "height": height,
            "num_outputs": num_outputs,
            "guidance_scale": 7.5,
            "num_inference_steps": 50
        }
    )
    return output  # List of image URLs

# Usage
hero_images = generate_sd_asset(
    prompt="(masterpiece, best quality), epic fantasy hero portrait for "
           "Blade Dancer, agile warrior with twin curved swords, dark blue "
           "armor with silver trim, glowing cyan eyes, dramatic lighting, "
           "MOBA game character, full body standing pose",
    width=1024, height=1024
)
```

## ComfyUI Workflow (for batch)

Use these node chains for consistent game assets:

1. **Load Checkpoint** → `realisticVisionV60_b1_22110.safetensors` (characters)
   or `pixelart-flatcolor.safetensors` (pixel style)

2. **Character Pipeline**: Load Checkpoint → CLIP Text Encode (positive)
   → CLIP Text Encode (negative) → KSampler → VAE Decode → Save Image

3. **Icon Pipeline**: Load Checkpoint → ControlNet (canny lineart)
   → CLIP → KSampler → Upscale (RealESRGAN) → PNG Output

## Batch Generation Script

```python
import subprocess
import json
from pathlib import Path

ASSETS = [
    {"type": "hero", "name": "Ember Mage", "aspect": (1024, 1024)},
    {"type": "hero", "name": "Shadow Assassin", "aspect": (1024, 1024)},
    {"type": "skill_icon", "name": "Flame Burst", "aspect": (512, 512)},
    {"type": "skill_icon", "name": "Shadow Step", "aspect": (512, 512)},
    {"type": "map", "name": "Volcano Arena", "aspect": (1344, 768)},
    {"type": "skin", "name": "Dragon Knight Armor", "aspect": (1024, 1024)},
]

def generate_batch():
    output_dir = Path("game_assets")
    output_dir.mkdir(exist_ok=True)

    for asset in ASSETS:
        # Use ComfyUI API or AUTOMATIC1111 API
        # This example uses AUTOMATIC1111 API
        response = requests.post(
            "http://localhost:7860/sdapi/v1/txt2img",
            json={
                "prompt": BUILD_PROMPT(asset),
                "width": asset["aspect"][0],
                "height": asset["aspect"][1],
                "batch_size": 4
            }
        )
        result = response.json()
        for i, img_b64 in enumerate(result["images"]):
            filename = f"{asset['type']}_{asset['name']}_{i}.png"
            save_image(img_b64, output_dir / filename)
```

## File Naming Convention

```
hero_{name}_{variant}.png           # 英雄立绘
map_{name}_{resolution}.png         # 地图背景
icon_{name}_{size}.png              # 技能图标
skin_{hero}_{name}_{variant}.png    # 皮肤原画
ui_{screen_type}_{variant}.png      # UI插画
```

## Recommended Checkpoint Models

| Asset Type | Model | Quality |
|-----------|-------|---------|
| Hero Characters | `realisticVisionV60_b1_22110` | ⭐⭐⭐⭐⭐ |
| Anime Characters | `counterfeitV30` | ⭐⭐⭐⭐⭐ |
| Pixel Art | `pixelart-flatcolor` | ⭐⭐⭐⭐ |
| Game Environments | `epicrealismRaw` | ⭐⭐⭐⭐⭐ |
| Icons/UI | `flat2` | ⭐⭐⭐⭐ |
| General Fantasy | `dreamshaper8` | ⭐⭐⭐⭐ |
