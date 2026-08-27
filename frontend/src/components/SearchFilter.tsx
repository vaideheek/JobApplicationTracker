import { useState } from 'react';
import { Search, Filter, X } from 'lucide-react';
import { STATUS_OPTIONS, STATUS_LABELS, PRIORITY_OPTIONS } from '../types';

interface SearchFilterProps {
  search: string;
  statusFilter: string;
  priorityFilter: string;
  dateFrom: string;
  dateTo: string;
  documentState: string;
  sortBy: string;
  sortDir: string;
  size: number;
  onSearchChange: (value: string) => void;
  onStatusChange: (value: string) => void;
  onPriorityChange: (value: string) => void;
  onDateFromChange: (value: string) => void;
  onDateToChange: (value: string) => void;
  onDocumentStateChange: (value: string) => void;
  onSortByChange: (value: string) => void;
  onSortDirChange: (value: string) => void;
  onSizeChange: (value: number) => void;
  onClearFilters: () => void;
}

export default function SearchFilter({
  search,
  statusFilter,
  priorityFilter,
  dateFrom,
  dateTo,
  documentState,
  sortBy,
  sortDir,
  size,
  onSearchChange,
  onStatusChange,
  onPriorityChange,
  onDateFromChange,
  onDateToChange,
  onDocumentStateChange,
  onSortByChange,
  onSortDirChange,
  onSizeChange,
  onClearFilters,
}: SearchFilterProps) {
  const [isOpen, setIsOpen] = useState(false);

  const hasActiveFilters =
    statusFilter ||
    priorityFilter ||
    dateFrom ||
    dateTo ||
    documentState ||
    sortBy !== 'lastUpdatedAt' ||
    sortDir !== 'desc' ||
    size !== 15;

  return (
    <div className="flex flex-col gap-3">
      {/* Search & Main Controls */}
      <div className="flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            id="search-input"
            type="text"
            placeholder="Search by company, role, location, or source..."
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white py-2.5 pl-10 pr-4 text-sm text-slate-900 placeholder-slate-400 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
          />
        </div>

        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => setIsOpen(!isOpen)}
            className={`inline-flex items-center gap-2 rounded-lg border px-4 py-2.5 text-sm font-medium transition-colors focus:outline-none focus:ring-1 focus:ring-brand-500 ${
              isOpen || hasActiveFilters
                ? 'border-brand-500 bg-brand-50 text-brand-700'
                : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
            }`}
          >
            <Filter size={18} />
            Filters {hasActiveFilters && <span className="h-2 w-2 rounded-full bg-brand-600" />}
          </button>

          {hasActiveFilters && (
            <button
              type="button"
              onClick={onClearFilters}
              className="inline-flex items-center gap-1 rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50 focus:outline-none focus:ring-1 focus:ring-brand-500"
            >
              Clear
            </button>
          )}
        </div>
      </div>

      {/* Advanced Filters Panel */}
      {isOpen && (
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {/* Status */}
            <div className="flex flex-col gap-1.5">
              <label htmlFor="filter-status" className="text-xs font-semibold text-slate-600">
                Status
              </label>
              <select
                id="filter-status"
                value={statusFilter}
                onChange={(e) => onStatusChange(e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              >
                <option value="">All Statuses</option>
                {STATUS_OPTIONS.map((s) => (
                  <option key={s} value={s}>
                    {STATUS_LABELS[s]}
                  </option>
                ))}
              </select>
            </div>

            {/* Priority */}
            <div className="flex flex-col gap-1.5">
              <label htmlFor="filter-priority" className="text-xs font-semibold text-slate-600">
                Priority
              </label>
              <select
                id="filter-priority"
                value={priorityFilter}
                onChange={(e) => onPriorityChange(e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              >
                <option value="">All Priorities</option>
                {PRIORITY_OPTIONS.map((p) => (
                  <option key={p} value={p}>
                    {p.charAt(0) + p.slice(1).toLowerCase()}
                  </option>
                ))}
              </select>
            </div>

            {/* Document State */}
            <div className="flex flex-col gap-1.5">
              <label htmlFor="filter-docs" className="text-xs font-semibold text-slate-600">
                Documents
              </label>
              <select
                id="filter-docs"
                value={documentState}
                onChange={(e) => onDocumentStateChange(e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              >
                <option value="">All Applications</option>
                <option value="HAS_DOCUMENTS">Has Documents</option>
                <option value="NO_DOCUMENTS">No Documents</option>
              </select>
            </div>

            {/* Page Size */}
            <div className="flex flex-col gap-1.5">
              <label htmlFor="filter-size" className="text-xs font-semibold text-slate-600">
                Page Size
              </label>
              <select
                id="filter-size"
                value={size}
                onChange={(e) => onSizeChange(Number(e.target.value))}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              >
                <option value={15}>15 per page</option>
                <option value={25}>25 per page</option>
                <option value={50}>50 per page</option>
                <option value={100}>100 per page</option>
              </select>
            </div>

            {/* Date From */}
            <div className="flex flex-col gap-1.5">
              <label htmlFor="filter-date-from" className="text-xs font-semibold text-slate-600">
                Applied Date From
              </label>
              <input
                id="filter-date-from"
                type="date"
                value={dateFrom}
                onChange={(e) => onDateFromChange(e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              />
            </div>

            {/* Date To */}
            <div className="flex flex-col gap-1.5">
              <label htmlFor="filter-date-to" className="text-xs font-semibold text-slate-600">
                Applied Date To
              </label>
              <input
                id="filter-date-to"
                type="date"
                value={dateTo}
                onChange={(e) => onDateToChange(e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              />
            </div>

            {/* Sort By */}
            <div className="flex flex-col gap-1.5">
              <label htmlFor="filter-sort-by" className="text-xs font-semibold text-slate-600">
                Sort By
              </label>
              <select
                id="filter-sort-by"
                value={sortBy}
                onChange={(e) => onSortByChange(e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              >
                <option value="lastUpdatedAt">Last Updated</option>
                <option value="dateApplied">Date Applied</option>
                <option value="createdAt">Date Created</option>
                <option value="companyName">Company Name</option>
              </select>
            </div>

            {/* Sort Direction */}
            <div className="flex flex-col gap-1.5">
              <label htmlFor="filter-sort-dir" className="text-xs font-semibold text-slate-600">
                Direction
              </label>
              <select
                id="filter-sort-dir"
                value={sortDir}
                onChange={(e) => onSortDirChange(e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              >
                <option value="desc">Descending</option>
                <option value="asc">Ascending</option>
              </select>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
