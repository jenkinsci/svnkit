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
import org.tmatesoft.svn.core.internal.wc.SVNRemoteDiffEditor;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
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
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNDiffClient extends SVNBasicClient {

    private ISVNDiffGenerator myDiffGenerator;
    private SVNDiffOptions myDiffOptions;

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

    protected SVNDiffClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
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
        doDiffURLURL(url, null, rN, url, null, rM, pegRevision, recursive, useAncestry, result);
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
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        if (rN.isLocal() && rM.isLocal()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "At least one revision must be non-local for " +
                    "a pegged diff");            
            SVNErrorManager.error(err);
        }
        path = new File(SVNPathUtil.validateFilePath(path.getAbsolutePath())).getAbsoluteFile();
        getDiffGenerator().init(path.getAbsolutePath(), path.getAbsolutePath());
        if (!(rM == SVNRevision.BASE || rM == SVNRevision.WORKING || rM == SVNRevision.COMMITTED)) {
            doDiffURLURL(null, path, rN, null, path, rM, pegRevision, recursive, useAncestry, result);
        } else {
            // head, prev,date,number will go here.
            doDiffURLWC(path, rN, pegRevision, path, rM, false, recursive, useAncestry, result);
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
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        getDiffGenerator().init(url1.toString(), url2.toString());
        doDiffURLURL(url1, null, rN, url2, null, rM, SVNRevision.UNDEFINED, recursive, useAncestry, result);
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
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        getDiffGenerator().init(path1.getAbsolutePath(), url2.toString());
        if (rN == SVNRevision.BASE || rN == SVNRevision.WORKING) {
            doDiffURLWC(url2, rM, SVNRevision.UNDEFINED, path1, rN, true, recursive, useAncestry, result);
        } else {
            doDiffURLURL(null, path1, rN, url2, null, rM, SVNRevision.UNDEFINED, recursive, useAncestry, result);
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
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        getDiffGenerator().init(url1.toString(), path2.getAbsolutePath());
        if (rM == SVNRevision.BASE || rM == SVNRevision.WORKING) {
            doDiffURLWC(url1, rN, SVNRevision.UNDEFINED, path2, rM, false, recursive, useAncestry, result);
        } else {
            doDiffURLURL(url1, null, rN, null, path2, rM, SVNRevision.UNDEFINED, recursive, useAncestry, result);
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
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }

        boolean isPath1Local = rN == SVNRevision.WORKING || rN == SVNRevision.BASE; 
        boolean isPath2Local = rM == SVNRevision.WORKING || rM == SVNRevision.BASE;
        getDiffGenerator().init(path1.getAbsolutePath(), path2.getAbsolutePath());
        if (isPath1Local && isPath2Local) {
            doDiffWCWC(path1, rN, path2, rM, recursive, useAncestry, result);
        } else if (isPath1Local) {
            doDiffURLWC(path2, rM, SVNRevision.UNDEFINED, path1, rN, true, recursive, useAncestry, result);
        } else if (isPath2Local) {
            doDiffURLWC(path1, rN, SVNRevision.UNDEFINED, path2, rM, false, recursive, useAncestry, result);
        } else {
            doDiffURLURL(null, path1, rN, null, path2, rM, SVNRevision.UNDEFINED, recursive, useAncestry, result);
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
        if (handler == null) {
            return;
        }
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }

        boolean isPath1Local = rN == SVNRevision.WORKING || rN == SVNRevision.BASE; 
        boolean isPath2Local = rM == SVNRevision.WORKING || rM == SVNRevision.BASE;
        if (isPath1Local || isPath2Local) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Summarizing diff can only compare repository to repository");
            SVNErrorManager.error(err);
        } 
        doDiffURLURL(null, path1, rN, null, path2, rM, SVNRevision.UNDEFINED, recursive, useAncestry, handler);        
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
        if (handler == null) {
            return;
        }
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        if (rN == SVNRevision.BASE || rN == SVNRevision.WORKING) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Summarizing diff can only compare repository to repository");
            SVNErrorManager.error(err);
        } else {
            doDiffURLURL(null, path1, rN, url2, null, rM, SVNRevision.UNDEFINED, recursive, useAncestry, handler);
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
            doDiffURLURL(url1, null, rN, null, path2, rM, SVNRevision.UNDEFINED, recursive, useAncestry, handler);
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
        if (handler == null) {
            return;
        }
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        doDiffURLURL(url1, null, rN, url2, null, rM, SVNRevision.UNDEFINED, recursive, useAncestry, handler);
    }
    
    private void doDiffURLWC(SVNURL url1, SVNRevision revision1, SVNRevision pegRevision, File path2, SVNRevision revision2, 
            boolean reverse, boolean recursive, boolean useAncestry, OutputStream result) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminAreaInfo info = wcAccess.openAnchor(path2, false, recursive ? SVNWCAccess.INFINITE_DEPTH : 0);
            File anchorPath = info.getAnchor().getRoot();
            String target = "".equals(info.getTargetName()) ? null : info.getTargetName();
            
            SVNEntry anchorEntry = info.getAnchor().getEntry("", false);
            if (anchorEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", anchorPath);
                SVNErrorManager.error(err);
            } else if (anchorEntry.getURL() == null) {
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
            AbstractDiffCallback callback = new SVNDiffCallback(info, getDiffGenerator(), 
                    reverse ? -1 : revNumber, reverse ? revNumber : -1, result);
            SVNDiffEditor editor = new SVNDiffEditor(wcAccess, info, callback, 
                    useAncestry, reverse /* reverse */,
                    revision2 == SVNRevision.BASE  || revision2 == SVNRevision.COMMITTED  /* compare to base */, 
                    recursive);
            SVNReporter reporter = new SVNReporter(info, info.getAnchor().getFile(info.getTargetName()), false, recursive, getDebugLog());
            
            long pegRevisionNumber = getRevisionNumber(revision2, repository, path2);
            try {
                repository.diff(url1, revNumber, pegRevisionNumber, target, !useAncestry, recursive, true, reporter, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
            } finally {
                editor.cleanup();
            }
        } finally {
            wcAccess.close();
        }
    }

    private void doDiffURLWC(File path1, SVNRevision revision1, SVNRevision pegRevision, File path2, SVNRevision revision2, 
            boolean reverse, boolean recursive, boolean useAncestry, OutputStream result) throws SVNException {
        
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminAreaInfo info = wcAccess.openAnchor(path2, false, recursive ? SVNWCAccess.INFINITE_DEPTH : 0);
            
            File anchorPath = info.getAnchor().getRoot();
            String target = "".equals(info.getTargetName()) ? null : info.getTargetName();
            
            SVNEntry anchorEntry = info.getAnchor().getEntry("", false);
            if (anchorEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", anchorPath);
                SVNErrorManager.error(err);
            } else if (anchorEntry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", anchorPath);
                SVNErrorManager.error(err);
            }
            SVNURL url1;
            SVNURL anchorURL = anchorEntry.getSVNURL();
            if (pegRevision.isValid()) {
                SVNRepositoryLocation[] locations = getLocations(null, path1, null, pegRevision, revision1, SVNRevision.UNDEFINED);
                url1 = locations[0].getURL();
                String anchorPath2 = SVNPathUtil.append(anchorURL.toString(), target == null ? "" : target);
                getDiffGenerator().init(url1.toString(), anchorPath2);
            } else {
                url1 = getURL(path1);
            }
            SVNRepository repository = createRepository(anchorURL, true);
            long revNumber = getRevisionNumber(revision1, repository, path1);
            AbstractDiffCallback callback = new SVNDiffCallback(info, getDiffGenerator(), 
                    reverse ? -1 : revNumber, reverse ? revNumber : -1, result);
            SVNDiffEditor editor = new SVNDiffEditor(wcAccess, info, callback,
                    useAncestry, 
                    reverse /* reverse */, 
                    revision2 == SVNRevision.BASE || revision2 == SVNRevision.COMMITTED /* compare to base */, 
                    recursive);
            SVNReporter reporter = new SVNReporter(info, info.getAnchor().getFile(info.getTargetName()), false, recursive, getDebugLog());
            
            // this should be rev2.
            long pegRevisionNumber = getRevisionNumber(revision2, repository, path2);
            try {
                repository.diff(url1, revNumber, pegRevisionNumber, target, !useAncestry, recursive, true, reporter, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
            } finally {
                editor.cleanup();
            }
        } finally {
            wcAccess.close();
        }
    }
    
    private void doDiffWCWC(File path1, SVNRevision revision1, File path2, SVNRevision revision2, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        if (!path1.equals(path2) || !(revision1 == SVNRevision.BASE && revision2 == SVNRevision.WORKING)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Only diffs between a path's text-base " +
                                    "and its working files are supported at this time (-rBASE:WORKING)");
            SVNErrorManager.error(err);
        }
        
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminAreaInfo info = wcAccess.openAnchor(path1, false, recursive ? SVNWCAccess.INFINITE_DEPTH: 0);
            SVNEntry entry = wcAccess.getEntry(path1, false);
            if (entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", path1);
                SVNErrorManager.error(err);
            }
            long rev = getRevisionNumber(revision1, null, path1);
            AbstractDiffCallback callback = new SVNDiffCallback(info, getDiffGenerator(), 
                    rev, -1, result);
            SVNDiffEditor editor = new SVNDiffEditor(wcAccess, info, callback, useAncestry, false, false, recursive);
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
            boolean recursive, boolean useAncestry, OutputStream result) throws SVNException {
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
        long rev2 = getRevisionNumber(revision2, repository2, path2);
        
        SVNNodeKind kind1 = repository1.checkPath("", rev1);
        SVNNodeKind kind2 = repository2.checkPath("", rev2);
        String target1 = null;
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
        repository2 = createRepository(url1, false); 
        SVNRemoteDiffEditor editor = null;
        try {
            SVNDiffCallback callback = new SVNDiffCallback(null, getDiffGenerator(), rev1, rev2, result);
            callback.setBasePath(basePath);
            editor = new SVNRemoteDiffEditor(null, null, callback, repository2, rev1, rev2, false, null, this);
            ISVNReporterBaton reporter = new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, rev1, false);
                    reporter.finishReport();
                }
            };
            repository1.diff(url2, rev2, rev1, target1, !useAncestry, recursive, true, reporter, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
        } finally {
            if (editor != null) {
                editor.cleanup();
            }
        }
    }

    private void doDiffURLURL(SVNURL url1, File path1, SVNRevision revision1, SVNURL url2, File path2, SVNRevision revision2, SVNRevision pegRevision,
            boolean recursive, boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
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
        long rev2 = getRevisionNumber(revision2, repository2, path2);
        
        SVNNodeKind kind1 = repository1.checkPath("", rev1);
        SVNNodeKind kind2 = repository2.checkPath("", rev2);
        String target1 = null;
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
        repository2 = createRepository(url1, false); 
        File tmpFile = getDiffGenerator().createTempDirectory();
        try {
            SVNDiffStatusEditor editor = new SVNDiffStatusEditor(basePath, repository2, rev1, handler);
            ISVNReporterBaton reporter = new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, rev1, false);
                    reporter.finishReport();
                }
            };
            repository1.diff(url2, rev2, rev1, target1, !useAncestry, recursive, false, reporter, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
        } finally {
            if (tmpFile != null) {
                SVNFileUtil.deleteAll(tmpFile, true, null);
            }
        }
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
     * @param  recusrsive     <span class="javakeyword">true</span> to descend 
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
    public void doMerge(File path1, SVNRevision revision1, File path2, SVNRevision revision2, File dstPath, boolean recusrsive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        path1 = new File(SVNPathUtil.validateFilePath(path1.getAbsolutePath())).getAbsoluteFile();
        path2 = new File(SVNPathUtil.validateFilePath(path2.getAbsolutePath())).getAbsoluteFile();
        dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath())).getAbsoluteFile();
        /*
         * Same as 2. merge sourceWCPATH1@N sourceWCPATH2@M [WCPATH]
         * or      3. merge -r N:M SOURCE[@REV] [WCPATH]
         * where SOURCE is a path and path1 and path2 are the same.
         */
        SVNRevision pegRevision = SVNRevision.UNDEFINED;
        if (path1.equals(path2)) {
            pegRevision = SVNRevision.WORKING;
        }
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
        SVNWCAccess wcAccess = createWCAccess();
        dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath()));
        try {
            dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath()));
            SVNAdminAreaInfo info = wcAccess.openAnchor(dstPath, !dryRun, recusrsive ? SVNWCAccess.INFINITE_DEPTH : 0);
            
            SVNEntry targetEntry = wcAccess.getEntry(dstPath, false);
            if (targetEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", dstPath);
                SVNErrorManager.error(err);
            }
            if (targetEntry.isFile()) {
                doMergeFile(url1, path1, revision1, url2, path2, revision2, pegRevision, info, force, dryRun);
            } else if (targetEntry.isDirectory()) {
                doMerge(url1, path1, revision1, url2, path2, revision2, pegRevision, info, recusrsive, useAncestry, force, dryRun);
            }
        } finally {
            wcAccess.close();
        }
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
     * @param  recusrsive     <span class="javakeyword">true</span> to descend 
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
    public void doMerge(File path1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, File dstPath, boolean recusrsive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        path1 = new File(SVNPathUtil.validateFilePath(path1.getAbsolutePath())).getAbsoluteFile();
        dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath())).getAbsoluteFile();
        SVNURL url1 = getURL(path1);
        if (url1 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path1);
            SVNErrorManager.error(err);
        }
        SVNRevision pegRevision = SVNRevision.UNDEFINED;
        if (url1.equals(url2)) {
            pegRevision = SVNRevision.HEAD;
        }
        SVNWCAccess wcAccess = createWCAccess();
        try {
            dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath()));
            SVNAdminAreaInfo info = wcAccess.openAnchor(dstPath, !dryRun, recusrsive ? SVNWCAccess.INFINITE_DEPTH : 0);
            
            SVNEntry targetEntry = wcAccess.getEntry(dstPath, false);
            if (targetEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", dstPath);
                SVNErrorManager.error(err);
            }
            if (targetEntry.isFile()) {
                doMergeFile(url1, path1, revision1, url2, null, revision2, pegRevision, info, force, dryRun);
            } else if (targetEntry.isDirectory()) {
                doMerge(url1, path1, revision1, url2, null, revision2, pegRevision, info, recusrsive, useAncestry, force, dryRun);
            }
        } finally {
            wcAccess.close();
        }
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
     * @param  recusrsive     <span class="javakeyword">true</span> to descend 
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
    public void doMerge(SVNURL url1, SVNRevision revision1, File path2, SVNRevision revision2, File dstPath, boolean recusrsive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        path2 = new File(SVNPathUtil.validateFilePath(path2.getAbsolutePath())).getAbsoluteFile();
        dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath())).getAbsoluteFile();
        SVNURL url2 = getURL(path2);
        if (url2 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path2);
            SVNErrorManager.error(err);
        }
        SVNRevision pegRevision = SVNRevision.UNDEFINED;
        if (url1.equals(url2)) {
            pegRevision = SVNRevision.WORKING;
        }
        SVNWCAccess wcAccess = createWCAccess();
        try {
            dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath()));
            SVNAdminAreaInfo info = wcAccess.openAnchor(dstPath, !dryRun, recusrsive ? SVNWCAccess.INFINITE_DEPTH : 0);
            
            SVNEntry targetEntry = wcAccess.getEntry(dstPath, false);
            if (targetEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", dstPath);
                SVNErrorManager.error(err);
            }
            if (targetEntry.isFile()) {
                doMergeFile(url1, null, revision1, url2, path2, revision2, pegRevision, info, force, dryRun);
            } else if (targetEntry.isDirectory()) {
                doMerge(url1, null, revision1, url2, path2, revision2, pegRevision, info, recusrsive, useAncestry, force, dryRun);
            }
        } finally {
            wcAccess.close();
        }
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
     * @param  recusrsive     <span class="javakeyword">true</span> to descend 
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
    public void doMerge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, File dstPath, boolean recusrsive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        SVNRevision pegRevision = SVNRevision.UNDEFINED;
        if (url1.equals(url2)) {
            pegRevision = SVNRevision.HEAD;
        }
        SVNWCAccess wcAccess = createWCAccess();
        try {
            dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath())).getAbsoluteFile();
            SVNAdminAreaInfo info = wcAccess.openAnchor(dstPath, !dryRun, recusrsive ? SVNWCAccess.INFINITE_DEPTH : 0);
            SVNEntry targetEntry = wcAccess.getEntry(dstPath, false);
            
            if (targetEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", dstPath);
                SVNErrorManager.error(err);
            }
            if (targetEntry.isFile()) {
                doMergeFile(url1, null, revision1, url2, null, revision2, pegRevision, info, force, dryRun);
            } else if (targetEntry.isDirectory()) {
                doMerge(url1, null, revision1, url2, null, revision2, pegRevision, info, recusrsive, useAncestry, force, dryRun);
            }
        } finally {
            wcAccess.close();
        }
         
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
     * @param  recusrsive     <span class="javakeyword">true</span> to descend 
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
    public void doMerge(SVNURL url1, SVNRevision pegRevision, SVNRevision revision1, SVNRevision revision2, File dstPath, boolean recusrsive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        if (pegRevision == null || !pegRevision.isValid()) {
            pegRevision = SVNRevision.HEAD;
        }
        SVNWCAccess wcAccess = createWCAccess();
        dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath())).getAbsoluteFile();
        try {
            SVNAdminAreaInfo info = wcAccess.openAnchor(dstPath, !dryRun, recusrsive ? SVNWCAccess.INFINITE_DEPTH : 0);
            
            SVNEntry targetEntry = wcAccess.getEntry(dstPath, false);
            if (targetEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", dstPath);
                SVNErrorManager.error(err);
            }
            if (targetEntry.isFile()) {
                doMergeFile(url1, null, revision1, url1, null, revision2, pegRevision, info, force, dryRun);
            } else if (targetEntry.isDirectory()) {
                doMerge(url1, null, revision1, url1, null, revision2, pegRevision, info, recusrsive, useAncestry, force, dryRun);
            }
        } finally {
            wcAccess.close();
        }
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
     * @param  recusrsive     <span class="javakeyword">true</span> to descend 
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
    public void doMerge(File path1, SVNRevision pegRevision, SVNRevision revision1, SVNRevision revision2, File dstPath, boolean recusrsive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        SVNURL url1 = getURL(path1);
        if (url1 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path1);
            SVNErrorManager.error(err);
        }
        /*
         * Equivalent of 3. merge -r N:M SOURCE[@REV] [WCPATH]
         * where SOURCE is a wc path.
         */
        if (pegRevision == null || !pegRevision.isValid()) {
            pegRevision = SVNRevision.WORKING;
        }
        SVNWCAccess wcAccess = createWCAccess();
        try {
            dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath())).getAbsoluteFile();
            path1 = new File(SVNPathUtil.validateFilePath(path1.getAbsolutePath())).getAbsoluteFile();
            SVNAdminAreaInfo info = wcAccess.openAnchor(dstPath.getAbsoluteFile(), !dryRun, recusrsive ? SVNWCAccess.INFINITE_DEPTH : 0);
            
            SVNEntry targetEntry = wcAccess.getEntry(dstPath, false);
            if (targetEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", dstPath);
                SVNErrorManager.error(err);
            }
            if (targetEntry.isFile()) {
                doMergeFile(url1, path1.getAbsoluteFile(), revision1, url1, path1.getAbsoluteFile(), revision2, pegRevision, info, force, dryRun);
            } else if (targetEntry.isDirectory()) {
                doMerge(url1, path1.getAbsoluteFile(), revision1, url1, path1.getAbsoluteFile(), revision2, pegRevision, info, recusrsive, useAncestry, force, dryRun);
            }
        } finally {
            wcAccess.close();
        }
    }
    
    private void doMerge(SVNURL url1, File path1, SVNRevision revision1, SVNURL url2, File path2, SVNRevision revision2, SVNRevision pegRevision,
            SVNAdminAreaInfo info, boolean recursive, boolean useAncestry, boolean force, boolean dryRun) throws SVNException {
        if (!revision1.isValid() || !revision2.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err);
        }
        if (pegRevision.isValid()) {
            SVNRepositoryLocation[] locations = getLocations(url2, path2, null, pegRevision, revision1, revision2);
            url1 = locations[0].getURL();
            url2 = locations[1].getURL();
            revision1 = SVNRevision.create(locations[0].getRevisionNumber());
            revision2 = SVNRevision.create(locations[1].getRevisionNumber());
            path1 = null;
            path2 = null;
        }
        SVNRepository repository1 = createRepository(url1, true);
        final long rev1 = getRevisionNumber(revision1, repository1, path1);
        long rev2 = getRevisionNumber(revision2, repository1, path2);
        SVNRepository repository2 = createRepository(url1, false);
        
        SVNMergeCallback callback = new SVNMergeCallback(info, url2, force, dryRun, getMergeOptions());
        SVNRemoteDiffEditor editor = new SVNRemoteDiffEditor(info, info.getTarget().getRoot(), callback, repository2, rev1, rev2, dryRun, this, this);
        
        try {
            repository1.diff(url2, rev2, rev1, null, !useAncestry, recursive, true,
                    new ISVNReporterBaton() {
                        public void report(ISVNReporter reporter) throws SVNException {
                            reporter.setPath("", null, rev1, false);
                            reporter.finishReport();
                        }
                    }, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
        } finally {
            editor.cleanup();
        }
        
    }
    
    private void doMergeFile(SVNURL url1, File path1, SVNRevision revision1, SVNURL url2, File path2, SVNRevision revision2, SVNRevision pegRevision,
            SVNAdminAreaInfo info, boolean force, boolean dryRun) throws SVNException {
        if (pegRevision.isValid()) {
            SVNRepositoryLocation[] locations = getLocations(url2, path2, null, pegRevision, revision1, revision2);
            url1 = locations[0].getURL();
            url2 = locations[1].getURL();
            revision1 = SVNRevision.create(locations[0].getRevisionNumber());
            revision2 = SVNRevision.create(locations[1].getRevisionNumber());
            path1 = null;
            path2 = null;
        }
        long[] rev1 = new long[1];
        long[] rev2 = new long[2];
        Map props1 = new HashMap();
        Map props2 = new HashMap();
        File f1 = null;
        File f2 = null;
        String name = info.getTargetName();
        String mimeType2;
        String mimeType1;
        SVNStatusType[] mergeResult;
        try {
            f1 = loadFile(url1, path1, revision1, props1, info, rev1);
            f2 = loadFile(url2, path2, revision2, props2, info, rev2);

            mimeType1 = (String) props1.get(SVNProperty.MIME_TYPE);
            mimeType2 = (String) props2.get(SVNProperty.MIME_TYPE);
            props1 = filterProperties(props1, true, false, false);
            props2 = filterProperties(props2, true, false, false);
            Map propsDiff = computePropsDiff(props1, props2);
            // remove non wc props from props1.
            for (Iterator names = props1.keySet().iterator(); names.hasNext();) {
                String propertyName = (String) names.next();
                if (propertyName.startsWith(SVNProperty.SVN_ENTRY_PREFIX) || propertyName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                    names.remove();
                }
            }
            SVNMergeCallback callback = new SVNMergeCallback(info, url2, force, dryRun, getMergeOptions());
            mergeResult = callback.fileChanged(name, f1, f2, rev1[0], rev2[0], mimeType1, mimeType2, props1, propsDiff);
        } finally {
            SVNFileUtil.deleteAll(f1, null);
            SVNFileUtil.deleteAll(f2, null);
        }
        handleEvent(
                SVNEventFactory.createUpdateModifiedEvent(info, info.getAnchor(), name, SVNNodeKind.FILE,
                        SVNEventAction.UPDATE_UPDATE, mimeType2, mergeResult[0], mergeResult[1], SVNStatusType.LOCK_INAPPLICABLE),
                ISVNEventHandler.UNKNOWN);
    }
    
    private File loadFile(SVNURL url, File path, SVNRevision revision, Map properties, SVNAdminAreaInfo info, long[] revNumber) throws SVNException {
        File tmpDir = info.getAnchor().getRoot();
        File result = SVNFileUtil.createUniqueFile(tmpDir, ".merge", ".tmp");
        SVNFileUtil.createEmptyFile(result);
        
        SVNRepository repository = createRepository(url, true);
        long revisionNumber = getRevisionNumber(revision, repository, path);
        OutputStream os = null;
        try {
            os = SVNFileUtil.openFileForWriting(result); 
            repository.getFile("", revisionNumber, properties, new SVNCancellableOutputStream(os, this));
        } finally {
            SVNFileUtil.closeFile(os);
        }
        if (revNumber != null && revNumber.length > 0) {
            revNumber[0] = revisionNumber;
        }
        return result;
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
}