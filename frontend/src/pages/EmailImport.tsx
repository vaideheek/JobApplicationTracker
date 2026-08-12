import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Mail,
  Sparkles,
  AlertTriangle,
  CheckCircle2,
  ChevronRight,
  FileText,
  Building2,
  Calendar,
  Briefcase,
  Check
} from 'lucide-react';
import { jobApplicationApi } from '../api/jobApplicationApi';
import { useAuth } from '../context/AuthContext';
import { STATUS_OPTIONS, STATUS_LABELS, PRIORITY_OPTIONS } from '../types';
import type { EmailParseResponse, ApplicationStatus, JobApplication, ApplicationPriority } from '../types';

export default function EmailImport() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const isDemo = user?.demoAccount || false;
  const [step, setStep] = useState<'input' | 'preview' | 'success'>('input');
  
  // Input Step States
  const [emailText, setEmailText] = useState('');
  const [isParsing, setIsParsing] = useState(false);
  const [parseError, setParseError] = useState('');

  // Preview Step States
  const [parsedData, setParsedData] = useState<EmailParseResponse | null>(null);
  const [formData, setFormData] = useState<EmailParseResponse>({
    companyName: '',
    jobTitle: '',
    status: 'APPLIED',
    stage: 'Applied',
    recruiterEmail: '',
    source: 'Email',
    importantDate: '',
    suggestedNotes: '',
    confidenceScore: 'LOW',
    needsReview: true,
    priority: undefined,
    suggestedPriority: undefined,
    priorityExplanation: '',
  });
  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState('');

  // Success Step States
  const [importedApp, setImportedApp] = useState<JobApplication | null>(null);

  // Handle Parse Email Text
  const handleParse = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!emailText.trim()) return;

    setIsParsing(true);
    setParseError('');

    try {
      const result = await jobApplicationApi.parseEmail({ rawEmailText: emailText });
      setParsedData(result);
      setFormData({
        ...result,
        // Ensure null fields are empty strings for input bindings
        companyName: result.companyName || '',
        jobTitle: result.jobTitle || '',
        stage: result.stage || '',
        recruiterEmail: result.recruiterEmail || '',
        source: result.source || 'Email',
        importantDate: result.importantDate || '',
        suggestedNotes: result.suggestedNotes || '',
        priority: result.priority || undefined,
        suggestedPriority: result.suggestedPriority || undefined,
        priorityExplanation: result.priorityExplanation || '',
      });
      setStep('preview');
    } catch (err: any) {
      setParseError(err.response?.data?.message || 'Failed to parse email. Please try again.');
    } finally {
      setIsParsing(false);
    }
  };

  // Handle Confirm Import
  const handleConfirm = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.companyName.trim() || !formData.jobTitle.trim()) {
      setSaveError('Company Name and Job Title are required.');
      return;
    }

    setIsSaving(true);
    setSaveError('');

    try {
      // Create request payload (convert empty dates to null)
      const payload: EmailParseResponse = {
        ...formData,
        importantDate: formData.importantDate ? formData.importantDate : null,
      };

      const result = await jobApplicationApi.confirmEmailImport(payload);
      setImportedApp(result);
      setStep('success');
    } catch (err: any) {
      setSaveError(err.response?.data?.message || 'Failed to import application. Please try again.');
    } finally {
      setIsSaving(false);
    }
  };

  const getConfidenceBadge = (score: 'HIGH' | 'MEDIUM' | 'LOW') => {
    switch (score) {
      case 'HIGH':
        return (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700 ring-1 ring-inset ring-emerald-600/10">
            <Sparkles className="h-3.5 w-3.5" /> High Confidence
          </span>
        );
      case 'MEDIUM':
        return (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-700 ring-1 ring-inset ring-amber-600/10">
            Medium Confidence
          </span>
        );
      case 'LOW':
      default:
        return (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-rose-50 px-2.5 py-1 text-xs font-semibold text-rose-700 ring-1 ring-inset ring-rose-600/10">
            <AlertTriangle className="h-3.5 w-3.5" /> Low Confidence
          </span>
        );
    }
  };

  return (
    <div className="mx-auto max-w-4xl">
      {/* Page Header */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <button
            onClick={() => {
              if (step === 'preview') setStep('input');
              else navigate(-1);
            }}
            className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700"
          >
            <ArrowLeft size={16} /> Back
          </button>
          <h1 className="text-2xl font-bold text-slate-900">Email Import</h1>
          <p className="mt-1 text-sm text-slate-500">
            Paste a job-related email to extract and import application details.
          </p>
        </div>

        {/* Steps Visualizer */}
        <div className="hidden items-center gap-2 text-sm font-medium text-slate-400 sm:flex">
          <span className={step === 'input' ? 'text-brand-600' : 'text-slate-600'}>Paste Email</span>
          <ChevronRight size={16} />
          <span className={step === 'preview' ? 'text-brand-600' : 'text-slate-600'}>Review & Edit</span>
          <ChevronRight size={16} />
          <span className={step === 'success' ? 'text-brand-600' : 'text-slate-600'}>Success</span>
        </div>
      </div>

      {isDemo && (
        <div className="mb-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 shadow-sm flex items-center gap-3">
          <AlertTriangle size={20} className="text-amber-600 flex-shrink-0" />
          <span>You are logged in as a <strong>Demo User</strong>. You can parse emails to preview extracted data, but confirming imports is disabled.</span>
        </div>
      )}

      {/* Step 1: Input Textarea */}
      {step === 'input' && (
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <form onSubmit={handleParse} className="space-y-4">
            <div>
              <label htmlFor="emailText" className="block text-sm font-semibold text-slate-700 mb-2">
                Paste Email Raw Text
              </label>
              <p className="text-xs text-slate-400 mb-3">
                Paste the full header and body of any application confirmation, interview invitation, assessment, rejection, or offer email.
              </p>
              <textarea
                id="emailText"
                rows={12}
                className="w-full rounded-lg border border-slate-200 p-4 text-sm font-sans focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                placeholder="Example:&#10;Subject: Interview request - Software Engineer role at Google&#10;From: recruiter@google.com&#10;&#10;Hi Candidate, we would like to schedule an interview..."
                value={emailText}
                onChange={(e) => setEmailText(e.target.value)}
                disabled={isParsing}
                required
              />
            </div>

            {parseError && (
              <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
                {parseError}
              </div>
            )}

            <div className="flex justify-end">
              <button
                type="submit"
                className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 disabled:bg-slate-300"
                disabled={isParsing || !emailText.trim()}
              >
                {isParsing ? (
                  <>
                    <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                    Extracting details...
                  </>
                ) : (
                  <>
                    <Sparkles size={16} /> Parse Email
                  </>
                )}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Step 2: Preview & Edit */}
      {step === 'preview' && (
        <div className="space-y-6">
          {/* Review Alert */}
          {formData.needsReview && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-800 shadow-sm">
              <div className="flex gap-3">
                <AlertTriangle className="h-5 w-5 text-amber-500 shrink-0 mt-0.5" />
                <div>
                  <h3 className="font-semibold text-amber-900">Please review extracted details before importing</h3>
                  <p className="mt-1 text-sm">
                    We could not extract some critical fields confidently. Please check and correct any blank or inaccurate fields.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* Form and Preview Cards */}
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            {/* Form Column */}
            <div className="lg:col-span-2 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="mb-4 flex items-center justify-between border-b border-slate-100 pb-4">
                <h2 className="text-lg font-bold text-slate-900">Extracted Application Info</h2>
                {getConfidenceBadge(formData.confidenceScore)}
              </div>

              <form onSubmit={handleConfirm} className="space-y-4">
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                      Company Name *
                    </label>
                    <input
                      type="text"
                      className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                      value={formData.companyName}
                      onChange={(e) => setFormData({ ...formData, companyName: e.target.value })}
                      required
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                      Job Title *
                    </label>
                    <input
                      type="text"
                      className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                      value={formData.jobTitle}
                      onChange={(e) => setFormData({ ...formData, jobTitle: e.target.value })}
                      required
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                      Status
                    </label>
                    <select
                      className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                      value={formData.status}
                      onChange={(e) => setFormData({ ...formData, status: e.target.value as ApplicationStatus })}
                    >
                      {STATUS_OPTIONS.map((status) => (
                        <option key={status} value={status}>
                          {STATUS_LABELS[status]}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                      Stage (e.g. Phone, Onsite)
                    </label>
                    <input
                      type="text"
                      className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                      value={formData.stage || ''}
                      onChange={(e) => setFormData({ ...formData, stage: e.target.value })}
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                      Recruiter Email
                    </label>
                    <input
                      type="email"
                      className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                      value={formData.recruiterEmail || ''}
                      onChange={(e) => setFormData({ ...formData, recruiterEmail: e.target.value })}
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                      Source
                    </label>
                    <input
                      type="text"
                      className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                      value={formData.source || ''}
                      onChange={(e) => setFormData({ ...formData, source: e.target.value })}
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                      Important Date (applied on/interview date)
                    </label>
                    <input
                      type="date"
                      className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                      value={formData.importantDate || ''}
                      onChange={(e) => setFormData({ ...formData, importantDate: e.target.value })}
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                      Priority Suggestion
                    </label>
                    <select
                      className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                      value={formData.priority || ''}
                      onChange={(e) => setFormData({ ...formData, priority: e.target.value as ApplicationPriority })}
                    >
                      <option value="">Select priority</option>
                      {PRIORITY_OPTIONS.map((p) => (
                        <option key={p} value={p}>
                          {p.charAt(0) + p.slice(1).toLowerCase()}
                        </option>
                      ))}
                    </select>
                    {formData.priorityExplanation && (
                      <p className="mt-1 text-xs text-slate-500 italic">
                        Suggested {formData.suggestedPriority ? formData.suggestedPriority.charAt(0) + formData.suggestedPriority.slice(1).toLowerCase() : ''} because: {formData.priorityExplanation}
                      </p>
                    )}
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                    Suggested Notes
                  </label>
                  <textarea
                    rows={4}
                    className="w-full rounded-lg border border-slate-200 p-3 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                    value={formData.suggestedNotes || ''}
                    onChange={(e) => setFormData({ ...formData, suggestedNotes: e.target.value })}
                  />
                </div>

                {saveError && (
                  <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
                    {saveError}
                  </div>
                )}

                <div className="flex justify-end gap-3 border-t border-slate-100 pt-4">
                  <button
                    type="button"
                    onClick={() => setStep('input')}
                    className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2"
                  >
                    Back to Edit
                  </button>
                  <button
                    type="submit"
                    className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 disabled:bg-slate-300"
                    disabled={isSaving || isDemo}
                  >
                    {isSaving ? (
                      <>
                        <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                        Saving...
                      </>
                    ) : (
                      <>
                        <Check size={16} /> Confirm Import
                      </>
                    )}
                  </button>
                </div>
              </form>
            </div>

            {/* Original Text Reference Column */}
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-6 shadow-sm flex flex-col h-[500px]">
              <div className="mb-3 flex items-center gap-2 text-slate-700 font-bold border-b border-slate-200 pb-3">
                <FileText size={18} />
                <span>Original Email Text</span>
              </div>
              <div className="flex-1 overflow-y-auto text-xs text-slate-600 font-mono bg-white p-3 rounded-lg border border-slate-200 whitespace-pre-wrap">
                {emailText}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Step 3: Success Screen */}
      {step === 'success' && importedApp && (
        <div className="rounded-xl border border-slate-200 bg-white p-8 text-center shadow-sm max-w-lg mx-auto">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
            <CheckCircle2 size={28} />
          </div>
          
          <h2 className="mt-4 text-xl font-bold text-slate-900">Application Imported Successfully!</h2>
          <p className="mt-2 text-sm text-slate-500">
            The details from your email have been successfully added to your dashboard.
          </p>

          {/* Imported Summary Card */}
          <div className="my-6 rounded-lg border border-slate-100 bg-slate-50 p-4 text-left space-y-3">
            <div className="flex items-center gap-3">
              <Building2 className="text-slate-400 h-5 w-5" />
              <div>
                <div className="text-xs text-slate-400 font-medium">Company</div>
                <div className="text-sm font-semibold text-slate-800">{importedApp.companyName}</div>
              </div>
            </div>
            
            <div className="flex items-center gap-3">
              <Briefcase className="text-slate-400 h-5 w-5" />
              <div>
                <div className="text-xs text-slate-400 font-medium">Job Title</div>
                <div className="text-sm font-semibold text-slate-800">{importedApp.jobTitle}</div>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <Calendar className="text-slate-400 h-5 w-5" />
              <div>
                <div className="text-xs text-slate-400 font-medium">Current Status</div>
                <div className="text-sm font-semibold text-slate-800">
                  {STATUS_LABELS[importedApp.status]} {importedApp.stage ? `(${importedApp.stage})` : ''}
                </div>
              </div>
            </div>
          </div>

          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <button
              onClick={() => {
                setEmailText('');
                setStep('input');
              }}
              className="rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2"
            >
              Import Another Email
            </button>
            <button
              onClick={() => navigate(`/applications/${importedApp.id}`)}
              className="rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2"
            >
              View Application Details
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
