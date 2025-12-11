"""
HTML测试报告生成器（修复版）
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
            generate_allure: 是否同时生成Allure报告
            generate_excel: 是否同时生成Excel报告
            generate_pdf: 是否同时生成PDF报告
            generate_word: 是否同时生成Word报告
        
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
        # 生成图表数据
        chart_data = self._generate_chart_data(data)
        
        # 生成步骤HTML
        steps_html = self._generate_steps_html(data['step_results'], is_batch)
        
        # 准备格式化数据
        case_code_html = f'<div class="info-row"><span class="info-label">用例编码</span><span class="info-value">{data["case_code"]}</span></div>' if data.get('case_code') else ''
        case_path_html = f'<div class="info-row"><span class="info-label">用例路径</span><span class="info-value">{data["case_path"]}</span></div>' if data.get('case_path') else ''
        
        # 状态徽章样式
        status_badge_class = 'pass' if data['status'] == 'PASS' else 'fail'
        status_icon = 'bi-check-circle' if data['status'] == 'PASS' else 'bi-x-circle'
        
        # 优先级样式
        priority = data.get('priority', 'medium')
        priority_class = 'danger' if priority == 'high' else 'warning' if priority == 'medium' else 'success'
        priority_upper = priority.upper()
        
        # 标签HTML
        tags = data.get('tags', [])
        if tags:
            tags_html = f'<div class="info-row"><span class="info-label">标签</span><span class="info-value">{"、".join([f"<span class=\"badge bg-secondary\">{tag}</span>" for tag in tags])}</span></div>'
        else:
            tags_html = ''
        
        # 图表数据
        step_names = [step.get('step_name', '步骤' + str(i+1)) for i, step in enumerate(data['step_results'])]
        step_names_json = json.dumps(step_names, ensure_ascii=False)
        step_durations = [max(step.get('duration', 0), 0.01) for step in data['step_results']]
        step_durations_json = json.dumps(step_durations)
        
        # 生成HTML模板
        html = f'''<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>测试报告 - {data['case_name']}</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.1/font/bootstrap-icons.css">
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <style>
        :root {
            --primary-color: #0d6efd;
            --success-color: #198754;
            --danger-color: #dc3545;
            --warning-color: #ffc107;
            --info-color: #0dcaf0;
            --dark-color: #212529;
            --light-color: #f8f9fa;
        }
        
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Helvetica Neue', Arial, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px 0;
        }
        
        .report-container {
            max-width: 1400px;
            margin: 0 auto;
            background: white;
            border-radius: 20px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            overflow: hidden;
        }
        
        .report-header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 40px;
            text-align: center;
        }
        
        .report-header h1 {
            font-size: 2.5rem;
            font-weight: 700;
            margin-bottom: 10px;
        }
        
        .report-header .subtitle {
            font-size: 1.1rem;
            opacity: 0.9;
        }
        
        .report-body {
            padding: 40px;
        }
        
        .stats-card {
            background: white;
            border-radius: 15px;
            padding: 25px;
            box-shadow: 0 4px 15px rgba(0,0,0,0.1);
            transition: transform 0.3s, box-shadow 0.3s;
            border-left: 4px solid var(--primary-color);
            margin-bottom: 20px;
        }
        
        .stats-card:hover {
            transform: translateY(-5px);
            box-shadow: 0 8px 25px rgba(0,0,0,0.15);
        }
        
        .stats-card.success {
            border-left-color: var(--success-color);
        }
        
        .stats-card.danger {
            border-left-color: var(--danger-color);
        }
        
        .stats-card.info {
            border-left-color: var(--info-color);
        }
        
        .stats-number {
            font-size: 2.5rem;
            font-weight: 700;
            margin-bottom: 5px;
        }
        
        .stats-label {
            font-size: 0.9rem;
            color: #6c757d;
            text-transform: uppercase;
            letter-spacing: 1px;
        }
        
        .status-badge {
            display: inline-block;
            padding: 8px 20px;
            border-radius: 50px;
            font-weight: 600;
            font-size: 0.9rem;
            letter-spacing: 0.5px;
        }
        
        .status-badge.pass {
            background: #d1e7dd;
            color: #0f5132;
        }
        
        .status-badge.fail {
            background: #f8d7da;
            color: #842029;
        }
        
        .step-card {
            background: white;
            border-radius: 12px;
            padding: 20px;
            margin-bottom: 15px;
            border: 2px solid #e9ecef;
            transition: all 0.3s;
        }
        
        .step-card:hover {
            border-color: var(--primary-color);
            box-shadow: 0 4px 15px rgba(0,0,0,0.1);
        }
        
        .step-card.passed {
            border-left: 4px solid var(--success-color);
        }
        
        .step-card.failed {
            border-left: 4px solid var(--danger-color);
        }
        
        .step-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 15px;
        }
        
        .step-name {
            font-size: 1.1rem;
            font-weight: 600;
            color: var(--dark-color);
        }
        
        .step-errors {
            margin-top: 15px;
            padding: 15px;
            background: #fff3cd;
            border-radius: 8px;
            border-left: 4px solid var(--warning-color);
        }
        
        .error-item {
            margin: 8px 0;
            padding: 8px 12px;
            background: white;
            border-radius: 6px;
            font-family: 'Courier New', monospace;
            font-size: 0.9rem;
            color: var(--danger-color);
        }
        
        .chart-container {
            background: white;
            border-radius: 15px;
            padding: 25px;
            box-shadow: 0 4px 15px rgba(0,0,0,0.1);
            margin-bottom: 30px;
        }
        
        .info-section {
            background: #f8f9fa;
            border-radius: 12px;
            padding: 20px;
            margin-bottom: 20px;
        }
        
        .info-row {
            display: flex;
            justify-content: space-between;
            padding: 10px 0;
            border-bottom: 1px solid #dee2e6;
        }
        
        .info-row:last-child {
            border-bottom: none;
        }
        
        .info-label {
            font-weight: 600;
            color: #6c757d;
        }
        
        .info-value {
            color: var(--dark-color);
        }
        
        .protocol-badge {
            display: inline-block;
            padding: 4px 10px;
            border-radius: 6px;
            font-size: 0.75rem;
            font-weight: 600;
            background: #e7f1ff;
            color: #0d6efd;
            margin-left: 10px;
        }
        
        @media (max-width: 768px) {
            .report-header h1 {
                font-size: 1.8rem;
            }
            
            .report-body {
                padding: 20px;
            }
            
            .stats-number {
                font-size: 2rem;
            }
        }
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
                <span class="status-badge {status_badge_class}">
                    <i class="bi {status_icon}"></i>
                    {data['status']}
                </span>
            </div>
            
            <!-- 测试信息 -->
            <div class="info-section">
                <h5 class="mb-3"><i class="bi bi-info-circle"></i> 测试信息</h5>
                <div class="info-row">
                    <span class="info-label">用例名称</span>
                    <span class="info-value">{data['case_name']}</span>
                </div>
                {case_code_html}
                {case_path_html}
                <div class="info-row">
                    <span class="info-label">执行环境</span>
                    <span class="info-value"><span class="badge bg-info">{data['env']}</span></span>
                </div>
                <div class="info-row">
                    <span class="info-label">优先级</span>
                    <span class="info-value">
                        <span class="badge bg-{priority_class}">
                            {priority_upper}
                        </span>
                    </span>
                </div>
                {tags_html}
                <div class="info-row">
                    <span class="info-label">执行时间</span>
                    <span class="info-value">{data['timestamp']}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">总耗时</span>
                    <span class="info-value">{data['execution_time']:.2f} 秒</span>
                </div>
                <div class="info-row">
                    <span class="info-label">跳过步骤</span>
                    <span class="info-value">{data['skipped_steps']}</span>
                </div>
            </div>
            
            <!-- 测试步骤详情 -->
            <div class="mt-4">
                <h4 class="mb-3"><i class="bi bi-list-check"></i> 测试步骤详情</h4>
                {steps_html}
            </div>
        </div>
    </div>
    
    <!-- Bootstrap 5 JS -->
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>'''
        
        return html
    
    def _generate_chart_data(self, data: Dict) -> str:
        """生成图表数据（预留，当前直接在模板中生成）"""
        return ""
    
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
            
            step_html = f'''<div class="step-card {status_class}">
    <div class="step-header">
        <div>
            <span class="step-name">
                <i class="bi {status_icon}"></i>
                {step_name}
            </span>
        </div>
        <div>
            <span class="badge bg-secondary">{duration:.2f}s</span>
        </div>
    </div>
    <div class="step-content">
        <div class="step-status">
            <span class="status-badge {status_class}">{status_text}</span>
        </div>
        '''
            
            if errors:
                step_html += f'''<div class="step-errors">
            <h6>错误信息:</h6>
            '''
                for error in errors:
                    step_html += f'<div class="error-item">{error}</div>'
                step_html += '</div>'
            
            step_html += '''</div>
</div>'''
            
            html_parts.append(step_html)
        
        return ''.join(html_parts)
    
    def _generate_chart_html(self, data: Dict) -> str:
        """生成图表HTML"""
        return ""