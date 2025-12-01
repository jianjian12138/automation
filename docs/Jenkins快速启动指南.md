# Jenkins CI/CD 快速启动指南

## 🚀 5分钟快速启动Jenkins（无需本地安装）

### 前置要求

- ✅ Docker Desktop（已安装并运行）
- ✅ 项目代码（已克隆）

---

## ⚡ 快速开始（3步骤）

### 步骤1: 启动Jenkins容器（1分钟）

```bash
# 在项目根目录执行
docker-compose up -d jenkins

# 查看启动日志
docker-compose logs -f jenkins
```

**等待1-2分钟**，直到看到日志：
```
Jenkins is fully up and running
```

---

### 步骤2: 获取初始密码（30秒）

```bash
# Windows PowerShell
docker exec ai_test_jenkins type C:\var\jenkins_home\secrets\initialAdminPassword

# Linux/Mac
docker exec ai_test_jenkins cat /var/jenkins_home/secrets/initialAdminPassword

# 或者运行初始化脚本
bash .jenkins/jenkins-setup.sh
```

**复制输出的密码**，稍后会用到。

---

### 步骤3: 访问并配置Jenkins（2分钟）

1. **打开浏览器访问**: http://localhost:8080

2. **输入初始密码**（刚才复制的）

3. **安装推荐的插件**
   - 点击 "Install suggested plugins"
   - 等待插件安装完成（约2-3分钟）

4. **创建管理员用户**
   - 用户名: `admin`
   - 密码: `admin123`（可自定义）
   - 邮箱: `admin@example.com`

5. **完成配置**
   - Jenkins URL: `http://localhost:8080`（默认即可）
   - 点击 "Save and Finish"

---

## 📋 创建第一个Pipeline

### 方法1: 使用Jenkinsfile（推荐）

1. **点击 "New Item"**

2. **输入项目名称**: `AI_TEST`

3. **选择 "Pipeline"** → 点击 "OK"

4. **配置Pipeline**:
   - **Definition**: 选择 "Pipeline script from SCM"
   - **SCM**: 选择 "Git"
   - **Repository URL**: 填写项目路径
     - 如果代码在本地: `file:///workspace/AI_TEST`
     - 如果是Git仓库: `https://github.com/your-repo/AI_TEST.git`
   - **Branch**: `*/main` 或 `*/master`
   - **Script Path**: `Jenkinsfile`

5. **点击 "Save"**

6. **点击 "Build Now"** 运行测试

---

### 方法2: 直接使用Pipeline脚本

1. **New Item** → **Pipeline** → **项目名称**: `AI_TEST`

2. **Pipeline配置**:
   - **Definition**: 选择 "Pipeline script"
   - 将 `Jenkinsfile` 内容复制到文本框中

3. **点击 "Save"** → **Build Now**

---

## 🎯 使用示例

### 运行所有测试

1. 点击 "Build with Parameters"
2. 使用默认参数（或根据需要修改）
3. 点击 "Build"

### 运行指定测试用例

1. Build with Parameters
2. 填写 `TEST_CASES`: `cases/api/examples/user_query.yaml`
3. 点击 "Build"

### 选择测试环境

1. Build with Parameters
2. 选择 `TEST_ENV`: `staging` 或 `prod`
3. 点击 "Build"

---

## 📊 查看测试结果

### 在Jenkins中查看

1. **Pipeline页面** → 点击构建号（如 #1）
2. **查看控制台输出**
3. **下载报告** → 点击 "Build Artifacts"
4. **查看报告** → 下载HTML报告文件

### 本地查看报告

报告保存在容器的 `/workspace/AI_TEST/reports/` 目录，也可以：

```bash
# 从容器复制报告到本地
docker cp ai_test_jenkins:/workspace/AI_TEST/reports ./reports_local

# 或者挂载目录到本地（修改docker-compose.yml）
```

---

## 🔧 常用操作

### 停止Jenkins

```bash
docker-compose stop jenkins
```

### 启动Jenkins

```bash
docker-compose start jenkins
```

### 重启Jenkins

```bash
docker-compose restart jenkins
```

### 查看Jenkins日志

```bash
docker-compose logs -f jenkins
```

### 进入Jenkins容器

```bash
docker exec -it ai_test_jenkins bash
```

### 清理并重新开始

```bash
# 停止并删除容器
docker-compose down

# 删除数据卷（会清除所有配置）
docker-compose down -v

# 重新启动
docker-compose up -d jenkins
```

---

## 🌊 使用Blue Ocean（现代化UI）

访问: http://localhost:8081

Blue Ocean提供更现代化的界面和更好的可视化。

---

## 📝 配置文件说明

### Jenkinsfile
- Pipeline配置脚本
- 定义了完整的CI/CD流程
- 支持参数化构建

### docker-compose.yml
- Docker Compose配置
- 包含Jenkins Classic和Blue Ocean
- 数据持久化配置

### scripts/ci/run_tests.sh
- Linux/Mac CI脚本
- 可在Jenkins或其他CI环境中使用

### scripts/ci/run_tests.bat
- Windows CI脚本
- 可在Jenkins或其他CI环境中使用

---

## ⚠️ 常见问题

### Q1: 无法访问 http://localhost:8080

**解决**:
```bash
# 检查容器是否运行
docker ps | grep jenkins

# 检查端口占用
netstat -ano | findstr :8080  # Windows
lsof -i :8080                 # Linux/Mac

# 修改端口（编辑docker-compose.yml）
ports:
  - "8081:8080"  # 改为其他端口
```

### Q2: 忘记管理员密码

**解决**:
```bash
# 重置密码（进入容器）
docker exec -it ai_test_jenkins bash
cat /var/jenkins_home/secrets/initialAdminPassword

# 或者删除admin用户配置（会清除所有配置）
docker-compose down -v
docker-compose up -d jenkins
```

### Q3: Pipeline执行失败

**解决**:
- 查看构建日志
- 检查Python环境
- 确认依赖安装成功
- 验证测试用例路径

### Q4: 无法找到测试报告

**解决**:
- 检查 `reports/` 目录权限
- 确认测试是否成功执行
- 查看构建日志中的错误信息

---

## 🎓 下一步

- ✅ 配置邮件通知（测试结果自动发送）
- ✅ 集成Slack/企业微信通知
- ✅ 配置定时任务（每天自动运行测试）
- ✅ 设置分支策略（PR自动触发测试）
- ✅ 集成代码质量检查工具

---

## 📚 相关文档

- [Jenkins集成说明](.jenkins/README.md) - 详细配置说明
- [Jenkinsfile](../Jenkinsfile) - Pipeline配置
- [CI脚本](scripts/ci/) - 测试执行脚本

---

**快速启动时间**: 约5分钟  
**版本**: v1.0  
**更新日期**: 2025-10-31
