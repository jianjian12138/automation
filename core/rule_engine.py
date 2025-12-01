# 导入必要的模块：异常跟踪、时间处理、正则表达式、抽象语法树、JSON处理、动态导入和类型检查
import traceback, time, regex, ast, json, importlib, types
# 从核心模块导入全局变量g
from core.http_client import g
# 从配置中心导入日志工具
from libs.config_center import LOG
# 从自定义异常模块导入所有异常类
from libs.custom_exception import *

# 定义默认关键字字典，用于存储导入的关键字函数
default_keywords = {}


# 导入指定模块中的关键字函数
def import_keywords(module):
    # 使用importlib动态导入模块
    imported_module = importlib.import_module(module)
    # 遍历模块中的所有属性和值
    for name, item in vars(imported_module).items():  # vars()返回对象的属性和值的字典，等同于obj.__dict__
        # 检查是否为函数类型
        if isinstance(item, types.FunctionType):  # 判断item是否为函数对象
            # 检查关键字是否已存在
            if name in default_keywords:
                # 如果关键字已存在，检查是否是同一个函数（避免重复导入）
                if default_keywords[name] is not item:
                    # 如果是不同的函数，记录警告但不抛出异常（允许覆盖）
                    import logging
                    logging.getLogger(__name__).warning(f"关键字 {name} 已存在，将被覆盖")
                # 无论是否已存在，都更新为最新的函数（允许覆盖）
                default_keywords[name] = item
            else:
                # 将函数添加到默认关键字字典
                default_keywords[name] = item


# 规则关键字处理类
class RuleKeyword:
    # 需要保留的原始关键字列表
    keep_origin_keyword_list = ["smart_sleep"]
    # 类型转换关键字列表
    type_keywords_list = ["int", "str", "float", "bool", "none", "list", "dict", "json"]

    # 执行关键字函数
    def keyword_execute(self, keyword_func_name, args: list,**kwargs:dict):
        try:
            # 检查关键字是否在默认关键字字典中
            if keyword_func_name in default_keywords.keys():
                # 处理带位置参数的情况
                if args:
                    LOG.info(f"关键字 {keyword_func_name} 开始执行，参数列表为 {args}")
                    result = default_keywords[keyword_func_name](*args)
                # 处理带关键字参数的情况
                elif kwargs:
                    LOG.info(f"关键字 {keyword_func_name} 开始执行，参数列表为 {kwargs}")
                    result = default_keywords[keyword_func_name](kwargs)
                # 处理无参数的情况
                else:
                    result = default_keywords[keyword_func_name]()
                LOG.info(f"关键字 {keyword_func_name} 执行完成，参数列表为 {args}，结果为 {result}")
            # 处理类型转换关键字
            elif keyword_func_name in self.type_keywords_list:
                # 类型转换只需要一个参数，处理参数列表
                args = args[0] if len(args) == 1 else ",".join(args)  # 字符串规则中以逗号分隔，类型转换只需要一个参数
                result = self.system_func_execute(keyword_func_name, args)
                LOG.info(f"类型转换关键字 {keyword_func_name} 执行完成，参数为 {args}，结果为 {result}")
            # 处理需要保留的原始关键字
            elif keyword_func_name in self.keep_origin_keyword_list:
                result = getattr(self, keyword_func_name)(*args)
                LOG.info(f"自定义关键字 {keyword_func_name} 执行完成，参数列表为 {args}，结果为 {result}")
            # 关键字不存在时抛出异常
            else:
                raise NoSuchKeyWordException(f"关键字: {keyword_func_name} 没有找到")
        # 处理断言失败异常
        except AssertionFailure as e:
            LOG.error(traceback.format_exc())
            raise e
        # 处理其他异常
        except Exception as exc:
            LOG.error(f"关键字 {keyword_func_name} 运行出错，共 {len(args)} 个参数，参数列表为 {args}")
            # 记录每个参数的信息
            for i, v in enumerate(args):
                LOG.error(f"出错关键字 {keyword_func_name} 的第 {i+1} 个参数 {v}，参数类型 {type(v)}")
            error_message = traceback.format_exc()
            LOG.error(error_message)
            try:
                from autofix.runner import handle_failure
                case_info = getattr(g, "case_info", None)
                step_info = getattr(case_info, "step_info", None) if case_info else None
                handle_failure(
                    case_info=case_info,
                    step_info=step_info,
                    keyword=keyword_func_name,
                    args=args,
                    kwargs=kwargs,
                    exception=exc,
                    error_message=error_message,
                )
            except Exception as autofix_exc:
                LOG.debug(f"自动修复流程执行失败: {autofix_exc}")
            raise KeyWordRuntimeException(f"关键字 {keyword_func_name} 运行出错，参数列表为 {args}；\n\n{error_message}")

        return result

    # 系统类型转换函数执行
    @staticmethod
    def system_func_execute(keyword_func_name, args):
        # 根据关键字类型执行相应的转换
        if keyword_func_name == "str":
            return str(args)
        elif keyword_func_name == "int":
            return int(args)
        elif keyword_func_name == "float":
            return float(args)
        elif keyword_func_name == "bool":
            # 特殊处理字符串类型的布尔值
            if isinstance(args, str) and args.lower() == "true":
                return True
            elif isinstance(args, str) and args.lower() == "false":
                return False
            else:
                return bool(args)
        elif keyword_func_name == "none":
            return None
        # 处理列表类型转换
        elif keyword_func_name == "list" and args[0] == "[" and args[-1] == "]":
            return ast.literal_eval(args)
        # 处理字典类型转换
        elif keyword_func_name == "dict" and args[0] == "{" and args[-1] == "}":
            return ast.literal_eval(args)
        # 处理JSON序列化
        elif keyword_func_name == "json" and isinstance(args, list) or isinstance(args, dict):
            return json.dumps(args, ensure_ascii=False)
        # 类型转换异常
        else:
            raise KeyWordRuntimeException(f"类型转换关键字 {keyword_func_name} 异常，参数 {args}")

    # 智能等待关键字实现
    def smart_sleep(self, rules, default_step=3, default_time_out=30):
        # 转换步长和超时时间为整数
        default_step = int(default_step)
        default_time_out = int(default_time_out)
        LOG.debug(f"开始执行智能等待，步长等待时间为{default_step}s，超时时间为{default_time_out}s")
        # 循环等待直到超时
        while default_time_out - default_step > 0:
            try:
                # 执行规则验证
                validation = self.rule_execute(rules)
            except AssertionError as e:
                validation = e
            # 如果验证通过，提前退出
            if validation is True:
                LOG.debug("满足智能等待退出条件，提前退出")
                return validation
            else:
                # 减少剩余超时时间
                default_time_out = default_time_out - default_step
                LOG.warning(
                    f"{rules} 返回验证条件不通过，结果: {validation}; 继续等待{default_step}s，{default_time_out}s 后超时退出")
                # 等待指定步长
                time.sleep(default_step)
        # 超时退出
        LOG.error(f"{rules} 返回验证条件不通过，智能等待超时退出")
        return validation

    # 规则执行方法（需要子类重写）
    def rule_execute(self, rule_expression):
        # 继承重写
        LOG.warning("未定义的规则解析，返回原数据！")
        return rule_expression


# 字符串规则关键字处理类，继承自RuleKeyword
class RuleStrKeyword(RuleKeyword):
    # 嵌套关键字正则表达式，用于匹配$开头的关键字
    nest_keywords = regex.compile(r'(?P<rule>\$\w+(\((?>[^()]+|(?2))*\)))')

    # 执行字符串规则
    def rule_execute(self, rule_expression):
        # 提取字符串中的标准变量，替换为get_variable关键字（支持Unicode变量名，如中文）
        variable_list = regex.findall(r"&[\p{L}\p{N}_]+", rule_expression)
        for variable in variable_list:
            real_variable = f"$get_variable({variable[1:]})"
            rule_expression = rule_expression.replace(variable, real_variable, 1)

        # 获取最外层的关键字名称
        outermost = regex.search(r"^\$(?P<name>\w+)[\s\S]*\)", rule_expression).group("name")
        # 处理需要保留的原始关键字（如智能等待）
        if outermost in self.keep_origin_keyword_list:  # 智能等待，不能从最里层开始解析
            # 提取参数部分
            param = rule_expression[1:-1].replace(outermost, "", 1)[1:]
            real_rule = regex.search(r"^\$\w+[\s\S]*\)", param).group()
            param = param.replace(real_rule, "rule")
            # 分割参数列表
            param_list = list(map(lambda x: x.strip(), param.split(",")))
            param_list[0] = real_rule
            # 执行关键字
            return self.keyword_execute(outermost, param_list)
        # 处理嵌套规则
        return self.nest_rule(rule_expression, {})

    # 嵌套规则解析
    def nest_rule(self, rule_string, placeholder_dict):
        # 正则表达式匹配最里层的关键字
        inmost_keyword_regex = regex.compile(r'(\$\w+(\((?>[^()]+|(?R))*\)))')
        # 提取元组中的第一个元素作为关键字字符串
        keyword_list = [match[0] for match in regex.findall(inmost_keyword_regex, rule_string)]
        # placeholder_dict用于存储解析后的关键字结果，键为$param_?形式
        if len(keyword_list) == 0:
            try:
                return placeholder_dict.pop(rule_string)
            except KeyError:
                LOG.error(traceback.format_exc())
                LOG.error(f"规则字符串 {rule_string} 解析异常，字符串不能包含()使用，请使用 &变量，代替该字符串")
                raise RuleRuntimeException

        # 去重关键字列表
        keyword_list = list(set(keyword_list))
        for keyword_func in keyword_list:
            # 提取关键字名称
            keyword_name = regex.search(r"^\$(?P<keyword_name>\w+)\([\s\S]*\)$", keyword_func).group("keyword_name")
            # 提取参数部分
            param = regex.search(r"^\$\w+\((?P<param>[\s\S]*)\)$", keyword_func).group("param")
            # 生成结果键
            result_key = f"$param_{len(placeholder_dict)}"
            # 处理无参数情况
            if param == "":
                placeholder_dict[result_key] = self.keyword_execute(keyword_name, [])
            else:
                # 解析参数列表
                param_list = self.param_interpret_list(param, placeholder_dict, keyword_name)
                placeholder_dict[result_key] = self.keyword_execute(keyword_name, param_list)
            # 替换关键字为占位符
            rule_string = rule_string.replace(keyword_func, result_key)
        # 递归解析嵌套规则
        return self.nest_rule(rule_string, placeholder_dict)

    # 参数解释列表
    @staticmethod
    def param_interpret_list(param_str, placeholder_dict, keyword_name):
        real_param_list = []
        # 优先按 ", " 分割，避免切断选择器中的前缀逗号（如 x,//... 或 s,#...）
        if ", " in param_str:
            param_list = param_str.split(", ")
        else:
            # 回退：逐字符扫描，忽略出现在选择器前缀后的逗号（x, / s, / t,）
            param_list = []
            buf = []
            i = 0
            length = len(param_str)
            while i < length:
                ch = param_str[i]
                if ch == ',':
                    # 判断是否是选择器前缀后的逗号：前一字符可能为 x/s/t，且后一字符不是空格
                    prev = param_str[i-1] if i-1 >= 0 else ''
                    nxt = param_str[i+1] if i+1 < length else ''
                    if prev in ('x', 's', 't') and nxt != ' ':
                        # 这是选择器内部逗号，保留
                        buf.append(ch)
                    else:
                        # 作为参数分隔符
                        param_list.append(''.join(buf))
                        buf = []
                else:
                    buf.append(ch)
                i += 1
            # 收尾
            if buf:
                param_list.append(''.join(buf))
        
        # 特殊处理get_host关键字
        if keyword_name == "get_host":
            # 确保参数列表保持原样
            real_param_list = [param.strip() for param in param_list]
            LOG.info(f"处理get_host关键字，原始参数列表: {param_list}，处理后参数列表: {real_param_list}")
            if len(real_param_list) < 2:
                LOG.error(f"get_host关键字需要至少2个参数，但只收到{len(real_param_list)}个: {real_param_list}")
                raise KeyWordIntroductionParameterException(f"get_host关键字需要至少2个参数，但只收到{len(real_param_list)}个: {real_param_list}")
            return real_param_list  # 直接返回处理后的参数列表，避免后续循环干扰
        # 特殊处理get_variable关键字
        elif keyword_name == "get_variable":
            # 确保参数列表保持原样
            real_param_list = [param.strip() for param in param_list]
            return real_param_list
        # 特殊处理get_re_data关键字
        elif keyword_name == "get_re_data":
            name = param_list.pop()
            if name.isdigit():
                param_str = ",".join(param_list)
                param_list = []
                param_list.append(param_str)
                param_list.append(name)
            else:
                param_list = []
                param_list.append(param_str)
        # 特殊处理re_search关键字
        elif keyword_name == "re_search":
            string = param_list.pop()
            if "$" in string:
                param_str = ",".join(param_list)
                param_list = []
                param_list.append(param_str)
                param_list.append(string)
            else:
                name = string
                string = param_list.pop()
                param_str = ",".join(param_list)
                param_list = []
                param_list.append(param_str)
                param_list.append(string)
                param_list.append(name)
        else:
            # 处理参数列表中的占位符
            for param in param_list:
                param = param.strip()
                if "$param_" in param:
                    param_keys = regex.findall(r"(?P<param>\$param_\d+)", param)
                    if param == param_keys[0]:  # 提取到的参数是完整的参数
                        real_param = placeholder_dict.get(param)
                        real_param_list.append(real_param)
                    else:  # 提取到的参数是参数字符串中的一部分
                        for param_key in param_keys:
                            param_value = placeholder_dict.get(param_key)
                            param = param.replace(param_key, str(param_value))
                        real_param_list.append(param)
                    # 删除多余的else分支
                else:
                    real_param_list.append(param)
        return real_param_list

    # 字符串包含规则处理
    def str_contain_rule(self, str_rule_expression):
        while True:
            try:
                # 提取字符串中的关键字表达式
                rule_regex = regex.compile(r'(?P<rule>\$\w+(\((?>[^()]+|(?2))*\)))')
                rule_string = regex.search(rule_regex, str_rule_expression).group("rule")
            except AttributeError:  # 字符串中不存在关键字了
                return str_rule_expression

            # 执行关键字并替换结果
            inner_str = self.rule_execute(rule_string)
            str_rule_expression = str_rule_expression.replace(rule_string, str(inner_str), 1)

    # 调用方法，处理字符串规则
    def __call__(self, str_rule_expression):
        # 检查是否为标准变量
        standard_variate_rule = regex.search(r'^&[\p{L}\p{N}_]+$', str_rule_expression)
        if str_rule_expression and str_rule_expression[0] == "$":  # $ 开头
            # 匹配标准关键字表达式
            keyword_str = regex.search(r'(?P<rule>^\$\w+(\((?>[^()]+|(?2))*\)))', str_rule_expression).group("rule")
            if keyword_str == str_rule_expression:  # 标准关键字字符串表达式
                result = self.rule_execute(str_rule_expression)
            else:  # 字符串包含关键字表达式
                result = self.str_contain_rule(str_rule_expression)
        elif standard_variate_rule:  # 标准变量
            result = self.rule_execute(f"$get_variable({str_rule_expression[1:]})")
        elif "$" in str_rule_expression:  # 字符串包含关键字表达式
            result = self.str_contain_rule(str_rule_expression)
        elif "&" in str_rule_expression:  # 字符串中存在标准变量，关键字强制转换为字符串
            inner_str_rule_list = regex.findall(r"&[\p{L}\p{N}_]+", str_rule_expression)
            new_str_rule_expression = str_rule_expression
            if inner_str_rule_list:
                for inner_str_rule in inner_str_rule_list:
                    # 先检查变量是否存在
                    var_name = inner_str_rule[1:]
                    # 1. 优先从step_variables获取（当前步骤的变量）
                    inner_str = None
                    if hasattr(g, 'case_info') and g.case_info:
                        if hasattr(g.case_info, 'step_info') and g.case_info.step_info:
                            step_vars = getattr(g.case_info.step_info, 'step_variables', {})
                            if var_name in step_vars:
                                inner_str = step_vars[var_name]
                    
                    # 2. 如果step_variables中没有，尝试从common_case_variables获取（公共步骤的变量）
                    # 优先查找公共步骤的变量，因为公共步骤的变量应该在主用例变量之前
                    if inner_str is None:
                        if hasattr(g, 'case_info') and g.case_info:
                            if hasattr(g.case_info, 'step_info') and g.case_info.step_info:
                                step_case_code = getattr(g.case_info.step_info, 'case_code', None)
                                if step_case_code:
                                    common_case_vars = getattr(g.case_info, 'common_case_variables', {})
                                    if step_case_code in common_case_vars:
                                        common_vars = common_case_vars[step_case_code]
                                        if isinstance(common_vars, dict) and var_name in common_vars:
                                            inner_str = common_vars[var_name]
                            # 如果当前步骤的case_code中没有找到，尝试在所有common_case_variables中查找
                            if inner_str is None:
                                common_case_vars = getattr(g.case_info, 'common_case_variables', {})
                                for case_code, common_vars in common_case_vars.items():
                                    if isinstance(common_vars, dict) and var_name in common_vars:
                                        inner_str = common_vars[var_name]
                                        break
                    
                    # 3. 如果还是没有，从case_variables获取（主用例的变量，可以覆盖公共步骤的变量）
                    if inner_str is None:
                        if hasattr(g, 'case_info') and hasattr(g.case_info, 'case_variables') and var_name in g.case_info.case_variables:
                            # 直接获取变量值并处理
                            value = g.case_info.case_variables[var_name]
                            if isinstance(value, str) and ('$' in value or '&' in value):
                                inner_str = rule(value)
                            else:
                                inner_str = value
                    
                    # 4. 如果还是没有，调用get_variable获取
                    if inner_str is None:
                        inner_str = self.rule_execute(f"$get_variable({var_name})")
                    
                    # 如果inner_str仍然是None，记录警告
                    if inner_str is None:
                        LOG.warning(f"变量 {var_name} 未找到，使用空字符串")
                        inner_str = ""
                    
                    new_str_rule_expression = new_str_rule_expression.replace(inner_str_rule, str(inner_str), 1)
            result = self.str_contain_rule(new_str_rule_expression)
        else:
            return str_rule_expression
        LOG.debug(f"字符串规则源数据解析 {str_rule_expression} 完成，结果为 {result}")
        return result


# 列表规则关键字处理类，继承自RuleKeyword
class RuleListKeyword(RuleKeyword):
    # 执行列表规则
    def rule_execute(self, list_rule_expression: list):
        if len(list_rule_expression) == 0:  # 空列表
            return list_rule_expression
        # 获取第一个元素作为函数名
        func_name = list_rule_expression[0]
        # 如果第一个元素是列表，递归解析
        if isinstance(func_name, list):  # 列表第一个是列表，可能也是关键字列表
            result = self.rule_execute(func_name)
            list_rule_expression[0] = result
            return self.rule_execute(list_rule_expression)
        # 如果第一个元素不是字符串，直接返回
        if isinstance(func_name, str) is False:  # 列表第一个是非字符串的其他类型
            return list_rule_expression
        # 如果第一个元素是$或&开头的关键字，直接返回
        if func_name[0] == "$" or func_name[0] == "&":  # 列表第一个是字符串关键字
            return list_rule_expression
        # 特殊处理smart_sleep关键字
        if func_name == "smart_sleep":  # 智能等待特殊处理
            return self.smart_sleep(*list_rule_expression[1:])

        # 处理参数列表
        args = []
        for i in range(1, len(list_rule_expression)):
            # 如果参数是列表，递归解析
            if isinstance(list_rule_expression[i], list):  # 关键字参数是关键字列表
                args.append(self.rule_execute(list_rule_expression[i]))  # 递归解析，不支持两种关键字嵌套
            else:
                args.append(list_rule_expression[i])
        # 执行关键字
        return self.keyword_execute(func_name, args)

    # 调用方法，处理列表规则
    def __call__(self, list_rule_expression):
        if list_rule_expression and list_rule_expression[0] and \
                isinstance(list_rule_expression[0], str) and list_rule_expression[0][0] == "$":
            LOG.debug("列表规则源数据解析 {}".format(list_rule_expression))
            result = self.rule_execute(list_rule_expression)
            if not result == list_rule_expression:
                LOG.debug(f"列表规则源数据解析 {list_rule_expression} 完成，结果为 {result}")
            return result
        else:
            return list_rule_expression


# 规则引擎主类
class Rule:
    def __init__(self):
        # 初始化字符串规则执行器和列表规则执行器
        self.str_rule_execute = RuleStrKeyword()
        self.list_rule_execute = RuleListKeyword()

    # 获取关键字文档
    @staticmethod
    def keyword_doc():
        keywords_doc = {}
        # 遍历默认关键字字典，收集函数文档
        for func_name, func in default_keywords.items():
            keywords_doc[func_name] = func.__doc__
        return keywords_doc

    # 验证规则处理
    def validate_rule_dispose(self, explainable, wait_retry=None):
        # 处理字符串类型的规则表达式
        if isinstance(explainable, str):
            # 替换比较运算符为相应的关键字
            outermost_rule = regex.sub(r"\((?>[^()]+|(?R))*\)", "", explainable)
            if "==" in outermost_rule:
                value_list = explainable.split("==")
                explainable = f"$equal({value_list[0]},{value_list[1]})"
            elif "!=" in outermost_rule:
                value_list = explainable.split("!=")
                explainable = f"$unequal({value_list[0]},{value_list[1]})"
            elif ">=" in outermost_rule:
                value_list = explainable.split(">=")
                explainable = f"$great_than_or_equal({value_list[0]},{value_list[1]})"
            elif "<=" in outermost_rule:
                value_list = explainable.split("<=")
                explainable = f"$less_than_or_equal({value_list[0]},{value_list[1]})"
            elif ">>" in outermost_rule:
                value_list = explainable.split(">>")
                explainable = f"$left_contain({value_list[0]},{value_list[1]})"
            elif "<<" in outermost_rule:
                value_list = explainable.split("<<")
                explainable = f"$right_contain({value_list[0]},{value_list[1]})"
            elif ">" in outermost_rule and "<" not in outermost_rule and ">=" not in outermost_rule and "<<" not in outermost_rule and ">>" not in outermost_rule:
                value_list = explainable.split(">")
                explainable = f"$greater_than({value_list[0]},{value_list[1]})"
            elif "<" in outermost_rule and ">" not in outermost_rule and "<=" not in outermost_rule and "<<" not in outermost_rule and ">>" not in outermost_rule:
                value_list = explainable.split("<")
                explainable = f"$less_than({value_list[0]},{value_list[1]})"

            # 添加智能等待
            if wait_retry is not None:
                explainable = f"$smart_sleep({explainable},{wait_retry[0]},{wait_retry[1]})"
        # 处理列表类型的规则表达式
        elif isinstance(explainable, list):
            if wait_retry is not None:
                explainable = explainable + wait_retry

        # 解析规则
        return self.rule_parse(explainable)

    # 规则解析主方法
    def rule_parse(self, explainable):
        # 处理字典类型
        if isinstance(explainable, dict):
            for key, value in explainable.items():
                result = self.rule_parse(value)
                explainable[key] = result
        # 处理列表类型
        elif isinstance(explainable, list):
            explainable = self.list_rule_execute(explainable)  # 执行列表类型关键字
            if isinstance(explainable, list):  # 如果解析结果还是列表类型
                for index, value in enumerate(explainable):  # 循环递归解析
                    explainable[index] = self.rule_parse(value)
        # 处理字符串类型
        elif isinstance(explainable, str):  # 字符串类型，可能包含关键字
            explainable = self.str_rule_execute(explainable)
        return explainable

    # 调用方法，启动规则解析
    def __call__(self, explainable):
        return self.rule_parse(explainable)


# 导入keywords模块中的关键字
# 不再导入keywords模块（不使用autotest_elegant）
# import_keywords("keywords")

# 定义get_variable函数（用于Web UI测试）
def get_variable(var_name: str):
    """
    获取变量值
    从step_variables、case_variables、common_case_variables或全局变量中获取
    """
    from core.http_client import g
    
    # 1. 尝试从step_variables获取
    if hasattr(g, 'case_info') and g.case_info:
        if hasattr(g.case_info, 'step_info') and g.case_info.step_info:
            step_vars = getattr(g.case_info.step_info, 'step_variables', {})
            if var_name in step_vars:
                return step_vars[var_name]
        
        # 2. 尝试从case_variables获取
        case_vars = getattr(g.case_info, 'case_variables', {})
        if var_name in case_vars:
            return case_vars[var_name]
        
        # 3. 尝试从common_case_variables获取（公共步骤的变量）
        if hasattr(g.case_info, 'step_info') and g.case_info.step_info:
            step_case_code = getattr(g.case_info.step_info, 'case_code', None)
            if step_case_code:
                common_case_vars = getattr(g.case_info, 'common_case_variables', {})
                if step_case_code in common_case_vars:
                    common_vars = common_case_vars[step_case_code]
                    if isinstance(common_vars, dict) and var_name in common_vars:
                        return common_vars[var_name]
        
        # 4. 如果当前步骤的case_code中没有找到，尝试在所有common_case_variables中查找
        common_case_vars = getattr(g.case_info, 'common_case_variables', {})
        for case_code, common_vars in common_case_vars.items():
            if isinstance(common_vars, dict) and var_name in common_vars:
                return common_vars[var_name]
    
    # 5. 尝试从全局变量获取
    if hasattr(g, 'variables') and isinstance(g.variables, dict):
        if var_name in g.variables:
            return g.variables[var_name]
    
    # 变量不存在，返回None
    LOG.warning(f"变量未找到: {var_name}")
    return None

# 将get_variable添加到关键字字典
default_keywords['get_variable'] = get_variable

# 定义get_global_variable函数（用于获取环境配置中的全局变量）
def get_global_variable(env_name: str, var_name: str):
    """
    获取环境配置中的全局变量
    
    :param env_name: 环境名称（如 ERP_TEST）
    :param var_name: 变量名称（如 pms_phone_number）
    :return: 变量值
    """
    from libs.config_center import ENV
    
    try:
        env_config = ENV.get(env_name, {})
        global_vars = env_config.get('global_variable', {})
        value = global_vars.get(var_name)
        
        if value is None:
            LOG.warning(f"全局变量未找到: {env_name}.global_variable.{var_name}")
            return None
        
        return value
    except Exception as e:
        LOG.error(f"获取全局变量失败: {env_name}.global_variable.{var_name}, 错误: {e}")
        return None

# 将get_global_variable添加到关键字字典
default_keywords['get_global_variable'] = get_global_variable

# 导入Playwright关键字（用于Web UI测试）
try:
    import_keywords("keywords.playwright_keywords")
except Exception as e:
    # 如果导入失败，不影响其他功能
    pass

# 创建规则引擎实例
rule = Rule()

# 主函数，用于测试
if __name__ == "__main__":
    # 导入测试所需的模块和类
    from core.case_context import UiCaseInfo
    from core.case_context import UiStepInfo
    from libs.config_center import ENV

    # 设置全局变量
    g.env = "CF_TEST_IT"
    g.case_info = UiCaseInfo({})
    g.case_info.step_info = UiStepInfo({})
    g.case_info.step_info.step_variables = {"a": "1", "b": "2", "c": "3"}
    # 测试数据
    d = {'ret': '0',
         'ticket': 't03QniYwD3hbJHaXNS8U8Nt8l0DNFqZd5tnCFRQw37W3vW6AoRM_q5E0RypaZq7P6RluSjHLNYDHI93HPDsCos1D-KMxXaieAKp',
         'randstr': '@Vk6'}
    # 测试规则解析
    print(rule("$bp(&a&b&c)"))
    def param_interpret_list(self, keyword_name, param_list):
        """参数解释成列表"""
        if not param_list:
            return []
        # 处理 get_re_data 和 re_search 特殊关键字
        if keyword_name in ['get_re_data', 're_search']:
            if len(param_list) == 2:
                return [param_list[0], param_list[1]]
            elif len(param_list) == 1:
                return [param_list[0], '.*?']
            else:
                return param_list
        # 对于其他关键字，直接返回参数列表
        return param_list
