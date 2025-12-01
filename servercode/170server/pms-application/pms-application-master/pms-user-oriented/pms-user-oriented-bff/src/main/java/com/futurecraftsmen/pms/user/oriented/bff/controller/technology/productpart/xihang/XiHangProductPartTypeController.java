//package com.futurecraftsmen.pms.user.oriented.bff.controller.technology.productpart.xihang;
//
//import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartTypeService;
//
//import org.aerie.forest.core.brick.log.GlobalLogger;
//import org.apache.dubbo.config.annotation.DubboReference;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
///**
// * @description 技术部-产品零件分类接口 控制器
// *
// * @author qierkang
// * @organization futurecraftsmen
// * @date Created in 2025/4/9 19:36
// */
//@RestController
//@RequestMapping("/pms/technical/product/part/type/xihang/")
//public class XiHangProductPartTypeController implements GlobalLogger {
//
//	@DubboReference(group = "pms", check = false)
//	private IProductPartTypeService productPartTypeService;
//
//	/**
//	 * 查询产品零件分类树
//	 * @author qierkang
//	 * @param attribute 入参信息
//	 * @return com.futurecraftsmen.pms.bff.domain.RetVal
//	 * 2024/11/27 下午5:45
//	 */
//	//@GetMapping(path = "getProductPartTypeTreeXh")
//	//public RetVal getProductPartTypeTreeXh(@RequestParam(name = "attribute") int attribute) {
//	//	try {
//	//		ProductPartTreeNodeRpcDTO getProductPartTypeTree =
//	//				productPartTypeService.getProductPartTypeTree(new ProductPartTypeRpcRequest().setAttribute(attribute));
//	//		return RetVal.builder(ResCode.SUCCESS_202, "查询产品零件分类树成功").addData(getProductPartTypeTree)
//	//				.build();
//	//	} catch (Exception e) {
//	//		ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询产品零件分类树失败");
//	//		return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//	//	}
//	//}
//
//	/**
//	 * 新增产品零件分类树
//	 * @author qierkang
//	 * @param requestData 入参信息
//	 * @return com.futurecraftsmen.pms.bff.domain.RetVal
//	 * 2024/11/27 下午5:45
//	 */
//	//@PostMapping(path = "addProductPartTypeXh")
//	//public RetVal addProductPartTypeXh(@RequestBody @Valid ProductPartTypeRpcRequest requestData,
//	//                                 BindingResult bindingResult) {
//	//	if (bindingResult.hasErrors()) {
//	//		String errorMessage = bindingResult.getFieldErrors().stream().map(FieldError::getDefaultMessage)
//	//				.collect(Collectors.joining("; "));
//	//		LOGGER.warn(errorMessage);
//	//		return RetVal.builder(ResCode.PARAMETER_ERROR_501, errorMessage).build();
//	//	} try {
//	//		ProductPartTreeNodeRpcDTO addProductPartType = productPartTypeService.addProductPartType(requestData);
//	//		return RetVal.builder(ResCode.SUCCESS_202, "新增产品零件分类成功").addData(addProductPartType).build();
//	//	} catch (Exception e) {
//	//		ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "新增产品零件分类失败");
//	//		return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//	//	}
//	//}
//
//	/**
//	 * 修改产品零件分类树
//	 * @author qierkang
//	 * @param requestData 入参信息
//	 * @return com.futurecraftsmen.pms.bff.domain.RetVal
//	 * 2024/11/27 下午5:45
//	 */
//	//@PostMapping(path = "updateProductPartTypeXh")
//	//public RetVal updateProductPartTypeXh(@RequestBody @Valid ProductPartTypeRpcRequest requestData,
//	//                                    BindingResult bindingResult) {
//	//	if (bindingResult.hasErrors()) {
//	//		String errorMessage = bindingResult.getFieldErrors().stream().map(FieldError::getDefaultMessage)
//	//				.collect(Collectors.joining("; "));
//	//		LOGGER.warn(errorMessage);
//	//		return RetVal.builder(ResCode.PARAMETER_ERROR_501, errorMessage).build();
//	//	} try {
//	//		productPartTypeService.updateProductPartType(requestData);
//	//		return RetVal.builder(ResCode.SUCCESS_202, "修改产品零件分类成功").build();
//	//	} catch (Exception e) {
//	//		ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "修改产品零件分类失败");
//	//		return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//	//	}
//	//}
//
//	/**
//	 * 删除产品零件分类树
//	 * @author qierkang
//	 * @param requestData 入参信息
//	 * @return com.futurecraftsmen.pms.bff.domain.RetVal
//	 * 2024/11/27 下午5:46
//	 */
//	//@PostMapping(path = "deleteProductPartTypeXh")
//	//public RetVal deleteProductPartTypeXh(@RequestBody @Valid ProductPartTypeRpcRequest requestData,
//	//                                    BindingResult bindingResult) {
//	//	if (bindingResult.hasErrors()) {
//	//		String errorMessage = bindingResult.getFieldErrors().stream().map(FieldError::getDefaultMessage)
//	//				.collect(Collectors.joining("; "));
//	//		LOGGER.warn(errorMessage);
//	//		return RetVal.builder(ResCode.PARAMETER_ERROR_501, errorMessage).build();
//	//	} try {
//	//		productPartTypeService.deleteProductPartType(requestData);
//	//		return RetVal.builder(ResCode.SUCCESS_202, "删除产品零件分类成功").build();
//	//	} catch (Exception e) {
//	//		ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "删除产品零件分类失败");
//	//		return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//	//	}
//	//}
//}
