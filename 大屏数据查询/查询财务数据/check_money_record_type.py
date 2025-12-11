#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import psycopg2

# 数据库连接配置
db_config = {
    'host': '192.168.2.172',
    'port': 5432,
    'user': 'postgres',
    'password': 'postgres',
    'database': 'micgenerp'
}

# 企业编码
enterprise_code = '190787210592256000'

def get_db_connection():
    """获取数据库连接"""
    try:
        conn = psycopg2.connect(
            host=db_config['host'],
            port=db_config['port'],
            user=db_config['user'],
            password=db_config['password'],
            database=db_config['database']
        )
        return conn
    except Exception as e:
        print(f"数据库连接失败: {e}")
        return None

def check_money_record_type():
    """查询money_record_type字段的实际值"""
    conn = get_db_connection()
    if not conn:
        return
    
    try:
        with conn.cursor() as cur:
            # 查询money_record_type的所有可能值
            sql = """
            SELECT DISTINCT money_record_type 
            FROM dm_m9.base_module_t_dm_m9_money_records_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            
            cur.execute(sql, (enterprise_code,))
            types = cur.fetchall()
            
            print("money_record_type的实际值：")
            for row in types:
                print(f"  - {row[0]}")
                
            # 查询一些示例数据，看看字段值
            sql_example = """
            SELECT money_record_type, contract_amount, confirmed_money 
            FROM dm_m9.base_module_t_dm_m9_money_records_9 
            WHERE enterprise_code = %s AND delete_flag = false 
            LIMIT 5;
            """
            
            print("\n示例数据：")
            cur.execute(sql_example, (enterprise_code,))
            examples = cur.fetchall()
            for row in examples:
                print(f"  money_record_type: {row[0]}, contract_amount: {row[1]}, confirmed_money: {row[2]}")
                
    except Exception as e:
        print(f"查询失败: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    check_money_record_type()
