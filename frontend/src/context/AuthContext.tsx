import React, { createContext, useContext, useState, useEffect } from 'react';
import { api } from '../api/jobApplicationApi';

interface AuthContextType {
  isAuthenticated: boolean;
  token: string | null;
  adminEmail: string | null;
  login: (token: string, email: string, role: string) => void;
  logout: () => void;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(sessionStorage.getItem('access_token'));
  const [adminEmail, setAdminEmail] = useState<string | null>(sessionStorage.getItem('admin_email'));
  const [loading, setLoading] = useState(true);

  // Set up request interceptor to inject JWT token
  useEffect(() => {
    const requestInterceptor = api.interceptors.request.use(
      (config) => {
        const storedToken = sessionStorage.getItem('access_token');
        if (storedToken && config.headers) {
          config.headers.Authorization = `Bearer ${storedToken}`;
        }
        return config;
      },
      (error) => Promise.reject(error)
    );

    return () => {
      api.interceptors.request.eject(requestInterceptor);
    };
  }, []);

  // Set up response interceptor to catch 401s
  useEffect(() => {
    const responseInterceptor = api.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response && error.response.status === 401) {
          sessionStorage.removeItem('access_token');
          sessionStorage.removeItem('admin_email');
          setToken(null);
          setAdminEmail(null);
          if (window.location.pathname !== '/login') {
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

  // Validate the token against the backend on mount
  useEffect(() => {
    const checkAuth = async () => {
      if (token) {
        try {
          const { data } = await api.get('/auth/me');
          setAdminEmail(data.email);
        } catch (e) {
          // If fetch fails, the response interceptor handles the 401 reset
          setToken(null);
          setAdminEmail(null);
        }
      }
      setLoading(false);
    };

    checkAuth();
  }, [token]);

  const login = (jwt: string, email: string, role: string) => {
    sessionStorage.setItem('access_token', jwt);
    sessionStorage.setItem('admin_email', email);
    setToken(jwt);
    setAdminEmail(email);
  };

  const logout = () => {
    sessionStorage.removeItem('access_token');
    sessionStorage.removeItem('admin_email');
    setToken(null);
    setAdminEmail(null);
    window.location.href = '/login';
  };

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated: !!token,
        token,
        adminEmail,
        login,
        logout,
        loading,
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
