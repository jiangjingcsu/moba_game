import type { Hero } from '@/types/game'
import { useGameStore } from '@/stores/game'

const SPAWN_POSITIONS = [
  { x: 400, y: 400 },
  { x: 3600, y: 3600 },
  { x: 2000, y: 400 },
]

export class HeroManager {
  constructor(private gameStore: ReturnType<typeof useGameStore>) {}

  addExperience(hero: Hero, amount: number) {
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

      this.gameStore.addGameEvent({
        type: 'level_up',
        timestamp: Date.now(),
        data: { heroId: hero.id, level: hero.level }
      })
    }
  }

  updateRespawn(hero: Hero, tickRate: number) {
    if (!hero.isAlive && hero.respawnTimer > 0) {
      hero.respawnTimer -= 1 / tickRate
      if (hero.respawnTimer <= 0) {
        hero.respawnTimer = 0
        hero.isAlive = true
        hero.hp = hero.maxHp
        hero.mp = hero.maxMp

        hero.position = { ...SPAWN_POSITIONS[hero.teamId] || SPAWN_POSITIONS[0] }
      }
    }
  }

  getSpawnPosition(teamId: number): { x: number; y: number } {
    return { ...SPAWN_POSITIONS[teamId] || SPAWN_POSITIONS[0] }
  }
}