# 贡献指南 / Contributing Guide

[English](#english) | [中文](#中文)

---

## 中文

感谢您对 LandPPT 项目的关注！我们欢迎所有形式的贡献，包括但不限于：

### 🤝 贡献方式

- 🐛 **Bug 报告**：发现问题并报告
- 💡 **功能建议**：提出新功能想法
- 📝 **代码贡献**：修复bug或实现新功能
- 📚 **文档改进**：完善文档和示例
- 🌐 **翻译工作**：帮助翻译界面和文档
- 🧪 **测试用例**：编写和改进测试

### 🚀 开始贡献

#### 1. 准备开发环境

```bash
# Fork 并克隆仓库
git clone https://github.com/your-username/LandPPT.git
cd LandPPT

# 创建虚拟环境
python -m venv .venv
source .venv/bin/activate  # Linux/Mac
# 或
.venv\Scripts\activate     # Windows

# 安装依赖
pip install uv
uv pip install -e ".[dev]"

# 配置环境变量
cp .env.example .env
# 编辑 .env 文件配置必要的API密钥

# 初始化数据库
python setup_database.py

# 运行项目
python run.py
```

#### 2. 开发流程

1. **创建分支**
   ```bash
   git checkout -b feature/your-feature-name
   # 或
   git checkout -b fix/your-bug-fix
   ```

2. **编写代码**
   - 遵循现有的代码风格
   - 添加必要的注释和文档字符串
   - 确保代码通过所有测试

3. **运行测试**
   ```bash
   # 运行所有测试
   pytest

   # 运行特定测试
   pytest tests/test_specific.py

   # 生成覆盖率报告
   pytest --cov=src/landppt
   ```

4. **代码格式化**
   ```bash
   # 格式化代码
   black src/
   isort src/

   # 检查代码质量
   flake8 src/
   mypy src/
   ```

5. **提交更改**
   ```bash
   git add .
   git commit -m "feat: add new feature description"
   # 或
   git commit -m "fix: fix bug description"
   ```

6. **推送并创建PR**
   ```bash
   git push origin feature/your-feature-name
   ```
   然后在GitHub上创建Pull Request

### 📝 代码规范

#### 提交信息格式
使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式：

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

类型包括：
- `feat`: 新功能
- `fix`: Bug修复
- `docs`: 文档更新
- `style`: 代码格式化
- `refactor`: 代码重构
- `test`: 测试相关
- `chore`: 构建过程或辅助工具的变动

#### Python代码规范
- 遵循 [PEP 8](https://pep8.org/) 规范
- 使用类型提示 (Type Hints)
- 编写清晰的文档字符串
- 保持函数和类的单一职责

#### 示例代码风格
```python
from typing import Optional, Dict, Any
import logging

logger = logging.getLogger(__name__)

class PPTGenerator:
    """PPT生成器类
    
    负责根据输入内容生成PPT演示文稿
    """
    
    def __init__(self, config: Dict[str, Any]) -> None:
        """初始化PPT生成器
        
        Args:
            config: 配置字典
        """
        self.config = config
        
    async def generate_ppt(
        self, 
        content: str, 
        template_id: Optional[str] = None
    ) -> Dict[str, Any]:
        """生成PPT
        
        Args:
            content: 输入内容
            template_id: 模板ID，可选
            
        Returns:
            生成的PPT数据
            
        Raises:
            ValueError: 当输入内容为空时
        """
        if not content.strip():
            raise ValueError("内容不能为空")
            
        logger.info(f"开始生成PPT，内容长度: {len(content)}")
        
        # 实现逻辑...
        return {"status": "success", "data": {}}
```

### 🧪 测试指南

#### 编写测试
- 为新功能编写单元测试
- 确保测试覆盖率不低于80%
- 使用有意义的测试名称
- 测试边界条件和异常情况

#### 测试示例
```python
import pytest
from unittest.mock import Mock, patch
from src.landppt.core.ppt_generator import PPTGenerator

class TestPPTGenerator:
    """PPT生成器测试类"""
    
    @pytest.fixture
    def generator(self):
        """创建PPT生成器实例"""
        config = {"template_dir": "/tmp/templates"}
        return PPTGenerator(config)
    
    async def test_generate_ppt_success(self, generator):
        """测试成功生成PPT"""
        content = "这是测试内容"
        result = await generator.generate_ppt(content)
        
        assert result["status"] == "success"
        assert "data" in result
    
    async def test_generate_ppt_empty_content(self, generator):
        """测试空内容异常"""
        with pytest.raises(ValueError, match="内容不能为空"):
            await generator.generate_ppt("")
```

### 📋 Issue 和 PR 模板

#### Bug 报告
报告bug时请包含：
- 问题描述
- 复现步骤
- 期望行为
- 实际行为
- 环境信息（操作系统、Python版本等）
- 相关日志或截图

#### 功能请求
提出新功能时请包含：
- 功能描述
- 使用场景
- 预期收益
- 可能的实现方案

### 🎯 开发重点

当前项目重点关注以下方面：

1. **AI集成优化**
   - 支持更多AI提供商
   - 优化AI调用性能
   - 改进错误处理

2. **模板系统**
   - 丰富模板库
   - 改进模板编辑器
   - 支持自定义模板

3. **用户体验**
   - 优化Web界面
   - 改进响应速度
   - 增强错误提示

4. **文档和测试**
   - 完善API文档
   - 增加测试覆盖率
   - 改进用户指南

### 📞 联系方式

如有疑问，可以通过以下方式联系：
- 创建 [GitHub Issue](https://github.com/your-username/LandPPT/issues)
- 参与 [GitHub Discussions](https://github.com/your-username/LandPPT/discussions)

---

## English

Thank you for your interest in the LandPPT project! We welcome all forms of contributions, including but not limited to:

### 🤝 Ways to Contribute

- 🐛 **Bug Reports**: Find and report issues
- 💡 **Feature Suggestions**: Propose new feature ideas
- 📝 **Code Contributions**: Fix bugs or implement new features
- 📚 **Documentation**: Improve docs and examples
- 🌐 **Translation**: Help translate interface and documentation
- 🧪 **Testing**: Write and improve test cases

### 🚀 Getting Started

#### 1. Development Environment Setup

```bash
# Fork and clone the repository
git clone https://github.com/your-username/LandPPT.git
cd LandPPT

# Create virtual environment
python -m venv .venv
source .venv/bin/activate  # Linux/Mac
# or
.venv\Scripts\activate     # Windows

# Install dependencies
pip install uv
uv pip install -e ".[dev]"

# Configure environment variables
cp .env.example .env
# Edit .env file to configure necessary API keys

# Initialize database
python setup_database.py

# Run the project
python run.py
```

#### 2. Development Workflow

1. **Create Branch**
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/your-bug-fix
   ```

2. **Write Code**
   - Follow existing code style
   - Add necessary comments and docstrings
   - Ensure code passes all tests

3. **Run Tests**
   ```bash
   # Run all tests
   pytest

   # Run specific tests
   pytest tests/test_specific.py

   # Generate coverage report
   pytest --cov=src/landppt
   ```

4. **Code Formatting**
   ```bash
   # Format code
   black src/
   isort src/

   # Check code quality
   flake8 src/
   mypy src/
   ```

5. **Commit Changes**
   ```bash
   git add .
   git commit -m "feat: add new feature description"
   # or
   git commit -m "fix: fix bug description"
   ```

6. **Push and Create PR**
   ```bash
   git push origin feature/your-feature-name
   ```
   Then create a Pull Request on GitHub

### 📝 Code Standards

#### Commit Message Format
Use [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

Types include:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation update
- `style`: Code formatting
- `refactor`: Code refactoring
- `test`: Test related
- `chore`: Build process or auxiliary tool changes

#### Python Code Standards
- Follow [PEP 8](https://pep8.org/) guidelines
- Use Type Hints
- Write clear docstrings
- Maintain single responsibility for functions and classes

### 🧪 Testing Guidelines

#### Writing Tests
- Write unit tests for new features
- Ensure test coverage is not less than 80%
- Use meaningful test names
- Test boundary conditions and exceptions

### 📋 Issue and PR Templates

#### Bug Reports
When reporting bugs, please include:
- Problem description
- Steps to reproduce
- Expected behavior
- Actual behavior
- Environment info (OS, Python version, etc.)
- Relevant logs or screenshots

#### Feature Requests
When proposing new features, please include:
- Feature description
- Use cases
- Expected benefits
- Possible implementation approaches

### 📞 Contact

If you have questions, you can contact us through:
- Create a [GitHub Issue](https://github.com/your-username/LandPPT/issues)
- Join [GitHub Discussions](https://github.com/your-username/LandPPT/discussions)

---

Thank you for contributing to LandPPT! 🎉
