// Demo Data for Modern UI Preview
class DemoData {
    constructor() {
        this.users = [
            {
                id: 1,
                username: 'student001',
                studentName: '张三',
                grade: '小学三年级',
                email: 'zhangsan@example.com',
                phone: '138****1234',
                status: 'active'
            },
            {
                id: 2,
                username: 'student002',
                studentName: '李四',
                grade: '初中一年级',
                email: 'lisi@example.com',
                phone: '139****5678',
                status: 'active'
            },
            {
                id: 3,
                username: 'student003',
                studentName: '王五',
                grade: '高中二年级',
                email: 'wangwu@example.com',
                phone: '137****9012',
                status: 'inactive'
            }
        ];

        this.stages = [
            {
                code: 'PRIMARY',
                name: '小学阶段',
                grades: ['一年级', '二年级', '三年级', '四年级', '五年级', '六年级'],
                userCount: 456,
                status: 'active'
            },
            {
                code: 'JUNIOR',
                name: '初中阶段',
                grades: ['初一', '初二', '初三'],
                userCount: 324,
                status: 'active'
            },
            {
                code: 'SENIOR',
                name: '高中阶段',
                grades: ['高一', '高二', '高三'],
                userCount: 234,
                status: 'active'
            }
        ];

        this.courses = [
            {
                id: 1,
                subject: '数学',
                stage: '小学',
                grade: '三年级',
                semester: 'SEMESTER_1',
                chapter: 1,
                chapterTitle: '数与代数',
                section: 1,
                sectionTitle: '整数的认识'
            },
            {
                id: 2,
                subject: '语文',
                stage: '初中',
                grade: '一年级',
                semester: 'SEMESTER_2',
                chapter: 2,
                chapterTitle: '现代文阅读',
                section: 3,
                sectionTitle: '记叙文阅读技巧'
            }
        ];

        this.statistics = {
            totalUsers: 1234,
            totalStages: 8,
            totalCourses: 156,
            growthRate: 15
        };
    }

    // Simulate API calls with delays
    async getUsers() {
        await this.delay(500);
        return this.users;
    }

    async getStages() {
        await this.delay(300);
        return this.stages;
    }

    async getCourses() {
        await this.delay(400);
        return this.courses;
    }

    async getStatistics() {
        await this.delay(200);
        return this.statistics;
    }

    async createUser(userData) {
        await this.delay(800);
        const newUser = {
            id: this.users.length + 1,
            ...userData,
            status: 'active'
        };
        this.users.push(newUser);
        return newUser;
    }

    async updateUser(id, userData) {
        await this.delay(600);
        const index = this.users.findIndex(user => user.id === id);
        if (index !== -1) {
            this.users[index] = { ...this.users[index], ...userData };
            return this.users[index];
        }
        throw new Error('User not found');
    }

    async deleteUser(id) {
        await this.delay(400);
        const index = this.users.findIndex(user => user.id === id);
        if (index !== -1) {
            this.users.splice(index, 1);
            return true;
        }
        throw new Error('User not found');
    }

    // Utility method to simulate network delay
    delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    // Generate chart data
    getUserGrowthData() {
        return {
            labels: ['1月', '2月', '3月', '4月', '5月', '6月'],
            datasets: [{
                label: '用户增长',
                data: [100, 150, 200, 280, 350, 420],
                borderColor: '#1e40af',
                backgroundColor: 'rgba(30, 64, 175, 0.1)',
                tension: 0.4
            }]
        };
    }

    getStageDistributionData() {
        return {
            labels: ['小学', '初中', '高中', '其他'],
            datasets: [{
                data: [456, 324, 234, 120],
                backgroundColor: [
                    '#1e40af',
                    '#1d4ed8',
                    '#0ea5e9',
                    '#10b981'
                ]
            }]
        };
    }

    getGradeStatisticsData() {
        return {
            labels: ['一年级', '二年级', '三年级', '四年级', '五年级', '六年级'],
            datasets: [{
                label: '学生人数',
                data: [65, 78, 85, 72, 69, 88],
                backgroundColor: 'rgba(30, 64, 175, 0.8)'
            }]
        };
    }
}

// Initialize demo data
window.demoData = new DemoData();

// Demo mode toggle
window.enableDemoMode = () => {
    console.log('Demo mode enabled - using sample data');
    
    // Override API calls with demo data
    if (window.api) {
        const originalGetUsers = window.api.getUsers;
        window.api.getUsers = () => window.demoData.getUsers();
        
        const originalGetStages = window.api.getStages;
        window.api.getStages = () => window.demoData.getStages();
        
        const originalGetCourses = window.api.getCourses;
        window.api.getCourses = () => window.demoData.getCourses();
    }
    
    // Populate demo data
    populateDemoData();
};

function populateDemoData() {
    // Update statistics
    const stats = window.demoData.statistics;
    document.getElementById('totalUsers').textContent = stats.totalUsers.toLocaleString();
    document.getElementById('totalStages').textContent = stats.totalStages;
    document.getElementById('totalCourses').textContent = stats.totalCourses;
    document.getElementById('growthRate').textContent = `+${stats.growthRate}%`;
    
    // Populate users table
    populateUsersTable();
    
    // Populate stages table
    populateStagesTable();
    
    // Populate courses table
    populateCoursesTable();
    
    // Initialize demo charts
    initDemoCharts();
}

function populateUsersTable() {
    const tbody = document.querySelector('#usersTable tbody');
    if (!tbody) return;
    
    tbody.innerHTML = '';
    
    window.demoData.users.forEach(user => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td><input type="checkbox" value="${user.id}"></td>
            <td>${user.id}</td>
            <td>${user.username}</td>
            <td>${user.studentName}</td>
            <td>${user.grade}</td>
            <td>${user.email}</td>
            <td>${user.phone}</td>
            <td><span class="status-badge status-${user.status}">${user.status === 'active' ? '活跃' : '非活跃'}</span></td>
            <td>
                <div class="action-buttons">
                    <button class="btn btn-sm btn-primary" onclick="editUser(${user.id})">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn btn-sm btn-danger" onclick="deleteUser(${user.id})">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </td>
        `;
        tbody.appendChild(row);
    });
}

function populateStagesTable() {
    const tbody = document.querySelector('#stagesTable tbody');
    if (!tbody) return;
    
    tbody.innerHTML = '';
    
    window.demoData.stages.forEach(stage => {
        const row = document.createElement('tr');
        const gradesPreview = stage.grades.slice(0, 3).map(grade => 
            `<span class="grade-tag">${grade}</span>`
        ).join('');
        const moreGrades = stage.grades.length > 3 ? 
            `<span class="grade-more">+${stage.grades.length - 3}</span>` : '';
        
        row.innerHTML = `
            <td><input type="checkbox" value="${stage.code}"></td>
            <td><span class="code-badge">${stage.code}</span></td>
            <td><strong>${stage.name}</strong></td>
            <td>
                <div class="grades-preview">
                    ${gradesPreview}
                    ${moreGrades}
                </div>
            </td>
            <td>
                <div class="user-count">
                    <span class="count-number">${stage.userCount}</span>
                    <span class="count-label">人</span>
                </div>
            </td>
            <td><span class="status-badge status-${stage.status}">${stage.status === 'active' ? '活跃' : '非活跃'}</span></td>
            <td>
                <div class="action-buttons">
                    <button class="btn btn-sm btn-primary" onclick="editStage('${stage.code}')">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn btn-sm btn-info" onclick="viewStageDetails('${stage.code}')">
                        <i class="fas fa-eye"></i>
                    </button>
                    <button class="btn btn-sm btn-danger" onclick="deleteStage('${stage.code}')">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </td>
        `;
        tbody.appendChild(row);
    });
}

function populateCoursesTable() {
    const tbody = document.querySelector('#coursesTable tbody');
    if (!tbody) return;
    
    tbody.innerHTML = '';
    
    window.demoData.courses.forEach(course => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${course.id}</td>
            <td>${course.subject}</td>
            <td><span class="stage-tag">${course.stage}</span></td>
            <td>${course.grade}</td>
            <td>${course.semester}</td>
            <td>${course.chapter}</td>
            <td>${course.chapterTitle}</td>
            <td>${course.section}</td>
            <td>${course.sectionTitle}</td>
            <td>
                <div class="action-buttons">
                    <button class="btn btn-sm btn-primary" onclick="editCourse(${course.id})">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn btn-sm btn-info" onclick="viewCourse(${course.id})">
                        <i class="fas fa-eye"></i>
                    </button>
                    <button class="btn btn-sm btn-danger" onclick="deleteCourse(${course.id})">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </td>
        `;
        tbody.appendChild(row);
    });
}

function initDemoCharts() {
    // User Growth Chart
    const userGrowthCtx = document.getElementById('userGrowthChart');
    if (userGrowthCtx && window.Chart) {
        new Chart(userGrowthCtx, {
            type: 'line',
            data: window.demoData.getUserGrowthData(),
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true
                    }
                }
            }
        });
    }
    
    // Stage Distribution Chart
    const stageDistCtx = document.getElementById('stageDistributionChart');
    if (stageDistCtx && window.Chart) {
        new Chart(stageDistCtx, {
            type: 'pie',
            data: window.demoData.getStageDistributionData(),
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'bottom'
                    }
                }
            }
        });
    }
    
    // Grade Statistics Chart
    const gradeStatsCtx = document.getElementById('gradeStatisticsChart');
    if (gradeStatsCtx && window.Chart) {
        new Chart(gradeStatsCtx, {
            type: 'bar',
            data: window.demoData.getGradeStatisticsData(),
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true
                    }
                }
            }
        });
    }
}

// Auto-enable demo mode if no real API is available
document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => {
        if (!window.api || typeof window.api.getUsers !== 'function') {
            console.log('No API detected, enabling demo mode');
            window.enableDemoMode();
        }
    }, 1000);
});