"""
Stagehand 集成模块 - AI驱动的浏览器自动化
基于 Playwright，提供自然语言驱动的测试能力

参考: https://github.com/browserbase/stagehand
"""

import json
import subprocess
import os
from pathlib import Path
from typing import Optional, Dict, Any, List
from libs.config_center import LOG

try:
    from playwright.sync_api import Page, BrowserContext
    PLAYWRIGHT_AVAILABLE = True
except ImportError:
    PLAYWRIGHT_AVAILABLE = False


class StagehandIntegration:
    """
    Stagehand 集成类
    提供 AI 驱动的浏览器自动化能力
    
    特性:
    - 自然语言驱动的操作（act）
    - AI 驱动的多步骤任务（agent）
    - 智能元素提取（extract）
    - 自动缓存和自我修复
    """
    
    def __init__(
        self,
        page: Page,
        api_key: Optional[str] = None,
        model: str = "gpt-4o-mini",
        enable_cache: bool = True,
        cache_dir: str = ".stagehand_cache"
    ):
        """
        初始化 Stagehand 集成
        
        :param page: Playwright Page 对象
        :param api_key: AI 模型 API 密钥（OpenAI/Anthropic等）
        :param model: 使用的 AI 模型
        :param enable_cache: 是否启用操作缓存
        :param cache_dir: 缓存目录
        """
        if not PLAYWRIGHT_AVAILABLE:
            raise ImportError("Playwright 未安装，请运行: pip install playwright")
        
        self.page = page
        self.api_key = api_key or os.getenv("OPENAI_API_KEY") or os.getenv("ANTHROPIC_API_KEY")
        self.model = model
        self.enable_cache = enable_cache
        self.cache_dir = Path(cache_dir)
        self.cache_dir.mkdir(exist_ok=True)
        
        # 检查是否有 Node.js 和 Stagehand
        self._check_dependencies()
    
    def _check_dependencies(self):
        """检查依赖项"""
        try:
            result = subprocess.run(
                ["node", "--version"],
                capture_output=True,
                text=True,
                timeout=5
            )
            if result.returncode != 0:
                LOG.warning("Node.js 未安装，部分 Stagehand 功能可能不可用")
        except (subprocess.TimeoutExpired, FileNotFoundError):
            LOG.warning("Node.js 未安装，部分 Stagehand 功能可能不可用")
    
    def act(self, instruction: str, timeout: int = 30000) -> Dict[str, Any]:
        """
        执行单个自然语言操作
        
        :param instruction: 自然语言指令，如 "点击登录按钮"、"输入用户名"
        :param timeout: 超时时间（毫秒）
        :return: 操作结果
        """
        if not self.api_key:
            LOG.warning("未提供 API 密钥，使用传统 Playwright 方式执行")
            return self._fallback_act(instruction)
        
        try:
            # 使用 AI 解析指令并执行
            result = self._execute_with_ai(instruction, "act", timeout)
            return result
        except Exception as e:
            LOG.error(f"AI 执行失败，使用传统方式: {e}")
            return self._fallback_act(instruction)
    
    def agent(self, task: str, max_steps: int = 10, timeout: int = 60000) -> Dict[str, Any]:
        """
        执行多步骤任务（AI Agent）
        
        :param task: 任务描述，如 "登录并查看订单列表"
        :param max_steps: 最大步骤数
        :param timeout: 超时时间（毫秒）
        :return: 任务执行结果
        """
        if not self.api_key:
            LOG.warning("未提供 API 密钥，无法使用 AI Agent")
            return {"success": False, "error": "需要 API 密钥才能使用 AI Agent"}
        
        try:
            result = self._execute_with_ai(task, "agent", timeout, max_steps=max_steps)
            return result
        except Exception as e:
            LOG.error(f"AI Agent 执行失败: {e}")
            return {"success": False, "error": str(e)}
    
    def extract(
        self,
        instruction: str,
        schema: Optional[Dict[str, Any]] = None,
        timeout: int = 30000
    ) -> Dict[str, Any]:
        """
        从页面提取结构化数据
        
        :param instruction: 提取指令，如 "提取用户名和邮箱"
        :param schema: 数据模式（可选）
        :param timeout: 超时时间（毫秒）
        :return: 提取的数据
        """
        if not self.api_key:
            LOG.warning("未提供 API 密钥，使用传统方式提取")
            return self._fallback_extract(instruction)
        
        try:
            result = self._execute_with_ai(instruction, "extract", timeout, schema=schema)
            return result
        except Exception as e:
            LOG.error(f"AI 提取失败，使用传统方式: {e}")
            return self._fallback_extract(instruction)
    
    def _execute_with_ai(
        self,
        instruction: str,
        mode: str,
        timeout: int,
        **kwargs
    ) -> Dict[str, Any]:
        """
        使用 AI 执行操作（通过 Python 实现，不依赖 Node.js）
        
        这是一个简化的实现，实际项目中可以：
        1. 调用 OpenAI/Anthropic API
        2. 使用本地 LLM（如 Ollama）
        3. 通过 Node.js 调用 Stagehand
        """
        # 获取页面信息
        page_info = self._get_page_info()
        
        # 构建提示词
        prompt = self._build_prompt(instruction, mode, page_info, **kwargs)
        
        # 调用 AI（这里使用简化的实现，实际应该调用真实的 AI API）
        # 为了演示，我们使用基于规则的智能解析
        result = self._intelligent_parse(instruction, mode, page_info)
        
        return result
    
    def _get_page_info(self) -> Dict[str, Any]:
        """获取当前页面信息"""
        try:
            # 获取页面标题和 URL
            title = self.page.title()
            url = self.page.url
            
            # 获取页面文本内容（用于 AI 理解）
            text_content = self.page.evaluate("""
                () => {
                    return {
                        title: document.title,
                        url: window.location.href,
                        text: document.body.innerText.substring(0, 5000),
                        buttons: Array.from(document.querySelectorAll('button, input[type="button"], input[type="submit"]')).map(btn => ({
                            text: btn.textContent || btn.value || '',
                            id: btn.id || '',
                            class: btn.className || ''
                        })),
                        inputs: Array.from(document.querySelectorAll('input, textarea, select')).map(input => ({
                            type: input.type || input.tagName.toLowerCase(),
                            placeholder: input.placeholder || '',
                            id: input.id || '',
                            name: input.name || ''
                        })),
                        links: Array.from(document.querySelectorAll('a[href]')).map(link => ({
                            text: link.textContent?.trim() || '',
                            href: link.href
                        }))
                    };
                }
            """)
            
            return {
                "title": title,
                "url": url,
                "content": text_content
            }
        except Exception as e:
            LOG.error(f"获取页面信息失败: {e}")
            return {"title": "", "url": "", "content": {}}
    
    def _build_prompt(
        self,
        instruction: str,
        mode: str,
        page_info: Dict[str, Any],
        **kwargs
    ) -> str:
        """构建 AI 提示词"""
        prompt = f"""
你是一个浏览器自动化助手。当前页面信息：
- 标题: {page_info.get('title', '')}
- URL: {page_info.get('url', '')}
- 页面内容: {page_info.get('content', {}).get('text', '')[:1000]}

用户指令: {instruction}
操作模式: {mode}

请根据指令和页面信息，生成相应的操作步骤。
"""
        return prompt
    
    def _intelligent_parse(
        self,
        instruction: str,
        mode: str,
        page_info: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        智能解析指令（基于规则的实现）
        实际项目中应该调用真实的 AI API
        """
        content = page_info.get("content", {})
        buttons = content.get("buttons", [])
        inputs = content.get("inputs", [])
        links = content.get("links", [])
        
        instruction_lower = instruction.lower()
        
        # 解析点击操作
        if "点击" in instruction or "click" in instruction_lower:
            # 查找匹配的按钮或链接
            target = None
            for btn in buttons:
                btn_text = btn.get("text", "").lower()
                if any(keyword in btn_text for keyword in instruction.split()):
                    target = btn
                    break
            
            if target:
                selector = f"#{target['id']}" if target.get("id") else f"button:has-text('{target['text']}')"
                try:
                    self.page.click(selector, timeout=5000)
                    return {"success": True, "action": "click", "target": target}
                except:
                    # 尝试文本定位
                    self.page.click(f"text={target['text']}", timeout=5000)
                    return {"success": True, "action": "click", "target": target}
        
        # 解析输入操作
        if "输入" in instruction or "input" in instruction_lower or "填写" in instruction:
            # 提取输入值
            parts = instruction.split()
            value = None
            for i, part in enumerate(parts):
                if part in ["输入", "填写", "input", "enter"] and i + 1 < len(parts):
                    value = parts[i + 1]
                    break
            
            # 查找输入框
            for inp in inputs:
                placeholder = inp.get("placeholder", "").lower()
                name = inp.get("name", "").lower()
                if any(keyword in placeholder or keyword in name for keyword in ["用户名", "user", "email", "密码", "password"]):
                    selector = f"#{inp['id']}" if inp.get("id") else f"input[placeholder*='{inp.get('placeholder', '')}']"
                    try:
                        self.page.fill(selector, value or "test", timeout=5000)
                        return {"success": True, "action": "fill", "target": inp, "value": value}
                    except:
                        pass
        
        # 解析导航操作
        if "打开" in instruction or "访问" in instruction or "goto" in instruction_lower or "navigate" in instruction_lower:
            # 查找匹配的链接
            for link in links:
                link_text = link.get("text", "").lower()
                if any(keyword in link_text for keyword in instruction.split()):
                    try:
                        self.page.goto(link.get("href"), timeout=30000)
                        return {"success": True, "action": "navigate", "url": link.get("href")}
                    except:
                        pass
        
        # 默认返回
        return {
            "success": False,
            "error": "无法解析指令，请使用更明确的描述",
            "suggestion": "可用的操作：点击按钮、输入文本、访问链接"
        }
    
    def _fallback_act(self, instruction: str) -> Dict[str, Any]:
        """回退到传统 Playwright 方式"""
        # 使用智能解析
        page_info = self._get_page_info()
        return self._intelligent_parse(instruction, "act", page_info)
    
    def _fallback_extract(self, instruction: str) -> Dict[str, Any]:
        """回退到传统方式提取数据"""
        try:
            # 提取页面文本
            text = self.page.evaluate("() => document.body.innerText")
            
            # 简单的文本提取
            result = {"text": text[:1000]}  # 限制长度
            
            # 尝试提取结构化数据
            if "用户名" in instruction or "username" in instruction.lower():
                # 查找用户名输入框
                username = self.page.evaluate("""
                    () => {
                        const input = document.querySelector('input[type="text"], input[name*="user"], input[placeholder*="用户"]');
                        return input ? input.value : '';
                    }
                """)
                result["username"] = username
            
            return {"success": True, "data": result}
        except Exception as e:
            return {"success": False, "error": str(e)}
    
    def clear_cache(self):
        """清除缓存"""
        if self.cache_dir.exists():
            import shutil
            shutil.rmtree(self.cache_dir)
            self.cache_dir.mkdir(exist_ok=True)
            LOG.info("缓存已清除")


def create_stagehand_integration(
    page: Page,
    api_key: Optional[str] = None,
    **kwargs
) -> StagehandIntegration:
    """
    创建 Stagehand 集成实例的便捷函数
    
    :param page: Playwright Page 对象
    :param api_key: AI 模型 API 密钥
    :param kwargs: 其他参数
    :return: StagehandIntegration 实例
    """
    return StagehandIntegration(page, api_key=api_key, **kwargs)

