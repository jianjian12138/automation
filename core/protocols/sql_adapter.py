"""
SQL协议适配器
支持直接执行SQL查询和数据库操作
"""
from typing import Dict, Any, List, Optional
import logging
from .base import ProtocolAdapter
from keyword_utils.db_utils import DataBase
from libs.config_center import ENV

LOG = logging.getLogger(__name__)


class SQLAdapter(ProtocolAdapter):
    """SQL协议适配器"""
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)
        self.env_name = config.get('env_name', 'ERP_TEST') if config else 'ERP_TEST'
        self.db_key = config.get('db_key', 'default') if config else 'default'
        self.db_type = config.get('db_type', 'postgres') if config else 'postgres'
    
    def send_request(self, step: Dict[str, Any]) -> Dict[str, Any]:
        """
        执行SQL查询
        
        :param step: 测试步骤配置
            - sql: SQL语句（支持变量替换）
            - query_type: 查询类型 (select, insert, update, delete, count等)
            - env_name: 环境名称（可选，默认使用配置中的env_name）
            - db_key: 数据库配置键名（可选，默认使用配置中的db_key）
            - db_type: 数据库类型（可选，默认postgres）
        :return: 响应结果
        """
        # 获取SQL语句
        sql = step.get('sql', '')
        if not sql:
            raise ValueError("SQL步骤必须提供sql字段")
        
        # 处理变量替换（支持$get_variable格式）
        # 先替换$get_variable格式的变量
        import re
        var_pattern = r'\$get_variable\(([^)]+)\)'
        var_matches = re.findall(var_pattern, sql)
        for var_name in var_matches:
            var_name = var_name.strip().strip('"').strip("'")
            var_value = self.get_variable(var_name)
            if var_value is not None:
                # 替换为实际值（需要加上引号，因为SQL中的字符串值需要引号）
                old_pattern = f'$get_variable({var_name})'
                new_value = f"'{var_value}'" if isinstance(var_value, str) else str(var_value)
                sql = sql.replace(old_pattern, new_value)
            else:
                LOG.warning(f"SQL变量替换失败: 变量 {var_name} 未找到")
        
        # 再处理简单的变量替换（$variable_name格式）
        sql = self.process_variables(sql)
        
        # 获取数据库配置
        env_name = step.get('env_name', self.env_name)
        db_key = step.get('db_key', self.db_key)
        db_type = step.get('db_type', self.db_type).lower()
        
        # 获取查询类型
        query_type = step.get('query_type', 'select').lower()
        
        LOG.info(f"[SQL] 执行SQL: {sql[:100]}...")
        LOG.info(f"[SQL] 环境: {env_name}, 数据库: {db_key}, 类型: {db_type}")
        
        try:
            # 获取数据库配置
            db_config = ENV.get(env_name, {}).get('data_base', {}).get(db_key, {})
            if not db_config:
                raise ValueError(f"未找到数据库配置: {env_name}.data_base.{db_key}")
            
            # 构建连接参数
            conn_dict = {
                'host': db_config['host'],
                'port': db_config.get('port', 5432),
                'user': db_config['user'],
                'password': db_config['password'],
                'database': db_config['database']
            }
            
            # 执行SQL
            with DataBase(conn_dict=conn_dict, db_type=db_type) as db:
                if db_type == 'postgres':
                    results = db.postgres_execute(sql)
                elif db_type == 'mysql':
                    results = db.mysql_execute(sql)
                else:
                    raise ValueError(f"不支持的数据库类型: {db_type}")
                
                # 构建响应结果
                result = {
                    'status_code': 200,
                    'success': True,
                    'data': results,
                    'row_count': len(results) if isinstance(results, list) else (results if isinstance(results, int) else 0),
                    'query_type': query_type,
                    'sql': sql,
                    'metadata': {
                        'env_name': env_name,
                        'db_key': db_key,
                        'db_type': db_type
                    }
                }
                
                # 如果是SELECT查询，尝试解析JSON
                if query_type == 'select' and isinstance(results, list) and results:
                    try:
                        # 如果结果只有一行，尝试提取字段值
                        if len(results) == 1:
                            row = results[0]
                            if isinstance(row, tuple) and len(row) > 0:
                                result['json'] = {'value': row[0]}
                            elif isinstance(row, dict):
                                result['json'] = row
                            else:
                                result['json'] = {'data': row}
                        else:
                            result['json'] = {'data': results, 'count': len(results)}
                    except:
                        result['json'] = None
                else:
                    result['json'] = None
                
                LOG.info(f"[SQL] 执行成功，影响行数/结果数: {result['row_count']}")
                
                return result
                
        except Exception as e:
            LOG.error(f"[SQL] 执行失败: {e}")
            return {
                'status_code': 500,
                'success': False,
                'data': None,
                'row_count': 0,
                'error': str(e),
                'sql': sql,
                'json': None,
                'metadata': {}
            }
    
    def validate_response(self, response: Dict[str, Any], assert_config: Dict[str, Any]) -> List[str]:
        """
        验证SQL执行结果
        
        :param response: SQL执行结果
        :param assert_config: 断言配置
            - success_assert: 是否成功 (true/false)
            - row_count_assert: 行数断言 (>=, <=, ==, >, <)
            - data_assert: 数据断言（JSONPath表达式）
        :return: 错误列表
        """
        errors = []
        
        if not assert_config:
            return errors
        
        # 检查是否成功
        success_assert = assert_config.get('success_assert')
        if success_assert is not None:
            if success_assert and not response.get('success', False):
                errors.append(f"SQL执行失败: {response.get('error', '未知错误')}")
            elif not success_assert and response.get('success', False):
                errors.append("预期SQL执行失败，但实际执行成功")
        
        # 检查行数
        row_count_assert = assert_config.get('row_count_assert')
        if row_count_assert:
            expected_count = assert_config.get('expected_count', 0)
            actual_count = response.get('row_count', 0)
            
            if '>=' in row_count_assert:
                if actual_count < expected_count:
                    errors.append(f"行数断言失败: 期望 >= {expected_count}, 实际 {actual_count}")
            elif '<=' in row_count_assert:
                if actual_count > expected_count:
                    errors.append(f"行数断言失败: 期望 <= {expected_count}, 实际 {actual_count}")
            elif '==' in row_count_assert or '=' in row_count_assert:
                if actual_count != expected_count:
                    errors.append(f"行数断言失败: 期望 == {expected_count}, 实际 {actual_count}")
            elif '>' in row_count_assert:
                if actual_count <= expected_count:
                    errors.append(f"行数断言失败: 期望 > {expected_count}, 实际 {actual_count}")
            elif '<' in row_count_assert:
                if actual_count >= expected_count:
                    errors.append(f"行数断言失败: 期望 < {expected_count}, 实际 {actual_count}")
        
        # 数据断言（JSONPath）
        data_assert = assert_config.get('data_assert')
        if data_assert and response.get('json'):
            try:
                from jsonpath_ng import parse
                json_data = response.get('json', {})
                
                if isinstance(data_assert, list):
                    for assertion in data_assert:
                        # 解析JSONPath表达式，如：$..count > 0
                        if '>' in assertion:
                            expr, value = assertion.split('>', 1)
                            expr = expr.strip()
                            value = int(value.strip())
                            jsonpath_expr = parse(expr)
                            matches = [match.value for match in jsonpath_expr.find(json_data)]
                            if not matches or matches[0] <= value:
                                errors.append(f"数据断言失败: {assertion}")
                        elif '<' in assertion:
                            expr, value = assertion.split('<', 1)
                            expr = expr.strip()
                            value = int(value.strip())
                            jsonpath_expr = parse(expr)
                            matches = [match.value for match in jsonpath_expr.find(json_data)]
                            if not matches or matches[0] >= value:
                                errors.append(f"数据断言失败: {assertion}")
                        elif '==' in assertion:
                            expr, value = assertion.split('==', 1)
                            expr = expr.strip()
                            value = value.strip().strip('"').strip("'")
                            jsonpath_expr = parse(expr)
                            matches = [match.value for match in jsonpath_expr.find(json_data)]
                            if not matches or str(matches[0]) != value:
                                errors.append(f"数据断言失败: {assertion}")
                        elif '!=' in assertion:
                            expr, value = assertion.split('!=', 1)
                            expr = expr.strip()
                            value = value.strip().strip('"').strip("'")
                            jsonpath_expr = parse(expr)
                            matches = [match.value for match in jsonpath_expr.find(json_data)]
                            if matches and str(matches[0]) == value:
                                errors.append(f"数据断言失败: {assertion}")
            except Exception as e:
                errors.append(f"数据断言解析失败: {e}")
        
        return errors
    
    def extract_variables(self, response: Dict[str, Any], extract_config: List[Dict[str, Any]]) -> Dict[str, Any]:
        """
        从SQL执行结果中提取变量
        
        :param response: SQL执行结果
        :param extract_config: 提取配置
        :return: 提取的变量字典
        """
        extracted = {}
        
        # 这里需要调用test_executor的_extract_variables_legacy方法
        # 但由于我们在适配器中，我们需要自己处理
        # 实际上，extract应该由test_executor统一处理
        # 这里返回空字典，让test_executor处理
        return extracted

