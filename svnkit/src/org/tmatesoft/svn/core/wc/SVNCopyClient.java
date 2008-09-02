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
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNCommitMediator;
import org.tmatesoft.svn.core.internal.wc.SVNCommitUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCommitter;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
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
import org.tmatesoft.svn.util.SVNLogType;

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
 * @version 1.2.0
 * @author  TMate Software Ltd.
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNCopyClient extends SVNBasicClient {

    private ISVNCommitHandler myCommitHandler;
    private ISVNCommitParameters myCommitParameters;
    private ISVNExternalsHandler myExternalsHandler;

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

    /**
     * Constructs and initializes an <b>SVNCopyClient</b> object
     * with the specified run-time configuration and repository pool object.
     * 
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNCopyClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
     * 
     * <p/>
     * If <code>repositoryPool</code> is <span class="javakeyword">null</span>,
     * then {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory} will be used to create {@link SVNRepository repository access objects}.
     *
     * @param repositoryPool   a repository pool object
     * @param options          a run-time configuration options driver
     */
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
     * Sets an externals handler to be used by this client object.
     * 
     * @param externalsHandler user's implementation of {@link ISVNExternalsHandler}
     * @see   #getExternalsHandler()
     * @since 1.2
     */
    public void setExternalsHandler(ISVNExternalsHandler externalsHandler) {
        myExternalsHandler = externalsHandler;
    }

    /**
     * Returns an externals handler used by this update client.
     * 
     * <p/>
     * If no user's handler is provided then {@link ISVNExternalsHandler#DEFAULT} is returned and 
     * used by this client object by default.
     * 
     * <p/>
     * For more information what externals handlers are for, please, refer to {@link ISVNExternalsHandler} and 
     * {@link #doCopy(SVNCopySource[], SVNURL, boolean, boolean, boolean, String, SVNProperties)}. 
     * 
     * @return           externals handler being in use
     * @see              #setExternalsHandler(ISVNExternalsHandler)
     * @since            1.2 
     */
    public ISVNExternalsHandler getExternalsHandler() {
        if (myExternalsHandler == null) {
            myExternalsHandler = ISVNExternalsHandler.DEFAULT;
        }
        return myExternalsHandler;
    }

    /** 
     * Copies each source in <code>sources</code> to <code>dst</code>.
     *
     * <p/>
     * If multiple <code>sources</code> are given, <code>dst</code> must be a directory, and <code>sources</code> 
     * will be copied as children of <code>dst</code>.
     *
     * <p/>
     * Each <code>src</code> in <code>sources</code> must be files or directories under version control,
     * or URLs of a versioned item in the repository. If <code>sources</code> has multiple items, they  
     * must be all repository URLs or all working copy paths.
     * 
     * <p/>
     * The parent of <code>dst</code> must already exist.
     * 
     * <p/>
     * If <code>sources</code> has only one item, attempts to copy it to <code>dst</code>. 
     * If <code>failWhenDstExists</code> is <span class="javakeyword">false</span> and <code>dst</code> already 
     * exists, attempts to copy the item as a child of <code>dst</code>. If <code>failWhenDstExists</code> is 
     * <span class="javakeyword">true</span> and <code>dst</code> already exists, throws an {@link SVNException} 
     * with the {@link SVNErrorCode#ENTRY_EXISTS} error code.
     * 
     * <p/>
     * If <code>sources</code> has multiple items, and <code>failWhenDstExists</code> is 
     * <span class="javakeyword">false</span>, all <code>sources</code> are copied as children of <code>dst</code>. 
     * If any child of <code>dst</code> already exists with the same name any item in <code>sources</code>,
     * throws an {@link SVNException} with the {@link SVNErrorCode#ENTRY_EXISTS} error code.
     * 
     * <p/>
     * If <code>sources</code> has multiple items, and <code>failWhenDstExists</code> is 
     * <span class="javakeyword">true</span>, throws an {@link SVNException} with the 
     * {@link SVNErrorCode#CLIENT_MULTIPLE_SOURCES_DISALLOWED}.
     *
     * <p/>
     * This method is just a variant of a local add operation, where <code>sources</code> are scheduled for 
     * addition as copies. No changes will happen to the repository until a commit occurs. This scheduling can 
     * be removed with {@link SVNWCClient#doRevert(File[], SVNDepth, Collection)}.
     * 
     * <p/>
     * If <code>makeParents is <span class="javakeyword">true</span>, creates any non-existent parent directories
     * also.
     * 
     * <p/>
     * If the caller's {@link ISVNEventHandler} is non-<span class="javakeyword">null</span>, invokes it  
     * for each item added at the new location.
     * 
     * <p/>
     * Note: this routine requires repository access only when sources are urls.
     * 
     * @param  sources               array of copy sources 
     * @param  dst                   destination working copy path
     * @param  isMove                if <span class="javakeyword">true</span>, then it will be a move operation 
     *                               (delete, then add with history)                 
     * @param  makeParents           if <span class="javakeyword">true</span>, creates non-existent parent 
     *                               directories as well
     * @param  failWhenDstExists     controls whether to fail or not if <code>dst</code> already exists
     * @throws SVNException          
     * @since                        1.2, SVN 1.5
     */
    public void doCopy(SVNCopySource[] sources, File dst, boolean isMove, boolean makeParents, 
            boolean failWhenDstExists) throws SVNException {
        if (sources.length > 1 && failWhenDstExists) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MULTIPLE_SOURCES_DISALLOWED);
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        sources = expandCopySources(sources);
        if (sources.length == 0) {
            return;
        }
        try {
            setupCopy(sources, new SVNPath(dst.getAbsolutePath()), isMove, makeParents, null, null);
        } catch (SVNException e) {
            SVNErrorCode err = e.getErrorMessage().getErrorCode();
            if (!failWhenDstExists && sources.length == 1 && (err == SVNErrorCode.ENTRY_EXISTS || err == SVNErrorCode.FS_ALREADY_EXISTS)) {
                SVNCopySource source = sources[0];
                String baseName = source.getName();
                if (source.isURL()) {
                    baseName = SVNEncodingUtil.uriDecode(baseName);
                }
                try {
                    setupCopy(sources, new SVNPath(new File(dst, baseName).getAbsolutePath()), isMove, 
                            makeParents, null, null);
                } catch (SVNException second) {
                    throw second;
                }
                return;
            }
            throw e;
        }
    }

   /** 
    * Copies each source in <code>sources</code> to <code>dst</code>.
    *
    * <p/>
    * If multiple <code>sources</code> are given, <code>dst</code> must be a directory, and <code>sources</code> 
    * will be copied as children of <code>dst</code>.
    *
    * <p/>
    * Each <code>src</code> in <code>sources</code> must be files or directories under version control,
    * or URLs of a versioned item in the repository. If <code>sources</code> has multiple items, they  
    * must be all repository URLs or all working copy paths.
    * 
    * <p/>
    * The parent of <code>dst</code> must already exist.
    * 
    * <p/>
    * If <code>sources</code> has only one item, attempts to copy it to <code>dst</code>. 
    * If <code>failWhenDstExists</code> is <span class="javakeyword">false</span> and <code>dst</code> already 
    * exists, attempts to copy the item as a child of <code>dst</code>. If <code>failWhenDstExists</code> is 
    * <span class="javakeyword">true</span> and <code>dst</code> already exists, throws an {@link SVNException} 
    * with the {@link SVNErrorCode#FS_ALREADY_EXISTS} error code.
    * 
    * <p/>
    * If <code>sources</code> has multiple items, and <code>failWhenDstExists</code> is 
    * <span class="javakeyword">false</span>, all <code>sources</code> are copied as children of <code>dst</code>. 
    * If any child of <code>dst</code> already exists with the same name any item in <code>sources</code>,
    * throws an {@link SVNException} with the {@link SVNErrorCode#FS_ALREADY_EXISTS} error code.
    * 
    * <p/>
    * If <code>sources</code> has multiple items, and <code>failWhenDstExists</code> is 
    * <span class="javakeyword">true</span>, throws an {@link SVNException} with the 
    * {@link SVNErrorCode#CLIENT_MULTIPLE_SOURCES_DISALLOWED}.
    *
    * <p/>
    * {@link ISVNAuthenticationManager Authentication manager} (whether provided directly through the 
    * appropriate constructor or in an {@link ISVNRepositoryPool} instance) and {@link #getCommitHandler() commit handler} 
    * are used to immediately attempt to commit the copy action in the repository. 
    *
    * <p/>
    * If <code>makeParents is <span class="javakeyword">true</span>, creates any non-existent parent directories
    * also.
    * 
    * <p/>
    * If non-<span class="javakeyword">null</span>, <code>revisionProperties</code> is an object holding 
    * additional, custom revision properties (<code>String</code> to {@link SVNPropertyValue} mappings) to be 
    * set on the new revision. This table cannot contain any standard Subversion properties.
    * 
    * <p/>
    * If the caller's {@link ISVNEventHandler} is non-<span class="javakeyword">null</span>, invokes it  
    * for each item added at the new location.
    * 
    * <p/>
    * When performing a wc-to-url copy (tagging|branching from a working copy) it's possible to fix 
    * revisions of external working copies (if any) which are located within the working copy being copied.
    * For example, imagine you have a working copy and on one of its subdirecotries you set an 
    * <span class="javastring">"svn:externals"</span> property which does not contain a revision number. 
    * Suppose you have made a tag from your working copy and in some period of time a user checks out 
    * that tag. It could have happened that the external project has evolved since the tag creation moment 
    * and the tag version is nomore compatible with it. So, the user has a broken project since it will not 
    * compile because of the API incompatibility between the two versions of the external project: the HEAD 
    * one and the one existed in the moment of the tag creation. That is why it appears useful to fix externals 
    * revisions during a wc-to-url copy. To enable externals revision fixing a user should implement 
    * {@link ISVNExternalsHandler}. The user's implementation 
    * {@link ISVNExternalsHandler#handleExternal(File, SVNURL, SVNRevision, SVNRevision, String, SVNRevision)} 
    * method will be called on every external that will be met in the working copy. If the user's implementation 
    * returns non-<span class="javakeyword">null</span> external revision, it's compared with the revisions 
    * fetched from the external definition. If they are different, the user's revision will be written in 
    * the external definition of the tag. Otherwise if the returned revision is equal to the revision from 
    * the external definition or if the user's implementation returns <span class="javakeyword">null</span> for 
    * that external, it will be skipped (i.e. left as is, unprocessed).        
    * 
    * <p/>
    * Note: this routine requires repository access.
    * 
    * @param  sources               array of copy sources 
    * @param  dst                   destination url
    * @param  isMove                if <span class="javakeyword">true</span>, then it will be a move operation 
    *                               (delete, then add with history)                 
    * @param  makeParents           if <span class="javakeyword">true</span>, creates non-existent parent 
    *                               directories as well
    * @param  failWhenDstExists     controls whether to fail or not if <code>dst</code> already exists
    * @param  commitMessage         commit log message
    * @param  revisionProperties    custom revision properties
    * @return                       information about the new committed revision 
    * @throws SVNException          
    * @since                        1.2, SVN 1.5
    */
    public SVNCommitInfo doCopy(SVNCopySource[] sources, SVNURL dst, boolean isMove, boolean makeParents, 
            boolean failWhenDstExists, String commitMessage, SVNProperties revisionProperties) throws SVNException {
        if (sources.length > 1 && failWhenDstExists) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MULTIPLE_SOURCES_DISALLOWED);
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        sources = expandCopySources(sources);
        if (sources.length == 0) {
            return SVNCommitInfo.NULL;
        }
        try {
            return setupCopy(sources, new SVNPath(dst.toString()), isMove, makeParents, commitMessage, 
                    revisionProperties);
        } catch (SVNException e) {
            SVNErrorCode err = e.getErrorMessage().getErrorCode();
            if (!failWhenDstExists && sources.length == 1 && (err == SVNErrorCode.ENTRY_EXISTS || err == SVNErrorCode.FS_ALREADY_EXISTS)) {
                SVNCopySource source = sources[0];
                String baseName = source.getName();
                if (!source.isURL()) {
                    baseName = SVNEncodingUtil.uriEncode(baseName);
                }
                try {
                    return setupCopy(sources, new SVNPath(dst.appendPath(baseName, true).toString()), isMove, 
                            makeParents, commitMessage, revisionProperties);
                } catch (SVNException second) {
                    throw second;
                }
            }
            throw e;
        }
    }

    /**
     * Converts a disjoint working copy to a copied one.
     * 
     * <p/>
     * Note: this routine does not require repository access. However if it's performed on an old format 
     * working copy where repository root urls were not written, the routine will connect to the repository 
     * to fetch the repository root url. 
     * 
     * @param  nestedWC      the root of the working copy located in another working copy (disjoint wc)
     * @throws SVNException  in the following cases:
     *                       <ul>
     *                       <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - 
     *                       if <code>nestedWC</code> is either not a directory, or has no parent at all;
     *                       if the current local filesystem parent of <code>nestedWC</code> is actually a 
     *                       child of it in the repository
     *                       <li/>exception with {@link SVNErrorCode#ENTRY_EXISTS} error code -  
     *                       if <code>nestedWC</code> is not a disjoint working copy, i.e. there is already
     *                       a versioned item under the parent path of <code>nestedWC</code>;
     *                       if <code>nestedWC</code> is not in the repository yet (has got a schedule for 
     *                       addition flag)
     *                       <li/>exception with {@link SVNErrorCode#WC_INVALID_SCHEDULE} error code - 
     *                       if <code>nestedWC</code> is not from the same repository as the parent directory;
     *                       if the parent of <code>nestedWC</code> is scheduled for deletion;
     *                       if <code>nestedWC</code> is scheduled for deletion
     *                       <li/>
     *                       </ul>
     * @since                1.2.0 
     */
    public void doCopy(File nestedWC) throws SVNException {
        copyDisjointWCToWC(nestedWC);
    }
    
    private SVNCopySource[] expandCopySources(SVNCopySource[] sources) throws SVNException {
        Collection expanded = new ArrayList(sources.length);
        for (int i = 0; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            if (source.isCopyContents() && source.isURL()) {
                // get children at revision.
                SVNRevision pegRevision = source.getPegRevision();
                if (!pegRevision.isValid()) {
                    pegRevision = SVNRevision.HEAD;
                }
                SVNRevision startRevision = source.getRevision();
                if (!startRevision.isValid()) {
                    startRevision = pegRevision;
                }
                SVNRepositoryLocation[] locations = getLocations(source.getURL(), null, null, pegRevision, startRevision, SVNRevision.UNDEFINED);
                SVNRepository repository = createRepository(locations[0].getURL(), null, null, true);
                long revision = locations[0].getRevisionNumber();
                Collection entries = new ArrayList();
                repository.getDir("", revision, null, 0, entries);
                for (Iterator ents = entries.iterator(); ents.hasNext();) {
                    SVNDirEntry entry = (SVNDirEntry) ents.next();
                    // add new copy source.
                    expanded.add(new SVNCopySource(SVNRevision.UNDEFINED, source.getRevision(), entry.getURL()));
                }
            } else {
                expanded.add(source);
            }
        }
        return (SVNCopySource[]) expanded.toArray(new SVNCopySource[expanded.size()]);
    }
    
    private String getUUIDFromPath(SVNWCAccess wcAccess, File path) throws SVNException {
        SVNEntry entry = wcAccess.getVersionedEntry(path, true);
        String uuid = null;
        if (entry.getUUID() != null) {
            uuid = entry.getUUID();
        } else if (entry.getURL() != null) {
            SVNRepository repos = createRepository(entry.getSVNURL(), null, null, false);
            try {
                uuid = repos.getRepositoryUUID(true);
            } finally {
                repos.closeSession();
            }
        } else {
            if (wcAccess.isWCRoot(path)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            uuid = getUUIDFromPath(wcAccess, path.getParentFile());
        }
        return uuid;
    }

    private static void postCopyCleanup(SVNAdminArea dir) throws SVNException {
        SVNPropertiesManager.deleteWCProperties(dir, null, false);
        SVNFileUtil.setHidden(dir.getAdminDirectory(), true);
        Map attributes = new SVNHashMap(); 
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

    private SVNCommitInfo setupCopy(SVNCopySource[] sources, SVNPath dst, boolean isMove, boolean makeParents, 
            String message, SVNProperties revprops) throws SVNException {
        List pairs = new ArrayList(sources.length);
        for (int i = 0; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            if (source.isURL() && 
                 (source.getPegRevision() == SVNRevision.BASE ||
                  source.getPegRevision() == SVNRevision.COMMITTED ||
                  source.getPegRevision() == SVNRevision.PREVIOUS)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                        "Revision type requires a working copy path, not URL");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        boolean srcIsURL = sources[0].isURL();
        boolean dstIsURL = dst.isURL();
        
        if (sources.length > 1) {
            for (int i = 0; i < sources.length; i++) {
                SVNCopySource source = sources[i];
                CopyPair pair = new CopyPair();
                pair.mySource = source.isURL() ? source.getURL().toString() : source.getFile().getAbsolutePath().replace(File.separatorChar, '/');
                pair.setSourceRevisions(source.getPegRevision(), source.getRevision());
                if (SVNPathUtil.isURL(pair.mySource) != srcIsURL) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                            "Cannot mix repository and working copy sources");
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                String baseName = source.getName();
                if (srcIsURL && !dstIsURL) {
                    baseName = SVNEncodingUtil.uriDecode(baseName);
                }
                pair.myDst = dstIsURL ? dst.getURL().appendPath(baseName, true).toString() : 
                    new File(dst.getFile(), baseName).getAbsolutePath().replace(File.separatorChar, '/');
                pairs.add(pair);
            }
        } else {
            SVNCopySource source = sources[0];
            CopyPair pair = new CopyPair();
            pair.mySource = source.isURL() ? source.getURL().toString() : source.getFile().getAbsolutePath().replace(File.separatorChar, '/');
            pair.setSourceRevisions(source.getPegRevision(), source.getRevision());
            pair.myDst = dstIsURL ? dst.getURL().toString() : dst.getFile().getAbsolutePath().replace(File.separatorChar, '/');
            pairs.add(pair);
        }
        
        if (!srcIsURL && !dstIsURL) {
            for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                CopyPair pair = (CopyPair) ps.next();
                String srcPath = pair.mySource;
                String dstPath = pair.myDst;
                if (SVNPathUtil.isAncestor(srcPath, dstPath)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                            "Cannot copy path ''{0}'' into its own child ''{1}",
                            new Object[] { srcPath, dstPath });
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        }
        if (isMove) {
            if (srcIsURL == dstIsURL) {
                for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                    CopyPair pair = (CopyPair) ps.next();
                    File srcPath = new File(pair.mySource);
                    File dstPath = new File(pair.myDst);
                    if (srcPath.equals(dstPath)) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                                "Cannot move path ''{0}'' into itself", srcPath);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                        "Moves between the working copy and the repository are not supported");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        } else {
            if (!srcIsURL) {
                boolean needReposRevision = false;
                boolean needReposPegRevision = false;
                for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                    CopyPair pair = (CopyPair) ps.next();
                    if (pair.mySourceRevision != SVNRevision.UNDEFINED && 
                            pair.mySourceRevision != SVNRevision.WORKING) {
                        needReposRevision = true;
                    }
                    if (pair.mySourcePegRevision != SVNRevision.UNDEFINED && 
                            pair.mySourcePegRevision != SVNRevision.WORKING) {
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
                            wcAccess.probeOpen(new File(pair.mySource), false, 0);
                            SVNEntry entry = wcAccess.getEntry(new File(pair.mySource), false);
                            SVNURL url = entry.isCopied() ? entry.getCopyFromSVNURL() : entry.getSVNURL();
                            if (url == null) {
                                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                                        "''{0}'' does not have a URL associated with it", new File(pair.mySource));
                                SVNErrorManager.error(err, SVNLogType.WC);
                            }
                            pair.mySource = url.toString();
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
            return copyWCToRepos(pairs, makeParents, message, revprops);
        } else if (srcIsURL && !dstIsURL) {
            // url2wc.
            copyReposToWC(pairs, makeParents);
            return SVNCommitInfo.NULL;
        } else {
            return copyReposToRepos(pairs, makeParents, isMove, message, revprops);
        }
    }
    
    private SVNCommitInfo copyWCToRepos(List copyPairs, boolean makeParents, String message, 
            SVNProperties revprops) throws SVNException {
        String topSrc = ((CopyPair) copyPairs.get(0)).mySource;
        for (int i = 1; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            topSrc = SVNPathUtil.getCommonPathAncestor(topSrc, pair.mySource);
        }
        SVNWCAccess wcAccess = createWCAccess();
        SVNCommitInfo info = null;
        ISVNEditor commitEditor = null;
        Collection tmpFiles = null;
        try {
            SVNAdminArea adminArea = wcAccess.probeOpen(new File(topSrc), false, SVNWCAccess.INFINITE_DEPTH);
            wcAccess.setAnchor(adminArea.getRoot());

            String topDstURL = ((CopyPair) copyPairs.get(0)).myDst;
            topDstURL = SVNPathUtil.removeTail(topDstURL);
            for (int i = 1; i < copyPairs.size(); i++) {
                CopyPair pair = (CopyPair) copyPairs.get(i);
                topDstURL = SVNPathUtil.getCommonPathAncestor(topDstURL, pair.myDst);
            }

            // should we use also wcAccess here? i do not think so.
            SVNRepository repos = createRepository(SVNURL.parseURIEncoded(topDstURL), adminArea.getRoot(), 
                    wcAccess, true);
            List newDirs = new ArrayList();
            if (makeParents) {
                String rootURL = topDstURL;
                SVNNodeKind kind = repos.checkPath("", -1);
                while(kind == SVNNodeKind.NONE) {
                    newDirs.add(rootURL);
                    rootURL = SVNPathUtil.removeTail(rootURL);
                    repos.setLocation(SVNURL.parseURIEncoded(rootURL), false);
                    kind = repos.checkPath("", -1);
                }
                topDstURL = rootURL;
            }

            for (int i = 0; i < copyPairs.size(); i++) {
                CopyPair pair = (CopyPair) copyPairs.get(i);
                SVNEntry entry = wcAccess.getEntry(new File(pair.mySource), false);
                pair.mySourceRevisionNumber = entry.getRevision();
                String dstRelativePath = SVNPathUtil.getPathAsChild(topDstURL, pair.myDst);
                dstRelativePath = SVNEncodingUtil.uriDecode(dstRelativePath);
                SVNNodeKind kind = repos.checkPath(dstRelativePath, -1);
                if (kind != SVNNodeKind.NONE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, 
                            "Path ''{0}'' already exists", SVNURL.parseURIEncoded(pair.myDst));
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            // create commit items list to fetch log messages.
            List commitItems = new ArrayList(copyPairs.size());
            if (makeParents) {
                for (int i = 0; i < newDirs.size(); i++) {
                    String newDirURL = (String) newDirs.get(i);
                    SVNURL url = SVNURL.parseURIEncoded(newDirURL);
                    SVNCommitItem item = new SVNCommitItem(null, url, null, SVNNodeKind.NONE, null, null, true, false, false, false, false, false);
                    commitItems.add(item);
                }
            }
            for (int i = 0; i < copyPairs.size(); i++) {
                CopyPair pair = (CopyPair) copyPairs.get(i);
                SVNURL url = SVNURL.parseURIEncoded(pair.myDst);
                SVNCommitItem item = new SVNCommitItem(null, url, null, SVNNodeKind.NONE, null, null, true, false, false, 
                        false, false, false);
                commitItems.add(item);
            }
            SVNCommitItem[] commitables = (SVNCommitItem[]) commitItems.toArray(new SVNCommitItem[commitItems.size()]);
            message = getCommitHandler().getCommitMessage(message, commitables);
            if (message == null) {
                return SVNCommitInfo.NULL;
            }
            revprops = getCommitHandler().getRevisionProperties(message, commitables, revprops == null ? new SVNProperties() : revprops);
            if (revprops == null) {
                return SVNCommitInfo.NULL;
            }

            Map allCommitables = new TreeMap(SVNCommitUtil.FILE_COMPARATOR);
            repos.setLocation(repos.getRepositoryRoot(true), false);
            Map pathsToExternalsProps = new SVNHashMap();
            for (int i = 0; i < copyPairs.size(); i++) {
                CopyPair source = (CopyPair) copyPairs.get(i);
                File srcFile = new File(source.mySource);
                SVNEntry entry = wcAccess.getVersionedEntry(srcFile, false);
                SVNAdminArea dirArea = null;
                if (entry.isDirectory()) {
                    dirArea = wcAccess.retrieve(srcFile);
                } else {
                    dirArea = wcAccess.retrieve(srcFile.getParentFile());
                }
                

                pathsToExternalsProps.clear();

                SVNCommitUtil.harvestCommitables(allCommitables, dirArea, srcFile, 
                        null, entry, source.myDst, entry.getURL(), true, false, false, null, SVNDepth.INFINITY, 
                        false, null, getCommitParameters(), pathsToExternalsProps);
                
                SVNCommitItem item = (SVNCommitItem) allCommitables.get(srcFile);
                SVNURL srcURL = entry.getSVNURL();

                Map mergeInfo = calculateTargetMergeInfo(srcFile, wcAccess, srcURL,  
                        source.mySourceRevisionNumber, repos, false);
                
                Map wcMergeInfo = SVNPropertiesManager.parseMergeInfo(srcFile, entry, false);
                if (wcMergeInfo != null && mergeInfo != null) {
                    mergeInfo = SVNMergeInfoUtil.mergeMergeInfos(mergeInfo, wcMergeInfo);
                } else if (mergeInfo == null) {
                    mergeInfo = wcMergeInfo;
                }
                if (mergeInfo != null) {
                    String mergeInfoString = SVNMergeInfoUtil.formatMergeInfoToString(mergeInfo); 
                    item.setProperty(SVNProperty.MERGE_INFO, SVNPropertyValue.create(mergeInfoString));
                }
                
                if (!pathsToExternalsProps.isEmpty()) {
                    LinkedList newExternals = new LinkedList(); 
                    for (Iterator pathsIter = pathsToExternalsProps.keySet().iterator(); pathsIter.hasNext();) {
                        File localPath = (File) pathsIter.next();
                        String externalsPropString = (String) pathsToExternalsProps.get(localPath);
                        SVNExternal[] externals = SVNExternal.parseExternals(localPath.getAbsolutePath(), 
                                externalsPropString);
                        boolean introduceVirtualExternalChange = false;
                        newExternals.clear();
                        for (int k = 0; k < externals.length; k++) {
                            File externalWC = new File(localPath, externals[k].getPath());
                            SVNEntry externalEntry = null;
                            try {
                                wcAccess.open(externalWC, false, 0);
                                externalEntry = wcAccess.getVersionedEntry(externalWC, false);
                            } catch (SVNException svne) {
                                if (svne instanceof SVNCancelException) {
                                    throw svne;
                                }
                            } finally {
                                wcAccess.closeAdminArea(externalWC);
                            }
                                
                            SVNRevision externalsWCRevision = SVNRevision.UNDEFINED;
                            if (externalEntry != null) {
                                externalsWCRevision = SVNRevision.create(externalEntry.getRevision());
                            }
                            
                            SVNRevision[] revs = getExternalsHandler().handleExternal(externalWC, 
                                    externals[k].resolveURL(repos.getRepositoryRoot(true), 
                                            externalEntry.getSVNURL()), externals[k].getRevision(), 
                                            externals[k].getPegRevision(), externals[k].getRawValue(), 
                                            externalsWCRevision);
                            if (revs != null && revs[0] == externals[k].getRevision()) {
                                newExternals.add(externals[k].getRawValue());
                            } else if (revs != null) {
                                SVNExternal newExternal = new SVNExternal(externals[k].getPath(), 
                                        externals[k].getUnresolvedUrl(), revs[1], 
                                        revs[0], true, externals[k].isPegRevisionExplicit(), 
                                        externals[k].isNewFormat());
                                
                                newExternals.add(newExternal.toString()); 

                                if (!introduceVirtualExternalChange) {
                                    introduceVirtualExternalChange = true;
                                }
                            }
                        }
                        
                        if (introduceVirtualExternalChange) {
                            String newExternalsProp = "";
                            for (Iterator externalsIter = newExternals.iterator(); externalsIter.hasNext();) {
                                String external = (String) externalsIter.next();
                                newExternalsProp += external + '\n';
                            }
                            
                            SVNCommitItem itemWithExternalsChanges = (SVNCommitItem) allCommitables.get(localPath);
                            if (itemWithExternalsChanges != null) {
                                itemWithExternalsChanges.setProperty(SVNProperty.EXTERNALS, 
                                        SVNPropertyValue.create(newExternalsProp));
                            } else {
                                SVNAdminArea childArea = wcAccess.retrieve(localPath);
                                String relativePath = childArea.getRelativePath(dirArea);
                                String itemURL = SVNPathUtil.append(source.myDst, 
                                        SVNEncodingUtil.uriEncode(relativePath));
                                itemWithExternalsChanges = new SVNCommitItem(localPath, 
                                        SVNURL.parseURIEncoded(itemURL), null, SVNNodeKind.DIR, null, null, 
                                        false, false, true, false, false, false);
                                itemWithExternalsChanges.setProperty(SVNProperty.EXTERNALS, 
                                        SVNPropertyValue.create(newExternalsProp));
                                allCommitables.put(localPath, itemWithExternalsChanges);
                            }
                        }
                    }
                }
            }
            
            commitItems = new ArrayList(allCommitables.values());
            // add parents to commits hash?
            if (makeParents) {
                for (int i = 0; i < newDirs.size(); i++) {
                    String newDirURL = (String) newDirs.get(i);
                    SVNURL url = SVNURL.parseURIEncoded(newDirURL);
                    SVNCommitItem item = new SVNCommitItem(null, url, null, SVNNodeKind.NONE, null, null, true, false, false, false, false, false);
                    commitItems.add(item);
                }
            }            
            commitables = (SVNCommitItem[]) commitItems.toArray(new SVNCommitItem[commitItems.size()]);
            for (int i = 0; i < commitables.length; i++) {
                commitables[i].setWCAccess(wcAccess);
            }
            allCommitables = new TreeMap();
            SVNURL url = SVNCommitUtil.translateCommitables(commitables, allCommitables);
            
            repos = createRepository(url, null, null, true);
            
            SVNCommitMediator mediator = new SVNCommitMediator(allCommitables);
            tmpFiles = mediator.getTmpFiles();

            message = SVNCommitClient.validateCommitMessage(message);

            SVNURL rootURL = repos.getRepositoryRoot(true);
            commitEditor = repos.getCommitEditor(message, null, true, revprops, mediator);
            info = SVNCommitter.commit(tmpFiles, allCommitables, rootURL.getPath(), commitEditor);
            commitEditor = null;
            
        } catch (SVNCancelException cancel) {
            throw cancel;
        } catch (SVNException e) {
            // wrap error message.
            SVNErrorMessage err = e.getErrorMessage().wrap("Commit failed (details follow):");
            SVNErrorManager.error(err, SVNLogType.WC);
        } finally {
            if (tmpFiles != null) {
                for (Iterator files = tmpFiles.iterator(); files.hasNext();) {
                    File file = (File) files.next();
                    SVNFileUtil.deleteFile(file);
                }
            }
            if (commitEditor != null && info == null) {
                // should we hide this exception?
                try {
                    commitEditor.abortEdit();
                } catch (SVNException e) {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, e);
                }
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
    
    private SVNCommitInfo copyReposToRepos(List copyPairs, boolean makeParents, boolean isMove, String message, SVNProperties revprops) throws SVNException {
        List pathInfos = new ArrayList();
        Map pathsMap = new SVNHashMap();
        for (int i = 0; i < copyPairs.size(); i++) {
            CopyPathInfo info = new CopyPathInfo();
            pathInfos.add(info);
        }
        String topURL = ((CopyPair) copyPairs.get(0)).mySource;
        String topDstURL = ((CopyPair) copyPairs.get(0)).myDst;
        for (int i = 1; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            topURL = SVNPathUtil.getCommonPathAncestor(topURL, pair.mySource);
        }
        if (copyPairs.size() == 1) {
            topURL = SVNPathUtil.getCommonPathAncestor(topURL, topDstURL);
        } else {
            topURL = SVNPathUtil.getCommonPathAncestor(topURL, SVNPathUtil.removeTail(topDstURL));
        }
        try {
            SVNURL.parseURIEncoded(topURL);
        } catch (SVNException e) {
            topURL = null;
        }
        if (topURL == null) {
            SVNURL url1 = SVNURL.parseURIEncoded(((CopyPair) copyPairs.get(0)).mySource);
            SVNURL url2 = SVNURL.parseURIEncoded(((CopyPair) copyPairs.get(0)).myDst);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Source and dest appear not to be in the same repository (src: ''{0}''; dst: ''{1}'')", new Object[] {url1, url2});
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        for (int i = 0; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            CopyPathInfo info = (CopyPathInfo) pathInfos.get(i);
            if (pair.mySource.equals(pair.myDst)) {
                info.isResurrection = true;
                if (topURL.equals(pair.mySource)) {
                    topURL = SVNPathUtil.removeTail(topURL);
                }
            }
        }
        SVNRepository topRepos = createRepository(SVNURL.parseURIEncoded(topURL), null, null, true);
        List newDirs = new ArrayList();
        if (makeParents) {
            CopyPair pair = (CopyPair) copyPairs.get(0);
            String relativeDir = SVNPathUtil.getPathAsChild(topURL, SVNPathUtil.removeTail(pair.myDst));
            if (relativeDir != null) {
                relativeDir = SVNEncodingUtil.uriDecode(relativeDir);
                SVNNodeKind kind = topRepos.checkPath(relativeDir, -1);
                while(kind == SVNNodeKind.NONE) {
                    newDirs.add(relativeDir);
                    relativeDir = SVNPathUtil.removeTail(relativeDir);
                    kind = topRepos.checkPath(relativeDir, -1);
                }
            }
        }
        
        String rootURL = topRepos.getRepositoryRoot(true).toString();
        for (int i = 0; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            CopyPathInfo info = (CopyPathInfo) pathInfos.get(i);
            if (!pair.myDst.equals(rootURL) && SVNPathUtil.getPathAsChild(pair.myDst, pair.mySource) != null) {
                info.isResurrection = true;
                // TODO still looks like a bug.
//                if (SVNPathUtil.removeTail(pair.myDst).equals(topURL)) {
                    topURL = SVNPathUtil.removeTail(topURL);
//                }
            }
        }
        
        topRepos.setLocation(SVNURL.parseURIEncoded(topURL), false);
        long latestRevision = topRepos.getLatestRevision();
        
        for (int i = 0; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            CopyPathInfo info = (CopyPathInfo) pathInfos.get(i);
            pair.mySourceRevisionNumber = getRevisionNumber(pair.mySourceRevision, topRepos, null);
            info.mySourceRevisionNumber = pair.mySourceRevisionNumber;
            
            SVNRepositoryLocation[] locations = getLocations(SVNURL.parseURIEncoded(pair.mySource), null, topRepos, pair.mySourcePegRevision, pair.mySourceRevision, SVNRevision.UNDEFINED);
            pair.mySource = locations[0].getURL().toString();
            String srcRelative = SVNPathUtil.getPathAsChild(topURL, pair.mySource);
            if (srcRelative != null) {
                srcRelative = SVNEncodingUtil.uriDecode(srcRelative);
            } else {
                srcRelative = "";
            }
            String dstRelative = SVNPathUtil.getPathAsChild(topURL, pair.myDst);
            if (dstRelative != null) {
                dstRelative = SVNEncodingUtil.uriDecode(dstRelative);
            } else {
                dstRelative = "";
            }
            if ("".equals(srcRelative) && isMove) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot move URL ''{0}'' into itself", SVNURL.parseURIEncoded(pair.mySource));
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            info.mySourceKind = topRepos.checkPath(srcRelative, pair.mySourceRevisionNumber);
            if (info.mySourceKind == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, 
                        "Path ''{0}'' does not exist in revision {1}", new Object[] {SVNURL.parseURIEncoded(pair.mySource), new Long(pair.mySourceRevisionNumber)});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            SVNNodeKind dstKind = topRepos.checkPath(dstRelative, latestRevision);
            if (dstKind != SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, 
                        "Path ''{0}'' already exists", dstRelative);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            info.mySource = pair.mySource;
            info.mySourcePath = srcRelative;
            info.myDstPath = dstRelative;
        }
        
        List paths = new ArrayList(copyPairs.size() * 2);
        List commitItems = new ArrayList(copyPairs.size() * 2);
        if (makeParents) {
            for (Iterator newDirsIter = newDirs.iterator(); newDirsIter.hasNext();) {
                String dirPath = (String) newDirsIter.next();
                SVNURL itemURL = SVNURL.parseURIEncoded(SVNPathUtil.append(topURL, dirPath));
                SVNCommitItem item = new SVNCommitItem(null, itemURL, null, SVNNodeKind.NONE, null, null, true, false, false, false, false, false);
                commitItems.add(item);
            }            
        }
        
        for (Iterator infos = pathInfos.iterator(); infos.hasNext();) {
            CopyPathInfo info = (CopyPathInfo) infos.next();
            SVNURL itemURL = SVNURL.parseURIEncoded(SVNPathUtil.append(topURL, info.myDstPath));
            SVNCommitItem item = new SVNCommitItem(null, itemURL, null, SVNNodeKind.NONE, null, null, true, false, false, false, false, false);
            commitItems.add(item);
            pathsMap.put(info.myDstPath, info);
            if (isMove && !info.isResurrection) {
                itemURL = SVNURL.parseURIEncoded(SVNPathUtil.append(topURL, info.mySourcePath));
                item = new SVNCommitItem(null, itemURL, null, SVNNodeKind.NONE, null, null, false, true, false, false, false, false);
                commitItems.add(item);
                pathsMap.put(info.mySourcePath, info);
            }
        }

        if (makeParents) {
            for (Iterator newDirsIter = newDirs.iterator(); newDirsIter.hasNext();) {
                String dirPath = (String) newDirsIter.next();
                CopyPathInfo info = new CopyPathInfo();
                info.myDstPath = dirPath;
                info.isDirAdded = true;
                paths.add(info.myDstPath);
                pathsMap.put(dirPath, info);
            }
        }
        
        for (Iterator infos = pathInfos.iterator(); infos.hasNext();) {
            CopyPathInfo info = (CopyPathInfo) infos.next();
            Map mergeInfo = calculateTargetMergeInfo(null, null, SVNURL.parseURIEncoded(info.mySource), 
                    info.mySourceRevisionNumber, topRepos, false);
            if (mergeInfo != null) {
                info.myMergeInfoProp = SVNMergeInfoUtil.formatMergeInfoToString(mergeInfo);
            }
            paths.add(info.myDstPath);
            if (isMove && !info.isResurrection) {
                paths.add(info.mySourcePath);
            }
        }
        
        SVNCommitItem[] commitables = (SVNCommitItem[]) commitItems.toArray(new SVNCommitItem[commitItems.size()]);
        message = getCommitHandler().getCommitMessage(message, commitables);
        if (message == null) {
            return SVNCommitInfo.NULL;
        }
        message = SVNCommitClient.validateCommitMessage(message);

        revprops = getCommitHandler().getRevisionProperties(message, commitables, revprops == null ? new SVNProperties() : revprops);
        if (revprops == null) {
            return SVNCommitInfo.NULL;
        }
        
        // now do real commit.
        ISVNEditor commitEditor = topRepos.getCommitEditor(message, null, true, revprops, null);
        ISVNCommitPathHandler committer = new CopyCommitPathHandler(pathsMap, isMove);

        SVNCommitInfo result = null;
        try {
            SVNCommitUtil.driveCommitEditor(committer, paths, commitEditor, latestRevision);
            result = commitEditor.closeEdit();
        } catch (SVNCancelException cancel) {
            throw cancel;
        } catch (SVNException e) {
            SVNErrorMessage err = e.getErrorMessage().wrap("Commit failed (details follow):");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        } finally {
            if (commitEditor != null && result == null) {
                try {
                    commitEditor.abortEdit();
                } catch (SVNException e) {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, e);
                }
            }
        }
        if (result != null && result.getNewRevision() >= 0) { 
            dispatchEvent(SVNEventFactory.createSVNEvent(null, SVNNodeKind.NONE, null, result.getNewRevision(), SVNEventAction.COMMIT_COMPLETED, null, null, null), ISVNEventHandler.UNKNOWN);
        }
        return result != null ? result : SVNCommitInfo.NULL;
    }
    
    private void copyReposToWC(List copyPairs, boolean makeParents) throws SVNException {
        for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
            CopyPair pair = (CopyPair) pairs.next();
            SVNRepositoryLocation[] locations = getLocations(SVNURL.parseURIEncoded(pair.mySource), null, null, pair.mySourcePegRevision, pair.mySourceRevision, SVNRevision.UNDEFINED);
            // new
            String actualURL = locations[0].getURL().toString();
            String originalSource = pair.mySource;
            pair.mySource = actualURL;
            pair.myOriginalSource = originalSource;
        }
        // get src and dst ancestors.
        String topDst = ((CopyPair) copyPairs.get(0)).myDst;
        if (copyPairs.size() > 1) {
            topDst = SVNPathUtil.removeTail(topDst);
        }
        String topSrc = ((CopyPair) copyPairs.get(0)).mySource;
        for(int i = 1; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            topSrc = SVNPathUtil.getCommonPathAncestor(topSrc, pair.mySource);
        }
        if (copyPairs.size() == 1) {
            topSrc = SVNPathUtil.removeTail(topSrc);
        }
        SVNRepository topSrcRepos = createRepository(SVNURL.parseURIEncoded(topSrc), null, null, false);
        try {
            for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
                CopyPair pair = (CopyPair) pairs.next();
                pair.mySourceRevisionNumber = getRevisionNumber(pair.mySourceRevision, topSrcRepos, null);
            }
            String reposPath = topSrcRepos.getLocation().toString(); 
            for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
                CopyPair pair = (CopyPair) pairs.next();
                String relativePath = SVNPathUtil.getPathAsChild(reposPath, pair.mySource);
                relativePath = SVNEncodingUtil.uriDecode(relativePath);
                SVNNodeKind kind = topSrcRepos.checkPath(relativePath, pair.mySourceRevisionNumber);
                if (kind == SVNNodeKind.NONE) {
                    SVNErrorMessage err = null;
                    if (pair.mySourceRevisionNumber >= 0) {
                        err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' not found in revision {1}",
                                new Object[] {SVNURL.parseURIEncoded(pair.mySource), new Long(pair.mySourceRevisionNumber)});
                    } else {
                        err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' not found in head revision", 
                                SVNURL.parseURIEncoded(pair.mySource));
                    }
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                pair.mySourceKind = kind;
                SVNFileType dstType = SVNFileType.getType(new File(pair.myDst));
                if (dstType != SVNFileType.NONE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "Path ''{0}'' already exists", new File(pair.myDst));
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                String dstParent = SVNPathUtil.removeTail(pair.myDst);
                SVNFileType dstParentFileType = SVNFileType.getType(new File(dstParent));
                if (makeParents && dstParentFileType == SVNFileType.NONE) {
                    // create parents.
                    addLocalParents(new File(dstParent), getEventDispatcher());
                } else if (dstParentFileType != SVNFileType.DIRECTORY) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "Path ''{0}'' is not a directory", dstParent);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            SVNWCAccess dstAccess = createWCAccess();
            try {
                dstAccess.probeOpen(new File(topDst), true, 0);
                for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
                    CopyPair pair = (CopyPair) pairs.next();
                    SVNEntry dstEntry = dstAccess.getEntry(new File(pair.myDst), false);
                    if (dstEntry != null && !dstEntry.isDirectory() && !dstEntry.isScheduledForDeletion()) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                                "Entry for ''{0}'' exists (though the working file is missing)", new File(pair.myDst)); 
                        SVNErrorManager.error(err, SVNLogType.WC);
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
                String dstParent = topDst;
                if (copyPairs.size() == 1) {
                    dstParent = SVNPathUtil.removeTail(topDst);
                }
                try {
                    dstUUID = getUUIDFromPath(dstAccess, new File(dstParent));
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
        
        if (pair.mySourceKind == SVNNodeKind.DIR) {
            // do checkout
            String srcURL = pair.myOriginalSource;
            SVNURL url = SVNURL.parseURIEncoded(srcURL);
            SVNUpdateClient updateClient = new SVNUpdateClient(getRepositoryPool(), getOptions());
            updateClient.setEventHandler(getEventDispatcher());

            File dstFile = new File(pair.myDst);
            SVNRevision srcRevision = pair.mySourceRevision;
            SVNRevision srcPegRevision = pair.mySourcePegRevision;
            updateClient.doCheckout(url, dstFile, srcPegRevision, srcRevision, SVNDepth.INFINITY, false);
            
            if (sameRepositories) {
                url = SVNURL.parseURIEncoded(pair.mySource);
                
                SVNAdminArea dstArea = dstAccess.open(dstFile, true, SVNWCAccess.INFINITE_DEPTH);
                SVNEntry dstRootEntry = dstArea.getEntry(dstArea.getThisDirName(), false);
                if (srcRevision == SVNRevision.HEAD) {
                    srcRevNum = dstRootEntry.getRevision();
                }
                SVNAdminArea dir = dstAccess.getAdminArea(dstFile.getParentFile());
                SVNWCManager.add(dstFile, dir, url, srcRevNum);
                Map srcMergeInfo = calculateTargetMergeInfo(null, null, url, srcRevNum, topSrcRepos, false);
                extendWCMergeInfo(dstFile, dstRootEntry, srcMergeInfo, dstAccess);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Source URL ''{0}'' is from foreign repository; leaving it as a disjoint WC", url);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        } else if (pair.mySourceKind == SVNNodeKind.FILE) {
            String srcURL = pair.mySource;
            SVNURL url = SVNURL.parseURIEncoded(srcURL);
            
            File dst = new File(pair.myDst);
            SVNAdminArea dir = dstAccess.getAdminArea(dst.getParentFile());
            File tmpFile = SVNAdminUtil.createTmpFile(dir);
            String path = getPathRelativeToRoot(null, url, null, null, topSrcRepos);
            SVNProperties props = new SVNProperties();
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
            SVNWCManager.addRepositoryFile(dir, dst.getName(), null, tmpFile, props, null, 
                    sameRepositories ? pair.mySource : null, 
                    sameRepositories ? srcRevNum : -1);

            SVNEntry entry = dstAccess.getEntry(dst, false);
            Map mergeInfo = calculateTargetMergeInfo(null, null, url, srcRevNum, topSrcRepos, false);
            extendWCMergeInfo(dst, entry, mergeInfo, dstAccess);

            SVNEvent event = SVNEventFactory.createSVNEvent(dst, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNEventAction.ADD, null, null, null);
            dstAccess.handleEvent(event);

            sleepForTimeStamp();
        }
    }

    private void copyWCToWC(List copyPairs, boolean isMove, boolean makeParents) throws SVNException {
        for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
            CopyPair pair = (CopyPair) pairs.next();
            SVNFileType srcFileType = SVNFileType.getType(new File(pair.mySource));
            if (srcFileType == SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, 
                        "Path ''{0}'' does not exist", new File(pair.mySource));
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            SVNFileType dstFileType = SVNFileType.getType(new File(pair.myDst));
            if (dstFileType != SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, 
                        "Path ''{0}'' already exists", new File(pair.myDst));
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            File dstParent = new File(SVNPathUtil.removeTail(pair.myDst));
            pair.myBaseName = SVNPathUtil.tail(pair.myDst);
            SVNFileType dstParentFileType = SVNFileType.getType(dstParent);
            if (makeParents && dstParentFileType == SVNFileType.NONE) {
                // create parents.
                addLocalParents(dstParent, getEventDispatcher());
            } else if (dstParentFileType != SVNFileType.DIRECTORY) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, 
                        "Path ''{0}'' is not a directory", dstParent);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        if (isMove) {
            moveWCToWC(copyPairs);
        } else {
            copyWCToWC(copyPairs);
        }
    }

    private void copyDisjointWCToWC(File nestedWC) throws SVNException {
        SVNFileType nestedWCType = SVNFileType.getType(nestedWC);
        if (nestedWCType != SVNFileType.DIRECTORY) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                    "This kind of copy can be run on a root of a disjoint wc directory only");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        nestedWC = new File(nestedWC.getAbsolutePath().replace(File.separatorChar, '/'));
        File nestedWCParent = nestedWC.getParentFile();
        if (nestedWCParent == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                    "{0} seems to be not a disjoint wc since it has no parent", nestedWC);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SVNWCAccess parentWCAccess = createWCAccess();
        SVNWCAccess nestedWCAccess = createWCAccess();
        try {
            SVNAdminArea parentArea = parentWCAccess.open(nestedWCParent, true, 0);
            
            SVNEntry srcEntryInParent = parentWCAccess.getEntry(nestedWC, false);
            if (srcEntryInParent != null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, 
                        "Entry ''{0}'' already exists in parent directory", nestedWC.getName());
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            SVNAdminArea nestedArea = nestedWCAccess.open(nestedWC, false, SVNWCAccess.INFINITE_DEPTH);

            SVNEntry nestedWCThisEntry = nestedWCAccess.getVersionedEntry(nestedWC, false);
            SVNEntry parentThisEntry = parentWCAccess.getVersionedEntry(nestedWCParent, false);
            
            // uuids may be identical while it might be absolutely independent repositories.
            // subversion uses repos roots comparison for local copies, and uuids comparison for 
            // operations involving ra access. so, I believe we should act similarly here.

            if (nestedWCThisEntry.getRepositoryRoot() != null && parentThisEntry.getRepositoryRoot() != null && 
                    !nestedWCThisEntry.getRepositoryRoot().equals(parentThisEntry.getRepositoryRoot())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE,                        
                        "Cannot copy to ''{0}'', as it is not from repository ''{1}''; it is from ''{2}''",
                        new Object[] { nestedWCParent, nestedWCThisEntry.getRepositoryRootURL(), 
                        parentThisEntry.getRepositoryRootURL() });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            
            if (parentThisEntry.isScheduledForDeletion()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE, 
                        "Cannot copy to ''{0}'', as it is scheduled for deletion", nestedWCParent); 
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            if (nestedWCThisEntry.isScheduledForDeletion()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE, 
                        "Cannot copy ''{0}'', as it is scheduled for deletion", nestedWC); 
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            
            SVNURL nestedWCReposRoot = getReposRoot(nestedWC, null, SVNRevision.WORKING, nestedArea, 
                    nestedWCAccess);
            String nestedWCPath = getPathRelativeToRoot(nestedWC, null, nestedWCReposRoot, nestedWCAccess, null);
            
            SVNURL parentReposRoot = getReposRoot(nestedWCParent, null, SVNRevision.WORKING, parentArea, 
                    parentWCAccess);
            String parentPath = getPathRelativeToRoot(nestedWCParent, null, parentReposRoot, parentWCAccess, null);
            
            if (SVNPathUtil.isAncestor(nestedWCPath, parentPath)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                        "Cannot copy path ''{0}'' into its own child ''{1}",
                        new Object[] { nestedWCPath, parentPath });
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            if ((nestedWCThisEntry.isScheduledForAddition() && !nestedWCThisEntry.isCopied()) || 
                    nestedWCThisEntry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, 
                        "Cannot copy or move ''{0}'': it is not in repository yet; " +
                        "try committing first", nestedWC);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            boolean[] extend = { false };
            Map mergeInfo = fetchMergeInfoForPropagation(nestedWC, extend, nestedWCAccess); 

            copyDisjointDir(nestedWC, parentWCAccess, nestedWCParent);
            parentWCAccess.probeTry(nestedWC, true, SVNWCAccess.INFINITE_DEPTH);
            propagateMegeInfo(nestedWC, mergeInfo, extend, parentWCAccess);

        } finally {
            parentWCAccess.close();
            nestedWCAccess.close();
            sleepForTimeStamp();
        }
    }
    
    private void copyDisjointDir(File nestedWC, SVNWCAccess parentAccess, File nestedWCParent) throws SVNException {
        SVNWCClient wcClient = new SVNWCClient((ISVNAuthenticationManager) null, null);
        wcClient.setEventHandler(getEventDispatcher());
        wcClient.doCleanup(nestedWC);

        SVNWCAccess nestedWCAccess = createWCAccess();
        SVNAdminArea dir = null;
        String copyFromURL = null;
        long copyFromRevision = -1;
        try {
            dir = nestedWCAccess.open(nestedWC, true, SVNWCAccess.INFINITE_DEPTH);
            SVNEntry nestedWCThisEntry = nestedWCAccess.getVersionedEntry(nestedWC, false);            
            postCopyCleanup(dir);
            if (nestedWCThisEntry.isCopied()) {
                if (nestedWCThisEntry.getCopyFromURL() != null) {
                    copyFromURL = nestedWCThisEntry.getCopyFromURL();
                    copyFromRevision = nestedWCThisEntry.getCopyFromRevision();
                }

                Map attributes = new SVNHashMap();
                attributes.put(SVNProperty.URL, copyFromURL);
                dir.modifyEntry(dir.getThisDirName(), attributes, true, false);
            } else {
                copyFromURL = nestedWCThisEntry.getURL();
                copyFromRevision = nestedWCThisEntry.getRevision();
            }
        } finally {
            nestedWCAccess.close();
        }
        SVNWCManager.add(nestedWC, parentAccess.getAdminArea(nestedWCParent), 
                SVNURL.parseURIEncoded(copyFromURL), copyFromRevision);
    }

    private void copyWCToWC(List pairs) throws SVNException {
        // find common ancestor for all dsts.
        String dstParentPath = null;
        for (Iterator ps = pairs.iterator(); ps.hasNext();) {
            CopyPair pair = (CopyPair) ps.next();
            String dstPath = pair.myDst;
            if (dstParentPath == null) {
                dstParentPath = SVNPathUtil.removeTail(pair.myDst);
            }
            dstParentPath = SVNPathUtil.getCommonPathAncestor(dstParentPath, dstPath);
        }
        SVNWCAccess dstAccess = createWCAccess();
        try {
            dstAccess.open(new File(dstParentPath), true, 0);
            for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                CopyPair pair = (CopyPair) ps.next();
                checkCancelled();
                SVNWCAccess srcAccess = null;
                String srcParent = SVNPathUtil.removeTail(pair.mySource);
                SVNFileType srcType = SVNFileType.getType(new File(pair.mySource));
                try {
                    if (srcParent.equals(dstParentPath)) {
                        if (srcType == SVNFileType.DIRECTORY) {
                            srcAccess = createWCAccess();
                            srcAccess.open(new File(pair.mySource), false, -1);
                        } else {
                            srcAccess = dstAccess;
                        }
                    } else {
                        try {
                            srcAccess = createWCAccess();
                            srcAccess.open(new File(srcParent), false, srcType == SVNFileType.DIRECTORY ? -1 : 0);
                        } catch (SVNException e) {
                            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                                srcAccess = null;
                            } else {
                                throw e;
                            }
                        }
                    }
                    // do real copy.
                    File sourceFile = new File(pair.mySource);
                    copyFiles(sourceFile, new File(dstParentPath), dstAccess, pair.myBaseName);
                    if (srcAccess != null) {
                        propagateMegeInfo(sourceFile, new File(pair.myDst), srcAccess, dstAccess);
                    }
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
            File srcParent = new File(SVNPathUtil.removeTail(pair.mySource));
            File dstParent = new File(SVNPathUtil.removeTail(pair.myDst));
            File sourceFile = new File(pair.mySource);
            SVNFileType srcType = SVNFileType.getType(sourceFile);
            SVNWCAccess srcAccess = createWCAccess();
            SVNWCAccess dstAccess = null;
            try {
                srcAccess.open(srcParent, true, srcType == SVNFileType.DIRECTORY ? -1 : 0);
                if (srcParent.equals(dstParent)) {
                    dstAccess = srcAccess;
                } else {
                    String srcParentPath = srcParent.getAbsolutePath().replace(File.separatorChar, '/');
                    srcParentPath = SVNPathUtil.validateFilePath(srcParentPath);
                    String dstParentPath = dstParent.getAbsolutePath().replace(File.separatorChar, '/');
                    dstParentPath = SVNPathUtil.validateFilePath(dstParentPath);
                    if (srcType == SVNFileType.DIRECTORY && 
                            SVNPathUtil.isAncestor(srcParentPath, dstParentPath)) {
                        dstAccess = srcAccess;
                    } else {
                        dstAccess = createWCAccess();
                        dstAccess.open(dstParent, true, 0);
                    }
                }
                copyFiles(sourceFile, dstParent, dstAccess, pair.myBaseName);
                propagateMegeInfo(sourceFile, new File(pair.myDst), srcAccess, dstAccess);
                // delete src.
                SVNWCManager.delete(srcAccess, srcAccess.getAdminArea(srcParent), sourceFile, true, true);
            } finally {
                if (dstAccess != null && dstAccess != srcAccess) {
                    dstAccess.close();
                }
                srcAccess.close();
            }
        }
        sleepForTimeStamp();
    }

    private void propagateMegeInfo(File src, File dst, SVNWCAccess srcAccess, SVNWCAccess dstAccess) throws SVNException {
        boolean[] extend = { false };
        Map mergeInfo = fetchMergeInfoForPropagation(src, extend, srcAccess);
        propagateMegeInfo(dst, mergeInfo, extend, dstAccess);
    }

    Map fetchMergeInfoForPropagation(File src, boolean[] extend, SVNWCAccess srcAccess) throws SVNException {
        SVNEntry entry = srcAccess.getVersionedEntry(src, false);
        if (entry.getSchedule() == null || (entry.isScheduledForAddition() && entry.isCopied())) {
            Map mergeInfo = calculateTargetMergeInfo(src, srcAccess, entry.getSVNURL(), 
                    entry.getRevision(), null, true);
            if (mergeInfo == null) {
                mergeInfo = new TreeMap();
            }
            extend[0] = true;
            return mergeInfo;
        }
        
        Map mergeInfo = SVNPropertiesManager.parseMergeInfo(src, entry, false);
        if (mergeInfo == null) {
            mergeInfo = new TreeMap();
        }
        extend[0] = false;
        return mergeInfo;
    }
    
    private void propagateMegeInfo(File dst, Map mergeInfo, boolean[] extend, SVNWCAccess dstAccess) throws SVNException {
        if (extend[0]) {
            SVNEntry dstEntry = dstAccess.getEntry(dst, false);
            extendWCMergeInfo(dst, dstEntry, mergeInfo, dstAccess);
            return;
        }
        SVNPropertiesManager.recordWCMergeInfo(dst, mergeInfo, dstAccess);
    }
    
    private void copyFiles(File src, File dstParent, SVNWCAccess dstAccess, String dstName) throws SVNException {
        SVNWCAccess srcAccess = createWCAccess();
        try {
            srcAccess.probeOpen(src, false, -1);
            SVNEntry dstEntry = dstAccess.getVersionedEntry(dstParent, false);
            SVNEntry srcEntry = srcAccess.getVersionedEntry(src, false);
            
            if (srcEntry.getRepositoryRoot() != null && dstEntry.getRepositoryRoot() != null && 
                    !srcEntry.getRepositoryRoot().equals(dstEntry.getRepositoryRoot())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE,                        
                        "Cannot copy to ''{0}'', as it is not from repository ''{1}''; it is from ''{2}''",
                        new Object[] {dstParent, srcEntry.getRepositoryRootURL(), dstEntry.getRepositoryRootURL()});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (dstEntry.isScheduledForDeletion()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE, 
                        "Cannot copy to ''{0}'', as it is scheduled for deletion", dstParent); 
                SVNErrorManager.error(err, SVNLogType.WC);
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
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNEntry dstEntry = dstAccess.getEntry(dst, false);
        if (dstEntry != null && !dstEntry.isScheduledForDeletion()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "There is already a versioned item ''{0}''", dst);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNEntry srcEntry = srcAccess.getVersionedEntry(src, false);
        if ((srcEntry.isScheduledForAddition() && !srcEntry.isCopied()) || srcEntry.getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "Cannot copy or move ''{0}'': it is not in repository yet; " +
            		"try committing first", src);
            SVNErrorManager.error(err, SVNLogType.WC);
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
    
    private void copyDirAdm(File src, SVNWCAccess srcAccess, SVNWCAccess dstAccess, File dstParent, 
            String dstName) throws SVNException {
        File dst = new File(dstParent, dstName);
        SVNEntry srcEntry = srcAccess.getVersionedEntry(src, false);
        if ((srcEntry.isScheduledForAddition() && !srcEntry.isCopied()) || srcEntry.getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, 
                    "Cannot copy or move ''{0}'': it is not in repository yet; " +
                    "try committing first", src);
            SVNErrorManager.error(err, SVNLogType.WC);
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

                Map attributes = new SVNHashMap();
                attributes.put(SVNProperty.URL, copyFromURL);
                dir.modifyEntry(dir.getThisDirName(), attributes, true, false);
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
                    copyAddedDirAdm(fsEntry, srcAccess, dst, dstParentAccess, name, entry != null);
                } else if (fsEntry.isFile()) {
                    copyAddedFileAdm(fsEntry, dstParentAccess, dst, name, entry != null);
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
            wcClient.doAdd(path, false, false, true, SVNDepth.EMPTY, true, true);
        } catch (SVNException e) {
            if (created) {
                SVNFileUtil.deleteAll(path, true);
            }
            throw e;
        }
    }

    private void extendWCMergeInfo(File path, SVNEntry entry, Map mergeInfo, SVNWCAccess access) throws SVNException {
        Map wcMergeInfo = SVNPropertiesManager.parseMergeInfo(path, entry, false);
        if (wcMergeInfo != null && mergeInfo != null) {
            wcMergeInfo = SVNMergeInfoUtil.mergeMergeInfos(wcMergeInfo, mergeInfo);
        } else if (wcMergeInfo == null) {
            wcMergeInfo = mergeInfo;
        }
        SVNPropertiesManager.recordWCMergeInfo(path, wcMergeInfo, access);
    }
    
    private Map calculateTargetMergeInfo(File srcFile, SVNWCAccess access, SVNURL srcURL, long srcRevision, 
            SVNRepository repository, boolean noReposAccess) throws SVNException {
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
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                            "Entry for ''{0}'' has no URL", srcFile);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        } else {
            url = srcURL;
        }

        Map targetMergeInfo = null;
        if (!isLocallyAdded) {
            String mergeInfoPath = null;
            if (!noReposAccess) {
                // TODO reparent repository if needed and then ensure that repository has the same location as before that call.
                mergeInfoPath = getPathRelativeToRoot(null, url, 
                        entry != null ? entry.getRepositoryRootURL() : null, access, repository);
                targetMergeInfo = getReposMergeInfo(repository, mergeInfoPath, srcRevision, 
                		SVNMergeInfoInheritance.INHERITED, true);
            } else {
                targetMergeInfo = getWCMergeInfo(srcFile, entry, null, SVNMergeInfoInheritance.INHERITED, false, 
                        new boolean[1]);
            }
        } 
        return targetMergeInfo;
    }
    
    private static class CopyCommitPathHandler implements ISVNCommitPathHandler {
        
        private Map myPathInfos;
        private boolean myIsMove;
        
        public CopyCommitPathHandler(Map pathInfos, boolean isMove) {
            myPathInfos = pathInfos;
            myIsMove = isMove;
        }
        
        public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
            CopyPathInfo pathInfo = (CopyPathInfo) myPathInfos.get(commitPath);
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
                    if (commitPath.equals(pathInfo.mySourcePath)) {
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
                if (pathInfo.mySourceKind == SVNNodeKind.DIR) {
                    commitEditor.addDir(commitPath, pathInfo.mySourcePath, pathInfo.mySourceRevisionNumber);
                    if (pathInfo.myMergeInfoProp != null) {
                        commitEditor.changeDirProperty(SVNProperty.MERGE_INFO, SVNPropertyValue.create(pathInfo.myMergeInfoProp));
                    }
                    closeDir = true;
                } else {
                    commitEditor.addFile(commitPath, pathInfo.mySourcePath, pathInfo.mySourceRevisionNumber);
                    if (pathInfo.myMergeInfoProp != null) {
                        commitEditor.changeFileProperty(commitPath, SVNProperty.MERGE_INFO, SVNPropertyValue.create(pathInfo.myMergeInfoProp));
                    }
                    commitEditor.closeFile(commitPath, null);
                }
            }
            return closeDir;
        }
    }

    private static class CopyPathInfo {
        public boolean isDirAdded;
        public boolean isResurrection;
        
        public SVNNodeKind mySourceKind;
        
        public String mySource;
        public String mySourcePath;
        public String myDstPath;
        
        public String myMergeInfoProp;
        public long mySourceRevisionNumber;
    }
    
    private static class CopyPair {
        
        public String mySource;
        public String myOriginalSource;
        
        public SVNNodeKind mySourceKind;
        
        public SVNRevision mySourceRevision;
        public SVNRevision mySourcePegRevision;
        public long mySourceRevisionNumber;

        public String myBaseName;
        public String myDst;
        
        public void setSourceRevisions(SVNRevision pegRevision, SVNRevision revision) {
            if (pegRevision == SVNRevision.UNDEFINED) {
                if (SVNPathUtil.isURL(mySource)) {
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
