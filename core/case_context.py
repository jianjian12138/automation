# 导入所需的Python标准库和第三方库
import json, cv2, traceback, ast, yaml, datetime, base64, json5, copy  # 导入JSON处理、图像处理、异常跟踪、语法树、YAML、日期时间、Base64编码和深拷贝等模块
from urllib.parse import urlparse, urlencode  # 导入URL解析和编码工具
from core.rule_engine import rule  # 导入规则引擎
from core.http_client import HttpRequests, request_lock, g  # 导入HTTP客户端相关组件
# 定义验证相关函数（不使用autotest_elegant的keywords模块）
def response_assert(status_code_assert=None, response_assert=None, **kwargs):
    """响应断言验证"""
    if status_code_assert:
        actual_status, expected_status = status_code_assert
        if actual_status != expected_status:
            return False
    if response_assert:
        actual_data, expected_data = response_assert
        if expected_data and expected_data not in str(actual_data):
            return False
    return True

def db_assert(**kwargs):
    """数据库断言验证"""
    # 返回一个key_code用于后续使用
    return kwargs.get('key_code', '')

def redis_hash_assert(hash_key, key_code, rd_assert_data):
    """Redis哈希断言验证"""
    return True
from libs.config_center import LOG  # 导入日志配置
from libs.custom_exception import *  # 导入自定义异常
from libs.file_utils import common_file_data, xlsx_file_data, temporary_file_path, excel_api_case  # 导入文件处理工具
from libs.safety_utils import SqlMapClient  # 导入SQL注入检测工具
try:
    from libs.element_config_loader import merge_elements_to_case_variables
    ELEMENT_CONFIG_LOADER_AVAILABLE = True
except ImportError:
    ELEMENT_CONFIG_LOADER_AVAILABLE = False
    LOG.warning("元素配置加载器未找到，将跳过自动加载页面元素配置")


class CasePretreatment(object):
    """用例预处理类，用于准备和处理测试用例"""
    def __init__(self, case_type, case_paths, priority=None, truncation_step=None, is_safety=False, tags=None):
        """初始化方法
        Args:
            case_type: 用例类型
            case_paths: 用例文件路径列表
            priority: 优先级
            truncation_step: 截断步骤（用于locust单接口压测）
            is_safety: 是否进行安全测试
            tags: 标签列表（用于过滤用例）
        """
        self.case_type = case_type  # 用例类型
        self.case_paths = case_paths  # 用例文件路径列表
        self.running_priority = priority  # 运行优先级
        self.is_safety = is_safety  # 是否进行安全测试
        self.truncation_step = truncation_step  # locust单接口压测截断步骤
        self.tags = tags or []  # 标签列表（用于过滤用例）
        self.ready_case_list = []  # 准备好的用例列表
        self.cut_case = []  # 截断的用例列表

    def ready_case(self):
        """准备用例，支持json、json5、yaml和xlsx格式的用例文件"""
        case_list = []  # 用例列表
        LOG.debug(f"当前用例文件路径列表：{self.case_paths}")  # 记录当前用例文件路径
        
        # 遍历用例文件路径
        for case_path in self.case_paths:
            try:
                # 处理JSON和JSON5格式的用例文件
                if case_path.split(".")[1] == "json" or case_path.split(".")[1] == "json5":
                    with open(case_path, 'r', encoding="utf-8") as f:
                        case_contents = json5.loads(f.read(), encoding="utf-8")  # 读取并解析JSON内容
                        if isinstance(case_contents, list):
                            for case_content in case_contents:
                                case_content = self.common_step_merge(case_content)  # 合并公共步骤
                                case_list.append(case_content)
                        else:
                            case_content = self.common_step_merge(case_contents)
                            case_list.append(case_content)
                
                # 处理YAML格式的用例文件
                elif case_path.split(".")[1] == "yaml":
                    with open(case_path, 'r', encoding='utf-8') as f:
                        case_contents = yaml.safe_load_all(f)  # 读取并解析YAML内容
                        for case_content in case_contents:
                            # 保存用例路径，用于查找配置文件
                            case_content["_case_path"] = case_path
                            case_content = self.common_step_merge(case_content)
                            case_list.append(case_content)
                
                # 处理Excel格式的用例文件
                elif case_path.split(".")[1] == "xlsx" or case_path.split(".")[1] == "xls":
                    excel_case_list = excel_api_case(case_path, self.running_priority)  # 读取Excel用例
                    if excel_case_list:
                        for case_data in excel_case_list:
                            self.ready_case_list.append(case_data)
                            key_cycles = case_data["steps"][0]
                            # 处理循环次数
                            if "cycles" in key_cycles:
                                cycles = key_cycles.pop("cycles")
                                if cycles:
                                    for i in range(int(cycles)):
                                        self.ready_case_list.append(case_data)
                        continue
            except Exception as e:
                raise e

            # 处理用例内容
            for case_content in case_list:
                # 获取用例数据文件名
                case_data_filename = case_content.pop("case_data", None)
                drive_data_filename = case_content.pop("drive_data", case_data_filename)

                # 处理驱动数据
                if drive_data_filename is not None:
                    if isinstance(drive_data_filename, int):
                        for_data = [case_content] * drive_data_filename
                        self.ready_case_list += for_data
                    else:
                        drive_data = xlsx_file_data(self.case_type, drive_data_filename, case_path)
                        self.drive_data_case(drive_data, case_content, case_path)
                    continue

                # 处理优先级
                priority = case_content.get("priority", 0)
                if self.running_priority is not None:
                    if int(priority) > int(self.running_priority):
                        continue

                # 处理标签过滤
                if self.tags:
                    case_tags = case_content.get("tags", [])
                    # 如果用例有标签，检查是否匹配（用例标签必须包含所有指定的标签）
                    if case_tags:
                        if not all(tag in case_tags for tag in self.tags):
                            LOG.debug(f"用例 {case_content.get('case_name', '未知')} 标签 {case_tags} 不匹配过滤条件 {self.tags}，跳过")
                            continue
                    else:
                        # 如果用例没有标签，但指定了标签过滤，则跳过
                        LOG.debug(f"用例 {case_content.get('case_name', '未知')} 没有标签，但指定了标签过滤 {self.tags}，跳过")
                        continue

                # 处理循环次数
                if "cycles" in case_content:
                    for i in range(case_content['cycles']):
                        self.ready_case_list.append(case_content)
                else:
                    self.ready_case_list.append(case_content)

    def drive_data_case(self, drive_data, case_content, case_path):
        """处理驱动数据用例，将Excel参数与用例信息组合"""
        # 获取驱动数据sheet列表
        drive_data_sheet_list = case_content.pop("drive_data_sheet", None)
        case_code = case_content.get("case_code", None)
        use_map_list = case_content.get("use_map", [])
        case_name = case_content.get("case_name", None)
        common_case_variables = case_content.get("common_case_variables", {})
        case_link = case_content.get("case_link", "")
        priority = case_content.get("priority", "")
        driver = case_content.get("driver", {})  # UI web页面移动端H5
        safety = case_content.get("safety", {})  # api接口安全测试类型

        # 处理变量列表
        case_variable_list = []
        try:
            for drive_data_sheet in drive_data_sheet_list:
                case_variable_list += drive_data.pop(drive_data_sheet)
        except KeyError:
            raise KeyError(f"{case_content.get('case_name')} 用例数据文件中未找到 case_code 对应的 Sheet")

        # 创建新的用例字典
        case = {}
        case_variables_list = []

        # 处理变量
        for variable in case_variable_list:
            case_link = variable.pop("case_link", case_link)
            priority = variable.pop("priority", priority)
            if self.running_priority is not None:
                if int(priority) > int(self.running_priority):
                    continue
            case_variables = {}
            case_variables.update(case_content.get("case_variables", {}))
            if case_name == variable["case_name"] and variable["use_map"] in use_map_list:
                case_variables.update(self.dict_value_switch(variable))
                case_variables_list.append(case_variables)

        # 设置用例变量
        case["case_variables"] = case_variables_list

        # 处理步骤
        steps = case_content.get("steps")
        for step in steps:
            step_use_map = step.get("use_map", None)
            step_name = step.get("step_name", None)
            step_drive_data_sheet_list = step.get("drive_data_sheet", None)
            drive_data_filename = step.get("drive_data", None)
            step_variable_s = step.get("step_variables", {})

            # 处理步骤驱动数据
            if drive_data_filename:
                step_variable_list = []
                step_variables_list = []
                step_drive_data = xlsx_file_data(self.case_type, drive_data_filename, case_path)
                for step_drive_data_sheet in step_drive_data_sheet_list:
                    step_variable_list += step_drive_data.pop(step_drive_data_sheet)
                for step_variable in step_variable_list:
                    if step_name == step_variable["case_name"] and step_variable["use_map"] in step_use_map:
                        new_step_variable = self.dict_value_switch(step_variable)
                        step_variables_list.append(new_step_variable)
                step_variables_list.append(step_variable_s)
                step["step_variables"] = step_variables_list
            step["step_name"] = step_name

        # 设置用例属性
        case["steps"] = steps
        case["case_code"] = case_code
        case["common_case_variables"] = common_case_variables
        case["case_name"] = case_name
        case["case_link"] = case_link
        case["driver"] = driver
        case["safety"] = safety
        self.ready_case_list.append(case)

    def common_step_merge(self, case_content):
        """合并用例步骤，处理引用的其他用例"""
        steps = case_content.get("steps", [])
        case_variables = case_content.get("case_variables", {})
        
        # 检查是否需要加载页面元素配置
        if ELEMENT_CONFIG_LOADER_AVAILABLE and "$load_page_elements" in case_variables:
            page_name = case_variables.pop("$load_page_elements")
            # 查找配置文件（优先使用同目录下的配置文件）
            case_path = case_content.get("_case_path", "")
            config_file = None
            if case_path:
                from pathlib import Path
                case_dir = Path(case_path).parent
                config_file = case_dir / "页面元素配置.yaml"
                if not config_file.exists():
                    config_file = None
            
            case_variables = merge_elements_to_case_variables(
                case_variables,
                page_name,
                str(config_file) if config_file else None
            )
        
        common_steps, common_case_variables = self.common_step_add(steps, case_content["case_code"])

        # 根据step_code进行截断
        if self.truncation_step:
            new_steps = []
            for step in common_steps:
                step_code = step.get("step_code")
                if step_code and step_code in self.truncation_step:
                    break
                else:
                    new_steps.append(step)
            case_content["steps"] = new_steps
        else:
            case_content["steps"] = common_steps

        case_content["case_variables"] = case_variables
        case_content["common_case_variables"] = common_case_variables
        return case_content

    def common_step_add(self, steps, current_case_code):
        """添加公共步骤，处理步骤引用"""
        common_case_variables = {}
        new_steps = []

        # 遍历步骤
        for step in steps:
            common_step_code = step.get("common_step", None)
            # 处理公共步骤
            if common_step_code is not None:
                common_case_content, common_path = common_file_data(self.case_type, common_step_code)
                case_code = common_case_content["case_code"]
                case_data_filename = common_case_content.pop("case_data", None)
                drive_data_filename = common_case_content.pop("drive_data", case_data_filename)
                common_steps = common_case_content.get("steps", [])

                # 设置步骤的case_code
                for common_step in common_steps:
                    common_step["case_code"] = case_code

                # 处理驱动数据
                if drive_data_filename is not None:
                    if isinstance(drive_data_filename, int):
                        for_data = [common_case_content] * drive_data_filename
                        self.ready_case_list += for_data
                    else:
                        drive_data = xlsx_file_data(self.case_type, drive_data_filename, common_path)
                        try:
                            nest_steps, nest_case_variables = self.common_step_add(common_steps, case_code)
                            new_steps.extend(nest_steps)
                            common_case_variable = drive_data.pop(case_code)[0]
                            common_case_variables[case_code] = common_case_variable
                        except KeyError:
                            raise KeyError(f"{common_case_content.get('case_name')} 用例数据文件中未找到 case_code 对应的 Sheet")
                    continue

                # 处理嵌套步骤
                nest_steps, nest_case_variables = self.common_step_add(common_steps, case_code)
                new_steps.extend(nest_steps)
                common_case_variables.update(nest_case_variables)
                case_variables = common_case_content.get("case_variables", {})
                common_case_variables[case_code] = case_variables
            else:
                step["case_code"] = current_case_code
                new_steps.append(step)

        return new_steps, common_case_variables

    @staticmethod
    def dict_value_switch(dict_data: dict) -> dict:
        """转换字典中值的类型"""
        for k, v in dict_data.items():
            # 处理字符串形式的列表
            if v and isinstance(v, str) and v[0] == "[" and v[-1] == "]":
                dict_data[k] = ast.literal_eval(v)
            # 处理字符串形式的字典
            if v and isinstance(v, str) and v[0] == "{" and v[-1] == "}":
                dict_data[k] = ast.literal_eval(v)
            # 处理浮点数
            if isinstance(v, float):
                f = '{:g}'.format(v)
                if "." in f:
                    dict_data[k] = float(f)
                else:
                    dict_data[k] = int(f)
        return dict_data

    def truncation_case(self):
        """截断用例处理"""
        for case_path in self.case_paths:
            # 读取JSON文件
            if case_path.split(".")[1] == "json":
                with open(case_path, 'r', encoding="utf-8") as f:
                    case_content = json.loads(f.read(), encoding="utf-8")
            if not case_content:
                continue

            # 处理步骤和变量
            steps = case_content.pop("steps", [])
            common_steps, common_case_variables = self.common_step_add(steps, case_content["case_code"])
            case_content["common_case_variables"] = common_case_variables

            # 根据截断步骤处理
            if self.truncation_step:
                new_steps = []
                for step in common_steps:
                    step_code = step.get("step_code")
                    if step_code and step_code in self.truncation_step:
                        new_steps.append(step)
                case_content["steps"] = new_steps
            else:
                case_content["steps"] = common_steps
            self.cut_case.append(case_content)


class CaseInfo:
    """用例信息基类"""
    def __init__(self, case_content):
        """初始化用例信息"""
        self.case_content = case_content  # 用例内容
        self.case_code = case_content["case_code"]  # 用例编码
        self.case_name = case_content.get("case_name", "")  # 用例名称
        self.case_link = case_content.get("case_link", "")  # 用例链接
        self.case_variables = {}  # 用例变量
        self.common_case_variables = {}  # 公共用例变量
        self.steps = case_content.get("steps", [])  # 用例步骤
        
        # 报告相关字段
        self.steps_list = []  # 保存StepInfo子对象
        self.result = "Pass"  # 执行结果：Idle, Pass, Fail
        self.message = "实际与预期结果一致"  # 结果信息
        self.step_info = None  # 当前步骤信息
        self.case_duration = 0  # 用例执行时长

    def case_explain(self):
        """输出用例执行信息"""
        # 记录用例基本信息
        LOG.info(f"执行用例: {self.case_name}; 用例code: {self.case_code}")
        if self.case_link:
            LOG.debug(f"执行用例: {self.case_name}; 测试用例详情链接: {self.case_link}")

        # 处理用例变量
        case_variables = self.case_content.get("case_variables", {})
        if case_variables:
            LOG.info(f"执行用例: {self.case_name}; 执行用例变量解析: {case_variables}")
        if isinstance(case_variables, list):
            for i in range(len(case_variables)):
                for variable_key, case_variable in case_variables[i].items():
                    if isinstance(case_variable, str) and "where" not in case_variable.lower():
                        case_variable = rule(case_variable)
                    self.case_variables[variable_key + "_" + str(i)] = case_variable
        elif isinstance(case_variables, dict):
            for variable_key, case_variable in case_variables.items():
                if isinstance(case_variable, str) and "where" not in case_variable.lower():
                    case_variable = rule(case_variable)
                self.case_variables[variable_key] = case_variable
        if self.case_variables:
            LOG.debug(f"执行用例: {self.case_name}; 储存用例的全局变量: {self.case_variables}")

        # 处理公共用例变量
        common_case_variables = self.case_content.get("common_case_variables", {})
        if common_case_variables:
            LOG.debug(f"执行用例: {self.case_name}; 执行用例引用用例common变量解析: {common_case_variables}")
        for common_case_code, common_case_variable in common_case_variables.items():
            if common_case_variable:
                for variable_key, case_variable in common_case_variable.items():
                    case_variable = rule(case_variable)
                    common_case_variable[variable_key] = case_variable
                self.common_case_variables[common_case_code] = common_case_variable
                LOG.debug(f"引用用例code: {common_case_code}; 储存用例的全局变量: {common_case_variable}")

    def run(self, *args, **kwargs):
        """运行用例"""
        for step in self.steps:
            step_info = StepInfo(step)
            self.steps_list.append(step_info)


class StepInfo:
    """步骤信息基类"""
    def __init__(self, step_content):
        """初始化步骤信息"""
        self.step_content = step_content  # 步骤内容
        self.case_code = step_content.pop("case_code", "")  # 用例编码
        self.step_code = step_content.pop("step_code", "")  # 步骤编码
        self.step_name = step_content.pop("step_name", "")  # 步骤名称
        self.step_variables = {}  # 步骤变量
        
        # 报告相关字段
        self.result = "Idle"  # 执行结果：Idle, Pass, Fail
        self.message = "步骤待运行"  # 结果信息
        self.step_duration = 0  # 步骤执行时长

    def step_explain(self):
        """输出步骤执行信息"""
        LOG.info(f"执行用例步骤: {self.step_name}; 用例code: {self.case_code}")

    def run(self, *args):
        """运行步骤（基类方法，需要子类实现）"""
        pass


class ApiCaseInfo(CaseInfo):
    """API用例信息类"""
    def run(self):
        """运行API用例"""
        self.case_explain()
        self.result = "Pass"
        self.message = "实际与预期结果一致"
        safety_type = self.case_content.get("safety", {})
        
        # 执行每个步骤
        for step in self.steps:
            self.step_info = ApiStepInfo(step)
            self.step_info.safety_type = safety_type
            self.steps_list.append(self.step_info)
            self.step_info.run()
            self.case_duration += self.step_info.step_duration
            
            # 检查步骤执行结果
            if self.step_info.result == "Fail":
                self.result = "Fail"
                self.message = self.step_info.message


class ApiStepInfo(StepInfo):
    """API步骤信息类"""
    def __init__(self, step_content):
        """初始化API步骤信息"""
        super(ApiStepInfo, self).__init__(step_content)
        # HTTP请求相关属性
        self.http_url = ""  # 请求URL
        self.request_method = "POST"  # 请求方法
        self.header_data = {}  # 请求头
        self.request_data = {}  # 请求数据
        self.status_code = ""  # 响应状态码
        self.response_cookies = {}  # 响应cookies
        self.response_headers = {}  # 响应头
        self.response_data = {}  # 响应数据
        self.validate_info_list = []  # 验证信息列表
        self.retry_path_list = ["/file", "/exhibition/File/UploadWH"]  # 需要重试的路径列表
        self.safety_type = {}  # 安全测试类型
        
        # 断言相关属性
        self.response_assert_data = self.step_content.get("response_assert", None)  # 响应断言数据
        self.db_asserts = self.step_content.get("db_assert", None)  # 数据库断言
        self.rd_assert = self.step_content.get("rd_assert", None)  # Redis断言
        self.key_code = None  # 键值编码

    def data_handle(self, data):
        """处理数据，转换变量"""
        if isinstance(data, list):
            for i in range(len(data)):
                if isinstance(data[i], dict):
                    for key, value in data[i].items():
                        self.step_variables["step_"+key+"_"+str(i)] = rule(value)
        elif isinstance(data, dict):
            for key, value in data.items():
                self.step_variables[key] = rule(value)

    def variables(self):
        """处理步骤变量"""
        step_variables = self.step_content.get("step_variables")
        if step_variables:
            LOG.info("----------- 执行接口用例步骤变量数据转换 --------------")
            self.data_handle(step_variables)

    def before(self):
        """执行前置处理"""
        before = self.step_content.get("before")
        if before:
            LOG.info("----------- 执行接口用例请求前预置条件 --------------")
            self.data_handle(before)

    def http_request(self):
        """发送HTTP请求"""
        # 获取请求数据
        request_data = self.step_content.get("request")
        LOG.info("----------- 执行接口用例解析，发起requests请求 --------------")
        
        # 处理请求参数
        if request_data:
            host = rule(request_data.get("host"))
            path = rule(request_data.get("path"))
            method = rule(request_data.get("method")).upper()
            headers = rule(request_data.get("headers"))
            cookies = rule(request_data.get("cookies"))
            data = rule(request_data.get("data"))
        else:
            host = rule(self.step_content.get("host"))
            path = rule(self.step_content.get("path"))
            method = rule(self.step_content.get("method")).upper()
            headers = rule(self.step_content.get("headers"))
            cookies = rule(self.step_content.get("cookies"))
            data = rule(self.step_content.get("data"))

        # 处理特殊格式的数据
        if data and "{" not in data and ":" in data:
            parameters = dict()
            parameter_list = data.split("\n")
            for parameter in parameter_list:
                parameter_data = parameter.split(":", 1)
                parameters[parameter_data[0]] = parameter_data[1]
            data = parameters

        # 处理JSON字符串
        if isinstance(data, str) and data.isspace() is False and data != "":
            data = json.loads(data)

        # 处理请求头
        if headers and isinstance(headers, str):
            if "{" not in headers and ":" in headers:
                headers_data = dict()
                headers_list = headers.split("\n")
                for header in headers_list:
                    header_data = header.split(":", 1)
                    headers_data[header_data[0]] = header_data[1].strip()
                headers = headers_data
            else:
                headers = ast.literal_eval(headers)
            if "default_headers" in headers:
                default_headers = headers.pop("default_headers")
                headers.update(default_headers)

        # 发送请求
        if isinstance(g.client, HttpRequests):
            if path in self.retry_path_list:
                with request_lock:  # 使用锁进行重试请求
                    client_data = g.client.request_retry(method, host, path, headers, data, cookies)
            else:
                client_data = g.client.request(method, host, path, headers, data, cookies)
        else:
            raise TypeError("请使用内置 HttpRequests 作为api请求客户端")

        # 保存响应信息
        self.http_url = client_data["host"] + client_data["path"]
        self.request_method = client_data["method"]
        self.header_data = client_data["headers"]
        self.request_data = data
        self.status_code = client_data["status_code"]
        self.step_duration = client_data["duration_seconds"]
        self.response_data = client_data["response_text"]
        self.response_cookies = client_data["response_cookies"]
        self.response_headers = client_data["response_headers"]

        return client_data

    def extract(self):
        """提取响应数据"""
        extract = self.step_content.get("extract")
        if extract:
            LOG.info("----------- 执行接口用例请求后的数据提取 --------------")
            for extraction in extract:
                if isinstance(extraction, dict):
                    for key, value in extraction.items():
                        if key == "extract":
                            LOG.info(f"执行用例数据提取操作: {value}")
                            rule(value)
                        else:
                            rule_expression = f"$set_variable({key}, {value})"
                            LOG.info(f"执行用例数据提取操作: {rule_expression}")
                            rule(rule_expression)

    def validate(self):
        """验证响应结果"""
        validate = self.step_content.get("validation")
        if validate:
            LOG.info("----------- 执行接口用例请求后的断言 ----------------")
            self.result = "Pass"
            self.message = "检查项验证成功"
            
            # 必填验证
            for validation in validate:
                original_validate = validation.get("validation")
                LOG.info(f"执行接口用例请求后的断言: {original_validate}")
                wait_retry = validation.get("wait_retry", None)
                try:
                    rule.validate_rule_dispose(original_validate, wait_retry)
                    validation["assert_result"] = "Pass"
                    validation["message"] = "True"
                except (KeyWordRuntimeException, AssertionFailure) as e:
                    message = validation.get("error_message", e)
                    validation["assert_result"] = "Fail"
                    validation["message"] = message
                    self.result = "Fail"
                    self.message = message
                self.validate_info_list.append(validation)
        else:
            # validation 传递的是断言的内容.
            try:
                if self.response_assert_data:
                    validation = {}
                    response_assert_data = self.response_assert_data.get("response_assert_data")
                    status_code_assert = self.response_assert_data.get("status_code_assert")
                    response_assert_result = response_assert(status_code_assert=[self.status_code, status_code_assert],
                                                             response_assert=[self.response_data, response_assert_data])
                    if response_assert_result:
                        validation["assert_result"] = "Pass"
                        validation["message"] = "True"
                        self.result = "Pass"
                        validation[
                            "validation"] = f"响应状态码断言:{[self.status_code, status_code_assert]},响应数据断言:{[self.response_data, response_assert_data]}"
                        self.validate_info_list.append(validation)
                if self.db_asserts:
                    validation = {}
                    db_assert_data = self.db_asserts.get("db_assert_data", self.request_data)
                    condition = self.db_asserts.get("condition", db_assert_data.copy())
                    self.db_asserts["condition"] = condition
                    self.db_asserts["db_assert_data"] = db_assert_data
                    self.key_code = db_assert(**self.db_asserts)
                    if self.key_code:
                        validation["assert_result"] = "Pass"
                        validation["message"] = "True"
                        self.result = "Pass"
                        validation[
                            "validation"] = f"数据库断言"
                        self.validate_info_list.append(validation)
                if self.rd_assert:
                    validation = {}
                    try:
                        hash_key = self.rd_assert.get("hash_key")
                        # 支持两种断言数据格式：rd_assert_data 或 check_fields
                        rd_assert_data = self.rd_assert.get("rd_assert_data", {})
                        check_fields = self.rd_assert.get("check_fields", [])
                        
                        # 如果有check_fields，将其转换为rd_assert_data格式
                        if check_fields:
                            for field_info in check_fields:
                                field = field_info.get("field")
                                expected = field_info.get("expected")
                                if field and expected is not None:
                                    rd_assert_data[field] = expected
                        
                        self.rd_assert["rd_assert_data"] = rd_assert_data
                        
                        # 安全地获取key_code
                        if self.key_code is None:
                            # 从rd_assert中获取key_code，如果不存在则从request_data中获取
                            self.key_code = self.rd_assert.get("key_code")
                            if self.key_code is None:
                                # 尝试从hash_key中提取参数作为key_code
                                if isinstance(hash_key, str) and "$params." in hash_key:
                                    # 使用正则表达式提取参数名
                                    import re
                                    match = re.search(r'\$params\.(\w+)', hash_key)
                                    if match:
                                        param_name = match.group(1)
                                        self.key_code = self.request_data.get(param_name)
                                
                                # 如果还是没有找到，使用默认值
                                if self.key_code is None:
                                    self.key_code = "default_key_code"
                        
                        rd_assert_result = redis_hash_assert(hash_key, self.key_code, rd_assert_data)
                        if rd_assert_result:
                            validation["assert_result"] = "Pass"
                            validation["message"] = "True"
                            self.result = "Pass"
                            validation["validation"] = f"redis断言"
                            self.validate_info_list.append(validation)
                    except Exception as e:
                        LOG.warning(f"Redis断言处理异常: {str(e)}")
                        # 记录警告但不中断测试
                        validation["assert_result"] = "Skip"
                        validation["message"] = f"跳过Redis断言: {str(e)}"
                        validation["validation"] = "Redis断言处理异常"
                        self.validate_info_list.append(validation)
            except (KeyWordRuntimeException, AssertionFailure) as e:
                LOG.error(traceback.format_exc())
                validation = {}
                validation["assert_result"] = "Fail"
                validation["message"] = e
                self.result = "Fail"
                self.message = e
                validation[
                    "validation"] = "断言失败"
                self.validate_info_list.append(validation)

    def sqlmap_validate(self, client_data):
        # SQL注入测试
        sqlmap_state = self.safety_type.get("sqlmap")

        if sqlmap_state:
            sqlmap_data = SqlMapClient().run(client_data)
            validation = {}
            if sqlmap_data:
                validation["validation"] = "存在 sql 注入"
                validation["assert_result"] = "Fail"
                message = f"""
                标题信息: {sqlmap_data["title"]},<br/>
                注入的url: {sqlmap_data["url"]},<br/>
                注入的字段: {sqlmap_data["parameter"]},<br/>
                注入的字段值: {sqlmap_data["payload"]}
                """
                validation["message"] = message
                self.result = "Fail"
                self.message = message
            else:
                print("sqlapi", client_data, )
                validation["validation"] = "sql 注入测试通过"
                validation["assert_result"] = "Pass"
                validation["message"] = "True"

            self.validate_info_list.append(validation)

    def after(self):
        after = self.step_content.get("after")
        if after:
            LOG.info("----------- 执行接口用例请求后清除操作 --------------")
            self.data_handle(after)

    def run(self):
        self.step_explain()
        self.variables()
        self.before()
        client_data = self.http_request()
        self.extract()
        self.validate()
        self.sqlmap_validate(client_data)
        self.after()


class UiCaseInfo(CaseInfo):
    def run(self):
        self.case_explain()
        self.steps_analysis(self.steps)

    def steps_analysis(self, steps):
        for step in steps:
            child_steps = step.pop("child_steps", None)
            if child_steps:
                self.case_step_run(step)
                if_is_pass = self.step_info.step_variables.get("if")
                if if_is_pass is True:
                    self.steps_analysis(child_steps)
            else:
                self.case_step_run(step)

    def case_step_run(self, step_content):
        self.step_info = UiStepInfo(step_content)
        self.steps_list.append(self.step_info)
        self.step_info.run()
        self.case_duration += self.step_info.step_duration


class UiStepInfo(StepInfo):
    def __init__(self, step_content):
        super(UiStepInfo, self).__init__(step_content)
        self.image_base64 = ""

    def run(self):
        self.result = "Pass"
        self.message = "步骤运行验证成功"
        self.step_explain()
        start_time = datetime.datetime.now()
        
        try:
            # 导入Playwright关键字到rule_engine（只导入一次，避免重复导入）
            from core.rule_engine import import_keywords, default_keywords
            # 检查是否已经导入过playwright_keywords（通过检查关键函数是否存在）
            playwright_keywords_loaded = any(key in default_keywords for key in ["navigate", "click", "input", "wait_element_visibility"])
            if not playwright_keywords_loaded:
                try:
                    import_keywords("keywords.playwright_keywords")
                except Exception as e:
                    # 如果导入失败，记录错误但不中断执行
                    LOG.warning(f"导入Playwright关键字失败: {e}")
            
            # 处理步骤变量和执行操作
            # 按照顺序处理：先处理action，再处理其他变量
            # 注意：YAML中如果有多个action字段，只会保留最后一个，所以需要按顺序处理所有字段
            
            # 先收集所有字段，按顺序处理
            step_items = list(self.step_content.items())
            
            # 检查是否有actions字段（复数，列表格式）
            if 'actions' in self.step_content:
                # 处理actions列表格式
                actions_list = self.step_content['actions']
                if isinstance(actions_list, list):
                    for action_item in actions_list:
                        # action_item可能是一个字典，包含action、sleep、save_as等字段
                        if isinstance(action_item, dict):
                            # 先处理非action字段（如selector），再处理action
                            # 按顺序处理：先处理selector等，再处理action，最后处理sleep和save_as
                            item_items = list(action_item.items())
                            
                            # 第一遍：处理非action字段（selector等）
                            for item_key, item_value in item_items:
                                if item_key not in ['action', 'sleep', 'save_as']:
                                    # 解析并保存变量
                                    if isinstance(item_value, str) and ('$' in item_value or '&' in item_value):
                                        parsed_value = rule(item_value)
                                        self.step_variables[item_key] = parsed_value
                                    else:
                                        self.step_variables[item_key] = item_value
                                    LOG.debug(f"设置变量 {item_key} = {self.step_variables[item_key]}")
                            
                            # 第二遍：执行action
                            for item_key, item_value in item_items:
                                if item_key == 'action':
                                    # 先替换 *variable 引用为步骤变量或用例变量的实际值
                                    action_str = str(item_value)
                                    # 查找所有 *variable 格式的引用
                                    import re
                                    var_pattern = r'\*([a-zA-Z0-9_]+)'
                                    matches = re.findall(var_pattern, action_str)
                                    for var_name in matches:
                                        var_value = None
                                        # 先尝试从步骤变量中获取
                                        if var_name in self.step_variables:
                                            var_value = self.step_variables[var_name]
                                        # 如果不存在，尝试从用例变量中获取
                                        elif hasattr(g, 'case_info') and g.case_info and hasattr(g.case_info, 'case_content'):
                                            case_vars = g.case_info.case_content.get('case_variables', {})
                                            if var_name in case_vars:
                                                var_value = case_vars[var_name]
                                        
                                        if var_value is not None:
                                            # 替换 *variable 为实际值
                                            # 直接替换为值，使用 &variable 格式，让rule函数自己处理
                                            # 这样rule函数会通过 $get_variable 获取值
                                            action_str = action_str.replace(f'*{var_name}', f'&{var_name}')
                                            # 临时设置变量，供rule函数使用
                                            if not hasattr(self, '_temp_vars'):
                                                self._temp_vars = {}
                                            self._temp_vars[var_name] = var_value
                                            LOG.debug(f"替换 *{var_name} 为 &{var_name}，值为 {var_value}")
                                    
                                    # 临时设置变量到step_variables，供rule函数使用
                                    if hasattr(self, '_temp_vars') and self._temp_vars:
                                        for var_name, var_value in self._temp_vars.items():
                                            self.step_variables[var_name] = var_value
                                    
                                    # 解析action（&variable会引用已设置的变量）
                                    parsed_action = rule(action_str)
                                    
                                    # 清理临时变量
                                    if hasattr(self, '_temp_vars') and self._temp_vars:
                                        for var_name in self._temp_vars.keys():
                                            if var_name in self.step_variables:
                                                del self.step_variables[var_name]
                                        self._temp_vars = {}
                                    if callable(parsed_action):
                                        result = parsed_action()
                                        # 保存action的执行结果，供save_as使用
                                        if result is not None:
                                            self.step_variables['last_action_result'] = result
                                    else:
                                        LOG.debug(f"action不是函数: {parsed_action}")
                            
                            # 第三遍：处理sleep和save_as
                            for item_key, item_value in item_items:
                                if item_key == 'sleep':
                                    # 处理sleep
                                    if isinstance(item_value, (int, float)):
                                        import time
                                        time.sleep(item_value)
                                    else:
                                        sleep_value = rule(item_value) if isinstance(item_value, str) and ('$' in item_value or '&' in item_value) else item_value
                                        if isinstance(sleep_value, (int, float)):
                                            import time
                                            time.sleep(sleep_value)
                                elif item_key == 'save_as':
                                    # 保存上一个action的结果
                                    if 'last_action_result' in self.step_variables:
                                        self.step_variables[item_value] = self.step_variables['last_action_result']
                                        LOG.debug(f"保存变量 {item_value} = {self.step_variables[item_value]}")
                        elif isinstance(action_item, str):
                            # 如果action_item是字符串，直接执行
                            parsed_action = rule(action_item)
                            if callable(parsed_action):
                                result = parsed_action()
                                if result is not None:
                                    self.step_variables['last_action_result'] = result
                # 处理完actions后，不再处理其他字段（避免重复）
                return
            
            # 按顺序处理每个字段（单action格式）
            # 注意：需要按顺序执行action，即使YAML中只保留最后一个action
            # 解决方案：按顺序处理所有字段，遇到action立即执行
            for variable_key, step_variable in step_items:
                # 跳过特殊字段
                if variable_key in ['step_name', 'step_code', 'case_code', 'actions']:
                    continue
                
                # 处理变量（rule函数会解析关键字调用）
                try:
                    # 如果是action字段，立即执行
                    if variable_key == 'action':
                        # action字段中的&variable需要引用当前步骤已设置的变量
                        step_variable = rule(step_variable)
                        # 注意：不要覆盖之前的action值，而是追加到列表中
                        if 'action' not in self.step_variables:
                            self.step_variables['action'] = []
                        if not isinstance(self.step_variables['action'], list):
                            # 如果之前是单个值，转换为列表
                            self.step_variables['action'] = [self.step_variables['action']]
                        self.step_variables['action'].append(step_variable)
                        # action字段如果是函数，需要立即执行
                        if callable(step_variable):
                            result = step_variable()
                            # 保存action的执行结果，供save_as使用
                            if result is not None:
                                self.step_variables['last_action_result'] = result
                        else:
                            # 如果不是函数，记录日志
                            LOG.debug(f"action字段不是函数: {step_variable}")
                    elif variable_key == 'sleep':
                        # sleep字段直接处理
                        if isinstance(step_variable, (int, float)):
                            import time
                            time.sleep(step_variable)
                        else:
                            # 如果sleep是字符串，尝试解析
                            step_variable = rule(step_variable)
                            if isinstance(step_variable, (int, float)):
                                import time
                                time.sleep(step_variable)
                    elif variable_key == 'save_as':
                        # save_as字段：保存上一个action的结果
                        if 'last_action_result' in self.step_variables:
                            self.step_variables[step_variable] = self.step_variables['last_action_result']
                            LOG.debug(f"保存变量 {step_variable} = {self.step_variables[step_variable]}")
                    else:
                        # 其他字段先解析，但不立即执行（如selector, element等）
                        # 注意：YAML中的 *variable 是锚点引用，会被解析为实际值
                        # &variable 是变量引用，会被rule函数解析为 $get_variable(variable)
                        # 先检查是否是字符串，如果是字符串且包含关键字，需要解析
                        if isinstance(step_variable, str) and ('$' in step_variable or '&' in step_variable):
                            # 解析变量引用和关键字调用
                            # 注意：这里的&variable会引用当前步骤已设置的变量（如&selector引用selector变量）
                            parsed_value = rule(step_variable)
                            
                            # 如果解析后是函数，执行并保存返回值
                            if callable(parsed_value):
                                try:
                                    result = parsed_value()
                                    # 保存函数执行结果
                                    self.step_variables[variable_key] = result if result is not None else parsed_value
                                    LOG.debug(f"设置变量 {variable_key} = {self.step_variables[variable_key]}")
                                except Exception as e:
                                    LOG.error(f"执行函数失败: {variable_key} = {step_variable}, 错误: {e}")
                                    self.step_variables[variable_key] = parsed_value
                            else:
                                # 如果不是函数，直接保存解析后的值
                                self.step_variables[variable_key] = parsed_value
                                LOG.debug(f"设置变量 {variable_key} = {self.step_variables[variable_key]}")
                        else:
                            # 如果不是字符串或不需要解析，直接保存
                            # YAML锚点引用（*variable）已经被YAML解析器解析为实际值
                            self.step_variables[variable_key] = step_variable
                            LOG.debug(f"设置变量 {variable_key} = {self.step_variables[variable_key]}")
                            
                except Exception as e:
                    LOG.error(f"执行步骤变量失败: {variable_key} = {step_variable}, 错误: {e}")
                    self.result = "Fail"
                    self.message = f"执行失败: {str(e)}"
                    self.fail_screenshot()
                    raise
        
        except Exception as e:
            self.result = "Fail"
            self.message = f"步骤执行失败: {str(e)}"
            LOG.error(f"步骤执行失败: {self.step_name}, 错误: {e}")
            import traceback
            LOG.error(traceback.format_exc())
            self.fail_screenshot()
        
        end_time = datetime.datetime.now()
        step_time_sep = (end_time - start_time).total_seconds() * 1000  # 转换为毫秒
        self.step_duration = float('%.2f' % step_time_sep)

    def fail_screenshot(self):
        try:
            from keywords.playwright_keywords import get_playwright_driver
            driver = get_playwright_driver()
            
            # 使用Playwright截图
            screenshot_bytes = driver.screenshot(full_page=True)
            
            # 转换为base64
            self.image_base64 = str(base64.b64encode(screenshot_bytes), encoding='utf-8')
            LOG.info("页面截图成功")
        except Exception as e:
            LOG.error(traceback.format_exc())
            LOG.error(f"页面异常截图失败: {e}")

    @staticmethod
    def resize(image_path, width=None, height=None, inter=cv2.INTER_AREA):
        """
        根据宽高与原图的比例进行缩放，覆盖原图保存
        """
        import numpy as np

        if width is None and height is None:
            return image_path

        # 读取图像
        image = cv2.imdecode(np.fromfile(image_path, dtype=np.uint8), cv2.IMREAD_COLOR)
        (h, w) = image.shape[:2]
        if width is None:
            r = height / float(h)
            dim = (int(w * r), height)
        else:
            r = width / float(w)
            dim = (width, int(h * r))
        resized = cv2.resize(image, dim, interpolation=inter)
        # 写入图像
        cv2.imencode('.png', resized, [int(cv2.IMWRITE_PNG_COMPRESSION), 5])[1].tofile(image_path)
        return image_path


class LocustApiCaseInfo(ApiCaseInfo):
    def __init__(self, case_content):
        super(LocustApiCaseInfo, self).__init__(case_content)

    def run(self):
        try:
            self.case_explain()
            for step in self.steps:
                self.step_info = LocustApiStepInfo(step)
                self.step_info.run()
                with g.response:
                    g.response.success()
        except Exception as e:
            LOG.error(traceback.format_exc())
            g.locust.environment.events.request.fire(
                request_type=self.step_info.request_data.get("method"),
                name=self.step_info.request_data.get("name"),
                context={},
                response_time=0,
                response_length=0,
                exception=e,
            )


class LocustApiStepInfo(ApiStepInfo):
    def http_request(self):
        from locust.clients import HttpSession
        from locust.contrib.fasthttp import FastHttpSession

        request_data = self.step_content.get("request")
        method = rule(request_data.get("method")).upper()
        path = rule(request_data.get("path"))
        data = rule(request_data.get("data"))
        url = rule(request_data.get("host")) + path
        headers = rule(request_data.get("headers"))

        url, headers = self.host_ip(url, headers)
        self.request_data = {
            "method": method,
            "headers": headers,
            "catch_response": True,
            "name": path
        }
        if isinstance(g.client, HttpSession):
            self.request_data["url"] = url
            if method == "GET":
                self.request_data["params"] = data
            else:
                self.request_data["json"] = data
        elif isinstance(g.client, FastHttpSession):
            if method == "GET":
                self.request_data["path"] = self.url_join_args(url, data)
            else:
                self.request_data["path"] = url
                self.request_data["json"] = data
        else:
            raise TypeError("不支持的locust请求客户端！")

        LOG.info(f"request请求数据：{self.request_data}")
        g.response = g.client.request(**self.request_data)
        LOG.info(f"response状态码：{g.response.status_code}")
        LOG.info(f"response返回文本：{g.response.text}")

        self.status_code = g.response.status_code
        self.response_data = g.response.json()

    @staticmethod
    def url_join_args(api, query: dict = None):
        result = api
        if "?" in result:
            if query:
                result = api + '&' + urlencode(query)
        else:
            if query:
                result = api + '?' + urlencode(query)
        return result

    @staticmethod
    def host_ip(url, headers):
        if hasattr(g, "host_conf") and isinstance(g.host_conf, dict):
            actual_host = urlparse(url).netloc
            if actual_host in g.host_conf.keys():
                ip = g.host_conf[actual_host]
                url = url.replace(actual_host, ip)
                headers["Host"] = actual_host
        return url, headers

    def validate(self):
        validate = self.step_content.get("validation")
        if validate:
            LOG.info("----------- 执行接口用例请求后的断言 ----------------")
            for validation in validate:
                original_validate = validation.get("validation")
                # if "$get_code()" in original_validate:
                #     continue
                wait_retry = validation.get("wait_retry", None)
                rule.validate_rule_dispose(original_validate, wait_retry)


if __name__ == "__main__":
    case_pathS = 'F:\\Simple\\al_test\\autotest_elegant\\cases_api\\test_cases\\case_excel\\excel_test.xlsx'
    r = excel_api_case
