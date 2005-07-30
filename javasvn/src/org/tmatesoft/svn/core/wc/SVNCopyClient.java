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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.internal.wc.SVNCommitMediator;
import org.tmatesoft.svn.core.internal.wc.SVNCommitUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCommitter;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNLog;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * This class provides methods to perform any kinds of copying and moving that <b>SVN</b>
 * supports - operating on both Working Copies (WC) and URLs.
 * 
 * <p>
 * Copy operations allow a user to copy versioned files and directories with all their 
 * previous history in several ways. 
 * 
 * <p>
 * Supported copy operations are:
 * <ul>
 * <li> Working Copy to Working Copy (WC-to-WC) copying - this operation copies the source
 * Working Copy item to the destination one and schedules the source copy for addition with history.
 * <li> Working Copy to URL (WC-to-URL) copying - this operation commits to the repository (exactly
 * to that repository location that is specified by URL) a copy of the Working Copy item.
 * <li> URL to Working Copy (URL-to-WC) copying - this operation will copy the source item from
 * the repository to the Working Copy item and schedule the source copy for addition with history.
 * <li> URL to URL (URL-to-URL) copying - this is a fully repository-side operation, it commits 
 * a copy of the source item to a specified repository location (within the same repository, of
 * course). 
 * </ul>
 * 
 * <p> 
 * Besides just copying <b>SVNCopyClient</b> also is able to move a versioned item - that is
 * first making a copy of the source item and second scheduling the source item for deletion 
 * when operating on a Working Copy or right committing the deletion of the source item when 
 * operating immediately on the repository.
 * 
 * <p>
 * Supported move operations are:
 * <ul>
 * <li> Working Copy to Working Copy (WC-to-WC) moving - this operation copies the source
 * Working Copy item to the destination one and schedules the source item for deletion.
 * <li> URL to URL (URL-to-URL) moving - this is a fully repository-side operation, it commits 
 * a copy of the source item to a specified repository location (within the same repository, of
 * course) and deletes the source item. 
 * </ul>
 * 
 * <p>
 * Overloaded <code>doCopy(..)</code> methods of <b>SVNCopyClient</b> are similar to
 * 'svn copy' and 'svn move' commands of the <b>SVN</b> command line client. 
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 * 
 */
public class SVNCopyClient extends SVNBasicClient {

    private ISVNCommitHandler myCommitHandler;

    public SVNCopyClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNCopyClient(ISVNRepositoryFactory repositoryFactory, ISVNOptions options) {
        super(repositoryFactory, options);
    }
    

    /**
     * Sets an implementation of <span class="style0">ISVNCommitHandler</span> to 
     * the commit handler that will be used during commit operations to handle 
     * commit log messages. The handler will receive a clien's log message and items 
     * (represented as <span class="style0">SVNCommitItem</span> objects) that will be 
     * committed. Depending on implementor's aims the initial log message can
     * be modified (or something else) and returned back. 
     * 
     * <p>
     * If using <span class="style0">SVNCopyClient</span> without specifying any
     * commit handler then a default one will be used - {@link DefaultSVNCommitHandler}.
     * 
     * @param handler               an implementor's handler that will be used to handle 
     *                              commit log messages
     * @see   #getCommitHandler()
     * @see   ISVNCommitHandler
     */
    public void setCommitHandler(ISVNCommitHandler handler) {
        myCommitHandler = handler;
    }

    /**
     * Returns the specified commit handler (if set) being in use or a default one 
     * (<span class="style0">DefaultSVNCommitHandler</span>) if no special 
     * implementations of <span class="style0">ISVNCommitHandler</span> were 
     * previousely provided.
     *   
     * @return  the commit handler being in use or a default one
     * @see     #setCommitHander(ISVNCommitHandler)
     * @see     ISVNCommitHabdler
     * @see     DefaultSVNCommitHandler 
     */
    public ISVNCommitHandler getCommitHandler() {
        if (myCommitHandler == null) {
            myCommitHandler = new DefaultSVNCommitHandler();
        }
        return myCommitHandler;
    }

    public SVNCommitInfo doCopy(SVNURL srcURL, SVNRevision srcRevision, SVNURL dstURL, boolean isMove, String commitMessage) throws SVNException {
        SVNURL topURL = SVNURLUtil.getCommonURLAncestor(srcURL, dstURL);
        if (topURL == null) {
            SVNErrorManager.error("svn: Source and dest appear not to be in the same repository (src: '" + srcURL + " dst: '" + dstURL + "')");
        }
        boolean isResurrect = false;
        if (dstURL.equals(srcURL)) {
            topURL = srcURL.removePathTail();
            isResurrect = true;
        }
        String srcPath = srcURL.equals(topURL) ? "" : srcURL.toString().substring(topURL.toString().length() + 1);
        srcPath = SVNEncodingUtil.uriDecode(srcPath);
        String dstPath = dstURL.equals(topURL) ? "" : dstURL.toString().substring(topURL.toString().length() + 1);
        dstPath = SVNEncodingUtil.uriDecode(dstPath);
        
        if ("".equals(srcPath) && isMove) {
            SVNErrorManager.error("svn: Cannot move URL '" + srcURL + "' into itself");
        }
        SVNRepository repository = createRepository(topURL);
        long srcRevNumber = getRevisionNumber(srcRevision, repository, null);
        long latestRevision = repository.getLatestRevision();
        
        if (srcRevNumber < 0) {
            srcRevNumber = latestRevision;
        }
        SVNNodeKind srcKind = repository.checkPath(srcPath, srcRevNumber);
        if (srcKind == SVNNodeKind.NONE) {
            SVNErrorManager.error("svn: Path '" + srcURL + "' does not exist in revision " + srcRevNumber);
        }
        SVNNodeKind dstKind = repository.checkPath(dstPath, latestRevision);
        if (dstKind == SVNNodeKind.DIR) {
            dstPath = SVNPathUtil.append(dstPath, SVNPathUtil.tail(srcURL.getPath()));
            if (repository.checkPath(dstPath, latestRevision) != SVNNodeKind.NONE) {
                SVNErrorManager.error("svn: Path '" + dstPath + "' already exist");
            }
        } else if (dstKind == SVNNodeKind.FILE) {
            SVNErrorManager.error("svn: Path '" + dstPath + "' already exist");
        }
        Collection commitItems = new ArrayList(2);
        commitItems.add(new SVNCommitItem(null, dstURL, srcURL, srcKind, SVNRevision.create(srcRevNumber), 
                true, false, false, false, true, false));
        if (isMove) {
            commitItems.add(new SVNCommitItem(null, srcURL, null, srcKind, SVNRevision.create(srcRevNumber), 
                    false, true, false, false, false, false));
        }
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, (SVNCommitItem[]) commitItems.toArray(new SVNCommitItem[commitItems.size()]));
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }

        ISVNEditor commitEditor = repository.getCommitEditor(commitMessage, null, false, null);
        ISVNCommitPathHandler committer = new CopyCommitPathHandler(srcPath, srcRevNumber, srcKind, dstPath, isMove, isResurrect);
        Collection paths = isMove ? Arrays.asList(new String[] { srcPath, dstPath }) : Collections.singletonList(dstPath);

        SVNCommitInfo result = null;
        try {
            SVNCommitUtil.driveCommitEditor(committer, paths, commitEditor, -1);
            result = commitEditor.closeEdit();
        } catch (SVNException e) {
            try {
                commitEditor.abortEdit();
            } catch (SVNException inner) {
                //
            }
            SVNDebugLog.logInfo(e);
            SVNErrorManager.error("svn: " + e.getMessage());
        }
        return result != null ? result : SVNCommitInfo.NULL;
    }

    public SVNCommitInfo doCopy(File srcPath, SVNRevision srcRevision, SVNURL dstURL, String commitMessage) throws SVNException {
        // may be url->url.
        if (srcRevision.isValid() && srcRevision != SVNRevision.WORKING) {
            SVNWCAccess wcAccess = createWCAccess(srcPath);
            SVNEntry srcEntry = wcAccess.getTargetEntry();
            if (srcEntry == null) {
                SVNErrorManager.error("svn: '" + srcPath + "' is not under version control");
            }
            if (srcEntry.getURL() == null) {
                SVNErrorManager.error("svn: '" + srcPath + "' has no URL");
            }
            return doCopy(srcEntry.getSVNURL(), srcRevision, dstURL, false, commitMessage);
        }
        SVNWCAccess wcAccess = createWCAccess(srcPath);
        
        SVNURL dstAnchorURL = dstURL.removePathTail();
        String dstTarget = SVNPathUtil.tail(dstURL.toString());
        dstTarget = SVNEncodingUtil.uriDecode(dstTarget);
        
        SVNRepository repository = createRepository(dstAnchorURL);
        SVNNodeKind dstKind = repository.checkPath(dstTarget, -1);
        if (dstKind == SVNNodeKind.DIR) {
            dstURL = dstURL.appendPath(srcPath.getName(), false);
        } else if (dstKind == SVNNodeKind.FILE) {
            SVNErrorManager.error("svn: File '" + dstURL + "' already exists");
        }

        SVNCommitItem[] items = new SVNCommitItem[] { 
                new SVNCommitItem(null, dstURL, null, SVNNodeKind.NONE, SVNRevision.UNDEFINED, true, false, false, false, true, false) 
                };
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, items);
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }

        Collection tmpFiles = null;
        SVNCommitInfo info = null;
        ISVNEditor commitEditor = null;
        try {
            wcAccess.open(false, true);

            Map commitables = new TreeMap();
            SVNEntry entry = wcAccess.getTargetEntry();
            if (entry == null) {
                SVNErrorManager.error("svn: '" + srcPath + "' is not under version control");
                return SVNCommitInfo.NULL;
            }
            
            SVNCommitUtil.harvestCommitables(commitables, wcAccess.getTarget(), srcPath, null, entry, dstURL.toString(), entry.getURL(), 
                    true, false, false, null, true);
            items = (SVNCommitItem[]) commitables.values().toArray(new SVNCommitItem[commitables.values().size()]);
            
            commitables = new TreeMap();
            dstURL = SVNURL.parseURIEncoded(SVNCommitUtil.translateCommitables(items, commitables));

            repository = createRepository(dstURL);
            SVNCommitMediator mediator = new SVNCommitMediator(wcAccess, commitables);
            tmpFiles = mediator.getTmpFiles();

            commitEditor = repository.getCommitEditor(commitMessage, null, false, mediator);
            info = SVNCommitter.commit(wcAccess, tmpFiles, commitables, repository.getRepositoryRoot(true).getPath(), commitEditor);
            commitEditor = null;
        } finally {
            if (tmpFiles != null) {
                for (Iterator files = tmpFiles.iterator(); files.hasNext();) {
                    File file = (File) files.next();
                    file.delete();
                }
            }
            if (commitEditor != null && info == null) {
                commitEditor.abortEdit();
            }
            if (wcAccess != null) {
                wcAccess.close(false);
            }
        }
        return info != null ? info : SVNCommitInfo.NULL;
    }

    public long doCopy(SVNURL srcURL, SVNRevision srcRevision, File dstPath) throws SVNException {
        SVNRepository repository = createRepository(srcURL);
        if (!srcRevision.isValid()) {
            srcRevision = SVNRevision.HEAD;
        }
        long srcRevisionNumber = getRevisionNumber(srcRevision, repository, null);
        SVNNodeKind srcKind = repository.checkPath("", srcRevisionNumber);
        
        if (srcKind == SVNNodeKind.NONE) {
            SVNErrorManager.error("svn: Path '" + srcURL + "' not found in revision " + srcRevisionNumber);
        }
        
        SVNFileType dstFileType = SVNFileType.getType(dstPath);
        if (dstFileType == SVNFileType.DIRECTORY) {
            dstPath = new File(dstPath, SVNPathUtil.tail(srcURL.getPath()));
        } else if (dstFileType != SVNFileType.NONE) {
            SVNErrorManager.error("svn: File '" + dstPath + "' already exists");
        }
        dstFileType = SVNFileType.getType(dstPath);
        if (dstFileType != SVNFileType.NONE) {
            SVNErrorManager.error("svn: '" + dstPath + "' is in the way");
        }
        
        SVNWCAccess dstAccess = createWCAccess(dstPath);
        SVNEntry dstEntry = dstAccess.getTargetEntry();
        if (dstEntry != null) {
            SVNErrorManager.error("svn: Entry for '" + dstPath + "' exists (though the working copy file is missing)");
        }
        
        boolean sameRepositories;
        
        repository.getRepositoryRoot(true);
        String srcUUID = repository.getRepositoryUUID();
        SVNWCAccess dstParentAccess = createWCAccess(dstPath.getParentFile());
        SVNEntry dstParentEntry = dstParentAccess.getTargetEntry();
        String dstUUID = dstParentEntry != null ? dstParentEntry.getUUID() : null;
        if (dstUUID == null || srcUUID == null) {
            sameRepositories = false;
        } else {
            sameRepositories = srcUUID.equals(dstUUID);
        }
        
        long revision = -1;
        if (srcKind == SVNNodeKind.DIR) {
            // do checkout.
            SVNUpdateClient updateClient = new SVNUpdateClient(getRepositoryFactory(), getOptions());
            updateClient.setDoNotSleepForTimeStamp(true);
            updateClient.setEventHandler(getEventDispatcher());
            
            revision = updateClient.doCheckout(srcURL, dstPath, srcRevision, srcRevision, true);
            // update copyfrom (if it is the same repository).
            if (sameRepositories) {
                try {
                    
                    SVNEntry newEntry = dstParentAccess.getTarget().getEntries().addEntry(dstPath.getName());
                    newEntry.setKind(SVNNodeKind.DIR);
                    newEntry.scheduleForAddition();
                    dstParentAccess.getTarget().getEntries().save(true);
                    SVNURL newURL = dstParentAccess.getTargetEntry().getSVNURL();
                    newURL = newURL.appendPath(dstPath.getName(), false);
                    
                    addDir(dstParentAccess.getTarget(), dstPath.getName(), srcURL.toString(), revision);
                    dstAccess = createWCAccess(dstPath);
                    addDir(dstAccess.getTarget(), "", srcURL.toString(), revision);
                    updateCopiedDirectory(dstAccess.getTarget(), "", newURL.toString(), null, -1);
                } finally {
                    dstAccess.close(true);
                }
            } else {
                SVNErrorManager.error("Source URL '" + srcURL + "' is from foreign repository; leaving it as a disjoint WC");
            }
        } else if (srcKind == SVNNodeKind.FILE) {
            Map properties = new HashMap();
            File tmpFile = null;

            File baseTmpFile = dstAccess.getAnchor().getBaseFile(dstPath.getName(), true);
            tmpFile = SVNFileUtil.createUniqueFile(baseTmpFile.getParentFile(), dstPath.getName(), ".tmp");
            OutputStream os = SVNFileUtil.openFileForWriting(tmpFile);

            try {
                repository.getFile("", srcRevisionNumber, properties, os);
            } finally {
                SVNFileUtil.closeFile(os);
            }
            SVNFileUtil.rename(tmpFile, baseTmpFile);
            if (tmpFile != null) {
                tmpFile.delete();
            }
            addFile(dstAccess.getAnchor(), dstPath.getName(), properties, sameRepositories ? srcURL.toString() : null, srcRevisionNumber);
            dstAccess.getAnchor().runLogs();
            dispatchEvent(SVNEventFactory.createAddedEvent(dstAccess, dstAccess.getAnchor(), dstAccess.getTargetEntry()));
            
            revision = srcRevisionNumber;
        }
        
        return revision;
    }

    public void doCopy(File srcPath, SVNRevision srcRevision, File dstPath, boolean force, boolean isMove) throws SVNException {
        if (srcRevision.isValid() && srcRevision != SVNRevision.WORKING && !isMove) {
            // url->wc copy
            SVNWCAccess wcAccess = createWCAccess(srcPath);
            SVNEntry srcEntry = wcAccess.getTargetEntry();
            if (srcEntry == null) {
                SVNErrorManager.error("svn: '" + srcPath + "' is not under version control");
            }
            if (srcEntry.getURL() == null) {
                SVNErrorManager.error("svn: '" + srcPath + "' has no URL");
            }
            doCopy(srcEntry.getSVNURL(), srcRevision, dstPath);
            return;
        }
        // 1. can't copy src to its own child
        if (SVNPathUtil.isChildOf(srcPath, dstPath)) {
            SVNErrorManager.error("svn: Cannot copy '" + srcPath + "' into its own child '" + dstPath + "'");
        }
        // 2. can't move path into itself
        if (isMove && srcPath.equals(dstPath)) {
            SVNErrorManager.error("svn: Cannot move '" + srcPath + "' into itself");
        }
        // 3. src should exist
        SVNFileType srcType = SVNFileType.getType(srcPath);
        if (srcType == SVNFileType.NONE) {
            SVNErrorManager.error("svn: Path '" + srcPath + "' does not exist");
        }
        // 4. if dst exists - use its child
        SVNFileType dstType = SVNFileType.getType(dstPath);
        if (dstType == SVNFileType.DIRECTORY) {
            dstPath = new File(dstPath, srcPath.getName());
            dstType = SVNFileType.getType(dstPath);
            if (dstType != SVNFileType.NONE) {
                SVNErrorManager.error("svn: '" + dstPath + "' already exist and is in a way");
            }
        } else if (dstType != SVNFileType.NONE) {
            SVNErrorManager.error("svn: File '" + dstPath + "' already exist");
        }
        // 5. if move -> check if dst could be deleted later.
        SVNWCAccess srcAccess = createWCAccess(srcPath);
        SVNWCAccess dstAccess = createWCAccess(dstPath);
        try {
            if (isMove) {
                if (srcAccess.getAnchor().getRoot().equals(dstAccess.getAnchor().getRoot())) {
                    dstAccess = srcAccess;
                }
                srcAccess.open(true, srcType == SVNFileType.DIRECTORY);
                if (!force) {
                    srcAccess.getAnchor().canScheduleForDeletion(dstAccess.getTargetName());
                }
            }
            if (srcAccess != dstAccess) {
                dstAccess.open(true, srcType == SVNFileType.DIRECTORY);
            }
            SVNEntry dstParentEntry = dstAccess.getAnchor().getEntries().getEntry("", true);
            if (dstParentEntry.isScheduledForDeletion()) {
                SVNErrorManager.error("svn: Cannot copy to '" + dstPath + "' as it is scheduled for deletion");
            }
            if (srcType == SVNFileType.DIRECTORY) {
                copyDirectory(dstAccess, srcAccess, dstPath.getName());
            } else {
                copyFile(dstAccess, srcAccess, dstPath.getName());
            }

            if (isMove) {
                srcAccess.getAnchor().scheduleForDeletion(srcPath.getName());
            }
        } finally {
            dstAccess.close(true);
            if (isMove && srcAccess != dstAccess) {
                srcAccess.close(true);
            }
        }
    }


    private void addFile(SVNDirectory dir, String fileName, Map properties, String copyFromURL, long copyFromRev) throws SVNException {
        SVNLog log = dir.getLog(0);
        Map regularProps = new HashMap();
        Map entryProps = new HashMap();
        Map wcProps = new HashMap();
        for (Iterator names = properties.keySet().iterator(); names.hasNext();) {
            String propName = (String) names.next();
            String propValue = (String) properties.get(propName);
            if (propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                entryProps.put(SVNProperty.shortPropertyName(propName), propValue);
            } else if (propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                wcProps.put(propName, propValue);
            } else {
                regularProps.put(propName, propValue);
            }
        }
        entryProps.put(SVNProperty.shortPropertyName(SVNProperty.KIND), SVNProperty.KIND_FILE);
        entryProps.put(SVNProperty.shortPropertyName(SVNProperty.REVISION), "0");
        entryProps.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), SVNProperty.SCHEDULE_ADD);
        if (copyFromURL != null) {
            entryProps.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_REVISION), Long.toString(copyFromRev));
            entryProps.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_URL), copyFromURL);
            entryProps.put(SVNProperty.shortPropertyName(SVNProperty.COPIED), Boolean.TRUE.toString());
        }

        log.logChangedEntryProperties(fileName, entryProps);
        log.logChangedWCProperties(fileName, wcProps);
        dir.mergeProperties(fileName, regularProps, null, true, log);

        Map command = new HashMap();
        command.put(SVNLog.NAME_ATTR, SVNFileUtil.getBasePath(dir.getBaseFile(fileName, true)));
        command.put(SVNLog.DEST_ATTR, fileName);
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();
        command.put(SVNLog.NAME_ATTR, SVNFileUtil.getBasePath(dir.getBaseFile(fileName, true)));
        command.put(SVNLog.DEST_ATTR, SVNFileUtil.getBasePath(dir.getBaseFile(fileName, false)));
        log.addCommand(SVNLog.MOVE, command, false);
        log.save();
    }

    private void copyFile(SVNWCAccess dstAccess, SVNWCAccess srcAccess,  String dstName) throws SVNException {
        SVNEntry dstEntry = dstAccess.getAnchor().getEntries().getEntry(dstName, false);
        File dstPath = new File(dstAccess.getAnchor().getRoot(), dstName);
        File srcPath = new File(srcAccess.getAnchor().getRoot(), srcAccess.getTargetName());
        if (dstEntry != null && dstEntry.isFile()) {
            if (dstEntry.isScheduledForDeletion()) {
                SVNErrorManager.error("svn: '" + dstPath + "' is scheduled for deletion; it must be committed before being overwritten");
            } else {
                SVNErrorManager.error("svn: There is already versioned item '" + dstPath + "'");
            }
        }
        SVNEntry srcEntry = srcAccess.getTargetEntry();
        if (srcEntry == null) {
            SVNErrorManager.error("svn: Cannot copy or move '" + srcPath + "': it's not under version control");
        } else if (srcEntry.isScheduledForAddition() || srcEntry.getURL() == null || srcEntry.isCopied()) {
            SVNErrorManager.error("svn: Cannot copy or move '" + srcPath  + "': it's not in repository yet; try committing first");
        }
        SVNFileType srcType = SVNFileType.getType(srcPath);
        if (srcType == SVNFileType.SYMLINK) {
            String name = SVNFileUtil.getSymlinkName(srcPath);
            if (name != null) {
                SVNFileUtil.createSymlink(dstPath, name);
            }
        } else {
            SVNFileUtil.copyFile(srcPath, dstPath, false);
        }
        // copy props, props base and text-base
        File srcTextBase = srcAccess.getAnchor().getBaseFile(srcAccess.getTargetName(), false);
        SVNProperties srcProps = srcAccess.getAnchor().getProperties(srcAccess.getTargetName(), false);
        boolean executable = srcProps.getPropertyValue(SVNProperty.EXECUTABLE) != null;
        SVNProperties srcBaseProps = srcAccess.getAnchor().getBaseProperties(srcAccess.getTargetName(), false);

        File dstTextBase = dstAccess.getAnchor().getBaseFile(dstName, false);
        SVNProperties dstProps = dstAccess.getAnchor().getProperties(dstName, false);
        SVNProperties dstBaseProps = srcAccess.getAnchor().getBaseProperties(dstName, false);
        if (srcTextBase.exists()) {
            SVNFileUtil.copyFile(srcTextBase, dstTextBase, false);
        }
        if (srcProps.getFile().exists()) {
            srcProps.copyTo(dstProps);
        }
        if (srcBaseProps.getFile().exists()) {
            srcBaseProps.copyTo(dstBaseProps);
        }
        if (executable) {
            SVNFileUtil.setExecutable(dstPath, true);
        }
        // and finally -> add.
        String copyFromURL = srcEntry.getURL();
        long copyFromRevision = srcEntry.getRevision();

        SVNEntry entry = dstAccess.getAnchor().add(dstName, false, false);
        entry.setCopied(true);
        entry.setCopyFromRevision(copyFromRevision);
        entry.setCopyFromURL(copyFromURL);
        entry.setRevision(copyFromRevision);
        entry.scheduleForAddition();
        dstAccess.getAnchor().getEntries().save(true);
    }
    
    private void addDir(SVNDirectory dir, String name, String copyFromURL, long copyFromRev) throws SVNException {
        SVNEntry entry = dir.getEntries().getEntry(name, true);
        if (entry == null) {
            entry = dir.getEntries().addEntry(name);
        }
        entry.setKind(SVNNodeKind.DIR);
        if (copyFromURL != null) {
            entry.setCopyFromRevision(copyFromRev);
            entry.setCopyFromURL(copyFromURL);
            entry.setCopied(true);
        }
        entry.scheduleForAddition();
        if ("".equals(name) && copyFromURL != null) {
            updateCopiedDirectory(dir, name, null, null, -1);
        }
        dir.getEntries().save(true);
    }

    private void copyDirectory(SVNWCAccess dstAccess, SVNWCAccess srcAccess, String dstName) throws SVNException {
        SVNEntry srcEntry = srcAccess.getTargetEntry();
        if (srcEntry == null) {
            SVNErrorManager.error("svn: '" + srcAccess.getTarget().getRoot() + "' is not under version control");
        } else if (srcEntry.isScheduledForAddition() || srcEntry.getURL() == null || srcEntry.isCopied()) {
            SVNErrorManager.error("svn: Cannot copy or move '" + srcAccess.getTarget().getRoot() + "': it is not in repository yet; try committing first");
        }
        String copyFromURL = srcEntry.getURL();
        long copyFromRev = srcEntry.getRevision();

        String newURL = dstAccess.getAnchor().getEntries().getEntry("", true).getURL();
        newURL = SVNPathUtil.append(newURL, SVNEncodingUtil.uriEncode(dstName));

        File dstPath = new File(dstAccess.getAnchor().getRoot(), dstName);

        SVNFileUtil.copyDirectory(srcAccess.getTarget().getRoot(), dstPath, true);

        SVNDirectory newDir = dstAccess.addDirectory(dstName, dstPath, true, true);

        SVNEntry entry = dstAccess.getAnchor().getEntries().addEntry(dstName);
        entry.setCopyFromRevision(copyFromRev);
        entry.setKind(SVNNodeKind.DIR);
        entry.scheduleForAddition();
        entry.setCopyFromURL(copyFromURL);
        entry.setCopied(true);

        SVNEvent event = SVNEventFactory.createAddedEvent(dstAccess, dstAccess.getAnchor(), entry);
        dispatchEvent(event);
        dstAccess.getTarget().getEntries().save(true);

        updateCopiedDirectory(newDir, "", newURL, null, -1);
        SVNEntry newRoot = newDir.getEntries().getEntry("", true);
        newRoot.scheduleForAddition();
        newRoot.setCopyFromRevision(copyFromRev);
        newRoot.setCopyFromURL(copyFromURL);
        newDir.getEntries().save(true);
        // fire added event.
    }

    static void updateCopiedDirectory(SVNDirectory dir, String name, String newURL, String copyFromURL, long copyFromRevision) throws SVNException {
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry(name, true);
        if (entry != null) {
            entry.setCopied(true);
            if (newURL != null) {
                entry.setURL(newURL);
            }
            if (entry.isFile()) {
                dir.getWCProperties(name).delete();
                if (copyFromURL != null) {
                    entry.setCopyFromURL(copyFromURL);
                    entry.setCopyFromRevision(copyFromRevision);
                }
            }
            boolean deleted = false;
            if (entry.isDeleted() && newURL != null) {
                // convert to scheduled for deletion.
                deleted = true;
                entry.setDeleted(false);
                entry.scheduleForDeletion();
                if (entry.isDirectory()) {
                    entry.setKind(SVNNodeKind.FILE);
                }
            }
            if (entry.getLockToken() != null && newURL != null) {
                entry.setLockToken(null);
                entry.setLockOwner(null);
                entry.setLockComment(null);
                entry.setLockCreationDate(null);
            }
            if (!"".equals(name) && entry.isDirectory() && !deleted) {
                SVNDirectory childDir = dir.getChildDirectory(name);
                if (childDir != null) {
                    String childCopyFromURL = copyFromURL == null ? null : SVNPathUtil.append(copyFromURL, SVNEncodingUtil.uriEncode(entry.getName()));
                    updateCopiedDirectory(childDir, "", newURL, childCopyFromURL, copyFromRevision);
                }
            } else if ("".equals(name)) {
                dir.getWCProperties("").delete();
                if (copyFromURL != null) {
                    entry.setCopyFromURL(copyFromURL);
                    entry.setCopyFromRevision(copyFromRevision);
                }
                for (Iterator ents = entries.entries(true); ents.hasNext();) {
                    SVNEntry childEntry = (SVNEntry) ents.next();
                    if ("".equals(childEntry.getName())) {
                        continue;
                    }
                    String childCopyFromURL = copyFromURL == null ? null : SVNPathUtil.append(copyFromURL, SVNEncodingUtil.uriEncode(childEntry.getName()));
                    String newChildURL = newURL == null ? null : SVNPathUtil.append(newURL, SVNEncodingUtil.uriEncode(childEntry.getName()));
                    updateCopiedDirectory(dir, childEntry.getName(), newChildURL, childCopyFromURL, copyFromRevision);
                }
                entries.save(true);
            }
        }
    }

    private static class CopyCommitPathHandler implements ISVNCommitPathHandler {
        
        private String mySrcPath;
        private String myDstPath;
        private long mySrcRev;
        private boolean myIsMove;
        private boolean myIsResurrect;
        private SVNNodeKind mySrcKind;

        public CopyCommitPathHandler(String srcPath, long srcRev, SVNNodeKind srcKind, String dstPath, boolean isMove, boolean isRessurect) {
            mySrcPath = srcPath;
            myDstPath = dstPath;
            mySrcRev = srcRev;
            myIsMove = isMove;
            mySrcKind = srcKind;
            myIsResurrect = isRessurect;
        }
        
        public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
            boolean doAdd = false;
            boolean doDelete = false;
            if (myIsResurrect) {
                if (!myIsMove) {
                    doAdd = true;
                }
            } else {
                if (myIsMove) {
                    if (commitPath.equals(mySrcPath)) {
                        doDelete = true;
                    } else {
                        doAdd = true;
                    }
                } else {
                    doAdd = true;
                }
            }
            if (doDelete) {
                commitEditor.deleteEntry(mySrcPath, -1);
            }
            boolean closeDir = false;
            if (doAdd) {
                if (mySrcKind == SVNNodeKind.DIR) {
                    commitEditor.addDir(myDstPath, mySrcPath, mySrcRev);
                    closeDir = true;
                } else {
                    commitEditor.addFile(myDstPath, mySrcPath, mySrcRev);
                    commitEditor.closeFile(myDstPath, null);
                }
            }
            return closeDir;
        }
    }
}
