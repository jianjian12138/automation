/*
 * @(#)com.futurecraftsmen.pms.user.oriented.bff.controller.collaborate 2025/7/3 17:04
 * @Author <a href="mailto:xyqierkang@gmail.com">ErKang Qi</a>
 * @Blog：https://www.qekang.com
 * Copyright (c) 2019-2025 Shanghai
 * All rights reserved.

 * This software is the confidential and proprietary information of
 * You shall not disclose such Confidential Information and shall use
 * it only in accordance with the terms of the license agreement
 */
package com.futurecraftsmen.pms.user.oriented.bff.controller.collaborate.warehouse;

import static com.futurecraftsmen.pms.user.oriented.bff.util.PdfParam.PDF_TEXT_FONT_SIZE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.log.GlobalLogger;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.fastjson2.JSON;
import com.beust.jcommander.internal.Maps;
import com.futurecraftsmen.pms.bff.domain.ExceptionAndView;
import com.futurecraftsmen.pms.bff.domain.RetVal;
import com.futurecraftsmen.pms.common.domain.ResCode;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.inbound.CollaborateInboundPageRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.inbound.CollaborateInboundPageRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.inbound.ContractCollaborateInboundOperation;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.lacktask.LackTaskSellOrderBatchItemInfo;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.outbound.CollaborateOutboundPageRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.outbound.CollaborateOutboundPageRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.outbound.CollaborateStockInfoRpcRequest;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.outbound.ContractCollaborateOutboundOperation;
import com.futurecraftsmen.pms.technical.api.domain.warehouse.InOrOutOperationDetailRecordPageRequest;
import com.futurecraftsmen.pms.technical.api.domain.warehouse.InOrOutOperationDetailRecordRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.warehouse.WarehouseInboundDetailPageRpcRequest;
import com.futurecraftsmen.pms.technical.api.service.collaborate.CollaborateInboundService;
import com.futurecraftsmen.pms.technical.api.service.collaborate.CollaborateOutboundService;
import com.futurecraftsmen.pms.technical.api.service.collaborate.CollaborateScheduleItemService;
import com.futurecraftsmen.pms.user.oriented.bff.aspect.ApplyPostPageConfig;
import com.futurecraftsmen.pms.user.oriented.bff.domain.pdf.PdfColumnMetadata;
import com.futurecraftsmen.pms.user.oriented.bff.domain.pdf.PdfPrintRequest;
import com.futurecraftsmen.pms.user.oriented.bff.dto.collaborate.CollaborateScheduleItemsReturn;
import com.futurecraftsmen.pms.user.oriented.bff.dto.collaborate.lacktask.QueryLackTaskSellOrderBatchItemInfo;
import com.futurecraftsmen.pms.user.oriented.bff.service.pdf.PdfPrintService;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.futurecraftsmen.pms.user.oriented.bff.util.DynamicExcelExporter;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;
import org.aerie.forest.core.brick.log.GlobalLogger;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

/**
 * @description 协作安排出入库
 *
 * @author zhanglijia
 * @organization futurecraftsmen
 * @date Created in 2025/7/3 17:04
 * @department: Product development
 */
@Slf4j
@RestController
@RequestMapping("/pms/collaborate/warehouse")
public class CollaborateWarehouseController implements GlobalLogger {

	@Value("${systemData.detailExport.hjNewInboundDetail}")
	private String inboundDetailExportUrl;

	@Value("${systemData.detailExport.hjNewOutboundDetail}")
	private String outboundDetailExportUrl;

	@Value("${systemData.detailExport.hjNewInboundPageList}")
	private String hjNewInboundPageListExportUrl;

	@Value("${systemData.detailExport.hjNewOutboundProductList}")
	private String hjNewOutboundProductListExportUrl;

	@Value("${systemData.detailExport.hjNewOutboundProductionMaterialList}")
	private String hjNewOutboundProductionMaterialListExportUrl;

	@Value("${systemData.detailExport.hjNewOutboundOutSendMaterialsList}")
	private String hjNewOutboundOutSendMaterialsListExportUrl;

	@DubboReference(group = "pms", check = false)
	private CollaborateScheduleItemService collaborateScheduleItemService;

	@DubboReference(group = "pms", check = false)
	private CollaborateInboundService collaborateInboundService;

	@DubboReference(group = "pms",check = false)
	private CollaborateOutboundService collaborateOutboundService;

	/**
	 * @description PDF 打印服务
	 */
	@jakarta.annotation.Resource
	private PdfPrintService pdfPrintService;


	@PostMapping(path = "/inbound/getPageList")
	@Operation(summary = "新版入库列表", description = "新版入库列表")
	@ApplyPostPageConfig(bindPageCodes = {428375581212147712L}, baseCode = 600116L)
	public RetVal getPageList(@RequestBody CollaborateInboundPageRpcRequest request) {
		try {
			return RetVal.builder(ResCode.SUCCESS_202, "查询列表成功").addData(collaborateInboundService.getPageList(request)).buildWithoutInitInfoPenetrateProcessor();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询列表失败")).build();
		}
	}

	@PostMapping(path = "/inbound/getListByContractCode")
	@Operation(summary = "入库列表-产品物料", description = "入库列表-产品物料")
	public RetVal getListByContractCode(@RequestBody CollaborateInboundPageRpcRequest request) {
		try {
			return RetVal.builder(ResCode.SUCCESS_202, "查询产品物料成功").addData(collaborateInboundService.getListByContractCode(request)).build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询产品物料失败")).build();
		}
	}

	@GetMapping(path = "/inbound/getListByDataKey")
	@Operation(summary = "入库列表-产品物料 申请信息", description = "入库列表-产品物料 申请信息")
	public RetVal getListByDataKey(@RequestParam(name = "dataKey") Long dataKey) {
		try {
			return RetVal.builder(ResCode.SUCCESS_202, "查询产品物料申请信息成功").addData(collaborateInboundService.getListByDataKey(dataKey)).build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询产品物料申请信息失败")).build();
		}
	}

	@PostMapping(path = "/inbound/inStockOperation")
	@Operation(summary = "入库", description = "入库")
	public RetVal inStockOperation(@RequestBody ContractCollaborateInboundOperation request) {
		try {
			collaborateInboundService.inStockOperation(request);
			return RetVal.builder(ResCode.SUCCESS_202, "入库成功").build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "入库失败")).build();
		}
	}

	@PostMapping(path = "/inbound/selfInboundOperation")
	@Operation(summary = "自主入库", description = "自主入库")
	public RetVal selfInboundOperation(@RequestBody ContractCollaborateInboundOperation request) {
		try {
			collaborateInboundService.selfInboundOperation(request);
			return RetVal.builder(ResCode.SUCCESS_202, "入库成功").build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "入库失败")).build();
		}
	}

	@PostMapping(path = "/inbound/getInStockDetailPageList")
	@Operation(summary = "航舰新版权益--入库明细列表", description = "航舰新版权益--入库明细列表")
	@ApplyPostPageConfig(bindPageCodes = {428375581212147712L}, baseCode = 600117L)
	public RetVal getInStockDetailPageList(@RequestBody InOrOutOperationDetailRecordPageRequest request) {
		try {
			return RetVal.builder(ResCode.SUCCESS_202, "查询列表成功").addData(collaborateInboundService.getInStockDetailPageList(request)).buildWithoutInitInfoPenetrateProcessor();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询列表失败")).build();
		}
	}

	@PostMapping(path = "/batchReturn")
	@Operation(summary = "销售订单批次维度数据退回", description = "销售订单批次维度数据退回")
	public RetVal sellOrderBatchReturn(@Valid @RequestBody CollaborateScheduleItemsReturn request, BindingResult bindingResult) {
		try {
			// POJO注解校验参数合法性，不合法返回错误信息
			if (bindingResult.hasErrors()) {
				log.warn(bindingResult.getFieldError().getDefaultMessage());
				return RetVal.builder(ResCode.PARAMETER_ERROR_501, bindingResult.getFieldError().getDefaultMessage())
						.build();
			}
			String rejectReason = "已被仓管退回啦~";
			collaborateScheduleItemService.returnCollaborateScheduleItems(request.getSellOrderCode(),rejectReason);
			return RetVal.builder(ResCode.SUCCESS_202, "退回成功").build();
		} catch (ExceptionPack e) {

			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, e.getException().getMessage())).build();
		}
	}

	@PostMapping(path = "/inbound/getInStockDetailPageListExport")
	@Operation(summary = "入库明细列表-导出", description = "入库明细列表-导出")
	public void getInStockDetailPageListExport(@Valid @RequestBody InOrOutOperationDetailRecordPageRequest requestData, BindingResult bindingResult, HttpServletResponse response) throws IOException {
		try {
			// 获取导出字段配置（使用工具类方法）
			List<String> exportColumns = DynamicExcelExporter.extractExportColumns(requestData);

			// 查询入库明细数据
			List<InOrOutOperationDetailRecordRpcDTO> list = collaborateInboundService.getInStockDetailPageListExport(requestData);

			// 如果exportColumns不为空，使用动态导出
			if (CollUtil.isNotEmpty(exportColumns)) {
				if (CollUtil.isEmpty(list)) {
					response.getWriter().write("无导出数据");
					return;
				}

				String randomSuffix = RandomUtil.randomString(3);
				String fileName = StrUtil.format("入库明细{}.xlsx", randomSuffix);

				// 设置响应头
				String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
				response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName);
				response.setContentType("application/vnd.ms-excel;charset=utf-8");
				response.setCharacterEncoding("UTF-8");

				OutputStream outputStream = new ByteArrayOutputStream();

				// 使用基于接口配置的简化导出方法，无需DTO类型适配
				DynamicExcelExporter.exportWebByApi(list, outputStream,
					"getInStockDetailPageListExport", exportColumns, "入库明细导出", null);

				byte[] byteArray = ((ByteArrayOutputStream) outputStream).toByteArray();
				response.getOutputStream().write(byteArray);
				response.getOutputStream().flush();
				response.getOutputStream().close();

				return;
			}

			// 原有的模板导出方式（不修改）
			// 设置响应头
			String fileName = URLEncoder.encode("入库明细.xlsx", "UTF-8").replaceAll("\\+", "%20");
			response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
			response.setContentType("application/vnd.ms-excel;charset=utf-8");
			response.setCharacterEncoding("UTF-8");

			Resource resource= new UrlResource(inboundDetailExportUrl);
			try (InputStream inputStream = resource.getInputStream(); ExcelWriter excelWriter = EasyExcel
					.write(response.getOutputStream(), InOrOutOperationDetailRecordRpcDTO.class).withTemplate(inputStream).build()) {
				WriteSheet writeSheet = EasyExcel.writerSheet().build();
				excelWriter.fill(list, writeSheet);
			} catch (Exception e) {
				throw new ExceptionPack(e, ExceptionMsg.builder("failed to export getInboundDetailPageListExport ").build());
			}
		} catch (Exception e) {
			response.getOutputStream().write(JSON.toJSONBytes(RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "入库明细列表-导出失败")).build()));
			response.getOutputStream().close();
		}
	}

	/**
	 * @description 新版出库列表
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 16:56
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/getPageList")
	@Operation(summary = "新版出库列表", description = "新版出库列表")
	@ApplyPostPageConfig(bindPageCodes = {428375439578890240L}, baseCode = 600118L)
	public RetVal getPageList(@RequestBody CollaborateOutboundPageRpcRequest request) {
		try {
			return RetVal.builder(ResCode.SUCCESS_202, "查询列表成功").addData(collaborateOutboundService.getPageList(request)).buildWithoutInitInfoPenetrateProcessor();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询列表失败")).build();
		}
	}

	/**
	 * @description 出库详情-产品
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 16:57
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/getProductListByContractCode")
	@Operation(summary = "出库详情-产品", description = "出库详情-产品")
	public RetVal getProductListByContractCode(@RequestBody CollaborateOutboundPageRpcRequest request) {
		try {
			return RetVal.builder(ResCode.SUCCESS_202, "查询列表成功").addData(collaborateOutboundService.getProductListByContractCode(request)).build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询列表失败")).build();
		}
	}

	/**
	 * @description 出库详情-物料
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 17:00
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/getProductionMaterialsListByContractCode")
	@Operation(summary = "出库详情-物料", description = "出库详情-物料")
	public RetVal getProductionMaterialsListByContractCode(@RequestBody CollaborateOutboundPageRpcRequest request) {
		try {
			return RetVal.builder(ResCode.SUCCESS_202, "查询列表成功").addData(collaborateOutboundService.getProductionMaterialsListByContractCode(request)).build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询列表失败")).build();
		}
	}

	/**
	 * @description 配料列表
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 17:13
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/getProductionSubMaterials")
	@Operation(summary = "配料列表", description = "配料列表")
	public RetVal getProductionSubMaterials(@RequestBody CollaborateOutboundPageRpcRequest request) {
		try {
			return RetVal.builder(ResCode.SUCCESS_202, "获取成功").addData(collaborateOutboundService.getProductionSubMaterials(request.getCollaborateCode())).build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "获取失败")).build();
		}
	}

	/**
	 * @description 出库详情-外发物料
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 17:08
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/getOutSendMaterialsListByContractCode")
	@Operation(summary = "出库详情-外发物料", description = "出库详情-外发物料")
	public RetVal getOutSendMaterialsListByContractCode(@RequestBody CollaborateOutboundPageRpcRequest request) {
		try {
			return RetVal.builder(ResCode.SUCCESS_202, "查询列表成功").addData(collaborateOutboundService.getOutSendMaterialsListByContractCode(request)).build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询列表失败")).build();
		}
	}


	/**
	 * @description  产品物料申请信息
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 17:10
	 * @department: Product development
	 */
	@GetMapping(path = "/outbound/getListByDataKey")
	@Operation(summary = "出库列表-产品物料 申请信息", description = "出库列表-产品物料 申请信息")
	public RetVal getOutListByDataKey(@RequestParam(name = "dataKey") Long dataKey) {
		try {
			return RetVal.builder(ResCode.SUCCESS_202, "查询产品物料申请信息成功").addData(collaborateOutboundService.getListByDataKey(dataKey)).build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询产品物料申请信息失败")).build();
		}
	}

//	/**
//	 * @description 获取库存信息
//	 *
//	 * @author wuchun
//	 * @organization futurecraftsmen
//	 * @date Created in 2025/7/15 17:11
//	 * @department: Product development
//	 */
//	@PostMapping(path = "/outbound/getStockInfo")
//	@Operation(summary = "获取库存信息", description = "获取库存信息")
//	public RetVal getStockInfo(@RequestBody CollaborateStockInfoRpcRequest request) {
//		try {
//			return RetVal.builder(ResCode.SUCCESS_202, "获取库存信息成功").addData(collaborateOutboundService.getStockInfo(request)).build();
//		} catch (Exception e) {
//			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
//					ExceptionAndView.INSTANCE.handleExceptionToView(e, "获取库存信息失败")).build();
//		}
//	}



	/**
	 * @description 单个出库
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 17:13
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/singleOutbound")
	@Operation(summary = "单个出库", description = "单个出库")
	public RetVal singleOutbound(@RequestBody ContractCollaborateOutboundOperation request) {
		try {
			if (request.isReceiveMaterial()) {
				collaborateOutboundService.receiveMaterialSingleOutbound(request, true);
			} else {
				collaborateOutboundService.singleOutbound(request);
			}
			return RetVal.builder(ResCode.SUCCESS_202, "出库成功").build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "出库失败")).build();
		}
	}

	/**
	 * @description 一键出库
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 17:13
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/batchOutbound")
	@Operation(summary = "一键出库", description = "一键出库")
	public RetVal batchOutbound(@RequestBody List<ContractCollaborateOutboundOperation>  request) {
		try {
			collaborateOutboundService.batchOutbound(request);
			return RetVal.builder(ResCode.SUCCESS_202, "一键出库成功").build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "一键出库失败")).build();
		}
	}

	/**
	 * @description 自主出库
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 17:13
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/selfOutbound")
	@Operation(summary = "自主出库", description = "自主出库")
	public RetVal selfOutbound(@RequestBody ContractCollaborateOutboundOperation request) {
		try {
			collaborateOutboundService.selfOutbound(request);
			return RetVal.builder(ResCode.SUCCESS_202, "出库成功").build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "出库失败")).build();
		}
	}

	/**
	 * @description 配料出库
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 17:13
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/ingredientOutbound")
	@Operation(summary = "配料出库", description = "配料出库")
	public RetVal ingredientOutbound(@RequestBody List<ContractCollaborateOutboundOperation> request) {
		try {
			collaborateOutboundService.ingredientOutbound(request);
			return RetVal.builder(ResCode.SUCCESS_202, "出库成功").build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "出库失败")).build();
		}
	}


	/**
	 * @description 获取对应库存的仓位及仓位库存批次信息
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 17:16
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/getStockPositionBatchInfo")
	@Operation(summary = "获取对应库存的仓位及仓位库存批次信息", description = "获取对应库存的仓位及仓位库存批次信息")
	public RetVal getStockPositionBatchInfo(@RequestBody CollaborateStockInfoRpcRequest request) {
		try {
			return RetVal.builder(ResCode.SUCCESS_202, "获取对应库存的仓位及仓位库存批次信息成功").addData(collaborateOutboundService.getStockPositionBatchInfo(request)).build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "获取对应库存的仓位及仓位库存批次信息失败")).build();
		}
	}

	/**
	 * @description 出库明细列表
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 17:17
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/getOutStockDetailPageList")
	@Operation(summary = "出库明细列表", description = "出库明细列表")
	@ApplyPostPageConfig(bindPageCodes = {428375439578890240L}, baseCode = 600119L)
	public RetVal getOutStockDetailPageList(@RequestBody InOrOutOperationDetailRecordPageRequest request) {
		try {
			return RetVal.builder(ResCode.SUCCESS_202, "查询出库明细列表成功").addData(collaborateOutboundService.getOutStockDetailPageList(request)).buildWithoutInitInfoPenetrateProcessor();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询出库明细列表失败")).build();
		}
	}

	/**
	 * @description 出库明细列表-导出
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/15 17:19
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/getOutStockDetailPageListExport")
	@Operation(summary = "出库明细列表-导出", description = "出库明细列表-导出")
	public void getOutStockDetailPageListExport(@Valid @RequestBody InOrOutOperationDetailRecordPageRequest requestData, BindingResult bindingResult, HttpServletResponse response) throws IOException {
		try {
			// 获取导出字段配置（使用工具类方法）
			List<String> exportColumns = DynamicExcelExporter.extractExportColumns(requestData);

			// 查询出库明细数据
			List<InOrOutOperationDetailRecordRpcDTO> list = collaborateOutboundService.getOutStockDetailPageListExport(requestData);

			// 如果exportColumns不为空，使用动态导出
			if (CollUtil.isNotEmpty(exportColumns)) {
				if (CollUtil.isEmpty(list)) {
					response.getWriter().write("无导出数据");
					return;
				}

				String randomSuffix = RandomUtil.randomString(3);
				String fileName = StrUtil.format("出库明细{}.xlsx", randomSuffix);

				// 设置响应头
				String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
				response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName);
				response.setContentType("application/vnd.ms-excel;charset=utf-8");
				response.setCharacterEncoding("UTF-8");

				OutputStream outputStream = new ByteArrayOutputStream();

				// 使用基于接口配置的简化导出方法，无需DTO类型适配
				DynamicExcelExporter.exportWebByApi(list, outputStream,
					"getOutStockDetailPageListExport", exportColumns, "出库明细导出", null);

				byte[] byteArray = ((ByteArrayOutputStream) outputStream).toByteArray();
				response.getOutputStream().write(byteArray);
				response.getOutputStream().flush();
				response.getOutputStream().close();

				return;
			}

			// 原有的模板导出方式（不修改）
			// 设置响应头
			String fileName = URLEncoder.encode("出库明细.xlsx", "UTF-8").replaceAll("\\+", "%20");
			response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
			response.setContentType("application/vnd.ms-excel;charset=utf-8");
			response.setCharacterEncoding("UTF-8");

			Resource resource= new UrlResource(outboundDetailExportUrl);
			try (InputStream inputStream = resource.getInputStream(); ExcelWriter excelWriter = EasyExcel
					.write(response.getOutputStream(), InOrOutOperationDetailRecordRpcDTO.class).withTemplate(inputStream).build()) {
				WriteSheet writeSheet = EasyExcel.writerSheet().build();
				excelWriter.fill(list, writeSheet);
			} catch (Exception e) {
				throw new ExceptionPack(e, ExceptionMsg.builder("failed to export getOutboundDetailPageListExport ").build());
			}
		} catch (Exception e) {
			response.getOutputStream().write(JSON.toJSONBytes(RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "出库明细列表-导出失败")).build()));
			response.getOutputStream().close();
		}
	}


	/**
	 * @description 新版航舰-入库详情下载
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/16 15:01
	 * @department: Product development
	 */
	@PostMapping(path = "/inbound/getListByContractCodeExport")
	@Operation(summary = "入库列表-产品物料-导出", description = "入库列表-产品物料-导出")
	public void getListByContractCodeExport(@Valid @RequestBody CollaborateInboundPageRpcRequest requestData, HttpServletResponse response) throws IOException {
		response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("新-入库列表.xls", "UTF-8"));
		response.setContentType("application/vnd.ms-excel;charset=utf-8");
		response.setCharacterEncoding("UTF-8");
		try {
			List<CollaborateInboundPageRpcDTO> list = collaborateInboundService.getListByContractCodeExport(requestData);
			Resource resource = new UrlResource(hjNewInboundPageListExportUrl);
			try (InputStream inputStream = resource.getInputStream(); ExcelWriter excelWriter = EasyExcel
					.write(response.getOutputStream(), CollaborateInboundPageRpcDTO.class).withTemplate(inputStream).build();) {
				WriteSheet writeSheet = EasyExcel.writerSheet().build();
				Map<String, Object> map = Maps.newHashMap();
				map.put("contractNumber", list.getFirst().getContractNumber());
				excelWriter.fill(map, writeSheet);
				excelWriter.fill(list, writeSheet);
			} catch (Exception e) {
				throw new ExceptionPack(e, ExceptionMsg.builder("failed to batch export getListByContractCodeExport").build());
			}
		} catch (Exception e) {
			response.getOutputStream().write(JSON.toJSONBytes(RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "导出入库列表数据失败")).build()));
			response.getOutputStream().close();
		}
	}

	/**
	 *
	 * @description 打印入库列表
	 * @param printRequest 入库任务code
	 * @return
	 *
	 * @author wuchun
	 * @date 2025/2/7 15:49
	 */
	@PostMapping("/inbound/getListByContractCodeExport/print")
	@Operation(summary = "打印入库列表", description = "打印入库列表")
	public void print(@Valid @RequestBody PdfPrintRequest<CollaborateInboundPageRpcRequest> printRequest, HttpServletResponse response) {
		try {
			List<CollaborateInboundPageRpcDTO> detailExportList = collaborateInboundService.getListByContractCodeExport(printRequest.getQueryParams());
			// 设置副标题（单号）
			String subtitle = "单号：" + detailExportList.getFirst().getContractNumber();
			printRequest.setSubtitle(subtitle);
			pdfPrintService.printPdf(
					CollaborateInboundPageRpcDTO.class,
					detailExportList,
					printRequest,
					response,
					"入库列表"
			);
		} catch (Exception e) {
			try {
				response.getOutputStream().write(JSON.toJSONBytes(RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
						ExceptionAndView.INSTANCE.handleExceptionToView(e, "打印入库任务详情列表数据失败")).build()));
				response.getOutputStream().close();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	private PdfPTable genInPdf(Font font, List<CollaborateInboundPageRpcDTO> detailExportList) throws DocumentException {
		// 创建表格
		PdfPTable table = new PdfPTable(8);
		float marginLeft = 2;
		float marginRight = 2;
		float tableWidth = PageSize.A4.rotate().getWidth() - marginLeft - marginRight;
		table.setWidthPercentage(tableWidth * 100 / PageSize.A4.rotate().getWidth()); // 设置表格宽度百分比
		float[] columnWidths = {2.0f,4.0f, 4.0f,4.0f,2.5f, 2.0f,2.5f, 4.0f};
		table.setWidths(columnWidths);

		// 添加表头
		String[] headers = {"序号","编号","名称", "型号规格","需入总量","单位", "申请入库量", "入库情况"};
		for (String header : headers) {
			PdfPCell cell = new PdfPCell(new Phrase(header, font));
			cell.setHorizontalAlignment(Element.ALIGN_CENTER);
			cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
			cell.setNoWrap(false); // 允许文字换行
			cell.setPaddingBottom(5f); // 设置单元格底部间距
			table.addCell(cell);
		}
		for (CollaborateInboundPageRpcDTO dto : detailExportList) {
			String[] data = {
					dto.getXh().toString(),
					dto.getUnityNo(),
					dto.getName(),
					dto.getModel(),
					dto.getOrderQuantity().stripTrailingZeros().toPlainString(),
					dto.getPcsChn(),
					dto.getApplyNum().stripTrailingZeros().toPlainString(),
					dto.getInventoryStatus()
			};
			for (String value : data) {
				PdfPCell cell = new PdfPCell(new Phrase(value, font));
				cell.setHorizontalAlignment(Element.ALIGN_CENTER);
				cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
				cell.setNoWrap(false); // 允许文字换行
				cell.setPaddingBottom(5f); // 设置单元格底部间距
				table.addCell(cell);
			}
		}
		return table;
	}


	/**
	 * @description 新版航舰-产品出库下载
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/16 16:36
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/getProductListByContractCodeExport")
	@Operation(summary = "出库列表-产品物料-导出", description = "出库列表-产品物料-导出")
	public void getProductListByContractCodeExport(@Valid @RequestBody CollaborateOutboundPageRpcRequest requestData, HttpServletResponse response) throws IOException {
		response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("新-产品出库列表.xls", "UTF-8"));
		response.setContentType("application/vnd.ms-excel;charset=utf-8");
		response.setCharacterEncoding("UTF-8");
		try {
			List<CollaborateOutboundPageRpcDTO> list = collaborateOutboundService.getProductListByContractCodeExport(requestData);
			Resource resource = new UrlResource(hjNewOutboundProductListExportUrl);
			try (InputStream inputStream = resource.getInputStream(); ExcelWriter excelWriter = EasyExcel
					.write(response.getOutputStream(), CollaborateOutboundPageRpcDTO.class).withTemplate(inputStream).build();) {
				WriteSheet writeSheet = EasyExcel.writerSheet().build();
				Map<String, Object> map = Maps.newHashMap();
				map.put("contractNumber", list.getFirst().getContractNumber());
				excelWriter.fill(map, writeSheet);
				excelWriter.fill(list, writeSheet);
			} catch (Exception e) {
				throw new ExceptionPack(e, ExceptionMsg.builder("failed to batch export getProductListByContractCodeExport").build());
			}
		} catch (Exception e) {
			response.getOutputStream().write(JSON.toJSONBytes(RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "下载产品出库列表数据失败")).build()));
			response.getOutputStream().close();
		}
	}


	/**
	 * @description 打印产品出库列表
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/16 16:59
	 * @department: Product development
	 */
	@PostMapping("/outbound/getProductListByContractCodeExport/print")
	@Operation(summary = "打印产品出库列表", description = "打印产品出库列表")
	public void productPrint(@Valid @RequestBody PdfPrintRequest<CollaborateOutboundPageRpcRequest> printRequest, HttpServletResponse response) {
		try {
			List<CollaborateOutboundPageRpcDTO> detailExportList = collaborateOutboundService.getProductListByContractCodeExport(printRequest.getQueryParams());

			// 设置副标题（单号）
			String subtitle = "单号：" + detailExportList.getFirst().getContractNumber();
			printRequest.setSubtitle(subtitle);
			printRequest.setGroup("product");

			pdfPrintService.printPdf(
					CollaborateOutboundPageRpcDTO.class,
					detailExportList,
					printRequest,
					response,
					"产品出库列表"
			);
		} catch (Exception e) {
			try {
				response.getOutputStream().write(JSON.toJSONBytes(RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
						ExceptionAndView.INSTANCE.handleExceptionToView(e, "打印产品出库数据失败")).build()));
				response.getOutputStream().close();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	private PdfPTable genProductOutPdf(Font font, List<CollaborateOutboundPageRpcDTO> detailExportList) throws DocumentException {
		// 创建表格
		PdfPTable table = new PdfPTable(9);
		float marginLeft = 2;
		float marginRight = 2;
		float tableWidth = PageSize.A4.rotate().getWidth() - marginLeft - marginRight;
		table.setWidthPercentage(tableWidth * 100 / PageSize.A4.rotate().getWidth()); // 设置表格宽度百分比
		float[] columnWidths = {2.0f,4.0f, 4.0f,4.0f,2.5f, 2.0f,2.5f,2.5f, 4.0f};
		table.setWidths(columnWidths);

		// 添加表头
		String[] headers = {"序号","产品编号","产品名称", "产品型号","需出总量","单位", "申请出库量", "成品库存","出库情况"};
		for (String header : headers) {
			PdfPCell cell = new PdfPCell(new Phrase(header, font));
			cell.setHorizontalAlignment(Element.ALIGN_CENTER);
			cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
			cell.setNoWrap(false); // 允许文字换行
			cell.setPaddingBottom(5f); // 设置单元格底部间距
			table.addCell(cell);
		}
		for (CollaborateOutboundPageRpcDTO dto : detailExportList) {
			String[] data = {
					dto.getXh(),
					dto.getUnityNo(),
					dto.getName(),
					dto.getModel(),
					dto.getOrderQuantity().stripTrailingZeros().toPlainString(),
					dto.getPcsChn(),
					dto.getApplyNum().stripTrailingZeros().toPlainString(),
					dto.getTotalInventory().stripTrailingZeros().toPlainString(),
					dto.getOutboundSituation()
			};
			for (String value : data) {
				PdfPCell cell = new PdfPCell(new Phrase(value, font));
				cell.setHorizontalAlignment(Element.ALIGN_CENTER);
				cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
				cell.setNoWrap(false); // 允许文字换行
				cell.setPaddingBottom(5f); // 设置单元格底部间距
				table.addCell(cell);
			}
		}
		return table;
	}

	/**
	 * @description 新版航舰-生产物料出库下载
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/16 16:36
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/getProductionMaterialsListByContractCodeExport")
	@Operation(summary = "出库列表-生产物料-导出", description = "出库列表-生产物料-导出")
	public void getProductionMaterialsListByContractCodeExport(@Valid @RequestBody CollaborateOutboundPageRpcRequest requestData, HttpServletResponse response) throws IOException {
		response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("新-生产物料出库列表.xls", "UTF-8"));
		response.setContentType("application/vnd.ms-excel;charset=utf-8");
		response.setCharacterEncoding("UTF-8");
		try {
			List<CollaborateOutboundPageRpcDTO> list = collaborateOutboundService.getProductionMaterialsListByContractCodeExport(requestData);
			Resource resource = new UrlResource(hjNewOutboundProductionMaterialListExportUrl);
			try (InputStream inputStream = resource.getInputStream(); ExcelWriter excelWriter = EasyExcel
					.write(response.getOutputStream(), CollaborateOutboundPageRpcDTO.class).withTemplate(inputStream).build();) {
				WriteSheet writeSheet = EasyExcel.writerSheet().build();
				Map<String, Object> map = Maps.newHashMap();
				map.put("contractNumber", list.getFirst().getContractNumber());
				excelWriter.fill(map, writeSheet);
				excelWriter.fill(list, writeSheet);
			} catch (Exception e) {
				throw new ExceptionPack(e, ExceptionMsg.builder("failed to batch export getProductListByContractCodeExport").build());
			}
		} catch (Exception e) {
			response.getOutputStream().write(JSON.toJSONBytes(RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "下载生产物料数据失败")).build()));
			response.getOutputStream().close();
		}
	}


	/**
	 * @description 打印产品出库列表
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/16 16:59
	 * @department: Product development
	 */
	@PostMapping("/outbound/getProductionMaterialsListByContractCodeExport/print")
	@Operation(summary = "打印生产物料出库列表", description = "打印生产物料出库列表")
	public void productionMaterialsPrint(@Valid @RequestBody PdfPrintRequest<CollaborateOutboundPageRpcRequest> printRequest, HttpServletResponse response) {
		try {
			List<CollaborateOutboundPageRpcDTO> detailExportList = collaborateOutboundService.getProductionMaterialsListByContractCodeExport(printRequest.getQueryParams());

			printRequest.setTitle("生产物料出库列表");
			// 设置副标题（单号）
			String subtitle = "单号：" + detailExportList.getFirst().getContractNumber();
			printRequest.setSubtitle(subtitle);
			printRequest.setGroup("material_production");

			pdfPrintService.printPdf(
					CollaborateOutboundPageRpcDTO.class,
					detailExportList,
					printRequest,
					response,
					"生产物料出库列表"
			);

		} catch (Exception e) {
			try {
				response.getOutputStream().write(JSON.toJSONBytes(RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
						ExceptionAndView.INSTANCE.handleExceptionToView(e, "打印生产物料数据失败")).build()));
				response.getOutputStream().close();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	private PdfPTable genProductionMaterialsOutPdf(Font font, List<CollaborateOutboundPageRpcDTO> detailExportList) throws DocumentException {
		// 创建表格
		PdfPTable table = new PdfPTable(11);
		float marginLeft = 2;
		float marginRight = 2;
		float tableWidth = PageSize.A4.rotate().getWidth() - marginLeft - marginRight;
		table.setWidthPercentage(tableWidth * 100 / PageSize.A4.rotate().getWidth()); // 设置表格宽度百分比
		float[] columnWidths = {2.0f,4.0f,4.0f,4.0f,2.5f,2.0f,2.5f,2.0f,2.5f,2.5f,2.0f};
		table.setWidths(columnWidths);

		// 添加表头
		String[] headers = {"序号","物料编号","物料名称", "物料型号","需求量","单位", "物料库存","在途量","已收未入量","申请出库量","已出量"};
		for (String header : headers) {
			PdfPCell cell = new PdfPCell(new Phrase(header, font));
			cell.setHorizontalAlignment(Element.ALIGN_CENTER);
			cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
			cell.setNoWrap(false); // 允许文字换行
			cell.setPaddingBottom(5f); // 设置单元格底部间距
			table.addCell(cell);
		}
		for (CollaborateOutboundPageRpcDTO dto : detailExportList) {
			String[] data = {
					dto.getXh(),
					dto.getUnityNo(),
					dto.getName(),
					dto.getModel(),
					dto.getOrderQuantity().stripTrailingZeros().toPlainString(),
					dto.getPcsChn(),
					dto.getTotalInventory().stripTrailingZeros().toPlainString(),
					dto.getOnWayNum().stripTrailingZeros().toPlainString(),
					dto.getReceivingNoIntoStockNum().stripTrailingZeros().toPlainString(),
					dto.getApplyNum().stripTrailingZeros().toPlainString(),
					dto.getOutboundQuantity().stripTrailingZeros().toPlainString()
			};
			for (String value : data) {
				PdfPCell cell = new PdfPCell(new Phrase(value, font));
				cell.setHorizontalAlignment(Element.ALIGN_CENTER);
				cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
				cell.setNoWrap(false); // 允许文字换行
				cell.setPaddingBottom(5f); // 设置单元格底部间距
				table.addCell(cell);
			}
		}
		return table;
	}

	/**
	 * @description 新版航舰-外发物料出库下载
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/16 16:36
	 * @department: Product development
	 */
	@PostMapping(path = "/outbound/getOutSendMaterialsListByContractCodeExport")
	@Operation(summary = "出库列表-外发物料-导出", description = "出库列表-外发物料-导出")
	public void getOutSendMaterialsListByContractCodeExport(@Valid @RequestBody CollaborateOutboundPageRpcRequest requestData, HttpServletResponse response) throws IOException {
		response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("新-外发物料出库列表.xls", "UTF-8"));
		response.setContentType("application/vnd.ms-excel;charset=utf-8");
		response.setCharacterEncoding("UTF-8");
		try {
			List<CollaborateOutboundPageRpcDTO> list = collaborateOutboundService.getOutSendMaterialsListByContractCodeExport(requestData);
			Resource resource = new UrlResource(hjNewOutboundOutSendMaterialsListExportUrl);
			try (InputStream inputStream = resource.getInputStream(); ExcelWriter excelWriter = EasyExcel
					.write(response.getOutputStream(), CollaborateOutboundPageRpcDTO.class).withTemplate(inputStream).build();) {
				WriteSheet writeSheet = EasyExcel.writerSheet().build();
				Map<String, Object> map = Maps.newHashMap();
				map.put("contractNumber", list.getFirst().getContractNumber());
				excelWriter.fill(map, writeSheet);
				excelWriter.fill(list, writeSheet);
			} catch (Exception e) {
				throw new ExceptionPack(e, ExceptionMsg.builder("failed to batch export getOutSendMaterialsListByContractCodeExport").build());
			}
		} catch (Exception e) {
			response.getOutputStream().write(JSON.toJSONBytes(RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "下载外发物料数据失败")).build()));
			response.getOutputStream().close();
		}
	}

	/**
	 * 出库列打印
	 * 获取可用的 PDF 打印列配置
	 * 前端可以通过此接口获取所有可打印的列，然后自由选择要打印哪些列
	 * @return 可用的列配置
	 */
	@PostMapping("/outbound/print/columns")
	@Operation(summary = "获取可用的 PDF 打印列配置", description = "获取可用的 PDF 打印列配置")
	public RetVal getOutboundPrintColumns(@Valid @RequestBody PdfPrintRequest<?> printRequest) {
		try {
			List<PdfColumnMetadata> columns = pdfPrintService.getAvailableColumns(CollaborateOutboundPageRpcDTO.class, printRequest.getGroup());
			return RetVal.builder(ResCode.SUCCESS_202, "获取可用的 PDF 打印列配置成功").addData(columns).build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "获取可用的 PDF 打印列配置失败")).build();
		}
	}

	/**
	 * 入库列打印
	 * 获取可用的 PDF 打印列配置
	 * 前端可以通过此接口获取所有可打印的列，然后自由选择要打印哪些列
	 * @return 可用的列配置
	 */
	@PostMapping("/inbound/print/columns")
	@Operation(summary = "获取可用的 PDF 打印列配置", description = "获取可用的 PDF 打印列配置")
	public RetVal getInboundPrintColumns(@RequestBody PdfPrintRequest<WarehouseInboundDetailPageRpcRequest> printRequest) {
		try {
			List<PdfColumnMetadata> columns = pdfPrintService.getAvailableColumns(CollaborateInboundPageRpcDTO.class, null);
			return RetVal.builder(ResCode.SUCCESS_202, "获取可用的 PDF 打印列配置成功").addData(columns).build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "获取可用的 PDF 打印列配置失败")).build();
		}
	}

	/**
	 * @description 打印外发物料出库列表
	 *
	 * @author wuchun
	 * @organization futurecraftsmen
	 * @date Created in 2025/7/16 16:59
	 * @department: Product development
	 */
	@PostMapping("/outbound/getOutSendMaterialsListByContractCodeExport/print")
	@Operation(summary = "打印外发物料出库列表", description = "打印外发物料出库列表")
	public void outSendMaterialsPrint(@Valid @RequestBody PdfPrintRequest<CollaborateOutboundPageRpcRequest> printRequest, HttpServletResponse response) {
		try {
			List<CollaborateOutboundPageRpcDTO> detailExportList = collaborateOutboundService.getOutSendMaterialsListByContractCodeExport(printRequest.getQueryParams());

			if (CollUtil.isEmpty(detailExportList)) {
				response.getOutputStream().write(JSON.toJSONBytes(RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503, "无打印外发物料出库列表数据").build()));
				response.getOutputStream().close();
				return;
			}

			printRequest.setTitle("外发物料列表");
			// 设置副标题（单号）
			String subtitle = "单号：" + detailExportList.getFirst().getContractNumber();
			printRequest.setSubtitle(subtitle);
			printRequest.setGroup("material_out");

			pdfPrintService.printPdf(
					CollaborateOutboundPageRpcDTO.class,
					detailExportList,
					printRequest,
					response,
					"外发物料出库列表"
			);

		} catch (Exception e) {
			try {
				response.getOutputStream().write(JSON.toJSONBytes(RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
						ExceptionAndView.INSTANCE.handleExceptionToView(e, "打印外发物料数据失败")).build()));
				response.getOutputStream().close();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	private PdfPTable genOutSendMaterialsOutPdf(Font font, List<CollaborateOutboundPageRpcDTO> detailExportList) throws DocumentException {
		// 创建表格
		PdfPTable table = new PdfPTable(10);
		float marginLeft = 2;
		float marginRight = 2;
		float tableWidth = PageSize.A4.rotate().getWidth() - marginLeft - marginRight;
		table.setWidthPercentage(tableWidth * 100 / PageSize.A4.rotate().getWidth()); // 设置表格宽度百分比
		float[] columnWidths = {2.0f,4.0f, 4.0f,4.0f,2.5f, 2.0f,2.5f,2.0f,2.5f, 2.5f};
		table.setWidths(columnWidths);

		// 添加表头
		String[] headers = {"序号","物料编号","物料名称", "物料型号","需求量","单位", "物料库存","在途量","已收未入量", "已出量"};
		for (String header : headers) {
			PdfPCell cell = new PdfPCell(new Phrase(header, font));
			cell.setHorizontalAlignment(Element.ALIGN_CENTER);
			cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
			cell.setNoWrap(false); // 允许文字换行
			cell.setPaddingBottom(5f); // 设置单元格底部间距
			table.addCell(cell);
		}
		for (CollaborateOutboundPageRpcDTO dto : detailExportList) {
			String[] data = {
					dto.getXh(),
					dto.getUnityNo(),
					dto.getName(),
					dto.getModel(),
					dto.getOrderQuantity().stripTrailingZeros().toPlainString(),
					dto.getPcsChn(),
					dto.getTotalInventory().stripTrailingZeros().toPlainString(),
					dto.getOnWayNum().stripTrailingZeros().toPlainString(),
					dto.getReceivingNoIntoStockNum().stripTrailingZeros().toPlainString(),
					dto.getOutboundQuantity().stripTrailingZeros().toPlainString()
			};
			for (String value : data) {
				PdfPCell cell = new PdfPCell(new Phrase(value, font));
				cell.setHorizontalAlignment(Element.ALIGN_CENTER);
				cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
				cell.setNoWrap(false); // 允许文字换行
				cell.setPaddingBottom(5f); // 设置单元格底部间距
				table.addCell(cell);
			}
		}
		return table;
	}

	@PostMapping(path = "/out/batch/items")
	@Operation(summary = "批次维度物料状态", description = "批次维度物料状态")
	public RetVal sellOrderBatchItemInfo(@Valid @RequestBody QueryLackTaskSellOrderBatchItemInfo request, BindingResult bindingResult) {
		try {
			// POJO注解校验参数合法性，不合法返回错误信息
			if (bindingResult.hasErrors()) {
				LOGGER.warn(bindingResult.getFieldError().getDefaultMessage());
				return RetVal.builder(ResCode.PARAMETER_ERROR_501, bindingResult.getFieldError().getDefaultMessage())
						.build();
			}
			if (request.getSellOrderCode()==null) {
				return RetVal.builder(ResCode.SUCCESS_202, "查询成功").addData(new LackTaskSellOrderBatchItemInfo()).build();
			}
			return RetVal.builder(ResCode.SUCCESS_202, "查询成功").addData(collaborateOutboundService.sellOrderBatchItems(request.getSellOrderCode())).build();
		} catch (Exception e) {
			return RetVal.builder(ResCode.SERVICE_UNAVAILABLE_503,
					ExceptionAndView.INSTANCE.handleExceptionToView(e, "查询失败")).build();
		}
	}

}
