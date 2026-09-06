import React, { useState, useEffect } from 'react';
import { Calendar, AlertCircle } from 'lucide-react';
import { format, parseISO } from 'date-fns';
import type { PeriodPreset } from '../types';

interface PeriodSelectorProps {
  range: PeriodPreset;
  from?: string;
  to?: string;
  compare: boolean;
  startDate?: string;
  endDate?: string;
  previousStartDate?: string | null;
  previousEndDate?: string | null;
  comparisonAvailable?: boolean;
  onChange: (params: { range: PeriodPreset; from?: string; to?: string; compare: boolean }) => void;
}

const PRESETS: { value: PeriodPreset; label: string }[] = [
  { value: 'THIS_WEEK', label: 'This Week' },
  { value: 'LAST_WEEK', label: 'Last Week' },
  { value: 'THIS_MONTH', label: 'This Month' },
  { value: 'LAST_MONTH', label: 'Last Month' },
  { value: 'LAST_30_DAYS', label: 'Last 30 Days' },
  { value: 'LAST_90_DAYS', label: 'Last 90 Days' },
  { value: 'YTD', label: 'Year to Date' },
  { value: 'ALL_TIME', label: 'All Time' },
  { value: 'CUSTOM', label: 'Custom' },
];

function formatDateDisplay(dateStr?: string | null): string {
  if (!dateStr) return '';
  try {
    return format(parseISO(dateStr), 'MMM d, yyyy');
  } catch {
    return dateStr;
  }
}

export default function PeriodSelector({
  range,
  from,
  to,
  compare,
  startDate,
  endDate,
  previousStartDate,
  previousEndDate,
  comparisonAvailable,
  onChange,
}: PeriodSelectorProps) {
  const [customFrom, setCustomFrom] = useState(from || startDate || '');
  const [customTo, setCustomTo] = useState(to || endDate || '');
  const [customError, setCustomError] = useState<string | null>(null);

  useEffect(() => {
    if (from) setCustomFrom(from);
    else if (startDate) setCustomFrom(startDate);

    if (to) setCustomTo(to);
    else if (endDate) setCustomTo(endDate);
  }, [from, to, startDate, endDate]);

  const handleRangeChange = (newRange: PeriodPreset) => {
    if (newRange === 'CUSTOM') {
      const initialFrom = customFrom || startDate || '';
      const initialTo = customTo || endDate || '';
      if (initialFrom && initialTo && initialFrom <= initialTo) {
        onChange({ range: 'CUSTOM', from: initialFrom, to: initialTo, compare });
      } else {
        onChange({ range: 'CUSTOM', from: undefined, to: undefined, compare });
      }
    } else {
      setCustomError(null);
      onChange({ range: newRange, compare });
    }
  };

  const handleCustomApply = (e: React.FormEvent) => {
    e.preventDefault();
    if (!customFrom || !customTo) {
      setCustomError('Please provide both Start and End dates.');
      return;
    }
    if (customFrom > customTo) {
      setCustomError('Start date cannot be after end date.');
      return;
    }
    setCustomError(null);
    onChange({ range: 'CUSTOM', from: customFrom, to: customTo, compare });
  };

  const handleCompareChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const nextCompare = e.target.checked;
    onChange({
      range,
      from: range === 'CUSTOM' ? customFrom : undefined,
      to: range === 'CUSTOM' ? customTo : undefined,
      compare: nextCompare,
    });
  };

  const isAllTime = range === 'ALL_TIME';

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm space-y-3">
      {/* Top Bar: Presets dropdown/buttons & Compare toggle */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-500 mr-1.5 flex items-center gap-1">
            <Calendar size={14} className="text-slate-400" />
            Period:
          </span>
          <div className="flex flex-wrap gap-1">
            {PRESETS.map((preset) => {
              const isActive = range === preset.value;
              return (
                <button
                  key={preset.value}
                  type="button"
                  onClick={() => handleRangeChange(preset.value)}
                  className={`px-3 py-1 text-xs font-medium rounded-lg transition-colors ${
                    isActive
                      ? 'bg-brand-600 text-white shadow-sm'
                      : 'bg-slate-50 text-slate-700 hover:bg-slate-100 hover:text-slate-900 border border-slate-200'
                  }`}
                >
                  {preset.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* Comparison Toggle */}
        <div className="flex items-center gap-2">
          <label
            className={`inline-flex items-center gap-2 text-xs font-medium select-none ${
              isAllTime ? 'opacity-40 cursor-not-allowed text-slate-400' : 'cursor-pointer text-slate-700 hover:text-slate-900'
            }`}
          >
            <input
              type="checkbox"
              checked={compare && !isAllTime}
              disabled={isAllTime}
              onChange={handleCompareChange}
              className="h-3.5 w-3.5 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
            />
            <span>Compare with previous period</span>
          </label>
        </div>
      </div>

      {/* Custom Range Selector Form */}
      {range === 'CUSTOM' && (
        <form onSubmit={handleCustomApply} className="flex flex-wrap items-center gap-3 pt-2 border-t border-slate-100">
          <div className="flex items-center gap-2">
            <label htmlFor="custom-from-date" className="text-xs font-medium text-slate-600">From:</label>
            <input
              id="custom-from-date"
              type="date"
              value={customFrom}
              onChange={(e) => {
                setCustomFrom(e.target.value);
                setCustomError(null);
              }}
              className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs text-slate-800 focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
            />
          </div>
          <div className="flex items-center gap-2">
            <label htmlFor="custom-to-date" className="text-xs font-medium text-slate-600">To:</label>
            <input
              id="custom-to-date"
              type="date"
              value={customTo}
              onChange={(e) => {
                setCustomTo(e.target.value);
                setCustomError(null);
              }}
              className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs text-slate-800 focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
            />
          </div>
          <button
            type="submit"
            className="rounded-lg bg-brand-600 px-3 py-1 text-xs font-medium text-white hover:bg-brand-700 transition-colors"
          >
            Apply Range
          </button>
          {customError && (
            <div className="flex items-center gap-1 text-xs text-red-600 font-medium">
              <AlertCircle size={14} />
              <span>{customError}</span>
            </div>
          )}
        </form>
      )}

      {/* Date Interval & Comparison Range Display */}
      {startDate && endDate && (
        <div className="flex flex-wrap items-center gap-2 pt-1 text-xs text-slate-500">
          <span className="font-medium text-slate-700">
            {formatDateDisplay(startDate)} – {formatDateDisplay(endDate)}
          </span>
          {compare && comparisonAvailable && previousStartDate && previousEndDate && (
            <>
              <span className="text-slate-400">vs</span>
              <span className="text-slate-500">
                {formatDateDisplay(previousStartDate)} – {formatDateDisplay(previousEndDate)}
              </span>
            </>
          )}
          {compare && !comparisonAvailable && !isAllTime && (
            <span className="text-slate-400 italic">(previous period comparison unavailable)</span>
          )}
          {isAllTime && (
            <span className="text-slate-400 italic">(All Time covers complete history)</span>
          )}
        </div>
      )}
    </div>
  );
}
