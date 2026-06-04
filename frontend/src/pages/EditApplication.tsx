import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import ApplicationForm from '../components/ApplicationForm';
import { jobApplicationApi } from '../api/jobApplicationApi';
import type { JobApplication, JobApplicationRequest } from '../types';

export default function EditApplication() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [app, setApp] = useState<JobApplication | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    jobApplicationApi.getById(Number(id))
      .then(setApp)
      .catch(() => navigate('/applications'))
      .finally(() => setLoading(false));
  }, [id, navigate]);

  const handleSubmit = async (data: JobApplicationRequest) => {
    await jobApplicationApi.update(Number(id), data);
    navigate(`/applications/${id}`);
  };

  if (loading) return <div className="flex h-64 items-center justify-center"><div className="h-8 w-8 animate-spin rounded-full border-2 border-brand-600 border-t-transparent" /></div>;
  if (!app) return null;

  const initialData: JobApplicationRequest = {
    companyName: app.companyName, jobTitle: app.jobTitle, location: app.location || '',
    jobUrl: app.jobUrl || '', dateApplied: app.dateApplied || '', status: app.status,
    stage: app.stage || '', recruiterEmail: app.recruiterEmail || '', notes: app.notes || '',
    source: app.source || '', companyCareerUrl: app.companyCareerUrl || '',
    salaryRange: app.salaryRange || '', priority: app.priority || undefined,
    followUpDate: app.followUpDate || '', deadlineDate: app.deadlineDate || '',
  };

  return (
    <div>
      <div className="mb-6">
        <button onClick={() => navigate(-1)} className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700"><ArrowLeft size={16} /> Back</button>
        <h1 className="text-2xl font-bold text-slate-900">Edit Application</h1>
        <p className="mt-1 text-sm text-slate-500">{app.jobTitle} at {app.companyName}</p>
      </div>
      <div className="rounded-xl border border-slate-200 bg-white p-6">
        <ApplicationForm initialData={initialData} onSubmit={handleSubmit} submitLabel="Save Changes" />
      </div>
    </div>
  );
}
