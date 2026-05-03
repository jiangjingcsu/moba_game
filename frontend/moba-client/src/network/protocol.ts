export enum ExtensionId {
    SYSTEM = 0x01,
    AUTH = 0x02,
    MATCH = 0x03,
    BATTLE = 0x04,
    ROOM = 0x05,
    SOCIAL = 0x06,
    SPECTATOR = 0x07,
}

export enum SystemCmdId {
    HEARTBEAT = 1,
}

export enum AuthCmdId {
    LOGIN = 1,
    RECONNECT = 2,
}

export enum MatchCmdId {
    JOIN = 1,
    STATUS = 2,
    CANCEL = 3,
    SUCCESS_NOTIFY = 4,
    PROGRESS_NOTIFY = 5,
}

export enum BattleCmdId {
    ENTER = 1,
    READY = 2,
    ACTION = 3,
    SKILL_CAST = 4,
    RECONNECT = 5,
    STATE_NOTIFY = 6,
    END_NOTIFY = 7,
    FRAME_SYNC = 8,
    COUNTDOWN_NOTIFY = 9,
    START_NOTIFY = 10,
    EVENT_NOTIFY = 11,
    HASH_CHECK = 12,
    STATE_CORRECTION = 13,
}

export enum RoomCmdId {
    CREATE = 1,
    JOIN = 2,
    LEAVE = 3,
    STATE_NOTIFY = 4,
}

export enum SocialCmdId {
    CHAT = 1,
    EMOTE = 2,
}

export enum SpectatorCmdId {
    JOIN = 1,
    LEAVE = 2,
}

export interface MessageRoute {
    extId: number
    cmdId: number
}

export function route(extId: ExtensionId, cmdId: number): MessageRoute {
    return { extId, cmdId }
}

export function routeKey(extId: number, cmdId: number): string {
    return `${extId}:${cmdId}`
}

export const MessageRoute = {
    HEARTBEAT: route(ExtensionId.SYSTEM, SystemCmdId.HEARTBEAT),
    LOGIN: route(ExtensionId.AUTH, AuthCmdId.LOGIN),
    RECONNECT: route(ExtensionId.AUTH, AuthCmdId.RECONNECT),
    MATCH_JOIN: route(ExtensionId.MATCH, MatchCmdId.JOIN),
    MATCH_STATUS: route(ExtensionId.MATCH, MatchCmdId.STATUS),
    MATCH_CANCEL: route(ExtensionId.MATCH, MatchCmdId.CANCEL),
    MATCH_SUCCESS_NOTIFY: route(ExtensionId.MATCH, MatchCmdId.SUCCESS_NOTIFY),
    MATCH_PROGRESS_NOTIFY: route(ExtensionId.MATCH, MatchCmdId.PROGRESS_NOTIFY),
    BATTLE_ENTER: route(ExtensionId.BATTLE, BattleCmdId.ENTER),
    BATTLE_READY: route(ExtensionId.BATTLE, BattleCmdId.READY),
    BATTLE_ACTION: route(ExtensionId.BATTLE, BattleCmdId.ACTION),
    BATTLE_SKILL_CAST: route(ExtensionId.BATTLE, BattleCmdId.SKILL_CAST),
    BATTLE_RECONNECT: route(ExtensionId.BATTLE, BattleCmdId.RECONNECT),
    BATTLE_STATE_NOTIFY: route(ExtensionId.BATTLE, BattleCmdId.STATE_NOTIFY),
    BATTLE_END_NOTIFY: route(ExtensionId.BATTLE, BattleCmdId.END_NOTIFY),
    BATTLE_FRAME_SYNC: route(ExtensionId.BATTLE, BattleCmdId.FRAME_SYNC),
    BATTLE_COUNTDOWN_NOTIFY: route(ExtensionId.BATTLE, BattleCmdId.COUNTDOWN_NOTIFY),
    BATTLE_START_NOTIFY: route(ExtensionId.BATTLE, BattleCmdId.START_NOTIFY),
    BATTLE_EVENT_NOTIFY: route(ExtensionId.BATTLE, BattleCmdId.EVENT_NOTIFY),
    BATTLE_HASH_CHECK: route(ExtensionId.BATTLE, BattleCmdId.HASH_CHECK),
    BATTLE_STATE_CORRECTION: route(ExtensionId.BATTLE, BattleCmdId.STATE_CORRECTION),
    ROOM_CREATE: route(ExtensionId.ROOM, RoomCmdId.CREATE),
    ROOM_JOIN: route(ExtensionId.ROOM, RoomCmdId.JOIN),
    ROOM_LEAVE: route(ExtensionId.ROOM, RoomCmdId.LEAVE),
    ROOM_STATE_NOTIFY: route(ExtensionId.ROOM, RoomCmdId.STATE_NOTIFY),
    CHAT: route(ExtensionId.SOCIAL, SocialCmdId.CHAT),
    EMOTE: route(ExtensionId.SOCIAL, SocialCmdId.EMOTE),
    SPECTATOR_JOIN: route(ExtensionId.SPECTATOR, SpectatorCmdId.JOIN),
    SPECTATOR_LEAVE: route(ExtensionId.SPECTATOR, SpectatorCmdId.LEAVE),
} as const

export const MessageId = MessageRoute

export const MatchStatus = {
    WAITING: 0,
    SUCCESS: 1,
    CANCELLED: 2,
    TIMEOUT: 3,
} as const

export interface LoginResponse {
    success: boolean
    userId: number
    playerName: string
    rank: number
    rankScore: number
    errorMessage: string
}

export interface MatchResponse {
    success: boolean
    battleId?: number
    gameMode: number
    currentPlayers: number
    neededPlayers: number
    state: string
    errorCode: string
    errorMessage: string
}

export interface MatchStatusResponse {
    found: boolean
    battleId?: number
    battleServerIp?: string
    battleServerPort?: number
    gameMode?: number
    state?: string
}

export interface MatchSuccessNotify {
    matchId: number
    battleId: number
    gameMode: number
    teamCount: number
    userIds: number[]
    battleServerIp: string
    battleServerPort: number
    aiMode: boolean
    aiLevel: number
    matchTime: number
}

export interface MatchProgressNotify {
    matchId: number
    currentPlayers: number
    neededPlayers: number
    gameMode: number
}

export interface BattleEnterResponse {
    success: boolean
    battleId: number
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
    battleId: number
    duration: number
    stats: any
}
