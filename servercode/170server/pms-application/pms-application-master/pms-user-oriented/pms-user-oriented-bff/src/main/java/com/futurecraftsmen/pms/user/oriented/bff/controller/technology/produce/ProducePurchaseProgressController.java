/*
 * Copyright (c) Zhongzao Software Co. LTD 2022-2062 All rights reserved
 */

package com.futurecraftsmen.pms.user.oriented.bff.controller.technology.produce;

import com.futurecraftsmen.pms.bff.domain.ExceptionAndView;
import com.futurecraftsmen.pms.bff.domain.RetVal;
import com.futurecraftsmen.pms.common.domain.ResCode;
import com.futurecraftsmen.pms.pas.api.rpc.lackmaterialtask.LackMaterialTaskConditionForProduceDTO;
import com.futurecraftsmen.pms.pas.api.service.lackmaterialtask.LackMaterialTaskService;
import com.futurecraftsmen.pms.user.oriented.bff.aspect.ApplyPostPageConfig;

import org.aerie.forest.core.brick.log.GlobalLogger;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @description  生产模块-采购进度 相关bff
 *
 * @author chengxinyu
 * @organization futurecraftsmen
 * @date 2024-11-18 14:23
 * @version 3.0.1.300
 */
@RestController
@RequestMapping("/pms/technology/produce")
@Tag(name = "生产模块-采购进度 相关bff")
public class ProducePurchaseProgressController implements GlobalLogger {

    /**
     * @description 采购任务服务
     */
    @DubboReference(check = false,group = "pms")
    private LackMaterialTaskService lackMaterialTaskService;



    /**
     * @description 获取状态可选值
     * @return 响应
     *
     * @author chengxinyu
     * @date 2024-11-3 14:23
     * @initVersion 3.0.1.300
     */
    @GetMapping(path = "/purchase/progress/state")
    @Operation(summary = "状态可选值", description = "状态可选值")
    public RetVal optionalState() {

        try {
            return RetVal.builder(ResCode.SUCCESS_202, "获取成功")
                    .addData(lackMaterialTaskService.optionalStates(true))
                    .build();
        } catch (Exception e) {
            return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
                    ExceptionAndView.INSTANCE.handleExceptionToView(e, "获取失败")).build();
        }
    }



    /**
     * @description 列表数据分页接口
     *
     * @param lackMaterialTaskConditionForProduceDTO
     * @param bindingResult 参数错误
     * @return 响应
     *
     * @author chengxinyu
     * @date 2024-11-3 14:23
     * @initVersion 3.0.1.300
     */
    @PostMapping(path = "/purchase/progress/page")
    @Operation(summary = "采购进度", description = "采购进度")
    @ApplyPostPageConfig(bindPageCodes = {392752692471660546L, 360524985310773248L}, baseCode = 1000019l)
    public RetVal paging(@Validated @RequestBody LackMaterialTaskConditionForProduceDTO lackMaterialTaskConditionForProduceDTO,
                             BindingResult bindingResult) {
        // POJO注解校验参数合法性，不合法返回错误信息
        if (bindingResult.hasErrors()) {
            LOGGER.warn(bindingResult.getFieldError().getDefaultMessage());
            return RetVal.builder(ResCode.PARAMETER_ERROR_501, bindingResult.getFieldError().getDefaultMessage())
                    .buildWithoutInitInfoPenetrateProcessor();
        }
        try {
            return RetVal.builder(ResCode.SUCCESS_202, "获取成功")
                    .addData(lackMaterialTaskService.pagingForProduce(lackMaterialTaskConditionForProduceDTO))
                    .buildWithoutInitInfoPenetrateProcessor();
        } catch (Exception e) {
            return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
                    ExceptionAndView.INSTANCE.handleExceptionToView(e, "获取失败")).buildWithoutInitInfoPenetrateProcessor();
        }
    }


}
