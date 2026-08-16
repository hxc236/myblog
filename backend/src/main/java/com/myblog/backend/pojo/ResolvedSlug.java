package com.myblog.backend.pojo;

/** slug 解析结果（#22）：命中详情或 301 重定向目标（二选一）。 */
public class ResolvedSlug {

    public PublicPostDetail detail;
    public String redirectToSlug;
}
