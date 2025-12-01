# 自定义异常基类 - 继承自Python内置Exception
class MyBaseFailure(Exception):
    pass  # 空实现，作为所有失败类异常的基类


# 断言失败异常 - 继承自MyBaseFailure
class AssertionFailure(MyBaseFailure):
    pass  # 用于表示断言失败场景的异常


# 自定义错误基类 - 继承自Python内置Exception
class MyBaseError(Exception):
    pass  # 空实现，作为所有错误类异常的基类


# 规则运行时异常 - 继承自MyBaseError
class RuleRuntimeException(MyBaseError):
    pass  # 用于表示规则执行过程中发生的运行时错误


# 关键字不存在异常 - 继承自MyBaseError
class NoSuchKeyWordException(MyBaseError):
    pass  # 用于表示未找到指定关键字时抛出的异常


# 关键字运行时异常 - 继承自MyBaseError
class KeyWordRuntimeException(MyBaseError):
    pass  # 用于表示关键字执行过程中发生的运行时错误


# 规则解析异常 - 继承自MyBaseError
class RuleParsingException(MyBaseError):
    pass  # 用于表示规则解析过程中发生的错误


# 关键字参数引入异常 - 继承自MyBaseError
class KeyWordIntroductionParameterException(MyBaseError):
    pass  # 用于表示关键字参数引入过程中发生的错误


# HTTP方法错误异常 - 继承自MyBaseError
class AutoHttpMethodErrorException(MyBaseError):
    pass  # 用于表示HTTP请求方法错误的异常


# 解析器类型枚举无效异常 - 继承自MyBaseError
class ParserTypeEnumInvalidException(MyBaseError):
    pass  # 用于表示解析器类型枚举值无效的异常


# WebDriver运行时异常 - 继承自MyBaseError
class WebDriverRuntimeException(MyBaseError):
    pass  # 用于表示WebDriver操作过程中发生的运行时错误


# WebUI运行时异常 - 继承自MyBaseError
class WebUiRuntimeException(MyBaseError):
    pass  # 用于表示WebUI操作过程中发生的运行时错误


# Sqlmap连接错误 - 继承自MyBaseError
class SqlmapConnectionError(MyBaseError):
    pass  # 用于表示与Sqlmap工具建立连接失败的异常
