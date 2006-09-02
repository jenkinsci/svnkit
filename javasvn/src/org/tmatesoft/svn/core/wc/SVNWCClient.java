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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNLog;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry2;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess2;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * The <b>SVNWCClient</b> class combines a number of version control 
 * operations mainly intended for local work with Working Copy items. This class
 * includes those operations that are destined only for local work on a 
 * Working Copy as well as those that are moreover able to access  a repository. 
 * 
 * <p>
 * Here's a list of the <b>SVNWCClient</b>'s methods 
 * matched against corresponing commands of the SVN command line 
 * client:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="70%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>JavaSVN</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doAdd()</td><td>'svn add'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doDelete()</td><td>'svn delete'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doCleanup()</td><td>'svn cleanup'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doInfo()</td><td>'svn info'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doLock()</td><td>'svn lock'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doUnlock()</td><td>'svn unlock'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>
 * doSetProperty()
 * </td>
 * <td>
 * 'svn propset PROPNAME PROPVAL PATH'<br />
 * 'svn propdel PROPNAME PATH'<br />
 * 'svn propedit PROPNAME PATH'
 * </td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doSetRevisionProperty()</td>
 * <td>
 * 'svn propset PROPNAME --revprop -r REV PROPVAL [URL]'<br />
 * 'svn propdel PROPNAME --revprop -r REV [URL]'<br />
 * 'svn propedit PROPNAME --revprop -r REV [URL]'
 * </td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>
 * doGetProperty()
 * </td>
 * <td>
 * 'svn propget PROPNAME PATH'<br />
 * 'svn proplist PATH'
 * </td>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetRevisionProperty()</td>
 * <td>
 * 'svn propget PROPNAME --revprop -r REV [URL]'<br />
 * 'svn proplist --revprop -r REV [URL]'
 * </td>
 * </tr>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doResolve()</td><td>'svn resolved'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doRevert()</td><td>'svn revert'</td>
 * </tr>
 * </table>
 * 
 * @version 1.1
 * @author  TMate Software Ltd.
 * @see     <a target="_top" href="http://tmate.org/svn/kb/examples/">Examples</a>
 */
public class SVNWCClient extends SVNBasicClient {
    /**
     * Constructs and initializes an <b>SVNWCClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNWCClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNWCClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the 
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     * 
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver     
     */
    public SVNWCClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNWCClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }
    
    /**
     * Gets contents of a file. 
     * If <vode>revision</code> is one of:
     * <ul>
     * <li>{@link SVNRevision#BASE BASE}
     * <li>{@link SVNRevision#WORKING WORKING}
     * <li>{@link SVNRevision#COMMITTED COMMITTED}
     * </ul>
     * then the file contents are taken from the Working Copy file item. 
     * Otherwise the file item's contents are taken from the repository
     * at a particular revision. 
     *  
     * @param  path               a Working Copy file item
     * @param  pegRevision        a revision in which the file item is first looked up
     * @param  revision           a target revision
     * @param  expandKeywords     if <span class="javakeyword">true</span> then
     *                            all keywords presenting in the file and listed in 
     *                            the file's {@link org.tmatesoft.svn.core.SVNProperty#KEYWORDS svn:keywords}
     *                            property (if set) will be substituted, otherwise not 
     * @param  dst                the destination where the file contents will be written to
     * @throws SVNException       if one of the following is true:
     *                            <ul>
     *                            <li><code>path</code> refers to a directory 
     *                            <li><code>path</code> does not exist 
     *                            <li><code>path</code> is not under version control
     *                            </ul>
     * @see                       #doGetFileContents(SVNURL, SVNRevision, SVNRevision, boolean, OutputStream)                           
     */
    public void doGetFileContents(File path, SVNRevision pegRevision,
            SVNRevision revision, boolean expandKeywords, OutputStream dst)
            throws SVNException {
        if (dst == null) {
            return;
        }
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.WORKING;
        }
        if (revision == SVNRevision.COMMITTED) {
            revision = SVNRevision.BASE;
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        if ("".equals(wcAccess.getTargetName()) || wcAccess.getTarget() != wcAccess.getAnchor()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_IS_DIRECTORY, "''{0}'' refers to a directory", path, SVNErrorMessage.TYPE_WARNING);
            SVNErrorManager.error(err);
        }
        checkCancelled();
        String name = wcAccess.getTargetName();
        if (revision == SVNRevision.WORKING || revision == SVNRevision.BASE) {
            File file = wcAccess.getAnchor().getBaseFile(name, false);
            boolean delete = false;
            SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName(), false);
            if (entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control or doesn''t exist", path, SVNErrorMessage.TYPE_WARNING);
                SVNErrorManager.error(err);
            }
            try {
                if (revision == SVNRevision.BASE) {
                    if (expandKeywords) {
                        delete = true;
                        file = wcAccess.getAnchor().getBaseFile(name, true).getParentFile();
                        file = SVNFileUtil.createUniqueFile(file, name, ".tmp");
                        SVNTranslator.translate(wcAccess.getAnchor(), name,
                                SVNFileUtil.getBasePath(wcAccess.getAnchor().getBaseFile(name, false)), 
                                SVNFileUtil.getBasePath(file), true, false);
                    }
                } else {
                    if (!expandKeywords) {
                        delete = true;
                        file = wcAccess.getAnchor().getBaseFile(name, true).getParentFile();
                        file = SVNFileUtil.createUniqueFile(file, name, ".tmp");
                        SVNTranslator.translate(wcAccess.getAnchor(), name,
                                name, SVNFileUtil.getBasePath(file), false,
                                false);
                    } else {
                        file = wcAccess.getAnchor().getFile(name);
                    }
                }
            } finally {
                if (file != null && file.exists()) {
                    InputStream is = SVNFileUtil.openFileForReading(file);
                    try {
                        int r;
                        while ((r = is.read()) >= 0) {
                            dst.write(r);
                        }
                    } catch (IOException e) {
                        getDebugLog().info(e);
                    } finally {
                        SVNFileUtil.closeFile(is);
                        if (delete) {
                            file.delete();
                        }
                    }
                }
            }
        } else {
            SVNRepository repos = createRepository(null, path, pegRevision, revision);
            checkCancelled();
            long revNumber = getRevisionNumber(revision, repos, path);
            checkCancelled();
            if (!expandKeywords) {
                repos.getFile("", revNumber, null, new SVNCancellableOutputStream(dst, this));
            } else {
                String adminDir = SVNFileUtil.getAdminDirectoryName();
                File tmpFile = SVNFileUtil.createUniqueFile(new File(path.getParentFile(), adminDir + "/tmp/text-base"), path.getName(), ".tmp");
                File tmpFile2 = null;
                OutputStream os = null;
                InputStream is = null;
                try {
                    os = SVNFileUtil.openFileForWriting(tmpFile);
                    Map properties = new HashMap();
                    repos.getFile("", revNumber, properties, new SVNCancellableOutputStream(os, this));
                    SVNFileUtil.closeFile(os);
                    os = null;
                    String keywords = (String) properties.get(SVNProperty.KEYWORDS);
                    String eol = (String) properties.get(SVNProperty.EOL_STYLE);
                    byte[] eolBytes = SVNTranslator.getWorkingEOL(eol);
                    Map keywordsMap = SVNTranslator.computeKeywords(keywords, repos.getLocation().toString(),
                            (String) properties.get(SVNProperty.LAST_AUTHOR),
                            (String) properties.get(SVNProperty.COMMITTED_DATE),
                            (String) properties.get(SVNProperty.COMMITTED_REVISION));
                    tmpFile2 = SVNFileUtil.createUniqueFile(new File(path.getParentFile(), adminDir + "/tmp/text-base"), path.getName(), ".tmp");
                    SVNTranslator.translate(tmpFile, tmpFile2, eolBytes, keywordsMap, false, true);
                    // cat tmp file
                    is = SVNFileUtil.openFileForReading(tmpFile2);
                    int r;
                    while ((r = is.read()) >= 0) {
                        dst.write(r);
                    }
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                    SVNErrorManager.error(err, e);
                } finally {
                    SVNFileUtil.closeFile(os);
                    SVNFileUtil.closeFile(is);
                    if (tmpFile != null) {
                        tmpFile.delete();
                    }
                    if (tmpFile2 != null) {
                        tmpFile2.delete();
                    }
                }
            }
        }
    }
    
    /**
     * Gets contents of a file of a particular revision from a repository. 
     * 
     * @param  url                a file item's repository location 
     * @param  pegRevision        a revision in which the file item is first looked up
     * @param  revision           a target revision
     * @param  expandKeywords     if <span class="javakeyword">true</span> then
     *                            all keywords presenting in the file and listed in 
     *                            the file's {@link org.tmatesoft.svn.core.SVNProperty#KEYWORDS svn:keywords}
     *                            property (if set) will be substituted, otherwise not 
     * @param  dst                the destination where the file contents will be written to
     * @throws SVNException       if one of the following is true:
     *                            <ul>
     *                            <li><code>url</code> refers to a directory 
     *                            <li>it's impossible to create temporary files
     *                            ({@link java.io.File#createTempFile(java.lang.String, java.lang.String) createTempFile()}
     *                            fails) necessary for file translating
     *                            </ul> 
     * @see                       #doGetFileContents(File, SVNRevision, SVNRevision, boolean, OutputStream)                           
     */
    public void doGetFileContents(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean expandKeywords, OutputStream dst) throws SVNException {
        revision = revision == null || !revision.isValid() ? SVNRevision.HEAD : revision;
        // now get contents from URL.
        Map properties = new HashMap();
        SVNRepository repos = createRepository(url, null, pegRevision, revision);
        checkCancelled();
        long revisionNumber = getRevisionNumber(revision, repos, null);
        checkCancelled();
        SVNNodeKind nodeKind = repos.checkPath("", revisionNumber);
        checkCancelled();
        if (nodeKind == SVNNodeKind.DIR) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_IS_DIRECTORY, "URL ''{0}'' refers to a directory", url, SVNErrorMessage.TYPE_WARNING);
            SVNErrorManager.error(err);
        }
        OutputStream os = null;
        InputStream is = null;
        File file = null;
        File file2 = null;
        try {
            file = File.createTempFile("svn-contents", ".tmp");
            file2 = File.createTempFile("svn-contents", ".tmp");
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            if (file != null) {
                file.delete();
            }
            if (file2 != null) {
                file2.delete();
            }
            SVNErrorManager.error(err, e);
            return;
        }
        try {
            
            os = new FileOutputStream(file);
            repos.getFile("", revisionNumber, properties, new SVNCancellableOutputStream(os, this));
            os.close();
            os = null;
            if (expandKeywords) {
                // use props at committed (peg) revision, not those.
                String keywords = (String) properties.get(SVNProperty.KEYWORDS);
                String eol = (String) properties.get(SVNProperty.EOL_STYLE);
                byte[] eolBytes = SVNTranslator.getWorkingEOL(eol);
                Map keywordsMap = SVNTranslator.computeKeywords(keywords, url.toString(),
                        (String) properties.get(SVNProperty.LAST_AUTHOR),
                        (String) properties.get(SVNProperty.COMMITTED_DATE),
                        (String) properties.get(SVNProperty.COMMITTED_REVISION));
                SVNTranslator.translate(file, file2, eolBytes, keywordsMap,
                        false, true);
            } else {
                file2 = file;
            }

            is = SVNFileUtil.openFileForReading(file2);
            int r;
            while ((r = is.read()) >= 0) {
                dst.write(r);
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(os);
            SVNFileUtil.closeFile(is);
            if (file != null) {
                file.delete();
            }
            if (file2 != null) {
                file2.delete();
            }
        }
    }
    
    /**
     * Recursively cleans up the working copy, removing locks and resuming 
     * unfinished operations. 
     * 
     * <p>
     * If you ever get a "working copy locked" error, use this method 
     * to remove stale locks and get your working copy into a usable 
     * state again.
     * 
     * @param  path             a WC path to start a cleanup from 
     * @throws SVNException     if one of the following is true:
     *                          <ul>
     *                          <li><code>path</code> does not exist
     *                          <li><code>path</code>'s parent directory
     *                          is not under version control
     *                          </ul>
     */
    public void doCleanup(File path) throws SVNException {
        SVNFileType fType = SVNFileType.getType(path);
        if (fType == SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "''{0}'' does not exist", path);
            SVNErrorManager.error(err);
        } else if (fType == SVNFileType.FILE || fType == SVNFileType.SYMLINK) {
            path = path.getParentFile();
        }
        if (!SVNWCAccess.isVersionedDirectory(path)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", path);
            SVNErrorManager.error(err);
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        wcAccess.open(true, true, true);
        wcAccess.getAnchor().cleanup();
        wcAccess.close(true);
    }
    
    /**
     * Sets, edits or deletes a property on a file or directory item(s).
     * 
     * <p> 
     * To set or edit a property simply provide a <code>propName</code> 
     * and a <code>propValue</code>. To delete a property set 
     * <code>propValue</code> to <span class="javakeyword">null</span> 
     * and the property <code>propName</code> will be deleted.
     * 
     * @param  path             a WC item which properties are to be 
     *                          modified
     * @param  propName         a property name
     * @param  propValue        a property value
     * @param  force            <span class="javakeyword">true</span> to
     *                          force the operation to run
     * @param  recursive        <span class="javakeyword">true</span> to
     *                          descend recursively
     * @param  handler          a caller's property handler
     * @throws SVNException     if one of the following is true:
     *                          <ul>
     *                          <li><code>propName</code> is a revision 
     *                          property 
     *                          <li><code>propName</code> starts
     *                          with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                          svn:wc:} prefix
     *                          </ul>
     * @see                     #doSetRevisionProperty(File, SVNRevision, String, String, boolean, ISVNPropertyHandler)
     * @see                     #doGetProperty(File, String, SVNRevision, SVNRevision, boolean)
     * @see                     #doGetRevisionProperty(File, String, SVNRevision, ISVNPropertyHandler)
     */
    public void doSetProperty(File path, String propName, String propValue, boolean force, boolean recursive, ISVNPropertyHandler handler) throws SVNException {
        propName = validatePropertyName(propName);
        if (SVNRevisionProperty.isRevisionProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, "Revision property ''{0}'' not allowed in this context", propName);
            SVNErrorManager.error(err);
        } else if (SVNProperty.isWorkingCopyProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, "''{0}'' is a wcprop, thus not accessible to clients", propName);
            SVNErrorManager.error(err);
        } else if (SVNProperty.isEntryProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, "''{0}'' is an entry property, thus not accessible to clients", propName);
            SVNErrorManager.error(err);
        }
        propValue = validatePropertyValue(propName, propValue, force);
        SVNWCAccess wcAccess = createWCAccess(path);
        try {
            wcAccess.open(true, recursive);
            doSetLocalProperty(wcAccess.getAnchor(), wcAccess.getTargetName(), propName, propValue, force, recursive, true, handler);
        } finally {
            wcAccess.close(true);
        }

/*        
        SVNWCAccess2 wcAccess = SVNWCAccess2.newInstance(getEventDispatcher());
        try {
            SVNAdminArea area = wcAccess.open(path, true, recursive ? SVNWCAccess2.INFINITE_DEPTH : 1);
            doSetLocalProperty(area, "", propName, propValue, force, recursive, true, handler);
        } finally {
            wcAccess.close();
        }
*/        
    }
    
    /**
     * Sets, edits or deletes an unversioned revision property.
     * This method uses a Working Copy item to obtain the URL of 
     * the repository which revision properties are to be changed.
     * 
     * <p> 
     * To set or edit a property simply provide a <code>propName</code> 
     * and a <code>propValue</code>. To delete a revision property set 
     * <code>propValue</code> to <span class="javakeyword">null</span> 
     * and the property <code>propName</code> will be deleted.
     * 
     * @param  path            a Working Copy item           
     * @param  revision        a revision which properties are to be
     *                         modified
     * @param  propName        a property name
     * @param  propValue       a property value
     * @param  force           <span class="javakeyword">true</span> to
     *                         force the operation to run
     * @param  handler         a caller's property handler
     * @throws SVNException    if one of the following is true:
     *                         <ul>
     *                         <li>the operation can not be performed 
     *                         without forcing 
     *                         <li><code>propName</code> starts
     *                         with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                         svn:wc:} prefix
     *                         </ul>
     * @see                    #doSetRevisionProperty(SVNURL, SVNRevision, String, String, boolean, ISVNPropertyHandler)
     * @see                    #doSetProperty(File, String, String, boolean, boolean, ISVNPropertyHandler)
     * @see                    #doGetProperty(File, String, SVNRevision, SVNRevision, boolean)
     * @see                    #doGetRevisionProperty(File, String, SVNRevision, ISVNPropertyHandler)                         
     */
    public void doSetRevisionProperty(File path, SVNRevision revision, String propName, String propValue, boolean force, ISVNPropertyHandler handler) throws SVNException {
        propName = validatePropertyName(propName);
        propValue = validatePropertyValue(propName, propValue, force);
        SVNURL url = getURL(path);
        doSetRevisionProperty(url, revision, propName, propValue, force, handler);
    }
    
    /**
     * Sets, edits or deletes an unversioned revision property.
     * This method uses a URL pointing to a repository which revision 
     * properties are to be changed.
     * 
     * <p> 
     * To set or edit a property simply provide a <code>propName</code> 
     * and a <code>propValue</code>. To delete a revision property set 
     * <code>propValue</code> to <span class="javakeyword">null</span> 
     * and the property <code>propName</code> will be deleted.
     * 
     * @param  url             a URL pointing to a repository location
     * @param  revision        a revision which properties are to be
     *                         modified
     * @param  propName        a property name
     * @param  propValue       a property value
     * @param  force           <span class="javakeyword">true</span> to
     *                         force the operation to run
     * @param  handler         a caller's property handler
     * @throws SVNException    if one of the following is true:
     *                         <ul>
     *                         <li>the operation can not be performed 
     *                         without forcing 
     *                         <li><code>propName</code> starts
     *                         with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                         svn:wc:} prefix
     *                         </ul>
     * @see                    #doSetRevisionProperty(File, SVNRevision, String, String, boolean, ISVNPropertyHandler)
     * @see                    #doSetProperty(File, String, String, boolean, boolean, ISVNPropertyHandler)
     * @see                    #doGetProperty(File, String, SVNRevision, SVNRevision, boolean)
     * @see                    #doGetRevisionProperty(File, String, SVNRevision, ISVNPropertyHandler)                         
     */
    public void doSetRevisionProperty(SVNURL url, SVNRevision revision, String propName, String propValue, boolean force, ISVNPropertyHandler handler) throws SVNException {
        propName = validatePropertyName(propName);
        propValue = validatePropertyValue(propName, propValue, force);
        if (!force && SVNRevisionProperty.AUTHOR.equals(propName) && propValue != null && propValue.indexOf('\n') >= 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_REVISION_AUTHOR_CONTAINS_NEWLINE, "Value will not be set unless forced");
            SVNErrorManager.error(err);
        }
        if (propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, "''{0}'' is a wcprop , thus not accessible to clients", propName);
            SVNErrorManager.error(err);
        }
        SVNRepository repos = createRepository(url, null, SVNRevision.UNDEFINED, revision);
        long revNumber = getRevisionNumber(revision, repos, null);
        repos.setRevisionPropertyValue(revNumber, propName, propValue);
        if (handler != null) {
            handler.handleProperty(revNumber, new SVNPropertyData(propName, propValue));
        }
    }
    
    /**
     * Gets an item's versioned property. It's possible to get either a local 
     * property (from a Working Copy) or a remote one (located in a repository). 
     * If <vode>revision</code> is one of:
     * <ul>
     * <li>{@link SVNRevision#BASE BASE}
     * <li>{@link SVNRevision#WORKING WORKING}
     * <li>{@link SVNRevision#COMMITTED COMMITTED}
     * </ul>
     * then the result is a WC item's property. Otherwise the 
     * property is taken from a repository (using the item's URL). 
     * 
     * @param  path           a WC item's path
     * @param  propName       an item's property name; if it's 
     *                        <span class="javakeyword">null</span> then
     *                        all the item's properties will be retrieved
     *                        but only the first of them returned 
     *                        
     * @param  pegRevision    a revision in which the item is first looked up
     * @param  revision       a target revision; 
     *                         
     * @param  recursive      <span class="javakeyword">true</span> to
     *                        descend recursively
     * @return                the item's property
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li><code>propName</code> starts
     *                        with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                        svn:wc:} prefix 
     *                        <li><code>path</code> is not under version control
     *                        </ul>
     * @see                   #doGetProperty(File, String, SVNRevision, SVNRevision, boolean, ISVNPropertyHandler)
     * @see                   #doSetProperty(File, String, String, boolean, boolean, ISVNPropertyHandler)
     */
    public SVNPropertyData doGetProperty(final File path, String propName,
            SVNRevision pegRevision, SVNRevision revision, boolean recursive)
            throws SVNException {
        final SVNPropertyData[] data = new SVNPropertyData[1];
        doGetProperty(path, propName, pegRevision, revision, recursive, new ISVNPropertyHandler() {
            public void handleProperty(File file, SVNPropertyData property) {
                if (data[0] == null && path.equals(file)) {
                    data[0] = property;
                }
            }
            public void handleProperty(SVNURL url, SVNPropertyData property) {
            }
            public void handleProperty(long revision, SVNPropertyData property) {
            }
        });
        return data[0];
    }
    
    /**
     * Gets an item's versioned property from a repository.  
     * This method is useful when having no Working Copy at all.
     * 
     * @param  url             an item's repository location
     * @param  propName        an item's property name; if it's 
     *                         <span class="javakeyword">null</span> then
     *                         all the item's properties will be retrieved
     *                         but only the first of them returned
     * @param  pegRevision     a revision in which the item is first looked up
     * @param  revision        a target revision
     * @param  recursive       <span class="javakeyword">true</span> to
     *                         descend recursively
     * @return                 the item's property
     * @throws SVNException    if <code>propName</code> starts
     *                         with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                         svn:wc:} prefix
     * @see                    #doGetProperty(SVNURL, String, SVNRevision, SVNRevision, boolean, ISVNPropertyHandler)
     * @see                    #doSetProperty(File, String, String, boolean, boolean, ISVNPropertyHandler)
     */
    public SVNPropertyData doGetProperty(final SVNURL url, String propName,
            SVNRevision pegRevision, SVNRevision revision, boolean recursive)
            throws SVNException {
        final SVNPropertyData[] data = new SVNPropertyData[1];
        doGetProperty(url, propName, pegRevision, revision, recursive, new ISVNPropertyHandler() {
            public void handleProperty(File file, SVNPropertyData property) {
            }
            public void handleProperty(long revision, SVNPropertyData property) {
            }
            public void handleProperty(SVNURL location, SVNPropertyData property) throws SVNException {
                if (data[0] == null && url.toString().equals(location.toString())) {
                    data[0] = property;
                }
            }
        });
        return data[0];
    }
    
    /**
     * Gets an item's versioned property and passes it to a provided property
     * handler. It's possible to get either a local property (from a Working 
     * Copy) or a remote one (located in a repository). 
     * If <vode>revision</code> is one of:
     * <ul>
     * <li>{@link SVNRevision#BASE BASE}
     * <li>{@link SVNRevision#WORKING WORKING}
     * <li>{@link SVNRevision#COMMITTED COMMITTED}
     * </ul>
     * then the result is a WC item's property. Otherwise the 
     * property is taken from a repository (using the item's URL). 
     * 
     * @param  path           a WC item's path
     * @param  propName       an item's property name; if it's 
     *                        <span class="javakeyword">null</span> then
     *                        all the item's properties will be retrieved
     *                        and passed to <code>handler</code> for
     *                        processing 
     * @param  pegRevision    a revision in which the item is first looked up
     * @param  revision       a target revision; 
     *                         
     * @param  recursive      <span class="javakeyword">true</span> to
     *                        descend recursively
     * @param  handler        a caller's property handler
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li><code>propName</code> starts
     *                        with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                        svn:wc:} prefix 
     *                        <li><code>path</code> is not under version control
     *                        </ul>
     * @see                   #doGetProperty(File, String, SVNRevision, SVNRevision, boolean)
     * @see                   #doSetProperty(File, String, String, boolean, boolean, ISVNPropertyHandler)
     */
    public void doGetProperty(File path, String propName, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNPropertyHandler handler) throws SVNException {
        if (propName != null && propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, "''{0}'' is a wcprop , thus not accessible to clients", propName);
            SVNErrorManager.error(err);
        }
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.WORKING;
        }
        SVNWCAccess wcAccess = createWCAccess(path);

        wcAccess.open(false, recursive);
        SVNEntry entry = wcAccess.getTargetEntry();
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", path);
            SVNErrorManager.error(err);
        }
        if (revision != SVNRevision.WORKING && revision != SVNRevision.BASE && revision != SVNRevision.COMMITTED) {
            SVNURL url = entry.getSVNURL();
            SVNRepository repository = createRepository(null, path, pegRevision, revision);
            long revisionNumber = getRevisionNumber(revision, repository, path);
            revision = SVNRevision.create(revisionNumber);
            doGetRemoteProperty(url, "", repository, propName, revision, recursive, handler);
        } else {
            doGetLocalProperty(wcAccess.getAnchor(), wcAccess.getTargetName(), propName, revision, recursive, handler);
        }
        wcAccess.close(false);
    }
    
    /**
     * Gets an item's versioned property from a repository and passes it to 
     * a provided property handler. This method is useful when having no 
     * Working Copy at all.
     * 
     * @param  url             an item's repository location
     * @param  propName        an item's property name; if it's 
     *                         <span class="javakeyword">null</span> then
     *                         all the item's properties will be retrieved
     *                         and passed to <code>handler</code> for
     *                         processing
     * @param  pegRevision     a revision in which the item is first looked up
     * @param  revision        a target revision
     * @param  recursive       <span class="javakeyword">true</span> to
     *                         descend recursively
     * @param  handler         a caller's property handler
     * @throws SVNException    if <code>propName</code> starts
     *                         with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                         svn:wc:} prefix
     * @see                    #doGetProperty(SVNURL, String, SVNRevision, SVNRevision, boolean)
     * @see                    #doSetProperty(File, String, String, boolean, boolean, ISVNPropertyHandler)
     */
    public void doGetProperty(SVNURL url, String propName, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNPropertyHandler handler) throws SVNException {
        if (propName != null && propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, "''{0}'' is a wcprop , thus not accessible to clients", propName);
            SVNErrorManager.error(err);
        }
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.HEAD;
        }
        SVNRepository repos = createRepository(url, null, pegRevision, revision);
        doGetRemoteProperty(url, "", repos, propName, revision, recursive, handler);
    }
    
    /**
     * Gets an unversioned revision property from a repository (getting
     * a repository URL from a Working Copy) and passes it to a provided 
     * property handler. 
     *  
     * @param  path             a local Working Copy item which repository
     *                          location is used to connect to a repository 
     * @param  propName         a revision property name; if this parameter 
     *                          is <span class="javakeyword">null</span> then
     *                          all the revision properties will be retrieved
     *                          and passed to <code>handler</code> for
     *                          processing
     * @param  revision         a revision which property is to be retrieved  
     * @param  handler          a caller's property handler
     * @throws SVNException     if one of the following is true:
     *                          <ul>
     *                          <li><code>revision</code> is invalid 
     *                          <li><code>propName</code> starts with the 
     *                          {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                          svn:wc:} prefix
     *                          </ul>
     * @see                     #doGetRevisionProperty(SVNURL, String, SVNRevision, ISVNPropertyHandler)
     * @see                     #doSetRevisionProperty(File, SVNRevision, String, String, boolean, ISVNPropertyHandler)
     */
    public void doGetRevisionProperty(File path, String propName, SVNRevision revision, ISVNPropertyHandler handler) throws SVNException {
        if (propName != null && propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, "''{0}'' is a wcprop , thus not accessible to clients", propName);
            SVNErrorManager.error(err);
        }
        if (!revision.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Valid revision have to be specified to fetch revision property");
            SVNErrorManager.error(err);
        }
        SVNRepository repository = createRepository(null, path, SVNRevision.UNDEFINED, revision);
        long revisionNumber = getRevisionNumber(revision, repository, path);
        doGetRevisionProperty(repository, propName, revisionNumber, handler);
    }
    
    /**
     * Gets an unversioned revision property from a repository and passes 
     * it to a provided property handler. 
     * 
     * @param  url              a URL pointing to a repository location
     *                          which revision property is to be got
     * @param  propName         a revision property name; if this parameter 
     *                          is <span class="javakeyword">null</span> then
     *                          all the revision properties will be retrieved
     *                          and passed to <code>handler</code> for
     *                          processing
     * @param  revision         a revision which property is to be retrieved  
     * @param  handler          a caller's property handler
     * @throws SVNException     if one of the following is true:
     *                          <ul>
     *                          <li><code>revision</code> is invalid 
     *                          <li><code>propName</code> starts with the 
     *                          {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                          svn:wc:} prefix
     *                          </ul>
     * @see                     #doGetRevisionProperty(File, String, SVNRevision, ISVNPropertyHandler)
     * @see                     #doSetRevisionProperty(SVNURL, SVNRevision, String, String, boolean, ISVNPropertyHandler)
     */
    public void doGetRevisionProperty(SVNURL url, String propName, SVNRevision revision, ISVNPropertyHandler handler) throws SVNException {
        if (propName != null && propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, "''{0}'' is a wcprop , thus not accessible to clients", propName);
            SVNErrorManager.error(err);
        }
        if (!revision.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Valid revision have to be specified to fetch revision property");
            SVNErrorManager.error(err);
        }
        SVNRepository repos = createRepository(url, true);
        long revNumber = getRevisionNumber(revision, repos, null);
        doGetRevisionProperty(repos, propName, revNumber, handler);
    }

    private void doGetRevisionProperty(SVNRepository repos, String propName, long revNumber, ISVNPropertyHandler handler) throws SVNException {
        if (propName != null) {
            String value = repos.getRevisionPropertyValue(revNumber, propName);
            if (value != null) {
                handler.handleProperty(revNumber, new SVNPropertyData(propName, value));
            }
        } else {
            Map props = new HashMap();
            repos.getRevisionProperties(revNumber, props);
            for (Iterator names = props.keySet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                String value = (String) props.get(name);
                handler.handleProperty(revNumber, new SVNPropertyData(name, value));
            }
        }
    }
    
    /**
     * Schedules a Working Copy item for deletion.
     * 
     * @param  path           a WC item to be deleted 
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         <span class="javakeyword">true</span> only to
     *                        try the delete operation without actual deleting
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li><code>path</code> is not under version control
     *                        <li>can not delete <code>path</code> without forcing
     *                        </ul>
     * @see                   #doDelete(File, boolean, boolean, boolean)
     */
    public void doDelete(File path, boolean force, boolean dryRun) throws SVNException {
        doDelete(path, force, true, dryRun);
    }
    
    /**
     * Schedules a Working Copy item for deletion. This method allows to
     * choose - whether file item(s) are to be deleted from the filesystem or 
     * not. Another version of the {@link #doDelete(File, boolean, boolean) doDelete()}
     * method is similar to the corresponding SVN client's command - <code>'svn delete'</code> 
     * as it always deletes files from the filesystem.
     * 
     * 
     * @param  path           a WC item to be deleted 
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  deleteFiles    if <span class="javakeyword">true</span> then
     *                        files will be scheduled for deletion as well as
     *                        deleted from the filesystem, otherwise files will
     *                        be only scheduled for addition and still be present
     *                        in the filesystem
     * @param  dryRun         <span class="javakeyword">true</span> only to
     *                        try the delete operation without actual deleting
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li><code>path</code> is not under version control
     *                        <li>can not delete <code>path</code> without forcing
     *                        </ul>
     */
    public void doDelete(File path, boolean force, boolean deleteFiles, boolean dryRun) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess(path);
        try {
            wcAccess.open(true, true, true);
            if (!force) {
                wcAccess.getAnchor().canScheduleForDeletion(wcAccess.getTargetName(), false);
            }
            if (!dryRun) {
                wcAccess.getAnchor().scheduleForDeletion(wcAccess.getTargetName(), deleteFiles);
            }
        } finally {
            wcAccess.close(true);
        }
    }
    
    /**
     * Schedules an unversioned item for addition to a repository thus 
     * putting it under version control.
     * 
     * <p>
     * To create and add to version control a new directory, set <code>mkdir</code>
     * to <span class="javakeyword">true</span>. 
     * 
     * @param  path                        a path to be put under version 
     *                                     control (will be added to a repository
     *                                     in next commit)
     * @param  force                       <span class="javakeyword">true</span> to
     *                                     force the operation to run
     * @param  mkdir                       if <span class="javakeyword">true</span> - 
     *                                     creates a new directory and schedules it for
     *                                     addition
     * @param  climbUnversionedParents     if <span class="javakeyword">true</span> and
     *                                     <code>path</code> is located in an unversioned
     *                                     parent directory then the parent will be automatically
     *                                     scheduled for addition, too 
     * @param  recursive                   <span class="javakeyword">true</span> to
     *                                     descend recursively (relevant for directories)
     * @throws SVNException                if one of the following is true:
     *                                     <ul>
     *                                     <li><code>path</code> doesn't belong
     *                                     to a Working Copy 
     *                                     <li><code>path</code> doesn't exist and
     *                                     <code>mkdir</code> is <span class="javakeyword">false</span>
     *                                     <li><code>path</code> is the root directory of the Working Copy
     */
    public void doAdd(File path, boolean force, boolean mkdir, boolean climbUnversionedParents, boolean recursive) throws SVNException {
        SVNFileType fType = SVNFileType.getType(path); 
        if (fType == SVNFileType.NONE && !mkdir) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "''{0}'' does not exist", path);
            SVNErrorManager.error(err);
        }
        if (climbUnversionedParents) {
            File parent = path.getParentFile();
            if (parent != null && SVNWCUtil.getWorkingCopyRoot(path, true) == null) {
                // path is in unversioned dir, try to add this parent before path.
                doAdd(parent, false, mkdir, climbUnversionedParents, false);
            }
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        try {
            wcAccess.open(true, recursive);
            checkCancelled();
            String name = wcAccess.getTargetName();

            if ("".equals(name) && !force) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "''{0}'' is already under version control", path);
                SVNErrorManager.error(err);
            }
            SVNDirectory dir = wcAccess.getAnchor();
            if (fType == SVNFileType.FILE || fType == SVNFileType.SYMLINK) {
                addSingleFile(dir, name);
            } else if (fType == SVNFileType.DIRECTORY && recursive) {
                // add dir and recurse.
                addDirectory(wcAccess, wcAccess.getAnchor(), name, force);
            } else {
                // add single dir, no force - report error anyway.
                dir.add(wcAccess.getTargetName(), mkdir, false);
            }
        } finally {
            wcAccess.close(true);
        }
    }
    
    /**
     * Reverts all local changes made to a Working Copy item(s) thus
     * bringing it to a 'pristine' state.
     * 
     * @param  path            a WC path to perform a revert on
     * @param  recursive       <span class="javakeyword">true</span> to
     *                         descend recursively (relevant for directories)
     * @throws SVNException    if one of the following is true:
     *                         <ul>
     *                         <li><code>path</code> is not under version control
     *                         <li>when trying to revert an addition of a directory
     *                         from within the directory itself
     *                         </ul>
     */
    public void doRevert(File path, boolean recursive) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess(path);

        // force recursive lock.
        boolean reverted = false;
        boolean replaced = false;
        SVNNodeKind kind = null;
        Collection recursiveFiles = new ArrayList();
        try {
            wcAccess.open(true, false);
            SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName(), true);
            if (entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", path);
                SVNErrorManager.error(err);
            }
            kind = entry.getKind();
            File file = wcAccess.getAnchor().getFile(wcAccess.getTargetName());
            if (entry.isDirectory()) {
                if (!entry.isScheduledForAddition() && !file.isDirectory()) {
                    // missing and not scheduled for addition.
                    handleEvent(SVNEventFactory.createNotRevertedEvent(
                            wcAccess, wcAccess.getAnchor(), entry),
                            ISVNEventHandler.UNKNOWN);
                    return;
                }
            }

            SVNEvent event = SVNEventFactory.createRevertedEvent(wcAccess, wcAccess.getAnchor(), entry);
            if (entry.isScheduledForAddition()) {
                boolean deleted = entry.isDeleted();
                if (entry.isFile()) {
                    wcAccess.getAnchor().destroy(entry.getName(), false);
                } else if (entry.isDirectory()) {
                    if ("".equals(wcAccess.getTargetName())) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OP_ON_CWD, "Cannot revert addition of the root directory; please try again from the parent directory");
                        SVNErrorManager.error(err);
                    }
                    if (!file.exists()) {
                        wcAccess.getAnchor().getEntries().deleteEntry(entry.getName());
                    } else {
                        wcAccess.open(true, true, true);
                        wcAccess.getAnchor().destroy(entry.getName(), false);
                    }
                }
                reverted = true;
                if (deleted && !"".equals(wcAccess.getTargetName())) {
                    // we are not in the root.
                    SVNEntry replacement = wcAccess.getAnchor().getEntries().addEntry(entry.getName());
                    replacement.setDeleted(true);
                    replacement.setKind(kind);
                }
            } else if (entry.isScheduledForReplacement() || entry.isScheduledForDeletion()) {
                replaced = entry.isScheduledForReplacement();
                if (entry.isDirectory() && wcAccess.getAnchor() != wcAccess.getTarget()) {
                    reverted |= wcAccess.getTarget().revert("");
                } else {
                    reverted |= wcAccess.getAnchor().revert(entry.getName());
                }
                reverted = true;
            } else {
                if (entry.isDirectory() && wcAccess.getAnchor() != wcAccess.getTarget()) {
                    reverted |= wcAccess.getTarget().revert("");
                } else {
                    reverted |= wcAccess.getAnchor().revert(entry.getName());
                }
            }
            if (reverted) {
                if (kind == SVNNodeKind.DIR && replaced) {
                    recursive = true;
                }
                if (!"".equals(wcAccess.getTargetName())) {
                    entry.unschedule();
                    entry.setConflictNew(null);
                    entry.setConflictOld(null);
                    entry.setConflictWorking(null);
                    entry.setPropRejectFile(null);
                }
                wcAccess.getAnchor().getEntries().save(false);
                if (kind == SVNNodeKind.DIR && wcAccess.getTarget() != wcAccess.getAnchor()) {
                    SVNEntry inner = wcAccess.getTarget().getEntries().getEntry("", true);
                    if (inner != null) {
                        // may be null if it was removed from wc.
                        inner.unschedule();
                        inner.setConflictNew(null);
                        inner.setConflictOld(null);
                        inner.setConflictWorking(null);
                        inner.setPropRejectFile(null);
                    }
                }
                wcAccess.getTarget().getEntries().save(false);
            }
            if (!"".equals(wcAccess.getTargetName()) && 
                    wcAccess.getTarget() == wcAccess.getAnchor()) {
                // missing or obstructed dir.
                recursive = false;
            }
            if (kind == SVNNodeKind.DIR && recursive) {
                // iterate over targets and revert
                checkCancelled();
                for (Iterator ents = wcAccess.getTarget().getEntries().entries(true); ents.hasNext();) {
                    SVNEntry childEntry = (SVNEntry) ents.next();
                    if ("".equals(childEntry.getName())) {
                        continue;
                    }
                    recursiveFiles.add(wcAccess.getTarget().getFile(childEntry.getName()));
                }
            }
            if (reverted) {
                // fire reverted event.
                handleEvent(event, ISVNEventHandler.UNKNOWN);
            }
        } finally {
            wcAccess.close(true);
        }
        // recurse
        if (kind == SVNNodeKind.DIR && recursive) {
            // iterate over targets and revert
            for (Iterator files = recursiveFiles.iterator(); files.hasNext();) {
                File file = (File) files.next();
                doRevert(file, recursive);
            }
        }
    }
    
    /**
     * Resolves a 'conflicted' state on a Working Copy item. 
     * 
     * @param  path            a WC item to be resolved
     * @param  recursive       <span class="javakeyword">true</span> to
     *                         descend recursively (relevant for directories) - this
     *                         will resolve the entire tree
     * @throws SVNException    if <code>path</code> is not under version control
     */
    public void doResolve(File path, boolean recursive) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess(path);
        try {
            wcAccess.open(true, recursive);
            String target = wcAccess.getTargetName();
            SVNDirectory dir = wcAccess.getAnchor();

            if (wcAccess.getTarget() != wcAccess.getAnchor()) {
                target = "";
                dir = wcAccess.getTarget();
            }
            SVNEntry entry = dir.getEntries().getEntry(target, false);
            if (entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", path);
                SVNErrorManager.error(err);
                return;
            }

            if (!recursive || entry.getKind() != SVNNodeKind.DIR) {
                if (dir.markResolved(target, true, true)) {
                    SVNEvent event = SVNEventFactory.createResolvedEvent(
                            wcAccess, dir, entry);
                    handleEvent(event, ISVNEventHandler.UNKNOWN);
                }
            } else {
                doResolveAll(wcAccess, dir);
            }
        } finally {
            wcAccess.close(true);
        }
    }

    private void doResolveAll(SVNWCAccess access, SVNDirectory dir) throws SVNException {
        checkCancelled();
        SVNEntries entries = dir.getEntries();
        Collection childDirs = new ArrayList();
        for (Iterator ents = entries.entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName()) || entry.isFile()) {
                if (dir.markResolved(entry.getName(), true, true)) {
                    SVNEvent event = SVNEventFactory.createResolvedEvent(
                            access, dir, entry);
                    handleEvent(event, ISVNEventHandler.UNKNOWN);
                }
            } else if (entry.isDirectory()) {
                SVNDirectory childDir = dir.getChildDirectory(entry.getName());
                if (childDir != null) {
                    childDirs.add(childDir);
                }
            }
        }
        entries.save(true);
        for (Iterator dirs = childDirs.iterator(); dirs.hasNext();) {
            SVNDirectory child = (SVNDirectory) dirs.next();
            doResolveAll(access, child);
        }
    }
    
    /**
     * Locks file items in a Working Copy as well as in a repository so that 
     * no other user can commit changes to them.
     * 
     * @param  paths         an array of local WC file paths that should be locked 
     * @param  stealLock     if <span class="javakeyword">true</span> then all existing
     *                       locks on the specified <code>paths</code> will be "stolen"
     * @param  lockMessage   an optional lock comment  
     * @throws SVNException  if one of the following is true:
     *                       <ul>
     *                       <li>a path to be locked is not under version control
     *                       <li>can not obtain a URL of a local path to lock it in 
     *                       the repository - there's no such entry
     *                       <li><code>paths</code> to be locked belong to different repositories
     *                       </ul>
     * @see                  #doLock(SVNURL[], boolean, String)
     */
    public void doLock(File[] paths, boolean stealLock, String lockMessage) throws SVNException {
        final Map entriesMap = new HashMap();
        for (int i = 0; i < paths.length; i++) {
            SVNWCAccess wcAccess = createWCAccess(paths[i]);
            try {
                wcAccess.open(false, false);
                SVNEntry entry = wcAccess.getTargetEntry();
                if (entry == null || entry.isHidden()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", wcAccess.getTargetName());
                    SVNErrorManager.error(err);
                }
                if (entry.getURL() == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", wcAccess.getTargetName());
                    SVNErrorManager.error(err);
                }
                SVNRevision revision = stealLock ? SVNRevision.UNDEFINED : SVNRevision.create(entry.getRevision());
                entriesMap.put(entry.getSVNURL(), new LockInfo(paths[i], revision));
            } finally {
                wcAccess.close(false);
            }
        }
        checkCancelled();
        SVNURL[] urls = (SVNURL[]) entriesMap.keySet().toArray(new SVNURL[entriesMap.size()]);
        Collection urlPaths = new HashSet();
        final SVNURL topURL = SVNURLUtil.condenceURLs(urls, urlPaths, false);
        if (urlPaths.isEmpty()) {
            urlPaths.add("");
        }
        if (topURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "Paths belongs to different repositories");
            SVNErrorManager.error(err);
        }
        // convert encoded paths to path->revision map.
        Map pathsRevisionsMap = new HashMap();
        for(Iterator encodedPaths = urlPaths. iterator(); encodedPaths.hasNext();) {
            String encodedPath = (String) encodedPaths.next();
            // get LockInfo for it.
            SVNURL fullURL = topURL.appendPath(encodedPath, true);
            LockInfo lockInfo = (LockInfo) entriesMap.get(fullURL);
            encodedPath = SVNEncodingUtil.uriDecode(encodedPath);
            if (lockInfo.myRevision == SVNRevision.UNDEFINED) {
                pathsRevisionsMap.put(encodedPath, null);
            } else {
                pathsRevisionsMap.put(encodedPath, new Long(lockInfo.myRevision.getNumber()));
            }
        }
        SVNRepository repository = createRepository(topURL, true);
        final SVNURL rootURL = repository.getRepositoryRoot(true);
        repository.lock(pathsRevisionsMap, lockMessage, stealLock, new ISVNLockHandler() {
            public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                SVNURL fullURL = rootURL.appendPath(path, false);
                LockInfo lockInfo = (LockInfo) entriesMap.get(fullURL);
                SVNWCAccess wcAccess = createWCAccess(lockInfo.myFile);
                if (error == null) {
                    try {
                        wcAccess.open(true, false);
                        SVNEntry entry = wcAccess.getTargetEntry();
                        entry.setLockToken(lock.getID());
                        entry.setLockComment(lock.getComment());
                        entry.setLockOwner(lock.getOwner());
                        entry.setLockCreationDate(SVNTimeUtil.formatDate(lock.getCreationDate()));
                        if (wcAccess.getAnchor().getProperties(entry.getName(), false).getPropertyValue(SVNProperty.NEEDS_LOCK) != null) {
                            SVNFileUtil.setReadonly(wcAccess.getAnchor().getFile(entry.getName()), false);
                        }
                        if (wcAccess.getAnchor().getProperties(entry.getName(), false).getPropertyValue(SVNProperty.EXECUTABLE) != null) {
                            SVNFileUtil.setExecutable(wcAccess.getAnchor().getFile(entry.getName()), true);
                        }
                        wcAccess.getAnchor().getEntries().save(true);
                        wcAccess.getAnchor().getEntries().close();
                        handleEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess.getTargetName(), SVNEventAction.LOCKED, lock, null),
                                ISVNEventHandler.UNKNOWN);
                    } finally {
                        wcAccess.close(true);
                    }
                } else {
                    handleEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess.getTargetName(), SVNEventAction.LOCK_FAILED, lock, error), 
                            ISVNEventHandler.UNKNOWN);
                }
            }
            public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) {
            }
        });
    }
    
    /**
     * Locks file items in a repository so that no other user can commit 
     * changes to them.
     * 
     * @param  urls          an array of URLs to be locked 
     * @param  stealLock     if <span class="javakeyword">true</span> then all existing
     *                       locks on the specified <code>urls</code> will be "stolen"
     * @param  lockMessage   an optional lock comment  
     * @throws SVNException  
     * @see                  #doLock(File[], boolean, String)
     */
    public void doLock(SVNURL[] urls, boolean stealLock, String lockMessage) throws SVNException {
        Collection paths = new HashSet();
        SVNURL topURL = SVNURLUtil.condenceURLs(urls, paths, false);
        if (paths.isEmpty()) {
            paths.add("");
        }
        Map pathsToRevisions = new HashMap();
        for (Iterator p = paths.iterator(); p.hasNext();) {
            String path = (String) p.next();
            path = SVNEncodingUtil.uriDecode(path);
            pathsToRevisions.put(path, null);
        }
        checkCancelled();
        SVNRepository repository = createRepository(topURL, true);
        repository.lock(pathsToRevisions, lockMessage, stealLock, new ISVNLockHandler() {
            public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                if (error != null) {
                    handleEvent(SVNEventFactory.createLockEvent(path, SVNEventAction.LOCK_FAILED, lock, error), ISVNEventHandler.UNKNOWN);
                } else {
                    handleEvent(SVNEventFactory.createLockEvent(path, SVNEventAction.LOCKED, lock, null), ISVNEventHandler.UNKNOWN);
                }
            }
            public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
            }
            
        });
    }
    
    /**
     * Unlocks file items in a Working Copy as well as in a repository.
     * 
     * @param  paths          an array of local WC file paths that should be unlocked
     * @param  breakLock      if <span class="javakeyword">true</span> and there are locks
     *                        that belong to different users then those locks will be also
     *                        unlocked - that is "broken"
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>a path is not under version control
     *                        <li>can not obtain a URL of a local path to unlock it in 
     *                        the repository - there's no such entry
     *                        <li>if a path is not locked in the Working Copy
     *                        and <code>breakLock</code> is <span class="javakeyword">false</span>
     *                        <li><code>paths</code> to be unlocked belong to different repositories
     *                        </ul>
     * @see                   #doUnlock(SVNURL[], boolean)                       
     */
    public void doUnlock(File[] paths, boolean breakLock) throws SVNException {
        final Map entriesMap = new HashMap();
        for (int i = 0; i < paths.length; i++) {
            SVNWCAccess wcAccess = createWCAccess(paths[i]);
            try {
                wcAccess.open(true, false);
                SVNEntry entry = wcAccess.getTargetEntry();
                if (entry == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", paths[i]);
                    SVNErrorManager.error(err);
                }
                if (entry.getURL() == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", paths[i]);
                    SVNErrorManager.error(err);
                }
                String lockToken = entry.getLockToken();
                if (!breakLock && lockToken == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MISSING_LOCK_TOKEN, "''{0}'' is not locked", paths[i]);
                    SVNErrorManager.error(err);
                }
                entriesMap.put(entry.getSVNURL(),  new LockInfo(paths[i], lockToken));
                wcAccess.getAnchor().getEntries().close();
            } finally {
                wcAccess.close(true);
            }
        }
        SVNURL[] urls = (SVNURL[]) entriesMap.keySet().toArray(new SVNURL[entriesMap.size()]);
        Collection urlPaths = new HashSet();
        final SVNURL topURL = SVNURLUtil.condenceURLs(urls, urlPaths, false);
        if (urlPaths.isEmpty()) {
            urlPaths.add("");
        }
        if (topURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Paths belong to different repositories");
            SVNErrorManager.error(err);
        }
        Map pathsTokensMap = new HashMap();
        for(Iterator encodedPaths = urlPaths. iterator(); encodedPaths.hasNext();) {
            String encodedPath = (String) encodedPaths.next();
            SVNURL fullURL = topURL.appendPath(encodedPath, true);
            LockInfo lockInfo = (LockInfo) entriesMap.get(fullURL);
            encodedPath = SVNEncodingUtil.uriDecode(encodedPath);
            pathsTokensMap.put(encodedPath, lockInfo.myToken);
        }
        checkCancelled();
        SVNRepository repository = createRepository(topURL, true);
        final SVNURL rootURL = repository.getRepositoryRoot(true);
        repository.unlock(pathsTokensMap, breakLock, new ISVNLockHandler() {
            public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
            }
            public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                SVNURL fullURL = rootURL.appendPath(path, false);
                LockInfo lockInfo = (LockInfo) entriesMap.get(fullURL);
                SVNWCAccess wcAccess = createWCAccess(lockInfo.myFile);
                if (error != null) {
                    handleEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess.getTargetName(), SVNEventAction.UNLOCK_FAILED, null, error), ISVNEventHandler.UNKNOWN);
                }
                if (lock != null) {
                    try {
                        wcAccess.open(true, false);
                        SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(
                                wcAccess.getTargetName(), true);
                        entry.setLockToken(null);
                        entry.setLockComment(null);
                        entry.setLockOwner(null);
                        entry.setLockCreationDate(null);
                        wcAccess.getAnchor().getEntries().save(true);
                        wcAccess.getAnchor().getEntries().close();
                        if (wcAccess.getAnchor().getProperties(entry.getName(), false).getPropertyValue(SVNProperty.NEEDS_LOCK) != null) {
                            SVNFileUtil.setReadonly(wcAccess.getAnchor().getFile(entry.getName()), true);
                        }
                        handleEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess.getTargetName(), SVNEventAction.UNLOCKED, lock, null),
                                ISVNEventHandler.UNKNOWN);
                    } finally {
                        wcAccess.close(true);
                    }
                }
            }
        });
    }

    /**
     * Unlocks file items in a repository.
     * 
     * @param  urls            an array of URLs that should be unlocked
     * @param  breakLock       if <span class="javakeyword">true</span> and there are locks
     *                         that belong to different users then those locks will be also
     *                         unlocked - that is "broken"
     * @throws SVNException
     * @see                    #doUnlock(File[], boolean)
     */
    public void doUnlock(SVNURL[] urls, boolean breakLock) throws SVNException {
        Collection paths = new HashSet();
        SVNURL topURL = SVNURLUtil.condenceURLs(urls, paths, false);
        if (paths.isEmpty()) {
            paths.add("");
        }
        Map pathsToTokens = new HashMap();
        for (Iterator p = paths.iterator(); p.hasNext();) {
            String path = (String) p.next();
            path = SVNEncodingUtil.uriDecode(path);
            pathsToTokens.put(path, null);
        }
        
        checkCancelled();
        SVNRepository repository = createRepository(topURL, true);
        if (!breakLock) {
            pathsToTokens = fetchLockTokens(repository, pathsToTokens);
        }
        repository.unlock(pathsToTokens, breakLock, new ISVNLockHandler() {
            public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
            }
            public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                if (error != null) {
                    handleEvent(SVNEventFactory.createLockEvent(path, SVNEventAction.UNLOCK_FAILED, null, error), ISVNEventHandler.UNKNOWN);
                } else {
                    handleEvent(SVNEventFactory.createLockEvent(path, SVNEventAction.UNLOCKED, null, null), ISVNEventHandler.UNKNOWN);
                }
            }
        });
    }
    
    /**
     * Collects information about Working Copy item(s) and passes it to an 
     * info handler. 
     * 
     * <p>
     * If <code>revision</code> is valid and not local,  
     * then information will be collected on remote items (that is taken from
     * a repository). Otherwise information is gathered on local items not
     * accessing a repository.
     * 
     * @param  path            a WC item on which info should be obtained
     * @param  revision        a target revision 
     * @param  recursive       <span class="javakeyword">true</span> to
     *                         descend recursively (relevant for directories)
     * @param  handler         a caller's info handler
     * @throws SVNException    if one of the following is true:
     *                         <ul>
     *                         <li><code>path</code> is not under version control
     *                         <li>can not obtain a URL corresponding to <code>path</code> to 
     *                         get its information from the repository - there's no such entry
     *                         <li>if a remote info: <code>path</code> is an item that does not exist in
     *                         the specified <code>revision</code>
     *                         </ul>
     * @see                    #doInfo(File, SVNRevision)
     * @see                    #doInfo(SVNURL, SVNRevision, SVNRevision, boolean, ISVNInfoHandler)
     */
    public void doInfo(File path, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException {
        doInfo(path, SVNRevision.UNDEFINED, revision, recursive, handler);
    }
    
    /**
     * Collects information about Working Copy item(s) and passes it to an 
     * info handler. 
     * 
     * <p>
     * If <code>revision</code> & <code>pegRevision</code> are valid and not 
     * local, then information will be collected 
     * on remote items (that is taken from a repository). Otherwise information 
     * is gathered on local items not accessing a repository.
     * 
     * @param  path            a WC item on which info should be obtained
     * @param  pegRevision     a revision in which <code>path</code> is first 
     *                         looked up
     * @param  revision        a target revision 
     * @param  recursive       <span class="javakeyword">true</span> to
     *                         descend recursively (relevant for directories)
     * @param  handler         a caller's info handler
     * @throws SVNException    if one of the following is true:
     *                         <ul>
     *                         <li><code>path</code> is not under version control
     *                         <li>can not obtain a URL corresponding to <code>path</code> to 
     *                         get its information from the repository - there's no such entry
     *                         <li>if a remote info: <code>path</code> is an item that does not exist in
     *                         the specified <code>revision</code>
     *                         </ul>
     * @see                    #doInfo(File, SVNRevision)
     * @see                    #doInfo(File, SVNRevision, boolean, ISVNInfoHandler)
     */
    public void doInfo(File path, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        boolean local = (revision == null || !revision.isValid() || revision.isLocal()) &&
            (pegRevision == null || !pegRevision.isValid() || pegRevision.isLocal());
        
        if (!local) {
            SVNWCAccess wcAccess = createWCAccess(path);
            SVNRevision wcRevision = null;
            SVNURL url = null;
            try {
                wcAccess.open(false, false);
                SVNEntry entry = wcAccess.getTargetEntry();
                if (entry == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", path);
                    SVNErrorManager.error(err);
                }
                url = entry.getSVNURL();
                if (url == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path);
                    SVNErrorManager.error(err);
                }
                wcRevision = SVNRevision.create(entry.getRevision());
            } finally {
                wcAccess.close(false);
            }
            doInfo(url, pegRevision == null || !pegRevision.isValid() || pegRevision.isLocal() ? wcRevision : pegRevision, revision, recursive, handler);
            return;
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        try {
            wcAccess.open(false, recursive);
            collectInfo(wcAccess.getAnchor(), wcAccess.getTargetName(),
                    recursive, handler);
        } finally {
            wcAccess.close(false);
        }
    }
    
    /**
     * Collects information about item(s) in a repository and passes it to 
     * an info handler. 
     * 
     * 
     * @param  url              a URL of an item which information is to be
     *                          obtained and processed 
     * @param  pegRevision      a revision in which the item is first looked up
     * @param  revision         a target revision
     * @param  recursive        <span class="javakeyword">true</span> to
     *                          descend recursively (relevant for directories)
     * @param  handler          a caller's info handler
     * @throws SVNException     if <code>url</code> is an item that does not exist in
     *                          the specified <code>revision</code>
     * @see                     #doInfo(SVNURL, SVNRevision, SVNRevision)
     * @see                     #doInfo(File, SVNRevision, boolean, ISVNInfoHandler)
     */
    public void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler)
            throws SVNException {
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.HEAD;
        }
        if (pegRevision == null || !pegRevision.isValid()) {
            pegRevision = revision;
        }

        SVNRepository repos = createRepository(url, null, pegRevision, revision);;
        url = repos.getLocation();
        long revNum = getRevisionNumber(revision, repos, null);
        SVNDirEntry rootEntry = null;
        try {
            rootEntry = repos.info("", revNum);
        } catch (SVNException e) {
            if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_IMPLEMENTED) {
                // for svnserve older then 1.2.0
                if (repos.getLocation().equals(repos.getRepositoryRoot(true))) {
                    rootEntry = new SVNDirEntry(url, "", SVNNodeKind.DIR, -1, false, -1, null, null);
                } else {
                    String name = SVNPathUtil.tail(url.getPath());
                    SVNURL location = repos.getLocation();
                    repos.setLocation(location.removePathTail(), false);
                    Collection dirEntries = repos.getDir("", revNum, null, (Collection) null);
                    for (Iterator ents = dirEntries.iterator(); ents.hasNext();) {
                        SVNDirEntry dirEntry = (SVNDirEntry) ents.next();
                        // dir entry name may differ from 'name', due to renames...
                        if (name.equals(dirEntry.getName())) {
                            rootEntry = dirEntry;
                            break;
                        }
                    }
                    repos.setLocation(location, false);
                }
            } else {
                throw e;
            }
        }
        if (rootEntry == null || rootEntry.getKind() == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL ''{0}'' non-existent in revision ''{1}''",
                    new Object[] {url, new Long(revNum)});
            SVNErrorManager.error(err);
        }
        SVNURL reposRoot = repos.getRepositoryRoot(true);
        String reposUUID = repos.getRepositoryUUID(true);
        // 1. get locks for this dir and below (only for dir).
        // and only when pegRev is HEAD.
        SVNLock[] locks = null;
        if (pegRevision == SVNRevision.HEAD && rootEntry.getKind() == SVNNodeKind.DIR) {
            try {
                locks = repos.getLocks("");
            } catch (SVNException e) {
                // may be not supported.
                if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_IMPLEMENTED) {
                    locks = new SVNLock[0];
                } else {
                    throw e;
                }
            }
        }
        locks = locks == null ? new SVNLock[0] : locks;
        Map locksMap = new HashMap();
        for (int i = 0; i < locks.length; i++) {
            SVNLock lock = locks[i];
            locksMap.put(lock.getPath(), lock);
        }        
        // 2. add lock for this entry, only when it is 'related' to head (and is a file).
        if (rootEntry.getKind() == SVNNodeKind.FILE) {
            try {
                SVNRepositoryLocation[] locations = getLocations(url, null, revision, SVNRevision.HEAD, SVNRevision.UNDEFINED);
                if (locations != null && locations.length > 0) {
                    SVNURL headURL = locations[0].getURL();
                    if (headURL.equals(url)) {
                        // get lock for this item (@headURL).
                        try {
                            SVNLock lock = repos.getLock("");
                            if (lock != null) {
                                locksMap.put(lock.getPath(), lock);
                            }
                        } catch (SVNException e) {
                            if (!(e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_IMPLEMENTED)) {
                                throw e;
                            } 
                        }
                    }
                }
            } catch (SVNException e) {
                SVNErrorCode code = e.getErrorMessage().getErrorCode();
                if (code != SVNErrorCode.FS_NOT_FOUND && code != SVNErrorCode.CLIENT_UNRELATED_RESOURCES) {
                    throw e;
                }
            }
        }
        
        String fullPath = url.getPath();
        String rootPath = fullPath.substring(reposRoot.getPath().length());
        if (!rootPath.startsWith("/")) {
            rootPath = "/" + rootPath;
        }
        collectInfo(repos, rootEntry, SVNRevision.create(revNum), rootPath, reposRoot, reposUUID, url, locksMap, recursive, handler);
    }
    
    /**
     * Returns the current Working Copy min- and max- revisions as well as
     * changes and switch status within a single string.
     * 
     * <p>
     * A return string has a form of <code>"minR[:maxR][M][S]"</code> where:
     * <ul>
     * <li><code>minR</code> - is the smallest revision number met in the
     * Working Copy
     * <li><code>maxR</code> - is the biggest revision number met in the
     * Working Copy; appears only if there are different revision in the
     * Working Copy
     * <li><code>M</code> - appears only if there're local edits to the 
     * Working Copy - that means 'Modified'
     * <li><code>S</code> - appears only if the Working Copy is switched
     * against a different URL
     * </ul>
     * If <code>path</code> is a directory - this method recursively descends
     * into the Working Copy, collects and processes local information. 
     * 
     * @param  path            a local path
     * @param  trailURL        optional: if not <span class="javakeyword">null</span>
     *                         specifies the name of the item that should be met 
     *                         in the URL corresponding to the repository location
     *                         of the <code>path</code>; if that URL ends with something
     *                         different than this optional parameter - the Working
     *                         Copy will be considered "switched"   
     * @return                 brief info on the Working Copy or the string 
     *                         "exported" if <code>path</code> is a clean directory  
     * @throws SVNException    if <code>path</code> is neither versioned nor
     *                         even exported
     */
    public String doGetWorkingCopyID(final File path, String trailURL) throws SVNException {
        try {
            createWCAccess(path);
        } catch (SVNException e) {            
            SVNFileType pathType = SVNFileType.getType(path);
            if (pathType == SVNFileType.DIRECTORY) {
                return "exported";
            } 
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "''{0}'' is not versioned and not exported", path);
            SVNErrorManager.error(err);
        }
        SVNStatusClient statusClient = new SVNStatusClient((ISVNAuthenticationManager) null, getOptions());
        statusClient.setIgnoreExternals(true);
        final long[] maxRevision = new long[1];
        final long[] minRevision = new long[] {-1};
        final boolean[] switched = new boolean[2];
        final String[] wcURL = new String[1];
        statusClient.doStatus(path, true, false, true, false, false, new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) {
                if (status.getEntryProperties() == null || status.getEntryProperties().isEmpty()) {
                    return;
                }
                if (status.getContentsStatus() != SVNStatusType.STATUS_ADDED) {
                    SVNRevision revision = status.getRevision();
                    if (revision != null) {
                        if (minRevision[0] < 0 || minRevision[0] > revision.getNumber()) {
                            minRevision[0] = revision.getNumber();
                        }
                        maxRevision[0] = Math.max(maxRevision[0], revision.getNumber());
                    }
                }
                switched[0] |= status.isSwitched();
                switched[1] |= status.getContentsStatus() != SVNStatusType.STATUS_NORMAL;
                switched[1] |= status.getPropertiesStatus() != SVNStatusType.STATUS_NORMAL &&
                    status.getPropertiesStatus() != SVNStatusType.STATUS_NONE;
                if (wcURL[0] == null && status.getFile() != null && status.getFile().equals(path) && status.getURL() != null) {
                    wcURL[0] = status.getURL().toString();
                }
            }
        });
        if (!switched[0] && trailURL != null) {
            if (wcURL[0] == null) {
                switched[0] = true;
            } else {
                switched[0] = !wcURL[0].endsWith(trailURL);
            }
        }
        StringBuffer id = new StringBuffer();
        id.append(minRevision[0]);
        if (minRevision[0] != maxRevision[0]) {
            id.append(":").append(maxRevision[0]);
        }
        if (switched[1]) {
            id.append("M");
        }
        if (switched[0]) {
            id.append("S");
        }
        return id.toString();
    } 
    
    /**
     * Collects and returns information on a single Working Copy item.
     * 
     * <p>
     * If <code>revision</code> is valid and not {@link SVNRevision#WORKING WORKING} 
     * then information will be collected on remote items (that is taken from
     * a repository). Otherwise information is gathered on local items not
     * accessing a repository.
     * 
     * @param  path            a WC item on which info should be obtained
     * @param  revision        a target revision
     * @return                 collected info
     * @throws SVNException    if one of the following is true:
     *                         <ul>
     *                         <li><code>path</code> is not under version control
     *                         <li>can not obtain a URL corresponding to <code>path</code> to 
     *                         get its information from the repository - there's no such entry 
     *                         <li>if a remote info: <code>path</code> is an item that does not exist in
     *                         the specified <code>revision</code>
     *                         </ul>
     * @see                    #doInfo(File, SVNRevision, boolean, ISVNInfoHandler)
     * @see                    #doInfo(SVNURL, SVNRevision, SVNRevision)                        
     */
    public SVNInfo doInfo(File path, SVNRevision revision) throws SVNException {
        final SVNInfo[] result = new SVNInfo[1];
        doInfo(path, revision, false, new ISVNInfoHandler() {
            public void handleInfo(SVNInfo info) {
                if (result[0] == null) {
                    result[0] = info;
                }
            }
        });
        return result[0];
    }
    
    /**
     * Collects and returns information on a single item in a repository. 
     * 
     * @param  url             a URL of an item which information is to be
     *                         obtained       
     * @param  pegRevision     a revision in which the item is first looked up
     * @param  revision        a target revision
     * @return                 collected info
     * @throws SVNException    if <code>url</code> is an item that does not exist in
     *                         the specified <code>revision</code>
     * @see                    #doInfo(SVNURL, SVNRevision, SVNRevision, boolean, ISVNInfoHandler)
     * @see                    #doInfo(File, SVNRevision)
     */
    public SVNInfo doInfo(SVNURL url, SVNRevision pegRevision,
            SVNRevision revision) throws SVNException {
        final SVNInfo[] result = new SVNInfo[1];
        doInfo(url, pegRevision, revision, false, new ISVNInfoHandler() {
            public void handleInfo(SVNInfo info) {
                if (result[0] == null) {
                    result[0] = info;
                }
            }
        });
        return result[0];
    }

    private static void collectInfo(SVNDirectory dir, String name,
            boolean recursive, ISVNInfoHandler handler) throws SVNException {
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry(name, false);
        dir.getWCAccess().checkCancelled();
        try {
            if (entry != null) {
                if (entry.isFile()) {
                    // child file
                    File file = dir.getFile(name);
                    handler.handleInfo(SVNInfo.createInfo(file, entry));
                    return;
                } else if (entry.isDirectory() && !"".equals(name)) {
                    // child dir
                    dir = dir.getChildDirectory(name);
                    if (dir != null) {
                        collectInfo(dir, "", recursive, handler);
                    }
                    return;
                } else if ("".equals(name)) {
                    // report root.
                    handler
                            .handleInfo(SVNInfo
                                    .createInfo(dir.getRoot(), entry));
                }

                if (recursive) {
                    for (Iterator ents = entries.entries(true); ents.hasNext();) {
                        SVNEntry childEntry = (SVNEntry) ents.next();
                        if ("".equals(childEntry.getName())) {
                            continue;
                        }
                        if (childEntry.isDirectory()) {
                            SVNDirectory childDir = dir
                                    .getChildDirectory(childEntry.getName());
                            if (childDir != null) {
                                collectInfo(childDir, "", recursive, handler);
                            }
                        } else if (childEntry.isFile()) {
                            handler.handleInfo(SVNInfo.createInfo(dir.getFile(childEntry.getName()), childEntry));
                        }
                    }
                }
            }
        } finally {
            entries.close();
        }

    }

    private void collectInfo(SVNRepository repos, SVNDirEntry entry,
            SVNRevision rev, String path, SVNURL root, String uuid, SVNURL url,
            Map locks, boolean recursive, ISVNInfoHandler handler)
            throws SVNException {
        checkCancelled();
        String displayPath = repos.getFullPath(path);
        displayPath = displayPath.substring(repos.getLocation().getPath().length());
        if ("".equals(displayPath) || "/".equals(displayPath)) {
            displayPath = path;
        }
        handler.handleInfo(SVNInfo.createInfo(displayPath, root, uuid, url, rev, entry, (SVNLock) locks.get(path)));
        if (entry.getKind() == SVNNodeKind.DIR && recursive) {
            Collection children = repos.getDir(path, rev.getNumber(), null,
                    new ArrayList());
            for (Iterator ents = children.iterator(); ents.hasNext();) {
                SVNDirEntry child = (SVNDirEntry) ents.next();
                SVNURL childURL = url.appendPath(child.getName(), false);
                collectInfo(repos, child, rev, SVNPathUtil.append(path, child
                        .getName()), root, uuid, childURL, locks, recursive,
                        handler);
            }
        }
    }

    private void addDirectory(SVNWCAccess wcAccess, SVNDirectory dir, String name, boolean force) throws SVNException {
        checkCancelled();
        if (dir.add(name, false, force) == null) {
            return;
        }
        File file = dir.getFile(name);
        SVNDirectory childDir = dir.getChildDirectory(name);
        if (childDir == null) {
            return;
        }
        File[] children = file.listFiles();
        for (int i = 0; children != null && i < children.length; i++) {
            File childFile = children[i];
            if (getOptions().isIgnored(childFile.getName()) ||  childDir.isIgnored(childFile.getName())) {
                continue;
            }
            if (SVNFileUtil.getAdminDirectoryName().equals(childFile.getName())) {
                continue;
            }
            SVNFileType fileType = SVNFileType.getType(childFile);
            if (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK) {
                SVNEntry entry = childDir.getEntries().getEntry(childFile.getName(), true);
                if (force && entry != null && !entry.isScheduledForDeletion() && !entry.isDeleted()) {
                    continue;
                }
                addSingleFile(childDir, childFile.getName());
            } else if (SVNFileType.DIRECTORY == fileType) {
                addDirectory(wcAccess, childDir, childFile.getName(), force);
            }
        }
    }

    private void addSingleFile(SVNDirectory dir, String name) throws SVNException {
        checkCancelled();
        File file = dir.getFile(name);
        dir.add(name, false, false);

        SVNProperties properties = dir.getProperties(name, false);
        if (SVNFileType.getType(file) == SVNFileType.SYMLINK) {
            properties.setPropertyValue(SVNProperty.SPECIAL, "*");
        } else {
            Map props = new HashMap();
	        String mimeType = SVNFileUtil.detectMimeType(file);
	        if (mimeType != null) {
	            props.put(SVNProperty.MIME_TYPE, mimeType);
	        }
            boolean executable;
            props = getOptions().applyAutoProperties(name, props);
            if (props.get(SVNProperty.MIME_TYPE) == null && mimeType != null) {
                props.put(SVNProperty.MIME_TYPE, mimeType);
                if (SVNProperty.isBinaryMimeType(mimeType)) {
                    props.remove(SVNProperty.EOL_STYLE);
                }
            }
            if (!props.containsKey(SVNProperty.EXECUTABLE)) {
                executable = SVNFileUtil.isExecutable(file);
                if (executable) {
                    props.put(SVNProperty.EXECUTABLE, "*");
                }
            }
            if (SVNProperty.isBinaryMimeType((String) props.get(SVNProperty.MIME_TYPE)) && props.get(SVNProperty.EOL_STYLE) != null) {
                props.remove(SVNProperty.EOL_STYLE);
            }
            
            for (Iterator names = props.keySet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                String propValue = (String) props.get(propName);
                try {
                    doSetLocalProperty(dir, name, propName, propValue, false, false, false, null);
                } catch (SVNException e) {
                    // skip cancellation here.
                }
            }
        }
    }

    private void doGetRemoteProperty(SVNURL url, String path,
            SVNRepository repos, String propName, SVNRevision rev,
            boolean recursive, ISVNPropertyHandler handler) throws SVNException {
        checkCancelled();
        long revNumber = getRevisionNumber(rev, repos, null);
        SVNNodeKind kind = repos.checkPath(path, revNumber);
        Map props = new HashMap();
        if (kind == SVNNodeKind.DIR) {
            Collection children = repos.getDir(path, revNumber, props,
                    recursive ? new ArrayList() : null);
            if (propName != null) {
                String value = (String) props.get(propName);
                if (value != null) {
                    handler.handleProperty(url, new SVNPropertyData(propName, value));
                }
            } else {
                for (Iterator names = props.keySet().iterator(); names.hasNext();) {
                    String name = (String) names.next();
                    if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)
                            || name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                        continue;
                    }
                    String value = (String) props.get(name);
                    handler.handleProperty(url, new SVNPropertyData(name, value));
                }
            }
            if (recursive) {
                checkCancelled();
                for (Iterator entries = children.iterator(); entries.hasNext();) {
                    SVNDirEntry child = (SVNDirEntry) entries.next();
                    SVNURL childURL = url.appendPath(child.getName(), false);
                    String childPath = "".equals(path) ? child.getName() : SVNPathUtil.append(path, child.getName());
                    doGetRemoteProperty(childURL, childPath, repos, propName, rev, recursive, handler);
                }
            }
        } else if (kind == SVNNodeKind.FILE) {
            repos.getFile(path, revNumber, props, null);
            if (propName != null) {
                String value = (String) props.get(propName);
                if (value != null) {
                    handler.handleProperty(url, new SVNPropertyData(propName, value));
                }
            } else {
                for (Iterator names = props.keySet().iterator(); names
                        .hasNext();) {
                    String name = (String) names.next();
                    if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)
                            || name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                        continue;
                    }
                    String value = (String) props.get(name);
                    handler.handleProperty(url, new SVNPropertyData(name, value));
                }
            }
        }
    }

    private void doGetLocalProperty(SVNDirectory anchor, String name,
            String propName, SVNRevision rev, boolean recursive,
            ISVNPropertyHandler handler) throws SVNException {
        checkCancelled();
        SVNEntries entries = anchor.getEntries();
        SVNEntry entry = entries.getEntry(name, true);
        if (entry == null
                || (rev == SVNRevision.WORKING && entry
                        .isScheduledForDeletion())) {
            return;
        }
        if (!"".equals(name)) {
            if (entry.getKind() == SVNNodeKind.DIR) {
                SVNDirectory dir = anchor.getChildDirectory(name);
                if (dir != null) {
                    doGetLocalProperty(dir, "", propName, rev, recursive,
                            handler);
                }
            } else if (entry.getKind() == SVNNodeKind.FILE) {
                SVNProperties props = rev == SVNRevision.WORKING ? anchor
                        .getProperties(name, false) : anchor.getBaseProperties(
                        name, false);
                if (propName != null) {
                    String value = props.getPropertyValue(propName);
                    if (value != null) {
                        handler.handleProperty(anchor.getFile(name), new SVNPropertyData(propName, value));
                    }
                } else {
                    Map propsMap = props.asMap();
                    for (Iterator names = propsMap.keySet().iterator(); names
                            .hasNext();) {
                        String pName = (String) names.next();
                        String value = (String) propsMap.get(pName);
                        handler.handleProperty(anchor.getFile(name), new SVNPropertyData(pName, value));
                    }
                }
            }
            entries.close();
            return;
        }
        SVNProperties props = rev == SVNRevision.WORKING ? anchor
                .getProperties(name, false) : anchor.getBaseProperties(name,
                false);
        if (propName != null) {
            String value = props.getPropertyValue(propName);
            if (value != null) {
                handler.handleProperty(anchor.getFile(name), new SVNPropertyData(propName, value));
            }
        } else {
            Map propsMap = props.asMap();
            for (Iterator names = propsMap.keySet().iterator(); names.hasNext();) {
                String pName = (String) names.next();
                String value = (String) propsMap.get(pName);
                handler.handleProperty(anchor.getFile(name), new SVNPropertyData(pName, value));
            }
        }
        if (!recursive) {
            return;
        }
        for (Iterator ents = entries.entries(true); ents.hasNext();) {
            SVNEntry childEntry = (SVNEntry) ents.next();
            if ("".equals(childEntry.getName())) {
                continue;
            }
            doGetLocalProperty(anchor, childEntry.getName(), propName, rev,
                    recursive, handler);
        }
    }

    private void doSetLocalProperty(SVNDirectory anchor, String name, String propName, String propValue, boolean force,
            boolean recursive, boolean cancel, ISVNPropertyHandler handler) throws SVNException {
        if (cancel) {
            checkCancelled();
        }
        SVNEntries entries = anchor.getEntries();
        if (!"".equals(name)) {
            SVNEntry entry = entries.getEntry(name, true);
            if (entry == null || (recursive && entry.isDeleted())) {
                return;
            }
            if (entry.getKind() == SVNNodeKind.DIR) {
                SVNDirectory dir = anchor.getChildDirectory(name);
                if (dir != null) {
                    doSetLocalProperty(dir, "", propName, propValue, force, recursive, cancel, handler);
                }
            } else if (entry.getKind() == SVNNodeKind.FILE) {
                File wcFile = anchor.getFile(name);
                if ((SVNProperty.IGNORE.equals(propName) || SVNProperty.EXTERNALS.equals(propName)) && propValue != null) {
                    if (!recursive) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot set ''{0}'' on a file (''{1}'')",
                                new Object[] {propName, wcFile});
                        SVNErrorManager.error(err);
                    }
                    return;
                }
                SVNProperties props = anchor.getProperties(name, false);
                if (SVNProperty.EXECUTABLE.equals(propName)) {
                    SVNFileUtil.setExecutable(wcFile, propValue != null);
                }
                if (!force && SVNProperty.EOL_STYLE.equals(propName) && propValue != null) {
                    if (SVNProperty.isBinaryMimeType(props.getPropertyValue(SVNProperty.MIME_TYPE))) {
                        if (!recursive) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "File ''{0}'' has binary mime type property", wcFile);
                            SVNErrorManager.error(err);
                        }
                        return;
                    }
                    if (!SVNTranslator.checkNewLines(wcFile)) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "File ''{0}'' has incosistent newlines", wcFile);
                        SVNErrorManager.error(err);
                    } 
                }
                String oldValue = props.getPropertyValue(propName);
                boolean modified = oldValue == null ? propValue != null : !oldValue.equals(propValue);
                props.setPropertyValue(propName, propValue);

                if (SVNProperty.EOL_STYLE.equals(propName) || SVNProperty.KEYWORDS.equals(propName)) {
                    entry.setTextTime(null);
                    entries.save(false);
                } else if (SVNProperty.NEEDS_LOCK.equals(propName) && propValue == null) {
                    SVNFileUtil.setReadonly(wcFile, false);
                }
                if (modified && handler != null) {
                    handler.handleProperty(anchor.getFile(name), new SVNPropertyData(propName, propValue));
                }
            }
            entries.close();
            return;
        }
        SVNProperties props = anchor.getProperties(name, false);
        if ((SVNProperty.KEYWORDS.equals(propName)
                || SVNProperty.EOL_STYLE.equals(propName)
                || SVNProperty.MIME_TYPE.equals(propName)
                || SVNProperty.EXECUTABLE.equals(propName)) && propValue != null) {
            if (!recursive) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot set ''{0}'' on a directory (''{1}'')",
                        new Object[] {propName, anchor.getRoot()});
                SVNErrorManager.error(err);
            }
        } else {
            String oldValue = props.getPropertyValue(propName);
            boolean modified = oldValue == null ? propValue != null : !oldValue.equals(propValue);
            props.setPropertyValue(propName, propValue);
            if (modified && handler != null) {
                handler.handleProperty(anchor.getFile(name), new SVNPropertyData(propName, propValue));
            }
        }
        if (!recursive) {
            return;
        }
        for (Iterator ents = entries.entries(true); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            doSetLocalProperty(anchor, entry.getName(), propName, propValue,
                    force, recursive, cancel, handler);
        }
    }

    private void doSetLocalProperty(SVNAdminArea anchor, String name, String propName, String propValue, boolean force,
            boolean recursive, boolean cancel, ISVNPropertyHandler handler) throws SVNException {
        if (cancel) {
            checkCancelled();
        }
        if (!"".equals(name)) {
            SVNEntry2 entry = anchor.getEntry(name, true);
            if (entry == null || (recursive && entry.isDeleted())) {
                return;
            }
            if (entry.getKind() == SVNNodeKind.DIR) {
                File path = new File(anchor.getRoot(), name);
                SVNAdminArea dir = anchor.getWCAccess().get(path, false);
                if (dir != null) {
                    doSetLocalProperty(dir, "", propName, propValue, force, recursive, cancel, handler);
                }
            } else if (entry.getKind() == SVNNodeKind.FILE) {
                File wcFile = anchor.getFile(name);
                if ((SVNProperty.IGNORE.equals(propName) || SVNProperty.EXTERNALS.equals(propName)) && propValue != null) {
                    if (!recursive) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot set ''{0}'' on a file (''{1}'')",
                                new Object[] {propName, wcFile});
                        SVNErrorManager.error(err);
                    }
                    return;
                }
                ISVNProperties props = anchor.getProperties(name);
                if (SVNProperty.EXECUTABLE.equals(propName)) {
                    SVNFileUtil.setExecutable(wcFile, propValue != null);
                }
                if (!force && SVNProperty.EOL_STYLE.equals(propName) && propValue != null) {
                    if (SVNProperty.isBinaryMimeType(props.getPropertyValue(SVNProperty.MIME_TYPE))) {
                        if (!recursive) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "File ''{0}'' has binary mime type property", wcFile);
                            SVNErrorManager.error(err);
                        }
                        return;
                    }
                    if (!SVNTranslator.checkNewLines(wcFile)) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "File ''{0}'' has incosistent newlines", wcFile);
                        SVNErrorManager.error(err);
                    } 
                }
                String oldValue = props.getPropertyValue(propName);
                boolean modified = oldValue == null ? propValue != null : !oldValue.equals(propValue);
                props.setPropertyValue(propName, propValue);

                if (SVNProperty.EOL_STYLE.equals(propName) || SVNProperty.KEYWORDS.equals(propName)) {
                    entry.setTextTime(null);
                } else if (SVNProperty.NEEDS_LOCK.equals(propName) && propValue == null) {
                    SVNFileUtil.setReadonly(wcFile, false);
                }
                if (modified && handler != null) {
                    handler.handleProperty(anchor.getFile(name), new SVNPropertyData(propName, propValue));
                }
            }
            ISVNLog log = anchor.getLog();
            anchor.saveVersionedProperties(log, true);
            anchor.saveEntries(true);
            log.save();
            anchor.runLogs();
            return;
        }
        ISVNProperties props = anchor.getProperties(name);
        if ((SVNProperty.KEYWORDS.equals(propName)
                || SVNProperty.EOL_STYLE.equals(propName)
                || SVNProperty.MIME_TYPE.equals(propName)
                || SVNProperty.EXECUTABLE.equals(propName)) && propValue != null) {
            if (!recursive) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot set ''{0}'' on a directory (''{1}'')",
                        new Object[] {propName, anchor.getRoot()});
                SVNErrorManager.error(err);
            }
        } else {
            String oldValue = props.getPropertyValue(propName);
            boolean modified = oldValue == null ? propValue != null : !oldValue.equals(propValue);
            props.setPropertyValue(propName, propValue);
            ISVNLog log = anchor.getLog();
            anchor.saveVersionedProperties(log, true);
            log.save();
            anchor.runLogs();
            if (modified && handler != null) {
                handler.handleProperty(anchor.getFile(name), new SVNPropertyData(propName, propValue));
            }
        }
        if (!recursive) {
            return;
        }
        for (Iterator ents = anchor.entries(true); ents.hasNext();) {
            SVNEntry2 entry = (SVNEntry2) ents.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            doSetLocalProperty(anchor, entry.getName(), propName, propValue,
                    force, recursive, cancel, handler);
        }
    }

    private static String validatePropertyName(String name) throws SVNException {
        if (name == null || name.trim().length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, "Property name is empty");
            SVNErrorManager.error(err);
            return name;
        }
        name = name.trim();
        if (!(Character.isLetter(name.charAt(0)) || name.charAt(0) == ':' || name.charAt(0) == '_')) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, "Bad property name ''{0}''", name);
            SVNErrorManager.error(err);
        }
        for (int i = 1; i < name.length(); i++) {
            if (!(Character.isLetterOrDigit(name.charAt(i))
                    || name.charAt(i) == '-' || name.charAt(i) == '.'
                    || name.charAt(i) == ':' || name.charAt(i) == '_')) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, "Bad property name ''{0}''", name);
                SVNErrorManager.error(err);
            }
        }
        return name;
    }

    private static String validatePropertyValue(String name, String value, boolean force) throws SVNException {
        if (value == null) {
            return value;
        }
        if (SVNProperty.isSVNProperty(name)) {
            value = value.replaceAll("\r\n", "\n");
            value = value.replace('\r', '\n');
        }
        if (!force && SVNProperty.EOL_STYLE.equals(name)) {
            value = value.trim();
        } else if (!force && SVNProperty.MIME_TYPE.equals(name)) {
            value = value.trim();
        } else if (SVNProperty.IGNORE.equals(name) || SVNProperty.EXTERNALS.equals(name)) {
            if (!value.endsWith("\n")) {
                value += "\n";
            }
            if (SVNProperty.EXTERNALS.equals(name)) {
                SVNExternalInfo[] externalInfos = SVNWCAccess.parseExternals("", value);
                for (int i = 0; externalInfos != null && i < externalInfos.length; i++) {
                    String path = externalInfos[i].getPath();
                    SVNExternalInfo.checkPath(path);
                }
            }
        } else if (SVNProperty.KEYWORDS.equals(name)) {
            value = value.trim();
        } else if (SVNProperty.EXECUTABLE.equals(name) || SVNProperty.SPECIAL.equals(name) || SVNProperty.NEEDS_LOCK.equals(name)) {
            value = "*";
        }
        return value;
    }
    
    private Map fetchLockTokens(SVNRepository repository, Map pathsTokensMap) throws SVNException {
        Map tokens = new HashMap();
        for (Iterator paths = pathsTokensMap.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNLock lock = repository.getLock(path);
            if (lock == null || lock.getID() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MISSING_LOCK_TOKEN, "''{0}'' is not locked", path);
                SVNErrorManager.error(err);
                continue;
            }
            tokens.put(path, lock.getID());
        }
        return tokens;
    }

    private static class LockInfo {

        public LockInfo(File file, SVNRevision rev) {
            myFile = file;
            myRevision = rev;
        }

        public LockInfo(File file, String token) {
            myFile = file;
            myToken = token;
        }

        private File myFile;
        private SVNRevision myRevision;
        private String myToken;
    }
}
