
# 定义模块导出的公共接口列表
__all__ = ["generate_token", "erp_login","get_token_data"]


# ERP系统登录并获取令牌数据
def erp_login(*args):
    # 延迟导入login函数，避免循环依赖
    from libs.token_utils import login
    # 初始化参数字典
    kwargs = {}
    # 解析输入参数列表，将键值对格式的参数添加到kwargs
    for data in args:
        if "=" in data:
           data_list = data.split("=")
           kwargs[data_list[0]] = data_list[1]
    # 调用login函数执行登录并返回令牌数据
    tokens_data = login(*args, **kwargs)
    return tokens_data


# 生成认证令牌
def generate_token(*args):
    # 延迟导入所需模块和函数
    from libs.token_utils import generate_tokens
    from libs.login_func import opi_tokens_data,pms_tokens_data
    # opi_tokens_data = ErpTokens.opi_tokens_data  # 注释掉的备用实现
    # pms_tokens_data = ErpTokens.pms_tokens_data  # 注释掉的备用实现
    # 判断输入参数类型
    if isinstance(args[0],str):
        # 根据字符串参数生成对应系统的令牌
        if "opi_host" == args[0] :
            return generate_tokens(opi_tokens_data)[0]
        elif "pms_host" == args[0]:
            return generate_tokens(pms_tokens_data)[0]
    else:
        # 直接使用输入数据生成令牌
        return generate_tokens(args[0])[0]


# 从令牌数据中获取指定索引的值
def get_token_data(data,index):
    # 返回数据列表中指定索引的元素
    return data[int(index)]