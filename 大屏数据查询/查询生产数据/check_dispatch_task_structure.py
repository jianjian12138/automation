import psycopg2
from psycopg2 import sql

# 数据库连接参数
DB_CONFIG = {
    'host': '192.168.2.172',
    'port': 5432,
    'database': 'micgenerp',
    'user': 'postgres',
    'password': 'postgres'
}

# 企业编码
ENTERPRISE_CODE = '190787210592256000'

def check_dispatch_task_structure():
    try:
        # 连接数据库
        conn = psycopg2.connect(**DB_CONFIG)
        conn.autocommit = True
        cursor = conn.cursor()
        print("成功连接到数据库")
        print("=" * 70)
        
        # 1. 查询调度任务零件表的结构
        print("1. 调度任务零件表结构")
        print("   表名：dm_m9.technology_t_dm_m9_dispatch_task_part_9")
        cursor.execute("""
        SELECT column_name, data_type 
        FROM information_schema.columns 
        WHERE table_name = 'technology_t_dm_m9_dispatch_task_part_9' 
        AND table_schema = 'dm_m9' 
        ORDER BY ordinal_position
        """)
        fields = cursor.fetchall()
        for field in fields:
            print(f"   {field[0]}: {field[1]}")
        
        # 2. 查询调度任务零件表的数据量
        print("\n2. 调度任务零件表数据量")
        cursor.execute("""
        SELECT COUNT(*) 
        FROM dm_m9.technology_t_dm_m9_dispatch_task_part_9 
        WHERE enterprise_code = %s AND delete_flag = false
        """, (ENTERPRISE_CODE,))
        count = cursor.fetchone()[0]
        print(f"   总记录数：{count}")
        
        # 3. 查询前5条记录，查看实际数据
        print("\n3. 前5条记录样本")
        cursor.execute("""
        SELECT * 
        FROM dm_m9.technology_t_dm_m9_dispatch_task_part_9 
        WHERE enterprise_code = %s AND delete_flag = false
        LIMIT 5
        """, (ENTERPRISE_CODE,))
        records = cursor.fetchall()
        if records:
            print(f"   记录字段数：{len(records[0])}")
            print(f"   第一条记录：{records[0]}")
        
        # 4. 查询调度任务产品表的结构
        print("\n4. 调度任务产品表结构")
        print("   表名：dm_m9.technology_t_dm_m9_dispatch_task_product_9")
        cursor.execute("""
        SELECT column_name, data_type 
        FROM information_schema.columns 
        WHERE table_name = 'technology_t_dm_m9_dispatch_task_product_9' 
        AND table_schema = 'dm_m9' 
        ORDER BY ordinal_position
        """)
        product_fields = cursor.fetchall()
        for field in product_fields:
            print(f"   {field[0]}: {field[1]}")
        
        # 5. 查询调度任务产品表的数据量
        print("\n5. 调度任务产品表数据量")
        cursor.execute("""
        SELECT COUNT(*) 
        FROM dm_m9.technology_t_dm_m9_dispatch_task_product_9 
        WHERE enterprise_code = %s AND delete_flag = false
        """, (ENTERPRISE_CODE,))
        product_count = cursor.fetchone()[0]
        print(f"   总记录数：{product_count}")
        
        # 6. 检查是否有相关的调度任务表
        print("\n6. 检查相关调度任务表")
        related_tables = [
            'dm_m9.technology_t_dm_m9_dispatch_task_9',
            'dm_m9.technology_t_dm_m9_dispatch_sheet_9',
            'dm_m9.technology_t_dm_m9_dispatch_record_9'
        ]
        for table in related_tables:
            cursor.execute(sql.SQL("SELECT COUNT(*) FROM {} WHERE enterprise_code = %s AND delete_flag = false").format(sql.Identifier(table)), (ENTERPRISE_CODE,))
            count = cursor.fetchone()[0]
            print(f"   {table}: {count} 条记录")
        
        # 关闭连接
        cursor.close()
        conn.close()
        print("\n" + "=" * 70)
        print("所有查询完成")
        print("成功关闭数据库连接")
        
    except Exception as e:
        print(f"连接数据库失败: {e}")

if __name__ == "__main__":
    check_dispatch_task_structure()