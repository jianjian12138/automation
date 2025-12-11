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

def check_sell_order_data():
    """检查销售订单数据，看看是否包含应收金额信息"""
    conn = get_db_connection()
    if not conn:
        return
    
    try:
        with conn.cursor() as cur:
            # 检查销售订单表的结构
            print("\n1. 检查销售订单表的结构：")
            sql_sell_order_columns = """
            SELECT column_name, data_type 
            FROM information_schema.columns 
            WHERE table_name = 'sell_t_dm_m9_sell_order_9' AND table_schema = 'dm_m9' 
            ORDER BY ordinal_position;
            """
            cur.execute(sql_sell_order_columns)
            columns = cur.fetchall()
            print(f"   销售订单表的列（前20个）：")
            for i, col in enumerate(columns[:20]):
                print(f"     {i+1}. {col[0]} ({col[1]})")
            
            # 检查销售订单表的总金额
            print("\n2. 检查销售订单表的总金额：")
            sql_sell_order_total = """
            SELECT 
                COALESCE(SUM(contract_amount), 0) AS total_contract_amount,  -- 合同金额
                COALESCE(SUM(order_amount), 0) AS total_order_amount,  -- 订单金额
                COALESCE(SUM(received_amount), 0) AS total_received_amount  -- 已收金额
            FROM dm_m9.sell_t_dm_m9_sell_order_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cur.execute(sql_sell_order_total, (enterprise_code,))
            sell_order_total = cur.fetchone()
            print(f"   销售订单总合同金额：{sell_order_total[0]:.4f} 元（约{sell_order_total[0]/10000:.2f}万元）")
            print(f"   销售订单总订单金额：{sell_order_total[1]:.4f} 元（约{sell_order_total[1]/10000:.2f}万元）")
            print(f"   销售订单总已收金额：{sell_order_total[2]:.4f} 元（约{sell_order_total[2]/10000:.2f}万元）")
            
            # 检查采购订单表的总金额
            print("\n3. 检查采购订单表的总金额：")
            sql_purchase_order_total = """
            SELECT 
                COALESCE(SUM(contract_amount), 0) AS total_contract_amount,  -- 合同金额
                COALESCE(SUM(order_amount), 0) AS total_order_amount,  -- 订单金额
                COALESCE(SUM(payed_amount), 0) AS total_payed_amount  -- 已付金额
            FROM dm_m9.purchase_t_dm_m9_purchase_order_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cur.execute(sql_purchase_order_total, (enterprise_code,))
            purchase_order_total = cur.fetchone()
            print(f"   采购订单总合同金额：{purchase_order_total[0]:.4f} 元（约{purchase_order_total[0]/10000:.2f}万元）")
            print(f"   采购订单总订单金额：{purchase_order_total[1]:.4f} 元（约{purchase_order_total[1]/10000:.2f}万元）")
            print(f"   采购订单总已付金额：{purchase_order_total[2]:.4f} 元（约{purchase_order_total[2]/10000:.2f}万元）")
            
    except Exception as e:
        print(f"查询失败: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    check_sell_order_data()
