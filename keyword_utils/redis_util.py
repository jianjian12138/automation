# 导入json模块用于JSON数据处理
import json
# 导入uuid模块用于生成唯一标识符
import uuid
# 导入redis模块用于Redis数据库操作
import redis
# 从libs.config_center导入LOG用于日志记录
from libs.config_center import LOG


# 定义Redis类，用于封装Redis数据库操作
class Redis(object):
    # 类初始化方法，接收Redis连接参数字典
    def __init__(self, conn_dict):
        # 创建一个连接参数字典的副本，避免修改原始字典
        conn_params = conn_dict.copy()
        
        # 从连接参数中提取并存储缓存变量信息（如果存在）
        self.cache_info = conn_params.pop("cache_variable", None)
        
        # 处理不支持的参数
        if 'username' in conn_params and conn_params['username'] is None:
            # 如果username为None，移除这个参数
            del conn_params['username']
        
        try:
            # 创建Redis连接池
            self.pool = redis.ConnectionPool(**conn_params)
        except Exception as e:
            # 记录连接池创建失败的错误
            LOG.error(f"Redis连接池创建失败: {str(e)}")
            # 再次尝试，只使用必要的参数
            minimal_params = {k: v for k, v in conn_params.items() 
                             if k in ['host', 'port', 'db', 'password']}
            try:
                self.pool = redis.ConnectionPool(**minimal_params)
                LOG.info("使用最小化参数成功创建Redis连接池")
            except Exception as inner_e:
                LOG.error(f"使用最小化参数创建Redis连接池也失败: {str(inner_e)}")
                # 如果仍然失败，抛出异常
                raise

    # 上下文管理器进入方法，用于with语句
    def __enter__(self):
        # 使用连接池创建Redis客户端实例
        self.redis = redis.Redis(connection_pool=self.pool)
        return self

    # 上下文管理器退出方法，用于with语句
    def __exit__(self, exc_type, exc_val, exc_tb):
        # 断开连接池连接
        self.pool.disconnect()

    # 获取Redis中String类型数据
    def get_redis_string(self, key):
        # 获取指定key的String值
        value = self.redis.get(key)
        if value:
            # 如果值存在，解码并JSON解析后返回
            return json.loads(value.decode())
        else:
            # 如果值不存在，记录警告日志并返回None
            logs = "未从redis中获取到{}:的值".format(key)
            LOG.warn(logs)
            return None

    # 获取Redis中Hash类型数据的指定字段值
    def get_redis_hash(self, hash_key, field):
        """
         获取hash类型的值
        :param hash_key:hash表名
        :param field: 字段key
        :return: 字段值
        """
        # 对字段名进行JSON格式转义(添加双引号)
        field_key = '"{}"'.format(field)
        # 获取Hash表中指定字段的值
        values = self.redis.hget(hash_key, field_key)
        if values:
            # 如果值存在，解码并JSON解析后返回
            return json.loads(values.decode())
        else:
            # 如果值不存在，记录警告日志并返回None
            logs = "未从redis中获取到{}:的值".format(hash_key)
            LOG.warn(logs)
            return None

    # 修改Redis中Hash类型数据的指定字段值
    def update_redis_hash_value(self, hash_key, field, value):
        """修改hash表中指定字段的值
        :param hash_key:hash表名
        :param field:字段名
        :param value:新值
        :return:
        """
        # 对字段名进行JSON格式转义
        field_key = '"{}"'.format(field)
        # 设置Hash表中指定字段的值
        self.redis.hset(hash_key, field_key, value)
        # 记录修改操作日志
        LOG.warn("修改redis{}中的{}的值".format(hash_key, field))

    # 修改Redis中String类型数据的值
    def update_redis_value(self, key, value):
        """修改redis中string字段的值
        :param key:键名
        :param value:新值
        :return:
        """
        # 设置String类型键的值
        self.redis.set(key, value)
        # 注释掉的日志记录语句
        # LOG.warn("修改redis{}的值为:{}".format(key, value))

    # 获取Redis中指定类型键的数据数量
    def get_value_count(self, keys_type, key):
        # 根据数据类型获取不同的数量
        if keys_type is "List":
            # 获取List类型的长度
            count = self.redis.llen(key)
        elif keys_type is "Set":
            # 获取Set类型的元素数量
            count = self.redis.scard(key)
        elif keys_type is "SortedSet":
            # 获取SortedSet类型的元素数量
            count = self.redis.zcard(key)
        elif keys_type is "Hash":
            # 获取Hash类型的字段数量
            count = self.redis.hlen(key)
        else:
            # 抛出不支持的数据类型异常
            raise TypeError("暂未支持数据类型")
        return count

    # 删除Redis中Hash类型数据的指定字段
    def del_hash_data(self, hash_key, key):
        # 删除hash数据
        # 对字段名进行JSON格式转义
        field_keys = '"{}"'.format(key)
        # 删除Hash表中指定字段
        self.redis.hdel(hash_key, field_keys)
        # 记录删除操作日志
        LOG.warn("删除redis{}中的{}的值".format(hash_key, key))

    # 更新缓存版本数据
    def update_cache_version(self, array_list, cache_version_key):
        # 生成UUID作为新版本号
        version_code = str(uuid.uuid4())
        # 获取当前缓存版本数据
        cache_versions_data = self.get_redis_string(cache_version_key)
        # 弹出最后一个元素(假设为版本列表)
        array_deque = cache_versions_data.pop()
        # 设置要更新的列表数据
        array_list = array_list
        # 构建更新详情对象
        update_details = {'@class': 'org.aerie.forest.cache.synchronization.BusCacheVersionUpdateDetails',
                          'updateDetails': ['java.util.ArrayList', array_list],
                          'versionCode': version_code}
        # 如果版本列表长度为16(最大容量)
        if len(array_deque) == 16:
            # 删除第一个元素(先进先出)
            array_deque.pop(0)
            # 添加新的更新详情
            array_deque.append(update_details)
        # 将更新后的版本列表添加回缓存数据
        cache_versions_data.append(array_deque)
        # 将缓存数据JSON序列化
        cache_versions_data = json.dumps(cache_versions_data)
        # 更新Redis中的缓存版本数据
        self.update_redis_value(cache_version_key, cache_versions_data)


# 当模块直接运行时执行的代码
if __name__ == '__main__':
    # 导入ENV配置(注释掉的示例)
    # from libs.config_center import ENV

    # 获取Redis配置(注释掉的示例)
    # redisbase_data = ENV["ERP_TEST"]["redisbase"]
    # cache_info = redisbase_data.pop("cache_variable")
    # print(redisbase_data)
    # 使用上下文管理器连接Redis并获取数据(注释掉的示例)
    # with Redis(ENV["ERP_TEST"]["redisbase"]["default"]) as r:
    #     a = r.get_redis_string("SecretKeyCode")

    # 打印获取的数据(注释掉的示例)
    # print(a)
    pass