/*
 * Copyright (c) Artisan Hovel Technology Co. Ltd. 1992-2062 All rights reserved
 */

package org.aerie.forest.dynamic.database.model.domain.module.datatype;

import java.io.Serial;

import org.aerie.forest.dynamic.database.model.domain.module.AbstractDBModule;

/**
 * @description 浮点数类型
 *
 * @author zhangqi
 * @organization aeire
 * @date 2022/11/2 15:46
 * @version v2.2.1.RC
 */
public abstract class AbstractFloatPointTypeOfDB<T extends AbstractDBModule, V extends AbstractFloatPointTypeOfDB<T, V>>
    extends AbstractDataTypeOfDB<T, V> {
    @Serial
    private static final long serialVersionUID = -3379301632229405464L;
}
