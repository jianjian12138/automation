"""
智能分片数据库查询工具
支持基于企业编码的分片数据查询
"""
import sys
from pathlib import Path

# 添加autotest_elegant到路径
autotest_path = Path(__file__).parent.parent.parent / "autotest_elegant"
sys.path.insert(0, str(autotest_path))

import logging
from typing import Any, Dict, List, Optional, Tuple
from keyword_utils.db_utils import DataBase
from libs.config_center import ENV

LOG = logging.getLogger(__name__)


class SmartShardingQuery:
    """智能分片数据库查询器"""
    
    def __init__(self, env_name: str = 'ERP_TEST', db_key: str = 'default'):
        """
        初始化分片查询器
        :param env_name: 环境名称
        :param db_key: 数据库配置键名
        """
        self.env_name = env_name
        self.db_key = db_key
        self.db = None
        
        # 缓存
        self._sharding_num_cache = {}  # {enterprise_code: sharding_num}
        self._sharded_tables_cache = {}  # {sharding_num: [table_names]}
        
        # 默认企业编码（从配置读取）
        self.default_enterprise_code = self._get_default_enterprise_code()
    
    def _get_default_enterprise_code(self) -> str:
        """从配置获取默认企业编码"""
        global_vars = ENV.get(self.env_name, {}).get('global_variable', {})
        enterprise_codes = global_vars.get('enterprise_code', [])
        
        if enterprise_codes:
            default_code = enterprise_codes[0] if isinstance(enterprise_codes, list) else enterprise_codes
            LOG.info(f"使用默认企业编码: {default_code}")
            return str(default_code)
        
        LOG.warning("未找到默认企业编码，使用配置的第一个")
        return '190787210592256000'  # fallback
    
    def _get_db_connection(self) -> DataBase:
        """获取数据库连接"""
        if self.db is None:
            try:
                db_config = ENV.get(self.env_name, {}).get('data_base', {}).get(self.db_key, {})
                if not db_config:
                    raise ValueError(f"未找到数据库配置: {self.env_name}.data_base.{self.db_key}")
                
                conn_dict = {
                    'host': db_config['host'],
                    'port': db_config.get('port', 5432),
                    'user': db_config['user'],
                    'password': db_config['password'],
                    'database': db_config['database']
                }
                
                self.db = DataBase(conn_dict=conn_dict, db_type="postgres")
                LOG.info(f"✓ 成功连接数据库: {db_config['database']}@{db_config['host']}")
            except Exception as e:
                LOG.error(f"数据库连接失败: {e}")
                raise
        
        return self.db
    
    def get_sharding_num(self, enterprise_code: Optional[str] = None) -> int:
        """
        获取企业的分片编号
        
        :param enterprise_code: 企业编码（None则使用默认）
        :return: 分片编号
        """
        if enterprise_code is None:
            enterprise_code = self.default_enterprise_code
        
        # 检查缓存
        if enterprise_code in self._sharding_num_cache:
            return self._sharding_num_cache[enterprise_code]
        
        try:
            db = self._get_db_connection()
            
            query = f"""
                SELECT sharding_num 
                FROM enterprise_base.t_enterprise_base_enterprise 
                WHERE enterprise_code = '{enterprise_code}'
            """
            
            with db:
                results = db.postgres_execute(query)
                
                if results and len(results) > 0:
                    sharding_num = results[0][0]
                    # sharding_num - 1 = 实际的表编号
                    actual_shard = sharding_num - 1
                    
                    self._sharding_num_cache[enterprise_code] = actual_shard
                    LOG.info(f"✓ 企业 {enterprise_code} 的分片编号: {actual_shard} (sharding_num={sharding_num})")
                    
                    return actual_shard
                else:
                    LOG.warning(f"未找到企业 {enterprise_code} 的分片信息")
                    return 0  # 默认返回0
        
        except Exception as e:
            LOG.error(f"获取分片编号失败: {e}")
            return 0
    
    def get_sharded_table_name(self, base_table_name: str, enterprise_code: Optional[str] = None) -> str:
        """
        获取分片表名
        
        :param base_table_name: 基础表名（如 product_part_procedure）
        :param enterprise_code: 企业编码
        :return: 实际的分片表名（如 technology_t_dm_m9_product_part_procedure_9）
        """
        shard_num = self.get_sharding_num(enterprise_code)
        
        # 构建分片表名
        # 格式: technology_t_dm_m9_{base_table_name}_{shard_num}
        sharded_table = f"technology_t_dm_m9_{base_table_name}_{shard_num}"
        
        LOG.info(f"表名映射: {base_table_name} -> {sharded_table}")
        return sharded_table
    
    def list_all_sharded_tables(self, enterprise_code: Optional[str] = None) -> List[Dict[str, str]]:
        """
        列出企业所有分片表及其用途
        
        :param enterprise_code: 企业编码
        :return: 表信息列表 [{'table_name': '', 'schema': '', 'description': ''}, ...]
        """
        shard_num = self.get_sharding_num(enterprise_code)
        
        try:
            db = self._get_db_connection()
            
            # 查询所有以分片编号结尾的表
            query = f"""
                SELECT 
                    table_schema,
                    table_name,
                    obj_description((table_schema || '.' || table_name)::regclass, 'pg_class') as table_comment
                FROM 
                    information_schema.tables
                WHERE 
                    table_name LIKE '%_{shard_num}'
                    AND table_schema NOT IN ('pg_catalog', 'information_schema')
                ORDER BY 
                    table_schema, table_name;
            """
            
            with db:
                results = db.postgres_execute(query)
                
                tables_info = []
                for row in results:
                    schema_name = row[0]
                    table_name = row[1]
                    comment = row[2] if row[2] else self._guess_table_purpose(table_name)
                    
                    tables_info.append({
                        'schema': schema_name,
                        'table_name': table_name,
                        'description': comment,
                        'full_name': f"{schema_name}.{table_name}"
                    })
                
                LOG.info(f"✓ 找到 {len(tables_info)} 个分片表（shard={shard_num}）")
                return tables_info
        
        except Exception as e:
            LOG.error(f"列出分片表失败: {e}")
            return []
    
    def _guess_table_purpose(self, table_name: str) -> str:
        """根据表名推测用途"""
        purpose_keywords = {
            'product_part_procedure': '产品零件工序',
            'product_part': '产品零件',
            'product': '产品',
            'contract': '合同',
            'reimbursement': '报销',
            'stock': '库存',
            'inventory': '库存',
            'purchase': '采购',
            'material': '物料',
            'warehouse': '仓库',
            'process': '流程',
            'procedure': '工序',
            'dispatch': '派工',
            'production': '生产',
            'finance': '财务',
            'invoice': '发票',
            'order': '订单',
        }
        
        table_lower = table_name.lower()
        for keyword, purpose in purpose_keywords.items():
            if keyword in table_lower:
                return purpose
        
        return '未知用途'
    
    def smart_query_sharded(self, 
                           field_name: str, 
                           base_table: Optional[str] = None,
                           conditions: Optional[Dict] = None,
                           enterprise_code: Optional[str] = None,
                           limit: int = 1) -> List[Dict]:
        """
        智能分片查询
        
        :param field_name: 字段名
        :param base_table: 基础表名（不带分片后缀）
        :param conditions: 查询条件
        :param enterprise_code: 企业编码
        :param limit: 返回记录数
        :return: 查询结果
        """
        shard_num = self.get_sharding_num(enterprise_code)
        
        try:
            db = self._get_db_connection()
            
            # 如果指定了基础表名，直接查询
            if base_table:
                sharded_table = self.get_sharded_table_name(base_table, enterprise_code)
                return self._query_from_sharded_table(sharded_table, field_name, conditions, limit)
            
            # 否则，查找所有可能的分片表
            all_tables = self.list_all_sharded_tables(enterprise_code)
            
            for table_info in all_tables:
                try:
                    full_table_name = table_info['full_name']
                    result = self._query_from_sharded_table(full_table_name, field_name, conditions, limit)
                    
                    if result:
                        LOG.info(f"✓ 从分片表 '{full_table_name}' 成功查询到数据")
                        return result
                
                except Exception as e:
                    LOG.debug(f"从表 '{table_info['table_name']}' 查询失败: {e}")
                    continue
            
            LOG.warning(f"所有分片表都未查询到数据（字段: {field_name}）")
            return []
        
        except Exception as e:
            LOG.error(f"智能分片查询失败: {e}")
            return []
    
    def _query_from_sharded_table(self,
                                  full_table_name: str,
                                  field_name: str,
                                  conditions: Optional[Dict],
                                  limit: int) -> List[Dict]:
        """从指定分片表查询数据"""
        db = self._get_db_connection()
        
        # 构建查询条件
        where_clauses = []
        
        if conditions:
            for field, value in conditions.items():
                if isinstance(value, str):
                    where_clauses.append(f"{field} = '{value}'")
                else:
                    where_clauses.append(f"{field} = {value}")
        
        where_sql = f"WHERE {' AND '.join(where_clauses)}" if where_clauses else ""
        
        query = f"""
            SELECT * FROM {full_table_name}
            {where_sql}
            ORDER BY RANDOM()
            LIMIT {limit}
        """
        
        with db:
            results = db.postgres_execute(query)
            
            if results:
                # 获取列名
                column_query = f"""
                    SELECT column_name
                    FROM information_schema.columns
                    WHERE table_name = '{full_table_name.split('.')[-1]}'
                    ORDER BY ordinal_position;
                """
                
                with db:
                    columns_result = db.postgres_execute(column_query)
                    columns = [row[0] for row in columns_result]
                
                return [dict(zip(columns, row)) for row in results]
        
        return []


# 全局实例
_sharding_query_instance = None


def get_sharding_query() -> SmartShardingQuery:
    """获取全局分片查询实例"""
    global _sharding_query_instance
    if _sharding_query_instance is None:
        _sharding_query_instance = SmartShardingQuery()
    return _sharding_query_instance


def query_sharded_field(field_name: str, 
                       base_table: Optional[str] = None,
                       conditions: Optional[Dict] = None,
                       enterprise_code: Optional[str] = None) -> Any:
    """
    便捷函数：查询分片字段值
    
    :param field_name: 字段名
    :param base_table: 基础表名
    :param conditions: 查询条件
    :param enterprise_code: 企业编码
    :return: 字段值（第一条记录的目标字段）
    """
    sq = get_sharding_query()
    results = sq.smart_query_sharded(field_name, base_table, conditions, enterprise_code, limit=1)
    
    if results and field_name in results[0]:
        return results[0][field_name]
    
    return None


if __name__ == '__main__':
    """测试和演示"""
    print("="*70)
    print("  智能分片数据库查询工具 - 测试")
    print("="*70)
    
    try:
        # 1. 创建查询器
        sq = SmartShardingQuery()
        
        # 2. 获取分片编号
        print("\n[1] 获取分片编号...")
        shard_num = sq.get_sharding_num('190787210592256000')
        print(f"    企业 190787210592256000 的分片编号: {shard_num}")
        
        # 3. 列出所有分片表
        print(f"\n[2] 列出所有分片表（shard={shard_num}）...")
        tables = sq.list_all_sharded_tables('190787210592256000')
        
        print(f"\n    共找到 {len(tables)} 个分片表:\n")
        
        # 按schema分组显示
        schemas = {}
        for table in tables:
            schema = table['schema']
            if schema not in schemas:
                schemas[schema] = []
            schemas[schema].append(table)
        
        for schema, schema_tables in schemas.items():
            print(f"    Schema: {schema}")
            for table in schema_tables[:10]:  # 每个schema只显示前10个
                print(f"      - {table['table_name']:<60} | {table['description']}")
            
            if len(schema_tables) > 10:
                print(f"      ... 还有 {len(schema_tables) - 10} 个表")
            print()
        
        # 4. 测试分片查询
        print("\n[3] 测试分片查询...")
        
        # 示例：查询合同编码
        print("    查询 contract_code...")
        result = sq.smart_query_sharded('contractcode', base_table='contract', limit=1)
        if result:
            print(f"    ✓ 找到数据: {list(result[0].keys())[:5]}...")
            if 'contractcode' in result[0]:
                print(f"    contract_code = {result[0]['contractcode']}")
        else:
            print("    ✗ 未找到数据")
        
        print("\n[SUCCESS] 测试完成！")
        
    except Exception as e:
        print(f"\n[ERROR] 测试失败: {e}")
        import traceback
        traceback.print_exc()

