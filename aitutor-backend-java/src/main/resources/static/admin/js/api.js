// API 配置和通用请求方法
class API {
    constructor() {
        this.baseURL = 'http://localhost:8080/api';
        this.token = localStorage.getItem('adminToken');
        console.log('API初始化，token状态:', this.token ? 'Present' : 'Missing');
    }

    // 设置认证令牌
    setToken(token) {
        this.token = token;
        localStorage.setItem('adminToken', token);
    }

    // 清除认证令牌
    clearToken() {
        this.token = null;
        localStorage.removeItem('adminToken');
    }

    // 获取请求头
    getHeaders() {
        const headers = {
            'Content-Type': 'application/json'
        };
        
        if (this.token) {
            headers['Authorization'] = `Bearer ${this.token}`;
        }
        
        return headers;
    }

    // 通用请求方法
    async request(url, options = {}) {
        const config = {
            headers: this.getHeaders(),
            ...options
        };

        console.log('API请求:', {
            url: `${this.baseURL}${url}`,
            method: config.method || 'GET',
            hasToken: !!this.token,
            headers: config.headers
        });

        try {
            const response = await fetch(`${this.baseURL}${url}`, config);
            console.log('API响应状态:', response.status);
            
            let data;
            const contentType = response.headers.get('content-type');
            
            if (contentType && contentType.includes('application/json')) {
                data = await response.json();
            } else {
                const text = await response.text();
                console.log('非JSON响应:', text);
                throw new Error(`服务器返回非JSON格式数据: ${text}`);
            }
            
            console.log('API响应数据:', data);

            if (!response.ok) {
                // 特别处理403错误
                if (response.status === 403) {
                    console.error('403错误 - 可能的原因:', {
                        hasToken: !!this.token,
                        tokenValue: this.token ? this.token.substring(0, 20) + '...' : 'null',
                        message: data.message
                    });
                }
                throw new Error(data.message || `HTTP error! status: ${response.status}`);
            }

            // 转换后端的ApiResponse格式为前端期望的格式
            if (data.code !== undefined) {
                return {
                    success: data.code === 200,
                    data: data.data,
                    message: data.message,
                    code: data.code
                };
            }

            return data;
        } catch (error) {
            console.error('API请求失败:', error);
            
            // 如果是401或403错误，清除token并跳转到登录页
            if (error.message.includes('401') || error.message.includes('Unauthorized') || 
                error.message.includes('403') || error.message.includes('Forbidden')) {
                console.log('认证失败，清除token并重新登录');
                this.clearToken();
                if (window.authManager) {
                    window.authManager.showLoginPage();
                } else {
                    window.location.reload();
                }
            }
            
            throw error;
        }
    }

    // GET 请求
    async get(url) {
        return this.request(url, { method: 'GET' });
    }

    // POST 请求
    async post(url, data) {
        return this.request(url, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    }

    // PUT 请求
    async put(url, data) {
        return this.request(url, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    }

    // DELETE 请求
    async delete(url) {
        return this.request(url, { method: 'DELETE' });
    }

    // 认证相关API
    async login(username, password) {
        return this.post('/auth/login', { username, password });
    }

    // 用户管理API
    async getUsers() {
        return this.get('/admin/users');
    }

    async getUserById(id) {
        return this.get(`/admin/users/${id}`);
    }

    async getUserByName(studentName) {
        return this.get(`/admin/users/search/name?studentName=${encodeURIComponent(studentName)}`);
    }

    async getUsersByStage(stage) {
        return this.get(`/admin/users/search/stage?stage=${encodeURIComponent(stage)}`);
    }

    async createUser(userData) {
        return this.post('/admin/users', userData);
    }

    async updateUser(id, userData) {
        return this.put(`/admin/users/${id}`, userData);
    }

    async deleteUser(id) {
        return this.delete(`/admin/users/${id}`);
    }

    // 阶段管理API
    async getStages() {
        return this.get('/admin/stages');
    }

    async getStageByCode(stageCode) {
        return this.get(`/admin/stages/${stageCode}`);
    }

    async getStageStatistics() {
        return this.get('/admin/stages/statistics');
    }

    async getGrades() {
        return this.get('/admin/grades');
    }

    async getGradeByCode(gradeCode) {
        return this.get(`/admin/grades/${gradeCode}`);
    }

    async getGradesByStage(stageCode) {
        return this.get(`/admin/stages/${stageCode}/grades`);
    }

    async getGradeStatistics() {
        return this.get('/admin/grades/statistics');
    }

    // 课程管理API
    async getCourses() {
        return this.get('/admin/courses');
    }

    async getCourseById(courseId) {
        return this.get(`/admin/courses/${courseId}`);
    }

    async getCoursesByStage(stageName) {
        return this.get(`/admin/courses/search/stage?stageName=${encodeURIComponent(stageName)}`);
    }

    async getCoursesBySubject(subject) {
        return this.get(`/admin/courses/search/subject?subject=${encodeURIComponent(subject)}`);
    }

    async createCourse(courseData) {
        return this.post('/admin/courses', courseData);
    }

    async updateCourse(id, courseData) {
        return this.put(`/admin/courses/${id}`, courseData);
    }

    async deleteCourse(id) {
        return this.delete(`/admin/courses/${id}`);
    }

    // 新增课程管理API - 条件搜索
    async searchCoursesByConditions(searchParams) {
        return this.post('/admin/courses/search', searchParams);
    }

    // 课程管理工作流API
    async getCourseSessionDetails(courseId) {
        return this.get(`/speech/session/${courseId}/details`);
    }

    // 课程状态检查API
    async isSessionExist(courseId) {
        return this.get(`/admin/audio-segments/course/${courseId}/session`);
    }

    async getSessionStatus(courseId) {
        return this.get(`/admin/audio-segments/course/${courseId}/audio-status`);
    }

    async isAudioSynthesisExist(courseId) {
        return this.get(`/admin/audio-segments/course/${courseId}/audio-synthesis`);
    }

    async existsSlidesByCourseId(courseId) {
        return this.get(`/admin/ppt/slides/exists/${courseId}`);
    }

    // 大纲管理API
    async getOutlines() {
        return this.get('/admin/outline');
    }

    async getOutlineById(id) {
        return this.get(`/admin/outline/${id}`);
    }

    async getOutlinesByCourseId(courseId) {
        return this.get(`/admin/outline/project/${courseId}`);
    }

    async createOutline(outlineData) {
        return this.post('/admin/outline', outlineData);
    }

    async updateOutline(outlineData) {
        return this.put('/admin/outline', outlineData);
    }

    async deleteOutline(id) {
        return this.delete(`/admin/outline/${id}`);
    }

    // PPT管理API
    async getPptSlides() {
        return this.get('/admin/ppt/slides');
    }

    async getPptSlideById(id) {
        return this.get(`/admin/ppt/slides/${id}`);
    }

    async getPptSlidesByCourseId(courseId) {
        return this.get(`/admin/ppt/slides/project/${courseId}`);
    }

    async createPptSlide(slideData) {
        return this.post('/admin/ppt/slides', slideData);
    }

    async updatePptSlide(id, slideData) {
        return this.put(`/admin/ppt/slides/${id}`, slideData);
    }

    async deletePptSlide(id) {
        return this.delete(`/admin/ppt/slides/${id}`);
    }

    // 语音管理API
    async bulkSynthesis(synthesisData) {
        return this.post('/speech/bulk-synthesis', synthesisData);
    }

    async getPageAudioSegments(courseId, pageNumber) {
        return this.get(`/speech/ppt/${courseId}/page/${pageNumber}`);
    }

    async getPPTAudioInfo(courseId) {
        return this.get(`/speech/ppt/${courseId}`);
    }

    async playPageAudio(courseId, pageNumber) {
        return this.get(`/speech/ppt/${courseId}/page/${pageNumber}/audio`);
    }

    async playAudioSegment(courseId, segmentIndex) {
        return this.get(`/speech/ppt/${courseId}/segment/${segmentIndex}/audio`);
    }

    async getPageSegmentMetadata(courseId, pageNumber) {
        return this.get(`/speech/ppt/${courseId}/page/${pageNumber}/segments`);
    }

    // 获取所有语音任务（会话）- 使用音频片段数据模拟
    async getSpeechTasks() {
        return this.getAllAudioSegments();
    }

    // 音频片段管理API
    async getAllAudioSegments() {
        return this.get('/admin/audio-segments');
    }

    async getAudioSegmentsByCourseId(courseId) {
        return this.get(`/admin/audio-segments/course/${courseId}`);
    }

    async getAudioSegmentById(id) {
        return this.get(`/admin/audio-segments/${id}`);
    }

    async updateAudioSegment(id, segmentData) {
        return this.put(`/admin/audio-segments/${id}`, segmentData);
    }

    async deleteAudioSegment(id) {
        return this.delete(`/admin/audio-segments/${id}`);
    }

    async deleteAudioSegmentsByCourseId(courseId) {
        return this.delete(`/admin/audio-segments/course/${courseId}`);
    }

    async getAudioSegmentStats(courseId) {
        return this.get(`/admin/audio-segments/course/${courseId}/stats`);
    }

    // 获取音频文件的Blob URL用于播放
    async getAudioBlobUrl(id) {
        const response = await fetch(`${this.baseURL}/admin/audio-segments/${id}`, {
            headers: this.getHeaders()
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        if (data.code === 200 && data.data && data.data.audioData) {
            // 将base64音频数据转换为Blob URL
            const audioData = data.data.audioData;
            const byteCharacters = atob(audioData);
            const byteNumbers = new Array(byteCharacters.length);
            for (let i = 0; i < byteCharacters.length; i++) {
                byteNumbers[i] = byteCharacters.charCodeAt(i);
            }
            const byteArray = new Uint8Array(byteNumbers);
            const blob = new Blob([byteArray], { type: 'audio/wav' });
            return URL.createObjectURL(blob);
        }
        
        throw new Error('无法获取音频数据');
    }

    // 会话审查管理API
    
    // 批量文本预处理
    async bulkPreprocessing(preprocessingData) {
        return this.post('/speech/bulk-preprocessing', preprocessingData);
    }

    // 获取待审核的会话列表
    async getPendingReviewSessions() {
        return this.get('/admin/review/api/pending-sessions');
    }

    // 获取所有会话列表（支持状态过滤）
    async getAllSessions(status = null) {
        const url = status ? `/admin/review/api/sessions?status=${encodeURIComponent(status)}` : '/admin/review/api/sessions';
        return this.get(url);
    }

    // 获取会话详细信息
    async getSessionDetails(courseId) {
        return this.get(`/speech/session/${courseId}/details`);
    }

    // 获取待审核会话详情
    async getPendingSessionByCourseId(courseId) {
        return this.get(`/admin/review/api/pending-sessions/${courseId}`);
    }

    // 管理员高级审核会话
    async adminReviewSession(courseId, reviewData) {
        return this.post(`/admin/review/api/sessions/${courseId}/admin-review`, reviewData);
    }

    // 执行批量语音合成
    async executeBulkSynthesis(courseId) {
        return this.post(`/admin/review/api/sessions/${courseId}/synthesize`);
    }

    // 根据状态获取会话列表
    async getSessionsByStatus(status) {
        return this.get(`/speech/sessions?status=${encodeURIComponent(status)}`);
    }
}

// 创建全局API实例
window.api = new API();