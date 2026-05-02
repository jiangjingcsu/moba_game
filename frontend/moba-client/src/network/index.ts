import { GameWebSocket, ConnectionState } from './WebSocket'
import { MessageId, BattleEnterResponse, BattleStateUpdate, BattleEndNotify, ReconnectResponse, MatchStatusResponse, MatchSuccessNotify } from './protocol'
import { ref } from 'vue'

const API_BASE = 'http://localhost:8080/api'
const GATEWAY_WS_URL = 'ws://localhost:9999/ws/game'

export const ws = new GameWebSocket(GATEWAY_WS_URL)
export const isSynced = ref(false)
export const ping = ref(0)
export const isConnected = ref(false)
export const battlePlayerId = ref<number>(0)
export const battleId = ref<string>('')
export const matchSuccessData = ref<MatchSuccessNotify | null>(null)

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

function getGatewayWsUrl(): string {
    if (authToken) {
        return `${GATEWAY_WS_URL}?token=${authToken}`
    }
    return GATEWAY_WS_URL
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
            localStorage.setItem('moba_auth_token', authToken!)
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

export async function connectToGateway(): Promise<boolean> {
    try {
        if (ws.state.value === ConnectionState.CONNECTED) {
            return true
        }

        ws['url'] = getGatewayWsUrl()
        await ws.connect()
        isConnected.value = true
        setupMessageHandlers()

        return true
    } catch (e) {
        console.error('Failed to connect to gateway:', e)
        isConnected.value = false
        return false
    }
}

export function joinMatch(gameMode: number = 1): Promise<{ success: boolean; matchId?: string; error?: string }> {
    return new Promise((resolve) => {
        const handler = (data: any) => {
            ws.off(MessageId.MATCH_JOIN_RESP, handler)
            if (data.success) {
                battlePlayerId.value = currentPlayer?.playerId || 0
                resolve({ success: true, matchId: data.matchId })
            } else {
                resolve({ success: false, error: data.errorMessage || data.errorCode || '匹配失败' })
            }
        }

        ws.on(MessageId.MATCH_JOIN_RESP, handler)
        ws.send(MessageId.MATCH_JOIN_REQ, {
            playerId: currentPlayer?.playerId || 0,
            nickname: currentPlayer?.nickname || '',
            rankScore: currentPlayer?.rankScore || 1000,
            gameMode,
        })

        setTimeout(() => {
            ws.off(MessageId.MATCH_JOIN_RESP, handler)
            resolve({ success: false, error: '匹配超时' })
        }, 30000)
    })
}

export function getMatchStatus(): Promise<{ matched: boolean; battleId?: string; battleServerIp?: string; battleServerPort?: number; error?: string }> {
    return new Promise((resolve) => {
        const handler = (data: MatchStatusResponse) => {
            ws.off(MessageId.MATCH_STATUS_RESP, handler)
            if (data.found) {
                resolve({
                    matched: true,
                    battleId: data.battleId || data.matchId,
                    battleServerIp: data.battleServerIp,
                    battleServerPort: data.battleServerPort,
                })
            } else {
                resolve({ matched: false })
            }
        }

        ws.on(MessageId.MATCH_STATUS_RESP, handler)
        ws.send(MessageId.MATCH_STATUS_REQ, {
            playerId: currentPlayer?.playerId || 0,
        })

        setTimeout(() => {
            ws.off(MessageId.MATCH_STATUS_RESP, handler)
            resolve({ matched: false, error: '查询超时' })
        }, 10000)
    })
}

export function cancelMatch(): Promise<{ success: boolean }> {
    return new Promise((resolve) => {
        const handler = (data: any) => {
            ws.off(MessageId.MATCH_CANCEL_RESP, handler)
            resolve({ success: data.success !== false })
        }

        ws.on(MessageId.MATCH_CANCEL_RESP, handler)
        ws.send(MessageId.MATCH_CANCEL_REQ, {
            playerId: currentPlayer?.playerId || 0,
        })

        setTimeout(() => {
            ws.off(MessageId.MATCH_CANCEL_RESP, handler)
            resolve({ success: false })
        }, 10000)
    })
}

export function sendMatchRequest(): Promise<{ success: boolean; battleId?: string; error?: string }> {
    return new Promise((resolve) => {
        const handler = (data: any) => {
            ws.off(MessageId.MATCH_JOIN_RESP, handler)
            if (data.success) {
                if (data.state === 'READY') {
                    battleId.value = data.matchId
                    resolve({ success: true, battleId: data.matchId })
                } else {
                    resolve({ success: true })
                }
            } else {
                resolve({ success: false, error: data.errorMessage || '匹配失败' })
            }
        }

        ws.on(MessageId.MATCH_JOIN_RESP, handler)
        ws.send(MessageId.MATCH_JOIN_REQ, {
            playerId: battlePlayerId.value,
            matchType: 1,
            preferredRole: 0,
        })

        setTimeout(() => {
            ws.off(MessageId.MATCH_JOIN_RESP, handler)
            resolve({ success: false, error: '匹配超时' })
        }, 30000)
    })
}

function setupMessageHandlers() {
    ws.on(MessageId.HEARTBEAT_REQ, () => {
        ws.send(MessageId.HEARTBEAT_RESP)
        ping.value = Date.now() - lastPingTime
        lastPingTime = Date.now()
    })

    ws.on(MessageId.HEARTBEAT_RESP, () => {
        ping.value = Date.now() - lastPingTime
    })

    ws.on(MessageId.MATCH_SUCCESS_NOTIFY, (data: MatchSuccessNotify) => {
        console.log('匹配成功通知:', data)
        matchSuccessData.value = data
        battleId.value = data.battleId
    })

    ws.on(MessageId.BATTLE_STATE_NOTIFY, (data: BattleStateUpdate) => {
        if (data.frame > currentFrame) {
            currentFrame = data.frame
            frameBuffer.push(data)
        }
    })

    ws.on(MessageId.BATTLE_END_NOTIFY, (data: BattleEndNotify) => {
        console.log('战斗结束:', data)
    })

    ws.on(MessageId.BATTLE_COUNTDOWN_NOTIFY, (data: any) => {
        console.log('倒计时:', data.seconds)
    })

    ws.on(MessageId.BATTLE_START_NOTIFY, (data: any) => {
        console.log('战斗开始:', data)
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
            ws.off(MessageId.BATTLE_ENTER_RESP, handler)
            if (data.success) {
                battleId.value = bId
            }
            resolve(data)
        }

        ws.on(MessageId.BATTLE_ENTER_RESP, handler)
        ws.send(MessageId.BATTLE_ENTER_REQ, request)

        setTimeout(() => {
            ws.off(MessageId.BATTLE_ENTER_RESP, handler)
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
            ws.off(MessageId.RECONNECT_RESP, handler)
            resolve(data)
        }

        ws.on(MessageId.RECONNECT_RESP, handler)
        ws.send(MessageId.RECONNECT_REQ, request)

        setTimeout(() => {
            ws.off(MessageId.RECONNECT_RESP, handler)
            resolve({ success: false, battleState: '', errorMessage: '重连超时' })
        }, 10000)
    })
}

export function sendInput(input: { heroId: string; type: string; targetX?: number; targetY?: number; targetId?: string; skillId?: string; itemId?: string; frame: number }) {
    if (!isConnected.value) return
    ws.send(MessageId.BATTLE_ACTION_REQ, input)
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
    ws.send(MessageId.BATTLE_SKILL_CAST_REQ, {
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
