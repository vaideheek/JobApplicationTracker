import { api } from './apiClient';

export interface AuthUser {
  id: number;
  username: string;
  displayName: string | null;
  demoAccount: boolean;
}

export const authApi = {
  login: async (username: string, password: string): Promise<AuthUser> => {
    const { data } = await api.post('/auth/login', { username, password });
    return data;
  },

  signup: async (username: string, password: string, displayName?: string): Promise<AuthUser> => {
    const { data } = await api.post('/auth/signup', { username, password, displayName });
    return data;
  },

  logout: async (): Promise<void> => {
    await api.post('/auth/logout');
  },

  me: async (): Promise<AuthUser> => {
    const { data } = await api.get('/auth/me');
    return data;
  },
};
