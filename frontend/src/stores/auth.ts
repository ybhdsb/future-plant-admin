import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import type { AuthUser } from '@/api/auth'

const KEY = 'fp_user'

export const useAuthStore = defineStore('auth', () => {
  const raw = localStorage.getItem(KEY)
  const user = ref<AuthUser | null>(raw ? JSON.parse(raw) : null)

  const isLoggedIn = computed(() => !!user.value)

  async function login(username: string, password: string) {
    const u = await authApi.login(username, password)
    user.value = { id: u.id, username: u.username, isAdmin: u.isAdmin }
    localStorage.setItem(KEY, JSON.stringify(user.value))
    return user.value
  }

  async function logout() {
    await authApi.logout()
    user.value = null
    localStorage.removeItem(KEY)
  }

  return { user, isLoggedIn, login, logout }
})
