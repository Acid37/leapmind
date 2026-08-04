// 会话审查管理模块
class SessionReviewManager {
    constructor() {
        this.sessions = [];
        this.filteredSessions = [];
        this.currentSession = null;
        this.isLoading = false;
    }

    // 初始化
    init() {
        this.bindEvents();
        this.loadSessions();
        this.updateStatistics();
    }

    // 绑定事件
    bindEvents() {
        // 刷新数据按钮
        document.getElementById('refreshSessionsBtn')?.addEventListener('click', () => {
            this.loadSessions();
        });

        // 批量文本预处理按钮
        document.getElementById('bulkPreprocessingBtn')?.addEventListener('click', () => {
            this.showBulkPreprocessingModal();
        });

        // 搜索功能
        document.getElementById('searchSessionsBtn')?.addEventListener('click', () => {
            this.searchSessions();
        });

        // 清空搜索
        document.getElementById('clearSessionSearchBtn')?.addEventListener('click', () => {
            this.clearSearch();
        });

        // 全选功能
        document.getElementById('selectAllSessions')?.addEventListener('change', (e) => {
            this.toggleSelectAll(e.target.checked);
        });

        // 状态过滤器变化
        document.getElementById('sessionSearchStatus')?.addEventListener('change', () => {
            this.searchSessions();
        });
    }

    // 加载会话列表
    async loadSessions() {
        if (this.isLoading) return;
        
        this.isLoading = true;
        this.showLoading();

        try {
            const response = await window.api.getAllSessions();
            
            if (response && response.length >= 0) {
                this.sessions = Array.isArray(response) ? response : (response.data || []);
            } else {
                this.sessions = [];
            }

            this.filteredSessions = [...this.sessions];
            this.renderSessionsTable();
            this.updateStatistics();
            this.showMessage('会话列表加载成功', 'success');
        } catch (error) {
            console.error('加载会话列表失败:', error);
            this.showMessage('加载会话列表失败: ' + error.message, 'error');
            this.sessions = [];
            this.filteredSessions = [];
            this.renderSessionsTable();
        } finally {
            this.isLoading = false;
            this.hideLoading();
        }
    }

    // 搜索会话
    searchSessions() {
        const searchId = document.getElementById('sessionSearchId')?.value.trim() || '';
        const searchStatus = document.getElementById('sessionSearchStatus')?.value || '';
        const searchDate = document.getElementById('sessionSearchDate')?.value || '';

        this.filteredSessions = this.sessions.filter(session => {
            const matchId = !searchId || (session.courseId && session.courseId.toLowerCase().includes(searchId.toLowerCase()));
            const matchStatus = !searchStatus || session.status === searchStatus;
            const matchDate = !searchDate || (session.createdAt && session.createdAt.startsWith(searchDate));

            return matchId && matchStatus && matchDate;
        });

        this.renderSessionsTable();
        this.showMessage(`找到 ${this.filteredSessions.length} 个匹配的会话`, 'info');
    }

    // 清空搜索
    clearSearch() {
        document.getElementById('sessionSearchId').value = '';
        document.getElementById('sessionSearchStatus').value = '';
        document.getElementById('sessionSearchDate').value = '';
        
        this.filteredSessions = [...this.sessions];
        this.renderSessionsTable();
        this.showMessage('搜索条件已清空', 'info');
    }

    // 渲染会话表格
    renderSessionsTable() {
        const tbody = document.querySelector('#sessionsTable tbody');
        if (!tbody) return;

        if (this.filteredSessions.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="10" class="text-center">
                        <div class="empty-state">
                            <i class="fas fa-inbox"></i>
                            <p>暂无会话数据</p>
                        </div>
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = this.filteredSessions.map(session => `
            <tr data-session-id="${session.courseId}">
                <td>
                    <input type="checkbox" class="session-checkbox" value="${session.courseId}">
                </td>
                <td>
                    <span class="session-id" title="${session.courseId}">${this.truncateText(session.courseId, 12)}</span>
                </td>
                <td>
                    <span class="session-title" title="${session.title || '未设置标题'}">${this.truncateText(session.title || '未设置标题', 20)}</span>
                </td>
                <td>
                    <div class="text-preview" title="${session.originalText || '无原文'}">
                        ${this.truncateText(session.originalText || '无原文', 30)}
                    </div>
                </td>
                <td>
                    <div class="text-preview" title="${session.polishedText || '无润色文本'}">
                        ${this.truncateText(session.polishedText || '无润色文本', 30)}
                    </div>
                </td>
                <td>
                    <span class="badge badge-secondary">${session.totalSegments || 0}</span>
                </td>
                <td>
                    <span class="status-badge status-${(session.processingStatus || session.status || 'unknown').toLowerCase()}">${this.getStatusDisplayName(session.processingStatus || session.status)}</span>
                </td>
                <td>
                    <span class="date-time">${this.formatDateTime(session.createdAt)}</span>
                </td>
                <td>
                    <span class="reviewer">${session.reviewedBy || '-'}</span>
                </td>
                <td>
                    <div class="action-buttons">
                        <button class="btn btn-sm btn-info" onclick="sessionReviewManager.viewSessionDetails('${session.courseId}')" title="查看详情">
                            <i class="fas fa-eye"></i>
                        </button>
                        <button class="btn btn-sm btn-warning" onclick="sessionReviewManager.editSession('${session.courseId}')" title="编辑">
                            <i class="fas fa-edit"></i>
                        </button>
                        ${this.getActionButtons(session)}
                    </div>
                </td>
            </tr>
        `).join('');
    }

    // 根据会话状态获取操作按钮
    getActionButtons(session) {
        const status = session.processingStatus || session.status;
        let buttons = '';

        if (status === 'PENDING_REVIEW') {
            buttons += `
                <button class="btn btn-sm btn-success" onclick="sessionReviewManager.reviewSession('${session.courseId}')" title="审核">
                    <i class="fas fa-clipboard-check"></i>
                </button>
            `;
        }

        if (status === 'APPROVED') {
            buttons += `
                <button class="btn btn-sm btn-primary" onclick="sessionReviewManager.executeSynthesis('${session.courseId}')" title="执行语音合成">
                    <i class="fas fa-microphone"></i>
                </button>
            `;
        }

        if (status === 'SYNTHESIZED') {
            buttons += `
                <button class="btn btn-sm btn-secondary" onclick="sessionReviewManager.downloadAudio('${session.courseId}')" title="下载音频">
                    <i class="fas fa-download"></i>
                </button>
            `;
        }

        return buttons;
    }

    // 查看会话详情
    async viewSessionDetails(courseId) {
        try {
            this.showLoading();
            const session = await window.api.getSessionDetails(courseId);
            this.showSessionDetailsModal(session);
        } catch (error) {
            console.error('获取会话详情失败:', error);
            this.showMessage('获取会话详情失败: ' + error.message, 'error');
        } finally {
            this.hideLoading();
        }
    }

    // 显示会话详情模态框
    showSessionDetailsModal(session) {
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
                            <span>${session.courseId}</span>
                        </div>
                        <div class="detail-item">
                            <label>PPT标题:</label>
                            <span>${session.title || '未设置'}</span>
                        </div>
                        <div class="detail-item">
                            <label>状态:</label>
                            <span class="status-badge status-${(session.processingStatus || session.status || 'unknown').toLowerCase()}">${this.getStatusDisplayName(session.processingStatus || session.status)}</span>
                        </div>
                        <div class="detail-item">
                            <label>创建时间:</label>
                            <span>${this.formatDateTime(session.createdAt)}</span>
                        </div>
                        <div class="detail-item">
                            <label>页面数:</label>
                            <span>${session.totalPages || 0}</span>
                        </div>
                        <div class="detail-item">
                            <label>片段数:</label>
                            <span>${session.totalSegments || 0}</span>
                        </div>
                    </div>
                </div>

                <div class="detail-section">
                    <h4>文本内容</h4>
                    <div class="text-content-section">
                        <div class="text-item">
                            <label>原始文本:</label>
                            <div class="text-display">${session.originalText || '无原始文本'}</div>
                        </div>
                        <div class="text-item">
                            <label>润色文本:</label>
                            <div class="text-display">${session.polishedText || '无润色文本'}</div>
                        </div>
                    </div>
                </div>

                ${session.segments && session.segments.length > 0 ? `
                <div class="detail-section">
                    <h4>文本片段</h4>
                    <div class="segments-list">
                        ${session.segments.map((segment, index) => `
                            <div class="segment-item">
                                <div class="segment-header">
                                    <span class="segment-index">片段 ${index + 1}</span>
                                    <span class="segment-page">页面 ${segment.pageNumber}</span>
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
                    <button class="btn btn-info" onclick="sessionReviewManager.editSession('${session.courseId}')">编辑会话</button>
                    ${(session.processingStatus || session.status) === 'PENDING_REVIEW' ? `
                        <button class="btn btn-warning" onclick="sessionReviewManager.reviewSession('${session.courseId}')">审核会话</button>
                    ` : ''}
                    ${(session.processingStatus || session.status) === 'APPROVED' ? `
                        <button class="btn btn-success" onclick="sessionReviewManager.executeSynthesis('${session.courseId}')">执行合成</button>
                    ` : ''}
                </div>
            </div>
        `;

        window.modal.show();
    }

    // 显示编辑模态框
    showEditModal(session) {
        const modalTitle = document.getElementById('modalTitle');
        const modalBody = document.getElementById('modalBody');

        modalTitle.textContent = '编辑会话';
        modalBody.innerHTML = `
            <form id="editForm" class="edit-form">
                <div class="session-info">
                    <h4>基本信息</h4>
                    <div class="form-group">
                        <label for="editTitle">PPT标题:</label>
                        <input type="text" id="editTitle" name="title" class="form-control" value="${session.title || ''}" placeholder="请输入PPT标题">
                    </div>
                    <div class="info-grid">
                        <div class="info-item">
                            <label>会话ID:</label>
                            <span>${session.courseId}</span>
                        </div>
                        <div class="info-item">
                            <label>当前状态:</label>
                            <span class="status-badge status-${(session.processingStatus || session.status || 'unknown').toLowerCase()}">${this.getStatusDisplayName(session.processingStatus || session.status)}</span>
                        </div>
                        <div class="info-item">
                            <label>片段数:</label>
                            <span>${session.totalSegments || 0}</span>
                        </div>
                    </div>
                </div>

                <div class="text-sections">
                    <div class="form-group">
                        <label for="editOriginalText">原始文本:</label>
                        <textarea id="editOriginalText" name="originalText" class="form-control" rows="6" placeholder="请输入原始文本">${session.originalText || ''}</textarea>
                    </div>
                    
                    <div class="form-group">
                        <label for="editPolishedText">润色文本:</label>
                        <textarea id="editPolishedText" name="polishedText" class="form-control" rows="6" placeholder="请输入润色后的文本">${session.polishedText || ''}</textarea>
                    </div>
                </div>

                <div class="modal-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="submit" class="btn btn-primary">保存修改</button>
                    <button type="button" class="btn btn-success" onclick="sessionReviewManager.showReviewFromEdit('${session.courseId}')">保存并审核</button>
                </div>
            </form>
        `;

        // 绑定表单提交事件
        document.getElementById('editForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.submitEdit(session.courseId);
        });

        window.modal.show();
    }

    // 从编辑模式切换到审核模式
    async showReviewFromEdit(courseId) {
        // 先保存编辑的内容
        await this.submitEdit(courseId, false);
        // 然后显示审核模态框
        setTimeout(() => {
            this.reviewSession(courseId);
        }, 500);
    }

    // 提交编辑
    async submitEdit(courseId, showMessage = true) {
        const form = document.getElementById('editForm');
        const formData = new FormData(form);
        
        try {
            this.showLoading();

            const editData = {
                reviewerId: 'admin', // 编辑时使用默认审核人
                approved: null, // 编辑时不设置审核结果
                comments: '编辑会话内容',
                updatedTitle: formData.get('title'),
                updatedPolishedText: formData.get('polishedText'),
                forceUpdateSegments: false
            };

            const response = await window.api.adminReviewSession(courseId, editData);
            
            if (response) {
                if (showMessage) {
                    this.showMessage('会话编辑成功', 'success');
                    window.modal.close();
                }
                this.loadSessions(); // 重新加载列表
                return true;
            } else {
                throw new Error('会话编辑失败');
            }
        } catch (error) {
            console.error('提交编辑失败:', error);
            if (showMessage) {
                this.showMessage('提交编辑失败: ' + error.message, 'error');
            }
            return false;
        } finally {
            this.hideLoading();
        }
    }

    // 编辑会话
    async editSession(courseId) {
        try {
            this.showLoading();
            const session = await window.api.getSessionDetails(courseId);
            this.showEditModal(session);
        } catch (error) {
            console.error('获取会话信息失败:', error);
            this.showMessage('获取会话信息失败: ' + error.message, 'error');
        } finally {
            this.hideLoading();
        }
    }

    // 审核会话
    async reviewSession(courseId) {
        try {
            this.showLoading();
            const session = await window.api.getSessionDetails(courseId);
            this.showReviewModal(session);
        } catch (error) {
            console.error('获取会话信息失败:', error);
            this.showMessage('获取会话信息失败: ' + error.message, 'error');
        } finally {
            this.hideLoading();
        }
    }

    // 显示审核模态框
    showReviewModal(session) {
        const modalTitle = document.getElementById('modalTitle');
        const modalBody = document.getElementById('modalBody');

        modalTitle.textContent = '会话审核';
        modalBody.innerHTML = `
            <form id="reviewForm" class="review-form">
                <div class="session-info">
                    <h4>会话信息</h4>
                    <div class="info-grid">
                        <div class="info-item">
                            <label>会话ID:</label>
                            <span>${session.courseId}</span>
                        </div>
                        <div class="info-item">
                            <label>PPT标题:</label>
                            <span>${session.title || '未设置'}</span>
                        </div>
                        <div class="info-item">
                            <label>片段数:</label>
                            <span>${session.totalSegments || 0}</span>
                        </div>
                    </div>
                </div>

                <div class="review-sections">
                    <div class="session-text-review">
                        <h4>会话文本审核</h4>
                        <div class="text-comparison">
                            <div class="text-column">
                                <label>原始文本:</label>
                                <div class="text-display">${session.originalText || '无原始文本'}</div>
                            </div>
                            <div class="text-column">
                                <label>润色文本:</label>
                                <textarea class="form-control" name="updatedPolishedText" rows="8">${session.polishedText || ''}</textarea>
                            </div>
                        </div>
                    </div>

                    ${session.segments && session.segments.length > 0 ? `
                        <div class="segments-review">
                            <h4>文本片段详情</h4>
                            ${session.segments.map((segment, index) => `
                                <div class="segment-review-item">
                                    <div class="segment-header">
                                        <span class="segment-label">片段 ${index + 1} (页面 ${segment.pageNumber || '未知'})</span>
                                    </div>
                                    <div class="text-comparison">
                                        <div class="text-column">
                                            <label>原始文本:</label>
                                            <div class="text-display">${segment.originalText || '无'}</div>
                                        </div>
                                        <div class="text-column">
                                            <label>润色文本:</label>
                                            <div class="text-display">${segment.polishedText || '无'}</div>
                                        </div>
                                    </div>
                                </div>
                            `).join('')}
                        </div>
                    ` : ''}

                    <div class="review-decision">
                        <h4>审核决定</h4>
                        <div class="form-group">
                            <label>审核结果:</label>
                            <div class="radio-group">
                                <label class="radio-label">
                                    <input type="radio" name="approved" value="true" required>
                                    <span class="radio-text">通过</span>
                                </label>
                                <label class="radio-label">
                                    <input type="radio" name="approved" value="false" required>
                                    <span class="radio-text">拒绝</span>
                                </label>
                            </div>
                        </div>
                        <div class="form-group">
                            <label for="reviewComments">审核意见:</label>
                            <textarea id="reviewComments" name="comments" class="form-control" rows="3" placeholder="请输入审核意见..."></textarea>
                        </div>
                        <div class="form-group">
                            <label for="reviewerId">审核人ID:</label>
                            <input type="text" id="reviewerId" name="reviewerId" class="form-control" placeholder="请输入审核人ID" required>
                        </div>
                    </div>
                </div>

                <div class="modal-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="submit" class="btn btn-primary">提交审核</button>
                </div>
            </form>
        `;

        // 绑定表单提交事件
        document.getElementById('reviewForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.submitReview(session.courseId);
        });

        window.modal.show();
    }

    // 提交审核
    async submitReview(courseId) {
        const form = document.getElementById('reviewForm');
        const formData = new FormData(form);
        
        try {
            this.showLoading();

            const reviewData = {
                reviewerId: formData.get('reviewerId'),
                approved: formData.get('approved') === 'true',
                comments: formData.get('comments') || '',
                updatedPolishedText: formData.get('updatedPolishedText') || null,
                forceUpdateSegments: true
            };

            const response = await window.api.adminReviewSession(courseId, reviewData);
            
            if (response) {
                this.showMessage('审核提交成功', 'success');
                window.modal.close();
                this.loadSessions(); // 重新加载列表
            } else {
                throw new Error('审核提交失败');
            }
        } catch (error) {
            console.error('提交审核失败:', error);
            this.showMessage('提交审核失败: ' + error.message, 'error');
        } finally {
            this.hideLoading();
        }
    }

    // 执行语音合成
    async executeSynthesis(courseId) {
        if (!confirm('确定要执行语音合成吗？此操作可能需要较长时间。')) {
            return;
        }

        try {
            this.showLoading();
            const response = await window.api.executeBulkSynthesis(courseId);
            
            if (response) {
                this.showMessage('语音合成已开始执行', 'success');
                this.loadSessions(); // 重新加载列表
            } else {
                throw new Error('语音合成执行失败');
            }
        } catch (error) {
            console.error('执行语音合成失败:', error);
            this.showMessage('执行语音合成失败: ' + error.message, 'error');
        } finally {
            this.hideLoading();
        }
    }

    // 显示批量文本预处理模态框
    showBulkPreprocessingModal() {
        const modalTitle = document.getElementById('modalTitle');
        const modalBody = document.getElementById('modalBody');

        modalTitle.textContent = '批量文本预处理';
        modalBody.innerHTML = `
            <form id="bulkPreprocessingForm" class="bulk-preprocessing-form">
                <div class="form-group">
                    <label for="jsonData">JSON数据:</label>
                    <textarea id="jsonData" name="jsonData" class="form-control json-editor" rows="20" 
                              placeholder="请输入完整的JSON数据，包含title和slides字段..." required></textarea>
                    <small class="form-text">
                        请输入完整的JSON数据，格式示例：<br>
                        {<br>
                        &nbsp;&nbsp;"title": "PPT标题",<br>
                        &nbsp;&nbsp;"slides": [...]<br>
                        }
                    </small>
                </div>

                <div class="json-actions">
                    <button type="button" class="btn btn-secondary" onclick="sessionReviewManager.formatJson()">格式化JSON</button>
                    <button type="button" class="btn btn-info" onclick="sessionReviewManager.validateJson()">验证JSON</button>
                    <button type="button" class="btn btn-outline" onclick="sessionReviewManager.clearJsonInput()">清空</button>
                    <button type="button" class="btn btn-success" onclick="sessionReviewManager.insertSampleJson()">插入示例</button>
                </div>

                <div class="modal-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="submit" class="btn btn-primary">开始预处理</button>
                </div>
            </form>
        `;

        // 绑定表单提交事件
        document.getElementById('bulkPreprocessingForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.submitBulkPreprocessing();
        });

        window.modal.show();
    }

    // 提交批量文本预处理
    async submitBulkPreprocessing() {
        const form = document.getElementById('bulkPreprocessingForm');
        const formData = new FormData(form);
        
        try {
            this.showLoading();

            const jsonDataStr = formData.get('jsonData');
            
            // 验证JSON格式
            let requestData;
            try {
                requestData = JSON.parse(jsonDataStr);
                
                // 验证必要字段
                if (!requestData.title) {
                    throw new Error('JSON数据必须包含title字段');
                }
                
                if (!requestData.slides || !Array.isArray(requestData.slides)) {
                    throw new Error('JSON数据必须包含slides数组字段');
                }
                
                if (requestData.slides.length === 0) {
                    throw new Error('slides数组不能为空');
                }
                
            } catch (jsonError) {
                throw new Error('JSON格式错误: ' + jsonError.message);
            }

            const response = await window.api.bulkPreprocessing(requestData);
            
            if (response) {
                this.showMessage('批量文本预处理已开始', 'success');
                window.modal.close();
                this.loadSessions(); // 重新加载列表
            } else {
                throw new Error('批量文本预处理失败');
            }
        } catch (error) {
            console.error('提交批量文本预处理失败:', error);
            this.showMessage('提交批量文本预处理失败: ' + error.message, 'error');
        } finally {
            this.hideLoading();
        }
    }

    // 下载音频
    async downloadAudio(courseId) {
        try {
            this.showLoading();
            const audioBlob = await window.api.getAudioBlobUrl(courseId);
            
            // 创建下载链接
            const url = window.URL.createObjectURL(audioBlob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `course_${courseId}_audio.wav`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
            
            this.showMessage('音频下载已开始', 'success');
        } catch (error) {
            console.error('下载音频失败:', error);
            this.showMessage('下载音频失败: ' + error.message, 'error');
        } finally {
            this.hideLoading();
        }
    }

    // 格式化JSON
    formatJson() {
        const textarea = document.getElementById('jsonData');
        try {
            const parsed = JSON.parse(textarea.value);
            textarea.value = JSON.stringify(parsed, null, 2);
            this.showMessage('JSON格式化成功', 'success');
        } catch (error) {
            this.showMessage('JSON格式错误，无法格式化', 'error');
        }
    }

    // 验证JSON
    validateJson() {
        const textarea = document.getElementById('jsonData');
        try {
            const parsed = JSON.parse(textarea.value);
            
            // 验证必要字段
            if (!parsed.title) {
                throw new Error('JSON数据必须包含title字段');
            }
            
            if (!parsed.slides || !Array.isArray(parsed.slides)) {
                throw new Error('JSON数据必须包含slides数组字段');
            }
            
            if (parsed.slides.length === 0) {
                throw new Error('slides数组不能为空');
            }
            
            // 验证每个slide的基本结构
            parsed.slides.forEach((slide, index) => {
                if (!slide.pageNumber && slide.pageNumber !== 0) {
                    throw new Error(`第${index + 1}个slide缺少pageNumber字段`);
                }
            });
            
            this.showMessage(`JSON验证成功！标题: "${parsed.title}", 包含${parsed.slides.length}个slides`, 'success');
        } catch (error) {
            this.showMessage('JSON验证失败: ' + error.message, 'error');
        }
    }

    // 清空JSON输入
    clearJsonInput() {
        document.getElementById('jsonData').value = '';
        this.showMessage('JSON输入已清空', 'info');
    }

    // 插入示例JSON
    insertSampleJson() {
        const sampleJson = {
            "title": "示例PPT - 数学课堂",
            "slides": [
                {
                    "pageNumber": 1,
                    "content": [
                        {
                            "type": "text",
                            "text": "欢迎来到数学课堂"
                        },
                        {
                            "type": "text", 
                            "text": "今天我们将学习有趣的数学知识"
                        }
                    ]
                },
                {
                    "pageNumber": 2,
                    "content": [
                        {
                            "type": "text",
                            "text": "加法运算"
                        },
                        {
                            "type": "text",
                            "text": "1 + 1 = 2"
                        },
                        {
                            "type": "text",
                            "text": "2 + 3 = 5"
                        }
                    ]
                },
                {
                    "pageNumber": 3,
                    "content": [
                        {
                            "type": "text",
                            "text": "减法运算"
                        },
                        {
                            "type": "text",
                            "text": "5 - 2 = 3"
                        },
                        {
                            "type": "text",
                            "text": "10 - 4 = 6"
                        }
                    ]
                }
            ]
        };
        
        const textarea = document.getElementById('jsonData');
        textarea.value = JSON.stringify(sampleJson, null, 2);
        this.showMessage('示例JSON已插入', 'success');
    }

    // 全选功能
    toggleSelectAll(checked) {
        const checkboxes = document.querySelectorAll('.session-checkbox');
        checkboxes.forEach(checkbox => {
            checkbox.checked = checked;
        });
    }

    // 更新统计信息
    updateStatistics() {
        const totalSessions = this.sessions.length;
        const pendingSessions = this.sessions.filter(s => s.status === 'PENDING_REVIEW').length;
        const approvedSessions = this.sessions.filter(s => s.status === 'APPROVED').length;
        const synthesizedSessions = this.sessions.filter(s => s.status === 'SYNTHESIZED').length;

        // 更新概览卡片
        this.updateElement('totalSessions', totalSessions);
        this.updateElement('pendingSessions', pendingSessions);
        this.updateElement('approvedSessions', approvedSessions);
        this.updateElement('synthesizedSessions', synthesizedSessions);
    }

    // 更新元素内容
    updateElement(id, value) {
        const element = document.getElementById(id);
        if (element) {
            element.textContent = value;
        }
    }

    // 显示加载状态
    showLoading() {
        const loadingOverlay = document.createElement('div');
        loadingOverlay.id = 'loadingOverlay';
        loadingOverlay.className = 'loading-overlay';
        loadingOverlay.innerHTML = `
            <div class="loading-spinner">
                <i class="fas fa-spinner fa-spin"></i>
                <span>加载中...</span>
            </div>
        `;
        
        // 添加样式
        loadingOverlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.5);
            display: flex;
            justify-content: center;
            align-items: center;
            z-index: 9999;
        `;
        
        document.body.appendChild(loadingOverlay);
    }

    // 隐藏加载状态
    hideLoading() {
        const loadingOverlay = document.getElementById('loadingOverlay');
        if (loadingOverlay) {
            loadingOverlay.remove();
        }
    }

    // 显示消息
    showMessage(message, type = 'info') {
        if (window.showNotification) {
            window.showNotification(message, type);
        } else {
            console.log(`[${type.toUpperCase()}] ${message}`);
        }
    }

    // 获取状态显示名称
    getStatusDisplayName(status) {
        const statusMap = {
            'PENDING_REVIEW': '待审核',
            'APPROVED': '已通过',
            'REJECTED': '已拒绝',
            'SYNTHESIZED': '已合成',
            'FAILED': '失败'
        };
        return statusMap[status] || status || '未知';
    }

    // 格式化日期时间
    formatDateTime(dateTime) {
        if (!dateTime) return '-';
        try {
            return new Date(dateTime).toLocaleString('zh-CN');
        } catch (error) {
            return dateTime;
        }
    }

    // 截断文本
    truncateText(text, maxLength) {
        if (!text) return '-';
        return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
    }
}

// 初始化会话审查管理器
let sessionReviewManager;

document.addEventListener('DOMContentLoaded', () => {
    // 延迟初始化，确保其他依赖已加载
    setTimeout(() => {
        sessionReviewManager = new SessionReviewManager();
        window.sessionReviewManager = sessionReviewManager;
        
        // 监听标签页切换事件
        document.addEventListener('tabChanged', (e) => {
            if (e.detail.tabId === 'session-review') {
                sessionReviewManager.init();
            }
        });
        
        console.log('SessionReviewManager initialized successfully');
    }, 100);
});