# 导入arrow模块用于时间处理，pytz用于时区转换，datetime用于日期时间操作
import arrow, pytz, datetime
# 导入random模块用于生成随机数
import random
# 导入re模块用于正则表达式匹配
import re
# 从faker库导入Faker类用于生成模拟数据
from faker import Faker


# 定义时间类型处理函数，将arrow时间对象转换为指定格式的字符串
def dispose_time_type(arrow_time: arrow.Arrow, time_type):
    # 如果目标类型是ctime（13位时间戳）
    if time_type == "ctime":  # 13位时间戳
        # 将arrow时间格式化为YYYY-MM-DD HH:mm:ss字符串
        datetime_str = arrow_time.format('YYYY-MM-DD HH:mm:ss')
        # 将格式化的时间字符串转换为13位时间戳
        str_time = datetime_timestamp(datetime_str)
    # 如果目标类型是utc（UTC时间格式）
    elif time_type == "utc":  # UTC 时间格式
        # utc_time = arrow_time.format('YYYY-MM-DDTHH:mm:ss')
        # str_time = utc_time[:-2] + '00Z'
        # 直接将arrow时间对象转换为字符串
        str_time = str(arrow_time)
    # 如果目标类型包含%（自定义时间格式）
    elif '%' in time_type:
        # 使用strftime方法格式化时间
        str_time = arrow_time.strftime(time_type)
    # 其他情况直接使用arrow的format方法格式化
    else:
        str_time = arrow_time.format(time_type)
    # 返回处理后的时间字符串
    return str_time


# 将datetime格式的字符串转换为13位时间戳（毫秒级）
def datetime_timestamp(datetime_str):
    # 创建8小时的时间差对象（用于时区转换）
    timedelta_offset = datetime.timedelta(hours=8)
    # 将字符串解析为datetime对象
    datetime_time = datetime.datetime.strptime(datetime_str, '%Y-%m-%d %H:%M:%S')
    # 计算相对于1970-01-01的时间差，并减去8小时偏移量（转为UTC时间）
    datetime_date = datetime_time - datetime.datetime(1970, 1, 1) - timedelta_offset
    # 计算总秒数并乘以1000转为毫秒，四舍五入后转为字符串返回
    timestamp = round(datetime_date.total_seconds() * 1000)
    return str(timestamp)


# 将13位时间戳转换为datetime格式的字符串
def timestamp_datetime(timestamp):
    # 创建8小时的时间差对象（用于时区转换）
    timedelta_offset = datetime.timedelta(hours=8)
    # 创建UTC时区对象
    tz_utc = pytz.timezone("UTC")
    # 将毫秒级时间戳转换为秒级
    timestamp = timestamp / 1000
    # 计算从1970-01-01开始的datetime对象
    datetime_time = datetime.datetime(1970, 1, 1) + datetime.timedelta(seconds=timestamp)
    # 将datetime对象本地化到UTC时区
    timezone_utc_date = tz_utc.localize(datetime_time, is_dst=None)
    # 添加8小时偏移量转换为北京时间
    timezone_date = timezone_utc_date + timedelta_offset
    # 格式化为YYYY-MM-DD HH:mm:ss字符串并返回
    datetime_result = timezone_date.strftime('%Y-%m-%d %H:%M:%S')
    return datetime_result


# 生成统一社会信用代码（18位）
def create_social_credit():
    # 统一社会信用代码字符集及对应值（用于校验位计算）
    check_dict = {
        "0": 0, "1": 1, "2": 2, "3": 3, "4": 4, "5": 5, "6": 6, "7": 7, "8": 8, "9": 9,
        "A": 10, "B": 11, "C": 12, "D": 13, "E": 14, "F": 15, "G": 16, "H": 17, "J": 18, "K": 19, "L": 20, "M": 21,
        "N": 22, "P": 23, "Q": 24, "R": 25, "T": 26, "U": 27, "W": 28, "X": 29, "Y": 30
    }
    # 创建值到字符的反向映射字典（用于根据计算值获取对应字符）
    dict_check = {value: key for key, value in check_dict.items()}

    # 登记管理部门代码：9-工商
    manage_code = [9]
    # 机构类型代码：1-企业，2-个体工商户，3-农民专业合作社，9-其他
    type_code = [1, 2, 3, 9]
    # 登记管理机关行政区划码：100000-国家用
    area_code = '100000'

    # 组织机构代码加权因子（用于校验位计算）
    weight_code = [3, 7, 9, 10, 5, 8, 4, 2]
    # 存储组织机构代码的列表
    org_code = []
    # 加权和初始值
    sum = 0
    # 生成8位本体代码
    for i in range(8):
        # 随机选择一个字符作为本体代码
        org_code.append(dict_check[random.randint(0, 30)])
        # 计算加权和
        sum = sum + check_dict[org_code[i]] * weight_code[i]
    # 计算第9位校验码
    C9 = 11 - sum % 11
    # 根据校验码值确定字符
    if C9 == 10:
        last_code = 'X'
    elif C9 == 11:
        last_code = '0'
    else:
        last_code = str(C9)
    # 组合完整组织机构代码（包含校验码）
    code = ''.join(org_code) + '-' + last_code
    # 移除连字符
    org_code = code.replace('-', '')

    # 计算统一社会信用代码的加权和
    sum = 0
    # 社会信用代码加权因子
    weight_code = [1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28]
    # 组合社会信用代码前17位
    code = str(random.choice(manage_code)) + str(random.choice(type_code)) + area_code + org_code
    # 计算前17位的加权和
    for i in range(17):
        sum = sum + check_dict[code[i:i + 1]] * weight_code[i]
    # 计算第18位校验码
    C18 = dict_check[30 - sum % 31]
    # 组合完整的18位统一社会信用代码
    social_code = code + C18
    # 返回生成的社会信用代码
    return social_code



# 计算身份证号码的校验位
def calculate_check_digit(id_number_without_check):
    # 身份证号码加权因子
    weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
    # 校验码对应值（0-10分别对应字符）
    check_codes = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2']

    # 计算加权和
    sum_ = 0
    for i in range(17):
        sum_ += int(id_number_without_check[i]) * weights[i]

    # 根据加权和取模11的结果获取校验码
    return check_codes[sum_ % 11]


# 生成有效的中国身份证号码
def generate_valid_id_number():
    # 创建Faker对象，指定区域为中文（中国）
    fake = Faker(locale='zh_CN')

    # 生成前17位数字（不包含校验码）
    id_number_without_check = fake.ssn()[:-1]  # Faker的ssn可能生成港澳台身份证，此处截取前17位

    # 计算校验码
    check_digit = calculate_check_digit(id_number_without_check)

    # 拼接完整的身份证号
    full_id_number = id_number_without_check + check_digit

    # 验证生成的身份证号是否符合正则表达式（17位数字+1位数字或X）
    id_pattern = re.compile(r'^\d{17}[\dXx]$')
    if not id_pattern.match(full_id_number):
        raise ValueError("生成的身份证号不符合格式要求")

    # 返回生成的有效身份证号
    return full_id_number

# 当模块直接运行时执行的代码
if __name__ == '__main__':

    # 生成一个合法的身份证号
    valid_id_number = generate_valid_id_number()
    # 打印生成的身份证号
    print(valid_id_number)

