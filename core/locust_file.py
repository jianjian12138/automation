import copy  # 导入copy模块，用于创建对象的深拷贝

from locust import HttpUser as RequestsHttpUser  # 从locust导入HttpUser类并命名为RequestsHttpUser，用于基于requests库的HTTP用户模拟
from locust import task, between, events  # 从locust导入任务装饰器(task)、等待时间函数(between)和事件系统(events)
from locust.contrib.fasthttp import FastHttpUser  # 从locust.contrib.fasthttp导入FastHttpUser类，用于基于geventhttpclient的高性能HTTP用户模拟
from locust.runners import MasterRunner  # 从locust.runners导入MasterRunner类，用于判断Locust运行模式
from prometheus_client.registry import REGISTRY  # 从prometheus_client.registry导入REGISTRY，用于注册Prometheus指标收集器
from core.case_context import LocustApiCaseInfo  # 从核心模块导入LocustApiCaseInfo类，用于管理Locust测试用例信息
from core.case_run import g  # 从case_run模块导入全局变量g，用于存储测试上下文
from libs.config_center import HOST_CONF  # 从配置中心模块导入HOST_CONF，获取不同环境的主机配置
from libs.data_collection import LocustCollector  # 从数据收集模块导入LocustCollector类，用于性能指标收集
from libs.file_utils import case_data_gain  # 从文件工具模块导入case_data_gain函数，用于获取测试用例数据
from main_locust import PATH, HTTP_CLIENT, RUN_ENV, IS_HOST, COLLECT  # 从main_locust模块导入全局配置：用例路径(PATH)、HTTP客户端类型(HTTP_CLIENT)、运行环境(RUN_ENV)、是否使用主机配置(IS_HOST)、是否启用指标收集(COLLECT)

locust_collect = None  # 初始化Locust指标收集器实例为None
if HTTP_CLIENT == "requests":  # 如果配置的HTTP客户端为requests
    HttpUser = RequestsHttpUser  # 使用基于requests的HttpUser类
elif HTTP_CLIENT == "geventhttpclient":  # 如果配置的HTTP客户端为geventhttpclient
    HttpUser = FastHttpUser  # 使用基于geventhttpclient的FastHttpUser类
else:
    raise NameError("不支持的 http 客户端")  # 抛出不支持的HTTP客户端异常


@events.init.add_listener  # 注册Locust初始化事件监听器
def locust_init(environment, **kwargs):  # 定义初始化事件处理函数
    # only run this on master & standalone
    if COLLECT and isinstance(environment.runner, MasterRunner):  # 如果启用指标收集且当前为Master或Standalone模式
        global locust_collect  # 声明使用全局变量locust_collect
        locust_collect = LocustCollector(environment.runner)  # 创建LocustCollector实例，传入运行器对象
        REGISTRY.register(locust_collect)  # 将收集器注册到Prometheus注册表
        locust_collect.timer_collector()  # 启动定时指标收集


@events.test_start.add_listener  # 注册测试开始事件监听器
def locust_start(**kwargs):  # 定义测试开始事件处理函数
    global locust_collect  # 声明使用全局变量locust_collect
    if isinstance(locust_collect, LocustCollector):  # 如果收集器实例有效
        locust_collect.timer_start()  # 启动收集器计时器


@events.test_stop.add_listener  # 注册测试停止事件监听器
def locust_stop(**kwargs):  # 定义测试停止事件处理函数
    global locust_collect  # 声明使用全局变量locust_collect
    if isinstance(locust_collect, LocustCollector):  # 如果收集器实例有效
        locust_collect.timer_cancel()  # 取消收集器计时器


class AutoTestUser(HttpUser):  # 定义自动测试用户类，继承自配置的HttpUser
    wait_time = between(0, 3)  # 设置任务执行间隔时间为0-3秒的随机值
    case_data = case_data_gain(PATH)  # 调用case_data_gain函数获取PATH路径下的测试用例数据
    index = 0  # 初始化用例数据索引为0

    def on_start(self):  # 测试用户开始执行时调用的初始化方法
        g.env = RUN_ENV  # 将运行环境存储到全局变量g中
        if IS_HOST:  # 如果启用主机配置
            g.host_conf = HOST_CONF[RUN_ENV]  # 从HOST_CONF获取当前环境的主机配置并存储到全局变量g中

    @task  # 使用task装饰器标记为Locust任务方法，会被Locust引擎调度执行
    def any_cases(self):  # 定义通用测试用例执行任务
        # case_content, case_data_queue = self.case_data
        # if case_data_queue:
        #     try:
        #         drive_case_variables = case_data_queue.get()  # 获取队列里的数据
        #         # LOG.info(f"队列数据{drive_case_variables}")
        #         case_content["case_variables"].update(drive_case_variables)
        #     except queue.Empty:  # 队列取空后，直接退出
        #         LOG.error("no data exist")
        #         self.environment.runner.quit()
        #         exit(0)
        #     if REPEAT:
        #         case_data_queue.put_nowait(drive_case_variables)

        case_content, drive_data = self.case_data  # 从case_data中解包测试用例内容和驱动数据
        if drive_data:  # 如果存在驱动数据
            self.index = (self.index + 1) % len(drive_data)  # 索引自增并取模，实现循环遍历
            drive_case_variables = drive_data[self.index]  # 获取当前索引的驱动数据
            case_content["case_variables"].update(drive_case_variables)  # 更新用例变量

        g.client = self.client  # 将Locust的HTTP客户端实例存储到全局变量g中
        g.locust = self  # 将当前测试用户实例存储到全局变量g中
        g.case_info = LocustApiCaseInfo(copy.deepcopy(case_content))  # 创建测试用例信息对象，深拷贝用例内容避免引用冲突
        g.case_info.run()  # 执行测试用例
