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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.AbstractDiffCallback;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNDiffCallback;
import org.tmatesoft.svn.core.internal.wc.SVNDiffEditor;
import org.tmatesoft.svn.core.internal.wc.SVNDiffStatusEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNMergeDriver;
import org.tmatesoft.svn.core.internal.wc.SVNRemoteDiffEditor;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNReporter;
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
public class SVNDiffClient extends SVNMergeDriver {

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
        doDiff(url, pegRevision, rN, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, result);
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
        doDiff(path, pegRevision, rN, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, result);
    }
    
    public void doDiff(File[] paths, SVNRevision rN, SVNRevision rM, SVNRevision pegRevision, SVNDepth depth, 
            boolean useAncestry, OutputStream result) throws SVNException {
        if (paths == null) {
            return;
        }
        
        for (int i = 0; i < paths.length; i++) {
            File path = paths[i];
            try {
                doDiff(path, pegRevision, rN, rM, depth, useAncestry, result);
            } catch (SVNException svne) {
                dispatchEvent(SVNEventFactory.createErrorEvent(svne.getErrorMessage()));
                continue;
            }
        }
    }
    
    public void doDiff(File path, SVNRevision pegRevision, SVNRevision rN, SVNRevision rM, SVNDepth depth, 
            boolean useAncestry, OutputStream result) throws SVNException {
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
        path = path.getAbsoluteFile();
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
        doDiff(url1, rN, url2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, result);
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
        doDiff(path1, rN, url2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, result);
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
        doDiff(url1, rN, path2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, result);
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
        doDiff(path1, rN, path2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, result);
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
        doDiffStatus(path1, rN, path2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, handler);
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
        doDiffStatus(path1, rN, url2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, handler);
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
        doDiffStatus(url1, rN, path2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, handler);
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
        doDiffStatus(url1, rN, url2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, handler);
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
        doMerge(path1, revision1, path2, revision2, dstPath, SVNDepth.getInfinityOrFilesDepth(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    public void doMerge(File path1, SVNRevision revision1, File path2, SVNRevision revision2, 
                        File dstPath, SVNDepth depth, boolean useAncestry, boolean force, 
                        boolean dryRun, boolean recordOnly) throws SVNException {
        path1 = path1.getAbsoluteFile();
        path2 = path2.getAbsoluteFile();
        dstPath = dstPath.getAbsoluteFile();
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
        runMerge(url1, revision1, url2, revision2, dstPath, depth, dryRun, force, !useAncestry, 
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
        doMerge(path1, revision1, url2, revision2, dstPath, SVNDepth.getInfinityOrFilesDepth(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    public void doMerge(File path1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, 
                        File dstPath, SVNDepth depth, boolean useAncestry, boolean force, 
                        boolean dryRun, boolean recordOnly) throws SVNException {
        path1 = path1.getAbsoluteFile();
        dstPath = dstPath.getAbsoluteFile();
        SVNURL url1 = getURL(path1);
        if (url1 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path1);
            SVNErrorManager.error(err);
        }
        runMerge(url1, revision1, url2, revision2, dstPath, depth, dryRun, force, !useAncestry, 
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
        doMerge(url1, revision1, path2, revision2, dstPath, SVNDepth.getInfinityOrFilesDepth(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    public void doMerge(SVNURL url1, SVNRevision revision1, File path2, SVNRevision revision2, 
                        File dstPath, SVNDepth depth, boolean useAncestry, boolean force, 
                        boolean dryRun, boolean recordOnly) throws SVNException {
        path2 = path2.getAbsoluteFile();
        dstPath = dstPath.getAbsoluteFile();
        SVNURL url2 = getURL(path2);
        if (url2 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path2);
            SVNErrorManager.error(err);
        }
        runMerge(url1, revision1, url2, revision2, dstPath, depth, dryRun, force, !useAncestry, 
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
        doMerge(url1, revision1, url2, revision2, dstPath, SVNDepth.getInfinityOrFilesDepth(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    public void doMerge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, 
                        File dstPath, SVNDepth depth, boolean useAncestry, boolean force, 
                        boolean dryRun, boolean recordOnly) throws SVNException {
         runMerge(url1, revision1, url2, revision2, dstPath, depth, dryRun, force, !useAncestry, 
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
        SVNRevisionRange range = new SVNRevisionRange(revision1, revision2);
        List rangesToMerge = new LinkedList();
        rangesToMerge.add(range);
    	doMerge(url1, pegRevision, rangesToMerge, dstPath, SVNDepth.getInfinityOrFilesDepth(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    public void doMerge(SVNURL url1, SVNRevision pegRevision, Collection rangesToMerge, File dstPath, 
    		SVNDepth depth, boolean useAncestry, boolean force, boolean dryRun, boolean recordOnly) throws SVNException {
        if (pegRevision == null || !pegRevision.isValid()) {
            pegRevision = SVNRevision.HEAD;
        }
        runPeggedMerge(url1, null, rangesToMerge, pegRevision, dstPath, depth, dryRun, 
                       force, !useAncestry, recordOnly);
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
        SVNRevisionRange range = new SVNRevisionRange(revision1, revision2);
        List rangesToMerge = new LinkedList();
        rangesToMerge.add(range);
    	doMerge(path1, pegRevision, rangesToMerge, dstPath, SVNDepth.getInfinityOrFilesDepth(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    public void doMerge(File path1, SVNRevision pegRevision, List rangesToMerge, File dstPath, SVNDepth depth, 
    		boolean useAncestry, boolean force, boolean dryRun, boolean recordOnly) throws SVNException {
        /*
         * Equivalent of 3. merge -r N:M SOURCE[@REV] [WCPATH]
         * where SOURCE is a wc path.
         */
        if (pegRevision == null || !pegRevision.isValid()) {
            pegRevision = SVNRevision.WORKING;
        }
        runPeggedMerge(null, path1, rangesToMerge, pegRevision, dstPath, depth, dryRun, 
                       force, !useAncestry, recordOnly);
    }

    public void doMergeReIntegrate(File srcPath, SVNRevision pegRevision, File dstPath, 
            boolean force, boolean dryRun) throws SVNException {
        if (pegRevision == null || !pegRevision.isValid()) {
            pegRevision = SVNRevision.WORKING;
        }
        runMergeReintegrate(null, srcPath, pegRevision, dstPath, force, dryRun);
    }
    
    public void doMergeReIntegrate(SVNURL srcURL, SVNRevision pegRevision, File dstPath, 
            boolean force, boolean dryRun) throws SVNException {
        if (pegRevision == null || !pegRevision.isValid()) {
            pegRevision = SVNRevision.WORKING;
        }
        runMergeReintegrate(srcURL, null, pegRevision, dstPath, force, dryRun);
    }

    public Map getMergeInfo(File path, SVNRevision pegRevision, SVNURL repositoryRoot[]) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminArea adminArea = wcAccess.probeOpen(path, false, 0);
            SVNEntry entry = wcAccess.getVersionedEntry(path, false);
            long revNum[] = { SVNRepository.INVALID_REVISION };
            SVNURL url = getEntryLocation(path, entry, revNum, SVNRevision.WORKING);
            SVNRepository repository = null;
            try {
            	repository = createRepository(url, false);
            	assertServerIsMergeInfoCapable(repository, path.toString());
            } finally {
            	repository.closeSession();
            }
            
            SVNURL reposRoot = getReposRoot(path, null, pegRevision, adminArea, wcAccess);
            if (repositoryRoot != null && repositoryRoot.length > 0) {
            	repositoryRoot[0] = reposRoot;
            }
            
            boolean[] indirect = { false };
            return getWCOrRepositoryMergeInfo(wcAccess, path, entry, 
            		SVNMergeInfoInheritance.INHERITED, indirect, false, null);
        } finally {
            wcAccess.close();
        }
    }

    public Map getMergeInfo(SVNURL url, SVNRevision pegRevision, SVNURL repositoryRoot[]) throws SVNException {
        SVNRepository repository = null;
        try {
        	repository = createRepository(url, true); 
            long revisionNum = getRevisionNumber(pegRevision, repository, null);
            SVNURL reposRoot = repository.getRepositoryRoot(true);
            if (repositoryRoot != null && repositoryRoot.length > 0) {
            	repositoryRoot[0] = reposRoot;
            }
            String relPath = getPathRelativeToRoot(null, url, reposRoot, null, null);
            return getReposMergeInfo(repository, relPath, revisionNum, 
            		SVNMergeInfoInheritance.INHERITED, false);
        } finally {
        	repository.closeSession();
        }
    }
    
    public SVNMergeRangeList getAvailableMergeInfo(File path, SVNRevision pegRevision, SVNURL mergeSrcURL) throws SVNException {
        SVNRepository repos = null;
        try {
        	repos = createRepository(mergeSrcURL, true);
            Map mergeInfo = getMergeInfo(path, pegRevision, null);
            Map history = getHistoryAsMergeInfo(null, path, pegRevision, SVNRepository.INVALID_REVISION, 
            		SVNRepository.INVALID_REVISION, null, null);
            if (mergeInfo == null) {
            	mergeInfo = history;
            } else {
            	mergeInfo = SVNMergeInfoUtil.mergeMergeInfos(mergeInfo, history);
            }
            
            Map sourceHistory = getHistoryAsMergeInfo(mergeSrcURL, null, SVNRevision.HEAD, 
            		SVNRepository.INVALID_REVISION, SVNRepository.INVALID_REVISION, repos, null);
            Map availableMergeInfo = SVNMergeInfoUtil.removeMergeInfo(mergeInfo, sourceHistory);
            SVNMergeRangeList rangeList = new SVNMergeRangeList(new SVNMergeRange[0]);
            for (Iterator availableIter = availableMergeInfo.values().iterator(); availableIter.hasNext();) {
            	SVNMergeRangeList availableRangeList = (SVNMergeRangeList) availableIter.next();
            	rangeList = rangeList.merge(availableRangeList);
			}
            return rangeList;
        } finally {
        	repos.closeSession();
        }
    }

    public SVNMergeRangeList getAvailableMergeInfo(SVNURL url, SVNRevision pegRevision, SVNURL mergeSrcURL) throws SVNException {
        SVNRepository repos = null;
        try {
        	repos = createRepository(mergeSrcURL, true);
            Map mergeInfo = getMergeInfo(url, pegRevision, null);
            Map history = getHistoryAsMergeInfo(url, null, pegRevision, SVNRepository.INVALID_REVISION, 
            		SVNRepository.INVALID_REVISION, null, null);
            if (mergeInfo == null) {
            	mergeInfo = history;
            } else {
            	mergeInfo = SVNMergeInfoUtil.mergeMergeInfos(mergeInfo, history);
            }
            
            Map sourceHistory = getHistoryAsMergeInfo(mergeSrcURL, null, SVNRevision.HEAD, 
            		SVNRepository.INVALID_REVISION, SVNRepository.INVALID_REVISION, repos, null);
            Map availableMergeInfo = SVNMergeInfoUtil.removeMergeInfo(mergeInfo, sourceHistory);
            SVNMergeRangeList rangeList = new SVNMergeRangeList(new SVNMergeRange[0]);
            for (Iterator availableIter = availableMergeInfo.values().iterator(); availableIter.hasNext();) {
            	SVNMergeRangeList availableRangeList = (SVNMergeRangeList) availableIter.next();
            	rangeList = rangeList.merge(availableRangeList);
			}
            return rangeList;
        } finally {
        	repos.closeSession();
        }
    }

    public Map getMergedMergeInfo(File path, SVNRevision pegRevision) throws SVNException {
    	SVNURL reposRoot[] = new SVNURL[1];
    	Map mergeInfo = getMergeInfo(path, pegRevision, reposRoot);
    	SVNURL repositoryRoot = reposRoot[0];
    	if (mergeInfo != null) {
    		Map fullPathMergeInfo = new HashMap();
            for (Iterator paths = mergeInfo.keySet().iterator(); paths.hasNext();) {
                String mergeSrcPath = (String) paths.next();
                SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(mergeSrcPath);
                if (mergeSrcPath.startsWith("/")) {
                    mergeSrcPath = mergeSrcPath.substring(1);
                }
                SVNURL url = repositoryRoot.appendPath(mergeSrcPath, false);
                fullPathMergeInfo.put(url, rangeList);
            }
            mergeInfo = fullPathMergeInfo;
    	}
    	return mergeInfo;
    }

    public Map getMergedMergeInfo(SVNURL url, SVNRevision pegRevision) throws SVNException {
    	SVNURL reposRoot[] = new SVNURL[1];
    	Map mergeInfo = getMergeInfo(url, pegRevision, reposRoot);
    	SVNURL repositoryRoot = reposRoot[0];
    	if (mergeInfo != null) {
    		Map fullPathMergeInfo = new HashMap();
            for (Iterator paths = mergeInfo.keySet().iterator(); paths.hasNext();) {
                String mergeSrcPath = (String) paths.next();
                SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(mergeSrcPath);
                if (mergeSrcPath.startsWith("/")) {
                    mergeSrcPath = mergeSrcPath.substring(1);
                }
                SVNURL nextURL = repositoryRoot.appendPath(mergeSrcPath, false);
                fullPathMergeInfo.put(nextURL, rangeList);
            }
            mergeInfo = fullPathMergeInfo;
    	}
    	return mergeInfo;
    }

    public Collection suggestMergeSources(File path, SVNRevision pegRevision) throws SVNException {
        LinkedList suggestions = new LinkedList();
        SVNURL reposRoot = getReposRoot(path, null, pegRevision, null, null);
        SVNLogClient logClient = new SVNLogClient(getRepositoryPool(), getOptions());
        SVNLocationEntry copyFromInfo = logClient.getCopySource(path, null, pegRevision);
        String copyFromPath = copyFromInfo.getPath();
        SVNURL copyFromURL = null;
        if (copyFromPath != null) {
            String relCopyFromPath = copyFromPath.startsWith("/") ? copyFromPath.substring(1) : copyFromPath;
            copyFromURL = reposRoot.appendPath(relCopyFromPath, false);
            suggestions.add(copyFromURL);
        }
        
        Map mergeInfo = getMergedMergeInfo(path, pegRevision);
        if (mergeInfo != null) {
            for (Iterator mergeSrcURLs = mergeInfo.keySet().iterator(); mergeSrcURLs.hasNext();) {
                SVNURL mergeSrcURL = (SVNURL) mergeSrcURLs.next();
                if (copyFromURL == null || !copyFromURL.equals(mergeSrcURL)) {
                    suggestions.add(mergeSrcURL);
                }
            }
        }
        return suggestions;
    }

    public Collection suggestMergeSources(SVNURL url, SVNRevision pegRevision) throws SVNException {
        LinkedList suggestions = new LinkedList();
        SVNURL reposRoot = getReposRoot(null, url, pegRevision, null, null);
        SVNLogClient logClient = new SVNLogClient(getRepositoryPool(), getOptions());
        SVNLocationEntry copyFromInfo = logClient.getCopySource(null, url, pegRevision);
        String copyFromPath = copyFromInfo.getPath();

        SVNURL copyFromURL = null;
        if (copyFromPath != null) {
            String relCopyFromPath = copyFromPath.startsWith("/") ? copyFromPath.substring(1) : copyFromPath;
            copyFromURL = reposRoot.appendPath(relCopyFromPath, false);
            suggestions.add(copyFromURL);
        }
        
        Map mergeInfo = getMergedMergeInfo(url, pegRevision);
        if (mergeInfo != null) {
            for (Iterator mergeSrcURLs = mergeInfo.keySet().iterator(); mergeSrcURLs.hasNext();) {
                SVNURL mergeSrcURL = (SVNURL) mergeSrcURLs.next();
                if (copyFromURL == null || !copyFromURL.equals(mergeSrcURL)) {
                    suggestions.add(mergeSrcURL);
                }
            }
        }
        return suggestions;
    }

}