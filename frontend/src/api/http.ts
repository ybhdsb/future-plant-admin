import axios from 'axios'

const http = axios.create({
  baseURL: '/',
  timeout: 30000,
  withCredentials: true,
})

http.interceptors.response.use(
  (res) => res,
  (err) => {
    const status = err?.response?.status
    if (status === 401 || status === 302) {
      const path = window.location.pathname
      if (!path.includes('/login')) {
        window.location.href = '/app/login'
      }
    }
    return Promise.reject(err)
  },
)

export default http

/** Plant APIs use code === 0 */
export function unwrapPlant<T = unknown>(payload: { code: number; message?: string; data: T }): T {
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
  return payload.data
}
