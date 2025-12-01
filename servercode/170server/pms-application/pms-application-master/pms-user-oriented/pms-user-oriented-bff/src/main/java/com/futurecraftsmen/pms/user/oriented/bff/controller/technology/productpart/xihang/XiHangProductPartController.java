//package com.futurecraftsmen.pms.user.oriented.bff.controller.technology.productpart.xihang;
//
//import com.futurecraftsmen.pms.bff.domain.ErrorFeedback;
//import com.futurecraftsmen.pms.bff.domain.ExceptionAndView;
//import com.futurecraftsmen.pms.bff.domain.RetVal;
//import com.futurecraftsmen.pms.common.domain.ResCode;
//import com.futurecraftsmen.pms.technical.api.domain.technical.procedure.ProductPartProcedureRpcRequest;
//import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartRpcRequest;
//import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.ProductPartUpdateRpcRequest;
//import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.ProductPartConsistDTO;
//import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.ProductPartConsistTreeDTO;
//import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.comp.ProductPartCompRequest;
//import com.futurecraftsmen.pms.technical.api.domain.technical.productpart.xihang.comp.ProductPartConsistRequest;
//import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartActionService;
//import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartCommonService;
//import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartProcedureService;
//import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartService;
//
//import org.aerie.forest.core.brick.log.GlobalLogger;
//import org.apache.dubbo.config.annotation.DubboReference;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.List;
//
//import jakarta.annotation.Resource;
//import lombok.extern.slf4j.Slf4j;
//
///**
// * @description 技术部-产品零件接口 控制器
// *
// * @author qierkang
// * @organization futurecraftsmen
// * @date Created in 2025/4/9 19:31
// */
//@Slf4j
//@RestController
//@RequestMapping("/pms/technical/product/part/xihang/")
//public class XiHangProductPartController implements GlobalLogger {
//
//	@DubboReference(group = "pms", check = false, timeout = 20000)
//	private IProductPartCommonService productPartCommonService;
//	@DubboReference(group = "pms", check = false, timeout = 20000)
//	private IProductPartService productPartService;
//	@Resource
//	private IProductPartProcedureService productPartProcedureService;
//	@DubboReference(group = "pms", check = false, timeout = 20000)
//	private IProductPartActionService productPartActionService;
//
//	/**
//	 * @description 分页查询产品零件-希航
//	 *
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2025/4/9 17:04
//	 */
//	//@PostMapping(path = "getPageListXh")
//	//public RetVal getPageListXh(@Valid @RequestBody ProductPartPageRequest requestData) {
//	//	try {
//	//		RpcPagingDTO<?> pageList = productPartActionService.getPageList(requestData);
//	//		return RetVal.builder(ResCode.SUCCESS_202, "分页查询产品零件成功").addData(pageList).build();
//	//	} catch (Exception e) {
//	//		ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "分页查询产品零件失败");
//	//		return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//	//	}
//	//}
//
//	/**
//	 * @description 新增产品零件-希航
//	 *
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2025/4/9 19:26
//	 */
//	//@PostMapping(path = "addProductPartXh")
//	//public RetVal addProductPartXh(@RequestBody @Valid ProductPartAddRpcRequest requestData) {
//	//	try {
//	//		ProductPartRpcDTO productPartRpcDTO = productPartActionService.addProductPart(requestData);
//	//		return RetVal.builder(ResCode.SUCCESS_202, "新增产品零件成功").addData(productPartRpcDTO).build();
//	//	} catch (Exception e) {
//	//		ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "新增产品零件失败");
//	//		return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//	//	}
//	//}
//
//	/**
//	 * @description 更新产品零件-希航
//	 *
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2025/4/9 19:27
//	 */
//	//@PostMapping(path = "updateProductPartXh")
//	//public RetVal updateProductPartXh(@RequestBody @Valid ProductPartUpdateRpcRequest requestData) {
//	//	try {
//	//		productPartActionService.updateProductPart(requestData);
//	//		return RetVal.builder(ResCode.SUCCESS_202, "修改产品零件成功").build();
//	//	} catch (Exception e) {
//	//		ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "修改产品零件失败");
//	//		return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//	//	}
//	//}
//
//	/**
//	 * @description 删除产品零件-希航
//	 *
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2025/4/9 19:27
//	 */
//	//@PostMapping(path = "deleteProductPartXh")
//	//public RetVal deleteProductPartXh(@RequestBody @Valid ProductPartRpcRequest requestData) {
//	//	try {
//	//		productPartActionService.deleteProductPart(requestData);
//	//		return RetVal.builder(ResCode.SUCCESS_202, "删除产品零件成功").build();
//	//	} catch (Exception e) {
//	//		ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "删除产品零件失败");
//	//		return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//	//	}
//	//}
//
//	/**
//	 * @description 复制产品零件-希航
//	 *
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2025/4/9 19:28
//	 */
//	//@PostMapping(path = "productPartCopyXh")
//	//public RetVal productPartCopyXh(@RequestBody @Valid ProductPartRpcRequest requestData) {
//	//	try {
//	//		ProductPartRpcDTO productPartRpcDTO = productPartActionService.productPartCopy(requestData);
//	//		return RetVal.builder(ResCode.SUCCESS_202, "复制产品零件成功").addData(productPartRpcDTO).build();
//	//	} catch (Exception e) {
//	//		ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "复制产品零件失败");
//	//		return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//	//	}
//	//}
//
//	/**
//	 * @description 产品零件批量编辑-希航
//	 *
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2025/4/9 19:29
//	 */
//	//@PostMapping(path = "productPartBatchUpdateXh")
//	//public RetVal productPartBatchUpdateXh(@RequestBody @Valid ProductPartUpdateRpcRequest requestData) {
//	//	try {
//	//		productPartActionService.productPartBatchUpdate(requestData);
//	//		return RetVal.builder(ResCode.SUCCESS_202, "产品零件批量编辑成功").build();
//	//	} catch (Exception e) {
//	//		ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "产品零件批量编辑失败");
//	//		return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//	//	}
//	//}
//
//	/**
//	 * @description 产品零件批量新增
//	 *
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2024/12/12 22:03
//	 */
//	//@PostMapping(path = "batchAddProductPartXh")
//	//public RetVal batchAddProductPartXh(@RequestBody List<ProductPartBatchAddRpcRequestXh> requestListData) {
//	//	try {
//	//		productPartActionService.batchAddProductPart(requestListData);
//	//		return RetVal.builder(ResCode.SUCCESS_202, "产品零件批量新增成功").build();
//	//	} catch (Exception e) {
//	//		ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "产品零件批量新增失败");
//	//		return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//	//	}
//	//}
//
//	/**
//	 * @description 查询产品、零件详细信息-希航
//	 *
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2025/4/9 19:30
//	 */
//	@PostMapping(path = "productPartDetailXh")
//	public RetVal productPartDetailXh(@RequestBody ProductPartRpcRequest requestListData) {
//		try {
//			return RetVal.builder(ResCode.SUCCESS_202, "查询产品、零件详细信息成功")
//					.addData(productPartService.productPartDetail(requestListData)).build();
//		} catch (Exception e) {
//			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询产品、零件详细信息失败");
//			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//		}
//	}
//
//	/**
//	 * @description 产品零件-根据文件编号删除图纸-希航
//	 *
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2025/4/9 19:31
//	 */
//	@PostMapping(path = "productPartDrawingDeleteXh")
//	public RetVal productPartDrawingDeleteXh(@RequestBody ProductPartRpcRequest requestData) {
//		try {
//			productPartService.productPartDrawingDelete(requestData);
//			return RetVal.builder(ResCode.SUCCESS_202, "产品零件-根据文件编号删除图纸-成功").build();
//		} catch (Exception e) {
//			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "产品零件-根据文件编号删除图纸-失败");
//			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//		}
//	}
//
//	/**
//	 * @description 产品、零件详情查询零件列表
//	 *
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2025/2/16 16:07
//	 */
//	@PostMapping(path = "getProcedurePartDetailListXh")
//	public RetVal getProcedurePartDetailListXh(@RequestBody ProductPartProcedureRpcRequest requestData) {
//		try {
//			return RetVal.builder(ResCode.SUCCESS_202, "产品、零件详情查询零件列表成功")
//					.addData(productPartProcedureService.getSupportingPartList(requestData)).build();
//		} catch (Exception e) {
//			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "产品、零件详情查询零件列表失败");
//			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//		}
//	}
//
//	/**
//	 * 绑定检验项目
//	 * @author qierkang
//	 * @param requestData 入参信息
//	 * @return com.futurecraftsmen.pms.bff.domain.RetVal
//	 * 2024/12/4 下午8:52
//	 */
//	@PostMapping(path = "bindingQualityItemsXh")
//	public RetVal bindingQualityItemsXh(@RequestBody ProductPartUpdateRpcRequest requestData) {
//		try {
//			productPartService.bindingQualityItems(requestData);
//			return RetVal.builder(ResCode.SUCCESS_202, "绑定检验项目成功").build();
//		} catch (Exception e) {
//			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "绑定检验项目失败");
//			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//		}
//	}
//
//	/**
//	 * @description 产品批量导入校验
//	 *
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2024/12/10 下午8:41
//	 */
//	//@PostMapping(path = "productAnalyzeExcelXh")
//	//public RetVal productAnalyzeExcelXh(@RequestParam(name = "file") MultipartFile file) {
//	//	try {
//	//		ParseExcelResult<?> resultList =
//	//				productPartActionService.
//	//						productAnalyzeExcel(new MultipartFileRpcDTO(file.getOriginalFilename(), file.getName(), file.getSize(),
//	//								file.getBytes()));
//	//		return RetVal.builder(ResCode.SUCCESS_202, "产品批量导入校验成功").addData(resultList).build();
//	//	} catch (Exception e) {
//	//		ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "产品批量导入校验失败");
//	//		return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//	//	}
//	//}
//
//	/**
//	 * @description 零件批量导入校验
//	 *
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2024/12/12 22:47
//	 */
//	//@PostMapping(path = "partAnalyzeExcelXh")
//	//public RetVal partAnalyzeExcelXh(@RequestParam(name = "file") MultipartFile file) {
//	//	try {
//	//		ParseExcelResult<?> resultList = productPartActionService.
//	//				partAnalyzeExcel(new MultipartFileRpcDTO(file.getOriginalFilename(), file.getName(), file.getSize(), file.getBytes()));
//	//		return RetVal.builder(ResCode.SUCCESS_202, "零件批量导入校验成功").addData(resultList).build();
//	//	} catch (Exception e) {
//	//		ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "零件批量导入校验失败");
//	//		return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//	//	}
//	//}
//
//	/**
//	 * @description 根据产品查询零件组成-正查询
//	 * 解释：根据产品编号查询有哪些零件组成
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2025/4/9 16:29
//	 */
//	@PostMapping(path = "getProductConsistListXh")
//	public RetVal getProductConsistListXh(@RequestBody ProductPartCompRequest requestData) {
//		try {
//			List<ProductPartConsistDTO> productConsistList = productPartCommonService.getProductConsistListXh(requestData);
//			return RetVal.builder(ResCode.SUCCESS_202, "产品查询成功").addData(productConsistList).build();
//		} catch (Exception e) {
//			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "产品查询失败");
//			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//		}
//	}
//
//	/**
//	 * @description 根据零件查询组成哪些产品-反查询
//	 * 解释：根据零件编号查询可以组成哪些产品
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2025/4/9 16:30
//	 */
//	@PostMapping(path = "getPartConsistListXh")
//	public RetVal getPartConsistListXh(@RequestBody ProductPartCompRequest requestData) {
//		try {
//			List<ProductPartConsistDTO> partConsistList = productPartCommonService.getProductConsistListXh(requestData);
//			return RetVal.builder(ResCode.SUCCESS_202, "零件查询成功").addData(partConsistList).build();
//		} catch (Exception e) {
//			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "零件查询失败");
//			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//		}
//	}
//
//	/**
//	 * @description 产品零件绑定关系
//	 *
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2025/4/10 20:53
//	 */
//	@PostMapping(path = "productPartConsistXh")
//	public RetVal productPartConsist(@RequestBody ProductPartConsistRequest requestData) {
//		try {
//			productPartCommonService.productPartConsistXh(requestData);
//			return RetVal.builder(ResCode.SUCCESS_202, "产品零件绑定关系成功").build();
//		} catch (Exception e) {
//			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "产品零件绑定关系失败");
//			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//		}
//	}
//
//	/**
//	 * @description 根据产品查询零件组成-正查询-树形结构
//	 * 解释：根据产品编号查询有哪些零件组成
//	 * @author qierkang
//	 * @organization futurecraftsmen
//	 * @date Created in 2025/4/9 16:29
//	 */
//	@PostMapping(path = "productPartConsistTreeFormXh")
//	public RetVal productPartConsistTreeFormXh(@RequestBody ProductPartCompRequest requestData) {
//		try {
//			List<ProductPartConsistTreeDTO> productConsistList = productPartCommonService.productPartConsistTreeFormXh(requestData);
//			return RetVal.builder(ResCode.SUCCESS_202, "BOM查询成功").addData(productConsistList).build();
//		} catch (Exception e) {
//			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "BOM查询失败");
//			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
//		}
//	}
//}
