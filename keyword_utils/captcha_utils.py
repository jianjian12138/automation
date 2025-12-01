# 导入urllib库的request模块，用于处理URL请求
from urllib import request
# 导入matplotlib.pyplot用于图像显示
import matplotlib.pyplot as plt
# 导入numpy用于数值计算和数组操作
import numpy as np
# 导入random用于生成随机数
import random
# 导入time用于时间控制
import time
# 导入cv2(OpenCV)用于图像处理
import cv2
# 导入traceback用于异常跟踪
import traceback
# 导入selenium相关模块用于Web自动化
import selenium
# 导入base64用于Base64编码解码
import base64
# 导入os用于文件系统操作
import os
# 导入pytesseract用于OCR文字识别
import pytesseract
# 从PIL库导入Image用于图像处理
from PIL import Image
# 从io库导入BytesIO用于字节流处理
from io import BytesIO
# 从urllib.request导入urlretrieve用于下载文件
from urllib.request import urlretrieve
# 从selenium.common.exceptions导入各种异常类
from selenium.common.exceptions import *
# 从selenium.webdriver.support导入expected_conditions用于显式等待
from selenium.webdriver.support import expected_conditions as EC
# 从selenium.webdriver.common.by导入By用于元素定位
from selenium.webdriver.common.by import By
# 从selenium.webdriver.support.ui导入WebDriverWait用于等待机制
from selenium.webdriver.support.ui import WebDriverWait
# 从selenium.webdriver导入ActionChains用于鼠标操作
from selenium.webdriver import ActionChains
# 从libs.config_center导入LOG用于日志记录
from libs.config_center import LOG
# 从libs.file_utils导入temporary_file_path和images_file_path用于文件路径处理
from libs.file_utils import temporary_file_path, images_file_path
# 从core.browser_driver导入get_element_locator用于元素定位器处理
from core.browser_driver import get_element_locator


# 下载验证码图片并保存到本地
def get_image(driver, xpath):
    # 生成图片保存路径
    images_path = images_file_path() + ".png"
    # 通过XPath定位图片元素并获取src属性值
    data = driver.find_element_by_xpath(xpath).get_attribute('src')
    # 打开URL并读取图片数据
    data = request.urlopen(data, timeout=30).read()
    # 将图片数据写入文件
    f = open(images_path, 'wb')
    f.write(data)
    f.close()
    # 返回图片路径
    return images_path


# 识别图片验证码（OCR）
def image_code(driver, xpath):
    # 获取验证码图片路径
    image_path = get_image(driver, xpath)
    print(image_path)
    # 等待5秒，确保图片加载完成
    time.sleep(5)
    # 打开图片
    image = Image.open(image_path)
    # 对图片做灰度处理
    image = image.convert('L')

    # 设置二值化阈值
    threshold = 150
    table = []
    # 创建二值化映射表
    for i in range(256):
        if i < threshold:
            table.append(0)
        else:
            table.append(1)
    # 通过表格转换成二进制图片
    image = image.point(table, "1")

    # 显示图片（调试用）
    image.show()
    # 使用Tesseract进行OCR识别，去除空格
    code = "".join(pytesseract.image_to_string(image, config='--psm 6').split())
    print(type(code))
    # 返回识别结果
    return code


# 显示OpenCV图像
def cv_show(name, img):
    cv2.imshow(name, img)
    cv2.waitKey()
    cv2.destroyAllWindows()


# 读取中文路径的图片
def cv_imread(file_path, flags=cv2.IMREAD_COLOR):
    # 使用numpy和cv2.imdecode处理中文路径
    image = cv2.imdecode(np.fromfile(file_path, dtype=np.uint8), flags)
    return image


# 保存图片到中文路径
def cv_imwrite(image_path, image, params=None):
    # 使用cv2.imencode处理中文路径
    cv2.imencode('.png', image, params)[1].tofile(image_path)


# 自定义预期条件：检查元素是否可见且可用
class presence_of_element_enableds:
    def __init__(self, *args):
        self.locators = args

    @staticmethod
    def locator_presence(driver, locator):
        try:
            # 查找元素
            element = driver.find_element(*locator)

            # JavaScript代码获取元素样式
            script = """
            var s = '';
            var o = getComputedStyle(arguments[0]);
            for (var i = 0; i < o.length; i++){
                if (o[i] == 'display'){
                    s += o.getPropertyValue(o[i]);
                }
            }
            return s;
            """
            # 执行JavaScript获取display属性
            display = driver.execute_script(script, element)
            # 检查元素是否显示
            if display and "none" in display:
                return False

            # 检查元素是否可用
            if element and element.is_enabled():
                return element
            else:
                return False
        except (NoSuchElementException, WebDriverException):
            return False

    def __call__(self, driver):
        result = False
        # 遍历所有定位器
        for locator in self.locators:
            locator = get_element_locator(locator)
            result = self.locator_presence(driver, locator)
            if result:
                return result
        return False


# 自定义预期条件：检查元素是否可点击
class presence_of_element_clicks:
    def __init__(self, *args):
        self.locators = args

    def __call__(self, driver):
        # 遍历所有定位器
        for locator in self.locators:
            try:
                locator = get_element_locator(locator)
                element = driver.find_element(*locator)
                # 尝试点击元素
                element.click()
                return element
            except (NoSuchElementException, WebDriverException):
                continue
            except (ElementNotVisibleException, NoSuchElementException, ElementNotInteractableException):
                continue
        return False


# 腾讯防水墙滑动验证码破解类
class Captcha:
    """
    腾讯防水墙滑动验证码破解
    使用OpenCV库
    成功率大概90%左右：在实际应用中，登录后可判断当前页面是否有登录成功才会出现的信息：比如用户名等。循环
    https://open.captcha.qq.com/online.html
    破解 腾讯滑动验证码
    腾讯防水墙
    python + seleniuum + cv2
    """

    def __init__(self, driver, retry):
        self.driver = driver
        self.retry = retry

    @staticmethod
    def get_position(chunk, canves):
        """
        判断缺口位置
        :param chunk: 缺口图片是原图
        :param canves: 
        :return: 位置 x, y
        """
        otemp = chunk
        oblk = canves
        target = cv_imread(otemp, 0)
        template = cv_imread(oblk, 0)
        # 创建临时文件路径
        temp = temporary_file_path("temp.jpg")
        targ = temporary_file_path("targ.jpg")
        cv_imwrite(temp, template)
        cv_imwrite(targ, target)
        target = cv_imread(targ)
        # 转换为灰度图
        target = cv2.cvtColor(target, cv2.COLOR_BGR2GRAY)
        # 反色处理
        target = abs(255 - target)
        cv_imwrite(targ, target)
        target = cv_imread(targ)
        template = cv_imread(temp)
        # 使用模板匹配寻找缺口位置
        result = cv2.matchTemplate(target, template, cv2.TM_CCOEFF_NORMED)
        # 寻找最佳匹配位置
        x, y = np.unravel_index(result.argmax(), result.shape)
        return x, y

    @staticmethod
    def get_track(distance):
        """
        模拟轨迹 假装是人在操作
        :param distance: 需要移动的距离
        :return: 轨迹列表
        """
        # 初速度
        v = 50
        # 单位时间为0.2s
        t = 0.2
        # 轨迹列表
        tracks = []
        # 当前位移
        current = 0
        # 减速阈值
        mid = distance * 7 / 8

        while current < distance:
            if current < mid:
                # 加速度（加速阶段）
                a = random.randint(4, 6)
            else:
                # 减速度（减速阶段）
                a = -random.randint(5, 7)

            # 初速度
            v0 = v
            # 计算0.2秒内的位移
            s = v0 * t + 0.5 * a * (t ** 2)
            # 更新当前位移
            current += s
            # 添加轨迹
            tracks.append(round(s))

            # 更新速度
            v = v0 + a * t

        # 反方向微调
        tracks.append(int(distance - current))
        return tracks

    # 快速破解验证码
    def fast_captcha_driver(self):
        # 等待并切换到验证码iframe
        web_driver_wait = WebDriverWait(self.driver, 15, 0.5)
        web_driver_wait.until(EC.frame_to_be_available_and_switch_to_it(("id", "tcaptcha_iframe")))
        time.sleep(0.5)
        # 获取背景图片元素
        bk_block = self.driver.find_element_by_xpath('//img[@id="slideBg"]')  # 大图
        # 获取图片宽度
        web_image_width = bk_block.size
        web_image_width = web_image_width['width']
        # 获取图片x坐标
        bk_block_x = bk_block.location['x']

        # 获取滑块图片元素
        slide_block = self.driver.find_element_by_xpath('//img[@id="slideBlock"]')  # 小滑块
        slide_block_x = slide_block.location['x']

        # 获取背景图片和滑块图片的URL
        bk_block = self.driver.find_element_by_xpath('//img[@id="slideBg"]').get_attribute('src')  # 大图 url
        slide_block = self.driver.find_element_by_xpath('//img[@id="slideBlock"]').get_attribute('src')  # 小滑块 图片url
        # 获取滑块元素
        slid_ing = self.driver.find_element_by_xpath('//div[@id="tcaptcha_drag_thumb"]')  # 滑块

        # 下载背景图片和滑块图片
        bk_block_path = temporary_file_path("bkBlock.png")
        slide_block_path = temporary_file_path("slideBlock.png")
        urlretrieve(bk_block, bk_block_path)
        urlretrieve(slide_block, slide_block_path)
        time.sleep(0.5)
        # 打开图片并获取真实宽度
        img_bkblock = Image.open(bk_block_path)
        real_width = img_bkblock.size[0]
        # 计算缩放比例
        width_scale = float(real_width) / float(web_image_width)
        # 计算缺口位置
        position = self.get_position(bk_block_path, slide_block_path)
        # 根据缩放比例计算真实位置
        real_position = position[1] / width_scale
        real_position = real_position - (slide_block_x - bk_block_x)
        # 生成移动轨迹
        track_list = self.get_track(real_position)
        # 执行鼠标操作
        ActionChains(self.driver).click_and_hold(on_element=slid_ing).perform()  # 点击鼠标左键，按住不放
        time.sleep(0.2)
        # 按照轨迹移动鼠标
        for track in track_list:
            ActionChains(self.driver).move_by_offset(xoffset=track, yoffset=0).perform()
        time.sleep(1)
        # 释放鼠标
        ActionChains(self.driver).release(on_element=slid_ing).perform()
        time.sleep(1)
        try:
            # 检查是否需要重试
            self.driver.find_element_by_id("reload")
            raise WebDriverException("滑块页面还存在。。。")
        except NoSuchElementException:
            LOG.info('滑块移动完成')

    # 运行破解程序
    def run(self):
        while self.retry:
            try:
                self.fast_captcha_driver()
                break
            except Exception:
                LOG.error(traceback.format_exc())
                LOG.error("滑动异常，正在重试。。。")
                self.retry -= 1
                # 点击刷新按钮
                self.driver.find_element_by_id("reload").click()
                # 切换回默认内容
                self.driver.switch_to.default_content()


# 极验滑动验证码破解类
class CrackGee:
    def __init__(self, driver, retry):
        self.driver = driver
        self.retry = retry
        self.wait = WebDriverWait(self.driver, 15)

    # 获取滑块元素
    def get_slider(self):
        """
        获取滑块
        :return: 滑块对象
        """
        slider = self.wait.until(EC.presence_of_element_located((By.XPATH, '//div[@class="geetest_slider_button"]')))
        return slider

    # 获取验证码图片
    def get_geetest_image(self, img_name, class_name):
        """
        获取验证码图片
        :return: 图片对象
        """
        captcha_path = temporary_file_path(img_name)
        # JavaScript代码获取canvas图片数据
        js = f'return document.getElementsByClassName("{class_name}")[0].toDataURL("image/png");'
        # 执行JS代码获取图片base64数据
        im_info = self.driver.execute_script(js)
        im_base64 = im_info.split(',')[1]  # 提取base64编码部分
        im_bytes = base64.b64decode(im_base64)  # 解码为字节流
        # 保存图片到本地
        with open(captcha_path, 'wb') as f:
            f.write(im_bytes)

        return captcha_path

    @staticmethod
    def get_back_canny(gap_img, background_img):
        # 读取缺口图片和背景图片
        image2 = cv_imread(gap_img)
        image3 = cv_imread(background_img)
        # 转换为灰度图
        img2 = cv2.cvtColor(image2, cv2.COLOR_BGR2GRAY)
        img3 = cv2.cvtColor(image3, cv2.COLOR_BGR2GRAY)

        # 计算差值图像
        img4 = img3 - img2
        # 中值滤波去噪
        img4 = cv2.medianBlur(img4, 5)
        # Canny边缘检测
        ref = cv2.Canny(img4, 80, 150)
        # 形态学闭运算
        kernel = np.ones((5, 5), np.uint8)
        ref = cv2.morphologyEx(ref, cv2.MORPH_CLOSE, kernel)
        # 查找轮廓
        cnts, hierarchy = cv2.findContours(ref.copy(), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        gap_x = 60
        # 遍历轮廓找到缺口位置
        for (i, c) in enumerate(cnts):
            (x, y, w, h) = cv2.boundingRect(c)
            area = cv2.contourArea(c)
            if area > 1500:
                gap_x = x
        return gap_x

    # 移动滑块到缺口位置
    def move_to_gap(self, slider, offset):
        """
        拖动滑块到缺口处
        :param slider: 滑块
        :param offset: 轨迹
        :return:
        """
        time.sleep(0.5)
        # 按住滑块
        ActionChains(self.driver).click_and_hold(on_element=slider).perform()
        # 移动到指定位置
        ActionChains(self.driver).move_by_offset(xoffset=offset, yoffset=0).perform()
        time.sleep(2)
        # 释放滑块
        ActionChains(self.driver).release().perform()

    # 修改元素display属性
    def element_display_alter(self, class_name, value):
        js = f'document.getElementsByClassName("{class_name}")[0].style.display="{value}";'
        self.driver.execute_script(js)

    # 检查元素是否可见且可用
    def is_enabled(self, timeout, sleep, *args):
        try:
            element = WebDriverWait(self.driver, timeout, sleep).until(
                presence_of_element_enableds(*args))
            return element
        except TimeoutException:
            return False

    # 检查元素是否可点击
    def is_click(self, timeout, sleep, *args):
        try:
            element = WebDriverWait(self.driver, timeout, sleep).until(
                presence_of_element_clicks(*args))
            return element
        except TimeoutException:
            return False

    # 调整元素宽度
    def quit_width(self):
        try:
            element = WebDriverWait(self.driver, 2).until(EC.presence_of_element_located(
                (By.XPATH, '//div[@class="geetest_holder geetest_mobile geetest_ant geetest_embed"]')))
            js_str = 'arguments[0].style["width"] = "";'
            self.driver.execute_script(js_str, element)
        except TimeoutException:
            return False

    # 破解极验验证码
    def crack(self):
        # 等待验证码元素加载
        img_element = self.is_enabled(
            4, 0.5,
            '//div[@class="geetest_holder geetest_mobile geetest_ant geetest_popup"]',
            '//div[@class="geetest_holder geetest_mobile geetest_ant geetest_embed"]')
        if img_element is False:
            raise RuntimeError("滑动验证码未出现！")
        self.quit_width()
        time.sleep(0.5)

        # 获取带缺口的验证码图片
        class_name2 = 'geetest_canvas_bg geetest_absolute'
        self.element_display_alter(class_name2, "none")
        image_path2 = self.get_geetest_image('captcha2.png', class_name2)
        self.element_display_alter(class_name2, "block")

        # 获取背景验证码图片
        class_name3 = 'geetest_canvas_fullbg geetest_fade geetest_absolute'
        self.element_display_alter(class_name3, "block")
        image_path3 = self.get_geetest_image('captcha3.png', class_name3)
        self.element_display_alter(class_name3, "none")

        # 计算缺口位置
        offset = self.get_back_canny(image_path2, image_path3)
        slider = self.get_slider()
        offset -= 2  # 微调

        # 移动滑块
        self.move_to_gap(slider, offset)

        # 检查是否成功
        success_element = self.is_enabled(
            2, 0.2,
            '//div[@class="geetest_ghost_success geetest_success_animate"]',
            '//div[@class="geetest_panel_success geetest_success_animate"]')
        if success_element is False:
            # 尝试刷新
            try_element = self.is_click(1, 0.5, '//a[@class="geetest_refresh_1"]')
            new_try = self.is_click(
                1, 0.2,
                '//div[text()="请点击此处重试"]',
                '//span[text()="请点击重试"]')
            if new_try is False:
                if try_element:
                    LOG.warning("滑动验证码刷新!")
                    self.retry -= 1
                else:
                    raise RuntimeError("滑动验证码运行出错！")
            else:
                LOG.warning("滑动验证码超5次点击重试!")
                self.retry -= 1
        else:
            self.retry = 0

    # 运行破解程序
    def run(self):
        # 点击开始验证按钮
        self.is_click(1, 0.5, '//span[@class="geetest_radar_tip_content"]')
        while self.retry:
            self.crack()
        LOG.info("滑动验证码成功")


# 测试函数：Canny边缘检测
def get_back_canny():
    file_path = r"D:/work/31huiyi/QA_scripts/UI Automation Testing/autotest_elegant/files/temporary/"  # 使用正斜杠避免转义问题
    image2 = cv_imread(f"{file_path}13896-captcha2.png")
    image3 = cv_imread(f"{file_path}13896-captcha3.png")

    img2 = cv2.cvtColor(image2, cv2.COLOR_BGR2GRAY)
    img3 = cv2.cvtColor(image3, cv2.COLOR_BGR2GRAY)
    img4 = img3 - img2
    cv_show('img4', img4)
    img4 = cv2.medianBlur(img4, 5)
    cv_show('aussian', img4)
    ref = cv2.Canny(img4, 80, 150)
    cv_show('res', ref)
    kernel = np.ones((5, 5), np.uint8)
    ref = cv2.morphologyEx(ref, cv2.MORPH_CLOSE, kernel)
    cv_show('ref', ref)

    cnts, hierarchy = cv2.findContours(ref.copy(), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    cur_img = image2.copy()
    cv2.drawContours(cur_img, cnts, -1, (0, 0, 255), 1)
    cv_show('cur_img', cur_img)
    for (i, c) in enumerate(cnts):
        (x, y, w, h) = cv2.boundingRect(c)
        area = cv2.contourArea(c)
        if area > 1500:
            cv2.circle(image2, (x, y), 1, (0, 0, 255), -1)
            cv_show('image2', image2)


# 测试函数：图像边缘检测
def edict():
    file_path = r"D:/work/31huiyi/QA_scripts/UI Automation Testing/autotest_elegant/files/temporary/"
    #file_path = r"D:\work\31huiyi\QA_scripts\UI Automation Testing\autotest_elegant\files\temporary\"
    img = cv_imread(f"{file_path}captcha16.png")
    cv_show("img", img)
    img_blur = cv2.GaussianBlur(img, (3, 3), 0)
    img_gray = cv2.cvtColor(img_blur, cv2.COLOR_BGR2GRAY)
    img_canny = cv2.Canny(img_gray, 100, 200)
    cv_show("img_canny", img_canny)


# 主函数（测试用）
if __name__ == '__main__':
    from selenium import webdriver

    # 配置Chrome浏览器参数
    caps = {
        'browserName': 'chrome',
        'version': '',
        'platform': 'ANY',
        'goog:loggingPrefs': {
            'performance': 'ALL',
        },
        'goog:chromeOptions': {
            'perfLoggingPrefs': {
                'enableNetwork': True
            },
            'w3c': False,
            'extensions': [],
            'args': []
        }
    }
    # 初始化WebDriver
    driver = webdriver.Chrome(desired_capabilities=caps)
    driver.get('http://192.168.101.11/')
    time.sleep(3)
    # 点击元素（示例操作）
    elements = driver.find_element_by_xpath('//*[@id="cybersecurity"]/div[1]/div[1]/div[1]/ul/div')
    elements.click()
    time.sleep(3)
    # 识别验证码
    xpath = "//div[@class = 'el-input-group__append']/img"
    image_code(driver, xpath)
