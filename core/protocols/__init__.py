"""
多协议支持模块
支持 HTTP、WebService、WebSocket、Dubbo、MQTT、SQL、Redis 等协议
"""
from .base import ProtocolAdapter
from .http_adapter import HTTPAdapter
from .webservice_adapter import WebServiceAdapter
from .websocket_adapter import WebSocketAdapter
from .dubbo_adapter import DubboAdapter

# 可选导入MQTT适配器（如果paho.mqtt模块未安装，则跳过）
try:
    from .mqtt_adapter import MQTTAdapter
except ImportError:
    MQTTAdapter = None

# 导入SQL和Redis适配器（必需）
from .sql_adapter import SQLAdapter
from .redis_adapter import RedisAdapter

__all__ = [
    'ProtocolAdapter',
    'HTTPAdapter',
    'WebServiceAdapter',
    'WebSocketAdapter',
    'DubboAdapter',
    'SQLAdapter',
    'RedisAdapter',
]

# 如果MQTT适配器可用，添加到__all__
if MQTTAdapter is not None:
    __all__.append('MQTTAdapter')
