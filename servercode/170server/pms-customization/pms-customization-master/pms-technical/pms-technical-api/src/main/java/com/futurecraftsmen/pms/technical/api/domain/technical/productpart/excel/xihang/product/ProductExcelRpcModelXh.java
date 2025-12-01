package com.futurecraftsmen.pms.technical.api.domain.technical.productpart.excel.xihang.product;

import com.alibaba.excel.annotation.ExcelProperty;
import com.futurecraftsmen.pms.common.domain.excel.BatchImportSelectionTypeEnum;
import com.futurecraftsmen.pms.common.domain.excel.UserSelection;
import com.futurecraftsmen.pms.common.domain.excel.model.StationaryExcelModel;
import com.futurecraftsmen.pms.common.excel.multi.ExcelMultiAop;
import com.futurecraftsmen.pms.common.excel.multi.ExcelMultiListAop;

import java.io.Serial;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @description 产品批量导入Excel模型
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2024/12/9 下午8:32
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductExcelRpcModelXh extends StationaryExcelModel {

	@Serial
	private static final long serialVersionUID = -6584922245911318719L;

	/**
	 * 产品分类
	 */
	@ExcelMultiAop
	@ExcelProperty("产品分类")
	private String productPartTypeName;

	/**
	 * 产品编号
	 */
	@ExcelProperty("产品编号")
	private String productPartUnityNo;

	/**
	 * 产品名称
	 */
	@ExcelProperty("产品名称")
	private String name;

	/**
	 * 型号
	 */
	@ExcelProperty("型号")
	private String model;

	/**
	 * 单位 名称
	 */
	@ExcelProperty("单位")
	private String pcsName;

	/**
	 * 是否标准,标准非标 名称
	 */
	@ExcelProperty("标准非标")
	private String standardName;

	/**
	 * 产品的版本
	 */
	@ExcelProperty("版本")
	private String version;

	/**
	 * 技术对接人名称
	 */
	@ExcelProperty("技术对接人")
	@UserSelection(type = BatchImportSelectionTypeEnum.CONTACT_PERSON)
	private String contactPersonName;

	/**
	 * 技术对接人编号
	 */
	private Long contactPersonCode;

	/**
	 * 状态
	 */
	@ExcelProperty("状态")
	private String stateStr;

	/**
	 * 备注
	 */
	@ExcelProperty("备注")
	private String remark;

	/**
	 * 图纸号
	 */
	@ExcelProperty("图纸号")
	private String drawingNumber;

	/**
	 * 检验项名称
	 */
	@ExcelProperty("检验项名称")
	private String inspectionItemName;

	private String inspectionItemNameList;

	/**
	 * 零件编号
	 */
	@ExcelProperty("零件编号")
	private String partUnityNo;

	/**
	 * 数量
	 */
	@ExcelProperty("数量")
	private String number;

	@ExcelMultiListAop
	private List<ProductMaterialModelXh> productMaterialModels;

	@Override
	public String getImportContent() {
		return "产品导入";
	}

}
