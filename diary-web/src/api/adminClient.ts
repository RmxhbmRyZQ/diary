import axios from 'axios';

const adminClient = axios.create({
  baseURL: '/admin',
  headers: { 'Content-Type': 'application/json' },
});

let adminToken: string | null = null;

export function setAdminToken(token: string | null) {
  adminToken = token;
}

export function getAdminToken(): string | null {
  return adminToken;
}

adminClient.interceptors.request.use((config) => {
  if (adminToken) {
    config.headers.Authorization = `Bearer ${adminToken}`;
  }
  return config;
});

adminClient.interceptors.response.use(
  (response) => {
    if (response.data?.code === 0) {
      return response.data;
    }
    return response.data;
  },
  (error) => {
    if (error.response?.status === 401) {
      setAdminToken(null);
      window.dispatchEvent(new CustomEvent('admin:session-expired'));
    }

    if (error.response) {
      const { status, data } = error.response;
      const apiError = new Error(data?.message || '请求失败');
      (apiError as Record<string, unknown>).code = status;
      return Promise.reject(apiError);
    }

    return Promise.reject(new Error('网络错误，请检查网络连接'));
  },
);

export default adminClient;
