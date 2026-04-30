<template>
  <div class="game-ui">
    <div class="top-bar">
      <div class="team-score">
        <div class="team-badge" :style="{ background: '#4a9eff' }">
          <span class="team-icon">🛡️</span>
          <span class="team-name">蓝方</span>
        </div>
        <div class="score-display">
          <span class="score-value" :style="{ color: '#4a9eff' }">{{ kills[0] }}</span>
          <span class="score-divider">:</span>
          <span class="score-value" :style="{ color: '#ff4a4a' }">{{ kills[1] }}</span>
          <span class="score-divider">:</span>
          <span class="score-value" :style="{ color: '#ffa500' }">{{ kills[2] }}</span>
        </div>
        <div class="team-badge" :style="{ background: '#ff4a4a' }">
          <span class="team-name">红方</span>
          <span class="team-icon">⚔️</span>
        </div>
      </div>

      <div class="game-time-container">
        <div class="game-phase" :class="gamePhase">{{ gamePhaseText }}</div>
        <div class="game-time">{{ formatTime(gameStore.gameTime) }}</div>
      </div>

      <div class="fps-ping">
        <span class="ping-value">
          <span class="ping-dot" :class="pingClass"></span>
          {{ ping }}ms
        </span>
        <span class="fps-value">{{ fps }} FPS</span>
      </div>
    </div>

    <div class="minimap-container">
      <div class="minimap-frame">
        <img src="/assets/ui/minimap_frame.png" class="minimap-frame-img" />
        <canvas ref="minimapCanvas" width="200" height="200"></canvas>
        <div class="minimap-overlay"></div>
      </div>
      <div class="minimap-stats">
        <div class="stat-item">
          <span class="stat-label">🏰</span>
          <span class="stat-value">{{ towersDestroyed }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">🐉</span>
          <span class="stat-value">{{ dragonsSlain }}</span>
        </div>
      </div>
    </div>

    <div
      v-if="gameStore.showScoreboard"
      class="scoreboard visible"
    >
      <div class="scoreboard-header">
        <h3>计分板</h3>
        <span class="scoreboard-hint">松开 Tab 隐藏</span>
      </div>

      <table class="scoreboard-table">
        <thead>
          <tr>
            <th>英雄</th>
            <th>K / D / A</th>
            <th>CS</th>
            <th>金币</th>
            <th>伤害</th>
            <th>出装</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="team in [0, 1, 2]" :key="team">
            <tr
              v-for="hero in getHeroesByTeam(team)"
              :key="hero.id"
              :class="{ 'is-me': hero.id === gameStore.myHeroId, 'is-dead': !hero.isAlive }"
              @mouseenter="gameStore.setHoveredHero(hero.id)"
              @mouseleave="gameStore.setHoveredHero(null)"
            >
              <td class="hero-cell">
                <div class="hero-avatar" :style="{ background: teamColors[hero.teamId] }">
                  <img v-if="roleAvatars[hero.role]" :src="roleAvatars[hero.role]" class="scoreboard-avatar-img" />
                  <span v-else>{{ hero.name[0] }}</span>
                </div>
                <div class="hero-info">
                  <span class="hero-name">{{ hero.name }}</span>
                  <span class="hero-level">Lv.{{ hero.level }}</span>
                </div>
              </td>
              <td class="kda-cell">
                <span class="kills">{{ hero.kda.kills }}</span>
                <span class="separator">/</span>
                <span class="deaths">{{ hero.kda.deaths }}</span>
                <span class="separator">/</span>
                <span class="assists">{{ hero.kda.assists }}</span>
              </td>
              <td class="cs-cell">{{ hero.kda.cs }}</td>
              <td class="gold-cell">
                <span class="gold-icon">💰</span>
                {{ hero.kda.gold }}
              </td>
              <td class="damage-cell">{{ formatNumber(hero.kda.damageDealt) }}</td>
              <td class="items-cell">
                <div
                  v-for="(item, i) in hero.items.slice(0, 6)"
                  :key="i"
                  class="mini-item"
                  :class="{ empty: !item }"
                >
                  <img v-if="item && itemImages[item.id]" :src="itemImages[item.id]" class="mini-item-img" />
                  <span v-else-if="item">{{ item?.icon || '+' }}</span>
                  <span v-else>+</span>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <div class="hero-panel" v-if="gameStore.myHero">
      <div class="hero-avatar-section">
        <div class="hero-portrait" :style="{ background: `linear-gradient(135deg, ${teamColors[gameStore.myHero.teamId]}, #1a1a2e)` }">
          <img src="/assets/ui/avatar_frame.png" class="avatar-frame-img" />
          <span class="portrait-icon">{{ roleIcons[gameStore.myHero.role] }}</span>
          <div class="portrait-level">{{ gameStore.myHero.level }}</div>
        </div>
        <div class="hero-name-badge">
          <span class="name">{{ gameStore.myHero.name }}</span>
          <span class="title">{{ gameStore.myHero.title }}</span>
        </div>
      </div>

      <div class="resource-bars">
        <div class="bar-row hp-row">
          <span class="bar-label">HP</span>
          <div class="bar-track">
            <div class="bar-fill hp-fill" :style="{ width: `${hpPercent}%` }"></div>
          </div>
          <span class="bar-value">{{ Math.round(gameStore.myHero.hp) }} / {{ gameStore.myHero.maxHp }}</span>
        </div>
        <div class="bar-row mp-row">
          <span class="bar-label">MP</span>
          <div class="bar-track">
            <div class="bar-fill mp-fill" :style="{ width: `${mpPercent}%` }"></div>
          </div>
          <span class="bar-value">{{ Math.round(gameStore.myHero.mp) }} / {{ gameStore.myHero.maxMp }}</span>
        </div>
        <div class="bar-row xp-row">
          <span class="bar-label">EXP</span>
          <div class="bar-track">
            <div class="bar-fill xp-fill" :style="{ width: `${xpPercent}%` }"></div>
          </div>
          <span class="bar-value">{{ Math.round(gameStore.myHero.experience) }} / {{ gameStore.myHero.experienceToNextLevel }}</span>
        </div>
      </div>

      <div class="stats-row">
        <div class="stat-box">
          <span class="stat-icon">⚔️</span>
          <span class="stat-num">{{ gameStore.myHero.attackDamage }}</span>
        </div>
        <div class="stat-box">
          <span class="stat-icon">✨</span>
          <span class="stat-num">{{ gameStore.myHero.abilityPower }}</span>
        </div>
        <div class="stat-box">
          <span class="stat-icon">🛡️</span>
          <span class="stat-num">{{ gameStore.myHero.armor }}</span>
        </div>
        <div class="stat-box">
          <span class="stat-icon">🔮</span>
          <span class="stat-num">{{ gameStore.myHero.magicResist }}</span>
        </div>
      </div>
    </div>

    <div class="bottom-bar">
      <div class="left-section">
        <div class="items-grid">
          <div
            v-for="(item, i) in gameStore.myHero?.items || emptyItems"
            :key="i"
            class="item-slot"
            :class="{ empty: !item }"
            @click="selectItem(item)"
          >
            <img src="/assets/ui/item_frame.png" class="item-frame-img" />
            <span class="item-icon" v-if="item">
              <img v-if="itemImages[item.id]" :src="itemImages[item.id]" class="item-img" />
              <span v-else>{{ item.icon }}</span>
            </span>
            <span class="slot-key" v-else>{{ i + 1 }}</span>
            <div v-if="item" class="item-tooltip">
              <div class="tooltip-name" :style="{ color: rarityColors[item.rarity || 'common'] }">{{ item.name }}</div>
              <div class="tooltip-desc">{{ item.description }}</div>
            </div>
          </div>
        </div>

        <div class="gold-panel">
          <div class="gold-display">
            <span class="gold-icon-large">💰</span>
            <div class="gold-info">
              <span class="gold-current">{{ gameStore.myHero?.kda.gold || 0 }}</span>
              <span class="gold-total">总: {{ gameStore.myHero?.kda.goldEarned || 0 }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="center-section">
        <div class="skills-container">
          <div class="passive-skill" :class="{ ready: true }" @mouseenter="showSkillTooltip('passive')">
            <div class="skill-icon passive">🌀</div>
            <span class="skill-key-passive">被动</span>
          </div>

          <div class="active-skills">
            <div
              v-for="skill in activeSkills"
              :key="skill.id"
              class="skill-slot"
              :class="{ 'on-cooldown': skill.currentCooldown > 0 }"
              @click="castSkill(skill.id)"
              @mouseenter="showSkillTooltip(skill.id)"
            >
              <div class="skill-icon-wrapper">
                <img src="/assets/ui/skill_frame.png" class="skill-frame-img" />
                <div class="skill-icon" :style="{ background: skillColor(skill.id) }">
                  {{ skillIcon(skill.id) }}
                </div>
                <div v-if="skill.currentCooldown > 0" class="cooldown-mask" :style="{ height: `${(skill.currentCooldown / skill.cooldown) * 100}%` }"></div>
              </div>
              <span class="skill-key">{{ skill.key }}</span>
              <span v-if="skill.currentCooldown > 0" class="cooldown-text">{{ Math.ceil(skill.currentCooldown / 15) }}s</span>
            </div>
          </div>

          <div class="summoner-spells">
            <div
              v-for="spell in summonerSpells"
              :key="spell.id"
              class="spell-slot"
              :class="{ 'on-cooldown': spell.currentCooldown > 0 }"
            >
              <div class="spell-icon" :style="{ background: spellColor(spell.id) }">
                {{ spellIcon(spell.id) }}
              </div>
              <span class="spell-key">{{ spell.key }}</span>
            </div>
          </div>
        </div>

        <button class="shop-btn" @click="showShop = !showShop">🛒 商店</button>
      </div>

      <div class="right-section">
        <div class="chat-container">
          <div class="chat-tabs">
            <button
              v-for="tab in ['全部', '队伍']"
              :key="tab"
              class="chat-tab"
              :class="{ active: chatTab === tab }"
              @click="chatTab = tab"
            >{{ tab }}</button>
          </div>
          <div class="chat-messages">
            <div
              v-for="msg in filteredMessages"
              :key="msg.id"
              class="chat-msg"
            >
              <span class="msg-sender" :style="{ color: teamColors[msg.senderTeamId] || '#aaa' }">[{{ msg.sender }}]</span>
              <span class="msg-text">{{ msg.text }}</span>
            </div>
          </div>
          <div class="chat-input-row">
            <input
              v-model="chatInput"
              @keyup.enter="sendChat"
              placeholder="按Enter发送..."
            />
          </div>
        </div>
      </div>
    </div>

    <div v-if="showShop" class="shop-panel">
      <div class="shop-header">
        <h3>🛒 商店</h3>
        <div class="shop-gold">💰 {{ gameStore.myHero?.kda.gold || 0 }}</div>
        <button class="close-btn" @click="showShop = false">✕</button>
      </div>
      <div class="shop-categories">
        <button
          v-for="cat in shopCategories"
          :key="cat.id"
          class="cat-btn"
          :class="{ active: shopCategory === cat.id }"
          @click="shopCategory = cat.id"
        >{{ cat.icon }} {{ cat.name }}</button>
      </div>
      <div class="shop-items">
        <div
          v-for="item in shopItems"
          :key="item.id"
          class="shop-item"
          :class="{ 'can-afford': (gameStore.myHero?.kda.gold || 0) >= item.price }"
          @click="buyItem(item)"
        >
          <div class="shop-item-icon">
            <img v-if="itemImages[item.id]" :src="itemImages[item.id]" class="item-img" />
            <span v-else>{{ item.icon }}</span>
          </div>
          <div class="shop-item-info">
            <div class="shop-item-name">{{ item.name }}</div>
            <div class="shop-item-stats">{{ formatItemStats(item.stats) }}</div>
            <div class="shop-item-price">💰 {{ item.price }}</div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showSkillTip" class="skill-tooltip" :style="tooltipPos">
      <div class="tooltip-header">
        <span class="tooltip-key">{{ currentTooltip?.key }}</span>
        <h4>{{ currentTooltip?.name }}</h4>
      </div>
      <p class="tooltip-desc">{{ currentTooltip?.description }}</p>
      <div class="tooltip-meta">
        <span v-if="currentTooltip?.cooldown">冷却: {{ currentTooltip.cooldown / 15 }}s</span>
        <span v-if="currentTooltip?.manaCost">消耗: {{ currentTooltip.manaCost }} MP</span>
        <span v-if="currentTooltip?.damage">伤害: {{ currentTooltip.damage }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useGameStore } from '@/stores/game'
import type { Hero, Item, ChatMessage, Skill } from '@/types/game'
import { HeroRole } from '@/types/game'
import { heroDefinitions, itemDefinitions } from '@/data/heroes'
import { lockstepManager } from '@/engine/LockstepManager'

const gameStore = useGameStore()
const minimapCanvas = ref<HTMLCanvasElement | null>(null)
const chatInput = ref('')
const chatTab = ref('全部')
const showShop = ref(false)
const shopCategory = ref('all')
const showSkillTip = ref(false)
const tooltipPos = ref({ top: '0px', left: '0px' })
const currentTooltip = ref<any>(null)

const teamColors = { 0: '#4a9eff', 1: '#ff4a4a', 2: '#ffa500' }
const rarityColors = { common: '#b0b0b0', rare: '#4a9eff', epic: '#9b59b6', legendary: '#f39c12' }
const emptyItems = Array(6).fill(null)

const ping = ref(30)
const fps = ref(60)
const towersDestroyed = ref(0)
const dragonsSlain = ref(0)

const roleIcons: Record<HeroRole, string> = {
  [HeroRole.TANK]: '🛡️', [HeroRole.WARRIOR]: '⚔️', [HeroRole.ASSASSIN]: '🗡️',
  [HeroRole.MAGE]: '✨', [HeroRole.MARKSMAN]: '🏹', [HeroRole.SUPPORT]: '💚',
}

const skills = computed(() => {
  const hero = gameStore.myHero
  if (!hero || hero.skills.length === 0) {
    return new Map([
      ['passive', { id: 'passive', name: '被动技能', icon: '🌀', description: '英雄被动能力', cooldown: 0, currentCooldown: 0, manaCost: 0, damage: 0, range: 0, type: 'passive' as const }],
      ['q', { id: 'q', name: '技能1', icon: '⚡', description: '', cooldown: 8, currentCooldown: 0, manaCost: 50, damage: 100, range: 300, type: 'active' as const }],
      ['w', { id: 'w', name: '技能2', icon: '🛡️', description: '', cooldown: 12, currentCooldown: 0, manaCost: 70, damage: 0, range: 0, type: 'active' as const }],
      ['e', { id: 'e', name: '技能3', icon: '💨', description: '', cooldown: 10, currentCooldown: 0, manaCost: 60, damage: 80, range: 500, type: 'active' as const }],
      ['r', { id: 'r', name: '终极技能', icon: '🔥', description: '', cooldown: 120, currentCooldown: 0, manaCost: 100, damage: 200, range: 0, type: 'ultimate' as const }],
    ])
  }
  const map = new Map<string, Skill>()
  for (const s of hero.skills) {
    map.set(s.id, s)
  }
  return map
})

const summonerSpells = computed(() => {
  const hero = gameStore.myHero
  if (!hero) return []
  return hero.summonerSpells.map((s, i) => ({
    ...s,
    key: i === 0 ? 'D' : 'F',
  }))
})

const shopCategories = [
  { id: 'all', icon: '📦', name: '全部' },
  { id: 'attack', icon: '⚔️', name: '攻击' },
  { id: 'magic', icon: '✨', name: '法术' },
  { id: 'defense', icon: '🛡️', name: '防御' },
  { id: 'boots', icon: '👢', name: '鞋子' },
  { id: 'consumable', icon: '🧪', name: '消耗品' },
]

const itemImages: Record<string, string> = {
  item_01: '/assets/items/sword.png',
  item_02: '/assets/items/doran_blade.png',
  item_04: '/assets/items/bf_sword.png',
  item_05: '/assets/items/infinity_edge.png',
  item_07: '/assets/items/rabadon.png',
  item_09: '/assets/items/frozen_heart.png',
  item_10: '/assets/items/sunfire_cape.png',
  item_11: '/assets/items/health_potion.png',
}

const roleAvatars: Record<string, string> = {
  warrior: '/assets/avatars/warrior_avatar.png',
  mage: '/assets/avatars/mage_avatar.png',
  assassin: '/assets/avatars/assassin_avatar.png',
  marksman: '/assets/avatars/marksman_avatar.png',
  support: '/assets/avatars/support_avatar.png',
  tank: '/assets/avatars/tank_avatar.png',
}

const shopItems = ref<Item[]>([
  { id: 'item_01', name: '长剑', icon: '⚔️', description: '增加攻击力', price: 350, stats: { attack: 10 }, rarity: 'common' as any },
  { id: 'item_02', name: '多兰之刃', icon: '🗡️', description: '攻击与生命', price: 450, stats: { attack: 8, health: 80 }, rarity: 'common' as any },
  { id: 'item_04', name: '暴风大剑', icon: '⚡', description: '大幅增加攻击力', price: 1300, stats: { attack: 40 }, rarity: 'rare' as any },
  { id: 'item_05', name: '无尽之刃', icon: '💎', description: '攻击与暴击', price: 3400, stats: { attack: 70, criticalStrike: 20 }, rarity: 'legendary' as any },
  { id: 'item_07', name: '灭世者之帽', icon: '🎩', description: '大量法术强度', price: 3600, stats: { abilityPower: 120 }, rarity: 'legendary' as any },
  { id: 'item_09', name: '冰霜之心', icon: '❄️', description: '护甲与法力', price: 2500, stats: { armor: 90, mana: 400 }, rarity: 'epic' as any },
  { id: 'item_10', name: '红莲斗篷', icon: '🔥', description: '生命与护甲', price: 2000, stats: { health: 450, armor: 30 }, rarity: 'rare' as any },
  { id: 'item_11', name: '生命药水', icon: '❤️', description: '恢复生命', price: 50, stats: { healthRegen: 15 }, rarity: 'common' as any },
])

const kills = computed(() => gameStore.totalKills)
const gamePhase = computed(() => gameStore.gamePhase)
const gamePhaseText = computed(() => {
  const map = { early: '前期', mid: '中期', late: '后期' }
  return map[gamePhase.value] || '前期'
})
const pingClass = computed(() => ping.value < 50 ? 'good' : ping.value < 100 ? 'medium' : 'bad')

const hpPercent = computed(() => gameStore.myHero ? (gameStore.myHero.hp / gameStore.myHero.maxHp) * 100 : 100)
const mpPercent = computed(() => gameStore.myHero ? (gameStore.myHero.mp / gameStore.myHero.maxMp) * 100 : 100)
const xpPercent = computed(() => gameStore.myHero ? (gameStore.myHero.experience / gameStore.myHero.experienceToNextLevel) * 100 : 0)

const activeSkills = computed(() => {
  return ['q', 'w', 'e', 'r'].map(id => {
    const s = skills.value.get(id)
    return s ? { ...s, key: id.toUpperCase() } : null
  }).filter(Boolean)
})

const filteredMessages = computed(() => {
  if (chatTab.value === '队伍') {
    return gameStore.chatMessages.filter(m => m.senderTeamId === gameStore.myTeamId)
  }
  return gameStore.chatMessages
})

let fpsInterval: number | null = null
let cooldownInterval: number | null = null
let minimapInterval: number | null = null

const handleKeyDown = (e: KeyboardEvent) => {
  if (e.key === 'Tab') {
    e.preventDefault()
    gameStore.showScoreboard = true
  }
}

const handleKeyUp = (e: KeyboardEvent) => {
  if (e.key === 'Tab') {
    gameStore.showScoreboard = false
  }
}

onMounted(() => {
  startFpsCounter()
  startCooldownTimer()
  startMinimapUpdate()
  initChatMessages()
  window.addEventListener('keydown', handleKeyDown)
  window.addEventListener('keyup', handleKeyUp)
})

onUnmounted(() => {
  if (fpsInterval) clearInterval(fpsInterval)
  if (cooldownInterval) clearInterval(cooldownInterval)
  if (minimapInterval) clearInterval(minimapInterval)
  window.removeEventListener('keydown', handleKeyDown)
  window.removeEventListener('keyup', handleKeyUp)
})

function formatTime(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

function formatNumber(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return n.toString()
}

function getHeroesByTeam(teamId: number): Hero[] {
  return Array.from(gameStore.heroes.values()).filter(h => h.teamId === teamId)
}

function castSkill(skillId: string) {
  const hero = gameStore.myHero
  if (!hero || !hero.isAlive) return
  const skill = hero.skills.find(s => s.id === skillId)
  if (!skill || skill.currentCooldown > 0 || hero.mp < skill.manaCost) return

  lockstepManager.queueInput(hero.id, 'skill', {
    skillId,
    targetX: hero.position.x,
    targetY: hero.position.y,
  })
}

function showSkillTooltip(skillId: string) {
  const skill = skills.value.get(skillId)
  if (skill) {
    currentTooltip.value = { ...skill, key: skillId.toUpperCase() }
    showSkillTip.value = true
  }
}

function selectItem(item: Item | null) {
  if (item) {
    gameStore.selectedItem = item.id
  }
}

function formatItemStats(stats: any): string {
  const parts: string[] = []
  if (stats.attack) parts.push(`+${stats.attack} 攻击`)
  if (stats.abilityPower) parts.push(`+${stats.abilityPower} 法强`)
  if (stats.armor) parts.push(`+${stats.armor} 护甲`)
  if (stats.health) parts.push(`+${stats.health} 生命`)
  return parts.join(' | ')
}

function buyItem(item: Item) {
  const hero = gameStore.myHero
  if (!hero) return

  const success = lockstepManager.buyItem(hero, item.id, item.price, item.stats)
  if (success) {
    showShop.value = false
  }
}

function sendChat() {
  if (chatInput.value.trim()) {
    gameStore.addChatMessage({
      id: Date.now().toString(),
      sender: '我',
      senderTeamId: gameStore.myTeamId,
      text: chatInput.value,
      timestamp: Date.now(),
      type: chatTab.value === '队伍' ? 'team' : 'all'
    })
    chatInput.value = ''
  }
}

function initChatMessages() {
  gameStore.addChatMessage({ id: '1', sender: '系统', senderTeamId: -1, text: '欢迎来到3v3v3 MOBA!', timestamp: Date.now(), type: 'all' })
  gameStore.addChatMessage({ id: '2', sender: '队友', senderTeamId: 0, text: '大家好，一起加油!', timestamp: Date.now(), type: 'team' })
}

function startFpsCounter() {
  let frames = 0
  let lastTime = performance.now()
  fpsInterval = window.setInterval(() => {
    const now = performance.now()
    fps.value = Math.round((frames * 1000) / (now - lastTime))
    frames = 0
    lastTime = now
  }, 1000)
  const countFrame = () => { frames++; requestAnimationFrame(countFrame) }
  requestAnimationFrame(countFrame)
}

function startCooldownTimer() {
  cooldownInterval = window.setInterval(() => {
    // Cooldowns are managed by LockstepManager, just trigger reactivity
  }, 1000 / 15)
}

function startMinimapUpdate() {
  minimapInterval = window.setInterval(() => {
    if (!minimapCanvas.value) return
    const ctx = minimapCanvas.value.getContext('2d')
    if (!ctx) return

    ctx.fillStyle = '#1a2a1e'
    ctx.fillRect(0, 0, 200, 200)

    ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)'
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(0, 0); ctx.lineTo(200, 200)
    ctx.moveTo(200, 0); ctx.lineTo(0, 200)
    ctx.stroke()

    ctx.fillStyle = 'rgba(74, 158, 255, 0.3)'
    ctx.fillRect(10, 10, 50, 50)
    ctx.fillStyle = 'rgba(255, 74, 74, 0.3)'
    ctx.fillRect(140, 140, 50, 50)

    Array.from(gameStore.heroes.values()).forEach(hero => {
      const x = (hero.position.x / 4000) * 200
      const y = (hero.position.y / 4000) * 200
      ctx.fillStyle = teamColors[hero.teamId]
      ctx.beginPath()
      ctx.arc(x, y, 5, 0, Math.PI * 2)
      ctx.fill()
      if (hero.id === gameStore.myHeroId) {
        ctx.strokeStyle = '#fff'
        ctx.lineWidth = 2
        ctx.beginPath()
        ctx.arc(x, y, 7, 0, Math.PI * 2)
        ctx.stroke()
      }
    })

    ctx.fillStyle = 'rgba(255, 215, 0, 0.5)'
    ctx.beginPath()
    ctx.arc(100, 100, 6, 0, Math.PI * 2)
    ctx.fill()
  }, 500)
}

function skillColor(id: string): string {
  const colors: Record<string, string> = { q: '#4a9eff', w: '#9b59b6', e: '#2ecc71', r: '#e74c3c' }
  return colors[id] || '#666'
}

function skillIcon(id: string): string {
  const icons: Record<string, string> = { q: '⚡', w: '🛡️', e: '💨', r: '🔥' }
  return icons[id] || '❓'
}

function spellColor(id: string): string {
  const colors: Record<string, string> = { flash: '#f39c12', heal: '#1abc9c' }
  return colors[id] || '#666'
}

function spellIcon(id: string): string {
  const icons: Record<string, string> = { flash: '✨', heal: '❤️' }
  return icons[id] || '❓'
}
</script>

<style scoped>
.game-ui {
  position: absolute;
  inset: 0;
  pointer-events: none;
  color: white;
  font-family: 'Microsoft YaHei', sans-serif;
  z-index: 10;
}

.bottom-bar {
  pointer-events: auto;
}

.shop-panel {
  pointer-events: auto;
}

.skill-tooltip {
  pointer-events: auto;
}

.chat-input-row input {
  pointer-events: auto;
}


.top-bar {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 8px 20px;
  background: linear-gradient(180deg, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0.5) 80%, transparent 100%);
  gap: 40px;
}

.team-score {
  display: flex;
  align-items: center;
  gap: 15px;
  background: rgba(0,0,0,0.4);
  padding: 6px 15px;
  border-radius: 20px;
}

.team-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
}

.score-display {
  display: flex;
  align-items: center;
  gap: 6px;
}

.score-value {
  font-size: 22px;
  font-weight: 800;
  min-width: 30px;
  text-align: center;
}

.score-divider {
  font-size: 16px;
  color: rgba(255,255,255,0.3);
}

.game-time-container {
  text-align: center;
}

.game-phase {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 10px;
  margin-bottom: 2px;
}

.game-phase.early { background: rgba(46, 204, 113, 0.2); color: #2ecc71; }
.game-phase.mid { background: rgba(243, 156, 18, 0.2); color: #f39c12; }
.game-phase.late { background: rgba(231, 76, 60, 0.2); color: #e74c3c; }

.game-time {
  font-size: 20px;
  font-weight: 700;
  font-family: 'Consolas', monospace;
}

.fps-ping {
  display: flex;
  gap: 12px;
  font-size: 12px;
  background: rgba(0,0,0,0.4);
  padding: 6px 12px;
  border-radius: 12px;
}

.ping-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 4px;
}

.ping-dot.good { background: #2ecc71; }
.ping-dot.medium { background: #f39c12; }
.ping-dot.bad { background: #e74c3c; }

.minimap-container {
  position: absolute;
  top: 60px;
  left: 15px;
}

.minimap-frame {
  width: 200px;
  height: 200px;
  background: #0a0a0a;
  border: 2px solid rgba(255,255,255,0.2);
  border-radius: 8px;
  overflow: hidden;
  position: relative;
}

.minimap-frame-img {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 2;
}

.minimap-frame canvas {
  width: 100%;
  height: 100%;
}

.minimap-stats {
  display: flex;
  gap: 10px;
  margin-top: 6px;
  justify-content: center;
}

.stat-item {
  background: rgba(0,0,0,0.6);
  padding: 3px 10px;
  border-radius: 10px;
  font-size: 12px;
}

.scoreboard {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%) scale(0.95);
  background: rgba(10, 10, 20, 0.95);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 12px;
  padding: 20px;
  opacity: 0;
  pointer-events: none;
  transition: all 0.2s ease;
  max-width: 800px;
  width: 90%;
}

.scoreboard.visible {
  opacity: 1;
  transform: translate(-50%, -50%) scale(1);
}

.scoreboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.scoreboard-header h3 { margin: 0; font-size: 18px; }

.scoreboard-hint {
  font-size: 12px;
  color: rgba(255,255,255,0.4);
}

.scoreboard-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.scoreboard-table th {
  text-align: left;
  padding: 8px 10px;
  color: rgba(255,255,255,0.5);
  font-weight: 500;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}

.scoreboard-table td {
  padding: 8px 10px;
  border-bottom: 1px solid rgba(255,255,255,0.05);
}

.scoreboard-table tr.is-me {
  background: rgba(74, 158, 255, 0.1);
}

.scoreboard-table tr.is-dead {
  opacity: 0.5;
}

.hero-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hero-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: bold;
  overflow: hidden;
}

.scoreboard-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.hero-info { display: flex; flex-direction: column; }
.hero-name { font-weight: 500; }
.hero-level { font-size: 11px; color: #ffd700; }

.kda-cell { font-family: monospace; font-weight: 600; }
.kills { color: #2ecc71; }
.deaths { color: #e74c3c; }
.assists { color: #3498db; }
.separator { color: rgba(255,255,255,0.3); }

.gold-cell { color: #ffd700; }
.gold-icon { margin-right: 4px; }

.items-cell {
  display: flex;
  gap: 3px;
}

.mini-item {
  width: 22px;
  height: 22px;
  background: rgba(255,255,255,0.1);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.mini-item-img {
  width: 18px;
  height: 18px;
  object-fit: contain;
}

.mini-item.empty {
  background: rgba(255,255,255,0.03);
  color: rgba(255,255,255,0.2);
}

.hero-panel {
  position: absolute;
  bottom: 180px;
  left: 15px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: rgba(0,0,0,0.7);
  padding: 12px;
  border-radius: 10px;
  border: 1px solid rgba(255,255,255,0.1);
}

.hero-avatar-section {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hero-portrait {
  width: 50px;
  height: 50px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  border: 2px solid rgba(255,255,255,0.3);
  position: relative;
}

.avatar-frame-img {
  position: absolute;
  top: -2px; left: -2px;
  width: calc(100% + 4px);
  height: calc(100% + 4px);
  pointer-events: none;
  z-index: 1;
}

.portrait-level {
  position: absolute;
  bottom: -5px;
  right: -5px;
  background: #ffd700;
  color: #000;
  font-size: 10px;
  font-weight: bold;
  padding: 1px 5px;
  border-radius: 8px;
}

.hero-name-badge {
  display: flex;
  flex-direction: column;
}

.hero-name-badge .name {
  font-weight: 600;
  font-size: 15px;
}

.hero-name-badge .title {
  font-size: 11px;
  color: rgba(255,255,255,0.5);
}

.resource-bars {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bar-label {
  font-size: 10px;
  font-weight: bold;
  width: 25px;
}

.bar-track {
  flex: 1;
  height: 12px;
  background: rgba(0,0,0,0.5);
  border-radius: 6px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 6px;
  transition: width 0.3s ease;
}

.hp-fill { background: linear-gradient(90deg, #27ae60, #2ecc71); }
.mp-fill { background: linear-gradient(90deg, #2980b9, #3498db); }
.xp-fill { background: linear-gradient(90deg, #8e44ad, #9b59b6); }

.bar-value {
  font-size: 10px;
  color: rgba(255,255,255,0.7);
  min-width: 70px;
  text-align: right;
}

.stats-row {
  display: flex;
  gap: 6px;
}

.stat-box {
  background: rgba(255,255,255,0.05);
  padding: 4px 8px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
}

.stat-num { font-weight: 600; }

.bottom-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: 10px 15px;
  background: linear-gradient(0deg, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0.7) 60%, transparent 100%);
  pointer-events: auto;
}

.left-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.items-grid {
  display: grid;
  grid-template-columns: repeat(3, 40px);
  gap: 4px;
}

.item-slot {
  width: 40px;
  height: 40px;
  background: rgba(255,255,255,0.08);
  border: 1px solid rgba(255,255,255,0.15);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
  font-size: 18px;
}

.item-frame-img {
  position: absolute;
  top: 0; left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.item-img {
  width: 28px;
  height: 28px;
  object-fit: contain;
  position: relative;
  z-index: 0;
}

.item-slot.empty {
  color: rgba(255,255,255,0.2);
}

.item-tooltip {
  display: none;
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(10,10,20,0.95);
  padding: 8px;
  border-radius: 6px;
  white-space: nowrap;
  z-index: 100;
  border: 1px solid rgba(255,255,255,0.2);
}

.item-slot:hover .item-tooltip {
  display: block;
}

.tooltip-name { font-weight: 600; margin-bottom: 3px; }
.tooltip-desc { font-size: 11px; color: rgba(255,255,255,0.6); }

.gold-panel { margin-top: 5px; }
.gold-display {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(0,0,0,0.5);
  padding: 6px 12px;
  border-radius: 8px;
}

.gold-icon-large { font-size: 18px; }
.gold-info { display: flex; flex-direction: column; }
.gold-current { font-size: 16px; font-weight: 700; color: #ffd700; }
.gold-total { font-size: 10px; color: rgba(255,255,255,0.4); }

.center-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.skills-container {
  display: flex;
  align-items: center;
  gap: 15px;
}

.passive-skill {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.passive-skill .skill-icon {
  width: 40px;
  height: 40px;
  border: 2px solid rgba(255,255,255,0.3);
}

.skill-key-passive {
  font-size: 9px;
  color: rgba(255,255,255,0.5);
}

.active-skills {
  display: flex;
  gap: 8px;
}

.skill-slot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  cursor: pointer;
  position: relative;
}

.skill-slot:hover .skill-icon {
  border-color: #4a9eff;
}

.skill-icon-wrapper {
  position: relative;
  overflow: hidden;
}

.skill-frame-img {
  position: absolute;
  top: 0; left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.skill-icon {
  width: 50px;
  height: 50px;
  border: 2px solid rgba(255,255,255,0.2);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  background: #333;
}

.cooldown-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  background: rgba(0,0,0,0.7);
  pointer-events: none;
}

.skill-key {
  font-size: 11px;
  font-weight: 600;
  color: rgba(255,255,255,0.7);
}

.cooldown-text {
  font-size: 12px;
  font-weight: bold;
  color: #e74c3c;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10;
}

.summoner-spells {
  display: flex;
  gap: 8px;
}

.spell-slot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  cursor: pointer;
}

.spell-icon {
  width: 40px;
  height: 40px;
  border: 2px solid rgba(255,255,255,0.2);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.spell-key {
  font-size: 10px;
  font-weight: 600;
}

.shop-btn {
  background: linear-gradient(135deg, #f39c12, #e67e22);
  border: none;
  color: white;
  padding: 8px 20px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  pointer-events: auto;
  transition: all 0.2s;
}

.shop-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(243, 156, 18, 0.3);
}

.right-section {
  max-width: 250px;
}

.chat-container {
  background: rgba(0,0,0,0.6);
  border-radius: 10px;
  overflow: hidden;
  width: 250px;
}

.chat-tabs {
  display: flex;
  background: rgba(0,0,0,0.3);
}

.chat-tab {
  flex: 1;
  padding: 6px;
  background: transparent;
  border: none;
  color: rgba(255,255,255,0.5);
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
}

.chat-tab.active {
  color: white;
  background: rgba(255,255,255,0.1);
}

.chat-messages {
  max-height: 100px;
  overflow-y: auto;
  padding: 8px;
}

.chat-msg {
  margin-bottom: 4px;
  font-size: 12px;
  line-height: 1.4;
}

.msg-sender {
  font-weight: 600;
  margin-right: 5px;
}

.msg-text {
  color: rgba(255,255,255,0.8);
}

.chat-input-row {
  border-top: 1px solid rgba(255,255,255,0.1);
}

.chat-input-row input {
  width: 100%;
  padding: 8px;
  background: transparent;
  border: none;
  color: white;
  outline: none;
  font-size: 12px;
}

.shop-panel {
  position: absolute;
  bottom: 180px;
  right: 270px;
  width: 350px;
  background: rgba(10,10,20,0.95);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 12px;
  padding: 15px;
  pointer-events: auto;
  max-height: 500px;
  overflow-y: auto;
}

.shop-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}

.shop-header h3 { margin: 0; font-size: 16px; }
.shop-gold { color: #ffd700; font-weight: 600; }
.close-btn {
  background: transparent;
  border: none;
  color: rgba(255,255,255,0.5);
  cursor: pointer;
  font-size: 16px;
}

.shop-categories {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.cat-btn {
  padding: 5px 10px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 15px;
  color: white;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
}

.cat-btn.active {
  background: rgba(74, 158, 255, 0.2);
  border-color: rgba(74, 158, 255, 0.5);
}

.shop-items {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.shop-item {
  padding: 10px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px;
  display: flex;
  gap: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.shop-item:hover {
  background: rgba(255,255,255,0.1);
  border-color: rgba(255,255,255,0.3);
}

.shop-item.can-afford {
  border-color: rgba(255, 215, 0, 0.3);
}

.shop-item-icon {
  font-size: 24px;
  width: 40px;
  height: 40px;
  background: rgba(0,0,0,0.3);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.shop-item-icon .item-img {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.shop-item-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.shop-item-name { font-weight: 500; font-size: 13px; }
.shop-item-stats { font-size: 10px; color: rgba(255,255,255,0.4); }
.shop-item-price { color: #ffd700; font-size: 12px; font-weight: 600; }

.skill-tooltip {
  position: fixed;
  background: rgba(10,10,20,0.95);
  border: 1px solid rgba(255,255,255,0.2);
  border-radius: 8px;
  padding: 12px;
  z-index: 1000;
  min-width: 200px;
}

.tooltip-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.tooltip-key {
  background: rgba(255,255,255,0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: bold;
}

.tooltip-header h4 { margin: 0; font-size: 14px; }
.tooltip-desc { font-size: 12px; color: rgba(255,255,255,0.6); margin-bottom: 8px; }
.tooltip-meta { display: flex; gap: 10px; font-size: 11px; color: #4a9eff; }
</style>
