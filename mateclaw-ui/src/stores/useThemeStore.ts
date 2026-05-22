import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'
export type ThemePreset = 'default' | 'blue'

const STORAGE_KEY = 'mateclaw-theme'
const PRESET_STORAGE_KEY = 'mateclaw-theme-preset'

export const useThemeStore = defineStore('theme', () => {
  function getInitialMode(): ThemeMode {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
    } catch { /* ignore */ }
    return 'system'
  }

  function resolveIsDark(mode: ThemeMode): boolean {
    if (mode === 'dark') return true
    if (mode === 'light') return false
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
  }

  function getInitialPreset(): ThemePreset {
    try {
      const stored = localStorage.getItem(PRESET_STORAGE_KEY)
      if (stored === 'default' || stored === 'blue') return stored
    } catch { /* ignore */ }
    return 'blue'
  }

  const mode = ref<ThemeMode>(getInitialMode())
  const isDark = ref(resolveIsDark(mode.value))
  const preset = ref<ThemePreset>(getInitialPreset())

  function setMode(newMode: ThemeMode) {
    mode.value = newMode
    isDark.value = resolveIsDark(newMode)
    try { localStorage.setItem(STORAGE_KEY, newMode) } catch { /* ignore */ }
  }

  function setPreset(newPreset: ThemePreset) {
    preset.value = newPreset
    try { localStorage.setItem(PRESET_STORAGE_KEY, newPreset) } catch { /* ignore */ }
  }

  function toggle() {
    setMode(isDark.value ? 'light' : 'dark')
  }

  // Apply .dark class to <html>
  watch(isDark, (dark) => {
    document.documentElement.classList.toggle('dark', dark)
  }, { immediate: true })

  watch(preset, (value) => {
    document.documentElement.dataset.themePreset = value
  }, { immediate: true })

  // Listen for OS-level preference changes when in 'system' mode
  const mql = window.matchMedia?.('(prefers-color-scheme: dark)')
  mql?.addEventListener('change', () => {
    if (mode.value === 'system') {
      isDark.value = mql.matches
    }
  })

  return { mode, isDark, preset, setMode, setPreset, toggle }
})
