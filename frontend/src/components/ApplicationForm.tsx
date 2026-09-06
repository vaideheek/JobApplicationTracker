import { useState, useEffect } from 'react';
import { STATUS_OPTIONS, STATUS_LABELS, PRIORITY_OPTIONS } from '../types';
import type { JobApplicationRequest, ApplicationStatus, ApplicationPriority } from '../types';
import { jobApplicationApi } from '../api/jobApplicationApi';
import { useAuth } from '../context/AuthContext';

interface ApplicationFormProps {
  initialData?: JobApplicationRequest;
  onSubmit: (data: JobApplicationRequest) => Promise<void>;
  submitLabel: string;
  children?: React.ReactNode;
  isSubmitting?: boolean;
  submitButtonText?: string;
  submitDisabled?: boolean;
}

const emptyForm: JobApplicationRequest = {
  companyName: '',
  jobTitle: '',
  location: '',
  jobUrl: '',
  dateApplied: new Date().toISOString().split('T')[0],
  status: 'APPLIED',
  stage: '',
  recruiterEmail: '',
  notes: '',
  source: '',
  companyCareerUrl: '',
  salaryRange: '',
  priority: undefined,
  followUpDate: '',
  deadlineDate: '',
  originalJobUrl: '',
  jobDescription: '',
};

export default function ApplicationForm({
  initialData,
  onSubmit,
  submitLabel,
  children,
  isSubmitting,
  submitButtonText,
  submitDisabled = false,
}: ApplicationFormProps) {
  const { user } = useAuth();
  const isDemo = user?.demoAccount || false;
  const [form, setForm] = useState<JobApplicationRequest>(initialData || emptyForm);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [suggestion, setSuggestion] = useState<{ priority: ApplicationPriority; explanation: string } | null>(null);

  useEffect(() => {
    const delayDebounce = setTimeout(async () => {
      try {
        const res = await jobApplicationApi.suggestPriority(form);
        setSuggestion(res);
      } catch (err) {
        console.error('Failed to get priority suggestion', err);
      }
    }, 400);

    return () => clearTimeout(delayDebounce);
  }, [form.companyName, form.status, form.source, form.salaryRange, form.followUpDate, form.deadlineDate]);

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
  ) => {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value || undefined }));
    if (errors[name]) {
      setErrors((prev) => {
        const next = { ...prev };
        delete next[name];
        return next;
      });
    }
  };

  const validate = (): boolean => {
    const newErrors: Record<string, string> = {};
    if (!form.companyName.trim()) newErrors.companyName = 'Company name is required';
    if (!form.jobTitle.trim()) newErrors.jobTitle = 'Job title is required';
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setLoading(true);
    try {
      await onSubmit(form);
    } catch {
      // Error handled by caller
    } finally {
      setLoading(false);
    }
  };

  const inputClass =
    'w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500';
  const labelClass = 'block text-sm font-medium text-slate-700 mb-1.5';
  const errorClass = 'mt-1 text-xs text-red-500';

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {/* Row 1: Company & Job Title */}
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
        <div>
          <label htmlFor="companyName" className={labelClass}>
            Company Name <span className="text-red-500">*</span>
          </label>
          <input
            id="companyName"
            name="companyName"
            value={form.companyName}
            onChange={handleChange}
            placeholder="e.g. Google"
            className={`${inputClass} ${errors.companyName ? 'border-red-400' : ''}`}
          />
          {errors.companyName && <p className={errorClass}>{errors.companyName}</p>}
        </div>
        <div>
          <label htmlFor="jobTitle" className={labelClass}>
            Job Title <span className="text-red-500">*</span>
          </label>
          <input
            id="jobTitle"
            name="jobTitle"
            value={form.jobTitle}
            onChange={handleChange}
            placeholder="e.g. Software Engineer"
            className={`${inputClass} ${errors.jobTitle ? 'border-red-400' : ''}`}
          />
          {errors.jobTitle && <p className={errorClass}>{errors.jobTitle}</p>}
        </div>
      </div>

      {/* Row 2: Location & Source */}
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
        <div>
          <label htmlFor="location" className={labelClass}>Location</label>
          <input
            id="location"
            name="location"
            value={form.location || ''}
            onChange={handleChange}
            placeholder="e.g. Remote, New York, NY"
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="source" className={labelClass}>Source</label>
          <input
            id="source"
            name="source"
            value={form.source || ''}
            onChange={handleChange}
            placeholder="e.g. LinkedIn, Indeed, Referral"
            className={inputClass}
          />
        </div>
      </div>

      {/* Row 3: Job URL & Company Career URL */}
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
        <div>
          <label htmlFor="jobUrl" className={labelClass}>Job URL</label>
          <input
            id="jobUrl"
            name="jobUrl"
            value={form.jobUrl || ''}
            onChange={handleChange}
            placeholder="https://..."
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="companyCareerUrl" className={labelClass}>Company Career URL</label>
          <input
            id="companyCareerUrl"
            name="companyCareerUrl"
            value={form.companyCareerUrl || ''}
            onChange={handleChange}
            placeholder="https://..."
            className={inputClass}
          />
        </div>
      </div>

      {/* Row 4: Status, Stage, Priority */}
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
        <div>
          <label htmlFor="status" className={labelClass}>
            Status <span className="text-red-500">*</span>
          </label>
          <select
            id="status"
            name="status"
            value={form.status}
            onChange={handleChange}
            className={inputClass}
          >
            {STATUS_OPTIONS.map((s) => (
              <option key={s} value={s}>{STATUS_LABELS[s]}</option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="stage" className={labelClass}>Stage</label>
          <input
            id="stage"
            name="stage"
            value={form.stage || ''}
            onChange={handleChange}
            placeholder="e.g. Phone Screen, Onsite"
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="priority" className={labelClass}>Priority</label>
          <select
            id="priority"
            name="priority"
            value={form.priority || ''}
            onChange={handleChange}
            className={inputClass}
          >
            <option value="">Select priority</option>
            {PRIORITY_OPTIONS.map((p) => (
              <option key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</option>
            ))}
          </select>
          {suggestion && (
            <div className="mt-1.5 flex flex-col gap-1 text-xs text-slate-500">
              <span>
                Suggested priority:{' '}
                <span className="font-semibold text-brand-700">
                  {suggestion.priority.charAt(0) + suggestion.priority.slice(1).toLowerCase()}
                </span>
                <span className="italic block mt-0.5">({suggestion.explanation})</span>
              </span>
              {form.priority !== suggestion.priority && (
                <button
                  type="button"
                  onClick={() => setForm((prev) => ({ ...prev, priority: suggestion.priority }))}
                  className="w-fit text-brand-600 font-medium hover:underline hover:text-brand-700"
                >
                  Accept suggestion
                </button>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Row 5: Dates */}
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
        <div>
          <label htmlFor="dateApplied" className={labelClass}>Date Applied</label>
          <input
            id="dateApplied"
            name="dateApplied"
            type="date"
            value={form.dateApplied || ''}
            onChange={handleChange}
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="followUpDate" className={labelClass}>Follow-up Date</label>
          <input
            id="followUpDate"
            name="followUpDate"
            type="date"
            value={form.followUpDate || ''}
            onChange={handleChange}
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="deadlineDate" className={labelClass}>Deadline Date</label>
          <input
            id="deadlineDate"
            name="deadlineDate"
            type="date"
            value={form.deadlineDate || ''}
            onChange={handleChange}
            className={inputClass}
          />
        </div>
      </div>

      {/* Row 6: Salary & Recruiter */}
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
        <div>
          <label htmlFor="salaryRange" className={labelClass}>Salary Range</label>
          <input
            id="salaryRange"
            name="salaryRange"
            value={form.salaryRange || ''}
            onChange={handleChange}
            placeholder="e.g. $120k - $150k"
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="recruiterEmail" className={labelClass}>Recruiter Email</label>
          <input
            id="recruiterEmail"
            name="recruiterEmail"
            type="email"
            value={form.recruiterEmail || ''}
            onChange={handleChange}
            placeholder="recruiter@company.com"
            className={inputClass}
          />
        </div>
      </div>

      {/* Job Information */}
      <div className="space-y-4 border-t border-slate-100 pt-6">
        <h3 className="text-base font-semibold text-slate-800">Job Information</h3>
        <div className="grid grid-cols-1 gap-6">
          <div>
            <label htmlFor="originalJobUrl" className={labelClass}>Job URL</label>
            <input
              id="originalJobUrl"
              name="originalJobUrl"
              value={form.originalJobUrl || ''}
              onChange={handleChange}
              placeholder="https://..."
              className={inputClass}
            />
          </div>
          <div>
            <label htmlFor="jobDescription" className={labelClass}>Job Description</label>
            <textarea
              id="jobDescription"
              name="jobDescription"
              value={form.jobDescription || ''}
              onChange={handleChange}
              rows={8}
              placeholder="Paste the job description here..."
              className={`${inputClass} resize-y`}
            />
          </div>
        </div>
      </div>

      {/* Notes */}
      <div>
        <label htmlFor="notes" className={labelClass}>Notes</label>
        <textarea
          id="notes"
          name="notes"
          value={form.notes || ''}
          onChange={handleChange}
          rows={4}
          placeholder="Any notes about this application..."
          className={`${inputClass} resize-y`}
        />
      </div>

      {/* Optional Child Sections (e.g. ApplicationDocumentsSection) */}
      {children}

      {/* Submit */}
      <div className="flex items-center justify-end gap-3 pt-2">
        {isDemo && (
          <span className="text-xs text-amber-600 font-semibold bg-amber-50 px-3 py-1.5 rounded-lg border border-amber-200">
            Form submission is disabled in demo mode
          </span>
        )}
        <button
          id="submit-application-btn"
          type="submit"
          disabled={(isSubmitting !== undefined ? isSubmitting : loading) || isDemo || submitDisabled}
          className="rounded-lg bg-brand-600 px-6 py-2.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50 inline-flex items-center gap-2"
        >
          {(isSubmitting !== undefined ? isSubmitting : loading) && (
            <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
          )}
          {submitButtonText || ((isSubmitting !== undefined ? isSubmitting : loading) ? 'Saving...' : submitLabel)}
        </button>
      </div>
    </form>
  );
}
