/**
 * API 类型定义
 *
 * 本文件定义了前端与后端交互所需的所有数据类型，
 * 与后端 DTO（数据传输对象）一一对应。
 */

/** 用户信息（登录后返回） */
export interface UserInfo {
  /** 用户 ID */
  id: number
  /** 用户名 */
  username: string
  /** 邮箱地址，可能为空 */
  email: string | null
  /** 手机号，可能为空 */
  phone: string | null
  /** 用户类型：SUPER_ADMIN | TENANT_OWNER | TENANT_ADMIN | TENANT_USER */
  userType: string
  /** 所属租户 ID，超级管理员为 null */
  tenantId: number | null
  /** 所属租户名称，超级管理员为 null */
  tenantName: string | null
  /** 角色编码列表 */
  roles: string[]
}

/** Token 响应（后端通过 Set-Cookie 设置 HttpOnly Cookie，JSON body 不含 token） */
export interface TokenResponse {
  /** 当前登录用户信息 */
  userInfo: UserInfo
}

/** 登录请求参数 */
export interface LoginRequest {
  /** 用户名 */
  username: string
  /** 密码 */
  password: string
}

/** 注册请求参数 */
export interface RegisterRequest {
  /** 用户名 */
  username: string
  /** 邮箱地址 */
  email: string
  /** 密码 */
  password: string
  /** 租户编码（用于关联租户） */
  tenantCode: string
}

/** 租户信息 */
export interface Tenant {
  /** 租户 ID */
  id: number
  /** 租户编码（唯一标识） */
  code: string
  /** 租户名称 */
  name: string
  /** 租户描述，可能为空 */
  description: string | null
  /** 租户状态：ACTIVE | TRIAL | PENDING | DISABLED | EXPIRED | DELETED */
  status: string
  /** 是否已使用过试用机会 */
  trialUsed: boolean
  /** 过期时间（ISO 日期时间格式），null 表示永不过期 */
  expiredAt: string | null
  /** 创建时间（ISO 日期时间格式） */
  createdAt: string
  /** 更新时间（ISO 日期时间格式） */
  updatedAt: string
}

/** 租户入驻申请 */
export interface TenantApplication {
  /** 申请 ID */
  id: number
  /** 公司名称 */
  companyName: string
  /** 联系人姓名 */
  contactName: string
  /** 联系人邮箱 */
  contactEmail: string
  /** 联系人手机号，可能为空 */
  contactPhone: string | null
  /** 申请说明，可能为空 */
  description: string | null
  /** 申请类型：REGISTER | TRIAL */
  applicationType: string
  /** 审核状态：PENDING | APPROVED | REJECTED */
  status: string
  /** 拒绝原因（仅审核拒绝时有值） */
  rejectReason: string | null
  /** 审核人用户名 */
  reviewedBy: string | null
  /** 审核时间（ISO 日期时间格式） */
  reviewedAt: string | null
  /** 创建时间（ISO 日期时间格式） */
  createdAt: string
}

/** 订阅信息 */
export interface Subscription {
  /** 订阅 ID */
  id: number
  /** 所属租户 ID */
  tenantId: number
  /** 套餐类型：MONTHLY | QUARTERLY | YEARLY */
  planType: string
  /** 金额（单位：元） */
  amount: number
  /** 订阅开始日期（ISO 日期格式） */
  startDate: string
  /** 订阅结束日期（ISO 日期格式） */
  endDate: string
  /** 订阅状态：PENDING | ACTIVE | EXPIRED | CANCELLED */
  status: string
  /** 支付方式：OFFLINE | ALIPAY | WECHAT | CARD_KEY */
  paymentMethod: string | null
  /** 支付流水号 */
  paymentRef: string | null
  /** 备注信息 */
  remark: string | null
  /** 创建时间（ISO 日期时间格式） */
  createdAt: string
}

/** 用户管理信息（管理后台使用） */
export interface User {
  /** 用户 ID */
  id: number
  /** 所属租户 ID */
  tenantId: number
  /** 用户名 */
  username: string
  /** 邮箱地址，可能为空 */
  email: string | null
  /** 手机号，可能为空 */
  phone: string | null
  /** 用户状态：ACTIVE | DISABLED */
  status: string
  /** 用户类型 */
  userType: string
  /** 已分配的角色名称列表 */
  roles: string[]
  /** 创建时间（ISO 日期时间格式） */
  createdAt: string
}

/** 角色信息 */
export interface Role {
  /** 角色 ID */
  id: number
  /** 所属租户 ID，系统角色为 null */
  tenantId: number | null
  /** 角色编码 */
  code: string
  /** 角色名称 */
  name: string
  /** 角色描述，可能为空 */
  description: string | null
  /** 是否为系统内置角色 */
  isSystem: boolean
  /** 创建时间（ISO 日期时间格式） */
  createdAt: string
}

/** 权限信息 */
export interface Permission {
  /** 权限 ID */
  id: number
  /** 权限编码 */
  code: string
  /** 权限名称 */
  name: string
  /** 所属资源模块 */
  resource: string
  /** 权限描述，可能为空 */
  description: string | null
}

/** 通知消息 */
export interface Notification {
  /** 通知 ID */
  id: number
  /** 所属租户 ID */
  tenantId: number
  /** 目标用户 ID，null 表示全体通知 */
  userId: number | null
  /** 通知标题 */
  title: string
  /** 通知内容 */
  content: string
  /** 通知类型：SYSTEM | AUDIT_RESULT | EXPIRY_WARNING | SUBSCRIPTION */
  type: string
  /** 是否已读 */
  isRead: boolean
  /** 阅读时间（ISO 日期时间格式），未读时为 null */
  readAt: string | null
  /** 创建时间（ISO 日期时间格式） */
  createdAt: string
}

/** 审计日志 */
export interface AuditLog {
  /** 日志 ID */
  id: number
  /** 所属租户 ID，系统级操作可能为 null */
  tenantId: number | null
  /** 操作用户 ID */
  userId: number | null
  /** 操作用户名 */
  username: string | null
  /** 操作模块 */
  module: string
  /** 操作动作 */
  action: string
  /** 操作目标（如资源名称） */
  target: string | null
  /** 操作详情 */
  detail: string | null
  /** 操作来源 IP 地址 */
  ipAddress: string | null
  /** 创建时间（ISO 日期时间格式） */
  createdAt: string
}

/** 卡密信息 */
export interface CardKey {
  /** 卡密 ID */
  id: number
  /** 卡密编码 */
  code: string
  /** 套餐类型：MONTHLY | QUARTERLY | YEARLY */
  planType: string
  /** 有效天数 */
  durationDays: number
  /** 金额（单位：元） */
  amount: number
  /** 卡密状态：UNUSED | REDEEMED | EXPIRED */
  status: string
  /** 生成者用户名 */
  generatedBy: string | null
  /** 兑换者用户名 */
  redeemedBy: string | null
  /** 兑换时间（ISO 日期时间格式） */
  redeemedAt: string | null
  /** 所属租户 ID（兑换后关联） */
  tenantId: number | null
  /** 过期时间（ISO 日期时间格式） */
  expiredAt: string | null
  /** 创建时间（ISO 日期时间格式） */
  createdAt: string
}

/** 发起支付请求参数 */
export interface PaymentInitiateRequest {
  /** 订阅 ID */
  subscriptionId: number
  /** 支付方式：ALIPAY | WECHAT | CARD_KEY */
  paymentMethod: string
  /** 支付完成后的回调地址 */
  returnUrl?: string
}

/** 发起支付响应 */
export interface PaymentInitiateResponse {
  /** 支付页面跳转地址 */
  paymentUrl: string
  /** 商户订单号 */
  outTradeNo: string
}

/** 卡密兑换请求参数 */
export interface CardKeyRedeemRequest {
  /** 卡密编码 */
  code: string
}

/**
 * 分页查询结果
 * @template T - 列表元素类型
 */
export interface PageResult<T> {
  /** 数据列表 */
  list: T[]
  /** 总记录数 */
  total: number
  /** 当前页码 */
  page: number
  /** 每页条数 */
  size: number
}

/**
 * 统一响应包装
 *
 * 后端所有接口统一返回此结构，code 为 200 表示成功，非 200 表示业务异常。
 * @template T - data 字段的实际数据类型
 */
export interface Result<T> {
  /** 响应码，200 表示成功 */
  code: number
  /** 响应消息 */
  message: string
  /** 响应数据 */
  data: T
}
