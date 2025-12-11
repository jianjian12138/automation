#title: 处理SQL执行结果MD文件
import re
import os

# SQL语句格式化函数
def format_sql(sql):
    # 先处理注释，确保注释前有换行
    sql = re.sub(r'(?<!^)(--)', r'\n\1', sql)
    
    # 定义关键字，注意顺序：先处理复合关键字，再处理单个关键字
    keywords = [
        r'\bINNER\s+JOIN\b', r'\bLEFT\s+JOIN\b', r'\bRIGHT\s+JOIN\b', r'\bFULL\s+JOIN\b',
        r'\bGROUP\s+BY\b', r'\bORDER\s+BY\b', r'\bHAVING\b',
        r'\bSELECT\b', r'\bFROM\b', r'\bWHERE\b', r'\bJOIN\b',
        r'\bWITH\b', r'\bAS\b', r'\bON\b', r'\bCASE\b', r'\bWHEN\b', r'\bELSE\b', r'\bEND\b',
        r'\bAND\b', r'\bOR\b', r'\bNOT\b', r'\bIN\b', r'\bLIKE\b', r'\bBETWEEN\b',
        r'\bCOALESCE\b', r'\bSUM\b', r'\bCOUNT\b', r'\bAVG\b', r'\bMAX\b', r'\bMIN\b',
        r'\bROUND\b', r'\bCAST\b', r'\b::\b'
    ]
    
    # 对于CTE语句，在AS后添加换行
    sql = re.sub(r'\bAS\s+\(', 'AS (\n', sql)
    
    # 在关键字前添加换行，但要避免在括号内的关键字
    for keyword in keywords:
        # 只在关键字前有非单词字符或行首时添加换行
        sql = re.sub(r'(?<!\w)(' + keyword + r')', r'\n\1', sql, flags=re.IGNORECASE)
    
    # 修复JOIN关键字被拆分的问题
    sql = re.sub(r'\bINNER\s*\n\s*JOIN\b', 'INNER JOIN', sql, flags=re.IGNORECASE)
    sql = re.sub(r'\bLEFT\s*\n\s*JOIN\b', 'LEFT JOIN', sql, flags=re.IGNORECASE)
    sql = re.sub(r'\bRIGHT\s*\n\s*JOIN\b', 'RIGHT JOIN', sql, flags=re.IGNORECASE)
    
    # 修复连续换行
    sql = re.sub(r'\n+', '\n', sql)
    
    # 缩进格式化
    lines = sql.split('\n')
    formatted_lines = []
    indent_level = 0
    indent_size = 4
    
    for line in lines:
        line = line.strip()
        if not line:
            continue
        
        # 处理特殊情况：注释行保持原有缩进
        if line.startswith('--'):
            formatted_lines.append(' ' * (indent_level * indent_size) + line)
            continue
        
        # 减少缩进的关键字
        upper_line = line.upper()
        if any(keyword in upper_line for keyword in ['END', 'FROM', 'WHERE', 'GROUP BY', 'ORDER BY', 'JOIN', 'ON']):
            indent_level = max(0, indent_level - 1)
        
        # 添加缩进
        formatted_lines.append(' ' * (indent_level * indent_size) + line)
        
        # 增加缩进的关键字
        if any(keyword in upper_line for keyword in ['SELECT', 'CASE', 'WITH', 'AS (']):
            indent_level += 1
        elif any(keyword in upper_line for keyword in ['AND', 'OR', 'WHEN', 'ELSE']):
            indent_level += 1
    
    return '\n'.join(formatted_lines)

# 读取原始MD文件
md_file_path = r'f:\JJ_test\automation-test-platform\sql\sql_execution_result.md'
output_file_path = r'f:\JJ_test\automation-test-platform\sql\processed_sql_result.md'

with open(md_file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 解析MD文件，提取每个SQL块
# 匹配模式：## 第 X 条SQL 到 --- 之间的内容
pattern = r'(## 第 \d+ 条SQL.*?)(?=## 第 \d+ 条SQL|$)'
sql_blocks = re.findall(pattern, content, re.DOTALL)

# 存储唯一的SQL条目
unique_sql_entries = {}

# 遍历每个SQL块，提取SQL语句和结果
for block in sql_blocks:
    # 提取SQL语句
    sql_pattern = r'```sql\n(.*?)\n```'    
    sql_match = re.search(sql_pattern, block, re.DOTALL)
    if sql_match:
        sql_statement = sql_match.group(1).strip()
        
        # 提取执行结果
        result_pattern = r'### 执行结果(.*?)(\*\*总行数\*\*|$)'
        result_match = re.search(result_pattern, block, re.DOTALL)
        result = result_match.group(1).strip() if result_match else ''
        
        # 提取总行数
        row_count_pattern = r'\*\*总行数\*\*:\s*(\d+)'
        row_count_match = re.search(row_count_pattern, block)
        row_count = row_count_match.group(1) if row_count_match else '0'
        
        # 存储唯一SQL，使用SQL语句作为键
        if sql_statement not in unique_sql_entries:
            unique_sql_entries[sql_statement] = {
                'result': result,
                'row_count': row_count
            }

# 生成处理后的MD内容
output_content = '# SQL执行结果（去重并添加说明）\n\n'
output_content += '**处理时间**: ' + os.popen('echo %date% %time%').read().strip() + '\n\n'
output_content += '**原始SQL数量**: ' + str(len(sql_blocks)) + '\n'
output_content += '**去重后SQL数量**: ' + str(len(unique_sql_entries)) + '\n\n'
output_content += '---\n\n'

# 为每个唯一SQL添加说明并写入
for i, (sql, data) in enumerate(unique_sql_entries.items(), 1):
    # 添加SQL序号
    output_content += f'## 第 {i} 条SQL\n\n'
    
    # 添加SQL说明
    explanation = ''
    if 'SELECT' in sql:
        if 'FROM dm_m9.production_t_dm_m9_awaiting_details_9' in sql:
            explanation = '**说明**: 查询生产待处理详情，按产品代码和名称分组，统计发货数量和产品数量\n\n'
        elif 'FROM dm_m9.base_module_t_dm_m9_history_price_9' in sql and 'type = 0' in sql:
            explanation = '**说明**: 查询历史价格记录，按月统计产品人民币总价的平均值\n\n'
        elif 'FROM dm_m9.sell_t_dm_m9_customers_9' in sql:
            explanation = '**说明**: 查询销售客户信息，包括基本信息和联系人ID\n\n'
        elif 'FROM dm_m9.sell_t_dm_m9_sell_contract_9' in sql and 'COUNT(1)' in sql:
            explanation = '**说明**: 按客户代码统计销售合同数量\n\n'
        elif 'FROM dm_m9.base_module_t_dm_m9_signed_contract_9' in sql and 'COUNT(*)' in sql:
            explanation = '**说明**: 统计指定时间段内的已签合同数量\n\n'
        elif 'FROM dm_m9.base_module_t_dm_m9_history_price_9' in sql and 'type = 1' in sql:
            explanation = '**说明**: 查询历史价格记录，获取指定合同的价格历史详情\n\n'
        elif 'FROM dm_m9.base_module_t_dm_m9_money_records_9' in sql and 'SUM' in sql:
            explanation = '**说明**: 统计指定合同的已确认金额总和\n\n'
        elif 'COALESCE(SUM' in sql and 'product_detail::json' in sql:
            explanation = '**说明**: 计算销售合同产品总金额，考虑汇率转换\n\n'
        else:
            explanation = '**说明**: 查询数据\n\n'
    
    output_content += explanation
    
    # 添加SQL语句
    output_content += '```sql\n'
    output_content += format_sql(sql) + '\n'
    output_content += '```\n\n'
    
    # 添加执行结果
    output_content += '### 执行结果\n\n'
    output_content += data['result'] + '\n'
    output_content += f'**总行数**: {data["row_count"]}\n\n'
    
    output_content += '---\n\n'

# 写入处理后的MD文件
with open(output_file_path, 'w', encoding='utf-8') as f:
    f.write(output_content)

print(f"处理完成！")
print(f"原始SQL数量: {len(sql_blocks)}")
print(f"去重后SQL数量: {len(unique_sql_entries)}")
print(f"输出文件: {output_file_path}")
