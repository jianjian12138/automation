import psycopg2

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

def check_product_part_mapping():
    try:
        # 连接数据库
        conn = psycopg2.connect(**DB_CONFIG)
        conn.autocommit = True
        cursor = conn.cursor()
        print("成功连接到数据库")
        print("=" * 70)
        
        # 1. 查询产品零件表的所有字段
        print("1. 产品零件表字段结构")
        print("   表名：dm_m9.technology_t_dm_m9_product_part_9")
        cursor.execute("""
        SELECT column_name, data_type 
        FROM information_schema.columns 
        WHERE table_name = 'technology_t_dm_m9_product_part_9' 
        AND table_schema = 'dm_m9' 
        ORDER BY ordinal_position
        """)
        fields = cursor.fetchall()
        for field in fields:
            print(f"   {field[0]}: {field[1]}")
        
        # 2. 查询报废表中的product_part_code样本数据
        print("\n2. 报废表product_part_code样本数据")
        print("   表名：dm_m9.production_t_dm_m9_scrap_data_9")
        cursor.execute("""
        SELECT DISTINCT product_part_code 
        FROM dm_m9.production_t_dm_m9_scrap_data_9 
        WHERE delete_flag = false AND product_part_code IS NOT NULL 
        LIMIT 20
        """)
        scrap_codes = cursor.fetchall()
        print(f"   共找到 {len(scrap_codes)} 个不同的product_part_code值")
        for code in scrap_codes[:10]:
            print(f"   {code[0]}")
        if len(scrap_codes) > 10:
            print(f"   ... 还有 {len(scrap_codes) - 10} 个值未显示")
        
        # 3. 查询产品零件表中的primary_key和name样本数据
        print("\n3. 产品零件表主键和名称样本数据")
        cursor.execute("""
        SELECT primary_key, name 
        FROM dm_m9.technology_t_dm_m9_product_part_9 
        WHERE delete_flag = false 
        LIMIT 20
        """)
        product_parts = cursor.fetchall()
        print(f"   共查询到 {len(product_parts)} 条产品零件数据")
        print("   primary_key | name")
        print("   " + "-" * 50)
        for part in product_parts[:10]:
            print(f"   {part[0]} | {part[1]}")
        if len(product_parts) > 10:
            print(f"   ... 还有 {len(product_parts) - 10} 条数据未显示")
        
        # 4. 检查是否有匹配的数据
        print("\n4. 检查匹配情况")
        if scrap_codes:
            sample_code = scrap_codes[0][0]
            cursor.execute("""
            SELECT primary_key, name 
            FROM dm_m9.technology_t_dm_m9_product_part_9 
            WHERE delete_flag = false 
            AND primary_key = %s
            """, (sample_code,))
            matches = cursor.fetchall()
            print(f"   用第一个报废code '{sample_code}'查询产品零件表：")
            if matches:
                for match in matches:
                    print(f"   匹配到：primary_key={match[0]}, name={match[1]}")
            else:
                print(f"   未找到匹配的数据")
        
        # 5. 统计匹配率
        print("\n5. 统计匹配率")
        cursor.execute("""
        SELECT 
            COUNT(*) AS total_scrap_records,
            COUNT(CASE WHEN pp.primary_key IS NOT NULL THEN 1 END) AS matched_records
        FROM dm_m9.production_t_dm_m9_scrap_data_9 sd
        LEFT JOIN dm_m9.technology_t_dm_m9_product_part_9 pp 
        ON sd.product_part_code = pp.primary_key
        WHERE sd.delete_flag = false
        """)
        match_stats = cursor.fetchone()
        total = match_stats[0]
        matched = match_stats[1]
        print(f"   报废记录总数：{total}")
        print(f"   匹配到产品零件名称的记录数：{matched}")
        print(f"   匹配率：{matched/total*100:.2f}%" if total > 0 else "   无报废记录")
        
        # 6. 检查产品零件表中的product_part_sign字段，可能与报废表的product_part_code匹配
        print("\n6. 检查产品零件表的product_part_sign字段")
        cursor.execute("""
        SELECT DISTINCT product_part_sign 
        FROM dm_m9.technology_t_dm_m9_product_part_9 
        WHERE delete_flag = false AND product_part_sign IS NOT NULL 
        LIMIT 10
        """)
        part_signs = cursor.fetchall()
        print(f"   product_part_sign样本数据：")
        for sign in part_signs[:5]:
            print(f"   {sign[0]}")
        
        # 7. 检查product_part_sign与报废表的匹配情况
        print("\n7. 检查product_part_sign匹配情况")
        if scrap_codes:
            sample_code = scrap_codes[0][0]
            cursor.execute("""
            SELECT primary_key, name, product_part_sign 
            FROM dm_m9.technology_t_dm_m9_product_part_9 
            WHERE delete_flag = false 
            AND (product_part_sign = %s OR primary_key = %s)
            """, (str(sample_code), sample_code))
            matches = cursor.fetchall()
            print(f"   用第一个报废code '{sample_code}'查询product_part_sign：")
            if matches:
                for match in matches:
                    print(f"   匹配到：primary_key={match[0]}, name={match[1]}, product_part_sign={match[2]}")
            else:
                print(f"   未找到匹配的数据")
        
        # 6. 检查是否有其他可能的匹配字段
        print("\n6. 检查其他可能的匹配字段")
        cursor.execute("""
        SELECT DISTINCT column_name 
        FROM information_schema.columns 
        WHERE table_name = 'technology_t_dm_m9_product_part_9' 
        AND table_schema = 'dm_m9' 
        AND data_type IN ('character varying', 'text', 'bigint', 'integer')
        """)
        possible_columns = [col[0] for col in cursor.fetchall()]
        print(f"   可能的匹配字段：{', '.join(possible_columns)}")
        
        # 关闭连接
        cursor.close()
        conn.close()
        print("\n" + "=" * 70)
        print("所有查询完成")
        print("成功关闭数据库连接")
        
    except Exception as e:
        print(f"连接数据库失败: {e}")

if __name__ == "__main__":
    check_product_part_mapping()