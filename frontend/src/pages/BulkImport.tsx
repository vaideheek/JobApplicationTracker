import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { UploadCloud, Folder, FileText, CheckCircle, AlertTriangle, XCircle, ArrowRight, Loader2, HelpCircle, Search, Filter, Calendar, Paperclip, Info, X } from 'lucide-react';
import { jobApplicationApi } from '../api/jobApplicationApi';
import { STATUS_OPTIONS, PRIORITY_OPTIONS, STATUS_LABELS } from '../types';
import type { BulkScanResponse, ScannedApplicationGroup, ScannedDocumentPreview, BulkConfirmRequest, BulkConfirmApplication } from '../types';

export default function BulkImport() {
  const navigate = useNavigate();
  const [manifestFile, setManifestFile] = useState<File | null>(null);
  const [laptopZipFile, setLaptopZipFile] = useState<File | null>(null);
  const [googleDriveZipFile, setGoogleDriveZipFile] = useState<File | null>(null);

  const [isLoading, setIsLoading] = useState(false);
  const [scanResult, setScanResult] = useState<BulkScanResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Preview state
  const [selectedApps, setSelectedApps] = useState<Record<string, boolean>>({});
  const [appDetails, setAppDetails] = useState<Record<string, Partial<ScannedApplicationGroup>>>({});

  // Unassigned files association state
  const [unassignedAssociations, setUnassignedAssociations] = useState<Record<string, { targetAppId: string; docType: string }>>({});
  const [selectedUnassigned, setSelectedUnassigned] = useState<Record<string, boolean>>({});

  const [importResult, setImportResult] = useState<{
    successCount: number;
    failureCount: number;
    documentFailures: Array<{ fileName: string; error: string }>;
  } | null>(null);

  // Filters & Search
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [confidenceFilter, setConfidenceFilter] = useState('');
  const [actionFilter, setActionFilter] = useState('');
  const [missingDateOnly, setMissingDateOnly] = useState(false);
  const [hasDocsOnly, setHasDocsOnly] = useState(false);
  const [verificationFilter, setVerificationFilter] = useState('');
  const [unresolvedTitlesOnly, setUnresolvedTitlesOnly] = useState(false);

  // Modal Dialog
  const [showConfirmModal, setShowConfirmModal] = useState(false);

  const handleScan = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!manifestFile || !laptopZipFile || !googleDriveZipFile) {
      setErrorMessage('Please select the JSON manifest and both ZIP files.');
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);
    setScanResult(null);
    setImportResult(null);

    try {
      const result = await jobApplicationApi.scanZipFiles(
        manifestFile,
        laptopZipFile,
        googleDriveZipFile
      );
      setScanResult(result);

      // Auto-select all by default
      const defaultSelections: Record<string, boolean> = {};
      const details: Record<string, Partial<ScannedApplicationGroup>> = {};
      result.applications.forEach((app) => {
        defaultSelections[app.tempAppId] = true;
        details[app.tempAppId] = {
          companyName: app.companyName,
          jobTitle: app.jobTitle,
          status: app.status,
          priority: app.priority,
          dateApplied: app.dateApplied,
        };
      });

      setSelectedApps(defaultSelections);
      setAppDetails(details);
    } catch (err: any) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to scan files.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleConfirmImport = async () => {
    if (!scanResult) return;

    setIsLoading(true);
    setErrorMessage(null);

    // Build the payload
    const applicationsPayload: BulkConfirmApplication[] = [];

    // Map selected applications
    scanResult.applications.forEach((app) => {
      if (selectedApps[app.tempAppId]) {
        const edits = appDetails[app.tempAppId] || {};

        // Find documents that belong to this application
        const docs = app.documents.map((doc) => ({
          tempDocId: doc.tempDocId,
          documentType: doc.documentType,
        }));

        applicationsPayload.push({
          tempAppId: app.tempAppId,
          companyName: edits.companyName || app.companyName,
          jobTitle: edits.jobTitle || app.jobTitle,
          status: edits.status || app.status,
          priority: edits.priority || app.priority,
          dateApplied: edits.dateApplied || app.dateApplied,
          stage: app.stage,
          source: app.source,
          notes: app.notes,
          documents: docs,
        });
      }
    });

    // Map selected unassigned files to their chosen applications
    scanResult.unassignedFiles.forEach((doc) => {
      if (selectedUnassigned[doc.tempDocId]) {
        const assoc = unassignedAssociations[doc.tempDocId];
        if (assoc && assoc.targetAppId && assoc.docType) {
          // Find if targetAppId already exists in payload
          let targetApp = applicationsPayload.find(a => a.tempAppId === assoc.targetAppId);
          if (!targetApp) {
            // Find corresponding scanned details from scanResult applications
            const scannedApp = scanResult.applications.find(a => a.tempAppId === assoc.targetAppId);
            if (scannedApp) {
              const edits = appDetails[assoc.targetAppId] || {};
              targetApp = {
                tempAppId: scannedApp.tempAppId,
                companyName: edits.companyName || scannedApp.companyName,
                jobTitle: edits.jobTitle || scannedApp.jobTitle,
                status: edits.status || scannedApp.status,
                priority: edits.priority || scannedApp.priority,
                dateApplied: edits.dateApplied || scannedApp.dateApplied,
                stage: scannedApp.stage,
                source: scannedApp.source,
                notes: scannedApp.notes,
                documents: [],
              };
              applicationsPayload.push(targetApp);
            }
          }

          if (targetApp) {
            targetApp.documents.push({
              tempDocId: doc.tempDocId,
              documentType: assoc.docType,
            });
          }
        }
      }
    });

    const confirmRequest: BulkConfirmRequest = {
      scanId: scanResult.scanId,
      applications: applicationsPayload,
    };

    try {
      const response = await jobApplicationApi.confirmBulkImport(confirmRequest);
      setImportResult(response);
      setScanResult(null); // Clear preview on successful response
    } catch (err: any) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to complete import.');
    } finally {
      setIsLoading(false);
    }
  };

  const updateAppField = (tempId: string, field: keyof ScannedApplicationGroup, value: string) => {
    setAppDetails((prev) => ({
      ...prev,
      [tempId]: {
        ...prev[tempId],
        [field]: value,
      },
    }));
  };

  const handleUnassignedAssoc = (tempDocId: string, targetAppId: string, docType: string) => {
    setUnassignedAssociations((prev) => ({
      ...prev,
      [tempDocId]: {
        targetAppId,
        docType,
      },
    }));
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      {/* Header */}
      <div className="mb-8 flex flex-col gap-2">
        <h1 className="text-2xl font-bold text-slate-900 md:text-3xl">Bulk Folder/ZIP Import</h1>
        <p className="text-sm text-slate-500">
          Upload your JSON manifest alongside your Laptop and Google Drive ZIP files to import multiple job applications and CVs.
        </p>
      </div>

      {errorMessage && (
        <div className="mb-6 flex items-start gap-3 rounded-lg bg-red-50 p-4 text-sm text-red-800 border border-red-200">
          <XCircle className="h-5 w-5 flex-shrink-0 text-red-600" />
          <div>{errorMessage}</div>
        </div>
      )}

      {/* Confirmation Modal Result */}
      {importResult && (
        <div className="mb-8 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-center gap-3 mb-4">
            <CheckCircle className="h-7 w-7 text-emerald-600" />
            <h2 className="text-xl font-bold text-slate-900">Import Batch Confirmed</h2>
          </div>
          <div className="grid grid-cols-2 gap-4 max-w-sm mb-6">
            <div className="rounded-lg bg-emerald-50 p-3 border border-emerald-100">
              <p className="text-xs text-emerald-700 font-semibold uppercase tracking-wider">Success Count</p>
              <p className="text-3xl font-extrabold text-emerald-800 mt-1">{importResult.successCount}</p>
            </div>
            <div className="rounded-lg bg-red-50 p-3 border border-red-100">
              <p className="text-xs text-red-700 font-semibold uppercase tracking-wider">Failure Count</p>
              <p className="text-3xl font-extrabold text-red-800 mt-1">{importResult.failureCount}</p>
            </div>
          </div>

          {importResult.documentFailures.length > 0 && (
            <div className="mt-4">
              <h3 className="text-sm font-semibold text-slate-800 mb-2">Individual Document Failures:</h3>
              <div className="max-h-40 overflow-y-auto border border-slate-200 rounded-lg divide-y divide-slate-100 text-xs">
                {importResult.documentFailures.map((fail, i) => (
                  <div key={i} className="p-3 flex justify-between gap-4">
                    <span className="font-medium text-slate-700">{fail.fileName}</span>
                    <span className="text-red-600">{fail.error}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="mt-6 flex gap-4">
            <button
              onClick={() => navigate('/applications')}
              className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-500 transition-colors"
            >
              Go to Applications List
              <ArrowRight size={16} />
            </button>
            <button
              onClick={() => setImportResult(null)}
              className="rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
            >
              Import Another Batch
            </button>
          </div>
        </div>
      )}

      {/* Upload files form */}
      {!scanResult && !importResult && (
        <form onSubmit={handleScan} className="space-y-6">
          <div className="grid gap-6 md:grid-cols-3">
            {/* Manifest Upload */}
            <div className="rounded-xl border border-dashed border-slate-300 bg-white p-6 text-center hover:bg-slate-50/50 transition-colors">
              <UploadCloud className="mx-auto h-10 w-10 text-slate-400 mb-3" />
              <h3 className="text-sm font-semibold text-slate-900 mb-1">Reconciled Manifest</h3>
              <p className="text-xs text-slate-400 mb-4">Upload the source JSON manifest</p>
              <input
                type="file"
                accept=".json"
                onChange={(e) => setManifestFile(e.target.files?.[0] || null)}
                className="hidden"
                id="manifest-input"
              />
              <label
                htmlFor="manifest-input"
                className="inline-flex cursor-pointer items-center justify-center rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
              >
                {manifestFile ? manifestFile.name : 'Select JSON File'}
              </label>
            </div>

            {/* Laptop Zip Upload */}
            <div className="rounded-xl border border-dashed border-slate-300 bg-white p-6 text-center hover:bg-slate-50/50 transition-colors">
              <Folder className="mx-auto h-10 w-10 text-slate-400 mb-3" />
              <h3 className="text-sm font-semibold text-slate-900 mb-1">Laptop ZIP Archive</h3>
              <p className="text-xs text-slate-400 mb-4">First ZIP file containing document files</p>
              <input
                type="file"
                accept=".zip"
                onChange={(e) => setLaptopZipFile(e.target.files?.[0] || null)}
                className="hidden"
                id="laptop-input"
              />
              <label
                htmlFor="laptop-input"
                className="inline-flex cursor-pointer items-center justify-center rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
              >
                {laptopZipFile ? laptopZipFile.name : 'Select ZIP File'}
              </label>
            </div>

            {/* Google Drive Zip Upload */}
            <div className="rounded-xl border border-dashed border-slate-300 bg-white p-6 text-center hover:bg-slate-50/50 transition-colors">
              <Folder className="mx-auto h-10 w-10 text-slate-400 mb-3" />
              <h3 className="text-sm font-semibold text-slate-900 mb-1">Google Drive ZIP</h3>
              <p className="text-xs text-slate-400 mb-4">Second ZIP file containing document files</p>
              <input
                type="file"
                accept=".zip"
                onChange={(e) => setGoogleDriveZipFile(e.target.files?.[0] || null)}
                className="hidden"
                id="drive-input"
              />
              <label
                htmlFor="drive-input"
                className="inline-flex cursor-pointer items-center justify-center rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
              >
                {googleDriveZipFile ? googleDriveZipFile.name : 'Select ZIP File'}
              </label>
            </div>
          </div>

          <div className="flex justify-end mt-4">
            <button
              type="submit"
              disabled={isLoading || !manifestFile || !laptopZipFile || !googleDriveZipFile}
              className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-6 py-3 text-sm font-semibold text-white shadow-sm hover:bg-brand-500 disabled:opacity-50 transition-colors"
            >
              {isLoading ? (
                <>
                  <Loader2 className="animate-spin" size={18} />
                  Scanning Archives...
                </>
              ) : (
                'Start Scanning'
              )}
            </button>
          </div>
        </form>
      )}

      {/* Preview Section */}
      {scanResult && !isLoading && (() => {
        const unresolvedIds = new Set([
          'APP-031', 'APP-079', 'APP-080', 'APP-081', 'APP-142', 'APP-204',
          'APP-207', 'APP-211', 'APP-217', 'APP-218', 'APP-226', 'APP-229'
        ]);

        const filteredApps = scanResult.applications.filter((app) => {
          const edits = appDetails[app.tempAppId] || {};
          const companyName = edits.companyName ?? app.companyName;
          const jobTitle = edits.jobTitle ?? app.jobTitle;
          const status = edits.status ?? app.status;
          const dateApplied = edits.dateApplied ?? app.dateApplied;

          if (
            searchQuery &&
            !companyName.toLowerCase().includes(searchQuery.toLowerCase()) &&
            !jobTitle.toLowerCase().includes(searchQuery.toLowerCase()) &&
            !(app.notes && app.notes.toLowerCase().includes(searchQuery.toLowerCase()))
          ) {
            return false;
          }

          if (statusFilter && status !== statusFilter) {
            return false;
          }

          if (confidenceFilter && app.evidenceConfidence !== confidenceFilter) {
            return false;
          }

          if (actionFilter && app.importAction !== actionFilter) {
            return false;
          }

          if (missingDateOnly && dateApplied && dateApplied.trim() !== '') {
            return false;
          }

          if (unresolvedTitlesOnly && !unresolvedIds.has(app.manifestApplicationId)) {
            return false;
          }

          if (hasDocsOnly && app.documents.length === 0) {
            return false;
          }

          if (verificationFilter) {
            const hasMatchingDoc = app.documents.some(
              (d) => d.validationStatus === verificationFilter
            );
            if (!hasMatchingDoc) return false;
          }

          return true;
        });

        // Compute summary metrics for selected applications
        const totalScanned = scanResult.applications.length;
        const totalCreate = scanResult.applications.filter((a) => a.importAction !== 'MATCH_EXISTING' && selectedApps[a.tempAppId]).length;
        const totalUpdate = scanResult.applications.filter((a) => a.importAction === 'MATCH_EXISTING' && selectedApps[a.tempAppId]).length;
        const totalExcluded = scanResult.applications.filter((a) => !selectedApps[a.tempAppId]).length;
        const totalUnassigned = scanResult.unassignedFiles.length;

        // Confidence counts
        const highConfidenceCount = scanResult.applications.filter((a) => a.evidenceConfidence === 'HIGH').length;
        const mediumConfidenceCount = scanResult.applications.filter((a) => a.evidenceConfidence === 'MEDIUM').length;
        const lowConfidenceCount = scanResult.applications.filter((a) => a.evidenceConfidence === 'LOW').length;

        // Missing date count
        const missingDateCount = scanResult.applications.filter((a) => !a.dateApplied || a.dateApplied.trim() === '').length;

        // Unresolved titles count
        const unresolvedTitlesCount = scanResult.applications.filter((a) => unresolvedIds.has(a.manifestApplicationId)).length;

        return (
          <div className="space-y-8">
            {scanResult.validationIssues && scanResult.validationIssues.length > 0 && (
              <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-red-800 space-y-2">
                <div className="flex items-center gap-2">
                  <AlertTriangle className="h-5 w-5 text-red-600" />
                  <h3 className="font-bold text-red-900">Validation Issues Found:</h3>
                </div>
                <ul className="list-disc pl-5 space-y-1 mt-2">
                  {scanResult.validationIssues.map((issue, index) => (
                    <li key={index}>{issue}</li>
                  ))}
                </ul>
                <p className="text-xs text-red-700 mt-2 font-medium">Please fix these issues to enable confirmation and import.</p>
              </div>
            )}

            {/* Metrics cards grid */}
            <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-5">
              <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
                <p className="text-xs text-slate-500 font-semibold uppercase tracking-wider">Total Scanned</p>
                <p className="text-2xl font-extrabold text-slate-900 mt-1">{totalScanned} Apps</p>
                <div className="text-[10px] text-slate-400 mt-2">Parsed from archives</div>
              </div>

              <div className="rounded-xl border border-emerald-200 bg-emerald-50/30 p-5 shadow-sm">
                <p className="text-xs text-emerald-700 font-semibold uppercase tracking-wider">Will Create</p>
                <p className="text-2xl font-extrabold text-emerald-950 mt-1">+{totalCreate} New</p>
                <div className="text-[10px] text-emerald-600 mt-2">Separate rows created</div>
              </div>

              <div className="rounded-xl border border-amber-200 bg-amber-50/30 p-5 shadow-sm">
                <p className="text-xs text-amber-700 font-semibold uppercase tracking-wider">Will Update</p>
                <p className="text-2xl font-extrabold text-amber-950 mt-1">+{totalUpdate} Match</p>
                <div className="text-[10px] text-amber-600 mt-2">Tripadvisor updates</div>
              </div>

              <div className="rounded-xl border border-slate-200 bg-slate-50 p-5 shadow-sm">
                <p className="text-xs text-slate-500 font-semibold uppercase tracking-wider">Excluded</p>
                <p className="text-2xl font-extrabold text-slate-700 mt-1">{totalExcluded} Skipped</p>
                <div className="text-[10px] text-slate-400 mt-2">Unchecked from import</div>
              </div>

              <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
                <p className="text-xs text-slate-500 font-semibold uppercase tracking-wider">Unassigned Files</p>
                <p className="text-2xl font-extrabold text-slate-950 mt-1">{totalUnassigned} Files</p>
                <div className="text-[10px] text-slate-400 mt-2">Left for manual review</div>
              </div>
            </div>

            {/* Filter controls panel */}
            <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm space-y-4">
              <div className="flex items-center gap-2 border-b border-slate-100 pb-3">
                <Filter className="h-5 w-5 text-slate-500" />
                <h3 className="font-bold text-slate-900">Filters & Search Tools</h3>
              </div>

              <div className="grid gap-4 md:grid-cols-4">
                {/* Search */}
                <div className="relative">
                  <span className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                    <Search className="h-4 w-4" />
                  </span>
                  <input
                    type="text"
                    placeholder="Search company, title, notes..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="pl-9 w-full rounded-lg border border-slate-200 py-2 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
                  />
                </div>

                {/* Status */}
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="w-full rounded-lg border border-slate-200 py-2 px-3 text-sm bg-white focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
                >
                  <option value="">All Statuses</option>
                  {STATUS_OPTIONS.map((opt) => (
                    <option key={opt} value={opt}>{STATUS_LABELS[opt] || opt}</option>
                  ))}
                </select>

                {/* Confidence */}
                <select
                  value={confidenceFilter}
                  onChange={(e) => setConfidenceFilter(e.target.value)}
                  className="w-full rounded-lg border border-slate-200 py-2 px-3 text-sm bg-white focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
                >
                  <option value="">All Confidences</option>
                  <option value="HIGH">High ({highConfidenceCount})</option>
                  <option value="MEDIUM">Medium ({mediumConfidenceCount})</option>
                  <option value="LOW">Low ({lowConfidenceCount})</option>
                </select>

                {/* Import Action */}
                <select
                  value={actionFilter}
                  onChange={(e) => setActionFilter(e.target.value)}
                  className="w-full rounded-lg border border-slate-200 py-2 px-3 text-sm bg-white focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
                >
                  <option value="">All Actions</option>
                  <option value="MATCH_EXISTING">MATCH_EXISTING (Tripadvisor)</option>
                  <option value="CREATE_NEW_ATTEMPT">CREATE_NEW_ATTEMPT</option>
                  <option value="CREATE_OR_MATCH_EXACT">CREATE_OR_MATCH_EXACT</option>
                </select>
              </div>

              {/* Quick Filters row */}
              <div className="flex flex-wrap gap-2 pt-2">
                <button
                  onClick={() => setUnresolvedTitlesOnly(!unresolvedTitlesOnly)}
                  className={`inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-xs font-semibold border transition-colors ${
                    unresolvedTitlesOnly
                      ? 'bg-amber-100 border-amber-300 text-amber-800'
                      : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  Unresolved Formal Titles ({unresolvedTitlesCount})
                </button>
                <button
                  onClick={() => setMissingDateOnly(!missingDateOnly)}
                  className={`inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-xs font-semibold border transition-colors ${
                    missingDateOnly
                      ? 'bg-red-100 border-red-300 text-red-800'
                      : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  Missing Dates ({missingDateCount})
                </button>
                <button
                  onClick={() => setHasDocsOnly(!hasDocsOnly)}
                  className={`inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-xs font-semibold border transition-colors ${
                    hasDocsOnly
                      ? 'bg-blue-100 border-blue-300 text-blue-800'
                      : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  Has Attached Documents
                </button>
                {(searchQuery || statusFilter || confidenceFilter || actionFilter || missingDateOnly || hasDocsOnly || verificationFilter || unresolvedTitlesOnly) && (
                  <button
                    onClick={() => {
                      setSearchQuery('');
                      setStatusFilter('');
                      setConfidenceFilter('');
                      setActionFilter('');
                      setMissingDateOnly(false);
                      setHasDocsOnly(false);
                      setVerificationFilter('');
                      setUnresolvedTitlesOnly(false);
                    }}
                    className="inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-xs font-semibold bg-slate-900 border border-slate-950 text-white hover:bg-slate-800 transition-colors"
                  >
                    Reset Filters
                  </button>
                )}
              </div>
            </div>

            {/* Applications list */}
            <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
              <div className="border-b border-slate-200 bg-slate-50/50 px-6 py-4 flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-bold text-slate-900">Scanned Applications ({filteredApps.length} shown)</h2>
                  <p className="text-xs text-slate-500 mt-1">Verify details, adjust values, and choose selections to import.</p>
                </div>
                <div className="flex items-center gap-3">
                  <button
                    onClick={() => {
                      const next = { ...selectedApps };
                      filteredApps.forEach(app => next[app.tempAppId] = true);
                      setSelectedApps(next);
                    }}
                    className="text-xs font-semibold text-brand-600 hover:text-brand-500"
                  >
                    Select All Visible
                  </button>
                  <span className="text-slate-300">|</span>
                  <button
                    onClick={() => {
                      const next = { ...selectedApps };
                      filteredApps.forEach(app => next[app.tempAppId] = false);
                      setSelectedApps(next);
                    }}
                    className="text-xs font-semibold text-slate-500 hover:text-slate-600"
                  >
                    Deselect All Visible
                  </button>
                </div>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse table-fixed min-w-[1200px]">
                  <thead>
                    <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase tracking-wider text-slate-500">
                      <th className="px-6 py-3.5 w-[60px] text-center">
                        <input
                          type="checkbox"
                          checked={filteredApps.length > 0 && filteredApps.every(app => selectedApps[app.tempAppId])}
                          onChange={(e) => {
                            const checked = e.target.checked;
                            const next = { ...selectedApps };
                            filteredApps.forEach(app => next[app.tempAppId] = checked);
                            setSelectedApps(next);
                          }}
                          className="rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                        />
                      </th>
                      <th className="px-6 py-3.5 w-[100px]">ID</th>
                      <th className="px-6 py-3.5 w-[200px]">Company Name</th>
                      <th className="px-6 py-3.5 w-[220px]">Job Title</th>
                      <th className="px-6 py-3.5 w-[140px]">Status</th>
                      <th className="px-6 py-3.5 w-[110px]">Priority</th>
                      <th className="px-6 py-3.5 w-[130px]">Date Applied</th>
                      <th className="px-6 py-3.5 w-[110px]">Confidence</th>
                      <th className="px-6 py-3.5 w-[260px]">Attachments</th>
                      <th className="px-6 py-3.5 w-[160px]">Action & Notes</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 text-sm text-slate-700">
                    {filteredApps.map((app) => {
                      const edits = appDetails[app.tempAppId] || {};
                      const isChecked = !!selectedApps[app.tempAppId];

                      let confidenceColor = 'bg-slate-50 border-slate-200 text-slate-700';
                      if (app.evidenceConfidence === 'HIGH') confidenceColor = 'bg-emerald-50 border-emerald-200 text-emerald-700';
                      else if (app.evidenceConfidence === 'MEDIUM') confidenceColor = 'bg-amber-50 border-amber-200 text-amber-700';
                      else if (app.evidenceConfidence === 'LOW') confidenceColor = 'bg-red-50 border-red-200 text-red-700';

                      return (
                        <tr key={app.tempAppId} className={`hover:bg-slate-50/50 ${isChecked ? '' : 'opacity-60 bg-slate-50/10'}`}>
                          {/* Selection Checkbox */}
                          <td className="px-6 py-4 text-center">
                            <input
                              type="checkbox"
                              checked={isChecked}
                              onChange={(e) => setSelectedApps(prev => ({ ...prev, [app.tempAppId]: e.target.checked }))}
                              className="rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                            />
                          </td>

                          {/* ID */}
                          <td className="px-6 py-4 font-mono text-xs text-slate-500">
                            {app.manifestApplicationId}
                          </td>

                          {/* Company Name */}
                          <td className="px-6 py-4">
                            <input
                              type="text"
                              value={edits.companyName ?? app.companyName}
                              title={edits.companyName ?? app.companyName}
                              onChange={(e) => updateAppField(app.tempAppId, 'companyName', e.target.value)}
                              className="w-full rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500 hover:border-slate-300 transition-colors"
                            />
                          </td>

                          {/* Job Title */}
                          <td className="px-6 py-4">
                            <input
                              type="text"
                              value={edits.jobTitle ?? app.jobTitle}
                              title={edits.jobTitle ?? app.jobTitle}
                              onChange={(e) => updateAppField(app.tempAppId, 'jobTitle', e.target.value)}
                              className="w-full rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500 hover:border-slate-300 transition-colors"
                            />
                          </td>

                          {/* Status */}
                          <td className="px-6 py-4">
                            <select
                              value={edits.status ?? app.status}
                              onChange={(e) => updateAppField(app.tempAppId, 'status', e.target.value)}
                              className="rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500 bg-white"
                            >
                              {STATUS_OPTIONS.map((opt) => (
                                <option key={opt} value={opt}>{STATUS_LABELS[opt] || opt}</option>
                              ))}
                            </select>
                          </td>

                          {/* Priority */}
                          <td className="px-6 py-4">
                            <select
                              value={edits.priority ?? app.priority}
                              onChange={(e) => updateAppField(app.tempAppId, 'priority', e.target.value)}
                              className="rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500 bg-white"
                            >
                              {PRIORITY_OPTIONS.map((opt) => (
                                <option key={opt} value={opt}>{opt}</option>
                              ))}
                            </select>
                          </td>

                          {/* Date Applied */}
                          <td className="px-6 py-4">
                            <input
                              type="text"
                              placeholder="YYYY-MM-DD"
                              value={edits.dateApplied ?? ''}
                              onChange={(e) => updateAppField(app.tempAppId, 'dateApplied', e.target.value)}
                              className={`w-full rounded-md border px-2 py-1 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500 ${
                                !(edits.dateApplied ?? app.dateApplied) ? 'border-amber-300 bg-amber-50/20' : 'border-slate-200'
                              }`}
                            />
                          </td>

                          {/* Evidence Confidence */}
                          <td className="px-6 py-4">
                            <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold border ${confidenceColor}`}>
                              {app.evidenceConfidence}
                            </span>
                          </td>

                          {/* Documents List */}
                          <td className="px-6 py-4 space-y-1.5">
                            {app.documents.map((doc, idx) => (
                              <div key={idx} className="flex items-center gap-1.5 text-xs">
                                <span className="font-medium text-slate-700 truncate max-w-[140px]" title={doc.fileName}>{doc.fileName}</span>
                                <span className="rounded bg-slate-100 text-slate-600 px-1 py-0.5 font-semibold text-[9px]">{doc.documentType}</span>
                                <span className={`rounded-full px-1.5 py-0.5 text-[9px] font-semibold border ${
                                  doc.validationStatus === 'VALID' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' :
                                  doc.validationStatus === 'CHANGED' ? 'bg-amber-50 text-amber-700 border-amber-200' :
                                  'bg-red-50 text-red-700 border-red-200'
                                }`}>{doc.validationStatus}</span>
                              </div>
                            ))}
                            {app.documents.length === 0 && (
                              <span className="text-slate-400 text-xs italic">No documents</span>
                            )}
                          </td>

                          {/* Import Action & Notes */}
                          <td className="px-6 py-4 text-xs space-y-1">
                            <div className="flex items-center gap-1.5">
                              {app.importAction === 'MATCH_EXISTING' ? (
                                <span className="inline-flex items-center gap-1 rounded bg-amber-50 border border-amber-200 text-amber-700 px-2 py-0.5 font-bold" title="Updates existing Tripadvisor application in database.">
                                  MATCH_EXISTING
                                </span>
                              ) : app.importAction === 'CREATE_NEW_ATTEMPT' ? (
                                <span className="inline-flex items-center gap-1 rounded bg-emerald-50 border border-emerald-200 text-emerald-700 px-2 py-0.5 font-bold" title="Creates a new application attempt.">
                                  CREATE_NEW
                                </span>
                              ) : (
                                <span className="inline-flex items-center gap-1 rounded bg-slate-50 border border-slate-200 text-slate-700 px-2 py-0.5 font-bold">
                                  MATCH_EXACT
                                </span>
                              )}
                            </div>
                            {app.notes && (
                              <p className="text-[10px] text-slate-400 italic line-clamp-2 hover:line-clamp-none transition-all" title={app.notes}>
                                {app.notes}
                              </p>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                    {filteredApps.length === 0 && (
                      <tr>
                        <td colSpan={10} className="px-6 py-12 text-center text-slate-400 italic bg-slate-50/50">
                          No applications match the current filters.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Unassigned unique files */}
            <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
              <div className="border-b border-slate-200 bg-slate-50/50 px-6 py-4">
                <h2 className="text-lg font-bold text-slate-900">Unassigned Unique Documents ({scanResult.unassignedFiles.length})</h2>
                <p className="text-xs text-slate-500 mt-1">Associate unassigned files with target applications before importing.</p>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse min-w-[800px]">
                  <thead>
                    <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase tracking-wider text-slate-500">
                      <th className="px-6 py-3.5 w-24 text-center">Select</th>
                      <th className="px-6 py-3.5">Filename</th>
                      <th className="px-6 py-3.5">Size</th>
                      <th className="px-6 py-3.5">SHA-256</th>
                      <th className="px-6 py-3.5">Target Application</th>
                      <th className="px-6 py-3.5">Document Type</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 text-sm text-slate-700">
                    {scanResult.unassignedFiles.map((doc) => {
                      const assoc = unassignedAssociations[doc.tempDocId] || { targetAppId: '', docType: '' };
                      const isSelected = !!selectedUnassigned[doc.tempDocId];
                      const canSelect = !!(assoc.targetAppId && assoc.docType);

                      return (
                        <tr key={doc.tempDocId} className="hover:bg-slate-50/50">
                          <td className="px-6 py-4 text-center">
                            <input
                              type="checkbox"
                              checked={isSelected && canSelect}
                              disabled={!canSelect}
                              onChange={(e) => setSelectedUnassigned(prev => ({ ...prev, [doc.tempDocId]: e.target.checked }))}
                              className="rounded border-slate-300 text-brand-600 focus:ring-brand-500 disabled:opacity-50"
                            />
                          </td>
                          <td className="px-6 py-4 font-medium text-slate-900 truncate max-w-xs" title={doc.fileName}>
                            <div className="flex items-center gap-2">
                              <Paperclip className="h-3.5 w-3.5 text-slate-400" />
                              {doc.fileName}
                            </div>
                          </td>
                          <td className="px-6 py-4">{(doc.fileSize / 1024).toFixed(1)} KB</td>
                          <td className="px-6 py-4 font-mono text-[10px] text-slate-400">{doc.sha256.substring(0, 8)}...</td>
                          <td className="px-6 py-4">
                            <select
                              value={assoc.targetAppId}
                              onChange={(e) => handleUnassignedAssoc(doc.tempDocId, e.target.value, assoc.docType)}
                              className="rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500 bg-white max-w-[200px]"
                            >
                              <option value="">-- Choose Target Application --</option>
                              {scanResult.applications.map((app) => {
                                const edits = appDetails[app.tempAppId] || {};
                                const comp = edits.companyName || app.companyName;
                                const tit = edits.jobTitle || app.jobTitle;
                                return (
                                  <option key={app.tempAppId} value={app.tempAppId}>
                                    {comp} - {tit}
                                  </option>
                                );
                              })}
                            </select>
                          </td>
                          <td className="px-6 py-4">
                            <select
                              value={assoc.docType}
                              onChange={(e) => handleUnassignedAssoc(doc.tempDocId, assoc.targetAppId, e.target.value)}
                              className="rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500 bg-white"
                            >
                              <option value="">-- Choose Type --</option>
                              <option value="CV">CV / Resume</option>
                              <option value="COVER_LETTER">Cover Letter</option>
                              <option value="OTHER">Other Documentation</option>
                            </select>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Confirm Actions */}
            <div className="flex justify-end gap-4 mt-6">
              <button
                onClick={() => {
                  setScanResult(null);
                  setManifestFile(null);
                  setLaptopZipFile(null);
                  setGoogleDriveZipFile(null);
                }}
                className="rounded-lg border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
              >
                Cancel Import
              </button>
              <button
                onClick={() => setShowConfirmModal(true)}
                disabled={isLoading || !scanResult.canConfirm}
                className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-6 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-500 disabled:opacity-50 transition-colors active:scale-98"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="animate-spin" size={18} />
                    Importing Applications...
                  </>
                ) : (
                  `Confirm & Import Selected`
                )}
              </button>
            </div>

            {/* Confirmation dialog overlay */}
            {showConfirmModal && (
              <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm">
                <div className="relative w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl border border-slate-100 flex flex-col gap-5">
                  <div className="flex items-start gap-4">
                    <div className="rounded-full bg-brand-50 p-3 text-brand-600">
                      <HelpCircle className="h-6 w-6" />
                    </div>
                    <div className="space-y-1">
                      <h3 className="text-lg font-bold text-slate-900">Confirm Bulk Import</h3>
                      <p className="text-sm text-slate-500">
                        You are about to finalize the import of selected application entries and document attachments.
                      </p>
                    </div>
                  </div>

                  <div className="rounded-xl bg-slate-50 p-4 border border-slate-100 space-y-3">
                    <div className="flex justify-between text-sm">
                      <span className="text-slate-500 font-medium">Applications to Create (New Attempts)</span>
                      <span className="font-bold text-emerald-700">+{totalCreate}</span>
                    </div>
                    <div className="flex justify-between text-sm">
                      <span className="text-slate-500 font-medium">Applications to Update (Tripadvisor Match)</span>
                      <span className="font-bold text-amber-700">+{totalUpdate}</span>
                    </div>
                    <div className="flex justify-between text-sm">
                      <span className="text-slate-500 font-medium">Excluded applications</span>
                      <span className="font-bold text-slate-400">{totalExcluded}</span>
                    </div>
                    <div className="border-t border-slate-200 my-2 pt-2 flex justify-between text-sm font-bold">
                      <span className="text-slate-900">Total Scanned Applications</span>
                      <span className="text-slate-900">{totalScanned}</span>
                    </div>
                  </div>

                  <div className="flex items-start gap-2.5 rounded-lg bg-amber-50/50 p-3 text-xs text-amber-800 border border-amber-100">
                    <Info className="h-4 w-4 text-amber-600 flex-shrink-0 mt-0.5" />
                    <p>
                      This operation is non-reversible. Newly created attempts will become independent records, and existing matches will update company application history directly.
                    </p>
                  </div>

                  <div className="flex justify-end gap-3 pt-2">
                    <button
                      onClick={() => setShowConfirmModal(false)}
                      className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
                    >
                      Cancel
                    </button>
                    <button
                      onClick={() => {
                        setShowConfirmModal(false);
                        handleConfirmImport();
                      }}
                      className="rounded-lg bg-brand-600 px-5 py-2 text-sm font-semibold text-white shadow-sm hover:bg-brand-500 transition-colors"
                    >
                      Proceed with Import
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        );
      })()}
    </div>
  );
}
