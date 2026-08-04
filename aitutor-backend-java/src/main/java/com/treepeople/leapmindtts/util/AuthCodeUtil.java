package com.treepeople.leapmindtts.util;

import java.util.Random;

/**
 *  
 * @ Package：com.treepeople.leapmindtts.util
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/11/3  21:41
 */
public class AuthCodeUtil {
    public static String randomCode() {
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

}
