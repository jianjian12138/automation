#!/bin/bash

echo "========================================"
echo "测试Jenkinsfile的fallback机制..."
echo "========================================"

# 模拟Jenkins工作目录
TEST_WORKSPACE="/tmp/jenkins_fallback_test"
rm -rf "$TEST_WORKSPACE"
mkdir -p "$TEST_WORKSPACE"
cd "$TEST_WORKSPACE"

echo "当前工作目录: $(pwd)"
echo "========================================"

# 模拟Jenkins环境变量
export VENV_DIR="/var/jenkins_home/venv"
export TEST_ENV="test"
export TEST_CASES="cases/api/decimal_place/add.yaml"

echo "模拟Jenkins环境变量设置完成"
echo "VENV_DIR: $VENV_DIR"
echo "TEST_ENV: $TEST_ENV"
echo "TEST_CASES: $TEST_CASES"
echo "========================================"

# 执行修复后的fallback逻辑
echo "执行fallback机制测试..."
echo ""

# 检查cases目录是否存在
if [ ! -d "cases" ]; then
    echo "❌ cases目录不存在，尝试从本地路径复制..."
    
    # 尝试多个可能的路径
    POSSIBLE_PATHS=( 
        "/var/jenkins_home/workspace/JJ_TEST/cases" 
        "/var/jenkins_home/workspace/cases" 
        "/var/jenkins_home/cases" 
        "f:/JJ_test/automation-test-platform/cases" 
        "/f/JJ_test/automation-test-platform/cases" 
        "../cases" 
        "../../cases" 
    )
    
    for SOURCE_PATH in "${POSSIBLE_PATHS[@]}"; do
        if [ -d "$SOURCE_PATH" ]; then
            echo "✅ 找到cases目录：$SOURCE_PATH"
            echo "复制cases目录到当前工作目录..."
            cp -r "$SOURCE_PATH" .
            break
        fi
    done
    
    # 再次检查cases目录是否存在
    if [ ! -d "cases" ]; then
        echo "❌ 无法找到cases目录，尝试创建并复制单个测试文件..."
        mkdir -p cases/api/decimal_place
        
        # 尝试复制add.yaml文件
        POSSIBLE_FILE_PATHS=( 
            "/var/jenkins_home/workspace/JJ_TEST/cases/api/decimal_place/add.yaml" 
            "/var/jenkins_home/workspace/cases/api/decimal_place/add.yaml" 
            "/var/jenkins_home/cases/api/decimal_place/add.yaml" 
            "f:/JJ_test/automation-test-platform/cases/api/decimal_place/add.yaml" 
            "/f/JJ_test/automation-test-platform/cases/api/decimal_place/add.yaml" 
        )
        
        for SOURCE_FILE in "${POSSIBLE_FILE_PATHS[@]}"; do
            if [ -f "$SOURCE_FILE" ]; then
                echo "✅ 找到add.yaml文件：$SOURCE_FILE"
                echo "复制add.yaml文件到当前工作目录..."
                cp "$SOURCE_FILE" cases/api/decimal_place/
                break
            fi
        done
    fi
fi

# 验证结果
echo "========================================"
echo "验证结果："
echo "========================================"

if [ -f "cases/api/decimal_place/add.yaml" ]; then
    echo "✅ 测试通过：add.yaml文件存在于预期位置"
    echo "文件内容预览："
    head -10 cases/api/decimal_place/add.yaml
else
    echo "❌ 测试失败：add.yaml文件不存在"
fi

echo "========================================"
echo "测试完成！"
echo "========================================"