# 导入特殊数据类型处理模块：uuid(唯一标识符)、datetime(日期时间)、decimal(高精度小数)
# 导入JSON处理模块：json(标准JSON)、json5(支持注释的JSON扩展)
import uuid, datetime, decimal, json, json5
# 从json模块导入基础JSON编码器类，并重命名为BaseJSONEncoder
from json import JSONEncoder as BaseJSONEncoder


# 自定义JSON编码器类，继承自标准JSONEncoder
class JSONEncoder(BaseJSONEncoder):

    def default(self, o):
        # 处理datetime.datetime类型：转换为"年-月-日 时:分:秒"格式字符串
        if isinstance(o, datetime.datetime):
            return o.strftime("%Y-%m-%d %H:%M:%S")
        # 处理datetime.date类型：转换为"年-月-日"格式字符串
        if isinstance(o, datetime.date):
            return o.strftime('%Y-%m-%d')
        # 处理decimal.Decimal类型：转换为字符串以保留精度
        if isinstance(o, decimal.Decimal):
            return str(o)
        # 处理uuid.UUID类型：转换为字符串表示
        if isinstance(o, uuid.UUID):
            return str(o)
        # 处理bytes类型：解码为UTF-8字符串
        if isinstance(o, bytes):
            return o.decode("utf-8")
        # 调用父类默认方法处理其他类型
        return super(JSONEncoder, self).default(o)


# 递归将数据对象中的值转换为字符串（特殊处理None和布尔值）
def data_obj_to_str(data):
    # 如果是字典类型，遍历键值对并递归处理值
    if isinstance(data, dict):
        for key, value in data.items():
            result = data_obj_to_str(value)
            data[key] = result
    # 如果是列表类型，遍历元素并递归处理
    elif isinstance(data, list):
        for index, value in enumerate(data):
            data[index] = data_obj_to_str(value)
    # 如果值为None，转换为空字符串
    elif data is None:
        return ""
    # 如果是布尔值，保持原值不变
    elif isinstance(data, bool):
        return data
    # 其他类型直接转换为字符串
    else:
        return str(data)

    return data


# JSON/JSON5处理工具类
class JsonWith:

    @staticmethod
    def dumps(obj, **kwargs):
        # 未实现的JSON序列化方法
        pass

    @staticmethod
    def loads(s, **kwargs):
        # 根据文件后缀判断使用JSON还是JSON5解析器
        suffix = s.split(".")[-1]
        if suffix == "json5":
            # 使用json5解析JSON5格式数据
            json5.loads(s, **kwargs)
        else:
            # 使用标准json解析JSON格式数据
            json.loads(s, **kwargs)


