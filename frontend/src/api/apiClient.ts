import axios from 'axios';
import { getApiBaseUrl } from './apiUrl';

// Create a single shared Axios instance
export const api = axios.create({
  baseURL: getApiBaseUrl(),
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // send/receive the session cookie
});

// In-memory storage for the CSRF token
let csrfToken: string | null = null;
let csrfHeaderName = 'X-CSRF-TOKEN';

export const csrfManager = {
  getToken: () => csrfToken,
  getHeaderName: () => csrfHeaderName,

  // Fetches a fresh CSRF token from GET /api/auth/csrf
  fetchToken: async (): Promise<string> => {
    try {
      const { data } = await api.get('/auth/csrf');
      csrfToken = data.token;
      csrfHeaderName = data.headerName || 'X-CSRF-TOKEN';
      return data.token;
    } catch (err) {
      console.error('Failed to fetch CSRF token:', err);
      throw err;
    }
  },

  clearToken: () => {
    csrfToken = null;
  }
};

// Request Interceptor: Attach CSRF header to mutating requests
api.interceptors.request.use(
  (config) => {
    const method = config.method?.toUpperCase();
    const isWrite = method === 'POST' || method === 'PUT' || method === 'PATCH' || method === 'DELETE';

    if (isWrite && csrfToken) {
      if (config.headers) {
        config.headers[csrfHeaderName] = csrfToken;
      }
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Flag to prevent infinite retry loops
let isRetryingCsrf = false;

// Response Interceptor: Handle CSRF retries on 403 Forbidden
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Check if error is 403 and this request hasn't been retried yet
    if (error.response && error.response.status === 403 && !originalRequest._retry && !isRetryingCsrf) {
      const code = error.response.data?.code || '';
      const isCsrfError = code === 'CSRF_INVALID';

      if (isCsrfError) {
        const method = originalRequest.method?.toUpperCase();
        const isWrite = method === 'POST' || method === 'PUT' || method === 'PATCH' || method === 'DELETE';

        if (isWrite) {
          // If the request contains multipart upload payload (FormData), do NOT retry automatically.
          // This avoids duplicate upload attempts. We refresh the token in-memory and return a clear error instead.
          const isFileUpload = originalRequest.data instanceof FormData;
          if (isFileUpload) {
            try {
              await csrfManager.fetchToken(); // refresh token in-memory for future requests
            } catch (csrfErr) {
              console.error('Failed to update CSRF token on upload error:', csrfErr);
            }
            return Promise.reject(new Error('CSRF validation failed for file upload. A fresh token has been retrieved. Please try uploading the file again.'));
          }

          originalRequest._retry = true;
          isRetryingCsrf = true;

          try {
            // Fetch a fresh token
            const token = await csrfManager.fetchToken();

            // Re-attach header and retry original request
            if (originalRequest.headers) {
              originalRequest.headers[csrfHeaderName] = token;
            }
            isRetryingCsrf = false;
            return api(originalRequest);
          } catch (retryErr) {
            isRetryingCsrf = false;
            return Promise.reject(retryErr);
          }
        }
      }
    }

    return Promise.reject(error);
  }
);
