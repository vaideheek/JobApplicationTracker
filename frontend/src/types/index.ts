export type ApplicationStatus =
  | 'APPLIED'
  | 'IN_REVIEW'
  | 'ASSESSMENT'
  | 'INTERVIEW'
  | 'OFFER'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'NO_RESPONSE';

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
  jobDescription: string | null;
  jobDescriptionSummary: string | null;
  originalJobUrl: string | null;
  matchScore: number | null;
  matchedSkills: string | null;
  missingSkills: string | null;
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
  jobDescription?: string;
  jobDescriptionSummary?: string;
  originalJobUrl?: string;
  matchScore?: number;
  matchedSkills?: string;
  missingSkills?: string;
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
  'NO_RESPONSE',
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
  NO_RESPONSE: 'No Response',
};

export const STATUS_COLORS: Record<ApplicationStatus, string> = {
  APPLIED: 'bg-blue-100 text-blue-800',
  IN_REVIEW: 'bg-amber-100 text-amber-800',
  ASSESSMENT: 'bg-purple-100 text-purple-800',
  INTERVIEW: 'bg-cyan-100 text-cyan-800',
  OFFER: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-red-100 text-red-800',
  WITHDRAWN: 'bg-gray-100 text-gray-600',
  NO_RESPONSE: 'bg-slate-100 text-slate-600',
};

export interface EmailParseRequest {
  rawEmailText: string;
}

export interface EmailParseResponse {
  companyName: string;
  jobTitle: string;
  status: ApplicationStatus;
  stage: string | null;
  recruiterEmail: string | null;
  source: string | null;
  importantDate: string | null;
  suggestedNotes: string | null;
  confidenceScore: 'HIGH' | 'MEDIUM' | 'LOW';
  needsReview: boolean;
  suggestedPriority?: ApplicationPriority;
  priorityExplanation?: string;
  priority?: ApplicationPriority;
}

export interface PrioritySuggestionResponse {
  priority: ApplicationPriority;
  explanation: string;
}

export interface CompanyAppCount {
  companyName: string;
  count: number;
}

export interface ApplicationDocument {
  id: number;
  fileName: string;
  fileType: string;
  documentType: 'CV' | 'COVER_LETTER' | 'OTHER';
  filePath: string;
  uploadedAt: string;
}

export interface RecommendedAction {
  type: 'FOLLOW_UP' | 'INTERVIEW_PREP' | 'ASSESSMENT_COMPLETE' | 'REVIEW_STALE';
  applicationId: number;
  companyName: string;
  jobTitle: string;
  message: string;
  detail: string;
}

export interface DashboardInsightsResponse {
  highPriorityCount: number;
  followUpNeededCount: number;
  upcomingInterviewsCount: number;
  staleApplicationsCount: number;
  missingDocumentsCount: number;
  topCompanies: CompanyAppCount[];
  responseRate: number;
  interviewConversionRate: number;
  offerConversionRate: number;
  recommendedActions: RecommendedAction[];
}

export interface ScannedDocumentPreview {
  tempDocId: string;
  fileName: string;
  documentType: string;
  fileSize: number;
  sha256: string;
  validationStatus: string;
}

export interface ScannedApplicationGroup {
  tempAppId: string;
  companyName: string;
  jobTitle: string;
  dateApplied: string | null;
  status: string;
  priority: string;
  isDuplicate: boolean;
  existingApplicationId: number | null;
  stage: string | null;
  source: string | null;
  notes: string | null;
  documents: ScannedDocumentPreview[];
}

export interface BulkScanResponse {
  scanId: string;
  applications: ScannedApplicationGroup[];
  unassignedFiles: ScannedDocumentPreview[];
  canConfirm: boolean;
  validationIssues: string[];
}

export interface BulkConfirmDocument {
  tempDocId: string;
  documentType: string;
}

export interface BulkConfirmApplication {
  tempAppId: string | null;
  companyName: string;
  jobTitle: string;
  status: string;
  priority: string;
  dateApplied: string | null;
  stage: string | null;
  source: string | null;
  notes: string | null;
  documents: BulkConfirmDocument[];
}

export interface BulkConfirmRequest {
  scanId: string;
  applications: BulkConfirmApplication[];
}

export interface DocumentFailureInfo {
  fileName: string;
  error: string;
}

export interface BulkConfirmResponse {
  successCount: number;
  failureCount: number;
  documentFailures: DocumentFailureInfo[];
}
