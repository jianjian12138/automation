# 导入二进制ASCII转换模块，用于十六进制字符串与二进制数据的转换
import binascii

# 从gmssl库导入sm2模块，提供SM2算法实现
from gmssl import sm2

# 从核心模块导入密钥生成工具
from core.gmsslCreateKey import create_key

# 定义模块公开接口，限制外部可访问的函数
__all__=["sm2_encrypted","sm2_decrypted"]

def sm2_encrypted(data, public_key):
    """
    SM2公钥加密函数
    :param data: 需要加密的数据（支持字符串或整数类型）
    :param public_key: SM2公钥（16进制字符串）
    :return: 加密后的16进制字符串，以"04"开头
    """
    # 处理公钥：如果公钥以"04"开头，则移除开头的"04"
    public_key = public_key.replace("04", "", 1) if public_key.startswith("04") else public_key
    # 如果数据是整数类型，转换为字符串
    if isinstance(data, int):
        data = str(data)
    # 创建SM2加密对象：指定公钥、空私钥、模式1（C1C3C2）、启用ASN.1编码
    crypt_sm2 = sm2.CryptSM2(public_key=public_key, private_key='', mode=1, asn1=True)
    # 将数据转换为UTF-8编码的字节流
    data = data.encode('UTF-8')
    # 执行加密操作，返回加密后的字节流
    cipher = crypt_sm2.encrypt(data)
    # 在加密结果前添加"04"前缀并转换为十六进制字符串返回
    return "04" + cipher.hex()


def sm2_decrypted(data, private_key):
    """
    SM2私钥解密函数
    :param data: 加密后的16进制字符串（必须以"04"开头）
    :param private_key: SM2私钥（16进制字符串）
    :return: 解密后的原始字符串
    """

    # 移除加密数据开头的"04"前缀
    cipher_token_without_04 = data[2:]
    # 将剩余的十六进制字符串转换为二进制数据
    cipher_token_binary = binascii.unhexlify(cipher_token_without_04)
    # 创建SM2解密对象：指定空公钥、私钥、模式1（C1C3C2）、启用ASN.1编码
    cipher = sm2.CryptSM2(public_key='', private_key=private_key, mode=1, asn1=True)
    # 执行解密操作，返回解密后的字节流
    decrypted_data = cipher.decrypt(cipher_token_binary)
    # 将解密后的字节流转换为UTF-8编码的字符串
    token = decrypted_data.decode('utf-8')
    return token


# 模块自测入口（当前未实现测试逻辑）
if __name__ == '__main__':
    pass

