/**
 * 后端 API 类型定义。
 *
 * 与 `api/` 模块的 Java DTO 一一对应，命名保留 camelCase 与后端字段一致，
 * 便于以后由 OpenAPI 自动生成时无缝替换。
 */

/** 后端统一响应包络 */
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: { code: string; message: string };
  timestamp: string;
}

/* ================== 鉴权 / 管理员 ================== */

export interface AdminUser {
  userId: string;
  username: string;
  displayName: string | null;
  role: string;
  enabled: boolean;
  firstLogin: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  tokenType: string;
  accessToken: string;
  expiresAt: string;
  user: AdminUser;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

export interface CreateAdminUserRequest {
  username: string;
  displayName?: string;
  role?: string;
  password?: string;
}

export interface UpdateAdminUserRequest {
  displayName?: string;
  role?: string;
  enabled?: boolean;
}

export interface AdminUserCreated {
  user: AdminUser;
  password: string | null;
}

export interface ResetPasswordResult {
  userId: string;
  username: string;
  password: string;
}

/* ================== 账号 ================== */

export interface Account {
  accountId: string;
  email: string | null;
  region: string | null;
  authMethod: string | null;
  provider: string | null;
  profileArn: string | null;
  machineId: string | null;
  proxyUrl: string | null;
  enabled: boolean;
  requestCount: number | null;
  errorCount: number | null;
  quotaUsed: number | null;
  quotaLimit: number | null;
  quotaExhaustedAt: string | null;
  quotaResetAt: string | null;
  suspendedAt: string | null;
  suspendReason: string | null;
  suspendMessage: string | null;
  lastUsedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAccountRequest {
  accountId?: string;
  id?: string;
  email?: string;
  accessToken?: string;
  refreshToken?: string;
  clientId?: string;
  clientSecret?: string;
  region?: string;
  authMethod?: string;
  provider?: string;
  idp?: string;
  profileArn?: string;
  machineId?: string;
  proxyUrl?: string;
  quotaLimit?: number;
  credentials?: {
    accessToken?: string;
    csrfToken?: string;
    refreshToken?: string;
    clientId?: string;
    clientSecret?: string;
    region?: string;
    authMethod?: string;
    provider?: string;
  };
  usage?: {
    limit?: number;
  };
}

export type UpdateAccountRequest = Partial<
  Omit<CreateAccountRequest, 'accountId'> & {
    enabled: boolean;
    quotaUsed: number;
    quotaResetAt: string;
  }
>;

export interface SuspendAccountRequest {
  reason: string;
  message?: string;
}

export interface TestAccountsRequest {
  accountIds?: string[];
  onlyEnabled?: boolean;
  model?: string;
  prompt?: string;
  maxConcurrency?: number;
}

export interface TestAccountsResult {
  total: number;
  success: number;
  failed: number;
  skipped: number;
  startedAt: string;
  finishedAt: string;
  items: TestAccountItem[];
}

export interface TestAccountItem {
  accountId: string;
  email: string | null;
  status: 'SUCCESS' | 'FAILED' | 'SKIPPED';
  message: string;
  detail: string | null;
  statusCode: number | null;
  latencyMs: number | null;
  testedAt: string;
  model: string | null;
}

export interface ImportAccountsRequest {
  accounts: CreateAccountRequest[];
  upsert?: boolean;
}

export interface ImportAccountItem {
  index: number;
  accountId: string | null;
  email: string | null;
  status: 'CREATED' | 'UPDATED' | 'SKIPPED' | 'FAILED';
  message: string | null;
  account: Account | null;
}

export interface ImportAccountsResult {
  total: number;
  created: number;
  updated: number;
  skipped: number;
  failed: number;
  items: ImportAccountItem[];
}

/* ================== API Key ================== */

export interface ApiKey {
  keyId: string;
  name: string;
  keyPrefix: string;
  enabled: boolean;
  creditsLimit: number | null;
  totalRequests: number | null;
  totalCredits: number | null;
  totalInputTokens: number | null;
  totalOutputTokens: number | null;
  lastUsedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateApiKeyRequest {
  name: string;
  creditsLimit?: number;
}

export interface UpdateApiKeyRequest {
  name?: string;
  enabled?: boolean;
  creditsLimit?: number;
}

export interface CreatedApiKey {
  keyId: string;
  name: string;
  /** 完整 sk-... 密钥，仅在创建响应中出现一次 */
  key: string;
  keyPrefix: string;
  enabled: boolean;
  creditsLimit: number | null;
  createdAt: string;
}

/* ================== 模型映射 ================== */

export type MappingType = 'replace' | 'alias' | 'loadbalance';

export interface ModelMapping {
  mappingId: string;
  name: string;
  enabled: boolean;
  mappingType: MappingType;
  sourceModel: string;
  targetModels: string[];
  weights: number[] | null;
  priority: number;
  apiKeyIds: string[] | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateModelMappingRequest {
  name: string;
  mappingType: MappingType;
  sourceModel: string;
  targetModels: string[];
  weights?: number[];
  priority?: number;
  apiKeyIds?: string[];
}

export interface UpdateModelMappingRequest {
  name?: string;
  enabled?: boolean;
  mappingType?: MappingType;
  sourceModel?: string;
  targetModels?: string[];
  weights?: number[];
  priority?: number;
  apiKeyIds?: string[];
}

/* ================== 日志 / 模型清单 ================== */

export interface RequestLog {
  requestId: string;
  apiKeyId: string | null;
  accountId: string | null;
  path: string;
  model: string | null;
  statusCode: number | null;
  success: boolean;
  inputTokens: number | null;
  outputTokens: number | null;
  credits: number | null;
  latencyMs: number | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface ModelInfo {
  id: string;
  object: string;
  owned_by: string;
  provider?: string;
  family?: string;
  targetModelId?: string;
  name?: string;
  modelName?: string;
  description?: string;
  supportedInputTypes?: string[];
  maxInputTokens?: number;
  maxOutputTokens?: number;
  context_length?: number;
  max_output_tokens?: number;
}

export interface HealthInfo {
  status: string;
  service: string;
  uptime_ms: number;
}
