# Skyvern 集成使用指南

## 📋 概述

Skyvern 是一个 AI 驱动的浏览器自动化工具，我们借鉴其核心思路，将其智能元素定位能力集成到我们的框架中，作为固定选择器的**备选方案**。

**核心价值**:
- 🤖 当固定选择器失败时，自动使用 AI 智能定位
- 🎯 提高测试的容错性和稳定性
- 🔄 混合策略：固定选择器优先，AI 定位备选

---

## 🚀 快速开始

### 1. 安装依赖

```bash
# 安装 OpenAI SDK（如果使用 OpenAI）
pip install openai

# 或安装 Anthropic SDK（如果使用 Anthropic）
pip install anthropic
```

### 2. 配置 API 密钥

```bash
# 设置环境变量
export OPENAI_API_KEY="your-api-key"
# 或
export ANTHROPIC_API_KEY="your-api-key"
```

或在 `config/config.yaml` 中配置：
```yaml
llm:
  provider: openai  # 或 anthropic
  api_key: your-api-key
  model: gpt-4o-mini  # 或 claude-3-haiku
```

---

## 💡 使用方式

### 方式 1：在 YAML 用例中使用 AI 定位

```yaml
case_name: 登录测试
case_code: LoginTest
tags:
  - smoke
  - critical
steps:
  - step_name: 点击登录按钮（AI 定位）
    actions:
      - action: $ai_locate_element("登录按钮", element_type="button")
        save_as: login_button_selector
      - action: $click(&login_button_selector)
```

### 方式 2：使用智能点击（推荐）

```yaml
case_name: 登录测试
case_code: LoginTest
tags:
  - smoke
  - critical
steps:
  - step_name: 智能点击登录按钮
    actions:
      # 优先使用固定选择器，失败时自动使用 AI 定位
      - action: $smart_click(&button_登录, description="登录按钮")
```

### 方式 3：作为备选方案

```yaml
case_name: 登录测试
case_code: LoginTest
steps:
  - step_name: 点击登录按钮
    actions:
      - action: $click(&button_登录)
        # 如果失败，自动尝试 AI 定位
        fallback:
          - action: $ai_locate_element("登录按钮")
            save_as: ai_selector
          - action: $click(&ai_selector)
```

---

## 🎯 最佳实践

### 1. 优先使用固定选择器

```yaml
# ✅ 推荐：固定选择器优先
- action: $smart_click(&button_登录, description="登录按钮")

# ❌ 不推荐：直接使用 AI 定位（成本高，不稳定）
- action: $ai_locate_element("登录按钮")
```

### 2. AI 定位作为备选方案

```yaml
# ✅ 推荐：固定选择器 + AI 备选
- action: $smart_click(&button_登录, description="登录按钮")

# 或
- action: $click(&button_登录)
  fallback:
    - action: $ai_locate_element("登录按钮")
```

### 3. 提供清晰的元素描述

```yaml
# ✅ 好的描述
- action: $ai_locate_element("登录按钮", element_type="button", context="页面顶部导航栏")

# ❌ 模糊的描述
- action: $ai_locate_element("按钮")
```

---

## 📊 性能与成本

### 成本考虑

- **固定选择器**: 无成本 ✅
- **AI 定位**: 需要 LLM API 调用，有成本 ⚠️

**建议**:
- 只在固定选择器失败时使用 AI 定位
- 使用成本较低的模型（如 `gpt-4o-mini`）
- 缓存 AI 定位结果（如果元素位置稳定）

### 性能考虑

- **固定选择器**: 快速（< 100ms）✅
- **AI 定位**: 较慢（1-3秒，取决于 LLM 响应时间）⚠️

**建议**:
- 优先使用固定选择器
- AI 定位作为最后的备选方案

---

## 🔧 配置选项

### 环境变量

```bash
# LLM 提供商
LLM_PROVIDER=openai  # 或 anthropic

# API 密钥
OPENAI_API_KEY=your-key
ANTHROPIC_API_KEY=your-key

# 模型选择
LLM_MODEL=gpt-4o-mini  # 或 claude-3-haiku
```

### 配置文件

在 `config/config.yaml` 中：

```yaml
llm:
  # 是否启用 AI 定位
  enable_ai_locator: true
  
  # LLM 提供商
  provider: openai  # openai / anthropic / gemini
  
  # API 密钥
  api_key: ${OPENAI_API_KEY}
  
  # 模型选择
  model: gpt-4o-mini
  
  # 是否启用缓存
  enable_cache: true
  cache_dir: .ai_locator_cache
```

---

## 📝 使用示例

### 示例 1：处理动态内容

```yaml
case_name: 动态内容测试
case_code: DynamicContentTest
steps:
  - step_name: 点击动态生成的按钮
    actions:
      # 按钮 ID 是动态的，使用 AI 定位
      - action: $smart_click("s,#dynamic-button-123", description="提交按钮")
```

### 示例 2：处理复杂页面结构

```yaml
case_name: 复杂页面测试
case_code: ComplexPageTest
steps:
  - step_name: 在复杂结构中定位元素
    actions:
      - action: $ai_locate_element(
          "用户信息编辑按钮",
          element_type="button",
          context="用户资料卡片中"
        )
        save_as: edit_button
      - action: $click(&edit_button)
```

### 示例 3：处理 iframe

```yaml
case_name: iframe 测试
case_code: IframeTest
steps:
  - step_name: 在 iframe 中定位元素
    actions:
      - action: $switch_to_iframe(&iframe_selector)
      - action: $smart_click("s,.button", description="iframe 中的确认按钮")
```

---

## ⚠️ 注意事项

### 1. 成本控制

- ✅ 只在必要时使用 AI 定位
- ✅ 使用成本较低的模型
- ✅ 启用缓存减少重复调用

### 2. 稳定性

- ✅ 固定选择器优先
- ✅ AI 定位作为备选
- ✅ 记录 AI 定位结果，便于调试

### 3. 可维护性

- ✅ 提供清晰的元素描述
- ✅ 记录 AI 定位的决策过程
- ✅ 定期审查和优化选择器

---

## 🔍 调试技巧

### 1. 查看 AI 定位日志

```bash
# 启用调试日志
export LOG_LEVEL=DEBUG

# 运行测试
python main.py cases/web/6000/test.yaml --type web
```

### 2. 保存 AI 定位结果

AI 定位结果会自动保存到日志中，格式：
```
[INFO] AI 定位成功: 登录按钮 -> s,.login-button
```

### 3. 手动测试 AI 定位

```python
from tools.skyvern_integration import SkyvernIntegration
from keywords.playwright_keywords import get_playwright_driver

driver = get_playwright_driver()
integration = SkyvernIntegration(driver.page)

# 测试定位
selector = integration.locate_element("登录按钮", element_type="button")
print(f"定位结果: {selector}")
```

---

## 📚 相关文档

- [Skyvern工具分析与集成建议.md](./Skyvern工具分析与集成建议.md) - 详细分析
- [Playwright最佳实践总结.md](./Playwright最佳实践总结.md) - 最佳实践
- [改进实施总结.md](./改进实施总结.md) - 框架改进总结

---

## 🎯 总结

**Skyvern 集成策略**:
- ✅ 作为**备选方案**，不是主要方案
- ✅ 固定选择器优先，AI 定位备选
- ✅ 提高测试容错性，但不增加不必要的成本
- ✅ 保持框架的稳定性和可维护性

**适用场景**:
- 固定选择器失败时
- 处理动态内容和复杂页面
- 提高测试的容错性

**不适用场景**:
- 所有元素都使用 AI 定位（成本高）
- 对稳定性要求极高的场景（固定选择器更可靠）

---

**最后更新**: 2025-11-13

