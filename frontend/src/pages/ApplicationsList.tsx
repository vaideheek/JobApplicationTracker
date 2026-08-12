import { useEffect, useState, useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Plus, Eye, Edit, Trash2, ChevronLeft, ChevronRight } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import SearchFilter from '../components/SearchFilter';
import StatusBadge from '../components/StatusBadge';
import DeleteModal from '../components/DeleteModal';
import { jobApplicationApi } from '../api/jobApplicationApi';
import { format } from 'date-fns';
import type { JobApplication, ApplicationStatus, PageResponse } from '../types';

export default function ApplicationsList() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [data, setData] = useState<PageResponse<JobApplication> | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleteTarget, setDeleteTarget] = useState<JobApplication | null>(null);
  const { user } = useAuth();
  const isDemo = user?.demoAccount || false;

  const search = searchParams.get('search') || '';
  const statusFilter = (searchParams.get('status') || '') as ApplicationStatus | '';
  const page = parseInt(searchParams.get('page') || '0', 10);

  const fetchApplications = useCallback(async () => {
    setLoading(true);
    try {
      const result = await jobApplicationApi.getAll({
        search: search || undefined,
        status: statusFilter || undefined,
        page,
        size: 15,
      });
      setData(result);
    } catch (err) {
      console.error('Failed to fetch applications:', err);
    } finally {
      setLoading(false);
    }
  }, [search, statusFilter, page]);

  useEffect(() => {
    fetchApplications();
  }, [fetchApplications]);

  const handleSearchChange = (value: string) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (value) next.set('search', value);
      else next.delete('search');
      next.set('page', '0');
      return next;
    });
  };

  const handleStatusChange = (value: ApplicationStatus | '') => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (value) next.set('status', value);
      else next.delete('status');
      next.set('page', '0');
      return next;
    });
  };

  const handlePageChange = (newPage: number) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('page', String(newPage));
      return next;
    });
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await jobApplicationApi.delete(deleteTarget.id);
      setDeleteTarget(null);
      fetchApplications();
    } catch (err) {
      console.error('Failed to delete:', err);
    }
  };

  return (
    <div>
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Applications</h1>
          <p className="mt-1 text-sm text-slate-500">
            {data ? `${data.totalElements} total applications` : 'Loading...'}
          </p>
        </div>
        {!isDemo && (
          <Link
            to="/applications/new"
            id="add-application-btn"
            className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-700"
          >
            <Plus size={18} /> Add Application
          </Link>
        )}
      </div>

      {/* Filters */}
      <div className="mb-6">
        <SearchFilter
          search={search}
          statusFilter={statusFilter}
          onSearchChange={handleSearchChange}
          onStatusChange={handleStatusChange}
        />
      </div>

      {/* Table */}
      <div className="rounded-xl border border-slate-200 bg-white">
        {loading ? (
          <div className="flex h-48 items-center justify-center">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-brand-600 border-t-transparent" />
          </div>
        ) : !data || data.content.length === 0 ? (
          <div className="px-6 py-16 text-center">
            <p className="text-slate-500">No applications found.</p>
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-slate-100 text-left text-xs font-medium uppercase tracking-wider text-slate-500">
                    <th className="px-6 py-3">Company</th>
                    <th className="px-6 py-3">Role</th>
                    <th className="px-6 py-3">Location</th>
                    <th className="px-6 py-3">Status</th>
                    <th className="px-6 py-3">Applied</th>
                    <th className="px-6 py-3">Priority</th>
                    <th className="px-6 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data.content.map((app) => (
                    <tr key={app.id} className="hover:bg-slate-50">
                      <td className="px-6 py-4 text-sm font-medium text-slate-900">
                        {app.companyName}
                      </td>
                      <td className="px-6 py-4 text-sm text-slate-600">{app.jobTitle}</td>
                      <td className="px-6 py-4 text-sm text-slate-500">
                        {app.location || '—'}
                      </td>
                      <td className="px-6 py-4">
                        <StatusBadge status={app.status} />
                      </td>
                      <td className="px-6 py-4 text-sm text-slate-500">
                        {app.dateApplied
                          ? format(new Date(app.dateApplied), 'MMM d, yyyy')
                          : '—'}
                      </td>
                      <td className="px-6 py-4 text-sm text-slate-500">
                        {app.priority ? (
                          <span
                            className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
                              app.priority === 'HIGH'
                                ? 'bg-red-50 text-red-700'
                                : app.priority === 'MEDIUM'
                                ? 'bg-amber-50 text-amber-700'
                                : 'bg-slate-50 text-slate-600'
                            }`}
                          >
                            {app.priority.charAt(0) + app.priority.slice(1).toLowerCase()}
                          </span>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td className="px-6 py-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Link
                            to={`/applications/${app.id}`}
                            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                            title="View"
                          >
                            <Eye size={16} />
                          </Link>
                          {!isDemo && (
                            <>
                              <Link
                                to={`/applications/${app.id}/edit`}
                                className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                                title="Edit"
                              >
                                <Edit size={16} />
                              </Link>
                              <button
                                onClick={() => setDeleteTarget(app)}
                                className="rounded-lg p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-600"
                                title="Delete"
                              >
                                <Trash2 size={16} />
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {data.totalPages > 1 && (
              <div className="flex items-center justify-between border-t border-slate-100 px-6 py-3">
                <p className="text-sm text-slate-500">
                  Page {data.number + 1} of {data.totalPages}
                </p>
                <div className="flex gap-2">
                  <button
                    onClick={() => handlePageChange(page - 1)}
                    disabled={data.first}
                    className="inline-flex items-center gap-1 rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50 disabled:opacity-40"
                  >
                    <ChevronLeft size={16} /> Prev
                  </button>
                  <button
                    onClick={() => handlePageChange(page + 1)}
                    disabled={data.last}
                    className="inline-flex items-center gap-1 rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50 disabled:opacity-40"
                  >
                    Next <ChevronRight size={16} />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* Delete Modal */}
      {deleteTarget && (
        <DeleteModal
          isOpen={!!deleteTarget}
          companyName={deleteTarget.companyName}
          jobTitle={deleteTarget.jobTitle}
          onConfirm={handleDelete}
          onCancel={() => setDeleteTarget(null)}
        />
      )}
    </div>
  );
}
