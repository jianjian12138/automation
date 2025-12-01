# 自动生成的测试用例说明

## 生成结果

### 后端代码分析结果
- **控制器数量**: 186个
- **API接口数量**: 163个
- **生成的测试用例**: 100+个YAML文件

### 前端代码分析结果
- **路由数量**: 0（前端代码已打包，无法提取路由信息）
- **组件数量**: 0（前端代码已打包，无法提取组件信息）
- **API数量**: 0（前端代码已打包，无法提取API调用）

## 生成的测试用例位置

### API测试用例
位置: `cases/auto_generated/api/apis/`

包含以下控制器测试用例：
- LoginController.yaml - 登录接口测试
- SellOrderController.yaml - 销售订单接口测试
- PurchaseContractController.yaml - 采购合同接口测试
- ... 等100+个测试用例文件

## 测试用例格式

每个测试用例文件包含：
- `case_code`: 用例代码
- `case_name`: 用例名称
- `priority`: 优先级
- `steps`: 测试步骤列表

每个步骤包含：
- `step_name`: 步骤名称
- `host`: 主机地址（使用变量）
- `path`: API路径
- `method`: HTTP方法（GET/POST/PUT/DELETE）
- `headers`: 请求头（使用token生成函数）
- `data`/`params`: 请求数据
- `response_assert`: 响应断言

## 使用说明

### 1. 查看生成的测试用例

```bash
# 查看所有生成的测试用例
ls cases/auto_generated/api/apis/

# 查看特定测试用例
cat cases/auto_generated/api/apis/LoginController.yaml
```

### 2. 调整测试用例

生成的测试用例是基础模板，需要根据实际情况调整：

1. **补充测试数据**
   - 修改`data`或`params`字段，添加真实的测试数据
   - 根据接口文档补充必填参数

2. **完善断言**
   - 修改`response_assert`，添加更详细的断言
   - 添加`jsonpath_assert`验证返回数据

3. **添加更多测试场景**
   - 正常场景：有效参数
   - 异常场景：无效参数、缺少参数等
   - 边界值测试：最小/最大值

### 3. 运行测试用例

```bash
# 运行单个测试用例
python main.py --path cases/auto_generated/api/apis/LoginController.yaml

# 运行所有测试用例
python main.py --path cases/auto_generated/api/apis/
```

## 注意事项

1. **测试数据**
   - 生成的测试用例中的测试数据是占位符
   - 需要根据实际情况补充真实的测试数据

2. **接口路径**
   - 部分接口路径可能需要调整
   - 建议对照接口文档确认路径

3. **断言逻辑**
   - 生成的断言是基础模板
   - 需要根据实际业务逻辑完善断言

4. **前端测试用例**
   - 由于前端代码已打包，无法自动生成前端测试用例
   - 建议手动创建前端测试用例，或提供前端源代码

## 下一步建议

1. **审查测试用例**
   - 检查生成的测试用例是否符合要求
   - 根据接口文档调整测试用例

2. **补充测试数据**
   - 添加真实的测试数据
   - 创建测试数据配置文件

3. **完善断言**
   - 添加更详细的断言逻辑
   - 验证返回数据的正确性

4. **添加异常场景**
   - 补充异常场景测试用例
   - 测试错误处理逻辑

5. **前端测试用例**
   - 手动创建前端测试用例
   - 或提供前端源代码重新生成

## 工具使用

如果需要重新生成测试用例：

```bash
# 生成全站测试用例
python tools/generate_all_testcases.py --frontend servercode/front --backend servercode/170server -o cases/auto_generated

# 只生成后端API测试用例
python tools/generate_testcases_from_backend.py servercode/170server -o cases/api/generated

# 只生成前端测试用例（如果有源代码）
python tools/generate_testcases_from_frontend.py servercode/front -o cases/web/generated
```

