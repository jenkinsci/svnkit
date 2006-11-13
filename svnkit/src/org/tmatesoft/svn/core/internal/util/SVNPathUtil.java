/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util;

import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNPathUtil {
    
    public static final Comparator PATH_COMPARATOR = new Comparator() {

        public int compare(Object o1, Object o2) {
            if (o1 == o2) {
                return 0;
            } else if (o1 == null) {
                return -1;
            } else if (o2 == null) {
                return 1;
            } else if (o1.getClass() != String.class || o2.getClass() != String.class) {
                return o1.getClass() == o2.getClass() ? 0 : o1.getClass() == String.class ? 1 : -1;
            }
            String p1 = (String) o1;
            String p2 = (String) o2;
            return p1.replace('/', '\0').toLowerCase().compareTo(p2.toLowerCase().replace('/', '\0'));
        }
        
    };
    
    public static String append(String f, String s) {
        f = f == null ? "" : f;
        s = s == null ? "" : s;
        int l1 = f.length();
        int l2 = s.length();
        char[] r = new char[l1 + l2 + 2];
        int index = 0;
        for(int i = 0; i < l1; i++) {
            char ch = f.charAt(i);
            if (i + 1 == l1 && ch == '/') {
                break;
            }
            r[index++] = ch;
        }
        for(int i = 0; i < l2; i++) {
            char ch = s.charAt(i);
            if (i == 0 && ch != '/' && index > 0) {
                r[index++] = '/';
            }
            if (i + 1 == l2 && ch == '/') {
                break;
            }
            r[index++] = ch;
        }
        return new String(r, 0, index);
    }
    
    public static boolean isSinglePathComponent(String name){
        /* Can't be empty or `..'  */
        if(name == null || "".equals(name) || "..".equals(name)){
            return true;
        }
        /* Slashes are bad */
        if(name.indexOf('/') != -1){
            return false;
        }
        /* It is valid.  */
        return true; 
    }
    
    public static String concatToAbs(String f, String s) throws SVNException {
        f = f == null || "".equals(f) ? "/" : f;
        s = s == null ? "" : s;
        
        if (!isCanonical(f)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_PATH_SYNTAX, "path component ''{0}'' is not canonical", f);
            SVNErrorManager.error(err);
        }

        if (!isCanonical(s)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_PATH_SYNTAX, "path component ''{0}'' is not canonical", s);
            SVNErrorManager.error(err);
        }

        if("/".equals(f)){
            if(s.startsWith("/")){
                return s;
            }
            return "/" + s;
        }
        return append(f, s);
    }
    
    public static boolean isCanonical(String path) {
        return (path != null && !(path.length() == 1 && path.charAt(0) == '.') && (path.length() <= 1 || path.charAt(path.length() - 1) != '/'));
    }
    
    public static String removeTail(String path) {
        int index = path.length() - 1;
        while(index >= 0) {
            if (path.charAt(index) == '/') {
                return path.substring(0, index); 
            }
            index--;
        }
        return "";
    }

    public static String getCommonPathAncestor(String path1, String path2) {
        if (path1 == null || path2 == null) {
            return null;
        }
        path1 = path1.replace(File.separatorChar, '/');
        path2 = path2.replace(File.separatorChar, '/');
    
        int index = 0;
        int separatorIndex = 0;
        while (index < path1.length() && index < path2.length()) {
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

    public static String canonicalizeAbsPath(String path) {
        //No path, no problem
        if (path == null) {
            return null;
        }       
        
        //If no content in path
        if ("".equals(path)) {
            return "/";
        }

        StringBuffer newString = new StringBuffer();
        //Set leading '/' character
        if (!path.startsWith("/")) {
            newString.append('/');
        }

        //dispose of slashes number of that is 
        boolean eatingSlashes = false;
        for (int count = 0; count < path.length(); count++) {
            if (path.charAt(count) == '/') {
                if (eatingSlashes) {
                    continue;
                }
                eatingSlashes = true;
            } else {
                if (eatingSlashes) {
                    eatingSlashes = false;
                }
            }
            newString.append(path.charAt(count));
        }           

        if (newString.length() > 1 && newString.charAt(newString.length() - 1) == '/') {
            newString.deleteCharAt(newString.length() - 1);
        }
        
        return newString.toString();
    }    
    
    public static void checkPathIsValid(String path) throws SVNException {
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (SVNEncodingUtil.isASCIIControlChar(ch)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_PATH_SYNTAX, "Invalid control character ''{0}'' in path ''{1}''", new String[] {"''0x" + SVNFormatUtil.getHexNumberFromByte((byte)ch) + "''", path});
                SVNErrorManager.error(err);
            }
        }
    }
    
    public static String getCommonURLAncestor(String url1, String url2) {
        // skip protocol and host, if they are different -> return "";
        if (url1 == null || url2 == null) {
            return null;
        }
        int index = 0;
        StringBuffer protocol = new StringBuffer();
        while (index < url1.length() && index < url2.length()) {
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
        protocol.append(getCommonPathAncestor(url1.substring(index), url2
                .substring(index)));
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

    public static String condenceURLs(String[] urls, Collection condencedPaths,
            boolean removeRedundantURLs) {
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
                    if (i == j) {
                        continue;
                    }
                    String url2 = urls[j];
                    if (url2 == null) {
                        continue;
                    }
                    String common = getCommonURLAncestor(url1, url2);
                    if ("".equals(common) || common == null) {
                        continue;
                    }
                    if (common.equals(url1)) {
                        urls[j] = null;
                    } else if (common.equals(url2)) {
                        urls[i] = null;
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
                    if (i == j) {
                        continue;
                    }
                    String path2 = paths[j];
                    if (path2 == null) {
                        continue;
                    }
                    String common = getCommonPathAncestor(path1, path2);
    
                    if ("".equals(common) || common == null) {
                        continue;
                    }
                    // remove logner path here
                    if (common.equals(path1)) {
                        paths[j] = null;
                    } else if (common.equals(path2)) {
                        paths[i] = null;
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
        for (StringTokenizer tokens = new StringTokenizer(path, "/", false); tokens
                .hasMoreTokens();) {
            String segment = tokens.nextToken();
            if ("..".equals(segment)) {
                if (!segments.isEmpty()) {
                    segments.remove(segments.size() - 1);
                } else {
                    File root = new File(System.getProperty("user.dir"));
                    while (root.getParentFile() != null) {
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
        if (path.length() > 1 && path.charAt(1) == '/') {
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
        childFile = new File(SVNPathUtil.validateFilePath(childFile
                .getParentFile().getAbsolutePath()));
        parentFile = new File(SVNPathUtil.validateFilePath(parentFile
                .getAbsolutePath()));
        while (childFile != null) {
            if (childFile.equals(parentFile)) {
                return true;
            }
            childFile = childFile.getParentFile();
        }
        return false;
    }

    public static String tail(String path) {
        int index = path.length() - 1;
        if (index >= 0 && index < path.length() && path.charAt(index) == '/') {
            index--;
        }
        for(int i = index; i >= 0; i--) {
            if (path.charAt(i) == '/') {
                return path.substring(i + 1, index + 1); 
            }
        }
        return path;
    }

    public static String head(String path) {
        for(int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                return path.substring(0, i); 
            }
        }
        return path;
    }

    public static String removeHead(String path) {
        for(int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                int ind = i;
                for(; ind < path.length(); ind++){
                    if(path.charAt(ind) == '/'){
                        continue;
                    }
                    break;
                }
                return path.substring(ind); 
            }
        }
        return "";
    }

    public static int getSegmentsCount(String path) {
        int count = path.length() > 0 ? 1 : 0;
        // skipe first char, then count number of '/'
        for(int i = 1; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                count++;
            }
        }        
        return count;
    }
    
    public static boolean isAncestor(String parentPath, String ancestorPath) {
        parentPath = parentPath == null ? "" : parentPath;
        ancestorPath = ancestorPath == null ? "" : ancestorPath;

        if (parentPath.length() == 0) {
            return !ancestorPath.startsWith("/");
        }
        
        if (ancestorPath.startsWith(parentPath)) {
            return parentPath.length() == ancestorPath.length() || parentPath.endsWith("/") || ancestorPath.charAt(parentPath.length()) == '/';
        }
        return false;
    }

    public static String pathIsChild(String path, String pathChild){
    	if(path == null || pathChild == null){
    		return null;
    	}
    	if(pathChild.compareTo(path) == 0){
    		return null;
    	}
    	int count = 0;
    	for(count = 0; count < path.length() && count < pathChild.length(); count++ ){
    		if(path.charAt(count) != pathChild.charAt(count)){
    			return null;
    		}
    	}    
    	if(count == path.length() && count < pathChild.length()){
            if(pathChild.charAt(count) == '/'){
                return pathChild.substring(count + 1);                      
            }else if(count == 1 && path.charAt(0) == '/'){
                return pathChild.substring(1);
            }
        }
    	return null;
    }
    
}
