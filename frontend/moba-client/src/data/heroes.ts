import type { Hero } from '@/types/game'
import { HeroRole } from '@/types/game'

export interface HeroDefinition {
  id: string
  name: string
  title: string
  role: HeroRole
  difficulty: number
  description: string
  colors: string[]
  baseStats: {
    hp: number
    mp: number
    attack: number
    armor: number
    magicResist: number
    attackSpeed: number
    moveSpeed: number
    range: number
    attackDamage: number
    abilityPower: number
  }
  skills: {
    id: string
    name: string
    description: string
    cooldown: number
    manaCost: number
    damage: number
    range: number
    type: 'active' | 'passive' | 'ultimate'
  }[]
}

export const heroDefinitions: HeroDefinition[] = [
  {
    id: 'warrior_01',
    name: '裂天战神',
    title: '不朽之躯',
    role: HeroRole.WARRIOR,
    difficulty: 2,
    description: '近战战士，擅长冲锋陷阵，拥有强大的生存能力和持续输出',
    colors: ['#1e3a5f', '#4a9eff', '#88ccff'],
    baseStats: {
      hp: 1200, mp: 400, attack: 65, armor: 35, magicResist: 32,
      attackSpeed: 0.65, moveSpeed: 340, range: 150, attackDamage: 65, abilityPower: 0
    },
    skills: [
      { id: 'passive', name: '战意沸腾', description: '每次普通攻击增加攻击力', cooldown: 0, manaCost: 0, damage: 0, range: 0, type: 'passive' },
      { id: 'q', name: '裂地斩', description: '向前挥砍，对路径上敌人造成伤害', cooldown: 8, manaCost: 50, damage: 120, range: 300, type: 'active' },
      { id: 'w', name: '铁壁', description: '获得护盾，减少受到伤害', cooldown: 12, manaCost: 70, damage: 0, range: 0, type: 'active' },
      { id: 'e', name: '冲锋', description: '向目标位置冲锋，击退敌人', cooldown: 10, manaCost: 60, damage: 80, range: 500, type: 'active' },
      { id: 'r', name: '天神下凡', description: '变身为战神，大幅提升属性', cooldown: 120, manaCost: 100, damage: 200, range: 0, type: 'ultimate' },
    ]
  },
  {
    id: 'mage_01',
    name: '霜华魔女',
    title: '寒冰使者',
    role: HeroRole.MAGE,
    difficulty: 3,
    description: '远程法师，拥有强大的爆发伤害和控制能力',
    colors: ['#2d1b4e', '#9b59b6', '#d4a5ff'],
    baseStats: {
      hp: 800, mp: 1000, attack: 45, armor: 20, magicResist: 30,
      attackSpeed: 0.55, moveSpeed: 330, range: 550, attackDamage: 45, abilityPower: 0
    },
    skills: [
      { id: 'passive', name: '寒冰之力', description: '技能命中减少敌人移速', cooldown: 0, manaCost: 0, damage: 0, range: 0, type: 'passive' },
      { id: 'q', name: '冰霜飞弹', description: '发射冰霜飞弹，穿透敌人', cooldown: 6, manaCost: 80, damage: 100, range: 700, type: 'active' },
      { id: 'w', name: '寒冰护体', description: '创造冰墙阻挡敌人', cooldown: 15, manaCost: 100, damage: 0, range: 500, type: 'active' },
      { id: 'e', name: '冰封领域', description: '在目标区域创造冰霜领域', cooldown: 10, manaCost: 90, damage: 80, range: 600, type: 'active' },
      { id: 'r', name: '绝对零度', description: '冻结大范围内所有敌人', cooldown: 140, manaCost: 150, damage: 350, range: 800, type: 'ultimate' },
    ]
  },
  {
    id: 'assassin_01',
    name: '影刃刺客',
    title: '暗影猎手',
    role: HeroRole.ASSASSIN,
    difficulty: 4,
    description: '近战刺客，擅长暗杀和机动，能够瞬间击杀脆皮敌人',
    colors: ['#3d0c0c', '#e74c3c', '#ff8888'],
    baseStats: {
      hp: 900, mp: 400, attack: 70, armor: 25, magicResist: 28,
      attackSpeed: 0.7, moveSpeed: 350, range: 125, attackDamage: 70, abilityPower: 0
    },
    skills: [
      { id: 'passive', name: '暗影步', description: '脱离战斗后获得移速加成', cooldown: 0, manaCost: 0, damage: 0, range: 0, type: 'passive' },
      { id: 'q', name: '影袭', description: '瞬移到目标身后造成伤害', cooldown: 7, manaCost: 50, damage: 150, range: 400, type: 'active' },
      { id: 'w', name: '烟雾弹', description: '释放烟雾，获得隐身', cooldown: 15, manaCost: 60, damage: 0, range: 0, type: 'active' },
      { id: 'e', name: '背刺', description: '从背后攻击造成额外伤害', cooldown: 5, manaCost: 40, damage: 100, range: 150, type: 'active' },
      { id: 'r', name: '死亡莲华', description: '快速旋转攻击周围所有敌人', cooldown: 100, manaCost: 120, damage: 400, range: 300, type: 'ultimate' },
    ]
  },
  {
    id: 'marksman_01',
    name: '星辰弓手',
    title: '破晓之光',
    role: HeroRole.MARKSMAN,
    difficulty: 3,
    description: '远程射手，拥有持续的物理输出能力，后期核心',
    colors: ['#4a3d0c', '#f39c12', '#ffd700'],
    baseStats: {
      hp: 750, mp: 350, attack: 60, armor: 18, magicResist: 25,
      attackSpeed: 0.75, moveSpeed: 325, range: 600, attackDamage: 60, abilityPower: 0
    },
    skills: [
      { id: 'passive', name: '精准射击', description: '连续攻击同一目标增加伤害', cooldown: 0, manaCost: 0, damage: 0, range: 0, type: 'passive' },
      { id: 'q', name: '穿云箭', description: '发射穿透箭矢，对路径敌人造成伤害', cooldown: 8, manaCost: 60, damage: 120, range: 800, type: 'active' },
      { id: 'w', name: '翻滚', description: '短距离翻滚并强化下次攻击', cooldown: 10, manaCost: 40, damage: 0, range: 300, type: 'active' },
      { id: 'e', name: '减速陷阱', description: '放置陷阱，触发后减速敌人', cooldown: 12, manaCost: 50, damage: 60, range: 400, type: 'active' },
      { id: 'r', name: '星辰坠落', description: '召唤箭雨覆盖大范围区域', cooldown: 110, manaCost: 130, damage: 350, range: 1000, type: 'ultimate' },
    ]
  },
  {
    id: 'support_01',
    name: '生命之灵',
    title: '治愈使者',
    role: HeroRole.SUPPORT,
    difficulty: 2,
    description: '远程辅助，擅长治疗和增益，保护队友',
    colors: ['#0c3d1e', '#2ecc71', '#88ff88'],
    baseStats: {
      hp: 850, mp: 800, attack: 40, armor: 22, magicResist: 35,
      attackSpeed: 0.55, moveSpeed: 330, range: 500, attackDamage: 40, abilityPower: 0
    },
    skills: [
      { id: 'passive', name: '生命链接', description: '附近队友获得生命回复', cooldown: 0, manaCost: 0, damage: 0, range: 0, type: 'passive' },
      { id: 'q', name: '治愈之光', description: '治疗目标队友', cooldown: 6, manaCost: 70, damage: 0, range: 600, type: 'active' },
      { id: 'w', name: '护盾', description: '为目标队友添加护盾', cooldown: 10, manaCost: 80, damage: 0, range: 600, type: 'active' },
      { id: 'e', name: '净化', description: '移除队友的负面效果', cooldown: 15, manaCost: 60, damage: 0, range: 500, type: 'active' },
      { id: 'r', name: '生命结界', description: '创造大范围治疗结界', cooldown: 130, manaCost: 150, damage: 0, range: 700, type: 'ultimate' },
    ]
  },
  {
    id: 'tank_01',
    name: '钢铁堡垒',
    title: '不动如山',
    role: HeroRole.TANK,
    difficulty: 1,
    description: '近战坦克，拥有极高的生存能力，擅长吸收伤害',
    colors: ['#2c3e50', '#34495e', '#95a5a6'],
    baseStats: {
      hp: 1500, mp: 300, attack: 55, armor: 45, magicResist: 40,
      attackSpeed: 0.5, moveSpeed: 315, range: 125, attackDamage: 55, abilityPower: 0
    },
    skills: [
      { id: 'passive', name: '钢铁意志', description: '受到伤害降低', cooldown: 0, manaCost: 0, damage: 0, range: 0, type: 'passive' },
      { id: 'q', name: '震荡波', description: '击飞前方敌人', cooldown: 9, manaCost: 60, damage: 100, range: 300, type: 'active' },
      { id: 'w', name: '嘲讽', description: '嘲讽附近敌人强制攻击自己', cooldown: 12, manaCost: 70, damage: 0, range: 350, type: 'active' },
      { id: 'e', name: '壁垒', description: '获得巨额护盾', cooldown: 14, manaCost: 80, damage: 0, range: 0, type: 'active' },
      { id: 'r', name: '不动如山', description: '免疫所有控制，大幅提升防御', cooldown: 120, manaCost: 100, damage: 0, range: 0, type: 'ultimate' },
    ]
  },
]

export const itemDefinitions = [
  { id: 'item_01', name: '长剑', price: 350, icon: '⚔️', stats: { attack: 10 } },
  { id: 'item_02', name: '多兰之刃', price: 450, icon: '🗡️', stats: { attack: 8, health: 80 } },
  { id: 'item_03', name: '狂战士胫甲', price: 800, icon: '👢', stats: { attackSpeed: 25, moveSpeed: 45 } },
  { id: 'item_04', name: '暴风大剑', price: 1300, icon: '⚡', stats: { attack: 40 } },
  { id: 'item_05', name: '无尽之刃', price: 3400, icon: '💎', stats: { attack: 70, criticalStrike: 20 } },
  { id: 'item_06', name: '法师之靴', price: 800, icon: '👟', stats: { abilityPower: 15, moveSpeed: 45 } },
  { id: 'item_07', name: '灭世者之帽', price: 3600, icon: '🎩', stats: { abilityPower: 120 } },
  { id: 'item_08', name: '守护天使', price: 2800, icon: '👼', stats: { attack: 40, armor: 40 } },
  { id: 'item_09', name: '冰霜之心', price: 2500, icon: '❄️', stats: { armor: 90, mana: 400 } },
  { id: 'item_10', name: '红莲斗篷', price: 2000, icon: '🔥', stats: { health: 450, armor: 30 } },
  { id: 'item_11', name: '生命药水', price: 50, icon: '❤️', stats: { healthRegen: 15 } },
  { id: 'item_12', name: '法力药水', price: 50, icon: '💙', stats: { manaRegen: 15 } },
]

export const roleIcons: Record<HeroRole, string> = {
  [HeroRole.TANK]: '🛡️',
  [HeroRole.WARRIOR]: '⚔️',
  [HeroRole.ASSASSIN]: '🗡️',
  [HeroRole.MAGE]: '✨',
  [HeroRole.MARKSMAN]: '🏹',
  [HeroRole.SUPPORT]: '💚',
}

export const roleNames: Record<HeroRole, string> = {
  [HeroRole.TANK]: '坦克',
  [HeroRole.WARRIOR]: '战士',
  [HeroRole.ASSASSIN]: '刺客',
  [HeroRole.MAGE]: '法师',
  [HeroRole.MARKSMAN]: '射手',
  [HeroRole.SUPPORT]: '辅助',
}

export const difficultyStars = (level: number): string => {
  return '⭐'.repeat(level) + '☆'.repeat(5 - level)
}
