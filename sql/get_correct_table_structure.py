#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
执行修改后的SQL文件，获取正确的表结构信息
"""

import sys
import os

# 添加项目根目录到Python路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from keyword_utils.db_utils import DataBase
from libs.config_center import ENV

def execute_sql_file(file_path):
    """执行SQL文件"""
    with open(file_path, 'r', encoding='utf-8') as f:
        sql_content = f.read()
    
    # 分割SQL语句（以;分割）
    sql_statements = []
    current_statement = ""
    
    for line in sql_content.splitlines():
        # 跳过注释行
        if line.strip().startswith('--'):
            continue
        
        current_statement += line + '\n'
        
        # 如果遇到分号且不是字符串内的分号，分割语句
        if ';' in line and line.count('"') % 2 == 0 and line.count("'") % 2 == 0:
            sql_statements.append(current_statement.strip())
            current_statement = ""
    
    # 执行每个SQL语句
    with DataBase(ENV["ERP_TEST"]["data_base"]["default"]) as db:
        results = []
        for sql in sql_statements:
            if sql:
                print(f"\n=== 执行SQL语句 ===")
                print(sql)
                result = db.postgres_execute(sql)
                results.append(result)
                
                # 如果是查询语句，打印结果
                if sql.strip().lower().startswith('select'):
                    print(f"\n=== 查询结果 ===")
                    for row in result:
                        print(row)
        
        return results

def generate_final_sql(customer_columns, contract_columns):
    """
    根据表结构生成最终的SQL查询语句
    注意：这个函数需要根据实际获取的表结构来调整
    """
    print(f"\n=== 生成最终SQL查询 ===")
    print(f"客户表字段: {customer_columns}")
    print(f"合同表字段: {contract_columns}")
    
    # 这里需要根据实际获取的字段名来调整
    final_sql = """
-- 查询销售客户已签章的订单合同数量
SELECT 
    c.customer_name AS "客户名称",
    c.customer_code AS "客户编码",
    COUNT(sc.id) AS "已签章合同数量"
FROM 
    dm_m9.sell_t_dm_m9_customers_9 c
INNER JOIN 
    dm_m9.sell_t_dm_m9_sell_contract_9 sc 
ON 
    c.id = sc.customer_id  -- 需要根据实际外键关系调整
WHERE 
    sc.signed_contract IS NOT NULL  -- 已签章条件
    AND sc.signed_contract != ''     -- 排除空字符串
    AND sc.is_deleted = false        -- 删除标记
GROUP BY 
    c.id, c.customer_name, c.customer_code
ORDER BY 
    "已签章合同数量" DESC;
    """
    
    print(final_sql)
    
    # 保存到文件
    with open("final_signed_contract_query.sql", "w", encoding="utf-8") as f:
        f.write(final_sql)
    
    print("\n✅ 最终SQL查询已保存到 final_signed_contract_query.sql")

def main():
    """主函数"""
    sql_file_path = r"f:\JJ_test\automation-test-platform\sql\get_table_structure.sql"
    print(f"执行SQL文件: {sql_file_path}")
    
    results = execute_sql_file(sql_file_path)
    
    # 假设results[0]是客户表结构，results[1]是合同表结构
    if len(results) >= 2:
        customer_columns = [row[0] for row in results[0]]  # 提取客户表字段名
        contract_columns = [row[0] for row in results[1]]  # 提取合同表字段名
        generate_final_sql(customer_columns, contract_columns)
    else:
        print("\n❌ 无法获取完整的表结构信息")

if __name__ == "__main__":
    main()
