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

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExportEditor;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.SVNUpdateEditor;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
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
 * <td><b>JavaSVN</b></td>
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
 * @version 1.0
 * @author  TMate Software Ltd.
 * @see     <a target="_top" href="http://tmate.org/svn/kb/examples/">Examples</a>
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

    protected SVNUpdateClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
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
        SVNWCAccess wcAccess = createWCAccess(file);
        final SVNReporter reporter = new SVNReporter(wcAccess, true, recursive);
        try {
            wcAccess.open(true, recursive);
            SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry("", false);
            SVNURL url = entry.getSVNURL();
            SVNUpdateEditor editor = new SVNUpdateEditor(wcAccess, null, recursive, isLeaveConflictsUnresolved());
            SVNRepository repos = createRepository(url, true);
            
            String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
            long revNumber = getRevisionNumber(revision, repos, file);
            repos.update(revNumber, target, recursive, reporter, SVNCancellableEditor.newInstance(editor, this));

            if (editor.getTargetRevision() >= 0) {
                if (recursive && !isIgnoreExternals()) {
                    handleExternals(wcAccess);
                }
                dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(wcAccess, editor.getTargetRevision()));
            }
            return editor.getTargetRevision();
        } finally {
            wcAccess.close(true);
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
        SVNWCAccess wcAccess = createWCAccess(file);
        final SVNReporter reporter = new SVNReporter(wcAccess, true, recursive);
        try {
            wcAccess.open(true, recursive);
            SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry("", false);
            if (entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", file);
                SVNErrorManager.error(err);
            }
            SVNURL sourceURL = entry.getSVNURL();
            if (url == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", file);
                SVNErrorManager.error(err);
            }
            SVNRepository repository = createRepository(sourceURL, true);
            long revNumber = getRevisionNumber(revision, repository, file);

            SVNUpdateEditor editor = new SVNUpdateEditor(wcAccess, url.toString(), recursive, isLeaveConflictsUnresolved());
            
            String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
            repository.update(url, revNumber, target, recursive, reporter, SVNCancellableEditor.newInstance(editor, this));

            if (editor.getTargetRevision() >= 0 && recursive && !isIgnoreExternals()) {
                handleExternals(wcAccess);
                dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(wcAccess, editor.getTargetRevision()));
            }
            return editor.getTargetRevision();
        } finally {
            wcAccess.close(true);
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
        if (dstPath == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_FILENAME, "Checkout destination path can not be NULL");
            SVNErrorManager.error(err);
        }
        SVNRepository repos = createRepository(url, null, pegRevision, revision);
        long revNumber = getRevisionNumber(revision, repos, null);
        SVNNodeKind targetNodeKind = repos.checkPath("", revNumber);
        String uuid = repos.getRepositoryUUID();
        SVNURL repositoryRoot = repos.getRepositoryRoot(true);
        if (targetNodeKind == SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "URL ''{0}'' refers to a file, not a directory", url);
            SVNErrorManager.error(err);
        } else if (targetNodeKind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL ''{0}'' doesn't exist", url);
            SVNErrorManager.error(err);
        }
        long result = -1;
        SVNWCAccess wcAccess = null;
        SVNEntry entry = null;
        try {
            try {
                wcAccess = createWCAccess(dstPath);
                entry = wcAccess != null ? wcAccess.getTargetEntry() : null;
            } catch (SVNException e) {
                //
            }
            if (!dstPath.exists() || wcAccess == null || entry == null) {
                createVersionedDirectory(dstPath, url, repositoryRoot, uuid, revNumber);
                result = doUpdate(dstPath, revision, recursive);
            } else if (dstPath.isDirectory() && entry != null) {
                if (url.equals(entry.getSVNURL())) {
                    result = doUpdate(dstPath, revision, recursive);
                } else {
                    String message = "''{0}'' is already a working copy for a different URL";
                    if (entry.isIncomplete()) {
                        message += "; perform update to complete it";
                    }
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, message, dstPath);
                    SVNErrorManager.error(err);
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
     * on which JavaSVN is run.
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
        SVNRepository repository = createRepository(url, null, pegRevision, revision);
        long revisionNumber = getRevisionNumber(revision, repository, null);
        long exportedRevision = doRemoteExport(repository, revisionNumber, dstPath, eolStyle, force, recursive);
        dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(null, exportedRevision));
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
     * on which JavaSVN is run.
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
        long exportedRevision = -1;
        if (revision != SVNRevision.BASE && revision != SVNRevision.WORKING && revision != SVNRevision.COMMITTED && revision != SVNRevision.UNDEFINED) {
            SVNRepository repository = createRepository(null, srcPath, pegRevision, revision);
            long revisionNumber = getRevisionNumber(revision, repository, srcPath);
            exportedRevision = doRemoteExport(repository, revisionNumber, dstPath, eolStyle, force, recursive); 
        } else {
            if (revision == SVNRevision.UNDEFINED) {
                revision = SVNRevision.WORKING;
            }
            copyVersionedDir(srcPath, dstPath, revision, eolStyle, force, recursive);
        }
        dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(null, exportedRevision));
        return exportedRevision;
    }
    
    private void copyVersionedDir(File from, File to, SVNRevision revision, String eolStyle, boolean force, boolean recursive) throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.create(from);
        wcAccess.open(false, false);
        SVNEntry targetEntry = wcAccess.getTargetEntry();
        if (targetEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control or doesn't exist", from,
                    SVNErrorMessage.TYPE_WARNING);
            SVNErrorManager.error(err);
        }
        if (revision == SVNRevision.WORKING && targetEntry.isScheduledForDeletion()) {
            return;
        }
        if (revision != SVNRevision.WORKING && targetEntry.isScheduledForAddition()) {
            return;
        }
        if (targetEntry.isDirectory()) {
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
            SVNEntries entries = wcAccess.getTarget().getEntries();
            for (Iterator ents = entries.entries(false); ents.hasNext();) {
                SVNEntry entry = (SVNEntry) ents.next();
                if (entry.isDirectory()) {
                    if ("".equals(entry.getName())) {
                        continue;
                    } else if (recursive) {
                        File childTo = new File(to, entry.getName());
                        File childFrom = new File(from, entry.getName());
                        copyVersionedDir(childFrom, childTo, revision, eolStyle, force, recursive);
                    }
                } else if (entry.isFile()) {
                    File childTo = new File(to, entry.getName());
                    copyVersionedFile(childTo, wcAccess.getTarget(), entry.getName(), revision, eolStyle);
                }
            }
        } else if (targetEntry.isFile()) {
            copyVersionedFile(to, wcAccess.getTarget(), wcAccess.getTargetName(), revision, eolStyle);
        }
    }

    private void copyVersionedFile(File dstPath, SVNDirectory dir, String fileName, SVNRevision revision, String eol) throws SVNException {
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry(fileName, false);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control or doesn't exist", 
                    dir.getFile(fileName),
                    SVNErrorMessage.TYPE_WARNING);
            SVNErrorManager.error(err);
        }
        if (revision == SVNRevision.WORKING && entry.isScheduledForDeletion()) {
            return;
        }
        if (revision != SVNRevision.WORKING && entry.isScheduledForAddition()) {
            return;
        }
        boolean modified = false;
        SVNProperties props = null;
        long timestamp;
        if (revision != SVNRevision.WORKING) {
            props = dir.getBaseProperties(fileName, false);
        } else {
            props = dir.getProperties(fileName, false);
            modified = dir.hasTextModifications(fileName, false);
        }
        boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;
        boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
        String keywords = props.getPropertyValue(SVNProperty.KEYWORDS);
        byte[] eols = eol != null ? SVNTranslator.getEOL(eol) : null;
        if (eols == null) {
            eol = props.getPropertyValue(SVNProperty.EOL_STYLE);
            eols = SVNTranslator.getWorkingEOL(eol);
        }
        if (modified && !special) {
            timestamp = dir.getFile(fileName).lastModified();
        } else {
            timestamp = SVNTimeUtil.parseDateAsLong(entry.getCommittedDate());
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
            keywordsMap = SVNTranslator.computeKeywords(keywords, entry.getURL(), author, entry.getCommittedDate(), rev);            
        }
        File srcFile = revision == SVNRevision.WORKING ? dir.getFile(fileName) : dir.getBaseFile(fileName, false);
        SVNFileType fileType = SVNFileType.getType(srcFile);
        if (fileType == SVNFileType.SYMLINK && revision == SVNRevision.WORKING) {
            // base will be translated OK, but working not.
            File tmpBaseFile = dir.getBaseFile(fileName, true);
            try {
                SVNTranslator.translate(srcFile, tmpBaseFile, eols, keywordsMap, special, false);
                SVNTranslator.translate(tmpBaseFile, dstPath, eols, keywordsMap, special, true);
            } finally {
                tmpBaseFile.delete();
            }
        } else {
            SVNTranslator.translate(srcFile, dstPath, eols, keywordsMap, special, true);
        }
        if (executable) {
            SVNFileUtil.setExecutable(dstPath, true);
        }
        if (!special && timestamp > 0) {
            dstPath.setLastModified(timestamp);
        }
    }

    private long doRemoteExport(SVNRepository repository, final long revNumber, File dstPath, String eolStyle, boolean force, boolean recursive) throws SVNException {
        SVNNodeKind dstKind = repository.checkPath("", revNumber);
        if (dstKind == SVNNodeKind.DIR) {
            SVNExportEditor editor = new SVNExportEditor(this, repository.getLocation().toString(), dstPath,  force, eolStyle);
            repository.update(revNumber, null, recursive, new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, revNumber, true);
                    reporter.finishReport();
                }
            }, SVNCancellableEditor.newInstance(editor, this));
            // nothing may be created.
            SVNFileType fileType = SVNFileType.getType(dstPath);
            if (fileType == SVNFileType.NONE) {
                editor.openRoot(revNumber);
            }
            if (!isIgnoreExternals() && recursive) {
                Map externals = editor.getCollectedExternals();
                for (Iterator files = externals.keySet().iterator(); files.hasNext();) {
                    File rootFile = (File) files.next();
                    String propValue = (String) externals.get(rootFile);
                    if (propValue == null) {
                        continue;
                    }
                    SVNExternalInfo[] infos = SVNWCAccess.parseExternals("", propValue);
                    for (int i = 0; i < infos.length; i++) {
                        File targetDir = new File(rootFile, infos[i].getPath());
                        SVNURL srcURL = infos[i].getOldURL();
                        long externalRevNumber = infos[i].getOldRevision();
                        SVNRevision srcRevision = externalRevNumber >=0 ? SVNRevision.create(externalRevNumber) : SVNRevision.HEAD;
                        String relativePath =  targetDir.equals(dstPath) ? "" : targetDir.getAbsolutePath().substring(dstPath.getAbsolutePath().length() + 1);
                        relativePath = relativePath.replace(File.separatorChar, '/');
                        dispatchEvent(SVNEventFactory.createUpdateExternalEvent(null, relativePath));
                        try {
                            setEventPathPrefix(relativePath);
                            doExport(srcURL, targetDir, srcRevision, srcRevision, eolStyle, force, recursive);
                        } catch (Throwable th) {
                            dispatchEvent(new SVNEvent(th.getMessage()));
                        } finally {
                            setEventPathPrefix(null);
                        }
                    }
                }
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
            Map properties = new HashMap();
            OutputStream os = null;
            File tmpFile = SVNFileUtil.createUniqueFile(dstPath.getParentFile(), dstPath.getName(), ".tmp");
            os = SVNFileUtil.openFileForWriting(tmpFile);
            try {
                repository.getFile("", revNumber, properties, os);
            } finally {
                SVNFileUtil.closeFile(os);
            }
            if (force && dstPath.exists()) {
                SVNFileUtil.deleteAll(dstPath, this);
            }
            Map keywords = SVNTranslator.computeKeywords((String) properties.get(SVNProperty.KEYWORDS), url,
                            (String) properties.get(SVNProperty.LAST_AUTHOR),
                            (String) properties.get(SVNProperty.COMMITTED_DATE),
                            (String) properties.get(SVNProperty.COMMITTED_REVISION));
            byte[] eols = null;
            if (SVNProperty.EOL_STYLE_NATIVE.equals(properties.get(SVNProperty.EOL_STYLE))) {
                eols = SVNTranslator.getWorkingEOL(eolStyle != null ? eolStyle : (String) properties.get(SVNProperty.EOL_STYLE));
            } else if (properties.containsKey(SVNProperty.EOL_STYLE)) {
                eols = SVNTranslator.getWorkingEOL((String) properties.get(SVNProperty.EOL_STYLE));
            }
            SVNTranslator.translate(tmpFile, dstPath, eols, keywords, properties.get(SVNProperty.SPECIAL) != null, true);
            tmpFile.delete();
            if (properties.get(SVNProperty.EXECUTABLE) != null) {
                SVNFileUtil.setExecutable(dstPath, true);
            }
            dispatchEvent(SVNEventFactory.createExportAddedEvent(dstPath.getParentFile(), dstPath, SVNNodeKind.FILE));            
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
    public void doRelocate(File dst, SVNURL oldURL, SVNURL newURL, boolean recursive) throws SVNException {
        doRelocate(dst, oldURL, newURL, recursive, true);        
    }
    
    public void doRelocate(File dst, SVNURL oldURL, SVNURL newURL, boolean recursive, boolean validateUUID) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess(dst);
        try {
            wcAccess.open(true, recursive);
            SVNEntry entry = wcAccess.getTargetEntry();
            String name = "";
            if (entry != null && entry.isFile()) {
                name = entry.getName();
            }
            doRelocate(wcAccess.getTarget(), name, oldURL.toString(), newURL.toString(), recursive, validateUUID ? new HashMap() : null);
        } finally {
            wcAccess.close(true);

        }
    }

    private void handleExternals(SVNWCAccess wcAccess) throws SVNException {
        for (Iterator externals = wcAccess.externals(); externals.hasNext();) {
            SVNExternalInfo external = (SVNExternalInfo) externals.next();
            if (external.getOldURL() == null && external.getNewURL() == null) {
                continue;
            }
            long revNumber = external.getNewRevision();
            SVNRevision revision = revNumber >= 0 ? SVNRevision.create(revNumber) : SVNRevision.HEAD;
            setEventPathPrefix(external.getPath());
            try {
                if (external.getOldURL() == null) {
                    external.getFile().mkdirs();
                    dispatchEvent(SVNEventFactory.createUpdateExternalEvent(wcAccess, ""));
                    doCheckout(external.getNewURL(), external.getFile(), revision, revision, true);
                } else if (external.getNewURL() == null) {
                    if (SVNWCAccess.isVersionedDirectory(external.getFile())) {
                        SVNWCAccess externalAccess = createWCAccess(external.getFile());
                        try {
                            externalAccess.open(true, true);
                            externalAccess.getAnchor().destroy("", true);
                        } finally {
                            externalAccess.close(true);
                        }
                    }
                } else if (external.isModified()) {
                    deleteExternal(external);
                    external.getFile().mkdirs();
                    dispatchEvent(SVNEventFactory.createUpdateExternalEvent(wcAccess, ""));
                    doCheckout(external.getNewURL(), external.getFile(), revision, revision, true);
                } else {
                    if (!external.getFile().isDirectory()) {
                        external.getFile().mkdirs();
                        doCheckout(external.getNewURL(), external.getFile(), revision, revision, true);
                    } else {
                        String url = null;
                        if (SVNWCAccess.isVersionedDirectory(external.getFile())) {
                            SVNWCAccess externalAccess = createWCAccess(external.getFile());
                            SVNEntry entry = externalAccess.getTargetEntry();
                            url = entry.getURL();
                        }
                        if (!external.getNewURL().toString().equals(url)) {
                            deleteExternal(external);
                            external.getFile().mkdirs();
                            dispatchEvent(SVNEventFactory.createUpdateExternalEvent(wcAccess, ""));
                            doCheckout(external.getNewURL(), external.getFile(), revision, revision, true);
                        } else {
                            dispatchEvent(SVNEventFactory.createUpdateExternalEvent(wcAccess, ""));
                            doUpdate(external.getFile(), revision, true);
                        }
                    }
                }
            } catch (Throwable th) {
                dispatchEvent(new SVNEvent(th.getMessage()));
                SVNDebugLog.logInfo(th);
            } finally {
                setEventPathPrefix(null);
            }
        }
    }

    private void deleteExternal(SVNExternalInfo external) throws SVNException {
        if (SVNWCAccess.isVersionedDirectory(external.getFile())) {
            SVNWCAccess externalAccess = createWCAccess(external.getFile());

            try {
                externalAccess.open(true, true);
                externalAccess.getAnchor().destroy("", true);
            } catch (Throwable th) {
                SVNDebugLog.logInfo(th);
            } finally {
                externalAccess.close(true);
            }
        }
        if (external.getFile().exists()) {
            external.getFile().getParentFile().mkdirs();
            File newLocation = SVNFileUtil.createUniqueFile(external.getFile().getParentFile(), external.getFile().getName(), ".OLD");
            SVNFileUtil.rename(external.getFile(), newLocation);
        }
    }

    private SVNDirectory createVersionedDirectory(File dstPath, SVNURL url, SVNURL rootURL, String uuid, long revNumber) throws SVNException {
        SVNDirectory.createVersionedDirectory(dstPath);
        // add entry first.
        SVNDirectory dir = new SVNDirectory(null, "", dstPath);
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry("", true);
        if (entry == null) {
            entry = entries.addEntry("");
        }
        entry.setURL(url.toString());
        entry.setUUID(uuid);
        entry.setRepositoryRootURL(rootURL);
        entry.setKind(SVNNodeKind.DIR);
        entry.setRevision(revNumber);
        entry.setIncomplete(true);

        entries.save(true);
        return dir;
    }
    
    private Map validateRelocateTargetURL(SVNURL targetURL, String expectedUUID, Map validatedURLs) throws SVNException {
        if (validatedURLs == null) {
            return null;
        }
        for(Iterator targetURLs = validatedURLs.keySet().iterator(); targetURLs.hasNext();) {
            SVNURL validatedURL = (SVNURL) targetURLs.next();
            if (targetURL.toString().startsWith(validatedURL.toString())) {
                continue;
            }
            String validatedUUID = (String) validatedURLs.get(validatedURL);
            if (validatedUUID != null && validatedUUID.equals(expectedUUID)) {
                return validatedURLs;
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_RELOCATION, "The repository at ''{0}'' has uuid ''{1}'', but the WC has ''{2}''",
                    new Object[] {validatedURL, validatedUUID, expectedUUID});
            SVNErrorManager.error(err);
        }
        SVNRepository repos = createRepository(targetURL, true);
        repos.testConnection();
        String actualUUID = repos.getRepositoryUUID();
        if (actualUUID == null || !actualUUID.equals(expectedUUID)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_RELOCATION, "The repository at ''{0}'' has uuid ''{1}'', but the WC has ''{2}''",
                    new Object[] {targetURL, actualUUID, expectedUUID});
            SVNErrorManager.error(err);
        }
        validatedURLs.put(targetURL, actualUUID);
        return validatedURLs;
    }
    
    private Map relocateEntry(SVNEntry entry, String from, String to, Map validatedURLs) throws SVNException {
        if (entry.getRepositoryRoot() != null) {
            // that is what i do not understand :)
            String repos = entry.getRepositoryRoot();
            if (from.length() > repos.length()) {
                String fromPath = from.substring(repos.length());
                if (!to.endsWith(fromPath)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_RELOCATION, "Relocate can only change the repository part of URL");
                    SVNErrorManager.error(err);
                }
                from = repos;
                to = to.substring(0, to.length() - fromPath.length());
            }
            if (repos.startsWith(from)) {
                entry.setRepositoryRoot(to + repos.substring(from.length()));
            }
        }
        if (entry.getURL() != null && entry.getURL().startsWith(from)) {
            entry.setURL(to + entry.getURL().substring(from.length()));
            if (entry.getUUID() != null && validatedURLs != null) {
                validatedURLs = validateRelocateTargetURL(entry.getSVNURL(), entry.getUUID(), validatedURLs);
            }
        }
        if (entry.getCopyFromURL() != null && entry.getCopyFromURL().startsWith(from)) {
            entry.setCopyFromURL(to + entry.getCopyFromURL().substring(from.length()));
            if (entry.getUUID() != null && validatedURLs != null) {
                validatedURLs = validateRelocateTargetURL(entry.getCopyFromSVNURL(), entry.getUUID(), validatedURLs);
            }
        }
        return validatedURLs;
    }
    
    private Map doRelocate(SVNDirectory dir, String name, String from, String to, boolean recursive, Map validatedURLs) throws SVNException {
        if (!"".equals(name)) {
            SVNEntry entry = dir.getEntries().getEntry(name, true);
            relocateEntry(entry, from, to, validatedURLs);
            dir.getWCProperties(name).setPropertyValue(SVNProperty.WC_URL, null);
            dir.getEntries().save(true);
            return validatedURLs;
        }
        SVNEntry rootEntry = dir.getEntries().getEntry("", true);
        validatedURLs = relocateEntry(rootEntry, from, to, validatedURLs);
        dir.getWCProperties("").setPropertyValue(SVNProperty.WC_URL, null);
        // now all child entries that doesn't has repos/url has new values.
        for(Iterator ents = dir.getEntries().entries(true); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            if (recursive && entry.isDirectory() && 
                    (entry.isScheduledForAddition() || !entry.isDeleted()) &&
                    !entry.isAbsent()) {
                SVNDirectory childDir = dir.getChildDirectory(entry.getName());
                if (childDir != null) {
                    validatedURLs = doRelocate(childDir, "", from, to, recursive, validatedURLs);
                }
            }
            validatedURLs = relocateEntry(entry, from, to, validatedURLs);
            dir.getWCProperties(entry.getName()).setPropertyValue(SVNProperty.WC_URL, null);
        }
        dir.getEntries().save(true);
        return validatedURLs;
        
    }
}
