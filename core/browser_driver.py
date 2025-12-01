#- 提供了浏览器驱动的工厂函数 get_driver() ，支持Chrome、Firefox和IE浏览器的自动化测试
#- 实现了元素定位方法的封装 get_element_locator() ，支持多种定位方式的简写形式
#- 提供了Chrome浏览器网络请求记录功能 chrome_network_request() ，可以捕获XHR请求的详细信息


# 导入json模块，用于处理JSON格式数据
import json
# 导入selenium的webdriver模块，用于浏览器自动化测试
from selenium import webdriver
# 导入Service类，用于Selenium 4.x的驱动管理（兼容旧版本）
try:
    from selenium.webdriver.chrome.service import Service as ChromeService
    from selenium.webdriver.firefox.service import Service as FirefoxService
    from selenium.webdriver.ie.service import Service as IEService
    SELENIUM_4_PLUS = True
except ImportError:
    # Selenium 3.x 不支持 Service 类，使用 None 作为占位符
    ChromeService = None
    FirefoxService = None
    IEService = None
    SELENIUM_4_PLUS = False

# 导入Options类，用于浏览器选项配置
try:
    from selenium.webdriver.chrome.options import Options as ChromeOptions
    from selenium.webdriver.firefox.options import Options as FirefoxOptions
    from selenium.webdriver.ie.options import Options as IEOptions
except ImportError:
    # 旧版本可能使用不同的导入路径
    try:
        from selenium.webdriver.chrome.options import Options as ChromeOptions
        from selenium.webdriver.firefox.options import Options as FirefoxOptions
        from selenium.webdriver.ie.options import Options as IEOptions
    except ImportError:
        # 如果都失败，使用 webdriver 模块中的 Options
        ChromeOptions = webdriver.ChromeOptions
        FirefoxOptions = webdriver.FirefoxOptions
        IEOptions = webdriver.IeOptions
# 导入By类，提供元素定位方式的枚举值
from selenium.webdriver.common.by import By
# 尝试导入MobileBy类，提供移动端元素定位方式的枚举值
# 使用try-except处理导入错误，确保API测试可以正常运行
MobileBy = None
try:
    from appium.webdriver.common.mobileby import MobileBy
except ImportError:
    # 如果没有安装appium，设置MobileBy为None，并记录日志
    import logging
    logging.warning("Appium模块未安装，移动测试功能将不可用")
# 导入WebDriverException异常类，用于处理webdriver相关异常
from selenium.common.exceptions import WebDriverException
# 导入日志模块
from libs.config_center import LOG


# 确认函数定义接受webdriver_path参数
def get_driver(browser="Chrome", webdriver_path=None, headless=False, mobile=None, chrome_binary_path=None):
    """
    获取浏览器驱动实例的工厂函数
    驱动下载地址说明：
    chromedriver：http://chromedriver.storage.googleapis.com/index.html
    geckodriver：https://github.com/mozilla/geckodriver/releases
    iedriverserver：http://selenium-release.storage.googleapis.com/index.html
    
    注意事项：
    firefox56以下不需要安装webdriver驱动
    firefox57（firefox47及以上版本）需要安装geckodriver-v0.19.1-win32/64
    IEDriverServer的版本号和Selenium的版本号必须一致

    :param browser: 浏览器类型，支持Chrome/Firefox/Ie
    :param webdriver_path: 浏览器驱动路径，配置环境变量后可不传
    :param headless: 是否启用无头模式
    :param mobile: 移动端模拟配置，例如：{'deviceName': 'iPhone 6/7/8'}
    :param chrome_binary_path: Chrome浏览器可执行文件路径（用于使用本地Chrome）
    :return: 浏览器驱动实例
    """
    if browser == "Chrome":  # 配置Chrome浏览器驱动
        # 设置驱动路径，如未指定则使用默认路径
        executable_path = webdriver_path or "chromedriver"
        LOG.info("谷歌浏览器启动中。。。")
        
        # 创建Chrome选项对象（Selenium 4.x方式）
        options = ChromeOptions()
        
        # 配置本地Chrome浏览器路径
        if chrome_binary_path:
            import os
            chrome_binary_path = os.path.abspath(chrome_binary_path)
            if os.path.exists(chrome_binary_path):
                options.binary_location = chrome_binary_path
                LOG.info(f"使用本地Chrome浏览器: {chrome_binary_path}")
            else:
                LOG.warning(f"指定的Chrome路径不存在: {chrome_binary_path}，将使用系统默认Chrome")
        else:
            # 尝试自动查找本地Chrome路径
            import os
            standard_chrome_paths = [
                r"C:\Program Files\Google\Chrome\Application\chrome.exe",
                r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
                os.path.expanduser(r"~\AppData\Local\Google\Chrome\Application\chrome.exe"),
            ]
            for path in standard_chrome_paths:
                if os.path.exists(path):
                    options.binary_location = path
                    LOG.info(f"自动检测到本地Chrome浏览器: {path}")
                    break
        
        # 配置日志记录
        options.set_capability('goog:loggingPrefs', {'performance': 'ALL'})
        
        # 配置无头模式
        if headless:
            LOG.info("谷歌浏览器开启无界面模式")
            options.add_argument('--headless')
            options.add_argument('--disable-gpu')
            
        # 配置移动端模拟
        if mobile:
            LOG.info("谷歌浏览器开启移动端模式")
            options.add_experimental_option("mobileEmulation", mobile)
        
        # 创建Service对象（Selenium 4.x方式）或使用旧方式
        if SELENIUM_4_PLUS:
            service = ChromeService(executable_path=executable_path) if executable_path else None
            if service:
                driver = webdriver.Chrome(service=service, options=options)
            else:
                driver = webdriver.Chrome(options=options)
        else:
            # Selenium 3.x 方式
            driver = webdriver.Chrome(executable_path=executable_path, options=options)
        
    elif browser == "Firefox":  # 配置Firefox浏览器驱动
        LOG.info("火狐浏览器启动中。。。")
        # 优先使用配置的驱动路径，如果没有则使用环境变量中的geckodriver
        executable_path = webdriver_path or "geckodriver"
        LOG.info(f"使用的geckodriver路径: {executable_path}")

        # 添加路径验证和Windows路径处理
        import os
        # 处理Windows路径中的特殊字符
        executable_path = os.path.abspath(executable_path)
        LOG.info(f"规范化后的geckodriver路径: {executable_path}")

        if not os.path.exists(executable_path):
            LOG.error(f"geckodriver文件不存在: {executable_path}")
            # 尝试使用短路径格式
            short_path = None
            try:
                # Windows短路径获取方法
                import subprocess
                result = subprocess.check_output(f'for %I in ("{executable_path}") do @echo %~sI', shell=True)
                short_path = result.decode().strip()
                LOG.info(f"尝试使用短路径: {short_path}")
                if os.path.exists(short_path):
                    executable_path = short_path
                else:
                    LOG.warning(f"短路径也不存在: {short_path}")
            except Exception as e:
                LOG.warning(f"无法获取短路径: {str(e)}")

            # 如果路径仍然不存在，尝试只使用文件名（依赖环境变量）
            if not os.path.exists(executable_path):
                LOG.warning("尝试使用环境变量中的geckodriver")
                executable_path = "geckodriver.exe"
                if not os.path.exists(executable_path):
                    raise FileNotFoundError(f"geckodriver文件不存在: {webdriver_path}")

        # 设置Firefox浏览器的功能参数
        options = webdriver.FirefoxOptions()

        # 配置无头模式
        if headless:
            LOG.info("火狐浏览器开启无界面模式")
            options.add_argument('--headless')
            options.add_argument('--disable-gpu')

        # 添加额外的配置以提高兼容性
        options.set_preference("marionette.enabled", True)
        options.set_preference("webdriver.gecko.driver", executable_path)

        try:
            # 自动获取Firefox二进制文件路径
            firefox_binary = None
            # 尝试标准安装路径
            standard_paths = [
                r"D:\Program Files (x86)\Mozilla Firefox\firefox.exe",
                r"C:\Program Files\Mozilla Firefox\firefox.exe",
                r"C:\Program Files (x86)\Mozilla Firefox\firefox.exe"
            ]
            for path in standard_paths:
                if os.path.exists(path):
                    firefox_binary = path
                    break

            if firefox_binary:
                options.binary_location = firefox_binary
                LOG.info(f"使用Firefox二进制文件: {firefox_binary}")
            else:
                LOG.warning("未找到Firefox二进制文件，使用系统默认")

            # 创建Service对象（Selenium 4.x方式）或使用旧方式
            if SELENIUM_4_PLUS:
                service = FirefoxService(executable_path=executable_path) if executable_path else None
                if service:
                    driver = webdriver.Firefox(service=service, options=options)
                else:
                    driver = webdriver.Firefox(options=options)
            else:
                # Selenium 3.x 方式
                driver = webdriver.Firefox(executable_path=executable_path, options=options)
            LOG.info("Firefox浏览器驱动初始化成功")
        except WebDriverException as e:
            LOG.error(f"Firefox驱动初始化失败: {str(e)}")
            LOG.error(f"请检查geckodriver版本是否与Firefox浏览器兼容，并确保驱动路径正确: {executable_path}")
            raise
        
    elif browser == "Ie":  # 配置IE浏览器驱动
        LOG.info("IE浏览器启动中。。。")
        executable_path = webdriver_path or "IEDriverServer.exe"
        options = IEOptions()
        # 创建Service对象（Selenium 4.x方式）或使用旧方式
        if SELENIUM_4_PLUS:
            service = IEService(executable_path=executable_path) if executable_path else None
            if service:
                driver = webdriver.Ie(service=service, options=options)
            else:
                driver = webdriver.Ie(options=options)
        else:
            # Selenium 3.x 方式
            driver = webdriver.Ie(executable_path=executable_path, options=options)
        
    else:  # 不支持的浏览器类型
        raise NameError("please enter a valid type of targeting browser!")
        
    return driver


def get_element_locator(selector):
    """
    元素定位方法的二次封装
    通过逗号分隔符解析定位方式和定位值
    支持多种定位方式的简写形式
    
    :param selector: 定位表达式，格式为：定位方式,定位值
    :return: 元素定位器元组(定位方式, 定位值)
    """
    # 如果没有逗号分隔符，默认使用xpath定位
    if ',' not in selector:
        return By.XPATH, selector
        
    # 分割定位方式和定位值
    selector_list = selector.split(',', 1)
    selector_by = selector_list[0].strip()
    selector_value = selector_list[1].strip()
    
    # 根据定位方式的简写返回对应的定位器
    if selector_by == "i" or selector_by == "id":
        locator = (By.ID, selector_value)
    elif selector_by == "n" or selector_by == "name":
        locator = (By.NAME, selector_value)
    elif selector_by == "u" or selector_by == "uid" or selector_by == "AutomationId":
        locator = (MobileBy.ACCESSIBILITY_ID, selector_value)
    elif selector_by == "c" or selector_by == "class_name" or selector_by == "class name":
        locator = (By.CLASS_NAME, selector_value)
    elif selector_by == "t" or selector_by == "tap_name" or selector_by == "tap name":
        locator = (By.TAG_NAME, selector_value)
    elif selector_by == "l" or selector_by == "link_text" or selector_by == "link text":
        locator = (By.LINK_TEXT, selector_value)
    elif selector_by == "p" or selector_by == "partial_link_text" or selector_by == "partial link text":
        locator = (By.PARTIAL_LINK_TEXT, selector_value)
    elif selector_by == "x" or selector_by == "xpath":
        locator = (By.XPATH, selector_value)
    elif selector_by == "s" or selector_by == "css_selector" or selector_by == "css selector":
        locator = (By.CSS_SELECTOR, selector_value)
    else:  # 未知的定位方式，默认使用xpath
        locator = (By.XPATH, selector)
        
    return locator


def chrome_network_request(driver):
    """
    获取Chrome浏览器的网络请求记录
    
    :param driver: Chrome浏览器驱动实例
    :return: 请求记录列表
    """
    # 获取性能日志
    requests_log = driver.get_log('performance')
    requests_list = []
    
    # 遍历日志记录
    for single_log in requests_log:
        # 解析日志消息
        message = json.loads(single_log['message'])
        message_params = message['message']['params']
        request_type = message_params.get('type')
        request_message = message_params.get('request')
        
        # 只处理XHR类型的请求
        if request_type == "XHR" and request_message:
            request_data = {}
            request_id = message_params.get('requestId')
            
            # 获取请求响应内容
            try:
                content = driver.execute_cdp_cmd('Network.getResponseBody', {'requestId': request_id})
            except WebDriverException:
                continue
                
            # 解析响应体
            try:
                body = json.loads(content.get("body"))
            except json.decoder.JSONDecodeError:
                continue
                
            # 保存请求URL和响应数据
            request_data["url"] = request_message.get("url")
            request_data["response"] = body
            requests_list.append(request_data)
            
    return requests_list


if __name__ == "__main__":
    # 测试代码示例（已注释）
    # d = get_driver(mobile={'deviceName': 'iPhone 6/7/8'})
    # print(get_element_locator('//span[contains(text(), "微站列表")]'))
    pass
