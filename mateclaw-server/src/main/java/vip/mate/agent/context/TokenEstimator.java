package vip.mate.agent.context;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Token 估算工具类
 * <p>
 * 按字符类型分段估算：
 * <ul>
 *   <li>CJK 字符（中日韩）：约 1 字符 ≈ 1 token</li>
 *   <li>ASCII 字符（英文、数字、符号）：约 4 字符 ≈ 1 token</li>
 * </ul>
 * 这是保守估算（偏高），确保压缩阈值不会触发过晚。
 *
 * @author MateClaw Team
 */
public final class TokenEstimator {

    /** 每条消息的固定开销 token（role 标记、分隔符等） */
    static final int PER_MESSAGE_OVERHEAD = 4;

    private TokenEstimator() {
    }

    /**
     * 估算文本 token 数。
     * CJK 字符按 1:1，ASCII 按 4:1，其他 Unicode 按 1.5:1。
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjkChars = 0;
        int asciiChars = 0;
        int otherChars = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCJK(c)) {
                cjkChars++;
            } else if (c <= 0x7F) {
                asciiChars++;
            } else {
                otherChars++;
            }
        }
        // CJK: 1 char ≈ 1 token; ASCII: 4 chars ≈ 1 token; Other: 1.5 chars ≈ 1 token
        return cjkChars + (asciiChars + 3) / 4 + (otherChars * 2 + 2) / 3;
    }

    /**
     * 估算单条消息 token 数（内容 + 消息开销）
     */
    public static int estimateTokens(Message message) {
        if (message == null) {
            return 0;
        }
        return estimateTokens(message.getText()) + PER_MESSAGE_OVERHEAD;
    }

    /**
     * 估算消息列表总 token 数
     */
    public static int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return messages.stream()
                .mapToInt(TokenEstimator::estimateTokens)
                .sum();
    }

    /**
     * 判断是否为 CJK 字符（中日韩统一表意文字 + 常用标点）
     */
    private static boolean isCJK(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.BOPOMOFO;
    }
}
