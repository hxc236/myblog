package com.myblog.backend.pojo;

import java.util.List;

/** Tag 契约（#19）：slug 唯一（由名称自动生成）。 */
public class TagItem {

    public Long id;
    public String slug;
    public String name;
}