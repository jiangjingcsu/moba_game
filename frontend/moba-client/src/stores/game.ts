import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Hero, TeamInfo, ChatMessage, GameEvent, Vector2D } from '@/types/game'
import { HeroRole } from '@/types/game'

export interface PlayerInfo {
  userId: number
  playerName: string
  nickname: string
  rank: number
  rankScore: number
  level: number
  avatar: string
  gold: number
  diamond: number
  isSignedIn: boolean
  signInDays: number
}

export interface BattleInfo {
  battleId: number
  mapId: number
  mapConfig: string
  heroId: number
  teamId: number
}

export interface GameMode {
  id: string
  code: number
  name: string
  description: string
  icon: string
  type: 'ranked' | 'casual' | 'custom' | 'tutorial'
  playerCount: string
  mapName: string
  isLocked: boolean
  lockReason: string
}

export interface DailyTask {
  id: string
  name: string
  description: string
  icon: string
  progress: number
  target: number
  reward: { type: 'gold' | 'diamond' | 'exp'; amount: number }
  isCompleted: boolean
  isClaimed: boolean
}

export const useGameStore = defineStore('game', () => {
  const gameState = ref<'login' | 'lobby' | 'hero_select' | 'matching' | 'loading' | 'playing' | 'ended'>(
    localStorage.getItem('moba_auth_token') ? 'lobby' : 'login'
  )
  const frame = ref(0)
  const gameTime = ref(0)
  const myHeroId = ref<string>('')
  const heroes = ref<Map<string, Hero>>(new Map())
  const isPaused = ref(false)
  const showScoreboard = ref(false)
  const selectedItem = ref<string | null>(null)
  const hoveredHeroId = ref<string | null>(null)
  const selectedGameMode = ref<string>('')
  const selectedHeroId = ref<string>('')
  const isOnlineMode = ref(false)

  const playerInfo = ref<PlayerInfo | null>(
    JSON.parse(localStorage.getItem('moba_player_info') || 'null')
  )
  const battleInfo = ref<BattleInfo | null>(null)
  const matchWaitTime = ref(0)

  const gameModes = ref<GameMode[]>([
    { id: 'ranked_3v3v3', code: 3, name: '排位赛', description: '3v3v3 竞技排位，赢取段位积分', icon: '🏆', type: 'ranked', playerCount: '3v3v3', mapName: '三方峡谷', isLocked: false, lockReason: '' },
    { id: 'casual_3v3v3', code: 3, name: '匹配赛', description: '3v3v3 休闲匹配，轻松对战', icon: '⚔️', type: 'casual', playerCount: '3v3v3', mapName: '三方峡谷', isLocked: false, lockReason: '' },
    { id: 'pve_3v3v3', code: 5, name: '人机模式', description: '3v3v3 人机对战，练习英雄技巧', icon: '🤖', type: 'pve', playerCount: '3v3v3', mapName: '三方峡谷', isLocked: false, lockReason: '' },
    { id: 'casual_5v5', code: 2, name: '5v5匹配', description: '5v5 休闲对战，经典模式', icon: '🛡️', type: 'casual', playerCount: '5v5', mapName: '标准战场', isLocked: false, lockReason: '' },
    { id: 'pve_5v5', code: 4, name: '5v5人机', description: '5v5 人机对战，练习配合', icon: '🤖', type: 'pve', playerCount: '5v5', mapName: '标准战场', isLocked: false, lockReason: '' },
    { id: 'tutorial', code: 0, name: '新手教程', description: '学习游戏基础操作和规则', icon: '📖', type: 'tutorial', playerCount: '单人', mapName: '训练场', isLocked: false, lockReason: '' },
  ])

  const dailyTasks = ref<DailyTask[]>([
    { id: 'task_1', name: '首胜奖励', description: '赢得第一场对战', icon: '🏅', progress: 0, target: 1, reward: { type: 'gold', amount: 500 }, isCompleted: false, isClaimed: false },
    { id: 'task_2', name: '战斗达人', description: '完成3场对战', icon: '⚔️', progress: 0, target: 3, reward: { type: 'gold', amount: 300 }, isCompleted: false, isClaimed: false },
    { id: 'task_3', name: '击杀之王', description: '累计击杀10名敌方英雄', icon: '💀', progress: 0, target: 10, reward: { type: 'diamond', amount: 50 }, isCompleted: false, isClaimed: false },
    { id: 'task_4', name: '团队协作', description: '获得5次助攻', icon: '🤝', progress: 0, target: 5, reward: { type: 'gold', amount: 200 }, isCompleted: false, isClaimed: false },
    { id: 'task_5', name: '经济大师', description: '单场获得5000金币', icon: '💰', progress: 0, target: 5000, reward: { type: 'diamond', amount: 30 }, isCompleted: false, isClaimed: false },
  ])

  const teams = ref<Map<number, TeamInfo>>(new Map([
    [0, { id: 0, name: '蓝方', color: '#4a9eff', kills: 0, towers: 0, gold: 0, dragons: 0, barons: 0 }],
    [1, { id: 1, name: '红方', color: '#ff4a4a', kills: 0, towers: 0, gold: 0, dragons: 0, barons: 0 }],
    [2, { id: 2, name: '橙方', color: '#ffa500', kills: 0, towers: 0, gold: 0, dragons: 0, barons: 0 }],
  ]))

  const chatMessages = ref<ChatMessage[]>([])
  const gameEvents = ref<GameEvent[]>([])

  const isLoggedIn = computed(() => playerInfo.value !== null)
  const myHero = computed(() => heroes.value.get(myHeroId.value) || null)
  const myTeamId = computed(() => myHero.value?.teamId ?? -1)

  const teamHeroes = computed(() => {
    if (myTeamId.value === -1) return []
    return Array.from(heroes.value.values()).filter(h => h.teamId === myTeamId.value)
  })

  const enemyHeroes = computed(() => {
    if (myTeamId.value === -1) return []
    return Array.from(heroes.value.values()).filter(h => h.teamId !== myTeamId.value)
  })

  const allHeroesSorted = computed(() => {
    return Array.from(heroes.value.values()).sort((a, b) => {
      if (a.teamId !== b.teamId) return a.teamId - b.teamId
      return b.kda.kills - a.kda.kills
    })
  })

  const totalKills = computed(() => {
    const result = [0, 0, 0]
    heroes.value.forEach(h => {
      if (h.teamId >= 0 && h.teamId < 3) {
        result[h.teamId] += h.kda.kills
      }
    })
    return result
  })

  const gamePhase = computed(() => {
    if (gameTime.value < 600) return 'early'
    if (gameTime.value < 1200) return 'mid'
    return 'late'
  })

  const rankName = computed(() => {
    if (!playerInfo.value) return ''
    const r = playerInfo.value.rank
    if (r <= 1) return '青铜'
    if (r <= 2) return '白银'
    if (r <= 3) return '黄金'
    if (r <= 4) return '铂金'
    if (r <= 5) return '钻石'
    if (r <= 6) return '大师'
    return '王者'
  })

  function setMyHero(heroId: string) {
    myHeroId.value = heroId
  }

  function updateHero(heroId: string, data: Partial<Hero>) {
    const hero = heroes.value.get(heroId)
    if (hero) {
      Object.assign(hero, data)
      heroes.value.set(heroId, hero)
    }
  }

  function addHero(hero: Hero) {
    heroes.value.set(hero.id, hero)
  }

  function removeHero(heroId: string) {
    heroes.value.delete(heroId)
  }

  function updateTeam(teamId: number, data: Partial<TeamInfo>) {
    const team = teams.value.get(teamId)
    if (team) {
      Object.assign(team, data)
      teams.value.set(teamId, team)
    }
  }

  function addChatMessage(msg: ChatMessage) {
    chatMessages.value.push(msg)
    if (chatMessages.value.length > 100) {
      chatMessages.value.shift()
    }
  }

  function addGameEvent(event: GameEvent) {
    gameEvents.value.push(event)
    if (gameEvents.value.length > 50) {
      gameEvents.value.shift()
    }
  }

  function nextFrame() {
    frame.value++
  }

  function updateGameTime(delta: number) {
    gameTime.value += delta
  }

  function setGameState(state: typeof gameState.value) {
    gameState.value = state
  }

  function toggleScoreboard() {
    showScoreboard.value = !showScoreboard.value
  }

  function setHoveredHero(heroId: string | null) {
    hoveredHeroId.value = heroId
  }

  function setPlayerInfo(info: PlayerInfo | null) {
    playerInfo.value = info
    if (info) {
      localStorage.setItem('moba_player_info', JSON.stringify(info))
    } else {
      localStorage.removeItem('moba_player_info')
    }
  }

  function setBattleInfo(info: BattleInfo | null) {
    battleInfo.value = info
  }

  function setMatchWaitTime(time: number) {
    matchWaitTime.value = time
  }

  function setSelectedGameMode(modeId: string) {
    selectedGameMode.value = modeId
  }

  function setSelectedHeroId(heroId: string) {
    selectedHeroId.value = heroId
  }

  function signIn() {
    if (playerInfo.value && !playerInfo.value.isSignedIn) {
      playerInfo.value.isSignedIn = true
      playerInfo.value.signInDays++
      playerInfo.value.gold += 100
    }
  }

  function claimTask(taskId: string) {
    const task = dailyTasks.value.find(t => t.id === taskId)
    if (task && task.isCompleted && !task.isClaimed) {
      task.isClaimed = true
      if (playerInfo.value) {
        if (task.reward.type === 'gold') {
          playerInfo.value.gold += task.reward.amount
        } else if (task.reward.type === 'diamond') {
          playerInfo.value.diamond += task.reward.amount
        }
      }
    }
  }

  function resetBattleState() {
    frame.value = 0
    gameTime.value = 0
    myHeroId.value = ''
    heroes.value.clear()
    battleInfo.value = null
    teams.value.forEach((team) => {
      team.kills = 0
      team.towers = 0
      team.gold = 0
      team.dragons = 0
      team.barons = 0
    })
    chatMessages.value = []
    gameEvents.value = []
  }

  function logout() {
    playerInfo.value = null
    gameState.value = 'login'
    selectedGameMode.value = ''
    selectedHeroId.value = ''
    localStorage.removeItem('moba_auth_token')
    localStorage.removeItem('moba_player_info')
  }

  return {
    gameState,
    frame,
    gameTime,
    myHeroId,
    heroes,
    isPaused,
    teams,
    chatMessages,
    gameEvents,
    showScoreboard,
    selectedItem,
    hoveredHeroId,
    myHero,
    myTeamId,
    teamHeroes,
    enemyHeroes,
    allHeroesSorted,
    totalKills,
    gamePhase,
    playerInfo,
    battleInfo,
    matchWaitTime,
    gameModes,
    dailyTasks,
    selectedGameMode,
    selectedHeroId,
    isLoggedIn,
    isOnlineMode,
    rankName,
    setMyHero,
    updateHero,
    addHero,
    removeHero,
    updateTeam,
    addChatMessage,
    addGameEvent,
    nextFrame,
    updateGameTime,
    setGameState,
    toggleScoreboard,
    setHoveredHero,
    setPlayerInfo,
    setBattleInfo,
    setMatchWaitTime,
    setSelectedGameMode,
    setSelectedHeroId,
    signIn,
    claimTask,
    resetBattleState,
    logout,
  }
})
