# Docker 安装指南

## 📋 概述

本指南提供Windows、Mac、Linux三个平台的Docker安装方法。

---

## 🪟 Windows 安装

### 方式1: Docker Desktop（推荐）

#### 前置要求

- Windows 10 64位（专业版、企业版、教育版）或 Windows 11
- 启用虚拟化（Hyper-V）
- 至少4GB RAM

#### 安装步骤

1. **下载Docker Desktop**
   - 访问: https://www.docker.com/products/docker-desktop/
   - 下载: `Docker Desktop Installer.exe`

2. **运行安装程序**
   - 双击安装程序
   - 勾选 "Use WSL 2 instead of Hyper-V"（推荐）
   - 点击 "Ok" 开始安装
   - 等待安装完成

3. **重启计算机**
   - 安装完成后需要重启

4. **启动Docker Desktop**
   - 启动后等待Docker引擎启动（约30秒）
   - 系统托盘会出现Docker图标

5. **验证安装**
   ```powershell
   docker --version
   docker-compose --version
   ```

#### 如果安装失败

**问题**: WSL 2未启用

**解决**:
```powershell
# 以管理员身份运行PowerShell
wsl --install
# 重启计算机
```

**问题**: 虚拟化未启用

**解决**:
1. 进入BIOS设置
2. 启用虚拟化（Intel VT-x 或 AMD-V）
3. 保存并重启

---

## 🍎 macOS 安装

### 方式1: Docker Desktop（推荐）

#### 前置要求

- macOS 10.15 或更高版本
- 至少4GB RAM

#### 安装步骤

1. **下载Docker Desktop**
   - 访问: https://www.docker.com/products/docker-desktop/
   - 下载: `Docker.dmg`

2. **安装**
   - 双击 `.dmg` 文件
   - 将Docker图标拖到Applications文件夹
   - 打开Applications，双击Docker图标
   - 点击 "Open"

3. **启动Docker**
   - 等待Docker启动
   - 菜单栏会出现Docker图标

4. **验证安装**
   ```bash
   docker --version
   docker-compose --version
   ```

### 方式2: Homebrew安装（可选）

```bash
# 安装Homebrew（如果未安装）
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 安装Docker Desktop
brew install --cask docker
```

---

## 🐧 Linux 安装

### Ubuntu/Debian

```bash
# 1. 更新软件包索引
sudo apt-get update

# 2. 安装依赖
sudo apt-get install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# 3. 添加Docker官方GPG密钥
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# 4. 设置仓库
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 5. 安装Docker Engine
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 6. 启动Docker
sudo systemctl start docker
sudo systemctl enable docker

# 7. 将当前用户添加到docker组（避免每次使用sudo）
sudo usermod -aG docker $USER
# 重新登录使组变更生效

# 8. 验证安装
docker --version
docker compose version
```

### CentOS/RHEL

```bash
# 1. 安装依赖
sudo yum install -y yum-utils

# 2. 添加Docker仓库
sudo yum-config-manager \
    --add-repo \
    https://download.docker.com/linux/centos/docker-ce.repo

# 3. 安装Docker Engine
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 4. 启动Docker
sudo systemctl start docker
sudo systemctl enable docker

# 5. 将用户添加到docker组
sudo usermod -aG docker $USER

# 6. 验证安装
docker --version
docker compose version
```

---

## ✅ 安装验证

### 验证Docker安装

```bash
# 检查Docker版本
docker --version
# 应该输出: Docker version 24.x.x, build ...

# 检查Docker Compose版本
docker-compose --version
# 或者（新版本）
docker compose version

# 运行测试容器
docker run hello-world
# 如果成功，会显示 "Hello from Docker!" 消息
```

### 验证Docker Compose

```bash
# 测试docker-compose（如果有独立版本）
docker-compose --version

# 或测试插件版本（新版本）
docker compose version
```

---

## 🔧 常见问题

### Windows

#### Q1: Docker Desktop启动失败

**错误**: "WSL 2 installation is incomplete"

**解决**:
```powershell
# 以管理员身份运行
wsl --update
wsl --set-default-version 2
```

#### Q2: 权限问题

**错误**: "Access denied"

**解决**:
- 确保以管理员身份运行Docker Desktop
- 检查Windows用户权限

### macOS

#### Q1: Docker Desktop无法启动

**解决**:
1. 检查系统要求（macOS版本）
2. 重新安装Docker Desktop
3. 重启Mac

#### Q2: 内存不足

**解决**:
- 在Docker Desktop设置中调整内存分配
- 关闭其他应用程序

### Linux

#### Q1: 权限被拒绝

**错误**: "Got permission denied while trying to connect to the Docker daemon socket"

**解决**:
```bash
# 将用户添加到docker组
sudo usermod -aG docker $USER

# 重新登录或执行
newgrp docker

# 验证
docker run hello-world
```

#### Q2: Docker服务未运行

**解决**:
```bash
# 启动Docker服务
sudo systemctl start docker

# 设置开机自启
sudo systemctl enable docker

# 检查状态
sudo systemctl status docker
```

---

## 📚 后续步骤

安装完成后，可以：

1. **启动Jenkins**
   ```bash
   cd AI_TEST
   docker-compose up -d jenkins
   ```

2. **验证安装**
   ```bash
   # 运行安装验证脚本
   python tools/verify_installation.py
   ```

---

## 🔗 官方资源

- **Docker Desktop**: https://www.docker.com/products/docker-desktop/
- **Docker文档**: https://docs.docker.com/
- **Docker Compose文档**: https://docs.docker.com/compose/

---

**更新日期**: 2025-10-31  
**版本**: v1.0
