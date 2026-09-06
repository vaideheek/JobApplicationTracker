import React, { useState, useRef } from 'react';
import { Upload, FileText, Trash2, AlertCircle, Plus, X } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export const MAX_DOCUMENT_SIZE = 10 * 1024 * 1024; // 10 MB
export const ALLOWED_EXTENSIONS = ['.pdf', '.docx'];

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function validateDocumentFile(file: File): string | null {
  const lowercase = file.name.toLowerCase();
  const validExt = ALLOWED_EXTENSIONS.some((ext) => lowercase.endsWith(ext));
  if (!validExt) {
    return `"${file.name}" has an unsupported file format. Only PDF and DOCX files are allowed.`;
  }
  if (file.size < 4) {
    return `"${file.name}" is empty or too small to be a valid PDF or DOCX.`;
  }
  if (file.size > MAX_DOCUMENT_SIZE) {
    return `"${file.name}" exceeds the maximum limit of 10 MB (${formatFileSize(file.size)}).`;
  }
  return null;
}

export interface ApplicationDocumentsSectionProps {
  cvFile: File | null;
  setCvFile: (file: File | null) => void;
  coverLetterFile: File | null;
  setCoverLetterFile: (file: File | null) => void;
  otherFiles: File[];
  setOtherFiles: React.Dispatch<React.SetStateAction<File[]>>;
  disabled?: boolean;
}

export default function ApplicationDocumentsSection({
  cvFile,
  setCvFile,
  coverLetterFile,
  setCoverLetterFile,
  otherFiles,
  setOtherFiles,
  disabled = false,
}: ApplicationDocumentsSectionProps) {
  const { user } = useAuth();
  const isDemo = user?.demoAccount || false;
  const isDisabled = disabled || isDemo;

  const [validationError, setValidationError] = useState<string | null>(null);

  const cvInputRef = useRef<HTMLInputElement>(null);
  const coverLetterInputRef = useRef<HTMLInputElement>(null);
  const otherInputRef = useRef<HTMLInputElement>(null);

  const handleCvChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;

    const error = validateDocumentFile(file);
    if (error) {
      setValidationError(error);
      return;
    }
    setValidationError(null);
    setCvFile(file);
  };

  const handleCoverLetterChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;

    const error = validateDocumentFile(file);
    if (error) {
      setValidationError(error);
      return;
    }
    setValidationError(null);
    setCoverLetterFile(file);
  };

  const handleOtherChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    e.target.value = '';
    if (files.length === 0) return;

    const errors: string[] = [];
    const validFiles: File[] = [];

    files.forEach((file) => {
      const err = validateDocumentFile(file);
      if (err) {
        errors.push(err);
      } else {
        validFiles.push(file);
      }
    });

    if (errors.length > 0) {
      setValidationError(errors.join(' '));
    } else {
      setValidationError(null);
    }

    if (validFiles.length > 0) {
      setOtherFiles((prev) => {
        const existingKeys = new Set(
          prev.map((f) => `${f.name}-${f.size}-${f.lastModified}`)
        );
        const filteredNew = validFiles.filter(
          (f) => !existingKeys.has(`${f.name}-${f.size}-${f.lastModified}`)
        );
        return [...prev, ...filteredNew];
      });
    }
  };

  const removeOtherFile = (indexToRemove: number) => {
    setOtherFiles((prev) => prev.filter((_, idx) => idx !== indexToRemove));
  };

  return (
    <div className="space-y-4 border-t border-slate-100 pt-6">
      {/* Section Heading & Helper */}
      <div>
        <div className="flex items-center justify-between">
          <h3 className="text-base font-semibold text-slate-800">Application Documents</h3>
          <span className="text-xs font-medium text-slate-400">
            Optional · PDF or DOCX · Max 10 MB per file
          </span>
        </div>
        <p className="mt-1 text-xs text-slate-500">
          Attach your CV, cover letter, or additional supporting documents now. They will be uploaded automatically once the application is created.
        </p>
      </div>

      {/* Demo Account Warning */}
      {isDemo && (
        <div className="rounded-lg bg-amber-50 p-3 text-xs text-amber-800 border border-amber-200 flex items-center gap-2">
          <AlertCircle size={16} className="text-amber-600 shrink-0" />
          <span>Document uploads are disabled in demo mode.</span>
        </div>
      )}

      {/* Client Validation Error Banner */}
      {validationError && (
        <div className="rounded-lg bg-rose-50 p-3 text-xs text-rose-800 border border-rose-200 flex items-start justify-between gap-2">
          <div className="flex items-start gap-2">
            <AlertCircle size={16} className="text-rose-600 shrink-0 mt-0.5" />
            <span>{validationError}</span>
          </div>
          <button
            type="button"
            onClick={() => setValidationError(null)}
            className="text-rose-500 hover:text-rose-700"
            aria-label="Dismiss error"
          >
            <X size={15} />
          </button>
        </div>
      )}

      {/* 3 Upload Slots Grid */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
        {/* 1. CV / Resume */}
        <div className="rounded-xl border border-slate-200 bg-white p-4 flex flex-col justify-between space-y-3">
          <div className="flex items-center justify-between border-b border-slate-100 pb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-600">
              CV / Resume
            </span>
            <span className="text-[11px] text-slate-400">Max 1 file</span>
          </div>

          {cvFile ? (
            <div className="flex items-center justify-between gap-3 rounded-lg bg-brand-50/60 p-3 border border-brand-100">
              <div className="flex items-center gap-2.5 overflow-hidden">
                <FileText className="h-5 w-5 text-brand-600 shrink-0" />
                <div className="flex flex-col overflow-hidden min-w-0">
                  <span className="text-xs font-semibold text-slate-900 truncate" title={cvFile.name}>
                    {cvFile.name}
                  </span>
                  <span className="text-[11px] text-slate-500">{formatFileSize(cvFile.size)}</span>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setCvFile(null)}
                disabled={isDisabled}
                className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-md transition-colors disabled:opacity-50 shrink-0"
                title="Remove CV"
                aria-label="Remove CV"
              >
                <Trash2 size={16} />
              </button>
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-slate-200 p-4 text-center hover:border-brand-400 transition-colors">
              <Upload className="h-5 w-5 text-slate-400 mb-1.5" />
              <span className="text-xs font-medium text-slate-600 mb-2">Attach CV or Resume</span>
              <button
                type="button"
                onClick={() => cvInputRef.current?.click()}
                disabled={isDisabled}
                aria-label="Choose CV file"
                className="rounded-lg bg-white border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 hover:border-slate-400 disabled:opacity-50 transition-colors"
              >
                Choose File
              </button>
            </div>
          )}

          {cvFile && !isDisabled && (
            <button
              type="button"
              onClick={() => cvInputRef.current?.click()}
              aria-label="Replace CV file"
              className="text-[11px] text-brand-600 hover:text-brand-700 font-medium text-center self-center"
            >
              Replace with another file
            </button>
          )}

          <input
            ref={cvInputRef}
            type="file"
            accept=".pdf,.docx"
            onChange={handleCvChange}
            disabled={isDisabled}
            className="hidden"
          />
        </div>

        {/* 2. Cover Letter */}
        <div className="rounded-xl border border-slate-200 bg-white p-4 flex flex-col justify-between space-y-3">
          <div className="flex items-center justify-between border-b border-slate-100 pb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-600">
              Cover Letter
            </span>
            <span className="text-[11px] text-slate-400">Max 1 file</span>
          </div>

          {coverLetterFile ? (
            <div className="flex items-center justify-between gap-3 rounded-lg bg-purple-50/60 p-3 border border-purple-100">
              <div className="flex items-center gap-2.5 overflow-hidden">
                <FileText className="h-5 w-5 text-purple-600 shrink-0" />
                <div className="flex flex-col overflow-hidden min-w-0">
                  <span className="text-xs font-semibold text-slate-900 truncate" title={coverLetterFile.name}>
                    {coverLetterFile.name}
                  </span>
                  <span className="text-[11px] text-slate-500">{formatFileSize(coverLetterFile.size)}</span>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setCoverLetterFile(null)}
                disabled={isDisabled}
                className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-md transition-colors disabled:opacity-50 shrink-0"
                title="Remove Cover Letter"
                aria-label="Remove Cover Letter"
              >
                <Trash2 size={16} />
              </button>
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-slate-200 p-4 text-center hover:border-brand-400 transition-colors">
              <Upload className="h-5 w-5 text-slate-400 mb-1.5" />
              <span className="text-xs font-medium text-slate-600 mb-2">Attach Cover Letter</span>
              <button
                type="button"
                onClick={() => coverLetterInputRef.current?.click()}
                disabled={isDisabled}
                aria-label="Choose cover letter file"
                className="rounded-lg bg-white border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 hover:border-slate-400 disabled:opacity-50 transition-colors"
              >
                Choose File
              </button>
            </div>
          )}

          {coverLetterFile && !isDisabled && (
            <button
              type="button"
              onClick={() => coverLetterInputRef.current?.click()}
              aria-label="Replace cover letter file"
              className="text-[11px] text-purple-600 hover:text-purple-700 font-medium text-center self-center"
            >
              Replace with another file
            </button>
          )}

          <input
            ref={coverLetterInputRef}
            type="file"
            accept=".pdf,.docx"
            onChange={handleCoverLetterChange}
            disabled={isDisabled}
            className="hidden"
          />
        </div>

        {/* 3. Other Documents */}
        <div className="rounded-xl border border-slate-200 bg-white p-4 flex flex-col justify-between space-y-3 md:col-span-2 lg:col-span-1">
          <div className="flex items-center justify-between border-b border-slate-100 pb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-600">
              Other Documents
            </span>
            <span className="text-[11px] text-slate-400">{otherFiles.length} selected</span>
          </div>

          {otherFiles.length > 0 ? (
            <div className="space-y-2 max-h-[140px] overflow-y-auto pr-1">
              {otherFiles.map((file, idx) => (
                <div
                  key={`${file.name}-${file.size}-${file.lastModified}-${idx}`}
                  className="flex items-center justify-between gap-2 rounded-lg bg-slate-50 p-2 border border-slate-100"
                >
                  <div className="flex items-center gap-2 overflow-hidden min-w-0">
                    <FileText className="h-4 w-4 text-indigo-500 shrink-0" />
                    <span className="text-xs font-medium text-slate-800 truncate" title={file.name}>
                      {file.name}
                    </span>
                    <span className="text-[10px] text-slate-400 shrink-0">
                      ({formatFileSize(file.size)})
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() => removeOtherFile(idx)}
                    disabled={isDisabled}
                    className="p-1 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded transition-colors disabled:opacity-50 shrink-0"
                    title={`Remove ${file.name}`}
                    aria-label={`Remove ${file.name}`}
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              ))}
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-slate-200 p-4 text-center hover:border-brand-400 transition-colors">
              <Upload className="h-5 w-5 text-slate-400 mb-1.5" />
              <span className="text-xs font-medium text-slate-600 mb-2">Transcripts, Certifications, Portfolios</span>
              <button
                type="button"
                onClick={() => otherInputRef.current?.click()}
                disabled={isDisabled}
                aria-label="Choose supporting document files"
                className="rounded-lg bg-white border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 hover:border-slate-400 disabled:opacity-50 transition-colors"
              >
                Choose File(s)
              </button>
            </div>
          )}

          {otherFiles.length > 0 && (
            <button
              type="button"
              onClick={() => otherInputRef.current?.click()}
              disabled={isDisabled}
              aria-label="Add more supporting document files"
              className="inline-flex items-center justify-center gap-1 rounded-lg border border-dashed border-slate-300 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50 transition-colors"
            >
              <Plus size={13} /> Add more files
            </button>
          )}

          <input
            ref={otherInputRef}
            type="file"
            accept=".pdf,.docx"
            multiple
            onChange={handleOtherChange}
            disabled={isDisabled}
            className="hidden"
          />
        </div>
      </div>
    </div>
  );
}
