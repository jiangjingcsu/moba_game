<template>
  <div class="lobby-screen">
    <div class="lobby-bg"></div>
    <div class="lobby-content">
      <!-- 顶部导航栏 -->
      <div class="lobby-header">
        <div class="header-left">
          <h1 class="lobby-title">三方对决</h1>
        </div>
        <div class="header-right">
          <div class="currency">
            <span class="gold">💰 {{ playerInfo?.gold || 0 }}</span>
            <span class="diamond">💎 {{ playerInfo?.diamond || 0 }}</span>
          </div>
          <div class="player-info" @click="showProfile = !showProfile">
            <div class="avatar">
              <img v-if="playerInfo?.avatar" :src="playerInfo.avatar" class="avatar-img" />
              <img v-else src="/assets/avatars/default.png" class="avatar-img" />
            </div>
            <div class="player-detail">
              <span class="player-name">{{ playerInfo?.nickname || '玩家' }}</span>
              <span class="player-rank">{{ gameStore.rankName }} {{ playerInfo?.rankScore || 0 }}</span>
            </div>
          </div>
          <button class="btn-logout" @click="handleLogout">退出</button>
        </div>
      </div>

      <!-- 主体区域 -->
      <div class="lobby-main">
        <!-- 左侧：游戏模式 -->
        <div class="lobby-modes">
          <h3>选择模式</h3>
          <div class="mode-list">
            <div
              v-for="mode in gameStore.gameModes"
              :key="mode.id"
              class="mode-card"
              :class="{ selected: gameStore.selectedGameMode === mode.id, locked: mode.isLocked }"
              @click="selectMode(mode)"
            >
              <div class="mode-icon">{{ mode.icon }}</div>
              <div class="mode-info">
                <div class="mode-name">{{ mode.name }}</div>
                <div class="mode-desc">{{ mode.description }}</div>
                <div class="mode-meta">
                  <span>{{ mode.playerCount }}</span>
                  <span>{{ mode.mapName }}</span>
                </div>
              </div>
              <div v-if="mode.isLocked" class="lock-overlay">
                <span>🔒 {{ mode.lockReason }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧：签到/任务 -->
        <div class="lobby-sidebar">
          <!-- 签到 -->
          <div class="sign-in-card">
            <h3>每日签到</h3>
            <div class="sign-in-content" v-if="playerInfo">
              <div class="sign-days">
                <span>已连续签到</span>
                <strong>{{ playerInfo.signInDays }}</strong>
                <span>天</span>
              </div>
              <button
                class="btn-sign"
                :class="{ signed: playerInfo.isSignedIn }"
                @click="handleSignIn"
                :disabled="playerInfo.isSignedIn"
              >
                {{ playerInfo.isSignedIn ? '✅ 已签到' : '签到领奖' }}
              </button>
            </div>
          </div>

          <!-- 每日任务 -->
          <div class="tasks-card">
            <h3>每日任务</h3>
            <div class="task-list">
              <div
                v-for="task in gameStore.dailyTasks"
                :key="task.id"
                class="task-item"
                :class="{ completed: task.isCompleted }"
              >
                <div class="task-icon">{{ task.icon }}</div>
                <div class="task-info">
                  <div class="task-name">{{ task.name }}</div>
                  <div class="task-progress">
                    <div class="progress-bar">
                      <div class="progress-fill" :style="{ width: Math.min(100, (task.progress / task.target) * 100) + '%' }"></div>
                    </div>
                    <span>{{ task.progress }}/{{ task.target }}</span>
                  </div>
                </div>
                <button
                  class="btn-claim"
                  :class="{ claimed: task.isClaimed }"
                  @click="gameStore.claimTask(task.id)"
                  :disabled="!task.isCompleted || task.isClaimed"
                >
                  {{ task.isClaimed ? '已领' : task.reward.type === 'gold' ? `💰${task.reward.amount}` : `💎${task.reward.amount}` }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部：开始匹配 -->
      <div class="lobby-footer">
        <div class="selected-mode" v-if="currentMode">
          <span>{{ currentMode.icon }} {{ currentMode.name }}</span>
          <span class="mode-map">{{ currentMode.mapName }}</span>
        </div>
        <button
          class="btn-start"
          :class="{ disabled: !gameStore.selectedGameMode }"
          @click="startMatch"
          :disabled="!gameStore.selectedGameMode"
        >
          {{ gameStore.selectedGameMode ? '开始匹配' : '请选择游戏模式' }}
        </button>
      </div>
    </div>

    <!-- 个人资料弹窗 -->
    <div class="profile-modal" v-if="showProfile" @click.self="showProfile = false">
      <div class="profile-content">
        <h3>个人资料</h3>
        <div class="profile-avatar">
          <img src="/assets/avatars/default.png" class="profile-avatar-img" />
        </div>
        <div class="profile-name">{{ playerInfo?.nickname || '玩家' }}</div>
        <div class="profile-rank">{{ gameStore.rankName }} - {{ playerInfo?.rankScore }}分</div>
        <div class="profile-stats">
          <div class="stat-item"><span>等级</span><strong>{{ playerInfo?.level || 1 }}</strong></div>
          <div class="stat-item"><span>金币</span><strong>{{ playerInfo?.gold || 0 }}</strong></div>
          <div class="stat-item"><span>钻石</span><strong>{{ playerInfo?.diamond || 0 }}</strong></div>
          <div class="stat-item"><span>签到</span><strong>{{ playerInfo?.signInDays || 0 }}天</strong></div>
        </div>
        <button class="btn-close" @click="showProfile = false">关闭</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useGameStore } from '@/stores/game'
import type { GameMode } from '@/stores/game'

const gameStore = useGameStore()
const showProfile = ref(false)

const playerInfo = computed(() => gameStore.playerInfo)
const currentMode = computed(() => gameStore.gameModes.find(m => m.id === gameStore.selectedGameMode))

function selectMode(mode: GameMode) {
  if (mode.isLocked) return
  gameStore.setSelectedGameMode(mode.id)
}

function handleSignIn() {
  gameStore.signIn()
}

function startMatch() {
  if (!gameStore.selectedGameMode) return
  gameStore.setGameState('hero_select')
}

function handleLogout() {
  gameStore.logout()
}
</script>

<style scoped>
.lobby-screen {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.lobby-bg {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: url('/assets/ui/bg_lobby.png') center/cover no-repeat;
}

.lobby-bg::after {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: linear-gradient(180deg, rgba(10, 14, 26, 0.88) 0%, rgba(17, 24, 39, 0.85) 50%, rgba(15, 23, 42, 0.9) 100%);
}

.lobby-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.lobby-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: rgba(0, 0, 0, 0.3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.header-left { display: flex; align-items: center; }

.lobby-title {
  font-size: 24px;
  font-weight: 800;
  background: linear-gradient(135deg, #4a9eff, #ff4a4a, #ffa500);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0;
}

.header-right { display: flex; align-items: center; gap: 20px; }

.currency { display: flex; gap: 16px; }
.gold { color: #fbbf24; font-weight: 600; }
.diamond { color: #60a5fa; font-weight: 600; }

.player-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 8px;
  transition: background 0.2s;
}
.player-info:hover { background: rgba(255, 255, 255, 0.06); }

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4a9eff, #357abd);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: bold;
  font-size: 16px;
  overflow: hidden;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.player-detail { display: flex; flex-direction: column; }
.player-name { color: #fff; font-size: 14px; font-weight: 600; }
.player-rank { color: #fbbf24; font-size: 12px; }

.btn-logout {
  padding: 6px 14px;
  background: rgba(255, 74, 74, 0.15);
  border: 1px solid rgba(255, 74, 74, 0.3);
  border-radius: 6px;
  color: #ff4a4a;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-logout:hover { background: rgba(255, 74, 74, 0.25); }

.lobby-main {
  flex: 1;
  display: flex;
  padding: 20px 24px;
  gap: 20px;
  overflow: hidden;
}

.lobby-modes {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.lobby-modes h3, .lobby-sidebar h3 {
  color: #8a9bae;
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 2px;
  margin: 0 0 16px;
}

.mode-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  overflow-y: auto;
}

.mode-card {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 18px;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  gap: 14px;
  position: relative;
  overflow: hidden;
}
.mode-card:hover { background: rgba(255, 255, 255, 0.08); border-color: rgba(74, 158, 255, 0.3); }
.mode-card.selected {
  background: rgba(74, 158, 255, 0.1);
  border-color: #4a9eff;
  box-shadow: 0 0 20px rgba(74, 158, 255, 0.15);
}
.mode-card.locked { opacity: 0.5; cursor: not-allowed; }

.mode-icon { font-size: 36px; line-height: 1; }
.mode-info { flex: 1; }
.mode-name { color: #fff; font-size: 16px; font-weight: 600; margin-bottom: 4px; }
.mode-desc { color: #6a7a8a; font-size: 12px; margin-bottom: 8px; }
.mode-meta { display: flex; gap: 12px; color: #5a6a7a; font-size: 11px; }

.lock-overlay {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8a9bae;
  font-size: 14px;
}

.lobby-sidebar {
  width: 300px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sign-in-card, .tasks-card {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 18px;
}

.sign-in-content { text-align: center; }
.sign-days { color: #8a9bae; font-size: 14px; margin-bottom: 12px; }
.sign-days strong { color: #fbbf24; font-size: 24px; margin: 0 4px; }

.btn-sign {
  width: 100%;
  padding: 10px;
  background: linear-gradient(135deg, #fbbf24, #f59e0b);
  border: none;
  border-radius: 8px;
  color: #000;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-sign:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(251, 191, 36, 0.3); }
.btn-sign.signed { background: rgba(255, 255, 255, 0.06); color: #6a7a8a; cursor: default; }
.btn-sign:disabled { opacity: 0.6; cursor: not-allowed; }

.task-list { display: flex; flex-direction: column; gap: 10px; }

.task-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.02);
}
.task-item.completed { background: rgba(46, 204, 113, 0.06); }

.task-icon { font-size: 20px; }
.task-info { flex: 1; }
.task-name { color: #ccc; font-size: 12px; margin-bottom: 4px; }
.task-progress { display: flex; align-items: center; gap: 6px; }
.task-progress span { color: #6a7a8a; font-size: 10px; white-space: nowrap; }

.progress-bar {
  flex: 1;
  height: 4px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 2px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4a9eff, #00d4ff);
  border-radius: 2px;
  transition: width 0.3s;
}

.btn-claim {
  padding: 4px 10px;
  background: rgba(74, 158, 255, 0.15);
  border: 1px solid rgba(74, 158, 255, 0.3);
  border-radius: 4px;
  color: #4a9eff;
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
}
.btn-claim:hover:not(:disabled) { background: rgba(74, 158, 255, 0.25); }
.btn-claim.claimed { background: rgba(255, 255, 255, 0.04); border-color: rgba(255, 255, 255, 0.08); color: #4a4a4a; cursor: default; }
.btn-claim:disabled { opacity: 0.5; cursor: not-allowed; }

.lobby-footer {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
  padding: 16px 24px;
  background: rgba(0, 0, 0, 0.3);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.selected-mode {
  color: #8a9bae;
  font-size: 14px;
  display: flex;
  gap: 12px;
}
.mode-map { color: #5a6a7a; }

.btn-start {
  padding: 14px 60px;
  background: linear-gradient(135deg, #4a9eff, #357abd);
  border: none;
  border-radius: 10px;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s;
  letter-spacing: 2px;
}
.btn-start:hover:not(:disabled) {
  background: linear-gradient(135deg, #5ab0ff, #4a9eff);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(74, 158, 255, 0.3);
}
.btn-start.disabled, .btn-start:disabled {
  background: rgba(255, 255, 255, 0.06);
  color: #4a4a4a;
  cursor: not-allowed;
}

.profile-modal {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.profile-content {
  background: #1a1a2e;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  padding: 32px;
  text-align: center;
  min-width: 320px;
}

.profile-content h3 { color: #fff; margin: 0 0 20px; }

.profile-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4a9eff, #357abd);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 28px;
  font-weight: bold;
  margin: 0 auto 12px;
  overflow: hidden;
}

.profile-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.profile-name { color: #fff; font-size: 20px; font-weight: 600; margin-bottom: 4px; }
.profile-rank { color: #fbbf24; font-size: 14px; margin-bottom: 20px; }

.profile-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 20px;
}

.stat-item {
  background: rgba(255, 255, 255, 0.04);
  border-radius: 8px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stat-item span { color: #6a7a8a; font-size: 12px; }
.stat-item strong { color: #fff; font-size: 18px; }

.btn-close {
  padding: 8px 24px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  color: #8a9bae;
  cursor: pointer;
}
.btn-close:hover { background: rgba(255, 255, 255, 0.1); }
</style>
