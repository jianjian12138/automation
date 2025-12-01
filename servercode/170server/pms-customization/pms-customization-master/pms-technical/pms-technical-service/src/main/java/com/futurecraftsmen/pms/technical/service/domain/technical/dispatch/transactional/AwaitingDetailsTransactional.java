package com.futurecraftsmen.pms.technical.service.domain.technical.dispatch.transactional;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.futurecraftsmen.pms.common.domain.serializer.BigDecimalAutoStripTrailingZerosSerializer;
import com.futurecraftsmen.pms.technical.service.domain.technical.dispatch.TransactionalModel;

import java.io.Serial;
import java.math.BigDecimal;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @description 收货、发货处理
 *
 * @author qierkang
 * @organization futurecraftsmen
 * @date Created in 2025/2/17 14:39
 */
@Data
@Accessors(chain = true)
public class AwaitingDetailsTransactional implements TransactionalModel {

	@Serial
	private static final long serialVersionUID = -1948904242023884639L;

	/**
	 *  批次号
	 */
	private String batchNumber;

	/**
	 * 产品编号
	 */
	private Long productCode;

	/**
	 * 产品调度唯一编号
	 */
	private Long mainId;

	/**
	 * 使用库存
	 */
	@JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
	private BigDecimal useStockNum;

	/**
	 * 库存总量
	 */
	@JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
	private BigDecimal stockAllNum;

	/**
	 * 库存明细编号
	 */
	private Long stockDetailCode;

	/**
	 * 库存编号
	 */
	private Long stockInfoCode;

	/**
	 * 调度任务产品表 主键
	 */
	private Long taskProduceCode;

	/**
	 * 备注
	 */
	private String remark;

	@Override
	public boolean getProductFlag() {
		return false;
	}

	@Override
	public Long getBatchGlobalCode() {
		return 0L;
	}

	@Override
	public BigDecimal getNeedNum() {
		return null;
	}

	@Override
	public void setNeedNum(BigDecimal needNum) {

	}

	@Override
	public Long getContractCode() {
		return 0L;
	}

	@Override
	public String getBussNumber() {
		return "";
	}
}
