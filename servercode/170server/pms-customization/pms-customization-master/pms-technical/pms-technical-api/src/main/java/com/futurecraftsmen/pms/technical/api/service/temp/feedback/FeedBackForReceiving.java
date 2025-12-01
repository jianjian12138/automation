package com.futurecraftsmen.pms.technical.api.service.temp.feedback;


import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Iterator;
import java.util.Set;

@Deprecated
@Data
public class FeedBackForReceiving extends FeedBackBase {

	/**
	 * @description 收货明细编号
	 */
	@NotNull(message = "收货明细编号缺失")
	private Long receivingDetail;


	public String errInfo() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<FeedBackForReceiving>> errors = validator.validate(this);
		if (!errors.isEmpty()) {
			Iterator<ConstraintViolation<FeedBackForReceiving>> iterator = errors.iterator();
			if (iterator.hasNext()) {
				ConstraintViolation<FeedBackForReceiving> firstElement = iterator.next();
				iterator.remove();
				return firstElement.getMessage();
			}
		}
		return null;
	}
}
