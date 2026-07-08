/**
 * AI Gateway 管理台一期 E2E 冒烟测试。
 *
 * 测试通过 Playwright route mock 控制面接口，不访问真实后端、Gateway 或外部 Provider。
 */

import { expect, test, type Page, type Route } from '@playwright/test'

/**
 * 返回统一 Result 响应。
 * @param data 响应数据
 * @returns Result 包装对象
 */
function ok(data: unknown) {
  return { code: 200, message: 'success', data }
}

/**
 * 返回分页响应。
 * @param list 分页数据
 * @returns PageResult 包装对象
 */
function pageResult(list: unknown[]) {
  return { list, total: list.length, page: 1, size: 20 }
}

/**
 * 向页面写出 JSON mock。
 * @param route Playwright route
 * @param data 响应数据
 */
async function fulfillJson(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(data),
  })
}

/**
 * 安装 AI 管理台所需的接口 mock。
 * @param page 页面对象
 * @param userType 用户类型
 */
async function setupAiGatewayRoutes(page: Page, userType: 'TENANT_ADMIN' | 'TENANT_USER') {
  await page.route('**/api/v1/users/me', async (route) => {
    await fulfillJson(route, ok({
      id: 10,
      username: userType === 'TENANT_ADMIN' ? 'tenant_admin' : 'tenant_member',
      email: 'user@example.com',
      phone: null,
      userType,
      tenantId: 1,
      tenantName: '测试租户',
      roles: [userType],
    }))
  })
  await page.route('**/api/v1/notifications/unread-count', async (route) => {
    await fulfillJson(route, ok(0))
  })

  await page.route('**/api/v1/ai/options', async (route) => {
    await fulfillJson(route, ok({
      providerKinds: [{ name: 'OPENAI', label: 'OpenAI', description: 'OpenAI 兼容供应商' }],
      protocolTypes: [{ name: 'OPENAI_COMPATIBLE', label: 'OpenAI Compatible', description: 'OpenAI 兼容协议' }],
      catalogStatuses: [
        { name: 'ENABLED', label: '启用', description: '可用' },
        { name: 'DISABLED', label: '停用', description: '不可用' },
      ],
      resourceStatuses: [
        { name: 'ENABLED', label: '启用', description: '可用' },
        { name: 'DISABLED', label: '停用', description: '不可用' },
        { name: 'DRAINING', label: '排空', description: '不接新请求' },
      ],
      credentialTypes: [{ name: 'API_KEY', label: 'API Key', description: 'Bearer API Key' }],
      canonicalOperations: [{ name: 'CHAT_COMPLETIONS', label: 'Chat Completions', description: '聊天补全' }],
      selectionPolicies: [{ name: 'WEIGHTED', label: 'Weighted', description: '权重选择' }],
      routePolicyStatuses: [{ name: 'DRAFT', label: '草稿', description: '未启用' }],
      clientApiKeyStatuses: [
        { name: 'ENABLED', label: '启用', description: '可用' },
        { name: 'REVOKED', label: '撤销', description: '不可恢复' },
      ],
    }))
  })

  await page.route('**/api/v1/ai/gateway/ready', async (route) => {
    await fulfillJson(route, ok({ status: 'READY', loadedTenantCount: 1 }))
  })
  await page.route('**/api/v1/ai/gateway/snapshot-status', async (route) => {
    await fulfillJson(route, ok({
      status: 'READY',
      loadedTenantCount: 1,
      loadedClientKeyCount: 1,
      loadedRoutePlanCount: 1,
      loadedRuntimePolicyCount: 1,
      snapshotRefreshFailedCount: 0,
      tenantRefreshPendingCount: 0,
      lastFullReconcileEpochMillis: 1783470000000,
    }))
  })
  await page.route('**/api/v1/ai/gateway/runtime-status', async (route) => {
    await fulfillJson(route, ok({ redisAvailable: true, activeLocalLeases: 0, runtimeStateUnavailableCount: 0 }))
  })
  await page.route('**/api/v1/ai/gateway/test-chat-completions', async (route) => {
    await fulfillJson(route, ok({
      status: 401,
      latencyMs: 18,
      errorCode: 'invalid_api_key',
      errorMessage: 'Client API Key 无效',
    }))
  })

  await page.route('**/api/v1/ai/providers/1/connections**', async (route) => {
    await fulfillJson(route, ok(pageResult([
      { id: 1, code: 'conn-openai', displayName: '连接一', protocolType: 'OPENAI_COMPATIBLE', baseUrl: 'https://api.example.test/v1', status: 'ENABLED' },
    ])))
  })
  await page.route('**/api/v1/ai/providers/1/credentials**', async (route) => {
    await fulfillJson(route, ok(pageResult([
      { id: 1, code: 'cred-openai', displayName: '凭据一', maskedPreview: 'sk-***test', secretVersion: 1, adminStatus: 'ENABLED' },
    ])))
  })
  await page.route(/.*\/api\/v1\/ai\/providers(?:\?.*)?$/, async (route) => {
    await fulfillJson(route, ok(pageResult([
      { id: 1, code: 'provider-openai', displayName: 'Provider 一', providerKind: 'OPENAI', status: 'ENABLED' },
    ])))
  })
  await page.route('**/api/v1/ai/models**', async (route) => {
    await fulfillJson(route, ok(pageResult([
      { id: 1, code: 'public-chat', displayName: '公开 Chat', modelFamily: 'chat', status: 'ENABLED' },
    ])))
  })
  await page.route('**/api/v1/ai/resources/1/runtime-policy', async (route) => {
    await fulfillJson(route, ok({ maxConcurrentRequests: 0, consecutiveFailureThreshold: 3, policyVersion: 1 }))
  })
  await page.route('**/api/v1/ai/resources**', async (route) => {
    await fulfillJson(route, ok(pageResult([
      { id: 1, code: 'res-openai', displayName: '资源一', upstreamConnectionId: 1, credentialId: 1, adminStatus: 'ENABLED' },
    ])))
  })
  await page.route('**/api/v1/ai/resource-pools/1/members**', async (route) => {
    await fulfillJson(route, ok(pageResult([
      { id: 1, executionResourceId: 1, priority: 100, weight: 1, adminStatus: 'ENABLED' },
    ])))
  })
  await page.route('**/api/v1/ai/resource-pools**', async (route) => {
    await fulfillJson(route, ok(pageResult([
      { id: 1, code: 'pool-main', displayName: '资源池一', adminStatus: 'ENABLED' },
    ])))
  })
  await page.route('**/api/v1/ai/resource-model-bindings**', async (route) => {
    await fulfillJson(route, ok(pageResult([
      { id: 1, publicModelId: 1, executionResourceId: 1, canonicalOperation: 'CHAT_COMPLETIONS', upstreamModelName: 'gpt-test', adminStatus: 'ENABLED' },
    ])))
  })
  await page.route('**/api/v1/ai/route-policies/1/targets**', async (route) => {
    await fulfillJson(route, ok(pageResult([
      { id: 1, resourcePoolId: 1, priority: 100, weight: 1, adminStatus: 'ENABLED' },
    ])))
  })
  await page.route('**/api/v1/ai/route-policies**', async (route) => {
    await fulfillJson(route, ok(pageResult([
      { id: 1, displayName: '路由策略一', publicModelId: 1, canonicalOperation: 'CHAT_COMPLETIONS', adminStatus: 'ENABLED' },
    ])))
  })
  await page.route('**/api/v1/ai/access-groups/1/model-grants**', async (route) => {
    await fulfillJson(route, ok(pageResult([
      { id: 1, publicModelId: 1, canonicalOperation: 'CHAT_COMPLETIONS', adminStatus: 'ENABLED' },
    ])))
  })
  await page.route('**/api/v1/ai/access-groups**', async (route) => {
    await fulfillJson(route, ok(pageResult([
      { id: 1, code: 'group-chat', displayName: '访问组一', adminStatus: 'ENABLED' },
    ])))
  })
  await page.route('**/api/v1/ai/client-api-keys/1/access-groups**', async (route) => {
    await fulfillJson(route, ok(pageResult([
      { id: 1, accessGroupId: 1, adminStatus: 'ENABLED' },
    ])))
  })
  await page.route(/.*\/api\/v1\/ai\/client-api-keys(?:\?.*)?$/, async (route) => {
    if (route.request().method() === 'POST') {
      await fulfillJson(route, ok({
        id: 2,
        code: 'key-console',
        displayName: '控制台测试 Key',
        maskedPreview: 'cvg_live_***test',
        adminStatus: 'ENABLED',
        keyVersion: 1,
        rawKey: 'cvg_live_mock_once_value',
      }))
      return
    }
    await fulfillJson(route, ok(pageResult([
      { id: 1, code: 'key-main', displayName: 'Client Key 一', maskedPreview: 'cvg_live_***main', adminStatus: 'ENABLED', keyVersion: 1 },
    ])))
  })
}

test('租户管理员可以打开 AI Gateway 管理台并完成一期关键交互', async ({ page }) => {
  await setupAiGatewayRoutes(page, 'TENANT_ADMIN')
  await page.goto('/console/ai-gateway')

  await expect(page.getByText('AI 网关').first()).toBeVisible()
  await page.getByRole('tab', { name: /上游配置/ }).click()
  await expect(page.getByText('Provider 一')).toBeVisible()

  await page.getByRole('tab', { name: /凭据与资源/ }).click()
  await page.getByRole('button', { name: /新增 Credential/ }).click()
  await expect(page.locator('input[type="password"]').first()).toBeVisible()
  await expect(page.getByText('sk-***test')).toBeVisible()
  await page.getByRole('button', { name: '取消' }).click()

  await page.getByRole('tab', { name: /下游访问/ }).click()
  await page.getByRole('button', { name: /新增 Client Key/ }).evaluate((node) => {
    (node as HTMLButtonElement).click()
  })
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('编码').fill('key-console')
  await dialog.getByLabel('名称').fill('控制台测试 Key')
  await dialog.getByRole('button', { name: /保存/ }).click()
  await expect(page.locator('input[value="cvg_live_mock_once_value"]')).toBeVisible()
  page.once('dialog', (dialog) => { void dialog.accept() })
  await page.getByRole('button', { name: /我已保存/ }).click()
  await expect(page.getByText('cvg_live_mock_once_value')).toHaveCount(0)

  await page.getByRole('tab', { name: /运行状态/ }).click()
  await expect(page.getByText('Snapshot Status')).toBeVisible()
  await expect(page.getByRole('tabpanel', { name: '运行状态' })).toContainText('loadedTenantCount')

  await page.getByRole('tab', { name: /在线测试/ }).click()
  await page.getByLabel('Client API Key').fill('cvg_live_bad')
  await page.getByLabel('Model').fill('public-chat')
  await page.getByRole('button', { name: /发送非流式测试/ }).click()
  await expect(page.getByRole('tabpanel', { name: '在线测试' })).toContainText('invalid_api_key')
})

test('租户成员不能访问 AI Gateway 管理台', async ({ page }) => {
  await setupAiGatewayRoutes(page, 'TENANT_USER')
  await page.goto('/console/ai-gateway')

  await expect(page.getByText('403')).toBeVisible()
  await expect(page.getByText('无权限访问此页面')).toBeVisible()
})
