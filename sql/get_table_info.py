#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
获取表结构信息并生成正确的SQL查询语句
"""

import sys
import os

# 添加项目根目录到Python路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from keyword_utils.db_utils import DataBase
from libs.config_center import ENV

def get_table_columns(table_name):
    """获取指定表的字段信息"""
    # 使用项目中的数据库工具类连接数据库
    with DataBase(ENV["ERP_TEST"]["data_base"]["default"]) as db:
        # 查询表的字段信息
        sql = f"""
        SELECT column_name, data_type, is_nullable, column_default, ordinal_position
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = '{table_name}'
        ORDER BY ordinal_position;
        """
        
        try:
            result = db.postgres_execute(sql)
            print(f"\n=== {table_name} 表结构 ===")
            print("字段名\t\t数据类型\t\t是否为空\t\t默认值\t\t位置")
            print("-" * 80)
            
            columns = []
            for row in result:
                column_name, data_type, is_nullable, column_default, ordinal_position = row
                columns.append(column_name)
                print(f"{column_name}\t\t{data_type}\t\t{is_nullable}\t\t{column_default}\t\t{ordinal_position}")
            
            return columns
        except Exception as e:
            print(f"获取表结构失败: {e}")
            return []

def generate_correct_sql(customer_columns, contract_columns):
    """根据表结构生成正确的SQL查询语句"""
    # 查找关联字段
    common_columns = set(customer_columns) & set(contract_columns)
    print(f"\n=== 两表公共字段 ===")
    for col in common_columns:
        print(f"- {col}")
    
    # 生成SQL查询语句
    关联字段 = "customer_code" if "customer_code" in common_columns else "id"
    
    sql = f"""
-- 查询销售客户已签章的订单合同数量
SELECT 
    c.customer_name AS "客户名称",
    c.customer_code AS "客户编码",
    COUNT(sc.id) AS "已签章合同数量"
FROM 
    sell_t_dm_m9_customers_9 c
INNER JOIN 
    sell_t_dm_m9_sell_contract_9 sc 
ON 
    c.{关联字段} = sc.{关联字段}
WHERE 
    sc.signed_contract IS NOT NULL  -- 已签章的合同，signed_contract字段不为空
    AND sc.signed_contract != ''     -- 排除空字符串
    AND sc.is_deleted = false        -- 排除已删除的合同
GROUP BY 
    c.id, c.customer_name, c.customer_code
ORDER BY 
    "已签章合同数量" DESC;
    """
    
    print(f"\n=== 生成的SQL查询语句 ===")
    print(sql)
    
    # 保存到文件
    with open("query_signed_contracts_final.sql", "w", encoding="utf-8") as f:
        f.write(sql)
    print("\n✅ SQL语句已保存到 query_signed_contracts_final.sql")

def main():
    """主函数"""
    print("正在获取表结构信息...")
    
    # 获取客户表和合同表的字段信息
    customer_columns = get_table_columns("sell_t_dm_m9_customers_9")
    contract_columns = get_table_columns("sell_t_dm_m9_sell_contract_9")
    
    if customer_columns and contract_columns:
        generate_correct_sql(customer_columns, contract_columns)
    else:
        print("\n❌ 无法获取表结构信息，无法生成SQL语句")

if __name__ == "__main__":
    main()
