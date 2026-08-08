import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'

/**
 * 掌握度颜色映射
 * >80 绿 / >50 黄 / >0 红 / 0 灰
 */
const getColor = (masteryRate) => {
  if (masteryRate > 80) return '#52c41a'
  if (masteryRate > 50) return '#faad14'
  if (masteryRate > 0) return '#ff4d4f'
  return '#d9d9d9'
}

/**
 * 知识图谱力导向图
 * 单击节点 → onNodeClick；双击节点 → onNodeDoubleClick
 */
export default function KnowledgeGraphCanvas({ data, onNodeClick, onNodeDoubleClick }) {
  const ref = useRef(null)

  useEffect(() => {
    if (!ref.current || !data) return
    const chart = echarts.init(ref.current)

    const nodes = (data.nodes || []).map(node => ({
      id: String(node.id ?? node.name),
      name: node.name,
      masteryRate: node.masteryRate ?? 0,
      weaknessLevel: node.weaknessLevel,
      isWeak: node.weaknessLevel === 'HIGH' || node.weaknessLevel === 'MEDIUM',
      symbolSize: (node.weaknessLevel === 'HIGH' || node.weaknessLevel === 'MEDIUM') ? 42 : 26,
      itemStyle: {
        color: getColor(node.masteryRate ?? 0),
        borderColor: 'rgba(255,255,255,0.6)',
        borderWidth: 1.5,
        shadowBlur: (node.weaknessLevel === 'HIGH' || node.weaknessLevel === 'MEDIUM') ? 15 : 0,
        shadowColor: 'rgba(255,77,79,0.4)',
      },
      label: { show: true, fontSize: 12, color: '#f3f4f6', fontWeight: node.weaknessLevel === 'HIGH' ? 600 : 400 },
    }))

    const links = (data.edges || []).map(edge => ({
      source: String(edge.source),
      target: String(edge.target),
      lineStyle: { color: 'rgba(255,255,255,0.25)', width: 1.5, curveness: 0.08 },
    }))

    chart.setOption({
      tooltip: {
        formatter: (params) => {
          if (params.dataType === 'node') {
            return `<b>${params.data.name}</b><br/>掌握度: ${(params.data.masteryRate ?? 0).toFixed(0)}%<br/>单击查看详情 · 双击去练习`
          }
          return ''
        },
      },
      series: [{
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        force: { repulsion: 260, edgeLength: [80, 180], gravity: 0.06 },
        data: nodes,
        links,
        emphasis: { focus: 'adjacency', lineStyle: { width: 3 } },
      }],
    })

    // 单击/双击区分：用定时器延迟单击，300ms 内再次点击则视为双击，取消单击动作
    let clickTimer = null
    let lastClickData = null

    chart.on('click', (params) => {
      if (params.dataType !== 'node') return
      // 记录本次点击，若已有 pending 的单击，则视为双击
      if (clickTimer && lastClickData && lastClickData.id === params.data.id) {
        clearTimeout(clickTimer)
        clickTimer = null
        lastClickData = null
        onNodeDoubleClick?.(params.data)
        return
      }
      lastClickData = params.data
      clickTimer = setTimeout(() => {
        onNodeClick?.(lastClickData)
        clickTimer = null
        lastClickData = null
      }, 300)
    })

    const handleResize = () => chart.resize()
    window.addEventListener('resize', handleResize)
    return () => {
      if (clickTimer) clearTimeout(clickTimer)
      chart.dispose()
      window.removeEventListener('resize', handleResize)
    }
  }, [data, onNodeClick, onNodeDoubleClick])

  return <div ref={ref} style={{ width: '100%', height: '520px' }} />
}
