import { useGameStore } from '@/stores/game'
import { getFrameBuffer, getCurrentFrame, sendMoveInput, sendSkillInput, isConnected, isSynced, requestResync } from '@/network'
import type { Hero, Vector2D, Skill } from '@/types/game'
import { heroDefinitions } from '@/data/heroes'

const TICK_RATE = 15
const TICK_INTERVAL = 1000 / TICK_RATE

export class LockstepManager {
  private gameStore: ReturnType<typeof useGameStore> | null = null
  private currentFrame = 0
  private lastTickTime = 0
  private isRunning = false
  private isOfflineMode = true

  private pendingInputs: Map<number, { heroId: string; type: string; targetX?: number; targetY?: number; skillId?: string }[]> = new Map()

  init(store: ReturnType<typeof useGameStore>) {
    this.gameStore = store
  }

  start(offlineMode = true) {
    this.isOfflineMode = offlineMode
    this.isRunning = true
    this.lastTickTime = performance.now()
    this.currentFrame = 0
  }

  stop() {
    this.isRunning = false
  }

  tick(deltaTime: number) {
    if (!this.isRunning || !this.gameStore) return

    this.currentFrame++

    if (this.isOfflineMode) {
      this.processOfflineTick(deltaTime)
    } else {
      this.processOnlineTick()
    }
  }

  private processOfflineTick(deltaTime: number) {
    this.gameStore.heroes.forEach((hero) => {
      if (!hero.isAlive) {
        this.updateRespawn(hero)
        return
      }

      this.regenResources(hero)
      this.updateCooldowns(hero)
      this.processPendingActions(hero)
    })
  }

  private processOnlineTick() {
    const frameBuffer = getFrameBuffer()

    for (const frameData of frameBuffer) {
      if (frameData.frame <= this.currentFrame) continue

      this.currentFrame = frameData.frame

      if (frameData.heroes) {
        for (const heroUpdate of frameData.heroes) {
          const heroId = String(heroUpdate.id)
          const existingHero = this.gameStore!.heroes.get(heroId)

          if (existingHero) {
            this.gameStore!.updateHero(heroId, heroUpdate)
          } else {
            this.addOnlineHero(heroId, heroUpdate)
          }
        }
      }
    }
  }

  private addOnlineHero(heroId: string, data: any) {
    const roleMap: Record<number, HeroRole> = {
      1: HeroRole.WARRIOR,
      2: HeroRole.MAGE,
      3: HeroRole.ASSASSIN,
      4: HeroRole.MARKSMAN,
      5: HeroRole.TANK,
      6: HeroRole.SUPPORT,
    }

    const role = roleMap[data.heroId] || HeroRole.WARRIOR
    const definition = heroDefinitions.find(d => d.role === role)
    const baseHp = data.maxHp || definition?.baseStats.hp || 5000
    const baseMp = data.maxMp || definition?.baseStats.mp || 2000

    const hero: Hero = {
      id: heroId,
      name: definition?.name || `Hero_${heroId}`,
      title: definition?.title || '',
      role,
      teamId: data.teamId ?? 0,
      playerId: heroId,
      playerName: `Player_${heroId}`,
      position: data.position || { x: 0, y: 0 },
      hp: data.hp ?? baseHp,
      maxHp: data.maxHp ?? baseHp,
      mp: data.mp ?? baseMp,
      maxMp: data.maxMp ?? baseMp,
      level: data.level ?? 1,
      experience: 0,
      experienceToNextLevel: 100,
      baseStats: {
        attack: definition?.baseStats.attack || 100,
        abilityPower: definition?.baseStats.abilityPower || 0,
        armor: definition?.baseStats.armor || 50,
        magicResist: definition?.baseStats.magicResist || 50,
        attackSpeed: definition?.baseStats.attackSpeed || 0.65,
        criticalStrike: 0,
        moveSpeed: definition?.baseStats.moveSpeed || 350,
        healthRegen: 5,
        manaRegen: 5,
        cooldownReduction: 0,
      },
      bonusStats: {
        attack: 0, abilityPower: 0, armor: 0, magicResist: 0,
        attackSpeed: 0, criticalStrike: 0, moveSpeed: 0,
        healthRegen: 0, manaRegen: 0, cooldownReduction: 0,
      },
      skills: (definition?.skills || []).map(s => ({
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
      })),
      items: [null, null, null, null, null, null],
      runes: { primary: { tree: 'precision', keystone: 'conqueror', slots: [] }, secondary: { tree: 'resolve', slots: [] }, shards: [] },
      kda: { kills: data.kills ?? 0, deaths: data.deaths ?? 0, assists: data.assists ?? 0, cs: 0, gold: data.gold ?? 0, goldEarned: data.gold ?? 0, damageDealt: 0, damageTaken: 0, healingDone: 0, visionScore: 0, wardsPlaced: 0, wardsDestroyed: 0 },
      isAlive: data.isAlive ?? true,
      respawnTimer: 0,
      isRecalling: false,
      speed: 5,
      range: definition?.baseStats.range || 150,
      attackDamage: definition?.baseStats.attack || 100,
      abilityPower: definition?.baseStats.abilityPower || 0,
      armor: definition?.baseStats.armor || 50,
      magicResist: definition?.baseStats.magicResist || 50,
      summonerSpells: [
        { id: 'flash', name: '闪现', icon: '✨', cooldown: 300, currentCooldown: 0, description: '瞬移' },
        { id: 'heal', name: '治疗', icon: '❤️', cooldown: 240, currentCooldown: 0, description: '治疗' },
      ],
    }

    this.gameStore!.addHero(hero)
  }

  queueInput(heroId: string, type: string, data?: { targetX?: number; targetY?: number; skillId?: string }) {
    const input = { heroId, type, ...data }

    if (this.isOfflineMode) {
      if (!this.pendingInputs.has(this.currentFrame)) {
        this.pendingInputs.set(this.currentFrame, [])
      }
      this.pendingInputs.get(this.currentFrame)!.push(input)
    } else {
      if (type === 'move' && data?.targetX !== undefined && data?.targetY !== undefined) {
        sendMoveInput(heroId, data.targetX, data.targetY, this.currentFrame)
      } else if (type === 'skill' && data?.skillId && data?.targetX !== undefined && data?.targetY !== undefined) {
        sendSkillInput(heroId, data.skillId, data.targetX, data.targetY, this.currentFrame)
      }
    }
  }

  private processPendingActions(hero: Hero) {
    const inputs = this.pendingInputs.get(this.currentFrame)
    if (!inputs) return

    for (const input of inputs) {
      if (input.heroId !== hero.id) continue

      switch (input.type) {
        case 'skill':
          if (input.skillId) {
            this.executeSkill(hero, input.skillId, { x: input.targetX || hero.position.x, y: input.targetY || hero.position.y })
          }
          break
        case 'attack':
          this.executeAttack(hero, input.targetX || 0, input.targetY || 0)
          break
        case 'item':
          break
      }
    }

    this.pendingInputs.delete(this.currentFrame)
  }

  private regenResources(hero: Hero) {
    if (!hero.isAlive) return

    hero.hp = Math.min(hero.maxHp, hero.hp + hero.baseStats.healthRegen / TICK_RATE)
    hero.mp = Math.min(hero.maxMp, hero.mp + hero.baseStats.manaRegen / TICK_RATE)
  }

  private updateCooldowns(hero: Hero) {
    for (const skill of hero.skills) {
      if (skill.currentCooldown > 0) {
        skill.currentCooldown = Math.max(0, skill.currentCooldown - 1 / TICK_RATE)
      }
    }
    for (const spell of hero.summonerSpells) {
      if (spell.currentCooldown > 0) {
        spell.currentCooldown = Math.max(0, spell.currentCooldown - 1 / TICK_RATE)
      }
    }
  }

  private executeSkill(hero: Hero, skillId: string, target: Vector2D) {
    const skill = hero.skills.find(s => s.id === skillId)
    if (!skill || skill.currentCooldown > 0 || hero.mp < skill.manaCost) return

    hero.mp -= skill.manaCost
    skill.currentCooldown = skill.cooldown

    if (skill.damage > 0) {
      this.gameStore!.heroes.forEach((otherHero) => {
        if (otherHero.teamId === hero.teamId || !otherHero.isAlive) return

        const dx = otherHero.position.x - target.x
        const dy = otherHero.position.y - target.y
        const dist = Math.sqrt(dx * dx + dy * dy)

        if (dist <= skill.range) {
          const damage = this.calculateDamage(hero, otherHero, skill.damage)
          this.applyDamage(otherHero, damage, hero)
        }
      })
    }

    this.gameStore!.addGameEvent({
      type: 'kill',
      timestamp: Date.now(),
      data: { heroId: hero.id, skillId, target }
    })
  }

  private executeAttack(hero: Hero, targetX: number, targetY: number) {
    let closestEnemy: Hero | null = null
    let closestDist = hero.range

    this.gameStore!.heroes.forEach((otherHero) => {
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
      const damage = this.calculateDamage(hero, closestEnemy, hero.attackDamage)
      this.applyDamage(closestEnemy, damage, hero)
    }
  }

  private calculateDamage(attacker: Hero, defender: Hero, baseDamage: number): number {
    const isPhysical = attacker.abilityPower === 0
    let armor = isPhysical ? defender.armor : defender.magicResist
    const penetration = 0
    armor = Math.max(0, armor - penetration)

    const damageReduction = armor / (100 + armor)
    let finalDamage = baseDamage * (1 - damageReduction)

    const critChance = attacker.baseStats.criticalStrike / 100
    if (Math.random() < critChance) {
      finalDamage *= 1.75
    }

    return Math.max(1, Math.round(finalDamage))
  }

  private applyDamage(target: Hero, damage: number, attacker: Hero) {
    target.hp -= damage
    attacker.kda.damageDealt += damage
    target.kda.damageTaken += damage

    if (target.hp <= 0) {
      target.hp = 0
      target.isAlive = false
      target.respawnTimer = 5 + target.level * 2

      attacker.kda.kills++
      target.kda.deaths++
      attacker.kda.gold += 300
      attacker.kda.goldEarned += 300

      const expGain = 100 + target.level * 50
      this.addExperience(attacker, expGain)

      this.gameStore!.addGameEvent({
        type: 'kill',
        timestamp: Date.now(),
        data: { killer: attacker.id, victim: target.id }
      })
    }
  }

  private addExperience(hero: Hero, amount: number) {
    hero.experience += amount

    while (hero.experience >= hero.experienceToNextLevel) {
      hero.experience -= hero.experienceToNextLevel
      hero.level++

      hero.experienceToNextLevel = Math.floor(hero.experienceToNextLevel * 1.15)

      hero.maxHp += 80 + hero.level * 10
      hero.hp = hero.maxHp
      hero.maxMp += 30 + hero.level * 5
      hero.mp = hero.maxMp
      hero.baseStats.attack += 3
      hero.baseStats.armor += 2
      hero.baseStats.magicResist += 1.5

      this.gameStore!.addGameEvent({
        type: 'level_up',
        timestamp: Date.now(),
        data: { heroId: hero.id, level: hero.level }
      })
    }
  }

  private updateRespawn(hero: Hero) {
    if (!hero.isAlive && hero.respawnTimer > 0) {
      hero.respawnTimer -= 1 / TICK_RATE
      if (hero.respawnTimer <= 0) {
        hero.respawnTimer = 0
        hero.isAlive = true
        hero.hp = hero.maxHp
        hero.mp = hero.maxMp

        const spawnPositions = [
          { x: 400, y: 400 },
          { x: 3600, y: 3600 },
          { x: 2000, y: 400 },
        ]
        hero.position = { ...spawnPositions[hero.teamId] || spawnPositions[0] }
      }
    }
  }

  buyItem(hero: Hero, itemId: string, itemPrice: number, itemStats: any): boolean {
    if (hero.kda.gold < itemPrice) return false

    const emptySlot = hero.items.findIndex(i => i === null)
    if (emptySlot === -1) return false

    hero.kda.gold -= itemPrice
    hero.kda.goldEarned -= itemPrice

    hero.items[emptySlot] = {
      id: itemId,
      name: '',
      icon: '',
      description: '',
      price: itemPrice,
      stats: itemStats,
      rarity: 'common' as any,
    }

    if (itemStats.attack) hero.baseStats.attack += itemStats.attack
    if (itemStats.abilityPower) hero.baseStats.abilityPower += itemStats.abilityPower
    if (itemStats.armor) hero.baseStats.armor += itemStats.armor
    if (itemStats.magicResist) hero.baseStats.magicResist += itemStats.magicResist
    if (itemStats.health) { hero.maxHp += itemStats.health; hero.hp += itemStats.health }
    if (itemStats.mana) { hero.maxMp += itemStats.mana; hero.mp += itemStats.mana }
    if (itemStats.attackSpeed) hero.baseStats.attackSpeed += itemStats.attackSpeed / 100
    if (itemStats.moveSpeed) hero.baseStats.moveSpeed += itemStats.moveSpeed
    if (itemStats.criticalStrike) hero.baseStats.criticalStrike += itemStats.criticalStrike

    hero.attackDamage = hero.baseStats.attack
    hero.abilityPower = hero.baseStats.abilityPower
    hero.armor = hero.baseStats.armor
    hero.magicResist = hero.baseStats.magicResist

    return true
  }

  getFrame(): number {
    return this.currentFrame
  }

  isOffline(): boolean {
    return this.isOfflineMode
  }

  getIsRunning(): boolean {
    return this.isRunning
  }
}

export const lockstepManager = new LockstepManager()
