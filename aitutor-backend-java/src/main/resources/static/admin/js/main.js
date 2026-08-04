// 主应用程序
class MainApp {
    constructor() {
        this.currentTab = 'users';
        this.managers = {};
        this.modal = null;
        
        this.init();
    }

    init() {
        // 初始化模态框
        this.initModal();
        
        // 初始化导航
        this.initNavigation();
        
        // 初始化管理器
        this.initManagers();
    }

    // 初始化模态框
    initModal() {
        const modal = document.getElementById('modal');
        const closeBtn = document.getElementById('closeModal');
        
        this.modal = {
            element: modal,
            show: () => {
                modal.classList.add('active');
                document.body.style.overflow = 'hidden';
            },
            close: () => {
                modal.classList.remove('active');
                document.body.style.overflow = '';
            }
        };

        // 绑定关闭事件
        closeBtn.addEventListener('click', () => this.modal.close());
        
        // 点击背景关闭
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                this.modal.close();
            }
        });

        // ESC键关闭
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && modal.classList.contains('active')) {
                this.modal.close();
            }
        });

        // 导出到全局
        window.modal = this.modal;
        
        // 导出通知函数到全局
        window.showNotification = this.showNotification.bind(this);
    }

    // 初始化导航
    initNavigation() {
        const navLinks = document.querySelectorAll('.nav-link');
        const tabContents = document.querySelectorAll('.tab-content');

        navLinks.forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                
                const tabName = link.dataset.tab;
                this.switchTab(tabName);
            });
        });
    }

    // 切换标签页
    switchTab(tabName) {
        // 更新导航状态
        document.querySelectorAll('.nav-link').forEach(link => {
            link.classList.remove('active');
        });
        document.querySelector(`[data-tab="${tabName}"]`).classList.add('active');

        // 更新内容区域
        document.querySelectorAll('.tab-content').forEach(content => {
            content.classList.remove('active');
        });
        document.getElementById(`${tabName}Tab`).classList.add('active');

        this.currentTab = tabName;

        // 如果管理器已初始化，刷新数据
        if (this.managers[tabName]) {
            this.refreshTabData(tabName);
        }
    }

    // 刷新标签页数据
    refreshTabData(tabName) {
        switch (tabName) {
            case 'users':
                if (this.managers.users) {
                    this.managers.users.loadUsers();
                }
                break;
            case 'stages':
                if (this.managers.stages) {
                    this.managers.stages.loadStageData();
                }
                break;
            case 'courses':
                if (this.managers.courses) {
                    this.managers.courses.loadCourses();
                }
                break;
            case 'outlines':
                if (this.managers.outlines) {
                    this.managers.outlines.loadOutlines();
                }
                break;
            case 'ppt':
                if (this.managers.ppt) {
                    this.managers.ppt.loadSlides();
                }
                break;
            case 'speech':
                if (this.managers.speech) {
                    this.managers.speech.loadSpeechTasks();
                }
                break;
            case 'session-review':
                if (this.managers['session-review']) {
                    this.managers['session-review'].loadSessions();
                }
                break;
        }
        
        // 发送tab切换事件
        document.dispatchEvent(new CustomEvent('tabChanged', {
            detail: { tabId: tabName }
        }));
    }

    // 初始化管理器
    initManagers() {
        // 检查是否在管理后台页面
        if (!document.getElementById('adminPage').classList.contains('active')) {
            return;
        }

        // 确保API有正确的token
        const token = localStorage.getItem('adminToken');
        if (token) {
            api.setToken(token);
            console.log('Token已设置到API实例，准备初始化管理器');
        } else {
            console.error('未找到token，无法初始化管理器');
            if (window.authManager) {
                window.authManager.showLoginPage();
            }
            return;
        }

        try {
            // 初始化用户管理器
            this.managers.users = new UserManager();
            window.userManager = this.managers.users;

            // 初始化阶段管理器
            this.managers.stages = new StageManager();
            window.stageManager = this.managers.stages;

            // 初始化课程管理器
            this.managers.courses = new CourseManager();
            window.courseManager = this.managers.courses;

            // 初始化新的课程管理模块
            if (window.courseManagement) {
                window.courseManagement.init();
                console.log('课程管理模块初始化完成');
            }

            // 初始化大纲管理器
            this.managers.outlines = new OutlineManager();
            window.outlineManager = this.managers.outlines;

            // 初始化PPT管理器
            this.managers.ppt = window.pptManager;

            // 初始化语音管理器
            this.managers.speech = new SpeechManager();
            window.speechManager = this.managers.speech;

            // 初始化会话审查管理器
            this.managers['session-review'] = new SessionReviewManager();
            window.sessionReviewManager = this.managers['session-review'];

            console.log('All managers initialized successfully');
        } catch (error) {
            console.error('Failed to initialize managers:', error);
        }
    }

    // 显示通知
    showNotification(message, type = 'info', duration = 3000) {
        // 创建通知元素
        const notification = document.createElement('div');
        notification.className = `notification notification-${type}`;
        notification.innerHTML = `
            <span class="notification-message">${message}</span>
            <button class="notification-close">&times;</button>
        `;

        // 添加样式
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 15px 20px;
            background: ${type === 'error' ? '#dc3545' : type === 'success' ? '#28a745' : '#17a2b8'};
            color: white;
            border-radius: 4px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
            z-index: 10000;
            display: flex;
            align-items: center;
            gap: 10px;
            max-width: 400px;
            animation: slideIn 0.3s ease-out;
        `;

        // 添加动画样式
        if (!document.getElementById('notification-styles')) {
            const style = document.createElement('style');
            style.id = 'notification-styles';
            style.textContent = `
                @keyframes slideIn {
                    from { transform: translateX(100%); opacity: 0; }
                    to { transform: translateX(0); opacity: 1; }
                }
                @keyframes slideOut {
                    from { transform: translateX(0); opacity: 1; }
                    to { transform: translateX(100%); opacity: 0; }
                }
                .notification-close {
                    background: none;
                    border: none;
                    color: white;
                    font-size: 18px;
                    cursor: pointer;
                    padding: 0;
                    margin-left: auto;
                }
            `;
            document.head.appendChild(style);
        }

        // 添加到页面
        document.body.appendChild(notification);

        // 绑定关闭事件
        const closeBtn = notification.querySelector('.notification-close');
        const closeNotification = () => {
            notification.style.animation = 'slideOut 0.3s ease-in';
            setTimeout(() => {
                if (notification.parentNode) {
                    notification.parentNode.removeChild(notification);
                }
            }, 300);
        };

        closeBtn.addEventListener('click', closeNotification);

        // 自动关闭
        if (duration > 0) {
            setTimeout(closeNotification, duration);
        }
    }

    // 确认对话框
    confirm(message, title = '确认') {
        return new Promise((resolve) => {
            const modalBody = document.getElementById('modalBody');
            modalBody.innerHTML = `
                <div class="confirm-dialog">
                    <p>${message}</p>
                    <div class="form-actions">
                        <button type="button" class="btn btn-secondary" id="confirmCancel">取消</button>
                        <button type="button" class="btn btn-danger" id="confirmOk">确认</button>
                    </div>
                </div>
            `;

            document.getElementById('modalTitle').textContent = title;
            this.modal.show();

            // 绑定事件
            document.getElementById('confirmCancel').addEventListener('click', () => {
                this.modal.close();
                resolve(false);
            });

            document.getElementById('confirmOk').addEventListener('click', () => {
                this.modal.close();
                resolve(true);
            });
        });
    }
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', () => {
    window.mainApp = new MainApp();
});

// 导出一些工具函数
window.utils = {
    formatDate: (date) => {
        if (!date) return '-';
        return new Date(date).toLocaleString('zh-CN');
    },
    
    formatNumber: (num) => {
        if (num === null || num === undefined) return '0';
        return num.toLocaleString();
    },
    
    truncateText: (text, length = 50) => {
        if (!text) return '-';
        return text.length > length ? text.substring(0, length) + '...' : text;
    },
    
    debounce: (func, wait) => {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }
};