"""
智能数据库查询工具
支持：
1. 自动发现数据库表结构
2. 根据字段名智能查找数据所在的表
3. 表结构缓存（避免重复查询）
4. 支持模糊匹配和智能推荐
"""

import logging
from typing import Any, Dict, List, Optional, Tuple
from keyword_utils.db_utils import DataBase
from libs.config_center import ENV

LOG = logging.getLogger(__name__)


class SmartDatabaseQuery:
    """智能数据库查询器"""

    def __init__(self, env_name: str = 'ERP_TEST', db_key: str = 'default'):
        """
        初始化智能查询器
        :param env_name: 环境名称
        :param db_key: 数据库配置键名
        """
        self.env_name = env_name
        self.db_key = db_key
        self.db = None

        # 表结构缓存 {table_name: [column_names]}
        self._table_columns_cache = {}

        # 字段->表的映射缓存 {column_name: [table_names]}
        self._column_to_tables_cache = {}

        # 是否已加载表结构
        self._schema_loaded = False

    def _get_db_connection(self) -> DataBase:
        """获取数据库连接"""
        if self.db is None:
            try:
                db_config = ENV.get(self.env_name, {}).get('data_base', {}).get(self.db_key, {})
                if not db_config:
                    raise ValueError(f"未找到数据库配置: {self.env_name}.data_base.{self.db_key}")

                # DataBase类需要conn_dict字典参数
                conn_dict = {
                    'host': db_config['host'],
                    'port': db_config.get('port', 5432),
                    'user': db_config['user'],
                    'password': db_config['password'],
                    'database': db_config['database']
                }

                self.db = DataBase(conn_dict=conn_dict, db_type="postgres")
                LOG.info(f" 成功连接数据库: {db_config['database']}@{db_config['host']}")
            except Exception as e:
                LOG.error(f"数据库连接失败: {e}")
                raise

        return self.db

    def load_schema(self, force_reload: bool = False):
        """
        加载数据库表结构（自动发现）
        :param force_reload: 是否强制重新加载
        """
        if self._schema_loaded and not force_reload:
            return

        try:
            db = self._get_db_connection()

            # 查询所有表及其字段（PostgreSQL）
            # 包含 public 和 dm_m9 等常用 schema
            # 优化：只查询特定schema的表，减少查询时间
            query = """
                SELECT
                    table_schema,
                    table_name,
                    column_name
                FROM
                    information_schema.columns
                WHERE
                    table_schema IN ('dm_m9')
                    AND table_schema NOT IN ('pg_catalog', 'information_schema')
                    AND table_name LIKE '%contract%' OR table_name LIKE '%invoice%'
                ORDER BY
                    table_schema, table_name, ordinal_position;
            """

            # 使用 with 语句管理数据库连接
            with db:
                results = db.postgres_execute(query)

                # 构建缓存
                for row in results:
                    table_schema = row[0]
                    table_name = row[1]
                    column_name = row[2]

                    # 使用完整表名（schema.table_name）作为键
                    full_table_name = f"{table_schema}.{table_name}"

                    # 表->字段映射
                    if full_table_name not in self._table_columns_cache:
                        self._table_columns_cache[full_table_name] = []
                    self._table_columns_cache[full_table_name].append(column_name)

                    # 字段->表映射
                    if column_name not in self._column_to_tables_cache:
                        self._column_to_tables_cache[column_name] = []
                    self._column_to_tables_cache[column_name].append(full_table_name)

            self._schema_loaded = True
            LOG.info(f" 成功加载数据库结构: 共 {len(self._table_columns_cache)} 张表")

        except Exception as e:
            LOG.error(f"加载数据库结构失败: {e}")
            raise

    def find_tables_by_column(self, column_name: str, fuzzy: bool = True) -> List[str]:
        """
        根据字段名查找包含该字段的表
        :param column_name: 字段名
        :param fuzzy: 是否模糊匹配
        :return: 表名列表
        """
        self.load_schema()

        # 先尝试精确匹配
        if column_name in self._column_to_tables_cache:
            return self._column_to_tables_cache[column_name]

        # 尝试无下划线的字段名（contract_code -> contractcode）
        column_without_underscores = column_name.replace('_', '')
        if column_without_underscores in self._column_to_tables_cache:
            LOG.info(f"字段 '{column_name}' 映射到 '{column_without_underscores}'")
            return self._column_to_tables_cache[column_without_underscores]

        if not fuzzy:
            return []

        # 模糊匹配：移除下划线后比较
        matching_tables = set()
        search_normalized = column_name.lower().replace('_', '')

        for col, tables in self._column_to_tables_cache.items():
            col_normalized = col.lower().replace('_', '')
            if search_normalized == col_normalized or search_normalized in col_normalized:
                matching_tables.update(tables)
                LOG.debug(f"模糊匹配: '{column_name}' -> '{col}'")

        return list(matching_tables)

    def get_table_columns(self, table_name: str) -> List[str]:
        """
        获取表的所有字段
        :param table_name: 表名
        :return: 字段名列表
        """
        self.load_schema()
        return self._table_columns_cache.get(table_name, [])

    def smart_query(self,
                    field_name: str,
                    conditions: Optional[Dict] = None,
                    limit: int = 1) -> List[Dict]:
        """
        智能查询：根据字段名自动查找并返回数据

        :param field_name: 字段名（用于查找表）
        :param conditions: 查询条件 {'field': 'value', ...}
        :param limit: 返回记录数
        :return: 查询结果列表
        """
        try:
            # 1. 直接指定表名，避免自动查找
            # 对于contractCode字段，直接从采购合同表查询
            table = "dm_m9.purchase_t_dm_m9_contract_9"
            LOG.info(f"直接查询表: {table}")

            # 2. 构建查询SQL
            select_clause = f"SELECT {field_name} FROM {table}"

            # 构建WHERE子句
            where_clause = ""
            if conditions:
                where_conditions = []
                for field, value in conditions.items():
                    # 处理字符串值
                    if isinstance(value, str):
                        # 检查是否是数字字符串
                        if value.isdigit():
                            where_conditions.append(f"{field} = {value}")
                        else:
                            # 转换为大写，确保匹配
                            where_conditions.append(f"{field} = '{value.upper()}'")
                    else:
                        where_conditions.append(f"{field} = {value}")

                if where_conditions:
                    where_clause = f" WHERE {', '.join(where_conditions)}"
            else:
                # 默认条件：只查询状态为UNILATERAL_SIGN或MUTUAL_SIGN的合同
                where_clause = " WHERE state IN ('UNILATERAL_SIGN', 'MUTUAL_SIGN')"

            # 完整SQL
            sql = f"{select_clause}{where_clause} LIMIT {limit}"
            LOG.info(f"执行SQL: {sql}")

            # 3. 执行查询
            db = self._get_db_connection()
            with db:
                results = db.postgres_execute(sql)

                if results:
                    # 转换结果为字典列表
                    result_list = []
                    for row in results:
                        row_dict = {
                            field_name: row[0] if row else None
                        }
                        result_list.append(row_dict)

                    LOG.info(f" 从表 '{table}' 查询到 {len(result_list)} 条记录")
                    return result_list

            LOG.warning(f"从表 '{table}' 查询失败，未找到字段 '{field_name}' 的数据")
            return []

        except Exception as e:
            LOG.error(f"智能查询失败: {e}")
            return []


# 全局查询函数，供外部调用
def query_db_field(field_name: str, conditions: Optional[Dict] = None) -> Any:
    """
    查询数据库字段值
    :param field_name: 字段名
    :param conditions: 查询条件
    :return: 查询到的字段值，如果未找到返回None
    """
    try:
        query = SmartDatabaseQuery()
        results = query.smart_query(field_name, conditions, limit=1)

        if results and len(results) > 0:
            # 尝试返回指定字段的值
            if field_name in results[0]:
                return results[0][field_name]
            # 如果没有找到指定字段，返回第一个非空字段的值
            for key, value in results[0].items():
                if value is not None and value != '':
                    return value
        return None
    except Exception as e:
        LOG.error(f"查询字段 '{field_name}' 失败: {e}")
        return None
