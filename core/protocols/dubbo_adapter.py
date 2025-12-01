"""
Dubbo 协议适配器
支持Apache Dubbo RPC调用
"""
import socket
import struct
import json
from typing import Dict, Any, Optional
import logging
from .base import ProtocolAdapter

LOG = logging.getLogger(__name__)


class DubboAdapter(ProtocolAdapter):
    """Dubbo协议适配器"""
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)
        self.timeout = config.get('timeout', 30) if config else 30
        self._connections = {}
    
    def send_request(self, step: Dict[str, Any]) -> Dict[str, Any]:
        """
        发送Dubbo请求
        
        :param step: 测试步骤配置
            - host: Dubbo服务地址
            - port: Dubbo服务端口 (默认20880)
            - interface: 服务接口名
            - method: 方法名
            - parameters: 方法参数列表
            - version: 服务版本 (可选)
            - group: 服务分组 (可选)
        :return: 响应结果
        """
        host = self.process_variables(step.get('host', ''))
        port = step.get('port', 20880)
        interface = self.process_variables(step.get('interface', ''))
        method = self.process_variables(step.get('method', ''))
        parameters = self.process_variables(step.get('parameters', []))
        version = self.process_variables(step.get('version', '0.0.0'))
        group = self.process_variables(step.get('group', ''))
        
        LOG.info(f"[Dubbo] 调用: {interface}.{method} @ {host}:{port}")
        
        try:
            # 构建Dubbo请求
            request_data = self._build_dubbo_request(
                interface, method, parameters, version, group
            )
            
            # 建立TCP连接
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(self.timeout)
            sock.connect((host, port))
            
            # 发送请求
            request_bytes = self._encode_dubbo_request(request_data)
            sock.sendall(request_bytes)
            
            # 接收响应
            response_bytes = self._receive_dubbo_response(sock)
            sock.close()
            
            # 解码响应
            response_data = self._decode_dubbo_response(response_bytes)
            
            return {
                'status_code': 200 if response_data else 0,
                'headers': {},
                'body': json.dumps(response_data) if response_data else '',
                'json': response_data,
                'metadata': {
                    'host': host,
                    'port': port,
                    'interface': interface,
                    'method': method,
                    'version': version,
                    'group': group,
                }
            }
            
        except Exception as e:
            LOG.error(f"[Dubbo] 请求失败: {e}")
            return {
                'status_code': 0,
                'headers': {},
                'body': '',
                'json': None,
                'error': str(e),
                'metadata': {
                    'host': host,
                    'port': port,
                    'interface': interface,
                    'method': method,
                }
            }
    
    def _build_dubbo_request(
        self, 
        interface: str, 
        method: str, 
        parameters: list, 
        version: str,
        group: str
    ) -> Dict:
        """构建Dubbo请求数据"""
        return {
            'interface': interface,
            'method': method,
            'parameters': parameters,
            'version': version,
            'group': group,
        }
    
    def _encode_dubbo_request(self, request_data: Dict) -> bytes:
        """
        编码Dubbo请求
        注意：这是简化实现，实际Dubbo协议更复杂
        """
        # Dubbo协议头
        header = bytearray(16)
        header[0] = 0xda  # Magic
        header[1] = 0xbb  # Magic
        header[2] = 0xc2  # 请求/响应标志
        
        # 将请求数据序列化为JSON
        body_json = json.dumps(request_data)
        body_bytes = body_json.encode('utf-8')
        
        # 设置数据长度
        body_length = len(body_bytes)
        struct.pack_into('!I', header, 12, body_length)
        
        return bytes(header) + body_bytes
    
    def _receive_dubbo_response(self, sock: socket.socket) -> bytes:
        """接收Dubbo响应"""
        # 读取响应头（16字节）
        header = sock.recv(16)
        if len(header) != 16:
            raise Exception("接收响应头失败")
        
        # 读取数据长度（从第12字节开始，4字节）
        body_length = struct.unpack('!I', header[12:16])[0]
        
        # 读取响应体
        body = b''
        while len(body) < body_length:
            chunk = sock.recv(body_length - len(body))
            if not chunk:
                break
            body += chunk
        
        return body
    
    def _decode_dubbo_response(self, response_bytes: bytes) -> Optional[Dict]:
        """解码Dubbo响应"""
        try:
            response_json = response_bytes.decode('utf-8')
            return json.loads(response_json)
        except:
            return None
    
    def validate_response(self, response: Dict[str, Any], assert_config: Dict[str, Any]) -> list:
        """验证Dubbo响应"""
        errors = []
        
        # 状态码断言
        if 'status_code_assert' in assert_config:
            expected_code = assert_config['status_code_assert']
            actual_code = response.get('status_code', 0)
            if actual_code != expected_code:
                errors.append(f"状态码断言失败: 期望{expected_code}, 实际{actual_code}")
        
        # JSONPath断言
        if 'jsonpath_assert' in assert_config:
            json_response = response.get('json')
            if json_response:
                from .http_adapter import HTTPAdapter
                http_adapter = HTTPAdapter()
                for assertion in assert_config['jsonpath_assert']:
                    error = http_adapter._assert_jsonpath(json_response, assertion)
                    if error:
                        errors.append(error)
            else:
                errors.append("JSONPath断言失败: 响应不是有效的JSON格式")
        
        # 响应消息断言
        if 'response_assert_data' in assert_config:
            expected_msg = assert_config['response_assert_data']
            body = response.get('body', '')
            if expected_msg not in body:
                errors.append(f"响应消息断言失败: 未找到'{expected_msg}'")
        
        return errors
