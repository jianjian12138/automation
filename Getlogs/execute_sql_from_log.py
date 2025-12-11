#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从日志提取SQL并执行工具
功能：
1. 从日志中提取SQL语句
2. 连接PostgreSQL数据库执行SQL
3. 生成包含执行结果的MD文件
"""

import sys
import psycopg2
import datetime
import argparse
import os
from get_logs_tool import LogExtractor

def execute_sql(sql, conn, cursor):
    """执行SQL语句并返回结果"""
    try:
        # 执行SQL
        cursor.execute(sql)
        conn.commit()
        
        # 获取结果
        if cursor.description:
            # 有结果集的查询
            columns = [desc[0] for desc in cursor.description]
            rows = cursor.fetchall()
            row_count = len(rows)
        else:
            # 无结果集的执行（如UPDATE、DELETE等）
            columns = []
            rows = []
            row_count = cursor.rowcount
        
        return {
            'success': True,
            'columns': columns,
            'rows': rows,
            'row_count': row_count,
            'error': None
        }
    except Exception as e:
        conn.rollback()
        return {
            'success': False,
            'columns': [],
            'rows': [],
            'row_count': 0,
            'error': str(e)
        }

def generate_sql_execution_report(sql_results, output_path):
    """生成SQL执行结果报告"""
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write("# SQL执行结果报告\n\n")
        f.write(f"**生成时间**: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
        f.write("---\n\n")
        
        for i, result in enumerate(sql_results, 1):
            f.write(f"## 第 {i} 条SQL\n\n")
            f.write(f"**时间**: {result['time']}\n\n")
            
            f.write("**原始SQL**:\n")
            f.write(f"```sql\n{result['original_sql']}\n```\n\n")
            
            f.write("**参数**:\n")
            f.write(f"```\n{result['params']}\n```\n\n")
            
            f.write("**执行SQL**:\n")
            f.write(f"```sql\n{result['processed_sql']}\n```\n\n")
            
            f.write("### 执行结果\n")
            
            if result['execution']['success']:
                f.write("**状态**: ✅ 执行成功\n\n")
                
                if result['execution']['columns']:
                    # 生成表格
                    f.write("| " + " | ".join(result['execution']['columns']) + " |\n")
                    f.write("| " + " | ".join(["---"] * len(result['execution']['columns'])) + " |\n")
                    
                    # 写入数据行
                    for row in result['execution']['rows']:
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
                
                f.write(f"**总行数**: {result['execution']['row_count']}\n")
            else:
                f.write("**状态**: ❌ 执行失败\n\n")
                f.write(f"**错误信息**:\n")
                f.write(f"```\n{result['execution']['error']}\n```\n")
            
            f.write("---\n\n")
    
    print(f"成功生成执行报告: {output_path}")

def main():
    parser = argparse.ArgumentParser(description='从日志提取SQL并执行工具')
    
    # SSH连接参数
    parser.add_argument('--host', required=True, help='服务器地址')
    parser.add_argument('--port', type=int, default=22, help='SSH端口')
    parser.add_argument('--username', default='root', help='SSH用户名')
    parser.add_argument('--password', help='SSH密码')
    parser.add_argument('--key', help='SSH私钥文件路径')
    parser.add_argument('--service', required=True, help='服务名称')
    parser.add_argument('--log', required=True, help='日志文件名')
    
    # SQL提取参数
    parser.add_argument('--start-time', required=True, help='开始时间（格式：YYYY-MM-DD HH:MM:SS）')
    parser.add_argument('--end-time', help='结束时间（格式：YYYY-MM-DD HH:MM:SS）')
    
    # 数据库连接参数
    parser.add_argument('--db-host', default='192.168.2.172', help='数据库地址')
    parser.add_argument('--db-port', type=int, default=5432, help='数据库端口')
    parser.add_argument('--db-name', default='micgenerp', help='数据库名称')
    parser.add_argument('--db-user', default='postgres', help='数据库用户名')
    parser.add_argument('--db-password', default='postgres', help='数据库密码')
    
    # 输出参数
    parser.add_argument('--output', default='sql_execution_result.md', help='输出报告文件名')
    
    args = parser.parse_args()
    
    # 解析时间
    start_time = datetime.datetime.strptime(args.start_time, '%Y-%m-%d %H:%M:%S')
    end_time = datetime.datetime.strptime(args.end_time, '%Y-%m-%d %H:%M:%S') if args.end_time else None
    
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
    
    # 连接数据库
    conn = None
    cursor = None
    try:
        # 连接数据库
        conn = psycopg2.connect(
            host=args.db_host,
            port=args.db_port,
            database=args.db_name,
            user=args.db_user,
            password=args.db_password
        )
        cursor = conn.cursor()
        print("数据库连接成功")
        
        # 下载日志文件到本地临时目录
        temp_dir = os.path.join(os.getcwd(), 'temp_logs')
        os.makedirs(temp_dir, exist_ok=True)
        local_log_path = os.path.join(temp_dir, args.log)
        
        if not extractor.download_log(args.service, args.log, local_log_path):
            return
        
        # 读取日志内容
        with open(local_log_path, 'r', encoding='utf-8', errors='ignore') as f:
            log_content = f.read()
        
        # 提取SQL语句
        print("正在从日志中提取SQL语句...")
        sql_statements = extractor.extract_sql_from_log(log_content, start_time, end_time)
        print(f"成功提取 {len(sql_statements)} 条SQL语句")
        
        # 执行SQL语句
        print("正在执行SQL语句...")
        
        sql_results = []
        for sql_info in sql_statements:
            # 执行SQL
            execution_result = execute_sql(sql_info['processed_sql'], conn, cursor)
            
            sql_results.append({
                'time': sql_info['time'].strftime('%Y-%m-%d %H:%M:%S'),
                'original_sql': sql_info['original_sql'],
                'processed_sql': sql_info['processed_sql'],
                'params': sql_info['params'],
                'execution': execution_result
            })
        
        # 生成执行报告
        generate_sql_execution_report(sql_results, args.output)
        
        print(f"SQL执行完成，共执行 {len(sql_results)} 条SQL语句")
        print(f"成功: {sum(1 for r in sql_results if r['execution']['success'])}")
        print(f"失败: {sum(1 for r in sql_results if not r['execution']['success'])}")
    
    except Exception as e:
        print(f"执行过程中发生错误: {e}")
    
    finally:
        # 关闭数据库连接
        if cursor:
            cursor.close()
        if conn:
            conn.close()
        
        # 断开SSH连接
        extractor.disconnect()
        
        # 清理临时文件
        if 'local_log_path' in locals() and os.path.exists(local_log_path):
            os.remove(local_log_path)
        if 'temp_dir' in locals() and os.path.exists(temp_dir):
            os.rmdir(temp_dir)

def interactive_mode():
    """交互式模式"""
    print("从日志提取SQL并执行工具")
    print("=" * 60)
    
    # 获取SSH连接参数
    host = input("请输入服务器地址: ")
    port = input("请输入SSH端口 (默认22): ")
    port = int(port) if port else 22
    username = input("请输入SSH用户名 (默认root): ")
    username = username if username else "root"
    
    auth_type = input("请选择认证方式 (1.密码认证 2.密钥认证): ")
    password = None
    key_filename = None
    
    if auth_type == "1":
        password = input("请输入SSH密码: ")
    elif auth_type == "2":
        key_filename = input("请输入SSH私钥文件路径: ")
    else:
        print("无效的认证方式")
        return
    
    service = input("请输入服务名称: ")
    log_file = input("请输入日志文件名: ")
    
    # 获取时间范围
    start_time_str = input("请输入开始时间 (格式: YYYY-MM-DD HH:MM:SS): ")
    end_time_str = input("请输入结束时间 (可选, 格式: YYYY-MM-DD HH:MM:SS): ")
    
    try:
        start_time = datetime.datetime.strptime(start_time_str, '%Y-%m-%d %H:%M:%S')
        end_time = datetime.datetime.strptime(end_time_str, '%Y-%m-%d %H:%M:%S') if end_time_str else None
    except Exception as e:
        print(f"时间格式错误: {e}")
        return
    
    # 获取数据库连接参数
    print("\n" + "=" * 30)
    print("数据库连接参数")
    print("=" * 30)
    db_host = input("请输入数据库地址 (默认192.168.2.172): ")
    db_host = db_host if db_host else "192.168.2.172"
    db_port = input("请输入数据库端口 (默认5432): ")
    db_port = int(db_port) if db_port else 5432
    db_name = input("请输入数据库名称 (默认micgenerp): ")
    db_name = db_name if db_name else "micgenerp"
    db_user = input("请输入数据库用户名 (默认postgres): ")
    db_user = db_user if db_user else "postgres"
    db_password = input("请输入数据库密码 (默认postgres): ")
    db_password = db_password if db_password else "postgres"
    
    # 获取输出文件名
    output_file = input("请输入输出报告文件名 (默认sql_execution_result.md): ")
    output_file = output_file if output_file else "sql_execution_result.md"
    
    # 初始化日志提取器
    extractor = LogExtractor(
        host=host,
        port=port,
        username=username,
        password=password,
        key_filename=key_filename
    )
    
    # 连接服务器
    if not extractor.connect():
        return
    
    # 连接数据库
    conn = None
    cursor = None
    try:
        # 连接数据库
        conn = psycopg2.connect(
            host=db_host,
            port=db_port,
            database=db_name,
            user=db_user,
            password=db_password
        )
        cursor = conn.cursor()
        print("数据库连接成功")
        
        # 下载日志文件到本地临时目录
        temp_dir = os.path.join(os.getcwd(), 'temp_logs')
        os.makedirs(temp_dir, exist_ok=True)
        local_log_path = os.path.join(temp_dir, log_file)
        
        if not extractor.download_log(service, log_file, local_log_path):
            return
        
        # 读取日志内容
        with open(local_log_path, 'r', encoding='utf-8', errors='ignore') as f:
            log_content = f.read()
        
        # 提取SQL语句
        print("正在从日志中提取SQL语句...")
        sql_statements = extractor.extract_sql_from_log(log_content, start_time, end_time)
        print(f"成功提取 {len(sql_statements)} 条SQL语句")
        
        # 执行SQL语句
        print("正在执行SQL语句...")
        
        sql_results = []
        for sql_info in sql_statements:
            # 执行SQL
            execution_result = execute_sql(sql_info['processed_sql'], conn, cursor)
            
            sql_results.append({
                'time': sql_info['time'].strftime('%Y-%m-%d %H:%M:%S'),
                'original_sql': sql_info['original_sql'],
                'processed_sql': sql_info['processed_sql'],
                'params': sql_info['params'],
                'execution': execution_result
            })
        
        # 生成执行报告
        generate_sql_execution_report(sql_results, output_file)
        
        print(f"SQL执行完成，共执行 {len(sql_results)} 条SQL语句")
        print(f"成功: {sum(1 for r in sql_results if r['execution']['success'])}")
        print(f"失败: {sum(1 for r in sql_results if not r['execution']['success'])}")
    
    except Exception as e:
        print(f"执行过程中发生错误: {e}")
    
    finally:
        # 关闭数据库连接
        if cursor:
            cursor.close()
        if conn:
            conn.close()
        
        # 断开SSH连接
        extractor.disconnect()
        
        # 清理临时文件
        if 'local_log_path' in locals() and os.path.exists(local_log_path):
            os.remove(local_log_path)
        if 'temp_dir' in locals() and os.path.exists(temp_dir):
            os.rmdir(temp_dir)

if __name__ == '__main__':
    if len(sys.argv) > 1:
        # 命令行模式
        main()
    else:
        # 交互式模式
        interactive_mode()
