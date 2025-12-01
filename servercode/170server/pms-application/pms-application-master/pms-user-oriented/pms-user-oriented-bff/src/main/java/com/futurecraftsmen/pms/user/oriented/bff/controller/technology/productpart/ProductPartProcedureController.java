package com.futurecraftsmen.pms.user.oriented.bff.controller.technology.productpart;

import com.futurecraftsmen.pms.api.dto.MultipartFileRpcDTO;
import com.futurecraftsmen.pms.api.dto.base.RpcPagingDTO;
import com.futurecraftsmen.pms.bff.domain.ErrorFeedback;
import com.futurecraftsmen.pms.bff.domain.ExceptionAndView;
import com.futurecraftsmen.pms.bff.domain.RetVal;
import com.futurecraftsmen.pms.common.domain.ResCode;
import com.futurecraftsmen.pms.common.domain.excel.ParseExcelResult;
import com.futurecraftsmen.pms.technical.api.domain.technical.procedure.ProcedureBatchAddRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.procedure.ProcedureExcelRpcModel;
import com.futurecraftsmen.pms.technical.api.domain.technical.procedure.ProductPartProcedureRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.procedure.ProductPartProcedureRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.procedure.SalesOrderProcedureRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.procedure.SalesOrderProcedureRpcRequest;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartProcedureService;
import com.futurecraftsmen.pms.user.oriented.bff.aspect.ApplyPostPageConfig;

import org.aerie.forest.core.brick.log.GlobalLogger;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * @description 产品零件工序 控制器
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2024/12/5 07:33
 */
@Slf4j
@RestController
@RequestMapping("/pms/technical/product/part/procedure/")
public class ProductPartProcedureController implements GlobalLogger {

	@DubboReference(group = "pms", check = false,timeout = 30000)
	private IProductPartProcedureService productPartProcedureService;

	/**
	 * 查询工序列表
	 * @author qierkang
	 * @date Created in 2024/12/5 下午6:37
	 * @title AutoTestSupportController.java
	 * Department: AutoTestSupportController
	 */
	@ApplyPostPageConfig(bindPageCodes = {351921110379003906L}, baseCode = 1001009L)
	@PostMapping(path = "productPartProcedurePageList")
	public RetVal productPartProcedurePageList(@RequestBody ProductPartProcedureRpcRequest requestData, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			String errorMessage = bindingResult.getFieldErrors().stream().map(FieldError::getDefaultMessage)
					.collect(Collectors.joining("; "));
			log.warn(errorMessage);
			return RetVal.builder(ResCode.PARAMETER_ERROR_501, errorMessage).build();
		} try {
			RpcPagingDTO<ProductPartProcedureRpcDTO> resultList = productPartProcedureService.productPartProcedurePageList(requestData);
			return RetVal.builder(ResCode.SUCCESS_202, "查询工序列表成功").addData(resultList).build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询工序列表失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}

	/**
	 * 新增工序
	 * @author qierkang
	 * @param requestData 入参信息
	 * @return com.futurecraftsmen.pms.bff.domain.RetVal
	 * 2024/12/5 下午6:38
	 */
	@PostMapping(path = "addProductPartProcedure")
    public RetVal addProductPartProcedure(@RequestBody ProductPartProcedureRpcRequest requestData) {
        try {
			productPartProcedureService.addProductPartProcedure(requestData);
			return RetVal.builder(ResCode.SUCCESS_202, "新增工序成功").build();
		} catch (Exception e) {
            ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, e.getMessage());
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}

	/**
	 * 更新工序
	 * @author qierkang
	 * @param requestData 入参信息
	 * @return com.futurecraftsmen.pms.bff.domain.RetVal
	 * 2024/12/5 下午6:38
	 */
	@PostMapping(path = "updateProductPartProcedure")
    public RetVal updateProductPartProcedure(@RequestBody ProductPartProcedureRpcRequest requestData) {
        try {
			productPartProcedureService.updateProductPartProcedure(requestData);
			return RetVal.builder(ResCode.SUCCESS_202, "更新工序成功").build();
		} catch (Exception e) {
            ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, e.getMessage());
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}

	/**
	 * 复制工序
	 * @author qierkang
	 * @param requestData 入参信息
	 * @return com.futurecraftsmen.pms.bff.domain.RetVal
	 * 2024/12/5 下午6:39
	 */
	@PostMapping(path = "copyProductPartProcedure")
	public RetVal copyProductPartProcedure(@RequestBody ProductPartProcedureRpcRequest requestData, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			String errorMessage = bindingResult.getFieldErrors().stream().map(FieldError::getDefaultMessage)
					.collect(Collectors.joining("; "));
			log.warn(errorMessage);
			return RetVal.builder(ResCode.PARAMETER_ERROR_501, errorMessage).build();
		} try {
			ProductPartProcedureRpcDTO resultDto = productPartProcedureService.copyProductPartProcedure(requestData);
			return RetVal.builder(ResCode.SUCCESS_202, "复制工序成功").addData(resultDto).build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "复制工序失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}

	/**
	 * 批量修改工序
	 * @author qierkang
	 * @param requestData 入参信息
	 * @return com.futurecraftsmen.pms.bff.domain.RetVal
	 * 2024/12/6 下午4:24
	 */
	@PostMapping(path = "batchUpdateProductPartProcedure")
	public RetVal batchUpdateProductPartProcedure(@RequestBody ProductPartProcedureRpcRequest requestData, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			String errorMessage = bindingResult.getFieldErrors().stream().map(FieldError::getDefaultMessage)
					.collect(Collectors.joining("; "));
			log.warn(errorMessage);
			return RetVal.builder(ResCode.PARAMETER_ERROR_501, errorMessage).build();
		} try {
			productPartProcedureService.batchUpdateProductPartProcedure(requestData);
			return RetVal.builder(ResCode.SUCCESS_202, "批量修改工序成功").build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "批量修改工序失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}

	/**
	 * @description 工序批量导入校验
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/8 下午4:01
	 */
	@PostMapping(path = "procedureAnalyzeExcel")
	public RetVal procedureAnalyzeExcel(@RequestParam(name = "file") MultipartFile file) {
		try {
			ParseExcelResult<ProcedureExcelRpcModel> resultList = productPartProcedureService.
					procedureAnalyzeExcel(new MultipartFileRpcDTO(file.getOriginalFilename(), file.getName(), file.getSize(), file.getBytes()));
			return RetVal.builder(ResCode.SUCCESS_202, "工序批量导入校验成功").addData(resultList).build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "工序批量导入校验失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}

	/**
	 * @description 批量新增工序
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/8 下午6:07
	 */
	@PostMapping(path = "batchAddProductPartProcedure")
	public RetVal batchAddProductPartProcedure(@RequestBody List<ProcedureBatchAddRpcRequest> requestListData, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			String errorMessage = bindingResult.getFieldErrors().stream().map(FieldError::getDefaultMessage)
					.collect(Collectors.joining("; "));
			log.warn(errorMessage);
			return RetVal.builder(ResCode.PARAMETER_ERROR_501, errorMessage).build();
		} try {
			productPartProcedureService.batchAddProductPartProcedure(requestListData);
			return RetVal.builder(ResCode.SUCCESS_202, "批量新增工序成功").build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "批量新增工序失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}

	/**
	 * @description 绑定工序检验项目
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/10 下午4:44
	 */
	@PostMapping(path = "bindingProcedureQualityItems")
	public RetVal bindingProcedureQualityItems(@RequestBody ProductPartProcedureRpcRequest requestData, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			String errorMessage = bindingResult.getFieldErrors().stream().map(FieldError::getDefaultMessage)
					.collect(Collectors.joining("; "));
			log.warn(errorMessage);
			return RetVal.builder(ResCode.PARAMETER_ERROR_501, errorMessage).build();
		} try {
			productPartProcedureService.bindingProcedureQualityItems(requestData);
			return RetVal.builder(ResCode.SUCCESS_202, "绑定工序检验项目成功").build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "绑定工序检验项目失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}

	/**
	 * @description 工序号查询绑定零件信息
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2024/12/12 22:03
	 */
	@PostMapping(path = "getProcedurePartList")
	public RetVal getProcedurePartList(@RequestBody ProductPartProcedureRpcRequest requestData, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			String errorMessage = bindingResult.getFieldErrors().stream().map(FieldError::getDefaultMessage)
					.collect(Collectors.joining("; "));
			log.warn(errorMessage);
			return RetVal.builder(ResCode.PARAMETER_ERROR_501, errorMessage).build();
		} try {
			return RetVal.builder(ResCode.SUCCESS_202, "工序号查询绑定零件信息成功")
					.addData(productPartProcedureService.getProcedurePartList(requestData)).build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "工序号查询绑定零件信息失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}

	/**
	 * @description 销售订单查询-工序信息
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/2/11 15:47
	 */
	@PostMapping(path = "getSalesOrderProcedureList")
	public RetVal getSalesOrderProcedureList(@RequestBody SalesOrderProcedureRpcRequest requestData) {
		try {
			List<SalesOrderProcedureRpcDTO> getSalesOrderProcedureList = productPartProcedureService.getSalesOrderProcedureList(requestData);
			return RetVal.builder(ResCode.SUCCESS_202, "查询工序列表成功").addData(getSalesOrderProcedureList).build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询工序列表失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}

	/**
	 * @description 删除产品零件工序
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/5/19 18:38
	 * @department: Product development
	 */
	@PostMapping(path = "deleteProductPartProcedure")
	public RetVal deleteProductPartProcedure(@RequestBody ProductPartProcedureRpcRequest requestData) {
		try {
			productPartProcedureService.deleteProductPartProcedure(requestData);
			return RetVal.builder(ResCode.SUCCESS_202, "删除产品零件工序成功").build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, e.getMessage());
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}
}
