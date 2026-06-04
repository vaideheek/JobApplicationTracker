import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import ApplicationForm from '../components/ApplicationForm';
import { jobApplicationApi } from '../api/jobApplicationApi';
import type { JobApplicationRequest } from '../types';

export default function AddApplication() {
  const navigate = useNavigate();

  const handleSubmit = async (data: JobApplicationRequest) => {
    await jobApplicationApi.create(data);
    navigate('/applications');
  };

  return (
    <div>
      <div className="mb-6">
        <button
          onClick={() => navigate(-1)}
          className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700"
        >
          <ArrowLeft size={16} /> Back
        </button>
        <h1 className="text-2xl font-bold text-slate-900">Add Application</h1>
        <p className="mt-1 text-sm text-slate-500">Track a new job application</p>
      </div>

      <div className="rounded-xl border border-slate-200 bg-white p-6">
        <ApplicationForm onSubmit={handleSubmit} submitLabel="Create Application" />
      </div>
    </div>
  );
}
