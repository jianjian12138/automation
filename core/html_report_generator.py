"""
HTML测试报告生成器
"""

import json
from typing import Dict, List
import datetime
from pathlib import Path

class HTMLReportGenerator:
    """HTML测试报告生成器"""
    
    def __init__(self, output_dir: str = "reports/html", keep_all_reports: bool = False):
        """
        初始化报告生成器
        
        Args:
            output_dir: 报告输出目录
            keep_all_reports: 是否保存所有报告，否则只保存最新报告
        """
        self.output_dir = output_dir
        self.keep_all_reports = keep_all_reports
        
        # 创建输出目录
        Path(self.output_dir).mkdir(parents=True, exist_ok=True)
    
    def generate_report(self, test_results: Dict, test_path: str, is_batch: bool = False) -> str:
        """
        生成HTML测试报告
        
        Args:
            test_results: 测试结果数据
            test_path: 测试文件路径
            is_batch: 是否为批量执行
        
        Returns:
            str: 生成的报告文件路径
        """
        # 准备报告数据
        data = {
            'case_name': test_results.get('case_name', '未知用例'),
            'case_code': test_results.get('case_code', ''),
            'case_path': test_path,
            'passed_steps': test_results.get('passed_steps', 0),
            'failed_steps': test_results.get('failed_steps', 0),
            'total_steps': test_results.get('total_steps', 0),
            'pass_rate': round((test_results.get('passed_steps', 0) / test_results.get('total_steps', 1)) * 100, 1),
            'status': 'PASS' if test_results.get('passed', False) else 'FAIL',
            'timestamp': datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'execution_time': sum(step.get('duration', 0) for step in test_results.get('step_results', [])),
            'env': 'test',  # 默认环境
            'priority': 'medium',  # 默认优先级
            'tags': [],  # 默认标签
            'skipped_steps': sum(1 for step in test_results.get('step_results', []) if step.get('skipped', False)),
            'step_results': test_results.get('step_results', [])
        }
        
        # 生成HTML内容
        html = self._generate_html_content(data, is_batch)
        
        # 生成报告文件名
        if is_batch:
            report_filename = f"batch_report_{datetime.datetime.now().strftime('%Y%m%d_%H%M%S')}.html"
        else:
            # 使用用例名称作为报告文件名（去除特殊字符）
            case_name = data['case_name'].replace(' ', '_').replace('/', '_').replace('\\', '_')
            if self.keep_all_reports:
                report_filename = f"{case_name}_{datetime.datetime.now().strftime('%Y%m%d_%H%M%S')}.html"
            else:
                report_filename = f"{case_name}.html"
        
        # 生成报告文件路径
        report_path = Path(self.output_dir) / report_filename
        
        # 写入报告文件
        with open(report_path, 'w', encoding='utf-8') as f:
            f.write(html)
        
        return str(report_path)
    
    def _generate_html_content(self, data: Dict, is_batch: bool = False) -> str:
        """生成HTML内容"""
        # 生成步骤HTML
        steps_html = self._generate_steps_html(data['step_results'], is_batch)
        
        # 状态徽章样式
        status_badge_class = 'pass' if data['status'] == 'PASS' else 'fail'
        status_icon = 'bi-check-circle' if data['status'] == 'PASS' else 'bi-x-circle'
        
        # 优先级样式
        priority = data.get('priority', 'medium')
        priority_class = 'danger' if priority == 'high' else 'warning' if priority == 'medium' else 'success'
        priority_upper = priority.upper()
        
        # 标签HTML
        tags = data.get('tags', [])
        tag_badges = []
        for tag in tags:
            tag_badges.append(f'<span class="badge bg-secondary">{tag}</span>')
        tags_joined = '、'.join(tag_badges)
        tags_html = f'<div class="info-row"><span class="info-label">标签</span><span class="info-value">{tags_joined}</span></div>' if tags else ''
        
        # 生成图表数据
        step_names = [step.get('step_name', f'步骤 {i+1}') for i, step in enumerate(data['step_results'])]
        step_names_json = json.dumps(step_names, ensure_ascii=False)
        step_durations = [max(step.get('duration', 0), 0.01) for step in data['step_results']]
        step_durations_json = json.dumps(step_durations)
        
        # 生成HTML模板
        html_template = '''<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>测试报告 - {0[case_name]}</title>
    
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
            <div class="subtitle">{0[case_name]}</div>
        </div>
        
        <!-- 报告主体 -->
        <div class="report-body">
            <!-- 测试概要 -->
            <div class="row mb-4">
                <div class="col-md-3">
                    <div class="stats-card success">
                        <div class="stats-number text-success">{0[passed_steps]}</div>
                        <div class="stats-label">通过</div>
                    </div>
                </div>
                <div class="col-md-3">
                    <div class="stats-card danger">
                        <div class="stats-number text-danger">{0[failed_steps]}</div>
                        <div class="stats-label">失败</div>
                    </div>
                </div>
                <div class="col-md-3">
                    <div class="stats-card info">
                        <div class="stats-number text-info">{0[total_steps]}</div>
                        <div class="stats-label">总计</div>
                    </div>
                </div>
                <div class="col-md-3">
                    <div class="stats-card">
                        <div class="stats-number" style="color: #667eea;">{0[pass_rate]:.1f}%</div>
                        <div class="stats-label">通过率</div>
                    </div>
                </div>
            </div>
            
            <!-- 状态徽章 -->
            <div class="text-center mb-4">
                <span class="status-badge {1}">
                    <i class="bi {2}"></i>
                    {0[status]}
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
                    <span class="info-value">{0[case_name]}</span>
                </div>
                <div class="info-row"><span class="info-label">用例编码</span><span class="info-value">{0[case_code]}</span></div>
                <div class="info-row"><span class="info-label">用例路径</span><span class="info-value">{0[case_path]}</span></div>
                <div class="info-row">
                    <span class="info-label">执行时间</span>
                    <span class="info-value">{0[timestamp]}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">总耗时</span>
                    <span class="info-value">{0[execution_time]:.2f} 秒</span>
                </div>
            </div>
            
            <!-- 测试步骤详情 -->
            <div class="mt-4">
                <h4 class="mb-3"><i class="bi bi-list-check"></i> 测试步骤详情</h4>
                {3}
            </div>
        </div>
    </div>
    
    <!-- Bootstrap 5 JS -->
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
    
    <!-- 图表初始化 -->
    <script>
        // 结果分布饼图
        const resultCtx = document.getElementById('resultChart').getContext('2d');
        new Chart(resultCtx, {{
            type: 'doughnut',
            data: {{
                labels: ['通过', '失败'],
                datasets: [{{
                    data: [{0[passed_steps]}, {0[failed_steps]}],
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
        const stepNames = {4};
        const stepDurations = {5};
        
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
</html>'''.format(
            data,
            status_badge_class,
            status_icon,
            steps_html,
            step_names_json,
            step_durations_json
        )
        
        return html_template
    
    def _generate_steps_html(self, step_results: List[Dict], is_batch: bool = False) -> str:
        """生成测试步骤HTML"""
        html_parts = []
        
        for i, step in enumerate(step_results, 1):
            step_name = step.get('step_name', f'步骤 {i}')
            passed = step.get('passed', False)
            errors = step.get('errors', [])
            duration = max(step.get('duration', 0), 0.01)
            request = step.get('request', {})
            response = step.get('response', {})
            
            status_class = 'passed' if passed else 'failed'
            status_icon = 'bi-check-circle-fill text-success' if passed else 'bi-x-circle-fill text-danger'
            status_text = '通过' if passed else '失败'
            
            # 生成错误信息HTML
            errors_html = ''
            if errors:
                errors_html = '<div class="step-errors"><strong><i class="bi bi-exclamation-triangle"></i> 错误信息：</strong>'
                for error in errors:
                    errors_html += f'<div class="error-item">{error}</div>'
                errors_html += '</div>'
            
            # 生成请求信息HTML
            request_html = ''
            if request:
                request_html = f'''<div class="mb-4">
                <h6 class="text-primary"><i class="bi bi-arrow-up-circle"></i> 请求信息</h6>
                <hr>
                <div class="mb-2">
                    <strong>方法:</strong> <span class="badge bg-primary">{request.get('method', '')}</span>
                </div>
                <div class="mb-2"><strong>完整URL:</strong> <code>{request.get('url', '')}</code></div>
                <div class="mb-2"><strong>Host:</strong> <code>{request.get('host', '')}</code></div>
                <div class="mb-2"><strong>Path:</strong> <code>{request.get('path', '')}</code></div>'''
                
                if request.get('headers'):
                    headers_str = json.dumps(request['headers'], indent=2, ensure_ascii=False)
                    request_html += f'''<div class="mb-2"><strong>Headers:</strong><pre class="bg-white p-2 rounded"><code>{headers_str}</code></pre></div>'''
                
                if request.get('data'):
                    data_str = json.dumps(request['data'], indent=2, ensure_ascii=False)
                    request_html += f'''<div class="mb-2"><strong>请求体:</strong><pre class="bg-white p-2 rounded"><code>{data_str}</code></pre></div>'''
                
                request_html += '</div>'
            
            # 生成响应信息HTML
            response_html = ''
            if response:
                response_html = f'''<div>
                <h6 class="text-success"><i class="bi bi-arrow-down-circle"></i> 响应信息</h6>
                <hr>
                <div class="mb-2">
                    <strong>状态码:</strong> <span class="badge bg-{status_class}">{response.get('status_code', '')}</span>
                </div>'''
                
                if response.get('headers'):
                    headers_str = json.dumps(response['headers'], indent=2, ensure_ascii=False)
                    response_html += f'''<div class="mb-2"><strong>响应Headers:</strong><pre class="bg-white p-2 rounded"><code>{headers_str}</code></pre></div>'''
                
                if response.get('body'):
                    body_str = json.dumps(response['body'], indent=2, ensure_ascii=False)
                    response_html += f'''<div class="mb-2">
                    <strong>响应体:</strong>
                    <pre class="bg-white p-2 rounded" style="max-height: 500px; overflow-y: auto;"><code>{body_str}</code></pre>
                </div>'''
                
                response_html += '</div>'
            
            # 生成步骤HTML
            step_html = f'''<div class="step-card {status_class}">
    <div class="step-header">
        <div>
            <span class="step-name">
                <i class="bi {status_icon}"></i>
                {step_name}
            </span>
        </div>
        <div>
            <span class="status-badge {status_class}">
                {status_text}
            </span>
            <small class="text-muted ms-2">{duration:.2f}s</small>
        </div>
    </div>
    {errors_html}
    <div class="mt-3">
        <button class="btn btn-sm btn-outline-primary" type="button" data-bs-toggle="collapse" 
                data-bs-target="#details-{i}" aria-expanded="true">
            <i class="bi bi-chevron-up"></i> 查看详细信息
        </button>
        <div class=" mt-3" id="details-{i}">
            <div class="card card-body bg-light">
                {request_html}
                {response_html}
            </div>
        </div>
    </div>
</div>'''
            
            html_parts.append(step_html)
        
        return ''.join(html_parts)
    
    def _generate_chart_data(self, data: Dict) -> str:
        """生成图表数据（预留，当前直接在模板中生成）"""
        return """"""