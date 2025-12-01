package com.futurecraftsmen.pms.user.oriented.bff.controller.technology.productpart;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.futurecraftsmen.pms.api.dto.base.RpcPagingDTO;
import com.futurecraftsmen.pms.dm.api.service.base.product.PmsProductService;
import com.futurecraftsmen.pms.dm.api.service.base.product.dto.ProductSimpleRpcDTO;
import com.futurecraftsmen.pms.pas.api.rpc.contract.purchase.PurchaseContractRpcDTO;
import com.futurecraftsmen.pms.pas.api.rpc.contract.sell.SellContractRpcDTO;
import com.futurecraftsmen.pms.pas.api.rpc.enquiry.purchase.PurchaseEnquiryRpcDTO;
import com.futurecraftsmen.pms.pas.api.rpc.quotation.sell.NewSellQuotationRpcDTO;
import com.futurecraftsmen.pms.user.oriented.bff.dto.productPart.OptionalProductPartBff;
import com.futurecraftsmen.pms.user.oriented.bff.dto.productPart.OptionalProductPartTypeBff;
import jakarta.validation.Valid;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.log.GlobalLogger;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import com.futurecraftsmen.pms.bff.domain.ErrorFeedback;
import com.futurecraftsmen.pms.bff.domain.ExceptionAndView;
import com.futurecraftsmen.pms.bff.domain.RetVal;
import com.futurecraftsmen.pms.common.domain.ResCode;
import com.futurecraftsmen.pms.pas.api.service.contract.PurchaseContractService;
import com.futurecraftsmen.pms.pas.api.service.contract.SellContractService;
import com.futurecraftsmen.pms.pas.api.service.enquiry.PurchaseEnquiryService;
import com.futurecraftsmen.pms.pas.api.service.quotation.NewSellQuotationService;
import com.futurecraftsmen.pms.technical.api.domain.technical.type.ProductPartTreeNodeRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.technical.type.ProductPartTypeRpcRequest;
import com.futurecraftsmen.pms.technical.api.service.technical.IProductPartTypeService;
import com.futurecraftsmen.pms.user.oriented.bff.form.enumvalue.ProdPartQueryType;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @description 技术部-产品零件分类接口 控制器
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2024/11/20 23:10
 */
@RestController
@Slf4j
@RequestMapping("/pms/pas/product/part")
public class PasProductPartQueryController implements GlobalLogger {

    @DubboReference(group = "pms", check = false)
    private IProductPartTypeService productPartTypeService;



	@DubboReference(group = "pms", check = false, retries = 0)
	private PurchaseEnquiryService purchaseEnquiryService;

	@DubboReference(group = "pms", check = false)
	private PurchaseContractService purchaseContractService;

	@DubboReference(group = "pms", check = false, retries = 0)
	private NewSellQuotationService newSellQuotationService;

	@DubboReference(group = "pms", check = false)
	private SellContractService sellContractService;



	@DubboReference(group = "pmsNew", check = false)
	private PmsProductService pmsProductService;



	/**
	 * 可选分类
	 * @author wuyangtao
	 * @param optionalProductPartTypeBff 入参信息
	 * @return com.futurecraftsmen.pms.bff.domain.RetVal
	 * 2025/09/24 下午5:45
	 */
	@PostMapping(path = "/type/optional")
	public RetVal optionalProductPartType(@Valid @RequestBody OptionalProductPartTypeBff optionalProductPartTypeBff ,
	                                             BindingResult bindingResult) {

		// POJO注解校验参数合法性，不合法返回错误信息
		if (bindingResult.hasErrors()) {
			return RetVal.builder(ResCode.PARAMETER_ERROR_501, bindingResult.getFieldError().getDefaultMessage())
					.build();
		}

		Long entityCode = optionalProductPartTypeBff.getEntityCode();


		int source = optionalProductPartTypeBff.getSource();

		ProdPartQueryType qryType = optionalProductPartTypeBff.getQryType();

		try {
			Long dataSourceEnterpriseCode = dataSourceEnterpriseCode(entityCode,source,qryType);

			if(dataSourceEnterpriseCode==null){
				return RetVal.builder(ResCode.SUCCESS_202, "查询成功").addData(List.of()).build();
			}

			List<ProductPartTreeNodeRpcDTO> data =
					productPartTypeService.getPasProductPartTypeTree(dataSourceEnterpriseCode);
			return RetVal.builder(ResCode.SUCCESS_202, "查询成功").addData(data)
					.build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}



	/**
	 * 可选产品物料
	 * @author wuyangtao
	 * @param optionalProductPartBff 入参信息
	 * @return com.futurecraftsmen.pms.bff.domain.RetVal
	 * 2025/09/24 下午5:45
	 */
	@PostMapping(path = "/optional")
	public RetVal optionalProductPart(@Valid @RequestBody OptionalProductPartBff optionalProductPartBff ,
	                                  BindingResult bindingResult) {

		// POJO注解校验参数合法性，不合法返回错误信息
		if (bindingResult.hasErrors()) {
			return RetVal.builder(ResCode.PARAMETER_ERROR_501, bindingResult.getFieldError().getDefaultMessage())
					.build();
		}

		Long entityCode = optionalProductPartBff.getEntityCode();


		int source = optionalProductPartBff.getSource();

		ProdPartQueryType qryType = optionalProductPartBff.getQryType();

		String keyword = optionalProductPartBff.getKeyword();

		Integer attribute = optionalProductPartBff.getAttribute();

		Long productPartTypeCode = optionalProductPartBff.getProductPartTypeCode();

		try {
			Long dataSourceEnterpriseCode = dataSourceEnterpriseCode(entityCode,source,qryType);
			if(dataSourceEnterpriseCode==null){
				return RetVal.builder(ResCode.SUCCESS_202, "查询成功").addData(new RpcPagingDTO(List.of(),0L)).build();
			}

			Page page = new Page(optionalProductPartBff.getCurrent(),optionalProductPartBff.getSize());
			RpcPagingDTO<ProductSimpleRpcDTO> data =
					pmsProductService.optionalProductsByType(page,dataSourceEnterpriseCode,keyword,attribute,productPartTypeCode);
			return RetVal.builder(ResCode.SUCCESS_202, "查询成功").addData(data)
					.build();
		} catch (Exception e) {
			ErrorFeedback errorView = ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询失败");
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, errorView).build();
		}
	}


	public Long dataSourceEnterpriseCode(Long entityCode,int source,ProdPartQueryType qryType) throws ExceptionPack {
		Long enterPriseCode = null;
		if (ProdPartQueryType.PURCHASE_ENQUIRY == qryType) {
			PurchaseEnquiryRpcDTO purchaseEnquiry = purchaseEnquiryService.innerPurchaseEnquiryTempDetail(entityCode);
			if (1 == source) {   // 供方
				if (purchaseEnquiry.getSeller() == null) {
					return null;
				}
				enterPriseCode = purchaseEnquiry.getSeller().getPmsEnterpriseCode();
			}else if (0 == source){  // 需方
				if (purchaseEnquiry.getBuyer() == null) {
					return null;
				}
				enterPriseCode = purchaseEnquiry.getBuyer().getPmsEnterpriseCode();
			}

		} else if (ProdPartQueryType.PURCHASE_CONTRACT == qryType) {
			PurchaseContractRpcDTO purchaseContract = purchaseContractService.innerPurchaseContractTempDetail(entityCode);
			if (1 == source) {
				if (purchaseContract.getSeller() == null) {
					return null;
				}
				enterPriseCode = purchaseContract.getSeller().getPmsEnterpriseCode();
			} else if (0 == source) {
				if (purchaseContract.getBuyer() == null) {
					return null;
				}
				enterPriseCode = purchaseContract.getBuyer().getPmsEnterpriseCode();
			}

		} else if (ProdPartQueryType.SELL_QUOTATION == qryType) {


			NewSellQuotationRpcDTO sellQuotationRpcDTO = newSellQuotationService.innerSellQuotationTempDetail(entityCode);

			if (sellQuotationRpcDTO.getSeller() == null) {
				return null;
			}

			enterPriseCode = sellQuotationRpcDTO.getSeller().getPmsEnterpriseCode();

		} else if (ProdPartQueryType.SELL_CONTRACT == qryType) {

			SellContractRpcDTO sellContract = sellContractService.innerSellContractTempDetail(entityCode);

			if (sellContract.getSeller() == null) {
				return null;
			}

			enterPriseCode = sellContract.getSeller().getPmsEnterpriseCode();
		}
		return enterPriseCode;
	}
}
