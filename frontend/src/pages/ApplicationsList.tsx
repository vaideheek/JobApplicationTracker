import { useEffect, useState, useCallback, useRef } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  Plus,
  Eye,
  Edit,
  Trash2,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  ChevronDown,
  ChevronsUpDown,
  X,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import SearchFilter from '../components/SearchFilter';
import StatusBadge from '../components/StatusBadge';
import DeleteModal from '../components/DeleteModal';
import { jobApplicationApi } from '../api/jobApplicationApi';
import { formatDateSafe } from '../utils/dateUtils';
import { STATUS_LABELS } from '../types';
import type { JobApplication, PageResponse } from '../types';

export default function ApplicationsList() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [data, setData] = useState<PageResponse<JobApplication> | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleteTarget, setDeleteTarget] = useState<JobApplication | null>(null);
  const requestIdRef = useRef(0);
  const { user } = useAuth();
  const isDemo = user?.demoAccount || false;

  // Extract query parameters with sensible defaults
  const search = searchParams.get('search') || '';
  const statusFilter = searchParams.get('status') || '';
  const priorityFilter = searchParams.get('priority') || '';
  const dateFrom = searchParams.get('dateFrom') || '';
  const dateTo = searchParams.get('dateTo') || '';
  const documentState = searchParams.get('documentState') || '';
  const sortBy = searchParams.get('sortBy') || 'lastUpdatedAt';
  const sortDir = searchParams.get('sortDir') || 'desc';
  const page = Math.max(0, parseInt(searchParams.get('page') || '0', 10));
  const size = parseInt(searchParams.get('size') || '15', 10);

  // Local state for debounced search input
  const [localSearch, setLocalSearch] = useState(search);

  // Synchronize local search input with URL search state (e.g. browser Back/Forward navigation)
  useEffect(() => {
    setLocalSearch(search);
  }, [search]);

  // Debounced search trigger: localSearch -> searchParams
  useEffect(() => {
    const handler = setTimeout(() => {
      if (localSearch !== search) {
        setSearchParams((prev) => {
          const next = new URLSearchParams(prev);
          if (localSearch) next.set('search', localSearch);
          else next.delete('search');
          next.set('page', '0');
          return next;
        });
      }
    }, 350);

    return () => clearTimeout(handler);
  }, [localSearch, search, setSearchParams]);

  const fetchApplications = useCallback(async () => {
    const currentRequestId = ++requestIdRef.current;

    // If date range is invalid (From > To), do not execute invalid query
    if (dateFrom && dateTo && dateFrom > dateTo) {
      setData(null);
      setLoading(false);
      return;
    }

    setLoading(true);

    try {
      const result = await jobApplicationApi.getAll({
        search: search || undefined,
        status: statusFilter || undefined,
        priority: priorityFilter || undefined,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        documentState: documentState || undefined,
        sortBy,
        sortDir,
        page,
        size,
      });

      // Discard stale responses if a newer request was dispatched
      if (currentRequestId !== requestIdRef.current) {
        return;
      }

      // Handle invalid page out-of-bounds correction
      if (result.totalPages === 0 && page > 0) {
        setData(result);
        setSearchParams((prev) => {
          const next = new URLSearchParams(prev);
          next.set('page', '0');
          return next;
        });
        return;
      }

      if (result.totalPages > 0 && page >= result.totalPages) {
        setData(result);
        setSearchParams((prev) => {
          const next = new URLSearchParams(prev);
          next.set('page', String(result.totalPages - 1));
          return next;
        });
        return;
      }

      setData(result);
    } catch (err) {
      if (currentRequestId === requestIdRef.current) {
        console.error('Failed to fetch applications:', err);
      }
    } finally {
      if (currentRequestId === requestIdRef.current) {
        setLoading(false);
      }
    }
  }, [search, statusFilter, priorityFilter, dateFrom, dateTo, documentState, sortBy, sortDir, page, size, setSearchParams]);

  useEffect(() => {
    fetchApplications();
  }, [fetchApplications]);

  // Invalidate any in-flight requests when component unmounts
  useEffect(() => {
    return () => {
      requestIdRef.current++;
    };
  }, []);

  // Helper to set individual query parameters
  const updateQueryParam = (key: string, value: string | null, resetPage = true) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (value) next.set(key, value);
      else next.delete(key);
      if (resetPage) next.set('page', '0');
      return next;
    });
  };

  const handleClearFilters = () => {
    setLocalSearch('');
    setSearchParams((prev) => {
      const next = new URLSearchParams();
      const currentSortBy = prev.get('sortBy');
      const currentSortDir = prev.get('sortDir');
      const currentSize = prev.get('size');
      if (currentSortBy) next.set('sortBy', currentSortBy);
      if (currentSortDir) next.set('sortDir', currentSortDir);
      if (currentSize) next.set('size', currentSize);
      next.set('page', '0');
      return next;
    });
  };

  const handleHeaderSort = (field: string) => {
    let nextDir = 'desc';
    if (sortBy === field) {
      nextDir = sortDir === 'asc' ? 'desc' : 'asc';
    } else {
      nextDir = field === 'companyName' ? 'asc' : 'desc';
    }
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('sortBy', field);
      next.set('sortDir', nextDir);
      next.set('page', '0');
      return next;
    });
  };

  const renderSortIcon = (field: string) => {
    if (sortBy !== field) {
      return <ChevronsUpDown size={14} className="ml-1 inline-block text-slate-400" />;
    }
    return sortDir === 'asc' ? (
      <ChevronUp size={14} className="ml-1 inline-block text-brand-600 font-bold" />
    ) : (
      <ChevronDown size={14} className="ml-1 inline-block text-brand-600 font-bold" />
    );
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

  // Compact pagination calculation
  const getPages = () => {
    if (!data) return [];
    const total = data.totalPages;
    const current = page;
    const pages: (number | string)[] = [];

    if (total <= 7) {
      for (let i = 0; i < total; i++) {
        pages.push(i);
      }
    } else {
      pages.push(0);
      if (current > 2) {
        pages.push('ellipsis-start');
      }

      const start = Math.max(1, current - 1);
      const end = Math.min(total - 2, current + 1);

      for (let i = start; i <= end; i++) {
        pages.push(i);
      }

      if (current < total - 3) {
        pages.push('ellipsis-end');
      }
      pages.push(total - 1);
    }
    return pages;
  };

  const showingStart = data && data.totalElements > 0 ? page * size + 1 : 0;
  const showingEnd = data ? Math.min((page + 1) * size, data.totalElements) : 0;

  return (
    <div>
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Applications</h1>
          <p className="mt-1 text-sm text-slate-500">
            {data ? `Showing ${showingStart}-${showingEnd} of ${data.totalElements} applications` : 'Loading...'}
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

      {/* Filters Panel */}
      <div className="mb-6">
        <SearchFilter
          search={localSearch}
          statusFilter={statusFilter}
          priorityFilter={priorityFilter}
          dateFrom={dateFrom}
          dateTo={dateTo}
          documentState={documentState}
          sortBy={sortBy}
          sortDir={sortDir}
          size={size}
          onSearchChange={setLocalSearch}
          onStatusChange={(val) => updateQueryParam('status', val)}
          onPriorityChange={(val) => updateQueryParam('priority', val)}
          onDateFromChange={(val) => updateQueryParam('dateFrom', val)}
          onDateToChange={(val) => updateQueryParam('dateTo', val)}
          onDocumentStateChange={(val) => updateQueryParam('documentState', val)}
          onSortByChange={(val) => updateQueryParam('sortBy', val)}
          onSortDirChange={(val) => updateQueryParam('sortDir', val)}
          onSizeChange={(val) => updateQueryParam('size', String(val))}
          onClearFilters={handleClearFilters}
        />
      </div>

      {/* Active Filter Chips */}
      {Boolean(search || statusFilter || priorityFilter || dateFrom || dateTo || documentState) && (
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <span className="text-xs font-medium text-slate-500">Active filters:</span>
          {search && (
            <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-700">
              Search: "{search}"
              <button
                type="button"
                onClick={() => {
                  setLocalSearch('');
                  updateQueryParam('search', null);
                }}
                className="ml-0.5 text-slate-400 hover:text-slate-600"
                title="Remove search filter"
              >
                <X size={12} />
              </button>
            </span>
          )}
          {statusFilter && (
            <span className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700 border border-blue-100">
              Status: {STATUS_LABELS[statusFilter as keyof typeof STATUS_LABELS] || statusFilter}
              <button
                type="button"
                onClick={() => updateQueryParam('status', null)}
                className="ml-0.5 text-blue-400 hover:text-blue-600"
                title="Remove status filter"
              >
                <X size={12} />
              </button>
            </span>
          )}
          {priorityFilter && (
            <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-1 text-xs font-medium text-amber-700 border border-amber-100">
              Priority: {priorityFilter.charAt(0) + priorityFilter.slice(1).toLowerCase()}
              <button
                type="button"
                onClick={() => updateQueryParam('priority', null)}
                className="ml-0.5 text-amber-400 hover:text-amber-600"
                title="Remove priority filter"
              >
                <X size={12} />
              </button>
            </span>
          )}
          {(dateFrom || dateTo) && (
            <span className="inline-flex items-center gap-1 rounded-full bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-700 border border-brand-200">
              Applied: {dateFrom && dateTo ? `${dateFrom} to ${dateTo}` : dateFrom ? `From ${dateFrom}` : `To ${dateTo}`}
              <button
                type="button"
                onClick={() => {
                  setSearchParams((prev) => {
                    const next = new URLSearchParams(prev);
                    next.delete('dateFrom');
                    next.delete('dateTo');
                    next.set('page', '0');
                    return next;
                  });
                }}
                className="ml-0.5 text-brand-400 hover:text-brand-600"
                title="Remove date filter"
              >
                <X size={12} />
              </button>
            </span>
          )}
          {documentState && (
            <span className="inline-flex items-center gap-1 rounded-full bg-purple-50 px-2.5 py-1 text-xs font-medium text-purple-700 border border-purple-100">
              Docs: {documentState === 'HAS_DOCUMENTS' ? 'Has Documents' : 'No Documents'}
              <button
                type="button"
                onClick={() => updateQueryParam('documentState', null)}
                className="ml-0.5 text-purple-400 hover:text-purple-600"
                title="Remove document filter"
              >
                <X size={12} />
              </button>
            </span>
          )}
          <button
            type="button"
            onClick={handleClearFilters}
            className="text-xs font-medium text-slate-500 hover:text-brand-600 underline ml-1"
          >
            Clear all
          </button>
        </div>
      )}

      {/* Applications Table */}
      <div className="rounded-xl border border-slate-200 bg-white">
        {loading ? (
          <div className="flex h-48 items-center justify-center">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-brand-600 border-t-transparent" />
          </div>
        ) : !data || data.content.length === 0 ? (
          <div className="px-6 py-16 text-center">
            {dateFrom && dateTo && dateFrom > dateTo ? (
              <div className="space-y-2">
                <p className="text-base font-semibold text-slate-800">Invalid Date Range</p>
                <p className="text-sm text-slate-500">"Applied From" cannot be later than "Applied To". Please adjust your dates.</p>
              </div>
            ) : (search || statusFilter || priorityFilter || dateFrom || dateTo || documentState) ? (
              <div className="space-y-3">
                <p className="text-base font-semibold text-slate-800">No matching applications</p>
                <p className="text-sm text-slate-500">No applications matched your active filters. Try adjusting or clearing them.</p>
                <button
                  type="button"
                  onClick={handleClearFilters}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50 shadow-sm"
                >
                  Clear all filters
                </button>
              </div>
            ) : (
              <div className="space-y-2">
                <p className="text-base font-semibold text-slate-800">No applications found</p>
                <p className="text-sm text-slate-500">Get started by creating your first application.</p>
              </div>
            )}
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-slate-100 text-left text-xs font-medium uppercase tracking-wider text-slate-500">
                    <th
                      onClick={() => handleHeaderSort('companyName')}
                      className="cursor-pointer select-none px-6 py-3 hover:bg-slate-50 hover:text-slate-700"
                    >
                      Company {renderSortIcon('companyName')}
                    </th>
                    <th className="px-6 py-3">Role</th>
                    <th className="px-6 py-3">Location</th>
                    <th className="px-6 py-3">Status</th>
                    <th
                      onClick={() => handleHeaderSort('dateApplied')}
                      className="cursor-pointer select-none px-6 py-3 hover:bg-slate-50 hover:text-slate-700"
                    >
                      Applied {renderSortIcon('dateApplied')}
                    </th>
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
                        {formatDateSafe(app.dateApplied)}
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

            {/* Pagination Controls */}
            <div className="flex flex-col items-center justify-between gap-4 border-t border-slate-100 px-6 py-4 sm:flex-row">
              <p className="text-sm text-slate-500">
                Showing {showingStart}-{showingEnd} of {data.totalElements} applications
              </p>
              <div className="flex flex-wrap items-center gap-1.5">
                {/* First */}
                <button
                  type="button"
                  onClick={() => updateQueryParam('page', '0', false)}
                  disabled={data.first}
                  className="inline-flex items-center rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:hover:bg-white"
                >
                  First
                </button>

                {/* Prev */}
                <button
                  type="button"
                  onClick={() => updateQueryParam('page', String(page - 1), false)}
                  disabled={data.first}
                  className="inline-flex items-center gap-1 rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:hover:bg-white"
                >
                  <ChevronLeft size={14} /> Prev
                </button>

                {/* Page Numbers */}
                {getPages().map((p, idx) => {
                  if (p === 'ellipsis-start' || p === 'ellipsis-end') {
                    return (
                      <span key={`ellipsis-${idx}`} className="px-2 text-slate-400 text-sm">
                        ...
                      </span>
                    );
                  }
                  const isCurrent = p === page;
                  return (
                    <button
                      key={`page-${p}`}
                      type="button"
                      onClick={() => updateQueryParam('page', String(p), false)}
                      className={`inline-flex h-8 w-8 items-center justify-center rounded-lg text-xs font-medium border ${
                        isCurrent
                          ? 'border-brand-600 bg-brand-600 text-white font-bold'
                          : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                      }`}
                    >
                      {Number(p) + 1}
                    </button>
                  );
                })}

                {/* Next */}
                <button
                  type="button"
                  onClick={() => updateQueryParam('page', String(page + 1), false)}
                  disabled={data.last}
                  className="inline-flex items-center gap-1 rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:hover:bg-white"
                >
                  Next <ChevronRight size={14} />
                </button>

                {/* Last */}
                <button
                  type="button"
                  onClick={() => updateQueryParam('page', String(data.totalPages - 1), false)}
                  disabled={data.last}
                  className="inline-flex items-center rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:hover:bg-white"
                >
                  Last
                </button>
              </div>
            </div>
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
