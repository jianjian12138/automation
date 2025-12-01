package com.futurecraftsmen.pms.technical.api.domain.collaborate.inspect;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.futurecraftsmen.pms.common.domain.serializer.BigDecimalAutoStripTrailingZerosSerializer;
import com.futurecraftsmen.pms.api.dto.base.AbstractRpcDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author Zeusedulous
 * @date 2025/1/8 14:12
 * @desc <派工>
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class CollaborateInspectDispatcherBatchSubmitRpcDTO extends AbstractRpcDTO {


    /**
     * 过程表的的主键id
     * @description
     */
    @NotNull(message = "过程检的主键id不能为空")
    private Long intoMainId;


    /**
     * 检验类型
     * @description
     */
    @NotNull(message = "检验类型不能为空")
    private Integer inspectType;

    /**
     * 抽检比例
     */
    @JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
    private BigDecimal inspectRatio;

    /**
     * 应检量
     */
    @JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
    private BigDecimal shouldInspectNum;

    /**
     *
     * @description 备注
     */
    private String remark;


    /**
     *
     * @description 待分配量
     */
    @JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
    private BigDecimal awaitNum;

    /**
     *
     * @description 任务量
     */
    @JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
    private BigDecimal taskNum;


    /**
     * 操作工集合
     * @description
     */
    List<CollaborateInspectDispatcherOperatorRpcDTO> operatorList;
}
