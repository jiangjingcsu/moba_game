<template>
  <div class="hero-select-screen">
    <div class="hero-select-bg"></div>
    <div class="hero-select-content">
      <div class="hs-header">
        <button class="btn-back" @click="goBack">← 返回大厅</button>
        <h2>选择英雄</h2>
        <div class="mode-badge">{{ currentMode?.icon }} {{ currentMode?.name }}</div>
      </div>

      <div class="hs-main">
        <div class="hero-grid">
          <div
            v-for="hero in heroList"
            :key="hero.id"
            class="hero-card"
            :class="{ selected: gameStore.selectedHeroId === hero.id }"
            @click="selectHero(hero)"
          >
            <div class="hero-avatar">
              <img :src="roleAvatars[hero.role]" :alt="hero.name" class="hero-avatar-img" />
            </div>
            <div class="hero-name">{{ hero.name }}</div>
            <div class="hero-role">{{ hero.roleName }}</div>
          </div>
        </div>

        <div class="hero-detail" v-if="selectedHero">
          <div class="detail-avatar">
            <img :src="roleAvatars[selectedHero.role]" :alt="selectedHero.name" class="detail-avatar-img" />
          </div>
          <h3>{{ selectedHero.name }}</h3>
          <p class="detail-title">{{ selectedHero.title }}</p>
          <p class="detail-desc">{{ selectedHero.description }}</p>
          <div class="detail-stats">
            <div class="stat-row"><span>攻击</span><div class="stat-bar"><div class="stat-fill" :style="{ width: selectedHero.stats.attack + '%' }"></div></div></div>
            <div class="stat-row"><span>防御</span><div class="stat-bar"><div class="stat-fill defense" :style="{ width: selectedHero.stats.defense + '%' }"></div></div></div>
            <div class="stat-row"><span>法术</span><div class="stat-bar"><div class="stat-fill magic" :style="{ width: selectedHero.stats.magic + '%' }"></div></div></div>
            <div class="stat-row"><span>难度</span><div class="stat-bar"><div class="stat-fill difficulty" :style="{ width: selectedHero.stats.difficulty + '%' }"></div></div></div>
          </div>
          <div class="detail-skills">
            <div v-for="skill in selectedHero.skills" :key="skill.id" class="skill-item">
              <span class="skill-key">{{ skill.id === 'passive' ? '被动' : skill.id.toUpperCase() }}</span>
              <span class="skill-name">{{ skill.name }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="hs-footer">
        <div class="selected-hero-info" v-if="selectedHero">
          {{ selectedHero.roleIcon }} {{ selectedHero.name }} - {{ selectedHero.title }}
        </div>
        <button
          class="btn-confirm"
          :disabled="!gameStore.selectedHeroId"
          @click="confirmHero"
        >
          {{ gameStore.selectedHeroId ? '确认选择' : '请选择英雄' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useGameStore } from '@/stores/game'
import { heroDefinitions } from '@/data/heroes'
import { HeroRole } from '@/types/game'

const gameStore = useGameStore()

const roleIcons: Record<string, string> = {
  warrior: '⚔️', mage: '✨', assassin: '🗡️', marksman: '🏹', support: '💚', tank: '🛡️'
}
const roleNames: Record<string, string> = {
  warrior: '战士', mage: '法师', assassin: '刺客', marksman: '射手', support: '辅助', tank: '坦克'
}
const roleColors: Record<string, string> = {
  warrior: 'linear-gradient(135deg, #e74c3c, #c0392b)',
  mage: 'linear-gradient(135deg, #9b59b6, #8e44ad)',
  assassin: 'linear-gradient(135deg, #2ecc71, #27ae60)',
  marksman: 'linear-gradient(135deg, #f39c12, #e67e22)',
  support: 'linear-gradient(135deg, #1abc9c, #16a085)',
  tank: 'linear-gradient(135deg, #3498db, #2980b9)',
}
const roleAvatars: Record<string, string> = {
  warrior: '/assets/avatars/warrior_avatar.png',
  mage: '/assets/avatars/mage_avatar.png',
  assassin: '/assets/avatars/assassin_avatar.png',
  marksman: '/assets/avatars/marksman_avatar.png',
  support: '/assets/avatars/support_avatar.png',
  tank: '/assets/avatars/tank_avatar.png',
}

const heroList = computed(() => heroDefinitions.map(h => ({
  id: h.id,
  name: h.name,
  title: h.title,
  role: h.role,
  roleName: roleNames[h.role] || h.role,
  roleIcon: roleIcons[h.role] || '👤',
  color: roleColors[h.role] || 'linear-gradient(135deg, #666, #444)',
  description: h.description || '',
  stats: {
    attack: Math.min(100, (h.baseStats.attackDamage || h.baseStats.attack || 60) / 1.2),
    defense: Math.min(100, (h.baseStats.armor || 30) / 0.6),
    magic: Math.min(100, (h.baseStats.abilityPower || 0) / 1.2 + 30),
    difficulty: h.difficulty || 50,
  },
  skills: h.skills || [],
})))

const selectedHero = computed(() => heroList.value.find(h => h.id === gameStore.selectedHeroId))
const currentMode = computed(() => gameStore.gameModes.find(m => m.id === gameStore.selectedGameMode))

function selectHero(hero: any) {
  gameStore.setSelectedHeroId(hero.id)
}

function confirmHero() {
  if (!gameStore.selectedHeroId) return
  gameStore.setGameState('matching')
}

function goBack() {
  gameStore.setGameState('lobby')
}
</script>

<style scoped>
.hero-select-screen {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.hero-select-bg {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: url('/assets/ui/bg_hero_select.png') center/cover no-repeat;
}

.hero-select-bg::after {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: linear-gradient(180deg, rgba(10, 14, 26, 0.9) 0%, rgba(17, 24, 39, 0.85) 50%, rgba(15, 23, 42, 0.9) 100%);
}

.hero-select-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.hs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: rgba(0, 0, 0, 0.3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.hs-header h2 { color: #fff; margin: 0; font-size: 20px; }

.btn-back {
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  color: #8a9bae;
  cursor: pointer;
  font-size: 13px;
}
.btn-back:hover { background: rgba(255, 255, 255, 0.1); }

.mode-badge {
  color: #fbbf24;
  font-size: 14px;
  font-weight: 600;
}

.hs-main {
  flex: 1;
  display: flex;
  padding: 20px;
  gap: 20px;
  overflow: hidden;
}

.hero-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
  overflow-y: auto;
  align-content: start;
}

.hero-card {
  background: rgba(255, 255, 255, 0.04);
  border: 2px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 14px 10px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
}
.hero-card:hover { background: rgba(255, 255, 255, 0.08); border-color: rgba(74, 158, 255, 0.3); }
.hero-card.selected {
  background: rgba(74, 158, 255, 0.1);
  border-color: #4a9eff;
  box-shadow: 0 0 15px rgba(74, 158, 255, 0.2);
}

.hero-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 8px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.06);
}

.hero-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.hero-name { color: #fff; font-size: 13px; font-weight: 600; margin-bottom: 2px; }
.hero-role { color: #6a7a8a; font-size: 11px; }

.hero-detail {
  width: 320px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.detail-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.06);
  border: 2px solid rgba(74, 158, 255, 0.3);
}

.detail-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.hero-detail h3 { color: #fff; font-size: 22px; margin: 0 0 4px; }
.detail-title { color: #fbbf24; font-size: 13px; margin: 0 0 12px; }
.detail-desc { color: #8a9bae; font-size: 12px; text-align: center; margin: 0 0 16px; line-height: 1.6; }

.detail-stats { width: 100%; margin-bottom: 16px; }
.stat-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.stat-row span { color: #6a7a8a; font-size: 12px; width: 36px; }
.stat-bar { flex: 1; height: 6px; background: rgba(255, 255, 255, 0.08); border-radius: 3px; overflow: hidden; }
.stat-fill { height: 100%; background: linear-gradient(90deg, #e74c3c, #ff6b6b); border-radius: 3px; transition: width 0.3s; }
.stat-fill.defense { background: linear-gradient(90deg, #3498db, #5dade2); }
.stat-fill.magic { background: linear-gradient(90deg, #9b59b6, #bb8fce); }
.stat-fill.difficulty { background: linear-gradient(90deg, #f39c12, #f7dc6f); }

.detail-skills { width: 100%; }
.skill-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}
.skill-key {
  padding: 2px 8px;
  background: rgba(74, 158, 255, 0.15);
  border-radius: 4px;
  color: #4a9eff;
  font-size: 11px;
  font-weight: 600;
}
.skill-name { color: #ccc; font-size: 13px; }

.hs-footer {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
  padding: 14px 24px;
  background: rgba(0, 0, 0, 0.3);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.selected-hero-info { color: #8a9bae; font-size: 14px; }

.btn-confirm {
  padding: 12px 50px;
  background: linear-gradient(135deg, #4a9eff, #357abd);
  border: none;
  border-radius: 10px;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s;
  letter-spacing: 2px;
}
.btn-confirm:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(74, 158, 255, 0.3); }
.btn-confirm:disabled { background: rgba(255, 255, 255, 0.06); color: #4a4a4a; cursor: not-allowed; }
</style>
