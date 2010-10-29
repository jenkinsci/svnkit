/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;
import java.util.Iterator;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @author TMate Software Ltd.
 */
public class SVNWCUtils {

    public static SVNDate readDate(long date) {
        long time = date / 1000;
        return new SVNDate(time, (int) (date - time * 1000));
    }

    public static SVNProperties propDiffs(SVNProperties targetProps, SVNProperties sourceProps) {
        SVNProperties propdiffs = new SVNProperties();
        for (Iterator i = sourceProps.nameSet().iterator(); i.hasNext();) {
            String key = (String) i.next();
            String propVal1 = sourceProps.getStringValue(key);
            String propVal2 = targetProps.getStringValue(key);
            if (propVal2 == null) {
                SVNPropertyValue p = SVNPropertyValue.create(null);
                propdiffs.put(key, p);
            } else if (!propVal1.equals(propVal2)) {
                SVNPropertyValue p = SVNPropertyValue.create(propVal2);
                propdiffs.put(key, p);
            }
        }
        for (Iterator i = targetProps.nameSet().iterator(); i.hasNext();) {
            String key = (String) i.next();
            String propVal = targetProps.getStringValue(key);
            if (null == sourceProps.getStringValue(key)) {
                SVNPropertyValue p = SVNPropertyValue.create(propVal);
                propdiffs.put(key, p);
            }
        }
        return propdiffs;
    }

    public static int relpathDepth(File relpath) {
        if (relpath == null) {
            return 0;
        }
        return relpathDepth(relpath.getPath());
    }

    public static int relpathDepth(String relpath) {
        int n = 1;
        if (relpath == null) {
            return 0;
        }
        int length = relpath.length();
        for (int i = 0; i < length; i++) {
            if (relpath.charAt(i) == '/')
                n++;
        }
        return n;
    }

    public static class UnserializedFileExternalInfo {

        public String path = null;
        public SVNRevision pegRevision = SVNRevision.UNDEFINED;
        public SVNRevision revision = SVNRevision.UNDEFINED;
    }

    public static UnserializedFileExternalInfo unserializeFileExternal(String str) throws SVNException {
        final UnserializedFileExternalInfo info = new UnserializedFileExternalInfo();
        if (str != null) {
            StringBuffer buffer = new StringBuffer(str);
            info.pegRevision = SVNAdminUtil.parseRevision(buffer);
            info.revision = SVNAdminUtil.parseRevision(buffer);
            info.path = buffer.toString();
        }
        return info;
    }

    public static String serializeFileExternal(String path, SVNRevision pegRevision, SVNRevision revision) throws SVNException {
        String representation = null;
        if (path != null) {
            String revStr = SVNAdminUtil.asString(revision, path);
            String pegRevStr = SVNAdminUtil.asString(pegRevision, path);
            representation = pegRevStr + ":" + revStr + ":" + path;
        }
        return representation;
    }

    public static String getPathAsChild(File parent, File child) {
        if (parent == null || child == null)
            return null;
        if (parent.equals(child))
            return null;
        final String parentPath = parent.toString();
        final String childPath = child.toString();
        if (!childPath.startsWith(parentPath))
            return null;
        final String restPath = childPath.substring(parentPath.length());
        if (restPath.startsWith(File.separator)) {
            return restPath.substring(1);
        }
        return restPath;
    }

    public static boolean isAncestor(File parent, File child) {
        if (parent == null || child == null)
            return false;
        if (parent.equals(child))
            return false;
        final String parentPath = parent.toString();
        final String childPath = child.toString();
        return childPath.startsWith(parentPath);
    }

}
