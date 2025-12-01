/*
 * Copyright (c) Artisan Hovel Technology Co. Ltd. 1992-2062 All rights reserved
 */

package org.aerie.forest.dynamic.database.model.domain.module.datatype;

import org.aerie.forest.core.brick.domain.value.EnumValueObject;

/**
 * @description PG的数据类型分类枚举
 *
 * @author zhangqi
 * @organization aeire
 * @date 2022/11/2 15:13
 * @version v2.2.1.RC
 */
public enum DataTypeClassificationOfPgEnum
    implements EnumValueObject<DataTypeClassificationOfPgEnum, Class<? extends AbstractDataTypeOfDB>> {

    /**
     * @description 整数类型
     */
    INTEGER(AbstractIntegerTypeOfDB.class),

    /**
     * @description 自增序列类型
     */
    SERIAL(AbstractSerialTypeOfDB.class),

    /**
     * @description 定点数类型
     */
    FIXED_POINT(AbstractFixedPointTypeOfDB.class),

    /**
     * @description 浮点数
     */
    FLOAT_POINT(AbstractFloatPointTypeOfDB.class),

    /**
     * @description 字符串类型
     */
    STRING(AbstractStringTypeOfDB.class),

    /**
     * @description 时间类型
     */
    TIME(AbstractTimeTypeOfDB.class),

    /**
     * @description json类型
     */
    JSON(AbstractJsonTypeOfDB.class),

    /**
     * @description 布尔类型
     */
    BOOLEAN(AbstractBooleanTypeOfDB.class);

    /**
     * @description 对应的值
     */
    private final Class<? extends AbstractDataTypeOfDB> value;

    /**
     * Constructor
     *
     * @param value 对应的值
     */
    DataTypeClassificationOfPgEnum(Class<? extends AbstractDataTypeOfDB> value) {
        this.value = value;
    }

    @Override
    public Class<? extends AbstractDataTypeOfDB> getValue() {
        return value;
    }
}
