import React, { useState } from 'react';
import { BarChart2, Info } from 'lucide-react';
import InfoTooltip from './InfoTooltip';
import type { VolumeBucket } from '../types';

interface ApplicationsVolumeChartProps {
  volumeBuckets: VolumeBucket[];
  unknownDateApplications?: number;
}

export default function ApplicationsVolumeChart({
  volumeBuckets,
  unknownDateApplications = 0,
}: ApplicationsVolumeChartProps) {
  const [hoveredBucket, setHoveredBucket] = useState<VolumeBucket | null>(null);

  const totalBucketApplications = volumeBuckets.reduce((sum, b) => sum + (b.applicationsSubmitted || 0), 0);
  const maxCount = Math.max(...volumeBuckets.map((b) => b.applicationsSubmitted || 0), 0);
  // Y-axis upper bound (at least 5 for nice scaling)
  const yUpper = maxCount > 0 ? (maxCount <= 4 ? 4 : Math.ceil(maxCount * 1.15)) : 5;

  // Chart dimensions in SVG coordinates
  const svgWidth = 800;
  const svgHeight = 180;
  const paddingLeft = 40;
  const paddingRight = 20;
  const paddingTop = 25;
  const paddingBottom = 35;

  const chartWidth = svgWidth - paddingLeft - paddingRight;
  const chartHeight = svgHeight - paddingTop - paddingBottom;

  const numBuckets = volumeBuckets.length;
  const slotWidth = numBuckets > 0 ? chartWidth / numBuckets : chartWidth;
  const barWidth = Math.max(Math.min(slotWidth * 0.7, 28), 2);

  // Pick labels to display along X-axis (prevent overlapping)
  const step = numBuckets > 20 ? Math.ceil(numBuckets / 8) : numBuckets > 10 ? 2 : 1;

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-2 mb-4 border-b border-slate-100 pb-3">
        <div className="flex items-center gap-2">
          <BarChart2 size={20} className="text-brand-600" />
          <h2 className="text-lg font-semibold text-slate-900">Applications Over Time</h2>
          <InfoTooltip
            title="Applications Over Time"
            content="Daily, weekly, or monthly submission volume across the selected period."
          />
        </div>
        <div className="flex items-center gap-3 text-xs">
          {hoveredBucket ? (
            <span className="font-semibold text-brand-700 bg-brand-50 px-2 py-0.5 rounded border border-brand-200">
              {hoveredBucket.label}: {hoveredBucket.applicationsSubmitted} application{hoveredBucket.applicationsSubmitted !== 1 ? 's' : ''}
            </span>
          ) : (
            <span className="text-slate-500 font-medium">
              Total in timeline: <strong className="text-slate-800">{totalBucketApplications}</strong>
            </span>
          )}
        </div>
      </div>

      {/* SVG Chart */}
      {numBuckets === 0 ? (
        <div className="flex h-44 items-center justify-center text-sm text-slate-400">
          No timeline data available for this period.
        </div>
      ) : (
        <div className="w-full overflow-hidden">
          <svg
            viewBox={`0 0 ${svgWidth} ${svgHeight}`}
            className="w-full h-auto overflow-visible select-none"
            style={{ maxHeight: '220px' }}
          >
            {/* Grid lines & Y-axis labels */}
            {[0, 0.5, 1].map((pct, idx) => {
              const yVal = Math.round(yUpper * pct);
              const yPos = paddingTop + chartHeight - pct * chartHeight;
              return (
                <g key={idx}>
                  <line
                    x1={paddingLeft}
                    y1={yPos}
                    x2={svgWidth - paddingRight}
                    y2={yPos}
                    stroke="#f1f5f9"
                    strokeWidth="1"
                    strokeDasharray={pct > 0 ? '4 4' : undefined}
                  />
                  <text
                    x={paddingLeft - 8}
                    y={yPos + 3}
                    textAnchor="end"
                    className="text-[10px] fill-slate-400 font-sans"
                  >
                    {yVal}
                  </text>
                </g>
              );
            })}

            {/* Bars */}
            {volumeBuckets.map((bucket, index) => {
              const count = bucket.applicationsSubmitted || 0;
              const xCenter = paddingLeft + index * slotWidth + slotWidth / 2;
              const barH = yUpper > 0 ? (count / yUpper) * chartHeight : 0;
              const xPos = xCenter - barWidth / 2;
              const yPos = paddingTop + chartHeight - barH;
              const isHovered = hoveredBucket?.bucketStart === bucket.bucketStart;

              const showLabel =
                index % step === 0 || index === numBuckets - 1;

              return (
                <g
                  key={bucket.bucketStart || index}
                  className="cursor-pointer"
                  onMouseEnter={() => setHoveredBucket(bucket)}
                  onMouseLeave={() => setHoveredBucket(null)}
                >
                  {/* Invisible hover area spanning full slot height for easy hover targeting */}
                  <rect
                    x={paddingLeft + index * slotWidth}
                    y={paddingTop}
                    width={slotWidth}
                    height={chartHeight}
                    fill="transparent"
                  />

                  {/* Active background highlight */}
                  {isHovered && (
                    <rect
                      x={paddingLeft + index * slotWidth}
                      y={paddingTop}
                      width={slotWidth}
                      height={chartHeight}
                      fill="#f8fafc"
                      opacity="0.8"
                    />
                  )}

                  {/* Value Bar */}
                  <rect
                    x={xPos}
                    y={count > 0 ? yPos : paddingTop + chartHeight - 1.5}
                    width={barWidth}
                    height={count > 0 ? Math.max(barH, 3) : 1.5}
                    rx={count > 0 ? (barWidth > 4 ? 2 : 1) : 0}
                    className={`transition-colors duration-150 ${
                      count > 0
                        ? isHovered
                          ? 'fill-brand-500'
                          : 'fill-brand-600'
                        : 'fill-slate-200'
                    }`}
                  >
                    <title>{`${bucket.label}: ${count} application${count !== 1 ? 's' : ''}`}</title>
                  </rect>

                  {/* X-axis label */}
                  {showLabel && (
                    <text
                      x={xCenter}
                      y={svgHeight - 10}
                      textAnchor="middle"
                      className={`text-[10px] font-sans transition-colors ${
                        isHovered ? 'fill-slate-800 font-semibold' : 'fill-slate-400'
                      }`}
                    >
                      {bucket.label}
                    </text>
                  )}
                </g>
              );
            })}
          </svg>
        </div>
      )}

      {/* Undated applications notice */}
      {unknownDateApplications > 0 && (
        <div className="mt-3 flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800 border border-amber-200">
          <Info size={15} className="shrink-0 text-amber-600" />
          <span>
            <strong>{unknownDateApplications}</strong> application{unknownDateApplications !== 1 ? 's' : ''} have no recorded application date and {unknownDateApplications !== 1 ? 'are' : 'is'} not shown on the timeline.
          </span>
        </div>
      )}
    </div>
  );
}
