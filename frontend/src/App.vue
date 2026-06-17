<template>
  <div class="app-container">
    <header class="app-header">
      <div class="header-content">
        <div class="logo">
          <span class="logo-icon">🎭</span>
          <h1 class="title">声控特效控制台</h1>
        </div>
        <div class="header-status">
          <span class="status-dot" :class="{ connected: wsConnected }"></span>
          <span class="status-text">{{ wsConnected ? '已连接' : '未连接' }}</span>
        </div>
      </div>
    </header>

    <main class="main-content">
      <section class="scripts-section">
        <h2 class="section-title">🎬 剧本列表</h2>
        <div class="scripts-grid">
          <div
            v-for="script in scripts"
            :key="script.id"
            class="script-card"
            :class="{ active: currentScriptId === script.id }"
            @click="selectScript(script)"
          >
            <div class="script-icon">{{ getCategoryIcon(script.category) }}</div>
            <div class="script-info">
              <h3 class="script-name">{{ script.name }}</h3>
              <p class="script-desc">{{ script.description }}</p>
              <div class="script-meta">
                <span class="category-tag">{{ script.category }}</span>
                <span class="track-count">{{ script.tracks?.length || 0 }} 音轨</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="control-section">
        <h2 class="section-title">🎛️ 播放控制</h2>
        
        <div class="control-bar">
          <button
            class="play-btn main-play"
            :class="{ playing: isPlaying }"
            :disabled="!currentScriptId"
            @click="togglePlayback"
          >
            <span class="play-icon">{{ isPlaying ? '⏸' : '▶' }}</span>
            <span>{{ isPlaying ? '暂停' : '播放' }}</span>
          </button>
          
          <button
            class="ctrl-btn stop-btn"
            :disabled="!currentScriptId"
            @click="stopPlayback"
          >
            ⏹ 停止
          </button>

          <div class="current-script" v-if="currentScript">
            <span class="label">当前剧本:</span>
            <span class="name">{{ currentScript.name }}</span>
          </div>
        </div>
      </section>

      <section class="waveform-section">
        <h2 class="section-title">📊 音轨波形</h2>
        
        <div class="waveform-list">
          <WaveformTrack
            v-for="track in displayTracks"
            :key="track.id"
            :track-id="track.id"
            :track-name="track.name"
            :track-type="track.type"
            :amplitudes="waveformData[track.id] || []"
            :is-playing="track.playing && isPlaying"
            :volume="track.volume"
            :color="getTrackColor(track.type)"
            @toggle="(play) => toggleTrack(track.id, play)"
            @volume-change="(vol) => setTrackVolume(track.id, vol)"
          />
        </div>

        <div v-if="displayTracks.length === 0" class="empty-state">
          <div class="empty-icon">🎵</div>
          <p>选择一个剧本开始播放</p>
        </div>
      </section>
    </main>

    <footer class="app-footer">
      <p>剧本杀声控特效系统 · 房间音响控制台</p>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, reactive } from 'vue'
import WaveformTrack from './components/WaveformTrack.vue'
import { scriptApi, playbackApi } from './api/audio'

const scripts = ref([])
const currentScriptId = ref(null)
const isPlaying = ref(false)
const wsConnected = ref(false)
const waveformData = reactive({})

let ws = null
let reconnectTimer = null

const currentScript = computed(() => {
  return scripts.value.find(s => s.id === currentScriptId.value)
})

const displayTracks = computed(() => {
  if (!currentScript.value) return []
  return currentScript.value.tracks || []
})

function getCategoryIcon(category) {
  const icons = {
    '恐怖': '👻',
    '悬疑': '🔍',
    '情感': '💕',
    '欢乐': '🎉'
  }
  return icons[category] || '🎭'
}

function getTrackColor(type) {
  const colors = {
    bgm: '#00d4ff',
    ambient: '#ff6b9d',
    effect: '#ffd93d'
  }
  return colors[type] || '#00d4ff'
}

async function loadScripts() {
  try {
    const res = await scriptApi.getAllScripts()
    scripts.value = res.data
  } catch (e) {
    console.error('加载剧本列表失败:', e)
  }
}

function selectScript(script) {
  currentScriptId.value = script.id
}

async function playScript() {
  if (!currentScriptId.value) return
  try {
    const res = await playbackApi.playScript(currentScriptId.value)
    updatePlaybackState(res.data)
    isPlaying.value = true
    updateTrackStates(res.data.tracks)
  } catch (e) {
    console.error('播放失败:', e)
  }
}

async function pausePlayback() {
  try {
    const res = await playbackApi.pause()
    isPlaying.value = false
    updatePlaybackState(res.data)
  } catch (e) {
    console.error('暂停失败:', e)
  }
}

async function stopPlayback() {
  try {
    const res = await playbackApi.stop()
    isPlaying.value = false
    updatePlaybackState(res.data)
    Object.keys(waveformData).forEach(key => {
      waveformData[key] = []
    })
  } catch (e) {
    console.error('停止失败:', e)
  }
}

function togglePlayback() {
  if (isPlaying.value) {
    pausePlayback()
  } else {
    playScript()
  }
}

async function toggleTrack(trackId, play) {
  try {
    const res = await playbackApi.toggleTrack(trackId, play)
    updateTrackStates(res.data.tracks)
  } catch (e) {
    console.error('切换音轨失败:', e)
  }
}

async function setTrackVolume(trackId, volume) {
  try {
    await playbackApi.setVolume(trackId, volume)
    const track = currentScript.value?.tracks?.find(t => t.id === trackId)
    if (track) {
      track.volume = volume
    }
  } catch (e) {
    console.error('设置音量失败:', e)
  }
}

function updatePlaybackState(state) {
  if (state.scriptId) {
    currentScriptId.value = state.scriptId
  }
  isPlaying.value = state.playing
}

function updateTrackStates(tracks) {
  if (!currentScript.value || !tracks) return
  
  tracks.forEach(trackState => {
    const track = currentScript.value.tracks.find(t => t.id === trackState.trackId)
    if (track) {
      track.playing = trackState.playing
      track.volume = trackState.volume
    }
  })
}

function connectWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const wsUrl = `${protocol}//${host}/ws/waveform`

  try {
    ws = new WebSocket(wsUrl)

    ws.onopen = () => {
      console.log('WebSocket 连接已建立')
      wsConnected.value = true
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type === 'waveform' && data.data) {
          data.data.forEach(wave => {
            waveformData[wave.trackId] = wave.amplitudes
          })
        }
      } catch (e) {
        console.error('解析 WebSocket 消息失败:', e)
      }
    }

    ws.onclose = () => {
      console.log('WebSocket 连接已关闭')
      wsConnected.value = false
      scheduleReconnect()
    }

    ws.onerror = (error) => {
      console.error('WebSocket 错误:', error)
      wsConnected.value = false
    }
  } catch (e) {
    console.error('创建 WebSocket 失败:', e)
    scheduleReconnect()
  }
}

function scheduleReconnect() {
  if (reconnectTimer) return
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connectWebSocket()
  }, 3000)
}

onMounted(() => {
  loadScripts()
  connectWebSocket()
})

onUnmounted(() => {
  if (ws) {
    ws.close()
  }
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
  }
})
</script>

<style scoped>
.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding: 16px 32px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-content {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  font-size: 28px;
}

.title {
  font-size: 22px;
  font-weight: 700;
  background: linear-gradient(135deg, #00d4ff, #ff6b9d);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4757;
  transition: all 0.3s ease;
}

.status-dot.connected {
  background: #00ff88;
  box-shadow: 0 0 10px #00ff88;
}

.status-text {
  font-size: 13px;
  color: #aaa;
}

.main-content {
  flex: 1;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
  padding: 32px;
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #fff;
}

.scripts-section {
}

.scripts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.script-card {
  background: rgba(255, 255, 255, 0.03);
  border: 2px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.script-card:hover {
  border-color: rgba(0, 212, 255, 0.3);
  background: rgba(0, 212, 255, 0.05);
  transform: translateY(-2px);
}

.script-card.active {
  border-color: #00d4ff;
  background: rgba(0, 212, 255, 0.1);
  box-shadow: 0 0 20px rgba(0, 212, 255, 0.2);
}

.script-icon {
  font-size: 36px;
  flex-shrink: 0;
}

.script-info {
  flex: 1;
}

.script-name {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  margin-bottom: 6px;
}

.script-desc {
  font-size: 13px;
  color: #888;
  line-height: 1.5;
  margin-bottom: 10px;
}

.script-meta {
  display: flex;
  gap: 10px;
}

.category-tag {
  font-size: 11px;
  padding: 3px 8px;
  background: rgba(255, 107, 157, 0.2);
  color: #ff6b9d;
  border-radius: 4px;
}

.track-count {
  font-size: 11px;
  color: #666;
}

.control-section {
}

.control-bar {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.play-btn {
  padding: 12px 32px;
  border: none;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.3s ease;
}

.main-play {
  background: linear-gradient(135deg, #00d4ff, #0088ff);
  color: #fff;
  box-shadow: 0 4px 20px rgba(0, 212, 255, 0.3);
}

.main-play:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 25px rgba(0, 212, 255, 0.4);
}

.main-play.playing {
  background: linear-gradient(135deg, #ffd93d, #ff9500);
  box-shadow: 0 4px 20px rgba(255, 217, 61, 0.3);
}

.play-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.play-icon {
  font-size: 16px;
}

.stop-btn {
  padding: 12px 24px;
  background: rgba(255, 71, 87, 0.2);
  border: 1px solid rgba(255, 71, 87, 0.3);
  color: #ff4757;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.stop-btn:hover:not(:disabled) {
  background: rgba(255, 71, 87, 0.3);
}

.stop-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.current-script {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.current-script .label {
  font-size: 13px;
  color: #888;
}

.current-script .name {
  font-size: 14px;
  font-weight: 600;
  color: #00d4ff;
}

.waveform-section {
  flex: 1;
}

.waveform-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #666;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.app-footer {
  text-align: center;
  padding: 20px;
  color: #555;
  font-size: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}
</style>
