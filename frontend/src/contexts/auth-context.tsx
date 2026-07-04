/**
 * 认证上下文
 *
 * 提供全局认证状态管理，包括：
 * - 登录 / 注册 / 登出
 * - 初始挂载时通过 Cookie 检测认证状态
 * - 用户信息刷新
 * - 基于用户类型的角色快捷判断
 *
 * Token 由后端通过 HttpOnly Cookie 管理，前端无需手动存储。
 */

import { createContext, useContext, useState, useCallback, useEffect } from 'react'
import { get, post } from '@/lib/api-client'
import type { UserInfo, TokenResponse } from '@/lib/types'

/** 认证上下文类型定义 */
interface AuthContextType {
  /** 当前登录用户信息，未登录时为 null */
  user: UserInfo | null
  /** 是否已登录 */
  isAuthenticated: boolean
  /** 是否正在检查初始认证状态（挂载时） */
  isLoading: boolean
  /** 是否为超级管理员 */
  isSuperAdmin: boolean
  /** 是否为租户管理员（租户所有者或租户管理员） */
  isTenantAdmin: boolean
  /** 是否为租户普通用户 */
  isTenantUser: boolean
  /**
   * 用户登录
   * @param username - 用户名
   * @param password - 密码
   */
  login: (username: string, password: string) => Promise<void>
  /**
   * 用户注册
   * @param username - 用户名
   * @param email - 邮箱地址
   * @param password - 密码
   * @param tenantCode - 租户编码
   * @param verificationToken - 邮箱验证凭据
   */
  register: (
    username: string,
    email: string,
    password: string,
    tenantCode: string,
    verificationToken: string,
  ) => Promise<void>
  /** 用户登出 */
  logout: () => Promise<void>
  /** 重新获取当前用户信息（用于资料修改后同步） */
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | null>(null)

/**
 * 认证状态 Provider
 *
 * 在组件树顶层使用，为所有子组件提供认证状态。
 * 挂载时自动调用 /api/v1/users/me 检查 Cookie 是否有效。
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  /** 获取当前用户信息 */
  const fetchCurrentUser = useCallback(async (): Promise<UserInfo | null> => {
    try {
      return await get<UserInfo>('/api/v1/users/me')
    } catch {
      // 401 或其他错误：用户未登录
      return null
    }
  }, [])

  /** 挂载时检测认证状态 */
  useEffect(() => {
    let cancelled = false

    const checkAuth = async () => {
      const currentUser = await fetchCurrentUser()
      if (!cancelled) {
        setUser(currentUser)
        setIsLoading(false)
      }
    }

    checkAuth()

    return () => {
      cancelled = true
    }
  }, [fetchCurrentUser])

  /** 登录 */
  const login = useCallback(async (username: string, password: string): Promise<void> => {
    const response = await post<TokenResponse>('/api/v1/auth/login', { username, password })
    setUser(response.userInfo)
  }, [])

  /** 注册 */
  const register = useCallback(
    async (
      username: string,
      email: string,
      password: string,
      tenantCode: string,
      verificationToken: string,
    ): Promise<void> => {
      const response = await post<TokenResponse>('/api/v1/auth/register', {
        username,
        email,
        password,
        tenantCode,
        verificationToken,
      })
      setUser(response.userInfo)
    },
    [],
  )

  /** 登出 */
  const logout = useCallback(async (): Promise<void> => {
    await post('/api/v1/auth/logout')
    setUser(null)
  }, [])

  /** 刷新用户信息 */
  const refreshUser = useCallback(async (): Promise<void> => {
    const currentUser = await fetchCurrentUser()
    setUser(currentUser)
  }, [fetchCurrentUser])

  /** 基于用户类型的角色快捷判断 */
  const isSuperAdmin = user?.userType === 'SUPER_ADMIN'
  const isTenantAdmin = user?.userType === 'TENANT_OWNER' || user?.userType === 'TENANT_ADMIN'
  const isTenantUser = user?.userType === 'TENANT_USER'

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        isSuperAdmin,
        isTenantAdmin,
        isTenantUser,
        login,
        register,
        logout,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

/**
 * 获取认证上下文
 *
 * 必须在 AuthProvider 内部使用，否则抛出异常。
 * @returns 认证上下文对象
 */
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内部使用')
  }
  return context
}
