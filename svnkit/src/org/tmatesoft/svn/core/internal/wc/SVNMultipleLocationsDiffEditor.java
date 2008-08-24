/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Iterator;

import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNFileLocationsFinder;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;

/**
 * @author TMate Software Ltd.
 * @version 1.2
 * @since 1.2
 */
public class SVNMultipleLocationsDiffEditor extends SVNRemoteDiffEditor {

    private ISVNFileLocationsFinder myFileLocationsFinder;
    private Collection myCurrentLocations;

    public SVNMultipleLocationsDiffEditor(ISVNFileLocationsFinder fileLocatonsFinder, SVNAdminArea adminArea, File target, AbstractDiffCallback callback, SVNRepository repos, long revision1, long revision2, boolean dryRun, ISVNEventHandler handler, ISVNEventHandler cancelHandler) {
        super(adminArea, target, callback, repos, revision1, revision2, dryRun, handler, cancelHandler);
        myFileLocationsFinder = fileLocatonsFinder;
        myCurrentLocations = new ArrayList();
    }

    private ISVNFileLocationsFinder getCopiedLocationsFinder() {
        return myFileLocationsFinder;
    }

    public void openFile(String path, long revision) throws SVNException {
        super.openFile(path, revision);
        myCurrentLocations = getCopiedFileInfos(getCurrentFile().myWCFile);
    }

    private Collection getCopiedFileInfos(File file) {
        Collection copiedTo = getCopiedLocationsFinder().findLocations(file);
        if (copiedTo == null) {
            return null;
        }
        
        Collection infos = new ArrayList(copiedTo.size());
        for (Iterator iterator = copiedTo.iterator(); iterator.hasNext();) {
            File location = (File) iterator.next();
            infos.add(new SVNFileLocationInfo(location));
        }
        return infos;
    }

    public void changeFileProperty(String commitPath, String name, SVNPropertyValue value) throws SVNException {
        super.changeFileProperty(commitPath, name, value);
    }

    public void applyTextDelta(String commitPath, String baseChecksum) throws SVNException {
        if (myCurrentLocations == null) {
            super.applyTextDelta(commitPath, baseChecksum);
            return;
        }
        
        for (Iterator iterator = myCurrentLocations.iterator(); iterator.hasNext();) {
            SVNFileLocationInfo fileInfo = (SVNFileLocationInfo) iterator.next();
            SVNAdminArea dir;
            try {
                dir = retrieveParent(fileInfo.myWCFile, true);
            } catch (SVNException e) {
                dir = null;
            }
            fileInfo.myFile = createTempFile(dir, SVNPathUtil.tail(fileInfo.myWCFile.getAbsolutePath()));
            fileInfo.myProcessor.applyTextDelta(getCurrentFile().myBaseFile, fileInfo.myFile, false);
        }
    }

    public OutputStream textDeltaChunk(String commitPath, SVNDiffWindow diffWindow) throws SVNException {
        if (myCurrentLocations == null) {
            return super.textDeltaChunk(commitPath, diffWindow);
        }
        
        for (Iterator iterator = myCurrentLocations.iterator(); iterator.hasNext();) {
            SVNFileLocationInfo fileInfo = (SVNFileLocationInfo) iterator.next();
            fileInfo.myProcessor.textDeltaChunk(diffWindow);
        }
        return SVNFileUtil.DUMMY_OUT;
    }

    public void textDeltaEnd(String commitPath) throws SVNException {
        if (myCurrentLocations == null) {
            super.textDeltaEnd(commitPath);
            return;
        }
        
        for (Iterator iterator = myCurrentLocations.iterator(); iterator.hasNext();) {
            SVNFileLocationInfo fileInfo = (SVNFileLocationInfo) iterator.next();
            fileInfo.myProcessor.textDeltaEnd();
        }
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        if (myCurrentLocations == null) {
            super.closeFile(commitPath, textChecksum);
            return;
        }
        
        for (Iterator iterator = myCurrentLocations.iterator(); iterator.hasNext();) {
            SVNFileLocationInfo fileInfo = (SVNFileLocationInfo) iterator.next();
            closeFile(fileInfo.myPath, getCurrentFile().myIsAdded, fileInfo.myWCFile, getCurrentFile().myBaseFile,
                    fileInfo.myFile, getCurrentFile().myBaseProperties, getCurrentFile().myPropertyDiff);
        }
    }

    private class SVNFileLocationInfo {
        private SVNDeltaProcessor myProcessor;
        private String myPath;
        private File myFile;
        private File myWCFile;

        public SVNFileLocationInfo(File file) {
            myProcessor = new SVNDeltaProcessor();
            myWCFile = file;
            myPath = SVNPathUtil.getRelativePath(getTarget().getAbsolutePath(), myWCFile.getAbsolutePath());
        }
    }
}
