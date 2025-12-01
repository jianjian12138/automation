#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
测试JSONPath断言的OR逻辑
用于验证断言问题的根本原因
"""

import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from jsonpath_ng import parse
# 直接导入类和方法
from core.test_executor import APITestExecutor

# 模拟实际的API响应（只有resCode，没有code）
test_data = {
    "resCode": "200",
    "msg": "获得可用的销售合同模板成功",
    "data": {
        "pageDetails": [
            {
                "type": None,
                "templateCode": "456189212611706880",
                "templateName": "新合同模版",
                "orderValue": 0,
                "state": None,
                "dataVersion": "0",
                "canDelete": False,
                "canUpdateOrderValue": False
            }
        ],
        "totalNum": 1
    },
    "customPageBaseConfig": None
}

# 测试断言
test_assertion = "$..code == 200 or $..resCode == 200 or $..code == 202 or $..resCode == 202"

print("=" * 80)
print("测试JSONPath断言OR逻辑")
print("=" * 80)
print(f"\n测试数据: {test_data}")
print(f"\n测试断言: {test_assertion}")
print("\n" + "-" * 80)

# 手动测试OR逻辑
print("\n【步骤1】手动测试OR逻辑的每个条件:")
import re
or_pattern = re.compile(r'\s+or\s+', re.IGNORECASE)
conditions = [cond.strip() for cond in or_pattern.split(test_assertion)]
print(f"拆分后的条件: {conditions}")

for i, condition in enumerate(conditions, 1):
    print(f"\n  条件 {i}: {condition}")
    try:
        pattern = r'(\$[^\s]+)\s*(==|!=|>=|<=|>|<)\s*(".*?"|\'.*?\'|[^\s]+)'
        match = re.match(pattern, condition)
        
        if match:
            path_expr = match.group(1)
            operator = match.group(2)
            expected = match.group(3)
            
            # 去除引号
            if expected.startswith('"') and expected.endswith('"'):
                expected = expected[1:-1]
            elif expected.startswith("'") and expected.endswith("'"):
                expected = expected[1:-1]
            
            print(f"    路径表达式: {path_expr}")
            print(f"    操作符: {operator}")
            print(f"    期望值: {expected} (类型: {type(expected).__name__})")
            
            # 执行JSONPath查询
            jsonpath_expr = parse(path_expr)
            matches = list(jsonpath_expr.find(test_data))
            
            print(f"    找到匹配数: {len(matches)}")
            
            if matches:
                for j, m in enumerate(matches):
                    actual_value = m.value
                    print(f"    匹配值 {j+1}: {actual_value} (类型: {type(actual_value).__name__})")
                    
                    # 测试值比较
                    actual_str = str(actual_value)
                    compare_result = actual_str == expected
                    print(f"    比较结果 (str({actual_value}) == {expected}): {compare_result}")
                    
                    if compare_result:
                        print(f"    [OK] 条件满足！")
                        break
                    else:
                        print(f"    [FAIL] 条件不满足")
            else:
                print(f"    [FAIL] 路径未找到数据")
        else:
            print(f"    [FAIL] 无法解析条件格式")
    except Exception as e:
        print(f"    [ERROR] 检查条件时出错: {e}")
        import traceback
        traceback.print_exc()

print("\n" + "-" * 80)
print("\n【步骤2】直接测试_assert_jsonpath方法:")
print("-" * 80)

# 直接创建APITestExecutor实例并测试_assert_jsonpath方法
try:
    # 创建一个简单的测试用例路径
    import tempfile
    import yaml
    with tempfile.NamedTemporaryFile(mode='w', suffix='.yaml', delete=False) as f:
        yaml.dump({'case_code': 'test', 'case_name': 'test'}, f)
        temp_config = f.name
    
    executor = APITestExecutor(temp_config)
    result = {'errors': []}
    
    print(f"调用_assert_jsonpath方法...")
    executor._assert_jsonpath(test_data, test_assertion, result)
    
    if result['errors']:
        print(f"\n[FAIL] 断言失败，错误信息:")
        for error in result['errors']:
            print(f"  - {error}")
    else:
        print(f"\n[OK] 断言通过！")
    
    # 清理临时文件
    import os
    os.unlink(temp_config)
        
except Exception as e:
    print(f"\n[ERROR] 测试出错: {e}")
    import traceback
    traceback.print_exc()

print("\n" + "=" * 80)
print("测试完成")
print("=" * 80)

