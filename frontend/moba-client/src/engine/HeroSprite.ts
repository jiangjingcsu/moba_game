import { Container, Graphics, Text, TextStyle, Texture, AnimatedSprite, Rectangle } from 'pixi.js'
import type { Hero, Vector2D } from '@/types/game'
import { HeroRole } from '@/types/game'

const SPRITE_COLS = 8
const SPRITE_ROWS = 8
const FRAME_WIDTH = 128
const FRAME_HEIGHT = 128
const HERO_DISPLAY_SIZE = 80

const FRAME_MAP = {
  idle_down: [0],
  walk_down: [1, 2, 3, 2],
  idle_left: [8],
  walk_left: [9, 10, 11, 10],
  idle_right: [16],
  walk_right: [17, 18, 19, 18],
  idle_up: [24],
  walk_up: [25, 26, 27, 26],
  attack_down: [4, 5],
  attack_left: [12, 13],
  attack_right: [20, 21],
  attack_up: [28, 29],
  skill_down: [6, 7],
  skill_left: [14, 15],
  skill_right: [22, 23],
  skill_up: [30, 31],
  hurt: [32, 33],
  death: [34, 35],
}

type Direction = 'down' | 'left' | 'right' | 'up'
type Action = 'idle' | 'walk' | 'attack' | 'skill' | 'hurt' | 'death'

export class HeroSprite extends Container {
  private heroData: Hero
  private circleRadius = 40

  private animatedSprite: AnimatedSprite | null = null
  private allFrames: Texture[] = []
  private isMoving = false
  private direction: Direction = 'down'
  private currentAction: Action = 'idle'

  private bodyCircle: Graphics
  private shadow: Graphics
  private healthBarBg: Graphics
  private healthBar: Graphics
  private nameText: Text
  private levelText: Text
  private skillEffect: Graphics

  constructor(hero: Hero) {
    super()
    this.heroData = hero

    this.shadow = new Graphics()
    this.bodyCircle = new Graphics()
    this.healthBarBg = new Graphics()
    this.healthBar = new Graphics()
    this.skillEffect = new Graphics()
    this.nameText = new Text(hero.name, new TextStyle({
      fontSize: 13,
      fill: 0xffffff,
      fontWeight: 'bold',
      dropShadow: true,
      dropShadowColor: '#000000',
      dropShadowBlur: 4,
      dropShadowDistance: 2,
    }))
    this.levelText = new Text(`Lv.${hero.level}`, new TextStyle({
      fontSize: 10,
      fill: 0xffff00,
      fontWeight: 'bold',
    }))

    this.addChild(this.shadow)
    this.addChild(this.bodyCircle)
    this.addChild(this.healthBarBg)
    this.addChild(this.healthBar)
    this.addChild(this.nameText)
    this.addChild(this.levelText)
    this.addChild(this.skillEffect)

    this.x = hero.position.x
    this.y = hero.position.y
    this.draw()
  }

  async loadSpriteSheet(texture: Texture) {
    this.extractFrames(texture)
    this.createAnimatedSprite()
  }

  private extractFrames(texture: Texture) {
    const baseTex = texture.baseTexture
    const imgWidth = baseTex.width
    const imgHeight = baseTex.height
    const fw = Math.floor(imgWidth / SPRITE_COLS)
    const fh = Math.floor(imgHeight / SPRITE_ROWS)

    for (let row = 0; row < SPRITE_ROWS; row++) {
      for (let col = 0; col < SPRITE_COLS; col++) {
        const rect = new Rectangle(col * fw, row * fh, fw, fh)
        this.allFrames.push(new Texture(baseTex, rect))
      }
    }
  }

  private createAnimatedSprite() {
    const idleFrames = this.getFrames('idle', 'down')
    this.animatedSprite = new AnimatedSprite(idleFrames)
    this.animatedSprite.anchor.set(0.5, 0.65)
    this.animatedSprite.width = HERO_DISPLAY_SIZE
    this.animatedSprite.height = HERO_DISPLAY_SIZE
    this.animatedSprite.animationSpeed = 0.1
    this.animatedSprite.loop = true
    this.animatedSprite.play()

    this.addChildAt(this.animatedSprite, 1)
  }

  private getFrames(action: Action, dir: Direction): Texture[] {
    const key = `${action}_${dir}` as keyof typeof FRAME_MAP
    const indices = FRAME_MAP[key] || FRAME_MAP.idle_down
    return indices.map(i => this.allFrames[i] || this.allFrames[0]).filter(Boolean)
  }

  playAnimation(action: Action, dir?: Direction) {
    if (!this.animatedSprite || this.allFrames.length === 0) return

    const newDir = dir || this.direction
    const newAction = action

    if (newAction === this.currentAction && newDir === this.direction) return

    this.currentAction = newAction
    this.direction = newDir

    const frames = this.getFrames(newAction, newDir)
    this.animatedSprite.textures = frames

    switch (newAction) {
      case 'idle':
        this.animatedSprite.animationSpeed = 0.08
        this.animatedSprite.loop = true
        break
      case 'walk':
        this.animatedSprite.animationSpeed = 0.15
        this.animatedSprite.loop = true
        break
      case 'attack':
        this.animatedSprite.animationSpeed = 0.25
        this.animatedSprite.loop = false
        this.animatedSprite.onComplete = () => {
          this.playAnimation(this.isMoving ? 'walk' : 'idle')
        }
        break
      case 'skill':
        this.animatedSprite.animationSpeed = 0.2
        this.animatedSprite.loop = false
        this.animatedSprite.onComplete = () => {
          this.showSkillEffect()
          this.playAnimation(this.isMoving ? 'walk' : 'idle')
        }
        break
      case 'hurt':
        this.animatedSprite.animationSpeed = 0.2
        this.animatedSprite.loop = false
        this.animatedSprite.onComplete = () => {
          this.playAnimation('idle')
        }
        break
      case 'death':
        this.animatedSprite.animationSpeed = 0.1
        this.animatedSprite.loop = false
        break
    }

    this.animatedSprite.play()
  }

  private showSkillEffect() {
    this.skillEffect.clear()
    const teamColors = [0x4a9eff, 0xff4a4a, 0xffa500]
    const color = teamColors[this.heroData.teamId] || 0xffffff
    this.skillEffect.beginFill(color, 0.4)
    this.skillEffect.drawCircle(0, 0, 60)
    this.skillEffect.endFill()
    setTimeout(() => { this.skillEffect.clear() }, 300)
  }

  setIsMoving(moving: boolean) {
    if (moving && !this.isMoving) {
      this.playAnimation('walk')
    } else if (!moving && this.isMoving) {
      this.playAnimation('idle')
    }
    this.isMoving = moving
  }

  setDirection(dir: Direction) {
    if (dir !== this.direction) {
      this.direction = dir
      this.playAnimation(this.currentAction, dir)
    }
  }

  updatePosition(position: Vector2D) {
    if (this.heroData.position.x !== position.x || this.heroData.position.y !== position.y) {
      const dx = position.x - this.x
      const dy = position.y - this.y
      if (Math.abs(dx) > Math.abs(dy)) {
        this.setDirection(dx > 0 ? 'right' : 'left')
      } else {
        this.setDirection(dy > 0 ? 'down' : 'up')
      }
    }
    this.heroData.position = { ...position }
    this.x = position.x
    this.y = position.y
  }

  updateDirectionFromTarget(target: Vector2D) {
    const dx = target.x - this.x
    const dy = target.y - this.y
    if (Math.abs(dx) > Math.abs(dy)) {
      this.setDirection(dx > 0 ? 'right' : 'left')
    } else if (Math.abs(dy) > 0.1) {
      this.setDirection(dy > 0 ? 'down' : 'up')
    }
  }

  draw() {
    this.drawShadow()
    this.drawBodyCircle()
    this.drawHealthBar()
    this.drawTexts()
  }

  private drawShadow() {
    this.shadow.clear()
    this.shadow.beginFill(0x000000, 0.25)
    this.shadow.drawEllipse(0, 30, 28, 10)
    this.shadow.endFill()
  }

  private drawBodyCircle() {
    this.bodyCircle.clear()
    const teamColors = [0x4a9eff, 0xff4a4a, 0xffa500]
    const teamColor = teamColors[this.heroData.teamId] || 0xffffff

    this.bodyCircle.lineStyle(3, teamColor, 0.8)
    this.bodyCircle.beginFill(teamColor, 0.2)
    this.bodyCircle.drawCircle(0, 0, this.circleRadius)
    this.bodyCircle.endFill()

    if (this.heroData.id === 'hero1') {
      this.bodyCircle.lineStyle(2, 0xffffff, 0.6)
      this.bodyCircle.drawCircle(0, 0, this.circleRadius + 3)
    }
  }

  private drawHealthBar() {
    const barWidth = 60
    const barHeight = 5
    const barY = -this.circleRadius - 20

    this.healthBarBg.clear()
    this.healthBarBg.beginFill(0x000000, 0.8)
    this.healthBarBg.drawRoundedRect(-barWidth / 2 - 1, barY - 1, barWidth + 2, barHeight + 2, 3)
    this.healthBarBg.endFill()

    const hpPercent = Math.max(0, Math.min(1, this.heroData.hp / this.heroData.maxHp))
    const hpColor = hpPercent > 0.6 ? 0x2ecc71 : hpPercent > 0.3 ? 0xf39c12 : 0xe74c3c

    this.healthBar.clear()
    if (hpPercent > 0) {
      this.healthBar.beginFill(hpColor)
      this.healthBar.drawRoundedRect(-barWidth / 2, barY, barWidth * hpPercent, barHeight, 2)
      this.healthBar.endFill()
    }
  }

  private drawTexts() {
    this.nameText.x = -this.nameText.width / 2
    this.nameText.y = this.circleRadius + 12
    this.levelText.x = -this.levelText.width / 2
    this.levelText.y = this.circleRadius + 26
  }

  updateHeroData(data: Partial<Hero>) {
    Object.assign(this.heroData, data)
    this.draw()
  }

  getHeroData(): Hero {
    return this.heroData
  }

  getIsMoving(): boolean {
    return this.isMoving
  }

  getDirection(): Direction {
    return this.direction
  }
}
