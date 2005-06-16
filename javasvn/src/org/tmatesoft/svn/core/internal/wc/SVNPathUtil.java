package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.Collection;
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

    public static String condenceURLs(String[] urls, Collection condencedPaths, boolean removeRedundantURLs) {
        if (urls == null || urls.length == 0) {
            return null;
        }
        if (urls.length == 1) {
            return urls[0];
        }
        String rootURL = urls[0];
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i];
            rootURL = getCommonURLAncestor(rootURL, url);
        }
        if (condencedPaths != null && removeRedundantURLs) {
            for (int i = 0; i < urls.length; i++) {
                String url1 = urls[i];
                if (url1 == null) {
                    continue;
                }
                for (int j = 0; j < urls.length; j++) {
                    String url2 = urls[j];
                    if (url2 == null) {
                        continue;
                    }
                    String common = getCommonURLAncestor(url1, url2);
                    if ("".equals(common) || common == null) {
                        continue;
                    }
                    if (common.equals(url1)) {
                        urls[i] = null;
                    } else if (common.equals(url2)) {
                        urls[j] = null;
                    }
                }
            }
            for (int j = 0; j < urls.length; j++) {
                String url = urls[j];
                if (url != null && url.equals(rootURL)) {
                    urls[j] = null;
                }
            }
        }

        if (condencedPaths != null) {
            for (int i = 0; i < urls.length; i++) {
                String url = urls[i];
                if (url == null) {
                    continue;
                }
                if (rootURL != null && !"".equals(rootURL)) {
                    url = url.substring(rootURL.length());
                    if (url.startsWith("/")) {
                        url = url.substring(1);
                    }
                }
                condencedPaths.add(url);
            }
        }
        return rootURL;
    }

    public static String condencePaths(String[] paths, Collection condencedPaths, boolean removeRedundantPaths) {
        if (paths == null || paths.length == 0) {
            return null;
        }
        if (paths.length == 1) {
            return paths[0];
        }
        String rootPath = paths[0];
        for (int i = 0; i < paths.length; i++) {
            String url = paths[i];
            rootPath = getCommonPathAncestor(rootPath, url);
        }
        if (condencedPaths != null && removeRedundantPaths) {
            for (int i = 0; i < paths.length; i++) {
                String path1 = paths[i];
                if (path1 == null) {
                    continue;
                }
                for (int j = 0; j < paths.length; j++) {
                    String path2 = paths[j];
                    if (path2 == null) {
                        continue;
                    }
                    String common = getCommonPathAncestor(path1, path2);
                    if ("".equals(common) || common == null) {
                        continue;
                    }
                    if (common.equals(path1)) {
                        paths[i] = null;
                    } else if (common.equals(path2)) {
                        paths[j] = null;
                    }
                }
            }
            for (int j = 0; j < paths.length; j++) {
                String path = paths[j];
                if (path != null && path.equals(rootPath)) {
                    paths[j] = null;
                }
            }
        }

        if (condencedPaths != null) {
            for (int i = 0; i < paths.length; i++) {
                String path = paths[i];
                if (path == null) {
                    continue;
                }
                if (rootPath != null && !"".equals(rootPath)) {
                    path = path.substring(rootPath.length());
                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }
                }
                condencedPaths.add(path);
            }
        }
        return rootPath;
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
