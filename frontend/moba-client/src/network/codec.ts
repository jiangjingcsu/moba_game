let sequenceId = 0

function nextSeq(): number {
    return ++sequenceId
}

export class MessageEncoder {
    static encode(messageId: number, data: any): string {
        const msg: Record<string, any> = {
            cmd: messageId,
            seq: nextSeq(),
            ver: 1,
            st: 1,
        }
        if (data !== undefined && data !== null) {
            msg.data = data
        }
        return JSON.stringify(msg)
    }
}

export class MessageDecoder {
    static decode(rawData: string): Array<{ messageId: number; data: any }> {
        const results: Array<{ messageId: number; data: any }> = []

        try {
            const msg = JSON.parse(rawData)
            results.push({
                messageId: msg.cmd || 0,
                data: msg.data || {},
            })
        } catch (e) {
            console.error('Failed to decode JSON message:', e)
        }

        return results
    }
}
