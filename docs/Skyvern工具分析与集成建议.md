# Skyvern 工具分析与集成建议

## 📋 Skyvern 简介

**Skyvern** 是一个 AI 驱动的浏览器自动化工具，使用 LLM（大语言模型）来理解和执行浏览器操作。

**核心特性**:
- 🤖 AI 驱动的自动化（使用 LLM 理解页面并执行操作）
- 🔗 工作流支持（链式调用多个任务）
- 👁️ 视觉理解能力（理解页面内容和元素）
- 📝 自然语言指令（用自然语言描述任务）
- 🔄 自动工作流生成（"Observer" 模式）
- 🎯 智能元素定位（不依赖固定选择器）

**GitHub**: https://github.com/Skyvern-AI/skyvern
**Stars**: 17.6k ⭐
**License**: AGPL-3.0

---

## 🔍 Skyvern vs 我们的框架

### 相似点

| 特性 | Skyvern | 我们的框架 |
|------|---------|-----------|
| 底层技术 | Playwright | Playwright ✅ |
| 自动化能力 | ✅ | ✅ |
| 工作流支持 | ✅ | ✅ (YAML 用例) |
| AI 集成 | ✅ (核心功能) | ✅ (Stagehand 集成) |
| 自然语言 | ✅ | ✅ (部分支持) |

### 不同点

| 特性 | Skyvern | 我们的框架 |
|------|---------|-----------|
| **主要定位** | AI 驱动的 RPA/工作流自动化 | 测试自动化框架 |
| **用例定义** | 自然语言/API | YAML 结构化用例 ✅ |
| **选择器策略** | AI 智能定位 | 固定选择器 + 智能等待 ✅ |
| **稳定性** | 依赖 LLM 理解 | 更稳定（固定选择器）✅ |
| **成本** | 需要 LLM API 调用 | 无额外成本 ✅ |
| **可维护性** | 黑盒（AI 决策） | 透明（可查看选择器）✅ |

---

## 💡 Skyvern 对我们的框架的帮助

### ✅ 有价值的特性

#### 1. **智能元素定位**（高价值）

**Skyvern 的做法**:
- 使用 LLM 理解页面上下文
- 根据元素周围的文本和结构智能定位
- 不依赖固定的选择器

**对我们的帮助**:
- 可以集成到我们的框架中，作为**备选定位策略**
- 当固定选择器失败时，使用 AI 智能定位
- 提高测试的容错性

**集成方案**:
```python
# 在 playwright_keywords.py 中添加
def ai_locate_element(description: str, timeout: int = 30000):
    """
    使用 AI 智能定位元素（Skyvern 风格）
    
    :param description: 元素描述，如 "登录按钮"、"用户名输入框"
    :param timeout: 超时时间
    :return: 元素选择器
    """
    # 1. 先尝试固定选择器
    # 2. 如果失败，使用 LLM 理解页面并定位元素
    # 3. 返回找到的元素
```

---

#### 2. **工作流链式调用**（中价值）

**Skyvern 的做法**:
- 支持将多个任务链接在一起
- 前一个任务的输出作为下一个任务的输入

**对我们的帮助**:
- 我们的框架已经有 YAML 用例支持步骤链
- 可以借鉴 Skyvern 的工作流编排方式
- 支持更复杂的工作流场景

**当前状态**:
- ✅ 我们已经支持步骤链（YAML steps）
- ✅ 支持变量传递（save_as）
- ⚠️ 可以改进工作流的可视化和管理

---

#### 3. **自动工作流生成（"Observer" 模式）**（高价值）

**Skyvern 的做法**:
- 观察用户操作，自动生成工作流
- 类似于我们的录制功能，但更智能

**对我们的帮助**:
- 可以增强我们的录制工具
- 不仅录制操作，还理解操作意图
- 自动生成更智能的测试用例

**集成方案**:
```python
# 在 record_testcase.py 中添加 Observer 模式
def record_with_observer(url: str, task_description: str):
    """
    使用 AI Observer 模式录制工作流
    
    :param url: 目标 URL
    :param task_description: 任务描述，如 "登录并创建订单"
    :return: 生成的工作流 YAML
    """
    # 1. 启动浏览器
    # 2. 使用 LLM 理解任务
    # 3. 观察用户操作或自动执行
    # 4. 生成智能工作流
```

---

#### 4. **视觉理解能力**（中价值）

**Skyvern 的做法**:
- 使用视觉模型理解页面内容
- 不依赖 DOM 结构

**对我们的帮助**:
- 可以处理动态内容
- 处理 iframe 和复杂页面结构
- 作为备选方案，当 DOM 定位失败时使用

---

#### 5. **上下文理解**（中价值）

**Skyvern 的做法**:
- LLM 理解页面整体上下文
- 根据周围元素理解目标元素

**对我们的帮助**:
- 提高元素定位的准确性
- 减少因页面变化导致的测试失败
- 生成更稳定的测试用例

---

## 🎯 集成建议

### 优先级 1：智能元素定位（高价值、中难度）

**目标**: 当固定选择器失败时，使用 AI 智能定位

**实现方案**:
1. 在 `playwright_keywords.py` 中添加 `ai_locate_element()` 函数
2. 集成 Skyvern 的定位逻辑（或类似的 LLM 调用）
3. 作为备选方案，在固定选择器失败时自动使用

**示例**:
```yaml
# 用例中可以使用 AI 定位
- step_name: 点击登录按钮
  actions:
    - action: $ai_click("登录按钮")  # AI 智能定位
    # 或
    - action: $click(&button_登录)  # 固定选择器（优先）
      fallback: $ai_click("登录按钮")  # 失败时使用 AI
```

---

### 优先级 2：增强录制工具（高价值、高难度）

**目标**: 使用 AI 理解录制意图，生成更智能的用例

**实现方案**:
1. 在录制过程中，使用 LLM 理解用户意图
2. 自动优化选择器（使用 AI 推荐的稳定选择器）
3. 自动添加断言和验证步骤

**示例**:
```bash
# 使用 AI 增强的录制
python tools/record_testcase.py "创建订单" "http://..." --ai-enhanced

# AI 会：
# 1. 理解"创建订单"的意图
# 2. 自动识别关键步骤
# 3. 生成更稳定的选择器
# 4. 自动添加验证步骤
```

---

### 优先级 3：工作流可视化（中价值、中难度）

**目标**: 借鉴 Skyvern 的工作流 UI，提供可视化的工作流管理

**实现方案**:
1. 创建 Web UI 来可视化工作流
2. 支持拖拽式工作流构建
3. 支持工作流分析和优化

---

## ⚠️ 注意事项

### 1. **成本考虑**

- Skyvern 需要 LLM API 调用，会产生成本
- 建议作为**备选方案**，而不是主要方案
- 只在固定选择器失败时使用 AI 定位

### 2. **稳定性考虑**

- AI 驱动的定位可能不够稳定
- 建议保持固定选择器作为主要方案
- AI 定位作为容错机制

### 3. **可维护性考虑**

- AI 决策是"黑盒"，难以调试
- 建议记录 AI 的决策过程
- 提供调试模式，显示 AI 的定位逻辑

---

## 🔧 技术实现路径

### 方案 1：直接集成 Skyvern（简单但依赖外部服务）

```python
# 安装 Skyvern
pip install skyvern

# 在框架中集成
from skyvern import SkyvernClient

def ai_locate_element(description: str):
    client = SkyvernClient(api_key="...")
    # 使用 Skyvern 定位元素
    return client.locate_element(description)
```

**优点**: 快速集成，功能完整
**缺点**: 依赖外部服务，需要 API 密钥

---

### 方案 2：借鉴 Skyvern 的思路，自己实现（复杂但可控）

```python
# 自己实现 AI 定位逻辑
def ai_locate_element(description: str, page_screenshot: bytes):
    # 1. 使用 LLM 理解页面内容
    # 2. 根据描述定位元素
    # 3. 返回选择器
    pass
```

**优点**: 完全可控，无外部依赖
**缺点**: 开发工作量大

---

### 方案 3：混合方案（推荐）

```python
# 优先使用固定选择器，失败时使用 AI
def smart_click(selector: str, description: str = None):
    try:
        # 1. 先尝试固定选择器
        return click(selector)
    except Exception:
        if description:
            # 2. 固定选择器失败，使用 AI 定位
            ai_selector = ai_locate_element(description)
            return click(ai_selector)
        else:
            raise
```

**优点**: 兼顾稳定性和智能性
**缺点**: 需要实现 AI 定位逻辑

---

## 📊 对比总结

| 维度 | Skyvern | 我们的框架 | 建议 |
|------|---------|-----------|------|
| **主要用途** | RPA/工作流自动化 | 测试自动化 | 保持测试自动化定位 ✅ |
| **用例定义** | 自然语言 | YAML 结构化 | 保持 YAML，增强 AI 辅助 ✅ |
| **选择器策略** | AI 智能定位 | 固定选择器 | 混合方案：固定优先，AI 备选 ✅ |
| **稳定性** | 依赖 LLM | 更稳定 | 保持稳定性优先 ✅ |
| **成本** | 需要 LLM API | 无额外成本 | AI 作为可选功能 ✅ |
| **可维护性** | 黑盒 | 透明 | 保持透明，记录 AI 决策 ✅ |

---

## 🎯 推荐集成策略

### 短期（1-2周）

1. ✅ **研究 Skyvern 的定位算法**
   - 了解其如何理解页面上下文
   - 学习其元素定位策略

2. ✅ **添加 AI 定位作为备选方案**
   - 在 `playwright_keywords.py` 中添加 `ai_locate_element()`
   - 在固定选择器失败时自动使用

### 中期（1-2月）

3. ✅ **增强录制工具**
   - 集成 AI 理解录制意图
   - 自动优化选择器
   - 自动添加验证步骤

4. ✅ **工作流可视化**
   - 借鉴 Skyvern 的 UI 设计
   - 提供可视化的工作流管理

### 长期（3-6月）

5. ✅ **Observer 模式**
   - 实现自动工作流生成
   - 观察用户操作，自动生成用例

---

## 📚 相关资源

- [Skyvern GitHub](https://github.com/Skyvern-AI/skyvern)
- [Skyvern 文档](https://docs.skyvern.com/)
- [我们的 Stagehand 集成](../core/stagehand_integration.py)
- [Playwright 最佳实践](./Playwright最佳实践总结.md)

---

## 💭 结论

**Skyvern 对我们的框架有帮助，但需要谨慎集成**：

✅ **值得借鉴的特性**:
- 智能元素定位（作为备选方案）
- 工作流编排方式
- Observer 模式（自动生成工作流）

⚠️ **需要注意的问题**:
- 成本（LLM API 调用）
- 稳定性（AI 决策可能不稳定）
- 可维护性（AI 决策是黑盒）

🎯 **推荐方案**:
- 保持我们框架的**稳定性优先**原则
- 将 AI 定位作为**备选方案**（固定选择器失败时使用）
- 增强录制工具，使用 AI 理解意图并优化用例
- 借鉴工作流编排方式，但不完全依赖 AI

---

**最后更新**: 2025-11-13

