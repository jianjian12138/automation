# 导入os模块，用于文件系统操作
import os
# 从项目库中导入配置中心、邮件工具、用例上下文、文件工具和用例运行相关模块
from libs.config_center import HOST_CONF, BASE_DIR, RUN_CONF
from libs.email_utils import MailUtils
from core.case_context import CasePretreatment
from libs.file_utils import case_dir_path, clear_temporary, locust_case_save
from core.case_run import multi_threading_run

# 清除临时文件，确保测试环境干净
clear_temporary()


# 定义主执行函数，处理不同类型的测试用例
def execute_main(case_type, env, path, priority, thread, retry, env_conf, tags=None):
    # 获取测试用例目录路径列表
    case_path_list = case_dir_path(case_type, path)
    # 从环境配置中获取locust_step参数
    locust_step = env_conf.get("locust_step")
    # 从环境配置中获取安全测试标记
    is_safety = env_conf.get("is_safety")
    # 创建用例预处理对象，初始化测试用例
    case_context = CasePretreatment(case_type, case_path_list, priority, locust_step, is_safety, tags=tags)
    # 准备测试用例，进行预处理
    case_context.ready_case()
    # 获取预处理后的测试用例列表
    ready_case_list = case_context.ready_case_list
    # 检查是否需要使用主机配置
    is_host = env_conf.get("is_host")
    if is_host:
        # 如果需要，将主机配置添加到环境配置中
        env_conf["host_conf"] = HOST_CONF[env]
    # 使用多线程运行测试用例，并获取报告数据
    report_data = multi_threading_run(case_type, ready_case_list, env, thread, retry, env_conf)

    # 返回测试报告数据
    return report_data


# 定义Locust性能测试执行函数
def execute_locust(env, path, priority, thread, retry, env_conf):
    # 获取locust_step参数
    locust_step = env_conf.get("locust_step")
    if locust_step:
        # 如果启用了locust_step，执行API测试用例
        report_data = execute_main("api", env, path, priority, thread, retry, env_conf)
    else:
        # 否则不执行测试，报告数据为None
        report_data = None

    # 获取API测试用例路径列表
    case_path_list = case_dir_path("api", path)
    # 创建用例预处理对象
    case_context = CasePretreatment("api", case_path_list, priority, locust_step)
    # 截断测试用例，适应Locust性能测试需求
    case_context.truncation_case()
    # 获取截断后的测试用例
    cut_cases = case_context.cut_case
    for cut_case in cut_cases:
        # 保存Locust测试用例
        locust_case_save(path, cut_case, report_data)

    # 返回报告数据
    return report_data


# 定义测试报告邮件发送函数
def report_email_send(case_type, email_conf=None):
    # 设置默认邮件配置为空字典
    email_conf = email_conf or {}
    # 从运行配置中获取是否发送邮件的标记
    is_sendmail = RUN_CONF.pop("is_sendmail", None)
    if is_sendmail:
        # 如果需要发送邮件，构建报告路径
        report_path = os.path.join(BASE_DIR, 'report', f'{case_type}_test_report.html')
        # 将报告路径添加到邮件配置
        email_conf["report_path"] = report_path
        # 发送测试报告邮件
        MailUtils(**email_conf).send()
