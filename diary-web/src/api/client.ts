import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
});

apiClient.interceptors.response.use(
  (response) => {
    if (response.data?.code === 0) {
      return response.data;
    }
    const businessError = new Error(response.data?.message || '请求失败');
    (businessError as Record<string, unknown>).code = response.data?.code || -1;
    return Promise.reject(businessError);
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response;

      if (status === 401) {
        window.dispatchEvent(new CustomEvent('auth:session-expired', { detail: data }));
      }

      if (status === 409) {
        const conflictError = new Error(data?.message || '版本冲突');
        (conflictError as Record<string, unknown>).code = 409;
        (conflictError as Record<string, unknown>).serverVersion = data?.data?.version;
        (conflictError as Record<string, unknown>).serverUpdatedAt = data?.data?.updated_at;
        return Promise.reject(conflictError);
      }

      if (status === 429) {
        const retryAfter = error.response.headers['retry-after'];
        const rateLimitError = new Error(data?.message || '请求过于频繁');
        (rateLimitError as Record<string, unknown>).code = 429;
        (rateLimitError as Record<string, unknown>).retryAfter = retryAfter ? parseInt(retryAfter, 10) : 60;
        return Promise.reject(rateLimitError);
      }

      const apiError = new Error(data?.message || '请求失败');
      (apiError as Record<string, unknown>).code = status;
      return Promise.reject(apiError);
    }

    return Promise.reject(new Error('网络错误，请检查网络连接'));
  },
);

export default apiClient;
