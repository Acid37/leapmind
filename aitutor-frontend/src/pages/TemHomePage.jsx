"use client"

import { Star, ChevronRight, MessageCircle, ChevronDown, Sparkles } from "lucide-react"
import { useState, useRef, useLayoutEffect, useEffect } from "react"
import { getAllStages, getGradesByStage } from '../services/educationService'
import { getSections, SEMESTER } from '../services/courseService'
import { getUserInfo, inferStageCodeFromGrade, saveUserInfo } from '../utils/tokenManager'
import { getUserProfile } from '../services/authService'
import { ApiError } from '../services/api'

// 添加全局滚动条样式
const scrollbarStyles = `
  .custom-scrollbar {
    scrollbar-width: thin;
    scrollbar-color: #A286FF #4c1d95;
  }
  .custom-scrollbar::-webkit-scrollbar {
    width: 10px;
  }
  .custom-scrollbar::-webkit-scrollbar-track {
    background: #4c1d95;
    border-radius: 10px;
  }
  .custom-scrollbar::-webkit-scrollbar-thumb {
    background: #A286FF;
    border-radius: 10px;
  }
  .custom-scrollbar::-webkit-scrollbar-thumb:hover {
    background: #b49fff;
  }
  .custom-scrollbar::-webkit-scrollbar-button {
    display: none !important;
    height: 0 !important;
    width: 0 !important;
  }
`

export default function LearningApp({
  onOpenProfile,
  onEnterProject,
  onOpenTeacherAvatar,
  onM2PhotoQa,
  onM2Explain,
  onOpenLearningProfile,
  onM1Practice,
}) {
  // UI 状态
  const [isGradeOpen, setIsGradeOpen] = useState(false)
  const [lineStyle, setLineStyle] = useState({ top: 0, bottom: 0 })
  const [selectedUnit, setSelectedUnit] = useState("")
  const [selectedSubject, setSelectedSubject] = useState("语文")
  const [toastMessage, setToastMessage] = useState("")
  const [showToast, setShowToast] = useState(false)
  const containerRef = useRef(null)
  const firstDotRef = useRef(null)
  const lastDotRef = useRef(null)

  // API 数据状态
  const [stages, setStages] = useState([]) // 教育阶段列表
  const [selectedStage, setSelectedStage] = useState(null) // 选中的教育阶段
  const [grades, setGrades] = useState([]) // 年级列表
  const [selectedGrade, setSelectedGrade] = useState(null) // 选中的年级
  const [selectedSemester, setSelectedSemester] = useState(SEMESTER.FIRST) // 选中的学期
  const [sections, setSections] = useState([]) // 课程章节数据
  const [loading, setLoading] = useState(false) // 加载状态
  const [error, setError] = useState(null) // 错误信息
  const isInitializing = useRef(true) // 标记是否正在初始化

  const subjects = ["语文", "数学", "英语", "物理", "更多科目"]
 

  // 显示提示信息
  const showFeatureToast = (message) => {
    setToastMessage(message)
    setShowToast(true)
    setTimeout(() => {
      setShowToast(false)
    }, 2000)
  }

  // 初始化：加载教育阶段和年级数据
  useEffect(() => {
    const loadInitialData = async () => {
      try {
        // 先从后端获取最新的用户信息
        let userInfo = null
        try {
          userInfo = await getUserProfile()
          console.log('[首页初始化] 从后端获取的用户信息:', userInfo)
        } catch (err) {
          console.warn('[首页初始化] 从后端获取用户信息失败，使用本地缓存:', err)
          // 如果后端获取失败，使用本地缓存
          userInfo = getUserInfo()
        }
        
        // 如果后端也没有，使用本地缓存
        if (!userInfo) {
          userInfo = getUserInfo()
        }
        
        console.log('[首页初始化] 最终使用的用户信息:', userInfo)
        
        // 获取教育阶段列表
        const stagesData = await getAllStages()
        setStages(stagesData)
        
        // 从用户信息中获取阶段和年级（优先使用 stage/grade，兼容 stageCode/gradeCode）
        // 优先使用 stage 字段，如果没有则使用 stageCode，再没有则根据 grade 推断
        let targetStageCode = userInfo?.stage || userInfo?.stageCode
        const gradeCode = userInfo?.grade || userInfo?.gradeCode
        
        console.log('[首页初始化] 提取的阶段代码:', targetStageCode, '年级代码:', gradeCode)
        
        // 如果没有 stage 但有 grade，根据 grade 推断 stage
        if (!targetStageCode && gradeCode) {
          targetStageCode = inferStageCodeFromGrade(gradeCode)
          console.log('[首页初始化] 根据年级代码推断的阶段代码:', gradeCode, '->', targetStageCode)
        }
        
        if (targetStageCode) {
          const defaultStage = stagesData.find(s => s.stageCode === targetStageCode)
          console.log('[首页初始化] 找到的教育阶段:', defaultStage)
          
          if (defaultStage) {
            setSelectedStage(defaultStage)
            // 加载该阶段的年级列表
            const gradesData = await getGradesByStage(defaultStage.stageCode)
            setGrades(gradesData)
            console.log('[首页初始化] 加载的年级列表:', gradesData)
            
            // 如果用户有年级信息，设置为默认年级
            if (gradeCode) {
              console.log('[首页初始化] 用户信息中的年级代码:', gradeCode)
              console.log('[首页初始化] 年级列表中的所有年级代码:', gradesData.map(g => g.gradeCode))
              
              const defaultGrade = gradesData.find(g => {
                // 精确匹配 gradeCode
                const match = g.gradeCode === gradeCode
                console.log(`[首页初始化] 比较: ${g.gradeCode} === ${gradeCode} ? ${match}`)
                return match
              })
              
              console.log('[首页初始化] 查找年级结果:', {
                gradeCode,
                found: defaultGrade,
                gradeName: defaultGrade?.gradeName
              })
              
              if (defaultGrade) {
                setSelectedGrade(defaultGrade)
                console.log('[首页初始化] ✅ 已设置年级:', defaultGrade.gradeName, 'gradeCode:', defaultGrade.gradeCode)
              } else {
                console.warn('[首页初始化] ⚠️ 警告：用户保存的年级代码', gradeCode, '在当前阶段中不存在')
                console.warn('[首页初始化] 可用的年级列表:', gradesData.map(g => ({ code: g.gradeCode, name: g.gradeName })))
                // 如果找不到对应的年级，选择第一个
                if (gradesData.length > 0) {
                  setSelectedGrade(gradesData[0])
                  console.log('[首页初始化] 使用第一个年级作为默认值:', gradesData[0].gradeName)
                }
              }
            } else {
              console.log('[首页初始化] 用户信息中没有年级代码')
              // 如果没有默认年级，选择第一个
              if (gradesData.length > 0) {
                console.log('[首页初始化] 用户没有保存年级，选择第一个:', gradesData[0].gradeName)
                setSelectedGrade(gradesData[0])
              }
            }
          } else {
            console.warn('[首页初始化] 警告：阶段代码', targetStageCode, '在系统中不存在')
            // 如果找不到对应的阶段，选择第一个
            if (stagesData.length > 0) {
              const firstStage = stagesData[0]
              setSelectedStage(firstStage)
              const gradesData = await getGradesByStage(firstStage.stageCode)
              setGrades(gradesData)
              if (gradesData.length > 0) {
                setSelectedGrade(gradesData[0])
              }
            }
          }
        } else if (stagesData.length > 0) {
          // 如果没有用户信息或没有保存的阶段，选择第一个教育阶段
          console.log('[首页初始化] 用户没有保存阶段信息，选择第一个阶段')
          const firstStage = stagesData[0]
          setSelectedStage(firstStage)
          const gradesData = await getGradesByStage(firstStage.stageCode)
          setGrades(gradesData)
          if (gradesData.length > 0) {
            setSelectedGrade(gradesData[0])
          }
        }
      } catch (err) {
        console.error('加载初始数据失败:', err)
        setError('加载数据失败，请刷新页面重试')
        showFeatureToast('加载数据失败，请刷新页面重试')
      }
    }

    const initPromise = loadInitialData()
    initPromise.finally(() => {
      // 初始化完成后，标记初始化结束
      isInitializing.current = false
      console.log('[首页初始化] 初始化完成')
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 当教育阶段改变时，加载对应的年级列表
  useEffect(() => {
    // 如果正在初始化，不执行此逻辑（初始化逻辑已经在 loadInitialData 中处理）
    if (isInitializing.current) {
      console.log('[首页阶段改变] 跳过：正在初始化中')
      return
    }
    
    if (selectedStage) {
      const loadGrades = async () => {
        try {
          console.log('[首页阶段改变] 用户手动切换阶段:', selectedStage.stageName)
          const gradesData = await getGradesByStage(selectedStage.stageCode)
          setGrades(gradesData)
          
          if (gradesData.length > 0) {
            // 优先尝试从用户信息中选择年级
            const userInfo = getUserInfo() || {}
            const userGradeCode = userInfo?.grade || userInfo?.gradeCode
            
            let targetGrade = null
            
            // 如果用户信息中有年级，且该年级在当前阶段的年级列表中，使用它
            if (userGradeCode) {
              targetGrade = gradesData.find(g => g.gradeCode === userGradeCode)
              if (targetGrade) {
                console.log('[首页阶段改变] 从用户信息中找到年级:', targetGrade.gradeName)
              }
            }
            
            // 如果用户信息中没有或找不到，检查当前选中的年级是否在新列表中
            if (!targetGrade && selectedGrade) {
              targetGrade = gradesData.find(g => g.gradeCode === selectedGrade.gradeCode)
              if (targetGrade) {
                console.log('[首页阶段改变] 当前选中的年级在新列表中:', targetGrade.gradeName)
              }
            }
            
            // 如果还是没找到，选择第一个
            if (!targetGrade) {
              targetGrade = gradesData[0]
              console.log('[首页阶段改变] 使用第一个年级:', targetGrade.gradeName)
            }
            
            setSelectedGrade(targetGrade)
          } else {
            setSelectedGrade(null)
          }
        } catch (err) {
          console.error('加载年级列表失败:', err)
          setError('加载年级列表失败')
        }
      }
      loadGrades()
    }
  }, [selectedStage])

  // 当学科、年级、学期改变时，加载课程章节
  useEffect(() => {
    if (selectedSubject && selectedStage && selectedGrade) {
      loadSections()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedSubject, selectedStage, selectedGrade, selectedSemester])

  // 加载课程章节数据
  const loadSections = async () => {
    if (!selectedSubject || !selectedStage || !selectedGrade) {
      return
    }

    setLoading(true)
    setError(null)
    
    try {
      const sectionsData = await getSections({
        subject: selectedSubject,
        stageName: selectedStage.stageName,
        gradeName: selectedGrade.gradeName,
        semester: selectedSemester,
      })
      
      setSections(sectionsData)
      console.log('课程章节数据加载成功:', sectionsData)
      
      // 如果有数据，设置第一个章（chapter）为选中
      if (sectionsData.length > 0) {
        const firstItem = sectionsData[0]
        // 使用 chapterNumber 作为选中标识，而不是 chapterTitle
        const firstChapterNumber = firstItem.chapterNumber != null ? String(firstItem.chapterNumber) : ''
        setSelectedUnit(firstChapterNumber)
        console.log('设置第一个章:', firstChapterNumber, '标题:', firstItem.chapterTitle)
      } else {
        setSelectedUnit('')
        console.log('没有课程数据')
      }
    } catch (err) {
      console.error('加载课程章节失败:', err)
      if (err instanceof ApiError) {
        setError(err.message || '加载课程章节失败')
        showFeatureToast(err.message || '加载课程章节失败')
      } else {
        setError('加载课程章节失败，请稍后重试')
        showFeatureToast('加载课程章节失败，请稍后重试')
      }
      setSections([])
    } finally {
      setLoading(false)
    }
  }

  // 将 API 返回的章节数据转换为按单元分组的格式
  // 注意：chapter = 章，section = 节
  // 按 chapterNumber（章编号）分组，每个组里放的是 section（节）数据
  // 显示时使用 chapterTitle（章标题）
  const processSectionsData = () => {
    if (!sections || sections.length === 0) {
      return {}
    }

    // 按 chapterNumber 分组（章）
    // 数据结构：{ chapterNumber: { chapterTitle: "标题", sections: [...] } }
    const grouped = {}
    
    sections.forEach((item) => {
      // 使用 chapterNumber 作为分组键，确保同一章下的所有节都在同一个列表中
      // 如果 chapterNumber 不存在，使用 '0' 作为默认值
      const chapterNumber = item.chapterNumber != null ? String(item.chapterNumber) : '0'
      
      if (!grouped[chapterNumber]) {
        // 使用第一个出现的 chapterTitle 作为该章的标题
        grouped[chapterNumber] = {
          chapterTitle: (item.chapterTitle && item.chapterTitle.trim()) || '未分类',
          chapterNumber: item.chapterNumber,
          sections: []
        }
      } else {
        // 如果该章已存在但 chapterTitle 为空，使用当前项的 chapterTitle
        if (!grouped[chapterNumber].chapterTitle || grouped[chapterNumber].chapterTitle === '未分类') {
          const newTitle = (item.chapterTitle && item.chapterTitle.trim())
          if (newTitle) {
            grouped[chapterNumber].chapterTitle = newTitle
          }
        }
      }
      
      // 转换为 UI 需要的格式，每个 item 是一个 section（节）
      grouped[chapterNumber].sections.push({
        id: item.sectionNumber || Math.random(),
        title: item.sectionTitle || '未命名节',
        subtitle: '', // 可以根据需要添加
        rating: Math.floor(Math.random() * 3) + 1, // 随机评分，实际应该从后端获取
        icon: getSubjectIcon(selectedSubject),
        chapterNumber: item.chapterNumber,
        chapterTitle: item.chapterTitle,
        sectionNumber: item.sectionNumber,
        sectionTitle: item.sectionTitle,
        sectionContent: item.sectionContent,
        chapterContent: item.chapterContent,
        // 直接使用后端返回的 courseId
        courseId: item.courseId,
        subject: item.subject,
      })
    })

    // 对每个章下的节按 sectionNumber 排序，确保顺序正确
    Object.keys(grouped).forEach((chapterNumber) => {
      grouped[chapterNumber].sections.sort((a, b) => {
        // 优先按 sectionNumber 排序
        if (a.sectionNumber != null && b.sectionNumber != null) {
          return a.sectionNumber - b.sectionNumber
        }
        // 如果没有 sectionNumber，保持原顺序
        return 0
      })
    })

    // 返回的数据结构需要包含学科这一层
    // 格式: { "语文": { "1": { chapterTitle: "第一章", sections: [...] }, "2": { ... } } }
    return {
      [selectedSubject]: grouped
    }
  }

  // 根据学科返回对应的图标
  const getSubjectIcon = (subject) => {
    const iconMap = {
      '语文': '📖',
      '数学': '🔢',
      '英语': '👋',
      '物理': '🚀',
      '更多科目': '📚',
    }
    return iconMap[subject] || '📚'
  }

  // 获取处理后的数据
  const processedData = processSectionsData()
  
  // 使用后端返回并处理后的数据；当后端无数据时显示"暂无课程"
  const subjectsData = processedData
  
  const unitsData = subjectsData[selectedSubject] || {}
  
  // 对章的列表进行排序，确保显示顺序正确
  // 按 chapterNumber 排序（数字排序）
  const sortedUnitNumbers = Object.keys(unitsData).sort((a, b) => {
    const numA = parseFloat(a) || 0
    const numB = parseFloat(b) || 0
    return numA - numB
  })
  
  // currentUnitNames 存储的是 chapterNumber（用于查找），但显示时使用 chapterTitle
  const currentUnitNames = sortedUnitNumbers
  
  // 根据选中的 chapterNumber 获取对应的节列表
  const currentUnitData = selectedUnit ? (unitsData[selectedUnit] || null) : null
  const currentSections = currentUnitData ? (currentUnitData.sections || []) : []
  
  // 获取当前选中章的标题（用于显示）
  const currentUnitTitle = currentUnitData ? currentUnitData.chapterTitle : ''

  // 调试信息（仅在开发环境）
  useEffect(() => {
    if (import.meta.env.DEV && sections.length > 0) {
      console.log('=== 课程数据调试信息 ===')
      console.log('当前学科:', selectedSubject)
      console.log('原始章节数据:', sections)
      console.log('处理后的数据:', processedData)
      console.log('当前学科的数据:', unitsData)
      console.log('单元列表（chapterNumber）:', currentUnitNames)
      console.log('选中的单元（chapterNumber）:', selectedUnit)
      console.log('当前单元的标题:', currentUnitTitle)
      console.log('当前单元的小节数量:', currentSections.length)
      console.log('当前单元的小节:', currentSections)
      console.log('========================')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sections.length, selectedSubject, selectedUnit])

  // 如果没有选中的单元，自动选择第一个
  useEffect(() => {
    if (currentUnitNames.length > 0 && !selectedUnit) {
      setSelectedUnit(currentUnitNames[0])
    }
  }, [currentUnitNames, selectedUnit])

  const features = [
    { id: 'photo-search', title: "拍照搜题", color: "from-pink-400 via-pink-300 to-orange-400", icon: "./svg/paizhaosouti.svg", action: onM2PhotoQa },
    { id: 'ai-explain', title: "AI 讲题", color: "from-purple-400 via-purple-300 to-indigo-500", icon: null, custom: true, action: onM2Explain },
    { id: 'learning-profile', title: "学情分析", color: "from-cyan-300 via-blue-300 to-blue-500", icon: "./svg/xueqingfenxi.svg", action: onOpenLearningProfile },
    { id: 'm1-practice', title: "AI 做题", color: "from-emerald-400 via-green-400 to-teal-500", icon: null, custom: true, action: onM1Practice },
  ]


  // 计算竖线的位置
  useLayoutEffect(() => {
    if (firstDotRef.current && lastDotRef.current && containerRef.current) {
      const containerTop = containerRef.current.getBoundingClientRect().top
      const firstDotTop = firstDotRef.current.getBoundingClientRect().top - containerTop
      const lastDotTop = lastDotRef.current.getBoundingClientRect().top - containerTop
      const containerHeight = containerRef.current.offsetHeight

      setLineStyle({
        top: firstDotTop + 6, // 蓝点半径
        bottom: containerHeight - lastDotTop - 6, // 蓝点半径
      })
    }
  }, [])

  return (
    <div className="min-h-screen w-full bg-gradient-to-br from-purple-700 via-purple-600 via-blue-600 via-blue-700 to-blue-900 text-white overflow-hidden" style={{backgroundImage: "linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%,rgb(86, 43, 205) 80%,rgb(47, 8, 154) 100%)"}}>
      <style>{scrollbarStyles}</style>
      <header className="px-10 py-6 flex items-center justify-between border-b border-purple-400/20">
        <div className="flex items-center gap-4">
          <span className="text-sm font-semibold text-purple-200/70 tracking-wide">LeapMind</span>
          <nav className="flex gap-10 ml-16 items-center">
            {subjects.map((subject) => (
              <button
                key={subject}
                onClick={() => {
                  setSelectedSubject(subject)
                  // 切换科目时重置单元选择，会在 useEffect 中自动选择第一个
                  setSelectedUnit("")
                }}
                className={`transition duration-300 font-medium tracking-wide flex items-center cursor-pointer border-none bg-transparent ${
                  selectedSubject === subject
                    ? "text-white font-black text-3xl drop-shadow-md"
                    : "text-purple-100/60 text-2xl hover:text-purple-100/90"
                }`}
              >
                {subject}
              </button>
            ))}
          </nav>
        </div>
        <div className="relative inline-flex items-center gap-4">
          <button
            type="button"
            onClick={() => onOpenTeacherAvatar?.()}
            className="inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-4 py-2.5 text-sm font-bold text-white shadow-lg backdrop-blur-md transition hover:-translate-y-0.5 hover:bg-white/20"
          >
            <Sparkles className="h-4 w-4 text-cyan-200" />
            虚拟教师
          </button>
          {/* 学期选择器 */}
          <div className="relative">
            <div className="inline-flex items-center rounded-full bg-[#A286FF]/40 p-1 shadow-lg">
              <button
                onClick={() => setSelectedSemester(SEMESTER.FIRST)}
                className={`px-5 py-2 rounded-full text-sm font-semibold transition-all duration-300 ${selectedSemester === SEMESTER.FIRST ? 'bg-[#A286FF] text-white shadow-lg' : 'bg-[#A286FF]/20 text-white/90 hover:bg-[#A286FF]/40'}`}
              >
                上册
              </button>
              <button
                onClick={() => setSelectedSemester(SEMESTER.SECOND)}
                className={`px-5 py-2 rounded-full text-sm font-semibold transition-all duration-300 ${selectedSemester === SEMESTER.SECOND ? 'bg-[#A286FF] text-white shadow-lg' : 'bg-[#A286FF]/20 text-white/90 hover:bg-[#A286FF]/40'}`}
              >
                下册
              </button>
            </div>
          </div>

          {/* 年级选择器 */}
          <div className="relative inline-flex items-center">
            <button
              onClick={() => setIsGradeOpen(!isGradeOpen)}
              className="px-8 py-3 pr-10 rounded-l-full bg-[#A286FF] flex items-center gap-2 shadow-lg hover:shadow-2xl transition-all duration-300 font-semibold text-white text-base"
            >
              <ChevronDown className={`w-5 h-5 transition-transform duration-300 ${isGradeOpen ? "rotate-180" : ""}`} />
              <span>{selectedGrade ? selectedGrade.gradeName : '选择年级'}</span>
            </button>
            <img 
              src="./login/avatar.png" 
              alt="User Avatar"
              className="w-12 h-12 rounded-full shadow-md -ml-5 relative z-10 object-cover cursor-pointer hover:ring-2 hover:ring-white/70"
              onClick={() => onOpenProfile && onOpenProfile()}
              title="个人主页"
            />
            {isGradeOpen && (
              <div className="absolute left-0 top-full mt-3 w-48 bg-gradient-to-b from-[#A286FF] to-[#9370DB] backdrop-blur-md border border-purple-300/40 rounded-3xl shadow-2xl z-50 overflow-hidden max-h-96 overflow-y-auto">
                {grades.length === 0 ? (
                  <div className="px-6 py-3 text-white/70 text-sm text-center">暂无年级数据</div>
                ) : (
                  grades.map((grade, index) => (
                    <button
                      key={grade.gradeCode}
                      onClick={() => {
                        setSelectedGrade(grade)
                        setIsGradeOpen(false)
                        // 注意：年级和阶段的设置应该在 ProfilePage 页面保存
                        // 这里的选择仅用于当前会话的课程筛选，不会持久化
                      }}
                      className={`block w-full text-left px-6 py-3 text-base font-semibold transition duration-200 ${
                        index !== grades.length - 1 ? "border-b border-white/20" : ""
                      } ${
                        selectedGrade?.gradeCode === grade.gradeCode
                          ? "bg-white/30 text-white backdrop-blur-sm"
                          : "text-white/90 hover:bg-white/20 hover:text-white"
                      }`}
                    >
                      {grade.gradeName}
                    </button>
                  ))
                )}
              </div>
            )}
          </div>
        </div>
      </header>

       {/* <div className="items-stretch flex-1 flex-col lg:flex-row gap-8 p-6 lg:p-8 justify-center  overflow-auto max-w-full mx-auto">  */}
       {/* <div className="flex flex-col lg:flex-row gap-8 p-6 lg:p-8 justify-center items-center min-h-[calc(100vh-120px)] max-w-full mx-auto"> */}
       <div className="flex flex-col lg:flex-row gap-8 p-6 lg:p-8 justify-center items-center min-h-[calc(100vh-120px)] max-w-full mx-auto">
        {/* Main Content */}
        {/*主要内内容的紫色阴影背景，包含整个学习模块列表*/}
        <div className="w-full lg:flex-1 lg:max-w-5xl bg-gradient-to-b from-purple-900/40 to-purple-800/20 rounded-3xl p-8 backdrop-blur-md border border-purple-500/20 shadow-2xl py-0 px-0 relative">
          {/* 目录标签 - 覆盖在标题行上面 */}
          <div className="absolute left-4 top-[-5px] bg-purple-500 rounded-t-none rounded-b-full text-xl font-bold py-10 px-5 shadow-lg z-10">目录</div>
          {/*蓝色课程卡片（包含"智能生成课"、"目录"和"选课"按钮的那个框）*/}
          <div className="w-full bg-gradient-to-r from-[#638AFF] to-[#638AFF] rounded-t-2xl p-0 mb-0 shadow-2xl border border-blue-300/30 backdrop-blur-sm">
            {/*这是蓝色卡片内部的**顶部一行布局**（包含"智能生成课"、"目录"、导航按钮和"选课"按钮）*/}
            <div className="flex flex-col sm:flex-row items-center justify-between mb-5 mt-5 ml-6 mr-6 gap-4">
              <div className="flex items-center gap-6 flex-wrap justify-center sm:justify-start ml-28">
                <button 
                  onClick={() => {
                    const currentIndex = currentUnitNames.indexOf(selectedUnit)
                    if (currentIndex > 0) {
                      setSelectedUnit(currentUnitNames[currentIndex - 1])
                    }
                  }}
                  className="w-10 h-10 rounded-full bg-gray-200 flex items-center justify-center hover:shadow-lg transition duration-200 shadow-md hover:scale-110 flex-shrink-0 disabled:opacity-50 disabled:cursor-not-allowed"
                  disabled={currentUnitNames.indexOf(selectedUnit) === 0}
                >
                  <ChevronRight className="w-6 h-6 text-gray-500 rotate-180 stroke-current" strokeWidth={5} />
                </button>
                <div className="text-2xl sm:text-3xl font-bold min-w-[120px] text-center">
                  {loading ? '加载中...' : currentUnitTitle || '暂无课程'}
                </div>
                <button 
                  onClick={() => {
                    const currentIndex = currentUnitNames.indexOf(selectedUnit)
                    if (currentIndex < currentUnitNames.length - 1) {
                      setSelectedUnit(currentUnitNames[currentIndex + 1])
                    }
                  }}
                  className="w-10 h-10 rounded-full bg-gray-200 flex items-center justify-center hover:shadow-lg transition duration-200 shadow-md hover:scale-110 flex-shrink-0 disabled:opacity-50 disabled:cursor-not-allowed"
                  disabled={currentUnitNames.indexOf(selectedUnit) === currentUnitNames.length - 1}
                >
                  <ChevronRight className="w-6 h-6 text-gray-500 stroke-current" strokeWidth={5} />
                </button>
              </div>
              <button className="bg-yellow-400 text-purple-900 px-8 py-3 rounded-full font-bold text-base hover:bg-yellow-300 transition duration-150 shadow-lg hover:shadow-2xl hover:-translate-y-1 active:translate-y-0.5 active:shadow-md border-b-4 border-yellow-600 hover:border-yellow-700 transform whitespace-nowrap">
                选课
              </button>
            </div>
          </div>
          
          <div
            ref={containerRef}
            className="bg-[#4210A5]/60 rounded-b-2xl p-6 backdrop-blur-lg border-x border-b border-purple-400/30 shadow-lg relative pl-8 max-h-96 overflow-y-auto custom-scrollbar"
            style={{
              scrollbarWidth: "thin",
              scrollbarColor: "#A286FF #4c1d95"
            }}
          >
            {/* Vertical timeline line */}
            <div
              className="absolute bg-gradient-to-b from-cyan-300 to-cyan-300/80"
              style={{
                left: "30px",
                width: "2px",
                top: lineStyle.top,
                bottom: lineStyle.bottom,
                transition: "all 0.3s ease",
              }}
            />

            <div className="space-y-4">
              {loading ? (
                <div className="text-center py-8 text-purple-200">加载中...</div>
              ) : error ? (
                <div className="text-center py-8 text-red-200">{error}</div>
              ) : currentSections.length === 0 ? (
                <div className="text-center py-8 text-purple-200">暂无课程</div>
              ) : (
                currentSections.map((section, index) => (
                <div key={section.id} className="flex items-center gap-3">
                  {/* Timeline dot - absolutely positioned relative to outer container */}
                  <div
                    ref={index === 0 ? firstDotRef : index === currentSections.length - 1 ? lastDotRef : null}
                    className="absolute w-3.5 h-3.5 rounded-full bg-cyan-300 shadow-lg z-10 -translate-x-1/2"
                    style={{ left: "31px", top: "calc((24px + (index) * (80px)) + 16px)" }}
                  />

                  {/* Section card */}
                  <div 
                    onClick={() => {
                      if (onEnterProject && section.courseId) {
                        console.log('进入课程:', section.courseId, section)
                        onEnterProject(section.courseId)
                      } else {
                        showFeatureToast('课程ID缺失，无法进入课程')
                      }
                    }}
                    className="bg-gradient-to-r from-violet-400/60 to-violet-400/60  rounded-[2.5rem] p-4 flex items-center gap-3 backdrop-blur-lg border border-purple-400/30 hover:border-purple-300/50 hover:from-purple-600/40 hover:to-purple-500/20 transition-all duration-300 group shadow-lg hover:shadow-xl flex-1 mx-8 cursor-pointer"
                  >
                    {/* Section icon and content */}
                    <div className="flex items-center gap-3 flex-1 min-w-0 mx-3">
                      <div className="text-4xl filter drop-shadow-md flex-shrink-0">{section.icon}</div>
                      <div className="flex-1 flex items-center gap-6">
                        <div className="font-bold text-2xl text-white">{section.title}</div>
                        {section.subtitle && (
                          <div className="flex items-center gap-1">
                            <svg className="w-4 h-4 text-white fill-white" viewBox="0 0 24 24">
                              <path d="M5 3v18l15-9L5 3z" />
                            </svg>
                            <div className="text-sm text-purple-200/70 font-medium">{section.subtitle}</div>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Star ratings */}
                    <div className="flex items-center gap-1 flex-shrink-0 mx-6">
                      {[...Array(3)].map((_, i) => (
                        <Star
                          key={i}
                          className={`w-8 h-8 transition duration-200 ${
                            i < section.rating ? "fill-yellow-300 text-yellow-300 drop-shadow-md" : "fill-purple-300 text-purple-300"
                          }`}
                        />
                      ))}
                    </div>
                  </div>
                </div>
                ))
              )}
            </div>
          </div>
        </div>

        <div className="w-full lg:w-80 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-1 gap-5">
          {features.map((feature, idx) => (
            <button
              type="button"
              key={feature.id || idx}
              onClick={() => feature.action ? feature.action() : showFeatureToast("该功能暂未开放~")}
              className={`w-full bg-gradient-to-br ${feature.color} rounded-xl p-0 text-center text-purple-900 font-semibold text-base shadow-2xl hover:shadow-xl transition-all duration-300 transform hover:scale-105 hover:-translate-y-1 cursor-pointer min-h-48 flex flex-col items-center justify-center border border-white/20 overflow-hidden focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-200/70`}
            >
              {feature.custom ? (
                <div className="flex items-center justify-center w-full h-full gap-3 px-3">
                  {/* 左边：小机器人 */}
                  <svg className="w-16 h-16 shrink-0" viewBox="0 0 80 80">
                    <circle cx="40" cy="38" r="22" fill="white" opacity="0.25"/>
                    <rect x="26" y="16" width="28" height="22" rx="6" fill="white" opacity="0.9"/>
                    <circle cx="33" cy="26" r="4" fill="#7C3AED"/>
                    <circle cx="47" cy="26" r="4" fill="#7C3AED"/>
                    <circle cx="34" cy="26" r="1.5" fill="white"/>
                    <circle cx="48" cy="26" r="1.5" fill="white"/>
                    <rect x="33" y="32" width="14" height="2" rx="1" fill="#7C3AED"/>
                    <rect x="28" y="15" width="8" height="3" rx="1.5" fill="white" opacity="0.6"/>
                    <rect x="44" y="15" width="8" height="3" rx="1.5" fill="white" opacity="0.6"/>
                    <rect x="35" y="38" width="10" height="12" rx="3" fill="white" opacity="0.7"/>
                    <rect x="33" y="50" width="5" height="8" rx="2" fill="white" opacity="0.5"/>
                    <rect x="42" y="50" width="5" height="8" rx="2" fill="white" opacity="0.5"/>
                    <circle cx="29" cy="32" r="3" fill="#F472B6" opacity="0.2"/>
                    <circle cx="51" cy="32" r="3" fill="#F472B6" opacity="0.2"/>
                  </svg>
                  {/* 中间：文字（放大居中） */}
                  <div className="flex flex-col items-center justify-center flex-1">
                    <span className="text-5xl font-black text-white drop-shadow-2xl tracking-[0.15em] leading-none">AI</span>
                    <span className="text-3xl font-bold text-white drop-shadow-xl tracking-[0.25em] leading-tight">
                      {feature.title.replace(/^AI\s*/, '').split('').join(' ')}
                    </span>
                    <div className="flex items-center gap-1.5 mt-1.5">
                      <div className="w-2 h-2 rounded-full bg-white/60" />
                      <div className="w-2 h-2 rounded-full bg-white/40" />
                      <div className="w-2 h-2 rounded-full bg-white/20" />
                    </div>
                  </div>
                  {/* 右边：星光 */}
                  <div className="flex flex-col items-center gap-1.5 shrink-0">
                    <svg className="w-6 h-6 text-yellow-200/70" viewBox="0 0 20 20" fill="currentColor">
                      <path d="M10 0l1.5 6.5L18 7l-5.5 3.5L13 17l-3-4.5L7 17l.5-6.5L2 7l6.5-.5z"/>
                    </svg>
                    <svg className="w-4 h-4 text-blue-200/50" viewBox="0 0 20 20" fill="currentColor">
                      <path d="M10 0l1.5 6.5L18 7l-5.5 3.5L13 17l-3-4.5L7 17l.5-6.5L2 7l6.5-.5z"/>
                    </svg>
                    <svg className="w-5 h-5 text-pink-200/50" viewBox="0 0 20 20" fill="currentColor">
                      <path d="M10 0l1.5 6.5L18 7l-5.5 3.5L13 17l-3-4.5L7 17l.5-6.5L2 7l6.5-.5z"/>
                    </svg>
                  </div>
                </div>
              ) : feature.emoji ? (
                <span className="text-5xl">{feature.emoji}</span>
              ) : feature.icon ? (
                <img src={feature.icon} alt={feature.title} className="w-full h-full object-cover" />
              ) : (
                <span>{feature.title}</span>
              )}
            </button>
          ))}
        </div>
      </div>

      {/* 气泡提示 */}
      {showToast && (
        <div className="fixed inset-0 flex items-center justify-center z-50 pointer-events-none">
          <div className="relative animate-bounce">
            {/* 气泡主体 */}
            <div className="bg-gradient-to-br from-[#7B5ADB] via-[#6B47D0] to-[#4E7FDB] text-white px-10 py-8 rounded-full shadow-2xl border-2 border-purple-300/50 backdrop-blur-md font-bold text-xl text-center max-w-xs relative"
              style={{
                boxShadow: "0 0 30px rgba(167, 139, 250, 0.6), 0 10px 40px rgba(0, 0, 0, 0.3)"
              }}
            >
              ✨ {toastMessage}
              
              {/* 装饰光晕 */}
              <div className="absolute inset-0 rounded-full bg-white/10 animate-pulse" />
            </div>
          </div>
        </div>
      )}

     
    </div>
  )
}
