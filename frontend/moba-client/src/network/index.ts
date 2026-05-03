import { GameWebSocket, ConnectionState, setDebugLoggers } from './WebSocket'
import { MessageRoute, BattleEnterResponse, BattleStateUpdate, BattleEndNotify, ReconnectResponse, MatchSuccessNotify, MatchProgressNotify } from './protocol'
import { ref } from 'vue'
import { useGameStore } from '@/stores/game'
import { useDebugStore } from '@/stores/debug'

const API_BASE = 'http://localhost:8080/api'
const MATCH_WS_URL = 'ws://localhost:9998/ws/match'
const DEFAULT_TIMEOUT_MS = 10000

export const matchWs = new GameWebSocket(MATCH_WS_URL, false, 'match')

let debugLoggersInitialized = false

function ensureDebugLoggers() {
    if (debugLoggersInitialized) return
    debugLoggersInitialized = true
    const debugStore = useDebugStore()
    setDebugLoggers(
        (source, extId, cmdId, seq, data) => debugStore.logSend(source, extId, cmdId, seq, data),
        (source, extId, cmdId, seq, data) => debugStore.logRecv(source, extId, cmdId, seq, data),
    )
}

export function initNetwork() {
    ensureDebugLoggers()
}
export const isSynced = ref(false)
export const ping = ref(0)
export const isConnected = ref(false)
export const battleuserId = ref<number>(0)
export const battleId = ref<number>(0)
export const matchSuccessData = ref<MatchSuccessNotify | null>(null)

export const matchProgress = ref<MatchProgressNotify | null>(null)

let battleWs: GameWebSocket | null = null

export interface PlayerInfo {
    userId: number
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

function syncPlayerFromStore() {
    const gameStore = useGameStore()
    const storePlayer = gameStore.playerInfo
    if (storePlayer && (!currentPlayer || currentPlayer.userId !== storePlayer.userId)) {
        currentPlayer = {
            userId: storePlayer.userId,
            playerName: storePlayer.playerName,
            nickname: storePlayer.nickname,
            rank: storePlayer.rank,
            rankScore: storePlayer.rankScore || 1000,
            level: storePlayer.level,
            avatar: storePlayer.avatar,
            gold: storePlayer.gold,
            diamond: storePlayer.diamond,
            isSignedIn: storePlayer.isSignedIn,
            signInDays: storePlayer.signInDays,
        }
    }
    const storeToken = localStorage.getItem('moba_auth_token')
    if (storeToken && !authToken) {
        authToken = storeToken
    }
}

function getMatchWsUrl(): string {
    syncPlayerFromStore()
    if (authToken) {
        return `${MATCH_WS_URL}?token=${authToken}`
    }
    return MATCH_WS_URL
}

function buildBattleWsUrl(ip: string, port: number): string {
    syncPlayerFromStore()
    const baseUrl = `ws://${ip}:${port}/ws/game`
    if (authToken) {
        return `${baseUrl}?token=${authToken}`
    }
    return baseUrl
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
                userId: result.data.userId,
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
            localStorage.setItem('moba_auth_token', authToken!)
            currentPlayer = {
                userId: result.data.userId,
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

export async function connectToMatchService(): Promise<boolean> {
    try {
        if (matchWs.state.value === ConnectionState.CONNECTED) {
            return true
        }

        matchWs.updateUrl(getMatchWsUrl())
        await matchWs.connect()
        setupMatchMessageHandlers()

        return true
    } catch (e) {
        console.error('Failed to connect to match service:', e)
        return false
    }
}

export async function connectToBattleServer(ip: string, port: number): Promise<boolean> {
    try {
        if (battleWs && battleWs.state.value === ConnectionState.CONNECTED) {
            return true
        }

        disconnectBattleServer()

        const url = buildBattleWsUrl(ip, port)
        battleWs = new GameWebSocket(url, false, 'battle')
        await battleWs.connect()
        isConnected.value = true
        setupBattleMessageHandlers()

        return true
    } catch (e) {
        console.error('Failed to connect to battle server:', e)
        isConnected.value = false
        return false
    }
}

export function disconnectBattleServer() {
    if (battleWs) {
        battleWs.disconnect()
        battleWs = null
    }
    isConnected.value = false
    frameBuffer = []
    currentFrame = 0
}

export function joinMatch(gameMode: number = 3): Promise<{ success: boolean; battleId?: number; error?: string }> {
    return new Promise((resolve) => {
        syncPlayerFromStore()
        let resolved = false
        let timer: ReturnType<typeof setTimeout> | null = null

        const handler = (data: any) => {
            matchWs.off(MessageRoute.MATCH_JOIN, handler)
            if (timer) { clearTimeout(timer); timer = null }
            if (resolved) return
            resolved = true
            if (data.success) {
                battleuserId.value = currentPlayer?.userId || 0
                resolve({ success: true, battleId: data.battleId })
            } else {
                resolve({ success: false, error: data.errorMessage || data.errorCode || '加入匹配失败' })
            }
        }

        matchWs.on(MessageRoute.MATCH_JOIN, handler)
        matchWs.send(MessageRoute.MATCH_JOIN, {
            nickname: currentPlayer?.nickname || '',
            rankScore: currentPlayer?.rankScore || 1000,
            gameMode,
        })

        timer = setTimeout(() => {
            matchWs.off(MessageRoute.MATCH_JOIN, handler)
            if (resolved) return
            resolved = true
            resolve({ success: false, error: '加入匹配超时' })
        }, DEFAULT_TIMEOUT_MS)
    })
}

export function cancelMatch(): Promise<{ success: boolean; error?: string }> {
    return new Promise((resolve) => {
        syncPlayerFromStore()
        let resolved = false
        let timer: ReturnType<typeof setTimeout> | null = null

        const handler = (data: any) => {
            matchWs.off(MessageRoute.MATCH_CANCEL, handler)
            if (timer) { clearTimeout(timer); timer = null }
            if (resolved) return
            resolved = true
            resolve({ success: data.success !== false })
        }

        matchWs.on(MessageRoute.MATCH_CANCEL, handler)
        matchWs.send(MessageRoute.MATCH_CANCEL, {})

        timer = setTimeout(() => {
            matchWs.off(MessageRoute.MATCH_CANCEL, handler)
            if (resolved) return
            resolved = true
            resolve({ success: false, error: '取消匹配超时' })
        }, DEFAULT_TIMEOUT_MS)
    })
}

function setupBattleMessageHandlers() {
    if (!battleWs) return

    battleWs.on(MessageRoute.HEARTBEAT, () => {
        ping.value = Date.now() - lastPingTime
        lastPingTime = Date.now()
    })

    battleWs.on(MessageRoute.BATTLE_STATE_NOTIFY, (data: BattleStateUpdate) => {
        if (data.frame > currentFrame) {
            currentFrame = data.frame
            frameBuffer.push(data)
        }
    })

    battleWs.on(MessageRoute.BATTLE_END_NOTIFY, (data: BattleEndNotify) => {
        console.log('战斗结束:', data)
    })

    battleWs.on(MessageRoute.BATTLE_COUNTDOWN_NOTIFY, (data: any) => {
        console.log('倒计时:', data.seconds)
    })

    battleWs.on(MessageRoute.BATTLE_START_NOTIFY, (data: any) => {
        console.log('战斗开始:', data)
    })
}

function setupMatchMessageHandlers() {
    matchWs.on(MessageRoute.HEARTBEAT, () => {
        ping.value = Date.now() - lastPingTime
        lastPingTime = Date.now()
    })

    matchWs.on(MessageRoute.MATCH_SUCCESS_NOTIFY, (data: MatchSuccessNotify) => {
        console.log('匹配成功通知:', data)
        matchSuccessData.value = data
        battleId.value = data.battleId
    })

    matchWs.on(MessageRoute.MATCH_PROGRESS_NOTIFY, (data: MatchProgressNotify) => {
        matchProgress.value = data
    })
}

export function enterBattle(bId: number, heroId: number, teamId: number): Promise<BattleEnterResponse> {
    return new Promise((resolve) => {
        if (!battleWs || battleWs.state.value !== ConnectionState.CONNECTED) {
            resolve({ success: false, battleId: 0, mapId: 0, mapConfig: '', errorMessage: '战斗服务器未连接' })
            return
        }

        let resolved = false
        let timer: ReturnType<typeof setTimeout> | null = null

        const request = {
            battleId: bId,
            heroId,
            teamId,
        }

        const handler = (data: BattleEnterResponse) => {
            battleWs?.off(MessageRoute.BATTLE_ENTER, handler)
            if (timer) { clearTimeout(timer); timer = null }
            if (resolved) return
            resolved = true
            if (data.success) {
                battleId.value = bId
            }
            resolve(data)
        }

        battleWs.on(MessageRoute.BATTLE_ENTER, handler)
        battleWs.send(MessageRoute.BATTLE_ENTER, request)

        timer = setTimeout(() => {
            battleWs?.off(MessageRoute.BATTLE_ENTER, handler)
            if (resolved) return
            resolved = true
            resolve({ success: false, battleId: 0, mapId: 0, mapConfig: '', errorMessage: '进入战斗超时' })
        }, DEFAULT_TIMEOUT_MS)
    })
}

export function reconnect(bId: number): Promise<ReconnectResponse> {
    return new Promise((resolve) => {
        if (!battleWs || battleWs.state.value !== ConnectionState.CONNECTED) {
            resolve({ success: false, battleState: '', errorMessage: '战斗服务器未连接' })
            return
        }

        let resolved = false
        let timer: ReturnType<typeof setTimeout> | null = null

        const request = {
            battleId: bId,
        }

        const handler = (data: ReconnectResponse) => {
            battleWs?.off(MessageRoute.BATTLE_RECONNECT, handler)
            if (timer) { clearTimeout(timer); timer = null }
            if (resolved) return
            resolved = true
            resolve(data)
        }

        battleWs.on(MessageRoute.BATTLE_RECONNECT, handler)
        battleWs.send(MessageRoute.BATTLE_RECONNECT, request)

        timer = setTimeout(() => {
            battleWs?.off(MessageRoute.BATTLE_RECONNECT, handler)
            if (resolved) return
            resolved = true
            resolve({ success: false, battleState: '', errorMessage: '重连超时' })
        }, DEFAULT_TIMEOUT_MS)
    })
}

export function sendInput(input: { heroId: string; type: string; targetX?: number; targetY?: number; targetId?: string; skillId?: string; itemId?: string; frame: number }) {
    if (!isConnected.value || !battleWs) return
    battleWs.send(MessageRoute.BATTLE_ACTION, input)
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
    if (!isConnected.value || !battleWs) return
    battleWs.send(MessageRoute.BATTLE_SKILL_CAST, {
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
    disconnectBattleServer()
    matchWs.disconnect()
    currentPlayer = null
    authToken = null
    isSynced.value = false
    battleuserId.value = 0
    battleId.value = 0
    matchSuccessData.value = null
}
