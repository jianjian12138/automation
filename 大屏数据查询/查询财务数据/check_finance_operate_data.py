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

def check_finance_operate_data():
    """检查财务操作记录的实际数据"""
    conn = get_db_connection()
    if not conn:
        return
    
    try:
        with conn.cursor() as cur:
            # 查询财务操作记录的 operation_type 实际值
            print("\n1. 检查 operation_type 字段的实际值：")
            sql_operation_type = """
            SELECT DISTINCT operation_type 
            FROM dm_m9.financing_t_dm_m9_contract_financing_operate_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cur.execute(sql_operation_type, (enterprise_code,))
            operation_types = cur.fetchall()
            print(f"   operation_type 的实际值：")
            for ot in operation_types:
                print(f"     - {ot[0]}")
            
            # 查询财务操作记录的示例数据
            print("\n2. 检查财务操作记录的示例数据：")
            sql_example = """
            SELECT operation_type, apply_amount, create_time 
            FROM dm_m9.financing_t_dm_m9_contract_financing_operate_9 
            WHERE enterprise_code = %s AND delete_flag = false 
            LIMIT 10;
            """
            cur.execute(sql_example, (enterprise_code,))
            examples = cur.fetchall()
            print(f"   示例数据（共10条）：")
            for row in examples:
                print(f"     operation_type: {row[0]}, apply_amount: {row[1]}, create_time: {row[2]}")
            
            # 查询财务操作记录的总数和总金额
            print("\n3. 检查财务操作记录的总数和总金额：")
            sql_total = """
            SELECT 
                COUNT(*) AS total_records,
                COALESCE(SUM(apply_amount), 0) AS total_amount
            FROM dm_m9.financing_t_dm_m9_contract_financing_operate_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cur.execute(sql_total, (enterprise_code,))
            total = cur.fetchone()
            print(f"   总记录数：{total[0]}")
            print(f"   总金额：{total[1]:.4f}（约{total[1]/10000:.2f}万元）")
            
            # 查询 base_module_t_dm_m9_money_records_9 表的示例数据
            print("\n4. 检查 base_module_t_dm_m9_money_records_9 表的示例数据：")
            sql_money_records = """
            SELECT money_record_type, contract_amount, confirmed_money, create_time 
            FROM dm_m9.base_module_t_dm_m9_money_records_9 
            WHERE enterprise_code = %s AND delete_flag = false 
            LIMIT 10;
            """
            cur.execute(sql_money_records, (enterprise_code,))
            money_records = cur.fetchone()
            print(f"   示例数据：{money_records}")
            
    except Exception as e:
        print(f"查询失败: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    check_finance_operate_data()
