/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test.wc;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNTestFile extends AbstractSVNTestFile {

    private static final SVNWCAccess ourWCAccess = SVNWCAccess.newInstance(SVNTestDebugLog.getEventHandler());

    private File myWCRoot;
    private File myFile;

    public SVNTestFile(File wcRoot, AbstractSVNTestFile file) {
        super(file);
        myWCRoot = wcRoot;
    }

    public SVNTestFile(File wcRoot, String path, SVNFileType fileType, byte[] content) {
        super(path, fileType, content);
        myWCRoot = wcRoot;
    }

    public SVNTestFile(File wcRoot, String path) {
        super(path);
        myWCRoot = wcRoot;
    }

    public SVNTestFile(File wcRoot, String path, byte[] content) {
        super(path, content);
        myWCRoot = wcRoot;
    }

    public SVNTestFile(File wcRoot, String path, String content) {
        super(path, content);
        myWCRoot = wcRoot;
    }

    private static SVNWCAccess getWCAccess() {
        return ourWCAccess;
    }

    public File getWCRoot() {
        return myWCRoot;
    }

    public File getFile() {
        if (myFile == null) {
            myFile = new File(getWCRoot(), getPath()).getAbsoluteFile();            
        }
        return myFile;
    }

    public SVNTestFile loadContent() throws SVNException {
        SVNFileType fileType = SVNFileType.getType(getFile());
        setFileType(fileType);

        if (fileType == SVNFileType.NONE) {
            setContent(null);
            setLinkTarget(null);
            setDefaults();
        }

        if (fileType == SVNFileType.FILE) {
            String content = SVNFileUtil.readFile(getFile());
            try {
                setContent(content.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                setContent(content.getBytes());
            }
        }

        if (fileType == SVNFileType.SYMLINK) {
            String target = SVNFileUtil.getSymlinkName(getFile());
            setLinkTarget(target);
        }
        return this;
    }

    public AbstractSVNTestFile reload() throws SVNException {
        loadContent();
        SVNFileType fileType = getFileType();

        if (fileType == SVNFileType.NONE) {
            setLinkTarget(null);
        }
        
        synchronized (getWCAccess()) {
            SVNAdminArea dir = null;
            try {
                dir = getWCAccess().open(getFile().getParentFile(), false, 0);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                    setVersioned(false);
                }
            }
            if (dir != null) {
                SVNEntry entry = getWCAccess().getEntry(getFile(), true);
                if (entry != null) {
                    reload(entry, dir);
                } else {
                    setDefaults();
                }
            }
        }
        return this;
    }

    public void reload(SVNEntry entry, SVNAdminArea parentDir) throws SVNException {
        setVersioned(true);
        setNodeKind(entry.getKind());
        setCopyFromLocation(entry.getCopyFromSVNURL());
        setCopyFromRevision(entry.getCopyFromRevision());
        if (entry.isScheduledForAddition()) {
            setAdded(true);
        } else if (entry.isScheduledForDeletion()) {
            setDeleted(true);
        } else if (entry.isScheduledForReplacement()) {
            setReplaced(true);
        }

        SVNVersionedProperties baseProperties = parentDir.getBaseProperties(entry.getName());
        SVNVersionedProperties properties = parentDir.getProperties(entry.getName());
        setBaseProperties(baseProperties == null ? null : baseProperties.asMap());
        setProperties(properties == null ? null : properties.asMap());

        setConflicted(parentDir.hasTextConflict(entry.getName()) || parentDir.hasPropConflict(entry.getName()));
    }

    public AbstractSVNTestFile dump(File workingCopyRoot) throws SVNException {
        if (!getWCRoot().equals(workingCopyRoot)) {
            throw new IllegalArgumentException("SVNTestFile#dump should be called with the same wc root as cached at the instance");
        }
        File path = new File(workingCopyRoot, getPath());
        if (getFileType() == SVNFileType.DIRECTORY) {
            path.mkdir();
        } else if (getFileType() == SVNFileType.FILE) {
            OutputStream os = SVNFileUtil.openFileForWriting(path);
            try {
                os.write(getContent());
            } catch (IOException e) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
                SVNErrorManager.error(error, SVNLogType.DEFAULT);
            } finally {
                SVNFileUtil.closeFile(os);
            }
        } else if (getFileType() == SVNFileType.SYMLINK) {
            String target = SVNPathUtil.getRelativePath(getPath(), getLinkTarget());
            SVNFileUtil.createSymlink(path, target);
        }
        return this;
    }
}
