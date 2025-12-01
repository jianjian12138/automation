# 尝试导入appium webdriver
# 使用try-except处理导入错误，确保API测试可以正常运行
mobdriver = None
try:
    from appium import webdriver as mobdriver  # 从appium导入webdriver并命名为mobdriver，用于创建移动设备驱动
except ImportError:
    # 如果没有安装appium，设置mobdriver为None，并记录日志
    import logging
    logging.warning("Appium模块未安装，移动测试功能将不可用")

from selenium.webdriver.common.by import By  # 从selenium导入By类，用于指定元素定位策略
from selenium.webdriver.support.wait import WebDriverWait  # 从selenium导入WebDriverWait类，用于实现显式等待
from selenium.webdriver.support import expected_conditions as ec  # 从selenium导入expected_conditions并命名为ec，用于定义等待条件


def get_app_driver(**kwargs):  # 定义获取App驱动的函数，接受关键字参数
    # 检查appium是否已安装
    if mobdriver is None:
        raise ImportError("Appium模块未安装，无法创建移动设备驱动")
        
    # appium的安装与环境配置：https://www.jianshu.com/p/51134c2d35a5
    # appium连接android模拟器：https://www.jianshu.com/p/a4fe290dfac9
    # appium官方中文文档：https://www.kancloud.cn/testerhome/appium_docs_cn/2001596
    desired_cap = kwargs  # 将传入的关键字参数赋值给desired_cap（期望的 capabilities）
    driver = mobdriver.Remote('http://127.0.0.1:4723/wd/hub', desired_cap)  # 创建Appium远程驱动实例，连接本地Appium服务器(默认端口4723)

    return driver  # 返回创建的驱动对象


if __name__ == "__main__":  # 当模块作为主程序运行时执行以下代码
    # 检查appium是否已安装
    if mobdriver is None:
        print("Appium模块未安装，无法运行示例代码")
        exit(1)
        
    desired_caps = {"deviceName": "WindowsPC",  # 定义设备名称(此处为WindowsPC，实际移动测试需改为真实设备名)
                    "platformName": "Windows",  # 定义平台名称(此处为Windows，实际移动测试需改为Android或iOS)
                    "app": "E:\31git\6.22--new.apk"}  # 定义要测试的应用路径(APK文件路径)
    mdriver = mobdriver.Remote('http://127.0.0.1:4723/wd/hub', desired_caps)  # 创建Appium驱动实例
    element = WebDriverWait(mdriver, 5, 0.5).until(  # 创建WebDriverWait对象，设置最长等待时间5秒，轮询间隔0.5秒
        ec.presence_of_element_located((By.CLASS_NAME, "WindowsForms10.EDIT.app.0.245fb7_r6_ad1"))  # 等待条件：指定类名的元素出现
    )

    element.send_keys('15565025655')  # 向找到的元素发送文本(此处为手机号)
