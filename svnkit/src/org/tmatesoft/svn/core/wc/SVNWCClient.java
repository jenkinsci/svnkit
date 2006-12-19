/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.internal.wc.SVNStatusEditor;
import org.tmatesoft.svn.core.internal.wc.SVNWCManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslatorOutputStream;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
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
 * <td><b>SVNKit</b></td>
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
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
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
    public void doGetFileContents(File path, SVNRevision pegRevision, SVNRevision revision, boolean expandKeywords, OutputStream dst) throws SVNException {
        if (dst == null) {
            return;
        }
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.BASE;
        } else if (revision == SVNRevision.COMMITTED) {
            revision = SVNRevision.BASE;
        }
        if ((!pegRevision.isValid() || pegRevision == SVNRevision.BASE || pegRevision == SVNRevision.WORKING) && 
                (!revision.isValid() || revision == SVNRevision.BASE || revision == SVNRevision.WORKING)) {
            if (pegRevision == null || !pegRevision.isValid()) {
                pegRevision = SVNRevision.BASE;
            } else if (pegRevision == SVNRevision.COMMITTED) {
                pegRevision = SVNRevision.BASE;
            }
            doGetLocalFileContents(path, dst, revision, expandKeywords);
        } else {
            SVNRepository repos = createRepository(null, path, pegRevision, revision);
            checkCancelled();
            long revNumber = getRevisionNumber(revision, repos, path);
            SVNNodeKind kind = repos.checkPath("", revNumber);
            if (kind == SVNNodeKind.DIR) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_IS_DIRECTORY, "URL ''{0}'' refers to a directory", repos.getLocation());
                SVNErrorManager.error(err);
            }
            checkCancelled();
            if (!expandKeywords) {
                repos.getFile("", revNumber, null, new SVNCancellableOutputStream(dst, this));
            } else {
                Map properties = new HashMap();
                repos.getFile("", revNumber, properties, null);
                checkCancelled();

                String keywords = (String) properties.get(SVNProperty.KEYWORDS);
                String eol = (String) properties.get(SVNProperty.EOL_STYLE);
                if (keywords != null || eol != null) {
                    String cmtRev = (String) properties.get(SVNProperty.COMMITTED_REVISION);
                    String cmtDate = (String) properties.get(SVNProperty.COMMITTED_DATE);
                    String author = (String) properties.get(SVNProperty.LAST_AUTHOR);
                    Map keywordsMap = SVNTranslator.computeKeywords(keywords, expandKeywords ? repos.getLocation().toString() : null, author, cmtDate, cmtRev, getOptions());
                    OutputStream translatingStream = new SVNTranslatorOutputStream(dst, SVNTranslator.getEOL(eol), false, keywordsMap, expandKeywords); 
                    repos.getFile("", revNumber, null, new SVNCancellableOutputStream(translatingStream, getEventDispatcher()));
                    try {
                        translatingStream.close();
                    } catch (IOException e) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()));
                    }
                } else {
                    repos.getFile("", revNumber, null, new SVNCancellableOutputStream(dst, getEventDispatcher()));
                }
            }
            try {
                dst.flush();
            } catch (IOException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()));
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
        SVNRepository repos = createRepository(url, null, pegRevision, revision);
        checkCancelled();
        long revNumber = getRevisionNumber(revision, repos, null);
        checkCancelled();
        SVNNodeKind nodeKind = repos.checkPath("", revNumber);
        checkCancelled();
        if (nodeKind == SVNNodeKind.DIR) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_IS_DIRECTORY, "URL ''{0}'' refers to a directory", url, SVNErrorMessage.TYPE_WARNING);
            SVNErrorManager.error(err);
        }
        checkCancelled();
        if (!expandKeywords) {
            repos.getFile("", revNumber, null, new SVNCancellableOutputStream(dst, this));
        } else {
            Map properties = new HashMap();
            repos.getFile("", revNumber, properties, null);
            checkCancelled();

            String keywords = (String) properties.get(SVNProperty.KEYWORDS);
            String eol = (String) properties.get(SVNProperty.EOL_STYLE);
            if (keywords != null || eol != null) {
                String cmtRev = (String) properties.get(SVNProperty.COMMITTED_REVISION);
                String cmtDate = (String) properties.get(SVNProperty.COMMITTED_DATE);
                String author = (String) properties.get(SVNProperty.LAST_AUTHOR);
                Map keywordsMap = SVNTranslator.computeKeywords(keywords, expandKeywords ? repos.getLocation().toString() : null, author, cmtDate, cmtRev, getOptions());
                OutputStream translatingStream = new SVNTranslatorOutputStream(dst, SVNTranslator.getEOL(eol), false, keywordsMap, expandKeywords); 
                repos.getFile("", revNumber, null, new SVNCancellableOutputStream(translatingStream, getEventDispatcher()));
                try {
                    translatingStream.close();
                } catch (IOException e) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()));
                }
            } else {
                repos.getFile("", revNumber, null, new SVNCancellableOutputStream(dst, getEventDispatcher()));
            }
        }
        try {
            dst.flush();
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()));
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
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminArea adminArea = wcAccess.open(path, true, true, 0);
            adminArea.cleanup();
        } finally {
            wcAccess.close();
            sleepForTimeStamp();
        }
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
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminArea area = wcAccess.probeOpen(path, true, recursive ? SVNWCAccess.INFINITE_DEPTH : 1);//wcAccess.open(path, true, recursive ? SVNWCAccess2.INFINITE_DEPTH : 1);
            SVNEntry entry = wcAccess.getEntry(path, false);
            if (entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", path);
                SVNErrorManager.error(err);
            }
            doSetLocalProperty(area, entry.isDirectory() ? area.getThisDirName() : entry.getName(), propName, propValue, force, recursive, true, handler);
        } finally {
            wcAccess.close();
        }
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
        SVNWCAccess wcAccess = createWCAccess();

        try {
            SVNAdminArea area = wcAccess.probeOpen(path, false, recursive ? SVNWCAccess.INFINITE_DEPTH : 0);
            SVNEntry entry = wcAccess.getEntry(path, false);
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
                boolean base = revision == SVNRevision.BASE;
                if (entry.getKind() == SVNNodeKind.DIR && recursive){
                    // area is path itself.
                    doGetLocalProperty(area, propName, base, handler);
                } else {
                    // area could only be path itself or child file in it.
                    if ((base && entry.isScheduledForAddition()) || (!base && entry.isScheduledForDeletion())) {
                        return;
                    }
                    SVNVersionedProperties properties = base ? area.getBaseProperties(entry.getName()) : area.getProperties(entry.getName());
                    if (propName != null) {
                        String propValue = properties.getPropertyValue(propName);
                        if (propValue != null) {
                            handler.handleProperty(path, new SVNPropertyData(propName, propValue));
                        }
                    } else {
                        Map allProps = properties.asMap();
                        for(Iterator names = allProps.keySet().iterator(); names.hasNext();) {
                            String name = (String) names.next();
                            String val = (String) allProps.get(name);
                            handler.handleProperty(area.getFile(entry.getName()), new SVNPropertyData(name, val));
                        }
                    }
                }
            }
        } finally {
            wcAccess.close();
        }
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
        SVNWCAccess wcAccess = createWCAccess();
        path = new File(SVNPathUtil.validateFilePath(path.getAbsolutePath())).getAbsoluteFile();
        try {
            if (!force) {
                SVNWCManager.canDelete(path, false, getOptions());
            }
            SVNAdminArea root = wcAccess.open(path.getParentFile(), true, 0); 
            if (!dryRun) {
                SVNWCManager.delete(wcAccess, root, path, deleteFiles);
            }
        } finally {
            wcAccess.close();
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
     * <p>
     * Calling this method is equivalent to 
     * <code>doAdd(path, force, mkdir, climbUnversionedParents, recursive, false)</code>. 
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
        doAdd(path, force, mkdir, climbUnversionedParents, recursive, false);
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
     * @param  includeIgnored              controls whether ignored items must be also added
     * @throws SVNException                if one of the following is true:
     *                                     <ul>
     *                                     <li><code>path</code> doesn't belong
     *                                     to a Working Copy 
     *                                     <li><code>path</code> doesn't exist and
     *                                     <code>mkdir</code> is <span class="javakeyword">false</span>
     *                                     <li><code>path</code> is the root directory of the Working Copy
     *                                     </ul>
     * @since                              1.1
     */
    public void doAdd(File path, boolean force, boolean mkdir, boolean climbUnversionedParents, boolean recursive, boolean includeIgnored) throws SVNException {
        path = new File(SVNPathUtil.validateFilePath(path.getAbsolutePath()));
        if (!mkdir && climbUnversionedParents) {
            // check if parent is versioned. if not, add it.
            SVNWCAccess wcAccess = createWCAccess();
            try {
                wcAccess.open(path.getParentFile(), false, 0);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                    doAdd(path.getParentFile(), false, false, climbUnversionedParents, false);
                } else {
                    throw e;
                }
            } finally {
                wcAccess.close();
            }
        }
        if (force && mkdir && SVNFileType.getType(path) == SVNFileType.DIRECTORY) {
            // directory is already there.
            doAdd(path, force, false, true, false, true);
            return;
        } else if (mkdir) {
            // attempt to create dir
            File parent = path;
            File firstCreated = path;
            while(parent != null && SVNFileType.getType(parent) == SVNFileType.NONE) {
                firstCreated = parent;
                parent = parent.getParentFile();
            }            
            boolean created = path.mkdirs();
            if (!created) {
                // delete created dirs.
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot create new directory ''{0}''", path);
                while(parent == null ? path != null : !path.equals(parent)) {
                    SVNFileUtil.deleteAll(path, true);
                    path = path.getParentFile();
                }
                SVNErrorManager.error(err);
            }
            try {
                doAdd(firstCreated, false, false, climbUnversionedParents, true, true);
            } catch (SVNException e) {
                SVNFileUtil.deleteAll(firstCreated, true);
                throw e;
            }
            return;
        }
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminArea dir = null;
            if (path.isDirectory()) {
                dir = wcAccess.open(SVNWCUtil.isVersionedDirectory(path.getParentFile()) ? path.getParentFile() : path, true, 0);
            } else {
                dir = wcAccess.open(path.getParentFile(), true, 0);
            }
            SVNFileType fileType = SVNFileType.getType(path);
            if (fileType == SVNFileType.DIRECTORY && recursive) {
                addDirectory(path, dir, force, includeIgnored);
            } else if (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK) {
                addFile(path, fileType, dir);
            } else {
                SVNWCManager.add(path, dir, null, SVNRevision.UNDEFINED);
            }
        } catch (SVNException e) {
            if (!(force && e.getErrorMessage().getErrorCode() == SVNErrorCode.ENTRY_EXISTS)) {
                throw e;
            }
        } finally {        
            wcAccess.close();
        }
    }
    
    private void addDirectory(File path, SVNAdminArea parentDir, boolean force, boolean noIgnore) throws SVNException {
        checkCancelled();
        try {
            SVNWCManager.add(path, parentDir, null, SVNRevision.UNDEFINED);
        } catch (SVNException e) {
            if (!(force && e.getErrorMessage().getErrorCode() == SVNErrorCode.ENTRY_EXISTS)) {
                throw e;
            }
        }
        SVNWCAccess access = parentDir.getWCAccess();
        SVNAdminArea dir = access.retrieve(path);
        Collection ignores = Collections.EMPTY_SET;
        if (!noIgnore) {
            ignores = SVNStatusEditor.getIgnorePatterns(dir, SVNStatusEditor.getGlobalIgnores(getOptions()));
        }
        File[] children = dir.getRoot().listFiles();
        for(int i = 0; children != null && i < children.length; i++) {
            checkCancelled();
            if (SVNFileUtil.getAdminDirectoryName().equals(children[i].getName())) {
                continue;
            }
            if (!noIgnore && SVNStatusEditor.isIgnored(ignores, children[i].getName())) {
                continue;
            }
            SVNFileType childType = SVNFileType.getType(children[i]);
            if (childType == SVNFileType.DIRECTORY) {
                addDirectory(children[i], dir, force, noIgnore);
            } else if (childType != SVNFileType.UNKNOWN) {
                try {
                    addFile(children[i], childType, dir);
                } catch (SVNException e) {
                    if (force && e.getErrorMessage().getErrorCode() == SVNErrorCode.ENTRY_EXISTS) {
                        continue;
                    }
                    throw e;
                }
            }
        }
        
    }
    
    private void addFile(File path, SVNFileType type, SVNAdminArea dir) throws SVNException {
        ISVNEventHandler handler = dir.getWCAccess().getEventHandler(); 
        dir.getWCAccess().setEventHandler(null);
        SVNWCManager.add(path, dir, null, SVNRevision.UNDEFINED);
        dir.getWCAccess().setEventHandler(handler);

        String mimeType = null;
        if (type == SVNFileType.SYMLINK) {
            SVNPropertiesManager.setProperty(dir.getWCAccess(), path, SVNProperty.SPECIAL, 
                    SVNProperty.getValueOfBooleanProperty(SVNProperty.SPECIAL), false);
        } else {
            Map props = SVNPropertiesManager.computeAutoProperties(getOptions(), path);
            for (Iterator names = props.keySet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                String propValue = (String) props.get(propName);
                SVNPropertiesManager.setProperty(dir.getWCAccess(), path, propName, propValue, false);
            }
            mimeType = (String) props.get(SVNProperty.MIME_TYPE);
        }
        SVNEvent event = SVNEventFactory.createAddedEvent(dir, path.getName(), SVNNodeKind.FILE, mimeType);
        dispatchEvent(event);
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
        path = new File(SVNPathUtil.validateFilePath(path.getAbsolutePath()));
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminAreaInfo info = wcAccess.openAnchor(path, true, recursive ? SVNWCAccess.INFINITE_DEPTH : 0);
            SVNEntry entry = wcAccess.getEntry(path, false);
            if (entry != null && entry.isDirectory() && entry.isScheduledForAddition()) {
                if (!recursive) {
                    getDebugLog().info("Forcing revert on path '" + path + "' to recurse");
                    recursive = true;
                    wcAccess.close();
                    info = wcAccess.openAnchor(path, true, SVNWCAccess.INFINITE_DEPTH);
                }
            }
            
            boolean useCommitTimes = getOptions().isUseCommitTimes();
            doRevert(path, info.getAnchor(), recursive, useCommitTimes);            
        } catch (SVNException e) {
            SVNErrorCode code = e.getErrorMessage().getErrorCode();
            if (code == SVNErrorCode.ENTRY_NOT_FOUND || code == SVNErrorCode.UNVERSIONED_RESOURCE) {
                SVNEvent event = SVNEventFactory.createSkipEvent(path.getParentFile(), path, SVNEventAction.SKIP, SVNEventAction.REVERT, null);
                dispatchEvent(event);
            }            
            throw e;
        } finally {
            wcAccess.close();
        }
    }
    
    private void doRevert(File path, SVNAdminArea parent, boolean recursive, boolean useCommitTimes) throws SVNException {
        checkCancelled();
        SVNAdminArea dir = parent.getWCAccess().probeRetrieve(path);
        SVNEntry entry = dir.getWCAccess().getEntry(path, false);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "Cannot revert: ''{0}'' is not under version control", path);
            SVNErrorManager.error(err);
        }
        if (entry.getKind() == SVNNodeKind.DIR) {
            SVNFileType fileType = SVNFileType.getType(path);
            if (fileType != SVNFileType.DIRECTORY && !entry.isScheduledForAddition()) {
                SVNEvent event = SVNEventFactory.createNotRevertedEvent(dir, entry);
                dispatchEvent(event);
                return;
            }
        }
        if (entry.getKind() != SVNNodeKind.DIR && entry.getKind() != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot revert ''{0}'': unsupported entry node kind", path);
            SVNErrorManager.error(err);
        }
        SVNFileType fileType = SVNFileType.getType(path);
        if (fileType == SVNFileType.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot revert ''{0}'': unsupported node kind in working copy", path);
            SVNErrorManager.error(err);
        }
        boolean reverted = false;
        if (entry.isScheduledForAddition()) {
            boolean wasDeleted = false;
            if (entry.getKind() == SVNNodeKind.FILE) {
                wasDeleted = entry.isDeleted();
                parent.removeFromRevisionControl(path.getName(), false, false);
            } else if (entry.getKind() == SVNNodeKind.DIR) {
                SVNEntry entryInParent = parent.getEntry(path.getName(), true);
                if (entryInParent != null) {
                    wasDeleted = entryInParent.isDeleted();
                }
                if (fileType == SVNFileType.NONE) {
                    parent.deleteEntry(path.getName());
                    parent.saveEntries(false);
                } else {
                    dir.removeFromRevisionControl("", false, false);
                }
            }
            reverted = true;
            recursive = false;
            if (wasDeleted) {
                SVNEntry entryInParent = parent.getEntry(path.getName(), true);
                if (entryInParent == null) {
                    entryInParent = parent.addEntry(path.getName());
                }
                entryInParent.setKind(entry.getKind());
                entryInParent.setDeleted(true);
                parent.saveEntries(false);
            }
        } else if (entry.getSchedule() == null || entry.isScheduledForDeletion() || entry.isScheduledForReplacement()) {
            if (entry.getKind() == SVNNodeKind.FILE) {
                reverted = revert(parent, entry.getName(), entry, useCommitTimes);
            } else if (entry.getKind() == SVNNodeKind.DIR) {
                reverted = revert(dir, dir.getThisDirName(), entry, useCommitTimes);
                if (entry.isScheduledForReplacement()) {
                    recursive = true;
                }
            }
        }
        if (reverted) {
            SVNEvent event = SVNEventFactory.createRevertedEvent(dir, entry);
            dispatchEvent(event);
        }
        if (recursive && entry.getKind() == SVNNodeKind.DIR) {
            for(Iterator entries = dir.entries(false); entries.hasNext();) {
                SVNEntry childEntry = (SVNEntry) entries.next();
                if (dir.getThisDirName().equals(childEntry.getName())) {
                    continue;
                }
                File childPath = new File(path, childEntry.getName());
                doRevert(childPath, dir, true, useCommitTimes);
            }
        }
    }
    
    private boolean revert(SVNAdminArea dir, String name, SVNEntry entry, boolean useCommitTime) throws SVNException {
        SVNLog log = dir.getLog();
        boolean reverted = false;
        SVNVersionedProperties baseProperties = null;
        Map command = new HashMap();
        
        if (entry.isScheduledForReplacement()) {
            String propRevertPath = SVNAdminUtil.getPropRevertPath(name, entry.getKind(), false);
            if (dir.getFile(propRevertPath).isFile()) {
                baseProperties = dir.getRevertProperties(name);
                command.put(SVNLog.NAME_ATTR, propRevertPath);
                log.addCommand(SVNLog.DELETE, command, false);
                command.clear();
                reverted = true;
            }
        }
        boolean reinstallWorkingFile = false;
        if (baseProperties == null) {
            if (dir.hasPropModifications(name)) {
                baseProperties = dir.getBaseProperties(name);
                SVNVersionedProperties propDiff = dir.getProperties(name).compareTo(baseProperties);
                Collection propNames = propDiff.getPropertyNames(null);
                reinstallWorkingFile = propNames.contains(SVNProperty.EXECUTABLE) ||
                                propNames.contains(SVNProperty.KEYWORDS) ||
                                propNames.contains(SVNProperty.EOL_STYLE) ||
                                propNames.contains(SVNProperty.SPECIAL) ||
                                propNames.contains(SVNProperty.NEEDS_LOCK);
            }
        }
        if (baseProperties != null) {
            // save base props both to base and working. 
            Map newProperties = baseProperties.asMap();
            SVNVersionedProperties originalBaseProperties = dir.getBaseProperties(name);
            SVNVersionedProperties workProperties = dir.getProperties(name);
            if (entry.isScheduledForReplacement()) {
                originalBaseProperties.removeAll();
            }
            workProperties.removeAll();
            for(Iterator names = newProperties.keySet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                if (entry.isScheduledForReplacement()) {
                    originalBaseProperties.setPropertyValue(propName, (String) newProperties.get(propName));
                }
                workProperties.setPropertyValue(propName, (String) newProperties.get(propName));
            }
            dir.saveVersionedProperties(log, false);
            reverted = true;
        } 
        Map newEntryProperties = new HashMap();
        if (entry.isScheduledForReplacement() && entry.isCopied()) {
            newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.COPIED), null);
            reverted = true;
        }
        if (entry.getKind() == SVNNodeKind.FILE) {
            if (!reinstallWorkingFile) {
                SVNFileType fileType = SVNFileType.getType(dir.getFile(name));
                if (fileType == SVNFileType.NONE) {
                    reinstallWorkingFile = true;
                }
            }
            String basePath = SVNAdminUtil.getTextBasePath(name, false);
            if (!dir.getFile(basePath).isFile()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Error restoring text for ''{0}''", dir.getFile(name));
                SVNErrorManager.error(err);                
            }
            File revertFile = dir.getFile(SVNAdminUtil.getTextRevertPath(name, false));
            if (revertFile.isFile()) {
                command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getTextRevertPath(name, false));
                command.put(SVNLog.DEST_ATTR, SVNAdminUtil.getTextBasePath(name, false));
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();
                reinstallWorkingFile = true;
            }
            if (!reinstallWorkingFile) {
                reinstallWorkingFile = dir.hasTextModifications(name, false, false, false);
            }
            if (reinstallWorkingFile) {
                command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getTextBasePath(name, false));
                command.put(SVNLog.DEST_ATTR, name);
                log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                command.clear();
                if (useCommitTime && entry.getCommittedDate() != null) {
                    command.put(SVNLog.NAME_ATTR, name);
                    command.put(SVNLog.TIMESTAMP_ATTR, entry.getCommittedDate());
                    log.addCommand(SVNLog.SET_TIMESTAMP, command, false);
                    command.clear();                    
                }
                command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNProperty.shortPropertyName(SVNProperty.TEXT_TIME), SVNLog.WC_TIMESTAMP);
                log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
                command.clear();                    
            }
            reverted |= reinstallWorkingFile;
        }
        if (entry.getConflictNew() != null) {
            command.put(SVNLog.NAME_ATTR, entry.getConflictNew());
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
            newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_NEW), null);
        }
        if (entry.getConflictOld() != null) {
            command.put(SVNLog.NAME_ATTR, entry.getConflictOld());
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
            newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_OLD), null);
        }
        if (entry.getConflictWorking() != null) {
            command.put(SVNLog.NAME_ATTR, entry.getConflictWorking());
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
            newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_WRK), null);
        }
        if (entry.getPropRejectFile() != null) {
            command.put(SVNLog.NAME_ATTR, entry.getPropRejectFile());
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
            newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.PROP_REJECT_FILE), null);
        }
        if (entry.getSchedule() != null) {
            newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), null);
            reverted = true;
        }
        if (!newEntryProperties.isEmpty()) {
            newEntryProperties.put(SVNLog.NAME_ATTR, name);
            log.addCommand(SVNLog.MODIFY_ENTRY, newEntryProperties, false);
        }
        log.save();
        dir.runLogs();
        return reverted;        
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
        SVNWCAccess wcAccess = createWCAccess();
        try {
            wcAccess.probeOpen(path, true, recursive ? SVNWCAccess.INFINITE_DEPTH : 0);
            if (!recursive) {
                SVNEntry entry = wcAccess.getEntry(path, false);
                if (entry == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", path);
                    SVNErrorManager.error(err);
                }
                resolveEntry(wcAccess, path, entry);
            } else {
                resolveAll(wcAccess, path);
            }
        } finally {
            wcAccess.close();
        }
    }
    
    private void resolveEntry(SVNWCAccess wcAccess, File path, SVNEntry entry) throws SVNException {
        if (entry.getKind() == SVNNodeKind.DIR && !"".equals(entry.getName())) {
            return;
        }
        File dirPath = path;
        if (entry.getKind() == SVNNodeKind.FILE) {
            dirPath = path.getParentFile();
        }
        SVNAdminArea dir = wcAccess.retrieve(dirPath);
        if (dir.markResolved(entry.getName(), true, true)) {
            SVNEvent event = SVNEventFactory.createRestoredEvent(null, dir, entry);
            dispatchEvent(event);
        }
    }

    private void resolveAll(SVNWCAccess access, File path) throws SVNException {
        checkCancelled();
        SVNEntry entry = access.getEntry(path, false);
        resolveEntry(access, path, entry);
        if (entry.isDirectory()) {
            SVNAdminArea dir = access.retrieve(path);
            for (Iterator ents = dir.entries(false); ents.hasNext();) {
                SVNEntry childEntry = (SVNEntry) ents.next();
                if ("".equals(childEntry.getName())) {
                    continue;
                }
                resolveAll(access, dir.getFile(childEntry.getName()));
            }
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
        Map pathsRevisionsMap = new HashMap();
        final SVNWCAccess wcAccess = createWCAccess();
        try {
            final SVNURL topURL = collectLockInfo(wcAccess, paths, entriesMap, pathsRevisionsMap, true, stealLock);
            SVNRepository repository = createRepository(topURL, true);
            final SVNURL rootURL = repository.getRepositoryRoot(true);
    
            repository.lock(pathsRevisionsMap, lockMessage, stealLock, new ISVNLockHandler() {
                public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                    SVNURL fullURL = rootURL.appendPath(path, false);
                    LockInfo lockInfo = (LockInfo) entriesMap.get(fullURL);
                    SVNAdminArea dir = wcAccess.probeRetrieve(lockInfo.myFile);
                    if (error == null) {
                        SVNEntry entry = wcAccess.getEntry(lockInfo.myFile, false);
                        if (entry == null) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", lockInfo.myFile);
                            SVNErrorManager.error(err);
                        }
                        entry.setLockToken(lock.getID());
                        entry.setLockComment(lock.getComment());
                        entry.setLockOwner(lock.getOwner());
                        entry.setLockCreationDate(SVNTimeUtil.formatDate(lock.getCreationDate()));
                        // get properties and values.
                        SVNVersionedProperties props = dir.getProperties(entry.getName());
                        
                        if (props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null) {
                            SVNFileUtil.setReadonly(dir.getFile(entry.getName()), false);
                        }
                        SVNFileUtil.setExecutable(dir.getFile(entry.getName()), props.getPropertyValue(SVNProperty.EXECUTABLE) != null);
                        dir.saveEntries(false);
                        handleEvent(SVNEventFactory.createLockEvent(dir, entry.getName(), SVNEventAction.LOCKED, lock, null),
                                ISVNEventHandler.UNKNOWN);
                    } else {
                        handleEvent(SVNEventFactory.createLockEvent(dir, lockInfo.myFile.getName(), SVNEventAction.LOCK_FAILED, lock, error), 
                                ISVNEventHandler.UNKNOWN);
                    }
                }
                public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) {
                }
            });
        } finally {
            wcAccess.close();
        }
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
                    handleEvent(SVNEventFactory.createLockEvent(null, path, SVNEventAction.LOCK_FAILED, lock, error), ISVNEventHandler.UNKNOWN);
                } else {
                    handleEvent(SVNEventFactory.createLockEvent(null, path, SVNEventAction.LOCKED, lock, null), ISVNEventHandler.UNKNOWN);
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
        Map pathsTokensMap = new HashMap();
        final SVNWCAccess wcAccess = createWCAccess();
        try {
            final SVNURL topURL = collectLockInfo(wcAccess, paths, entriesMap, pathsTokensMap, false, breakLock);
            checkCancelled();
            SVNRepository repository = createRepository(topURL, true);
            final SVNURL rootURL = repository.getRepositoryRoot(true);
            repository.unlock(pathsTokensMap, breakLock, new ISVNLockHandler() {
                public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                }
                public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                    SVNURL fullURL = rootURL.appendPath(path, false);
                    LockInfo lockInfo = (LockInfo) entriesMap.get(fullURL);
                    SVNEventAction action = null;
                    SVNAdminArea dir = wcAccess.probeRetrieve(lockInfo.myFile);
                    if (error == null || (error != null && error.getErrorCode() != SVNErrorCode.FS_LOCK_OWNER_MISMATCH)) {
                        SVNEntry entry = wcAccess.getEntry(lockInfo.myFile, false);
                        if (entry == null) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", lockInfo.myFile);
                            SVNErrorManager.error(err);
                        }
                        entry.setLockToken(null);
                        entry.setLockComment(null);
                        entry.setLockOwner(null);
                        entry.setLockCreationDate(null);
                        
                        SVNVersionedProperties props = dir.getProperties(entry.getName());
                        
                        if (props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null) {
                            SVNFileUtil.setReadonly(dir.getFile(entry.getName()), true);
                        }
                        dir.saveEntries(false);
                        action = SVNEventAction.UNLOCKED;
                    } 
                    if (error != null) {
                        action = SVNEventAction.UNLOCK_FAILED;
                    }
                    if (action != null) {
                      handleEvent(SVNEventFactory.createLockEvent(dir, lockInfo.myFile.getName(), action, lock, error), ISVNEventHandler.UNKNOWN);
                    }
                }
            });
        } finally {
            wcAccess.close();
        }
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
                    handleEvent(SVNEventFactory.createLockEvent(null, path, SVNEventAction.UNLOCK_FAILED, null, error), ISVNEventHandler.UNKNOWN);
                } else {
                    handleEvent(SVNEventFactory.createLockEvent(null, path, SVNEventAction.UNLOCKED, null, null), ISVNEventHandler.UNKNOWN);
                }
            }
        });
    }

    private SVNURL collectLockInfo(SVNWCAccess wcAccess, File[] files, Map lockInfo, Map lockPaths, boolean lock, boolean stealLock) throws SVNException {
        String[] paths = new String[files.length];
        for(int i = 0; i < files.length; i++) {
            paths[i] = files[i].getAbsolutePath();
            paths[i] = paths[i].replace(File.separatorChar, '/');
        }
        Collection condencedPaths = new ArrayList();
        String commonParentPath = SVNPathUtil.condencePaths(paths, condencedPaths, false);
        if (condencedPaths.isEmpty()) {
            condencedPaths.add(SVNPathUtil.tail(commonParentPath));
            commonParentPath = SVNPathUtil.removeTail(commonParentPath);
        }
        if (commonParentPath == null || "".equals(commonParentPath)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "No common parent found, unable to operate on dijoint arguments");
            SVNErrorManager.error(err);
        }
        paths = (String[]) condencedPaths.toArray(new String[condencedPaths.size()]);
        int depth = 0;
        for (int i = 0; i < paths.length; i++) {
            int segments = SVNPathUtil.getSegmentsCount(paths[i]);
            if (depth < segments) {
                depth = segments;
            }
        }
        wcAccess.probeOpen(new File(commonParentPath), true, depth);
        for (int i = 0; i < paths.length; i++) {
            File file = new File(commonParentPath, paths[i]);
            SVNEntry entry = wcAccess.getEntry(file, false);
            if (entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", file.getName());
                SVNErrorManager.error(err);
            }
            if (entry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", file);
                SVNErrorManager.error(err);
            }
            if (lock) {
                SVNRevision revision = stealLock ? SVNRevision.UNDEFINED : SVNRevision.create(entry.getRevision());
                lockInfo.put(entry.getSVNURL(), new LockInfo(file, revision));
            } else {
                if (!stealLock && entry.getLockToken() == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MISSING_LOCK_TOKEN, "''{0}'' is not locked in this working copy", file);
                    SVNErrorManager.error(err);
                }
                lockInfo.put(entry.getSVNURL(), new LockInfo(file, entry.getLockToken()));
            }
        }
        checkCancelled();
        SVNURL[] urls = (SVNURL[]) lockInfo.keySet().toArray(new SVNURL[lockInfo.size()]);
        Collection urlPaths = new HashSet();
        final SVNURL topURL = SVNURLUtil.condenceURLs(urls, urlPaths, false);
        if (urlPaths.isEmpty()) {
            urlPaths.add("");
        }
        if (topURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Unable to lock/unlock across multiple repositories");
            SVNErrorManager.error(err);
        }
        // prepare Map for SVNRepository (decoded path : revision/lock token).
        for(Iterator encodedPaths = urlPaths. iterator(); encodedPaths.hasNext();) {
            String encodedPath = (String) encodedPaths.next();
            // get LockInfo for it.
            SVNURL fullURL = topURL.appendPath(encodedPath, true);
            LockInfo info = (LockInfo) lockInfo.get(fullURL);
            encodedPath = SVNEncodingUtil.uriDecode(encodedPath);
            if (lock) {
                if (info.myRevision == SVNRevision.UNDEFINED) {
                    lockPaths.put(encodedPath, null);
                } else {
                    lockPaths.put(encodedPath, new Long(info.myRevision.getNumber()));
                }
            } else {
                lockPaths.put(encodedPath, info.myToken);
            }
        }
        return topURL;
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
            SVNWCAccess wcAccess = createWCAccess();
            SVNRevision wcRevision = null;
            SVNURL url = null;
            try {
                wcAccess.probeOpen(path, false, 0);
                SVNEntry entry = wcAccess.getEntry(path, false);
                if (entry == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", path);
                    SVNErrorManager.error(err);
                }
                url = entry.getSVNURL();
                if (url == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path);
                    SVNErrorManager.error(err);
                }
                wcRevision = SVNRevision.create(entry.getRevision());
            } finally {
                wcAccess.close();
            }
            doInfo(url, pegRevision == null || !pegRevision.isValid() || pegRevision.isLocal() ? wcRevision : pegRevision, revision, recursive, handler);
            return;
        }
        SVNWCAccess wcAccess = createWCAccess();
        try {
            wcAccess.probeOpen(path, false, recursive ? SVNWCAccess.INFINITE_DEPTH : 0);
            SVNEntry entry = wcAccess.getEntry(path, false);
            if (entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "Cannot read entry for ''{0}''", path);
                SVNErrorManager.error(err);
            }
            if (entry.isFile()) {
                reportEntry(path, entry, handler);
            } else if (entry.isDirectory()) {
                if (recursive) {
                    reportAllEntries(wcAccess, path, handler);
                } else {
                    reportEntry(path, entry, handler);
                }
            }
        } finally {
            wcAccess.close();
        }
    }
    
    private void reportEntry(File path, SVNEntry entry, ISVNInfoHandler handler) throws SVNException {
        if (entry.isDirectory() && !"".equals(entry.getName())) {
            return;
        }
        handler.handleInfo(SVNInfo.createInfo(path, entry));
    }

    private void reportAllEntries(SVNWCAccess wcAccess, File path, ISVNInfoHandler handler) throws SVNException {
        SVNEntry entry = wcAccess.getEntry(path, false);
        reportEntry(path, entry, handler);
        if (entry.isDirectory()) {
            SVNAdminArea dir = wcAccess.retrieve(path);
            for(Iterator entries = dir.entries(false); entries.hasNext();) {
                SVNEntry childEntry = (SVNEntry) entries.next();
                if (dir.getThisDirName().equals(childEntry.getName())) {
                    continue;
                }
                File childPath = dir.getFile(childEntry.getName());
                if (childEntry.isDirectory()) {
                    reportAllEntries(wcAccess, childPath, handler);
                }
                reportEntry(childPath, childEntry, handler);
            }
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
    public void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException {
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
                SVNRepositoryLocation[] locations = getLocations(url, null, null, revision, SVNRevision.HEAD, SVNRevision.UNDEFINED);
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
        SVNWCAccess wcAccess = createWCAccess();
        try {
            wcAccess.probeOpen(path, false, 0);
        } catch (SVNException e) {            
            SVNFileType pathType = SVNFileType.getType(path);
            if (pathType == SVNFileType.DIRECTORY) {
                return "exported";
            } 
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "''{0}'' is not versioned and not exported", path);
            SVNErrorManager.error(err);
        } finally {
            wcAccess.close();
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
    
    private void collectInfo(SVNRepository repos, SVNDirEntry entry,
            SVNRevision rev, String path, SVNURL root, String uuid, SVNURL url,
            Map locks, boolean recursive, ISVNInfoHandler handler) throws SVNException {
        checkCancelled();
        String displayPath = repos.getFullPath(path);
        displayPath = displayPath.substring(repos.getLocation().getPath().length());
        if ("".equals(displayPath) || "/".equals(displayPath)) {
            displayPath = path;
        }
        handler.handleInfo(SVNInfo.createInfo(displayPath, root, uuid, url, rev, entry, (SVNLock) locks.get(path)));
        if (entry.getKind() == SVNNodeKind.DIR && recursive) {
            Collection children = repos.getDir(path, rev.getNumber(), null, new ArrayList());
            for (Iterator ents = children.iterator(); ents.hasNext();) {
                SVNDirEntry child = (SVNDirEntry) ents.next();
                SVNURL childURL = url.appendPath(child.getName(), false);
                collectInfo(repos, child, rev, SVNPathUtil.append(path, child.getName()), root, uuid, childURL, locks, recursive, handler);
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

    private void doGetLocalProperty(SVNAdminArea area, String propName, boolean base, ISVNPropertyHandler handler) throws SVNException {
        checkCancelled();
        for(Iterator entries = area.entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (entry.getKind() == SVNNodeKind.DIR && !"".equals(entry.getName())) {
                continue;
            }
            if ((base && entry.isScheduledForAddition()) || (!base && entry.isScheduledForDeletion())) {
                continue;
            }
            SVNVersionedProperties properties = base ? area.getBaseProperties(entry.getName()) : area.getProperties(entry.getName());
            if (propName != null) {
                String propVal = properties.getPropertyValue(propName);
                if (propVal != null) {
                    handler.handleProperty(area.getFile(entry.getName()), new SVNPropertyData(propName, propVal));
                }
            } else {
                Map allProps = properties.asMap();
                for(Iterator names = allProps.keySet().iterator(); names.hasNext();) {
                    String name = (String) names.next();
                    String val = (String) allProps.get(name);
                    handler.handleProperty(area.getFile(entry.getName()), new SVNPropertyData(name, val));
                }
            }
        }
        
        for(Iterator entries = area.entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (entry.getKind() == SVNNodeKind.DIR && !"".equals(entry.getName())) {
                SVNAdminArea childArea = null;
                try {
                    childArea = area.getWCAccess().retrieve(area.getFile(entry.getName()));
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                        continue;
                    }
                    throw e;
                }
                if (childArea != null) {
                    doGetLocalProperty(childArea, propName, base, handler);
                }
            }
        }
    }

    private void doSetLocalProperty(SVNAdminArea anchor, String name, String propName, String propValue, boolean force,
            boolean recursive, boolean cancel, ISVNPropertyHandler handler) throws SVNException {
        if (cancel) {
            checkCancelled();
        }
        if (!"".equals(name)) {
            SVNEntry entry = anchor.getEntry(name, true);
            if (entry == null || (recursive && entry.isDeleted())) {
                return;
            }
            if (entry.getKind() == SVNNodeKind.DIR) {
                File path = anchor.getFile(name);
                SVNAdminArea dir = anchor.getWCAccess().retrieve(path);
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
                SVNVersionedProperties props = anchor.getProperties(name);
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
            SVNLog log = anchor.getLog();
            anchor.saveVersionedProperties(log, true);
            anchor.saveEntries(false);
            log.save();
            anchor.runLogs();
            return;
        }
        SVNVersionedProperties props = anchor.getProperties(name);
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
            SVNLog log = anchor.getLog();
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
            SVNEntry entry = (SVNEntry) ents.next();
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
    
    private void doGetLocalFileContents(File path, OutputStream dst, SVNRevision revision, boolean expandKeywords) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        InputStream input = null;
        boolean hasMods = false;
        SVNVersionedProperties properties = null;

        try {
            SVNAdminArea area = wcAccess.open(path.getParentFile(), false, 0);
            SVNEntry entry = wcAccess.getEntry(path, false);
            if (entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control or doesn''t exist", path, SVNErrorMessage.TYPE_WARNING);
                SVNErrorManager.error(err);
            } else if (entry.getKind() != SVNNodeKind.FILE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' refers to a directory", path, SVNErrorMessage.TYPE_WARNING);
                SVNErrorManager.error(err);
            }
            String name = path.getName();
            if (revision != SVNRevision.WORKING) {
                // get base version and base props.
                input = area.getBaseFileForReading(name, false);
                properties = area.getBaseProperties(name);
            } else {
                // get working version and working props.
                input = SVNFileUtil.openFileForReading(area.getFile(path.getName()));
                hasMods = area.hasPropModifications(name) || area.hasTextModifications(name, true);
                properties = area.getProperties(name);
            }
            String eolStyle = properties.getPropertyValue(SVNProperty.EOL_STYLE);
            String keywords = properties.getPropertyValue(SVNProperty.KEYWORDS);
            boolean special = properties.getPropertyValue(SVNProperty.SPECIAL) != null;
            byte[] eols = null;
            Map keywordsMap = null;
            String time = null;

            if (eolStyle != null) {
                eols = SVNTranslator.getEOL(eolStyle);
            }
            if (hasMods && !special) {
                time = SVNTimeUtil.formatDate(new Date(path.lastModified()));
            } else {
                time = entry.getCommittedDate();
            }
            if (keywords != null) {
                String url = entry.getURL();
                String author = hasMods ? "(local)" : entry.getAuthor();
                String rev = hasMods ? entry.getCommittedRevision() + "M" : entry.getCommittedRevision() + ""; 
                keywordsMap = SVNTranslator.computeKeywords(keywords, expandKeywords ? url : null, author, time, rev, getOptions());
            }
            OutputStream translatingStream = eols != null || keywordsMap != null ? new SVNTranslatorOutputStream(dst, eols, false, keywordsMap, expandKeywords) : dst;
            try {
                SVNTranslator.copy(input, new SVNCancellableOutputStream(translatingStream, getEventDispatcher()));
                if (translatingStream != dst) {
                    SVNFileUtil.closeFile(translatingStream);
                }
                dst.flush();
            } catch (IOException e) {
                if (e instanceof SVNCancellableOutputStream.IOCancelException) {
                    SVNErrorManager.cancel(e.getMessage());
                }
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()));
            }
        } finally {
            SVNFileUtil.closeFile(input);
            wcAccess.close();
        }
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
