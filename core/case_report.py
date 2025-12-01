# 导入os模块用于文件路径操作
import os
# 导入jinja2模板引擎相关类，用于生成HTML报告
from jinja2 import Environment, FileSystemLoader
# 从配置中心导入日志对象和项目根目录常量
from libs.config_center import LOG, BASE_DIR


# 测试报告生成类
class TestReport:

    def __init__(self, case_type, project_name, report_env, report_exe_user):
        self.case_type = case_type  # 用例类型（如API、UI等）
        self.project_name = project_name  # 项目名称
        self.report_env = report_env  # 测试环境名
        self.report_exe_user = report_exe_user  # 测试人员
        self.report_path = ""  # 报告名称与路径
        self.report_start_time = ""  # 起始时间
        self.report_end_time = ""  # 结束时间 eg:2019-06-10 12:10:03.432
        self.report_test_result = "Pass"  # 存在任何case失败，则测试结果设置为Fail
        self.report_exe_counts = 0  # 总用例数
        self.report_exe_idle = 0  # 未执行用例数
        self.report_exe_pass = 0  # 通过用例数
        self.report_exe_fail = 0  # 失败用例数
        self.report_test_rate = "0.0%"  # 通过率
        self.report_result_color = "#999999"  # 结果颜色
        self.case_fail = []  # 失败用例列表
        self.case_all = []  # 所有用例列表

    @staticmethod
    def obtain_color(test_result):
        # 根据测试结果返回对应的颜色代码
        if test_result == "Pass":
            color = "#009900"  # 通过-绿色
        elif test_result == "Fail":
            color = "#ff0000"  # 失败-红色
        else:
            color = "#999999"  # 其他-灰色
        return color

    def report_gen(self, case_info_list):
        # 将用例信息转换为字典并添加到所有用例列表
        for i in case_info_list:
            self.case_all.append(i.__dict__)
        # 计算总用例数
        self.report_exe_counts = len(self.case_all)
        # 筛选失败用例并更新测试结果
        for case_info in self.case_all:
            if case_info.get("result") == "Fail":
                self.case_fail.append(case_info)
                self.report_test_result = "Fail"
        # 计算失败和通过用例数
        self.report_exe_fail = len(self.case_fail)
        self.report_exe_pass = self.report_exe_counts - self.report_exe_fail
        # 设置结果颜色
        self.report_result_color = self.obtain_color(self.report_test_result)
        # 计算通过率
        if self.report_exe_counts != 0:
            self.report_test_rate = "%.1f%%" % (self.report_exe_pass / self.report_exe_counts * 100)

        # 处理用例数据，准备生成报告
        for case in self.case_all:
            # 移除不需要在报告中显示的字段
            case.pop("client", None)
            case.pop("step_code", None)
            case.pop("step_info", None)
            case.pop("case_content", None)
            case.pop("case_variables", None)
            case.pop("common_case_variables", None)

            # 处理用例执行时间（毫秒转秒并保留两位小数）
            case_duration = case.get("case_duration", 0.0)
            # case["case_duration"] = float('%.2f' % (case_duration / 1000))

            # 处理用例链接，添加HTML超链接
            case_link = case.get("case_link")
            if case_link:
                case["case_name"] = f'<a href="{case_link}" target="_blank">{case.get("case_name")}</a>'
            # 设置用例结果颜色
            case["result_color"] = self.obtain_color(case.get("result", "Idle"))

            # 处理步骤信息
            steps_list = case.get("steps_list", [])
            steps_info_list = []
            for i, step in enumerate(steps_list):
                step = step.__dict__
                # 移除步骤中不需要显示的字段
                step.pop("client", None)
                step.pop("step_content", None)
                step.pop("step_variables", None)
                step.pop("retry_path_list", None)
                # 设置步骤结果颜色
                step["result_color"] = self.obtain_color(step.get("result", "Idle"))
                # 设置步骤编号
                step_code = step.get("step_code")
                if not step_code:
                    step["step_code"] = str(i+1)

                # 处理断言信息
                validate_info_list = step.get("validate_info_list", [])
                for validate in validate_info_list:
                    validate["result_color"] = self.obtain_color(validate.get("assert_result", "Idle"))

                steps_info_list.append(step)
            # 将处理后的步骤信息添加到用例数据
            case.pop("steps_info_dict", None)
            case["steps_info_list"] = steps_info_list

        # 生成HTML报告
        self.generate_html()

    def generate_html(self):
        # 配置Jinja2模板环境，加载模板文件
        env = Environment(loader=FileSystemLoader(os.path.join(BASE_DIR, "files", "template")))
        # 获取对应类型的报告模板
        template = env.get_template(f'{self.case_type}_report_template.html')

        # 创建报告目录（如果不存在）
        report_path = os.path.join(BASE_DIR, 'report')
        if not os.path.exists(report_path):
            os.mkdir(report_path)
        # 设置报告文件路径
        report_file_path = os.path.join(report_path, f'{self.case_type}_test_report.html')

        # 渲染模板并写入HTML文件
        with open(report_file_path, 'w', encoding='utf-8') as f:
            html_content = template.render(**self.__dict__)
            f.write(html_content)  # 写入模板 生成html
        # 记录日志：报告生成完成
        LOG.info("生成报告完成")


# 主函数：测试报告生成功能（仅用于调试）
if __name__ == "__main__":
    # 示例API报告数据
    pass  # 添加缩进的空语句块或实际执行代码
    