"""
Allure报告生成器
基于HttpRunner框架的Allure报告功能
支持Allure 2.0+ 版本
"""
import json
import os
from datetime import datetime
from pathlib import Path
import logging
from typing import Dict, List, Any

LOG = logging.getLogger(__name__)


def ensure_allure_installed():
    """
    检查并确保Allure相关库已安装
    如果未安装，给出提示信息
    """
    try:
        import allure
        return True
    except ImportError:
        LOG.warning("Allure库未安装，Allure报告功能不可用")
        LOG.warning("请使用 pip install allure-pytest 安装Allure库")
        return False


class AllureReportGenerator:
    """
    Allure报告生成器
    生成Allure 2.0+ 兼容的JSON格式报告数据
    """
    
    def __init__(self, output_dir: str = "reports/allure"):
        """
        初始化Allure报告生成器
        
        :param output_dir: Allure报告输出目录
        """
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
        # 检查Allure库是否安装
        self.allure_available = ensure_allure_installed()
    
    def generate_report(self, test_results: Dict[str, Any], case_path: str = "", is_batch: bool = False) -> str:
        """
        生成Allure报告
        
        :param test_results: 测试结果字典
        :param case_path: 测试用例路径
        :param is_batch: 是否为批量执行
        :return: 生成的报告目录路径
        """
        if not self.allure_available:
            LOG.warning("Allure报告生成失败：Allure库未安装")
            return ""
        
        try:
            # 生成Allure测试结果JSON文件
            self._generate_allure_json(test_results, case_path, is_batch)
            
            LOG.info(f"Allure报告数据已生成: {self.output_dir}")
            LOG.info(f"使用命令 'allure generate {self.output_dir} -o {self.output_dir}/html' 生成HTML报告")
            LOG.info(f"使用命令 'allure serve {self.output_dir}' 查看实时报告")
            
            return str(self.output_dir)
        except Exception as e:
            LOG.error(f"生成Allure报告失败: {e}")
            return ""
    
    def _generate_allure_json(self, test_results: Dict[str, Any], case_path: str, is_batch: bool):
        """
        生成Allure测试结果JSON文件
        
        Allure报告文件格式：
        - 每个测试用例生成一个JSON文件
        - 文件名格式：uuid.json
        - 包含测试用例的所有信息
        """
        import uuid
        
        # 准备测试用例数据
        case_id = str(uuid.uuid4())
        case_name = test_results.get('case_name', 'Unknown Test')
        case_code = test_results.get('case_code', '')
        total_steps = test_results.get('total_steps', 0)
        passed_steps = test_results.get('passed_steps', 0)
        failed_steps = test_results.get('failed_steps', 0)
        step_results = test_results.get('step_results', [])
        
        passed = test_results.get('passed', False)
        status = 'passed' if passed else 'failed'
        duration = sum(max(step.get('duration', 0), 0.01) for step in step_results) * 1000  # 转换为毫秒
        
        # 获取开始和结束时间
        timestamp = datetime.now().timestamp() * 1000  # 转换为毫秒
        start_time = int(timestamp - duration)
        end_time = int(timestamp)
        
        # 生成Allure测试用例JSON
        allure_test_case = {
            "uuid": case_id,
            "name": case_name,
            "fullName": f"{case_code}.{case_name}" if case_code else case_name,
            "status": status,
            "statusDetails": {
                "message": "All tests passed" if passed else "Some tests failed",
                "trace": ""
            },
            "stage": "finished",
            "start": start_time,
            "stop": end_time,
            "duration": duration,
            "steps": self._generate_allure_steps(step_results),
            "attachments": [],
            "parameters": self._generate_allure_parameters(test_results, case_path),
            "labels": self._generate_allure_labels(test_results),
            "links": []
        }
        
        # 保存JSON文件
        json_file = self.output_dir / f"{case_id}-result.json"
        with open(json_file, 'w', encoding='utf-8') as f:
            json.dump(allure_test_case, f, ensure_ascii=False, indent=2)
    
    def _generate_allure_steps(self, step_results: List[Dict]) -> List[Dict]:
        """
        生成Allure测试步骤
        
        :param step_results: 步骤结果列表
        :return: Allure步骤列表
        """
        allure_steps = []
        
        for i, step in enumerate(step_results):
            step_uuid = str(uuid.uuid4())
            step_name = step.get('step_name', f'Step {i+1}')
            passed = step.get('passed', False)
            status = 'passed' if passed else 'failed'
            duration = max(step.get('duration', 0), 0.01) * 1000  # 转换为毫秒
            errors = step.get('errors', [])
            request = step.get('request', {})
            response = step.get('response', {})
            
            # 步骤开始和结束时间
            timestamp = datetime.now().timestamp() * 1000
            step_start = int(timestamp - duration)
            step_stop = int(timestamp)
            
            # 生成步骤信息
            allure_step = {
                "name": step_name,
                "status": status,
                "start": step_start,
                "stop": step_stop,
                "attachments": [],
                "parameters": [],
                "steps": []
            }
            
            # 添加错误信息
            if errors:
                allure_step["statusDetails"] = {
                    "message": "\n".join(errors),
                    "trace": "\n".join(errors)
                }
            
            # 添加请求和响应作为附件
            if request:
                # 添加请求信息作为附件
                request_content = json.dumps(request, ensure_ascii=False, indent=2)
                allure_step["attachments"].append({
                    "name": "Request",
                    "source": f"request_{step_uuid}.json",
                    "type": "application/json"
                })
                # 保存请求附件文件
                request_file = self.output_dir / f"request_{step_uuid}.json"
                with open(request_file, 'w', encoding='utf-8') as f:
                    f.write(request_content)
            
            if response:
                # 添加响应信息作为附件
                response_content = json.dumps(response, ensure_ascii=False, indent=2)
                allure_step["attachments"].append({
                    "name": "Response",
                    "source": f"response_{step_uuid}.json",
                    "type": "application/json"
                })
                # 保存响应附件文件
                response_file = self.output_dir / f"response_{step_uuid}.json"
                with open(response_file, 'w', encoding='utf-8') as f:
                    f.write(response_content)
            
            allure_steps.append(allure_step)
        
        return allure_steps
    
    def _generate_allure_parameters(self, test_results: Dict[str, Any], case_path: str) -> List[Dict]:
        """
        生成Allure测试参数
        
        :param test_results: 测试结果字典
        :param case_path: 测试用例路径
        :return: Allure参数列表
        """
        parameters = []
        
        # 添加基本参数
        parameters.append({
            "name": "case_path",
            "value": case_path
        })
        
        parameters.append({
            "name": "env",
            "value": test_results.get('env', 'default')
        })
        
        parameters.append({
            "name": "priority",
            "value": test_results.get('priority', 'medium')
        })
        
        return parameters
    
    def _generate_allure_labels(self, test_results: Dict[str, Any]) -> List[Dict]:
        """
        生成Allure测试标签
        
        :param test_results: 测试结果字典
        :return: Allure标签列表
        """
        labels = []
        
        # 添加基本标签
        labels.append({
            "name": "severity",
            "value": test_results.get('priority', 'medium')
        })
        
        labels.append({
            "name": "env",
            "value": test_results.get('env', 'default')
        })
        
        labels.append({
            "name": "framework",
            "value": "AI_TEST"
        })
        
        # 添加自定义标签
        tags = test_results.get('tags', [])
        for tag in tags:
            labels.append({
                "name": "tag",
                "value": tag
            })
        
        return labels
    
    def generate_html_report(self, output_dir: str = None):
        """
        生成HTML格式的Allure报告
        
        :param output_dir: HTML报告输出目录
        :return: HTML报告目录路径
        """
        import subprocess
        
        if not self.allure_available:
            LOG.warning("Allure报告生成失败：Allure库未安装")
            return ""
        
        try:
            # 检查allure命令是否可用
            subprocess.run(["allure", "--version"], check=True, capture_output=True, text=True)
            
            html_output_dir = Path(output_dir) if output_dir else self.output_dir / "html"
            html_output_dir.mkdir(parents=True, exist_ok=True)
            
            # 生成HTML报告
            cmd = ["allure", "generate", str(self.output_dir), "-o", str(html_output_dir), "--clean"]
            subprocess.run(cmd, check=True)
            
            LOG.info(f"Allure HTML报告已生成: {html_output_dir}")
            return str(html_output_dir)
        except subprocess.CalledProcessError as e:
            LOG.error(f"生成Allure HTML报告失败: {e}")
            return ""
        except FileNotFoundError:
            LOG.error("Allure命令未找到，请确保Allure已安装并添加到PATH环境变量")
            LOG.error("下载地址: https://github.com/allure-framework/allure2/releases")
            return ""
    
    def serve_report(self, port: int = 8080):
        """
        启动Allure报告服务器
        
        :param port: 服务器端口
        """
        import subprocess
        
        if not self.allure_available:
            LOG.warning("Allure报告服务器启动失败：Allure库未安装")
            return False
        
        try:
            # 检查allure命令是否可用
            subprocess.run(["allure", "--version"], check=True, capture_output=True, text=True)
            
            # 启动报告服务器
            cmd = ["allure", "serve", str(self.output_dir), "--port", str(port)]
            LOG.info(f"启动Allure报告服务器: http://localhost:{port}")
            LOG.info("按 Ctrl+C 停止服务器")
            
            subprocess.run(cmd, check=True)
            return True
        except subprocess.CalledProcessError as e:
            LOG.error(f"Allure报告服务器启动失败: {e}")
            return False
        except FileNotFoundError:
            LOG.error("Allure命令未找到，请确保Allure已安装并添加到PATH环境变量")
            LOG.error("下载地址: https://github.com/allure-framework/allure2/releases")
            return False
