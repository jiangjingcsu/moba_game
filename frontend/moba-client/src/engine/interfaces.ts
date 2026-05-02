import type { Hero, Vector2D, ItemStats } from '@/types/game'

export interface ICombatSystem {
  calculateDamage(attacker: Hero, defender: Hero, baseDamage: number): number
  executeSkill(hero: Hero, skillId: string, target: Vector2D): void
  executeAttack(hero: Hero, targetX: number, targetY: number): void
  setOnKillCallback(callback: (data: { killer: Hero; victim: Hero; expGain: number }) => void): void
}

export interface IHeroManager {
  addExperience(hero: Hero, amount: number): void
  updateRespawn(hero: Hero, tickRate: number): void
  getSpawnPosition(teamId: number): { x: number; y: number }
}

export interface IItemSystem {
  buyItem(hero: Hero, itemId: string, itemPrice: number, itemStats: ItemStats): boolean
  sellItem(hero: Hero, slotIndex: number): boolean
}

export interface IResourceSystem {
  regenResources(hero: Hero, tickRate: number): void
  updateCooldowns(hero: Hero, tickRate: number): void
  canUseSkill(hero: Hero, skillId: string): boolean
  canUseSummonerSpell(hero: Hero, spellId: string): boolean
  useSkill(hero: Hero, skillId: string): boolean
  useSummonerSpell(hero: Hero, spellId: string): boolean
}