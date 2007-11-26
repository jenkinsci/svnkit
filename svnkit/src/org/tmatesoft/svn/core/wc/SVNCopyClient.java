/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNMergeInfoManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.internal.wc.SVNWCManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;

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
 * @version 1.1.1
 * @author  TMate Software Ltd.
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 * 
 */
public class SVNCopyClient extends SVNBasicClient {

    private ISVNCommitHandler myCommitHandler;
    private ISVNCommitParameters myCommitParameters;
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

    public SVNCopyClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
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
     * Sets commit parameters to use.
     * 
     * <p>
     * When no parameters are set {@link DefaultSVNCommitParameters default} 
     * ones are used. 
     * 
     * @param parameters commit parameters
     * @see              #getCommitParameters()
     */
    public void setCommitParameters(ISVNCommitParameters parameters) {
        myCommitParameters = parameters;
    }

    /**
     * Returns commit parameters. 
     * 
     * <p>
     * If no user parameters were previously specified, once creates and 
     * returns {@link DefaultSVNCommitParameters default} ones. 
     * 
     * @return commit parameters
     * @see    #setCommitParameters(ISVNCommitParameters)
     */
    public ISVNCommitParameters getCommitParameters() {
        if (myCommitParameters == null) {
            myCommitParameters = new DefaultSVNCommitParameters();
        }
       return myCommitParameters;
    }
    
    /**
     * Copies/moves a source URL to a destination one immediately committing changes
     * to a repository. Equivalent to <code>doCopy(srcURL, srcRevision, dstURL, isMove, false, commitMessage)</code>.
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
     * @throws SVNException
     * @see                   #doCopy(SVNURL, SVNRevision, SVNURL, boolean, boolean, String)                       
     */
    /*
    public SVNCommitInfo doCopy(SVNURL srcURL, SVNRevision srcRevision, SVNURL dstURL, boolean isMove, String commitMessage) throws SVNException {
        return doCopy(srcURL, srcRevision, dstURL, isMove, false, commitMessage);
    }*/
    
    /**
     * Copies/moves a source URL to a destination one immediately committing changes
     * to a repository. 
     * 
     * <p>
     * If <code>dstURL</code> and <code>srcURL</code> are the same, 
     * <code>failWhenDstExists</code> is <span class="javakeyword">false</span> and 
     * <code>srcURL</code> is a directory then this directory will be copied into itself.
     * 
     * <p> 
     * If <code>dstURL</code> is a directory, <code>dstURL</code> and <code>srcURL</code> are not the same, 
     * <code>failWhenDstExists</code> is <span class="javakeyword">false</span>, <code>dstURL</code> 
     * has not the last path element entry of <code>srcURL</code> then that entry will be copied into 
     * <code>dstURL</code>. 
     * 
     * @param  srcURL            a source repository location URL
     * @param  srcRevision       a revision of <code>srcURL</code>
     * @param  dstURL            a target URL where <code>srcURL</code> is to be
     *                           copied/moved
     * @param  isMove            <span class="javakeyword">true</span> to move the source
     *                           to the target (only URL-to-URL), 
     *                           <span class="javakeyword">false</span> to copy
     * @param failWhenDstExists  <span class="javakeyword">true</span> to force a failure if 
     *                           the destination exists   
     * @param  commitMessage     a commit log message
     * @return                   information on the committed revision
     * @throws SVNException      if one of the following is true:
     *                           <ul>
     *                           <li><code>srcURL</code> and <code>dstURL</code> are not in the
     *                           same repository  
     *                           <li><code>srcURL</code> was not found in <code>srcRevision</code>
     *                           <li><code>dstURL</code> and <code>srcURL</code> are the same and 
     *                           <code>failWhenDstExists</code> is <span class="javakeyword">true</span>
     *                           <li><code>dstURL</code> already exists and <code>failWhenDstExists</code> 
     *                           is <span class="javakeyword">true</span>
     *                           <li><code>dstURL</code> already exists, <code>failWhenDstExists</code> 
     *                           is <span class="javakeyword">false</span>, but <code>dstURL</code> 
     *                           already contains the top path element name of <code>srcURL</code> 
     *                           <li><code>isMove = </code><span class="javakeyword">true</span> and 
     *                           <code>dstURL = srcURL</code>
     *                           </ul>
     */
    /*
    public SVNCommitInfo doCopy(SVNURL srcURL, SVNRevision srcRevision, SVNURL dstURL, boolean isMove, boolean failWhenDstExists, String commitMessage) throws SVNException {
        return doCopy(srcURL, srcRevision, srcRevision, dstURL, isMove, failWhenDstExists, false, commitMessage, null);
    }
    

    public SVNCommitInfo doCopy(SVNURL srcURL, SVNRevision pegRevision, SVNRevision srcRevision, SVNURL dstURL, boolean isMove, boolean failWhenDstExists, boolean makeParents, String commitMessage, Map revisionProperties) throws SVNException {
        return doCopy(new SVNCopySource[]{new SVNCopySource(pegRevision, srcRevision, srcURL)}, dstURL, isMove, failWhenDstExists, makeParents, commitMessage, revisionProperties);
    }*/

    /**
     * Copies a source Working Copy path (or its repository location URL) to a destination 
     * URL immediately committing changes to a repository.
     * 
     * <p>
     * Equivalent to <code>doCopy(srcPath, srcRevision, dstURL, false, commitMessage)</code>. 
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
     * @see                   #doCopy(File, SVNRevision, SVNURL, boolean, String)
     */
    /*
    public SVNCommitInfo doCopy(File srcPath, SVNRevision srcRevision, SVNURL dstURL, String commitMessage) throws SVNException {
        return doCopy(srcPath, srcRevision, dstURL, false, commitMessage);
    }*/
    
    /**
     * Copies a source Working Copy path (or its repository location URL) to a destination 
     * URL immediately committing changes to a repository.
     * 
     * <p>
     * If <code>srcRevision</code> is not {@link SVNRevision#WORKING} then the repository
     * location URL of <code>srcPath</code> is copied to <code>dstURL</code>. Otherwise
     * <code>srcPath</code> itself.
     * 
     * <p>
     * <code>failWhenDstExists</code> behaves 
     * like in {@link #doCopy(SVNURL, SVNRevision, SVNURL, boolean, boolean, String)}. 
     * 
     * @param  srcPath          a source Working Copy path
     * @param  srcRevision      a revision of <code>srcPath</code>
     * @param  dstURL           a target URL where <code>srcPath</code> is to be
     *                          copied
     * @param failWhenDstExists <span class="javakeyword">true</span> to force a failure if 
     *                          the destination exists   
     * @param  commitMessage    a commit log message
     * @return                  information on the committed revision
     * @throws SVNException     if one of the following is true:
     *                          <ul>
     *                          <li><code>srcPath</code> is not under version control
     *                          <li><code>srcPath</code> has no URL
     *                          <li>the repository location of <code>srcPath</code> was not 
     *                          found in <code>srcRevision</code>
     *                          <li><code>dstURL</code> already exists and 
     *                          <code>failWhenDstExists</code> is <span class="javakeyword">true</span>
     *                          </ul>
     */
    /*
    public SVNCommitInfo doCopy(File srcPath, SVNRevision srcRevision, SVNURL dstURL, boolean failWhenDstExists, String commitMessage) throws SVNException {
        return doCopy(srcPath, srcRevision, dstURL, failWhenDstExists, commitMessage, null);
    }
    
    public SVNCommitInfo doCopy(File srcPath, SVNRevision srcRevision, SVNURL dstURL, boolean failWhenDstExists, String commitMessage, Map revisionProperties) throws SVNException {
        return doCopy(srcPath, srcRevision, dstURL, failWhenDstExists, false, commitMessage, revisionProperties);
    }
    
    public SVNCommitInfo doCopy(File srcPath, SVNRevision srcRevision, SVNURL dstURL, boolean failWhenDstExists, boolean makeParents, String commitMessage, Map revisionProperties) throws SVNException {
        return doCopy(new SVNCopySource[]{new SVNCopySource(SVNRevision.UNDEFINED, srcRevision, srcPath)}, dstURL, false, failWhenDstExists, makeParents, commitMessage, revisionProperties);
    }
    
    public SVNCommitInfo doCopy(SVNCopySource[] sources, SVNURL dstURL, boolean isMove, boolean failWhenDstExists, boolean makeParents, String commitMessage, Map revisionProperties) throws SVNException {
        if (sources.length > 1 && failWhenDstExists) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MULTIPLE_SOURCES_DISALLOWED);
            SVNErrorManager.error(err);
        }
        
        for (int i = 0; i < sources.length; i++) {
            SVNCopySource source = sources[i]; 
            SVNRevision pegRevision = source.getPegRevision();
            if (source.isURL() && (pegRevision == SVNRevision.COMMITTED || 
                    pegRevision == SVNRevision.BASE || pegRevision == SVNRevision.PREVIOUS)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                        "Revision type requires a working copy path, not a URL");
                SVNErrorManager.error(err);
            }
        }

        boolean srcsAreURLs = sources[0].isURL();
        if (sources.length > 1) {
            for (int i = 0; i < sources.length; i++) {
                SVNCopySource source = sources[i];
                if (srcsAreURLs != source.isURL()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                            "Cannot mix repository and working copy sources");
                    SVNErrorManager.error(err);
                }
                resolveRevisions(source);
                String name = source.getName();
                source.setDestinationURL(dstURL.appendPath(name, false));
            }            
        } else {
            SVNCopySource source = sources[0];
            resolveRevisions(source);
            source.setDestinationURL(dstURL);
        }
        
        if (isMove) {
            if (srcsAreURLs) {
                for (int i = 0; i < sources.length; i++) {
                    SVNCopySource source = sources[i];
                    if (source.getURL().equals(dstURL)) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot move URL ''{0}'' into itself", source.getURL());
                        SVNErrorManager.error(err);
                    }
                }                
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Moves between the working copy and the repository are not supported");
                SVNErrorManager.error(err);
            }
        } else {
            if (!srcsAreURLs) {
                boolean needRepoRevision = false;
                boolean needRepoPegRevision = false;
                for (int i = 0; i < sources.length; i++) {
                    SVNCopySource source = sources[i];
                    if (source.getRevision() != SVNRevision.UNDEFINED && 
                            source.getRevision() != SVNRevision.WORKING) {
                        needRepoRevision = true;
                    }
                    if (source.getPegRevision() != SVNRevision.UNDEFINED && 
                            source.getPegRevision() != SVNRevision.WORKING) {
                        needRepoPegRevision = true;
                    }
                    if (needRepoRevision || needRepoPegRevision) {
                        break;
                    }
                }
                if (needRepoRevision || needRepoPegRevision) {
                    SVNWCAccess wcAccess = createWCAccess();
                    for (int i = 0; i < sources.length; i++) {
                        SVNCopySource source = sources[i];
                        wcAccess.probeOpen(source.getPath(), false, 0); 
                        SVNEntry srcEntry = null;
                        try {
                            srcEntry = wcAccess.getVersionedEntry(source.getPath(), false);
                        } finally {
                            wcAccess.close();
                        }
                        SVNURL url = srcEntry.isCopied() ? srcEntry.getCopyFromSVNURL() : srcEntry.getSVNURL();
                        if (url == null) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                                    "''{0}'' does not have a URL associated with it", source.getPath());
                            SVNErrorManager.error(err);
                        }
                        source.setPath(null);
                        source.setURL(url);
                        if (!needRepoPegRevision) {
                            if (srcEntry.isCopied()) {
                                source.setPegRevision(SVNRevision.create(srcEntry.getCopyFromRevision()));
                            } else {
                                source.setPegRevision(SVNRevision.create(srcEntry.getRevision()));
                            }
                        }
                    }
                    srcsAreURLs = true;
                }
            }
        }
        if (srcsAreURLs) {
            return copyReposToRepos(sources, dstURL, isMove, failWhenDstExists, makeParents, commitMessage, revisionProperties);
        } 
        return copyLocalToRepos(sources, failWhenDstExists, makeParents, commitMessage, revisionProperties);
    }*/

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
    /*
    public long doCopy(SVNURL srcURL, SVNRevision srcRevision, File dstPath) throws SVNException {
        return doCopy(srcURL, SVNRevision.UNDEFINED, srcRevision, dstPath);
    }
    
    public long doCopy(SVNURL srcURL, SVNRevision pegRevision, SVNRevision srcRevision, File dstPath) throws SVNException {
        return doCopy(srcURL, pegRevision, srcRevision, dstPath, false, false);
    }
    
    public long doCopy(SVNURL srcURL, SVNRevision pegRevision, SVNRevision srcRevision, File dstPath, 
            boolean makeParents, boolean failWhenDstExists) throws SVNException {
        dstPath = dstPath.getAbsoluteFile();

        if (pegRevision == SVNRevision.BASE || pegRevision == SVNRevision.COMMITTED || pegRevision == SVNRevision.PREVIOUS) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Revision type requires a working copy path, not a URL");
            SVNErrorManager.error(err);
        } else if (pegRevision == SVNRevision.UNDEFINED) {
            pegRevision = SVNRevision.HEAD;
        }
        if (!srcRevision.isValid()) {
            srcRevision = pegRevision;
        }
        
        SVNRepositoryLocation[] locs = getLocations(srcURL, null, null, pegRevision, srcRevision, SVNRevision.UNDEFINED);
        srcURL = locs[0].getURL();

        SVNRepository repository = createRepository(srcURL, true);
    
        long srcRevisionNumber = getRevisionNumber(srcRevision, repository, null);
        SVNNodeKind srcKind = repository.checkPath("", srcRevisionNumber);
        
        if (srcKind == SVNNodeKind.NONE) {
            if (SVNRevision.isValidRevisionNumber(srcRevisionNumber)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' not found in revision {1}",
                        new Object[] {srcURL, new Long(srcRevisionNumber)});
                SVNErrorManager.error(err);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' not found in head revision", srcURL);
                SVNErrorManager.error(err);
            }
        }
        
        SVNFileType dstFileType = SVNFileType.getType(dstPath);
        if (dstFileType == SVNFileType.DIRECTORY) {
            if (failWhenDstExists) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, "Path ''{0}'' already exists", dstPath);
                SVNErrorManager.error(err);
            }
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
        
        File dstParent = dstPath.getParentFile();
        SVNFileType dstParentType = SVNFileType.getType(dstParent);
        if (dstParentType == SVNFileType.NONE && makeParents) {
            SVNWCClient wcClient = new SVNWCClient(getRepositoryPool(), getOptions());
            try {
                wcClient.doAdd(dstParent, false, true, true, false, false, true);
            } catch (SVNException svne) {
                SVNFileUtil.deleteAll(dstParent, true);
                throw svne;
            }
        } else if (dstParentType != SVNFileType.DIRECTORY) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "Path ''{0}'' is not a directory", dstParent);
            SVNErrorManager.error(err);
        }
        
        SVNWCAccess dstAccess = createWCAccess();
        long revision = -1;
        try {
            SVNAdminArea adminArea = dstAccess.probeOpen(dstPath, true, 0);
            
            SVNEntry dstEntry = dstAccess.getEntry(dstPath, false);
            if (dstEntry != null && !dstEntry.isDirectory() && !dstEntry.isScheduledForDeletion()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Entry for ''{0}'' exists (though the working copy file is missing)", dstPath);
                SVNErrorManager.error(err);
            }
            
            boolean sameRepositories;
            
            String srcUUID = null;
            String dstUUID = null;
            
            try {
                srcUUID = repository.getRepositoryUUID(true);
                dstUUID = getUUIDFromPath(dstAccess, dstPath.getParentFile());
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
            
            if (srcKind == SVNNodeKind.DIR) {
                // do checkout.
                SVNUpdateClient updateClient = new SVNUpdateClient(getRepositoryPool(), getOptions());
                updateClient.setEventHandler(getEventDispatcher());
    
                revision = updateClient.doCheckout(srcURL, dstPath, srcRevision, srcRevision, 
                        SVNDepth.INFINITY, false);
                
                if (sameRepositories) {
                    SVNAdminArea dstArea = dstAccess.open(dstPath, true, SVNWCAccess.INFINITE_DEPTH);
                    SVNEntry dstRootEntry = dstArea.getEntry(dstArea.getThisDirName(), false);
                    if (srcRevision == SVNRevision.HEAD) {
                        revision = srcRevisionNumber = dstRootEntry.getRevision();
                    }
                    SVNWCManager.add(dstPath, adminArea, srcURL, srcRevisionNumber);
                    String relPath = getPathRelativeToRoot(null, srcURL, null, null, repository);
                    Map srcMergeInfo = calculateTargetMergeInfo(null, null, srcURL, relPath, srcRevisionNumber, 
                            repository);
                    extendWCMergeInfo(dstPath, dstRootEntry, srcMergeInfo, dstArea);
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Source URL ''{0}'' is from foreign repository; leaving it as a disjoint WC", srcURL);
                    SVNErrorManager.error(err);
                }
            } else if (srcKind == SVNNodeKind.FILE) {
                Map properties = new HashMap();
                File tmpFile = null;
    
                File baseTmpFile = adminArea.getBaseFile(dstPath.getName(), true);
                tmpFile = SVNFileUtil.createUniqueFile(baseTmpFile.getParentFile(), ".copy", ".tmp");
                OutputStream os = null;
                
                long realRevision = -1;
                try {
                    os = SVNFileUtil.openFileForWriting(tmpFile);
                    realRevision = repository.getFile("", srcRevisionNumber, properties, new SVNCancellableOutputStream(os, this));
                } finally {
                    SVNFileUtil.closeFile(os);
                }
                if (!SVNRevision.isValidRevisionNumber(srcRevisionNumber)) {
                    srcRevisionNumber = realRevision;
                }
                SVNWCManager.addRepositoryFile(adminArea, dstPath.getName(), null, tmpFile, null, properties,  
                        sameRepositories ? srcURL.toString() : null, 
                        sameRepositories ? srcRevisionNumber : -1);
                
                dstEntry = dstAccess.getEntry(dstPath, false);
                String relPath = getPathRelativeToRoot(null, srcURL, null, null, repository);
                Map srcMergeInfo = calculateTargetMergeInfo(null, null, srcURL, relPath, srcRevisionNumber, 
                                                            repository);
                
                extendWCMergeInfo(dstPath, dstEntry, srcMergeInfo, adminArea);

                String mimeType = null;
                try {
                    mimeType = adminArea.getProperties(dstEntry.getName()).getPropertyValue(SVNProperty.MIME_TYPE);
                } catch (SVNException e) {
                    //
                }
                dispatchEvent(SVNEventFactory.createSVNEvent(adminArea.getFile(dstEntry.getName()), dstEntry.getKind(),mimeType, 0, SVNEventAction.ADD, null, null, null));
                revision = srcRevisionNumber;
                sleepForTimeStamp();
            }
        } finally {
            dstAccess.close();
        }
        return revision;
    }*/

    public void doCopy(SVNCopySource[] sources, File dst, boolean isMove, boolean makeParents, boolean failWhenDstExists) throws SVNException {
        if (sources.length > 1 && failWhenDstExists) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MULTIPLE_SOURCES_DISALLOWED);
            SVNErrorManager.error(err);
        }
        try {
            setupCopy(sources, new SVNPath(dst.getAbsolutePath()), isMove, makeParents);
        } catch (SVNException e) {
            SVNDebugLog.getDefaultLog().info(e);
            SVNErrorCode err = e.getErrorMessage().getErrorCode();
            if (!failWhenDstExists && sources.length == 1 && (err == SVNErrorCode.ENTRY_EXISTS || err == SVNErrorCode.FS_ALREADY_EXISTS)) {
                SVNCopySource source = sources[0];
                String baseName = source.getName();
                if (source.isURL()) {
                    baseName = SVNEncodingUtil.uriDecode(baseName);
                }
                try {
                    setupCopy(sources, new SVNPath(new File(dst, baseName).getAbsolutePath()), isMove, makeParents);
                } catch (SVNException second) {
                    SVNDebugLog.getDefaultLog().info(second);
                    throw e;
                }
                return;
            }
            throw e;
        }
    }

    public SVNCommitInfo doCopy(SVNCopySource[] sources, SVNURL dst, boolean isMove, boolean makeParents, boolean failWhenDstExists,
            String commitMessage, Map revisionProperties) throws SVNException {
        if (sources.length > 1 && failWhenDstExists) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MULTIPLE_SOURCES_DISALLOWED);
            SVNErrorManager.error(err);
        }
        try {
            return setupCopy(sources, new SVNPath(dst.toString()), isMove, makeParents);
        } catch (SVNException e) {
            SVNErrorCode err = e.getErrorMessage().getErrorCode();
            if (!failWhenDstExists && sources.length == 1 && (err == SVNErrorCode.ENTRY_EXISTS || err == SVNErrorCode.FS_ALREADY_EXISTS)) {
                SVNCopySource source = sources[0];
                String baseName = source.getName();
                if (!source.isURL()) {
                    baseName = SVNEncodingUtil.uriEncode(baseName);
                }
                try {
                    return setupCopy(sources, new SVNPath(dst.appendPath(baseName, true).toString()), isMove, makeParents);
                } catch (SVNException e1) {
                    throw e1;
                }
            }
            throw e;
        }
    }
    
    private String getUUIDFromPath(SVNWCAccess wcAccess, File path) throws SVNException {
        SVNEntry entry = wcAccess.getVersionedEntry(path, true);
        String uuid = null;
        if (entry.getUUID() != null) {
            uuid = entry.getUUID();
        } else if (entry.getURL() != null) {
            SVNRepository repos = createRepository(entry.getSVNURL(), false);
            try {
                uuid = repos.getRepositoryUUID(true);
            } finally {
                repos.closeSession();
            }
        } else {
            if (wcAccess.isWCRoot(path)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path);
                SVNErrorManager.error(err);
            }
            uuid = getUUIDFromPath(wcAccess, path.getParentFile());
        }
        return uuid;
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
     * @deprecated
     */
    /*
    public void doCopy(File srcPath, SVNRevision srcRevision, File dstPath, boolean force, boolean isMove) throws SVNException {
        doCopy(srcPath, srcRevision, isMove, false, false, false, dstPath);
    }
    */
    /*
    public void doCopy(File srcPath, SVNRevision srcRevision, boolean isMove, boolean makeParents, 
            boolean includeMergeHistory, boolean failWhenDstExists, File dstPath) throws SVNException {
        srcPath = srcPath.getAbsoluteFile();
        dstPath = dstPath.getAbsoluteFile();
        if (srcRevision.isValid() && srcRevision != SVNRevision.WORKING && !isMove) {
            // url->wc copy
            SVNWCAccess wcAccess = createWCAccess();
            SVNURL srcURL = null;
            SVNRevision pegRevision = SVNRevision.UNDEFINED;
            try {
                wcAccess.probeOpen(srcPath, false, 0);
                SVNEntry srcEntry = wcAccess.getVersionedEntry(srcPath, false);
                if (srcEntry.getURL() == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' does not seem to have a URL associated with it", srcPath);
                    SVNErrorManager.error(err);
                }
                srcURL = srcEntry.getSVNURL();
                pegRevision = SVNRevision.create(srcEntry.getRevision());
            } finally {
                wcAccess.close();
            }
            doCopy(srcURL, pegRevision, srcRevision, dstPath);
            return;
        }
        // 1. can't copy src to its own child
        if (SVNPathUtil.isAncestor(srcPath.getAbsolutePath(), dstPath.getAbsolutePath()) || srcPath.equals(dstPath)) {
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
            if (failWhenDstExists) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, "Path ''{0}'' already exists", dstPath);
                SVNErrorManager.error(err);
            }

            dstPath = new File(dstPath, srcPath.getName());
            dstType = SVNFileType.getType(dstPath);
            if (dstType != SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "''{0}'' already exists and is in the way", dstPath);
                SVNErrorManager.error(err);
            }
        } else if (dstType != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "File ''{0}'' already exists", dstPath);
            SVNErrorManager.error(err);
        }
        File dstParent = dstPath.getParentFile();
        SVNFileType dstParentType = SVNFileType.getType(dstParent);
        if (dstParentType == SVNFileType.NONE && makeParents) {
            SVNWCClient wcClient = new SVNWCClient(getRepositoryPool(), getOptions());
            try {
                wcClient.doAdd(dstParent, false, true, true, false, false, true);
            } catch (SVNException svne) {
                SVNFileUtil.deleteAll(dstParent, true);
                throw svne;
            }
        } else if (dstParentType != SVNFileType.DIRECTORY) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "Path ''{0}'' is not a directory", dstParent);
            SVNErrorManager.error(err);
        }
        SVNWCAccess wcAccess = createWCAccess();
        SVNAdminArea adminArea = null;
        File srcParent = srcPath.getParentFile();

        try {
            SVNAdminArea srcParentArea = null;
            if (isMove) {
                srcParentArea = wcAccess.open(srcParent, true, srcType == SVNFileType.DIRECTORY ? SVNWCAccess.INFINITE_DEPTH : 0);
                if (srcParent.equals(dstParent)) {
                    adminArea = srcParentArea;
                } else {
                    if (srcType == SVNFileType.DIRECTORY && SVNPathUtil.isAncestor(srcParent.getAbsolutePath(), dstParent.getAbsolutePath())) {
                        adminArea = wcAccess.retrieve(dstParent);
                    } else {
                        adminArea = wcAccess.open(dstParent, true, 0);
                    }
                }
            } else {
                adminArea = wcAccess.open(dstParent, true, 0);
            }
            
            SVNWCAccess srcAccess = createWCAccess();
            try {
                SVNAdminArea srcArea = srcAccess.probeOpen(srcPath, false, SVNWCAccess.INFINITE_DEPTH);
                SVNEntry dstEntry = adminArea.getVersionedEntry(adminArea.getThisDirName(), false);
                SVNEntry srcEntry = srcAccess.getVersionedEntry(srcPath, false);

                if (srcEntry.getRepositoryRoot() != null && dstEntry.getRepositoryRoot() != null &&
                        !srcEntry.getRepositoryRoot().equals(dstEntry.getRepositoryRoot())) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE, "Cannot copy to ''{0}'', as it is not from repository ''{1}''; it is from ''{2}''", new Object[] {adminArea.getRoot(), srcEntry.getRepositoryRoot(), dstEntry.getRepositoryRoot()});
                    SVNErrorManager.error(err);
                }
                
                if (dstEntry.isScheduledForDeletion()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE, "Cannot copy to ''{0}'' as it is scheduled for deletion", adminArea.getRoot());
                    SVNErrorManager.error(err);
                }
                
                if (srcType == SVNFileType.FILE) {
                    if (srcEntry.isScheduledForAddition() && !srcEntry.isCopied()) {
                        copyAddedFile(adminArea, srcPath, dstPath.getName(), true);
                    } else {
                        copyFile(adminArea, srcArea, srcPath, dstPath.getName());
                    }
                } else if (srcType == SVNFileType.DIRECTORY) {
                    if (srcEntry.isScheduledForAddition() && !srcEntry.isCopied()) {
                        copyAddedDir(adminArea, srcArea, srcPath, dstPath.getName(), true);
                    } else {
                        copyDir(adminArea, srcArea, srcPath, dstPath.getName());
                    }
                }
            } finally {
                srcAccess.close();
            }
            
            try {
                SVNAdminArea srcArea = null;
                if (srcParent.equals(dstParent)) {
                    if (srcType == SVNFileType.DIRECTORY) {
                        srcArea = srcAccess.open(srcPath, false, SVNWCAccess.INFINITE_DEPTH);
                    } else {
                        srcArea = adminArea; 
                    }
                } else {
                    srcArea = srcAccess.open(srcParent, false, srcType == SVNFileType.DIRECTORY ? 
                            SVNWCAccess.INFINITE_DEPTH : 0);
                }
                propagateMergeInfoWithWC(srcPath, dstPath, srcArea, adminArea, includeMergeHistory);
            } finally {
                srcAccess.close();
            }
            
            if (isMove) {
                SVNWCManager.delete(srcParentArea.getWCAccess(), srcParentArea, srcPath, true, false);
            }
        } finally {
            wcAccess.close();
        }
    }
*/
    private void copyAddedFile(SVNAdminArea dstParent, File srcPath, String dstName, boolean isAdded) throws SVNException {
        File dstPath = dstParent.getFile(dstName);
        SVNFileUtil.copyFile(srcPath, dstPath, false);
        if (isAdded) {
            SVNWCManager.add(dstPath, dstParent, null, SVNRepository.INVALID_REVISION);
        }
    }
    
    private void copyAddedDir(SVNAdminArea dstParent, SVNAdminArea srcArea, File srcPath, String dstName, boolean isAdded) throws SVNException {
        File dstPath = dstParent.getFile(dstName);
        if (!isAdded) {
            SVNFileUtil.copyDirectory(srcPath, dstPath, true, srcArea.getWCAccess());
        } else {
            checkCancelled();
            if (!dstPath.exists()) {
                dstPath.mkdirs();
            }
            
            SVNWCManager.add(dstPath, dstParent, null, SVNRepository.INVALID_REVISION);
            SVNAdminArea dstChildArea = dstParent.getWCAccess().retrieve(dstPath);
            SVNAdminArea srcChildArea = srcArea.getWCAccess().retrieve(srcPath);
            File[] entries = SVNFileListUtil.listFiles(srcPath);
            
            for (int i = 0; entries != null && i < entries.length; i++) {
                checkCancelled();
                File fsEntry = entries[i];
                String name = fsEntry.getName();
                if (SVNFileUtil.getAdminDirectoryName().equals(name)) {
                    continue;
                }
            
                SVNEntry entry = srcChildArea.getEntry(name, true);
                if (fsEntry.isDirectory()) {
                    copyAddedDir(dstChildArea, srcChildArea, fsEntry, name, entry != null);
                } else if (fsEntry.isFile()) {
                    copyAddedFile(dstChildArea, fsEntry, name, entry != null);
                }
            }
        }
    }
    
    private void copyFile(SVNAdminArea dstParent, SVNAdminArea srcArea,  File srcPath, String dstName) throws SVNException {
        SVNWCAccess wcAccess = dstParent.getWCAccess();
        File dstPath = dstParent.getFile(dstName);
        
        if (SVNFileType.getType(dstPath) != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "''{0}'' already exists and is in the way", dstPath);
            SVNErrorManager.error(err);
        }
        
        SVNEntry dstEntry = wcAccess.getEntry(dstPath, false);
        if (dstEntry != null && dstEntry.isFile()) {
            if (!dstEntry.isScheduledForDeletion()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "There is already a versioned item ''{0}''", dstPath);
                SVNErrorManager.error(err);
            }
        }
        SVNEntry srcEntry = srcArea.getWCAccess().getVersionedEntry(srcPath, false);
        if ((srcEntry.isScheduledForAddition() && !srcEntry.isCopied()) || srcEntry.getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot copy or move ''{0}'': it''s not in repository yet; try committing first", srcPath);
            SVNErrorManager.error(err);
        }
        
        String copyFromURL = null;
        long copyFromRevision = SVNRepository.INVALID_REVISION;
        if (srcEntry.isCopied()) {
            if (srcEntry.getCopyFromURL() != null) {
                copyFromURL = srcEntry.getCopyFromURL();
                copyFromRevision = srcEntry.getCopyFromRevision();
            } else {
                SVNLocationEntry copyFromEntry = getCopyFromInfoFromParent(srcPath, srcArea);
                copyFromURL = copyFromEntry.getPath();
                copyFromRevision = copyFromEntry.getRevision();
            }
            if (dstEntry != null && copyFromRevision == dstEntry.getRevision() &&
                dstEntry.getURL().equals(copyFromURL)) {
                copyFromURL = null;
                copyFromRevision = SVNRepository.INVALID_REVISION;
            }
        } else {
            copyFromURL = srcEntry.getURL();
            copyFromRevision = srcEntry.getRevision();
        }
        
        File textBase = srcArea.getBaseFile(srcPath.getName(), false);
        File tmpTextBase = dstParent.getBaseFile(dstName, true);
        
        Map baseProperties = srcArea.getBaseProperties(srcEntry.getName()).asMap();
        Map properties = srcArea.getProperties(srcEntry.getName()).asMap();
        
        SVNFileUtil.copyFile(textBase, tmpTextBase, false);
        File tmpFile = SVNFileUtil.createUniqueFile(dstParent.getRoot(), ".copy", ".tmp");
        SVNFileUtil.copy(srcArea.getFile(srcEntry.getName()), tmpFile, false, false);
       
        SVNWCManager.addRepositoryFile(dstParent, dstName, tmpFile, tmpTextBase, baseProperties, properties, copyFromURL, copyFromRevision);
    
        SVNEvent event = SVNEventFactory.createSVNEvent(dstParent.getFile(dstName), SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNEventAction.ADD, null, null, null);
        dstParent.getWCAccess().handleEvent(event);

    }

    private void copyDir(SVNAdminArea dstParent, SVNAdminArea srcArea, File srcPath, String dstName) throws SVNException {
        SVNEntry srcEntry = srcArea.getWCAccess().getVersionedEntry(srcPath, false);
        if ((srcEntry.isScheduledForAddition() && !srcEntry.isCopied()) || srcEntry.getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot copy or move ''{0}'': it''s not in repository yet; try committing first", srcPath);
            SVNErrorManager.error(err);
        }
        
        File dstPath = dstParent.getFile(dstName);
        SVNFileUtil.copyDirectory(srcPath, dstPath, true, srcArea.getWCAccess());
        
        SVNWCClient wcClient = new SVNWCClient((ISVNAuthenticationManager) null, (ISVNOptions) null);
        wcClient.doCleanup(dstPath);
        
        SVNWCAccess access = SVNWCAccess.newInstance(null);
        String copyFromURL = null;
        long copyFromRevision = SVNRepository.INVALID_REVISION;
        try {
            SVNAdminArea tmpDir = access.open(dstPath, true, SVNWCAccess.INFINITE_DEPTH);
            postCopyCleanup(tmpDir);
            if (srcEntry.isCopied()) {
                SVNEntry dstEntry = access.getEntry(dstPath, false);
                if (srcEntry.getCopyFromURL() != null) {
                    copyFromURL = srcEntry.getCopyFromURL();
                    copyFromRevision = srcEntry.getCopyFromRevision();
                } else {
                    SVNLocationEntry copyFromEntry = getCopyFromInfoFromParent(srcPath, srcArea);
                    copyFromURL = copyFromEntry.getPath();
                    copyFromRevision = copyFromEntry.getRevision();
                }
                if (dstEntry != null && copyFromRevision == dstEntry.getRevision() &&
                    dstEntry.getURL().equals(copyFromURL)) {
                    copyFromURL = null;
                    copyFromRevision = SVNRepository.INVALID_REVISION;
                }

                Map attributes = new HashMap();
                attributes.put(SVNProperty.URL, copyFromURL);
                tmpDir.modifyEntry(tmpDir.getThisDirName(), attributes, true, false);
            } else {
                copyFromURL = srcEntry.getURL();
                copyFromRevision = srcEntry.getRevision();
            }
        } finally {
            access.close();
        }
        SVNWCManager.add(dstPath, dstParent, SVNURL.parseURIEncoded(copyFromURL), copyFromRevision);
    }

    static void postCopyCleanup(SVNAdminArea dir) throws SVNException {
        SVNPropertiesManager.deleteWCProperties(dir, null, false);
        SVNFileUtil.setHidden(dir.getAdminDirectory(), true);
        Map attributes = new HashMap(); 
        boolean save = false;
        
        for(Iterator entries = dir.entries(true); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            boolean deleted = entry.isDeleted();
            SVNNodeKind kind = entry.getKind();
            boolean force = false;
            
            if (entry.isDeleted()) {
                force = true;
                attributes.put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_DELETE);
                attributes.put(SVNProperty.DELETED, null);
                if (entry.isDirectory()) {
                    attributes.put(SVNProperty.KIND, SVNProperty.KIND_FILE);
                }
            }
            if (entry.getLockToken() != null) {
                force = true;
                attributes.put(SVNProperty.LOCK_TOKEN, null);
                attributes.put(SVNProperty.LOCK_OWNER, null);
                attributes.put(SVNProperty.LOCK_CREATION_DATE, null);
            }
            if (force) {
                dir.modifyEntry(entry.getName(), attributes, false, force);
                save = true;
            }
            if (!deleted && kind == SVNNodeKind.DIR && !dir.getThisDirName().equals(entry.getName())) {
                SVNAdminArea childDir = dir.getWCAccess().retrieve(dir.getFile(entry.getName()));
                postCopyCleanup(childDir);
            }
            
            attributes.clear();
        }
        
        if (save) {
            dir.saveEntries(false);
        }
    }
/*
    private SVNCommitInfo copyLocalToRepos(SVNCopySource[] sources, boolean failWhenDstExists, boolean makeParents, String commitMessage, Map revisionProperties) throws SVNException {
        String topSrcPath = sources[0].getPath().getAbsolutePath();
        for (int i = 1; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            topSrcPath = SVNPathUtil.getCommonPathAncestor(topSrcPath, source.getPath().getAbsolutePath());
        }
        File topSrcFile = new File(topSrcPath);
        
        SVNWCAccess wcAccess = createWCAccess();
        SVNAdminArea adminArea = wcAccess.probeOpen(topSrcFile, false, SVNWCAccess.INFINITE_DEPTH);
        wcAccess.setAnchor(adminArea.getRoot());
        
        SVNURL topDstURL = sources[0].getDstURL().removePathTail();
        for (int i = 1; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            topDstURL = SVNURLUtil.getCommonURLAncestor(topDstURL, source.getDstURL());
        }        

        SVNRepository repository = createRepository(topDstURL, true);
        List newDirs = null; 
        if (makeParents) {
            SVNURL rootURL = topDstURL;
            newDirs = new LinkedList();
            SVNNodeKind kind = repository.checkPath("", SVNRepository.INVALID_REVISION);
            while (kind == SVNNodeKind.NONE) {
                newDirs.add(rootURL);
                rootURL = rootURL.removePathTail();
                repository = createRepository(rootURL, true);
                kind = repository.checkPath("", SVNRepository.INVALID_REVISION);
            }
            topDstURL = rootURL;
        }

        for (int i = 0; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            SVNEntry entry = wcAccess.getVersionedEntry(source.getPath(), false);
            SVNURL srcURL = entry.getSVNURL();
            SVNURL reposRoot = entry.getRepositoryRootURL();
            if (reposRoot == null) {
                reposRoot = repository.getRepositoryRoot(true);
            }
            String srcPath = SVNPathUtil.getPathAsChild(reposRoot.getPath(), srcURL.getPath());
            if (srcPath == null || "".equals(srcPath)) {
                srcPath = "/";
            } else {
                srcPath = !srcPath.startsWith("/") ? "/" + srcPath : srcPath;    
            }
            source.setSrcPath(srcPath);
            source.setSrcRevisionNumber(entry.getRevision());
            String dstPath = SVNPathUtil.getPathAsChild(topDstURL.getPath(), source.getDstURL().getPath());
            SVNNodeKind dstKind = repository.checkPath(dstPath, -1);
            if (dstKind == SVNNodeKind.DIR) {
                if (failWhenDstExists) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, "Path ''{0}'' already exists", source.getDstURL());
                    SVNErrorManager.error(err);
                }
                source.setDestinationURL(source.getDstURL().appendPath(source.getPath().getName(), false));
            } else if (dstKind == SVNNodeKind.FILE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, "File ''{0}'' already exists", source.getDstURL());
                SVNErrorManager.error(err);
            }

            source.setDstPath(dstPath);
        }
        
        Collection commitItems = new LinkedList();

        if (makeParents) {
            for (Iterator urls = newDirs.iterator(); urls.hasNext();) {
                SVNURL newURL = (SVNURL) urls.next();
                commitItems.add(new SVNCommitItem(null, newURL, null, 
                        SVNNodeKind.DIR, SVNRevision.UNDEFINED, SVNRevision.UNDEFINED, 
                        true, false, false, false, false, false));

            }
        }
        for (int i = 0; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            SVNCommitItem item = new SVNCommitItem(null, source.getDstURL(), null, 
                        SVNNodeKind.NONE, SVNRevision.UNDEFINED, SVNRevision.UNDEFINED, 
                        true, false, false, false, true, false);
            item.setWCAccess(adminArea.getWCAccess());
            commitItems.add(item);
        }
        
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, (SVNCommitItem[]) commitItems.toArray(new SVNCommitItem[commitItems.size()]));
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }

        Collection tmpFiles = null;
        SVNCommitInfo info = null;
        ISVNEditor commitEditor = null;
        try {
            Map commitables = new TreeMap();
            for (int i = 0; i < sources.length; i++) {
                SVNCopySource source = sources[i];
                SVNEntry entry = wcAccess.getVersionedEntry(source.getPath(), false);
                SVNAdminArea dirArea = null;
                if (entry.isDirectory()) {
                    dirArea = wcAccess.retrieve(source.getPath());
                } else {
                    dirArea = wcAccess.retrieve(source.getPath().getParentFile());
                }
                SVNCommitUtil.harvestCommitables(commitables, dirArea, source.getPath(), 
                                                 null, entry, source.getDstURL().toString(), 
                                                 entry.getURL(), true, false, false, null, 
                                                 SVNDepth.INFINITY, false, null, getCommitParameters());
                
                SVNCommitItem item = (SVNCommitItem) commitables.get(source.getPath());
                SVNURL srcURL = entry.getSVNURL();
                Map mergeInfo = calculateTargetMergeInfo(source.getPath(), wcAccess, srcURL,  
                        source.getSrcPath(), source.getSrcRevisionNumber(), repository);
                
                Map wcMergeInfo = SVNPropertiesManager.parseMergeInfo(source.getPath(), entry, false);
                if (wcMergeInfo != null) {
                    mergeInfo = SVNMergeInfoManager.mergeMergeInfos(mergeInfo, wcMergeInfo);
                }
                
                String mergeInfoString = SVNMergeInfoManager.formatMergeInfoToString(mergeInfo); 
                item.setMergeInfoProp(mergeInfoString);
            }
            
            Collection cmtItems = new LinkedList(commitables.values());
            if (makeParents) {
                for (Iterator urls = newDirs.iterator(); urls.hasNext();) {
                    SVNURL newURL = (SVNURL) urls.next();
                    cmtItems.add(new SVNCommitItem(null, newURL, null, 
                            SVNNodeKind.DIR, SVNRevision.UNDEFINED, SVNRevision.UNDEFINED, 
                            true, false, false, false, false, false));

                }
            }
            for (Iterator iter = cmtItems.iterator(); iter.hasNext();) {
                SVNCommitItem item = (SVNCommitItem) iter.next();
                item.setWCAccess(wcAccess);
            }

            SVNCommitItem[] items = (SVNCommitItem[]) cmtItems.toArray(new SVNCommitItem[cmtItems.size()]);
            
            commitables = new TreeMap();
            topDstURL = SVNCommitUtil.translateCommitables(items, commitables);

            repository = createRepository(topDstURL, true);
            SVNCommitMediator mediator = new SVNCommitMediator(commitables);
            tmpFiles = mediator.getTmpFiles();

            commitMessage = SVNCommitClient.validateCommitMessage(commitMessage);
            SVNURL rootURL = repository.getRepositoryRoot(true);
            commitEditor = repository.getCommitEditor(commitMessage, null, true, revisionProperties, mediator);
            info = SVNCommitter.commit(tmpFiles, commitables, rootURL.getPath(), commitEditor);
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
                wcAccess.close();
            }
        }
        if (info != null && info.getNewRevision() >= 0) { 
            dispatchEvent(SVNEventFactory.createSVNEvent(null, SVNNodeKind.NONE, null, info.getNewRevision(), SVNEventAction.COMMIT_COMPLETED, null, null, null), ISVNEventHandler.UNKNOWN);
        }
        return info != null ? info : SVNCommitInfo.NULL;
    }
    
    private SVNCommitInfo copyReposToRepos(SVNCopySource[] sources, SVNURL dstURL, boolean isMove, boolean failWhenDstExists, boolean makeParents, String commitMessage, Map revisionProperties) throws SVNException {
        SVNURL topSrcURL = sources[0].getURL();
        for (int i = 1; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            topSrcURL = SVNURLUtil.getCommonURLAncestor(topSrcURL, source.getURL());
        }
        SVNURL topURL = SVNURLUtil.getCommonURLAncestor(topSrcURL, dstURL);
        if (topURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Source and dest appear not to be in the same repository (src: ''{0}''; dst: ''{1}'')", new Object[] {sources[0].getURL(), dstURL});
            SVNErrorManager.error(err);
        }
        for (int i = 0; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            source.setRessurection(true);
            if (source.getURL().equals(source.getDstURL())) {
                topURL = source.getURL().removePathTail();
            }
        }
        
        SVNRepository repository = createRepository(topURL, false);
        
        List newDirs = null;
        if (makeParents) {
            newDirs = new LinkedList();
            SVNCopySource source = sources[0];
            SVNURL dst = source.getDstURL().removePathTail();
            String dir = SVNPathUtil.getPathAsChild(topURL.getPath(), dst.getPath());
            SVNNodeKind kind = repository.checkPath(dir, SVNRepository.INVALID_REVISION);
            while (kind == SVNNodeKind.NONE) {
                newDirs.add(dir);
                dir = SVNPathUtil.removeTail(dir);
                kind = repository.checkPath(dir, SVNRepository.INVALID_REVISION);
            }
        }
        
        SVNURL repositoryRoot = repository.getRepositoryRoot(true);
        for (int i = 0; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            if (!source.getDstURL().equals(repositoryRoot) && source.getURL().getPath().startsWith(source.getDstURL().getPath() + "/")) {
                source.setRessurection(true);
                topURL = topURL.removePathTail();
                repository = createRepository(topURL, false);
            }
        }        

        long latestRevision = repository.getLatestRevision();
        for (int i = 0; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            long srcRevNumber = getRevisionNumber(source.getRevision(), repository, null);
            if (srcRevNumber < 0) {
                srcRevNumber = latestRevision;
            }
            source.setSrcRevisionNumber(srcRevNumber);

            SVNRepositoryLocation[] locs = getLocations(source.getURL(), null, null, source.getPegRevision(), source.getRevision(), SVNRevision.UNDEFINED);
            source.setURL(locs[0].getURL());

            // substring one more char, because path always starts with /, and we need relative path.
            String srcPath = SVNPathUtil.getPathAsChild(topURL.getURIEncodedPath(), source.getURL().getURIEncodedPath()); 
            srcPath = srcPath == null ? "" : SVNEncodingUtil.uriDecode(srcPath);
            String dstPath = SVNPathUtil.getPathAsChild(topURL.getPath(), source.getDstURL().getPath());
            dstPath = dstPath == null ? "" : SVNEncodingUtil.uriDecode(dstPath);
            
            if ("".equals(srcPath) && isMove) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot move URL ''{0}'' into itself", source.getURL());
                SVNErrorManager.error(err);
            }
    
            SVNNodeKind srcKind = repository.checkPath(srcPath, srcRevNumber);
            if (srcKind == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' does not exist in revision {1}", new Object[] {source.getURL(), new Long(srcRevNumber)});
                SVNErrorManager.error(err);
            }
            source.setSrcKind(srcKind);
            SVNNodeKind dstKind = repository.checkPath(dstPath, latestRevision);
            if (dstKind == SVNNodeKind.DIR) {
                if (failWhenDstExists) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, "Path ''{0}'' already exists", dstPath);
                    SVNErrorManager.error(err);
                }
                dstPath = SVNPathUtil.append(dstPath, SVNPathUtil.tail(source.getURL().getPath()));
                if (repository.checkPath(dstPath, latestRevision) != SVNNodeKind.NONE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, "Path ''{0}'' already exists", dstPath);
                    SVNErrorManager.error(err);
                }
            } else if (dstKind == SVNNodeKind.FILE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, "Path ''{0}'' already exists", dstPath);
                SVNErrorManager.error(err);
            }
            source.setSrcPath(srcPath);
            source.setDstPath(dstPath);
        }
        
        Collection commitItems = new LinkedList();
        Map actionHash = new HashMap();
        if (makeParents) {
            for (Iterator dirs = newDirs.iterator(); dirs.hasNext();) {
                String dir = (String) dirs.next();
                commitItems.add(new SVNCommitItem(null, topURL.appendPath(dir, false), null, 
                        SVNNodeKind.DIR, SVNRevision.UNDEFINED, SVNRevision.UNDEFINED, 
                        true, false, false, false, false, false));
                
            }
        }
        for (int i = 0; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            commitItems.add(new SVNCommitItem(null, source.getDstURL(), source.getURL(), 
                    source.getSrcKind(), SVNRevision.UNDEFINED, SVNRevision.create(source.getSrcRevisionNumber()), 
                    true, false, false, false, true, false));
            
            PathDriverInfo info = new PathDriverInfo();
            info.isResurrection = source.isRessurection();
            info.mySrcKind = source.getSrcKind();
            info.mySrcPath = source.getSrcPath();
            info.mySrcRevisionNumber = source.getSrcRevisionNumber();
            info.isDirAdded = false;
            actionHash.put(source.getDstPath(), info);
            if (isMove && !source.isRessurection()) {
                commitItems.add(new SVNCommitItem(null, source.getURL(), null, 
                        source.getSrcKind(), SVNRevision.create(source.getSrcRevisionNumber()), 
                        SVNRevision.UNDEFINED, false, true, false, false, false, false));
                actionHash.put(source.getSrcPath(), info);                
            }
        }        
        
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, (SVNCommitItem[]) commitItems.toArray(new SVNCommitItem[commitItems.size()]));
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }

        Collection paths = new LinkedList();
        if (makeParents) {
            for (Iterator dirs = newDirs.iterator(); dirs.hasNext();) {
                String dir = (String) dirs.next();
                PathDriverInfo info = new PathDriverInfo();
                info.myDstPath = dir;
                info.isDirAdded = true;
                actionHash.put(dir, info);                
                paths.add(dir);
            }
        }        

        commitMessage = SVNCommitClient.validateCommitMessage(commitMessage);
        for (int i = 0; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            PathDriverInfo info = (PathDriverInfo) actionHash.get(source.getDstPath());
            if (info != null && !info.isDirAdded) {
                Map mergeInfo = calculateTargetMergeInfo(null, null, source.getURL(), info.mySrcPath, 
                        info.mySrcRevisionNumber, repository);
                info.myMergeInfoProp = SVNMergeInfoManager.formatMergeInfoToString(mergeInfo);
            }
            paths.add(source.getDstPath());
            if (isMove && !source.isRessurection()) {
                paths.add(source.getSrcPath());
            }
        }
        
        
        ISVNEditor commitEditor = repository.getCommitEditor(commitMessage, null, true, revisionProperties, null);
        ISVNCommitPathHandler committer = new CopyCommitPathHandler2(actionHash, isMove);

        SVNCommitInfo result = null;
        try {
            SVNCommitUtil.driveCommitEditor(committer, paths, commitEditor, latestRevision);
            result = commitEditor.closeEdit();
        } catch (SVNException e) {
            try {
                commitEditor.abortEdit();
            } catch (SVNException inner) {
                //
            }
            SVNErrorMessage err = e.getErrorMessage().wrap("Commit failed (details follow):");
            SVNErrorManager.error(err);
        }
        if (result != null && result.getNewRevision() >= 0) { 
            dispatchEvent(SVNEventFactory.createSVNEvent(null, SVNNodeKind.NONE, null, result.getNewRevision(), SVNEventAction.COMMIT_COMPLETED, null, null, null), ISVNEventHandler.UNKNOWN);
        }
        return result != null ? result : SVNCommitInfo.NULL;
    }
  */  
    private SVNCommitInfo setupCopy(SVNCopySource[] sources, SVNPath dst, boolean isMove, boolean makeParents) throws SVNException {
        List pairs = new ArrayList(sources.length);
        for (int i = 0; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            if (source.isURL() && 
                 (source.getPegRevision() == SVNRevision.BASE ||
                  source.getPegRevision() == SVNRevision.COMMITTED ||
                  source.getPegRevision() == SVNRevision.PREVIOUS)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Revision type requires a working copy path, not URL");
                SVNErrorManager.error(err);
            }
        }
        boolean srcIsURL = sources[0].isURL();
        boolean dstIsURL = dst.isURL();
        
        if (sources.length > 1) {
            for (int i = 0; i < sources.length; i++) {
                SVNCopySource source = sources[i];
                CopyPair pair = new CopyPair();
                pair.mySource = source;
                pair.setSourceRevisions(source.getPegRevision(), source.getRevision());
                if (pair.mySource.isURL() != srcIsURL) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot mix repository and working copy sources");
                    SVNErrorManager.error(err);
                }
                String baseName = source.getName();
                if (srcIsURL && !dstIsURL) {
                    baseName = SVNEncodingUtil.uriDecode(baseName);
                }
                pair.myDst = dst.append(baseName);
                pairs.add(pair);
            }
        } else {
            SVNCopySource source = sources[0];
            CopyPair pair = new CopyPair();
            pair.mySource = source;
            pair.setSourceRevisions(source.getPegRevision(), source.getRevision());
            pair.myDst = dst;
            pairs.add(pair);
        }
        if (!srcIsURL && !dstIsURL) {
            for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                CopyPair pair = (CopyPair) ps.next();
                String srcPath = pair.mySource.getPath().getAbsolutePath();
                String dstPath = pair.myDst.getFile().getAbsolutePath();
                if (SVNPathUtil.isAncestor(srcPath.replace(File.separatorChar, '/'), dstPath.replace(File.separatorChar, '/'))) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot copy path ''{0}'' to its own child ''{1}",
                            new Object[] {srcPath, dstPath});
                    SVNErrorManager.error(err);
                }
            }
        }
        if (isMove) {
            if (srcIsURL == dstIsURL) {
                for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                    CopyPair pair = (CopyPair) ps.next();
                    File srcPath = pair.mySource.getPath();
                    File dstPath = pair.myDst.getFile();
                    if (srcPath.equals(dstPath)) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot move path ''{0}'' into itself",
                                srcPath);
                        SVNErrorManager.error(err);
                    }
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Moves between the working copy and the repository are not supported");
                SVNErrorManager.error(err);
            }
        } else {
            if (!srcIsURL) {
                boolean needReposRevision = false;
                boolean needReposPegRevision = false;
                for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                    CopyPair pair = (CopyPair) ps.next();
                    if (pair.mySourceRevision != SVNRevision.UNDEFINED && pair.mySourceRevision != SVNRevision.WORKING) {
                        needReposRevision = true;
                    }
                    if (pair.mySourcePegRevision != SVNRevision.UNDEFINED && pair.mySourcePegRevision != SVNRevision.WORKING) {
                        needReposPegRevision = true;
                    }
                    if (needReposRevision || needReposPegRevision) {
                        break;
                    }
                }
                if (needReposRevision || needReposPegRevision) {
                    for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                        CopyPair pair = (CopyPair) ps.next();
                        SVNWCAccess wcAccess = createWCAccess();
                        try {
                            wcAccess.probeOpen(pair.mySource.getPath(), false, 0);
                            SVNEntry entry = wcAccess.getEntry(pair.mySource.getPath(), false);
                            SVNURL url = entry.isCopied() ? entry.getCopyFromSVNURL() : entry.getSVNURL();
                            if (url == null) {
                                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' does not have a URL associated with it", 
                                        pair.mySource.getPath());
                                SVNErrorManager.error(err);
                            }
                            pair.mySource = new SVNCopySource(pair.mySource.getRevision(), pair.mySource.getPegRevision(), url);
                            if (!needReposPegRevision || pair.mySourcePegRevision == SVNRevision.BASE) {
                                pair.mySourcePegRevision = entry.isCopied() ? SVNRevision.create(entry.getCopyFromRevision()) : SVNRevision.create(entry.getRevision()); 
                            }
                            if (pair.mySourceRevision == SVNRevision.BASE) {
                                pair.mySourceRevision = entry.isCopied() ? SVNRevision.create(entry.getCopyFromRevision()) : SVNRevision.create(entry.getRevision()); 
                            }
                        } finally {
                            wcAccess.close();
                        }
                    }
                    srcIsURL = true;
                }
            }
        }
        if (!srcIsURL && !dstIsURL) {
            copyWCToWC(pairs, isMove, makeParents);
            return SVNCommitInfo.NULL;
        } else if (!srcIsURL && dstIsURL) {
            //wc2url.
        } else if (srcIsURL && !dstIsURL) {
            // url2wc.
            copyReposToWC(pairs, makeParents);
            return SVNCommitInfo.NULL;
        } else {
            return copyReposToRepos(pairs, makeParents, isMove);
        }
        return SVNCommitInfo.NULL;
    }
    
    private SVNCommitInfo copyReposToRepos(List copyPairs, boolean makeParents, boolean isMove) throws SVNException {
        List pathInfos = new ArrayList();
        for (int i = 0; i < copyPairs.size(); i++) {
            CopyPathInfo info = new CopyPathInfo();
            pathInfos.add(info);
        }
        SVNURL topURL = ((CopyPair) copyPairs.get(0)).mySource.getURL();
        for (int i = 1; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            topURL = SVNURLUtil.getCommonURLAncestor(topURL, pair.mySource.getURL());
        }
        if (topURL == null) {
            // TODO error on different repositories.
        }
        for (int i = 0; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            CopyPathInfo info = (CopyPathInfo) pathInfos.get(i);
            if (pair.mySource.getURL().equals(pair.myDst.getURL())) {
                info.isResurrection = true;
                if (topURL.equals(pair.mySource.getURL())) {
                    topURL = topURL.removePathTail();
                }
            }
        }
        SVNRepository topRepos = createRepository(topURL, true);
        List newDirs = new ArrayList();
        if (makeParents) {
            CopyPair pair = (CopyPair) copyPairs.get(0);
            SVNURL dstURL = pair.myDst.getURL();
            String relativeDir = dstURL.getPath().substring(topURL.getPath().length());
            if (relativeDir.startsWith("/")) {
                relativeDir = relativeDir.substring(1);
            }
            SVNNodeKind kind = topRepos.checkPath(relativeDir, -1);
            while(kind == SVNNodeKind.NONE) {
                newDirs.add(relativeDir);
                relativeDir = SVNPathUtil.removeTail(relativeDir);
                kind = topRepos.checkPath(relativeDir, -1);
            }
        }
        SVNURL rootURL = topRepos.getRepositoryRoot(true);
        for (int i = 0; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            CopyPathInfo info = (CopyPathInfo) pathInfos.get(i);
            if (!pair.myDst.getURL().equals(rootURL) &&
                    SVNPathUtil.getPathAsChild(pair.myDst.getURL().getPath(), pair.mySource.getURL().getPath()) != null) {
                info.isResurrection = true;
                // TODO looks like a bug.
                topURL = topURL.removePathTail();
            }
        }
        topRepos.setLocation(topURL, false);
        long latestRevision = topRepos.getLatestRevision();
        
        for (int i = 0; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            CopyPathInfo info = (CopyPathInfo) pathInfos.get(i);
            pair.mySourceRevisionNumber = getRevisionNumber(pair.mySourceRevision, topRepos, null);
            info.mySrcRevisionNumber = pair.mySourceRevisionNumber;
            
            SVNRepositoryLocation[] locations = getLocations(pair.mySource.getURL(), null, topRepos, pair.mySourcePegRevision, pair.mySourceRevision, SVNRevision.UNDEFINED);
        }
        
        return null;
    }
    
    private void copyReposToWC(List copyPairs, boolean makeParents) throws SVNException {
        for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
            CopyPair pair = (CopyPair) pairs.next();
            SVNRepositoryLocation[] locations = getLocations(pair.mySource.getURL(), null, null, pair.mySourcePegRevision, pair.mySourceRevision, SVNRevision.UNDEFINED);
            // new
            SVNURL actualURL = locations[0].getURL();
            SVNCopySource originalSource = pair.mySource;
            SVNCopySource actualSource = new SVNCopySource(originalSource.getPegRevision(), originalSource.getRevision(), actualURL);
            pair.mySource = actualSource;
            pair.myOriginalSource = originalSource;
        }
        // get src and dst ancestors.
        File topDst = ((CopyPair) copyPairs.get(0)).myDst.getFile();
        if (copyPairs.size() > 1) {
            topDst = topDst.getParentFile();
        }
        SVNURL topSrc = ((CopyPair) copyPairs.get(0)).mySource.getURL();
        for(int i = 1; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            topSrc = SVNURLUtil.getCommonURLAncestor(topSrc, pair.mySource.getURL());
        }
        if (copyPairs.size() == 1) {
            topSrc = topSrc.removePathTail();
        }
        SVNRepository topSrcRepos = createRepository(topSrc, false);
        try {
            for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
                CopyPair pair = (CopyPair) pairs.next();
                pair.mySourceRevisionNumber = getRevisionNumber(pair.mySourceRevision, topSrcRepos, null);
            }
            String reposPath = topSrcRepos.getLocation().getPath(); 
            for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
                CopyPair pair = (CopyPair) pairs.next();
                String relativePath = pair.mySource.getURL().getPath();
                relativePath = relativePath.substring(reposPath.length());
                if (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }
                SVNNodeKind kind = topSrcRepos.checkPath(relativePath, pair.mySourceRevisionNumber);
                if (kind == SVNNodeKind.NONE) {
                    SVNErrorMessage err = null;
                    if (pair.mySourceRevisionNumber >= 0) {
                        err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' not found in revision {1}",
                                new Object[] {pair.mySource.getURL(), new Long(pair.mySourceRevisionNumber)});
                    } else {
                        err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' not found in head revision", pair.mySource.getURL());
                    }
                    SVNErrorManager.error(err);
                }
                pair.mySourceKind = kind;
                SVNFileType dstType = SVNFileType.getType(pair.myDst.getFile());
                if (dstType != SVNFileType.NONE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "Path ''{0}'' already exists", pair.myDst.getFile());
                    SVNErrorManager.error(err);
                }
                File dstParent = pair.myDst.getFile().getParentFile();
                SVNFileType dstParentFileType = SVNFileType.getType(dstParent);
                if (makeParents && dstParentFileType == SVNFileType.NONE) {
                    // create parents.
                    addLocalParents(dstParent, getEventDispatcher());
                } else if (dstParentFileType != SVNFileType.DIRECTORY) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "Path ''{0}'' is not a directory", dstParent);
                    SVNErrorManager.error(err);
                }
            }
            SVNWCAccess dstAccess = createWCAccess();
            try {
                dstAccess.probeOpen(topDst, true, 0);
                for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
                    CopyPair pair = (CopyPair) pairs.next();
                    SVNEntry dstEntry = dstAccess.getEntry(pair.myDst.getFile(), false);
                    if (dstEntry != null && !dstEntry.isDirectory() && !dstEntry.isScheduledForDeletion()) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                                "Entry for ''{0}'' exists (though the working file is missing)", pair.myDst.getFile()); 
                        SVNErrorManager.error(err);
                    }
                }
                String srcUUID = null;
                String dstUUID = null;
                try {
                    srcUUID = topSrcRepos.getRepositoryUUID(true);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.RA_NO_REPOS_UUID) {
                        throw e;
                    }
                }
                File dstParent = topDst;
                if (copyPairs.size() == 1) {
                    dstParent = topDst.getParentFile();
                }
                try {
                    dstUUID = getUUIDFromPath(dstAccess, dstParent);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.RA_NO_REPOS_UUID) {
                        throw e;
                    }
                }
                boolean sameRepos = false;
                if (srcUUID != null) {
                    sameRepos = srcUUID.equals(dstUUID);
                }
                for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
                    CopyPair pair = (CopyPair) pairs.next();
                    copyReposToWC(pair, sameRepos, topSrcRepos, dstAccess);
                }
            } finally {
                dstAccess.close();
            }
        } finally {
            topSrcRepos.closeSession();
        }
        
    }
    
    private void copyReposToWC(CopyPair pair, boolean sameRepositories, SVNRepository topSrcRepos, SVNWCAccess dstAccess) throws SVNException {
        long srcRevNum = pair.mySourceRevisionNumber;
        SVNURL srcURL = pair.myOriginalSource.getURL();
        
        if (pair.mySourceKind == SVNNodeKind.DIR) {
            // do checkout
            SVNUpdateClient updateClient = new SVNUpdateClient(getRepositoryPool(), getOptions());
            updateClient.setEventHandler(getEventDispatcher());

            File dstPath = pair.myDst.getFile();
            SVNRevision srcRevision = pair.mySourceRevision;
            SVNRevision srcPegRevision = pair.mySourcePegRevision;
            updateClient.doCheckout(srcURL, dstPath, srcPegRevision, srcRevision, SVNDepth.INFINITY, false);
            
            if (sameRepositories) {
                SVNAdminArea dstArea = dstAccess.open(dstPath, true, SVNWCAccess.INFINITE_DEPTH);
                SVNEntry dstRootEntry = dstArea.getEntry(dstArea.getThisDirName(), false);
                if (srcRevision == SVNRevision.HEAD) {
                    srcRevNum = dstRootEntry.getRevision();
                }
                SVNAdminArea dir = dstAccess.getAdminArea(dstPath.getParentFile());
                SVNWCManager.add(dstPath, dir, srcURL, srcRevNum);
                Map srcMergeInfo = calculateTargetMergeInfo(null, null, srcURL, srcRevNum, topSrcRepos);
                extendWCMergeInfo(dstPath, dstRootEntry, srcMergeInfo, dstAccess);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Source URL ''{0}'' is from foreign repository; leaving it as a disjoint WC", srcURL);
                SVNErrorManager.error(err);
            }
        } else if (pair.mySourceKind == SVNNodeKind.FILE) {
            File dst = pair.myDst.getFile();
            SVNAdminArea dir = dstAccess.getAdminArea(dst.getParentFile());
            File tmpFile = SVNAdminUtil.createTmpFile(dir);
            String path = getPathRelativeToRoot(null, srcURL, null, null, topSrcRepos);
            Map props = new HashMap();
            OutputStream os = null;
            long revision = -1;
            try {
                os = SVNFileUtil.openFileForWriting(tmpFile);
                revision = topSrcRepos.getFile(path, srcRevNum, props, new SVNCancellableOutputStream(os, this));
            } finally {
                SVNFileUtil.closeFile(os);
            }
            if (srcRevNum < 0) {
                srcRevNum = revision;
            }
            SVNWCManager.addRepositoryFile(dir, dst.getName(), null, tmpFile, null, props, 
                    sameRepositories ? pair.mySource.getURL().toString() : null, 
                    sameRepositories ? srcRevNum : -1);

            SVNEntry entry = dstAccess.getEntry(dst, false);
            Map mergeInfo = calculateTargetMergeInfo(null, null, srcURL, srcRevNum, topSrcRepos);
            extendWCMergeInfo(dst, entry, mergeInfo, dstAccess);

            SVNEvent event = SVNEventFactory.createSVNEvent(dst, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNEventAction.ADD, null, null, null);
            dstAccess.handleEvent(event);

            sleepForTimeStamp();
        }
    }

    private void copyWCToWC(List copyPairs, boolean isMove, boolean makeParents) throws SVNException {
        for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
            CopyPair pair = (CopyPair) pairs.next();
            SVNFileType srcFileType = SVNFileType.getType(pair.mySource.getPath());
            if (srcFileType == SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Path ''{0}'' does not exist", pair.mySource.getPath());
                SVNErrorManager.error(err);
            }
            SVNFileType dstFileType = SVNFileType.getType(pair.myDst.getFile());
            if (dstFileType != SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "Path ''{0}'' already exists", pair.myDst.getFile());
                SVNErrorManager.error(err);
            }
            File dstParent = pair.myDst.getFile().getParentFile();
            pair.myBaseName = pair.myDst.getFile().getName();
            SVNFileType dstParentFileType = SVNFileType.getType(dstParent);
            if (makeParents && dstParentFileType == SVNFileType.NONE) {
                // create parents.
                addLocalParents(dstParent, getEventDispatcher());
            } else if (dstParentFileType != SVNFileType.DIRECTORY) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "Path ''{0}'' is not a directory", dstParent);
                SVNErrorManager.error(err);
            }
        }
        if (isMove) {
            moveWCToWC(copyPairs);
        } else {
            copyWCToWC(copyPairs);
        }
    }
    
    private void copyWCToWC(List pairs) throws SVNException {
        // find common ancestor for all dsts.
        String dstParentPath = null;
        for (Iterator ps = pairs.iterator(); ps.hasNext();) {
            CopyPair pair = (CopyPair) ps.next();
            String dstPath = pair.myDst.getFile().getAbsolutePath();
            if (dstParentPath == null) {
                dstParentPath = pair.myDst.getFile().getParentFile().getAbsolutePath();
            }
            dstParentPath = SVNPathUtil.getCommonPathAncestor(dstParentPath, dstPath);
        }
        File dstParent = new File(dstParentPath);
        SVNWCAccess dstAccess = createWCAccess();
        try {
            dstAccess.open(dstParent, true, 0);
            for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                CopyPair pair = (CopyPair) ps.next();
                checkCancelled();
                SVNWCAccess srcAccess = null;
                File srcParent = pair.mySource.getPath().getParentFile();
                SVNFileType srcType = SVNFileType.getType(pair.mySource.getPath());
                try {
                    if (srcParent.equals(dstParent)) {
                        if (srcType == SVNFileType.DIRECTORY) {
                            srcAccess = createWCAccess();
                            srcAccess.open(pair.mySource.getPath(), false, -1);
                        } else {
                            srcAccess = dstAccess;
                        }
                    } else {
                        srcAccess = createWCAccess();
                        srcAccess.open(srcParent, false, srcType == SVNFileType.DIRECTORY ? -1 : 0);
                    }
                    // do real copy.
                    copyFiles(pair.mySource.getPath(), dstParent, dstAccess, pair.myBaseName);
                    propagateMegeInfo(pair, srcAccess, dstAccess);
                } finally {
                    if (srcAccess != null && srcAccess != dstAccess) {
                        srcAccess.close();
                    }
                }
            }
        } finally {
            dstAccess.close();
            sleepForTimeStamp();
        }
    }

    private void moveWCToWC(List pairs) throws SVNException {
        for (Iterator ps = pairs.iterator(); ps.hasNext();) {
            CopyPair pair = (CopyPair) ps.next();
            checkCancelled();
            File srcParent = pair.mySource.getPath().getParentFile();
            File dstParent = pair.myDst.getFile().getParentFile();
            SVNFileType srcType = SVNFileType.getType(pair.mySource.getPath());
            SVNWCAccess srcAccess = createWCAccess();
            SVNWCAccess dstAccess = null;
            try {
                srcAccess.open(srcParent, true, srcType == SVNFileType.DIRECTORY ? -1 : 0);
                if (srcParent.equals(dstParent)) {
                    dstAccess = srcAccess;
                } else {
                    String srcParentPath = srcParent.getAbsolutePath().replace(File.separatorChar, '/');
                    String dstParentPath = dstParent.getAbsolutePath().replace(File.separatorChar, '/');
                    if (srcType == SVNFileType.DIRECTORY && 
                            SVNPathUtil.isAncestor(srcParentPath, dstParentPath)) {
                        dstAccess = srcAccess;
                    } else {
                        dstAccess = createWCAccess();
                        dstAccess.open(dstParent, true, 0);
                    }
                }
                copyFiles(pair.mySource.getPath(), dstParent, dstAccess, pair.myBaseName);
                propagateMegeInfo(pair, srcAccess, dstAccess);
                // delete src.
                SVNWCManager.delete(srcAccess, srcAccess.getAdminArea(srcParent), pair.mySource.getPath(), true, true);
            } finally {
                if (dstAccess != srcAccess) {
                    dstAccess.close();
                }
                srcAccess.close();
            }
        }
        sleepForTimeStamp();
    }
    
    private void propagateMegeInfo(CopyPair pair, SVNWCAccess srcAccess, SVNWCAccess dstAccess) throws SVNException {
        SVNEntry entry = srcAccess.getVersionedEntry(pair.mySource.getPath(), false);
        if (entry.getSchedule() == null || (entry.isScheduledForAddition() && entry.isCopied())) {
            SVNRepository repos = createRepository(entry.getSVNURL(), true);
            Map mergeInfo = calculateTargetMergeInfo(pair.mySource.getPath(), srcAccess, entry.getSVNURL(), entry.getRevision(), repos);
            SVNEntry dstEntry = dstAccess.getEntry(pair.myDst.getFile(), false);
            extendWCMergeInfo(pair.myDst.getFile(), dstEntry, mergeInfo, dstAccess);
            return;
        }
        Map mergeInfo = SVNPropertiesManager.parseMergeInfo(pair.mySource.getPath(), entry, false);
        if (mergeInfo == null) {
            mergeInfo = new TreeMap();
            SVNPropertiesManager.recordWCMergeInfo(pair.myDst.getFile(), mergeInfo, dstAccess);
        } 
    }
    
    private void copyFiles(File src, File dstParent, SVNWCAccess dstAccess, String dstName) throws SVNException {
        SVNWCAccess srcAccess = createWCAccess();
        try {
            srcAccess.probeOpen(src, false, -1);
            SVNEntry dstEntry = dstAccess.getEntry(dstParent, false);
            SVNEntry srcEntry = srcAccess.getEntry(src, false);
            
            if ((srcEntry.getRepositoryRoot() == null && dstEntry.getRepositoryRoot() != null) || 
                    !srcEntry.getRepositoryRoot().equals(dstEntry.getRepositoryRoot())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE, 
                        "Cannot copy to ''{0}'', as it is not from repository ''{1}''; it is from ''{2}''",
                        new Object[] {dstParent, srcEntry.getRepositoryRootURL(), dstEntry.getRepositoryRootURL()});
                SVNErrorManager.error(err);
            }
            if (dstEntry.isScheduledForDeletion()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE, 
                        "Cannot copy to ''{0}'', as it is scheduled for deletion", dstParent); 
                SVNErrorManager.error(err);
            }
            SVNFileType srcType = SVNFileType.getType(src);
            if (srcType == SVNFileType.FILE || srcType == SVNFileType.SYMLINK) {
                if (srcEntry.isScheduledForAddition() && !srcEntry.isCopied()) {
                    copyAddedFileAdm(src, dstAccess, dstParent, dstName, true);
                } else {
                    copyFileAdm(src, srcAccess, dstParent, dstAccess, dstName);
                }
            } else if (srcType == SVNFileType.DIRECTORY) {
                if (srcEntry.isScheduledForAddition() && !srcEntry.isCopied()) {
                    copyAddedDirAdm(src, srcAccess, dstParent, dstAccess, dstName, true);
                } else {
                    copyDirAdm(src, srcAccess, dstAccess, dstParent, dstName);
                }
            }
        } finally {
            srcAccess.close();
        }
    }
    
    private void copyFileAdm(File src, SVNWCAccess srcAccess, File dstParent, SVNWCAccess dstAccess, String dstName) throws SVNException {
        File dst = new File(dstParent, dstName);
        SVNFileType dstType = SVNFileType.getType(dst);
        if (dstType != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "''{0}'' already exists and is in the way", dst);
            SVNErrorManager.error(err);
        }
        SVNEntry dstEntry = dstAccess.getEntry(dst, false);
        if (dstEntry != null && dstEntry.isFile()) {
            if (!dstEntry.isScheduledForDeletion()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "There is already a versioned item ''{0}''", dst);
                SVNErrorManager.error(err);
            }
        }
        SVNEntry srcEntry = srcAccess.getVersionedEntry(src, false);
        if ((srcEntry.isScheduledForAddition() && !srcEntry.isCopied()) || srcEntry.getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "Cannot copy or move ''{0}'': it is not in repository yet; " +
            		"try committing first", src);
            SVNErrorManager.error(err);
        }
        String copyFromURL = null;
        long copyFromRevision = -1;
        SVNAdminArea srcDir = srcAccess.getAdminArea(src.getParentFile());
        if (srcEntry.isCopied()) {
            // get cf info - one of the parents has to keep that.
            SVNLocationEntry location = determineCopyFromInfo(src, srcAccess, srcEntry, dstEntry);
            copyFromURL = location.getPath();
            copyFromRevision = location.getRevision();
        } else {
            copyFromURL = srcEntry.getURL();
            copyFromRevision = srcEntry.getRevision();
        }
        // copy base file.
        File srcBaseFile = new File(src.getParentFile(), SVNAdminUtil.getTextBasePath(src.getName(), false));
        File dstBaseFile = new File(dstParent, SVNAdminUtil.getTextBasePath(dstName, true));
        SVNFileUtil.copyFile(srcBaseFile, dstBaseFile, false);
        SVNVersionedProperties srcBaseProps = srcDir.getBaseProperties(src.getName()); 
        SVNVersionedProperties srcWorkingProps = srcDir.getProperties(src.getName()); 

        // copy wc file.
        SVNAdminArea dstDir = dstAccess.getAdminArea(dstParent); 
        File tmpWCFile = SVNAdminUtil.createTmpFile(dstDir);
        if (srcWorkingProps.getPropertyValue(SVNProperty.SPECIAL) != null) {
            // TODO create symlink there?
            SVNFileUtil.copyFile(src, tmpWCFile, false);
        } else {
            SVNFileUtil.copyFile(src, tmpWCFile, false);
        }
        SVNWCManager.addRepositoryFile(dstDir, dstName, tmpWCFile, null, srcBaseProps.asMap(), srcWorkingProps.asMap(), copyFromURL, copyFromRevision);

        SVNEvent event = SVNEventFactory.createSVNEvent(dst, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNEventAction.ADD, null, null, null);
        dstAccess.handleEvent(event);
    }
    

    private void copyAddedFileAdm(File src, SVNWCAccess dstAccess, File dstParent, String dstName, boolean isAdded) throws SVNException {
        File dst = new File(dstParent, dstName);
        SVNFileUtil.copyFile(src, dst, false);
        if (isAdded) {
            SVNWCManager.add(dst, dstAccess.getAdminArea(dstParent), null, SVNRepository.INVALID_REVISION);
        }
    }
    
    private void copyDirAdm(File src, SVNWCAccess srcAccess, SVNWCAccess dstAccess, File dstParent, String dstName) throws SVNException {
        File dst = new File(dstParent, dstName);
        SVNEntry srcEntry = srcAccess.getVersionedEntry(src, false);
        if ((srcEntry.isScheduledForAddition() && !srcEntry.isCopied()) || srcEntry.getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "Cannot copy or move ''{0}'': it is not in repository yet; " +
                    "try committing first", src);
            SVNErrorManager.error(err);
        }
        SVNFileUtil.copyDirectory(src, dst, true, getEventDispatcher());
        SVNWCClient wcClient = new SVNWCClient((ISVNAuthenticationManager) null, null);
        wcClient.setEventHandler(getEventDispatcher());
        wcClient.doCleanup(dst);
        
        SVNWCAccess tgtAccess = createWCAccess();
        SVNAdminArea dir = null;
        String copyFromURL = null;
        long copyFromRevision = -1;
        try {
            dir = tgtAccess.open(dst, true, -1);
            postCopyCleanup(dir);
            if (srcEntry.isCopied()) {
                SVNEntry dstEntry = dstAccess.getEntry(dst, false);
                SVNLocationEntry info = determineCopyFromInfo(src, srcAccess, srcEntry, dstEntry);
                copyFromURL = info.getPath();
                copyFromRevision = info.getRevision();
                dir.getEntry("", false).setURL(info.getPath());
            } else {
                copyFromURL = srcEntry.getURL();
                copyFromRevision = srcEntry.getRevision();
            }
        } finally {
            tgtAccess.close();
        }
        SVNWCManager.add(dst, dstAccess.getAdminArea(dstParent), SVNURL.parseURIEncoded(copyFromURL), copyFromRevision);
    }

    private void copyAddedDirAdm(File src, SVNWCAccess srcAccess, File dstParent, SVNWCAccess dstParentAccess, String dstName, boolean isAdded) throws SVNException {
        File dst = new File(dstParent, dstName);
        if (!isAdded) {
            SVNFileUtil.copyDirectory(src, dst, true, getEventDispatcher());
        } else {
            checkCancelled();
            dst.mkdirs();
            
            SVNWCManager.add(dst, dstParentAccess.getAdminArea(dstParent), null, SVNRepository.INVALID_REVISION);
            SVNAdminArea srcChildArea = srcAccess.retrieve(src);
            
            File[] entries = SVNFileListUtil.listFiles(src);
            
            for (int i = 0; entries != null && i < entries.length; i++) {
                checkCancelled();
                File fsEntry = entries[i];
                String name = fsEntry.getName();
                if (SVNFileUtil.getAdminDirectoryName().equals(name)) {
                    continue;
                }
            
                SVNEntry entry = srcChildArea.getEntry(name, true);
                if (fsEntry.isDirectory()) {
                    copyAddedDirAdm(fsEntry, srcAccess, dstParent, dstParentAccess, name, entry != null);
                } else if (fsEntry.isFile()) {
                    copyAddedFileAdm(fsEntry, dstParentAccess, dstParent, name, entry != null);
                }
            }
        }
    }
    
    private SVNLocationEntry determineCopyFromInfo(File src, SVNWCAccess srcAccess, SVNEntry srcEntry, SVNEntry dstEntry) throws SVNException {
        String url = null;
        long rev = -1;
        if (srcEntry.getCopyFromURL() != null) {
            url = srcEntry.getCopyFromURL();
            rev = srcEntry.getCopyFromRevision();
        } else {
            SVNLocationEntry info = getCopyFromInfoFromParent(src, srcAccess);
            url = info.getPath();
            rev = info.getRevision();
        }
        if (dstEntry != null && rev == dstEntry.getRevision() && url.equals(dstEntry.getCopyFromURL())) {
            url = null;
            rev = -1;
        } 
        return new SVNLocationEntry(rev, url);
    }
    
    private SVNLocationEntry getCopyFromInfoFromParent(File file, SVNWCAccess access) throws SVNException {
        File parent = file.getParentFile();
        String rest = file.getName();
        String url = null;
        long rev = -1;
        while (parent != null && url == null) {
            try {
                SVNEntry entry = access.getVersionedEntry(parent, false);
                url = entry.getCopyFromURL();
                rev = entry.getCopyFromRevision();
            } catch (SVNException e) {
                SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
                try {
                    wcAccess.probeOpen(parent, false, -1);
                    SVNEntry entry = wcAccess.getVersionedEntry(parent, false);
                    url = entry.getCopyFromURL();
                    rev = entry.getCopyFromRevision();
                } finally {
                    wcAccess.close();
                }
            }
            if (url != null) {
                url = SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(rest));
            } else {
                rest = SVNPathUtil.append(parent.getName(), rest);
                parent = parent.getParentFile();
            }
        }
        return new SVNLocationEntry(rev, url);
    }

    
    private void addLocalParents(File path, ISVNEventHandler handler) throws SVNException {
        boolean created = path.mkdirs();
        SVNWCClient wcClient = new SVNWCClient((ISVNAuthenticationManager) null, null);
        try {
            wcClient.setEventHandler(handler);
            wcClient.doAdd(path, false, false, true, true, true, true);
        } catch (SVNException e) {
            if (created) {
                SVNFileUtil.deleteAll(path, true);
            }
            throw e;
        }
    }
    
    private SVNLocationEntry getCopyFromInfoFromParent(File path, SVNAdminArea adminArea) throws SVNException {
        String copyFromURL = null;
        long copyFromRevision = SVNRepository.INVALID_REVISION; 
        File parentPath = path.getParentFile();
        String name = path.getName();
        while (copyFromURL == null) {
            SVNEntry entry = null;
            if (parentPath.equals(adminArea.getRoot()) || SVNPathUtil.isAncestor(adminArea.getRoot().getAbsolutePath(), parentPath.getAbsolutePath())) {
                SVNAdminArea parentArea = adminArea.getWCAccess().retrieve(parentPath);
                entry = parentArea.getVersionedEntry(parentArea.getThisDirName(), false);
            } else {
                SVNWCAccess access = SVNWCAccess.newInstance(null);
                try {
                    access.probeOpen(parentPath, false, SVNWCAccess.INFINITE_DEPTH);
                    entry = access.getVersionedEntry(parentPath, false);
                } finally {
                    access.close();
                }
            }
            if (entry.getCopyFromURL() != null) {
                copyFromURL = SVNPathUtil.append(entry.getCopyFromURL(), SVNEncodingUtil.uriEncode(name));
                copyFromRevision = entry.getCopyFromRevision();
            } else {
                name = SVNPathUtil.append(parentPath.getName(), name);
                parentPath = parentPath.getParentFile();
            }
        }
        return new SVNLocationEntry(copyFromRevision, copyFromURL);
    }

    private void extendWCMergeInfo(File path, SVNEntry entry, Map mergeInfo, SVNWCAccess access) throws SVNException {

        Map wcMergeInfo = SVNPropertiesManager.parseMergeInfo(path, entry, false);
        if (wcMergeInfo != null) {
            wcMergeInfo = SVNMergeInfoManager.mergeMergeInfos(wcMergeInfo, mergeInfo);
        } else {
            wcMergeInfo = mergeInfo;
        }
        
        SVNPropertiesManager.recordWCMergeInfo(path, wcMergeInfo, access);
    }
    
    private Map calculateTargetMergeInfo(File srcFile, SVNWCAccess access, SVNURL srcURL, long srcRevision, SVNRepository repository) throws SVNException {
        Map targetMergeInfo = null;
        boolean isLocallyAdded = false;
        SVNEntry entry = null;
        SVNURL url = null;        
        if (access != null) {
            entry = access.getVersionedEntry(srcFile, false);
            if (entry.isScheduledForAddition() && !entry.isCopied()) {
                isLocallyAdded = true;
            } else {
                if (entry.getCopyFromURL() != null) {
                    url = entry.getCopyFromSVNURL();
                    srcRevision = entry.getCopyFromRevision();
                } else if (entry.getURL() != null) {
                    url = entry.getSVNURL();
                    srcRevision = entry.getRevision();
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Entry for ''{0}'' has no URL", srcFile);
                    SVNErrorManager.error(err);
                }
            }
        } else {
            url = srcURL;
        }

        if (!isLocallyAdded) {
            String srcAbsPath = getPathRelativeToRoot(srcFile, url, entry != null ? entry.getRepositoryRootURL() : null, access, repository);
            targetMergeInfo = repository.getMergeInfo(new String[] { srcAbsPath }, srcRevision, SVNMergeInfoInheritance.INHERITED);
        } else {
            targetMergeInfo = new TreeMap();
        }
        return targetMergeInfo;
    }
    
    private Map getImpliedMergeInfo(SVNRepository repos, String relPath, String path, long revision) throws SVNException {
        Map impliedMergeInfo = new TreeMap();
        long oldestRev = getPathLastChangeRevision(relPath, revision, repos);
        if (!SVNRevision.isValidRevisionNumber(oldestRev)) {
            return impliedMergeInfo;
        }
        SVNMergeRange range = new SVNMergeRange(oldestRev - 1, revision, true);
        SVNMergeRangeList rangeList = new SVNMergeRangeList(new SVNMergeRange[] { range });
        impliedMergeInfo.put(path, rangeList);
        return impliedMergeInfo;
    }
    
    private void propagateMergeInfoWithWC(File srcFile, File dstFile, SVNAdminArea srcArea, SVNAdminArea dstArea, boolean includeMergeHistory) throws SVNException {
        Map mergeInfo = null;
        SVNWCAccess srcAccess = srcArea.getWCAccess();
        SVNWCAccess dstAccess = dstArea.getWCAccess();
        SVNEntry srcEntry = srcAccess.getVersionedEntry(srcFile, false);
        if (includeMergeHistory) {
            if (srcEntry.getSchedule() == null || (srcEntry.isScheduledForAddition() && srcEntry.isCopied())) {
                SVNRepository repos = createRepository(srcEntry.getSVNURL(), true);
                mergeInfo = calculateTargetMergeInfo(srcFile, srcAccess, null, srcEntry.getRevision(), repos);
                SVNEntry dstEntry = dstAccess.getVersionedEntry(dstFile, false);
                extendWCMergeInfo(dstFile, dstEntry, mergeInfo, dstAccess);
                return;
            }
        }
        
        mergeInfo = SVNPropertiesManager.parseMergeInfo(srcFile, srcEntry, false);
        if (mergeInfo == null) {
            mergeInfo = new TreeMap();
            SVNPropertiesManager.recordWCMergeInfo(dstFile, mergeInfo, dstAccess);
        } 
    }
    
    private static class CopyCommitPathHandler2 implements ISVNCommitPathHandler {
        
        private Map myPathInfos;
        private boolean myIsMove;
        
        public CopyCommitPathHandler2(Map pathInfos, boolean isMove) {
            myPathInfos = pathInfos;
            myIsMove = isMove;
        }
        
        public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
            PathDriverInfo pathInfo = (PathDriverInfo) myPathInfos.get(commitPath);
            boolean doAdd = false;
            boolean doDelete = false;
            if (pathInfo.isDirAdded) {
                commitEditor.addDir(commitPath, null, SVNRepository.INVALID_REVISION);
                return true;
            }
            
            if (pathInfo.isResurrection) {
                if (!myIsMove) {
                    doAdd = true;
                }
            } else {
                if (myIsMove) {
                    if (commitPath.equals(pathInfo.mySrcPath)) {
                        doDelete = true;
                    } else {
                        doAdd = true;
                    }
                } else {
                    doAdd = true;
                }
            }
            if (doDelete) {
                commitEditor.deleteEntry(commitPath, -1);
            }
            boolean closeDir = false;
            if (doAdd) {
                SVNPathUtil.checkPathIsValid(commitPath);
                if (pathInfo.mySrcKind == SVNNodeKind.DIR) {
                    commitEditor.addDir(commitPath, pathInfo.mySrcPath, 
                                        pathInfo.mySrcRevisionNumber);
                    if (pathInfo.myMergeInfoProp != null) {
                        commitEditor.changeDirProperty(SVNProperty.MERGE_INFO, 
                                                       pathInfo.myMergeInfoProp);
                    }
                    closeDir = true;
                } else {
                    commitEditor.addFile(commitPath, pathInfo.mySrcPath, 
                                         pathInfo.mySrcRevisionNumber);
                    if (pathInfo.myMergeInfoProp != null) {
                        commitEditor.changeFileProperty(commitPath, SVNProperty.MERGE_INFO, 
                                                        pathInfo.myMergeInfoProp);
                    }
                    commitEditor.closeFile(commitPath, null);
                }
            }
            return closeDir;
        }
        
    }

    private static class PathDriverInfo {
        boolean isDirAdded;
        boolean isResurrection;
        SVNNodeKind mySrcKind;
        String mySrcPath;
        String myDstPath;
        String myMergeInfoProp;
        long mySrcRevisionNumber;
    }
    
    private static class CopyPathInfo {
        public boolean isDirAdded;
        public boolean isResurrection;
        public SVNNodeKind mySrcKind;
        public String mySrcPath;
        public String myDstPath;
        public String myMergeInfoProp;
        public long mySrcRevisionNumber;
    }
    
    private static class CopyPair {
        public SVNNodeKind mySourceKind;
        public long mySourceRevisionNumber;
        public SVNCopySource myOriginalSource;
        public String myBaseName;
        SVNCopySource mySource;
        SVNRevision mySourceRevision;
        SVNRevision mySourcePegRevision;
        SVNPath myDst;
        
        public void setSourceRevisions(SVNRevision pegRevision, SVNRevision revision) {
            if (pegRevision == SVNRevision.UNDEFINED) {
                if (mySource.isURL()) {
                    pegRevision = SVNRevision.HEAD;
                } else {
                    pegRevision = SVNRevision.WORKING;
                }
            }
            if (revision == SVNRevision.UNDEFINED) {
                revision = pegRevision;
            }
            mySourceRevision = revision;
            mySourcePegRevision = pegRevision;
        }
    }

}
