#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import psycopg2
import psycopg2.extras
import os

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


def statistics_finance():
    """财务数据统计（与API一致，单位：元）"""
    print("\n1. 财务数据统计（与API一致，单位：元）")
    print("-" * 50)
    
    conn = get_db_connection()
    if not conn:
        return
    
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
            # 1. 总统计：应收总额，已收金额，应付总额，已付金额
            # 已收金额 = 所有收款类型（COLLECT）且已确认（CONFIRMED）的款项记录的 SUM(COALESCE(confirmed_money, record_amount)) 之和
            # 已付金额 = 所有付款类型（PAYMENT）且已确认（CONFIRMED）的款项记录的 SUM(COALESCE(confirmed_money, record_amount)) 之和
            # 与API查询逻辑保持一致
            sql_total = """
            SELECT 
                -- 已收金额（元）：所有收款类型且已确认的款项记录的SUM(COALESCE(confirmed_money, record_amount))之和
                COALESCE(SUM(CASE WHEN mr.money_record_type = 'COLLECT' AND mr.approval_status = 'CONFIRMED' THEN COALESCE(mr.confirmed_money, mr.record_amount) ELSE 0 END), 0) AS total_received,
                -- 已付金额（元）：所有付款类型且已确认的款项记录的SUM(COALESCE(confirmed_money, record_amount))之和
                COALESCE(SUM(CASE WHEN mr.money_record_type = 'PAYMENT' AND mr.approval_status = 'CONFIRMED' THEN COALESCE(mr.confirmed_money, mr.record_amount) ELSE 0 END), 0) AS total_payed
            FROM dm_m9.base_module_t_dm_m9_money_records_9 mr
            WHERE mr.enterprise_code = %s AND mr.delete_flag = 'f';
            """
            
            cur.execute(sql_total, (enterprise_code,))
            total_result = cur.fetchone()
            
            # 2. 单独查询应收总额和应付总额
            # 应收总额 = 所有销售合同（SELL_CONTRACT）的 contract_amount 之和
            # 应付总额 = 所有采购合同（PURCHASE_CONTRACT）的 contract_amount 之和
            # 与API查询条件保持一致：添加canceled条件，使用正确的delete_flag格式
            sql_contract_amount = """
            SELECT 
                -- 应收总额（元）：所有销售合同的contract_amount之和
                COALESCE(SUM(CASE WHEN contract_type = 'SELL_CONTRACT' THEN contract_amount ELSE 0 END), 0) AS total_receivable,
                -- 应付总额（元）：所有采购合同的contract_amount之和
                COALESCE(SUM(CASE WHEN contract_type = 'PURCHASE_CONTRACT' THEN contract_amount ELSE 0 END), 0) AS total_payable
            FROM dm_m9.base_module_t_dm_m9_signed_contract_9
            WHERE enterprise_code = %s AND delete_flag = 'f' AND (canceled = 'false' OR canceled IS NULL);
            """
            
            cur.execute(sql_contract_amount, (enterprise_code,))
            contract_amount_result = cur.fetchone()
            
            # 计算未收和未付金额
            total_receivable = contract_amount_result['total_receivable']
            total_received = total_result['total_received']
            total_payable = contract_amount_result['total_payable']
            total_payed = total_result['total_payed']
            
            # 未收金额 = 应收总额 - 已收金额
            total_unreceived = total_receivable - total_received
            # 未付金额 = 应付总额 - 已付金额
            total_unpayed = total_payable - total_payed
            
            print(f"应收总额: {total_receivable}")
            print(f"已收金额: {total_received}")
            print(f"未收金额: {total_unreceived}")
            print(f"应付总额: {total_payable}")
            print(f"已付金额: {total_payed}")
            print(f"未付金额: {total_unpayed}")
            
            # 打印与API响应对比
            print("\n与API响应对比：")
            print(f"API应收总额: 4833627992.2501")
            print(f"脚本应收总额: {contract_amount_result['total_receivable']}")
            print(f"API已收金额: 4128804.6701")
            print(f"脚本已收金额: {total_result['total_received']}")
            print(f"API应付总额: 5386061.9082")
            print(f"脚本应付总额: {contract_amount_result['total_payable']}")
            print(f"API已付金额: 1243707.5633333333333")
            print(f"脚本已付金额: {total_result['total_payed']}")
            
            # 3. 按月份统计已收金额和未收金额（万元）
            print("\n按月份统计应收已收（万元）：")
            print(f"{'月份':<10} {'已收金额':<15} {'未收金额':<15}")
            print("-" * 40)
            
            # 总应收金额（万元）
            total_receivable_10k = contract_amount_result['total_receivable'] / 10000
            
            # 计算每月的已收金额和累计已收金额
            sql_monthly_received = """
            WITH months AS (
                SELECT '2025-01' AS month UNION ALL SELECT '2025-02' AS month UNION ALL
                SELECT '2025-03' AS month UNION ALL SELECT '2025-04' AS month UNION ALL
                SELECT '2025-05' AS month UNION ALL SELECT '2025-06' AS month UNION ALL
                SELECT '2025-07' AS month UNION ALL SELECT '2025-08' AS month UNION ALL
                SELECT '2025-09' AS month UNION ALL SELECT '2025-10' AS month UNION ALL
                SELECT '2025-11' AS month UNION ALL SELECT '2025-12' AS month
            ),
            monthly_data AS (
                SELECT 
                    m.month,
                    COALESCE(SUM(CASE WHEN mr.money_record_type = 'COLLECT' AND mr.approval_status = 'CONFIRMED' THEN COALESCE(mr.confirmed_money, mr.record_amount) ELSE 0 END), 0) / 10000 AS monthly_received
                FROM months m
                LEFT JOIN dm_m9.base_module_t_dm_m9_money_records_9 mr ON TO_CHAR(mr.create_time, 'YYYY-MM') = m.month AND mr.enterprise_code = %s AND mr.delete_flag = 'f'
                GROUP BY m.month
                ORDER BY m.month
            )
            SELECT 
                month,
                monthly_received,
                %s - SUM(monthly_received) OVER (ORDER BY month) AS monthly_unreceived
            FROM monthly_data;
            """
            
            cur.execute(sql_monthly_received, (enterprise_code, total_receivable_10k))
            monthly_received_results = cur.fetchall()
            
            for row in monthly_received_results:
                print(f"{row['month']:<10} {row['monthly_received']:<15.2f} {row['monthly_unreceived']:<15.2f}")
            
            # 4. 按月份统计已付金额和未付金额（万元）
            print("\n按月份统计应付已付（万元）：")
            print(f"{'月份':<10} {'已付金额':<15} {'未付金额':<15}")
            print("-" * 40)
            
            # 总应付金额（万元）
            total_payable_10k = contract_amount_result['total_payable'] / 10000
            
            # 计算每月的已付金额和累计已付金额
            sql_monthly_payed = """
            WITH months AS (
                SELECT '2025-01' AS month UNION ALL SELECT '2025-02' AS month UNION ALL
                SELECT '2025-03' AS month UNION ALL SELECT '2025-04' AS month UNION ALL
                SELECT '2025-05' AS month UNION ALL SELECT '2025-06' AS month UNION ALL
                SELECT '2025-07' AS month UNION ALL SELECT '2025-08' AS month UNION ALL
                SELECT '2025-09' AS month UNION ALL SELECT '2025-10' AS month UNION ALL
                SELECT '2025-11' AS month UNION ALL SELECT '2025-12' AS month
            ),
            monthly_data AS (
                SELECT 
                    m.month,
                    COALESCE(SUM(CASE WHEN mr.money_record_type = 'PAYMENT' AND mr.approval_status = 'CONFIRMED' THEN COALESCE(mr.confirmed_money, mr.record_amount) ELSE 0 END), 0) / 10000 AS monthly_payed
                FROM months m
                LEFT JOIN dm_m9.base_module_t_dm_m9_money_records_9 mr ON TO_CHAR(mr.create_time, 'YYYY-MM') = m.month AND mr.enterprise_code = %s AND mr.delete_flag = 'f'
                GROUP BY m.month
                ORDER BY m.month
            )
            SELECT 
                month,
                monthly_payed,
                %s - SUM(monthly_payed) OVER (ORDER BY month) AS monthly_unpayed
            FROM monthly_data;
            """
            
            cur.execute(sql_monthly_payed, (enterprise_code, total_payable_10k))
            monthly_payed_results = cur.fetchall()
            
            for row in monthly_payed_results:
                print(f"{row['month']:<10} {row['monthly_payed']:<15.2f} {row['monthly_unpayed']:<15.2f}")
                
    except Exception as e:
        print(f"查询失败: {e}")
    finally:
        conn.close()


def statistics_customer_factor():
    """客户系数统计"""
    print("\n2. 客户系数统计")
    print("-" * 50)
    
    conn = get_db_connection()
    if not conn:
        return
    
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
            # 统计有价格系数的客户数据
            sql_customer_factor = """
            SELECT 
                c.customers_sign AS customer_name,  -- 客户名称
                c.customer_grade AS customer_grade,  -- 客户等级
                c.price_factor AS price_factor  -- 价格系数
            FROM dm_m9.sell_t_dm_m9_customers_9 c 
            WHERE c.enterprise_code = %s AND c.delete_flag = false AND c.price_factor IS NOT NULL
            ORDER BY c.customers_sign;
            """
            
            cur.execute(sql_customer_factor, (enterprise_code,))
            customer_results = cur.fetchall()
            
            if not customer_results:
                print("暂无客户系数数据")
                return
            
            print(f"{'客户名称':<20} {'客户等级':<15} {'价格系数':<10}")
            print("-" * 45)
            
            for row in customer_results:
                # 将Boolean类型的customer_grade转换为中文描述
                grade_desc = "未知"
                if row['customer_grade'] is not None:
                    if row['customer_grade'] is False:
                        grade_desc = "军工级"
                    elif row['customer_grade'] is True:
                        grade_desc = "工业级"
                print(f"{row['customer_name']:<20} {grade_desc:<15} {row['price_factor']:<10}")
                
    except Exception as e:
        print(f"查询失败: {e}")
    finally:
        conn.close()


if __name__ == "__main__":
    print("财务数据统计")
    print("=" * 50)
    
    statistics_finance()
    statistics_customer_factor()
    
    print("\n" + "=" * 50)
    print("统计完成")