import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ExtensionId } from '@/network/protocol'

export type MessageDirection = 'send' | 'recv'

export interface WsLogEntry {
    id: number
    timestamp: number
    direction: MessageDirection
    source: string
    extId: number
    cmdId: number
    seq: number
    routeKey: string
    data: any
    raw: any
}

const EXTENSION_NAMES: Record<number, string> = {
    [ExtensionId.SYSTEM]: 'SYSTEM',
    [ExtensionId.AUTH]: 'AUTH',
    [ExtensionId.MATCH]: 'MATCH',
    [ExtensionId.BATTLE]: 'BATTLE',
    [ExtensionId.ROOM]: 'ROOM',
    [ExtensionId.SOCIAL]: 'SOCIAL',
    [ExtensionId.SPECTATOR]: 'SPECTATOR',
}

const MAX_LOG_ENTRIES = 500

let nextId = 0

export const useDebugStore = defineStore('debug', () => {
    const logs = ref<WsLogEntry[]>([])
    const filterText = ref('')
    const filterDirection = ref<MessageDirection | ''>('')
    const autoScroll = ref(true)
    const isPanelOpen = ref(false)
    const expandedIds = ref<Set<number>>(new Set())

    const filteredLogs = computed(() => {
        let result = logs.value
        if (filterDirection.value) {
            result = result.filter(e => e.direction === filterDirection.value)
        }
        if (filterText.value.trim()) {
            const keyword = filterText.value.trim().toLowerCase()
            result = result.filter(e => {
                const extName = getExtName(e.extId)
                const dataStr = JSON.stringify(e.data).toLowerCase()
                return (
                    extName.toLowerCase().includes(keyword) ||
                    e.routeKey.includes(keyword) ||
                    dataStr.includes(keyword) ||
                    e.source.toLowerCase().includes(keyword)
                )
            })
        }
        return result
    })

    const sendCount = computed(() => logs.value.filter(e => e.direction === 'send').length)
    const recvCount = computed(() => logs.value.filter(e => e.direction === 'recv').length)

    function getExtName(extId: number): string {
        return EXTENSION_NAMES[extId] || `EXT_${extId}`
    }

    function logSend(source: string, extId: number, cmdId: number, seq: number, data: any) {
        addEntry({
            direction: 'send',
            source,
            extId,
            cmdId,
            seq,
            data,
        })
    }

    function logRecv(source: string, extId: number, cmdId: number, seq: number, data: any) {
        addEntry({
            direction: 'recv',
            source,
            extId,
            cmdId,
            seq,
            data,
        })
    }

    function addEntry(params: {
        direction: MessageDirection
        source: string
        extId: number
        cmdId: number
        seq: number
        data: any
    }) {
        const raw = {
            extId: params.extId,
            cmdId: params.cmdId,
            seq: params.seq,
            data: params.data,
        }
        const entry: WsLogEntry = {
            id: ++nextId,
            timestamp: Date.now(),
            direction: params.direction,
            source: params.source,
            extId: params.extId,
            cmdId: params.cmdId,
            seq: params.seq,
            routeKey: `${params.extId}:${params.cmdId}`,
            data: params.data,
            raw,
        }
        logs.value.push(entry)
        if (logs.value.length > MAX_LOG_ENTRIES) {
            logs.value.splice(0, logs.value.length - MAX_LOG_ENTRIES)
        }
    }

    function toggleExpand(id: number) {
        const s = new Set(expandedIds.value)
        if (s.has(id)) {
            s.delete(id)
        } else {
            s.add(id)
        }
        expandedIds.value = s
    }

    function isExpanded(id: number): boolean {
        return expandedIds.value.has(id)
    }

    function expandAll() {
        const s = new Set<number>()
        for (const entry of filteredLogs.value) {
            s.add(entry.id)
        }
        expandedIds.value = s
    }

    function collapseAll() {
        expandedIds.value = new Set()
    }

    function clearLogs() {
        logs.value = []
        expandedIds.value = new Set()
    }

    function togglePanel() {
        isPanelOpen.value = !isPanelOpen.value
    }

    return {
        logs,
        filterText,
        filterDirection,
        autoScroll,
        isPanelOpen,
        expandedIds,
        filteredLogs,
        sendCount,
        recvCount,
        getExtName,
        logSend,
        logRecv,
        toggleExpand,
        isExpanded,
        expandAll,
        collapseAll,
        clearLogs,
        togglePanel,
    }
})
