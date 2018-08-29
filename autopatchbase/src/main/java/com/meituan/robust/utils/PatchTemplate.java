package com.meituan.robust.utils;


import com.meituan.robust.ChangeQuickRedirect;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Created by mivanzhang on 16/7/26.
 *
 * 补丁的模板类，补丁中的类会填充这个模板生成补丁的转发类（这部分可以参看补丁的结构）
 */
public class PatchTemplate implements ChangeQuickRedirect {
    public static final String MATCH_ALL_PARAMETER = "(\\w*\\.)*\\w*";

    public PatchTemplate() {
    }

    private static final Map<Object, Object> keyToValueRelation = new WeakHashMap<>();

    @Override
    public Object accessDispatch(String methodName, Object[] paramArrayOfObject) {
        return null;
    }

    @Override
    public boolean isSupport(String methodName, Object[] paramArrayOfObject) {
        return true;
    }
}
