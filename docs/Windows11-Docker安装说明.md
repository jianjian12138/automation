# Windows 11 Docker 安装说明

## 📋 Windows 11 特定说明

Windows 11 对 Docker 的支持与 Windows 10 略有不同，本文档提供 Windows 11 特定的安装指导。

---

## ✅ Windows 11 要求

- **操作系统**: Windows 11 21H2 或更高版本（Build 22000+）
- **架构**: 64位（x64）
- **内存**: 至少 4GB RAM（推荐 8GB 或更多）
- **虚拟化**: 需要在 BIOS 中启用虚拟化功能

---

## 🚀 安装步骤（Windows 11 优化）

### 方式1: 使用 Windows 11 优化脚本（推荐）

```powershell
# 以管理员身份运行PowerShell
powershell -ExecutionPolicy Bypass -File F:\BaiduNetdiskDownload\AT\AI_TEST\scripts\install\install_docker_windows_win11.ps1
```

**优化特性**:
- ✅ 自动检测 Windows 11
- ✅ 使用 `wsl --install` 方法（Windows 11 推荐）
- ✅ 更好的错误处理
- ✅ 针对 Windows 11 的优化

### 方式2: 使用一键安装脚本

一键安装脚本会自动检测 Windows 11 并使用优化版本：

```powershell
powershell -ExecutionPolicy Bypass -File F:\BaiduNetdiskDownload\AT\AI_TEST\scripts\setup\install_and_start.ps1
```

### 方式3: 手动安装（推荐用于生产环境）

#### 步骤1: 安装 WSL 2（Windows 11 通常已预装）

Windows 11 通常已预装 WSL 2，但如果没有：

```powershell
# 以管理员身份运行
wsl --install

# 或者只安装 WSL 2（不安装 Linux 发行版）
wsl --install --no-distribution

# 重启计算机（如需要）
```

#### 步骤2: 下载 Docker Desktop

1. **访问 Docker 官网**
   - 网址: https://www.docker.com/products/docker-desktop/
   - 点击 "Download for Windows"

2. **下载安装程序**
   - 文件名: `Docker Desktop Installer.exe`
   - 大小: 约500MB

#### 步骤3: 安装 Docker Desktop

1. **运行安装程序**
   - 双击 `Docker Desktop Installer.exe`
   - **重要**: 勾选 "Use WSL 2 instead of Hyper-V"（推荐）

2. **完成安装**
   - 等待安装完成（约5-10分钟）
   - 点击 "Close and restart"（如提示）

3. **启动 Docker Desktop**
   - 从开始菜单启动 Docker Desktop
   - 等待 Docker 引擎启动（约30-60秒）
   - 系统托盘会出现 Docker 图标

#### 步骤4: 验证安装

```powershell
# 检查 Docker 版本
docker --version

# 检查 Docker Compose 版本
docker-compose --version

# 运行测试容器
docker run hello-world
```

---

## 🔧 Windows 11 特定配置

### 1. WSL 2 后端（推荐）

Windows 11 推荐使用 WSL 2 后端而非 Hyper-V：

- ✅ **性能更好**
- ✅ **资源占用更少**
- ✅ **与 Windows 11 集成更好**

在 Docker Desktop 安装时选择 "Use WSL 2 instead of Hyper-V"。

### 2. 虚拟化设置

确保 BIOS 中启用了虚拟化：

1. **进入 BIOS 设置**（重启时按 F2/F12/Del 等）
2. **查找虚拟化选项**（通常在 "Advanced" 或 "CPU Configuration"）
3. **启用以下选项**:
   - Intel VT-x（Intel 处理器）
   - AMD-V（AMD 处理器）
   - Virtualization Technology

### 3. Windows 功能

确保以下 Windows 功能已启用：

```powershell
# 检查 WSL
wsl --status

# 检查虚拟化
Get-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V-All
```

---

## ⚠️ Windows 11 常见问题

### Q1: Docker Desktop 无法启动

**错误**: "Docker Desktop failed to start"

**解决**:
1. **检查 WSL 2**:
   ```powershell
   wsl --status
   ```
   确保 WSL 2 是默认版本

2. **重启 WSL**:
   ```powershell
   wsl --shutdown
   ```
   然后重新启动 Docker Desktop

3. **检查虚拟化**:
   - 打开任务管理器
   - 查看 "性能" → "CPU"
   - 确保 "虚拟化" 显示为 "已启用"

### Q2: WSL 2 安装失败

**错误**: "WSL 2 installation is incomplete"

**解决**:
```powershell
# Windows 11: 使用新方法
wsl --install --no-distribution

# 如果失败，手动启用功能
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart

# 重启计算机
```

### Q3: Docker 命令不可用

**错误**: "docker: command not found"

**解决**:
1. **确保 Docker Desktop 正在运行**
   - 检查系统托盘中的 Docker 图标
   - 确保图标显示为 "运行中"

2. **重启 PowerShell**
   - 关闭当前 PowerShell 窗口
   - 重新打开 PowerShell（Docker Desktop 需要刷新 PATH）

3. **检查 PATH 环境变量**
   ```powershell
   $env:PATH -split ';' | Select-String docker
   ```

### Q4: 性能问题

**问题**: Docker 容器运行缓慢

**解决**:
1. **使用 WSL 2 后端**（而非 Hyper-V）
2. **分配更多资源**:
   - 打开 Docker Desktop 设置
   - Resources → Advanced
   - 增加 CPU 和内存分配

3. **启用文件共享**:
   - Settings → Resources → File Sharing
   - 添加项目目录

---

## 📊 Windows 11 vs Windows 10

| 特性 | Windows 10 | Windows 11 |
|------|------------|------------|
| **WSL 2** | 需要手动安装 | 通常已预装 |
| **虚拟化** | Hyper-V 或 WSL 2 | 推荐 WSL 2 |
| **安装方法** | `dism.exe` | `wsl --install` |
| **性能** | 良好 | 更好 |
| **兼容性** | 好 | 优秀 |

---

## 🎯 最佳实践（Windows 11）

1. ✅ **使用 WSL 2 后端**（而非 Hyper-V）
2. ✅ **确保虚拟化已启用**
3. ✅ **分配足够的资源**（至少 4GB RAM）
4. ✅ **定期更新 Docker Desktop**
5. ✅ **使用 Windows 11 优化的脚本**

---

## 📚 相关文档

- **Docker安装指南**: [Docker安装指南.md](Docker安装指南.md)
- **手动安装指南**: [Docker手动安装指南.md](Docker手动安装指南.md)
- **Jenkins快速启动**: [Jenkins快速启动指南.md](Jenkins快速启动指南.md)

---

## ✅ 安装完成后

安装完成后，可以：

```powershell
# 启动 Jenkins
cd F:\BaiduNetdiskDownload\AT\AI_TEST
docker-compose up -d jenkins

# 访问 Jenkins
# 浏览器打开: http://localhost:8080
```

---

**更新时间**: 2025-10-31  
**Windows 11 版本**: 21H2+  
**Docker Desktop 版本**: 4.0+
