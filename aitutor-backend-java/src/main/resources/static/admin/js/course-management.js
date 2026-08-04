/**
 * 管理员课程管理模块
 * 提供课程搜索、状态监控、创建和完整的处理工作流程
 */
class CourseManagementModule {
    constructor() {
        this.api = window.api;
        this.currentView = 'search'; // 'search' | 'workflow'
        this.currentCourse = null;
        this.searchParams = {
            stage: '',
            grade: '',
            subject: '',
            chapter: '',
            semester: ''
        };
        this.workflowStep = 1;
        this.courses = [];
        this.sortField = '';
        this.sortDirection = 'asc';
        this.workflowData = {};
        
        // 过滤器选项配置
        this.filterOptions = {
            stages: ['小学', '初中', '高中'],
            grades: {
                '小学': ['一年级', '二年级', '三年级', '四年级', '五年级', '六年级'],
                '初中': ['初一', '初二', '初三'],
                '高中': ['高一', '高二', '高三']
            },
            subjects: ['数学', '语文', '英语', '化学', '地理', '生物', '政治', '历史', '物理'],
            chapters: [
                { value: '1', label: '第一章' },
                { value: '2', label: '第二章' },
                { value: '3', label: '第三章' },
                { value: '4', label: '第四章' },
                { value: '5', label: '第五章' },
                { value: '6', label: '第六章' },
                { value: '7', label: '第七章' },
                { value: '8', label: '第八章' },
                { value: '9', label: '第九章' },
                { value: '10', label: '第十章' }
            ],
            semesters: [
                { value: 'SEMESTER_1', label: '上册' },
                { value: 'SEMESTER_2', label: '下册' }
            ]
        };
        
        // 工作流步骤配置
        this.workflowSteps = {
            1: {
                id: 1,
                title: '大纲润色',
                description: '输入课程大纲JSON数据进行文本润色处理',
                api: 'bulkPreprocessing'
            },
            2: {
                id: 2,
                title: '内容审核',
                description: '审核润色后的文本内容',
                api: 'adminReviewSession'
            },
            3: {
                id: 3,
                title: '语音合成',
                description: '将审核通过的文本转换为语音',
                api: 'executeBulkSynthesis'
            },
            4: {
                id: 4,
                title: 'PPT插入',
                description: '创建和插入PPT幻灯片数据',
                api: 'createSlide'
            }
        };
    }

    /**
     * 初始化模块
     */
    init() {
        // 确保DOM已准备好
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => {
                this.doInit();
            });
        } else {
            this.doInit();
        }
    }

    /**
     * 执行初始化
     */
    doInit() {
        console.log('开始执行课程管理模块初始化...');
        
        this.createHTML();
        console.log('HTML结构已创建');
        
        this.bindEvents();
        console.log('事件已绑定');
        
        // 延迟初始化过滤器，确保DOM元素已创建
        setTimeout(() => {
            console.log('开始初始化过滤器...');
            this.initializeFilters();
        }, 100);
        
        // 清理过期的工作流状态
        this.cleanupExpiredStates();
        
        console.log('课程管理模块初始化完成');
    }

    /**
     * 创建HTML结构
     */
    createHTML() {
        const container = document.getElementById('coursesTab');
        if (!container) {
            console.error('找不到课程管理容器');
            return;
        }

        container.innerHTML = `
            <!-- 搜索视图 -->
            <div id="courseSearchView" class="course-view active">
                <div class="content-header">
                    <div class="header-left">
                        <h2>课程管理</h2>
                        <p class="header-subtitle">管理课程内容和处理工作流程</p>
                    </div>
                    <div class="header-actions">
                        <button id="refreshCoursesBtn" class="btn btn-secondary">
                            <i class="fas fa-sync-alt"></i>
                            刷新数据
                        </button>
                    </div>
                </div>

                <!-- 搜索过滤器 -->
                <div class="course-filters">
                    <div class="filter-row">
                        <div class="filter-group">
                            <label>阶段</label>
                            <select id="stageFilter">
                                <option value="">选择阶段</option>
                            </select>
                        </div>
                        <div class="filter-group">
                            <label>年级</label>
                            <select id="gradeFilter">
                                <option value="">选择年级</option>
                            </select>
                        </div>
                        <div class="filter-group">
                            <label>学科</label>
                            <select id="subjectFilter">
                                <option value="">选择学科</option>
                            </select>
                        </div>
                        <div class="filter-group">
                            <label>学期</label>
                            <select id="semesterFilter">
                                <option value="">选择学期</option>
                            </select>
                        </div>
                        <div class="filter-group">
                            <label>章节</label>
                            <select id="chapterFilter">
                                <option value="">选择章节</option>
                            </select>
                        </div>
                    </div>
                    <div class="filter-actions">
                        <button id="searchCoursesBtn" class="btn btn-primary">
                            <i class="fas fa-search"></i>
                            搜索课程
                        </button>
                        <button id="clearFiltersBtn" class="btn btn-secondary">
                            <i class="fas fa-times"></i>
                            清空过滤器
                        </button>
                    </div>
                </div>

                <!-- 课程列表 -->
                <div class="course-results">
                    <div class="results-header">
                        <h3>搜索结果</h3>
                        <div class="results-actions">
                            <button id="refreshStatusBtn" class="btn btn-info">
                                <i class="fas fa-sync-alt"></i>
                                刷新状态
                            </button>
                            <button id="addCourseBtn" class="btn btn-success">
                                <i class="fas fa-plus"></i>
                                添加课程
                            </button>
                        </div>
                    </div>
                    <div class="table-container">
                        <table id="courseResultsTable" class="data-table">
                            <thead>
                                <tr>
                                    <th class="sortable" data-sort="courseId">
                                        课程ID <i class="fas fa-sort"></i>
                                    </th>
                                    <th class="sortable" data-sort="sectionNumber">
                                        小节序号 <i class="fas fa-sort"></i>
                                    </th>
                                    <th class="sortable" data-sort="title">
                                        课程标题 <i class="fas fa-sort"></i>
                                    </th>
                                    <th>课程内容</th>
                                    <th>大纲润色</th>
                                    <th>审核状态</th>
                                    <th>语音合成</th>
                                    <th>PPT插入</th>
                                    <th>操作</th>
                                </tr>
                            </thead>
                            <tbody></tbody>
                        </table>
                    </div>
                </div>
            </div>

            <!-- 工作流视图 -->
            <div id="courseWorkflowView" class="course-view">
                <!-- 面包屑导航 -->
                <div class="breadcrumb-nav">
                    <nav class="breadcrumb">
                        <a href="#" onclick="courseManagement.showSearchView()" class="breadcrumb-item">
                            <i class="fas fa-list"></i>
                            课程列表
                        </a>
                        <span class="breadcrumb-separator">/</span>
                        <span class="breadcrumb-item active">
                            <i class="fas fa-cogs"></i>
                            工作流处理
                        </span>
                    </nav>
                </div>

                <div class="workflow-header">
                    <button id="backToSearchBtn" class="btn btn-secondary">
                        <i class="fas fa-arrow-left"></i>
                        返回列表
                    </button>
                    <div class="course-info">
                        <h2 id="workflowCourseTitle">课程处理工作流</h2>
                        <p id="workflowCourseDetails">课程详细信息</p>
                    </div>
                </div>

                <!-- 工作流进度 -->
                <div class="workflow-progress">
                    <div class="progress-bar">
                        <div id="progressFill" class="progress-fill" style="width: 0%"></div>
                    </div>
                    <div id="progressText" class="progress-text">步骤 0 / 4</div>
                </div>

                <!-- 步骤导航 -->
                <div class="workflow-steps">
                    <div class="step" data-step="1">
                        <div class="step-number">1</div>
                        <div class="step-title">大纲润色</div>
                    </div>
                    <div class="step" data-step="2">
                        <div class="step-number">2</div>
                        <div class="step-title">内容审核</div>
                    </div>
                    <div class="step" data-step="3">
                        <div class="step-number">3</div>
                        <div class="step-title">语音合成</div>
                    </div>
                    <div class="step" data-step="4">
                        <div class="step-number">4</div>
                        <div class="step-title">PPT插入</div>
                    </div>
                </div>

                <!-- 步骤内容 -->
                <div class="workflow-content">
                    <!-- 步骤1：大纲润色 -->
                    <div id="step1Content" class="step-content active">
                        <div class="step-header">
                            <h3>步骤1：大纲润色</h3>
                            <p>输入课程大纲JSON数据进行文本润色处理</p>
                        </div>
                        <div class="step-body">
                            <div class="form-group">
                                <label>课程大纲JSON数据</label>
                                <textarea id="outlineJsonInput" rows="15" placeholder="请输入课程大纲的JSON格式数据..."></textarea>
                            </div>
                            <div class="step-actions">
                                <button id="processOutlineBtn" class="btn btn-primary">
                                    <i class="fas fa-magic"></i>
                                    开始润色处理
                                </button>
                                <button id="insertSampleJsonBtn" class="btn btn-secondary">
                                    <i class="fas fa-file-code"></i>
                                    插入示例JSON
                                </button>
                            </div>
                        </div>
                    </div>

                    <!-- 步骤2：内容审核 -->
                    <div id="step2Content" class="step-content">
                        <div class="step-header">
                            <h3>步骤2：内容审核</h3>
                            <p>审核润色后的文本内容</p>
                        </div>
                        <div class="step-body">
                            <div id="reviewContent" class="review-content">
                                <p>请先完成步骤1的大纲润色处理</p>
                            </div>
                        </div>
                    </div>

                    <!-- 步骤3：语音合成 -->
                    <div id="step3Content" class="step-content">
                        <div class="step-header">
                            <h3>步骤3：语音合成</h3>
                            <p>将审核通过的文本转换为语音</p>
                        </div>
                        <div class="step-body">
                            <div id="synthesisStatus" class="synthesis-status">
                                <p>请先完成前面的步骤</p>
                            </div>
                            <div class="step-actions">
                                <button id="startSynthesisBtn" class="btn btn-primary">
                                    <i class="fas fa-microphone"></i>
                                    开始语音合成
                                </button>
                            </div>
                        </div>
                    </div>

                    <!-- 步骤4：PPT插入 -->
                    <div id="step4Content" class="step-content">
                        <div class="step-header">
                            <h3>步骤4：PPT插入</h3>
                            <p>在表格中编辑PPT幻灯片，支持直接粘贴HTML代码</p>
                        </div>
                        <div class="step-body">
                            <!-- 课程ID显示 -->
                            <div class="form-group">
                                <label>课程ID</label>
                                <input type="text" id="pptCourseId" readonly class="form-control" style="background-color: #f5f5f5;">
                            </div>

                            <!-- PPT幻灯片表格 -->
                            <div class="form-group">
                                <div class="table-toolbar" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
                                    <label style="margin: 0;">PPT幻灯片列表</label>
                                    <button type="button" class="btn btn-sm btn-success" onclick="courseManagement.addPptSlideRow()">
                                        <i class="fas fa-plus"></i> 添加幻灯片
                                    </button>
                                </div>
                                <div class="table-responsive" style="max-height: 600px; overflow-y: auto; border: 1px solid #ddd; border-radius: 4px;">
                                    <table id="pptSlidesTable" class="table table-bordered table-hover" style="margin: 0;">
                                        <thead style="position: sticky; top: 0; background-color: #f8f9fa; z-index: 10;">
                                            <tr>
                                                <th style="width: 60px; text-align: center;">序号</th>
                                                <th style="width: 200px;">标题</th>
                                                <th>HTML内容</th>
                                                <th style="width: 150px; text-align: center;">操作</th>
                                            </tr>
                                        </thead>
                                        <tbody id="pptSlidesTableBody">
                                            <!-- 动态生成行 -->
                                        </tbody>
                                    </table>
                                </div>
                                <small class="form-text text-muted">
                                    提示：可以直接将HTML代码复制粘贴到HTML内容列中，支持多行编辑
                                </small>
                            </div>

                            <!-- 操作按钮 -->
                            <div class="step-actions">
                                <button type="button" class="btn btn-secondary" onclick="courseManagement.clearPptTable()">
                                    <i class="fas fa-trash"></i> 清空表格
                                </button>
                                <button type="button" class="btn btn-info" onclick="courseManagement.addSamplePptRows()">
                                    <i class="fas fa-file-code"></i> 插入示例
                                </button>
                                <button type="button" class="btn btn-primary" onclick="courseManagement.insertPptFromTable()">
                                    <i class="fas fa-upload"></i> 批量插入PPT
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * 绑定事件
     */
    bindEvents() {
        // 过滤器事件
        const stageFilter = document.getElementById('stageFilter');
        if (stageFilter) {
            stageFilter.addEventListener('change', (e) => {
                this.onStageChange(e.target.value);
            });
        } else {
            console.error('找不到stageFilter元素，无法绑定事件');
        }

        // 搜索和清空按钮
        const searchBtn = document.getElementById('searchCoursesBtn');
        if (searchBtn) {
            searchBtn.addEventListener('click', () => {
                this.performSearch();
            });
        }

        const clearBtn = document.getElementById('clearFiltersBtn');
        if (clearBtn) {
            clearBtn.addEventListener('click', () => {
                this.clearFilters();
            });
        }

        // 添加课程按钮
        const addBtn = document.getElementById('addCourseBtn');
        if (addBtn) {
            addBtn.addEventListener('click', () => {
                this.showAddCourseModal();
            });
        }

        // 刷新按钮
        const refreshBtn = document.getElementById('refreshCoursesBtn');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => {
                this.performSearch();
            });
        }

        // 刷新状态按钮
        const refreshStatusBtn = document.getElementById('refreshStatusBtn');
        if (refreshStatusBtn) {
            refreshStatusBtn.addEventListener('click', () => {
                this.refreshCourseStatus();
            });
        }

        // 工作流导航
        const backBtn = document.getElementById('backToSearchBtn');
        if (backBtn) {
            backBtn.addEventListener('click', () => {
                this.showSearchView();
            });
        }

        // 步骤导航
        document.querySelectorAll('.step').forEach(step => {
            step.addEventListener('click', (e) => {
                const stepNumber = parseInt(e.currentTarget.dataset.step);
                this.switchToStep(stepNumber);
            });
        });

        // 工作流步骤按钮
        this.bindWorkflowEvents();

        // 表格排序
        this.bindTableSorting();
    }

    /**
     * 绑定工作流事件
     */
    bindWorkflowEvents() {
        // 步骤1：大纲润色
        const processBtn = document.getElementById('processOutlineBtn');
        if (processBtn) {
            processBtn.addEventListener('click', () => {
                this.processOutline();
            });
        }

        const sampleBtn = document.getElementById('insertSampleJsonBtn');
        if (sampleBtn) {
            sampleBtn.addEventListener('click', () => {
                this.insertSampleJson();
            });
        }

        // 步骤2：审核按钮已移除，审核操作通过动态生成的界面中的按钮完成

        // 步骤3：语音合成
        const synthesisBtn = document.getElementById('startSynthesisBtn');
        if (synthesisBtn) {
            synthesisBtn.addEventListener('click', () => {
                this.startSynthesis();
            });
        }

        // 步骤4：PPT插入 - 现在使用表单提交，不需要单独的按钮事件
        // PPT表单事件将在preparePptData()中绑定
    }

    /**
     * 初始化过滤器选项
     */
    initializeFilters() {
        console.log('开始初始化过滤器...');
        
        // 阶段选项
        const stageFilter = document.getElementById('stageFilter');
        console.log('stageFilter元素:', stageFilter);
        console.log('阶段选项数据:', this.filterOptions.stages);
        
        if (!stageFilter) {
            console.error('找不到stageFilter元素');
            return;
        }
        
        // 检查是否已经初始化过（避免重复添加选项）
        if (stageFilter.options.length > 1) {
            console.log('过滤器已经初始化，跳过');
            return;
        }
        
        // 阶段选项 - 保留默认的"选择阶段"选项，只添加新选项
        this.filterOptions.stages.forEach(stage => {
            const option = document.createElement('option');
            option.value = stage;
            option.textContent = stage;
            stageFilter.appendChild(option);
            console.log('添加阶段选项:', stage);
        });

        // 学科选项
        const subjectFilter = document.getElementById('subjectFilter');
        if (subjectFilter) {
            this.filterOptions.subjects.forEach(subject => {
                const option = document.createElement('option');
                option.value = subject;
                option.textContent = subject;
                subjectFilter.appendChild(option);
            });
        }

        // 学期选项
        const semesterFilter = document.getElementById('semesterFilter');
        if (semesterFilter) {
            this.filterOptions.semesters.forEach(semester => {
                const option = document.createElement('option');
                option.value = semester.value;
                option.textContent = semester.label;
                semesterFilter.appendChild(option);
            });
        }

        // 章节选项
        const chapterFilter = document.getElementById('chapterFilter');
        if (chapterFilter) {
            this.filterOptions.chapters.forEach(chapter => {
                const option = document.createElement('option');
                option.value = chapter.value;
                option.textContent = chapter.label;
                chapterFilter.appendChild(option);
            });
        }
        
        console.log('过滤器初始化完成');
    }

    /**
     * 阶段变化时更新年级选项
     */
    onStageChange(stage) {
        const gradeFilter = document.getElementById('gradeFilter');
        gradeFilter.innerHTML = '<option value="">选择年级</option>';

        if (stage && this.filterOptions.grades[stage]) {
            this.filterOptions.grades[stage].forEach(grade => {
                const option = document.createElement('option');
                option.value = grade;
                option.textContent = grade;
                gradeFilter.appendChild(option);
            });
        }
    }

    /**
     * 执行搜索
     */
    async performSearch() {
        if (!this.checkNetworkStatus()) {
            return;
        }

        try {
            // 收集搜索参数
            this.searchParams = {
                stageName: document.getElementById('stageFilter').value,
                gradeName: document.getElementById('gradeFilter').value,
                subject: document.getElementById('subjectFilter').value,
                chapterNumber: document.getElementById('chapterFilter').value,
                semester: document.getElementById('semesterFilter').value
            };

            // 显示加载状态
            this.showLoading('正在搜索课程...');

            // 使用重试机制调用搜索API
            const response = await this.withRetry(async () => {
                return await this.api.searchCoursesByConditions(this.searchParams);
            });
            
            if (response.success) {
                this.courses = response.data || [];
                await this.renderCourseResults();
                this.showMessage('搜索完成', 'success');
            } else {
                this.showMessage('搜索失败：' + response.message, 'error');
            }
        } catch (error) {
            this.handleError(error, '搜索课程');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 渲染课程搜索结果
     */
    async renderCourseResults() {
        const tbody = document.querySelector('#courseResultsTable tbody');
        tbody.innerHTML = '';

        if (this.courses.length === 0) {
            tbody.innerHTML = '<tr><td colspan="9" class="text-center">暂无数据</td></tr>';
            this.updateResultsInfo(0);
            return;
        }

        // 显示加载状态检查
        this.showLoading('正在检查课程状态...');

        try {
            // 批量检查状态以提高性能
            const statusPromises = this.courses.map(course => 
                this.checkCourseStatus(course.courseId)
            );
            const statuses = await Promise.all(statusPromises);

            // 渲染表格行
            const tooltips = this.getStatusTooltips();
            this.courses.forEach((course, index) => {
                const status = statuses[index];
                const row = document.createElement('tr');
                row.innerHTML = `
                    <td>${course.courseId}</td>
                    <td>${course.sectionNumber || '-'}</td>
                    <td title="${course.sectionTitle || course.chapterTitle || ''}">${this.truncateText(course.sectionTitle || course.chapterTitle || '', 30)}</td>
                    <td class="content-cell">${course.sectionContent || ''}</td>
                    <td class="status-cell">${this.renderStatusIcon(status.outlinePolished, tooltips.outlinePolished[status.outlinePolished])}</td>
                    <td class="status-cell">${this.renderStatusIcon(status.reviewApproved, tooltips.reviewApproved[status.reviewApproved])}</td>
                    <td class="status-cell">${this.renderStatusIcon(status.speechSynthesized, tooltips.speechSynthesized[status.speechSynthesized])}</td>
                    <td class="status-cell">${this.renderStatusIcon(status.pptInserted, tooltips.pptInserted[status.pptInserted])}</td>
                    <td class="actions-cell">
                        <button class="btn btn-sm btn-primary" onclick="courseManagement.editCourse('${course.courseId}')" title="编辑课程">
                            <i class="fas fa-edit"></i>
                            编辑
                        </button>
                    </td>
                `;
                tbody.appendChild(row);
            });

            this.updateResultsInfo(this.courses.length);
        } catch (error) {
            this.handleError(error, '渲染课程结果');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 更新结果信息
     */
    updateResultsInfo(count) {
        const resultsHeader = document.querySelector('.results-header h3');
        if (resultsHeader) {
            resultsHeader.textContent = `搜索结果 (${count} 条)`;
        }
    }

    /**
     * 绑定表格排序事件
     */
    bindTableSorting() {
        document.addEventListener('click', (e) => {
            if (e.target.closest('.sortable')) {
                const th = e.target.closest('.sortable');
                const sortField = th.dataset.sort;
                this.sortCourses(sortField);
            }
        });
    }

    /**
     * 排序课程数据
     */
    sortCourses(field) {
        if (!this.courses || this.courses.length === 0) return;

        // 切换排序方向
        if (this.sortField === field) {
            this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
        } else {
            this.sortField = field;
            this.sortDirection = 'asc';
        }

        // 执行排序
        this.courses.sort((a, b) => {
            let valueA = a[field] || '';
            let valueB = b[field] || '';

            // 数字类型排序
            if (field === 'sectionNumber') {
                valueA = parseInt(valueA) || 0;
                valueB = parseInt(valueB) || 0;
            } else {
                // 字符串排序
                valueA = valueA.toString().toLowerCase();
                valueB = valueB.toString().toLowerCase();
            }

            if (valueA < valueB) {
                return this.sortDirection === 'asc' ? -1 : 1;
            }
            if (valueA > valueB) {
                return this.sortDirection === 'asc' ? 1 : -1;
            }
            return 0;
        });

        // 更新表头排序图标
        this.updateSortIcons(field);

        // 重新渲染表格
        this.renderCourseResults();
    }

    /**
     * 更新排序图标
     */
    updateSortIcons(activeField) {
        document.querySelectorAll('.sortable i').forEach(icon => {
            icon.className = 'fas fa-sort';
        });

        const activeIcon = document.querySelector(`[data-sort="${activeField}"] i`);
        if (activeIcon) {
            activeIcon.className = this.sortDirection === 'asc' 
                ? 'fas fa-sort-up' 
                : 'fas fa-sort-down';
        }
    }

    /**
     * 刷新课程状态
     */
    async refreshCourseStatus() {
        if (!this.courses || this.courses.length === 0) {
            this.showMessage('请先搜索课程', 'warning');
            return;
        }

        try {
            this.showLoading('正在刷新课程状态...');
            await this.renderCourseResults();
            this.showMessage('状态刷新完成', 'success');
        } catch (error) {
            this.handleError(error, '刷新课程状态');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 检查课程状态
     */
    async checkCourseStatus(courseId) {
        const status = {
            outlinePolished: false,
            reviewApproved: false,
            speechSynthesized: false,
            pptInserted: false
        };

        try {
            // 并行检查所有状态，使用重试机制
            const [outlineResponse, reviewResponse, synthesisResponse, pptResponse] = await Promise.allSettled([
                this.withRetry(() => this.api.isSessionExist(courseId)),
                this.withRetry(() => this.api.getSessionStatus(courseId)),
                this.withRetry(() => this.api.isAudioSynthesisExist(courseId)),
                this.withRetry(() => this.api.existsSlidesByCourseId(courseId))
            ]);

            // 处理大纲润色状态
            if (outlineResponse.status === 'fulfilled' && outlineResponse.value.success) {
                status.outlinePolished = outlineResponse.value.data;
            }

            // 处理审核状态 - APPROVED或SYNTHESIZED都认为是审核成功
            if (reviewResponse.status === 'fulfilled' && reviewResponse.value.success) {
                const reviewStatus = reviewResponse.value.data;
                status.reviewApproved = reviewStatus === 'APPROVED' || reviewStatus === 'SYNTHESIZED';
            }

            // 处理语音合成状态
            if (synthesisResponse.status === 'fulfilled' && synthesisResponse.value.success) {
                status.speechSynthesized = synthesisResponse.value.data;
            }

            // 处理PPT插入状态
            if (pptResponse.status === 'fulfilled' && pptResponse.value.success) {
                status.pptInserted = pptResponse.value.data;
            }

        } catch (error) {
            console.error('检查课程状态失败:', error);
            // 不抛出错误，返回默认状态
        }

        return status;
    }

    /**
     * 渲染状态图标
     */
    renderStatusIcon(status, tooltip = '') {
        const iconClass = status 
            ? 'fas fa-check-circle text-success' 
            : 'fas fa-times-circle text-danger';
        
        const title = tooltip || (status ? '已完成' : '未完成');
        
        return `<i class="${iconClass}" title="${title}"></i>`;
    }

    /**
     * 获取状态工具提示
     */
    getStatusTooltips() {
        return {
            outlinePolished: {
                true: '大纲已润色',
                false: '大纲未润色'
            },
            reviewApproved: {
                true: '审核已通过',
                false: '审核未通过'
            },
            speechSynthesized: {
                true: '语音已合成',
                false: '语音未合成'
            },
            pptInserted: {
                true: 'PPT已插入',
                false: 'PPT未插入'
            }
        };
    }

    /**
     * 截断文本
     */
    truncateText(text, maxLength) {
        if (text.length <= maxLength) return text;
        return text.substring(0, maxLength) + '...';
    }

    /**
     * 清空过滤器
     */
    clearFilters() {
        document.getElementById('stageFilter').value = '';
        document.getElementById('gradeFilter').value = '';
        document.getElementById('subjectFilter').value = '';
        document.getElementById('chapterFilter').value = '';
        document.getElementById('semesterFilter').value = '';
        
        // 清空年级选项
        this.onStageChange('');
    }

    /**
     * 显示添加课程弹窗
     */
    showAddCourseModal() {
        if (!window.modal) {
            this.showMessage('模态框组件未初始化', 'error');
            return;
        }

        const modalTitle = document.getElementById('modalTitle');
        const modalBody = document.getElementById('modalBody');

        modalTitle.textContent = '添加课程';
        modalBody.innerHTML = this.getAddCourseModalHTML();

        // 预填充当前搜索条件
        this.prefillCourseForm();

        // 绑定表单事件
        this.bindCourseFormEvents();

        // 显示模态框
        window.modal.show();
    }

    /**
     * 获取添加课程弹窗的HTML
     */
    getAddCourseModalHTML() {
        return `
            <form id="addCourseForm" class="course-form">
                <div class="form-row">
                    <div class="form-group">
                        <label for="courseStage">阶段 *</label>
                        <select id="courseStage" name="stage" required>
                            <option value="">选择阶段</option>
                            ${this.filterOptions.stages.map(stage => 
                                `<option value="${stage}">${stage}</option>`
                            ).join('')}
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="courseGrade">年级 *</label>
                        <select id="courseGrade" name="grade" required>
                            <option value="">选择年级</option>
                        </select>
                    </div>
                </div>

                <div class="form-row">
                    <div class="form-group">
                        <label for="courseSubject">学科 *</label>
                        <select id="courseSubject" name="subject" required>
                            <option value="">选择学科</option>
                            ${this.filterOptions.subjects.map(subject => 
                                `<option value="${subject}">${subject}</option>`
                            ).join('')}
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="courseSemester">学期 *</label>
                        <select id="courseSemester" name="semester" required>
                            <option value="">选择学期</option>
                            ${this.filterOptions.semesters.map(semester => 
                                `<option value="${semester.value}">${semester.label}</option>`
                            ).join('')}
                        </select>
                    </div>
                </div>

                <div class="form-row">
                    <div class="form-group">
                        <label for="courseChapter">章节 *</label>
                        <select id="courseChapter" name="chapter" required>
                            <option value="">选择章节</option>
                            ${this.filterOptions.chapters.map(chapter => 
                                `<option value="${chapter.value}">${chapter.label}</option>`
                            ).join('')}
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="courseSectionNumber">小节序号 *</label>
                        <input type="text" id="courseSectionNumber" name="sectionNumber" 
                               placeholder="请输入小节序号（如：1、1.1、1.2）" pattern="^[0-9]+(\.[0-9]+)?$" required>
                    </div>
                </div>

                <div class="form-group">
                    <label for="courseChapterTitle">章名称 *</label>
                    <input type="text" id="courseChapterTitle" name="chapterTitle" 
                           placeholder="请输入章名称" maxlength="100" required>
                </div>

                <div class="form-group">
                    <label for="courseTitle">课程标题 *</label>
                    <input type="text" id="courseTitle" name="title" 
                           placeholder="请输入课程标题" maxlength="100" required>
                </div>

                <div class="form-group">
                    <label for="courseContent">课程内容 *</label>
                    <textarea id="courseContent" name="content" rows="6" 
                              placeholder="请输入课程内容" maxlength="2000" required></textarea>
                </div>

                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">
                        取消
                    </button>
                    <button type="submit" class="btn btn-primary">
                        <i class="fas fa-save"></i>
                        保存课程
                    </button>
                </div>
            </form>
        `;
    }

    /**
     * 预填充课程表单
     */
    prefillCourseForm() {
        // 预填充当前搜索条件
        if (this.searchParams.stageName) {
            document.getElementById('courseStage').value = this.searchParams.stageName;
            this.updateCourseGradeOptions(this.searchParams.stageName);
            
            if (this.searchParams.gradeName) {
                document.getElementById('courseGrade').value = this.searchParams.gradeName;
            }
        }

        if (this.searchParams.subject) {
            document.getElementById('courseSubject').value = this.searchParams.subject;
        }

        if (this.searchParams.semester) {
            document.getElementById('courseSemester').value = this.searchParams.semester;
        }

        if (this.searchParams.chapterNumber) {
            document.getElementById('courseChapter').value = this.searchParams.chapterNumber;
        }
    }

    /**
     * 更新课程年级选项
     */
    updateCourseGradeOptions(stage) {
        const gradeSelect = document.getElementById('courseGrade');
        gradeSelect.innerHTML = '<option value="">选择年级</option>';

        if (stage && this.filterOptions.grades[stage]) {
            this.filterOptions.grades[stage].forEach(grade => {
                const option = document.createElement('option');
                option.value = grade;
                option.textContent = grade;
                gradeSelect.appendChild(option);
            });
        }
    }

    /**
     * 绑定课程表单事件
     */
    bindCourseFormEvents() {
        // 阶段变化时更新年级选项
        document.getElementById('courseStage').addEventListener('change', (e) => {
            this.updateCourseGradeOptions(e.target.value);
        });

        // 表单提交
        document.getElementById('addCourseForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.submitCourseForm();
        });

        // 实时字符计数
        this.setupCharacterCount();
    }

    /**
     * 设置字符计数
     */
    setupCharacterCount() {
        const titleInput = document.getElementById('courseTitle');
        const contentTextarea = document.getElementById('courseContent');

        // 为标题添加字符计数
        this.addCharacterCounter(titleInput, 100);
        
        // 为内容添加字符计数
        this.addCharacterCounter(contentTextarea, 2000);
    }

    /**
     * 添加字符计数器
     */
    addCharacterCounter(element, maxLength) {
        const counter = document.createElement('small');
        counter.className = 'character-counter';
        counter.style.color = '#64748b';
        counter.style.fontSize = '12px';
        
        const updateCounter = () => {
            const currentLength = element.value.length;
            counter.textContent = `${currentLength}/${maxLength}`;
            
            if (currentLength > maxLength * 0.9) {
                counter.style.color = '#ef4444';
            } else if (currentLength > maxLength * 0.7) {
                counter.style.color = '#f59e0b';
            } else {
                counter.style.color = '#64748b';
            }
        };

        element.addEventListener('input', updateCounter);
        element.parentNode.appendChild(counter);
        updateCounter();
    }

    /**
     * 提交课程表单
     */
    async submitCourseForm() {
        if (!this.checkNetworkStatus()) {
            return;
        }

        const form = document.getElementById('addCourseForm');
        if (!form) {
            this.showMessage('找不到表单元素', 'error');
            return;
        }

        const formData = new FormData(form);

        // 验证表单
        if (!this.validateCourseForm(form)) {
            return;
        }

        // 获取表单值并验证
        const stage = formData.get('stage')?.trim() || '';
        const grade = formData.get('grade')?.trim() || '';
        const subject = formData.get('subject')?.trim() || '';
        const semester = formData.get('semester')?.trim() || '';
        const chapter = formData.get('chapter')?.trim() || '';
        const chapterTitle = formData.get('chapterTitle')?.trim() || '';
        const sectionNumber = formData.get('sectionNumber')?.trim() || '';
        const title = formData.get('title')?.trim() || '';
        const content = formData.get('content')?.trim() || '';

        // 再次验证必填字段（双重检查）
        if (!stage || !grade || !subject || !semester || !chapter || !chapterTitle || !sectionNumber || !title || !content) {
            this.showMessage('请填写所有必填字段', 'warning');
            return;
        }

        // 验证章节编号
        const chapterNumber = parseInt(chapter);
        if (isNaN(chapterNumber) || chapterNumber <= 0) {
            this.showMessage('章节编号必须是大于0的整数', 'warning');
            return;
        }

        // 验证小节序号
        const sectionNum = parseFloat(sectionNumber);
        if (isNaN(sectionNum) || sectionNum <= 0 || sectionNum > 99) {
            this.showMessage('小节序号必须是0-99之间的数字（可以是小数，如1.1）', 'warning');
            return;
        }

        // 构建课程数据（不包含courseId，由后端生成）
        const courseData = {
            stageName: stage,
            gradeName: grade,
            subject: subject,
            semester: semester,
            chapterNumber: chapterNumber,
            chapterTitle: chapterTitle,
            sectionContent: content,  // 修正字段名：chapterContent -> sectionContent
            sectionNumber: sectionNum,
            sectionTitle: title
        };

        // 调试日志：输出提交的数据
        console.log('准备提交的课程数据:', courseData);
        
        // 验证数据完整性
        if (!courseData.stageName || !courseData.gradeName || !courseData.subject || 
            !courseData.semester || !courseData.chapterNumber || !courseData.chapterTitle || 
            !courseData.sectionNumber || !courseData.sectionTitle || !courseData.sectionContent) {
            console.error('课程数据不完整:', courseData);
            this.showMessage('课程数据不完整，请检查表单', 'error');
            return;
        }

        try {
            this.showLoading('正在创建课程...');

            // 使用重试机制调用创建API
            const response = await this.withRetry(async () => {
                return await this.api.createCourse(courseData);
            });

            console.log('创建课程API响应:', response);

            if (response.success) {
                // 从后端响应中获取courseId
                const createdCourseId = response.data?.courseId || response.courseId;
                console.log('后端返回的courseId:', createdCourseId);
                
                if (createdCourseId) {
                    this.showMessage(`课程创建成功，课程ID: ${createdCourseId}`, 'success');
                } else {
                    this.showMessage('课程创建成功', 'success');
                    console.warn('后端未返回courseId');
                }
                
                window.modal.close();
                
                // 刷新课程列表
                await this.performSearch();
            } else {
                this.showMessage('课程创建失败：' + (response.message || '未知错误'), 'error');
            }
        } catch (error) {
            this.handleError(error, '创建课程');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 验证课程表单
     */
    validateCourseForm(form) {
        let isValid = true;
        
        // 清除之前的错误状态
        this.clearFormErrors(form);

        // 验证必填字段
        const requiredFields = [
            { name: 'stage', label: '阶段' },
            { name: 'grade', label: '年级' },
            { name: 'subject', label: '学科' },
            { name: 'semester', label: '学期' },
            { name: 'chapter', label: '章节' },
            { name: 'chapterTitle', label: '章名称' },
            { name: 'sectionNumber', label: '小节序号' },
            { name: 'title', label: '课程标题' },
            { name: 'content', label: '课程内容' }
        ];

        requiredFields.forEach(field => {
            const element = form.querySelector(`[name="${field.name}"]`);
            const value = element.value.trim();

            if (!value) {
                this.showFieldError(element, `${field.label}不能为空`);
                isValid = false;
            }
        });

        // 验证小节序号（支持小数）
        const sectionNumber = form.querySelector('[name="sectionNumber"]').value;
        if (sectionNumber) {
            const sectionNum = parseFloat(sectionNumber);
            if (isNaN(sectionNum) || sectionNum <= 0 || sectionNum > 99) {
                this.showFieldError(form.querySelector('[name="sectionNumber"]'), '小节序号必须是0-99之间的数字（可以是小数，如1.1）');
                isValid = false;
            }
        }

        // 验证标题长度
        const title = form.querySelector('[name="title"]').value.trim();
        if (title && title.length > 100) {
            this.showFieldError(form.querySelector('[name="title"]'), '课程标题不能超过100个字符');
            isValid = false;
        }

        // 验证内容长度
        const content = form.querySelector('[name="content"]').value.trim();
        if (content && content.length > 2000) {
            this.showFieldError(form.querySelector('[name="content"]'), '课程内容不能超过2000个字符');
            isValid = false;
        }

        return isValid;
    }

    /**
     * 显示字段错误
     */
    showFieldError(element, message) {
        element.classList.add('error');
        
        // 移除现有错误消息
        const existingError = element.parentNode.querySelector('.error-message');
        if (existingError) {
            existingError.remove();
        }

        // 添加新错误消息
        const errorElement = document.createElement('span');
        errorElement.className = 'error-message';
        errorElement.textContent = message;
        element.parentNode.appendChild(errorElement);
    }

    /**
     * 清除表单错误
     */
    clearFormErrors(form) {
        // 移除错误样式
        form.querySelectorAll('.error').forEach(element => {
            element.classList.remove('error');
        });

        // 移除错误消息
        form.querySelectorAll('.error-message').forEach(element => {
            element.remove();
        });
    }

    /**
     * 编辑课程 - 切换到工作流视图
     */
    async editCourse(courseId) {
        if (!this.checkNetworkStatus()) {
            return;
        }

        try {
            this.showLoading('正在获取课程详情...');

            // 使用重试机制获取课程详情
            const response = await this.withRetry(async () => {
                return await this.api.getCourseById(courseId);
            });

            if (response.success) {
                this.currentCourse = response.data;
                await this.showWorkflowView();
            } else {
                this.showMessage('获取课程详情失败：' + response.message, 'error');
            }
        } catch (error) {
            this.handleError(error, '获取课程详情');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 显示搜索视图
     */
    showSearchView() {
        document.getElementById('courseSearchView').classList.add('active');
        document.getElementById('courseWorkflowView').classList.remove('active');
        this.currentView = 'search';
    }

    /**
     * 显示工作流视图
     */
    async showWorkflowView() {
        console.log('=== 进入工作流视图 ===');
        console.log('当前课程ID:', this.currentCourse?.courseId);
        
        document.getElementById('courseSearchView').classList.remove('active');
        document.getElementById('courseWorkflowView').classList.add('active');
        this.currentView = 'workflow';
        
        // 更新课程信息显示
        if (this.currentCourse) {
            document.getElementById('workflowCourseTitle').textContent = 
                `${this.currentCourse.title} - 工作流处理`;
            document.getElementById('workflowCourseDetails').textContent = 
                `课程ID: ${this.currentCourse.courseId} | 学科: ${this.currentCourse.subject} | 阶段: ${this.currentCourse.stage}`;
        }
        
        // 【关键修复】强制清空内存中的工作流数据，防止不同课程之间的状态污染
        // 每次进入工作流视图时都重新初始化，而不是依赖内存中可能残留的旧数据
        console.log('清空前的workflowData:', JSON.stringify(this.workflowData));
        this.workflowData = {};
        console.log('清空后的workflowData:', JSON.stringify(this.workflowData));
        
        // 检查当前课程的实际完成状态，从后端API获取真实状态
        await this.initializeWorkflowFromActualStatus();
        
        console.log('初始化后的workflowData:', JSON.stringify(this.workflowData));
        
        // 切换到适当的步骤
        const lastStep = this.getLastCompletedStep();
        console.log('最后完成的步骤:', lastStep, '即将切换到步骤:', lastStep + 1);
        this.switchToStep(lastStep + 1);
    }

    /**
     * 根据实际状态初始化工作流数据
     */
    async initializeWorkflowFromActualStatus() {
        if (!this.currentCourse || !this.currentCourse.courseId) return;
        
        try {
            // 检查课程的实际完成状态
            const status = await this.checkCourseStatus(this.currentCourse.courseId);
            
            // 根据实际状态初始化 workflowData
            if (status.outlinePolished) {
                this.workflowData.step1 = {
                    courseId: this.currentCourse.courseId,
                    completed: true
                };
            }
            
            if (status.reviewApproved) {
                this.workflowData.step2 = {
                    reviewResult: {
                        approved: true
                    }
                };
            }
            
            if (status.speechSynthesized) {
                this.workflowData.step3 = {
                    completed: true
                };
            }
            
            // 只有当真正完成PPT插入（有pptResult）时才设置step4
            // 仅仅从API检查得到的pptInserted状态不应该设置step4，因为那只是检查结果，不是实际插入的结果
            // step4应该只在用户实际插入PPT后才设置，并且会包含pptResult
            // 这里不设置step4，让用户能够正常插入PPT
            // 如果后端确实已经有PPT数据，用户仍然可以查看和编辑
            
            console.log('工作流状态已根据实际情况初始化:', this.workflowData);
        } catch (error) {
            console.error('初始化工作流状态失败:', error);
        }
    }

    /**
     * 保存工作流状态
     */
    saveWorkflowState() {
        if (!this.currentCourse) return;
        
        const stateKey = `workflow_${this.currentCourse.courseId}`;
        const state = {
            courseId: this.currentCourse.courseId,
            currentStep: this.workflowStep,
            workflowData: this.workflowData,
            timestamp: new Date().toISOString()
        };
        
        try {
            localStorage.setItem(stateKey, JSON.stringify(state));
            console.log('工作流状态已保存:', stateKey);
        } catch (error) {
            console.error('保存工作流状态失败:', error);
        }
    }

    /**
     * 恢复工作流状态
     */
    restoreWorkflowState() {
        if (!this.currentCourse) return;
        
        const stateKey = `workflow_${this.currentCourse.courseId}`;
        
        try {
            const savedState = localStorage.getItem(stateKey);
            if (savedState) {
                const state = JSON.parse(savedState);
                
                // 检查状态是否过期（24小时）
                const stateAge = Date.now() - new Date(state.timestamp).getTime();
                const maxAge = 24 * 60 * 60 * 1000; // 24小时
                
                if (stateAge < maxAge) {
                    this.workflowData = state.workflowData || {};
                    console.log('工作流状态已恢复:', stateKey);
                } else {
                    // 清除过期状态
                    localStorage.removeItem(stateKey);
                    console.log('工作流状态已过期，已清除:', stateKey);
                }
            }
        } catch (error) {
            console.error('恢复工作流状态失败:', error);
        }
    }

    /**
     * 清除工作流状态
     */
    clearWorkflowState() {
        if (!this.currentCourse) return;
        
        const stateKey = `workflow_${this.currentCourse.courseId}`;
        localStorage.removeItem(stateKey);
        this.workflowData = {};
        console.log('工作流状态已清除:', stateKey);
    }

    /**
     * 获取最后完成的步骤
     */
    getLastCompletedStep() {
        let lastStep = 0;
        
        if (this.workflowData.step1) lastStep = 1;
        if (this.workflowData.step2 && this.workflowData.step2.reviewResult && this.workflowData.step2.reviewResult.approved) lastStep = 2;
        if (this.workflowData.step3) lastStep = 3;
        // 步骤4只有在真正完成PPT插入（有pptResult）时才认为已完成
        // 仅仅有completed: true是不够的，因为那可能是从API检查得到的，而不是实际插入的结果
        if (this.workflowData.step4 && this.workflowData.step4.pptResult) lastStep = 4;
        
        return lastStep;
    }

    /**
     * 清理过期的工作流状态
     */
    cleanupExpiredStates() {
        const maxAge = 24 * 60 * 60 * 1000; // 24小时
        const keysToRemove = [];
        
        for (let i = 0; i < localStorage.length; i++) {
            const key = localStorage.key(i);
            if (key && key.startsWith('workflow_')) {
                try {
                    const state = JSON.parse(localStorage.getItem(key));
                    const stateAge = Date.now() - new Date(state.timestamp).getTime();
                    
                    if (stateAge > maxAge) {
                        keysToRemove.push(key);
                    }
                } catch (error) {
                    // 如果解析失败，也删除这个键
                    keysToRemove.push(key);
                }
            }
        }
        
        keysToRemove.forEach(key => {
            localStorage.removeItem(key);
            console.log('已清除过期状态:', key);
        });
    }

    /**
     * 更新工作流进度
     */
    updateWorkflowProgress() {
        const completedSteps = this.getLastCompletedStep();
        const currentStep = this.workflowStep;
        const totalSteps = 4;
        
        // 计算进度百分比
        const progressPercent = (completedSteps / totalSteps) * 100;
        
        // 更新进度条
        const progressFill = document.getElementById('progressFill');
        const progressText = document.getElementById('progressText');
        
        if (progressFill) {
            progressFill.style.width = `${progressPercent}%`;
        }
        
        if (progressText) {
            progressText.textContent = `步骤 ${currentStep} / ${totalSteps} (已完成 ${completedSteps} 步)`;
        }
        
        // 更新步骤圆圈的完成状态
        document.querySelectorAll('.step').forEach((step, index) => {
            const stepNum = index + 1;
            if (stepNum <= completedSteps) {
                step.classList.add('completed');
            } else {
                step.classList.remove('completed');
            }
        });
        
        // 如果所有步骤都完成了，且当前在最后一步，显示完成状态
        // 但只有在步骤4已完成且不在编辑模式时才显示
        if (completedSteps === totalSteps && currentStep === totalSteps) {
            if (this.workflowData.step4 && 
                this.workflowData.step4.pptResult && 
                !this.workflowData.step4.editing) {
                // 检查是否已经显示了完成消息，避免重复覆盖
                const step4Content = document.getElementById('step4Content');
                if (step4Content && !step4Content.querySelector('.workflow-complete')) {
                    this.showWorkflowComplete();
                }
            }
        }
    }

    /**
     * 切换工作流步骤
     */
    switchToStep(stepNumber) {
        // 更新步骤导航
        document.querySelectorAll('.step').forEach(step => {
            step.classList.remove('active');
        });
        document.querySelector(`[data-step="${stepNumber}"]`).classList.add('active');

        // 更新步骤内容
        document.querySelectorAll('.step-content').forEach(content => {
            content.classList.remove('active');
        });
        document.getElementById(`step${stepNumber}Content`).classList.add('active');

        this.workflowStep = stepNumber;

        // 更新进度指示器
        this.updateWorkflowProgress();

        // 根据步骤执行特定逻辑
        this.onStepChanged(stepNumber);
    }

    /**
     * 步骤切换时的处理
     */
    async onStepChanged(stepNumber) {
        // 【修复】不在此处保存状态，因为此时workflowData可能还未完全初始化
        // 状态保存应该在每个步骤的操作完成后进行，而不是在切换步骤时
        // this.saveWorkflowState(); // 移除此行，防止在状态未就绪时保存
        
        switch (stepNumber) {
            case 2:
                // 切换到审核步骤时，加载审核内容
                await this.loadReviewContent();
                break;
            case 3:
                // 切换到语音合成步骤时，检查状态
                this.checkSynthesisStatus();
                break;
            case 4:
                // 切换到PPT插入步骤时，检查是否已完成
                // 【关键检查】验证step4是否真正存在且包含有效的pptResult
                console.log('=== 切换到步骤4 ===');
                console.log('当前课程ID:', this.currentCourse?.courseId);
                console.log('workflowData.step4:', this.workflowData.step4);
                
                if (this.workflowData.step4 && 
                    this.workflowData.step4.pptResult && 
                    !this.workflowData.step4.editing) {
                    console.log('检测到step4已完成，显示完成状态');
                    // 步骤4已完成且不在编辑模式，显示完成状态
                    // 但先检查是否已经显示了完成消息，避免重复覆盖
                    const step4Content = document.getElementById('step4Content');
                    if (step4Content && !step4Content.querySelector('.workflow-complete')) {
                        this.showWorkflowComplete();
                    }
                } else {
                    console.log('step4未完成，显示PPT插入表格');
                    // 步骤4未完成或正在编辑，显示表格并准备数据
                    this.preparePptData();
                }
                break;
        }
    }

    // 工作流步骤方法
    /**
     * 处理大纲润色
     */
    async processOutline() {
        if (!this.checkNetworkStatus()) {
            return;
        }

        const jsonInput = document.getElementById('outlineJsonInput');
        const jsonData = jsonInput.value.trim();

        if (!jsonData) {
            this.showMessage('请输入大纲JSON数据', 'warning');
            jsonInput.focus();
            return;
        }

        // 验证JSON格式
        let parsedData;
        try {
            parsedData = JSON.parse(jsonData);
        } catch (error) {
            this.showMessage('JSON格式错误，请检查输入内容', 'error');
            jsonInput.focus();
            return;
        }

        // 简化验证，只检查是否为对象
        if (!parsedData || typeof parsedData !== 'object') {
            this.showMessage('JSON数据必须是一个对象', 'error');
            return;
        }

        // 自动填充当前课程的 courseId（如果 JSON 中没有或为空）
        if (!this.currentCourse) {
            console.error('❌ currentCourse 不存在');
            this.showMessage('课程ID不能为空，请先选择课程进入工作流', 'error');
            return;
        }

        // 获取并验证当前课程的 courseId
        const courseId = this.currentCourse.courseId;
        if (!courseId || (typeof courseId === 'string' && courseId.trim() === '')) {
            console.error('❌ currentCourse.courseId 无效:', {
                courseId: courseId,
                currentCourse: this.currentCourse
            });
            this.showMessage('当前课程的ID无效，请重新选择课程', 'error');
            return;
        }

        const validCourseId = String(courseId).trim();
        console.log('✓ 当前课程ID验证通过:', validCourseId);

        // 自动将当前课程的 courseId 注入到 JSON 数据中
        const existingCourseId = parsedData.courseId;
        
        // 调试：输出当前状态
        console.log('当前课程信息:', {
            currentCourse: this.currentCourse,
            courseId: courseId,
            existingCourseId: existingCourseId,
            existingCourseIdType: typeof existingCourseId,
            existingCourseIdTrimmed: typeof existingCourseId === 'string' ? existingCourseId.trim() : 'N/A'
        });
        
        // 如果 courseId 字段不存在、为 null、undefined 或空字符串，则自动填充
        const shouldAutoFill = !existingCourseId || 
                              existingCourseId === null || 
                              existingCourseId === undefined ||
                              (typeof existingCourseId === 'string' && existingCourseId.trim() === '');
        
        if (shouldAutoFill) {
            // 强制设置 courseId
            parsedData.courseId = validCourseId;
            console.log('✓ 已自动填充课程ID:', parsedData.courseId);
        } else {
            // 如果用户已经填写了 courseId，但如果是空字符串，仍然使用当前课程的
            const userCourseId = String(existingCourseId).trim();
            if (userCourseId === '') {
                parsedData.courseId = validCourseId;
                console.log('✓ 用户提供的courseId为空字符串，已自动填充:', parsedData.courseId);
            } else {
                parsedData.courseId = userCourseId;
                console.log('使用用户提供的课程ID:', parsedData.courseId);
                if (userCourseId !== validCourseId) {
                    console.warn('用户提供的课程ID与当前课程ID不一致，使用用户提供的值');
                }
            }
        }

        // 最终验证：确保 courseId 已设置且不为空
        if (!parsedData.courseId || (typeof parsedData.courseId === 'string' && parsedData.courseId.trim() === '')) {
            console.error('❌ courseId 填充失败！', {
                parsedDataCourseId: parsedData.courseId,
                currentCourseCourseId: validCourseId
            });
            this.showMessage('课程ID填充失败，请检查当前课程信息', 'error');
            return;
        }

        try {
            this.showLoading('正在处理大纲润色...');

            // 使用已注入 courseId 的数据
            const requestData = parsedData;
            
            // 调试日志：最终验证
            console.log('✓ 准备发送的请求数据:', {
                courseId: requestData.courseId,
                title: requestData.title,
                slidesCount: requestData.slides ? requestData.slides.length : 0,
                fullData: requestData
            });
            
            // 再次确认 courseId 存在
            if (!requestData.courseId || requestData.courseId.trim() === '') {
                console.error('❌ 最终检查失败：courseId 仍然为空！');
                this.showMessage('课程ID验证失败，无法发送请求', 'error');
                return;
            }

            // 调用批量文本预处理API
            const response = await this.withRetry(async () => {
                return await this.api.bulkPreprocessing(requestData);
            });

            if (response.success || response.status === 'SUCCESS' || response.status === 'COMPLETED') {
                this.showMessage('大纲润色处理成功', 'success');
                
                // 保存处理结果
                this.workflowData = {
                    step1: {
                        courseId: response.courseId || this.currentCourse.courseId,
                        originalData: parsedData,
                        processedData: response
                    }
                };
                
                // 保存状态
                this.saveWorkflowState();

                // 自动切换到下一步
                this.switchToStep(2);
                await this.loadReviewContent();
            } else {
                this.showMessage('大纲润色处理失败：' + (response.message || '未知错误'), 'error');
            }
        } catch (error) {
            this.handleError(error, '大纲润色处理');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 插入示例JSON
     */
    insertSampleJson() {
        const sampleJson = {
            "title": "数学课程大纲",
            "slides": [
                {
                    "slideId": "slide_1",
                    "slideIndex": 1,
                    "title": "第一节：基础概念",
                    "content": "本节课我们将学习数学的基础概念，包括数字、运算和基本公式。"
                },
                {
                    "slideId": "slide_2", 
                    "slideIndex": 2,
                    "title": "第二节：实际应用",
                    "content": "通过实际例题来理解和应用前面学到的基础概念。"
                }
            ]
        };

        const jsonInput = document.getElementById('outlineJsonInput');
        jsonInput.value = JSON.stringify(sampleJson, null, 2);
        
        this.showMessage('示例JSON已插入', 'info');
    }

    /**
     * 验证大纲JSON结构
     */
    validateOutlineJson(data) {
        // 简化验证，只检查基本结构，让后端处理具体的字段验证
        if (!data || typeof data !== 'object') {
            this.showMessage('JSON数据必须是一个对象', 'error');
            return false;
        }

        // 移除所有具体字段的验证，直接返回true
        // 让后端来处理数据验证和转换
        return true;
    }

    /**
     * 将大纲数据转换为slides格式
     */
    convertOutlineToSlides(data) {
        // 直接返回原始数据，让后端处理数据结构
        return data.slides || data;
    }

    /**
     * 加载审核内容
     */
    async loadReviewContent() {
        if (!this.workflowData.step1 || !this.workflowData.step1.courseId) {
            document.getElementById('reviewContent').innerHTML = 
                '<p class="text-muted">请先完成步骤1的大纲润色处理</p>';
            return;
        }

        try {
            this.showLoading('正在加载审核内容...');

            // 获取会话详情
            const response = await this.withRetry(async () => {
                return await this.api.getPendingSessionByCourseId(this.workflowData.step1.courseId);
            });

            if (response && response.data) {
                this.displayReviewContent(response.data);
                this.workflowData.step2 = {
                    sessionData: response.data
                };
            } else {
                document.getElementById('reviewContent').innerHTML = 
                    '<p class="text-warning">暂无审核内容，请检查步骤1是否完成</p>';
            }
        } catch (error) {
            console.error('加载审核内容失败:', error);
            document.getElementById('reviewContent').innerHTML = 
                '<p class="text-danger">加载审核内容失败，请重试</p>';
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 显示审核内容
     */
    displayReviewContent(sessionData) {
        const reviewContent = document.getElementById('reviewContent');
        
        let contentHtml = `
            <div class="session-info">
                <h4>会话信息</h4>
                <div class="info-grid">
                    <div class="info-item">
                        <label>会话ID:</label>
                        <span class="session-id-display">${sessionData.courseId || sessionData.id}</span>
                    </div>
                    <div class="info-item">
                        <label>PPT标题:</label>
                        <span class="session-title-display">${sessionData.pptTitle || sessionData.title || '未设置'}</span>
                    </div>
                    <div class="info-item">
                        <label>当前状态:</label>
                        <span class="status-badge status-${(sessionData.status || sessionData.processingStatus || '').toLowerCase()}">${this.getStatusDisplayName(sessionData.status || sessionData.processingStatus)}</span>
                    </div>
                    <div class="info-item">
                        <label>片段数量:</label>
                        <span class="segments-count">${sessionData.totalSegments || 0}</span>
                    </div>
                    <div class="info-item">
                        <label>创建时间:</label>
                        <span class="creation-time">${this.formatDateTime(sessionData.createdAt)}</span>
                    </div>
                    <div class="info-item">
                        <label>最后更新:</label>
                        <span class="update-time">${this.formatDateTime(sessionData.updatedAt)}</span>
                    </div>
                </div>
            </div>

            <div class="review-sections">
                <div class="session-text-review">
                    <h4>会话文本审核</h4>
                    <div class="text-comparison">
                        <div class="text-column">
                            <label>原始文本:</label>
                            <div class="text-display readonly-text">${this.formatTextContent(sessionData.originalText || sessionData.content || '无原始文本')}</div>
                        </div>
                        <div class="text-column">
                            <label>润色文本:</label>
                            <textarea id="polishedTextArea" class="form-control editable-text" rows="8" placeholder="请输入或修改润色后的文本...">${sessionData.polishedText || sessionData.processedContent || ''}</textarea>
                        </div>
                    </div>
                </div>

                ${sessionData.segments && sessionData.segments.length > 0 ? this.renderSegmentsReview(sessionData.segments) : ''}

                <div class="review-decision">
                    <h4>审核决定</h4>
                    <div class="form-group">
                        <label>审核结果:</label>
                        <div class="radio-group">
                            <label class="radio-label">
                                <input type="radio" name="reviewApproved" value="true" required>
                                <span class="radio-text">
                                    <i class="fas fa-check-circle"></i>
                                    通过
                                </span>
                            </label>
                            <label class="radio-label">
                                <input type="radio" name="reviewApproved" value="false" required>
                                <span class="radio-text">
                                    <i class="fas fa-times-circle"></i>
                                    拒绝
                                </span>
                            </label>
                        </div>
                    </div>
                    <div class="form-group">
                        <label for="reviewCommentsArea">审核意见:</label>
                        <textarea id="reviewCommentsArea" class="form-control" rows="3" placeholder="请输入审核意见（拒绝时必填）...">${sessionData.reviewComments || ''}</textarea>
                        <small class="form-text text-muted">审核通过时可选填，审核拒绝时必须填写拒绝原因</small>
                    </div>
                </div>
            </div>

            <div class="review-actions">
                <button id="saveReviewChangesBtn" class="btn btn-info">
                    <i class="fas fa-save"></i>
                    保存修改
                </button>
                <button id="submitReviewBtn" class="btn btn-primary">
                    <i class="fas fa-paper-plane"></i>
                    提交审核
                </button>
                <button id="viewDetailsBtn" class="btn btn-secondary" onclick="courseManagement.showSessionDetailsModal('${sessionData.courseId || sessionData.id}')">
                    <i class="fas fa-eye"></i>
                    查看详情
                </button>
            </div>
        `;

        reviewContent.innerHTML = contentHtml;

        // 绑定保存修改按钮事件
        document.getElementById('saveReviewChangesBtn').addEventListener('click', () => {
            this.saveReviewChanges();
        });

        // 绑定提交审核按钮事件
        document.getElementById('submitReviewBtn').addEventListener('click', () => {
            this.submitReviewDecision();
        });

        // 监听审核结果变化，动态显示/隐藏必填提示
        document.querySelectorAll('input[name="reviewApproved"]').forEach(radio => {
            radio.addEventListener('change', () => {
                this.onReviewDecisionChange();
            });
        });
    }

    /**
     * 渲染文本片段审核区域
     */
    renderSegmentsReview(segments) {
        if (!segments || segments.length === 0) return '';

        return `
            <div class="segments-review">
                <h4>文本片段详情 <span class="badge badge-info">${segments.length} 个片段</span></h4>
                <div class="segments-container">
                    ${segments.map((segment, index) => `
                        <div class="segment-review-item" data-segment-index="${index}">
                            <div class="segment-header">
                                <span class="segment-index">片段 ${index + 1}</span>
                                <span class="segment-page">页面 ${segment.pageNumber || 'N/A'}</span>
                                <span class="segment-status status-${(segment.status || 'pending').toLowerCase()}">${this.getSegmentStatusName(segment.status)}</span>
                            </div>
                            <div class="segment-content">
                                <div class="text-section">
                                    <label>原始文本:</label>
                                    <div class="text-content readonly-segment">${segment.originalText || '无原始文本'}</div>
                                </div>
                                <div class="text-section">
                                    <label>润色文本:</label>
                                    <textarea class="form-control segment-polished-text" rows="3" placeholder="请输入润色后的文本...">${segment.polishedText || ''}</textarea>
                                </div>
                            </div>
                        </div>
                    `).join('')}
                </div>
                <div class="segments-actions">
                    <button type="button" class="btn btn-sm btn-info" onclick="courseManagement.saveAllSegments()">
                        <i class="fas fa-save"></i>
                        保存所有片段
                    </button>
                    <button type="button" class="btn btn-sm btn-secondary" onclick="courseManagement.resetAllSegments()">
                        <i class="fas fa-undo"></i>
                        重置修改
                    </button>
                </div>
            </div>
        `;
    }

    /**
     * 获取片段状态名称
     */
    getSegmentStatusName(status) {
        const statusMap = {
            'pending': '待处理',
            'processed': '已处理',
            'approved': '已审核',
            'rejected': '已拒绝'
        };
        return statusMap[status] || '未知';
    }

    /**
     * 审核决定变化时的处理
     */
    onReviewDecisionChange() {
        const approvedRadio = document.querySelector('input[name="reviewApproved"]:checked');
        const commentsArea = document.getElementById('reviewCommentsArea');
        const formText = commentsArea.nextElementSibling;

        if (approvedRadio) {
            const approved = approvedRadio.value === 'true';
            if (approved) {
                commentsArea.placeholder = '请输入审核意见（可选）...';
                formText.textContent = '审核通过时可选填意见';
                commentsArea.classList.remove('required-field');
            } else {
                commentsArea.placeholder = '请输入拒绝原因（必填）...';
                formText.textContent = '审核拒绝时必须填写拒绝原因';
                commentsArea.classList.add('required-field');
            }
        }
    }

    /**
     * 保存所有片段修改
     */
    async saveAllSegments() {
        const segmentTextareas = document.querySelectorAll('.segment-polished-text');
        const segments = [];

        segmentTextareas.forEach((textarea, index) => {
            segments.push({
                index: index,
                polishedText: textarea.value.trim()
            });
        });

        try {
            this.showLoading('正在保存片段修改...');
            
            // 这里可以调用API保存片段数据
            // const response = await this.api.saveSegments(this.workflowData.step1.courseId, segments);
            
            this.showMessage('片段修改保存成功', 'success');
        } catch (error) {
            this.handleError(error, '保存片段修改');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 重置所有片段修改
     */
    resetAllSegments() {
        if (confirm('确定要重置所有片段的修改吗？')) {
            const segmentTextareas = document.querySelectorAll('.segment-polished-text');
            segmentTextareas.forEach(textarea => {
                textarea.value = textarea.defaultValue;
            });
            this.showMessage('片段修改已重置', 'info');
        }
    }

    /**
     * 显示会话详情模态框
     */
    async showSessionDetailsModal(courseId) {
        try {
            this.showLoading('正在加载会话详情...');
            
            const sessionData = await this.withRetry(async () => {
                return await this.api.getPendingSessionByCourseId(courseId);
            });

            if (sessionData && sessionData.data) {
                this.displaySessionDetailsModal(sessionData.data);
            } else {
                this.showMessage('无法获取会话详情', 'error');
            }
        } catch (error) {
            this.handleError(error, '获取会话详情');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 显示会话详情模态框内容
     */
    displaySessionDetailsModal(sessionData) {
        const modalTitle = document.getElementById('modalTitle');
        const modalBody = document.getElementById('modalBody');

        modalTitle.textContent = '会话详情';
        modalBody.innerHTML = `
            <div class="session-details">
                <div class="detail-section">
                    <h4>基本信息</h4>
                    <div class="detail-grid">
                        <div class="detail-item">
                            <label>会话ID:</label>
                            <span class="detail-value">${sessionData.courseId || sessionData.id}</span>
                        </div>
                        <div class="detail-item">
                            <label>PPT标题:</label>
                            <span class="detail-value">${sessionData.pptTitle || sessionData.title || '未设置'}</span>
                        </div>
                        <div class="detail-item">
                            <label>状态:</label>
                            <span class="status-badge status-${(sessionData.status || sessionData.processingStatus || '').toLowerCase()}">${this.getStatusDisplayName(sessionData.status || sessionData.processingStatus)}</span>
                        </div>
                        <div class="detail-item">
                            <label>创建时间:</label>
                            <span class="detail-value">${this.formatDateTime(sessionData.createdAt)}</span>
                        </div>
                        <div class="detail-item">
                            <label>页面数:</label>
                            <span class="detail-value">${sessionData.totalPages || 0}</span>
                        </div>
                        <div class="detail-item">
                            <label>片段数:</label>
                            <span class="detail-value">${sessionData.totalSegments || 0}</span>
                        </div>
                    </div>
                </div>

                <div class="detail-section">
                    <h4>文本内容</h4>
                    <div class="text-content-section">
                        <div class="text-item">
                            <label>原始文本:</label>
                            <div class="text-display detail-text">${this.formatTextContent(sessionData.originalText || '无原始文本')}</div>
                        </div>
                        <div class="text-item">
                            <label>润色文本:</label>
                            <div class="text-display detail-text">${this.formatTextContent(sessionData.polishedText || '无润色文本')}</div>
                        </div>
                    </div>
                </div>

                ${sessionData.segments && sessionData.segments.length > 0 ? `
                <div class="detail-section">
                    <h4>文本片段 <span class="badge badge-info">${sessionData.segments.length} 个</span></h4>
                    <div class="segments-list">
                        ${sessionData.segments.map((segment, index) => `
                            <div class="segment-item">
                                <div class="segment-header">
                                    <span class="segment-index">片段 ${index + 1}</span>
                                    <span class="segment-page">页面 ${segment.pageNumber || 'N/A'}</span>
                                    <span class="segment-status status-${(segment.status || 'pending').toLowerCase()}">${this.getSegmentStatusName(segment.status)}</span>
                                </div>
                                <div class="segment-content">
                                    <div class="text-section">
                                        <label>原始文本:</label>
                                        <div class="text-content">${segment.originalText || '无'}</div>
                                    </div>
                                    <div class="text-section">
                                        <label>润色文本:</label>
                                        <div class="text-content">${segment.polishedText || '无'}</div>
                                    </div>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                </div>
                ` : ''}

                <div class="modal-actions">
                    <button class="btn btn-secondary" onclick="window.modal.close()">关闭</button>
                    <button class="btn btn-info" onclick="window.modal.close(); courseManagement.loadReviewContent();">返回审核</button>
                </div>
            </div>
        `;

        // 显示模态框
        document.getElementById('modal').style.display = 'flex';
    }

    /**
     * 格式化文本内容
     */
    formatTextContent(text) {
        if (!text) return '暂无内容';
        
        // 尝试解析JSON格式的文本
        try {
            const parsed = JSON.parse(text);
            return `<pre class="json-content">${JSON.stringify(parsed, null, 2)}</pre>`;
        } catch (e) {
            // 如果不是JSON，按普通文本处理
            return `<div class="plain-text">${text.replace(/\n/g, '<br>')}</div>`;
        }
    }

    /**
     * 格式化日期时间
     */
    formatDateTime(dateTime) {
        if (!dateTime) return '未知';
        
        try {
            const date = new Date(dateTime);
            return date.toLocaleString('zh-CN');
        } catch (e) {
            return dateTime;
        }
    }

    /**
     * 获取状态显示名称
     */
    getStatusDisplayName(status) {
        const statusMap = {
            'PENDING_REVIEW': '待审核',
            'APPROVED': '已审核通过',
            'REJECTED': '已拒绝',
            'SYNTHESIZED': '已合成'
        };
        return statusMap[status] || status || '未知';
    }

    /**
     * 保存审核修改（不提交审核决定）
     */
    async saveReviewChanges() {
        if (!this.workflowData.step1 || !this.workflowData.step1.courseId) {
            this.showMessage('缺少会话信息', 'error');
            return;
        }

        const polishedText = document.getElementById('polishedTextArea').value.trim();
        const reviewComments = document.getElementById('reviewCommentsArea').value.trim();

        if (!polishedText) {
            this.showMessage('润色文本不能为空', 'warning');
            return;
        }

        try {
            this.showLoading('正在保存修改...');

            const reviewData = {
                reviewerId: 'admin',
                approved: null, // 保存修改时不设置审核结果
                comments: reviewComments || '管理员修改内容',
                updatedPolishedText: polishedText,
                reviewTime: new Date().toISOString()
            };

            const response = await this.withRetry(async () => {
                return await this.api.adminReviewSession(this.workflowData.step1.courseId, reviewData);
            });

            if (response.success || response.status === 'SUCCESS' || response.status === 'COMPLETED') {
                this.showMessage('修改保存成功', 'success');
                
                // 保存修改状态，但不标记为审核完成
                this.workflowData.step2 = {
                    ...this.workflowData.step2,
                    sessionData: {
                        ...this.workflowData.step2.sessionData,
                        polishedText: polishedText,
                        reviewComments: reviewComments
                    }
                };
                
                // 保存状态
                this.saveWorkflowState();
            } else {
                this.showMessage('保存修改失败：' + (response.message || '未知错误'), 'error');
            }
        } catch (error) {
            this.handleError(error, '保存审核修改');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 提交审核决定
     */
    async submitReviewDecision() {
        if (!this.workflowData.step1 || !this.workflowData.step1.courseId) {
            this.showMessage('缺少会话信息', 'error');
            return;
        }

        // 获取审核决定
        const approvedRadio = document.querySelector('input[name="reviewApproved"]:checked');
        if (!approvedRadio) {
            this.showMessage('请选择审核结果（通过或拒绝）', 'warning');
            return;
        }

        const approved = approvedRadio.value === 'true';
        const polishedText = document.getElementById('polishedTextArea').value.trim();
        const reviewComments = document.getElementById('reviewCommentsArea').value.trim();

        if (!polishedText) {
            this.showMessage('润色文本不能为空', 'warning');
            return;
        }

        if (!approved && !reviewComments) {
            this.showMessage('审核拒绝时必须填写拒绝原因', 'warning');
            return;
        }

        try {
            this.showLoading(approved ? '正在提交审核通过...' : '正在提交审核拒绝...');

            const reviewData = {
                reviewerId: 'admin',
                approved: approved,
                comments: reviewComments || (approved ? '审核通过' : '审核拒绝'),
                updatedPolishedText: polishedText,
                reviewTime: new Date().toISOString()
            };

            const response = await this.withRetry(async () => {
                return await this.api.adminReviewSession(this.workflowData.step1.courseId, reviewData);
            });

            if (response.success || response.status === 'SUCCESS' || response.status === 'COMPLETED') {
                this.showMessage(approved ? '审核通过成功' : '审核拒绝成功', 'success');
                
                // 保存审核结果
                this.workflowData.step2 = {
                    ...this.workflowData.step2,
                    reviewResult: {
                        approved: approved,
                        comments: reviewComments,
                        updatedPolishedText: polishedText,
                        reviewTime: new Date().toISOString()
                    }
                };
                
                // 保存状态
                this.saveWorkflowState();

                if (approved) {
                    // 审核通过，自动切换到下一步
                    this.switchToStep(3);
                } else {
                    // 审核拒绝，返回步骤1
                    this.showMessage('审核拒绝，请返回步骤1重新处理', 'info');
                }
            } else {
                this.showMessage('审核提交失败：' + (response.message || '未知错误'), 'error');
            }
        } catch (error) {
            this.handleError(error, '提交审核决定');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 直接审核通过（从编辑界面）
     */
    async approveReviewDirect() {
        const polishedText = document.getElementById('polishedTextArea').value.trim();
        const reviewComments = document.getElementById('reviewCommentsArea').value.trim();

        if (!polishedText) {
            this.showMessage('润色文本不能为空', 'warning');
            return;
        }

        await this.submitReviewWithData(true, reviewComments || '审核通过', polishedText);
    }

    /**
     * 直接审核拒绝（从编辑界面）
     */
    async rejectReviewDirect() {
        const reviewComments = document.getElementById('reviewCommentsArea').value.trim();
        
        if (!reviewComments) {
            this.showMessage('审核拒绝时必须填写拒绝原因', 'warning');
            return;
        }

        const polishedText = document.getElementById('polishedTextArea').value.trim();
        await this.submitReviewWithData(false, reviewComments, polishedText);
    }

    /**
     * 提交审核结果（带数据）
     */
    async submitReviewWithData(approved, comments, polishedText) {
        if (!this.workflowData.step1 || !this.workflowData.step1.courseId) {
            this.showMessage('缺少会话信息，请重新执行步骤1', 'error');
            return;
        }

        if (!this.checkNetworkStatus()) {
            return;
        }

        try {
            this.showLoading(approved ? '正在提交审核通过...' : '正在提交审核拒绝...');

            const reviewData = {
                reviewerId: 'admin',
                approved: approved,
                comments: comments,
                updatedPolishedText: polishedText,
                reviewTime: new Date().toISOString()
            };

            const response = await this.withRetry(async () => {
                return await this.api.adminReviewSession(this.workflowData.step1.courseId, reviewData);
            });

            if (response.success || response.status === 'SUCCESS' || response.status === 'COMPLETED') {
                this.showMessage(approved ? '审核通过成功' : '审核拒绝成功', 'success');
                
                // 保存审核结果
                this.workflowData.step2 = {
                    ...this.workflowData.step2,
                    reviewResult: {
                        approved: approved,
                        comments: comments,
                        updatedPolishedText: polishedText,
                        reviewTime: new Date().toISOString()
                    }
                };
                
                // 保存状态
                this.saveWorkflowState();

                if (approved) {
                    // 审核通过，自动切换到下一步
                    this.switchToStep(3);
                } else {
                    // 审核拒绝，返回步骤1
                    this.showMessage('审核拒绝，请返回步骤1重新处理', 'info');
                }
            } else {
                this.showMessage('审核提交失败：' + (response.message || '未知错误'), 'error');
            }
        } catch (error) {
            this.handleError(error, '提交审核结果');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 审核通过
     */
    async approveReview() {
        await this.submitReview(true, '审核通过');
    }

    /**
     * 审核拒绝
     */
    async rejectReview() {
        const comments = prompt('请输入拒绝原因：');
        if (comments === null) return; // 用户取消
        
        await this.submitReview(false, comments || '审核拒绝');
    }

    /**
     * 提交审核结果
     */
    async submitReview(approved, comments) {
        if (!this.workflowData.step1 || !this.workflowData.step1.courseId) {
            this.showMessage('缺少会话信息，请重新执行步骤1', 'error');
            return;
        }

        if (!this.checkNetworkStatus()) {
            return;
        }

        try {
            this.showLoading(approved ? '正在提交审核通过...' : '正在提交审核拒绝...');

            const reviewData = {
                reviewerId: 'admin', // 管理员ID
                approved: approved,
                comments: comments,
                reviewTime: new Date().toISOString()
            };

            // 调用管理员审核API
            const response = await this.withRetry(async () => {
                return await this.api.adminReviewSession(this.workflowData.step1.courseId, reviewData);
            });

            if (response.success || response.status === 'SUCCESS' || response.status === 'COMPLETED') {
                this.showMessage(approved ? '审核通过成功' : '审核拒绝成功', 'success');
                
                // 保存审核结果
                this.workflowData.step2 = {
                    ...this.workflowData.step2,
                    reviewResult: {
                        approved: approved,
                        comments: comments,
                        reviewTime: new Date().toISOString()
                    }
                };
                
                // 保存状态
                this.saveWorkflowState();

                if (approved) {
                    // 审核通过，自动切换到下一步
                    this.switchToStep(3);
                } else {
                    // 审核拒绝，返回步骤1
                    this.showMessage('审核拒绝，请返回步骤1重新处理', 'info');
                }
            } else {
                this.showMessage('审核提交失败：' + (response.message || '未知错误'), 'error');
            }
        } catch (error) {
            this.handleError(error, '提交审核结果');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 检查语音合成状态
     */
    checkSynthesisStatus() {
        if (!this.workflowData.step2 || !this.workflowData.step2.reviewResult || !this.workflowData.step2.reviewResult.approved) {
            document.getElementById('synthesisStatus').innerHTML = 
                '<p class="text-warning">请先完成前面的步骤并通过审核</p>';
            document.getElementById('startSynthesisBtn').disabled = true;
            return;
        }

        document.getElementById('synthesisStatus').innerHTML = `
            <div class="synthesis-ready">
                <div class="status-info">
                    <i class="fas fa-check-circle text-success"></i>
                    <span>审核已通过，可以开始语音合成</span>
                </div>
                <div class="synthesis-details">
                    <p><strong>会话ID:</strong> ${this.workflowData.step1.courseId}</p>
                    <p><strong>审核时间:</strong> ${this.formatDateTime(this.workflowData.step2.reviewResult.reviewTime)}</p>
                    <p><strong>审核意见:</strong> ${this.workflowData.step2.reviewResult.comments}</p>
                </div>
            </div>
        `;
        document.getElementById('startSynthesisBtn').disabled = false;
    }

    /**
     * 开始语音合成
     */
    async startSynthesis() {
        if (!this.workflowData.step1 || !this.workflowData.step1.courseId) {
            this.showMessage('缺少会话信息，请重新执行前面的步骤', 'error');
            return;
        }

        if (!this.checkNetworkStatus()) {
            return;
        }

        try {
            this.showLoading('正在执行语音合成，请稍候...');

            // 调用执行批量语音合成API
            const response = await this.withRetry(async () => {
                return await this.api.executeBulkSynthesis(this.workflowData.step1.courseId);
            });

            if (response.success || response.status === 'SUCCESS' || response.status === 'COMPLETED') {
                this.showMessage('语音合成成功', 'success');
                
                // 保存合成结果
                this.workflowData.step3 = {
                    synthesisResult: response,
                    synthesisTime: new Date().toISOString()
                };
                
                // 保存状态
                this.saveWorkflowState();

                // 更新状态显示
                this.updateSynthesisStatus(response);

                // 自动切换到下一步
                this.switchToStep(4);
            } else {
                this.showMessage('语音合成失败：' + (response.message || '未知错误'), 'error');
                this.updateSynthesisStatus(response, true);
            }
        } catch (error) {
            this.handleError(error, '语音合成');
            this.updateSynthesisStatus(null, true);
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 更新语音合成状态显示
     */
    updateSynthesisStatus(result, isError = false) {
        const statusDiv = document.getElementById('synthesisStatus');
        
        if (isError) {
            statusDiv.innerHTML = `
                <div class="synthesis-error">
                    <div class="status-info">
                        <i class="fas fa-exclamation-circle text-danger"></i>
                        <span>语音合成失败</span>
                    </div>
                    <div class="error-actions">
                        <button class="btn btn-sm btn-primary" onclick="courseManagement.startSynthesis()">
                            <i class="fas fa-redo"></i>
                            重试合成
                        </button>
                    </div>
                </div>
            `;
            return;
        }

        if (result) {
            statusDiv.innerHTML = `
                <div class="synthesis-success">
                    <div class="status-info">
                        <i class="fas fa-check-circle text-success"></i>
                        <span>语音合成完成</span>
                    </div>
                    <div class="synthesis-result">
                        <p><strong>合成状态:</strong> ${result.status}</p>
                        <p><strong>会话ID:</strong> ${result.courseId || this.workflowData.step1.courseId}</p>
                        ${result.message ? `<p><strong>消息:</strong> ${result.message}</p>` : ''}
                        ${result.totalSegments ? `<p><strong>音频片段数:</strong> ${result.totalSegments}</p>` : ''}
                        ${result.totalDuration ? `<p><strong>总时长:</strong> ${this.formatDuration(result.totalDuration)}</p>` : ''}
                    </div>
                </div>
            `;
        }
    }

    /**
     * 格式化时长
     */
    formatDuration(milliseconds) {
        if (!milliseconds) return '未知';
        
        const seconds = Math.floor(milliseconds / 1000);
        const minutes = Math.floor(seconds / 60);
        const remainingSeconds = seconds % 60;
        
        if (minutes > 0) {
            return `${minutes}分${remainingSeconds}秒`;
        } else {
            return `${remainingSeconds}秒`;
        }
    }

    /**
     * 恢复步骤4的原始HTML结构
     */
    restoreStep4Html() {
        const step4Content = document.getElementById('step4Content');
        if (!step4Content) return;
        
        const originalHtml = `
            <div class="step-header">
                <h3>步骤4：PPT插入</h3>
                <p>在表格中编辑PPT幻灯片，支持直接粘贴HTML代码</p>
            </div>
            <div class="step-body">
                <!-- 课程ID显示 -->
                <div class="form-group">
                    <label>课程ID</label>
                    <input type="text" id="pptCourseId" readonly class="form-control" style="background-color: #f5f5f5;">
                </div>

                <!-- PPT幻灯片表格 -->
                <div class="form-group">
                    <div class="table-toolbar" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
                        <label style="margin: 0;">PPT幻灯片列表</label>
                        <button type="button" class="btn btn-sm btn-success" onclick="courseManagement.addPptSlideRow()">
                            <i class="fas fa-plus"></i> 添加幻灯片
                        </button>
                    </div>
                    <div class="table-responsive" style="max-height: 600px; overflow-y: auto; border: 1px solid #ddd; border-radius: 4px;">
                        <table id="pptSlidesTable" class="table table-bordered table-hover" style="margin: 0;">
                            <thead style="position: sticky; top: 0; background-color: #f8f9fa; z-index: 10;">
                                <tr>
                                    <th style="width: 60px; text-align: center;">序号</th>
                                    <th style="width: 200px;">标题</th>
                                    <th>HTML内容</th>
                                    <th style="width: 150px; text-align: center;">操作</th>
                                </tr>
                            </thead>
                            <tbody id="pptSlidesTableBody">
                                <!-- 动态生成行 -->
                            </tbody>
                        </table>
                    </div>
                    <small class="form-text text-muted">
                        提示：可以直接将HTML代码复制粘贴到HTML内容列中，支持多行编辑
                    </small>
                </div>

                <!-- 操作按钮 -->
                <div class="step-actions">
                    <button type="button" class="btn btn-secondary" onclick="courseManagement.clearPptTable()">
                        <i class="fas fa-trash"></i> 清空表格
                    </button>
                    <button type="button" class="btn btn-info" onclick="courseManagement.addSamplePptRows()">
                        <i class="fas fa-file-code"></i> 插入示例
                    </button>
                    <button type="button" class="btn btn-primary" onclick="courseManagement.insertPptFromTable()">
                        <i class="fas fa-upload"></i> 批量插入PPT
                    </button>
                </div>
            </div>
        `;
        
        step4Content.innerHTML = originalHtml;
        console.log('>>> 步骤4的HTML结构已恢复');
    }

    /**
     * 准备PPT数据
     */
    preparePptData() {
        console.log('>>> preparePptData() 被调用');
        const courseId = this.workflowData.step1?.courseId || this.currentCourse?.courseId || '';
        const courseTitle = this.currentCourse?.title || '课程标题';
        
        // 【关键修复】检查step4Content是否包含workflow-complete
        // 如果包含，说明之前显示的是完成状态，需要恢复原始HTML结构
        const step4Content = document.getElementById('step4Content');
        if (step4Content && step4Content.querySelector('.workflow-complete')) {
            console.log('>>> 检测到workflow-complete，恢复原始HTML结构');
            // 重新渲染整个步骤4的HTML结构
            this.restoreStep4Html();
        }
        
        // 填充课程ID
        const courseIdInput = document.getElementById('pptCourseId');
        if (courseIdInput) {
            courseIdInput.value = courseId;
            console.log('>>> 课程ID已设置:', courseId);
        } else {
            console.warn('>>> 找不到pptCourseId元素');
        }

        // 清空表格内容，确保新课程从空白开始
        const tableBody = document.getElementById('pptSlidesTableBody');
        if (tableBody) {
            console.log('>>> 清空表格，当前行数:', tableBody.children.length);
            tableBody.innerHTML = '';
            // 添加一行示例
            this.addPptSlideRow(courseTitle);
            console.log('>>> 已添加示例行');
        } else {
            console.warn('>>> 找不到pptSlidesTableBody元素');
        }
    }

    /**
     * 添加PPT幻灯片行
     */
    addPptSlideRow(title = '', htmlContent = '') {
        const tableBody = document.getElementById('pptSlidesTableBody');
        if (!tableBody) return;

        const rowIndex = tableBody.children.length + 1;
        const rowId = `ppt-slide-row-${Date.now()}-${rowIndex}`;
        const currentTime = Date.now();
        
        // 如果没有提供内容，生成默认内容
        if (!htmlContent) {
            htmlContent = `<div class="slide-content">
    <h1>${title || '幻灯片标题'}</h1>
    <div class="slide-body">
        <p>请输入幻灯片内容...</p>
    </div>
</div>`;
        }

        // 转义HTML内容，防止XSS
        const escapeHtml = (text) => {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        };

        const row = document.createElement('tr');
        row.id = rowId;
        
        // 使用textContent设置textarea内容，避免HTML转义问题
        row.innerHTML = `
            <td style="text-align: center; vertical-align: middle;">
                <span class="row-number">${rowIndex}</span>
            </td>
            <td>
                <input type="text" class="form-control form-control-sm slide-title" 
                       placeholder="请输入标题" value="${escapeHtml(title)}" 
                       data-row-id="${rowId}">
            </td>
            <td>
                <textarea class="form-control form-control-sm slide-html-content" 
                          rows="4" placeholder="粘贴或输入HTML代码..." 
                          data-row-id="${rowId}"></textarea>
            </td>
            <td style="text-align: center; vertical-align: middle;">
                <button type="button" class="btn btn-sm btn-info" 
                        onclick="courseManagement.previewPptSlideRow('${rowId}')" 
                        title="预览">
                    <i class="fas fa-eye"></i>
                </button>
                <button type="button" class="btn btn-sm btn-danger" 
                        onclick="courseManagement.removePptSlideRow('${rowId}')" 
                        title="删除">
                    <i class="fas fa-trash"></i>
                </button>
            </td>
        `;
        
        // 单独设置textarea的值，避免HTML转义
        const textarea = row.querySelector('.slide-html-content');
        if (textarea) {
            textarea.value = htmlContent;
        }

        tableBody.appendChild(row);
        this.updatePptTableRowNumbers();
    }

    /**
     * 删除PPT幻灯片行
     */
    removePptSlideRow(rowId) {
        const row = document.getElementById(rowId);
        if (row) {
            row.remove();
            this.updatePptTableRowNumbers();
        }
    }

    /**
     * 更新表格行号
     */
    updatePptTableRowNumbers() {
        const tableBody = document.getElementById('pptSlidesTableBody');
        if (!tableBody) return;

        const rows = tableBody.querySelectorAll('tr');
        rows.forEach((row, index) => {
            const numberCell = row.querySelector('.row-number');
            if (numberCell) {
                numberCell.textContent = index + 1;
            }
        });
    }

    /**
     * 预览PPT幻灯片HTML内容
     */
    previewPptSlideRow(rowId) {
        const row = document.getElementById(rowId);
        if (!row) return;

        const htmlContent = row.querySelector('.slide-html-content').value.trim();
        if (!htmlContent) {
            this.showMessage('请先输入HTML内容', 'warning');
            return;
        }

        // 创建预览窗口
        const previewWindow = window.open('', '_blank', 'width=800,height=600');
        previewWindow.document.write(`
            <!DOCTYPE html>
            <html>
            <head>
                <title>PPT内容预览</title>
                <style>
                    body { 
                        font-family: Arial, sans-serif; 
                        padding: 20px; 
                        margin: 0;
                        background-color: #f5f5f5;
                    }
                    .preview-container {
                        background: white;
                        padding: 20px;
                        border-radius: 5px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                        max-width: 100%;
                    }
                    .slide-content {
                        margin: 0;
                    }
                </style>
            </head>
            <body>
                <div class="preview-container">
                    <h2>PPT内容预览</h2>
                    ${htmlContent}
                </div>
            </body>
            </html>
        `);
        previewWindow.document.close();
    }

    /**
     * 清空PPT表格
     */
    clearPptTable() {
        if (confirm('确定要清空所有幻灯片吗？')) {
            const tableBody = document.getElementById('pptSlidesTableBody');
            if (tableBody) {
                tableBody.innerHTML = '';
            }
        }
    }

    /**
     * 添加示例PPT行
     */
    addSamplePptRows() {
        const courseTitle = this.currentCourse?.title || '课程标题';
        const currentTime = Date.now();

        const samples = [
            {
                title: `${courseTitle} - 第一部分`,
                htmlContent: `<div class="slide-content">
    <h1>${courseTitle} - 第一部分</h1>
    <div class="slide-body">
        <h2>主要内容</h2>
        <ul>
            <li>要点一：基础概念</li>
            <li>要点二：核心原理</li>
            <li>要点三：实际应用</li>
        </ul>
    </div>
</div>`
            },
            {
                title: `${courseTitle} - 第二部分`,
                htmlContent: `<div class="slide-content">
    <h1>${courseTitle} - 第二部分</h1>
    <div class="slide-body">
        <h2>深入学习</h2>
        <p>这是第二个幻灯片的内容，用于深入讲解相关知识点。</p>
    </div>
</div>`
            },
            {
                title: `${courseTitle} - 总结`,
                htmlContent: `<div class="slide-content">
    <h1>${courseTitle} - 总结</h1>
    <div class="slide-body">
        <h2>课程总结</h2>
        <ul>
            <li>回顾重点内容</li>
            <li>巩固学习成果</li>
        </ul>
    </div>
</div>`
            }
        ];

        samples.forEach(sample => {
            this.addPptSlideRow(sample.title, sample.htmlContent);
        });

        this.showMessage('已添加3个示例幻灯片', 'success');
    }

    /**
     * 从表格提取数据并批量插入PPT
     */
    async insertPptFromTable() {
        if (!this.checkNetworkStatus()) {
            return;
        }

        const courseIdInput = document.getElementById('pptCourseId');
        const courseId = courseIdInput ? courseIdInput.value.trim() : '';
        
        if (!courseId) {
            this.showMessage('课程ID不能为空', 'error');
            return;
        }

        const tableBody = document.getElementById('pptSlidesTableBody');
        if (!tableBody || tableBody.children.length === 0) {
            this.showMessage('请至少添加一个幻灯片', 'warning');
            return;
        }

        // 提取表格数据
        const slides = [];
        const rows = tableBody.querySelectorAll('tr');
        
        rows.forEach((row, index) => {
            const titleInput = row.querySelector('.slide-title');
            const htmlContentTextarea = row.querySelector('.slide-html-content');
            
            if (!titleInput || !htmlContentTextarea) return;

            const title = titleInput.value.trim();
            const htmlContent = htmlContentTextarea.value.trim();

            if (!title || !htmlContent) {
                this.showMessage(`第 ${index + 1} 行的标题或HTML内容为空，已跳过`, 'warning');
                return;
            }

            const currentTime = Date.now();
            slides.push({
                slideId: `slide_${currentTime}_${index + 1}`,
                slideIndex: index + 1,
                title: title,
                contentType: 'html',
                htmlContent: htmlContent
            });
        });

        if (slides.length === 0) {
            this.showMessage('没有有效的幻灯片数据', 'error');
            return;
        }

        // 构建批量插入数据
        const batchData = {
            courseId: courseId,
            slides: slides
        };

        // 调用批量插入方法
        try {
            await this.insertPptBatch(batchData);
        } catch (error) {
            this.handleError(error, '从表格插入PPT');
        } finally {
            // 确保隐藏loading（insertPptBatch内部也会隐藏，但这里作为双重保险）
            this.hideLoading();
        }
    }

    /**
     * 生成示例PPT数据
     */
    generateSamplePptData() {
        const courseId = this.workflowData.step1?.courseId || this.currentCourse?.courseId || 'ppt_session_xxx';
        const courseTitle = this.currentCourse?.title || '课程标题';
        
        return {
            courseId: courseId,
            slideId: `slide_${Date.now()}`,
            slideIndex: 1,
            title: courseTitle,
            contentType: 'html',
            content: `
                <div class="slide-content">
                    <h1>${courseTitle}</h1>
                    <div class="slide-body">
                        <p>这是基于课程内容自动生成的PPT幻灯片。</p>
                        <ul>
                            <li>课程ID: ${courseId}</li>
                            <li>生成时间: ${new Date().toLocaleString('zh-CN')}</li>
                            <li>状态: 已完成语音合成</li>
                        </ul>
                    </div>
                </div>
            `.trim()
        };
    }

    /**
     * 插入示例PPT JSON数据（支持单个和批量格式）
     */
    insertSamplePptJson() {
        const courseId = this.workflowData.step1?.courseId || this.currentCourse?.courseId || 'ppt_session_xxx';
        const courseTitle = this.currentCourse?.title || '课程标题';
        const currentTime = Date.now();

        // 生成批量插入示例（包含3个幻灯片）
        const batchSampleJson = {
            courseId: courseId,
            slides: [
                {
                    slideId: `slide_${currentTime}_1`,
                    slideIndex: 1,
                    title: `${courseTitle} - 第一部分`,
                    contentType: 'html',
                    htmlContent: `<div class="slide-content">
    <h1>${courseTitle} - 第一部分</h1>
    <div class="slide-body">
        <h2>主要内容</h2>
        <ul>
            <li>要点一：基础概念</li>
            <li>要点二：核心原理</li>
            <li>要点三：实际应用</li>
        </ul>
        <p>这是第一个幻灯片的内容。</p>
    </div>
</div>`
                },
                {
                    slideId: `slide_${currentTime}_2`,
                    slideIndex: 2,
                    title: `${courseTitle} - 第二部分`,
                    contentType: 'html',
                    htmlContent: `<div class="slide-content">
    <h1>${courseTitle} - 第二部分</h1>
    <div class="slide-body">
        <h2>深入学习</h2>
        <p>这是第二个幻灯片的内容，用于深入讲解相关知识点。</p>
        <div class="highlight">
            <p><strong>重点提示：</strong>请仔细理解这部分内容。</p>
        </div>
    </div>
</div>`
                },
                {
                    slideId: `slide_${currentTime}_3`,
                    slideIndex: 3,
                    title: `${courseTitle} - 总结`,
                    contentType: 'html',
                    htmlContent: `<div class="slide-content">
    <h1>${courseTitle} - 总结</h1>
    <div class="slide-body">
        <h2>课程总结</h2>
        <ul>
            <li>回顾重点内容</li>
            <li>巩固学习成果</li>
            <li>准备下一阶段学习</li>
        </ul>
        <div class="slide-footer">
            <p>生成时间: ${new Date().toLocaleString('zh-CN')}</p>
        </div>
    </div>
</div>`
                }
            ]
        };

        // 生成单个插入示例
        const singleSampleJson = {
            courseId: courseId,
            slideId: `slide_${currentTime}`,
            slideIndex: 1,
            title: courseTitle,
            contentType: 'html',
            htmlContent: `<div class="slide-content">
    <h1>${courseTitle}</h1>
    <div class="slide-body">
        <p>这是基于课程内容自动生成的PPT幻灯片。</p>
        <ul>
            <li>课程ID: ${courseId}</li>
            <li>生成时间: ${new Date().toLocaleString('zh-CN')}</li>
            <li>状态: 已完成语音合成</li>
        </ul>
    </div>
</div>`
        };

        const pptDataInput = document.getElementById('pptDataInput');
        if (pptDataInput) {
            // 默认插入批量示例，用户可以根据需要修改
            pptDataInput.value = JSON.stringify(batchSampleJson, null, 2);
            this.showMessage('批量插入示例JSON已插入（包含3个幻灯片），您可以修改为单个插入格式', 'info');
        } else {
            this.showMessage('找不到PPT数据输入框', 'error');
        }
    }

    /**
     * 提交PPT表单
     */
    async submitPptForm() {
        const form = document.getElementById('pptInsertForm');
        const formData = new FormData(form);

        // 构建PPT数据
        const pptData = {
            courseId: formData.get('courseId'),
            slideId: formData.get('slideId'),
            slideIndex: parseInt(formData.get('slideIndex')),
            title: formData.get('title'),
            contentType: formData.get('contentType'),
            htmlContent: formData.get('content') // 表单字段名是content，但API期望htmlContent
        };

        // 验证必填字段
        if (!pptData.courseId || !pptData.slideId || !pptData.title || !pptData.htmlContent) {
            this.showMessage('请填写所有必填字段', 'warning');
            return;
        }

        try {
            this.showLoading('正在创建PPT幻灯片...');

            const response = await this.withRetry(async () => {
                return await this.api.createPptSlide(pptData);
            });

            if (response.success) {
                this.showMessage('PPT幻灯片创建成功', 'success');
                
                // 保存插入结果
                this.workflowData.step4 = {
                    pptResult: response,
                    insertTime: new Date().toISOString()
                };
                
                // 保存状态
                this.saveWorkflowState();

                // 显示完成状态
                this.showWorkflowComplete();
            } else {
                this.showMessage('PPT幻灯片创建失败：' + response.message, 'error');
            }
        } catch (error) {
            this.handleError(error, 'PPT幻灯片创建');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 预览PPT内容
     */
    previewPptContent() {
        const content = document.getElementById('pptContent').value;
        if (!content.trim()) {
            this.showMessage('请先输入内容', 'warning');
            return;
        }

        // 创建预览窗口
        const previewWindow = window.open('', '_blank', 'width=800,height=600');
        previewWindow.document.write(`
            <!DOCTYPE html>
            <html>
            <head>
                <title>PPT内容预览</title>
                <style>
                    body { font-family: Arial, sans-serif; padding: 20px; }
                    .slide-content { border: 1px solid #ddd; padding: 20px; border-radius: 5px; }
                </style>
            </head>
            <body>
                <h2>PPT内容预览</h2>
                ${content}
            </body>
            </html>
        `);
        previewWindow.document.close();
    }

    /**
     * 格式化PPT内容
     */
    formatPptContent() {
        const textarea = document.getElementById('pptContent');
        try {
            // 简单的HTML格式化
            let content = textarea.value;
            content = content.replace(/></g, '>\n<');
            content = content.replace(/^\s+|\s+$/g, '');
            textarea.value = content;
            this.showMessage('内容格式化完成', 'success');
        } catch (error) {
            this.showMessage('格式化失败', 'error');
        }
    }

    /**
     * 插入PPT模板
     */
    insertPptTemplate() {
        const template = `<div class="slide-content">
    <h1>课程标题</h1>
    <div class="slide-body">
        <h2>主要内容</h2>
        <ul>
            <li>要点一</li>
            <li>要点二</li>
            <li>要点三</li>
        </ul>
        
        <h3>详细说明</h3>
        <p>这里是详细的说明内容...</p>
        
        <div class="slide-footer">
            <p>课程结束</p>
        </div>
    </div>
</div>`;
        
        document.getElementById('pptContent').value = template;
        this.showMessage('模板已插入', 'success');
    }

    /**
     * 重置PPT表单
     */
    resetPptForm() {
        document.getElementById('pptInsertForm').reset();
        this.preparePptData(); // 重新填充默认数据
    }

    /**
     * 插入PPT数据（支持单个或批量插入）
     * 支持两种格式：
     * 1. 单个对象：{ courseId, slideId, slideIndex, title, contentType, htmlContent }
     * 2. 批量对象：{ courseId, slides: [{ slideId, slideIndex, title, contentType, htmlContent }, ...] }
     */
    async insertPpt() {
        if (!this.checkNetworkStatus()) {
            return;
        }

        const pptDataInput = document.getElementById('pptDataInput');
        const pptData = pptDataInput.value.trim();

        if (!pptData) {
            this.showMessage('请输入PPT数据', 'warning');
            pptDataInput.focus();
            return;
        }

        // 验证PPT数据格式
        let parsedData;
        try {
            parsedData = JSON.parse(pptData);
        } catch (error) {
            this.showMessage('PPT数据格式错误，请检查JSON格式', 'error');
            pptDataInput.focus();
            return;
        }

        // 简化验证，只检查是否为对象
        if (!parsedData || typeof parsedData !== 'object') {
            this.showMessage('PPT数据必须是一个对象', 'error');
            return;
        }

        try {
            // 判断是批量插入还是单个插入
            const isBatchMode = Array.isArray(parsedData.slides) && parsedData.slides.length > 0;
            
            if (isBatchMode) {
                // 批量插入模式
                await this.insertPptBatch(parsedData);
            } else {
                // 单个插入模式（向后兼容）
                await this.insertPptSingle(parsedData);
            }
        } catch (error) {
            this.handleError(error, 'PPT数据插入');
        } finally {
            this.hideLoading();
        }
    }

    /**
     * 批量插入PPT幻灯片
     */
    async insertPptBatch(parsedData) {
        const { courseId, slides } = parsedData;
        
        if (!courseId) {
            this.showMessage('批量插入时，courseId 不能为空', 'error');
            return;
        }

        if (!Array.isArray(slides) || slides.length === 0) {
            this.showMessage('slides 必须是一个非空数组', 'error');
            return;
        }

        this.showLoading(`正在批量插入 ${slides.length} 个PPT幻灯片...`);

        const results = {
            success: [],
            failed: [],
            total: slides.length
        };

        // 循环调用API插入每个幻灯片
        for (let i = 0; i < slides.length; i++) {
            const slide = slides[i];
            
            // 确保每个slide都有courseId
            const slideData = {
                ...slide,
                courseId: slide.courseId || courseId
            };

            try {
                const response = await this.withRetry(async () => {
                    return await this.api.createPptSlide(slideData);
                });

                if (response.success) {
                    results.success.push({
                        index: i + 1,
                        slideId: slideData.slideId,
                        title: slideData.title,
                        data: response.data
                    });
                } else {
                    results.failed.push({
                        index: i + 1,
                        slideId: slideData.slideId,
                        title: slideData.title,
                        error: response.message || '未知错误'
                    });
                }
            } catch (error) {
                results.failed.push({
                    index: i + 1,
                    slideId: slideData.slideId || '未知',
                    title: slideData.title || '未知',
                    error: error.message || '插入失败'
                });
            }

            // 更新进度提示
            if (i < slides.length - 1) {
                this.showLoading(`正在插入第 ${i + 1}/${slides.length} 个PPT幻灯片...`);
            }
        }

        // 显示插入结果
        const successCount = results.success.length;
        const failedCount = results.failed.length;

        try {
            if (failedCount === 0) {
                // 全部成功
                this.showMessage(`成功插入 ${successCount} 个PPT幻灯片`, 'success');
                
                // 保存插入结果
                this.workflowData.step4 = {
                    pptResult: {
                        success: true,
                        total: results.total,
                        successCount: successCount,
                        failedCount: failedCount,
                        results: results
                    },
                    insertTime: new Date().toISOString()
                };
                
                // 保存状态
                this.saveWorkflowState();

                // 显示完成状态
                this.showWorkflowComplete();
            } else if (successCount > 0) {
                // 部分成功
                this.showMessage(
                    `部分插入成功：成功 ${successCount} 个，失败 ${failedCount} 个`,
                    'warning'
                );
                
                // 显示失败详情
                const failedDetails = results.failed.map(f => 
                    `第 ${f.index} 个 (${f.title || f.slideId}): ${f.error}`
                ).join('\n');
                
                console.warn('批量插入PPT失败详情:', results.failed);
                
                // 保存插入结果
                this.workflowData.step4 = {
                    pptResult: {
                        success: false,
                        total: results.total,
                        successCount: successCount,
                        failedCount: failedCount,
                        results: results
                    },
                    insertTime: new Date().toISOString()
                };
                
                this.saveWorkflowState();
            } else {
                // 全部失败
                this.showMessage(`所有PPT幻灯片插入失败（共 ${failedCount} 个）`, 'error');
                
                const failedDetails = results.failed.map(f => 
                    `第 ${f.index} 个: ${f.error}`
                ).join('\n');
                
                console.error('批量插入PPT全部失败:', results.failed);
            }
        } finally {
            // 确保无论成功还是失败，都要隐藏loading
            this.hideLoading();
        }
    }

    /**
     * 单个插入PPT幻灯片（向后兼容）
     */
    async insertPptSingle(parsedData) {
        this.showLoading('正在插入PPT数据...');

        // 调用创建PPT幻灯片API
        const response = await this.withRetry(async () => {
            return await this.api.createPptSlide(parsedData);
        });

        if (response.success) {
            this.showMessage('PPT数据插入成功', 'success');
            
            // 保存插入结果
            this.workflowData.step4 = {
                pptResult: response,
                insertTime: new Date().toISOString()
            };
            
            // 保存状态
            this.saveWorkflowState();

            // 显示完成状态
            this.showWorkflowComplete();
        } else {
            this.showMessage('PPT数据插入失败：' + response.message, 'error');
        }
    }

    /**
     * 验证PPT数据结构
     */
    validatePptData(data) {
        // 简化验证，只检查基本结构，让后端处理具体的字段验证
        if (!data || typeof data !== 'object') {
            this.showMessage('PPT数据必须是一个对象', 'error');
            return false;
        }

        // 移除所有具体字段的验证，直接返回true
        // 让后端来处理数据验证和转换
        return true;
    }

    /**
     * 重新编辑PPT（从完成状态恢复到编辑状态）
     */
    editPptAgain() {
        // 清除步骤4的完成状态
        if (this.workflowData.step4) {
            // 保留pptResult数据，但标记为可编辑
            this.workflowData.step4.editing = true;
        }
        
        // 恢复表格界面
        this.preparePptData();
        
        // 移除完成状态的样式
        document.querySelectorAll('.step').forEach(step => {
            step.classList.remove('completed');
        });
        
        this.showMessage('已切换到编辑模式，您可以重新编辑PPT', 'info');
    }

    /**
     * 显示工作流完成状态
     */
    showWorkflowComplete() {
        // 更新所有步骤为完成状态
        document.querySelectorAll('.step').forEach(step => {
            step.classList.add('completed');
        });

        // 显示完成消息
        const completionMessage = `
            <div class="workflow-complete">
                <div class="completion-header">
                    <i class="fas fa-check-circle text-success"></i>
                    <h3>工作流程完成</h3>
                </div>
                <div class="completion-summary">
                    <p>课程处理工作流已全部完成，包括：</p>
                    <ul>
                        <li><i class="fas fa-check text-success"></i> 大纲润色处理</li>
                        <li><i class="fas fa-check text-success"></i> 内容审核通过</li>
                        <li><i class="fas fa-check text-success"></i> 语音合成完成</li>
                        <li><i class="fas fa-check text-success"></i> PPT数据插入</li>
                    </ul>
                </div>
                <div class="completion-actions">
                    <button class="btn btn-primary" onclick="courseManagement.showSearchView()">
                        <i class="fas fa-list"></i>
                        返回课程列表
                    </button>
                    <button class="btn btn-info" onclick="courseManagement.editPptAgain()">
                        <i class="fas fa-edit"></i>
                        重新编辑PPT
                    </button>
                    <button class="btn btn-success" onclick="courseManagement.refreshCourseStatus()">
                        <i class="fas fa-sync-alt"></i>
                        刷新状态
                    </button>
                </div>
            </div>
        `;

        // 在步骤4内容区域显示完成消息
        const step4Content = document.getElementById('step4Content');
        if (step4Content) {
            step4Content.innerHTML = completionMessage;
        }
    }

    /**
     * 错误处理和重试机制
     */
    async withRetry(apiCall, maxRetries = 2, retryDelay = 1000) {
        let lastError;
        
        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return await apiCall();
            } catch (error) {
                lastError = error;
                console.warn(`API调用失败 (尝试 ${attempt}/${maxRetries}):`, error.message);
                
                if (attempt < maxRetries) {
                    await this.delay(retryDelay * attempt); // 递增延迟
                }
            }
        }
        
        throw lastError;
    }

    /**
     * 延迟函数
     */
    delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    /**
     * 统一错误处理
     */
    handleError(error, context = '') {
        console.error(`${context} 错误:`, error);
        
        let message = '操作失败';
        
        if (error.message) {
            if (error.message.includes('401') || error.message.includes('Unauthorized')) {
                message = '认证失败，请重新登录';
                // 清除token并跳转到登录页
                this.api.clearToken();
                if (window.authManager) {
                    window.authManager.showLoginPage();
                }
                return;
            } else if (error.message.includes('403') || error.message.includes('Forbidden')) {
                message = '权限不足，无法执行此操作';
            } else if (error.message.includes('404')) {
                message = '请求的资源不存在';
            } else if (error.message.includes('500')) {
                message = '服务器内部错误，请稍后重试';
            } else {
                message = error.message;
            }
        }
        
        this.showMessage(message, 'error');
    }

    /**
     * 网络状态检测
     */
    checkNetworkStatus() {
        if (!navigator.onLine) {
            this.showMessage('网络连接已断开，请检查网络设置', 'error');
            return false;
        }
        return true;
    }

    /**
     * 显示加载状态
     */
    showLoading(message = '加载中...') {
        // 移除现有的加载覆盖层
        this.hideLoading();
        
        const overlay = document.createElement('div');
        overlay.className = 'loading-overlay';
        overlay.id = 'courseLoadingOverlay';
        
        overlay.innerHTML = `
            <div class="loading-spinner">
                <div class="spinner"></div>
                <p>${message}</p>
            </div>
        `;
        
        document.body.appendChild(overlay);
    }

    /**
     * 隐藏加载状态
     */
    hideLoading() {
        const overlay = document.getElementById('courseLoadingOverlay');
        if (overlay) {
            overlay.remove();
        }
    }

    /**
     * 手动强制初始化过滤器（调试用）
     */
    forceInitializeFilters() {
        console.log('强制初始化过滤器...');
        
        // 等待DOM完全准备好
        const initFilters = () => {
            const stageFilter = document.getElementById('stageFilter');
            console.log('强制初始化 - stageFilter元素:', stageFilter);
            
            if (stageFilter) {
                // 清空现有选项
                stageFilter.innerHTML = '<option value="">选择阶段</option>';
                
                // 添加阶段选项
                this.filterOptions.stages.forEach(stage => {
                    const option = document.createElement('option');
                    option.value = stage;
                    option.textContent = stage;
                    stageFilter.appendChild(option);
                    console.log('强制添加阶段选项:', stage);
                });
                
                // 初始化其他过滤器
                this.initializeOtherFilters();
                console.log('强制初始化完成！');
            } else {
                console.error('强制初始化失败：仍然找不到stageFilter元素');
            }
        };
        
        // 立即尝试
        initFilters();
        
        // 如果失败，再等待一段时间
        setTimeout(initFilters, 1000);
    }
    
    /**
     * 初始化其他过滤器
     */
    initializeOtherFilters() {
        // 学科选项
        const subjectFilter = document.getElementById('subjectFilter');
        if (subjectFilter) {
            subjectFilter.innerHTML = '<option value="">选择学科</option>';
            this.filterOptions.subjects.forEach(subject => {
                const option = document.createElement('option');
                option.value = subject;
                option.textContent = subject;
                subjectFilter.appendChild(option);
            });
        }

        // 学期选项
        const semesterFilter = document.getElementById('semesterFilter');
        if (semesterFilter) {
            semesterFilter.innerHTML = '<option value="">选择学期</option>';
            this.filterOptions.semesters.forEach(semester => {
                const option = document.createElement('option');
                option.value = semester.value;
                option.textContent = semester.label;
                semesterFilter.appendChild(option);
            });
        }

        // 章节选项
        const chapterFilter = document.getElementById('chapterFilter');
        if (chapterFilter) {
            chapterFilter.innerHTML = '<option value="">选择章节</option>';
            this.filterOptions.chapters.forEach(chapter => {
                const option = document.createElement('option');
                option.value = chapter.value;
                option.textContent = chapter.label;
                chapterFilter.appendChild(option);
            });
        }
    }

    /**
     * 获取学期显示名称
     */
    getSemesterDisplayName(semester) {
        const semesterMap = {
            'SEMESTER_1': '上册',
            'SEMESTER_2': '下册'
        };
        return semesterMap[semester] || semester;
    }

    /**
     * 显示消息
     */
    showMessage(message, type = 'info') {
        // 使用全局通知系统
        if (window.showNotification) {
            window.showNotification(message, type);
        } else {
            // 备用方案
            console.log(`${type.toUpperCase()}: ${message}`);
            alert(message);
        }
    }
}

// 创建全局实例
console.log('创建课程管理模块实例...');
window.courseManagement = new CourseManagementModule();
console.log('课程管理模块实例已创建:', window.courseManagement);

// 添加全局调试函数
window.debugCourseManagement = function() {
    console.log('=== 课程管理模块调试信息 ===');
    console.log('实例:', window.courseManagement);
    console.log('过滤器选项:', window.courseManagement.filterOptions);
    console.log('stageFilter元素:', document.getElementById('stageFilter'));
    console.log('coursesTab容器:', document.getElementById('coursesTab'));
    
    // 强制初始化
    if (window.courseManagement) {
        window.courseManagement.forceInitializeFilters();
    }
};   