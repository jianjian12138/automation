# Strix 安全测试集成设计

## 1. 背景与目标

Strix 是一个面向渗透测试场景的开源 AI 安全代理，能够在真实环境中运行应用、发现漏洞，并生成可验证的 PoC 与结构化报告，且支持在 CI/CD 流水线中自动触发扫描 [[Strix](https://github.com/usestrix/strix)]. 为了让测试框架在功能测试之外具备基础的安全巡检能力，本次集成旨在：

- 通过统一入口一键调用 Strix，对指定 Web/API 目标进行安全扫描；
- 将扫描结果与现有测试报告体系协同展示，并保存原始产出；
- 支持在回归/CI 场景按需触发，避免对现有流程造成侵入式改动。

## 2. 高层架构

```
main.py (--security-scan) 
        │
        ▼
core/strix_manager.py  ──► 调用者上下文（用例路径、目标 URL）
        │
        ├─► libs/strix_runner.py         (封装 CLI 调用、依赖检测、参数拼装)
        │
        ├─► reports/security/…          (保存 Strix 原始输出、HTML/JSON)
        │
        └─► core/case_report.py         (安全扫描摘要写入框架报告)
```

关键要素：

1. **独立管理层**：`core/strix_manager.py` 负责解析 CLI 参数、读取配置、决定扫描目标。
2. **Runner 封装**：`libs/strix_runner.py` 屏蔽对 `strix` 命令的直接依赖，统一处理：
   - Strix 安装与 Docker 状态检查；
   - 目标类型映射（源码目录 / URL / 混合）；
   - 日志流式输出与错误处理。
3. **结果落地**：扫描结果写入 `reports/security/<case_code>/`，并回填摘要给框架报告（若启用）。
4. **与功能测试解耦**：安全扫描可以独立执行，或在执行功能用例后追加触发，不影响原有逻辑。

## 3. 配置方案

在 `config/environment.yaml` 新增 `STRIX_SECURITY` 段：

```yaml
STRIX_SECURITY:
  enabled: false              # 默认关闭，按需开启
  cli_path: strix             # 可自定义可执行文件路径
  llm: openai/gpt-5           # 默认 LLM 提供者
  report_dir: reports/security
  default_targets: []         # 为空时需 CLI 显式传入
  extra_args: []              # 透传给 Strix 的附加参数
```

环境变量读取顺序：

1. CLI 参数（最高优先级，例如 `--security-target`, `--security-scan`）；
2. `STRIX_SECURITY` 配置；
3. 系统环境变量（如 `STRIX_LLM`, `LLM_API_KEY` 等，与 Strix CLI 保持一致）。

## 4. 触发方式

| 场景 | 入口 | 行为 |
| ---- | ---- | ---- |
| 独立安全扫描 | `python main.py --security-scan --security-target <url_or_path>` | 仅执行 Strix，退出状态与 Strix CLI 保持一致 |
| 功能测试附带扫描 | `python main.py cases/...yaml --type web --security-scan` | 功能用例执行完成后调用 `StrixManager`，目标默认取用例中的 `base_url` 或配置项 |
| CI/CD | 在现有流水线脚本中调用，或者通过 Jenkins 步骤/自定义命令 | 与本地一致，支持 `--security-only`（进入安全模式，跳过功能测试） |

## 5. Runner 细节

`libs/strix_runner.py` 计划提供以下能力：

- `check_dependencies()`：检测 `strix` 可执行文件、Python 版本、本地 Docker 进程是否可用，缺少依赖时返回提示。
- `build_command(targets: List[str], options: Dict[str, Any])`：组装命令行参数（例如 `-n`、`--instruction`）。
- `run(command)`：使用 `subprocess.Popen` 流式读取输出，实时写入日志文件，完成后返回退出码与生成的报告目录。
- `collect_artifacts()`：整理 Strix 默认输出目录 `agent_runs/<run-name>`，复制到框架 `report_dir`。

错误处理策略：

1. 如果依赖缺失 → 仅记录警告，不阻断功能测试（除非 `--security-only`）。
2. 如果扫描失败（非 0 返回码） → 在框架报告中标记失败，并提示查看详细日志/报告。

## 6. 报告融合

- 在 `reports/security/<case_code>/<timestamp>/` 下保存：
  - `strix_stdout.log`：完整 CLI 输出；
  - `summary.json`：解析后的高层结果（severity、vulns、建议）；
  - Strix 原始目录快照。
- 框架 HTML 报告增加“安全扫描”章节，展示：
  - 扫描状态（成功/失败/跳过）；
  - 发现的漏洞数量及最高严重度；
  - 报告路径链接。
- 若开启自动化通知（比如飞书/钉钉），可在后续迭代将安全摘要一并推送。

## 7. 迭代路线

1. **V1（当前目标）**：完成 CLI 触发与结果落地，确保不会影响现有测试流程。
2. **V2**：解析 Strix 生成的 PoC、漏洞详情，映射至统一的漏洞模型；规划基于严重度的阈值控制（扫描失败时可阻断流水线）。
3. **V3**：结合自动修复能力，尝试对简单安全问题给出自动修复建议或自动配置修复步骤。

## 8. 风险与注意事项

- Strix 默认需要 Docker 沙箱，确保运行环境开启虚拟化并安装 Docker。
- 安全扫描具有侵入性，请确保目标环境（或测试用户）拥有合法授权。
- 执行时间可能较长，建议在配置中增加超时与最大并发控制。
- 必须妥善保管 LLM/API 密钥，避免在日志中明文输出。

---

本设计完成后，可进入实现阶段（`libs/strix_runner.py`、`core/strix_manager.py`、`main.py` 参数扩展等），再补充用户文档与示例。完成实现后可继续探索与自动修复框架联动的可能性。
*** End Patch

