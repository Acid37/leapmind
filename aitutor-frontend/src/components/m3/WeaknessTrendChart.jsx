import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'

/**
 * 薄弱程度趋势折线图（近7天 vs 前7天错误率）
 */
export default function WeaknessTrendChart({ recentErrorRate, previousErrorRate, errorRate }) {
  const ref = useRef(null)

  useEffect(() => {
    if (!ref.current) return
    const chart = echarts.init(ref.current)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 16, top: 20, bottom: 24 },
      xAxis: {
        type: 'category',
        data: ['前7天', '近期', '历史'],
        axisLine: { lineStyle: { color: 'rgba(255,255,255,0.2)' } },
        axisLabel: { color: 'rgba(255,255,255,0.6)', fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 100,
        axisLine: { show: false },
        splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
        axisLabel: { color: 'rgba(255,255,255,0.5)', fontSize: 10, formatter: '{value}%' },
      },
      series: [{
        name: '错误率',
        type: 'line',
        smooth: true,
        symbolSize: 8,
        data: [previousErrorRate ?? 0, recentErrorRate ?? 0, (errorRate ?? 0) * 100],
        lineStyle: { width: 3, color: '#a78bfa' },
        itemStyle: { color: '#a78bfa' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(167,139,250,0.4)' },
            { offset: 1, color: 'rgba(167,139,250,0.02)' },
          ]),
        },
      }],
    })
    const handleResize = () => chart.resize()
    window.addEventListener('resize', handleResize)
    return () => {
      chart.dispose()
      window.removeEventListener('resize', handleResize)
    }
  }, [recentErrorRate, previousErrorRate, errorRate])

  return <div ref={ref} style={{ width: '100%', height: '240px' }} />
}
