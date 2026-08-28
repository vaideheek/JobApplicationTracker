import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Briefcase,
  Users,
  Trophy,
  XCircle,
  CalendarDays,
  ArrowRight,
  AlertTriangle,
  Clock,
  Hourglass,
  TrendingUp,
  Award,
  CheckCircle2,
  Bell,
  FileWarning
} from 'lucide-react';
import StatCard from '../components/StatCard';
import StatusBadge from '../components/StatusBadge';
import { jobApplicationApi } from '../api/jobApplicationApi';
import { format } from 'date-fns';
import type { DashboardStats, JobApplication, DashboardInsightsResponse } from '../types';

export default function Dashboard() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [insights, setInsights] = useState<DashboardInsightsResponse | null>(null);
  const [recent, setRecent] = useState<JobApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [statsFailed, setStatsFailed] = useState(false);
  const [insightsFailed, setInsightsFailed] = useState(false);
  const [recentFailed, setRecentFailed] = useState(false);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [statsResult, insightsResult, appsResult] = await Promise.allSettled([
          jobApplicationApi.getStats(),
          jobApplicationApi.getInsights(),
          jobApplicationApi.getAll({
            page: 0,
            size: 15,
            sortBy: 'lastUpdatedAt',
            sortDir: 'desc',
          }),
        ]);

        if (statsResult.status === 'fulfilled') {
          setStats(statsResult.value);
        } else {
          setStatsFailed(true);
          console.error('Failed to fetch dashboard stats');
        }

        if (insightsResult.status === 'fulfilled') {
          setInsights(insightsResult.value);
        } else {
          setInsightsFailed(true);
          console.error('Failed to fetch dashboard insights');
        }

        if (appsResult.status === 'fulfilled') {
          setRecent((appsResult.value.content || []).slice(0, 5));
        } else {
          setRecentFailed(true);
          console.error('Failed to fetch recent applications');
        }
      } catch (err) {
        console.error('Failed to fetch dashboard data');
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
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Dashboard</h1>
        <p className="mt-1 text-sm text-slate-500">
          Overview of your job application progress
        </p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <StatCard
          label="Total Applications"
          value={statsFailed ? '—' : (stats?.totalApplications ?? 0)}
          icon={Briefcase}
          color="bg-brand-100 text-brand-600"
        />
        <StatCard
          label="Interviews"
          value={statsFailed ? '—' : (stats?.interviews ?? 0)}
          icon={Users}
          color="bg-cyan-100 text-cyan-600"
        />
        <StatCard
          label="Offers"
          value={statsFailed ? '—' : (stats?.offers ?? 0)}
          icon={Trophy}
          color="bg-emerald-100 text-emerald-600"
        />
        <StatCard
          label="Rejected"
          value={statsFailed ? '—' : (stats?.rejections ?? 0)}
          icon={XCircle}
          color="bg-red-100 text-red-600"
        />
        <StatCard
          label="This Week"
          value={statsFailed ? '—' : (stats?.applicationsThisWeek ?? 0)}
          icon={CalendarDays}
          color="bg-amber-100 text-amber-600"
        />
      </div>

      {/* Insights Stats Grid */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <StatCard
          label="High Priority"
          value={insightsFailed ? '—' : (insights?.highPriorityCount ?? 0)}
          icon={AlertTriangle}
          color="bg-rose-100 text-rose-600"
        />
        <StatCard
          label="Follow-ups Needed"
          value={insightsFailed ? '—' : (insights?.followUpNeededCount ?? 0)}
          icon={Clock}
          color="bg-indigo-100 text-indigo-600"
        />
        <StatCard
          label="Upcoming Interviews"
          value={insightsFailed ? '—' : (insights?.upcomingInterviewsCount ?? 0)}
          icon={CalendarDays}
          color="bg-violet-100 text-violet-600"
        />
        <StatCard
          label="Stale (14+ Days)"
          value={insightsFailed ? '—' : (insights?.staleApplicationsCount ?? 0)}
          icon={Hourglass}
          color="bg-slate-100 text-slate-600"
        />
        <StatCard
          label="Missing Documents"
          value={insightsFailed ? '—' : (insights?.missingDocumentsCount ?? 0)}
          icon={FileWarning}
          color="bg-amber-100 text-amber-600"
        />
      </div>

      {/* Middle Row: Recent Applications & Recommended Actions */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Left: Recent Applications */}
        <div className="lg:col-span-2 rounded-xl border border-slate-200 bg-white">
          <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
            <h2 className="text-lg font-semibold text-slate-900">Recent Applications</h2>
            <Link
              to="/applications"
              className="flex items-center gap-1 text-sm font-medium text-brand-600 hover:text-brand-700"
            >
              View all <ArrowRight size={16} />
            </Link>
          </div>

          {recentFailed ? (
            <div className="px-6 py-12 text-center">
              <FileWarning size={40} className="mx-auto text-slate-300" />
              <p className="mt-3 text-sm text-slate-500">Recent applications unavailable.</p>
            </div>
          ) : recent.length === 0 ? (
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

        {/* Right: Recommended Actions */}
        <div className="rounded-xl border border-slate-200 bg-white p-6 flex flex-col h-[380px]">
          <div className="mb-4 flex items-center gap-2 border-b border-slate-100 pb-3">
            <Bell size={20} className="text-brand-600" />
            <h2 className="text-lg font-semibold text-slate-900">Recommended Actions</h2>
          </div>
          
          <div className="flex-1 space-y-3 overflow-y-auto pr-1">
            {insightsFailed ? (
              <div className="flex flex-col items-center justify-center py-12 text-center h-full">
                <FileWarning size={36} className="text-slate-400 mb-2" />
                <p className="text-sm font-semibold text-slate-800">Recommendations unavailable</p>
              </div>
            ) : !insights?.recommendedActions || insights.recommendedActions.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-center h-full">
                <CheckCircle2 size={36} className="text-emerald-500 mb-2" />
                <p className="text-sm font-semibold text-slate-800">All caught up!</p>
                <p className="text-xs text-slate-500 mt-1">No urgent recommendations right now.</p>
              </div>
            ) : (
              insights.recommendedActions.map((action, idx) => {
                const getActionStyle = (type: string) => {
                  switch (type) {
                    case 'FOLLOW_UP':
                      return { bg: 'bg-rose-50 border-rose-100', text: 'text-rose-800', icon: Clock, iconColor: 'text-rose-500' };
                    case 'INTERVIEW_PREP':
                      return { bg: 'bg-violet-50 border-violet-100', text: 'text-violet-800', icon: CalendarDays, iconColor: 'text-violet-500' };
                    case 'ASSESSMENT_COMPLETE':
                      return { bg: 'bg-cyan-50 border-cyan-100', text: 'text-cyan-800', icon: Award, iconColor: 'text-cyan-500' };
                    case 'REVIEW_STALE':
                    default:
                      return { bg: 'bg-slate-50 border-slate-100', text: 'text-slate-800', icon: Hourglass, iconColor: 'text-slate-500' };
                  }
                };
                const style = getActionStyle(action.type);
                const Icon = style.icon;

                return (
                  <Link
                    key={idx}
                    to={`/applications/${action.applicationId}`}
                    className={`flex items-start gap-3 rounded-lg border p-3 transition-shadow hover:shadow-sm ${style.bg}`}
                  >
                    <Icon className={`h-5 w-5 shrink-0 mt-0.5 ${style.iconColor}`} />
                    <div className="flex-1">
                      <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                        {action.type.replace('_', ' ')}
                      </p>
                      <p className={`text-sm font-semibold mt-0.5 ${style.text}`}>
                        {action.message}
                      </p>
                      <p className="text-xs text-slate-500 mt-1">
                        {action.detail} · <span className="font-semibold text-brand-600 hover:underline">View App</span>
                      </p>
                    </div>
                  </Link>
                );
              })
            )}
          </div>
        </div>
      </div>

      {/* Bottom Row: Analytics & Top Companies Grid */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        {/* Analytics Panel */}
        <div className="rounded-xl border border-slate-200 bg-white p-6">
          <div className="mb-4 flex items-center gap-2 border-b border-slate-100 pb-3">
            <TrendingUp size={20} className="text-brand-600" />
            <h2 className="text-lg font-semibold text-slate-900">Application Analytics</h2>
          </div>
          
          <div className="space-y-5">
            {/* Response Rate */}
            <div>
              <div className="flex justify-between items-center text-sm mb-1.5">
                <span className="font-semibold text-slate-700">Response Rate</span>
                <span className="font-bold text-brand-700">
                  {insightsFailed ? '—' : `${(insights?.responseRate ?? 0).toFixed(1)}%`}
                </span>
              </div>
              <p className="text-xs text-slate-400 mb-2">Applications progressing past review / applied (excluding in-review)</p>
              <div className="w-full bg-slate-100 rounded-full h-2.5">
                <div 
                  className="bg-brand-600 h-2.5 rounded-full transition-all duration-500" 
                  style={{ width: `${insightsFailed ? 0 : (insights?.responseRate ?? 0)}%` }}
                />
              </div>
            </div>

            {/* Interview Conversion Rate */}
            <div>
              <div className="flex justify-between items-center text-sm mb-1.5">
                <span className="font-semibold text-slate-700">Interview Conversion Rate</span>
                <span className="font-bold text-cyan-700">
                  {insightsFailed ? '—' : `${(insights?.interviewConversionRate ?? 0).toFixed(1)}%`}
                </span>
              </div>
              <p className="text-xs text-slate-400 mb-2">Percentage of applications that reach interview stage</p>
              <div className="w-full bg-slate-100 rounded-full h-2.5">
                <div 
                  className="bg-cyan-500 h-2.5 rounded-full transition-all duration-500" 
                  style={{ width: `${insightsFailed ? 0 : (insights?.interviewConversionRate ?? 0)}%` }}
                />
              </div>
            </div>

            {/* Offer Conversion Rate */}
            <div>
              <div className="flex justify-between items-center text-sm mb-1.5">
                <span className="font-semibold text-slate-700">Offer Conversion Rate</span>
                <span className="font-bold text-emerald-700">
                  {insightsFailed ? '—' : `${(insights?.offerConversionRate ?? 0).toFixed(1)}%`}
                </span>
              </div>
              <p className="text-xs text-slate-400 mb-2">Percentage of applications resulting in offers</p>
              <div className="w-full bg-slate-100 rounded-full h-2.5">
                <div 
                  className="bg-emerald-500 h-2.5 rounded-full transition-all duration-500" 
                  style={{ width: `${insightsFailed ? 0 : (insights?.offerConversionRate ?? 0)}%` }}
                />
              </div>
            </div>
          </div>
        </div>

        {/* Top Companies */}
        <div className="rounded-xl border border-slate-200 bg-white p-6 flex flex-col h-[320px]">
          <div className="mb-4 flex items-center gap-2 border-b border-slate-100 pb-3">
            <Briefcase size={20} className="text-brand-600" />
            <h2 className="text-lg font-semibold text-slate-900">Top Companies Applied To</h2>
          </div>
          
          <div className="flex-1 flex flex-col justify-center space-y-4 overflow-y-auto">
            {insightsFailed ? (
              <div className="text-center py-12 text-slate-400 text-sm h-full flex items-center justify-center">
                Top companies data unavailable.
              </div>
            ) : !insights?.topCompanies || insights.topCompanies.length === 0 ? (
              <div className="text-center py-12 text-slate-400 text-sm h-full flex items-center justify-center">
                No company data available yet.
              </div>
            ) : (
              insights.topCompanies.map((company, idx) => {
                const maxCount = insights.topCompanies[0]?.count || 1;
                const pct = (company.count / maxCount) * 100;
                return (
                  <div key={idx} className="space-y-1">
                    <div className="flex justify-between items-center text-sm">
                      <span className="font-medium text-slate-800">{company.companyName}</span>
                      <span className="text-xs text-slate-500 font-semibold">{company.count} app{company.count > 1 ? 's' : ''}</span>
                    </div>
                    <div className="w-full bg-slate-100 rounded-full h-2">
                      <div 
                        className="bg-brand-500 h-2 rounded-full transition-all duration-500" 
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
