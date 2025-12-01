package com.futurecraftsmen.pms.technical.api.domain.technical.productpart.excel.standard;

import com.alibaba.excel.annotation.ExcelProperty;
import com.futurecraftsmen.pms.common.domain.excel.BatchImportSelectionTypeEnum;
import com.futurecraftsmen.pms.common.domain.excel.UserSelection;
import com.futurecraftsmen.pms.common.domain.excel.model.StationaryExcelModel;
import com.futurecraftsmen.pms.common.excel.multi.ExcelMultiAop;
import com.futurecraftsmen.pms.common.excel.multi.ExcelMultiListAop;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * @description 导入校验 部件-无工艺路线
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2025/4/27 17:49
 * @department: Product development
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ComponentWorkNStandardModel extends StationaryExcelModel {

	@Serial
	private static final long serialVersionUID = -6584922245911318719L;

	/**
	 * 部件分类
	 */
	@ExcelMultiAop
	@ExcelProperty("部件分类")
	private String productPartTypeName;
	private Long productPartTypeCode;

	/**
	 * 部件编号
	 */
	@ExcelProperty("部件编号")
	private String unityNo;

	/**
	 * 是否是编码规则生成
	 * @description
	 */
	private boolean generate = false;

	/**
	 * 部件名称
	 */
	@ExcelProperty("部件名称")
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
	 * 版本
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

	/**
	 * 物料编号
	 */
	@ExcelProperty("物料编号")
	private String materialUnityNo;

	/**
	 * 数量
	 */
	@ExcelProperty("数量")
	private String number;

	@ExcelMultiListAop
	private List<WorkNStandardModel> materialModels;

	@Override
	public String getImportContent() {
		return "导入校验 部件-无工艺路线";
	}

}
