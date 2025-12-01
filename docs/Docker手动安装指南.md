# Docker 手动安装指南（Windows）

## 🚀 快速安装步骤

### 步骤1: 下载Docker Desktop

1. **访问Docker官网**
   - 网址: https://www.docker.com/products/docker-desktop/
   - 点击 "Download for Windows"

2. **下载安装程序**
   - 文件名: `Docker Desktop Installer.exe`
   - 大小: 约500MB
   - 下载位置: 默认在Downloads文件夹

### 步骤2: 运行安装程序

1. **双击安装程序**
   - 找到下载的 `Docker Desktop Installer.exe`
   - 双击运行

2. **安装向导**
   - 勾选 "Use WSL 2 instead of Hyper-V"（推荐）
   - 点击 "Ok" 开始安装
   - 等待安装完成（约5-10分钟）

### 步骤3: 完成安装

1. **安装完成后**
   - 点击 "Close and restart"（如需要）
   - 或手动重启计算机

2. **启动Docker Desktop**
   - 从开始菜单启动Docker Desktop
   - 等待Docker引擎启动（约30秒-1分钟）
   - 系统托盘会出现Docker图标

### 步骤4: 验证安装

打开PowerShell或命令提示符，运行：

```powershell
# 检查Docker版本
docker --version

# 应该输出类似: Docker version 24.x.x, build ...

# 检查Docker Compose版本
docker-compose --version

# 运行测试容器
docker run hello-world
```

如果成功，会显示 "Hello from Docker!" 消息。

---

## ✅ 安装完成

安装完成后，可以：

1. **启动Jenkins**
   ```powershell
   cd F:\BaiduNetdiskDownload\AT\AI_TEST
   docker-compose up -d jenkins
   ```

2. **访问Jenkins**
   - 浏览器打开: http://localhost:8080
   - 获取初始密码:
     ```powershell
     docker exec ai_test_jenkins cat /var/jenkins_home/secrets/initialAdminPassword
     ```

---

## ⚠️ 常见问题

### Q1: 安装程序无法运行

**解决**:
- 确保以管理员身份运行
- 检查系统要求（Windows 10 64位或更高版本）

### Q2: 需要重启计算机

**解决**:
- 如果提示需要重启，请重启计算机
- 重启后重新运行安装程序（如果需要）

### Q3: WSL 2未安装

**解决**:
```powershell
# 以管理员身份运行PowerShell
wsl --install

# 重启计算机
```

### Q4: Docker Desktop启动失败

**解决**:
1. 检查虚拟化是否启用（BIOS设置）
2. 检查WSL 2是否安装: `wsl --version`
3. 查看Docker Desktop日志

### Q5: 端口8080已被占用

**解决**:
1. 查找占用端口的程序:
   ```powershell
   netstat -ano | findstr :8080
   ```
2. 停止占用端口的程序
3. 或修改 `docker-compose.yml` 中的端口映射

---

## 📚 相关文档

- **Docker安装指南**: [Docker安装指南.md](Docker安装指南.md)
- **Jenkins快速启动**: [Jenkins快速启动指南.md](Jenkins快速启动指南.md)

---

**安装时间**: 约10-15分钟  
**更新日期**: 2025-10-31
