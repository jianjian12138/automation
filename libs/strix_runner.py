"""
Strix Runner - 封装对 Strix CLI 的调用逻辑
"""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence

from libs.config_center import LOG


@dataclass
class StrixResult:
    success: bool
    exit_code: int
    report_path: Optional[Path]
    message: str = ""
    raw_output: Optional[str] = None


class StrixRunner:
    """
    对 Strix CLI 的轻量封装，负责：
    - 依赖检测
    - 命令拼装
    - 日志/报告落地
    """

    def __init__(
        self,
        cli_path: str = "strix",
        report_dir: str = "reports/security",
        env: Optional[Dict[str, str]] = None,
    ) -> None:
        self.cli_path = cli_path
        self.report_dir = Path(report_dir)
        self.report_dir.mkdir(parents=True, exist_ok=True)
        self.env = os.environ.copy()
        if env:
            # 仅注入非空值，避免污染系统变量
            for key, value in env.items():
                if value is not None:
                    self.env[key] = str(value)

    def available(self) -> bool:
        """检测 Strix CLI 是否可用"""
        exe = shutil.which(self.cli_path)
        if exe:
            LOG.debug(f"Strix CLI detected: {exe}")
            return True
        LOG.warning(f"未检测到 Strix CLI ({self.cli_path})，请安装: pipx install strix-agent")
        return False

    def run_scan(
        self,
        targets: Sequence[str],
        *,
        instructions: Optional[str] = None,
        extra_args: Optional[Iterable[str]] = None,
        non_interactive: bool = True,
        timeout: Optional[int] = None,
    ) -> StrixResult:
        """
        执行 Strix 扫描
        :param targets: 目标列表，可以是 URL 或源码目录
        :param instructions: 附加指令
        :param extra_args: 透传给 CLI 的其他参数
        :param non_interactive: 是否以非交互模式运行
        :param timeout: 可选超时时间（秒）
        """
        if not targets:
            return StrixResult(
                success=False,
                exit_code=1,
                report_path=None,
                message="没有可用的扫描目标",
            )

        cmd = [self.cli_path]
        if non_interactive:
            cmd.append("-n")

        for target in targets:
            cmd.extend(["--target", target])

        if instructions:
            cmd.extend(["--instruction", instructions])

        if extra_args:
            cmd.extend(list(extra_args))

        LOG.info(f"Strix 扫描命令: {' '.join(cmd)}")

        # 记录执行前的 agent_runs 状态，用于定位最新输出
        agent_runs_dir = Path.cwd() / "agent_runs"
        before_runs = set(agent_runs_dir.glob("*")) if agent_runs_dir.exists() else set()

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        stdout_path = self.report_dir / f"strix_stdout_{timestamp}.log"

        try:
            with subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
                env=self.env,
            ) as proc, stdout_path.open("w", encoding="utf-8") as log_fp:
                full_output: List[str] = []
                for line in proc.stdout or []:
                    sys.stdout.write(line)
                    log_fp.write(line)
                    full_output.append(line)

                proc.wait(timeout=timeout)
                exit_code = proc.returncode or 0
        except subprocess.TimeoutExpired:
            return StrixResult(
                success=False,
                exit_code=124,
                report_path=stdout_path,
                message="Strix 扫描超时",
            )
        except FileNotFoundError as exc:
            return StrixResult(
                success=False,
                exit_code=1,
                report_path=None,
                message=f"Strix CLI 未找到: {exc}",
            )

        # 抄存最新的 agent_runs 内容
        new_run_dir = self._collect_latest_run(agent_runs_dir, before_runs, timestamp)

        return StrixResult(
            success=exit_code == 0,
            exit_code=exit_code,
            report_path=new_run_dir or stdout_path,
            message="Strix 扫描完成" if exit_code == 0 else "Strix 扫描失败",
            raw_output=stdout_path.read_text(encoding="utf-8"),
        )

    def _collect_latest_run(
        self,
        agent_runs_dir: Path,
        before: Iterable[Path],
        timestamp: str,
    ) -> Optional[Path]:
        if not agent_runs_dir.exists():
            return None

        new_dirs = [path for path in agent_runs_dir.glob("*") if path not in before]
        if not new_dirs:
            # 未产生新目录，也返回 None
            return None

        # 选择最新修改的目录
        new_dirs.sort(key=lambda p: p.stat().st_mtime, reverse=True)
        latest_run = new_dirs[0]
        dest = self.report_dir / f"strix_run_{timestamp}"

        try:
            if dest.exists():
                shutil.rmtree(dest)
            shutil.copytree(latest_run, dest)

            # 生成简短摘要文件
            summary = self._extract_summary(dest)
            if summary:
                summary_path = dest / "summary.json"
                summary_path.write_text(
                    json.dumps(summary, ensure_ascii=False, indent=2),
                    encoding="utf-8",
                )

            LOG.info(f"Strix 报告已保存: {dest}")
            return dest
        except Exception as exc:  # pragma: no cover
            LOG.warning(f"无法复制 Strix 输出目录: {exc}")
            return None

    @staticmethod
    def _extract_summary(run_dir: Path) -> Optional[Dict[str, str]]:
        """
        尝试从 Strix 默认产物中提取摘要信息。
        目前先扫描 JSON/MD 文件，后续可按官方格式调整。
        """
        findings_dir = run_dir / "findings"
        if not findings_dir.exists():
            return None

        issues = []
        for file in findings_dir.glob("*.json"):
            try:
                data = json.loads(file.read_text(encoding="utf-8"))
            except Exception:
                continue

            severity = data.get("severity") or data.get("cvss", {}).get("label")
            title = data.get("title") or data.get("name")
            if title:
                issues.append({"title": title, "severity": severity or "unknown"})

        return {
            "issues": issues,
            "total_issues": len(issues),
        }

