#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
页面元素配置加载工具

功能：从页面元素配置.yaml文件中加载元素配置，并合并到测试用例的case_variables中

使用方法：
1. 在测试用例的case_variables中使用 $load_page_elements(page_name) 函数
2. 或者在case_context.py中自动加载

示例：
```yaml
case_variables:
  url: http://example.com
  # 自动加载销售合同页面的所有元素
  $load_page_elements: sales_contract_page
```
"""

import yaml
from pathlib import Path
from typing import Dict, Any, Optional
from libs.config_center import LOG, BASE_DIR


def load_page_elements(page_name: str, config_file: Optional[str] = None) -> Dict[str, Any]:
    """
    从页面元素配置文件中加载指定页面的元素配置
    
    :param page_name: 页面名称（如 "sales_contract_page", "login_page"）
    :param config_file: 配置文件路径（可选，默认查找同目录下的页面元素配置.yaml）
    :return: 元素配置字典
    """
    # 如果没有指定配置文件，尝试查找同目录下的配置文件
    if config_file is None:
        # 尝试在用例目录下查找
        config_file = Path(BASE_DIR) / "cases" / "web" / "6000" / "页面元素配置.yaml"
        if not config_file.exists():
            # 如果不存在，尝试在common_step目录下查找
            config_file = Path(BASE_DIR) / "cases" / "web" / "common_step" / "页面元素配置.yaml"
    
    if not config_file or not Path(config_file).exists():
        LOG.warning(f"页面元素配置文件不存在: {config_file}")
        return {}
    
    try:
        with open(config_file, 'r', encoding='utf-8') as f:
            config_data = yaml.safe_load(f)
        
        if not config_data:
            LOG.warning(f"配置文件为空: {config_file}")
            return {}
        
        # 查找指定页面的元素配置
        if page_name in config_data:
            elements = config_data[page_name]
            LOG.info(f"成功加载页面元素配置: {page_name}, 共 {len(elements)} 个元素")
            return elements
        else:
            LOG.warning(f"页面配置不存在: {page_name}, 可用页面: {list(config_data.keys())}")
            return {}
            
    except Exception as e:
        LOG.error(f"加载页面元素配置失败: {e}")
        import traceback
        traceback.print_exc()
        return {}


def merge_elements_to_case_variables(
    case_variables: Dict[str, Any],
    page_name: str,
    config_file: Optional[str] = None
) -> Dict[str, Any]:
    """
    将页面元素配置合并到测试用例的case_variables中
    
    :param case_variables: 测试用例的case_variables字典
    :param page_name: 页面名称
    :param config_file: 配置文件路径（可选）
    :return: 合并后的case_variables字典
    """
    # 加载页面元素配置
    page_elements = load_page_elements(page_name, config_file)
    
    if not page_elements:
        return case_variables
    
    # 合并元素配置（用例中的变量优先级更高）
    merged_variables = {**page_elements, **case_variables}
    
    LOG.info(f"已合并页面元素配置到case_variables: {page_name}")
    return merged_variables


def load_all_page_elements(config_file: Optional[str] = None) -> Dict[str, Dict[str, Any]]:
    """
    加载所有页面的元素配置
    
    :param config_file: 配置文件路径（可选）
    :return: 所有页面的元素配置字典
    """
    if config_file is None:
        config_file = Path(BASE_DIR) / "cases" / "web" / "6000" / "页面元素配置.yaml"
        if not Path(config_file).exists():
            config_file = Path(BASE_DIR) / "cases" / "web" / "common_step" / "页面元素配置.yaml"
    
    if not config_file or not Path(config_file).exists():
        LOG.warning(f"页面元素配置文件不存在: {config_file}")
        return {}
    
    try:
        with open(config_file, 'r', encoding='utf-8') as f:
            config_data = yaml.safe_load(f)
        
        if not config_data:
            return {}
        
        # 过滤掉非页面配置的键（如case_code, case_name等）
        page_configs = {}
        for key, value in config_data.items():
            if isinstance(value, dict) and not key.startswith('case_'):
                page_configs[key] = value
        
        LOG.info(f"成功加载所有页面元素配置，共 {len(page_configs)} 个页面")
        return page_configs
        
    except Exception as e:
        LOG.error(f"加载所有页面元素配置失败: {e}")
        return {}

