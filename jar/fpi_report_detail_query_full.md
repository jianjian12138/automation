# /fpi/report/detail/query 接口详细分析

## 1. 接口请求

### 1.1 请求URL
`POST /fpi/report/detail/query`

### 1.2 请求方法
`POST`

### 1.3 请求头
| 字段名 | 类型 | 描述 | 示例值 |
|-------|------|------|--------|
| Content-Type | String | 请求内容类型 | application/json |
| Authorization | String | 认证令牌 | Bearer eyJhbGciOiJIUzI1NiJ9... |

### 1.4 请求体
```json
{
  "reportType": "sellDetailStatistics",
  "params": {
    "startDate": "2025-01-01",
    "endDate": "2025-12-31",
    "staff": "123456",
    "paymentType": "1"
  },
  "page": 1,
  "size": 10
}
```

## 2. 接口传参

### 2.1 必传参数
| 参数名 | 类型 | 描述 | 示例值 |
|-------|------|------|--------|
| reportType | String | 报表类型，对应InnerInterfaceStrategy中的常量 | sellDetailStatistics |
| page | Integer | 页码 | 1 |
| size | Integer | 每页大小 | 10 |

### 2.2 可选参数
| 参数名 | 类型 | 描述 | 示例值 |
|-------|------|------|--------|
| params | Object | 报表查询参数，根据报表类型不同而变化 | {"startDate": "2025-01-01", "endDate": "2025-12-31"} |
| params.startDate | String | 开始日期 | 2025-01-01 |
| params.endDate | String | 结束日期 | 2025-12-31 |
| params.staff | String | 员工ID | 123456 |
| params.paymentType | String | 付款方式 | 1 |

## 3. 接口响应

### 3.1 响应格式
```json
{
  "code": "0",
  "message": "查询成功",
  "data": {
    "data": [
      {
        "axis": "2025-01",
        "data": "1000.00"
      }
    ],
    "extraInfo": {
      "maxValue": "1000.00",
      "minValue": "1000.00"
    },
    "quotas": [
      {
        "key": "总额(万元)",
        "value": "1000.00"
      }
    ]
  }
}
```

### 3.2 响应字段说明
| 字段名 | 类型 | 描述 |
|-------|------|------|
| code | String | 响应码，0表示成功，非0表示失败 |
| message | String | 响应消息 |
| data | Object | 响应数据 |
| data.data | Array | 报表数据 |
| data.extraInfo | Object | 额外信息 |
| data.extraInfo.maxValue | String | 最大值 |
| data.extraInfo.minValue | String | 最小值 |
| data.quotas | Array | 指标信息 |

## 4. 接口请求过程使用的SQL语句

### 4.1 销售详情统计SQL

#### 4.1.1 核心查询SQL
```sql
-- 销售详情统计查询
SELECT 
    DATE_FORMAT(create_time, '%Y-%m') AS axis,
    SUM(product_rmb_total_price) AS data
FROM 
    dm_m9.base_module_t_dm_m9_history_price_9
WHERE 
    enterprise_code = ?
    AND delete_flag = 'f'
    AND type = 1
    AND create_time BETWEEN ? AND ?
    AND seller IN (?)
    AND payment_strategy = ?
GROUP BY 
    DATE_FORMAT(create_time, '%Y-%m')
ORDER BY 
    axis
```

#### 4.1.2 销售总额统计SQL
```sql
-- 销售总额统计
SELECT 
    SUM(product_rmb_total_price) AS total
FROM 
    dm_m9.base_module_t_dm_m9_history_price_9
WHERE 
    enterprise_code = ?
    AND delete_flag = 'f'
    AND type = 1
    AND create_time BETWEEN ? AND ?
    AND seller IN (?)
    AND payment_strategy = ?
```

### 4.2 采购详情统计SQL

#### 4.2.1 核心查询SQL
```sql
-- 采购详情统计查询
SELECT 
    DATE_FORMAT(create_time, '%Y-%m') AS axis,
    SUM(product_rmb_total_price) AS data
FROM 
    dm_m9.base_module_t_dm_m9_history_price_9
WHERE 
    enterprise_code = ?
    AND delete_flag = 'f'
    AND type = 0
    AND create_time BETWEEN ? AND ?
    AND vender IN (?)
GROUP BY 
    DATE_FORMAT(create_time, '%Y-%m')
ORDER BY 
    axis
```

#### 4.2.2 采购总额统计SQL
```sql
-- 采购总额统计
SELECT 
    SUM(product_rmb_total_price) AS total
FROM 
    dm_m9.base_module_t_dm_m9_history_price_9
WHERE 
    enterprise_code = ?
    AND delete_flag = 'f'
    AND type = 0
    AND create_time BETWEEN ? AND ?
    AND vender IN (?)
```

### 4.3 库存统计SQL

#### 4.3.1 库存概况SQL
```sql
-- 库存概况查询
SELECT 
    product_code AS productCode,
    product_name AS productName,
    SUM(inventory_quantity) AS totalInventory,
    SUM(safety_stock) AS safetyStock
FROM 
    dm_m9.inventory_t_dm_m9_inventory_9
WHERE 
    enterprise_code = ?
    AND delete_flag = 'f'
GROUP BY 
    product_code, product_name
```

### 4.4 生产进度统计SQL

#### 4.4.1 生产进度查询
```sql
-- 生产进度查询
SELECT 
    task_code AS taskCode,
    product_code AS productCode,
    planned_quantity AS plannedQuantity,
    SUM(completed_quantity) AS completedQuantity,
    ROUND((SUM(completed_quantity) / planned_quantity) * 100, 2) AS progress
FROM 
    dm_m9.production_t_dm_m9_produce_task_9
WHERE 
    enterprise_code = ?
    AND delete_flag = 'f'
    AND create_time BETWEEN ? AND ?
GROUP BY 
    task_code, product_code, planned_quantity
```

## 5. 接口和SQL的简单说明

### 5.1 接口实现说明

#### 5.1.1 核心实现类
- **`InnerReportDataSetQueryImpl`**：实现了`InnerReportDataSetQuery`接口，是报表查询的核心实现类
- **`InnerInterfaceStrategy`**：报表策略接口，每种报表类型对应一个实现类
- **`SellDetailStatisticsStrategy`**：销售详情统计策略类

#### 5.1.2 核心方法
- **`executeByInnerInterface`**：根据报表类型执行查询
- **`getTarget`**：获取指定类型的策略类
- **`execute`**：策略类的核心执行方法，执行具体的SQL查询

#### 5.1.3 工作流程
1. 客户端发送请求，指定报表类型和参数
2. 接口根据报表类型获取对应的策略类
3. 策略类执行具体的SQL查询
4. 返回格式化的查询结果

### 5.2 SQL说明

#### 5.2.1 表结构说明
- **`dm_m9.base_module_t_dm_m9_history_price_9`**：历史价格表，存储销售和采购的历史价格记录
- **`dm_m9.inventory_t_dm_m9_inventory_9`**：库存表，存储产品库存信息
- **`dm_m9.production_t_dm_m9_produce_task_9`**：生产任务表，存储生产任务信息

#### 5.2.2 SQL优化建议
1. **索引优化**：在`enterprise_code`、`create_time`、`product_code`等字段上添加索引
2. **分区表**：对历史价格表按时间分区，提高查询性能
3. **预计算**：对于频繁查询的报表，使用物化视图或预计算结果
4. **参数绑定**：使用参数绑定防止SQL注入，提高查询性能

### 5.3 接口特点

#### 5.3.1 优点
- **灵活性高**：支持多种报表类型，涵盖多个业务领域
- **扩展性强**：新的报表类型可以通过实现新的策略类轻松添加
- **性能优良**：采用策略模式设计，每个策略类专注于一种报表查询
- **响应格式统一**：所有报表返回统一的格式，方便前端处理

#### 5.3.2 限制
- **SQL复杂度**：复杂报表的SQL查询可能较复杂，需要优化
- **数据量限制**：处理大量数据时，需要考虑分页和性能优化

## 6. 支持的报表类型

| 报表类型 | 描述 | 对应策略类 |
|---------|------|------------|
| sellDetailStatistics | 销售详情统计 | SellDetailStatisticsStrategy |
| purchaseDetailStatistics | 采购详情统计 | PurchaseDetailStatisticsStrategy |
| inventoryAlert | 库存预警 | InventoryAlertStrategy |
| incomeOverview | 收入概览 | IncomeOverviewStrategy |
| summaryOverview | 汇总概览 | SummaryOverviewStrategy |
| produceDispatchState | 生产调度状态 | ProduceDispatchStateStrategy |
| deliveryProgress | 发货进度 | DeliveryProgressStrategy |

## 7. 接口调用示例

### 7.1 Java调用示例
```java
// 示例代码，使用RestTemplate调用接口
RestTemplate restTemplate = new RestTemplate();

// 设置请求头
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.setBearerAuth("your_token");

// 设置请求体
Map<String, Object> requestBody = new HashMap<>();
requestBody.put("reportType", "sellDetailStatistics");
requestBody.put("page", 1);
requestBody.put("size", 10);

Map<String, Object> params = new HashMap<>();
params.put("startDate", "2025-01-01");
params.put("endDate", "2025-12-31");
requestBody.put("params", params);

// 发送请求
HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
ResponseEntity<Map> response = restTemplate.postForEntity(
    "http://localhost:8080/fpi/report/detail/query",
    requestEntity,
    Map.class
);

// 处理响应
Map<String, Object> responseBody = response.getBody();
System.out.println(responseBody);
```

### 7.2 Python调用示例
```python
import requests
import json

# 设置请求URL和 headers
url = "http://localhost:8080/fpi/report/detail/query"
headers = {
    "Content-Type": "application/json",
    "Authorization": "Bearer your_token"
}

# 设置请求体
request_data = {
    "reportType": "sellDetailStatistics",
    "params": {
        "startDate": "2025-01-01",
        "endDate": "2025-12-31"
    },
    "page": 1,
    "size": 10
}

# 发送请求
response = requests.post(url, headers=headers, data=json.dumps(request_data))

# 处理响应
if response.status_code == 200:
    print(json.dumps(response.json(), indent=2, ensure_ascii=False))
else:
    print(f"请求失败: {response.status_code} {response.text}")
```

## 8. 性能优化建议

### 8.1 客户端优化
1. **合理设置分页参数**：根据实际需求设置合适的`page`和`size`参数
2. **缓存查询结果**：对于频繁查询的报表，在客户端缓存结果
3. **异步调用**：对于耗时较长的查询，使用异步调用

### 8.2 服务端优化
1. **SQL优化**：优化SQL查询语句，添加合适的索引
2. **连接池配置**：合理配置数据库连接池
3. **缓存机制**：使用Redis等缓存中间件缓存热点数据
4. **异步处理**：对于复杂的报表查询，采用异步处理方式
5. **数据库读写分离**：对于读多写少的场景，采用数据库读写分离

## 9. 安全建议

### 9.1 认证授权
- 确保接口有完善的认证授权机制
- 使用JWT令牌进行身份验证
- 实现细粒度的权限控制

### 9.2 SQL注入防护
- 使用参数绑定防止SQL注入
- 对输入参数进行严格验证
- 实现输入参数的白名单过滤

### 9.3 数据脱敏
- 对于敏感数据，进行脱敏处理
- 实现数据访问的细粒度控制

## 10. 总结

`/fpi/report/detail/query` 是一个功能强大的报表查询接口，采用策略模式设计，支持多种报表类型，涵盖了企业运营的各个方面。该接口具有良好的扩展性，新的报表类型可以通过实现新的策略类轻松添加。

通过合理的SQL优化、缓存机制和异步处理，可以进一步提高接口的性能和稳定性，满足大规模并发查询的需求。