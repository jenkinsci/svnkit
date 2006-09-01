/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * Contains factories for different formats.
 * 
 * Each factory knows:
 * 
 * - whether directory is versioned.
 * - how to create new admin area.
 * - how to upgrade from one area to another (save area in certain format).
 * 
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public abstract class SVNAdminAreaFactory implements Comparable {
    
    private static final Collection ourFactories = new TreeSet();
    
    static {
        SVNAdminAreaFactory.registerFactory(new SVNAdminArea14Factory());
        SVNAdminAreaFactory.registerFactory(new SVNXMLAdminAreaFactory());
    }
    
    public static int checkWC(File path) throws SVNException {
        SVNException error = null;
        int version = -1;
        for(Iterator factories = ourFactories.iterator(); factories.hasNext();) {
            SVNAdminAreaFactory factory = (SVNAdminAreaFactory) factories.next();
            try {
                version = factory.doCheckWC(path);
                if (version == 0) {
                    return version;
                }
                
                if (version > factory.getSupportedVersion()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, 
                            "This client is too old to work with working copy ''{0}''; please get a newer Subversion client", 
                            path);
                    SVNErrorManager.error(err);
                } else if (version < factory.getSupportedVersion()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, 
                            "Working copy format of {0} is too old ({1}); please check out your working copy again", 
                            new Object[] {path, new Integer(version)});
                    SVNErrorManager.error(err);
                } 
            } catch (SVNException e) {
                error = e;
                continue;
            }
            return version;
        }
        throw error;
    }
    
    public static SVNAdminArea open(File path) throws SVNException {
        SVNErrorMessage error = null;
        int version = -1;
        
        for(Iterator factories = ourFactories.iterator(); factories.hasNext();) {
            SVNAdminAreaFactory factory = (SVNAdminAreaFactory) factories.next();
            try {
                version = factory.getVersion(path);
                if (version > factory.getSupportedVersion()) {
                    error = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, 
                            "This client is too old to work with working copy ''{0}''; please get a newer Subversion client", 
                            path);
                    SVNErrorManager.error(error);
                } else if (version < factory.getSupportedVersion()) {
                    error = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, 
                            "Working copy format of {0} is too old ({1}); please check out your working copy again", 
                            new Object[] {path, new Integer(version)});
                    SVNErrorManager.error(error);
                } 
            } catch (SVNException e) {
                continue;
            }
            SVNAdminArea adminArea = factory.doOpen(path, version);
            if (adminArea != null) {
                return adminArea;
            }
        }
        error = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
        SVNErrorManager.error(error);
        return null;
    }

    public static SVNAdminArea createVersionedDirectory(File dir) throws SVNException {
        if (!ourFactories.isEmpty()) {
            SVNAdminAreaFactory newestFactory = (SVNAdminAreaFactory) ourFactories.iterator().next();
            return newestFactory.doCreateVersionedDirectory(dir);
        }
        return null;
    }

    public static SVNAdminArea upgrade(SVNAdminArea area) throws SVNException {
        if (!ourFactories.isEmpty()) {
            SVNAdminAreaFactory newestFactory = (SVNAdminAreaFactory) ourFactories.iterator().next();
            area = newestFactory.doUpgrade(area);
        }
        return area;
    }
    
    protected String getAdminDirectoryName() {
        return SVNFileUtil.getAdminDirectoryName();
    }

    protected abstract int getSupportedVersion();
    
    protected abstract int getVersion(File path) throws SVNException;
    
    protected abstract SVNAdminArea doOpen(File path, int version) throws SVNException;

    protected abstract SVNAdminArea doCreateVersionedDirectory(File path) throws SVNException;

    protected abstract SVNAdminArea doUpgrade(SVNAdminArea area) throws SVNException;

    protected abstract int doCheckWC(File path) throws SVNException;

    protected static void registerFactory(SVNAdminAreaFactory factory) {
        if (factory != null) {
            ourFactories.add(factory);
        }
    }

    public int compareTo(Object o) {
        if (o == null || !(o instanceof SVNAdminAreaFactory)) {
            return -1;
        }
        int version = ((SVNAdminAreaFactory) o).getSupportedVersion(); 
        return getSupportedVersion() > version ? -1 : (getSupportedVersion() < version) ? 1 : 0; 
    }
}
