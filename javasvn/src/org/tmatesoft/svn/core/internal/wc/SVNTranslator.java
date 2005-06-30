/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.TimeUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class SVNTranslator {
    
    public static final byte[] CRLF = new byte[] {'\r', '\n'};
    public static final byte[] LF = new byte[] {'\n'};
    public static final byte[] CR = new byte[] {'\r'};
    public static final byte[] NATIVE = System.getProperty("line.separator").getBytes();
    
    public static void translate(SVNDirectory dir, String name, String srcPath, String dstPath, boolean expand, boolean safe) throws SVNException {
        File src = dir.getFile(srcPath, false);
        File dst = safe ? SVNFileUtil.createUniqueFile(dir.getRoot(), dstPath, ".tmp") : dir.getFile(dstPath, false);
        
        SVNProperties props = dir.getProperties(name, false);
        String keywords = props.getPropertyValue(SVNProperty.KEYWORDS);
        String eolStyle = props.getPropertyValue(SVNProperty.EOL_STYLE);
        boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;
        Map keywordsMap = null;
        byte[] eols;
        if (keywords != null) {
            if (expand) {
                SVNEntry entry = dir.getEntries().getEntry(name, true);
                String url = entry.getURL();
                String author = entry.getAuthor();
                String date = entry.getCommittedDate();
                String rev = Long.toString(entry.getCommittedRevision());
                keywordsMap = computeKeywords(keywords, url, author, date, rev);
            } else {                
                keywordsMap = computeKeywords(keywords, null, null, null, null);
            }
        }
        if (!expand) {
            eols = getBaseEOL(eolStyle);
        } else {
            eols = getWorkingEOL(eolStyle);
        }
        translate(src, dst, eols, keywordsMap, special, expand);
        if (safe) {
            SVNFileUtil.rename(dst, dir.getFile(dstPath, false));
        }
    }
    
    public static void translate(File src, File dst, byte[] eol, Map keywords, boolean special, boolean expand) throws SVNException {
        if (src == null || dst == null) {
            SVNErrorManager.error(0, null);
            return;
        }
        if (src.equals(dst)) {
            return;
        }
        if (special) {
            if (dst.exists()) {
                dst.delete();
            }
            if (SVNFileUtil.isWindows) {
                SVNFileUtil.copyFile(src, dst, true);
            } else if (expand) {
                // create symlink to target, and create it at dst
                DebugLog.log("creating symlink: ");
                DebugLog.log("link file: " + dst);
                DebugLog.log("link name file: " + src);
                SVNFileUtil.createSymlink(dst, src);
            } else {
                SVNFileUtil.detranslateSymlink(src, dst);
            }
            return;

        }
        OutputStream os = SVNFileUtil.openFileForWriting(dst);
        InputStream is = SVNFileUtil.openFileForReading(src);
        try {
            copy(is, os, eol, keywords);
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        } finally {
            SVNFileUtil.closeFile(os);
            SVNFileUtil.closeFile(is);
        }
    }
    
    public static boolean checkNewLines(File file) {
        if (file == null || !file.exists() || file.isDirectory()) {
            return true;            
        }
        InputStream is = null;
        try {
            is = SVNFileUtil.openFileForReading(file);
            int r;
            byte[] lastFoundEOL = null;
            byte[] currentEOL = null;
            while((r = is.read()) >= 0) {
                if (r == '\n') {
                    currentEOL = LF;
                } else if (r == '\r') {
                    currentEOL = CR;
                    r = is.read();
                    if (r == '\n') {
                        currentEOL = CRLF;
                    }
                }
                if (lastFoundEOL == null) {
                    lastFoundEOL = currentEOL;
                } else if (currentEOL != null && lastFoundEOL != currentEOL) {
                    return false;
                }
            }
        } catch (IOException e) {
            return false;
        } catch (SVNException e) {
            return false;
        } finally {
            SVNFileUtil.closeFile(is);
        }
        return true;
    }

    private static void copy(InputStream src, OutputStream dst, byte[] eol, Map keywords) throws IOException {
        if (keywords != null && keywords.isEmpty()) {
            keywords = null;
        }
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
                if (keywordLength == 0) {
                    if (length > 0) {
                        dst.write(keywordBuffer, 0, length);
                    }
                } else if (keywordLength > 0) {
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
            keyword[i] = (byte) (value != null && vOffset < value.length ? '#' : ' ');
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

    public static Map computeKeywords(String keywords, String u, String a, String d, String r) {
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
                    rev = expand && rev == null ? r.getBytes("UTF-8") : rev;
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
                        rev = rev == null ? r.getBytes("UTF-8") : rev;
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
        } catch (IOException e) {
            //
        }
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
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
            case '&':
                result.append("&amp;");
                break;
            case '<':
                result.append("&lt;");
                break;
            case '>':
                result.append("&gt;");
                break;
            case '\"':
                result.append("&quot;");
                break;
            case '\'':
                result.append("&apos;");
                break;
            case '\r':
                result.append("&#13;");
                break;
            case '\n':
                result.append("&#10;");
                break;
            case '\t':
                result.append("&#9;");
                break;
            default:
            result.append(ch);
            }
        }
        return result.toString();
    }
    
    private static final Map XML_UNESCAPE_MAP = new HashMap();
    static {
        XML_UNESCAPE_MAP.put("&amp;", "&");
        XML_UNESCAPE_MAP.put("&lt;", "<");
        XML_UNESCAPE_MAP.put("&gt;", ">");
        XML_UNESCAPE_MAP.put("&quot;", "\"");
        XML_UNESCAPE_MAP.put("&apos;", "'");
        XML_UNESCAPE_MAP.put("&#13;", "\r");
        XML_UNESCAPE_MAP.put("&#10;", "\n");
        XML_UNESCAPE_MAP.put("&#9;", "\t");
    }

    public static String xmlDecode(String value) {
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '&') {
                // check for escape sequence.
                String replacement = null;
                for(int j = i + 1; j < i + 6 && j < value.length(); j++) {
                    if (value.charAt(j) == ';' && j - i > 1) {
                        // try to uescape from i + 1 to j - 1;
                        String escape = value.substring(i, j + 1); // full token like &..;
                        replacement = (String) XML_UNESCAPE_MAP.get(escape);
                        if (replacement != null) {
                            result.append(replacement);
                            // change 'i' value 
                            i = j;
                        }
                        break;
                    }
                }
                if (replacement != null) {
                    continue;
                }
            }
            result.append(ch);
        }
        return result.toString();
    }

    public static byte[] getBaseEOL(String eolStyle) {
        if ("native".equals(eolStyle)) {
            return LF;
        } else if ("CR".equals(eolStyle)) {
            return CR;
        } else if ("LF".equals(eolStyle)) {
            return LF;
        } else if ("CRLF".equals(eolStyle)) {
            return CRLF;
        }
        return null;
    }

    public static byte[] getWorkingEOL(String eolStyle) {
        if ("native".equals(eolStyle)) {
            return NATIVE;
        } else if ("CR".equals(eolStyle)) {
            return CR;
        } else if ("LF".equals(eolStyle)) {
            return LF;
        } else if ("CRLF".equals(eolStyle)) {
            return CRLF;
        }
        return null;
    }
}
