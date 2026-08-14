package com.myblog.publicsite.media;

/** Media Asset 元数据契约（#24）。 */
public class MediaAsset {

    public Long id;
    public String objectKey;
    public String fileName;
    public String mimeType;
    public Long sizeBytes;
    public Integer width;
    public Integer height;
    public String checksumSha256;
    public String altText;
    public String publicUrl;
    public String createdAt;
    /** 是否被 Draft 或 Published Revision 引用（列表标记，#24）。 */
    public Boolean referenced;
}
