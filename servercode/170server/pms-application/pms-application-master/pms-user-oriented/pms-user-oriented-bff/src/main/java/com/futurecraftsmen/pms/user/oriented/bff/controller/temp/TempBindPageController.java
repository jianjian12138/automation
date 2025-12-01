package com.futurecraftsmen.pms.user.oriented.bff.controller.temp;

import org.aerie.forest.core.brick.log.GlobalLogger;
import org.aerie.forest.core.brick.processor.datapenetrate.GlobalTokenPenetrate;
import org.aerie.forest.core.brick.processor.datapenetrate.InfoPenetrateProcessor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.futurecraftsmen.pms.bff.domain.ExceptionAndView;
import com.futurecraftsmen.pms.bff.domain.RetVal;
import com.futurecraftsmen.pms.common.domain.ResCode;
import com.futurecraftsmen.pms.dm.api.service.base.menu.dto.EnterpriseMenuRpcDTO;
import com.futurecraftsmen.pms.dm.api.service.base.menu.dto.EnterprisePage;
import com.futurecraftsmen.pms.right.api.domain.CustomPageEnum;
import com.futurecraftsmen.pms.right.api.service.EnterpriseMenuConfigService;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/pms/temp/bindpage")
public class TempBindPageController implements GlobalLogger {



    @DubboReference(group = "pms", check = false, retries = 0)
    private EnterpriseMenuConfigService enterpriseMenuConfigService;

    @GetMapping(path = "/customer")
    @Operation(summary = "客户列表页面人员下拉框的绑定页面")
    public RetVal customer() {
        try{
            Long bindPageCode = null;
            long enterpriseCode = InfoPenetrateProcessor.INSTANCE.getPenetrateInfoNotNull(GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL);

            EnterpriseMenuRpcDTO enterpriseMenu = enterpriseMenuConfigService.queryDepartmentConfigForEnterprise(enterpriseCode,false);


            EnterprisePage SALE_QUOTATION = enterpriseMenu.findPage(CustomPageEnum.SALE_QUOTATION.getPageCode());
            if(SALE_QUOTATION!=null){
                bindPageCode = CustomPageEnum.SALE_QUOTATION.getPageCode();
            }


            if(bindPageCode==null){
                EnterprisePage SALE_QUOTATION_XI_HANG = enterpriseMenu.findPage(CustomPageEnum.SALE_QUOTATION_XI_HANG.getPageCode());
                if(SALE_QUOTATION_XI_HANG!=null){
                    bindPageCode = CustomPageEnum.SALE_QUOTATION_XI_HANG.getPageCode();
                }
            }


            if(bindPageCode==null){
                EnterprisePage SALE_CONTRACT = enterpriseMenu.findPage(CustomPageEnum.SALE_CONTRACT.getPageCode());
                if(SALE_CONTRACT!=null){
                    bindPageCode = CustomPageEnum.SALE_CONTRACT.getPageCode();
                }
            }

            if(bindPageCode==null){
                EnterprisePage SALE_CONTRACT_XI_HANG = enterpriseMenu.findPage(CustomPageEnum.SALE_CONTRACT_XI_HANG.getPageCode());
                if(SALE_CONTRACT_XI_HANG!=null){
                    bindPageCode = CustomPageEnum.SALE_CONTRACT_XI_HANG.getPageCode();
                }
            }

            if(bindPageCode==null){
                bindPageCode = CustomPageEnum.SALE_CUSTOMER.getPageCode();
            }

            return RetVal.builder(ResCode.SUCCESS_202, "查询成功")
                    .addData(bindPageCode+"").build();

        } catch (Exception e) {
            return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
                    ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询失败")).build();
        }
    }


    @GetMapping(path = "/vender")
    @Operation(summary = "供应商页面人员下拉框的绑定页面")
    public RetVal vender() {
        try{
            Long bindPageCode = null;
            long enterpriseCode = InfoPenetrateProcessor.INSTANCE.getPenetrateInfoNotNull(GlobalTokenPenetrate.ENTERPRISE_CODE_THREAD_LOCAL);

            EnterpriseMenuRpcDTO enterpriseMenu = enterpriseMenuConfigService.queryDepartmentConfigForEnterprise(enterpriseCode,false);


            EnterprisePage PROCUREMENT_QUOTATION = enterpriseMenu.findPage(CustomPageEnum.PROCUREMENT_QUOTATION.getPageCode());
            if(PROCUREMENT_QUOTATION!=null){
                bindPageCode = CustomPageEnum.PROCUREMENT_QUOTATION.getPageCode();
            }


            if(bindPageCode==null){
                EnterprisePage PROCUREMENT_CONTRACT = enterpriseMenu.findPage(CustomPageEnum.PROCUREMENT_CONTRACT.getPageCode());
                if(PROCUREMENT_CONTRACT!=null){
                    bindPageCode = CustomPageEnum.PROCUREMENT_CONTRACT.getPageCode();
                }
            }


            if(bindPageCode==null){
                bindPageCode = CustomPageEnum.PROCUREMENT_VENDER.getPageCode();
            }

            return RetVal.builder(ResCode.SUCCESS_202, "查询成功")
                    .addData(bindPageCode+"").build();

        } catch (Exception e) {
            return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
                    ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询失败")).build();
        }
    }
}
