import axios from 'axios'

const api = axios.create({
  baseURL: '/api/audio',
  timeout: 10000
})

export const scriptApi = {
  getAllScripts() {
    return api.get('/scripts')
  },

  getScriptById(id) {
    return api.get(`/scripts/${id}`)
  }
}

export const playbackApi = {
  playScript(scriptId) {
    return api.post(`/play/${scriptId}`)
  },

  pause() {
    return api.post('/pause')
  },

  resume() {
    return api.post('/resume')
  },

  stop() {
    return api.post('/stop')
  },

  getState() {
    return api.get('/state')
  },

  setVolume(trackId, volume) {
    return api.post(`/tracks/${trackId}/volume`, { volume })
  },

  toggleTrack(trackId, play) {
    return api.post(`/tracks/${trackId}/toggle`, { play })
  }
}

export default api
