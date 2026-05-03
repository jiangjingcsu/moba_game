<template>
  <div v-if="isDebugEnabled" class="debug-wrapper">
    <button class="debug-toggle" @click="debugStore.togglePanel" :title="debugStore.isPanelOpen ? '关闭调试面板' : '打开调试面板'">
      <span class="toggle-icon">{{ debugStore.isPanelOpen ? '▶' : '◀' }}</span>
      <span class="toggle-label">WS</span>
    </button>

    <transition name="slide">
      <div v-if="debugStore.isPanelOpen" class="debug-sidebar">
        <div class="sidebar-header">
          <h3>WebSocket 调试</h3>
          <div class="header-stats">
            <span class="stat-send">↑{{ debugStore.sendCount }}</span>
            <span class="stat-recv">↓{{ debugStore.recvCount }}</span>
          </div>
          <div class="header-actions">
            <button class="btn-icon" @click="debugStore.expandAll" title="全部展开">⊞</button>
            <button class="btn-icon" @click="debugStore.collapseAll" title="全部折叠">⊟</button>
            <button class="btn-icon" @click="debugStore.clearLogs" title="清空日志">🗑</button>
            <button class="btn-icon" @click="debugStore.togglePanel" title="关闭">✕</button>
          </div>
        </div>

        <div class="sidebar-filters">
          <input
            v-model="debugStore.filterText"
            class="filter-input"
            type="text"
            placeholder="搜索消息..."
          />
          <div class="filter-tabs">
            <button
              :class="{ active: debugStore.filterDirection === '' }"
              @click="debugStore.filterDirection = ''"
            >全部</button>
            <button
              :class="{ active: debugStore.filterDirection === 'send' }"
              @click="debugStore.filterDirection = 'send'"
            >发送</button>
            <button
              :class="{ active: debugStore.filterDirection === 'recv' }"
              @click="debugStore.filterDirection = 'recv'"
            >接收</button>
          </div>
        </div>

        <div class="sidebar-logs" ref="logsContainer">
          <div
            v-for="entry in debugStore.filteredLogs"
            :key="entry.id"
            class="log-entry"
            :class="[`dir-${entry.direction}`, { expanded: debugStore.isExpanded(entry.id) }]"
          >
            <div class="log-summary" @click="debugStore.toggleExpand(entry.id)">
              <span class="log-direction">{{ entry.direction === 'send' ? '↑' : '↓' }}</span>
              <span class="log-source">[{{ entry.source }}]</span>
              <span class="log-route">{{ debugStore.getExtName(entry.extId) }}:{{ entry.cmdId }}</span>
              <span class="log-seq">seq={{ entry.seq }}</span>
              <span class="log-time">{{ formatTime(entry.timestamp) }}</span>
            </div>
            <div v-if="debugStore.isExpanded(entry.id)" class="log-detail" @click.stop>
              <pre>{{ formatData(entry.raw) }}</pre>
            </div>
          </div>
          <div v-if="debugStore.filteredLogs.length === 0" class="log-empty">
            暂无消息
          </div>
        </div>

        <div class="sidebar-footer">
          <label class="auto-scroll-label">
            <input type="checkbox" v-model="debugStore.autoScroll" />
            自动滚动
          </label>
          <span class="log-count">{{ debugStore.filteredLogs.length }} / {{ debugStore.logs.length }}</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { watch, nextTick, ref } from 'vue'
import { useDebugStore } from '@/stores/debug'
import { isDebugEnabled } from '@/config/debug'

const debugStore = useDebugStore()
const logsContainer = ref<HTMLElement | null>(null)

function formatTime(ts: number): string {
  const d = new Date(ts)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}.${d.getMilliseconds().toString().padStart(3, '0')}`
}

function formatData(data: any): string {
  try {
    return JSON.stringify(data, null, 2)
  } catch {
    return String(data)
  }
}

watch(
  () => debugStore.filteredLogs.length,
  async () => {
    if (debugStore.autoScroll && logsContainer.value) {
      await nextTick()
      logsContainer.value.scrollTop = logsContainer.value.scrollHeight
    }
  },
)
</script>

<style scoped>
.debug-wrapper {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 9999;
  height: 100%;
  display: flex;
  pointer-events: none;
}

.debug-wrapper > * {
  pointer-events: auto;
}

.debug-toggle {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 36px;
  background: rgba(0, 0, 0, 0.75);
  border: 1px solid rgba(74, 158, 255, 0.3);
  border-right: none;
  border-radius: 6px 0 0 6px;
  color: #4a9eff;
  cursor: pointer;
  padding: 8px 0;
  transition: background 0.2s;
}

.debug-toggle:hover {
  background: rgba(74, 158, 255, 0.15);
}

.toggle-icon {
  font-size: 12px;
  line-height: 1;
}

.toggle-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
}

.debug-sidebar {
  width: 420px;
  height: 100%;
  background: rgba(10, 14, 26, 0.95);
  border-left: 1px solid rgba(74, 158, 255, 0.2);
  display: flex;
  flex-direction: column;
  backdrop-filter: blur(8px);
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(0, 0, 0, 0.3);
}

.sidebar-header h3 {
  flex: 1;
  font-size: 15px;
  color: #4a9eff;
  margin: 0;
  white-space: nowrap;
}

.header-stats {
  display: flex;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
}

.stat-send { color: #fbbf24; }
.stat-recv { color: #34d399; }

.header-actions {
  display: flex;
  gap: 4px;
}

.btn-icon {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  color: #8a9bae;
  cursor: pointer;
  font-size: 14px;
  padding: 0;
  transition: all 0.15s;
}

.btn-icon:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.sidebar-filters {
  padding: 8px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-input {
  width: 100%;
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  color: #ccc;
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
}

.filter-input:focus {
  border-color: rgba(74, 158, 255, 0.5);
}

.filter-input::placeholder {
  color: #555;
}

.filter-tabs {
  display: flex;
  gap: 4px;
}

.filter-tabs button {
  flex: 1;
  padding: 5px 0;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 4px;
  color: #6a7a8a;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.filter-tabs button:hover {
  background: rgba(255, 255, 255, 0.08);
}

.filter-tabs button.active {
  background: rgba(74, 158, 255, 0.15);
  border-color: rgba(74, 158, 255, 0.4);
  color: #4a9eff;
}

.sidebar-logs {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
}

.sidebar-logs::-webkit-scrollbar {
  width: 4px;
}

.sidebar-logs::-webkit-scrollbar-track {
  background: transparent;
}

.sidebar-logs::-webkit-scrollbar-thumb {
  background: rgba(74, 158, 255, 0.3);
  border-radius: 2px;
}

.log-entry {
  padding: 4px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.03);
  transition: background 0.1s;
}

.log-entry:hover {
  background: rgba(255, 255, 255, 0.04);
}

.log-entry.dir-send {
  border-left: 2px solid #fbbf24;
}

.log-entry.dir-recv {
  border-left: 2px solid #34d399;
}

.log-entry.expanded {
  background: rgba(255, 255, 255, 0.06);
}

.log-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
  overflow: hidden;
  cursor: pointer;
}

.log-direction {
  font-weight: 700;
  flex-shrink: 0;
}

.dir-send .log-direction { color: #fbbf24; }
.dir-recv .log-direction { color: #34d399; }

.log-source {
  color: #6a7a8a;
  flex-shrink: 0;
}

.log-route {
  color: #4a9eff;
  font-weight: 600;
  flex-shrink: 0;
}

.log-seq {
  color: #8b5cf6;
  font-size: 12px;
  flex-shrink: 0;
}

.log-time {
  color: #4a5a6a;
  margin-left: auto;
  font-size: 12px;
  flex-shrink: 0;
}

.log-detail {
  margin-top: 6px;
  padding: 8px 10px;
  background: rgba(0, 0, 0, 0.4);
  border-radius: 4px;
  overflow-x: auto;
  cursor: default;
  user-select: text;
}

.log-detail pre {
  margin: 0;
  color: #b0c4de;
  font-size: 15px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.log-empty {
  padding: 20px;
  text-align: center;
  color: #4a5a6a;
  font-size: 12px;
}

.sidebar-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(0, 0, 0, 0.2);
}

.auto-scroll-label {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #6a7a8a;
  font-size: 13px;
  cursor: pointer;
}

.auto-scroll-label input {
  accent-color: #4a9eff;
}

.log-count {
  color: #4a5a6a;
  font-size: 13px;
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.25s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}
</style>
