import http, { unwrapAuth } from './http'

export interface AuthUser {
  id: number
  username: string
  isAdmin?: boolean
}

export async function login(username: string, password: string): Promise<AuthUser> {
  const body = new URLSearchParams()
  body.set('username', username)
  body.set('password', password)
  const { data } = await http.post('/auths/login', body, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  })
  return unwrapAuth<AuthUser>(data)
}

export async function logout(): Promise<void> {
  try {
    await http.get('/auths/logout')
  } catch {
    /* ignore */
  }
}
