用例中token获取
enterprise_code ：

- 来源： config/environment.yaml 文件中的 ERP_TEST.global_variable.enterprise_code 列表
- 具体值： 190787210592256000 （使用列表中的第一个企业编码）
账号密码 ：

- 账号： 15565025655 （来自 ERP_TEST.global_variable.pms_phone_number ）
- 密码： pms2023@! （来自 ERP_TEST.global_variable.pms_pass_word ）
token生成流程 ：

1. $generate_token(pms_host) 占位符调用 libs/genter_auth_tokens.py 中的 generate_token 函数
2. 该函数调用 libs/login_func.py 中的 pms_tokens_data 变量
3. pms_tokens_data 通过 login 函数生成，使用上述企业编码、账号和密码
4. 最终生成的token用于请求头的Authorization字段
配置文件路径 ：

- 主配置文件： f:\JJ_test\automation-test-platform\config\environment.yaml
- token生成逻辑： f:\JJ_test\automation-test-platform\libs\genter_auth_tokens.py
- 登录逻辑： f:\JJ_test\automation-test-platform\libs\token_utils.py