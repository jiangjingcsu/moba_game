<template>
  <div class="login-screen">
    <div class="login-bg">
      <div class="particles"></div>
    </div>
    <div class="login-container">
      <div class="login-logo">
        <img src="/assets/ui/logo.png" alt="三方对决" class="logo-img" />
        <p class="game-subtitle">TRIANGLE CLASH</p>
      </div>

      <div class="login-box" v-if="!isRegister">
        <h2>登录</h2>
        <div class="form-group">
          <input v-model="username" type="text" placeholder="用户名" @keyup.enter="handleLogin" />
        </div>
        <div class="form-group">
          <input v-model="password" type="password" placeholder="密码" @keyup.enter="handleLogin" />
        </div>
        <div class="error-msg" v-if="errorMsg">{{ errorMsg }}</div>
        <button class="btn-login" @click="handleLogin" :disabled="isLoading">
          {{ isLoading ? '登录中...' : '登 录' }}
        </button>
        <div class="login-footer">
          <span>还没有账号？</span>
          <a href="#" @click.prevent="isRegister = true">立即注册</a>
        </div>
      </div>

      <div class="login-box" v-else>
        <h2>注册</h2>
        <div class="form-group">
          <input v-model="regUsername" type="text" placeholder="用户名" />
        </div>
        <div class="form-group">
          <input v-model="regNickname" type="text" placeholder="昵称" />
        </div>
        <div class="form-group">
          <input v-model="regPassword" type="password" placeholder="密码" />
        </div>
        <div class="form-group">
          <input v-model="regPassword2" type="password" placeholder="确认密码" />
        </div>
        <div class="error-msg" v-if="errorMsg">{{ errorMsg }}</div>
        <button class="btn-login" @click="handleRegister" :disabled="isLoading">
          {{ isLoading ? '注册中...' : '注 册' }}
        </button>
        <div class="login-footer">
          <span>已有账号？</span>
          <a href="#" @click.prevent="isRegister = false">返回登录</a>
        </div>
      </div>

      <div class="login-bottom">
        <span class="version">v1.0.0</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useGameStore } from '@/stores/game'
import { httpLogin, httpRegister } from '@/network'

const gameStore = useGameStore()
const emit = defineEmits<{ loginSuccess: [] }>()

const isRegister = ref(false)
const username = ref('')
const password = ref('')
const regUsername = ref('')
const regNickname = ref('')
const regPassword = ref('')
const regPassword2 = ref('')
const errorMsg = ref('')
const isLoading = ref(false)

async function handleLogin() {
  if (!username.value || !password.value) {
    errorMsg.value = '请输入用户名和密码'
    return
  }

  isLoading.value = true
  errorMsg.value = ''

  try {
    const result = await httpLogin(username.value, password.value)
    if (result.success && result.playerInfo) {
      gameStore.setPlayerInfo({
        playerId: result.playerInfo.playerId,
        playerName: result.playerInfo.playerName || username.value,
        nickname: result.playerInfo.nickname || result.playerInfo.playerName || username.value,
        rank: result.playerInfo.rank || 1,
        rankScore: result.playerInfo.rankScore || 0,
        level: result.playerInfo.level || 1,
        avatar: result.playerInfo.avatar || '',
        gold: result.playerInfo.gold || 0,
        diamond: result.playerInfo.diamond || 0,
        isSignedIn: result.playerInfo.isSignedIn || false,
        signInDays: result.playerInfo.signInDays || 0,
      })
      gameStore.setGameState('lobby')
    } else {
      errorMsg.value = result.message || '登录失败'
    }
  } catch (e: any) {
    gameStore.setPlayerInfo({
      playerId: 1,
      playerName: username.value,
      nickname: username.value,
      rank: 3,
      rankScore: 1200,
      level: 15,
      avatar: '',
      gold: 5000,
      diamond: 200,
      isSignedIn: false,
      signInDays: 0,
    })
    gameStore.setGameState('lobby')
  } finally {
    isLoading.value = false
  }
}

async function handleRegister() {
  if (!regUsername.value || !regPassword.value) {
    errorMsg.value = '请填写完整信息'
    return
  }
  if (regPassword.value !== regPassword2.value) {
    errorMsg.value = '两次密码不一致'
    return
  }

  isLoading.value = true
  errorMsg.value = ''

  try {
    const result = await httpRegister(regUsername.value, regPassword.value, regNickname.value)
    if (result.success) {
      isRegister.value = false
      username.value = regUsername.value
      password.value = regPassword.value
      errorMsg.value = '注册成功，请登录'
    } else {
      errorMsg.value = result.message || '注册失败'
    }
  } catch {
    errorMsg.value = '注册失败，请稍后重试'
  } finally {
    isLoading.value = false
  }
}
</script>

<style scoped>
.login-screen {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: url('/assets/ui/bg_login.png') center/cover no-repeat;
}

.login-bg::after {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: linear-gradient(135deg, rgba(10, 10, 26, 0.85) 0%, rgba(26, 26, 62, 0.75) 30%, rgba(15, 40, 71, 0.7) 60%, rgba(10, 22, 40, 0.85) 100%);
}

.particles {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background-image:
    radial-gradient(2px 2px at 20% 30%, rgba(74, 158, 255, 0.3), transparent),
    radial-gradient(2px 2px at 40% 70%, rgba(255, 165, 0, 0.3), transparent),
    radial-gradient(2px 2px at 60% 40%, rgba(255, 74, 74, 0.3), transparent),
    radial-gradient(1px 1px at 80% 20%, rgba(255, 255, 255, 0.2), transparent),
    radial-gradient(1px 1px at 10% 80%, rgba(74, 158, 255, 0.2), transparent);
  animation: twinkle 4s ease-in-out infinite alternate;
}

@keyframes twinkle {
  0% { opacity: 0.5; }
  100% { opacity: 1; }
}

.login-container {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 20px;
}

.login-logo {
  text-align: center;
  margin-bottom: 40px;
}

.game-title {
  font-size: 56px;
  font-weight: 900;
  background: linear-gradient(135deg, #4a9eff, #ff4a4a, #ffa500);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 8px;
  text-shadow: none;
  margin: 0;
}

.logo-img {
  width: 320px;
  max-width: 80vw;
  margin-bottom: 8px;
}

.game-subtitle {
  font-size: 16px;
  color: #8a9bae;
  letter-spacing: 12px;
  margin-top: 8px;
}

.login-box {
  width: 380px;
  background: rgba(15, 20, 40, 0.9);
  border: 1px solid rgba(74, 158, 255, 0.2);
  border-radius: 12px;
  padding: 36px 32px;
  backdrop-filter: blur(10px);
}

.login-box h2 {
  text-align: center;
  color: #fff;
  font-size: 24px;
  margin: 0 0 28px;
  font-weight: 600;
}

.form-group {
  margin-bottom: 16px;
}

.form-group input {
  width: 100%;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  color: #fff;
  font-size: 15px;
  outline: none;
  transition: border-color 0.3s;
  box-sizing: border-box;
}

.form-group input:focus {
  border-color: #4a9eff;
  background: rgba(74, 158, 255, 0.06);
}

.form-group input::placeholder {
  color: #5a6a7a;
}

.error-msg {
  color: #ff4a4a;
  font-size: 13px;
  margin-bottom: 12px;
  text-align: center;
}

.btn-login {
  width: 100%;
  padding: 13px;
  background: linear-gradient(135deg, #4a9eff, #357abd);
  border: none;
  border-radius: 8px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  letter-spacing: 4px;
}

.btn-login:hover:not(:disabled) {
  background: linear-gradient(135deg, #5ab0ff, #4a9eff);
  transform: translateY(-1px);
  box-shadow: 0 4px 15px rgba(74, 158, 255, 0.3);
}

.btn-login:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.login-footer {
  text-align: center;
  margin-top: 20px;
  color: #6a7a8a;
  font-size: 13px;
}

.login-footer a {
  color: #4a9eff;
  text-decoration: none;
  margin-left: 4px;
}

.login-footer a:hover {
  text-decoration: underline;
}

.login-bottom {
  position: absolute;
  bottom: 20px;
  color: #3a4a5a;
  font-size: 12px;
}
</style>
