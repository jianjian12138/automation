# 导入必要模块
import time, socket  # 时间处理模块和网络套接字模块
from itertools import chain  # 用于将多个迭代器链接成一个单一的迭代器
from html import escape  # 用于HTML转义，防止XSS攻击
from locust.runners import MasterRunner  # 从Locust导入主运行器类
from locust.stats import sort_stats  # 从Locust导入统计数据排序函数
from locust import runners as locust_runners  # 导入Locust运行器模块
from locust.util.rounding import proper_round  # 导入Locust的四舍五入工具函数
from threading import Timer  # 导入线程计时器类
from prometheus_client.metrics_core import Metric  # 导入Prometheus指标核心类
from prometheus_client.registry import REGISTRY  # 导入Prometheus注册表
from prometheus_client.exposition import pushadd_to_gateway, delete_from_gateway  # 导入Prometheus网关推送和删除函数


class RepeatingTimer(Timer):
    """
    重复执行计时器 - 继承自threading.Timer
    重写run方法，实现任务的周期性重复执行
    """
    def run(self):
        # 循环执行任务，直到计时器被取消
        while not self.finished.is_set():
            # 执行目标函数
            self.function(*self.args, **self.kwargs)
            # 等待指定的时间间隔
            self.finished.wait(self.interval)


class LocustCollector:
    """
    Locust性能测试数据收集器
    用于收集Locust测试指标并推送到Prometheus
    """
    registry = REGISTRY  # Prometheus注册表实例

    def __init__(self, runner):
        """
        初始化LocustCollector实例
        :param runner: Locust运行器实例
        """
        self.runner = runner  # 保存Locust运行器实例
        self.prometheus_gateway = 'http://49.232.195.89:9091'  # Prometheus网关地址
        self.prometheus_job = 'locust'  # Prometheus任务名称
        self.timer = None  # 计时器实例初始化为None

    def collect(self):
        """
        收集Locust测试指标
        :return: 生成Prometheus指标
        """
        # 获取Locust运行器实例
        runner = self.runner
        
        # 收集请求统计数据
        stats = []
        # 遍历所有统计条目和总计统计
        for s in chain(sort_stats(runner.stats.entries), [runner.stats.total]):
            stats.append({
                "method": s.method,  # 请求方法
                "name": s.name,  # 请求名称
                "num_requests": s.num_requests,  # 请求总数
                "num_failures": s.num_failures,  # 失败请求数
                "avg_response_time": s.avg_response_time,  # 平均响应时间
                "min_response_time": s.min_response_time or 0,  # 最小响应时间
                "max_response_time": s.max_response_time,  # 最大响应时间
                "current_rps": s.current_rps,  # 当前RPS(每秒请求数)
                "median_response_time": s.median_response_time,  # 中位数响应时间
                "ninetieth_response_time": s.get_response_time_percentile(0.9),  # 90%响应时间
                "avg_content_length": s.avg_content_length,  # 平均内容长度
                "current_fail_per_sec": s.current_fail_per_sec  # 当前每秒失败数
            })

        # 收集错误统计数据
        errors = []
        for e in runner.errors.values():
            err_dict = e.to_dict()  # 将错误信息转换为字典
            # 对错误信息中的特殊字符进行HTML转义
            err_dict["name"] = escape(err_dict["name"])
            err_dict["error"] = escape(err_dict["error"])
            err_dict["method"] = escape(err_dict["method"])
            errors.append(err_dict)

        # 创建用户数量指标
        metric = Metric('locust_user_count', 'Swarmed users', 'gauge')
        metric.add_sample('locust_user_count', value=runner.user_count, labels={})
        yield metric

        # 创建错误数量指标
        metric = Metric('locust_errors', 'Locust requests errors', 'gauge')
        for err in errors:
            metric.add_sample('locust_errors', value=err['occurrences'],
                              labels={'path': err['name'], 'method': err['method'],
                                      'error': err['error']})
        yield metric

        # 如果是分布式测试，创建从节点数量指标
        is_distributed = isinstance(runner, locust_runners.MasterRunner)
        if is_distributed:
            metric = Metric('locust_slave_count', 'Locust number of workers', 'gauge')
            metric.add_sample('locust_slave_count', value=len(runner.clients.values()), labels={})
            yield metric

        # 创建失败率指标
        metric = Metric('locust_fail_ratio', 'Locust failure ratio', 'gauge')
        metric.add_sample('locust_fail_ratio', value=runner.stats.total.fail_ratio, labels={})
        yield metric

        # 创建测试状态指标
        metric = Metric('locust_state', 'State of the locust swarm', 'gauge')
        metric.add_sample('locust_state', value=1, labels={'state': runner.state})
        yield metric

        # 定义需要收集的统计指标列表
        stats_metrics = ['avg_content_length', 'avg_response_time', 'current_rps', 'current_fail_per_sec',
                         'max_response_time', 'ninetieth_response_time', 'median_response_time',
                         'min_response_time',
                         'num_failures', 'num_requests']

        # 为每个统计指标创建Prometheus指标
        for mtr in stats_metrics:
            mtype = 'gauge'  # 指标类型为 gauge
            metric = Metric('locust_stats_' + mtr, 'Locust stats ' + mtr, mtype)
            for stat in stats:
                # 聚合统计的方法标签为None，将其命名为Aggregated
                if 'Aggregated' != stat['name']:
                    metric.add_sample('locust_stats_' + mtr, value=stat[mtr],
                                      labels={'path': stat['name'], 'method': stat['method']})
                else:
                    metric.add_sample('locust_stats_' + mtr, value=stat[mtr],
                                      labels={'path': stat['name'], 'method': 'Aggregated'})
            yield metric

    def data_post(self):
        """将收集的指标推送到Prometheus网关"""
        pushadd_to_gateway(self.prometheus_gateway, job=self.prometheus_job, registry=self.registry)

    def data_delete(self):
        """从Prometheus网关删除指标"""
        delete_from_gateway(self.prometheus_gateway, job=self.prometheus_job)

    def timer_collector(self):
        """创建重复计时器，每5秒执行一次数据推送"""
        self.timer = RepeatingTimer(5.0, self.data_post, [])

    def timer_start(self):
        """启动计时器"""
        try:
            self.timer.start()
        except RuntimeError:
            # 如果计时器已在运行，则重新创建并启动
            self.timer_collector()
            self.timer.start()

    def timer_cancel(self):
        """取消计时器并删除Prometheus指标"""
        self.timer.cancel()
        self.data_delete()


class LocustCollectorInfluxDB:
    """
    Locust性能测试数据收集器 - InfluxDB版本
    用于收集Locust测试指标并存储到InfluxDB数据库
    """

    def __init__(self, runner, case_name):
        """
        初始化LocustCollectorInfluxDB实例
        :param runner: Locust运行器实例
        :param case_name: 测试用例名称
        """
        self.runner = runner  # 保存Locust运行器实例
        self.case_name = case_name  # 保存测试用例名称
        self.timer = None  # 计时器实例初始化为None
        self.influx_db_client = self.influx_db_create()  # 创建InfluxDB客户端
        self.hostname = socket.gethostname()  # 获取主机名

    def timer_collector(self):
        """创建重复计时器，每2秒执行一次数据收集"""
        self.timer = RepeatingTimer(2.0, self.data_collection, [])

    def timer_start(self):
        """启动计时器"""
        try:
            self.timer.start()
        except RuntimeError:
            # 如果计时器已在运行，则重新创建并启动
            self.timer_collector()
            self.timer.start()

    def timer_cancel(self):
        """取消计时器"""
        self.timer.cancel()

    def get_locust_stats(self):
        """
        获取Locust测试统计数据
        :return: 包含统计数据的字典
        """
        # 收集指标仅当Locust运行器处于生成或运行状态
        runner = self.runner
        stats = []

        # 获取并处理请求统计数据
        for s in chain(sort_stats(runner.stats.entries), [runner.stats.total]):
            stats.append({
                "method": s.method,
                "name": s.name,
                "safe_name": escape(s.name, quote=False),
                "num_requests": s.num_requests,
                "num_failures": s.num_failures,
                "avg_response_time": float(s.avg_response_time),
                "min_response_time": 0.0 if s.min_response_time is None else float(proper_round(s.min_response_time)),
                "max_response_time": float(proper_round(s.max_response_time)),
                "current_rps": float(s.current_rps),
                "current_fail_per_sec": float(s.current_fail_per_sec),
                "median_response_time": int(s.median_response_time),
                "ninety_five_response_time": int(s.get_response_time_percentile(0.95)),
                "avg_content_length": float(s.avg_content_length),
            })

        # 获取并处理错误统计数据
        errors = []
        for e in runner.errors.values():
            err_dict = e.to_dict()
            err_dict["name"] = escape(err_dict["name"])
            err_dict["error"] = escape(err_dict["error"])
            errors.append(err_dict)

        # 截断统计和错误数据以提高渲染性能，保留聚合统计
        report = {"stats": stats[:500], "errors": errors[:500]}
        if len(stats) > 500:
            report["stats"] += [stats[-1]]

        # 添加总计统计数据
        if stats:
            report["total_rps"] = float(stats[len(stats) - 1]["current_rps"])
            report["fail_ratio"] = float(runner.stats.total.fail_ratio)
            report["current_response_time_percentile_95"] = int(
                runner.stats.total.get_current_response_time_percentile(0.95) or 0)
            report["current_response_time_percentile_50"] = int(
                runner.stats.total.get_current_response_time_percentile(0.5) or 0)

        # 如果是分布式测试，添加工作节点信息
        is_distributed = isinstance(runner, MasterRunner)
        if is_distributed:
            workers = []
            for worker in runner.clients.values():
                workers.append({
                    "id": worker.id,
                    "state": worker.state,
                    "user_count": worker.user_count,
                    "cpu_usage": float(worker.cpu_usage)
                })
            report["workers"] = workers

        # 添加测试状态和用户数量
        report["state"] = runner.state
        report["user_count"] = runner.user_count

        return report

    @staticmethod
    def influx_db_create(database='locust'):
        """
        创建InfluxDB客户端
        :param database: 数据库名称，默认为'locust'
        :return: InfluxDB客户端实例
        """
        from influxdb import InfluxDBClient  # 延迟导入InfluxDBClient

        # 创建InfluxDB客户端并切换到指定数据库
        influx_db_client = InfluxDBClient(host="106.75.33.65", port="8086")
        influx_db_client.switch_database(database)
        return influx_db_client

    def data_collection(self):
        """收集Locust测试数据并写入InfluxDB"""
        locust_data = []
        now_time = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.localtime())  # 当前时间，ISO格式
        locust_report = self.get_locust_stats()  # 获取Locust测试报告

        # 处理统计数据
        stats = locust_report.pop("stats", [])
        if stats:
            influx_db_stats = self.data_stats(stats, now_time)
            locust_data += influx_db_stats

        # 处理错误数据
        errors = locust_report.pop("errors", [])
        if errors:
            influx_db_errors = self.data_errors(errors, now_time)
            locust_data += influx_db_errors

        # 处理工作节点数据
        workers = locust_report.pop("workers", [])
        if workers:
            influx_db_workers = self.data_workers(workers, now_time)
            locust_data += influx_db_workers

        # 添加总体数据
        state = locust_report.pop("state", "idle")
        data_template = {
            "measurement": "locust_all",  # 表名
            "tags": {  # 标签
                "case_name": self.case_name,
                "state": state
            },
            "time": now_time,  # 时间戳
            "fields": locust_report  # 字段数据
        }
        locust_data.append(data_template)

        # 将数据写入InfluxDB
        self.influx_db_client.write_points(locust_data)

    def data_stats(self, stats, now_time):
        """
        格式化统计数据为InfluxDB格式
        :param stats: 统计数据列表
        :param now_time: 当前时间
        :return: 格式化后的InfluxDB数据列表
        """
        influx_db_stats = []
        for i in stats:
            name = i.pop("name")
            stats_data_template = {
                "measurement": "locust",  # 表名
                "tags": {  # 标签
                    "case_name": self.case_name,
                    "name": name
                },
                "time": now_time,  # 时间戳
                "fields": i  # 字段数据
            }
            influx_db_stats.append(stats_data_template)
        return influx_db_stats

    def data_errors(self, errors, now_time):
        """
        格式化错误数据为InfluxDB格式
        :param errors: 错误数据列表
        :param now_time: 当前时间
        :return: 格式化后的InfluxDB数据列表
        """
        influx_db_errors = []
        for i in errors:
            name = i.pop("name")
            errors_data_template = {
                "measurement": "locust_errors",  # 表名
                "tags": {  # 标签
                    "case_name": self.case_name,
                    "name": name
                },
                "time": now_time,  # 时间戳
                "fields": i  # 字段数据
            }
            influx_db_errors.append(errors_data_template)
        return influx_db_errors

    def data_workers(self, workers, now_time):
        """
        格式化工作节点数据为InfluxDB格式
        :param workers: 工作节点数据列表
        :param now_time: 当前时间
        :return: 格式化后的InfluxDB数据列表
        """
        influx_db_workers = []
        for i in workers:
            worker_id = i.pop("id")
            workers_data_template = {
                "measurement": "locust_workers",  # 表名
                "tags": {  # 标签
                    "case_name": self.case_name,
                    "worker_id": worker_id
                },
                "time": now_time,  # 时间戳
                "fields": i  # 字段数据
            }
            influx_db_workers.append(workers_data_template)
        return influx_db_workers


"""
Locust事件监听器示例代码（当前为注释状态）
用于在Locust测试生命周期中自动启动和停止数据收集
"""
"""
@events.init.add_listener
def locust_init(environment, **kwargs):
    # 仅在主节点和独立模式下运行
    if COLLECT and not isinstance(environment.runner, WorkerRunner):
        global collector
        case_name = PATH.split(".")[0]
        collector = LocustCollector(environment.runner, case_name)
        collector.timer_collector()


@events.test_start.add_listener
def locust_start(** kwargs):
    global collector
    if isinstance(collector, LocustCollector):
        collector.timer_start()


@events.test_stop.add_listener
def locust_stop(**kwargs):
    global collector
    if isinstance(collector, LocustCollector):
        collector.timer_cancel()
"""

# 主程序入口（空实现）
if __name__ == "__main__":
    pass
