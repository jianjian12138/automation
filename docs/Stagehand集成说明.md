# Stagehand AI 驱动浏览器自动化集成说明

## 📋 概述

Stagehand 是一个 AI 驱动的浏览器自动化框架，基于 Playwright，提供自然语言驱动的测试能力。本框架已集成 Stagehand 功能，可以显著提升 Web 自动化的智能化水平。

**参考**: [Stagehand GitHub](https://github.com/browserbase/stagehand)

## 🎯 核心特性

### 1. 自然语言驱动的操作（act）
- 使用自然语言描述操作，无需精确的选择器
- 示例：`点击登录按钮`、`输入用户名test`、`访问订单页面`

### 2. AI Agent 多步骤任务（agent）
- 执行复杂的多步骤任务
- 示例：`登录并查看订单列表`、`填写表单并提交`

### 3. 智能数据提取（extract）
- 从页面提取结构化数据
- 示例：`提取用户名和邮箱`、`提取订单列表`

### 4. 自动缓存和自我修复
- 自动缓存重复操作，提高执行速度
- 当页面变化时，自动调用 AI 进行修复

## 🚀 快速开始

### 1. 安装依赖

```bash
# 确保已安装 Playwright
pip install playwright
playwright install chromium

# Stagehand 集成已包含在框架中，无需额外安装
```

### 2. 配置 API 密钥（可选）

如果需要使用 AI 功能，需要配置 API 密钥：

```bash
# 方式1: 环境变量
export OPENAI_API_KEY=your_api_key
# 或
export ANTHROPIC_API_KEY=your_api_key

# 方式2: 在代码中传递
stagehand = driver.get_stagehand(api_key="your_api_key")
```

**注意**: 如果不提供 API 密钥，框架会使用基于规则的智能解析，仍然可以工作，但功能有限。

### 3. 在测试用例中使用

#### 方式1: 使用关键字函数（推荐）

```yaml
case_name: Stagehand 测试示例
case_code: StagehandTest
steps:
  - step_name: 访问页面
    action: $navigate(http://example.com)
    sleep: 2

  - step_name: 使用 AI 点击按钮
    action: $ai_act(点击登录按钮)
    sleep: 1

  - step_name: 使用 AI 输入文本
    action: $ai_act(输入用户名test)
    sleep: 1

  - step_name: 使用 AI Agent 执行任务
    action: $ai_agent(登录并查看首页)
    sleep: 3

  - step_name: 使用 AI 提取数据
    action: $ai_extract(提取用户名和邮箱)
    save_as: user_info
```

#### 方式2: 在 Python 代码中使用

```python
from core.playwright_driver import PlaywrightDriver
from core.stagehand_integration import StagehandIntegration

# 创建驱动
driver = PlaywrightDriver()

# 访问页面
driver.navigate("http://example.com")

# 获取 Stagehand 集成
stagehand = driver.get_stagehand(api_key="your_api_key")

# 执行自然语言操作
result = stagehand.act("点击登录按钮")
print(result)

# 执行多步骤任务
result = stagehand.agent("登录并查看订单列表")
print(result)

# 提取数据
data = stagehand.extract("提取用户名和邮箱")
print(data)
```

## 📖 API 参考

### `ai_act(instruction: str, timeout: int = 30000)`

执行单个自然语言操作。

**参数**:
- `instruction`: 自然语言指令
- `timeout`: 超时时间（毫秒）

**示例**:
```yaml
action: $ai_act(点击登录按钮)
action: $ai_act(输入用户名test)
action: $ai_act(访问订单页面)
```

### `ai_agent(task: str, max_steps: int = 10, timeout: int = 60000)`

执行多步骤任务（AI Agent）。

**参数**:
- `task`: 任务描述
- `max_steps`: 最大步骤数
- `timeout`: 超时时间（毫秒）

**示例**:
```yaml
action: $ai_agent(登录并查看订单列表)
action: $ai_agent(填写表单并提交)
```

### `ai_extract(instruction: str, schema: Optional[Dict] = None, timeout: int = 30000)`

从页面提取结构化数据。

**参数**:
- `instruction`: 提取指令
- `schema`: 数据模式（可选）
- `timeout`: 超时时间（毫秒）

**示例**:
```yaml
action: $ai_extract(提取用户名和邮箱)
action: $ai_extract(提取订单列表)
```

## 💡 使用场景

### 1. 快速原型测试
当页面结构不熟悉时，使用自然语言快速编写测试：

```yaml
- step_name: 快速测试登录流程
  action: $ai_agent(登录系统并查看首页)
```

### 2. 动态页面处理
当页面元素经常变化时，AI 可以自动适应：

```yaml
- step_name: 智能点击按钮
  action: $ai_act(点击提交按钮)
  # AI 会自动找到按钮，即使选择器变化
```

### 3. 数据提取
从复杂页面中提取结构化数据：

```yaml
- step_name: 提取用户信息
  action: $ai_extract(提取用户名、邮箱和手机号)
  save_as: user_info
```

## ⚙️ 高级配置

### 自定义 AI 模型

```python
stagehand = StagehandIntegration(
    page=driver.page,
    api_key="your_api_key",
    model="gpt-4o-mini",  # 或 "claude-3-5-sonnet"
    enable_cache=True,
    cache_dir=".stagehand_cache"
)
```

### 启用缓存

```python
# 启用操作缓存，提高执行速度
stagehand = driver.get_stagehand(enable_cache=True)
```

### 清除缓存

```python
stagehand.clear_cache()
```

## 🔧 故障排除

### 1. API 密钥未配置

**问题**: `未提供 API 密钥，使用传统 Playwright 方式执行`

**解决**: 
- 设置环境变量 `OPENAI_API_KEY` 或 `ANTHROPIC_API_KEY`
- 或在代码中传递 `api_key` 参数

### 2. 指令解析失败

**问题**: `无法解析指令，请使用更明确的描述`

**解决**: 
- 使用更明确的自然语言描述
- 或提供 API 密钥以使用 AI 解析

### 3. 元素未找到

**问题**: `元素未找到`

**解决**: 
- 确保页面已完全加载
- 使用 `sleep` 等待页面加载
- 或使用 AI Agent 自动处理

## 📊 性能优化

### 1. 使用缓存
启用缓存可以显著提高重复操作的执行速度：

```python
stagehand = driver.get_stagehand(enable_cache=True)
```

### 2. 批量操作
使用 AI Agent 执行多步骤任务，而不是多次调用 `act`：

```yaml
# 推荐：使用 Agent
action: $ai_agent(登录并查看订单列表)

# 不推荐：多次调用
action: $ai_act(点击登录)
action: $ai_act(输入用户名)
action: $ai_act(输入密码)
action: $ai_act(点击提交)
```

## 🎓 最佳实践

1. **混合使用**: 对于熟悉的页面，使用传统 Playwright 关键字；对于不熟悉的页面，使用 Stagehand AI
2. **明确指令**: 使用清晰、具体的自然语言指令
3. **合理超时**: 根据操作复杂度设置合适的超时时间
4. **启用缓存**: 对于重复执行的测试，启用缓存以提高速度
5. **错误处理**: 使用 try-catch 处理 AI 操作可能的失败

## 📚 更多资源

- [Stagehand 官方文档](https://docs.stagehand.dev)
- [Stagehand GitHub](https://github.com/browserbase/stagehand)
- [Playwright 文档](https://playwright.dev/python/)

## 🔄 版本历史

- **v1.0** (2025-11-06): 初始集成 Stagehand 功能
  - 支持自然语言操作（act）
  - 支持 AI Agent 多步骤任务
  - 支持智能数据提取
  - 支持操作缓存

