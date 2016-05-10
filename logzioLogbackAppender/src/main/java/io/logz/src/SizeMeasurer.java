package io.logz.src;

import com.google.common.base.Utf8;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Created by roiravhon on 5/10/16.
 */
public class SizeMeasurer {

    public static int getSizeOfStringList(List<String> inputString) {

        int totalSize = 0;

        for(String currString : inputString) {

            totalSize += encodedLength(currString);
        }

        return totalSize;
    }

    private static int encodedLength(String s) {
        try {
            int length = Utf8.encodedLength(s);
            return length;
        } catch (IllegalArgumentException e) {
            return s.getBytes(StandardCharsets.UTF_8).length;
        }
    }


}
