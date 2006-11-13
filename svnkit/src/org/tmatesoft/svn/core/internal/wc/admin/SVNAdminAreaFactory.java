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
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
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
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public abstract class SVNAdminAreaFactory implements Comparable {
    
    public static final int WC_FORMAT_14 = SVNAdminArea14Factory.WC_FORMAT;
    public static final int WC_FORMAT_13 = SVNXMLAdminAreaFactory.WC_FORMAT;
    
    private static final Collection ourFactories = new TreeSet();
    private static boolean ourIsUpgradeEnabled = Boolean.valueOf(System.getProperty("svnkit.upgradeWC", System.getProperty("javasvn.upgradeWC", "true"))).booleanValue();
    private static ISVNAdminAreaFactorySelector ourSelector;
    private static ISVNAdminAreaFactorySelector ourDefaultSelector = new DefaultSelector();
    
    static {
        SVNAdminAreaFactory.registerFactory(new SVNAdminArea14Factory());
        SVNAdminAreaFactory.registerFactory(new SVNXMLAdminAreaFactory());
    }
    
    public static void setUpgradeEnabled(boolean enabled) {
        ourIsUpgradeEnabled = enabled;
    }

    public static boolean isUpgradeEnabled() {
        return ourIsUpgradeEnabled;
    }
    
    public static void setSelector(ISVNAdminAreaFactorySelector selector) {
        ourSelector = selector;
    }
    
    public static ISVNAdminAreaFactorySelector getSelector() {
        return ourSelector != null ? ourSelector : ourDefaultSelector;
    }
    
    public static int checkWC(File path, boolean useSelector) throws SVNException {
        Collection enabledFactories = ourFactories;
        if (useSelector) {
            enabledFactories = getSelector().getEnabledFactories(path, enabledFactories, false);
        }
        SVNException error = null;
        int version = -1;
        for(Iterator factories = enabledFactories.iterator(); factories.hasNext();) {
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
                            "Working copy format of {0} is too old ({1,number,integer}); please check out your working copy again", 
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
        Collection enabledFactories = getSelector().getEnabledFactories(path, ourFactories, false);
        
        for(Iterator factories = enabledFactories.iterator(); factories.hasNext();) {
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
                error = e.getErrorMessage() ;
                continue;
            }
            SVNAdminArea adminArea = factory.doOpen(path, version);
            if (adminArea != null) {
                return adminArea;
            }
        }
        if (error == null) {
            error = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
        }
        SVNErrorManager.error(error);
        return null;
    }

    public static SVNAdminArea upgrade(SVNAdminArea area) throws SVNException {
        if (isUpgradeEnabled() && !ourFactories.isEmpty()) {
            Collection enabledFactories = getSelector().getEnabledFactories(area.getRoot(), ourFactories, true);
            if (!enabledFactories.isEmpty()) {
                SVNAdminAreaFactory newestFactory = (SVNAdminAreaFactory) enabledFactories.iterator().next();
                area = newestFactory.doUpgrade(area);
            }
        }
        return area;
    }
    
    private static int readFormatVersion(File adminDir) throws SVNException {
        SVNErrorMessage error = null;
        int version = -1;
        
        Collection enabledFactories = getSelector().getEnabledFactories(adminDir.getParentFile(), ourFactories, false);
        for(Iterator factories = enabledFactories.iterator(); factories.hasNext();) {
            SVNAdminAreaFactory factory = (SVNAdminAreaFactory) factories.next();
            try {
                version = factory.getVersion(adminDir);
            } catch (SVNException e) {
                error = e.getErrorMessage();
                continue;
            }
            return version;
        }

        if (error == null) {
            error = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", adminDir);
        }
        SVNErrorManager.error(error);
        return -1;
    }

    public static void createVersionedDirectory(File path, String url, String rootURL, String uuid, long revNumber) throws SVNException {
        if (!ourFactories.isEmpty()) {
            if (!checkAdminAreaExists(path, url, revNumber)) {
                Collection enabledFactories = getSelector().getEnabledFactories(path, ourFactories, true);
                if (!enabledFactories.isEmpty()) {
                    SVNAdminAreaFactory newestFactory = (SVNAdminAreaFactory) enabledFactories.iterator().next();
                    newestFactory.doCreateVersionedDirectory(path, url, rootURL, uuid, revNumber);
                }
            }
        }
    }

    public static void createVersionedDirectory(File path, SVNURL url, SVNURL rootURL, String uuid, long revNumber) throws SVNException {
        createVersionedDirectory(path, url != null ? url.toString() : null, rootURL != null ? rootURL.toString() : null, uuid, revNumber);
    }
        
    private static boolean checkAdminAreaExists(File dir, String url, long revision) throws SVNException {
        File adminDir = new File(dir, SVNFileUtil.getAdminDirectoryName());
        if (adminDir.exists() && !adminDir.isDirectory()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "''{0}'' is not a directory", dir);
            SVNErrorManager.error(err);
        } else if (!adminDir.exists()) {
            return false;
        } 
        
        boolean wcExists = false;
        try {
            readFormatVersion(adminDir);
            wcExists = true;
        } catch (SVNException svne) {
            return false;
        }
        
        if (wcExists) {
            SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
            SVNAdminArea adminArea = wcAccess.open(dir, false, 0);
            SVNEntry entry = adminArea.getEntry(adminArea.getThisDirName(), false);
            wcAccess.closeAdminArea(dir);
            if (entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "No entry for ''{0}''", dir);
                SVNErrorManager.error(err);
            }
            if (!entry.isScheduledForDeletion()) {
                if (entry.getRevision() != revision) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Revision {0,number,integer} doesn''t match existing revision {1,number,integer} in ''{2}''", new Object[]{new Long(revision), new Long(entry.getRevision()), dir});
                    SVNErrorManager.error(err);
                }
                if (!url.equals(entry.getURL())) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "URL ''{0}'' doesn''t match existing URL ''{1}'' in ''{2}''", new Object[]{url, entry.getURL(), dir});
                    SVNErrorManager.error(err);
                }
            }
        }
        return wcExists;
    }

    public abstract int getSupportedVersion();
    
    protected abstract int getVersion(File path) throws SVNException;
    
    protected abstract SVNAdminArea doOpen(File path, int version) throws SVNException;

    protected abstract SVNAdminArea doUpgrade(SVNAdminArea area) throws SVNException;

    protected abstract void doCreateVersionedDirectory(File path, String url, String rootURL, String uuid, long revNumber) throws SVNException;
    
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
    
    private static class DefaultSelector implements ISVNAdminAreaFactorySelector {

        public Collection getEnabledFactories(File path, Collection factories, boolean writeAccess) throws SVNException {
            return factories;
        }

    }
}
