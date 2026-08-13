import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { UploadCloud, Folder, FileText, CheckCircle, AlertTriangle, XCircle, ArrowRight, Loader2, HelpCircle } from 'lucide-react';
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
      {scanResult && !isLoading && (
        <div className="space-y-8">
          {/* Applications list */}
          <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
            <div className="border-b border-slate-200 bg-slate-50/50 px-6 py-4">
              <h2 className="text-lg font-bold text-slate-900">Scanned Applications ({scanResult.applications.length})</h2>
              <p className="text-xs text-slate-500 mt-1">Verify, edit, and select applications to bulk import.</p>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase tracking-wider text-slate-500">
                    <th className="px-6 py-3.5 w-12 text-center">
                      <input
                        type="checkbox"
                        checked={Object.values(selectedApps).every(Boolean)}
                        onChange={(e) => {
                          const checked = e.target.checked;
                          const next: Record<string, boolean> = {};
                          scanResult.applications.forEach(app => next[app.tempAppId] = checked);
                          setSelectedApps(next);
                        }}
                        className="rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                      />
                    </th>
                    <th className="px-6 py-3.5">Company Name</th>
                    <th className="px-6 py-3.5">Job Title</th>
                    <th className="px-6 py-3.5">Status</th>
                    <th className="px-6 py-3.5">Priority</th>
                    <th className="px-6 py-3.5">Date Applied</th>
                    <th className="px-6 py-3.5">Attached Documents</th>
                    <th className="px-6 py-3.5">Verification</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200 text-sm text-slate-700">
                  {scanResult.applications.map((app) => {
                    const edits = appDetails[app.tempAppId] || {};
                    const isChecked = !!selectedApps[app.tempAppId];

                    return (
                      <tr key={app.tempAppId} className={`hover:bg-slate-50/50 ${isChecked ? '' : 'opacity-60 bg-slate-50/10'}`}>
                        <td className="px-6 py-4 text-center">
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={(e) => setSelectedApps(prev => ({ ...prev, [app.tempAppId]: e.target.checked }))}
                            className="rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                          />
                        </td>
                        <td className="px-6 py-4 font-medium text-slate-900">
                          <input
                            type="text"
                            value={edits.companyName ?? app.companyName}
                            onChange={(e) => updateAppField(app.tempAppId, 'companyName', e.target.value)}
                            className="w-full rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
                          />
                        </td>
                        <td className="px-6 py-4">
                          <input
                            type="text"
                            value={edits.jobTitle ?? app.jobTitle}
                            onChange={(e) => updateAppField(app.tempAppId, 'jobTitle', e.target.value)}
                            className="w-full rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
                          />
                        </td>
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
                        <td className="px-6 py-4">
                          <input
                            type="text"
                            placeholder="YYYY-MM-DD"
                            value={edits.dateApplied ?? ''}
                            onChange={(e) => updateAppField(app.tempAppId, 'dateApplied', e.target.value)}
                            className="w-32 rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
                          />
                        </td>
                        <td className="px-6 py-4 space-y-1.5">
                          {app.documents.map((doc, idx) => (
                            <div key={idx} className="flex flex-wrap items-center gap-1.5 text-xs">
                              <span className="font-medium text-slate-700 truncate max-w-40" title={doc.fileName}>{doc.fileName}</span>
                              <span className="rounded bg-slate-100 text-slate-600 px-1 py-0.5 font-semibold text-[10px]">{doc.documentType}</span>
                              <span className={`rounded-full px-1.5 py-0.5 text-[9px] font-semibold border ${
                                doc.validationStatus === 'VALID' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' :
                                doc.validationStatus === 'CHANGED' ? 'bg-amber-50 text-amber-700 border-amber-200' :
                                'bg-red-50 text-red-700 border-red-200'
                              }`}>{doc.validationStatus}</span>
                            </div>
                          ))}
                          {app.documents.length === 0 && (
                            <span className="text-slate-400 text-xs italic">No documents attached</span>
                          )}
                        </td>
                        <td className="px-6 py-4 text-center">
                          {app.isDuplicate ? (
                            <span className="inline-flex items-center gap-1 rounded bg-amber-50 border border-amber-200 text-amber-700 px-2 py-1 text-xs font-semibold" title="Matches an existing database record. Attachments will be updated/added directly.">
                              <AlertTriangle size={14} />
                              Reuses Record
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 rounded bg-emerald-50 border border-emerald-200 text-emerald-700 px-2 py-1 text-xs font-semibold">
                              New Record
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Unassigned unique files */}
          <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
            <div className="border-b border-slate-200 bg-slate-50/50 px-6 py-4">
              <h2 className="text-lg font-bold text-slate-900">Unassigned unique documents ({scanResult.unassignedFiles.length})</h2>
              <p className="text-xs text-slate-500 mt-1">Associate unassigned files with target applications before importing.</p>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase tracking-wider text-slate-500">
                    <th className="px-6 py-3.5 w-12 text-center">Select</th>
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
                    // Enable selection only if target application and document type are chosen
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
                          {doc.fileName}
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
              onClick={handleConfirmImport}
              disabled={isLoading}
              className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-6 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-500 disabled:opacity-50 transition-colors"
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
        </div>
      )}
    </div>
  );
}
