"""
WebSocket 协议适配器
支持WebSocket协议的实时通信测试
"""
import asyncio
import websockets
import json
from typing import Dict, Any, Optional
import logging
import time
from .base import ProtocolAdapter

LOG = logging.getLogger(__name__)


class WebSocketAdapter(ProtocolAdapter):
    """WebSocket协议适配器"""
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)
        self.timeout = config.get('timeout', 30) if config else 30
        self.connection_timeout = config.get('connection_timeout', 10) if config else 10
        self._connections = {}  # 存储连接
    
    def send_request(self, step: Dict[str, Any]) -> Dict[str, Any]:
        """
        发送WebSocket请求
        
        :param step: 测试步骤配置
            - ws_url: WebSocket URL (ws:// 或 wss://)
            - message: 要发送的消息
            - wait_response: 是否等待响应 (默认True)
            - wait_timeout: 等待响应的超时时间 (秒)
            - close_after: 发送后是否关闭连接
        :return: 响应结果
        """
        ws_url = self.process_variables(step.get('ws_url', step.get('host', '')))
        message = self.process_variables(step.get('message', ''))
        wait_response = step.get('wait_response', True)
        wait_timeout = step.get('wait_timeout', self.timeout)
        close_after = step.get('close_after', False)
        connection_id = step.get('connection_id', 'default')
        
        LOG.info(f"[WebSocket] 连接到: {ws_url}")
        
        try:
            # 使用asyncio运行WebSocket操作
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            result = loop.run_until_complete(
                self._send_websocket_message(
                    ws_url, message, wait_response, wait_timeout, close_after, connection_id
                )
            )
            loop.close()
            return result
        except Exception as e:
            LOG.error(f"[WebSocket] 请求失败: {e}")
            return {
                'status_code': 0,
                'headers': {},
                'body': '',
                'json': None,
                'error': str(e),
                'metadata': {
                    'ws_url': ws_url,
                    'connection_id': connection_id
                }
            }
    
    async def _send_websocket_message(
        self, 
        ws_url: str, 
        message: Any, 
        wait_response: bool,
        wait_timeout: float,
        close_after: bool,
        connection_id: str
    ) -> Dict[str, Any]:
        """异步发送WebSocket消息"""
        try:
            # 检查是否已有连接
            ws = self._connections.get(connection_id)
            if not ws or ws.closed:
                ws = await asyncio.wait_for(
                    websockets.connect(ws_url),
                    timeout=self.connection_timeout
                )
                self._connections[connection_id] = ws
                LOG.info(f"[WebSocket] 连接建立: {connection_id}")
            
            # 序列化消息
            if isinstance(message, (dict, list)):
                message_str = json.dumps(message)
            else:
                message_str = str(message)
            
            # 发送消息
            await ws.send(message_str)
            LOG.info(f"[WebSocket] 消息已发送: {message_str[:100]}")
            
            # 等待响应
            received_messages = []
            if wait_response:
                try:
                    response = await asyncio.wait_for(ws.recv(), timeout=wait_timeout)
                    received_messages.append(response)
                    LOG.info(f"[WebSocket] 收到响应: {response[:100]}")
                except asyncio.TimeoutError:
                    LOG.warning(f"[WebSocket] 等待响应超时 ({wait_timeout}秒)")
            
            # 关闭连接
            if close_after:
                await ws.close()
                if connection_id in self._connections:
                    del self._connections[connection_id]
                LOG.info(f"[WebSocket] 连接已关闭: {connection_id}")
            
            # 尝试解析JSON响应
            json_response = None
            if received_messages:
                try:
                    json_response = json.loads(received_messages[0])
                except:
                    pass
            
            return {
                'status_code': 200,  # WebSocket成功连接
                'headers': {},
                'body': received_messages[0] if received_messages else '',
                'messages': received_messages,
                'json': json_response,
                'metadata': {
                    'ws_url': ws_url,
                    'connection_id': connection_id,
                    'message_sent': message_str,
                }
            }
            
        except Exception as e:
            return {
                'status_code': 0,
                'headers': {},
                'body': '',
                'messages': [],
                'json': None,
                'error': str(e),
                'metadata': {
                    'ws_url': ws_url,
                    'connection_id': connection_id
                }
            }
    
    def validate_response(self, response: Dict[str, Any], assert_config: Dict[str, Any]) -> list:
        """验证WebSocket响应"""
        errors = []
        
        # 状态码断言（WebSocket连接成功通常是200）
        if 'status_code_assert' in assert_config:
            expected_code = assert_config['status_code_assert']
            actual_code = response.get('status_code', 0)
            if actual_code != expected_code:
                errors.append(f"状态码断言失败: 期望{expected_code}, 实际{actual_code}")
        
        # 消息断言
        if 'message_assert' in assert_config:
            expected_msg = assert_config['message_assert']
            messages = response.get('messages', [])
            found = any(expected_msg in msg for msg in messages)
            if not found:
                errors.append(f"消息断言失败: 未找到'{expected_msg}'")
        
        # JSONPath断言（如果响应是JSON）
        if 'jsonpath_assert' in assert_config:
            json_response = response.get('json')
            if json_response:
                # 使用HTTP适配器的JSONPath断言逻辑
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
    
    def close_all_connections(self):
        """关闭所有WebSocket连接"""
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            for conn_id, ws in self._connections.items():
                if not ws.closed:
                    loop.run_until_complete(ws.close())
            self._connections.clear()
        finally:
            loop.close()
