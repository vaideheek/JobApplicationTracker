import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Briefcase, Users, Trophy, XCircle, CalendarDays, ArrowRight } from 'lucide-react';
import StatCard from '../components/StatCard';
import StatusBadge from '../components/StatusBadge';
import { jobApplicationApi } from '../api/jobApplicationApi';
import { format } from 'date-fns';
import type { DashboardStats, JobApplication } from '../types';

export default function Dashboard() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [recent, setRecent] = useState<JobApplication[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [statsData, appsData] = await Promise.all([
          jobApplicationApi.getStats(),
          jobApplicationApi.getAll({ page: 0, size: 5 }),
        ]);
        setStats(statsData);
        setRecent(appsData.content);
      } catch (err) {
        console.error('Failed to fetch dashboard data:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-brand-600 border-t-transparent" />
      </div>
    );
  }

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-900">Dashboard</h1>
        <p className="mt-1 text-sm text-slate-500">
          Overview of your job application progress
        </p>
      </div>

      {/* Stats Grid */}
      <div className="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <StatCard
          label="Total Applications"
          value={stats?.totalApplications ?? 0}
          icon={Briefcase}
          color="bg-brand-100 text-brand-600"
        />
        <StatCard
          label="Interviews"
          value={stats?.interviews ?? 0}
          icon={Users}
          color="bg-cyan-100 text-cyan-600"
        />
        <StatCard
          label="Offers"
          value={stats?.offers ?? 0}
          icon={Trophy}
          color="bg-emerald-100 text-emerald-600"
        />
        <StatCard
          label="Rejected"
          value={stats?.rejections ?? 0}
          icon={XCircle}
          color="bg-red-100 text-red-600"
        />
        <StatCard
          label="This Week"
          value={stats?.applicationsThisWeek ?? 0}
          icon={CalendarDays}
          color="bg-amber-100 text-amber-600"
        />
      </div>

      {/* Recent Applications */}
      <div className="rounded-xl border border-slate-200 bg-white">
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <h2 className="text-lg font-semibold text-slate-900">Recent Applications</h2>
          <Link
            to="/applications"
            className="flex items-center gap-1 text-sm font-medium text-brand-600 hover:text-brand-700"
          >
            View all <ArrowRight size={16} />
          </Link>
        </div>

        {recent.length === 0 ? (
          <div className="px-6 py-12 text-center">
            <Briefcase size={40} className="mx-auto text-slate-300" />
            <p className="mt-3 text-sm text-slate-500">No applications yet.</p>
            <Link
              to="/applications/new"
              className="mt-3 inline-block text-sm font-medium text-brand-600 hover:text-brand-700"
            >
              Add your first application →
            </Link>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-slate-100 text-left text-xs font-medium uppercase tracking-wider text-slate-500">
                  <th className="px-6 py-3">Company</th>
                  <th className="px-6 py-3">Role</th>
                  <th className="px-6 py-3">Status</th>
                  <th className="px-6 py-3">Applied</th>
                  <th className="px-6 py-3">Updated</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {recent.map((app) => (
                  <tr key={app.id} className="hover:bg-slate-50">
                    <td className="px-6 py-4">
                      <Link
                        to={`/applications/${app.id}`}
                        className="text-sm font-medium text-slate-900 hover:text-brand-600"
                      >
                        {app.companyName}
                      </Link>
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-600">{app.jobTitle}</td>
                    <td className="px-6 py-4">
                      <StatusBadge status={app.status} />
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-500">
                      {app.dateApplied
                        ? format(new Date(app.dateApplied), 'MMM d, yyyy')
                        : '—'}
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-400">
                      {format(new Date(app.lastUpdatedAt), 'MMM d')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
