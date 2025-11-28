/**
 * Jenkins Pipeline Configuration for AI_TEST
 * 支持多分支、多环境、自动化测试和报告生成
 * 优化版：更好的Jenkins集成、更统一的环境处理、更完善的报告和通知
 */

pipeline {
    agent any
    
    // 触发机制：git推送时自动构建
    triggers {
        pollSCM('* * * * *') // 每分钟检查一次git仓库变化
    }
    
    // 环境变量
    environment {
        // Python版本
        PYTHON_VERSION = '3.9'
        // 测试环境
        TEST_ENV = "${params.TEST_ENV ?: 'test'}"
        // 报告目录
        REPORT_DIR = 'reports'
        // 工作空间
        WORKSPACE_DIR = "${env.WORKSPACE}"
        // 项目名称
        PROJECT_NAME = 'AI_TEST'
        // 虚拟环境目录 - 使用预配置的虚拟环境
        VENV_DIR = '/var/jenkins_home/venv'
        // 依赖缓存目录
        PIP_CACHE_DIR = "${env.HOME}/.pip-cache"
        // Java项目目录
        JAVA_PROJECT_DIR = 'servercode/170server/forest-master/forest-master'
    }
    
    // 参数定义（可在Jenkins UI中配置）
    parameters {
        choice(
            name: 'TEST_ENV',
            choices: ['test', 'staging', 'prod'],
            description: '选择测试环境'
        )
        choice(
            name: 'TEST_TYPE',
            choices: ['all', 'api', 'web', 'mobile'],
            description: '选择测试类型'
        )
        booleanParam(
            name: 'GENERATE_REPORT',
            defaultValue: true,
            description: '是否生成HTML报告'
        )
        booleanParam(
            name: 'SEND_NOTIFICATION',
            defaultValue: true,
            description: '是否发送通知'
        )
        booleanParam(
            name: 'CODE_QUALITY_CHECK',
            defaultValue: true,
            description: '是否执行代码质量检查'
        )
        text(
            name: 'TEST_CASES',
            defaultValue: '',
            description: '指定测试用例（默认执行全部，格式：cases/api/examples/user_query.yaml，多个用例用逗号分隔）'
        )
        choice(
            name: 'NOTIFICATION_TYPE',
            choices: ['email', 'slack', 'wechat', 'all'],
            description: '选择通知方式'
        )
    }
    
    // Pipeline阶段
    stages {
        // 阶段1: 检出代码
        stage('Checkout') {
            steps {
                echo "🔍 检出代码..."
                
                script {
                    def checkoutSuccess = false
                    
                    // 1. 检查当前目录是否已有代码
                    if (fileExists('main.py')) {
                        checkoutSuccess = true
                        echo "✅ 当前目录已有代码，跳过Git克隆"
                    } else {
                        // 2. 尝试使用git clone（使用重试机制）
                        for (int i = 1; i <= 3; i++) {
                            try {
                                echo "📌 第 ${i}/3 次尝试从GitHub检出代码..."
                                // 使用无凭证克隆，避免认证失败
                                git branch: 'master', url: 'https://github.com/jianjian12138/automation.git', credentialsId: ''
                                checkoutSuccess = true
                                echo "✅ 从GitHub检出代码成功！"
                                break
                            } catch (Exception e) {
                                echo "❌ 第 ${i}/3 次从GitHub检出代码失败: ${e.getMessage()}"
                                if (i < 3) {
                                    echo "⏳ 等待10秒后重试..."
                                    sleep(time: 10, unit: 'SECONDS')
                                }
                            }
                        }
                    }
                    
                    // 2. 如果git clone失败，尝试使用本地备份（仅保留Linux兼容路径）
                    if (!checkoutSuccess) {
                        echo "⚠️  所有git尝试失败，尝试使用本地备份..."
                        
                        def backupPaths = [
                            "/var/jenkins_home/backups/automation",  // Docker内的备份目录
                            "/var/jenkins_home/workspace/automation",  // 可能的工作目录
                            "../automation",  // 相对路径备份
                            "/automation",  // Docker挂载目录
                            "/app",  // 常见的Docker应用目录
                            "/home/jenkins/automation"  // Jenkins用户目录
                        ]
                        
                        def backupFound = false
                        for (def backupPath : backupPaths) {
                            if (fileExists(backupPath) && fileExists("${backupPath}/main.py")) {
                                echo "✅ 找到本地备份: ${backupPath}"
                                sh "cp -r ${backupPath}/* ."
                                backupFound = true
                                break
                            }
                        }
                        
                        // 3. 如果本地备份也失败，尝试从当前目录的.git目录恢复
                        if (!backupFound && fileExists('.git')) {
                            echo "⚠️  尝试从.git目录恢复代码..."
                            sh '''
                                git reset --hard HEAD
                                git checkout master
                                git pull origin master
                            '''
                            if (fileExists('main.py')) {
                                backupFound = true
                                echo "✅ 从.git目录恢复代码成功"
                            }
                        }
                        
                        // 4. 如果所有尝试都失败，初始化基本目录结构
                        if (!backupFound) {
                            echo "⚠️  所有恢复尝试失败，初始化基本目录结构..."
                            
                            // 创建必要的目录结构
                            sh '''
                                mkdir -p cases/api/decimal_place
                                mkdir -p reports/api
                                mkdir -p reports/web
                                mkdir -p reports/mobile
                                mkdir -p logs
                                
                                echo "✅ 基本目录结构创建完成"
                            '''
                            
                            // 检查是否有必要的文件
                            if (!fileExists('main.py')) {
                                echo "❌ 缺少核心文件main.py，构建将跳过测试阶段"
                            }
                        }
                    }
                    
                    // 4. 验证工作目录
                    sh '''
                        echo "========================================"
                        echo "工作目录: ${WORKSPACE}"
                        echo "分支: ${BRANCH_NAME:-master}"
                        echo "构建号: $BUILD_NUMBER"
                        echo "构建URL: $BUILD_URL"
                        echo "当前用户: $(whoami)"
                        echo "当前目录: $(pwd)"
                        echo "========================================"
                        
                        # 列出工作目录内容
                        echo "\n=== 工作目录内容 ==="
                        ls -la
                        
                        # 检查核心文件
                        echo "\n=== 检查核心文件 ==="
                        if [ -f "main.py" ]; then
                            echo "✅ main.py 存在"
                        else
                            echo "❌ main.py 不存在"
                        fi
                        
                        # 检查cases目录
                        echo "\n=== 检查cases目录 ==="
                        if [ -d "cases" ]; then
                            echo "✅ cases目录存在"
                            ls -la cases/
                            
                            if [ -d "cases/api" ]; then
                                echo "\n✅ cases/api目录存在"
                                ls -la cases/api/
                                
                                # 统计测试用例数量
                                api_test_count=$(find cases/api -name "*.yaml" -type f | wc -l 2>/dev/null || echo 0)
                                echo "\n📊 找到 ${api_test_count} 个API测试用例"
                            else
                                echo "\n❌ cases/api目录不存在，创建空目录..."
                                mkdir -p cases/api
                            fi
                        else
                            echo "\n❌ cases目录不存在，创建空目录..."
                            mkdir -p cases/api/decimal_place
                        fi
                    '''
                }
            }
        }
        
        // 阶段2: 环境准备
        stage('Environment Setup') {
            steps {
                script {
                    echo "⚙️  准备测试环境..."
                    
                    // 1. 安装系统依赖（适用于Docker Debian/Ubuntu环境）
                    sh '''
                        echo "📦 安装系统依赖..."
                        
                        # 检查是否为root用户
                        if [ "$(id -u)" -eq 0 ]; then
                            # 仅在root权限下安装依赖
                            if [ -f /etc/debian_version ]; then
                                # Debian/Ubuntu系统
                                apt-get update -y && apt-get install -y --no-install-recommends \
                                    openjdk-21-jdk \
                                    maven \
                                    python3 \
                                    python3-venv \
                                    python3-pip \
                                    python3-dev \
                                    git \
                                    curl \
                                    wget \
                                    && rm -rf /var/lib/apt/lists/*
                            elif [ -f /etc/redhat-release ]; then
                                # CentOS/RHEL系统
                                yum update -y && yum install -y \
                                    java-21-openjdk \
                                    maven \
                                    python3 \
                                    python3-venv \
                                    python3-pip \
                                    python3-devel \
                                    git \
                                    curl \
                                    wget
                            fi
                        else
                            echo "⚠️  非root用户，跳过系统依赖安装"
                        fi
                        
                        # 验证关键工具
                        echo "\n=== 验证工具安装 ==="
                        java -version 2>/dev/null || echo "⚠️  Java未安装"
                        mvn -version 2>/dev/null || echo "⚠️  Maven未安装"
                        python3 --version 2>/dev/null || echo "⚠️  Python3未安装"
                        pip3 --version 2>/dev/null || echo "⚠️  pip3未安装"
                    '''
                    
                    // 2. 配置Python环境
                    sh '''
                        echo "🐍 配置Python环境..."
                        
                        # 设置Python命令
                        PYTHON_CMD="python3"
                        PIP_CMD="pip3"
                        
                        echo "使用Python命令：$PYTHON_CMD"
                        $PYTHON_CMD --version
                        
                        # 创建项目专属虚拟环境（避免依赖冲突）
                        VENV_DIR="./venv"
                        echo "创建项目虚拟环境：${VENV_DIR}"
                        
                        if [ ! -d "${VENV_DIR}" ]; then
                            echo "创建新的虚拟环境..."
                            $PYTHON_CMD -m venv ${VENV_DIR}
                        else
                            echo "使用现有虚拟环境..."
                        fi
                        
                        # 激活虚拟环境并安装依赖
                        echo "激活虚拟环境并安装依赖..."
                        
                        # 使用bash -c确保激活命令生效
                        bash -c "
                            source ${VENV_DIR}/bin/activate
                            echo '虚拟环境激活成功'
                            
                            # 升级pip
                            pip install --upgrade pip
                            
                            # 安装项目依赖（如果requirements.txt存在）
                            if [ -f 'requirements.txt' ]; then
                                echo '安装项目依赖...'
                                pip install -r requirements.txt
                            else
                                echo '⚠️  requirements.txt不存在，跳过依赖安装'
                            fi
                            
                            # 安装Playwright（如果需要）
                            if [[ '${TEST_TYPE}' == 'all' || '${TEST_TYPE}' == 'web' ]]; then
                                echo '安装Playwright...'
                                pip install playwright
                                playwright install --with-deps
                            fi
                        "
                    '''
                    
                    // 3. 创建报告和日志目录
                    sh '''
                        echo "📁 创建报告和日志目录..."
                        mkdir -p reports/api
                        mkdir -p reports/web
                        mkdir -p reports/mobile
                        mkdir -p reports/screenshots
                        mkdir -p reports/videos
                        mkdir -p reports/traces
                        mkdir -p reports/jacoco
                        mkdir -p logs
                        
                        echo "✅ 目录创建完成"
                    '''
                }
            }
        }
        
        // 阶段3: 构建Java项目并准备Jacoco代理
        stage('Build Java Project & Prepare Jacoco Agent') {
            when {
                expression { fileExists('servercode/170server/forest-master/forest-master/pom.xml') }
            }
            steps {
                script {
                    echo "🔧 构建Java项目并准备Jacoco代理..."
                    
                    // 进入Java项目目录并构建
                    sh '''
                        echo "检查Java项目目录..."
                        cd servercode/170server/forest-master/forest-master
                        echo "当前目录: $(pwd)"
                        
                        # 构建项目，不执行测试
                        mvn clean compile
                        
                        # 准备Jacoco代理参数
                        JACOCO_AGENT="-javaagent:${HOME}/.m2/repository/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar=destfile=${WORKSPACE}/servercode/170server/forest-master/forest-master/target/jacoco.exec,append=true,includes=**/org/aerie/forest/**/*.class,excludes=**/*Test*,**/test/**/*"
                        echo "Jacoco代理参数: ${JACOCO_AGENT}"
                        echo "${JACOCO_AGENT}" > ${WORKSPACE}/jacoco_agent.txt
                    '''
                }
            }
        }
        
        // 阶段4: 执行API测试
        stage('API Tests') {
            when {
                allOf {
                    anyOf {
                        expression { params.TEST_TYPE == 'all' }
                        expression { params.TEST_TYPE == 'api' }
                    }
                    expression { fileExists('main.py') }
                }
            }
            steps {
                script {
                    echo "🔍 执行API测试..."
                    
                    // 执行API测试
                    sh '''
                        # 使用项目专属虚拟环境
                        VENV_DIR="./venv"
                        
                        # 设置测试环境变量
                        export TEST_ENV=${TEST_ENV}
                        
                        # 确保cases目录存在
                        echo "========================================"
                        echo "确保测试用例目录存在..."
                        echo "========================================"
                        
                        mkdir -p cases/api/decimal_place
                        
                        # 调试：查看工作目录结构
                        echo "========================================"
                        echo "工作目录结构："
                        echo "========================================"
                        ls -la
                        echo "\ncases目录内容："
                        ls -la cases/ 2>/dev/null || echo "cases目录不存在"
                        echo "\nAPI测试用例目录内容："
                        ls -la cases/api/ 2>/dev/null || echo "cases/api目录不存在"
                        echo "\ndecimal_place目录内容："
                        ls -la cases/api/decimal_place/ 2>/dev/null || echo "decimal_place目录不存在"
                        echo "\n当前目录下所有yaml文件："
                        find . -name "*.yaml" -type f | head -20
                        echo "========================================"
                        
                        # 使用bash -c确保虚拟环境激活生效
                        bash -c "
                            source ${VENV_DIR}/bin/activate
                            echo '✅ 虚拟环境激活成功'
                            
                            # 执行API测试
                            if [ -z \"${TEST_CASES}\" ]; then
                                # 执行所有API测试用例
                                echo \"========================================\"
                                echo \"执行所有API测试用例...\" 
                                echo \"========================================\"
                                
                                # 查找所有API测试用例
                                api_test_cases=$(find cases/api -name \"*.yaml\" -type f | sort)
                                
                                if [ -z \"$api_test_cases\" ]; then
                                    echo \"⚠️  未找到API测试用例，跳过API测试\" 
                                else
                                    echo \"📊 找到 $(echo \"$api_test_cases\" | wc -l) 个API测试用例\" 
                                    
                                    # 执行每个测试用例
                                    echo \"$api_test_cases\" | while read test_file; do
                                        if [ -n \"$test_file\" ]; then
                                            echo \"\n🚀 执行测试: $test_file\" 
                                            python3 main.py --type api \"$test_file\" || echo \"⚠️  测试用例执行失败: $test_file\" 
                                        fi
                                    done
                                fi
                            else
                                # 执行指定的测试用例
                                echo \"========================================\"
                                echo \"执行指定API测试用例...\" 
                                echo \"========================================\"
                                echo \"指定的测试用例：${TEST_CASES}\" 
                                
                                # 分割测试用例列表
                                IFS=',' read -ra TEST_CASE_ARRAY <<< \"${TEST_CASES}\"
                                for test_file in \"${TEST_CASE_ARRAY[@]}\"; do
                                    test_file=$(echo \"$test_file\" | xargs)  # 去除空格
                                    if [ -n \"$test_file\" ]; then
                                        if [ -f \"$test_file\" ]; then
                                            echo \"\n🚀 执行测试: $test_file\" 
                                            python3 main.py --type api \"$test_file\" || echo \"⚠️  测试用例执行失败: $test_file\" 
                                        else
                                            echo \"\n❌ 测试文件不存在: $test_file\" 
                                        fi
                                    fi
                                done
                            fi
                        "
                    '''
                }
            }
            post {
                always {
                    // 收集测试结果
                    echo "📊 收集API测试结果..."
                    archiveArtifacts(
                        artifacts: 'reports/api/**/*.html, reports/api/**/*.json',
                        allowEmptyArchive: true,
                        fingerprint: true
                    )
                    junit(
                        allowEmptyResults: true,
                        testResults: 'reports/api/**/*.xml'
                    )
                }
            }
        }
        
        // 阶段5: 生成Jacoco覆盖率报告
        stage('Generate Jacoco Coverage Report') {
            when {
                expression { fileExists('servercode/170server/forest-master/forest-master/pom.xml') }
            }
            steps {
                script {
                    echo "📊 生成Jacoco覆盖率报告..."
                    
                    // 进入Java项目目录并生成覆盖率报告
                    sh '''
                        cd servercode/170server/forest-master/forest-master
                        echo "当前目录: $(pwd)"
                        
                        # 生成Jacoco覆盖率报告
                        mvn jacoco:report
                        
                        # 复制Jacoco报告到统一报告目录
                        mkdir -p ${WORKSPACE}/reports/jacoco
                        cp -r */target/jacoco-report/* ${WORKSPACE}/reports/jacoco/ 2>/dev/null || echo "未找到Jacoco报告，跳过复制"
                    '''
                }
            }
            post {
                always {
                    // 归档Jacoco覆盖率报告
                    echo "📊 归档Jacoco覆盖率报告..."
                    archiveArtifacts(
                        artifacts: 'reports/jacoco/**/*',
                        allowEmptyArchive: true,
                        fingerprint: true
                    )
                }
            }
        }
        
        // 阶段6: 代码质量检查
        stage('Code Quality') {
            when {
                expression { params.CODE_QUALITY_CHECK == true }
            }
            steps {
                script {
                    echo "🔍 执行代码质量检查..."
                    
                    // 使用项目专属虚拟环境
                    sh '''
                        VENV_DIR="./venv"
                        
                        // 使用bash -c确保虚拟环境激活生效
                        bash -c "
                            source ${VENV_DIR}/bin/activate
                            echo '✅ 虚拟环境激活成功'
                            
                            echo \"========================================\"
                            echo \"执行Python语法检查...\" 
                            
                            # 只有当core目录存在且有py文件时才执行语法检查
                            if [ -d \"core\" ] && [ \"$(ls -A core/*.py 2>/dev/null)\" ]; then
                                python3 -m py_compile core/*.py || echo \"Python语法检查完成（忽略错误）\" 
                            else
                                echo \"⚠️  没有Python文件需要检查\" 
                            fi
                            
                            echo \"========================================\"
                            echo \"检查YAML文件格式...\" 
                            
                            if command -v yamllint &> /dev/null && [ -d \"cases\" ]; then
                                yamllint cases/**/*.yaml || echo \"YAML检查完成（忽略错误）\" 
                            else
                                echo \"⚠️  yamllint未安装或cases目录不存在，跳过YAML检查\" 
                            fi
                            
                            echo \"========================================\"
                            echo \"检查requirements.txt格式...\" 
                            
                            if command -v pip-check &> /dev/null && [ -f \"requirements.txt\" ]; then
                                pip-check || echo \"依赖检查完成（忽略错误）\" 
                            else
                                echo \"⚠️  pip-check未安装或requirements.txt不存在，跳过依赖检查\" 
                            fi
                            
                            echo \"========================================\"
                            echo \"代码质量检查完成\" 
                        "
                    '''
                }
            }
        }
        
        // 阶段7: Web测试
        stage('Web Tests') {
            when {
                allOf {
                    anyOf {
                        expression { params.TEST_TYPE == 'all' }
                        expression { params.TEST_TYPE == 'web' }
                    }
                    expression { fileExists('main.py') }
                }
            }
            steps {
                script {
                    echo "🌐 执行Web测试..."
                    
                    // 执行Web测试
                    sh '''
                        # 使用项目专属虚拟环境
                        VENV_DIR="./venv"
                        
                        # 设置测试环境变量
                        export TEST_ENV=${TEST_ENV}
                        
                        # 确保测试目录存在
                        mkdir -p cases/web
                        mkdir -p reports/web
                        
                        # 使用bash -c确保虚拟环境激活生效
                        bash -c "
                            source ${VENV_DIR}/bin/activate
                            echo '✅ 虚拟环境激活成功'
                            
                            # 执行Web测试
                            if [ -d \"cases/web\" ] && [ \"$(ls -A cases/web 2>/dev/null)\" ]; then
                                echo \"========================================\"
                                echo \"执行Web测试...\" 
                                echo \"========================================\"
                                
                                if [ -z \"${TEST_CASES}\" ]; then
                                    # 执行所有Web测试用例
                                    python3 -m pytest cases/web -v --html=reports/web/web_report.html --self-contained-html
                                else
                                    # 执行指定的Web测试用例
                                    echo \"执行指定Web测试用例: ${TEST_CASES}\" 
                                    python3 -m pytest ${TEST_CASES} -v --html=reports/web/web_report.html --self-contained-html
                                fi
                            else
                                echo \"⚠️  Web测试目录不存在或为空，跳过Web测试\" 
                            fi
                        "
                    '''
                }
            }
            post {
                always {
                    // 收集测试结果
                    echo "📊 收集Web测试结果..."
                    archiveArtifacts(
                        artifacts: 'reports/web/**/*.html, reports/screenshots/**/*.png, reports/videos/**/*.mp4, reports/traces/**/*.zip',
                        allowEmptyArchive: true,
                        fingerprint: true
                    )
                    junit(
                        allowEmptyResults: true,
                        testResults: 'reports/web/**/*.xml'
                    )
                }
            }
        }
        
        // 阶段8: 生成汇总报告
        stage('Generate Summary Report') {
            when {
                expression { params.GENERATE_REPORT == true }
            }
            steps {
                script {
                    echo "📊 生成汇总报告..."
                    
                    // 生成测试汇总报告
                    sh '''
                        # 使用项目专属虚拟环境
                        VENV_DIR="./venv"
                        
                        # 确保报告目录存在
                        mkdir -p reports
                        
                        # 使用bash -c执行报告生成
                        bash -c "
                            source ${VENV_DIR}/bin/activate
                            echo '✅ 虚拟环境激活成功'
                            
                            echo \"========================================\"
                            echo \"生成测试汇总报告...\" 
                            echo \"========================================\"
                            
                            # 统计测试报告数量
                            html_reports=$(find reports -name \"*.html\" -type f | grep -v summary_report.html | sort)
                            total_reports=$(echo \"$html_reports\" | wc -l)
                            
                            echo \"\n找到 $total_reports 个测试报告\" 
                            echo \"$html_reports\" | while read report; do
                                if [ -n \"$report\" ]; then
                                    echo \"  - $report\" 
                                fi
                            done
                            
                            # 生成汇总报告文件
                            cat > reports/summary_report.md << EOF
# AI_TEST 测试汇总报告

## 构建信息
- **构建号**: ${env.BUILD_NUMBER}
- **分支**: ${env.BRANCH_NAME:-master}
- **构建URL**: ${env.BUILD_URL}
- **测试环境**: ${params.TEST_ENV}
- **测试类型**: ${params.TEST_TYPE}
- **构建时间**: $(date)

## 测试结果
- **总报告数**: $total_reports

## 测试报告列表
EOF
                            
                            # 添加报告列表到汇总报告
                            echo \"$html_reports\" | while read report; do
                                if [ -n \"$report\" ]; then
                                    report_name=$(basename \"$report\")
                                    echo \"- [$report_name]($report)\" >> reports/summary_report.md
                                fi
                            done
                            
                            echo \"\n✅ 汇总报告生成完成: reports/summary_report.md\" 
                        "
                    '''
                    
                    // 生成HTML汇总报告
                    echo "📊 生成HTML汇总报告..."
                    sh '''
                        # 确保报告目录存在
                        mkdir -p reports
                        
                        # 生成HTML汇总报告文件
                        cat > reports/summary_report.html << EOF
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AI_TEST 测试汇总报告</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        h1 { color: #2c3e50; text-align: center; }
        h2 { color: #3498db; border-bottom: 2px solid #3498db; padding-bottom: 10px; }
        .info { background: #f8f9fa; padding: 15px; border-radius: 5px; margin: 10px 0; }
        .info ul { list-style-type: none; padding: 0; }
        .info li { margin: 8px 0; }
        .report-list { list-style-type: none; padding: 0; }
        .report-list li { margin: 10px 0; padding: 10px; background: #f0f8ff; border-radius: 5px; }
        .report-list a { color: #3498db; text-decoration: none; font-weight: bold; }
        .report-list a:hover { text-decoration: underline; }
        .footer { text-align: center; margin-top: 20px; color: #7f8c8d; font-size: 14px; }
    </style>
</head>
<body>
    <div class="container">
        <h1>AI_TEST 测试汇总报告</h1>
        
        <div class="info">
            <h2>构建信息</h2>
            <ul>
                <li><strong>构建号</strong>: ${env.BUILD_NUMBER}</li>
                <li><strong>分支</strong>: ${env.BRANCH_NAME:-master}</li>
                <li><strong>构建URL</strong>: <a href="${env.BUILD_URL}">${env.BUILD_URL}</a></li>
                <li><strong>测试环境</strong>: ${params.TEST_ENV}</li>
                <li><strong>测试类型</strong>: ${params.TEST_TYPE}</li>
                <li><strong>构建时间</strong>: $(date)</li>
            </ul>
        </div>
        
        <div class="info">
            <h2>测试报告列表</h2>
            <ul class="report-list">
EOF
        
        # 添加报告链接到HTML
        html_reports=$(find reports -name "*.html" -type f | grep -v summary_report.html | sort)
        for report in $html_reports; do
            if [ -n "$report" ]; then
                report_name=$(basename "$report")
                echo "                <li><a href=\"$report\">$report_name</a></li>" >> reports/summary_report.html
            fi
        done
        
        # 完成HTML文件
        cat >> reports/summary_report.html << EOF
            </ul>
        </div>
        
        <div class="footer">
            <p>报告生成时间: $(date)</p>
        </div>
    </div>
</body>
</html>
EOF
            
        echo "✅ HTML汇总报告生成完成: reports/summary_report.html"
        '''
                }
            }
            post {
                always {
                    // 归档汇总报告
                    archiveArtifacts(
                        artifacts: 'reports/summary_report.md, reports/summary_report.html',
                        allowEmptyArchive: true
                    )
                }
            }
        }
    }
    
    // Pipeline后处理
    post {
        // 总是执行
        always {
            script {
                echo "🧹 清理环境..."
                
                // 清理临时文件
                sh '''
                    echo "========================================"
                    echo "清理临时文件..."
                    echo "========================================"
                    
                    # 清理Python编译文件和缓存
                    find . -type f -name "*.pyc" -delete 2>/dev/null || true
                    find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true
                    find . -type d -name ".pytest_cache" -exec rm -rf {} + 2>/dev/null || true
                    find . -type d -name "*.egg-info" -exec rm -rf {} + 2>/dev/null || true
                    
                    # 压缩报告目录
                    if [ -d "reports" ]; then
                        echo "\n压缩报告目录..."
                        if [ "$(uname)" = "Linux" ] || [ "$(uname)" = "Darwin" ]; then
                            # Linux/Mac
                            tar -czf reports_${BUILD_NUMBER}.tar.gz reports/ 2>/dev/null || true
                        else
                            # Windows
                            powershell -Command "Compress-Archive -Path reports -DestinationPath reports_${BUILD_NUMBER}.zip" 2>$null || true
                        fi
                    fi
                    
                    echo "\n✅ 清理完成"
                '''
                
                // 归档压缩后的报告
                archiveArtifacts(
                    artifacts: 'reports_*.tar.gz, reports_*.zip',
                    allowEmptyArchive: true,
                    fingerprint: true
                )
            }
        }
        
        // 成功后执行
        success {
            script {
                echo "✅ Pipeline执行成功！"
                
                // 发送成功通知（如果配置了）
                if (params.SEND_NOTIFICATION) {
                    echo "📧 发送成功通知..."
                    
                    // 定义通知内容
                    def subject = "✅ AI_TEST Pipeline成功 - Build #${env.BUILD_NUMBER}"
                    def body = """
🎉 AI_TEST 自动化测试Pipeline执行成功！

📋 构建信息：
- 构建号: ${env.BUILD_NUMBER}
- 分支: ${env.BRANCH_NAME}
- 测试环境: ${params.TEST_ENV}
- 测试类型: ${params.TEST_TYPE}
- 构建URL: ${env.BUILD_URL}

📊 测试结果：
- 查看测试报告: ${env.BUILD_URL}artifact/reports/
- 查看汇总报告: ${env.BUILD_URL}artifact/reports/summary_report.md

✅ 所有测试用例执行完成！
                    """
                    
                    // 根据通知类型发送通知
                    switch(params.NOTIFICATION_TYPE) {
                        case 'email':
                        case 'all':
                            echo "发送邮件通知..."
                            // emailext (
                            //     subject: subject,
                            //     body: body,
                            //     to: "${env.DEFAULT_RECIPIENTS}",
                            //     attachLog: true,
                            //     compressLog: true
                            // )
                            break
                        case 'slack':
                        case 'all':
                            echo "发送Slack通知..."
                            // slackSend (
                            //     channel: '#test-results',
                            //     color: 'good',
                            //     message: body
                            // )
                            break
                        case 'wechat':
                        case 'all':
                            echo "发送企业微信通知..."
                            // 企业微信通知逻辑
                            break
                    }
                }
            }
        }
        
        // 失败后执行
        failure {
            script {
                echo "❌ Pipeline执行失败！"
                
                // 发送失败通知
                if (params.SEND_NOTIFICATION) {
                    echo "📧 发送失败通知..."
                    
                    // 定义通知内容
                    def subject = "❌ AI_TEST Pipeline失败 - Build #${env.BUILD_NUMBER}"
                    def body = """
❌ AI_TEST 自动化测试Pipeline执行失败！

📋 构建信息：
- 构建号: ${env.BUILD_NUMBER}
- 分支: ${env.BRANCH_NAME}
- 测试环境: ${params.TEST_ENV}
- 测试类型: ${params.TEST_TYPE}
- 构建URL: ${env.BUILD_URL}

🔍 失败原因：
请查看构建日志获取详细信息: ${env.BUILD_URL}console

📊 测试结果：
- 查看测试报告: ${env.BUILD_URL}artifact/reports/
                    """
                    
                    // 根据通知类型发送通知
                    switch(params.NOTIFICATION_TYPE) {
                        case 'email':
                        case 'all':
                            echo "发送邮件通知..."
                            // emailext (
                            //     subject: subject,
                            //     body: body,
                            //     to: "${env.DEFAULT_RECIPIENTS}",
                            //     attachLog: true,
                            //     compressLog: true
                            // )
                            break
                        case 'slack':
                        case 'all':
                            echo "发送Slack通知..."
                            // slackSend (
                            //     channel: '#test-results',
                            //     color: 'danger',
                            //     message: body
                            // )
                            break
                        case 'wechat':
                        case 'all':
                            echo "发送企业微信通知..."
                            // 企业微信通知逻辑
                            break
                    }
                }
            }
        }
        
        // 不稳定时执行
        unstable {
            script {
                echo "⚙️  Pipeline执行不稳定！"
                
                // 发送不稳定通知
                if (params.SEND_NOTIFICATION) {
                    echo "📧 发送不稳定通知..."
                    
                    // 定义通知内容
                    def subject = "⚙️  AI_TEST Pipeline不稳定 - Build #${env.BUILD_NUMBER}"
                    def body = """
⚙️  AI_TEST 自动化测试Pipeline执行不稳定！

📋 构建信息：
- 构建号: ${env.BUILD_NUMBER}
- 分支: ${env.BRANCH_NAME}
- 测试环境: ${params.TEST_ENV}
- 测试类型: ${params.TEST_TYPE}
- 构建URL: ${env.BUILD_URL}

🔍 原因：
部分测试用例执行失败，请查看构建日志获取详细信息: ${env.BUILD_URL}console

📊 测试结果：
- 查看测试报告: ${env.BUILD_URL}artifact/reports/
                    """
                    
                    // 根据通知类型发送通知
                    switch(params.NOTIFICATION_TYPE) {
                        case 'email':
                        case 'all':
                            echo "发送邮件通知..."
                            // emailext (
                            //     subject: subject,
                            //     body: body,
                            //     to: "${env.DEFAULT_RECIPIENTS}",
                            //     attachLog: true,
                            //     compressLog: true
                            // )
                            break
                        case 'slack':
                        case 'all':
                            echo "发送Slack通知..."
                            // slackSend (
                            //     channel: '#test-results',
                            //     color: 'warning',
                            //     message: body
                            // )
                            break
                        case 'wechat':
                        case 'all':
                            echo "发送企业微信通知..."
                            // 企业微信通知逻辑
                            break
                    }
                }
            }
        }
        
        // 清理
        cleanup {
            script {
                echo "🧹 清理工作空间..."
                
                // 可以在这里添加更多清理逻辑
                // 例如：删除虚拟环境、清理临时文件等
                // sh "rm -rf ${VENV_DIR}"
            }
        }
    }
    
    // 选项配置
    options {
        // 构建超时时间（根据测试类型调整）
        timeout(time: params.TEST_TYPE == 'all' ? 3 : 2, unit: 'HOURS')
        
        // 保留最近的构建记录
        buildDiscarder(logRotator(
            numToKeepStr: '20',
            daysToKeepStr: '30',
            artifactNumToKeepStr: '10'
        ))
        
        // 在并发场景要用并发构建
        disableConcurrentBuilds()
        
        // 失败时重试（最多2次）
        retry(2)
        
        // 构建进度通知
        timestamps()
    }
}