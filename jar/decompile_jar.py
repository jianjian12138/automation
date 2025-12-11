import os
import subprocess
import shutil

# 配置参数
JAR_PATH = "f:\\JJ_test\\automation-test-platform\\jar\\pms-pas-service-3.1.1.300_#_[2025-12-03~~10_44_34#034].jar"
TEMP_JAR_DIR = "f:\\JJ_test\\automation-test-platform\\jar\\temp_jar"
OUTPUT_DIR = "f:\\JJ_test\\automation-test-platform\\jar\\temp_jar\\decompiled_source"

# 确保输出目录存在
os.makedirs(OUTPUT_DIR, exist_ok=True)

# 步骤1: 解压JAR文件（如果尚未解压）
if not os.path.exists(os.path.join(TEMP_JAR_DIR, "BOOT-INF")):
    print("正在解压JAR文件...")
    subprocess.run(["jar", "xf", JAR_PATH], cwd=TEMP_JAR_DIR, check=True)
    print("JAR文件解压完成")

# 步骤2: 遍历所有.class文件
class_files = []
for root, dirs, files in os.walk(os.path.join(TEMP_JAR_DIR, "BOOT-INF", "classes")):
    for file in files:
        if file.endswith(".class"):
            class_files.append(os.path.join(root, file))

print(f"找到 {len(class_files)} 个.class文件")

# 步骤3: 使用javap反编译每个类
failed_classes = []
success_count = 0

for class_file in class_files:
    # 生成完整类名（例如：com.futurecraftsmen.pms.pas.service.impl.report.InnerReportDataSetQueryImpl）
    relative_path = os.path.relpath(class_file, os.path.join(TEMP_JAR_DIR, "BOOT-INF", "classes"))
    class_name = relative_path.replace(".class", "").replace(os.sep, ".")
    
    # 生成输出文件路径
    output_file = os.path.join(OUTPUT_DIR, relative_path.replace(".class", ".java"))
    output_dir = os.path.dirname(output_file)
    os.makedirs(output_dir, exist_ok=True)
    
    try:
        # 使用javap反编译类
        result = subprocess.run(
            ["javap", "-c", "-l", "-p", "-s", "-v", class_file],
            capture_output=True,
            text=True,
            check=True
        )
        
        # 将反编译结果写入.java文件
        with open(output_file, "w", encoding="utf-8") as f:
            f.write(f"// 反编译结果: {class_name}\n")
            f.write(f"// 原始文件: {class_file}\n")
            f.write("// 生成时间: " + subprocess.check_output(["date", "/t"], text=True).strip() + "\n\n")
            f.write(result.stdout)
        
        success_count += 1
        print(f"反编译成功: {class_name}")
        
    except subprocess.CalledProcessError as e:
        failed_classes.append(class_name)
        print(f"反编译失败: {class_name} - {e}")
    except Exception as e:
        failed_classes.append(class_name)
        print(f"处理失败: {class_name} - {e}")

# 步骤4: 创建README.md文件
try:
    readme_content = "# JAR包反编译结果\n\n"
    readme_content += "## 反编译信息\n\n"
    readme_content += f"- **JAR文件**: {JAR_PATH}\n"
    readme_content += f"- **反编译时间**: {subprocess.check_output(['date', '/t'], text=True).strip()}\n"
    readme_content += f"- **总类数**: {len(class_files)}\n"
    readme_content += f"- **成功反编译**: {success_count}\n"
    readme_content += f"- **失败反编译**: {len(failed_classes)}\n\n"
    readme_content += "## 反编译工具\n\n"
    readme_content += "使用Java自带的`javap`工具反编译，生成的是类的字节码和方法签名信息，不是完整的Java源码。\n\n"
    readme_content += "## 注意事项\n\n"
    readme_content += "1. javap生成的不是完整的Java源码，而是类的字节码和方法签名信息\n"
    readme_content += "2. 如需完整源码，请使用专业反编译工具如jadx或jd-gui\n"
    readme_content += "3. 反编译结果仅用于学习和分析，请勿用于商业用途\n\n"
    
    if failed_classes:
        readme_content += "## 反编译失败的类\n\n"
        readme_content += "```\n"
        for cls in failed_classes:
            readme_content += f"{cls}\n"
        readme_content += "```\n"
    
    with open(os.path.join(OUTPUT_DIR, "README.md"), "w", encoding="utf-8") as f:
        f.write(readme_content)
    
    print("README.md文件生成成功")
except Exception as e:
    print(f"生成README.md失败: {e}")

print(f"\n反编译完成！")
print(f"成功: {success_count}, 失败: {len(failed_classes)}")
print(f"输出目录: {OUTPUT_DIR}")