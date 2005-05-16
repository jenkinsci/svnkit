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
        
        // 1. read bytes from stream
        // 2. if eol is met -> translate eol
        // 3. if $ is met -> read next 255 bytes. 
        // 3.1. locate next $, but not eol.
        // 3.2. when located -> translate to the same array or to the new one. unread remaining. write translated.
        // 3.3. when not located (or other interesting char located) -> write all before that char, unread remaining.
        // 4. simple char -> simple write.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(2048);
        keywords = keywords.isEmpty() ? null : keywords;
        PushbackInputStream in = new PushbackInputStream(src, 2048);
        while(true) {
            int r = in.read();
            System.out.println("from stream: " + ((char) r));
            if (r < 0) {
                buffer.writeTo(dst);
                // eof.
                return;
            }
            buffer.write(r);
            if ((r == '\r' || r == '\n') && eol != null) {
                // advance in buffer for 1 more char.
                int next = in.read();
                int start = buffer.size() - 1;
                int end = start;
                if (next >= 0) {
                    buffer.write(next);
                    end++;
                }
                // translate eol
                byte[] bytes = buffer.toByteArray();
                buffer.reset();
                if (translateEOL(bytes, start, end, eol, dst) >= 0) {
                    in.unread(next);
                }
            } else if (r == '$' && keywords != null) {
                int start = buffer.size() - 1;
                // advance in buffer for 256 more chars.
                byte[] keywordBuffer = new byte[256];
                // fill from readAhead, remaining from buffer.
                int l = in.read(keywordBuffer);
                buffer.write(keywordBuffer, 0, l);
                int end = -1;
                for(int i = 0; i < l; i++) {
                    if (keywordBuffer[i] == '$') {
                        // end of keyword, translate!
                        end = start + i + 1;
                        break;
                    }
                }
                byte[] bytes = buffer.toByteArray();
                buffer.reset();
                if (end > 0) {
                    int from = translateKeywords(bytes, start, end, keywords, dst);
                    if (from < bytes.length) {
                        // unread all from 'from'
                        System.out.println("unreading from " + from + ", lenght " + (bytes.length - from));
                        System.out.println("str: " + new String(bytes, from, (bytes.length - from)));
                        
                        in.unread(bytes, from, bytes.length - from);
                    }
                } else {
                    // unread all that was read after first '$'.
                    in.unread(bytes, start + 1, l);
                }
            } else {
                if (buffer.size() > 2048) {
                    // flush buffer.
                    buffer.writeTo(dst);
                    buffer.reset();
                }
            }
        }        
    }
    
    private static int translateEOL(byte[] buffer, int start, int end, byte[] eol, OutputStream out) throws IOException {
        out.write(buffer, 0, start);
        out.write(eol);
        if (buffer[start] == '\r' && end < buffer.length && buffer[end] == '\n') {
            return -1;
        }
        return end;
    }

    private static int translateKeywords(byte[] buffer, int start, int end, Map keywords, OutputStream out) throws IOException {
        // before keyword.
        out.write(buffer, 0, start);
        int totalLength = end - start + 1;
        // make smthng with keyword here.
        // 1. match existing keyword.
        int keywordLength = 0;
        int offset = start;
        for(int i = start + 1; i <= end; i++) {
            if (buffer[i] == '$' || buffer[i] == ':') {
                keywordLength = i - (start + 1);
                offset = i;
                break;
            }
        }
        String keyword = new String(buffer, start + 1, keywordLength, "UTF-8");
        if (!keywords.containsKey(keyword)) {
            out.write(buffer[start]);
            return start + 1;
        }
        byte[] value = (byte[]) keywords.get(keyword);
        out.write(buffer, start, keywordLength + 1); // $keyword
        if (totalLength - keywordLength >= 6 && buffer[offset] == ':' && buffer[offset + 1] == ':' && buffer[offset + 2] == ' ' && 
                (buffer[end - 1] == ' ' || buffer[end - 1] == '#')) {
            // fixed length
            if (value != null) {
                int valueOffset = 0;
                out.write(' ');
                offset++;
                while (buffer[offset] != '$') {
                    if (valueOffset < value.length) {
                        // or '#' if next is '$'
                        if (buffer[offset + 1] == '$') {
                            out.write('#');
                        } else {
                            out.write(value[valueOffset]);                            
                        }
                    } else {
                        out.write(' ');
                    }
                    valueOffset++;
                    offset++;
                }
            } else {
                while (buffer[offset] != '$') {
                    out.write(' ');
                    offset++;
                }
            }
            out.write('$');
            return end + 1; 
        } else if (totalLength - keywordLength >= 4 && buffer[offset] == ':' && buffer[offset + 1] == ' ' &&  
                buffer[end - 1] == ' ') {
            if (value != null ) {
                out.write(':');
                out.write(' ');
                if (value.length > 0) {
                    out.write(value);
                    out.write(' ');
                }
            }
            out.write('$');
            return end + 1;
        } else if (buffer[offset] == '$' || (buffer[offset] == ':' && buffer[offset + 1] == '$')) {
            // unexpanded
            if (value != null ) {
                out.write(':');
                out.write(' ');
                if (value.length > 0) {
                    out.write(value);
                    out.write(' ');
                }
            }
            out.write('$');
            return end + 1;
        }
        return start + keywordLength + 1;
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
