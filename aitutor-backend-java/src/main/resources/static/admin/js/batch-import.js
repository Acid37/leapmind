/**
 * 批量导入用户功能
 */
class BatchImportManager {
    constructor() {
        this.init();
    }

    init() {
        this.bindEvents();
    }

    bindEvents() {
        // 批量导入按钮
        document.getElementById('batchImportBtn')?.addEventListener('click', () => {
            this.showBatchImportModal();
        });

        // 下载模板按钮
        document.getElementById('downloadTemplateBtn')?.addEventListener('click', () => {
            this.downloadTemplate();
        });
    }

    showBatchImportModal() {
        const modalTitle = document.getElementById('modalTitle');
        const modalBody = document.getElementById('modalBody');
        
        modalTitle.textContent = '批量导入用户';
        modalBody.innerHTML = `
            <div class="batch-import-container">
                <div class="import-steps">
                    <div class="step active" data-step="1">
                        <div class="step-number">1</div>
                        <div class="step-title">选择文件</div>
                    </div>
                    <div class="step" data-step="2">
                        <div class="step-number">2</div>
                        <div class="step-title">预览数据</div>
                    </div>
                    <div class="step" data-step="3">
                        <div class="step-number">3</div>
                        <div class="step-title">导入结果</div>
                    </div>
                </div>

                <div class="import-content">
                    <!-- 步骤1: 文件选择 -->
                    <div class="step-content active" data-step="1">
                        <div class="file-upload-area">
                            <div class="upload-zone" id="uploadZone">
                                <i class="fas fa-cloud-upload-alt upload-icon"></i>
                                <h3>拖拽Excel文件到此处</h3>
                                <p>或者 <span class="upload-link" id="selectFileBtn">点击选择文件</span></p>
                                <p class="upload-hint">支持 .xlsx, .xls 格式，最大10MB</p>
                            </div>
                            <input type="file" id="fileInput" accept=".xlsx,.xls" style="display: none;">
                        </div>
                        
                        <div class="template-section">
                            <h4>导入说明</h4>
                            <ul class="import-rules">
                                <li>Excel文件必须包含以下列：用户名、学生姓名、年级、邮箱、手机号</li>
                                <li>用户名必须唯一，不能重复</li>
                                <li>邮箱格式必须正确</li>
                                <li>手机号必须为11位数字</li>
                                <li>年级必须是有效的年级代码（如：PRIMARY_1, JUNIOR_1等）</li>
                            </ul>
                            <button id="downloadTemplateInModal" class="btn btn-outline">
                                <i class="fas fa-download"></i> 下载Excel模板
                            </button>
                        </div>
                    </div>

                    <!-- 步骤2: 数据预览 -->
                    <div class="step-content" data-step="2">
                        <div class="preview-header">
                            <h4>数据预览</h4>
                            <div class="preview-stats">
                                <span class="stat-item">总计: <span id="totalRows">0</span> 行</span>
                                <span class="stat-item valid">有效: <span id="validRows">0</span> 行</span>
                                <span class="stat-item invalid">错误: <span id="invalidRows">0</span> 行</span>
                            </div>
                        </div>
                        
                        <div class="preview-table-container">
                            <table id="previewTable" class="preview-table">
                                <thead>
                                    <tr>
                                        <th>状态</th>
                                        <th>用户名</th>
                                        <th>学生姓名</th>
                                        <th>年级</th>
                                        <th>邮箱</th>
                                        <th>手机号</th>
                                        <th>错误信息</th>
                                    </tr>
                                </thead>
                                <tbody></tbody>
                            </table>
                        </div>

                        <div class="preview-actions">
                            <button id="backToFileBtn" class="btn btn-secondary">返回选择文件</button>
                            <button id="startImportBtn" class="btn btn-primary">开始导入</button>
                        </div>
                    </div>

                    <!-- 步骤3: 导入结果 -->
                    <div class="step-content" data-step="3">
                        <div class="import-result">
                            <div class="result-header">
                                <i class="fas fa-check-circle result-icon success"></i>
                                <h4>导入完成</h4>
                            </div>
                            
                            <div class="result-stats">
                                <div class="stat-card success">
                                    <div class="stat-number" id="successCount">0</div>
                                    <div class="stat-label">成功导入</div>
                                </div>
                                <div class="stat-card error">
                                    <div class="stat-number" id="errorCount">0</div>
                                    <div class="stat-label">导入失败</div>
                                </div>
                                <div class="stat-card total">
                                    <div class="stat-number" id="totalCount">0</div>
                                    <div class="stat-label">总计处理</div>
                                </div>
                            </div>

                            <div class="result-details" id="resultDetails">
                                <!-- 详细结果信息 -->
                            </div>

                            <div class="result-actions">
                                <button id="downloadErrorReportBtn" class="btn btn-outline" style="display: none;">
                                    <i class="fas fa-download"></i> 下载错误报告
                                </button>
                                <button id="closeImportModalBtn" class="btn btn-primary">完成</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // 显示模态框
        document.getElementById('modal').style.display = 'block';
        
        // 绑定模态框内的事件
        this.bindModalEvents();
    }

    bindModalEvents() {
        const fileInput = document.getElementById('fileInput');
        const uploadZone = document.getElementById('uploadZone');
        const selectFileBtn = document.getElementById('selectFileBtn');

        // 文件选择
        selectFileBtn.addEventListener('click', () => {
            fileInput.click();
        });

        // 拖拽上传
        uploadZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            uploadZone.classList.add('dragover');
        });

        uploadZone.addEventListener('dragleave', () => {
            uploadZone.classList.remove('dragover');
        });

        uploadZone.addEventListener('drop', (e) => {
            e.preventDefault();
            uploadZone.classList.remove('dragover');
            const files = e.dataTransfer.files;
            if (files.length > 0) {
                this.handleFileSelect(files[0]);
            }
        });

        // 文件输入变化
        fileInput.addEventListener('change', (e) => {
            if (e.target.files.length > 0) {
                this.handleFileSelect(e.target.files[0]);
            }
        });

        // 模态框内下载模板
        document.getElementById('downloadTemplateInModal')?.addEventListener('click', () => {
            this.downloadTemplate();
        });

        // 返回文件选择
        document.getElementById('backToFileBtn')?.addEventListener('click', () => {
            this.showStep(1);
        });

        // 开始导入
        document.getElementById('startImportBtn')?.addEventListener('click', () => {
            this.startImport();
        });

        // 关闭模态框
        document.getElementById('closeImportModalBtn')?.addEventListener('click', () => {
            document.getElementById('modal').style.display = 'none';
            // 刷新用户列表
            if (window.usersManager) {
                window.usersManager.loadUsers();
            }
        });
    }

    handleFileSelect(file) {
        // 验证文件类型
        const allowedTypes = [
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            'a