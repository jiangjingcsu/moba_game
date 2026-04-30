<template>
  <div class="match-screen">
    <div class="match-bg"></div>
    <div class="match-content">
      <div class="match-header">
        <button class="btn-cancel" @click="cancelMatch">✕ 取消匹配</button>
        <h2>匹配中</h2>
      </div>

      <div class="match-body">
        <div class="match-ring">
          <div class="ring-outer"></div>
          <div class="ring-inner"></div>
          <div class="match-time">{{ formatTime(waitTime) }}</div>
        </div>
        <p class="match-tip">正在寻找对手...</p>
        <div class="match-info">
          <span>{{ currentMode?.icon }} {{ currentMode?.name }}</span>
          <span>{{ currentMode?.playerCount }}</span>
        </div>
      </div>

      <div class="match-players">
        <div class="team-slot" v-for="i in 9" :key="i" :class="{ filled: i <= filledSlots }">
          <div class="slot-avatar">{{ i <= filledSlots ? '👤' : '?' }}</div>
          <div class="slot-name">{{ i <= filledSlots ? '玩家' + i : '等待中...' }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useGameStore } from '@/stores/game'

const gameStore = useGameStore()
const waitTime = ref(0)
const filledSlots = ref(1)
let timer: number | null = null

const currentMode = computed(() => gameStore.gameModes.find(m => m.id === gameStore.selectedGameMode))

onMounted(() => {
  timer = window.setInterval(() => {
    waitTime.value++
    if (filledSlots.value < 9 && Math.random() < 0.3) {
      filledSlots.value++
    }
    if (waitTime.value >= 5 && filledSlots.value >= 9) {
      gameStore.setGameState('loading')
      if (timer) clearInterval(timer)
    }
    if (waitTime.value >= 8) {
      filledSlots.value = 9
      setTimeout(() => {
        gameStore.setGameState('loading')
      }, 500)
      if (timer) clearInterval(timer)
    }
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

function cancelMatch() {
  if (timer) clearInterval(timer)
  gameStore.setGameState('hero_select')
}
</script>

<style scoped>
.match-screen {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.match-bg {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: linear-gradient(180deg, #0a0e1a 0%, #111827 50%, #0f172a 100%);
}

.match-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  padding: 40px;
}

.match-header {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 20px;
}

.match-header h2 { color: #fff; margin: 0; }

.btn-cancel {
  padding: 8px 18px;
  background: rgba(255, 74, 74, 0.15);
  border: 1px solid rgba(255, 74, 74, 0.3);
  border-radius: 8px;
  color: #ff4a4a;
  cursor: pointer;
  font-size: 14px;
}
.btn-cancel:hover { background: rgba(255, 74, 74, 0.25); }

.match-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.match-ring {
  position: relative;
  width: 180px;
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
}

.ring-outer {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  border: 3px solid rgba(74, 158, 255, 0.3);
  border-top-color: #4a9eff;
  border-radius: 50%;
  animation: spin 1.5s linear infinite;
}

.ring-inner {
  position: absolute;
  top: 15px; left: 15px; right: 15px; bottom: 15px;
  border: 2px solid rgba(255, 165, 0, 0.2);
  border-bottom-color: #ffa500;
  border-radius: 50%;
  animation: spin 2s linear infinite reverse;
}

@keyframes spin { to { transform: rotate(360deg); } }

.match-time {
  font-size: 36px;
  font-weight: 700;
  color: #fff;
  z-index: 1;
}

.match-tip {
  color: #8a9bae;
  font-size: 16px;
  margin: 0 0 12px;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.match-info {
  display: flex;
  gap: 16px;
  color: #5a6a7a;
  font-size: 14px;
}

.match-players {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  max-width: 600px;
}

.team-slot {
  width: 60px;
  text-align: center;
  opacity: 0.4;
  transition: all 0.3s;
}
.team-slot.filled { opacity: 1; }

.slot-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
  border: 2px solid rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  margin: 0 auto 4px;
}
.team-slot.filled .slot-avatar {
  border-color: #4a9eff;
  background: rgba(74, 158, 255, 0.1);
}

.slot-name { color: #6a7a8a; font-size: 10px; }
.team-slot.filled .slot-name { color: #ccc; }
</style>
