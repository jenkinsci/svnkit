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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNCancelException;
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
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNImportMediator;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * The <b>SVNCommitClient</b> class provides methods to perform operations that relate to 
 * committing changes to an SVN repository. These operations are similar to 
 * respective commands of the native SVN command line client 
 * and include ones which operate on working copy items as well as ones
 * that operate only on a repository.
 * 
 * <p>
 * Here's a list of the <b>SVNCommitClient</b>'s commit-related methods 
 * matched against corresponing commands of the SVN command line 
 * client:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>JavaSVN</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doCommit()</td><td>'svn commit'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doImport()</td><td>'svn import'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doDelete()</td><td>'svn delete URL'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doMkDir()</td><td>'svn mkdir URL'</td>
 * </tr>
 * </table>
 *   
 * @version 1.0
 * @author  TMate Software Ltd.
 * @see     <a target="_top" href="http://tmate.org/svn/kb/examples/">Examples</a>
 * 
 */
public class SVNCommitClient extends SVNBasicClient {

    private ISVNCommitHandler myCommitHandler;
    
    /**
     * Constructs and initializes an <b>SVNCommitClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNCommitClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNCommitClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the 
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     * 
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver     
     */
    public SVNCommitClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNCommitClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
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
     * If using <b>SVNCommitClient</b> without specifying any
     * commit handler then a default one will be used - {@link DefaultSVNCommitHandler}.
     * 
     * @param handler				an implementor's handler that will be used to handle 
     * 								commit log messages
     * @see	  #getCommitHandler()
     * @see	  ISVNCommitHandler
     */
    public void setCommitHander(ISVNCommitHandler handler) {
        myCommitHandler = handler;
    }
    
    /**
     * Returns the specified commit handler (if set) being in use or a default one 
     * (<b>DefaultSVNCommitHandler</b>) if no special 
     * implementations of <b>ISVNCommitHandler</b> were 
     * previousely provided.
     *   
     * @return	the commit handler being in use or a default one
     * @see	    #setCommitHander(ISVNCommitHandler)
     * @see		ISVNCommitHandler
     * @see		DefaultSVNCommitHandler 
     */
    public ISVNCommitHandler getCommitHandler() {
        if (myCommitHandler == null) {
            myCommitHandler = new DefaultSVNCommitHandler();
        }
        return myCommitHandler;
    }
    
    /**
     * Committs removing specified URL-paths from the repository. 
     *   
     * @param  urls				an array containing URL-strings that represent
     * 							repository locations to be removed
     * @param  commitMessage	a string to be a commit log message
     * @return					information on a new revision as the result
     * 							of the commit
     * @throws SVNException     if one of the following is true:
     *                          <ul>
     *                          <li>a URL does not exist
     *                          <li>probably some of URLs refer to different
     *                          repositories
     *                          </ul>
     */
    public SVNCommitInfo doDelete(SVNURL[] urls, String commitMessage)
            throws SVNException {
        if (urls == null || urls.length == 0) {
            return SVNCommitInfo.NULL;
        }
        List paths = new ArrayList();
        SVNURL rootURL = SVNURLUtil.condenceURLs(urls, paths, true);
        if (rootURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "Cannot compute common root URL for specified URLs");
            SVNErrorManager.error(err);
        }
        if (paths.isEmpty()) {
            // there is just root.
            paths.add(SVNPathUtil.tail(rootURL.getURIEncodedPath()));
            rootURL = rootURL.removePathTail();
        }
        SVNCommitItem[] commitItems = new SVNCommitItem[paths.size()];
        for (int i = 0; i < commitItems.length; i++) {
            String path = (String) paths.get(i);
            commitItems[i] = new SVNCommitItem(null, rootURL.appendPath(path, true),
                    null, SVNNodeKind.NONE, SVNRevision.UNDEFINED, false, true, false, false, false, false);
        }
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, commitItems);
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }

        List decodedPaths = new ArrayList();
        for (Iterator commitPaths = paths.iterator(); commitPaths.hasNext();) {
            String path = (String) commitPaths.next();
            decodedPaths.add(SVNEncodingUtil.uriDecode(path));
        }
        paths = decodedPaths;
        SVNRepository repos = createRepository(rootURL, true);
        for (Iterator commitPath = paths.iterator(); commitPath.hasNext();) {
            String path = (String) commitPath.next();
            SVNNodeKind kind = repos.checkPath(path, -1);
            if (kind == SVNNodeKind.NONE) {
                SVNURL url = rootURL.appendPath(path, false);
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "URL ''{0}'' does not exist", url);
                SVNErrorManager.error(err);
            }
        }
        commitMessage = validateCommitMessage(commitMessage);
        ISVNEditor commitEditor = repos.getCommitEditor(commitMessage, null, false, null);
        ISVNCommitPathHandler deleter = new ISVNCommitPathHandler() {
            public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
                commitEditor.deleteEntry(commitPath, -1);
                return false;
            }
        };
        SVNCommitInfo info;
        try {
            SVNCommitUtil.driveCommitEditor(deleter, paths, commitEditor, -1);
            info = commitEditor.closeEdit();
        } catch (SVNException e) {
            try {
                commitEditor.abortEdit();
            } catch (SVNException inner) {
                //
            }
            throw e;
        }
        if (info != null && info.getNewRevision() >= 0) { 
            dispatchEvent(SVNEventFactory.createCommitCompletedEvent(null, info.getNewRevision()), ISVNEventHandler.UNKNOWN);
        }
        return info != null ? info : SVNCommitInfo.NULL;
    }
    
    /**
     * Committs a creation of a new directory/directories in the repository.
     * 
     * @param  urls				an array containing URL-strings that represent
     * 							new repository locations to be created
     * @param  commitMessage	a string to be a commit log message
     * @return					information on a new revision as the result
     * 							of the commit
     * @throws SVNException     if some of URLs refer to different
     *                          repositories
     */
    public SVNCommitInfo doMkDir(SVNURL[] urls, String commitMessage) throws SVNException {
        if (urls == null || urls.length == 0) {
            return SVNCommitInfo.NULL;
        }
        List paths = new ArrayList();
        SVNURL rootURL = SVNURLUtil.condenceURLs(urls, paths, false);
        if (rootURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "Cannot compute common root URL for specified URLs");
            SVNErrorManager.error(err);
        }
        if (paths.isEmpty()) {
            paths.add(SVNPathUtil.tail(rootURL.getURIEncodedPath()));
            rootURL = rootURL.removePathTail();
        }
        
        if (paths.contains("")) {
            List convertedPaths = new ArrayList();
            String tail = SVNPathUtil.tail(rootURL.getURIEncodedPath());
            rootURL = rootURL.removePathTail();
            for (Iterator commitPaths = paths.iterator(); commitPaths.hasNext();) {
                String path = (String) commitPaths.next();
                if ("".equals(path)) {
                    convertedPaths.add(tail);
                } else {
                    convertedPaths.add(SVNPathUtil.append(tail, path));
                }
            }
            paths = convertedPaths;
        }
        SVNCommitItem[] commitItems = new SVNCommitItem[paths.size()];
        for (int i = 0; i < commitItems.length; i++) {
            String path = (String) paths.get(i);
            commitItems[i] = new SVNCommitItem(null, rootURL.appendPath(path, true),
                    null, SVNNodeKind.DIR, SVNRevision.UNDEFINED, true, false, false, false, false, false);
        }
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, commitItems);
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }

        List decodedPaths = new ArrayList();
        for (Iterator commitPaths = paths.iterator(); commitPaths.hasNext();) {
            String path = (String) commitPaths.next();
            decodedPaths.add(SVNEncodingUtil.uriDecode(path));
        }
        paths = decodedPaths;
        SVNRepository repos = createRepository(rootURL, true);
        commitMessage = validateCommitMessage(commitMessage);
        ISVNEditor commitEditor = repos.getCommitEditor(commitMessage, null, false, null);
        ISVNCommitPathHandler creater = new ISVNCommitPathHandler() {
            public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
                commitEditor.addDir(commitPath, null, -1);
                return true;
            }
        };
        SVNCommitInfo info;
        try {
            SVNCommitUtil.driveCommitEditor(creater, paths, commitEditor, -1);
            info = commitEditor.closeEdit();
        } catch (SVNException e) {
            try {
                commitEditor.abortEdit();
            } catch (SVNException inner) {
                //
            }
            throw e;
        }
        if (info != null && info.getNewRevision() >= 0) { 
            dispatchEvent(SVNEventFactory.createCommitCompletedEvent(null, info.getNewRevision()), ISVNEventHandler.UNKNOWN);
        }
        return info != null ? info : SVNCommitInfo.NULL;
    }
    
    /**
     * Committs an addition of a local unversioned file or directory into 
     * the repository. If the destination URL (<code>dstURL</code>) contains any
     * non-existent parent directories they will be automatically created by the
     * server. 
     * 
     * @param  path				a local unversioned file or directory to be imported
     * 							into the repository
     * @param  dstURL			a URL-string that represents a repository location
     * 							where the <code>path</code> will be imported 			
     * @param  commitMessage	a string to be a commit log message
     * @param  recursive		this flag is relevant only when the <code>path</code> is 
     * 							a directory: if <span class="javakeyword">true</span> then the entire directory
     * 							tree will be imported including all child directories, otherwise 
     * 							only items located in the directory itself
     * @return					information on a new revision as the result
     * 							of the commit
     * @throws SVNException     if one of the following is true:
     *                          <ul>
     *                          <li><code>dstURL</code> is invalid
     *                          <li>the path denoted by <code>dstURL</code> already
     *                          exists
     *                          <li><code>path</code> contains a reserved name - <i>'.svn'</i>
     *                          </ul>
     */
    public SVNCommitInfo doImport(File path, SVNURL dstURL, String commitMessage, boolean recursive) throws SVNException {
        return doImport(path, dstURL, commitMessage, true, recursive);
    }

    /**
     * Committs an addition of a local unversioned file or directory into 
     * the repository. If the destination URL (<code>dstURL</code>) contains any
     * non-existent parent directories they will be automatically created by the
     * server. 
     * 
     * @param  path             a local unversioned file or directory to be imported
     *                          into the repository
     * @param  dstURL           a URL-string that represents a repository location
     *                          where the <code>path</code> will be imported            
     * @param  commitMessage    a string to be a commit log message
     * @param  useGlobalIgnores if <span class="javakeyword">true</span> 
     *                          then those paths that match global ignore patterns controlled 
     *                          by a config options driver (see {@link ISVNOptions#isIgnored(String) isIgnored()}) 
     *                          will not be imported, otherwise global ignore patterns are not  
     *                          used
     * @param  recursive        this flag is relevant only when the <code>path</code> is 
     *                          a directory: if <span class="javakeyword">true</span> then the entire directory
     *                          tree will be imported including all child directories, otherwise 
     *                          only items located in the directory itself
     * @return                  information on a new revision as the result
     *                          of the commit
     * @throws SVNException     if one of the following is true:
     *                          <ul>
     *                          <li><code>dstURL</code> is invalid
     *                          <li>the path denoted by <code>dstURL</code> already
     *                          exists
     *                          <li><code>path</code> contains a reserved name - <i>'.svn'</i>
     *                          </ul>
     */
    public SVNCommitInfo doImport(File path, SVNURL dstURL, String commitMessage, boolean useGlobalIgnores, boolean recursive) throws SVNException {
        // first find dstURL root.
        SVNRepository repos = null;
        SVNFileType srcKind = SVNFileType.getType(path);
        List newPaths = new ArrayList();
        SVNURL rootURL = dstURL;
        while (true) {
            repos = createRepository(rootURL, true);
            if (repos.checkPath("", -1) == SVNNodeKind.NONE) {
                newPaths.add(SVNPathUtil.tail(rootURL.getPath()));
                rootURL = rootURL.removePathTail();
            } else {
                break;
            }
        }
        if (newPaths.isEmpty() && (srcKind == SVNFileType.FILE || srcKind == SVNFileType.SYMLINK)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "Path ''{0}'' already exists", dstURL);            
            SVNErrorManager.error(err);
        }
        if (newPaths.contains(SVNFileUtil.getAdminDirectoryName())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ADM_DIR_RESERVED, "''{0}'' is a reserved name and cannot be imported", SVNFileUtil.getAdminDirectoryName());            
            SVNErrorManager.error(err);
        }
        SVNCommitItem[] items = new SVNCommitItem[1];
        items[0] = new SVNCommitItem(path, dstURL, null, srcKind == SVNFileType.DIRECTORY ? SVNNodeKind.DIR : SVNNodeKind.FILE, SVNRevision.UNDEFINED, 
                        true, false, false, false, false, false);
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, items);
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }
        commitMessage = validateCommitMessage(commitMessage);
        ISVNEditor commitEditor = repos.getCommitEditor(commitMessage, null, false, new SVNImportMediator(srcKind == SVNFileType.DIRECTORY ? path : path.getParentFile()));
        String filePath = "";
        if (srcKind != SVNFileType.DIRECTORY) {
            filePath = (String) newPaths.remove(0);
            for (int i = 0; i < newPaths.size(); i++) {
                String newDir = (String) newPaths.get(i);
                filePath = newDir + "/" + filePath;
            }
        }
        checkCancelled();
        boolean changed = false;
        SVNCommitInfo info = null;
        try {
            commitEditor.openRoot(-1);
            String newDirPath = null;
            for (int i = newPaths.size() - 1; i >= 0; i--) {
                newDirPath = newDirPath == null ? (String) newPaths.get(i) : SVNPathUtil.append(newDirPath, (String) newPaths.get(i));
                commitEditor.addDir(newDirPath, null, -1);
            }
            if (srcKind == SVNFileType.DIRECTORY) {
                changed = importDir(path, path, newDirPath, useGlobalIgnores, recursive, commitEditor);
            } else {
                if (useGlobalIgnores && getOptions().isIgnored(path.getName())) {
                    changed = false;
                } else {
                    changed = importFile(path.getParentFile(), path, srcKind, filePath, commitEditor);
                }
            }
            if (!changed) {
                try {
                    commitEditor.abortEdit();
                } catch (SVNException e) {}
                return SVNCommitInfo.NULL;
            }
            for (int i = 0; i < newPaths.size(); i++) {
                commitEditor.closeDir();
            }
            info = commitEditor.closeEdit();
        } finally {
            if (!changed || info == null) {
                try {
                    commitEditor.abortEdit();
                } catch (SVNException e) {
                    //
                }
            }
        }
        if (info != null && info.getNewRevision() >= 0) { 
            dispatchEvent(SVNEventFactory.createCommitCompletedEvent(null, info.getNewRevision()), ISVNEventHandler.UNKNOWN);
        }
        return info != null ? info : SVNCommitInfo.NULL;
    }
    
    /**
     * Committs local changes made to the Working Copy items (provided as an array of 
     * {@link java.io.File}s) to the repository. 
     * 
     * @param  paths			an array of local items which should be traversed
     * 							to commit changes they have to the repository  
     * @param  keepLocks		if <span class="javakeyword">true</span> and there are local items that 
     * 							were locked then the commit will left them locked,
     * 							otherwise the items will be unlocked after the commit
     * 							succeeds  
     * @param  commitMessage	a string to be a commit log message
     * @param  force			<span class="javakeyword">true</span> to force a non-recursive commit; if
     * 							<code>recursive</code> is set to <span class="javakeyword">true</span> the <code>force</code>
     * 							flag is ignored
     * @param  recursive		relevant only for directory items: if <span class="javakeyword">true</span> then 
     * 							the entire directory tree will be committed including all child directories, 
     * 							otherwise only items located in the directory itself
     * @return					information on a new revision as the result
     * 							of the commit
     * @throws SVNException
     * @see	                    #doCommit(SVNCommitPacket, boolean, String) 
     */
    public SVNCommitInfo doCommit(File[] paths, boolean keepLocks, String commitMessage, boolean force, boolean recursive) throws SVNException {
        SVNCommitPacket packet = doCollectCommitItems(paths, keepLocks, force, recursive);
        try {
            packet = packet.removeSkippedItems();
            return doCommit(packet, keepLocks, commitMessage);
        } finally {
            if (packet != null) {
                packet.dispose();
            }
        }
    }
    
    /**
     * Committs local changes made to the Working Copy items to the repository. 
     * 
     * <p>
     * <code>commitPacket</code> contains commit items (<b>SVNCommitItem</b>) 
     * which represent local Working Copy items that were changed and are to be committed. 
     * Commit items are gathered in a single <b>SVNCommitPacket</b>
     * by invoking {@link #doCollectCommitItems(File[], boolean, boolean, boolean) doCollectCommitItems()}. 
     * 
     * @param  commitPacket		a single object that contains items to be committed
     * @param  keepLocks		if <span class="javakeyword">true</span> and there are local items that 
     * 							were locked then the commit will left them locked,
     * 							otherwise the items will be unlocked after the commit
     * 							succeeds
     * @param  commitMessage	a string to be a commit log message
     * @return					information on a new revision as the result
     * 							of the commit
     * @throws SVNException     
     * @see	   SVNCommitItem
     * 
     */
    public SVNCommitInfo doCommit(SVNCommitPacket commitPacket, boolean keepLocks, String commitMessage) throws SVNException {
        SVNCommitInfo[] info = doCommit(new SVNCommitPacket[] {commitPacket}, keepLocks, commitMessage);
        if (info != null && info.length > 0) {
            if (info[0].getErrorMessage() != null) {
                SVNErrorManager.error(info[0].getErrorMessage());
            }
            return info[0];
        } 
        return SVNCommitInfo.NULL;
    }
    
    /**
     * Committs local changes, made to the Working Copy items, to the repository. 
     * 
     * <p>
     * <code>commitPackets</code> is an array of packets that contain commit items (<b>SVNCommitItem</b>) 
     * which represent local Working Copy items that were changed and are to be committed. 
     * Commit items are gathered in a single <b>SVNCommitPacket</b>
     * by invoking {@link #doCollectCommitItems(File[], boolean, boolean, boolean) doCollectCommitItems()}. 
     * 
     * <p>
     * This allows to commit separate trees of Working Copies "belonging" to different
     * repositories. One packet per one repository. If repositories are different (it means more than
     * one commit will be done), <code>commitMessage</code> may be replaced by a commit handler
     * to be a specific one for each commit.
     * 
     * @param  commitPackets    logically grouped items to be committed
     * @param  keepLocks        if <span class="javakeyword">true</span> and there are local items that 
     *                          were locked then the commit will left them locked,
     *                          otherwise the items will be unlocked after the commit
     *                          succeeds
     * @param  commitMessage    a string to be a commit log message
     * @return                  committed information
     * @throws SVNException
     */
    public SVNCommitInfo[] doCommit(SVNCommitPacket[] commitPackets, boolean keepLocks, String commitMessage) throws SVNException {
        if (commitPackets == null || commitPackets.length == 0) {
            return new SVNCommitInfo[0];
        }

        Collection tmpFiles = null;
        SVNCommitInfo info = null;
        ISVNEditor commitEditor = null;

        Collection infos = new ArrayList();
        boolean needsSleepForTimeStamp = false;
        for (int p = 0; p < commitPackets.length; p++) {
            SVNCommitPacket commitPacket = commitPackets[p].removeSkippedItems();
            if (commitPacket.getCommitItems().length == 0) {
                continue;
            }
            try {
                commitMessage = getCommitHandler().getCommitMessage(commitMessage, commitPacket.getCommitItems());                
                if (commitMessage == null) {
                    infos.add(SVNCommitInfo.NULL);
                    continue;
                }
                commitMessage = validateCommitMessage(commitMessage);
                Map commitables = new TreeMap();
                String baseURL = SVNCommitUtil.translateCommitables(commitPacket.getCommitItems(), commitables);
                Map lockTokens = SVNCommitUtil.translateLockTokens(commitPacket.getLockTokens(), baseURL);

                SVNRepository repository = createRepository(SVNURL.parseURIEncoded(baseURL), true);
                SVNCommitMediator mediator = new SVNCommitMediator(commitables);
                tmpFiles = mediator.getTmpFiles();
                commitEditor = repository.getCommitEditor(commitMessage, lockTokens, keepLocks, mediator);
                // commit.
                // set event handler for each wc access.
                for (int i = 0; i < commitPacket.getCommitItems().length; i++) {
                    commitPacket.getCommitItems()[i].getWCAccess().setEventDispatcher(getEventDispatcher());
                }
                String repositoryRoot = repository.getRepositoryRoot(true).getPath();
                info = SVNCommitter.commit(mediator.getTmpFiles(), commitables, repositoryRoot, commitEditor);
                // update wc.
                Collection processedItems = new HashSet();
                Collection explicitCommitPaths = new HashSet();
                for (Iterator urls = commitables.keySet().iterator(); urls.hasNext();) {
                    String url = (String) urls.next();
                    SVNCommitItem item = (SVNCommitItem) commitables.get(url);
                    explicitCommitPaths.add(item.getPath());
                }
                
                for (Iterator urls = commitables.keySet().iterator(); urls.hasNext();) {
                    String url = (String) urls.next();
                    SVNCommitItem item = (SVNCommitItem) commitables.get(url);
                    SVNWCAccess wcAccess = item.getWCAccess();
                    String path = item.getPath();
                    SVNDirectory dir;
                    String target;

                    if (item.getKind() == SVNNodeKind.DIR) {
                        dir = wcAccess.getDirectory(path);
                        target = "";
                    } else {
                        dir = wcAccess.getDirectory(SVNPathUtil.removeTail(path));
                        target = SVNPathUtil.tail(path);
                    }
                    if (dir == null) {
                        if (hasProcessedParents(processedItems, path)) {
                            processedItems.add(path);
                            continue;
                        }
                        if (item.isDeleted() && item.getKind() == SVNNodeKind.DIR) {
                            String parentPath = "".equals(path) ? null : SVNPathUtil.removeTail(path);
                            String nameInParent = "".equals(path) ? null : SVNPathUtil.tail(path);
                            if (parentPath != null) {
                                SVNDirectory parentDir = wcAccess.getDirectory(parentPath);
                                if (parentDir != null) {
                                    SVNEntry entryInParent = parentDir.getEntries().getEntry(nameInParent, true);
                                    if (entryInParent != null) {
                                        entryInParent.unschedule();
                                        entryInParent.setDeleted(true);
                                        parentDir.getEntries().save(false);
                                    }
                                }
                            }
                            processedItems.add(path);
                            continue;
                        }

                    }
                    SVNEntry entry = dir.getEntries().getEntry(target, true);
                    if (entry == null && hasProcessedParents(processedItems, path)) {
                        processedItems.add(path);
                        continue;
                    }
                    boolean recurse = false;
                    if (item.isAdded() && item.getCopyFromURL() != null && item.getKind() == SVNNodeKind.DIR) {
                        recurse = true;
                    }
                    boolean removeLock = !keepLocks && item.isLocked();
                    // update entry in dir.
                    Map wcPropChanges = mediator.getWCProperties(item);
                    dir.commit(target, info, wcPropChanges, removeLock, recurse, explicitCommitPaths);
                    processedItems.add(path);
                } 
                needsSleepForTimeStamp = true;
                // commit completed, include revision number.
                dispatchEvent(SVNEventFactory.createCommitCompletedEvent(null, info.getNewRevision()), ISVNEventHandler.UNKNOWN);
            } catch (SVNException e) {
                if (e instanceof SVNCancelException) {
                    throw e;
                }
                SVNErrorMessage err = SVNErrorMessage.create(e.getErrorMessage().getErrorCode(), "Commit failed (details follow):");
                err.setChildErrorMessage(e.getErrorMessage());
                infos.add(new SVNCommitInfo(-1, null, null, err));
                dispatchEvent(new SVNEvent(err), ISVNEventHandler.UNKNOWN);
                continue;
            } finally {
                if (info == null && commitEditor != null) {
                    try {
                        commitEditor.abortEdit();
                    } catch (SVNException e) {
                        //
                    }
                }
                if (tmpFiles != null) {
                    for (Iterator files = tmpFiles.iterator(); files.hasNext();) {
                        File file = (File) files.next();
                        file.delete();
                    }
                }
                if (commitPacket != null) {
                    commitPacket.dispose();
                }
            }
            infos.add(info != null ? info : SVNCommitInfo.NULL);
        }
        if (needsSleepForTimeStamp) {
            sleepForTimeStamp();
        }
        return (SVNCommitInfo[]) infos.toArray(new SVNCommitInfo[infos.size()]);
    }
    
    /**
     * Collects commit items (containing detailed information on each Working Copy item
     * that was changed and need to be committed to the repository) into a single 
     * <b>SVNCommitPacket</b>. Further this commit packet can be passed to
     * {@link #doCommit(SVNCommitPacket, boolean, String) doCommit()}.
     * 
     * @param  paths			an array of local items which should be traversed
     * 							to collect information on every changed item (one 
     * 							<b>SVNCommitItem</b> per each
     * 							modified local item)
     * @param  keepLocks		if <span class="javakeyword">true</span> and there are local items that 
     * 							were locked then these items will be left locked after
     * 							traversing all of them, otherwise the items will be unlocked
     * @param  force			forces collecting commit items for a non-recursive commit  
     * @param  recursive		relevant only for directory items: if <span class="javakeyword">true</span> then 
     * 							the entire directory tree will be traversed including all child 
     * 							directories, otherwise only items located in the directory itself
     * 							will be processed
     * @return					an <b>SVNCommitPacket</b> containing
     * 							all Working Copy items having local modifications and represented as 
     * 							<b>SVNCommitItem</b> objects; if no modified
     * 							items were found then 
     * 							{@link SVNCommitPacket#EMPTY} is returned
     * @throws SVNException
     * @see	                    SVNCommitItem
     */
    public SVNCommitPacket doCollectCommitItems(File[] paths, boolean keepLocks, boolean force, boolean recursive) throws SVNException {
        if (paths == null || paths.length == 0) {
            return SVNCommitPacket.EMPTY;
        }
        Set targets = new TreeSet();
        SVNStatusClient statusClient = new SVNStatusClient(getRepositoryPool(), getOptions());
        SVNWCAccess wcAccess = SVNCommitUtil.createCommitWCAccess(paths, recursive, force, targets, statusClient);
        try {
            Map lockTokens = new HashMap();
            SVNCommitItem[] commitItems = SVNCommitUtil.harvestCommitables(wcAccess, targets, lockTokens, !keepLocks, recursive, force);
            boolean hasModifications = false;
            for (int i = 0; commitItems != null && i < commitItems.length; i++) {
                SVNCommitItem commitItem = commitItems[i];
                if (commitItem.isAdded() || commitItem.isDeleted()
                        || commitItem.isContentsModified()
                        || commitItem.isPropertiesModified()
                        || commitItem.isCopied()) {
                    hasModifications = true;
                    break;
                }
            }
            if (!hasModifications) {
                wcAccess.close(true);
                return SVNCommitPacket.EMPTY;
            }
            return new SVNCommitPacket(wcAccess, commitItems, lockTokens);
        } catch (SVNException e) {
            wcAccess.close(true);
            if (e instanceof SVNCancelException) {
                throw e;
            }
            SVNErrorMessage nestedErr = e.getErrorMessage();
            SVNErrorMessage err = SVNErrorMessage.create(nestedErr.getErrorCode(), "Commit failed (details follow):");
            SVNErrorManager.error(err, e);
            return null;
        }
    }
    
    /**
     * Collects commit items (containing detailed information on each Working Copy item
     * that was changed and need to be committed to the repository) into different 
     * <b>SVNCommitPacket</b>s. This allows to prepare commit packets for different
     * Working Copies located "belonging" different repositories. Separate packets will
     * be committed separately. If the repository is the same for all the paths, then all 
     * collected commit packets can be combined into a single one and committed in a single 
     * transaction. 
     * 
     * @param  paths            an array of local items which should be traversed
     *                          to collect information on every changed item (one 
     *                          <b>SVNCommitItem</b> per each
     *                          modified local item)
     * @param  keepLocks        if <span class="javakeyword">true</span> and there are local items that 
     *                          were locked then these items will be left locked after
     *                          traversing all of them, otherwise the items will be unlocked
     * @param  force            forces collecting commit items for a non-recursive commit  
     * @param  recursive        relevant only for directory items: if <span class="javakeyword">true</span> then 
     *                          the entire directory tree will be traversed including all child 
     *                          directories, otherwise only items located in the directory itself
     *                          will be processed
     * @param combinePackets    if <span class="javakeyword">true</span> then collected commit
     *                          packets will be joined into a single one, so that to be committed
     *                          in a single transaction
     * @return                  an array of commit packets
     * @throws SVNException
     * @see                     SVNCommitItem
     */
    public SVNCommitPacket[] doCollectCommitItems(File[] paths, boolean keepLocks, boolean force, boolean recursive, boolean combinePackets) throws SVNException {
        if (paths == null || paths.length == 0) {
            return new SVNCommitPacket[0];
        }
        Collection packets = new ArrayList();
        Map targets = new HashMap();
        SVNStatusClient statusClient = new SVNStatusClient(getRepositoryPool(), getOptions());
        SVNWCAccess[] wcAccesses = SVNCommitUtil.createCommitWCAccess2(paths, recursive, force, targets, statusClient);

        for (int i = 0; i < wcAccesses.length; i++) {
            SVNWCAccess wcAccess = wcAccesses[i];
            Collection targetPaths = (Collection) targets.get(wcAccess);
            try {
                Map lockTokens = new HashMap();
                SVNCommitItem[] commitItems = SVNCommitUtil.harvestCommitables(wcAccess, targetPaths, lockTokens, !keepLocks, recursive, force);
                boolean hasModifications = false;
                for (int j = 0; commitItems != null && j < commitItems.length; j++) {
                    SVNCommitItem commitItem = commitItems[j];
                    if (commitItem.isAdded() || commitItem.isDeleted() || commitItem.isContentsModified() || commitItem.isPropertiesModified() || commitItem.isCopied()) {
                        hasModifications = true;
                        break;
                    }
                }
                if (!hasModifications) {
                    wcAccess.close(true);
                    continue;
                }
                packets.add(new SVNCommitPacket(wcAccess, commitItems, lockTokens));
            } catch (SVNException e) {
                for (int j = 0; j < wcAccesses.length; j++) {
                    wcAccesses[j].close(true);
                }
                if (e instanceof SVNCancelException) {
                    throw e;
                }
                SVNErrorMessage nestedErr = e.getErrorMessage();
                SVNErrorMessage err = SVNErrorMessage.create(nestedErr.getErrorCode(), "Commit failed (details follow):");
                SVNErrorManager.error(err, e);
            }
        }
        SVNCommitPacket[] packetsArray = (SVNCommitPacket[]) packets.toArray(new SVNCommitPacket[packets.size()]);
        if (!combinePackets) {
            return packetsArray;
        }
        Map repoUUIDs = new HashMap();
        Map locktokensMap = new HashMap();
        try {
            // get wc root for each packet and uuid for each root.
            // group items by uuid.
            for (int i = 0; i < packetsArray.length; i++) {
                SVNCommitPacket packet = packetsArray[i];
                File wcRoot = SVNWCUtil.getWorkingCopyRoot(packet.getCommitItems()[0].getWCAccess().getAnchor().getRoot(), true);
                SVNWCAccess rootWCAccess = SVNWCAccess.create(wcRoot);
                String uuid = rootWCAccess.getTargetEntry().getUUID();
                SVNURL url = rootWCAccess.getTargetEntry().getSVNURL();
                rootWCAccess.close(false);
                if (uuid == null) {
                    if (url != null) {
                        SVNRepository repos = createRepository(url, true);
                        uuid = repos.getRepositoryUUID(true);
                    } else {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", wcRoot);
                        SVNErrorManager.error(err);
                    }
                }
                if (!repoUUIDs.containsKey(uuid)) {
                    repoUUIDs.put(uuid, new ArrayList());
                    locktokensMap.put(uuid, new HashMap());
                }
                Collection items = (Collection) repoUUIDs.get(uuid);
                Map lockTokens = (Map) locktokensMap.get(uuid);
                for (int j = 0; j < packet.getCommitItems().length; j++) {
                    items.add(packet.getCommitItems()[j]);
                }
                if (packet.getLockTokens() != null) {
                    lockTokens.putAll(packet.getLockTokens());
                }
            }
            packetsArray = new SVNCommitPacket[repoUUIDs.size()];
            int index = 0;
            for (Iterator roots = repoUUIDs.keySet().iterator(); roots.hasNext();) {
                String uuid = (String) roots.next();
                Collection items = (Collection) repoUUIDs.get(uuid);
                Map lockTokens = (Map) locktokensMap.get(uuid);
                SVNCommitItem[] itemsArray = (SVNCommitItem[]) items.toArray(new SVNCommitItem[items.size()]);
                packetsArray[index++] = new SVNCommitPacket(null, itemsArray, lockTokens);
            }
        } catch (SVNException e) {
            for (int j = 0; j < wcAccesses.length; j++) {
                wcAccesses[j].close(true);
            }
            if (e instanceof SVNCancelException) {
                throw e;
            }            
            SVNErrorMessage nestedErr = e.getErrorMessage();
            SVNErrorMessage err = SVNErrorMessage.create(nestedErr.getErrorCode(), "Commit failed (details follow):");
            SVNErrorManager.error(err, e);
        }
        return packetsArray;        
    }

    private boolean importDir(File rootFile, File dir, String importPath, boolean useGlobalIgnores, boolean recursive, ISVNEditor editor) throws SVNException {
        checkCancelled();
        File[] children = dir.listFiles();
        boolean changed = false;
        for (int i = 0; children != null && i < children.length; i++) {
            File file = children[i];
            if (SVNFileUtil.getAdminDirectoryName().equals(file.getName())) {
                SVNEvent skippedEvent = SVNEventFactory.createSkipEvent(
                        rootFile, file, SVNEventAction.SKIP, SVNEventAction.COMMIT_ADDED, SVNNodeKind.NONE);
                handleEvent(skippedEvent, ISVNEventHandler.UNKNOWN);
                continue;
            }
            if (useGlobalIgnores && getOptions().isIgnored(file.getName())) {
                continue;
            }
            String path = importPath == null ? file.getName() : SVNPathUtil.append(importPath, file.getName());
            SVNFileType fileType = SVNFileType.getType(file);
            if (fileType == SVNFileType.DIRECTORY && recursive) {
                editor.addDir(path, null, -1);
                changed |= true;
                SVNEvent event = SVNEventFactory.createCommitEvent(rootFile,
                        file, SVNEventAction.COMMIT_ADDED, SVNNodeKind.DIR,
                        null);
                handleEvent(event, ISVNEventHandler.UNKNOWN);
                importDir(rootFile, file, path, useGlobalIgnores, recursive, editor);
                editor.closeDir();
            } else if (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK){
                changed |= importFile(rootFile, file, fileType, path, editor);
            }

        }
        return changed;
    }

    private boolean importFile(File rootFile, File file, SVNFileType fileType, String filePath, ISVNEditor editor) throws SVNException {
        if (fileType == null || fileType == SVNFileType.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "unknown or unversionable type for ''{0}''", file);
            SVNErrorManager.error(err);
        }
        editor.addFile(filePath, null, -1);
        String mimeType = null;
        Map autoProperties = new HashMap();
        if (fileType != SVNFileType.SYMLINK) {
            autoProperties = getOptions().applyAutoProperties(file.getName(), autoProperties);
            if (!autoProperties.containsKey(SVNProperty.MIME_TYPE)) {
                mimeType = SVNFileUtil.detectMimeType(file);
                if (mimeType != null) {
                    autoProperties.put(SVNProperty.MIME_TYPE, mimeType);
                    autoProperties.remove(SVNProperty.EOL_STYLE);
                }
            }
            if (!autoProperties.containsKey(SVNProperty.EXECUTABLE) && SVNFileUtil.isExecutable(file)) {
                autoProperties.put(SVNProperty.EXECUTABLE, "");
            }
        } else {
            autoProperties.put(SVNProperty.SPECIAL, "*");
        }
        for (Iterator names = autoProperties.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            String value = (String) autoProperties.get(name);
            if (SVNProperty.EOL_STYLE.equals(name) && value != null) {
                if (SVNProperty.isBinaryMimeType((String) autoProperties.get(SVNProperty.MIME_TYPE))) {
                    SVNDebugLog.logInfo("svn: File '" + file + "' has binary mime-type, svn:eol-style property is not applicable");
                    continue;
                }else if (!SVNTranslator.checkNewLines(file)) {
                    SVNDebugLog.logInfo("svn: File '" + file + "' has inconsistent newlines");
                    continue;
                } 
            }
            editor.changeFileProperty(filePath, name, value);
        }
        // send "adding"
        SVNEvent addedEvent = SVNEventFactory.createCommitEvent(rootFile, file, SVNEventAction.COMMIT_ADDED, SVNNodeKind.FILE, mimeType);
        handleEvent(addedEvent, ISVNEventHandler.UNKNOWN);
        editor.applyTextDelta(filePath, null);
        // translate and send file.
        String eolStyle = (String) autoProperties.get(SVNProperty.EOL_STYLE);
        String keywords = (String) autoProperties.get(SVNProperty.KEYWORDS);
        boolean special = autoProperties.get(SVNProperty.SPECIAL) != null;
        File tmpFile = null;
        if (eolStyle != null || keywords != null || special) {
            byte[] eolBytes = SVNTranslator.getBaseEOL(eolStyle);
            Map keywordsMap = keywords != null ? SVNTranslator.computeKeywords(keywords, null, null, null, null) : null;
            tmpFile = SVNFileUtil.createUniqueFile(file.getParentFile(), file.getName(), ".tmp");
            SVNTranslator.translate(file, tmpFile, eolBytes, keywordsMap, special, false);
        }
        File importedFile = tmpFile != null ? tmpFile : file;
        String checksum = SVNFileUtil.computeChecksum(importedFile);
        SVNDiffWindow[] windows = SVNDiffWindowBuilder.createReplacementDiffWindows(importedFile.length(), 100*1024);
        byte[] newData = new byte[100*1024];
        InputStream is = null;
        OutputStream os = null;
        try {
            is = SVNFileUtil.openFileForReading(importedFile);
            for (int i = 0; i < windows.length; i++) {
                SVNDiffWindow window = windows[i];
                os = editor.textDeltaChunk(filePath, window);
                is.read(newData, 0, (int) window.getNewDataLength());
                os.write(newData, 0, (int) window.getNewDataLength());
                SVNFileUtil.closeFile(os);
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(is);
            SVNFileUtil.closeFile(os);
            if (tmpFile != null) {
                tmpFile.delete();
            }
        }
        editor.textDeltaEnd(filePath);
        editor.closeFile(filePath, checksum);
        return true;
    }

    private static boolean hasProcessedParents(Collection paths, String path) {
        path = SVNPathUtil.removeTail(path);
        if (paths.contains(path)) {
            return true;
        }
        if ("".equals(path)) {
            return false;
        }
        return hasProcessedParents(paths, path);
    }
    
    static String validateCommitMessage(String message) {
        return SVNTranslator.convertEOLs(message);
    }

}
