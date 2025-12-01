package com.futurecraftsmen.pms.technical.service.domain.quality.inspect.finish;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.futurecraftsmen.pms.common.domain.serializer.BigDecimalAutoStripTrailingZerosSerializer;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.aerie.forest.core.brick.domain.dto.AbstractDatabaseModel;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

@NoArgsConstructor
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class InspectFinishDispatcherDetailModel extends AbstractDatabaseModel {

    @Serial
    private static final long serialVersionUID = -5790416573913401589L;


    /**
     *
     * @description 主键
     */
    private long primaryKeyValue;

    /**
     *
     * @description 批次号
     */
    private String batchNum;


    /**
     *
     * @description 工序名称
     */
    private String procedureName;

    /**
     *
     * @description 产品零件id
     */
    private long productOrPart;

    /**
     *
     * @description 产品零件名称
     */
    private String productOrPartName;

    /**
     * 合同的单号
     * @description
     */
    private String bussNumber;

    /**
     * 规格型号
     * @description
     */
    private String model;

    /**
     * 单位
     * @description
     */
    private String pcsName;

    /**
     *
     * @description 任务截止时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date overdueDate;

    /**
     * 检验类型
     * @description
     */
    private Integer inspectType;

    /**
     * 抽检比例
     */
    @JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
    private BigDecimal inspectRatio;

    /**
     * 总的应检量
     */
    @JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
    private BigDecimal shouldInspectNum;


    /**
     *
     * @description 待分配量
     */
    @JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
    private BigDecimal awaitNum;


    /**
     * 分发数量
     */
    @JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
    private BigDecimal allocationNum;


    /**
     *
     * @description 任务量
     */
    @JsonSerialize(using = BigDecimalAutoStripTrailingZerosSerializer.class)
    private BigDecimal taskNum;


    @Override
    public long getPrimaryKeyValue() {
        return primaryKeyValue;
    }
}
