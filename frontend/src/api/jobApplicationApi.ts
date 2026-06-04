import axios from 'axios';
import type {
  JobApplication,
  JobApplicationRequest,
  DashboardStats,
  PageResponse,
  ApplicationStatus,
} from '../types';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

export const jobApplicationApi = {
  // Dashboard
  getStats: async (): Promise<DashboardStats> => {
    const { data } = await api.get('/dashboard/stats');
    return data;
  },

  // Applications CRUD
  getAll: async (params?: {
    search?: string;
    status?: ApplicationStatus;
    page?: number;
    size?: number;
  }): Promise<PageResponse<JobApplication>> => {
    const { data } = await api.get('/applications', { params });
    return data;
  },

  getById: async (id: number): Promise<JobApplication> => {
    const { data } = await api.get(`/applications/${id}`);
    return data;
  },

  create: async (request: JobApplicationRequest): Promise<JobApplication> => {
    const { data } = await api.post('/applications', request);
    return data;
  },

  update: async (id: number, request: JobApplicationRequest): Promise<JobApplication> => {
    const { data } = await api.put(`/applications/${id}`, request);
    return data;
  },

  delete: async (id: number): Promise<void> => {
    await api.delete(`/applications/${id}`);
  },
};
