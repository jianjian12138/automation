"""
Playwright驱动封装 - 智能化Web自动化
与现有Selenium框架兼容，提供更稳定、更快速的浏览器自动化能力

作者: AI Assistant
创建日期: 2025-10-30
"""

import json
import time
from datetime import datetime
from typing import Optional, Dict, Any, List
from pathlib import Path

try:
    from playwright.sync_api import sync_playwright, Page, Browser, BrowserContext, Playwright
    PLAYWRIGHT_AVAILABLE = True
except ImportError:
    PLAYWRIGHT_AVAILABLE = False
    print("警告: Playwright未安装，请运行: pip install playwright && playwright install")

from libs.config_center import LOG


class PlaywrightDriver:
    """
    Playwright驱动封装类
    
    特性:
    - 自动等待机制，减少TimeoutException
    - 智能元素定位，支持多种选择器
    - 自动录制视频和trace
    - 网络请求拦截和mock
    - 多浏览器支持(Chromium, Firefox, WebKit)
    """
    
    def __init__(
        self,
        browser: str = "chromium",
        headless: bool = False,
        slow_mo: int = 0,
        viewport: Dict[str, int] = None,
        record_video: bool = False,
        record_trace: bool = True,
        timeout: int = 30000,
        executable_path: str = None,
        use_mcp: bool = False
    ):
        """
        初始化Playwright驱动
        
        :param browser: 浏览器类型 chromium/firefox/webkit
        :param headless: 是否无头模式
        :param slow_mo: 慢速执行(ms)，用于调试
        :param viewport: 视口大小 {'width': 1920, 'height': 1080}
        :param record_video: 是否录制视频
        :param record_trace: 是否录制trace
        :param timeout: 默认超时时间(ms)
        :param executable_path: 本地浏览器可执行文件路径（用于使用本地Chrome）
        :param use_mcp: 是否使用MCP（Model Context Protocol）代替Playwright
        """
        if not PLAYWRIGHT_AVAILABLE:
            raise ImportError("Playwright未安装")
        
        self.browser_type = browser
        self.headless = headless
        self.slow_mo = slow_mo
        self.record_video = record_video
        self.record_trace = record_trace
        self.timeout = timeout
        self.executable_path = executable_path
        self.use_mcp = use_mcp
        
        # 设置默认视口
        self.viewport = viewport or {'width': 1920, 'height': 1080}
        
        # 创建必要的目录
        self._create_dirs()
        
        # 启动Playwright
        self.playwright: Playwright = sync_playwright().start()
        self.browser: Browser = self._launch_browser()
        self.context: BrowserContext = self._create_context()
        self.page: Page = self.context.new_page()
        
        # 设置默认超时
        self.page.set_default_timeout(self.timeout)
        self.page.set_default_navigation_timeout(self.timeout)
        
        # 启动trace录制
        if self.record_trace:
            self.context.tracing.start(screenshots=True, snapshots=True, sources=True)
        
        # Stagehand 集成（可选，延迟初始化）
        self._stagehand = None
        self._stagehand_api_key = None
        
        LOG.info(f"Playwright驱动已启动: {browser}, headless={headless}")
    
    def _create_dirs(self):
        """创建必要的目录"""
        dirs = ['./videos', './traces', './screenshots', './logs/network']
        for dir_path in dirs:
            Path(dir_path).mkdir(parents=True, exist_ok=True)
    
    def _launch_browser(self) -> Browser:
        """启动浏览器"""
        launch_options = {
            'headless': self.headless,
            'slow_mo': self.slow_mo,
        }
        
        # 如果指定了本地浏览器路径，使用本地浏览器
        if self.executable_path:
            import os
            executable_path = os.path.abspath(self.executable_path)
            if os.path.exists(executable_path):
                launch_options['executable_path'] = executable_path
                LOG.info(f"使用本地浏览器: {executable_path}")
            else:
                LOG.warning(f"指定的浏览器路径不存在: {executable_path}，将使用Playwright默认浏览器")
        elif self.browser_type in ["chromium", "chrome"]:
            # 尝试自动查找本地Chrome路径
            import os
            standard_chrome_paths = [
                r"C:\Program Files\Google\Chrome\Application\chrome.exe",
                r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
                os.path.expanduser(r"~\AppData\Local\Google\Chrome\Application\chrome.exe"),
            ]
            for path in standard_chrome_paths:
                if os.path.exists(path):
                    launch_options['executable_path'] = path
                    LOG.info(f"自动检测到本地Chrome浏览器: {path}")
                    break
        
        if self.browser_type == "chromium" or self.browser_type == "chrome":
            return self.playwright.chromium.launch(**launch_options)
        elif self.browser_type == "firefox":
            return self.playwright.firefox.launch(**launch_options)
        elif self.browser_type == "webkit" or self.browser_type == "safari":
            return self.playwright.webkit.launch(**launch_options)
        else:
            raise ValueError(f"不支持的浏览器类型: {self.browser_type}")
    
    def _create_context(self) -> BrowserContext:
        """创建浏览器上下文"""
        context_options = {
            'viewport': self.viewport,
            'ignore_https_errors': True,  # 忽略HTTPS错误
            'accept_downloads': True,  # 允许下载
        }
        
        # 视频录制配置
        if self.record_video:
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            context_options['record_video_dir'] = f'./videos/{timestamp}'
            context_options['record_video_size'] = self.viewport
        
        # HAR网络日志
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        context_options['record_har_path'] = f'./logs/network/har_{timestamp}.har'
        
        return self.browser.new_context(**context_options)
    
    # ==================== 基础操作 ====================
    
    def navigate(self, url: str, wait_until: str = 'networkidle'):
        """
        导航到URL
        
        :param url: 目标URL
        :param wait_until: 等待条件 load/domcontentloaded/networkidle
        """
        LOG.info(f"导航到: {url}")
        self.page.goto(url, wait_until=wait_until)
        # 等待页面加载完成后再获取title
        try:
            self.page.wait_for_load_state('networkidle', timeout=30000)
            title = self.page.title()
            LOG.info(f"页面加载完成: {title}")
        except Exception as e:
            LOG.warning(f"获取页面标题失败: {e}")
    
    def click(self, selector: str, timeout: Optional[int] = None, force: bool = False):
        """
        智能点击元素
        
        :param selector: 元素选择器
        :param timeout: 超时时间(ms)
        :param force: 强制点击(跳过可见性检查)
        """
        LOG.info(f"点击元素: {selector}")
        self.page.click(
            selector,
            timeout=timeout or self.timeout,
            force=force
        )
    
    def fill(self, selector: str, text: str, timeout: Optional[int] = None):
        """
        填充输入框(自动清空)
        
        :param selector: 元素选择器
        :param text: 输入文本
        :param timeout: 超时时间(ms)
        """
        LOG.info(f"填充文本: {selector} = {text}")
        self.page.fill(selector, text, timeout=timeout or self.timeout)
    
    def type(self, selector: str, text: str, delay: int = 50):
        """
        模拟打字输入
        
        :param selector: 元素选择器
        :param text: 输入文本
        :param delay: 每个字符延迟(ms)
        """
        LOG.info(f"输入文本: {selector} = {text}")
        self.page.type(selector, text, delay=delay)
    
    def select_option(self, selector: str, value: str = None, label: str = None):
        """
        选择下拉框选项
        
        :param selector: 下拉框选择器
        :param value: 选项值
        :param label: 选项标签
        """
        if value:
            LOG.info(f"选择选项(值): {selector} = {value}")
            self.page.select_option(selector, value=value)
        elif label:
            LOG.info(f"选择选项(标签): {selector} = {label}")
            self.page.select_option(selector, label=label)
    
    def check(self, selector: str):
        """勾选复选框/单选框"""
        LOG.info(f"勾选: {selector}")
        self.page.check(selector)
    
    def uncheck(self, selector: str):
        """取消勾选复选框"""
        LOG.info(f"取消勾选: {selector}")
        self.page.uncheck(selector)
    
    # ==================== 等待相关 ====================
    
    def wait_for_selector(self, selector: str, state: str = 'visible', timeout: Optional[int] = None):
        """
        等待元素出现
        
        :param selector: 元素选择器
        :param state: 状态 attached/detached/visible/hidden
        :param timeout: 超时时间(ms)
        :return: 元素对象
        """
        LOG.info(f"等待元素: {selector}, 状态={state}")
        return self.page.wait_for_selector(
            selector,
            state=state,
            timeout=timeout or self.timeout
        )
    
    def wait_for_url(self, url_pattern: str, timeout: Optional[int] = None):
        """等待URL匹配"""
        LOG.info(f"等待URL: {url_pattern}")
        self.page.wait_for_url(url_pattern, timeout=timeout or self.timeout)
    
    def wait_for_load_state(self, state: str = 'networkidle'):
        """
        等待页面加载状态
        
        :param state: load/domcontentloaded/networkidle
        """
        LOG.info(f"等待加载状态: {state}")
        self.page.wait_for_load_state(state)
    
    def wait_for_response(self, url_pattern: str, timeout: Optional[int] = None):
        """
        等待API响应
        
        :param url_pattern: URL模式(支持通配符)
        :param timeout: 超时时间(ms)
        :return: Response对象
        """
        LOG.info(f"等待API响应: {url_pattern}")
        with self.page.expect_response(url_pattern, timeout=timeout or self.timeout) as response_info:
            response = response_info.value
            LOG.info(f"收到响应: {response.url}, 状态码={response.status}")
            return response
    
    # ==================== 断言相关 ====================
    
    def is_visible(self, selector: str) -> bool:
        """检查元素是否可见"""
        return self.page.is_visible(selector)
    
    def is_enabled(self, selector: str) -> bool:
        """检查元素是否可用"""
        return self.page.is_enabled(selector)
    
    def is_checked(self, selector: str) -> bool:
        """检查复选框是否勾选"""
        return self.page.is_checked(selector)
    
    def get_text(self, selector: str) -> str:
        """获取元素文本"""
        if not selector:
            raise ValueError("selector参数不能为空")
        try:
            text = self.page.text_content(selector)
            return text if text else ""
        except Exception as e:
            LOG.error(f"获取元素文本失败: {selector}, 错误: {e}")
            return ""
    
    def get_attribute(self, selector: str, name: str) -> Optional[str]:
        """获取元素属性"""
        return self.page.get_attribute(selector, name)
    
    def get_value(self, selector: str) -> str:
        """获取输入框值"""
        return self.page.input_value(selector)
    
    # ==================== 高级功能 ====================
    
    def screenshot(self, path: Optional[str] = None, full_page: bool = False) -> bytes:
        """
        截图
        
        :param path: 保存路径(不提供则返回bytes)
        :param full_page: 是否截取整个页面
        :return: 图片bytes
        """
        if not path:
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            path = f"./screenshots/screenshot_{timestamp}.png"
        
        LOG.info(f"截图: {path}")
        return self.page.screenshot(path=path, full_page=full_page)
    
    def execute_script(self, script: str, *args) -> Any:
        """
        执行JavaScript
        
        :param script: JS代码
        :param args: 传递给JS的参数
        :return: JS返回值
        """
        LOG.info(f"执行脚本: {script[:100]}...")
        return self.page.evaluate(script, *args)
    
    def get_cookies(self) -> List[Dict]:
        """获取所有cookies"""
        return self.context.cookies()
    
    def add_cookie(self, cookie: Dict):
        """添加cookie"""
        self.context.add_cookies([cookie])
    
    def clear_cookies(self):
        """清除所有cookies"""
        LOG.info("清除cookies")
        self.context.clear_cookies()
    
    def get_local_storage(self, key: str) -> Optional[str]:
        """获取localStorage值"""
        return self.page.evaluate(f"() => localStorage.getItem('{key}')")
    
    def set_local_storage(self, key: str, value: str):
        """设置localStorage"""
        self.page.evaluate(f"() => localStorage.setItem('{key}', '{value}')")
    
    def get_stagehand(self, api_key: Optional[str] = None, **kwargs):
        """
        获取 Stagehand 集成实例（AI 驱动的浏览器自动化）
        
        :param api_key: AI 模型 API 密钥（可选，也可通过环境变量设置）
        :param kwargs: 其他 Stagehand 参数
        :return: StagehandIntegration 实例
        """
        # 如果已创建且参数相同，直接返回
        if self._stagehand is not None and api_key == self._stagehand_api_key:
            return self._stagehand
        
        try:
            from core.stagehand_integration import StagehandIntegration
            self._stagehand = StagehandIntegration(
                self.page,
                api_key=api_key,
                **kwargs
            )
            self._stagehand_api_key = api_key
            LOG.info("Stagehand 集成已初始化")
            return self._stagehand
        except ImportError as e:
            LOG.warning(f"Stagehand 集成未找到: {e}")
            return None
        except Exception as e:
            LOG.error(f"初始化 Stagehand 集成失败: {e}")
            return None
    
    # ==================== 网络相关 ====================
    
    def route(self, url_pattern: str, handler):
        """
        拦截和修改网络请求
        
        :param url_pattern: URL模式
        :param handler: 处理函数
        """
        LOG.info(f"拦截请求: {url_pattern}")
        self.page.route(url_pattern, handler)
    
    def mock_api(self, url_pattern: str, response_body: Any, status: int = 200):
        """
        Mock API响应
        
        :param url_pattern: API URL模式
        :param response_body: 响应内容
        :param status: HTTP状态码
        """
        LOG.info(f"Mock API: {url_pattern}")
        
        def handler(route):
            route.fulfill(
                status=status,
                content_type='application/json',
                body=json.dumps(response_body)
            )
        
        self.page.route(url_pattern, handler)
    
    # ==================== iframe处理 ====================
    
    def get_frame(self, name_or_url: str):
        """
        获取iframe(Playwright自动处理，通常不需要切换)
        
        :param name_or_url: frame名称或URL
        :return: Frame对象
        """
        return self.page.frame(name=name_or_url)
    
    # ==================== 多窗口/标签页 ====================
    
    def new_page(self) -> Page:
        """打开新标签页"""
        LOG.info("打开新标签页")
        return self.context.new_page()
    
    def switch_to_page(self, index: int):
        """切换到指定标签页"""
        LOG.info(f"切换到标签页: {index}")
        pages = self.context.pages
        if index < len(pages):
            self.page = pages[index]
        else:
            raise IndexError(f"标签页索引超出范围: {index}")
    
    # ==================== 清理相关 ====================
    
    def close_page(self):
        """关闭当前页面"""
        LOG.info("关闭页面")
        self.page.close()
    
    def close(self, save_trace: bool = True):
        """
        关闭浏览器
        
        :param save_trace: 是否保存trace
        """
        LOG.info("关闭Playwright驱动")
        
        # 保存trace
        if self.record_trace and save_trace:
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            trace_path = f"./traces/trace_{timestamp}.zip"
            try:
                self.context.tracing.stop(path=trace_path)
                LOG.info(f"Trace已保存: {trace_path}")
                LOG.info(f"查看trace: playwright show-trace {trace_path}")
            except Exception as e:
                LOG.warning(f"保存trace失败: {e}")
        
        # 关闭上下文和浏览器
        try:
            self.context.close()
            self.browser.close()
            self.playwright.stop()
        except Exception as e:
            LOG.error(f"关闭驱动失败: {e}")
    
    def __enter__(self):
        """上下文管理器入口"""
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """上下文管理器出口"""
        # 如果有异常，保存失败信息
        if exc_type:
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            
            # 截图
            try:
                screenshot_path = f"./screenshots/failed_{timestamp}.png"
                self.page.screenshot(path=screenshot_path, full_page=True)
                LOG.error(f"失败截图: {screenshot_path}")
            except:
                pass
            
            # 保存trace
            if self.record_trace:
                try:
                    trace_path = f"./traces/failed_{timestamp}.zip"
                    self.context.tracing.stop(path=trace_path)
                    LOG.error(f"失败trace: {trace_path}")
                    LOG.error(f"分析失败: playwright show-trace {trace_path}")
                except:
                    pass
        
        self.close(save_trace=not exc_type)  # 异常时trace已保存


# ==================== 智能选择器辅助类 ====================

class SmartSelector:
    """智能选择器 - 支持多种定位策略"""
    
    @staticmethod
    def parse(selector: str) -> str:
        """
        解析和优化选择器
        
        支持格式:
        - "text=登录" -> 文本定位
        - "role=button[name=提交]" -> 角色定位
        - "登录" -> 自动判断(智能匹配)
        - ".class" / "#id" -> CSS
        - "//xpath" -> XPath
        """
        selector = selector.strip()
        
        # 已经是Playwright格式
        if any(selector.startswith(prefix) for prefix in ['text=', 'role=', 'data-testid=']):
            return selector
        
        # XPath
        if selector.startswith('//') or selector.startswith('xpath='):
            return selector
        
        # CSS选择器
        if selector.startswith('.') or selector.startswith('#') or selector.startswith('['):
            return selector
        
        # 智能匹配 - 默认用文本
        return f"text={selector}"
    
    @staticmethod
    def text(text: str) -> str:
        """文本定位"""
        return f"text={text}"
    
    @staticmethod
    def role(role: str, name: str = None) -> str:
        """角色定位"""
        if name:
            return f"role={role}[name={name}]"
        return f"role={role}"
    
    @staticmethod
    def testid(testid: str) -> str:
        """Test ID定位"""
        return f"data-testid={testid}"
    
    @staticmethod
    def placeholder(text: str) -> str:
        """占位符定位"""
        return f"[placeholder*='{text}']"


# ==================== 使用示例 ====================

if __name__ == "__main__":
    # 示例1: 基础用法
    with PlaywrightDriver(browser="chromium", headless=False) as driver:
        # 导航
        driver.navigate("https://www.baidu.com")
        
        # 输入搜索
        driver.fill("#kw", "Playwright自动化测试")
        
        # 点击搜索按钮
        driver.click("#su")
        
        # 等待结果
        driver.wait_for_selector(".result")
        
        # 截图
        driver.screenshot("baidu_search.png")
    
    # 示例2: API等待
    with PlaywrightDriver() as driver:
        driver.navigate("https://example.com/login")
        
        # 填写表单
        driver.fill("#username", "admin")
        driver.fill("#password", "password")
        
        # 点击登录并等待API响应
        driver.click("text=登录")
        response = driver.wait_for_response("**/api/login")
        
        # 验证响应
        assert response.status == 200
        
        # 等待跳转
        driver.wait_for_url("**/dashboard")
    
    # 示例3: 智能选择器
    with PlaywrightDriver() as driver:
        driver.navigate("https://example.com")
        
        # 多种定位方式
        driver.click(SmartSelector.text("登录"))
        driver.click(SmartSelector.role("button", "提交"))
        driver.fill(SmartSelector.placeholder("用户名"), "admin")

