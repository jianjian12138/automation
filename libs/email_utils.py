# 导入必要模块
import smtplib  # SMTP邮件发送协议模块
import traceback  # 异常跟踪模块
import datetime  # 日期时间处理模块
from email.mime.text import MIMEText  # 用于构建文本邮件内容
from email.mime.multipart import MIMEMultipart  # 用于构建多部分邮件（文本+附件）
from email.mime.application import MIMEApplication  # 用于处理邮件附件
from email.header import Header  # 用于设置邮件头信息
import os  # 操作系统功能模块

from libs.config_center import LOG  # 从配置中心导入日志对象


class MailUtils:
    """邮件发送工具类 - 用于发送包含测试报告的邮件"""
    def __init__(self, mail_host, mail_port, mail_user, mail_pwd, recipients, cc_recipients, subject="", report_path="",
                 content="", attachment=True):
        """
        初始化邮件发送工具
        :param mail_host: SMTP服务器地址
        :param mail_port: SMTP服务器端口
        :param mail_user: 发件人邮箱
        :param mail_pwd: 发件人邮箱密码/授权码
        :param recipients: 收件人列表
        :param cc_recipients: 抄送列表
        :param subject: 邮件主题
        :param report_path: 测试报告文件路径
        :param content: 邮件正文内容
        :param attachment: 是否添加附件
        """
        self.mail_host = mail_host  # SMTP服务器地址
        self.mail_port = mail_port  # SMTP服务器端口
        self.mail_user = mail_user  # 发件人邮箱
        self.mail_pwd = mail_pwd  # 发件人邮箱密码/授权码
        self.to_list = recipients  # 收件人列表
        self.cc_list = cc_recipients  # 抄送列表
        self.subject = subject  # 邮件主题
        self.report_path = report_path  # 测试报告文件路径
        self.attachment = attachment  # 是否添加附件
        self.content = content  # 邮件正文内容

    def send(self):
        """发送邮件的核心方法"""
        try:
            # 创建多部分邮件对象（支持文本和附件）
            root_msg = MIMEMultipart()
            # 设置邮件主题，使用UTF-8编码
            root_msg['Subject'] = Header(self.subject, 'utf-8')
            # 设置发件人，使用UTF-8编码
            root_msg['From'] = Header(self.mail_user, 'utf-8')
            # 设置收件人，将列表转换为分号分隔的字符串
            root_msg['To'] = ';'.join(self.to_list)
            # 设置抄送，将列表转换为分号分隔的字符串
            root_msg['Cc'] = ';'.join(self.cc_list)
            # 创建邮件正文对象（HTML格式）
            message = MIMEText(self.content, 'html', 'utf-8')

            # 如果提供了测试报告路径
            if self.report_path:
                # 读取测试报告HTML内容
                with open(self.report_path, "r", encoding="UTF-8") as f:
                    data = f.read()
                # 将邮件内容与报告内容合并
                html = self.content + "\n" + data
                # 更新邮件正文为合并后的HTML内容
                message = MIMEText(html, 'html', 'utf-8')
                # 如果需要添加附件
                if self.attachment:
                    # 获取报告文件名
                    file_name = os.path.split(self.report_path)[-1]
                    # 创建附件对象并读取报告文件内容
                    attachment_msg = MIMEApplication(open(self.report_path, 'rb').read())
                    # 设置附件头信息
                    attachment_msg.add_header('content-disposition', 'attachment', filename=file_name)
                    # 将附件添加到邮件对象
                    root_msg.attach(attachment_msg)

            # 将邮件正文添加到多部分邮件对象
            root_msg.attach(message)
            # 创建SMTP服务器连接
            smtp_server = smtplib.SMTP(host=self.mail_host, port=self.mail_port)
            # 设置调试级别（1表示开启调试输出）
            smtp_server.set_debuglevel(1)
            # 连接SMTP服务器
            smtp_server.connect(self.mail_host, self.mail_port)
            # 启用TLS加密
            smtp_server.starttls()
            # 登录邮箱
            smtp_server.login(self.mail_user, self.mail_pwd)
            # 发送邮件（发件人，收件人+抄送，邮件内容字符串）
            smtp_server.sendmail(self.mail_user, self.to_list + self.cc_list, root_msg.as_string())

        except Exception:
            # 记录邮件发送失败日志
            LOG.error("发送邮件失败")
            # 记录详细的异常堆栈信息
            LOG.error(traceback.format_exc())


# 主程序入口（空实现，用于模块单独运行测试）
if __name__ == "__main__":
    pass
