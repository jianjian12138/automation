# 导入操作系统接口模块，用于文件路径处理
import os

# 导入数据库操作工具类
from keyword_utils.db_utils import DataBase
# 导入Redis操作工具类
from keyword_utils.redis_util import Redis
# 导入配置中心环境变量
from libs.config_center import ENV

# 从环境配置中获取默认数据库连接信息并初始化数据库连接
pgSql = DataBase(ENV["ERP_TEST"]["data_base"]['default'])
# 建立数据库连接
pgSql.db_connect()

# 从环境配置中获取默认Redis连接信息并初始化Redis客户端
r = Redis(ENV["ERP_TEST"]["redis_base"]["default"])


def set_table():
    # 设置系统表数据
    # SQL文件存放目录（硬编码路径，需根据实际环境调整）
    base_dir = r"E:\HJ\altest\autotest_elegant\files\sql\系统表"
    # 执行目录下所有SQL文件
    pgSql.postgres_execute(base_dir)


def set_enum():
    # 设置系统枚举数据
    # SQL文件存放目录（硬编码路径，需根据实际环境调整）
    base_dir = r"E:\HJ\altest\autotest_elegant\files\sql\系统枚举"
    # 执行目录下所有SQL文件
    pgSql.postgres_execute(base_dir)


def set_pms_data():
    # 准备PMS系统数据（当前未完全实现）
    file_names = []
    # SQL文件基础目录（硬编码路径，需根据实际环境调整）
    base_dir = r"C:\Users\95768\Desktop\ERP\JAR包\dm2"
    # 遍历目录下所有SQL文件
    for root, dirs, files in os.walk(base_dir):
        for file in files:
            if ".sql" in file:
                file_names.append(os.path.join(root, file))
    # 去重处理SQL文件列表
    s = list(set(file_names))


def del_dm_base_enum():
    # 删除dm_base模块下的枚举数据
    # 查询指定时间后创建的枚举
    dm_enum_sql = "select enum_code FROM dm_base.t_dm_base_enum WHERE create_time >= '2024-09-03 20:41:51' ORDER BY create_time DESC "
    enum_modules = pgSql.postgres_execute(dm_enum_sql)[0]
    array_list = []
    for dm_enum in enum_modules:
        enum_code = dm_enum[0]
        array_list.append(enum_code)
        # 删除枚举记录
        delete_module = "DELETE FROM dm_base.t_dm_base_enum WHERE enum_code = {}".format(enum_code)
        pgSql.postgres_execute(delete_module, "delete")
        # 删除Redis缓存中的枚举数据
        r.del_hash_data(enum_module_hash_key, str(enum_code))
    # 更新总线缓存版本
    r.update_cache_version(array_list, enum_module_version_key)
    print("执行完成，删除enum_module成功！！！")


def del_dm_base_module():
    # 删除dm_base模块数据
    # 查询非系统模块的module_code
    dm_module_sql = "select module_code FROM dm_base.t_dm_base_module WHERE module_name_en not in  {}".format(
        system_module_en)
    dm_modules = pgSql.postgres_execute(dm_module_sql)[0]
    array_list = []
    for dm_module in dm_modules:
        module_code = dm_module[0]
        array_list.append(module_code)
        # 删除模块记录
        delete_module = "DELETE FROM dm_base.t_dm_base_module WHERE module_code = {}".format(module_code)
        pgSql.postgres_execute(delete_module, "delete")
        # 删除Redis缓存中的模块数据
        r.del_hash_data(module_hash_key, str(module_code))
    # 更新总线缓存版本
    r.update_cache_version(array_list, cache_module_version_key)
    print("执行完成，删除dm_module成功！！！")


def del_dm_base_module_table():
    # 删除dm_base模块表数据
    # 查询非系统表的table_code
    dm_table_sql = "select table_code FROM dm_base.t_dm_base_module_table WHERE  table_name_en not in {}".format(
        system_tables_name_en)
    dm_tables = pgSql.postgres_execute(dm_table_sql)[0]
    array_list = []
    for dm_table in dm_tables:
        table_code = dm_table[0]
        array_list.append(table_code)
        # 删除表记录
        delete_module_table = "DELETE FROM dm_base.t_dm_base_module_table WHERE table_code = {}".format(table_code)
        pgSql.postgres_execute(delete_module_table, "delete")
        # 删除Redis缓存中的表数据
        r.del_hash_data(table_hash_key, str(table_code))
    # 更新总线缓存版本
    r.update_cache_version(array_list, cache_table_version_key)
    print("执行完成，删除dm_module_table成功！！！")


def del_dm_base_module_table_filed():
    # 删除dm_base模块表字段数据
    # 查询非系统字段的field_code
    dm_field_sql = "select field_code FROM dm_base.t_dm_base_module_table_field WHERE field_code not in {}".format(
        system_field_code)
    dm_fields = pgSql.postgres_execute(dm_field_sql)[0]
    array_list = []
    for dm_field in dm_fields:
        field_code = dm_field[0]
        array_list.append(field_code)
        # 删除字段记录
        delete_field = "DELETE FROM dm_base.t_dm_base_module_table_field WHERE field_code = {}".format(field_code)
        pgSql.postgres_execute(delete_field, "delete")
        # 删除Redis缓存中的字段数据
        r.del_hash_data(field_hash_key, str(field_code))
    # 更新总线缓存版本
    r.update_cache_version(array_list, cache_fields_version_key)
    print("执行完成，del_dm_base_module_table_filed！！！")


def del_dpa_base_page_design():
    # 删除页面设计数据
    # 查询非系统模块的page_code
    dm_page_sql = "select page_code FROM dm_base.t_dm_base_page  WHERE module_code not in  {}".format(
        system_module_code)

    dm_pages = pgSql.postgres_execute(dm_page_sql)[0]
    for dm_page in dm_pages:
        page_code = dm_page[0]
        # 删除页面记录
        delete_page = "DELETE FROM dm_base.t_dm_base_page WHERE page_code = {}".format(page_code)
        pgSql.postgres_execute(delete_page, "delete")
        # 删除Redis缓存中的页面数据
        r.del_hash_data(page_module_hash_key, str(page_code))
    print("执行完成，删除dm_base_page成功！！！")


def del_dp_base_process_template():
    # 删除流程模板数据
    # 构建查询条件（排除系统流程）
    if len(system_process_code) > 0:
        condition = "WHERE process_code not in {}".format(system_process_code)
    else:
        condition = None
    # 查询非系统流程的process_code
    dp_process_sql = "select process_code FROM dp_base.t_dp_base_process_template {}".format(condition)
    rows = pgSql.postgres_execute(dp_process_sql)[0]
    array_list = []
    for process in rows:
        process_code = process[0]
        array_list.append(process_code)
        # 删除流程模板记录
        delete_process = "DELETE FROM dp_base.t_dp_base_process_template WHERE process_code = {}".format(process_code)
        pgSql.postgres_execute(delete_process, "delete")
        # 删除Redis缓存中的流程模板数据
        r.del_hash_data(process_template_hash_key, str(process_code))
    # 更新总线缓存版本
    r.update_cache_version(array_list, cache_process_template_version_key)
    print("执行完成，删除流程模板成功！！！")


def del_dm_table():
    # 删除/清空dm分表数据
    # 遍历16个dm分表（dm_m0至dm_m15）
    for i in range(16):
        dm = "dm_m{}".format(i)
        # 跳过dm_m9分表
        if i in [9]:
            continue
        # 查询当前分表下的所有表名
        dm_tables_sql = "select table_name from information_schema.tables where table_schema='{}'"
        dm_tables = pgSql.postgres_execute(dm_tables_sql)
        for dm_table in dm_tables:
            table = dm_table[0]
            # 跳过包含特定关键字的表（基础模块和人员相关表）
            if "base_module" and "staff" in table:
                continue
            elif "base_module" and "post" in table:
                continue
            elif "base_module" and "department" in table:
                continue
            elif "base_module" and "seal" in table:
                continue
            elif "base_module" and "user_job_log" in table:
                continue
            elif "base_module" and "customer_job" in table:
                continue
            elif "financing" and "product_grade" in table:
                continue
            elif "financing" and "payment_strategy" in table:
                continue
            elif "financing" and "histo_record" in table:
                continue
            else:
                # 物理删除指定企业的数据（企业ID：284025908866711553）
                delete_flag = 'DELETE FROM "{}"."{}" WHERE "enterprise_code" = 284025908866711553'
                pgSql.postgres_execute(delete_flag)
    print("执行完成，删除dm表成功！！！")


def del_dm_m_assign_table(name):
    # 删除指定名称的dm分表
    # 遍历16个dm分表
    for i in range(16):
        dm = "dm_m{}".format(i)
        # 查询当前分表下的所有表名
        dm_tables_sql = "select table_name from information_schema.tables where table_schema='{}'"
        dm_tables = pgSql.postgres_execute(dm_tables_sql)[0]
        table_name = name

        for dm_table in dm_tables:
            table = dm_table[0]
            # 匹配包含指定名称的表
            if table_name in table:
                # 删除匹配的表
                delete_table = "Drop Table {}.{}".format(dm, table)
                print(delete_table)
                pgSql.postgres_execute(delete_table, "delete")
    print("执行完成，删除dm表成功！！！")


def del_dp_table():
    # 删除/清空dp分表数据
    # 遍历16个dp分表（dp_p0至dp_p15）
    for i in range(16):
        dp = "dp_p{}".format(i)
        # 查询当前分表下的所有表名
        dp_tables_sql = "select table_name from information_schema.tables where table_schema='{}'"
        dp_tables = pgSql.postgres_execute(dp_tables_sql)
        for dp_table in dp_tables:
            table = dp_table[0]
            # 清空表数据（保留表结构）
            delete_table = "TRUNCATE {}.{}".format(dp, table)
            pgSql.postgres_execute(delete_table)
    print("执行完成，删除dp表成功！！！")


# 模块自测入口
if __name__ == '__main__':
    pass
    # 以下为测试用例（当前仅启用del_dm_table函数）
    # del_dm_base_enum()
    # del_dm_base_module()
    # del_dm_base_module_table()
    # del_dm_base_module_table_filed()
    # del_dm_base_page()
    # del_dp_base_process_template()
    del_dm_table()
    # del_dm_m_assign_table("dis_task_detail")
    # del_dm_table()
    # del_dp_table()
    # set_pms_data()
    # set_table()
    # set_enum()
    # del_dm_m_assign_table("a_sell_order")
    # print(str(now))
    # print(type(str(now)))
    # del_dm_m_assign_table("c_product")
    # del_dp_base_process_template()
