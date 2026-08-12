import React, { createContext, useContext, useState, useEffect } from 'react';
import { authApi, type AuthUser } from '../api/authApi';
import { api, csrfManager } from '../api/apiClient';

interface AuthContextType {
  user: AuthUser | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  // Validate the session on mount
  useEffect(() => {
    const initAuth = async () => {
      try {
        // Retrieve initial CSRF token (sets up session context)
        await csrfManager.fetchToken();
        const currentUser = await authApi.me();
        setUser(currentUser);
      } catch (err) {
        setUser(null);
      } finally {
        setLoading(false);
      }
    };
    initAuth();
  }, []);

  // Response interceptor to catch 401s
  useEffect(() => {
    const responseInterceptor = api.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response && error.response.status === 401) {
          const configUrl = error.config?.url || '';
          const isAuthEndpoint = configUrl.includes('/auth/me') || configUrl.includes('/auth/login') || configUrl.includes('/auth/logout');

          setUser(null);
          csrfManager.clearToken();

          if (!isAuthEndpoint && window.location.pathname !== '/login') {
            window.location.href = '/login?expired=true';
          }
        }
        return Promise.reject(error);
      }
    );

    return () => {
      api.interceptors.response.eject(responseInterceptor);
    };
  }, []);

  const login = async (username: string, password: string) => {
    // 1. Fetch pre-login CSRF token
    await csrfManager.fetchToken();
    // 2. Perform authentication call (CSRF header will be automatically injected)
    const loggedInUser = await authApi.login(username, password);
    setUser(loggedInUser);
    // 3. Fetch/refresh post-login rotated CSRF token
    await csrfManager.fetchToken();
  };

  const logout = async () => {
    try {
      await authApi.logout();
    } catch (e) {
      console.error('Logout call failed', e);
    } finally {
      setUser(null);
      csrfManager.clearToken();
      window.location.href = '/login';
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
