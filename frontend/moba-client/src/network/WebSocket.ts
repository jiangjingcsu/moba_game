import { ref } from 'vue'
import { MessageEncoder, MessageDecoder } from './codec'
import { MessageId } from './protocol'

export enum ConnectionState {
  DISCONNECTED = 'disconnected',
  CONNECTING = 'connecting',
  CONNECTED = 'connected',
  RECONNECTING = 'reconnecting'
}

type MessageCallback = (data: any) => void

export class GameWebSocket {
  private ws: WebSocket | null = null
  private url: string
  private reconnectAttempts = 0
  private maxReconnectAttempts = 5
  private reconnectDelay = 1000

  public state = ref<ConnectionState>(ConnectionState.DISCONNECTED)
  public error = ref<string>('')

  private messageCallbacks: Map<number, MessageCallback[]> = new Map()
  private messageQueue: Array<{ messageId: number; data: any }> = []

  constructor(url: string) {
    this.url = url
  }

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        this.state.value = ConnectionState.CONNECTING
        MessageDecoder.resetBuffer()
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

  send(messageId: number, data: any = {}) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      const buffer = MessageEncoder.encode(messageId, data)
      this.ws.send(buffer)
    } else {
      this.messageQueue.push({ messageId, data })
    }
  }

  on(messageId: number, callback: MessageCallback) {
    if (!this.messageCallbacks.has(messageId)) {
      this.messageCallbacks.set(messageId, [])
    }
    this.messageCallbacks.get(messageId)!.push(callback)
  }

  off(messageId: number, callback: MessageCallback) {
    const callbacks = this.messageCallbacks.get(messageId)
    if (callbacks) {
      const index = callbacks.indexOf(callback)
      if (index !== -1) {
        callbacks.splice(index, 1)
      }
      if (callbacks.length === 0) {
        this.messageCallbacks.delete(messageId)
      }
    }
  }

  private handleMessage(rawData: ArrayBuffer) {
    try {
      const messages = MessageDecoder.decode(rawData)
      for (const msg of messages) {
        const callbacks = this.messageCallbacks.get(msg.messageId)
        if (callbacks) {
          for (const cb of callbacks) {
            cb(msg.data)
          }
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
        this.send(msg.messageId, msg.data)
      }
    }
  }

  disconnect() {
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    this.state.value = ConnectionState.DISCONNECTED
    this.messageCallbacks.clear()
    this.messageQueue = []
    MessageDecoder.resetBuffer()
  }
}
