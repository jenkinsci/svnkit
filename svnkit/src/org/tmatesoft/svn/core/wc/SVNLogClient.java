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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNAnnotationGenerator;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * The <b>SVNLogClient</b> class is intended for such purposes as getting
 * revisions history, browsing repository entries and annotating file contents.
 * 
 * <p>
 * Here's a list of the <b>SVNLogClient</b>'s methods 
 * matched against corresponing commands of the <b>SVN</b> command line 
 * client:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNKit</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doLog()</td><td>'svn log'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doList()</td><td>'svn list'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doAnnotate()</td><td>'svn blame'</td>
 * </tr>
 * </table>
 * 
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNLogClient extends SVNBasicClient {

    private SVNDiffOptions myDiffOptions;

    /**
     * Constructs and initializes an <b>SVNLogClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNLogClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNLogClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the 
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     * 
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver     
     */
    public SVNLogClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    public SVNLogClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }
    
    /**
     * Sets diff options for this client to use in annotate operations.
     * 
     * @param diffOptions diff options object
     */
    public void setDiffOptions(SVNDiffOptions diffOptions) {
        myDiffOptions = diffOptions;
    }

    /**
     * Gets the diff options that are used in annotate operations 
     * by this client. Creates a new one if none was used before.
     * 
     * @return diff options
     */
    public SVNDiffOptions getDiffOptions() {
        if (myDiffOptions == null) {
            myDiffOptions = new SVNDiffOptions();
        }
        return myDiffOptions;
    }

    
    /**
     * Obtains annotation information for each file text line from a repository
     * (using a Working Copy path to get a corresponding URL) and passes it to a 
     * provided annotation handler. 
     * 
     * <p>
     * If <code>startRevision</code> is invalid (for example, 
     * <code>startRevision = </code>{@link SVNRevision#UNDEFINED UNDEFINED}) then
     * it's set to revision 1.
     * 
     * <p>
     * Calling this method is equivalent to 
     * <code>doAnnotate(path, pegRevision, startRevision, endRevision, false, handler)</code>.
     * 
     * @param  path           a WC file item to be annotated
     * @param  pegRevision    a revision in which <code>path</code> is first looked up
     *                        in the repository
     * @param  startRevision  a revision for an operation to start from
     * @param  endRevision    a revision for an operation to stop at
     * @param  handler        a caller's handler to process annotation information
     * @throws SVNException   if <code>startRevision > endRevision</code>
     * @see                   #doAnnotate(SVNURL, SVNRevision, SVNRevision, SVNRevision, ISVNAnnotateHandler)
     */
    public void doAnnotate(File path, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, ISVNAnnotateHandler handler) throws SVNException {
        doAnnotate(path, pegRevision, startRevision, endRevision, false, handler);
    }

    /**
     * Obtains annotation information for each file text line from a repository
     * (using a Working Copy path to get a corresponding URL) and passes it to a 
     * provided annotation handler. 
     * 
     * <p>
     * If <code>startRevision</code> is invalid (for example, 
     * <code>startRevision = </code>{@link SVNRevision#UNDEFINED UNDEFINED}) then
     * it's set to revision 1.
     * 
     * @param  path           a WC file item to be annotated
     * @param  pegRevision    a revision in which <code>path</code> is first looked up
     *                        in the repository
     * @param  startRevision  a revision for an operation to start from
     * @param  endRevision    a revision for an operation to stop at
     * @param  force          forces operation to run (all files to be treated as 
     *                        text, no matter what SVNKit has inferred from the mime-type 
     *                        property) 
     * @param  handler        a caller's handler to process annotation information
     * @throws SVNException
     * @since                 1.1
     */
    public void doAnnotate(File path, SVNRevision pegRevision, SVNRevision startRevision, 
                           SVNRevision endRevision, boolean force, ISVNAnnotateHandler handler) throws SVNException {
        doAnnotate(path, pegRevision, startRevision, endRevision, force, false, handler, null);
    }

    public void doAnnotate(File path, SVNRevision pegRevision, SVNRevision startRevision, 
                           SVNRevision endRevision, boolean force, boolean includeMergedRevisions, 
                           ISVNAnnotateHandler handler, String inputEncoding) throws SVNException {
        if (startRevision == null || !startRevision.isValid()) {
            startRevision = SVNRevision.create(1);
        }
        if (endRevision == null || !endRevision.isValid()) {
            endRevision = pegRevision;
        }
        SVNRepository repos = createRepository(null, path, pegRevision, endRevision);
        long endRev = getRevisionNumber(endRevision, repos, path);
        long startRev = getRevisionNumber(startRevision, repos, path);
        if (endRev < startRev) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Start revision must precede end revision"));
        }
        File tmpFile = new File(path.getParentFile(), SVNFileUtil.getAdminDirectoryName());
        tmpFile = new File(tmpFile, "tmp/text-base");
        if (!tmpFile.isDirectory()) {
            tmpFile = SVNFileUtil.createTempDirectory("annotate");
        }
        doAnnotate(path.getAbsolutePath(), startRev, tmpFile, repos, endRev, force, handler, 
                   inputEncoding, includeMergedRevisions);
    }

    /**
     * Obtains annotation information for each file text line from a repository
     * and passes it to a provided annotation handler. 
     * 
     * <p>
     * If <code>startRevision</code> is invalid (for example, 
     * <code>startRevision = </code>{@link SVNRevision#UNDEFINED UNDEFINED}) then
     * it's set to revision 1.
     * 
     * <p>
     * Calling this method is equivalent to  
     * <code>doAnnotate(url, pegRevision, startRevision, endRevision, handler, null)</code>.
     * 
     * @param  url            a URL of a text file that is to be annotated 
     * @param  pegRevision    a revision in which <code>path</code> is first looked up
     *                        in the repository
     * @param  startRevision  a revision for an operation to start from
     * @param  endRevision    a revision for an operation to stop at
     * @param  handler        a caller's handler to process annotation information
     * @throws SVNException   if <code>startRevision > endRevision</code>
     * @see                   #doAnnotate(File, SVNRevision, SVNRevision, SVNRevision, ISVNAnnotateHandler)
     */
    public void doAnnotate(SVNURL url, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, ISVNAnnotateHandler handler) throws SVNException {
        doAnnotate(url, pegRevision, startRevision, endRevision, handler, null);
    }

    /**
     * Obtains annotation information for each file text line from a repository
     * and passes it to a provided annotation handler. 
     * 
     * <p>
     * If <code>startRevision</code> is invalid (for example, 
     * <code>startRevision = </code>{@link SVNRevision#UNDEFINED UNDEFINED}) then
     * it's set to revision 1.
     * 
     * <p>
     * Calling this method is equivalent to  
     * <code>doAnnotate(url, pegRevision, startRevision, endRevision, false, handler, inputEncoding)</code>.
     * 
     * @param  url            a URL of a text file that is to be annotated 
     * @param  pegRevision    a revision in which <code>path</code> is first looked up
     *                        in the repository
     * @param  startRevision  a revision for an operation to start from
     * @param  endRevision    a revision for an operation to stop at
     * @param  handler        a caller's handler to process annotation information
     * @param  inputEncoding  a desired character set (encoding) of text lines
     * @throws SVNException
     */
    public void doAnnotate(SVNURL url, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, ISVNAnnotateHandler handler, String inputEncoding) throws SVNException {
        doAnnotate(url, pegRevision, startRevision, endRevision, false, handler, inputEncoding);
    }

    /**
     * Obtains annotation information for each file text line from a repository
     * and passes it to a provided annotation handler. 
     * 
     * <p>
     * If <code>startRevision</code> is invalid (for example, 
     * <code>startRevision = </code>{@link SVNRevision#UNDEFINED UNDEFINED}) then
     * it's set to revision 1.
     *
     * <p>
     * If <code>inputEncoding</code> is <span class="javakeyword">null</span> then 
     * <span class="javastring">"file.encoding"</span> system property is used.
     *  
     * @param  url            a URL of a text file that is to be annotated 
     * @param  pegRevision    a revision in which <code>path</code> is first looked up
     *                        in the repository
     * @param  startRevision  a revision for an operation to start from
     * @param  endRevision    a revision for an operation to stop at
     * @param  force          forces operation to run (all files to be treated as 
     *                        text, no matter what SVNKit has inferred from the mime-type 
     *                        property) 
     * @param  handler        a caller's handler to process annotation information
     * @param  inputEncoding  a desired character set (encoding) of text lines
     * @throws SVNException
     * @since                 1.1
     */
	public void doAnnotate(SVNURL url, SVNRevision pegRevision, SVNRevision startRevision, 
                           SVNRevision endRevision, boolean force, ISVNAnnotateHandler handler, 
                           String inputEncoding) throws SVNException {
	    doAnnotate(url, pegRevision, startRevision, endRevision, force, false, 
                   handler, inputEncoding);
	}

    public void doAnnotate(SVNURL url, SVNRevision pegRevision, SVNRevision startRevision, 
                           SVNRevision endRevision, boolean force, boolean includeMergedRevisions, 
                           ISVNAnnotateHandler handler, String inputEncoding) throws SVNException {
        if (startRevision == null || !startRevision.isValid()) {
            startRevision = SVNRevision.create(1);
        }
        if (endRevision == null || !endRevision.isValid()) {
            endRevision = pegRevision;
        }
        SVNRepository repos = createRepository(url, null, pegRevision, endRevision);
        long endRev = getRevisionNumber(endRevision, repos, null);
        long startRev = getRevisionNumber(startRevision, repos, null);
        if (endRev < startRev) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                                                         "Start revision must precede end revision"));
        }
        File tmpFile = SVNFileUtil.createTempDirectory("annotate");
        doAnnotate(repos.getLocation().toDecodedString(), startRev, tmpFile, repos, endRev, force, 
                   handler, inputEncoding, includeMergedRevisions);
    
    }
    
    private void doAnnotate(String path, long startRev, File tmpFile, SVNRepository repos, 
                            long endRev, boolean force, ISVNAnnotateHandler handler, 
                            String inputEncoding, boolean includeMergedRevisions) throws SVNException {
        SVNAnnotationGenerator generator = new SVNAnnotationGenerator(path, tmpFile, startRev, 
                                                                      force, includeMergedRevisions,
                                                                      getDiffOptions(), this);
        try {
            repos.getFileRevisions("", startRev > 0 ? startRev - 1 : startRev, 
                                   endRev, includeMergedRevisions, generator);
            generator.reportAnnotations(handler, inputEncoding);
        } finally {
            generator.dispose();
            SVNFileUtil.deleteAll(tmpFile, !"text-base".equals(tmpFile.getName()), null);
        }
    }
    
    /**
     * Gets commit log messages with other revision specific 
     * information from a repository (using Working Copy paths to get 
     * corresponding URLs) and passes them to a log entry handler for
     * processing. Useful for observing the history of affected paths,
     * author, date and log comments information per revision.
     * 
     * <p>
     * If <code>paths</code> is not empty then the result will be restricted
     * to only those revisions from the specified range [<code>startRevision</code>, <code>endRevision</code>], 
     * where <code>paths</code> were changed in the repository. To cover the
     * entire range set <code>paths</code> just to an empty array:
     * <pre class="javacode">
     *     logClient.doLog(<span class="javakeyword">new</span> File[]{<span class="javastring">""</span>},..);</pre><br />
     * <p>
     * If <code>startRevision</code> is valid but <code>endRevision</code> is
     * not (for example, <code>endRevision = </code>{@link SVNRevision#UNDEFINED UNDEFINED})
     * then <code>endRevision</code> is equated to <code>startRevision</code>.
     * 
     * <p>
     * If <code>startRevision</code> is invalid (for example, {@link SVNRevision#UNDEFINED UNDEFINED}) 
     * then it's equated to {@link SVNRevision#BASE BASE}. In this case if <code>endRevision</code> is
     * also invalid, then <code>endRevision</code> is set to revision 0.
     * 
     * <p>
     * Calling this method is equivalent to 
     * <code>doLog(paths, SVNRevision.UNDEFINED, startRevision, endRevision, stopOnCopy, reportPaths, limit, handler)</code>.
     * 
     * @param  paths           an array of Working Copy paths,
     *                         should not be <span class="javakeyword">null</span>
     * @param  startRevision   a revision for an operation to start from (including
     *                         this revision)    
     * @param  endRevision     a revision for an operation to stop at (including
     *                         this revision)
     * @param  stopOnCopy      <span class="javakeyword">true</span> not to cross
     *                         copies while traversing history, otherwise copies history
     *                         will be also included into processing
     * @param  reportPaths     <span class="javakeyword">true</span> to report
     *                         of all changed paths for every revision being processed 
     *                         (those paths will be available by calling 
     *                         {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  limit           a maximum number of log entries to be processed 
     * @param  handler         a caller's log entry handler
     * @throws SVNException    if one of the following is true:
     *                         <ul>
     *                         <li>a path is not under version control
     *                         <li>can not obtain a URL of a WC path - there's no such
     *                         entry in the Working Copy
     *                         <li><code>paths</code> contain entries that belong to
     *                         different repositories
     *                         </ul>
     * @see                    #doLog(SVNURL, String[], SVNRevision, SVNRevision, SVNRevision, boolean, boolean, long, ISVNLogEntryHandler)                        
     */
    public void doLog(File[] paths, SVNRevision startRevision, SVNRevision endRevision, boolean stopOnCopy, boolean reportPaths, long limit, final ISVNLogEntryHandler handler) throws SVNException {
        doLog(paths, SVNRevision.UNDEFINED, startRevision, endRevision, stopOnCopy, reportPaths, limit, handler);
    }
    
    public void doLog(ISVNPathList pathList, SVNRevision startRevision, SVNRevision endRevision, 
                      boolean stopOnCopy, boolean reportPaths, boolean includeMergedRevisions, 
                      boolean omitLogText, long limit, final ISVNLogEntryHandler handler) throws SVNException {
        if (pathList == null) {
            return;
        }

        File[] paths = pathList.getPaths();
        
        SVNRevision pegRevision = pathList.getPegRevision();
        if (paths == null || paths.length == 0 || handler == null) {
            return;
        }
        
        if (startRevision.isValid() && !endRevision.isValid()) {
            endRevision = startRevision;
        } else if (!startRevision.isValid()) {
            if (!pegRevision.isValid()) {
                startRevision = SVNRevision.BASE;
            } else {
                startRevision = pegRevision;
            }
            if (!endRevision.isValid()) {
                endRevision = SVNRevision.create(0);
            }
        }
        ISVNLogEntryHandler wrappingHandler = new ISVNLogEntryHandler() {
            public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                checkCancelled();
                handler.handleLogEntry(logEntry);
            }
        };
        SVNURL[] urls = new SVNURL[paths.length];
        SVNWCAccess wcAccess = createWCAccess();
        for (int i = 0; i < paths.length; i++) {
            checkCancelled();
            File path = paths[i];
            wcAccess.probeOpen(path, false, 0); 
            SVNEntry entry = wcAccess.getVersionedEntry(path, false); 
            if (entry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Entry ''{0}'' has no URL", path);
                SVNErrorManager.error(err);
            }
            urls[i] = entry.getSVNURL();
            wcAccess.closeAdminArea(path);
        }
        if (urls.length == 0) {
            return;
        }
        Collection targets = new TreeSet();
        SVNURL baseURL = SVNURLUtil.condenceURLs(urls, targets, true);
        if (baseURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "target log paths belong to different repositories");
            SVNErrorManager.error(err);
        }
        if (targets.isEmpty()) {
            targets.add("");
        }
        SVNRevision rev = SVNRevision.UNDEFINED;
        if (startRevision.getNumber() >= 0 && endRevision.getNumber() >= 0) {
            rev = startRevision.getNumber() > endRevision.getNumber() ? startRevision : endRevision;
        } else if (startRevision.getDate() != null && endRevision.getDate() != null) {
            rev = startRevision.getDate().compareTo(endRevision.getDate()) > 0 ? startRevision : endRevision;
        } 
        SVNRepository repos = rev.isValid() ? //!startRevision.isLocal() && !pegRevision.isLocal() ?
                createRepository(baseURL, null, pegRevision, rev) : createRepository(baseURL, true);
        String[] targetPaths = (String[]) targets.toArray(new String[targets.size()]);
        for (int i = 0; i < targetPaths.length; i++) {
            targetPaths[i] = SVNEncodingUtil.uriDecode(targetPaths[i]);
        }
        if (startRevision.isLocal() || endRevision.isLocal()) {
            for (int i = 0; i < paths.length; i++) {
                checkCancelled();
                long startRev = getRevisionNumber(startRevision, repos, paths[i]);
                long endRev = getRevisionNumber(endRevision, repos, paths[i]);
                repos.log(targetPaths, startRev, endRev, reportPaths, stopOnCopy, limit, 
                          includeMergedRevisions, omitLogText, wrappingHandler);
            }
        } else {
            long startRev = getRevisionNumber(startRevision, repos, null);
            long endRev = getRevisionNumber(endRevision, repos, null);
            repos.log(targetPaths, startRev, endRev, reportPaths, stopOnCopy, limit, 
                      includeMergedRevisions, omitLogText, wrappingHandler);
        }
    }
    
    /**
     * Gets commit log messages with other revision specific 
     * information from a repository (using Working Copy paths to get 
     * corresponding URLs) and passes them to a log entry handler for
     * processing. Useful for observing the history of affected paths,
     * author, date and log comments information per revision.
     * 
     * <p>
     * If <code>paths</code> is not empty then the result will be restricted
     * to only those revisions from the specified range [<code>startRevision</code>, <code>endRevision</code>], 
     * where <code>paths</code> were changed in the repository. To cover the
     * entire range set <code>paths</code> just to an empty array:
     * <pre class="javacode">
     *     logClient.doLog(<span class="javakeyword">new</span> File[]{<span class="javastring">""</span>},..);</pre><br />
     * <p>
     * If <code>startRevision</code> is valid but <code>endRevision</code> is
     * not (for example, <code>endRevision = </code>{@link SVNRevision#UNDEFINED UNDEFINED})
     * then <code>endRevision</code> is equated to <code>startRevision</code>.
     * 
     * <p>
     * If <code>startRevision</code> is invalid (for example, {@link SVNRevision#UNDEFINED UNDEFINED}) 
     * then it's equated to {@link SVNRevision#BASE BASE}. In this case if <code>endRevision</code> is
     * also invalid, then <code>endRevision</code> is set to revision 0.
     * 
     * @param  paths           an array of Working Copy paths,
     *                         should not be <span class="javakeyword">null</span>
     * @param  pegRevision     a revision in which <code>path</code> is first looked up
     *                         in the repository
     * @param  startRevision   a revision for an operation to start from (including
     *                         this revision)    
     * @param  endRevision     a revision for an operation to stop at (including
     *                         this revision)
     * @param  stopOnCopy      <span class="javakeyword">true</span> not to cross
     *                         copies while traversing history, otherwise copies history
     *                         will be also included into processing
     * @param  reportPaths     <span class="javakeyword">true</span> to report
     *                         of all changed paths for every revision being processed 
     *                         (those paths will be available by calling 
     *                         {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  limit           a maximum number of log entries to be processed 
     * @param  handler         a caller's log entry handler
     * @throws SVNException    if one of the following is true:
     *                         <ul>
     *                         <li>a path is not under version control
     *                         <li>can not obtain a URL of a WC path - there's no such
     *                         entry in the Working Copy
     *                         <li><code>paths</code> contain entries that belong to
     *                         different repositories
     *                         </ul>
     */
    public void doLog(File[] paths, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, boolean stopOnCopy, boolean reportPaths, long limit, final ISVNLogEntryHandler handler) throws SVNException {
        SVNPathList pathList = SVNPathList.create(paths, pegRevision);
        doLog(pathList, startRevision, endRevision, stopOnCopy, reportPaths, 
              false, false, limit, handler);
    }
    
    /**
     * Gets commit log messages with other revision specific 
     * information from a repository and passes them to a log entry 
     * handler for processing. Useful for observing the history of 
     * affected paths, author, date and log comments information per revision.
     * 
     * <p>
     * If <code>paths</code> is <span class="javakeyword">null</span> or empty
     * then <code>url</code> is the target path that is used to restrict the result
     * to only those revisions from the specified range [<code>startRevision</code>, <code>endRevision</code>], 
     * where <code>url</code> was changed in the repository. Otherwise if <code>paths</code> is
     * not empty then <code>url</code> is the root for all those paths (that are
     * used for restricting the result).
     * 
     * <p>
     * If <code>startRevision</code> is valid but <code>endRevision</code> is
     * not (for example, <code>endRevision = </code>{@link SVNRevision#UNDEFINED UNDEFINED})
     * then <code>endRevision</code> is equated to <code>startRevision</code>.
     * 
     * <p>
     * If <code>startRevision</code> is invalid (for example, {@link SVNRevision#UNDEFINED UNDEFINED}) 
     * then it's equated to {@link SVNRevision#HEAD HEAD}. In this case if <code>endRevision</code> is
     * also invalid, then <code>endRevision</code> is set to revision 0.
     * 
     * 
     * @param  url             a target URL            
     * @param  paths           an array of paths relative to the target 
     *                         <code>url</code>
     * @param  pegRevision     a revision in which <code>url</code> is first looked up
     * @param  startRevision   a revision for an operation to start from (including
     *                         this revision)    
     * @param  endRevision     a revision for an operation to stop at (including
     *                         this revision)
     * @param  stopOnCopy      <span class="javakeyword">true</span> not to cross
     *                         copies while traversing history, otherwise copies history
     *                         will be also included into processing
     * @param  reportPaths     <span class="javakeyword">true</span> to report
     *                         of all changed paths for every revision being processed 
     *                         (those paths will be available by calling 
     *                         {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  limit           a maximum number of log entries to be processed 
     * @param  handler         a caller's log entry handler
     * @throws SVNException
     * @see                    #doLog(File[], SVNRevision, SVNRevision, boolean, boolean, long, ISVNLogEntryHandler)
     * @since                  1.1, new in Subversion 1.4
     */
    public void doLog(SVNURL url, String[] paths, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, boolean stopOnCopy, boolean reportPaths, long limit, final ISVNLogEntryHandler handler) throws SVNException {
        doLog(url, paths, pegRevision, startRevision, endRevision, stopOnCopy, 
              reportPaths, false, false, limit, handler);
    }
    
    public void doLog(SVNURL url, String[] paths, SVNRevision pegRevision, 
                      SVNRevision startRevision, SVNRevision endRevision, boolean stopOnCopy, 
                      boolean reportPaths, boolean includeMergeInfo, boolean omitLogText, 
                      long limit, final ISVNLogEntryHandler handler) throws SVNException {
        if (startRevision.isValid() && !endRevision.isValid()) {
            endRevision = startRevision;
        } else if (!startRevision.isValid()) {
            if (!pegRevision.isValid()) {
                startRevision = SVNRevision.HEAD;
            } else {
                startRevision = pegRevision;
            }
            if (!endRevision.isValid()) {
                endRevision = SVNRevision.create(0);
            }
        }
        paths = paths == null || paths.length == 0 ? new String[] {""} : paths;
        ISVNLogEntryHandler wrappingHandler = new ISVNLogEntryHandler() {
            public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                checkCancelled();
                handler.handleLogEntry(logEntry);
            }
        };
        SVNRevision rev = SVNRevision.UNDEFINED;
        if (startRevision.getNumber() >= 0 && endRevision.getNumber() >= 0) {
            rev = startRevision.getNumber() > endRevision.getNumber() ? startRevision : endRevision;
        } else if (startRevision.getDate() != null && endRevision.getDate() != null) {
            rev = startRevision.getDate().compareTo(endRevision.getDate()) > 0 ? startRevision : endRevision;
        } 
        SVNRepository repos = rev.isValid() ? 
                createRepository(url, null, pegRevision, rev) : createRepository(url, true);
        checkCancelled();
        long startRev = getRevisionNumber(startRevision, repos, null);
        checkCancelled();
        long endRev = getRevisionNumber(endRevision, repos, null);
        checkCancelled();
        repos.log(paths, startRev, endRev, reportPaths, stopOnCopy, limit, 
                  includeMergeInfo, omitLogText, wrappingHandler);
    }
    
    public SVNLocationEntry getCopySource(File path, SVNRevision revision) throws SVNException {
        SVNRepository repos = createRepository(null, path, revision, revision);
        SVNWCAccess access = createWCAccess();
        SVNLocationEntry copyFromEntry = null;
        try {
            access.probeOpen(path, false, 0);
            SVNEntry entry = access.getVersionedEntry(path, false);
            SVNURL url = entry.getSVNURL();
            SVNURL reposRoot = entry.getRepositoryRootURL();
            if (reposRoot == null) {
                reposRoot = repos.getRepositoryRoot(true);
            }
            String targetPath = url.getPath().substring(reposRoot.getPath().length());
            if (!targetPath.startsWith("/")) {
                targetPath = "/" + targetPath;
            }
            
            SVNRevision oldestRevision = SVNRevision.create(1);
            CopyFromReceiver receiver = new CopyFromReceiver(targetPath); 
            try {
                doLog(new File[] {path}, revision, revision, oldestRevision, true, true, 0, receiver);
                copyFromEntry = receiver.getCopyFromLocation();
            } catch (SVNException e) {
                SVNErrorCode errCode = e.getErrorMessage().getErrorCode();
                if (errCode == SVNErrorCode.FS_NOT_FOUND || errCode == SVNErrorCode.RA_DAV_REQUEST_FAILED) {
                    return new SVNLocationEntry(SVNRepository.INVALID_REVISION, null);
                }
                throw e;
            }
        } finally {
            access.close();
        }

        return copyFromEntry == null ? new SVNLocationEntry(SVNRepository.INVALID_REVISION, null) 
                                     : copyFromEntry;
    }
    
    /**
     * Browses directory entries from a repository (using Working 
     * Copy paths to get corresponding URLs) and uses the provided dir 
     * entry handler to process them.
     * 
     * <p>
     * On every entry that this method stops it gets some useful entry 
     * information which is packed into an {@link org.tmatesoft.svn.core.SVNDirEntry}
     * object and passed to the <code>handler</code>'s 
     * {@link org.tmatesoft.svn.core.ISVNDirEntryHandler#handleDirEntry(SVNDirEntry) handleDirEntry()} method.
     *  
     * @param  path           a WC item to get its repository location            
     * @param  pegRevision    a revision in which the item's URL is first looked up
     * @param  revision       a target revision
     * @param  fetchLocks     <span class="javakeyword">true</span> to fetch locks 
     *                        information from a repository
     * @param  recursive      <span class="javakeyword">true</span> to
     *                        descend recursively (relevant for directories)    
     * @param  handler        a caller's directory entry handler (to process
     *                        info on an entry)
     * @throws SVNException 
     * @see                   #doList(SVNURL, SVNRevision, SVNRevision, boolean, ISVNDirEntryHandler)  
     */
    public void doList(File path, SVNRevision pegRevision, SVNRevision revision, boolean fetchLocks, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        doList(path, pegRevision, revision, fetchLocks, recursive ? SVNDepth.INFINITY : SVNDepth.IMMEDIATES, SVNDirEntry.DIRENT_ALL, handler);
    }
    
    public void doList(File path, SVNRevision pegRevision, SVNRevision revision, boolean fetchLocks, SVNDepth depth, int entryFields, ISVNDirEntryHandler handler) throws SVNException {
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.BASE;
        }
        SVNRepository repos = createRepository(null, path, pegRevision, revision);
        long rev = getRevisionNumber(revision, repos, path);
        doList(repos, rev, handler, fetchLocks, depth, entryFields);
    }

    /**
     * Browses directory entries from a repository (using Working 
     * Copy paths to get corresponding URLs) and uses the provided dir 
     * entry handler to process them.
     * 
     * <p>
     * On every entry that this method stops it gets some useful entry 
     * information which is packed into an {@link org.tmatesoft.svn.core.SVNDirEntry}
     * object and passed to the <code>handler</code>'s 
     * {@link org.tmatesoft.svn.core.ISVNDirEntryHandler#handleDirEntry(SVNDirEntry) handleDirEntry()} method.
     *  
     * @param  path           a WC item to get its repository location            
     * @param  pegRevision    a revision in which the item's URL is first looked up
     * @param  revision       a target revision
     * @param  recursive      <span class="javakeyword">true</span> to
     *                        descend recursively (relevant for directories)    
     * @param  handler        a caller's directory entry handler (to process
     *                        info on an entry)
     * @throws SVNException 
     * @see                   #doList(SVNURL, SVNRevision, SVNRevision, boolean, ISVNDirEntryHandler)  
     */
    public void doList(File path, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        doList(path, pegRevision, revision, false, recursive, handler);
        
    }
    
    /**
     * Browses directory entries from a repository and uses the provided 
     * dir entry handler to process them. This method is 
     * especially useful when having no Working Copy. 
     * 
     * <p>
     * On every entry that this method stops it gets some useful entry 
     * information which is packed into an {@link org.tmatesoft.svn.core.SVNDirEntry}
     * object and passed to the <code>handler</code>'s 
     * {@link org.tmatesoft.svn.core.ISVNDirEntryHandler#handleDirEntry(SVNDirEntry) handleDirEntry()} method.
     * 
     * @param  url            a repository location to be "listed"
     * @param  pegRevision    a revision in which the item's URL is first looked up
     * @param  revision       a target revision
     * @param  fetchLocks     <span class="javakeyword">true</span> to 
     *                        fetch locks information from repository
     * @param  recursive      <span class="javakeyword">true</span> to
     *                        descend recursively (relevant for directories)    
     * @param  handler        a caller's directory entry handler (to process
     *                        info on an entry)
     * @throws SVNException
     * @see                   #doList(File, SVNRevision, SVNRevision, boolean, ISVNDirEntryHandler)   
     */
    public void doList(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean fetchLocks, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        doList(url, pegRevision, revision, fetchLocks, recursive ? SVNDepth.INFINITY : SVNDepth.IMMEDIATES, SVNDirEntry.DIRENT_ALL, handler);
    }
    
    public void doList(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean fetchLocks, SVNDepth depth, int entryFields, ISVNDirEntryHandler handler) throws SVNException {
        long[] pegRev = new long[] {-1};
        SVNRepository repos = createRepository(url, null, pegRevision, revision, pegRev);
        if (pegRev[0] < 0) {
            pegRev[0] = getRevisionNumber(revision, repos, null);
        }
        doList(repos, pegRev[0], handler, fetchLocks, depth, entryFields);
    }

    /**
     * Browses directory entries from a repository and uses the provided 
     * dir entry handler to process them. This method is 
     * especially useful when having no Working Copy. 
     * 
     * <p>
     * On every entry that this method stops it gets some useful entry 
     * information which is packed into an {@link org.tmatesoft.svn.core.SVNDirEntry}
     * object and passed to the <code>handler</code>'s 
     * {@link org.tmatesoft.svn.core.ISVNDirEntryHandler#handleDirEntry(SVNDirEntry) handleDirEntry()} method.
     * 
     * @param  url            a repository location to be "listed"
     * @param  pegRevision    a revision in which the item's URL is first looked up
     * @param  revision       a target revision
     * @param  recursive      <span class="javakeyword">true</span> to
     *                        descend recursively (relevant for directories)    
     * @param  handler        a caller's directory entry handler (to process
     *                        info on an entry)
     * @throws SVNException
     * @see                   #doList(File, SVNRevision, SVNRevision, boolean, ISVNDirEntryHandler)   
     */
    public void doList(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        doList(url, pegRevision, revision, false, recursive, handler);
    }

    private void doList(SVNRepository repos, long rev, final ISVNDirEntryHandler handler, boolean fetchLocks, SVNDepth depth, int entryFields) throws SVNException {
        SVNURL url = repos.getLocation();
        SVNURL reposRoot = repos.getRepositoryRoot(true);
        SVNDirEntry entry = null;
        SVNException error = null;
        try {
            entry = repos.info("", rev); 
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_IMPLEMENTED) {
                error = svne;
            } else {
                throw svne;
            }
        }
        
        if (error != null) {
            SVNNodeKind kind = repos.checkPath("", rev);
            if (kind != SVNNodeKind.NONE) {
                if (!url.equals(reposRoot)) {
                    String name = SVNPathUtil.tail(repos.getLocation().getPath());
                    repos.setLocation(repos.getLocation().removePathTail(), false);
                    Collection dirEntries = repos.getDir("", rev, null, entryFields, (Collection) null);
                    repos.setLocation(url, false);
                    
                    for (Iterator ents = dirEntries.iterator(); ents.hasNext();) {
                        SVNDirEntry dirEntry = (SVNDirEntry) ents.next();
                        if (name.equals(dirEntry.getName())) {
                            entry = dirEntry;
                            break;
                        }
                    }
                    if (entry != null) {
                        entry.setRelativePath(kind == SVNNodeKind.FILE ? name : "");
                    }
                } else {
                    Map props = new HashMap();
                    repos.getDir("", rev, props, entryFields, (Collection) null);
                    Map revProps = repos.getRevisionProperties(rev, null);
                    String author = (String) revProps.get(SVNRevisionProperty.AUTHOR);
                    String dateStr = (String) revProps.get(SVNRevisionProperty.DATE);
                    Date datestamp = null;
                    if (dateStr != null) {
                        datestamp = SVNTimeUtil.parseDateString(dateStr);
                    }
                    entry = new SVNDirEntry(url, "", kind, 0, !props.isEmpty(), rev, datestamp, author);
                    entry.setRelativePath("");
                }
            }
        } else if (entry != null) {
            entry.setRelativePath(entry.getKind() == SVNNodeKind.DIR ? "" : entry.getName());
        }
        
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "URL ''{0}'' non-existent in that revision", url);
            SVNErrorManager.error(err);
        }
        
        final Map locksMap = new HashMap();
        if (fetchLocks) {
            SVNLock[] locks = new SVNLock[0];
            try {
                locks = repos.getLocks("");                
            } catch (SVNException e) {
                if (!(e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_IMPLEMENTED)) {
                    throw e;
                }                
            }
            
            if (locks != null && locks.length > 0) {
                SVNURL root = repos.getRepositoryRoot(true);
                for (int i = 0; i < locks.length; i++) {
                    String repositoryPath = locks[i].getPath();
                    locksMap.put(root.appendPath(repositoryPath, false), locks[i]); 
                }
            }
        }
        
        ISVNDirEntryHandler nestedHandler = new ISVNDirEntryHandler() {
            public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
                dirEntry.setLock((SVNLock) locksMap.get(dirEntry.getURL()));
                handler.handleDirEntry(dirEntry);
            }
        };

        nestedHandler.handleDirEntry(entry);
        if (entry.getKind() == SVNNodeKind.DIR && (depth == SVNDepth.FILES || 
                depth == SVNDepth.IMMEDIATES ||
                depth == SVNDepth.INFINITY)) {
            list(repos, "", rev, depth, entryFields, nestedHandler);
        }
    }

    private static void list(SVNRepository repository, String path, long rev, SVNDepth depth, int entryFields, ISVNDirEntryHandler handler) throws SVNException {
        if (depth == SVNDepth.EMPTY) {
            return;
        }
        Collection entries = new TreeSet();
        entries = repository.getDir(path, rev, null, entryFields, entries);

        for (Iterator iterator = entries.iterator(); iterator.hasNext();) {
            SVNDirEntry entry = (SVNDirEntry) iterator.next();
            String childPath = SVNPathUtil.append(path, entry.getName());
            entry.setRelativePath(childPath);
            if (entry.getKind() == SVNNodeKind.FILE || depth == SVNDepth.IMMEDIATES ||
                depth == SVNDepth.INFINITY) {
                handler.handleDirEntry(entry);
            }
            if (entry.getKind() == SVNNodeKind.DIR && entry.getDate() != null && depth == SVNDepth.INFINITY) {
                list(repository, childPath, rev, depth, entryFields, handler);
            }
        }
    }
    
    private static class CopyFromReceiver implements ISVNLogEntryHandler {
        private String myTargetPath;
        private SVNLocationEntry myCopyFromLocation;
        
        public CopyFromReceiver(String targetPath) {
            myTargetPath = targetPath;
        }
        
        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            if (myCopyFromLocation != null) {
                return;
            }
            
            Map changedPaths = logEntry.getChangedPaths();
            if (changedPaths != null && !changedPaths.isEmpty()) {
                TreeMap sortedChangedPaths = new TreeMap(Collections.reverseOrder());
                sortedChangedPaths.putAll(changedPaths);
                for (Iterator changedPathsIter = sortedChangedPaths.keySet().iterator(); changedPathsIter.hasNext();) {
                    String changedPath = (String) changedPathsIter.next();
                    SVNLogEntryPath logEntryPath = (SVNLogEntryPath) sortedChangedPaths.get(changedPath);
                    if (logEntryPath.getCopyPath() != null && 
                        SVNRevision.isValidRevisionNumber(logEntryPath.getCopyRevision()) && 
                        SVNPathUtil.isAncestor(changedPath, myTargetPath)) {
                        String copyFromPath = null;
                        if (changedPath.equals(myTargetPath)) {
                            copyFromPath = logEntryPath.getCopyPath();
                        } else {
                            String relPath = myTargetPath.substring(changedPath.length());
                            if (relPath.startsWith("/")) {
                                relPath = relPath.substring(1);
                            }
                            copyFromPath = SVNPathUtil.concatToAbs(logEntryPath.getCopyPath(), relPath);
                        }
                        myCopyFromLocation = new SVNLocationEntry(logEntryPath.getCopyRevision(), copyFromPath);
                        break;
                    }
                }
            }
        } 

        public SVNLocationEntry getCopyFromLocation() {
            return myCopyFromLocation;
        }
    }

}