# SQL和Redis步骤使用说明

## 概述

测试框架现已支持直接执行SQL查询和Redis操作，无需依赖HTTP请求。这使得测试用例更加灵活，可以直接操作数据库和缓存。

## SQL步骤使用

### 基本语法

```yaml
- step_name: 步骤名称
  protocol: sql
  sql: SELECT * FROM table_name WHERE condition = 'value'
  query_type: select  # select, insert, update, delete, count等
  env_name: ERP_TEST  # 可选，环境名称，默认使用配置中的env_name
  db_key: default     # 可选，数据库配置键名，默认使用配置中的db_key
  db_type: postgres   # 可选，数据库类型，默认postgres
  response_assert:
    success_assert: true           # 是否成功断言
    row_count_assert: >=          # 行数断言操作符 (>=, <=, ==, >, <)
    expected_count: 0              # 期望的行数
    data_assert:                    # 数据断言（JSONPath表达式）
      - $..count > 0
      - $..value == 'expected'
  extract:
    - extract: $set_variable(variableName,$get_response_data($.data[0].field_name))
    - extract: $set_variable(variableName,$get_response_data($.json.value))
```

### 示例

#### 1. 查询数据

```yaml
- step_name: 查询产品数据
  protocol: sql
  sql: SELECT product_part_code FROM product_part WHERE enterprise_code = '$get_variable(enterpriseCode)' LIMIT 1
  query_type: select
  env_name: ERP_TEST
  db_key: default
  db_type: postgres
  response_assert:
    success_assert: true
    row_count_assert: >=
    expected_count: 0
  extract:
    - extract: $set_variable(productPartCode,$get_response_data($.data[0].product_part_code))
    - extract: $set_variable(productPartCode,$get_response_data($.json.value))
```

#### 2. 插入数据

```yaml
- step_name: 插入测试数据
  protocol: sql
  sql: INSERT INTO test_table (name, value) VALUES ('test', 'value')
  query_type: insert
  response_assert:
    success_assert: true
```

#### 3. 更新数据

```yaml
- step_name: 更新数据
  protocol: sql
  sql: UPDATE test_table SET value = 'new_value' WHERE id = '$get_variable(id)'
  query_type: update
  response_assert:
    success_assert: true
```

#### 4. 删除数据

```yaml
- step_name: 删除数据
  protocol: sql
  sql: DELETE FROM test_table WHERE id = '$get_variable(id)'
  query_type: delete
  response_assert:
    success_assert: true
```

### 变量替换

SQL语句支持变量替换，使用`$get_variable(variableName)`格式：

```yaml
sql: SELECT * FROM table WHERE id = '$get_variable(id)' AND name = '$get_variable(name)'
```

### 响应数据结构

SQL查询的响应数据结构如下：

```json
{
  "status_code": 200,
  "success": true,
  "data": [
    {
      "field1": "value1",
      "field2": "value2"
    }
  ],
  "row_count": 1,
  "query_type": "select",
  "sql": "SELECT * FROM table",
  "json": {
    "value": "value1",  // 单行单列查询时
    "data": [...]      // 多行查询时
  },
  "metadata": {
    "env_name": "ERP_TEST",
    "db_key": "default",
    "db_type": "postgres"
  }
}
```

## Redis步骤使用

### 基本语法

```yaml
- step_name: 步骤名称
  protocol: redis
  operation: get  # get, set, delete, exists, keys, hget, hset, hgetall, ttl
  key: cache_key
  value: cache_value  # 用于set和hset操作
  field: hash_field   # 用于hget和hset操作
  pattern: pattern_*  # 用于keys操作
  ttl: 3600           # 过期时间（秒），用于set操作
  env_name: ERP_TEST  # 可选，环境名称
  db_key: default     # 可选，Redis配置键名
  response_assert:
    success_assert: true
    value_assert: "expected_value"
    exists_assert: true
    data_assert:
      - $..value == 'expected'
  extract:
    - extract: $set_variable(variableName,$get_response_data($.json.value))
```

### 示例

#### 1. GET操作

```yaml
- step_name: 获取缓存值
  protocol: redis
  operation: get
  key: cache_key
  response_assert:
    success_assert: true
    value_assert: "expected_value"
  extract:
    - extract: $set_variable(cacheValue,$get_response_data($.json.value))
```

#### 2. SET操作

```yaml
- step_name: 设置缓存值
  protocol: redis
  operation: set
  key: cache_key
  value: cache_value
  ttl: 3600  # 过期时间（秒）
  response_assert:
    success_assert: true
```

#### 3. DELETE操作

```yaml
- step_name: 删除缓存
  protocol: redis
  operation: delete
  key: cache_key
  response_assert:
    success_assert: true
```

#### 4. EXISTS操作

```yaml
- step_name: 检查缓存是否存在
  protocol: redis
  operation: exists
  key: cache_key
  response_assert:
    success_assert: true
    exists_assert: true
```

#### 5. KEYS操作

```yaml
- step_name: 查找所有匹配的键
  protocol: redis
  operation: keys
  pattern: cache_*
  response_assert:
    success_assert: true
  extract:
    - extract: $set_variable(cacheKeys,$get_response_data($.json.keys))
```

#### 6. HGET操作

```yaml
- step_name: 获取Hash字段值
  protocol: redis
  operation: hget
  key: hash_key
  field: hash_field
  response_assert:
    success_assert: true
  extract:
    - extract: $set_variable(hashValue,$get_response_data($.json.value))
```

#### 7. HSET操作

```yaml
- step_name: 设置Hash字段值
  protocol: redis
  operation: hset
  key: hash_key
  field: hash_field
  value: hash_value
  response_assert:
    success_assert: true
```

#### 8. HGETALL操作

```yaml
- step_name: 获取所有Hash字段
  protocol: redis
  operation: hgetall
  key: hash_key
  response_assert:
    success_assert: true
  extract:
    - extract: $set_variable(hashData,$get_response_data($.json))
```

#### 9. TTL操作

```yaml
- step_name: 获取键的过期时间
  protocol: redis
  operation: ttl
  key: cache_key
  response_assert:
    success_assert: true
  extract:
    - extract: $set_variable(keyTTL,$get_response_data($.json.ttl))
```

### 变量替换

Redis步骤支持变量替换，使用`$get_variable(variableName)`格式：

```yaml
operation: get
key: $get_variable(cacheKey)
value: $get_variable(cacheValue)
```

### 响应数据结构

Redis操作的响应数据结构如下：

```json
{
  "status_code": 200,
  "success": true,
  "operation": "get",
  "key": "cache_key",
  "data": "cache_value",
  "json": {
    "value": "cache_value"
  },
  "metadata": {}
}
```

## 注意事项

1. **SQL步骤**：
   - SQL语句中的变量替换使用`$get_variable(variableName)`格式
   - 框架会自动处理变量替换
   - 对于分片表，需要手动指定表名或使用框架的智能查询功能（`$get_db_field`）

2. **Redis步骤**：
   - 需要安装`redis`模块：`pip install redis`
   - Redis配置需要在`environment.yaml`中配置`redis_base`部分
   - 所有操作都支持变量替换

3. **变量提取**：
   - SQL查询结果使用`$.data[0].field_name`或`$.json.value`提取
   - Redis操作结果使用`$.json.value`或`$.json.keys`提取

4. **断言**：
   - SQL步骤支持`success_assert`、`row_count_assert`和`data_assert`
   - Redis步骤支持`success_assert`、`value_assert`、`exists_assert`和`data_assert`

## 完整示例

```yaml
case_name: 测试SQL和Redis步骤
case_code: TestSQLRedis
priority: 2
steps:
  # SQL查询步骤
  - step_name: 查询产品数据
    protocol: sql
    sql: SELECT product_part_code FROM product_part WHERE enterprise_code = '$get_variable(enterpriseCode)' LIMIT 1
    query_type: select
    response_assert:
      success_assert: true
      row_count_assert: >=
      expected_count: 0
    extract:
      - extract: $set_variable(productPartCode,$get_response_data($.json.value))
  
  # Redis GET步骤
  - step_name: 获取缓存
    protocol: redis
    operation: get
    key: $get_variable(cacheKey)
    response_assert:
      success_assert: true
    extract:
      - extract: $set_variable(cacheValue,$get_response_data($.json.value))
  
  # Redis SET步骤
  - step_name: 设置缓存
    protocol: redis
    operation: set
    key: $get_variable(cacheKey)
    value: $get_variable(cacheValue)
    ttl: 3600
    response_assert:
      success_assert: true
```

