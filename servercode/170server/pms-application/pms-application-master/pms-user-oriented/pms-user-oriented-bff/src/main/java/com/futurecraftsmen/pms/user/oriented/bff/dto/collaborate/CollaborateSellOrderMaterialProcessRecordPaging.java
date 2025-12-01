package com.futurecraftsmen.pms.user.oriented.bff.dto.collaborate;


import com.futurecraftsmen.pms.api.dto.base.AbstractBffPagingDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;


@NoArgsConstructor
@AllArgsConstructor
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Schema(name = "CollaborateSellOrderMaterialProcessRecordPaging", description = "批次下产品物料进度记录分页查询")
public class CollaborateSellOrderMaterialProcessRecordPaging extends AbstractBffPagingDTO {

//	@NotNull(message = "批次缺失")
	private Long sellOrderCode;

//	@NotNull(message = "产品物料缺失")
	private Long collaborateMaterial;
}
