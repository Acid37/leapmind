import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'

const LEVEL_COLOR = {
  HIGH: '#ff4d4f',
  MEDIUM: '#faad14',
  LOW: '#52c41a',
  MASTERED: '#1890ff',
  UNKNOWN: '#8c8c8c',
}

/**
 * 关联知识图谱（ECharts 力导向图）
 * 展示薄弱知识点与前置/后置知识点的关系
 */
export default function KpKnowledgeGraph({ data, onNodeClick }) {
  const ref = useRef(null)

  useEffect(() => {
    if (!ref.current || !data) return
    const chart = echarts.init(ref.current)

    chart.setOption({
      tooltip: {
        formatter: (params) => {
          if (params.dataType === 'node') {
            return `${params.data.name}<br/>掌握度: ${(params.data.masteryRate ?? 0).toFixed(0)}%`
          }
          return ''
        },
      },
      series: [{
        type: 'graph',
        layout: 'force',
        roam: true,
        force: { repulsion: 200, edgeLength: 120, gravity: 0.08 },
        label: { show: true, fontSize: 11, color: '#e5e7eb' },
        data: (data.nodes || []).map(node => ({
          id: String(node.id || node.name),
          name: node.name,
          masteryRate: node.masteryRate ?? 0,
          symbolSize: node.weaknessLevel === 'HIGH' ? 34 : node.weaknessLevel === 'MASTERED' ? 28 : 24,
          itemStyle: {
            color: LEVEL_COLOR[node.weaknessLevel] || '#8c8c8c',
            borderColor: 'rgba(255,255,255,0.5)',
            borderWidth: 1.5,
          },
        })),
        links: (data.edges || []).map(edge => ({
          source: String(edge.source),
          target: String(edge.target),
          lineStyle: { color: 'rgba(255,255,255,0.25)', width: 1.5 },
        })),
        emphasis: { focus: 'adjacency', lineStyle: { width: 2.5 } },
      }],
    })

    if (onNodeClick) {
      chart.on('click', (params) => {
        if (params.dataType === 'node') onNodeClick(params.data)
      })
    }

    const handleResize = () => chart.resize()
    window.addEventListener('resize', handleResize)
    return () => {
      chart.dispose()
      window.removeEventListener('resize', handleResize)
    }
  }, [data, onNodeClick])

  return <div ref={ref} style={{ width: '100%', height: '260px' }} />
}
