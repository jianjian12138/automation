# 注释掉的gevent猴子补丁导入，用于协程支持（当前未启用）
# from gevent import monkey
# 注释掉的猴子补丁应用，会修改标准库以支持协程（当前未启用）
# monkey.patch_all()
# 导入os模块用于文件系统操作
import os
# 导入pymysql模块用于MySQL数据库连接，getpass用于获取用户输入，platform用于获取系统信息，json用于JSON处理
import pymysql, getpass, platform, json
# 导入time模块用于时间相关操作
import time

# 导入re模块用于正则表达式操作
import re
# 从pymongo导入MongoClient用于MongoDB数据库连接
from pymongo import MongoClient
# 从sshtunnel导入SSHTunnelForwarder用于SSH隧道转发
from sshtunnel import SSHTunnelForwarder
# 从libs.config_center导入LOG用于日志记录
from libs.config_center import LOG
# 导入psycopg2模块用于PostgreSQL数据库连接
import psycopg2


# 定义DataBase类，用于统一管理不同类型数据库的连接和操作
class DataBase(object):
    # 类初始化方法，接收数据库连接参数、数据库类型和SSH配置
    def __init__(self, conn_dict, db_type="postgres", ssh_dict=None):
        self.conn_dict = conn_dict  # 存储数据库连接参数
        self.db_type = db_type      # 存储数据库类型，默认为postgres
        self.db = None              # 数据库连接对象
        self.cursor = None          # 数据库游标对象
        self.connect_type = None    # 连接类型（如ssh）
        self.ssh_dict = ssh_dict    # SSH配置参数
        self.ssh_server = None      # SSH隧道对象

    # 上下文管理器进入方法，用于with语句
    def __enter__(self):
        # 从连接参数中获取连接类型，默认为None
        self.connect_type = self.conn_dict.pop("connect_type", None)
        # 如果连接类型为ssh，则建立SSH隧道
        if self.connect_type == "ssh":
            self.ssh_connect()
        # 建立数据库连接
        self.db_connect()
        return self

    # 上下文管理器退出方法，用于with语句
    def __exit__(self, exc_type, exc_value, tb):
        # 关闭数据库连接和SSH隧道
        self.close()

    # 数据库连接方法，根据数据库类型选择不同的连接方式
    def db_connect(self):
        if self.db_type == "mysql":
            # MySQL数据库连接，使用pymysql
            self.db = pymysql.connect(**self.conn_dict)
            # 创建游标对象，使用DictCursor以字典形式返回结果
            self.cursor = self.db.cursor(cursor=pymysql.cursors.DictCursor)
        elif self.db_type == "mongodb":
            # MongoDB数据库连接，使用MongoClient
            self.db = MongoClient(**self.conn_dict)
        elif self.db_type == "postgres":
            # PostgreSQL数据库连接，使用psycopg2
            self.db = psycopg2.connect(**self.conn_dict)
            # 创建游标对象
            self.cursor = self.db.cursor()
        else:
            # 抛出不支持的数据库类型异常
            raise TypeError(f"暂未支持 {self.db_type} 数据库")

    # SSH隧道连接方法
    def ssh_connect(self):
        # 获取SSH私钥路径
        private_key = self.ssh_dict.get("private_key")
        # 如果是Windows系统且私钥路径包含{}，则替换为当前用户名
        if private_key and platform.system() == 'Windows' and '{}' in private_key:
            private_key = self.ssh_dict["private_key"].format(getpass.getuser())

        # 创建SSH隧道转发器
        self.ssh_server = SSHTunnelForwarder(
            (self.ssh_dict["host"], self.ssh_dict["port"]),
            ssh_username=self.ssh_dict["username"],
            ssh_pkey=private_key,
            ssh_password=self.ssh_dict.get("ssh_password"),
            remote_bind_address=(self.conn_dict['host'], self.conn_dict['port']))
        # 启动SSH隧道
        self.ssh_server.start()

        # 修改数据库连接参数为SSH隧道的本地绑定地址
        self.conn_dict['host'] = '127.0.0.1'
        self.conn_dict['port'] = self.ssh_server.local_bind_port

    # 关闭数据库连接和SSH隧道
    def close(self):
        # 关闭MySQL或PostgreSQL游标
        if self.db_type == "mysql" or self.db_type == "postgres":
            self.cursor.close()
        # 关闭数据库连接
        self.db.close()
        # 关闭SSH连接
        if self.connect_type == "ssh":
            self.ssh_server.close()

    # PostgreSQL执行SQL方法，返回查询结果或影响行数
    def postgres_execute(self, sql_text) -> list:
        # 正则表达式模式，用于匹配SELECT语句
        pattern = r"\bselect\b\s+[^;]+from\s+"
        try:
            # 如果sql_text是元组或列表，则遍历执行每个SQL语句
            if isinstance(sql_text, tuple) or isinstance(sql_text, list):
                for i in range(len(sql_text)):
                    LOG.info("开始运行的postgreSQL执行脚本：" + sql_text[i])
                    if sql_text[i]:
                        self.cursor.execute(sql_text[i])
                        # 使用正则表达式检查是否为SELECT语句
                        match = re.search(pattern, sql_text[i], re.IGNORECASE)
                        if match:
                            # 获取查询结果
                            data = self.cursor.fetchall()
                            # 如果结果为空元组，则转换为空列表
                            if data == ():
                                data = []
                        else:
                            # 获取影响行数
                            data = self.cursor.rowcount
                        LOG.info("postgreSQL获取到数据为： {}".format(data))
            else:
                # 执行单个SQL语句
                LOG.info("开始运行的postgreSQL执行脚本：" + sql_text)
                self.cursor.execute(sql_text)
                match = re.search(pattern, sql_text, re.IGNORECASE)
                if match:
                    data = self.cursor.fetchall()
                    if data == ():
                        data = []
                else:
                    data = self.cursor.rowcount
                LOG.info("postgreSQL获取到数据为： {}".format(data))

        except Exception as e:
            # 发生异常时回滚事务
            self.db.rollback()  # 事务回滚
            raise e
        else:
            # 无异常时提交事务
            self.db.commit()  # 事务提交
            LOG.info('postgreSQL事务处理成功')
            return data

    # MySQL执行SQL方法，返回查询结果
    def mysql_execute(self, sql_text) -> list:
        try:
            # 如果sql_text是元组或列表，则遍历执行每个SQL语句
            if isinstance(sql_text, tuple) or isinstance(sql_text, list):
                for i in range(len(sql_text)):
                    LOG.info("开始运行的sql执行脚本：" + sql_text[i])
                    self.cursor.execute(sql_text[i])
            else:
                # 执行单个SQL语句
                LOG.info("开始运行的sql执行脚本：" + sql_text)
                self.cursor.execute(sql_text)
            # 获取查询结果
            data = self.cursor.fetchall()
            # 如果结果为空元组，则转换为空列表
            if data == ():
                data = []
        except Exception as e:
            # 发生异常时回滚事务
            self.db.rollback()  # 事务回滚
            raise e
        else:
            # 无异常时提交事务
            self.db.commit()  # 事务提交
            LOG.info('mysql事务处理成功')
            LOG.info("mysql获取到数据为： {}".format(data))
        return data

    # MongoDB查询方法
    def mongo_find(self, db_name, table_name, json_text):
        # 获取数据库对象
        db = self.db[db_name]
        # 获取集合对象
        collection = db[table_name]
        # 解析查询条件
        if isinstance(json_text, dict):
            json_obj = json_text
        elif isinstance(json_text, str) and json_text[0] == "{" and json_text[-1] == "}":
            # 如果是JSON字符串，则解析为字典
            json_obj = json.loads(json_text, encoding='utf-8')
        else:
            # 抛出查询格式错误异常
            raise TypeError("mongodb 查询输入错误，请检查查询语句")
        # 执行查询并转换结果为列表
        data = list(collection.find(json_obj))
        return data


# 当模块直接运行时执行的代码
if __name__ == "__main__":
    # 从libs.config_center导入ENV配置
    from libs.config_center import ENV

    # 定义遍历目录下所有文件的函数
    def list_files(directory):
        for root, dirs, files in os.walk(directory):
            for file in files:
                sql_files = os.path.join(root, file)

                with open(sql_files, 'r', encoding="utf-8") as f:
                    sql_script = f.read()
                # 按换行符分割SQL语句（注意：此处可能应为\n而非/n）
                sql_statements = sql_script.split("/n")
                for sql_statement in sql_statements:
                    print("-----------------------------------------------")
                    # 使用DataBase上下文管理器连接数据库
                    with DataBase(ENV["ERP_TEST"]["data_base"]['default']) as pgSql:
                        data = pgSql.postgres_execute(sql_statement)
                        time.sleep(0.5)

                        # time.sleep(1)
                        # if cont:
                        # sql_statements.remove(sql_statement)

    # 调用list_files函数，遍历指定目录下的SQL文件并执行
    list_files(r"C:\Users\95768\Desktop\ERP\JAR包\官网产品和产品类型脚本(3)\官网产品和产品类型脚本")
