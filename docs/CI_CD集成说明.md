# CI/CD 集成说明

## 📋 概述

AI_TEST框架现已支持完整的CI/CD集成，包括Jenkins和其他主流CI/CD平台。

---

## 🚀 快速开始（5分钟）

### 方式1: 使用Docker Compose（推荐）

**无需本地安装Jenkins**，使用Docker快速启动：

```bash
# 1. 启动Jenkins
docker-compose up -d jenkins

# 2. 获取初始密码
docker exec ai_test_jenkins cat /var/jenkins_home/secrets/initialAdminPassword

# 3. 访问Jenkins
# 浏览器打开: http://localhost:8080
# 输入初始密码，安装插件，创建管理员用户

# 4. 创建Pipeline项目
# - New Item -> Pipeline
# - 项目名称: AI_TEST
# - Pipeline script from SCM
# - Script Path: Jenkinsfile
```

### 方式2: 在现有Jenkins服务器上使用

1. 将项目代码推送到Git仓库
2. 在Jenkins中创建Pipeline项目
3. 配置Pipeline script from SCM
4. 指向包含Jenkinsfile的仓库

---

## 📁 相关文件

### 核心配置文件

| 文件 | 说明 |
|------|------|
| `Jenkinsfile` | Jenkins Pipeline配置 |
| `docker-compose.yml` | Docker Compose配置（本地Jenkins） |
| `.github/workflows/ci.yml` | GitHub Actions配置 |

### 脚本文件

| 文件 | 说明 |
|------|------|
| `scripts/ci/run_tests.sh` | Linux/Mac CI脚本 |
| `scripts/ci/run_tests.bat` | Windows CI脚本 |

### 文档

| 文件 | 说明 |
|------|------|
| `docs/Jenkins快速启动指南.md` | 5分钟快速启动指南 |
| `.jenkins/README.md` | 详细配置说明 |
| `docs/Jenkins集成总结.md` | 集成完成总结 |

---

## 🎯 支持的CI/CD平台

### 1. Jenkins ✅

- **Pipeline配置**: `Jenkinsfile`
- **Docker部署**: `docker-compose.yml`
- **详细文档**: `.jenkins/README.md`

### 2. GitHub Actions ✅

- **配置文件**: `.github/workflows/ci.yml`
- **自动触发**: push/pull_request
- **多Python版本**: 3.9, 3.10, 3.11

### 3. GitLab CI（可适配）

- 将Jenkinsfile转换为`.gitlab-ci.yml`
- 语法类似，易于转换

### 4. Azure DevOps（可适配）

- 将Jenkinsfile转换为`azure-pipelines.yml`
- 需要少量语法调整

---

## 🔧 Pipeline功能

### 支持的功能

- ✅ **多环境测试** - test/staging/prod
- ✅ **多类型测试** - API/Web/Mobile
- ✅ **参数化构建** - 灵活的测试配置
- ✅ **自动报告生成** - HTML测试报告
- ✅ **结果归档** - 自动归档测试报告
- ✅ **并行执行** - 支持并行测试
- ✅ **重试机制** - 失败自动重试

### Pipeline参数

| 参数 | 选项 | 默认值 |
|------|------|--------|
| TEST_ENV | test/staging/prod | test |
| TEST_TYPE | all/api/web/mobile | all |
| GENERATE_REPORT | true/false | true |
| SEND_NOTIFICATION | true/false | true |
| TEST_CASES | 用例路径 | 空 |

---

## 📊 测试报告

Pipeline执行后会自动：
1. ✅ 生成HTML测试报告
2. ✅ 归档报告文件（可在Jenkins中下载）
3. ✅ 归档日志文件
4. ✅ 生成测试汇总

---

## 🔔 通知配置

### 邮件通知

在Jenkinsfile中配置SMTP服务器：
1. Manage Jenkins -> System
2. 配置 "Extended E-mail Notification"
3. 设置SMTP服务器和端口
4. 取消注释邮件通知代码

### 其他通知方式

- **Slack** - 使用Slack Notification插件
- **企业微信** - 使用企业微信插件
- **钉钉** - 使用钉钉插件

---

## 💡 使用建议

### 1. 开发环境

- 本地运行测试
- 快速验证

### 2. 测试环境

- 提交代码后自动触发
- 快速反馈

### 3. 生产环境

- 手动触发或定时任务
- 完整回归测试

---

## 📚 更多资源

- [Jenkins快速启动指南](Jenkins快速启动指南.md)
- [Jenkins集成总结](Jenkins集成总结.md)
- [.jenkins/README.md](../.jenkins/README.md)

---

**更新日期**: 2025-10-31  
**版本**: v1.0
