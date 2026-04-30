<template>
  <div class="loading-screen">
    <div class="loading-content">
      <div class="loading-spinner"></div>
      <h2 class="loading-title">正在进入战斗...</h2>
      <p class="loading-tip">{{ loadingTip }}</p>
      <div class="loading-progress">
        <div class="progress-bar" :style="{ width: progress + '%' }"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useGameStore } from '@/stores/game'

const gameStore = useGameStore()
const progress = ref(0)
const loadingTip = ref('正在连接服务器...')

let progressInterval: number | null = null

onMounted(() => {
  const tips = ['正在连接服务器...', '正在加载地图...', '正在初始化英雄...', '准备就绪！']
  let tipIndex = 0

  progressInterval = window.setInterval(() => {
    progress.value += Math.random() * 12 + 3

    if (progress.value > 25 && tipIndex === 0) { loadingTip.value = tips[1]; tipIndex++ }
    if (progress.value > 50 && tipIndex === 1) { loadingTip.value = tips[2]; tipIndex++ }
    if (progress.value > 80 && tipIndex === 2) { loadingTip.value = tips[3]; tipIndex++ }

    if (progress.value >= 100) {
      progress.value = 100
      if (progressInterval) clearInterval(progressInterval)
      setTimeout(() => {
        gameStore.setGameState('playing')
      }, 300)
    }
  }, 200)
})

onUnmounted(() => {
  if (progressInterval) clearInterval(progressInterval)
})
</script>

<style scoped>
.loading-screen {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading-content {
  text-align: center;
  color: white;
}

.loading-spinner {
  width: 80px;
  height: 80px;
  border: 4px solid rgba(255, 255, 255, 0.2);
  border-top-color: #4a9eff;
  border-radius: 50%;
  margin: 0 auto 30px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-title {
  font-size: 28px;
  font-weight: bold;
  margin-bottom: 10px;
  text-shadow: 0 0 20px rgba(74, 158, 255, 0.5);
}

.loading-tip {
  font-size: 14px;
  color: #8a9bae;
  margin-bottom: 30px;
}

.loading-progress {
  width: 300px;
  height: 6px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
  overflow: hidden;
  margin: 0 auto;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #4a9eff, #00d4ff);
  border-radius: 3px;
  transition: width 0.3s ease;
}
</style>
