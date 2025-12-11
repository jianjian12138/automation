import psycopg2

# 数据库连接参数
DB_CONFIG = {
    'host': '192.168.2.172',
    'port': 5432,
    'database': 'micgenerp',
    'user': 'postgres',
    'password': 'postgres'
}

def check_product_table_structure():
    try:
        # 连接数据库
        conn = psycopg2.connect(**DB_CONFIG)
        conn.autocommit = True
        cursor = conn.cursor()
        print("成功连接到数据库")
        print("=" * 70)
        
        # 查询产品表的结构
        print("1. 产品表结构")
        print("   表名：dm_m9.technology_t_dm_m9_product_9")
        cursor.execute("""
        SELECT column_name, data_type 
        FROM information_schema.columns 
        WHERE table_name = 'technology_t_dm_m9_product_9' 
        AND table_schema = 'dm_m9' 
        ORDER BY ordinal_position
        """)
        fields = cursor.fetchall()
        for field in fields:
            print(f"   {field[0]}: {field[1]}")
        
        # 查询产品零件表的结构
        print("\n2. 产品零件表结构")
        print("   表名：dm_m9.technology_t_dm_m9_product_part_9")
        cursor.execute("""
        SELECT column_name, data_type 
        FROM information_schema.columns 
        WHERE table_name = 'technology_t_dm_m9_product_part_9' 
        AND table_schema = 'dm_m9' 
        ORDER BY ordinal_position
        """)
        part_fields = cursor.fetchall()
        for field in part_fields:
            print(f"   {field[0]}: {field[1]}")
        
        # 查询前5条产品记录
        print("\n3. 前5条产品记录")
        cursor.execute("""
        SELECT * 
        FROM dm_m9.technology_t_dm_m9_product_9 
        WHERE delete_flag = false
        LIMIT 5
        """)
        products = cursor.fetchall()
        if products:
            print(f"   记录字段数：{len(products[0])}")
            print(f"   第一条记录：{products[0]}")
        
        # 关闭连接
        cursor.close()
        conn.close()
        print("\n" + "=" * 70)
        print("所有查询完成")
        print("成功关闭数据库连接")
        
    except Exception as e:
        print(f"连接数据库失败: {e}")

if __name__ == "__main__":
    check_product_table_structure()