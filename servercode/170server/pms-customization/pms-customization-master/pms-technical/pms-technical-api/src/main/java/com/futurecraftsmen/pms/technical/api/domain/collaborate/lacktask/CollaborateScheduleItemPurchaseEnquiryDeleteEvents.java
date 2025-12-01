package com.futurecraftsmen.pms.technical.api.domain.collaborate.lacktask;


import com.futurecraftsmen.pms.api.dto.base.AbstractRpcDTO;
import com.futurecraftsmen.pms.technical.api.domain.collaborate.lacktask.base.CollaborateScheduleItemPurchaseEnquiryDeleteEvent;
import lombok.*;
import lombok.experimental.Accessors;
import org.aerie.forest.core.brick.exception.ExceptionMsg;
import org.aerie.forest.core.brick.exception.ExceptionPack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * @author chengxinyu
 * @description 协作安排数据-询价后，采购询价删除
 * @organization futurecraftsmen
 * @date 2025-07-18 10:16
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class CollaborateScheduleItemPurchaseEnquiryDeleteEvents extends AbstractRpcDTO {

	private List<CollaborateScheduleItemPurchaseEnquiryDeleteEvent> events = new ArrayList<>();

	private Long opStaff;

	public List<Long> collaborateCodes() throws ExceptionPack {
		try {
			if (events == null || events.isEmpty()) {
				return Collections.emptyList();
			}
			return events.stream().map(CollaborateScheduleItemPurchaseEnquiryDeleteEvent::getCollaborateCode).distinct().toList();
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to get collaborateCodes").build());
		}
	}

	/***
	 * @description 获取协作安排数据编号 -> 删除数量
	 *
	 * @author chengxinyu
	 * @date 2024-11-19 9:29
	 */
	public Map<Long, BigDecimal> collaborateCode2DeleteNum() throws ExceptionPack {
		try {

			return events.stream().collect(Collectors.toMap(CollaborateScheduleItemPurchaseEnquiryDeleteEvent::getCollaborateCode, CollaborateScheduleItemPurchaseEnquiryDeleteEvent::getEnquiryDeleteNum,
					(existing, replacement) -> existing.add(replacement)));
		} catch (Exception e) {
			throw new ExceptionPack(e, ExceptionMsg.builder("fail to get collaborateCode2DeleteNum").build());
		}
	}
}
