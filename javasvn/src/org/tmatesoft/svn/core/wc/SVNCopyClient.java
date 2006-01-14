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
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
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

/**
 * The <b>SVNCopyClient</b> provides methods to perform any kinds of copying and moving that SVN
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
 * first making a copy of the source item and then scheduling the source item for deletion 
 * when operating on a Working Copy, or right committing the deletion of the source item when 
 * operating immediately on the repository.
 * 
 * <p>
 * Supported move operations are:
 * <ul>
 * <li> Working Copy to Working Copy (WC-to-WC) moving - this operation copies the source
 * Working Copy item to the destination one and schedules the source item for deletion.
 * <li> URL to URL (URL-to-URL) moving - this is a fully repository-side operation, it commits 
 * a copy of the source item to a specified repository location and deletes the source item. 
 * </ul>
 * 
 * <p>
 * Overloaded <b>doCopy()</b> methods of <b>SVNCopyClient</b> are similar to
 * <code>'svn copy'</code> and <code>'svn move'</code> commands of the SVN command line client. 
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 * @see     <a target="_top" href="http://tmate.org/svn/kb/examples/">Examples</a>
 * 
 */
public class SVNCopyClient extends SVNBasicClient {

    private ISVNCommitHandler myCommitHandler;
    /**
     * Constructs and initializes an <b>SVNCopyClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNCopyClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNCopyClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the 
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     * 
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver     
     */
    public SVNCopyClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNCopyClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }
    

    /**
     * Sets an implementation of <b>ISVNCommitHandler</b> to 
     * the commit handler that will be used during commit operations to handle 
     * commit log messages. The handler will receive a clien's log message and items 
     * (represented as <b>SVNCommitItem</b> objects) that will be 
     * committed. Depending on implementor's aims the initial log message can
     * be modified (or something else) and returned back. 
     * 
     * <p>
     * If using <b>SVNCopyClient</b> without specifying any
     * commit handler then a default one will be used - {@link DefaultSVNCommitHandler}.
     * 
     * @param handler               an implementor's handler that will be used to handle 
     *                              commit log messages
     * @see   #getCommitHandler()
     * @see   SVNCommitItem
     */
    public void setCommitHandler(ISVNCommitHandler handler) {
        myCommitHandler = handler;
    }

    /**
     * Returns the specified commit handler (if set) being in use or a default one 
     * (<b>DefaultSVNCommitHandler</b>) if no special 
     * implementations of <b>ISVNCommitHandler</b> were 
     * previousely provided.
     *   
     * @return  the commit handler being in use or a default one
     * @see     #setCommitHandler(ISVNCommitHandler)
     * @see     DefaultSVNCommitHandler 
     */
    public ISVNCommitHandler getCommitHandler() {
        if (myCommitHandler == null) {
            myCommitHandler = new DefaultSVNCommitHandler();
        }
        return myCommitHandler;
    }
    
    /**
     * Copies/moves a source URL to a destination one immediately committing changes
     * to a repository.
     * 
     * @param  srcURL         a source repository location URL
     * @param  srcRevision    a revision of <code>srcURL</code>
     * @param  dstURL         a target URL where <code>srcURL</code> is to be
     *                        copied/moved
     * @param  isMove         <span class="javakeyword">true</span> to move the source
     *                        to the target (only URL-to-URL), 
     *                        <span class="javakeyword">false</span> to copy
     * @param  commitMessage  a commit log message
     * @return                information on the committed revision
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li><code>srcURL</code> and <code>dstURL</code> are not in the
     *                        same repository  
     *                        <li><code>srcURL</code> was not found in <code>srcRevision</code>
     *                        <li><code>dstURL</code> already exists
     *                        <li><code>isMove = </code><span class="javakeyword">true</span> and 
     *                        <code>dstURL = srcURL</code>
     *                        </ul>
     */
    public SVNCommitInfo doCopy(SVNURL srcURL, SVNRevision srcRevision, SVNURL dstURL, boolean isMove, String commitMessage) throws SVNException {
        SVNURL topURL = SVNURLUtil.getCommonURLAncestor(srcURL, dstURL);
        if (topURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Source and dest appear not to be in the same repository (src: ''{0}''; dst: ''{1}'')", new Object[] {srcURL, dstURL});
            SVNErrorManager.error(err);
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
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot move URL ''{0}'' into itself", srcURL);
            SVNErrorManager.error(err);
        }
        SVNRepository repository = createRepository(topURL, true);
        long srcRevNumber = getRevisionNumber(srcRevision, repository, null);
        long latestRevision = repository.getLatestRevision();
        
        if (srcRevNumber < 0) {
            srcRevNumber = latestRevision;
        }
        SVNNodeKind srcKind = repository.checkPath(srcPath, srcRevNumber);
        if (srcKind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' does not exist in revision {1}", new Object[] {srcURL, new Long(srcRevNumber)});
            SVNErrorManager.error(err);
        }
        SVNNodeKind dstKind = repository.checkPath(dstPath, latestRevision);
        if (dstKind == SVNNodeKind.DIR) {
            dstPath = SVNPathUtil.append(dstPath, SVNPathUtil.tail(srcURL.getPath()));
            if (repository.checkPath(dstPath, latestRevision) != SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, "Path ''{0}'' already exists", dstPath);
                SVNErrorManager.error(err);
            }
        } else if (dstKind == SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, "Path ''{0}'' already exists", dstPath);
            SVNErrorManager.error(err);
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
            SVNErrorMessage nestedErr = e.getErrorMessage();
            SVNErrorMessage err = SVNErrorMessage.create(nestedErr.getErrorCode(), "Commit failed (details follow):");
            SVNErrorManager.error(err, e);
        }
        if (result != null && result.getNewRevision() >= 0) { 
            dispatchEvent(SVNEventFactory.createCommitCompletedEvent(null, result.getNewRevision()), ISVNEventHandler.UNKNOWN);
        }
        return result != null ? result : SVNCommitInfo.NULL;
    }
    
    /**
     * Copies a source Working Copy path (or its repository location URL) to a destination 
     * URL immediately committing changes to a repository.
     * 
     * <p>
     * If <code>srcRevision</code> is not {@link SVNRevision#WORKING} then the repository
     * location URL of <code>srcPath</code> is copied to <code>dstURL</code>. Otherwise
     * <code>srcPath</code> itself.
     * 
     * @param  srcPath        a source Working Copy path
     * @param  srcRevision    a revision of <code>srcPath</code>
     * @param  dstURL         a target URL where <code>srcPath</code> is to be
     *                        copied
     * @param  commitMessage  a commit log message
     * @return                information on the committed revision
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li><code>srcPath</code> is not under version control
     *                        <li><code>srcPath</code> has no URL
     *                        <li>the repository location of <code>srcPath</code> was not 
     *                        found in <code>srcRevision</code>
     *                        <li><code>dstURL</code> already exists
     *                        </ul>
     */
    public SVNCommitInfo doCopy(File srcPath, SVNRevision srcRevision, SVNURL dstURL, String commitMessage) throws SVNException {
        // may be url->url.
        if (srcRevision.isValid() && srcRevision != SVNRevision.WORKING) {
            SVNWCAccess wcAccess = createWCAccess(srcPath);
            SVNEntry srcEntry = wcAccess.getTargetEntry();
            if (srcEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", srcPath);
                SVNErrorManager.error(err);
            }
            if (srcEntry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", srcPath);
                SVNErrorManager.error(err);
            }
            return doCopy(srcEntry.getSVNURL(), srcRevision, dstURL, false, commitMessage);
        }
        SVNWCAccess wcAccess = createWCAccess(srcPath);
        
        SVNURL dstAnchorURL = dstURL.removePathTail();
        String dstTarget = SVNPathUtil.tail(dstURL.toString());
        dstTarget = SVNEncodingUtil.uriDecode(dstTarget);
        
        SVNRepository repository = createRepository(dstAnchorURL, true);
        SVNNodeKind dstKind = repository.checkPath(dstTarget, -1);
        if (dstKind == SVNNodeKind.DIR) {
            dstURL = dstURL.appendPath(srcPath.getName(), false);
        } else if (dstKind == SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, "File ''{0}'' already exists", dstURL);
            SVNErrorManager.error(err);
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
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", srcPath);
                SVNErrorManager.error(err);
                return SVNCommitInfo.NULL;
            }
            
            SVNCommitUtil.harvestCommitables(commitables, wcAccess.getTarget(), srcPath, null, entry, dstURL.toString(), entry.getURL(), 
                    true, false, false, null, true, false);
            items = (SVNCommitItem[]) commitables.values().toArray(new SVNCommitItem[commitables.values().size()]);
            for (int i = 0; i < items.length; i++) {
                items[i].setWCAccess(wcAccess);
            }
            
            commitables = new TreeMap();
            dstURL = SVNURL.parseURIEncoded(SVNCommitUtil.translateCommitables(items, commitables));

            repository = createRepository(dstURL, true);
            SVNCommitMediator mediator = new SVNCommitMediator(commitables);
            tmpFiles = mediator.getTmpFiles();

            commitEditor = repository.getCommitEditor(commitMessage, null, false, mediator);
            info = SVNCommitter.commit(tmpFiles, commitables, repository.getRepositoryRoot(true).getPath(), commitEditor);
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
        if (info != null && info.getNewRevision() >= 0) { 
            dispatchEvent(SVNEventFactory.createCommitCompletedEvent(null, info.getNewRevision()), ISVNEventHandler.UNKNOWN);
        }
        return info != null ? info : SVNCommitInfo.NULL;
    }
    
    /**
     * Copies a source URL to a destination Working Copy path. 
     * 
     * <p>
     * <code>dstPath</code> will be automatically scheduled for addition with history.
     * 
     * @param  srcURL         a source URL
     * @param  srcRevision    a revision of <code>srcURL</code>
     * @param  dstPath        a destination WC path
     * @return                the revision number of a source
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li><code>srcURL</code> was not found in <code>srcRevision</code>
     *                        <li><code>dstPath</code> already exists
     *                        <li><code>dstPath</code> appears in <code>srcURL</code>
     *                        <li><code>dstPath</code> and <code>srcURL</code> are from
     *                        different repositories
     *                        <li><code>dstPath</code> is under version control but missing
     *                        </ul>
     */
    public long doCopy(SVNURL srcURL, SVNRevision srcRevision, File dstPath) throws SVNException {
        SVNRepository repository = createRepository(srcURL, true);
        if (!srcRevision.isValid()) {
            srcRevision = SVNRevision.HEAD;
        }
        long srcRevisionNumber = getRevisionNumber(srcRevision, repository, null);
        SVNNodeKind srcKind = repository.checkPath("", srcRevisionNumber);
        
        if (srcKind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' not found in revision {1}",
                    new Object[] {srcURL, new Long(srcRevisionNumber)});
            SVNErrorManager.error(err);
        }
        
        SVNFileType dstFileType = SVNFileType.getType(dstPath);
        if (dstFileType == SVNFileType.DIRECTORY) {
            dstPath = new File(dstPath, SVNPathUtil.tail(srcURL.getPath()));
        } else if (dstFileType != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "File ''{0}'' already exists", dstPath);
            SVNErrorManager.error(err);
        }
        dstFileType = SVNFileType.getType(dstPath);
        if (dstFileType != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "''{0}'' is in the way", dstPath);
            SVNErrorManager.error(err);
        }
        
        SVNWCAccess dstAccess = createWCAccess(dstPath);
        SVNEntry dstEntry = dstAccess.getTargetEntry();
        if (dstEntry != null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Entry for ''{0}'' exists (though the working copy file is missing)", dstPath);
            SVNErrorManager.error(err);
        }
        
        boolean sameRepositories;
        
        String srcUUID = null;
        String dstUUID = null;
        SVNWCAccess dstParentAccess = createWCAccess(dstPath.getParentFile());
        SVNEntry dstParentEntry = dstParentAccess.getTargetEntry();
        SVNURL repositoryRootURL = repository.getRepositoryRoot(true);
        try {
            srcUUID = repository.getRepositoryUUID();
            dstUUID = dstParentEntry != null ? dstParentEntry.getUUID() : null;
            if (dstParentEntry != null && dstParentEntry.getURL() != null && dstUUID == null) {
                SVNURL url = dstParentEntry.getSVNURL();
                SVNRepository dstRepos = createRepository(url, false);
                dstUUID = dstRepos.getRepositoryUUID();
            }
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NO_REPOS_UUID) {
                srcUUID = dstUUID = null;
            } else {
                throw e;
            }
        }
        if (dstUUID == null || srcUUID == null) {
            sameRepositories = false;
        } else {
            sameRepositories = srcUUID.equals(dstUUID);
        }
        
        long revision = -1;
        if (srcKind == SVNNodeKind.DIR) {
            // do checkout.
            SVNUpdateClient updateClient = new SVNUpdateClient(getRepositoryPool(), getOptions());
            updateClient.setEventHandler(getEventDispatcher());

            updateClient.setEventPathPrefix("");
            try {
                revision = updateClient.doCheckout(srcURL, dstPath, srcRevision, srcRevision, true);
            } finally {
                updateClient.setEventPathPrefix(null);
            }
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
                    dstAccess.close(true);
                    dstAccess = createWCAccess(dstPath);
                    addDir(dstAccess.getTarget(), "", srcURL.toString(), revision);
                    dstAccess.open(true, true);
                    updateCopiedDirectory(dstAccess.getTarget(), "", newURL.toString(), repositoryRootURL.toString(), null, -1);
                } finally {
                    dstAccess.close(true);
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Source URL ''{0}'' is from foreign repository; leaving it as a disjoint WC", srcURL);
                SVNErrorManager.error(err);
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
    
    /**
     * Copies/moves a source Working Copy path to a destination Working Copy path.
     * 
     * <p>
     * If <code>srcRevision</code> is not {@link SVNRevision#WORKING} and 
     * <code>isMove = </code><span class="javakeyword">false</span>, then the repository
     * location URL of <code>srcPath</code> is copied to <code>dstPath</code>. Otherwise
     * <code>srcPath</code> itself.
     * 
     * <p>
     * <code>dstPath</code> will be automatically scheduled for addition with history.
     * 
     * @param  srcPath        a source WC path
     * @param  srcRevision    a revision of <code>srcPath</code>
     * @param  dstPath        a destination WC path 
     * @param  force          <span class="javakeyword">true</span> to force the operation
     *                        to run
     * @param  isMove         <span class="javakeyword">true</span> to move the source
     *                        to the target (only WC-to-WC), 
     *                        <span class="javakeyword">false</span> to copy
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li><code>dstPath</code> already exists and is in the way
     *                        containing an item with the same name as the source
     *                        <li><code>srcPath</code> is not under version control
     *                        <li><code>srcPath</code> does not exist
     *                        <li><code>srcPath</code> has no URL
     *                        <li><code>dstPath</code> is a child of <code>srcPath</code>
     *                        <li><code>dstPath</code> is scheduled for deletion
     *                        <li><code>isMove = </code><span class="javakeyword">true</span> and 
     *                        <code>dstURL = srcURL</code>
     *                        </ul>
     */
    public void doCopy(File srcPath, SVNRevision srcRevision, File dstPath, boolean force, boolean isMove) throws SVNException {
        if (srcRevision.isValid() && srcRevision != SVNRevision.WORKING && !isMove) {
            // url->wc copy
            SVNWCAccess wcAccess = createWCAccess(srcPath);
            SVNEntry srcEntry = wcAccess.getTargetEntry();
            if (srcEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", srcPath);
                SVNErrorManager.error(err);
            }
            if (srcEntry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", srcPath);
                SVNErrorManager.error(err);
            }
            doCopy(srcEntry.getSVNURL(), srcRevision, dstPath);
            return;
        }
        // 1. can't copy src to its own child
        if (SVNPathUtil.isChildOf(srcPath, dstPath)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot copy ''{0}'' into its own child ''{1}''",
                    new Object[] {srcPath, dstPath});
            SVNErrorManager.error(err);
        }
        // 2. can't move path into itself
        if (isMove && srcPath.equals(dstPath)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot move ''{0}'' into itself", srcPath);
            SVNErrorManager.error(err);
        }
        // 3. src should exist
        SVNFileType srcType = SVNFileType.getType(srcPath);
        if (srcType == SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Path ''{0}'' does not exist", srcPath);
            SVNErrorManager.error(err);
        }
        // 4. if dst exists - use its child
        SVNFileType dstType = SVNFileType.getType(dstPath);
        if (dstType == SVNFileType.DIRECTORY) {
            dstPath = new File(dstPath, srcPath.getName());
            dstType = SVNFileType.getType(dstPath);
            if (dstType != SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "''{0}'' already exists and is in a way", dstPath);
                SVNErrorManager.error(err);
            }
        } else if (dstType != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "File ''{0}'' already exists", dstPath);
            SVNErrorManager.error(err);
        }
        // 5. if move -> check if dst could be deleted later.
        SVNWCAccess srcAccess = createWCAccess(srcPath);
        SVNWCAccess dstAccess = createWCAccess(dstPath);
        try {
            if (srcAccess.getTargetEntry() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", srcPath);
                SVNErrorManager.error(err);
            }
            // compare src entry repos with dst parent repos.
            String srcRepos = srcAccess.getTargetEntry().getRepositoryRoot();
            String dstRepos = dstAccess.getAnchor().getEntries().getEntry("", true).getRepositoryRoot();
            if (srcRepos != null && dstRepos != null && !srcRepos.equals(dstRepos)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE, "Cannot copy to ''{0}'', as it is not from repository ''{1}''; it is from ''{2}''",
                        new Object[] {dstPath, srcRepos, dstRepos});
                SVNErrorManager.error(err);
            }
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
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE, "Cannot copy to ''{0}'' as it is scheduled for deletion", dstPath);
                SVNErrorManager.error(err);
            }
            if (srcType == SVNFileType.DIRECTORY) {
                copyDirectory(dstAccess, srcAccess, dstPath.getName());
            } else {
                copyFile(dstAccess, srcAccess, dstPath.getName());
            }

            if (isMove) {
                srcAccess.getAnchor().scheduleForDeletion(srcPath.getName(), true);
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
        dir.mergeProperties(fileName, null, regularProps, true, log);

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
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "''{0}'' is scheduled for deletion; it must be committed before being overwritten", dstPath);
                SVNErrorManager.error(err);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "There is already versioned item ''{0}''", dstPath);
                SVNErrorManager.error(err);
            }
        }
        SVNEntry srcEntry = srcAccess.getTargetEntry();
        if (srcEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "Cannot copy or move ''{0}'': it's not under version control", srcPath);
            SVNErrorManager.error(err);
        } else if (srcEntry.isScheduledForAddition() || srcEntry.getURL() == null || srcEntry.isCopied()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot copy or move ''{0}'': it's not in repository yet; try committing first", srcPath);
            SVNErrorManager.error(err);
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
            updateCopiedDirectory(dir, name, null, entry.getRepositoryRoot(), null, -1);
        }
        dir.getEntries().save(true);
    }

    private void copyDirectory(SVNWCAccess dstAccess, SVNWCAccess srcAccess, String dstName) throws SVNException {
        SVNEntry srcEntry = srcAccess.getTargetEntry();
        if (srcEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", srcAccess.getTarget().getRoot());
            SVNErrorManager.error(err);
        } else if (srcEntry.isScheduledForAddition() || srcEntry.getURL() == null || srcEntry.isCopied()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot copy or move ''{0}'': it is not in repository yet; try committing first", srcAccess.getTarget().getRoot());
            SVNErrorManager.error(err);
        }
        String copyFromURL = srcEntry.getURL();
        long copyFromRev = srcEntry.getRevision();

        String newURL = dstAccess.getAnchor().getEntries().getEntry("", true).getURL();
        String reposRootURL = dstAccess.getAnchor().getEntries().getEntry("", true).getRepositoryRoot();
        newURL = SVNPathUtil.append(newURL, SVNEncodingUtil.uriEncode(dstName));

        File dstPath = new File(dstAccess.getAnchor().getRoot(), dstName);

        SVNFileUtil.copyDirectory(srcAccess.getTarget().getRoot(), dstPath, true, this);

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

        updateCopiedDirectory(newDir, "", newURL, reposRootURL, null, -1);
        SVNEntry newRoot = newDir.getEntries().getEntry("", true);
        newRoot.scheduleForAddition();
        newRoot.setCopyFromRevision(copyFromRev);
        newRoot.setCopyFromURL(copyFromURL);
        newDir.getEntries().save(true);
        // fire added event.
    }

    static void updateCopiedDirectory(SVNDirectory dir, String name, String newURL, String reposRootURL, String copyFromURL, long copyFromRevision) throws SVNException {
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry(name, true);
        if (entry != null) {
            entry.setCopied(true);
            if (newURL != null) {
                entry.setURL(newURL);
            }
            entry.setRepositoryRoot(reposRootURL);
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
                    updateCopiedDirectory(childDir, "", newURL, reposRootURL, childCopyFromURL, copyFromRevision);
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
                    updateCopiedDirectory(dir, childEntry.getName(), newChildURL, reposRootURL, childCopyFromURL, copyFromRevision);
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
