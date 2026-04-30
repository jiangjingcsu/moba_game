import type { Vector2D, PlayerInput } from '@/types/game'

export class InputHandler {
  private mousePosition: Vector2D = { x: 0, y: 0 }
  private keyStates: Map<string, boolean> = new Map()
  private canvas: HTMLCanvasElement | null = null
  
  private onMoveCallback: ((pos: Vector2D) => void) | null = null
  private onSkillCallback: ((input: PlayerInput) => void) | null = null
  private onAbilityCallback: ((ability: string) => void) | null = null

  setup(canvas: HTMLCanvasElement) {
    this.canvas = canvas

    canvas.style.pointerEvents = 'auto'
    canvas.style.cursor = 'crosshair'

    canvas.addEventListener('contextmenu', (e) => e.preventDefault())
    
    canvas.addEventListener('mousedown', (e) => {
      if (e.button === 2) {
        e.preventDefault()
        const screenPos = { x: e.clientX, y: e.clientY }
        if (this.onMoveCallback) {
          this.onMoveCallback(screenPos)
        }
      }
    })

    canvas.addEventListener('mousemove', (e) => {
      this.mousePosition = { x: e.clientX, y: e.clientY }
    })

    window.addEventListener('keydown', (e) => {
      if (e.target instanceof HTMLInputElement) return
      
      const key = e.key.toLowerCase()
      this.keyStates.set(key, true)
      
      if (key === ' ') {
        e.preventDefault()
      }
      
      switch (key) {
        case 'q':
        case 'w':
        case 'e':
        case 'r':
          this.castSkill(key.toUpperCase())
          break
        case ' ':
          if (this.onAbilityCallback) {
            this.onAbilityCallback('center_camera')
          }
          break
      }
    })

    window.addEventListener('keyup', (e) => {
      const key = e.key.toLowerCase()
      this.keyStates.set(key, false)
    })

    canvas.addEventListener('wheel', (e) => {
      e.preventDefault()
    }, { passive: false })
  }

  private castSkill(skillKey: string) {
    if (this.onSkillCallback) {
      this.onSkillCallback({
        heroId: '',
        type: 'skill',
        skillId: skillKey.toLowerCase(),
        frame: 0
      })
    }
  }

  setMoveCallback(callback: (pos: Vector2D) => void) {
    this.onMoveCallback = callback
  }

  setSkillCallback(callback: (input: PlayerInput) => void) {
    this.onSkillCallback = callback
  }

  setAbilityCallback(callback: (ability: string) => void) {
    this.onAbilityCallback = callback
  }

  getMousePosition(): Vector2D {
    return { ...this.mousePosition }
  }

  isKeyPressed(key: string): boolean {
    return this.keyStates.get(key.toLowerCase()) || false
  }

  cleanup() {
    if (this.canvas) {
      this.canvas.style.pointerEvents = ''
      this.canvas.style.cursor = ''
    }
    this.onMoveCallback = null
    this.onSkillCallback = null
    this.onAbilityCallback = null
    this.keyStates.clear()
    this.canvas = null
  }
}
