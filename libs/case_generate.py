# 导入系统模块和第三方库
import os, sys, json, base64, yaml, time, datetime, jmespath, requests, copy, traceback, json5
import pandas as pd
import urllib.parse as urlparse
from json.decoder import JSONDecodeError

# 导入项目内部模块
from libs.config_center import LOG, ENV  # 日志工具和环境配置
from libs.file_utils import TEMPORARY_PATH, clear_temporary  # 临时文件路径和清理工具

# 忽略HTTPS证书警告的注释代码
# from requests.packages.urllib3.exceptions import InsecureRequestWarning
# requests.packages.urllib3.disable_warnings(InsecureRequestWarning)

# 初始化时清理临时目录
clear_temporary()


# ------------------------------ 文件操作工具函数 ------------------------------

def dump_yaml(test_case, yaml_file):
    """将测试用例数据写入YAML文件"
    with open(yaml_file, "w", encoding="utf-8") as outfile:
        # 允许Unicode字符，禁用流式风格，缩进4空格
        yaml.dump(
            test_case, outfile, allow_unicode=True, default_flow_style=False, indent=4
        )


def dump_json(test_case, json_file):
    """将测试用例数据写入JSON/JSON5文件"
    suffix = json_file.split(".")[-1]  # 获取文件后缀
    with open(json_file, "w", encoding="utf-8") as outfile:
        if suffix == "json":
            # 标准JSON格式序列化
            my_json_str = json.dumps(test_case, ensure_ascii=False, indent=4)
        elif suffix == "json5":
            # JSON5格式序列化（支持注释和尾随逗号）
            my_json_str = json5.dumps(test_case, ensure_ascii=False, indent=4)
        
        # 确保字符串编码为UTF-8
        if isinstance(my_json_str, bytes):
            my_json_str = my_json_str.decode("utf-8")

        outfile.write(my_json_str)


def dump_xlsx(drive_data, xlsx_file=None, case_code="Sheet1"):
    """将驱动数据写入Excel文件"
    # 默认输出到临时目录
    if xlsx_file is None:
        xlsx_file = os.path.join(TEMPORARY_PATH, "test.xlsx")
    
    # 确保数据为列表格式
    if isinstance(drive_data, dict):
        drive_data = [drive_data]
    
    # Excel工作表名最大长度限制处理
    if len(case_code) > 31:
        case_code = case_code[30:]
    
    # 创建Excel写入器，禁用URL自动转换
    writer = pd.ExcelWriter(xlsx_file, engine='xlsxwriter', options={'strings_to_urls': False})
    pd.DataFrame(drive_data).to_excel(writer, sheet_name=case_code, index=False)  # 写入数据，不包含索引
    writer.save()


# ------------------------------ HAR文件解析器 ------------------------------

class HarParser:
    """从HAR(HTTP Archive)文件解析并生成测试用例"""
    def __init__(self, har_file_path, filter_str=None, exclude_str=None):
        # 验证HAR文件路径有效性
        self.har_file_path = self.ensure_file_path(har_file_path)
        # 请求过滤字符串（包含指定字符串的接口才会被解析）
        self.filter_str = filter_str
        # 请求排除字符串（默认排除静态资源和特定域名）
        self.exclude_str = exclude_str or ".js|.css|.png|.jpg|.svg|.ico|.gif|.woff2|.well-known|fs.31huiyi.com"
        self.show_response = True  # 是否包含响应数据
        self.show_cookies = True   # 是否包含Cookies
        self.case_variables = {}   # 用例级变量存储

    @staticmethod
    def ensure_file_path(path):
        """验证HAR文件路径有效性"""
        # 检查文件格式
        if not path or not path.endswith(".har"):
            LOG.error("HAR file not specified.")
            sys.exit(1)

        # 检查文件是否存在
        if not os.path.isfile(path):
            LOG.error(f"HAR file not exists: {path}")
            sys.exit(1)

        # 转换为绝对路径
        if not os.path.isabs(path):
            path = os.path.join(os.getcwd(), path)

        return path

    @staticmethod
    def load_har_log_entries(file_path):
        """加载HAR文件并返回日志条目列表"""
        with open(file_path, mode="rb") as f:
            try:
                content_json = json.load(f)  # 解析HAR JSON数据
                return content_json["log"]["entries"]  # 返回所有请求条目
            except (TypeError, JSONDecodeError) as ex:
                LOG.error(f"failed to load HAR file {file_path}: {ex}")
                sys.exit(1)
            except KeyError:
                LOG.error(f"log entries not found in HAR file: {content_json}")
                sys.exit(1)

    @staticmethod
    def x_www_form_urlencoded(post_data):
        """将字典转换为x-www-form-urlencoded格式字符串"""
        if isinstance(post_data, dict):
            return "&".join([f"{key}={value}" for key, value in post_data.items()])
        else:
            return post_data

    @staticmethod
    def convert_x_www_form_urlencoded_to_dict(post_data):
        """将x-www-form-urlencoded格式字符串转换为字典"""
        if isinstance(post_data, str):
            converted_dict = {}
            for k_v in post_data.split("&"):  # 按&分割键值对
                try:
                    key, value = k_v.split("=")  # 按=分割键和值
                except ValueError:
                    raise Exception(f"Invalid x_www_form_urlencoded data format: {post_data}")
                converted_dict[key] = urlparse.unquote(value)  # URL解码
            return converted_dict
        else:
            return post_data

    @staticmethod
    def convert_list_to_dict(origin_list):
        """将HAR格式的键值对列表转换为字典"""
        return {item["name"]: item.get("value") for item in origin_list}

    def __make_request_url(self, test_step_dict, entry_json):
        """解析请求URL和查询参数，构建测试步骤的URL和参数"""
        # 解析查询参数
        request_params = self.convert_list_to_dict(entry_json["request"].get("queryString", []))

        url = entry_json["request"].get("url")
        if not url:
            LOG.exception("url missed in request.")
            sys.exit(1)

        method = entry_json["request"].get("method")
        parsed_object = urlparse.urlparse(url)  # 解析URL components

        # 构建主机地址（支持环境变量替换）
        host = parsed_object.scheme + "://" + parsed_object.netloc
        servers = jmespath.search("*.servers", ENV)  # 从环境配置中查找服务器信息
        for server in servers:
            for key, value in server.items():
                if value == host:
                    host = f"$get_host({key})"  # 替换为环境变量引用
        test_step_dict["request"]["host.ini"] = host

        # 处理GET请求参数和路径
        if method.upper() == "GET" and request_params:
            test_step_dict["request"]["path"] = parsed_object.path
            test_step_dict["request"]["data"] = request_params
        else:
            # 非GET请求拼接查询参数到路径
            if request_params:
                test_step_dict["request"]["path"] = parsed_object.path + "?" + parsed_object.query
            else:
                test_step_dict["request"]["path"] = parsed_object.path

        # 生成步骤名称（用下划线替换路径分隔符）
        test_step_dict["step_name"] = parsed_object.path.replace("/", "_")[1:]

    @staticmethod
    def __make_request_method(test_step_dict, entry_json):
        """解析请求方法并设置到测试步骤"""
        method = entry_json["request"].get("method")
        if not method:
            LOG.exception("method missed in request.")
            sys.exit(1)

        test_step_dict["request"]["method"] = method

    def __make_request_cookies(self, test_step_dict, entry_json):
        """解析请求Cookies并设置到测试步骤"""
        if self.show_cookies:
            cookies = {}
            for cookie in entry_json["request"].get("cookies", []):
                cookies[cookie["name"]] = cookie["value"]

            if cookies:
                test_step_dict["request"]["cookies"] = cookies

    def __make_request_headers(self, test_step_dict, entry_json):
        """解析请求头并设置到测试步骤"""
        for header in entry_json["request"].get("headers", []):
            # 处理Authorization头（提取为用例变量）
            if header["name"].lower() == "authorization":
                test_step_dict["request"]["headers"]["authorization"] = "&authorization"
                self.case_variables["authorization"] = header["value"]

            # 处理Content-Type头（仅保留JSON类型）
            if header["name"].lower() == "content-type" and header["value"].startswith("application/json"):
                test_step_dict["request"]["headers"]["Content-Type"] = "application/json"

    def _make_request_data(self, test_step_dict, entry_json):
        """解析请求体数据，构建测试步骤的请求数据"""
        method = entry_json["request"].get("method")
        if method in ["POST", "PUT", "PATCH"]:  # 仅处理写操作请求
            post_data = entry_json["request"].get("postData", {})
            mime_type = post_data.get("mimeType")

            # 提取请求体数据（text和params字段互斥）
            if "text" in post_data:
                post_data = post_data.get("text")
            else:
                params = post_data.get("params", [])
                post_data = self.convert_list_to_dict(params)

            # 根据MIME类型处理数据格式
            if not mime_type:
                pass
            elif mime_type.startswith("application/json"):
                test_step_dict["request"]["headers"]["Content-Type"] = "application/json"
                try:
                    post_data = json.loads(post_data)  # 解析JSON数据
                except JSONDecodeError:
                    pass
            elif mime_type.startswith("application/x-www-form-urlencoded"):
                test_step_dict["request"]["headers"]["Content-Type"] = "application/x-www-form-urlencoded"
                post_data = self.convert_x_www_form_urlencoded_to_dict(post_data)  # 转换为字典
            else:
                # 兼容更多MIME类型
                pass

            test_step_dict["request"]["data"] = post_data

    def _make_response(self, test_step_dict, entry_json):
        """解析响应数据，构建测试步骤的响应验证部分"""
        # 解析响应头
        headers_mapping = self.convert_list_to_dict(entry_json["response"].get("headers", []))
        test_step_dict["response"]["headers"] = headers_mapping

        # 解析响应Cookies
        cookies_mapping = self.convert_list_to_dict(entry_json["response"].get("cookies", []))
        test_step_dict["response"]["cookies"] = cookies_mapping

        # 解析响应体
        resp_content_dict = entry_json["response"].get("content")
        text = resp_content_dict.get("text")
        if not text:
            return

        mime_type = resp_content_dict.get("mimeType")
        if mime_type and mime_type.startswith("application/json"):
            # 处理Base64编码内容
            encoding = resp_content_dict.get("encoding")
            if encoding and encoding == "base64":
                content = base64.b64decode(text)
                try:
                    content = content.decode("utf-8")
                except UnicodeDecodeError:
                    LOG.warning(f"failed to decode base64 content with utf-8 !")
                    return
            else:
                content = text

            # 解析JSON响应
            try:
                resp_content_json = json.loads(content)
            except JSONDecodeError:
                LOG.warning(f"response content can not be loaded as json: {content}")
                return

            # 仅保留字典类型的响应
            if not isinstance(resp_content_json, dict):
                return

            test_step_dict["response"]["data"] = resp_content_json

    @staticmethod
    def _make_validation(test_step_dict, entry_json):
        """生成响应状态码验证规则"""
        status = entry_json["response"].get("status")
        test_step_dict["validation"].append({"validation": f"$get_code()==$int({status})"})

    def _prepare_test_step(self, entry_json):
        """从单条HAR日志条目提取信息，构建测试步骤字典"""
        # 初始化测试步骤结构
        test_step_dict = {
            "step_code": "",
            "step_name": "",
            "step_variables": {},
            "before": [],
            "request": {
                "host.ini": "",
                "path": "",
                "headers": {},
                "cookies": {},
                "method": "",
                "data": {}
            },
            "response": {
                "headers": {},
                "cookies": {},
                "data": {}
            },
            "extract": [],
            "validation": [],
            "after": []
        }

        # 填充测试步骤各部分信息
        self.__make_request_url(test_step_dict, entry_json)
        self.__make_request_method(test_step_dict, entry_json)
        self.__make_request_cookies(test_step_dict, entry_json)
        self.__make_request_headers(test_step_dict, entry_json)
        self._make_request_data(test_step_dict, entry_json)
        self._make_response(test_step_dict, entry_json)
        self._make_validation(test_step_dict, entry_json)

        # 是否显示响应数据
        if not self.show_response:
            test_step_dict.pop("response")
        return test_step_dict

    def _prepare_test_steps(self):
        """解析HAR文件中的所有日志条目，生成测试步骤列表"""
        def is_exclude(url, exclude_str):
            """检查URL是否需要排除"""
            exclude_str_list = exclude_str.split("|")
            for exclude_str in exclude_str_list:
                exclude_str = exclude_str.strip()
                if exclude_str and exclude_str in url:
                    return True
            return False

        test_steps = []
        log_entries = self.load_har_log_entries(self.har_file_path)
        for entry_json in log_entries:
            url = entry_json["request"].get("url")
            method = entry_json["request"].get("method")

            # 应用过滤规则
            if self.filter_str and self.filter_str not in url:
                continue
            if is_exclude(url, self.exclude_str):
                continue
            if method.upper() in ["OPTIONS"]:  # 排除OPTIONS请求
                continue

            # 生成测试步骤
            test_step_dict = self._prepare_test_step(entry_json)
            test_steps.append(test_step_dict)

        # 为测试步骤编号
        for index, step in enumerate(test_steps):
            step["step_code"] = str(index + 1)

        return test_steps

    def _make_test_case(self):
        """从HAR文件提取信息，构建完整测试用例"""
        LOG.info("Extract info from HAR file and prepare for test_case.")

        test_case = {
            "case_code": str(int(round(time.time() * 1000))),  # 用时间戳作为用例编码
            "case_name": datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'),  # 用当前时间作为用例名称
            "priority": 3,  # 优先级（3为中）
            "case_variables": self.case_variables,  # 用例级变量
            "steps": self._prepare_test_steps()  # 测试步骤列表
        }

        return test_case

    def gen_test_case(self, file_type="JSON"):
        """生成测试用例文件"""
        LOG.info(f"Start to generate test_case from {self.har_file_path}")
        har_file = os.path.splitext(self.har_file_path)[0]  # 获取HAR文件的基础路径

        try:
            test_case = self._make_test_case()
        except Exception as ex:
            raise

        # 根据文件类型生成不同格式的用例文件
        if file_type == "JSON":
            output_test_case_file = f"{har_file}.json"
            dump_json(test_case, output_test_case_file)
        elif file_type == "YAML":
            output_test_case_file = f"{har_file}.yml"
            dump_yaml(test_case, output_test_case_file)
        else:
            raise  # 不支持的文件类型

        LOG.info(f"generated test_case: {output_test_case_file}")


// ... existing code ...

class YApiParser:
    """从YApi接口文档生成测试用例"""
    def __init__(self):
        self.yapi_host = "https://mock.31huiyi.com"  # YApi服务器地址
        # YApi访问令牌（不同项目令牌不同）
        self.yapi_token = "b02f6759da1123f0c72bf43da6c4bd2456011d0dcda71bbd3c24d22b2875307b"

    def request(self, api, data=None, method="get"):
        """请求YApi接口，带重试机制"""
        data = data or {}
        if isinstance(data, dict):
            data["token"] = self.yapi_token  # 添加认证令牌

        retry = True
        while retry:
            if method == "get":
                # 发送GET请求
                r = requests.get(self.yapi_host + api, params=data)
                LOG.info(f"请求接口：{api}，请求数据：{data}，返回数据：{r.text}")
            else:
                raise  # 仅支持GET请求

            # 检查响应状态码
            if r.status_code == 200:
                return r
            else:
                LOG.info("接口返回异常，等待5秒继续尝试")
                time.sleep(5)

    def make_json(self, data):
        """将YApi的JSON Schema转换为测试数据模板"""
        if isinstance(data, dict):
            t = data["type"]
            if t == "object":
                result = {}
                properties = data["properties"]
                for key, value in properties.items():
                    result[key] = self.make_json(value)  # 递归处理嵌套对象
            elif t == "array":
                result = []
                items = data["items"]
                result.append(self.make_json(items))  # 递归处理数组元素
            elif t == "string":
                result = "string"  # 字符串类型占位符
            elif t == "number":
                result = "number"  # 数字类型占位符
            elif t == "integer":
                result = "integer"  # 整数类型占位符
            elif t == "boolean":
                result = "boolean"  # 布尔类型占位符
            else:
                raise TypeError(f"{t} 类型未处理！")
        elif isinstance(data, list):
            result = {}
            for field in data:
                value = field.get("value")
                if value:
                    result[field["name"]] = field["value"]
                else:
                    result[field["name"]] = "string"
        else:
            raise TypeError(f"{data} 类型为 {type(data)}，非 list 与 dict ！")
        return result

    def make_request_json_body(self, interface_info):
        """构建请求体JSON模板"""
        res_body_type = interface_info["res_body_type"]
        if res_body_type == "json":
            method = interface_info["method"]
            if method == "GET":
                data = interface_info["req_query"]  # GET请求参数
            else:
                data = json.loads(interface_info["req_body_other"])  # 请求体数据
        else:
            raise TypeError(f"{res_body_type} 返回值类型未处理！")

        result = self.make_json(data)
        return result

    def make_headers(self, req_headers):
        """构建请求头字典"""
        headers = {}
        for header in req_headers:
            headers[header["name"]] = header["value"]
        return headers

    def make_response_json_body(self, res_body):
        """构建响应体JSON模板"""
        data = json.loads(res_body)
        return self.make_json(data)

    def get_api_data(self, yapi_cat_name=None, yapi_api_name=None):
        """从YApi获取接口数据，支持按分类名和接口名过滤"""
        # 获取接口列表
        response = self.request("/api/interface/list_menu").json()
        
        # 按分类名过滤
        if yapi_cat_name:
            cat_list_menu = jmespath.search(f"data[?name==`{yapi_cat_name}`].list | [0]", response)
        else:
            cat_list_menu = []
            cat_list = jmespath.search("data[*].list", response)
            for i in cat_list:
                cat_list_menu += i
        
        # 按接口名过滤
        if yapi_api_name:
            interface_id_list = jmespath.search(f"[?title==`{yapi_api_name}`]._id", cat_list_menu)
        else:
            interface_id_list = jmespath.search("[*]._id", cat_list_menu)
        
        # 获取接口详情
        api_data_list = []
        for interface_id in interface_id_list:
            api_data = {}
            interface_info = self.request("/api/interface/get", {"id": interface_id}).json()["data"]
            api_data["headers"] = self.make_headers(interface_info["req_headers"])
            api_data["request"] = self.make_request_json_body(interface_info)
            api_data["response"] = self.make_response_json_body(interface_info["res_body"])
            api_data["path"] = interface_info["path"]
            api_data["method"] = interface_info["method"]
            api_data["name"] = interface_info["title"]
            api_data_list.append(api_data)

        return api_data_list


// ... existing code ...

class SwaggerParser:
    """从Swagger规范生成接口测试用例"""
    def __init__(self):
        self.swagger_url = "http://192.168.15.100:11109/swagger/v1/swagger.json"  # Swagger JSON地址
        self.swagger_data = self.request_swagger_data()  # Swagger规范数据
        self.case_host = "$get_host(gateway)"  # 接口域名（环境变量引用）
        self.api_path = ""  # 接口路径
        self.case_method = ""  # 请求方法
        self.case_data = {}  # 请求数据模板
        self.case_response = {}  # 响应数据模板

    def request_swagger_data(self):
        """请求Swagger JSON数据"""
        data = requests.get(self.swagger_url).json()
        return data

    def make_json_drive(self, data):
        """将Swagger的JSON Schema转换为测试数据驱动模板"""
        if isinstance(data, dict):
            ref = data.get('$ref')  # 处理引用类型
            all_of = data.get('allOf')  # 处理组合类型
            if ref:
                result = self.ref_data(ref)
            elif all_of:
                result = ""
                for i in all_of:
                    result = self.make_json_drive(i)
            else:
                t = data["type"]
                if t == "object":
                    result = {}
                    properties = data.get("properties", {})
                    for key, value in properties.items():
                        if key == "children":  # 特殊处理递归结构
                            result[key] = None
                        else:
                            result[key] = self.make_json_drive(value)
                elif t == "array":
                    result = []
                    items = data["items"]
                    result.append(self.make_json_drive(items))
                elif t == "string":
                    result = "string"
                elif t == "number":
                    result = "number"
                elif t == "integer":
                    result = "integer"
                elif t == "boolean":
                    result = "boolean"
                else:
                    raise TypeError(f"{t} 类型未处理！")
        else:
            raise TypeError(f"类型错误，{data} 类型为 {type(data)} ！")
        return result

    def ref_data(self, ref):
        """解析Swagger中的$ref引用"""
        definition = self.swagger_data['components']['schemas'][ref.split('/')[-1]]
        all_of = definition.get("allOf")
        result = {}
        if all_of:
            for param_data in all_of:
                ref_nest = param_data.get('$ref')
                if ref_nest:
                    param = self.ref_data(ref_nest)
                else:
                    param = self.make_json_drive(param_data)
                result.update(param)
        else:
            result = self.make_json_drive(definition)
        return result

    def get_api_data(self):
        """获取接口详细信息并生成测试数据"""
        self.case_data = {}
        self.case_response = {}
        paths = self.swagger_data['paths']
        path_data = paths[self.api_path]
        
        # 解析请求方法和参数
        for method, params in path_data.items():
            self.case_method = method
            parameters = params.get("parameters", [])
            if parameters:
                for each in parameters:
                    if each.get('in') == 'query':  # 查询参数
                        name = each.get('name', "")
                        field_type = each.get('type', "")
                        self.case_data[name] = field_type
                    elif each.get('in') == 'body':  # 请求体参数
                        schema = each.get('schema')
                        if schema:
                            ref = schema.get('$ref')
                            if ref:
                                param = self.ref_data(ref)
                                self.case_data.update(param)
            
            # 解析请求体
            request_body = params.get("requestBody", {})
            if request_body:
                content = request_body.get("content", {})
                json_data = content.get("application/json", {})
                schema = json_data.get('schema')
                if schema:
                    ref = schema.get('$ref')
                    if ref:
                        param = self.ref_data(ref)
                        self.case_data.update(param)
            
            # 解析响应
            responses = params.get("responses", {})
            for code, each in responses.items():
                if code == "200":  # 只处理200响应
                    schema = each.get('schema')
                    if schema:
                        ref = schema.get('$ref')
                        if ref:
                            response = self.ref_data(ref)
                            self.case_response.update(response)


// ... existing code ...

class SwaggerAllParser(SwaggerParser):
    """从Swagger生成全部接口的正常场景测试用例"""
    def __init__(self):
        super(SwaggerAllParser, self).__init__()
        # 接口分组名称映射
        self.api_name_dict = {
            "/api/Agg/": "聚合接口",
            "/api/Account/": "账号",
            "/api/AccountGroup/": "账号分组",
            "/api/Data/": "数据",
            "/api/Department/": "部门",
            "/api/Login/": "登陆",
            "/api/Organization/": "账号",
            "/api/Permission/": "权限",
            "/api/Role/": "角色",
            "/api/User/": "用户",
            "/api/App/": "应用",
            "/api/Car": "汽车",
            "/api/Program/": "程序",
            "/api/Resource/": "资源",
            "/api/Template/": "模板",
            "/api/Tenant/": "Tenant"
        }

    def suite_name(self, api_path):
        """根据接口路径获取测试套件名称"""
        for k, v in self.api_name_dict.items():
            if k in api_path:
                return v

    def make_test_case(self):
        """构建测试用例结构"""
        case_code = self.api_path.replace("/", "_")[5:]
        test_case = {
            "case_code": case_code,
            "case_name": case_code,
            "priority": 3,
            "case_variables": {},
            "steps": [{
                "step_code": self.api_path.replace("/", "_")[1:],
                "step_name": case_code,
                "step_variables": {},
                "before": [],
                "request": {
                    "host.ini": self.case_host,
                    "path": self.api_path[4:],
                    "headers": {
                        "Content-Type": "application/json",
                        "x-tenantId": "31",
                        "authorization": "&authorization"
                    },
                    "cookies": {},
                    "method": self.case_method.upper(),
                    "data": self.case_data
                },
                "response": self.case_response,
                "extract": [],
                "validation": [
                    {"validation": "$get_code()==$int(200)"},
                    {"validation": "$get_jmespath($get_response(), sysMessage)==成功"},
                    {"validation": "$get_jmespath($get_response(), isSuccess)==&isSuccess"}
                ],
                "after": []
            }]
        }
        return test_case

    def case_parser(self):
        """解析所有接口并生成测试用例文件"""
        paths = self.swagger_data["paths"]
        for api_path in paths.keys():
            if api_path in ["/api/Department/v1/tree"]:  # 跳过特定接口
                continue
            self.api_path = api_path
            self.get_api_data()
            test_case = self.make_test_case()
            case_code = test_case["case_code"]
            name = self.suite_name(api_path)
            suite = os.path.join(TEMPORARY_PATH, name)
            if not os.path.exists(suite):
                os.mkdir(suite)  # 创建测试套件目录
            case_file = os.path.join(suite, f"{case_code}.json")
            dump_json(test_case, case_file)  # 保存测试用例

    def path_data(self):
        """生成接口路径数据"""
        data = []
        paths = self.swagger_data["paths"]
        for api_path in paths.keys():
            data.append({"path": api_path})
        dump_xlsx(data)


// ... existing code ...

class SwaggerSingleParser(SwaggerParser):
    """从Swagger生成单个接口的详细测试用例（含异常场景）"""
    def __init__(self):
        super(SwaggerSingleParser, self).__init__()
        self.swagger_url = "http://192.168.15.100:11109/swagger/v1/swagger.json"
        self.case_type = "创建"  # 用例类型：创建/列表/删除/更新/详情
        self.database_verify = False  # 是否启用数据库验证
        self.api_name = "创建订单"  # 接口名称
        self.api_path = "/api/order/v1/create"  # 接口路径
        self.xlsx_data = {"case_name": "", "isSuccess": "", "sysMessage": ""}  # Excel驱动数据
        self.case_name_xlsx_data = []  # 用例名称列表

    def case_name_list(self):
        """生成用例名称列表"""
        self.case_name_xlsx_data.append({"case_name": f"{self.api_name}，数据正确性验证(全字段覆盖)"})
        for field, v in self.xlsx_data.items():
            if field not in ["case_name", "isSuccess"]:
                # 空值验证
                self.case_name_xlsx_data.append({"case_name": f"{self.api_name}，字段{field}为空验证"})
                # 枚举类型验证
                if "type" in field.lower() or field in ["status", "source", "gender"]:
                    self.case_name_xlsx_data.append({"case_name": f"{self.api_name}，字段{field}存在的枚举验证"})
                    self.case_name_xlsx_data.append({"case_name": f"{self.api_name}，字段{field}不存在的枚举验证"})
                else:
                    # 长度验证（非ID字段）
                    if "Id" not in field:
                        self.case_name_xlsx_data.append({"case_name": f"{self.api_name}，字段{field}最大长度验证，长度50"})
                        self.case_name_xlsx_data.append({"case_name": f"{self.api_name}，字段{field}最大长度+1验证，长度51"})
        
        # 生成用例名称Excel
        xlsx_file = os.path.join(TEMPORARY_PATH, f"{self.api_name}用例名称.xlsx")
        dump_xlsx(self.case_name_xlsx_data, xlsx_file)

    def request_data(self, data):
        """生成请求数据（支持参数化标记）"""
        if isinstance(data, dict):
            result = {}
            for k, v in data.items():
                value = self.request_data(v)
                if value == "&":
                    result[k] = f"&{k}"  # 参数化标记
                    self.xlsx_data[k] = ""
                else:
                    result[k] = self.request_data(v)
        elif isinstance(data, list):
            result = []
            for v in data:
                result.append(self.request_data(v))
        else:
            result = "&"
        return result

    def make_test_case(self):
        """生成测试用例"""
        case_data = self.request_data(self.case_data)
        case_code = self.api_path.replace("/", "_")[5:]
        test_case = {
            "case_data": f"{self.api_name}用例数据",
            "case_code": case_code,
            "case_name": self.api_name,
            "priority": 3,
            "case_variables": {},
            "steps": [{
                "step_code": self.api_path.replace("/", "_")[1:],
                "step_name": self.api_name,
                "step_variables": {},
                "before": [],
                "request": {
                    "host.ini": self.case_host,
                    "path": self.api_path,
                    "headers": {
                        "Content-Type": "application/json",
                        "x-accountId": "3b450000-00f0-5254-27f5-08d6e8b65998",
                        "x-name": "admin"
                    },
                    "cookies": {},
                    "method": self.case_method.upper(),
                    "data": case_data
                },
                "extract": [],
                "validation": [
                    {"validation": "$get_code()==$int(200)"},
                    {"validation": "$get_jmespath($get_response(), sysMessage)==成功"},
                    {"validation": "$get_jmespath($get_response(), isSuccess)==&isSuccess"}
                ],
                "after": []
            }]
        }

        # 数据库验证逻辑
        if self.database_verify:
            test_case.pop("case_data")
            test_case["case_code"] = f"{test_case.get('case_code')}_database"
            self.api_name = f"{self.api_name}-数据库数据验证"
            test_case["case_name"] = self.api_name
            test_case["case_variables"] = self.xlsx_data
            for i in test_case["steps"]:
                i["validation"][2] = {"validation": "$get_jmespath($get_response(), isSuccess)==$bool(true)"}
                i["step_variables"]["sql"] = ""
                if self.case_type in ["创建", "更新", "详情"]:
                    i["extract"].append({"database_data": "$get_jmespath($get_sql(&sql), [0])"})
                    if self.case_type in ["详情"]:
                        i["extract"].append({"return_data": "$get_jmespath($get_response(), returnObj)"})
                elif self.case_type in ["删除"]:
                    i["extract"].append({"sql_data": "$get_sql(&sql)"})
                    i["validation"].append({"validation": "&sql_data==[]"})
        else:
            # 异常场景处理
            if self.case_type in ["删除", "详情"]:
                test_case["steps"].append(copy.deepcopy(test_case["steps"][0]))
                test_case["steps"][0]["step_code"] = f"{test_case.get('case_code')}_empty"
                test_case["steps"][0]["step_name"] = f"{test_case.get('case_name')}-Id为空"
                test_case["steps"][1]["step_code"] = f"{test_case.get('case_code')}_not_exist"
                test_case["steps"][1]["step_name"] = f"{test_case.get('case_name')}-Id为不存在的值"
                if self.case_type in ["删除"]:
                    for i in test_case["steps"]:
                        i["validation"][2] = {"validation": "$get_jmespath($get_response(), isSuccess)==$bool(false)"}
                if self.case_type in ["详情"]:
                    for i in test_case["steps"]:
                        i["validation"][2] = {"validation": "$get_jmespath($get_response(), isSuccess)==$bool(true)"}
                        i["validation"].append({"validation": "$get_jmespath($get_response(), returnObj)==$none()"})
                test_case["case_name"] = f"{self.api_name}-异常情况"
            
            # 生成用例数据Excel
            if self.case_type in ["创建", "更新"]:
                self.case_name_list()
                for i in test_case["steps"]:
                    i["validation"][1] = {"validation": "$get_jmespath($get_response(), sysMessage)==&sysMessage"}
            case_code = test_case["case_code"]
            xlsx_file = os.path.join(TEMPORARY_PATH, f"{self.api_name}用例数据.xlsx")
            dump_xlsx(self.xlsx_data, xlsx_file, case_code)

        # 保存测试用例文件
        case_file = os.path.join(TEMPORARY_PATH, f"{self.api_name}.json")
        dump_json(test_case, case_file)
        return test_case


// ... existing code ...

class ApiContrast:
    """接口数据对比工具（支持自动合并接口变更）"""
    def __init__(self, is_merge):
        self.is_merge = is_merge  # True:合并变更 False:仅打印差异

    def contrast(self, case_path, request_api_path, new_request_data):
        """对比用例文件与新接口数据的差异"""
        for root, dirs, files in os.walk(case_path):
            for file in files:
                suffix = file.split(".")[-1]
                if suffix == "json" or suffix == "json5":
                    json_file_path = os.path.abspath(os.path.join(root, file))
                    with open(json_file_path, 'r', encoding="utf-8") as f:
                        case_content = json5.loads(f.read())
                        steps = case_content.get("steps", [])
                        for step in steps:
                            path = step.get("request", {}).get("path", "")
                            if path == request_api_path:
                                request_data = step.get("request", {}).get("data", {})
                                # 计算差异
                                diff_data = json_tools.diff(request_data, new_request_data)
                                diff_result = []
                                for i in diff_data:
                                    if "remove" in i or "add" in i:
                                        diff_result.append(i)
                                LOG.info(f"用例路径: {json_file_path} \n接口: {path}, 发送变化的字段: {diff_result}")
                                # 合并变更
                                if self.is_merge:
                                    step["request"]["data"] = self.diff_merge(diff_result, request_data)
                                    dump_json(case_content, json_file_path)

    def diff_merge(self, diff_data, request_data):
        """合并差异数据到现有请求数据"""
        for field in diff_data:
            field_keys = field.keys()
            if "add" in field_keys:
                add_field = field["add"][1:].rsplit("/")
                value = field["value"]
                exec(f"request_data{self.field_path(add_field)} = value")
            elif "remove" in field_keys:
                remove_field = field["remove"][1:].rsplit("/")
                exec(f"del request_data{self.field_path(remove_field)}")
        return request_data

    @staticmethod
    def field_path(field):
        """生成字段路径表达式"""
        v = ""
        for i in field:
            if i.isdigit():
                v += f"[{i}]"
            else:
                v += f"['{i}']"
        return v


class CaseGenerate:
    """用例生成工具类"""
    def __init__(self):
        self.case_name_list = []
        self.name = ""
        self.method = ""
        self.path = ""

    def case_name_generate(self, api_data_list):
        """根据API数据生成用例名称列表"""
        for api_data in api_data_list:
            self.name = api_data["name"]
            self.method = api_data["method"]
            self.path = api_data["path"]
            request = api_data["request"]
            case_name = self.make_case_name(request, [])
            self.case_name_list += case_name
        
        # 去重并生成Excel
        l = []
        for case_name in self.case_name_list:
            l.append({"case_name": case_name})
        dump_xlsx(l)

    def make_case_name(self, data, case_name: list):
        """递归生成用例名称"""
        if isinstance(data, dict):
            # 添加基础用例
            name1 = f"{self.name}{self.path}，基础功能验证(全字段覆盖)"
            name2 = f"{self.name}{self.path}，数据库数据验证"
            if name1 not in case_name:
                case_name.append(name1)
            if name2 not in case_name:
                case_name.append(name2)
            
            # 为每个字段生成用例
            for key, value in data.items():
                if isinstance(value, str):
                    case_name.append(f"{self.name}{self.path}，字段{key}为空验证")
                    if value == "string":
                        if "id" == key[-2:].lower():
                            pass
                        else:
                            case_name.append(f"{self.name}{self.path}，字段{key}最大长度验证")
                            case_name.append(f"{self.name}{self.path}，字段{key}最大长度+1验证")
                    elif value == "integer":
                        if "type" in key.lower():
                            case_name.append(f"{self.name}{self.path}，字段{key}存在的枚举验证")
                            case_name.append(f"{self.name}{self.path}，字段{key}不存在的枚举验证")
                        else:
                            case_name.append(f"{self.name}{self.path}，字段{key}最大长度验证")
                            case_name.append(f"{self.name}{self.path}，字段{key}最大长度+1验证")
                    elif value == "number":
                        case_name.append(f"{self.name}{self.path}，字段{key}最大长度验证")
                        case_name.append(f"{self.name}{self.path}，字段{key}最大长度+1验证")
                    elif value == "boolean":
                        case_name.append(f"{self.name}{self.path}，字段{key}两种布尔状态验证")
                    else:
                        raise TypeError(f"键 {key} 未识别的类型 {value} ")
                else:
                    case_name = self.make_case_name(value, case_name)
        elif isinstance(data, list):
            for value in data:
                case_name = self.make_case_name(value, case_name)
        elif isinstance(data, str):
            case_name = []
            if data == "string":
                case_name.append(f"{self.name}{self.path}，字段{data}为空验证")
                if "id" == data[-2:].lower():
                    pass
                else:
                    case_name.append(f"{self.name}{self.path}，字段{data}最大长度验证")
                    case_name.append(f"{self.name}{self.path}，字段{data}最大长度+1验证")
        else:
            raise TypeError(f"未识别的类型 {data} ")
        return case_name


# 模块自测代码
if __name__ == "__main__":
    # h = HarParser(r"C:\Users\Administrator\Desktop\Untitled4.har")
    # h.gen_test_case()

    # api_json_auto()

    # y = YApiParser()
    # print(y.get_api_data("订单服务", "查询订单日志"))

    s = SwaggerSingleParser()
    s.get_api_data()
    # print(s.case_data)
    # print(s.case_response)
    s.make_test_case()

    # ss = SwaggerAllParser()
    # ss.path_data()

    # api_path = "/api/Account/v1/CreateOrUpdateCertificate"
    # d = SwaggerParser()
    # d.api_path = api_path
    # d.get_api_data()
    # print(d.case_data)
    # dd = ApiContrast(True)
    # dd.contrast("./cases_api/test_cases/业务中台/用户中心/组织", api_path, d.case_data)

    # c = CaseGenerate()
    # dd = YApiParser().get_api_data("订单服务")
    # c.case_name_generate(dd)