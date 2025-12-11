# crawled_elements - 页面元素爬取功能文档

## 📋 功能概述

`crawled_elements` 目录用于存储和管理通过自动化方式爬取的页面元素配置。该功能允许测试人员快速获取页面元素信息，生成标准化的元素配置文件，从而加速测试用例开发。

### 核心优势
- **自动化爬取**：无需手动编写元素定位器，自动生成标准化的元素配置
- **多格式输出**：支持生成标注截图、文本清单和JSON格式元素数据
- **AI增强**：结合Stagehand AI实现智能元素定位和操作
- **易于集成**：爬取的元素配置可直接用于测试用例

## 📁 目录结构

```
crawled_elements/
├── README.md                  # 本文档
├── page_elements_config.yaml  # 页面元素配置文件示例
└── generated/                 # 自动生成的元素配置文件
    ├── login_page.yaml        # 登录页面元素配置
    └── dashboard_page.yaml    # 仪表板页面元素配置
```

## 🛠️ 元素爬取核心功能

元素爬取功能主要由 `playwright_keywords.py` 中的以下核心函数实现：

### 1. `annotate_interactives(scope_label: str = "Step") -> str`
- **功能**：扫描当前页面的可交互元素，生成带编号标注的完整页面截图
- **输出**：
  - 标注截图：`reports/web/annotated/annotated_{scope_label}.png`
  - 元素清单JSON：`reports/web/interactives/interactives_{scope_label}.json`
  - 元素清单TXT：`reports/web/interactives/interactives_{scope_label}.txt`
- **元素信息**：每个元素包含 `cssPath`、`xpath`、`role`、`aria`、`placeholder`、`name`、`type` 等属性

### 2. `dump_elements(max_per_type: int = 50) -> str`
- **功能**：枚举当前上下文内的常见元素
- **支持元素类型**：input、button、a、div（可见文本）、span（可见文本）
- **输出**：`reports/web/elements/latest.txt`
- **参数**：`max_per_type` - 每种元素类型的最大输出数量

### 3. `dump_dropdown_options(scope_label: str = "Step") -> str`
- **功能**：导出当前可见下拉面板中的所有选项
- **支持下拉类型**：Ant Design 下拉菜单（.ant-select-dropdown）
- **输出**：`reports/web/dropdown/latest.txt`

### 4. `dump_iframes() -> str`
- **功能**：保存页面所有iframe信息
- **输出**：`reports/web/iframes/latest.txt`
- **iframe信息**：包含 `src`、`id`、`name`、`class` 等属性

## 🚀 使用方法

### 方式1：在测试用例中直接调用

在Web测试用例的YAML文件中，可以直接调用爬取关键字：

```yaml
case_name: 销售合同页面元素爬取测试
priority: 1
tags:
  - smoke
steps:
  - step_name: 导航到销售合同页面
    action: $navigate(http://example.com/sales-contract)
    sleep: 3

  - step_name: 生成元素标注图
    action: $annotate_interactives(scope_label="SalesContract")
    sleep: 2

  - step_name: 导出页面元素清单
    action: $dump_elements(max_per_type=50)
    sleep: 1

  - step_name: 爬取下拉选项
    action: $click_select_and_wait_open(合同类型)
    sleep: 1
    action: $dump_dropdown_options(scope_label="ContractType")
```

### 方式2：通过Python脚本调用

可以创建独立的Python脚本，用于批量爬取多个页面的元素：

```python
from keywords.playwright_keywords import annotate_interactives, dump_elements
from core.playwright_driver import PlaywrightDriver

# 初始化Playwright驱动
driver = PlaywrightDriver()
driver.start()
driver.navigate("http://example.com")

# 生成元素标注图
annotate_interactives(scope_label="HomePage")

# 导出元素清单
dump_elements(max_per_type=100)

# 关闭驱动
driver.close()
```

### 方式3：AI驱动的元素操作

结合Stagehand AI，可以使用自然语言进行元素爬取和操作：

```yaml
- step_name: AI登录并爬取元素
  action: $ai_agent(登录系统并爬取仪表板页面的所有可交互元素)
```

## 📝 元素配置文件格式

爬取的元素信息可以整理为标准化的YAML格式，用于测试用例开发：

```yaml
# page_elements_config.yaml
sales_contract_page:
  # 页面标题
  page_title: text=销售合同管理
  
  # 按钮
  button_add_contract: r,button,新增合同  # role+name定位
  button_save: r,button,保存  # role+name定位
  button_cancel: r,button,取消  # role+name定位
  
  # 输入框
  input_contract_code: s,.ant-input[name="contractCode"]  # CSS选择器定位
  input_customer_name: s,.ant-input[name="customerName"]  # CSS选择器定位
  input_amount: s,.ant-input[name="amount"]  # CSS选择器定位
  
  # 下拉选择器
  select_contract_type: s,.ant-select[name="contractType"]  # CSS选择器定位
  
  # 表格
  table_contract_list: s,.ant-table  # CSS选择器定位
  
  # 搜索按钮
  button_search: t,搜索  # 文本定位
```

## 🔧 元素配置加载

使用 `element_config_loader.py` 中的函数加载元素配置：

```python
from libs.element_config_loader import load_page_elements

# 加载销售合同页面元素配置
elements = load_page_elements("sales_contract_page")

# 在测试用例中使用
button_add = elements["button_add_contract"]
```

## 🎯 最佳实践

### 1. 定期更新元素配置
- 建议在页面UI变更后重新爬取元素配置
- 可以将元素爬取集成到CI/CD流程中，确保配置始终最新

### 2. 选择合适的定位策略
按照优先级选择元素定位策略：
1. **`data-testid`** (`t,testid`) - 最稳定
2. **`role+name`** (`r,role,name`) - Playwright推荐
3. **`id`** (`s,#id`) - 稳定可靠
4. **`text`** (`x,//*[contains(text(),"xxx")]`) - 较不稳定

### 3. 合理组织元素配置
- 按页面分组存储元素配置
- 为元素命名使用清晰的前缀（如 `button_`、`input_`、`select_`）
- 添加注释说明元素用途

### 4. 结合AI驱动功能
- 对于动态页面，使用AI驱动的元素定位
- 对于复杂流程，使用AI Agent执行多步骤元素爬取

## ❓ 常见问题

### Q1: 爬取的元素定位器不稳定怎么办？
**A**: 尝试使用更高优先级的定位策略，或结合AI驱动的元素定位。

### Q2: 如何处理动态生成的元素？
**A**: 使用相对定位或AI驱动的元素定位，避免使用固定的CSS路径或XPath。

### Q3: 爬取的元素数量太多怎么办？
**A**: 使用 `max_per_type` 参数限制每种元素类型的输出数量，或在爬取前缩小页面范围。

### Q4: 如何将爬取结果整合到现有测试用例？
**A**: 将爬取结果整理为标准化的YAML格式，然后使用 `load_page_elements()` 函数加载到测试用例中。

## 📚 相关资源

- [Playwright最佳实践](docs/Playwright最佳实践应用计划.md)
- [Stagehand集成说明](docs/Stagehand集成说明.md)
- [智能测试数据管理指南](docs/智能测试数据管理指南.md)

## 📞 支持与反馈

如果您在使用过程中遇到问题或有改进建议，请联系测试框架维护团队。

---

**版本**: v1.0  
**更新日期**: 2025-12-09  
**状态**: ✅ 生产就绪
