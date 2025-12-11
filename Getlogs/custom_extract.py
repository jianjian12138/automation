#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
自定义日志提取工具
功能：
1. 使用自定义正则表达式提取日志信息
2. 生成MD格式报告
"""

import os
from get_logs_tool import LogExtractor

def main():
    print("自定义日志提取工具")
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
    
    service = input("请输入服务名称: ")
    log_file = input("请输入日志文件名: ")
    
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
        # 下载日志文件到本地临时目录
        temp_dir = os.path.join(os.getcwd(), 'temp_logs')
        os.makedirs(temp_dir, exist_ok=True)
        local_log_path = os.path.join(temp_dir, log_file)
        
        if not extractor.download_log(service, log_file, local_log_path):
            return
        
        # 读取日志内容
        with open(local_log_path, 'r', encoding='utf-8', errors='ignore') as f:
            log_content = f.read()
        
        # 获取提取参数
        print("\n" + "=" * 50)
        print("自定义提取设置")
        print("=" * 50)
        pattern = input("请输入正则表达式: ")
        case_sensitive = input("是否区分大小写? (y/n, 默认n): ")
        case_insensitive = case_sensitive.lower() != 'y'
        
        # 执行提取
        print("\n正在执行自定义提取...")
        matches = extractor.extract_custom(log_content, pattern, case_insensitive=case_insensitive)
        print(f"成功提取 {len(matches)} 条匹配信息")
        
        # 生成报告
        output_file = input("请输入输出报告文件名 (默认custom_extract_result.md): ")
        output_file = output_file if output_file else "custom_extract_result.md"
        
        extractor.generate_md_report(matches, 'custom', output_file)
        print(f"成功生成报告: {output_file}")
        
        # 显示部分结果
        print("\n" + "=" * 50)
        print("提取结果预览 (前5条):")
        print("=" * 50)
        for i, match in enumerate(matches[:5], 1):
            print(f"\n第 {i} 条:")
            print("-" * 30)
            print(f"内容: {match['content'][:100]}...")
            if match['groups']:
                print(f"捕获组: {match['groups']}")
    
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
