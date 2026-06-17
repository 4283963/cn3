<template>
  <div class="waveform-track">
    <div class="track-header">
      <div class="track-info">
        <span class="track-indicator" :class="{ active: isPlaying }"></span>
        <span class="track-name">{{ trackName }}</span>
        <span class="track-type">{{ trackTypeLabel }}</span>
      </div>
      <div class="track-controls">
        <button
          class="ctrl-btn toggle-btn"
          :class="{ active: isPlaying }"
          @click="togglePlay"
        >
          {{ isPlaying ? '⏸' : '▶' }}
        </button>
        <div class="volume-control">
          <span class="volume-icon">🔊</span>
          <input
            type="range"
            min="0"
            max="1"
            step="0.01"
            :value="volume"
            @input="onVolumeChange"
            class="volume-slider"
          />
        </div>
      </div>
    </div>
    <div class="waveform-container" ref="containerRef">
      <canvas ref="canvasRef" class="waveform-canvas"></canvas>
      <div class="waveform-glow" :style="{ opacity: glowOpacity }"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  trackId: {
    type: String,
    required: true
  },
  trackName: {
    type: String,
    default: '音轨'
  },
  trackType: {
    type: String,
    default: 'bgm'
  },
  amplitudes: {
    type: Array,
    default: () => []
  },
  isPlaying: {
    type: Boolean,
    default: false
  },
  volume: {
    type: Number,
    default: 0.6
  },
  color: {
    type: String,
    default: '#00d4ff'
  }
})

const emit = defineEmits(['toggle', 'volumeChange'])

const canvasRef = ref(null)
const containerRef = ref(null)
let animationId = null
let displayAmplitudes = []

const trackTypeLabel = computed(() => {
  const labels = {
    bgm: '背景音乐',
    ambient: '环境音',
    effect: '音效'
  }
  return labels[props.trackType] || props.trackType
})

const glowOpacity = computed(() => {
  if (!props.isPlaying || props.amplitudes.length === 0) return 0.1
  const avg = props.amplitudes.reduce((a, b) => a + Math.abs(b), 0) / props.amplitudes.length
  return Math.min(0.6, 0.1 + avg * 0.8)
})

function togglePlay() {
  emit('toggle', !props.isPlaying)
}

function onVolumeChange(e) {
  emit('volumeChange', parseFloat(e.target.value))
}

function drawWaveform() {
  const canvas = canvasRef.value
  const container = containerRef.value
  if (!canvas || !container) return

  const ctx = canvas.getContext('2d')
  const dpr = window.devicePixelRatio || 1
  const width = container.clientWidth
  const height = container.clientHeight

  if (canvas.width !== width * dpr || canvas.height !== height * dpr) {
    canvas.width = width * dpr
    canvas.height = height * dpr
    canvas.style.width = width + 'px'
    canvas.style.height = height + 'px'
    ctx.scale(dpr, dpr)
  }

  ctx.clearRect(0, 0, width, height)

  const barCount = 64
  const barWidth = (width - 20) / barCount - 2
  const centerY = height / 2

  if (displayAmplitudes.length === 0) {
    displayAmplitudes = new Array(barCount).fill(0)
  }

  for (let i = 0; i < barCount; i++) {
    let targetValue = 0
    if (props.isPlaying && props.amplitudes.length > 0) {
      const idx = Math.floor(i * props.amplitudes.length / barCount)
      targetValue = Math.abs(props.amplitudes[idx] || 0) * 2
    }

    displayAmplitudes[i] = displayAmplitudes[i] * 0.7 + targetValue * 0.3

    const barHeight = Math.max(2, displayAmplitudes[i] * height * 0.8)
    const x = 10 + i * (barWidth + 2)

    const gradient = ctx.createLinearGradient(0, centerY - barHeight / 2, 0, centerY + barHeight / 2)
    gradient.addColorStop(0, props.color)
    gradient.addColorStop(0.5, lightenColor(props.color, 30))
    gradient.addColorStop(1, props.color)

    ctx.fillStyle = gradient
    ctx.shadowColor = props.color
    ctx.shadowBlur = props.isPlaying ? 10 : 3

    const radius = barWidth / 2
    ctx.beginPath()
    roundRect(ctx, x, centerY - barHeight / 2, barWidth, barHeight, radius)
    ctx.fill()
  }

  ctx.shadowBlur = 0
  ctx.fillStyle = props.color
  ctx.globalAlpha = 0.3
  ctx.fillRect(0, centerY - 0.5, width, 1)
  ctx.globalAlpha = 1

  animationId = requestAnimationFrame(drawWaveform)
}

function roundRect(ctx, x, y, w, h, r) {
  if (h < r * 2) r = h / 2
  if (w < r * 2) r = w / 2
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

function lightenColor(color, percent) {
  const num = parseInt(color.replace('#', ''), 16)
  const amt = Math.round(2.55 * percent)
  const R = Math.min(255, (num >> 16) + amt)
  const G = Math.min(255, (num >> 8 & 0x00FF) + amt)
  const B = Math.min(255, (num & 0x0000FF) + amt)
  return '#' + (0x1000000 + R * 0x10000 + G * 0x100 + B).toString(16).slice(1)
}

let resizeObserver = null

onMounted(() => {
  if (containerRef.value) {
    resizeObserver = new ResizeObserver(() => {
      // Canvas will resize on next draw
    })
    resizeObserver.observe(containerRef.value)
  }
  drawWaveform()
})

onUnmounted(() => {
  if (animationId) {
    cancelAnimationFrame(animationId)
  }
  if (resizeObserver) {
    resizeObserver.disconnect()
  }
})
</script>

<style scoped>
.waveform-track {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  backdrop-filter: blur(10px);
  transition: all 0.3s ease;
}

.waveform-track:hover {
  border-color: rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.05);
}

.track-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.track-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.track-indicator {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #444;
  transition: all 0.3s ease;
}

.track-indicator.active {
  background: #00ff88;
  box-shadow: 0 0 10px #00ff88;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.track-name {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
}

.track-type {
  font-size: 12px;
  padding: 3px 8px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  color: #aaa;
}

.track-controls {
  display: flex;
  align-items: center;
  gap: 16px;
}

.ctrl-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  transition: all 0.2s ease;
}

.ctrl-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: scale(1.1);
}

.ctrl-btn.active {
  background: rgba(0, 212, 255, 0.3);
  color: #00d4ff;
}

.volume-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.volume-icon {
  font-size: 14px;
}

.volume-slider {
  width: 80px;
  height: 4px;
  -webkit-appearance: none;
  appearance: none;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 2px;
  outline: none;
  cursor: pointer;
}

.volume-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #00d4ff;
  cursor: pointer;
  box-shadow: 0 0 8px rgba(0, 212, 255, 0.5);
  transition: transform 0.2s ease;
}

.volume-slider::-webkit-slider-thumb:hover {
  transform: scale(1.2);
}

.waveform-container {
  position: relative;
  height: 80px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 8px;
  overflow: hidden;
}

.waveform-canvas {
  width: 100%;
  height: 100%;
  display: block;
}

.waveform-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 80%;
  height: 60%;
  background: radial-gradient(ellipse, rgba(0, 212, 255, 0.3) 0%, transparent 70%);
  pointer-events: none;
  transition: opacity 0.1s ease;
}
</style>
