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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeInheritance;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.AbstractDiffCallback;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNDiffCallback;
import org.tmatesoft.svn.core.internal.wc.SVNDiffEditor;
import org.tmatesoft.svn.core.internal.wc.SVNDiffStatusEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNMergeCallback;
import org.tmatesoft.svn.core.internal.wc.SVNMergeInfoManager;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.internal.wc.SVNRemoteDiffEditor;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNEntryHandler;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * The <b>SVNDiffClient</b> class provides methods allowing to get differences
 * between versioned items ('diff' operation) as well as ones intended for 
 * merging file contents.
 * 
 * <p>
 * Here's a list of the <b>SVNDiffClient</b>'s methods 
 * matched against corresponing commands of the SVN command line 
 * client:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNKit</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doDiff()</td><td>'svn diff'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doDiffStatus()</td><td>'svn diff --summarize'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doMerge()</td><td>'svn merge'</td>
 * </tr>
 * </table>
 * 
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNDiffClient extends SVNBasicClient {

    private ISVNDiffGenerator myDiffGenerator;
    private SVNDiffOptions myDiffOptions;
    private ISVNConflictHandler myConflictHandler;

    /**
     * Constructs and initializes an <b>SVNDiffClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNDiffClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNDiffClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the 
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     * 
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver     
     */
    public SVNDiffClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    public SVNDiffClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }
    
    /**
     * Sets the specified diff driver for this object to use for
     * generating and writing file differences to an otput stream.
     * 
     * <p>
     * If no specific diff driver was set in this way, a default one
     * will be used (see {@link DefaultSVNDiffGenerator}). 
     * 
     * @param diffGenerator a diff driver
     * @see   #getDiffGenerator()
     */
    public void setDiffGenerator(ISVNDiffGenerator diffGenerator) {
        myDiffGenerator = diffGenerator;
    }
    
    /**
     * Returns the diff driver being in use.
     *  
     * <p>
     * If no specific diff driver was previously provided, a default one
     * will be returned (see {@link DefaultSVNDiffGenerator}). 
     * 
     * @return the diff driver being in use
     * @see    #setDiffGenerator(ISVNDiffGenerator)
     */
    public ISVNDiffGenerator getDiffGenerator() {
        if (myDiffGenerator == null) {
            myDiffGenerator = new DefaultSVNDiffGenerator();
        }
        return myDiffGenerator;
    }
    
    /**
     * Sets diff options for this client to use in merge operations.
     * 
     * @param diffOptions diff options object
     */
    public void setMergeOptions(SVNDiffOptions diffOptions) {
        myDiffOptions = diffOptions;
    }

    /**
     * Gets the diff options that are used in merge operations 
     * by this client. Creates a new one if none was used before.
     * 
     * @return diff options
     */
    public SVNDiffOptions getMergeOptions() {
        if (myDiffOptions == null) {
            myDiffOptions = new SVNDiffOptions();
        }
        return myDiffOptions;
    }

    public ISVNConflictHandler getConflictHandler() {
        return myConflictHandler;
    }
    
    public void setConflictHandler(ISVNConflictHandler conflictHandler) {
        myConflictHandler = conflictHandler;
    }

    /**
     * Generates the differences for the specified URL taken from the two 
     * specified revisions and writes the result to the provided output
     * stream.
     * 
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn diff -r N:M URL'</code> command.
     * 
     * @param  url            a repository location
     * @param  pegRevision    a revision in which <code>url</code> is first looked up
     * @param  rN             an old revision                          
     * @param  rM             a new revision
     * @param  recursive      <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>rN</code>, <code>rM</code> and
     *                        <code>pegRevision</code> is invalid
     *                        <li>at least one of <code>rN</code> and <code>rM</code> is
     *                        a local revision (see {@link SVNRevision#isLocal()})
     *                        <li><code>url</code> was not found in <code>rN</code>
     *                        <li><code>url</code> was not found in <code>rM</code>
     *                        </ul>
     */
    public void doDiff(SVNURL url, SVNRevision pegRevision, SVNRevision rN, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        doDiff(url, pegRevision, rN, rM, SVNDepth.fromRecurse(recursive), useAncestry, result);
    }
     
    public void doDiff(SVNURL url, SVNRevision pegRevision, SVNRevision rN, SVNRevision rM, SVNDepth depth, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        if (rN.isLocal() || rM.isLocal()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions must be non-local for " +
                "a pegged diff of an URL");            
            SVNErrorManager.error(err);
        }
        getDiffGenerator().init(url.toString(), url.toString());
        doDiffURLURL(url, null, rN, url, null, rM, pegRevision, depth, useAncestry, result);
    }
    
    /**
     * Generates the differences for the specified path taken from the two 
     * specified revisions and writes the result to the provided output
     * stream.
     * 
     * <p>
     * If <code>rM</code> is a local revision (see {@link SVNRevision#isLocal()}),
     * then the Working Copy <code>path</code> is compared with the corresponding 
     * repository file at revision <code>rN</code> (that is similar to the SVN command 
     * line client's <code>'svn diff -r N path'</code> command). 
     * 
     * <p>
     * Otherwise if both <code>rN</code> and <code>rM</code> are non-local, then 
     * the repository location of <code>path</code> is compared for these 
     * revisions (<code>'svn diff -r N:M URL'</code>).
     * 
     * @param  path           a Working Copy file path
     * @param  pegRevision    a revision in which the repository location of <code>path</code> 
     *                        is first looked up
     * @param  rN             an old revision                          
     * @param  rM             a new revision (or a local one)
     * @param  recursive      <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>rN</code>, <code>rM</code> and
     *                        <code>pegRevision</code> is invalid
     *                        <li>both <code>rN</code> and <code>rM</code> are 
     *                        local revisions
     *                        <li><code>path</code> was not found in <code>rN</code>
     *                        <li><code>path</code> was not found in <code>rM</code>
     *                        </ul>
     */
    public void doDiff(File path, SVNRevision pegRevision, SVNRevision rN, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        doDiff(path, pegRevision, rN, rM, SVNDepth.fromRecurse(recursive), useAncestry, result);
    }
    
    public void doDiff(ISVNPathList pathList, SVNRevision rN, SVNRevision rM, SVNDepth depth, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (pathList == null) {
            return;
        }
        
        for (Iterator paths = pathList.getPathsIterator(); paths.hasNext();) {
            File path = (File) paths.next();
            SVNRevision pegRevision = pathList.getPegRevision(path);
            try {
                doDiff(path, pegRevision, rN, rM, depth, useAncestry, result);
            } catch (SVNException svne) {
                dispatchEvent(new SVNEvent(svne.getErrorMessage()));
                continue;
            }
        }
    }
    
    public void doDiff(File path, SVNRevision pegRevision, SVNRevision rN, SVNRevision rM, SVNDepth depth, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        boolean rNisLocal = rN == SVNRevision.BASE || rN == SVNRevision.WORKING;
        boolean rMisLocal = rM == SVNRevision.BASE || rM == SVNRevision.WORKING;
        if (rNisLocal && rMisLocal) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "At least one revision must be non-local for " +
                    "a pegged diff");            
            SVNErrorManager.error(err);
        }
        path = new File(SVNPathUtil.validateFilePath(path.getAbsolutePath())).getAbsoluteFile();
        getDiffGenerator().init(path.getAbsolutePath(), path.getAbsolutePath());
        if (!(rM == SVNRevision.BASE || rM == SVNRevision.WORKING || rM == SVNRevision.COMMITTED)) {
            if ((rN == SVNRevision.BASE || rN == SVNRevision.WORKING || rN == SVNRevision.COMMITTED)) {
                doDiffURLWC(path, rM, pegRevision, path, rN, true, depth, useAncestry, result);
            } else {
                doDiffURLURL(null, path, rN, null, path, rM, pegRevision, depth, useAncestry, result);
            }
        } else {
            // head, prev,date,number will go here.
            doDiffURLWC(path, rN, pegRevision, path, rM, false, depth, useAncestry, result);
        }
    }
    
    /**
     * Generates the differences for the specified URLs taken from the two 
     * specified revisions and writes the result to the provided output
     * stream.
     * 
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn diff -r N:M URL1 URL2'</code> command.
     * 
     * @param  url1           the first URL to be compared
     * @param  rN             a revision of <code>url1</code>
     * @param  url2           the second URL to be compared
     * @param  rM             a revision of <code>url2</code>
     * @param  recursive      <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>rN</code> and <code>rM</code> is
     *                        invalid
     *                        <li><code>url1</code> was not found in <code>rN</code>
     *                        <li><code>url2</code> was not found in <code>rM</code>
     *                        </ul>
     */
    public void doDiff(SVNURL url1, SVNRevision rN, SVNURL url2, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        doDiff(url1, rN, url2, rM, SVNDepth.fromRecurse(recursive), useAncestry, result);
    }
    
    public void doDiff(SVNURL url1, SVNRevision rN, SVNURL url2, SVNRevision rM, SVNDepth depth, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        getDiffGenerator().init(url1.toString(), url2.toString());
        doDiffURLURL(url1, null, rN, url2, null, rM, SVNRevision.UNDEFINED, depth, useAncestry, result);
    }
    
    /**
     * Generates the differences comparing the specified URL in a certain 
     * revision against either the specified Working Copy path or its repository 
     * location URL in the specified revision, and writes the result to the provided output
     * stream.
     * 
     * <p>
     * If <code>rN</code> is not a local revision (see {@link SVNRevision#isLocal()}),
     * then its repository location URL as it is in the revision represented by 
     * <code>rN</code> is taken for comparison with <code>url2</code>.
     * 
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn diff -r N:M PATH URL'</code> command.
     * 
     * @param  path1          a WC path  
     * @param  rN             a revision of <code>path1</code>
     * @param  url2           a repository location URL that is to be compared 
     *                        against <code>path1</code> (or its repository location)
     * @param  rM             a revision of <code>url2</code> 
     * @param  recursive      <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>rN</code> and <code>rM</code> is
     *                        invalid
     *                        <li><code>path1</code> is not under version control
     *                        <li><code>path1</code> has no URL
     *                        <li><code>url2</code> was not found in <code>rM</code>
     *                        <li>the repository location of <code>path1</code> was 
     *                        not found in <code>rN</code>
     *                        </ul>
     */
    public void doDiff(File path1, SVNRevision rN, SVNURL url2, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        doDiff(path1, rN, url2, rM, SVNDepth.fromRecurse(recursive), useAncestry, result);
    }
    
    public void doDiff(ISVNPathList pathList, SVNURL[] urls, SVNRevision rM, SVNDepth depth, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (pathList == null || urls == null) {
            return;
        }
        int i = 0;
        for (Iterator paths = pathList.getPathsIterator(); paths.hasNext() && i < urls.length; i++) {
            File path = (File) paths.next();
            SVNRevision rN = pathList.getPegRevision(path);
            SVNURL url = urls[i];
            try {
                doDiff(path, rN, url, rM, depth, useAncestry, result);
            } catch (SVNException svne) {
                dispatchEvent(new SVNEvent(svne.getErrorMessage()));
                continue;
            }
        }
    }
    
    public void doDiff(File path1, SVNRevision rN, SVNURL url2, SVNRevision rM, SVNDepth depth, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        getDiffGenerator().init(path1.getAbsolutePath(), url2.toString());
        if (rN == SVNRevision.BASE || rN == SVNRevision.WORKING) {
            doDiffURLWC(url2, rM, SVNRevision.UNDEFINED, path1, rN, true, depth, useAncestry, result);
        } else {
            doDiffURLURL(null, path1, rN, url2, null, rM, SVNRevision.UNDEFINED, depth, useAncestry, result);
        }
    }
    
    /**
     * Generates the differences comparing either the specified Working Copy path or 
     * its repository location URL in the specified revision against the specified URL 
     * in a certain revision, and writes the result to the provided output stream.
     * 
     * <p>
     * If <code>rM</code> is not a local revision (see {@link SVNRevision#isLocal()}),
     * then its repository location URL as it is in the revision represented by 
     * <code>rM</code> is taken for comparison with <code>url1</code>.
     * 
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn diff -r N:M URL PATH'</code> command.
     * 
     * @param  url1           a repository location URL 
     * @param  rN             a revision of <code>url1</code>
     * @param  path2          a WC path that is to be compared 
     *                        against <code>url1</code>
     * @param  rM             a revision of <code>path2</code>
     * @param  recursive      <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>rN</code> and <code>rM</code> is
     *                        invalid
     *                        <li><code>path2</code> is not under version control
     *                        <li><code>path2</code> has no URL
     *                        <li><code>url1</code> was not found in <code>rN</code>
     *                        <li>the repository location of <code>path2</code> was 
     *                        not found in <code>rM</code>
     *                        </ul>
     */
    public void doDiff(SVNURL url1, SVNRevision rN, File path2, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        doDiff(url1, rN, path2, rM, SVNDepth.fromRecurse(recursive), useAncestry, result);
    }

    public void doDiff(SVNURL[] urls, SVNRevision rN, ISVNPathList pathList, SVNDepth depth, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (pathList == null || urls == null) {
            return;
        }
        int i = 0;
        for (Iterator paths = pathList.getPathsIterator(); paths.hasNext() && i < urls.length; i++) {
            File path = (File) paths.next();
            SVNRevision rM = pathList.getPegRevision(path);
            SVNURL url = urls[i];
            try {
                doDiff(url, rN, path, rM, depth, useAncestry, result);
            } catch (SVNException svne) {
                dispatchEvent(new SVNEvent(svne.getErrorMessage()));
                continue;
            }
        }
    }
    
    public void doDiff(SVNURL url1, SVNRevision rN, File path2, SVNRevision rM, SVNDepth depth, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        getDiffGenerator().init(url1.toString(), path2.getAbsolutePath());
        if (rM == SVNRevision.BASE || rM == SVNRevision.WORKING) {
            doDiffURLWC(url1, rN, SVNRevision.UNDEFINED, path2, rM, false, depth, useAncestry, result);
        } else {
            doDiffURLURL(url1, null, rN, null, path2, rM, SVNRevision.UNDEFINED, depth, useAncestry, result);
        }
    }
    
    /**
     * Generates the differences comparing either the specified Working Copy paths or 
     * their repository location URLs (any combinations are possible) in the specified 
     * revisions and writes the result to the provided output stream.
     * 
     * <p>
     * If both <code>rN</code> and <code>rM</code> are local revisions (see {@link SVNRevision#isLocal()}),
     * then a Working Copy <code>path2</code> is compared against a Working Copy <code>path1</code>.
     * 
     * <p>
     * If <code>rN</code> is a local revision but <code>rM</code> is not, then
     * the repository location URL of <code>path2</code> as it is in the revision 
     * represented by <code>rM</code> is compared against the Working Copy <code>path1</code>.
     *
     * <p>
     * If <code>rM</code> is a local revision but <code>rN</code> is not, then
     * the Working Copy <code>path2</code> is compared against the repository location 
     * URL of <code>path1</code> as it is in the revision represented by <code>rN</code>.
     * 
     * <p>
     * If both <code>rN</code> and <code>rM</code> are non-local revisions, then the
     * repository location URL of <code>path2</code> in revision <code>rM</code> is 
     * compared against the repository location URL of <code>path1</code> in revision 
     * <code>rN</code>.
     * 
     * @param  path1          a WC path
     * @param  rN             a revision of <code>path1</code>
     * @param  path2          a WC path that is to be compared 
     *                        against <code>path1</code>
     * @param  rM             a revision of <code>path2</code>
     * @param  recursive      <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>rN</code> and <code>rM</code> is
     *                        invalid
     *                        <li><code>path1</code> is not under version control
     *                        <li><code>path1</code> has no URL
     *                        <li><code>path2</code> is not under version control
     *                        <li><code>path2</code> has no URL
     *                        <li>the repository location of <code>path1</code> was 
     *                        not found in <code>rN</code>
     *                        <li>the repository location of <code>path2</code> was 
     *                        not found in <code>rM</code>
     *                        <li>both <code>rN</code> and <code>rM</code> are local,
     *                        but either <code>path1</code> does not equal <code>path2</code>,
     *                        or <code>rN</code> is not {@link SVNRevision#BASE}, or
     *                        <code>rM</code> is not {@link SVNRevision#WORKING} 
     *                        </ul>
     */
    public void doDiff(File path1, SVNRevision rN, File path2, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        doDiff(path1, rN, path2, rM, SVNDepth.fromRecurse(recursive), useAncestry, result);
    }
    
    public void doDiff(ISVNPathList pathList1, ISVNPathList pathList2, SVNDepth depth, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (pathList1 == null || pathList2 == null) {
            return;
        }
        
        for (Iterator paths1 = pathList1.getPathsIterator(), 
                paths2 = pathList2.getPathsIterator(); paths1.hasNext() && paths2.hasNext();) {
            File path1 = (File) paths1.next();
            File path2 = (File) paths2.next();
            SVNRevision rN = pathList1.getPegRevision(path1);
            SVNRevision rM = pathList2.getPegRevision(path2);
            try {
                doDiff(path1, rN, path2, rM, depth, useAncestry, result);
            } catch (SVNException svne) {
                dispatchEvent(new SVNEvent(svne.getErrorMessage()));
                continue;
            }
        }
    }
    
    public void doDiff(File path1, SVNRevision rN, File path2, SVNRevision rM, SVNDepth depth, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }

        boolean isPath1Local = rN == SVNRevision.WORKING || rN == SVNRevision.BASE; 
        boolean isPath2Local = rM == SVNRevision.WORKING || rM == SVNRevision.BASE;
        getDiffGenerator().init(path1.getAbsolutePath(), path2.getAbsolutePath());
        if (isPath1Local && isPath2Local) {
            doDiffWCWC(path1, rN, path2, rM, depth, useAncestry, result);
        } else if (isPath1Local) {
            doDiffURLWC(path2, rM, SVNRevision.UNDEFINED, path1, rN, true, depth, useAncestry, result);
        } else if (isPath2Local) {
            doDiffURLWC(path1, rN, SVNRevision.UNDEFINED, path2, rM, false, depth, useAncestry, result);
        } else {
            doDiffURLURL(null, path1, rN, null, path2, rM, SVNRevision.UNDEFINED, depth, useAncestry, result);
        }
    }

    /**
     * Diffs one path against another one providing short status-like change information to the provided
     * handler. This method functionality is equivalent to the 'svn diff --summarize' command.
     * 
     * @param  path1             the path of a left-hand item to diff
     * @param  rN                a revision of <code>path1</code>
     * @param  path2             the path of a right-hand item to diff
     * @param  rM                a revision of <code>path2</code>
     * @param  recursive         controls whether operation must recurse or not 
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  handler           a diff status handler
     * @throws SVNException
     * @since                    1.1, new in Subversion 1.4
     */
    public void doDiffStatus(File path1, SVNRevision rN, File path2, SVNRevision rM, boolean recursive, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        doDiffStatus(path1, rN, path2, rM, SVNDepth.fromRecurse(recursive), useAncestry, handler);
    }
    
    public void doDiffStatus(File path, SVNRevision rN, SVNRevision rM, SVNRevision pegRevision, SVNDepth depth, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        
        if (pegRevision == null) {
            pegRevision = SVNRevision.UNDEFINED;
        }
        
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Not all required revisions are specified");            
            SVNErrorManager.error(err);
        }
        
        boolean isPath1Local = rN == SVNRevision.WORKING || rN == SVNRevision.BASE; 
        boolean isPath2Local = rM == SVNRevision.WORKING || rM == SVNRevision.BASE;
        if (isPath1Local || isPath2Local) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Summarizing diff can only compare repository to repository");
            SVNErrorManager.error(err);
        } 
        doDiffURLURL(null, path, rN, null, path, rM, pegRevision, depth, useAncestry, handler);        
    }
    
    public void doDiffStatus(ISVNPathList pathList1, ISVNPathList pathList2, SVNDepth depth, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        if (pathList1 == null || pathList2 == null) {
            return;
        }
        for (Iterator paths1 = pathList1.getPathsIterator(), 
                paths2 = pathList2.getPathsIterator(); paths1.hasNext() && paths2.hasNext();) {
            File path1 = (File) paths1.next();
            File path2 = (File) paths2.next();
            SVNRevision rN = pathList1.getPegRevision(path1);
            SVNRevision rM = pathList2.getPegRevision(path2);
            try {
                doDiffStatus(path1, rN, path2, rM, depth, useAncestry, handler);
            } catch (SVNException svne) {
                dispatchEvent(new SVNEvent(svne.getErrorMessage()));
                continue;
            }
        }        
    }
    
    public void doDiffStatus(File path1, SVNRevision rN, File path2, SVNRevision rM, SVNDepth depth, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Not all required revisions are specified");            
            SVNErrorManager.error(err);
        }

        boolean isPath1Local = rN == SVNRevision.WORKING || rN == SVNRevision.BASE; 
        boolean isPath2Local = rM == SVNRevision.WORKING || rM == SVNRevision.BASE;
        if (isPath1Local || isPath2Local) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Summarizing diff can only compare repository to repository");
            SVNErrorManager.error(err);
        } 
        doDiffURLURL(null, path1, rN, null, path2, rM, SVNRevision.UNDEFINED, depth, useAncestry, handler);        
    }
    
    /**
     * Diffs a path against a url providing short status-like change information to the provided
     * handler. This method functionality is equivalent to the 'svn diff --summarize' command.
     * 
     * @param  path1             the path of a left-hand item to diff
     * @param  rN                a revision of <code>path1</code>
     * @param  url2              the url of a right-hand item to diff
     * @param  rM                a revision of <code>url2</code>
     * @param  recursive         controls whether operation must recurse or not 
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  handler           a diff status handler
     * @throws SVNException
     * @since                    1.1, new in Subversion 1.4
     */
    public void doDiffStatus(File path1, SVNRevision rN, SVNURL url2, SVNRevision rM, boolean recursive, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        doDiffStatus(path1, rN, url2, rM, SVNDepth.fromRecurse(recursive), useAncestry, handler);
    }
    
    public void doDiffStatus(ISVNPathList pathList, SVNURL[] urls, SVNRevision rM, SVNDepth depth, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        if (pathList == null || urls == null) {
            return;
        }
        int i = 0;
        for (Iterator paths = pathList.getPathsIterator(); paths.hasNext() && i < urls.length; i++) {
            File path = (File) paths.next();
            SVNRevision rN = pathList.getPegRevision(path);
            SVNURL url = urls[i];
            try {
                doDiffStatus(path, rN, url, rM, depth, useAncestry, handler);
            } catch (SVNException svne) {
                dispatchEvent(new SVNEvent(svne.getErrorMessage()));                
                continue;
            }
        }
    }
    
    public void doDiffStatus(File path1, SVNRevision rN, SVNURL url2, SVNRevision rM, SVNDepth depth, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Not all required revisions are specified");            
            SVNErrorManager.error(err);
        }
        if (rN == SVNRevision.BASE || rN == SVNRevision.WORKING) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Summarizing diff can only compare repository to repository");
            SVNErrorManager.error(err);
        } else {
            doDiffURLURL(null, path1, rN, url2, null, rM, SVNRevision.UNDEFINED, depth, useAncestry, handler);
        }
    }

    /**
     * Diffs a url against a path providing short status-like change information to the provided
     * handler. This method functionality is equivalent to the 'svn diff --summarize' command.
     * 
     * @param  url1              the url of a left-hand item to diff
     * @param  rN                a revision of <code>url1</code>
     * @param  path2             the path of a right-hand item to diff
     * @param  rM                a revision of <code>path2</code>
     * @param  recursive         controls whether operation must recurse or not 
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  handler           a diff status handler
     * @throws SVNException
     * @since                    1.1, new in Subversion 1.4
     */
    public void doDiffStatus(SVNURL url1, SVNRevision rN, File path2, SVNRevision rM, boolean recursive, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        doDiffStatus(url1, rN, path2, rM, SVNDepth.fromRecurse(recursive), useAncestry, handler);
    }
    
    public void doDiffStatus(SVNURL[] urls, SVNRevision rN, ISVNPathList pathList, SVNDepth depth, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        if (pathList == null || urls == null) {
            return;
        }
        int i = 0;
        for (Iterator paths = pathList.getPathsIterator(); paths.hasNext() && i < urls.length; i++) {
            File path = (File) paths.next();
            SVNRevision rM = pathList.getPegRevision(path);
            SVNURL url = urls[i];
            try {
                doDiffStatus(url, rN, path, rM, depth, useAncestry, handler);
            } catch (SVNException svne) {
                dispatchEvent(new SVNEvent(svne.getErrorMessage()));
                continue;
            }
        }
    }
    
    public void doDiffStatus(SVNURL url1, SVNRevision rN, File path2, SVNRevision rM, SVNDepth depth, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }   
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        if (rM == SVNRevision.BASE || rM == SVNRevision.WORKING) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Summarizing diff can only compare repository to repository");
            SVNErrorManager.error(err);
        } else {
            doDiffURLURL(url1, null, rN, null, path2, rM, SVNRevision.UNDEFINED, depth, useAncestry, handler);
        }
    }

    /**
     * Diffs one url against another one providing short status-like change information to the provided
     * handler. This method functionality is equivalent to the 'svn diff --summarize' command.
     * 
     * @param  url1              the url of a left-hand item to diff
     * @param  rN                a revision of <code>url1</code>
     * @param  url2              the url of a right-hand item to diff
     * @param  rM                a revision of <code>url2</code>
     * @param  recursive         controls whether operation must recurse or not 
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  handler           a diff status handler
     * @throws SVNException
     * @since                    1.1, new in Subversion 1.4
     */
    public void doDiffStatus(SVNURL url1, SVNRevision rN, SVNURL url2, SVNRevision rM, boolean recursive, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        doDiffStatus(url1, rN, url2, rM, SVNDepth.fromRecurse(recursive), useAncestry, handler);
    }
    
    public void doDiffStatus(SVNURL url, SVNRevision rN, SVNRevision rM, SVNRevision pegRevision, SVNDepth depth, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }

        if (pegRevision == null) {
            pegRevision = SVNRevision.UNDEFINED;
        }
        
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        doDiffURLURL(url, null, rN, url, null, rM, pegRevision, depth, useAncestry, handler);
    }

    public void doDiffStatus(SVNURL url1, SVNRevision rN, SVNURL url2, SVNRevision rM, SVNDepth depth, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        doDiffURLURL(url1, null, rN, url2, null, rM, SVNRevision.UNDEFINED, depth, useAncestry, handler);
    }
    
    private void doDiffURLWC(SVNURL url1, SVNRevision revision1, SVNRevision pegRevision, File path2, SVNRevision revision2, 
            boolean reverse, SVNDepth depth, boolean useAncestry, OutputStream result) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminAreaInfo info = wcAccess.openAnchor(path2, false, SVNDepth.recurseFromDepth(depth) ? SVNWCAccess.INFINITE_DEPTH : 0);
            File anchorPath = info.getAnchor().getRoot();
            String target = "".equals(info.getTargetName()) ? null : info.getTargetName();
            
            SVNEntry anchorEntry = info.getAnchor().getVersionedEntry("", false);
            if (anchorEntry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", anchorPath);
                SVNErrorManager.error(err);
            }
            SVNURL anchorURL = anchorEntry.getSVNURL();
            if (pegRevision.isValid()) {
                SVNRepositoryLocation[] locations = getLocations(url1, null, null, pegRevision, revision1, SVNRevision.UNDEFINED);
                url1 = locations[0].getURL();
                String anchorPath2 = SVNPathUtil.append(anchorURL.toString(), target == null ? "" : target);
                getDiffGenerator().init(url1.toString(), anchorPath2);
            }
            SVNRepository repository = createRepository(anchorURL, true);
            long revNumber = getRevisionNumber(revision1, repository, null);
            AbstractDiffCallback callback = new SVNDiffCallback(info.getAnchor(), 
                                                                getDiffGenerator(), 
                                                                reverse ? -1 : revNumber, reverse ? revNumber : -1, result);
            SVNDiffEditor editor = new SVNDiffEditor(wcAccess, info, callback, 
                    useAncestry, reverse /* reverse */,
                    revision2 == SVNRevision.BASE  || revision2 == SVNRevision.COMMITTED  /* compare to base */, 
                    depth);
            SVNReporter reporter = new SVNReporter(info, info.getAnchor().getFile(info.getTargetName()), false, depth, getDebugLog());
            
            long pegRevisionNumber = getRevisionNumber(revision2, repository, path2);
            try {
                repository.diff(url1, revNumber, pegRevisionNumber, target, !useAncestry, depth, true, reporter, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
            } finally {
                editor.cleanup();
            }
        } finally {
            wcAccess.close();
        }
    }

    private void doDiffURLWC(File path1, SVNRevision revision1, SVNRevision pegRevision, 
                             File path2, SVNRevision revision2, boolean reverse, SVNDepth depth, 
                             boolean useAncestry, OutputStream result) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        try {
            int admDepth = getAdminDepth(depth);
            SVNAdminAreaInfo info = wcAccess.openAnchor(path2, false, admDepth);
            File anchorPath = info.getAnchor().getRoot();
            String target = "".equals(info.getTargetName()) ? null : info.getTargetName();
            
            SVNEntry anchorEntry = info.getAnchor().getVersionedEntry("", false);
            if (anchorEntry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", anchorPath);
                SVNErrorManager.error(err);
            }
            SVNURL url1;
            SVNURL anchorURL = anchorEntry.getSVNURL();
            if (pegRevision.isValid()) {
                SVNRepositoryLocation[] locations = getLocations(null, path1, null, pegRevision, revision1, SVNRevision.UNDEFINED);
                url1 = locations[0].getURL();
                String anchorPath2 = SVNPathUtil.append(anchorURL.toString(), target == null ? "" : target);
                if (!reverse) {
                    getDiffGenerator().init(url1.toString(), anchorPath2);
                } else {
                    getDiffGenerator().init(anchorPath2, url1.toString());
                } 
            } else {
                url1 = getURL(path1);
            }
            SVNRepository repository = createRepository(anchorURL, true);
            long revNumber = getRevisionNumber(revision1, repository, path1);
            AbstractDiffCallback callback = new SVNDiffCallback(info.getAnchor(), 
                                                                getDiffGenerator(), 
                                                                reverse ? -1 : revNumber, reverse ? revNumber : -1, result);
            SVNDiffEditor editor = new SVNDiffEditor(wcAccess, info, callback,
                    useAncestry, 
                    reverse /* reverse */, 
                    revision2 == SVNRevision.BASE || revision2 == SVNRevision.COMMITTED /* compare to base */, 
                    depth);
            SVNReporter reporter = new SVNReporter(info, info.getAnchor().getFile(info.getTargetName()), false, depth, getDebugLog());
            
            // this should be rev2.
            long pegRevisionNumber = getRevisionNumber(revision2, repository, path2);
            try {
                repository.diff(url1, revNumber, pegRevisionNumber, target, !useAncestry, depth, true, reporter, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
            } finally {
                editor.cleanup();
            }
        } finally {
            wcAccess.close();
        }
    }
    
    private void doDiffWCWC(File path1, SVNRevision revision1, File path2, SVNRevision revision2, 
                            SVNDepth depth, boolean useAncestry, OutputStream result) throws SVNException {
        if (!path1.equals(path2) || !(revision1 == SVNRevision.BASE && revision2 == SVNRevision.WORKING)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Only diffs between a path's text-base " +
                                    "and its working files are supported at this time (-rBASE:WORKING)");
            SVNErrorManager.error(err);
        }
        
        SVNWCAccess wcAccess = createWCAccess();
        try {
            int admDepth = getAdminDepth(depth);
            SVNAdminAreaInfo info = wcAccess.openAnchor(path1, false, admDepth);
            wcAccess.getVersionedEntry(path1, false);
            long rev = getRevisionNumber(revision1, null, path1);
            AbstractDiffCallback callback = new SVNDiffCallback(info.getAnchor(), 
                                                                getDiffGenerator(), 
                                                                rev, -1, result);
            SVNDiffEditor editor = new SVNDiffEditor(wcAccess, info, callback, useAncestry, false, 
                                                     false, depth);
            try {
                editor.closeEdit();
            } finally {
                editor.cleanup();
            }
        } finally {
            wcAccess.close();
        }
    }
    
    private void doDiffURLURL(SVNURL url1, File path1, SVNRevision revision1, SVNURL url2, File path2, SVNRevision revision2, SVNRevision pegRevision,
            SVNDepth depth, boolean useAncestry, OutputStream result) throws SVNException {
        File basePath = null;
        if (path1 != null) {
            basePath = path1;
        }
        if (path2 != null) {
            basePath = path2;
        }
        if (pegRevision.isValid()) {
            SVNRepositoryLocation[] locations = getLocations(url2, path2, null, pegRevision, revision1, revision2);
            url1 = locations[0].getURL();
            url2 = locations[1].getURL();
            
            getDiffGenerator().init(url1.toString(), url2.toString());
        } else {
            url1 = url1 == null ? getURL(path1) : url1;
            url2 = url2 == null ? getURL(path2) : url2;
        }
        SVNRepository repository1 = createRepository(url1, true);
        SVNRepository repository2 = createRepository(url2, false);

        final long rev1 = getRevisionNumber(revision1, repository1, path1);
        long rev2 = -1;
        String target1 = null;
        SVNNodeKind kind1 = null;
        SVNNodeKind kind2 = null;
        try {
            rev2 = getRevisionNumber(revision2, repository2, path2);            
            kind1 = repository1.checkPath("", rev1);
            kind2 = repository2.checkPath("", rev2);
            if (kind1 == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "''{0}'' was not found in the repository at revision {1}",
                        new Object[] {url1, new Long(rev1)});
                SVNErrorManager.error(err);
            } else if (kind2 == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "''{0}'' was not found in the repository at revision {1}",
                        new Object[] {url2, new Long(rev2)});
                SVNErrorManager.error(err);
            }
        } finally {
            repository2.closeSession();
        }
        if (kind1 == SVNNodeKind.FILE || kind2 == SVNNodeKind.FILE) {
            target1 = SVNPathUtil.tail(url1.getPath());
            if (basePath != null) {
                basePath = basePath.getParentFile();
            }
            url1 = SVNURL.parseURIEncoded(SVNPathUtil.removeTail(url1.toString()));
            repository1 = createRepository(url1, true);
        }
        repository2 = createRepository(url1, false); 
        SVNRemoteDiffEditor editor = null;
        try {
            SVNDiffCallback callback = new SVNDiffCallback(null, getDiffGenerator(), rev1, rev2, result);
            callback.setBasePath(basePath);
            editor = new SVNRemoteDiffEditor(null, null, callback, repository2, rev1, rev2, false, null, this);
            ISVNReporterBaton reporter = new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    //TODO(sd): dynamic depth here
                    reporter.setPath("", null, rev1, SVNDepth.INFINITY, false);
                    reporter.finishReport();
                }
            };
            repository1.diff(url2, rev2, rev1, target1, !useAncestry, depth, true, reporter, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
        } finally {
            if (editor != null) {
                editor.cleanup();
            }
            repository2.closeSession();
        }
    }

    private void doDiffURLURL(SVNURL url1, File path1, SVNRevision revision1, SVNURL url2, File path2, SVNRevision revision2, SVNRevision pegRevision,
            SVNDepth depth, boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
        File basePath = null;
        if (path1 != null) {
            basePath = path1;
        }
        if (path2 != null) {
            basePath = path2;
        }
        if (pegRevision.isValid()) {
            SVNRepositoryLocation[] locations = getLocations(url2, path2, null, pegRevision, revision1, revision2);
            url1 = locations[0].getURL();
            url2 = locations[1].getURL();
            
            getDiffGenerator().init(url1.toString(), url2.toString());
        } else {
            url1 = url1 == null ? getURL(path1) : url1;
            url2 = url2 == null ? getURL(path2) : url2;
        }
        SVNRepository repository1 = createRepository(url1, true);
        SVNRepository repository2 = createRepository(url2, false);
        
        final long rev1 = getRevisionNumber(revision1, repository1, path1);
        long rev2 = -1;
        SVNNodeKind kind1 = null;
        SVNNodeKind kind2 = null;
        String target1 = null;

        try {
            rev2 = getRevisionNumber(revision2, repository2, path2);
            
            kind1 = repository1.checkPath("", rev1);
            kind2 = repository2.checkPath("", rev2);
            if (kind1 == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "''{0}'' was not found in the repository at revision {1}",
                        new Object[] {url1, new Long(rev1)});
                SVNErrorManager.error(err);
            } else if (kind2 == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "''{0}'' was not found in the repository at revision {1}",
                        new Object[] {url2, new Long(rev2)});
                SVNErrorManager.error(err);
            }
            if (kind1 == SVNNodeKind.FILE || kind2 == SVNNodeKind.FILE) {
                target1 = SVNPathUtil.tail(url1.getPath());
                if (basePath != null) {
                    basePath = basePath.getParentFile();
                }
                url1 = SVNURL.parseURIEncoded(SVNPathUtil.removeTail(url1.toString()));
                repository1 = createRepository(url1, true);
            }
        } finally {
            repository2.closeSession();
        }
        repository2 = createRepository(url1, false); 
        File tmpFile = getDiffGenerator().createTempDirectory();
        try {
            SVNDiffStatusEditor editor = new SVNDiffStatusEditor(basePath, repository2, rev1, handler);
            ISVNReporterBaton reporter = new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    //TODO(sd): dynamic depth here
                    reporter.setPath("", null, rev1, SVNDepth.INFINITY, false);
                    reporter.finishReport();
                }
            };
            repository1.diff(url2, rev2, rev1, target1, !useAncestry, depth, false, reporter, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
        } finally {
            if (tmpFile != null) {
                SVNFileUtil.deleteAll(tmpFile, true, null);
            }
            repository2.closeSession();
        }
    }
    
    private int getAdminDepth(SVNDepth depth) {
        int admDepth = SVNWCAccess.INFINITE_DEPTH;
        if (depth == SVNDepth.IMMEDIATES) {
            admDepth = 1;
        } else if (depth == SVNDepth.EMPTY || depth == SVNDepth.FILES) {
            admDepth = 0;
        }
        return admDepth;
    }
    
    /**
     * Applies the differences between two sources (using Working Copy paths to 
     * get corresponding URLs of the sources) to a Working Copy path.
     *
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn merge sourceWCPATH1@rev1 sourceWCPATH2@rev2 WCPATH'</code> command.
     * 
     * <p>
     * If you need only to try merging your file(s) without actual merging, you
     * should set <code>dryRun</code> to <span class="javakeyword">true</span>.
     * Your event handler will be dispatched status type information on the target 
     * path(s). If a path can be successfully merged, the status type will be
     * {@link SVNStatusType#MERGED} for that path.  
     * 
     * @param  path1          the first source path
     * @param  revision1      a revision of <code>path1</code>
     * @param  path2          the second source path which URL is to be compared
     *                        against the URL of <code>path1</code>
     * @param  revision2      a revision of <code>path2</code>
     * @param  dstPath        the target path to which the result should
     *                        be applied 
     * @param  recursive     <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>revision1</code> and <code>revision2</code> is
     *                        invalid
     *                        <li><code>path1</code> has no URL
     *                        <li><code>path2</code> has no URL
     *                        <li>the repository location of <code>path1</code> was 
     *                        not found in <code>revision1</code>
     *                        <li>the repository location of <code>path2</code> was 
     *                        not found in <code>revision2</code>
     *                        <li><code>dstPath</code> is not under version control
     *                        </ul>
     */
    public void doMerge(File path1, SVNRevision revision1, File path2, SVNRevision revision2, File dstPath, boolean recursive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        doMerge(path1, revision1, path2, revision2, dstPath, SVNDepth.fromRecurse(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    public void doMerge(File path1, SVNRevision revision1, File path2, SVNRevision revision2, 
                        File dstPath, SVNDepth depth, boolean useAncestry, boolean force, 
                        boolean dryRun, boolean recordOnly) throws SVNException {
        path1 = new File(SVNPathUtil.validateFilePath(path1.getAbsolutePath())).getAbsoluteFile();
        path2 = new File(SVNPathUtil.validateFilePath(path2.getAbsolutePath())).getAbsoluteFile();
        dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath())).getAbsoluteFile();
        /*
         * Same as 2. merge sourceWCPATH1@N sourceWCPATH2@M [WCPATH]
         * or      3. merge -r N:M SOURCE[@REV] [WCPATH]
         * where SOURCE is a path and path1 and path2 are the same.
         */
        SVNURL url1 = getURL(path1);
        if (url1 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path1);
            SVNErrorManager.error(err);
        }
        SVNURL url2 = getURL(path2);
        if (url2 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path2);
            SVNErrorManager.error(err);
        }
        runMerge(url1, revision1, url2, revision2, dstPath, depth, dryRun, force, useAncestry, 
                 recordOnly);
    }
    
    /**
     * Applies the differences between two sources (a source URL against the 
     * repository location URL of a source Working Copy path) to a Working Copy 
     * path.
     * 
     * <p>
     * If you need only to try merging your file(s) without actual merging, you
     * should set <code>dryRun</code> to <span class="javakeyword">true</span>.
     * Your event handler will be dispatched status type information on the target 
     * path(s). If a path can be successfully merged, the status type will be
     * {@link SVNStatusType#MERGED} for that path.  
     * 
     * @param  path1          the first source - a WC path 
     * @param  revision1      a revision of <code>path1</code>
     * @param  url2           the second source - a URL that is to be compared 
     *                        against the URL of <code>path1</code>
     * @param  revision2      a revision of <code>url2</code>
     * @param  dstPath        the target path to which the result should
     *                        be applied
     * @param  recursive     <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>revision1</code> and <code>revision2</code> is
     *                        invalid
     *                        <li><code>path1</code> has no URL
     *                        <li>the repository location of <code>path1</code> was 
     *                        not found in <code>revision1</code>
     *                        <li><code>url2</code> was not found in 
     *                        <code>revision2</code>
     *                        <li><code>dstPath</code> is not under version control
     *                        </ul>
     */
    public void doMerge(File path1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, File dstPath, boolean recursive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        doMerge(path1, revision1, url2, revision2, dstPath, SVNDepth.fromRecurse(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    public void doMerge(File path1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, 
                        File dstPath, SVNDepth depth, boolean useAncestry, boolean force, 
                        boolean dryRun, boolean recordOnly) throws SVNException {
        path1 = new File(SVNPathUtil.validateFilePath(path1.getAbsolutePath())).getAbsoluteFile();
        dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath())).getAbsoluteFile();
        SVNURL url1 = getURL(path1);
        if (url1 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path1);
            SVNErrorManager.error(err);
        }
        runMerge(url1, revision1, url2, revision2, dstPath, depth, dryRun, force, useAncestry, 
                 recordOnly);
    }
    
    /**
     * Applies the differences between two sources (the repository location URL of 
     * a source Working Copy against a source URL) to a Working Copy path.
     * 
     * <p>
     * If you need only to try merging your file(s) without actual merging, you
     * should set <code>dryRun</code> to <span class="javakeyword">true</span>.
     * Your event handler will be dispatched status type information on the target 
     * path(s). If a path can be successfully merged, the status type will be
     * {@link SVNStatusType#MERGED} for that path.  
     * 
     * @param  url1           the first source - a URL
     * @param  revision1      a revision of <code>url1</code>
     * @param  path2          the second source - a WC path that is to be compared 
     *                        against <code>url1</code>
     * @param  revision2      a revision of <code>path2</code>
     * @param  dstPath        the target path to which the result should
     *                        be applied
     * @param  recursive     <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>revision1</code> and <code>revision2</code> is
     *                        invalid
     *                        <li><code>path2</code> has no URL
     *                        <li><code>url1</code> was not found in 
     *                        <code>revision1</code>
     *                        <li>the repository location of <code>path2</code> was 
     *                        not found in <code>revision2</code>
     *                        <li><code>dstPath</code> is not under version control
     *                        </ul>
     */
    public void doMerge(SVNURL url1, SVNRevision revision1, File path2, SVNRevision revision2, File dstPath, boolean recursive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        doMerge(url1, revision1, path2, revision2, dstPath, SVNDepth.fromRecurse(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    public void doMerge(SVNURL url1, SVNRevision revision1, File path2, SVNRevision revision2, 
                        File dstPath, SVNDepth depth, boolean useAncestry, boolean force, 
                        boolean dryRun, boolean recordOnly) throws SVNException {
        path2 = new File(SVNPathUtil.validateFilePath(path2.getAbsolutePath())).getAbsoluteFile();
        dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath())).getAbsoluteFile();
        SVNURL url2 = getURL(path2);
        if (url2 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path2);
            SVNErrorManager.error(err);
        }
        runMerge(url1, revision1, url2, revision2, dstPath, depth, dryRun, force, useAncestry, 
                 recordOnly);
    }
    
    /**
     * Applies the differences between two sources (one source URL against another 
     * source URL) to a Working Copy path.
     *
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn merge sourceURL1@rev1 sourceURL2@rev2 WCPATH'</code> command.
     * 
     * <p>
     * If you need only to try merging your file(s) without actual merging, you
     * should set <code>dryRun</code> to <span class="javakeyword">true</span>.
     * Your event handler will be dispatched status type information on the target 
     * path(s). If a path can be successfully merged, the status type will be
     * {@link SVNStatusType#MERGED} for that path.  
     * 
     * @param  url1           the first source URL
     * @param  revision1      a revision of <code>url1</code>
     * @param  url2           the second source URL that is to be compared against 
     *                        <code>url1</code>
     * @param  revision2      a revision of <code>url2</code>
     * @param  dstPath        the target path to which the result should
     *                        be applied
     * @param  recursive     <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>revision1</code> and <code>revision2</code> is
     *                        invalid
     *                        <li><code>url1</code> was not found in 
     *                        <code>revision1</code>
     *                        <li><code>url2</code> was not found in 
     *                        <code>revision2</code>
     *                        <li><code>dstPath</code> is not under version control
     *                        </ul>
     */
    public void doMerge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, File dstPath, boolean recursive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        doMerge(url1, revision1, url2, revision2, dstPath, SVNDepth.fromRecurse(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    public void doMerge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, 
                        File dstPath, SVNDepth depth, boolean useAncestry, boolean force, 
                        boolean dryRun, boolean recordOnly) throws SVNException {
         runMerge(url1, revision1, url2, revision2, dstPath, depth, dryRun, force, useAncestry, 
                  recordOnly);
    }
    
    /**
     * Applies the differences between two sources (a source URL in a particular
     * revision against the same source URL in another particular revision) to a 
     * Working Copy path.
     * 
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn merge -r rev1:rev2 URL@pegRev WCPATH'</code> command.
     * 
     * <p>
     * If you need only to try merging your file(s) without actual merging, you
     * should set <code>dryRun</code> to <span class="javakeyword">true</span>.
     * Your event handler will be dispatched status type information on the target 
     * path(s). If a path can be successfully merged, the status type will be
     * {@link SVNStatusType#MERGED} for that path.  
     * 
     * @param  url1           a source URL
     * @param  pegRevision    a revision in which code>url1</code> 
     *                        is first looked up
     * @param  revision1      a left-hand revision of <code>url1</code> 
     * @param  revision2      a right-hand revision of <code>url1</code>
     * @param  dstPath        the target path to which the result should
     *                        be applied
     * @param  recursive     <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>revision1</code>, <code>revision2</code> and
     *                        <code>pegRevision</code> is invalid
     *                        <li><code>url1</code> was not found in 
     *                        <code>revision1</code>
     *                        <li><code>url1</code> was not found in 
     *                        <code>revision2</code>
     *                        <li><code>dstPath</code> is not under version control
     *                        </ul>
     */
    public void doMerge(SVNURL url1, SVNRevision pegRevision, SVNRevision revision1, SVNRevision revision2, File dstPath, boolean recursive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        doMerge(url1, pegRevision, revision1, revision2, dstPath, SVNDepth.fromRecurse(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    public void doMerge(SVNURL url1, SVNRevision pegRevision, SVNRevision revision1, 
                        SVNRevision revision2, File dstPath, SVNDepth depth, boolean useAncestry, 
                        boolean force, boolean dryRun, boolean recordOnly) throws SVNException {
        if (pegRevision == null || !pegRevision.isValid()) {
            pegRevision = SVNRevision.HEAD;
        }
        runPeggedMerge(url1, null, pegRevision, revision1, revision2, dstPath, depth, dryRun, 
                       force, useAncestry, recordOnly);
    }
    
    /**
     * Applies the differences between two sources (the repository location of
     * a source Working Copy path in a particular revision against the repository
     * location of the same path in another particular revision) to a 
     * Working Copy path.
     * 
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn merge -r rev1:rev2 sourceWCPATH@pegRev WCPATH'</code> command.
     * 
     * <p>
     * If you need only to try merging your file(s) without actual merging, you
     * should set <code>dryRun</code> to <span class="javakeyword">true</span>.
     * Your event handler will be dispatched status type information on the target 
     * path(s). If a path can be successfully merged, the status type will be
     * {@link SVNStatusType#MERGED} for that path.  
     * 
     * @param  path1          a source WC path
     * @param  pegRevision    a revision in which the repository location of 
     *                        <code>path1</code> is first looked up
     * @param  revision1      a left-hand revision of <code>path1</code> 
     * @param  revision2      a right-hand revision of <code>path1</code>
     * @param  dstPath        the target path to which the result should
     *                        be applied
     * @param  recursive     <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>revision1</code>, <code>revision2</code> and
     *                        <code>pegRevision</code> is invalid
     *                        <li><code>path1</code> has no URL
     *                        <li>the repository location of <code>path1</code> was not found in 
     *                        <code>revision1</code>
     *                        <li>the repository location of <code>path1</code> was not found in 
     *                        <code>revision2</code>
     *                        <li><code>dstPath</code> is not under version control
     *                        </ul>
     */
    public void doMerge(File path1, SVNRevision pegRevision, SVNRevision revision1, SVNRevision revision2, File dstPath, boolean recursive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        doMerge(path1, pegRevision, revision1, revision2, dstPath, SVNDepth.fromRecurse(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    public void doMerge(File path1, SVNRevision pegRevision, SVNRevision revision1, 
                        SVNRevision revision2, File dstPath, SVNDepth depth, boolean useAncestry, 
                        boolean force, boolean dryRun, boolean recordOnly) throws SVNException {
        /*
         * Equivalent of 3. merge -r N:M SOURCE[@REV] [WCPATH]
         * where SOURCE is a wc path.
         */
        if (pegRevision == null || !pegRevision.isValid()) {
            pegRevision = SVNRevision.WORKING;
        }
        runPeggedMerge(null, path1, pegRevision, revision1, revision2, dstPath, depth, dryRun, 
                       force, useAncestry, recordOnly);
    }
    
    public SVNMergeInfo getMergeInfo(File path) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        try {
            wcAccess.probeOpen(path, false, 0);
            SVNEntry entry = wcAccess.getVersionedEntry(path, false);
            Map mergeInfo = new TreeMap();
            getWCOrRepositoryMergeInfo(wcAccess, mergeInfo, path, entry, 
                                       SVNMergeInfoInheritance.INHERITED, 
                                       false, false, null);
            if (mergeInfo.isEmpty()) {
                return null;
            }
            return new SVNMergeInfo(path, mergeInfo);
        } finally {
            wcAccess.close();
        }
    }

    public SVNMergeInfo getMergeInfo(SVNURL url, SVNRevision revision) throws SVNException {
        SVNRepository repository = createRepository(url, true);
        long revisionNum = getRevisionNumber(revision, repository, null);
        SVNURL repositoryRoot = repository.getRepositoryRoot(true);
        String relPath = url.getPath().substring(repositoryRoot.getPath().length());
        if (!relPath.startsWith("/")) {
            relPath = "/" + relPath;
        }

        Map mergeInfos = repository.getMergeInfo(new String[] {relPath}, revisionNum, 
                                                 SVNMergeInfoInheritance.INHERITED);
        return (SVNMergeInfo) mergeInfos.get(relPath);
    }
    
    private SVNRevision[] getAssumedDefaultRevisionRange(SVNRevision revision1, 
                                                          SVNRevision revision2, 
                                                          SVNRepository repository) throws SVNException {
        long headRevNumber = SVNRepository.INVALID_REVISION;
        SVNRevision assumedRevision1 = SVNRevision.UNDEFINED;
        SVNRevision assumedRevision2 = SVNRevision.UNDEFINED;
        if (!revision1.isValid()) {
            headRevNumber = getRevisionNumber(SVNRevision.HEAD, repository, null);
            long assumedRev1Number = getPathLastChangeRevision("", headRevNumber, repository);
            if (SVNRevision.isValidRevisionNumber(assumedRev1Number)) {
                assumedRevision1 = SVNRevision.create(assumedRev1Number);
            }
        } else {
            assumedRevision1 = revision1;
        }
        
        if (!revision2.isValid()) {
            if (SVNRevision.isValidRevisionNumber(headRevNumber)) {
                assumedRevision2 = SVNRevision.create(headRevNumber);
            } else {
                assumedRevision2 = SVNRevision.HEAD;
            }
        } else {
            assumedRevision2 = revision2;
        }
        
        SVNRevision[] revs = new SVNRevision[2];
        revs[0] = assumedRevision1;
        revs[1] = assumedRevision2;
        return revs;
    }
    
    private void runPeggedMerge(SVNURL srcURL, File srcPath, SVNRevision pegRevision, 
                                SVNRevision revision1, SVNRevision revision2, File dstPath, 
                                SVNDepth depth, boolean dryRun, boolean force, boolean useAncestry, 
                                boolean recordOnly) throws SVNException {
    
        SVNWCAccess wcAccess = createWCAccess();
        dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath()));
        try {
            dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath()));
            SVNAdminArea adminArea = wcAccess.probeOpen(dstPath, !dryRun, 
                                                        SVNDepth.recurseFromDepth(depth) ? 
                                                        SVNWCAccess.INFINITE_DEPTH : 0);
            SVNEntry targetEntry = wcAccess.getVersionedEntry(dstPath, false);
            
            SVNURL wcReposRoot = null;
            if (targetEntry.getRepositoryRoot() != null) {
                wcReposRoot = targetEntry.getRepositoryRootURL();
            } else {
                SVNRepository repos = createRepository(null, dstPath, 
                                                       SVNRevision.WORKING, 
                                                       SVNRevision.WORKING);
                wcReposRoot = repos.getRepositoryRoot(true);
            }
            
            if (depth == null || depth == SVNDepth.UNKNOWN) {
                depth = targetEntry.getDepth();
            }
            
            if (srcURL == null && srcPath == null) {
                LinkedList suggestedSources = suggestMergeSources(dstPath, SVNRevision.WORKING);
                if (suggestedSources.isEmpty()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
                                                                 "Unable to determine merge source for ''{0}'', please provide an explicit source",
                                                                 dstPath);
                    SVNErrorManager.error(err);
                }
                
                String suggestedSrc = (String) suggestedSources.getFirst();
                if (suggestedSrc.startsWith("/")) {
                    suggestedSrc = suggestedSrc.substring(1);
                }
                srcURL = wcReposRoot.appendPath(suggestedSrc, false);
            } else if (srcURL == null && srcPath != null) {
                srcURL = getURL(srcPath);
                if (srcURL == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                                                                 "''{0}'' has no URL", srcPath);
                    SVNErrorManager.error(err);
                }
            }
            
            Merger merger = createMerger(srcURL, srcURL, targetEntry, dstPath, wcAccess, dryRun, 
                                         force, recordOnly, getConflictHandler());
            SVNRepository repository = createRepository(srcURL, true);
            SVNRevision[] revs = getAssumedDefaultRevisionRange(revision1, revision2, repository);

            SVNRepositoryLocation[] locations = getLocations(srcURL, srcPath, null, pegRevision, 
                                                             revs[0], revs[1]);
            SVNURL url1 = locations[0].getURL();
            SVNURL url2 = locations[1].getURL();
            revision1 = SVNRevision.create(locations[0].getRevisionNumber());
            revision2 = SVNRevision.create(locations[1].getRevisionNumber());
            
            if (targetEntry.isFile()) {
                merger.doMergeFile(url1, revision1, url1, revision2, dstPath, adminArea, 
                                   !useAncestry);
            } else if (targetEntry.isDirectory()) {
                LinkedList childrenWithMergeInfo = null;
                childrenWithMergeInfo = merger.discoverAndMergeChildren(targetEntry, 
                                                                        revision1, 
                                                                        revision2, 
                                                                        depth, 
                                                                        url1,
                                                                        wcReposRoot,
                                                                        !useAncestry);
                
                merger.doMerge(url1, revision1, url2, revision2, dstPath, adminArea, depth, 
                               childrenWithMergeInfo, useAncestry, merger.myHasMissingChildren,
                               merger.myHasExistingMergeInfo ? 0 : -1);
                
                if (!dryRun) {
                    merger.elideChildren(childrenWithMergeInfo, dstPath, targetEntry);
                }
            }
            if (!dryRun) {
                elideMergeInfo(wcAccess, dstPath, false, null);
            }
        } finally {
            wcAccess.close();
        }
    }
    
    private void runMerge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, 
                         File dstPath, SVNDepth depth, boolean dryRun, boolean force, 
                         boolean useAncestry, boolean recordOnly) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath()));
        try {
            dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath()));
            SVNAdminArea adminArea = wcAccess.probeOpen(dstPath, !dryRun, 
                                                        SVNDepth.recurseFromDepth(depth) ? 
                                                        SVNWCAccess.INFINITE_DEPTH : 0);

            SVNEntry targetEntry = wcAccess.getVersionedEntry(dstPath, false);
            SVNURL wcReposRoot = null;
            if (targetEntry.getRepositoryRoot() != null) {
                wcReposRoot = targetEntry.getRepositoryRootURL();
            } else {
                SVNRepository repos = createRepository(null, dstPath, 
                                                       SVNRevision.WORKING, 
                                                       SVNRevision.WORKING);
                wcReposRoot = repos.getRepositoryRoot(true);
            }
            
            if (depth == null || depth == SVNDepth.UNKNOWN) {
                depth = targetEntry.getDepth();
            }
            
            Merger merger = createMerger(url1, url2, targetEntry, dstPath, wcAccess, dryRun, force,  
                                         recordOnly, getConflictHandler());
            if (targetEntry.isFile()) {
                merger.doMergeFile(url1, revision1, url2, revision2, dstPath, adminArea, 
                                   !useAncestry);
            } else if (targetEntry.isDirectory()) {
                LinkedList childrenWithMergeInfo = null;
                if (url1.equals(url2)) {
                    childrenWithMergeInfo = merger.discoverAndMergeChildren(targetEntry, 
                                                                            revision1, 
                                                                            revision2, 
                                                                            depth, 
                                                                            url1,
                                                                            wcReposRoot,
                                                                            !useAncestry);
                }
                
                merger.doMerge(url1, revision1, url2, revision2, dstPath, adminArea, depth, 
                               childrenWithMergeInfo, useAncestry, merger.myHasMissingChildren,
                               merger.myHasExistingMergeInfo ? 0 : -1);
                
                if (!dryRun) {
                    merger.elideChildren(childrenWithMergeInfo, dstPath, targetEntry);
                }
            }
            if (!dryRun) {
                elideMergeInfo(wcAccess, dstPath, false, null);
            }
        } finally {
            wcAccess.close();
        }
    }
    
    private LinkedList suggestMergeSources(File path, SVNRevision revision) throws SVNException {
        LinkedList suggestions = new LinkedList();
        SVNLogClient logClient = new SVNLogClient(getRepositoryPool(), getOptions());
        SVNLocationEntry copyFromInfo = logClient.getCopySource(path, revision);
        String copyFromPath = copyFromInfo.getPath();
        
        if (copyFromPath != null) {
            suggestions.add(copyFromPath);
        }
        
        SVNMergeInfo mergeInfo = getMergeInfo(path);
        if (mergeInfo == null) {
            return suggestions;
        }
        
        Map mergeSrcsToRanges = mergeInfo.getMergeSourcesToMergeLists();
        for (Iterator mergeSrcPaths = mergeSrcsToRanges.keySet().iterator(); mergeSrcPaths.hasNext();) {
            String mergeSrc = (String) mergeSrcPaths.next();
            if (copyFromPath == null || !copyFromPath.equals(mergeSrc)) {
                suggestions.add(mergeSrc);
            }
        }
        return suggestions;
    }
    
    private Merger createMerger(SVNURL url1, SVNURL url2, SVNEntry entry, File target, 
                                SVNWCAccess access, boolean dryRun, boolean force, 
                                boolean recordOnly, ISVNConflictHandler conflictHandler) throws SVNException {
        Merger merger = new Merger();
        merger.myURL = url2;
        merger.myTarget = target;
        merger.myIsForce = force;
        merger.myIsDryRun = dryRun;
        merger.myIsRecordOnly = recordOnly;
        merger.myOperativeNotificationsNumber = 0;
        merger.myWCAccess = access;
        merger.myConflictHandler = conflictHandler; 
        
        if (dryRun) {
            merger.myIsSameRepository = false;
        } else {
            SVNRepository repos = createRepository(url1, true);
            SVNURL reposRoot = repos.getRepositoryRoot(true);
            merger.myIsSameRepository = SVNPathUtil.isAncestor(reposRoot.toDecodedString(), 
                                                               entry.getRepositoryRootURL().toDecodedString());
        }
        return merger;
    }

    private static Map computePropsDiff(Map props1, Map props2) {
        Map propsDiff = new HashMap();
        for (Iterator names = props2.keySet().iterator(); names.hasNext();) {
            String newPropName = (String) names.next();
            if (props1.containsKey(newPropName)) {
                // changed.
                Object oldValue = props2.get(newPropName);
                if (!oldValue.equals(props1.get(newPropName))) {
                    propsDiff.put(newPropName, props2.get(newPropName));
                }
            } else {
                // added.
                propsDiff.put(newPropName, props2.get(newPropName));
            }
        }
        for (Iterator names = props1.keySet().iterator(); names.hasNext();) {
            String oldPropName = (String) names.next();
            if (!props2.containsKey(oldPropName)) {
                // deleted
                propsDiff.put(oldPropName, null);
            }
        }
        return propsDiff;
    }

    private static Map filterProperties(Map props1, boolean leftRegular,
            boolean leftEntry, boolean leftWC) {
        Map result = new HashMap();
        for (Iterator names = props1.keySet().iterator(); names.hasNext();) {
            String propName = (String) names.next();
            if (!leftEntry && propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                continue;
            }
            if (!leftWC && propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                continue;
            }
            if (!leftRegular
                    && !(propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX) || propName
                            .startsWith(SVNProperty.SVN_WC_PREFIX))) {
                continue;
            }
            result.put(propName, props1.get(propName));
        }
        return result;
    }
    
    private class Merger implements ISVNEventHandler {
        boolean myIsSameURLs;
        boolean myIsSameRepository;
        boolean myIsDryRun;
        boolean myIsRecordOnly;
        boolean myIsForce;
        boolean myHasMissingChildren;
        boolean myHasExistingMergeInfo;
        int myOperativeNotificationsNumber;
        SVNURL myURL;
        File myTarget;
        LinkedList mySkippedPaths;
        SVNWCAccess myWCAccess;
        ISVNConflictHandler myConflictHandler;
        
        public void doMergeFile(SVNURL url1, SVNRevision revision1, SVNURL url2, 
                                SVNRevision revision2, File dstPath, 
                                SVNAdminArea adminArea, boolean ignoreAncestry) throws SVNException {

            if (!revision1.isValid() || !revision2.isValid()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                                                             "Not all required revisions are specified");
                SVNErrorManager.error(err);
            }

            myWCAccess.probeTry(dstPath, true, -1);
            SVNEntry entry = myWCAccess.getVersionedEntry(dstPath, false);
            
            boolean isReplace = false;
            if (!ignoreAncestry) {
                try {
                    getLocations(url2, null, null, revision2, revision1, SVNRevision.UNDEFINED);
                } catch (SVNException svne) {
                    if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.CLIENT_UNRELATED_RESOURCES) {
                        isReplace = true;
                    } else {
                        throw svne;
                    }
                }
            }
            
            myIsSameURLs = url1.equals(url2);
            if (!myIsSameURLs && myIsRecordOnly) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
                                                             "Use of two URLs is not compatible with mergeinfo modification"); 
                SVNErrorManager.error(err);
            }

            SVNRepository repository1 = createRepository(url1, true);
            SVNRepository repository2 = createRepository(url2, false);
            
            try {
                Object[] mergeActionInfo = grokRangeinfoFromRevisions(repository1, revision1, 
                                                                      repository2, revision2);
                
                SVNMergeRange range = (SVNMergeRange) mergeActionInfo[0];
                MergeAction action = (MergeAction) mergeActionInfo[1];
               
                boolean isIndirect = false; 
                boolean isRollBack = false;
                Map targetMergeInfo = new TreeMap();
                SVNMergeRangeList remainingRangeList = null;
                String reposPath = null;
                if (myIsSameURLs) {
                    if (action == MergeAction.NO_OP) {
                        return;
                    }
                    isIndirect = getWCOrRepositoryMergeInfo(myWCAccess, targetMergeInfo, dstPath, entry, 
                                                            SVNMergeInfoInheritance.INHERITED, 
                                                            isIndirect, false, repository1);
                    
                    isRollBack = action == MergeAction.ROLL_BACK;
                    SVNURL reposRoot = repository1.getRepositoryRoot(true);
                    reposPath = url1.getPath().substring(reposRoot.getPath().length());
                    if (!reposPath.startsWith("/")) {
                        reposPath = "/" + reposPath;
                    }
                    
                    if (myIsRecordOnly) {
                        if (myIsDryRun || !myIsSameRepository) {
                            return;
                        } 
    
                        Map merges = determineMerges(dstPath, range);
                        if (isIndirect) {
                            String mergeInfoStr = targetMergeInfo.isEmpty() ? null 
                                                                            : SVNMergeInfoManager.formatMergeInfoToString(targetMergeInfo);
                            SVNPropertiesManager.setProperty(myWCAccess, dstPath, 
                                                             SVNProperty.MERGE_INFO, 
                                                             mergeInfoStr, true);
                                
                        }
                        updateWCMergeInfo(dstPath, reposPath, entry, merges, isRollBack);
                        return;
                    }
                
                    SVNMergeRangeList requestedRangeList = calculateRequestedRanges(range,
                                                                                    url1,
                                                                                    entry, 
                                                                                    repository1);
                    remainingRangeList = calculateMergeRanges(reposPath, requestedRangeList, 
                                                              targetMergeInfo, isRollBack);
                } else {
                    isRollBack = false;
                    remainingRangeList = new SVNMergeRangeList(new SVNMergeRange[] {range});
                }
    
                SVNMergeRange[] remainingRanges = remainingRangeList.getRanges();
                SVNMergeCallback callback = new SVNMergeCallback(adminArea, myURL, myIsForce, 
                                                                 myIsDryRun, getMergeOptions(),
                                                                 myConflictHandler);
    
                for (int i = 0; i < remainingRanges.length; i++) {
                    SVNMergeRange nextRange = remainingRanges[i];
                    this.handleEvent(SVNEventFactory.createMergeBeginEvent(dstPath, nextRange), 
                                     ISVNEventHandler.UNKNOWN);
                    
                    Map props1 = new HashMap();
                    Map props2 = new HashMap();
                    File f1 = null;
                    File f2 = null;
    
                    String name = dstPath.getName();
                    String mimeType2;
                    String mimeType1;
                    SVNStatusType[] mergeResult;
    
                    try {
                        f1 = loadFile(repository1, nextRange.getStartRevision(), props1, adminArea);
                        f2 = loadFile(repository2, nextRange.getEndRevision(), props2, adminArea);
    
                        mimeType1 = (String) props1.get(SVNProperty.MIME_TYPE);
                        mimeType2 = (String) props2.get(SVNProperty.MIME_TYPE);
                        props1 = filterProperties(props1, true, false, false);
                        props2 = filterProperties(props2, true, false, false);
    
                        Map propsDiff = computePropsDiff(props1, props2);
                        
                        if (isReplace) {
                            SVNStatusType cstatus = callback.fileDeleted(name, f1, f2, mimeType1, 
                                                                         mimeType2, props1);
                            notifyFileMerge(adminArea, name, SVNEventAction.UPDATE_DELETE, cstatus, 
                                            SVNStatusType.UNKNOWN, null);
                            
                            mergeResult = callback.fileAdded(name, f1, f2, nextRange.getStartRevision(), 
                                                             nextRange.getEndRevision(), mimeType1, mimeType2, 
                                                             props1, propsDiff);
                            
                            notifyFileMerge(adminArea, name, SVNEventAction.UPDATE_ADD, 
                                            mergeResult[0], mergeResult[1], null);
                        } else {
                            mergeResult = callback.fileChanged(name, f1, f2, nextRange.getStartRevision(), 
                                                               nextRange.getEndRevision(), mimeType1, 
                                                               mimeType2, props1, propsDiff);
                            notifyFileMerge(adminArea, name, SVNEventAction.UPDATE_UPDATE, 
                                            mergeResult[0], mergeResult[1], mimeType2);
                        }
                                
                        if (myIsSameURLs) {
                            if (!myIsDryRun && myIsSameRepository) {
                                Map merges = determineMerges(dstPath, nextRange);
                                if (i == 0 && isIndirect) {
                                    String mergeInfoStr = null;
                                    if (!merges.isEmpty()) {
                                        mergeInfoStr = SVNMergeInfoManager.formatMergeInfoToString(targetMergeInfo);
                                    }
                                    SVNPropertiesManager.setProperty(myWCAccess, dstPath, 
                                                                     SVNProperty.MERGE_INFO, 
                                                                     mergeInfoStr, true);
                                }
                                updateWCMergeInfo(dstPath, reposPath, entry, merges, isRollBack);
                            }
    
                            if (mySkippedPaths != null) {
                                mySkippedPaths.clear();
                            }
                        }
                    } finally {
                        SVNFileUtil.deleteAll(f1, null);
                        SVNFileUtil.deleteAll(f2, null);
                    }
                }
            } finally {
                repository2.closeSession();
            }
            
            if (!myIsDryRun) {
                String targetPath = myTarget.getAbsolutePath();
                targetPath = targetPath.replace(File.separatorChar, '/');
                
                String dst = dstPath.getAbsolutePath();
                dst = dst.replace(File.separatorChar, '/');
                if (dst.startsWith(targetPath)) {
                    int targetCount = SVNPathUtil.getSegmentsCount(dst);
                    int mergeTargetCount = SVNPathUtil.getSegmentsCount(targetPath);
                    if (targetCount - mergeTargetCount > 1) {
                        File elisionLimitPath = dstPath;
                        for (int i = 0; i < targetCount - mergeTargetCount - 1; i++) {
                            elisionLimitPath = elisionLimitPath.getParentFile();
                        }
                        elideMergeInfo(myWCAccess, dstPath, false, elisionLimitPath);
                    }
                }
            }
            sleepForTimeStamp();
        }
    
        public void doMerge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, 
                             final File dstPath, SVNAdminArea adminArea, 
                             SVNDepth depth, final LinkedList childrenWithMergeInfo, 
                             boolean useAncestry, boolean targetMissingChild, int targetIndex) throws SVNException {
            if (!revision1.isValid() || !revision2.isValid()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
                SVNErrorManager.error(err);
            }
            
            myIsSameURLs = url1.equals(url2);
            if (!myIsSameURLs && myIsRecordOnly) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
                                                             "Use of two URLs is not compatible with mergeinfo modification"); 
                SVNErrorManager.error(err);
            }

            SVNEntry entry = myWCAccess.getVersionedEntry(dstPath, false);
            SVNRepository repository1 = createRepository(url1, true);
            Object[] mergeActionInfo = grokRangeinfoFromRevisions(repository1, revision1, 
                                                                  repository1, revision2);

            SVNMergeRange range = (SVNMergeRange) mergeActionInfo[0];
            range.setInheritable(!targetMissingChild);
            MergeAction mergeAction = (MergeAction) mergeActionInfo[1];
            if (mergeAction == MergeAction.NO_OP) {
                return;
            }

            SVNRepository repository2 = createRepository(url1, false);
            boolean isIndirect = false;
            boolean isRollBack = false;
            String reposPath = null;
            SVNMergeRangeList remainingRangeList = null;
            Map targetMergeInfo = new TreeMap();
            if (myIsSameURLs) {
                isIndirect = getWCOrRepositoryMergeInfo(myWCAccess, targetMergeInfo, dstPath, entry, 
                                                        SVNMergeInfoInheritance.INHERITED, 
                                                        isIndirect, false, repository1);
                isRollBack = mergeAction == MergeAction.ROLL_BACK;

                SVNURL reposRoot = repository1.getRepositoryRoot(true);
                reposPath = url1.getPath().substring(reposRoot.getPath().length());
                if (!reposPath.startsWith("/")) {
                    reposPath = "/" + reposPath;
                }
                
                if (myIsRecordOnly) {
                    if (myIsDryRun || !myIsSameRepository) {
                        return;
                    } 
                    
                    Map merges = determineMerges(dstPath, range);
                    if (isIndirect) {
                        String mergeInfoStr = targetMergeInfo.isEmpty() ? null 
                                                                        : SVNMergeInfoManager.formatMergeInfoToString(targetMergeInfo);
                        SVNPropertiesManager.setProperty(myWCAccess, dstPath, 
                                                         SVNProperty.MERGE_INFO, 
                                                         mergeInfoStr, true);

                    }
                    updateWCMergeInfo(dstPath, reposPath, entry, merges, isRollBack);
                    return;
                }
                
                SVNMergeRangeList requestedRangeList = calculateRequestedRanges(range,  
                                                                                url1,
                                                                                entry, 
                                                                                repository1);
                remainingRangeList = calculateMergeRanges(reposPath, requestedRangeList, 
                                                          targetMergeInfo, isRollBack);
            } else {
                isRollBack = false;
                remainingRangeList = new SVNMergeRangeList(new SVNMergeRange[] {range});
            }

            SVNMergeRange[] remainingRanges = remainingRangeList.getRanges();
            SVNMergeCallback callback = new SVNMergeCallback(adminArea, myURL, myIsForce, myIsDryRun, 
                                                             getMergeOptions(), myConflictHandler);

            SVNRemoteDiffEditor editor = null; 
            final String targetPath = dstPath.getAbsolutePath().replace(File.separatorChar, '/');

            try {
                for (int i = 0; i < remainingRanges.length; i++) {
                    SVNMergeRange nextRange = remainingRanges[i];
                    this.handleEvent(SVNEventFactory.createMergeBeginEvent(dstPath, nextRange), 
                                     ISVNEventHandler.UNKNOWN);
                    
                    final long rev1 = nextRange.getStartRevision();
                    final long rev2 = nextRange.getEndRevision();
                    
                    if (editor == null) {
                        editor = new SVNRemoteDiffEditor(adminArea, adminArea.getRoot(), 
                                                         callback, repository2, 
                                                         rev1, 
                                                         rev2, 
                                                         myIsDryRun, this, this);
    
                    } else {
                        editor.reset(rev1, rev2);
                    }
    
                    final SVNDepth reportDepth = depth;
                    final boolean isSameURLs = myIsSameURLs;
                    try {
                        repository1.diff(url2, rev2, rev1, null, !useAncestry, depth, true,
                                         new ISVNReporterBaton() {
                                             public void report(ISVNReporter reporter) throws SVNException {
                                                 reporter.setPath("", null, rev1, reportDepth, false);
                                                 if (isSameURLs && childrenWithMergeInfo != null &&
                                                     !childrenWithMergeInfo.isEmpty()) {
    
                                                     for (Iterator paths = childrenWithMergeInfo.iterator(); paths.hasNext();) {
                                                        MergePath childMergePath = (MergePath) paths.next();
                                                        if (childMergePath == null) {
                                                            continue;
                                                        }
                                                       
                                                        String childPath = childMergePath.myPath.getAbsolutePath();
                                                        childPath = childPath.replace(File.separatorChar, '/');
                                                        if (!dstPath.equals(childMergePath.myPath) && 
                                                            SVNPathUtil.isAncestor(targetPath, childPath)) {
                                                            String relChildPath = childPath.substring(targetPath.length());
                                                            if (relChildPath.startsWith("/")) {
                                                                relChildPath = relChildPath.substring(1);
                                                            }
                                                            reporter.setPath(relChildPath, null, rev2, reportDepth, false);
                                                        }
                                                     }
                                                 }
                                                 reporter.finishReport();
                                             }
                                         }, 
                                         SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
                    } finally {
                        editor.cleanup();
                    }
    
                    if (myIsSameURLs) {
                        if (!myIsDryRun && myIsSameRepository) {
                            Map merges = determineMerges(dstPath, nextRange);
                            if (i == 0 && isIndirect) {
                                String mergeInfoStr = null;
                                if (!merges.isEmpty()) {
                                    mergeInfoStr = SVNMergeInfoManager.formatMergeInfoToString(targetMergeInfo);
                                }
                                SVNPropertiesManager.setProperty(myWCAccess, dstPath, 
                                                                 SVNProperty.MERGE_INFO, 
                                                                 mergeInfoStr, true);
                            }
                            updateWCMergeInfo(dstPath, reposPath, entry, merges, isRollBack);
                        }
                        if (mySkippedPaths != null) {
                            mySkippedPaths.clear();
                        }
                    }
                }            
            } finally {
                repository2.closeSession();
            }
            
            if (myIsSameURLs && !myIsDryRun && myIsSameRepository && targetIndex > 0) {
                MergePath mergePath = (MergePath) childrenWithMergeInfo.get(targetIndex);
                if (mergePath.myHasNonInheritableMergeInfo && !mergePath.myHasMissingChildren) {
                    SVNMergeRangeList inheritableRangeList = new SVNMergeRangeList(new SVNMergeRange[] {range});
                    Map inheritableMerges = new TreeMap();
                    inheritableMerges.put(reposPath, inheritableRangeList);
                    Map merges = SVNMergeInfoManager.getInheritableMergeInfo(targetMergeInfo, 
                                                                             reposPath, 
                                                                             range.getStartRevision(), 
                                                                             range.getEndRevision());
                    if (!SVNMergeInfoManager.mergeInfoEquals(merges, targetMergeInfo, 
                                                             SVNMergeRangeInheritance.IGNORE_INHERITANCE)) {
                        merges = SVNMergeInfoManager.mergeMergeInfos(merges, inheritableMerges, 
                                                                     SVNMergeRangeInheritance.EQUAL_INHERITANCE);
                    
                        String mergeInfoStr = null;
                        if (!merges.isEmpty()) {
                            mergeInfoStr = SVNMergeInfoManager.formatMergeInfoToString(merges);
                        }
                        
                        SVNPropertiesManager.setProperty(myWCAccess, dstPath, 
                                                         SVNProperty.MERGE_INFO, 
                                                         mergeInfoStr, true);
                    }
                }
            }
            
            if (!myIsDryRun) {
                String mergeTargetPath = myTarget.getAbsolutePath();
                mergeTargetPath = mergeTargetPath.replace(File.separatorChar, '/');
                if (targetPath.startsWith(mergeTargetPath)) {
                    int targetCount = SVNPathUtil.getSegmentsCount(targetPath);
                    int mergeTargetCount = SVNPathUtil.getSegmentsCount(mergeTargetPath);
                    if (targetCount - mergeTargetCount > 1) {
                        File elisionLimitPath = dstPath;
                        for (int i = 0; i < targetCount - mergeTargetCount - 1; i++) {
                            elisionLimitPath = elisionLimitPath.getParentFile();
                        }
                        elideMergeInfo(myWCAccess, dstPath, false, elisionLimitPath);
                    }
                }
            }
            sleepForTimeStamp();
        }

        public LinkedList discoverAndMergeChildren(SVNEntry parentEntry, SVNRevision revision1, 
                                                   SVNRevision revision2, SVNDepth depth,
                                                   SVNURL parentMergeSourceURL,
                                                   SVNURL wcRootURL,
                                                   boolean ignoreAncestry) throws SVNException {
            
            String parentMergeSourcePath = parentMergeSourceURL.getPath();
            String wcRootPath = wcRootURL.getPath();
            String mergeSourcePath = parentMergeSourcePath.substring(wcRootPath.length()); 
            LinkedList childrenWithMergeInfo = getMergeInfoPaths(parentEntry,
                                                                 myTarget,
                                                                 mergeSourcePath);
            
            final Map deletedPaths = new HashMap();
            ISVNDiffStatusHandler handler = new ISVNDiffStatusHandler() {
                public void handleDiffStatus(SVNDiffStatus diffStatus) throws SVNException {
                    if (diffStatus.getModificationType() == SVNStatusType.STATUS_DELETED) {
                        deletedPaths.put(diffStatus.getFile(), diffStatus.getFile());
                    }
                }
            };
                
            doDiffStatus(parentMergeSourceURL, revision1, revision2, SVNRevision.HEAD, 
                         depth, !ignoreAncestry, handler);

            for (ListIterator childrenMergePaths = childrenWithMergeInfo.listIterator(); 
                 childrenMergePaths.hasNext();) {
                int ind = childrenMergePaths.nextIndex();
                MergePath childMergePath = (MergePath) childrenMergePaths.next();
                if (myTarget.equals(childMergePath.myPath)) {
                    if (childMergePath.myHasMissingChildren) {
                        myHasMissingChildren = true;
                    }
                    myHasExistingMergeInfo = true;
                    continue;
                }
                
                if (deletedPaths.containsKey(childMergePath.myPath)) {
                    childrenMergePaths.remove();
                    continue;
                }
                
                SVNEntry childEntry = myWCAccess.getVersionedEntry(childMergePath.myPath, false);
                String relPath = SVNPathUtil.getRelativePath(myTarget, childMergePath.myPath);
                
                SVNURL childURL = parentMergeSourceURL.appendPath(relPath, false);
                SVNAdminArea adminArea = childEntry.getAdminArea();
                if (childEntry.isFile()) {
                    doMergeFile(childURL, revision1, childURL, revision2, childMergePath.myPath, 
                                adminArea, ignoreAncestry);
                } else if (childEntry.isDirectory()) {
                    doMerge(childURL, revision1, childURL, revision2, childMergePath.myPath, 
                            adminArea, depth, childrenWithMergeInfo, !ignoreAncestry, 
                            childMergePath.myHasMissingChildren, ind);
                }
            }
            
            return childrenWithMergeInfo;
        }
        
        public void elideChildren(LinkedList childrenWithMergeInfo, File dstPath, SVNEntry entry) throws SVNException {
            if (childrenWithMergeInfo != null && !childrenWithMergeInfo.isEmpty()) {
                Map filesToValues = SVNPropertiesManager.getWorkingCopyPropertyValues(entry.getAdminArea(), 
                                                                                      entry.getName(), 
                                                                                      SVNProperty.MERGE_INFO, 
                                                                                      false, false); 
                String mergeInfoStr = (String) filesToValues.get(dstPath);
                Map targetMergeInfo = null;
                if (mergeInfoStr != null) {
                    targetMergeInfo = SVNMergeInfoManager.parseMergeInfo(new StringBuffer(mergeInfoStr), 
                                                                         null);
                }
                File lastImmediateChild = null;
                for (ListIterator childrenMergePaths = childrenWithMergeInfo.listIterator(); 
                     childrenMergePaths.hasNext();) {
                    boolean isFirst = !childrenMergePaths.hasPrevious(); 
                    MergePath childMergePath = (MergePath) childrenMergePaths.next();
                    if (childMergePath == null) {
                        continue;
                    }
                    if (isFirst) {
                        if (childMergePath.myPath.equals(dstPath)) {
                            lastImmediateChild = null;
                            continue;
                        }
                        lastImmediateChild = childMergePath.myPath;
                    } else if (lastImmediateChild != null) {
                        String lastImmediateChildPath = lastImmediateChild.getAbsolutePath();
                        lastImmediateChildPath = lastImmediateChildPath.replace(File.separatorChar, 
                                                                                '/');
                        String childPath = childMergePath.myPath.getAbsolutePath();
                        childPath = childPath.replace(File.separatorChar, '/');
                        if (SVNPathUtil.isAncestor(lastImmediateChildPath, childPath)) {
                            continue;
                        }
                        lastImmediateChild = childMergePath.myPath;
                    } else {
                        lastImmediateChild = childMergePath.myPath;
                    }
                    
                    SVNEntry childEntry = myWCAccess.getVersionedEntry(childMergePath.myPath, false);
                    SVNAdminArea adminArea = childEntry.getAdminArea();
                    boolean isSwitched = adminArea.isEntrySwitched(childEntry); 
                    if (!isSwitched) {
                        Map childFileToValue = SVNPropertiesManager.getWorkingCopyPropertyValues(childEntry.getAdminArea(), 
                                                                                                 childEntry.getName(), 
                                                                                                 SVNProperty.MERGE_INFO, 
                                                                                                 false, false); 
                        String childMergeInfoStr = (String) childFileToValue.get(childMergePath.myPath);
                        Map childMergeInfo = null;
                        if (childMergeInfoStr != null) {
                            childMergeInfo = SVNMergeInfoManager.parseMergeInfo(new StringBuffer(childMergeInfoStr), 
                                                                                null);
                        }
                        
                        String childRelPath = childMergePath.myPath.getName();
                        File childParent = childMergePath.myPath.getParentFile();
                        while (!dstPath.equals(childParent)) {
                            childRelPath = SVNPathUtil.append(childParent.getName(), childRelPath);
                            childParent = childParent.getParentFile();
                        }
                        
                        SVNMergeInfoManager.elideMergeInfo(targetMergeInfo, childMergeInfo, 
                                                           childMergePath.myPath, childRelPath, 
                                                           myWCAccess);
                    }
                }
            }
        }
        
        public void handleEvent(SVNEvent event, double progress) throws SVNException {
            if (myIsSameURLs) {
                if (event.getContentsStatus() == SVNStatusType.CONFLICTED || 
                    event.getContentsStatus() == SVNStatusType.MERGED ||
                    event.getContentsStatus() == SVNStatusType.CHANGED ||
                    event.getPropertiesStatus() == SVNStatusType.CONFLICTED ||
                    event.getPropertiesStatus() == SVNStatusType.MERGED ||
                    event.getPropertiesStatus() == SVNStatusType.CHANGED) {
                    myOperativeNotificationsNumber++;
                }
                if (event.getAction() == SVNEventAction.SKIP) {
                    File skippedPath = event.getFile();
                    if (mySkippedPaths == null) {
                        mySkippedPaths = new LinkedList();
                    }
                    mySkippedPaths.add(skippedPath);
                }
            }
            
            SVNDiffClient.this.handleEvent(event, progress);
        }

        public void checkCancelled() throws SVNCancelException {
            SVNDiffClient.this.checkCancelled();
        }

        private LinkedList getMergeInfoPaths(SVNEntry entry, final File target, 
                                             final String mergeSrcPath) throws SVNException {
            SVNAdminArea adminArea = entry.getAdminArea();
            final LinkedList children = new LinkedList();
            ISVNEntryHandler handler = new ISVNEntryHandler() {
                public void handleEntry(File path, SVNEntry entry, SVNAdminArea adminArea) throws SVNException {
                    if (entry.isScheduledForDeletion()) {
                        return;
                    }
                    
                    SVNVersionedProperties props = adminArea.getProperties(entry.getName());
                    String mergeInfoProp = props.getPropertyValue(SVNProperty.MERGE_INFO);
                    boolean isSwitched = false;
                    boolean hasMergeInfoFromMergeSrc = false;
                    if (mergeInfoProp != null) {
                        String relToTargetPath = SVNPathUtil.getRelativePath(target, path);
                        String mergeSrcChildPath = SVNPathUtil.concatToAbs(mergeSrcPath, 
                                                                           relToTargetPath);
                        Map mergeInfo = SVNMergeInfoManager.parseMergeInfo(new StringBuffer(mergeInfoProp), 
                                                                           null);
                        if (mergeInfo.containsKey(mergeSrcChildPath)) {
                            hasMergeInfoFromMergeSrc = true;
                        }
                    } 
                    
                    isSwitched = adminArea.isEntrySwitched(entry);
                    
                    if (hasMergeInfoFromMergeSrc || isSwitched) {
                        boolean hasNonInheritable = mergeInfoProp != null && 
                                                    mergeInfoProp.indexOf(SVNMergeRangeList.MERGE_INFO_NONINHERITABLE_STRING) != -1; 
                        MergePath child = new MergePath(path, false, isSwitched, hasNonInheritable);
                        children.add(child);
                    }
                }
                
                public void handleError(File path, SVNErrorMessage error) throws SVNException {
                    while (error.hasChildErrorMessage()) {
                        error = error.getChildErrorMessage();
                    }
                    if (error.getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                        return;
                    }
                    SVNErrorManager.error(error);
                }
            };
            
            adminArea.walkEntries(entry.getName(), handler, false, true);
            
            for (ListIterator mergePaths = children.listIterator(); mergePaths.hasNext();) {
                MergePath child = (MergePath) mergePaths.next();
                
                if (child.myHasNonInheritableMergeInfo) {
                    SVNAdminArea childArea = myWCAccess.probeTry(child.myPath, true, 
                                                                 SVNWCAccess.INFINITE_DEPTH);
                    
                    for (Iterator entries = childArea.entries(false); entries.hasNext();) {
                        SVNEntry childEntry = (SVNEntry) entries.next();
                        if (childArea.getThisDirName().equals(childEntry.getName())) {
                            continue;
                        }
                        
                        File childPath = childArea.getFile(childEntry.getName()); 
                        if (!children.contains(new MergePath(childPath))) {
                            MergePath childOfNonInheriatable = new MergePath(childPath, false, 
                                                                             false, false);
                            mergePaths.add(childOfNonInheriatable);
                            if (!myIsDryRun && myIsSameRepository) {
                                Map mergeInfo = new TreeMap(); 
                                getWCMergeInfo(mergeInfo, childPath, entry, myTarget, 
                                               SVNMergeInfoInheritance.NEAREST_ANCESTOR, 
                                               false);
                                
                                String mergeInfoStr = null;
                                if (mergeInfo != null && !mergeInfo.isEmpty()) {
                                    mergeInfoStr = SVNMergeInfoManager.formatMergeInfoToString(mergeInfo);
                                }
                                
                                SVNPropertiesManager.setProperty(myWCAccess, childPath, 
                                                                 SVNProperty.MERGE_INFO, 
                                                                 mergeInfoStr, true);
                            }
                        }
                    }
                }
                
                if (child.myIsSwitched && !myTarget.equals(child.myPath)) {
                    File parentPath = child.myPath.getParentFile();
                    int parentInd = children.indexOf(new MergePath(parentPath));
                    MergePath parent = parentInd != -1 ? (MergePath) children.get(parentInd)
                                                       : null;
                    if (parent != null) {
                        parent.myHasMissingChildren = true; 
                    } else {
                        parent = new MergePath(parentPath, true, false, false);
                        mergePaths.add(parent);
                    }
                    
                    SVNAdminArea parentArea = myWCAccess.probeTry(parentPath, true, 
                                                                  SVNWCAccess.INFINITE_DEPTH);
                    for (Iterator siblings = parentArea.entries(false); siblings.hasNext();) {
                        SVNEntry siblingEntry = (SVNEntry) siblings.next();
                        if (parentArea.getThisDirName().equals(siblingEntry.getName())) {
                            continue;
                        }
                        
                        File siblingPath = parentArea.getFile(siblingEntry.getName());
                        if (!children.contains(new MergePath(siblingPath))) {
                            MergePath siblingOfMissing = new MergePath(siblingPath, false, 
                                                                       false, false);
                            mergePaths.add(siblingOfMissing);
                        }
                    }
                }
            }
            Collections.sort(children);
            return children;
        }
        
        private void notifyFileMerge(SVNAdminArea adminArea, String name, SVNEventAction action, 
                                     SVNStatusType cstate, SVNStatusType pstate, String mimeType) throws SVNException {
            action = cstate == SVNStatusType.MISSING ? SVNEventAction.SKIP : action;
            SVNEvent event = SVNEventFactory.createUpdateModifiedEvent(adminArea, name, 
                                                                       SVNNodeKind.FILE, action,
                                                                       mimeType, cstate, pstate, 
                                                                       SVNStatusType.LOCK_INAPPLICABLE);
            this.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
        
        private Object[] grokRangeinfoFromRevisions(SVNRepository repos1, 
                                                    SVNRevision rev1, 
                                                    SVNRepository repos2, 
                                                    SVNRevision rev2) throws SVNException {
            long startRev = getRevisionNumber(rev1, repos1, null);
            long endRev = getRevisionNumber(rev2, repos2, null);
            MergeAction action = null;
            
            if (myIsSameURLs) {
                if (startRev < endRev) {
                    action = MergeAction.MERGE; 
                } else if (startRev > endRev) {
                    action = MergeAction.ROLL_BACK;
                } else {
                    action = MergeAction.NO_OP;
                    startRev = endRev = SVNRepository.INVALID_REVISION;
                }
            } else {
                action = MergeAction.MERGE;
            }
            
            SVNMergeRange range = new SVNMergeRange(startRev, endRev, true);
            return new Object[] {range, action};
        }
     
        private Map determineMerges(File targetPath, SVNMergeRange range) {
            int numberOfSkippedPaths = mySkippedPaths != null ? mySkippedPaths.size() : 0;
            Map merges = new TreeMap();
            if (numberOfSkippedPaths == 0 || myOperativeNotificationsNumber > 0) {
                SVNMergeRangeList rangeList = new SVNMergeRangeList(new SVNMergeRange[] {range});
                merges.put(targetPath, rangeList);
                if (numberOfSkippedPaths > 0) {
                    for (Iterator skippedPaths = mySkippedPaths.iterator(); skippedPaths.hasNext();) {
                        File skippedPath = (File) skippedPaths.next();
                        merges.put(skippedPath, new SVNMergeRangeList(new SVNMergeRange[0]));
                        //TODO: numberOfSkippedPaths < myOperativeNotificationsNumber
                    }
                }
            }
            return merges;
        }
        
        private void updateWCMergeInfo(File targetPath, String parentReposPath, 
                                       SVNEntry entry, Map merges, boolean isRollBack) throws SVNException {
            
            for (Iterator paths = merges.keySet().iterator(); paths.hasNext();) {
                File path = (File) paths.next();
                SVNMergeRangeList ranges = (SVNMergeRangeList) merges.get(path);
                Map fileToProp = SVNPropertiesManager.getWorkingCopyPropertyValues(entry.getAdminArea(), 
                                                                                   entry.getName(), 
                                                                                   SVNProperty.MERGE_INFO, 
                                                                                   false, 
                                                                                   false); 

                String propValue = (String) fileToProp.get(path);
                Map mergeInfo = null;
                if (propValue != null) {
                    mergeInfo = SVNMergeInfoManager.parseMergeInfo(new StringBuffer(propValue), mergeInfo);
                }
                
                if (mergeInfo == null && ranges.getSize() == 0) {
                    mergeInfo = new TreeMap();
                    getWCMergeInfo(mergeInfo, path, entry, null, 
                                   SVNMergeInfoInheritance.NEAREST_ANCESTOR, true);
                }
                
                if (mergeInfo == null) {
                    mergeInfo = new TreeMap();
                }
                
                String parent = targetPath.getAbsolutePath();
                parent = parent.replace(File.separatorChar, '/');
                String child = path.getAbsolutePath();
                child = child.replace(File.separatorChar, '/');
                String reposPath = null;
                if (parent.length() < child.length()) {
                    String childRelPath = child.substring(parent.length());
                    if (childRelPath.startsWith("/")) {
                        childRelPath = childRelPath.substring(1);
                    }
                    reposPath = SVNPathUtil.concatToAbs(parentReposPath, childRelPath);
                } else {
                    reposPath = parentReposPath;
                }
                
                SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(reposPath);
                if (rangeList == null) {
                    rangeList = new SVNMergeRangeList(new SVNMergeRange[0]);
                }
                
                if (isRollBack) {
                    ranges = ranges.dup();
                    ranges = ranges.reverse();
                    rangeList = rangeList.diff(ranges, SVNMergeRangeInheritance.IGNORE_INHERITANCE);
                } else {
                    rangeList = rangeList.merge(ranges, SVNMergeRangeInheritance.IGNORE_INHERITANCE);
                }
                
                mergeInfo.put(reposPath, rangeList);
                //TODO: I do not understand this:) how mergeInfo can be ever empty here????
                if (isRollBack && mergeInfo.isEmpty()) {
                    mergeInfo = null;
                }
                
                String mergeInfoStr = null;
                if (mergeInfo != null && !mergeInfo.isEmpty()) {
                    mergeInfoStr = SVNMergeInfoManager.formatMergeInfoToString(mergeInfo);
                }
                
                try {
                    SVNPropertiesManager.setProperty(myWCAccess, path, SVNProperty.MERGE_INFO, 
                                                     mergeInfoStr, true);
                } catch (SVNException svne) {
                    if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.ENTRY_NOT_FOUND) {
                        throw svne;
                    }
                }
            }
        }
        
        private SVNMergeRangeList calculateRequestedRanges(SVNMergeRange unrefinedRange, 
                                                           SVNURL srcURL,                                           
                                                           SVNEntry entry,
                                                           SVNRepository repository) throws SVNException {
            SVNURL reposRoot = entry.getRepositoryRootURL();
            if (reposRoot == null) {
                reposRoot = repository.getRepositoryRoot(true);
            }
            String reposPath = srcURL.getPath().substring(reposRoot.getPath().length());
            if (!reposPath.startsWith("/")) {
                reposPath = "/" + reposPath;
            }
            
            long minRevision = Math.min(unrefinedRange.getStartRevision(), 
                                        unrefinedRange.getEndRevision());
            
            Map startMergeInfoMap = repository.getMergeInfo(new String[] {reposPath}, 
                                                            minRevision, 
                                                            SVNMergeInfoInheritance.INHERITED);
            SVNMergeInfo startMergeInfo = startMergeInfoMap != null ? 
                                          (SVNMergeInfo) startMergeInfoMap.get(reposPath) :
                                          null;
            long maxRevision = Math.max(unrefinedRange.getStartRevision(), 
                                        unrefinedRange.getEndRevision());
            
            Map endMergeInfoMap = repository.getMergeInfo(new String[] {reposPath}, 
                                                          maxRevision, 
                                                          SVNMergeInfoInheritance.INHERITED);
            SVNMergeInfo endMergeInfo = endMergeInfoMap != null ? 
                                        (SVNMergeInfo) endMergeInfoMap.get(reposPath) : 
                                        null;
            Map added = new HashMap();
            SVNMergeInfoManager.diffMergeInfo(null, added, 
                                              startMergeInfo != null ?     
                                              startMergeInfo.getMergeSourcesToMergeLists() :
                                              null, 
                                              endMergeInfo != null ?
                                              endMergeInfo.getMergeSourcesToMergeLists() :
                                              null, 
                                              SVNMergeRangeInheritance.EQUAL_INHERITANCE);

            SVNMergeRangeList srcRangeListForTgt = null;
            if (!added.isEmpty()) {
                String srcReposPath = entry.getSVNURL().getPath().substring(reposRoot.getPath().length());
                if (!srcReposPath.startsWith("/")) {
                    srcReposPath = "/" + srcReposPath;
                }
                srcRangeListForTgt = (SVNMergeRangeList) added.get(srcReposPath);
            }
            
            SVNMergeRangeList requestedRangeList = new SVNMergeRangeList(new SVNMergeRange[] {unrefinedRange});
            if (srcRangeListForTgt != null) {
                requestedRangeList = requestedRangeList.diff(srcRangeListForTgt, 
                                                             SVNMergeRangeInheritance.EQUAL_INHERITANCE);
            }
            return requestedRangeList;
        }
        
        private SVNMergeRangeList calculateMergeRanges(String reposPath, 
                                                       SVNMergeRangeList requestedRangeList,
                                                       Map targetMergeInfo,
                                                       boolean isRollBack) {
            if (isRollBack) {
                requestedRangeList = requestedRangeList.dup();
            }
            
            SVNMergeRangeList remainingRangeList = requestedRangeList;
            SVNMergeRangeList targetRangeList = null;
            if (targetMergeInfo != null) {
                targetRangeList = (SVNMergeRangeList) targetMergeInfo.get(reposPath);
            }
            
            if (targetRangeList != null) {
                if (isRollBack) {
                    requestedRangeList = requestedRangeList.reverse();
                    remainingRangeList = requestedRangeList.intersect(targetRangeList);
                    remainingRangeList = remainingRangeList.reverse();
                } else {
                    remainingRangeList = requestedRangeList.diff(targetRangeList, 
                                                                 SVNMergeRangeInheritance.IGNORE_INHERITANCE);
                    
                }
            }
            return remainingRangeList;
        }
        
        private File loadFile(SVNRepository repository, long revision, 
                              Map properties, SVNAdminArea adminArea) throws SVNException {
            File tmpDir = adminArea.getAdminTempDirectory();
            File result = SVNFileUtil.createUniqueFile(tmpDir, ".merge", ".tmp");
            SVNFileUtil.createEmptyFile(result);
            
            OutputStream os = null;
            try {
                os = SVNFileUtil.openFileForWriting(result); 
                repository.getFile("", revision, properties, 
                                   new SVNCancellableOutputStream(os, SVNDiffClient.this));
            } finally {
                SVNFileUtil.closeFile(os);
            }
            return result;
        }

    }
    
    private static class MergeAction {
        public static final MergeAction MERGE = new MergeAction();
        public static final MergeAction ROLL_BACK = new MergeAction();
        public static final MergeAction NO_OP = new MergeAction();
        
        private MergeAction() {
        }
    }
    
    private static class MergePath implements Comparable {
        File myPath;
        boolean myHasMissingChildren;
        boolean myIsSwitched;
        boolean myHasNonInheritableMergeInfo;
        
        public MergePath(File path) {
            myPath = path;
        }
        
        public MergePath(File path, boolean hasMissingChildren, boolean isSwitched, 
                         boolean hasNonInheritableMergeInfo) {
            myPath = path;
            myHasNonInheritableMergeInfo = hasNonInheritableMergeInfo;
            myIsSwitched = isSwitched;
            myHasMissingChildren = hasMissingChildren;
        }
        
        public int compareTo(Object obj) {
            if (obj == null || obj.getClass() != MergePath.class) {
                return -1;
            }
            MergePath mergePath = (MergePath) obj; 
            if (this == mergePath) {
                return 0;
            }
            return myPath.compareTo(mergePath.myPath);
        }
        
        public boolean equals(Object obj) {
            return compareTo(obj) == 0;
        }
        
        public String toString() {
            return myPath.toString();
        }
    }
    
}