package com.myblog.backend.service;

import com.myblog.backend.pojo.SiteSettings;

/**
 * 站点设置服务契约（#17）：读取与“保存并发布”。
 *
 * <p>实现见 {@link com.myblog.backend.service.impl.SiteSettingsServiceImpl}。
 * 保存在同一事务中原子更新 Public Introduction、技能分组、作品区设置与
 * 联系方式；任何一步失败都会整体回滚，Visitor 始终看到最近一次完整发布的
 * 内容。管理端只能提交本服务固定接受的字段（隐私字段边界由 DTO 形状 +
 * 全局 strict JSON 反序列化共同保证）。
 */
public interface SiteSettingsService {

    /** 数据库读路径是否可用。 */
    boolean isAvailable();

    /** 读取当前站点设置（编辑表单初始值）。 */
    SiteSettings getSettings();

    /**
     * 校验并原子保存站点设置。
     *
     * @throws IllegalArgumentException 校验失败（字段缺失、超长、重名等）
     */
    void saveSettings(SiteSettings settings);
}
