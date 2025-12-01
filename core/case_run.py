# 导入必要模块：深拷贝、异常跟踪、函数装饰器
import copy, traceback
from functools import wraps
# 导入文件清理工具、线程池相关模块
from libs.file_utils import clear_threading_files
from concurrent.futures import ThreadPoolExecutor, as_completed
# 导入HTTP客户端、驱动管理和用例上下文相关模块
from core.http_client import HttpRequests, g
# 注意：Web UI测试已改用Playwright，不再使用Selenium
# from core.browser_driver import get_driver  # 已废弃，使用Playwright
from core.mobile_driver import get_app_driver
from core.case_context import ApiCaseInfo, UiCaseInfo, UiStepInfo, CaseInfo
# 导入日志工具和自定义异常
from libs.config_center import LOG
from libs.custom_exception import *


# 失败重试装饰器：用于包装测试用例执行方法，提供异常捕获和重试机制
def fail_run():
    def decorator(func):
        @wraps(func)
        def wrapper(self, retry, *args, **kwargs):
            # 循环执行重试逻辑
            for i in range(retry):
                try:
                    # 执行被装饰的测试方法
                    result = func(self, *args, **kwargs)

                    return result
                except Exception as e:
                    # 处理测试过程中的异常
                    if hasattr(g, 'case_info') and hasattr(g.case_info, 'step_info'):
                        # 获取当前步骤名称
                        if hasattr(g.case_info.step_info, 'step_name'):
                            step_name = g.case_info.step_info.step_name
                            g.case_info.step_info.result = "Fail"
                            g.case_info.step_info.message = e
                        else:
                            step_name = "还未开始运行步骤"

                        # 记录用例失败信息
                        case_name = g.case_info.case_name
                        g.case_info.result = "Fail"
                        g.case_info.message = f"发生异常的用例名称: {case_name}，步骤名: {step_name}，运行异常: {e}"

                        # 根据异常类型记录不同级别日志
                        if type(e) in (AssertionFailure, KeyWordRuntimeException, RuleRuntimeException):
                            LOG.error(f"当前运行用例名称: {case_name}，步骤名: {step_name}，用例后续步骤不再运行！", exc_info=True)
                        else:
                            LOG.error(traceback.format_exc())
                            LOG.error(f"发生未知异常，用例名称: {case_name}，步骤名:{step_name}，用例后续步骤不再运行！")

                        # UI测试失败时进行截图
                        if isinstance(g.case_info.step_info, UiStepInfo) and isinstance(g.driver, WebDriver):
                            g.case_info.step_info.fail_screenshot()  # 异常截图

                        # 准备重试（非最后一次失败时）
                        if not i + 1 == retry:
                            LOG.error(f"用例名称: {case_name} 开始进行重试，开始第 {i + 2} 次运行")
                    else:
                        # 处理无case_info的异常情况
                        LOG.error(traceback.format_exc())
                        if not i + 1 == retry:
                            LOG.error(f"发生未知异常，开始进行重试，开始第 {i + 2} 次运行")

                    # 最后一次重试失败时返回结果
                    if i + 1 == retry:  # 最后一次重试发生异常
                        return g.case_info
                finally:
                    # 清理线程临时文件
                    clear_threading_files()
                    # 清理用例上下文信息
                    if hasattr(g, 'case_info') and isinstance(g.case_info, CaseInfo):
                        delattr(g.case_info, "step_info")
                        delattr(g.case_info, "case_content")
                        if isinstance(g.case_info, ApiCaseInfo):
                            g.client.close()
                        elif isinstance(g.case_info, UiCaseInfo):
                            # 检查driver是否存在再调用quit()
                            if hasattr(g, 'driver') and g.driver:
                                if hasattr(g.driver, 'close'):
                                    g.driver.close()
                                elif hasattr(g.driver, 'quit'):
                                    g.driver.quit()

        return wrapper

    return decorator


# 测试用例执行类：管理不同类型测试用例的执行过程
class CaseRun:

    def __init__(self, env_name, env_conf):
        self.env_name = env_name  # 测试环境名称
        self.env_conf = env_conf  # 环境配置参数

    # API测试用例执行方法（带失败重试机制）
    @fail_run()
    def case_api_run(self, case):
        g.env = self.env_name  # 设置全局环境名称
        host_conf = self.env_conf.get("host_conf")  # 获取主机配置
        g.client = HttpRequests(host_conf=host_conf)  # 初始化HTTP客户端
        # 深拷贝用例数据避免线程安全问题
        g.case_info = ApiCaseInfo(copy.deepcopy(case))
        g.case_info.run()  # 执行API测试用例

        return g.case_info

    # Web UI测试用例执行方法（带失败重试机制）
    @fail_run()
    def case_web_ui_run(self, case):
        g.env = self.env_name  # 设置全局环境名称
        env_conf = copy.deepcopy(self.env_conf)  # 深拷贝环境配置
        
        # 新增：优先使用用例文件中的浏览器配置
        # 检查用例中是否有直接的browser配置
        case_browser = case.get("browser")
        if case_browser:
            env_conf["browser"] = case_browser
            # 根据浏览器类型更新对应的驱动路径
            if case_browser == "Firefox":
                # 优先使用config.ini中的firefox_driver_path配置
                env_conf["webdriver_path"] = env_conf.get("firefox_driver_path", "geckodriver.exe")
        # 检查用例driver配置中的browser
        elif "driver" in case and "browser" in case["driver"]:
            env_conf["browser"] = case["driver"]["browser"]
            # 根据浏览器类型更新对应的驱动路径
            if case["driver"]["browser"] == "Firefox":
                # 优先使用config.ini中的firefox_driver_path配置
                env_conf["webdriver_path"] = env_conf.get("firefox_driver_path", "geckodriver.exe")
        
        # 处理移动设备模拟配置
        mobile_emulation = case.get("driver", {}).get("mobile_emulation", None)
        if mobile_emulation:
            env_conf["mobile"] = mobile_emulation
        
        # 新增：移除不被get_driver函数接受的firefox_driver_path参数
        if "firefox_driver_path" in env_conf:
            del env_conf["firefox_driver_path"]
            
        # 使用Playwright驱动（不再使用Selenium）
        try:
            from core.playwright_driver import PlaywrightDriver
            from keywords.playwright_keywords import set_playwright_driver
            import yaml
            import os
            
            # 获取浏览器配置
            browser_type = env_conf.get("browser", "Chrome").lower()
            if browser_type == "chrome":
                browser_type = "chromium"
            elif browser_type == "edge":
                browser_type = "chromium"  # Edge使用chromium
            
            headless = case.get("driver", {}).get("headless", False)
            
            # 读取config.yaml配置
            executable_path = None
            use_mcp = False
            try:
                config_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), "config", "config.yaml")
                if os.path.exists(config_path):
                    with open(config_path, 'r', encoding='utf-8') as f:
                        config = yaml.safe_load(f)
                        if config and 'playwright' in config:
                            playwright_config = config['playwright']
                            # 读取本地Chrome路径
                            if 'chrome_executable_path' in playwright_config:
                                executable_path = playwright_config['chrome_executable_path']
                            # 读取MCP配置
                            if 'use_mcp' in playwright_config:
                                use_mcp = playwright_config['use_mcp']
            except Exception as e:
                LOG.warning(f"读取config.yaml配置失败: {e}，将使用默认配置")
            
            # 从用例配置中读取（优先级更高）
            driver_config = case.get("driver", {})
            if "chrome_executable_path" in driver_config:
                executable_path = driver_config["chrome_executable_path"]
            if "use_mcp" in driver_config:
                use_mcp = driver_config["use_mcp"]
            
            # 初始化Playwright驱动
            playwright_driver = PlaywrightDriver(
                browser=browser_type,
                headless=headless,
                record_trace=True,
                executable_path=executable_path,
                use_mcp=use_mcp
            )
            
            # 设置全局Playwright驱动
            set_playwright_driver(playwright_driver)
            g.driver = playwright_driver  # 兼容性：保持g.driver引用
            
            LOG.info(f"Playwright驱动初始化成功: {browser_type}, headless={headless}")
            
        except Exception as e:
            LOG.error(f"Playwright驱动初始化失败: {e}")
            import traceback
            LOG.error(traceback.format_exc())
            # 初始化case_info，避免后续AttributeError
            g.case_info = UiCaseInfo(copy.deepcopy(case))
            g.case_info.status = "失败"
            g.case_info.error_info = str(e)
            return g.case_info
        
        # 初始化UI用例信息
        g.case_info = UiCaseInfo(copy.deepcopy(case))
        
        try:
            g.case_info.run()  # 执行Web UI测试用例
        finally:
            # 关闭Playwright驱动
            if hasattr(g, 'driver') and g.driver:
                try:
                    g.driver.close()
                except:
                    pass

        return g.case_info

    # 移动UI测试用例执行方法（带失败重试机制）
    @fail_run()
    def case_mobile_ui_run(self, case):
        g.env = self.env_name  # 设置全局环境名称
        g.driver = get_app_driver(**self.env_conf)  # 获取移动应用驱动
        g.driver.hide_keyboard()  # 隐藏键盘
        g.case_info = UiCaseInfo(copy.deepcopy(case))  # 初始化UI用例信息
        g.case_info.run()  # 执行移动UI测试用例

        return g.case_info

    # PC UI测试用例执行方法（带失败重试机制）
    @fail_run()
    def case_pc_ui_run(self, case):
        g.env = self.env_name  # 设置全局环境名称
        g.driver = get_app_driver(**self.env_conf)  # 获取PC应用驱动
        g.case_info = UiCaseInfo(copy.deepcopy(case))  # 初始化UI用例信息
        g.case_info.run()  # 执行PC UI测试用例

        return g.case_info


# 多线程执行测试用例
def multi_threading_run(case_type, ready_list, env_name, max_workers, retry_num, env_conf):
    case_run = CaseRun(env_name, env_conf)  # 创建测试执行器实例
    # 获取对应类型的测试执行方法
    case_run_func = getattr(case_run, f"case_{case_type}_run")
    max_workers = round(max_workers)  # 确保线程数为整数

    # 根据线程数配置执行测试
    if max_workers > 1:
        report_data = []
        executor = ThreadPoolExecutor(max_workers=max_workers)  # 创建线程池
        # 提交所有测试任务
        all_task = [executor.submit(case_run_func, retry_num, case) for case in ready_list]
        # 获取执行结果
        for future in as_completed(all_task):
            case_info = future.result()
            LOG.info(f"用例名称: {case_info.case_name} 运行完成。")
            report_data.append(case_info)
    elif max_workers == 1:
        # 单线程顺序执行
        report_data = [case_run_func(retry_num, case) for case in ready_list]
    else:
        raise ValueError("线程数设置错误！")

    LOG.info("全部用例运行完成，开始生成报告。。。")
    return report_data
