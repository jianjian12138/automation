package com.futurecraftsmen.pms.user.oriented.bff.dto.collaborate;


import com.futurecraftsmen.pms.api.dto.base.AbstractBffPagingDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;


@NoArgsConstructor
@AllArgsConstructor
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Schema(name = "CollaborateSellOrderMaterialProcessOverviewPaging", description = "批次物料进度概览分页查询")
public class CollaborateSellOrderMaterialProcessOverviewPaging extends AbstractBffPagingDTO {

	private Long sellOrderCode;

}
