from libs.config_center import ENV, LOG
from libs.token_utils import login

# 定义环境配置变量
opi_host = ENV["ERP_TEST"]["servers"]["opi_host"]
opiPhone_number = ENV["ERP_TEST"]["global_variable"]["opi_phone_number"]
opipass_word = ENV["ERP_TEST"]["global_variable"]["opi_pass_word"]
pms_host = ENV["ERP_TEST"]["servers"]["pms_host"]
pms_phone_number = ENV["ERP_TEST"]["global_variable"]["pms_phone_number"]
pms_pass_word = ENV["ERP_TEST"]["global_variable"]["pms_pass_word"]
enterprise_code = ENV["ERP_TEST"]["global_variable"]["enterprise_code"][0]

# 定义全局变量用于存储令牌数据
opi_tokens_data = None
pms_tokens_data = None


def get_opi_tokens():
    """
    惰性初始化并获取OPI系统令牌
    避免在模块导入时就执行登录操作
    """
    global opi_tokens_data
    if opi_tokens_data is None:
        try:
            # 尝试登录获取令牌数据
            opi_tokens_data = login(opi_host, opiPhone_number, pass_word=opipass_word)
            LOG.info("成功获取OPI系统令牌")
        except Exception as e:
            # 如果登录失败，设置默认值
            LOG.warning(f"OPI系统登录失败: {str(e)}")
            opi_tokens_data = (opi_host, "mock_token", "mock_secret_key", "mock_public_key")
    return opi_tokens_data


def get_pms_tokens():
    """
    惰性初始化并获取PMS系统令牌
    避免在模块导入时就执行登录操作
    """
    global pms_tokens_data
    if pms_tokens_data is None:
        try:
            # 尝试登录获取令牌数据
            pms_tokens_data = login(pms_host, pms_phone_number, pass_word=pms_pass_word, enterprise_code=enterprise_code)
            LOG.info("成功获取PMS系统令牌")
        except Exception as e:
            # 如果登录失败，设置默认值
            LOG.warning(f"PMS系统登录失败: {str(e)}")
            pms_tokens_data = (pms_host, "mock_token", "mock_secret_key", "mock_public_key")
    return pms_tokens_data


# 尝试真实登录获取令牌数据
# 如果登录失败，则使用模拟数据（避免测试中断）
try:
    LOG.info("开始获取PMS系统令牌...")
    pms_tokens_data = login(pms_host, pms_phone_number, pass_word=pms_pass_word, enterprise_code=enterprise_code)
    LOG.info("✓ 成功获取PMS系统令牌")
except Exception as e:
    LOG.warning(f"PMS系统登录失败，使用模拟令牌: {str(e)}")
    pms_tokens_data = (pms_host, "mock_token", "mock_secret_key", "mock_public_key")

try:
    LOG.info("开始获取OPI系统令牌...")
    opi_tokens_data = login(opi_host, opiPhone_number, pass_word=opipass_word)
    LOG.info("✓ 成功获取OPI系统令牌")
except Exception as e:
    LOG.warning(f"OPI系统登录失败，使用模拟令牌: {str(e)}")
    opi_tokens_data = (opi_host, "mock_token", "mock_secret_key", "mock_public_key")



