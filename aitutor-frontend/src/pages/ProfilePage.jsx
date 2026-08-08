import React, { useEffect, useMemo, useState } from 'react'
import { getAllStages, getGradesByStage } from '../services/educationService'
import { updateUserProfile, getUserProfile } from '../services/authService'
import { getUserInfo, saveUserInfo, inferStageCodeFromGrade } from '../utils/tokenManager'

const defaultProfile = {
  name: '小明同学',
  stageName: '小学',
  grade: '一年级',
  subjects: ['语文', '数学', '英语'],
  motto: '坚持学习，每天进步一点点 ✨',
}

const ProfilePage = ({ onBack }) => {
  const [profile, setProfile] = useState(defaultProfile)
  const [isEditing, setIsEditing] = useState(false)
  const [formValues, setFormValues] = useState(defaultProfile)
  const [saving, setSaving] = useState(false)

  // 教育阶段/年级相关状态
  const [stages, setStages] = useState([])
  const [grades, setGrades] = useState([])
  const [selectedStage, setSelectedStage] = useState(null) // { stageCode, stageName }
  const [selectedGrade, setSelectedGrade] = useState(null) // { gradeCode, gradeName }

  const subjectsText = useMemo(() => profile.subjects.join(' · '), [profile.subjects])

  // 初始化时从 localStorage 读取用户信息
  useEffect(() => {
    const loadUserProfile = async () => {
      // 先从后端获取最新的用户信息
      let userInfo = null
      try {
        userInfo = await getUserProfile()
        console.log('[ProfilePage初始化] 从后端获取的用户信息:', userInfo)
      } catch (err) {
        console.warn('[ProfilePage初始化] 从后端获取用户信息失败，使用本地缓存:', err)
        userInfo = getUserInfo()
      }
      
      if (!userInfo) {
        userInfo = getUserInfo()
      }
      
      if (userInfo) {
        console.log('[ProfilePage初始化] 最终使用的用户信息:', userInfo)
        
        // 加载阶段列表，以便根据 stageCode 查找 stageName
        let stageName = userInfo.stageName
        let gradeName = userInfo.gradeName || userInfo.grade
        
        try {
          const stagesList = await getAllStages()
          const stageCode = userInfo.stage || userInfo.stageCode
          
          if (stageCode && !stageName) {
            const stageObj = stagesList.find(s => s.stageCode === stageCode)
            if (stageObj) {
              stageName = stageObj.stageName
              console.log('[ProfilePage初始化] 根据 stageCode 找到阶段名称:', stageName)
            }
          }
          
          // 如果有年级代码，尝试查找年级名称
          const gradeCode = userInfo.grade || userInfo.gradeCode
          if (gradeCode && stageCode && (!gradeName || gradeName === gradeCode)) {
            try {
              const gradesList = await getGradesByStage(stageCode)
              const gradeObj = gradesList.find(g => g.gradeCode === gradeCode)
              if (gradeObj) {
                gradeName = gradeObj.gradeName
                console.log('[ProfilePage初始化] 根据 gradeCode 找到年级名称:', gradeName)
              }
            } catch (err) {
              console.warn('[ProfilePage初始化] 加载年级列表失败:', err)
            }
          }
        } catch (err) {
          console.warn('[ProfilePage初始化] 加载阶段列表失败:', err)
        }
        
        setProfile({
          name: userInfo.realName || userInfo.name || userInfo.username || userInfo.studentName || defaultProfile.name,
          stageName: stageName || defaultProfile.stageName,
          grade: gradeName || defaultProfile.grade,
          subjects: userInfo.subjects || defaultProfile.subjects,
          motto: userInfo.motto || defaultProfile.motto,
        })
      }
    }
    
    loadUserProfile()
  }, [])

  const handleOpenEdit = () => {
    setFormValues({
      name: profile.name,
      stageName: profile.stageName,
      grade: profile.grade,
      subjects: profile.subjects.join('，'),
      motto: profile.motto,
    })
    setIsEditing(true)
  }

  const handleCloseEdit = () => {
    if (!saving) setIsEditing(false)
  }

  const handleChange = (field, value) => {
    setFormValues((prev) => ({ ...prev, [field]: value }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSaving(true)

    try {
      const current = getUserInfo() || {}
      const payload = {}
      
      // 构建请求 payload，使用后端接口的字段名：stage 和 grade
      if (selectedStage?.stageCode) {
        payload.stage = selectedStage.stageCode // stageCode 对应后端的 stage 字段
      }
      if (selectedGrade?.gradeCode) {
        payload.grade = selectedGrade.gradeCode // gradeCode 对应后端的 grade 字段
      }
      
      // 如果有姓名修改，也添加到 payload
      if (formValues.name.trim()) {
        payload.studentName = formValues.name.trim()
      }
      
      console.log('[ProfilePage保存] 当前用户信息:', current)
      console.log('[ProfilePage保存] 选中的阶段:', selectedStage)
      console.log('[ProfilePage保存] 选中的年级对象:', selectedGrade)
      console.log('[ProfilePage保存] selectedGrade?.gradeCode:', selectedGrade?.gradeCode)
      console.log('[ProfilePage保存] selectedGrade?.gradeName:', selectedGrade?.gradeName)
      console.log('[ProfilePage保存] 准备发送给后端的 payload:', payload)
      
      // 验证 payload 中的数据是否正确
      if (payload.grade && payload.grade === payload.stage) {
        console.error('[ProfilePage保存] 错误：grade 和 stage 的值相同！', payload)
        alert('数据错误：年级和阶段的值相同，请重新选择')
        setSaving(false)
        return
      }
      
      // 验证 gradeCode 是否是有效的年级代码（应该是 GRADE_X 格式，不应该是阶段代码）
      if (payload.grade) {
        const validStageCodes = ['PRIMARY', 'JUNIOR', 'SENIOR']
        if (validStageCodes.includes(payload.grade)) {
          console.error('[ProfilePage保存] 错误：grade 的值是阶段代码而不是年级代码！', {
            grade: payload.grade,
            selectedGrade: selectedGrade,
            grades: grades
          })
          alert(`数据错误：年级代码 "${payload.grade}" 是阶段代码，不是年级代码。请重新选择年级。`)
          setSaving(false)
          return
        }
        
        // 验证是否是有效的年级代码格式（GRADE_X）
        if (!payload.grade.startsWith('GRADE_')) {
          console.warn('[ProfilePage保存] 警告：grade 的值不是标准的 GRADE_X 格式', payload.grade)
        }
      }
      
      // 调用后端接口更新用户信息（即使只修改了姓名也要保存）
      if (Object.keys(payload).length > 0) {
        const updated = await updateUserProfile(payload)
        console.log('[ProfilePage保存] 后端返回的更新数据:', updated)
        
        // 从 stages 和 grades 列表中查找对应的名称
        const finalStageCode = updated?.stage || selectedStage?.stageCode || current.stageCode || current.stage
        const finalGradeCode = updated?.grade || selectedGrade?.gradeCode || current.gradeCode || current.grade
        
        // 查找阶段名称
        let finalStageName = selectedStage?.stageName || current.stageName
        if (finalStageCode && stages.length > 0) {
          const stageObj = stages.find(s => s.stageCode === finalStageCode)
          if (stageObj) {
            finalStageName = stageObj.stageName
          }
        }
        
        // 查找年级名称
        let finalGradeName = selectedGrade?.gradeName || current.gradeName
        if (finalGradeCode) {
          // 先尝试从当前 grades 列表中查找
          const gradeObj = grades.find(g => g.gradeCode === finalGradeCode)
          if (gradeObj) {
            finalGradeName = gradeObj.gradeName
          } else if (finalStageCode) {
            // 如果当前 grades 列表中没有，尝试重新加载对应阶段的年级列表
            try {
              const gradeList = await getGradesByStage(finalStageCode)
              const foundGrade = gradeList.find(g => g.gradeCode === finalGradeCode)
              if (foundGrade) {
                finalGradeName = foundGrade.gradeName
              }
            } catch (err) {
              console.warn('加载年级列表失败，使用当前年级名称:', err)
            }
          }
        }
        
        // 更新本地存储的用户信息
        const updatedUserInfo = {
          ...current,
          // 阶段和年级相关字段
          stageCode: finalStageCode,
          gradeCode: finalGradeCode,
          stage: finalStageCode, // 同时保存 stage 字段以兼容后端
          grade: finalGradeCode, // 同时保存 grade 字段以兼容后端
          stageName: finalStageName,
          gradeName: finalGradeName,
          // 其他字段
          realName: updated?.studentName || formValues.name.trim() || current.realName || current.name,
          name: updated?.studentName || formValues.name.trim() || current.name,
          studentName: updated?.studentName || formValues.name.trim() || current.studentName,
          email: updated?.email || current.email,
          phone: updated?.phone || current.phone,
          // 合并后端返回的所有字段
          ...updated,
        }
        
        saveUserInfo(updatedUserInfo)
        console.log('[ProfilePage保存] 已更新本地存储的用户信息:', updatedUserInfo)
      }

      const subjects = formValues.subjects
        .split(/[，,]/)
        .map((item) => item.trim())
        .filter(Boolean)

      // 更新界面显示
      setProfile({
        name: formValues.name.trim() || defaultProfile.name,
        stageName: selectedStage?.stageName || formValues.stageName || defaultProfile.stageName,
        grade: selectedGrade?.gradeName || formValues.grade?.trim() || defaultProfile.grade,
        subjects: subjects.length ? subjects : defaultProfile.subjects,
        motto: formValues.motto.trim() || defaultProfile.motto,
      })
    } catch (err) {
      console.error('保存用户信息失败:', err)
      alert('保存失败，请稍后重试')
    } finally {
      setSaving(false)
      setIsEditing(false)
    }
  }

  // 加载教育阶段
  useEffect(() => {
    const loadStages = async () => {
      try {
        const list = await getAllStages()
        setStages(list || [])
      } catch (err) {
        console.error('获取教育阶段失败:', err)
        setStages([])
      }
    }
    loadStages()
  }, [])

  // 打开编辑时，初始化阶段选择（根据用户当前的 stage 或 stageCode）
  useEffect(() => {
    if (!isEditing || !stages.length) return
    
    const userInfo = getUserInfo() || {}
    // 优先使用 stageCode，如果没有则使用 stage 字段，再没有则根据 stageName 匹配
    const userStageCode = userInfo.stageCode || userInfo.stage
    
    let stage = null
    if (userStageCode) {
      // 根据 stageCode 查找
      stage = stages.find((s) => s.stageCode === userStageCode)
    }
    
    // 如果没找到，尝试根据 stageName 匹配
    if (!stage) {
      stage = stages.find((s) => s.stageName === (profile.stageName || formValues.stageName))
    }
    
    // 如果还是没找到，选择第一个
    if (!stage && stages.length > 0) {
      stage = stages[0]
    }
    
    setSelectedStage(stage)
    console.log('[ProfilePage] 初始化阶段选择:', stage)
  }, [isEditing, stages]) // eslint-disable-line react-hooks/exhaustive-deps

  // 阶段变化时，加载对应年级并初始化年级选择（根据用户当前的 grade 或 gradeCode）
  useEffect(() => {
    if (!isEditing || !selectedStage?.stageCode) return
    
    const loadGrades = async () => {
      try {
        const list = await getGradesByStage(selectedStage.stageCode)
        console.log('[ProfilePage] 加载的年级列表:', list)
        console.log('[ProfilePage] 第一个年级对象结构:', list?.[0])
        setGrades(list || [])
        
        const userInfo = getUserInfo() || {}
        // 优先使用 gradeCode，如果没有则使用 grade 字段，再没有则根据 gradeName 匹配
        const userGradeCode = userInfo.gradeCode || userInfo.grade
        console.log('[ProfilePage] 用户当前的 gradeCode:', userGradeCode)
        console.log('[ProfilePage] 用户当前的 grade:', userInfo.grade)
        
        let grade = null
        if (userGradeCode) {
          // 根据 gradeCode 查找
          grade = list.find((g) => g.gradeCode === userGradeCode)
          console.log('[ProfilePage] 根据 gradeCode 查找结果:', grade)
          
          // 如果没找到，检查是否是 stageCode 被错误地保存为 gradeCode
          if (!grade && userGradeCode === userInfo.stageCode) {
            console.warn('[ProfilePage] 警告：gradeCode 和 stageCode 相同，可能是数据错误')
          }
        }
        
        // 如果没找到，尝试根据 gradeName 匹配
        if (!grade) {
          grade = list.find((g) => g.gradeName === (profile.grade || formValues.grade))
          console.log('[ProfilePage] 根据 gradeName 查找结果:', grade)
        }
        
        // 如果还是没找到，选择第一个
        if (!grade && list.length > 0) {
          grade = list[0]
          console.log('[ProfilePage] 使用第一个年级:', grade)
        }
        
        // 验证 grade 对象的结构
        if (grade) {
          console.log('[ProfilePage] 设置的年级对象:', {
            gradeCode: grade.gradeCode,
            gradeName: grade.gradeName,
            fullObject: grade
          })
          
          // 如果 gradeCode 和 stageCode 相同，说明数据有问题
          if (grade.gradeCode === selectedStage.stageCode) {
            console.error('[ProfilePage] 错误：年级对象的 gradeCode 和阶段 stageCode 相同！', {
              grade,
              stage: selectedStage
            })
          }
        }
        
        setSelectedGrade(grade)
      } catch (err) {
        console.error('获取年级列表失败:', err)
        setGrades([])
        setSelectedGrade(null)
      }
    }
    loadGrades()
  }, [isEditing, selectedStage]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div
      className="min-h-screen w-full text-white overflow-hidden"
      style={{
        backgroundImage:
          'linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%, rgb(86,43,205) 80%, rgb(47,8,154) 100%)',
      }}
    >
      {/* 顶部栏 */}
      <header className="px-8 py-6 flex items-center justify-between border-b border-purple-400/20">
        <div className="flex items-center gap-3">
          <span className="text-sm font-semibold text-purple-200/80 tracking-wide">LeapMind</span>
          <span className="ml-6 text-2xl font-extrabold drop-shadow">个人主页</span>
        </div>
        <button
          onClick={onBack}
          className="rounded-full bg-white/10 px-5 py-2 text-sm font-semibold text-white/90 border border-white/20 backdrop-blur hover:bg-white/15 transition"
        >返回</button>
      </header>

      {/* 内容区域 */}
      <div className="mx-auto max-w-6xl p-6 lg:p-10">
        {/* 顶部卡片：头像 + 基本信息 */}
        <div className="relative bg-gradient-to-b from-purple-900/40 to-purple-800/20 rounded-3xl p-8 border border-purple-500/20 backdrop-blur-lg shadow-2xl">
          <div className="flex flex-col md:flex-row items-center md:items-end gap-6">
            <img
              src="./login/avatar.png"
              alt="avatar"
              className="w-28 h-28 rounded-full object-cover shadow-xl ring-4 ring-white/10"
            />
            <div className="flex-1">
              <div className="flex items-center gap-4 flex-wrap">
                <h2 className="text-3xl font-black">{profile.name}</h2>
                <span className="px-3 py-1 text-xs rounded-full bg-white/10 border border-white/20">{profile.stageName}</span>
                <span className="px-3 py-1 text-xs rounded-full bg-white/10 border border-white/20">{profile.grade}</span>
                <span className="px-3 py-1 text-xs rounded-full bg-white/10 border border-white/20">{subjectsText}</span>
              </div>
              <p className="mt-2 text-purple-200/80">{profile.motto}</p>
            </div>
            <div className="flex items-center gap-3">
              <button
                onClick={handleOpenEdit}
                className="rounded-full bg-gradient-to-r from-[#A286FF] to-[#638AFF] px-6 py-2 font-bold shadow-lg hover:shadow-purple-500/40"
              >
                编辑资料
              </button>
            </div>
          </div>
        </div>

        {/* 统计面板 */}
        <div className="mt-8 grid grid-cols-1 md:grid-cols-3 gap-6">
          {[
            { label: '本周学习时长', value: '5h 20m' },
            { label: '完成小节', value: '18' },
            { label: '连续学习天数', value: '7天' },
          ].map((s, i) => (
            <div key={i} className="rounded-2xl bg-[#4210A5]/60 border border-purple-400/30 p-6 backdrop-blur-lg shadow-xl">
              <div className="text-purple-200/80 text-sm">{s.label}</div>
              <div className="mt-2 text-3xl font-extrabold">{s.value}</div>
            </div>
          ))}
        </div>

        {/* 最近学习记录 */}
        <div className="mt-8 rounded-3xl bg-[#4210A5]/60 border border-purple-400/30 p-6 backdrop-blur-lg shadow-xl">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-xl font-bold">最近学习</h3>
            <button className="text-sm text-purple-200/80 hover:text-white">查看全部</button>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {[
              { title: '语文 第一单元 · 第3课 口语交际', progress: 76 },
              { title: '数学 第二单元 · 第9课 混合运算', progress: 42 },
              { title: '英语 第一单元 · Unit 6 Listening', progress: 58 },
              { title: '物理 第一单元 · 第4课 牛顿运动定律', progress: 30 },
            ].map((item, idx) => (
              <div key={idx} className="rounded-2xl border border-purple-400/30 bg-white/5 p-4">
                <div className="font-semibold">{item.title}</div>
                <div className="mt-3 h-3 w-full rounded-full bg-white/10 overflow-hidden">
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-[#A286FF] via-[#896BFF] to-[#638AFF]"
                    style={{ width: `${item.progress}%` }}
                  />
                </div>
                <div className="mt-1 text-right text-xs text-purple-200/80">{item.progress}%</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* 编辑资料弹窗 */}
      {isEditing && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
          <div className="w-full max-w-lg rounded-3xl border border-white/20 bg-[#27106D]/95 p-8 shadow-2xl">
            <h2 className="text-2xl font-semibold text-white">编辑资料</h2>
            <p className="mt-1 text-sm text-purple-200/70">更新你的基本信息，帮助系统提供更精准的学习推荐。</p>

            <form onSubmit={handleSubmit} className="mt-6 space-y-5">
              <div>
                <label className="mb-2 block text-sm font-semibold text-purple-100/90" htmlFor="profile-name">
                  姓名 / 昵称
                </label>
                <input
                  id="profile-name"
                  type="text"
                  value={formValues.name}
                  onChange={(e) => handleChange('name', e.target.value)}
                  className="w-full rounded-2xl border border-white/20 bg-white/10 px-4 py-3 text-white placeholder-purple-200/60 outline-none focus:border-white focus:ring-2 focus:ring-white/40"
                  placeholder="请输入姓名或昵称"
                />
              </div>

              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <div>
                  <label className="mb-2 block text-sm font-semibold text-purple-100/90" htmlFor="profile-stage">
                    教育阶段
                  </label>
                  <select
                    id="profile-stage"
                    value={selectedStage?.stageCode || ''}
                    onChange={(e) => {
                      const next = stages.find((s) => s.stageCode === e.target.value) || null
                      setSelectedStage(next)
                      setSelectedGrade(null)
                    }}
                    className="w-full rounded-2xl border border-white/30 bg-white/20 px-4 py-3 text-white font-medium outline-none focus:border-white/50 focus:bg-white/30 focus:ring-2 focus:ring-white/40 cursor-pointer"
                    style={{ color: '#ffffff' }}
                  >
                    <option value="" disabled className="bg-[#27106D] text-white">请选择阶段</option>
                    {stages.map((s) => (
                      <option key={s.stageCode} value={s.stageCode} className="bg-[#27106D] text-white">
                        {s.stageName}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="mb-2 block text-sm font-semibold text-purple-100/90" htmlFor="profile-grade">
                    年级
                  </label>
                  <select
                    id="profile-grade"
                    value={selectedGrade?.gradeCode || ''}
                    onChange={(e) => {
                      const next = grades.find((g) => g.gradeCode === e.target.value) || null
                      setSelectedGrade(next)
                    }}
                    disabled={!grades.length}
                    className="w-full rounded-2xl border border-white/30 bg-white/20 px-4 py-3 text-white font-medium outline-none focus:border-white/50 focus:bg-white/30 focus:ring-2 focus:ring-white/40 disabled:opacity-60 disabled:cursor-not-allowed cursor-pointer"
                    style={{ color: '#ffffff' }}
                  >
                    <option value="" disabled className="bg-[#27106D] text-white">{grades.length ? '请选择年级' : '请先选择阶段'}</option>
                    {grades.map((g) => (
                      <option key={g.gradeCode} value={g.gradeCode} className="bg-[#27106D] text-white">
                        {g.gradeName}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <div>
                  <label className="mb-2 block text-sm font-semibold text-purple-100/90" htmlFor="profile-subjects">
                    主修科目
                  </label>
                  <input
                    id="profile-subjects"
                    type="text"
                    value={formValues.subjects}
                    onChange={(e) => handleChange('subjects', e.target.value)}
                    className="w-full rounded-2xl border border-white/20 bg-white/10 px-4 py-3 text-white placeholder-purple-200/60 outline-none focus:border-white focus:ring-2 focus:ring-white/40"
                    placeholder="语文，数学，英语"
                  />
                </div>
              </div>

              <div>
                <label className="mb-2 block text-sm font-semibold text-purple-100/90" htmlFor="profile-motto">
                  学习签名
                </label>
                <textarea
                  id="profile-motto"
                  rows={3}
                  value={formValues.motto}
                  onChange={(e) => handleChange('motto', e.target.value)}
                  className="w-full rounded-2xl border border-white/20 bg-white/10 px-4 py-3 text-white placeholder-purple-200/60 outline-none focus:border-white focus:ring-2 focus:ring-white/40"
                  placeholder="写一句激励自己的话"
                />
              </div>

              <div className="flex items-center justify-end gap-4 pt-2">
                <button
                  type="button"
                  onClick={handleCloseEdit}
                  disabled={saving}
                  className="rounded-full border border-white/20 px-5 py-2 text-sm font-semibold text-white/80 transition hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="rounded-full bg-gradient-to-r from-[#A286FF] to-[#638AFF] px-6 py-2 text-sm font-semibold text-white shadow-lg transition hover:shadow-purple-400/50 disabled:cursor-not-allowed disabled:opacity-70"
                >
                  {saving ? '保存中…' : '保存修改'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

export default ProfilePage


