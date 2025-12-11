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

def check_data_issues():
    try:
        # 连接数据库
        conn = psycopg2.connect(**DB_CONFIG)
        conn.autocommit = True  # 设置自动提交，每个查询独立事务
        cursor = conn.cursor()
        print("成功连接到数据库")
        print("=" * 70)
        
        # 1. 检查设备状态字段的实际值
        print("\n1. 检查设备状态字段值")
        print("   表名：dm_m9.technology_t_dm_m9_tooling_equipment_9")
        try:
            # 查询设备状态字段的所有不同值
            sql_check_status = """
            SELECT DISTINCT quality_inspection_equipment_status, COUNT(*) AS count
            FROM dm_m9.technology_t_dm_m9_tooling_equipment_9
            WHERE enterprise_code = %s AND delete_flag = false
            GROUP BY quality_inspection_equipment_status
            ORDER BY count DESC;
            """
            cursor.execute(sql_check_status, (ENTERPRISE_CODE,))
            status_result = cursor.fetchall()
            print("   设备状态值分布：")
            for status in status_result:
                status_value = status[0]
                count = status[1]
                print(f"   状态值: {status_value} | 数量: {count}")
        except Exception as e:
            print(f"   查询失败: {e}")
        
        # 2. 检查调度任务表是否有数据
        print("\n2. 检查调度任务表数据")
        print("   表名：dm_m9.technology_t_dm_m9_dispatch_task_part_9")
        try:
            # 查询调度任务表的总记录数
            sql_check_dispatch = """
            SELECT COUNT(*) AS total_records
            FROM dm_m9.technology_t_dm_m9_dispatch_task_part_9
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cursor.execute(sql_check_dispatch, (ENTERPRISE_CODE,))
            dispatch_result = cursor.fetchone()
            total_records = dispatch_result[0]
            print(f"   符合条件的记录总数: {total_records}")
            
            # 如果有记录，查询前5条记录的关键字段
            if total_records > 0:
                sql_sample_dispatch = """
                SELECT id, part_code, total_sum, remain_quantity, status
                FROM dm_m9.technology_t_dm_m9_dispatch_task_part_9
                WHERE enterprise_code = %s AND delete_flag = false
                LIMIT 5;
                """
                cursor.execute(sql_sample_dispatch, (ENTERPRISE_CODE,))
                sample_result = cursor.fetchall()
                print("   前5条记录示例:")
                print("   ID | 物料编码 | 总数量 | 剩余数量 | 状态")
                print("   " + "-" * 50)
                for sample in sample_result:
                    id = sample[0]
                    part_code = sample[1]
                    total_sum = sample[2]
                    remain_quantity = sample[3]
                    status = sample[4]
                    print(f"   {id} | {part_code} | {total_sum} | {remain_quantity} | {status}")
        except Exception as e:
            print(f"   查询失败: {e}")
        
        # 3. 检查生产进度表是否有数据
        print("\n3. 检查生产进度表数据")
        print("   表名：dm_m9.production_t_dm_m9_task_schedule_9")
        try:
            # 查询生产进度表的总记录数
            sql_check_progress = """
            SELECT COUNT(*) AS total_records
            FROM dm_m9.production_t_dm_m9_task_schedule_9
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cursor.execute(sql_check_progress, (ENTERPRISE_CODE,))
            progress_result = cursor.fetchone()
            total_records = progress_result[0]
            print(f"   符合条件的记录总数: {total_records}")
        except Exception as e:
            print(f"   查询失败: {e}")
        
        print("\n" + "=" * 70)
        print("所有查询完成")
        
        # 关闭游标和连接
        cursor.close()
        conn.close()
        print("成功关闭数据库连接")
        
    except Exception as e:
        print(f"连接数据库失败: {e}")

if __name__ == "__main__":
    check_data_issues()