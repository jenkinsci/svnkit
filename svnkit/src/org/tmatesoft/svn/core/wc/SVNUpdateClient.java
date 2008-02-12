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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.ISVNFileFetcher;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExportEditor;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.internal.wc.SVNUpdateEditor;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * This class provides methods which allow to check out, update, switch and relocate a
 * Working Copy as well as export an unversioned directory or file from a repository.
 * 
 * <p>
 * Here's a list of the <b>SVNUpdateClient</b>'s methods 
 * matched against corresponing commands of the SVN command line 
 * client:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNKit</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doCheckout()</td><td>'svn checkout'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doUpdate()</td><td>'svn update'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doSwitch()</td><td>'svn switch'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doRelocate()</td><td>'svn switch --relocate oldURL newURL'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doExport()</td><td>'svn export'</td>
 * </tr>
 * </table>
 * 
 * @version 1.1.1
 * @author  TMate Software Ltd.
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNUpdateClient extends SVNBasicClient {

    /**
     * Constructs and initializes an <b>SVNUpdateClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNUpdateClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNUpdateClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the 
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     * 
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver     
     */
    public SVNUpdateClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    public SVNUpdateClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }
    
    /**
     * Brings the Working Copy item up-to-date with repository changes at the specified
     * revision.
     * 
     * <p>
     * As a revision <b>SVNRevision</b>'s pre-defined constant fields can be used. For example,
     * to update the Working Copy to the latest revision of the repository use 
     * {@link SVNRevision#HEAD HEAD}.
     * 
     * @param  file			the Working copy item to be updated
     * @param  revision		the desired revision against which the item will be updated 
     * @param  recursive	if <span class="javakeyword">true</span> and <code>file</code> is
     * 						a directory then the entire tree will be updated, otherwise if 
     * 						<span class="javakeyword">false</span> - only items located immediately
     * 						in the directory itself
     * @return				the revision number to which <code>file</code> was updated to
     * @throws SVNException 
     */
    public long doUpdate(File file, SVNRevision revision, boolean recursive) throws SVNException {
        return doUpdate(file, revision, recursive, false);
    }
    
    public long doUpdate(File file, SVNRevision revision, boolean recursive, boolean force) throws SVNException {
        return doUpdate(file, revision, SVNDepth.fromRecurse(recursive), force);
    }    
    
    public long[] doUpdate(File[] paths, SVNRevision revision, SVNDepth depth, boolean force) throws SVNException {
        if (paths == null) {
            return new long[0];
        }
        Collection revisions = new LinkedList();
        for (int i = 0; i < paths.length; i++) {
            checkCancelled();
            File path = paths[i];
            try {
                setEventPathPrefix("");
                handlePathListItem(path);
                long rev = doUpdate(path, revision, depth, force);
                revisions.add(new Long(rev));
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                    SVNEvent skipEvent = SVNEventFactory.createSVNEvent(path, SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, SVNEventAction.SKIP, SVNEventAction.UPDATE_COMPLETED, null, null);
                    dispatchEvent(skipEvent);
                    revisions.add(new Long(-1));
                    continue;
                }
                throw svne;
            } finally {
                setEventPathPrefix(null);
            }
        }
        sleepForTimeStamp();
        long[] result = new long[revisions.size()];
        int i = 0;
        for (Iterator revs = revisions.iterator(); revs.hasNext();) {
            Long value = (Long) revs.next();
            result[i++] = value.longValue();
        }
        return result;
    }

    public long doUpdate(File file, SVNRevision revision, SVNDepth depth, boolean force) throws SVNException {
        depth = depth == null ? SVNDepth.UNKNOWN : depth;
        file = file.getAbsoluteFile();
        SVNWCAccess wcAccess = createWCAccess();
        SVNAdminAreaInfo adminInfo = null;
        int admOpenDepth = SVNWCAccess.INFINITE_DEPTH;
        if (depth == SVNDepth.EMPTY || depth == SVNDepth.FILES) {
            admOpenDepth = 0;
        }
        try {
            adminInfo = wcAccess.openAnchor(file, true, admOpenDepth);
            SVNAdminArea anchorArea = adminInfo.getAnchor();

            SVNEntry entry = anchorArea.getEntry(anchorArea.getThisDirName(), false);
            SVNURL url = entry.getSVNURL();
            if (url == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                        "Entry ''{0}'' has no URL", anchorArea.getRoot());
                SVNErrorManager.error(err);
            }
  
            final SVNReporter reporter = new SVNReporter(adminInfo, file, true, depth, getDebugLog());
            String[] preservedExts = getOptions().getPreservedConflictFileExtensions();
            
            SVNRepository repos = createRepository(url, true);
            
            String target = "".equals(adminInfo.getTargetName()) ? null : adminInfo.getTargetName();
            long revNumber = getRevisionNumber(revision, repos, file);
            SVNURL reposRoot = repos.getRepositoryRoot(true);
            wcAccess.setRepositoryRoot(file, reposRoot);
            
            final SVNRepository repos2 = createRepository(reposRoot, false);
            ISVNFileFetcher fileFetcher = new ISVNFileFetcher() {
                public long fetchFile(String path, long revision, OutputStream os, SVNProperties properties) throws SVNException {
                    return repos2.getFile(path, revision, properties, os);
                }
            };
            SVNUpdateEditor editor = new SVNUpdateEditor(adminInfo, null,                                                       
                                                         force, depth, preservedExts,
                                                         fileFetcher);
            
            repos.update(revNumber, target, depth, true, reporter, 
                    SVNCancellableEditor.newInstance(editor, this, getDebugLog()));

            if (editor.getTargetRevision() >= 0) {
                if ((depth == SVNDepth.INFINITY || depth == SVNDepth.UNKNOWN) && !isIgnoreExternals()) {
                    handleExternals(adminInfo.getAnchor().getRoot(), 
                            adminInfo.getOldExternals(), adminInfo.getNewExternals(), adminInfo.getDepths(), url, reposRoot, depth, false, true);
                }
                dispatchEvent(SVNEventFactory.createSVNEvent(adminInfo.getTarget().getRoot(), SVNNodeKind.NONE, null, editor.getTargetRevision(), SVNEventAction.UPDATE_COMPLETED, null, null, null));
            }
            return editor.getTargetRevision();
        } finally {
            wcAccess.close();
            sleepForTimeStamp();
        }
    }
    
    /**
     * Updates the Working Copy item to mirror a new URL. 
     * 
     * <p>
     * As a revision <b>SVNRevision</b>'s pre-defined constant fields can be used. For example,
     * to update the Working Copy to the latest revision of the repository use 
     * {@link SVNRevision#HEAD HEAD}.
     * 
     * <p>
     * Calling this method is equivalent to 
     * <code>doSwitch(file, url, SVNRevision.UNDEFINED, revision, recursive)</code>.
     * 
     * @param  file			the Working copy item to be switched
     * @param  url			the repository location as a target against which the item will 
     * 						be switched
     * @param  revision		the desired revision of the repository target   
     * @param  recursive	if <span class="javakeyword">true</span> and <code>file</code> is
     * 						a directory then the entire tree will be updated, otherwise if 
     * 						<span class="javakeyword">false</span> - only items located immediately
     * 						in the directory itself
     * @return				the revision number to which <code>file</code> was updated to
     * @throws SVNException 
     */
    public long doSwitch(File file, SVNURL url, SVNRevision revision, boolean recursive) throws SVNException {
        return doSwitch(file, url, SVNRevision.UNDEFINED, revision, recursive);
    }

    /**
     * Updates the Working Copy item to mirror a new URL. 
     * 
     * <p>
     * As a revision <b>SVNRevision</b>'s pre-defined constant fields can be used. For example,
     * to update the Working Copy to the latest revision of the repository use 
     * {@link SVNRevision#HEAD HEAD}.
     * 
     * @param  file         the Working copy item to be switched
     * @param  url          the repository location as a target against which the item will 
     *                      be switched
     * @param  pegRevision  a revision in which <code>file</code> is first looked up
     *                      in the repository
     * @param  revision     the desired revision of the repository target   
     * @param  recursive    if <span class="javakeyword">true</span> and <code>file</code> is
     *                      a directory then the entire tree will be updated, otherwise if 
     *                      <span class="javakeyword">false</span> - only items located immediately
     *                      in the directory itself
     * @return              the revision number to which <code>file</code> was updated to
     * @throws SVNException
     */
    public long doSwitch(File file, SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive) throws SVNException {
        return doSwitch(file, url, pegRevision, revision, recursive, false);
    }
    
    public long doSwitch(File file, SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, boolean force) throws SVNException {
        return doSwitch(file, url, pegRevision, revision, SVNDepth.fromRecurse(recursive), force);
    }    
    
    public long doSwitch(File file, SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth, boolean force) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminAreaInfo info = wcAccess.openAnchor(file, true, SVNWCAccess.INFINITE_DEPTH);
            final SVNReporter reporter = new SVNReporter(info, file, true, depth, getDebugLog());
            SVNAdminArea anchorArea = info.getAnchor();
            SVNEntry entry = anchorArea.getVersionedEntry(anchorArea.getThisDirName(), false);
            SVNURL sourceURL = entry.getSVNURL();
            if (sourceURL == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Directory ''{0}'' has no URL", anchorArea.getRoot());
                SVNErrorManager.error(err);
            }
            long[] revs = new long[1];
            // should fail on missing repository.
            SVNRepository repository = createRepository(url, null, pegRevision, revision, revs);
            long revNumber = revs[0];
            url = repository.getLocation();
            // root of the switched repos.
            SVNURL sourceRoot = repository.getRepositoryRoot(true);
            if (!SVNPathUtil.isAncestor(sourceRoot.toString(), sourceURL.toString())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SWITCH, "''{0}''\nis not the same repository as\n''{1}''",
                        new Object[] {url.toString(), sourceRoot.toString()});
                SVNErrorManager.error(err);
            }
            // reparent to the sourceURL
            repository.setLocation(sourceURL, false);
            String[] preservedExts = getOptions().getPreservedConflictFileExtensions();
            SVNUpdateEditor editor = new SVNUpdateEditor(info, url.toString(),                                                         
                                                         force, depth, preservedExts, 
                                                         null);
            String target = "".equals(info.getTargetName()) ? null : info.getTargetName();
            repository.update(url, revNumber, target, depth, reporter, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));

            if (editor.getTargetRevision() >= 0 && !isIgnoreExternals() && depth.isRecursive()) {
                handleExternals(info.getAnchor().getRoot(), info.getOldExternals(), info.getNewExternals(), info.getDepths(), url, sourceRoot, depth, false, false);
            }
            
            dispatchEvent(SVNEventFactory.createSVNEvent(info.getTarget().getRoot(), SVNNodeKind.NONE, null, editor.getTargetRevision(), SVNEventAction.UPDATE_COMPLETED, null, null, null));
            
            return editor.getTargetRevision();
        } finally {
            wcAccess.close();
            sleepForTimeStamp();
        }
    }
    
    /**
     * Checks out a Working Copy from a repository.
     * 
     * <p>
     * If the destination path (<code>dstPath</code>) is <span class="javakeyword">null</span>
     * then the last component of <code>url</code> is used for the local directory name.
     * 
     * <p>
     * As a revision <b>SVNRevision</b>'s pre-defined constant fields can be used. For example,
     * to check out a Working Copy at the latest revision of the repository use 
     * {@link SVNRevision#HEAD HEAD}.
     * 
     * @param  url			a repository location from where a Working Copy will be checked out		
     * @param  dstPath		the local path where the Working Copy will be placed
     * @param  pegRevision	the revision at which <code>url</code> will be firstly seen
     * 						in the repository to make sure it's the one that is needed
     * @param  revision		the desired revision of the Working Copy to be checked out
     * @param  recursive	if <span class="javakeyword">true</span> and <code>url</code> is
     * 						a directory then the entire tree will be checked out, otherwise if 
     * 						<span class="javakeyword">false</span> - only items located immediately
     * 						in the directory itself
     * @return				the revision number of the Working Copy
     * @throws SVNException <code>url</code> refers to a file, not a directory; <code>dstPath</code>
     * 						already exists but it is a file, not a directory; <code>dstPath</code> already
     * 						exists and is a versioned directory but has a different URL (repository location
     * 						against which the directory is controlled)  
     */
    public long doCheckout(SVNURL url, File dstPath, SVNRevision pegRevision, SVNRevision revision, boolean recursive) throws SVNException {
        return doCheckout(url, dstPath, pegRevision, revision, recursive, false);
    }

    public long doCheckout(SVNURL url, File dstPath, SVNRevision pegRevision, SVNRevision revision, boolean recursive, boolean force) throws SVNException {
        return doCheckout(url, dstPath, pegRevision, revision, SVNDepth.fromRecurse(recursive), force);
    }
    
    public long doCheckout(SVNURL url, File dstPath, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth, boolean force) throws SVNException {
        if (dstPath == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_FILENAME, "Checkout destination path can not be NULL");
            SVNErrorManager.error(err);
        }
        pegRevision = pegRevision == null ? SVNRevision.UNDEFINED : pegRevision;
        
        if (!revision.isValid() && pegRevision.isValid()) {
            revision = pegRevision;
        }
        
        if (!revision.isValid()) {
            revision = SVNRevision.HEAD;
        }
        
        SVNRepository repos = createRepository(url, null, pegRevision, revision);
        url = repos.getLocation();
        long revNumber = getRevisionNumber(revision, repos, null);
        SVNNodeKind targetNodeKind = repos.checkPath("", revNumber);
        if (targetNodeKind == SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "URL ''{0}'' refers to a file, not a directory", url);
            SVNErrorManager.error(err);
        } else if (targetNodeKind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL ''{0}'' doesn''t exist", url);
            SVNErrorManager.error(err);
        }
        String uuid = repos.getRepositoryUUID(true);
        SVNURL repositoryRoot = repos.getRepositoryRoot(true);

        long result = -1;
        depth = depth == null ? SVNDepth.UNKNOWN : depth;
        try {
            SVNWCAccess wcAccess = createWCAccess();
            SVNFileType kind = SVNFileType.getType(dstPath);
            if (kind == SVNFileType.NONE) {
                depth = depth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : depth;
                SVNAdminAreaFactory.createVersionedDirectory(dstPath, url, repositoryRoot, uuid, revNumber, depth);
                result = doUpdate(dstPath, revision, depth, force);
            } else if (kind == SVNFileType.DIRECTORY) {
                int formatVersion = SVNAdminAreaFactory.checkWC(dstPath, true);
                if (formatVersion != 0) {
                    SVNAdminArea adminArea = wcAccess.open(dstPath, false, 0);
                    SVNEntry rootEntry = adminArea.getEntry(adminArea.getThisDirName(), false);
                    wcAccess.closeAdminArea(dstPath);
                    if (rootEntry.getSVNURL() != null && url.equals(rootEntry.getSVNURL())) {
                        result = doUpdate(dstPath, revision, depth, force);
                    } else {
                        String message = "''{0}'' is already a working copy for a different URL";
                        if (rootEntry.isIncomplete()) {
                            message += "; perform update to complete it";
                        }
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, message, dstPath);
                        SVNErrorManager.error(err);
                    }
                } else {
                    depth = depth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : depth;
                    SVNAdminAreaFactory.createVersionedDirectory(dstPath, url, repositoryRoot, uuid, revNumber, depth);
                    result = doUpdate(dstPath, revision, depth, force);
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NODE_KIND_CHANGE, "''{0}'' already exists and is not a directory", dstPath);
                SVNErrorManager.error(err);
            }
        } finally {
            sleepForTimeStamp();
        }
        return result;
    }
    
    /**
     * Exports a clean directory or single file from a repository.
     * 
     * <p>
     * If <code>eolStyle</code> is not <span class="javakeyword">null</span> then it should denote
     * a specific End-Of-Line marker for the files to be exported. Significant values for 
     * <code>eolStyle</code> are:
     * <ul>
     * <li>"CRLF" (Carriage Return Line Feed) - this causes files to contain '\r\n' line ending sequences 
     * for EOL markers, regardless of the operating system in use (for instance, this EOL marker is used by 
     * software on the Windows platform).
     * <li>"LF" (Line Feed) - this causes files to contain '\n' line ending sequences 
     * for EOL markers, regardless of the operating system in use (for instance, this EOL marker is used by 
     * software on the Unix platform). 
     * <li>"CR" (Carriage Return) - this causes files to contain '\r' line ending sequences 
     * for EOL markers, regardless of the operating system in use (for instance, this EOL marker was used by 
     * software on older Macintosh platforms).
     * <li>"native" - this causes files to contain the EOL markers that are native to the operating system 
     * on which SVNKit is run.
     * </ul>
     * 
     * @param  url				a repository location from where the unversioned directory/file  will
     * 							be exported
     * @param  dstPath			the local path where the repository items will be exported to 			
     * @param  pegRevision		the revision at which <code>url</code> will be firstly seen
     * 							in the repository to make sure it's the one that is needed
     * @param  revision			the desired revision of the directory/file to be exported
     * @param  eolStyle			a string that denotes a specific End-Of-Line charecter;  
     * @param  force			<span class="javakeyword">true</span> to fore the operation even
     * 							if there are local files with the same names as those in the repository
     * 							(local ones will be replaced) 
     * @param  recursive		if <span class="javakeyword">true</span> and <code>url</code> is
     * 							a directory then the entire tree will be exported, otherwise if 
     * 							<span class="javakeyword">false</span> - only items located immediately
     * 							in the directory itself
     * @return					the revision number of the exported directory/file 
     * @throws SVNException
     */
    public long doExport(SVNURL url, File dstPath, SVNRevision pegRevision, SVNRevision revision, String eolStyle, boolean force, boolean recursive) throws SVNException {
        return doExport(url, dstPath, pegRevision, revision, eolStyle, force, SVNDepth.fromRecurse(recursive));
    }
    
    public long doExport(SVNURL url, File dstPath, SVNRevision pegRevision, SVNRevision revision, String eolStyle, boolean force, SVNDepth depth) throws SVNException {
        SVNRepository repository = createRepository(url, null, pegRevision, revision);
        long revisionNumber = getRevisionNumber(revision, repository, null);
        if (revisionNumber < 0) {
            revisionNumber = repository.getLatestRevision();
        }
        long exportedRevision = doRemoteExport(repository, revisionNumber, dstPath, eolStyle, force, depth);
        dispatchEvent(SVNEventFactory.createSVNEvent(null, SVNNodeKind.NONE, null, exportedRevision, SVNEventAction.UPDATE_COMPLETED, null, null, null));
        return exportedRevision;
    }

    /**
     * Exports a clean directory or single file from eihter a source Working Copy or
     * a repository.
     * 
     * <p>
     * How this method works:
     * <ul>
     * <li> If <code>revision</code> is different from {@link SVNRevision#BASE BASE}, 
     * {@link SVNRevision#WORKING WORKING}, {@link SVNRevision#COMMITTED COMMITTED}, 
     * {@link SVNRevision#UNDEFINED UNDEFINED} - then the repository origin of <code>srcPath</code>
     * will be exported (what is done by "remote" {@link #doExport(SVNURL, File, SVNRevision, SVNRevision, String, boolean, boolean)
     * doExport()}).
     * <li> In other cases a clean unversioned copy of <code>srcPath</code> - either a directory or a single file -
     * is exported to <code>dstPath</code>. 
     * </ul>
     * 
     * <p>
     * If <code>eolStyle</code> is not <span class="javakeyword">null</span> then it should denote
     * a specific End-Of-Line marker for the files to be exported. Significant values for 
     * <code>eolStyle</code> are:
     * <ul>
     * <li>"CRLF" (Carriage Return Line Feed) - this causes files to contain '\r\n' line ending sequences 
     * for EOL markers, regardless of the operating system in use (for instance, this EOL marker is used by 
     * software on the Windows platform).
     * <li>"LF" (Line Feed) - this causes files to contain '\n' line ending sequences 
     * for EOL markers, regardless of the operating system in use (for instance, this EOL marker is used by 
     * software on the Unix platform). 
     * <li>"CR" (Carriage Return) - this causes files to contain '\r' line ending sequences 
     * for EOL markers, regardless of the operating system in use (for instance, this EOL marker was used by 
     * software on older Macintosh platforms).
     * <li>"native" - this causes files to contain the EOL markers that are native to the operating system 
     * on which SVNKit is run.
     * </ul>
     * 
     * @param  srcPath			a repository location from where the unversioned directory/file  will
     * 							be exported
     * @param  dstPath			the local path where the repository items will be exported to 			
     * @param  pegRevision		the revision at which <code>url</code> will be firstly seen
     * 							in the repository to make sure it's the one that is needed
     * @param  revision			the desired revision of the directory/file to be exported
     * @param  eolStyle			a string that denotes a specific End-Of-Line charecter;  
     * @param  force			<span class="javakeyword">true</span> to fore the operation even
     * 							if there are local files with the same names as those in the repository
     * 							(local ones will be replaced) 
     * @param  recursive		if <span class="javakeyword">true</span> and <code>url</code> is
     * 							a directory then the entire tree will be exported, otherwise if 
     * 							<span class="javakeyword">false</span> - only items located immediately
     * 							in the directory itself
     * @return					the revision number of the exported directory/file 
     * @throws SVNException
     */
    public long doExport(File srcPath, final File dstPath, SVNRevision pegRevision, SVNRevision revision, String eolStyle,
            final boolean force, boolean recursive) throws SVNException {
        return doExport(srcPath, dstPath, pegRevision, revision, eolStyle, force, SVNDepth.fromRecurse(recursive));
    }
    
    public long doExport(File srcPath, final File dstPath, SVNRevision pegRevision, SVNRevision revision, String eolStyle,
            final boolean force, SVNDepth depth) throws SVNException {
        long exportedRevision = -1;
        if (revision != SVNRevision.BASE && revision != SVNRevision.WORKING && revision != SVNRevision.COMMITTED && revision != SVNRevision.UNDEFINED) {
            SVNRepository repository = createRepository(null, srcPath, pegRevision, revision);
            long revisionNumber = getRevisionNumber(revision, repository, srcPath);
            exportedRevision = doRemoteExport(repository, revisionNumber, dstPath, eolStyle, force, depth); 
        } else {
            if (revision == SVNRevision.UNDEFINED) {
                revision = SVNRevision.WORKING;
            }
            copyVersionedDir(srcPath, dstPath, revision, eolStyle, force, depth);
        }
        dispatchEvent(SVNEventFactory.createSVNEvent(null, SVNNodeKind.NONE, null, exportedRevision, SVNEventAction.UPDATE_COMPLETED, null, null, null));
        return exportedRevision;
    }
    
    private void copyVersionedDir(File from, File to, SVNRevision revision, String eolStyle, boolean force, SVNDepth depth) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        SVNAdminArea adminArea = wcAccess.probeOpen(from, false, 0);
        
        SVNEntry entry = null;
        try {
            entry = wcAccess.getVersionedEntry(from, false);
        } catch (SVNException svne) {
            wcAccess.close();
            throw svne;
        }
        
        if (revision == SVNRevision.WORKING && entry.isScheduledForDeletion()) {
            return;
        }
        if (revision != SVNRevision.WORKING && entry.isScheduledForAddition()) {
            return;
        }
        if (entry.isDirectory()) {
            // create dir
            boolean dirCreated = to.mkdirs();
            if (!to.exists() || to.isFile()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot create directory ''{0}''", to);
                SVNErrorManager.error(err);
            }
            if (!dirCreated && to.isDirectory() && !force) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "''{0}'' already exists and will not be owerwritten unless forced", to);
                SVNErrorManager.error(err);
            }
            // read entries
            for (Iterator ents = adminArea.entries(false); ents.hasNext();) {
                SVNEntry childEntry = (SVNEntry) ents.next();
                if (childEntry.isDirectory()) {
                    if (adminArea.getThisDirName().equals(childEntry.getName())) {
                        continue;
                    } else if (depth == SVNDepth.INFINITY) {
                        File childTo = new File(to, childEntry.getName());
                        File childFrom = new File(from, childEntry.getName());
                        copyVersionedDir(childFrom, childTo, revision, eolStyle, force, depth);
                    }
                } else if (childEntry.isFile()) {
                    File childTo = new File(to, childEntry.getName());
                    copyVersionedFile(childTo, adminArea, childEntry.getName(), revision, eolStyle);
                }
            }
            if (!isIgnoreExternals() && depth == SVNDepth.INFINITY && entry.getDepth() == SVNDepth.INFINITY) {
                SVNVersionedProperties properties = adminArea.getProperties(adminArea.getThisDirName());
                String externalsValue = properties.getStringPropertyValue(SVNProperty.EXTERNALS);
                if (externalsValue != null) {
                    SVNExternal[] externals = SVNExternal.parseExternals(adminArea.getRoot().getAbsolutePath(), externalsValue);
                    for (int i = 0; i < externals.length; i++) {
                        SVNExternal info = externals[i];
                        File srcPath = new File(adminArea.getRoot(), info.getPath());
                        File dstPath = new File(to, info.getPath());
                        if (SVNPathUtil.getSegmentsCount(info.getPath()) > 1) {
                            if (!dstPath.getParentFile().exists() && !dstPath.getParentFile().mkdirs()) {
                                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CLIENT_IS_DIRECTORY, "Could not create directory ''{0}''", dstPath.getParentFile()));
                            }
                        }
                        copyVersionedDir(srcPath, dstPath, revision, eolStyle, force, SVNDepth.INFINITY);
                    }
                }
            }
        } else if (entry.isFile()) {
            copyVersionedFile(to, adminArea, entry.getName(), revision, eolStyle);
        }
        
        wcAccess.close();
    }

    private void copyVersionedFile(File dstPath, SVNAdminArea adminArea, String fileName, SVNRevision revision, String eol) throws SVNException {
        SVNEntry entry = adminArea.getEntry(fileName, false);
        if (revision == SVNRevision.WORKING && entry.isScheduledForDeletion()) {
            return;
        }
        if (revision != SVNRevision.WORKING && entry.isScheduledForAddition()) {
            return;
        }
        boolean modified = false;
        SVNVersionedProperties props = null;
        long timestamp;
        if (revision != SVNRevision.WORKING) {
            props = adminArea.getBaseProperties(fileName);
        } else {
            props = adminArea.getProperties(fileName);
            modified = adminArea.hasTextModifications(fileName, false);
        }
        boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;
        boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
        String keywords = props.getStringPropertyValue(SVNProperty.KEYWORDS);
        String charsetProp = props.getStringPropertyValue(SVNProperty.CHARSET);
        String charset = SVNTranslator.getCharset(charsetProp, getOptions());
        byte[] eols = eol != null ? SVNTranslator.getEOL(eol, getOptions()) : null;
        if (eols == null) {
            eol = props.getStringPropertyValue(SVNProperty.EOL_STYLE);
            eols = SVNTranslator.getEOL(eol, getOptions());
        }
        if (modified && !special) {
            timestamp = adminArea.getFile(fileName).lastModified();
        } else {
            timestamp = SVNDate.parseDateAsMilliseconds(entry.getCommittedDate());
        }
        Map keywordsMap = null;
        if (keywords != null) {
            String rev = Long.toString(entry.getCommittedRevision());
            String author;
            if (modified) {
                author = "(local)";
                rev += "M";
            } else {
                author = entry.getAuthor();                
            }
            keywordsMap = SVNTranslator.computeKeywords(keywords, entry.getURL(), author, entry.getCommittedDate(), rev, getOptions());            
        }
        File srcFile = revision == SVNRevision.WORKING ? adminArea.getFile(fileName) : adminArea.getBaseFile(fileName, false);
        SVNFileType fileType = SVNFileType.getType(srcFile);
        if (fileType == SVNFileType.SYMLINK && revision == SVNRevision.WORKING) {
            // base will be translated OK, but working not.
            File tmpBaseFile = adminArea.getBaseFile(fileName, true);
            try {
                SVNTranslator.translate(srcFile, tmpBaseFile, charset, eols, keywordsMap, special, false);
                SVNTranslator.translate(tmpBaseFile, dstPath, charset, eols, keywordsMap, special, true);
            } finally {
                tmpBaseFile.delete();
            }
        } else {
            SVNTranslator.translate(srcFile, dstPath, charset, eols, keywordsMap, special, true);
        }
        if (executable) {
            SVNFileUtil.setExecutable(dstPath, true);
        }
        if (!special && timestamp > 0) {
            dstPath.setLastModified(timestamp);
        }
    }

    private long doRemoteExport(SVNRepository repository, final long revNumber, File dstPath, String eolStyle, boolean force, SVNDepth depth) throws SVNException {
        SVNNodeKind dstKind = repository.checkPath("", revNumber);
        if (dstKind == SVNNodeKind.DIR) {
            SVNExportEditor editor = new SVNExportEditor(this, repository.getLocation().toString(), dstPath,  force, eolStyle, getOptions());
            repository.update(revNumber, null, depth, false, new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, revNumber, SVNDepth.INFINITY, true);
                    reporter.finishReport();
                }
            }, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
            // nothing may be created.
            SVNFileType fileType = SVNFileType.getType(dstPath);
            if (fileType == SVNFileType.NONE) {
                editor.openRoot(revNumber);
            }
            if (!isIgnoreExternals() && depth == SVNDepth.INFINITY) {
                Map externals = editor.getCollectedExternals();
                handleExternals(dstPath, Collections.EMPTY_MAP, externals, Collections.EMPTY_MAP, repository.getLocation(), repository.getRepositoryRoot(true), 
                        depth, true, true);
            }
        } else if (dstKind == SVNNodeKind.FILE) {
            String url = repository.getLocation().toString();
            if (dstPath.isDirectory()) {
                dstPath = new File(dstPath, SVNEncodingUtil.uriDecode(SVNPathUtil.tail(url)));
            }
            if (dstPath.exists()) {
                if (!force) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Path ''{0}'' already exists", dstPath);
                    SVNErrorManager.error(err);
                }
            } else {
                dstPath.getParentFile().mkdirs();
            }
            SVNProperties properties = new SVNProperties();
            OutputStream os = null;
            File tmpFile = SVNFileUtil.createUniqueFile(dstPath.getParentFile(), ".export", ".tmp");
            try {
                os = SVNFileUtil.openFileForWriting(tmpFile);
                try {
                    repository.getFile("", revNumber, properties, new SVNCancellableOutputStream(os, this));
                } finally {
                    SVNFileUtil.closeFile(os);
                }
                if (force && dstPath.exists()) {
                    SVNFileUtil.deleteAll(dstPath, this);
                }
                boolean binary = SVNProperty.isBinaryMimeType(properties.getStringValue(SVNProperty.MIME_TYPE));
                String charset = SVNTranslator.getCharset(properties.getStringValue(SVNProperty.CHARSET), getOptions());
                Map keywords = SVNTranslator.computeKeywords(properties.getStringValue(SVNProperty.KEYWORDS), url,
                        properties.getStringValue(SVNProperty.LAST_AUTHOR),
                        properties.getStringValue(SVNProperty.COMMITTED_DATE),
                        properties.getStringValue(SVNProperty.COMMITTED_REVISION), getOptions());
                byte[] eols = null;
                if (SVNProperty.EOL_STYLE_NATIVE.equals(properties.getStringValue(SVNProperty.EOL_STYLE))) {
                    eols = SVNTranslator.getEOL(eolStyle != null ? eolStyle : properties.getStringValue(SVNProperty.EOL_STYLE), getOptions());
                } else if (properties.containsName(SVNProperty.EOL_STYLE)) {
                    eols = SVNTranslator.getEOL(properties.getStringValue(SVNProperty.EOL_STYLE), getOptions());
                }
                if (binary) {
                    charset = null;
                    eols = null;
                    keywords = null;
                }
                SVNTranslator.translate(tmpFile, dstPath, charset, eols, keywords, properties.getStringValue(SVNProperty.SPECIAL) != null, true);
            } finally {
                SVNFileUtil.deleteFile(tmpFile);
            }
            if (properties.getStringValue(SVNProperty.EXECUTABLE) != null) {
                SVNFileUtil.setExecutable(dstPath, true);
            }
            dispatchEvent(SVNEventFactory.createSVNEvent(dstPath, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNEventAction.UPDATE_ADD, null, null, null));
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL ''{0}'' doesn't exist", repository.getLocation());
            SVNErrorManager.error(err);
        }
        return revNumber;
    }
    
    /**
     * Substitutes the beginning part of a Working Copy's URL with a new one.
     * 
     * <p> 
     * When a repository root location or a URL schema is changed the old URL of the 
     * Working Copy which starts with <code>oldURL</code> should be substituted for a
     * new URL beginning - <code>newURL</code>.
     * 
     * @param  dst				a Working Copy item's path 
     * @param  oldURL			the old beginning part of the repository's URL that should
     * 							be overwritten  
     * @param  newURL			a new beginning part for the repository location that
     * 							will overwrite <code>oldURL</code> 
     * @param  recursive		if <span class="javakeyword">true</span> and <code>dst</code> is
     * 							a directory then the entire tree will be relocated, otherwise if 
     * 							<span class="javakeyword">false</span> - only <code>dst</code> itself
     * @throws SVNException
     */
    /* TODO(sd): "I don't see any reason to change this recurse parameter
     * to a depth, but making a note to re-check this logic later."
     */
    public void doRelocate(File dst, SVNURL oldURL, SVNURL newURL, boolean recursive) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminArea adminArea = wcAccess.probeOpen(dst, true, recursive ? SVNWCAccess.INFINITE_DEPTH : 0);
            String name = dst.equals(adminArea.getRoot()) ? adminArea.getThisDirName() : dst.getName();
            String from = oldURL.toString();
            String to = newURL.toString();
            if (from.endsWith("/")) {
                from = from.substring(0, from.length() - 1);
            } 
            if (to.endsWith("/")) {
                to = to.substring(0, to.length() - 1);
            } 
            doRelocate(adminArea, name, from, to, recursive, new HashMap());
        } finally {
            wcAccess.close();
        }
    }

    /**
     * Canonicalizes all urls in the specified Working Copy.
     * 
     * @param dst               a WC path     
     * @param omitDefaultPort   if <span class="javakeyword">true</span> then removes all
     *                          port numbers from urls which equal to default ones, otherwise
     *                          does not
     * @param recursive         recurses an operation
     * @throws SVNException
     */
    public void doCanonicalizeURLs(File dst, boolean omitDefaultPort, boolean recursive) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminAreaInfo adminAreaInfo = wcAccess.openAnchor(dst, true, recursive ? SVNWCAccess.INFINITE_DEPTH : 0);
            SVNAdminArea target = adminAreaInfo.getTarget();
            SVNEntry entry = wcAccess.getEntry(dst, false);
            String name = target.getThisDirName();
            if (entry != null && entry.isFile()) {
                name = entry.getName();
            }
            doCanonicalizeURLs(adminAreaInfo, target, name, omitDefaultPort, recursive);
            if (recursive && !isIgnoreExternals()) {
                for(Iterator externals = adminAreaInfo.getNewExternals().keySet().iterator(); externals.hasNext();) {
                    String path = (String) externals.next();
                    String external = (String) adminAreaInfo.getNewExternals().get(path);
                    SVNExternal[] exts = SVNExternal.parseExternals(path, external);
                    File owner = new File(adminAreaInfo.getAnchor().getRoot(), path);
                    for (int i = 0; i < exts.length; i++) {
                        File externalFile = new File(owner, exts[i].getPath()); 
                        try {
                            doCanonicalizeURLs(externalFile, omitDefaultPort, true);
                        } catch (SVNCancelException e) {
                            throw e;
                        } catch (SVNException e) {
                            getDebugLog().info(e);
                        }
                    }
                }
            }
        } finally {
            wcAccess.close();
        }
    }

    private void doCanonicalizeURLs(SVNAdminAreaInfo adminAreaInfo, SVNAdminArea adminArea, String name, boolean omitDefaultPort, boolean recursive) throws SVNException {
        boolean save = false;
        checkCancelled();
        if (!adminArea.getThisDirName().equals(name)) {
            SVNEntry entry = adminArea.getEntry(name, true);
            save = canonicalizeEntry(entry, omitDefaultPort);
            adminArea.getWCProperties(name).setPropertyValue(SVNProperty.WC_URL, null);
            if (save) {
                adminArea.saveEntries(false);
            }
            return;
        }
        if (!isIgnoreExternals()) {
            SVNPropertyValue externalsValue = adminArea.getProperties(adminArea.getThisDirName()).getPropertyValue(SVNProperty.EXTERNALS);
            String ownerPath = adminArea.getRelativePath(adminAreaInfo.getAnchor());
            String externals = externalsValue == null ? null : externalsValue.getString();
            adminAreaInfo.addExternal(ownerPath, externals, externals);
            if (externalsValue != null) {
                externalsValue = SVNPropertyValue.create(canonicalizeExtenrals(externals, omitDefaultPort));
                adminArea.getProperties(adminArea.getThisDirName()).setPropertyValue(SVNProperty.EXTERNALS, externalsValue);
            }
        }
        
        SVNEntry rootEntry = adminArea.getEntry(adminArea.getThisDirName(), true);
        save = canonicalizeEntry(rootEntry, omitDefaultPort);
        adminArea.getWCProperties(adminArea.getThisDirName()).setPropertyValue(SVNProperty.WC_URL, null);
        // now all child entries that doesn't has repos/url has new values.
        for(Iterator ents = adminArea.entries(true); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (adminArea.getThisDirName().equals(entry.getName())) {
                continue;
            }
            checkCancelled();
            if (recursive && entry.isDirectory() && 
                    (entry.isScheduledForAddition() || !entry.isDeleted()) &&
                    !entry.isAbsent()) {
                SVNAdminArea childArea = adminArea.getWCAccess().retrieve(adminArea.getFile(entry.getName()));
                if (childArea != null) {
                    doCanonicalizeURLs(adminAreaInfo, childArea, "", omitDefaultPort, recursive);
                }
            }
            save |= canonicalizeEntry(entry, omitDefaultPort);
            SVNVersionedProperties properties = adminArea.getWCProperties(entry.getName());
            if (properties != null) {
                properties.setPropertyValue(SVNProperty.WC_URL, null);
            }
        }
        if (save) {
            adminArea.saveEntries(true);
        }
    }
    
    private static String canonicalizeExtenrals(String externals, boolean omitDefaultPort) throws SVNException {
        if (externals == null) {
            return null;
        }
        StringBuffer canonicalized = new StringBuffer();
        for(StringTokenizer lines = new StringTokenizer(externals, "\r\n", true); lines.hasMoreTokens();) {
            String line = lines.nextToken();
            if (line.trim().length() == 0 || line.trim().startsWith("#") 
                    || line.indexOf('\r') >= 0 || line.indexOf('\n') >= 0) {
                canonicalized.append(line);
                continue;
            }
            String[] tokens = line.split("[ \t]");
            int index = tokens.length - 1;
            SVNURL url = null;
            if (index >= 1) {
                try {
                    url = SVNURL.parseURIEncoded(tokens[index]);
                } catch (SVNException e) {
                    url = null;
                }
            } 
            SVNURL canonicalURL = canonicalizeURL(url, omitDefaultPort);
            if (canonicalURL == null) {
                canonicalized.append(line);
            } else {
                canonicalized.append(tokens[0]);
                canonicalized.append(' ');
                if (index == 2) {
                    canonicalized.append(tokens[1]);
                    canonicalized.append(' ');
                }
                canonicalized.append(canonicalURL.toString());
            }
        }
        return canonicalized.toString();
    }
    
    private static boolean canonicalizeEntry(SVNEntry entry, boolean omitDefaultPort) throws SVNException {
        boolean updated = false;
        SVNURL root = canonicalizeURL(entry.getRepositoryRootURL(), omitDefaultPort);
        if (root != null) {
            updated |= entry.setRepositoryRootURL(root);            
        }
        SVNURL url = canonicalizeURL(entry.getSVNURL(), omitDefaultPort);
        if (url != null) {
            updated |= entry.setURL(url.toString());
        }
        SVNURL copyFrom = canonicalizeURL(entry.getCopyFromSVNURL(), omitDefaultPort);
        if (copyFrom != null) {
            updated |= entry.setCopyFromURL(copyFrom.toString());
        }
        return updated;
    }
    
    private static SVNURL canonicalizeURL(SVNURL url, boolean omitDefaultPort) throws SVNException {
        if (url == null || url.getPort() <= 0) {
            // no url or file url.
            return null;
        }
        int defaultPort = SVNURL.getDefaultPortNumber(url.getProtocol());
        if (defaultPort <= 0) {
            // file or svn+ext URL.
            return null;
        }
        if (omitDefaultPort) {
            // remove port if it is same as default.
            if (url.hasPort() && url.getPort() == defaultPort) {
                return SVNURL.create(url.getProtocol(), url.getUserInfo(), url.getHost(), -1, url.getPath(), false);
            }
        } else if (!url.hasPort()) {
            // set port if there is no port set.
            return SVNURL.create(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), false);
        }
        return null;
    }

    private void handleExternals(File root, Map oldExternals, Map newExternals, Map depths, SVNURL fromURL, SVNURL rootURL, SVNDepth requestedDepth, boolean isExport, boolean updateUnchanged) throws SVNException {
        Set diff = new HashSet();
        if (oldExternals != null) {
            diff.addAll(oldExternals.keySet());
        } 
        if (newExternals != null) {
            diff.addAll(newExternals.keySet());
        }
        // now we have diff.
        for (Iterator diffPaths = diff.iterator(); diffPaths.hasNext();) {
            String diffPath = (String) diffPaths.next();
            SVNDepth depth = depths == Collections.EMPTY_MAP ? SVNDepth.INFINITY : (SVNDepth) depths.get(diffPath);
            if (depth == null) {
                // TODO convert diffpath to full path.
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Traversal of ''{0}'' found no ambient depth", diffPath);
                SVNErrorManager.error(err);
            }
            if ((requestedDepth.getId() < SVNDepth.INFINITY.getId() && requestedDepth != SVNDepth.UNKNOWN) ||
                    (depth.getId() < SVNDepth.INFINITY.getId() && requestedDepth.getId() < SVNDepth.INFINITY.getId())) {
                continue;
            }
            String oldValue = (String) oldExternals.get(diffPath);
            String newValue = (String) newExternals.get(diffPath);

            // TODO convert diffpath to full path.
            SVNExternal[] previous = oldValue != null ? SVNExternal.parseExternals(diffPath, oldValue) : null;
            SVNExternal[] current = newValue != null ? SVNExternal.parseExternals(diffPath, newValue) : null;
            Map oldParsedExternals = new HashMap();
            Map newParsedExternals = new HashMap();
            // put to another hashes.
            for (int i = 0; current != null && i < current.length; i++) {
                newParsedExternals.put(current[i].getPath(), current[i]);
            }
            for (int i = 0; previous != null && i < previous.length; i++) {
                oldParsedExternals.put(previous[i].getPath(), previous[i]);
            }
            // finally handle changes.
            ExternalDiff externalDiff = new ExternalDiff();
            externalDiff.isExport = isExport;
            externalDiff.isUpdateUnchanged = updateUnchanged;
            externalDiff.rootURL = rootURL;

            for (Iterator paths = oldParsedExternals.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                externalDiff.oldExternal = (SVNExternal) oldParsedExternals.get(path);
                externalDiff.newExternal = (SVNExternal) newParsedExternals.get(path);
                externalDiff.owner = new File(root, diffPath);
                externalDiff.ownerURL = fromURL.appendPath(diffPath, false);
                handleExternalChange(externalDiff.oldExternal.getPath(), externalDiff);
            }
            for (Iterator paths = newParsedExternals.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                if (!oldParsedExternals.containsKey(path)) {
                    externalDiff.oldExternal = null;
                    externalDiff.newExternal = (SVNExternal) newParsedExternals.get(path);
                    externalDiff.owner = new File(root, diffPath);
                    externalDiff.ownerURL = fromURL.appendPath(diffPath, false);
                    handleExternalChange(externalDiff.newExternal.getPath(), externalDiff);
                }
            }
        }
    }
    
    private void handleExternalChange(String targetDir, ExternalDiff externalDiff) throws SVNException {
        File target = new File(externalDiff.owner, targetDir);
        SVNURL oldURL = null;
        SVNURL newURL = null;
        if (externalDiff.oldExternal != null && !externalDiff.isExport) {
            oldURL = externalDiff.oldExternal.resolveURL(externalDiff.rootURL, externalDiff.ownerURL);
        }
        if (externalDiff.newExternal != null) {
            newURL = externalDiff.newExternal.resolveURL(externalDiff.rootURL, externalDiff.ownerURL);
        }
        if (oldURL == null && newURL == null) {
            return;
        }
        try {
            setEventPathPrefix("path");
            if (oldURL == null) {
                target.getParentFile().mkdirs();
                dispatchEvent(SVNEventFactory.createSVNEvent(target, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.UPDATE_EXTERNAL, null, null, null));
                if (externalDiff.isExport) {
                    doExport(newURL, target, externalDiff.newExternal.getPegRevision(), externalDiff.newExternal.getRevision(), null, false, SVNDepth.INFINITY); 
                } else {
                    doCheckout(newURL, target, externalDiff.newExternal.getPegRevision(), externalDiff.newExternal.getRevision(), SVNDepth.INFINITY, false);
                }
            } else if (newURL == null) {
                SVNWCAccess wcAccess = createWCAccess();
                SVNAdminArea area = wcAccess.open(target, true, SVNWCAccess.INFINITE_DEPTH);
                SVNException error = null;
                try {
                    area.removeFromRevisionControl(area.getThisDirName(), true, false);
                } catch (SVNException svne) {
                    error = svne;
                }
                if (error == null || error.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                    try {
                        wcAccess.close();
                    } catch (SVNException svne) {
                        error = error == null ? svne : error;
                    }
                }
                if (error != null && error.getErrorMessage().getErrorCode() != SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                    throw error;
                }
            } else if (externalDiff.isUpdateUnchanged || !externalDiff.compareExternals(oldURL, newURL)) {
                dispatchEvent(SVNEventFactory.createSVNEvent(target, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.UPDATE_EXTERNAL, null, null, null));
                SVNFileType fileType = SVNFileType.getType(target);
                boolean empty = false;
                if (fileType == SVNFileType.DIRECTORY) {
                    File[] children = target.listFiles();
                    if (children != null && children.length == 0) {
                        empty = true;
                    }
                }
                
                if (fileType == SVNFileType.DIRECTORY && !empty) {
                    SVNWCAccess wcAccess = createWCAccess();
                    SVNAdminArea area = wcAccess.open(target, true, 0);
                    SVNEntry entry = area.getEntry(area.getThisDirName(), false);
                    wcAccess.close();
                    String url = entry.getURL();
    
                    if (entry != null && entry.getURL() != null) {
                        if (newURL.toString().equals(url)) {
                            doUpdate(target, externalDiff.newExternal.getRevision(), SVNDepth.UNKNOWN, true);
                            return;
                        } else if (entry.getRepositoryRoot() != null) {
                            if (!SVNPathUtil.isAncestor(entry.getRepositoryRoot(), newURL.toString())) {
                                SVNRepository repos = createRepository(newURL, true);
                                SVNURL reposRoot = repos.getRepositoryRoot(true);
                                try {
                                    doRelocate(target, entry.getSVNURL(), reposRoot, true);
                                } catch (SVNException svne) {
                                    if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_INVALID_RELOCATION || 
                                            svne.getErrorMessage().getErrorCode() == SVNErrorCode.CLIENT_INVALID_RELOCATION) {
                                        deleteExternal(target);
                                        target.mkdirs();
                                        doCheckout(newURL, target, externalDiff.newExternal.getPegRevision(), externalDiff.newExternal.getRevision(), SVNDepth.INFINITY, false);
                                        return;
                                    } 
                                    throw svne;
                                }
                            }
                            doSwitch(target, newURL, externalDiff.newExternal.getPegRevision(), externalDiff.newExternal.getRevision(), SVNDepth.INFINITY, false);
                            return;
                        }
                    }
                    deleteExternal(target);
                    target.mkdirs();
                    doCheckout(newURL, target, externalDiff.newExternal.getPegRevision(), externalDiff.newExternal.getRevision(), SVNDepth.INFINITY, false);
                    return;
                } 
                if (fileType != SVNFileType.DIRECTORY) {
                    target.mkdirs();
                }
                doCheckout(newURL, target, externalDiff.newExternal.getPegRevision(), externalDiff.newExternal.getRevision(), SVNDepth.INFINITY, true);
            }
        } catch (SVNCancelException cancel) {
            throw cancel;
        } catch (SVNException e) {
            SVNDebugLog.getDefaultLog().info(e); 
            SVNEvent event = SVNEventFactory.createSVNEvent(target, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.SKIP, SVNEventAction.UPDATE_EXTERNAL, e.getErrorMessage(), null);
            dispatchEvent(event);
        } finally {
            setEventPathPrefix(null);
        }
    }

    protected SVNRevision getExternalRevision(File file, SVNURL newURL) {
        if (file == null || newURL == null) {
            return SVNRevision.HEAD;
        }
        return SVNRevision.HEAD;
    }

    private void deleteExternal(File external) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        SVNAdminArea adminArea = wcAccess.open(external, true, SVNWCAccess.INFINITE_DEPTH);
        SVNException error = null;
        try {
            adminArea.removeFromRevisionControl(adminArea.getThisDirName(), true, false);
        } catch (SVNException svne) {
            getDebugLog().info(svne);
            error = svne;
        }
        
        if (error == null || error.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
            wcAccess.close();
        }
        
        if (error != null && error.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
            external.getParentFile().mkdirs();
            File newLocation = SVNFileUtil.createUniqueFile(external.getParentFile(), external.getName(), ".OLD");
            SVNFileUtil.rename(external, newLocation);
        } else if (error != null) {
            throw error;
        }
    }

    private Map validateRelocateTargetURL(SVNURL targetURL, String expectedUUID, Map validatedURLs, boolean isRoot) throws SVNException {
        if (validatedURLs == null) {
            return null;
        }

        for(Iterator targetURLs = validatedURLs.keySet().iterator(); targetURLs.hasNext();) {
            SVNURL validatedURL = (SVNURL) targetURLs.next();
            if (targetURL.toString().startsWith(validatedURL.toString())) {
                if (isRoot && !targetURL.equals(validatedURL)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_RELOCATION, "''{0}'' is not the root of the repository", targetURL);
                    SVNErrorManager.error(err);
                }
                String validatedUUID = (String) validatedURLs.get(validatedURL);
                if (expectedUUID != null && !expectedUUID.equals(validatedUUID)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_RELOCATION, "The repository at ''{0}'' has uuid ''{1}'', but the WC has ''{2}''",
                            new Object[] {validatedURL, expectedUUID, validatedUUID});
                    SVNErrorManager.error(err);
                }
                return validatedURLs;
            }
        }
        SVNRepository repos = createRepository(targetURL, false);
        try {
            SVNURL actualRoot = repos.getRepositoryRoot(true);
            if (isRoot && !targetURL.equals(actualRoot)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_RELOCATION, "''{0}'' is not the root of the repository", targetURL);
                SVNErrorManager.error(err);
            }
    
            String actualUUID = repos.getRepositoryUUID(true);
            if (expectedUUID != null && !expectedUUID.equals(actualUUID)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_RELOCATION, "The repository at ''{0}'' has uuid ''{1}'', but the WC has ''{2}''",
                        new Object[] {targetURL, expectedUUID, actualUUID});
                SVNErrorManager.error(err);
            }
            validatedURLs.put(targetURL, actualUUID);
        } finally {
            repos.closeSession();
        }
        return validatedURLs;
    }
    
    private Map relocateEntry(SVNEntry entry, String from, String to, Map validatedURLs) throws SVNException {
        if (entry.getRepositoryRoot() != null) {
            // that is what i do not understand :)
            String repos = entry.getRepositoryRoot();
            if (from.length() > repos.length()) {
                String fromPath = from.substring(repos.length());
                if (!to.endsWith(fromPath)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_RELOCATION, "Relocate can only change the repository part of an URL");
                    SVNErrorManager.error(err);
                }
                from = repos;
                to = to.substring(0, to.length() - fromPath.length());
            }
            if (repos.startsWith(from)) {
                entry.setRepositoryRoot(to + repos.substring(from.length()));
                validatedURLs = validateRelocateTargetURL(entry.getRepositoryRootURL(), entry.getUUID(), validatedURLs, true);
            }
        }
        if (entry.getURL() != null && entry.getURL().startsWith(from)) {
            entry.setURL(to + entry.getURL().substring(from.length()));
            if (entry.getUUID() != null && validatedURLs != null) {
                validatedURLs = validateRelocateTargetURL(entry.getSVNURL(), entry.getUUID(), validatedURLs, false);
            }
        }
        if (entry.getCopyFromURL() != null && entry.getCopyFromURL().startsWith(from)) {
            entry.setCopyFromURL(to + entry.getCopyFromURL().substring(from.length()));
            if (entry.getUUID() != null && validatedURLs != null) {
                validatedURLs = validateRelocateTargetURL(entry.getCopyFromSVNURL(), entry.getUUID(), validatedURLs, false);
            }
        }
        return validatedURLs;
    }
    
    private Map doRelocate(SVNAdminArea adminArea, String name, String from, String to, boolean recursive, Map validatedURLs) throws SVNException {
        SVNEntry entry = adminArea.getEntry(name, true);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND);
            SVNErrorManager.error(err);
        }
        
        if (entry.isFile()) {
            relocateEntry(entry, from, to, validatedURLs);
            SVNPropertiesManager.deleteWCProperties(adminArea, name, false);
            adminArea.saveEntries(false);
            return validatedURLs;
        }
        
        validatedURLs = relocateEntry(entry, from, to, validatedURLs);
        SVNWCAccess wcAccess = adminArea.getWCAccess();
        for (Iterator entries = adminArea.entries(true); entries.hasNext();) {
            SVNEntry childEntry = (SVNEntry) entries.next();
            if (adminArea.getThisDirName().equals(childEntry.getName())) {
                continue;
            }
            if (recursive && childEntry.isDirectory() && 
                    (childEntry.isScheduledForAddition() || !childEntry.isDeleted()) && !childEntry.isAbsent()) {
                File childDir = adminArea.getFile(childEntry.getName());
                if (wcAccess.isMissing(childDir)) {
                    continue;
                }
                SVNAdminArea childArea = wcAccess.retrieve(childDir);
                validatedURLs = doRelocate(childArea, childArea.getThisDirName(), from, to, recursive, validatedURLs);
            }
            validatedURLs = relocateEntry(childEntry, from, to, validatedURLs);
            SVNPropertiesManager.deleteWCProperties(adminArea, childEntry.getName(), false);
        }
        SVNPropertiesManager.deleteWCProperties(adminArea, "", false);
        adminArea.saveEntries(false);
        return validatedURLs;
    }
    
    private static class ExternalDiff {
        
        public SVNExternal oldExternal;
        public SVNExternal newExternal;
        
        public File owner;
        public SVNURL ownerURL;
        
        public SVNURL rootURL;
        
        public boolean isExport;
        public boolean isUpdateUnchanged;
        
        public boolean compareExternals(SVNURL oldURL, SVNURL newURL) {
            return oldURL.equals(newURL) && 
                oldExternal.getRevision().equals(newExternal.getRevision()) &&
                oldExternal.getPegRevision().equals(newExternal.getPegRevision());
        }
    }
}
