# 导入HTTP请求客户端类，用于发送HTTP请求
from core.http_client import HttpRequests
# 定义断言工具函数（不使用autotest_elegant的keywords模块）
def equal(actual, expected):
    """断言两个值相等"""
    assert actual == expected, f"期望值: {expected}, 实际值: {actual}"

def left_contain(text, substring):
    """断言文本包含子字符串"""
    assert substring in str(text), f"期望包含: {substring}, 实际文本: {text}"
# 导入配置中心的环境变量，用于获取测试环境配置
from libs.config_center import  ENV
# 导入登录函数，用于获取不同系统的令牌数据
from libs.login_func import opi_tokens_data, pms_tokens_data
# 导入令牌生成装饰器，用于自动处理令牌获取和刷新
from libs.token_utils import generate_token

# 初始化HTTP请求客户端实例
client = HttpRequests()
# 从环境配置中获取ERP测试环境的OPI服务地址
host = ENV["ERP_TEST"]["servers"]["opi_host"]
# 从环境配置中获取ERP测试环境的PMS服务地址
pms_host = ENV["ERP_TEST"]["servers"]["pms_host"]

# 从环境配置中获取OPI系统的测试手机号
phone_number = ENV["ERP_TEST"]["global_variable"]["opi_phone_number"]
# 从环境配置中获取OPI系统的测试密码
pass_word = ENV["ERP_TEST"]["global_variable"]["opi_pass_word"]

# 使用令牌生成装饰器，自动获取和注入OPI系统的访问令牌
@generate_token(opi_tokens_data)
def http_request(**kwargs):
    # 从关键字参数中获取期望的响应状态码，默认为200
    assert_status_code = kwargs.get("assert_status_code", 200)
    # 从关键字参数中获取响应断言数据，默认为"resCode":"200"
    response_assert_data = kwargs.get("response_assert_data",'"resCode":"200"')
    # 从关键字参数中获取请求数据，默认为None
    data = kwargs.get("data",None)
    # 从关键字参数中获取请求头，默认为None
    headers = kwargs.get("headers",None)
    # 从关键字参数中获取请求路径（必传参数）
    path = kwargs.get("path")
    # 从关键字参数中获取请求方法（必传参数）
    method =  kwargs.get("method")
    # 发送HTTP请求并获取响应
    response = client.request(method, host, path, headers, data)
    # 提取响应状态码
    status_code = response["status_code"]
    # 提取响应文本
    response_text = response["response_text"]
    # 断言响应状态码是否符合预期
    equal(status_code,assert_status_code)
    # 断言响应文本是否包含期望的数据
    left_contain(response_text,response_assert_data)
    # 返回响应文本
    return response_text

# 使用令牌生成装饰器，自动获取和注入PMS系统的访问令牌
@generate_token(pms_tokens_data)
def pms_http_request(** kwargs):
    # 从关键字参数中获取期望的响应状态码，默认为200
    assert_status_code = kwargs.get("assert_status_code", 200)
    # 从关键字参数中获取响应断言数据
    response_assert_data = kwargs.get("response_assert_data")
    # 从关键字参数中获取请求数据，默认为None
    data = kwargs.get("data",None)
    # 从关键字参数中获取请求头，默认为None
    headers = kwargs.get("headers",None)
    # 从关键字参数中获取请求路径（必传参数）
    path = kwargs.get("path")
    # 从关键字参数中获取请求方法（必传参数）
    method =  kwargs.get("method")
    # 发送HTTP请求并获取响应
    response = client.request(method, pms_host, path, headers, data)
    # 提取响应状态码
    status_code = response["status_code"]
    # 提取响应文本
    response_text = response["response_text"]
    # 断言响应状态码是否符合预期
    equal(status_code,assert_status_code)
    # 断言响应文本是否包含期望的数据
    left_contain(response_text,response_assert_data)
    # 返回响应文本
    return response_text


# 模块主程序入口（空实现，用于模块自测）
if __name__ == '__main__':
    pass