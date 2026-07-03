/**
 * API 客户端
 *
 * 提供统一的 HTTP 请求封装，基于 fetch 实现。
 * 主要功能：
 * - 自动携带 HttpOnly Cookie（credentials: 'include'）
 * - 统一解析后端 Result<T> 响应格式
 * - 401 自动刷新 Token（Promise 单例模式，避免并发请求重复刷新）
 * - 刷新失败自动跳转登录页
 */

import { ApiError } from '@/lib/api-error'
import type { Result } from '@/lib/types'

/**
 * Token 刷新 Promise 单例
 *
 * 当多个请求同时收到 401 时，只触发一次刷新请求，
 * 后续请求共享同一个 Promise，避免重复刷新。
 */
let refreshPromise: Promise<boolean> | null = null

/**
 * 刷新认证 Token
 *
 * 通过 HttpOnly Cookie 中的 Refresh Token 获取新的 Access Token。
 * 使用 Promise 单例模式确保并发场景下只发起一次刷新请求。
 * @returns 刷新是否成功
 */
async function refreshToken(): Promise<boolean> {
  if (refreshPromise) return refreshPromise

  refreshPromise = (async () => {
    try {
      const resp = await fetch('/api/v1/auth/refresh', {
        method: 'POST',
        credentials: 'include',
      })
      return resp.ok
    } finally {
      refreshPromise = null
    }
  })()

  return refreshPromise
}

/**
 * 发送 HTTP 请求
 *
 * 统一请求入口，处理以下逻辑：
 * 1. 构建请求头和请求体
 * 2. 发送请求并解析 Result<T> 响应
 * 3. 401 状态自动刷新 Token 并重试
 * 4. 业务错误码非 0 时抛出 ApiError
 *
 * @template T - 响应数据类型
 * @param method - HTTP 方法（GET / POST / PUT / DELETE）
 * @param path - 请求路径（以 /api 开头）
 * @param body - 请求体（仅 POST / PUT 时使用）
 * @returns 解析后的响应数据
 * @throws {ApiError} 当后端返回业务错误码非 0 时
 * @throws {Error} 当网络请求失败且非 401 时
 */
async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  /** 构建请求配置 */
  const headers: Record<string, string> = {}
  let requestBody: string | undefined

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    requestBody = JSON.stringify(body)
  }

  /** 执行请求的公共逻辑 */
  const doFetch = async (): Promise<Response> => {
    return fetch(path, {
      method,
      headers,
      body: requestBody,
      credentials: 'include',
    })
  }

  let resp = await doFetch()

  // 401 未授权：尝试刷新 Token 后重试
  if (resp.status === 401) {
    const refreshed = await refreshToken()
    if (refreshed) {
      // 刷新成功，重试原始请求
      resp = await doFetch()
    } else {
      // 刷新失败，清除认证状态并跳转到登录页
      window.location.href = '/login'
      throw new Error('认证已过期，正在跳转到登录页')
    }
  }

  // 非 2xx 响应且未被 401 逻辑处理，抛出网络错误
  if (!resp.ok) {
    throw new Error(`请求失败: ${resp.status} ${resp.statusText}`)
  }

  // 解析统一响应格式
  const result: Result<T> = await resp.json()

  // 业务错误码非 0，抛出 ApiError
  if (result.code !== 0) {
    throw new ApiError(result.code, result.message)
  }

  return result.data
}

/**
 * 发送 GET 请求
 * @template T - 响应数据类型
 * @param path - 请求路径
 * @returns 解析后的响应数据
 */
export function get<T>(path: string): Promise<T> {
  return request<T>('GET', path)
}

/**
 * 发送 POST 请求
 * @template T - 响应数据类型
 * @param path - 请求路径
 * @param body - 请求体
 * @returns 解析后的响应数据
 */
export function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>('POST', path, body)
}

/**
 * 发送 PUT 请求
 * @template T - 响应数据类型
 * @param path - 请求路径
 * @param body - 请求体
 * @returns 解析后的响应数据
 */
export function put<T>(path: string, body?: unknown): Promise<T> {
  return request<T>('PUT', path, body)
}

/**
 * 发送 DELETE 请求
 * @template T - 响应数据类型
 * @param path - 请求路径
 * @returns 解析后的响应数据
 */
export function del<T>(path: string): Promise<T> {
  return request<T>('DELETE', path)
}
