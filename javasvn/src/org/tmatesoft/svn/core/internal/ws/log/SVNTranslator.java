/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.internal.ws.fs.FSUtil;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;

class SVNTranslator {
    
    private static final byte[] CRLF = new byte[] {'\r', '\n'};
    private static final byte[] LF = new byte[] {'\n'};
    private static final byte[] CR = new byte[] {'\r'};
    private static final byte[] NATIVE = System.getProperty("line.separator").getBytes();
    
    public static void translate(File src, File dst, byte[] eol, Map keywords, boolean special) throws SVNException {
        if (src == null || dst == null) {
            SVNErrorManager.error(0, null);
            return;
        }
        if (src.equals(dst)) {
            return;
        }
        if (special) {
            try {
                if (dst.exists()) {
                    dst.delete();
                }
                if (FSUtil.isWindows) {
                    dst.createNewFile();
                    SVNFileUtil.copy(src, dst);                
                } else {
                    SVNFileUtil.createSymlink(src, dst);
                }
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            }
            return;

        }
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(src);
            os = new FileOutputStream(dst);
            copy(is, os, eol, keywords);
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
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
            
        }
    }

    private static void copy(InputStream src, OutputStream dst, byte[] eol, Map keywords) throws IOException {
        keywords = keywords.isEmpty() ? null : keywords;
        PushbackInputStream in = new PushbackInputStream(src, 2048);
        byte[] keywordBuffer = new byte[256];
        
        while(true) {
            int r = in.read();
            if (r < 0) {
                return;
            }
            if ((r == '\r' || r == '\n') && eol != null) {
                int next = in.read();
                dst.write(eol);
                if (r == '\r' && next == '\n') {
                    continue;
                }
                if (next < 0) {
                    return;
                }
                in.unread(next);
            } else if (r == '$' && keywords != null) {
                // advance in buffer for 256 more chars.
                dst.write(r);
                int length = in.read(keywordBuffer);
                int keywordLength = 0;
                for(int i = 0; i < length; i++) {
                    if (keywordBuffer[i] == '\r' || keywordBuffer[i] == '\n') {
                        // failure, save all before i, unread remains.
                        dst.write(keywordBuffer, 0, i);
                        in.unread(keywordBuffer, i, length - i);
                        keywordLength = -1;
                        break;
                    } else if (keywordBuffer[i] == '$') {
                        keywordLength = i + 1;
                        break;
                    }
                }
                if (keywordLength < 0) {
                    continue;
                } else if (keywordLength == 0) {
                    if (length > 0) {
                        dst.write(keywordBuffer, 0, length);
                    }
                } else {
                    int from = translateKeyword(dst, keywords, keywordBuffer, keywordLength);
                    in.unread(keywordBuffer, from, length - from);
                }
            } else {
                dst.write(r);
            }
        }        
    }

    private static int translateKeyword(OutputStream os, Map keywords, byte[] keyword, int length) throws IOException {
        // $$ = 0, 2 => 1,0
        String keywordName = null;
        int i = 0;
        for(i = 0; i < length; i++) {
            if (keyword[i] == '$' || keyword[i] == ':') {
                // from first $ to the offset i, exclusive
                keywordName = new String(keyword, 0, i, "UTF-8");
                break;
            }
            // write to os, we do not need it.
            os.write(keyword[i]);
        }
        
        if (!keywords.containsKey(keywordName)) {
            // unknown keyword, just write trailing chars.
            // already written is $keyword[i]..
            // but do not write last '$' - it could be a start of another keyword.
            os.write(keyword, i, length - i - 1);
            return length - 1;
        }
        byte[] value = (byte[]) keywords.get(keywordName);
        // now i points to the first char after keyword name.
        if (length - i > 5 && keyword[i] == ':' && keyword[i + 1] == ':' && keyword[i + 2] == ' ' &&
                (keyword[length - 2] == ' ' || keyword[length - 2] == '#')) {
            // :: x $
            // fixed size keyword.
            // 1. write value to keyword
            int vOffset = 0;
            int start = i;
            for(i = i + 3; i < length - 2; i++) {
                if (value == null) {
                    keyword[i] = ' ';
                } else {
                    keyword[i] = vOffset < value.length ? value[vOffset] : (byte) ' '; 
                }
                vOffset++;
            }
            keyword[i] = (byte) (vOffset < value.length ? '#' : ' ');
            // now save all.
            os.write(keyword, start, length - start);
        } else if (length - i > 4 && keyword[i] == ':' && keyword[i + 1] == ' ' && keyword[length - 2] == ' ') {
            // : x $
            if (value != null) {
                os.write(keyword, i, value.length > 0 ? 1 : 2); // ': ' or ':'
                os.write(value);
                os.write(keyword, length - 2, 2); // ' $';
            } else {
                os.write('$');
            }
        } else if (keyword[i] == '$' || (keyword[i] == ':' && keyword[i + 1] == '$')) {
            // $ or :$
            if (value != null) {
                os.write(':');
                os.write(' ');
                os.write(value);
                if (value.length > 0) {
                    os.write(' ');
                }
                os.write('$');
            } else {
                os.write('$');
            }
        } else {
            // something wrong. write all, but not last $
            os.write(keyword, i, length - i - 1);
            return length - 1;
        }
        return length;
        
    }

    public static Map computeKeywords(String keywords, String u, String a, String d, long r) {
        if (keywords == null) {
            return Collections.EMPTY_MAP;
        }
        boolean expand = u != null;
        byte[] date = null;
        byte[] url = null;
        byte[] rev = null;
        byte[] author = null;
        byte[] name = null;
        byte[] id = null;
        
        Map map = new HashMap();
        try {
            for(StringTokenizer tokens = new StringTokenizer(keywords, " \t\n\b\r\f"); tokens.hasMoreTokens();) {
                String token = tokens.nextToken();
                if ("LastChangedDate".equals(token) || "Date".equals(token)) {
                    date = expand && date == null ? TimeUtil.toHumanDate(d).getBytes("UTF-8") : date;
                    map.put("LastChangedDate", date);
                    map.put("Date",  date);
                } else if ("LastChangedRevision".equals(token) || "Revision".equals(token) || "Rev".equals(token)) {
                    rev = expand && rev == null ? Long.toString(r).getBytes("UTF-8") : rev;
                    map.put("LastChangedRevision", rev);
                    map.put("Revision", rev);
                    map.put("Rev", rev);
                } else if ("LastChangedBy".equals(token) || "Author".equals(token)) {
                    author = expand && author == null ? (a == null ? new byte[0] : a.getBytes("UTF-8")) : author;
                    map.put("LastChangedBy", author);
                    map.put("Author", author);
                } else if ("HeadURL".equals(token) || "URL".equals(token)) {
                    url = expand && url == null ? PathUtil.decode(u).getBytes("UTF-8") : url;
                    map.put("HeadURL", url);
                    map.put("URL", url);
                } else if ("Id".equals(token)) {
                    if (expand && id == null) {
                        rev = rev == null ? Long.toString(r).getBytes("UTF-8") : rev;
                        date = date == null ? TimeUtil.toHumanDate(d).getBytes("UTF-8") : date;                
                        name = name == null ? PathUtil.decode(PathUtil.tail(u)).getBytes("UTF-8") : name;
                        author = author == null ? (a == null ? new byte[0] : a.getBytes("UTF-8")) : author;
                        ByteArrayOutputStream bos = new ByteArrayOutputStream(); 
                        bos.write(name);
                        bos.write(' ');
                        bos.write(rev);
                        bos.write(' ');
                        bos.write(date);
                        bos.write(' ');
                        bos.write(author);
                        bos.close();
                        id = bos.toByteArray();
                    }
                    map.put("Id", expand ? id : null);
                }
            }
        } catch (IOException e) {}
        return map;
    }

    public static byte[] getEOL(String propertyValue) {
        if ("native".equals(propertyValue)) {
            return NATIVE;
        } else if ("LF".equals(propertyValue)) {
            return LF;
        } else if ("CR".equals(propertyValue)) {
            return CR;
        } else if ("CRLF".equals(propertyValue)) {
            return CRLF;
        }
        return null;
    }

    public static String xmlEncode(String value) {
        value = value.replaceAll("&", "&amp;");
        value = value.replaceAll("<", "&lt;");
        value = value.replaceAll(">", "&gt;");
        value = value.replaceAll("\"", "&quot;");
        value = value.replaceAll("'", "&apos;");
        value = value.replaceAll("\t", "&#09;");
        return value;
    }

    public static String xmlDecode(String value) {
        value = value.replaceAll("&lt;", "<");
        value = value.replaceAll("&gt;", ">");
        value = value.replaceAll("&quot;", "\"");
        value = value.replaceAll("&apos;", "'");
        value = value.replaceAll("&#09;", "\t");
        value = value.replaceAll("&amp;", "&");
        return value;
    }
}
