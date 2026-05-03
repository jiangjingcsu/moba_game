import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { initNetwork } from './network'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

initNetwork()

app.mount('#app')
