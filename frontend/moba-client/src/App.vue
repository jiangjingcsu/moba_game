<template>
  <div class="game-container">
    <LoginScreen v-if="gameStore.gameState === 'login'" />
    <GameLobby v-if="gameStore.gameState === 'lobby'" />
    <HeroSelect v-if="gameStore.gameState === 'hero_select'" />
    <MatchScreen v-if="gameStore.gameState === 'matching'" />
    <LoadingScreen v-if="gameStore.gameState === 'loading'" />
    <GameCanvas v-if="gameStore.gameState === 'playing'" />
    <GameUI v-if="gameStore.gameState === 'playing'" />
    <DebugSidebar />
  </div>
</template>

<script setup lang="ts">
import { watch, onMounted, onUnmounted } from 'vue'
import LoginScreen from './components/LoginScreen.vue'
import GameLobby from './components/GameLobby.vue'
import HeroSelect from './components/HeroSelect.vue'
import MatchScreen from './components/MatchScreen.vue'
import LoadingScreen from './components/LoadingScreen.vue'
import GameCanvas from './components/GameCanvas.vue'
import GameUI from './components/GameUI.vue'
import DebugSidebar from './components/DebugSidebar.vue'
import { useGameStore } from './stores/game'

const gameStore = useGameStore()

watch(() => gameStore.gameState, (state) => {
  console.log('Game state:', state)
}, { immediate: true })

function handleBeforeUnload(e: BeforeUnloadEvent) {
  const state = gameStore.gameState
  if (state === 'playing' || state === 'matching' || state === 'loading') {
    e.preventDefault()
  }
}

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  width: 100%;
  height: 100%;
  overflow: hidden;
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
  background: #0a0e1a;
  color: #fff;
}
</style>

<style scoped>
.game-container {
  width: 100%;
  height: 100%;
  position: relative;
}

.game-container > * {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}
</style>
