import { MessageId } from './protocol'

export class MessageEncoder {
  static encode(messageId: number, data: any): ArrayBuffer {
    const bodyStr = this.serializeBody(messageId, data)
    const bodyBytes = new TextEncoder().encode(bodyStr)

    const totalLength = 4 + bodyBytes.byteLength
    const buffer = new ArrayBuffer(4 + totalLength)
    const view = new DataView(buffer)

    view.setInt32(0, totalLength, false)
    view.setInt32(4, messageId, false)
    new Uint8Array(buffer, 8).set(bodyBytes)

    return buffer
  }

  private static serializeBody(messageId: number, data: any): string {
    switch (messageId) {
      case MessageId.LOGIN_REQUEST:
        return `${data.playerName || ''}|${data.clientVersion || 0}|${data.platform || ''}`

      case MessageId.MATCH_REQUEST:
        return `${data.playerId || 0}|${data.matchType || 0}|${data.preferredRole || 0}`

      case MessageId.MATCH_CANCEL_REQUEST:
        return `${data.playerId || 0}`

      case MessageId.BATTLE_ENTER_REQUEST:
        return `${data.playerId || 0}|${data.battleId || ''}|${data.heroId || 0}|${data.teamId || 0}`

      case MessageId.RECONNECT_REQUEST:
        return `${data.playerId || 0}|${data.battleId || ''}`

      case MessageId.PLAYER_ACTION_REQUEST:
        return `${data.heroId || ''}|${data.type || 'move'}|${data.targetX || 0}|${data.targetY || 0}|${data.targetId || ''}|${data.skillId || ''}|${data.itemId || ''}|${data.frame || 0}`

      case MessageId.SKILL_CAST_REQUEST:
        return `${data.heroId || ''}|${data.skillId || ''}|${data.targetX || 0}|${data.targetY || 0}|${data.frame || 0}`

      case MessageId.HEARTBEAT_RESPONSE:
        return ''

      default:
        return JSON.stringify(data) || ''
    }
  }
}

export class MessageDecoder {
  private static buffer: ArrayBuffer | null = null
  private static bufferOffset: number = 0

  static decode(rawData: ArrayBuffer): Array<{ messageId: number; data: any }> {
    const results: Array<{ messageId: number; data: any }> = []

    if (this.buffer) {
      const merged = new Uint8Array(this.buffer.byteLength + rawData.byteLength)
      merged.set(new Uint8Array(this.buffer), 0)
      merged.set(new Uint8Array(rawData), this.buffer.byteLength)
      rawData = merged.buffer
      this.buffer = null
    }

    let offset = 0
    const bytes = new Uint8Array(rawData)

    while (offset < bytes.byteLength) {
      if (offset + 4 > bytes.byteLength) {
        this.saveRemaining(bytes, offset)
        break
      }

      const view = new DataView(rawData, offset)
      const totalLength = view.getInt32(0, false)

      if (totalLength < 4 || offset + 4 + totalLength > bytes.byteLength) {
        this.saveRemaining(bytes, offset)
        break
      }

      const messageId = view.getInt32(4, false)
      const bodyLength = totalLength - 4
      const bodyBytes = new Uint8Array(rawData, offset + 8, bodyLength)
      const bodyStr = new TextDecoder().decode(bodyBytes)

      results.push({
        messageId,
        data: this.parseBody(messageId, bodyStr),
      })

      offset += 4 + totalLength
    }

    return results
  }

  private static saveRemaining(bytes: Uint8Array, offset: number) {
    const remaining = bytes.byteLength - offset
    if (remaining > 0) {
      this.buffer = new ArrayBuffer(remaining)
      new Uint8Array(this.buffer).set(bytes.slice(offset))
      this.bufferOffset = 0
    }
  }

  static resetBuffer() {
    this.buffer = null
    this.bufferOffset = 0
  }

  private static parseBody(messageId: number, bodyStr: string): any {
    if (!bodyStr || bodyStr.length === 0) {
      return {}
    }

    const parts = bodyStr.split('|')

    switch (messageId) {
      case MessageId.LOGIN_RESPONSE:
        return {
          success: parts[0] === 'true',
          playerId: parseInt(parts[1]) || 0,
          playerName: parts[2] || '',
          rank: parseInt(parts[3]) || 0,
          rankScore: parseInt(parts[4]) || 0,
          errorMessage: parts[5] || '',
        }

      case MessageId.MATCH_RESPONSE:
        return {
          success: parts[0] === 'true',
          matchStatus: parseInt(parts[1]) || 0,
          waitTime: parseInt(parts[2]) || 0,
          battleId: parts[3] || '',
          errorMessage: parts[4] || '',
        }

      case MessageId.BATTLE_ENTER_RESPONSE:
        return {
          success: parts[0] === 'true',
          battleId: parts[1] || '',
          mapId: parseInt(parts[2]) || 0,
          mapConfig: parts[3] || '',
          errorMessage: parts[4] || '',
        }

      case MessageId.BATTLE_STATE_UPDATE:
        try {
          return JSON.parse(bodyStr)
        } catch {
          return { frame: 0, gameTime: 0, heroes: [], towers: [], minions: [], monsters: [] }
        }

      case MessageId.BATTLE_END_NOTIFY:
        try {
          return JSON.parse(bodyStr)
        } catch {
          return { winnerTeamId: 0, battleId: '', duration: 0, stats: {} }
        }

      case MessageId.RECONNECT_RESPONSE:
        return {
          success: parts[0] === 'true',
          battleState: parts[1] || '',
          errorMessage: parts[2] || '',
        }

      case MessageId.HEARTBEAT_REQUEST:
        return {}

      case MessageId.HEARTBEAT_RESPONSE:
        return {}

      default:
        try {
          return JSON.parse(bodyStr)
        } catch {
          return {}
        }
    }
  }
}
