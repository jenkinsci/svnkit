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
                dispatchEvent(new SVNEvent(svne.getErrorMessage()));
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
        doMerge(url1, revision1, url2, revision2, dstPath, SVNDepth.fromRecurse(recursive), 
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
                       force, !useAncestry, recordOnly);
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
                                SVNDepth depth, boolean dryRun, boolean force, boolean ignoreAncestry, 
                                boolean recordOnly) throws SVNException {
    
        SVNWCAccess wcAccess = createWCAccess();
        dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath()));
        Merger merger = null;
        try {
            dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath()));
            SVNAdminArea adminArea = wcAccess.probeOpen(dstPath, !dryRun, SVNWCAccess.INFINITE_DEPTH);

            SVNEntry targetEntry = wcAccess.getVersionedEntry(dstPath, false);
            SVNURL wcReposRoot = null;
            if (targetEntry.getRepositoryRoot() != null) {
                wcReposRoot = targetEntry.getRepositoryRootURL();
            } else {
                SVNRepository repos = createRepository(null, dstPath, 
                                                       SVNRevision.WORKING, 
                                                       SVNRevision.WORKING);
                try {
                    wcReposRoot = repos.getRepositoryRoot(true);
                } finally {
                    repos.closeSession();
                }
            }
            
            SVNURL url = srcURL == null ? getURL(srcPath) : srcURL;
            if (url == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", 
                        srcPath);
                SVNErrorManager.error(err);
            }
            
            if (depth == null || depth == SVNDepth.UNKNOWN) {
                depth = targetEntry.getDepth();
            }
            
            SVNRepository repository1 = createRepository(url, true);

            merger = createMerger(repository1, null, url, targetEntry, dstPath, wcAccess, dryRun, force,  
                                         recordOnly);
            
            SVNRevision[] revs = getAssumedDefaultRevisionRange(revision1, revision2, merger.myRepository1);
            SVNRepositoryLocation[] locations = getLocations(url, srcPath, null, pegRevision, 
                    revs[0], revs[1]);

            SVNURL url1 = locations[0].getURL();
            SVNURL url2 = locations[1].getURL();
            revision1 = SVNRevision.create(locations[0].getRevisionNumber());
            revision2 = SVNRevision.create(locations[1].getRevisionNumber());

            merger.myIsSameURLs = url1.equals(url2);
            if (!merger.myIsSameURLs && recordOnly) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
                                                             "Use of two URLs is not compatible with mergeinfo modification"); 
                SVNErrorManager.error(err);
            }
            merger.myRepository1.setLocation(url1, true);
            if (!revision1.isValid() || !revision2.isValid()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                        "Not all required revisions are specified");            
                SVNErrorManager.error(err);
            }

            Object[] mergeActionInfo = merger.grokRangeInfoFromRevisions(merger.myRepository1, merger.myRepository1, 
                    revision1, revision2);

            SVNMergeRange range = (SVNMergeRange) mergeActionInfo[0];
            MergeAction mergeAction = (MergeAction) mergeActionInfo[1];
            if (mergeAction == MergeAction.NO_OP || (recordOnly && dryRun)) {
                return;
            }
            
            boolean isRollBack = mergeAction == MergeAction.ROLL_BACK;
            if (merger.myIsSameRepository && recordOnly) {
                merger.recordMergeInfoForRecordOnlyMerge(url1, range, isRollBack, targetEntry);
                return;
            }
            
            if (targetEntry.isFile()) {
                merger.doMergeFile(url1, range.getStartRevision(), url2, range.getEndRevision(), 
                        dstPath, adminArea, ignoreAncestry, isRollBack);
            } else if (targetEntry.isDirectory()) {
                LinkedList childrenWithMergeInfo = null;
                if (merger.myIsSameURLs) {
                    childrenWithMergeInfo = merger.discoverAndMergeChildren(targetEntry, range.getStartRevision(), 
                                                                            range.getEndRevision(), depth, url1, 
                                                                            wcReposRoot, adminArea, ignoreAncestry, 
                                                                            isRollBack);
                    if (!dryRun && merger.myIsOperativeMerge) {
                        merger.elideChildren(childrenWithMergeInfo, dstPath, targetEntry);
                    }
                } else {
                    merger.doMerge(url1, range.getStartRevision(), url2, range.getEndRevision(), dstPath, adminArea, 
                            depth, childrenWithMergeInfo, ignoreAncestry, merger.myHasMissingChildren);
                }
            }
            
            if (!dryRun && merger.myIsOperativeMerge) {
                elideMergeInfo(wcAccess, dstPath, false, null);
            }
        } finally {
            try {
                wcAccess.close();
            } catch (SVNException svne) {
                //
            }
            if (merger != null) {
                if (merger.myRepository1 != null) {
                    merger.myRepository1.closeSession();
                }
                if (merger.myRepository2 != null) {
                    merger.myRepository2.closeSession();
                }
            }
        }

        /*
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
                                         force, recordOnly);
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
                
                merger.cleanUpNoOpMerge(childrenWithMergeInfo);
                if (!dryRun && (merger.myIsOperativeMerge || merger.myIsRecordOnly)) {
                    merger.elideChildren(childrenWithMergeInfo, dstPath, targetEntry);
                }
            }
            if (!dryRun && (merger.myIsOperativeMerge || merger.myIsRecordOnly)) {
                elideMergeInfo(wcAccess, dstPath, false, null);
            }
        } finally {
            wcAccess.close();
        }
*/    
    }
    
    private void runMerge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, 
                         File dstPath, SVNDepth depth, boolean dryRun, boolean force, 
                         boolean ignoreAncestry, boolean recordOnly) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath()));
        Merger merger = null;
        try {
            dstPath = new File(SVNPathUtil.validateFilePath(dstPath.getAbsolutePath()));
            SVNAdminArea adminArea = wcAccess.probeOpen(dstPath, !dryRun, SVNWCAccess.INFINITE_DEPTH);

            SVNEntry targetEntry = wcAccess.getVersionedEntry(dstPath, false);
            SVNURL wcReposRoot = null;
            if (targetEntry.getRepositoryRoot() != null) {
                wcReposRoot = targetEntry.getRepositoryRootURL();
            } else {
                SVNRepository repos = createRepository(null, dstPath, 
                                                       SVNRevision.WORKING, 
                                                       SVNRevision.WORKING);
                try {
                    wcReposRoot = repos.getRepositoryRoot(true);
                } finally {
                    repos.closeSession();
                }
            }
            
            if (depth == null || depth == SVNDepth.UNKNOWN) {
                depth = targetEntry.getDepth();
            }
            
            SVNRepository repository1 = createRepository(url1, false);
            SVNRepository repository2 = createRepository(url2, false);

            merger = createMerger(repository1, repository2, url2, targetEntry, dstPath, wcAccess, dryRun, force,  
                                         recordOnly);
            

            if (!revision1.isValid() || !revision2.isValid()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                        "Not all required revisions are specified");            
                SVNErrorManager.error(err);
            }
            
            merger.myIsSameURLs = url1.equals(url2);
            if (!merger.myIsSameURLs && recordOnly) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
                                                             "Use of two URLs is not compatible with mergeinfo modification"); 
                SVNErrorManager.error(err);
            }

            Object[] mergeActionInfo = merger.grokRangeInfoFromRevisions(merger.myRepository1, merger.myRepository2, 
                    revision1, revision2);

            SVNMergeRange range = (SVNMergeRange) mergeActionInfo[0];
            MergeAction mergeAction = (MergeAction) mergeActionInfo[1];
            if (mergeAction == MergeAction.NO_OP || (recordOnly && dryRun)) {
                return;
            }
            
            boolean isRollBack = mergeAction == MergeAction.ROLL_BACK;
            if (merger.myIsSameRepository && recordOnly) {
                merger.recordMergeInfoForRecordOnlyMerge(url1, range, isRollBack, targetEntry);
                return;
            }
            
            if (targetEntry.isFile()) {
                merger.doMergeFile(url1, range.getStartRevision(), url2, range.getEndRevision(), 
                        dstPath, adminArea, ignoreAncestry, isRollBack);
            } else if (targetEntry.isDirectory()) {
                LinkedList childrenWithMergeInfo = null;
                if (merger.myIsSameURLs) {
                    childrenWithMergeInfo = merger.discoverAndMergeChildren(targetEntry, range.getStartRevision(), 
                                                                            range.getEndRevision(), depth, url1, 
                                                                            wcReposRoot, adminArea, ignoreAncestry, 
                                                                            isRollBack);
                    if (!dryRun && merger.myIsOperativeMerge) {
                        merger.elideChildren(childrenWithMergeInfo, dstPath, targetEntry);
                    }
                } else {
                    merger.doMerge(url1, range.getStartRevision(), url2, range.getEndRevision(), dstPath, adminArea, 
                            depth, childrenWithMergeInfo, ignoreAncestry, merger.myHasMissingChildren);
                }
            }
            
            if (!dryRun && merger.myIsOperativeMerge) {
                elideMergeInfo(wcAccess, dstPath, false, null);
            }
        } finally {
            try {
                wcAccess.close();
            } catch (SVNException svne) {
                //
            }
            if (merger != null) {
                if (merger.myRepository1 != null) {
                    merger.myRepository1.closeSession();
                }
                if (merger.myRepository2 != null) {
                    merger.myRepository2.closeSession();
                }
            }
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
    
    private Merger createMerger(SVNRepository repository1, SVNRepository repository2, SVNURL url, SVNEntry entry, 
            File target, SVNWCAccess access, boolean dryRun, boolean force, boolean recordOnly) throws SVNException {
        Merger merger = new Merger();
        merger.myURL = url;
        merger.myTarget = target;
        merger.myIsForce = force;
        merger.myIsDryRun = dryRun;
        merger.myIsRecordOnly = recordOnly;
        merger.myOperativeNotificationsNumber = 0;
        merger.myWCAccess = access;
        merger.myRepository1 = repository1;
        merger.myRepository2 = repository2;
        
        if (dryRun) {
            merger.myIsSameRepository = false;
        } else {
            SVNURL reposRoot = repository1.getRepositoryRoot(true);
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
        boolean myIsOperativeMerge;
        boolean myIsTargetHasDummyMergeRange;
        int myOperativeNotificationsNumber;
        Map myMergedPaths;
        Map myConflictedPaths;
        SVNURL myURL;
        File myTarget;
        LinkedList mySkippedPaths;
        SVNWCAccess myWCAccess;
        SVNRepository myRepository1;
        SVNRepository myRepository2;
        
        public void doMergeFile(SVNURL url1, long revision1, SVNURL url2, long revision2, 
                File dstPath, SVNAdminArea adminArea, boolean ignoreAncestry, 
                boolean isRollBack) throws SVNException {
            myWCAccess.probeTry(dstPath, true, -1);
            SVNEntry entry = myWCAccess.getVersionedEntry(dstPath, false);
            
            boolean isReplace = false;
            SVNErrorMessage error = null;
            if (!ignoreAncestry) {
                try {
                    getLocations(url2, null, null, SVNRevision.create(revision2), SVNRevision.create(revision1), 
                            SVNRevision.UNDEFINED);
                } catch (SVNException svne) {
                    if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.CLIENT_UNRELATED_RESOURCES) {
                        isReplace = true;
                    } else {
                        throw svne;
                    }
                }
            }
            
            if (myRepository2 == null) {
                myRepository2 = createRepository(url2, false);
            }
            
            SVNMergeRange range = new SVNMergeRange(revision1, revision2, true);
            boolean isIndirect = false; 
            String reposPath = null;
            SVNMergeRangeList remainingRangesList = null;
            Map targetMergeInfo = new TreeMap();
            
            if (myIsSameURLs && myIsSameRepository) {
                myRepository1.setLocation(entry.getSVNURL(), true);
                isIndirect = getWCOrRepositoryMergeInfo(myWCAccess, targetMergeInfo, dstPath, entry, 
                        SVNMergeInfoInheritance.INHERITED, isIndirect, false, myRepository1);
                myRepository1.setLocation(url1, true);
                SVNURL reposRoot = myRepository1.getRepositoryRoot(true);
                String reposRootPath = reposRoot.getPath();
                String path = url1.getPath();
                if (!path.startsWith(reposRootPath)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, 
                            "URL ''{0}'' is not a child of repository root URL ''{1}''", new Object[] { url1, reposRoot });
                    SVNErrorManager.error(err);
                }
                
                reposPath = path.substring(reposRootPath.length());
                if (!reposPath.startsWith("/")) {
                    reposPath = "/" + reposPath;
                }
                remainingRangesList = calculateRemainingRanges(url1, reposPath, entry, range, targetMergeInfo, 
                        myRepository1, isRollBack);
            } else {
                remainingRangesList = new SVNMergeRangeList(new SVNMergeRange[] { range });
            }
            
            SVNMergeRange[] remainingRanges = remainingRangesList.getRanges();
            SVNMergeCallback callback = new SVNMergeCallback(adminArea, myURL, myIsForce, myIsDryRun, 
                    getMergeOptions(), myConflictedPaths);
            
            for (int i = 0; i < remainingRanges.length; i++) {
                SVNMergeRange nextRange = remainingRanges[i];
                this.handleEvent(SVNEventFactory.createMergeBeginEvent(dstPath, myIsSameURLs ? nextRange : null), 
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
                    f1 = loadFile(myRepository1, nextRange.getStartRevision(), props1, adminArea);
                    f2 = loadFile(myRepository2, nextRange.getEndRevision(), props2, adminArea);

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
                    
                    if (myConflictedPaths == null) {
                        myConflictedPaths = callback.getConflictedPaths();
                    }
                    if (myIsSameURLs) {
                        if (!myIsDryRun && myIsSameRepository) {
                            Map merges = determinePerformedMerges(dstPath, nextRange, SVNDepth.INFINITY);
                            if (myIsOperativeMerge) {
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
                        }
                        myOperativeNotificationsNumber = 0;
                        if (mySkippedPaths != null) {
                            mySkippedPaths.clear();
                        }
                        if (myMergedPaths != null) {
                            myMergedPaths.clear();
                        }
                    }
                } finally {
                    SVNFileUtil.deleteAll(f1, null);
                    SVNFileUtil.deleteAll(f2, null);
                }
                
                if (i < remainingRanges.length - 1 && myConflictedPaths != null && !myConflictedPaths.isEmpty()) {
                    error = makeMergeConflictError(dstPath, nextRange);
                    break;
                }
            }
            
            sleepForTimeStamp();
            if (error != null) {
                SVNErrorManager.error(error);
            }
        }
    
        public void doMerge(SVNURL url1, long revision1, SVNURL url2, long revision2, 
                             final File dstPath, SVNAdminArea adminArea, 
                             SVNDepth depth, final LinkedList childrenWithMergeInfo,
                             boolean ignoreAncestry, boolean targetMissingChild) throws SVNException {

            SVNMergeRange range = new SVNMergeRange(revision1, revision2, !targetMissingChild && 
                    (depth == SVNDepth.INFINITY || depth == SVNDepth.IMMEDIATES));
            
            this.handleEvent(SVNEventFactory.createMergeBeginEvent(dstPath, myIsSameURLs ? range : null), 
                    ISVNEventHandler.UNKNOWN);
   
            SVNMergeCallback mergeCallback = new SVNMergeCallback(adminArea, myURL, myIsForce, myIsDryRun, 
                    getMergeOptions(), myConflictedPaths);

            driveMergeReportEditor(dstPath, url1, url2, childrenWithMergeInfo, range.getStartRevision(), 
                    range.getEndRevision(), depth, ignoreAncestry, adminArea, mergeCallback, null);

            sleepForTimeStamp();
        }

        public LinkedList discoverAndMergeChildren(SVNEntry parentEntry, long revision1, 
                long revision2, SVNDepth depth, SVNURL parentMergeSourceURL, SVNURL wcRootURL,
                SVNAdminArea adminArea, boolean ignoreAncestry, boolean isRollBack) throws SVNException {
            
            String parentMergeSourcePath = null;
            if (parentMergeSourceURL.equals(wcRootURL)) {
                parentMergeSourcePath = "/";
            } else {
                String parentPath = parentMergeSourceURL.getPath();
                String wcRootPath = wcRootURL.getPath();
                parentMergeSourcePath = parentPath.substring(wcRootPath.length());
                if (!parentMergeSourcePath.startsWith("/")) {
                    parentMergeSourcePath = "/" + parentMergeSourcePath;
                }
            }
            
            LinkedList childrenWithMergeInfo = getMergeInfoPaths(parentEntry, myTarget,
                    parentMergeSourcePath, depth);
            
            MergePath targetMergePath = (MergePath) childrenWithMergeInfo.getFirst();
            myHasMissingChildren = targetMergePath.myHasMissingChildren;
            SVNMergeRange range = new SVNMergeRange(revision1, revision2, !myHasMissingChildren || 
                    (depth != SVNDepth.INFINITY && depth != SVNDepth.IMMEDIATES));
            
            SVNRepository repository = createRepository(parentMergeSourceURL, false);
            try {
                populateRemainingRanges(childrenWithMergeInfo, repository, range, 
                        parentMergeSourcePath, isRollBack);
            } finally {
                repository.closeSession();
            }

            long endRevision = getNearestEndRevision(childrenWithMergeInfo);
            long startRevision = revision1;
            SVNErrorMessage error = null;
            while (SVNRevision.isValidRevisionNumber(endRevision)) {
                sliceRemainingRanges(childrenWithMergeInfo, endRevision);
                doMerge(parentMergeSourceURL, startRevision, parentMergeSourceURL, endRevision, myTarget, 
                        adminArea, depth, childrenWithMergeInfo, ignoreAncestry, myIsTargetHasDummyMergeRange);
                removeFirstRangeFromRemainingRanges(childrenWithMergeInfo);
                long nextEndRevision = getNearestEndRevision(childrenWithMergeInfo);
                if (SVNRevision.isValidRevisionNumber(nextEndRevision) && myConflictedPaths != null &&
                        !myConflictedPaths.isEmpty()) {
                    SVNMergeRange conflictedRange = new SVNMergeRange(startRevision, endRevision, false);
                    error = makeMergeConflictError(myTarget, conflictedRange);
                    range.setEndRevision(endRevision);
                    break;
                }
                startRevision = endRevision + 1;
                if (startRevision > revision2) {
                    break;
                }
                endRevision = nextEndRevision;
            }
            
            if (!myIsDryRun && myIsSameRepository) {
                removeAbsentChildren(myTarget, childrenWithMergeInfo);
                Map merges = determinePerformedMerges(myTarget, range, depth);
                if (!myIsOperativeMerge) {
                    if (error != null) {
                        SVNErrorManager.error(error);
                    }
                    return childrenWithMergeInfo;
                }
                
                recordMergeInfoOnMergedChildren(depth);
                updateWCMergeInfo(myTarget, parentMergeSourcePath, parentEntry, merges, isRollBack);
                for (int i = 0; i < childrenWithMergeInfo.size(); i++) {
                    MergePath child = (MergePath) childrenWithMergeInfo.get(i);
                    if (child == null || child.myIsAbsent) {
                        continue;
                    }
                    
                    String childRelPath = null;
                    if (myTarget.equals(child.myPath)) {
                        childRelPath = "";
                    } else {
                        childRelPath = SVNPathUtil.getRelativePath(myTarget, child.myPath);
                    }
                    
                    SVNEntry childEntry = myWCAccess.getVersionedEntry(child.myPath, false);
                    String childMergeSourcePath = SVNPathUtil.concatToAbs(parentMergeSourcePath, childRelPath);
                    if (myIsOperativeMerge) {
                        TreeMap childMerges = new TreeMap();
                        SVNMergeRange childMergeRange = new SVNMergeRange(range.getStartRevision(), 
                                range.getEndRevision(), childEntry.isFile() ? true : (!myHasMissingChildren || 
                                        (depth != SVNDepth.INFINITY && depth != SVNDepth.IMMEDIATES)));
                        SVNMergeRangeList childMergeRangeList = new SVNMergeRangeList(new SVNMergeRange[] { 
                                childMergeRange });
                        
                        childMerges.put(child.myPath, childMergeRangeList);
                        if (child.myIsIndirectMergeInfo) {
                            String mergeInfoStr = null;
                            if (!child.myPreMergeMergeInfo.isEmpty()) {
                                mergeInfoStr = SVNMergeInfoManager.formatMergeInfoToString(child.myPreMergeMergeInfo);
                            }
                            SVNPropertiesManager.setProperty(myWCAccess, child.myPath, SVNProperty.MERGE_INFO, 
                                    mergeInfoStr, true);
                        }
                        
                        updateWCMergeInfo(child.myPath, childMergeSourcePath, childEntry, childMerges, isRollBack);
                    }
                    markMergeInfoAsInheritableForARange(child.myPath, childMergeSourcePath, child.myPreMergeMergeInfo, 
                            range, childrenWithMergeInfo, true, i);
                    if (i > 0) {
                        elideTargetMergeInfo(child.myPath);
                    }
                    
                }
            }
            
            if (error != null) {
                SVNErrorManager.error(error);
            }
            return childrenWithMergeInfo;
        }
        
        public void elideChildren(LinkedList childrenWithMergeInfo, File dstPath, SVNEntry entry) throws SVNException {
            if (childrenWithMergeInfo != null && !childrenWithMergeInfo.isEmpty()) {
                //TODO: fixme!
                Map filesToValues = SVNPropertiesManager.getWorkingCopyPropertyValues(entry, 
                                                                                      SVNProperty.MERGE_INFO, 
                                                                                      SVNDepth.EMPTY, false); 
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
                        //TODO: fixme!
                        Map childFileToValue = SVNPropertiesManager.getWorkingCopyPropertyValues(childEntry, 
                                                                                                 SVNProperty.MERGE_INFO, 
                                                                                                 SVNDepth.EMPTY, false); 
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
                    event.getPropertiesStatus() == SVNStatusType.CHANGED ||
                    event.getAction() == SVNEventAction.UPDATE_ADD) {
                    myOperativeNotificationsNumber++;
                }

                if (event.getContentsStatus() == SVNStatusType.MERGED ||
                        event.getContentsStatus() == SVNStatusType.CHANGED ||
                        event.getPropertiesStatus() == SVNStatusType.MERGED ||
                        event.getPropertiesStatus() == SVNStatusType.CHANGED ||
                        event.getAction() == SVNEventAction.UPDATE_ADD) {
                    File mergedPath = event.getFile();
                    if (myMergedPaths == null) {
                        myMergedPaths = new HashMap();
                    }
                    myMergedPaths.put(mergedPath, mergedPath);
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

        public Object[] grokRangeInfoFromRevisions(SVNRepository repository1, SVNRepository repository2, 
                SVNRevision rev1, SVNRevision rev2) throws SVNException {
            long startRev = getRevisionNumber(rev1, repository1, null);
            long endRev = getRevisionNumber(rev2, repository2, null);
            
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

        public void recordMergeInfoForRecordOnlyMerge(SVNURL url1, SVNMergeRange range, 
                boolean isRollBack, SVNEntry entry) throws SVNException {
            Map merges = new TreeMap();
            Map targetMergeInfo = new TreeMap();
            myRepository1.setLocation(entry.getSVNURL(), true);
            boolean isIndirect = getWCOrRepositoryMergeInfo(myWCAccess, targetMergeInfo, myTarget, 
                    entry, SVNMergeInfoInheritance.INHERITED, false, false, myRepository1);
            myRepository1.setLocation(url1, true);

            SVNURL reposRoot = myRepository1.getRepositoryRoot(true);
            String reposRootPath = reposRoot.getPath();
            String path = url1.getPath();
            if (!path.startsWith(reposRootPath)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, 
                        "URL ''{0}'' is not a child of repository root URL ''{1}''", new Object[] { url1, reposRoot });
                SVNErrorManager.error(err);
            }
            
            String reposPath = path.substring(reposRootPath.length());
            if (!reposPath.startsWith("/")) {
                reposPath = "/" + reposPath;
            }
            
            SVNMergeRangeList rangeList = new SVNMergeRangeList(new SVNMergeRange[] { range });
            merges.put(myTarget, rangeList);
            if (isIndirect) {
                String mergeInfoStr = null;
                if (!merges.isEmpty()) {
                    mergeInfoStr = SVNMergeInfoManager.formatMergeInfoToString(targetMergeInfo);
                }
                SVNPropertiesManager.setProperty(myWCAccess, myTarget, SVNProperty.MERGE_INFO, 
                        mergeInfoStr, true);
            }
            updateWCMergeInfo(myTarget, reposPath, entry, merges, isRollBack);
        }
        
        private void elideTargetMergeInfo(File target) throws SVNException {
            if (!myIsDryRun && myIsOperativeMerge && !myTarget.equals(target)) {
                elideMergeInfo(myWCAccess, target, false, myTarget);
            }
        }
        
        private void markMergeInfoAsInheritableForARange(File target, String reposPath, Map targetMergeInfo, 
                SVNMergeRange range, LinkedList childrenWithMergeInfo, boolean sameURLs, int targetIndex) throws SVNException {
            if (targetMergeInfo != null && sameURLs && !myIsDryRun && myIsSameRepository && targetIndex >= 0) {
                MergePath mergePath = (MergePath) childrenWithMergeInfo.get(targetIndex);
                if (mergePath != null && mergePath.myHasNonInheritableMergeInfo && !mergePath.myHasMissingChildren) {
                    SVNMergeRangeList inheritableRangeList = new SVNMergeRangeList(new SVNMergeRange[] { range });
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
                        
                        SVNPropertiesManager.setProperty(myWCAccess, target, SVNProperty.MERGE_INFO, 
                                mergeInfoStr, true);
                    }
                }
            }
        }
        
        private void recordMergeInfoOnMergedChildren(SVNDepth depth) throws SVNException {
            if (depth != SVNDepth.INFINITY && myMergedPaths != null) {
                boolean isIndirectChildMergeInfo = false;
                TreeMap childMergeInfo = new TreeMap();
                for (Iterator paths = myMergedPaths.keySet().iterator(); paths.hasNext();) {
                    File mergedPath = (File) paths.next();
                    SVNEntry childEntry = myWCAccess.getVersionedEntry(mergedPath, false);
                    if ((childEntry.isDirectory() && myTarget.equals(mergedPath) && depth == SVNDepth.IMMEDIATES) ||
                            (childEntry.isFile() && depth == SVNDepth.FILES)) {
                        isIndirectChildMergeInfo = getWCOrRepositoryMergeInfo(myWCAccess, childMergeInfo, mergedPath, 
                                childEntry, SVNMergeInfoInheritance.INHERITED, isIndirectChildMergeInfo, false, 
                                myRepository1);
                        if (isIndirectChildMergeInfo) {
                            String mergeInfoStr = null;
                            if (!childMergeInfo.isEmpty()) {
                                mergeInfoStr = SVNMergeInfoManager.formatMergeInfoToString(childMergeInfo);
                            }
                            SVNPropertiesManager.setProperty(myWCAccess, mergedPath, SVNProperty.MERGE_INFO, 
                                    mergeInfoStr, true);
                        }
                    }
                }
            }
        }
        
        private void removeAbsentChildren(File target, LinkedList childrenWithMergeInfo) {
            for (Iterator children = childrenWithMergeInfo.iterator(); children.hasNext();) {
                MergePath child = (MergePath) children.next();
                String topDir = target.getAbsolutePath().replace(File.separatorChar, '/');
                String childPath = child.myPath.getAbsolutePath().replace(File.separatorChar, '/');
                if (child != null && child.myIsAbsent && SVNPathUtil.isAncestor(topDir, childPath)) {
                    if (mySkippedPaths != null) {
                        mySkippedPaths.remove(child.myPath);
                    }
                    children.remove();
                }
            }
        }
        
        private void removeFirstRangeFromRemainingRanges(LinkedList childrenWithMergeInfo) {
            for (Iterator children = childrenWithMergeInfo.iterator(); children.hasNext();) {
                MergePath child = (MergePath) children.next();
                if (child == null || child.myIsAbsent) {
                    continue;
                }
                if (child.myRemainingRanges != null && !child.myRemainingRanges.isEmpty()) {
                    SVNMergeRange[] originalRemainingRanges = child.myRemainingRanges.getRanges();
                    SVNMergeRange[] remainingRanges = new SVNMergeRange[originalRemainingRanges.length - 1];
                    for (int i = 1; i < originalRemainingRanges.length; i++) {
                        SVNMergeRange originalRange = originalRemainingRanges[i];
                        remainingRanges[i - 1] = originalRange;
                    }
                    child.myRemainingRanges = new SVNMergeRangeList(remainingRanges);
                }
            }
        }
        
        private void sliceRemainingRanges(LinkedList childrenWithMergeInfo, long endRevision) {
            for (Iterator children = childrenWithMergeInfo.iterator(); children.hasNext();) {
                MergePath child = (MergePath) children.next();
                if (child == null || child.myIsAbsent) {
                    continue;
                }
                if (child.myRemainingRanges != null && !child.myRemainingRanges.isEmpty()) {
                    SVNMergeRange[] originalRemainingRanges = child.myRemainingRanges.getRanges();
                    SVNMergeRange range = originalRemainingRanges[0];
                    if (range.getStartRevision() < endRevision && range.getEndRevision() > endRevision) {
                        SVNMergeRange splitRange1 = new SVNMergeRange(range.getStartRevision(), endRevision, 
                                range.isInheritable());
                        SVNMergeRange splitRange2 = new SVNMergeRange(endRevision + 1, range.getEndRevision(), 
                                range.isInheritable());
                        SVNMergeRange[] remainingRanges = new SVNMergeRange[originalRemainingRanges.length + 1];
                        remainingRanges[0] = splitRange1;
                        remainingRanges[1] = splitRange2;
                        for (int i = 1; i < originalRemainingRanges.length; i++) {
                            SVNMergeRange originalRange = originalRemainingRanges[i];
                            remainingRanges[2 + i] = originalRange;
                        }
                        child.myRemainingRanges = new SVNMergeRangeList(remainingRanges);
                    }
                }
            }
        }
        
        private long getNearestEndRevision(LinkedList childrenWithMergeInfo) {
            long nearestEndRevision = SVNRepository.INVALID_REVISION;
            for (Iterator children = childrenWithMergeInfo.iterator(); children.hasNext();) {
                MergePath child = (MergePath) children.next();
                if (child == null || child.myIsAbsent) {
                    continue;
                }
                if (child.myRemainingRanges != null && !child.myRemainingRanges.isEmpty()) {
                    SVNMergeRange[] remainingRanges = child.myRemainingRanges.getRanges();
                    SVNMergeRange range = remainingRanges[0];
                    if (!SVNRevision.isValidRevisionNumber(nearestEndRevision)) {
                        nearestEndRevision = range.getEndRevision();
                    } else if (range.getEndRevision() < nearestEndRevision) {
                        nearestEndRevision = range.getEndRevision();
                    }
                }
            }
            return nearestEndRevision;
        }
        
        private void populateRemainingRanges(LinkedList childrenWithMergeInfo, 
                SVNRepository repository, SVNMergeRange range, 
                String parentMergeSrcPath, boolean isRollBack) throws SVNException {
            
            for (ListIterator childrenMergePaths = childrenWithMergeInfo.listIterator(); 
            childrenMergePaths.hasNext();) {
                MergePath childMergePath = (MergePath) childrenMergePaths.next();
                if (childMergePath == null || childMergePath.myIsAbsent) {
                    continue;
                }
                
                String childRelativePath = null;
                if (myTarget.equals(childMergePath.myPath)) {
                    childRelativePath = "";
                } else {
                    childRelativePath = SVNPathUtil.getRelativePath(myTarget, childMergePath.myPath);
                }
                String childMergeSrcPath = SVNPathUtil.concatToAbs(parentMergeSrcPath, childRelativePath); 
                SVNEntry childEntry = myWCAccess.getVersionedEntry(childMergePath.myPath, false);
                childMergePath.myPreMergeMergeInfo = new TreeMap();
                childMergePath.myIsIndirectMergeInfo = getWCOrRepositoryMergeInfo(myWCAccess, 
                        childMergePath.myPreMergeMergeInfo, childMergePath.myPath, childEntry, 
                        SVNMergeInfoInheritance.INHERITED, false, false, null);

                childMergePath.myRemainingRanges = calculateRemainingRanges(childEntry.getSVNURL(), childMergeSrcPath, 
                        childEntry, range, childMergePath.myPreMergeMergeInfo, repository, isRollBack);
                
                if (myTarget.equals(childMergePath.myPath) && (childMergePath.myRemainingRanges == null || 
                        childMergePath.myRemainingRanges.isEmpty())) {
                    SVNMergeRange dummyRange = new SVNMergeRange(range.getEndRevision(), range.getEndRevision(), 
                            range.isInheritable());
                    childMergePath.myRemainingRanges = new SVNMergeRangeList(new SVNMergeRange[] { dummyRange });
                    myIsTargetHasDummyMergeRange = true;
                }
            }
        }
        
        private SVNRemoteDiffEditor driveMergeReportEditor(File target, SVNURL url1, SVNURL url2, 
                final LinkedList childrenWithMergeInfo, long start, final long end, SVNDepth depth, 
                boolean ignoreAncestry, SVNAdminArea adminArea, SVNMergeCallback mergeCallback, 
                SVNRemoteDiffEditor editor) throws SVNException {
            
            long defaultStart = start;
            if (myIsTargetHasDummyMergeRange) {
                defaultStart = end;
            } else if (childrenWithMergeInfo != null && !childrenWithMergeInfo.isEmpty()) {
                MergePath targetMergePath = (MergePath) childrenWithMergeInfo.getFirst();
                SVNMergeRangeList remainingRanges = targetMergePath.myRemainingRanges; 
                if (remainingRanges != null && !remainingRanges.isEmpty()) {
                    SVNMergeRange[] ranges = remainingRanges.getRanges();
                    SVNMergeRange range = ranges[0];
                    defaultStart = range.getStartRevision();
                }
            }

            SVNRepository repository2 = null;
            if (editor == null) {
                repository2 = createRepository(url1, false);
                editor = new SVNRemoteDiffEditor(adminArea, adminArea.getRoot(), mergeCallback, repository2, 
                        defaultStart, end, myIsDryRun, this, this);
            } else {
                editor.reset(defaultStart, end);
            }

            final SVNDepth reportDepth = depth;
            final boolean isSameURLs = myIsSameURLs;
            final long reportStart = defaultStart;
            final String targetPath = target.getAbsolutePath().replace(File.separatorChar, '/');
            try {
                myRepository1.diff(url2, end, end, null, ignoreAncestry, depth, true,
                                 new ISVNReporterBaton() {
                                     public void report(ISVNReporter reporter) throws SVNException {
                                         
                                         reporter.setPath("", null, reportStart, reportDepth, false);

                                         if (isSameURLs && childrenWithMergeInfo != null) {
                                             for (Iterator paths = childrenWithMergeInfo.iterator(); paths.hasNext();) {
                                                MergePath childMergePath = (MergePath) paths.next();
                                                if (childMergePath == null || childMergePath.myIsAbsent || 
                                                        childMergePath.myRemainingRanges == null || 
                                                        childMergePath.myRemainingRanges.isEmpty()) {
                                                    continue;
                                                }
                                                
                                                SVNMergeRangeList remainingRangesList = childMergePath.myRemainingRanges; 
                                                SVNMergeRange[] remainingRanges = remainingRangesList.getRanges();
                                                SVNMergeRange range = remainingRanges[0];
                                                
                                                if (range.getStartRevision() == reportStart) {
                                                    continue;
                                                } 
                                                  
                                                String childPath = childMergePath.myPath.getAbsolutePath();
                                                childPath = childPath.replace(File.separatorChar, '/');
                                                String relChildPath = childPath.substring(targetPath.length());
                                                if (relChildPath.startsWith("/")) {
                                                    relChildPath = relChildPath.substring(1);
                                                }
                                                if (range.getStartRevision() > end) {
                                                    reporter.setPath(relChildPath, null, end, reportDepth, false);
                                                } else {
                                                    reporter.setPath(relChildPath, null, range.getStartRevision(), 
                                                            reportDepth, false);
                                                }
                                             }
                                         }
                                         reporter.finishReport();
                                     }
                                 }, 
                                 SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
            } finally {
                if (repository2 != null) {
                    repository2.closeSession();
                }
                editor.cleanup();
            }
            
            if (myConflictedPaths == null) {
                myConflictedPaths = mergeCallback.getConflictedPaths();
            }
                 
            return editor;
        }
        
        private SVNErrorMessage makeMergeConflictError(File targetPath, SVNMergeRange range) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, 
                    "One or more conflicts were produced while merging r{0,number,integer}:{1,number,integer} into\n" + 
                    "''{2}'' --\n" +
                    "resolve all conflicts and rerun the merge to apply the remaining\n" + 
                    "unmerged revisions", new Object[] { new Long(range.getStartRevision()), 
                    new Long(range.getEndRevision()), targetPath} );
            return error;
        }
        
        private LinkedList getMergeInfoPaths(SVNEntry entry, final File target, 
                                             final String mergeSrcPath, final SVNDepth depth) throws SVNException {
            final LinkedList children = new LinkedList();
            ISVNEntryHandler handler = new ISVNEntryHandler() {
                public void handleEntry(File path, SVNEntry entry) throws SVNException {
                    SVNAdminArea adminArea = entry.getAdminArea();
                    if (entry.isDirectory() && !adminArea.getThisDirName().equals(entry.getName()) &&
                            !entry.isAbsent()) {
                        return;
                    }
                
                    if (entry.isScheduledForDeletion() || entry.isDeleted()) {
                        return;
                    }
                    
                    boolean isSwitched = false;
                    boolean hasMergeInfoFromMergeSrc = false;
                    String mergeInfoProp = null;
                    if (!entry.isAbsent()) {
                        SVNVersionedProperties props = adminArea.getProperties(entry.getName());
                        mergeInfoProp = props.getPropertyValue(SVNProperty.MERGE_INFO);
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
                    }

                    File parent = path.getParentFile();
                    if (hasMergeInfoFromMergeSrc || isSwitched || 
                            entry.getDepth() == SVNDepth.EMPTY || 
                            entry.getDepth() == SVNDepth.FILES || entry.isAbsent() || 
                            (depth == SVNDepth.IMMEDIATES && entry.isDirectory() &&
                                    parent != null && parent.equals(target))) {
                        boolean hasMissingChild = entry.getDepth() == SVNDepth.EMPTY || 
                        entry.getDepth() == SVNDepth.FILES || (depth == SVNDepth.IMMEDIATES && 
                                entry.isDirectory() && parent != null && parent.equals(target)); 
                        
                        boolean hasNonInheritable = false;
                        String propVal = null;
                        if (mergeInfoProp != null) {
                            if (mergeInfoProp.indexOf(SVNMergeRangeList.MERGE_INFO_NONINHERITABLE_STRING) != -1) {
                                hasNonInheritable = true;
                            }
                            propVal = mergeInfoProp;
                        }
                        
                        if (!hasNonInheritable && (entry.getDepth() == SVNDepth.EMPTY || 
                                entry.getDepth() == SVNDepth.FILES)) {
                            hasNonInheritable = true;
                        }
                        
                        MergePath child = new MergePath(path, hasMissingChild, isSwitched, 
                                hasNonInheritable, entry.isAbsent(), propVal);
                        children.add(child);
                    }
                }
                
                public void handleError(File path, SVNErrorMessage error) throws SVNException {
                    while (error.hasChildErrorMessage()) {
                        error = error.getChildErrorMessage();
                    }
                    if (error.getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND || 
                            error.getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                        return;
                    }
                    SVNErrorManager.error(error);
                }
            };
            
            if (entry.isFile()) {
                handler.handleEntry(myTarget, entry);
            } else {
                myWCAccess.walkEntries(myTarget, handler, true, depth);
            }
            
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
                                                                             false, false, false, 
                                                                             null);
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
                
                if (child.myIsAbsent || (child.myIsSwitched && !myTarget.equals(child.myPath))) {
                    File parentPath = child.myPath.getParentFile();
                    int parentInd = children.indexOf(new MergePath(parentPath));
                    MergePath parent = parentInd != -1 ? (MergePath) children.get(parentInd)
                                                       : null;
                    if (parent != null) {
                        parent.myHasMissingChildren = true; 
                    } else {
                        parent = new MergePath(parentPath, true, false, false, false, null);
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
                            MergePath siblingOfMissing = new MergePath(siblingPath, false, false, 
                                    false, false, null);
                            mergePaths.add(siblingOfMissing);
                        }
                    }
                }
            }
            
            if (children.isEmpty() || !children.contains(new MergePath(myTarget))) {
                boolean hasMissingChild = entry.getDepth() == SVNDepth.EMPTY || 
                entry.getDepth() == SVNDepth.FILES;
                MergePath targetItem = new MergePath(myTarget, hasMissingChild, false, 
                        hasMissingChild, false, null);
                children.add(targetItem);
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
        
        private void cleanUpNoOpMerge(LinkedList childrenWithMergeInfo) throws SVNException {
            if (childrenWithMergeInfo != null && !myIsOperativeMerge && !myIsDryRun &&
                myIsSameRepository && !myIsRecordOnly) {
                for (Iterator mergePaths = childrenWithMergeInfo.iterator(); mergePaths.hasNext();) {
                    MergePath child = (MergePath) mergePaths.next();
                    if (!myTarget.equals(child.myPath)) {
                        SVNPropertiesManager.setProperty(myWCAccess, child.myPath, 
                                                         SVNProperty.MERGE_INFO, 
                                                         child.myMergeInfoPropValue, 
                                                         true);
                    }
                }
            }
        }
        
        private Map determinePerformedMerges(File targetPath, SVNMergeRange range, SVNDepth depth) throws SVNException {
            int numberOfSkippedPaths = mySkippedPaths != null ? mySkippedPaths.size() : 0;
            Map merges = new TreeMap();
            if (myOperativeNotificationsNumber == 0 && !myIsOperativeMerge && 
                    myTarget.equals(targetPath)) {
                return merges;
            }
            
            if (myOperativeNotificationsNumber > 0 && !myIsOperativeMerge) {
                myIsOperativeMerge = true;
            }

            SVNMergeRangeList rangeList = new SVNMergeRangeList(new SVNMergeRange[] { range } );
            merges.put(targetPath, rangeList);
                
            if (numberOfSkippedPaths > 0) {
                for (Iterator skippedPaths = mySkippedPaths.iterator(); skippedPaths.hasNext();) {
                    File skippedPath = (File) skippedPaths.next();
                    merges.put(skippedPath, new SVNMergeRangeList(new SVNMergeRange[0]));
                    //TODO: numberOfSkippedPaths < myOperativeNotificationsNumber
                }
            }
            
            if (depth != SVNDepth.INFINITY && myMergedPaths != null) {
                for (Iterator mergedPathsIter = myMergedPaths.keySet().iterator(); 
                mergedPathsIter.hasNext();) {
                    File mergedPath = (File) mergedPathsIter.next();
                    SVNMergeRangeList childRangeList = null;
                    SVNMergeRange childMergeRange = new SVNMergeRange(range.getStartRevision(), range.getEndRevision(), 
                            range.isInheritable());
                    SVNEntry childEntry = myWCAccess.getVersionedEntry(mergedPath, false);
                    if ((childEntry.isDirectory() && mergedPath.equals(myTarget) && 
                            depth == SVNDepth.IMMEDIATES) || (childEntry.isFile() && 
                                    depth == SVNDepth.FILES)) {
                        childMergeRange.setInheritable(true);
                        childRangeList = new SVNMergeRangeList(new SVNMergeRange[] { childMergeRange } );
                        merges.put(mergedPath, childRangeList);
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
                Map fileToProp = SVNPropertiesManager.getWorkingCopyPropertyValues(entry, 
                                                                                   SVNProperty.MERGE_INFO, 
                                                                                   SVNDepth.EMPTY, 
                                                                                   false); 

                String propValue = (String) fileToProp.get(path);
                Map mergeInfo = null;
                if (propValue != null) {
                    mergeInfo = SVNMergeInfoManager.parseMergeInfo(new StringBuffer(propValue), 
                            mergeInfo);
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
                    rangeList = rangeList.merge(ranges, SVNMergeRangeInheritance.EQUAL_INHERITANCE);
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
        
        private SVNMergeRangeList calculateRemainingRanges(SVNURL url, String reposPath, SVNEntry entry, 
                SVNMergeRange range, Map targetMergeInfo, SVNRepository repository, boolean isRollBack) throws SVNException {
            SVNMergeRangeList requestedRangeList = calculateRequestedRanges(range, url, entry, repository);
            SVNMergeRangeList remainingRangeList = calculateMergeRanges(reposPath, requestedRangeList, targetMergeInfo, 
                    isRollBack);
            return remainingRangeList;
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
                String tgtReposPath = entry.getSVNURL().getPath().substring(reposRoot.getPath().length());
                if (!tgtReposPath.startsWith("/")) {
                    tgtReposPath = "/" + tgtReposPath;
                }
                srcRangeListForTgt = (SVNMergeRangeList) added.get(tgtReposPath);
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
        boolean myIsAbsent;
        boolean myIsIndirectMergeInfo;
        String myMergeInfoPropValue;
        SVNMergeRangeList myRemainingRanges;
        Map myPreMergeMergeInfo;
        
        public MergePath(File path) {
            myPath = path;
        }
        
        public MergePath(File path, boolean hasMissingChildren, boolean isSwitched, 
                boolean hasNonInheritableMergeInfo, boolean absent, String propValue) {
            myPath = path;
            myHasNonInheritableMergeInfo = hasNonInheritableMergeInfo;
            myIsSwitched = isSwitched;
            myHasMissingChildren = hasMissingChildren;
            myIsAbsent = absent;
            myMergeInfoPropValue = propValue;
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