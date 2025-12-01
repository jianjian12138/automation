import logging
import colorama
from colorama import Fore, Style
from libs.custom_exception import AssertionFailure

class WindowsColorFormatter(logging.Formatter):
    def __init__(self, fmt=None, datefmt=None, style='%'):
        super().__init__(fmt, datefmt, style)
        # 增强colorama初始化，确保在Windows上正确工作
        colorama.init(autoreset=True, convert=True)

    def format(self, record):
        # 为不同日志级别设置不同颜色
        if record.levelname == 'ERROR':
            record.levelname = f"{Fore.RED}{record.levelname}{Style.RESET_ALL}"
            # 检查是否为AssertionFailure异常
            if record.exc_info and isinstance(record.exc_info[1], AssertionFailure):
                record.msg = f"{Fore.RED}{record.msg}{Style.RESET_ALL}"
                # 修改：直接对整个异常文本进行着色
                if record.exc_text:
                    # 提取异常类型名称
                    exc_type_name = type(record.exc_info[1]).__name__
                    # 构建完整的异常类型字符串（包含模块路径）
                    full_exc_type_name = f"libs.custom_exception.{exc_type_name}:"
                    # 替换异常类型名称
                    record.exc_text = record.exc_text.replace(
                        full_exc_type_name,
                        f"{Fore.RED}{full_exc_type_name}{Style.RESET_ALL}"
                    )
        elif record.levelname == 'WARNING':
            record.levelname = f"{Fore.YELLOW}{record.levelname}{Style.RESET_ALL}"
        elif record.levelname == 'INFO':
            record.levelname = f"{Fore.GREEN}{record.levelname}{Style.RESET_ALL}"
        elif record.levelname == 'DEBUG':
            record.levelname = f"{Fore.BLUE}{record.levelname}{Style.RESET_ALL}"

        # 添加：将包含'响应结果'的文本设置为蓝色
        if '响应数据:' in record.msg:
            # 分割'响应数据:'和后面的值
            prefix, value = record.msg.split('响应数据:', 1)
            # 重新组合，将值设置为深蓝色
            record.msg = f"{prefix}响应数据:{Fore.BLUE}{value}{Style.RESET_ALL}"
        if record.exc_text and '响应数据:' in record.exc_text:
            # 分割'响应数据:'和后面的值
            prefix, value = record.exc_text.split('响应数据:', 1)
            # 重新组合，将值设置为深蓝色
            record.exc_text = f"{prefix}响应数据:{Fore.BLUE}{value}{Style.RESET_ALL}"

        return super().format(record)