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

def check_table_columns(table_name):
    """查询表的列信息"""
    conn = get_db_connection()
    if not conn:
        return
    
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
            sql = f"""
            SELECT column_name, data_type
            FROM information_schema.columns
            WHERE table_name = %s AND table_schema = %s
            ORDER BY ordinal_position;
            """
            
            # 解析表名，获取schema和table
            if '.' in table_name:
                schema, table = table_name.split('.', 1)
            else:
                schema = 'public'
                table = table_name
            
            cur.execute(sql, (table, schema))
            columns = cur.fetchall()
            
            print(f"\n表 {table_name} 的列信息：")
            print(f"{'列名':<30} {'数据类型':<20}")
            print("-" * 50)
            
            for col in columns:
                print(f"{col['column_name']:<30} {col['data_type']:<20}")
                
    except Exception as e:
        print(f"查询失败: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    # 检查相关表的结构
    tables_to_check = [
        'dm_m9.base_module_t_dm_m9_signed_contract_9',
        'dm_m9.base_module_t_dm_m9_money_records_9'
    ]
    
    for table in tables_to_check:
        check_table_columns(table)
