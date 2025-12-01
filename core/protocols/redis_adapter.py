"""
Redis协议适配器
支持Redis操作（GET, SET, DELETE, EXISTS等）
"""
from typing import Dict, Any, List, Optional
import logging
from .base import ProtocolAdapter
from libs.config_center import ENV

# 尝试导入redis模块
try:
    import redis
    REDIS_SUPPORT = True
except ImportError:
    REDIS_SUPPORT = False
    redis = None

LOG = logging.getLogger(__name__)


class RedisAdapter(ProtocolAdapter):
    """Redis协议适配器"""
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)
        self.env_name = config.get('env_name', 'ERP_TEST') if config else 'ERP_TEST'
        self.db_key = config.get('db_key', 'default') if config else 'default'
        self.redis_client = None
    
    def _get_redis_client(self, step: Dict[str, Any]):
        """获取Redis客户端"""
        env_name = step.get('env_name', self.env_name)
        db_key = step.get('db_key', self.db_key)
        
        # 获取Redis配置
        redis_config = ENV.get(env_name, {}).get('redis_base', {}).get(db_key, {})
        if not redis_config:
            raise ValueError(f"未找到Redis配置: {env_name}.redis_base.{db_key}")
        
        # 创建Redis客户端
        if not self.redis_client:
            if not REDIS_SUPPORT:
                raise ImportError("redis模块未安装，请先安装: pip install redis")
            self.redis_client = redis.Redis(
                host=redis_config['host'],
                port=redis_config.get('port', 6379),
                db=redis_config.get('db', 0),
                password=redis_config.get('password'),
                decode_responses=True
            )
        
        return self.redis_client
    
    def send_request(self, step: Dict[str, Any]) -> Dict[str, Any]:
        """
        执行Redis操作
        
        :param step: 测试步骤配置
            - operation: Redis操作类型 (get, set, delete, exists, keys, hget, hset等)
            - key: Redis键名
            - value: Redis值（用于SET操作）
            - field: Redis字段名（用于HGET/HSET操作）
            - ttl: 过期时间（秒，用于SET操作）
            - pattern: 键名模式（用于KEYS操作）
        :return: 响应结果
        """
        operation = step.get('operation', 'get').lower()
        
        # 处理变量替换
        key = self.process_variables(step.get('key', ''))
        value = self.process_variables(step.get('value', ''))
        field = self.process_variables(step.get('field', ''))
        pattern = self.process_variables(step.get('pattern', ''))
        ttl = step.get('ttl', None)
        
        if not REDIS_SUPPORT:
            raise ImportError("redis模块未安装，请先安装: pip install redis")
        
        LOG.info(f"[Redis] 执行操作: {operation}, 键: {key}")
        
        try:
            client = self._get_redis_client(step)
            
            result = {
                'status_code': 200,
                'success': True,
                'operation': operation,
                'key': key,
                'data': None,
                'metadata': {}
            }
            
            # 根据操作类型执行不同的Redis命令
            if operation == 'get':
                if not key:
                    raise ValueError("GET操作必须提供key")
                value = client.get(key)
                result['data'] = value
                result['json'] = {'value': value} if value else None
                
            elif operation == 'set':
                if not key:
                    raise ValueError("SET操作必须提供key")
                if value is None:
                    raise ValueError("SET操作必须提供value")
                client.set(key, value, ex=ttl if ttl else None)
                result['data'] = 'OK'
                result['json'] = {'result': 'OK'}
                
            elif operation == 'delete' or operation == 'del':
                if not key:
                    raise ValueError("DELETE操作必须提供key")
                deleted = client.delete(key)
                result['data'] = deleted
                result['json'] = {'deleted_count': deleted}
                
            elif operation == 'exists':
                if not key:
                    raise ValueError("EXISTS操作必须提供key")
                exists = client.exists(key)
                result['data'] = bool(exists)
                result['json'] = {'exists': bool(exists)}
                
            elif operation == 'keys':
                pattern = pattern or '*'
                keys = client.keys(pattern)
                result['data'] = keys
                result['json'] = {'keys': keys, 'count': len(keys)}
                
            elif operation == 'hget':
                if not key:
                    raise ValueError("HGET操作必须提供key")
                if not field:
                    raise ValueError("HGET操作必须提供field")
                value = client.hget(key, field)
                result['data'] = value
                result['json'] = {'value': value} if value else None
                
            elif operation == 'hset':
                if not key:
                    raise ValueError("HSET操作必须提供key")
                if not field:
                    raise ValueError("HSET操作必须提供field")
                if value is None:
                    raise ValueError("HSET操作必须提供value")
                client.hset(key, field, value)
                result['data'] = 'OK'
                result['json'] = {'result': 'OK'}
                
            elif operation == 'hgetall':
                if not key:
                    raise ValueError("HGETALL操作必须提供key")
                data = client.hgetall(key)
                result['data'] = data
                result['json'] = data
                
            elif operation == 'ttl':
                if not key:
                    raise ValueError("TTL操作必须提供key")
                ttl_value = client.ttl(key)
                result['data'] = ttl_value
                result['json'] = {'ttl': ttl_value}
                
            else:
                raise ValueError(f"不支持的Redis操作: {operation}")
            
            LOG.info(f"[Redis] 操作成功: {operation}, 结果: {result.get('data')}")
            
            return result
            
        except Exception as e:
            LOG.error(f"[Redis] 操作失败: {e}")
            return {
                'status_code': 500,
                'success': False,
                'operation': operation,
                'key': key,
                'data': None,
                'error': str(e),
                'json': None,
                'metadata': {}
            }
    
    def validate_response(self, response: Dict[str, Any], assert_config: Dict[str, Any]) -> List[str]:
        """
        验证Redis操作结果
        
        :param response: Redis操作结果
        :param assert_config: 断言配置
            - success_assert: 是否成功 (true/false)
            - value_assert: 值断言
            - exists_assert: 存在性断言
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
                errors.append(f"Redis操作失败: {response.get('error', '未知错误')}")
            elif not success_assert and response.get('success', False):
                errors.append("预期Redis操作失败，但实际操作成功")
        
        # 值断言
        value_assert = assert_config.get('value_assert')
        if value_assert:
            actual_value = response.get('data')
            if isinstance(value_assert, dict):
                expected_value = value_assert.get('expected')
                operator = value_assert.get('operator', '==')
                
                if operator == '==' and actual_value != expected_value:
                    errors.append(f"值断言失败: 期望 == {expected_value}, 实际 {actual_value}")
                elif operator == '!=' and actual_value == expected_value:
                    errors.append(f"值断言失败: 期望 != {expected_value}, 实际 {actual_value}")
                elif operator == 'contains' and expected_value not in str(actual_value):
                    errors.append(f"值断言失败: 期望包含 {expected_value}, 实际 {actual_value}")
            else:
                if actual_value != value_assert:
                    errors.append(f"值断言失败: 期望 {value_assert}, 实际 {actual_value}")
        
        # 存在性断言
        exists_assert = assert_config.get('exists_assert')
        if exists_assert is not None:
            actual_exists = bool(response.get('data'))
            if exists_assert and not actual_exists:
                errors.append("存在性断言失败: 期望键存在，但实际不存在")
            elif not exists_assert and actual_exists:
                errors.append("存在性断言失败: 期望键不存在，但实际存在")
        
        # 数据断言（JSONPath）
        data_assert = assert_config.get('data_assert')
        if data_assert and response.get('json'):
            try:
                from jsonpath_ng import parse
                json_data = response.get('json', {})
                
                if isinstance(data_assert, list):
                    for assertion in data_assert:
                        # 解析JSONPath表达式
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
            except Exception as e:
                errors.append(f"数据断言解析失败: {e}")
        
        return errors
    
    def extract_variables(self, response: Dict[str, Any], extract_config: List[Dict[str, Any]]) -> Dict[str, Any]:
        """
        从Redis操作结果中提取变量
        
        :param response: Redis操作结果
        :param extract_config: 提取配置
        :return: 提取的变量字典
        """
        extracted = {}
        # extract应该由test_executor统一处理
        return extracted

