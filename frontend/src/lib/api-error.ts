/**
 * API 错误类
 *
 * 用于封装后端返回的业务错误信息，包含错误码和错误消息。
 * 区别于网络错误（如断网、超时等），此类专门表示后端业务逻辑层面的异常。
 */
export class ApiError extends Error {
  /** 后端业务错误码 */
  public readonly code: number

  /**
   * 创建 API 错误实例
   * @param code - 后端业务错误码
   * @param message - 错误描述信息
   */
  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

/**
 * 判断给定错误是否为 ApiError 实例
 *
 * 用于在 catch 块中进行类型守卫，区分业务错误和其他类型的错误。
 * @param error - 待检查的错误对象
 * @returns 如果 error 是 ApiError 实例则返回 true
 */
export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
