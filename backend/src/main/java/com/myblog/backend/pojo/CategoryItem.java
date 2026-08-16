package com.myblog.backend.pojo;

import java.util.List;

/** Category 契约（#19）：名称唯一；内置 Uncategorized 不可删除。 */
public class CategoryItem {

    public Long id;
    public String name;
    public Boolean uncategorized;
}