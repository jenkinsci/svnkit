/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNDiffEditor;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNMergeEditor;
import org.tmatesoft.svn.core.internal.wc.SVNMerger;
import org.tmatesoft.svn.core.internal.wc.SVNRemoteDiffEditor;
import org.tmatesoft.svn.core.internal.wc.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNDiffClient extends SVNBasicClient {

    private ISVNDiffGenerator myDiffGenerator;

    public SVNDiffClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNDiffClient(ISVNRepositoryFactory repositoryFactory, ISVNOptions options) {
        super(repositoryFactory, options);
    }

    public void setDiffGenerator(ISVNDiffGenerator diffGenerator) {
        myDiffGenerator = diffGenerator;
    }

    public ISVNDiffGenerator getDiffGenerator() {
        if (myDiffGenerator == null) {
            myDiffGenerator = new DefaultSVNDiffGenerator();
        }
        return myDiffGenerator;
    }

    public void doDiff(SVNURL url, SVNRevision pegRevision, SVNRevision rN, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorManager.error("svn: Not all required revisions are specified");
        }
        if (rN.isLocal() || rM.isLocal()) {
            SVNErrorManager.error("svn: Both revisions must be non-local for " +
                                   "a pegged diff of an URL");
        }
        getDiffGenerator().init(url.toString(), url.toString());
        doDiffURLURL(url, null, rN, url, null, rM, pegRevision, recursive, useAncestry, result);
    }
    
    public void doDiff(File path, SVNRevision pegRevision, SVNRevision rN, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorManager.error("svn: Not all required revisions are specified");
        }
        if (rN.isLocal() && rM.isLocal()) {
            SVNErrorManager.error("svn: At least one revision must be non-local for " +
                                   "a pegged diff");
        }
        getDiffGenerator().init(path.getAbsolutePath(), path.getAbsolutePath());
        if (!rM.isLocal()) {
            doDiffURLURL(null, path, rN, null, path, rM, pegRevision, recursive, useAncestry, result);
        } else {
            doDiffURLWC(path, rN, pegRevision, path, rM, false, recursive, useAncestry, result);
        }
    }

    public void doDiff(SVNURL url1, SVNRevision rN, SVNURL url2, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorManager.error("svn: Not all required revisions are specified");
        }
        getDiffGenerator().init(url1.toString(), url2.toString());
        doDiffURLURL(url1, null, rN, url2, null, rM, SVNRevision.UNDEFINED, recursive, useAncestry, result);
    }
    public void doDiff(File path1, SVNRevision rN, SVNURL url2, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorManager.error("svn: Not all required revisions are specified");
        }
        getDiffGenerator().init(path1.getAbsolutePath(), url2.toString());
        if (rN == SVNRevision.BASE || rN == SVNRevision.WORKING) {
            doDiffURLWC(url2, rM, SVNRevision.UNDEFINED, path1, rN, true, recursive, useAncestry, result);
        } else {
            doDiffURLURL(null, path1, rN, url2, null, rM, SVNRevision.UNDEFINED, recursive, useAncestry, result);
        }
    }
    public void doDiff(SVNURL url1, SVNRevision rN, File path2, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorManager.error("svn: Not all required revisions are specified");
        }
        getDiffGenerator().init(url1.toString(), path2.getAbsolutePath());
        if (rM == SVNRevision.BASE || rM == SVNRevision.WORKING) {
            doDiffURLWC(url1, rN, SVNRevision.UNDEFINED, path2, rM, false, recursive, useAncestry, result);
        } else {
            doDiffURLURL(url1, null, rN, null, path2, rM, SVNRevision.UNDEFINED, recursive, useAncestry, result);
        }
    }
    public void doDiff(File path1, SVNRevision rN, File path2, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorManager.error("svn: Not all required revisions are specified");
        }
        boolean isPath1Local = rN == SVNRevision.WORKING || rN == SVNRevision.BASE; 
        boolean isPath2Local = rM == SVNRevision.WORKING || rM == SVNRevision.BASE;
        getDiffGenerator().init(path1.getAbsolutePath(), path2.getAbsolutePath());
        if (isPath1Local && isPath2Local) {
            doDiffWCWC(path1, rN, path2, rM, recursive, useAncestry, result);
        } else if (isPath1Local) {
            doDiffURLWC(path2, rM, SVNRevision.UNDEFINED, path1, rN, true, recursive, useAncestry, result);
        } else if (isPath2Local) {
            doDiffURLWC(path1, rN, SVNRevision.UNDEFINED, path2, rM, false, recursive, useAncestry, result);
        } else {
            doDiffURLURL(null, path1, rN, null, path2, rM, SVNRevision.UNDEFINED, recursive, useAncestry, result);
        }
    }
    
    private void doDiffURLWC(SVNURL url1, SVNRevision revision1, SVNRevision pegRevision, File path2, SVNRevision revision2, 
            boolean reverse, boolean recursive, boolean useAncestry, OutputStream result) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess(path2);
        wcAccess.open(false, recursive);
        
        File anchorPath = wcAccess.getAnchor().getRoot();
        String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
        
        SVNEntry anchorEntry = wcAccess.getAnchor().getEntries().getEntry("", false);
        if (anchorEntry == null) {
            SVNErrorManager.error("svn: '" + anchorPath + "' is not under version control");
        } else if (anchorEntry.getURL() == null) {
            SVNErrorManager.error("svn: '" + anchorPath + "' has no URL");
        }
        SVNURL anchorURL = anchorEntry.getSVNURL();
        if (pegRevision.isValid()) {
            SVNRepositoryLocation[] locations = getLocations(url1, null, pegRevision, revision1, SVNRevision.UNDEFINED);
            url1 = locations[0].getURL();
            String anchorPath2 = SVNPathUtil.append(anchorURL.toString(), target == null ? "" : target);
            getDiffGenerator().init(url1.toString(), anchorPath2);
        }
        SVNRepository repository = createRepository(anchorURL);
        SVNDiffEditor editor = new SVNDiffEditor(wcAccess, getDiffGenerator(),
                useAncestry, reverse /* reverse */,
                revision2 == SVNRevision.BASE /* compare to base */, result);
        SVNReporter reporter = new SVNReporter(wcAccess, false, recursive);
        long revNumber = getRevisionNumber(revision1, repository, null);
        
        repository.diff(url1, revNumber, target, !useAncestry, recursive, reporter, editor);
        
        wcAccess.close(false);
    }

    private void doDiffURLWC(File path1, SVNRevision revision1, SVNRevision pegRevision, File path2, SVNRevision revision2, 
            boolean reverse, boolean recursive, boolean useAncestry, OutputStream result) throws SVNException {
        SVNURL url1 = getURL(path1);
        
        SVNWCAccess wcAccess = createWCAccess(path2);
        wcAccess.open(false, recursive);
        
        File anchorPath = wcAccess.getAnchor().getRoot();
        String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
        
        SVNEntry anchorEntry = wcAccess.getAnchor().getEntries().getEntry("", false);
        if (anchorEntry == null) {
            SVNErrorManager.error("svn: '" + anchorPath + "' is not under version control");
        } else if (anchorEntry.getURL() == null) {
            SVNErrorManager.error("svn: '" + anchorPath + "' has no URL");
        }
        SVNURL anchorURL = anchorEntry.getSVNURL();
        if (pegRevision.isValid()) {
            SVNRepositoryLocation[] locations = getLocations(url1, path1, pegRevision, revision1, SVNRevision.UNDEFINED);
            url1 = locations[0].getURL();
            String anchorPath2 = SVNPathUtil.append(anchorURL.toString(), target == null ? "" : target);
            getDiffGenerator().init(url1.toString(), anchorPath2);
        }
        SVNRepository repository = createRepository(anchorURL);
        SVNDiffEditor editor = new SVNDiffEditor(wcAccess, getDiffGenerator(),
                useAncestry, reverse /* reverse */, revision2 == SVNRevision.BASE /* compare to base */, result);
        SVNReporter reporter = new SVNReporter(wcAccess, false, recursive);
        long revNumber = getRevisionNumber(revision1, repository, path1);
        
        repository.diff(url1, revNumber, target, !useAncestry, recursive, reporter, editor);
        
        wcAccess.close(false);
    }
    
    private void doDiffWCWC(File path1, SVNRevision revision1, File path2, SVNRevision revision2, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (!path1.equals(path2) || !(revision1 == SVNRevision.BASE && revision2 == SVNRevision.WORKING)) {
            SVNErrorManager.error("svn: Only diffs between a path's text-base " +
                                    "and its working files are supported at this time");
        }
        
        SVNWCAccess wcAccess = createWCAccess(path1);
        wcAccess.open(false, recursive);
        if (wcAccess.getTargetEntry() == null) {
            SVNErrorManager.error("svn: '" + path1 + "' is not under version control");
        }
        SVNDiffEditor editor = new SVNDiffEditor(wcAccess, getDiffGenerator(), useAncestry, false, false, result);
        editor.closeEdit();
        wcAccess.close(false);
    }
    
    private void doDiffURLURL(SVNURL url1, File path1, SVNRevision revision1, SVNURL url2, File path2, SVNRevision revision2, SVNRevision pegRevision,
            boolean recursive, boolean useAncestry, OutputStream result) throws SVNException {
        url1 = url1 == null ? getURL(path1) : url1;
        url2 = url2 == null ? getURL(path2) : url2;
        File basePath = null;
        if (path1 != null) {
            basePath = path1;
        }
        if (path2 != null) {
            basePath = path2;
        }
        if (pegRevision.isValid()) {
            SVNRepositoryLocation[] locations = getLocations(url2, path2, pegRevision, revision1, revision2);
            url1 = locations[0].getURL();
            url2 = locations[1].getURL();
            
            getDiffGenerator().init(url1.toString(), url2.toString());
        }
        SVNRepository repository1 = createRepository(url1);
        SVNRepository repository2 = createRepository(url2);
        
        final long rev1 = getRevisionNumber(revision1, repository1, path1);
        long rev2 = getRevisionNumber(revision2, repository2, path2);
        
        SVNNodeKind kind1 = repository1.checkPath("", rev1);
        SVNNodeKind kind2 = repository2.checkPath("", rev2);
        String target1 = null;
        if (kind1 == SVNNodeKind.NONE) {
            SVNErrorManager.error("svn: '" + url1 + "' was not found in the repository at revision " + rev1);
        } else if (kind2 == SVNNodeKind.NONE) {
            SVNErrorManager.error("svn: '" + url2 + "' was not found in the repository at revision " + rev2);
        }
        if (kind1 == SVNNodeKind.FILE || kind2 == SVNNodeKind.FILE) {
            target1 = SVNPathUtil.tail(url1.getPath());
            if (basePath != null) {
                basePath = basePath.getParentFile();
            }
            url1 = SVNURL.parseURIEncoded(SVNPathUtil.removeTail(url1.toString()));
            repository1 = createRepository(url1);
        }
        repository2 = createRepository(url1); 
        File tmpFile = getDiffGenerator().createTempDirectory();
        try {
            String baseDisplayPath = basePath != null ? basePath.getAbsolutePath().replace(File.separatorChar, '/') : "";
            SVNRemoteDiffEditor editor = new SVNRemoteDiffEditor(baseDisplayPath, tmpFile, getDiffGenerator(), repository2, rev1, result);
            ISVNReporterBaton reporter = new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, rev1, false);
                    reporter.finishReport();
                }
            };
            repository1.diff(url2, rev2, rev1, target1, !useAncestry, recursive, reporter, editor);
        } finally {
            if (tmpFile != null) {
                SVNFileUtil.deleteAll(tmpFile);
            }
        }
    }
    
    public void doMerge(File path1, SVNRevision revision1, File path2, SVNRevision revision2, File dstPath, boolean recusrsive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        SVNURL url1 = getURL(path1);
        if (url1 == null) {
            SVNErrorManager.error("svn: '" + path1 + "' has no URL");
        }
        SVNURL url2 = getURL(path2);
        if (url2 == null) {
            SVNErrorManager.error("svn: '" + path2 + "' has no URL");
        }
        SVNWCAccess wcAccess = createWCAccess(dstPath);
        wcAccess.open(!dryRun, recusrsive);
        
        SVNEntry targetEntry = wcAccess.getTargetEntry();
        if (targetEntry == null) {
            SVNErrorManager.error("svn: '" + dstPath + "' is not under version control");
        }
        if (targetEntry.isFile()) {
            doMergeFile(url1, path1, revision1, url2, path2, revision2, SVNRevision.UNDEFINED, wcAccess, recusrsive, useAncestry, force, dryRun);
        } else if (targetEntry.isDirectory()) {
            doMerge(url1, path1, revision1, url2, path2, revision2, SVNRevision.UNDEFINED, wcAccess, recusrsive, useAncestry, force, dryRun);
        }
        wcAccess.close(!dryRun);
    }
    
    public void doMerge(File path1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, File dstPath, boolean recusrsive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        SVNURL url1 = getURL(path1);
        if (url1 == null) {
            SVNErrorManager.error("svn: '" + path1 + "' has no URL");
        }
        SVNWCAccess wcAccess = createWCAccess(dstPath);
        wcAccess.open(!dryRun, recusrsive);
        
        SVNEntry targetEntry = wcAccess.getTargetEntry();
        if (targetEntry == null) {
            SVNErrorManager.error("svn: '" + dstPath + "' is not under version control");
        }
        if (targetEntry.isFile()) {
            doMergeFile(url1, path1, revision1, url2, null, revision2, SVNRevision.UNDEFINED, wcAccess, recusrsive, useAncestry, force, dryRun);
        } else if (targetEntry.isDirectory()) {
            doMerge(url1, path1, revision1, url2, null, revision2, SVNRevision.UNDEFINED, wcAccess, recusrsive, useAncestry, force, dryRun);
        }
        wcAccess.close(!dryRun);
    }

    public void doMerge(SVNURL url1, SVNRevision revision1, File path2, SVNRevision revision2, File dstPath, boolean recusrsive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        SVNURL url2 = getURL(path2);
        if (url2 == null) {
            SVNErrorManager.error("svn: '" + path2 + "' has no URL");
        }
        SVNWCAccess wcAccess = createWCAccess(dstPath);
        wcAccess.open(!dryRun, recusrsive);
        
        SVNEntry targetEntry = wcAccess.getTargetEntry();
        if (targetEntry == null) {
            SVNErrorManager.error("svn: '" + dstPath + "' is not under version control");
        }
        if (targetEntry.isFile()) {
            doMergeFile(url1, null, revision1, url2, path2, revision2, SVNRevision.UNDEFINED, wcAccess, recusrsive, useAncestry, force, dryRun);
        } else if (targetEntry.isDirectory()) {
            doMerge(url1, null, revision1, url2, path2, revision2, SVNRevision.UNDEFINED, wcAccess, recusrsive, useAncestry, force, dryRun);
        }
        wcAccess.close(!dryRun);
    }

    public void doMerge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, File dstPath, boolean recusrsive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess(dstPath);
        wcAccess.open(!dryRun, recusrsive);
        
        SVNEntry targetEntry = wcAccess.getTargetEntry();
        if (targetEntry == null) {
            SVNErrorManager.error("svn: '" + dstPath + "' is not under version control");
        }
        if (targetEntry.isFile()) {
            doMergeFile(url1, null, revision1, url2, null, revision2, SVNRevision.UNDEFINED, wcAccess, recusrsive, useAncestry, force, dryRun);
        } else if (targetEntry.isDirectory()) {
            doMerge(url1, null, revision1, url2, null, revision2, SVNRevision.UNDEFINED, wcAccess, recusrsive, useAncestry, force, dryRun);
        }
        wcAccess.close(!dryRun);
         
    }
    
    public void doMerge(SVNURL url1, SVNRevision pegRevision, SVNRevision revision1, SVNRevision revision2, File dstPath, boolean recusrsive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess(dstPath);
        wcAccess.open(!dryRun, recusrsive);
        
        SVNEntry targetEntry = wcAccess.getTargetEntry();
        if (targetEntry == null) {
            SVNErrorManager.error("svn: '" + dstPath + "' is not under version control");
        }
        if (targetEntry.isFile()) {
            doMergeFile(url1, null, revision1, url1, null, revision2, pegRevision, wcAccess, recusrsive, useAncestry, force, dryRun);
        } else if (targetEntry.isDirectory()) {
            doMerge(url1, null, revision1, url1, null, revision2, pegRevision, wcAccess, recusrsive, useAncestry, force, dryRun);
        }
        wcAccess.close(!dryRun);
    }

    public void doMerge(File path1, SVNRevision pegRevision, SVNRevision revision1, SVNRevision revision2, File dstPath, boolean recusrsive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        SVNURL url1 = getURL(path1);
        if (url1 == null) {
            SVNErrorManager.error("svn: '" + path1 + "' has no URL");
        }
        SVNWCAccess wcAccess = createWCAccess(dstPath);
        wcAccess.open(!dryRun, recusrsive);
        
        SVNEntry targetEntry = wcAccess.getTargetEntry();
        if (targetEntry == null) {
            SVNErrorManager.error("svn: '" + dstPath + "' is not under version control");
        }
        if (targetEntry.isFile()) {
            doMergeFile(url1, path1, revision1, url1, path1, revision2, pegRevision, wcAccess, recusrsive, useAncestry, force, dryRun);
        } else if (targetEntry.isDirectory()) {
            doMerge(url1, path1, revision1, url1, path1, revision2, pegRevision, wcAccess, recusrsive, useAncestry, force, dryRun);
        }
        wcAccess.close(!dryRun);
    }
    
    private void doMerge(SVNURL url1, File path1, SVNRevision revision1, SVNURL url2, File path2, SVNRevision revision2, SVNRevision pegRevision,
            SVNWCAccess wcAccess, boolean recursive, boolean useAncestry, boolean force, boolean dryRun) throws SVNException {
        if (!revision1.isValid() || !revision2.isValid()) {
            SVNErrorManager.error("svn: Not all required revisions are specified");
        }
        if (pegRevision.isValid()) {
            SVNRepositoryLocation[] locations = getLocations(url2, path2, pegRevision, revision1, revision2);
            url1 = locations[0].getURL();
            url2 = locations[1].getURL();
            revision1 = SVNRevision.create(locations[0].getRevisionNumber());
            revision2 = SVNRevision.create(locations[1].getRevisionNumber());
            path1 = null;
            path2 = null;
        }
        SVNRepository repository1 = createRepository(url1);
        final long rev1 = getRevisionNumber(revision1, repository1, path1);
        long rev2 = getRevisionNumber(revision2, repository1, path2);
        SVNRepository repository2 = createRepository(url1);
        
        SVNMerger merger = new SVNMerger(wcAccess, url2.toString(), rev2, force, dryRun, isLeaveConflictsUnresolved());
        SVNMergeEditor mergeEditor = new SVNMergeEditor(wcAccess, repository2, rev1, rev2, merger);
        
        repository1.diff(url2, rev2, rev1, null, !useAncestry, recursive,
                new ISVNReporterBaton() {
                    public void report(ISVNReporter reporter) throws SVNException {
                        reporter.setPath("", null, rev1, false);
                        reporter.finishReport();
                    }
                }, mergeEditor);
        
    }
    
    private void doMergeFile(SVNURL url1, File path1, SVNRevision revision1, SVNURL url2, File path2, SVNRevision revision2, SVNRevision pegRevision,
            SVNWCAccess wcAccess, boolean recursive, boolean useAncestry, boolean force, boolean dryRun) throws SVNException {
        
        if (pegRevision.isValid()) {
            SVNRepositoryLocation[] locations = getLocations(url2, path2, pegRevision, revision1, revision2);
            url1 = locations[0].getURL();
            url2 = locations[1].getURL();
            revision1 = SVNRevision.create(locations[0].getRevisionNumber());
            revision2 = SVNRevision.create(locations[1].getRevisionNumber());
            path1 = null;
            path2 = null;
        }
        long[] rev1 = new long[1];
        long[] rev2 = new long[2];
        Map props1 = new HashMap();
        Map props2 = new HashMap();
        File f1 = null;
        File f2 = null;
        String name = wcAccess.getTargetName();
        String mimeType2;
        String mimeType1;
        SVNStatusType[] mergeResult;
        try {
            f1 = loadFile(url1, path1, revision1, props1, wcAccess, rev1);
            f2 = loadFile(url2, path2, revision2, props2, wcAccess, rev2);

            mimeType1 = (String) props1.get(SVNProperty.MIME_TYPE);
            mimeType2 = (String) props2.get(SVNProperty.MIME_TYPE);
            props1 = filterProperties(props1, true, false, false);
            props2 = filterProperties(props2, true, false, false);
            Map propsDiff = computePropsDiff(props1, props2);
            
            SVNMerger merger = new SVNMerger(wcAccess, url2.toString(), rev2[0], force, dryRun, isLeaveConflictsUnresolved());
            mergeResult = merger.fileChanged(name, f1, f2, rev1[0], rev2[0], mimeType1, mimeType2, props1, propsDiff);
        } finally {
            SVNFileUtil.deleteAll(f1);
            SVNFileUtil.deleteAll(f2);
        }
        handleEvent(
                SVNEventFactory.createUpdateModifiedEvent(wcAccess, wcAccess.getAnchor(), name, SVNNodeKind.FILE,
                        SVNEventAction.UPDATE_UPDATE, mimeType2, mergeResult[0], mergeResult[1], SVNStatusType.LOCK_INAPPLICABLE),
                ISVNEventHandler.UNKNOWN);
    }
    
    private File loadFile(SVNURL url, File path, SVNRevision revision, Map properties, SVNWCAccess wcAccess, long[] revNumber) throws SVNException {
        String name = wcAccess.getTargetName();        
        File tmpDir = wcAccess.getAnchor().getRoot();
        File result = SVNFileUtil.createUniqueFile(tmpDir, name, ".tmp");
        SVNFileUtil.createEmptyFile(result);
        
        SVNRepository repository = createRepository(url);
        long revisionNumber = getRevisionNumber(revision, repository, path);
        OutputStream os = null;
        try {
            os = SVNFileUtil.openFileForWriting(result); 
            repository.getFile("", revisionNumber, properties, os);
        } finally {
            SVNFileUtil.closeFile(os);
        }
        if (revNumber != null && revNumber.length > 0) {
            revNumber[0] = revisionNumber;
        }
        return result;
    }

    private static Map computePropsDiff(Map props1, Map props2) {
        Map propsDiff = new HashMap();
        for (Iterator names = props2.keySet().iterator(); names.hasNext();) {
            String newPropName = (String) names.next();
            if (props1.containsKey(newPropName)) {
                // changed.
                Object oldValue = props2.get(newPropName);
                if (!oldValue.equals(props1.get(newPropName))) {
                    propsDiff.put(newPropName, props2.get(newPropName));
                }
            } else {
                // added.
                propsDiff.put(newPropName, props2.get(newPropName));
            }
        }
        for (Iterator names = props1.keySet().iterator(); names.hasNext();) {
            String oldPropName = (String) names.next();
            if (!props2.containsKey(oldPropName)) {
                // deleted
                propsDiff.put(oldPropName, null);
            }
        }
        return propsDiff;
    }

    private static Map filterProperties(Map props1, boolean leftRegular,
            boolean leftEntry, boolean leftWC) {
        Map result = new HashMap();
        for (Iterator names = props1.keySet().iterator(); names.hasNext();) {
            String propName = (String) names.next();
            if (!leftEntry && propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                continue;
            }
            if (!leftWC && propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                continue;
            }
            if (!leftRegular
                    && !(propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX) || propName
                            .startsWith(SVNProperty.SVN_WC_PREFIX))) {
                continue;
            }
            result.put(propName, props1.get(propName));
        }
        return result;
    }
}