/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashSet;

import org.tmatesoft.svn.core.ISVNDirectoryEntry;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 */
class SVNExternal {
    
    private String myPath;
    private long myRevision;
    private SVNRepositoryLocation myLocation;
    
    public SVNExternal(String path, long revision, SVNRepositoryLocation location) {
        myPath = path;
        myRevision = revision;
        myLocation = location;
    }
    
    public SVNRepositoryLocation getLocation() {
        return myLocation;
    }
    
    public String getPath() {
        return myPath;
    }
    
    public long getRevision() {
        return myRevision;
    }
    
    public int hashCode() {
        return myLocation.toString().hashCode() + 7*myPath.hashCode() + 21*(myRevision + "").hashCode();
    }
    
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (o.getClass() != SVNExternal.class) {
            return false;
        }
        SVNExternal e = (SVNExternal) o;
        return e.myLocation.toString().equals(myLocation.toString()) && 
            e.myPath.equals(myPath) && 
            e.myRevision == myRevision;
    }
    
    public static Collection create(ISVNDirectoryEntry entry, Collection externals) {
        String propertyValue = null;
        try {
            propertyValue = entry.getPropertyValue(SVNProperty.EXTERNALS);
        } catch (SVNException e2) {
            return externals;
        }
        return create(entry, propertyValue, externals);
        
    }
    
    public static Collection create(ISVNDirectoryEntry entry, String propertyValue, Collection externals) {        
        if (entry == null || propertyValue == null) {
            return externals;
        }
        if (propertyValue == null) {
            return externals;
        }
        String basePath = entry.getPath();
        // process lines.
        BufferedReader reader = new BufferedReader(new StringReader(propertyValue));
        String line;
        try {
            while((line = reader.readLine()) != null) {
                int index = line.lastIndexOf("://");
                if (index < 0) {
                    continue;
                }
                index = line.lastIndexOf(" ", index);
                String url = line.substring(index + 1);
                SVNRepositoryLocation location = null;
                try {
                    location = SVNRepositoryLocation.parseURL(url);
                } catch (SVNException e1) {
                }
                if (location == null) {
                    continue;
                }
                line = line.substring(0, index);
                long revision = -1;
                index = line.lastIndexOf("-r");
                if (index > 0) {
                    String rev = line.substring(index + "-r".length()).trim();
                    revision = Long.parseLong(rev);
                    line = line.substring(0, index);
                }
                // locate path.
                String path = PathUtil.append(basePath, line.trim());
                path = PathUtil.removeLeadingSlash(path);
                if (externals == null) {
                    externals = new HashSet();
                }
                externals.add(new SVNExternal(path, revision, location));
            }
            reader.close();
        } catch (IOException e) {
        }
        return externals;
    }
}
