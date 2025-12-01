package com.futurecraftsmen.pms.user.oriented.bff.controller.technology.productpart;

import com.futurecraftsmen.pms.api.dto.base.RpcPagingDTO;
import com.futurecraftsmen.pms.bff.domain.ErrorFeedback;
import com.futurecraftsmen.pms.bff.domain.ExceptionAndView;
import com.futurecraftsmen.pms.bff.domain.RetVal;
import com.futurecraftsmen.pms.common.domain.ResCode;
import com.futurecraftsmen.pms.technical.api.domain.technical.items.ProductPartQualityItemsRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.items.ProductPartQualityItemsRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.items.QualityItemsAddUpdateRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.items.QualityItemsRequest;
import com.futurecraftsmen.pms.technical.api.domain.technical.items.TestMethodRpcDTO;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartQualityItemsService;
import com.futurecraftsmen.pms.user.oriented.bff.aspect.ApplyPostPageConfig;

import org.aerie.forest.core.brick.log.GlobalLogger;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * @description 产品零件质检检验项 控制器
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2024/12/7 10:08
 */
@Slf4j
@RestController
@RequestMapping("/pms/technical/product/part/quality/items/")
public class ProductPartQualityItemsController implements GlobalLogger {

	@DubboReference(group = "pms", check = false)
	private IProductPartQualityItemsService productPartQualityItemsService;

	/**
	 * 查询产品零件质检检验项列表
	 * @author qierkang
	 * @param requestData 入参信息
	 * @return com.futurecraftsmen.pms.bff.domain.RetVal
	 * 2024/12/4 下午8:52
	 */
	@PostMapping(path = "getProductPartQualityItemsList")
	public RetVal getProductPartQualityItemsList(@RequestBody ProductPartQualityItemsRpcRequest requestData) {
		try {
			requestData.setEnable(Boolean.TRUE);
			List<ProductPartQualityItemsRpcDTO> resultList = productPartQualityItemsService.getProductPartQualityItemsList(requestData);
			return RetVal.builder(ResCode.SUCCESS_202, "查询质检成功").addData(resultList)
					.build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询质检失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}

	/**
	 * @description 质检检验项-列表
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/5/8 21:48
	 * @department: Product development
	 */
	@PostMapping(path = "getQualityItemsPageList")
	@ApplyPostPageConfig(bindPageCodes = {365952643951624195L}, baseCode = 900014L)
	public RetVal getQualityItemsPageList(@RequestBody QualityItemsRequest requestData) {
		try {
			RpcPagingDTO<ProductPartQualityItemsRpcDTO> pageList = productPartQualityItemsService.getQualityItemsPageList(requestData);
			return RetVal.builder(ResCode.SUCCESS_202, "质检检验项-列表成功").addData(pageList).buildWithoutInitInfoPenetrateProcessor();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "质检检验项-列表失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}

	/**
	 * @description 质检检验项-新增
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/5/8 21:07
	 * @department: Product development
	 */
	@PostMapping(path = "addQualityItems")
	public RetVal addQualityItems(@RequestBody QualityItemsAddUpdateRequest requestData) {
		try {
			productPartQualityItemsService.addQualityItems(requestData);
			return RetVal.builder(ResCode.SUCCESS_202, "质检检验项-新增成功").build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "质检检验项-新增失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}

	/**
	 * @description 质检检验项-更新
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/5/8 21:11
	 * @department: Product development
	 */
	@PostMapping(path = "updateQualityItems")
	public RetVal updateQualityItems(@RequestBody QualityItemsAddUpdateRequest requestData) {
		try {
			productPartQualityItemsService.updateQualityItems(requestData);
			return RetVal.builder(ResCode.SUCCESS_202, "质检检验项-更新成功").build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "质检检验项-更新失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}

	/**
	 * @description 查询检验方法枚举
	 *
	 * @author qierkang
	 * @organization futurecraftsmen
	 * @date Created in 2025/5/12 20:56
	 * @department: Product development
	 */
	@PostMapping(path = "getTestMethod")
	public RetVal getTestMethod() {
		try {
			List<TestMethodRpcDTO> resultList = productPartQualityItemsService.getTestMethod();
			return RetVal.builder(ResCode.SUCCESS_202, "查询检验方法成功").addData(resultList).build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询检验方法失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}
}
