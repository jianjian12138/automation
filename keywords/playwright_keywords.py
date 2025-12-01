"""
Playwright关键字函数 - 用于Web UI测试
提供与Selenium兼容的关键字接口，但底层使用Playwright
"""

import time
import base64
import re
from typing import Optional, Any, Dict
from libs.config_center import LOG
import json, os, datetime
import logging

# 全局Playwright驱动（由case_run.py设置）
_playwright_driver = None


def set_playwright_driver(driver):
    """设置全局Playwright驱动"""
    global _playwright_driver
    _playwright_driver = driver


def get_playwright_driver():
    """获取全局Playwright驱动"""
    if _playwright_driver is None:
        raise RuntimeError("Playwright驱动未初始化，请先调用set_playwright_driver")
    return _playwright_driver


def enable_web_logs():
    """将运行日志输出到 reports/web/logs/latest.log（每次覆盖）"""
    base_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'reports', 'web')
    logs_dir = os.path.join(base_dir, 'logs')
    os.makedirs(logs_dir, exist_ok=True)
    log_path = os.path.join(logs_dir, 'latest.log')

    # 移除已存在的同路径 FileHandler，避免重复写入
    to_remove = []
    for h in LOG.handlers:
        if isinstance(h, logging.FileHandler):
            try:
                if hasattr(h, 'baseFilename') and os.path.abspath(h.baseFilename) == os.path.abspath(log_path):
                    to_remove.append(h)
            except Exception:
                continue
    for h in to_remove:
        LOG.removeHandler(h)
        try:
            h.close()
        except Exception:
            pass

    file_handler = logging.FileHandler(log_path, mode='w', encoding='utf-8')
    fmt = logging.Formatter('%(asctime)s - %(levelname)s - %(name)s - %(message)s')
    file_handler.setFormatter(fmt)
    LOG.addHandler(file_handler)
    # 确保 logger 级别不过低
    if LOG.level > logging.INFO:
        LOG.setLevel(logging.INFO)
    LOG.info(f"日志已重定向到: {log_path}")
    return log_path


def _parse_selector(selector: str) -> str:
    """
    解析选择器格式
    支持格式：
    - "s,selector" -> CSS选择器
    - "x,//xpath" -> XPath
    - "text=文本" -> 文本定位
    - "selector" -> 默认CSS选择器
    """
    if not selector or selector is None:
        raise ValueError(f"selector不能为空或None，请检查变量是否正确定义和解析。当前值: {selector}")
    
    # 如果selector不是字符串，尝试转换
    if not isinstance(selector, str):
        LOG.warning(f"selector不是字符串类型: {type(selector)}, 值: {selector}，尝试转换为字符串")
        selector = str(selector)
    
    selector = selector.strip()
    
    if ',' in selector and len(selector) > 2:
        prefix, selector = selector.split(',', 1)
        prefix = prefix.strip().lower()
        selector = selector.strip()
        
        if prefix == 's':
            # CSS选择器
            return selector
        elif prefix == 'x':
            # XPath
            return f"xpath={selector}"
        elif prefix == 't':
            # 文本定位
            return f"text={selector}"
        else:
            # 默认CSS选择器
            return selector
    
    # 如果已经是Playwright格式
    if selector.startswith(('text=', 'role=', 'xpath=')):
        return selector
    
    # 如果字符串以"//"开头，按xpath处理
    if selector.startswith('//'):
        return f"xpath={selector}"
    
    # 如果字符串以"text="开头
    if selector.startswith('text='):
        return selector
    
    # 默认作为CSS选择器
    return selector


def navigate(url: str):
    """导航到URL"""
    driver = get_playwright_driver()
    LOG.info(f"导航到: {url}")
    driver.navigate(url)
    return True


def click(selector: str):
    """
    点击元素（增强版：自动重试和多策略兜底）
    策略顺序: 
    1. 标准点击
    2. 滚动+悬停+点击
    3. 强制点击 (force=True)
    4. JS点击 (evaluate)
    """
    driver = get_playwright_driver()
    parsed_selector = _parse_selector(selector)
    LOG.info(f"点击元素: {parsed_selector}")
    
    # 获取当前上下文 (Page 或 Frame)
    ctx = driver.page
    if hasattr(driver, 'current_frame') and driver.current_frame:
        ctx = driver.current_frame

    # 定义点击策略
    def _do_click(strategy_name, action_func):
        try:
            action_func()
            LOG.info(f"点击成功 (策略: {strategy_name})")
            return True
        except Exception as e:
            LOG.warning(f"策略 {strategy_name} 失败: {e}")
            return False

    # 获取 locator
    try:
        if hasattr(ctx, 'locator'):
            loc = ctx.locator(parsed_selector)
            # 策略1: 标准点击 (尝试较短超时，以便快速进入兜底)
            if _do_click("Standard", lambda: loc.click(timeout=5000)): return True
            
            # 策略2: 滚动 + 悬停 + 点击
            if _do_click("Hover", lambda: (loc.scroll_into_view_if_needed(timeout=2000), loc.hover(timeout=2000), loc.click(timeout=3000))): return True
            
            # 策略3: 强制点击
            if _do_click("Force", lambda: loc.click(force=True, timeout=3000)): return True
            
            # 策略4: JS点击
            if _do_click("JS", lambda: loc.evaluate("el => el.click()")): return True
            
        else:
            # 如果 ctx 不支持 locator (罕见), 回退到 driver.click
            driver.click(parsed_selector)
            return True
            
    except Exception as e:
        LOG.error(f"元素点击最终失败: {parsed_selector}, 错误: {e}")
        raise e
    
    raise Exception(f"无法点击元素: {parsed_selector} (所有策略均已尝试)")
    return True


def hover_then_click(selector: str):
    """先悬停再点击（更稳）：在当前上下文中 hover 后 click"""
    driver = get_playwright_driver()
    selector = _parse_selector(selector)
    LOG.info(f"悬停并点击元素: {selector}")
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else driver.page
    loc = ctx.locator(selector)
    loc.scroll_into_view_if_needed()
    loc.hover()
    loc.click()
    return True


def force_click(selector: str):
    """强制点击（忽略可见性/遮挡），谨慎使用"""
    driver = get_playwright_driver()
    selector = _parse_selector(selector)
    LOG.info(f"强制点击元素: {selector}")
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else driver.page
    ctx.locator(selector).click(force=True)
    return True


def scroll_into_view(selector: str):
    """将元素滚动至视口内（容错版，超时不抛错）"""
    driver = get_playwright_driver()
    selector = _parse_selector(selector)
    LOG.info(f"滚动元素到视口: {selector}")
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else driver.page
    loc = ctx.locator(selector)
    try:
        # 使用较短超时，避免阻塞流程
        loc.scroll_into_view_if_needed(timeout=3000)
    except Exception as e:
        LOG.warning(f"scroll_into_view 超时或失败，忽略继续: {e}")
    return True


def select_first_option_via_keyboard(down_times: int = 1):
    """用于 Select 下拉：按 ArrowDown 指向第一项并回车确认（AntD/常见下拉稳健兜底）"""
    driver = get_playwright_driver()
    LOG.info(f"通过键盘选择第一条下拉项: ArrowDown x{down_times} + Enter")
    # 无论是否在iframe，键盘事件都发送到激活元素
    try:
        for _ in range(max(1, int(down_times))):
            driver.page.keyboard.press('ArrowDown')
        driver.page.keyboard.press('Enter')
        return True
    except Exception as e:
        LOG.warning(f"键盘选择第一条失败: {e}")
        # 兜底：尝试在当前frame发送
        try:
            ctx = driver.current_frame if getattr(driver, 'current_frame', None) else driver.page
            kb = getattr(ctx, 'keyboard', None) or driver.page.keyboard
            for _ in range(max(1, int(down_times))):
                kb.press('ArrowDown')
            kb.press('Enter')
            return True
        except Exception as e2:
            LOG.warning(f"在当前上下文发送键盘失败: {e2}")
            return False


def js_click(selector: str):
    """使用 JS 触发点击（可绕过部分遮挡/可见性校验）"""
    driver = get_playwright_driver()
    selector = _parse_selector(selector)
    LOG.info(f"JS 点击元素: {selector}")
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else driver.page
    ctx.locator(selector).evaluate("el => el.click()")
    return True


def wait_for_dropdown_open(timeout: int = 30000) -> bool:
    """等待下拉面板打开（.ant-select-dropdown:visible 或 .ant-select-open 状态）"""
    driver = get_playwright_driver()
    page = driver.page
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else page
    LOG.info(f"等待下拉面板打开，超时={timeout}ms")
    deadline = time.time() + max(1, int(timeout)) / 1000
    while time.time() < deadline:
        try:
            if page.locator('.ant-select-dropdown').is_visible():
                return True
        except Exception:
            pass
        try:
            # 在当前上下文检查 .ant-select-open
            if ctx.locator('.ant-select-open').count() > 0:
                return True
        except Exception:
            pass
        time.sleep(0.1)
    return False


def retry_click(selector: str, attempts: int = 4):
    """按序列多策略点击：normal -> hover -> force -> js"""
    driver = get_playwright_driver()
    selector = _parse_selector(selector)
    LOG.info(f"重试点击: {selector}, 次数={attempts}")
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else driver.page
    loc = ctx.locator(selector)
    for i in range(max(1, int(attempts))):
        try:
            if i == 0:
                loc.click()
            elif i == 1:
                loc.scroll_into_view_if_needed(); loc.hover(); loc.click()
            elif i == 2:
                loc.click(force=True)
            else:
                loc.evaluate("el => el.click()")
            return True
        except Exception as e:
            LOG.warning(f"第{i+1}次点击失败: {e}")
            time.sleep(0.2)
    return False


def click_select_and_wait_open(hints: Any, timeout: int = 5000) -> bool:
    """在弹窗内根据提示词查找选择框并点击，等待下拉打开。优先 page.modal，其次 frame.modal，最后通用 .ant-select-selector。"""
    driver = get_playwright_driver()
    page = driver.page
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else page
    if isinstance(hints, str):
        hints = [hints]
    LOG.info(f"click_select_and_wait_open，hints={hints}")

    def try_scope(scope):
        for h in hints:
            sel = scope.locator(f".ant-form-item:has-text('{h}') .ant-select-selector, .ant-select:has-text('{h}') .ant-select-selector").first
            if sel and sel.count() > 0:
                try:
                    sel.scroll_into_view_if_needed(); sel.hover()
                except Exception:
                    pass
                try:
                    sel.click()
                except Exception:
                    try:
                        sel.click(force=True)
                    except Exception:
                        sel.evaluate("el => el.click()")
                if wait_for_dropdown_open(timeout):
                    return True
        return False

    # 0) VXE 顶层弹窗
    try:
        if try_scope(page.locator('.vxe-dynamics, .vxe-modal--body')):
            return True
    except Exception:
        pass
    # 1) Ant 顶层弹窗
    try:
        if try_scope(page.locator('.ant-modal')):
            return True
    except Exception:
        pass
    # 2) VXE frame 级弹窗
    try:
        if try_scope(ctx.locator('.vxe-dynamics, .vxe-modal--body')):
            return True
    except Exception:
        pass
    # 3) Ant frame 级弹窗
    try:
        if try_scope(ctx.locator('.ant-modal')):
            return True
    except Exception:
        pass
    # 4) 通用：当前上下文第一个 select
    try:
        sel = ctx.locator('.ant-select .ant-select-selector').first
        if sel and sel.count() > 0:
            sel.click()
            if wait_for_dropdown_open(timeout):
                return True
    except Exception:
        pass
    return False


def annotate_interactives(scope_label: str = "Step") -> str:
    """扫描可交互元素并生成带编号标注截图+清单（返回截图路径）

    增强：为每个标注点额外记录 cssPath、xpath、role、aria、placeholder、name、type、outerHTML 片段。
    """
    driver = get_playwright_driver()
    page = driver.page
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else page

    LOG.info("扫描可交互元素并生成标注图…")

    # 选择容器：优先 VXE 弹窗，再次 Ant 弹窗，否则 body
    container_selector = 'body'
    try:
        if page.locator('.vxe-dynamics, .vxe-modal--body').count() > 0:
            container_selector = '.vxe-dynamics, .vxe-modal--body'
        elif page.locator('.ant-modal').count() > 0:
            container_selector = '.ant-modal'
        else:
            # 尝试当前frame内
            if ctx.locator('.vxe-dynamics, .vxe-modal--body').count() > 0:
                container_selector = '.vxe-dynamics, .vxe-modal--body'
            elif ctx.locator('.ant-modal').count() > 0:
                container_selector = '.ant-modal'
    except Exception:
        pass

    # 收集元素
    interactives = []
    script = """
    (containerSel) => {
      const container = document.querySelector(containerSel) || document.body;
      const nodes = container.querySelectorAll('button, [role="button"], a[href], input, textarea, select, .ant-select-selector, .ant-btn, .el-button, [tabindex]');
      const vis = (el)=>{
        const cs = window.getComputedStyle(el);
        const rect = el.getBoundingClientRect();
        return cs && cs.visibility !== 'hidden' && cs.display !== 'none' && rect.width>1 && rect.height>1;
      };
      const getCssPath = (el)=>{
        if(!el || !el.ownerDocument) return '';
        const path=[]; let node=el;
        while(node && node.nodeType===1 && node!==node.ownerDocument.documentElement){
          let sel = node.nodeName.toLowerCase();
          if(node.id){ sel += `#${node.id}`; path.unshift(sel); break; }
          let sib = node; let nth=1;
          while(sib = sib.previousElementSibling){ if(sib.nodeName===node.nodeName) nth++; }
          const hasSiblings = node.parentElement && Array.from(node.parentElement.children).filter(n=>n.nodeName===node.nodeName).length>1;
          if(hasSiblings) sel += `:nth-of-type(${nth})`;
          path.unshift(sel); node = node.parentElement;
        }
        return path.length? path.join(' > ') : '';
      };
      const getXPath = (el)=>{
        if(!el) return '';
        const segs=[]; let node=el;
        while(node && node.nodeType===1){
          let i=1; let sib=node.previousSibling;
          while(sib){ if(sib.nodeType===1 && sib.nodeName===node.nodeName) i++; sib=sib.previousSibling; }
          const seg = `${node.nodeName.toLowerCase()}[${i}]`;
          segs.unshift(seg); node=node.parentNode;
        }
        return '/' + segs.join('/');
      };
      const arr = [];
      let idx = 1;
      nodes.forEach(el=>{
        if(!vis(el)) return;
        const r = el.getBoundingClientRect();
        const text = (el.innerText||'').trim().slice(0,100);
        const tag = el.tagName.toLowerCase();
        const cls = el.className||'';
        const id = el.id||'';
        const role = el.getAttribute('role') || '';
        const placeholder = el.getAttribute('placeholder') || '';
        const name = el.getAttribute('name') || '';
        const type = el.getAttribute('type') || '';
        const aria = {};
        Array.from(el.attributes).forEach(a=>{ if(a.name.startsWith('aria-')) aria[a.name]=a.value; });
        const outerHTML = (el.outerHTML||'').replace(/\s+/g,' ').slice(0,300);
        const cssPath = getCssPath(el);
        const xpath = getXPath(el);
        const entry = {i: idx++, tag, id, cls, text, x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height), role, placeholder, name, type, aria, cssPath, xpath, outerHTML};
        arr.push(entry);
      });
      // 绘制覆盖层
      const overlay = document.createElement('div');
      overlay.id = '__ai_overlay__';
      overlay.style.cssText = 'position:fixed;inset:0;pointer-events:none;z-index:999999999;';
      document.body.appendChild(overlay);
      arr.forEach(it=>{
        const box = document.createElement('div');
        box.style.cssText = `position:fixed;left:${it.x}px;top:${it.y}px;width:${it.w}px;height:${it.h}px;border:2px solid #ff0033;border-radius:3px;box-sizing:border-box;`;
        const badge = document.createElement('div');
        badge.textContent = String(it.i);
        badge.style.cssText = 'position:absolute;left:-8px;top:-10px;background:#ff0033;color:#fff;font:12px/16px Arial;padding:0 4px;border-radius:8px;';
        box.appendChild(badge);
        overlay.appendChild(box);
      });
      return arr;
    }
    """
    try:
        interactives = ctx.evaluate(script, container_selector)
    except Exception as e:
        LOG.warning(f"收集可交互元素失败: {e}")
        interactives = []

    # 输出文件
    base_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'reports', 'web')
    inter_dir = os.path.join(base_dir, 'interactives')
    ann_dir = os.path.join(base_dir, 'annotated')
    os.makedirs(inter_dir, exist_ok=True)
    os.makedirs(ann_dir, exist_ok=True)
    json_path = os.path.join(inter_dir, f'interactives_{scope_label}.json')
    txt_path = os.path.join(inter_dir, f'interactives_{scope_label}.txt')
    shot_path = os.path.join(ann_dir, f'annotated_{scope_label}.png')

    try:
        with open(json_path, 'w', encoding='utf-8') as f:
            json.dump(interactives, f, ensure_ascii=False, indent=2)
        with open(txt_path, 'w', encoding='utf-8') as f:
            for it in interactives:
                line = (
                    f"[{it['i']}] tag={it.get('tag','')} id={it.get('id','')} class={it.get('cls','')} text={it.get('text','')} "
                    f"role={it.get('role','')} name={it.get('name','')} type={it.get('type','')} placeholder={it.get('placeholder','')}\n"
                )
                f.write(line)
    except Exception as e:
        LOG.warning(f"写入交互元素清单失败: {e}")

    # 截图
    try:
        page.screenshot(path=shot_path, full_page=True)
        LOG.info(f"标注截图: {shot_path}")
    except Exception as e:
        LOG.warning(f"截图失败: {e}")

    # 清除覆盖层
    try:
        ctx.evaluate("() => { const n = document.getElementById('__ai_overlay__'); if(n) n.remove(); }")
    except Exception:
        pass

    LOG.info(f"标注输出: 截图={shot_path} 清单JSON={json_path} 清单TXT={txt_path}")
    return shot_path


def auto_pick_and_click_by_text(hints: Any, within_modal: bool = True) -> bool:
    """在（弹窗内）根据提示词就近选择 .ant-select-selector 并点击，随后用键盘选择第一项"""
    driver = get_playwright_driver()
    page = driver.page
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else page
    if isinstance(hints, str):
        hints = [hints]
    LOG.info(f"基于提示词尝试点击下拉: {hints}")
    if within_modal:
        # 容器：VXE 或 Ant 弹窗（优先顶层）
        try:
            scope = page.locator('.vxe-dynamics, .vxe-modal--body, .ant-modal')
            if scope.count() == 0:
                scope = ctx.locator('.vxe-dynamics, .vxe-modal--body, .ant-modal')
        except Exception:
            scope = ctx
    else:
        scope = ctx
    try:
        for h in hints:
            try:
                sel = scope.locator(f".ant-form-item:has-text('{h}') .ant-select-selector, .ant-select:has-text('{h}') .ant-select-selector").first
                if sel and sel.count() > 0:
                    sel.scroll_into_view_if_needed()
                    try:
                        sel.hover()
                    except Exception:
                        pass
                    try:
                        sel.click()
                    except Exception:
                        sel.click(force=True)
                    # 键盘法确认第一条
                    select_first_option_via_keyboard(1)
                    return True
            except Exception:
                continue
    except Exception as e:
        LOG.warning(f"auto_pick_and_click_by_text 失败: {e}")
    return False



def input(selector: str, text: str):
    """输入文本"""
    driver = get_playwright_driver()
    selector = _parse_selector(selector)
    LOG.info(f"输入文本: {selector} = {text}")
    
    # 如果当前在iframe中，使用frame_locator的fill方法
    if hasattr(driver, 'current_frame') and driver.current_frame:
        # 检查是frame_locator还是frame对象
        if hasattr(driver.current_frame, 'locator'):
            # 如果是frame_locator，使用locator方法
            driver.current_frame.locator(selector).fill(text)
        else:
            # 如果是frame对象，直接使用
            driver.current_frame.fill(selector, text)
    else:
        driver.fill(selector, text)
    return True


def wait_element_visibility(timeout, selector: str):
    """等待元素可见"""
    driver = get_playwright_driver()
    selector = _parse_selector(selector)
    
    # 确保timeout是整数
    if isinstance(timeout, str):
        try:
            timeout = int(timeout)
        except ValueError:
            timeout = 30  # 默认30秒
    
    LOG.info(f"等待元素可见: {selector}, 超时={timeout}秒")
    
    # 如果当前在iframe中，使用frame_locator的wait_for方法
    if hasattr(driver, 'current_frame') and driver.current_frame:
        # 检查是frame_locator还是frame对象
        if hasattr(driver.current_frame, 'locator'):
            # 如果是frame_locator，使用locator方法
            try:
                driver.current_frame.locator(selector).wait_for(state='visible', timeout=timeout * 1000)
            except Exception as e:
                # 如果等待失败，尝试等待iframe加载完成后再试
                LOG.warning(f"首次等待元素失败: {e}，尝试等待iframe加载后重试")
                import time
                time.sleep(2)  # 等待2秒让iframe内容加载
                driver.current_frame.locator(selector).wait_for(state='visible', timeout=timeout * 1000)
        else:
            # 如果是frame对象，使用wait_for_selector
            try:
                driver.current_frame.wait_for_selector(selector, state='visible', timeout=timeout * 1000)
            except Exception as e:
                LOG.warning(f"首次等待元素失败: {e}，尝试等待iframe加载后重试")
                import time
                time.sleep(2)
                driver.current_frame.wait_for_selector(selector, state='visible', timeout=timeout * 1000)
    else:
        driver.wait_for_selector(selector, state='visible', timeout=timeout * 1000)
    return selector  # 返回选择器，用于后续操作


def wait_element_clickable(timeout: int, selector: str):
    """等待元素可点击"""
    driver = get_playwright_driver()
    selector = _parse_selector(selector)
    
    # 确保timeout是整数
    if isinstance(timeout, str):
        try:
            timeout = int(timeout)
        except ValueError:
            timeout = 30  # 默认30秒
    
    LOG.info(f"等待元素可点击: {selector}, 超时={timeout}秒")
    
    # 如果当前在iframe中，使用frame_locator的wait_for方法
    if hasattr(driver, 'current_frame') and driver.current_frame:
        # 检查是frame_locator还是frame对象
        if hasattr(driver.current_frame, 'locator'):
            # 如果是frame_locator，使用locator方法
            locator = driver.current_frame.locator(selector)
            locator.wait_for(state='visible', timeout=timeout * 1000)
            # 检查元素是否可点击
            if not locator.is_enabled():
                raise Exception(f"元素不可点击: {selector}")
        else:
            # 如果是frame对象，使用wait_for_selector
            driver.current_frame.wait_for_selector(selector, state='visible', timeout=timeout * 1000)
            # 检查元素是否可点击
            if not driver.current_frame.is_enabled(selector):
                raise Exception(f"元素不可点击: {selector}")
    else:
        element = driver.wait_for_selector(selector, state='visible', timeout=timeout * 1000)
        # 检查元素是否可点击
        if not driver.is_enabled(selector):
            raise Exception(f"元素不可点击: {selector}")
    return selector  # 返回选择器，用于后续操作


def wait_text_in_element(selector: str, text: str, timeout: int = 30):
    """
    等待元素包含文本（Playwright版本）
    
    :param selector: 元素选择器（可以是变量引用，如 *variable_name）
    :param text: 期望的文本内容
    :param timeout: 超时时间（秒），可以是整数或字符串
    :return: True 如果元素包含文本
    """
    driver = get_playwright_driver()
    
    # 解析变量引用（如果selector以*开头，说明是变量引用）
    if isinstance(selector, str) and selector.startswith('*'):
        # 尝试从上下文获取变量值
        try:
            from core.case_context import g
            if hasattr(g, 'case_info') and g.case_info:
                # 先尝试从step_variables获取
                if hasattr(g.case_info, 'step_info') and g.case_info.step_info:
                    step_variables = getattr(g.case_info.step_info, 'step_variables', {})
                    var_name = selector[1:]  # 去掉*前缀
                    if var_name in step_variables:
                        selector = step_variables[var_name]
                        LOG.debug(f"从step_variables解析变量 *{var_name} = {selector}")
                
                # 如果step_variables中没有，尝试从case_variables获取
                if isinstance(selector, str) and selector.startswith('*'):
                    case_variables = getattr(g.case_info, 'case_variables', {})
                    var_name = selector[1:]  # 去掉*前缀
                    if var_name in case_variables:
                        selector = case_variables[var_name]
                        LOG.debug(f"从case_variables解析变量 *{var_name} = {selector}")
        except Exception as e:
            LOG.debug(f"解析变量失败: {e}，继续使用原始selector")
    
    # 解析选择器格式
    selector = _parse_selector(selector)

    # 确保timeout是整数（处理字符串类型的timeout）
    if isinstance(timeout, str):
        try:
            timeout = int(timeout)
        except ValueError:
            LOG.warning(f"timeout参数无法转换为整数: {timeout}，使用默认值30秒")
            timeout = 30
    elif not isinstance(timeout, (int, float)):
        LOG.warning(f"timeout参数类型不正确: {type(timeout)}，使用默认值30秒")
        timeout = 30
    
    # 确保timeout是正数
    timeout = max(1, int(timeout))
    
    LOG.info(f"等待元素包含文本: {selector}, 文本={text}, 超时={timeout}秒")

    # 转换超时时间为毫秒（Playwright使用毫秒）
    timeout_ms = timeout * 1000

    # 选择正确的上下文（支持已切换的 iframe）
    driver = get_playwright_driver()
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else driver.page

    # 使用 Playwright 原生 selector engines，避免将 xpath= 误当作 CSS
    try:
        # 直接用 ctx.locator(selector) 并结合 has_text 过滤
        # 对于 text= 选择器，Playwright 自带文本匹配，无需额外过滤
        if selector.startswith('text='):
            locator = ctx.locator(selector)
        else:
            locator = ctx.locator(selector).filter(has_text=text)

        locator.wait_for(state='visible', timeout=timeout_ms)
        LOG.info(f"元素包含文本 '{text}' 已找到")
        return True
        
    except Exception as e:
        # 如果locator方法失败，使用轮询方式
        LOG.debug(f"locator方法失败: {e}，使用轮询方式检查文本")
        
        # 方法2: 使用轮询方式检查元素文本
        start_time = time.time()
        while time.time() - start_time < timeout:
            try:
                # 等待元素出现（直接使用已解析的 selector）
                ctx.wait_for_selector(selector, timeout=1000)

                # 获取元素文本
                element_text = None
                try:
                    element_text = ctx.locator(selector).first.text_content()
                except Exception:
                    element_text = driver.get_text(selector)
                if element_text and text in element_text:
                    LOG.info(f"元素包含文本 '{text}' 已找到（轮询方式）")
                    return True
            except Exception as e:
                LOG.debug(f"获取元素文本失败: {e}")
            
            time.sleep(0.5)  # 每0.5秒检查一次
        
        # 超时，抛出异常
        raise Exception(f"等待超时：元素 '{selector}' 在 {timeout} 秒内未包含文本 '{text}'")


def switch_to_iframe(selector: str):
    """切换到iframe"""
    driver = get_playwright_driver()
    selector = _parse_selector(selector)
    LOG.info(f"切换到iframe: {selector}")
    
    # 根据当前上下文选择查找iframe的对象
    context = driver.page
    if hasattr(driver, 'current_frame') and driver.current_frame:
        # Frame 对象具备 wait_for_selector 方法
        if hasattr(driver.current_frame, 'wait_for_selector'):
            context = driver.current_frame
        # FrameLocator 没有 wait_for_selector，但仍可以作为兜底
    
    try:
        # 先等待iframe元素出现
        iframe_element = context.wait_for_selector(selector, state='attached', timeout=15000)
    except Exception as e:
        LOG.debug(f"通过选择器查找iframe失败，将尝试根据URL匹配: {e}")
        iframe_element = None
    
    frame = None
    if iframe_element:
        try:
            frame = iframe_element.content_frame()
        except Exception as e:
            LOG.warning(f"获取iframe内容失败: {e}")
            frame = None
    
    if frame is None:
        # 如果无法通过元素获取frame，尝试根据selector中的src关键字匹配frame
        url_keyword = None
        match = re.search(r"contains\(@src,\s*['\"]([^'\"]+)['\"]\)", selector)
        if match:
            url_keyword = match.group(1)
        elif selector.startswith("frame_url="):
            url_keyword = selector.split("=", 1)[1].strip()
        
        if url_keyword:
            LOG.debug(f"尝试通过URL关键字匹配iframe: {url_keyword}")
            candidate_frames = []
            if context is driver.page:
                candidate_frames = driver.page.frames
            else:
                candidate_frames = getattr(context, "child_frames", [])
            for child in candidate_frames:
                if url_keyword in child.url:
                    frame = child
                    break
    
    if frame:
        driver.current_frame = frame
        try:
            frame.wait_for_load_state('domcontentloaded', timeout=10000)
            LOG.debug("iframe内容已加载")
        except Exception as e:
            LOG.warning(f"等待iframe加载超时，继续执行: {e}")
        return True
    
    # 如果无法获取Frame对象，谨慎使用 frame_locator 作为兜底：先确认DOM中确有该 iframe 元素
    try:
        frame_locator_selector = selector
        if selector.startswith("frame_url="):
            url_keyword = selector.split("=", 1)[1].strip()
            frame_locator_selector = f"iframe[src*='{url_keyword}']"

        # 先检查是否存在匹配的 iframe 元素
        matches = driver.page.locator(frame_locator_selector)
        count = matches.count() if hasattr(matches, 'count') else 0
        if count and count > 0:
            frame_locator = driver.page.frame_locator(frame_locator_selector)
            driver.current_frame = frame_locator
            try:
                frame_locator.locator('xpath=//body').wait_for(state='attached', timeout=5000)
            except Exception as e:
                LOG.warning(f"等待iframe body超时，继续执行: {e}")
            return True
        else:
            LOG.warning(f"未找到匹配的iframe元素: {frame_locator_selector}，保持默认上下文")
            return False
    except Exception as e:
        LOG.warning(f"查找iframe失败，保持默认上下文: {e}")
        return False


def switch_to_default_frame():
    """切换回默认frame"""
    driver = get_playwright_driver()
    LOG.info("切换回默认frame")
    driver.current_frame = None
    return True


def assert_element_text_contains(selector: str, expected_text: str):
    """断言元素文本包含指定文本"""
    driver = get_playwright_driver()
    
    # 如果selector为None或空，尝试从step_variables获取element或selector变量
    if not selector or selector == 'None' or selector == '':
        from core.http_client import g
        if hasattr(g, 'case_info') and g.case_info:
            if hasattr(g.case_info, 'step_info') and g.case_info.step_info:
                step_vars = getattr(g.case_info.step_info, 'step_variables', {})
                LOG.debug(f"当前step_variables: {step_vars}")
                # 尝试从element变量获取
                element_value = step_vars.get('element')
                if element_value:
                    selector = element_value
                    LOG.debug(f"从element变量获取selector: {selector}")
                else:
                    # 如果element不存在，尝试从selector变量获取
                    selector_value = step_vars.get('selector')
                    if selector_value:
                        selector = selector_value
                        LOG.debug(f"从selector变量获取selector: {selector}")
            
            # 如果还是找不到，尝试从case_variables中查找可能的变量
            if not selector or selector == 'None' or selector == '':
                # 首先尝试从当前步骤的selector变量获取（如果element变量不存在，可能selector变量存在）
                if hasattr(g.case_info, 'step_info') and g.case_info.step_info:
                    step_vars = getattr(g.case_info.step_info, 'step_variables', {})
                    selector_value = step_vars.get('selector')
                    if selector_value:
                        selector = selector_value
                        LOG.debug(f"从step_variables的selector变量获取selector: {selector}")
                
                # 如果还是找不到，尝试从case_variables中查找可能的变量
                if not selector or selector == 'None' or selector == '':
                    case_vars = getattr(g.case_info, 'case_variables', {})
                    LOG.debug(f"尝试从case_variables查找变量，可用变量: {list(case_vars.keys())}")
                    
                    # 尝试查找常见的元素变量名（优先查找与当前步骤相关的变量）
                    # 根据expected_text，尝试推断可能的变量名
                    possible_var_names = ['element', 'selector', 'quick_navigation', 'login_message', 
                                         'username_input', 'password_input', 'login_button']
                    for var_name in possible_var_names:
                        if var_name in case_vars:
                            selector = case_vars[var_name]
                            LOG.debug(f"从case_variables的{var_name}变量获取selector: {selector}")
                            break
    
    if not selector or selector == 'None' or selector == '':
        # 记录详细的调试信息
        from core.http_client import g
        debug_info = []
        if hasattr(g, 'case_info') and g.case_info:
            if hasattr(g.case_info, 'step_info') and g.case_info.step_info:
                step_vars = getattr(g.case_info.step_info, 'step_variables', {})
                debug_info.append(f"step_variables: {step_vars}")
            case_vars = getattr(g.case_info, 'case_variables', {})
            debug_info.append(f"case_variables keys: {list(case_vars.keys())}")
        raise ValueError(f"selector参数不能为空，请确保element或selector变量已正确设置。调试信息: {'; '.join(debug_info)}")
    
    # selector可能是选择器字符串，需要解析
    selector = _parse_selector(str(selector))
    LOG.info(f"断言元素文本包含: {selector}, 期望文本={expected_text}")
    
    # 如果当前在iframe中，使用frame_locator获取文本
    if hasattr(driver, 'current_frame') and driver.current_frame:
        # 检查是frame_locator还是frame对象
        if hasattr(driver.current_frame, 'locator'):
            # 如果是frame_locator，使用locator方法
            actual_text = driver.current_frame.locator(selector).text_content()
        else:
            # 如果是frame对象，直接使用
            actual_text = driver.current_frame.text_content(selector)
    else:
        actual_text = driver.get_text(selector)
    
    if actual_text is None:
        actual_text = ""
    
    if expected_text not in actual_text:
        raise AssertionError(f"元素文本不包含期望文本。实际: {actual_text}, 期望: {expected_text}")
    return True


def assert_url_contains(expected_url: str):
    """断言URL包含指定文本"""
    driver = get_playwright_driver()
    current_url = driver.page.url
    LOG.info(f"断言URL包含: 当前URL={current_url}, 期望包含={expected_url}")
    
    if expected_url not in current_url:
        raise AssertionError(f"URL不包含期望文本。实际: {current_url}, 期望包含: {expected_url}")
    return True


def get_local_storage_item(key: str):
    """获取localStorage项"""
    driver = get_playwright_driver()
    value = driver.get_local_storage(key)
    LOG.info(f"获取localStorage: {key} = {value}")
    return value


def set_local_storage_item(key: str, value: str):
    """设置localStorage项"""
    driver = get_playwright_driver()
    driver.set_local_storage(key, value)
    LOG.info(f"设置localStorage: {key} = {value}")
    return True


def check_login_status() -> bool:
    """检查是否已登录"""
    driver = get_playwright_driver()
    try:
        authorization = driver.get_local_storage('Authorization')
        if authorization:
            LOG.info("检测到已登录状态（存在Authorization）")
            return True
    except Exception as e:
        LOG.debug(f"检查登录状态失败: {e}")
    
    # 检查页面是否有登录成功的标识
    try:
        # 尝试查找"快捷导航"文本，如果存在说明已登录
        from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
        from keywords.playwright_keywords import _parse_selector
        selector = _parse_selector('s,p.title')
        driver.page.wait_for_selector(selector, timeout=2000)
        text = driver.page.locator(selector).text_content()
        if text and '快捷导航' in text:
            LOG.info("检测到已登录状态（页面包含快捷导航）")
            return True
    except Exception:
        pass
    
    LOG.info("未检测到登录状态")
    return False


def skip_login_if_logged_in():
    """如果已登录则跳过登录步骤（返回True表示已登录，False表示未登录）"""
    is_logged_in = check_login_status()
    if is_logged_in:
        LOG.info("已登录，跳过登录步骤")
        # 设置一个变量标记已登录，后续步骤可以根据这个变量决定是否跳过
        from core.case_context import g
        if hasattr(g, 'case_info') and g.case_info:
            if hasattr(g.case_info, 'step_info') and g.case_info.step_info:
                g.case_info.step_info.step_variables['is_logged_in'] = True
    else:
        LOG.info("未登录，需要执行登录")
    return is_logged_in


def driver_wait(timeout: int):
    """创建等待对象（兼容性函数）"""
    return timeout


def sleep(seconds: float):
    """睡眠"""
    time.sleep(seconds)
    return True


def set_variable(name: str, value: Any):
    """设置变量（存储到全局变量字典）"""
    from core.http_client import g
    # 如果值为 None，且变量已存在，则不覆盖（保持原有值）
    if value is None:
        if hasattr(g, 'case_info') and g.case_info:
            if hasattr(g.case_info, 'step_info') and g.case_info.step_info:
                if name in g.case_info.step_info.step_variables:
                    LOG.debug(f"变量 {name} 的值为 None，保持原有值: {g.case_info.step_info.step_variables[name]}")
                    return g.case_info.step_info.step_variables[name]
            elif hasattr(g.case_info, 'variables') and name in g.case_info.variables:
                LOG.debug(f"变量 {name} 的值为 None，保持原有值: {g.case_info.variables[name]}")
                return g.case_info.variables[name]
        elif hasattr(g, 'variables') and name in g.variables:
            LOG.debug(f"变量 {name} 的值为 None，保持原有值: {g.variables[name]}")
            return g.variables[name]
    
    if not hasattr(g, 'case_info') or not g.case_info:
        # 如果没有case_info，创建一个简单的变量存储
        if not hasattr(g, 'variables'):
            g.variables = {}
        g.variables[name] = value
    else:
        # 存储到case_info的step_variables中
        if not hasattr(g.case_info, 'step_info'):
            # 如果没有step_info，创建一个简单的变量存储
            if not hasattr(g.case_info, 'variables'):
                g.case_info.variables = {}
            g.case_info.variables[name] = value
        else:
            # 存储到step_variables中
            g.case_info.step_info.step_variables[name] = value
    LOG.info(f"设置变量: {name} = {value}")
    return value


# ===================== 调试辅助关键词 =====================
def dump_dropdown_options(scope_label: str = "Step") -> str:
    """导出当前可见下拉面板(ant-select-dropdown)中的所有选项文本与可用状态。
    保存到 reports/web/dropdown/latest.txt，并返回文件路径。
    """
    import os
    driver = get_playwright_driver()
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else driver.page

    # 仅抓取可见的 AntD 下拉
    try:
        dropdown = ctx.locator("css=.ant-select-dropdown:not(.ant-select-dropdown-hidden)")
        if hasattr(dropdown, 'count') and dropdown.count() == 0:
            # 兼容多个 portal
            dropdown = ctx.locator("css=.ant-select-dropdown")
    except Exception:
        dropdown = ctx.locator("css=.ant-select-dropdown")

    option_locator = dropdown.locator("css=.ant-select-item-option")
    texts = []
    try:
        count = option_locator.count() if hasattr(option_locator, 'count') else 0
    except Exception:
        count = 0
    for i in range(count):
        opt = option_locator.nth(i)
        try:
            cls = opt.get_attribute("class") or ""
            disabled = "ant-select-item-option-disabled" in cls
            txt = opt.locator("css=.ant-select-item-option-content").inner_text(timeout=1000)
        except Exception:
            try:
                txt = opt.inner_text(timeout=1000)
            except Exception:
                txt = ""
            disabled = "ant-select-item-option-disabled" in (opt.get_attribute("class") or "")
        texts.append((i, txt.strip(), disabled))

    # 输出文件
    base_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "reports", "web")
    out_dir = os.path.join(base_dir, "dropdown")
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "latest.txt")
    try:
        with open(out_path, 'w', encoding='utf-8') as f:
            f.write(f"DROPDOWN OPTIONS ({scope_label})\n")
            for idx, t, dis in texts:
                f.write(f"[{idx}] {'(禁用) ' if dis else ''}{t}\n")
    except Exception:
        pass
    LOG.info(f"下拉选项导出: {out_path}, 项数={len(texts)}")
    return out_path

# ===================== 键盘与焦点辅助 =====================
def focus(selector: str):
    """将焦点移动到指定元素。"""
    driver = get_playwright_driver()
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else driver.page
    sel = _parse_selector(selector)
    LOG.info(f"焦点定位到: {sel}")
    loc = ctx.locator(sel)
    loc.wait_for(state='attached', timeout=5000)
    try:
        loc.scroll_into_view_if_needed(timeout=3000)
    except Exception:
        pass
    loc.focus()

def press_key(key: str):
    """在当前页面发送一个按键（如 Enter、Escape）。"""
    driver = get_playwright_driver()
    page = driver.page if hasattr(driver, 'page') else None
    if not page:
        raise Exception("Playwright 页面不可用，无法发送按键")
    LOG.info(f"发送按键: {key}")
    page.keyboard.press(key)

def press_enter_on(selector: str):
    """先聚焦到元素，再发送 Enter。"""
    focus(selector)
    press_key('Enter')
def dump_iframes() -> str:
    """打印并保存页面所有 iframe 信息（src、id、name、class），返回保存文件路径"""
    import os, datetime
    driver = get_playwright_driver()
    page = driver.page
    base_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "reports", "web")
    out_dir = os.path.join(base_dir, "iframes")
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "latest.txt")

    lines = []
    lines.append("IFRAME LIST:")
    for idx, frame in enumerate(page.frames):
        src = frame.url
        name = getattr(frame, "name", "") if frame.name else ""
        frame_id = frame_name = frame_class = None
        try:
            el = frame.frame_element()
            frame_id = el.get_attribute("id")
            frame_name = el.get_attribute("name")
            frame_class = el.get_attribute("class")
        except Exception:
            pass
        lines.append(f"[{idx}] url={src} name={name} id={frame_id} nameAttr={frame_name} class={frame_class}")

    content = "\n".join(lines)
    try:
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(content)
    except Exception:
        pass
    LOG.info(content)
    LOG.info(f"iframe 列表已保存: {out_path}")
    return out_path


def dump_elements(max_per_type: int = 50) -> str:
    """打印并保存当前上下文内常见元素（input/button/a/div[可见文本]）。若已切换iframe，则在iframe内枚举。
    返回保存文件路径。"""
    import os, datetime
    driver = get_playwright_driver()
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else driver.page

    # 兼容字符串参数，确保为正整数
    try:
        if isinstance(max_per_type, str):
            max_per_type = int(max_per_type.strip())
        elif not isinstance(max_per_type, int):
            max_per_type = int(max_per_type)
    except Exception:
        max_per_type = 50
    if max_per_type <= 0:
        max_per_type = 50

    base_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "reports", "web")
    out_dir = os.path.join(base_dir, "elements")
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "latest.txt")

    def safe_get(locator, getter):
        try:
            return getter()
        except Exception:
            return None

    lines = []
    scope = "iframe" if getattr(driver, 'current_frame', None) else "page"
    lines.append(f"ELEMENTS DUMP (scope={scope}):")

    # Inputs
    inputs = ctx.locator("input")
    count = inputs.count() if hasattr(inputs, 'count') else 0
    lines.append(f"inputs: {count}")
    for i in range(min(count, max_per_type)):
        el = inputs.nth(i)
        placeholder = safe_get(el, lambda: el.get_attribute("placeholder"))
        el_type = safe_get(el, lambda: el.get_attribute("type"))
        el_id = safe_get(el, lambda: el.get_attribute("id"))
        cls = safe_get(el, lambda: el.get_attribute("class"))
        lines.append(f"  [{i}] type={el_type} placeholder={placeholder} id={el_id} class={cls}")

    # Buttons
    buttons = ctx.locator("button")
    bcount = buttons.count() if hasattr(buttons, 'count') else 0
    lines.append(f"buttons: {bcount}")
    for i in range(min(bcount, max_per_type)):
        el = buttons.nth(i)
        text = safe_get(el, lambda: el.text_content())
        cls = safe_get(el, lambda: el.get_attribute("class"))
        lines.append(f"  [{i}] text={text!r} class={cls}")

    # Links
    links = ctx.locator("a")
    lcount = links.count() if hasattr(links, 'count') else 0
    lines.append(f"links: {lcount}")
    for i in range(min(lcount, max_per_type)):
        el = links.nth(i)
        text = safe_get(el, lambda: el.text_content())
        href = safe_get(el, lambda: el.get_attribute("href"))
        lines.append(f"  [{i}] text={text!r} href={href}")

    # Spans with text (helpful when buttons wrap text spans)
    spans = ctx.locator("span")
    scount = spans.count() if hasattr(spans, 'count') else 0
    lines.append(f"spans: {scount}")
    for i in range(min(scount, max_per_type)):
        el = spans.nth(i)
        text = safe_get(el, lambda: el.text_content())
        cls = safe_get(el, lambda: el.get_attribute("class"))
        if text and text.strip():
            lines.append(f"  [{i}] text={text.strip()!r} class={cls}")

    # Divs with text (toolbars often use div wrappers)
    divs = ctx.locator("div")
    dcount = divs.count() if hasattr(divs, 'count') else 0
    lines.append(f"divs_with_text: {dcount}")
    shown = 0
    for i in range(dcount):
        if shown >= max_per_type:
            break
        el = divs.nth(i)
        text = safe_get(el, lambda: el.text_content())
        if text and text.strip():
            cls = safe_get(el, lambda: el.get_attribute("class"))
            lines.append(f"  [{i}] text={text.strip()!r} class={cls}")
            shown += 1

    content = "\n".join(lines)
    try:
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(content)
    except Exception:
        pass
    LOG.info(content)
    LOG.info(f"元素列表已保存: {out_path}")
    return out_path


def dump_text_candidates(text: str, max_candidates: int = 15) -> str:
    """根据给定文本枚举候选元素，输出关键信息并保存到文件，返回保存路径。
    会在当前上下文（页面或已切换的 iframe）内搜索包含该文本的节点，并给出可尝试的选择器建议。
    """
    import os, datetime
    driver = get_playwright_driver()
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else driver.page

    # 兼容字符串参数
    try:
        if isinstance(max_candidates, str):
            max_candidates = int(max_candidates.strip())
        else:
            max_candidates = int(max_candidates)
    except Exception:
        max_candidates = 15
    if max_candidates <= 0:
        max_candidates = 15

    base_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "reports", "web")
    out_dir = os.path.join(base_dir, "text_candidates")
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "latest.txt")

    lines = []
    scope = "iframe" if getattr(driver, 'current_frame', None) else "page"
    lines.append(f"TEXT CANDIDATES (scope={scope}, text={text!r}):")

    def cap(s: str, n: int = 200) -> str:
        if s is None:
            return ""
        s = s.strip()
        return s if len(s) <= n else s[:n] + "..."

    # 构造多个查询策略
    queries = [
        f"text={text}",
        f":has-text(\"{text}\")",
        f"span:has-text(\"{text}\")",
        f"div:has-text(\"{text}\")",
        f"button:has-text(\"{text}\")",
        f"a:has-text(\"{text}\")",
        f"[role='button']:has-text(\"{text}\")",
    ]

    seen = set()
    total = 0
    for q in queries:
        try:
            locator = ctx.locator(q)
            count = locator.count() if hasattr(locator, 'count') else 0
        except Exception:
            continue
        for i in range(count):
            if total >= max_candidates:
                break
            el = locator.nth(i)
            try:
                # 用 JS 读取详细信息
                info = el.evaluate(
                    "(node) => ({\n"
                    "  tag: node.tagName && node.tagName.toLowerCase(),\n"
                    "  id: node.id || null,\n"
                    "  cls: node.className || null,\n"
                    "  role: node.getAttribute && node.getAttribute('role'),\n"
                    "  html: node.outerHTML,\n"
                    "  text: node.innerText || node.textContent\n"
                    "})"
                )
            except Exception:
                info = {"tag": None, "id": None, "cls": None, "role": None, "html": None, "text": None}

            # 去重（基于outerHTML片段）
            key = info.get("html") or info.get("text") or f"{q}-{i}"
            key = cap(key, 150)
            if key in seen:
                continue
            seen.add(key)
            total += 1

            tag = info.get("tag")
            el_id = info.get("id")
            cls = info.get("cls")
            role = info.get("role")
            text_snippet = cap(info.get("text") or "", 160)
            html_snippet = cap(info.get("html") or "", 200)

            # 生成建议选择器
            suggestions = []
            if el_id:
                suggestions.append(f"s,#{el_id}")
            if cls:
                first_cls = str(cls).split()[0]
                if first_cls:
                    if tag:
                        suggestions.append(f"s,{tag}.{first_cls}:has-text(\"{text}\")")
                    suggestions.append(f"s,.{first_cls}:has-text(\"{text}\")")
            if tag:
                suggestions.append(f"s,{tag}:has-text(\"{text}\")")
            suggestions.append(f"s,[role='button']:has-text(\"{text}\")")

            lines.append(f"- QUERY: {q}")
            lines.append(f"  tag={tag} id={el_id} class={cls} role={role}")
            lines.append(f"  text={text_snippet!r}")
            lines.append(f"  outerHTML={html_snippet!r}")
            if suggestions:
                lines.append(f"  suggestions: {', '.join(suggestions)}")

            # 限制输出数量
            if total >= max_candidates:
                break

    content = "\n".join(lines)
    try:
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(content)
    except Exception:
        pass
    LOG.info(content)
    LOG.info(f"文本候选已保存: {out_path}")
    return out_path

def assert_variable_not_empty(name: str):
    """断言变量不为空"""
    from core.http_client import g
    value = None
    
    # 尝试从step_variables获取
    if hasattr(g, 'case_info') and g.case_info and hasattr(g.case_info, 'step_info'):
        value = g.case_info.step_info.step_variables.get(name)
    
    # 如果step_variables中没有，尝试从case_info.variables获取
    if value is None and hasattr(g, 'case_info') and g.case_info:
        if hasattr(g.case_info, 'variables'):
            value = g.case_info.variables.get(name)
    
    # 如果还没有，尝试从g.variables获取
    if value is None and hasattr(g, 'variables'):
        value = g.variables.get(name)
    
    if value is None or value == '':
        raise AssertionError(f"变量 {name} 为空或不存在")
    
    LOG.info(f"断言变量不为空: {name} = {value}")
    return True


def take_screenshot(path: Optional[str] = None, full_page: bool = False):
    """截图"""
    driver = get_playwright_driver()
    LOG.info(f"截图: {path or '自动生成路径'}")
    return driver.screenshot(path=path, full_page=full_page)


# ============================================
# Stagehand AI 驱动关键字函数
# ============================================

def click_at(x: int, y: int, delay_ms: int = 0):
    """
    在页面绝对坐标点击（适用于跨域iframe等场景）
    :param x: 页面X坐标（相对于视口左上角+滚动偏移后的page坐标）
    :param y: 页面Y坐标
    :param delay_ms: 点击后可选延时（毫秒）
    """
    driver = get_playwright_driver()
    LOG.info(f"坐标点击: ({x}, {y})")
    driver.page.mouse.click(x, y)
    if delay_ms and isinstance(delay_ms, int) and delay_ms > 0:
        import time as _time
        _time.sleep(delay_ms / 1000.0)
    return True

def ai_act(instruction: str, timeout: int = 30000):
    """
    使用 AI 执行自然语言操作（Stagehand 风格）
    
    :param instruction: 自然语言指令，如 "点击登录按钮"、"输入用户名test"
    :param timeout: 超时时间（毫秒）
    :return: 操作结果
    """
    driver = get_playwright_driver()
    page = driver.page
    
    # 导入 Stagehand 集成
    try:
        from core.stagehand_integration import StagehandIntegration
        
        # 创建 Stagehand 集成实例
        stagehand = StagehandIntegration(page)
        
        # 执行操作
        result = stagehand.act(instruction, timeout)
        
        LOG.info(f"AI 操作执行: {instruction}, 结果: {result.get('success', False)}")
        return result
    except ImportError as e:
        LOG.error(f"Stagehand 集成未找到: {e}")
        raise RuntimeError("Stagehand 集成未安装，请检查 core/stagehand_integration.py")


def ai_agent(task: str, max_steps: int = 10, timeout: int = 60000):
    """
    使用 AI Agent 执行多步骤任务（Stagehand 风格）
    
    :param task: 任务描述，如 "登录并查看订单列表"
    :param max_steps: 最大步骤数
    :param timeout: 超时时间（毫秒）
    :return: 任务执行结果
    """
    driver = get_playwright_driver()
    page = driver.page
    
    # 导入 Stagehand 集成
    try:
        from core.stagehand_integration import StagehandIntegration
        
        # 创建 Stagehand 集成实例
        stagehand = StagehandIntegration(page)
        
        # 执行任务
        result = stagehand.agent(task, max_steps, timeout)
        
        LOG.info(f"AI Agent 执行: {task}, 结果: {result.get('success', False)}")
        return result
    except ImportError as e:
        LOG.error(f"Stagehand 集成未找到: {e}")
        raise RuntimeError("Stagehand 集成未安装，请检查 core/stagehand_integration.py")


def ai_extract(instruction: str, schema: Optional[Dict] = None, timeout: int = 30000):
    """
    使用 AI 从页面提取结构化数据（Stagehand 风格）
    
    :param instruction: 提取指令，如 "提取用户名和邮箱"
    :param schema: 数据模式（可选）
    :param timeout: 超时时间（毫秒）
    :return: 提取的数据
    """
    driver = get_playwright_driver()
    page = driver.page
    
    # 导入 Stagehand 集成
    try:
        from core.stagehand_integration import StagehandIntegration
        
        # 创建 Stagehand 集成实例
        stagehand = StagehandIntegration(page)
        
        # 提取数据
        result = stagehand.extract(instruction, schema, timeout)
        
        LOG.info(f"AI 提取执行: {instruction}, 成功: {result.get('success', False)}")
        return result
    except ImportError as e:
        LOG.error(f"Stagehand 集成未找到: {e}")
        raise RuntimeError("Stagehand 集成未安装，请检查 core/stagehand_integration.py")


def wait_for_network_idle(timeout: int = 30000):
    """
    等待网络空闲（所有网络请求完成）
    
    这是 Playwright 最佳实践推荐的智能等待方式，替代固定 sleep
    
    :param timeout: 超时时间（毫秒），默认30秒
    :return: True 如果网络空闲
    """
    driver = get_playwright_driver()
    page = driver.page
    
    LOG.info(f"等待网络空闲，超时={timeout}ms")
    
    try:
        # 等待网络空闲（500ms内没有网络请求）
        page.wait_for_load_state("networkidle", timeout=timeout)
        LOG.info("网络已空闲")
        return True
    except Exception as e:
        LOG.warning(f"等待网络空闲超时或失败: {e}")
        return False


def wait_for_url(url_pattern: str, timeout: int = 30000):
    """
    等待 URL 匹配指定模式
    
    :param url_pattern: URL 模式（支持通配符，如 "**/login"）
    :param timeout: 超时时间（毫秒），默认30秒
    :return: True 如果 URL 匹配
    """
    driver = get_playwright_driver()
    page = driver.page
    
    LOG.info(f"等待 URL 匹配: {url_pattern}, 超时={timeout}ms")
    
    try:
        # 等待 URL 匹配
        page.wait_for_url(url_pattern, timeout=timeout)
        LOG.info(f"URL 已匹配: {page.url}")
        return True
    except Exception as e:
        LOG.warning(f"等待 URL 匹配超时或失败: {e}")
        return False


def ai_locate_element(
    description: str,
    element_type: Optional[str] = None,
    context: Optional[str] = None,
    timeout: int = 30000
) -> Optional[str]:
    """
    使用 AI 智能定位元素（Skyvern 风格）
    
    这是 Playwright 最佳实践推荐的智能定位方式，作为固定选择器的备选方案
    
    :param description: 元素描述，如 "登录按钮"、"用户名输入框"
    :param element_type: 元素类型（button/input/select等），可选
    :param context: 上下文信息，可选
    :param timeout: 超时时间（毫秒）
    :return: 元素选择器，如果失败返回 None
    """
    try:
        from tools.skyvern_integration import SkyvernIntegration
        
        driver = get_playwright_driver()
        page = driver.page
        
        integration = SkyvernIntegration(page)
        selector = integration.locate_element(description, element_type, context)
        
        if selector:
            LOG.info(f"AI 定位成功: {description} -> {selector}")
            return selector
        else:
            LOG.warning(f"AI 定位失败: {description}")
            return None
            
    except ImportError as e:
        LOG.warning(f"Skyvern 集成未安装: {e}")
        LOG.info("提示: 安装依赖后可使用 AI 定位功能")
        return None
    except Exception as e:
        LOG.error(f"AI 定位异常: {e}")
        return None


def smart_click(
    selector: str,
    description: Optional[str] = None,
    fallback_to_ai: bool = True
):
    """
    智能点击：优先使用固定选择器，失败时使用 AI 定位（Skyvern 风格）
    
    这是 Playwright 最佳实践推荐的混合定位策略
    
    :param selector: 固定选择器（优先使用）
    :param description: 元素描述（用于 AI 定位备选）
    :param fallback_to_ai: 是否在失败时使用 AI 定位
    :return: 点击结果
    """
    try:
        # 1. 先尝试固定选择器（最稳定）
        return click(selector)
    except Exception as e:
        LOG.warning(f"固定选择器失败: {selector}, 错误: {e}")
        
        if fallback_to_ai and description:
            # 2. 固定选择器失败，使用 AI 定位（备选方案）
            LOG.info(f"尝试使用 AI 定位: {description}")
            ai_selector = ai_locate_element(description)
            
            if ai_selector:
                LOG.info(f"AI 定位成功，使用选择器: {ai_selector}")
                return click(ai_selector)
            else:
                raise Exception(f"固定选择器和 AI 定位都失败: {selector}, {description}")
        else:
            raise e


def dump_html_source(selector: str, label: str = "dump") -> str:
    """
    导出指定元素的 HTML 源码 (outerHTML) 到文件
    :param selector: 元素选择器
    :param label: 文件名标签
    :return: 文件路径
    """
    import os
    driver = get_playwright_driver()
    selector = _parse_selector(selector)
    ctx = driver.current_frame if getattr(driver, 'current_frame', None) else driver.page
    
    LOG.info(f"导出HTML源码: {selector}, 标签={label}")
    
    try:
        # 尝试获取元素
        locator = ctx.locator(selector).first
        locator.wait_for(state='attached', timeout=5000)
        html_content = locator.evaluate("el => el.outerHTML")
        
        # 保存到文件
        base_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "reports", "web")
        out_dir = os.path.join(base_dir, "dom")
        os.makedirs(out_dir, exist_ok=True)
        out_path = os.path.join(out_dir, f"dom_{label}.html")
        
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(f"<!-- Source for selector: {selector} -->\n")
            f.write(html_content)
            
        LOG.info(f"HTML源码已保存: {out_path}")
        return out_path
        
    except Exception as e:
        LOG.error(f"导出HTML源码失败: {e}")
        return ""

