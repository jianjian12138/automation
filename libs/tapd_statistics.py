# 导入所需模块：
# requests - 用于发送HTTP请求
# os - 用于文件路径处理
# time - 用于时间戳转换
# math - 用于数学计算（如向上取整）
# json - 用于JSON数据处理
# arrow - 用于日期时间处理
# jmespath - 用于JSON数据查询
import requests, os, time, math, json, arrow, jmespath
# 从datetime模块导入datetime类，用于处理日期时间对象
from datetime import datetime
# 从jinja2导入模板环境和文件系统加载器，用于HTML报告生成
from jinja2 import Environment, FileSystemLoader
# 从配置中心导入日志对象和基础路径常量
from libs.config_center import LOG, BASE_DIR


# 定义TapdData类，封装TAPD数据获取与统计功能
class TapdData:
    # TAPD API基础URL
    tapd_url = 'https://api.tapd.cn'
    # Bug状态映射字典：将TAPD返回的英文状态转换为中文显示
    bug_map = {'new': '新建', 'in_progress': '开发中', 'unconfirmed': '已修复（开发环境）', 
               'assigned': '测试中（测试环境)', 'verified': '已修复（测试环境)', 
               'resolved': '已上线（正式环境)', 'rejected': '已拒绝', 'closed': '已关闭',
               'reopened': '重新打开'}
    # 需求状态映射字典：将TAPD返回的英文状态转换为中文显示
    story_map = {'planning': '规划中', 'status_1': '交互设计中', 'UI_design': 'UI设计', 
                 'status_5': '需求测试', 'developing': '开发中', 'audited': '开发完成',
                 'testing': '测试中', 'status_2': '已测试（测试环境）', 
                 'status_4': '已上线（生产环境）', 'product_experience': '产品和设计确认',
                 'rejected': '已拒绝', 'resolved': '已关闭', 'status_3': '重新打开'}
    # 优先级映射字典：将TAPD返回的英文优先级转换为中文显示
    priority_map = {'urgent': '紧急', 'high': '高', 'medium': '中', 'low': '低', 
                    'insignificant': '无关紧要'}
    # 发版单状态映射字典：将TAPD返回的英文状态转换为中文显示
    launch_forms_map = {'initial': '初始化', 'auditing': '评审中', 'signing': '待签发', 
                        'sign_completed': '签发完成', 'finished': '确认结束'}
    # 发布状态映射字典：将TAPD返回的英文状态转换为中文显示
    releases_map = {'done': '已关闭', 'open': '开启'}


    # 类初始化方法
    def __init__(self, auth, workspace_list=None, end_date_frame=None):
        self.auth = auth  # TAPD API认证信息（用户名和密码元组）
        self.workspace_name_list = workspace_list  # 需要统计的工作空间名称列表
        # 设置发布统计的日期范围，默认为本周
        self.releases_end_date_frame = end_date_frame or self.this_week_time().split("~")
        self.statistic_data = []  # 存储最终统计结果的数据列表
        self.workspace_releases_data = {}  # 存储工作空间与发布信息的映射关系

    # 获取工作空间ID并初始化基础数据
    def set_workspace_id(self):
        # 调用TAPD API获取公司所有项目信息
        r = self.request('/workspaces/projects?company_id=31213646')
        data = r.json()["data"]
        # 遍历需要统计的工作空间名称
        for workspace_name in self.workspace_name_list:
            # 使用jmespath查询工作空间ID
            workspace_id = jmespath.search(f"[*].Workspace | [?name=='{workspace_name}'].id | [0]", data)
            workspace_data = {}
            LOG.info(f"项目名称：{workspace_name}，项目id：{workspace_id}")
            # 获取该工作空间的所有发布计划
            self.workspace_releases_data[workspace_id] = self.releases_info(workspace_id)
            releases_active_data = []
            # 筛选出在统计日期范围内的发布计划
            for release in self.workspace_releases_data[workspace_id]:
                end_date = release["enddate"]
                if end_date >= self.releases_end_date_frame[0] and end_date <= self.releases_end_date_frame[1]:
                    releases_active_data.append(release)

            # 如果有符合条件的发布计划，则收集工作空间基础数据
            if releases_active_data:
                workspace_data["releases_data"] = releases_active_data
                workspace_data["workspace_name"] = workspace_name
                workspace_data["workspace_id"] = workspace_id
                # 获取高优先级Bug数量
                workspace_data["workspace_red_priority_bug"] = self.get_priority_bug(workspace_id)
                # 构建高优先级Bug查询链接
                workspace_data["workspace_red_bug_link"] = f'https://www.tapd.cn/{workspace_id}/bugtrace/bugreports/my_view?filter=true&data[Filter][priority][]=urgent&data[Filter][priority][]=high&data[Filter][status][]=new&data[Filter][status][]=in_progress&data[Filter][status][]=unconfirmed&data[Filter][status][]=reopened&qksearch=true&qksearch=true'
                self.statistic_data.append(workspace_data)

    # 获取高优先级Bug数量
    def get_priority_bug(self, workspace_id):
        red_priority = ['紧急', '高']  # 需要关注的优先级列表
        bug_status = ['新建', '开发中', '已修复（开发环境）', '重新打开']  # 需要关注的Bug状态列表
        # 将中文优先级转换为TAPD API所需的英文状态值
        priority = self.status_map(workspace_id, "priority", red_priority)
        # 将中文状态转换为TAPD API所需的英文状态值
        status = self.status_map(workspace_id, "bug", bug_status)
        # 构建API查询参数
        query = {"workspace_id": workspace_id,
                 "priority": priority,
                 "status": status,
                 "limit": 200}
        # 调用TAPD API查询符合条件的Bug数量
        r = self.request(f'/bugs/count', data=query)
        bugs_sum = r.json()["data"]["count"]  # 解析API响应获取Bug总数
        LOG.info(f"项目id：{workspace_id}，遗留紧急与高优先级bug为：{bugs_sum} 个")
        return str(bugs_sum)

    # 获取发布计划详情信息
    def get_releases_info(self):
        # 遍历每个工作空间的统计数据
        for workspace_data in self.statistic_data:
            workspace_data["releases_info"] = []  # 存储发布计划详情
            workspace_id = workspace_data.get("workspace_id")
            releases_data = workspace_data["releases_data"]
            schedule_all = 0  # 发布进度总和
            # 遍历每个发布计划
            for release in releases_data:
                releases_correlation = {}
                release_id = release["id"]
                end_date = release["enddate"]
                releases_correlation["end_date"] = end_date
                releases_correlation["releases_name"] = release["name"]
                # 构建发布计划链接
                releases_link = f"https://www.tapd.cn/{workspace_id}/releases/view/{release_id}"
                releases_correlation["releases_link"] = releases_link
                # 计算需求提测率
                lift_rate = self.releases_lift_rate(workspace_id, release_id)
                releases_correlation["lift_rate"] = f"{lift_rate}%"
                # 计算缺陷修复率
                repair_rate = self.releases_repair_rate(workspace_id, release_id)
                releases_correlation["repair_rate"] = f"{repair_rate}%"
                # 计算发布进度（提测率和修复率的平均值）
                release_schedule = (lift_rate + repair_rate) / 2
                releases_correlation["release_schedule"] = f"{release_schedule}%"
                # 获取紧急和高优先级缺陷数量
                releases_correlation["red_bug"] = self.releases_bug_priority(workspace_id, release_id)
                # 获取发布状态
                releases_correlation["releases_status"] = self.releases_map.get(release["status"])
                workspace_data["releases_info"].append(releases_correlation)
                schedule_all += release_schedule

            # 计算平均发布进度
            releases_num = len(workspace_data["releases_info"])
            release_schedule_all = int(float('%.2f' % (schedule_all / releases_num)))
            workspace_data["release_schedule_all"] = f"{release_schedule_all}%"

    def get_releases_bug_info(self):
        bug_status = ['新建', '开发中', '已修复（开发环境）', '测试中（测试环境)', '已修复（测试环境)', '已上线（正式环境)', '已拒绝', '已关闭', '重新打开']
        for workspace_data in self.statistic_data:
            bugs_list = []
            releases_data = workspace_data["releases_data"]
            for release in releases_data:
                release_id = release["id"]
                workspace_data["bug_schedule_info"] = []
                workspace_id = workspace_data.get("workspace_id")
                bug_list, bug_id_list = self.bugs_data(workspace_id, bug_status, release_id)
                bugs_list += bug_list
                for bug_info in bug_list:
                    bug_overtime = {}
                    bug_id = bug_info["id"]
                    bug_overtime[
                        "bug_link"] = f"https://tapd.tencent.com/{workspace_id}/bugtrace/bugs/view?bug_id={bug_id}"
                    bug_overtime["bug_title"] = bug_info["title"]
                    bug_info_status = self.bug_map.get(bug_info["status"])
                    bug_overtime["current_status"] = bug_info_status
                    bug_overtime["current_handler"] = bug_info["current_owner"]
                    bug_overtime = self.rest_release_add(workspace_id, bug_overtime, "bug", release_id)
                    workspace_data["bug_schedule_info"].append(bug_overtime)
            workspace_data["releases_bug_statistics"] = self.releases_bug_statistics(bugs_list)

    def releases_bug_statistics(self, bugs_list):
        # 已解决状态
        resolution_state = ['已修复（开发环境）', '测试中（测试环境)', '已修复（测试环境)', '已上线（正式环境)', '已拒绝', '已关闭']
        # 已修复状态
        repaired_state = ['已修复（开发环境）', '测试中（测试环境)', '已修复（测试环境)', '已上线（正式环境)']

        # 全部bug，未解决的bug，解决率
        bugs_num = len(bugs_list)
        bugs_unsolved = 0
        for bug_info in bugs_list:
            if self.bug_map.get(bug_info["status"]) in resolution_state:
                pass
            else:
                bugs_unsolved += 1
        bugs_rate = 100 if bugs_num == 0 else int(float('%.2f' % ((bugs_num - bugs_unsolved) / bugs_num)) * 100)

        # 优先级为高bug，未解决的优先级为高bug，解决率
        serious_bug = 0
        unsolved_serious_bug = 0
        # 一般bug，未解决的一般bug，解决率
        generic_bug = 0
        unsolved_generic_bug = 0
        for bug_info in bugs_list:
            if self.priority_map.get(bug_info["priority"]) in ['高']:
                serious_bug += 1
                if self.bug_map.get(bug_info["status"]) in resolution_state:
                    pass
                else:
                    unsolved_serious_bug += 1
            elif self.priority_map.get(bug_info["priority"]) in ['中', '低', '无关紧要']:
                generic_bug += 1
                if self.bug_map.get(bug_info["status"]) in resolution_state:
                    pass
                else:
                    unsolved_generic_bug += 1
        serious_bug_rate = 100 if serious_bug == 0 else int(
            float('%.2f' % ((serious_bug - unsolved_serious_bug) / serious_bug)) * 100)
        generic_bug_rate = 100 if generic_bug == 0 else int(
            float('%.2f' % ((generic_bug - unsolved_generic_bug) / generic_bug)) * 100)

        # 紧急bug，未解决的bug，解决率
        urgency_bug = 0
        unsolved_urgency_bug = 0
        for bug_info in bugs_list:
            if self.priority_map.get(bug_info["priority"]) in ['紧急']:
                urgency_bug += 1
                if self.bug_map.get(bug_info["status"]) in resolution_state:
                    pass
                else:
                    unsolved_urgency_bug += 1
        urgency_bug_rate = 100 if urgency_bug == 0 else int(
            float('%.2f' % ((urgency_bug - unsolved_urgency_bug) / urgency_bug)) * 100)

        # 新增bug，修复的bug，关闭的bug
        new_bug = 0
        repair_new_bug = 0
        close_new_bug = 0
        for bug_info in bugs_list:
            created = time.strftime("%Y-%m-%d", time.localtime())
            bug_created = bug_info["created"]
            if created in bug_created:
                new_bug += 1
                if self.bug_map.get(bug_info["status"]) in repaired_state:
                    repair_new_bug += 1
                if self.bug_map.get(bug_info["status"]) in ['已拒绝', '已关闭']:
                    close_new_bug += 1

        bug_statistics = f"""
        今日新增Bug {new_bug} 个，已修复未关闭Bug{repair_new_bug} 个，关闭Bug {close_new_bug} 个 <br>
        项目目前遗留Bug共 {bugs_num} 个，未解决Bug {bugs_unsolved} 个，项目Bug整体解决率：{bugs_rate}% <br>
            其中优先级紧急Bug {urgency_bug} 个，未解决 {unsolved_urgency_bug} 个，解决率：{urgency_bug_rate}% <br>
            其中优先级高Bug {serious_bug} 个，未解决 {unsolved_serious_bug} 个，解决率：{serious_bug_rate}% <br>
            其中优先级中低Bug {generic_bug} 个，未解决 {unsolved_generic_bug} 个，解决率：{generic_bug_rate}% <br>
        """
        return bug_statistics


    def get_releases_stories_info(self):
        story_status = ['规划中', '交互设计中', 'UI设计', '需求测试', '开发中', '开发完成', '测试中', '已测试（测试环境）', '已上线（生产环境）', '已拒绝', '已关闭',
                        '产品和设计确认', '重新打开']
        for workspace_data in self.statistic_data:
            releases_data = workspace_data["releases_data"]
            for release in releases_data:
                release_id = release["id"]
                workspace_data["story_schedule_info"] = []
                workspace_id = workspace_data.get("workspace_id")
                story_list, story_id_list = self.story_data(workspace_id, story_status, release_id)
                for story_info in story_list:
                    story_overtime = {}
                    story_id = story_info["id"]
                    story_overtime[
                        "story_link"] = f"https://tapd.tencent.com/{workspace_id}/prong/stories/view/{story_id}"
                    story_overtime["story_title"] = story_info["name"]
                    story_overtime["current_status"] = self.story_map.get(story_info["status"])
                    story_overtime["current_handler"] = story_info["owner"]
                    story_overtime = self.rest_release_add(workspace_id, story_overtime, "story", release_id)
                    workspace_data["story_schedule_info"].append(story_overtime)

    def get_bug_info(self, created_frame=None):
        bug_status = ['新建', '开发中', '已修复（开发环境）', '重新打开']
        created_time = created_frame or self.this_week_time().split("~")
        for workspace_data in self.statistic_data:
            workspace_data["bug_reopen_info"] = []
            workspace_data["bug_overtime_info"] = []
            workspace_id = workspace_data.get("workspace_id")

            bug_list, bug_id_list = self.bugs_data(workspace_id, bug_status)
            reopened_bug_id_list = self.bug_changes_data(workspace_id, bug_id_list)

            for bug_info in bug_list:
                bug_reopen = {}
                bug_overtime = {}
                bug_id = bug_info["id"]
                # 重新打开次数
                reopened_number = reopened_bug_id_list.count(bug_id)
                stay_duration = self.up_now_time(bug_info["modified"])
                created = bug_info["created"]
                if reopened_number > 0 and created > created_time[0] and created < created_time[1]:
                    bug_reopen["bug_link"] = f"https://www.tapd.cn/{workspace_id}/bugtrace/bugs/view?bug_id={bug_id}"
                    bug_reopen["bug_title"] = bug_info["title"]
                    bug_reopen["reopen_number"] = reopened_number
                    bug_reopen["current_status"] = self.bug_map.get(bug_info["status"])
                    bug_reopen["current_handler"] = bug_info["current_owner"]
                    release_id = bug_info["release_id"]
                    bug_reopen = self.rest_release_add(workspace_id, bug_reopen, "bug", release_id)
                    workspace_data["bug_reopen_info"].append(bug_reopen)
                if stay_duration // 24 > 3:
                    bug_overtime["bug_link"] = f"https://www.tapd.cn/{workspace_id}/bugtrace/bugs/view?bug_id={bug_id}"
                    bug_overtime["bug_title"] = bug_info["title"]
                    bug_overtime["current_status"] = self.bug_map.get(bug_info["status"])
                    bug_overtime["current_handler"] = bug_info["current_owner"]
                    bug_overtime["modified"] = bug_info["modified"]
                    bug_overtime["stay_duration"] = self.hour_time_ctime(stay_duration)
                    release_id = bug_info["release_id"]
                    bug_overtime = self.rest_release_add(workspace_id, bug_overtime, "bug", release_id)
                    workspace_data["bug_overtime_info"].append(bug_overtime)

    def get_stories_info(self, created_frame=None):
        story_status = ['规划中', '交互设计中', 'UI设计', '需求测试', '开发中', '开发完成',
                        '产品和设计确认', '重新打开']
        created_time = created_frame or self.this_week_time().split("~")
        for workspace_data in self.statistic_data:
            workspace_data["story_reopen_info"] = []
            workspace_data["story_overtime_info"] = []
            workspace_id = workspace_data.get("workspace_id")

            story_list, story_id_list = self.story_data(workspace_id, story_status)
            reopened_story_id_list = self.story_changes_data(workspace_id, story_id_list)

            for story_info in story_list:
                story_reopen = {}
                story_overtime = {}
                story_id = story_info["id"]
                # 重新打开次数
                reopened_number = reopened_story_id_list.count(story_id)
                if story_info["status"] == "status_3":
                    reopened_number += 1
                stay_duration = self.up_now_time(story_info["modified"])
                created = story_info["created"]
                if reopened_number > 0 and created > created_time[0] and created < created_time[1]:
                    story_reopen["story_link"] = f"https://www.tapd.cn/{workspace_id}/prong/stories/view/{story_id}"
                    story_reopen["story_title"] = story_info["name"]
                    story_reopen["reopen_number"] = reopened_number
                    story_reopen["current_status"] = self.story_map.get(story_info["status"])
                    story_reopen["current_handler"] = story_info["owner"]
                    release_id = story_info["release_id"]
                    story_reopen = self.rest_release_add(workspace_id, story_reopen, "story", release_id)
                    workspace_data["story_reopen_info"].append(story_reopen)
                if stay_duration // 24 > 3:
                    story_overtime["story_link"] = f"https://www.tapd.cn/{workspace_id}/prong/stories/view/{story_id}"
                    story_overtime["story_title"] = story_info["name"]
                    story_overtime["current_status"] = self.story_map.get(story_info["status"])
                    story_overtime["current_handler"] = story_info["owner"]
                    story_overtime["stay_duration"] = self.hour_time_ctime(stay_duration)
                    story_overtime["modified"] = story_info["modified"]
                    release_id = story_info["release_id"]
                    story_overtime = self.rest_release_add(workspace_id, story_overtime, "story", release_id)
                    workspace_data["story_overtime_info"].append(story_overtime)

    def get_sign_issue_fail(self, created_frame=None):
        if created_frame:
            created_time = "~".join(created_frame)
        else:
            created_time = self.this_week_time()
        for workspace_data in self.statistic_data:
            workspace_data["sign_issue_repulse"] = []
            workspace_id = workspace_data.get("workspace_id")
            p = 1
            while True:
                query = {"workspace_id": workspace_id,
                         "limit": 200,
                         "created": created_time,
                         "page": p}
                r = self.request(f'/launch_forms', data=query)
                launch_forms_info = r.json()["data"]
                if launch_forms_info:
                    for launch_forms in launch_forms_info:
                        launch_forms_data = launch_forms["LaunchForm"]
                        flows_list = launch_forms_data["flows"].split("|")[1:]
                        if "initial" in flows_list:
                            launch_forms_dict = {}
                            launch_forms_dict["title"] = launch_forms_data["title"]
                            launch_forms_dict[
                                "launch_forms_link"] = f"https://www.tapd.cn/{workspace_id}/launch/launch_form/view/{launch_forms_data['id']}"
                            launch_forms_dict["status"] = self.launch_forms_map.get(launch_forms_data["status"])
                            release_id = launch_forms_data["release_id"]
                            launch_forms_dict["fail_number"] = flows_list.count("initial")
                            launch_forms_dict["creator"] = launch_forms_data["creator"]
                            launch_forms_dict["created"] = launch_forms_data["created"]
                            launch_forms_dict = self.rest_release_add(workspace_id, launch_forms_dict, "launchform",
                                                                      release_id)
                            workspace_data["sign_issue_repulse"].append(launch_forms_dict)
                else:
                    break
                p += 1

    def get_smoking_fail(self, created_frame=None):
        if created_frame:
            created_time = "~".join(created_frame)
        else:
            created_time = self.this_week_time()
        for workspace_data in self.statistic_data:
            workspace_data["smoke_repulse"] = []
            workspace_id = workspace_data.get("workspace_id")
            p = 1
            while True:
                query = {"workspace_id": workspace_id,
                         "limit": 200,
                         "created": created_time,
                         "release_result": "release_fail",
                         "page": p}
                r = self.request(f'/launch_forms', data=query)
                launch_forms_info = r.json()["data"]
                if launch_forms_info:
                    for launch_forms in launch_forms_info:
                        launch_forms_dict = {}
                        launch_forms_dict["title"] = launch_forms_data["title"]
                        launch_forms_dict[
                            "launch_forms_link"] = f"https://www.tapd.cn/{workspace_id}/launch/launch_form/view/{launch_forms_data['id']}"
                        # launch_forms_dict["status"] = self.launch_forms_map.get(launch_forms_data["status"])
                        release_id = launch_forms_data["release_id"]
                        launch_forms_dict["archived_by"] = launch_forms_data["archived_by"]
                        launch_forms_dict["creator"] = launch_forms_data["creator"]
                        launch_forms_dict["created"] = launch_forms_data["created"]
                        launch_forms_dict = self.rest_release_add(workspace_id, launch_forms_dict, "launchform",
                                                                  release_id)
                        workspace_data["smoke_repulse"].append(launch_forms_dict)
                else:
                    break
                p += 1

    def story_data(self, workspace_id, status, release_id=None):
        status = self.status_map(workspace_id, "story", status)
        story_id_list = []
        story_list = []
        p = 1
        while True:
            query = {"workspace_id": workspace_id,
                     "status": status,
                     "release_id": release_id,
                     "limit": 200,
                     "page": p}
            r = self.request(f'/stories', data=query)
            story_info = r.json()["data"]

            if story_info:
                for story in story_info:
                    story_id = story["Story"]["id"]
                    story_list.append(story["Story"])
                    story_id_list.append(story_id)
            else:
                break
            p += 1
        LOG.info(f"项目id：{workspace_id}，获取到的未关闭需求总数：{len(story_list)}")
        return story_list, story_id_list

    def bugs_data(self, workspace_id, status, release_id=None):
        status = self.status_map(workspace_id, "bug", status)
        bug_id_list = []
        bug_list = []
        p = 1
        while True:
            query = {"workspace_id": workspace_id,
                     "status": status,
                     "limit": 200,
                     "release_id": release_id,
                     "page": p}
            r = self.request(f'/bugs', data=query)
            bugs_info = r.json()["data"]
            if bugs_info:
                for bug in bugs_info:
                    bug_id = bug["Bug"]["id"]
                    bug_list.append(bug["Bug"])
                    bug_id_list.append(bug_id)
            else:
                break
            p += 1
        LOG.info(f"项目id：{workspace_id}，获取到的未关闭bug总数：{len(bug_list)}")
        return bug_list, bug_id_list

    def bug_changes_data(self, workspace_id, bug_id_list):
        # reopened_bug_changes_list = []
        reopened_bug_id_list = []
        bugs_id_list = self.list_allocation(bug_id_list, 30)
        for bug_ids in bugs_id_list:
            bug_ids = ",".join(bug_ids)

            p = 1
            while True:
                query = {"workspace_id": workspace_id,
                         # "bug_id": "1131174600001034415,1131174600001028019",
                         "bug_id": bug_ids,
                         "field": "status",
                         "new_value": "reopened",
                         "limit": 30,
                         "page": p}
                r = self.request(f'/bug_changes', data=query)
                bug_changes_info = r.json()["data"]

                if bug_changes_info:
                    for bug_changes in bug_changes_info:
                        bug_id = bug_changes["BugChange"]["bug_id"]
                        reopened_bug_id_list.append(bug_id)
                        # reopened_bug_changes_list.append(bug_changes["BugChange"])
                else:
                    break
                p += 1
        return reopened_bug_id_list

    def story_changes_data(self, workspace_id, story_id_list):
        reopened_story_id_list = []
        story_id_list = self.list_allocation(story_id_list, 30)
        for story_ids in story_id_list:
            story_ids = ",".join(story_ids)

            p = 1
            while True:
                query = {"workspace_id": workspace_id,
                         "story_id": story_ids,
                         "change_summary": "status_3",
                         "limit": 30,
                         "page": p}
                r = self.request(f'/story_changes', data=query)
                story_changes_info = r.json()["data"]

                if story_changes_info:
                    for story_changes in story_changes_info:
                        story_id = story_changes["WorkitemChange"]["story_id"]
                        changes = story_changes["WorkitemChange"]["changes"]
                        if '"value_before":"status_3"' in changes:
                            reopened_story_id_list.append(story_id)
                else:
                    break
                p += 1
        return reopened_story_id_list

    def releases_lift_rate(self, workspace_id, release_id):
        demand_status = ['测试中', '已测试（测试环境）', '已上线（生产环境）', '重新打开', '已拒绝', '已关闭']
        status = self.status_map(workspace_id, "story", demand_status)
        query = {"workspace_id": workspace_id,
                 "release_id": release_id}
        r1 = self.request('/stories/count', data=query)
        stories_sum = r1.json()["data"]["count"]

        query["status"] = status
        r2 = self.request('/stories/count', data=query)
        stories_done = r2.json()["data"]["count"]
        if stories_sum == 0:
            return 100
        lift_rate = int(float('%.2f' % (stories_done / stories_sum)) * 100)
        return lift_rate

    def releases_repair_rate(self, workspace_id, release_id):
        bug_status = ['测试中（测试环境)', '已修复（测试环境)', '已上线（正式环境)', '已拒绝', '已关闭']
        status = self.status_map(workspace_id, "bug", bug_status)
        query = {"workspace_id": workspace_id,
                 "release_id": release_id}
        r1 = self.request('/bugs/count', data=query)
        bugs_sum = r1.json()["data"]["count"]

        query["status"] = status
        r2 = self.request('/bugs/count', data=query)
        bugs_done = r2.json()["data"]["count"]
        if bugs_sum == 0:
            return 100
        repair_rate = int(float('%.2f' % (bugs_done / bugs_sum)) * 100)
        return repair_rate

    def releases_bug_priority(self, workspace_id, release_id):
        red_priority = ['紧急', '高']
        bug_status = ['新建', '开发中', '已修复（开发环境）', '重新打开']
        priority = self.status_map(workspace_id, "priority", red_priority)
        status = self.status_map(workspace_id, "bug", bug_status)
        query = {"workspace_id": workspace_id,
                 "release_id": release_id,
                 "priority": priority,
                 "status": status,
                 "limit": 200}
        r = self.request(f'/bugs/count', data=query)
        bugs_sum = r.json()["data"]["count"]
        return str(bugs_sum)

    def rest_release_add(self, workspace_id, rest_info, rest_type, release_id):
        if release_id and release_id != "0":
            release_title = jmespath.search(f"[?id=='{release_id}'].name | [0]",
                                            self.workspace_releases_data[workspace_id])
            rest_info["release_title"] = release_title
            rest_info[
                "release_link"] = f"https://www.tapd.cn/{workspace_id}/releases/view/{release_id}#tab=tab-{rest_type}"
        else:
            rest_info["release_title"] = ""
            rest_info["release_link"] = "#"
        return rest_info

    def releases_info(self, workspace_id):
        releases_data_list = []
        p = 1
        while True:
            query = {"workspace_id": workspace_id,
                     "limit": 200,
                     "page": p}
            r = self.request(f'/releases', data=query)
            releases_info = r.json()["data"]
            if releases_info:
                releases_data_list += releases_info
            else:
                break
            p += 1
        LOG.info(f"项目id：{workspace_id}，获取到的发布计划总数：{len(releases_data_list)}")
        data = jmespath.search(f"[*].Release", releases_data_list)
        return data

    def status_map(self, workspace_id, system, zh_status):
        # r = self.request(f'/workflows/status_map?system={system}&workspace_id={workspace_id}')
        # data = r.json()["data"]
        if system == "bug":
            data = self.bug_map
        elif system == "story":
            data = self.story_map
        elif system == "priority":
            data = self.priority_map
        elif system == "releases":
            data = self.releases_map
        else:
            raise
        en_status = []
        for state in zh_status:
            status = self.dict_value_query_key(data, state)
            en_status.append(status)
        return '|'.join(en_status)

    @staticmethod
    def this_week_time():
        a = arrow.now()
        week_floor, week_ceil = a.span("weeks")
        week_floor.format('YYYY-MM-DD')
        week_ceil.format('YYYY-MM-DD')
        created_time = f"{week_floor.format('YYYY-MM-DD')}~{week_ceil.format('YYYY-MM-DD')}"
        return created_time

    @staticmethod
    def up_now_time(created):
        time_struct = datetime.strptime(created, "%Y-%m-%d %H:%M:%S")
        now = datetime.now()
        duration = (now - time_struct).total_seconds()

        duration = math.ceil(duration / 3600)
        # if duration // 24 < 3:
        #     return str(duration) + " h"
        # else:
        #     return "超过 3 天"
        return duration

    @staticmethod
    def hour_time_ctime(hour_time):
        day = hour_time // 24
        hour = hour_time % 24
        if hour:
            return f"{day}天{hour}小时"
        else:
            return f"{day}天"

    @staticmethod
    def dict_value_query_key(dict_data, value):
        return list(dict_data.keys())[list(dict_data.values()).index(value)]

    @staticmethod
    def list_allocation(list_data, number):
        # n = math.ceil(len(list_data) / number)
        return [list_data[i:i + number] for i in range(0, len(list_data), number)]

    def request(self, api, method="get", data=None):
        retry = True
        while retry:
            if method == "get":
                r = requests.get(self.tapd_url + api, params=data, auth=self.auth)
                # LOG.info(f"请求接口：{api}，请求数据：{data}，返回数据：{r.json()}")
            else:
                raise
            if r.status_code == 200:
                return r
            else:
                LOG.info("接口超频，等待5秒")
                time.sleep(5)


def generate_html(report_info_list):
    env = Environment(loader=FileSystemLoader(os.path.join(BASE_DIR, "files", "template")))  # 加载模板
    template = env.get_template('tapd_report_template.html')

    report_path = os.path.join(BASE_DIR, 'report')
    if not os.path.exists(report_path):
        os.mkdir(report_path)
    report_file_path = os.path.join(report_path, 'tapd_report.html')

    with open(report_file_path, 'w', encoding='utf-8') as f:
        html_content = template.render(report_info_list=report_info_list,
                                       subject_time=time.strftime("%Y-%m-%d", time.localtime()),
                                       update_time=time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()))
        f.write(html_content)  # 写入模板 生成html
    LOG.info("生成报告完成")


if __name__ == "__main__":
    work_auth = ('Jrf*Qb9G', 'C4F40C4D-4D06-3077-AF07-499361B878F9')
    workspace_name_list = ['第128届广交会']
    t = TapdData(work_auth, workspace_name_list, ["2020-01-01", '2021-01-01'])
    t.set_workspace_id()
    t.get_releases_info()
    t.get_releases_bug_info()
    # t.get_releases_stories_info()
    t.get_bug_info()
    # t.get_stories_info()
    # t.get_sign_issue_fail()
    # t.get_smoking_fail()
    # print(t.statistic_data)
    # print(json.dumps(t.statistic_data, ensure_ascii=False))
    generate_html(t.statistic_data)

    # print(t.releases_info("61367386"))
    # t.bugs_data("31174600", ['已拒绝', '已关闭'])
    # print(t.up_now_time("2020-08-01 16:11:48"))
    # t.story_data("61367386", ['规划中'], "1161367386001000545")
    # print(t.request(f'/workflows/status_map?system=bug&workspace_id=61367386').json()["data"])
    # print(t.request("/bug_changes?workspace_id=31174600&new_value=reopened&bug_id=1131174600001034415").json()["data"])
    # print(t.request("/bugs?workspace_id=61367386&id=1161367386001037048").json()["data"]["Bug"])
    # print(t.request("/stories/count?workspace_id=51204029&release_id=1151204029001000530&status=testing|status_2|status_4|status_3").json()["data"])
    # t.releases_lift_rate("51204029", "1151204029001000530")
    # print(t.request("/story_changes?workspace_id=31174600&story_id=1131174600001020964,1131174600001020979&change_summary=status_3").json()["data"])
    # print(t.request("/launch_forms?workspace_id=61367386").json()["data"])
    # print(t.request("/releases?workspace_id=61367386&status=open&enddate=2020-01-01~2021-01-01").json()["data"])
    # print(t.status_map("31174600", "bug"))
    # print(t.status_map("31174600", "story", ["测试中"]))
    # print(t.story_changes_data("31174600", ["1131174600001016699"]))

    # str.encode('utf-8').decode('unicode_escape')
    # "\\u96f7\\u8d77\\u6ce2".encode('utf-8').decode('unicode_escape')

# 需求提测率：根据发布计划获取需求状态（测试中，已测试、已上线、重新打开），显示每一条发布计划名称、已提测需求/总数、发布计划链接
# 缺陷修复率：根据发布计划获取缺陷状态（测试中、已修复、 已上线、已拒绝、已关闭），显示每一条发布计划名称、已修复缺陷/总数、发布计划链接
# 紧急和高优先级缺陷：根据发布计划获取非关闭和已拒绝，且优先级为紧急和高的缺陷总数，显示发布计划、紧急和高优先级缺陷遗留总数、
# 项目紧急和高优先级缺陷遗留总数
# 缺陷重新打开：当前状态非已关闭已拒绝的，缺陷历史有重新打开或当前状态为重新打开，显示 缺陷标题、重新打开次数、当前状态、当前处理人、缺陷链接
# 缺陷停留时长：状态非已关闭已拒绝的总时长超过3天，显示缺陷标题[Bug标题]、停留时长、当前状态、当前处理人、缺陷链接

# 需求缺陷数：缺陷标题存在"需求测试" 或 缺陷类型为需求遗漏的缺陷，显示需求标题、需求测试缺陷总数、需求测试未关闭缺陷总数 、当前状态、当前处理人、需求创建人、需求链接
# 产品体验打回：需求历史变更中有状态"产品和设计确认"到"重新打开"的需求的需求部分详情（含重新打开的指派人），显示需求标题、当前状态、当前处理人、需求链接

# 签发打回：发布评审中签发重新初始化的，显示发布评审名称、链接、当前状态、签发失败次数、签发人、发布评审创建人、创建时间
# 冒烟打回：发布评审中发布确认结果中选择发布失败的，显示发布评审名称、链接、当前状态、发布确认人、发布评审创建人、创建时间
```
