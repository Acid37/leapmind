// 语音管理模块
class SpeechManager {
    constructor() {
        this.speechTasksTable = document.getElementById('speechTasksTable').querySelector('tbody');
        this.bulkSynthesisBtn = document.getElementById('bulkSynthesisBtn');
        this.refreshSpeechBtn = document.getElementById('refreshSpeechBtn');
        this.searchSpeechBtn = document.getElementById('searchSpeechBtn');
        this.clearSpeechSearchBtn = document.getElementById('clearSpeechSearchBtn');
        this.speechSearchCourse = document.getElementById('speechSearchCourse');
        this.speechSearchStatus = document.getElementById('speechSearchStatus');

        this.speechTasks = [];
        this.audioSegments = [];
        this.currentAudio = null; // 当前播放的音频对象
        this.currentView = 'segments'; // 当前视图：tasks 或 segments，默认显示音频片段
        this.init();
    }

    init() {
        // 绑定事件
        this.bulkSynthesisBtn.addEventListener('click', () => this.showBulkSynthesisModal());
        this.refreshSpeechBtn.addEventListener('click', () => this.refreshCurrentView());
        this.searchSpeechBtn.addEventListener('click', () => this.searchCurrentView());
        this.clearSpeechSearchBtn.addEventListener('click', () => this.clearSearch());

        // 添加视图切换按钮
        this.addViewToggleButtons();

        // 默认加载音频片段列表
        this.loadAudioSegments();
    }

    // 添加视图切换按钮
    addViewToggleButtons() {
        const headerActions = document.querySelector('#speechTab .header-actions');
        if (headerActions && !document.getElementById('viewToggleBtn')) {
            const toggleBtn = document.createElement('button');
            toggleBtn.id = 'viewToggleBtn';
            toggleBtn.className = 'btn btn-info';
            toggleBtn.innerHTML = '<i class="fas fa-tasks"></i> 查看语音任务';
            toggleBtn.addEventListener('click', () => this.toggleView());

            // 插入到刷新按钮之前
            headerActions.insertBefore(toggleBtn, this.refreshSpeechBtn);
        }
    }

    // 切换视图
    toggleView() {
        if (this.currentView === 'tasks') {
            this.currentView = 'segments';
            this.loadAudioSegments();
            document.getElementById('viewToggleBtn').innerHTML = '<i class="fas fa-tasks"></i> 查看语音任务';
            this.updateViewTitle('音频片段管理', '管理系统中的音频片段数据');
        } else {
            this.currentView = 'tasks';
            this.loadSpeechTasks();
            document.getElementById('viewToggleBtn').innerHTML = '<i class="fas fa-list"></i> 查看音频片段';
            this.updateViewTitle('语音管理', '管理PPT语音合成和音频文件');
        }
    }

    // 更新视图标题（如果页面中有对应元素）
    updateViewTitle(title, subtitle) {
        const titleElement = document.querySelector('#speechTab .header-left h2') ||
            document.querySelector('.test-header h1');
        const subtitleElement = document.querySelector('#speechTab .header-left .header-subtitle') ||
            document.querySelector('.test-header p');

        if (titleElement) {
            titleElement.textContent = title;
        }
        if (subtitleElement) {
            subtitleElement.textContent = subtitle;
        }
    }

    // 刷新当前视图
    refreshCurrentView() {
        if (this.currentView === 'tasks') {
            this.loadSpeechTasks();
        } else {
            this.loadAudioSegments();
        }
    }

    // 搜索当前视图
    searchCurrentView() {
        if (this.currentView === 'tasks') {
            this.searchSpeechTasks();
        } else {
            this.searchAudioSegments();
        }
    }

    // 加载语音任务列表
    async loadSpeechTasks() {
        try {
            console.log('Loading speech tasks...');
            // 初始化时使用getAllAudioSegments接口
            const response = await api.getAllAudioSegments();
            console.log('Speech tasks response:', response);
            if (response.success) {
                // 将音频片段数据转换为任务格式显示
                const segments = response.data || [];
                this.speechTasks = this.convertSegmentsToTasks(segments);
                this.renderSpeechTasks(this.speechTasks);
                this.updateStatistics();
            } else {
                console.error('Failed to load speech tasks:', response.message);
                this.showMessage('加载语音任务列表失败: ' + response.message, 'error');
            }
        } catch (error) {
            console.error('Failed to load speech tasks:', error);
            this.showMessage('加载语音任务列表失败: ' + error.message, 'error');
        }
    }

    // 将音频片段数据转换为任务格式
    convertSegmentsToTasks(segments) {
        const taskMap = new Map();

        segments.forEach(segment => {
            const courseId = segment.courseId;
            if (!taskMap.has(courseId)) {
                taskMap.set(courseId, {
                    courseId: courseId,
                    title: `课程 ${courseId}`,
                    totalSlides: 0,
                    totalContentPoints: 0,
                    status: 'COMPLETED',
                    startTime: segment.createdAt,
                    segments: []
                });
            }

            const task = taskMap.get(courseId);
            task.segments.push(segment);
            task.totalContentPoints++;

            // 更新最早的创建时间
            if (segment.createdAt && (!task.startTime || segment.createdAt < task.startTime)) {
                task.startTime = segment.createdAt;
            }
        });

        // 估算幻灯片数量（假设每10个片段为一页）
        taskMap.forEach(task => {
            task.totalSlides = Math.ceil(task.totalContentPoints / 10) || 1;
        });

        return Array.from(taskMap.values());
    }

    // 加载音频片段列表
    async loadAudioSegments() {
        try {
            console.log('Loading audio segments...');
            const response = await api.getAllAudioSegments();
            console.log('Audio segments response:', response);
            if (response.success) {
                this.audioSegments = response.data || [];
                this.renderAudioSegments(this.audioSegments);
                this.updateAudioSegmentStatistics();
            } else {
                console.error('Failed to load audio segments:', response.message);
                this.showMessage('加载音频片段列表失败: ' + response.message, 'error');
            }
        } catch (error) {
            console.error('Failed to load audio segments:', error);
            this.showMessage('加载音频片段列表失败: ' + error.message, 'error');
        }
    }

    // 搜索语音任务
    async searchSpeechTasks() {
        const courseId = this.speechSearchCourse.value.trim();
        const status = this.speechSearchStatus.value.trim();

        try {
            let filteredTasks = [...this.speechTasks];

            if (courseId) {
                filteredTasks = filteredTasks.filter(task =>
                    task.courseId && task.courseId.toLowerCase().includes(courseId.toLowerCase())
                );
            }

            if (status) {
                filteredTasks = filteredTasks.filter(task => task.status === status);
            }

            this.renderSpeechTasks(filteredTasks);
        } catch (error) {
            console.error('Search failed:', error);
            this.showMessage('搜索失败', 'error');
        }
    }

    // 搜索音频片段
    async searchAudioSegments() {
        const courseId = this.speechSearchCourse.value.trim();

        try {
            if (courseId) {
                const response = await api.getAudioSegmentsByCourseId(courseId);
                if (response.success) {
                    this.renderAudioSegments(response.data || []);
                }
            } else {
                this.renderAudioSegments(this.audioSegments);
            }
        } catch (error) {
            console.error('Search failed:', error);
            this.showMessage('搜索失败', 'error');
        }
    }

    // 清除搜索
    clearSearch() {
        this.speechSearchCourse.value = '';
        this.speechSearchStatus.value = '';
        if (this.currentView === 'tasks') {
            this.renderSpeechTasks(this.speechTasks);
        } else {
            this.renderAudioSegments(this.audioSegments);
        }
    }

    // 渲染语音任务列表
    renderSpeechTasks(tasks) {
        console.log('Rendering speech tasks:', tasks);
        // 更新表头
        this.updateTableHeader('tasks');

        if (!tasks || tasks.length === 0) {
            this.speechTasksTable.innerHTML = `
                <tr>
                    <td colspan="8" class="empty-state">
                        <h3>暂无语音任务</h3>
                        <p>点击"批量语音合成"按钮创建新任务</p>
                    </td>
                </tr>
            `;
            return;
        }

        this.speechTasksTable.innerHTML = tasks.map(task => `
            <tr>
                <td><input type="checkbox" value="${task.courseId}"></td>
                <td>${task.courseId}</td>
                <td>${task.title || '未知标题'}</td>
                <td>${task.totalSlides || 0}</td>
                <td>${task.totalContentPoints || 0}</td>
                <td>
                    <span class="status-badge status-${task.status?.toLowerCase() || 'unknown'}">
                        ${this.getStatusDisplayName(task.status)}
                    </span>
                </td>
                <td>${this.formatDateTime(task.startTime)}</td>
                <td class="actions">
                    <button class="btn btn-primary" onclick="speechManager.viewTaskDetails('${task.courseId}')">查看</button>
                    <button class="btn btn-success" onclick="speechManager.playAudio('${task.courseId}')">播放</button>
                    <button class="btn btn-warning" onclick="speechManager.downloadAudio('${task.courseId}')">下载</button>
                </td>
            </tr>
        `).join('');
    }

    // 渲染音频片段列表
    renderAudioSegments(segments) {
        console.log('Rendering audio segments:', segments);
        // 更新表头
        this.updateTableHeader('segments');

        if (!segments || segments.length === 0) {
            this.speechTasksTable.innerHTML = `
                <tr>
                    <td colspan="6" class="empty-state">
                        <h3>暂无音频片段</h3>
                        <p>请先进行语音合成生成音频片段</p>
                    </td>
                </tr>
            `;
            return;
        }

        this.speechTasksTable.innerHTML = segments.map(segment => `
            <tr>
                <td><input type="checkbox" value="${segment.id}"></td>
                <td>${segment.courseId || '未知'}</td>
                <td class="text-content" title="${segment.originalText || ''}">
                    ${this.truncateText(segment.originalText || '无原始文本', 40)}
                </td>
                <td class="text-content" title="${segment.polishedText || ''}">
                    ${this.truncateText(segment.polishedText || '无润色文本', 40)}
                </td>
                <td class="audio-cell">
                    ${segment.audioData ? `
                        <button class="btn btn-success btn-sm" onclick="speechManager.playSegmentAudio(${segment.id})">
                            <i class="fas fa-play"></i> 播放
                        </button>
                        <button class="btn btn-info btn-sm" onclick="speechManager.downloadSegmentAudio(${segment.id})">
                            <i class="fas fa-download"></i>
                        </button>
                    ` : '<span class="text-muted">无音频</span>'}
                </td>
                <td class="actions">
                    <button class="btn btn-primary btn-sm" onclick="speechManager.viewSegmentDetails(${segment.id})">查看</button>
                    <button class="btn btn-warning btn-sm" onclick="speechManager.editSegment(${segment.id})">编辑</button>
                    <button class="btn btn-danger btn-sm" onclick="speechManager.deleteSegment(${segment.id})">删除</button>
                </td>
            </tr>
        `).join('');
    }

    // 更新表头
    updateTableHeader(viewType) {
        const table = document.getElementById('speechTasksTable');
        const thead = table.querySelector('thead tr');

        if (viewType === 'tasks') {
            thead.innerHTML = `
                <th><input type="checkbox" id="selectAllTasks"></th>
                <th>课程ID</th>
                <th>PPT标题</th>
                <th>幻灯片数量</th>
                <th>内容点数量</th>
                <th>状态</th>
                <th>开始时间</th>
                <th>操作</th>
            `;
        } else {
            thead.innerHTML = `
                <th><input type="checkbox" id="selectAllSegments"></th>
                <th>课程ID</th>
                <th>原始文本</th>
                <th>润色文本</th>
                <th>音频</th>
                <th>操作</th>
            `;
        }
    }

    // 截断文本
    truncateText(text, maxLength) {
        if (!text) return '';
        if (text.length <= maxLength) return text;
        return text.substring(0, maxLength) + '...';
    }

    // 播放音频片段
    async playSegmentAudio(segmentId) {
        try {
            console.log('Playing segment audio:', segmentId);
            // 停止当前播放的音频
            if (this.currentAudio) {
                this.currentAudio.pause();
                this.currentAudio = null;
            }

            // 获取音频数据并播放
            const response = await api.getAudioSegmentById(segmentId);
            if (response.success && response.data && response.data.audioData) {
                const audioData = response.data.audioData;

                // 将base64音频数据转换为Blob URL
                const byteCharacters = atob(audioData);
                const byteNumbers = new Array(byteCharacters.length);
                for (let i = 0; i < byteCharacters.length; i++) {
                    byteNumbers[i] = byteCharacters.charCodeAt(i);
                }
                const byteArray = new Uint8Array(byteNumbers);
                const blob = new Blob([byteArray], { type: 'audio/wav' });
                const audioUrl = URL.createObjectURL(blob);

                // 创建音频对象并播放
                this.currentAudio = new Audio(audioUrl);
                this.currentAudio.onended = () => {
                    URL.revokeObjectURL(audioUrl);
                    this.currentAudio = null;
                };
                this.currentAudio.onerror = () => {
                    URL.revokeObjectURL(audioUrl);
                    this.currentAudio = null;
                    this.showMessage('音频播放失败', 'error');
                };

                await this.currentAudio.play();
                this.showMessage('开始播放音频', 'success');
            } else {
                this.showMessage('无法获取音频数据', 'error');
            }
        } catch (error) {
            console.error('Failed to play segment audio:', error);
            this.showMessage('播放音频失败: ' + error.message, 'error');
        }
    }

    // 下载音频片段
    async downloadSegmentAudio(segmentId) {
        try {
            const response = await api.getAudioSegmentById(segmentId);
            if (response.success && response.data && response.data.audioData) {
                const audioData = response.data.audioData;
                const segment = response.data;

                // 将base64音频数据转换为Blob
                const byteCharacters = atob(audioData);
                const byteNumbers = new Array(byteCharacters.length);
                for (let i = 0; i < byteCharacters.length; i++) {
                    byteNumbers[i] = byteCharacters.charCodeAt(i);
                }
                const byteArray = new Uint8Array(byteNumbers);
                const blob = new Blob([byteArray], { type: 'audio/wav' });

                // 创建下载链接
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `audio_${segment.courseId}_${segment.segmentIndex || segmentId}.wav`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);

                this.showMessage('音频下载成功', 'success');
            } else {
                this.showMessage('无法获取音频数据', 'error');
            }
        } catch (error) {
            console.error('Failed to download segment audio:', error);
            this.showMessage('下载音频失败: ' + error.message, 'error');
        }
    }

    // 查看音频片段详情
    async viewSegmentDetails(segmentId) {
        try {
            const response = await api.getAudioSegmentById(segmentId);
            if (response.success && response.data) {
                this.showSegmentDetailsModal(response.data);
            }
        } catch (error) {
            console.error('Failed to get segment details:', error);
            this.showMessage('获取音频片段详情失败', 'error');
        }
    }

    // 显示音频片段详情模态框
    showSegmentDetailsModal(segment) {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <div class="segment-details">
                <div class="form-row">
                    <div class="form-group">
                        <label>ID:</label>
                        <p>${segment.id}</p>
                    </div>
                    <div class="form-group">
                        <label>课程ID:</label>
                        <p>${segment.courseId || '未知'}</p>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>片段索引:</label>
                        <p>${segment.segmentIndex || '未知'}</p>
                    </div>
                    <div class="form-group">
                        <label>音频格式:</label>
                        <p>${segment.audioFormat || '未知'}</p>
                    </div>
                </div>
                <div class="form-group">
                    <label>原始文本:</label>
                    <div class="text-display">${segment.originalText || '无'}</div>
                </div>
                <div class="form-group">
                    <label>润色文本:</label>
                    <div class="text-display">${segment.polishedText || '无'}</div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>音频大小:</label>
                        <p>${this.formatFileSize(segment.audioSize)}</p>
                    </div>
                    <div class="form-group">
                        <label>时长:</label>
                        <p>${this.formatDuration(segment.duration)}</p>
                    </div>
                </div>
                <div class="form-group">
                    <label>创建时间:</label>
                    <p>${this.formatDateTime(segment.createdAt)}</p>
                </div>
                ${segment.audioData ? `
                    <div class="form-group">
                        <label>音频播放:</label>
                        <div class="audio-controls">
                            <button type="button" class="btn btn-success" onclick="speechManager.playSegmentAudio(${segment.id})">
                                <i class="fas fa-play"></i> 播放
                            </button>
                            <button type="button" class="btn btn-info" onclick="speechManager.downloadSegmentAudio(${segment.id})">
                                <i class="fas fa-download"></i> 下载
                            </button>
                        </div>
                    </div>
                ` : ''}
                <div class="form-actions">
                    <button type="button" class="btn btn-warning" onclick="speechManager.editSegment(${segment.id})">编辑</button>
                    <button type="button" class="btn btn-danger" onclick="speechManager.deleteSegment(${segment.id})">删除</button>
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">关闭</button>
                </div>
            </div>
        `;

        document.getElementById('modalTitle').textContent = `音频片段详情 - ${segment.id}`;
        window.modal.show();
    }

    // 编辑音频片段
    async editSegment(segmentId) {
        try {
            const response = await api.getAudioSegmentById(segmentId);
            if (response.success && response.data) {
                this.showEditSegmentModal(response.data);
            }
        } catch (error) {
            console.error('Failed to get segment for editing:', error);
            this.showMessage('获取音频片段信息失败', 'error');
        }
    }

    // 显示编辑音频片段模态框
    showEditSegmentModal(segment) {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <form id="editSegmentForm">
                <input type="hidden" name="id" value="${segment.id}">
                <div class="form-row">
                    <div class="form-group">
                        <label for="editCourseId">课程ID *</label>
                        <input type="text" id="editCourseId" name="courseId" value="${segment.courseId || ''}" required>
                    </div>
                    <div class="form-group">
                        <label for="editSegmentIndex">片段索引 *</label>
                        <input type="number" id="editSegmentIndex" name="segmentIndex" value="${segment.segmentIndex || 0}" required>
                    </div>
                </div>
                <div class="form-group">
                    <label for="editOriginalText">原始文本</label>
                    <textarea id="editOriginalText" name="originalText" rows="3" placeholder="请输入原始文本">${segment.originalText || ''}</textarea>
                </div>
                <div class="form-group">
                    <label for="editPolishedText">润色文本</label>
                    <textarea id="editPolishedText" name="polishedText" rows="3" placeholder="请输入润色后的文本">${segment.polishedText || ''}</textarea>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label for="editAudioFormat">音频格式</label>
                        <select id="editAudioFormat" name="audioFormat">
                            <option value="wav" ${segment.audioFormat === 'wav' ? 'selected' : ''}>WAV</option>
                            <option value="mp3" ${segment.audioFormat === 'mp3' ? 'selected' : ''}>MP3</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="editSampleRate">采样率</label>
                        <select id="editSampleRate" name="sampleRate">
                            <option value="16000" ${segment.sampleRate === 16000 ? 'selected' : ''}>16000 Hz</option>
                            <option value="22050" ${segment.sampleRate === 22050 ? 'selected' : ''}>22050 Hz</option>
                            <option value="44100" ${segment.sampleRate === 44100 ? 'selected' : ''}>44100 Hz</option>
                        </select>
                    </div>
                </div>
                <div class="form-group">
                    <label for="editDuration">时长 (毫秒)</label>
                    <input type="number" id="editDuration" name="duration" value="${segment.duration || 0}">
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="submit" class="btn btn-primary">保存</button>
                </div>
            </form>
        `;

        document.getElementById('modalTitle').textContent = `编辑音频片段 - ${segment.id}`;
        window.modal.show();

        // 绑定表单提交事件
        const form = document.getElementById('editSegmentForm');
        form.addEventListener('submit', (e) => this.handleEditSegment(e));
    }

    // 处理编辑音频片段
    async handleEditSegment(e) {
        e.preventDefault();

        const formData = new FormData(e.target);
        const segmentData = {
            courseId: formData.get('courseId'),
            segmentIndex: parseInt(formData.get('segmentIndex')),
            originalText: formData.get('originalText'),
            polishedText: formData.get('polishedText'),
            audioFormat: formData.get('audioFormat'),
            sampleRate: parseInt(formData.get('sampleRate')),
            duration: parseInt(formData.get('duration')) || null
        };

        try {
            const segmentId = formData.get('id');
            const response = await api.updateAudioSegment(segmentId, segmentData);

            if (response.success) {
                this.showMessage('音频片段更新成功', 'success');
                window.modal.close();
                this.loadAudioSegments();
            } else {
                this.showMessage(response.message || '更新音频片段失败', 'error');
            }
        } catch (error) {
            console.error('Failed to update segment:', error);
            this.showMessage('更新音频片段失败: ' + error.message, 'error');
        }
    }

    // 删除音频片段
    async deleteSegment(segmentId) {
        if (!confirm('确定要删除这个音频片段吗？此操作不可恢复。')) {
            return;
        }

        try {
            const response = await api.deleteAudioSegment(segmentId);
            if (response.success) {
                this.showMessage('音频片段删除成功', 'success');
                this.loadAudioSegments();
                if (window.modal && window.modal.isVisible) {
                    window.modal.close();
                }
            } else {
                this.showMessage(response.message || '删除音频片段失败', 'error');
            }
        } catch (error) {
            console.error('Failed to delete segment:', error);
            this.showMessage('删除音频片段失败: ' + error.message, 'error');
        }
    }

    // 显示批量语音合成模态框
    showBulkSynthesisModal() {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <form id="bulkSynthesisForm">
                <div class="form-group">
                    <label for="jsonInput">JSON数据 *</label>
                    <textarea id="jsonInput" name="jsonData" rows="15" required 
                        placeholder="请输入JSON格式的批量语音合成数据&#10;例如：&#10;{&#10;  &quot;title&quot;: &quot;示例PPT&quot;,&#10;  &quot;slides&quot;: [&#10;    {&#10;      &quot;slideNumber&quot;: 1,&#10;      &quot;contentPoints&quot;: [&quot;第一页标题&quot;, &quot;第一页内容1&quot;, &quot;第一页内容2&quot;]&#10;    },&#10;    {&#10;      &quot;slideNumber&quot;: 2,&#10;      &quot;contentPoints&quot;: [&quot;第二页标题&quot;, &quot;第二页内容1&quot;]&#10;    }&#10;  ]&#10;}"></textarea>
                    <small class="form-text text-muted">
                        请输入符合BulkSynthesisRequest格式的JSON数据
                    </small>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="button" class="btn btn-info" onclick="speechManager.validateJson()">验证JSON</button>
                    <button type="submit" class="btn btn-primary">开始合成</button>
                </div>
            </form>
        `;

        document.getElementById('modalTitle').textContent = '批量语音合成';
        window.modal.show();

        // 绑定表单提交事件
        const form = document.getElementById('bulkSynthesisForm');
        form.addEventListener('submit', (e) => this.handleBulkSynthesis(e));
    }

    // 验证JSON格式
    validateJson() {
        const jsonInput = document.getElementById('jsonInput');
        const jsonData = jsonInput.value.trim();

        if (!jsonData) {
            this.showMessage('请输入JSON数据', 'error');
            return;
        }

        try {
            const parsedData = JSON.parse(jsonData);

            // 基本验证
            if (!parsedData.title) {
                this.showMessage('JSON数据缺少title字段', 'error');
                return;
            }

            if (!parsedData.slides || !Array.isArray(parsedData.slides)) {
                this.showMessage('JSON数据缺少slides字段或slides不是数组', 'error');
                return;
            }

            if (parsedData.slides.length === 0) {
                this.showMessage('slides数组不能为空', 'error');
                return;
            }

            // 验证slides结构
            for (let i = 0; i < parsedData.slides.length; i++) {
                const slide = parsedData.slides[i];
                if (!slide.slideNumber || !slide.contentPoints || !Array.isArray(slide.contentPoints)) {
                    this.showMessage(`第${i + 1}个slide格式错误，需要包含slideNumber和contentPoints数组`, 'error');
                    return;
                }
            }

            this.showMessage('JSON格式验证通过！', 'success');
        } catch (error) {
            this.showMessage('JSON格式错误: ' + error.message, 'error');
        }
    }

    // 处理批量语音合成
    async handleBulkSynthesis(e) {
        e.preventDefault();

        const formData = new FormData(e.target);
        const jsonData = formData.get('jsonData').trim();

        if (!jsonData) {
            this.showMessage('请输入JSON数据', 'error');
            return;
        }

        let synthesisData;
        try {
            synthesisData = JSON.parse(jsonData);
        } catch (error) {
            this.showMessage('JSON格式错误: ' + error.message, 'error');
            return;
        }

        // 基本验证
        if (!synthesisData.title) {
            this.showMessage('JSON数据缺少title字段', 'error');
            return;
        }

        if (!synthesisData.slides || !Array.isArray(synthesisData.slides) || synthesisData.slides.length === 0) {
            this.showMessage('JSON数据缺少有效的slides字段', 'error');
            return;
        }

        try {
            this.showMessage('正在提交批量语音合成请求...', 'info');
            const response = await api.bulkSynthesis(synthesisData);

            if (response.success || response.status === 'SUCCESS') {
                this.showMessage('批量语音合成任务已启动，请稍后查看结果', 'success');
                window.modal.close();
                // 刷新数据
                setTimeout(() => {
                    this.refreshCurrentView();
                }, 2000);
            } else {
                this.showMessage(response.message || '批量语音合成失败', 'error');
            }
        } catch (error) {
            console.error('Failed to start bulk synthesis:', error);
            this.showMessage('批量语音合成失败: ' + error.message, 'error');
        }
    }

    // 查看任务详情
    async viewTaskDetails(courseId) {
        try {
            const response = await api.getPPTAudioInfo(courseId);
            if (response.success && response.data) {
                this.showTaskDetailsModal(response.data);
            }
        } catch (error) {
            console.error('Failed to get task details:', error);
            this.showMessage('获取任务详情失败', 'error');
        }
    }

    // 显示任务详情模态框
    showTaskDetailsModal(taskInfo) {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <div class="task-details">
                <div class="form-row">
                    <div class="form-group">
                        <label>课程ID:</label>
                        <p>${taskInfo.courseId}</p>
                    </div>
                    <div class="form-group">
                        <label>状态:</label>
                        <span class="status-badge status-${taskInfo.status?.toLowerCase() || 'unknown'}">
                            ${this.getStatusDisplayName(taskInfo.status)}
                        </span>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>幻灯片数量:</label>
                        <p>${taskInfo.totalSlides || 0}</p>
                    </div>
                    <div class="form-group">
                        <label>内容点数量:</label>
                        <p>${taskInfo.totalContentPoints || 0}</p>
                    </div>
                </div>
                <div class="form-group">
                    <label>开始时间:</label>
                    <p>${this.formatDateTime(taskInfo.startTime)}</p>
                </div>
                ${taskInfo.message ? `
                    <div class="form-group">
                        <label>消息:</label>
                        <p>${taskInfo.message}</p>
                    </div>
                ` : ''}
                <div class="form-actions">
                    <button type="button" class="btn btn-success" onclick="speechManager.playAudio('${taskInfo.courseId}')">播放音频</button>
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">关闭</button>
                </div>
            </div>
        `;

        document.getElementById('modalTitle').textContent = `任务详情 - ${taskInfo.courseId}`;
        window.modal.show();
    }

    // 播放音频
    async playAudio(courseId) {
        try {
            // 获取PPT音频信息
            const audioInfoResponse = await api.getPPTAudioInfo(courseId);
            if (!audioInfoResponse.success || !audioInfoResponse.data) {
                this.showMessage('无法获取音频信息', 'error');
                return;
            }

            const audioInfo = audioInfoResponse.data;
            if (!audioInfo.pages || audioInfo.pages.length === 0) {
                this.showMessage('该课程暂无音频数据', 'error');
                return;
            }

            // 显示音频播放控制面板
            this.showAudioPlayerModal(courseId, audioInfo);
        } catch (error) {
            console.error('Failed to play audio:', error);
            this.showMessage('播放音频失败: ' + error.message, 'error');
        }
    }

    // 显示音频播放器模态框
    showAudioPlayerModal(courseId, audioInfo) {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <div class="audio-player">
                <div class="audio-info">
                    <h4>${audioInfo.title || courseId}</h4>
                    <p>总页数: ${audioInfo.totalSlides} | 总内容点: ${audioInfo.totalContentPoints}</p>
                </div>
                <div class="page-selector">
                    <label for="pageSelect">选择页面:</label>
                    <select id="pageSelect" class="form-control">
                        ${audioInfo.pages.map(page =>
            `<option value="${page.pageNumber}">第${page.pageNumber}页 (${page.segmentCount}个片段)</option>`
        ).join('')}
                    </select>
                    <button type="button" class="btn btn-success" onclick="speechManager.playPageAudio('${courseId}')">
                        <i class="fas fa-play"></i> 播放页面
                    </button>
                </div>
                <div class="audio-controls">
                    <audio id="audioPlayer" controls style="width: 100%; margin: 10px 0;">
                        您的浏览器不支持音频播放
                    </audio>
                </div>
                <div class="segments-list" id="segmentsList">
                    <!-- 片段列表将在这里动态加载 -->
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-info" onclick="speechManager.downloadCourseAudio('${courseId}')">
                        <i class="fas fa-download"></i> 下载全部音频
                    </button>
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">关闭</button>
                </div>
            </div>
        `;

        document.getElementById('modalTitle').textContent = `音频播放器 - ${courseId}`;
        window.modal.show();

        // 绑定页面选择事件
        const pageSelect = document.getElementById('pageSelect');
        pageSelect.addEventListener('change', () => this.loadPageSegments(courseId, parseInt(pageSelect.value)));

        // 默认加载第一页的片段
        if (audioInfo.pages.length > 0) {
            this.loadPageSegments(courseId, audioInfo.pages[0].pageNumber);
        }
    }

    // 加载页面片段
    async loadPageSegments(courseId, pageNumber) {
        try {
            const response = await api.getPageSegmentMetadata(courseId, pageNumber);
            if (response.success && response.data) {
                this.renderPageSegments(courseId, pageNumber, response.data);
            }
        } catch (error) {
            console.error('Failed to load page segments:', error);
        }
    }

    // 渲染页面片段
    renderPageSegments(courseId, pageNumber, segments) {
        const segmentsList = document.getElementById('segmentsList');
        if (!segments || segments.length === 0) {
            segmentsList.innerHTML = '<p class="text-muted">该页面暂无音频片段</p>';
            return;
        }

        segmentsList.innerHTML = `
            <h5>第${pageNumber}页音频片段</h5>
            <div class="segments">
                ${segments.map(segment => `
                    <div class="segment-item">
                        <div class="segment-content">
                            <span class="segment-index">${segment.globalIndex}</span>
                            <span class="segment-text">${this.truncateText(segment.textContent, 60)}</span>
                        </div>
                        <div class="segment-actions">
                            <button class="btn btn-sm btn-success" onclick="speechManager.playSegmentByIndex('${courseId}', ${segment.globalIndex})">
                                <i class="fas fa-play"></i>
                            </button>
                            <button class="btn btn-sm btn-info" onclick="speechManager.downloadSegmentByIndex('${courseId}', ${segment.globalIndex})">
                                <i class="fas fa-download"></i>
                            </button>
                        </div>
                    </div>
                `).join('')}
            </div>
        `;
    }

    // 播放页面音频
    async playPageAudio(courseId) {
        try {
            const pageSelect = document.getElementById('pageSelect');
            const pageNumber = parseInt(pageSelect.value);

            const audioPlayer = document.getElementById('audioPlayer');
            const audioUrl = `${api.baseURL}/speech/ppt/${courseId}/page/${pageNumber}/audio`;

            audioPlayer.src = audioUrl;
            audioPlayer.load();
            await audioPlayer.play();

            this.showMessage(`开始播放第${pageNumber}页音频`, 'success');
        } catch (error) {
            console.error('Failed to play page audio:', error);
            this.showMessage('播放页面音频失败', 'error');
        }
    }

    // 通过索引播放音频片段
    async playSegmentByIndex(courseId, segmentIndex) {
        try {
            const audioPlayer = document.getElementById('audioPlayer');
            const audioUrl = `${api.baseURL}/speech/ppt/${courseId}/segment/${segmentIndex}/audio`;

            audioPlayer.src = audioUrl;
            audioPlayer.load();
            await audioPlayer.play();

            this.showMessage(`开始播放片段 ${segmentIndex}`, 'success');
        } catch (error) {
            console.error('Failed to play segment audio:', error);
            this.showMessage('播放音频片段失败', 'error');
        }
    }

    // 通过索引下载音频片段
    async downloadSegmentByIndex(courseId, segmentIndex) {
        try {
            const audioUrl = `${api.baseURL}/speech/ppt/${courseId}/segment/${segmentIndex}/audio`;
            const a = document.createElement('a');
            a.href = audioUrl;
            a.download = `audio_${courseId}_segment_${segmentIndex}.wav`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);

            this.showMessage('音频片段下载已开始', 'success');
        } catch (error) {
            console.error('Failed to download segment audio:', error);
            this.showMessage('下载音频片段失败', 'error');
        }
    }

    // 下载课程全部音频
    async downloadCourseAudio(courseId) {
        try {
            // 获取音频信息
            const audioInfoResponse = await api.getPPTAudioInfo(courseId);
            if (!audioInfoResponse.success || !audioInfoResponse.data) {
                this.showMessage('无法获取音频信息', 'error');
                return;
            }

            const audioInfo = audioInfoResponse.data;
            if (!audioInfo.pages || audioInfo.pages.length === 0) {
                this.showMessage('该课程暂无音频数据', 'error');
                return;
            }

            // 下载每一页的音频
            for (const page of audioInfo.pages) {
                const audioUrl = `${api.baseURL}/speech/ppt/${courseId}/page/${page.pageNumber}/audio`;
                const a = document.createElement('a');
                a.href = audioUrl;
                a.download = `audio_${courseId}_page_${page.pageNumber}.wav`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);

                // 添加延迟避免同时下载太多文件
                await new Promise(resolve => setTimeout(resolve, 500));
            }

            this.showMessage('全部音频下载已开始', 'success');
        } catch (error) {
            console.error('Failed to download course audio:', error);
            this.showMessage('下载课程音频失败: ' + error.message, 'error');
        }
    }

    // 下载音频（兼容原有接口）
    async downloadAudio(courseId) {
        await this.downloadCourseAudio(courseId);
    }

    // 更新统计信息
    updateStatistics() {
        const totalFiles = this.speechTasks.length;
        const processingTasks = this.speechTasks.filter(task => task.status === 'PROCESSING').length;
        const completedTasks = this.speechTasks.filter(task => task.status === 'COMPLETED').length;

        document.getElementById('totalAudioFiles').textContent = totalFiles;
        document.getElementById('processingTasks').textContent = processingTasks;
        document.getElementById('completedTasks').textContent = completedTasks;
    }

    // 更新音频片段统计信息
    updateAudioSegmentStatistics() {
        const totalSegments = this.audioSegments.length;
        const totalSize = this.audioSegments.reduce((sum, segment) => sum + (segment.audioSize || 0), 0);
        const totalDuration = this.audioSegments.reduce((sum, segment) => sum + (segment.duration || 0), 0);

        document.getElementById('totalAudioFiles').textContent = totalSegments;
        document.getElementById('totalDuration').textContent = this.formatDuration(totalDuration);
        document.getElementById('processingTasks').textContent = '0';
        document.getElementById('completedTasks').textContent = totalSegments;
    }

    // 获取状态显示名称
    getStatusDisplayName(status) {
        const statusMap = {
            'STARTED': '已启动',
            'PROCESSING': '处理中',
            'COMPLETED': '已完成',
            'FAILED': '失败'
        };
        return statusMap[status] || status || '未知';
    }

    // 格式化日期时间
    formatDateTime(dateTime) {
        if (!dateTime) return '未知';
        try {
            return new Date(dateTime).toLocaleString('zh-CN');
        } catch (error) {
            return dateTime;
        }
    }

    // 格式化文件大小
    formatFileSize(bytes) {
        if (!bytes) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    // 格式化时长
    formatDuration(milliseconds) {
        if (!milliseconds) return '0秒';
        const seconds = Math.floor(milliseconds / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);

        if (hours > 0) {
            return `${hours}小时${minutes % 60}分钟${seconds % 60}秒`;
        } else if (minutes > 0) {
            return `${minutes}分钟${seconds % 60}秒`;
        } else {
            return `${seconds}秒`;
        }
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
window.SpeechManager = SpeechManager;