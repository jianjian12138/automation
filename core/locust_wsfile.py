import time  # 导入time模块，用于计时和控制等待时间

import websocket  # 导入websocket模块，用于创建和管理WebSocket连接
from locust import User, task, events, constant  # 从locust导入用户类(User)、任务装饰器(task)、事件系统(events)和固定等待时间函数(constant)

# import jsonpath  # 注释：导入jsonpath模块，用于JSON数据解析（当前未启用）
from test_exp.ws_data import Qdata  # 从测试模块导入Qdata类，用于管理WebSocket测试的队列数据


def eventType_success(eventType, recvText, total_time):  # 定义事件类型成功回调函数，用于记录特定事件的性能指标
    events.request_success.fire(request_type="[RECV]",  # 触发Locust的请求成功事件
                                name=eventType,  # 事件类型名称
                                response_time=total_time,  # 响应时间（毫秒）
                                response_length=len(recvText))  # 响应数据长度


class WebSocketClient(object):  # 定义WebSocket客户端类，封装WebSocket连接和消息处理
    _locust_environment = None  # 类属性：存储Locust环境对象

    def __init__(self, host):  # 构造方法，初始化WebSocket客户端
        self.host = host  # 存储WebSocket服务端主机地址
        # 针对 WSS 关闭 SSL 校验警报
        self.ws = websocket.WebSocket()  # 创建WebSocket对象（未建立连接）

    def connect(self, burl):  # 定义连接方法，建立WebSocket连接
        start_time = time.time()  # 记录连接开始时间
        try:
            self.conn = self.ws.connect(url=burl)  # 尝试建立WebSocket连接
        except websocket.WebSocketConnectionClosedException as e:  # 捕获连接已关闭异常
            total_time = int((time.time() - start_time) * 1000)  # 计算耗时（毫秒）
            events.request_failure.fire(  # 触发请求失败事件
                request_type="[Connect]", name='Connection is already closed', response_time=total_time, exception=e)
        except websocket.WebSocketTimeoutException as e:  # 捕获连接超时异常
            total_time = int((time.time() - start_time) * 1000)  # 计算耗时（毫秒）
            events.request_failure.fire(  # 触发请求失败事件
                request_type="[Connect]", name='TimeOut', response_time=total_time, exception=e)
        else:
            total_time = int((time.time() - start_time) * 1000)  # 计算耗时（毫秒）
            events.request_success.fire(  # 触发请求成功事件
                request_type="[Connect]", name='WebSocket', response_time=total_time, response_length=0)
        return self.conn  # 返回连接对象

    def recv(self):  # 定义接收消息方法
        return self.ws.recv()  # 调用WebSocket对象的recv()方法接收消息

    def send(self, msg):  # 定义发送消息方法
        self.ws.send(msg)  # 调用WebSocket对象的send()方法发送消息


class WebsocketUser(User):  # 定义WebSocket用户基类，继承自Locust的User类
    abstract = True  # 标记为抽象类，防止Locust直接实例化此类

    def __init__(self, *args, **kwargs):  # 构造方法，初始化WebSocket用户
        super(WebsocketUser, self).__init__(*args, **kwargs)  # 调用父类构造方法
        self.client = WebSocketClient(self.host)  # 创建WebSocket客户端实例
        self.client._locust_environment = self.environment  # 将Locust环境对象赋值给客户端


class ApiUser(WebsocketUser):  # 定义具体的API测试用户类，继承自WebsocketUser
    # host = "ws://ws.xxxxx.com/"  # 注释：WebSocket服务端主机地址（当前通过动态URL配置）
    # wait_time = between(0, 3)  # 注释：任务间等待时间（随机0-3秒，当前使用固定等待时间）
    wait_time = constant(1)  # 设置任务执行间隔为固定1秒

    @task()  # 使用task装饰器标记为Locust任务，无权重参数表示默认权重1
    def pft(self):  # 定义性能测试任务方法
        que_data = Qdata.get()  # 从Qdata队列获取测试数据（包含token和GUAC_ID）
        print("tokens,guacid队列数据：",que_data[1], que_data[0], Qdata.qsize(),"-----------------------------------------------------------------------------")  # 打印队列数据和当前队列大小
        self.url = 'ws://192.168.101.11:8311/websocket-tunnel?token={}&' \  # 构造WebSocket连接URL
                   'GUAC_DATA_SOURCE=mysql&GUAC_ID={}&GUAC_TYPE=c&GUAC_WIDTH=1920&GUAC_HEIGHT=1080&GUAC_DPI=96&' \  # 连接参数：数据源、GUAC_ID、类型、分辨率、DPI
                   'GUAC_AUDIO=audio%2FL8&GUAC_AUDIO=audio%2FL16&GUAC_IMAGE=image%2Fjpeg&GUAC_IMAGE=image%2Fpng&GUAC_IMAGE=image%2Fwebp'.format(que_data[1], que_data[0])  # 连接参数：音频格式、图像格式，使用队列数据格式化URL
        self.data = {}  # 初始化请求数据字典（当前未使用）
        self.client.connect(self.url)  # 建立WebSocket连接
        print("发送的url:", self.url)  # 打印完整连接URL
        # 避免队列为空后，在取出队列数据后，重新加入队列中
        Qdata.put(que_data)  # 将取出的测试数据重新放回队列，实现循环复用

        while True:  # 进入消息收发循环
            # 消息接收计时
            start_time = time.time()  # 记录消息接收开始时间
            recv = self.client.recv()  # 接收WebSocket消息
            print(recv, que_data[1], "接收到的消息-----------------------------------------------------------")  # 打印接收到的消息和对应的token
            total_time = int((time.time() - start_time) * 1000)  # 计算消息接收耗时（毫秒）
            self.client.send(recv)  # 将接收到的消息原封不动地发送回去（模拟回声测试）
            # 为每个推送过来的事件进行归类和独立计算性能指标
            # try:
            # recv_j = json.loads(recv)  # 注释：将JSON字符串解析为字典（当前未启用）
            # eventType_s = jsonpath.jsonpath(recv_j, expr='$.eventType')  # 注释：使用jsonpath提取事件类型（当前未启用）
            # eventType_success(eventType_s[0], recv, total_time)  # 注释：触发事件类型成功回调（当前未启用）
            # except websocket.WebSocketConnectionClosedException as e:
            #     events.request_failure.fire(request_type="[ERROR] WebSocketConnectionClosedException",
            #                                 name='Connection is already closed.',
            #                                 response_time=total_time,
            #                                 exception=e)
            # except:
            #     print(recv)
            #     # 正常 OK 响应，或者其它心跳响应加入进来避免当作异常处理
            #     if 'ok' in recv:
            #         eventType_success('ok', 'ok', total_time)
