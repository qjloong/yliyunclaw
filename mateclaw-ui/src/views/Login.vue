<template>
  <div class="login-page">
    <div class="login-center">
      <div class="login-logo">
        <img src="/logo/mateclaw_logo_s.png" alt="Meta Y" class="logo-image" />
        <img src="/logo/text_logo.png" alt="Meta Y" class="logo-title-image" />
      </div>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="input-wrap">
          <input
            v-model="form.username"
            type="text"
            class="form-input"
            :placeholder="t('login.placeholders.username')"
            :aria-label="t('login.fields.username')"
            autocomplete="username"
            required
          />
        </div>

        <div class="input-wrap">
          <input
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            class="form-input form-input--has-eye"
            :placeholder="t('login.placeholders.password')"
            :aria-label="t('login.fields.password')"
            autocomplete="current-password"
            required
          />
          <button type="button" class="eye-btn" @click="showPassword = !showPassword">
            <svg v-if="!showPassword" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
              <line x1="1" y1="1" x2="23" y2="23"/>
            </svg>
          </button>
        </div>

        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

        <button type="submit" class="login-btn" :disabled="loading">
          <span v-if="!loading">{{ t('login.signIn') }}</span>
          <span v-else class="loading-dots">
            <span></span><span></span><span></span>
          </span>
        </button>
      </form>

    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/index'
import { saveAuthSession } from '@/utils/auth'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const loading = ref(false)
const showPassword = ref(false)
const errorMsg = ref('')
const form = reactive({ username: '', password: '' })

async function handleLogin() {
  if (!form.username || !form.password) return
  loading.value = true
  errorMsg.value = ''
  try {
    const res: any = await authApi.login(form)
    const data = res.data || res
    saveAuthSession({
      token: data.token,
      userId: data.id || '1',
      username: data.username || form.username,
      role: data.role || 'user',
    })
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/'
    router.push(redirect)
  } catch (e: any) {
    errorMsg.value = typeof e === 'string' ? e : t('login.failed')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #f3f8ff 0%, #e2eeff 100%);
  padding: 24px;
}

:root.dark .login-page,
html.dark .login-page {
  background: linear-gradient(160deg, var(--mc-bg) 0%, #0b1220 100%);
}

.login-center {
  width: 100%;
  max-width: 380px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 40px;
  animation: fadeUp 0.6s ease-out both;
}

/* Logo */
.login-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-bottom: 20px;
}

.logo-image {
  display: block;
  width: 62px;
  height: 62px;
  object-fit: contain;
  filter: drop-shadow(0 8px 24px rgba(59, 136, 255, 0.28));
  animation: breathe 3.5s ease-in-out infinite;
  flex: 0 0 auto;
}

.logo-title-image {
  display: block;
  width: min(188px, 58vw);
  height: auto;
  object-fit: contain;
  flex: 0 1 auto;
}

/* Form */
.login-form {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.form-input {
  width: 100%;
  padding: 14px 16px;
  border: 1.5px solid var(--mc-border);
  border-radius: 12px;
  font-size: 15px;
  color: var(--mc-text-primary);
  background: var(--mc-bg-sunken);
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
}

.form-input--has-eye {
  padding-right: 44px;
}

.form-input:focus {
  border-color: var(--mc-primary);
  background: var(--mc-bg-elevated);
  box-shadow: 0 0 0 3px rgba(59, 136, 255, 0.12);
}

.eye-btn {
  position: absolute;
  right: 12px;
  width: 28px;
  height: 28px;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--mc-text-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
}

.eye-btn:hover {
  color: var(--mc-primary);
}

/* Error */
.error-msg {
  padding: 10px 14px;
  background: var(--mc-danger-bg);
  border: 1px solid var(--mc-danger);
  border-radius: 10px;
  font-size: 13px;
  color: var(--mc-danger);
}

/* Button */
.login-btn {
  width: 100%;
  padding: 12px;
  background: linear-gradient(135deg, var(--mc-primary), var(--mc-primary-hover));
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  margin-top: 4px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(59, 136, 255, 0.28);
}

.login-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* Loading */
.loading-dots {
  display: flex;
  gap: 5px;
  align-items: center;
}

.loading-dots span {
  width: 6px;
  height: 6px;
  background: white;
  border-radius: 50%;
  animation: bounce 1.2s infinite;
}

.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-5px); }
}

/* Breathing animation */
@keyframes breathe {
  0%, 100% {
    transform: scale(1);
    filter: drop-shadow(0 6px 20px rgba(217, 119, 87, 0.3));
  }
  50% {
    transform: scale(1.06);
    filter: drop-shadow(0 8px 28px rgba(217, 119, 87, 0.45));
  }
}

/* Entrance animation */
@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Mobile */
@media (max-width: 480px) {
  .login-page {
    padding: 16px;
  }

  .login-logo {
    gap: 10px;
  }

  .logo-image {
    width: 52px;
    height: 52px;
  }

  .logo-title-image {
    width: min(170px, 60vw);
  }
}
</style>
