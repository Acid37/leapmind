// 用户管理模块
class UserManager {
    constructor() {
        this.usersTable = document.getElementById('usersTable').querySelector('tbody');
        this.addUserBtn = document.getElementById('addUserBtn');
        this.searchUsersBtn = document.getElementById('searchUsersBtn');
        this.clearUserSearchBtn = document.getElementById('clearUserSearchBtn');
        this.userSearchName = document.getElementById('userSearchName');
        this.userSearchStage = document.getElementById('userSearchStage');
        
        this.users = [];
        this.init();
    }

    init() {
        // 绑定事件
        this.addUserBtn.addEventListener('click', () => this.showAddUserModal());
        this.searchUsersBtn.addEventListener('click', () => this.searchUsers());
        this.clearUserSearchBtn.addEventListener('click', () => this.clearSearch());
        
        // 加载用户列表
        this.loadUsers();
    }

    // 加载用户列表
    async loadUsers() {
        try {
            console.log('开始加载用户列表...');
            const response = await api.getUsers();
            console.log('用户列表响应:', response);
            
            if (response.success) {
                this.users = response.data || [];
                console.log('用户数据:', this.users);
                this.renderUsers(this.users);
            } else {
                console.error('加载用户失败:', response.message);
                this.showMessage(response.message || '加载用户列表失败', 'error');
            }
        } catch (error) {
            console.error('Failed to load users:', error);
            this.showMessage(error.message || '加载用户列表失败', 'error');
        }
    }

    // 搜索用户
    async searchUsers() {
        const name = this.userSearchName.value.trim();
        const stage = this.userSearchStage.value;

        try {
            let users = [];
            
            if (name) {
                const response = await api.getUserByName(name);
                if (response.success && response.data) {
                    users = [response.data];
                }
            } else if (stage) {
                const response = await api.getUsersByStage(stage);
                if (response.success) {
                    users = response.data || [];
                }
            } else {
                // 如果没有搜索条件，加载所有用户
                await this.loadUsers();
                return;
            }
            
            this.renderUsers(users);
        } catch (error) {
            console.error('Search failed:', error);
            this.showMessage('搜索失败', 'error');
        }
    }

    // 清除搜索
    clearSearch() {
        this.userSearchName.value = '';
        this.userSearchStage.value = '';
        this.loadUsers();
    }

    // 渲染用户列表
    renderUsers(users) {
        if (!users || users.length === 0) {
            this.usersTable.innerHTML = `
                <tr>
                    <td colspan="8" class="empty-state">
                        <h3>暂无用户数据</h3>
                        <p>点击"添加用户"按钮创建新用户</p>
                    </td>
                </tr>
            `;
            return;
        }

        this.usersTable.innerHTML = users.map(user => `
            <tr>
                <td>${user.id}</td>
                <td>${user.username}</td>
                <td>${user.studentName || '-'}</td>
                <td>${this.getGradeDisplay(user.grade)}</td>
                <td>${user.email || '-'}</td>
                <td>${user.phone || '-'}</td>
                <td>
                    <span class="status-badge ${user.status === 1 ? 'status-active' : 'status-inactive'}">
                        ${user.status === 1 ? '正常' : '禁用'}
                    </span>
                </td>
                <td class="actions">
                    <button class="btn btn-warning" onclick="userManager.editUser(${user.id})">编辑</button>
                    <button class="btn btn-danger" onclick="userManager.deleteUser(${user.id})">删除</button>
                </td>
            </tr>
        `).join('');
    }

    // 获取年级显示名称
    getGradeDisplay(grade) {
        const gradeMap = {
            'GRADE_1': '小学一年级',
            'GRADE_2': '小学二年级',
            'GRADE_3': '小学三年级',
            'GRADE_4': '小学四年级',
            'GRADE_5': '小学五年级',
            'GRADE_6': '小学六年级',
            'GRADE_7': '初一',
            'GRADE_8': '初二',
            'GRADE_9': '初三'
        };
        return gradeMap[grade] || grade;
    }

    // 显示添加用户模态框
    showAddUserModal() {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <form id="addUserForm">
                <div class="form-row">
                    <div class="form-group">
                        <label for="addUsername">用户名 *</label>
                        <input type="text" id="addUsername" name="username" required>
                    </div>
                    <div class="form-group">
                        <label for="addPassword">密码 *</label>
                        <input type="password" id="addPassword" name="password" required>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label for="addStudentName">学生姓名 *</label>
                        <input type="text" id="addStudentName" name="studentName" required>
                    </div>
                    <div class="form-group">
                        <label for="addGrade">年级 *</label>
                        <select id="addGrade" name="grade" required>
                            <option value="">请选择年级</option>
                            <option value="GRADE_1">小学一年级</option>
                            <option value="GRADE_2">小学二年级</option>
                            <option value="GRADE_3">小学三年级</option>
                            <option value="GRADE_4">小学四年级</option>
                            <option value="GRADE_5">小学五年级</option>
                            <option value="GRADE_6">小学六年级</option>
                            <option value="GRADE_7">初一</option>
                            <option value="GRADE_8">初二</option>
                            <option value="GRADE_9">初三</option>
                        </select>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label for="addEmail">邮箱</label>
                        <input type="email" id="addEmail" name="email">
                    </div>
                    <div class="form-group">
                        <label for="addPhone">手机号</label>
                        <input type="tel" id="addPhone" name="phone">
                    </div>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="submit" class="btn btn-primary">创建用户</button>
                </div>
            </form>
        `;

        document.getElementById('modalTitle').textContent = '添加用户';
        window.modal.show();

        // 绑定表单提交事件
        document.getElementById('addUserForm').addEventListener('submit', (e) => this.handleAddUser(e));
    }

    // 处理添加用户
    async handleAddUser(e) {
        e.preventDefault();
        
        const formData = new FormData(e.target);
        const userData = Object.fromEntries(formData.entries());

        try {
            const response = await api.createUser(userData);
            if (response.success) {
                this.showMessage('用户创建成功', 'success');
                window.modal.close();
                this.loadUsers();
            }
        } catch (error) {
            console.error('Failed to create user:', error);
            this.showMessage(error.message || '创建用户失败', 'error');
        }
    }

    // 编辑用户
    async editUser(id) {
        try {
            const response = await api.getUserById(id);
            if (response.success && response.data) {
                this.showEditUserModal(response.data);
            }
        } catch (error) {
            console.error('Failed to get user:', error);
            this.showMessage('获取用户信息失败', 'error');
        }
    }

    // 显示编辑用户模态框
    showEditUserModal(user) {
        const modalBody = document.getElementById('modalBody');
        modalBody.innerHTML = `
            <form id="editUserForm">
                <input type="hidden" name="id" value="${user.id}">
                <div class="form-row">
                    <div class="form-group">
                        <label for="editUsername">用户名</label>
                        <input type="text" id="editUsername" name="username" value="${user.username}" readonly>
                    </div>
                    <div class="form-group">
                        <label for="editStudentName">学生姓名</label>
                        <input type="text" id="editStudentName" name="studentName" value="${user.studentName || ''}">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label for="editGrade">年级</label>
                        <select id="editGrade" name="grade">
                            <option value="">请选择年级</option>
                            <option value="GRADE_1" ${user.grade === 'GRADE_1' ? 'selected' : ''}>小学一年级</option>
                            <option value="GRADE_2" ${user.grade === 'GRADE_2' ? 'selected' : ''}>小学二年级</option>
                            <option value="GRADE_3" ${user.grade === 'GRADE_3' ? 'selected' : ''}>小学三年级</option>
                            <option value="GRADE_4" ${user.grade === 'GRADE_4' ? 'selected' : ''}>小学四年级</option>
                            <option value="GRADE_5" ${user.grade === 'GRADE_5' ? 'selected' : ''}>小学五年级</option>
                            <option value="GRADE_6" ${user.grade === 'GRADE_6' ? 'selected' : ''}>小学六年级</option>
                            <option value="GRADE_7" ${user.grade === 'GRADE_7' ? 'selected' : ''}>初一</option>
                            <option value="GRADE_8" ${user.grade === 'GRADE_8' ? 'selected' : ''}>初二</option>
                            <option value="GRADE_9" ${user.grade === 'GRADE_9' ? 'selected' : ''}>初三</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="editEmail">邮箱</label>
                        <input type="email" id="editEmail" name="email" value="${user.email || ''}">
                    </div>
                </div>
                <div class="form-group">
                    <label for="editPhone">手机号</label>
                    <input type="tel" id="editPhone" name="phone" value="${user.phone || ''}">
                </div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="window.modal.close()">取消</button>
                    <button type="submit" class="btn btn-primary">更新用户</button>
                </div>
            </form>
        `;

        document.getElementById('modalTitle').textContent = '编辑用户';
        window.modal.show();

        // 绑定表单提交事件
        document.getElementById('editUserForm').addEventListener('submit', (e) => this.handleEditUser(e));
    }

    // 处理编辑用户
    async handleEditUser(e) {
        e.preventDefault();
        
        const formData = new FormData(e.target);
        const userData = Object.fromEntries(formData.entries());
        const id = userData.id;
        delete userData.id;
        delete userData.username; // 不允许修改用户名

        try {
            const response = await api.updateUser(id, userData);
            if (response.success) {
                this.showMessage('用户更新成功', 'success');
                window.modal.close();
                this.loadUsers();
            }
        } catch (error) {
            console.error('Failed to update user:', error);
            this.showMessage(error.message || '更新用户失败', 'error');
        }
    }

    // 删除用户
    async deleteUser(id) {
        if (!confirm('确定要删除这个用户吗？此操作不可恢复。')) {
            return;
        }

        try {
            const response = await api.deleteUser(id);
            if (response.success) {
                this.showMessage('用户删除成功', 'success');
                this.loadUsers();
            }
        } catch (error) {
            console.error('Failed to delete user:', error);
            this.showMessage(error.message || '删除用户失败', 'error');
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
window.UserManager = UserManager;