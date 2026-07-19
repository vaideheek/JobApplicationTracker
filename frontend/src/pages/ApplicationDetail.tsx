import { useEffect, useState } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { 
  ArrowLeft, 
  Edit, 
  Trash2, 
  ExternalLink, 
  Mail, 
  MapPin, 
  Calendar, 
  Clock, 
  DollarSign, 
  Flag,
  Upload,
  Download,
  Trash,
  FileText,
  FileWarning
} from 'lucide-react';
import StatusBadge from '../components/StatusBadge';
import Timeline from '../components/Timeline';
import DeleteModal from '../components/DeleteModal';
import { jobApplicationApi } from '../api/jobApplicationApi';
import { format } from 'date-fns';
import type { JobApplication, ApplicationDocument } from '../types';

export default function ApplicationDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [app, setApp] = useState<JobApplication | null>(null);
  const [loading, setLoading] = useState(true);
  const [showDelete, setShowDelete] = useState(false);
  
  // Tab and document states
  const [activeTab, setActiveTab] = useState<'overview' | 'documents' | 'timeline'>('overview');
  const [docs, setDocs] = useState<ApplicationDocument[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const fetchDocs = async () => {
    try {
      const data = await jobApplicationApi.getDocuments(Number(id));
      setDocs(data);
    } catch (err) {
      console.error('Failed to fetch documents:', err);
    }
  };

  useEffect(() => {
    jobApplicationApi.getById(Number(id))
      .then(setApp)
      .catch(() => navigate('/applications'))
      .finally(() => setLoading(false));

    fetchDocs();
  }, [id, navigate]);

  const handleDelete = async () => {
    await jobApplicationApi.delete(Number(id));
    navigate('/applications');
  };

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>, type: 'CV' | 'COVER_LETTER' | 'OTHER') => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploading(true);
    setUploadError(null);
    try {
      await jobApplicationApi.uploadDocument(Number(id), file, type);
      await fetchDocs();
    } catch (err: any) {
      setUploadError(err.response?.data?.message || 'Failed to upload document.');
    } finally {
      setUploading(false);
      // Reset input value
      e.target.value = '';
    }
  };

  const handleDeleteDoc = async (docId: number) => {
    try {
      await jobApplicationApi.deleteDocument(Number(id), docId);
      await fetchDocs();
    } catch (err) {
      console.error('Failed to delete document:', err);
    }
  };

  const handleDownload = async (docId: number, fileName: string) => {
    try {
      const blob = await jobApplicationApi.downloadDocument(Number(id), docId);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', fileName);
      document.body.appendChild(link);
      link.click();
      if (link.parentNode) {
        link.parentNode.removeChild(link);
      }
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Failed to download document:', err);
    }
  };

  if (loading) return <div className="flex h-64 items-center justify-center"><div className="h-8 w-8 animate-spin rounded-full border-2 border-brand-600 border-t-transparent" /></div>;
  if (!app) return null;

  const renderDocCard = (title: string, type: 'CV' | 'COVER_LETTER', doc?: ApplicationDocument) => {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-5 space-y-4 shadow-sm">
        <div className="flex justify-between items-center border-b border-slate-100 pb-2">
          <h3 className="font-semibold text-slate-800 text-sm">{title}</h3>
          {doc && <span className="text-xs text-slate-400">Uploaded {format(new Date(doc.uploadedAt), 'MMM d, yyyy')}</span>}
        </div>
        {doc ? (
          <div className="flex items-center justify-between gap-4 p-3 bg-slate-50 rounded-lg border border-slate-100">
            <div className="flex items-center gap-2.5 overflow-hidden">
              <FileText className="h-5 w-5 text-brand-600 shrink-0" />
              <span className="text-sm font-medium text-slate-700 truncate">{doc.fileName}</span>
            </div>
            <div className="flex gap-2 shrink-0">
              <button 
                onClick={() => handleDownload(doc.id, doc.fileName)}
                className="p-1.5 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-md transition-colors"
                title="Download"
              >
                <Download size={16} />
              </button>
              <button 
                onClick={() => handleDeleteDoc(doc.id)}
                className="p-1.5 text-rose-500 hover:text-rose-700 hover:bg-rose-50 rounded-md transition-colors"
                title="Delete"
              >
                <Trash size={16} />
              </button>
            </div>
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center border-2 border-dashed border-slate-200 rounded-lg p-6 hover:border-brand-500 transition-colors">
            <Upload className="h-6 w-6 text-slate-400 mb-2" />
            <span className="text-xs text-slate-500 font-medium mb-3">Upload PDF or DOCX (Max 10MB)</span>
            <label className="cursor-pointer inline-flex items-center justify-center rounded-lg bg-brand-600 px-4 py-2 text-xs font-semibold text-white hover:bg-brand-700 transition-colors">
              Choose File
              <input 
                type="file" 
                accept=".pdf,.docx" 
                onChange={(e) => handleUpload(e, type)} 
                className="hidden" 
                disabled={uploading}
              />
            </label>
          </div>
        )}
      </div>
    );
  };

  const renderOtherDocsCard = () => {
    const otherDocs = docs.filter(d => d.documentType === 'OTHER');
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-5 space-y-4 shadow-sm">
        <div className="flex justify-between items-center border-b border-slate-100 pb-2">
          <h3 className="font-semibold text-slate-800 text-sm">Other Documents</h3>
          <span className="text-xs text-slate-400">{otherDocs.length} file(s)</span>
        </div>
        
        {otherDocs.length > 0 && (
          <div className="space-y-2 max-h-[200px] overflow-y-auto pr-1">
            {otherDocs.map((doc) => (
              <div key={doc.id} className="flex items-center justify-between gap-4 p-3 bg-slate-50 rounded-lg border border-slate-100">
                <div className="flex items-center gap-2.5 overflow-hidden">
                  <FileText className="h-5 w-5 text-indigo-500 shrink-0" />
                  <div className="flex flex-col overflow-hidden">
                    <span className="text-sm font-medium text-slate-700 truncate">{doc.fileName}</span>
                    <span className="text-[10px] text-slate-400">Uploaded {format(new Date(doc.uploadedAt), 'MMM d, yyyy')}</span>
                  </div>
                </div>
                <div className="flex gap-2 shrink-0">
                  <button 
                    onClick={() => handleDownload(doc.id, doc.fileName)}
                    className="p-1.5 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-md transition-colors"
                    title="Download"
                  >
                    <Download size={16} />
                  </button>
                  <button 
                    onClick={() => handleDeleteDoc(doc.id)}
                    className="p-1.5 text-rose-500 hover:text-rose-700 hover:bg-rose-50 rounded-md transition-colors"
                    title="Delete"
                  >
                    <Trash size={16} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        <div className="flex flex-col items-center justify-center border-2 border-dashed border-slate-200 rounded-lg p-6 hover:border-brand-500 transition-colors">
          <Upload className="h-6 w-6 text-slate-400 mb-2" />
          <span className="text-xs text-slate-500 font-medium mb-3">Upload other files (PDF, DOCX)</span>
          <label className="cursor-pointer inline-flex items-center justify-center rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-700 transition-colors">
            Upload File
            <input 
              type="file" 
              accept=".pdf,.docx" 
              onChange={(e) => handleUpload(e, 'OTHER')} 
              className="hidden" 
              disabled={uploading}
            />
          </label>
        </div>
      </div>
    );
  };

  return (
    <div>
      <button onClick={() => navigate('/applications')} className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700">
        <ArrowLeft size={16} /> Back to Applications
      </button>

      {/* Header */}
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">{app.jobTitle}</h1>
          <p className="mt-1 text-lg text-slate-600">{app.companyName}</p>
          <div className="mt-2"><StatusBadge status={app.status} /></div>
        </div>
        <div className="flex gap-2">
          <Link to={`/applications/${app.id}/edit`} className="inline-flex items-center gap-2 rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
            <Edit size={16} /> Edit
          </Link>
          <button onClick={() => setShowDelete(true)} className="inline-flex items-center gap-2 rounded-lg border border-red-200 px-4 py-2 text-sm font-medium text-red-600 hover:bg-red-50">
            <Trash2 size={16} /> Delete
          </button>
        </div>
      </div>

      {/* Tab Selectors */}
      <div className="mb-6 border-b border-slate-200">
        <div className="flex gap-6">
          {(['overview', 'documents', 'timeline'] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`pb-3 text-sm font-semibold capitalize border-b-2 transition-colors ${
                activeTab === tab
                  ? 'border-brand-600 text-brand-600'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              {tab}
            </button>
          ))}
        </div>
      </div>

      {/* Tab Contents */}
      {activeTab === 'overview' && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <div className="lg:col-span-2 space-y-6">
            {/* Details */}
            <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
              <h2 className="mb-4 text-lg font-semibold text-slate-900">Details</h2>
              <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                {app.location && <Detail icon={MapPin} label="Location" value={app.location} />}
                {app.dateApplied && <Detail icon={Calendar} label="Date Applied" value={format(new Date(app.dateApplied), 'MMM d, yyyy')} />}
                {app.stage && <Detail icon={Flag} label="Stage" value={app.stage} />}
                {app.source && <Detail icon={Flag} label="Source" value={app.source} />}
                {app.salaryRange && <Detail icon={DollarSign} label="Salary Range" value={app.salaryRange} />}
                {app.priority && <Detail icon={Flag} label="Priority" value={app.priority.charAt(0) + app.priority.slice(1).toLowerCase()} />}
                {app.recruiterEmail && <Detail icon={Mail} label="Recruiter" value={app.recruiterEmail} />}
                {app.followUpDate && <Detail icon={Calendar} label="Follow-up" value={format(new Date(app.followUpDate), 'MMM d, yyyy')} />}
                {app.deadlineDate && <Detail icon={Calendar} label="Deadline" value={format(new Date(app.deadlineDate), 'MMM d, yyyy')} />}
                <Detail icon={Clock} label="Last Updated" value={format(new Date(app.lastUpdatedAt), 'MMM d, yyyy · h:mm a')} />
              </dl>

              {/* Links */}
              {(app.jobUrl || app.companyCareerUrl || app.originalJobUrl) && (
                <div className="mt-6 flex flex-wrap gap-3 border-t border-slate-100 pt-4">
                  {app.jobUrl && (
                    <a href={app.jobUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:text-brand-700">
                      <ExternalLink size={14} /> Job Posting
                    </a>
                  )}
                  {app.originalJobUrl && (
                    <a href={app.originalJobUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:text-brand-700">
                      <ExternalLink size={14} /> Job URL
                    </a>
                  )}
                  {app.companyCareerUrl && (
                    <a href={app.companyCareerUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:text-brand-700">
                      <ExternalLink size={14} /> Career Page
                    </a>
                  )}
                </div>
              )}
            </div>

            {/* Job Matching Analysis (Future Ready nullable columns) */}
            {app.matchScore !== null && (
              <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
                <h2 className="mb-4 text-lg font-semibold text-slate-900">Job Matching Analysis</h2>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                  <div className="rounded-lg border border-slate-100 p-4 bg-slate-50/55 flex flex-col justify-center">
                    <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Match Score</span>
                    <span className="text-3xl font-extrabold text-brand-600">{app.matchScore}%</span>
                  </div>
                  {app.matchedSkills && (
                    <div className="lg:col-span-2 rounded-lg border border-slate-100 p-4 bg-slate-50/55">
                      <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider block mb-1">Matched Skills</span>
                      <p className="text-sm text-slate-700">{app.matchedSkills}</p>
                    </div>
                  )}
                  {app.missingSkills && (
                    <div className="lg:col-span-3 rounded-lg border border-rose-100 p-4 bg-rose-50/20">
                      <span className="text-xs font-semibold text-rose-600 uppercase tracking-wider block mb-1">Missing Skills</span>
                      <p className="text-sm text-slate-700">{app.missingSkills}</p>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Job Description */}
            {app.jobDescription && (
              <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
                <h2 className="mb-3 text-lg font-semibold text-slate-900">Job Description</h2>
                <div className="rounded-lg bg-slate-50 p-4 border border-slate-100 max-h-[400px] overflow-y-auto">
                  <p className="whitespace-pre-wrap text-sm text-slate-600 leading-relaxed font-sans">{app.jobDescription}</p>
                </div>
              </div>
            )}

            {/* Notes */}
            {app.notes && (
              <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
                <h2 className="mb-3 text-lg font-semibold text-slate-900">Notes</h2>
                <p className="whitespace-pre-wrap text-sm text-slate-600 leading-relaxed">{app.notes}</p>
              </div>
            )}
          </div>
        </div>
      )}

      {activeTab === 'documents' && (
        <div className="space-y-6">
          {/* Warning banner */}
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 flex gap-3 text-amber-800 text-sm">
            <FileWarning className="h-5 w-5 shrink-0 text-amber-600 mt-0.5" />
            <div>
              <p className="font-semibold">Local Storage MVP Warning</p>
              <p className="mt-0.5 font-normal">Files are stored locally for this MVP. Do not upload sensitive documents if you plan to make the repository public.</p>
            </div>
          </div>

          {uploadError && (
            <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-red-700 text-sm">
              {uploadError}
            </div>
          )}

          <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
            {renderDocCard('CV Used', 'CV', docs.find(d => d.documentType === 'CV'))}
            {renderDocCard('Cover Letter Used', 'COVER_LETTER', docs.find(d => d.documentType === 'COVER_LETTER'))}
          </div>

          {/* Other Documents */}
          {renderOtherDocsCard()}
        </div>
      )}

      {activeTab === 'timeline' && (
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold text-slate-900">Status Timeline</h2>
          <Timeline entries={app.statusHistory || []} />
        </div>
      )}

      <DeleteModal isOpen={showDelete} companyName={app.companyName} jobTitle={app.jobTitle} onConfirm={handleDelete} onCancel={() => setShowDelete(false)} />
    </div>
  );
}

function Detail({ icon: Icon, label, value }: { icon: React.ElementType; label: string; value: string }) {
  return (
    <div className="flex items-start gap-3">
      <Icon size={16} className="mt-0.5 flex-shrink-0 text-slate-400" />
      <div>
        <dt className="text-xs font-medium text-slate-500">{label}</dt>
        <dd className="text-sm text-slate-900">{value}</dd>
      </div>
    </div>
  );
}
