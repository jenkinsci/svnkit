package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 12.06.2005
 * Time: 19:20:52
 * To change this template use File | Settings | File Templates.
 */
public class SVNPathUtil {

    public static String getCommonPathAncestor(String path1, String path2) {
        if (path1 == null || path2 == null) {
            return null;
        }
        path1 = path1.replace(File.separatorChar, '/');
        path2 = path2.replace(File.separatorChar, '/');

        int index = 0;
        int separatorIndex = 0;
        while(index < path1.length() && index < path2.length()) {
            if (path1.charAt(index) != path2.charAt(index)) {
                break;
            }
            if (path1.charAt(index) == '/') {
                separatorIndex = index;
            }
            index++;
        }
        if (index == path1.length() && index == path2.length()) {
            return path1;
        } else if (index == path1.length() && path2.charAt(index) == '/') {
            return path1;
        } else if (index == path2.length() && path1.charAt(index) == '/') {
            return path2;
        }
        return path1.substring(0, separatorIndex);
    }

    public static String getCommonURLAncestor(String url1, String url2) {
        // skip protocol and host, if they are different -> return "";
        if (url1 == null || url2 == null) {
            return null;
        }
        int index = 0;
        StringBuffer protocol = new StringBuffer();
        while(index < url1.length() && index < url2.length()) {
            char ch1 = url1.charAt(index);
            if (ch1 != url2.charAt(index)) {
                return "";
            }
            if (ch1 == ':') {
                break;
            }
            protocol.append(ch1);
            index++;
        }
        index += 3; // skip ://
        protocol.append("://");
        if (index >= url1.length() || index >= url2.length()) {
            return "";
        }
        protocol.append(getCommonPathAncestor(url1.substring(index), url2.substring(index)));
        return protocol.toString();
    }

    public static File getCommonFileAncestor(File file1, File file2) {
        String path1 = file1.getAbsolutePath();
        String path2 = file2.getAbsolutePath();
        path1 = validateFilePath(path1);
        path2 = validateFilePath(path2);
        String commonPath = getCommonPathAncestor(path1, path2);
        if (commonPath != null) {
            return new File(commonPath);
        }
        return null;
    }

    public static String validateFilePath(String path) {
        path = path.replace(File.separatorChar, '/');
        StringBuffer result = new StringBuffer();
        List segments = new LinkedList();
        for(StringTokenizer tokens = new StringTokenizer(path, "/", false); tokens.hasMoreTokens();) {
            String segment = tokens.nextToken();
            if ("..".equals(segment)) {
                if (!segments.isEmpty()) {
                    segments.remove(segments.size() - 1);
                } else {
                    File root = new File(System.getProperty("user.dir"));
                    while(root.getParentFile() != null) {
                        segments.add(0, root.getParentFile().getName());
                        root = root.getParentFile();
                    }
                }
                continue;
            } else if (".".equals(segment) || segment.length() == 0) {
                continue;
            }
            segments.add(segment);
        }
        if (path.length() > 0 && path.charAt(0) == '/') {
            result.append("/");
        }
        for (Iterator tokens = segments.iterator(); tokens.hasNext();) {
            String token = (String) tokens.next();
            result.append(token);
            if (tokens.hasNext()) {
                result.append('/');
            }
        }
        return result.toString();
    }

    public static boolean isChildOf(File parentFile, File childFile) {
        if (parentFile == null || childFile == null) {
            return false;
        }
        childFile = childFile.getParentFile();
        while(childFile != null) {
            if (childFile.equals(parentFile)) {
                return true;
            }
            childFile = childFile.getParentFile();
        }
        return false;
    }
}
