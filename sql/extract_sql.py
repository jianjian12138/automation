#title: 从日志文件中提取SQL语句
import re
import datetime

# 读取日志文件
log_file_path = r'f:\JJ_test\automation-test-platform\sql\pasService-1.log'
sql_file_path = r'f:\JJ_test\automation-test-platform\sql\sql.txt'

# 定义时间阈值
start_time = datetime.datetime(2025, 12, 3, 15, 36, 0)

# 存储提取的SQL语句
full_sqls = []

# 遍历日志文件（逐行处理）
with open(log_file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 匹配包含时间戳、Preparing和Parameters的完整SQL块
# 匹配模式：时间戳行 + ... + Preparing行 + Parameters行
pattern = r'(-\s+2025-12-03\s+\d{2}:\d{2}:\d{2},\d{3}.*?)(?=-\s+2025-12-03\s+\d{2}:\d{2}:\d{2},\d{3}|$)' 
blocks = re.findall(pattern, content, re.DOTALL)

for block in blocks:
    # 检查时间
    time_match = re.search(r'2025-12-03\s+(\d{2}:\d{2}:\d{2}),\d{3}', block)
    if time_match:
        log_time_str = f"2025-12-03 {time_match.group(1)}"
        log_time = datetime.datetime.strptime(log_time_str, '%Y-%m-%d %H:%M:%S')
        if log_time < start_time:
            continue
    
    # 查找SQL语句，匹配格式：==>  Preparing: select ...
    sql_match = re.search(r'==>\s+Preparing:\s+(.*?)\n', block, re.DOTALL)
    if sql_match:
        sql = sql_match.group(1).strip()
        
        # 查找参数，匹配格式：==> Parameters: 123(Long), abc(String)
        params_match = re.search(r'==> Parameters:\s+(.*?)\n', block)
        params = []
        if params_match:
            params_str = params_match.group(1).strip()
            # 解析参数
            param_list = params_str.split(', ')
            for param in param_list:
                params.append(param.strip())
        
        # 将参数填入SQL语句
        full_sql = sql
        for param in params:
            # 提取值和类型，格式：190787210592256000(Long)
            if '(' in param and ')' in param:
                value, type_part = param.rsplit('(', 1)
                value = value.strip()
                type_name = type_part.rstrip(')')
                
                # 根据参数类型添加引号
                if type_name in ['String', 'Timestamp', 'Date']:
                    full_sql = full_sql.replace('?', f"'{value}'", 1)
                elif type_name in ['Long', 'Integer', 'Double', 'Float', 'BigDecimal']:
                    full_sql = full_sql.replace('?', value, 1)
                elif type_name == 'Boolean':
                    full_sql = full_sql.replace('?', value.lower(), 1)
                else:
                    # 其他类型，直接替换
                    full_sql = full_sql.replace('?', f"'{value}'", 1)
            else:
                # 没有类型信息，直接替换
                full_sql = full_sql.replace('?', f"'{param}'", 1)
        
        full_sqls.append(full_sql)

# 写入SQL文件
with open(sql_file_path, 'w', encoding='utf-8') as f:
    for sql in full_sqls:
        f.write(sql + '\n\n')

print(f"提取完成，共提取 {len(full_sqls)} 条SQL语句")
