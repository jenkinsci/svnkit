package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnLogMergeInfo;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgLogMergeInfo extends SvnNgOperationRunner<SVNLogEntry, SvnLogMergeInfo> {

    @Override
    protected SVNLogEntry run(SVNWCContext context) throws SVNException {
        SVNURL[] root = new SVNURL[1];
        
        Map<String, Map<String, SVNMergeRangeList>> mergeInfoCatalog = 
                SvnNgMergeinfoUtil.getMergeInfo(getWcContext(), getRepositoryAccess(), getOperation().getFirstTarget(), getOperation().getDepth() == SVNDepth.INFINITY, true, root);
        
        SvnTarget target = getOperation().getFirstTarget();
        File reposRelPath = null;
        if (target.isURL()) {
            reposRelPath = SVNFileUtil.createFilePath(SVNPathUtil.getRelativePath(root[0].getPath(), target.getURL().getPath()));
        } else {
            reposRelPath = getWcContext().getNodeReposRelPath(getOperation().getFirstTarget().getFile());
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
            history = getRepositoryAccess().getHistoryAsMergeInfo(null, target, -1, -1);
        }
        sourceHistory = getRepositoryAccess().getHistoryAsMergeInfo(null, getOperation().getSource(), -1, -1);
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
                String subtreeRelPath = reposRelPathStr.substring(subtreePath.length() + 1);
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
                    masterNonInheritableRangeList = masterNonInheritableRangeList.merge(rl);
                }
            }
            mergedMergeInfo = SVNMergeInfoUtil.intersectMergeInfo(subtreeInheritableMergeInfo, subtreeSourceHistory, false);

            SVNMergeRangeList subtreeMergeRangeList = new SVNMergeRangeList((SVNMergeRange[]) null);
            if (!mergedMergeInfo.isEmpty()) {
                for (SVNMergeRangeList rl : mergedMergeInfo.values()) {
                    rl.setInheritable(false);
                    masterInheritableRangeList = masterInheritableRangeList.merge(rl);
                    subtreeMergeRangeList = subtreeMergeRangeList.merge(rl);
                }
            }
            inheritableSubtreeMerges.put(subtreePath, subtreeMergeRangeList);
        }
        
        if (!masterInheritableRangeList.isEmpty()) {
            for (String path : inheritableSubtreeMerges.keySet()) {
                SVNMergeRangeList subtreeMergedRangeList = inheritableSubtreeMerges.get(path);
                SVNMergeRangeList deletedRanges = subtreeMergedRangeList.remove(masterInheritableRangeList, true);
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
        SVNMergeRange youngestRange = masterInheritableRangeList.getRanges()[masterInheritableRangeList.getSize() - 1];
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
        return getOperation().first();
    }

    private static class LogHandlerFilter implements ISVNLogEntryHandler {
        ISVNLogEntryHandler realHandler;
        SVNMergeRangeList rangeList;
        
        public LogHandlerFilter(ISVNLogEntryHandler handler, SVNMergeRangeList rangeList) {
            this.realHandler = handler;
            this.rangeList = rangeList;
        }
        
        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            SVNMergeRange range = new SVNMergeRange(logEntry.getRevision() - 1, logEntry.getRevision(), true);
            SVNMergeRangeList thisRangeList = new SVNMergeRangeList(range);
            SVNMergeRangeList intersection = thisRangeList.intersect(rangeList, true);
            if (intersection == null || intersection.isEmpty()) {
                return;
            }
            
            SVNErrorManager.assertionFailure(intersection.getSize() == 1, "intersection list size is " + intersection.getSize(), SVNLogType.WC);
            if (realHandler != null) {
                realHandler.handleLogEntry(logEntry);
            }
        }
    }

}
