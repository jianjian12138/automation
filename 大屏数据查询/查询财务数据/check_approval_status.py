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


def check_approval_status():
    """检查款项记录表中的approval_status字段的可能值"""
    conn = get_db_connection()
    if not conn:
        return
    
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
            # 查询款项记录表中approval_status的所有可能值
            sql_approval_status = """
            SELECT DISTINCT approval_status
            FROM dm_m9.base_module_t_dm_m9_money_records_9
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            
            cur.execute(sql_approval_status, (enterprise_code,))
            approval_status_values = cur.fetchall()
            
            print("款项记录表中的approval_status可能值：")
            for row in approval_status_values:
                print(f"  - {row['approval_status']}")
            
            # 查询不同money_record_type和approval_status组合的金额统计
            sql_money_record_type_stats = """
            SELECT 
                money_record_type,
                approval_status,
                COUNT(*) AS record_count,
                COALESCE(SUM(record_amount), 0) AS total_record_amount,
                COALESCE(SUM(confirmed_money), 0) AS total_confirmed_money
            FROM dm_m9.base_module_t_dm_m9_money_records_9
            WHERE enterprise_code = %s AND delete_flag = false
            GROUP BY money_record_type, approval_status
            ORDER BY money_record_type, approval_status;
            """
            
            cur.execute(sql_money_record_type_stats, (enterprise_code,))
            money_record_stats = cur.fetchall()
            
            print("\n不同money_record_type和approval_status组合的金额统计：")
            print(f"{'money_record_type':<20} {'approval_status':<20} {'记录数':<10} {'record_amount总和':<20} {'confirmed_money总和':<20}")
            print("-" * 90)
            for row in money_record_stats:
                print(f"{row['money_record_type']:<20} {row['approval_status']:<20} {row['record_count']:<10} {row['total_record_amount']:<20} {row['total_confirmed_money']:<20}")
            
            # 查询与API结果接近的已收和已付金额
            sql_api_approximation = """
            SELECT
                -- 应收已收金额
                (SELECT COALESCE(SUM(mr.record_amount), 0) 
                 FROM dm_m9.base_module_t_dm_m9_money_records_9 mr 
                 WHERE mr.enterprise_code = %s AND mr.delete_flag = false AND mr.money_record_type = 'COLLECT') AS api_received,
                -- 应付已付金额
                (SELECT COALESCE(SUM(mr.record_amount), 0) 
                 FROM dm_m9.base_module_t_dm_m9_money_records_9 mr 
                 WHERE mr.enterprise_code = %s AND mr.delete_flag = false AND mr.money_record_type = 'PAYMENT') AS api_payed;
            """
            
            cur.execute(sql_api_approximation, (enterprise_code, enterprise_code))
            api_approximation = cur.fetchone()
            
            print(f"\n不考虑approval_status的已收金额：{api_approximation['api_received'] / 10000:.2f} 万元")
            print(f"不考虑approval_status的已付金额：{api_approximation['api_payed'] / 10000:.2f} 万元")
                
    except Exception as e:
        print(f"查询失败: {e}")
    finally:
        conn.close()


if __name__ == "__main__":
    check_approval_status()