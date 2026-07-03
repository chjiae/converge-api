/**
 * API 请求 Hooks
 *
 * 提供 useQuery 和 useMutation 两个通用 Hook，用于数据获取和数据变更。
 * 内置加载状态、错误处理、组件卸载防护等机制。
 */

import { useState, useEffect, useCallback, useRef } from 'react'

/**
 * useQuery — 用于数据获取
 *
 * 在挂载时及依赖变化时自动调用 fetcher 获取数据。
 * 组件卸载时自动中止未完成的请求，避免内存泄漏。
 *
 * @template T - 返回数据类型
 * @param fetcher - 异步获取函数
 * @param deps - 依赖数组，变化时重新获取
 * @returns 包含 data、loading、error、refetch 的对象
 */
export function useQuery<T>(
  fetcher: () => Promise<T>,
  deps: React.DependencyList = [],
): {
  /** 获取到的数据，请求未完成或失败时为 null */
  data: T | null
  /** 是否正在加载 */
  loading: boolean
  /** 请求错误，成功时为 null */
  error: Error | null
  /** 手动重新获取数据 */
  refetch: () => void
} {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  /** 用于组件卸载时中止请求，防止对已卸载组件调用 setState */
  const mountedRef = useRef(true)

  /** 将 fetcher 存入 ref，避免将其加入依赖数组导致不必要的重新请求 */
  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher

  /** 执行数据获取 */
  const execute = useCallback(() => {
    setLoading(true)
    setError(null)

    fetcherRef
      .current()
      .then((result) => {
        if (mountedRef.current) {
          setData(result)
          setLoading(false)
        }
      })
      .catch((err: Error) => {
        if (mountedRef.current) {
          setError(err)
          setLoading(false)
        }
      })
  }, [])

  /** 挂载时及依赖变化时自动获取 */
  useEffect(() => {
    mountedRef.current = true
    execute()

    return () => {
      mountedRef.current = false
    }
    // deps 是外部传入的依赖数组，需要展开以触发重新获取
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  /** 手动重新获取，重置状态后再次执行 */
  const refetch = useCallback(() => {
    execute()
  }, [execute])

  return { data, loading, error, refetch }
}

/**
 * useMutation — 用于数据变更
 *
 * 提供手动触发的变更函数，内置加载状态和错误处理。
 * 组件卸载后不会更新状态，避免 React 警告。
 *
 * @template T - 变更操作返回数据类型
 * @template V - 变更操作输入参数类型，默认为 void
 * @param mutator - 异步变更函数
 * @returns 包含 mutate、loading、error、data 的对象
 */
export function useMutation<T, V = void>(
  mutator: (input: V) => Promise<T>,
): {
  /**
   * 触发变更操作
   * @param input - 变更参数
   * @returns 操作成功返回数据，失败返回 null
   */
  mutate: (input: V) => Promise<T | null>
  /** 是否正在执行变更 */
  loading: boolean
  /** 变更错误，成功时为 null */
  error: Error | null
  /** 最近一次变更的返回数据 */
  data: T | null
} {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<Error | null>(null)

  /** 防止对已卸载组件调用 setState */
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  /** 将 mutator 存入 ref，避免每次渲染都创建新的 mutate 函数 */
  const mutatorRef = useRef(mutator)
  mutatorRef.current = mutator

  /** 触发变更 */
  const mutate = useCallback(async (input: V): Promise<T | null> => {
    setLoading(true)
    setError(null)

    try {
      const result = await mutatorRef.current(input)
      if (mountedRef.current) {
        setData(result)
        setLoading(false)
      }
      return result
    } catch (err) {
      if (mountedRef.current) {
        setError(err instanceof Error ? err : new Error(String(err)))
        setLoading(false)
      }
      return null
    }
  }, [])

  return { mutate, loading, error, data }
}
