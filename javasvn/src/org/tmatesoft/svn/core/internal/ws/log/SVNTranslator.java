/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.internal.ws.fs.FSUtil;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;

class SVNTranslator {
    
    public static void translate(File src, File dst, byte[] eol, Map keywords, boolean special) throws SVNException {
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
            try {
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
                byte[] keywordBuffer = new byte[256];
                int length = in.read(keywordBuffer);
                int keywordLength = 0;
                for(int i = 0; i < length; i++) {
                    if (keywordBuffer[i] == '\r' || keywordBuffer[i] == '\n') {
                        // failure, save all before i, unread remains.
                        dst.write(keywordBuffer, 0, i);
                        in.unread(keywordBuffer, i, length - i);
                        keywordBuffer = null;
                        break;
                    } else if (keywordBuffer[i] == '$') {
                        keywordLength = i + 1;
                        break;
                    }
                }
                if (keywordBuffer == null) {
                    continue;
                } else if (keywordLength == 0) {
                    dst.write(keywordBuffer, 0, length);
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

    public static Map computeKeywords(String keywords, String url, String author, String date, long revision) {
        if (keywords == null) {
            return Collections.EMPTY_MAP;
        }
        date = date == null ? null : TimeUtil.toHumanDate(date);
        String revStr = revision < 0 ? null : Long.toString(revision);
        String name = url == null ? null : PathUtil.tail(url);
        if (name != null) {
            name = PathUtil.decode(name);
        }
        
        Map map = new HashMap();
        for(StringTokenizer tokens = new StringTokenizer(keywords, " \t\n\b\r\f"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if ("LastChangedDate".equals(token) || "Date".equals(token)) {
                map.put("LastChangedDate", date);
                map.put("Date",  date);
            } else if ("LastChangedRevision".equals(token) || "Revision".equals(token) || "Rev".equals(token)) {
                map.put("LastChangedRevision", revStr);
                map.put("Revision", revStr);
                map.put("Rev", revStr);
            } else if ("LastChangedBy".equals(token) || "Author".equals(token)) {
                map.put("LastChangedBy", author);
                map.put("Author", author);
            } else if ("HeadURL".equals(token) || "URL".equals(token)) {                
                map.put("HeadURL", url);
                map.put("URL", url);
            } else if ("Id".equals(token)) {
                if (url == null) {
                    map.put("Id", null); 
                } else {
                    StringBuffer id = new StringBuffer();
                    id.append(name);
                    id.append(' ');
                    id.append(revStr);
                    id.append(' ');
                    id.append(date);
                    id.append(' ');
                    id.append(author);
                    map.put("Id", id.toString());
                }
            }
        }
        Map result = new HashMap();
        for (Iterator keys = map.keySet().iterator(); keys.hasNext();) {
            String key = (String) keys.next();
            String value = (String) map.get(key);
            if (value != null) {
                try {
                    result.put(key, value.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    result.put(key, value.getBytes());
                }
            } else {
                result.put(key, null);
            }
        }
        return result;
    }

    public static byte[] getEOL(String propertyValue) {
        if ("native".equals(propertyValue)) {
            return System.getProperty("line.separator").getBytes();
        } else if ("LF".equals(propertyValue)) {
            return new byte[] {'\n'};
        } else if ("CR".equals(propertyValue)) {
            return new byte[] {'\r'};
        } else if ("CRLF".equals(propertyValue)) {
            return new byte[] {'\r', '\n'};
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
