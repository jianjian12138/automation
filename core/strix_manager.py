"""
Strix 安全集成管理
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence

import yaml

from libs.config_center import ENV, LOG
from libs.strix_runner import StrixResult, StrixRunner


@dataclass
class SecurityScanResult:
    executed: bool
    success: bool
    report_path: Optional[Path] = None
    message: str = ""
    issues: Optional[List[Dict[str, str]]] = None


class StrixManager:
    def __init__(self, config: Optional[Dict[str, any]] = None) -> None:
        env_section = ENV.get("STRIX_SECURITY", {}) if ENV else {}
        self.config = config or env_section
        self.enabled = bool(self.config.get("enabled", False))
        self.cli_path = self.config.get("cli_path", "strix")
        self.llm = self.config.get("llm")
        self.report_dir = self.config.get("report_dir", "reports/security")
        self.default_targets = self.config.get("default_targets", [])
        self.extra_args = self.config.get("extra_args", [])

    def execute(
        self,
        *,
        targets: Optional[Sequence[str]] = None,
        instructions: Optional[str] = None,
        force: bool = False,
        timeout: Optional[int] = None,
        security_only: bool = False,
    ) -> SecurityScanResult:
        if not force and not self.enabled:
            LOG.info("Strix 安全扫描未启用，跳过")
            return SecurityScanResult(executed=False, success=True, message="Strix disabled")

        runner = StrixRunner(
            cli_path=self.cli_path,
            report_dir=self.report_dir,
            env={"STRIX_LLM": self.llm} if self.llm else None,
        )
        if not runner.available():
            message = "Strix CLI 不可用，跳过安全扫描"
            LOG.warning(message)
            if security_only:
                return SecurityScanResult(False, False, message=message)
            return SecurityScanResult(False, True, message=message)

        resolved_targets = list(targets or self.default_targets or [])
        if not resolved_targets:
            message = "未找到任何 Strix 扫描目标"
            LOG.warning(message)
            if security_only:
                return SecurityScanResult(False, False, message=message)
            return SecurityScanResult(False, True, message=message)

        result = runner.run_scan(
            resolved_targets,
            instructions=instructions,
            extra_args=self.extra_args,
            timeout=timeout,
        )
        issues = self._extract_issues(result.report_path)
        return SecurityScanResult(
            executed=True,
            success=result.success,
            report_path=result.report_path,
            message=result.message,
            issues=issues,
        )

    def extend_targets_with_case(
        self,
        base_targets: Optional[Iterable[str]],
        test_type: str,
        test_path: str,
    ) -> List[str]:
        """
        从测试用例中提取潜在的 URL/路径，并与已有目标合并去重
        """
        resolved: List[str] = list(base_targets or [])
        case_targets = self._extract_case_targets(test_type, test_path)
        for item in case_targets:
            if item not in resolved:
                resolved.append(item)
        return resolved

    def _extract_case_targets(self, test_type: str, test_path: str) -> List[str]:
        path = Path(test_path)
        if not path.exists():
            return []
        if path.suffix.lower() != ".yaml":
            return []

        try:
            data = yaml.safe_load(path.read_text(encoding="utf-8"))
        except Exception as exc:  # pragma: no cover
            LOG.debug(f"无法解析用例文件以提取安全扫描目标: {exc}")
            return []

        targets: List[str] = []

        def append_if_url(value: Optional[str]) -> None:
            if isinstance(value, str) and value.startswith(("http://", "https://")):
                targets.append(value)

        case_variables = data.get("case_variables", {}) if isinstance(data, dict) else {}
        if isinstance(case_variables, dict):
            append_if_url(case_variables.get("base_url"))
            append_if_url(case_variables.get("url"))

        # 从 steps 中收集可能的 URL（如 navigate 等参数）
        steps = data.get("steps", [])
        if isinstance(steps, list):
            for step in steps:
                if not isinstance(step, dict):
                    continue
                for key, value in step.items():
                    if isinstance(value, str):
                        append_if_url(value)
                    elif isinstance(value, dict):
                        for inner in value.values():
                            if isinstance(inner, str):
                                append_if_url(inner)

        return targets

    @staticmethod
    def _extract_issues(report_path: Optional[Path]) -> Optional[List[Dict[str, str]]]:
        if not report_path:
            return None

        summary_file = report_path / "summary.json" if report_path.is_dir() else None
        if summary_file and summary_file.exists():
            try:
                data = json.loads(summary_file.read_text(encoding="utf-8"))
                issues = data.get("issues")
                if isinstance(issues, list):
                    return issues
            except Exception as exc:  # pragma: no cover
                LOG.debug(f"解析 Strix summary 失败: {exc}")
        return None

