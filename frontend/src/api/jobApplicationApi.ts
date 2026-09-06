import { api } from './apiClient';
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
  BulkScanResponse,
  BulkConfirmRequest,
  BulkConfirmResponse,
  PeriodAnalyticsResponse,
  CurrentPipelineResponse,
} from '../types';

export const jobApplicationApi = {
  // Dashboard
  getStats: async (): Promise<DashboardStats> => {
    const { data } = await api.get('/dashboard/stats');
    return data;
  },

  getPeriodAnalytics: async (params?: {
    range?: string;
    from?: string;
    to?: string;
    compare?: boolean;
    timezone?: string;
  }): Promise<PeriodAnalyticsResponse> => {
    let timezone = params?.timezone;
    if (!timezone && typeof Intl !== 'undefined' && Intl.DateTimeFormat) {
      try {
        timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
      } catch {
        // fallback to undefined/server UTC
      }
    }
    const queryParams: Record<string, any> = { ...params };
    if (timezone) {
      queryParams.timezone = timezone;
    }
    const { data } = await api.get('/dashboard/period', { params: queryParams });
    return data;
  },

  getCurrentPipeline: async (): Promise<CurrentPipelineResponse> => {
    const { data } = await api.get('/dashboard/pipeline');
    return data;
  },

  // Applications CRUD
  getAll: async (params?: {
    search?: string;
    status?: string;
    priority?: string;
    dateFrom?: string;
    dateTo?: string;
    documentState?: string;
    sortBy?: string;
    sortDir?: string;
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
    const { data } = await api.post(`/applications/${applicationId}/documents`, formData);
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

  scanZipFiles: async (
    manifest: File,
    laptopZip: File,
    googleDriveZip: File
  ): Promise<BulkScanResponse> => {
    const formData = new FormData();
    formData.append('manifest', manifest);
    formData.append('laptopZip', laptopZip);
    formData.append('googleDriveZip', googleDriveZip);
    const { data } = await api.post('/bulk-import/scan', formData);
    return data;
  },

  confirmBulkImport: async (request: BulkConfirmRequest): Promise<BulkConfirmResponse> => {
    const { data } = await api.post('/bulk-import/confirm', request);
    return data;
  },
};
