    def execute_case(self, case_path: str) -> Dict:
        """
        执行测试用例（增强版，支持数据驱动测试）
        
        :param case_path: 用例文件路径
        :return: 执行结果
        """
        case = self.load_case(case_path)
        LOG.info(f"开始执行用例: {case.get('case_name', 'Unknown')}")
        
        # 清空之前的数据记录
        self.inserted_data = []
        
        # 初始化结果
        results = {
            'case_name': case.get('case_name'),
            'case_code': case.get('case_code'),
            'total_steps': 0,
            'passed_steps': 0,
            'failed_steps': 0,
            'step_results': [],
            'data_driven': False,
            'data_driven_results': []
        }
        
        # 获取测试数据
        test_data = case.get('data', [])
        
        # 检查是否为数据驱动测试
        if test_data:
            LOG.info(f"开始数据驱动测试，共 {len(test_data)} 组数据")
            results['data_driven'] = True
            
            # 执行每组测试数据
            for data_index, data in enumerate(test_data, 1):
                LOG.info(f"执行第 {data_index}/{len(test_data)} 组数据: {data}")
                
                # 保存原始变量，执行完后恢复
                original_variables = self.variables.copy()
                
                # 设置当前组测试数据到变量中
                self.variables.update(data)
                
                # 执行当前组数据的测试用例
                data_results = self._execute_single_case(case)
                
                # 记录当前组数据的结果
                data_results['data_index'] = data_index
                data_results['test_data'] = data
                results['data_driven_results'].append(data_results)
                
                # 合并结果
                results['total_steps'] += data_results['total_steps']
                results['passed_steps'] += data_results['passed_steps']
                results['failed_steps'] += data_results['failed_steps']
                results['step_results'].extend(data_results['step_results'])
                
                # 恢复原始变量
                self.variables = original_variables
        else:
            # 普通测试用例
            results = self._execute_single_case(case)
        
        results['passed'] = results['failed_steps'] == 0
        LOG.info(f"用例执行完成: {results['passed_steps']}/{results['total_steps']} 通过")
        
        return results
        
    def _execute_single_case(self, case: Dict) -> Dict:
        """
        执行单个测试用例（内部方法，用于数据驱动测试）
        
        :param case: 测试用例字典
        :return: 执行结果
        """
        # 清空之前的数据记录
        self.inserted_data = []
        
        results = {
            'case_name': case.get('case_name'),
            'case_code': case.get('case_code'),
            'total_steps': len(case.get('steps', [])),
            'passed_steps': 0,
            'failed_steps': 0,
            'step_results': []
        }
        
        try:
            for step in case.get('steps', []):
                # 检查是否需要跳过步骤（条件判断）
                skip_when = step.get('skip_when')
                if skip_when:
                    step_name = step.get('step_name', 'Unknown Step')
                    LOG.debug(f"评估跳过条件: {step_name}, 条件: {skip_when}, 当前productCode值: {self.variables.get('productCode')}")
                    should_skip = self._evaluate_skip_condition(skip_when)
                    LOG.debug(f"跳过条件评估结果: {should_skip}")
                    if should_skip:
                        LOG.info(f"跳过步骤（条件满足）: {step_name}, 条件: {skip_when}")
                        step_result = {
                            'step_name': step_name,
                            'passed': True,
                            'errors': [],
                            'duration': 0.0,
                            'skipped': True,
                            'skip_reason': skip_when
                        }
                        results['step_results'].append(step_result)
                        results['passed_steps'] += 1
                        continue
                
                # 检查是否需要重试直到条件满足
                retry_until = step.get('retry_until')
                if retry_until:
                    # 创建条件检查函数
                    def condition_check(result):
                        """检查重试条件是否满足"""
                        try:
                            # 支持多种条件格式
                            if isinstance(retry_until, dict):
                                # 字典格式：检查变量是否有值
                                var_name = retry_until.get('variable')
                                if var_name:
                                    var_value = self.variables.get(var_name)
                                    # 检查变量是否有值（不为None、空字符串、空列表）
                                    if var_value is not None and var_value != '' and var_value != []:
                                        return True
                                # 检查JSONPath表达式
                                jsonpath_expr = retry_until.get('jsonpath')
                                if jsonpath_expr:
                                    response = result.get('response')
                                    if response:
                                        try:
                                            response_dict = response.json() if hasattr(response, 'json') else response
                                            # 支持比较操作符（如 $..resCode == 200）
                                            expected_value = None
                                            actual_jsonpath_expr = jsonpath_expr
                                            if '==' in jsonpath_expr:
                                                # 分离JSONPath表达式和比较值
                                                import re
                                                parts = re.split(r'\s*==\s*', jsonpath_expr, 1)
                                                if len(parts) == 2:
                                                    actual_jsonpath_expr = parts[0].strip()
                                                    try:
                                                        # 尝试解析期望值（支持数字和字符串）
                                                        expected_value_str = parts[1].strip()
                                                        if expected_value_str.isdigit():
                                                            expected_value = int(expected_value_str)
                                                        elif expected_value_str.replace('.', '', 1).isdigit():
                                                            expected_value = float(expected_value_str)
                                                        else:
                                                            expected_value = expected_value_str.strip('"').strip("'")
                                                    except Exception:
                                                        expected_value = parts[1].strip()
                                            
                                            jsonpath_expr_parsed = parse(actual_jsonpath_expr)
                                            matches = [match.value for match in jsonpath_expr_parsed.find(response_dict)]
                                            if matches and matches[0] is not None:
                                                actual_val = matches[0]
                                                # 如果指定了期望值，检查是否等于期望值
                                                if expected_value is not None:
                                                    # 尝试转换为相同类型进行比较
                                                    try:
                                                        if isinstance(actual_val, str) and isinstance(expected_value, (int, float)):
                                                            actual_val = int(actual_val) if isinstance(expected_value, int) else float(actual_val)
                                                        elif isinstance(actual_val, (int, float)) and isinstance(expected_value, str):
                                                            expected_value = int(expected_value) if expected_value.isdigit() else float(expected_value)
                                                        if actual_val == expected_value:
                                                            return True
                                                    except (ValueError, TypeError):
                                                        # 如果转换失败，直接比较
                                                        if actual_val == expected_value:
                                                            return True
                                                # 如果没有指定期望值，只检查值是否存在
                                                elif actual_val != '' and actual_val != []:
                                                    return True
                                        except Exception as e:
                                            LOG.debug(f"检查JSONPath条件失败: {e}")
                            elif isinstance(retry_until, str):
                                # 字符串格式：直接检查变量是否有值
                                var_value = self.variables.get(retry_until)
                                if var_value is not None and var_value != '' and var_value != []:
                                    return True
                            return False
                        except Exception as e:
                            LOG.error(f"检查重试条件失败: {e}")
                            return False
                    
                    step_result = self._retry_step_until_condition(step, condition_check)
                else:
                    step_result = self._execute_step(step)
                results['step_results'].append(step_result)
                
                # 跟踪插入的数据
                if self.enable_cleanup:
                    self._track_inserted_data(step, step_result)
                
                if step_result['passed']:
                    results['passed_steps'] += 1
                else:
                    results['failed_steps'] += 1
        finally:
            # 执行数据清理
            if self.enable_cleanup:
                self._cleanup_inserted_data()
                
        results['passed'] = results['failed_steps'] == 0
        return results