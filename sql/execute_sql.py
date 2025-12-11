#title: 执行SQL文件并生成MD文件
import psycopg2
import time

# 数据库连接信息
db_config = {
    'host': '192.168.2.172',
    'port': 5432,
    'database': 'micgenerp',
    'user': 'postgres',
    'password': 'postgres'
}

# 读取SQL文件
sql_file_path = r'f:\JJ_test\automation-test-platform\sql\sql.txt'
output_file_path = r'f:\JJ_test\automation-test-platform\sql\sql_execution_result.md'

# 连接数据库
try:
    conn = psycopg2.connect(**db_config)
    cursor = conn.cursor()
    print("数据库连接成功")
except Exception as e:
    print(f"数据库连接失败: {e}")
    exit(1)

# 读取SQL语句
with open(sql_file_path, 'r', encoding='utf-8') as f:
    sql_content = f.read()

# 分割SQL语句
sql_statements = sql_content.split('\n\n')
sql_statements = [stmt.strip() for stmt in sql_statements if stmt.strip()]

# 执行SQL并生成MD文件
with open(output_file_path, 'w', encoding='utf-8') as md_file:
    # 写入标题
    md_file.write('# SQL执行结果\n\n')
    md_file.write(f'**执行时间**: {time.strftime("%Y-%m-%d %H:%M:%S")}\n\n')
    md_file.write(f'**数据库**: {db_config["database"]}@{db_config["host"]}:{db_config["port"]}\n\n')
    md_file.write(f'**SQL文件**: {sql_file_path}\n\n')
    md_file.write('---\n\n')
    
    # 执行每条SQL语句
    for i, sql in enumerate(sql_statements, 1):
        md_file.write(f'## 第 {i} 条SQL\n\n')
        md_file.write('```sql\n')
        md_file.write(sql)
        md_file.write('\n```\n\n')
        
        try:
            # 执行SQL
            cursor.execute(sql)
            conn.commit()
            
            # 获取结果
            if cursor.description:
                # 有结果集的查询
                columns = [desc[0] for desc in cursor.description]
                rows = cursor.fetchall()
                
                md_file.write('### 执行结果\n\n')
                md_file.write('| ' + ' | '.join(columns) + ' |\n')
                md_file.write('| ' + ' | '.join(['---'] * len(columns)) + ' |\n')
                
                for row in rows:
                    row_str = []
                    for item in row:
                        if item is None:
                            row_str.append('NULL')
                        else:
                            row_str.append(str(item))
                    md_file.write('| ' + ' | '.join(row_str) + ' |\n')
                
                md_file.write(f'\n**总行数**: {len(rows)}\n\n')
            else:
                # 无结果集的执行（如UPDATE、DELETE等）
                md_file.write('### 执行结果\n\n')
                md_file.write(f'**执行成功**，影响行数: {cursor.rowcount}\n\n')
                
        except Exception as e:
            # 捕获错误，跳过继续执行
            md_file.write('### 执行错误\n\n')
            md_file.write(f'```\n{e}\n```\n\n')
            conn.rollback()
        
        md_file.write('---\n\n')

# 关闭连接
cursor.close()
conn.close()

print(f"执行完成，结果已保存到: {output_file_path}")
