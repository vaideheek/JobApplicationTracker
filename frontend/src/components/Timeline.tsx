import { format } from 'date-fns';
import { STATUS_LABELS } from '../types';
import type { StatusHistoryEntry } from '../types';

interface TimelineProps {
  entries: StatusHistoryEntry[];
}

export default function Timeline({ entries }: TimelineProps) {
  if (!entries || entries.length === 0) {
    return (
      <p className="text-sm text-slate-400 italic">No status changes recorded yet.</p>
    );
  }

  return (
    <div className="relative space-y-0">
      {/* Vertical line */}
      <div className="absolute left-3 top-2 bottom-2 w-px bg-slate-200" />

      {entries.map((entry, index) => (
        <div key={entry.id} className="relative flex gap-4 pb-6 last:pb-0">
          {/* Dot */}
          <div
            className={`relative z-10 mt-1.5 h-6 w-6 flex-shrink-0 rounded-full border-2 ${
              index === 0
                ? 'border-brand-500 bg-brand-50'
                : 'border-slate-300 bg-white'
            } flex items-center justify-center`}
          >
            <div
              className={`h-2 w-2 rounded-full ${
                index === 0 ? 'bg-brand-500' : 'bg-slate-300'
              }`}
            />
          </div>

          {/* Content */}
          <div className="flex-1 pt-0.5">
            <p className="text-sm font-medium text-slate-900">
              {entry.fromStatus
                ? `${STATUS_LABELS[entry.fromStatus]} → ${STATUS_LABELS[entry.toStatus]}`
                : `Set to ${STATUS_LABELS[entry.toStatus]}`}
            </p>
            {entry.note && (
              <p className="mt-0.5 text-sm text-slate-500">{entry.note}</p>
            )}
            <p className="mt-1 text-xs text-slate-400">
              {format(new Date(entry.changedAt), 'MMM d, yyyy · h:mm a')}
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}
