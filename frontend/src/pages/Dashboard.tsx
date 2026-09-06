import { useEffect, useState, useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
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
  FileWarning,
  Send,
  MessageSquare,
  Activity,
  Layers,
  Info,
} from 'lucide-react';
import StatCard, { StatComparison } from '../components/StatCard';
import StatusBadge from '../components/StatusBadge';
import PeriodSelector from '../components/PeriodSelector';
import ApplicationsVolumeChart from '../components/ApplicationsVolumeChart';
import { jobApplicationApi } from '../api/jobApplicationApi';
import { format } from 'date-fns';
import type {
  DashboardStats,
  JobApplication,
  DashboardInsightsResponse,
  CurrentPipelineResponse,
  PeriodAnalyticsResponse,
  PeriodPreset,
} from '../types';

const VALID_PRESETS: PeriodPreset[] = [
  'THIS_WEEK',
  'LAST_WEEK',
  'THIS_MONTH',
  'LAST_MONTH',
  'LAST_30_DAYS',
  'LAST_90_DAYS',
  'YTD',
  'ALL_TIME',
  'CUSTOM',
];

export default function Dashboard() {
  const [searchParams, setSearchParams] = useSearchParams();

  // URL state for period analytics
  const rangeParam = searchParams.get('range') as PeriodPreset | null;
  const currentRange: PeriodPreset =
    rangeParam && VALID_PRESETS.includes(rangeParam) ? rangeParam : 'THIS_MONTH';
  const customFrom = searchParams.get('from') || undefined;
  const customTo = searchParams.get('to') || undefined;
  const compare = searchParams.get('compare') !== 'false';

  // Global dashboard state
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [insights, setInsights] = useState<DashboardInsightsResponse | null>(null);
  const [pipeline, setPipeline] = useState<CurrentPipelineResponse | null>(null);
  const [recent, setRecent] = useState<JobApplication[]>([]);
  const [initialLoading, setInitialLoading] = useState(true);

  // Period analytics state
  const [periodAnalytics, setPeriodAnalytics] = useState<PeriodAnalyticsResponse | null>(null);
  const [periodLoading, setPeriodLoading] = useState(false);
  const [periodFailed, setPeriodFailed] = useState(false);

  // Individual failure flags
  const [statsFailed, setStatsFailed] = useState(false);
  const [insightsFailed, setInsightsFailed] = useState(false);
  const [pipelineFailed, setPipelineFailed] = useState(false);
  const [recentFailed, setRecentFailed] = useState(false);

  // Fetch initial global data once
  useEffect(() => {
    const fetchGlobalData = async () => {
      try {
        const [statsResult, insightsResult, pipelineResult, appsResult] = await Promise.allSettled([
          jobApplicationApi.getStats(),
          jobApplicationApi.getInsights(),
          jobApplicationApi.getCurrentPipeline(),
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

        if (pipelineResult.status === 'fulfilled') {
          setPipeline(pipelineResult.value);
        } else {
          setPipelineFailed(true);
          console.error('Failed to fetch current pipeline');
        }

        if (appsResult.status === 'fulfilled') {
          setRecent((appsResult.value.content || []).slice(0, 5));
        } else {
          setRecentFailed(true);
          console.error('Failed to fetch recent applications');
        }
      } catch {
        console.error('Failed to fetch dashboard global data');
      } finally {
        setInitialLoading(false);
      }
    };

    fetchGlobalData();
  }, []);

  // Fetch period analytics whenever range/from/to/compare changes
  useEffect(() => {
    const fetchPeriod = async () => {
      // If CUSTOM but dates are missing or invalid, do not send bad request
      if (currentRange === 'CUSTOM' && (!customFrom || !customTo || customFrom > customTo)) {
        return;
      }

      setPeriodLoading(true);
      setPeriodFailed(false);
      try {
        const data = await jobApplicationApi.getPeriodAnalytics({
          range: currentRange,
          from: currentRange === 'CUSTOM' ? customFrom : undefined,
          to: currentRange === 'CUSTOM' ? customTo : undefined,
          compare: currentRange !== 'ALL_TIME' && compare,
        });
        setPeriodAnalytics(data);
      } catch {
        setPeriodFailed(true);
        console.error('Failed to fetch period analytics');
      } finally {
        setPeriodLoading(false);
      }
    };

    fetchPeriod();
  }, [currentRange, customFrom, customTo, compare]);

  const handlePeriodChange = ({
    range,
    from,
    to,
    compare: nextCompare,
  }: {
    range: PeriodPreset;
    from?: string;
    to?: string;
    compare: boolean;
  }) => {
    const newParams: Record<string, string> = { range };
    if (range === 'CUSTOM') {
      if (from) newParams.from = from;
      if (to) newParams.to = to;
    }
    if (!nextCompare && range !== 'ALL_TIME') {
      newParams.compare = 'false';
    }
    setSearchParams(newParams);
  };

  const getComparison = (
    currentVal?: number,
    prevVal?: number,
    neutral?: boolean
  ): StatComparison | null => {
    if (
      !compare ||
      !periodAnalytics?.comparisonAvailable ||
      currentVal === undefined ||
      prevVal === undefined
    ) {
      return null;
    }
    return {
      diff: currentVal - prevVal,
      prevValue: prevVal,
      neutral,
    };
  };

  const submittedDrilldownUrl = useMemo(() => {
    if (currentRange === 'ALL_TIME') return '/applications';
    if (periodAnalytics?.from && periodAnalytics?.to) {
      return `/applications?dateFrom=${periodAnalytics.from}&dateTo=${periodAnalytics.to}`;
    }
    return '/applications';
  }, [currentRange, periodAnalytics?.from, periodAnalytics?.to]);

  if (initialLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-brand-600 border-t-transparent" />
      </div>
    );
  }

  const cur = periodAnalytics?.current;
  const prev = periodAnalytics?.previous;

  return (
    <div className="space-y-8">
      {/* 1. Header & Period Selector */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Dashboard</h1>
          <p className="mt-1 text-sm text-slate-500">
            Overview of your job search progress, metrics, and activity
          </p>
        </div>
      </div>

      {/* Period Selector with URL Persistence */}
      <PeriodSelector
        range={currentRange}
        from={customFrom}
        to={customTo}
        compare={compare}
        startDate={periodAnalytics?.from}
        endDate={periodAnalytics?.to}
        previousStartDate={periodAnalytics?.previousFrom}
        previousEndDate={periodAnalytics?.previousTo}
        comparisonAvailable={periodAnalytics?.comparisonAvailable ?? periodAnalytics?.previous != null}
        onChange={handlePeriodChange}
      />

      {/* 2. Period Performance Cards */}
      <div>
        <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <h2 className="text-base font-semibold text-slate-900">Period Performance</h2>
            <span
              title="Event metrics reflect logged user and employer activity. Synthetic bulk-import history entries are excluded."
              className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600 cursor-help border border-slate-200"
            >
              <Info size={12} className="text-slate-400" />
              <span>Organic Activity Only</span>
            </span>
          </div>
          {periodLoading && (
            <span className="text-xs text-slate-400 animate-pulse">Updating metrics...</span>
          )}
        </div>

        {periodFailed ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-center text-sm text-red-700">
            Unable to load period analytics. Please try another date range.
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-6">
            <StatCard
              label="Applications Submitted"
              value={cur ? cur.applicationsSubmitted : 0}
              icon={Send}
              color="bg-brand-100 text-brand-600"
              to={submittedDrilldownUrl}
              comparison={getComparison(cur?.applicationsSubmitted, prev?.applicationsSubmitted)}
              tooltip="Total applications submitted during this period. Click to view."
            />
            <StatCard
              label="Responses Recorded"
              value={cur ? cur.responsesRecorded : 0}
              icon={MessageSquare}
              color="bg-emerald-100 text-emerald-600"
              comparison={getComparison(cur?.responsesRecorded, prev?.responsesRecorded)}
              tooltip="Distinct applications receiving an employer response during this period. Excludes bulk import history."
            />
            <StatCard
              label="Reached Assessment"
              value={cur ? cur.assessmentsReached : 0}
              icon={Award}
              color="bg-purple-100 text-purple-600"
              comparison={getComparison(cur?.assessmentsReached, prev?.assessmentsReached)}
              tooltip="Applications that advanced to Assessment stage during this period. Excludes bulk import history."
            />
            <StatCard
              label="Reached Interview"
              value={cur ? cur.interviewsReached : 0}
              icon={Users}
              color="bg-cyan-100 text-cyan-600"
              comparison={getComparison(cur?.interviewsReached, prev?.interviewsReached)}
              tooltip="Applications that advanced to Interview stage during this period. Excludes bulk import history."
            />
            <StatCard
              label="Offers Received"
              value={cur ? cur.offersReached : 0}
              icon={Trophy}
              color="bg-amber-100 text-amber-600"
              comparison={getComparison(cur?.offersReached, prev?.offersReached)}
              tooltip="Offers recorded during this period. Excludes bulk import history."
            />
            <StatCard
              label="Rejections"
              value={cur ? cur.rejectionsRecorded : 0}
              icon={XCircle}
              color="bg-rose-100 text-rose-600"
              comparison={getComparison(cur?.rejectionsRecorded, prev?.rejectionsRecorded, true)}
              tooltip="Rejections recorded during this period. Excludes bulk import history."
            />
          </div>
        )}
      </div>

      {/* 3. Applications Volume Timeline Chart */}
      {periodAnalytics && (
        <ApplicationsVolumeChart
          volumeBuckets={periodAnalytics.volume}
          unknownDateApplications={periodAnalytics.unknownDateApplications}
        />
      )}

      {/* 4. Current Pipeline / Status Overview */}
      <div>
        <div className="mb-3">
          <div className="flex items-center gap-2">
            <Layers size={18} className="text-brand-600" />
            <h2 className="text-base font-semibold text-slate-900">Current Pipeline</h2>
          </div>
          <p className="mt-0.5 text-xs text-slate-500">
            Real-time status breakdown across all tracked applications
          </p>
        </div>

        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
          <StatCard
            label="Total Active"
            value={pipelineFailed ? '—' : (pipeline?.totalActive ?? 0)}
            icon={Activity}
            color="bg-brand-50 text-brand-700"
            subtext="In active process"
          />
          <StatCard
            label="Applied"
            value={pipelineFailed ? '—' : (pipeline?.applied ?? 0)}
            icon={Send}
            color="bg-blue-50 text-blue-700"
            to="/applications?status=APPLIED"
          />
          <StatCard
            label="In Review"
            value={pipelineFailed ? '—' : (pipeline?.inReview ?? 0)}
            icon={Clock}
            color="bg-amber-50 text-amber-700"
            to="/applications?status=IN_REVIEW"
          />
          <StatCard
            label="Assessment"
            value={pipelineFailed ? '—' : (pipeline?.assessment ?? 0)}
            icon={Award}
            color="bg-purple-50 text-purple-700"
            to="/applications?status=ASSESSMENT"
          />
          <StatCard
            label="Interview"
            value={pipelineFailed ? '—' : (pipeline?.interview ?? 0)}
            icon={Users}
            color="bg-cyan-50 text-cyan-700"
            to="/applications?status=INTERVIEW"
          />
          <StatCard
            label="Offer"
            value={pipelineFailed ? '—' : (pipeline?.offer ?? 0)}
            icon={Trophy}
            color="bg-emerald-50 text-emerald-700"
            to="/applications?status=OFFER"
          />
          <StatCard
            label="No Response"
            value={pipelineFailed ? '—' : (pipeline?.noResponse ?? 0)}
            icon={Hourglass}
            color="bg-slate-50 text-slate-600"
            to="/applications?status=NO_RESPONSE"
          />
        </div>
      </div>

      {/* 5. Insights & Action Cards */}
      <div>
        <div className="mb-3">
          <h2 className="text-base font-semibold text-slate-900">Attention & Insights</h2>
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
          <StatCard
            label="High Priority"
            value={insightsFailed ? '—' : (insights?.highPriorityCount ?? 0)}
            icon={AlertTriangle}
            color="bg-rose-100 text-rose-600"
            to="/applications?priority=HIGH"
            tooltip="Click to view all High Priority applications"
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
            to="/applications?documentState=NO_DOCUMENTS"
            tooltip="Click to view applications missing CVs or cover letters"
          />
        </div>
      </div>

      {/* 6. Middle Row: Recent Applications & Recommended Actions */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Left: Recent Applications */}
        <div className="lg:col-span-2 rounded-xl border border-slate-200 bg-white shadow-sm">
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
        <div className="rounded-xl border border-slate-200 bg-white p-6 flex flex-col h-[380px] shadow-sm">
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
                      return {
                        bg: 'bg-rose-50 border-rose-100',
                        text: 'text-rose-800',
                        icon: Clock,
                        iconColor: 'text-rose-500',
                      };
                    case 'INTERVIEW_PREP':
                      return {
                        bg: 'bg-violet-50 border-violet-100',
                        text: 'text-violet-800',
                        icon: CalendarDays,
                        iconColor: 'text-violet-500',
                      };
                    case 'ASSESSMENT_COMPLETE':
                      return {
                        bg: 'bg-cyan-50 border-cyan-100',
                        text: 'text-cyan-800',
                        icon: Award,
                        iconColor: 'text-cyan-500',
                      };
                    case 'REVIEW_STALE':
                    default:
                      return {
                        bg: 'bg-slate-50 border-slate-100',
                        text: 'text-slate-800',
                        icon: Hourglass,
                        iconColor: 'text-slate-500',
                      };
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
                        {action.detail} ·{' '}
                        <span className="font-semibold text-brand-600 hover:underline">
                          View App
                        </span>
                      </p>
                    </div>
                  </Link>
                );
              })
            )}
          </div>
        </div>
      </div>

      {/* 7. Bottom Row: Analytics & Top Companies Grid */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        {/* Analytics Panel */}
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
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
              <p className="text-xs text-slate-400 mb-2">
                Applications progressing past review / applied (excluding in-review)
              </p>
              <div className="w-full bg-slate-100 rounded-full h-2.5">
                <div
                  className="bg-brand-600 h-2.5 rounded-full transition-all duration-500"
                  style={{ width: `${insightsFailed ? 0 : insights?.responseRate ?? 0}%` }}
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
              <p className="text-xs text-slate-400 mb-2">
                Percentage of applications that reach interview stage
              </p>
              <div className="w-full bg-slate-100 rounded-full h-2.5">
                <div
                  className="bg-cyan-500 h-2.5 rounded-full transition-all duration-500"
                  style={{
                    width: `${insightsFailed ? 0 : insights?.interviewConversionRate ?? 0}%`,
                  }}
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
              <p className="text-xs text-slate-400 mb-2">
                Percentage of applications resulting in offers
              </p>
              <div className="w-full bg-slate-100 rounded-full h-2.5">
                <div
                  className="bg-emerald-500 h-2.5 rounded-full transition-all duration-500"
                  style={{
                    width: `${insightsFailed ? 0 : insights?.offerConversionRate ?? 0}%`,
                  }}
                />
              </div>
            </div>
          </div>
        </div>

        {/* Top Companies */}
        <div className="rounded-xl border border-slate-200 bg-white p-6 flex flex-col h-[320px] shadow-sm">
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
                      <span className="text-xs text-slate-500 font-semibold">
                        {company.count} app{company.count > 1 ? 's' : ''}
                      </span>
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
