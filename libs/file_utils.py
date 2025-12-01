# 导入系统模块：文件操作(os)、数据解析(json/yaml)、Excel处理(xlrd/pandas)、线程(threading)、系统操作(sys)、文件复制(shutil)、队列(queue)
import os, json, xlrd, threading, sys, yaml, shutil, queue
# 导入pandas库用于Excel数据处理
import pandas as pd
# 从项目配置中心导入基础路径(BASE_DIR)和日志对象(LOG)
from libs.config_center import BASE_DIR, LOG
# 从xlrd库导入打开Excel工作簿的函数
from xlrd import open_workbook

# 定义临时文件存储路径：项目根目录/files/temporary
TEMPORARY_PATH = os.path.join(BASE_DIR, "files", "temporary")
# 定义图片文件存储路径：项目根目录/files/images
IMAGES_PATH = os.path.join(BASE_DIR, "files", "images")
# 如果临时目录不存在则创建
if not os.path.exists(TEMPORARY_PATH):
    os.mkdir(TEMPORARY_PATH)


# 递归查找指定目录下的目标文件并返回绝对路径
def get_file_path(file_path, file_target):
    # 如果目录不存在，直接返回None
    if not os.path.exists(file_path):
        return None
    # 遍历file_path目录下的所有子目录和文件
    for root, dirs, files in os.walk(file_path):
        # 检查当前目录下的文件是否包含目标文件
        for file in files:
            if file == file_target:
                # 返回目标文件的绝对路径
                return os.path.join(root, file)
    return None


# 读取SQL/Mongo脚本文件并返回内容
def sql_file_data(sql_file):
    # 查找SQL脚本文件(.sql)
    sql_path = get_file_path(os.path.join(BASE_DIR, "files", "sql"), f"{sql_file}.sql")
    if sql_path and os.path.isfile(sql_path):
        LOG.info(f"当前sql脚本路径：{sql_path}")
        with open(sql_path, "r", encoding="utf-8") as f:
            data = f.read()
        return data

    # 未找到SQL文件时查找Mongo脚本文件(.json)
    sql_path = get_file_path(os.path.join(BASE_DIR, "files", "sql"), f"{sql_file}.json")
    if sql_path and os.path.isfile(sql_path):
        LOG.info(f"当前mongo脚本路径：{sql_path}")
        with open(sql_path, "r", encoding="utf-8") as f:
            data = f.read()
        data = json.loads(data, encoding="utf-8")
        return data

    # 未找到脚本文件时直接返回文件名
    return sql_file


# 读取公用用例步骤文件(YAML/JSON)并返回解析后的数据
def common_file_data(case_type, common_step_file):
    """
    读取公用用例步骤文件(YAML/JSON)并返回解析后的数据
    
    支持多种查找方式：
    1. 精确匹配文件名
    2. 不区分大小写匹配
    3. 驼峰命名转下划线命名（CommonLogin -> common_login）
    4. 新旧目录结构（cases/web 和 cases_web）
    5. case_type 映射（web_ui -> web）
    """
    import re
    
    # case_type 映射（兼容旧命名）
    case_type_map = {
        "web_ui": "web",
        "web_ui_test": "web",
    }
    # 如果 case_type 在映射中，使用映射后的值
    actual_case_type = case_type_map.get(case_type, case_type)
    
    # 将驼峰命名转换为下划线命名（如 CommonLogin -> common_login）
    def camel_to_snake(name):
        # 在大写字母前插入下划线，然后转小写
        s1 = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
        return re.sub('([a-z0-9])([A-Z])', r'\1_\2', s1).lower()
    
    # 生成可能的文件名列表
    possible_names = [
        common_step_file,  # 原始名称（如 CommonLogin）
        common_step_file.lower(),  # 全小写（如 commonlogin）
        camel_to_snake(common_step_file),  # 驼峰转下划线（如 common_login）
    ]
    # 去重
    possible_names = list(dict.fromkeys(possible_names))
    
    # 支持的目录结构（同时支持原始 case_type 和映射后的 case_type）
    possible_dirs = []
    for ct in [case_type, actual_case_type]:
        possible_dirs.append(os.path.join(BASE_DIR, "cases", ct, "common_step"))  # 新结构：cases/web/common_step
        possible_dirs.append(os.path.join(BASE_DIR, f"cases_{ct}", "common_step"))  # 旧结构：cases_web/common_step
    # 去重
    possible_dirs = list(dict.fromkeys(possible_dirs))
    
    # 支持的扩展名
    extensions = [".yaml", ".json"]
    
    # 尝试所有可能的组合
    for directory in possible_dirs:
        if not os.path.exists(directory):
            LOG.debug(f"目录不存在，跳过: {directory}")
            continue
        for name in possible_names:
            for ext in extensions:
                filename = f"{name}{ext}"
                common_path = get_file_path(directory, filename)
                if common_path and os.path.isfile(common_path):
                    LOG.info(f"找到公共步骤文件: {common_path}")
                    if ext == ".yaml":
                        with open(common_path, 'r', encoding='utf-8') as f:
                            data = yaml.safe_load(f.read())
                        return data, common_path
                    else:  # .json
                        with open(common_path, "r", encoding="utf-8") as f:
                            data = f.read()
                        data = json.loads(data, encoding="utf-8")
                        return data, common_path
                else:
                    LOG.debug(f"未找到文件: {os.path.join(directory, filename)}")

    # 未找到文件时抛出异常
    raise NameError(f"没有该公用用例步骤：{common_step_file}（已尝试：{', '.join(possible_names)}）")


# 读取Excel驱动数据文件并返回多Sheet页数据字典
def xlsx_file_data(case_type, drive_data_file, case_path):
    # 优先查找用例文件同级目录下的Excel文件
    file_path = os.path.join(os.path.dirname(case_path), f"{drive_data_file}.xlsx")
    if os.path.isfile(file_path):
        xlsx_path = file_path
    else:
        # 未找到时查找公共驱动数据目录
        xlsx_path = get_file_path(os.path.join(BASE_DIR, f"cases_{case_type}", "drive_data"), f"{drive_data_file}.xlsx")
    drive_data = {}
    if xlsx_path and os.path.isfile(xlsx_path):
        book = xlrd.open_workbook(xlsx_path)
        table_names = book.sheet_names()
        for name in table_names:
            # 读取Sheet数据并转换为字典列表，空白单元格用空字符串填充
            data = pd.read_excel(book, sheet_name=name).fillna("").to_dict(orient='records')
            drive_data[name] = data
    else:
        raise NameError(f"没有该驱动数据文件：{drive_data_file}.xlsx")

    return drive_data


# 保存Locust性能测试用例和驱动数据
def locust_case_save(file_name, case_content, report_data):
    drive_data_file_name = case_content.get("case_data")
    case_code = case_content.get("case_code")
    if drive_data_file_name:
        # 获取源驱动数据文件路径
        xlsx_path = get_file_path(os.path.join(BASE_DIR, "cases_api", "drive_data"), f"{drive_data_file_name}.xlsx")
        # 根据驱动数据名称类型确定目标路径
        if isinstance(drive_data_file_name, str):
            drive_data_path = os.path.join(BASE_DIR, "cases_locust", "drive_data", f"{drive_data_file_name}.xlsx")
        elif isinstance(drive_data_file_name, int):
            drive_data_path = os.path.join(BASE_DIR, "cases_locust", "drive_data", f"{file_name.split('.')[0]}.xlsx")
            case_content["case_data"] = file_name.split('.')[0]
        else:
            raise TypeError("case_data值类型错误")

    # 保存JSON格式的测试用例
    if file_name.split(".")[1] == "json":
        locust_data_file = os.path.join(BASE_DIR, "cases_locust", "test_cases", file_name)
        with open(locust_data_file, "w", encoding="utf-8") as dump_f:
            json.dump(case_content, dump_f, indent=4, ensure_ascii=False)

    # 处理驱动数据文件
    if drive_data_file_name:
        if report_data:
            # 从报告数据生成驱动数据
            drive_data = []
            for case_info in report_data:
                locust_variables = case_info.case_variables
                drive_data.append(locust_variables)
            pd.DataFrame(drive_data).to_excel(drive_data_path, sheet_name=case_code, index=False)
        else:
            # 直接复制源驱动数据文件
            shutil.copyfile(xlsx_path, drive_data_path)


# 加载Locust压测用例和驱动数据
def case_data_gain(case_path):
    # 获取用例文件路径
    case_path = case_dir_path("locust", case_path)[0]
    print("加载locust 压测用例", case_path)
    with open(case_path, 'r', encoding="utf-8") as f:
        case_content = json.loads(f.read())
    # 移除优先级字段
    case_content.pop("priority", None)
    case_code = case_content.get("case_code")
    case_data_name = case_content.pop("case_data", None)
    case_data = None
    if case_data_name:
        # 加载对应的驱动数据
        case_data = xlsx_file_data("locust", case_data_name, case_path).get(case_code)
    LOG.info("压测数据加载完成！")
    return case_content, case_data


# 解析用例目录路径，返回用例文件列表
def case_dir_path(case_type, dir_target):
    # 不再导入keywords模块（不使用autotest_elegant）
    # from core.rule_engine import import_keywords
    abs_path = os.path.abspath(dir_target)
    # 支持新的目录结构：cases/web, cases/api, cases/mobile
    # 也支持旧的目录结构：cases_web_ui/test_cases, cases_api/test_cases
    if case_type == "web_ui":
        # 尝试新结构：cases/web
        new_path = os.path.join(BASE_DIR, "cases", "web")
        if os.path.exists(new_path):
            file_path = new_path
        else:
            # 回退到旧结构：cases_web_ui/test_cases
            file_path = os.path.join(BASE_DIR, f"cases_{case_type}", "test_cases")
    else:
        file_path = os.path.join(BASE_DIR, f"cases_{case_type}", "test_cases")

    # 处理文件路径（单文件模式）
    if "." in dir_target:
        case_path = None
        # 首先检查是否为绝对路径的文件
        if os.path.isfile(abs_path):
            case_path = abs_path
        # 检查是否为相对于file_path的路径
        elif os.path.isfile(os.path.join(file_path, dir_target)):
            case_path = os.path.join(file_path, dir_target)
        # 在file_path目录树中搜索文件
        else:
            case_path = get_file_path(file_path, dir_target)
        # 如果还是没找到，尝试直接使用abs_path（处理中文文件名编码问题）
        if not case_path or not os.path.isfile(case_path):
            # 尝试直接使用绝对路径（即使os.path.isfile返回False，文件可能仍然存在）
            try:
                with open(abs_path, 'r', encoding='utf-8') as f:
                    f.read(1)  # 尝试读取一个字符
                case_path = abs_path
            except Exception as e:
                # 如果打开失败，尝试使用文件名匹配
                file_name = os.path.basename(abs_path)
                dir_name = os.path.dirname(abs_path)
                if os.path.exists(dir_name):
                    try:
                        files = os.listdir(dir_name)
                        for f in files:
                            if f == file_name or (f.endswith('.yaml') and file_name.endswith('.yaml')):
                                case_path = os.path.join(dir_name, f)
                                break
                    except:
                        pass
        # 验证文件格式
        if case_path:
            ext = case_path.split(".")[-1].lower()
            if ext in ["json", "json5", "yaml", "xlsx", "xls"]:
                # 最后验证文件是否真的可以打开
                try:
                    with open(case_path, 'r', encoding='utf-8') as f:
                        f.read(1)
                    return [case_path]
                except:
                    pass
        raise FileNotFoundError(f"用例文件 {dir_target} 没有找到，搜索路径: {file_path}")

    # 处理目录路径（多文件模式）
    if os.path.isdir(abs_path):
        case_file_path = abs_path
    else:
        case_file_path = ""
        for root, dirs, files in os.walk(file_path):
            for file in dirs:
                if file == dir_target:
                    case_file_path = os.path.join(root, file)

    if case_file_path:
        LOG.info(f"当前用例执行路径：{case_file_path}")
    else:
        raise FileNotFoundError("用例执行路径未找到")

    # 遍历目录下所有用例文件
    file_path_list = []
    for parent, dirnames, filenames in os.walk(case_file_path, followlinks=True):
        for filename in filenames:
            file_path = os.path.join(parent, filename)
            # 根据文件后缀筛选用例文件
            ext = filename.split(".")[1].lower()
            if ext in ["json", "json5", "yaml", "xlsx", "xls"]:
                file_path_list.append(file_path)
            elif ext == "py":
                # 导入Python关键字文件（可选，如果keywords模块不存在则跳过）
                try:
                    from core.rule_engine import import_keywords
                    sys.path.insert(1, parent)
                    import_keywords(filename.split(".")[0])
                except (ImportError, ModuleNotFoundError):
                    # 如果keywords模块不存在，跳过导入
                    LOG.debug(f"跳过关键字文件导入: {filename}")
            else:
                LOG.warning(f"文件: {filename} 非json、yaml格式用例文件")
    if file_path_list:
        return file_path_list
    else:
        raise FileNotFoundError(f"文件夹 {case_file_path} 内不存在用例文件")


# 获取当前线程的图片存储路径
def images_file_path():
    threading_id = threading.currentThread().ident
    return os.path.join(IMAGES_PATH, f"{threading_id}")


# 获取当前线程的临时文件路径
def temporary_file_path(file_name):
    threading_id = threading.currentThread().ident
    return os.path.join(TEMPORARY_PATH, f"{threading_id}-{file_name}")


# 获取模板文件路径
def template_file_path(file_name):
    path = get_file_path(os.path.join(BASE_DIR, "files", "template"), file_name)
    return path


# 清理所有临时文件
def clear_temporary():
    for file in os.listdir(TEMPORARY_PATH):
        file_path = os.path.join(TEMPORARY_PATH, file)
        os.remove(file_path)


# 清理当前线程的临时文件
def clear_threading_files():
    threading_id = str(threading.currentThread().ident)
    for file in os.listdir(TEMPORARY_PATH):
        file_path = os.path.join(TEMPORARY_PATH, file)
        if threading_id in file_path:
            os.remove(file_path)


# 解析Excel格式的API测试用例
def excel_api_case(case_path, running_priority):
    file = open_workbook(case_path)
    sheet_names = file.sheet_names()
    # 排除说明性Sheet
    if "用例说明" in sheet_names:
        sheet_names.remove("用例说明")
    if "common_step" in sheet_names:
        sheet_names.remove("common_step")
    excel_case_list = list()

    # 遍历每个Sheet页
    for sheet_name in sheet_names:
        sheet = file.sheet_by_name(sheet_name)
        row_count = sheet.nrows
        for i in range(row_count):
            # 跳过表头行
            if sheet.row_values(i)[0] != "case_code":
                dict_case = dict(zip(sheet.row_values(0), sheet.row_values(i)))
                # 处理优先级过滤
                priority = dict_case["priority"]
                if isinstance(priority, float):
                    priority = str(int(priority))
                if running_priority is not None:
                    if priority.strip() and int(priority) > int(running_priority):
                        continue
                # 处理主机地址默认值
                if not dict_case["host"].strip():
                    dict_case["host"] = "$get_host(gateway)"
                # 构建用例基本信息
                case_content = {}
                steps_dic = {}
                step_list = []
                steps_dic["step_name"] = dict_case["case_name"]
                keys_list = list(dict_case.keys())
                for n in range(3):
                    case_content[keys_list[n]] = dict_case.pop(keys_list[n])
                # 处理公共步骤
                if "common_step" in dict_case and dict_case["common_step"].strip():
                    common_step_list = dict_case["common_step"].strip().split(";")
                    for step in common_step_list:
                        step_list.append({"common_step": step})
                    dict_case.pop("common_step")
                # 构建请求步骤
                steps_dic["request"] = dict_case
                # 处理参数提取
                if "extract" in dict_case and dict_case["extract"].strip():
                    extract_list = dict_case["extract"].strip().split(";")
                    steps_dic["extract"] = [{"extract": item} for item in extract_list]
                    dict_case.pop("extract")
                # 处理结果验证
                if "validation" in dict_case and dict_case["validation"].strip():
                    validation_list = dict_case["validation"].strip().split(";")
                    steps_dic["validation"] = [{"validation": item} for item in validation_list]
                    dict_case.pop("validation")
                # 处理循环参数
                if "cycles" in dict_case:
                    steps_dic["cycles"] = dict_case.pop("cycles")
                step_list.append(steps_dic)
                case_content["steps"] = step_list
                # 合并公共步骤
                excel_case = excel_api_comm_case(file, case_content)
                excel_case_list.append(excel_case)

    return excel_case_list


# 合并Excel用例中的公共步骤
def excel_api_comm_case(file, case_content):
    steps = case_content.get("steps", [])
    case_step = steps.pop()
    case_step_list = []
    # 读取common_step Sheet
    sheet = file.sheet_by_name("common_step")
    sheet_case_name_list = sheet.col_values(1)
    # 处理每个公共步骤
    for common_step in steps:
        step_name = common_step["common_step"].strip()
        if step_name:
            common_step_row = sheet_case_name_list.index(step_name)
            dict_case = dict(zip(sheet.row_values(0), sheet.row_values(common_step_row)))
            if not dict_case["host"].strip():
                dict_case["host"] = "$get_host(gateway)"
            common_case_content = {}
            steps_dic = {}
            steps_dic["step_name"] = dict_case["case_name"]
            keys_list = list(dict_case.keys())
            for n in range(3):
                common_case_content[keys_list[n]] = dict_case.pop(keys_list[n])
            # 递归处理嵌套公共步骤
            if "common_step" in dict_case and dict_case["common_step"].strip():
                common_step_list = dict_case["common_step"].strip().split(";")
                for step in common_step_list:
                    common_step_dic = {"common_step": step}
            steps_dic["request"] = dict_case
            # 处理提取参数
            if "extract" in dict_case and dict_case["extract"].strip():
                extract_list = dict_case["extract"].strip().split(";")
                steps_dic["extract"] = [{"extract": item} for item in extract_list]
                dict_case.pop("extract")
            # 处理验证参数
            if "validation" in dict_case and dict_case["validation"].strip():
                validation_list = dict_case["validation"].strip().split(";")
                steps_dic["validation"] = [{"validation": item} for item in validation_list]
                dict_case.pop("validation")
            case_step_list.append(steps_dic)
    case_step_list.append(case_step)
    case_content["steps"] = case_step_list
    return case_content


# 主函数（测试用）
if __name__ == "__main__":
    case_path = 'F:\\Simple\\武汉测试\\al_test\\autotest_elegant\\cases_api\\test_cases\\case_excel\\西普云课.xlsx'
    # excel_get_case_variables(case_path)
