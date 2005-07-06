/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class FileTypeUtil {

    public static boolean isTextFile(File file) throws IOException {
        if (file == null) {
            return true;
        }
        Reader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            return isTextFile(reader, Integer.MAX_VALUE);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                }
            }
        }
    }

    public static boolean isTextFile(Reader rawReader, int maxDetectionCharCount)
            throws IOException {
        final Reader reader = new BufferedReader(rawReader);
        try {
            for (int chr = reader.read(), readCharCount = 0; chr >= 0; chr = reader
                    .read(), readCharCount++) {
                if (chr < 9) {
                    return false;
                }

                if (readCharCount > maxDetectionCharCount) {
                    break;
                }
            }
            return true;
        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                // ignore
            }
        }
    }
}