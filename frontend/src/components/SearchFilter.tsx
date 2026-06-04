import { Search } from 'lucide-react';
import { STATUS_OPTIONS, STATUS_LABELS } from '../types';
import type { ApplicationStatus } from '../types';

interface SearchFilterProps {
  search: string;
  statusFilter: ApplicationStatus | '';
  onSearchChange: (value: string) => void;
  onStatusChange: (value: ApplicationStatus | '') => void;
}

export default function SearchFilter({
  search,
  statusFilter,
  onSearchChange,
  onStatusChange,
}: SearchFilterProps) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row">
      {/* Search */}
      <div className="relative flex-1">
        <Search size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          id="search-input"
          type="text"
          placeholder="Search by company or role..."
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          className="w-full rounded-lg border border-slate-300 bg-white py-2.5 pl-10 pr-4 text-sm text-slate-900 placeholder-slate-400 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
        />
      </div>

      {/* Status filter */}
      <select
        id="status-filter"
        value={statusFilter}
        onChange={(e) => onStatusChange(e.target.value as ApplicationStatus | '')}
        className="rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-700 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
      >
        <option value="">All Statuses</option>
        {STATUS_OPTIONS.map((s) => (
          <option key={s} value={s}>
            {STATUS_LABELS[s]}
          </option>
        ))}
      </select>
    </div>
  );
}
