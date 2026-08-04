// PPT管理模块
class PPTManager {
    constructor() {
        this.currentSlides = [];
        this.filteredSlides = [];
        this.currentEditingSlide = null;
        this.previewWindow = null;
        this.init();
    }

    init() {
        this.bindEvents();
        console.log('PPT管理模块初始化完成');
    }

    bindEvents() {
        // 刷新按钮
        document.getElementById('refreshPptBtn')?.addEventListener('click', () => {
            this.loadSlides();
        });

        // 添加幻灯片按钮
        document.getElementById('addPptSlideBtn')?.addEventListener('click', () => {
            this.showAddSlideModal();
        });

        // 搜索功能
        document.getElementById('searchPptBtn')?.addEventListener('click', () => {
            this.searchSlides();
        });

        // 清空搜索
        document.getElementById('clearPptSearchBtn')?.addEventListener('click', () => {
            this.clearSearch();
        });

        // 全选功能
        document.getElementById('selectAllSlides')?.addEventListener('change', (e) => {
            this.toggleSelectAll(e.target.checked);
        });

        // 监听tab切换到PPT管理时加载数据
        document.addEventListener('tabChanged', (e) => {
            if (e.detail.tabId === 'ppt') {
                this.loadSlides();
            }
        });
    }

    async loadSlides() {
        try {
            console.log('开始加载PPT幻灯片数据...');
            const response = await window.api.getPptSlides();
            
            if (response.success) {
                this.currentSlides = response.data || [];
                this.filteredSlides = [...this.currentSlides];
                this.updateStatistics();
                this.renderSlidesTable();
                console.log('PPT幻灯片数据加载成功:', this.currentSlides.length);
            } else {
                throw new Error(response.message || '加载幻灯片失败');
            }
        } catch (error) {
            console.error('加载PPT幻灯片失败:', error);
            this.showNotification('加载幻灯片失败: ' + error.message, 'error');
        }
    }

    updateStatistics() {
        const totalSlides = this.currentSlides.length;
        const uniqueProjects = new Set(this.currentSlides.map(slide => slide.courseId)).size;
        const htmlSlides = this.currentSlides.filter(slide => 
            slide.contentType === 'html' || slide.htmlContent
        ).length;
        
        // 计算本周新增（简单模拟）
        const oneWeekAgo = new Date();
        oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);
        const recentSlides = this.currentSlides.filter(slide => {
            if (!slide.createAt) return false;
            const createDate = new Date(slide.createAt);
            return createDate > oneWeekAgo;
        }).length;

        // 更新统计卡片
        document.getElementById('totalSlides').textContent = totalSlides;
        document.getElementById('totalPptProjects').textContent = uniqueProjects;
        document.getElementById('htmlSlides').textContent = htmlSlides;
        document.getElementById('recentSlides').textContent = recentSlides;
    }

    renderSlidesTable() {
        const tbody = document.querySelector('#pptSlidesTable tbody');
        if (!tbody) return;

        tbody.innerHTML = '';

        if (this.filteredSlides.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="10" class="text-center">
                        <div class="empty-state">
                            <i class="fas fa-presentation"></i>
                            <p>暂无幻灯片数据</p>
                        </div>
                    </td>
                </tr>
            `;
            return;
        }

        this.filteredSlides.forEach(slide => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>
                    <input type="checkbox" class="slide-checkbox" value="${slide.id}">
                </td>
                <td>${slide.id}</td>
                <td>
                    <span class="course-id" title="${slide.courseId}">
                        ${this.truncateText(slide.courseId, 15)}
                    </span>
                </td>
                <td>
                    <span class="slide-id" title="${slide.slideId}">
                        ${this.truncateText(slide.slideId, 15)}
                    </span>
                </td>
                <td>
                    <span class="slide-index">${slide.slideIndex}</span>
                </td>
                <td>
                    <span class="slide-title" title="${slide.title || ''}">
                        ${this.truncateText(slide.title || '无标题', 20)}
                    </span>
                </td>
                <td>
                    <span class="content-type ${slide.contentType || 'unknown'}">
                        ${slide.contentType || '未知'}
                    </span>
                </td>
                <td>
                    <div class="content-preview">
                        ${this.renderContentPreview(slide)}
                    </div>
                </td>
                <td>
                    <span class="create-time">
                        ${slide.createAt ? this.formatDate(slide.createAt) : '未知'}
                    </span>
                </td>
                <td>
                    <div class="action-buttons">
                        <button class="btn btn-sm btn-info" onclick="window.pptManager.viewSlide(${slide.id})" title="查看">
                            <i class="fas fa-eye"></i>
                        </button>
                        <button class="btn btn-sm btn-primary" onclick="window.pptManager.editSlide(${slide.id})" title="编辑">
                            <i class="fas fa-edit"></i>
                        </button>
                        <button class="btn btn-sm btn-success" onclick="window.pptManager.previewSlide(${slide.id})" title="预览">
                            <i class="fas fa-play"></i>
                        </button>
                        <button class="btn btn-sm btn-danger" onclick="window.pptManager.deleteSlide(${slide.id})" title="删除">
                            <i class="fas fa-trash"></i>
                        </button>
                    </div>
                </td>
            `;
            tbody.appendChild(row);
        });
    }

    renderContentPreview(slide) {
        if (!slide.htmlContent) {
            return '<span class="no-content">无内容</span>';
        }

        const content = slide.htmlContent;
        const textContent = content.replace(/<[^>]*>/g, ''); // 移除HTML标签
        const preview = this.truncateText(textContent, 50);
        
        return `
            <div class="html-preview" title="${textContent}">
                <i class="fas fa-code"></i>
                <span>${preview}</span>
            </div>
        `;
    }

    async searchSlides() {
        const courseId = document.getElementById('pptSearchProject').value.trim();
        const title = document.getElementById('pptSearchTitle').value.trim();
        const contentType = document.getElementById('pptSearchType').value;

        try {
            let slides = [];

            if (courseId) {
                // 按项目ID搜索
                const response = await window.api.getPptSlidesByCourseId(courseId);
                if (response.success) {
                    slides = response.data || [];
                }
            } else {
                // 使用当前所有数据进行过滤
                slides = [...this.currentSlides];
            }

            // 应用其他过滤条件
            this.filteredSlides = slides.filter(slide => {
                let matches = true;

                if (title && slide.title) {
                    matches = matches && slide.title.toLowerCase().includes(title.toLowerCase());
                }

                if (contentType) {
                    matches = matches && slide.contentType === contentType;
                }

                return matches;
            });

            this.renderSlidesTable();
            this.showNotification(`找到 ${this.filteredSlides.length} 个匹配的幻灯片`, 'success');
        } catch (error) {
            console.error('搜索幻灯片失败:', error);
            this.showNotification('搜索失败: ' + error.message, 'error');
        }
    }

    clearSearch() {
        document.getElementById('pptSearchProject').value = '';
        document.getElementById('pptSearchTitle').value = '';
        document.getElementById('pptSearchType').value = '';
        
        this.filteredSlides = [...this.currentSlides];
        this.renderSlidesTable();
        this.showNotification('搜索条件已清空', 'info');
    }

    showAddSlideModal() {
        const modalTitle = document.getElementById('modalTitle');
        const modalBody = document.getElementById('modalBody');
        
        modalTitle.textContent = '添加PPT幻灯片';
        modalBody.innerHTML = this.getSlideFormHTML();
        
        this.showModal();
        this.initSlideForm();
    }

    async editSlide(slideId) {
        try {
            const response = await window.api.getPptSlideById(slideId);
            if (response.success && response.data) {
                this.currentEditingSlide = response.data;
                
                const modalTitle = document.getElementById('modalTitle');
                const modalBody = document.getElementById('modalBody');
                
                modalTitle.textContent = '编辑PPT幻灯片';
                modalBody.innerHTML = this.getSlideFormHTML(response.data);
                
                this.showModal();
                this.initSlideForm(response.data);
            } else {
                throw new Error(response.message || '获取幻灯片详情失败');
            }
        } catch (error) {
            console.error('编辑幻灯片失败:', error);
            this.showNotification('编辑幻灯片失败: ' + error.message, 'error');
        }
    }

    async viewSlide(slideId) {
        try {
            const response = await window.api.getPptSlideById(slideId);
            if (response.success && response.data) {
                const slide = response.data;
                
                const modalTitle = document.getElementById('modalTitle');
                const modalBody = document.getElementById('modalBody');
                
                modalTitle.textContent = '查看PPT幻灯片';
                modalBody.innerHTML = this.getSlideViewHTML(slide);
                
                this.showModal();
            } else {
                throw new Error(response.message || '获取幻灯片详情失败');
            }
        } catch (error) {
            console.error('查看幻灯片失败:', error);
            this.showNotification('查看幻灯片失败: ' + error.message, 'error');
        }
    }

    previewSlide(slideId) {
        const slide = this.currentSlides.find(s => s.id === slideId);
        if (!slide || !slide.htmlContent) {
            this.showNotification('该幻灯片没有可预览的HTML内容', 'warning');
            return;
        }

        // 关闭之前的预览窗口
        if (this.previewWindow && !this.previewWindow.closed) {
            this.previewWindow.close();
        }

        // 创建预览窗口
        this.previewWindow = window.open('', 'slidePreview', 
            'width=800,height=600,scrollbars=yes,resizable=yes');
        
        if (this.previewWindow) {
            this.previewWindow.document.write(`
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>幻灯片预览 - ${slide.title || slide.slideId}</title>
                    <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                            margin: 0;
                            padding: 20px;
                            background: #f5f5f5;
                        }
                        .preview-header {
                            background: white;
                            padding: 15px;
                            border-radius: 8px;
                            margin-bottom: 20px;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                        }
                        .preview-content {
                            background: white;
                            padding: 20px;
                            border-radius: 8px;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                            min-height: 400px;
                        }
                        .slide-info {
                            display: flex;
                            gap: 20px;
                            margin-bottom: 10px;
                        }
                        .info-item {
                            font-size: 14px;
                            color: #666;
                        }
                        .info-label {
                            font-weight: bold;
                            color: #333;
                        }
                    </style>
                </head>
                <body>
                    <div class="preview-header">
                        <h2>${slide.title || '无标题'}</h2>
                        <div class="slide-info">
                            <div class="info-item">
                                <span class="info-label">课程ID:</span> ${slide.courseId}
                            </div>
                            <div class="info-item">
                                <span class="info-label">幻灯片ID:</span> ${slide.slideId}
                            </div>
                            <div class="info-item">
                                <span class="info-label">索引:</span> ${slide.slideIndex}
                            </div>
                            <div class="info-item">
                                <span class="info-label">类型:</span> ${slide.contentType || '未知'}
                            </div>
                        </div>
                    </div>
                    <div class="preview-content">
                        ${slide.htmlContent}
                    </div>
                </body>
                </html>
            `);
            this.previewWindow.document.close();
        } else {
            this.showNotification('无法打开预览窗口，请检查浏览器弹窗设置', 'error');
        }
    }

    async deleteSlide(slideId) {
        if (!confirm('确定要删除这个幻灯片吗？此操作不可撤销。')) {
            return;
        }

        try {
            const response = await window.api.deletePptSlide(slideId);
            if (response.success) {
                this.showNotification('幻灯片删除成功', 'success');
                this.loadSlides(); // 重新加载数据
            } else {
                throw new Error(response.message || '删除幻灯片失败');
            }
        } catch (error) {
            console.error('删除幻灯片失败:', error);
            this.showNotification('删除幻灯片失败: ' + error.message, 'error');
        }
    }

    getSlideFormHTML(slide = null) {
        const isEdit = !!slide;
        return `
            <form id="slideForm" class="slide-form">
                <div class="form-row">
                    <div class="form-group">
                        <label for="slideCourseId">课程ID *</label>
                        <input type="text" id="slideCourseId" name="courseId" 
                               value="${slide?.courseId || ''}" required>
                    </div>
                    <div class="form-group">
                        <label for="slideSlideId">幻灯片ID *</label>
                        <input type="text" id="slideSlideId" name="slideId" 
                               value="${slide?.slideId || ''}" required>
                    </div>
                </div>
                
                <div class="form-row">
                    <div class="form-group">
                        <label for="slideIndex">索引 *</label>
                        <input type="number" id="slideIndex" name="slideIndex" 
                               value="${slide?.slideIndex || 1}" min="1" required>
                    </div>
                    <div class="form-group">
                        <label for="slideContentType">内容类型</label>
                        <select id="slideContentType" name="contentType">
                            <option value="html" ${slide?.contentType === 'html' ? 'selected' : ''}>HTML</option>
                            <option value="text" ${slide?.contentType === 'text' ? 'selected' : ''}>文本</option>
                            <option value="image" ${slide?.contentType === 'image' ? 'selected' : ''}>图片</option>
                            <option value="video" ${slide?.contentType === 'video' ? 'selected' : ''}>视频</option>
                        </select>
                    </div>
                </div>
                
                <div class="form-group">
                    <label for="slideTitle">标题</label>
                    <input type="text" id="slideTitle" name="title" 
                           value="${slide?.title || ''}" placeholder="输入幻灯片标题">
                </div>
                
                <div class="form-group">
                    <label for="slideHtmlContent">HTML内容</label>
                    <div class="html-editor-container">
                        <div class="editor-toolbar">
                            <button type="button" class="btn btn-sm btn-secondary" onclick="window.pptManager.previewHtmlContent()">
                                <i class="fas fa-eye"></i> 预览
                            </button>
                            <button type="button" class="btn btn-sm btn-info" onclick="window.pptManager.formatHtmlContent()">
                                <i class="fas fa-code"></i> 格式化
                            </button>
                            <button type="button" class="btn btn-sm btn-success" onclick="window.pptManager.insertHtmlTemplate()">
                                <i class="fas fa-plus"></i> 插入模板
                            </button>
                        </div>
                        <textarea id="slideHtmlContent" name="htmlContent" rows="10" 
                                  placeholder="输入HTML内容...">${slide?.htmlContent || ''}</textarea>
                        <div id="htmlPreview" class="html-preview-container" style="display: none;">
                            <div class="preview-header">
                                <span>HTML预览</span>
                                <button type="button" class="btn btn-sm btn-secondary" onclick="window.pptManager.hideHtmlPreview()">
                                    <i class="fas fa-times"></i>
                                </button>
                            </div>
                            <div class="preview-content"></div>
                        </div>
                    </div>
                </div>
                
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.pptManager.hideModal()">取消</button>
                    <button type="submit" class="btn btn-primary">
                        ${isEdit ? '更新' : '创建'}幻灯片
                    </button>
                </div>
            </form>
        `;
    }

    getSlideViewHTML(slide) {
        return `
            <div class="slide-view">
                <div class="slide-info-grid">
                    <div class="info-item">
                        <label>ID:</label>
                        <span>${slide.id}</span>
                    </div>
                    <div class="info-item">
                        <label>课程ID:</label>
                        <span>${slide.courseId}</span>
                    </div>
                    <div class="info-item">
                        <label>幻灯片ID:</label>
                        <span>${slide.slideId}</span>
                    </div>
                    <div class="info-item">
                        <label>索引:</label>
                        <span>${slide.slideIndex}</span>
                    </div>
                    <div class="info-item">
                        <label>标题:</label>
                        <span>${slide.title || '无标题'}</span>
                    </div>
                    <div class="info-item">
                        <label>内容类型:</label>
                        <span class="content-type ${slide.contentType || 'unknown'}">
                            ${slide.contentType || '未知'}
                        </span>
                    </div>
                    <div class="info-item">
                        <label>创建时间:</label>
                        <span>${slide.createAt ? this.formatDate(slide.createAt) : '未知'}</span>
                    </div>
                </div>
                
                ${slide.htmlContent ? `
                    <div class="html-content-section">
                        <div class="section-header">
                            <h4>HTML内容</h4>
                            <div class="section-actions">
                                <button class="btn btn-sm btn-info" onclick="window.pptManager.previewSlide(${slide.id})">
                                    <i class="fas fa-external-link-alt"></i> 新窗口预览
                                </button>
                            </div>
                        </div>
                        <div class="html-content-display">
                            <div class="code-view">
                                <pre><code>${this.escapeHtml(slide.htmlContent)}</code></pre>
                            </div>
                            <div class="rendered-view">
                                <div class="rendered-content">
                                    ${slide.htmlContent}
                                </div>
                            </div>
                        </div>
                    </div>
                ` : '<div class="no-content">该幻灯片没有HTML内容</div>'}
                
                <div class="view-actions">
                    <button class="btn btn-primary" onclick="window.pptManager.editSlide(${slide.id})">
                        <i class="fas fa-edit"></i> 编辑
                    </button>
                    <button class="btn btn-secondary" onclick="window.pptManager.hideModal()">
                        <i class="fas fa-times"></i> 关闭
                    </button>
                </div>
            </div>
        `;
    }

    initSlideForm(slide = null) {
        const form = document.getElementById('slideForm');
        if (!form) return;

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            await this.saveSlide(slide);
        });

        // 初始化HTML内容编辑器的一些功能
        const htmlTextarea = document.getElementById('slideHtmlContent');
        if (htmlTextarea) {
            // 添加Tab键支持
            htmlTextarea.addEventListener('keydown', (e) => {
                if (e.key === 'Tab') {
                    e.preventDefault();
                    const start = e.target.selectionStart;
                    const end = e.target.selectionEnd;
                    e.target.value = e.target.value.substring(0, start) + '    ' + e.target.value.substring(end);
                    e.target.selectionStart = e.target.selectionEnd = start + 4;
                }
            });
        }
    }

    async saveSlide(existingSlide = null) {
        const form = document.getElementById('slideForm');
        const formData = new FormData(form);
        
        // 获取表单数据并进行验证
        const courseId = formData.get('courseId');
        const slideId = formData.get('slideId');
        const slideIndex = formData.get('slideIndex');
        
        console.log('表单数据获取结果:', {
            courseId: courseId,
            slideId: slideId,
            slideIndex: slideIndex,
            title: formData.get('title'),
            contentType: formData.get('contentType'),
            htmlContent: formData.get('htmlContent')
        });
        
        // 验证必填字段
        if (!courseId || courseId.trim() === '') {
            this.showNotification('课程ID不能为空', 'error');
            return;
        }
        
        if (!slideId || slideId.trim() === '') {
            this.showNotification('幻灯片ID不能为空', 'error');
            return;
        }
        
        if (!slideIndex || isNaN(parseInt(slideIndex))) {
            this.showNotification('幻灯片索引必须是有效数字', 'error');
            return;
        }
        
        const slideData = {
            courseId: courseId.trim(),
            slideId: slideId.trim(),
            slideIndex: parseInt(slideIndex),
            title: formData.get('title') || '',
            contentType: formData.get('contentType') || 'html',
            htmlContent: formData.get('htmlContent') || ''
        };
        
        console.log('准备发送的数据:', slideData);

        try {
            let response;
            if (existingSlide) {
                // 更新现有幻灯片
                console.log('更新幻灯片，ID:', existingSlide.id);
                console.log('发送更新请求到:', `/admin/ppt/slides/${existingSlide.id}`);
                console.log('更新数据:', JSON.stringify(slideData, null, 2));
                response = await window.api.updatePptSlide(existingSlide.id, slideData);
            } else {
                // 创建新幻灯片
                console.log('创建新幻灯片');
                console.log('发送创建请求到:', '/admin/ppt/slides');
                console.log('创建数据:', JSON.stringify(slideData, null, 2));
                response = await window.api.createPptSlide(slideData);
            }
            
            console.log('API响应:', response);

            if (response.success) {
                this.showNotification(
                    existingSlide ? '幻灯片更新成功' : '幻灯片创建成功', 
                    'success'
                );
                this.hideModal();
                this.loadSlides(); // 重新加载数据
            } else {
                throw new Error(response.message || '保存幻灯片失败');
            }
        } catch (error) {
            console.error('保存幻灯片失败:', error);
            this.showNotification('保存幻灯片失败: ' + error.message, 'error');
        }
    }

    previewHtmlContent() {
        const htmlContent = document.getElementById('slideHtmlContent').value;
        const previewContainer = document.getElementById('htmlPreview');
        const previewContent = previewContainer.querySelector('.preview-content');
        
        if (!htmlContent.trim()) {
            this.showNotification('请先输入HTML内容', 'warning');
            return;
        }

        previewContent.innerHTML = htmlContent;
        previewContainer.style.display = 'block';
    }

    hideHtmlPreview() {
        const previewContainer = document.getElementById('htmlPreview');
        previewContainer.style.display = 'none';
    }

    formatHtmlContent() {
        const textarea = document.getElementById('slideHtmlContent');
        let html = textarea.value;
        
        try {
            // 简单的HTML格式化
            html = html.replace(/></g, '>\n<');
            html = html.replace(/^\s+|\s+$/g, '');
            
            // 添加缩进
            const lines = html.split('\n');
            let indentLevel = 0;
            const formattedLines = lines.map(line => {
                const trimmed = line.trim();
                if (!trimmed) return '';
                
                if (trimmed.startsWith('</')) {
                    indentLevel = Math.max(0, indentLevel - 1);
                }
                
                const formatted = '    '.repeat(indentLevel) + trimmed;
                
                if (trimmed.startsWith('<') && !trimmed.startsWith('</') && !trimmed.endsWith('/>')) {
                    indentLevel++;
                }
                
                return formatted;
            });
            
            textarea.value = formattedLines.join('\n');
            this.showNotification('HTML内容已格式化', 'success');
        } catch (error) {
            this.showNotification('格式化失败: ' + error.message, 'error');
        }
    }

    insertHtmlTemplate() {
        const templates = {
            'basic': `<div class="slide-content">
    <h2>幻灯片标题</h2>
    <p>这里是幻灯片的内容...</p>
</div>`,
            'title-content': `<div class="slide">
    <div class="slide-header">
        <h1>主标题</h1>
        <h2>副标题</h2>
    </div>
    <div class="slide-body">
        <p>内容段落...</p>
        <ul>
            <li>要点一</li>
            <li>要点二</li>
            <li>要点三</li>
        </ul>
    </div>
</div>`,
            'image-text': `<div class="slide-layout">
    <div class="slide-image">
        <img src="image-url" alt="描述" style="max-width: 100%; height: auto;">
    </div>
    <div class="slide-text">
        <h3>图片说明</h3>
        <p>相关文字描述...</p>
    </div>
</div>`
        };

        const templateOptions = Object.keys(templates).map(key => 
            `<option value="${key}">${key}</option>`
        ).join('');

        const modalBody = document.createElement('div');
        modalBody.innerHTML = `
            <div class="template-selector">
                <h4>选择HTML模板</h4>
                <select id="templateSelect">
                    <option value="">选择模板...</option>
                    ${templateOptions}
                </select>
                <div class="template-preview" id="templatePreview"></div>
                <div class="template-actions">
                    <button type="button" class="btn btn-secondary" onclick="this.closest('.modal').style.display='none'">取消</button>
                    <button type="button" class="btn btn-primary" onclick="window.pptManager.applyTemplate()">应用模板</button>
                </div>
            </div>
        `;

        // 创建临时模态框
        const tempModal = document.createElement('div');
        tempModal.className = 'modal';
        tempModal.style.display = 'block';
        tempModal.innerHTML = `
            <div class="modal-content">
                <div class="modal-header">
                    <h3>插入HTML模板</h3>
                    <button onclick="this.closest('.modal').remove()" class="close-btn">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="modal-body"></div>
            </div>
        `;
        tempModal.querySelector('.modal-body').appendChild(modalBody);
        document.body.appendChild(tempModal);

        // 绑定模板选择事件
        const templateSelect = tempModal.querySelector('#templateSelect');
        const templatePreview = tempModal.querySelector('#templatePreview');
        
        templateSelect.addEventListener('change', (e) => {
            const template = templates[e.target.value];
            if (template) {
                templatePreview.innerHTML = `<pre><code>${this.escapeHtml(template)}</code></pre>`;
            } else {
                templatePreview.innerHTML = '';
            }
        });

        // 应用模板函数
        window.pptManager.applyTemplate = () => {
            const selectedTemplate = templateSelect.value;
            if (selectedTemplate && templates[selectedTemplate]) {
                const textarea = document.getElementById('slideHtmlContent');
                const currentContent = textarea.value;
                const template = templates[selectedTemplate];
                
                if (currentContent.trim()) {
                    if (confirm('当前已有内容，是否要替换？')) {
                        textarea.value = template;
                    } else {
                        textarea.value = currentContent + '\n\n' + template;
                    }
                } else {
                    textarea.value = template;
                }
                
                tempModal.remove();
                this.showNotification('模板已插入', 'success');
            } else {
                this.showNotification('请选择一个模板', 'warning');
            }
        };
    }

    toggleSelectAll(checked) {
        const checkboxes = document.querySelectorAll('.slide-checkbox');
        checkboxes.forEach(checkbox => {
            checkbox.checked = checked;
        });
    }

    // 工具方法
    truncateText(text, maxLength) {
        if (!text) return '';
        return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
    }

    formatDate(dateString) {
        try {
            const date = new Date(dateString);
            return date.toLocaleString('zh-CN');
        } catch (error) {
            return dateString;
        }
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    showModal() {
        if (window.modal) {
            window.modal.show();
        } else {
            // 备用方案
            const modal = document.getElementById('modal');
            if (modal) {
                modal.classList.add('active');
                document.body.style.overflow = 'hidden';
            }
        }
    }

    hideModal() {
        if (window.modal) {
            window.modal.close();
        } else {
            // 备用方案
            const modal = document.getElementById('modal');
            if (modal) {
                modal.classList.remove('active');
                document.body.style.overflow = '';
            }
        }
    }

    showNotification(message, type = 'info') {
        // 使用现有的通知系统
        if (window.showNotification) {
            window.showNotification(message, type);
        } else {
            console.log(`[${type.toUpperCase()}] ${message}`);
        }
    }
}

// 创建全局PPT管理实例
window.pptManager = new PPTManager();