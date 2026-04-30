import { GameWebSocket, ConnectionState } from './WebSocket'
import { MessageId, BattleEnterResponse, BattleStateUpdate, BattleEndNotify, ReconnectResponse } from './protocol'
import { ref } from 'vue'

const API_BASE = 'http://localhost:8080/api'
const BATTLE_WS_URL = 'ws://localhost:8888/ws/battle'

export const ws = new GameWebSocket(BATTLE_WS_URL)
export const isSynced = ref(false)
export const ping = ref(0)
export const isConnected = ref(false)
export const battlePlayerId = ref<number>(0)
export const battleId = ref<string>('')

export interface PlayerInfo {
  playerId: number
  playerName: string
  nickname: string
  rank: number
  rankScore: number
  level?: number
  avatar?: string
  gold?: number
  diamond?: number
  isSignedIn?: boolean
  signInDays?: number
}

export const RESPONSE_CODE = {
  SUCCESS: 0,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  CONFLICT: 409,
  TOO_MANY_REQUESTS: 429,
  SERVER_ERROR: 500,
} as const

function isApiSuccess(result: any): boolean {
  return result.code === RESPONSE_CODE.SUCCESS
}

let currentPlayer: PlayerInfo | null = null
let authToken: string | null = localStorage.getItem('moba_auth_token')
let frameBuffer: BattleStateUpdate[] = []
let currentFrame = 0
let lastPingTime = 0

function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (authToken) {
    headers['Authorization'] = `Bearer ${authToken}`
  }
  return headers
}

export async function httpLogin(username: string, password: string): Promise<{ success: boolean; playerInfo?: PlayerInfo; token?: string; error?: string }> {
  try {
    const response = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })
    const result = await response.json()
    if (isApiSuccess(result) && result.data) {
      authToken = result.data.token
      localStorage.setItem('moba_auth_token', authToken)
      currentPlayer = {
        playerId: result.data.playerId,
        playerName: result.data.username,
        nickname: result.data.nickname,
        rank: result.data.rank || 1,
        rankScore: result.data.rankScore || 1000,
        level: result.data.level || 1,
        avatar: result.data.avatar || '',
        gold: result.data.gold || 0,
        diamond: result.data.diamond || 0,
        isSignedIn: result.data.isSignedIn || false,
        signInDays: result.data.signInDays || 0,
      }
      return { success: true, playerInfo: currentPlayer, token: authToken! }
    }
    return { success: false, error: result.message || '登录失败' }
  } catch (e: any) {
    return { success: false, error: e.message || '网络错误' }
  }
}

export async function httpRegister(username: string, password: string, nickname: string): Promise<{ success: boolean; playerInfo?: PlayerInfo; token?: string; error?: string }> {
  try {
    const response = await fetch(`${API_BASE}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password, nickname }),
    })
    const result = await response.json()
    if (isApiSuccess(result) && result.data) {
      authToken = result.data.token
      currentPlayer = {
        playerId: result.data.playerId,
        playerName: result.data.username,
        nickname: result.data.nickname,
        rank: result.data.rank || 1,
        rankScore: result.data.rankScore || 1000,
        level: result.data.level || 1,
        avatar: result.data.avatar || '',
        gold: result.data.gold || 0,
        diamond: result.data.diamond || 0,
        isSignedIn: result.data.isSignedIn || false,
        signInDays: result.data.signInDays || 0,
      }
      return { success: true, playerInfo: currentPlayer, token: authToken! }
    }
    return { success: false, error: result.message || '注册失败' }
  } catch (e: any) {
    return { success: false, error: e.message || '网络错误' }
  }
}

export async function joinMatch(gameMode: number = 1): Promise<{ success: boolean; error?: string }> {
  try {
    const response = await fetch(`${API_BASE}/match/join`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ gameMode }),
    })
    const result = await response.json()
    return { success: isApiSuccess(result), error: result.message }
  } catch (e: any) {
    return { success: false, error: e.message || '网络错误' }
  }
}

export async function getMatchStatus(): Promise<{ matched: boolean; battleId?: string; battleServerUrl?: string; playerIds?: number[]; error?: string }> {
  try {
    const response = await fetch(`${API_BASE}/match/status`, {
      method: 'GET',
      headers: getAuthHeaders(),
    })
    const result = await response.json()
    if (isApiSuccess(result) && result.data) {
      if (result.data.matchStatus === 'MATCHED') {
        return {
          matched: true,
          battleId: result.data.battleId,
          battleServerUrl: result.data.battleServerUrl,
          playerIds: result.data.playerIds,
        }
      }
      return { matched: false }
    }
    return { matched: false, error: result.message }
  } catch (e: any) {
    return { matched: false, error: e.message || '网络错误' }
  }
}

export async function cancelMatch(): Promise<{ success: boolean }> {
  try {
    const response = await fetch(`${API_BASE}/match/cancel`, {
      method: 'POST',
      headers: getAuthHeaders(),
    })
    const result = await response.json()
    return { success: isApiSuccess(result) }
  } catch (e: any) {
    return { success: false }
  }
}

export async function connectToBattleServer(): Promise<boolean> {
  try {
    if (ws.state.value === ConnectionState.CONNECTED) {
      return true
    }

    await ws.connect()
    isConnected.value = true
    setupMessageHandlers()

    const loginSuccess = await sendLoginRequest()
    if (!loginSuccess) {
      console.error('Battle server login failed')
      return false
    }

    return true
  } catch (e) {
    console.error('Failed to connect to battle server:', e)
    isConnected.value = false
    return false
  }
}

function sendLoginRequest(): Promise<boolean> {
  const playerName = currentPlayer?.playerName || currentPlayer?.nickname || `Player_${Date.now()}`

  return new Promise((resolve) => {
    const handler = (data: any) => {
      ws.off(MessageId.LOGIN_RESPONSE, handler)
      if (data.success) {
        battlePlayerId.value = data.playerId
        console.log('Battle server login success, playerId:', data.playerId)
        resolve(true)
      } else {
        console.error('Battle server login failed:', data.errorMessage)
        resolve(false)
      }
    }

    ws.on(MessageId.LOGIN_RESPONSE, handler)
    ws.send(MessageId.LOGIN_REQUEST, {
      playerName,
      clientVersion: 1,
      platform: 'web',
    })

    setTimeout(() => {
      ws.off(MessageId.LOGIN_RESPONSE, handler)
      resolve(false)
    }, 10000)
  })
}

function setupMessageHandlers() {
  ws.on(MessageId.HEARTBEAT_REQUEST, () => {
    ws.send(MessageId.HEARTBEAT_RESPONSE)
    ping.value = Date.now() - lastPingTime
    lastPingTime = Date.now()
  })

  ws.on(MessageId.HEARTBEAT_RESPONSE, () => {
    ping.value = Date.now() - lastPingTime
  })

  ws.on(MessageId.BATTLE_STATE_UPDATE, (data: BattleStateUpdate) => {
    if (data.frame > currentFrame) {
      currentFrame = data.frame
      frameBuffer.push(data)
    }
  })

  ws.on(MessageId.BATTLE_END_NOTIFY, (data: BattleEndNotify) => {
    console.log('战斗结束:', data)
  })
}

export function sendMatchRequest(): Promise<{ success: boolean; battleId?: string; error?: string }> {
  return new Promise((resolve) => {
    const handler = (data: any) => {
      ws.off(MessageId.MATCH_RESPONSE, handler)
      if (data.success && data.matchStatus === 1) {
        battleId.value = data.battleId
        resolve({ success: true, battleId: data.battleId })
      } else if (data.success && data.matchStatus === 0) {
        resolve({ success: true })
      } else {
        resolve({ success: false, error: data.errorMessage || '匹配失败' })
      }
    }

    ws.on(MessageId.MATCH_RESPONSE, handler)
    ws.send(MessageId.MATCH_REQUEST, {
      playerId: battlePlayerId.value,
      matchType: 1,
      preferredRole: 0,
    })

    setTimeout(() => {
      ws.off(MessageId.MATCH_RESPONSE, handler)
      resolve({ success: false, error: '匹配超时' })
    }, 30000)
  })
}

export function enterBattle(bId: string, heroId: number, teamId: number): Promise<BattleEnterResponse> {
  return new Promise((resolve) => {
    const request = {
      playerId: battlePlayerId.value,
      battleId: bId,
      heroId,
      teamId,
    }

    const handler = (data: BattleEnterResponse) => {
      ws.off(MessageId.BATTLE_ENTER_RESPONSE, handler)
      if (data.success) {
        battleId.value = bId
      }
      resolve(data)
    }

    ws.on(MessageId.BATTLE_ENTER_RESPONSE, handler)
    ws.send(MessageId.BATTLE_ENTER_REQUEST, request)

    setTimeout(() => {
      ws.off(MessageId.BATTLE_ENTER_RESPONSE, handler)
      resolve({ success: false, battleId: '', mapId: 0, mapConfig: '', errorMessage: '进入战斗超时' })
    }, 10000)
  })
}

export function reconnect(bId: string): Promise<ReconnectResponse> {
  return new Promise((resolve) => {
    const request = {
      playerId: battlePlayerId.value,
      battleId: bId,
    }

    const handler = (data: ReconnectResponse) => {
      ws.off(MessageId.RECONNECT_RESPONSE, handler)
      resolve(data)
    }

    ws.on(MessageId.RECONNECT_RESPONSE, handler)
    ws.send(MessageId.RECONNECT_REQUEST, request)

    setTimeout(() => {
      ws.off(MessageId.RECONNECT_RESPONSE, handler)
      resolve({ success: false, battleState: '', errorMessage: '重连超时' })
    }, 10000)
  })
}

export function sendInput(input: { heroId: string; type: string; targetX?: number; targetY?: number; targetId?: string; skillId?: string; itemId?: string; frame: number }) {
  if (!isConnected.value) return
  ws.send(MessageId.PLAYER_ACTION_REQUEST, input)
}

export function sendMoveInput(heroId: string, targetX: number, targetY: number, frame: number) {
  sendInput({
    heroId,
    type: 'move',
    targetX,
    targetY,
    frame,
  })
}

export function sendSkillInput(heroId: string, skillId: string, targetX: number, targetY: number, frame: number) {
  if (!isConnected.value) return
  ws.send(MessageId.SKILL_CAST_REQUEST, {
    heroId,
    skillId,
    targetX,
    targetY,
    frame,
  })
}

export function requestResync() {
  isSynced.value = false
}

export function getFrameBuffer() {
  const buffer = [...frameBuffer]
  frameBuffer = []
  return buffer
}

export function getCurrentFrame() {
  return currentFrame
}

export function getCurrentPlayer(): PlayerInfo | null {
  return currentPlayer
}

export function getAuthToken(): string | null {
  return authToken
}

export function disconnect() {
  ws.disconnect()
  isConnected.value = false
  currentPlayer = null
  authToken = null
  isSynced.value = false
  frameBuffer = []
  currentFrame = 0
  battlePlayerId.value = 0
  battleId.value = ''
}
