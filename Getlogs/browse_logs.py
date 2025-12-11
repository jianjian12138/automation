#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
日志文件浏览工具
功能：
1. 连接远程服务器
2. 浏览ERP目录下的服务
3. 浏览指定服务下的日志文件
4. 显示日志文件信息
"""

import os
import sys
from get_logs_tool import LogExtractor

def main():
    print("日志文件浏览工具")
    print("=" * 50)
    
    # 获取连接参数
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
    
    try:
        while True:
            print("\n" + "=" * 50)
            print("1. 列出所有服务")
            print("2. 查看服务日志文件")
            print("3. 退出")
            choice = input("请选择操作: ")
            
            if choice == "1":
                # 列出所有服务
                print("\n正在获取服务列表...")
                services = extractor.list_erp_services()
                if services:
                    print("\nERP服务列表:")
                    print("-" * 30)
                    for i, service in enumerate(services, 1):
                        print(f"{i}. {service}")
                else:
                    print("未找到服务")
            
            elif choice == "2":
                # 查看服务日志文件
                service_name = input("请输入服务名称: ")
                print(f"\n正在获取{service_name}的日志文件...")
                logs = extractor.list_service_logs(service_name)
                if logs:
                    print(f"\n{service_name}的日志文件:")
                    print("-" * 50)
                    print(f"{'序号':<5} {'文件名':<30} {'大小(KB)':<10} {'修改时间':<20}")
                    print("-" * 50)
                    for i, log in enumerate(logs, 1):
                        size_kb = round(log['size'] / 1024, 2)
                        mtime_str = log['mtime'].strftime('%Y-%m-%d %H:%M:%S')
                        print(f"{i:<5} {log['name']:<30} {size_kb:<10} {mtime_str:<20}")
                else:
                    print("未找到日志文件")
            
            elif choice == "3":
                # 退出
                print("退出浏览工具")
                break
            
            else:
                print("无效的选择")
    
    finally:
        # 断开连接
        extractor.disconnect()

if __name__ == '__main__':
    main()
