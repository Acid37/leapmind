// 认证管理
class AuthManager {
    constructor() {
        this.loginForm = document.getElementById('loginForm');
        this.loginError = document.getElementById('loginError');
        this.loginPage = document.getElementById('loginPage');
        this.adminPage = document.getElementById('adminPage');
        this.adminUsername = document.getElementById('adminUsername');
        this.logoutBtn = document.getElementById('logoutBtn');

        this.init();
    }

    init() {
        // 绑定登录表单事件
        this.loginForm.addEventListener('submit', (e) => this.handleLogin(e));

        // 绑定退出登录事件
        this.logoutBtn.addEventListener('click', () => this.handleLogout());

        // 检查是否已登录
        this.checkAuthStatus();
    }

    // 检查认证状态
    checkAuthStatus() {
        const token = localStorage.getItem('adminToken');
        const username = localStorage.getItem('adminUsername');

        console.log('检查认证状态:', { token: token ? 'Present' : 'Missing', username });

        if (token && username) {
            // 确保API实例有正确的token
            api.setToken(token);
            console.log('Token已设置到API实例');
            this.showAdminPage(username);
        } else {
            console.log('未找到有效的认证信息，显示登录页面');
            this.showLoginPage();
        }
    }

    // 处理登录
    async handleLogin(e) {
        e.preventDefault();

        const username = document.getElementById('username').value;
        const password = document.getElementById('password').value;

        this.clearError();
        this.setLoading(true);

        try {
            console.log('Attempting login with:', { username, password: '***' });
            const response = await api.login(username, password);
            console.log('Login response:', response);

            // 处理不同的响应格式
            let token, userInfo;

            if (response.success && response.data) {
                // 标准格式: { success: true, data: { token, userInfo } }
                token = response.data.token;
                userInfo = response.data.userInfo;
            } else if (response.token && response.userInfo) {
                // 直接格式: { token, userInfo }
                token = response.token;
                userInfo = response.userInfo;
            } else if (response.data && response.data.token) {
                // 嵌套格式: { data: { token, userInfo } }
                token = response.data.token;
                userInfo = response.data.userInfo || response.data;
            } else {
                console.error('Unexpected response format:', response);
                this.showError('登录响应格式错误');
                return;
            }

            console.log('Extracted token:', token ? 'Present' : 'Missing');
            console.log('Extracted userInfo:', userInfo);

            if (!token) {
                this.showError('未收到认证令牌');
                return;
            }

            if (!userInfo || !userInfo.username) {
                this.showError('未收到用户信息');
                return;
            }

            // 验证是否为管理员
            if (this.isAdmin(userInfo)) {
                // 保存认证信息
                api.setToken(token);
                localStorage.setItem('adminUsername', userInfo.username);
                localStorage.setItem('adminUserInfo', JSON.stringify(userInfo));

                console.log('Login successful, showing admin page');
                this.showAdminPage(userInfo.username);
            } else {
                console.log('User is not admin:', userInfo);
                this.showError(`用户 "${userInfo.username}" 没有管理员权限，身份标识: ${userInfo.identify || '未知'}`);
            }
        } catch (error) {
            console.error('Login error:', error);
            this.showError(error.message || '登录失败，请稍后重试');
        } finally {
            this.setLoading(false);
        }
    }

    // 验证是否为管理员 - 基于登录返回的identify字段
    isAdmin(userInfo) {
        // 如果传入的是字符串，说明是旧的调用方式，返回false
        if (typeof userInfo === 'string') {
            console.log('使用旧的用户名验证方式，请更新为使用userInfo对象');
            return false;
        }

        // 检查identify字段来判断是否为管理员
        const identify = userInfo?.identify;
        const isAdminUser = identify === 'admin' || identify === 'administrator';

        console.log(`检查用户身份:`, {
            username: userInfo?.username,
            identify: identify,
            isAdmin: isAdminUser
        });

        return isAdminUser;
    }

    // 处理退出登录
    handleLogout() {
        if (confirm('确定要退出登录吗？')) {
            api.clearToken();
            localStorage.removeItem('adminUsername');
            localStorage.removeItem('adminUserInfo');
            this.showLoginPage();
        }
    }

    // 显示登录页面
    showLoginPage() {
        this.loginPage.classList.add('active');
        this.adminPage.classList.remove('active');
        this.clearForm();
        this.clearError();
    }

    // 显示管理后台页面
    showAdminPage(username) {
        this.loginPage.classList.remove('active');
        this.adminPage.classList.add('active');
        this.adminUsername.textContent = `欢迎，${username}`;

        // Token状态不再显示在界面上，仅用于内部认证

        // 触发页面初始化
        if (window.mainApp) {
            window.mainApp.init();
        }
    }

    // Token状态管理（内部使用，不显示在界面）
    updateTokenStatus() {
        // Token状态仅用于内部验证，不再显示在用户界面
        const token = localStorage.getItem('adminToken');
        console.log('Token状态:', token ? '已设置' : '未设置');
    }

    // 显示错误信息
    showError(message) {
        this.loginError.textContent = message;
        this.loginError.style.display = 'block';
    }

    // 清除错误信息
    clearError() {
        this.loginError.textContent = '';
        this.loginError.style.display = 'none';
    }

    // 清除表单
    clearForm() {
        this.loginForm.reset();
    }

    // 设置加载状态
    setLoading(loading) {
        const submitBtn = this.loginForm.querySelector('button[type="submit"]');

        if (loading) {
            submitBtn.disabled = true;
            submitBtn.innerHTML = '<span class="loading"></span> 登录中...';
        } else {
            submitBtn.disabled = false;
            submitBtn.innerHTML = '登录';
        }
    }
}

// 初始化认证管理器
document.addEventListener('DOMContentLoaded', () => {
    window.authManager = new AuthManager();
});