export type ApplicationStatus =
  | 'APPLIED'
  | 'IN_REVIEW'
  | 'ASSESSMENT'
  | 'INTERVIEW'
  | 'OFFER'
  | 'REJECTED'
  | 'WITHDRAWN';

export type ApplicationPriority = 'LOW' | 'MEDIUM' | 'HIGH';

export interface JobApplication {
  id: number;
  companyName: string;
  jobTitle: string;
  location: string | null;
  jobUrl: string | null;
  dateApplied: string | null;
  status: ApplicationStatus;
  stage: string | null;
  recruiterEmail: string | null;
  notes: string | null;
  source: string | null;
  companyCareerUrl: string | null;
  salaryRange: string | null;
  priority: ApplicationPriority | null;
  followUpDate: string | null;
  deadlineDate: string | null;
  createdAt: string;
  lastUpdatedAt: string;
  statusHistory?: StatusHistoryEntry[];
}

export interface JobApplicationRequest {
  companyName: string;
  jobTitle: string;
  location?: string;
  jobUrl?: string;
  dateApplied?: string;
  status: ApplicationStatus;
  stage?: string;
  recruiterEmail?: string;
  notes?: string;
  source?: string;
  companyCareerUrl?: string;
  salaryRange?: string;
  priority?: ApplicationPriority;
  followUpDate?: string;
  deadlineDate?: string;
}

export interface StatusHistoryEntry {
  id: number;
  fromStatus: ApplicationStatus | null;
  toStatus: ApplicationStatus;
  changedAt: string;
  note: string | null;
}

export interface DashboardStats {
  totalApplications: number;
  interviews: number;
  offers: number;
  rejections: number;
  applicationsThisWeek: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export const STATUS_OPTIONS: ApplicationStatus[] = [
  'APPLIED',
  'IN_REVIEW',
  'ASSESSMENT',
  'INTERVIEW',
  'OFFER',
  'REJECTED',
  'WITHDRAWN',
];

export const PRIORITY_OPTIONS: ApplicationPriority[] = ['LOW', 'MEDIUM', 'HIGH'];

export const STATUS_LABELS: Record<ApplicationStatus, string> = {
  APPLIED: 'Applied',
  IN_REVIEW: 'In Review',
  ASSESSMENT: 'Assessment',
  INTERVIEW: 'Interview',
  OFFER: 'Offer',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
};

export const STATUS_COLORS: Record<ApplicationStatus, string> = {
  APPLIED: 'bg-blue-100 text-blue-800',
  IN_REVIEW: 'bg-amber-100 text-amber-800',
  ASSESSMENT: 'bg-purple-100 text-purple-800',
  INTERVIEW: 'bg-cyan-100 text-cyan-800',
  OFFER: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-red-100 text-red-800',
  WITHDRAWN: 'bg-gray-100 text-gray-600',
};
