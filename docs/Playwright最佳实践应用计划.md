# Playwright 最佳实践应用计划

基于《Playwright 测试最佳实践》文章，本文档规划如何将这些实践应用到我们的测试框架中。

## 📋 文章要点总结

### 1. 按风险级别组织测试（@smoke / @full）
### 2. 使用稳定的定位策略（优先 data-testid）
### 3. 充分利用自动等待机制（避免 waitForTimeout）
### 4. 用 fixtures 管理认证（storageState）
### 5. 通过 API 准备测试数据（不走 UI）
### 6. 控制网络请求（Mock 外部依赖）
### 7. 视觉回归测试（使用 mask）
### 8. Trace 和视频（只在必要时记录）
### 9. 合理设置并发数（有策略地分片）
### 10. 按用户场景组织（helper 函数）
### 11. 让不稳定性可见（test.slow/fixme/fail）
### 12. 优化测试报告（标准化命名）

---

## 🎯 框架改进计划

### ✅ 已实现的功能

1. **自动等待机制** - 框架已支持 `$wait_element_visibility`、`$wait_element_clickable` 等
2. **公共方法提取** - 已有 `common_login.yaml` 等公共步骤
3. **变量引用** - 使用 `&variable` 避免参数解析问题
4. **步骤优化** - 转换工具会自动优化步骤

### 🔄 需要改进的功能

#### 优先级 1：立即改进（高价值、低难度）

##### 1.1 支持测试标签（@smoke / @full）

**目标**: 支持按风险级别组织测试

**实现方案**:
- 在 YAML 用例中添加 `tags` 字段
- 在执行器支持按标签过滤
- 更新转换工具，根据用例名称/内容自动添加标签

**示例**:
```yaml
case_name: 销售合同
case_code: SalesContract
priority: 1
tags:
  - smoke  # 冒烟测试
  - critical  # 关键流程
```

**执行命令**:
```bash
# 只运行冒烟测试
python main.py --tags smoke --type web

# 运行完整回归测试
python main.py --tags full --type web
```

---

##### 1.2 优化选择器策略（优先 data-testid）

**目标**: 生成更稳定的选择器

**实现方案**:
- 更新 `playwright_codegen_to_yaml.py`，优先识别 `data-testid`
- 如果录制代码中有 `getByTestId`，转换为 `t,testid_value` 格式
- 在选择器变量中标注优先级

**当前问题**:
```yaml
# 当前生成的选择器（不稳定）
button_登录: x,//button[contains(text(),"登录")]
```

**改进后**:
```yaml
# 优先使用 data-testid（如果存在）
button_登录: t,login-button  # t, 表示 testid
# 或
button_登录: x,//button[@data-testid="login-button"]
```

**转换工具改进**:
- 检测 `page.getByTestId()` 调用
- 生成 `t,testid` 格式的选择器
- 在选择器变量中标注优先级

---

##### 1.3 减少硬编码 sleep，使用智能等待

**目标**: 避免不必要的 `sleep`，使用条件等待

**当前问题**:
```yaml
- action: $navigate(&url)
  sleep: 2  # 硬编码等待
```

**改进方案**:
- 使用 `$wait_for_network_idle()` 替代固定 sleep
- 使用 `$wait_for_url()` 等待页面加载完成
- 只在必要时使用 sleep（如动画）

**改进后**:
```yaml
- action: $navigate(&url)
- action: $wait_for_network_idle()
```

---

#### 优先级 2：中期改进（高价值、中难度）

##### 2.1 支持 storageState（登录状态管理）

**目标**: 避免每个测试都重新登录

**实现方案**:
- 创建全局登录脚本，保存 storageState
- 在执行器中支持加载 storageState
- 更新 `common_login.yaml` 支持"跳过已登录"逻辑

**工作流程**:
```bash
# 1. 生成登录状态（一次性）
python tools/setup_auth_state.py --url "http://..." --username "..." --password "..."

# 2. 测试用例自动使用已保存的状态
python main.py cases/web/6000/SalesContract.yaml --type web --use-auth-state
```

**YAML 用例改进**:
```yaml
case_name: 销售合同
case_code: SalesContract
priority: 1
auth:
  use_storage_state: true  # 使用已保存的登录状态
  # 或
  skip_if_logged_in: true  # 如果已登录则跳过登录步骤
```

---

##### 2.2 支持 API 数据准备

**目标**: 通过 API 快速准备测试数据，UI 只验证结果

**实现方案**:
- 在 YAML 用例中添加 `setup` 步骤（API 调用）
- 支持在测试前通过 HTTP 请求准备数据
- UI 测试只验证结果

**示例**:
```yaml
case_name: 销售合同
case_code: SalesContract
priority: 1

# 测试前准备数据（API）
setup:
  - step_name: 创建测试订单
    actions:
      - action: $http_post(/api/test/createOrder, {"items": ["sku-123"], "status": "pending"})
        save_as: order_id

# UI 测试只验证结果
steps:
  - step_name: 验证订单状态
    actions:
      - action: $navigate(&order_url)
      - action: $wait_text_in_element(&status_element, "待处理")
```

---

##### 2.3 支持网络请求 Mock（HAR 文件）

**目标**: Mock 不可控的外部依赖

**实现方案**:
- 在执行器中支持加载 HAR 文件
- 支持 `routeFromHAR` 功能
- 在用例配置中指定 HAR 文件路径

**示例**:
```yaml
case_name: 销售合同
case_code: SalesContract
priority: 1

# Mock 配置
mocks:
  - har_file: fixtures/catalog.har
    url_pattern: "**/catalog**"
    update: false  # 只回放，不更新
```

---

#### 优先级 3：长期改进（中价值、高难度）

##### 3.1 支持视觉回归测试

**目标**: 使用 mask 处理动态区域的视觉测试

**实现方案**:
- 添加 `$screenshot` 关键字，支持 mask 参数
- 在用例中配置需要 mask 的元素
- 支持阈值和像素差异配置

**示例**:
```yaml
- step_name: 验证购物车页面
  actions:
    - action: $screenshot(cart.png, mask=["[data-testid=clock]"], max_diff_pixels=120)
```

---

##### 3.2 优化 Trace 和视频记录

**目标**: 只在失败时记录，提高执行速度

**实现方案**:
- 在执行器中支持条件记录
- 配置 `trace: on-first-retry`
- 配置 `video: retain-on-failure`
- 配置 `screenshot: only-on-failure`

**配置示例**:
```yaml
# config.yaml
playwright:
  trace: on-first-retry
  video: retain-on-failure
  screenshot: only-on-failure
```

---

##### 3.3 支持测试并发和分片

**目标**: 合理设置并发数，支持测试分片

**实现方案**:
- 在执行器中支持 worker 配置
- 支持按文件/标签分片
- 支持 CI 环境自动调整并发数

**配置示例**:
```yaml
# config.yaml
execution:
  workers: 4  # 并发数
  shard: 1/2  # 分片（1/2 表示分成2片，这是第1片）
```

---

##### 3.4 优化测试报告

**目标**: 标准化产物命名，突出关键信息

**实现方案**:
- 截图按测试场景命名
- 报告包含失败步骤、截图、trace、网络请求
- 支持 HTML 报告和 JUnit 报告

**改进**:
- 截图命名: `artifacts/{test_name}-{step_name}.png`
- Trace 文件: `artifacts/{test_name}-trace.zip`
- 报告路径: `reports/{timestamp}-{test_name}.html`

---

##### 3.5 支持测试不稳定性标记

**目标**: 让不稳定性可见，便于追踪和修复

**实现方案**:
- 在 YAML 用例中添加 `unstable` 标记
- 支持 `slow`、`fixme`、`expected_fail` 等标记
- 在报告中突出显示不稳定的测试

**示例**:
```yaml
case_name: 信用卡3DS支付流程
case_code: CreditCard3DS
priority: 2
unstable:
  slow: true  # 已知较慢
  fixme: true  # 主分支临时跳过
  # 或
  expected_fail: true  # 仅CI环境标记为预期失败
```

---

## 📝 实施步骤

### 阶段 1：立即改进（1-2周）

1. ✅ 支持测试标签（@smoke / @full）
2. ✅ 优化选择器策略（优先 data-testid）
3. ✅ 减少硬编码 sleep

### 阶段 2：中期改进（2-4周）

1. ✅ 支持 storageState（登录状态管理）
2. ✅ 支持 API 数据准备
3. ✅ 支持网络请求 Mock

### 阶段 3：长期改进（1-2月）

1. ✅ 支持视觉回归测试
2. ✅ 优化 Trace 和视频记录
3. ✅ 支持测试并发和分片
4. ✅ 优化测试报告
5. ✅ 支持测试不稳定性标记

---

## 🔧 技术实现细节

### 1. 选择器优先级策略

```python
# 选择器优先级（从高到低）
SELECTOR_PRIORITY = [
    'data-testid',      # t,testid
    'id',               # s,#id
    'name',             # s,[name="xxx"]
    'role+name',        # r,button,登录
    'text',             # x,//*[contains(text(),"xxx")]
    'css',              # s,.class
    'xpath',            # x,//xxx
]
```

### 2. 智能等待策略

```python
# 等待策略映射
WAIT_STRATEGIES = {
    'navigate': 'wait_for_network_idle',
    'click': 'wait_element_clickable',
    'input': 'wait_element_visible',
    'assert': 'wait_text_visible',
}
```

### 3. 标签自动识别

```python
# 根据用例名称/内容自动添加标签
def auto_detect_tags(case_name, case_steps):
    tags = []
    if '登录' in case_name or 'login' in case_name.lower():
        tags.append('smoke')
        tags.append('critical')
    if '支付' in case_name or 'payment' in case_name.lower():
        tags.append('critical')
    if len(case_steps) > 20:
        tags.append('full')
    return tags
```

---

## 📚 相关文档

- [Playwright 官方文档](https://playwright.dev/python/)
- [测试用例录制和转换工具使用说明.md](../tools/测试用例录制和转换工具使用说明.md)
- [框架使用指南](./快速开始.md)

---

**最后更新**: 2025-11-13

