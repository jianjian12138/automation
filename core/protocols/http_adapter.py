"""
HTTP协议适配器
支持标准HTTP/HTTPS请求
"""
import requests
from typing import Dict, Any, Optional, Callable
import logging
import re
from jsonpath_ng import parse
from .base import ProtocolAdapter

LOG = logging.getLogger(__name__)

# 导入框架的配置
try:
    from libs.config_center import ENV
    AUTOTEST_SUPPORT = True
except ImportError:
    AUTOTEST_SUPPORT = False
    ENV = {}


class HTTPAdapter(ProtocolAdapter):
    """HTTP协议适配器"""
    
    def __init__(self, config: Dict[str, Any] = None, variable_processor: Optional[Callable] = None):
        super().__init__(config)
        self.session = requests.Session()
        # 设置默认超时
        self.timeout = config.get('timeout', 30) if config else 30
        # 保存变量处理函数引用（用于处理$get_host等函数调用）
        self.variable_processor = variable_processor
    
    def process_variables(self, value: Any) -> Any:
        """
        处理变量替换（重写基类方法，支持函数调用）
        
        :param value: 需要处理的值
        :return: 处理后的值
        """
        # 如果有变量处理函数，使用它（支持函数调用）
        if self.variable_processor:
            # 处理字典类型
            if isinstance(value, dict):
                return self._process_dict_variables(value)
            # 处理列表类型
            elif isinstance(value, list):
                return [self.process_variables(item) for item in value]
            # 处理字符串类型
            elif isinstance(value, str):
                return self.variable_processor(value)
            else:
                return value
        # 否则使用基类方法（仅支持简单变量）
        return super().process_variables(value)
    
    def _process_dict_variables(self, data: Dict) -> Dict:
        """
        处理字典中的变量（支持数组字段的特殊处理）
        
        :param data: 需要处理的字典
        :return: 处理后的字典
        """
        if not isinstance(data, dict):
            return data
        
        result = {}
        for key, value in data.items():
            if isinstance(value, str):
                # 处理变量表达式（如$get_variable(...)）
                processed_value = self.process_variables(value)
                
                # 特殊处理：如果字段名以Codes结尾，需要确保结果是数组
                if key.endswith('Codes') or key.endswith('codes'):
                    # 如果已经是数组，直接使用
                    if isinstance(processed_value, list):
                        result[key] = processed_value
                    # 如果是JSON字符串，尝试解析为数组
                    elif isinstance(processed_value, str) and processed_value.strip().startswith('['):
                        try:
                            import json
                            parsed = json.loads(processed_value)
                            if isinstance(parsed, list):
                                result[key] = parsed
                            else:
                                result[key] = [parsed]
                        except (json.JSONDecodeError, TypeError):
                            # 解析失败，当作普通字符串处理（但这种情况不应该发生）
                            LOG.warning(f"[HTTP] 无法解析数组字段 {key} 的JSON字符串: {processed_value[:100]}")
                            result[key] = [processed_value]  # 至少确保是数组格式
                    else:
                        # 如果不是数组也不是JSON字符串，转换为数组（单个值）
                        if processed_value is not None and processed_value != '':
                            result[key] = [processed_value]
                        else:
                            result[key] = []
                else:
                    result[key] = processed_value
            elif isinstance(value, dict):
                result[key] = self._process_dict_variables(value)
            elif isinstance(value, list):
                result[key] = [self.process_variables(item) for item in value]
            else:
                # 对于非字符串类型的值，如果是数组字段，确保是数组格式
                if (key.endswith('Codes') or key.endswith('codes')) and value is not None:
                    if not isinstance(value, list):
                        result[key] = [value]
                    else:
                        result[key] = value
                else:
                    result[key] = value
        
        return result
    
    def send_request(self, step: Dict[str, Any]) -> Dict[str, Any]:
        """
        发送HTTP请求
        
        :param step: 测试步骤配置
            - method: HTTP方法 (GET, POST, PUT, DELETE等)
            - host: 主机地址
            - path/url: 请求路径
            - headers: 请求头
            - params: URL参数
            - data: 请求体数据
        :return: 响应结果
        """
        # 处理变量
        method = step.get('method', 'GET').upper()
        host = self.process_variables(step.get('host', ''))
        path = self.process_variables(step.get('path', step.get('url', '')))
        headers_raw = step.get('headers', {})
        if isinstance(headers_raw, dict):
            headers = self.process_variables(headers_raw)
        else:
            headers = self.process_variables(headers_raw)
        
        params_raw = step.get('params', {})
        if isinstance(params_raw, dict):
            params = self._process_dict_variables(params_raw)
        else:
            params = self.process_variables(params_raw)
        
        data_raw = step.get('data', {})
        if isinstance(data_raw, dict):
            data = self._process_dict_variables(data_raw)
        else:
            data = self.process_variables(data_raw)
        
        # 构建完整URL
        if host:
            full_url = f"{host}{path}" if path.startswith('/') else f"{host}/{path}"
        else:
            full_url = path
        
        # 调试：打印请求数据（特别是数组字段）
        if isinstance(data, dict):
            import json
            LOG.debug(f"[HTTP] 请求数据: {json.dumps(data, ensure_ascii=False, indent=2)}")
            # 检查数组字段是否正确
            for key, value in data.items():
                if isinstance(value, list):
                    LOG.debug(f"[HTTP] 数组字段 {key}: {value} (类型: {type(value).__name__})")
                elif isinstance(value, str) and value.strip().startswith('['):
                    LOG.warning(f"[HTTP] 警告: 字段 {key} 可能是JSON字符串而不是数组: {value[:100]}")
        
        LOG.info(f"[HTTP] 请求: {method} {full_url}")
        
        try:
            # 发送请求
            # 对于POST/PUT/PATCH请求，如果data是dict，使用json参数（会自动序列化为JSON）
            # 如果data不是dict（如字符串），使用data参数
            if method in ['POST', 'PUT', 'PATCH']:
                if isinstance(data, dict):
                    # 确保数组字段正确序列化
                    import json
                    request_json = json.dumps(data, ensure_ascii=False)
                    LOG.debug(f"[HTTP] 请求JSON体: {request_json}")
                    response = self.session.request(
                        method=method,
                        url=full_url,
                        headers=headers if isinstance(headers, dict) else {},
                        params=params if isinstance(params, dict) else {},
                        json=data,  # requests会自动序列化dict为JSON
                        timeout=self.timeout
                    )
                else:
                    response = self.session.request(
                        method=method,
                        url=full_url,
                        headers=headers if isinstance(headers, dict) else {},
                        params=params if isinstance(params, dict) else {},
                        data=data,  # 字符串或表单数据
                        timeout=self.timeout
                    )
            else:
                # GET/DELETE等请求，使用params
                response = self.session.request(
                    method=method,
                    url=full_url,
                    headers=headers if isinstance(headers, dict) else {},
                    params=params if isinstance(params, dict) else {},
                    timeout=self.timeout
                )
            
            # 构建响应结果
            result = {
                'status_code': response.status_code,
                'headers': dict(response.headers),
                'body': response.text,
                'metadata': {
                    'url': full_url,
                    'method': method,
                    'request_headers': dict(headers) if isinstance(headers, dict) else {},
                }
            }
            
            # 尝试解析JSON
            try:
                result['json'] = response.json()
            except:
                result['json'] = None
            
            LOG.info(f"[HTTP] 响应状态码: {response.status_code}")
            
            return result
            
        except Exception as e:
            LOG.error(f"[HTTP] 请求失败: {e}")
            return {
                'status_code': 0,
                'headers': {},
                'body': '',
                'json': None,
                'error': str(e),
                'metadata': {}
            }
    
    def validate_response(self, response: Dict[str, Any], assert_config: Dict[str, Any]) -> list:
        """
        验证HTTP响应
        
        :param response: 响应结果
        :param assert_config: 断言配置
            - status_code_assert: 状态码断言
            - response_assert_data: 响应消息断言
            - jsonpath_assert: JSONPath断言列表
        :return: 错误列表
        """
        errors = []
        
        # 状态码断言
        if 'status_code_assert' in assert_config:
            expected_code = assert_config['status_code_assert']
            actual_code = response.get('status_code', 0)
            if actual_code != expected_code:
                errors.append(f"状态码断言失败: 期望{expected_code}, 实际{actual_code}")
            else:
                LOG.info(f"[HTTP] 状态码断言通过: {actual_code}")
        
        # 响应消息断言
        if 'response_assert_data' in assert_config:
            expected_msg = assert_config['response_assert_data']
            response_body = response.get('body', '')
            if expected_msg not in response_body:
                errors.append(f"响应消息断言失败: 未找到'{expected_msg}'")
            else:
                LOG.info(f"[HTTP] 响应消息断言通过")
        
        # JSONPath断言
        if 'jsonpath_assert' in assert_config:
            response_json = response.get('json')
            if response_json:
                for assertion in assert_config['jsonpath_assert']:
                    error = self._assert_jsonpath(response_json, assertion)
                    if error:
                        errors.append(error)
            else:
                errors.append("JSONPath断言失败: 响应不是有效的JSON格式")
        
        return errors
    
    def _assert_jsonpath(self, data: Any, assertion: str) -> str:
        """
        执行JSONPath断言
        支持:
        - 简单断言: $..field == value
        - OR 逻辑: $..field1 == value1 or $..field2 == value2
        """
        # 处理 OR 逻辑
        import re
        or_pattern = re.compile(r'\s+or\s+', re.IGNORECASE)
        if or_pattern.search(assertion):
            LOG.debug(f"[HTTP] 检测到OR逻辑断言: {assertion}")
            conditions = [cond.strip() for cond in or_pattern.split(assertion)]
            LOG.debug(f"[HTTP] OR条件拆分结果: {conditions}")
            
            # 只要有一个条件满足即可
            for condition in conditions:
                # 直接检查条件是否满足，不记录任何错误信息
                try:
                    # 解析条件
                    pattern = r'(\$[^\s]+)\s*(==|!=|>=|<=|>|<)\s*(".*?"|\'.*?\'|[^\s]+)'
                    match = re.match(pattern, condition)
                    
                    if match:
                        path_expr = match.group(1)
                        operator = match.group(2)
                        expected = match.group(3)
                        
                        # 去除引号
                        if expected.startswith('"') and expected.endswith('"'):
                            expected = expected[1:-1]
                        elif expected.startswith("'") and expected.endswith("'"):
                            expected = expected[1:-1]
                        
                        # 执行JSONPath查询
                        jsonpath_expr = parse(path_expr)
                        matches = list(jsonpath_expr.find(data))
                        
                        LOG.debug(f"[HTTP] 检查OR条件: {condition}, 路径: {path_expr}, 找到匹配数: {len(matches)}")
                        
                        # 如果找到数据，检查值是否匹配
                        if matches:
                            # 检查每个匹配值
                            all_match = True
                            for m in matches:
                                actual_value = m.value
                                compare_result = self._compare_values(actual_value, operator, expected)
                                LOG.debug(f"[HTTP]   匹配值: {actual_value}, 期望: {expected}, 操作符: {operator}, 比较结果: {compare_result}")
                                if not compare_result:
                                    all_match = False
                                    break
                            
                            # 如果所有匹配值都满足条件，断言通过
                            if all_match:
                                LOG.info(f"[HTTP] JSONPath断言通过 (OR条件): {condition}")
                                return None  # 条件满足，直接返回，不报告任何错误
                        else:
                            LOG.debug(f"[HTTP]   路径 '{path_expr}' 未找到数据，继续检查下一个条件")
                    else:
                        # 简单存在性断言
                        path_expr = condition
                        jsonpath_expr = parse(path_expr)
                        matches = list(jsonpath_expr.find(data))
                        if matches:
                            LOG.info(f"[HTTP] JSONPath断言通过 (OR条件): {condition}")
                            return None  # 条件满足，直接返回，不报告任何错误
                except Exception as e:
                    # 条件检查出错，继续检查下一个条件
                    LOG.debug(f"[HTTP] 检查条件 {condition} 时出错: {e}")
                    continue
            
            # 所有条件都失败，报告错误
            # 收集所有条件的错误信息用于报告
            all_errors = []
            for condition in conditions:
                try:
                    pattern = r'(\$[^\s]+)\s*(==|!=|>=|<=|>|<)\s*(".*?"|\'.*?\'|[^\s]+)'
                    match = re.match(pattern, condition)
                    
                    if match:
                        path_expr = match.group(1)
                        jsonpath_expr = parse(path_expr)
                        matches = list(jsonpath_expr.find(data))
                        if not matches:
                            all_errors.append(f"路径'{path_expr}'未找到数据")
                        else:
                            # 检查值是否匹配
                            operator = match.group(2)
                            expected = match.group(3)
                            if expected.startswith('"') and expected.endswith('"'):
                                expected = expected[1:-1]
                            elif expected.startswith("'") and expected.endswith("'"):
                                expected = expected[1:-1]
                            
                            for m in matches:
                                actual_value = m.value
                                if not self._compare_values(actual_value, operator, expected):
                                    all_errors.append(f"{path_expr} = {actual_value}, 期望{operator}{expected}")
                                    break
                    else:
                        path_expr = condition
                        jsonpath_expr = parse(path_expr)
                        matches = list(jsonpath_expr.find(data))
                        if not matches:
                            all_errors.append(f"路径'{path_expr}'未找到数据")
                except Exception:
                    pass
            
            # 报告错误（优先报告值不匹配的错误）
            if all_errors:
                value_mismatch_errors = [e for e in all_errors if '期望' in e or ('=' in e and '未找到数据' not in e)]
                if value_mismatch_errors:
                    return f"JSONPath断言失败: {value_mismatch_errors[0]}"
                else:
                    return f"JSONPath断言失败: OR条件中所有路径都未找到数据: {assertion}"
            else:
                return f"JSONPath断言失败: 所有OR条件都不满足: {assertion}"
        
        # 简单断言（没有OR逻辑）
        pattern = r'(\$[^\s]+)\s*(==|!=|>=|<=|>|<)\s*(".*?"|\'.*?\'|[^\s]+)'
        match = re.match(pattern, assertion)
        
        if match:
            path_expr = match.group(1)
            operator = match.group(2)
            expected = match.group(3)
            
            # 去除引号
            if expected.startswith('"') and expected.endswith('"'):
                expected = expected[1:-1]
            elif expected.startswith("'") and expected.endswith("'"):
                expected = expected[1:-1]
            
            # 执行JSONPath查询
            try:
                jsonpath_expr = parse(path_expr)
                matches = jsonpath_expr.find(data)
                
                if not matches:
                    return f"JSONPath断言失败: 路径'{path_expr}'未找到数据"
                
                # 检查每个匹配值
                for match_obj in matches:
                    actual_value = match_obj.value
                    if not self._compare_values(actual_value, operator, expected):
                        return f"JSONPath断言失败: {path_expr} = {actual_value}, 期望{operator}{expected}"
                
                LOG.info(f"[HTTP] JSONPath断言通过: {assertion}")
                return None
                
            except Exception as e:
                return f"JSONPath断言失败: {e}"
        else:
            # 简单存在性断言
            path_expr = assertion
            try:
                jsonpath_expr = parse(path_expr)
                matches = jsonpath_expr.find(data)
                if not matches:
                    return f"JSONPath断言失败: 路径'{path_expr}'未找到数据"
                else:
                    LOG.info(f"[HTTP] JSONPath断言通过: {path_expr}")
                    return None
            except Exception as e:
                return f"JSONPath断言失败: {e}"
    
    def _compare_values(self, actual: Any, operator: str, expected: str) -> bool:
        """比较值"""
        try:
            # 尝试转换为数字
            try:
                actual_num = float(actual)
                expected_num = float(expected)
                is_numeric = True
            except:
                is_numeric = False
            
            if operator == '==':
                return str(actual) == expected
            elif operator == '!=':
                return str(actual) != expected
            elif operator == '>=' and is_numeric:
                return actual_num >= expected_num
            elif operator == '<=' and is_numeric:
                return actual_num <= expected_num
            elif operator == '>' and is_numeric:
                return actual_num > expected_num
            elif operator == '<' and is_numeric:
                return actual_num < expected_num
            else:
                return True
        except:
            return False
