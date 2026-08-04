// 课程管理模块
class CourseManager {
    constructor() {
        this.coursesTable = document.getElementById('coursesTable').querySelector('tbody');
        this.addCourseBtn = document.getElementById('addCourseBtn');
        this.searchCoursesBtn = document.getElementById('searchCoursesBtn');
        this.clearCourseSearchBtn = document.getElementById('clearCourseSearchBtn');
        this.courseSearchStage = document.getElementById('courseSearchStage');
        this.courseSearchSubject = document.getElementById('courseSearchSubject');
        
        this.courses = [];
        this.init();
    }

    init() {
        // 绑定事件
        // 如果 CourseManagementModule 存在，则使用它的添加课程功能，避免冲突
        if (!window.courseManagement) {
            this.addCourseBtn.addEventListener('click', () => this.showAddCourseModal());
        } else {
            console.log('CourseManagementModule 已存在，使用它的添加课程功能');
        }
        this.searchCoursesBtn.addEventListener('click', () => this.searchCourses());
        this.clearCourseSearchBtn.addEventListener('click', () => this.clearSearch());
        
        // 加载课程列表
        this.loadCourses();
    }

    // 加载课程列表
    async loadCourses() {
        try {
            const response = await api.getCourses();
            if (response.success) {
                this.courses = response.data || [];
                this.renderCourses(this.courses);
            }
        } catch (error) {
            console.error('Failed to load courses:', error);
            this.showMessage('加载课程列表失败', 'error');
        }
    }

    // 搜索课程
    async searchCourses() {
        const stage = this.courseSearchStage.value.trim();
        const subject = this.courseSearchSubject.value.trim();

        try {
            let courses = [];
            
            if (stage && subject) {
                // 如果同时有阶段和学科条件，先按阶段搜索，再过滤学科
                const stageResponse = await api.getCoursesByStage(stage);
                if (stageResponse.success) {
                    courses = (stageResponse.data || []).filter(course => 
                        course.subject.toLowerCase().includes(subject.toLowerCase())
                    );
                }
            } else if (stage) {
                const response = await api.getCoursesByStage(stage);
                if (response.success) {
                    courses = response.data || [];
                }
            } else if (subject) {
                const response = await api.getCoursesBySubject(subject);
                if (response.success) {
                    courses = response.data || [];
                }
            } else {
                // 如果没有搜索条件，加载所有课程
                await this.loadCourses();
                return;
            }
            
            this.renderCourses(courses);
        } catch (error) {
            console.error('Search failed:', error);
            this.showMessage('搜索失败', 'error');
        }
    }

    // 清除搜索
    clearSearch() {
        this.courseSearchStage.value = '';
        this.courseSearchSubject.value = '';
        this.loadCourses();
    }

    // 渲染课程列表
    renderCourses(courses) {
        if (!courses || courses.length === 0) {
            this.coursesTable.innerHTML = `
                <tr>
                    <td colspan="10" class="empty-state">
                        <h3>暂无课程数据</h3>
                        <p>点击"添加课程"按钮创建新课程</p>
                    </td>
                </tr>
            `;
            return;
        }

        this.coursesTable.innerHTML = courses.map(course => `
            <tr>
                <td>${course.courseId || course.id}</td>
                <td>${course.subject}</td>
                <td>${course.stageName}</td>
                <td>${course.gradeName}</td>
                <td>${this.getSemesterDisplayName(course.semester)}</td>
                <td>${course.chapterNumber}</td>
                <td>${course.chapterTitle}</td>
                <td>${course.sectionNumber}</td>
                <td>${course.sectionTitle}</td>
                <td class="actions">
                    <button class="btn btn-primary" onclick="courseManager.viewCourse('${course.courseId || course.id}')">查看</button>
                    <button class="btn btn-warning" onclick="courseManager.editCourse('${course.courseId || course.id}', ${course.id})">编辑</button>
                    <button class="btn btn-danger" onclick="courseManager.deleteCourse(${course.id})">删除</button>
                </td>
            </tr>
        `).join('');
    }

    // 显示添加课程模态框
    showAddCourseModal() {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <form id="addCourseForm">
                <div class="form-group">
                    <label for="addCourseId">课程ID *</label>
                    <input type="text" id="addCourseId" name="courseId" required placeholder="请输入课程唯一标识">
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label for="addSubject">学科 *</label>
                        <input type="text" id="addSubject" name="subject" required>
                    </div>
                    <div class="form-group">
                        <label for="addStageName">阶段 *</label>
                        <select id="addStageName" name="stageName" required>
                            <option value="">请选择阶段</option>
                            <option value="小学">小学</option>
                            <option value="初中">初中</option>
                        </select>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label for="addGradeName">年级 *</label>
                        <select id="addGradeName" name="gradeName" required>
                            <option value="">请选择年级</option>
                            <option value="一年级">一年级</option>
                            <option value="二年级">二年级</option>
                            <option value="三年级">三年级</option>
                            <option value="四年级">四年级</option>
                            <option value="五年级">五年级</option>
                            <option value="六年级">六年级</option>
                            <option value="初一">初一</option>
                            <option value="初二">初二</option>
                            <option value="初三">初三</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="addSemester">学期 *</label>
                        <select id="addSemester" name="semester" required>
                            <option value="">请选择学期</option>
                            <option value="SEMESTER_1">上册</option>
                            <option value="SEMESTER_2">下册</option>
                        </select>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label for="addChapterNumber">章节编号 *</label>
                        <input type="number" id="addChapterNumber" name="chapterNumber" required>
                    </div>
                    <div class="form-group">
                        <label for="addSectionNumber">小节编号 *</label>
                        <input type="number" id="addSectionNumber" name="sectionNumber" step="0.1" required>
                    </div>
                </div>
                <div class="form-group">
                    <label for="addChapterTitle">章节标题 *</label>
                    <input type="text" id="addChapterTitle" name="chapterTitle" required>
                </div>
                <div class="form-group">
                    <label for="addSectionTitle">小节标题 *</label>
                    <input type="text" id="addSectionTitle" name="sectionTitle" required>
                </div>
                <div class="form-group">
                    <label for="addChapterContent">章节内容</label>
                    <textarea id="addChapterContent" name="chapterContent" rows="4"></textarea>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="submit" class="btn btn-primary">创建课程</button>
                </div>
            </form>
        `;

        document.getElementById('modalTitle').textContent = '添加课程';
        window.modal.show();

        // 绑定表单提交事件
        document.getElementById('addCourseForm').addEventListener('submit', (e) => this.handleAddCourse(e));
    }

    // 处理添加课程
    async handleAddCourse(e) {
        e.preventDefault();
        
        const formData = new FormData(e.target);
        const courseData = Object.fromEntries(formData.entries());
        
        // 转换数据类型
        courseData.chapterNumber = parseInt(courseData.chapterNumber);
        courseData.sectionNumber = parseFloat(courseData.sectionNumber);

        try {
            const response = await api.createCourse(courseData);
            if (response.success) {
                this.showMessage('课程创建成功', 'success');
                window.modal.close();
                this.loadCourses();
            } else {
                this.showMessage(response.message || '创建课程失败', 'error');
            }
        } catch (error) {
            console.error('Failed to create course:', error);
            this.showMessage(error.message || '创建课程失败', 'error');
        }
    }

    // 查看课程详情
    async viewCourse(courseId) {
        try {
            const response = await api.getCourseById(courseId);
            if (response.success && response.data) {
                this.showCourseDetailsModal(response.data);
            }
        } catch (error) {
            console.error('Failed to get course:', error);
            this.showMessage('获取课程信息失败', 'error');
        }
    }

    // 显示课程详情模态框
    showCourseDetailsModal(course) {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <div class="course-details">
                <div class="form-row">
                    <div class="form-group">
                        <label>课程ID:</label>
                        <p>${course.courseId || course.id}</p>
                    </div>
                    <div class="form-group">
                        <label>学科:</label>
                        <p>${course.subject}</p>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>阶段:</label>
                        <p>${course.stageName}</p>
                    </div>
                    <div class="form-group">
                        <label>年级:</label>
                        <p>${course.gradeName}</p>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>学期:</label>
                        <p>${this.getSemesterDisplayName(course.semester)}</p>
                    </div>
                    <div class="form-group">
                        <label>章节编号:</label>
                        <p>${course.chapterNumber}</p>
                    </div>
                </div>
                <div class="form-group">
                    <label>章节标题:</label>
                    <p>${course.chapterTitle}</p>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>小节编号:</label>
                        <p>${course.sectionNumber}</p>
                    </div>
                    <div class="form-group">
                        <label>小节标题:</label>
                        <p>${course.sectionTitle}</p>
                    </div>
                </div>
                ${course.sectionContent ? `
                    <div class="form-group">
                        <label>小节内容:</label>
                        <div class="content-box">${course.sectionContent}</div>
                    </div>
                ` : ''}
                <div class="form-actions">
                    <button type="button" class="btn btn-warning" onclick="courseManager.editCourse('${course.courseId || course.id}', ${course.id})">编辑</button>
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">关闭</button>
                </div>
            </div>
        `;

        document.getElementById('modalTitle').textContent = `课程详情 - ${course.subject}`;
        window.modal.show();
    }

    // 编辑课程
    async editCourse(courseId, dbId) {
        try {
            const response = await api.getCourseById(courseId);
            if (response.success && response.data) {
                // 确保数据中包含数据库ID用于更新
                response.data.dbId = dbId;
                this.showEditCourseModal(response.data);
            }
        } catch (error) {
            console.error('Failed to get course:', error);
            this.showMessage('获取课程信息失败', 'error');
        }
    }

    // 显示编辑课程模态框
    showEditCourseModal(course) {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <form id="editCourseForm">
                <input type="hidden" name="dbId" value="${course.dbId || course.id}">
                <div class="form-row">
                    <div class="form-group">
                        <label for="editSubject">学科</label>
                        <input type="text" id="editSubject" name="subject" value="${course.subject}">
                    </div>
                    <div class="form-group">
                        <label for="editStageName">阶段</label>
                        <select id="editStageName" name="stageName">
                            <option value="">请选择阶段</option>
                            <option value="小学" ${course.stageName === '小学' ? 'selected' : ''}>小学</option>
                            <option value="初中" ${course.stageName === '初中' ? 'selected' : ''}>初中</option>
                        </select>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label for="editGradeName">年级</label>
                        <select id="editGradeName" name="gradeName">
                            <option value="">请选择年级</option>
                            <option value="一年级" ${course.gradeName === '一年级' ? 'selected' : ''}>一年级</option>
                            <option value="二年级" ${course.gradeName === '二年级' ? 'selected' : ''}>二年级</option>
                            <option value="三年级" ${course.gradeName === '三年级' ? 'selected' : ''}>三年级</option>
                            <option value="四年级" ${course.gradeName === '四年级' ? 'selected' : ''}>四年级</option>
                            <option value="五年级" ${course.gradeName === '五年级' ? 'selected' : ''}>五年级</option>
                            <option value="六年级" ${course.gradeName === '六年级' ? 'selected' : ''}>六年级</option>
                            <option value="初一" ${course.gradeName === '初一' ? 'selected' : ''}>初一</option>
                            <option value="初二" ${course.gradeName === '初二' ? 'selected' : ''}>初二</option>
                            <option value="初三" ${course.gradeName === '初三' ? 'selected' : ''}>初三</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="editSemester">学期</label>
                        <select id="editSemester" name="semester">
                            <option value="">请选择学期</option>
                            <option value="SEMESTER_1" ${course.semester === 'SEMESTER_1' || course.semester === '上册' ? 'selected' : ''}>上册</option>
                            <option value="SEMESTER_2" ${course.semester === 'SEMESTER_2' || course.semester === '下册' ? 'selected' : ''}>下册</option>
                        </select>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label for="editChapterNumber">章节编号</label>
                        <input type="number" id="editChapterNumber" name="chapterNumber" value="${course.chapterNumber}">
                    </div>
                    <div class="form-group">
                        <label for="editSectionNumber">小节编号</label>
                        <input type="number" id="editSectionNumber" name="sectionNumber" step="0.1" value="${course.sectionNumber}">
                    </div>
                </div>
                <div class="form-group">
                    <label for="editChapterTitle">章节标题</label>
                    <input type="text" id="editChapterTitle" name="chapterTitle" value="${course.chapterTitle}">
                </div>
                <div class="form-group">
                    <label for="editSectionTitle">小节标题</label>
                    <input type="text" id="editSectionTitle" name="sectionTitle" value="${course.sectionTitle}">
                </div>
                <div class="form-group">
                    <label for="editSectionContent">小节内容</label>
                    <textarea id="editSectionContent" name="sectionContent" rows="4">${course.sectionContent || ''}</textarea>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="submit" class="btn btn-primary">更新课程</button>
                </div>
            </form>
        `;

        document.getElementById('modalTitle').textContent = '编辑课程';
        window.modal.show();

        // 绑定表单提交事件
        document.getElementById('editCourseForm').addEventListener('submit', (e) => this.handleEditCourse(e));
    }

    // 处理编辑课程
    async handleEditCourse(e) {
        e.preventDefault();
        
        const formData = new FormData(e.target);
        const courseData = Object.fromEntries(formData.entries());
        const dbId = courseData.dbId;
        delete courseData.dbId;

        // 转换数据类型
        if (courseData.chapterNumber) {
            courseData.chapterNumber = parseInt(courseData.chapterNumber);
        }
        if (courseData.sectionNumber) {
            courseData.sectionNumber = parseFloat(courseData.sectionNumber);
        }

        try {
            const response = await api.updateCourse(dbId, courseData);
            if (response.success) {
                this.showMessage('课程更新成功', 'success');
                window.modal.close();
                this.loadCourses();
            }
        } catch (error) {
            console.error('Failed to update course:', error);
            this.showMessage(error.message || '更新课程失败', 'error');
        }
    }

    // 删除课程
    async deleteCourse(id) {
        if (!confirm('确定要删除这个课程吗？此操作不可恢复。')) {
            return;
        }

        try {
            const response = await api.deleteCourse(id);
            if (response.success) {
                this.showMessage('课程删除成功', 'success');
                this.loadCourses();
            }
        } catch (error) {
            console.error('Failed to delete course:', error);
            this.showMessage(error.message || '删除课程失败', 'error');
        }
    }

    // 获取学期显示名称
    getSemesterDisplayName(semester) {
        const semesterMap = {
            'SEMESTER_1': '上册',
            'SEMESTER_2': '下册'
        };
        return semesterMap[semester] || semester;
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
window.CourseManager = CourseManager;