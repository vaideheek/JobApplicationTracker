import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import { ArrowUpRight, ArrowDownRight, Minus, Info } from 'lucide-react';

export interface StatComparison {
  diff: number;
  prevValue?: number;
  subtext?: string;
  neutral?: boolean;
}

interface StatCardProps {
  label: string;
  value: number | string;
  icon: LucideIcon;
  color: string;
  to?: string;
  comparison?: StatComparison | null;
  tooltip?: string;
  subtext?: string;
}

export default function StatCard({
  label,
  value,
  icon: Icon,
  color,
  to,
  comparison,
  tooltip,
  subtext,
}: StatCardProps) {
  const content = (
    <div className={`rounded-xl border border-slate-200 bg-white p-5 transition-all ${
      to ? 'hover:border-brand-400 hover:shadow-md cursor-pointer' : 'shadow-sm'
    }`}>
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 truncate">{label}</p>
            {tooltip && (
              <span title={tooltip} className="text-slate-400 hover:text-slate-600 cursor-help">
                <Info size={13} />
              </span>
            )}
          </div>
          <p className="mt-1.5 text-2xl font-bold text-slate-900">{value}</p>

          {/* Comparison indicator */}
          {comparison && (
            <div className="mt-2 flex items-center gap-1.5 text-xs">
              {comparison.neutral ? (
                comparison.diff > 0 ? (
                  <span className="inline-flex items-center text-slate-600 font-semibold">
                    <ArrowUpRight size={14} className="stroke-[2.5]" />
                    +{comparison.diff}
                  </span>
                ) : comparison.diff < 0 ? (
                  <span className="inline-flex items-center text-slate-600 font-semibold">
                    <ArrowDownRight size={14} className="stroke-[2.5]" />
                    {comparison.diff}
                  </span>
                ) : (
                  <span className="inline-flex items-center text-slate-400 font-medium">
                    <Minus size={14} />
                    0
                  </span>
                )
              ) : comparison.diff > 0 ? (
                <span className="inline-flex items-center text-emerald-600 font-semibold">
                  <ArrowUpRight size={14} className="stroke-[2.5]" />
                  +{comparison.diff}
                </span>
              ) : comparison.diff < 0 ? (
                <span className="inline-flex items-center text-rose-600 font-semibold">
                  <ArrowDownRight size={14} className="stroke-[2.5]" />
                  {comparison.diff}
                </span>
              ) : (
                <span className="inline-flex items-center text-slate-400 font-medium">
                  <Minus size={14} />
                  0
                </span>
              )}
              <span className="text-slate-400 text-[11px]">
                {comparison.subtext || (comparison.prevValue !== undefined ? `(was ${comparison.prevValue})` : 'vs prev')}
              </span>
            </div>
          )}

          {/* Subtext */}
          {subtext && (
            <p className="mt-1 text-[11px] text-slate-400">{subtext}</p>
          )}
        </div>

        <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-lg ${color}`}>
          <Icon size={22} />
        </div>
      </div>
    </div>
  );

  if (to) {
    return (
      <Link to={to} className="block group text-inherit no-underline">
        {content}
      </Link>
    );
  }

  return content;
}
