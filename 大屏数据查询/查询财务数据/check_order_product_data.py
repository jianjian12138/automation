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

def check_order_product_data():
    """检查销售订单产品明细表和采购订单产品明细表，看看是否包含金额信息"""
    conn = get_db_connection()
    if not conn:
        return
    
    try:
        with conn.cursor() as cur:
            # 检查销售订单产品明细表
            print("\n1. 检查销售订单产品明细表：")
            sql_sell_order_product = """
            SELECT 
                COALESCE(SUM(contract_amount), 0) AS total_contract_amount,  -- 合同金额
                COALESCE(SUM(total_amount), 0) AS total_total_amount,  -- 总金额
                COUNT(*) AS total_records
            FROM dm_m9.sell_t_dm_m9_sell_order_product_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cur.execute(sql_sell_order_product, (enterprise_code,))
            sell_order_product = cur.fetchone()
            print(f"   销售订单产品明细：")
            print(f"     合同金额总和：{sell_order_product[0]:.4f} 元（约{sell_order_product[0]/10000:.2f}万元）")
            print(f"     总金额总和：{sell_order_product[1]:.4f} 元（约{sell_order_product[1]/10000:.2f}万元）")
            print(f"     记录总数：{sell_order_product[2]}")
            
            # 检查采购订单产品明细表
            print("\n2. 检查采购订单产品明细表：")
            sql_purchase_order_product = """
            SELECT 
                COALESCE(SUM(contract_amount), 0) AS total_contract_amount,  -- 合同金额
                COALESCE(SUM(total_amount), 0) AS total_total_amount,  -- 总金额
                COUNT(*) AS total_records
            FROM dm_m9.purchase_t_dm_m9_purchase_order_product_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cur.execute(sql_purchase_order_product, (enterprise_code,))
            purchase_order_product = cur.fetchone()
            print(f"   采购订单产品明细：")
            print(f"     合同金额总和：{purchase_order_product[0]:.4f} 元（约{purchase_order_product[0]/10000:.2f}万元）")
            print(f"     总金额总和：{purchase_order_product[1]:.4f} 元（约{purchase_order_product[1]/10000:.2f}万元）")
            print(f"     记录总数：{purchase_order_product[2]}")
            
            # 检查销售报价单表
            print("\n3. 检查销售报价单表：")
            sql_sell_quotation = """
            SELECT 
                COALESCE(SUM(total_amount), 0) AS total_total_amount,  -- 总金额
                COUNT(*) AS total_records
            FROM dm_m9.sell_t_dm_m9_sell_quotation_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cur.execute(sql_sell_quotation, (enterprise_code,))
            sell_quotation = cur.fetchone()
            print(f"   销售报价单：")
            print(f"     总金额总和：{sell_quotation[0]:.4f} 元（约{sell_quotation[0]/10000:.2f}万元）")
            print(f"     记录总数：{sell_quotation[1]}")
            
            # 检查采购报价单表
            print("\n4. 检查采购报价单表：")
            sql_purchase_quotation = """
            SELECT 
                COALESCE(SUM(total_amount), 0) AS total_total_amount,  -- 总金额
                COUNT(*) AS total_records
            FROM dm_m9.purchase_t_dm_m9_buyer_quotation_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cur.execute(sql_purchase_quotation, (enterprise_code,))
            purchase_quotation = cur.fetchone()
            print(f"   采购报价单：")
            print(f"     总金额总和：{purchase_quotation[0]:.4f} 元（约{purchase_quotation[0]/10000:.2f}万元）")
            print(f"     记录总数：{purchase_quotation[1]}")
            
    except Exception as e:
        print(f"查询失败: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    check_order_product_data()
