#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
销售数据统计脚本
企业编码: 190787210592256000
"""

import psycopg2
import psycopg2.extras
import yaml
from datetime import datetime
import os

# 数据库连接配置
DB_CONFIG = {
    'host': '192.168.2.172',
    'port': 5432,
    'database': 'micgenerp',
    'user': 'postgres',
    'password': 'postgres'
}

ENTERPRISE_CODE = '190787210592256000'


def get_db_connection():
    """获取数据库连接"""
    return psycopg2.connect(**DB_CONFIG)


def statistics_sales_data():
    """
    统计销售数据: 年销售总额，月度销售总额和已收未收
    """
    print("=== 1. 销售数据统计 ===")
    conn = get_db_connection()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
            # 年销售总额 - 使用与5月接口一致的逻辑，直接查询history_price表
            cur.execute("SELECT COALESCE(SUM(product_rmb_total_price), 0) AS annual_sales_total FROM dm_m9.base_module_t_dm_m9_history_price_9 WHERE enterprise_code = %s AND delete_flag = 'f' AND type = 1 AND record_time IS NOT NULL AND EXTRACT(YEAR FROM record_time) = EXTRACT(YEAR FROM CURRENT_DATE);", (ENTERPRISE_CODE,))
            annual_result = cur.fetchone()
            print(f"年销售总额: {annual_result['annual_sales_total']:.2f} 元")
            
            # 查询月度销售总额数据 - 使用与5月.md一致的逻辑
            cur.execute("SELECT TO_CHAR(record_time, 'YYYY-MM') AS month, COALESCE(SUM(product_rmb_total_price), 0) AS monthly_sales_total FROM dm_m9.base_module_t_dm_m9_history_price_9 WHERE enterprise_code = %s AND delete_flag = 'f' AND type = 1 AND record_time IS NOT NULL AND EXTRACT(YEAR FROM record_time) = EXTRACT(YEAR FROM CURRENT_DATE) GROUP BY TO_CHAR(record_time, 'YYYY-MM') ORDER BY month;", (ENTERPRISE_CODE,))
            monthly_sales = cur.fetchall()
            
            # 查询月度已收未收统计数据 - 使用与5月.md完全一致的逻辑，先按合同聚合，再计算月度数据
            cur.execute("""
            WITH contract_sales AS (
                SELECT 
                    TO_CHAR(record_time, 'YYYY-MM') AS month,
                    signed_contract,
                    SUM(product_rmb_total_price) AS contract_sales
                FROM dm_m9.base_module_t_dm_m9_history_price_9
                WHERE enterprise_code = %s 
                    AND delete_flag = 'f'
                    AND type = 1
                    AND record_time IS NOT NULL
                    AND EXTRACT(YEAR FROM record_time) = EXTRACT(YEAR FROM CURRENT_DATE)
                GROUP BY TO_CHAR(record_time, 'YYYY-MM'), signed_contract
            ),
            contract_received AS (
                SELECT 
                    signed_contract,
                    SUM(COALESCE(confirmed_money, record_amount)) AS contract_received
                FROM dm_m9.base_module_t_dm_m9_money_records_9
                WHERE enterprise_code = %s
                    AND approval_status = 'CONFIRMED'
                    AND delete_flag = 'f'
                    AND signed_contract IN (SELECT signed_contract FROM contract_sales)
                GROUP BY signed_contract
            )
            SELECT 
                cs.month,
                COALESCE(SUM(COALESCE(cr.contract_received, 0)), 0) AS total_received,
                COALESCE(SUM(cs.contract_sales), 0) - COALESCE(SUM(COALESCE(cr.contract_received, 0)), 0) AS total_unreceived
            FROM contract_sales cs
            LEFT JOIN contract_received cr ON cr.signed_contract = cs.signed_contract
            GROUP BY cs.month
            ORDER BY cs.month;
            """, (ENTERPRISE_CODE, ENTERPRISE_CODE))
            monthly_receivables = cur.fetchall()
            
            # 合并月度销售和收款数据
            monthly_data = {}
            
            # 添加销售数据
            for sale in monthly_sales:
                month = sale['month']
                monthly_data[month] = {
                    'sales_total': sale['monthly_sales_total'],
                    'total_received': 0,
                    'total_unreceived': 0
                }
            
            # 添加收款数据
            for receivable in monthly_receivables:
                month = receivable['month']
                if month not in monthly_data:
                    monthly_data[month] = {
                        'sales_total': 0,
                        'total_received': receivable['total_received'],
                        'total_unreceived': receivable['total_unreceived']
                    }
                else:
                    monthly_data[month]['total_received'] = receivable['total_received']
                    monthly_data[month]['total_unreceived'] = receivable['total_unreceived']
            
            # 按月份排序
            sorted_months = sorted(monthly_data.keys())
            
            # 输出合并后的月度数据
            print("\n月度销售与收款统计:")
            print(f"{'月份':<10} {'销售总额(元)':<15} {'已收款(元)':<15} {'未收款(元)':<15}")
            print("-" * 60)
            
            # 计算年度总计
            annual_total_sales = 0
            annual_total_received = 0
            annual_total_unreceived = 0
            
            for month in sorted_months:
                data = monthly_data[month]
                print(f"{month:<10} {data['sales_total']:<15.2f} {data['total_received']:<15.2f} {data['total_unreceived']:<15.2f}")
                
                # 累加年度总计
                annual_total_sales += data['sales_total']
                annual_total_received += data['total_received']
                annual_total_unreceived += data['total_unreceived']
            
            # 显示年度总计
            print("-" * 60)
            print(f"{'年度总计':<10} {annual_total_sales:<15.2f} {annual_total_received:<15.2f} {annual_total_unreceived:<15.2f}")
            
    except Exception as e:
        print(f"销售数据统计失败: {e}")
    finally:
        conn.close()


def statistics_quotation_data():
    """
    统计报价数据: 年报价总额，月度报价额
    """
    print("\n=== 2. 报价数据统计 ===")
    conn = get_db_connection()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
            # 获取当前年份的开始和结束日期
            current_year = datetime.now().year
            start_date = f"{current_year}-01-01 00:00:00"
            end_date = f"{current_year}-12-31 23:59:59"
            
            # 年报价总额 - 使用sell_quotation表
            cur.execute("SELECT COALESCE(SUM( CAST( COALESCE( (product_detail::json->>'productAmount')::numeric, 0 ) AS NUMERIC ) ), 0) AS annual_quote_total FROM dm_m9.sell_t_dm_m9_sell_quotation_9 WHERE enterprise_code = %s AND delete_flag = 'f' AND state = 'DEAL' AND create_time IS NOT NULL AND create_time >= %s AND create_time <= %s;", (ENTERPRISE_CODE, start_date, end_date))
            annual_result = cur.fetchone()
            annual_quote_total = annual_result['annual_quote_total']
            print(f"年报价总额: {annual_quote_total:.2f} 元")
            
            # 月度报价额 - 使用2025-07-01到2025-12-31的日期范围
            print("\n月度报价额:")
            cur.execute("SELECT to_char(record_time, 'YYYY-MM') AS summarymonth, AVG(product_rmb_total_price) AS amount FROM dm_m9.base_module_t_dm_m9_history_price_9 WHERE type = 0 AND delete_flag = false AND enterprise_code = %s AND record_time >= %s AND record_time <= %s GROUP BY to_char(record_time, 'YYYY-MM') ORDER BY summarymonth", (ENTERPRISE_CODE, '2025-07-01 00:00:00', '2025-12-31 23:59:59'))
            monthly_results = cur.fetchall()
            
            if not monthly_results:
                print("暂无月度报价数据")
            else:
                for row in monthly_results:
                    print(f"{row['summarymonth']}: {row['amount']:.2f} 元")
            
    except Exception as e:
        print(f"报价数据统计失败: {e}")
    finally:
        conn.close()


def statistics_customer_data():
    """
    统计客户数据：客户总数，每个客户的销售订单数
    """
    print("\n=== 3. 客户数据统计 ===")
    conn = get_db_connection()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
            # 获取所有客户信息
            cur.execute("SELECT primary_key, customers_sign, customers_belong, tax_number, location, invoice_tel, opening_bank, account_number, price_factor, enterprise_code, contacts::BIGINT[] FROM dm_m9.sell_t_dm_m9_customers_9 WHERE enterprise_code = %s AND delete_flag = 'f' ORDER BY create_time DESC", (ENTERPRISE_CODE,))
            customers = cur.fetchall()
            
            # 客户总数
            print(f"客户总数: {len(customers)} 个")
            
            # 获取每个客户的销售订单数
            cur.execute("SELECT customer_code AS customercode, COUNT(1) AS contractcount FROM dm_m9.sell_t_dm_m9_sell_contract_9 WHERE enterprise_code = %s AND delete_flag = 'f' AND customer_code IS NOT NULL GROUP BY customer_code", (ENTERPRISE_CODE,))
            customer_contracts = cur.fetchall()
            
            # 构建客户合同映射
            contract_map = {}
            for item in customer_contracts:
                contract_map[item['customercode']] = item['contractcount']
            
            # 输出每个客户的销售订单数
            print("\n每个客户的销售订单数:")
            for customer in customers:
                customer_id = customer['primary_key']
                customer_name = customer['customers_sign']
                order_count = contract_map.get(customer_id, 0)
                print(f"客户: {customer_name}, 销售订单数: {order_count}")
            
    except Exception as e:
        print(f"客户数据统计失败: {e}")
    finally:
        conn.close()


def statistics_delivery_progress():
    """
    统计发货进度：按照产品维度统计产品deliveryRatePercent，输出产品名和发货进度
    """
    print("\n=== 4. 发货进度统计 ===")
    conn = get_db_connection()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
            # 按照产品维度统计发货进度
            cur.execute("WITH shipped AS (SELECT atd.awaiting_details_code, SUM(COALESCE(atd.this_count, 0)) AS shipped_total FROM dm_m9.production_t_dm_m9_awaiting_task_details_9 atd INNER JOIN dm_m9.production_t_dm_m9_invoice_order_9 io ON io.primary_key = atd.invoice_order WHERE atd.enterprise_code = %s AND atd.delete_flag = false AND io.enterprise_code = %s AND io.delete_flag = false AND io.packaged = true GROUP BY atd.awaiting_details_code) SELECT ad.product_code AS productcode, ad.product_name AS productname, COALESCE(SUM(sh.shipped_total), 0) AS shippedtotal, SUM(COALESCE(ad.product_quantity, 0)) AS quantitytotal FROM dm_m9.production_t_dm_m9_awaiting_details_9 ad LEFT JOIN shipped sh ON sh.awaiting_details_code = ad.primary_key WHERE ad.enterprise_code = %s AND ad.delete_flag = 'f' AND ad.product_code IS NOT NULL GROUP BY ad.product_code, ad.product_name", (ENTERPRISE_CODE, ENTERPRISE_CODE, ENTERPRISE_CODE))
            delivery_results = cur.fetchall()
            
            if not delivery_results:
                print("暂无发货进度数据")
            else:
                for row in delivery_results:
                    # 计算发货进度百分比
                    if row['quantitytotal'] > 0:
                        delivery_rate = (row['shippedtotal'] / row['quantitytotal']) * 100
                    else:
                        delivery_rate = 0
                    print(f"产品名: {row['productname']}, 发货进度: {delivery_rate:.2f}%")
            
    except Exception as e:
        print(f"发货进度统计失败: {e}")
    finally:
        conn.close()


def main():
    """主函数"""
    print(f"开始统计企业 {ENTERPRISE_CODE} 的销售相关数据...")
    print("=" * 50)
    
    # 执行各项统计
    statistics_sales_data()
    statistics_quotation_data()
    statistics_customer_data()
    statistics_delivery_progress()
    
    print("\n" + "=" * 50)
    print("销售数据统计完成！")


if __name__ == "__main__":
    main()