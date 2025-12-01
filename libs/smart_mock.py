"""
AI 驱动的智能 Mock 数据生成器
支持：
1. 基于字段名的智能类型推断
2. AI 辅助的上下文相关数据生成
3. 支持中文数据生成
4. 支持自定义约束条件
"""

from faker import Faker
import random
import re
from datetime import datetime, timedelta
from typing import Any, Dict, Optional, List
import logging

LOG = logging.getLogger(__name__)

class SmartMockGenerator:
    """智能 Mock 数据生成器"""
    
    def __init__(self, locale='zh_CN'):
        """
        初始化生成器
        :param locale: 地区设置，默认中文
        """
        self.fake_zh = Faker('zh_CN')  # 中文
        self.fake_en = Faker('en_US')  # 英文
        
        # 字段名模式匹配规则（智能推断）
        self.field_patterns = {
            # 个人信息
            r'(name|姓名|userName|user_name)': self._generate_name,
            r'(phone|手机|mobile|电话|phoneNumber|phone_number)': self._generate_phone,
            r'(email|邮箱|mail)': self._generate_email,
            r'(id_card|身份证|idCard)': self._generate_id_card,
            r'(address|地址)': self._generate_address,
            
            # 公司/组织
            r'(company|公司|企业|companyName)': self._generate_company,
            r'(department|部门)': self._generate_department,
            r'(job|职位|position)': self._generate_job,
            
            # 数字
            r'(amount|金额|price|价格|money|unitPrice|unit_price)': self._generate_amount,
            r'(quantity|数量|count)': self._generate_quantity,
            r'(age|年龄)': self._generate_age,
            r'(code|编码|编号)': self._generate_code,
            
            # 日期时间
            r'(date|日期|time|时间|createTime|updateTime|create_time|update_time)': self._generate_datetime,
            
            # 状态/枚举
            r'(status|状态)': self._generate_status,
            r'(type|类型)': self._generate_type,
            
            # 文本
            r'(remark|备注|comment|说明|description|描述)': self._generate_text,
            r'(title|标题)': self._generate_title,
        }
    
    def generate(self, field_name: str, data_type: str = 'auto', constraints: Optional[Dict] = None) -> Any:
        """
        智能生成 Mock 数据
        
        :param field_name: 字段名（用于智能推断）
        :param data_type: 数据类型（auto=自动推断, string, int, float, bool, date, etc.）
        :param constraints: 约束条件 {'min': 0, 'max': 100, 'length': 10, 'pattern': 'regex'}
        :return: 生成的 Mock 数据
        """
        constraints = constraints or {}
        
        try:
            # 1. 智能推断：根据字段名匹配生成器
            if data_type == 'auto':
                generator = self._match_generator(field_name)
                if generator:
                    return generator(field_name, constraints)
            
            # 2. 根据指定类型生成
            return self._generate_by_type(field_name, data_type, constraints)
            
        except Exception as e:
            LOG.warning(f"Mock 数据生成失败: {e}, 使用默认值")
            return self._generate_default(data_type)
    
    def _match_generator(self, field_name: str):
        """匹配字段名对应的生成器"""
        for pattern, generator in self.field_patterns.items():
            if re.search(pattern, field_name, re.IGNORECASE):
                return generator
        return None
    
    def _generate_by_type(self, field_name: str, data_type: str, constraints: Dict) -> Any:
        """根据数据类型生成"""
        type_generators = {
            'string': lambda: self.fake_zh.word(),
            'int': lambda: random.randint(constraints.get('min', 1), constraints.get('max', 1000000)),
            'float': lambda: self._generate_float_with_precision(constraints),
            'bool': lambda: random.choice([True, False]),
            'date': lambda: self.fake_zh.date(),
            'datetime': lambda: self.fake_zh.iso8601(),
            'text': lambda: self.fake_zh.text(max_nb_chars=constraints.get('length', 200)),
        }
        
        generator = type_generators.get(data_type.lower())
        return generator() if generator else self._generate_default(data_type)
    
    # ==================== 智能生成器 ====================
    
    def _generate_name(self, field_name: str, constraints: Dict) -> str:
        """生成姓名"""
        return self.fake_zh.name()
    
    def _generate_phone(self, field_name: str, constraints: Dict) -> str:
        """生成手机号"""
        return self.fake_zh.phone_number()
    
    def _generate_email(self, field_name: str, constraints: Dict) -> str:
        """生成邮箱"""
        return self.fake_en.email()
    
    def _generate_id_card(self, field_name: str, constraints: Dict) -> str:
        """生成身份证号"""
        return self.fake_zh.ssn()
    
    def _generate_address(self, field_name: str, constraints: Dict) -> str:
        """生成地址"""
        return self.fake_zh.address()
    
    def _generate_company(self, field_name: str, constraints: Dict) -> str:
        """生成公司名"""
        return self.fake_zh.company()
    
    def _generate_department(self, field_name: str, constraints: Dict) -> str:
        """生成部门名"""
        departments = ['技术部', '销售部', '市场部', '人事部', '财务部', '运营部', '产品部', '设计部']
        return random.choice(departments)
    
    def _generate_job(self, field_name: str, constraints: Dict) -> str:
        """生成职位"""
        return self.fake_zh.job()
    
    def _generate_amount(self, field_name: str, constraints: Dict):
        """生成金额/单价"""
        # 支持生成13位小数的随机数（用于unitPrice）
        min_val = constraints.get('min', 0.0000000000001)
        max_val = constraints.get('max', 999999999999.9999999999999)
        decimals = constraints.get('decimals', 13)
        
        # 生成随机数
        random_val = random.uniform(min_val, max_val)
        
        # 如果decimals为13，需要特殊处理以保持13位小数精度
        if decimals == 13:
            # 将随机数格式化为字符串，保留13位小数
            formatted = f"{random_val:.13f}"
            # 移除末尾的0，但保留小数点
            formatted = formatted.rstrip('0').rstrip('.')
            return formatted if '.' in formatted else formatted + '.0'
        else:
            return round(random_val, decimals)
    
    def _generate_float_with_precision(self, constraints: Dict):
        """生成指定精度的浮点数"""
        min_val = constraints.get('min', 0.01)
        max_val = constraints.get('max', 1000000.00)
        decimals = constraints.get('decimals', 2)
        
        # 生成随机数
        random_val = random.uniform(min_val, max_val)
        
        # 如果decimals为13，需要特殊处理以保持13位小数精度
        if decimals == 13:
            # 将随机数格式化为字符串，保留13位小数
            formatted = f"{random_val:.13f}"
            # 移除末尾的0，但保留小数点
            formatted = formatted.rstrip('0').rstrip('.')
            return formatted if '.' in formatted else formatted + '.0'
        else:
            return round(random_val, decimals)
    
    def _generate_quantity(self, field_name: str, constraints: Dict):
        """生成数量"""
        # 支持生成13位小数的随机数
        min_val = constraints.get('min', 0.0000000000001)
        max_val = constraints.get('max', 999999999999.9999999999999)
        decimals = constraints.get('decimals', 13)
        
        # 生成随机数
        random_val = random.uniform(min_val, max_val)
        
        # 如果decimals为13，需要特殊处理以保持13位小数精度
        if decimals == 13:
            # 将随机数格式化为字符串，保留13位小数
            formatted = f"{random_val:.13f}"
            # 移除末尾的0，但保留小数点
            formatted = formatted.rstrip('0').rstrip('.')
            return formatted if '.' in formatted else formatted + '.0'
        else:
            return round(random_val, decimals)
    
    def _generate_age(self, field_name: str, constraints: Dict) -> int:
        """生成年龄"""
        return random.randint(constraints.get('min', 18), constraints.get('max', 60))
    
    def _generate_code(self, field_name: str, constraints: Dict) -> str:
        """生成编码"""
        length = constraints.get('length', 18)
        # 生成类似雪花 ID 的长数字
        return ''.join([str(random.randint(0, 9)) for _ in range(length)])
    
    def _generate_datetime(self, field_name: str, constraints: Dict) -> str:
        """生成日期时间"""
        # 生成最近一年内的时间戳
        days_ago = constraints.get('days_ago', 365)
        start_date = datetime.now() - timedelta(days=days_ago)
        
        timestamp = self.fake_zh.date_time_between(start_date=start_date, end_date='now')
        
        # 根据字段名决定返回格式
        if 'time' in field_name.lower():
            return timestamp.strftime('%Y-%m-%d %H:%M:%S')
        else:
            return timestamp.strftime('%Y-%m-%d')
    
    def _generate_status(self, field_name: str, constraints: Dict) -> str:
        """生成状态"""
        statuses = constraints.get('options', ['正常', '禁用', '待审核', '已删除'])
        return random.choice(statuses)
    
    def _generate_type(self, field_name: str, constraints: Dict) -> str:
        """生成类型"""
        types = constraints.get('options', ['类型A', '类型B', '类型C'])
        return random.choice(types)
    
    def _generate_text(self, field_name: str, constraints: Dict) -> str:
        """生成文本"""
        return self.fake_zh.text(max_nb_chars=constraints.get('length', 100))
    
    def _generate_title(self, field_name: str, constraints: Dict) -> str:
        """生成标题"""
        return self.fake_zh.sentence()
    
    def _generate_default(self, data_type: str) -> Any:
        """生成默认值"""
        defaults = {
            'string': 'test_data',
            'int': 1,
            'float': 1.0,
            'bool': True,
            'date': datetime.now().strftime('%Y-%m-%d'),
            'datetime': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
        }
        return defaults.get(data_type.lower(), 'mock_data')
    
    def batch_generate(self, field_configs: List[Dict]) -> Dict[str, Any]:
        """
        批量生成 Mock 数据
        
        :param field_configs: 字段配置列表
            [
                {'name': 'userName', 'type': 'auto'},
                {'name': 'age', 'type': 'int', 'constraints': {'min': 18, 'max': 60}},
            ]
        :return: 生成的数据字典
        """
        result = {}
        for config in field_configs:
            field_name = config.get('name')
            data_type = config.get('type', 'auto')
            constraints = config.get('constraints', {})
            
            result[field_name] = self.generate(field_name, data_type, constraints)
        
        return result


# 全局单例
_mock_generator = None

def get_mock_generator() -> SmartMockGenerator:
    """获取全局 Mock 生成器实例"""
    global _mock_generator
    if _mock_generator is None:
        _mock_generator = SmartMockGenerator()
    return _mock_generator


def mock_data(field_name: str, data_type: str = 'auto', **constraints) -> Any:
    """
    快捷函数：生成 Mock 数据
    
    示例：
        mock_data('userName')  # 自动推断生成姓名
        mock_data('age', 'int', min=18, max=60)  # 生成 18-60 的整数
        mock_data('amount', 'float', min=0.01, max=1000.00, decimals=2)  # 生成金额
    """
    generator = get_mock_generator()
    return generator.generate(field_name, data_type, constraints)


if __name__ == '__main__':
    # 测试示例
    generator = SmartMockGenerator()
    
    print("=== 智能 Mock 数据生成测试 ===\n")
    
    # 测试智能推断
    print("1. 智能推断测试：")
    print(f"  userName: {generator.generate('userName')}")
    print(f"  phoneNumber: {generator.generate('phoneNumber')}")
    print(f"  email: {generator.generate('email')}")
    print(f"  companyName: {generator.generate('companyName')}")
    print(f"  invoiceAmount: {generator.generate('invoiceAmount')}")
    print(f"  createTime: {generator.generate('createTime')}")
    
    # 测试约束条件
    print("\n2. 约束条件测试：")
    print(f"  age (18-30): {generator.generate('age', constraints={'min': 18, 'max': 30})}")
    print(f"  amount (0.01-1000.00): {generator.generate('amount', constraints={'min': 0.01, 'max': 1000.00, 'decimals': 8})}")
    
    # 测试批量生成
    print("\n3. 批量生成测试：")
    data = generator.batch_generate([
        {'name': 'userName', 'type': 'auto'},
        {'name': 'age', 'type': 'int', 'constraints': {'min': 25, 'max': 35}},
        {'name': 'phoneNumber', 'type': 'auto'},
        {'name': 'contractCode', 'type': 'auto'},
        {'name': 'invoiceAmount', 'type': 'auto', 'constraints': {'min': 100, 'max': 10000, 'decimals': 2}},
    ])
    
    for key, value in data.items():
        print(f"  {key}: {value}")

