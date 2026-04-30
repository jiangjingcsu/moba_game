import { Spritesheet, Texture, utils } from 'pixi.js'

export interface SpriteFrame {
  name: string
  frame: { x: number, y: number, width: number, height: number }
}

export interface AnimationConfig {
  frames: string[]
  speed: number
  loop: boolean
}

export class HeroSpriteSheet {
  private spritesheet: Spritesheet | null = null
  private textures: Map<string, Texture> = new Map()
  private animationConfigs: Map<string, AnimationConfig> = new Map()

  async load(texture: Texture) {
    const data = this.generateSpriteSheetData()
    this.spritesheet = new Spritesheet(texture, data, texture.baseTexture)
    await this.spritesheet.parse()

    Object.keys(this.spritesheet.textures).forEach(name => {
      this.textures.set(name, this.spritesheet!.textures[name])
    })

    this.setupAnimations()
  }

  private generateSpriteSheetData() {
    return {
      frames: {
        'attack_left': { frame: { x: 0, y: 0, w: 256, h: 256 }, rotated: false },
        'walk_1': { frame: { x: 256, y: 0, w: 256, h: 256 }, rotated: false },
        'attack_effect': { frame: { x: 512, y: 0, w: 256, h: 256 }, rotated: false },
        'walk_2': { frame: { x: 768, y: 0, w: 256, h: 256 }, rotated: false },
        'attack_right': { frame: { x: 0, y: 256, w: 256, h: 256 }, rotated: false },
        'idle': { frame: { x: 256, y: 256, w: 256, h: 256 }, rotated: false },
        'dodge': { frame: { x: 512, y: 256, w: 256, h: 256 }, rotated: false },
        'back': { frame: { x: 768, y: 256, w: 256, h: 256 }, rotated: false },
      },
      meta: {
        image: 'hero.png',
        size: { w: 1024, h: 512 },
        scale: '1'
      }
    }
  }

  private setupAnimations() {
    this.animationConfigs.set('idle', {
      frames: ['idle'],
      speed: 1,
      loop: true
    })

    this.animationConfigs.set('walk', {
      frames: ['walk_1', 'walk_2', 'idle', 'walk_2'],
      speed: 8,
      loop: true
    })

    this.animationConfigs.set('attack', {
      frames: ['idle', 'attack_left', 'attack_effect', 'attack_right', 'idle'],
      speed: 12,
      loop: false
    })

    this.animationConfigs.set('dodge', {
      frames: ['idle', 'dodge', 'idle'],
      speed: 10,
      loop: false
    })
  }

  getAnimationConfig(name: string): AnimationConfig | undefined {
    return this.animationConfigs.get(name)
  }

  getTexture(name: string): Texture | undefined {
    return this.textures.get(name)
  }

  getAllTextures(): Map<string, Texture> {
    return this.textures
  }

  destroy() {
    if (this.spritesheet) {
      this.spritesheet.destroy()
    }
  }
}
