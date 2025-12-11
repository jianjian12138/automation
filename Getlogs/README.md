# 日志提取工具

## 功能介绍

本工具用于从远程服务器的ERP日志文件中提取信息，并生成MD格式报告。

### 主要功能

1. **远程服务器连接**：通过SSH连接到远程服务器
2. **日志文件浏览**：浏览ERP目录下的服务和日志文件
3. **SQL提取与执行**：
   - 从日志中提取SQL语句
   - 替换参数占位符
   - 连接PostgreSQL数据库执行SQL
   - 生成包含执行结果的MD报告
4. **错误信息提取**：提取日志中的错误信息
5. **异常信息提取**：提取日志中的异常信息
6. **自定义提取**：使用正则表达式自定义提取规则

## 目录结构

```
getlogs/
├── get_logs_tool.py      # 核心日志提取类
├── browse_logs.py        # 日志文件浏览工具
├── execute_sql_from_log.py  # SQL提取与执行工具
├── custom_extract.py     # 自定义提取工具
├── requirements.txt      # 依赖包列表
└── README.md             # 工具说明文档
```

## 安装依赖

```bash
pip install -r requirements.txt
```

## 使用说明

### 1. 图形界面工具（推荐）

全新的PyQt5图形界面工具，整合了所有功能，操作更直观便捷。

```bash
python logs_tool_gui.py
```

**功能特性：**
- 服务器连接配置（支持密码认证和密钥认证）
- 可视化服务和日志文件浏览
- SQL语句提取与报告生成
- 自定义正则表达式提取
- 报告管理（查看、删除）
- 多标签页设计，操作流畅

### 2. 命令行工具

#### 2.1 日志文件浏览工具

用于浏览远程服务器上的ERP服务和日志文件。

```bash
python browse_logs.py
```

#### 2.2 SQL提取与执行工具

从日志中提取SQL语句，执行并生成报告。支持命令行模式和交互式模式。

##### 命令行模式

```bash
python execute_sql_from_log.py --host <服务器地址> --service <服务名称> --log <日志文件名> --action sql --start-time "YYYY-MM-DD HH:MM:SS" [--end-time "YYYY-MM-DD HH:MM:SS"]
```

##### 交互式模式

```bash
python execute_sql_from_log.py
```

#### 2.3 自定义提取工具

使用正则表达式从日志中提取自定义信息。

```bash
python custom_extract.py
```

### 4. 直接使用核心类

```python
from get_logs_tool import LogExtractor

# 初始化提取器
extractor = LogExtractor(
    host='192.168.2.170',
    port=22,
    username='root',
    password='password'
)

# 连接服务器
extractor.connect()

# 执行提取操作
# ...

# 断开连接
extractor.disconnect()
```

## 报告格式

工具生成的MD报告包含以下内容：

- 报告标题和生成时间
- 提取的SQL语句/错误/异常/自定义信息
- 执行结果（如果是SQL）
- 上下文信息

## 注意事项

1. 确保SSH连接参数正确
2. 确保目标日志文件存在且有读取权限
3. 执行SQL时确保数据库连接参数正确
4. 自定义正则表达式时注意语法正确性
5. 工具会自动清理临时文件

## 示例

### 提取SQL并执行

```bash
python execute_sql_from_log.py --host 192.168.2.170 --service pas-service --log pasService-1.log --start-time "2025-12-02 18:25:00" --output sql_results.md
```

### 提取错误信息

```bash
# 使用核心工具提取错误信息
python -c "from get_logs_tool import LogExtractor; extractor = LogExtractor(host='192.168.2.170', password='zhongzao123'); extractor.connect(); extractor.download_log('pas-service', 'pasService-1.log', 'temp.log'); f = open('temp.log', 'r', encoding='utf-8'); content = f.read(); f.close(); errors = extractor.extract_errors(content); extractor.generate_md_report(errors, 'error', 'error_results.md'); extractor.disconnect(); import os; os.remove('temp.log')"

### 提取异常信息

```bash
# 使用核心工具提取异常信息
python -c "from get_logs_tool import LogExtractor; extractor = LogExtractor(host='192.168.2.170', password='zhongzao123'); extractor.connect(); extractor.download_log('pas-service', 'pasService-1.log', 'temp.log'); f = open('temp.log', 'r', encoding='utf-8'); content = f.read(); f.close(); exceptions = extractor.extract_exceptions(content); extractor.generate_md_report(exceptions, 'exception', 'exception_results.md'); extractor.disconnect(); import os; os.remove('temp.log')"


### 自定义提取

```bash
python custom_extract.py
```

## 版本说明

- v1.0.0: 初始版本，包含基本功能
