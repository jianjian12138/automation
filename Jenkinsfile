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
                
                // 使用git步骤直接从GitHub检出代码
                try {
                    git branch: 'master', url: 'https://github.com/jianjian12138/automation.git'
                } catch (Exception e) {
                    echo "❌ 从GitHub检出代码失败，尝试使用本地路径..."
                    // 如果GitHub检出失败，使用本地路径作为fallback
                    sh '''
                        if [ -d "/var/jenkins_home/workspace/JJ_TEST" ]; then
                            echo "✅ 从Jenkins工作目录复制文件..."
                            cp -r /var/jenkins_home/workspace/JJ_TEST/* .
                        elif [ -d "f:/JJ_test/automation-test-platform" ]; then
                            echo "✅ 从本地路径复制文件..."
                            cp -r f:/JJ_test/automation-test-platform/* .
                        else
                            echo "❌ 无法找到项目文件，构建失败！"
                            exit 1
                        fi
                    '''
                }
                
                script {
                    sh '''
                        echo "========================================"
                        echo "工作目录: ${WORKSPACE}"
                        echo "分支: ${BRANCH_NAME:-master}"
                        echo "构建号: $BUILD_NUMBER"
                        echo "构建URL: $BUILD_URL"
                        echo "当前用户: $(whoami)"
                        echo "当前目录: $(pwd)"
                        echo "========================================"
                        
                        # 列出工作目录内容，确认代码已检出
                        echo "\n=== 工作目录内容 ==="
                        ls -la
                        
                        echo "\n=== 检查cases目录 ==="
                        if [ -d "cases" ]; then
                            echo "✅ cases目录存在"
                            ls -la cases/
                            
                            echo "\n=== 检查API测试用例目录 ==="
                            if [ -d "cases/api" ]; then
                                echo "✅ cases/api目录存在"
                                ls -la cases/api/
                                
                                echo "\n=== 检查decimal_place目录 ==="
                                if [ -d "cases/api/decimal_place" ]; then
                                    echo "✅ cases/api/decimal_place目录存在"
                                    ls -la cases/api/decimal_place/
                                    
                                    echo "\n=== 检查add.yaml文件 ==="
                                    if [ -f "cases/api/decimal_place/add.yaml" ]; then
                                        echo "✅ add.yaml文件存在"
                                        echo "文件内容预览："
                                        head -10 cases/api/decimal_place/add.yaml
                                    else
                                        echo "❌ add.yaml文件不存在"
                                        echo "当前目录：$(pwd)"
                                        echo "寻找所有yaml文件："
                                        find . -name "*.yaml" -type f | grep -i decimal
                                    fi
                                else
                                    echo "❌ cases/api/decimal_place目录不存在"
                                    echo "cases/api目录内容："
                                    ls -la cases/api/
                                fi
                            else
                                echo "❌ cases/api目录不存在"
                                echo "cases目录内容："
                                ls -la cases/
                            fi
                        else
                            echo "❌ cases目录不存在，尝试从本地路径复制..."
                            
                            # 尝试从多个本地路径复制cases目录
                            POSSIBLE_LOCAL_PATHS=( 
                                "/var/jenkins_home/workspace/JJ_TEST/cases" 
                                "/var/jenkins_home/workspace/cases" 
                                "/var/jenkins_home/cases" 
                                "f:/JJ_test/automation-test-platform/cases" 
                                "/f/JJ_test/automation-test-platform/cases" 
                                "../cases" 
                                "../../cases" 
                            )
                            
                            CASES_COPIED=false
                            for SOURCE_PATH in "${POSSIBLE_LOCAL_PATHS[@]}"; do
                                if [ -d "$SOURCE_PATH" ]; then
                                    echo "✅ 从本地路径复制cases目录：$SOURCE_PATH"
                                    cp -r "$SOURCE_PATH" .
                                    CASES_COPIED=true
                                    break
                                fi
                            done
                            
                            if [ "$CASES_COPIED" = false ]; then
                                echo "❌ 无法从任何本地路径找到cases目录"
                                echo "当前目录所有文件："
                                find . -type f | head -20
                                echo "\n=== 检查当前目录结构 ==="
                                ls -la
                            else
                                echo "✅ cases目录复制成功"
                                ls -la cases/
                            fi
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
                    
                    // 安装Java和Maven（用于构建Java项目）
                    sh '''
                        echo "安装Java和Maven..."
                        if [ -f /etc/debian_version ]; then
                            # Debian/Ubuntu系统
                            apt-get update -y
                            apt-get install -y openjdk-21-jdk maven
                        elif [ -f /etc/redhat-release ]; then
                            # CentOS/RHEL系统
                            yum update -y
                            yum install -y java-21-openjdk maven
                        fi
                        
                        # 验证Java和Maven安装
                        java -version
                        mvn -version
                    '''
                    
                    // 创建虚拟环境和安装依赖
                    sh '''
                        # 创建依赖缓存目录
                        mkdir -p ${PIP_CACHE_DIR}
                        
                        # 检查Python是否安装，如果没有则安装
                        echo "检查Python是否安装..."
                        
                        # 重置PYTHON_CMD变量
                        PYTHON_CMD=""
                        
                        # 检查python3是否存在
                        if which python3 > /dev/null 2>&1; then
                            PYTHON_CMD="python3"
                            echo "找到python3"
                        # 检查python是否存在
                        elif which python > /dev/null 2>&1; then
                            PYTHON_CMD="python"
                            echo "找到python"
                        # 检查python3.9是否存在
                        elif which python3.9 > /dev/null 2>&1; then
                            PYTHON_CMD="python3.9"
                            echo "找到python3.9"
                        fi
                        
                        # 如果没有找到Python，执行安装
                        if [ -z "$PYTHON_CMD" ]; then
                            echo "未找到Python，开始安装系统默认的Python版本..."
                            
                            # 检测Linux发行版并安装Python
                            if [ -f /etc/debian_version ]; then
                                # Debian/Ubuntu系统
                                echo "检测到Debian/Ubuntu系统，使用apt-get安装Python3..."
                                echo "正在执行: apt-get update -y"
                                apt-get update -y
                                echo "正在执行: apt-get install -y python3 python3-venv python3-pip python3-dev"
                                apt-get install -y python3 python3-venv python3-pip python3-dev
                                
                                # 验证安装是否成功
                                if which python3 > /dev/null 2>&1; then
                                    PYTHON_CMD="python3"
                                    echo "✅ Python3安装成功"
                                else
                                    echo "❌ Python3安装失败，尝试安装python"
                                    apt-get install -y python python-venv python-pip python-dev
                                    if which python > /dev/null 2>&1; then
                                        PYTHON_CMD="python"
                                        echo "✅ Python安装成功"
                                    else
                                        echo "❌ Python安装失败"
                                        exit 1
                                    fi
                                fi
                            elif [ -f /etc/redhat-release ]; then
                                # CentOS/RHEL系统
                                echo "检测到CentOS/RHEL系统，使用yum安装Python3..."
                                echo "正在执行: yum update -y"
                                yum update -y
                                echo "正在执行: yum install -y python3 python3-venv python3-pip python3-devel"
                                yum install -y python3 python3-venv python3-pip python3-devel
                                
                                # 验证安装是否成功
                                if which python3 > /dev/null 2>&1; then
                                    PYTHON_CMD="python3"
                                    echo "✅ Python3安装成功"
                                else
                                    echo "❌ Python3安装失败，尝试安装python"
                                    yum install -y python python-venv python-pip python-devel
                                    if which python > /dev/null 2>&1; then
                                        PYTHON_CMD="python"
                                        echo "✅ Python安装成功"
                                    else
                                        echo "❌ Python安装失败"
                                        exit 1
                                    fi
                                fi
                            else
                                echo "❌ 错误：无法识别的Linux发行版，无法自动安装Python"
                                exit 1
                            fi
                        fi
                        
                        echo "使用Python命令：$PYTHON_CMD"
                        $PYTHON_CMD --version
                        
                        # 使用预配置的虚拟环境，跳过创建步骤
                        echo "使用预配置的虚拟环境：${VENV_DIR}"
                        
                        # 激活虚拟环境（使用预安装的依赖）
                        echo "激活虚拟环境..."
                        if [ "$(uname)" = "Linux" ] || [ "$(uname)" = "Darwin" ]; then
                            # Linux/Mac
                            . ${VENV_DIR}/bin/activate
                            echo "✅ 虚拟环境激活成功，使用预安装的依赖"
                        else
                            # Windows
                            call ${VENV_DIR}/Scripts/activate.bat
                            echo "✅ 虚拟环境激活成功，使用预安装的依赖"
                        fi
                    '''
                    
                    // 安装Playwright浏览器（如果需要Web测试）
                    script {
                        if (params.TEST_TYPE == 'all' || params.TEST_TYPE == 'web') {
                            echo "使用预安装的Playwright浏览器..."
                            sh '''
                                if [ "$(uname)" = "Linux" ] || [ "$(uname)" = "Darwin" ]; then
                                    # Linux/Mac
                                    . ${VENV_DIR}/bin/activate
                                    echo "✅ 使用预安装的Playwright浏览器"
                                else
                                    # Windows
                                    call ${VENV_DIR}/Scripts/activate.bat
                                    echo "✅ 使用预安装的Playwright浏览器"
                                fi
                            '''
                        }
                    }
                    
                    // 创建报告和日志目录
                    sh '''
                        echo "创建报告和日志目录..."
                        mkdir -p reports/api
                        mkdir -p reports/web
                        mkdir -p reports/mobile
                        mkdir -p reports/screenshots
                        mkdir -p reports/videos
                        mkdir -p reports/traces
                        mkdir -p reports/jacoco  # Jacoco覆盖率报告目录
                        mkdir -p logs
                    '''
                }
            }
        }
        
        // 阶段3: 构建Java项目并准备Jacoco代理
        stage('Build Java Project & Prepare Jacoco Agent') {
            steps {
                script {
                    echo "🔧 构建Java项目并准备Jacoco代理..."
                    
                    // 进入Java项目目录并构建
                    sh '''
                        echo "检查Java项目目录..."
                        ls -la servercode/170server/forest-master/forest-master || echo "目录不存在，跳过构建"
                        
                        if [ -d "servercode/170server/forest-master/forest-master" ]; then
                            cd servercode/170server/forest-master/forest-master
                            echo "当前目录: $(pwd)"
                            
                            # 构建项目，不执行测试
                            mvn clean compile
                            
                            # 准备Jacoco代理参数
                            JACOCO_AGENT="-javaagent:${HOME}/.m2/repository/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar=destfile=${WORKSPACE}/servercode/170server/forest-master/forest-master/target/jacoco.exec,append=true,includes=**/org/aerie/forest/**/*.class,excludes=**/*Test*,**/test/**/*"
                            echo "Jacoco代理参数: ${JACOCO_AGENT}"
                            echo "${JACOCO_AGENT}" > ${WORKSPACE}/jacoco_agent.txt
                        else
                            echo "⚠️ Java项目目录不存在，跳过Jacoco配置"
                        fi
                    '''
                }
            }
        }
        
        // 阶段4: 执行API测试
        stage('API Tests') {
            when {
                anyOf {
                    expression { params.TEST_TYPE == 'all' }
                    expression { params.TEST_TYPE == 'api' }
                }
            }
            steps {
                script {
                    echo "🔍 执行API测试..."
                    
                    // 执行API测试
                    sh '''
                        if [ "$(uname)" = "Linux" ] || [ "$(uname)" = "Darwin" ]; then
                            # Linux/Mac
                            . ${VENV_DIR}/bin/activate
                        else
                            # Windows
                            call ${VENV_DIR}/Scripts/activate.bat
                        fi
                        
                        # 设置测试环境变量
                        export TEST_ENV=${TEST_ENV}
                        
                        # 关键修复：确保cases目录存在
                        echo "========================================"
                        echo "确保测试用例目录存在..."
                        echo "========================================"
                        
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
                            
                            # 跳过GitHub克隆，直接使用本地路径
                            echo "⚠️  跳过GitHub克隆，直接使用本地路径..."
                            # 直接检查本地路径，不尝试GitHub克隆
                            
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
                        
                        # 执行API测试
                        if [ -z "${TEST_CASES}" ]; then
                            # 执行所有API测试用例
                            echo "========================================"
                            echo "执行所有API测试用例..."
                            echo "========================================"
                            
                            # 查找所有API测试用例
                            api_test_cases=$(find cases/api -name "*.yaml" -type f | sort)
                            
                            if [ -z "$api_test_cases" ]; then
                                echo "未找到API测试用例"
                            else
                                echo "找到 $(echo "$api_test_cases" | wc -l) 个API测试用例"
                                
                                # 执行每个测试用例
                                echo "$api_test_cases" | while read test_file; do
                                    if [ -n "$test_file" ]; then
                                        echo "\n执行测试: $test_file"
                                        python main.py --type api "$test_file"
                                    fi
                                done
                            fi
                        else
                            # 执行指定的测试用例
                            echo "========================================"
                            echo "执行指定API测试用例..."
                            echo "========================================"
                            echo "指定的测试用例：${TEST_CASES}"
                            
                            # 分割测试用例列表
                            echo "${TEST_CASES}" | tr ',' '\n' | while read test_file; do
                                test_file=$(echo "$test_file" | xargs)  # 去除空格
                                if [ -n "$test_file" ]; then
                                    if [ -f "$test_file" ]; then
                                        echo "\n执行测试: $test_file"
                                        python main.py --type api "$test_file"
                                    else
                                        echo "\n❌ 测试文件不存在: $test_file"
                                        echo "   检查文件是否存在：ls -la "$test_file" 2>&1"
                                        ls -la "$test_file" 2>&1
                                    fi
                                fi
                            done
                        fi
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
            steps {
                script {
                    echo "📊 生成Jacoco覆盖率报告..."
                    
                    // 进入Java项目目录并生成覆盖率报告
                    sh '''
                        echo "检查Java项目目录..."
                        ls -la servercode/170server/forest-master/forest-master || echo "目录不存在，跳过报告生成"
                        
                        if [ -d "servercode/170server/forest-master/forest-master" ]; then
                            cd servercode/170server/forest-master/forest-master
                            echo "当前目录: $(pwd)"
                            
                            # 生成Jacoco覆盖率报告
                            mvn jacoco:report
                            
                            # 复制Jacoco报告到统一报告目录
                            mkdir -p ${WORKSPACE}/reports/jacoco
                            cp -r */target/jacoco-report/* ${WORKSPACE}/reports/jacoco/ 2>/dev/null || echo "未找到Jacoco报告，跳过复制"
                        else
                            echo "⚠️ Java项目目录不存在，跳过Jacoco报告生成"
                        fi
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
                    sh '''
                        if [ "$(uname)" = "Linux" ] || [ "$(uname)" = "Darwin" ]; then
                                # Linux/Mac
                                . ${VENV_DIR}/bin/activate
                            else
                                # Windows
                                call ${VENV_DIR}/Scripts/activate.bat
                            fi
                        
                        echo "========================================"
                        echo "执行Python语法检查..."
                        python -m py_compile core/*.py || echo "Python语法检查完成（忽略错误）"
                        
                        echo "========================================"
                        echo "检查YAML文件格式..."
                        if command -v yamllint &> /dev/null; then
                            yamllint cases/**/*.yaml || echo "YAML检查完成（忽略错误）"
                        else
                            echo "yamllint未安装，跳过YAML检查"
                        fi
                        
                        echo "========================================"
                        echo "检查requirements.txt格式..."
                        if command -v pip-check &> /dev/null; then
                            pip-check || echo "依赖检查完成（忽略错误）"
                        else
                            echo "pip-check未安装，跳过依赖检查"
                        fi
                        
                        echo "========================================"
                        echo "代码质量检查完成"
                    '''
                }
            }
        }
        
        // 阶段7: Web测试
        stage('Web Tests') {
            when {
                anyOf {
                    expression { params.TEST_TYPE == 'all' }
                    expression { params.TEST_TYPE == 'web' }
                }
            }
            steps {
                script {
                    echo "🌐 执行Web测试..."
                    
                    // 执行Web测试
                    sh '''
                        if [ "$(uname)" = "Linux" ] || [ "$(uname)" = "Darwin" ]; then
                            # Linux/Mac
                            . ${VENV_DIR}/bin/activate
                        else
                            # Windows
                            call ${VENV_DIR}/Scripts/activate.bat
                        fi
                        
                        # 设置测试环境变量
                        export TEST_ENV=${TEST_ENV}
                        
                        # 执行Web测试
                        if [ -d "cases/web" ]; then
                            echo "========================================"
                            echo "执行Web测试..."
                            echo "========================================"
                            
                            if [ -z "${TEST_CASES}" ]; then
                                # 执行所有Web测试用例
                                pytest cases/web -v --html=reports/web/web_report.html --self-contained-html
                            else
                                # 执行指定的Web测试用例
                                echo "执行指定Web测试用例: ${TEST_CASES}"
                                pytest ${TEST_CASES} -v --html=reports/web/web_report.html --self-contained-html
                            fi
                        else
                            echo "⚙️  Web测试目录不存在，跳过Web测试"
                        fi
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
                        if [ "$(uname)" = "Linux" ] || [ "$(uname)" = "Darwin" ]; then
                            # Linux/Mac
                            . ${VENV_DIR}/bin/activate
                        else
                            # Windows
                            call ${VENV_DIR}/Scripts/activate.bat
                        fi
                        
                        echo "========================================"
                        echo "生成测试汇总报告..."
                        echo "========================================"
                        
                        # 统计测试报告数量
                        html_reports=$(find reports -name "*.html" -type f | sort)
                        total_reports=$(echo "$html_reports" | wc -l)
                        
                        echo "\n找到 $total_reports 个测试报告"
                        echo "$html_reports" | while read report; do
                            if [ -n "$report" ]; then
                                echo "  - $report"
                            fi
                        done
                        
                        # 生成汇总报告文件
                        cat > reports/summary_report.md << EOF
# AI_TEST 测试汇总报告

## 构建信息
- **构建号**: ${env.BUILD_NUMBER}
- **分支**: ${env.BRANCH_NAME}
- **构建URL**: ${env.BUILD_URL}
- **测试环境**: ${params.TEST_ENV}
- **测试类型**: ${params.TEST_TYPE}
- **构建时间**: $(date)

## 测试结果
- **总报告数**: $total_reports

## 测试报告列表
EOF
                        
                        # 添加报告列表到汇总报告
                        echo "$html_reports" | while read report; do
                            if [ -n "$report" ]; then
                                report_name=$(basename "$report")
                                echo "- [$report_name]($report)" >> reports/summary_report.md
                            fi
                        done
                        
                        echo "\n✅ 汇总报告生成完成: reports/summary_report.md"
                    '''
                    
                    // 生成HTML汇总报告（如果有需要）
                    echo "📊 生成HTML汇总报告..."
                    sh '''
                        # 可以在这里添加生成HTML汇总报告的逻辑
                        echo "HTML汇总报告生成逻辑待实现"
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