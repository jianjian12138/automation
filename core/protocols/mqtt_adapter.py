"""
MQTT 协议适配器
支持MQTT协议的消息发布和订阅
"""
import paho.mqtt.client as mqtt
import json
import time
from typing import Dict, Any, Optional, Callable
import logging
from threading import Event
from .base import ProtocolAdapter

LOG = logging.getLogger(__name__)


class MQTTAdapter(ProtocolAdapter):
    """MQTT协议适配器"""
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)
        self.timeout = config.get('timeout', 30) if config else 30
        self._clients = {}
        self._received_messages = {}  # 存储接收到的消息
    
    def send_request(self, step: Dict[str, Any]) -> Dict[str, Any]:
        """
        发送MQTT请求（发布或订阅）
        
        :param step: 测试步骤配置
            - action: publish 或 subscribe
            - broker: MQTT Broker地址
            - port: MQTT端口 (默认1883)
            - topic: 主题
            - message: 消息内容 (publish时使用)
            - qos: QoS等级 (0, 1, 2，默认0)
            - client_id: 客户端ID
            - username: 用户名 (可选)
            - password: 密码 (可选)
            - wait_messages: 订阅时等待的消息数量
            - wait_timeout: 等待超时时间
        :return: 响应结果
        """
        action = step.get('action', 'publish').lower()
        broker = self.process_variables(step.get('broker', step.get('host', '')))
        port = step.get('port', 1883)
        topic = self.process_variables(step.get('topic', ''))
        message = self.process_variables(step.get('message', ''))
        qos = step.get('qos', 0)
        client_id = step.get('client_id', f'mqtt_client_{int(time.time())}')
        username = self.process_variables(step.get('username'))
        password = self.process_variables(step.get('password'))
        wait_messages = step.get('wait_messages', 1)
        wait_timeout = step.get('wait_timeout', self.timeout)
        
        LOG.info(f"[MQTT] {action.upper()}: {topic} @ {broker}:{port}")
        
        try:
            # 获取或创建MQTT客户端
            if client_id not in self._clients:
                client = mqtt.Client(client_id=client_id)
                if username and password:
                    client.username_pw_set(username, password)
                
                # 设置回调
                client.on_connect = self._on_connect
                client.on_message = self._on_message
                client.on_publish = self._on_publish
                client.on_subscribe = self._on_subscribe
                
                # 连接到Broker
                client.connect(broker, port, 60)
                client.loop_start()
                self._clients[client_id] = client
                time.sleep(1)  # 等待连接建立
            else:
                client = self._clients[client_id]
            
            result = {
                'status_code': 200,
                'headers': {},
                'body': '',
                'messages': [],
                'metadata': {
                    'broker': broker,
                    'port': port,
                    'topic': topic,
                    'client_id': client_id,
                    'action': action,
                }
            }
            
            if action == 'publish':
                # 发布消息
                if isinstance(message, (dict, list)):
                    message_str = json.dumps(message)
                else:
                    message_str = str(message)
                
                msg_info = client.publish(topic, message_str, qos)
                msg_info.wait_for_publish()
                
                result['body'] = f"消息已发布到主题: {topic}"
                result['metadata']['message_published'] = message_str
                LOG.info(f"[MQTT] 消息已发布: {topic}")
                
            elif action == 'subscribe':
                # 订阅主题
                client.subscribe(topic, qos)
                
                # 初始化消息存储
                if client_id not in self._received_messages:
                    self._received_messages[client_id] = []
                
                # 等待消息
                start_time = time.time()
                while len(self._received_messages.get(client_id, [])) < wait_messages:
                    if time.time() - start_time > wait_timeout:
                        LOG.warning(f"[MQTT] 等待消息超时 ({wait_timeout}秒)")
                        break
                    time.sleep(0.1)
                
                # 获取接收到的消息
                messages = self._received_messages.get(client_id, [])
                result['messages'] = messages
                result['body'] = json.dumps(messages) if messages else ''
                
                # 尝试解析JSON
                if messages:
                    try:
                        result['json'] = json.loads(messages[0])
                    except:
                        result['json'] = None
                
                LOG.info(f"[MQTT] 接收到 {len(messages)} 条消息")
            
            return result
            
        except Exception as e:
            LOG.error(f"[MQTT] 请求失败: {e}")
            return {
                'status_code': 0,
                'headers': {},
                'body': '',
                'messages': [],
                'error': str(e),
                'metadata': {
                    'broker': broker,
                    'port': port,
                    'topic': topic,
                    'action': action,
                }
            }
    
    def _on_connect(self, client, userdata, flags, rc):
        """连接回调"""
        if rc == 0:
            LOG.info(f"[MQTT] 连接成功")
        else:
            LOG.error(f"[MQTT] 连接失败, 错误码: {rc}")
    
    def _on_message(self, client, userdata, msg):
        """消息接收回调"""
        client_id = client._client_id.decode() if isinstance(client._client_id, bytes) else client._client_id
        message = msg.payload.decode('utf-8')
        
        if client_id not in self._received_messages:
            self._received_messages[client_id] = []
        
        self._received_messages[client_id].append(message)
        LOG.info(f"[MQTT] 收到消息: {msg.topic} - {message[:100]}")
    
    def _on_publish(self, client, userdata, mid):
        """发布回调"""
        LOG.debug(f"[MQTT] 消息已发布: mid={mid}")
    
    def _on_subscribe(self, client, userdata, mid, granted_qos):
        """订阅回调"""
        LOG.info(f"[MQTT] 订阅成功: qos={granted_qos}")
    
    def validate_response(self, response: Dict[str, Any], assert_config: Dict[str, Any]) -> list:
        """验证MQTT响应"""
        errors = []
        
        # 状态码断言
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
        
        # JSONPath断言（如果消息是JSON）
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
    
    def disconnect_all(self):
        """断开所有MQTT连接"""
        for client_id, client in self._clients.items():
            try:
                client.loop_stop()
                client.disconnect()
            except:
                pass
        self._clients.clear()
        self._received_messages.clear()
