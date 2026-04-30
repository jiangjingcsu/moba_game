<template>
  <div class="main-menu">
    <div class="menu-background">
      <div class="particles"></div>
      <div class="fog"></div>
    </div>
    
    <div class="menu-content">
      <header class="menu-header">
        <div class="logo-container">
          <div class="logo-glow"></div>
          <h1 class="game-title">
            <span class="title-accent">MOBA</span>
            <span class="title-sub">3v3v3</span>
          </h1>
        </div>
        <p class="game-subtitle">多人在线战斗竞技场</p>
        <div class="server-info">
          <span class="server-status">
            <span class="status-dot" :class="getServerStatusClass"></span>
            {{ getServerStatusText }}
          </span>
          <span class="ping">延迟: <strong>{{ ping }}ms</strong></span>
          <span class="version">v1.0.0</span>
        </div>
      </header>

      <nav class="menu-navigation">
        <button class="nav-btn primary" @click="handleStartGame">
          <span class="btn-icon">⚔️</span>
          <span class="btn-text">
            <span class="btn-title">开始游戏</span>
            <span class="btn-desc">进入3v3v3战斗</span>
          </span>
          <span class="btn-arrow">→</span>
        </button>
        
        <div class="secondary-buttons">
          <button class="nav-btn" @click="showHeroSelect = true">
            <span class="btn-icon">👤</span>
            <span class="btn-text">
              <span class="btn-title">英雄选择</span>
              <span class="btn-desc">6位英雄等你挑选</span>
            </span>
          </button>
          
          <button class="nav-btn" @click="showSettings = true">
            <span class="btn-icon">⚙️</span>
            <span class="btn-text">
              <span class="btn-title">游戏设置</span>
              <span class="btn-desc">音量、画质设置</span>
            </span>
          </button>
          
          <button class="nav-btn" @click="showHelp = true">
            <span class="btn-icon">❓</span>
            <span class="btn-text">
              <span class="btn-title">操作说明</span>
              <span class="btn-desc">查看游戏指南</span>
            </span>
          </button>
        </div>
      </nav>
    </div>

    <footer class="menu-footer">
      <p>© 2024 MOBA 3v3v3 | Powered by Vue3 + Pixi.js</p>
    </footer>

    <div v-if="showLogin" class="modal" @click.self="showLogin = false">
      <div class="modal-content login-modal">
        <h2>🔐 登录游戏</h2>
        <p class="modal-desc">{{ isRegisterMode ? '注册新账号' : '登录你的账号' }}</p>

        <div class="login-form">
          <div class="form-group">
            <label>用户名</label>
            <input
              type="text"
              v-model="loginForm.username"
              placeholder="请输入用户名"
              maxlength="50"
              @keyup.enter="handleLogin"
            />
          </div>
          <div class="form-group">
            <label>密码</label>
            <input
              type="password"
              v-model="loginForm.password"
              placeholder="请输入密码"
              maxlength="50"
              @keyup.enter="handleLogin"
            />
          </div>
          <div v-if="isRegisterMode" class="form-group">
            <label>昵称</label>
            <input
              type="text"
              v-model="loginForm.nickname"
              placeholder="请输入游戏昵称"
              maxlength="50"
              @keyup.enter="handleLogin"
            />
          </div>

          <div v-if="loginError" class="error-message">
            {{ loginError }}
          </div>
        </div>

        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showLogin = false">取消</button>
          <button class="btn btn-link" @click="isRegisterMode = !isRegisterMode">
            {{ isRegisterMode ? '已有账号？登录' : '没有账号？注册' }}
          </button>
          <button class="btn btn-primary" @click="handleLogin" :disabled="isLoggingIn">
            {{ isLoggingIn ? '处理中...' : (isRegisterMode ? '注册' : '登录') }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="showSettings" class="modal" @click.self="showSettings = false">
      <div class="modal-content settings-modal">
        <h2>⚙️ 游戏设置</h2>
        
        <section class="settings-section">
          <h3>音频设置</h3>
          <div class="setting-row">
            <label>主音量</label>
            <div class="slider-container">
              <input type="range" min="0" max="100" v-model.number="settings.volume">
              <span class="slider-value">{{ settings.volume }}%</span>
            </div>
          </div>
          <div class="setting-row">
            <label>音效</label>
            <div class="slider-container">
              <input type="range" min="0" max="100" v-model.number="settings.sfx">
              <span class="slider-value">{{ settings.sfx }}%</span>
            </div>
          </div>
        </section>

        <section class="settings-section">
          <h3>图像设置</h3>
          <div class="setting-row">
            <label>画质</label>
            <select v-model="settings.quality">
              <option value="low">低 (流畅)</option>
              <option value="medium">中 (平衡)</option>
              <option value="high">高 (精美)</option>
              <option value="ultra">极致 (最佳)</option>
            </select>
          </div>
          <div class="setting-row">
            <label>帧率限制</label>
            <select v-model="settings.fpsLimit">
              <option value="30">30 FPS</option>
              <option value="60">60 FPS</option>
              <option value="120">120 FPS</option>
              <option value="unlimited">无限制</option>
            </select>
          </div>
          <div class="setting-row">
            <label>分辨率</label>
            <span class="setting-value">{{ windowWidth }}x{{ windowHeight }}</span>
          </div>
        </section>

        <section class="settings-section">
          <h3>控制设置</h3>
          <div class="setting-row">
            <label>鼠标速度</label>
            <div class="slider-container">
              <input type="range" min="1" max="10" v-model.number="settings.mouseSpeed">
              <span class="slider-value">{{ settings.mouseSpeed }}</span>
            </div>
          </div>
        </section>

        <div class="modal-actions">
          <button class="btn btn-secondary" @click="resetSettings">恢复默认</button>
          <button class="btn btn-primary" @click="showSettings = false">确定</button>
        </div>
      </div>
    </div>

    <div v-if="showHeroSelect" class="modal" @click.self="showHeroSelect = false">
      <div class="modal-content hero-select-modal">
        <div class="modal-header">
          <h2>👤 选择英雄</h2>
          <p class="modal-desc">选择你的出战英雄，每位英雄都有独特的技能</p>
        </div>
        
        <div class="role-filter">
          <button
            v-for="role in roles"
            :key="role.key"
            class="role-btn"
            :class="{ active: selectedRole === role.key }"
            @click="selectedRole = role.key"
          >
            {{ role.icon }} {{ role.name }}
          </button>
        </div>

        <div class="hero-grid">
          <div
            v-for="hero in filteredHeroes"
            :key="hero.id"
            class="hero-card"
            :class="{ selected: selectedHero === hero.id }"
            @click="selectedHero = hero.id"
          >
            <div class="hero-card-bg" :style="{ background: `linear-gradient(135deg, ${hero.colors[0]}, ${hero.colors[1]})` }"></div>
            <div class="hero-card-content">
              <div class="hero-icon" :style="{ background: hero.colors[1] }">
                <span class="role-badge">{{ roleIcons[hero.role] }}</span>
              </div>
              <h3>{{ hero.name }}</h3>
              <p class="hero-title">{{ hero.title }}</p>
              <div class="hero-meta">
                <span class="hero-role">{{ roleNames[hero.role] }}</span>
                <span class="hero-difficulty" :title="`难度: ${hero.difficulty}/5`">
                  {{ '⭐'.repeat(hero.difficulty) }}{{ '☆'.repeat(5 - hero.difficulty) }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showHeroSelect = false">取消</button>
          <button
            class="btn btn-primary"
            :disabled="!selectedHero"
            @click="confirmHeroSelect"
          >
            确认选择
          </button>
        </div>
      </div>
    </div>

    <div v-if="showHelp" class="modal" @click.self="showHelp = false">
      <div class="modal-content help-modal">
        <h2>❓ 操作说明</h2>
        
        <div class="help-section">
          <h3>🖱️ 鼠标操作</h3>
          <table class="help-table">
            <tr><td>右键点击地面</td><td>移动英雄</td></tr>
            <tr><td>左键点击英雄</td><td>选择目标</td></tr>
            <tr><td>滚轮</td><td>缩放地图</td></tr>
          </table>
        </div>

        <div class="help-section">
          <h3>⌨️ 键盘快捷键</h3>
          <table class="help-table">
            <tr><td>Q / W / E</td><td>释放技能</td></tr>
            <tr><td>R</td><td>释放终极技能</td></tr>
            <tr><td>D / F</td><td>召唤师技能</td></tr>
            <tr><td>空格</td><td>摄像机居中</td></tr>
            <tr><td>Tab</td><td>显示计分板</td></tr>
          </table>
        </div>

        <div class="help-section">
          <h3>📋 游戏目标</h3>
          <ul>
            <li>与队友合作，摧毁敌方基地</li>
            <li>击杀敌方英雄获得金币和经验</li>
            <li>击杀野怪获取增益效果</li>
            <li>购买装备提升英雄实力</li>
          </ul>
        </div>

        <div class="modal-actions">
          <button class="btn btn-primary" @click="showHelp = false">我知道了</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useGameStore } from '@/stores/game'
import { heroDefinitions, roleIcons, roleNames } from '@/data/heroes'
import { HeroRole } from '@/types/game'
import { httpLogin, httpRegister, joinMatch, getMatchStatus, cancelMatch, connectToBattleServer, enterBattle, isConnected, ping } from '@/network'

const emit = defineEmits<{
  (e: 'start-game'): void
}>()

const gameStore = useGameStore()
const showSettings = ref(false)
const showHeroSelect = ref(false)
const showHelp = ref(false)
const showLogin = ref(false)
const selectedHero = ref<string>('')
const selectedRole = ref<string>('all')
const isLoggingIn = ref(false)
const isMatching = ref(false)
const isRegisterMode = ref(false)
const loginError = ref('')

const windowWidth = ref(window.innerWidth)
const windowHeight = ref(window.innerHeight)

const loginForm = ref({
  username: '',
  password: '',
  nickname: '',
})

const settings = ref({
  volume: 80,
  sfx: 70,
  quality: 'high',
  fpsLimit: '60',
  mouseSpeed: 5
})

const roles = ref([
  { key: 'all', icon: '👥', name: '全部' },
  { key: HeroRole.WARRIOR, icon: '⚔️', name: '战士' },
  { key: HeroRole.MAGE, icon: '✨', name: '法师' },
  { key: HeroRole.ASSASSIN, icon: '🗡️', name: '刺客' },
  { key: HeroRole.MARKSMAN, icon: '🏹', name: '射手' },
  { key: HeroRole.SUPPORT, icon: '💚', name: '辅助' },
  { key: HeroRole.TANK, icon: '🛡️', name: '坦克' },
])

let pingInterval: number | null = null
let matchCheckInterval: number | null = null

const filteredHeroes = computed(() => {
  if (selectedRole.value === 'all') return heroDefinitions
  return heroDefinitions.filter(h => h.role === selectedRole.value)
})

onMounted(() => {
  pingInterval = window.setInterval(() => {
    ping.value = 25 + Math.floor(Math.random() * 20)
  }, 2000)
})

onUnmounted(() => {
  if (pingInterval) clearInterval(pingInterval)
  if (matchCheckInterval) clearInterval(matchCheckInterval)
})

const handleLogin = async () => {
  if (!loginForm.value.username.trim() || !loginForm.value.password.trim()) {
    loginError.value = '请输入用户名和密码'
    return
  }
  if (isRegisterMode.value && !loginForm.value.nickname.trim()) {
    loginError.value = '请输入昵称'
    return
  }

  isLoggingIn.value = true
  loginError.value = ''

  try {
    const result = isRegisterMode.value
      ? await httpRegister(loginForm.value.username, loginForm.value.password, loginForm.value.nickname)
      : await httpLogin(loginForm.value.username, loginForm.value.password)

    if (result.success && result.playerInfo) {
      gameStore.setPlayerInfo({
        playerId: result.playerInfo.playerId,
        playerName: result.playerInfo.nickname || result.playerInfo.playerName,
        rank: result.playerInfo.rank,
        rankScore: result.playerInfo.rankScore,
      })
      showLogin.value = false
      if (!selectedHero.value) {
        showHeroSelect.value = true
      } else {
        handleStartGame()
      }
    } else {
      loginError.value = result.error || (isRegisterMode.value ? '注册失败' : '登录失败')
    }
  } catch (e) {
    loginError.value = '请求失败，请检查网络'
  } finally {
    isLoggingIn.value = false
  }
}

const handleStartGame = async () => {
  if (!gameStore.playerInfo) {
    showLogin.value = true
    return
  }

  if (!selectedHero.value && gameStore.playerInfo) {
    showHeroSelect.value = true
    return
  }

  isMatching.value = true
  gameStore.setGameState('matching')

  try {
    const joinResult = await joinMatch(1)
    if (!joinResult.success) {
      loginError.value = joinResult.error || '加入匹配失败'
      isMatching.value = false
      gameStore.setGameState('menu')
      return
    }

    matchCheckInterval = window.setInterval(async () => {
      const status = await getMatchStatus()
      if (status.matched && status.battleId) {
        if (matchCheckInterval) {
          clearInterval(matchCheckInterval)
          matchCheckInterval = null
        }

        try {
          await connectToBattleServer()
        } catch (e) {
          console.error('连接战斗服务器失败:', e)
          isMatching.value = false
          gameStore.setGameState('menu')
          return
        }

        const heroIdNum = parseInt(selectedHero.value) || 1
        const battleResponse = await enterBattle(status.battleId, heroIdNum, 0)

        if (battleResponse.success) {
          isMatching.value = false
          gameStore.setGameState('loading')
          gameStore.setBattleInfo({
            battleId: battleResponse.battleId,
            mapId: battleResponse.mapId,
            mapConfig: battleResponse.mapConfig,
            heroId: heroIdNum,
            teamId: 0,
          })
          emit('start-game')
        } else {
          isMatching.value = false
          gameStore.setGameState('menu')
          console.error('进入战斗失败:', battleResponse.errorMessage)
        }
      }
    }, 2000)
  } catch (e) {
    isMatching.value = false
    gameStore.setGameState('menu')
    console.error('匹配失败:', e)
  }
}

const confirmHeroSelect = () => {
  if (selectedHero.value) {
    showHeroSelect.value = false
  }
}

const resetSettings = () => {
  settings.value = {
    volume: 80,
    sfx: 70,
    quality: 'high',
    fpsLimit: '60',
    mouseSpeed: 5
  }
}

const getServerStatusText = computed(() => {
  if (isConnected.value) return '服务器在线'
  return '服务器离线'
})

const getServerStatusClass = computed(() => {
  return isConnected.value ? 'online' : 'offline'
})
</script>

<style scoped>
.main-menu {
  width: 100%;
  height: 100%;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.menu-background {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #0a0a1a 0%, #1a1a3e 30%, #0f3460 60%, #0a0a1a 100%);
  z-index: 0;
}

.menu-background::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at 30% 20%, rgba(74, 158, 255, 0.1) 0%, transparent 50%),
              radial-gradient(ellipse at 70% 80%, rgba(255, 74, 74, 0.1) 0%, transparent 50%);
}

.menu-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 500px;
}

.menu-header {
  text-align: center;
  margin-bottom: 50px;
}

.logo-container {
  position: relative;
  display: inline-block;
}

.logo-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(74, 158, 255, 0.2) 0%, transparent 70%);
  animation: pulse 3s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { transform: translate(-50%, -50%) scale(1); opacity: 0.5; }
  50% { transform: translate(-50%, -50%) scale(1.2); opacity: 0.8; }
}

.game-title {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 0;
}

.title-accent {
  font-size: 80px;
  font-weight: 900;
  background: linear-gradient(135deg, #4a9eff, #88ccff, #4a9eff);
  background-size: 200% auto;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: shimmer 3s linear infinite;
  text-shadow: 0 0 40px rgba(74, 158, 255, 0.3);
}

.title-sub {
  font-size: 36px;
  color: rgba(255, 255, 255, 0.6);
  letter-spacing: 8px;
  margin-top: -10px;
}

@keyframes shimmer {
  0% { background-position: 0% center; }
  100% { background-position: 200% center; }
}

.game-subtitle {
  font-size: 18px;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 10px;
  letter-spacing: 4px;
}

.server-info {
  display: flex;
  gap: 20px;
  margin-top: 20px;
  padding: 10px 20px;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 20px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 5px;
}

.status-dot.online {
  background: #2ecc71;
  box-shadow: 0 0 10px rgba(46, 204, 113, 0.5);
}

.status-dot.offline {
  background: #e74c3c;
  box-shadow: 0 0 10px rgba(231, 76, 60, 0.5);
}

.ping strong {
  color: #2ecc71;
}

.menu-navigation {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.nav-btn {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 18px 25px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  color: white;
  cursor: pointer;
  transition: all 0.3s ease;
  text-align: left;
}

.nav-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(74, 158, 255, 0.3);
  transform: translateY(-2px);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
}

.nav-btn.primary {
  background: linear-gradient(135deg, rgba(74, 158, 255, 0.3), rgba(45, 125, 210, 0.3));
  border-color: rgba(74, 158, 255, 0.5);
}

.nav-btn.primary:hover {
  background: linear-gradient(135deg, rgba(74, 158, 255, 0.5), rgba(45, 125, 210, 0.5));
  box-shadow: 0 10px 40px rgba(74, 158, 255, 0.3);
}

.btn-icon {
  font-size: 28px;
  flex-shrink: 0;
}

.btn-text {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.btn-title {
  font-size: 18px;
  font-weight: 600;
}

.btn-desc {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 2px;
}

.btn-arrow {
  font-size: 20px;
  color: rgba(255, 255, 255, 0.3);
  transition: transform 0.3s ease;
}

.nav-btn:hover .btn-arrow {
  transform: translateX(5px);
  color: rgba(255, 255, 255, 0.6);
}

.secondary-buttons {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.secondary-buttons .nav-btn {
  padding: 15px 18px;
  flex-direction: column;
  text-align: center;
  gap: 8px;
}

.secondary-buttons .btn-text {
  align-items: center;
}

.menu-footer {
  position: absolute;
  bottom: 20px;
  color: rgba(255, 255, 255, 0.3);
  font-size: 12px;
}

.modal {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.85);
  backdrop-filter: blur(10px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  padding: 20px;
}

.modal-content {
  background: linear-gradient(180deg, #1a1a2e 0%, #0f1a2e 100%);
  padding: 35px;
  border-radius: 16px;
  width: 100%;
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 25px 50px rgba(0, 0, 0, 0.5);
}

.settings-modal {
  max-width: 500px;
}

.login-modal {
  max-width: 400px;
}

.login-form {
  margin: 20px 0;
}

.form-group {
  margin-bottom: 15px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
}

.form-group input {
  width: 100%;
  padding: 12px 15px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  color: white;
  font-size: 14px;
  box-sizing: border-box;
}

.form-group input:focus {
  outline: none;
  border-color: #4a9eff;
}

.form-group input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.error-message {
  color: #e74c3c;
  font-size: 13px;
  margin-top: 10px;
  padding: 8px 12px;
  background: rgba(231, 76, 60, 0.1);
  border-radius: 6px;
}

.settings-modal h2,
.hero-select-modal h2,
.help-modal h2 {
  margin: 0 0 10px 0;
  font-size: 24px;
}

.modal-desc {
  color: rgba(255, 255, 255, 0.5);
  margin-bottom: 25px;
  font-size: 14px;
}

.settings-section {
  margin-bottom: 25px;
  padding-bottom: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.settings-section h3 {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 15px;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.setting-row label {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
}

.slider-container {
  display: flex;
  align-items: center;
  gap: 10px;
}

.slider-container input[type="range"] {
  width: 150px;
}

.slider-value {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  min-width: 40px;
  text-align: right;
}

.setting-row select {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: white;
  padding: 8px 12px;
  border-radius: 6px;
}

.setting-value {
  color: rgba(255, 255, 255, 0.6);
  font-size: 13px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

.btn {
  padding: 10px 25px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s ease;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(135deg, #4a9eff, #2d7dd2);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(74, 158, 255, 0.3);
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.1);
  color: white;
}

.btn-secondary:hover {
  background: rgba(255, 255, 255, 0.2);
}

.btn-link {
  background: none;
  border: none;
  color: #4a9eff;
  cursor: pointer;
  font-size: 13px;
  padding: 10px 15px;
}

.btn-link:hover {
  text-decoration: underline;
}

.hero-select-modal {
  max-width: 900px;
  max-height: 90vh;
  overflow-y: auto;
}

.role-filter {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.role-btn {
  padding: 8px 15px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 20px;
  color: white;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s ease;
}

.role-btn:hover,
.role-btn.active {
  background: rgba(74, 158, 255, 0.2);
  border-color: rgba(74, 158, 255, 0.5);
}

.hero-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 15px;
  margin-bottom: 25px;
}

.hero-card {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid transparent;
}

.hero-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 15px 30px rgba(0, 0, 0, 0.4);
}

.hero-card.selected {
  border-color: #4a9eff;
  box-shadow: 0 0 20px rgba(74, 158, 255, 0.3);
}

.hero-card-bg {
  position: absolute;
  inset: 0;
  opacity: 0.3;
}

.hero-card-content {
  position: relative;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hero-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  border: 3px solid rgba(255, 255, 255, 0.3);
}

.role-badge {
  font-size: 24px;
}

.hero-card h3 {
  margin: 0;
  font-size: 16px;
}

.hero-title {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
  margin: 4px 0 10px 0;
}

.hero-meta {
  display: flex;
  justify-content: space-between;
  width: 100%;
  font-size: 11px;
}

.hero-role {
  background: rgba(255, 255, 255, 0.1);
  padding: 3px 8px;
  border-radius: 10px;
  color: rgba(255, 255, 255, 0.6);
}

.hero-difficulty {
  font-size: 10px;
}

.help-modal {
  max-width: 500px;
}

.help-section {
  margin-bottom: 20px;
}

.help-section h3 {
  font-size: 15px;
  margin-bottom: 10px;
  color: #4a9eff;
}

.help-table {
  width: 100%;
  font-size: 13px;
}

.help-table td {
  padding: 6px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.help-table td:first-child {
  color: #4a9eff;
  font-weight: 500;
  white-space: nowrap;
}

.help-section ul {
  list-style: none;
  padding: 0;
}

.help-section li {
  padding: 6px 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
}

.help-section li::before {
  content: '•';
  color: #4a9eff;
  margin-right: 10px;
}
</style>
