# 导入必要的模块：os(文件系统操作)、time(时间处理)、base64(Base64编码)、datetime(日期时间)、uuid(唯一标识符)
# urllib.parse(URL解析)、json(JSON处理)、hashlib(哈希算法)、hmac(密钥哈希)、re(正则表达式)、requests(HTTP请求)
import os, time, base64, datetime, uuid, urllib.parse, json, hashlib, hmac, re, requests
# 从urllib.parse导入urlparse(URL解析)、quote(URL编码)、unquote(URL解码)、urlencode(URL参数编码)
from urllib.parse import urlparse, quote, unquote, urlencode
# 从core.http_client导入全局变量g
from core.http_client import g
# 从core.http_client导入HttpRequests类，用于发送HTTP请求
from core.http_client import HttpRequests
# 从libs.config_center导入ENV配置，用于获取环境变量
from libs.config_center import ENV


# 定义FileHttp类，用于处理文件上传相关操作
class FileHttp:

    # 类初始化方法
    def __init__(self):
        # 获取当前时间戳(秒级)，用于生成签名和过期时间
        self.time_stamp = int(time.time())

    # 获取临时上传凭证(用于COS等云存储服务)
    def get_temporary_certificate(self):
        # 从环境配置中获取文件服务主机地址
        host = ENV[g.env]["servers"]["fs"]
        # 构造获取临时凭证的URL
        fs_host = f"{host}/file/GetTemporaryCertificate"
        # 设置请求头为JSON格式
        header = {"Content-Type": "application/json"}
        # 构造请求数据：包含时间戳和文件类型
        data = {"date": {int(self.time_stamp * 1000)}, "type": 1, "unifiedPromptError": False}
        # 创建HttpRequests实例
        client = HttpRequests()
        # 发送GET请求获取临时凭证
        client.request("GET", fs_host, header, data)
        # 返回JSON解析后的凭证响应
        return json.loads(client.response)

    # 文件上传方法，接收文件路径参数
    def file_upload(self, file_path):
        # 获取文件扩展名(如.jpg、.txt)
        file_extension = os.path.splitext(file_path)[1]
        # 计算凭证过期时间(当前时间+15分钟，并转换为UTC+8时区)
        utc_time = datetime.datetime.utcfromtimestamp(self.time_stamp + 15 * 60) + datetime.timedelta(hours=8)
        # 获取临时上传凭证
        certificate_response = self.get_temporary_certificate()
        # 从凭证中获取COS存储主机地址
        file_host = certificate_response["cosHost"]
        # 解析主机地址中的路径部分作为文件存储日期
        file_date = urllib.parse.urlparse(file_host).path
        # 生成UUID作为文件名(去除横杠)
        uuid_file = str(uuid.uuid1()).replace('-', '')
        # 构造完整的文件存储路径(key)
        key = f'{file_date}/{uuid_file}{file_extension}'
        # 生成签名时间戳(开始时间;结束时间)
        key_time = f"{self.time_stamp};{self.time_stamp + 15 * 60}"
        # 从凭证中获取临时SecretId
        tmpSecretId = certificate_response["credentials"]["tmpSecretId"]
        # 从凭证中获取临时SecretKey
        tmpSecretKey = certificate_response["credentials"]["tmpSecretKey"]
        # 设置请求头为multipart/form-data格式(用于文件上传)
        header = {"Content-Type": "multipart/form-data"}
        # 构造上传策略(policy)数据
        policy_data = {'expiration': utc_time.strftime("%Y-%m-%dT%H:%M:%S.000Z"),
                       'conditions': [{'q-sign-algorithm': 'sha1'},
                                      {'q-ak': tmpSecretId},
                                      {'q-sign-time': key_time},
                                      {'bucket': certificate_response["bucketId"]},
                                      {'key': key}]}
        # 将policy数据JSON序列化并进行Base64编码
        policy = base64.b64encode(json.dumps(policy_data, separators=(',', ':')).encode('utf-8')).decode()

        # 使用SecretKey和key_time生成签名密钥(sign_key)
        sign_key = hmac.new(tmpSecretKey.encode('utf-8'), key_time.encode('utf-8'), hashlib.sha1).hexdigest()
        # 计算HTTP请求串的SHA1哈希值
        sha1_http_string = hashlib.sha1(f"post\n{key}\n\n\n".encode('utf-8')).hexdigest()
        # 构造待签名串
        string_to_sign = f"sha1\n{key_time}\n{sha1_http_string}\n"
        # 使用sign_key对待签名串进行HMAC-SHA1签名
        signature = hmac.new(sign_key.encode('utf-8'), string_to_sign.encode('utf-8'), hashlib.sha1).hexdigest()
        # 构造表单数据(包含文件信息、签名、策略等)
        form_data = {
            "key": key,
            "policy": policy,
            "q-sign-algorithm": "sha1",
            "q-ak": tmpSecretId,
            "x-cos-security-token": certificate_response["credentials"]["token"],
            "q-signature": signature,
            "file": file_path
        }
        # 创建HttpRequests实例
        client = HttpRequests()
        # 发送POST请求上传文件
        client.request("POST", file_host + "/", header, form_data)


# 将参数字典转换为URL查询字符串(如key1=val1&key2=val2)
def url_param(get_param):
    # 初始化参数列表
    url_param_list = []
    # 遍历参数字典，拼接键值对
    for k, v in get_param.items():
        url_param_list.append(k + "=" + v)
    # 用&连接所有键值对，返回URL查询字符串
    url_str = '&'.join(url_param_list)
    return url_str


# 处理OAuth2登录认证流程，获取访问令牌
def login_auth(auth_url, role_url, client_id, username, password):
    # 设置请求头为JSON格式
    headers = {
        "Content-Type": "application/json",
        # "Accept-Encoding": "gzip, deflate, br",
        # "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.111 Safari/537.36"
    }
    # 构造OAuth2认证参数
    param = {
        "client_id": client_id,
        "redirect_uri": f"{role_url}/callback.html",
        "scope": "openid profile AppGateway",
        "response_type": "code",
        # "state": "b24242fd1e6341fd94b8c15955f7163e",
        # "code_challenge": "Dx9DsnGIfNimOSOToxkB3Wal_iN_MpaP1wJMqoaXdYg",
        # "state": "9b084549b3b34601a717319dcddb141f",
        # "code_challenge": "uabEurI5TVQlniJHv0DC0u1hW2c6XnptsAV6aIxW3uw",
        "code_challenge_method": "S256",
        "response_mode": "query"
    }
    # 将参数转换为URL查询字符串
    url_str = url_param(param)
    # 构造回调URL
    url_str = "/connect/authorize/callback?" + url_str

    # 创建HttpRequests实例
    client = HttpRequests()
    # 发送POST请求进行用户登录
    client.request(
        "POST",
        auth_url,
        "/api/auth/v2/user/login",
        headers,
        {
            "userName": username,
            "password": password,
            "returnUrl": url_str
        }
    )
    # 更新认证参数(代码挑战)
    # param["state"] = "ec75993c1e864423a1bdbf992762318c"
    param["code_challenge"] = "RjYmY2AIeStOvFhy5c1a9GknHQnNNxmQGrzhTH-6yOA"
    # 发送GET请求获取授权码(code)
    client.request(
        "GET",
        auth_url,
        "/connect/authorize/callback",
        headers,
        param
    )
    # 从响应头的Location中提取重定向URL
    location = client.response.history[0].headers['Location']
    # 使用正则表达式从URL中提取授权码(code)
    code = re.search("code=(?P<code>.+?)&", location).group("code")
    # 发送POST请求获取访问令牌(access_token)
    client.request(
        "POST",
        auth_url,
        "/connect/token",
        {"Content-Type": "application/x-www-form-urlencoded"},
        {
            "client_id": client_id,
            "redirect_uri": f"{role_url}/callback.html",
            "code": code,
            "code_verifier": "d161c8c7c8864cc5b5ea5fdd1e4772170b7e8d84d8814475981ad7e64f262c76045feab71b2b42fa8410e3e703f661fd",
            "grant_type": "authorization_code"
        }
    )
    # 从响应中提取访问令牌
    id_token = client.response.json().get("access_token")
    # 从响应中提取令牌类型(通常为Bearer)
    token_type = client.response.json().get("token_type")
    # 拼接完整的Authorization头值
    auto = token_type + " " + id_token
    # 关闭HTTP客户端连接
    client.close()
    # 返回Authorization头值
    return auto


# 获取验证码(支持手机号或邮箱)
def get_captcha(account, sponsor_username=None, sponsor_password=None, client_id=None):
    # 从环境配置中获取认证服务URL
    auth_url = ENV[g.env]["servers"]["auth"]
    # 从环境配置中获取配置服务URL
    conf_url = ENV[g.env]["servers"]["conf"]
    # 从环境配置中获取网关服务URL
    gateway_url = ENV[g.env]["servers"]["gateway"]
    # 获取客户端ID(优先使用传入值，否则从环境配置获取)
    client_id = client_id or ENV[g.env]["global_variable"]["ClientId"]
    # 获取管理员用户名(优先使用传入值，否则从环境配置获取)
    sponsor_username = sponsor_username or ENV[g.env]["global_variable"]["organizers_username"]
    # 获取管理员密码(优先使用传入值，否则从环境配置获取)
    sponsor_password = sponsor_password or ENV[g.env]["global_variable"]["organizers_password"]
    # 执行登录认证，获取访问令牌
    auth = login_auth(auth_url, conf_url, client_id, sponsor_username, sponsor_password)

    # 邮箱格式正则表达式
    email_pattern = r"^[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+){0,4}@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+){0,4}$"
    # 手机号格式正则表达式(中国手机号)
    phone_pattern = r"^1[3|4|5|6|7|8|9][0-9]{9}$"
    # 将账号转换为字符串
    account = str(account)
    # 判断账号是否为手机号格式
    if re.match(phone_pattern, account):
        # 创建HttpRequests实例
        client = HttpRequests()
        # 发送POST请求获取手机验证码
        client.request(
            "POST", gateway_url, "/Api/Communication/SmsCore/GetSmsByMobile",
            {"Content-Type": "application/json", "Authorization": auth},
            {"mobile": account, "pageNo": 1, "pageSize": 20}
        )
        # 获取响应对象
        response = client.response
    # 判断账号是否为邮箱格式
    elif re.match(email_pattern, account):
        # 创建HttpRequests实例
        client = HttpRequests()
        # 发送POST请求获取邮箱验证码
        client.request(
            "POST", gateway_url, "/Api/Communication/EmailCore/GetEmailsByEmail",
            {"Content-Type": "application/json", "Authorization": auth},
            {"email": account, "pageNo": 1, "pageSize": 20}
        )
        # 获取响应对象
        response = client.response
    else:
        # 抛出账号格式错误异常
        raise TypeError("手机或邮箱格式不正确")
    # 从响应JSON中提取最新的验证码(取最后一条记录的authCode字段)
    code = response.json()["list"][-1].get("authCode")
    # 返回验证码
    return code


# 当模块直接运行时执行的代码
if __name__ == "__main__":
    # 导入os模块
    import os
    # 设置环境(如测试环境)
    # g.env = "DH_TEST2"
    # 创建FileHttp实例
    # f = FileHttp()
    # 调用文件上传方法(示例)
    # f.file_upload(r"D:\work\python\project\api_test_project\elegant\files\template\身份证正面基础.jpg")

    # 认证服务URL
    # auth_url = "https://test2-oauth.31huiyi.com"
    # 网关服务URL
    # gateway_url = "https://test2-gateway.31huiyi.com"
    # 角色服务URL
    # role_url = "https://test2-conf.31huiyi.com"
    # 用户名
    # username = "admin"
    # 密码
    # password = "31@huiyi.com"
    # 客户端ID
    # client_id = "evos"

    # 生产环境认证服务URL
    # auth_url = "https://hw-oauth.31huiyi.com"
    # 生产环境网关服务URL
    # gateway_url = "https://hw-gateway.31huiyi.com"
    # 生产环境角色服务URL
    # role_url = "https://hw-mybooth.31huiyi.com"
    # 生产环境用户名
    # username = "18610295360"
    # 生产环境密码
    # password = "huiyi@31"
    # 生产环境客户端ID
    # client_id = "exhibitionCenter"

    # 执行登录认证获取令牌
    # ls = login_auth(auth_url, role_url, client_id, username, password)
    # 获取验证码(示例)
    # ls = get_captcha("13000000001")
    # 打印结果
    # print(ls)
