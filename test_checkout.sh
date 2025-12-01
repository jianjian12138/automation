#!/bin/bash

echo "========================================"
echo "测试代码检出逻辑..."
echo "========================================"

# 模拟Jenkins工作目录
TEST_WORKSPACE="/tmp/jenkins_test_workspace"
rm -rf "$TEST_WORKSPACE"
mkdir -p "$TEST_WORKSPACE"
cd "$TEST_WORKSPACE"

echo "当前工作目录: $(pwd)"
echo "分支: main"
echo "构建号: 1"
echo "构建URL: http://localhost:8080/job/test/1"
echo "========================================"

# 检查当前目录是否为git仓库
if [ -d ".git" ]; then
    echo "当前目录是git仓库，执行git pull更新代码..."
    git pull origin main || echo "git pull失败，尝试git fetch + git checkout"
else
    echo "当前目录不是git仓库，执行git clone检出代码..."
    # 克隆当前项目到工作目录
    git clone file:///f/JJ_test/automation-test-platform .
fi

# 列出工作目录内容，确认代码已检出
echo "\n工作目录内容："
ls -la
echo "\n查看当前目录所有文件和目录（包括隐藏文件）："
find . -maxdepth 2 -type f -o -type d | sort
echo "\n查看cases目录是否存在："
ls -la cases/ 2>/dev/null || echo "cases目录不存在"
echo "\n查看decimal_place目录内容："
ls -la cases/api/decimal_place/ 2>/dev/null || echo "decimal_place目录不存在"
echo "\n查看add.yaml文件是否存在："
ls -la cases/api/decimal_place/add.yaml 2>/dev/null || echo "add.yaml文件不存在"

echo "========================================"
echo "测试完成！"
echo "========================================"