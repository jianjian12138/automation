# 导入datetime模块用于日期时间处理，time模块用于时间相关操作
import datetime, time
# 导入json模块用于JSON数据处理
import json
# 导入random模块用于生成随机数
import random
# 导入re模块用于正则表达式操作
import re
# 导入string模块用于字符串操作
import string
# 导入subprocess模块用于执行外部命令
import subprocess

# 导入chardet模块用于检测字符编码
import chardet

# 从keyword_utils.db_utils模块导入DataBase类，用于数据库操作
from keyword_utils.db_utils import DataBase
# 从libs.config_center模块导入ENV配置，用于获取环境变量
from libs.config_center import ENV

# 创建DataBase实例，连接到ERP_TEST环境的默认数据库
pgSql = DataBase(ENV["ERP_TEST"]["data_base"]['default'])


def get_system_data():
    # 定义系统模块列表，包含各业务模块标识
    system_modules = (
        'technology', 'personal', 'warehouse', 'purchase', 'financing', 'publicData', 'sell', 'role', 'gongxuyewu',
        'public_data', 'tgongxu', 'test', 'TestPage')
    # 构建SQL查询，获取指定模块的表信息
    get_system_tables_sql = 
        "SELECT table_code,table_name_en,module_code FROM dm_base.t_dm_base_module_table WHERE module_code in (SELECT module_code FROM dm_base.t_dm_base_module WHERE module_name_en in {}) LIMIT 1000 OFFSET 0".format(
        system_modules)
    rows, rows_affected = pgSql.postgres_execute(get_system_tables_sql)
    system_tables = rows
    system_tables_code = []
    system_tables_name_en = []
    system_module_code = []
    system_field_code = []
    system_process_code_list = []
    for tables_code, table_name_en, module_code in system_tables:
        system_tables_code.append(tables_code)
        system_tables_name_en.append(table_name_en)
        system_module_code.append(module_code)
    system_tables_code = tuple(system_tables_code)
    system_tables_name_en = tuple(system_tables_name_en)
    system_module_code = tuple(set(system_module_code))
    get_system_fields_sql = "SELECT field_code FROM dm_base.t_dm_base_module_table_field WHERE table_code  in {}".format(
        system_tables_code)
    system_fields = pgSql.postgres_execute(get_system_fields_sql)[0]
    for filed_code in system_fields:
        system_field_code.append(filed_code[0])
    system_field_code = tuple(set(system_field_code))
    dp_process_sql = "select process_code,process_info FROM dp_base.t_dp_base_process_template"
    rows = pgSql.postgres_execute(dp_process_sql)[0]
    for table_code in system_tables_code:
        for process_code, process_info in rows:
            if str(table_code) in process_info:
                system_process_code_list.append(process_code)
    system_process = tuple(set(system_process_code_list))
    return system_module_code, system_field_code, system_process, system_tables_code, system_modules, system_tables_name_en


def excel_assign_case(case_name):
    """
    获取指定测试用例的打印结果，用于参数使用
    :return:
    """
    data = None
    cmd_data = 'pytest -k {} --disable-warnings -s'.format(case_name)

    out_data = subprocess.run(cmd_data, capture_output=True)
    out_data_stdout = out_data.stdout
    decode_type = chardet.detect(out_data_stdout)["encoding"]
    out_data_str = out_data.stdout.decode(decode_type)
    pattern = re.compile(r'.py(.*})')
    match = pattern.search(out_data_str)
    if match:
        data = match.group(1)
    return data


def draft(data):
    """
    生成model:value的json格式参数，使用场景例如：报告保存，提交流程
    :param data:
    :return:
    """

    draft_data = {}
    for field in data:
        types = field["type"]
        model = field["model"]
        if types == 'select':
            value = field["options"]["options"][0]["value"]
        elif types == 'date':
            data_format = field["options"]["format"]
            current_time = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            value = current_time
        else:
            characters = string.ascii_letters + string.digits
            value = ''.join(random.choice(characters) for _ in range(5))
        draft_data[model] = value
    return draft_data



# system_data = get_system_data()
# system_module_code = system_data[0]
# system_field_code = system_data[1]
# system_process_code = system_data[2]
# system_tables_code = system_data[3]
# system_module_en = system_data[4]
# system_tables_name_en = system_data[5]

# system_data = []
# system_module_code =  []
# system_field_code = []
# system_process_code = []
# system_tables_code = []
# system_module_en =  []
# system_tables_name_en = []
if __name__ == '__main__':
    # excel_assign_case("test_add_process_get_form")
    pass
