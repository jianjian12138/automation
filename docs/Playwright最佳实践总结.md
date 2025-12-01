# Playwright 最佳实践总结与应用

基于《Playwright 测试最佳实践》文章，本文档总结关键要点并说明如何应用到我们的框架。

## 📖 文章核心要点

### 1. 按风险级别组织测试（@smoke / @full）
**要点**: 高风险场景（登录、下单、支付）需要快速、精准覆盖，低风险 UI 细节可以放到完整回归测试中。

**应用**: 
- ✅ 已在改进计划中规划
- 用例中添加 `tags: [smoke, critical]` 字段
- 执行时支持 `--tags smoke` 过滤

---

### 2. 使用稳定的定位策略（优先 data-testid）
**要点**: 不稳定的选择器是测试不稳定的主要原因。优先使用 `data-testid` 属性。

**应用**: 
- ✅ **已实现**: 转换工具已支持 `getByTestId()`，生成 `t,testid` 格式
- ✅ **已优化**: `_role_to_selector` 方法优先使用 `r,role,name` 格式（Playwright 推荐）
- 选择器优先级: `data-testid` > `id` > `role+name` > `text` > `css` > `xpath`

**示例**:
```yaml
# 最佳实践：使用 data-testid
button_登录: t,login-button  # t, 表示 testid

# 次优：使用 role
button_登录: r,button,登录  # r, 表示 role

# 避免：仅使用文本
button_登录: x,//button[contains(text(),"登录")]
```

---

### 3. 充分利用自动等待机制（避免 waitForTimeout）
**要点**: 手动写 `waitForTimeout` 是代码异味。应该依赖 Playwright 的自动等待和 web-first 断言。

**应用**:
- ✅ **已实现**: 框架支持 `$wait_element_visibility`、`$wait_element_clickable` 等
- ✅ **已优化**: 转换工具生成智能等待，减少硬编码 `sleep`
- ⚠️ **待改进**: 进一步减少固定 `sleep`，使用条件等待

**改进前**:
```yaml
- action: $navigate(&url)
  sleep: 2  # 硬编码等待
```

**改进后**:
```yaml
- action: $navigate(&url)
- action: $wait_for_network_idle()  # 智能等待
```

---

### 4. 用 fixtures 管理认证（storageState）
**要点**: 用 `storageState` 保存登录态，别在每个测试里都重新登录。

**应用**:
- 📋 **已规划**: 在改进计划中
- 创建全局登录脚本，保存 `storageState`
- 用例配置 `auth.use_storage_state: true`

**工作流程**:
```bash
# 1. 生成登录状态（一次性）
python tools/setup_auth_state.py

# 2. 测试用例自动使用已保存的状态
python main.py cases/web/6000/SalesContract.yaml --use-auth-state
```

---

### 5. 通过 API 准备测试数据（不走 UI）
**要点**: UI 操作慢而且脆弱。优先用后端接口准备测试数据，然后在 UI 验证结果。

**应用**:
- 📋 **已规划**: 在改进计划中
- 用例中添加 `setup` 步骤（API 调用）
- UI 测试只验证结果

**示例**:
```yaml
setup:
  - action: $http_post(/api/test/createOrder, {"items": ["sku-123"]})
    save_as: order_id

steps:
  - action: $navigate(/orders/&order_id)
  - action: $wait_text_in_element(&status, "待处理")
```

---

### 6. 控制网络请求（Mock 外部依赖）
**要点**: 第三方接口会带来不确定性。用 HAR 文件固化它们。

**应用**:
- 📋 **已规划**: 在改进计划中
- 支持 `routeFromHAR` 功能
- 用例配置 `mocks` 字段

---

### 7. 视觉回归测试（使用 mask）
**要点**: 视觉测试有用但会产生很多无用差异。明确设置阈值和 mask 动态区域。

**应用**:
- 📋 **已规划**: 在改进计划中
- 添加 `$screenshot` 关键字，支持 `mask` 参数

---

### 8. Trace 和视频（只在必要时记录）
**要点**: Playwright trace 在测试失败时能救命，但全程录制浪费时间和存储。

**应用**:
- 📋 **已规划**: 在改进计划中
- 配置 `trace: on-first-retry`
- 配置 `video: retain-on-failure`
- 配置 `screenshot: only-on-failure`

---

### 9. 合理设置并发数（有策略地分片）
**要点**: 更高的并发数不总是意味着更快的执行。

**应用**:
- 📋 **已规划**: 在改进计划中
- 支持 worker 配置
- 支持按文件/标签分片

---

### 10. 按用户场景组织（helper 函数）
**要点**: Page Object 可能变得臃肿难维护。试试"剧本式"的 helper 函数。

**应用**:
- ✅ **已实现**: 已有 `common_login.yaml` 等公共步骤
- ✅ **已实现**: 支持 `common_step` 引用
- 继续扩展公共步骤库

---

### 11. 让不稳定性可见（test.slow/fixme/fail）
**要点**: 别掩盖不稳定的测试。用注解标记出来。

**应用**:
- 📋 **已规划**: 在改进计划中
- 用例中添加 `unstable` 标记
- 支持 `slow`、`fixme`、`expected_fail` 等

---

### 12. 优化测试报告（标准化命名）
**要点**: 好的报告能快速终结争论。标准化产物命名，突出关键信息。

**应用**:
- 📋 **已规划**: 在改进计划中
- 截图命名: `artifacts/{test_name}-{step_name}.png`
- 报告包含失败步骤、截图、trace、网络请求

---

## 🎯 立即可以应用的改进

### 1. 优化选择器生成（✅ 已完成）

转换工具已优化，优先识别 `getByTestId()` 并生成 `t,testid` 格式。

### 2. 使用 role 定位（✅ 已完成）

`_role_to_selector` 方法已更新，优先使用 `r,role,name` 格式。

### 3. 减少硬编码 sleep（🔄 进行中）

逐步将固定 `sleep` 替换为智能等待。

---

## 📚 相关文档

- [Playwright最佳实践应用计划.md](./Playwright最佳实践应用计划.md) - 详细改进计划
- [测试用例录制和转换工具使用说明.md](../tools/测试用例录制和转换工具使用说明.md)
- [框架使用指南](./快速开始.md)

---

**最后更新**: 2025-11-13

