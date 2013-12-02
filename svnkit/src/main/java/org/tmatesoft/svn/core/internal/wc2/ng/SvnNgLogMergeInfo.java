package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.*;

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnLog;
import org.tmatesoft.svn.core.wc2.SvnLogMergeInfo;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgLogMergeInfo extends SvnNgOperationRunner<SVNLogEntry, SvnLogMergeInfo> {

    @Override
    public boolean isApplicable(SvnLogMergeInfo operation, SvnWcGeneration wcGeneration) throws SVNException {
        boolean targetOk = operation.getFirstTarget().isURL() 
                || SvnOperationFactory.detectWcGeneration(operation.getFirstTarget().getFile(), true) == SvnWcGeneration.V17;
        boolean sourceOk = operation.getSource().isURL() 
                || SvnOperationFactory.detectWcGeneration(operation.getSource().getFile(), true) == SvnWcGeneration.V17;
        return targetOk && sourceOk;
    }

    @Override
    public SvnWcGeneration getWcGeneration() {
        return SvnWcGeneration.NOT_DETECTED;
    }

    @Override
    protected SVNLogEntry run(SVNWCContext context) throws SVNException {
        SVNURL[] root = new SVNURL[1];

        if (getOperation().getDepth() != SVNDepth.EMPTY && getOperation().getDepth() != SVNDepth.INFINITY) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Only depths 'infinity' and 'empty' are currently supported");
            SVNErrorManager.error(errorMessage, SVNLogType.CLIENT);
        }

        Collection<SvnRevisionRange> ranges = getOperation().getRanges();
        SVNRevision sourceStartRevision = (ranges == null || ranges.size() == 0) ? SVNRevision.UNDEFINED : ranges.iterator().next().getStart();
        SVNRevision sourceEndRevision = (ranges == null || ranges.size() == 0) ? SVNRevision.UNDEFINED : ranges.iterator().next().getEnd();

        if (sourceStartRevision.isLocal()) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION);
            SVNErrorManager.error(errorMessage, SVNLogType.CLIENT);
        }
        if (sourceEndRevision.isLocal()) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION);
            SVNErrorManager.error(errorMessage, SVNLogType.CLIENT);
        }
        if (sourceEndRevision != SVNRevision.UNDEFINED && sourceStartRevision == SVNRevision.UNDEFINED) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION);
            SVNErrorManager.error(errorMessage, SVNLogType.CLIENT);
        }
        if (sourceEndRevision == SVNRevision.UNDEFINED && sourceStartRevision != SVNRevision.UNDEFINED) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION);
            SVNErrorManager.error(errorMessage, SVNLogType.CLIENT);
        }


        Map<String, Map<String, SVNMergeRangeList>> targetMergeInfoCatalog = null;
        Map<String, Map<String, SVNMergeRangeList>> mergeInfoCatalog = null;

        SVNRepository sourceRepository = null;
        SVNRepository targetRepository = null;
        if (targetMergeInfoCatalog != null) {
            if (targetMergeInfoCatalog.size() == 0) {
                Structure<SvnRepositoryAccess.RepositoryInfo> repositoryInfo = getRepositoryAccess().createRepositoryFor(getOperation().getFirstTarget(), getOperation().getFirstTarget().getPegRevision(), getOperation().getFirstTarget().getPegRevision(), null);
                targetRepository = repositoryInfo.get(SvnRepositoryAccess.RepositoryInfo.repository);
                root[0] = targetRepository.getRepositoryRoot(true);
            } else {
                mergeInfoCatalog = SvnNgMergeinfoUtil.getMergeInfo(getWcContext(), getRepositoryAccess(), getOperation().getFirstTarget(), getOperation().getDepth() == SVNDepth.INFINITY, true, root);
                targetMergeInfoCatalog = mergeInfoCatalog;
            }
        } else {
            mergeInfoCatalog = SvnNgMergeinfoUtil.getMergeInfo(getWcContext(), getRepositoryAccess(), getOperation().getFirstTarget(), getOperation().getDepth() == SVNDepth.INFINITY, true, root);
        }

        File reposRelPath = null;
        SvnTarget target = getOperation().getFirstTarget();
        if (!target.isURL()) {
            reposRelPath = getWcContext().getNodeReposRelPath(getOperation().getFirstTarget().getFile());
        } else {
            reposRelPath = SVNFileUtil.createFilePath(SVNPathUtil.getRelativePath(root[0].getPath(), target.getURL().getPath()));
        }

        if (mergeInfoCatalog == null) {
            if (getOperation().isFindMerged()) {
                return getOperation().first();
            }
            mergeInfoCatalog = new TreeMap<String, Map<String,SVNMergeRangeList>>();
            mergeInfoCatalog.put(SVNFileUtil.getFilePath(reposRelPath), new TreeMap<String, SVNMergeRangeList>());
        }
        Map<String, SVNMergeRangeList> history = null;
        Map<String, SVNMergeRangeList> sourceHistory = null;
        if (!getOperation().isFindMerged()) {
            history = getRepositoryAccess().getHistoryAsMergeInfo(targetRepository, target, -1, -1);
        }
        Structure<SvnRepositoryAccess.RepositoryInfo> repositoryInfo = getRepositoryAccess().createRepositoryFor(getOperation().getSource(), getOperation().getSource().getPegRevision(), getOperation().getSource().getPegRevision(), null);
        sourceRepository = repositoryInfo.get(SvnRepositoryAccess.RepositoryInfo.repository);
        long pathRevision = repositoryInfo.lng(SvnRepositoryAccess.RepositoryInfo.revision);
        Structure<SvnRepositoryAccess.RevisionsPair> startRevisionPair = getRepositoryAccess().getRevisionNumber(sourceRepository, getOperation().getSource(), sourceStartRevision, null);
        long startRevision = startRevisionPair.lng(SvnRepositoryAccess.RevisionsPair.revNumber);
        Structure<SvnRepositoryAccess.RevisionsPair> endRevisionPair = getRepositoryAccess().getRevisionNumber(sourceRepository, getOperation().getSource(), sourceEndRevision, null);
        long endRevision = endRevisionPair.lng(SvnRepositoryAccess.RevisionsPair.revNumber);
        sourceHistory = getRepositoryAccess().getHistoryAsMergeInfo(null, getOperation().getSource(), Math.max(endRevision, startRevision), Math.min(endRevision, startRevision));

        boolean oldestRevsFirst = startRevision <= endRevision;

        String reposRelPathStr = SVNFileUtil.getFilePath(reposRelPath);
        SVNMergeRangeList masterNonInheritableRangeList = new SVNMergeRangeList((SVNMergeRange[]) null);
        SVNMergeRangeList masterInheritableRangeList = new SVNMergeRangeList((SVNMergeRange[]) null);
        Map<String, SVNMergeRangeList> inheritableSubtreeMerges = new TreeMap<String, SVNMergeRangeList>();
        
        for (String subtreePath : mergeInfoCatalog.keySet()) {
            Map<String, SVNMergeRangeList> subtreeMergeInfo = mergeInfoCatalog.get(subtreePath);
            
            Map<String, SVNMergeRangeList> subtreeHistory = null;
            Map<String, SVNMergeRangeList> subtreeSourceHistory;
            Map<String, SVNMergeRangeList> subtreeInheritableMergeInfo;
            Map<String, SVNMergeRangeList> subtreeNonInheritableMergeInfo;
            Map<String, SVNMergeRangeList> mergedNonInheritableMergeInfo;
            Map<String, SVNMergeRangeList> mergedMergeInfo;

            boolean isSubtree = !subtreePath.equals(reposRelPathStr);
            
            if (isSubtree) {
                String subtreeRelPath = subtreePath.substring(reposRelPathStr.length() + 1);
                subtreeSourceHistory = SVNMergeInfoUtil.appendSuffix(sourceHistory, subtreeRelPath);
                if (!getOperation().isFindMerged()) {
                    subtreeHistory = SVNMergeInfoUtil.appendSuffix(history, subtreeRelPath);
                }
            } else {
                subtreeSourceHistory = sourceHistory;
                if (!getOperation().isFindMerged()) {
                    subtreeHistory = history;
                }
            }
            if (!getOperation().isFindMerged()) {
                Map<String, SVNMergeRangeList> mergedViaHistory = SVNMergeInfoUtil.intersectMergeInfo(subtreeHistory, subtreeSourceHistory, true);
                subtreeMergeInfo = SVNMergeInfoUtil.mergeMergeInfos(subtreeMergeInfo, mergedViaHistory);
            }
            subtreeInheritableMergeInfo = SVNMergeInfoUtil.getInheritableMergeInfo(subtreeMergeInfo, null, -1, -1, true);
            subtreeNonInheritableMergeInfo = SVNMergeInfoUtil.getInheritableMergeInfo(subtreeMergeInfo, null, -1, -1, false);
            mergedNonInheritableMergeInfo = SVNMergeInfoUtil.intersectMergeInfo(subtreeNonInheritableMergeInfo, subtreeSourceHistory, false);
            
            if (!mergedNonInheritableMergeInfo.isEmpty()) {
                for (SVNMergeRangeList rl : mergedNonInheritableMergeInfo.values()) {
                    rl.setInheritable(false);
                    masterNonInheritableRangeList = masterNonInheritableRangeList.merge(rl.dup());
                }
            }
            mergedMergeInfo = SVNMergeInfoUtil.intersectMergeInfo(subtreeInheritableMergeInfo, subtreeSourceHistory, false);

            SVNMergeRangeList subtreeMergeRangeList = new SVNMergeRangeList((SVNMergeRange[]) null);
            if (!mergedMergeInfo.isEmpty()) {
                for (SVNMergeRangeList rl : mergedMergeInfo.values()) {
                    masterInheritableRangeList = masterInheritableRangeList.merge(rl.dup());
                    subtreeMergeRangeList = subtreeMergeRangeList.merge(rl.dup());
                }
            }
            inheritableSubtreeMerges.put(subtreePath, subtreeMergeRangeList);
        }
        
        if (!masterInheritableRangeList.isEmpty()) {
            for (String path : inheritableSubtreeMerges.keySet()) {
                SVNMergeRangeList subtreeMergedRangeList = inheritableSubtreeMerges.get(path);
                // present in master, but not in subtree.
                SVNMergeRangeList deletedRanges = masterInheritableRangeList.diff(subtreeMergedRangeList, true);
                if (!deletedRanges.isEmpty()) {
                    deletedRanges.setInheritable(false);
                    masterNonInheritableRangeList = masterNonInheritableRangeList.merge(deletedRanges);
                    masterInheritableRangeList = masterInheritableRangeList.remove(deletedRanges, false);
                }
            }
        }

        if (getOperation().isFindMerged()) {
            masterInheritableRangeList = masterInheritableRangeList.merge(masterNonInheritableRangeList);
        } else {
            SVNMergeRangeList sourceMasterRangeList = new SVNMergeRangeList((SVNMergeRange[]) null);
            for(SVNMergeRangeList rl : sourceHistory.values()) {
                sourceMasterRangeList = sourceMasterRangeList.merge(rl);
            }
            sourceMasterRangeList = sourceMasterRangeList.remove(masterNonInheritableRangeList, false);
            sourceMasterRangeList = sourceMasterRangeList.merge(masterNonInheritableRangeList);
            masterInheritableRangeList = sourceMasterRangeList.remove(masterInheritableRangeList, true);
        }
        
        if (masterInheritableRangeList.isEmpty()) {
            return getOperation().first();
        }
        
        List<String> mergeSourcePaths = new ArrayList<String>();
        String logTarget = null;
        SVNMergeRange youngestRange = masterInheritableRangeList.getRanges()[masterInheritableRangeList.getSize() - 1].dup();
        SVNMergeRangeList youngestRangeList = new SVNMergeRangeList(youngestRange.getEndRevision() - 1, youngestRange.getEndRevision(), youngestRange.isInheritable());
        for (String key : sourceHistory.keySet()) {
            SVNMergeRangeList subtreeMergedList = sourceHistory.get(key);
            SVNMergeRangeList intersection = youngestRangeList.intersect(subtreeMergedList, false);
            mergeSourcePaths.add(key);
            if (!intersection.isEmpty()) {
                logTarget = key;
            }
        }
        if (logTarget != null && logTarget.startsWith("/")) {
            logTarget = logTarget.substring(1);
        }
        
        SVNURL logTargetURL = logTarget != null ? SVNWCUtils.join(root[0], SVNFileUtil.createFilePath(logTarget)) : root[0];
        
        logForMergeInfoRangeList(logTargetURL, 
                mergeSourcePaths, 
                getOperation().isFindMerged(), 
                masterInheritableRangeList,
                oldestRevsFirst,
                mergeInfoCatalog, 
                "/" + reposRelPathStr, 
                getOperation().isDiscoverChangedPaths(), 
                getOperation().getRevisionProperties(), 
                getOperation());
        
        return getOperation().first();
    }
    
    @SuppressWarnings("unchecked")
    private void logForMergeInfoRangeList(SVNURL sourceURL, List<String> mergeSourcePaths, boolean filteringMerged, 
            SVNMergeRangeList rangelist, boolean oldestRevsFirst, Map<String, Map<String, SVNMergeRangeList>> targetCatalog, String absReposTargetPath, boolean discoverChangedPaths,
            String[] revprops, ISvnObjectReceiver<SVNLogEntry> receiver) throws SVNException {
        if (rangelist.isEmpty()) {
            return;
        }
        if (targetCatalog == null) {
            targetCatalog = new TreeMap<String, Map<String,SVNMergeRangeList>>();
        }
        Map<String, Map<String, SVNMergeRangeList>> adjustedCatalog = new TreeMap<String, Map<String,SVNMergeRangeList>>();
        for (String relativePath : targetCatalog.keySet()) {
            Map<String, SVNMergeRangeList> mi = targetCatalog.get(relativePath);
            if (!relativePath.startsWith("/")) {
                relativePath = "/" + relativePath;
            }
            adjustedCatalog.put(relativePath, mi);
        }
        List<SVNMergeRange> ranges = rangelist.getRangesAsList();
        Collections.sort(ranges);
        SVNMergeRange youngestRange = ranges.get(ranges.size() - 1);
        SVNMergeRange oldestRange = ranges.get(0);
        
        long youngestRev = youngestRange.getEndRevision();
        long oldestRev = oldestRange.getStartRevision();
        
        LogEntryReceiver filteringReceiver = new LogEntryReceiver();
        filteringReceiver.receiver = receiver;
        filteringReceiver.rangelist = rangelist;
        filteringReceiver.isFilteringMerged = filteringMerged;
        filteringReceiver.targetCatalog = adjustedCatalog;
        filteringReceiver.mergeSourcePaths = mergeSourcePaths;
        filteringReceiver.reposTargertAbsPath = absReposTargetPath;
        
        SvnLog log = getOperation().getOperationFactory().createLog();
        
        log.setSingleTarget(SvnTarget.fromURL(sourceURL, SVNRevision.create(youngestRev)));
        log.setDiscoverChangedPaths(true /*getOperation().isDiscoverChangedPaths()*/);
        log.setRevisionProperties(getOperation().getRevisionProperties());
        log.setLimit(-1);
        log.setStopOnCopy(false);
        log.setUseMergeHistory(false);
        log.addRange(oldestRevsFirst ? SvnRevisionRange.create(SVNRevision.create(oldestRev), SVNRevision.create(youngestRev)) : SvnRevisionRange.create(SVNRevision.create(youngestRev), SVNRevision.create(oldestRev)));
        log.setReceiver(filteringReceiver);
        
        log.run();
    }

    private static class LogEntryReceiver implements ISvnObjectReceiver<SVNLogEntry> {

        private boolean isFilteringMerged;
        private List<String> mergeSourcePaths;
        private String reposTargertAbsPath;
        private Map<String, Map<String, SVNMergeRangeList>> targetCatalog;
        private SVNMergeRangeList rangelist;
        private ISvnObjectReceiver<SVNLogEntry> receiver;
        
        public LogEntryReceiver() {
        }

        public void receive(SvnTarget target, SVNLogEntry logEntry) throws SVNException {
            if (logEntry.getRevision() == 0) {
                return;
            }
            SVNMergeRangeList thisRangeList = new SVNMergeRangeList(logEntry.getRevision() - 1, logEntry.getRevision(), true);
            SVNMergeRangeList intersection = this.rangelist.intersect(thisRangeList, false);
            
            if (intersection == null || intersection.isEmpty()) {
                return;
            }
            intersection = thisRangeList.intersect(rangelist, true);
            logEntry.setNonInheriable(intersection.isEmpty());
            
            if ((logEntry.isNonInheritable() || !isFilteringMerged) && logEntry.getChangedPaths() != null) {
                boolean allSubtreesHaveThisRev = true;
                SVNMergeRangeList thisRevRangeList = new SVNMergeRangeList(logEntry.getRevision() - 1, logEntry.getRevision(), true);
                for (String changedPath : logEntry.getChangedPaths().keySet()) {
                    String mergeSourceRelTarget = null;
                    boolean interrupted = false;
                    String mSourcePath = null;
                    for (String mergeSourcePath : this.mergeSourcePaths) {
                        mSourcePath = mergeSourcePath;
                        mergeSourceRelTarget = mergeSourcePath.equals(changedPath) ? "" : SVNPathUtil.getPathAsChild(mergeSourcePath, changedPath);
                        if (mergeSourceRelTarget != null) {
                            interrupted = true;
                            if ("".equals(mergeSourceRelTarget) && logEntry.getChangedPaths().get(changedPath).getType() != 'M') {                                
                                interrupted = false;                                
                            }
                            break;
                        }
                    }
                    if (!interrupted) {
                        continue;
                    }
                    String targetPathAffected = SVNPathUtil.append(reposTargertAbsPath, mergeSourceRelTarget);
                    if (!targetPathAffected.startsWith("/")) {
                        targetPathAffected = "/" + targetPathAffected;
                    }
                    Map<String, SVNMergeRangeList> nearestAncestorMergeInfo = null;
                    boolean ancestorIsSelf = false;
                    for(String path : targetCatalog.keySet()) {
                        if (SVNPathUtil.isAncestor(path, targetPathAffected)) {
                            nearestAncestorMergeInfo = targetCatalog.get(path);
                            ancestorIsSelf = path.equals(targetPathAffected);
                            if (ancestorIsSelf) {
                                break;
                            }
                        }
                    }
                    
                    if (nearestAncestorMergeInfo != null && ancestorIsSelf && logEntry.getChangedPaths().get(changedPath).getType() != 'M') {
                        //
                        SVNMergeRangeList rlist = nearestAncestorMergeInfo.get(changedPath);
                        SVNMergeRange youngestRange = rlist.getRanges()[rlist.getSize() - 1];
                        if (youngestRange.getEndRevision() > logEntry.getRevision()) {
                            continue;
                        }
                    }
                    boolean foundThisRevision = false;
                    if (nearestAncestorMergeInfo != null) {
                        for(String path : nearestAncestorMergeInfo.keySet()) {
                            SVNMergeRangeList rlist = nearestAncestorMergeInfo.get(path);
                            if (SVNPathUtil.isAncestor(mSourcePath, path)) {
                                SVNMergeRangeList inter = rlist.intersect(thisRevRangeList, false);
                                if (!inter.isEmpty()) {
                                    if (ancestorIsSelf) {
                                        foundThisRevision = true;
                                        break;
                                    } else {
                                        inter = rlist.intersect(thisRevRangeList, true);
                                        if (!inter.isEmpty()) {
                                            foundThisRevision = true;
                                            break;
                                        }
                                    }
                                }
                                
                            }
                        }
                    }
                    if (!foundThisRevision) {
                        allSubtreesHaveThisRev = false;
                        break;
                    }
                }
                
                if (allSubtreesHaveThisRev) {
                    if (isFilteringMerged) {
                        logEntry.setNonInheriable(false);
                    } else {
                        return;
                    }
                }
            }
            
            receiver.receive(target, logEntry);
            
        }
    }

}
