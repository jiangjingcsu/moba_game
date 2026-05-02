import type { Hero, Item, ItemStats } from '@/types/game'
import { Rarity } from '@/types/game'

export class ItemSystem {
  buyItem(hero: Hero, itemId: string, itemPrice: number, itemStats: ItemStats): boolean {
    if (hero.kda.gold < itemPrice) return false

    const emptySlot = hero.items.findIndex(i => i === null)
    if (emptySlot === -1) return false

    hero.kda.gold -= itemPrice
    hero.kda.goldEarned -= itemPrice

    const item: Item = {
      id: itemId,
      name: '',
      icon: '',
      description: '',
      price: itemPrice,
      stats: itemStats,
      rarity: Rarity.COMMON,
    }

    hero.items[emptySlot] = item
    this.applyItemStats(hero, itemStats)

    return true
  }

  private applyItemStats(hero: Hero, itemStats: ItemStats) {
    if (itemStats.attack) hero.baseStats.attack += itemStats.attack
    if (itemStats.abilityPower) hero.baseStats.abilityPower += itemStats.abilityPower
    if (itemStats.armor) hero.baseStats.armor += itemStats.armor
    if (itemStats.magicResist) hero.baseStats.magicResist += itemStats.magicResist
    if (itemStats.health) {
      hero.maxHp += itemStats.health
      hero.hp += itemStats.health
    }
    if (itemStats.mana) {
      hero.maxMp += itemStats.mana
      hero.mp += itemStats.mana
    }
    if (itemStats.attackSpeed) hero.baseStats.attackSpeed += itemStats.attackSpeed / 100
    if (itemStats.moveSpeed) hero.baseStats.moveSpeed += itemStats.moveSpeed
    if (itemStats.criticalStrike) hero.baseStats.criticalStrike += itemStats.criticalStrike

    hero.attackDamage = hero.baseStats.attack
    hero.abilityPower = hero.baseStats.abilityPower
    hero.armor = hero.baseStats.armor
    hero.magicResist = hero.baseStats.magicResist
  }

  sellItem(hero: Hero, slotIndex: number): boolean {
    if (slotIndex < 0 || slotIndex >= hero.items.length) return false

    const item = hero.items[slotIndex]
    if (!item) return false

    const sellValue = Math.floor(item.price * 0.6)
    hero.kda.gold += sellValue
    hero.kda.goldEarned += sellValue

    this.removeItemStats(hero, item.stats)
    hero.items[slotIndex] = null

    return true
  }

  private removeItemStats(hero: Hero, itemStats: ItemStats) {
    if (itemStats.attack) hero.baseStats.attack -= itemStats.attack
    if (itemStats.abilityPower) hero.baseStats.abilityPower -= itemStats.abilityPower
    if (itemStats.armor) hero.baseStats.armor -= itemStats.armor
    if (itemStats.magicResist) hero.baseStats.magicResist -= itemStats.magicResist
    if (itemStats.health) {
      hero.hp = Math.min(hero.hp, hero.maxHp - itemStats.health)
      hero.maxHp -= itemStats.health
    }
    if (itemStats.mana) {
      hero.mp = Math.min(hero.mp, hero.maxMp - itemStats.mana)
      hero.maxMp -= itemStats.mana
    }
    if (itemStats.attackSpeed) hero.baseStats.attackSpeed -= itemStats.attackSpeed / 100
    if (itemStats.moveSpeed) hero.baseStats.moveSpeed -= itemStats.moveSpeed
    if (itemStats.criticalStrike) hero.baseStats.criticalStrike -= itemStats.criticalStrike

    hero.attackDamage = hero.baseStats.attack
    hero.abilityPower = hero.baseStats.abilityPower
    hero.armor = hero.baseStats.armor
    hero.magicResist = hero.baseStats.magicResist
  }
}