export enum MessageId {
  HEARTBEAT_REQUEST = 0x0001,
  HEARTBEAT_RESPONSE = 0x0002,
  LOGIN_REQUEST = 0x0010,
  LOGIN_RESPONSE = 0x0011,
  MATCH_REQUEST = 0x0020,
  MATCH_RESPONSE = 0x0021,
  MATCH_CANCEL_REQUEST = 0x0022,
  BATTLE_ENTER_REQUEST = 0x0030,
  BATTLE_ENTER_RESPONSE = 0x0031,
  PLAYER_ACTION_REQUEST = 0x0040,
  BATTLE_STATE_UPDATE = 0x0041,
  SKILL_CAST_REQUEST = 0x0042,
  BATTLE_END_NOTIFY = 0x0050,
  RECONNECT_REQUEST = 0x0060,
  RECONNECT_RESPONSE = 0x0061,
}

export interface LoginRequest {
  playerName: string
  clientVersion: number
  platform: string
}

export interface LoginResponse {
  success: boolean
  playerId: number
  playerName: string
  rank: number
  rankScore: number
  errorMessage: string
}

export interface MatchRequest {
  playerId: number
  matchType: number
  preferredRole: number
}

export interface MatchResponse {
  success: boolean
  matchStatus: number
  waitTime: number
  battleId: string
  errorMessage: string
}

export const MatchStatus = {
  WAITING: 0,
  SUCCESS: 1,
  CANCELLED: 2,
  TIMEOUT: 3,
} as const

export interface BattleEnterRequest {
  playerId: number
  battleId: string
  heroId: number
  teamId: number
}

export interface BattleEnterResponse {
  success: boolean
  battleId: string
  mapId: number
  mapConfig: string
  errorMessage: string
}

export interface ReconnectRequest {
  playerId: number
  battleId: string
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
