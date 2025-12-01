"""
智能 Redis 数据获取工具
支持：
1. 基于模式匹配的键查找
2. 智能数据类型处理
3. 支持 Hash、List、Set、String 等数据类型
4. 缓存变量提取
"""

import logging
import json
from typing import Any, Dict, List, Optional
from keyword_utils.redis_util import Redis
from libs.config_center import ENV

LOG = logging.getLogger(__name__)


class SmartRedisQuery:
    """智能 Redis 查询器"""
    
    def __init__(self, env_name: str = 'ERP_TEST', redis_key: str = 'default'):
        """
        初始化 Redis 查询器
        :param env_name: 环境名称
        :param redis_key: Redis 配置键名
        """
        self.env_name = env_name
        self.redis_key = redis_key
        self.redis = None
    
    def _get_redis_connection(self) -> Redis:
        """获取 Redis 连接"""
        if self.redis is None:
            try:
                redis_config = ENV.get(self.env_name, {}).get('redis_base', {}).get(self.redis_key, {})
                if not redis_config:
                    raise ValueError(f"未找到 Redis 配置: {self.env_name}.redis_base.{self.redis_key}")
                
                self.redis = Redis(
                    host=redis_config['host'],
                    port=redis_config.get('port', 6379),
                    db=redis_config.get('db', 0),
                    password=redis_config.get('password'),
                    username=redis_config.get('username')
                )
                LOG.info(f"✓ 成功连接 Redis: {redis_config['host']}:{redis_config['port']}/{redis_config['db']}")
            except Exception as e:
                LOG.error(f"Redis 连接失败: {e}")
                raise
        
        return self.redis
    
    def find_keys(self, pattern: str) -> List[str]:
        """
        根据模式查找键
        :param pattern: 模式（支持通配符 * 和 ?）
        :return: 匹配的键列表
        """
        try:
            redis = self._get_redis_connection()
            keys = redis.keys_redis(pattern)
            LOG.info(f"模式 '{pattern}' 匹配到 {len(keys)} 个键")
            return keys
        except Exception as e:
            LOG.error(f"查找键失败: {e}")
            return []
    
    def get_value(self, key: str, default: Any = None) -> Any:
        """
        获取键值（自动处理数据类型）
        :param key: 键名
        :param default: 默认值（键不存在时返回）
        :return: 键值
        """
        try:
            redis = self._get_redis_connection()
            
            # 检查键是否存在
            if not redis.exists_redis(key):
                LOG.warning(f"Redis 键不存在: {key}")
                return default
            
            # 获取键的类型
            key_type = redis.type_redis(key)
            
            # 根据类型获取值
            if key_type == 'string':
                value = redis.get_redis(key)
                # 尝试解析 JSON
                try:
                    return json.loads(value) if value else default
                except:
                    return value if value else default
                    
            elif key_type == 'hash':
                return redis.hgetall_redis(key) or default
                
            elif key_type == 'list':
                return redis.lrange_redis(key, 0, -1) or default
                
            elif key_type == 'set':
                return list(redis.smembers_redis(key)) if redis.smembers_redis(key) else default
                
            elif key_type == 'zset':
                return redis.zrange_redis(key, 0, -1, withscores=True) or default
            
            else:
                LOG.warning(f"不支持的 Redis 数据类型: {key_type}")
                return default
                
        except Exception as e:
            LOG.error(f"获取 Redis 值失败: {e}")
            return default
    
    def get_hash_field(self, key: str, field: str, default: Any = None) -> Any:
        """
        获取 Hash 的字段值
        :param key: Hash 键名
        :param field: 字段名
        :param default: 默认值
        :return: 字段值
        """
        try:
            redis = self._get_redis_connection()
            value = redis.hget_redis(key, field)
            
            # 尝试解析 JSON
            if value:
                try:
                    return json.loads(value)
                except:
                    return value
            
            return default
            
        except Exception as e:
            LOG.error(f"获取 Hash 字段失败: {e}")
            return default
    
    def get_cache_variable(self, variable_name: str, default: Any = None) -> Any:
        """
        获取缓存变量（从 environment.yaml 配置中查找）
        
        :param variable_name: 变量名（在 cache_variable 中配置的键）
        :param default: 默认值
        :return: 缓存值
        """
        try:
            # 从配置中获取缓存键
            redis_config = ENV.get(self.env_name, {}).get('redis_base', {}).get(self.redis_key, {})
            cache_variables = redis_config.get('cache_variable', {})
            
            cache_key = cache_variables.get(variable_name)
            
            if not cache_key:
                LOG.warning(f"缓存变量配置不存在: {variable_name}")
                return default
            
            LOG.info(f"获取缓存变量: {variable_name} -> {cache_key}")
            return self.get_value(cache_key, default)
            
        except Exception as e:
            LOG.error(f"获取缓存变量失败: {e}")
            return default
    
    def smart_search(self, keyword: str, limit: int = 10) -> Dict[str, Any]:
        """
        智能搜索：根据关键词查找相关的 Redis 键及其值
        
        :param keyword: 关键词
        :param limit: 返回结果数量限制
        :return: {key: value} 字典
        """
        try:
            # 搜索包含关键词的键
            pattern = f"*{keyword}*"
            keys = self.find_keys(pattern)
            
            if not keys:
                LOG.warning(f"未找到包含 '{keyword}' 的 Redis 键")
                return {}
            
            # 获取每个键的值
            results = {}
            for key in keys[:limit]:
                results[key] = self.get_value(key)
            
            return results
            
        except Exception as e:
            LOG.error(f"智能搜索失败: {e}")
            return {}
    
    def close(self):
        """关闭 Redis 连接"""
        if self.redis:
            self.redis.close()
            self.redis = None


# 全局实例缓存
_redis_instances = {}

def get_smart_redis(env_name: str = 'ERP_TEST', redis_key: str = 'default') -> SmartRedisQuery:
    """获取智能 Redis 查询器实例（带缓存）"""
    cache_key = f"{env_name}_{redis_key}"
    
    if cache_key not in _redis_instances:
        _redis_instances[cache_key] = SmartRedisQuery(env_name, redis_key)
    
    return _redis_instances[cache_key]


def get_redis_data(key_or_pattern: str, 
                   field: Optional[str] = None,
                   env_name: str = 'ERP_TEST', 
                   redis_key: str = 'default') -> Any:
    """
    快捷函数：获取 Redis 数据
    
    示例：
        get_redis_data('user:1001')  # 获取普通键值
        get_redis_data('user:*')  # 模式匹配，返回第一个匹配的值
        get_redis_data('user:1001', 'name')  # 获取 Hash 字段
    """
    redis = get_smart_redis(env_name, redis_key)
    
    # 如果包含通配符，先查找键
    if '*' in key_or_pattern or '?' in key_or_pattern:
        keys = redis.find_keys(key_or_pattern)
        if not keys:
            return None
        key_or_pattern = keys[0]  # 使用第一个匹配的键
    
    # 获取值
    if field:
        return redis.get_hash_field(key_or_pattern, field)
    else:
        return redis.get_value(key_or_pattern)


def get_cache_var(variable_name: str, 
                  env_name: str = 'ERP_TEST', 
                  redis_key: str = 'default') -> Any:
    """
    快捷函数：获取缓存变量
    
    示例：
        get_cache_var('operation_user_hash_key')  # 从 environment.yaml 配置中获取
    """
    redis = get_smart_redis(env_name, redis_key)
    return redis.get_cache_variable(variable_name)


if __name__ == '__main__':
    # 测试示例
    print("=== 智能 Redis 查询测试 ===\n")
    
    try:
        redis = SmartRedisQuery()
        
        # 测试1：查找键
        print("1. 查找包含 'SBC' 的键...")
        keys = redis.find_keys('*SBC*')
        print(f"   找到 {len(keys)} 个键: {keys[:5]}\n")
        
        # 测试2：获取值
        if keys:
            print(f"2. 获取第一个键的值: {keys[0]}")
            value = redis.get_value(keys[0])
            print(f"   值: {str(value)[:100]}...\n")
        
        # 测试3：获取缓存变量
        print("3. 获取缓存变量: operation_user_hash_key")
        cache_value = redis.get_cache_variable('operation_user_hash_key')
        if cache_value:
            print(f"   成功: {str(cache_value)[:100]}...")
        else:
            print("   未找到")
        
        redis.close()
        print("\n✓ 测试完成")
        
    except Exception as e:
        print(f"\n✗ 测试失败: {e}")

