# Docker 安装快速开始

## 🚀 3步完成Docker安装

### 步骤1: 运行安装助手（自动检测系统）

```bash
# 在项目根目录执行
python scripts/install/install_docker.py
```

安装助手会自动：
- ✅ 检测操作系统（Windows/macOS/Linux）
- ✅ 检查Docker是否已安装
- ✅ 提供针对性的安装指导
- ✅ 自动运行安装脚本（可选）

### 步骤2: 按照提示安装

根据安装助手的提示：
- **Windows**: 自动下载并安装Docker Desktop
- **macOS**: 使用Homebrew或手动下载安装
- **Linux**: 自动配置仓库并安装Docker

### 步骤3: 验证安装

```bash
# 检查Docker版本
docker --version

# 检查Docker Compose版本
docker-compose --version

# 运行测试容器
docker run hello-world
```

---

## 📋 各平台安装方法

### Windows（推荐）

#### 方式1: 使用安装脚本（推荐）

```powershell
# 以管理员身份运行PowerShell
powershell -ExecutionPolicy Bypass -File scripts/install/install_docker_windows.ps1
```

#### 方式2: 手动安装

1. 访问: https://www.docker.com/products/docker-desktop/
2. 下载 `Docker Desktop Installer.exe`
3. 运行安装程序
4. 重启计算机（如需要）
5. 启动Docker Desktop

### macOS（推荐）

#### 方式1: 使用Homebrew（推荐）

```bash
# 安装Homebrew（如果未安装）
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 安装Docker Desktop
brew install --cask docker

# 启动Docker Desktop
open /Applications/Docker.app
```

#### 方式2: 使用安装脚本

```bash
bash scripts/install/install_docker_mac.sh
```

### Linux（推荐）

#### 方式1: 使用安装脚本（推荐）

```bash
# 运行安装脚本（需要sudo权限）
bash scripts/install/install_docker_linux.sh
```

#### 方式2: 手动安装（Ubuntu/Debian）

```bash
# 更新软件包索引
sudo apt-get update

# 安装依赖
sudo apt-get install -y ca-certificates curl gnupg lsb-release

# 添加Docker官方GPG密钥
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# 设置仓库
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 安装Docker Engine
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 启动Docker
sudo systemctl start docker
sudo systemctl enable docker

# 将用户添加到docker组（避免每次使用sudo）
sudo usermod -aG docker $USER
# 重新登录使组变更生效
```

---

## ✅ 验证安装

### 检查Docker版本

```bash
docker --version
# 应该输出: Docker version 24.x.x, build ...
```

### 检查Docker Compose版本

```bash
docker-compose --version
# 或（新版本）
docker compose version
```

### 运行测试容器

```bash
docker run hello-world
# 如果成功，会显示 "Hello from Docker!" 消息
```

---

## 🔧 常见问题

### Windows

#### Q1: Docker Desktop启动失败

**解决**:
```powershell
# 启用WSL 2
wsl --install
# 重启计算机
```

#### Q2: 权限问题

**解决**: 以管理员身份运行PowerShell

### macOS

#### Q1: Docker Desktop无法启动

**解决**: 重启Mac，然后重新启动Docker Desktop

#### Q2: 内存不足

**解决**: 在Docker Desktop设置中调整内存分配

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

---

## 📚 相关文档

- **详细指南**: [Docker安装指南](Docker安装指南.md)
- **脚本说明**: [scripts/install/README.md](../scripts/install/README.md)

---

## 🎯 安装完成后

安装完成后，可以：

1. **启动Jenkins**
   ```bash
   docker-compose up -d jenkins
   ```

2. **访问Jenkins**
   - 浏览器打开: http://localhost:8080
   - 获取初始密码: `docker exec ai_test_jenkins cat /var/jenkins_home/secrets/initialAdminPassword`

3. **配置Pipeline**
   - 按照 [Jenkins快速启动指南](Jenkins快速启动指南.md) 配置

---

**快速安装时间**: 约5-10分钟  
**版本**: v1.0  
**更新日期**: 2025-10-31
