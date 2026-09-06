import { useState, useEffect, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  ArrowLeft,
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  FileText,
  RotateCcw,
  ExternalLink,
  HelpCircle,
} from 'lucide-react';
import ApplicationForm from '../components/ApplicationForm';
import ApplicationDocumentsSection, { formatFileSize } from '../components/ApplicationDocumentsSection';
import { jobApplicationApi } from '../api/jobApplicationApi';
import type { JobApplication, JobApplicationRequest } from '../types';

type SubmissionState =
  | 'IDLE'
  | 'CREATING_APPLICATION'
  | 'UPLOADING_DOCUMENTS'
  | 'COMPLETE'
  | 'PARTIAL_FAILURE';

export type UploadFailureType = 'DEFINITE_REJECTION' | 'AMBIGUOUS_TRANSPORT';

interface PendingDocumentItem {
  file: File;
  type: 'CV' | 'COVER_LETTER' | 'OTHER';
}

interface FailedDocumentItem extends PendingDocumentItem {
  failureType: UploadFailureType;
  error: string;
  isRetrySafe: boolean;
}

// Whitelist of existing stack's known definite pre-persistence responses.
// For any other status (including 408 Request Timeout and all 5xx),
// persistence cannot be safely ruled out, so it must be treated as AMBIGUOUS_TRANSPORT.
const DEFINITE_REJECTION_STATUSES = new Set([400, 401, 403, 404, 413, 415]);

/**
 * Classifies an upload error into DEFINITE_REJECTION or AMBIGUOUS_TRANSPORT
 * and extracts a clean, sanitized user-facing message with zero sensitive leakage.
 */
export function classifyUploadError(err: any): {
  failureType: UploadFailureType;
  message: string;
} {
  // 1. Definite server response received
  if (err && err.response) {
    const status = err.response.status;

    // Specific whitelisted 4xx client errors where current backend / proxy semantics
    // establish pre-persistence rejection.
    if (DEFINITE_REJECTION_STATUSES.has(status)) {
      const rawMsg = err.response.data?.message || err.response.data?.error;
      let cleanMsg = '';

      if (typeof rawMsg === 'string' && rawMsg.trim()) {
        // Strip any Java exception names (e.g. "IllegalArgumentException: ...")
        cleanMsg = rawMsg.replace(/^[a-zA-Z0-9_.]+Exception:\s*/, '').trim();
        // Remove filesystem paths or internal symbols
        cleanMsg = cleanMsg.replace(/\/[\w./-]+/g, '').trim();
        if (cleanMsg.length > 150) {
          cleanMsg = cleanMsg.substring(0, 147) + '...';
        }
      }

      if (!cleanMsg) {
        if (status === 413) {
          cleanMsg = 'File size exceeds the 10 MB limit.';
        } else if (status === 403) {
          cleanMsg = 'Upload not permitted or demo restricted.';
        } else if (status === 401) {
          cleanMsg = 'Authentication required. Please log in again.';
        } else if (status === 404) {
          cleanMsg = 'Application record not found.';
        } else if (status === 415) {
          cleanMsg = 'Unsupported document format. Please provide a valid PDF or DOCX.';
        } else if (status === 400) {
          cleanMsg = 'Invalid file format or corrupted document.';
        } else {
          cleanMsg = 'The server rejected this file. Ensure it is a valid PDF or DOCX under 10 MB.';
        }
      }

      return {
        failureType: 'DEFINITE_REJECTION',
        message: cleanMsg,
      };
    }

    // 408 Request Timeout: client/proxy timed out waiting for server, cannot confirm non-persistence
    if (status === 408) {
      return {
        failureType: 'AMBIGUOUS_TRANSPORT',
        message: 'Request timed out during upload. Upload status is uncertain.',
      };
    }

    // 502 / 504 Gateway / Proxy timeouts: Request may have reached backend before proxy timed out
    if (status === 502 || status === 504) {
      return {
        failureType: 'AMBIGUOUS_TRANSPORT',
        message: 'Gateway timeout during upload. Upload status is uncertain.',
      };
    }

    // All other 5xx errors: Backend or proxy may have processed upload before dropping connection
    if (status >= 500) {
      return {
        failureType: 'AMBIGUOUS_TRANSPORT',
        message: 'Server error encountered during upload. Upload status could not be confirmed.',
      };
    }

    // Any other unexpected 4xx not proven to be pre-persistence
    return {
      failureType: 'AMBIGUOUS_TRANSPORT',
      message: `Upload request returned unexpected status (${status}). Upload status could not be confirmed.`,
    };
  }

  // 2. No HTTP response: Network error, timeout, aborted connection, offline
  // Server might have committed the document before the connection was lost.
  return {
    failureType: 'AMBIGUOUS_TRANSPORT',
    message: 'Network connection lost or timed out. Upload status could not be confirmed.',
  };
}

/**
 * Checks whether retrying an upload is guaranteed safe against duplicate document creation.
 *
 * For ALL document types (CV, COVER_LETTER, OTHER):
 * If failure classification is AMBIGUOUS_TRANSPORT, isRetrySafe = false.
 * Overlapping concurrent requests or unconfirmed server writes mean replacement semantics
 * alone do not provide true idempotency.
 *
 * Automatic retry is permitted ONLY for DEFINITE_REJECTION (e.g. 4xx validation failures
 * where backend guarantees the document was rejected before persistence).
 */
export function isRetrySafe(
  _type: 'CV' | 'COVER_LETTER' | 'OTHER',
  failureType: UploadFailureType
): boolean {
  return failureType === 'DEFINITE_REJECTION';
}

export default function AddApplication() {
  const navigate = useNavigate();

  // Document state
  const [cvFile, setCvFile] = useState<File | null>(null);
  const [coverLetterFile, setCoverLetterFile] = useState<File | null>(null);
  const [otherFiles, setOtherFiles] = useState<File[]>([]);

  // Submission state machine
  const [submissionState, setSubmissionState] = useState<SubmissionState>('IDLE');
  const [createdApp, setCreatedApp] = useState<JobApplication | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  // Synchronous mutex locks preventing double submission / retry races before async React re-renders
  const createLockRef = useRef(false);
  const retryLockRef = useRef(false);

  // Upload progress tracking
  const [uploadProgress, setUploadProgress] = useState<{
    current: number;
    total: number;
    currentFileName: string;
  } | null>(null);

  // Partial failure lists
  const [successDocs, setSuccessDocs] = useState<PendingDocumentItem[]>([]);
  const [failedDocs, setFailedDocs] = useState<FailedDocumentItem[]>([]);
  const [isRetrying, setIsRetrying] = useState(false);

  const isSubmitting =
    submissionState === 'CREATING_APPLICATION' ||
    submissionState === 'UPLOADING_DOCUMENTS';

  // Prevent accidental navigation or window closing during active submission
  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (isSubmitting) {
        e.preventDefault();
        e.returnValue = '';
      }
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [isSubmitting]);

  // Main submission workflow
  const handleSubmit = async (data: JobApplicationRequest) => {
    // Synchronous mutex guard: reject immediate duplicate submissions (double clicks, repeated Enter)
    if (createLockRef.current || submissionState !== 'IDLE') return;
    createLockRef.current = true;

    setFormError(null);
    setSubmissionState('CREATING_APPLICATION');

    // 1. Create the application entity first
    let app: JobApplication;
    try {
      app = await jobApplicationApi.create(data);
      setCreatedApp(app);
      // NOTE: Once application creation succeeds, createLockRef.current remains true
      // for the lifetime of this component instance to ensure the application is never
      // created a second time during document uploads or partial failure recovery.
    } catch (err: any) {
      createLockRef.current = false;
      setSubmissionState('IDLE');
      const rawMsg = err.response?.data?.message || err.message;
      const cleanMsg = typeof rawMsg === 'string' && rawMsg.trim()
        ? rawMsg.replace(/^[a-zA-Z0-9_.]+Exception:\s*/, '').trim()
        : 'Failed to create application. Please check your inputs and try again.';
      setFormError(cleanMsg);
      return;
    }

    // 2. Gather selected documents in deterministic sequence: CV, Cover Letter, Other
    const pendingUploads: PendingDocumentItem[] = [];
    if (cvFile) pendingUploads.push({ file: cvFile, type: 'CV' });
    if (coverLetterFile) pendingUploads.push({ file: coverLetterFile, type: 'COVER_LETTER' });
    otherFiles.forEach((file) => pendingUploads.push({ file, type: 'OTHER' }));

    // If no documents attached, complete immediately
    if (pendingUploads.length === 0) {
      setSubmissionState('COMPLETE');
      navigate(`/applications/${app.id}`);
      return;
    }

    // 3. Sequentially upload documents
    setSubmissionState('UPLOADING_DOCUMENTS');
    const successful: PendingDocumentItem[] = [];
    const failed: FailedDocumentItem[] = [];

    for (let i = 0; i < pendingUploads.length; i++) {
      const item = pendingUploads[i];
      setUploadProgress({
        current: i + 1,
        total: pendingUploads.length,
        currentFileName: item.file.name,
      });

      try {
        await jobApplicationApi.uploadDocument(app.id, item.file, item.type);
        successful.push(item);
      } catch (uploadErr: any) {
        const { failureType, message } = classifyUploadError(uploadErr);
        const retrySafe = isRetrySafe(item.type, failureType);

        failed.push({
          file: item.file,
          type: item.type,
          failureType,
          error: message,
          isRetrySafe: retrySafe,
        });
      }
    }

    setUploadProgress(null);

    // 4. Evaluate outcome: complete vs partial failure
    if (failed.length === 0) {
      setSubmissionState('COMPLETE');
      navigate(`/applications/${app.id}`);
    } else {
      setSuccessDocs(successful);
      setFailedDocs(failed);
      setSubmissionState('PARTIAL_FAILURE');
    }
  };

  // Retry ONLY safe failed uploads against the already created application
  const handleRetryFailedUploads = async () => {
    // Synchronous mutex guard against double activation
    if (!createdApp || retryLockRef.current || isRetrying) return;
    retryLockRef.current = true;

    // Filter only files that are guaranteed safe to retry
    const retryableItems = failedDocs.filter((d) => d.isRetrySafe);
    if (retryableItems.length === 0) {
      retryLockRef.current = false;
      return;
    }

    setIsRetrying(true);
    try {
      const stillFailed: FailedDocumentItem[] = [];
      const newlySucceeded: PendingDocumentItem[] = [];

      for (let i = 0; i < retryableItems.length; i++) {
        const item = retryableItems[i];
        setUploadProgress({
          current: i + 1,
          total: retryableItems.length,
          currentFileName: item.file.name,
        });

        try {
          await jobApplicationApi.uploadDocument(createdApp.id, item.file, item.type);
          newlySucceeded.push({ file: item.file, type: item.type });
        } catch (retryErr: any) {
          const { failureType, message } = classifyUploadError(retryErr);
          const retrySafe = isRetrySafe(item.type, failureType);

          stillFailed.push({
            file: item.file,
            type: item.type,
            failureType,
            error: message,
            isRetrySafe: retrySafe,
          });
        }
      }

      setUploadProgress(null);

      setSuccessDocs((prev) => [...prev, ...newlySucceeded]);

      // Keep items that were not retried (e.g. uncertain files) plus any that failed again
      const unretriedItems = failedDocs.filter((d) => !d.isRetrySafe);
      const updatedFailed = [...unretriedItems, ...stillFailed];
      setFailedDocs(updatedFailed);

      // If everything succeeded completely, navigate to application
      if (updatedFailed.length === 0) {
        setSubmissionState('COMPLETE');
        navigate(`/applications/${createdApp.id}`);
      }
    } finally {
      retryLockRef.current = false;
      setIsRetrying(false);
      setUploadProgress(null);
    }
  };

  // Human-readable document type labels
  const getDocTypeLabel = (type: 'CV' | 'COVER_LETTER' | 'OTHER') => {
    switch (type) {
      case 'CV':
        return 'CV / Resume';
      case 'COVER_LETTER':
        return 'Cover Letter';
      case 'OTHER':
        return 'Other Document';
    }
  };

  // Dynamic submit button label
  const getSubmitButtonText = () => {
    if (submissionState === 'CREATING_APPLICATION') {
      return 'Creating application...';
    }
    if (submissionState === 'UPLOADING_DOCUMENTS' && uploadProgress) {
      return `Uploading documents (${uploadProgress.current}/${uploadProgress.total})...`;
    }
    if (submissionState === 'UPLOADING_DOCUMENTS') {
      return 'Uploading documents...';
    }
    return 'Create Application';
  };

  // Render Partial Failure Screen
  if (submissionState === 'PARTIAL_FAILURE' && createdApp) {
    const totalDocs = successDocs.length + failedDocs.length;
    const retryableDocs = failedDocs.filter((d) => d.isRetrySafe);
    const ambiguousDocs = failedDocs.filter((d) => !d.isRetrySafe);

    return (
      <div className="space-y-6">
        <div className="rounded-xl border border-amber-300 bg-amber-50 p-6 shadow-sm">
          <div className="flex items-start gap-4">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-amber-100 text-amber-600">
              <AlertTriangle size={26} />
            </div>
            <div className="flex-1 min-w-0">
              <h2 className="text-lg font-bold text-amber-900">
                Application Created With Document Upload Issues
              </h2>
              <p className="mt-1 text-sm text-amber-800">
                Your application for <strong className="text-amber-950">{createdApp.jobTitle}</strong> at{' '}
                <strong className="text-amber-950">{createdApp.companyName}</strong> was saved successfully (Application ID #{createdApp.id}), but{' '}
                <strong>{failedDocs.length}</strong> of <strong>{totalDocs}</strong> document{totalDocs !== 1 ? 's' : ''} encountered issues during upload.
              </p>
              <p className="mt-1 text-xs text-amber-700">
                The application was not rolled back. You can retry safe uploads below or view the application to verify and manage your documents.
              </p>
            </div>
          </div>
        </div>

        {/* Upload Status Card */}
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm space-y-6">
          <h3 className="text-base font-semibold text-slate-800">Document Upload Summary</h3>

          {/* Succeeded Files */}
          {successDocs.length > 0 && (
            <div className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-emerald-700">
                Successfully Uploaded ({successDocs.length})
              </span>
              <div className="space-y-2">
                {successDocs.map((doc, idx) => (
                  <div
                    key={`success-${idx}`}
                    className="flex items-center justify-between gap-3 rounded-lg bg-emerald-50/60 p-3 border border-emerald-100"
                  >
                    <div className="flex items-center gap-2.5 overflow-hidden">
                      <CheckCircle2 size={18} className="text-emerald-600 shrink-0" />
                      <FileText size={16} className="text-emerald-700 shrink-0" />
                      <div className="flex flex-col min-w-0">
                        <span className="text-xs font-semibold text-slate-800 truncate">
                          {doc.file.name}
                        </span>
                        <span className="text-[11px] text-slate-500">
                          {getDocTypeLabel(doc.type)} · {formatFileSize(doc.file.size)}
                        </span>
                      </div>
                    </div>
                    <span className="text-xs font-medium text-emerald-700 bg-emerald-100 px-2 py-0.5 rounded-full shrink-0">
                      Uploaded
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Failed / Uncertain Files */}
          {failedDocs.length > 0 && (
            <div className="space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-700">
                Attention Required ({failedDocs.length})
              </span>
              <div className="space-y-2">
                {failedDocs.map((doc, idx) => {
                  const isAmbiguous = !doc.isRetrySafe;

                  return (
                    <div
                      key={`failed-${idx}`}
                      className={`flex flex-col gap-2 rounded-lg p-3 border ${
                        isAmbiguous
                          ? 'bg-amber-50/70 border-amber-200'
                          : 'bg-rose-50/60 border-rose-200'
                      }`}
                    >
                      <div className="flex items-center justify-between gap-3">
                        <div className="flex items-center gap-2.5 overflow-hidden">
                          {isAmbiguous ? (
                            <HelpCircle size={18} className="text-amber-600 shrink-0" />
                          ) : (
                            <XCircle size={18} className="text-rose-600 shrink-0" />
                          )}
                          <FileText
                            size={16}
                            className={isAmbiguous ? 'text-amber-700 shrink-0' : 'text-rose-700 shrink-0'}
                          />
                          <div className="flex flex-col min-w-0">
                            <span className="text-xs font-semibold text-slate-800 truncate">
                              {doc.file.name}
                            </span>
                            <span className="text-[11px] text-slate-500">
                              {getDocTypeLabel(doc.type)} · {formatFileSize(doc.file.size)}
                            </span>
                          </div>
                        </div>
                        <span
                          className={`text-xs font-medium px-2 py-0.5 rounded-full shrink-0 ${
                            isAmbiguous
                              ? 'text-amber-800 bg-amber-100 border border-amber-300'
                              : 'text-rose-700 bg-rose-100'
                          }`}
                        >
                          {isAmbiguous ? 'Upload status uncertain' : 'Upload Failed'}
                        </span>
                      </div>

                      {isAmbiguous ? (
                        <p className="text-xs text-amber-800 bg-white/80 p-2.5 rounded border border-amber-200 leading-relaxed">
                          <strong>Note:</strong> The application was created, but we could not confirm whether this file was saved because the connection was interrupted. Review the application's Documents section before uploading it again.
                        </p>
                      ) : (
                        <p className="text-xs text-rose-700 bg-white/70 p-2 rounded border border-rose-200">
                          <strong>Reason:</strong> {doc.error}
                        </p>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Ambiguous files notice when retryable items exist */}
          {ambiguousDocs.length > 0 && retryableDocs.length > 0 && (
            <div className="rounded-lg bg-amber-50 p-3 text-xs text-amber-800 border border-amber-200 flex items-start gap-2">
              <AlertCircle size={16} className="text-amber-600 shrink-0 mt-0.5" />
              <span>
                <strong>{ambiguousDocs.length}</strong> file(s) have uncertain upload status and cannot be automatically retried to prevent duplicate documents. Safe uploads ({retryableDocs.length}) will be retried.
              </span>
            </div>
          )}

          {/* Progress during retry */}
          {isRetrying && uploadProgress && (
            <div
              className="rounded-lg bg-brand-50 p-3 text-xs text-brand-800 border border-brand-200 flex items-center gap-2"
              aria-live="polite"
            >
              <div className="h-4 w-4 animate-spin rounded-full border-2 border-brand-600 border-t-transparent" />
              <span>
                Retrying upload {uploadProgress.current} of {uploadProgress.total}:{' '}
                <strong>{uploadProgress.currentFileName}</strong>...
              </span>
            </div>
          )}

          {/* Actions */}
          <div className="flex flex-wrap items-center justify-between gap-3 pt-4 border-t border-slate-100">
            {retryableDocs.length > 0 ? (
              <button
                type="button"
                onClick={handleRetryFailedUploads}
                disabled={isRetrying}
                className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50 transition-colors"
              >
                <RotateCcw size={16} className={isRetrying ? 'animate-spin' : ''} />
                {isRetrying
                  ? 'Retrying Uploads...'
                  : retryableDocs.length < failedDocs.length
                  ? `Retry Safe Uploads (${retryableDocs.length})`
                  : 'Retry Failed Uploads'}
              </button>
            ) : (
              <p className="text-xs text-slate-500 italic">
                Automatic retry is unavailable for uncertain files to prevent duplicates.
              </p>
            )}

            <Link
              to={`/applications/${createdApp.id}`}
              className={`inline-flex items-center gap-1.5 rounded-lg px-5 py-2.5 text-sm font-semibold transition-colors ${
                retryableDocs.length === 0
                  ? 'bg-brand-600 text-white hover:bg-brand-700 shadow-sm'
                  : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 hover:border-slate-400'
              }`}
            >
              <span>View Application</span>
              <ExternalLink size={16} />
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6">
        <button
          onClick={() => navigate(-1)}
          disabled={isSubmitting}
          className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700 disabled:opacity-50 transition-colors"
        >
          <ArrowLeft size={16} /> Back
        </button>
        <h1 className="text-2xl font-bold text-slate-900">Add Application</h1>
        <p className="mt-1 text-sm text-slate-500">Track a new job application</p>
      </div>

      {/* Top Form Error Banner */}
      {formError && (
        <div className="mb-6 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 shadow-sm flex items-start gap-3">
          <AlertCircle size={20} className="text-rose-600 shrink-0 mt-0.5" />
          <div className="flex-1">
            <h4 className="font-semibold text-rose-900">Unable to create application</h4>
            <p className="mt-0.5 text-xs text-rose-700">{formError}</p>
          </div>
        </div>
      )}

      {/* Live Upload Progress Announcement */}
      {isSubmitting && (
        <div
          aria-live="polite"
          className="mb-6 rounded-xl border border-brand-200 bg-brand-50 p-4 text-sm text-brand-900 shadow-sm flex items-center gap-3"
        >
          <div className="h-5 w-5 animate-spin rounded-full border-2 border-brand-600 border-t-transparent shrink-0" />
          <div>
            <p className="font-semibold">
              {submissionState === 'CREATING_APPLICATION'
                ? 'Creating application record...'
                : uploadProgress
                ? `Uploading document ${uploadProgress.current} of ${uploadProgress.total}: ${uploadProgress.currentFileName}...`
                : 'Uploading documents...'}
            </p>
            <p className="text-xs text-brand-700 mt-0.5">
              Please stay on this page until processing is complete.
            </p>
          </div>
        </div>
      )}

      <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <ApplicationForm
          onSubmit={handleSubmit}
          submitLabel="Create Application"
          isSubmitting={isSubmitting}
          submitButtonText={getSubmitButtonText()}
          submitDisabled={isSubmitting}
        >
          {/* Optional Document Uploads Section */}
          <ApplicationDocumentsSection
            cvFile={cvFile}
            setCvFile={setCvFile}
            coverLetterFile={coverLetterFile}
            setCoverLetterFile={setCoverLetterFile}
            otherFiles={otherFiles}
            setOtherFiles={setOtherFiles}
            disabled={isSubmitting}
          />
        </ApplicationForm>
      </div>
    </div>
  );
}
