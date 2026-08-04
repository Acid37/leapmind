// 大纲管理模块
class OutlineManager {
    constructor() {
        this.outlinesTable = document.getElementById('outlinesTable').querySelector('tbody');
        this.addOutlineBtn = document.getElementById('addOutlineBtn');
        this.refreshOutlinesBtn = document.getElementById('refreshOutlinesBtn');
        this.searchOutlinesBtn = document.getElementById('searchOutlinesBtn');
        this.clearOutlineSearchBtn = document.getElementById('clearOutlineSearchBtn');
        this.outlineSearchProject = document.getElementById('outlineSearchProject');
        this.outlineSearchTitle = document.getElementById('outlineSearchTitle');
        
        this.outlines = [];
        this.init();
    }

    init() {
        // 绑定事件
        this.addOutlineBtn.addEventListener('click', () => this.showAddOutlineModal());
        this.refreshOutlinesBtn.addEventListener('click', () => this.loadOutlines());
        this.searchOutlinesBtn.addEventListener('click', () => this.searchOutlines());
        this.clearOutlineSearchBtn.addEventListener('click', () => this.clearSearch());
        
        // 加载大纲列表
        this.loadOutlines();
        this.updateStatistics();
    }

    // 加载大纲列表
    async loadOutlines() {
        try {
            console.log('开始加载大纲列表...');
            const response = await api.getOutlines();
            console.log('大纲列表响应:', response);
            
            if (response.success) {
                this.outlines = response.data || [];
                console.log('大纲数据:', this.outlines);
                this.renderOutlines(this.outlines);
                this.updateStatistics();
            } else {
                console.error('加载大纲失败:', response.message);
                this.showMessage(response.message || '加载大纲列表失败', 'error');
            }
        } catch (error) {
            console.error('Failed to load outlines:', error);
            this.showMessage(error.message || '加载大纲列表失败', 'error');
        }
    }

    // 搜索大纲
    async searchOutlines() {
        const courseId = this.outlineSearchProject.value.trim();
        const title = this.outlineSearchTitle.value.trim();

        try {
            let outlines = [];
            
            if (courseId) {
                const response = await api.getOutlinesByCourseId(courseId);
                if (response.success) {
                    outlines = response.data || [];
                }
            } else if (title) {
                // 在本地数据中搜索标题
                outlines = this.outlines.filter(outline => {
                    const outlineInfo = this.parseOutlineJson(outline.outlineJson);
                    return outlineInfo.title.toLowerCase().includes(title.toLowerCase());
                });
            } else {
                // 如果没有搜索条件，加载所有大纲
                await this.loadOutlines();
                return;
            }
            
            this.renderOutlines(outlines);
        } catch (error) {
            console.error('Search failed:', error);
            this.showMessage('搜索失败', 'error');
        }
    }

    // 清除搜索
    clearSearch() {
        this.outlineSearchProject.value = '';
        this.outlineSearchTitle.value = '';
        this.loadOutlines();
    }

    // 渲染大纲列表
    renderOutlines(outlines) {
        if (!outlines || outlines.length === 0) {
            this.outlinesTable.innerHTML = `
                <tr>
                    <td colspan="8" class="empty-state">
                        <h3>暂无大纲数据</h3>
                        <p>点击"添加大纲"按钮创建新大纲</p>
                    </td>
                </tr>
            `;
            return;
        }

        this.outlinesTable.innerHTML = outlines.map(outline => {
            const outlineInfo = this.parseOutlineJson(outline.outlineJson);
            return `
                <tr>
                    <td>
                        <input type="checkbox" class="outline-checkbox" value="${outline.id}">
                    </td>
                    <td>${outline.id}</td>
                    <td>
                        <span class="code-badge">${outline.courseId}</span>
                    </td>
                    <td>
                        <strong>${outlineInfo.title}</strong>
                    </td>
                    <td>
                        <div class="content-preview">
                            ${this.truncateContent(outlineInfo.preview)}
                        </div>
                    </td>
                    <td>
                        <small>${this.formatDate(outline.createTime)}</small>
                    </td>
                    <td>
                        <small>${this.formatDate(outline.updateTime)}</small>
                    </td>
                    <td class="action-buttons">
                        <button class="btn btn-secondary" onclick="outlineManager.viewOutline(${outline.id})" title="查看">
                            <i class="fas fa-eye"></i>
                        </button>
                        <button class="btn btn-warning" onclick="outlineManager.editOutline(${outline.id})" title="编辑">
                            <i class="fas fa-edit"></i>
                        </button>
                        <button class="btn btn-danger" onclick="outlineManager.deleteOutline(${outline.id})" title="删除">
                            <i class="fas fa-trash"></i>
                        </button>
                    </td>
                </tr>
            `;
        }).join('');

        // 绑定全选事件
        const selectAllCheckbox = document.getElementById('selectAllOutlines');
        const checkboxes = document.querySelectorAll('.outline-checkbox');
        
        selectAllCheckbox.addEventListener('change', (e) => {
            checkboxes.forEach(checkbox => {
                checkbox.checked = e.target.checked;
            });
        });
    }

    // 解析大纲JSON
    parseOutlineJson(outlineJson) {
        try {
            const parsed = JSON.parse(outlineJson);
            return {
                title: parsed.title || '未命名大纲',
                preview: this.generatePreview(parsed),
                data: parsed
            };
        } catch (error) {
            return {
                title: '解析错误',
                preview: '无效的JSON格式',
                data: null
            };
        }
    }

    // 生成内容预览
    generatePreview(outlineData) {
        if (!outlineData) return '无内容';
        
        let preview = '';
        if (outlineData.description) {
            preview = outlineData.description;
        } else if (outlineData.sections && outlineData.sections.length > 0) {
            preview = outlineData.sections.map(section => section.title || section.name).join(', ');
        } else if (outlineData.content) {
            preview = outlineData.content;
        } else {
            preview = JSON.stringify(outlineData).substring(0, 100);
        }
        
        return preview;
    }

    // 截断内容显示
    truncateContent(content) {
        if (!content) return '-';
        const maxLength = 50;
        if (content.length > maxLength) {
            return content.substring(0, maxLength) + '...';
        }
        return content;
    }

    // 验证JSON格式
    validateJson() {
        const textarea = document.getElementById('addOutlineJson') || document.getElementById('editOutlineJson');
        if (!textarea) return;

        try {
            const jsonData = JSON.parse(textarea.value);
            this.showMessage('JSON格式验证通过', 'success');
            
            // 显示解析后的预览
            const preview = this.generatePreview(jsonData);
            const title = jsonData.title || '未命名大纲';
            
            // 在textarea下方显示预览
            let previewDiv = textarea.parentNode.querySelector('.json-preview');
            if (!previewDiv) {
                previewDiv = document.createElement('div');
                previewDiv.className = 'json-preview';
                textarea.parentNode.appendChild(previewDiv);
            }
            
            previewDiv.innerHTML = `
                <div class="preview-info">
                    <strong>预览:</strong><br>
                    <strong>标题:</strong> ${title}<br>
                    <strong>内容:</strong> ${this.truncateContent(preview)}
                </div>
            `;
        } catch (error) {
            this.showMessage('JSON格式错误: ' + error.message, 'error');
        }
    }

    // 格式化JSON
    formatJson() {
        const textarea = document.getElementById('addOutlineJson') || document.getElementById('editOutlineJson');
        if (!textarea) return;

        try {
            const jsonData = JSON.parse(textarea.value);
            const formattedJson = JSON.stringify(jsonData, null, 2);
            textarea.value = formattedJson;
            this.showMessage('JSON格式化成功', 'success');
        } catch (error) {
            this.showMessage('JSON格式错误，无法格式化: ' + error.message, 'error');
        }
    }

    // HTML转义
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // 设置JSON编辑器快捷键
    setupJsonEditorShortcuts(textareaId) {
        const textarea = document.getElementById(textareaId);
        if (!textarea) return;

        textarea.addEventListener('keydown', (e) => {
            // Ctrl+Shift+F 格式化JSON
            if (e.ctrlKey && e.shiftKey && e.key === 'F') {
                e.preventDefault();
                this.formatJson();
            }
            
            // Ctrl+Shift+V 验证JSON
            if (e.ctrlKey && e.shiftKey && e.key === 'V') {
                e.preventDefault();
                this.validateJson();
            }
            
            // Tab键插入2个空格而不是制表符
            if (e.key === 'Tab') {
                e.preventDefault();
                const start = textarea.selectionStart;
                const end = textarea.selectionEnd;
                const value = textarea.value;
                
                if (e.shiftKey) {
                    // Shift+Tab 减少缩进
                    const lineStart = value.lastIndexOf('\n', start - 1) + 1;
                    const lineEnd = value.indexOf('\n', start);
                    const line = value.substring(lineStart, lineEnd === -1 ? value.length : lineEnd);
                    
                    if (line.startsWith('  ')) {
                        textarea.value = value.substring(0, lineStart) + line.substring(2) + value.substring(lineEnd === -1 ? value.length : lineEnd);
                        textarea.selectionStart = textarea.selectionEnd = start - 2;
                    }
                } else {
                    // Tab 增加缩进
                    textarea.value = value.substring(0, start) + '  ' + value.substring(end);
                    textarea.selectionStart = textarea.selectionEnd = start + 2;
                }
            }
        });

        // 添加快捷键提示
        const helpText = textarea.parentNode.querySelector('.form-help');
        if (helpText) {
            helpText.innerHTML += '<br><small style="color: #6c757d;">快捷键: Ctrl+Shift+F 格式化, Ctrl+Shift+V 验证, Tab 缩进</small>';
        }
    }

    // 格式化日期
    formatDate(dateString) {
        if (!dateString) return '-';
        const date = new Date(dateString);
        return date.toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    // 更新统计数据
    updateStatistics() {
        const totalOutlines = this.outlines.length;
        const uniqueProjects = new Set(this.outlines.map(o => o.courseId)).size;
        
        // 计算本周新增（简化处理，实际应该从后端获取）
        const oneWeekAgo = new Date();
        oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);
        const recentOutlines = this.outlines.filter(outline => {
            if (!outline.createTime) return false;
            return new Date(outline.createTime) > oneWeekAgo;
        }).length;

        document.getElementById('totalOutlines').textContent = totalOutlines;
        document.getElementById('totalProjects').textContent = uniqueProjects;
        document.getElementById('completedOutlines').textContent = totalOutlines; // 简化处理
        document.getElementById('recentOutlines').textContent = recentOutlines;
    }

    // 显示添加大纲模态框
    showAddOutlineModal() {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <form id="addOutlineForm">
                <div class="form-group">
                    <label for="addCourseId">项目ID *</label>
                    <input type="text" id="addCourseId" name="courseId" required placeholder="请输入项目ID">
                </div>
                <div class="form-group">
                    <label for="addOutlineJson">大纲内容 (JSON格式) *</label>
                    <textarea id="addOutlineJson" name="outlineJson" required class="json-editor" placeholder='请输入JSON格式的大纲内容，例如：
{
  "title": "项目大纲标题",
  "sections": [
    {
      "title": "第一章",
      "content": "章节内容",
      "order": 1
    }
  ]
}' rows="15"></textarea>
                    <small class="form-help">请输入有效的JSON格式大纲内容</small>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="button" class="btn btn-outline" onclick="outlineManager.formatJson()">格式化JSON</button>
                    <button type="button" class="btn btn-outline" onclick="outlineManager.validateJson()">验证JSON</button>
                    <button type="submit" class="btn btn-primary">创建大纲</button>
                </div>
            </form>
        `;

        document.getElementById('modalTitle').textContent = '添加大纲';
        window.modal.show();

        // 绑定表单提交事件
        document.getElementById('addOutlineForm').addEventListener('submit', (e) => this.handleAddOutline(e));
        
        // 为JSON编辑器添加键盘快捷键
        this.setupJsonEditorShortcuts('addOutlineJson');
    }

    // 处理添加大纲
    async handleAddOutline(e) {
        e.preventDefault();
        
        const formData = new FormData(e.target);
        const outlineData = Object.fromEntries(formData.entries());

        // 验证JSON格式
        try {
            JSON.parse(outlineData.outlineJson);
        } catch (error) {
            this.showMessage('大纲内容必须是有效的JSON格式', 'error');
            return;
        }

        try {
            const response = await api.createOutline(outlineData);
            if (response.success) {
                this.showMessage('大纲创建成功', 'success');
                window.modal.close();
                this.loadOutlines();
            } else {
                this.showMessage(response.message || '创建大纲失败', 'error');
            }
        } catch (error) {
            console.error('Failed to create outline:', error);
            this.showMessage(error.message || '创建大纲失败', 'error');
        }
    }

    // 查看大纲详情
    async viewOutline(id) {
        try {
            const response = await api.getOutlineById(id);
            if (response.success && response.data) {
                this.showOutlineDetailModal(response.data, true);
            } else {
                this.showMessage('获取大纲详情失败', 'error');
            }
        } catch (error) {
            console.error('Failed to get outline:', error);
            this.showMessage('获取大纲详情失败', 'error');
        }
    }

    // 编辑大纲
    async editOutline(id) {
        try {
            const response = await api.getOutlineById(id);
            if (response.success && response.data) {
                this.showEditOutlineModal(response.data);
            } else {
                this.showMessage('获取大纲信息失败', 'error');
            }
        } catch (error) {
            console.error('Failed to get outline:', error);
            this.showMessage('获取大纲信息失败', 'error');
        }
    }

    // 显示大纲详情模态框
    showOutlineDetailModal(outline, readOnly = false) {
        const outlineInfo = this.parseOutlineJson(outline.outlineJson);
        
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <div class="outline-detail">
                <div class="detail-section">
                    <h4>基本信息</h4>
                    <div class="detail-grid">
                        <div class="detail-item">
                            <label>大纲ID:</label>
                            <span>${outline.id}</span>
                        </div>
                        <div class="detail-item">
                            <label>项目ID:</label>
                            <span class="code-badge">${outline.courseId}</span>
                        </div>
                        <div class="detail-item">
                            <label>标题:</label>
                            <span>${outlineInfo.title}</span>
                        </div>
                    </div>
                </div>
                <div class="detail-section">
                    <h4>大纲内容 (JSON)</h4>
                    <div class="content-display">
                        <pre>${JSON.stringify(outlineInfo.data, null, 2)}</pre>
                    </div>
                </div>
                <div class="detail-section">
                    <h4>时间信息</h4>
                    <div class="detail-grid">
                        <div class="detail-item">
                            <label>创建时间:</label>
                            <span>${this.formatDate(outline.createTime)}</span>
                        </div>
                        <div class="detail-item">
                            <label>更新时间:</label>
                            <span>${this.formatDate(outline.updateTime)}</span>
                        </div>
                    </div>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">关闭</button>
                    ${!readOnly ? `<button type="button" class="btn btn-primary" onclick="outlineManager.editOutline(${outline.id})">编辑</button>` : ''}
                </div>
            </div>
        `;

        document.getElementById('modalTitle').textContent = '大纲详情';
        window.modal.show();
    }

    // 显示编辑大纲模态框
    showEditOutlineModal(outline) {
        // 格式化JSON内容
        let formattedJson = '';
        try {
            const jsonData = JSON.parse(outline.outlineJson);
            formattedJson = JSON.stringify(jsonData, null, 2);
        } catch (error) {
            // 如果JSON解析失败，使用原始内容
            formattedJson = outline.outlineJson || '';
        }

        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <form id="editOutlineForm">
                <input type="hidden" name="id" value="${outline.id}">
                <div class="form-group">
                    <label for="editOutlineJson">大纲内容 (JSON格式) *</label>
                    <textarea id="editOutlineJson" name="outlineJson" required rows="20" class="json-editor">${this.escapeHtml(formattedJson)}</textarea>
                    <small class="form-help">请输入有效的JSON格式大纲内容</small>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="button" class="btn btn-outline" onclick="outlineManager.formatJson()">格式化JSON</button>
                    <button type="button" class="btn btn-outline" onclick="outlineManager.validateJson()">验证JSON</button>
                    <button type="submit" class="btn btn-primary">更新大纲</button>
                </div>
            </form>
        `;

        document.getElementById('modalTitle').textContent = '编辑大纲';
        window.modal.show();

        // 绑定表单提交事件
        document.getElementById('editOutlineForm').addEventListener('submit', (e) => this.handleEditOutline(e));
        
        // 为JSON编辑器添加键盘快捷键
        this.setupJsonEditorShortcuts('editOutlineJson');
    }

    // 处理编辑大纲
    async handleEditOutline(e) {
        e.preventDefault();
        
        const formData = new FormData(e.target);
        const outlineData = Object.fromEntries(formData.entries());

        // 验证JSON格式
        try {
            JSON.parse(outlineData.outlineJson);
        } catch (error) {
            this.showMessage('大纲内容必须是有效的JSON格式', 'error');
            return;
        }

        // 转换数据类型
        if (outlineData.id) {
            outlineData.id = parseInt(outlineData.id);
        }

        try {
            const response = await api.updateOutline(outlineData);
            if (response.success) {
                this.showMessage('大纲更新成功', 'success');
                window.modal.close();
                this.loadOutlines();
            } else {
                this.showMessage(response.message || '更新大纲失败', 'error');
            }
        } catch (error) {
            console.error('Failed to update outline:', error);
            this.showMessage(error.message || '更新大纲失败', 'error');
        }
    }

    // 删除大纲
    async deleteOutline(id) {
        const confirmed = await this.confirmDelete();
        if (!confirmed) return;

        try {
            const response = await api.deleteOutline(id);
            if (response.success) {
                this.showMessage('大纲删除成功', 'success');
                this.loadOutlines();
            } else {
                this.showMessage(response.message || '删除大纲失败', 'error');
            }
        } catch (error) {
            console.error('Failed to delete outline:', error);
            this.showMessage(error.message || '删除大纲失败', 'error');
        }
    }

    // 确认删除对话框
    confirmDelete() {
        return new Promise((resolve) => {
            if (window.mainApp && window.mainApp.confirm) {
                window.mainApp.confirm('确定要删除这个大纲吗？此操作不可恢复。', '确认删除')
                    .then(resolve);
            } else {
                resolve(confirm('确定要删除这个大纲吗？此操作不可恢复。'));
            }
        });
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
window.OutlineManager = OutlineManager;