#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
元素爬取功能演示脚本
演示如何使用playwright_keywords中的元素爬取功能
"""

import os
import sys
from pathlib import Path

# 添加项目根目录到Python路径
sys.path.append(str(Path(__file__).parent.parent))

from keywords.playwright_keywords import (
    annotate_interactives,
    dump_elements,
    dump_dropdown_options,
    dump_iframes,
    set_playwright_driver
)
from core.playwright_driver import PlaywrightDriver


def demo_element_crawling():
    """演示元素爬取功能"""
    print("=== 元素爬取功能演示 ===")
    
    # 初始化Playwright驱动
    driver = None
    try:
        # 创建PlaywrightDriver实例
        driver = PlaywrightDriver()
        
        # 启动浏览器
        driver.start(browser_type="chromium", headless=False)
        
        # 设置全局Playwright驱动
        set_playwright_driver(driver)
        
        # 示例1：爬取百度首页元素
        print("\n1. 爬取百度首页元素...")
        driver.navigate("https://www.baidu.com")
        
        # 生成元素标注图
        screenshot_path = annotate_interactives(scope_label="BaiduHome")
        print(f"   标注截图已保存到: {screenshot_path}")
        
        # 导出元素清单
        elements_path = dump_elements(max_per_type=50)
        print(f"   元素清单已保存到: {elements_path}")
        
        # 导出iframe信息
        iframe_path = dump_iframes()
        print(f"   iframe信息已保存到: {iframe_path}")
        
        # 示例2：爬取GitHub首页元素
        print("\n2. 爬取GitHub首页元素...")
        driver.navigate("https://github.com")
        
        # 生成元素标注图
        screenshot_path = annotate_interactives(scope_label="GitHubHome")
        print(f"   标注截图已保存到: {screenshot_path}")
        
        # 导出元素清单
        elements_path = dump_elements(max_per_type=100)
        print(f"   元素清单已保存到: {elements_path}")
        
        print("\n=== 元素爬取演示完成 ===")
        print("\n爬取结果说明：")
        print("- 标注截图：reports/web/annotated/")
        print("- 元素清单：reports/web/interactives/")
        print("- 元素文本：reports/web/elements/latest.txt")
        print("- iframe信息：reports/web/iframes/latest.txt")
        
        # 手动输入继续，以便查看浏览器
        input("\n按Enter键关闭浏览器...")
        
    except Exception as e:
        print(f"\n演示过程中发生错误: {e}")
        import traceback
        traceback.print_exc()
    finally:
        if driver:
            driver.close()


if __name__ == "__main__":
    demo_element_crawling()
