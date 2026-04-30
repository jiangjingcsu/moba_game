import type { Hero, PlayerInput, Vector2D } from '@/types/game'

const TICK_RATE = 15
const TICK_INTERVAL = 1000 / TICK_RATE
const INPUT_DELAY = 4

export class LockstepEngine {
  private frame: number = 0
  private inputBuffer: Map<number, Map<string, PlayerInput>> = new Map()
  private localInputs: PlayerInput[] = []
  private onTickCallback: ((frame: number, inputs: Map<string, PlayerInput>) => void) | null = null
  private isRunning = false
  private lastTickTime = 0
  private accumulator = 0

  start() {
    this.isRunning = true
    this.lastTickTime = performance.now()
    this.accumulator = 0
  }

  stop() {
    this.isRunning = false
  }

  tick(currentTime: number) {
    if (!this.isRunning) return

    const deltaTime = currentTime - this.lastTickTime
    this.lastTickTime = currentTime
    this.accumulator += deltaTime

    while (this.accumulator >= TICK_INTERVAL) {
      this.processFrame()
      this.accumulator -= TICK_INTERVAL
    }
  }

  private processFrame() {
    const targetFrame = this.frame + INPUT_DELAY
    
    const frameInputs = this.inputBuffer.get(this.frame) || new Map()
    
    if (this.onTickCallback) {
      this.onTickCallback(this.frame, frameInputs)
    }

    this.inputBuffer.delete(this.frame)
    this.frame++
  }

  addLocalInput(input: PlayerInput) {
    const frameNumber = this.frame + INPUT_DELAY
    
    if (!this.inputBuffer.has(frameNumber)) {
      this.inputBuffer.set(frameNumber, new Map())
    }
    
    this.inputBuffer.get(frameNumber)!.set(input.heroId, input)
    this.localInputs.push(input)
  }

  addRemoteInputs(frame: number, inputs: PlayerInput[]) {
    if (!this.inputBuffer.has(frame)) {
      this.inputBuffer.set(frame, new Map())
    }
    
    const frameMap = this.inputBuffer.get(frame)!
    inputs.forEach(input => {
      frameMap.set(input.heroId, input)
    })
  }

  getCurrentFrame(): number {
    return this.frame
  }

  getTickRate(): number {
    return TICK_RATE
  }

  setOnTickCallback(callback: (frame: number, inputs: Map<string, PlayerInput>) => void) {
    this.onTickCallback = callback
  }

  getStateHash(): string {
    return `frame:${this.frame},inputs:${this.localInputs.length}`
  }

  reset() {
    this.frame = 0
    this.inputBuffer.clear()
    this.localInputs = []
    this.accumulator = 0
  }
}

export const lockstep = new LockstepEngine()
