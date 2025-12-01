package com.futurecraftsmen.pms.technical.api.domain.technical.productpart.excel.xihang.product;

import com.alibaba.excel.annotation.ExcelProperty;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * @description 产品合并单元格格式
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2024/12/12 10:52
 */
@Data
public class ProductMaterialModelXh implements Serializable {

	@Serial
	private static final long serialVersionUID = -686664944674062682L;

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

	/**
	 * 封装返回错误提示信息
	 *  {@link com.futurecraftsmen.pms.technical.service.common.enums.ExcelValidatorEnum }
	 */
	private List<String> checkFailList;

	/**
	 * 封装返回提示信息
	 *  {@link com.futurecraftsmen.pms.technical.service.common.enums.ExcelValidatorEnum }
	 */
	private List<String> checkPartReminderList;
}
