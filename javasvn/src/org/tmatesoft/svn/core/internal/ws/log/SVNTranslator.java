/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.internal.ws.fs.FSUtil;

class SVNTranslator {
    
    public static void translate(File src, File dst, byte[] eol, Map keywords, boolean special) throws IOException {
        if (src == null || dst == null) {
            // TODO: throw exception
            return;
        }
        if (dst.exists()) {
            // TODO: throw exception
            return;
        }
        if (src.equals(dst)) {
            return;
        }
        // 'special' case.
        if (special) {
            // just create symlink, if possible, or just copy file.
            if (FSUtil.isWindows) {
                dst.createNewFile();
                rawCopy(src, dst);                
            } else {
                createSymlink(src, dst);
            }
            return;
        }
        copy(src, dst, eol, keywords);
    }

    public void detranslate(File src, File dst, String keywords, boolean special, String eol) throws IOException {
        
    }

    private static void copy(File src, File dst, byte[] eol, Map keywords) throws IOException {
        int keywordLength = 0;
        keywords = keywords.isEmpty() ? null : keywords;
        if (keywords != null) {
            for (Iterator keys = keywords.keySet().iterator(); keys.hasNext();) {
                String key = (String) keys.next();
                keywordLength = Math.max(keywordLength, key.length());
            }
        }
        byte[] buffer = new byte[2048];
        byte[] keywordBuffer = new byte[keywordLength - 1];
        
        int position = 0;
        FileInputStream is = null;
        FileOutputStream os = null;
        try {
            is = new FileInputStream(src);
            os = new FileOutputStream(dst);
            while(true) {
                int r = is.read();
                position++;
                if (r < 0) {
                    // eof.
                    flushBuffer(os, buffer, position + 1, null, 0);
                    return;
                }
                buffer[position] = (byte) (r & 0xFF);
                r = r & 0xFF;
                if (eol != null && (r == '\n' || r == '\r')) {
                    int next = is.read();
                    flushBuffer(os, buffer, position + 1, eol, eol.length);
                    if (next < 0) {
                        // eof.
                        return;
                    } else if (r == '\r' && next == '\n') {
                        position = 0;
                    } else {
                        buffer[0] = (byte) next;
                        position = 1;
                    }
                    continue;
                }
                if (keywords != null && r == '$') {
                    int l = is.read(keywordBuffer);
                    if (l <= 0) {
                        // eof.
                        flushBuffer(os, buffer, position + 1, null, 0);
                        return;
                    }
                    String keyword = null;
                    for(int i = 0; i < l; i++) {
                        if (keywordBuffer[i] == '$') {
                            keyword = "$" + new String(keywordBuffer, 0, i + 1);
                            byte[] value = (byte[]) keywords.get(keyword);
                            if (value == null) {
                                keyword = null;
                            } else {
                                flushBuffer(os, buffer, position + 1, value, value.length);
                                if (l - i > 0) {
                                    os.write(keywordBuffer, i + 1, l - i);
                                }
                            }                            
                            break;
                        }
                    }
                    position = 0;
                    if (keyword == null) {
                        flushBuffer(os, buffer, position + 1, keywordBuffer, l);
                        continue;
                    }
                }
            }
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
    }
    
    private static void flushBuffer(OutputStream os, byte[] buffer, int count, byte[] tail, int tailCount) throws IOException {
        os.write(buffer, 0, count);
        if (tail != null && tailCount > 0) {
            os.write(tail, 0, tailCount);
        }        
    }
    
    private static void rawCopy(File src, File dst) throws IOException {
        FileChannel srcChannel = null;
        FileChannel dstChannel = null;
        try {
            srcChannel = new FileInputStream(src).getChannel();
            dstChannel = new FileOutputStream(dst).getChannel();
            long count = srcChannel.size();
            while(count > 0) {
                count -= dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
            }
        } finally {
            if (dstChannel != null) {
                try {
                    dstChannel.close();
                } catch (IOException e) {}
            }
            if (srcChannel != null) {
                try {
                    srcChannel.close();
                } catch (IOException e) {}
            }
        }
    }

    private static void createSymlink(File src, File dst) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
        FileInputStream is = null;
        try {
            is = new FileInputStream(src);
            byte[] buffer = new byte[1024];
            while(true) {
                int read = is.read(buffer);
                if (read <= 0) {
                    break;
                }
                bos.write(buffer, 0, read);
            }
        } finally {
            if (bos != null) {
                bos.close();
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
        if (bos == null) {
            // TODO: throw exception
            return;
        }
        String link = new String(bos.toByteArray(), "UTF-8");
        Runtime.getRuntime().exec("ln -s '" + link + "' '" + dst.getName() + "'");
    }

    public static Map computeKeywords(ISVNEntries entries, String name) {
        return null;
    }

    public static byte[] getEOL(String property) {
        return null;
    }
}
