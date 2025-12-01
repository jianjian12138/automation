import re, ast  # 导入正则表达式模块(re)和抽象语法树模块(ast)，用于数据解析和处理
import requests, threading, traceback, json, mimetypes, os, time, jmespath, copy  # 导入常用模块：HTTP请求(requests)、线程(threading)、异常跟踪(traceback)、JSON处理(json)、MIME类型检测(mimetypes)、文件操作(os)、时间(time)、JSON查询(jmespath)、深拷贝(copy)
from urllib.parse import urlparse  # 从urllib.parse导入urlparse，用于URL解析
from requests.cookies import RequestsCookieJar  # 从requests.cookies导入RequestsCookieJar，用于Cookie管理
from requests import Session  # 从requests导入Session，用于创建持久化的HTTP会话
from werkzeug.local import Local  # 从werkzeug.local导入Local，用于实现线程隔离的本地存储
from libs.custom_exception import AutoHttpMethodErrorException  # 从自定义异常模块导入AutoHttpMethodErrorException，用于HTTP方法错误处理
from libs.config_center import LOG  # 从配置中心模块导入LOG，用于日志记录
from requests_toolbelt.multipart.encoder import MultipartEncoder  # 从requests_toolbelt导入MultipartEncoder，用于构造multipart/form-data类型请求

from urllib3.exceptions import InsecureRequestWarning  # 从urllib3.exceptions导入InsecureRequestWarning，用于处理不安全的HTTPS请求警告

requests.urllib3.disable_warnings(InsecureRequestWarning)  # 禁用urllib3的不安全请求警告（忽略SSL证书验证错误）
request_lock = threading.Lock()  # 创建线程锁对象，用于保证HTTP请求操作的线程安全
ui_lock = threading.Lock()  # 创建UI操作线程锁对象，用于保证UI相关操作的线程安全
g = Local()  # 创建Local对象，用于存储线程隔离的全局变量


class RequestValidation:  # 定义请求验证工具类，提供HTTP请求相关的验证方法
    """ FusionHttpClient工具函数 """

    @staticmethod
    def header_validate(header: dict) -> dict:  # 静态方法：验证并处理请求头
        # 由于requests header字典中，值类型不能为int，此方法检测header，如果发现header中包含int类型值，则将值类型修改为string
        if header:  # 如果header不为空
            for i in header.keys():  # 遍历header的所有键
                if isinstance(header[i], int):  # 如果header值为int类型
                    header[i] = str(header[i])  # 将int值转换为字符串
                    # 默认为keep-alive，然而在多次访问后不能结束并回到连接池中，导致不能产生新的连接
                    # header["Connection"] = 'keep-alive'
        return header  # 返回处理后的header

    @staticmethod
    def http_method_validate(method):  # 静态方法：验证HTTP请求方法是否支持
        """请求方式的支持类型"""
        if method not in ["GET", "POST", "DELETE", "HEAD", "PUT", "PEATCH"]:  # 检查方法是否在支持列表中
            raise AutoHttpMethodErrorException  # 如果不支持，抛出自定义异常


class HttpRequests:  # 定义HTTP请求类，封装HTTP请求的发送、处理和响应解析功能
    def __init__(self, client=None, host_conf=None):  # 类构造方法
        self.client = client or Session()  # 初始化HTTP会话对象，如未提供则创建新的Session
        self.host_conf = host_conf  # 存储主机配置信息（用于IP映射等）
        self.cookies = RequestsCookieJar()  # 初始化CookieJar对象，用于管理Cookie
        self.response = None  # 初始化响应对象，用于存储最近一次请求的响应


    def request(self, method, host, path, headers=None, data=None, cookies=None, *args, **kwargs):  # 核心方法：发送HTTP请求
        data_dict = dict()  # 初始化数据字典，用于存储请求和响应信息
        host, headers = self.host_ip(host, headers)  # 处理主机IP映射和Host请求头
        headers, params, data = self.content_type_classify(method, headers, data)  # 根据Content-Type分类处理请求参数

        headers = RequestValidation.header_validate(headers)  # 验证并处理请求头
        if cookies and isinstance(cookies, dict):  # 如果提供了cookies且为字典类型
            self.cookies.update(cookies)  # 更新会话Cookie
        self.response = self.client.request(method=method,  # 发送HTTP请求
                                            url=host + path,  # 构造完整URL
                                            params=params,  # URL查询参数
                                            data=data,  # 请求体数据
                                            headers=headers,  # 请求头
                                            cookies=self.cookies,  # Cookie信息
                                            # proxies=proxies,  # 代理配置（当前未启用）
                                            verify=False,  # 禁用SSL证书验证
                                            *args, **kwargs)  # 其他可选参数
        response_text, data = self.report_optimize(self.response, data)  # 优化响应数据格式（用于报告生成）
        # data_dict["response_text"] = re.sub(r'[\n\t\r]+', ' ', str(self.response.text))  # 替换空白字符
        data_dict["method"] = method  # 记录请求方法
        data_dict["host"] = host  # 记录请求主机
        data_dict["path"] = path  # 记录请求路径
        data_dict["headers"] = headers  # 记录请求头
        data_dict["params"] = params  # 记录URL查询参数
        if params:  # 如果存在查询参数
            data_dict["url_params"] = params  # 记录解析后的URL参数
        else:
            data_dict["url_params"] = None  # 无参数时设为None
        data_dict["cookies"] = cookies  # 记录请求时传入的Cookie
        request_time_sep = self.response.elapsed.total_seconds() * 1000  # 计算请求耗时（毫秒）
        data_dict["data"] = self.response.request.body  # 记录请求体内容
        data_dict["duration_seconds"] = float('%.2f' % request_time_sep)  # 格式化请求耗时（保留两位小数）
        data_dict["status_code"] = self.response.status_code  # 记录响应状态码
        data_dict["response_cookies"] = dict(self.response.cookies)  # 记录响应中的Cookie
        data_dict["response_text"] = self.response.text  # 记录响应文本
        data_dict["response_headers"] = self.response.headers  # 记录响应头
        data_dict["request_headers"] = self.response.request.headers  # 记录实际发送的请求头
        data_dict["request_url"] = self.response.url  # 记录完整请求URL
        LOG.info("\n请求路径与接口: {request_url}; \n"  # 记录详细请求日志
                 "请求方式: {method}; \n"
                 "请求头: {request_headers}; \n"
                 "Get传入参数: {params}; \n"
                 "Get请求解析后实际参数: {url_params}; \n"
                 "请求体: {data}; \n"
                 # "请求cookies: {cookies}; "
                 "请求耗时: {duration_seconds}ms; \n"
                 # "响应状态码: {status_code}; \n"
                 # "响应headers: {response_headers}; \n"
                 # "响应cookies: {response_cookies}; \n"
                 "响应数据: {response_text};".format(**data_dict))
        self.cookies.update(self.response.cookies)
        return data_dict

    def request_retry(self, method, host, path, headers=None, data=None, cookies=None):
        data_dict = {}
        for request_num in range(5):
            data_dict = self.request(method, host, path, headers, data, cookies)
            # if self.response == "API calls quota exceeded! maximum admitted 3 per 10s.":
            if data_dict["status_code"] == 429:  # 429 Too Many Requests 表示在一定的时间内用户发送了太多的请求，即超出了“频次限制”
                LOG.warning(f"接口超频报错：{data_dict['response_text']}")
                LOG.warning(f"接口超频，正在进行等待，开始第 {request_num} 次重试")
                time.sleep(4)
            else:
                time.sleep(4)
                break
        return data_dict

    def close(self):
        self.client.close()

    def host_ip(self, host, headers):
        """配置host请求"""
        if self.host_conf:
            actual_host = urlparse(host).netloc
            if isinstance(actual_host, dict) and actual_host in self.host_conf.keys():
                ip = self.host_conf[actual_host]
                host = host.replace(actual_host, ip)
                headers["Host"] = actual_host
            else:
                host = self.host_conf
        return host, headers

    def content_type_classify(self, method, headers, data):
        """不同headers参数请求"""
        params = None
        if headers and "Content-Type" in headers:
            if method == "POST" and "application/json" in headers.get("Content-Type"):
                data = json.dumps(data)
            elif method == "POST" and "application/x-www-form-urlencoded" in headers.get("Content-Type"):
                pass
            elif method == "POST" and "multipart/form-data" in headers.get("Content-Type"):
                headers, data = self.file_parameter(headers, data)
            elif method == "GET":
                params = data
                data = None
            else:
                data = json.dumps(data)
        elif method == "GET":
            params = data
            data = None
        else:  # 不确定性，此处需要根据情况添加，默认传递json参数类型
            # headers = {"Content-Type": "application/json"}
            data = json.dumps(data)
        return headers, params, data

    @staticmethod
    def file_parameter(headers, data):
        """配置文件上传请求"""
        for key, value in data.items():
            if isinstance(value, str) and len(value) < 200 and os.path.isfile(value):
                file_type = mimetypes.guess_type(value)[0] or 'application/octet-stream'
                file_name = os.path.basename(value)
                with open(value, 'rb') as f:
                    file_data = f.read()
                data[key] = (file_name, file_data, file_type,)
            if (value or value == 0) and isinstance(data[key], tuple) is False:
                data[key] = str(data[key])
        data = MultipartEncoder(data)
        headers["Content-Type"] = data.content_type
        return headers, data

    @staticmethod
    def report_optimize(response, data):
        content_type = response.headers.get("Content-Type")
        if content_type in ["application/octet-stream", "image/png"]:
            response_text = "二进制返回体"
        else:
            response_text = response.text

        if isinstance(data, str):
            data = json.loads(data)
        elif isinstance(data, MultipartEncoder):
            data = data.fields
            for k, v in data.items():
                if isinstance(v, tuple):
                    data[k] = (v[0], v[2])
        return response_text, data

    def get_location(self):
        location = self.response.history[0].headers['Location']
        return location


class LocustHttpSession:
    def __init__(self, client, host_conf=None):
        self.client = client
        self.host_conf = host_conf

    def host_configuration(self, url, headers):
        if self.host_conf:
            actual_host = urlparse(url).netloc
            if actual_host in self.host_conf.keys():
                ip = self.host_conf[actual_host]
                url = url.replace(actual_host, ip)
                headers["Host"] = actual_host
        return url, headers


if __name__ == "__main__":
    # client1 = HttpRequests()
    patj = r"E:\HJ\测试\altest\autotest_elegant\files\images\16488.png"
    a = os.path.isfile(patj)
    print(a)
