/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.ws.fs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.diff.delta.SVNSequenceLine;
import org.tmatesoft.svn.core.diff.delta.SVNSequenceLineReader;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
public class FSUtil {

    public final static boolean isWindows;
    public final static OutputStream NULL_OUTPUT = new OutputStream() {
                                                        public void write(int b) throws IOException {
                                                        }
                                                    };

    static {
        String osName = System.getProperty("os.name");
        isWindows = osName != null && osName.toLowerCase().indexOf("windows") >= 0;
    }

    public static boolean isSymlink(File file) {
        if (isWindows || file == null) {
            return false;
        }
        if (!file.exists()) {
            // may be a "broken" symlink.
            File parent = file.getParentFile();
            String[] children = parent.list();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    if (children[i].equals(file.getName())) {
                        return true;
                    }
                }
            }
            return false;
        }
        try {
            return !file.getAbsolutePath().equals(file.getCanonicalPath());
        } catch (IOException e) {}
        return false;
    }

    public static boolean isFileOrSymlinkExists(File file) {
        if (file == null) {
            return false;
        }
        if (isWindows) {
            return file.exists();
        }
        if (!file.exists()) {
            File parent = file.getParentFile();
            String[] children = parent.list();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    if (children[i].equals(file.getName())) {
                        return true;
                    }
                }
            }
            return false;
        }
        return true;
    }

    public static void setHidden(File file, boolean hidden) {
        if (!isWindows || file == null || !file.exists() || file.isHidden()) {
            return;
        }
        try {
            Runtime.getRuntime().exec("attrib " + (hidden ? "+" : "-") + "H \"" + file.getAbsolutePath() + "\"");
        } catch (Throwable th) {}
    }

    public static void setExecutable(File file, boolean executable) {
        if (isWindows || file == null || !file.exists()) {
            return;
        }
        try {
            Runtime.getRuntime().exec("chmod ugo" + (executable ? "+" : "-") + "x \"" + file.getAbsolutePath() + "\"");
        } catch (Throwable th) {}
    }

    public static void setReadonly(File file, boolean readonly) {
        if (file.canWrite() == !readonly || !file.exists()) {
            return;
        }
        if (readonly && file.setReadOnly()) {
            return;
        }

        try {
            Process p = null;
            if (isWindows) {
                p = Runtime.getRuntime().exec("attrib " + (readonly ? "+" : "-") + "R \"" + file.getAbsolutePath() + "\"");
            } else {
                p = Runtime.getRuntime().exec(new String[] { "chmod", "ugo" + (readonly ? "-" : "+") + "w", file.getAbsolutePath() });
            }
            if (p != null) {
                p.waitFor();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public static void deleteAll(File dir) {
        if (dir == null) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                File child = children[i];
                deleteAll(child);
            }
        }
        if (!dir.delete()) {
            DebugLog.log("can't delete file " + dir.getAbsolutePath());
        }
    }

    public static File copyAll(File source, File dst, String asName, FileFilter filter) throws IOException {
        if (!source.exists()) {
            return null;
        }
        if (filter != null && !filter.accept(source)) {
            return dst;
        }
        if (source.isDirectory()) {
            File dstDir = new File(dst, asName);
            dstDir.mkdirs();
            File[] children = source.listFiles();
            for (int i = 0; i < children.length; i++) {
                copyAll(children[i], dstDir, children[i].getName(), filter);
            }
            return dstDir;
        }
        InputStream is = null;
        OutputStream os = null;
        dst = new File(dst, asName);
        if (!dst.getParentFile().exists()) {
            dst.getParentFile().mkdirs();
        }
        try {
            is = new BufferedInputStream(new FileInputStream(source));
            os = new BufferedOutputStream(new FileOutputStream(dst));
            FSUtil.copy(is, os, null, null, null);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {}
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
            dst.setLastModified(source.lastModified());
        }
        return dst;
    }

    public static boolean compareFiles(File f1, File f2, boolean isBinary) throws IOException {
        InputStream is1 = null;
        InputStream is2 = null;
        try {
            if (isBinary && f1.length() != f2.length()) {
                return false;
            }
            is1 = new BufferedInputStream(new FileInputStream(f1));
            is2 = new BufferedInputStream(new FileInputStream(f2));
            if (isBinary) {
                while (true) {
                    int read1 = is1.read();
                    int read2 = is2.read();
                    if (read1 != read2) {
                        return false;
                    }
                    if (read1 < 0 || read2 < 0) {
                        break;
                    }
                }
            } else {
                SVNSequenceLineReader reader = new SVNSequenceLineReader(new byte[0]);
                SVNSequenceLine[] lines1 = reader.read(is1);
                SVNSequenceLine[] lines2 = reader.read(is2);
                if (lines1 == null || lines2 == null) {
                    return lines1 == lines2;
                }
                if (lines1.length != lines2.length) {
                    return false;
                }
                for (int i = 0; i < lines1.length; i++) {
                    if (!lines1[i].equals(lines2[i])) {
                        return false;
                    }
                }
            }
        } finally {
            if (is1 != null) {
                try {
                    is1.close();
                } catch (IOException e) {}
            }
            if (is2 != null) {
                try {
                    is2.close();
                } catch (IOException e) {}
            }
        }
        return true;
    }

    public static String copy(InputStream is, OutputStream os, MessageDigest digest) throws IOException {
        return FSUtil.copy(is, os, null, digest);
    }

    public static String copy(InputStream is, OutputStream os, String eol, MessageDigest digest) throws IOException {
        return FSUtil.copy(is, os, eol, null, digest);
    }

    /**
     * @param keywords
     *            contains keywords to expand, or unexpand. key is keyword name,
     *            null value will result in keyword expanding, non-null in
     *            keyword unexpadning. null map will left keywords untouched.
     */
    public static String copy(InputStream is, final OutputStream os, String eol, Map keywordsMap, final MessageDigest digest) throws IOException {
        OutputStream dos = digest != null ? new OutputStream() {
            public void write(int b) throws IOException {
                digest.update((byte) (b & 0xff));
                os.write(b);
            }

            public void write(byte b[], int o, int l) throws IOException {
                digest.update(b, o, l);
                os.write(b, o, l);
            }
        } : os;

        keywordsMap = keywordsMap != null && keywordsMap.isEmpty() ? null : keywordsMap;
        if (eol == null && keywordsMap == null) {
            while (true) {
                int read = is.read(myBinaryBuffer);
                if (read < 0) {
                    return toHexDigest(digest);
                }
                dos.write(myBinaryBuffer, 0, read);
            }
        }

        byte[] replaceWith = SVNProperty.getEOLBytes(eol);
        OutputStream target = keywordsMap != null ? myLineBuffer : dos;

        myFirstByte = -1;
        do {
            if (replaceWith == null) {
                replaceWith = readLine(is, target);
            } else if (readLine(is, target) == null) {
                replaceWith = null;
            }
            if (keywordsMap != null) {
                byte[] strBytes = myLineBuffer.toByteArray();
                if (strBytes.length > 0) {
                    String line = new String(strBytes);
                    for (Iterator keywords = keywordsMap.keySet().iterator(); keywords.hasNext();) {
                        String keyword = (String) keywords.next();
                        line = expandKeyword(line, keyword, (String) keywordsMap.get(keyword));
                    }
                    dos.write(line.getBytes());
                }
                myLineBuffer.reset();
            }
            if (replaceWith != null) {
                dos.write(replaceWith);
            }
        } while (replaceWith != null);
        return toHexDigest(digest);
    }

    private static ByteArrayOutputStream myLineBuffer = new ByteArrayOutputStream();
    private static byte[] myBinaryBuffer = new byte[8192 * 4];
    private static int myFirstByte;

    private static final int CR = '\r';
    private static final int LF = '\n';
    private static final byte[] CR_BYTES = new byte[] { '\r' };
    private static final byte[] LF_BYTES = new byte[] { '\n' };
    private static final byte[] CRLF_BYTES = new byte[] { '\r', '\n' };

    private static byte[] readLine(InputStream is, OutputStream os) throws IOException {
        int b;
        if (myFirstByte >= 0) {
            os.write(myFirstByte);
            myFirstByte = -1;
        }
        while (true) {
            b = is.read();
            switch (b) {
            case CR:
                b = is.read();
                myFirstByte = -1;
                if (b == LF) {
                    return CRLF_BYTES;
                } else if (b >= 0) {
                    myFirstByte = b;
                }
                return CR_BYTES;
            case LF:
                myFirstByte = -1;
                return LF_BYTES;
            default:
                if (b < 0) {
                    myFirstByte = -1;
                    return null;
                }
                os.write(b);
            }
        }
    }

    private static String toHexDigest(MessageDigest digest) {
        if (digest == null) {
            return null;
        }
        byte[] result = digest.digest();
        String hexDigest = "";
        for (int i = 0; i < result.length; i++) {
            byte b = result[i];
            int lo = b & 0xf;
            int hi = (b >> 4) & 0xf;
            hexDigest += Integer.toHexString(hi) + Integer.toHexString(lo);
        }
        return hexDigest;
    }

    private static String expandKeyword(String line, String keyword, String value) {
        int index = 0;
        StringBuffer result = new StringBuffer();
        if (value != null) {
            String pattern = "$" + keyword + "$";
            while (true) {
                int prevIndex = index;
                index = line.indexOf(pattern, index);
                if (index >= 0) {
                    result.append(line.substring(prevIndex, index));
                    result.append("$");
                    result.append(keyword);
                    result.append(": ");
                    result.append(value);
                    if (value.length() > 0) {
                        result.append(' ');
                    }
                    result.append("$");
                    index += pattern.length();
                } else {
                    result.append(line.substring(prevIndex));
                    return result.toString();
                }
            }
        }
        String pattern = "$" + keyword + ": ";
        while (true) {
            int prevIndex = index;
            index = line.indexOf(pattern, index);
            int lastIndex = line.indexOf("$", index + 1);
            if (index >= 0 && lastIndex >= 0) {
                result.append(line.substring(prevIndex, index));
                result.append("$");
                result.append(keyword);
                result.append("$");
                index = lastIndex + 1;
            } else {
                result.append(line.substring(prevIndex));
                return result.toString();
            }
        }
    }

    public static String copy(File from, File to, String eol, MessageDigest digest) {
        return copy(from, to, eol, null, digest);
    }

    public static String copy(File from, File to, String eol, Map keywords, MessageDigest digest) {
        InputStream is = null;
        OutputStream os = null;
        if (!from.exists()) {
            return null;
        } else if (!to.exists()) {
            to.getParentFile().mkdirs();
            try {
                to.createNewFile();
            } catch (IOException e1) {
                return null;
            }
        }
        FSUtil.setReadonly(to, false);
        try {
            is = new BufferedInputStream(new FileInputStream(from));
            os = new BufferedOutputStream(new FileOutputStream(to));
            return copy(is, os, eol, keywords, digest);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e1) {}
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e1) {}
            }
        }
        return null;
    }

    public static void sleepForTimestamp() {
        long time = System.currentTimeMillis();
        time = 1010 - (time - (time / 1000) * 1000);
        DebugLog.benchmark("WATING FOR: " + time + " ms.");
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {}
    }
    
    public static String getChecksum(File file, MessageDigest digest) {
        if (!file.isFile()) {
            return null;
        }
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            return copy(is, NULL_OUTPUT, digest);
        } catch (IOException e) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
        }
    }

}
