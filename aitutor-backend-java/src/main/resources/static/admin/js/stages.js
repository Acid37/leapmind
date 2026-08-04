// 阶段管理模块
class StageManager {
    constructor() {
        this.stagesTable = document.getElementById('stagesTable').querySelector('tbody');
        this.gradesTable = document.getElementById('gradesTable').querySelector('tbody');
        this.refreshStagesBtn = document.getElementById('refreshStagesBtn');
        this.addStageBtn = document.getElementById('addStageBtn');
        this.addGradeBtn = document.getElementById('addGradeBtn');
        this.manageUsersBtn = document.getElementById('manageUsersBtn');
        this.exportStagesBtn = document.getElementById('exportStagesBtn');
        
        // 概览卡片元素
        this.totalUsersEl = document.getElementById('totalUsers');
        this.totalStagesEl = document.getElementById('totalStages');
        this.totalGradesEl = document.getElementById('totalGrades');
        this.growthRateEl = document.getElementById('growthRate');
        
        // 搜索输入
        this.stageSearchInput = document.getElementById('stageSearchInput');
        this.gradeSearchInput = document.getElementById('gradeSearchInput');
        
        this.stages = [];
        this.grades = [];
        this.charts = {};
        this.init();
    }

    init() {
        // 绑定事件
        this.refreshStagesBtn.addEventListener('click', () => this.loadStageData());
        this.addStageBtn.addEventListener('click', () => this.showAddStageModal());
        this.addGradeBtn.addEventListener('click', () => this.showAddGradeModal());
        this.manageUsersBtn.addEventListener('click', () => this.showUserManagement());
        this.exportStagesBtn.addEventListener('click', () => this.exportData());
        
        // 搜索功能
        this.stageSearchInput.addEventListener('input', (e) => this.filterStages(e.target.value));
        this.gradeSearchInput.addEventListener('input', (e) => this.filterGrades(e.target.value));
        
        // 图表控制按钮
        this.initChartControls();
        
        // 加载阶段数据
        this.loadStageData();
    }

    // 初始化图表控制
    initChartControls() {
        document.querySelectorAll('.btn-chart').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const chartType = e.target.dataset.chart;
                const chartCard = e.target.closest('.chart-card');
                
                // 更新按钮状态
                chartCard.querySelectorAll('.btn-chart').forEach(b => b.classList.remove('active'));
                e.target.classList.add('active');
                
                // 重新渲染图表
                if (chartCard.querySelector('#stageDistributionChart')) {
                    this.renderStageChart(chartType);
                } else if (chartCard.querySelector('#gradeStatisticsChart')) {
                    this.renderGradeChart(chartType);
                }
            });
        });
    }

    // 加载阶段数据
    async loadStageData() {
        try {
            console.log('开始加载阶段数据...');
            
            // 并行加载所有数据
            const [stageStatsResponse, gradeStatsResponse, stagesResponse, gradesResponse] = await Promise.all([
                api.getStageStatistics(),
                api.getGradeStatistics(),
                api.getStages(),
                api.getGrades()
            ]);

            console.log('API响应结果:', {
                stageStats: stageStatsResponse,
                gradeStats: gradeStatsResponse,
                stages: stagesResponse,
                grades: gradesResponse
            });

            // 处理阶段统计数据
            if (stageStatsResponse && (stageStatsResponse.success || stageStatsResponse.data)) {
                const stageStatsData = stageStatsResponse.data || stageStatsResponse;
                console.log('处理阶段统计数据:', stageStatsData);
                this.stageStatsData = Array.isArray(stageStatsData) ? stageStatsData : [];
                this.renderStageStats(this.stageStatsData);
            } else {
                console.warn('阶段统计数据响应异常:', stageStatsResponse);
                this.stageStatsData = [];
            }

            // 处理年级统计数据
            if (gradeStatsResponse && (gradeStatsResponse.success || gradeStatsResponse.data)) {
                const gradeStatsData = gradeStatsResponse.data || gradeStatsResponse;
                console.log('处理年级统计数据:', gradeStatsData);
                this.gradeStatsData = Array.isArray(gradeStatsData) ? gradeStatsData : [];
                this.renderGradeStats(this.gradeStatsData);
            } else {
                console.warn('年级统计数据响应异常:', gradeStatsResponse);
                this.gradeStatsData = [];
            }

            // 处理阶段基础数据
            if (stagesResponse && (stagesResponse.success || stagesResponse.data)) {
                const stagesData = stagesResponse.data || stagesResponse;
                console.log('处理阶段基础数据:', stagesData);
                this.stages = Array.isArray(stagesData) ? stagesData : [];
                
                // 如果有统计数据，合并用户数量信息
                if (this.stageStatsData && this.stageStatsData.length > 0) {
                    this.stages = this.stages.map(stage => {
                        const statsStage = this.stageStatsData.find(s => s.stageCode === stage.stageCode);
                        return {
                            ...stage,
                            userCount: statsStage ? statsStage.userCount : stage.userCount || 0
                        };
                    });
                }
                
                this.renderStages(this.stages);
            } else {
                console.warn('阶段基础数据响应异常:', stagesResponse);
                this.stages = [];
                this.renderStages([]);
            }

            // 处理年级基础数据
            if (gradesResponse && (gradesResponse.success || gradesResponse.data)) {
                const gradesData = gradesResponse.data || gradesResponse;
                console.log('处理年级基础数据:', gradesData);
                this.grades = Array.isArray(gradesData) ? gradesData : [];
                this.renderGrades(this.grades);
            } else {
                console.warn('年级基础数据响应异常:', gradesResponse);
                this.grades = [];
                this.renderGrades([]);
            }

            // 更新概览卡片
            this.updateOverviewCards();
            
            // 渲染图表
            this.renderCharts();
            
            console.log('阶段数据加载完成');
            this.showMessage('数据加载成功', 'success');
            
        } catch (error) {
            console.error('Failed to load stage data:', error);
            this.showMessage(`加载阶段数据失败: ${error.message}`, 'error');
        }
    }

    // 更新概览卡片
    updateOverviewCards() {
        // 使用统计数据中的真实用户数
        let totalUsers = 0;
        
        // 如果有阶段统计数据，使用统计数据
        if (this.stageStatsData && Array.isArray(this.stageStatsData)) {
            totalUsers = this.stageStatsData.reduce((sum, stage) => sum + (stage.userCount || 0), 0);
        } else if (this.stages && Array.isArray(this.stages)) {
            // 否则使用基础阶段数据
            totalUsers = this.stages.reduce((sum, stage) => sum + (stage.userCount || 0), 0);
        }
        
        this.totalUsersEl.textContent = totalUsers.toLocaleString();
        
        // 阶段数量
        const stageCount = this.stages ? this.stages.length : 0;
        this.totalStagesEl.textContent = stageCount;
        
        // 年级数量
        const gradeCount = this.grades ? this.grades.length : 0;
        this.totalGradesEl.textContent = gradeCount;
        
        // 计算增长率（基于用户数据的简单计算）
        const growthRate = totalUsers > 0 ? Math.min(Math.floor((totalUsers / 10)), 50) : 0;
        this.growthRateEl.textContent = `+${growthRate}%`;
        
        console.log('概览卡片更新:', {
            totalUsers,
            stageCount,
            gradeCount,
            growthRate
        });
        
        // 添加动画效果
        this.animateNumbers();
    }

    // 数字动画效果
    animateNumbers() {
        const cards = document.querySelectorAll('.overview-card');
        cards.forEach((card, index) => {
            setTimeout(() => {
                card.style.transform = 'scale(1.05)';
                setTimeout(() => {
                    card.style.transform = 'scale(1)';
                }, 200);
            }, index * 100);
        });
    }

    // 渲染图表
    renderCharts() {
        this.renderStageChart('pie');
        this.renderGradeChart('bar');
    }

    // 渲染阶段分布图表
    renderStageChart(type = 'pie') {
        const ctx = document.getElementById('stageDistributionChart').getContext('2d');
        
        // 销毁现有图表
        if (this.charts.stageChart) {
            this.charts.stageChart.destroy();
        }

        // 使用统计数据，如果没有则使用基础数据
        const chartData = this.stageStatsData && this.stageStatsData.length > 0 
            ? this.stageStatsData 
            : this.stages || [];

        console.log('阶段图表数据:', chartData);

        const data = {
            labels: chartData.map(stage => stage.stageName || '未知阶段'),
            datasets: [{
                data: chartData.map(stage => stage.userCount || 0),
                backgroundColor: [
                    '#1e40af', // 深蓝色主题
                    '#3b82f6',
                    '#0ea5e9',
                    '#1d4ed8',
                    '#1e3a8a',
                    '#0f172a'
                ],
                borderWidth: 2,
                borderColor: '#fff'
            }]
        };

        const config = {
            type: type,
            data: data,
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            padding: 20,
                            usePointStyle: true
                        }
                    },
                    tooltip: {
                        callbacks: {
                            label: function(context) {
                                const total = context.dataset.data.reduce((a, b) => a + b, 0);
                                const percentage = ((context.parsed / total) * 100).toFixed(1);
                                return `${context.label}: ${context.parsed} 人 (${percentage}%)`;
                            }
                        }
                    }
                },
                animation: {
                    animateScale: true,
                    animateRotate: true
                }
            }
        };

        this.charts.stageChart = new Chart(ctx, config);
    }

    // 渲染年级统计图表
    renderGradeChart(type = 'bar') {
        const ctx = document.getElementById('gradeStatisticsChart').getContext('2d');
        
        // 销毁现有图表
        if (this.charts.gradeChart) {
            this.charts.gradeChart.destroy();
        }

        // 使用统计数据，如果没有则使用基础数据
        const chartData = this.gradeStatsData && this.gradeStatsData.length > 0 
            ? this.gradeStatsData 
            : this.grades || [];

        console.log('年级图表数据:', chartData);

        const gradeData = chartData.map(grade => ({
            label: grade.description ? grade.description.replace(/\s*\(用户数:\s*\d+\)/, '') : '未知年级',
            count: this.extractUserCountFromDescription(grade.description || '', true)
        }));

        const data = {
            labels: gradeData.map(item => item.label),
            datasets: [{
                label: '用户数量',
                data: gradeData.map(item => item.count),
                backgroundColor: type === 'line' ? 'rgba(30, 64, 175, 0.1)' : 'rgba(30, 64, 175, 0.8)',
                borderColor: '#1e40af',
                borderWidth: 2,
                fill: type === 'line',
                tension: 0.4
            }]
        };

        const config = {
            type: type,
            data: data,
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false
                    },
                    tooltip: {
                        callbacks: {
                            label: function(context) {
                                return `${context.label}: ${context.parsed.y} 人`;
                            }
                        }
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            stepSize: 1
                        }
                    },
                    x: {
                        ticks: {
                            maxRotation: 45
                        }
                    }
                },
                animation: {
                    duration: 1000,
                    easing: 'easeInOutQuart'
                }
            }
        };

        this.charts.gradeChart = new Chart(ctx, config);
    }

    // 渲染阶段统计
    renderStageStats(stageStats) {
        console.log('渲染阶段统计:', stageStats);
        
        if (!stageStats || stageStats.length === 0) {
            console.log('暂无阶段统计数据');
            return;
        }

        // 这里不需要渲染到特定元素，因为统计数据会用于图表和概览卡片
        console.log('阶段统计数据已处理:', stageStats.map(stage => ({
            name: stage.stageName,
            count: stage.userCount || 0
        })));
    }

    // 渲染年级统计
    renderGradeStats(gradeStats) {
        console.log('渲染年级统计:', gradeStats);
        
        if (!gradeStats || gradeStats.length === 0) {
            console.log('暂无年级统计数据');
            return;
        }

        // 这里不需要渲染到特定元素，因为统计数据会用于图表
        console.log('年级统计数据已处理:', gradeStats.map(grade => ({
            name: grade.description,
            count: this.extractUserCountFromDescription(grade.description || '', true)
        })));
    }

    // 从描述中提取用户数量
    extractUserCount(description) {
        const match = description.match(/用户数:\s*(\d+)/);
        return match ? match[1] : '0';
    }

    // 渲染阶段列表
    renderStages(stages) {
        if (!stages || stages.length === 0) {
            this.stagesTable.innerHTML = `
                <tr>
                    <td colspan="7" class="empty-state">
                        <h3>暂无阶段数据</h3>
                        <p>点击"添加阶段"按钮创建新的教育阶段</p>
                    </td>
                </tr>
            `;
            return;
        }

        this.stagesTable.innerHTML = stages.map((stage, index) => `
            <tr data-stage-id="${stage.stageCode}">
                <td>
                    <input type="checkbox" class="stage-checkbox" value="${stage.stageCode}">
                </td>
                <td>
                    <div class="code-badge">${stage.stageCode}</div>
                </td>
                <td>
                    <div class="stage-name">
                        <strong>${stage.stageName}</strong>
                    </div>
                </td>
                <td>
                    <div class="grades-preview">
                        ${this.formatGradesModern(stage.grades)}
                    </div>
                </td>
                <td>
                    <div class="user-count">
                        <span class="count-number">${(stage.userCount || 0).toLocaleString()}</span>
                        <span class="count-label">人</span>
                    </div>
                </td>
                <td>
                    <span class="status-badge status-active">启用</span>
                </td>
                <td class="actions">
                    <div class="action-buttons">
                        <button class="btn btn-sm btn-primary" onclick="stageManager.viewStageDetails('${stage.stageCode}')" title="查看详情">
                            👁️
                        </button>
                        <button class="btn btn-sm btn-warning" onclick="stageManager.editStage('${stage.stageCode}')" title="编辑">
                            ✏️
                        </button>
                        <button class="btn btn-sm btn-info" onclick="stageManager.manageStageUsers('${stage.stageCode}')" title="管理用户">
                            👥
                        </button>
                        <button class="btn btn-sm btn-danger" onclick="stageManager.deleteStage('${stage.stageCode}')" title="删除">
                            🗑️
                        </button>
                    </div>
                </td>
            </tr>
        `).join('');
    }

    // 现代化年级格式显示
    formatGradesModern(grades) {
        if (!grades || grades.length === 0) {
            return '<span class="no-grades">暂无年级</span>';
        }
        
        const displayGrades = grades.slice(0, 3);
        const remaining = grades.length - 3;
        
        let html = displayGrades.map(grade => 
            `<span class="grade-tag">${grade.description}</span>`
        ).join('');
        
        if (remaining > 0) {
            html += `<span class="grade-more">+${remaining}个</span>`;
        }
        
        return html;
    }

    // 格式化年级列表
    formatGrades(grades) {
        if (!grades || grades.length === 0) {
            return '-';
        }
        
        return grades.map(grade => grade.description).join('、');
    }

    // 查看阶段详情
    async viewStageDetails(stageCode) {
        try {
            const response = await api.getStageByCode(stageCode);
            if (response.success && response.data) {
                this.showStageDetailsModal(response.data);
            }
        } catch (error) {
            console.error('Failed to get stage details:', error);
            this.showMessage('获取阶段详情失败', 'error');
        }
    }

    // 显示阶段详情模态框
    showStageDetailsModal(stage) {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <div class="stage-details">
                <div class="form-group">
                    <label>阶段代码:</label>
                    <p>${stage.stageCode}</p>
                </div>
                <div class="form-group">
                    <label>阶段名称:</label>
                    <p>${stage.stageName}</p>
                </div>
                <div class="form-group">
                    <label>用户数量:</label>
                    <p>${stage.userCount || 0} 人</p>
                </div>
                <div class="form-group">
                    <label>包含年级:</label>
                    <div class="grade-list">
                        ${stage.grades ? stage.grades.map(grade => `
                            <div class="grade-item">
                                <strong>${grade.description}</strong>
                                <span class="grade-code">(${grade.code})</span>
                            </div>
                        `).join('') : '暂无年级信息'}
                    </div>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">关闭</button>
                </div>
            </div>
        `;

        document.getElementById('modalTitle').textContent = `阶段详情 - ${stage.stageName}`;
        window.modal.show();
    }

    // 查看年级列表
    async viewGrades(stageCode) {
        try {
            const response = await api.getGradesByStage(stageCode);
            if (response.success && response.data) {
                this.showGradesModal(stageCode, response.data);
            }
        } catch (error) {
            console.error('Failed to get grades:', error);
            this.showMessage('获取年级列表失败', 'error');
        }
    }

    // 显示年级列表模态框
    showGradesModal(stageCode, grades) {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <div class="grades-list">
                ${grades && grades.length > 0 ? `
                    <div class="table-container">
                        <table class="data-table">
                            <thead>
                                <tr>
                                    <th>年级代码</th>
                                    <th>年级名称</th>
                                    <th>所属阶段</th>
                                    <th>操作</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${grades.map(grade => `
                                    <tr>
                                        <td>${grade.code}</td>
                                        <td>${grade.description}</td>
                                        <td>${grade.stage}</td>
                                        <td>
                                            <button class="btn btn-primary" onclick="stageManager.viewGradeDetails('${grade.code}')">查看详情</button>
                                        </td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                ` : '<p class="empty-state">该阶段暂无年级信息</p>'}
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">关闭</button>
                </div>
            </div>
        `;

        const stageName = stageCode === 'PRIMARY' ? '小学' : stageCode === 'JUNIOR' ? '初中' : stageCode;
        document.getElementById('modalTitle').textContent = `${stageName} - 年级列表`;
        window.modal.show();
    }

    // 查看年级详情
    async viewGradeDetails(gradeCode) {
        try {
            const response = await api.getGradeByCode(gradeCode);
            if (response.success && response.data) {
                this.showGradeDetailsModal(response.data);
            }
        } catch (error) {
            console.error('Failed to get grade details:', error);
            this.showMessage('获取年级详情失败', 'error');
        }
    }

    // 显示年级详情模态框
    showGradeDetailsModal(grade) {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <div class="grade-details">
                <div class="form-group">
                    <label>年级代码:</label>
                    <p>${grade.code}</p>
                </div>
                <div class="form-group">
                    <label>年级名称:</label>
                    <p>${grade.description}</p>
                </div>
                <div class="form-group">
                    <label>所属阶段:</label>
                    <p>${grade.stage} (${grade.stageCode})</p>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">关闭</button>
                </div>
            </div>
        `;

        document.getElementById('modalTitle').textContent = `年级详情 - ${grade.description}`;
        window.modal.show();
    }

    // 渲染年级表格
    renderGrades(grades) {
        if (!grades || grades.length === 0) {
            this.gradesTable.innerHTML = `
                <tr>
                    <td colspan="8" class="empty-state">
                        <h3>暂无年级数据</h3>
                        <p>点击"添加年级"按钮创建新的年级</p>
                    </td>
                </tr>
            `;
            return;
        }

        this.gradesTable.innerHTML = grades.map((grade, index) => `
            <tr data-grade-id="${grade.code}">
                <td>
                    <input type="checkbox" class="grade-checkbox" value="${grade.code}">
                </td>
                <td>
                    <div class="code-badge">${grade.code}</div>
                </td>
                <td>
                    <div class="grade-name">
                        <strong>${grade.description.replace(/\s*\(用户数:\s*\d+\)/, '')}</strong>
                    </div>
                </td>
                <td>
                    <span class="stage-tag">${grade.stage}</span>
                </td>
                <td>
                    <div class="user-count">
                        <span class="count-number">${this.extractUserCountFromDescription(grade.description, true)}</span>
                        <span class="count-label">人</span>
                    </div>
                </td>
                <td>
                    <div class="sort-order">
                        <input type="number" value="${index + 1}" class="sort-input" min="1" 
                               onchange="stageManager.updateGradeSort('${grade.code}', this.value)">
                    </div>
                </td>
                <td>
                    <span class="status-badge status-active">启用</span>
                </td>
                <td class="actions">
                    <div class="action-buttons">
                        <button class="btn btn-sm btn-primary" onclick="stageManager.viewGradeDetails('${grade.code}')" title="查看详情">
                            👁️
                        </button>
                        <button class="btn btn-sm btn-warning" onclick="stageManager.editGrade('${grade.code}')" title="编辑">
                            ✏️
                        </button>
                        <button class="btn btn-sm btn-info" onclick="stageManager.manageGradeUsers('${grade.code}')" title="管理用户">
                            👥
                        </button>
                        <button class="btn btn-sm btn-danger" onclick="stageManager.deleteGrade('${grade.code}')" title="删除">
                            🗑️
                        </button>
                    </div>
                </td>
            </tr>
        `).join('');
    }

    // 从描述中提取用户数量
    extractUserCountFromDescription(description, returnNumber = false) {
        const match = description.match(/用户数:\s*(\d+)/);
        const count = match ? parseInt(match[1]) : 0;
        return returnNumber ? count : count + ' 人';
    }

    // 搜索过滤阶段
    filterStages(searchTerm) {
        const rows = this.stagesTable.querySelectorAll('tr');
        rows.forEach(row => {
            const text = row.textContent.toLowerCase();
            const isVisible = text.includes(searchTerm.toLowerCase());
            row.style.display = isVisible ? '' : 'none';
        });
    }

    // 搜索过滤年级
    filterGrades(searchTerm) {
        const rows = this.gradesTable.querySelectorAll('tr');
        rows.forEach(row => {
            const text = row.textContent.toLowerCase();
            const isVisible = text.includes(searchTerm.toLowerCase());
            row.style.display = isVisible ? '' : 'none';
        });
    }

    // 显示添加阶段模态框
    showAddStageModal() {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <form id="addStageForm" class="modern-form">
                <div class="form-group">
                    <label for="stageCode">阶段代码 *</label>
                    <input type="text" id="stageCode" name="stageCode" required 
                           placeholder="如：HIGH_SCHOOL" class="form-input">
                    <small class="form-help">请使用英文大写字母和下划线</small>
                </div>
                <div class="form-group">
                    <label for="stageName">阶段名称 *</label>
                    <input type="text" id="stageName" name="stageName" required 
                           placeholder="如：高中" class="form-input">
                </div>
                <div class="form-group">
                    <label for="stageDescription">阶段描述</label>
                    <textarea id="stageDescription" name="description" 
                              placeholder="请输入阶段描述..." class="form-textarea"></textarea>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="submit" class="btn btn-success">创建阶段</button>
                </div>
            </form>
        `;

        document.getElementById('modalTitle').textContent = '添加教育阶段';
        window.modal.show();

        document.getElementById('addStageForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.showMessage('添加阶段功能开发中', 'info');
        });
    }

    // 显示添加年级模态框
    showAddGradeModal() {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <form id="addGradeForm" class="modern-form">
                <div class="form-group">
                    <label for="gradeCode">年级代码 *</label>
                    <input type="text" id="gradeCode" name="gradeCode" required 
                           placeholder="如：GRADE_10" class="form-input">
                </div>
                <div class="form-group">
                    <label for="gradeName">年级名称 *</label>
                    <input type="text" id="gradeName" name="gradeName" required 
                           placeholder="如：高一" class="form-input">
                </div>
                <div class="form-group">
                    <label for="gradeStage">所属阶段 *</label>
                    <select id="gradeStage" name="stage" required class="form-select">
                        <option value="">请选择阶段</option>
                        ${this.stages.map(stage => 
                            `<option value="${stage.stageCode}">${stage.stageName}</option>`
                        ).join('')}
                    </select>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="submit" class="btn btn-success">创建年级</button>
                </div>
            </form>
        `;

        document.getElementById('modalTitle').textContent = '添加年级';
        window.modal.show();

        document.getElementById('addGradeForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.showMessage('添加年级功能开发中', 'info');
        });
    }

    // 编辑阶段
    editStage(stageCode) {
        const stage = this.stages.find(s => s.stageCode === stageCode);
        if (!stage) return;

        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <form id="editStageForm" class="modern-form">
                <div class="form-group">
                    <label for="editStageCode">阶段代码</label>
                    <input type="text" id="editStageCode" value="${stage.stageCode}" readonly class="form-input">
                </div>
                <div class="form-group">
                    <label for="editStageName">阶段名称 *</label>
                    <input type="text" id="editStageName" value="${stage.stageName}" required class="form-input">
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="submit" class="btn btn-primary">保存修改</button>
                </div>
            </form>
        `;

        document.getElementById('modalTitle').textContent = `编辑阶段 - ${stage.stageName}`;
        window.modal.show();

        document.getElementById('editStageForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.showMessage('编辑阶段功能开发中', 'info');
        });
    }

    // 删除阶段
    async deleteStage(stageCode) {
        const stage = this.stages.find(s => s.stageCode === stageCode);
        if (!stage) return;

        const confirmed = await this.showConfirmDialog(
            `确定要删除阶段"${stage.stageName}"吗？`,
            '此操作将影响该阶段下的所有年级和用户，请谨慎操作！'
        );

        if (confirmed) {
            this.showMessage('删除阶段功能开发中', 'info');
        }
    }

    // 编辑年级
    editGrade(gradeCode) {
        this.showMessage('编辑年级功能开发中', 'info');
    }

    // 删除年级
    async deleteGrade(gradeCode) {
        const grade = this.grades.find(g => g.code === gradeCode);
        if (!grade) return;

        const confirmed = await this.showConfirmDialog(
            `确定要删除年级"${grade.description}"吗？`,
            '此操作将影响该年级的所有用户！'
        );

        if (confirmed) {
            this.showMessage('删除年级功能开发中', 'info');
        }
    }

    // 更新年级排序
    updateGradeSort(gradeCode, newOrder) {
        this.showMessage(`年级 ${gradeCode} 排序更新为 ${newOrder}`, 'info');
    }

    // 导出数据
    exportData() {
        const data = {
            stages: this.stages,
            grades: this.grades,
            exportTime: new Date().toISOString()
        };
        
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `stage_data_${new Date().toISOString().split('T')[0]}.json`;
        a.click();
        URL.revokeObjectURL(url);
        
        this.showMessage('数据导出成功', 'success');
    }

    // 确认对话框
    showConfirmDialog(title, message) {
        return new Promise((resolve) => {
            const modalBody = document.getElementById('modalBody');
            modalBody.innerHTML = `
                <div class="confirm-dialog">
                    <div class="confirm-icon">⚠️</div>
                    <h3>${title}</h3>
                    <p>${message}</p>
                    <div class="form-actions">
                        <button type="button" class="btn btn-secondary" id="confirmCancel">取消</button>
                        <button type="button" class="btn btn-danger" id="confirmOk">确认删除</button>
                    </div>
                </div>
            `;

            document.getElementById('modalTitle').textContent = '确认操作';
            window.modal.show();

            document.getElementById('confirmCancel').addEventListener('click', () => {
                window.modal.close();
                resolve(false);
            });

            document.getElementById('confirmOk').addEventListener('click', () => {
                window.modal.close();
                resolve(true);
            });
        });
    }

    // 管理阶段用户
    async manageStageUsers(stageCode) {
        try {
            const response = await api.getUsersByStage(stageCode);
            if (response.success) {
                this.showStageUsersModal(stageCode, response.data || []);
            }
        } catch (error) {
            console.error('Failed to get stage users:', error);
            this.showMessage('获取阶段用户失败', 'error');
        }
    }

    // 管理年级用户
    async manageGradeUsers(gradeCode) {
        try {
            // 这里需要添加按年级查询用户的API
            this.showMessage('年级用户管理功能开发中', 'info');
        } catch (error) {
            console.error('Failed to get grade users:', error);
            this.showMessage('获取年级用户失败', 'error');
        }
    }

    // 显示阶段用户管理模态框
    showStageUsersModal(stageCode, users) {
        const stageName = stageCode === 'PRIMARY' ? '小学' : stageCode === 'JUNIOR' ? '初中' : stageCode;
        
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <div class="stage-users-management">
                <div class="users-summary">
                    <p>当前 <strong>${stageName}</strong> 阶段共有 <strong>${users.length}</strong> 名用户</p>
                </div>
                
                ${users && users.length > 0 ? `
                    <div class="table-container">
                        <table class="data-table">
                            <thead>
                                <tr>
                                    <th>用户ID</th>
                                    <th>用户名</th>
                                    <th>学生姓名</th>
                                    <th>当前年级</th>
                                    <th>操作</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${users.map(user => `
                                    <tr>
                                        <td>${user.id}</td>
                                        <td>${user.username}</td>
                                        <td>${user.studentName || '-'}</td>
                                        <td>${this.getGradeDisplay(user.grade)}</td>
                                        <td>
                                            <button class="btn btn-warning btn-sm" onclick="stageManager.transferUser(${user.id}, '${user.username}')">转移年级</button>
                                        </td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                ` : '<p class="empty-state">该阶段暂无用户</p>'}
                
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">关闭</button>
                </div>
            </div>
        `;

        document.getElementById('modalTitle').textContent = `${stageName} - 用户管理`;
        window.modal.show();
    }

    // 转移用户年级
    async transferUser(userId, username) {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <form id="transferUserForm">
                <div class="form-group">
                    <label>用户信息:</label>
                    <p><strong>${username}</strong> (ID: ${userId})</p>
                </div>
                <div class="form-group">
                    <label for="newGrade">转移到年级:</label>
                    <select id="newGrade" name="grade" required>
                        <option value="">请选择年级</option>
                        <option value="GRADE_1">小学一年级</option>
                        <option value="GRADE_2">小学二年级</option>
                        <option value="GRADE_3">小学三年级</option>
                        <option value="GRADE_4">小学四年级</option>
                        <option value="GRADE_5">小学五年级</option>
                        <option value="GRADE_6">小学六年级</option>
                        <option value="GRADE_7">初一</option>
                        <option value="GRADE_8">初二</option>
                        <option value="GRADE_9">初三</option>
                    </select>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="submit" class="btn btn-primary">确认转移</button>
                </div>
            </form>
        `;

        document.getElementById('modalTitle').textContent = '转移用户年级';
        window.modal.show();

        // 绑定表单提交事件
        document.getElementById('transferUserForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const newGrade = document.getElementById('newGrade').value;
            
            if (!newGrade) {
                this.showMessage('请选择目标年级', 'error');
                return;
            }

            try {
                const response = await api.updateUser(userId, { grade: newGrade });
                if (response.success) {
                    this.showMessage('用户年级转移成功', 'success');
                    window.modal.close();
                    this.loadStageData(); // 刷新数据
                }
            } catch (error) {
                console.error('Failed to transfer user:', error);
                this.showMessage(error.message || '转移失败', 'error');
            }
        });
    }

    // 显示用户管理
    showUserManagement() {
        this.showMessage('用户管理功能：可以在用户管理标签页中进行详细的用户操作', 'info');
    }

    // 显示批量转移
    showBatchTransfer() {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <form id="batchTransferForm">
                <div class="form-group">
                    <label for="sourceGrade">源年级:</label>
                    <select id="sourceGrade" name="sourceGrade" required>
                        <option value="">请选择源年级</option>
                        <option value="GRADE_1">小学一年级</option>
                        <option value="GRADE_2">小学二年级</option>
                        <option value="GRADE_3">小学三年级</option>
                        <option value="GRADE_4">小学四年级</option>
                        <option value="GRADE_5">小学五年级</option>
                        <option value="GRADE_6">小学六年级</option>
                        <option value="GRADE_7">初一</option>
                        <option value="GRADE_8">初二</option>
                        <option value="GRADE_9">初三</option>
                    </select>
                </div>
                <div class="form-group">
                    <label for="targetGrade">目标年级:</label>
                    <select id="targetGrade" name="targetGrade" required>
                        <option value="">请选择目标年级</option>
                        <option value="GRADE_1">小学一年级</option>
                        <option value="GRADE_2">小学二年级</option>
                        <option value="GRADE_3">小学三年级</option>
                        <option value="GRADE_4">小学四年级</option>
                        <option value="GRADE_5">小学五年级</option>
                        <option value="GRADE_6">小学六年级</option>
                        <option value="GRADE_7">初一</option>
                        <option value="GRADE_8">初二</option>
                        <option value="GRADE_9">初三</option>
                    </select>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="submit" class="btn btn-warning">批量转移</button>
                </div>
            </form>
        `;

        document.getElementById('modalTitle').textContent = '批量转移用户年级';
        window.modal.show();

        // 绑定表单提交事件
        document.getElementById('batchTransferForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.showMessage('批量转移功能开发中，请使用单个用户转移功能', 'info');
        });
    }

    // 获取年级显示名称
    getGradeDisplay(grade) {
        const gradeMap = {
            'GRADE_1': '小学一年级',
            'GRADE_2': '小学二年级',
            'GRADE_3': '小学三年级',
            'GRADE_4': '小学四年级',
            'GRADE_5': '小学五年级',
            'GRADE_6': '小学六年级',
            'GRADE_7': '初一',
            'GRADE_8': '初二',
            'GRADE_9': '初三'
        };
        return gradeMap[grade] || grade;
    }

    // 显示消息
    showMessage(message, type = 'info') {
        // 使用主应用的通知系统
        if (window.mainApp && window.mainApp.showNotification) {
            window.mainApp.showNotification(message, type);
        } else {
            // 降级到alert
            if (type === 'error') {
                alert('错误: ' + message);
            } else {
                alert(message);
            }
        }
    }
}

// 导出到全局
window.StageManager = StageManager;