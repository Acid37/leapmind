// Global Master Templates Management JavaScript

async function updateTagFilter() {
    const tagFilter = document.getElementById('tagFilter');

    try {
        // Get all templates to extract tags (without pagination)
        const response = await fetch('/api/global-master-templates/?active_only=true&page_size=1000');
        if (response.ok) {
            const data = await response.json();
            const allTemplates = data.templates || [];

            const allTags = new Set();
            allTemplates.forEach(template => {
                template.tags.forEach(tag => allTags.add(tag));
            });

            // Clear existing options except "所有标签"
            tagFilter.innerHTML = '<option value="">所有标签</option>';

            // Add tag options
            Array.from(allTags).sort().forEach(tag => {
                const option = document.createElement('option');
                option.value = tag;
                option.textContent = tag;
                tagFilter.appendChild(option);
            });
        }
    } catch (error) {
        console.warn('Failed to load tags for filter:', error);
    }
}

// filterTemplates function removed - now using server-side pagination

function showLoading(show) {
    document.getElementById('loadingIndicator').style.display = show ? 'block' : 'none';
    document.getElementById('templatesGrid').style.display = show ? 'none' : 'grid';
}

function openTemplateModal(templateId = null) {
    editingTemplateId = templateId;
    const modal = document.getElementById('templateModal');
    const title = document.getElementById('modalTitle');
    const form = document.getElementById('templateForm');
    
    if (templateId) {
        title.textContent = '编辑母版';
        loadTemplateForEdit(templateId);
    } else {
        title.textContent = '新建母版';
        form.reset();
    }
    
    modal.style.display = 'flex';
}

function closeTemplateModal() {
    document.getElementById('templateModal').style.display = 'none';
    editingTemplateId = null;
}

function openAIGenerationModal() {
    document.getElementById('aiGenerationModal').style.display = 'flex';
    document.getElementById('aiGenerationForm').reset();
}

function closeAIGenerationModal() {
    document.getElementById('aiGenerationModal').style.display = 'none';

    // 重置模态框状态
    document.getElementById('aiFormContainer').style.display = 'block';
    document.getElementById('aiGenerationProgress').style.display = 'none';
    document.getElementById('aiGenerationComplete').style.display = 'none';

    // 重置表单
    document.getElementById('aiGenerationForm').reset();
}

function closePreviewModal() {
    document.getElementById('previewModal').style.display = 'none';
}

async function loadTemplateForEdit(templateId) {
    try {
        const response = await fetch(`/api/global-master-templates/${templateId}`);
        if (!response.ok) {
            throw new Error('Failed to load template');
        }
        
        const template = await response.json();
        
        document.getElementById('templateName').value = template.template_name;
        document.getElementById('templateDescription').value = template.description;
        document.getElementById('templateTags').value = template.tags.join(', ');
        document.getElementById('isDefault').checked = template.is_default;
        document.getElementById('htmlTemplate').value = template.html_template;
    } catch (error) {
        console.error('Error loading template:', error);
        alert('加载模板失败: ' + error.message);
    }
}

async function handleTemplateSubmit(event) {
    event.preventDefault();
    
    const formData = new FormData(event.target);
    const templateData = {
        template_name: formData.get('template_name'),
        description: formData.get('description'),
        html_template: formData.get('html_template'),
        tags: formData.get('tags').split(',').map(tag => tag.trim()).filter(tag => tag),
        is_default: formData.get('is_default') === 'on'
    };
    
    try {
        let response;
        if (editingTemplateId) {
            // Update existing template
            response = await fetch(`/api/global-master-templates/${editingTemplateId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(templateData)
            });
        } else {
            // Create new template
            response = await fetch('/api/global-master-templates/', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(templateData)
            });
        }
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.detail || 'Failed to save template');
        }
        
        closeTemplateModal();
        loadTemplates(currentPage);
        alert(editingTemplateId ? '模板更新成功！' : '模板创建成功！');
    } catch (error) {
        console.error('Error saving template:', error);
        alert('保存模板失败: ' + error.message);
    }
}

async function handleAIGeneration(event) {
    event.preventDefault();

    const formData = new FormData(event.target);
    const requestData = {
        prompt: formData.get('prompt'),
        template_name: formData.get('template_name'),
        description: formData.get('description'),
        tags: formData.get('tags').split(',').map(tag => tag.trim()).filter(tag => tag)
    };

    try {
        // 切换到进度显示界面
        showAIGenerationProgress();

        // 开始流式生成
        await startStreamingGeneration(requestData);

    } catch (error) {
        console.error('Error generating template:', error);
        showAIGenerationError(error.message);
    }
}

function showAIGenerationProgress() {
    // 隐藏表单，显示进度
    document.getElementById('aiFormContainer').style.display = 'none';
    document.getElementById('aiGenerationProgress').style.display = 'block';
    document.getElementById('aiGenerationComplete').style.display = 'none';

    // 重置进度状态
    document.getElementById('statusText').textContent = '正在分析需求...';
    document.getElementById('aiResponseStream').innerHTML = '';
}

function showAIGenerationComplete() {
    // 隐藏进度，显示完成
    document.getElementById('aiGenerationProgress').style.display = 'none';
    document.getElementById('aiGenerationComplete').style.display = 'block';
}

function showAIGenerationError(errorMessage) {
    // 显示错误并返回表单
    document.getElementById('aiGenerationProgress').style.display = 'none';
    document.getElementById('aiFormContainer').style.display = 'block';
    alert('AI生成模板失败: ' + errorMessage);
}

async function startStreamingGeneration(requestData) {
    let generatedTemplateId = null;

    try {
        // 更新状态
        updateGenerationStatus('正在连接AI服务...');

        const response = await fetch('/api/global-master-templates/generate-stream', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(requestData)
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.detail || 'Failed to generate template');
        }

        // 更新状态
        updateGenerationStatus('AI正在思考设计方案...');

        // 处理流式响应
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const { done, value } = await reader.read();

            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop(); // 保留不完整的行

            for (const line of lines) {
                if (line.trim() === '') continue;

                try {
                    if (line.startsWith('data: ')) {
                        const data = JSON.parse(line.slice(6));
                        await handleStreamData(data);

                        if (data.type === 'complete' && data.template_id) {
                            generatedTemplateId = data.template_id;
                        }
                    }
                } catch (e) {
                    console.warn('Failed to parse stream data:', line, e);
                }
            }
        }

        // 生成完成
        updateGenerationStatus('模板生成完成！');
        showAIGenerationComplete();

        // 设置查看按钮的模板ID
        if (generatedTemplateId) {
            document.getElementById('viewGeneratedTemplateBtn').onclick = () => {
                closeAIGenerationModal();
                loadTemplates(1); // 回到第一页查看新生成的模板
                // 可以添加滚动到新模板的逻辑
            };
        }

    } catch (error) {
        console.error('Streaming generation error:', error);
        throw error;
    }
}

async function handleStreamData(data) {
    const responseStream = document.getElementById('aiResponseStream');

    switch (data.type) {
        case 'status':
            updateGenerationStatus(data.message);
            break;

        case 'thinking':
            // 显示AI思考过程
            appendToStream(data.content);
            break;

        case 'progress':
            updateGenerationStatus(data.message);
            if (data.content) {
                appendToStream(data.content);
            }
            break;

        case 'complete':
            updateGenerationStatus('模板生成完成！');
            appendToStream('\n\n✅ 模板已成功生成并保存！');
            break;

        case 'error':
            throw new Error(data.message);
    }
}

function updateGenerationStatus(message) {
    document.getElementById('statusText').textContent = message;
}

function appendToStream(content) {
    const responseStream = document.getElementById('aiResponseStream');

    // 移除之前的光标
    const existingCursor = responseStream.querySelector('.typing-cursor');
    if (existingCursor) {
        existingCursor.remove();
    }

    // 添加新内容
    const contentSpan = document.createElement('span');
    contentSpan.textContent = content;
    responseStream.appendChild(contentSpan);

    // 添加新的光标
    const cursor = document.createElement('span');
    cursor.className = 'typing-cursor';
    responseStream.appendChild(cursor);

    // 滚动到底部
    responseStream.scrollTop = responseStream.scrollHeight;
}

function previewTemplate() {
    const htmlContent = document.getElementById('htmlTemplate').value;
    if (!htmlContent.trim()) {
        alert('请先输入HTML模板代码');
        return;
    }
    
    showPreview(htmlContent);
}

// previewTemplateById 和 showPreview 函数已移至HTML文件中

// 这些函数已移至HTML文件中，以便onclick事件可以访问
// editTemplate, duplicateTemplate, setDefaultTemplate, deleteTemplate

// 导入模板功能
async function handleTemplateImport(event) {
    const file = event.target.files[0];
    if (!file) {
        return;
    }

    try {
        const fileContent = await readFileContent(file);
        let templateData;

        if (file.name.endsWith('.json')) {
            // JSON格式导入
            templateData = JSON.parse(fileContent);

            // 验证必要字段
            if (!templateData.template_name || !templateData.html_template) {
                throw new Error('JSON文件格式不正确，缺少必要字段 template_name 或 html_template');
            }
        } else if (file.name.endsWith('.html')) {
            // HTML文件导入
            const fileName = file.name.replace('.html', '');
            templateData = {
                template_name: fileName,
                description: `从文件 ${file.name} 导入`,
                html_template: fileContent,
                tags: ['导入'],
                is_default: false
            };
        } else {
            throw new Error('不支持的文件格式，请选择 .html 或 .json 文件');
        }

        // 确保标签是数组格式
        if (typeof templateData.tags === 'string') {
            templateData.tags = templateData.tags.split(',').map(tag => tag.trim()).filter(tag => tag);
        }
        if (!Array.isArray(templateData.tags)) {
            templateData.tags = [];
        }

        // 创建模板
        const response = await fetch('/api/global-master-templates/', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(templateData)
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.detail || 'Failed to import template');
        }

        // 清空文件输入
        event.target.value = '';

        // 重新加载模板列表
        loadTemplates(1); // 回到第一页查看新导入的模板
        alert('模板导入成功！');

    } catch (error) {
        console.error('Error importing template:', error);
        alert('导入模板失败: ' + error.message);
        // 清空文件输入
        event.target.value = '';
    }
}

// 读取文件内容
function readFileContent(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = (e) => resolve(e.target.result);
        reader.onerror = (e) => reject(new Error('文件读取失败'));
        reader.readAsText(file, 'UTF-8');
    });
}

// 导出模板功能已移至HTML文件中，以便onclick事件可以访问
