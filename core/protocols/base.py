"""
协议适配器基类
定义所有协议适配器的统一接口
"""
from abc import ABC, abstractmethod
from typing import Dict, Any, Optional
import logging

LOG = logging.getLogger(__name__)


class ProtocolAdapter(ABC):
    """协议适配器基类"""
    
    def __init__(self, config: Dict[str, Any] = None):
        """
        初始化协议适配器
        
        :param config: 协议配置
        """
        self.config = config or {}
        self.variables = {}  # 变量存储
        
    @abstractmethod
    def send_request(self, step: Dict[str, Any]) -> Dict[str, Any]:
        """
        发送请求
        
        :param step: 测试步骤配置
        :return: 响应结果，包含以下字段：
            - status_code: 状态码（如果有）
            - headers: 响应头（如果有）
            - body: 响应体
            - json: JSON响应（如果有）
            - metadata: 其他协议特定数据
        """
        pass
    
    @abstractmethod
    def validate_response(self, response: Dict[str, Any], assert_config: Dict[str, Any]) -> list:
        """
        验证响应
        
        :param response: 响应结果
        :param assert_config: 断言配置
        :return: 错误列表，空列表表示验证通过
        """
        pass
    
    def set_variables(self, variables: Dict[str, Any]):
        """设置变量"""
        self.variables = variables or {}
    
    def get_variable(self, key: str, default: Any = None) -> Any:
        """获取变量"""
        return self.variables.get(key, default)
    
    def process_variables(self, value: Any) -> Any:
        """
        处理变量替换
        
        :param value: 需要处理的值
        :return: 处理后的值
        """
        if isinstance(value, str):
            # 简单的变量替换：$variable_name
            import re
            pattern = r'\$(\w+)'
            matches = re.findall(pattern, value)
            for var_name in matches:
                var_value = self.get_variable(var_name)
                if var_value is not None:
                    value = value.replace(f'${var_name}', str(var_value))
            return value
        elif isinstance(value, dict):
            return {k: self.process_variables(v) for k, v in value.items()}
        elif isinstance(value, list):
            return [self.process_variables(item) for item in value]
        else:
            return value
    
    def extract_variables(self, response: Dict[str, Any], extract_config: list) -> Dict[str, Any]:
        """
        从响应中提取变量
        
        :param response: 响应结果
        :param extract_config: 提取配置
        :return: 提取的变量字典
        """
        extracted = {}
        for extract_item in extract_config:
            if isinstance(extract_item, dict) and 'extract' in extract_item:
                # 解析提取表达式，如：$set_variable(name, $jsonpath)
                # 这里简化处理，实际可以更复杂
                expr = extract_item['extract']
                LOG.info(f"提取变量: {expr}")
                # TODO: 实现实际的变量提取逻辑
        return extracted
    
    def get_protocol_name(self) -> str:
        """获取协议名称"""
        return self.__class__.__name__.replace('Adapter', '').lower()
