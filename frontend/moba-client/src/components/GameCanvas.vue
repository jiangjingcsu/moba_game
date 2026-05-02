<template>
  <canvas ref="canvasRef" class="game-canvas"></canvas>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { initPixiApp, destroyPixiApp, gameApp } from '@/engine/pixi'
import { GameMap } from '@/engine/GameMap'
import { HeroSprite } from '@/engine/HeroSprite'
import { InputHandler } from '@/engine/InputHandler'
import { lockstepManager } from '@/engine/LockstepManager'
import { useGameStore } from '@/stores/game'
import { HeroRole } from '@/types/game'
import { heroDefinitions } from '@/data/heroes'
import { connectToGateway, sendMatchRequest, enterBattle, isConnected, battlePlayerId, disconnect as disconnectBattle } from '@/network'
import type { Hero, Vector2D, Skill } from '@/types/game'
import { Container, Graphics, Texture } from 'pixi.js'

const canvasRef = ref<HTMLCanvasElement | null>(null)
const gameStore = useGameStore()

let inputHandler: InputHandler | null = null
let heroSprites: Map<string, HeroSprite> = new Map()
let animationId: number | null = null

let worldContainer: Container | null = null
let gameMap: GameMap | null = null
let heroLayer: Container | null = null
let moveIndicator: Graphics | null = null
let cameraOffset: Vector2D = { x: 0, y: 0 }
let cameraZoom = 1
let cameraTarget: Vector2D = { x: 400, y: 400 }

const moveTargets: Map<string, Vector2D> = new Map()
let heroSpriteSheetTextures: Map<string, Texture> = new Map()
let tickAccumulator = 0
const TICK_RATE = 15
const TICK_INTERVAL = 1000 / TICK_RATE
let lastTime = 0

onMounted(async () => {
  if (!canvasRef.value) return

  await initPixiApp(canvasRef.value)

  worldContainer = new Container()
  gameApp.value!.stage.addChild(worldContainer)

  gameMap = new GameMap()
  await gameMap.init()
  worldContainer.addChild(gameMap)

  heroLayer = new Container()
  worldContainer.addChild(heroLayer)

  moveIndicator = new Graphics()
  worldContainer.addChild(moveIndicator)

  await loadHeroSpriteSheets()

  inputHandler = new InputHandler()
  inputHandler.setup(canvasRef.value)

  inputHandler.setMoveCallback((screenPos: Vector2D) => {
    handleMoveInput(screenPos)
  })

  inputHandler.setSkillCallback((input) => {
    handleSkillInput(input.skillId)
  })

  inputHandler.setAbilityCallback((ability: string) => {
    if (ability === 'center_camera' && gameStore.myHero) {
      cameraTarget = { ...gameStore.myHero.position }
    }
  })

  lockstepManager.init(gameStore)

  const onlineMode = await tryConnectBattleServer()
  gameStore.isOnlineMode = onlineMode

  lockstepManager.start(!onlineMode)

  await initTestHeroes(heroLayer)

  if (gameStore.myHero) {
    cameraTarget = { ...gameStore.myHero.position }
    cameraOffset = { ...cameraTarget }
  }

  lastTime = performance.now()
  gameLoop()
})

onUnmounted(() => {
  if (animationId) cancelAnimationFrame(animationId)
  lockstepManager.stop()
  if (inputHandler) inputHandler.cleanup()
  if (worldContainer) worldContainer.destroy({ children: true })
  destroyPixiApp()
  heroSprites.clear()
  moveTargets.clear()
  heroSpriteSheetTextures.clear()
  if (gameStore.isOnlineMode) {
    disconnectBattle()
  }
})

async function loadHeroSpriteSheets() {
  const heroFiles: Record<string, string> = {
    [HeroRole.WARRIOR]: '/assets/heroes/warrior.png',
    [HeroRole.MAGE]: '/assets/heroes/mage.png',
    [HeroRole.ASSASSIN]: '/assets/heroes/assassin.png',
    [HeroRole.MARKSMAN]: '/assets/heroes/marksman.png',
    [HeroRole.SUPPORT]: '/assets/heroes/support.png',
    [HeroRole.TANK]: '/assets/heroes/tank.png',
  }

  for (const [role, path] of Object.entries(heroFiles)) {
    try {
      const texture = Texture.from(path)
      await new Promise<void>((resolve, reject) => {
        if (texture.baseTexture.valid) {
          resolve()
        } else {
          texture.baseTexture.on('loaded', () => resolve())
          texture.baseTexture.on('error', () => reject())
        }
      })
      heroSpriteSheetTextures.set(role, texture)
      console.log(`Loaded sprite sheet for ${role}`)
    } catch {
      console.warn(`No sprite sheet for ${role}, using fallback`)
    }
  }
}

function handleMoveInput(screenPos: Vector2D) {
  if (!worldContainer || !gameStore.myHero) return
  const app = gameApp.value
  if (!app) return

  const worldX = (screenPos.x - worldContainer.x) / cameraZoom
  const worldY = (screenPos.y - worldContainer.y) / cameraZoom

  const targetX = Math.max(100, Math.min(worldX, 3900))
  const targetY = Math.max(100, Math.min(worldY, 3900))

  moveTargets.set(gameStore.myHeroId, { x: targetX, y: targetY })

  if (gameStore.isOnlineMode) {
    lockstepManager.queueInput(gameStore.myHeroId, 'move', {
      targetX,
      targetY,
    })
  }

  if (moveIndicator) {
    moveIndicator.clear()
    moveIndicator.lineStyle(2, 0x00ff00, 0.9)
    moveIndicator.drawCircle(targetX, targetY, 15)
    moveIndicator.moveTo(targetX - 20, targetY)
    moveIndicator.lineTo(targetX + 20, targetY)
    moveIndicator.moveTo(targetX, targetY - 20)
    moveIndicator.lineTo(targetX, targetY + 20)
    setTimeout(() => { if (moveIndicator) moveIndicator.clear() }, 400)
  }
}

function handleSkillInput(skillId: string) {
  if (!gameStore.myHero || !gameStore.myHero.isAlive) return

  const skill = gameStore.myHero.skills.find(s => s.id === skillId)
  if (!skill || skill.currentCooldown > 0) return
  if (gameStore.myHero.mp < skill.manaCost) return

  const sprite = heroSprites.get(gameStore.myHeroId)
  if (sprite) {
    sprite.playAnimation('skill')
  }

  lockstepManager.queueInput(gameStore.myHeroId, 'skill', {
    skillId,
    targetX: gameStore.myHero.position.x,
    targetY: gameStore.myHero.position.y,
  })
}

function updateMovement() {
  if (gameStore.isOnlineMode) {
    updateOnlineHeroPositions()
    return
  }

  moveTargets.forEach((target, heroId) => {
    const sprite = heroSprites.get(heroId)
    if (!sprite) {
      moveTargets.delete(heroId)
      return
    }

    const hero = sprite.getHeroData()
    if (!hero.isAlive) {
      moveTargets.delete(heroId)
      sprite.setIsMoving(false)
      return
    }

    const dx = target.x - hero.position.x
    const dy = target.y - hero.position.y
    const distance = Math.sqrt(dx * dx + dy * dy)

    if (distance > 5) {
      const moveSpeed = hero.baseStats.moveSpeed / 60
      const moveX = (dx / distance) * moveSpeed
      const moveY = (dy / distance) * moveSpeed

      hero.position.x += moveX
      hero.position.y += moveY

      sprite.updateDirectionFromTarget(target)
      sprite.updatePosition(hero.position)
      sprite.setIsMoving(true)

      if (heroId === gameStore.myHeroId) {
        checkAutoAttack(hero)
      }
    } else {
      moveTargets.delete(heroId)
      sprite.setIsMoving(false)
    }
  })
}

function updateOnlineHeroPositions() {
  gameStore.heroes.forEach((hero) => {
    const sprite = heroSprites.get(hero.id)
    if (!sprite) return

    sprite.updatePosition(hero.position)

    const prevData = sprite.getHeroData()
    const dx = hero.position.x - prevData.position.x
    const dy = hero.position.y - prevData.position.y
    const dist = Math.sqrt(dx * dx + dy * dy)
    sprite.setIsMoving(dist > 1)

    if (dist > 1) {
      sprite.updateDirectionFromTarget(hero.position)
    }
  })
}

function checkAutoAttack(hero: Hero) {
  let closestEnemy: Hero | null = null
  let closestDist = hero.range

  gameStore.heroes.forEach((otherHero) => {
    if (otherHero.teamId === hero.teamId || !otherHero.isAlive) return
    const dx = otherHero.position.x - hero.position.x
    const dy = otherHero.position.y - hero.position.y
    const dist = Math.sqrt(dx * dx + dy * dy)
    if (dist < closestDist) {
      closestDist = dist
      closestEnemy = otherHero
    }
  })

  if (closestEnemy) {
    const sprite = heroSprites.get(hero.id)
    if (sprite) {
      sprite.playAnimation('attack')
    }
    lockstepManager.queueInput(hero.id, 'attack', {
      targetX: closestEnemy.position.x,
      targetY: closestEnemy.position.y,
    })
  }
}

function updateCamera() {
  if (!worldContainer || !gameApp.value || !gameStore.myHero) return

  cameraTarget = { ...gameStore.myHero.position }

  cameraOffset.x += (cameraTarget.x - cameraOffset.x) * 0.08
  cameraOffset.y += (cameraTarget.y - cameraOffset.y) * 0.08

  const screenW = gameApp.value.screen.width / 2
  const screenH = gameApp.value.screen.height / 2

  worldContainer.x = -cameraOffset.x * cameraZoom + screenW
  worldContainer.y = -cameraOffset.y * cameraZoom + screenH
  worldContainer.scale.set(cameraZoom)
}

function syncHeroSprites() {
  gameStore.heroes.forEach((hero) => {
    const sprite = heroSprites.get(hero.id)
    if (sprite) {
      sprite.updateHeroData({
        hp: hero.hp,
        maxHp: hero.maxHp,
        mp: hero.mp,
        maxMp: hero.maxMp,
        level: hero.level,
        isAlive: hero.isAlive,
      })

      if (!hero.isAlive) {
        sprite.playAnimation('death')
        sprite.alpha = 0.5
      } else {
        sprite.alpha = 1
      }
    }
  })
}

function createTestHero(id: string, name: string, title: string, role: HeroRole, teamId: number, position: Vector2D, playerName: string): Hero {
  const definition = heroDefinitions.find(d => d.role === role)
  const baseHp = definition?.baseStats.hp || 1000
  const baseMp = definition?.baseStats.mp || 400

  const skills: Skill[] = (definition?.skills || []).map(s => ({
    id: s.id,
    name: s.name,
    icon: s.id === 'passive' ? '🌀' : s.id === 'q' ? '⚡' : s.id === 'w' ? '🛡️' : s.id === 'e' ? '💨' : '🔥',
    description: s.description,
    cooldown: s.cooldown,
    currentCooldown: 0,
    manaCost: s.manaCost,
    damage: s.damage,
    range: s.range,
    type: s.type,
  }))

  return {
    id, name, title, role, teamId, playerId: id, playerName,
    position: { ...position },
    hp: baseHp, maxHp: baseHp, mp: baseMp, maxMp: baseMp,
    level: 1, experience: 0, experienceToNextLevel: 100,
    baseStats: {
      attack: definition?.baseStats.attack || 60,
      abilityPower: definition?.baseStats.abilityPower || 0,
      armor: definition?.baseStats.armor || 30,
      magicResist: definition?.baseStats.magicResist || 30,
      attackSpeed: definition?.baseStats.attackSpeed || 0.65,
      criticalStrike: 0,
      moveSpeed: definition?.baseStats.moveSpeed || 340,
      healthRegen: 5, manaRegen: 5, cooldownReduction: 0,
    },
    bonusStats: {
      attack: 0, abilityPower: 0, armor: 0, magicResist: 0,
      attackSpeed: 0, criticalStrike: 0, moveSpeed: 0,
      healthRegen: 0, manaRegen: 0, cooldownReduction: 0,
    },
    skills,
    items: [null, null, null, null, null, null],
    runes: { primary: { tree: 'precision', keystone: 'conqueror', slots: [] }, secondary: { tree: 'resolve', slots: [] }, shards: [] },
    kda: { kills: 0, deaths: 0, assists: 0, cs: 0, gold: 500, goldEarned: 500, damageDealt: 0, damageTaken: 0, healingDone: 0, visionScore: 0, wardsPlaced: 0, wardsDestroyed: 0 },
    isAlive: true, respawnTimer: 0, isRecalling: false,
    speed: 5, range: definition?.baseStats.range || 150,
    attackDamage: definition?.baseStats.attackDamage || 60,
    abilityPower: definition?.baseStats.abilityPower || 0,
    armor: definition?.baseStats.armor || 30,
    magicResist: definition?.baseStats.magicResist || 30,
    summonerSpells: [
      { id: 'flash', name: '闪现', icon: '✨', cooldown: 300, currentCooldown: 0, description: '瞬移' },
      { id: 'heal', name: '治疗', icon: '❤️', cooldown: 240, currentCooldown: 0, description: '治疗' },
    ],
  }
}

async function initTestHeroes(container: Container) {
  const heroes: Hero[] = [
    createTestHero('hero1', '裂天战神', '不朽之躯', HeroRole.WARRIOR, 0, { x: 400, y: 400 }, '玩家一'),
    createTestHero('hero2', '霜华魔女', '寒冰使者', HeroRole.MAGE, 1, { x: 3600, y: 3600 }, '玩家二'),
    createTestHero('hero3', '影刃刺客', '暗影猎手', HeroRole.ASSASSIN, 2, { x: 2000, y: 400 }, '玩家三'),
    createTestHero('hero4', '星辰弓手', '破晓之光', HeroRole.MARKSMAN, 0, { x: 500, y: 500 }, '玩家四'),
    createTestHero('hero5', '生命之灵', '治愈使者', HeroRole.SUPPORT, 1, { x: 3500, y: 3500 }, '玩家五'),
    createTestHero('hero6', '钢铁堡垒', '不动如山', HeroRole.TANK, 2, { x: 2100, y: 500 }, '玩家六'),
  ]

  for (const hero of heroes) {
    gameStore.addHero(hero)
    const sprite = new HeroSprite(hero)

    const texture = heroSpriteSheetTextures.get(hero.role)
    if (texture) {
      try {
        await sprite.loadSpriteSheet(texture)
      } catch (e) {
        console.warn(`Failed to load sprite sheet for ${hero.name}`, e)
      }
    }

    heroSprites.set(hero.id, sprite)
    container.addChild(sprite)
  }

  let myHeroId = 'hero1'
  if (gameStore.selectedHeroId) {
    const selectedDef = heroDefinitions.find(d => d.id === gameStore.selectedHeroId)
    if (selectedDef) {
      for (const [id, hero] of gameStore.heroes) {
        if (hero.role === selectedDef.role) {
          myHeroId = id
          break
        }
      }
    }
  }
  gameStore.setMyHero(myHeroId)
}

async function tryConnectBattleServer(): Promise<boolean> {
  try {
    const connected = await connectToGateway()
    if (!connected) {
      console.warn('Failed to connect to gateway, using offline mode')
      return false
    }

    const matchResult = await sendMatchRequest()
    if (!matchResult.success) {
      console.warn('Match request failed, using offline mode')
      return false
    }

    if (matchResult.battleId) {
      const heroRole = heroDefinitions.find(d => d.id === gameStore.selectedHeroId)
      const heroIdNum = heroRole
        ? ['warrior', 'mage', 'assassin', 'marksman', 'support', 'tank'].indexOf(heroRole.role) + 1
        : 1

      const enterResult = await enterBattle(matchResult.battleId, heroIdNum, 0)
      if (!enterResult.success) {
        console.warn('Enter battle failed:', enterResult.errorMessage)
        return false
      }

      console.log('Entered battle:', enterResult.battleId, 'mapId:', enterResult.mapId)
      return true
    }

    return false
  } catch (e) {
    console.warn('Battle server connection error, using offline mode:', e)
    return false
  }
}

function gameLoop() {
  const now = performance.now()
  const deltaTime = (now - lastTime) / 1000
  lastTime = now

  gameStore.updateGameTime(deltaTime)

  tickAccumulator += deltaTime * 1000
  while (tickAccumulator >= TICK_INTERVAL) {
    lockstepManager.tick(TICK_INTERVAL / 1000)
    tickAccumulator -= TICK_INTERVAL
  }

  updateMovement()
  syncHeroSprites()
  updateCamera()

  animationId = requestAnimationFrame(gameLoop)
}
</script>

<style scoped>
.game-canvas {
  width: 100%;
  height: 100%;
  display: block;
  position: absolute;
  top: 0;
  left: 0;
}
</style>
