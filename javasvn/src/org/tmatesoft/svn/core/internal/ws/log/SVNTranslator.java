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
import java.nio.channels.FileChannel;
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
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(src);
            os = new FileOutputStream(dst);
            copy(is, os, eol, keywords);
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
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(2048);
        keywords = keywords.isEmpty() ? null : keywords;
        while(true) {
            int r = src.read();
            if (r < 0) {
                dst.write(buffer.toByteArray());
                // eof.
                return;
            }
            if (r == '\r' || r == '\n' && eol != null) {
                // advance in buffer for 1 more char.
                int next = src.read();
                int start = buffer.size() - 1;
                int end = buffer.size();
                if (next >= 0) {
                    buffer.write(next);
                    end++;
                }
                // translate eol
                byte[] bytes = buffer.toByteArray();
                buffer.reset();
                if (translateEOL(bytes, start, end, eol, dst) >= 0) {
                    // for new buffer, could be anything.
                    buffer.write(next);
                }
            } else if (r == '$' && keywords != null) {
                int start = buffer.size() - 1;
                // advance in buffer for 256 more chars.
                byte[] keywordBuffer = new byte[256];                
                int l = src.read(keywordBuffer);
                buffer.write(keywordBuffer, 0, l);
                int end = -1;
                for(int i = 0; i < l; i++) {
                    if (keywordBuffer[i] == '$') {
                        // end of keyword, translate!
                        end = i;
                        break;
                    }
                }
                byte[] bytes = buffer.toByteArray();
                buffer.reset();
                if (end > 0) {
                    int from = translateKeywords(bytes, start, end, keywords, dst);
                    if (from < bytes.length) {
                        buffer.write(bytes, from, bytes.length - from);
                    }
                } else {
                    dst.write(bytes);
                }
            } else {
                buffer.write(r);
                if (buffer.size() > 2048) {
                    // flush buffer.
                    dst.write(buffer.toByteArray());
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
            return start;
        }
        byte[] value = (byte[]) keywords.get(keyword);
        out.write(buffer, start, keywordLength + 1); // $keyword
        if (totalLength - keywordLength >= 6 && buffer[offset] == ':' && buffer[offset + 1] == ':' && buffer[offset + 2] == ' ' && 
                (buffer[end - 1] == ' ' || buffer[end - 1] == '#')) {
            // fixed length
            if (value != null) {
                int valueOffset = 0;
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
        return start;
    }
    
    public static void rawCopy(File src, File dst) throws IOException {
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
    /*
    public static void setExecutable(File file, ISVNEntries entries, String name) {
        if (entries.getProperty(name, SVNProperty.EXECUTABLE) != null) {
            if (FSUtil.isWindows) {
                FSUtil.setExecutable(file, true);
            }
        }        
    }

    public static void setReadonly(File file, ISVNEntries entries, String name) {
        if (entries.getProperty(name, SVNProperty.NEEDS_LOCK) != null &&
                entries.getProperty(name, SVNProperty.LOCK_TOKEN) == null) {
            FSUtil.setReadonly(file, true);
        }        
    }

    public static Map computeKeywords(ISVNEntries entries, String name, boolean expand) {
        String keywords = entries.getProperty(name, SVNProperty.KEYWORDS);
        if (keywords == null) {
            return null;
        }
        Map map = new HashMap();
        for(StringTokenizer tokens = new StringTokenizer(keywords, ","); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if ("LastChangedDate".equals(token) || "Date".equals(token)) {
                String dateStr = entries.getProperty(name, SVNProperty.COMMITTED_DATE);
                dateStr = TimeUtil.toHumanDate(dateStr);
                map.put("LastChangedDate", expand ? dateStr : null);
                map.put("Date",  expand ? dateStr : null);
            } else if ("LastChangedRevision".equals(token) || "Revision".equals(token) || "Rev".equals(token)) {
                String revStr = entries.getProperty(name, SVNProperty.COMMITTED_REVISION);
                map.put("LastChangedRevision", expand ? revStr : null);
                map.put("Revision", expand ? revStr : null);
                map.put("Rev", expand ? revStr : null);
            } else if ("LastChangedBy".equals(token) || "Author".equals(token)) {
                String author = entries.getProperty(name, SVNProperty.LAST_AUTHOR);
                author = author == null ? "" : author;
                map.put("LastChangedBy", expand ? author : null);
                map.put("Author", expand ? author : null);
            } else if ("HeadURL".equals(token) || "URL".equals(token)) {                
                String url = entries.getProperty(name, SVNProperty.URL);
                map.put("HeadURL", expand ? url : null);
                map.put("URL", expand ? url : null);
            } else if ("Id".equals(token)) {
                StringBuffer id = new StringBuffer();
                id.append(entries.getProperty(name, SVNProperty.NAME));
                id.append(' ');
                id.append(entries.getProperty(name, SVNProperty.COMMITTED_REVISION));
                id.append(' ');
                String dateStr = entries.getProperty(name, SVNProperty.COMMITTED_DATE);
                dateStr = TimeUtil.toHumanDate(dateStr);
                id.append(dateStr);
                id.append(' ');
                String author = entries.getProperty(name, SVNProperty.LAST_AUTHOR);
                author = author == null ? "" : author;
                id.append(author);
                map.put("Id", expand ? id.toString() : null);
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
    }*/

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
