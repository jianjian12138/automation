package com.futurecraftsmen.pms.technical.api.service.temp.feedback;


import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.futurecraftsmen.pms.common.domain.serializer.BigDecimalAutoStripTrailingZerosSerializer;
import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.assertprocess.AssertException;

import java.io.Serializable;
import java.math.BigDecimal;

@Deprecated
@Data
public class FeedBackBase implements Serializable {
	/**
	 * @description 企业编号
	 */
	private Long enterpriseCode;


	/**
	 * @description 签收量
	 */
	@JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
	private BigDecimal receive;

	/**
	 * @description 送检量
	 */
	@JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
	private BigDecimal inspection;

	/**
	 * @description 反馈量
	 */
	@JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
	private BigDecimal feedbackNum;

	/**
	 * @description 检验结论
	 * * 1 整批退
	 * * 2 不合格退
	 * {@link com.futurecraftsmen.pms.technical.api.domain.quality.UnPassDealEnum}
	 */
	private int conclusion;


	public void valid() throws AssertException {
		String errorInfo = errInfo();
		if (StrUtil.isNotBlank(errorInfo)) {
			throw new AssertException(ExceptionMsg.builder("errorInfo").msgView(errorInfo).build());
		}
		//送检量不能大于签收量
		if (inspection.compareTo(receive) > 0) {
			throw new AssertException(ExceptionMsg.builder("inspection can not bigger than receive").msgView("送检量不能大于签收量").build());
		}


		//整批退时,反馈量需要等于收货量
		if (conclusion == 1 && feedbackNum.compareTo(receive) != 0) {
			throw new AssertException(ExceptionMsg.builder("feedbackNum must equal to receive").msgView("整批退时,反馈量需要等于收货量").build());
		}

		//不合格退时,反馈量不能大于收货量
		if (conclusion == 2) {
			if (feedbackNum.compareTo(receive) > 0) {
				throw new AssertException(ExceptionMsg.builder("feedbackNum can not bigger than receive").msgView("送检量不能大于签收量").build());
			}
		}

	}


	protected String errInfo() {
		return null;
	}
}
