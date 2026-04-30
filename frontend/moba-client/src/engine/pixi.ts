import { Application } from 'pixi.js'
import { ref, shallowRef } from 'vue'

export const gameApp = shallowRef<Application | null>(null)
export const isGameReady = ref(false)

export async function initPixiApp(canvas: HTMLCanvasElement): Promise<Application> {
  const app = new Application({
    view: canvas,
    width: window.innerWidth,
    height: window.innerHeight,
    backgroundColor: 0x1a1a2e,
    resolution: window.devicePixelRatio || 1,
    autoDensity: true,
    antialias: true,
  })

  gameApp.value = app
  isGameReady.value = true

  window.addEventListener('resize', () => {
    app.renderer.resize(window.innerWidth, window.innerHeight)
  })

  return app
}

export function destroyPixiApp() {
  if (gameApp.value) {
    gameApp.value.destroy(true)
    gameApp.value = null
    isGameReady.value = false
  }
}
