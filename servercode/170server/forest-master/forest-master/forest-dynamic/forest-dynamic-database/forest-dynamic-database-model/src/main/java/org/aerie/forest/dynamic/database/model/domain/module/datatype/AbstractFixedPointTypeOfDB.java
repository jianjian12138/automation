/*
 * Copyright (c) Artisan Hovel Technology Co. Ltd. 1992-2062 All rights reserved
 */

package org.aerie.forest.dynamic.database.model.domain.module.datatype;

import java.io.Serial;

import org.aerie.forest.dynamic.database.model.domain.module.AbstractDBModule;

/**
 * @description 定点类型
 * @param <T>
 * @param <V>
 *
 * @author quark
 * @organization futurecraftsmen
 * @date 2024/4/12 16:19
 * @version v3.1.0.GA
 */
public abstract class AbstractFixedPointTypeOfDB<T extends AbstractDBModule, V extends AbstractFixedPointTypeOfDB<T, V>>
    extends AbstractDataTypeOfDB<T, V> {

    @Serial
    private static final long serialVersionUID = 912753302676002690L;
}
