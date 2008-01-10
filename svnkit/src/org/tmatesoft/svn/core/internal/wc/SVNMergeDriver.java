/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNEntryHandler;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNLocationSegment;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNBasicClient;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public abstract class SVNMergeDriver extends SVNBasicClient {

    /**
     * @deprecated
     */
    boolean myIsSameURLs = false;
    /**
     * @deprecated
     */
    boolean myHasMissingChildren = false;

    protected boolean myAreSourcesAncestral;
    protected boolean myIsSameRepository;
    protected boolean myIsDryRun;
    protected boolean myIsRecordOnly;
    protected boolean myIsForce;
    protected boolean myIsTargetMissingChild;
    protected boolean myHasExistingMergeInfo;
    protected boolean myIsOperativeMerge;
    protected boolean myIsTargetHasDummyMergeRange;
    protected boolean myIsIgnoreAncestry;
    protected boolean myIsSingleFileMerge;
    protected boolean myIsMergeInfoCapable;
    protected boolean myIsAddNecessitatedMerge;
    protected boolean myIsOverrideSet;
    protected int myOperativeNotificationsNumber;
    protected int myNotificationsNumber;
    protected int myCurrentAncestorIndex;
    protected List myMergedPaths;
    protected Map myConflictedPaths;
    protected Map myDryRunDeletions;
    protected SVNURL myURL;
    protected File myTarget;
    protected File myAddedPath;
    protected List mySkippedPaths;
    protected List myChildrenWithMergeInfo;
    protected SVNWCAccess myWCAccess;
    protected SVNRepository myRepository1;
    protected SVNRepository myRepository2;
    
    public SVNMergeDriver(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNMergeDriver(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }
    
    public abstract SVNDiffOptions getMergeOptions();

    protected void init(SVNRepository repository1, SVNRepository repository2, SVNURL url, 
            SVNEntry entry, File target, SVNWCAccess access, boolean dryRun, boolean force, 
            boolean recordOnly) throws SVNException {
        myURL = url;
        myTarget = target;
        myIsForce = force;
        myIsDryRun = dryRun;
        myIsRecordOnly = recordOnly;
        myOperativeNotificationsNumber = 0;
        myWCAccess = access;
        myRepository1 = repository1;
        myRepository2 = repository2;
        myIsSameURLs = false;
        myHasMissingChildren = false;
        myHasExistingMergeInfo = false;
        myIsOperativeMerge = false;
        myIsTargetHasDummyMergeRange = false;
        myMergedPaths = null;
        myConflictedPaths = null;
        mySkippedPaths = null;
        
        if (dryRun) {
            myIsSameRepository = false;
        } else {
            SVNURL reposRoot = repository1.getRepositoryRoot(true);
            myIsSameRepository = SVNPathUtil.isAncestor(reposRoot.toDecodedString(), 
                    entry.getRepositoryRootURL().toDecodedString());
        }
    }

/*    protected void init2( SVNEntry entry, File target, SVNWCAccess access, boolean dryRun, boolean force, 
            boolean recordOnly, boolean ignoreAncestry, boolean sourcesRelated, boolean sameRepository,
            boolean targetMissingChild) throws SVNException {
        myURL = url;
        myTarget = target;
        myIsForce = force;
        myIsDryRun = dryRun;
        myIsRecordOnly = recordOnly;
        myOperativeNotificationsNumber = 0;
        myWCAccess = access;
        myRepository1 = repository1;
        myRepository2 = repository2;
        myIsSameURLs = false;
        myHasMissingChildren = false;
        myHasExistingMergeInfo = false;
        myIsOperativeMerge = false;
        myIsTargetHasDummyMergeRange = false;
        myMergedPaths = null;
        myConflictedPaths = null;
        mySkippedPaths = null;
    }
*/
    protected void runPeggedMerge(SVNURL srcURL, File srcPath, SVNRevision pegRevision, 
            SVNRevision revision1, SVNRevision revision2, File dstPath, SVNDepth depth, boolean dryRun, 
            boolean force, boolean ignoreAncestry, boolean recordOnly) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        dstPath = dstPath.getAbsoluteFile();
        try {
            dstPath = dstPath.getAbsoluteFile();
            SVNAdminArea adminArea = wcAccess.probeOpen(dstPath, !dryRun, SVNWCAccess.INFINITE_DEPTH);

            SVNEntry targetEntry = wcAccess.getVersionedEntry(dstPath, false);
            SVNURL wcReposRoot = null;
            if (targetEntry.getRepositoryRoot() != null) {
                wcReposRoot = targetEntry.getRepositoryRootURL();
            } else {
                SVNRepository repos = createRepository(null, dstPath, SVNRevision.WORKING, SVNRevision.WORKING);
                wcReposRoot = repos.getRepositoryRoot(true);
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

            init(repository1, null, url, targetEntry, dstPath, wcAccess, dryRun, force, recordOnly);
            
            SVNRevision[] revs = getAssumedDefaultRevisionRange(revision1, revision2, myRepository1);
            SVNRepositoryLocation[] locations = getLocations(url, srcPath, null, pegRevision, 
                    revs[0], revs[1]);

            SVNURL url1 = locations[0].getURL();
            SVNURL url2 = locations[1].getURL();
            revision1 = SVNRevision.create(locations[0].getRevisionNumber());
            revision2 = SVNRevision.create(locations[1].getRevisionNumber());

            myIsSameURLs = url1.equals(url2);
            if (!myIsSameURLs && recordOnly) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
                "Use of two URLs is not compatible with mergeinfo modification"); 
                SVNErrorManager.error(err);
            }
            myRepository1.setLocation(url1, true);
            if (!revision1.isValid() || !revision2.isValid()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                        "Not all required revisions are specified");            
                SVNErrorManager.error(err);
            }

            Object[] mergeActionInfo = grokRangeInfoFromRevisions(myRepository1, myRepository1, 
                    revision1, revision2);

            SVNMergeRange range = (SVNMergeRange) mergeActionInfo[0];
            MergeAction mergeAction = (MergeAction) mergeActionInfo[1];
            if (mergeAction == MergeAction.NO_OP || (recordOnly && dryRun)) {
                return;
            }
            
            boolean isRollBack = mergeAction == MergeAction.ROLL_BACK;
            if (myIsSameRepository && recordOnly) {
                recordMergeInfoForRecordOnlyMerge(url1, range, isRollBack, targetEntry);
                return;
            }
            
            if (targetEntry.isFile()) {
                doMergeFile(url1, range.getStartRevision(), url2, range.getEndRevision(), 
                        dstPath, adminArea, ignoreAncestry, isRollBack);
            } else if (targetEntry.isDirectory()) {
                LinkedList childrenWithMergeInfo = null;
                if (myIsSameURLs) {
                    childrenWithMergeInfo = discoverAndMergeChildren(targetEntry, range.getStartRevision(), 
                            range.getEndRevision(), depth, url1, wcReposRoot, adminArea, ignoreAncestry, 
                            isRollBack);
                    if (!dryRun && myIsOperativeMerge) {
                        elideChildren(childrenWithMergeInfo, dstPath, targetEntry);
                    }
                } else {
                    doMerge(url1, range.getStartRevision(), url2, range.getEndRevision(), dstPath, adminArea, 
                            depth, childrenWithMergeInfo, ignoreAncestry, myHasMissingChildren);
                }
            }
            
            if (!dryRun && myIsOperativeMerge) {
                elideMergeInfo(wcAccess, dstPath, false, null);
            }
        } finally {
            try {
                wcAccess.close();
            } catch (SVNException svne) {
                //
            }

            if (myRepository1 != null) {
                myRepository1.closeSession();
            }
            if (myRepository2 != null) {
                myRepository2.closeSession();
            }
        }
    }

    protected void runMerge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, 
            File dstPath, SVNDepth depth, boolean dryRun, boolean force, 
            boolean ignoreAncestry, boolean recordOnly) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        dstPath = dstPath.getAbsoluteFile();
        try {
            dstPath = dstPath.getAbsoluteFile();
            SVNAdminArea adminArea = wcAccess.probeOpen(dstPath, !dryRun, SVNWCAccess.INFINITE_DEPTH);

            SVNEntry targetEntry = wcAccess.getVersionedEntry(dstPath, false);
            SVNURL wcReposRoot = null;
            if (targetEntry.getRepositoryRoot() != null) {
                wcReposRoot = targetEntry.getRepositoryRootURL();
            } else {
                SVNRepository repos = createRepository(null, dstPath, SVNRevision.WORKING, SVNRevision.WORKING);
                wcReposRoot = repos.getRepositoryRoot(true);
            }
            
            if (depth == null || depth == SVNDepth.UNKNOWN) {
                depth = targetEntry.getDepth();
            }
            
            SVNRepository repository1 = createRepository(url1, false);
            SVNRepository repository2 = createRepository(url2, false);

            init(repository1, repository2, url2, targetEntry, dstPath, wcAccess, dryRun, force,  
                    recordOnly);
            

            if (!revision1.isValid() || !revision2.isValid()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                        "Not all required revisions are specified");            
                SVNErrorManager.error(err);
            }
            
            myIsSameURLs = url1.equals(url2);
            if (!myIsSameURLs && recordOnly) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
                "Use of two URLs is not compatible with mergeinfo modification"); 
                SVNErrorManager.error(err);
            }

            Object[] mergeActionInfo = grokRangeInfoFromRevisions(myRepository1, myRepository2, 
                    revision1, revision2);

            SVNMergeRange range = (SVNMergeRange) mergeActionInfo[0];
            MergeAction mergeAction = (MergeAction) mergeActionInfo[1];
            if (mergeAction == MergeAction.NO_OP || (recordOnly && dryRun)) {
                return;
            }
            
            boolean isRollBack = mergeAction == MergeAction.ROLL_BACK;
            if (myIsSameRepository && recordOnly) {
                recordMergeInfoForRecordOnlyMerge(url1, range, isRollBack, targetEntry);
                return;
            }
            
            if (targetEntry.isFile()) {
                doMergeFile(url1, range.getStartRevision(), url2, range.getEndRevision(),
                        dstPath, adminArea, ignoreAncestry, isRollBack);
            } else if (targetEntry.isDirectory()) {
                LinkedList childrenWithMergeInfo = null;
                if (myIsSameURLs) {
                    childrenWithMergeInfo = discoverAndMergeChildren(targetEntry, range.getStartRevision(), 
                            range.getEndRevision(), depth, url1, 
                            wcReposRoot, adminArea, ignoreAncestry, 
                            isRollBack);
                    if (!dryRun && myIsOperativeMerge) {
                        elideChildren(childrenWithMergeInfo, dstPath, targetEntry);
                    }
                } else {
                    doMerge(url1, range.getStartRevision(), url2, range.getEndRevision(), dstPath, adminArea, 
                            depth, childrenWithMergeInfo, ignoreAncestry, myHasMissingChildren);
                }
            }
            
            if (!dryRun && myIsOperativeMerge) {
                elideMergeInfo(wcAccess, dstPath, false, null);
            }
        } finally {
            try {
                wcAccess.close();
            } catch (SVNException svne) {
                //
            }

            if (myRepository1 != null) {
                myRepository1.closeSession();
            }

            if (myRepository2 != null) {
                myRepository2.closeSession();
            }
        }
    }
    
    protected void runMerge2(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, 
            File targetWCPath, SVNDepth depth, boolean dryRun, boolean force, 
            boolean ignoreAncestry, boolean recordOnly) throws SVNException {
        
        if (!revision1.isValid() || !revision2.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                    "Not all required revisions are specified");            
            SVNErrorManager.error(err);
        }
        
        SVNRepository repository1 = null;
        SVNRepository repository2 = null;
        myWCAccess = createWCAccess();
        targetWCPath = targetWCPath.getAbsoluteFile();
        try {
            SVNAdminArea adminArea = myWCAccess.probeOpen(targetWCPath, !dryRun, SVNWCAccess.INFINITE_DEPTH);

            SVNEntry entry = myWCAccess.getVersionedEntry(targetWCPath, false);
            SVNURL wcReposRoot = getReposRoot(targetWCPath, null, SVNRevision.WORKING, adminArea, myWCAccess);
                
            long[] latestRev = new long[1];
            latestRev[0] = SVNRepository.INVALID_REVISION;

            repository1 = createRepository(url1, false);
            SVNURL sourceReposRoot = repository1.getRepositoryRoot(true); 
            long rev1 = getRevisionNumber(revision1, latestRev, repository1, null); 

            repository2 = createRepository(url2, false);
            long rev2 = getRevisionNumber(revision2, latestRev, repository2, null); 
            
            boolean sameRepos = sourceReposRoot.equals(wcReposRoot);
            String youngestCommonPath = null;
            long youngestCommonRevision = SVNRepository.INVALID_REVISION;
            if (!ignoreAncestry) {
            	SVNLocationEntry youngestLocation = getYoungestCommonAncestor(null, url1, 
            			SVNRevision.create(rev1), null, url2, SVNRevision.create(rev2));
            	youngestCommonPath = youngestLocation.getPath();
            	youngestCommonRevision = youngestLocation.getRevision();
            }

            boolean related = false;
            boolean ancestral = false;
            List mergeSources = null;
            if (youngestCommonPath != null && SVNRevision.isValidRevisionNumber(youngestCommonRevision)) {
            	SVNRevisionRange range = null;
            	List ranges = new LinkedList();
            	related = true;
            	SVNURL youngestCommonURL = sourceReposRoot.appendPath(youngestCommonPath, false);

            	if (youngestCommonURL.equals(url2) && youngestCommonRevision == rev2) {
            		ancestral = true;
            		SVNRevision sRev = SVNRevision.create(rev1);
            		SVNRevision eRev = SVNRevision.create(youngestCommonRevision);
            		range = new SVNRevisionRange(sRev, eRev);
            		ranges.add(range);
            		mergeSources = normalizeMergeSources(url1, url1, sourceReposRoot, sRev, 
            				ranges, repository1);
            	} else if (youngestCommonURL.equals(url1) && youngestCommonRevision == rev1) {
            		ancestral = true;
            		SVNRevision sRev = SVNRevision.create(youngestCommonRevision);
            		SVNRevision eRev = SVNRevision.create(rev2);
            		range = new SVNRevisionRange(sRev, eRev);
            		ranges.add(range);
            		mergeSources = normalizeMergeSources(url2, url2, sourceReposRoot, eRev, 
            				ranges, repository2);
            	} else {
            		SVNRevision sRev = SVNRevision.create(rev1);
            		SVNRevision eRev = SVNRevision.create(youngestCommonRevision);
            		range = new SVNRevisionRange(sRev, eRev);
            		ranges.add(range);
            		List removeSources = normalizeMergeSources(url1, url1, sourceReposRoot, sRev, 
            				ranges, repository1);
            		sRev = eRev;
            		eRev = SVNRevision.create(rev2);
            		range = new SVNRevisionRange(sRev, eRev);
            		ranges.clear();
            		ranges.add(range);
            		List addSources = normalizeMergeSources(url2, url2, sourceReposRoot, eRev, 
            				ranges, repository2);
            		
            		repository1.closeSession();
            		repository2.closeSession();
            		
            		if (!recordOnly) {
            			MergeSource fauxSource = new MergeSource();
            			fauxSource.myURL1 = url1;
            			fauxSource.myURL2 = url2;
            			fauxSource.myRevision1 = rev1;
            			fauxSource.myRevision2 = rev2;
            			List fauxSources = new LinkedList();
            			fauxSources.add(fauxSource);
            			doMerge(fauxSources, targetWCPath, entry, adminArea, ancestral, related, sameRepos, 
            					ignoreAncestry, force, dryRun, recordOnly, depth);
            		}
            		
            		doMerge(addSources, targetWCPath, entry, adminArea, true, related, sameRepos, 
            				ignoreAncestry, force, dryRun, true, depth);
            		doMerge(removeSources, targetWCPath, entry, adminArea, true, related, sameRepos, 
            				ignoreAncestry, force, dryRun, true, depth);
            		return;
            	}
            } else {
                MergeSource mergeSrc = new MergeSource();
                mergeSrc.myURL1 = url1;
                mergeSrc.myURL2 = url2;
                mergeSrc.myRevision1 = rev1;
                mergeSrc.myRevision2 = rev2;
                mergeSources.add(mergeSrc);
            }

    		repository1.closeSession();
    		repository2.closeSession();
            
    		doMerge(mergeSources, targetWCPath, entry, adminArea, ancestral, related, sameRepos, 
    				ignoreAncestry, force, dryRun, recordOnly, depth);
        } finally {
        	if (repository1 != null) {
        		repository1.closeSession();
        	}
        	if (repository2 != null) {
        		repository2.closeSession();
        	}
        	try {
                myWCAccess.close();
            } catch (SVNException svne) {
                //
            }
         }
    }
    
    protected void doMerge(Collection mergeSources, File target, SVNEntry targetEntry, SVNAdminArea adminArea, 
            boolean sourcesAncestral, boolean sourcesRelated, boolean sameRepository, boolean ignoreAncestry, 
            boolean force, boolean dryRun, boolean recordOnly, SVNDepth depth) throws SVNException {
        if (recordOnly && dryRun) {
            return;
        }
        
        if (recordOnly && !sourcesAncestral) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
            "Use of two URLs is not compatible with mergeinfo modification"); 
            SVNErrorManager.error(err);
        }
        
        if (depth == SVNDepth.UNKNOWN) {
        }
        depth = targetEntry.getDepth();
        
        myIsForce = force;
        myIsDryRun = dryRun;
        myIsRecordOnly = recordOnly;
        myIsIgnoreAncestry = ignoreAncestry;
        myIsSameRepository = sameRepository;
        myIsMergeInfoCapable = false;
        myAreSourcesAncestral = sourcesAncestral;
        myIsTargetMissingChild = false;
        myIsSingleFileMerge = false;
        myTarget = target;
        myNotificationsNumber = 0;
        myOperativeNotificationsNumber = 0;
        myCurrentAncestorIndex = -1;
        myMergedPaths = null;
        mySkippedPaths = null;
        myChildrenWithMergeInfo = null;
        myHasExistingMergeInfo = false;
        
        boolean checkedMergeInfoCapability = false;
        for (Iterator mergeSourcesIter = mergeSources.iterator(); mergeSourcesIter.hasNext();) {
            MergeSource mergeSource = (MergeSource) mergeSourcesIter.next();
            SVNURL url1 = mergeSource.myURL1;
            SVNURL url2 = mergeSource.myURL2;
            long revision1 = mergeSource.myRevision1;
            long revision2 = mergeSource.myRevision2;
            if (revision1 == revision2 && mergeSource.myURL1.equals(mergeSource.myURL2)) {
                return;
            }
            
            try {
                myRepository1 = createRepository(url1, false);
                myRepository2 = createRepository(url2, false);
                myIsTargetHasDummyMergeRange = false;
                myURL = url2;
                myAddedPath = null;
                myConflictedPaths = null;
                myDryRunDeletions = dryRun ? new HashMap() : null;
                myIsOperativeMerge = false;
                myIsAddNecessitatedMerge = false;
                myIsOverrideSet = false;
             
                if (!checkedMergeInfoCapability) {
                	myIsMergeInfoCapable = myRepository1.hasCapability(SVNCapability.MERGE_INFO);
                	checkedMergeInfoCapability = true;
                }
                
                if (myIsSameRepository && recordOnly) {
                    SVNURL mergeSourceURL = revision1 < revision2 ? url2 : url1;
                    SVNMergeRange range = new SVNMergeRange(revision1, revision2, true);
                    recordMergeInfoForRecordOnlyMerge2(mergeSourceURL, range, targetEntry);
                    continue;
                }
                
                if (targetEntry.isFile()) {
                    doFileMerge(url1, revision1, url2, revision2, target, adminArea, sourcesRelated);
                } else if (targetEntry.isDirectory()) {
                    doDirectoryMerge(url1, revision1, url2, revision2, targetEntry, adminArea, depth);
                }
                
                if (!dryRun && myIsOperativeMerge) {
                    elideMergeInfo2(myWCAccess, target, targetEntry, null);
                }
            } finally {
                if (myRepository1 != null) {
                    myRepository1.closeSession();
                }
                if (myRepository2 != null) {
                    myRepository2.closeSession();
                }
            }
        }
    }
    
    /**
     * @deprecated
     */
    protected void doMergeFile(SVNURL url1, long revision1, SVNURL url2, long revision2, 
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
        boolean[] isIndirect = new boolean[1];
        isIndirect[0] = false; 
        String reposPath = null;
        SVNMergeRangeList remainingRangesList = null;
        Map targetMergeInfo = null;
        
        if (myIsSameURLs && myIsSameRepository) {
            myRepository1.setLocation(entry.getSVNURL(), true);
            targetMergeInfo = getWCOrRepositoryMergeInfo(myWCAccess, dstPath, entry, 
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
            this.handleEvent(SVNEventFactory.createSVNEvent(dstPath, SVNNodeKind.NONE, null, SVNRepository.INVALID_REVISION, SVNEventAction.MERGE_BEGIN, null, null, myIsSameURLs ? nextRange : null), ISVNEventHandler.UNKNOWN);

            SVNProperties props1 = new SVNProperties();
            SVNProperties props2 = new SVNProperties();
            File f1 = null;
            File f2 = null;

            String name = dstPath.getName();
            String mimeType2;
            String mimeType1;
            SVNStatusType[] mergeResult;

            try {
                f1 = loadFile(myRepository1, nextRange.getStartRevision(), props1, adminArea);
                f2 = loadFile(myRepository2, nextRange.getEndRevision(), props2, adminArea);

                mimeType1 = props1.getStringValue(SVNProperty.MIME_TYPE);
                mimeType2 = props2.getStringValue(SVNProperty.MIME_TYPE);
                props1 = filterProperties(props1, true, false, false);
                props2 = filterProperties(props2, true, false, false);

                SVNProperties propsDiff = computePropsDiff(props1, props2);
                
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
                            if (i == 0 && isIndirect[0]) {
                                SVNPropertiesManager.recordWCMergeInfo(dstPath, targetMergeInfo, myWCAccess);
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

    protected void doFileMerge(SVNURL url1, long revision1, SVNURL url2, long revision2, 
            File targetWCPath, SVNAdminArea adminArea, boolean sourcesRelated) throws SVNException {
        boolean isRollBack = revision1 > revision2;
        SVNURL primaryURL = isRollBack ? url1 : url2;
        boolean honorMergeInfo = isHonorMergeInfo();
        boolean recordMergeInfo = isRecordMergeInfo();
        myIsSingleFileMerge = true;
        boolean[] indirect = { false };
        Map targetMergeInfo = null;
        String mergeInfoPath = null;
        SVNMergeRangeList remainingRangeList = null;
        SVNErrorMessage error = null;
        
        myWCAccess.probeTry(targetWCPath, true, SVNWCAccess.INFINITE_DEPTH);
        SVNEntry entry = myWCAccess.getVersionedEntry(targetWCPath, false);
        
        SVNMergeRange range = new SVNMergeRange(revision1, revision2, true);
        if (honorMergeInfo) {
            myRepository1.setLocation(entry.getSVNURL(), false);
            Map[] fullMergeInfo = getFullMergeInfo(entry, indirect, SVNMergeInfoInheritance.INHERITED, 
                    myRepository1, targetWCPath, Math.max(revision1, revision2), Math.min(revision1, revision2));
            targetMergeInfo = fullMergeInfo[0];
            Map implicitMergeInfo = fullMergeInfo[1];
            myRepository1.setLocation(url1, false);
            SVNURL sourceRootURL = myRepository1.getRepositoryRoot(true);
            mergeInfoPath = getPathRelativeToRoot(null, primaryURL, sourceRootURL, null, null);
            remainingRangeList = calculateRemainingRanges(sourceRootURL, url1, revision1, url2, revision2, true, 
                    targetMergeInfo, implicitMergeInfo, entry, myRepository1);
        } else {
            remainingRangeList = new SVNMergeRangeList(range);
        }
        
        SVNMergeRange[] remainingRanges = remainingRangeList.getRanges();
        SVNMergeCallback callback = new SVNMergeCallback(adminArea, myURL, myIsForce, myIsDryRun, 
                getMergeOptions(), myConflictedPaths);

        for (int i = 0; i < remainingRanges.length; i++) {
            SVNMergeRange nextRange = remainingRanges[i];
            this.handleEvent(SVNEventFactory.createSVNEvent(targetWCPath, SVNNodeKind.UNKNOWN, null, 
                    SVNRepository.INVALID_REVISION, SVNEventAction.MERGE_BEGIN, null, null, 
                    myAreSourcesAncestral ? nextRange : null), ISVNEventHandler.UNKNOWN);

            SVNProperties props1 = new SVNProperties();
            SVNProperties props2 = new SVNProperties();
            File f1 = null;
            File f2 = null;

            String name = targetWCPath.getName();
            String mimeType2;
            String mimeType1;
            SVNStatusType[] mergeResult;

            try {
                f1 = loadFile(myRepository1, nextRange.getStartRevision(), props1, adminArea);
                f2 = loadFile(myRepository2, nextRange.getEndRevision(), props2, adminArea);

                mimeType1 = props1.getStringValue(SVNProperty.MIME_TYPE);
                mimeType2 = props2.getStringValue(SVNProperty.MIME_TYPE);
                props1 = filterProperties(props1, true, false, false);
                props2 = filterProperties(props2, true, false, false);

                SVNProperties propsDiff = computePropsDiff(props1, props2);
                
                if (!(myIsIgnoreAncestry || sourcesRelated)) {
                    SVNStatusType cstatus = callback.fileDeleted(name, f1, f2, mimeType1, 
                            mimeType2, props1);
                    notifySingleFileMerge(targetWCPath, SVNEventAction.UPDATE_DELETE, cstatus, 
                    		SVNStatusType.UNKNOWN);
                    
                    mergeResult = callback.fileAdded(name, f1, f2, nextRange.getStartRevision(), 
                                                     nextRange.getEndRevision(), mimeType1, mimeType2, 
                                                     props1, propsDiff);
                    
                    notifySingleFileMerge(targetWCPath, SVNEventAction.UPDATE_ADD, 
                                    mergeResult[0], mergeResult[1]);
                } else {
                    mergeResult = callback.fileChanged(name, f1, f2, nextRange.getStartRevision(), 
                                                       nextRange.getEndRevision(), mimeType1, 
                                                       mimeType2, props1, propsDiff);
                    notifySingleFileMerge(targetWCPath, SVNEventAction.UPDATE_UPDATE, 
                                    mergeResult[0], mergeResult[1]);
                }
            } finally {
                SVNFileUtil.deleteAll(f1, null);
                SVNFileUtil.deleteAll(f2, null);
            }
            
            if (i < remainingRanges.length - 1 && myConflictedPaths != null && !myConflictedPaths.isEmpty()) {
                error = makeMergeConflictError(targetWCPath, nextRange);
                break;
            }
        }
        
        if (recordMergeInfo && remainingRanges.length > 0) {
            Map merges = determinePerformedMerges(targetWCPath, range, SVNDepth.INFINITY);
            if (myIsOperativeMerge) {
                if (indirect[0]) {
                    SVNPropertiesManager.recordWCMergeInfo(targetWCPath, targetMergeInfo, myWCAccess);
                }
                updateWCMergeInfo(targetWCPath, mergeInfoPath, entry, merges, isRollBack);
            }
        }

        sleepForTimeStamp();
        if (error != null) {
            SVNErrorManager.error(error);
        }
    }

    protected void doDirectoryMerge(SVNURL url1, long revision1, SVNURL url2, long revision2,
    		SVNEntry parentEntry, SVNAdminArea adminArea, SVNDepth depth) throws SVNException {
        File targetWCAccessPath = adminArea.getRoot();
    	boolean isRollBack = revision1 > revision2;
    	SVNURL primaryURL = isRollBack ? url1 : url2;
    	boolean honorMergeInfo = isHonorMergeInfo();
    	boolean recordMergeInfo = isRecordMergeInfo();
    	boolean sameURLs = url1.equals(url2);
    	
    	myChildrenWithMergeInfo = new LinkedList();
    	if (!(myAreSourcesAncestral && myIsSameRepository)) {
    		if (myAreSourcesAncestral) {
    			
    		}
    	}
    }
    
    protected void doMerge(SVNURL url1, long revision1, SVNURL url2, long revision2, 
                         final File dstPath, SVNAdminArea adminArea, 
                         SVNDepth depth, final LinkedList childrenWithMergeInfo,
                         boolean ignoreAncestry, boolean targetMissingChild) throws SVNException {

        SVNMergeRange range = new SVNMergeRange(revision1, revision2, !targetMissingChild && 
                (depth == SVNDepth.INFINITY || depth == SVNDepth.IMMEDIATES));
        
        this.handleEvent(SVNEventFactory.createSVNEvent(dstPath, SVNNodeKind.NONE, null, SVNRepository.INVALID_REVISION, SVNEventAction.MERGE_BEGIN, null, null, myIsSameURLs ? range : null),
                ISVNEventHandler.UNKNOWN);

        SVNMergeCallback mergeCallback = new SVNMergeCallback(adminArea, myURL, myIsForce, myIsDryRun, 
                getMergeOptions(), myConflictedPaths);

        driveMergeReportEditor(dstPath, url1, url2, childrenWithMergeInfo, range.getStartRevision(), 
                range.getEndRevision(), depth, ignoreAncestry, adminArea, mergeCallback, null);

        sleepForTimeStamp();
    }

    protected LinkedList discoverAndMergeChildren(SVNEntry parentEntry, long revision1, 
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
                    childRelPath = SVNPathUtil.getRelativePath(myTarget.getAbsolutePath(), child.myPath.getAbsolutePath());
                }
                
                SVNEntry childEntry = myWCAccess.getVersionedEntry(child.myPath, false);
                String childMergeSourcePath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(parentMergeSourcePath, childRelPath));
                if (myIsOperativeMerge) {
                    TreeMap childMerges = new TreeMap();
                    SVNMergeRange childMergeRange = new SVNMergeRange(range.getStartRevision(), 
                            range.getEndRevision(), childEntry.isFile() ? true : (!myHasMissingChildren || 
                                    (depth != SVNDepth.INFINITY && depth != SVNDepth.IMMEDIATES)));
                    SVNMergeRangeList childMergeRangeList = new SVNMergeRangeList(childMergeRange);
                    
                    childMerges.put(child.myPath, childMergeRangeList);
                    if (child.myIsIndirectMergeInfo) {
                        SVNPropertiesManager.recordWCMergeInfo(child.myPath, child.myPreMergeMergeInfo, 
                                myWCAccess);
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
    
    protected void elideChildren(LinkedList childrenWithMergeInfo, File dstPath, SVNEntry entry) throws SVNException {
        if (childrenWithMergeInfo != null && !childrenWithMergeInfo.isEmpty()) {
            Map targetMergeInfo = SVNPropertiesManager.parseMergeInfo(dstPath, entry, false);
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
                    Map childMergeInfo = SVNPropertiesManager.parseMergeInfo(childMergePath.myPath, childEntry, 
                            false);
                    
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
        boolean isOperativeNotification = false;
        if (event.getContentsStatus() == SVNStatusType.CONFLICTED || 
                event.getContentsStatus() == SVNStatusType.MERGED ||
                event.getContentsStatus() == SVNStatusType.CHANGED ||
                event.getPropertiesStatus() == SVNStatusType.CONFLICTED ||
                event.getPropertiesStatus() == SVNStatusType.MERGED ||
                event.getPropertiesStatus() == SVNStatusType.CHANGED ||
                event.getAction() == SVNEventAction.UPDATE_ADD) {
            myOperativeNotificationsNumber++;
            isOperativeNotification = true;
        }

        if (myAreSourcesAncestral) {
            myNotificationsNumber++;
            if (!myIsSingleFileMerge && isOperativeNotification) {
                int newNearestAncestorIndex = findNearestAncestor(myChildrenWithMergeInfo, event.getFile());
                if (newNearestAncestorIndex != myCurrentAncestorIndex) {
                    MergePath child = (MergePath) myChildrenWithMergeInfo.get(newNearestAncestorIndex);
                    myCurrentAncestorIndex = newNearestAncestorIndex;
                    if (!child.myIsAbsent && !child.myRemainingRanges.isEmpty() &&
                    		!(newNearestAncestorIndex == 0 && myIsTargetHasDummyMergeRange)) {
                        SVNMergeRange ranges[] = child.myRemainingRanges.getRanges();
                    	SVNEvent mergeBeginEvent = SVNEventFactory.createSVNEvent(child.myPath, 
                        		SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, 
                        		SVNEventAction.MERGE_BEGIN, null, null, ranges[0]);
                    	super.handleEvent(mergeBeginEvent, ISVNEventHandler.UNKNOWN);
                    }
                }
            }
            
            if (event.getContentsStatus() == SVNStatusType.MERGED ||
            		event.getContentsStatus() == SVNStatusType.CHANGED ||
            		event.getPropertiesStatus() == SVNStatusType.MERGED ||
            		event.getPropertiesStatus() == SVNStatusType.CHANGED ||
            		event.getAction() == SVNEventAction.UPDATE_ADD) {
            	File mergedPath = event.getFile();
            	if (myMergedPaths == null) {
            		myMergedPaths = new LinkedList();
            	}
            	myMergedPaths.add(mergedPath);
            }
            
            if (event.getAction() == SVNEventAction.SKIP) {
                File skippedPath = event.getFile();
                if (mySkippedPaths == null) {
                    mySkippedPaths = new LinkedList();
                }
                mySkippedPaths.add(skippedPath);
            }
        } else if (!myIsSingleFileMerge && myOperativeNotificationsNumber == 1 && isOperativeNotification) {
        	SVNEvent mergeBeginEvent = SVNEventFactory.createSVNEvent(myTarget, 
            		SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, 
            		SVNEventAction.MERGE_BEGIN, null, null, null);
        	super.handleEvent(mergeBeginEvent, ISVNEventHandler.UNKNOWN);
        }
        
        super.handleEvent(event, progress);
    }

    public void checkCancelled() throws SVNCancelException {
        super.checkCancelled();
    }

    protected Object[] grokRangeInfoFromRevisions(SVNRepository repository1, SVNRepository repository2, 
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

    /**
     * @deprecated
     */
    protected void recordMergeInfoForRecordOnlyMerge(SVNURL url1, SVNMergeRange range, 
            boolean isRollBack, SVNEntry entry) throws SVNException {
        Map merges = new TreeMap();
        Map targetMergeInfo = null;
        myRepository1.setLocation(entry.getSVNURL(), true);
        boolean[] isIndirect = new boolean[1];
        isIndirect[0] = false;
        targetMergeInfo = getWCOrRepositoryMergeInfo(myWCAccess, myTarget, 
                entry, SVNMergeInfoInheritance.INHERITED, isIndirect, false, myRepository1);
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
        
        SVNMergeRangeList rangeList = new SVNMergeRangeList(range);
        merges.put(myTarget, rangeList);
        if (isIndirect[0]) {
            SVNPropertiesManager.recordWCMergeInfo(myTarget, targetMergeInfo, myWCAccess);
        }
        updateWCMergeInfo(myTarget, reposPath, entry, merges, isRollBack);
    }

    protected void recordMergeInfoForRecordOnlyMerge2(SVNURL url, SVNMergeRange range, 
            SVNEntry entry) throws SVNException {
        Map merges = new TreeMap();
        Map targetMergeInfo = null;
        myRepository1.setLocation(entry.getSVNURL(), true);
        boolean[] isIndirect = new boolean[1];
        isIndirect[0] = false;
        targetMergeInfo = getWCOrRepositoryMergeInfo(myWCAccess, myTarget, 
                entry, SVNMergeInfoInheritance.INHERITED, isIndirect, false, myRepository1);
        myRepository1.setLocation(url, true);
        String reposPath = getPathRelativeToRoot(null, url, null, myWCAccess, myRepository1);
        SVNMergeRangeList rangeList = new SVNMergeRangeList(range);
        merges.put(myTarget, rangeList);
        if (isIndirect[0]) {
            SVNPropertiesManager.recordWCMergeInfo(myTarget, targetMergeInfo, myWCAccess);
        }
        boolean isRollBack = range.getStartRevision() > range.getEndRevision();
        updateWCMergeInfo(myTarget, reposPath, entry, merges, isRollBack);
    }

    private boolean isHonorMergeInfo() {
    	return myIsMergeInfoCapable && myAreSourcesAncestral && myIsSameRepository && !myIsIgnoreAncestry;
    }

    private boolean isRecordMergeInfo() {
    	return myIsMergeInfoCapable && myAreSourcesAncestral && myIsSameRepository && !myIsDryRun;
    }
    
    private List normalizeMergeSources(SVNURL url, SVNURL sourceURL, SVNURL sourceRootURL, 
    		SVNRevision pegRevision, List rangesToMerge, SVNRepository repository) throws SVNException {
    	long youngestRevision[] = { SVNRepository.INVALID_REVISION };
    	long pegRevNum = getRevisionNumber(pegRevision, youngestRevision, repository, null);
    	if (!SVNRevision.isValidRevisionNumber(pegRevNum)) {
    		SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION);
    		SVNErrorManager.error(err);
    	}
    	
    	List mergeRanges = new ArrayList(rangesToMerge.size());
    	for (Iterator rangesIter = rangesToMerge.iterator(); rangesIter.hasNext();) {
			SVNRevisionRange revRange = (SVNRevisionRange) rangesIter.next();
			SVNRevision rangeStart = revRange.getStartRevision();
			SVNRevision rangeEnd = revRange.getEndRevision();
			
			if (!rangeStart.isValid() || !rangeEnd.isValid()) {
				SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
						"Not all required revisions are specified");
				SVNErrorManager.error(err);
			}
			
			long rangeStartRev = getRevisionNumber(rangeStart, youngestRevision, repository, null); 
			long rangeEndRev = getRevisionNumber(rangeEnd, youngestRevision, repository, null);
			if (rangeStartRev != rangeEndRev) {
				SVNMergeRange range = new SVNMergeRange(rangeStartRev, rangeEndRev, true);
				mergeRanges.add(range);
			}
    	}
    	
    	SVNMergeRangeList mergeRangesList = SVNMergeRangeList.fromCollection(mergeRanges);
    	mergeRangesList = mergeRangesList.compactMergeRanges();
    	mergeRanges = mergeRangesList.getRangesAsList();
    	if (mergeRanges.isEmpty()) {
    		return mergeRanges;
    	}
    	
    	long oldestRequestedRev = SVNRepository.INVALID_REVISION;
    	long youngestRequestedRev = SVNRepository.INVALID_REVISION;
    	for (Iterator rangesIter = mergeRanges.iterator(); rangesIter.hasNext();) {
			SVNMergeRange range = (SVNMergeRange) rangesIter.next();
			long minRev = Math.min(range.getStartRevision(), range.getEndRevision());
			long maxRev = Math.max(range.getStartRevision(), range.getEndRevision());
			
			if (!SVNRevision.isValidRevisionNumber(oldestRequestedRev) || oldestRequestedRev < minRev) {
				oldestRequestedRev = minRev;
			}
			if (!SVNRevision.isValidRevisionNumber(youngestRequestedRev) || youngestRequestedRev < maxRev) {
				youngestRequestedRev = maxRev;
			}
		}
    	
    	if (pegRevNum < youngestRequestedRev) {
            getLocations(sourceURL, null, repository, SVNRevision.create(pegRevNum), 
                    SVNRevision.create(youngestRequestedRev), SVNRevision.UNDEFINED);
            pegRevNum = youngestRequestedRev;
    	}

    	Collection segments = repository.getLocationSegments("", pegRevNum, youngestRequestedRev, 
    			oldestRequestedRev);
    	
    	List resultMergeSources = new LinkedList();
    	for (Iterator rangesIter = mergeRanges.iterator(); rangesIter.hasNext();) {
			SVNMergeRange range = (SVNMergeRange) rangesIter.next();
			SVNLocationSegment[] segmentsArray = (SVNLocationSegment[]) segments.toArray(new SVNLocationSegment[segments.size()]);
			List mergeSources = combineRangeWithSegments(range, segmentsArray, sourceRootURL);
			resultMergeSources.addAll(mergeSources);
    	}
    	return resultMergeSources;
    }
    
    private List combineRangeWithSegments(SVNMergeRange range, SVNLocationSegment[] segments, 
    		SVNURL sourceRootURL) throws SVNException {
    	long minRev = Math.min(range.getStartRevision(), range.getEndRevision()) + 1;
    	long maxRev = Math.max(range.getStartRevision(), range.getEndRevision());
    	boolean subtractive = range.getStartRevision() > range.getEndRevision();
    	List mergeSources = new LinkedList();
    	for (int i = 0; i < segments.length; i++) {
			SVNLocationSegment segment = segments[i];
			if (segment.getEndRevision() < minRev || segment.getStartRevision() > maxRev || 
					segment.getPath() == null) {
				continue;
			}
			
			String path1 = null;
			long rev1 = Math.max(segment.getStartRevision(), minRev) - 1;
			if (minRev <= segment.getStartRevision()) {
				if (i > 0) {
					path1 = segments[i - 1].getPath();
				}
				if (path1 == null && i > 1) {
					path1 = segments[i - 2].getPath();
					rev1 = segments[i - 2].getEndRevision();
				}
			} else {
				path1 = segment.getPath();
			}
			
			if (path1 == null || segment.getPath() == null) {
				continue;
			}
			
			MergeSource mergeSource = new MergeSource();
			mergeSource.myURL1 = sourceRootURL.appendPath(path1, false);
			mergeSource.myURL2 = sourceRootURL.appendPath(segment.getPath(), false);
			mergeSource.myRevision1 = rev1;
			mergeSource.myRevision2 = Math.min(segment.getEndRevision(), maxRev);
			if (subtractive) {
				long tmpRev = mergeSource.myRevision1;
				SVNURL tmpURL = mergeSource.myURL1;
				mergeSource.myRevision1 = mergeSource.myRevision2;
				mergeSource.myURL1 = mergeSource.myURL2;
				mergeSource.myRevision2 = tmpRev;
				mergeSource.myURL2 = tmpURL;
			}
			mergeSources.add(mergeSource);
    	}
    	
    	if (subtractive && !mergeSources.isEmpty()) {
    		Collections.sort(mergeSources, new Comparator() {
				public int compare(Object o1, Object o2) {
					MergeSource source1 = (MergeSource) o1;
					MergeSource source2 = (MergeSource) o2;
					long src1Rev1 = source1.myRevision1;
					long src2Rev1 = source2.myRevision1;
					if (src1Rev1 == src2Rev1) {
						return 0;
					}
					return src1Rev1 < src2Rev1 ? 1 : -1;
				}
    		});
    	}
    	return mergeSources;
    }
    
    private SVNLocationEntry getYoungestCommonAncestor(File path1, SVNURL url1, SVNRevision revision1, 
    		File path2, SVNURL url2, SVNRevision revision2) throws SVNException {
    	Map history1 = getHistoryAsMergeInfo(url1, path1, revision1, SVNRepository.INVALID_REVISION, 
    			SVNRepository.INVALID_REVISION, null, null);
    	Map history2 = getHistoryAsMergeInfo(url2, path2, revision2, SVNRepository.INVALID_REVISION, 
    			SVNRepository.INVALID_REVISION, null, null);
    	
    	long youngestCommonRevision = SVNRepository.INVALID_REVISION;
    	String youngestCommonPath = null;
    	for (Iterator historyIter = history1.entrySet().iterator(); historyIter.hasNext();) {
    		Map.Entry historyEntry = (Map.Entry) historyIter.next();
    		String path = (String) historyEntry.getKey();
    		SVNMergeRangeList ranges1 = (SVNMergeRangeList) historyEntry.getValue();
    		SVNMergeRangeList ranges2 = (SVNMergeRangeList) history2.get(path);
    		if (ranges2 != null) {
    			SVNMergeRangeList commonList = ranges2.intersect(ranges1);
    			if (!commonList.isEmpty()) {
    				SVNMergeRange commonRanges[] = commonList.getRanges();
    				SVNMergeRange youngestCommonRange = commonRanges[commonRanges.length - 1];
    				if (!SVNRevision.isValidRevisionNumber(youngestCommonRevision) || 
    						youngestCommonRange.getEndRevision() > youngestCommonRevision) {
    					youngestCommonRevision = youngestCommonRange.getEndRevision();
    					youngestCommonPath = path;
    				}
    			}
    		}
    	}
    	return new SVNLocationEntry(youngestCommonRevision, youngestCommonPath);
    }
    
    private Map[] getFullMergeInfo(SVNEntry entry, boolean[] indirect, SVNMergeInfoInheritance inherit,
            SVNRepository repos, File target, long start, long end) throws SVNException {
        Map[] result = new Map[2];
        if (!SVNRevision.isValidRevisionNumber(start) || !SVNRevision.isValidRevisionNumber(end) || 
                start <= end) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                    "ASSERTION FAILED in SVNMergeDriver.getFullMergeInfo()");
            SVNErrorManager.error(err);
        }
        
        //get recorded merge info
        result[0] = getWCOrRepositoryMergeInfo(myWCAccess, target, entry, inherit, indirect, false, repos);
        long[] targetRev = new long[1];
        targetRev[0] = SVNRepository.INVALID_REVISION;
        SVNURL url = deriveLocation(target, null, targetRev, SVNRevision.WORKING, repos, myWCAccess);
        if (targetRev[0] <= end) {
            result[1] = new TreeMap();//implicit merge info
            return result;
        }
        
        boolean closeSession = false;
        boolean reparent = false;
        SVNURL sessionURL = null;
        try {
            if (repos != null) {
                sessionURL = repos.getLocation();
                if (!sessionURL.equals(url)) {
                    repos.setLocation(url, false);
                    reparent = true;
                }
            } else {
                repos = createRepository(url, false);
                closeSession = true;
            }
            
            if (targetRev[0] < start) {
                getLocations(url, null, repos, SVNRevision.create(targetRev[0]), 
                        SVNRevision.create(start), SVNRevision.UNDEFINED);
                targetRev[0] = start;
            }
            result[1] = getHistoryAsMergeInfo(url, null, SVNRevision.create(targetRev[0]), start, end, 
            		repos, null);
            if (reparent) {
                repos.setLocation(sessionURL, false);
            }
        } finally {
            if (closeSession) {
                repos.closeSession();
            }
        }
        return result;
    }
    
    private int findNearestAncestor(Collection childrenWithMergeInfo, File path) {
        if (childrenWithMergeInfo == null) {
            return 0;
        }

        int ancestorIndex = 0;
        int i = 0;
        for (Iterator childrenIter = childrenWithMergeInfo.iterator(); childrenIter.hasNext();) {
            MergePath child = (MergePath) childrenIter.next();
            String childPath = child.myPath.getAbsolutePath();
            String pathStr = path.getAbsolutePath();
            if (SVNPathUtil.isAncestor(childPath, pathStr)) {
                ancestorIndex = i;
            }
            i++;
        }
        return ancestorIndex;
    }
    
    private Map getHistoryAsMergeInfo(SVNURL url, File path, SVNRevision pegRevision, long rangeYoungest, 
            long rangeOldest, SVNRepository repos, SVNWCAccess access) throws SVNException {
        Map mergeInfo = new TreeMap();
        long[] pegRevNum = new long[1];
        pegRevNum[0] = SVNRepository.INVALID_REVISION;
        url = deriveLocation(path, url, pegRevNum, pegRevision, repos, access);
        
        boolean closeSession = false;
        try {
            if (repos == null) {
                repos = createRepository(url, false);
                closeSession = true;
            }
            if (!SVNRevision.isValidRevisionNumber(rangeYoungest)) {
                rangeYoungest = pegRevNum[0];
            }
            if (!SVNRevision.isValidRevisionNumber(rangeOldest)) {
                rangeOldest = 0;
            }
            
            Collection segments = repos.getLocationSegments("", pegRevNum[0], rangeYoungest, rangeOldest);
            for (Iterator segmentsIter = segments.iterator(); segmentsIter.hasNext();) {
                SVNLocationSegment segment = (SVNLocationSegment) segmentsIter.next();
                if (segment.getPath() == null) {
                    continue;
                }
                String sourcePath = segment.getPath();
                Collection pathRanges = (Collection) mergeInfo.get(sourcePath);
                if (pathRanges == null) {
                    pathRanges = new LinkedList();
                    mergeInfo.put(sourcePath, pathRanges);
                }
                SVNMergeRange range = new SVNMergeRange(Math.max(segment.getStartRevision() - 1, 0), 
                        segment.getEndRevision(), true);
                pathRanges.add(range);
            }
            for (Iterator mergeInfoIter = mergeInfo.entrySet().iterator(); mergeInfoIter.hasNext();) {
                Map.Entry mergeInfoEntry = (Map.Entry) mergeInfoIter.next();
                Collection pathRanges = (Collection) mergeInfoEntry.getValue();
                mergeInfoEntry.setValue(SVNMergeRangeList.fromCollection(pathRanges));
            }
            return mergeInfo;
        } finally {
            if (closeSession) {
                repos.closeSession();
            }
        }
    }
    
    private SVNRevision[] getAssumedDefaultRevisionRange(SVNRevision revision1, SVNRevision revision2, 
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

    private static SVNProperties computePropsDiff(SVNProperties props1, SVNProperties props2) {
        SVNProperties propsDiff = new SVNProperties();
        for (Iterator names = props2.nameSet().iterator(); names.hasNext();) {
            String newPropName = (String) names.next();
            if (props1.containsName(newPropName)) {
                // changed.
                SVNPropertyValue oldValue = props2.getSVNPropertyValue(newPropName);
                if (!oldValue.equals(props1.getSVNPropertyValue(newPropName))) {
                    propsDiff.put(newPropName, props2.getSVNPropertyValue(newPropName));
                }
            } else {
                // added.
                propsDiff.put(newPropName, props2.getSVNPropertyValue(newPropName));
            }
        }
        for (Iterator names = props1.nameSet().iterator(); names.hasNext();) {
            String oldPropName = (String) names.next();
            if (!props2.containsName(oldPropName)) {
                // deleted
                propsDiff.put(oldPropName, (SVNPropertyValue) null);
            }
        }
        return propsDiff;
    }

    private static SVNProperties filterProperties(SVNProperties props1, boolean leftRegular,
            boolean leftEntry, boolean leftWC) {
        SVNProperties result = new SVNProperties();
        for (Iterator names = props1.nameSet().iterator(); names.hasNext();) {
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
            result.copyValue(props1, propName);
        }
        return result;
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
                SVNMergeRangeList inheritableRangeList = new SVNMergeRangeList(range);
                Map inheritableMerges = new TreeMap();
                inheritableMerges.put(reposPath, inheritableRangeList);
                Map merges = SVNMergeInfoManager.getInheritableMergeInfo(targetMergeInfo, 
                                                                         reposPath, 
                                                                         range.getStartRevision(), 
                                                                         range.getEndRevision());
                if (!SVNMergeInfoManager.mergeInfoEquals(merges, targetMergeInfo, false)) {
                    merges = SVNMergeInfoManager.mergeMergeInfos(merges, inheritableMerges);
                
                    SVNPropertiesManager.recordWCMergeInfo(target, merges, myWCAccess);
                }
            }
        }
    }
    
    private void recordMergeInfoOnMergedChildren(SVNDepth depth) throws SVNException {
        if (depth != SVNDepth.INFINITY && myMergedPaths != null) {
            boolean[] isIndirectChildMergeInfo = new boolean[1];
            isIndirectChildMergeInfo[0] = false;
            Map childMergeInfo = new TreeMap();
            for (Iterator paths = myMergedPaths.iterator(); paths.hasNext();) {
                File mergedPath = (File) paths.next();
                SVNEntry childEntry = myWCAccess.getVersionedEntry(mergedPath, false);
                if ((childEntry.isDirectory() && myTarget.equals(mergedPath) && depth == SVNDepth.IMMEDIATES) ||
                        (childEntry.isFile() && depth == SVNDepth.FILES)) {
                    childMergeInfo = getWCOrRepositoryMergeInfo(myWCAccess, mergedPath, 
                            childEntry, SVNMergeInfoInheritance.INHERITED, isIndirectChildMergeInfo, false, 
                            myRepository1);
                    if (isIndirectChildMergeInfo[0]) {
                        SVNPropertiesManager.recordWCMergeInfo(mergedPath, childMergeInfo, myWCAccess);
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
                childRelativePath = SVNPathUtil.getRelativePath(myTarget.getAbsolutePath(), childMergePath.myPath.getAbsolutePath());
            }
            String childMergeSrcPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(parentMergeSrcPath, childRelativePath));
            SVNEntry childEntry = myWCAccess.getVersionedEntry(childMergePath.myPath, false);
            boolean[] indirect = new boolean[1];
            indirect[0] = false;
            childMergePath.myPreMergeMergeInfo = getWCOrRepositoryMergeInfo(myWCAccess, 
                    childMergePath.myPath, childEntry, SVNMergeInfoInheritance.INHERITED, indirect, false, null);
            childMergePath.myIsIndirectMergeInfo = indirect[0];
            childMergePath.myRemainingRanges = calculateRemainingRanges(childEntry.getSVNURL(), 
                    childMergeSrcPath, childEntry, range, childMergePath.myPreMergeMergeInfo, 
                    repository, isRollBack);
            
            if (myTarget.equals(childMergePath.myPath) && (childMergePath.myRemainingRanges == null || 
                    childMergePath.myRemainingRanges.isEmpty())) {
                SVNMergeRange dummyRange = new SVNMergeRange(range.getEndRevision(), range.getEndRevision(), 
                        range.isInheritable());
                childMergePath.myRemainingRanges = new SVNMergeRangeList(dummyRange);
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
                    mergeInfoProp = props.getStringPropertyValue(SVNProperty.MERGE_INFO);
                    if (mergeInfoProp != null) {
                        String relToTargetPath = SVNPathUtil.getRelativePath(target.getAbsolutePath(), path.getAbsolutePath());
                        String mergeSrcChildPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(mergeSrcPath,
                                                                           relToTargetPath));
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
                            Map mergeInfo = null; 
                            mergeInfo = getWCMergeInfo(childPath, entry, myTarget, 
                                           SVNMergeInfoInheritance.NEAREST_ANCESTOR, 
                                           false, new boolean[1]);
                            
                            SVNPropertiesManager.recordWCMergeInfo(childPath, mergeInfo, myWCAccess);
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
    
    private void notifySingleFileMerge(File targetWCPath, SVNEventAction action, 
            SVNStatusType cstate, SVNStatusType pstate) throws SVNException {
        action = cstate == SVNStatusType.MISSING ? SVNEventAction.SKIP : action;
        SVNEvent event = SVNEventFactory.createSVNEvent(targetWCPath, SVNNodeKind.FILE, null, 
                SVNRepository.INVALID_REVISION, cstate, pstate, SVNStatusType.LOCK_INAPPLICABLE, action, 
                null, null, null);
        this.handleEvent(event, ISVNEventHandler.UNKNOWN);
    }

    private void notifyFileMerge(SVNAdminArea adminArea, String name, SVNEventAction action, 
            SVNStatusType cstate, SVNStatusType pstate, String mimeType) throws SVNException {
        action = cstate == SVNStatusType.MISSING ? SVNEventAction.SKIP : action;
        SVNEvent event = SVNEventFactory.createSVNEvent(adminArea.getFile(name), SVNNodeKind.FILE, mimeType, 
        		SVNRepository.INVALID_REVISION, cstate, pstate, SVNStatusType.LOCK_INAPPLICABLE, action, null, 
        		null, null);
        this.handleEvent(event, ISVNEventHandler.UNKNOWN);
    }

    private Map determinePerformedMerges(File targetPath, SVNMergeRange range, SVNDepth depth) throws SVNException {
        int numberOfSkippedPaths = mySkippedPaths != null ? mySkippedPaths.size() : 0;
        Map merges = new TreeMap();
        
        if (myOperativeNotificationsNumber > 0) {
            myIsOperativeMerge = true;
        } else {
        	return merges;
        }
        
        SVNMergeRangeList rangeList = new SVNMergeRangeList(range);
        merges.put(targetPath, rangeList);
            
        if (numberOfSkippedPaths > 0) {
            for (Iterator skippedPaths = mySkippedPaths.iterator(); skippedPaths.hasNext();) {
                File skippedPath = (File) skippedPaths.next();
                merges.put(skippedPath, new SVNMergeRangeList(new SVNMergeRange[0]));
                //TODO: numberOfSkippedPaths < myOperativeNotificationsNumber
            }
        }
        
        if (depth != SVNDepth.INFINITY && myMergedPaths != null) {
            for (Iterator mergedPathsIter = myMergedPaths.iterator(); mergedPathsIter.hasNext();) {
                File mergedPath = (File) mergedPathsIter.next();
                SVNMergeRangeList childRangeList = null;
                SVNMergeRange childMergeRange = range.dup();
                SVNEntry childEntry = myWCAccess.getVersionedEntry(mergedPath, false);
                if ((childEntry.isDirectory() && mergedPath.equals(myTarget) && 
                        depth == SVNDepth.IMMEDIATES) || (childEntry.isFile() && 
                                depth == SVNDepth.FILES)) {
                    childMergeRange.setInheritable(true);
                    childRangeList = new SVNMergeRangeList(childMergeRange);
                    merges.put(mergedPath, childRangeList);
                }
            }
        }
        return merges;
    }
    
    private void updateWCMergeInfo(File targetPath, String parentReposPath, 
                                   SVNEntry entry, Map merges, boolean isRollBack) throws SVNException {
        
        for (Iterator mergesEntries = merges.entrySet().iterator(); mergesEntries.hasNext();) {
            Map.Entry pathToRangeList = (Map.Entry) mergesEntries.next();
            File path = (File) pathToRangeList.getKey();
            SVNMergeRangeList ranges = (SVNMergeRangeList) pathToRangeList.getValue();
            
            Map mergeInfo = SVNPropertiesManager.parseMergeInfo(path, entry, false);
            if (mergeInfo == null && ranges.isEmpty()) {
                mergeInfo = getWCMergeInfo(path, entry, null, 
                        SVNMergeInfoInheritance.NEAREST_ANCESTOR, true, new boolean[1]);
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
                reposPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(parentReposPath, childRelPath));
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
                rangeList = rangeList.diff(ranges, false);
            } else {
                rangeList = rangeList.merge(ranges);
            }
            
            mergeInfo.put(reposPath, rangeList);
            //TODO: I do not understand this:) how mergeInfo can be ever empty here????
            if (isRollBack && mergeInfo.isEmpty()) {
                mergeInfo = null;
            }
            
            SVNMergeInfoUtil.removeEmptyRangeLists(mergeInfo);
            
            try {
                SVNPropertiesManager.recordWCMergeInfo(path, mergeInfo, myWCAccess);
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.ENTRY_NOT_FOUND) {
                    throw svne;
                }
            }
        }
    }
    
    private SVNMergeRangeList calculateRemainingRanges(SVNURL sourceRootURL, SVNURL url1, long revision1, 
            SVNURL url2, long revision2, boolean inheritable, Map targetMergeInfo, Map implicitMergeInfo,  
            SVNEntry entry, SVNRepository repository) throws SVNException {
        SVNURL primaryURL = revision1 < revision2 ? url2 : url1;
        SVNURL oldURL = repository.getLocation();
        repository.setLocation(sourceRootURL, false);
        
        SVNMergeRangeList requestedRangeList = filterReflectedRevisions(sourceRootURL, url1, revision1, url2, 
                revision2, inheritable, entry.getSVNURL(), repository);
        repository.setLocation(oldURL, false);
        
        String mergeInfoPath = getPathRelativeToRoot(null, primaryURL, sourceRootURL, null, repository);
        
        SVNMergeRangeList remainingRanges = filterMergedRevisions(mergeInfoPath, requestedRangeList, 
                targetMergeInfo, implicitMergeInfo, entry, revision1 > revision2); 
        return remainingRanges;
    }

    private SVNMergeRangeList filterMergedRevisions(String mergeInfoPath, SVNMergeRangeList requestedRangeList,
            Map targetMergeInfo, Map implicitMergeInfo, SVNEntry entry, boolean isRollBack) throws SVNException {
        Map mergeInfo = null;
        SVNMergeRangeList targetRangeList = null;
        SVNMergeRangeList remainingRanges = null;
        
        if (isRollBack) {
            mergeInfo = SVNMergeInfoManager.dupMergeInfo(implicitMergeInfo, null);
            if (targetMergeInfo != null) {
                mergeInfo = SVNMergeInfoManager.mergeMergeInfos(mergeInfo, targetMergeInfo);
            }
            
            targetRangeList = (SVNMergeRangeList) mergeInfo.get(mergeInfoPath);
            if (targetRangeList != null) {
                requestedRangeList = requestedRangeList.dup();
                requestedRangeList = requestedRangeList.reverse();
                remainingRanges = requestedRangeList.intersect(targetRangeList);
                remainingRanges = remainingRanges.reverse();
            } else {
                remainingRanges = new SVNMergeRangeList(new SVNMergeRange[0]);
            }
        } else {
            remainingRanges = requestedRangeList;
            if (getOptions().isAllowAllForwardMergesFromSelf()) {
                if (targetMergeInfo != null) {
                    targetRangeList = (SVNMergeRangeList) targetMergeInfo.get(mergeInfoPath);
                }
            } else {
                mergeInfo = SVNMergeInfoManager.dupMergeInfo(implicitMergeInfo, null);
                if (targetMergeInfo != null) {
                    mergeInfo = SVNMergeInfoManager.mergeMergeInfos(mergeInfo, targetMergeInfo);
                }
                targetRangeList = (SVNMergeRangeList) mergeInfo.get(mergeInfoPath);
            }
            if (targetRangeList != null) {
                remainingRanges = requestedRangeList.diff(targetRangeList, false);
            }
        }
        return remainingRanges;
    }

    private SVNMergeRangeList filterReflectedRevisions(SVNURL sourceRootURL, SVNURL url1, long revision1, 
            SVNURL url2, long revision2, boolean inheritable, SVNURL targetURL, SVNRepository repository) throws SVNException {
        long minRev = Math.min(revision1, revision2);
        long maxRev = Math.max(revision1, revision2);
        SVNURL minURL = revision1 < revision2 ? url1 : url2;
        SVNURL maxURL = revision1 < revision2 ? url2 : url1;
        String minRelPath = getPathRelativeToRoot(null, minURL, sourceRootURL, null, repository);
        String maxRelPath = getPathRelativeToRoot(null, maxURL, sourceRootURL, null, repository);
       
        Map startMergeInfoMap = repository.getMergeInfo(new String[] { minRelPath }, minRev, 
                SVNMergeInfoInheritance.INHERITED);
        SVNMergeInfo startMergeInfo = startMergeInfoMap != null ? 
                                      (SVNMergeInfo) startMergeInfoMap.get(minRelPath) : null;
        
        Map endMergeInfoMap = repository.getMergeInfo(new String[] { maxRelPath }, maxRev, 
                SVNMergeInfoInheritance.INHERITED);
        SVNMergeInfo endMergeInfo = endMergeInfoMap != null ? (SVNMergeInfo) endMergeInfoMap.get(maxRelPath) : null;
        Map added = new HashMap();
        SVNMergeInfoManager.diffMergeInfo(null, added, startMergeInfo != null ? 
                startMergeInfo.getMergeSourcesToMergeLists() : null, 
                endMergeInfo != null ? endMergeInfo.getMergeSourcesToMergeLists() : null, false);

        SVNMergeRangeList srcRangeListForTgt = null;
        if (!added.isEmpty()) {
            String mergeInfoPath = getPathRelativeToRoot(null, targetURL, sourceRootURL, null, repository);
            srcRangeListForTgt = (SVNMergeRangeList) added.get(mergeInfoPath);
        }
        
        SVNMergeRange range = new SVNMergeRange(revision1, revision2, inheritable);
        SVNMergeRangeList requestedRangeList = new SVNMergeRangeList(range);
        if (srcRangeListForTgt != null) {
            requestedRangeList = requestedRangeList.diff(srcRangeListForTgt, false);
        }
        return requestedRangeList;
    }

    /**
     * @deprecated
     */
    private SVNMergeRangeList calculateRemainingRanges(SVNURL url, String reposPath, SVNEntry entry, 
            SVNMergeRange range, Map targetMergeInfo, SVNRepository repository, boolean isRollBack) throws SVNException {
        SVNMergeRangeList requestedRangeList = calculateRequestedRanges(range, url, entry, repository);
        SVNMergeRangeList remainingRangeList = calculateMergeRanges(reposPath, requestedRangeList, 
                targetMergeInfo, isRollBack);
        return remainingRangeList;
    }
    /**
     * @deprecated
     */
    private SVNMergeRangeList calculateRequestedRanges(SVNMergeRange unrefinedRange, SVNURL srcURL, SVNEntry entry,
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
                                          false);

        SVNMergeRangeList srcRangeListForTgt = null;
        if (!added.isEmpty()) {
            String tgtReposPath = entry.getSVNURL().getPath().substring(reposRoot.getPath().length());
            if (!tgtReposPath.startsWith("/")) {
                tgtReposPath = "/" + tgtReposPath;
            }
            srcRangeListForTgt = (SVNMergeRangeList) added.get(tgtReposPath);
        }
        
        SVNMergeRangeList requestedRangeList = new SVNMergeRangeList(unrefinedRange);
        if (srcRangeListForTgt != null) {
            requestedRangeList = requestedRangeList.diff(srcRangeListForTgt, false);
        }
        return requestedRangeList;
    }

    /**
     * @deprecated
     */
    private SVNMergeRangeList calculateMergeRanges(String reposPath, SVNMergeRangeList requestedRangeList,
            Map targetMergeInfo, boolean isRollBack) {
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
                remainingRangeList = requestedRangeList.diff(targetRangeList, false);
                
            }
        }
        return remainingRangeList;
    }
    
    private File loadFile(SVNRepository repository, long revision, 
                          SVNProperties properties, SVNAdminArea adminArea) throws SVNException {
        File tmpDir = adminArea.getAdminTempDirectory();
        File result = SVNFileUtil.createUniqueFile(tmpDir, ".merge", ".tmp");
        SVNFileUtil.createEmptyFile(result);
        
        OutputStream os = null;
        try {
            os = SVNFileUtil.openFileForWriting(result); 
            repository.getFile("", revision, properties, 
                               new SVNCancellableOutputStream(os, this));
        } finally {
            SVNFileUtil.closeFile(os);
        }
        return result;
    }

    protected static class MergeSource {
        private SVNURL myURL1;
        private long myRevision1;
        private SVNURL myURL2;
        private long myRevision2;
    }
    
    protected static class MergeAction {
        public static final MergeAction MERGE = new MergeAction();
        public static final MergeAction ROLL_BACK = new MergeAction();
        public static final MergeAction NO_OP = new MergeAction();
        
        private MergeAction() {
        }
    }
    
    protected static class MergePath implements Comparable {
        File myPath;
        boolean myHasMissingChildren;
        boolean myIsSwitched;
        boolean myHasNonInheritableMergeInfo;
        boolean myIsAbsent;
        boolean myIsIndirectMergeInfo;
        boolean myIsScheduledForDeletion;
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
