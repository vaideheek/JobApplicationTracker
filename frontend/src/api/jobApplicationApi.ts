import axios from 'axios';
import { getApiBaseUrl } from './apiUrl';
import type {
  JobApplication,
  JobApplicationRequest,
  DashboardStats,
  PageResponse,
  ApplicationStatus,
  EmailParseRequest,
  EmailParseResponse,
  PrioritySuggestionResponse,
  DashboardInsightsResponse,
  ApplicationDocument,
} from '../types';

export const api = axios.create({
  baseURL: getApiBaseUrl(),
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

  // Email Import
  parseEmail: async (request: EmailParseRequest): Promise<EmailParseResponse> => {
    const { data } = await api.post('/email-import/parse', request);
    return data;
  },

  confirmEmailImport: async (response: EmailParseResponse): Promise<JobApplication> => {
    const { data } = await api.post('/email-import/confirm', response);
    return data;
  },

  suggestPriority: async (request: JobApplicationRequest): Promise<PrioritySuggestionResponse> => {
    const { data } = await api.post('/applications/suggest-priority', request);
    return data;
  },

  getInsights: async (): Promise<DashboardInsightsResponse> => {
    const { data } = await api.get('/dashboard/insights');
    return data;
  },

  // Documents
  getDocuments: async (applicationId: number): Promise<ApplicationDocument[]> => {
    const { data } = await api.get(`/applications/${applicationId}/documents`);
    return data;
  },

  uploadDocument: async (
    applicationId: number,
    file: File,
    documentType: 'CV' | 'COVER_LETTER' | 'OTHER'
  ): Promise<ApplicationDocument> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('documentType', documentType);
    const { data } = await api.post(`/applications/${applicationId}/documents`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return data;
  },

  deleteDocument: async (applicationId: number, documentId: number): Promise<void> => {
    await api.delete(`/applications/${applicationId}/documents/${documentId}`);
  },

  downloadDocument: async (applicationId: number, documentId: number): Promise<Blob> => {
    const { data } = await api.get(`/applications/${applicationId}/documents/${documentId}`, {
      responseType: 'blob',
    });
    return data;
  },
};
