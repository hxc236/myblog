package com.myblog.backend.utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * 媒体内容校验（#24）：只接受 JPEG / PNG / WebP；拒绝 SVG、GIF、超过
 * 5 MB 或超过 4096×4096 的内容。类型按魔数判定（不信任扩展名/声明 MIME），
 * 尺寸 JPEG/PNG 用 ImageIO，WebP 解析容器头部（Java ImageIO 不支持 WebP）。
 */
public final class MediaContentValidator {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_DIMENSION = 4096;

    private MediaContentValidator() {
    }

    /** 校验结果：通过时携带规范化 MIME 与尺寸。 */
    public static class Validated {

        public final String mimeType;
        public final int width;
        public final int height;

        Validated(String mimeType, int width, int height) {
            this.mimeType = mimeType;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * @throws IllegalArgumentException 类型/大小/尺寸任一不满足时
     */
    public static Validated validate(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("文件内容为空");
        }
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("图片超过 5 MB 上限");
        }
        String mime = detectMime(content);
        if (mime == null) {
            throw new IllegalArgumentException("只接受 JPEG、PNG 或 WebP 图片");
        }
        int[] size = detectSize(content, mime);
        if (size[0] > MAX_DIMENSION || size[1] > MAX_DIMENSION) {
            throw new IllegalArgumentException("图片超过 4096×4096 上限");
        }
        return new Validated(mime, size[0], size[1]);
    }

    /** 魔数判定：JPEG / PNG / WebP；SVG、GIF 与其他内容返回 null。 */
    static String detectMime(byte[] content) {
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (content.length >= 8
                && (content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N'
                && content[3] == 'G' && (content[4] & 0xFF) == 0x0D
                && (content[5] & 0xFF) == 0x0A && (content[6] & 0xFF) == 0x1A
                && (content[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        if (content.length >= 12
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F'
                && content[3] == 'F' && content[8] == 'W' && content[9] == 'E'
                && content[10] == 'B' && content[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static int[] detectSize(byte[] content, String mime) {
        if ("image/webp".equals(mime)) {
            int[] size = webpSize(content);
            if (size != null) {
                return size;
            }
            throw new IllegalArgumentException("无法解析 WebP 尺寸");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new IllegalArgumentException("无法解析图片内容");
            }
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException e) {
            throw new IllegalArgumentException("无法解析图片内容", e);
        }
    }

    /**
     * WebP 容器头部尺寸解析：VP8X（扩展）/ VP8（有损）/ VP8L（无损）。
     * 返回 {width, height}，无法识别时返回 null。
     */
    static int[] webpSize(byte[] content) {
        if (content.length < 30) {
            return null;
        }
        String fourCc = new String(content, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        if ("VP8X".equals(fourCc) && content.length >= 30) {
            int width = readUInt24(content, 24) + 1;
            int height = readUInt24(content, 27) + 1;
            return new int[]{width, height};
        }
        if ("VP8 ".equals(fourCc) && content.length >= 26) {
            // 20 字节容器/块头 + 3 字节帧标签后为 14 位小端宽高
            int width = (content[23] & 0xFF) | ((content[24] & 0x3F) << 8);
            int height = ((content[24] & 0xFF) >> 6) | ((content[25] & 0xFF) << 2);
            return new int[]{width, height};
        }
        if ("VP8L".equals(fourCc) && content.length >= 25) {
            int bits = (content[21] & 0xFF) | ((content[22] & 0xFF) << 8)
                    | ((content[23] & 0xFF) << 16) | ((content[24] & 0xFF) << 24);
            int width = (bits & 0x3FFF) + 1;
            int height = ((bits >> 14) & 0x3FFF) + 1;
            return new int[]{width, height};
        }
        return null;
    }

    private static int readUInt24(byte[] content, int offset) {
        return (content[offset] & 0xFF)
                | ((content[offset + 1] & 0xFF) << 8)
                | ((content[offset + 2] & 0xFF) << 16);
    }
}