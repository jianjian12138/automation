# 导入必要模块：时间处理、系统操作、HTTP请求、JSON解析和子进程管理
import time, sys, os, requests, json, subprocess
# 从配置中心导入日志对象和基础目录常量
from libs.config_center import LOG, BASE_DIR
# 导入线程模块，用于并发处理
from threading import Thread
# 导入自定义异常，用于处理Sqlmap连接错误
from libs.custom_exception import SqlmapConnectionError


class SqlMapClient:
    
    # 类属性：存储已扫描过的路径，避免重复扫描
    pass_data = []

    def __init__(self):
        # 初始化方法：配置Sqlmap API服务地址
        # 注意：使用前需手动启动sqlmapapi服务：python sqlmap/sqlmapapi.py -s
        self.server = "http://127.0.0.1:8775"

    def start_sqlmap_server(self):
        # 启动Sqlmap API服务的方法（当前未启用）
        # 注：子线程/子进程方式启动服务存在扫描请求不发送问题，建议手动启动
        time.sleep(2)

    def create_new_task(self):
        # 创建新的扫描任务
        # 发送GET请求到/task/new端点创建任务
        r = requests.get(f"{self.server}/task/new")
        # 从响应中提取taskid
        task_id = r.json()['taskid']
        # 验证任务ID是否有效
        if task_id != "":
            return task_id
        else:
            raise ValueError("SqlMap创建任务失败!")

    def start_target_scan(self, task_id, request_data):
        # 开始目标扫描
        # 初始化SSL强制标志
        force_ssl = False
        # 构建完整请求URL
        url = request_data["host"] + request_data["path"]
        # 检查是否为HTTPS请求
        if "https://" in url:
            force_ssl = True

        # 获取请求方法并标准化
        method = request_data["method"]
        if method.upper() == "GET":
            # 处理GET请求参数
            params = request_data.get("params")
            if "?" in url:
                data = ""
            else:
                data = "?"
            # 拼接查询参数
            for k, v in params.items():
                data += f"{k}={v}&"
            url = url + data[:-1]
            data = None
        else:
            # 处理非GET请求数据
            data = request_data.get("data")
        # 如果数据是字典类型，转换为JSON字符串
        if isinstance(data, dict):
            data = json.dumps(data)

        # 处理请求头
        header = request_data.get("headers")
        headers = ""
        for k, v in header.items():
            headers += f"{k}: {v}\n"

        # 处理Cookies
        cookies = request_data.get("cookies")
        if isinstance(cookies, dict):
            cookie = ""
            for k, v in cookies.items():
                cookie += f"{k}={v};"
        else:
            cookie = None

        # 生成HAR文件路径，用于保存扫描请求日志
        file_name = request_data["path"].replace("/", "_")[1:]
        har_file = os.path.join(BASE_DIR, "logs", f"{file_name}.har")

        # 构建扫描参数字典
        scan_dict = {
            "url": url,
            "method": method,
            "headers": headers,
            "data": data,
            "cookie": cookie,
            "forceSSL": force_ssl,
            "harFile": har_file,
            "optimize": True,
            "flushSession": True
        }
        # 记录扫描请求参数
        LOG.info(f"向sqlmap发送请求数据：{scan_dict}")
        # 发送扫描启动请求
        r = requests.post(f'{self.server}/scan/{task_id}/start',
                          data=json.dumps(scan_dict),
                          headers={'Content-Type': 'application/json'})
        # 验证扫描是否成功启动
        if r.json()['success']:
            return r.json()['engineid']
        else:
            raise ValueError("SqlMap任务扫描失败!")

    def get_scan_status(self, task_id):
        # 获取扫描状态
        status = requests.get(f'{self.server}/scan/{task_id}/status').json()['status']
        # 判断状态：terminated表示完成，running表示进行中
        if status == 'terminated':
            return True
        elif status == 'running':
            return False
        else:
            raise ValueError("SqlMap获取任务状态失败!")

    def get_result(self, task_id):
        # 获取扫描结果
        r = requests.get(f'{self.server}/scan/{task_id}/data')
        data = r.json()['data']
        # 如果存在扫描结果，提取关键信息
        if data:
            parameter, title, payload, url = "", "", "", ""
            for i in data:
                value = i.get("value")
                if isinstance(value, dict):
                    url = value.get("url")
                elif isinstance(value, list):
                    parameter = value[0].get("parameter")
                    title_data = [elem for elem in value[0]["data"].values()]
                    title = title_data[0].get("title").encode().decode()
                    payload = title_data[0].get("payload").encode().decode()
            result = {"url": url, "parameter": parameter, "title": title, "payload": payload}
            LOG.error(f"查询到sql注入：{result}")
            return result
        else:
            return False

    def run(self, request_data):
        # 主运行方法：协调扫描任务的创建、启动、状态检查和结果获取
        try:
            path = request_data["path"]
            # 跳过已扫描过的路径
            if path not in self.pass_data:
                self.pass_data.append(path)
                # 创建新任务
                task_id = self.create_new_task()
                LOG.info(f"sqlmap 当前任务id {task_id}，请求数据 {request_data}")
                # 启动扫描
                self.start_target_scan(task_id, request_data)
                # 设置扫描超时时间（10分钟）和轮询频率（2秒）
                timeout = 600
                poll_frequency = 2
                while timeout >= 0:
                    # 检查扫描状态
                    status = self.get_scan_status(task_id)
                    if status:
                        # 获取并返回扫描结果
                        result = self.get_result(task_id)
                        return result
                    time.sleep(poll_frequency)
                    timeout -= poll_frequency
                LOG.warning(f"sqlmap 扫描任务已进行 {timeout}s 超时停止任务，当前任务id {task_id}，请求数据 {request_data}")
                # 超时后停止扫描任务
                requests.get(f'{self.server}/scan/{task_id}/stop')
                return False
        except requests.exceptions.ConnectionError:
            # 处理Sqlmap API连接错误
            import sqlmap
            sqlmapapi_file = sqlmap.__file__.replace("__init__", "sqlmapapi")
            raise SqlmapConnectionError(f"sqlmapapi 服务未开启，请使用命令开启服务: python {sqlmapapi_file} -s")


# 单例模式实例化（当前注释掉，未启用）
# sqlmap_client = SqlMapClient()


# 模块自测代码
if __name__ == "__main__":
    s = SqlMapClient()
    # s.start_sqlmap_server()
    # 测试POST请求扫描
    d = {
        "host": "http://127.0.0.1:5000",
        "path": "/sql_map/post",
        "headers": {"token": "token", "Content-Type": "application/json"},
        "method": "POST",
        "data": {"test": "123", "data": {"id": "2", "name": "test2"}}
    }
    # 执行扫描并打印结果
    print(s.run(d))
    # time.sleep(5)
    # os.system("taskkill /t /f /pid %s" % s.sqlmap_process.pid)
    # s.process.terminate()
