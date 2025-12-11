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

def query_production_data():
    try:
        # 连接数据库
        conn = psycopg2.connect(**DB_CONFIG)
        conn.autocommit = True  # 设置自动提交，每个查询独立事务
        cursor = conn.cursor()
        print("成功连接到数据库")
        print("=" * 70)
        
        # 1. 设备统计
        print("\n1. 设备统计")
        print("   接口：http://192.168.2.112:3356/fpi/technology/toolingEquipment/getToolingEquipmentPageList")
        print("   表名：dm_m9.technology_t_dm_m9_tooling_equipment_9")
        try:
            # 设备状态枚举映射
            equipment_status_map = {
                322883994612924416: "在用",
                322884581136007168: "检修中",
                322884060287336448: "保养中",
                322884626660982784: "报废",
                322884156143960064: "计量/检定中"
            }
            
            # 查询设备总量和各状态设备数量
            sql_equipment_stats = """
            SELECT 
                COUNT(*) AS total_equipment,
                SUM(CASE WHEN quality_inspection_equipment_status = 322883994612924416 THEN 1 ELSE 0 END) AS in_use_count,
                SUM(CASE WHEN quality_inspection_equipment_status = 322884581136007168 THEN 1 ELSE 0 END) AS under_maintenance_count,
                SUM(CASE WHEN quality_inspection_equipment_status = 322884060287336448 THEN 1 ELSE 0 END) AS in_maintenance_count,
                SUM(CASE WHEN quality_inspection_equipment_status = 322884626660982784 THEN 1 ELSE 0 END) AS scrap_count,
                SUM(CASE WHEN quality_inspection_equipment_status = 322884156143960064 THEN 1 ELSE 0 END) AS verification_count
            FROM dm_m9.technology_t_dm_m9_tooling_equipment_9
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cursor.execute(sql_equipment_stats, (ENTERPRISE_CODE,))
            stats_result = cursor.fetchone()
            total_equipment = stats_result[0]
            in_use_count = stats_result[1]
            under_maintenance_count = stats_result[2]
            in_maintenance_count = stats_result[3]
            scrap_count = stats_result[4]
            verification_count = stats_result[5]
            
            print(f"   设备总量：{total_equipment}")
            print(f"   在用设备数量：{in_use_count}")
            print(f"   检修中设备数量：{under_maintenance_count}")
            print(f"   保养中设备数量：{in_maintenance_count}")
            print(f"   报废设备数量：{scrap_count}")
            print(f"   计量/检定中设备数量：{verification_count}")
            
            # 打印所有设备名称、设备编号和设备状态
            print("\n   设备详细列表：")
            print("   设备名称 | 设备编号 | 设备状态值 | 设备状态")
            print("   " + "-" * 55)
            sql_equipment_list = """
            SELECT devicename, tooling_equipment_sign, quality_inspection_equipment_status
            FROM dm_m9.technology_t_dm_m9_tooling_equipment_9
            WHERE enterprise_code = %s AND delete_flag = false;
            """
            cursor.execute(sql_equipment_list, (ENTERPRISE_CODE,))
            equipment_list = cursor.fetchall()
            for equipment in equipment_list:
                equipment_name = equipment[0] if equipment[0] else ""
                equipment_code = equipment[1] if equipment[1] else ""
                status = equipment[2]
                # 根据枚举映射转换状态值
                status_value = str(status) if status is not None else "NULL"
                status_text = equipment_status_map.get(status, "未知") if status is not None else "NULL"
                print(f"   {equipment_name} | {equipment_code} | {status_value} | {status_text}")
        except Exception as e:
            print(f"   查询失败: {e}")
        
        # 2. 生产进度
        print("\n2. 生产进度")
        print("   接口：http://192.168.2.112:3356/fpi/technology/produce/detail/page/allocation/my/list")
        print("   表名：dm_m9.technology_t_dm_m9_produce_main_9, dm_m9.technology_t_dm_m9_produce_sub_dispatcher_9, dm_m9.technology_t_dm_m9_produce_sub_report_9")
        try:
            sql_production_progress = """
            SELECT 
                TO_CHAR(pm.create_time, 'YYYY-MM') AS month,
                COUNT(DISTINCT pm.primary_key) AS total_tasks,
                COUNT(DISTINCT CASE WHEN pd.allocation_num > 0 THEN pm.primary_key ELSE NULL END) AS dispatched_tasks,
                COUNT(DISTINCT CASE WHEN pr.finish_num > 0 THEN pm.primary_key ELSE NULL END) AS completed_tasks
            FROM dm_m9.technology_t_dm_m9_produce_main_9 pm
            LEFT JOIN dm_m9.technology_t_dm_m9_produce_sub_dispatcher_9 pd 
                ON pm.primary_key = pd.main_id AND pd.delete_flag = false
            LEFT JOIN dm_m9.technology_t_dm_m9_produce_sub_report_9 pr 
                ON pm.primary_key = pr.main_id AND pr.delete_flag = false
            WHERE pm.enterprise_code = %s AND pm.delete_flag = false
            GROUP BY TO_CHAR(pm.create_time, 'YYYY-MM')
            ORDER BY month;
            """
            cursor.execute(sql_production_progress, (ENTERPRISE_CODE,))
            progress_result = cursor.fetchall()
            print("   月份 | 任务总量 | 已分派 | 已完成")
            print("   " + "-" * 35)
            for progress in progress_result:
                month = progress[0]
                total_tasks = progress[1]
                dispatched_tasks = progress[2]
                completed_tasks = progress[3]
                print(f"   {month} | {total_tasks} | {dispatched_tasks} | {completed_tasks}")
        except Exception as e:
            print(f"   查询失败: {e}")
        
        # 3. 报废统计
        print("\n3. 报废统计")
        print("   接口：http://192.168.2.112:3356/fpi/technology/produce/scrapDataPageList")
        print("   表名：dm_m9.production_t_dm_m9_scrap_data_9")
        try:
            # 按产品部件名称统计报废次数（关联产品部件表获取名称）
            sql_scrap_stats = """
            SELECT 
                COALESCE(pp.product_part_sign, '未知部件') AS product_part_name,
                COUNT(*) AS scrap_count,
                SUM(sd.scrap_num) AS scrap_total_quantity
            FROM dm_m9.production_t_dm_m9_scrap_data_9 sd
            LEFT JOIN dm_m9.technology_t_dm_m9_product_part_9 pp ON sd.product_part_code = pp.primary_key
            WHERE sd.enterprise_code = %s AND sd.delete_flag = false
            GROUP BY pp.product_part_sign
            ORDER BY scrap_count DESC;
            """
            cursor.execute(sql_scrap_stats, (ENTERPRISE_CODE,))
            scrap_stats_result = cursor.fetchall()
            print("   产品部件名称 | 报废次数 | 报废总量")
            print("   " + "-" * 40)
            for scrap_stat in scrap_stats_result:
                product_part_name = scrap_stat[0]
                scrap_count = scrap_stat[1]
                scrap_total = scrap_stat[2] if scrap_stat[2] else 0
                print(f"   {product_part_name} | {scrap_count} | {scrap_total}")
            
            # 打印所有报废记录（关联产品部件表获取名称）
            print("\n   报废详细列表：")
            print("   产品部件名称 | 报废数量 | 报废原因")
            print("   " + "-" * 50)
            sql_scrap_details = """
            SELECT 
                COALESCE(pp.product_part_sign, '未知部件') AS product_part_name,
                sd.scrap_num,
                sd.scrap_remark
            FROM dm_m9.production_t_dm_m9_scrap_data_9 sd
            LEFT JOIN dm_m9.technology_t_dm_m9_product_part_9 pp ON sd.product_part_code = pp.primary_key
            WHERE sd.enterprise_code = %s AND sd.delete_flag = false;
            """
            cursor.execute(sql_scrap_details, (ENTERPRISE_CODE,))
            scrap_details_result = cursor.fetchall()
            for scrap_detail in scrap_details_result:
                product_part_name = scrap_detail[0]
                scrap_quantity = scrap_detail[1] if scrap_detail[1] else 0
                scrap_reason = scrap_detail[2] if scrap_detail[2] else "无"
                print(f"   {product_part_name} | {scrap_quantity} | {scrap_reason}")
        except Exception as e:
            print(f"   查询失败: {e}")
        
        # 4. 调度任务
        print("\n4. 调度任务")
        print("   接口：http://192.168.2.112:3356/fpi/technology/sellorder/dispatch/part/material/page")
        print("   表名：dm_m9.production_t_dm_m9_product_snapshot_9, dm_m9.technology_t_dm_m9_product_part_9, dm_m9.technology_t_dm_m9_dispatch_task_product_9")
        try:
            sql_dispatch_tasks = """
            SELECT 
                pp.product_part_sign AS material_name,
                SUM(COALESCE(ps.quantity * ps.task_num, 0) - COALESCE(ps.finish_Num, 0)) AS unarranged_quantity,
                SUM(COALESCE(ps.finish_Num, 0)) AS arranged_quantity
            FROM dm_m9.production_t_dm_m9_product_snapshot_9 ps
            INNER JOIN dm_m9.technology_t_dm_m9_product_part_9 pp ON ps.part_code = pp.primary_key
            WHERE ps.enterprise_code = %s AND ps.delete_flag = false
                AND pp.delete_flag = false
                AND exists( select 1 from dm_m9.technology_t_dm_m9_dispatch_task_product_9 dt 
                           where dt.primary_key = ps.business_id and dt.delete_flag=false 
                           and coalesce(dt.remain_quantity,0)>0 )
                AND ps.task_Num * ps.quantity > COALESCE( ps.finish_Num, 0)
                AND ps.part_code IS NOT NULL
            GROUP BY pp.product_part_sign
            ORDER BY pp.product_part_sign;
            """
            cursor.execute(sql_dispatch_tasks, (ENTERPRISE_CODE,))
            dispatch_result = cursor.fetchall()
            print("   物料名称 | 未按排量 | 已安排量")
            print("   " + "-" * 45)
            for dispatch in dispatch_result:
                material_name = dispatch[0] if dispatch[0] else "未知名称"
                unarranged_quantity = dispatch[1] if dispatch[1] else 0
                arranged_quantity = dispatch[2] if dispatch[2] else 0
                print(f"   {material_name} | {unarranged_quantity} | {arranged_quantity}")
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
    query_production_data()