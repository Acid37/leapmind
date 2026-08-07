import { useId } from 'react';

const VIEWBOX_WIDTH = 440;
const VIEWBOX_HEIGHT = 370;
const CENTER_X = VIEWBOX_WIDTH / 2;
const CENTER_Y = 178;
const CHART_RADIUS = 112;
const LABEL_RADIUS = 148;
const GRID_LEVELS = [20, 40, 60, 80, 100];
const DEFAULT_COLOR = '#0ea5e9';

const clampPercentage = (value) => {
  const parsed = Number.parseFloat(value);

  if (!Number.isFinite(parsed)) {
    return 0;
  }

  return Math.min(100, Math.max(0, parsed));
};

const polarPoint = (index, count, radius) => {
  const angle = -Math.PI / 2 + (index * Math.PI * 2) / count;

  return {
    x: CENTER_X + Math.cos(angle) * radius,
    y: CENTER_Y + Math.sin(angle) * radius,
  };
};

const pointList = (count, radius) =>
  Array.from({ length: count }, (_, index) => {
    const point = polarPoint(index, count, radius);
    return `${point.x.toFixed(2)},${point.y.toFixed(2)}`;
  }).join(' ');

const shortenLabel = (label) => {
  const text = String(label);
  return text.length > 10 ? `${text.slice(0, 9)}…` : text;
};

/**
 * Responsive learning-dimension radar chart.
 *
 * @param {{
 *   dimensions?: Array<{ key?: string, label?: string, value?: number|string, color?: string }>,
 *   className?: string
 * }} props
 */
const RadarChart = ({ dimensions = [], className = '' }) => {
  const rawId = useId();
  const gradientId = `radar-fill-${rawId.replace(/:/g, '')}`;
  const normalizedDimensions = Array.isArray(dimensions)
    ? dimensions
        .filter((dimension) => dimension && typeof dimension === 'object')
        .map((dimension, index) => ({
          ...dimension,
          key: dimension.key ?? `dimension-${index}`,
          label: dimension.label || dimension.key || `维度 ${index + 1}`,
          value: clampPercentage(dimension.value),
          color: dimension.color || DEFAULT_COLOR,
        }))
    : [];

  if (normalizedDimensions.length === 0) {
    return (
      <div
        className={`flex min-h-72 items-center justify-center rounded-2xl border border-dashed border-white/15 bg-black/10 px-6 text-center ${className}`}
        role="status"
      >
        <div>
          <p className="text-sm font-semibold text-white/70">暂无能力维度数据</p>
          <p className="mt-1 text-xs text-white/40">完成学习后，这里会生成你的能力雷达图</p>
        </div>
      </div>
    );
  }

  const dimensionCount = normalizedDimensions.length;
  const dataPoints = normalizedDimensions
    .map((dimension, index) => {
      const point = polarPoint(index, dimensionCount, CHART_RADIUS * (dimension.value / 100));
      return `${point.x.toFixed(2)},${point.y.toFixed(2)}`;
    })
    .join(' ');
  const average = Math.round(
    normalizedDimensions.reduce((total, dimension) => total + dimension.value, 0) /
      dimensionCount,
  );

  return (
    <figure className={`w-full ${className}`} aria-label={`学习能力雷达图，平均掌握度 ${average}%`}>
      <svg
        className="h-auto w-full overflow-visible"
        viewBox={`0 0 ${VIEWBOX_WIDTH} ${VIEWBOX_HEIGHT}`}
        role="img"
        aria-labelledby={`${gradientId}-title ${gradientId}-description`}
        preserveAspectRatio="xMidYMid meet"
      >
        <title id={`${gradientId}-title`}>学习能力雷达图</title>
        <desc id={`${gradientId}-description`}>
          {normalizedDimensions
            .map((dimension) => `${dimension.label} ${Math.round(dimension.value)}%`)
            .join('，')}
        </desc>

        <defs>
          <radialGradient id={gradientId} cx="50%" cy="45%" r="60%">
            <stop offset="0%" stopColor="#38bdf8" stopOpacity="0.48" />
            <stop offset="100%" stopColor="#2563eb" stopOpacity="0.16" />
          </radialGradient>
        </defs>

        <g aria-hidden="true">
          {GRID_LEVELS.map((level) =>
            dimensionCount >= 3 ? (
              <polygon
                key={level}
                points={pointList(dimensionCount, CHART_RADIUS * (level / 100))}
                fill={level === 100 ? 'rgba(255,255,255,.04)' : 'none'}
                stroke={level === 100 ? 'rgba(255,255,255,.3)' : 'rgba(255,255,255,.14)'}
                strokeWidth={level === 100 ? 1.4 : 1}
              />
            ) : (
              <circle
                key={level}
                cx={CENTER_X}
                cy={CENTER_Y}
                r={CHART_RADIUS * (level / 100)}
                fill={level === 100 ? 'rgba(255,255,255,.04)' : 'none'}
                stroke={level === 100 ? 'rgba(255,255,255,.3)' : 'rgba(255,255,255,.14)'}
                strokeWidth={level === 100 ? 1.4 : 1}
              />
            ),
          )}

          {normalizedDimensions.map((dimension, index) => {
            const outerPoint = polarPoint(index, dimensionCount, CHART_RADIUS);
            return (
              <line
                key={`axis-${dimension.key}`}
                x1={CENTER_X}
                y1={CENTER_Y}
                x2={outerPoint.x}
                y2={outerPoint.y}
                stroke="rgba(255,255,255,.18)"
                strokeWidth="1"
              />
            );
          })}

          {dimensionCount >= 3 ? (
            <polygon
              points={dataPoints}
              fill={`url(#${gradientId})`}
              stroke="#0284c7"
              strokeWidth="2.5"
              strokeLinejoin="round"
            />
          ) : (
            <polyline
              points={dataPoints}
              fill="none"
              stroke="#0284c7"
              strokeWidth="2.5"
              strokeLinecap="round"
            />
          )}
        </g>

        {normalizedDimensions.map((dimension, index) => {
          const valuePoint = polarPoint(
            index,
            dimensionCount,
            CHART_RADIUS * (dimension.value / 100),
          );
          const labelPoint = polarPoint(index, dimensionCount, LABEL_RADIUS);
          const horizontalOffset = labelPoint.x - CENTER_X;
          const textAnchor = horizontalOffset > 12 ? 'start' : horizontalOffset < -12 ? 'end' : 'middle';
          const valueOffset = labelPoint.y < CENTER_Y - 80 ? -2 : 15;

          return (
            <g key={dimension.key}>
              <circle
                cx={valuePoint.x}
                cy={valuePoint.y}
                r="5"
                fill={dimension.color}
                stroke="#ffffff"
                strokeWidth="2.5"
              />
              <text
                x={labelPoint.x}
                y={labelPoint.y + valueOffset}
                textAnchor={textAnchor}
                fill="rgba(255,255,255,.68)"
                className="text-[12px] font-semibold"
              >
                <title>{dimension.label}</title>
                {shortenLabel(dimension.label)}
              </text>
              <text
                x={labelPoint.x}
                y={labelPoint.y + valueOffset + 17}
                textAnchor={textAnchor}
                fill={dimension.color}
                className="text-[12px] font-bold"
              >
                {Math.round(dimension.value)}%
              </text>
            </g>
          );
        })}

        <g aria-hidden="true">
          <circle cx={CENTER_X} cy={CENTER_Y} r="31" fill="rgba(20,12,55,.9)" stroke="rgba(255,255,255,.2)" />
          <text
            x={CENTER_X}
            y={CENTER_Y - 2}
            textAnchor="middle"
            fill="rgba(255,255,255,.55)"
            className="text-[10px] font-medium"
          >
            平均掌握
          </text>
          <text
            x={CENTER_X}
            y={CENTER_Y + 17}
            textAnchor="middle"
            fill="#ffffff"
            className="text-[17px] font-bold"
          >
            {average}%
          </text>
        </g>
      </svg>
    </figure>
  );
};

export default RadarChart;
