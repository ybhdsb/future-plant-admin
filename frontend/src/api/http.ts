import axios from 'axios'

const http = axios.create({
  baseURL: '/',
  timeout: 30000,
  withCredentials: true,
  // 不要跟随到 /login HTML，否则 SPA 会解包失败误报「请求失败」
  maxRedirects: 0,
  validateStatus: (status) => (status >= 200 && status < 300) || status === 302,
})

function clearClientSession() {
  try {
    localStorage.removeItem('fp_user')
  } catch {
    /* ignore */
  }
}

function goLogin() {
  const path = window.location.pathname
  if (!path.includes('/login')) {
    window.location.href = '/app/login'
  }
}

http.interceptors.response.use(
  (res) => {
    // 部分环境下仍可能拿到 302
    if (res.status === 302) {
      clearClientSession()
      goLogin()
      return Promise.reject(new Error('未登录或会话已过期'))
    }
    return res
  },
  (err) => {
    const status = err?.response?.status
    if (status === 401 || status === 302) {
      clearClientSession()
      goLogin()
      return Promise.reject(new Error('未登录或会话已过期'))
    }
    return Promise.reject(err)
  },
)

export default http

/** Plant APIs use code === 0 */
export function unwrapPlant<T = unknown>(payload: { code: number; message?: string; data: T }): T {
  if (!payload || typeof payload !== 'object') {
    throw new Error('请求失败')
  }
  if (payload.code === 401) {
    clearClientSession()
    goLogin()
    throw new Error(payload.message || '未登录或会话已过期')
  }
  if (payload.code !== 0) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data
}

/** Auth APIs use code === 200 */
export function unwrapAuth<T = unknown>(payload: { code: number; msg?: string; data: T }): T {
  if (payload.code !== 200) {
    throw new Error(payload.msg || '登录失败')
  }
  // 兼容后端失败时 code=200 但 data 为错误码的旧写法
  if (payload.data === (402 as unknown) || payload.data === (405 as unknown)) {
    throw new Error(payload.msg || '登录失败')
  }
  return payload.data
}
