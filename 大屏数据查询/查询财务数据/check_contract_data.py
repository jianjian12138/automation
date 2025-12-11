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

def check_contract_data():
    """检查合同数据，看看是否与接口返回的数据一致"""
    conn = get_db_connection()
    if not conn:
        return
    
    try:
        with conn.cursor() as cur:
            # 检查销售合同总额
            print("\n1. 检查销售合同数据：")
            sql_sell_contract = """
            SELECT COALESCE(SUM(contract_amount), 0) AS total_contract_amount
            FROM dm_m9.sell_t_dm_m9_sell_contract_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cur.execute(sql_sell_contract, (enterprise_code,))
            sell_contract = cur.fetchone()
            print(f"   销售合同总额：{sell_contract[0]:.4f}（约{sell_contract[0]/10000:.2f}万元）")
            
            # 检查采购合同总额
            print("\n2. 检查采购合同数据：")
            sql_purchase_contract = """
            SELECT COALESCE(SUM(contract_amount), 0) AS total_contract_amount
            FROM dm_m9.purchase_t_dm_m9_purchase_contract_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cur.execute(sql_purchase_contract, (enterprise_code,))
            purchase_contract = cur.fetchone()
            print(f"   采购合同总额：{purchase_contract[0]:.4f}（约{purchase_contract[0]/10000:.2f}万元）")
            
            # 检查财务操作记录
            print("\n3. 检查财务操作记录：")
            sql_finance_operate = """
            SELECT 
                COALESCE(SUM(CASE WHEN money_type = 'INCOME' THEN money_amount ELSE 0 END), 0) AS total_income,
                COALESCE(SUM(CASE WHEN money_type = 'EXPENSE' THEN money_amount ELSE 0 END), 0) AS total_expense
            FROM dm_m9.financing_t_dm_m9_contract_financing_operate_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cur.execute(sql_finance_operate, (enterprise_code,))
            finance_operate = cur.fetchone()
            print(f"   财务收入总额：{finance_operate[0]:.4f}（约{finance_operate[0]/10000:.2f}万元）")
            print(f"   财务支出总额：{finance_operate[1]:.4f}（约{finance_operate[1]/10000:.2f}万元）")
            
            # 检查财务操作记录的money_type
            print("\n4. 检查财务操作记录的money_type：")
            sql_money_type = """
            SELECT DISTINCT money_type 
            FROM dm_m9.financing_t_dm_m9_contract_financing_operate_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cur.execute(sql_money_type, (enterprise_code,))
            money_types = cur.fetchall()
            print(f"   money_type的实际值：")
            for mt in money_types:
                print(f"     - {mt[0]}")
                
    except Exception as e:
        print(f"查询失败: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    check_contract_data()
