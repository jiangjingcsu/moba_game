export enum MessageId {
    HEARTBEAT_REQ = (0x01 << 8) | (1 << 3) | 0,
    HEARTBEAT_RESP = (0x01 << 8) | (1 << 3) | 1,
    LOGIN_REQ = (0x02 << 8) | (1 << 3) | 0,
    LOGIN_RESP = (0x02 << 8) | (1 << 3) | 1,
    RECONNECT_REQ = (0x02 << 8) | (2 << 3) | 0,
    RECONNECT_RESP = (0x02 << 8) | (2 << 3) | 1,
    MATCH_JOIN_REQ = (0x03 << 8) | (1 << 3) | 0,
    MATCH_JOIN_RESP = (0x03 << 8) | (1 << 3) | 1,
    MATCH_STATUS_REQ = (0x03 << 8) | (2 << 3) | 0,
    MATCH_STATUS_RESP = (0x03 << 8) | (2 << 3) | 1,
    MATCH_CANCEL_REQ = (0x03 << 8) | (3 << 3) | 0,
    MATCH_CANCEL_RESP = (0x03 << 8) | (3 << 3) | 1,
    MATCH_SUCCESS_NOTIFY = (0x03 << 8) | (4 << 3) | 2,
    BATTLE_ENTER_REQ = (0x04 << 8) | (1 << 3) | 0,
    BATTLE_ENTER_RESP = (0x04 << 8) | (1 << 3) | 1,
    BATTLE_READY_REQ = (0x04 << 8) | (2 << 3) | 0,
    BATTLE_READY_RESP = (0x04 << 8) | (2 << 3) | 1,
    BATTLE_ACTION_REQ = (0x04 << 8) | (3 << 3) | 0,
    BATTLE_SKILL_CAST_REQ = (0x04 << 8) | (4 << 3) | 0,
    BATTLE_STATE_NOTIFY = (0x04 << 8) | (5 << 3) | 2,
    BATTLE_END_NOTIFY = (0x04 << 8) | (6 << 3) | 2,
    BATTLE_FRAME_SYNC_NOTIFY = (0x04 << 8) | (7 << 3) | 2,
    BATTLE_COUNTDOWN_NOTIFY = (0x04 << 8) | (8 << 3) | 2,
    BATTLE_START_NOTIFY = (0x04 << 8) | (9 << 3) | 2,
    BATTLE_EVENT_NOTIFY = (0x04 << 8) | (10 << 3) | 2,
    BATTLE_HASH_CHECK_NOTIFY = (0x04 << 8) | (11 << 3) | 2,
    BATTLE_STATE_CORRECTION_NOTIFY = (0x04 << 8) | (12 << 3) | 2,
    ROOM_CREATE_REQ = (0x05 << 8) | (1 << 3) | 0,
    ROOM_CREATE_RESP = (0x05 << 8) | (1 << 3) | 1,
    ROOM_JOIN_REQ = (0x05 << 8) | (2 << 3) | 0,
    ROOM_JOIN_RESP = (0x05 << 8) | (2 << 3) | 1,
    ROOM_LEAVE_REQ = (0x05 << 8) | (3 << 3) | 0,
    ROOM_STATE_NOTIFY = (0x05 << 8) | (4 << 3) | 2,
    CHAT_REQ = (0x06 << 8) | (1 << 3) | 0,
    CHAT_NOTIFY = (0x06 << 8) | (1 << 3) | 2,
    EMOTE_REQ = (0x06 << 8) | (2 << 3) | 0,
    EMOTE_NOTIFY = (0x06 << 8) | (2 << 3) | 2,
    SPECTATOR_JOIN_REQ = (0x07 << 8) | (1 << 3) | 0,
    SPECTATOR_JOIN_RESP = (0x07 << 8) | (1 << 3) | 1,
    SPECTATOR_LEAVE_REQ = (0x07 << 8) | (2 << 3) | 0,
}

export const MatchStatus = {
    WAITING: 0,
    SUCCESS: 1,
    CANCELLED: 2,
    TIMEOUT: 3,
} as const

export interface LoginResponse {
    success: boolean
    playerId: number
    playerName: string
    rank: number
    rankScore: number
    errorMessage: string
}

export interface MatchResponse {
    success: boolean
    matchId: string
    gameMode: number
    currentPlayers: number
    neededPlayers: number
    state: string
    errorCode: string
    errorMessage: string
}

export interface MatchStatusResponse {
    found: boolean
    matchId?: string
    battleId?: string
    battleServerIp?: string
    battleServerPort?: number
    gameMode?: number
    state?: string
}

export interface MatchSuccessNotify {
    matchId: string
    battleId: string
    battleServerIp: string
    battleServerPort: number
    gameMode: number
    playerIds: number[]
}

export interface BattleEnterResponse {
    success: boolean
    battleId: string
    mapId: number
    mapConfig: string
    errorMessage: string
}

export interface ReconnectResponse {
    success: boolean
    battleState: string
    errorMessage: string
}

export interface BattleStateUpdate {
    frame: number
    gameTime: number
    heroes: any[]
    towers: any[]
    minions: any[]
    monsters: any[]
}

export interface BattleEndNotify {
    winnerTeamId: number
    battleId: string
    duration: number
    stats: any
}
