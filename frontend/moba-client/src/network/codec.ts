import type { MessageRoute } from './protocol'
import { routeKey } from './protocol'

const HEADER_SIZE = 11

let sequenceId = 0

export function nextSeq(): number {
    return ++sequenceId
}

export interface DecodedMessage {
    extId: number
    cmdId: number
    seq: number
    data: any
    routeKey: string
}

export class MessageEncoder {
    static encodeJson(r: MessageRoute, data: any, seq?: number): string {
        const msg: Record<string, any> = {
            extId: r.extId,
            cmdId: r.cmdId,
            seq: seq ?? nextSeq(),
        }
        if (data !== undefined && data !== null) {
            msg.data = data
        }
        return JSON.stringify(msg)
    }

    static encodeBinary(r: MessageRoute, data: any, seq?: number): ArrayBuffer {
        const jsonStr = (data !== undefined && data !== null) ? JSON.stringify(data) : ''
        const dataBytes = new TextEncoder().encode(jsonStr)
        const totalLength = HEADER_SIZE + dataBytes.byteLength
        const buffer = new ArrayBuffer(totalLength)
        const view = new DataView(buffer)

        view.setInt32(0, totalLength, false)
        view.setInt32(4, seq ?? nextSeq(), false)
        view.setUint16(8, r.extId, false)
        view.setUint8(10, r.cmdId)

        const resultArray = new Uint8Array(buffer)
        resultArray.set(dataBytes, HEADER_SIZE)

        return buffer
    }
}

export class MessageDecoder {
    static decodeJson(rawData: string): DecodedMessage | null {
        try {
            const msg = JSON.parse(rawData)
            const extId = msg.extId ?? 0
            const cmdId = msg.cmdId ?? 0
            return {
                extId,
                cmdId,
                seq: msg.seq ?? 0,
                data: msg.data ?? {},
                routeKey: routeKey(extId, cmdId),
            }
        } catch (e) {
            console.error('JSON解码失败:', e)
            return null
        }
    }

    static decodeBinary(buffer: ArrayBuffer): DecodedMessage | null {
        try {
            if (buffer.byteLength < HEADER_SIZE) {
                console.warn('二进制帧过短: %d字节, 至少需要%d字节', buffer.byteLength, HEADER_SIZE)
                return null
            }

            const view = new DataView(buffer)
            const totalLength = view.getInt32(0, false)
            if (totalLength < HEADER_SIZE || totalLength > buffer.byteLength) {
                console.warn('二进制帧totalLength异常: totalLength=%d, bufferLen=%d', totalLength, buffer.byteLength)
                return null
            }
            const seq = view.getInt32(4, false)
            const extId = view.getUint16(8, false)
            const cmdId = view.getUint8(10)

            const dataLength = totalLength - HEADER_SIZE
            let data: any = {}
            if (dataLength > 0 && buffer.byteLength >= totalLength) {
                const dataBytes = new Uint8Array(buffer, HEADER_SIZE, dataLength)
                const jsonStr = new TextDecoder().decode(dataBytes)
                if (jsonStr.length > 0) {
                    try {
                        data = JSON.parse(jsonStr)
                    } catch {
                        data = {}
                    }
                }
            } else if (dataLength > 0 && buffer.byteLength > HEADER_SIZE) {
                console.warn('二进制帧数据截断: expected=%d字节, actual=%d字节', dataLength, buffer.byteLength - HEADER_SIZE)
                const dataBytes = new Uint8Array(buffer, HEADER_SIZE)
                const jsonStr = new TextDecoder().decode(dataBytes)
                if (jsonStr.length > 0) {
                    try {
                        data = JSON.parse(jsonStr)
                    } catch {
                        data = {}
                    }
                }
            }

            return {
                extId,
                cmdId,
                seq,
                data,
                routeKey: routeKey(extId, cmdId),
            }
        } catch (e) {
            console.error('二进制解码失败:', e)
            return null
        }
    }

    static decode(rawData: string | ArrayBuffer): DecodedMessage | null {
        if (typeof rawData === 'string') {
            return MessageDecoder.decodeJson(rawData)
        }
        return MessageDecoder.decodeBinary(rawData)
    }
}
