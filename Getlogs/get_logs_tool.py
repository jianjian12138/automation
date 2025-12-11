#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
日志提取工具
功能：
1. 连接远程服务器，浏览ERP目录下的日志文件
2. 提取指定时间范围内的SQL语句
3. 提取日志中的错误信息
4. 提取日志中的异常信息
5. 支持自定义提取规则
6. 生成MD格式报告
"""

import paramiko
import re
import os
import datetime
import argparse
import json

class LogExtractor:
    def __init__(self, host, port=22, username='root', password=None, key_filename=None, erp_path='/opt/erp'):
        """初始化SSH连接"""
        self.host = host
        self.port = port
        self.username = username
        self.password = password
        self.key_filename = key_filename
        self.erp_path = erp_path
        self.ssh_client = None
        self.sftp_client = None
    
    def connect(self):
        """建立SSH连接"""
        try:
            self.ssh_client = paramiko.SSHClient()
            self.ssh_client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
            
            if self.key_filename:
                self.ssh_client.connect(
                    hostname=self.host,
                    port=self.port,
                    username=self.username,
                    key_filename=self.key_filename
                )
            else:
                self.ssh_client.connect(
                    hostname=self.host,
                    port=self.port,
                    username=self.username,
                    password=self.password
                )
            
            self.sftp_client = self.ssh_client.open_sftp()
            print(f"成功连接到服务器: {self.host}")
            return True
        except Exception as e:
            print(f"连接服务器失败: {e}")
            return False
    
    def disconnect(self):
        """关闭SSH连接"""
        if self.sftp_client:
            self.sftp_client.close()
        if self.ssh_client:
            self.ssh_client.close()
        print("已断开与服务器的连接")
    
    def list_erp_services(self):
        """列出ERP目录下的服务文件夹"""
        try:
            services = []
            for item in self.sftp_client.listdir_attr(self.erp_path):
                if item.st_mode & 0o040000:
                    services.append(item.filename)
            return services
        except Exception as e:
            print(f"列出服务文件夹失败: {e}")
            return []
    
    def list_service_logs(self, service_name):
        """列出指定服务下的日志文件"""
        try:
            # 使用正斜杠构造路径，确保SFTP客户端能正确处理
            service_path = f"{self.erp_path}/{service_name}"
            # 处理可能的双斜杠问题
            service_path = service_path.replace('//', '/')
            logs = []
            for item in self.sftp_client.listdir_attr(service_path):
                if item.filename.endswith('.log') or item.filename == 'nohup.out':
                    logs.append({
                        'name': item.filename,
                        'size': item.st_size,
                        'mtime': datetime.datetime.fromtimestamp(item.st_mtime)
                    })
            return logs
        except Exception as e:
            print(f"列出服务日志失败: {e}")
            return []
    
    def download_log(self, service_name, log_filename, local_path):
        """下载日志文件到本地"""
        try:
            # 构造远程路径，使用Linux路径分隔符
            remote_path = f"{self.erp_path}/{service_name}/{log_filename}"
            # 处理可能的双斜杠问题
            remote_path = remote_path.replace('//', '/')
            self.sftp_client.get(remote_path, local_path)
            print(f"成功下载日志文件: {remote_path} -> {local_path}")
            return True
        except Exception as e:
            print(f"下载日志文件失败: {e}")
            return False
    
    def extract_sql_from_log(self, log_content, start_time, end_time=None, skip_time_filter=False):
        """从日志中提取SQL语句"""
        sql_statements = []
        
        print(f"\n=== SQL提取开始 ===")
        print(f"时间范围: {start_time} 到 {end_time}")
        print(f"跳过时间过滤: {skip_time_filter}")
        print(f"日志内容长度: {len(log_content)} 字符")
        
        # 匹配所有包含Preparing的日志行，不依赖Parameters
        # 改进的正则表达式，匹配更灵活
        sql_pattern = r'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}).*?==>\s+Preparing:\s+(.*?)\n'
        matches = re.findall(sql_pattern, log_content, re.DOTALL)
        
        print(f"找到 {len(matches)} 个SQL匹配")
        
        # 为了调试，先打印前5个匹配的时间，看看日志中的实际时间格式
        print(f"\n前5个匹配的时间:")
        for i, match in enumerate(matches[:5]):
            log_time_str, sql = match
            print(f"  匹配 {i+1}: {log_time_str}")
        
        for i, match in enumerate(matches):
            try:
                log_time_str, sql = match
                # 解析时间，保留毫秒部分，用于调试
                full_log_time_str = log_time_str.replace(',', '.')
                log_time = datetime.datetime.strptime(full_log_time_str, '%Y-%m-%d %H:%M:%S.%f')
                
                print(f"\n匹配 {i+1}:")
                print(f"  原始时间字符串: {log_time_str}")
                print(f"  解析后的时间: {log_time}")
                print(f"  SQL: {sql[:50]}...")
                
                # 时间过滤
                include_sql = False
                
                if skip_time_filter:
                    include_sql = True
                    print("  ✓ 跳过时间过滤")
                else:
                    # 转换为只比较年月日时分，忽略秒和毫秒
                    log_time_truncated = log_time.replace(second=0, microsecond=0)
                    start_time_truncated = start_time.replace(second=0, microsecond=0)
                    end_time_truncated = end_time.replace(second=0, microsecond=0) if end_time else None
                    
                    print(f"  截断后的时间: {log_time_truncated}")
                    print(f"  截断后的开始时间: {start_time_truncated}")
                    if end_time_truncated:
                        print(f"  截断后的结束时间: {end_time_truncated}")
                    
                    # 检查是否在时间范围内
                    if log_time_truncated >= start_time_truncated:
                        if not end_time_truncated or log_time_truncated <= end_time_truncated:
                            include_sql = True
                            print(f"  ✓ 时间在范围内")
                        else:
                            print(f"  ✗ 时间超出结束范围")
                    else:
                        print(f"  ✗ 时间早于开始范围")
                
                if include_sql:
                    # 提取参数（如果有）
                    params_pattern = r'==>\s+Parameters:\s+(.*?)\n'
                    # 从SQL语句出现位置开始搜索，搜索范围扩大到2000字符
                    sql_start_pos = log_content.find(sql)
                    search_range = log_content[sql_start_pos:sql_start_pos+2000]
                    params_match = re.search(params_pattern, search_range)
                    params_str = params_match.group(1) if params_match else ''
                    
                    print(f"  参数: {params_str[:30]}...")
                    
                    # 解析参数
                    params = []
                    if params_str:
                        param_list = params_str.split(', ')
                        for param in param_list:
                            params.append(param.strip())
                    
                    # 将参数填入SQL语句
                    full_sql = sql
                    for param in params:
                        # 提取值和类型
                        if '(' in param and ')' in param:
                            value, type_part = param.rsplit('(', 1)
                            value = value.strip()
                            type_name = type_part.rstrip(')')
                            
                            # 根据参数类型添加引号
                            if type_name in ['String', 'Timestamp', 'Date']:
                                full_sql = full_sql.replace('?', f"'{value}'", 1)
                            elif type_name in ['Long', 'Integer', 'Double', 'Float']:
                                full_sql = full_sql.replace('?', value, 1)
                            elif type_name == 'Boolean':
                                full_sql = full_sql.replace('?', value.lower(), 1)
                            else:
                                # 其他类型，直接替换
                                full_sql = full_sql.replace('?', f"'{value}'", 1)
                        else:
                            # 没有类型信息，直接替换
                            full_sql = full_sql.replace('?', f"'{param}'", 1)
                    
                    # 添加到结果列表
                    sql_statements.append({
                        'time': log_time,
                        'original_sql': sql,
                        'processed_sql': full_sql,
                        'params': params_str
                    })
                    print(f"  ✓ 成功添加到结果列表")
                    print(f"  累计提取: {len(sql_statements)} 条")
            except Exception as e:
                print(f"处理SQL匹配 {i+1} 时出错: {e}")
                continue
        
        print(f"\n=== SQL提取结束 ===")
        print(f"最终提取到 {len(sql_statements)} 条SQL语句")
        return sql_statements
    

    
    def extract_errors(self, log_content, case_insensitive=True):
        """提取日志中的错误信息"""
        errors = []
        
        # 错误匹配模式
        error_pattern = r'(ERROR|Error|error).*?'
        if case_insensitive:
            error_pattern = r'(?i)error.*?'
        
        # 按行处理日志
        lines = log_content.split('\n')
        for i, line in enumerate(lines):
            if re.search(error_pattern, line):
                # 获取上下文（前后各2行）
                start = max(0, i-2)
                end = min(len(lines), i+3)
                context = lines[start:end]
                errors.append({
                    'line_number': i+1,
                    'content': line.strip(),
                    'context': '\n'.join(context)
                })
        
        return errors
    
    def extract_exceptions(self, log_content, case_insensitive=True):
        """提取日志中的异常信息"""
        exceptions = []
        
        # 异常匹配模式
        exception_pattern = r'(Exception|exception).*?'
        if case_insensitive:
            exception_pattern = r'(?i)exception.*?'
        
        # 按行处理日志
        lines = log_content.split('\n')
        for i, line in enumerate(lines):
            if re.search(exception_pattern, line):
                # 获取上下文（前后各5行）
                start = max(0, i-5)
                end = min(len(lines), i+6)
                context = lines[start:end]
                exceptions.append({
                    'line_number': i+1,
                    'content': line.strip(),
                    'context': '\n'.join(context)
                })
        
        return exceptions
    
    def extract_custom(self, log_content, pattern, case_insensitive=True):
        """根据自定义正则表达式提取日志信息"""
        matches = []
        
        flags = re.DOTALL
        if case_insensitive:
            flags |= re.IGNORECASE
        
        # 查找所有匹配
        for match in re.finditer(pattern, log_content, flags):
            matches.append({
                'content': match.group(0),
                'groups': match.groups()
            })
        
        return matches
    
    def generate_md_report(self, data, report_type, output_path):
        """生成MD格式报告"""
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(f"# 日志提取报告\n\n")
            f.write(f"**报告类型**: {report_type}\n")
            f.write(f"**生成时间**: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
            f.write("---\n\n")
            
            if report_type == 'sql':
                # 生成SQL报告
                for i, sql_info in enumerate(data, 1):
                    f.write(f"## 第 {i} 条SQL\n\n")
                    f.write(f"**时间**: {sql_info['time'].strftime('%Y-%m-%d %H:%M:%S')}\n\n")
                    f.write("**原始SQL**:\n")
                    f.write(f"```sql\n{sql_info['original_sql']}\n```\n\n")
                    f.write("**参数**:\n")
                    f.write(f"```\n{sql_info['params']}\n```\n\n")
                    f.write("**处理后SQL**:\n")
                    f.write(f"```sql\n{sql_info['processed_sql']}\n```\n\n")
                    
                    # 检查是否包含执行结果
                    if 'execution' in sql_info:
                        f.write("### 执行结果\n")
                        
                        if sql_info['execution']['success']:
                            f.write("**状态**: ✅ 执行成功\n\n")
                            
                            if sql_info['execution']['columns']:
                                # 生成表格
                                f.write("| " + " | ".join(sql_info['execution']['columns']) + " |\n")
                                f.write("| " + " | ".join(["---"] * len(sql_info['execution']['columns'])) + " |\n")
                                
                                # 写入数据行
                                for row in sql_info['execution']['rows']:
                                    # 处理每个字段，确保特殊字符被正确处理
                                    processed_row = []
                                    for field in row:
                                        if field is None:
                                            processed_row.append("NULL")
                                        elif isinstance(field, datetime.datetime):
                                            processed_row.append(field.strftime('%Y-%m-%d %H:%M:%S'))
                                        elif isinstance(field, datetime.date):
                                            processed_row.append(field.strftime('%Y-%m-%d'))
                                        else:
                                            processed_row.append(str(field))
                                    f.write("| " + " | ".join(processed_row) + " |\n")
                                f.write("\n")
                            
                            f.write(f"**总行数**: {sql_info['execution']['row_count']}\n")
                        else:
                            f.write("**状态**: ❌ 执行失败\n\n")
                            f.write(f"**错误信息**:\n")
                            f.write(f"```\n{sql_info['execution']['error']}\n```\n")
                    
                    f.write("---\n\n")
            
            elif report_type == 'error':
                # 生成错误报告
                for i, error_info in enumerate(data, 1):
                    f.write(f"## 第 {i} 条错误\n\n")
                    f.write(f"**行号**: {error_info['line_number']}\n\n")
                    f.write("**错误内容**:\n")
                    f.write(f"```\n{error_info['content']}\n```\n\n")
                    f.write("**上下文**:\n")
                    f.write(f"```\n{error_info['context']}\n```\n\n")
                    f.write("---\n\n")
            
            elif report_type == 'exception':
                # 生成异常报告
                for i, exception_info in enumerate(data, 1):
                    f.write(f"## 第 {i} 条异常\n\n")
                    f.write(f"**行号**: {exception_info['line_number']}\n\n")
                    f.write("**异常内容**:\n")
                    f.write(f"```\n{exception_info['content']}\n```\n\n")
                    f.write("**上下文**:\n")
                    f.write(f"```\n{exception_info['context']}\n```\n\n")
                    f.write("---\n\n")
            
            elif report_type == 'custom':
                # 生成自定义报告
                for i, match_info in enumerate(data, 1):
                    f.write(f"## 第 {i} 条匹配\n\n")
                    f.write("**匹配内容**:\n")
                    f.write(f"```\n{match_info['content']}\n```\n\n")
                    if match_info['groups']:
                        f.write("**捕获组**:\n")
                        for j, group in enumerate(match_info['groups'], 1):
                            f.write(f"- 组 {j}: {group}\n")
                        f.write("\n")
                    f.write("---\n\n")
        
        print(f"成功生成报告: {output_path}")

def main():
    parser = argparse.ArgumentParser(description='日志提取工具')
    parser.add_argument('--host', required=True, help='服务器地址')
    parser.add_argument('--port', type=int, default=22, help='SSH端口')
    parser.add_argument('--username', default='root', help='SSH用户名')
    parser.add_argument('--password', help='SSH密码')
    parser.add_argument('--key', help='SSH私钥文件路径')
    parser.add_argument('--service', required=True, help='服务名称')
    parser.add_argument('--log', required=True, help='日志文件名')
    parser.add_argument('--action', required=True, choices=['sql', 'error', 'exception', 'custom'], help='提取动作')
    parser.add_argument('--start-time', help='开始时间（格式：YYYY-MM-DD HH:MM:SS）')
    parser.add_argument('--end-time', help='结束时间（格式：YYYY-MM-DD HH:MM:SS）')
    parser.add_argument('--pattern', help='自定义提取正则表达式')
    parser.add_argument('--output', default='output.md', help='输出报告文件名')
    
    args = parser.parse_args()
    
    # 初始化日志提取器
    extractor = LogExtractor(
        host=args.host,
        port=args.port,
        username=args.username,
        password=args.password,
        key_filename=args.key
    )
    
    # 连接服务器
    if not extractor.connect():
        return
    
    try:
        # 下载日志文件到本地临时目录
        temp_dir = os.path.join(os.getcwd(), 'temp_logs')
        os.makedirs(temp_dir, exist_ok=True)
        local_log_path = os.path.join(temp_dir, args.log)
        
        if not extractor.download_log(args.service, args.log, local_log_path):
            return
        
        # 读取日志内容
        with open(local_log_path, 'r', encoding='utf-8', errors='ignore') as f:
            log_content = f.read()
        
        # 根据动作提取信息
        if args.action == 'sql':
            if not args.start_time:
                print('提取SQL需要指定开始时间 --start-time')
                return
            
            start_time = datetime.datetime.strptime(args.start_time, '%Y-%m-%d %H:%M:%S')
            end_time = None
            if args.end_time:
                end_time = datetime.datetime.strptime(args.end_time, '%Y-%m-%d %H:%M:%S')
            
            sql_statements = extractor.extract_sql_from_log(log_content, start_time, end_time)
            print(f"成功提取 {len(sql_statements)} 条SQL语句")
            extractor.generate_md_report(sql_statements, 'sql', args.output)
        
        elif args.action == 'error':
            errors = extractor.extract_errors(log_content)
            print(f"成功提取 {len(errors)} 条错误信息")
            extractor.generate_md_report(errors, 'error', args.output)
        
        elif args.action == 'exception':
            exceptions = extractor.extract_exceptions(log_content)
            print(f"成功提取 {len(exceptions)} 条异常信息")
            extractor.generate_md_report(exceptions, 'exception', args.output)
        
        elif args.action == 'custom':
            if not args.pattern:
                print('自定义提取需要指定正则表达式 --pattern')
                return
            
            matches = extractor.extract_custom(log_content, args.pattern)
            print(f"成功提取 {len(matches)} 条匹配信息")
            extractor.generate_md_report(matches, 'custom', args.output)
    
    finally:
        # 断开连接
        extractor.disconnect()
        
        # 清理临时文件
        if 'local_log_path' in locals() and os.path.exists(local_log_path):
            os.remove(local_log_path)
        if 'temp_dir' in locals() and os.path.exists(temp_dir):
            os.rmdir(temp_dir)

if __name__ == '__main__':
    main()
