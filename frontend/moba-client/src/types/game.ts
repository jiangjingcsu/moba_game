export interface Vector2D {
  x: number
  y: number
}

export enum HeroRole {
  TANK = 'tank',
  WARRIOR = 'warrior',
  ASSASSIN = 'assassin',
  MAGE = 'mage',
  MARKSMAN = 'marksman',
  SUPPORT = 'support'
}

export enum Rarity {
  COMMON = 'common',
  RARE = 'rare',
  EPIC = 'epic',
  LEGENDARY = 'legendary'
}

export interface Skill {
  id: string
  name: string
  icon: string
  description: string
  cooldown: number
  currentCooldown: number
  manaCost: number
  damage: number
  range: number
  type: 'active' | 'passive' | 'ultimate'
}

export interface Item {
  id: string
  name: string
  icon: string
  description: string
  price: number
  stats: ItemStats
  rarity: Rarity
  buildFrom?: string[]
  buildInto?: string[]
}

export interface ItemStats {
  attack?: number
  abilityPower?: number
  armor?: number
  magicResist?: number
  health?: number
  mana?: number
  attackSpeed?: number
  criticalStrike?: number
  moveSpeed?: number
  healthRegen?: number
  manaRegen?: number
  cooldownReduction?: number
}

export interface Runes {
  primary: {
    tree: string
    keystone: string
    slots: string[]
  }
  secondary: {
    tree: string
    slots: string[]
  }
  shards: string[]
}

export interface HeroStats {
  attack: number
  abilityPower: number
  armor: number
  magicResist: number
  attackSpeed: number
  criticalStrike: number
  moveSpeed: number
  healthRegen: number
  manaRegen: number
  cooldownReduction: number
}

export interface KillDeathAssist {
  kills: number
  deaths: number
  assists: number
  cs: number
  gold: number
  goldEarned: number
  damageDealt: number
  damageTaken: number
  healingDone: number
  visionScore: number
  wardsPlaced: number
  wardsDestroyed: number
}

export interface Hero {
  id: string
  name: string
  title: string
  role: HeroRole
  teamId: number
  userId: string
  playerName: string
  position: Vector2D
  velocity?: Vector2D
  
  hp: number
  maxHp: number
  mp: number
  maxMp: number
  level: number
  experience: number
  experienceToNextLevel: number
  
  baseStats: HeroStats
  bonusStats: HeroStats
  
  skills: Skill[]
  items: (Item | null)[]
  runes: Runes
  
  kda: KillDeathAssist
  
  isAlive: boolean
  respawnTimer: number
  isRecalling: boolean
  
  speed: number
  range: number
  attackDamage: number
  abilityPower: number
  armor: number
  magicResist: number
  
  skinId?: string
  summonerSpells: SummonerSpell[]
}

export interface SummonerSpell {
  id: string
  name: string
  icon: string
  cooldown: number
  currentCooldown: number
  description: string
}

export interface Tower {
  id: string
  name: string
  teamId: number
  position: Vector2D
  hp: number
  maxHp: number
  range: number
  damage: number
  armor: number
  magicResist: number
  isAlive: boolean
  lane: 'top' | 'mid' | 'bot'
  tier: number
}

export interface Monster {
  id: string
  name: string
  type: 'jungle_small' | 'jungle_large' | 'buff' | 'objective' | 'boss'
  position: Vector2D
  hp: number
  maxHp: number
  damage: number
  armor: number
  magicResist: number
  isAlive: boolean
  respawnTimer: number
  respawnTime: number
  rewards: MonsterRewards
}

export interface MonsterRewards {
  gold: number
  experience: number
  buff?: {
    name: string
    duration: number
    effects: Partial<HeroStats>
  }
}

export interface Minion {
  id: string
  type: 'melee' | 'caster' | 'cannon' | 'super'
  teamId: number
  position: Vector2D
  hp: number
  maxHp: number
  damage: number
  lane: 'top' | 'mid' | 'bot'
}

export interface GameObject {
  id: string
  position: Vector2D
  velocity?: Vector2D
}

export interface GameState {
  frame: number
  heroes: Map<string, Hero>
  towers: Map<string, Tower>
  monsters: Map<string, Monster>
  minions: Map<string, Minion>
  gameTime: number
  gamePhase: 'early' | 'mid' | 'late'
}

export interface PlayerInput {
  heroId: string
  type: 'move' | 'skill' | 'attack' | 'item' | 'recall'
  target?: Vector2D
  targetId?: string
  skillId?: string
  itemId?: string
  frame: number
}

export interface ChatMessage {
  id: string
  sender: string
  senderTeamId: number
  text: string
  timestamp: number
  type: 'all' | 'team'
}

export interface TeamInfo {
  id: number
  name: string
  color: string
  kills: number
  towers: number
  gold: number
  dragons: number
  barons: number
}

export interface GameEvent {
  type: 'kill' | 'tower_destroyed' | 'monster_killed' | 'item_purchased' | 'level_up'
  timestamp: number
  data: any
}
