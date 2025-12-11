import psycopg2

# 数据库连接参数
DB_CONFIG = {
    'host': '192.168.2.172',
    'port': 5432,
    'database': 'micgenerp',
    'user': 'postgres',
    'password': 'postgres'
}

def check_table_structure():
    try:
        # 连接数据库
        conn = psycopg2.connect(**DB_CONFIG)
        cursor = conn.cursor()
        print("成功连接到数据库")
        
        # 需要检查的表列表
        tables = [
            'dm_m9.technology_t_dm_m9_tooling_equipment_9',
            'dm_m9.production_t_dm_m9_task_schedule_9',
            'dm_m9.production_t_dm_m9_scrap_data_9',
            'dm_m9.technology_t_dm_m9_dispatch_task_part_9',
            'dm_m9.technology_t_dm_m9_product_9',
            'dm_m9.technology_t_dm_m9_product_part_9',
            'dm_m9.technology_t_dm_m9_part_9'
        ]
        
        for table in tables:
            print(f"\n=== 表结构: {table} ===")
            # 查询表的字段信息
            sql = f"""
            SELECT column_name, data_type, column_default
            FROM information_schema.columns
            WHERE table_schema || '.' || table_name = %s
            ORDER BY ordinal_position;
            """
            cursor.execute(sql, (table,))
            columns = cursor.fetchall()
            
            print(f"字段总数: {len(columns)}")
            print("字段名 | 数据类型 | 默认值")
            print("-" * 50)
            for column in columns:
                column_name = column[0]
                data_type = column[1]
                column_default = column[2] if column[2] is not None else "NULL"
                print(f"{column_name} | {data_type} | {column_default}")
        
        # 关闭游标和连接
        cursor.close()
        conn.close()
        print("\n成功关闭数据库连接")
        
    except Exception as e:
        print(f"连接数据库失败: {e}")

if __name__ == "__main__":
    check_table_structure()