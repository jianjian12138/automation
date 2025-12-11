#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
仓库数据查询脚本（合并版）
根据企业编码查询库存量、入库量、出库量、收货总数量
支持多个数据库连接配置
"""

import psycopg2
from psycopg2 import sql
import os

# 数据库连接配置选项
DB_CONFIGS = {
    'db1': {
        'name': 'PMS数据库',
        'config': {
            'host': '192.168.2.112',
            'port': 5432,
            'database': 'pms_db',
            'user': 'pms',
            'password': 'pms123'
        }
    },
    'db2': {
        'name': 'MICGENERP数据库',
        'config': {
            'host': '192.168.2.172',
            'port': 5432,
            'database': 'micgenerp',
            'user': 'postgres',
            'password': 'postgres'
        }
    }
}

# 企业编码
ENTERPRISE_CODE = '190787210592256000'


def query_warehouse_data(db_key='db2', show_table_structure=False, show_latest_records=False):
    """
    查询仓库数据
    
    :param db_key: 数据库配置键，可选值：'db1' 或 'db2'
    :param show_table_structure: 是否显示表结构
    :param show_latest_records: 是否显示最新记录
    """
    try:
        # 选择数据库配置
        if db_key not in DB_CONFIGS:
            print(f"错误：无效的数据库配置键 '{db_key}'，可选值：{list(DB_CONFIGS.keys())}")
            return
        
        db_config = DB_CONFIGS[db_key]
        print(f"\n使用数据库：{db_config['name']}")
        print(f"连接到：{db_config['config']['host']}:{db_config['config']['port']}/{db_config['config']['database']}")
        
        # 连接数据库
        conn = psycopg2.connect(**db_config['config'])
        conn.autocommit = True  # 设置自动提交，每个查询独立事务
        cursor = conn.cursor()
        print("成功连接到数据库")
        print("=" * 70)
        
        # 查询相关表的结构（可选）
        if show_table_structure:
            print("\n1. 查询相关表的结构")
            print("=" * 70)
            
            # 查询库存表的结构
            print("\n1.1 库存表结构 (warehouse_t_dm_m9_stock_9):")
            sql_stock = """
            SELECT column_name, data_type 
            FROM information_schema.columns 
            WHERE table_schema = 'dm_m9' 
            AND table_name = 'warehouse_t_dm_m9_stock_9' 
            AND column_name IN ('total_inventory', 'enterprise_code', 'delete_flag', 'create_time');
            """
            try:
                cursor.execute(sql_stock)
                stock_cols = cursor.fetchall()
                for col in stock_cols:
                    print(f"   - {col[0]} ({col[1]})")
            except Exception as e:
                print(f"   查询失败: {e}")
            
            # 查询入库单详情表的结构
            print("\n1.2 入库单详情表结构 (warehouse_t_dm_m9_inbound_order_detail_9):")
            sql_inbound = """
            SELECT column_name, data_type 
            FROM information_schema.columns 
            WHERE table_schema = 'dm_m9' 
            AND table_name = 'warehouse_t_dm_m9_inbound_order_detail_9' 
            AND column_name IN ('stored_num', 'product_part', 'enterprise_code', 'delete_flag', 'create_time');
            """
            try:
                cursor.execute(sql_inbound)
                inbound_cols = cursor.fetchall()
                for col in inbound_cols:
                    print(f"   - {col[0]} ({col[1]})")
            except Exception as e:
                print(f"   查询失败: {e}")
            
            # 查询出库单详情表的结构
            print("\n1.3 出库单详情表结构 (warehouse_t_dm_m9_outbound_order_detail_9):")
            sql_outbound = """
            SELECT column_name, data_type 
            FROM information_schema.columns 
            WHERE table_schema = 'dm_m9' 
            AND table_name = 'warehouse_t_dm_m9_outbound_order_detail_9' 
            AND column_name IN ('out_num', 'product_part', 'enterprise_code', 'delete_flag', 'create_time');
            """
            try:
                cursor.execute(sql_outbound)
                outbound_cols = cursor.fetchall()
                for col in outbound_cols:
                    print(f"   - {col[0]} ({col[1]})")
            except Exception as e:
                print(f"   查询失败: {e}")
            
        # 执行核心数据查询
        print("\n2. 执行核心数据查询")
        print("=" * 70)
        
        # 1. 总库存量查询
        print("\n2.1 总库存量查询")
        print(f"   接口：http://192.168.2.112:3356/fpi/technology/inventory/pageList")
        print("   表名：dm_m9.warehouse_t_dm_m9_stock_9")
        print("   字段：total_inventory")
        try:
            sql_total_stock = """
            SELECT SUM(COALESCE(total_inventory, 0)) AS total_stock
            FROM dm_m9.warehouse_t_dm_m9_stock_9
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cursor.execute(sql_total_stock, (ENTERPRISE_CODE,))
            result = cursor.fetchone()
            print(f"   查询结果：总库存量 = {result[0]}")
        except Exception as e:
            print(f"   查询失败: {e}")
        
        # 2. 总出库量查询
        print("\n2.2 总出库量查询")
        print(f"   接口：http://192.168.2.112:3356/fpi/warehouse/outbound/getOutboundDetailPageList")
        print("   表名：dm_m9.warehouse_t_dm_m9_outbound_order_detail_9")
        print("   字段：out_num (出库数量)")
        try:
            sql_total_outbound = """
            SELECT SUM(COALESCE(out_num, 0)) AS total_outbound_quantity
            FROM dm_m9.warehouse_t_dm_m9_outbound_order_detail_9
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cursor.execute(sql_total_outbound, (ENTERPRISE_CODE,))
            result = cursor.fetchone()
            print(f"   查询结果：总出库量 = {result[0]}")
        except Exception as e:
            print(f"   查询失败: {e}")
        
        # 3. 总入库量查询
        print("\n2.3 总入库量查询")
        print(f"   接口：http://192.168.2.112:3356/fpi/warehouse/inbound/getInboundDetailPageList")
        print("   表名：dm_m9.warehouse_t_dm_m9_inbound_order_detail_9")
        print("   字段：stored_num (入库数量)")
        try:
            sql_total_inbound = """
            SELECT SUM(COALESCE(stored_num, 0)) AS total_inbound_quantity
            FROM dm_m9.warehouse_t_dm_m9_inbound_order_detail_9
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cursor.execute(sql_total_inbound, (ENTERPRISE_CODE,))
            result = cursor.fetchone()
            print(f"   查询结果：总入库量 = {result[0]}")
        except Exception as e:
            print(f"   查询失败: {e}")
        
        # 4. 收货总数量查询
        print("\n2.4 收货总数量查询")
        print("   接口：http://192.168.2.112:3356/fpi/purchase/self/receiving/getReceiveRecordPageList")
        print("   表名：dm_m9.purchase_t_dm_m9_self_receiving_record_9 (自主收货记录) 和 dm_m9.purchase_t_dm_m9_receiving_record_9 (收货记录)")
        print("   字段：receive (收货数量)")
        try:
            # 查询自主收货记录表 (self_receiving_record)
            sql_self_receive = """
            SELECT COALESCE(SUM(receive), 0) AS self_receive_total
            FROM dm_m9.purchase_t_dm_m9_self_receiving_record_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cursor.execute(sql_self_receive, (ENTERPRISE_CODE,))
            self_receive_result = cursor.fetchone()
            self_receive_total = self_receive_result[0]
            
            # 查询收货记录表 (receiving_record)
            sql_receive = """
            SELECT COALESCE(SUM(receive), 0) AS receive_total
            FROM dm_m9.purchase_t_dm_m9_receiving_record_9 
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cursor.execute(sql_receive, (ENTERPRISE_CODE,))
            receive_result = cursor.fetchone()
            receive_total = receive_result[0]
            
            # 计算总收货数量
            total_receive = self_receive_total + receive_total
            print(f"   查询结果：自主收货 = {self_receive_total}, 收货记录 = {receive_total}, 总收货数量 = {total_receive}")
        except Exception as e:
            print(f"   查询失败: {e}")
        
        # 显示最新记录（可选）
        if show_latest_records:
            print("\n3. 显示最新记录")
            print("=" * 70)
            
            # 最新入库记录
            print("\n3.1 最新入库记录")
            sql_latest_inbound = """
            SELECT stored_num, product_or_part, create_time
            FROM dm_m9.warehouse_t_dm_m9_inbound_order_detail_9
            WHERE enterprise_code = %s AND delete_flag = false
            ORDER BY create_time DESC
            LIMIT 5;
            """
            try:
                cursor.execute(sql_latest_inbound, (ENTERPRISE_CODE,))
                latest_inbound_records = cursor.fetchall()
                for record in latest_inbound_records:
                    print(f"   入库量: {record[0]}, 产品: {record[1]}, 时间: {record[2]}")
            except Exception as e:
                print(f"   查询失败: {e}")
            
            # 最新出库记录
            print("\n3.2 最新出库记录")
            sql_latest_outbound = """
            SELECT out_num, product_or_part, create_time
            FROM dm_m9.warehouse_t_dm_m9_outbound_order_detail_9
            WHERE enterprise_code = %s AND delete_flag = false
            ORDER BY create_time DESC
            LIMIT 5;
            """
            try:
                cursor.execute(sql_latest_outbound, (ENTERPRISE_CODE,))
                latest_outbound_records = cursor.fetchall()
                for record in latest_outbound_records:
                    print(f"   出库量: {record[0]}, 产品: {record[1]}, 时间: {record[2]}")
            except Exception as e:
                print(f"   查询失败: {e}")
            
            # 最新库存记录
            print("\n3.3 最新库存记录")
            sql_latest_stock = """
            SELECT total_inventory, product_part_code, create_time
            FROM dm_m9.warehouse_t_dm_m9_stock_9
            WHERE enterprise_code = %s AND delete_flag = false
            ORDER BY create_time DESC
            LIMIT 5;
            """
            try:
                cursor.execute(sql_latest_stock, (ENTERPRISE_CODE,))
                latest_stock_records = cursor.fetchall()
                for record in latest_stock_records:
                    print(f"   库存量: {record[0]}, 产品: {record[1]}, 时间: {record[2]}")
            except Exception as e:
                print(f"   查询失败: {e}")
        
        print("\n" + "=" * 70)
        print("所有查询完成")
        
        # 关闭游标和连接
        cursor.close()
        conn.close()
        print("成功关闭数据库连接")
        
    except Exception as e:
        print(f"\n查询失败: {e}")


if __name__ == "__main__":
    print("仓库数据查询脚本（合并版）")
    print("=" * 70)
    print("企业编码：", ENTERPRISE_CODE)
    print("=" * 70)
    
    # 默认使用db2（192.168.2.172的micgenerp数据库）
    # 如需使用其他数据库，可修改此处的db_key参数
    # 可选值：'db1'（PMS数据库）、'db2'（MICGENERP数据库）
    query_warehouse_data(
        db_key='db2',
        show_table_structure=True,  # 是否显示表结构
        show_latest_records=True    # 是否显示最新记录
    )
