# 导入必要的模块
import ast  # 用于安全地解析字符串形式的Python字面量和容器结构
import os, time, platform, yaml, re  # 系统操作、时间处理、平台信息、YAML解析、正则表达式
import logging.config  # 日志配置模块
from logging.handlers import TimedRotatingFileHandler  # 日志轮转处理器

from configparser import ConfigParser  # 配置文件解析器


class MultiCompatibleTimedRotatingFileHandler(TimedRotatingFileHandler):
    # 解决多进程环境下日志文件抢占问题的自定义日志轮转处理器
    def doRollover(self):
        # 关闭当前日志流
        if self.stream:
            self.stream.close()
            self.stream = None
        
        # 计算轮转时间点
        currentTime = int(time.time())
        dstNow = time.localtime(currentTime)[-1]
        t = self.rolloverAt - self.interval
        
        # 处理UTC时间和夏令时问题
        if self.utc:
            timeTuple = time.gmtime(t)
        else:
            timeTuple = time.localtime(t)
            dstThen = timeTuple[-1]
            if dstNow != dstThen:
                if dstNow:
                    addend = 3600
                else:
                    addend = -3600
                timeTuple = time.localtime(t + addend)
        
        # 根据操作系统处理日志文件轮转
        if platform.system() != 'Windows':
            # Linux/Unix系统处理方式 - 使用文件锁避免冲突
            dfn = self.baseFilename + "." + time.strftime(self.suffix, timeTuple)
            if not os.path.exists(dfn):
                f = open(self.baseFilename, 'a')
                fcntl = __import__("fcntl")
                fcntl.lockf(f.fileno(), fcntl.LOCK_EX)
                if os.path.exists(self.baseFilename):
                    os.rename(self.baseFilename, dfn)
        else:
            # Windows系统处理方式
            dfn = self.rotation_filename(self.baseFilename + "." + time.strftime(self.suffix, timeTuple))
            if os.path.exists(dfn):
                os.remove(dfn)
            self.rotate(self.baseFilename, dfn)
        
        # 删除超过备份数量的旧日志文件
        if self.backupCount > 0:
            for s in self.getFilesToDelete():
                os.remove(s)
        
        # 重新打开日志流
        if not self.delay:
            self.stream = self._open()
        
        # 计算下一次轮转时间
        newRolloverAt = self.computeRollover(currentTime)
        while newRolloverAt <= currentTime:
            newRolloverAt = newRolloverAt + self.interval
        
        # 调整夏令时影响
        if (self.when == 'MIDNIGHT' or self.when.startswith('W')) and not self.utc:
            dstAtRollover = time.localtime(newRolloverAt)[-1]
            if dstNow != dstAtRollover:
                if not dstNow:
                    addend = -3600
                else:
                    addend = 3600
                newRolloverAt += addend
        self.rolloverAt = newRolloverAt


class ConfigInfo(ConfigParser):
    @staticmethod
    def register_conf(conf_cls, conf_name):
        # 注册配置信息 - 从config.ini读取指定配置
        conf_path = os.path.join(BASE_DIR, "config", "config.ini")
        config = ConfigParser()  # 创建配置解析器实例
        config.read(conf_path, encoding="utf-8")  # 读取配置文件
        # 解析配置值（使用ast.literal_eval安全解析字符串为Python对象）
        register_conf = ast.literal_eval(config.get(conf_cls, conf_name))
        return register_conf


    @staticmethod
    def register_log():
        # 注册日志配置
        logging.TimedRotatingFileHandler = MultiCompatibleTimedRotatingFileHandler  # 替换默认的日志轮转处理器
        logging_path = os.path.join(BASE_DIR, "logs")  # 日志文件存放路径
        if not os.path.exists(logging_path):
            os.mkdir(logging_path)  # 如果日志目录不存在则创建
        logging_config_path = os.path.join(BASE_DIR, "config", "logging.yaml")  # 日志配置文件路径
        with open(logging_config_path, 'r', encoding='utf-8') as f:
            dict_conf = yaml.safe_load(f.read())  # 加载YAML格式的日志配置
        logging.config.dictConfig(dict_conf)  # 应用日志配置
        logger = logging.getLogger()  # 获取根日志器实例
        return logger

    @staticmethod
    def register_env():
        # 注册环境配置 - 从environment.yaml加载环境变量
        env_path = os.path.join(BASE_DIR, 'config', 'environment.yaml')
        with open(env_path, 'r', encoding='utf-8') as f:
            env = yaml.safe_load(f)  # 加载YAML格式的环境配置
        return env

    @staticmethod
    def register_host():
        # 注册主机配置 - 从host文件解析主机映射关系
        host_path = os.path.join(BASE_DIR, 'config', 'host')
        with open(host_path, 'r', encoding='utf-8') as f:
            host_data = f.read() + "["  # 添加终止符以便正则表达式匹配
        
        # 使用正则表达式提取所有环境头部
        header_list = re.findall(r"\[(?P<header>[^]]+)\]", host_data)
        host_conf = {}
        for env in header_list:
            # 提取每个环境对应的主机配置
            env_host = re.search(rf"\[{env}\](?P<host>[\s\S]*?)\[", host_data).group("host").strip()
            host_list = env_host.strip().split("\n")
            host_dict = {}
            for single_host in host_list:
                host_twain = single_host.strip().split(" ")
                ip = host_twain[0]
                host = host_twain[-1]
                host_dict[host] = ip
            host_conf[env] = host_dict
        return host_conf


BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(BASE_DIR)
LOG = ConfigInfo.register_log()
ENV = ConfigInfo.register_env()
HOST_CONF = ConfigInfo.register_host()
RUN_CONF = ConfigInfo.register_conf("RUN", "RUN_CONF")
CASE_CONF = ConfigInfo.register_conf("CASE", "CASE_CONF")
EMAIL_CONF = ConfigInfo.register_conf("EMAIL", "EMAIL_CONF")
EMAIL_CONF["subject"] = f'{RUN_CONF["PROJECT"]}项目{RUN_CONF["RUN_ENV"]}环境{RUN_CONF["TYPE"]}_自动化测试报告'

if __name__ == "__main__":
    # print(ENV["ERP_TEST"])
    # print(HOST_CONF)
    print(ENV)
    # default_headers = ENV["ERP_TEST"]["servers"]["default_headers"]

