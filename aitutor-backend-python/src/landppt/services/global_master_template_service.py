"""
Global Master Template Service for managing reusable master templates
"""

import json
import logging
import time
import base64
from typing import Dict, Any, List, Optional
from io import BytesIO

from ..ai import get_ai_provider, AIMessage, MessageRole
from ..core.config import ai_config
from ..database.service import DatabaseService
from ..database.database import AsyncSessionLocal

# Configure logger for this module
logger = logging.getLogger(__name__)


class GlobalMasterTemplateService:
    """Service for managing global master templates"""

    def __init__(self, provider_name: Optional[str] = None):
        self.provider_name = provider_name

    @property
    def ai_provider(self):
        """Dynamically get AI provider to ensure latest config"""
        provider_name = self.provider_name or ai_config.default_ai_provider
        return get_ai_provider(provider_name)

    async def create_template(self, template_data: Dict[str, Any]) -> Dict[str, Any]:
        """Create a new global master template"""
        try:
            # Validate required fields
            required_fields = ['template_name', 'html_template']
            for field in required_fields:
                if not template_data.get(field):
                    raise ValueError(f"Missing required field: {field}")

            # Check if template name already exists
            async with AsyncSessionLocal() as session:
                db_service = DatabaseService(session)
                existing = await db_service.get_global_master_template_by_name(template_data['template_name'])
                if existing:
                    raise ValueError(f"Template name '{template_data['template_name']}' already exists")

            # Generate preview image if not provided
            if not template_data.get('preview_image'):
                template_data['preview_image'] = await self._generate_preview_image(template_data['html_template'])

            # Extract style config if not provided
            if not template_data.get('style_config'):
                template_data['style_config'] = self._extract_style_config(template_data['html_template'])

            # Set default values
            template_data.setdefault('description', '')
            template_data.setdefault('tags', [])
            template_data.setdefault('is_default', False)
            template_data.setdefault('is_active', True)
            template_data.setdefault('created_by', 'system')

            # Create template
            async with AsyncSessionLocal() as session:
                db_service = DatabaseService(session)
                template = await db_service.create_global_master_template(template_data)

                return {
                    "id": template.id,
                    "template_name": template.template_name,
                    "description": template.description,
                    "preview_image": template.preview_image,
                    "tags": template.tags,
                    "is_default": template.is_default,
                    "is_active": template.is_active,
                    "usage_count": template.usage_count,
                    "created_by": template.created_by,
                    "created_at": template.created_at,
                    "updated_at": template.updated_at
                }

        except Exception as e:
            logger.error(f"Failed to create global master template: {e}")
            raise

    async def get_all_templates(self, active_only: bool = True) -> List[Dict[str, Any]]:
        """Get all global master templates"""
        try:
            async with AsyncSessionLocal() as session:
                db_service = DatabaseService(session)
                templates = await db_service.get_all_global_master_templates(active_only)

                return [
                    {
                        "id": template.id,
                        "template_name": template.template_name,
                        "description": template.description,
                        "preview_image": template.preview_image,
                        "tags": template.tags,
                        "is_default": template.is_default,
                        "is_active": template.is_active,
                        "usage_count": template.usage_count,
                        "created_by": template.created_by,
                        "created_at": template.created_at,
                        "updated_at": template.updated_at
                    }
                    for template in templates
                ]

        except Exception as e:
            logger.error(f"Failed to get global master templates: {e}")
            raise

    async def get_all_templates_paginated(
        self,
        active_only: bool = True,
        page: int = 1,
        page_size: int = 6,
        search: Optional[str] = None
    ) -> Dict[str, Any]:
        """Get all global master templates with pagination"""
        try:
            async with AsyncSessionLocal() as session:
                db_service = DatabaseService(session)

                # Calculate offset
                offset = (page - 1) * page_size

                # Get templates with pagination
                templates, total_count = await db_service.get_global_master_templates_paginated(
                    active_only=active_only,
                    offset=offset,
                    limit=page_size,
                    search=search
                )

                # Calculate pagination info
                total_pages = (total_count + page_size - 1) // page_size
                has_next = page < total_pages
                has_prev = page > 1

                template_list = [
                    {
                        "id": template.id,
                        "template_name": template.template_name,
                        "description": template.description,
                        "preview_image": template.preview_image,
                        "tags": template.tags,
                        "is_default": template.is_default,
                        "is_active": template.is_active,
                        "usage_count": template.usage_count,
                        "created_by": template.created_by,
                        "created_at": template.created_at,
                        "updated_at": template.updated_at
                    }
                    for template in templates
                ]

                return {
                    "templates": template_list,
                    "pagination": {
                        "current_page": page,
                        "page_size": page_size,
                        "total_count": total_count,
                        "total_pages": total_pages,
                        "has_next": has_next,
                        "has_prev": has_prev
                    }
                }
        except Exception as e:
            logger.error(f"Failed to get paginated templates: {e}")
            raise

    async def get_template_by_id(self, template_id: int) -> Optional[Dict[str, Any]]:
        """Get global master template by ID"""
        try:
            async with AsyncSessionLocal() as session:
                db_service = DatabaseService(session)
                template = await db_service.get_global_master_template_by_id(template_id)

                if not template:
                    return None

                return {
                    "id": template.id,
                    "template_name": template.template_name,
                    "description": template.description,
                    "html_template": template.html_template,
                    "preview_image": template.preview_image,
                    "style_config": template.style_config,
                    "tags": template.tags,
                    "is_default": template.is_default,
                    "is_active": template.is_active,
                    "usage_count": template.usage_count,
                    "created_by": template.created_by,
                    "created_at": template.created_at,
                    "updated_at": template.updated_at
                }

        except Exception as e:
            logger.error(f"Failed to get global master template {template_id}: {e}")
            raise

    async def update_template(self, template_id: int, update_data: Dict[str, Any]) -> bool:
        """Update a global master template"""
        try:
            # Check if template name conflicts (if being updated)
            if 'template_name' in update_data:
                async with AsyncSessionLocal() as session:
                    db_service = DatabaseService(session)
                    existing = await db_service.get_global_master_template_by_name(update_data['template_name'])
                    if existing and existing.id != template_id:
                        raise ValueError(f"Template name '{update_data['template_name']}' already exists")

            # Update preview image if HTML template is updated
            if 'html_template' in update_data and 'preview_image' not in update_data:
                update_data['preview_image'] = await self._generate_preview_image(update_data['html_template'])

            # Update style config if HTML template is updated
            if 'html_template' in update_data and 'style_config' not in update_data:
                update_data['style_config'] = self._extract_style_config(update_data['html_template'])

            async with AsyncSessionLocal() as session:
                db_service = DatabaseService(session)
                return await db_service.update_global_master_template(template_id, update_data)

        except Exception as e:
            logger.error(f"Failed to update global master template {template_id}: {e}")
            raise

    async def delete_template(self, template_id: int) -> bool:
        """Delete a global master template"""
        try:
            async with AsyncSessionLocal() as session:
                db_service = DatabaseService(session)

                # Check if template exists
                template = await db_service.get_global_master_template_by_id(template_id)
                if not template:
                    logger.warning(f"Template {template_id} not found for deletion")
                    return False

                # Check if it's the default template
                if template.is_default:
                    raise ValueError("Cannot delete the default template")

                logger.info(f"Deleting template {template_id}: {template.template_name}")
                result = await db_service.delete_global_master_template(template_id)

                if result:
                    logger.info(f"Successfully deleted template {template_id}")
                else:
                    logger.warning(f"Failed to delete template {template_id} - no rows affected")

                return result

        except Exception as e:
            logger.error(f"Failed to delete global master template {template_id}: {e}")
            raise

    async def set_default_template(self, template_id: int) -> bool:
        """Set a template as default"""
        try:
            async with AsyncSessionLocal() as session:
                db_service = DatabaseService(session)
                return await db_service.set_default_global_master_template(template_id)

        except Exception as e:
            logger.error(f"Failed to set default template {template_id}: {e}")
            raise

    async def get_default_template(self) -> Optional[Dict[str, Any]]:
        """Get the default template"""
        try:
            async with AsyncSessionLocal() as session:
                db_service = DatabaseService(session)
                template = await db_service.get_default_global_master_template()

                if not template:
                    return None

                return {
                    "id": template.id,
                    "template_name": template.template_name,
                    "description": template.description,
                    "html_template": template.html_template,
                    "preview_image": template.preview_image,
                    "style_config": template.style_config,
                    "tags": template.tags,
                    "is_default": template.is_default,
                    "is_active": template.is_active,
                    "usage_count": template.usage_count,
                    "created_by": template.created_by,
                    "created_at": template.created_at,
                    "updated_at": template.updated_at
                }

        except Exception as e:
            logger.error(f"Failed to get default template: {e}")
            raise

    async def generate_template_with_ai_stream(self, prompt: str, template_name: str, description: str = "", tags: List[str] = None):
        """Generate a new template using AI with streaming response"""
        import asyncio
        import json

        # 构建AI提示词
        ai_prompt = f"""
作为专业的PPT模板设计师，请根据以下要求生成一个HTML母版模板。

请按照以下步骤思考并生成：

1. 首先分析用户需求
2. 设计模板的整体风格和布局
3. 确定色彩方案和字体选择
4. 编写HTML结构
5. 添加CSS样式
6. 优化和完善

用户需求：{prompt}

设计要求：
1. **严格尺寸控制**：页面尺寸必须为1280x720像素（16:9比例）
2. **完整HTML结构**：包含<!DOCTYPE html>、head、body等完整结构
3. **内联样式**：所有CSS样式必须内联，确保自包含性
4. **响应式设计**：适配不同屏幕尺寸但保持16:9比例
5. **占位符支持**：在适当位置使用占位符，如：
   - {{{{ page_title }}}} - 页面标题，默认居左
   - {{{{ page_content }}}} - 页面内容
   - {{{{ current_page_number }}}} - 当前页码
   - {{{{ total_page_count }}}} - 总页数
6. **技术要求**：
   - 使用Tailwind CSS或内联CSS
   - 支持Font Awesome图标
   - 支持Chart.js、ECharts.js、D3.js等图表库
   - 确保所有内容在720px高度内完全显示

请详细说明你的设计思路，然后生成完整的HTML模板代码，使用```html代码块格式返回。
"""

        try:
            # 检查AI提供商是否支持流式响应
            if hasattr(self.ai_provider, 'stream_text_completion'):
                # 使用流式API
                async for chunk in self.ai_provider.stream_text_completion(
                    prompt=ai_prompt,
                    max_tokens=ai_config.max_tokens,
                    temperature=0.7
                ):
                    yield {
                        'type': 'thinking',
                        'content': chunk
                    }
            else:
                # 模拟流式响应
                yield {'type': 'thinking', 'content': '🤔 正在分析您的需求...\n\n'}
                await asyncio.sleep(1)

                yield {'type': 'thinking', 'content': f'需求分析：{prompt}\n\n'}
                await asyncio.sleep(0.5)

                yield {'type': 'thinking', 'content': '🎨 开始设计模板风格...\n'}
                await asyncio.sleep(1)

                yield {'type': 'thinking', 'content': '📐 确定布局结构...\n'}
                await asyncio.sleep(0.8)

                yield {'type': 'thinking', 'content': '🎯 选择配色方案...\n'}
                await asyncio.sleep(0.7)

                yield {'type': 'thinking', 'content': '💻 开始编写HTML代码...\n'}
                await asyncio.sleep(1)

                # 调用标准AI生成
                response = await self.ai_provider.text_completion(
                    prompt=ai_prompt,
                    max_tokens=ai_config.max_tokens,
                    temperature=0.7
                )

                yield {'type': 'thinking', 'content': '✨ 优化样式和交互效果...\n'}
                await asyncio.sleep(0.5)

                # 处理AI响应
                html_template = self._extract_html_from_response(response.content)

                if not self._validate_html_template(html_template):
                    raise ValueError("Generated HTML template is invalid")

                yield {'type': 'thinking', 'content': '💾 保存模板到数据库...\n'}
                await asyncio.sleep(0.3)

                # 创建模板
                template_data = {
                    'template_name': template_name,
                    'description': description or f"AI生成的模板：{prompt[:100]}",
                    'html_template': html_template,
                    'tags': tags or ['AI生成'],
                    'created_by': 'AI'
                }

                result = await self.create_template(template_data)

                yield {
                    'type': 'complete',
                    'message': '模板生成完成！',
                    'template_id': result['id']
                }

        except Exception as e:
            logger.error(f"Failed to generate template with AI stream: {e}")
            yield {
                'type': 'error',
                'message': str(e)
            }

    async def generate_template_with_ai(self, prompt: str, template_name: str, description: str = "", tags: List[str] = None) -> Dict[str, Any]:
        """Generate a new template using AI"""
        try:
            # Construct AI prompt for template generation
            ai_prompt = f"""
作为专业的PPT模板设计师，请根据以下要求生成一个HTML母版模板：

用户需求：{prompt}

设计要求：
1. **严格尺寸控制**：页面尺寸必须为1280x720像素（16:9比例）
2. **完整HTML结构**：包含<!DOCTYPE html>、head、body等完整结构
3. **内联样式**：所有CSS样式必须内联，确保自包含性
4. **响应式设计**：适配不同屏幕尺寸但保持16:9比例
5. **占位符支持**：在适当位置使用占位符，如：
   - {{{{ page_title }}}} - 页面标题，默认居左
   - {{{{ page_content }}}} - 页面内容
   - {{{{ current_page_number }}}} - 当前页码
   - {{{{ total_page_count }}}} - 总页数
6. **技术要求**：
   - 使用Tailwind CSS或内联CSS
   - 支持Font Awesome图标
   - 支持Chart.js、ECharts.js、D3.js等图表库
   - 确保所有内容在720px高度内完全显示

请生成完整的HTML模板代码，使用```html代码块格式返回。
"""

            # Call AI to generate template
            response = await self.ai_provider.text_completion(
                prompt=ai_prompt,
                max_tokens=ai_config.max_tokens,
                temperature=0.7
            )

            # Extract HTML from response
            html_template = self._extract_html_from_response(response.content)

            logger.info(f"Extracted HTML template. Length: {len(html_template)}")
            logger.debug(f"HTML template preview: {html_template[:500]}...")

            # Validate generated HTML
            if not self._validate_html_template(html_template):
                logger.error(f"Generated HTML template validation failed.")
                logger.error(f"Template length: {len(html_template)}")
                logger.error(f"Template preview (first 2000 chars): {html_template[:2000]}")
                logger.error(f"Template ending (last 500 chars): {html_template[-500:]}")
                raise ValueError("Generated HTML template is invalid")

            # Create template data
            template_data = {
                'template_name': template_name,
                'description': description or f"AI生成的模板：{prompt[:100]}",
                'html_template': html_template,
                'tags': tags or ['AI生成'],
                'created_by': 'AI'
            }

            # Create the template
            return await self.create_template(template_data)

        except Exception as e:
            logger.error(f"Failed to generate template with AI: {e}")
            raise

    def _extract_html_from_response(self, response_content: str) -> str:
        """Extract HTML code from AI response with improved extraction"""
        import re

        logger.info(f"Extracting HTML from response. Content length: {len(response_content)}")

        # Try to extract HTML code block (most common format)
        html_match = re.search(r'```html\s*(.*?)\s*```', response_content, re.DOTALL)
        if html_match:
            extracted = html_match.group(1).strip()
            logger.info(f"Extracted HTML from code block. Length: {len(extracted)}")
            return extracted

        # Try to extract any code block that contains DOCTYPE
        code_block_match = re.search(r'```[a-zA-Z]*\s*(<!DOCTYPE html.*?</html>)\s*```', response_content, re.DOTALL | re.IGNORECASE)
        if code_block_match:
            extracted = code_block_match.group(1).strip()
            logger.info(f"Extracted HTML from generic code block. Length: {len(extracted)}")
            return extracted

        # Try to extract DOCTYPE HTML directly
        doctype_match = re.search(r'<!DOCTYPE html.*?</html>', response_content, re.DOTALL | re.IGNORECASE)
        if doctype_match:
            extracted = doctype_match.group(0).strip()
            logger.info(f"Extracted HTML from direct match. Length: {len(extracted)}")
            return extracted

        # If no specific pattern found, check if the content itself is HTML
        content_stripped = response_content.strip()
        if content_stripped.lower().startswith('<!doctype html') and content_stripped.lower().endswith('</html>'):
            logger.info(f"Content appears to be direct HTML. Length: {len(content_stripped)}")
            return content_stripped

        # Return original content as last resort
        logger.warning(f"Could not extract HTML from response, returning original content. Preview: {response_content[:200]}")
        return response_content.strip()

    def _validate_html_template(self, html_content: str) -> bool:
        """Validate HTML template with improved error reporting"""
        try:
            if not html_content or not html_content.strip():
                logger.error("HTML validation failed: Content is empty")
                return False

            html_lower = html_content.lower().strip()

            # Check basic HTML structure with more flexible validation
            if not html_lower.startswith('<!doctype html'):
                logger.error(f"HTML validation failed: Missing or incorrect DOCTYPE. Content starts with: {html_content[:100]}")
                return False

            if '</html>' not in html_lower:
                logger.error("HTML validation failed: Missing closing </html> tag")
                return False

            # Check required elements with better error reporting
            required_elements = {
                '<head>': '<head',
                '<body>': '<body',
                '<title>': '<title'
            }
            missing_elements = []

            for element_name, element_pattern in required_elements.items():
                if element_pattern not in html_lower:
                    missing_elements.append(element_name)

            if missing_elements:
                logger.error(f"HTML validation failed: Missing required elements: {missing_elements}")
                return False

            logger.info("HTML template validation passed successfully")
            return True

        except Exception as e:
            logger.error(f"HTML validation failed with exception: {e}")
            return False

    async def _generate_preview_image(self, html_template: str) -> str:
        """Generate preview image for template (placeholder implementation)"""
        # This is a placeholder implementation
        placeholder_svg = """
        <svg width="320" height="180" xmlns="http://www.w3.org/2000/svg">
            <rect width="320" height="180" fill="#f3f4f6"/>
            <text x="160" y="90" text-anchor="middle" font-family="Arial" font-size="14" fill="#6b7280">
                模板预览
            </text>
        </svg>
        """
        return f"data:image/svg+xml;base64,{base64.b64encode(placeholder_svg.encode()).decode()}"

    def _extract_style_config(self, html_content: str) -> Dict[str, Any]:
        """Extract style configuration from HTML"""
        import re

        style_config = {
            "dimensions": "1280x720",
            "aspect_ratio": "16:9",
            "framework": "HTML + CSS"
        }

        try:
            # Extract color configuration
            color_matches = re.findall(r'(?:background|color)[^:]*:\s*([^;]+)', html_content, re.IGNORECASE)
            if color_matches:
                style_config["colors"] = list(set(color_matches[:10]))  # Limit to 10 colors

            # Extract font configuration
            font_matches = re.findall(r'font-family[^:]*:\s*([^;]+)', html_content, re.IGNORECASE)
            if font_matches:
                style_config["fonts"] = list(set(font_matches[:5]))  # Limit to 5 fonts

            # Check for frameworks
            if 'tailwind' in html_content.lower():
                style_config["framework"] = "Tailwind CSS"
            elif 'bootstrap' in html_content.lower():
                style_config["framework"] = "Bootstrap"

        except Exception as e:
            logger.warning(f"Failed to extract style config: {e}")

        return style_config

    async def get_templates_by_tags(self, tags: List[str], active_only: bool = True) -> List[Dict[str, Any]]:
        """Get global master templates by tags"""
        try:
            async with AsyncSessionLocal() as session:
                db_service = DatabaseService(session)
                templates = await db_service.get_global_master_templates_by_tags(tags, active_only)

                return [
                    {
                        "id": template.id,
                        "template_name": template.template_name,
                        "description": template.description,
                        "preview_image": template.preview_image,
                        "tags": template.tags,
                        "is_default": template.is_default,
                        "is_active": template.is_active,
                        "usage_count": template.usage_count,
                        "created_by": template.created_by,
                        "created_at": template.created_at,
                        "updated_at": template.updated_at
                    }
                    for template in templates
                ]

        except Exception as e:
            logger.error(f"Failed to get global master templates by tags: {e}")
            raise

    async def get_templates_by_tags_paginated(
        self,
        tags: List[str],
        active_only: bool = True,
        page: int = 1,
        page_size: int = 6,
        search: Optional[str] = None
    ) -> Dict[str, Any]:
        """Get global master templates by tags with pagination"""
        try:
            async with AsyncSessionLocal() as session:
                db_service = DatabaseService(session)

                # Calculate offset
                offset = (page - 1) * page_size

                # Get templates with pagination
                templates, total_count = await db_service.get_global_master_templates_by_tags_paginated(
                    tags=tags,
                    active_only=active_only,
                    offset=offset,
                    limit=page_size,
                    search=search
                )

                # Calculate pagination info
                total_pages = (total_count + page_size - 1) // page_size
                has_next = page < total_pages
                has_prev = page > 1

                template_list = [
                    {
                        "id": template.id,
                        "template_name": template.template_name,
                        "description": template.description,
                        "preview_image": template.preview_image,
                        "tags": template.tags,
                        "is_default": template.is_default,
                        "is_active": template.is_active,
                        "usage_count": template.usage_count,
                        "created_by": template.created_by,
                        "created_at": template.created_at,
                        "updated_at": template.updated_at
                    }
                    for template in templates
                ]

                return {
                    "templates": template_list,
                    "pagination": {
                        "current_page": page,
                        "page_size": page_size,
                        "total_count": total_count,
                        "total_pages": total_pages,
                        "has_next": has_next,
                        "has_prev": has_prev
                    }
                }
        except Exception as e:
            logger.error(f"Failed to get paginated templates by tags: {e}")
            raise

    async def increment_template_usage(self, template_id: int) -> bool:
        """Increment template usage count"""
        try:
            async with AsyncSessionLocal() as session:
                db_service = DatabaseService(session)
                return await db_service.increment_global_master_template_usage(template_id)

        except Exception as e:
            logger.error(f"Failed to increment template usage {template_id}: {e}")
            raise
