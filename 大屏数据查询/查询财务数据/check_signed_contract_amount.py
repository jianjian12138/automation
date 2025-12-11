#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import psycopg2
import psycopg2.extras

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


def check_signed_contract_amount():
    """检查已签章合同表中的合同金额分布"""
    conn = get_db_connection()
    if not conn:
        return
    
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
            # 查询已签章合同表中的合同金额分布，按identity分组
            sql_signed_contract_amount = """
            SELECT 
                identity,
                COUNT(*) AS contract_count,
                COALESCE(SUM(contract_amount), 0) AS total_contract_amount,
                COALESCE(MAX(contract_amount), 0) AS max_contract_amount,
                COALESCE(MIN(contract_amount), 0) AS min_contract_amount
            FROM dm_m9.base_module_t_dm_m9_signed_contract_9
            WHERE enterprise_code = %s AND delete_flag = false
            GROUP BY identity
            ORDER BY identity;
            """
            
            cur.execute(sql_signed_contract_amount, (enterprise_code,))
            contract_amount_stats = cur.fetchall()
            
            print("已签章合同表中的合同金额分布（按identity分组）：")
            print(f"{'identity':<10} {'合同数':<10} {'合同金额总和':<20} {'最大合同金额':<20} {'最小合同金额':<20}")
            print("-" * 80)
            for row in contract_amount_stats:
                print(f"{row['identity']:<10} {row['contract_count']:<10} {row['total_contract_amount']:<20} {row['max_contract_amount']:<20} {row['min_contract_amount']:<20}")
            
            # 查询identity=false的合同的详细信息
            sql_identity_false_contracts = """
            SELECT 
                primary_key AS contract_id,
                contract_number,
                contract_amount,
                identity,
                contract_type,
                create_time
            FROM dm_m9.base_module_t_dm_m9_signed_contract_9
            WHERE enterprise_code = %s AND delete_flag = false AND identity = false
            ORDER BY contract_amount DESC;
            """
            
            cur.execute(sql_identity_false_contracts, (enterprise_code,))
            identity_false_contracts = cur.fetchall()
            
            print("\nidentity=false（采购合同）的详细信息：")
            print(f"{'合同ID':<20} {'合同编号':<20} {'合同金额':<20} {'合同类型':<20} {'创建时间':<20}")
            print("-" * 100)
            for row in identity_false_contracts:
                print(f"{row['contract_id']:<20} {row['contract_number']:<20} {row['contract_amount']:<20} {row['contract_type']:<20} {row['create_time']:<20}")
            
            # 查询与API结果接近的应付总额和已付金额
            sql_api_approximation = """
            SELECT
                -- API应付总额接近值
                (SELECT COALESCE(SUM(sc.contract_amount), 0) 
                 FROM dm_m9.base_module_t_dm_m9_signed_contract_9 sc 
                 WHERE sc.enterprise_code = %s AND sc.delete_flag = false AND sc.identity = false) AS api_payable_total,
                -- API已付金额接近值
                (SELECT COALESCE(SUM(mr.confirmed_money), 0) 
                 FROM dm_m9.base_module_t_dm_m9_money_records_9 mr 
                 WHERE mr.enterprise_code = %s AND mr.delete_flag = false AND mr.money_record_type = 'PAYMENT' AND mr.approval_status = 'CONFIRMED') AS api_payed_total;
            """
            
            cur.execute(sql_api_approximation, (enterprise_code, enterprise_code))
            api_approximation = cur.fetchone()
            
            print(f"\nAPI应付总额接近值：{api_approximation['api_payable_total']:.4f}（约 {api_approximation['api_payable_total'] / 10000:.2f} 万元）")
            print(f"API已付金额接近值：{api_approximation['api_payed_total']:.4f}（约 {api_approximation['api_payed_total'] / 10000:.2f} 万元）")
            
            # 查询款项记录表中所有PAYMENT类型记录的confirmed_money总和
            sql_total_payment_confirmed_money = """
            SELECT COALESCE(SUM(confirmed_money), 0) AS total_payment_confirmed_money
            FROM dm_m9.base_module_t_dm_m9_money_records_9
            WHERE enterprise_code = %s AND delete_flag = false AND money_record_type = 'PAYMENT';
            """
            
            cur.execute(sql_total_payment_confirmed_money, (enterprise_code,))
            total_payment_confirmed_money = cur.fetchone()
            
            print(f"\n所有PAYMENT类型记录的confirmed_money总和：{total_payment_confirmed_money['total_payment_confirmed_money']:.4f}（约 {total_payment_confirmed_money['total_payment_confirmed_money'] / 10000:.2f} 万元）")
                
    except Exception as e:
        print(f"查询失败: {e}")
    finally:
        conn.close()


if __name__ == "__main__":
    check_signed_contract_amount()