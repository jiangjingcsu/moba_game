import type { Hero, Vector2D } from '@/types/game'
import { useGameStore } from '@/stores/game'

export interface KillEventData {
  killer: Hero
  victim: Hero
  expGain: number
}

export type KillEventHandler = (data: KillEventData) => void

export class CombatSystem {
  private onKillCallback: KillEventHandler | null = null

  constructor(private gameStore: ReturnType<typeof useGameStore>) {}

  setOnKillCallback(callback: KillEventHandler) {
    this.onKillCallback = callback
  }

  calculateDamage(attacker: Hero, defender: Hero, baseDamage: number): number {
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

  executeSkill(hero: Hero, skillId: string, target: Vector2D) {
    const skill = hero.skills.find(s => s.id === skillId)
    if (!skill || skill.currentCooldown > 0 || hero.mp < skill.manaCost) return

    hero.mp -= skill.manaCost
    skill.currentCooldown = skill.cooldown

    if (skill.damage > 0) {
      this.gameStore.heroes.forEach((otherHero) => {
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

    this.gameStore.addGameEvent({
      type: 'kill',
      timestamp: Date.now(),
      data: { heroId: hero.id, skillId, target }
    })
  }

  executeAttack(hero: Hero, _targetX: number, _targetY: number) {
    let closestEnemy: Hero | null = null
    let closestDist = hero.range

    this.gameStore.heroes.forEach((otherHero) => {
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

      this.gameStore.addGameEvent({
        type: 'kill',
        timestamp: Date.now(),
        data: { killer: attacker.id, victim: target.id }
      })

      if (this.onKillCallback) {
        this.onKillCallback({ killer: attacker, victim: target, expGain })
      }
    }
  }
}