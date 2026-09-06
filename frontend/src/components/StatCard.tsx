import { Link, useNavigate } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import { ArrowUpRight, ArrowDownRight, Minus } from 'lucide-react';
import InfoTooltip from './InfoTooltip';

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
  const navigate = useNavigate();

  const handleCardClick = (e: React.MouseEvent) => {
    // If to is defined and not middle/cmd click, navigate
    if (to && e.button === 0 && !e.metaKey && !e.ctrlKey) {
      navigate(to);
    }
  };

  return (
    <div
      onClick={handleCardClick}
      className={`relative rounded-xl border border-slate-200 bg-white p-5 transition-all ${
        to
          ? 'hover:border-brand-400 hover:shadow-md cursor-pointer'
          : 'shadow-sm'
      }`}
    >
      {/* Stretched link for keyboard navigation, screen readers, and Cmd/middle-click without invalid nesting */}
      {to && (
        <Link
          to={to}
          className="absolute inset-0 z-0 rounded-xl focus:outline-none focus:ring-2 focus:ring-brand-500"
          aria-label={`View ${label}`}
        />
      )}

      <div className="relative z-10 flex items-start justify-between gap-2 pointer-events-auto">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <p
              title={label}
              className="text-xs font-semibold uppercase tracking-wider text-slate-500 truncate cursor-inherit"
            >
              {label}
            </p>
            {tooltip && (
              <div
                className="relative z-20"
                onClick={(e) => e.stopPropagation()}
              >
                <InfoTooltip title={label} content={tooltip} />
              </div>
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
}
