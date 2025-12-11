"""
AI_TEST 主程序
统一入口，支持Web/API/Mobile测试执行
"""
# 标准库导入
import argparse
import os
import subprocess
import sys
import logging
from pathlib import Path

# 第三方库导入

# 本地导入
from core.test_executor import APITestExecutor

# 配置日志
logging.basicConfig(level=logging.INFO)
LOG = logging.getLogger(__name__)


def generate_html_report(test_results: dict, test_path: str, test_type: str, generate_report: bool = True, keep_reports: bool = False, generate_allure: bool = False):
    """
    生成HTML测试报告（增强版，支持Allure报告）
    
    Args:
        test_results: 测试结果数据
        test_path: 测试文件路径
        test_type: 测试类型 (web/api/mobile)
        generate_report: 是否生成HTML报告
        keep_reports: 是否保存所有报告
        generate_allure: 是否生成Allure报告
    """
    # 生成HTML报告
    if generate_report:
        try:
            from core.html_report_generator import HTMLReportGenerator
            # 根据测试类型生成不同目录的报告
            report_dir = f"reports/{test_type}"
            report_generator = HTMLReportGenerator(output_dir=report_dir, keep_all_reports=keep_reports)
            report_path = report_generator.generate_report(test_results, test_path, is_batch=False)
            
            print(f"\n{'='*60}")
            print(f"[OK] HTML测试报告已生成: {report_path}")
            if keep_reports:
                print(f"[INFO] 报告保存模式: 保存所有历史报告")
            else:
                print(f"[INFO] 报告保存模式: 覆盖同名报告")
        except Exception as e:
            LOG.warning(f"HTML报告生成失败: {e}")
            import traceback
            traceback.print_exc()
    
    # 生成Allure报告
    if generate_allure:
        try:
            from core.allure_report_generator import AllureReportGenerator
            # 根据测试类型生成不同目录的Allure报告
            allure_dir = f"reports/allure/{test_type}"
            allure_generator = AllureReportGenerator(output_dir=allure_dir)
            allure_path = allure_generator.generate_report(test_results, test_path, is_batch=False)
            
            if allure_path:
                print(f"[OK] Allure报告数据已生成: {allure_path}")
                print(f"[INFO] 使用命令 'allure generate {allure_path} -o {allure_path}/html' 生成HTML报告")
                print(f"[INFO] 使用命令 'allure serve {allure_path}' 查看实时报告")
        except Exception as e:
            LOG.warning(f"Allure报告生成失败: {e}")
            import traceback
            traceback.print_exc()
    
    if generate_report or generate_allure:
        print(f"{'='*60}\n")


def print_test_results(test_results: dict):
    """
    打印测试结果
    
    Args:
        test_results: 测试结果数据
    """
    print(f"\n{'='*60}")
    print(f"用例名称: {test_results['case_name']}")
    print(f"总步骤数: {test_results['total_steps']}")
    print(f"通过: {test_results['passed_steps']}")
    print(f"失败: {test_results['failed_steps']}")
    print(f"{'='*60}\n")
    
    # 打印详细结果
    for i, step_result in enumerate(test_results['step_results'], 1):
        status = "[PASS]" if step_result['passed'] else "[FAIL]"
        print(f"{i}. {step_result['step_name']}: {status}")
        
        if step_result['errors']:
            for error in step_result['errors']:
                print(f"   错误: {error}")


def detect_test_type(test_path: str) -> str:
    """
    检测测试类型
    
    Args:
        test_path: 测试文件路径
        
    Returns:
        str: 测试类型 (web/api/mobile)
        
    Raises:
        ValueError: 无法识别测试类型
    """
    path = Path(test_path)
    path_str = str(path).lower()
    
    # 优先根据路径中的关键字判断
    if "web" in path_str or path.suffix == ".py":
        return "web"
    elif "api" in path_str and path.suffix == ".yaml":
        return "api"
    elif "mobile" in path_str and path.suffix == ".yaml":
        return "mobile"
    # 其次根据文件扩展名判断
    elif path.suffix == ".py":
        return "web"
    elif path.suffix == ".yaml":
        return "api"
    else:
        raise ValueError(f"无法识别测试类型: {test_path}")


def run_web_test(test_path: str, generate_report: bool = True, keep_reports: bool = False, headless: bool = False, tags: list = None):
    """
    运行Web测试
    
    Args:
        test_path: 测试文件路径
        generate_report: 是否生成报告
        keep_reports: 是否保存所有报告
        headless: 是否以无头模式运行
        tags: 标签过滤
    """
    LOG.info(f"运行Web测试: {test_path}")
    if tags:
        LOG.info(f"标签过滤: {tags}")
    
    # 使用框架的执行器执行Web测试
    from core.case_execute import execute_main
    from libs.config_center import CASE_CONF
    
    # 获取Web UI测试的环境配置
    env = "test"  # 默认环境
    env_conf = CASE_CONF.get("web_ui", {})
    
    # 执行测试用例
    report_data = execute_main("web_ui", env, test_path, None, 1, 1, env_conf, tags=tags)
    
    # 将UiCaseInfo列表转换为报告数据格式
    if isinstance(report_data, list) and len(report_data) > 0:
        # 单个用例执行
        case_info = report_data[0]
        
        # 检查是否有步骤数据
        steps_list = getattr(case_info, 'steps_list', [])
        if not steps_list:
            # 如果steps_list为空，可能是执行失败或未执行步骤
            # 尝试从case_content或steps属性中获取步骤信息
            case_content = getattr(case_info, 'case_content', {})
            if not case_content:
                # 如果case_content为空，尝试从steps属性获取
                steps = getattr(case_info, 'steps', [])
            else:
                steps = case_content.get('steps', [])
            
            LOG.info(f"steps_list为空，从case_content/steps获取步骤信息，找到{len(steps)}个步骤")
            if steps:
                # 创建虚拟步骤结果（用于报告显示）
                virtual_steps = []
                case_result = getattr(case_info, 'result', 'Pass')
                error_info = getattr(case_info, 'error_info', getattr(case_info, 'message', '步骤执行失败'))
                
                for step in steps:
                    # 创建一个简单的对象来模拟StepInfo
                    class VirtualStepInfo:
                        def __init__(self, step_data, case_result, error_msg):
                            self.step_name = step_data.get('step_name', '未知步骤')
                            # 如果用例失败或有错误信息，所有步骤标记为失败
                            # 检查多种失败标识：result == 'Fail', status == '失败', 或者有error_info
                            if case_result == 'Fail' or error_msg or getattr(case_info, 'status', '') == '失败':
                                self.result = 'Fail'
                                self.message = error_msg if error_msg else '步骤执行失败'
                            else:
                                self.result = 'Idle'
                                self.message = '步骤未执行'
                            self.step_duration = 0.01
                            self.image_base64 = ''
                    
                    step_info = VirtualStepInfo(step, case_result, error_info)
                    virtual_steps.append(step_info)
                steps_list = virtual_steps
                LOG.info(f"创建了{len(steps_list)}个虚拟步骤用于报告显示")
        
        # 转换为报告数据格式
        # 计算通过和失败的步骤数
        passed_steps = sum(1 for step in steps_list if getattr(step, 'result', 'Idle') == "Pass")
        failed_steps = sum(1 for step in steps_list if getattr(step, 'result', 'Idle') == "Fail")
        
        # 如果所有步骤都是Idle，但有错误信息，则标记为失败
        if passed_steps == 0 and failed_steps == 0 and steps_list:
            # 检查是否有错误信息
            error_info = getattr(case_info, 'error_info', '')
            status = getattr(case_info, 'status', '')
            if error_info or status == '失败':
                # 将所有步骤标记为失败
                failed_steps = len(steps_list)
                for step in steps_list:
                    step.result = 'Fail'
                    if not hasattr(step, 'message') or not step.message:
                        step.message = error_info if error_info else '步骤执行失败'
        
        test_results = {
            'case_name': getattr(case_info, 'case_name', '未知用例'),
            'case_code': getattr(case_info, 'case_code', ''),
            'total_steps': len(steps_list),
            'passed_steps': passed_steps,
            'failed_steps': failed_steps,
            'passed': getattr(case_info, 'result', 'Fail') == "Pass" and failed_steps == 0,
            'step_results': []
        }
        
        # 转换步骤结果
        for step in steps_list:
            step_result = {
                'step_name': getattr(step, 'step_name', '未知步骤'),
                'passed': getattr(step, 'result', 'Idle') == "Pass",
                'duration': getattr(step, 'step_duration', 0.01),
                'errors': [] if getattr(step, 'result', 'Idle') == "Pass" else [getattr(step, 'message', '步骤执行失败')],
                'request': {},
                'response': {}
            }
            
            # 如果有截图，添加到响应中
            if hasattr(step, 'image_base64') and getattr(step, 'image_base64', ''):
                step_result['response']['screenshot'] = step.image_base64
            
            test_results['step_results'].append(step_result)
        
        # 打印结果
        print_test_results(test_results)
        
        # 生成HTML报告
        generate_html_report(test_results, test_path, "web", generate_report, keep_reports)
        
        if not test_results['passed']:
            sys.exit(1)
    else:
        LOG.error("未获取到测试结果")
        sys.exit(1)


def run_api_test(test_path: str, generate_report: bool = True, keep_reports: bool = False, enable_cleanup: bool = True, generate_allure: bool = False):
    """
    运行API测试
    
    Args:
        test_path: 测试文件路径
        generate_report: 是否生成报告
        keep_reports: 是否保存所有报告
        enable_cleanup: 是否启用数据清理
        generate_allure: 是否生成Allure报告
    """
    LOG.info(f"运行API测试: {test_path}")
    
    executor = APITestExecutor(enable_cleanup=enable_cleanup)
    result = executor.execute_case(test_path)
    
    # 打印结果
    print_test_results(result)
    
    # 生成HTML报告
    generate_html_report(result, test_path, "api", generate_report, keep_reports, generate_allure)
    
    if not result['passed']:
        sys.exit(1)


def run_mobile_test(test_path: str, generate_report: bool = True, keep_reports: bool = False):
    """
    运行Mobile测试（Maestro）
    
    Args:
        test_path: 测试文件路径
        generate_report: 是否生成报告
        keep_reports: 是否保存所有报告
    """
    LOG.info(f"运行Mobile测试: {test_path}")
    
    cmd = ["maestro", "test", test_path]
    result = subprocess.run(cmd, capture_output=True, text=True)
    print(result.stdout)
    
    if result.returncode != 0:
        print(result.stderr)
        sys.exit(result.returncode)


def main():
    """主函数"""
    parser = argparse.ArgumentParser(
        description="AI_TEST - AI驱动的全栈自动化测试框架",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 运行Web测试
  python main.py cases/web/examples/test_demo.py
  
  # 运行API测试
  python main.py cases/api/examples/user_query.yaml
  
  # 运行Mobile测试
  python main.py cases/mobile/examples/login.yaml
  
  # 指定测试类型
  python main.py --type api cases/api/users.yaml
  
  # 无头模式运行Web测试
  python main.py --headless cases/web/test_login.py
        """
    )
    
    parser.add_argument(
        "test_path",
        nargs="+",
        help="测试文件路径或目录（支持多个文件）"
    )
    
    parser.add_argument(
        "--type",
        choices=["web", "api", "mobile"],
        help="测试类型（不指定则自动检测）"
    )
    
    parser.add_argument(
        "--headless",
        action="store_true",
        help="Web测试无头模式"
    )
    
    parser.add_argument(
        "--env",
        default="test",
        help="运行环境（test/staging/prod）"
    )
    
    parser.add_argument(
        "--no-report",
        action="store_true",
        help="不生成HTML报告"
    )
    
    parser.add_argument(
        "--keep-reports",
        action="store_true",
        help="保存所有测试报告（默认覆盖同名报告）"
    )
    
    parser.add_argument(
        "--no-cleanup",
        action="store_true",
        help="禁用数据清理功能（默认启用）"
    )
    
    parser.add_argument(
        "--tags",
        nargs="+",
        help="按标签过滤测试用例（例如: --tags smoke critical）"
    )
    parser.add_argument(
        "--allure",
        action="store_true",
        help="生成Allure报告"
    )
    parser.add_argument(
        "--security-scan",
        action="store_true",
        help="执行完功能测试后触发 Strix 安全扫描"
    )
    parser.add_argument(
        "--security-only",
        action="store_true",
        help="仅运行 Strix 安全扫描（跳过功能测试）"
    )
    parser.add_argument(
        "--security-target",
        dest="security_targets",
        nargs="+",
        help="Strix 安全扫描目标（URL 或源码目录），可多选"
    )
    parser.add_argument(
        "--security-instruction",
        help="传递给 Strix 的附加指令，例如聚焦特定漏洞类型"
    )
    parser.add_argument(
        "--security-timeout",
        type=int,
        help="Strix 扫描超时时间（秒）"
    )
    parser.add_argument(
        "--parallel",
        action="store_true",
        help="API测试并行执行"
    )
    
    args = parser.parse_args()
    
    # 规范化路径（支持多个测试文件和目录，处理中文文件名）
    import os
    from pathlib import Path
    
    # 获取所有测试文件路径
    test_paths = []
    for path in args.test_path:
        abs_path = os.path.abspath(path)
        if os.path.isdir(abs_path):
            # 如果是目录，递归查找所有测试文件
            for root, dirs, files in os.walk(abs_path):
                for file in files:
                    if file.endswith(".yaml") or file.endswith(".py"):
                        test_file = os.path.join(root, file)
                        test_paths.append(test_file)
        elif os.path.isfile(abs_path):
            # 如果是文件，直接添加
            test_paths.append(abs_path)
        else:
            LOG.warning(f"路径不存在或无法访问: {path}")
    
    if not test_paths:
        LOG.error("未找到任何测试文件")
        sys.exit(1)
    
    LOG.info(f"找到 {len(test_paths)} 个测试文件")
    
    # 检测或使用指定的测试类型
    test_type = args.type or detect_test_type(test_paths[0])
    
    LOG.info(f"测试类型: {test_type}")
    LOG.info(f"运行环境: {args.env}")

    # 如果只运行安全扫描，则跳过功能测试
    if args.security_only:
        from core.strix_manager import StrixManager

        strix_manager = StrixManager()
        combined_targets = strix_manager.extend_targets_with_case(
            args.security_targets,
            test_type,
            test_paths[0],
        )
        scan_result = strix_manager.execute(
            targets=combined_targets,
            instructions=args.security_instruction,
            force=True,
            timeout=args.security_timeout,
            security_only=True,
        )
        if scan_result.report_path:
            LOG.info(f"Strix 报告位置: {scan_result.report_path}")
        if scan_result.issues:
            LOG.warning(f"Strix 发现 {len(scan_result.issues)} 个潜在安全问题")
        if not scan_result.success:
            LOG.error(f"Strix 扫描失败: {scan_result.message}")
            sys.exit(1)
        LOG.info("Strix 安全扫描完成")
        sys.exit(0)
    
    # 执行测试
    try:
        if test_type == "api" and args.parallel:
            # 并行执行API测试
            LOG.info("并行执行API测试")
            from concurrent.futures import ThreadPoolExecutor, as_completed
            
            def execute_single_api_test(test_path):
                """执行单个API测试"""
                executor = APITestExecutor(enable_cleanup=not args.no_cleanup)
                return executor.execute_case(test_path)
            
            # 收集所有测试结果
            all_results = []
            
            # 使用线程池并行执行
            with ThreadPoolExecutor() as executor:
                # 提交所有测试任务
                future_to_test = {executor.submit(execute_single_api_test, test_path): test_path for test_path in test_paths}
                
                # 处理完成的任务
                for future in as_completed(future_to_test):
                    test_path = future_to_test[future]
                    try:
                        result = future.result()
                        all_results.append((test_path, result))
                        print_test_results(result)
                        generate_html_report(result, test_path, "api", generate_report=not args.no_report, keep_reports=args.keep_reports, generate_allure=args.allure)
                    except Exception as e:
                        LOG.error(f"测试执行失败: {test_path} - {e}")
            
            # 检查是否所有测试都通过
            all_passed = all(result[1]['passed'] for result in all_results)
            if not all_passed:
                sys.exit(1)
        else:
            # 串行执行测试
            all_passed = True
            for test_path in test_paths:
                try:
                    if test_type == "web":
                        run_web_test(
                            test_path,
                            generate_report=not args.no_report,
                            keep_reports=args.keep_reports,
                            headless=args.headless,
                            tags=args.tags
                        )
                    elif test_type == "api":
                        run_api_test(
                            test_path, 
                            generate_report=not args.no_report,
                            keep_reports=args.keep_reports,
                            enable_cleanup=not args.no_cleanup,
                            generate_allure=args.allure
                        )
                    elif test_type == "mobile":
                        run_mobile_test(
                            test_path,
                            generate_report=not args.no_report,
                            keep_reports=args.keep_reports
                        )
                except Exception as e:
                    LOG.error(f"测试执行失败: {test_path} - {e}")
                    all_passed = False
            
            if not all_passed:
                sys.exit(1)
    except Exception as e:
        LOG.error(f"测试执行失败: {e}")
        sys.exit(1)

    if args.security_scan:
        from core.strix_manager import StrixManager

        strix_manager = StrixManager()
        combined_targets = strix_manager.extend_targets_with_case(
            args.security_targets,
            test_type,
            test_paths[0],  # 只传递第一个文件用于分析
        )
        scan_result = strix_manager.execute(
            targets=combined_targets,
            instructions=args.security_instruction,
            force=True,
            timeout=args.security_timeout,
        )
        if scan_result.executed:
            LOG.info(f"Strix 扫描结果: {scan_result.message}")
            if scan_result.report_path:
                LOG.info(f"Strix 报告位置: {scan_result.report_path}")
            if scan_result.issues:
                LOG.warning(f"Strix 发现 {len(scan_result.issues)} 个潜在安全问题")
        else:
            LOG.info(scan_result.message)


if __name__ == "__main__":
    main()

