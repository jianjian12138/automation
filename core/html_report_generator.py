"""
现代化的HTML测试报告生成器
使用Bootstrap 5和Chart.js创建美观专业的测试报告
"""
import json
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Any
import logging

LOG = logging.getLogger(__name__)


class HTMLReportGenerator:
    """HTML测试报告生成器"""
    
    def __init__(self, output_dir: str = "reports", keep_all_reports: bool = False):
        """
        初始化报告生成器
        
        :param output_dir: 报告输出目录
        :param keep_all_reports: 是否保存所有报告（True=保存所有，False=覆盖同名报告）
        """
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.keep_all_reports = keep_all_reports
    
    def generate_report(self, test_results: Dict[str, Any], case_path: str = "", is_batch: bool = False) -> str:
        """
        生成HTML测试报告
        
        :param test_results: 测试结果字典
        :param case_path: 测试用例路径
        :param is_batch: 是否为批量执行（批量执行时折叠详细信息）
        :return: 生成的报告文件路径
        """
        # 准备报告数据
        report_data = self._prepare_report_data(test_results, case_path)
        report_data['is_batch'] = is_batch
        
        # 生成HTML内容
        html_content = self._generate_html_content(report_data)
        
        # 保存报告文件
        # 优先使用case_code（通常是英文），如果没有则使用case_name，最后才使用文件路径
        case_code = test_results.get('case_code', '')
        case_name = test_results.get('case_name', '')
        
        # 生成文件名：优先使用case_code，如果没有则使用case_name，最后使用文件路径
        if case_code:
            file_base_name = case_code
        elif case_name:
            # 如果case_name包含中文，使用case_code或转换为拼音/英文
            # 这里先尝试使用case_name，如果包含非ASCII字符，则使用case_code或文件路径
            if all(ord(c) < 128 for c in case_name):
                file_base_name = case_name
            else:
                # 包含中文，使用case_code或文件路径
                if case_code:
                    file_base_name = case_code
                else:
                    # 使用文件路径的stem，但进行编码处理
                    file_base_name = Path(case_path).stem if case_path else "test"
        else:
            # 使用文件路径的stem
            file_base_name = Path(case_path).stem if case_path else "test"
        
        # 清理文件名中的非法字符（Windows文件名不允许的字符）
        import re
        file_base_name = re.sub(r'[<>:"/\\|?*]', '_', file_base_name)
        
        if self.keep_all_reports:
            # 保存所有报告（使用时间戳）
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            report_filename = f"{file_base_name}_report_{timestamp}.html"
        else:
            # 覆盖同名报告（使用固定文件名）
            report_filename = f"{file_base_name}_report.html"
        
        # 使用UTF-8编码保存文件名（Windows需要特殊处理）
        # 在Windows上，Path对象可能无法正确处理中文文件名，需要手动编码
        try:
            report_path = self.output_dir / report_filename
        except (UnicodeEncodeError, UnicodeDecodeError):
            # 如果出现编码错误，使用case_code或默认名称
            if case_code:
                file_base_name = case_code
            else:
                file_base_name = "test_report"
            
            if self.keep_all_reports:
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                report_filename = f"{file_base_name}_{timestamp}.html"
            else:
                report_filename = f"{file_base_name}.html"
            
            report_path = self.output_dir / report_filename
        
        # 确保目录存在
        report_path.parent.mkdir(parents=True, exist_ok=True)
        
        # 使用UTF-8编码写入文件内容
        with open(report_path, 'w', encoding='utf-8') as f:
            f.write(html_content)
        
        LOG.info(f"测试报告已生成: {report_path}")
        return str(report_path)
    
    def _prepare_report_data(self, test_results: Dict, case_path: str) -> Dict:
        """准备报告数据"""
        case_name = test_results.get('case_name', 'Unknown Test')
        case_code = test_results.get('case_code', '')
        total_steps = test_results.get('total_steps', 0)
        passed_steps = test_results.get('passed_steps', 0)
        failed_steps = test_results.get('failed_steps', 0)
        step_results = test_results.get('step_results', [])
        
        # 计算统计数据
        passed = test_results.get('passed', False)
        pass_rate = (passed_steps / total_steps * 100) if total_steps > 0 else 0
        
        # 计算执行时间（如果步骤中没有duration，则使用0.1秒作为默认值）
        execution_time = sum(max(step.get('duration', 0), 0.01) for step in step_results)
        
        return {
            'case_name': case_name,
            'case_code': case_code,
            'case_path': case_path,
            'total_steps': total_steps,
            'passed_steps': passed_steps,
            'failed_steps': failed_steps,
            'skipped_steps': 0,  # 暂时不支持
            'pass_rate': pass_rate,
            'execution_time': execution_time,
            'status': 'PASS' if passed else 'FAIL',
            'timestamp': datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            'step_results': step_results,
        }
    
    def _generate_html_content(self, data: Dict) -> str:
        """生成HTML内容"""
        # 生成图表数据
        chart_data = self._generate_chart_data(data)
        
        html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>测试报告 - {data['case_name']}</title>
    
    <!-- Bootstrap 5 CSS -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
    <!-- Bootstrap Icons -->
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.1/font/bootstrap-icons.css">
    <!-- Chart.js -->
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    
    <style>
        :root {{
            --primary-color: #0d6efd;
            --success-color: #198754;
            --danger-color: #dc3545;
            --warning-color: #ffc107;
            --info-color: #0dcaf0;
            --dark-color: #212529;
            --light-color: #f8f9fa;
        }}
        
        body {{
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Helvetica Neue', Arial, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px 0;
        }}
        
        .report-container {{
            max-width: 1400px;
            margin: 0 auto;
            background: white;
            border-radius: 20px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            overflow: hidden;
        }}
        
        .report-header {{
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 40px;
            text-align: center;
        }}
        
        .report-header h1 {{
            font-size: 2.5rem;
            font-weight: 700;
            margin-bottom: 10px;
        }}
        
        .report-header .subtitle {{
            font-size: 1.1rem;
            opacity: 0.9;
        }}
        
        .report-body {{
            padding: 40px;
        }}
        
        .stats-card {{
            background: white;
            border-radius: 15px;
            padding: 25px;
            box-shadow: 0 4px 15px rgba(0,0,0,0.1);
            transition: transform 0.3s, box-shadow 0.3s;
            border-left: 4px solid var(--primary-color);
            margin-bottom: 20px;
        }}
        
        .stats-card:hover {{
            transform: translateY(-5px);
            box-shadow: 0 8px 25px rgba(0,0,0,0.15);
        }}
        
        .stats-card.success {{
            border-left-color: var(--success-color);
        }}
        
        .stats-card.danger {{
            border-left-color: var(--danger-color);
        }}
        
        .stats-card.info {{
            border-left-color: var(--info-color);
        }}
        
        .stats-number {{
            font-size: 2.5rem;
            font-weight: 700;
            margin-bottom: 5px;
        }}
        
        .stats-label {{
            font-size: 0.9rem;
            color: #6c757d;
            text-transform: uppercase;
            letter-spacing: 1px;
        }}
        
        .status-badge {{
            display: inline-block;
            padding: 8px 20px;
            border-radius: 50px;
            font-weight: 600;
            font-size: 0.9rem;
            letter-spacing: 0.5px;
        }}
        
        .status-badge.pass {{
            background: #d1e7dd;
            color: #0f5132;
        }}
        
        .status-badge.fail {{
            background: #f8d7da;
            color: #842029;
        }}
        
        .step-card {{
            background: white;
            border-radius: 12px;
            padding: 20px;
            margin-bottom: 15px;
            border: 2px solid #e9ecef;
            transition: all 0.3s;
        }}
        
        .step-card:hover {{
            border-color: var(--primary-color);
            box-shadow: 0 4px 15px rgba(0,0,0,0.1);
        }}
        
        .step-card.passed {{
            border-left: 4px solid var(--success-color);
        }}
        
        .step-card.failed {{
            border-left: 4px solid var(--danger-color);
        }}
        
        .step-header {{
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 15px;
        }}
        
        .step-name {{
            font-size: 1.1rem;
            font-weight: 600;
            color: var(--dark-color);
        }}
        
        .step-errors {{
            margin-top: 15px;
            padding: 15px;
            background: #fff3cd;
            border-radius: 8px;
            border-left: 4px solid var(--warning-color);
        }}
        
        .error-item {{
            margin: 8px 0;
            padding: 8px 12px;
            background: white;
            border-radius: 6px;
            font-family: 'Courier New', monospace;
            font-size: 0.9rem;
            color: var(--danger-color);
        }}
        
        .chart-container {{
            background: white;
            border-radius: 15px;
            padding: 25px;
            box-shadow: 0 4px 15px rgba(0,0,0,0.1);
            margin-bottom: 30px;
        }}
        
        .info-section {{
            background: #f8f9fa;
            border-radius: 12px;
            padding: 20px;
            margin-bottom: 20px;
        }}
        
        .info-row {{
            display: flex;
            justify-content: space-between;
            padding: 10px 0;
            border-bottom: 1px solid #dee2e6;
        }}
        
        .info-row:last-child {{
            border-bottom: none;
        }}
        
        .info-label {{
            font-weight: 600;
            color: #6c757d;
        }}
        
        .info-value {{
            color: var(--dark-color);
        }}
        
        .protocol-badge {{
            display: inline-block;
            padding: 4px 10px;
            border-radius: 6px;
            font-size: 0.75rem;
            font-weight: 600;
            background: #e7f1ff;
            color: #0d6efd;
            margin-left: 10px;
        }}
        
        @media (max-width: 768px) {{
            .report-header h1 {{
                font-size: 1.8rem;
            }}
            
            .report-body {{
                padding: 20px;
            }}
            
            .stats-number {{
                font-size: 2rem;
            }}
        }}
    </style>
</head>
<body>
    <div class="report-container">
        <!-- 报告头部 -->
        <div class="report-header">
            <h1><i class="bi bi-clipboard-check"></i> 测试报告</h1>
            <div class="subtitle">{data['case_name']}</div>
        </div>
        
        <!-- 报告主体 -->
        <div class="report-body">
            <!-- 测试概要 -->
            <div class="row mb-4">
                <div class="col-md-3">
                    <div class="stats-card success">
                        <div class="stats-number text-success">{data['passed_steps']}</div>
                        <div class="stats-label">通过</div>
                    </div>
                </div>
                <div class="col-md-3">
                    <div class="stats-card danger">
                        <div class="stats-number text-danger">{data['failed_steps']}</div>
                        <div class="stats-label">失败</div>
                    </div>
                </div>
                <div class="col-md-3">
                    <div class="stats-card info">
                        <div class="stats-number text-info">{data['total_steps']}</div>
                        <div class="stats-label">总计</div>
                    </div>
                </div>
                <div class="col-md-3">
                    <div class="stats-card">
                        <div class="stats-number" style="color: #667eea;">{data['pass_rate']:.1f}%</div>
                        <div class="stats-label">通过率</div>
                    </div>
                </div>
            </div>
            
            <!-- 状态徽章 -->
            <div class="text-center mb-4">
                <span class="status-badge {'pass' if data['status'] == 'PASS' else 'fail'}">
                    <i class="bi {'bi-check-circle' if data['status'] == 'PASS' else 'bi-x-circle'}"></i>
                    {data['status']}
                </span>
            </div>
            
            <!-- 图表 -->
            <div class="row mb-4">
                <div class="col-md-6">
                    <div class="chart-container">
                        <h5 class="mb-3"><i class="bi bi-pie-chart"></i> 测试结果分布</h5>
                        <canvas id="resultChart"></canvas>
                    </div>
                </div>
                <div class="col-md-6">
                    <div class="chart-container">
                        <h5 class="mb-3"><i class="bi bi-bar-chart"></i> 执行时间统计</h5>
                        <canvas id="timeChart"></canvas>
                    </div>
                </div>
            </div>
            
            <!-- 测试信息 -->
            <div class="info-section">
                <h5 class="mb-3"><i class="bi bi-info-circle"></i> 测试信息</h5>
                <div class="info-row">
                    <span class="info-label">用例名称</span>
                    <span class="info-value">{data['case_name']}</span>
                </div>
                {f'<div class="info-row"><span class="info-label">用例编码</span><span class="info-value">{data["case_code"]}</span></div>' if data['case_code'] else ''}
                {f'<div class="info-row"><span class="info-label">用例路径</span><span class="info-value">{data["case_path"]}</span></div>' if data['case_path'] else ''}
                <div class="info-row">
                    <span class="info-label">执行时间</span>
                    <span class="info-value">{data['timestamp']}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">总耗时</span>
                    <span class="info-value">{data['execution_time']:.2f} 秒</span>
                </div>
            </div>
            
            <!-- 测试步骤详情 -->
            <div class="mt-4">
                <h4 class="mb-3"><i class="bi bi-list-check"></i> 测试步骤详情</h4>
                {self._generate_steps_html(data['step_results'], data.get('is_batch', False))}
            </div>
        </div>
    </div>
    
    <!-- Bootstrap 5 JS -->
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
    
    <!-- 图表初始化 -->
    <script>
        {chart_data}
        
        // 结果分布饼图
        const resultCtx = document.getElementById('resultChart').getContext('2d');
        new Chart(resultCtx, {{
            type: 'doughnut',
            data: {{
                labels: ['通过', '失败'],
                datasets: [{{
                    data: [{data['passed_steps']}, {data['failed_steps']}],
                    backgroundColor: ['#198754', '#dc3545'],
                    borderWidth: 0
                }}]
            }},
            options: {{
                responsive: true,
                maintainAspectRatio: true,
                plugins: {{
                    legend: {{
                        position: 'bottom'
                    }}
                }}
            }}
        }});
        
        // 执行时间柱状图
        const timeCtx = document.getElementById('timeChart').getContext('2d');
        const stepNames = {json.dumps([step.get('step_name', f'步骤{i+1}') for i, step in enumerate(data['step_results'])], ensure_ascii=False)};
        const stepDurations = {json.dumps([max(step.get('duration', 0), 0.01) for step in data['step_results']])};
        
        new Chart(timeCtx, {{
            type: 'bar',
            data: {{
                labels: stepNames,
                datasets: [{{
                    label: '执行时间 (秒)',
                    data: stepDurations,
                    backgroundColor: '#0d6efd',
                    borderRadius: 8
                }}]
            }},
            options: {{
                responsive: true,
                maintainAspectRatio: true,
                plugins: {{
                    legend: {{
                        display: false
                    }}
                }},
                scales: {{
                    y: {{
                        beginAtZero: true
                    }}
                }}
            }}
        }});
    </script>
</body>
</html>"""
        return html
    
    def _generate_chart_data(self, data: Dict) -> str:
        """生成图表数据（预留，当前直接在模板中生成）"""
        return ""
    
    def _generate_steps_html(self, step_results: List[Dict], is_batch: bool = False) -> str:
        """生成测试步骤HTML"""
        html_parts = []
        
        # 如果是批量执行，默认折叠详细信息
        default_expanded = not is_batch
        
        for i, step in enumerate(step_results, 1):
            step_name = step.get('step_name', f'步骤 {i}')
            passed = step.get('passed', False)
            errors = step.get('errors', [])
            duration = max(step.get('duration', 0), 0.01)
            request = step.get('request', {})
            response = step.get('response', {})
            protocol = response.get('metadata', {}).get('protocol', 'http')
            
            status_class = 'passed' if passed else 'failed'
            status_icon = 'bi-check-circle-fill text-success' if passed else 'bi-x-circle-fill text-danger'
            status_text = '通过' if passed else '失败'
            
            # 协议徽章
            protocol_badge = f'<span class="protocol-badge">{protocol.upper()}</span>' if protocol != 'http' else ''
            
            step_html = f"""
                <div class="step-card {status_class}">
                    <div class="step-header">
                        <div>
                            <span class="step-name">
                                <i class="bi {status_icon}"></i>
                                {step_name}
                                {protocol_badge}
                            </span>
                        </div>
                        <div>
                            <span class="status-badge {'pass' if passed else 'fail'}">
                                {status_text}
                            </span>
                            <small class="text-muted ms-2">{duration:.2f}s</small>
                        </div>
                    </div>
            """
            
            # 如果有错误，显示错误信息
            if errors:
                errors_html = '<div class="step-errors"><strong><i class="bi bi-exclamation-triangle"></i> 错误信息：</strong>'
                for error in errors:
                    errors_html += f'<div class="error-item">{error}</div>'
                errors_html += '</div>'
                step_html += errors_html
            
            # 检查是否有截图
            screenshot = response.get('screenshot') if isinstance(response, dict) else None
            
            # 如果有截图，显示截图
            if screenshot:
                step_html += self._generate_screenshot_html(screenshot, i)
            
            # 请求和响应详细信息
            has_details = request or response
            if has_details:
                # 如果是批量执行，默认折叠；单个用例，默认展开
                collapse_class = '' if default_expanded else 'collapse'
                aria_expanded = 'true' if default_expanded else 'false'
                chevron_icon = 'bi-chevron-up' if default_expanded else 'bi-chevron-down'
                
                step_html += f"""
                    <div class="mt-3">
                        <button class="btn btn-sm btn-outline-primary" type="button" data-bs-toggle="collapse" 
                                data-bs-target="#details-{i}" aria-expanded="{aria_expanded}">
                            <i class="bi {chevron_icon}"></i> 查看详细信息
                        </button>
                        <div class="{collapse_class} mt-3" id="details-{i}">
                            <div class="card card-body bg-light">
                                {self._generate_request_html(request, i) if request else ''}
                                {self._generate_response_html(response, i) if response else ''}
                            </div>
                        </div>
                    </div>
                """
            
            step_html += '</div>'
            html_parts.append(step_html)
        
        return '\n'.join(html_parts)
    
    def _generate_screenshot_html(self, screenshot: str, step_index: int) -> str:
        """生成截图HTML"""
        if not screenshot:
            return ''
        
        return f'''
        <div class="screenshot-section" style="margin-top: 15px;">
            <h6 style="margin-bottom: 10px; color: #6c757d;">
                <i class="bi bi-image"></i> 失败截图
            </h6>
            <div class="screenshot-container" style="text-align: center; padding: 10px; background: #f8f9fa; border-radius: 8px;">
                <img src="data:image/png;base64,{screenshot}" 
                     alt="失败截图" 
                     style="max-width: 100%; height: auto; border-radius: 6px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); cursor: pointer;"
                     onclick="window.open(this.src, '_blank')"
                     title="点击查看大图">
            </div>
        </div>
        '''
    
    def _generate_request_html(self, request: Dict, step_index: int) -> str:
        """生成请求信息HTML"""
        method = request.get('method', 'GET')
        url = request.get('url', '')
        host = request.get('host', '')
        path = request.get('path', '')
        headers = request.get('headers', {})
        params = request.get('params', {})
        data = request.get('data', {})
        
        import json
        headers_json = json.dumps(headers, ensure_ascii=False, indent=2) if headers else '{}'
        params_json = json.dumps(params, ensure_ascii=False, indent=2) if params else '{}'
        data_json = json.dumps(data, ensure_ascii=False, indent=2) if data else '{}'
        
        return f"""
            <div class="mb-4">
                <h6 class="text-primary"><i class="bi bi-arrow-up-circle"></i> 请求信息</h6>
                <hr>
                <div class="mb-2">
                    <strong>方法:</strong> <span class="badge bg-primary">{method}</span>
                </div>
                {f'<div class="mb-2"><strong>完整URL:</strong> <code>{url}</code></div>' if url else ''}
                {f'<div class="mb-2"><strong>Host:</strong> <code>{host}</code></div>' if host else ''}
                {f'<div class="mb-2"><strong>Path:</strong> <code>{path}</code></div>' if path else ''}
                {f'<div class="mb-2"><strong>Query参数:</strong><pre class="bg-white p-2 rounded"><code>{params_json}</code></pre></div>' if params else ''}
                {f'<div class="mb-2"><strong>Headers:</strong><pre class="bg-white p-2 rounded"><code>{headers_json}</code></pre></div>' if headers else ''}
                {f'<div class="mb-2"><strong>请求体:</strong><pre class="bg-white p-2 rounded"><code>{data_json}</code></pre></div>' if data else ''}
            </div>
        """
    
    def _generate_response_html(self, response: Dict, step_index: int) -> str:
        """生成响应信息HTML"""
        status_code = response.get('status_code', 'N/A')
        headers = response.get('headers', {})
        body = response.get('body', '')
        response_json = response.get('json')
        metadata = response.get('metadata', {})
        
        import json
        
        # 格式化响应体
        if response_json:
            body_formatted = json.dumps(response_json, ensure_ascii=False, indent=2)
        elif body:
            # 尝试解析JSON
            try:
                body_parsed = json.loads(body)
                body_formatted = json.dumps(body_parsed, ensure_ascii=False, indent=2)
            except:
                body_formatted = body[:2000] + ('...' if len(body) > 2000 else '')
        else:
            body_formatted = '(空响应)'
        
        headers_json = json.dumps(headers, ensure_ascii=False, indent=2) if headers else '{}'
        
        # 状态码颜色
        if isinstance(status_code, int):
            if 200 <= status_code < 300:
                status_badge_class = 'bg-success'
            elif 400 <= status_code < 500:
                status_badge_class = 'bg-warning'
            elif status_code >= 500:
                status_badge_class = 'bg-danger'
            else:
                status_badge_class = 'bg-secondary'
        else:
            status_badge_class = 'bg-secondary'
        
        return f"""
            <div>
                <h6 class="text-success"><i class="bi bi-arrow-down-circle"></i> 响应信息</h6>
                <hr>
                <div class="mb-2">
                    <strong>状态码:</strong> <span class="badge {status_badge_class}">{status_code}</span>
                </div>
                {f'<div class="mb-2"><strong>协议:</strong> {metadata.get("protocol", "HTTP").upper()}</div>' if metadata.get('protocol') else ''}
                {f'<div class="mb-2"><strong>响应Headers:</strong><pre class="bg-white p-2 rounded"><code>{headers_json}</code></pre></div>' if headers else ''}
                <div class="mb-2">
                    <strong>响应体:</strong>
                    <pre class="bg-white p-2 rounded" style="max-height: 500px; overflow-y: auto;"><code>{body_formatted}</code></pre>
                </div>
            </div>
        """
