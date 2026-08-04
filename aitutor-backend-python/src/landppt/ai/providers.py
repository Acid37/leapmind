"""
AI provider implementations
"""

import asyncio
import json
import logging
from typing import List, Dict, Any, Optional, AsyncGenerator

from .base import AIProvider, AIMessage, AIResponse, MessageRole
from ..core.config import ai_config

logger = logging.getLogger(__name__)

class OpenAIProvider(AIProvider):
    """OpenAI API provider"""
    
    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        try:
            import openai
            self.client = openai.AsyncOpenAI(
                api_key=config.get("api_key"),
                base_url=config.get("base_url")
            )
        except ImportError:
            logger.warning("OpenAI library not installed. Install with: pip install openai")
            self.client = None
    
    async def chat_completion(self, messages: List[AIMessage], **kwargs) -> AIResponse:
        """Generate chat completion using OpenAI"""
        if not self.client:
            raise RuntimeError("OpenAI client not available")
        
        config = self._merge_config(**kwargs)
        
        # Convert messages to OpenAI format
        openai_messages = [
            {"role": msg.role.value, "content": msg.content}
            for msg in messages
        ]
        
        try:
            response = await self.client.chat.completions.create(
                model=config.get("model", self.model),
                messages=openai_messages,
                # max_tokens=config.get("max_tokens", 2000),
                temperature=config.get("temperature", 0.7),
                top_p=config.get("top_p", 1.0)
            )
            
            choice = response.choices[0]
            
            return AIResponse(
                content=choice.message.content,
                model=response.model,
                usage={
                    "prompt_tokens": response.usage.prompt_tokens,
                    "completion_tokens": response.usage.completion_tokens,
                    "total_tokens": response.usage.total_tokens
                },
                finish_reason=choice.finish_reason,
                metadata={"provider": "openai"}
            )
            
        except Exception as e:
            # 提供更详细的错误信息
            error_msg = str(e)
            if "Expecting value" in error_msg:
                logger.error(f"OpenAI API JSON parsing error: {error_msg}. This usually indicates the API returned malformed JSON.")
            elif "timeout" in error_msg.lower():
                logger.error(f"OpenAI API timeout error: {error_msg}")
            elif "rate limit" in error_msg.lower():
                logger.error(f"OpenAI API rate limit error: {error_msg}")
            else:
                logger.error(f"OpenAI API error: {error_msg}")
            raise
    
    async def text_completion(self, prompt: str, **kwargs) -> AIResponse:
        """Generate text completion using OpenAI chat format"""
        messages = [AIMessage(role=MessageRole.USER, content=prompt)]
        return await self.chat_completion(messages, **kwargs)

    async def stream_chat_completion(self, messages: List[AIMessage], **kwargs) -> AsyncGenerator[str, None]:
        """Stream chat completion using OpenAI"""
        if not self.client:
            raise RuntimeError("OpenAI client not available")

        config = self._merge_config(**kwargs)

        # Convert messages to OpenAI format
        openai_messages = [
            {"role": msg.role.value, "content": msg.content}
            for msg in messages
        ]

        try:
            stream = await self.client.chat.completions.create(
                model=config.get("model", self.model),
                messages=openai_messages,
                # max_tokens=config.get("max_tokens", 2000),
                temperature=config.get("temperature", 0.7),
                top_p=config.get("top_p", 1.0),
                stream=True
            )

            async for chunk in stream:
                if chunk.choices and chunk.choices[0].delta.content:
                    yield chunk.choices[0].delta.content

        except Exception as e:
            logger.error(f"OpenAI streaming error: {e}")
            raise

    async def stream_text_completion(self, prompt: str, **kwargs) -> AsyncGenerator[str, None]:
        """Stream text completion using OpenAI chat format"""
        messages = [AIMessage(role=MessageRole.USER, content=prompt)]
        async for chunk in self.stream_chat_completion(messages, **kwargs):
            yield chunk

class AnthropicProvider(AIProvider):
    """Anthropic Claude API provider"""
    
    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        try:
            import anthropic
            self.client = anthropic.AsyncAnthropic(
                api_key=config.get("api_key")
            )
        except ImportError:
            logger.warning("Anthropic library not installed. Install with: pip install anthropic")
            self.client = None
    
    async def chat_completion(self, messages: List[AIMessage], **kwargs) -> AIResponse:
        """Generate chat completion using Anthropic Claude"""
        if not self.client:
            raise RuntimeError("Anthropic client not available")
        
        config = self._merge_config(**kwargs)
        
        # Convert messages to Anthropic format
        system_message = None
        claude_messages = []
        
        for msg in messages:
            if msg.role == MessageRole.SYSTEM:
                system_message = msg.content
            else:
                claude_messages.append({
                    "role": msg.role.value,
                    "content": msg.content
                })
        
        try:
            response = await self.client.messages.create(
                model=config.get("model", self.model),
                # max_tokens=config.get("max_tokens", 2000),
                temperature=config.get("temperature", 0.7),
                system=system_message,
                messages=claude_messages
            )
            
            content = response.content[0].text if response.content else ""
            
            return AIResponse(
                content=content,
                model=response.model,
                usage={
                    "prompt_tokens": response.usage.input_tokens,
                    "completion_tokens": response.usage.output_tokens,
                    "total_tokens": response.usage.input_tokens + response.usage.output_tokens
                },
                finish_reason=response.stop_reason,
                metadata={"provider": "anthropic"}
            )
            
        except Exception as e:
            logger.error(f"Anthropic API error: {e}")
            raise
    
    async def text_completion(self, prompt: str, **kwargs) -> AIResponse:
        """Generate text completion using Anthropic chat format"""
        messages = [AIMessage(role=MessageRole.USER, content=prompt)]
        return await self.chat_completion(messages, **kwargs)

class GoogleProvider(AIProvider):
    """Google Gemini API provider"""

    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        try:
            import google.generativeai as genai
            genai.configure(api_key=config.get("api_key"))
            self.client = genai
            self.model_instance = genai.GenerativeModel(config.get("model", "gemini-1.5-flash"))
        except ImportError:
            logger.warning("Google Generative AI library not installed. Install with: pip install google-generativeai")
            self.client = None
            self.model_instance = None

    async def chat_completion(self, messages: List[AIMessage], **kwargs) -> AIResponse:
        """Generate chat completion using Google Gemini"""
        if not self.client or not self.model_instance:
            raise RuntimeError("Google Gemini client not available")

        config = self._merge_config(**kwargs)

        # Convert messages to Gemini format
        # Gemini uses a different conversation format
        conversation_parts = []
        for msg in messages:
            if msg.role == MessageRole.SYSTEM:
                # System messages are handled differently in Gemini
                conversation_parts.append(f"System: {msg.content}")
            elif msg.role == MessageRole.USER:
                conversation_parts.append(f"User: {msg.content}")
            elif msg.role == MessageRole.ASSISTANT:
                conversation_parts.append(f"Assistant: {msg.content}")

        # Combine all parts into a single prompt
        prompt = "\n".join(conversation_parts)

        try:
            # Configure generation parameters
            # 确保max_tokens不会太小，至少1000个token用于生成内容
            max_tokens = max(config.get("max_tokens", 16384), 1000)
            generation_config = {
                "temperature": config.get("temperature", 0.7),
                "top_p": config.get("top_p", 1.0),
                # "max_output_tokens": max_tokens,
            }

            # 配置安全设置 - 设置为较宽松的安全级别以减少误拦截
            safety_settings = [
                {
                    "category": "HARM_CATEGORY_HARASSMENT",
                    "threshold": "BLOCK_ONLY_HIGH"
                },
                {
                    "category": "HARM_CATEGORY_HATE_SPEECH",
                    "threshold": "BLOCK_ONLY_HIGH"
                },
                {
                    "category": "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                    "threshold": "BLOCK_ONLY_HIGH"
                },
                {
                    "category": "HARM_CATEGORY_DANGEROUS_CONTENT",
                    "threshold": "BLOCK_ONLY_HIGH"
                }
            ]


            response = await self._generate_async(prompt, generation_config, safety_settings)
            logger.debug(f"Google Gemini API response: {response}")

            # 检查响应状态和安全过滤
            finish_reason = "stop"
            content = ""

            if response.candidates:
                candidate = response.candidates[0]
                finish_reason = candidate.finish_reason.name if hasattr(candidate.finish_reason, 'name') else str(candidate.finish_reason)

                # 检查是否被安全过滤器阻止或其他问题
                if finish_reason == "SAFETY":
                    logger.warning("Content was blocked by safety filters")
                    content = "[内容被安全过滤器阻止]"
                elif finish_reason == "RECITATION":
                    logger.warning("Content was blocked due to recitation")
                    content = "[内容因重复而被阻止]"
                elif finish_reason == "MAX_TOKENS":
                    logger.warning("Response was truncated due to max tokens limit")
                    # 尝试获取部分内容
                    try:
                        if hasattr(candidate, 'content') and candidate.content and hasattr(candidate.content, 'parts') and candidate.content.parts:
                            content = candidate.content.parts[0].text if candidate.content.parts[0].text else "[响应因token限制被截断，无内容]"
                        else:
                            content = "[响应因token限制被截断，无内容]"
                    except Exception as text_error:
                        logger.warning(f"Failed to get truncated response text: {text_error}")
                        content = "[响应因token限制被截断，无法获取内容]"
                elif finish_reason == "OTHER":
                    logger.warning("Content was blocked for other reasons")
                    content = "[内容被其他原因阻止]"
                else:
                    # 正常情况下获取文本
                    try:
                        if hasattr(candidate, 'content') and candidate.content and hasattr(candidate.content, 'parts') and candidate.content.parts:
                            content = candidate.content.parts[0].text if candidate.content.parts[0].text else ""
                        else:
                            # 回退到response.text
                            content = response.text if hasattr(response, 'text') and response.text else ""
                    except Exception as text_error:
                        logger.warning(f"Failed to get response text: {text_error}")
                        content = "[无法获取响应内容]"
            else:
                logger.warning("No candidates in response")
                content = "[响应中没有候选内容]"

            return AIResponse(
                content=content,
                model=self.model,
                usage={
                    "prompt_tokens": response.usage_metadata.prompt_token_count if hasattr(response, 'usage_metadata') else 0,
                    "completion_tokens": response.usage_metadata.candidates_token_count if hasattr(response, 'usage_metadata') else 0,
                    "total_tokens": response.usage_metadata.total_token_count if hasattr(response, 'usage_metadata') else 0
                },
                finish_reason=finish_reason,
                metadata={"provider": "google"}
            )

        except Exception as e:
            logger.error(f"Google Gemini API error: {e}")
            raise

    async def _generate_async(self, prompt: str, generation_config: Dict[str, Any], safety_settings=None):
        """Async wrapper for Gemini generation"""
        import asyncio
        loop = asyncio.get_event_loop()

        def _generate_sync():
            kwargs = {
                "generation_config": generation_config
            }
            if safety_settings:
                kwargs["safety_settings"] = safety_settings

            return self.model_instance.generate_content(
                prompt,
                **kwargs
            )

        return await loop.run_in_executor(None, _generate_sync)

    async def text_completion(self, prompt: str, **kwargs) -> AIResponse:
        """Generate text completion using Google Gemini"""
        messages = [AIMessage(role=MessageRole.USER, content=prompt)]
        return await self.chat_completion(messages, **kwargs)

class OllamaProvider(AIProvider):
    """Ollama local model provider"""
    
    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        try:
            import ollama
            self.client = ollama.AsyncClient(host=config.get("base_url", "http://localhost:11434"))
        except ImportError:
            logger.warning("Ollama library not installed. Install with: pip install ollama")
            self.client = None
    
    async def chat_completion(self, messages: List[AIMessage], **kwargs) -> AIResponse:
        """Generate chat completion using Ollama"""
        if not self.client:
            raise RuntimeError("Ollama client not available")
        
        config = self._merge_config(**kwargs)
        
        # Convert messages to Ollama format
        ollama_messages = [
            {"role": msg.role.value, "content": msg.content}
            for msg in messages
        ]
        
        try:
            response = await self.client.chat(
                model=config.get("model", self.model),
                messages=ollama_messages,
                options={
                    "temperature": config.get("temperature", 0.7),
                    "top_p": config.get("top_p", 1.0),
                    # "num_predict": config.get("max_tokens", 2000)
                }
            )
            
            content = response.get("message", {}).get("content", "")
            
            return AIResponse(
                content=content,
                model=config.get("model", self.model),
                usage=self._calculate_usage(
                    " ".join([msg.content for msg in messages]),
                    content
                ),
                finish_reason="stop",
                metadata={"provider": "ollama"}
            )
            
        except Exception as e:
            logger.error(f"Ollama API error: {e}")
            raise
    
    async def text_completion(self, prompt: str, **kwargs) -> AIResponse:
        """Generate text completion using Ollama"""
        messages = [AIMessage(role=MessageRole.USER, content=prompt)]
        return await self.chat_completion(messages, **kwargs)

class AIProviderFactory:
    """Factory for creating AI providers"""

    _providers = {
        "openai": OpenAIProvider,
        "anthropic": AnthropicProvider,
        "google": GoogleProvider,
        "gemini": GoogleProvider,  # Alias for google
        "ollama": OllamaProvider
    }

    @classmethod
    def create_provider(cls, provider_name: str, config: Optional[Dict[str, Any]] = None) -> AIProvider:
        """Create an AI provider instance"""
        if config is None:
            config = ai_config.get_provider_config(provider_name)

        # Built-in providers
        if provider_name not in cls._providers:
            raise ValueError(f"Unknown provider: {provider_name}")

        provider_class = cls._providers[provider_name]
        return provider_class(config)
    
    @classmethod
    def get_available_providers(cls) -> List[str]:
        """Get list of available providers"""
        return list(cls._providers.keys())

class AIProviderManager:
    """Manager for AI provider instances with caching and reloading"""

    def __init__(self):
        self._provider_cache = {}
        self._config_cache = {}

    def get_provider(self, provider_name: Optional[str] = None) -> AIProvider:
        """Get AI provider instance with caching"""
        if provider_name is None:
            provider_name = ai_config.default_ai_provider

        # Get current config for the provider
        current_config = ai_config.get_provider_config(provider_name)

        # Check if we have a cached provider and if config has changed
        cache_key = provider_name
        if (cache_key in self._provider_cache and
            cache_key in self._config_cache and
            self._config_cache[cache_key] == current_config):
            return self._provider_cache[cache_key]

        # Create new provider instance
        provider = AIProviderFactory.create_provider(provider_name, current_config)

        # Cache the provider and config
        self._provider_cache[cache_key] = provider
        self._config_cache[cache_key] = current_config

        return provider

    def clear_cache(self):
        """Clear provider cache to force reload"""
        self._provider_cache.clear()
        self._config_cache.clear()

    def reload_provider(self, provider_name: str):
        """Reload a specific provider"""
        cache_key = provider_name
        if cache_key in self._provider_cache:
            del self._provider_cache[cache_key]
        if cache_key in self._config_cache:
            del self._config_cache[cache_key]

# Global provider manager
_provider_manager = AIProviderManager()

def get_ai_provider(provider_name: Optional[str] = None) -> AIProvider:
    """Get AI provider instance"""
    return _provider_manager.get_provider(provider_name)

def reload_ai_providers():
    """Reload all AI providers (clear cache)"""
    _provider_manager.clear_cache()
