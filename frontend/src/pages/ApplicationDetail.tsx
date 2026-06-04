import { useEffect, useState } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { ArrowLeft, Edit, Trash2, ExternalLink, Mail, MapPin, Calendar, Clock, DollarSign, Flag } from 'lucide-react';
import StatusBadge from '../components/StatusBadge';
import Timeline from '../components/Timeline';
import DeleteModal from '../components/DeleteModal';
import { jobApplicationApi } from '../api/jobApplicationApi';
import { format } from 'date-fns';
import type { JobApplication } from '../types';

export default function ApplicationDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [app, setApp] = useState<JobApplication | null>(null);
  const [loading, setLoading] = useState(true);
  const [showDelete, setShowDelete] = useState(false);

  useEffect(() => {
    jobApplicationApi.getById(Number(id))
      .then(setApp)
      .catch(() => navigate('/applications'))
      .finally(() => setLoading(false));
  }, [id, navigate]);

  const handleDelete = async () => {
    await jobApplicationApi.delete(Number(id));
    navigate('/applications');
  };

  if (loading) return <div className="flex h-64 items-center justify-center"><div className="h-8 w-8 animate-spin rounded-full border-2 border-brand-600 border-t-transparent" /></div>;
  if (!app) return null;

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

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Details */}
        <div className="lg:col-span-2 space-y-6">
          <div className="rounded-xl border border-slate-200 bg-white p-6">
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
            {(app.jobUrl || app.companyCareerUrl) && (
              <div className="mt-6 flex flex-wrap gap-3 border-t border-slate-100 pt-4">
                {app.jobUrl && (
                  <a href={app.jobUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:text-brand-700">
                    <ExternalLink size={14} /> Job Posting
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

          {/* Notes */}
          {app.notes && (
            <div className="rounded-xl border border-slate-200 bg-white p-6">
              <h2 className="mb-3 text-lg font-semibold text-slate-900">Notes</h2>
              <p className="whitespace-pre-wrap text-sm text-slate-600 leading-relaxed">{app.notes}</p>
            </div>
          )}
        </div>

        {/* Timeline */}
        <div className="rounded-xl border border-slate-200 bg-white p-6">
          <h2 className="mb-4 text-lg font-semibold text-slate-900">Status Timeline</h2>
          <Timeline entries={app.statusHistory || []} />
        </div>
      </div>

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
