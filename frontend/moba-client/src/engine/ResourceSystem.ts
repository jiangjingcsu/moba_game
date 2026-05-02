import type { Hero } from '@/types/game'

export class ResourceSystem {
  regenResources(hero: Hero, tickRate: number) {
    if (!hero.isAlive) return

    hero.hp = Math.min(hero.maxHp, hero.hp + hero.baseStats.healthRegen / tickRate)
    hero.mp = Math.min(hero.maxMp, hero.mp + hero.baseStats.manaRegen / tickRate)
  }

  updateCooldowns(hero: Hero, tickRate: number) {
    for (const skill of hero.skills) {
      if (skill.currentCooldown > 0) {
        skill.currentCooldown = Math.max(0, skill.currentCooldown - 1 / tickRate)
      }
    }
    for (const spell of hero.summonerSpells) {
      if (spell.currentCooldown > 0) {
        spell.currentCooldown = Math.max(0, spell.currentCooldown - 1 / tickRate)
      }
    }
  }

  canUseSkill(hero: Hero, skillId: string): boolean {
    const skill = hero.skills.find(s => s.id === skillId)
    if (!skill) return false
    if (skill.currentCooldown > 0) return false
    if (hero.mp < skill.manaCost) return false
    return true
  }

  canUseSummonerSpell(hero: Hero, spellId: string): boolean {
    const spell = hero.summonerSpells.find(s => s.id === spellId)
    if (!spell) return false
    if (spell.currentCooldown > 0) return false
    return true
  }

  useSkill(hero: Hero, skillId: string): boolean {
    const skill = hero.skills.find(s => s.id === skillId)
    if (!skill || skill.currentCooldown > 0 || hero.mp < skill.manaCost) return false

    hero.mp -= skill.manaCost
    skill.currentCooldown = skill.cooldown
    return true
  }

  useSummonerSpell(hero: Hero, spellId: string): boolean {
    const spell = hero.summonerSpells.find(s => s.id === spellId)
    if (!spell || spell.currentCooldown > 0) return false

    spell.currentCooldown = spell.cooldown
    return true
  }
}