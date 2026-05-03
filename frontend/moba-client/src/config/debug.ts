import { ref } from 'vue'

const DEBUG_KEY = 'moba_debug_mode'

function readDebugFromEnv(): boolean {
    return import.meta.env.VITE_DEBUG === 'true'
}

function readDebugFromStorage(): boolean {
    return localStorage.getItem(DEBUG_KEY) === 'true'
}

function writeDebugToStorage(enabled: boolean): void {
    localStorage.setItem(DEBUG_KEY, String(enabled))
}

const initialValue = readDebugFromEnv() || readDebugFromStorage()

export const isDebugEnabled = ref<boolean>(initialValue)

export const DebugConfig = {
    get enabled(): boolean {
        return isDebugEnabled.value
    },

    set enabled(value: boolean) {
        isDebugEnabled.value = value
        writeDebugToStorage(value)
    },

    toggle(): boolean {
        const next = !isDebugEnabled.value
        isDebugEnabled.value = next
        writeDebugToStorage(next)
        return next
    },
}
