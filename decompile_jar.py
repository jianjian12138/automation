#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
jar包反编译脚本
"""

import os
import subprocess
import shutil
import sys
from pathlib import Path

# 配置信息
JAR_PATH = r"f:\JJ_test\automation-test-platform\jar\pms-pas-service-3.1.1.300_#_[2025-12-03~~10_44_34#034].jar"
OUTPUT_DIR = r"f:\JJ_test\automation-test-platform\jar\temp_jar\decompiled_source"
BOOT_INF_CLASSES = r"f:\JJ_test\automation-test-platform\jar\temp_jar\BOOT-INF\classes"

# 确保输出目录存在
def ensure_dir(directory):
    if not os.path.exists(directory):
        os.makedirs(directory)
        print(f"创建目录: {directory}")
    else:
        print(f"目录已存在: {directory}")

# 反编译单个类
def decompile_class(class_name, output_file):
    """
    使用javap反编译单个类
    :param class_name: 类名，如 com.example.Test
    :param output_file: 输出文件路径
    """
    try:
        cmd = ["javap", "-c", "-p", "-verbose", class_name]
        print(f"执行命令: {' '.join(cmd)}")
        result = subprocess.run(cmd, cwd=BOOT_INF_CLASSES, capture_output=True, text=True, timeout=30)
        
        if result.returncode == 0:
            with open(output_file, 'w', encoding='utf-8') as f:
                f.write(f"// 反编译类: {class_name}\n")
                f.write(f"// 使用命令: {' '.join(cmd)}\n")
                f.write(f"// 执行目录: {BOOT_INF_CLASSES}\n\n")
                f.write(result.stdout)
            print(f"成功反编译类: {class_name} -> {output_file}")
            return True
        else:
            print(f"反编译失败: {class_name}")
            print(f"错误信息: {result.stderr}")
            return False
    except Exception as e:
        print(f"反编译异常: {class_name} - {e}")
        return False

# 遍历所有class文件
def find_all_classes(classes_dir):
    """
    遍历classes目录，查找所有class文件
    :param classes_dir: classes目录路径
    :return: 类名列表
    """
    class_names = []
    for root, dirs, files in os.walk(classes_dir):
        for file in files:
            if file.endswith('.class'):
                # 构建类名
                relative_path = os.path.relpath(root, classes_dir)
                if relative_path == '.':
                    class_name = file[:-6]  # 移除.class后缀
                else:
                    class_name = f"{relative_path.replace(os.sep, '.')}.{file[:-6]}"
                class_names.append(class_name)
    return class_names

# 主函数
def main():
    print("开始反编译jar包...")
    print(f"JAR文件路径: {JAR_PATH}")
    print(f"输出目录: {OUTPUT_DIR}")
    print(f"BOOT-INF/classes路径: {BOOT_INF_CLASSES}")
    print("=" * 60)
    
    # 确保输出目录存在
    ensure_dir(OUTPUT_DIR)
    
    # 查找所有class文件
    print("查找所有class文件...")
    class_names = find_all_classes(BOOT_INF_CLASSES)
    print(f"共找到 {len(class_names)} 个class文件")
    
    # 反编译前10个类作为示例
    print("\n反编译示例类...")
    example_classes = class_names[:10]
    for class_name in example_classes:
        # 构建输出文件路径
        output_file = os.path.join(OUTPUT_DIR, f"{class_name.replace('.', os.sep)}.java")
        output_dir = os.path.dirname(output_file)
        ensure_dir(output_dir)
        
        # 反编译类
        decompile_class(class_name, output_file)
    
    # 创建说明文件
    readme_content = f"# JAR包反编译结果\n\n"\
                     f"## 反编译信息\n\n"\
                     f"- **JAR文件**: {JAR_PATH}\n"\
                     f"- **反编译时间**: {subprocess.check_output(['date', '/t'], text=True).strip()}\n"\
                     f"- **总类数**: {len(class_names)}\n"\
                     f"- **示例类数**: {len(example_classes)}\n\n"\
                     f"## 反编译工具\n\n"\
                     f"使用Java自带的`javap`工具反编译，生成的是类的字节码和方法签名信息，不是完整的Java源码。\n\n"\
                     f"## 注意事项\n\n"\
                     f"1. javap生成的不是完整的Java源码，而是类的字节码和方法签名信息\n"\
                     f"2. 如需完整源码，请使用专业反编译工具如jadx或jd-gui\n"\
                     f"3. 反编译结果仅用于学习和分析，请勿用于商业用途\n\n"\
                     f"## 示例类列表\n\n"\
                     f"```\n"\
                     f"\n".join(example_classes)\n                     f"\n```\n"
    
    with open(os.path.join(OUTPUT_DIR, "README.md"), 'w', encoding='utf-8') as f:
        f.write(readme_content)
    
    print(f"\n反编译完成！")
    print(f"结果保存在: {OUTPUT_DIR}")
    print(f"生成的示例类数: {len(example_classes)}")
    print(f"完整类列表保存在: {os.path.join(OUTPUT_DIR, 'class_list.txt')}")
    
    # 保存完整类列表
    with open(os.path.join(OUTPUT_DIR, 'class_list.txt'), 'w', encoding='utf-8') as f:
        for class_name in class_names:
            f.write(f"{class_name}\n")

if __name__ == "__main__":
    main()
