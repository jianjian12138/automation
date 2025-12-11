import sys
import os
import datetime
from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QTabWidget, QGroupBox, QFormLayout, QLineEdit, QPushButton,
    QComboBox, QTextEdit, QLabel, QSpinBox, QDateEdit, QTimeEdit,
    QDateTimeEdit, QFileDialog, QMessageBox, QTableWidget,
    QTableWidgetItem, QProgressBar, QSplitter, QTreeWidget,
    QTreeWidgetItem, QMenu, QAction, QInputDialog, QRadioButton,
    QButtonGroup, QFrame
)
from PyQt5.QtCore import Qt, QThread, pyqtSignal, QDate, QTime, QDateTime
from PyQt5.QtGui import QFont, QIcon

from get_logs_tool import LogExtractor

class ConnectThread(QThread):
    result = pyqtSignal(bool, str)
    
    def __init__(self, host, port, username, password, key_filename):
        super().__init__()
        self.host = host
        self.port = port
        self.username = username
        self.password = password
        self.key_filename = key_filename
        self.extractor = None
    
    def run(self):
        try:
            self.extractor = LogExtractor(
                host=self.host,
                port=self.port,
                username=self.username,
                password=self.password,
                key_filename=self.key_filename
            )
            success = self.extractor.connect()
            self.result.emit(success, "连接成功" if success else "连接失败")
        except Exception as e:
            self.result.emit(False, f"连接异常: {str(e)}")

class DownloadLogThread(QThread):
    result = pyqtSignal(bool, str, str)
    
    def __init__(self, extractor, service, log_file, local_path):
        super().__init__()
        self.extractor = extractor
        self.service = service
        self.log_file = log_file
        self.local_path = local_path
    
    def run(self):
        try:
            success = self.extractor.download_log(self.service, self.log_file, self.local_path)
            self.result.emit(success, self.local_path, "下载成功" if success else "下载失败")
        except Exception as e:
            self.result.emit(False, "", f"下载异常: {str(e)}")

class ExtractSQLThread(QThread):
    result = pyqtSignal(list, str)
    
    def __init__(self, extractor, log_content, start_time, end_time):
        super().__init__()
        self.extractor = extractor
        self.log_content = log_content
        self.start_time = start_time
        self.end_time = end_time
    
    def run(self):
        try:
            # 修复时间过滤逻辑，确保能正确提取指定时间范围内的SQL
            # 问题：用户设置的时间范围是14:33-14:35，但提取到的SQL时间都是11:25:28
            # 修复：将skip_time_filter设为False，正确使用时间过滤
            sql_statements = self.extractor.extract_sql_from_log(
                self.log_content, 
                self.start_time, 
                self.end_time, 
                skip_time_filter=False
            )
            if sql_statements:
                self.result.emit(sql_statements, "提取成功")
            else:
                self.result.emit([], "未提取到SQL语句")
        except Exception as e:
            self.result.emit([], f"提取异常: {str(e)}")

class GenerateReportThread(QThread):
    result = pyqtSignal(bool, str)
    
    def __init__(self, extractor, data, report_type, output_path):
        super().__init__()
        self.extractor = extractor
        self.data = data
        self.report_type = report_type
        self.output_path = output_path
    
    def run(self):
        try:
            self.extractor.generate_md_report(self.data, self.report_type, self.output_path)
            self.result.emit(True, "报告生成成功")
        except Exception as e:
            self.result.emit(False, f"报告生成异常: {str(e)}")

class LogsToolGUI(QMainWindow):
    def __init__(self):
        super().__init__()
        self.initUI()
        self.extractor = None
        self.temp_dir = os.path.join(os.getcwd(), 'temp_logs')
        os.makedirs(self.temp_dir, exist_ok=True)
    
    def initUI(self):
        self.setWindowTitle('日志提取工具 - GUI')
        self.setGeometry(100, 100, 1200, 800)
        
        # 创建中心部件
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        
        # 主布局
        main_layout = QVBoxLayout(central_widget)
        
        # 创建标签页
        self.tab_widget = QTabWidget()
        
        # 连接配置标签
        self.connect_tab = self.create_connect_tab()
        self.tab_widget.addTab(self.connect_tab, "连接配置")
        
        # 日志浏览标签
        self.browse_tab = self.create_browse_tab()
        self.tab_widget.addTab(self.browse_tab, "日志浏览")
        
        # SQL提取标签
        self.sql_tab = self.create_sql_tab()
        self.tab_widget.addTab(self.sql_tab, "SQL提取")
        
        # 自定义提取标签
        self.custom_tab = self.create_custom_tab()
        self.tab_widget.addTab(self.custom_tab, "自定义提取")
        
        # 报告管理标签
        self.report_tab = self.create_report_tab()
        self.tab_widget.addTab(self.report_tab, "报告管理")
        
        main_layout.addWidget(self.tab_widget)
        
        # 状态栏
        self.statusBar().showMessage("未连接到服务器")
    
    def create_connect_tab(self):
        """创建连接配置标签"""
        tab = QWidget()
        layout = QVBoxLayout(tab)
        
        # 连接配置组
        connect_group = QGroupBox("服务器连接配置")
        connect_layout = QFormLayout(connect_group)
        
        # 服务器信息
        self.host_edit = QLineEdit()
        self.host_edit.setPlaceholderText("请输入服务器地址")
        self.port_spin = QSpinBox()
        self.port_spin.setRange(1, 65535)
        self.port_spin.setValue(22)
        self.username_edit = QLineEdit("root")
        
        # 认证方式
        auth_group = QGroupBox("认证方式")
        auth_layout = QHBoxLayout(auth_group)
        self.auth_radio_pwd = QRadioButton("密码认证")
        self.auth_radio_key = QRadioButton("密钥认证")
        self.auth_radio_pwd.setChecked(True)
        auth_btn_group = QButtonGroup()
        auth_btn_group.addButton(self.auth_radio_pwd)
        auth_btn_group.addButton(self.auth_radio_key)
        auth_layout.addWidget(self.auth_radio_pwd)
        auth_layout.addWidget(self.auth_radio_key)
        auth_layout.addStretch()
        
        # 认证信息
        self.password_edit = QLineEdit()
        self.password_edit.setEchoMode(QLineEdit.Password)
        self.key_edit = QLineEdit()
        self.key_edit.setPlaceholderText("请输入密钥文件路径")
        self.key_btn = QPushButton("浏览...")
        self.key_btn.clicked.connect(self.browse_key_file)
        self.key_edit.setEnabled(False)
        self.key_btn.setEnabled(False)
        
        # 认证方式切换
        self.auth_radio_pwd.toggled.connect(self.toggle_auth_mode)
        self.auth_radio_key.toggled.connect(self.toggle_auth_mode)
        
        # 连接按钮
        self.connect_btn = QPushButton("连接服务器")
        self.connect_btn.clicked.connect(self.connect_server)
        self.disconnect_btn = QPushButton("断开连接")
        self.disconnect_btn.clicked.connect(self.disconnect_server)
        self.disconnect_btn.setEnabled(False)
        
        # 添加到布局
        connect_layout.addRow("服务器地址:", self.host_edit)
        connect_layout.addRow("端口:", self.port_spin)
        connect_layout.addRow("用户名:", self.username_edit)
        connect_layout.addRow(auth_group)
        connect_layout.addRow("密码:", self.password_edit)
        key_layout = QHBoxLayout()
        key_layout.addWidget(self.key_edit)
        key_layout.addWidget(self.key_btn)
        connect_layout.addRow("密钥文件:", key_layout)
        
        btn_layout = QHBoxLayout()
        btn_layout.addWidget(self.connect_btn)
        btn_layout.addWidget(self.disconnect_btn)
        connect_layout.addRow(btn_layout)
        
        layout.addWidget(connect_group)
        return tab
    
    def create_browse_tab(self):
        """创建日志浏览标签"""
        tab = QWidget()
        layout = QVBoxLayout(tab)
        
        # 服务和日志浏览
        browse_layout = QHBoxLayout()
        
        # 服务列表
        service_group = QGroupBox("服务列表")
        service_layout = QVBoxLayout(service_group)
        self.service_tree = QTreeWidget()
        self.service_tree.setHeaderLabels(["服务名称"])
        self.service_tree.setContextMenuPolicy(Qt.CustomContextMenu)
        self.service_tree.customContextMenuRequested.connect(self.show_service_context_menu)
        # 添加点击服务名称自动显示日志文件功能
        self.service_tree.itemClicked.connect(lambda item, column: self.view_service_logs(item.text(0)))
        service_layout.addWidget(self.service_tree)
        
        # 日志文件列表
        log_group = QGroupBox("日志文件")
        log_layout = QVBoxLayout(log_group)
        self.log_table = QTableWidget(0, 4)
        self.log_table.setHorizontalHeaderLabels(["文件名", "大小(KB)", "修改时间", "操作"])
        self.log_table.horizontalHeader().setStretchLastSection(True)
        log_layout.addWidget(self.log_table)
        
        browse_layout.addWidget(service_group, 1)
        browse_layout.addWidget(log_group, 2)
        
        # 刷新按钮
        refresh_btn = QPushButton("刷新服务列表")
        refresh_btn.clicked.connect(self.refresh_services)
        
        layout.addWidget(refresh_btn)
        layout.addLayout(browse_layout)
        return tab
    
    def create_sql_tab(self):
        """创建SQL提取标签"""
        tab = QWidget()
        layout = QVBoxLayout(tab)
        
        # 日志选择
        log_select_group = QGroupBox("日志选择")
        log_select_layout = QFormLayout(log_select_group)
        
        self.sql_service_combo = QComboBox()
        self.sql_log_combo = QComboBox()
        self.sql_refresh_btn = QPushButton("刷新日志列表")
        self.sql_refresh_btn.clicked.connect(self.refresh_sql_logs)
        # 添加服务选择变化信号连接
        self.sql_service_combo.currentIndexChanged.connect(self.refresh_sql_logs)
        
        sql_log_layout = QHBoxLayout()
        sql_log_layout.addWidget(self.sql_log_combo)
        sql_log_layout.addWidget(self.sql_refresh_btn)
        
        log_select_layout.addRow("服务:", self.sql_service_combo)
        log_select_layout.addRow("日志文件:", sql_log_layout)
        
        # 时间范围
        time_group = QGroupBox("时间范围")
        time_layout = QHBoxLayout(time_group)
        
        self.start_datetime = QDateTimeEdit(QDateTime.currentDateTime().addDays(-1))
        self.start_datetime.setCalendarPopup(True)
        self.end_datetime = QDateTimeEdit(QDateTime.currentDateTime())
        self.end_datetime.setCalendarPopup(True)
        
        time_layout.addWidget(QLabel("开始时间:"))
        time_layout.addWidget(self.start_datetime)
        time_layout.addWidget(QLabel("结束时间:"))
        time_layout.addWidget(self.end_datetime)
        time_layout.addStretch()
        
        # 提取按钮
        extract_btn = QPushButton("提取SQL语句")
        extract_btn.clicked.connect(self.extract_sql)
        
        # SQL结果
        sql_result_group = QGroupBox("SQL提取结果")
        sql_result_layout = QVBoxLayout(sql_result_group)
        
        self.sql_result_table = QTableWidget(0, 4)
        self.sql_result_table.setHorizontalHeaderLabels(["时间", "原始SQL", "处理后SQL", "参数"])
        self.sql_result_table.horizontalHeader().setStretchLastSection(True)
        sql_result_layout.addWidget(self.sql_result_table)
        
        # 数据库连接配置
        db_group = QGroupBox("数据库连接配置")
        db_layout = QFormLayout(db_group)
        self.db_host_edit = QLineEdit("192.168.2.172")
        self.db_port_spin = QSpinBox()
        self.db_port_spin.setRange(1, 65535)
        self.db_port_spin.setValue(5432)
        self.db_name_edit = QLineEdit("micgenerp")
        self.db_user_edit = QLineEdit("postgres")
        self.db_password_edit = QLineEdit("postgres")
        self.db_password_edit.setEchoMode(QLineEdit.Password)
        
        db_layout.addRow("数据库地址:", self.db_host_edit)
        db_layout.addRow("端口:", self.db_port_spin)
        db_layout.addRow("数据库名:", self.db_name_edit)
        db_layout.addRow("用户名:", self.db_user_edit)
        db_layout.addRow("密码:", self.db_password_edit)
        
        # 执行按钮
        execute_btn = QPushButton("执行SQL语句")
        execute_btn.clicked.connect(self.execute_sql_statements)
        
        # 生成报告
        report_btn = QPushButton("生成SQL报告")
        report_btn.clicked.connect(self.generate_sql_report)
        
        layout.addWidget(log_select_group)
        layout.addWidget(time_group)
        layout.addWidget(db_group)
        layout.addWidget(extract_btn)
        layout.addWidget(execute_btn)
        layout.addWidget(sql_result_group)
        layout.addWidget(report_btn)
        return tab
    
    def create_custom_tab(self):
        """创建自定义提取标签"""
        tab = QWidget()
        layout = QVBoxLayout(tab)
        
        # 日志选择
        custom_log_group = QGroupBox("日志选择")
        custom_log_layout = QFormLayout(custom_log_group)
        
        self.custom_service_combo = QComboBox()
        self.custom_log_combo = QComboBox()
        self.custom_refresh_btn = QPushButton("刷新日志列表")
        self.custom_refresh_btn.clicked.connect(self.refresh_custom_logs)
        # 添加服务选择变化信号连接
        self.custom_service_combo.currentIndexChanged.connect(self.refresh_custom_logs)
        
        custom_log_combo_layout = QHBoxLayout()
        custom_log_combo_layout.addWidget(self.custom_log_combo)
        custom_log_combo_layout.addWidget(self.custom_refresh_btn)
        
        custom_log_layout.addRow("服务:", self.custom_service_combo)
        custom_log_layout.addRow("日志文件:", custom_log_combo_layout)
        
        # 提取配置
        extract_config_group = QGroupBox("提取配置")
        extract_config_layout = QFormLayout(extract_config_group)
        
        self.custom_pattern_edit = QLineEdit()
        self.custom_pattern_edit.setPlaceholderText("请输入正则表达式")
        
        case_layout = QHBoxLayout()
        self.case_sensitive_radio = QRadioButton("区分大小写")
        self.case_insensitive_radio = QRadioButton("不区分大小写")
        self.case_insensitive_radio.setChecked(True)
        case_layout.addWidget(self.case_sensitive_radio)
        case_layout.addWidget(self.case_insensitive_radio)
        case_layout.addStretch()
        
        extract_config_layout.addRow("正则表达式:", self.custom_pattern_edit)
        extract_config_layout.addRow(case_layout)
        
        # 提取按钮
        custom_extract_btn = QPushButton("执行自定义提取")
        custom_extract_btn.clicked.connect(self.custom_extract)
        
        # 提取结果
        custom_result_group = QGroupBox("提取结果")
        custom_result_layout = QVBoxLayout(custom_result_group)
        
        self.custom_result_table = QTableWidget(0, 2)
        self.custom_result_table.setHorizontalHeaderLabels(["匹配内容", "捕获组"])
        self.custom_result_table.horizontalHeader().setStretchLastSection(True)
        custom_result_layout.addWidget(self.custom_result_table)
        
        # 生成报告
        custom_report_btn = QPushButton("生成自定义报告")
        custom_report_btn.clicked.connect(self.generate_custom_report)
        
        layout.addWidget(custom_log_group)
        layout.addWidget(extract_config_group)
        layout.addWidget(custom_extract_btn)
        layout.addWidget(custom_result_group)
        layout.addWidget(custom_report_btn)
        return tab
    
    def create_report_tab(self):
        """创建报告管理标签"""
        tab = QWidget()
        layout = QVBoxLayout(tab)
        
        # 报告列表
        report_list_group = QGroupBox("报告列表")
        report_list_layout = QVBoxLayout(report_list_group)
        
        self.report_tree = QTreeWidget()
        self.report_tree.setHeaderLabels(["报告名称", "类型", "创建时间"])
        self.report_tree.setContextMenuPolicy(Qt.CustomContextMenu)
        self.report_tree.customContextMenuRequested.connect(self.show_report_context_menu)
        report_list_layout.addWidget(self.report_tree)
        
        # 刷新报告列表
        refresh_reports_btn = QPushButton("刷新报告列表")
        refresh_reports_btn.clicked.connect(self.refresh_report_list)
        
        # 报告内容
        report_content_group = QGroupBox("报告内容")
        report_content_layout = QVBoxLayout(report_content_group)
        
        self.report_content = QTextEdit()
        self.report_content.setReadOnly(True)
        report_content_layout.addWidget(self.report_content)
        
        layout.addWidget(refresh_reports_btn)
        layout.addWidget(report_list_group)
        layout.addWidget(report_content_group)
        return tab
    
    def browse_key_file(self):
        """浏览密钥文件"""
        file_path, _ = QFileDialog.getOpenFileName(self, "选择密钥文件", ".", "All Files (*);;PEM Files (*.pem)")
        if file_path:
            self.key_edit.setText(file_path)
    
    def toggle_auth_mode(self):
        """切换认证模式"""
        if self.auth_radio_pwd.isChecked():
            self.password_edit.setEnabled(True)
            self.key_edit.setEnabled(False)
            self.key_btn.setEnabled(False)
        else:
            self.password_edit.setEnabled(False)
            self.key_edit.setEnabled(True)
            self.key_btn.setEnabled(True)
    
    def connect_server(self):
        """连接服务器"""
        host = self.host_edit.text().strip()
        port = self.port_spin.value()
        username = self.username_edit.text().strip()
        
        if not host:
            QMessageBox.warning(self, "警告", "请输入服务器地址")
            return
        
        password = self.password_edit.text() if self.auth_radio_pwd.isChecked() else None
        key_filename = self.key_edit.text() if self.auth_radio_key.isChecked() else None
        
        # 使用线程连接服务器
        self.connect_btn.setEnabled(False)
        self.statusBar().showMessage("正在连接服务器...")
        
        self.connect_thread = ConnectThread(host, port, username, password, key_filename)
        self.connect_thread.result.connect(self.on_connect_result)
        self.connect_thread.start()
    
    def on_connect_result(self, success, message):
        """连接结果处理"""
        if success:
            self.extractor = self.connect_thread.extractor
            self.statusBar().showMessage("已连接到服务器")
            QMessageBox.information(self, "成功", "服务器连接成功")
            self.connect_btn.setEnabled(False)
            self.disconnect_btn.setEnabled(True)
            # 刷新服务列表
            self.refresh_services()
            self.refresh_sql_services()
            self.refresh_custom_services()
        else:
            self.statusBar().showMessage("连接失败")
            QMessageBox.critical(self, "错误", message)
            self.connect_btn.setEnabled(True)
    
    def disconnect_server(self):
        """断开服务器连接"""
        if self.extractor:
            self.extractor.disconnect()
            self.extractor = None
        self.statusBar().showMessage("未连接到服务器")
        self.connect_btn.setEnabled(True)
        self.disconnect_btn.setEnabled(False)
        # 清空服务和日志列表
        self.service_tree.clear()
        self.log_table.setRowCount(0)
        self.sql_service_combo.clear()
        self.sql_log_combo.clear()
        self.custom_service_combo.clear()
        self.custom_log_combo.clear()
        QMessageBox.information(self, "成功", "已断开服务器连接")
    
    def refresh_services(self):
        """刷新服务列表"""
        if not self.extractor:
            QMessageBox.warning(self, "警告", "请先连接到服务器")
            return
        
        try:
            self.service_tree.clear()
            services = self.extractor.list_erp_services()
            for service in services:
                QTreeWidgetItem(self.service_tree, [service])
            QMessageBox.information(self, "成功", f"共找到 {len(services)} 个服务")
        except Exception as e:
            QMessageBox.critical(self, "错误", f"刷新服务列表失败: {str(e)}")
    
    def show_service_context_menu(self, position):
        """显示服务上下文菜单"""
        item = self.service_tree.itemAt(position)
        if item:
            menu = QMenu()
            view_logs_action = QAction("查看日志文件", self)
            view_logs_action.triggered.connect(lambda: self.view_service_logs(item.text(0)))
            menu.addAction(view_logs_action)
            menu.exec_(self.service_tree.viewport().mapToGlobal(position))
    
    def view_service_logs(self, service_name):
        """查看服务日志文件"""
        if not self.extractor:
            QMessageBox.warning(self, "警告", "请先连接到服务器")
            return
        
        try:
            logs = self.extractor.list_service_logs(service_name)
            self.log_table.setRowCount(0)
            for log in logs:
                row = self.log_table.rowCount()
                self.log_table.insertRow(row)
                self.log_table.setItem(row, 0, QTableWidgetItem(log['name']))
                self.log_table.setItem(row, 1, QTableWidgetItem(f"{round(log['size'] / 1024, 2)}"))
                self.log_table.setItem(row, 2, QTableWidgetItem(log['mtime'].strftime('%Y-%m-%d %H:%M:%S')))
                
                # 下载按钮
                download_btn = QPushButton("下载")
                download_btn.clicked.connect(lambda checked, s=service_name, l=log['name']: self.download_log(s, l))
                self.log_table.setCellWidget(row, 3, download_btn)
        except Exception as e:
            QMessageBox.critical(self, "错误", f"获取日志列表失败: {str(e)}")
    
    def download_log(self, service_name, log_filename):
        """下载日志文件"""
        if not self.extractor:
            QMessageBox.warning(self, "警告", "请先连接到服务器")
            return
        
        save_path, _ = QFileDialog.getSaveFileName(
            self, "保存日志文件", log_filename, "Log Files (*.log);;All Files (*)"
        )
        if not save_path:
            return
        
        # 使用线程下载日志
        self.statusBar().showMessage(f"正在下载日志: {log_filename}")
        
        self.download_thread = DownloadLogThread(self.extractor, service_name, log_filename, save_path)
        self.download_thread.result.connect(self.on_download_result)
        self.download_thread.start()
    
    def on_download_result(self, success, local_path, message):
        """下载结果处理"""
        if success:
            self.statusBar().showMessage("下载完成")
            QMessageBox.information(self, "成功", f"日志文件已保存到: {local_path}")
        else:
            self.statusBar().showMessage("下载失败")
            QMessageBox.critical(self, "错误", message)
    
    def refresh_sql_services(self):
        """刷新SQL提取的服务列表"""
        if not self.extractor:
            return
        
        try:
            self.sql_service_combo.clear()
            services = self.extractor.list_erp_services()
            self.sql_service_combo.addItems(services)
        except Exception:
            pass
    
    def refresh_sql_logs(self):
        """刷新SQL提取的日志列表"""
        if not self.extractor:
            QMessageBox.warning(self, "警告", "请先连接到服务器")
            return
        
        service = self.sql_service_combo.currentText()
        if not service:
            QMessageBox.warning(self, "警告", "请先选择服务")
            return
        
        try:
            logs = self.extractor.list_service_logs(service)
            self.sql_log_combo.clear()
            for log in logs:
                self.sql_log_combo.addItem(log['name'])
        except Exception as e:
            QMessageBox.critical(self, "错误", f"刷新日志列表失败: {str(e)}")
    
    def extract_sql(self):
        """提取SQL语句"""
        if not self.extractor:
            QMessageBox.warning(self, "警告", "请先连接到服务器")
            return
        
        service = self.sql_service_combo.currentText()
        log_file = self.sql_log_combo.currentText()
        if not service or not log_file:
            QMessageBox.warning(self, "警告", "请选择服务和日志文件")
            return
        
        start_time = self.start_datetime.dateTime().toPyDateTime()
        end_time = self.end_datetime.dateTime().toPyDateTime()
        
        # 显示详细的提取信息
        QMessageBox.information(self, "提示", f"开始提取SQL\n服务: {service}\n日志文件: {log_file}\n时间范围: {start_time} 到 {end_time}")
        
        # 下载日志文件到本地临时目录
        local_log_path = os.path.join(self.temp_dir, log_file)
        print(f"下载日志文件到: {local_log_path}")
        
        try:
            # 确保临时目录存在
            os.makedirs(self.temp_dir, exist_ok=True)
            
            # 下载日志文件
            if not self.extractor.download_log(service, log_file, local_log_path):
                QMessageBox.critical(self, "错误", "下载日志文件失败")
                return
            
            # 验证文件是否下载成功
            if not os.path.exists(local_log_path):
                QMessageBox.critical(self, "错误", "日志文件下载失败，文件不存在")
                return
            
            # 读取日志内容
            print(f"读取日志文件: {local_log_path}")
            with open(local_log_path, 'r', encoding='utf-8', errors='ignore') as f:
                log_content = f.read()
            
            print(f"日志内容长度: {len(log_content)} 字符")
            
            # 使用线程提取SQL
            self.statusBar().showMessage("正在提取SQL语句...")
            self.extract_sql_thread = ExtractSQLThread(self.extractor, log_content, start_time, end_time)
            self.extract_sql_thread.result.connect(self.on_sql_extract_result)
            self.extract_sql_thread.start()
        except Exception as e:
            print(f"提取SQL过程中出错: {e}")
            QMessageBox.critical(self, "错误", f"提取SQL失败: {str(e)}")
    
    def on_sql_extract_result(self, sql_statements, message):
        """SQL提取结果处理"""
        self.statusBar().showMessage("SQL提取完成")
        
        # 添加调试信息
        print(f"SQL提取结果: {len(sql_statements)} 条")
        print(f"消息: {message}")
        
        if sql_statements:
            # 确保表格可见且有足够的行数
            QMessageBox.information(self, "成功", f"共提取到 {len(sql_statements)} 条SQL语句")
            # 显示提取结果
            self.sql_result_table.setRowCount(0)
            for sql_info in sql_statements:
                row = self.sql_result_table.rowCount()
                self.sql_result_table.insertRow(row)
                # 设置表格项
                time_item = QTableWidgetItem(sql_info['time'].strftime('%Y-%m-%d %H:%M:%S'))
                original_sql_item = QTableWidgetItem(sql_info['original_sql'])
                processed_sql_item = QTableWidgetItem(sql_info['processed_sql'])
                params_item = QTableWidgetItem(sql_info['params'])
                
                # 添加到表格
                self.sql_result_table.setItem(row, 0, time_item)
                self.sql_result_table.setItem(row, 1, original_sql_item)
                self.sql_result_table.setItem(row, 2, processed_sql_item)
                self.sql_result_table.setItem(row, 3, params_item)
            # 调整列宽
            self.sql_result_table.resizeColumnsToContents()
            # 保存到实例变量，用于生成报告
            self.current_sql_results = sql_statements
        else:
            QMessageBox.warning(self, "警告", message)
    
    def execute_sql_statements(self):
        """执行SQL语句"""
        if not hasattr(self, 'current_sql_results') or not self.current_sql_results:
            QMessageBox.warning(self, "警告", "请先提取SQL语句")
            return
        
        # 获取数据库连接参数
        db_host = self.db_host_edit.text().strip()
        db_port = self.db_port_spin.value()
        db_name = self.db_name_edit.text().strip()
        db_user = self.db_user_edit.text().strip()
        db_password = self.db_password_edit.text().strip()
        
        if not db_host or not db_name or not db_user:
            QMessageBox.warning(self, "警告", "请填写完整的数据库连接信息")
            return
        
        # 导入psycopg2
        try:
            import psycopg2
        except ImportError:
            QMessageBox.critical(self, "错误", "未安装psycopg2库，请先安装")
            return
        
        # 连接数据库
        conn = None
        cursor = None
        try:
            conn = psycopg2.connect(
                host=db_host,
                port=db_port,
                database=db_name,
                user=db_user,
                password=db_password
            )
            cursor = conn.cursor()
            QMessageBox.information(self, "成功", "数据库连接成功")
            
            # 执行SQL语句
            self.statusBar().showMessage(f"正在执行SQL语句，共 {len(self.current_sql_results)} 条")
            
            # 执行每条SQL
            for i, sql_info in enumerate(self.current_sql_results):
                self.statusBar().showMessage(f"正在执行第 {i+1}/{len(self.current_sql_results)} 条SQL")
                
                # 执行SQL
                try:
                    cursor.execute(sql_info['processed_sql'])
                    conn.commit()
                    
                    # 获取执行结果
                    execution_result = {
                        'success': True,
                        'columns': [],
                        'rows': [],
                        'row_count': 0,
                        'error': None
                    }
                    
                    # 检查是否有结果集
                    if cursor.description:
                        # 有结果集的查询
                        columns = [desc[0] for desc in cursor.description]
                        rows = cursor.fetchall()
                        row_count = len(rows)
                        execution_result['columns'] = columns
                        execution_result['rows'] = rows
                        execution_result['row_count'] = row_count
                    else:
                        # 无结果集的执行（如UPDATE、DELETE等）
                        execution_result['row_count'] = cursor.rowcount
                    
                    # 添加执行结果到SQL信息中
                    sql_info['execution'] = execution_result
                    
                except Exception as e:
                    conn.rollback()
                    # 添加错误信息到SQL信息中
                    sql_info['execution'] = {
                        'success': False,
                        'columns': [],
                        'rows': [],
                        'row_count': 0,
                        'error': str(e)
                    }
            
            QMessageBox.information(self, "成功", f"SQL执行完成，共执行 {len(self.current_sql_results)} 条")
            
        except Exception as e:
            QMessageBox.critical(self, "错误", f"执行SQL失败: {str(e)}")
        finally:
            # 关闭数据库连接
            if cursor:
                cursor.close()
            if conn:
                conn.close()
    
    def generate_sql_report(self):
        """生成SQL报告"""
        if not hasattr(self, 'current_sql_results') or not self.current_sql_results:
            QMessageBox.warning(self, "警告", "请先提取SQL语句")
            return
        
        # 选择保存路径
        save_path, _ = QFileDialog.getSaveFileName(
            self, "保存SQL报告", f"sql_report_{datetime.datetime.now().strftime('%Y%m%d_%H%M%S')}.md", "Markdown Files (*.md)"
        )
        if not save_path:
            return
        
        # 使用线程生成报告
        self.statusBar().showMessage("正在生成SQL报告...")
        self.generate_report_thread = GenerateReportThread(
            self.extractor, self.current_sql_results, 'sql', save_path
        )
        self.generate_report_thread.result.connect(self.on_report_generate_result)
        self.generate_report_thread.start()
    
    def refresh_custom_services(self):
        """刷新自定义提取的服务列表"""
        if not self.extractor:
            return
        
        try:
            self.custom_service_combo.clear()
            services = self.extractor.list_erp_services()
            self.custom_service_combo.addItems(services)
        except Exception:
            pass
    
    def refresh_custom_logs(self):
        """刷新自定义提取的日志列表"""
        if not self.extractor:
            QMessageBox.warning(self, "警告", "请先连接到服务器")
            return
        
        service = self.custom_service_combo.currentText()
        if not service:
            QMessageBox.warning(self, "警告", "请先选择服务")
            return
        
        try:
            logs = self.extractor.list_service_logs(service)
            self.custom_log_combo.clear()
            for log in logs:
                self.custom_log_combo.addItem(log['name'])
        except Exception as e:
            QMessageBox.critical(self, "错误", f"刷新日志列表失败: {str(e)}")
    
    def custom_extract(self):
        """执行自定义提取"""
        if not self.extractor:
            QMessageBox.warning(self, "警告", "请先连接到服务器")
            return
        
        service = self.custom_service_combo.currentText()
        log_file = self.custom_log_combo.currentText()
        pattern = self.custom_pattern_edit.text().strip()
        
        if not service or not log_file:
            QMessageBox.warning(self, "警告", "请选择服务和日志文件")
            return
        
        if not pattern:
            QMessageBox.warning(self, "警告", "请输入正则表达式")
            return
        
        case_insensitive = self.case_insensitive_radio.isChecked()
        
        # 下载日志文件到本地临时目录
        local_log_path = os.path.join(self.temp_dir, log_file)
        try:
            if not self.extractor.download_log(service, log_file, local_log_path):
                QMessageBox.critical(self, "错误", "下载日志文件失败")
                return
            
            # 读取日志内容
            with open(local_log_path, 'r', encoding='utf-8', errors='ignore') as f:
                log_content = f.read()
            
            # 执行提取
            matches = self.extractor.extract_custom(log_content, pattern, case_insensitive=case_insensitive)
            
            # 显示提取结果
            self.custom_result_table.setRowCount(0)
            for match in matches:
                row = self.custom_result_table.rowCount()
                self.custom_result_table.insertRow(row)
                self.custom_result_table.setItem(row, 0, QTableWidgetItem(match['content']))
                groups_str = str(match['groups']) if match['groups'] else ""
                self.custom_result_table.setItem(row, 1, QTableWidgetItem(groups_str))
            
            QMessageBox.information(self, "成功", f"共提取到 {len(matches)} 条匹配信息")
            # 保存到实例变量，用于生成报告
            self.current_custom_results = matches
        except Exception as e:
            QMessageBox.critical(self, "错误", f"自定义提取失败: {str(e)}")
    
    def generate_custom_report(self):
        """生成自定义报告"""
        if not hasattr(self, 'current_custom_results') or not self.current_custom_results:
            QMessageBox.warning(self, "警告", "请先执行自定义提取")
            return
        
        # 选择保存路径
        save_path, _ = QFileDialog.getSaveFileName(
            self, "保存自定义报告", f"custom_report_{datetime.datetime.now().strftime('%Y%m%d_%H%M%S')}.md", "Markdown Files (*.md)"
        )
        if not save_path:
            return
        
        # 使用线程生成报告
        self.statusBar().showMessage("正在生成自定义报告...")
        self.generate_report_thread = GenerateReportThread(
            self.extractor, self.current_custom_results, 'custom', save_path
        )
        self.generate_report_thread.result.connect(self.on_report_generate_result)
        self.generate_report_thread.start()
    
    def on_report_generate_result(self, success, message):
        """报告生成结果处理"""
        self.statusBar().showMessage("报告生成完成")
        
        if success:
            QMessageBox.information(self, "成功", message)
            self.refresh_report_list()
        else:
            QMessageBox.critical(self, "错误", message)
    
    def refresh_report_list(self):
        """刷新报告列表"""
        try:
            self.report_tree.clear()
            # 遍历当前目录，查找所有md文件
            for file in os.listdir('.'):
                if file.endswith('.md') and file != 'README.md':
                    # 尝试确定报告类型
                    report_type = "未知"
                    if 'sql' in file.lower():
                        report_type = "SQL"
                    elif 'custom' in file.lower():
                        report_type = "自定义"
                    elif 'error' in file.lower():
                        report_type = "错误"
                    elif 'exception' in file.lower():
                        report_type = "异常"
                    
                    # 获取文件创建时间
                    mtime = datetime.datetime.fromtimestamp(os.path.getmtime(file))
                    
                    # 添加到树控件
                    QTreeWidgetItem(self.report_tree, [file, report_type, mtime.strftime('%Y-%m-%d %H:%M:%S')])
        except Exception as e:
            QMessageBox.critical(self, "错误", f"刷新报告列表失败: {str(e)}")
    
    def show_report_context_menu(self, position):
        """显示报告上下文菜单"""
        item = self.report_tree.itemAt(position)
        if item:
            menu = QMenu()
            view_action = QAction("查看报告", self)
            view_action.triggered.connect(lambda: self.view_report(item.text(0)))
            delete_action = QAction("删除报告", self)
            delete_action.triggered.connect(lambda: self.delete_report(item.text(0)))
            menu.addAction(view_action)
            menu.addAction(delete_action)
            menu.exec_(self.report_tree.viewport().mapToGlobal(position))
    
    def view_report(self, report_name):
        """查看报告内容"""
        try:
            with open(report_name, 'r', encoding='utf-8') as f:
                content = f.read()
            self.report_content.setPlainText(content)
        except Exception as e:
            QMessageBox.critical(self, "错误", f"查看报告失败: {str(e)}")
    
    def delete_report(self, report_name):
        """删除报告"""
        reply = QMessageBox.question(
            self, "确认删除", f"确定要删除报告 '{report_name}' 吗？",
            QMessageBox.Yes | QMessageBox.No, QMessageBox.No
        )
        if reply == QMessageBox.Yes:
            try:
                os.remove(report_name)
                QMessageBox.information(self, "成功", f"报告 '{report_name}' 已删除")
                self.refresh_report_list()
                if self.report_content.toPlainText():
                    self.report_content.clear()
            except Exception as e:
                QMessageBox.critical(self, "错误", f"删除报告失败: {str(e)}")
    
    def closeEvent(self, event):
        """关闭窗口时的清理工作"""
        # 断开服务器连接
        if self.extractor:
            self.extractor.disconnect()
        # 清理临时目录
        if os.path.exists(self.temp_dir):
            try:
                for file in os.listdir(self.temp_dir):
                    file_path = os.path.join(self.temp_dir, file)
                    if os.path.isfile(file_path):
                        os.remove(file_path)
                os.rmdir(self.temp_dir)
            except Exception:
                pass
        event.accept()

if __name__ == '__main__':
    app = QApplication(sys.argv)
    window = LogsToolGUI()
    window.show()
    sys.exit(app.exec_())
