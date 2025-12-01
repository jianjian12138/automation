# 🤖 Cursor AI 使用指南

> 零成本、零配置，让AI为你生成测试用例

---

## 🎯 为什么选择Cursor？

### vs OpenAI API

| 对比项 | OpenAI API | Cursor |
|--------|-----------|---------|
| **成本** | $600/年 | **$0** |
| **配置** | 需要API Key | **无需配置** |
| **集成** | 需要编写代码 | **IDE内置** |
| **响应速度** | 网络延迟 | **即时响应** |
| **上下文理解** | 有限 | **完整项目** |

**结论**: Cursor = OpenAI能力 + 零成本 + 更好体验

---

## 📚 核心功能

### 1. Composer（Cmd+I）

**用途**: 生成、修改、重构代码

**场景**:
- ✅ 生成完整测试用例
- ✅ 批量创建测试
- ✅ 重构代码结构

**示例**:
```
按Cmd+I，输入：
"生成用户登录接口的5个测试场景
保存到 cases/api/login.yaml"
```

### 2. Chat（Cmd+L）

**用途**: 代码分析、问题解答、故障诊断

**场景**:
- ✅ 分析测试失败原因
- ✅ 解释代码逻辑
- ✅ 提供优化建议

**示例**:
```
按Cmd+L，输入：
"@test_demo.py 这个测试为什么失败了？
[粘贴错误日志]"
```

### 3. Inline Edit（Cmd+K）

**用途**: 快速修改代码片段

**场景**:
- ✅ 修复单个测试用例
- ✅ 优化断言
- ✅ 调整配置

**示例**:
```
选中代码，按Cmd+K，输入：
"添加更多断言，验证用户名和邮箱"
```

---

## 🚀 实战案例

### 案例1: 生成API测试用例

#### 场景
你有一个用户管理API，需要生成完整的测试用例。

#### 步骤

**1. 准备API规范**（可选）
创建 `docs/user_api_spec.yaml`:
```yaml
/api/users:
  get:
    summary: 查询用户列表
    parameters:
      - name: page
      - name: size
  post:
    summary: 创建用户
    requestBody:
      properties:
        username: string
        email: string
```

**2. 使用Cursor Composer生成**
按 `Cmd+I`，输入:
```
@docs/user_api_spec.yaml
为用户管理API生成完整测试用例
包含：
1. 查询列表（正常、空参数、分页）
2. 创建用户（正常、缺少参数、重复用户名）
3. 更新用户（正常、不存在的用户）
4. 删除用户（正常、不存在的用户）
保存到 cases/api/user_management.yaml
```

**3. Cursor自动生成**

Cursor会分析API规范，生成如下YAML:
```yaml
case_name: 用户管理接口测试
case_code: UserManagementTest
priority: 1
steps:
  - step_name: 查询用户列表-正常场景
    method: GET
    url: /api/users
    params:
      page: 1
      size: 10
    response_assert:
      status_code_assert: 200
      jsonpath_assert:
        - $..totalCount >= 0
        - $..data != null
  
  - step_name: 创建用户-正常场景
    method: POST
    url: /api/users
    data:
      username: "test_user"
      email: "test@example.com"
    response_assert:
      status_code_assert: 201
      jsonpath_assert:
        - $.id != null
        - $.username == "test_user"
  
  # ... 更多测试场景
```

**4. 执行测试**
```bash
python core/test_executor.py cases/api/user_management.yaml
```

---

### 案例2: 生成Playwright Web测试

#### 场景
为电商网站的购物流程生成自动化测试。

#### 步骤

**1. 在Cursor Composer中输入**
```
生成一个完整的购物流程Playwright测试
URL: https://shop.example.com
流程：
1. 登录
2. 搜索商品"手机"
3. 点击第一个商品
4. 加入购物车
5. 进入购物车
6. 结算
保存到 cases/web/test_shopping_flow.py
```

**2. Cursor生成代码**
```python
from playwright.sync_api import sync_playwright, expect

def test_shopping_flow():
    """完整购物流程测试"""
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=False)
        context = browser.new_context()
        page = context.new_page()
        
        context.tracing.start(screenshots=True, snapshots=True)
        
        try:
            # 1. 登录
            page.goto("https://shop.example.com/login")
            page.fill("input#username", "test_user")
            page.fill("input#password", "password123")
            page.click("button:has-text('登录')")
            
            # 2. 搜索商品
            page.fill("input[placeholder='搜索']", "手机")
            page.click("button.search")
            
            # 3. 点击第一个商品
            page.click(".product-item:first-child")
            
            # 4. 加入购物车
            page.click("text=加入购物车")
            expect(page.locator("text=添加成功")).to_be_visible()
            
            # 5. 进入购物车
            page.click("a[href='/cart']")
            expect(page).to_have_url("**/cart")
            
            # 6. 结算
            page.click("text=去结算")
            expect(page).to_have_url("**/checkout")
            
            page.screenshot(path="reports/screenshots/shopping_flow.png")
            
        finally:
            context.tracing.stop(path="reports/traces/shopping_flow.zip")
            browser.close()
```

**3. 执行测试**
```bash
python cases/web/test_shopping_flow.py
```

---

### 案例3: 故障诊断与自动修复

#### 场景
测试失败，需要快速定位和修复。

#### 步骤

**1. 运行测试失败**
```bash
python cases/web/test_login.py

# 错误输出：
# TimeoutError: Timeout 30000ms exceeded
# waiting for selector "input#username"
```

**2. 使用Cursor Chat诊断**
按 `Cmd+L`，输入:
```
@test_login.py
测试失败，错误日志：
TimeoutError: Timeout 30000ms exceeded
waiting for selector "input#username"

请分析原因并提供修复方案
```

**3. Cursor分析并回复**
```
【诊断结果】
问题：元素定位超时

可能原因：
1. CSS选择器不稳定（#username可能动态变化）
2. 元素加载慢
3. 页面结构改变

【修复方案】

方案1（推荐）：使用文本定位器
page.fill("placeholder=请输入用户名", "admin")

方案2：增加等待时间
page.wait_for_selector("input#username", state="visible", timeout=60000)
page.fill("input#username", "admin")

方案3：使用测试ID
page.fill("[data-testid=username]", "admin")

【修复后的代码】
...
```

**4. 应用修复**
在Cursor中选中错误代码，按 `Cmd+K`，输入:
```
使用方案1修复
```

Cursor自动修改代码！

---

### 案例4: 批量生成测试

#### 场景
有10个API端点，需要快速生成所有测试用例。

#### 步骤

**1. 创建端点列表**
`docs/api_endpoints.txt`:
```
POST /api/login
GET /api/users
POST /api/users
PUT /api/users/{id}
DELETE /api/users/{id}
GET /api/products
POST /api/orders
GET /api/orders/{id}
PUT /api/orders/{id}/cancel
GET /api/reports
```

**2. 使用Cursor Composer**
```
@api_endpoints.txt
为列表中的每个API端点生成测试用例
每个端点包含5个测试场景
保存到 cases/api/ 对应的文件
```

**3. Cursor批量生成**
自动创建:
- `cases/api/login.yaml`
- `cases/api/users.yaml`
- `cases/api/products.yaml`
- `cases/api/orders.yaml`
- `cases/api/reports.yaml`

**效率提升**: 10个API × 5个场景 = 50个测试用例，5分钟完成！

---

## 💡 高级技巧

### 技巧1: 引用文件

使用`@`符号引用项目文件:
```
@docs/api_spec.yaml
@config/environment.yaml
@cases/api/user_query.yaml
```

Cursor会读取文件内容作为上下文。

### 技巧2: 多文件生成

一次性生成多个相关文件:
```
生成用户模块的完整测试
包括：
1. API测试用例 -> cases/api/users/
2. Web测试用例 -> cases/web/test_users.py
3. 测试数据 -> cases/data/users.json
```

### 技巧3: 代码审查

让Cursor审查生成的代码:
```
@cases/api/login.yaml
请审查这个测试用例，检查：
1. 场景覆盖是否完整
2. 断言是否充分
3. 是否有改进建议
```

### 技巧4: 模式学习

提供示例，让Cursor学习模式:
```
@cases/api/examples/user_query.yaml
参考这个示例的风格和结构
为订单管理API生成类似的测试用例
```

### 技巧5: 增量优化

持续优化测试:
```
@test_login.py
优化这个测试：
1. 添加更多边界值测试
2. 增加异常处理
3. 添加性能断言
```

---

## 📝 最佳提示词模板

### API测试生成

```
生成[功能名称]的API测试用例
接口: [METHOD] [URL]
参数: [参数列表]
测试场景:
1. 正常场景: [描述]
2. 异常场景: [描述]
3. 边界场景: [描述]
保存到: cases/api/[文件名].yaml
```

### Web测试生成

```
生成[功能名称]的Playwright测试
URL: [页面地址]
测试流程:
1. [步骤1]
2. [步骤2]
3. [步骤3]
断言: [期望结果]
保存到: cases/web/test_[功能名].py
```

### Mobile测试生成

```
生成[功能名称]的Maestro测试
App ID: [应用包名]
测试流程:
1. [步骤1]
2. [步骤2]
3. [步骤3]
保存到: cases/mobile/[功能名].yaml
```

### 故障诊断

```
@[测试文件]
测试失败，错误日志:
[粘贴完整错误日志]

请诊断并提供:
1. 失败原因
2. 修复方案（至少3个）
3. 修复后的完整代码
```

---

## ⚙️ .cursorrules配置详解

`.cursorrules`定义了AI生成规范，关键配置:

### API测试规范
```yaml
output_format: YAML
required_fields:
  - case_name
  - case_code
  - steps
step_requirements:
  - step_name
  - method
  - url
  - response_assert
```

### 生成约束
```yaml
test_scenarios_min: 5  # 每个接口至少5个场景
assertion_min: 3       # 每个步骤至少3个断言
```

### 自定义规则

编辑`.cursorrules`添加项目特定规则:
```yaml
# 自定义：公司内部API规范
company_api_rules:
  base_url_template: "$get_host({{env}}, {{service}})"
  auth_template: "Bearer $generate_token({{service}})"
  required_headers:
    - Content-Type: application/json
    - X-Request-ID: $generate_uuid()
```

---

## 🎯 实用场景

### 场景1: 新人入职

**新人**: "我不懂Playwright，怎么写测试？"
**Cursor**: 

```
@快速开始.md
我是新人，需要学习如何编写Playwright测试
请给我一个完整示例，包括：
1. 登录
2. 填写表单
3. 提交验证
并解释每一步的作用
```

### 场景2: 紧急修复

**场景**: 线上发现Bug，需要立即补充测试

```
紧急：需要为用户登录接口补充测试
Bug: 当密码包含特殊字符时登录失败
生成针对特殊字符的测试用例
保存到 cases/api/login_special_chars.yaml
```

### 场景3: 重构优化

**场景**: 项目重构，测试需要同步更新

```
@cases/web/old_test.py
这个测试使用了旧的页面结构
新页面结构：
- 登录按钮从 #loginBtn 改为 [data-testid="login"]
- 用户名输入框从 #username 改为 input[name="user"]
请重构这个测试
```

---

## 🔧 故障排查

### Cursor没有响应？

**检查**:
1. 是否在AI_TEST项目根目录？
2. `.cursorrules`文件是否存在？
3. 提示词是否包含关键词？

### 生成的代码不符合预期？

**优化提示词**:
```
# 不够清晰
"生成测试"

# 更清晰
"生成用户登录API的测试用例
包含5个场景：正常登录、错误密码、空用户名、SQL注入、并发登录
每个场景至少3个断言
保存到 cases/api/login.yaml"
```

### 生成的测试执行失败？

**使用Cursor诊断**:
```
@[生成的测试文件]
执行失败，错误:
[错误信息]
请修复
```

---

## 📊 效率对比

### 手工编写 vs Cursor生成

| 任务 | 手工时间 | Cursor时间 | 提升倍数 |
|------|---------|-----------|----------|
| 1个API测试（5场景） | 30分钟 | **10秒** | **180倍** |
| 1个Web测试 | 20分钟 | **15秒** | **80倍** |
| 10个API端点测试 | 5小时 | **2分钟** | **150倍** |
| 故障诊断 | 30分钟 | **30秒** | **60倍** |

**总结**: 平均效率提升 **100倍以上**

---

## 🎉 总结

### Cursor AI的价值

1. **零成本**: 无需OpenAI API Key
2. **零配置**: 开箱即用
3. **高效率**: 100倍提升
4. **高质量**: AI生成+人工审核

### 成功关键

1. ✅ 清晰的提示词
2. ✅ 充分的上下文（@引用文件）
3. ✅ 迭代优化（不满意就再次生成）
4. ✅ 人工审核（AI辅助，人负责）

### 下一步

1. 尝试生成第一个测试用例
2. 使用Chat诊断一个失败测试
3. 探索更多高级功能

**开始你的AI测试之旅！** 🚀

