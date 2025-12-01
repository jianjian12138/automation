"""
测试执行器
支持YAML用例解析和执行
支持智能测试数据管理（Mock、数据库、Redis）
支持多协议：HTTP、WebService、WebSocket、Dubbo、MQTT
"""
import yaml
import requests
import json
from pathlib import Path
from typing import Dict, List, Any
from jsonpath_ng import parse
import logging
import re

logging.basicConfig(level=logging.INFO)
LOG = logging.getLogger(__name__)

# 导入协议适配器
try:
    from core.protocols import (
        HTTPAdapter,
        WebServiceAdapter,
        WebSocketAdapter,
        DubboAdapter,
        SQLAdapter,
        RedisAdapter
    )
    # 尝试导入MQTT适配器（可选）
    try:
        from core.protocols import MQTTAdapter
    except ImportError:
        MQTTAdapter = None
        LOG.debug("MQTT适配器未安装，跳过")
    PROTOCOL_SUPPORT = True
except ImportError as e:
    PROTOCOL_SUPPORT = False
    LOG.warning(f"未能导入协议适配器: {e}")

# 使用AI_TEST框架自己的token工具（不使用autotest_elegant）
try:
    from libs.genter_auth_tokens import generate_token as gen_token_func
    from libs.config_center import ENV
    AUTOTEST_SUPPORT = True
    LOG.info("✓ 成功导入token工具")
except ImportError as e:
    AUTOTEST_SUPPORT = False
    LOG.warning(f"未能导入token工具: {e}")

# 导入智能数据管理工具
try:
    from libs.smart_mock import mock_data as smart_mock
    from libs.smart_db import query_db_field
    from libs.smart_redis import get_redis_data, get_cache_var
    SMART_DATA_SUPPORT = True
    LOG.info("✓ 成功导入智能数据管理工具")
except ImportError as e:
    SMART_DATA_SUPPORT = False
    LOG.warning(f"未能导入智能数据管理工具: {e}")


class APITestExecutor:
    """API测试执行器"""
    
    def __init__(self, config_path: str = "config/environment.yaml", enable_cleanup: bool = True):
        """
        初始化执行器
        
        :param config_path: 配置文件路径
        :param enable_cleanup: 是否启用数据清理功能
        """
        self.config = self._load_config(config_path)
        self.variables = {}  # 全局变量存储
        self.session = requests.Session()
        self.enable_cleanup = enable_cleanup  # 是否启用数据清理
        self.inserted_data = []  # 存储插入的数据信息，用于清理
        
        # 初始化协议适配器
        self.protocol_adapters = {}
        if PROTOCOL_SUPPORT:
            # 传入变量处理函数，使HTTP适配器支持$get_host等函数调用
            self.protocol_adapters['http'] = HTTPAdapter(
                self.config.get('http', {}), 
                variable_processor=self._process_variables
            )
            self.protocol_adapters['https'] = self.protocol_adapters['http']  # HTTPS使用HTTP适配器
            self.protocol_adapters['webservice'] = WebServiceAdapter(self.config.get('webservice', {}))
            self.protocol_adapters['soap'] = self.protocol_adapters['webservice']  # SOAP是WebService的别名
            self.protocol_adapters['websocket'] = WebSocketAdapter(self.config.get('websocket', {}))
            self.protocol_adapters['ws'] = self.protocol_adapters['websocket']  # ws是websocket的别名
            self.protocol_adapters['wss'] = self.protocol_adapters['websocket']  # wss是websocket的别名
            self.protocol_adapters['dubbo'] = DubboAdapter(self.config.get('dubbo', {}))
            # 可选注册MQTT适配器
            if MQTTAdapter is not None:
                self.protocol_adapters['mqtt'] = MQTTAdapter(self.config.get('mqtt', {}))
            # 新增SQL和Redis适配器
            self.protocol_adapters['sql'] = SQLAdapter(self.config.get('sql', {}))
            self.protocol_adapters['redis'] = RedisAdapter(self.config.get('redis', {}))
            
            # 设置变量到所有适配器
            for adapter in self.protocol_adapters.values():
                adapter.set_variables(self.variables)
        
    def _load_config(self, config_path: str) -> Dict:
        """加载配置文件"""
        config_file = Path(config_path)
        if config_file.exists():
            with open(config_file, 'r', encoding='utf-8') as f:
                return yaml.safe_load(f)
        return {}
    
    def _get_config_value(self, config_path: str) -> Any:
        """
        从配置文件读取参数值
        支持路径格式: quantity.normal.min 或 unitPrice.boundary.max_value
        
        :param config_path: 配置路径，如 'quantity.normal.min' 或 'unitPrice.boundary.max_value'
        :return: 配置值
        """
        try:
            # 默认配置文件路径
            config_file = Path("config/contract_test_params.yaml")
            if not config_file.exists():
                LOG.warning(f"配置文件不存在: {config_file}")
                return None
            
            # 加载配置文件
            with open(config_file, 'r', encoding='utf-8') as f:
                config_data = yaml.safe_load(f)
            
            if not config_data:
                return None
            
            # 按路径解析配置值
            # 支持路径格式: quantity.normal.min 或 unitPrice.boundary.max_value
            keys = config_path.split('.')
            value = config_data
            
            for key in keys:
                if isinstance(value, dict) and key in value:
                    value = value[key]
                else:
                    LOG.warning(f"配置路径不存在: {config_path}")
                    return None
            
            return value
        except Exception as e:
            LOG.error(f"读取配置文件失败: {e}")
            return None
        
    def load_case(self, case_path: str) -> Dict:
        """
        加载测试用例
        
        :param case_path: 用例文件路径
        :return: 用例字典
        """
        LOG.info(f"加载测试用例: {case_path}")
        with open(case_path, 'r', encoding='utf-8') as f:
            return yaml.safe_load(f)
            
    def execute_case(self, case_path: str) -> Dict:
        """
        执行测试用例
        
        :param case_path: 用例文件路径
        :return: 执行结果
        """
        case = self.load_case(case_path)
        LOG.info(f"开始执行用例: {case.get('case_name', 'Unknown')}")
        
        # 清空之前的数据记录
        self.inserted_data = []
        
        results = {
            'case_name': case.get('case_name'),
            'case_code': case.get('case_code'),
            'total_steps': len(case.get('steps', [])),
            'passed_steps': 0,
            'failed_steps': 0,
            'step_results': []
        }
        
        try:
            for step in case.get('steps', []):
                # 检查是否需要跳过步骤（条件判断）
                skip_when = step.get('skip_when')
                if skip_when:
                    step_name = step.get('step_name', 'Unknown Step')
                    LOG.debug(f"评估跳过条件: {step_name}, 条件: {skip_when}, 当前productCode值: {self.variables.get('productCode')}")
                    should_skip = self._evaluate_skip_condition(skip_when)
                    LOG.debug(f"跳过条件评估结果: {should_skip}")
                    if should_skip:
                        LOG.info(f"跳过步骤（条件满足）: {step_name}, 条件: {skip_when}")
                        step_result = {
                            'step_name': step_name,
                            'passed': True,
                            'errors': [],
                            'duration': 0.0,
                            'skipped': True,
                            'skip_reason': skip_when
                        }
                        results['step_results'].append(step_result)
                        results['passed_steps'] += 1
                        continue
                
                # 检查是否需要重试直到条件满足
                retry_until = step.get('retry_until')
                if retry_until:
                    # 创建条件检查函数
                    def condition_check(result):
                        """检查重试条件是否满足"""
                        try:
                            # 支持多种条件格式
                            if isinstance(retry_until, dict):
                                # 字典格式：检查变量是否有值
                                var_name = retry_until.get('variable')
                                if var_name:
                                    var_value = self.variables.get(var_name)
                                    # 检查变量是否有值（不为None、空字符串、空列表）
                                    if var_value is not None and var_value != '' and var_value != []:
                                        return True
                                # 检查JSONPath表达式
                                jsonpath_expr = retry_until.get('jsonpath')
                                if jsonpath_expr:
                                    response = result.get('response')
                                    if response:
                                        try:
                                            response_dict = response.json() if hasattr(response, 'json') else response
                                            # 支持比较操作符（如 $..resCode == 200）
                                            expected_value = None
                                            actual_jsonpath_expr = jsonpath_expr
                                            if '==' in jsonpath_expr:
                                                # 分离JSONPath表达式和比较值
                                                import re
                                                parts = re.split(r'\s*==\s*', jsonpath_expr, 1)
                                                if len(parts) == 2:
                                                    actual_jsonpath_expr = parts[0].strip()
                                                    try:
                                                        # 尝试解析期望值（支持数字和字符串）
                                                        expected_value_str = parts[1].strip()
                                                        if expected_value_str.isdigit():
                                                            expected_value = int(expected_value_str)
                                                        elif expected_value_str.replace('.', '', 1).isdigit():
                                                            expected_value = float(expected_value_str)
                                                        else:
                                                            expected_value = expected_value_str.strip('"\'')
                                                    except Exception:
                                                        expected_value = parts[1].strip()
                                            
                                            jsonpath_expr_parsed = parse(actual_jsonpath_expr)
                                            matches = [match.value for match in jsonpath_expr_parsed.find(response_dict)]
                                            if matches and matches[0] is not None:
                                                actual_val = matches[0]
                                                # 如果指定了期望值，检查是否等于期望值
                                                if expected_value is not None:
                                                    # 尝试转换为相同类型进行比较
                                                    try:
                                                        if isinstance(actual_val, str) and isinstance(expected_value, (int, float)):
                                                            actual_val = int(actual_val) if isinstance(expected_value, int) else float(actual_val)
                                                        elif isinstance(actual_val, (int, float)) and isinstance(expected_value, str):
                                                            expected_value = int(expected_value) if expected_value.isdigit() else float(expected_value)
                                                        if actual_val == expected_value:
                                                            return True
                                                    except (ValueError, TypeError):
                                                        # 如果转换失败，直接比较
                                                        if actual_val == expected_value:
                                                            return True
                                                # 如果没有指定期望值，只检查值是否存在
                                                elif actual_val != '' and actual_val != []:
                                                    return True
                                        except Exception as e:
                                            LOG.debug(f"检查JSONPath条件失败: {e}")
                            elif isinstance(retry_until, str):
                                # 字符串格式：直接检查变量是否有值
                                var_value = self.variables.get(retry_until)
                                if var_value is not None and var_value != '' and var_value != []:
                                    return True
                            return False
                        except Exception as e:
                            LOG.error(f"检查重试条件失败: {e}")
                            return False
                    
                    step_result = self._retry_step_until_condition(step, condition_check)
                else:
                    step_result = self._execute_step(step)
                results['step_results'].append(step_result)
                
                # 跟踪插入的数据
                if self.enable_cleanup:
                    self._track_inserted_data(step, step_result)
                
                if step_result['passed']:
                    results['passed_steps'] += 1
                else:
                    results['failed_steps'] += 1
        finally:
            # 执行数据清理
            if self.enable_cleanup:
                self._cleanup_inserted_data()
                
        results['passed'] = results['failed_steps'] == 0
        LOG.info(f"用例执行完成: {results['passed_steps']}/{results['total_steps']} 通过")
        
        return results
        
    def _evaluate_skip_condition(self, skip_when: Any) -> bool:
        """
        评估跳过条件 - 通用条件判断方法
        
        支持多种条件格式，简单实用：
        
        1. 字符串格式（向后兼容）:
           - 简单变量名: "var_name" - 变量为空/None/不存在则跳过
           - 表达式字符串: '"value" not in var_name' - 支持表达式判断
        
        2. 字典格式（推荐，功能更强大）:
           - 单条件:
             {
               "var": "var_name",
               "operator": "not_contains",  # 操作符
               "value": "expected_value"     # 期望值（可选）
             }
           
           - 组合条件（AND/OR）:
             {
               "logic": "or",  # and 或 or
               "conditions": [
                 {"var": "var1", "operator": "empty"},
                 {"var": "var2", "operator": "equals", "value": "test"}
               ]
             }
        
        支持的操作符（operator）:
        - empty: 变量为空（None/[]/''）
        - not_empty: 变量不为空
        - is_none: 变量为 None
        - is_not_none: 变量不为 None
        - equals: 等于（需要 value）
        - not_equals: 不等于（需要 value）
        - contains: 包含（需要 value，适用于 list/str）
        - not_contains: 不包含（需要 value，适用于 list/str）
        - in: 值在变量中（需要 value，value 在 var 中）
        - not_in: 值不在变量中（需要 value，value 不在 var 中）
        - gt: 大于（需要 value，数值比较）
        - gte: 大于等于（需要 value，数值比较）
        - lt: 小于（需要 value，数值比较）
        - lte: 小于等于（需要 value，数值比较）
        
        :param skip_when: 跳过条件配置
        :return: True 表示应该跳过步骤，False 表示不跳过
        """
        try:
            # 1. 字符串格式（向后兼容）
            if isinstance(skip_when, str):
                return self._evaluate_string_condition(skip_when)
            
            # 2. 字典格式
            elif isinstance(skip_when, dict):
                # 检查是否是组合条件
                if 'logic' in skip_when and 'conditions' in skip_when:
                    return self._evaluate_combined_condition(skip_when)
                # 单条件
                else:
                    return self._evaluate_single_condition(skip_when)
            
            # 3. 列表格式（多个条件，默认 OR）
            elif isinstance(skip_when, list):
                # 如果任一条件满足，则跳过
                return any(self._evaluate_single_condition(cond) for cond in skip_when)
            
            return False
        except Exception as e:
            LOG.error(f"评估跳过条件失败: {skip_when}, 错误: {e}")
            return False
    
    def _evaluate_string_condition(self, condition: str) -> bool:
        """
        评估字符串格式的条件（向后兼容）
        
        :param condition: 条件字符串
        :return: 是否跳过
        """
        condition = condition.strip()
        
        # 检查是否是表达式（包含操作符）
        if any(op in condition for op in [' is ', ' == ', ' != ', ' in ', ' not in ', ' > ', ' < ', ' >= ', ' <= ']):
            return self._evaluate_expression(condition)
        else:
            # 简单的变量名，检查是否不存在或为空
            value = self.variables.get(condition)
            return self._is_empty(value)
    
    def _evaluate_single_condition(self, condition: dict) -> bool:
        """
        评估单个条件
        
        :param condition: 条件字典
        :return: 是否跳过
        """
        var_name = condition.get('var', '')
        operator = condition.get('operator', 'empty')
        expected_value = condition.get('value', None)
        
        if not var_name:
            LOG.warning(f"条件配置缺少 var 字段: {condition}")
            return False
        
        # 获取变量值
        var_value = self.variables.get(var_name)
        
        # 根据操作符判断
        try:
            if operator == 'empty':
                return self._is_empty(var_value)
            elif operator == 'not_empty':
                return not self._is_empty(var_value)
            elif operator == 'is_none':
                return var_value is None
            elif operator == 'is_not_none':
                return var_value is not None
            elif operator == 'equals':
                return var_value == expected_value
            elif operator == 'not_equals':
                return var_value != expected_value
            elif operator == 'contains':
                return self._check_contains(var_value, expected_value)
            elif operator == 'not_contains':
                return not self._check_contains(var_value, expected_value)
            elif operator == 'in':
                # value 在 var_value 中
                return self._check_contains(var_value, expected_value)
            elif operator == 'not_in':
                # value 不在 var_value 中
                return not self._check_contains(var_value, expected_value)
            elif operator == 'gt':
                return self._compare_numeric(var_value, expected_value, '>')
            elif operator == 'gte':
                return self._compare_numeric(var_value, expected_value, '>=')
            elif operator == 'lt':
                return self._compare_numeric(var_value, expected_value, '<')
            elif operator == 'lte':
                return self._compare_numeric(var_value, expected_value, '<=')
            else:
                LOG.warning(f"不支持的操作符: {operator}")
                return False
        except Exception as e:
            LOG.error(f"评估条件失败: {condition}, 错误: {e}")
            return False
    
    def _evaluate_combined_condition(self, condition: dict) -> bool:
        """
        评估组合条件（AND/OR）
        
        :param condition: 组合条件配置
        :return: 是否跳过
        """
        logic = condition.get('logic', 'or').lower()
        conditions = condition.get('conditions', [])
        
        if not conditions:
            return False
        
        results = [self._evaluate_single_condition(cond) for cond in conditions]
        
        if logic == 'and':
            return all(results)
        else:  # or
            return any(results)
    
    def _is_empty(self, value: Any) -> bool:
        """判断值是否为空"""
        return value is None or value == [] or value == '' or (isinstance(value, dict) and len(value) == 0)
    
    def _check_contains(self, var_value: Any, expected_value: Any) -> bool:
        """检查变量是否包含期望值"""
        if var_value is None:
            return False
        if isinstance(var_value, (list, str, dict)):
            return expected_value in var_value
        return False
    
    def _compare_numeric(self, var_value: Any, expected_value: Any, operator: str) -> bool:
        """数值比较"""
        try:
            var_num = float(var_value) if var_value is not None else 0
            expected_num = float(expected_value) if expected_value is not None else 0
            
            if operator == '>':
                return var_num > expected_num
            elif operator == '>=':
                return var_num >= expected_num
            elif operator == '<':
                return var_num < expected_num
            elif operator == '<=':
                return var_num <= expected_num
            return False
        except (ValueError, TypeError):
            return False
    
    def _evaluate_expression(self, expression: str) -> bool:
        """
        评估表达式字符串（向后兼容）
        支持: "var_name is None", "var_name == []", '"value" not in var_name'
        支持函数调用: "$get_variable(var_name) != null"
        
        :param expression: 表达式字符串
        :return: 表达式结果
        """
        try:
            # 先处理函数调用，将函数调用替换为变量值
            # 支持 $get_variable(var_name) 格式
            import re
            var_pattern = r'\$get_variable\s*\(\s*([^)]+)\s*\)'
            def replace_var(match):
                var_name = match.group(1).strip().strip('"').strip("'")
                var_value = self.variables.get(var_name)
                # 返回字符串表示，用于后续比较
                if var_value is None:
                    return 'None'
                elif var_value == '':
                    return "''"
                elif var_value == []:
                    return '[]'
                else:
                    return repr(var_value)
            
            # 替换所有 $get_variable(...) 调用
            original_expression = expression
            expression = re.sub(var_pattern, replace_var, expression)
            LOG.debug(f"表达式替换: {original_expression} -> {expression}")
            
            # 处理 "var_name is None" 或 "None is None"
            if ' is None' in expression:
                parts = expression.split(' is None')
                if len(parts) == 2:
                    var_expr = parts[0].strip()
                    # 如果是字符串 'None'，表示变量不存在或为None
                    if var_expr == 'None' or var_expr == "'None'":
                        return True
                    # 否则尝试从变量中获取
                    var_value = self.variables.get(var_expr)
                    return var_value is None
            
            # 处理 "var_name == []" 或 "[] == []"
            if ' == []' in expression:
                parts = expression.split(' == []')
                if len(parts) == 2:
                    var_expr = parts[0].strip()
                    if var_expr == '[]' or var_expr == "'[]'":
                        return True
                    var_value = self.variables.get(var_expr)
                    return var_value == []
            
            # 处理 '"value" not in var_name' 或 '"value" in var_name'
            if ' not in ' in expression:
                parts = expression.split(' not in ')
                if len(parts) == 2:
                    value_expr = parts[0].strip().strip('"').strip("'")
                    var_name = parts[1].strip()
                    var_value = self.variables.get(var_name)
                    if var_value is None or var_value == []:
                        return True
                    return not self._check_contains(var_value, value_expr)
            
            if ' in ' in expression and ' not in ' not in expression:
                parts = expression.split(' in ')
                if len(parts) == 2:
                    value_expr = parts[0].strip().strip('"').strip("'")
                    var_name = parts[1].strip()
                    var_value = self.variables.get(var_name)
                    if var_value is None or var_value == []:
                        return False
                    return self._check_contains(var_value, value_expr)
            
            # 处理 "var_name == value" 或 "var_name != value"
            # 支持 "None != null" 或 "'' != ''" 等
            if ' == ' in expression:
                parts = expression.split(' == ')
                if len(parts) == 2:
                    var_expr = parts[0].strip()
                    expected_value = parts[1].strip().strip('"').strip("'")
                    
                    # 处理特殊值
                    if var_expr == 'None' or var_expr == "'None'":
                        return expected_value == 'None' or expected_value == 'null'
                    elif var_expr == "''" or var_expr == '""':
                        return expected_value == '' or expected_value == "''" or expected_value == '""'
                    elif var_expr == '[]':
                        return expected_value == '[]'
                    
                    # 尝试从变量中获取
                    var_value = self.variables.get(var_expr)
                    if var_value is None:
                        return expected_value == 'None' or expected_value == 'null'
                    return str(var_value) == expected_value
            
            if ' != ' in expression:
                parts = expression.split(' != ')
                if len(parts) == 2:
                    var_expr = parts[0].strip()
                    expected_value = parts[1].strip().strip('"').strip("'")
                    
                    # 处理特殊值
                    if var_expr == 'None' or var_expr == "'None'":
                        return expected_value != 'None' and expected_value != 'null'
                    elif var_expr == "''" or var_expr == '""':
                        return expected_value != '' and expected_value != "''" and expected_value != '""'
                    elif var_expr == '[]':
                        return expected_value != '[]'
                    
                    # 尝试从变量中获取
                    var_value = self.variables.get(var_expr)
                    if var_value is None:
                        return expected_value != 'None' and expected_value != 'null'
                    return str(var_value) != expected_value
            
            # 处理 "and" 和 "or" 逻辑
            if ' and ' in expression:
                parts = expression.split(' and ')
                if len(parts) == 2:
                    return self._evaluate_expression(parts[0].strip()) and self._evaluate_expression(parts[1].strip())
            
            if ' or ' in expression:
                parts = expression.split(' or ')
                if len(parts) == 2:
                    return self._evaluate_expression(parts[0].strip()) or self._evaluate_expression(parts[1].strip())
            
            return False
        except Exception as e:
            LOG.error(f"评估表达式失败: {expression}, 错误: {e}")
            return False
    
    def _retry_step_until_condition(self, step: Dict, condition_check: callable, max_retries: int = None, retry_delay: int = None) -> Dict:
        """
        重试步骤直到条件满足
        
        :param step: 步骤配置
        :param condition_check: 条件检查函数，返回True表示条件满足，停止重试
        :param max_retries: 最大重试次数，None时从config读取
        :param retry_delay: 重试间隔（秒），None时从config读取
        :return: 最后一次执行结果
        """
        import time
        
        # 从config读取重试配置
        if max_retries is None:
            try:
                config_file = Path("config/config.yaml")
                if config_file.exists():
                    with open(config_file, 'r', encoding='utf-8') as f:
                        config_data = yaml.safe_load(f)
                        max_retries = config_data.get('api', {}).get('step_retry', {}).get('max_retries', 10)
                else:
                    max_retries = 10
            except Exception as e:
                LOG.warning(f"读取重试配置失败: {e}, 使用默认值10")
                max_retries = 10
        
        if retry_delay is None:
            try:
                config_file = Path("config/config.yaml")
                if config_file.exists():
                    with open(config_file, 'r', encoding='utf-8') as f:
                        config_data = yaml.safe_load(f)
                        retry_delay = config_data.get('api', {}).get('step_retry', {}).get('retry_delay', 1)
                else:
                    retry_delay = 1
            except Exception as e:
                LOG.warning(f"读取重试延迟配置失败: {e}, 使用默认值1秒")
                retry_delay = 1
        
        step_name = step.get('step_name', 'Unknown Step')
        LOG.info(f"开始重试步骤: {step_name}, 最大重试次数: {max_retries}, 重试间隔: {retry_delay}秒")
        
        last_result = None
        for attempt in range(max_retries):
            if attempt > 0:
                LOG.info(f"第 {attempt + 1} 次重试步骤: {step_name}")
                time.sleep(retry_delay)
            
            # 执行步骤
            result = self._execute_step(step)
            last_result = result
            
            # 检查条件（注意：变量提取在_execute_step中已经完成）
            if condition_check(result):
                LOG.info(f"✓ 步骤重试成功: {step_name}, 第 {attempt + 1} 次尝试后条件满足")
                return result
            else:
                if attempt < max_retries - 1:
                    LOG.info(f"步骤重试条件未满足: {step_name}, 第 {attempt + 1} 次尝试，将继续重试...")
                else:
                    LOG.warning(f"步骤重试条件未满足: {step_name}, 第 {attempt + 1} 次尝试，已达到最大重试次数")
        
        LOG.warning(f"步骤重试达到最大次数: {step_name}, 已重试 {max_retries} 次")
        return last_result
    
    def _execute_step(self, step: Dict) -> Dict:
        """
        执行单个步骤
        
        :param step: 步骤配置
        :return: 执行结果
        """
        import time
        step_name = step.get('step_name', 'Unknown Step')
        LOG.info(f"执行步骤: {step_name}")
        
        result = {
            'step_name': step_name,
            'passed': False,
            'errors': [],
            'duration': 0.0,
            'start_time': time.time()
        }
        
        try:
            # 1. 确定使用的协议
            protocol = step.get('protocol', 'http').lower()
            
            # 如果未指定协议，尝试从步骤类型推断
            if protocol == 'http' and 'protocol' not in step:
                # 检查是否有SQL相关字段
                if step.get('sql'):
                    protocol = 'sql'
                # 检查是否有Redis相关字段
                elif step.get('operation') in ['get', 'set', 'delete', 'del', 'exists', 'keys', 'hget', 'hset', 'hgetall', 'ttl']:
                    protocol = 'redis'
                # 从URL推断
                else:
                    host = self._process_variables(step.get('host', ''))
                    if host:
                        if host.startswith('ws://') or host.startswith('wss://'):
                            protocol = 'websocket'
                        elif 'dubbo' in host.lower() or step.get('port', 0) == 20880:
                            protocol = 'dubbo'
                        elif step.get('topic') or step.get('action') in ['publish', 'subscribe']:
                            protocol = 'mqtt'
            
            # 2. 获取对应的协议适配器
            adapter = self._get_protocol_adapter(protocol)
            
            # 如果不支持多协议或协议适配器不可用，回退到原有HTTP处理
            if not adapter and protocol in ['http', 'https']:
                # 保存请求信息
                host = self._process_variables(step.get('host', ''))
                path = self._process_variables(step.get('path', step.get('url', '')))
                method = step.get('method', 'GET').upper()
                headers_raw = step.get('headers', {})
                if isinstance(headers_raw, str):
                    headers = self._process_variables(headers_raw)
                else:
                    headers = self._process_dict(headers_raw)
                params = self._process_dict(step.get('params', {}))
                data = self._process_dict(step.get('data', {}))
                full_url = f"{host}{path}" if host else path
                
                result['request'] = {
                    'method': method,
                    'url': full_url,
                    'host': host,
                    'path': path,
                    'headers': headers,
                    'params': params,
                    'data': data
                }
                
                # 使用原有的HTTP请求逻辑（向后兼容）
                response_dict = self._send_http_request_legacy(step)
                result['response'] = response_dict
                
                # 执行断言
                assert_config = step.get('response_assert', {})
                self._assert_response_legacy(response_dict, assert_config, result)
                
                # 提取变量
                if 'extract' in step:
                    # 保存当前步骤的请求数据，供extract使用
                    self.current_request_data = result.get('request', {})
                    self._extract_variables_legacy(response_dict, step['extract'])
            else:
                if not adapter:
                    raise ValueError(f"不支持的协议: {protocol}")
                
                # 更新适配器的变量
                adapter.set_variables(self.variables)
                
                # 3. 保存请求信息（在发送请求前保存，以便在报告中显示）
                host = self._process_variables(step.get('host', ''))
                path = self._process_variables(step.get('path', step.get('url', '')))
                method = step.get('method', 'GET').upper()
                headers_raw = step.get('headers', {})
                if isinstance(headers_raw, str):
                    headers = self._process_variables(headers_raw)
                else:
                    headers = self._process_dict(headers_raw)
                params = self._process_dict(step.get('params', {}))
                data = self._process_dict(step.get('data', {}))
                full_url = f"{host}{path}" if host else path
                
                result['request'] = {
                    'method': method,
                    'url': full_url,
                    'host': host,
                    'path': path,
                    'headers': headers,
                    'params': params,
                    'data': data
                }
                
                # 4. 发送请求
                response = adapter.send_request(step)
                
                # 5. 保存响应
                result['response'] = response
                
                # 6. 执行断言
                assert_config = step.get('response_assert', {})
                errors = adapter.validate_response(response, assert_config)
                result['errors'].extend(errors)
                
                # 7. 提取变量
                if 'extract' in step:
                    # 统一使用_extract_variables_legacy方法处理变量提取（所有协议都使用这个方法）
                    # 保存当前步骤的响应数据，供extract使用
                    self.current_response_data = response
                    # 对于HTTP协议，response是dict格式，需要转换为legacy格式
                    if protocol in ['http', 'https']:
                        # HTTP适配器返回的response格式已经是dict，直接使用
                        response_dict = response
                    else:
                        # 其他协议也转换为dict格式
                        response_dict = response if isinstance(response, dict) else {'body': str(response), 'json': None}
                    self._extract_variables_legacy(response_dict, step['extract'])
                # 更新适配器变量
                adapter.set_variables(self.variables)
                
            result['passed'] = len(result['errors']) == 0
            
        except Exception as e:
            LOG.error(f"步骤执行失败: {e}")
            result['errors'].append(str(e))
        finally:
            # 计算执行时间
            if 'start_time' in result:
                result['duration'] = time.time() - result['start_time']
            
        return result
    
    def _get_protocol_adapter(self, protocol: str):
        """
        获取协议适配器
        
        :param protocol: 协议名称
        :return: 协议适配器实例
        """
        if not PROTOCOL_SUPPORT:
            # 如果不支持多协议，回退到HTTP
            if protocol in ['http', 'https']:
                # 使用原有的HTTP请求方式
                return None
            else:
                raise ValueError(f"协议适配器未启用，无法使用协议: {protocol}")
        
        adapter = self.protocol_adapters.get(protocol.lower())
        if not adapter:
            # 默认使用HTTP适配器
            LOG.warning(f"未找到协议适配器: {protocol}，使用HTTP适配器")
            adapter = self.protocol_adapters.get('http')
        
        return adapter
    
    def _send_http_request_legacy(self, step: Dict) -> Dict:
        """
        发送HTTP请求（向后兼容方法）
        当协议适配器不可用时使用
        """
        method = step.get('method', 'GET').upper()
        url = self._process_variables(step.get('path', step.get('url', '')))
        
        # 处理headers - 可能是字符串（变量）或字典
        headers_raw = step.get('headers', {})
        if isinstance(headers_raw, str):
            headers = self._process_variables(headers_raw)
        else:
            headers = self._process_dict(headers_raw)
            
        params = self._process_dict(step.get('params', {}))
        data = self._process_dict(step.get('data', {}))
        
        # 获取完整URL
        host = self._process_variables(step.get('host', ''))
        if host:
            full_url = f"{host}{url}"
        else:
            full_url = url
        
        # 调试：记录请求数据（特别是数组类型的字段）
        import json
        data_str = json.dumps(data, ensure_ascii=False, indent=2) if data else '{}'
        LOG.debug(f"请求数据: {data_str}")
        if 'sellProductCodes' in data:
            LOG.info(f"✓ sellProductCodes字段类型: {type(data.get('sellProductCodes'))}, 值: {data.get('sellProductCodes')}")
            
        LOG.info(f"请求: {method} {full_url}")
        
        try:
            response = self.session.request(
                method=method,
                url=full_url,
                headers=headers,
                params=params,
                json=data if method in ['POST', 'PUT', 'PATCH'] else None,
                timeout=30
            )
            
            return {
                'status_code': response.status_code,
                'headers': dict(response.headers),
                'body': response.text,
                'json': response.json() if response.text else None
            }
        except Exception as e:
            LOG.error(f"HTTP请求失败: {e}")
            return {
                'status_code': 0,
                'headers': {},
                'body': '',
                'json': None,
                'error': str(e)
            }
    
    def _assert_response_legacy(self, response_dict: Dict, assert_config: Dict, result: Dict):
        """
        执行响应断言（向后兼容方法）
        """
        # 状态码断言
        if 'status_code_assert' in assert_config:
            expected_code = assert_config['status_code_assert']
            actual_code = response_dict.get('status_code', 0)
            if actual_code != expected_code:
                error = f"状态码断言失败: 期望{expected_code}, 实际{actual_code}"
                LOG.error(error)
                result['errors'].append(error)
            else:
                LOG.info(f"状态码断言通过: {actual_code}")
                
        # 响应消息断言
        if 'response_assert_data' in assert_config:
            expected_msg = assert_config['response_assert_data']
            response_body = response_dict.get('body', '')
            if expected_msg not in response_body:
                error = f"响应消息断言失败: 未找到'{expected_msg}'"
                LOG.error(error)
                result['errors'].append(error)
            else:
                LOG.info(f"响应消息断言通过")
                
        # JSONPath断言
        if 'jsonpath_assert' in assert_config:
            try:
                response_json = response_dict.get('json')
                if response_json:
                    for assertion in assert_config['jsonpath_assert']:
                        self._assert_jsonpath(response_json, assertion, result)
                else:
                    error = "JSONPath断言失败: 响应不是有效的JSON格式"
                    LOG.error(error)
                    result['errors'].append(error)
            except Exception as e:
                error = f"JSONPath断言失败: {e}"
                LOG.error(error)
                result['errors'].append(error)
    
    def _extract_variables_legacy(self, response_dict: Dict, extracts: List):
        """
        提取变量（向后兼容方法）
        支持:
        - $set_variable(var_name, $get_response_data(jsonpath))
        """
        response_json = response_dict.get('json')
        if not response_json:
            try:
                body = response_dict.get('body', '')
                if body:
                    response_json = json.loads(body)
            except Exception as e:
                LOG.warning(f"解析响应JSON失败: {e}")
                response_json = None
        
        if not response_json:
            LOG.warning(f"响应数据为空，无法提取变量")
            return
        
        LOG.info(f"开始提取变量，响应JSON类型: {type(response_json)}, 是否有data字段: {'data' in response_json if isinstance(response_json, dict) else 'N/A'}")
        
        for extract_expr in extracts:
            if isinstance(extract_expr, dict) and 'extract' in extract_expr:
                expr = extract_expr['extract']
                LOG.info(f"提取变量: {expr}")
                
                # 解析 $set_variable(var_name, value_expr)
                # 修复：需要匹配完整的value_expr，即使包含括号
                # 先找到逗号位置，然后从逗号后匹配到最后一个右括号
                set_var_pattern = r'\$set_variable\s*\(\s*([^,]+)\s*,\s*(.+)\s*\)'
                match = re.match(set_var_pattern, expr)
                if match:
                    var_name = match.group(1).strip().strip('"').strip("'")
                    value_expr = match.group(2).strip()
                    # 如果value_expr以$get_response_data开头，需要确保提取完整的括号内容
                    if value_expr.startswith('$get_response_data'):
                        # 找到第一个左括号，然后找到匹配的右括号
                        start_idx = value_expr.find('(')
                        if start_idx != -1:
                            paren_count = 0
                            for i in range(start_idx, len(value_expr)):
                                if value_expr[i] == '(':
                                    paren_count += 1
                                elif value_expr[i] == ')':
                                    paren_count -= 1
                                    if paren_count == 0:
                                        value_expr = value_expr[:i+1]
                                        break
                    
                    # 处理 $get_response_data(jsonpath)
                    if value_expr.startswith('$get_response_data'):
                        LOG.info(f"匹配到$get_response_data表达式: {value_expr}")
                        # 修复正则表达式：使用非贪婪匹配，但确保匹配到完整的括号
                        # 匹配 $get_response_data(...) 格式，其中...可能包含括号
                        get_data_pattern = r'\$get_response_data\s*\(\s*(.+?)\s*\)\s*$'
                        data_match = re.match(get_data_pattern, value_expr)
                        if not data_match:
                            # 如果正则匹配失败，尝试更宽松的匹配
                            get_data_pattern = r'\$get_response_data\s*\(\s*(.+)\s*\)'
                            data_match = re.search(get_data_pattern, value_expr)
                        if data_match:
                            LOG.info(f"正则表达式匹配成功")
                            jsonpath_expr = data_match.group(1).strip()
                            LOG.info(f"提取的JSONPath表达式: {jsonpath_expr}")
                            
                            try:
                                jsonpath_parse = parse(jsonpath_expr)
                                matches = jsonpath_parse.find(response_json)
                                LOG.info(f"JSONPath查询: {jsonpath_expr}, 找到 {len(matches) if matches else 0} 个匹配")
                                
                                if matches:
                                    # 特殊处理：如果变量名是productCodes（或包含Codes复数），提取所有匹配的值作为数组
                                    if var_name.endswith('Codes') or var_name.endswith('codes'):
                                        # 提取所有匹配的值作为数组
                                        value = [m.value for m in matches if m.value is not None]
                                        # 如果数组为空，尝试提取第一个值
                                        if not value and matches:
                                            value = [matches[0].value] if matches[0].value is not None else []
                                    else:
                                        # 如果只有一条匹配，直接返回值；否则返回列表的第一个值
                                        if len(matches) == 1:
                                            value = matches[0].value
                                        else:
                                            # 如果有多个匹配，优先返回第一个非None的值
                                            value = None
                                            for m in matches:
                                                if m.value is not None:
                                                    value = m.value
                                                    break
                                            if value is None:
                                                value = matches[0].value
                                    # 只有当变量为空时才设置值，避免覆盖已提取的值
                                    current_value = self.variables.get(var_name)
                                    if self._is_empty(current_value):
                                        self.variables[var_name] = value
                                        LOG.info(f"✓ 成功提取变量: {var_name} = {value}")
                                    else:
                                        LOG.info(f"跳过提取（变量已有值）: {var_name} = {current_value}")
                                else:
                                    # 只有当变量为空时才设置为None，避免覆盖已提取的值
                                    current_value = self.variables.get(var_name)
                                    if self._is_empty(current_value):
                                        self.variables[var_name] = None
                                        LOG.warning(f"未找到匹配的数据: {jsonpath_expr}")
                                    else:
                                        LOG.info(f"跳过提取（变量已有值）: {var_name} = {current_value}")
                                    # 调试：输出响应JSON的结构
                                    if isinstance(response_json, dict):
                                        LOG.warning(f"响应JSON顶层键: {list(response_json.keys())}")
                                        if 'data' in response_json:
                                            LOG.warning(f"data字段类型: {type(response_json['data'])}, 是否包含contractCode: {'contractCode' in response_json['data'] if isinstance(response_json['data'], dict) else 'N/A'}")
                            except Exception as e:
                                LOG.error(f"提取变量失败: {e}, JSONPath: {jsonpath_expr}, 错误详情: {str(e)}, 错误类型: {type(e).__name__}")
                                import traceback
                                LOG.debug(f"错误堆栈: {traceback.format_exc()}")
                                self.variables[var_name] = None
                        else:
                            LOG.error(f"正则表达式匹配失败: value_expr={value_expr}")
                            self.variables[var_name] = None
                    elif value_expr.startswith('$get_request_param'):
                        # 处理 $get_request_param(param_name) - 从当前步骤的请求参数中提取值
                        LOG.info(f"匹配到$get_request_param表达式: {value_expr}")
                        get_param_pattern = r'\$get_request_param\s*\(\s*(.+?)\s*\)'
                        param_match = re.match(get_param_pattern, value_expr)
                        if param_match:
                            param_name = param_match.group(1).strip().strip('"').strip("'")
                            # 从当前步骤的请求数据中获取参数值
                            request_data = getattr(self, 'current_request_data', {})
                            param_value = None
                            if 'data' in request_data:
                                param_value = request_data['data'].get(param_name)
                            if param_value is None and 'params' in request_data:
                                param_value = request_data['params'].get(param_name)
                            if param_value is not None:
                                self.variables[var_name] = param_value
                                LOG.info(f"✓ 成功从请求参数提取变量: {var_name} = {param_value}")
                            else:
                                LOG.warning(f"请求参数中未找到: {param_name}")
                                self.variables[var_name] = None
                        else:
                            LOG.error(f"正则表达式匹配失败: value_expr={value_expr}")
                            self.variables[var_name] = None
                    else:
                        # 处理其他函数调用（如$execute_sql, $get_db_field等）
                        # 只有当变量为空时才处理，避免覆盖已提取的值
                        current_value = self.variables.get(var_name)
                        if self._is_empty(current_value):
                            # 先尝试处理变量表达式中的函数调用
                            processed_value = self._process_variables(value_expr)
                            if processed_value != value_expr:
                                # 如果处理后的值与原始值不同，说明有函数调用被处理了
                                self.variables[var_name] = processed_value
                                LOG.info(f"✓ 成功设置变量（已处理函数调用）: {var_name} = {processed_value}")
                            else:
                                # 如果没有函数调用，直接设置变量值
                                self.variables[var_name] = value_expr
                                LOG.info(f"✓ 成功设置变量: {var_name} = {value_expr}")
                        else:
                            LOG.info(f"跳过提取（变量已有值）: {var_name} = {current_value}")
        
    def _assert_response(self, response: requests.Response, assert_config: Dict, result: Dict):
        """
        执行响应断言（保留用于向后兼容）
        注意：新代码应该使用协议适配器的validate_response方法
        """
        # 状态码断言
        if 'status_code_assert' in assert_config:
            expected_code = assert_config['status_code_assert']
            if response.status_code != expected_code:
                error = f"状态码断言失败: 期望{expected_code}, 实际{response.status_code}"
                LOG.error(error)
                result['errors'].append(error)
            else:
                LOG.info(f"状态码断言通过: {response.status_code}")
                
        # 响应消息断言
        if 'response_assert_data' in assert_config:
            expected_msg = assert_config['response_assert_data']
            response_text = response.text
            if expected_msg not in response_text:
                error = f"响应消息断言失败: 未找到'{expected_msg}'"
                LOG.error(error)
                result['errors'].append(error)
            else:
                LOG.info(f"响应消息断言通过")
                
        # JSONPath断言
        if 'jsonpath_assert' in assert_config:
            try:
                response_json = response.json()
                for assertion in assert_config['jsonpath_assert']:
                    self._assert_jsonpath(response_json, assertion, result)
            except Exception as e:
                error = f"JSONPath断言失败: {e}"
                LOG.error(error)
                result['errors'].append(error)
                
    def _assert_jsonpath(self, data: Any, assertion: str, result: Dict):
        """
        执行JSONPath断言
        支持:
        - 简单断言: $..field == value
        - OR 逻辑: $..field1 == value1 or $..field2 == value2
        """
        # 处理 OR 逻辑（检查 ' or ' 模式，注意空格）
        import re
        or_pattern = re.compile(r'\s+or\s+', re.IGNORECASE)
        if or_pattern.search(assertion):
            LOG.debug(f"检测到OR逻辑断言: {assertion}")
            conditions = [cond.strip() for cond in or_pattern.split(assertion)]
            LOG.debug(f"OR条件拆分结果: {conditions}")
            
            # 只要有一个条件满足即可
            for condition in conditions:
                # 直接检查条件是否满足，不记录任何错误信息
                try:
                    # 解析条件
                    import re
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
                        
                        LOG.debug(f"检查OR条件: {condition}, 路径: {path_expr}, 找到匹配数: {len(matches)}")
                        
                        # 如果找到数据，检查值是否匹配
                        if matches:
                            # 检查每个匹配值
                            all_match = True
                            for m in matches:
                                actual_value = m.value
                                compare_result = self._compare_values(actual_value, operator, expected)
                                LOG.debug(f"  匹配值: {actual_value}, 期望: {expected}, 操作符: {operator}, 比较结果: {compare_result}")
                                if not compare_result:
                                    all_match = False
                                    break
                            
                            # 如果所有匹配值都满足条件，断言通过
                            if all_match:
                                LOG.info(f"JSONPath断言通过 (OR条件): {condition}")
                                return  # 条件满足，直接返回，不报告任何错误
                        else:
                            LOG.debug(f"  路径 '{path_expr}' 未找到数据，继续检查下一个条件")
                    else:
                        # 简单存在性断言
                        path_expr = condition
                        jsonpath_expr = parse(path_expr)
                        matches = list(jsonpath_expr.find(data))
                        if matches:
                            LOG.info(f"JSONPath断言通过 (OR条件): {condition}")
                            return  # 条件满足，直接返回，不报告任何错误
                except Exception as e:
                    # 条件检查出错，继续检查下一个条件
                    LOG.debug(f"检查条件 {condition} 时出错: {e}")
                    continue
            
            # 所有条件都失败，报告错误
            # 收集所有条件的错误信息用于报告
            all_errors = []
            for condition in conditions:
                try:
                    import re
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
                    result['errors'].append(f"JSONPath断言失败: {value_mismatch_errors[0]}")
                else:
                    result['errors'].append(f"JSONPath断言失败: OR条件中所有路径都未找到数据: {assertion}")
            else:
                result['errors'].append(f"JSONPath断言失败: 所有OR条件都不满足: {assertion}")
            
            LOG.error(f"JSONPath断言失败: 所有OR条件都不满足: {assertion}")
            return
        
        # 简单断言
        self._assert_single_jsonpath(data, assertion, result)
    
    def _check_single_jsonpath_condition(self, data: Any, assertion: str, result: Dict) -> bool:
        """
        检查单个JSONPath条件是否满足（用于OR逻辑）
        返回True表示条件满足，False表示条件不满足
        """
        import re
        
        # 匹配带引号的字符串或数字
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
            jsonpath_expr = parse(path_expr)
            matches = jsonpath_expr.find(data)
            
            if not matches:
                # 找不到数据，条件不满足（记录错误，但这是OR逻辑中的正常情况，继续检查下一个条件）
                error = f"JSONPath断言失败: 路径'{path_expr}'未找到数据"
                result['errors'].append(error)
                return False
                
            # 检查每个匹配值
            for match in matches:
                actual_value = match.value
                if not self._compare_values(actual_value, operator, expected):
                    # 值不匹配，条件不满足（记录错误，因为这是真正的值不匹配）
                    error = f"JSONPath断言失败: {path_expr} = {actual_value}, 期望{operator}{expected}"
                    result['errors'].append(error)
                    return False
                    
            # 所有匹配值都满足条件
            return True
        else:
            # 简单存在性断言
            path_expr = assertion
            jsonpath_expr = parse(path_expr)
            matches = jsonpath_expr.find(data)
            if not matches:
                # 找不到数据，条件不满足（记录错误，但这是OR逻辑中的正常情况，继续检查下一个条件）
                error = f"JSONPath断言失败: 路径'{path_expr}'未找到数据"
                result['errors'].append(error)
                return False
            else:
                return True
    
    def _assert_single_jsonpath(self, data: Any, assertion: str, result: Dict):
        """执行单个JSONPath断言"""
        # 解析断言表达式: $..field >= 0 或 $..field == "value"
        import re
        
        # 匹配带引号的字符串或数字
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
            jsonpath_expr = parse(path_expr)
            matches = jsonpath_expr.find(data)
            
            if not matches:
                error = f"JSONPath断言失败: 路径'{path_expr}'未找到数据"
                LOG.error(error)
                result['errors'].append(error)
                return
                
            # 检查每个匹配值
            for match in matches:
                actual_value = match.value
                if not self._compare_values(actual_value, operator, expected):
                    error = f"JSONPath断言失败: {path_expr} = {actual_value}, 期望{operator}{expected}"
                    LOG.error(error)
                    result['errors'].append(error)
                    return
                    
            LOG.info(f"JSONPath断言通过: {assertion}")
        else:
            # 简单存在性断言
            path_expr = assertion
            jsonpath_expr = parse(path_expr)
            matches = jsonpath_expr.find(data)
            if not matches:
                error = f"JSONPath断言失败: 路径'{path_expr}'未找到数据"
                LOG.error(error)
                result['errors'].append(error)
            else:
                LOG.info(f"JSONPath断言通过: {path_expr}")
                
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
            
    def _extract_variables(self, response: requests.Response, extracts: List):
        """提取变量"""
        for extract_expr in extracts:
            # 解析提取表达式: $set_variable(name, value)
            if isinstance(extract_expr, dict) and 'extract' in extract_expr:
                expr = extract_expr['extract']
                # 简单实现，实际需要更复杂的解析
                LOG.info(f"提取变量: {expr}")
                
    def _process_variables(self, text: str) -> Any:
        """
        处理文本中的变量
        支持的变量函数:
        - $get_host(环境,主机名): 获取主机地址
        - $generate_token(主机名): 生成token头
        - $mock_data(字段名, [类型], [约束]): 生成Mock数据
        - $get_db_field(字段名, [条件]): 从数据库获取字段值
        - $get_redis(键或模式, [字段]): 从Redis获取数据
        - $get_cache_var(变量名): 获取缓存变量
        """
        if not isinstance(text, str):
            return text
            
        processed = text
        
        # 0. 先处理 $get_config(config_path) - 因为$mock_data可能需要从配置读取参数
        config_pattern = r'\$get_config\(([^)]+)\)'
        config_matches = re.findall(config_pattern, processed)
        config_cache = {}  # 缓存配置值，避免重复读取
        for config_path in config_matches:
            config_path = config_path.strip().strip('"').strip("'")
            if config_path not in config_cache:
                try:
                    config_value = self._get_config_value(config_path)
                    config_cache[config_path] = config_value
                except Exception as e:
                    LOG.error(f"读取配置失败: {e}")
                    config_cache[config_path] = None
        
        # 1. 处理 $mock_data(field_name, [type], [constraints])
        # 注意：支持从配置读取的参数（通过config_cache）
        if SMART_DATA_SUPPORT:
            mock_pattern = r'\$mock_data\(([^)]+)\)'
            mock_matches = re.findall(mock_pattern, processed)
            for params in mock_matches:
                try:
                    parts = [p.strip().strip('"').strip("'") for p in params.split(',')]
                    field_name = parts[0]
                    data_type = parts[1] if len(parts) > 1 else 'auto'
                    
                    # 解析约束条件（如果有）
                    constraints = {}
                    if len(parts) > 2:
                        # 简单的键值对解析: min=1,max=100 或 min=$get_config(quantity.normal.min)
                        for i in range(2, len(parts)):
                            if '=' in parts[i]:
                                key, value = parts[i].split('=', 1)
                                key = key.strip()
                                value = value.strip().strip('"').strip("'")
                                
                                # 如果值是$get_config(...)，从缓存中读取配置
                                if value.startswith('$get_config('):
                                    config_match = re.match(r'\$get_config\(([^)]+)\)', value)
                                    if config_match:
                                        config_path = config_match.group(1).strip().strip('"').strip("'")
                                        config_value = config_cache.get(config_path)
                                        if config_value is not None:
                                            try:
                                                # 尝试转换为数字
                                                if isinstance(config_value, (int, float)):
                                                    constraints[key] = config_value
                                                elif isinstance(config_value, str):
                                                    # 尝试将字符串转换为数字
                                                    try:
                                                        constraints[key] = float(config_value)
                                                    except:
                                                        constraints[key] = config_value
                                                else:
                                                    constraints[key] = config_value
                                            except Exception as e:
                                                LOG.warning(f"配置值转换失败: {e}")
                                                constraints[key] = config_value
                                        else:
                                            LOG.warning(f"配置未找到: {config_path}，使用默认值")
                                            constraints[key] = 0.0000000000001 if 'min' in key else 999999999999.9999999999999
                                else:
                                    try:
                                        constraints[key] = eval(value)
                                    except:
                                        constraints[key] = value
                    
                    mock_value = smart_mock(field_name, data_type, **constraints)
                    LOG.info(f"✓ Mock数据生成: $mock_data({field_name}) = {mock_value}")
                    processed = processed.replace(f'$mock_data({params})', str(mock_value))
                except Exception as e:
                    LOG.error(f"Mock数据生成失败: {e}")
        
        # 2. 处理 $get_db_field(field_name, [conditions])
        if SMART_DATA_SUPPORT:
            db_pattern = r'\$get_db_field\(([^)]+)\)'
            db_matches = re.findall(db_pattern, processed)
            for params in db_matches:
                try:
                    # 先处理条件字符串中的$get_variable调用
                    # 找到所有$get_variable调用并替换为实际值
                    cond_str_with_vars = params
                    var_pattern = r'\$get_variable\(([^)]+)\)'
                    var_matches = re.findall(var_pattern, cond_str_with_vars)
                    for var_name in var_matches:
                        var_name = var_name.strip().strip('"').strip("'")
                        var_value = self.variables.get(var_name)
                        if var_value is not None:
                            # 替换为实际值（使用完整匹配，避免部分替换）
                            old_expr = f'$get_variable({var_name})'
                            cond_str_with_vars = cond_str_with_vars.replace(old_expr, str(var_value))
                        else:
                            LOG.warning(f"在$get_db_field参数中，变量未找到: $get_variable({var_name})")
                    
                    # 现在解析参数
                    parts = [p.strip().strip('"').strip("'") for p in cond_str_with_vars.split(',', 1)]
                    field_name = parts[0]
                    
                    conditions = {}
                    if len(parts) > 1:
                        # 解析条件: status='正常',type='A' 或 contractCode=123456,sellProductCode=789012
                        cond_str = parts[1]
                        for cond in cond_str.split(','):
                            if '=' in cond:
                                key, value = cond.split('=', 1)
                                # 移除引号
                                value = value.strip().strip('"').strip("'")
                                conditions[key.strip()] = value
                    
                    db_value = query_db_field(field_name, conditions if conditions else None)
                    LOG.info(f"✓ 数据库查询: $get_db_field({field_name}) = {db_value}")
                    processed = processed.replace(f'$get_db_field({params})', str(db_value) if db_value else '')
                except Exception as e:
                    LOG.error(f"数据库查询失败: {e}")
            
            # 处理 $execute_sql(SQL语句) - 执行自定义SQL查询
            sql_pattern = r'\$execute_sql\(([^)]+)\)'
            sql_matches = re.findall(sql_pattern, processed)
            for sql_query_raw in sql_matches:
                try:
                    # 保存原始SQL查询字符串（用于替换）
                    sql_query_original = sql_query_raw
                    # 移除SQL查询字符串中的外层引号（如果有）
                    sql_query = sql_query_raw.strip().strip('"').strip("'")
                    
                    # 执行SQL查询
                    from keyword_utils.db_utils import DataBase
                    from libs.config_center import get_config
                    
                    # 获取数据库配置
                    config = get_config()
                    env_config = config.get('ERP_TEST', {})
                    db_config = env_config.get('database', {}).get('default', {})
                    
                    # 执行SQL查询
                    with DataBase(db_config, db_type='postgres') as db:
                        results = db.postgres_execute(sql_query)
                        
                        # 如果查询有结果，返回第一行的第一个值
                        if results and len(results) > 0 and len(results[0]) > 0:
                            sql_value = results[0][0]
                            LOG.info(f"✓ SQL查询执行成功: $execute_sql(...) = {sql_value}")
                            # 使用原始SQL查询字符串进行替换
                            processed = processed.replace(f'$execute_sql({sql_query_original})', str(sql_value))
                        else:
                            LOG.warning(f"SQL查询无结果: $execute_sql(...)")
                            # 使用原始SQL查询字符串进行替换
                            processed = processed.replace(f'$execute_sql({sql_query_original})', '')
                except Exception as e:
                    LOG.error(f"SQL查询执行失败: {e}")
                    # 使用原始SQL查询字符串进行替换
                    processed = processed.replace(f'$execute_sql({sql_query_original})', '')
        
        # 3. 处理 $get_redis(key_or_pattern, [field])
        if SMART_DATA_SUPPORT:
            redis_pattern = r'\$get_redis\(([^)]+)\)'
            redis_matches = re.findall(redis_pattern, processed)
            for params in redis_matches:
                try:
                    parts = [p.strip().strip('"').strip("'") for p in params.split(',')]
                    key = parts[0]
                    field = parts[1] if len(parts) > 1 else None
                    
                    redis_value = get_redis_data(key, field)
                    LOG.info(f"✓ Redis查询: $get_redis({key}) = {str(redis_value)[:100]}")
                    
                    # 如果是字典或列表，转为JSON字符串
                    if isinstance(redis_value, (dict, list)):
                        value_str = json.dumps(redis_value, ensure_ascii=False)
                    else:
                        value_str = str(redis_value) if redis_value else ''
                    
                    processed = processed.replace(f'$get_redis({params})', value_str)
                except Exception as e:
                    LOG.error(f"Redis查询失败: {e}")
        
        # 4. 处理 $get_cache_var(variable_name)
        if SMART_DATA_SUPPORT:
            cache_pattern = r'\$get_cache_var\(([^)]+)\)'
            cache_matches = re.findall(cache_pattern, processed)
            for var_name in cache_matches:
                try:
                    var_name = var_name.strip().strip('"').strip("'")
                    cache_value = get_cache_var(var_name)
                    LOG.info(f"✓ 缓存变量: $get_cache_var({var_name}) = {str(cache_value)[:100]}")
                    
                    if isinstance(cache_value, (dict, list)):
                        value_str = json.dumps(cache_value, ensure_ascii=False)
                    else:
                        value_str = str(cache_value) if cache_value else ''
                    
                    processed = processed.replace(f'$get_cache_var({var_name})', value_str)
                except Exception as e:
                    LOG.error(f"缓存变量获取失败: {e}")
        
        # 5. 处理 $get_host(env, host_name)
        host_pattern = r'\$get_host\(([^,]+),\s*([^)]+)\)'
        host_matches = re.findall(host_pattern, processed)
        for env_name, host_name in host_matches:
            env_name = env_name.strip()
            host_name = host_name.strip()
            
            if AUTOTEST_SUPPORT and env_name in ENV:
                # 使用框架的配置
                host_value = ENV[env_name].get("servers", {}).get(host_name, "")
                LOG.info(f"$get_host({env_name},{host_name}) = {host_value}")
                processed = processed.replace(f'$get_host({env_name},{host_name})', host_value)
            else:
                # 使用本地配置
                processed = processed.replace(f'$get_host({env_name},{host_name})', 
                                            self.config.get('test_env', {}).get('api_base_url', ''))
        
        # 6. 处理剩余的 $get_config(config_path) - 从配置文件读取参数（用于直接使用，不在$mock_data中）
        # 注意：已经在步骤0中处理了用于$mock_data的配置，这里处理直接使用的配置
        config_pattern = r'\$get_config\(([^)]+)\)'
        config_matches = re.findall(config_pattern, processed)
        for config_path in config_matches:
            config_path = config_path.strip().strip('"').strip("'")
            try:
                # 支持路径格式: quantity.normal.min 或 unitPrice.boundary.max_value
                config_value = self._get_config_value(config_path)
                if config_value is not None:
                    LOG.info(f"✓ 读取配置: $get_config({config_path}) = {config_value}")
                    # 如果配置值是字典或列表，转为JSON字符串；否则转为字符串
                    if isinstance(config_value, (dict, list)):
                        config_value_str = json.dumps(config_value, ensure_ascii=False)
                    else:
                        config_value_str = str(config_value)
                    processed = processed.replace(f'$get_config({config_path})', config_value_str)
                else:
                    LOG.warning(f"配置未找到: $get_config({config_path})")
                    processed = processed.replace(f'$get_config({config_path})', '')
            except Exception as e:
                LOG.error(f"读取配置失败: {e}")
                processed = processed.replace(f'$get_config({config_path})', '')
        
        # 7. 处理 $get_variable(variable_name) - 从之前步骤提取的变量中获取值
        # 注意：在_process_dict中会有特殊处理，这里先标记，让_process_dict处理
        # 但如果是直接字符串替换的场景，需要转换为JSON字符串
        var_pattern = r'\$get_variable\(([^)]+)\)'
        var_matches = re.findall(var_pattern, processed)
        for var_name in var_matches:
            var_name = var_name.strip().strip('"').strip("'")
            var_value = self.variables.get(var_name)
            if var_value is not None:
                LOG.info(f"✓ 获取变量: $get_variable({var_name}) = {var_value}")
                # 如果变量值是数组或字典，转换为JSON字符串以便后续解析
                if isinstance(var_value, (dict, list)):
                    var_value_str = json.dumps(var_value, ensure_ascii=False)
                else:
                    var_value_str = str(var_value)
                processed = processed.replace(f'$get_variable({var_name})', var_value_str)
            else:
                LOG.warning(f"变量未找到: $get_variable({var_name})")
                processed = processed.replace(f'$get_variable({var_name})', '')
        
        # 8. 处理 $generate_token(host_name)
        token_pattern = r'\$generate_token\(([^)]+)\)'
        token_matches = re.findall(token_pattern, processed)
        
        for host_name in token_matches:
            host_name = host_name.strip()
            
            if AUTOTEST_SUPPORT:
                try:
                    # 使用框架的token生成
                    token_headers = gen_token_func(host_name)
                    LOG.info(f"✓ 成功生成token: $generate_token({host_name})")
                    # 返回headers字典，不是字符串
                    return token_headers
                except Exception as e:
                    LOG.error(f"生成token失败: {e}")
                    return {"Content-Type": "application/json", "Authorization": "Bearer mock_token"}
            else:
                # 没有token工具支持时，返回默认headers
                LOG.warning(f"token工具未导入，使用默认token")
                return {"Content-Type": "application/json", "Authorization": "Bearer default_token"}
            
        return processed
    
    def _track_inserted_data(self, step: Dict, step_result: Dict):
        """
        跟踪插入的数据，用于后续清理
        
        :param step: 步骤配置
        :param step_result: 步骤执行结果
        """
        try:
            method = step.get('method', '').upper()
            # 只跟踪POST、PUT、PATCH等可能插入数据的请求
            if method not in ['POST', 'PUT', 'PATCH']:
                return
            
            # 检查响应是否成功
            if not step_result.get('passed', False):
                return
            
            response = step_result.get('response', {})
            if not response:
                return
            
            # 从响应中提取插入的数据ID
            # 支持多种常见格式：
            # 1. { "data": { "id": 123 } }
            # 2. { "id": 123 }
            # 3. { "result": { "primary_key": 123 } }
            # 4. { "resCode": "200", "data": { "code": "abc123" } }
            
            body = response.get('body', '')
            if not body:
                return
            
            # 尝试解析JSON
            try:
                if isinstance(body, str):
                    response_data = json.loads(body)
                else:
                    response_data = body
            except:
                return
            
            # 提取插入的数据信息
            inserted_record = {}
            
            # 从data字段提取ID
            if 'data' in response_data and isinstance(response_data['data'], dict):
                data = response_data['data']
                # 常见的主键字段名
                for key in ['id', 'primary_key', 'code', 'record_id', 'contract_code', 'reimbursement_code']:
                    if key in data:
                        inserted_record['id'] = data[key]
                        inserted_record['id_field'] = key
                        break
                # 如果data本身就是ID
                if 'id' not in inserted_record and isinstance(data, (str, int)):
                    inserted_record['id'] = data
                    inserted_record['id_field'] = 'data'
            # 直接从响应提取ID
            elif 'id' in response_data:
                inserted_record['id'] = response_data['id']
                inserted_record['id_field'] = 'id'
            
            # 如果响应中没有返回ID，尝试从请求参数中提取用于清理的字段
            if 'id' not in inserted_record:
                # 获取请求数据（使用已处理过的数据，从step_result中获取）
                request_data = step_result.get('request', {}).get('data', {})
                if not request_data:
                    # 如果没有从step_result获取到，尝试从原始step获取并处理
                    request_data = self._process_dict(step.get('data', {}))
                
                if isinstance(request_data, dict):
                    # 对于客户保存接口，使用 customersSign 和 unityNo 作为清理条件
                    if '/sell/customer/save' in step.get('path', ''):
                        if 'customersSign' in request_data:
                            inserted_record['cleanup_field'] = 'customers_sign'
                            inserted_record['cleanup_value'] = request_data['customersSign']
                        if 'unityNo' in request_data:
                            inserted_record['cleanup_field2'] = 'unity_no'
                            inserted_record['cleanup_value2'] = request_data['unityNo']
            
            # 从步骤配置中提取表名或清理配置
            cleanup_config = step.get('cleanup', {})
            if cleanup_config:
                inserted_record['table'] = cleanup_config.get('table')
                inserted_record['id_field'] = cleanup_config.get('id_field', 'id')
                inserted_record['delete_method'] = cleanup_config.get('method', 'DELETE')
                inserted_record['delete_path'] = cleanup_config.get('path')
            else:
                # 尝试从路径推断表名
                path = step.get('path', '')
                if path:
                    # 从路径提取资源名称，如 /api/users/add -> users
                    parts = [p for p in path.split('/') if p and p not in ['api', 'pms', 'add', 'create', 'save']]
                    if parts:
                        inserted_record['resource'] = parts[-1]
            
            # 如果有ID或清理字段，记录到插入数据列表
            if 'id' in inserted_record or 'cleanup_field' in inserted_record:
                inserted_record['method'] = method
                inserted_record['path'] = step.get('path', '')
                inserted_record['host'] = step.get('host', '')
                self.inserted_data.append(inserted_record)
                LOG.info(f"✓ 跟踪插入数据: {inserted_record}")
        except Exception as e:
            LOG.debug(f"跟踪插入数据失败: {e}")
    
    def _cleanup_inserted_data(self):
        """
        清理插入的数据
        """
        if not self.inserted_data:
            LOG.info("无需清理数据")
            return
        
        LOG.info(f"开始清理 {len(self.inserted_data)} 条插入的数据...")
        
        # 逆序清理（后插入的先删除）
        for record in reversed(self.inserted_data):
            try:
                if 'delete_path' in record and record['delete_path']:
                    # 使用配置的删除路径
                    host = self._process_variables(record.get('host', ''))
                    path = self._process_variables(record['delete_path'])
                    url = f"{host}{path}" if host else path
                    
                    # 发送DELETE请求
                    response = self.session.delete(url, headers=self._get_headers())
                    if response.status_code in [200, 204]:
                        LOG.info(f"✓ 成功清理数据: {url}")
                    else:
                        LOG.warning(f"清理数据失败: {url}, 状态码: {response.status_code}")
                else:
                    # 尝试从表名和ID直接删除
                    if 'table' in record and 'id' in record:
                        self._delete_from_database(record)
                    # 如果没有ID但有清理字段，使用清理字段删除
                    elif 'table' in record and 'cleanup_field' in record:
                        self._delete_from_database_by_field(record)
                    else:
                        LOG.warning(f"无法清理数据，缺少配置: {record}")
            except Exception as e:
                LOG.warning(f"清理数据异常: {record}, 错误: {e}")
        
        LOG.info("数据清理完成")
    
    def _delete_from_database(self, record: Dict):
        """
        从数据库删除记录
        
        :param record: 记录信息
        """
        try:
            from keyword_utils.db_utils import DataBase
            
            # 获取数据库配置
            try:
                from libs.config_center import ENV
                db_config = ENV.get('ERP_TEST', {}).get('data_base', {}).get('default', {})
                if not db_config:
                    LOG.warning(f"无法获取数据库配置: ERP_TEST.data_base.default")
                    return
            except Exception as e:
                LOG.warning(f"获取数据库配置失败: {e}")
                return
            
            # 获取数据库连接 - 需要转换为conn_dict格式
            conn_dict = {
                'host': db_config['host'],
                'port': db_config.get('port', 5432),
                'user': db_config['user'],
                'password': db_config['password'],
                'database': db_config['database']
            }
            db = DataBase(conn_dict=conn_dict, db_type='postgres')
            table_name = record['table']
            id_value = record['id']
            id_field = record.get('id_field', 'id')
            
            # 构建删除SQL
            if '.' in table_name:
                schema, table = table_name.split('.', 1)
                quoted_table = f'"{schema}"."{table}"'
            else:
                quoted_table = f'"{table_name}"'
            
            # 处理ID值（字符串需要加引号）
            if isinstance(id_value, str):
                id_value_str = f"'{id_value}'"
            else:
                id_value_str = str(id_value)
            
            delete_sql = f'DELETE FROM {quoted_table} WHERE "{id_field}" = {id_value_str}'
            
            # 执行删除
            with db:
                db.postgres_execute(delete_sql)
            LOG.info(f"✓ 从数据库删除记录: {table_name}.{id_field} = {id_value}")
        except Exception as e:
            LOG.warning(f"从数据库删除记录失败: {e}")
    
    def _delete_from_database_by_field(self, record: Dict):
        """
        根据清理字段从数据库删除记录
        
        :param record: 记录信息
        """
        try:
            from keyword_utils.db_utils import DataBase
            
            # 获取数据库配置
            try:
                from libs.config_center import ENV
                db_config = ENV.get('ERP_TEST', {}).get('data_base', {}).get('default', {})
                if not db_config:
                    LOG.warning(f"无法获取数据库配置: ERP_TEST.data_base.default")
                    return
            except Exception as e:
                LOG.warning(f"获取数据库配置失败: {e}")
                return
            
            # 获取数据库连接 - 需要转换为conn_dict格式
            conn_dict = {
                'host': db_config['host'],
                'port': db_config.get('port', 5432),
                'user': db_config['user'],
                'password': db_config['password'],
                'database': db_config['database']
            }
            db = DataBase(conn_dict=conn_dict, db_type='postgres')
            table_name = record['table']
            
            # 构建删除SQL
            if '.' in table_name:
                schema, table = table_name.split('.', 1)
                quoted_table = f'"{schema}"."{table}"'
            else:
                quoted_table = f'"{table_name}"'
            
            # 构建WHERE条件
            conditions = []
            if 'cleanup_field' in record and 'cleanup_value' in record:
                field = record['cleanup_field']
                value = record['cleanup_value']
                if isinstance(value, str):
                    value_str = f"'{value}'"
                else:
                    value_str = str(value)
                conditions.append(f'"{field}" = {value_str}')
            
            if 'cleanup_field2' in record and 'cleanup_value2' in record:
                field2 = record['cleanup_field2']
                value2 = record['cleanup_value2']
                if isinstance(value2, str):
                    value_str2 = f"'{value2}'"
                else:
                    value_str2 = str(value2)
                conditions.append(f'"{field2}" = {value_str2}')
            
            if not conditions:
                LOG.warning(f"无法删除记录，缺少清理字段: {record}")
                return
            
            where_clause = ' AND '.join(conditions)
            delete_sql = f'DELETE FROM {quoted_table} WHERE {where_clause}'
            
            # 执行删除
            with db:
                db.postgres_execute(delete_sql)
            LOG.info(f"✓ 从数据库删除记录: {table_name} WHERE {where_clause}")
        except Exception as e:
            LOG.warning(f"从数据库删除记录失败: {e}")
    
    def _get_headers(self) -> Dict:
        """获取请求头"""
        try:
            if AUTOTEST_SUPPORT:
                # 使用框架的token生成
                return gen_token_func('pms_host')
            else:
                return {"Content-Type": "application/json"}
        except:
            return {"Content-Type": "application/json"}
        
    def _process_dict(self, data: Dict) -> Dict:
        """处理字典中的变量"""
        if isinstance(data, dict):
            result = {}
            for key, value in data.items():
                if isinstance(value, str):
                    # 检查是否是变量表达式（如$get_variable(...)）
                    if '$get_variable(' in value:
                        # 先处理变量表达式，获取实际值
                        processed_value = self._process_variables(value)
                        # 如果处理后的值是JSON字符串，尝试解析
                        if isinstance(processed_value, str) and processed_value.strip().startswith('['):
                            try:
                                import json
                                parsed = json.loads(processed_value)
                                # 如果解析成功且是数组，直接使用数组
                                if isinstance(parsed, list):
                                    result[key] = parsed
                                else:
                                    result[key] = processed_value
                            except (json.JSONDecodeError, TypeError):
                                # 解析失败，检查是否是数组类型字段
                                if (key.endswith('Codes') or key.endswith('codes')):
                                    # 尝试解析为数组
                                    try:
                                        parsed = json.loads(processed_value)
                                        if isinstance(parsed, list):
                                            result[key] = parsed
                                        else:
                                            result[key] = [parsed]
                                    except:
                                        result[key] = [processed_value]
                                else:
                                    result[key] = processed_value
                        elif isinstance(processed_value, (list, dict)):
                            # 如果处理后的值直接是数组或字典，直接使用
                            result[key] = processed_value
                        else:
                            # 其他情况，检查是否是数组类型字段
                            if (key.endswith('Codes') or key.endswith('codes')) and processed_value:
                                # 尝试解析为JSON（如果是JSON字符串）
                                try:
                                    import json
                                    parsed = json.loads(processed_value)
                                    if isinstance(parsed, list):
                                        result[key] = parsed
                                    else:
                                        result[key] = [parsed]
                                except (json.JSONDecodeError, TypeError):
                                    # 如果不是JSON字符串，且是单个值，转换为数组
                                    if not isinstance(processed_value, list):
                                        result[key] = [processed_value]
                                    else:
                                        result[key] = processed_value
                            else:
                                result[key] = processed_value
                    else:
                        # 不是变量表达式，正常处理
                        processed_value = self._process_variables(value)
                        if (key.endswith('Codes') or key.endswith('codes')) and processed_value:
                            # 尝试解析为JSON（如果是JSON字符串）
                            try:
                                import json
                                parsed = json.loads(processed_value)
                                if isinstance(parsed, list):
                                    result[key] = parsed
                                else:
                                    result[key] = [parsed]
                            except (json.JSONDecodeError, TypeError):
                                # 如果不是JSON字符串，且是单个值，转换为数组
                                if not isinstance(processed_value, list):
                                    result[key] = [processed_value]
                                else:
                                    result[key] = processed_value
                        else:
                            result[key] = processed_value
                elif isinstance(value, dict):
                    result[key] = self._process_dict(value)
                elif isinstance(value, list):
                    result[key] = self._process_list(value)
                else:
                    # 处理非字符串类型的值，如果是sellProductCodes字段且是单个值，转换为数组
                    if (key.endswith('Codes') or key.endswith('codes')) and value is not None:
                        if not isinstance(value, list):
                            result[key] = [value]
                        else:
                            result[key] = value
                    else:
                        result[key] = value
            return result
        elif isinstance(data, list):
            return self._process_list(data)
        else:
            return data
    
    def _process_list(self, data: list) -> list:
        """处理列表中的变量"""
        if not isinstance(data, list):
            return data
            
        result = []
        for item in data:
            if isinstance(item, str):
                result.append(self._process_variables(item))
            elif isinstance(item, dict):
                result.append(self._process_dict(item))
            elif isinstance(item, list):
                result.append(self._process_list(item))
            else:
                result.append(item)
        return result


# 示例用法
if __name__ == "__main__":
    executor = APITestExecutor()
    
    # 执行测试用例
    result = executor.execute_case("cases/api/examples/user_query.yaml")
    
    # 打印结果
    print(json.dumps(result, indent=2, ensure_ascii=False))

