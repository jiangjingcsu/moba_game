import { Container, Graphics, Texture, Sprite } from 'pixi.js'
import { gameApp } from './pixi'
import type { Vector2D } from '@/types/game'

export class GameMap extends Container {
  private gridLayer: Graphics
  private terrainLayer: Graphics
  private objectLayer: Container
  private clickIndicator: Graphics

  public readonly MAP_WIDTH = 4000
  public readonly MAP_HEIGHT = 4000
  public readonly GRID_SIZE = 100

  private cameraOffset: Vector2D = { x: 0, y: 0 }
  private cameraZoom = 1

  constructor() {
    super()
    this.gridLayer = new Graphics()
    this.terrainLayer = new Graphics()
    this.objectLayer = new Container()
    this.clickIndicator = new Graphics()

    this.addChild(this.terrainLayer)
    this.addChild(this.gridLayer)
    this.addChild(this.objectLayer)
    this.addChild(this.clickIndicator)
  }

  async init() {
    this.drawTerrain()
    this.drawRiver()
    this.drawLanes()
    this.drawGrid()
    this.drawSpawnPoints()
    this.drawTowers()
    this.drawJungleCamps()
    this.drawMapBorders()
  }

  private drawTerrain() {
    const g = this.terrainLayer
    g.clear()

    g.beginFill(0x1a3a2a)
    g.drawRect(0, 0, this.MAP_WIDTH, this.MAP_HEIGHT)
    g.endFill()

    for (let i = 0; i < 50; i++) {
      const x = Math.random() * this.MAP_WIDTH
      const y = Math.random() * this.MAP_HEIGHT
      const size = 30 + Math.random() * 80
      const shade = 0x1a3a2a + Math.floor(Math.random() * 0x0a1a0a)
      
      g.beginFill(shade, 0.3)
      g.drawCircle(x, y, size)
      g.endFill()
    }
  }

  private drawRiver() {
    const g = this.terrainLayer
    
    g.lineStyle(3, 0x4a8abf, 0.4)
    g.beginFill(0x2a6a9f, 0.3)
    
    g.moveTo(0, this.MAP_HEIGHT)
    g.quadraticCurveTo(this.MAP_WIDTH * 0.3, this.MAP_HEIGHT * 0.7, this.MAP_WIDTH * 0.5, this.MAP_HEIGHT * 0.5)
    g.quadraticCurveTo(this.MAP_WIDTH * 0.7, this.MAP_HEIGHT * 0.3, this.MAP_WIDTH, 0)
    g.lineTo(this.MAP_WIDTH, 80)
    g.quadraticCurveTo(this.MAP_WIDTH * 0.7, this.MAP_HEIGHT * 0.3 + 80, this.MAP_WIDTH * 0.5, this.MAP_HEIGHT * 0.5 + 80)
    g.quadraticCurveTo(this.MAP_WIDTH * 0.3, this.MAP_HEIGHT * 0.7 + 80, 0, this.MAP_HEIGHT - 80)
    g.closePath()
    g.endFill()

    g.lineStyle(0)
    for (let i = 0; i < 20; i++) {
      const x = (this.MAP_WIDTH / 20) * i + Math.random() * 100
      const y = this.MAP_HEIGHT - x + Math.random() * 60 - 30
      g.beginFill(0x6aaaff, 0.2)
      g.drawCircle(x, y, 3 + Math.random() * 5)
      g.endFill()
    }
  }

  private drawLanes() {
    const g = this.terrainLayer

    g.lineStyle(80, 0x2a4a3a, 0.5)
    
    g.moveTo(200, 200)
    g.lineTo(this.MAP_WIDTH - 200, this.MAP_HEIGHT - 200)

    g.lineStyle(0)
    
    for (let i = 0; i < 100; i++) {
      const t = i / 100
      const x = 200 + (this.MAP_WIDTH - 400) * t
      const y = 200 + (this.MAP_HEIGHT - 400) * t
      
      g.beginFill(0x3a6a5a, 0.2)
      g.drawCircle(x + (Math.random() - 0.5) * 60, y + (Math.random() - 0.5) * 60, 2 + Math.random() * 3)
      g.endFill()
    }

    g.lineStyle(40, 0x2a4a3a, 0.4)
    g.moveTo(200, this.MAP_HEIGHT - 200)
    g.lineTo(this.MAP_WIDTH / 2, this.MAP_HEIGHT / 2)
    g.lineTo(this.MAP_WIDTH - 200, this.MAP_HEIGHT - 200)

    g.moveTo(this.MAP_WIDTH - 200, 200)
    g.lineTo(this.MAP_WIDTH / 2, this.MAP_HEIGHT / 2)
    g.lineTo(200, 200)
  }

  private drawGrid() {
    const g = this.gridLayer
    g.clear()
    g.lineStyle(0.5, 0xffffff, 0.05)

    for (let x = 0; x <= this.MAP_WIDTH; x += this.GRID_SIZE) {
      g.moveTo(x, 0)
      g.lineTo(x, this.MAP_HEIGHT)
    }

    for (let y = 0; y <= this.MAP_HEIGHT; y += this.GRID_SIZE) {
      g.moveTo(0, y)
      g.lineTo(this.MAP_WIDTH, y)
    }
  }

  private drawSpawnPoints() {
    const g = this.terrainLayer

    g.beginFill(0x4a9eff, 0.15)
    g.drawCircle(400, 400, 200)
    g.endFill()
    g.lineStyle(2, 0x4a9eff, 0.6)
    g.drawCircle(400, 400, 200)

    g.beginFill(0xff4a4a, 0.15)
    g.drawCircle(3600, 3600, 200)
    g.endFill()
    g.lineStyle(2, 0xff4a4a, 0.6)
    g.drawCircle(3600, 3600, 200)

    g.beginFill(0xffa500, 0.15)
    g.drawCircle(2000, 400, 200)
    g.endFill()
    g.lineStyle(2, 0xffa500, 0.6)
    g.drawCircle(2000, 400, 200)
  }

  private drawTowers() {
    const towerPositions = [
      { x: 800, y: 800, team: 0, tier: 1 },
      { x: 1200, y: 1200, team: 0, tier: 2 },
      { x: 3200, y: 3200, team: 1, tier: 1 },
      { x: 2800, y: 2800, team: 1, tier: 2 },
      { x: 600, y: 3400, team: 2, tier: 1 },
      { x: 3400, y: 600, team: 2, tier: 2 },
    ]

    towerPositions.forEach(pos => {
      const g = new Graphics()
      const colors = [0x4a9eff, 0xff4a4a, 0xffa500]
      const color = colors[pos.team]
      
      g.lineStyle(2, 0xffffff, 0.3)
      g.beginFill(color, 0.4)
      g.drawRect(pos.x - 25, pos.y - 25, 50, 50)
      g.endFill()
      
      g.lineStyle(2, color, 0.8)
      g.drawCircle(pos.x, pos.y, 35)
      g.endFill()
      
      g.lineStyle(0)
      g.beginFill(0xffffff, 0.8)
      g.drawCircle(pos.x, pos.y, 8)
      g.endFill()

      this.objectLayer.addChild(g)
    })
  }

  private drawJungleCamps() {
    const camps = [
      { x: 1500, y: 1000, type: 'buff' },
      { x: 2500, y: 1000, type: 'buff' },
      { x: 1500, y: 3000, type: 'buff' },
      { x: 2500, y: 3000, type: 'buff' },
      { x: 2000, y: 2000, type: 'boss' },
    ]

    camps.forEach(camp => {
      const g = new Graphics()
      const color = camp.type === 'boss' ? 0xff6600 : 0xffd700
      
      g.lineStyle(2, color, 0.6)
      g.beginFill(color, 0.3)
      g.drawCircle(camp.x, camp.y, 45)
      g.endFill()
      
      g.lineStyle(0)
      for (let i = 0; i < 3; i++) {
        const angle = (i / 3) * Math.PI * 2
        const x = camp.x + Math.cos(angle) * 20
        const y = camp.y + Math.sin(angle) * 20
        g.beginFill(color, 0.5)
        g.drawCircle(x, y, 8)
        g.endFill()
      }

      this.objectLayer.addChild(g)
    })
  }

  private drawMapBorders() {
    const g = this.terrainLayer
    
    g.lineStyle(4, 0x0a1a0a, 0.8)
    g.drawRect(0, 0, this.MAP_WIDTH, this.MAP_HEIGHT)
  }

  showClickIndicator(worldPos: Vector2D) {
    this.clickIndicator.clear()
    
    this.clickIndicator.lineStyle(2, 0x00ff00, 0.8)
    this.clickIndicator.drawCircle(worldPos.x, worldPos.y, 15)
    
    this.clickIndicator.lineStyle(1, 0x00ff00, 0.5)
    this.clickIndicator.moveTo(worldPos.x - 20, worldPos.y)
    this.clickIndicator.lineTo(worldPos.x + 20, worldPos.y)
    this.clickIndicator.moveTo(worldPos.x, worldPos.y - 20)
    this.clickIndicator.lineTo(worldPos.x, worldPos.y + 20)

    setTimeout(() => {
      this.clickIndicator.clear()
    }, 500)
  }

  followTarget(position: Vector2D) {
    const app = gameApp.value
    if (!app) return

    this.cameraOffset.x = position.x - app.screen.width / 2 / this.cameraZoom
    this.cameraOffset.y = position.y - app.screen.height / 2 / this.cameraZoom

    this.cameraOffset.x = Math.max(0, Math.min(this.cameraOffset.x, this.MAP_WIDTH - app.screen.width / this.cameraZoom))
    this.cameraOffset.y = Math.max(0, Math.min(this.cameraOffset.y, this.MAP_HEIGHT - app.screen.height / this.cameraZoom))

    this.x = -this.cameraOffset.x * this.cameraZoom
    this.y = -this.cameraOffset.y * this.cameraZoom
    this.scale.set(this.cameraZoom)
  }

  worldToScreen(worldPos: Vector2D): Vector2D {
    const app = gameApp.value
    if (!app) return { x: 0, y: 0 }

    return {
      x: (worldPos.x - this.cameraOffset.x) * this.cameraZoom,
      y: (worldPos.y - this.cameraOffset.y) * this.cameraZoom,
    }
  }

  screenToWorld(screenPos: Vector2D): Vector2D {
    return {
      x: screenPos.x / this.cameraZoom + this.cameraOffset.x,
      y: screenPos.y / this.cameraZoom + this.cameraOffset.y,
    }
  }

  zoomIn() {
    this.cameraZoom = Math.min(this.cameraZoom * 1.2, 2)
  }

  zoomOut() {
    this.cameraZoom = Math.max(this.cameraZoom / 1.2, 0.5)
  }

  getZoom(): number {
    return this.cameraZoom
  }
}
