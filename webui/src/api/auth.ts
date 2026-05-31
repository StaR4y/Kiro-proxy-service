/** 认证模块 API。对应后端 /auth/*。 */
import http, { unwrap } from '@/utils/http';
import type {
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  ApiResponse,
} from '@/types/api';

export const authApi = {
  login(payload: LoginRequest) {
    return unwrap<LoginResponse>(http.post<ApiResponse<LoginResponse>>('/auth/login', payload));
  },
  changePassword(payload: ChangePasswordRequest) {
    return unwrap<void>(http.post<ApiResponse<void>>('/auth/password', payload));
  },
};
