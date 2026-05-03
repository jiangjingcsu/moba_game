import { ref } from 'vue'
import { MessageEncoder, MessageDecoder, type DecodedMessage, nextSeq } from './codec'
import type { MessageRoute } from './protocol'
import { routeKey } from './protocol'
import { isDebugEnabled } from '@/config/debug'

export enum ConnectionState {
    DISCONNECTED = 'disconnected',
    CONNECTING = 'connecting',
    CONNECTED = 'connected',
    RECONNECTING = 'reconnecting'
}

type MessageCallback = (data: any, msg: DecodedMessage) => void

type DebugLogFn = (source: string, extId: number, cmdId: number, seq: number, data: any) => void

let debugLogSend: DebugLogFn | null = null
let debugLogRecv: DebugLogFn | null = null

export function setDebugLoggers(sendLogger: DebugLogFn, recvLogger: DebugLogFn) {
    debugLogSend = sendLogger
    debugLogRecv = recvLogger
}

export class GameWebSocket {
    private ws: WebSocket | null = null
    private url: string
    private reconnectAttempts = 0
    private maxReconnectAttempts = 5
    private reconnectDelay = 1000
    private useBinary: boolean
    private label: string

    public state = ref<ConnectionState>(ConnectionState.DISCONNECTED)
    public error = ref<string>('')

    private messageCallbacks: Map<string, MessageCallback[]> = new Map()
    private messageQueue: Array<{ route: MessageRoute; data: any }> = []

    constructor(url: string, useBinary = false, label = 'ws') {
        this.url = url
        this.useBinary = useBinary
        this.label = label
    }

    connect(): Promise<void> {
        return new Promise((resolve, reject) => {
            try {
                this.state.value = ConnectionState.CONNECTING
                this.ws = new WebSocket(this.url)
                this.ws.binaryType = 'arraybuffer'

                this.ws.onopen = () => {
                    this.state.value = ConnectionState.CONNECTED
                    this.reconnectAttempts = 0
                    this.flushMessageQueue()
                    resolve()
                }

                this.ws.onmessage = (event) => {
                    this.handleMessage(event.data)
                }

                this.ws.onclose = () => {
                    this.state.value = ConnectionState.DISCONNECTED
                    this.handleReconnect()
                }

                this.ws.onerror = () => {
                    this.error.value = 'WebSocket连接错误'
                    reject(new Error('WebSocket连接错误'))
                }
            } catch (e) {
                this.error.value = 'WebSocket连接失败'
                reject(e)
            }
        })
    }

    send(route: MessageRoute, data: any = {}) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            const seq = nextSeq()
            if (this.useBinary) {
                const buffer = MessageEncoder.encodeBinary(route, data, seq)
                this.ws.send(buffer)
            } else {
                const json = MessageEncoder.encodeJson(route, data, seq)
                this.ws.send(json)
            }
            if (isDebugEnabled.value && debugLogSend) {
                debugLogSend(this.label, route.extId, route.cmdId, seq, data)
            }
        } else {
            this.messageQueue.push({ route, data })
        }
    }

    on(route: MessageRoute, callback: MessageCallback) {
        const key = routeKey(route.extId, route.cmdId)
        if (!this.messageCallbacks.has(key)) {
            this.messageCallbacks.set(key, [])
        }
        this.messageCallbacks.get(key)!.push(callback)
    }

    off(route: MessageRoute, callback: MessageCallback) {
        const key = routeKey(route.extId, route.cmdId)
        const callbacks = this.messageCallbacks.get(key)
        if (callbacks) {
            const index = callbacks.indexOf(callback)
            if (index !== -1) {
                callbacks.splice(index, 1)
            }
            if (callbacks.length === 0) {
                this.messageCallbacks.delete(key)
            }
        }
    }

    private handleMessage(rawData: string | ArrayBuffer) {
        try {
            const msg = MessageDecoder.decode(rawData)
            if (!msg) return

            if (isDebugEnabled.value && debugLogRecv) {
                debugLogRecv(this.label, msg.extId, msg.cmdId, msg.seq, msg.data)
            }

            const key = msg.routeKey
            const callbacks = this.messageCallbacks.get(key)
            if (callbacks) {
                for (const cb of callbacks) {
                    cb(msg.data, msg)
                }
            }
        } catch (e) {
            console.error('解析消息失败:', e)
        }
    }

    private handleReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.state.value = ConnectionState.RECONNECTING
            this.reconnectAttempts++

            setTimeout(() => {
                this.connect().catch(() => {})
            }, this.reconnectDelay * this.reconnectAttempts)
        }
    }

    private flushMessageQueue() {
        while (this.messageQueue.length > 0 && this.ws?.readyState === WebSocket.OPEN) {
            const msg = this.messageQueue.shift()
            if (msg) {
                this.send(msg.route, msg.data)
            }
        }
    }

    updateUrl(newUrl: string) {
        if (this.state.value !== ConnectionState.DISCONNECTED) {
            return
        }
        this.url = newUrl
    }

    disconnect() {
        if (this.ws) {
            this.ws.close()
            this.ws = null
        }
        this.state.value = ConnectionState.DISCONNECTED
        this.messageCallbacks.clear()
        this.messageQueue = []
    }
}
