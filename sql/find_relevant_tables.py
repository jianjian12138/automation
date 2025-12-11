#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
查找与客户和销售合同相关的表
"""

import sys
import os

# 添加项目根目录到Python路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from keyword_utils.db_utils import DataBase
from libs.config_center import ENV

def find_relevant_tables():
    """查找与客户和销售合同相关的表"""
    with DataBase(ENV["ERP_TEST"]["data_base"]["default"]) as db:
        # 查找包含customer或contract的表
        sql = """
        SELECT schemaname, tablename 
        FROM pg_tables 
        WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
        AND (tablename ILIKE '%customer%' OR tablename ILIKE '%contract%' OR tablename ILIKE '%sell%')
        ORDER BY schemaname, tablename;
        """
        
        try:
            result = db.postgres_execute(sql)
            print("\n=== 与客户和销售合同相关的表 ===")
            print("Schema\t\t表名")
            print("-" * 50)
            
            for row in result:
                schemaname, tablename = row
                print(f"{schemaname}\t\t{tablename}")
            
            return result
        except Exception as e:
            print(f"查找相关表失败: {e}")
            return []

def main():
    """主函数"""
    print("查找与客户和销售合同相关的表...")
    find_relevant_tables()

if __name__ == "__main__":
    main()
