/**
 * 课程管理工作流补丁
 * 添加缺失的工作流步骤方法
 */

// 扩展CourseManagementModule类，添加工作流方法
if (window.CourseManagementModule) {
    const proto = window.CourseManagementModule.prototype;

    /**
     * 切换工作流步骤
     */
    proto.switchToStep = function(stepNumber) {
        console.log(`>>> switchToStep被调用, stepNumber: ${stepNumber}`);
        
        // 更新步骤指示器
        document.querySelectorAll('.step').forEach(step => {
            step.classList.remove('active');
        });
        const stepElement = document.querySelector(`[data-step="${stepNumber}"]`);
        if (stepElement) {
            stepElement.classList.add('active');
            console.log('>>> 步骤指示器已更新');
        } else {
            console.warn('>>> 找不到步骤指示器元素:', `[data-step="${stepNumber}"]`);
        }

        // 更新步骤内容
        document.querySelectorAll('.step-content').forEach(content => {
            content.classList.remove('active');
        });
        const contentElement = document.getElementById(`step${stepNumber}Content`);
        if (contentElement) {
            contentElement.classList.add('active');
            console.log('>>> 步骤内容已更新');
        } else {
            console.warn('>>> 找不到步骤内容元素:', `step${stepNumber}Content`);
        }

        this.workflowStep = stepNumber;
        console.log('>>> workflowStep已设置为:', this.workflowStep);

        // 根据步骤执行特定逻辑
        console.log('>>> 准备调用onStepChanged');
        this.onStepChanged(stepNumber);
    };

    /**
     * 步骤切换时的处理
     */
    proto.onStepChanged = async function(stepNumber) {
        console.log('>>> onStepChanged被调用, stepNumber:', stepNumber);
        
        // 【修复】不在切换步骤时保存状态，防止保存不正确的数据
        // 状态应该在每个步骤完成后保存，而不是在切换时
        // if (typeof this.saveWorkflowState === 'function') {
        //     this.saveWorkflowState();
        // }
        
        switch (stepNumber) {
            case 2:
                // 切换到审核步骤时，加载审核内容
                console.log('>>> 步骤2：准备加载审核内容');
                // 使用setTimeout确保DOM已更新
                setTimeout(async () => {
                    console.log('>>> 开始加载审核内容');
                    await this.loadReviewContent();
                }, 100);
                break;
            case 3:
                // 切换到语音合成步骤时，检查状态
                if (typeof this.checkSynthesisStatus === 'function') {
                    this.checkSynthesisStatus();
                }
                break;
            case 4:
                // 切换到PPT插入步骤时，检查是否已完成
                console.log('>>> [PATCH] 步骤4：检查PPT插入状态');
                console.log('>>> [PATCH] 当前课程ID:', this.currentCourse?.courseId);
                console.log('>>> [PATCH] workflowData.step4:', this.workflowData.step4);
                
                // 【关键检查】只有当step4真正完成（有pptResult）且不在编辑模式时，才显示完成状态
                if (this.workflowData.step4 && 
                    this.workflowData.step4.pptResult && 
                    !this.workflowData.step4.editing) {
                    console.log('>>> [PATCH] 检测到step4已完成，显示完成状态');
                    // 步骤4已完成且不在编辑模式，显示完成状态
                    const step4Content = document.getElementById('step4Content');
                    if (step4Content && !step4Content.querySelector('.workflow-complete')) {
                        if (typeof this.showWorkflowComplete === 'function') {
                            this.showWorkflowComplete();
                        }
                    }
                } else {
                    console.log('>>> [PATCH] step4未完成，准备PPT数据');
                    // 步骤4未完成或正在编辑，准备PPT数据
                    // 【强制调用preparePptData】确保表格被正确初始化
                    if (typeof this.preparePptData === 'function') {
                        this.preparePptData();
                    } else {
                        console.error('>>> [PATCH] preparePptData方法不存在！');
                    }
                }
                break;
        }
    };

    /**
     * 处理大纲润色
     */
    proto.processOutline = async function() {
        if (!this.checkNetworkStatus()) {
            return;
        }

        const jsonInput = document.getElementById('outlineJsonInput');
        if (!jsonInput) {
            this.showMessage('找不到输入框元素', 'error');
            return;
        }

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

        try {
            this.showLoading('正在处理大纲润色...');

            // 调用批量文本预处理API
            const response = await this.withRetry(async () => {
                return await this.api.bulkPreprocessing(parsedData);
            });

            console.log('大纲润色响应:', response);

            if (response.success || response.status === 'SUCCESS' || response.status === 'COMPLETED') {
                this.showMessage('大纲润色处理成功', 'success');
                
                // 保存处理结果 - 关键：保存courseId
                const courseId = response.courseId || response.data?.courseId || parsedData.courseId;
                console.log('>>> 保存的courseId:', courseId);
                
                this.workflowData = {
                    step1: {
                        courseId: courseId,
                        originalData: parsedData,
                        processedData: response
                    }
                };
                
                console.log('>>> workflowData已更新:', this.workflowData);
                
                // 保存状态
                if (typeof this.saveWorkflowState === 'function') {
                    this.saveWorkflowState();
                }

                // 自动切换到下一步
                console.log('>>> 准备切换到步骤2');
                setTimeout(() => {
                    console.log('>>> 执行switchToStep(2)');
                    this.switchToStep(2);
                }, 500);
            } else {
                this.showMessage('大纲润色处理失败：' + (response.message || '未知错误'), 'error');
            }
        } catch (error) {
            this.handleError(error, '大纲润色处理');
        } finally {
            this.hideLoading();
        }
    };

    /**
     * 加载审核内容
     */
    proto.loadReviewContent = async function() {
        console.log('开始加载审核内容...');
        console.log('workflowData:', this.workflowData);
        
        const reviewContent = document.getElementById('reviewContent');
        if (!reviewContent) {
            console.error('找不到reviewContent元素');
            return;
        }

        if (!this.workflowData.step1 || !this.workflowData.step1.courseId) {
            console.warn('缺少courseId，无法加载审核内容');
            reviewContent.innerHTML = '<p class="text-warning">请先完成步骤1的大纲润色处理</p>';
            return;
        }

        const courseId = this.workflowData.step1.courseId;
        console.log('使用courseId加载审核内容:', courseId);

        try {
            this.showLoading('正在加载审核内容...');

            // 获取会话详情
            const response = await this.withRetry(async () => {
                return await this.api.getPendingSessionByCourseId(courseId);
            });

            console.log('获取到的会话数据:', response);

            if (response && (response.data || response.courseId)) {
                const sessionData = response.data || response;
                this.displayReviewContent(sessionData);
                this.workflowData.step2 = {
                    sessionData: sessionData
                };
                this.saveWorkflowState();
            } else {
                reviewContent.innerHTML = '<p class="text-warning">暂无审核内容，请检查步骤1是否完成</p>';
            }
        } catch (error) {
            console.error('加载审核内容失败:', error);
            reviewContent.innerHTML = `<p class="text-danger">加载审核内容失败：${error.message}</p>`;
        } finally {
            this.hideLoading();
        }
    };

    /**
     * 显示审核内容
     */
    proto.displayReviewContent = function(sessionData) {
        console.log('显示审核内容:', sessionData);
        
        const reviewContent = document.getElementById('reviewContent');
        if (!reviewContent) {
            console.error('找不到reviewContent元素');
            return;
        }
        
        let contentHtml = `
            <div class="session-info">
                <h4>会话信息</h4>
                <div class="info-grid">
                    <div class="info-item">
                        <label>会话ID:</label>
                        <span class="session-id-display">${sessionData.courseId || sessionData.id || '未知'}</span>
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
                </div>
            </div>

            <div class="review-sections">
                <div class="session-text-review">
                    <h4>会话文本审核</h4>
                    <div class="text-comparison">
                        <div class="text-column">
                            <label>原始文本:</label>
                            <div class="text-display readonly-text">${this.formatTextForDisplay(sessionData.originalText || sessionData.content || '无原始文本')}</div>
                        </div>
                        <div class="text-column">
                            <label>润色文本:</label>
                            <textarea id="polishedTextArea" class="form-control editable-text" rows="8" placeholder="请输入或修改润色后的文本...">${sessionData.polishedText || sessionData.processedContent || ''}</textarea>
                        </div>
                    </div>
                </div>

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
            </div>
        `;

        reviewContent.innerHTML = contentHtml;

        // 绑定保存修改按钮事件
        const saveBtn = document.getElementById('saveReviewChangesBtn');
        if (saveBtn) {
            saveBtn.addEventListener('click', () => {
                this.saveReviewChanges();
            });
        }

        // 绑定提交审核按钮事件
        const submitBtn = document.getElementById('submitReviewBtn');
        if (submitBtn) {
            submitBtn.addEventListener('click', () => {
                this.submitReviewDecision();
            });
        }

        // 监听审核结果变化
        document.querySelectorAll('input[name="reviewApproved"]').forEach(radio => {
            radio.addEventListener('change', () => {
                this.onReviewDecisionChange();
            });
        });
    };

    /**
     * 格式化文本用于显示
     */
    proto.formatTextForDisplay = function(text) {
        if (!text) return '暂无内容';
        
        // 转义HTML特殊字符
        const escaped = text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
        
        // 保留换行
        return escaped.replace(/\n/g, '<br>');
    };

    /**
     * 获取状态显示名称
     */
    proto.getStatusDisplayName = function(status) {
        const statusMap = {
            'PENDING_REVIEW': '待审核',
            'APPROVED': '已审核通过',
            'REJECTED': '已拒绝',
            'SYNTHESIZED': '已合成',
            'COMPLETED': '已完成'
        };
        return statusMap[status] || status || '未知';
    };

    /**
     * 审核决定变化时的处理
     */
    proto.onReviewDecisionChange = function() {
        const approvedRadio = document.querySelector('input[name="reviewApproved"]:checked');
        const commentsArea = document.getElementById('reviewCommentsArea');
        const formText = commentsArea?.nextElementSibling;

        if (approvedRadio && commentsArea) {
            const approved = approvedRadio.value === 'true';
            if (approved) {
                commentsArea.placeholder = '请输入审核意见（可选）...';
                if (formText) formText.textContent = '审核通过时可选填意见';
                commentsArea.classList.remove('required-field');
            } else {
                commentsArea.placeholder = '请输入拒绝原因（必填）...';
                if (formText) formText.textContent = '审核拒绝时必须填写拒绝原因';
                commentsArea.classList.add('required-field');
            }
        }
    };

    /**
     * 保存审核修改
     */
    proto.saveReviewChanges = async function() {
        if (!this.workflowData.step1 || !this.workflowData.step1.courseId) {
            this.showMessage('缺少会话信息', 'error');
            return;
        }

        const polishedText = document.getElementById('polishedTextArea')?.value.trim();
        const reviewComments = document.getElementById('reviewCommentsArea')?.value.trim();

        if (!polishedText) {
            this.showMessage('润色文本不能为空', 'warning');
            return;
        }

        try {
            this.showLoading('正在保存修改...');

            const reviewData = {
                reviewerId: 'admin',
                approved: null,
                comments: reviewComments || '管理员修改内容',
                updatedPolishedText: polishedText,
                reviewTime: new Date().toISOString()
            };

            const response = await this.withRetry(async () => {
                return await this.api.adminReviewSession(this.workflowData.step1.courseId, reviewData);
            });

            if (response.success || response.status === 'SUCCESS') {
                this.showMessage('修改保存成功', 'success');
                
                this.workflowData.step2 = {
                    ...this.workflowData.step2,
                    sessionData: {
                        ...this.workflowData.step2.sessionData,
                        polishedText: polishedText,
                        reviewComments: reviewComments
                    }
                };
                
                this.saveWorkflowState();
            } else {
                this.showMessage('保存修改失败：' + (response.message || '未知错误'), 'error');
            }
        } catch (error) {
            this.handleError(error, '保存审核修改');
        } finally {
            this.hideLoading();
        }
    };

    /**
     * 提交审核决定
     */
    proto.submitReviewDecision = async function() {
        if (!this.workflowData.step1 || !this.workflowData.step1.courseId) {
            this.showMessage('缺少会话信息', 'error');
            return;
        }

        const approvedRadio = document.querySelector('input[name="reviewApproved"]:checked');
        if (!approvedRadio) {
            this.showMessage('请选择审核结果（通过或拒绝）', 'warning');
            return;
        }

        const approved = approvedRadio.value === 'true';
        const polishedText = document.getElementById('polishedTextArea')?.value.trim();
        const reviewComments = document.getElementById('reviewCommentsArea')?.value.trim();

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

            if (response.success || response.status === 'SUCCESS') {
                this.showMessage(approved ? '审核通过成功' : '审核拒绝成功', 'success');
                
                this.workflowData.step2 = {
                    ...this.workflowData.step2,
                    reviewResult: {
                        approved: approved,
                        comments: reviewComments,
                        updatedPolishedText: polishedText,
                        reviewTime: new Date().toISOString()
                    }
                };
                
                this.saveWorkflowState();

                if (approved) {
                    setTimeout(() => {
                        this.switchToStep(3);
                    }, 1000);
                } else {
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
    };

    console.log('课程管理工作流补丁已加载');
}
