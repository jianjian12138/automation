"""
WebService (SOAP) 协议适配器
支持SOAP协议的WebService调用
"""
import requests
from typing import Dict, Any
import logging
from xml.etree import ElementTree as ET
from .base import ProtocolAdapter

LOG = logging.getLogger(__name__)


class WebServiceAdapter(ProtocolAdapter):
    """WebService (SOAP) 协议适配器"""
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)
        self.session = requests.Session()
        self.timeout = config.get('timeout', 30) if config else 30
    
    def send_request(self, step: Dict[str, Any]) -> Dict[str, Any]:
        """
        发送SOAP请求
        
        :param step: 测试步骤配置
            - wsdl_url: WSDL地址
            - service_name: 服务名称
            - operation: 操作方法
            - soap_action: SOAP Action (可选)
            - soap_body: SOAP Body XML内容
            - soap_headers: SOAP Headers (可选)
        :return: 响应结果
        """
        wsdl_url = self.process_variables(step.get('wsdl_url', ''))
        service_name = self.process_variables(step.get('service_name', ''))
        operation = self.process_variables(step.get('operation', ''))
        soap_action = self.process_variables(step.get('soap_action', ''))
        soap_body = self.process_variables(step.get('soap_body', ''))
        soap_headers = self.process_variables(step.get('soap_headers', {}))
        params = self.process_variables(step.get('params', {}))
        
        # 如果没有提供soap_body，则根据operation和params构建
        if not soap_body and operation:
            soap_body = self._build_soap_body(operation, params)
        
        # 构建SOAP信封
        soap_envelope = self._build_soap_envelope(soap_body, soap_headers)
        
        # 设置SOAP请求头
        headers = {
            'Content-Type': 'text/xml; charset=utf-8',
            'SOAPAction': soap_action or f'"{operation}"' if operation else ''
        }
        headers.update(soap_headers)
        
        # 确定请求URL
        request_url = wsdl_url or step.get('host', '')
        
        LOG.info(f"[WebService] 请求: {operation} @ {request_url}")
        
        try:
            response = self.session.post(
                url=request_url,
                data=soap_envelope.encode('utf-8'),
                headers=headers,
                timeout=self.timeout
            )
            
            result = {
                'status_code': response.status_code,
                'headers': dict(response.headers),
                'body': response.text,
                'xml': None,
                'metadata': {
                    'operation': operation,
                    'wsdl_url': wsdl_url,
                    'soap_action': soap_action,
                }
            }
            
            # 尝试解析XML
            try:
                result['xml'] = ET.fromstring(response.text)
            except:
                pass
            
            LOG.info(f"[WebService] 响应状态码: {response.status_code}")
            
            return result
            
        except Exception as e:
            LOG.error(f"[WebService] 请求失败: {e}")
            return {
                'status_code': 0,
                'headers': {},
                'body': '',
                'xml': None,
                'error': str(e),
                'metadata': {}
            }
    
    def _build_soap_envelope(self, body: str, headers: Dict = None) -> str:
        """构建SOAP信封"""
        soap_headers = ''
        if headers:
            headers_xml = ''.join([f'<{k}>{v}</{k}>' for k, v in headers.items()])
            soap_headers = f'<soap:Header>{headers_xml}</soap:Header>'
        
        envelope = f'''<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    {soap_headers}
    <soap:Body>
        {body}
    </soap:Body>
</soap:Envelope>'''
        return envelope
    
    def _build_soap_body(self, operation: str, params: Dict) -> str:
        """根据操作和参数构建SOAP Body"""
        body_params = ''.join([f'<{k}>{v}</{k}>' for k, v in params.items()])
        return f'<{operation}>{body_params}</{operation}>'
    
    def validate_response(self, response: Dict[str, Any], assert_config: Dict[str, Any]) -> list:
        """验证WebService响应"""
        errors = []
        
        # 状态码断言
        if 'status_code_assert' in assert_config:
            expected_code = assert_config['status_code_assert']
            actual_code = response.get('status_code', 0)
            if actual_code != expected_code:
                errors.append(f"状态码断言失败: 期望{expected_code}, 实际{actual_code}")
        
        # XML路径断言
        if 'xpath_assert' in assert_config:
            xml_root = response.get('xml')
            if xml_root:
                for xpath_expr, expected_value in assert_config['xpath_assert'].items():
                    try:
                        matches = xml_root.findall(xpath_expr)
                        if not matches:
                            errors.append(f"XPath断言失败: 路径'{xpath_expr}'未找到")
                        else:
                            actual_value = matches[0].text if matches else None
                            if str(actual_value) != str(expected_value):
                                errors.append(f"XPath断言失败: {xpath_expr} = {actual_value}, 期望{expected_value}")
                    except Exception as e:
                        errors.append(f"XPath断言失败: {e}")
            else:
                errors.append("XPath断言失败: 响应不是有效的XML格式")
        
        # 响应消息断言
        if 'response_assert_data' in assert_config:
            expected_msg = assert_config['response_assert_data']
            response_body = response.get('body', '')
            if expected_msg not in response_body:
                errors.append(f"响应消息断言失败: 未找到'{expected_msg}'")
        
        return errors
