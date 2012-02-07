package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.LocationsInfo;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.RepositoryInfo;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.SVNLocationSegment;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnGetProperties;
import org.tmatesoft.svn.core.wc2.SvnMerge;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgMergeReintegrate extends SvnNgOperationRunner<Void, SvnMerge>{

    @Override
    public boolean isApplicable(SvnMerge operation, SvnWcGeneration wcGeneration) throws SVNException {
        if (super.isApplicable(operation, wcGeneration)) {
            return operation.isReintegrate();
        }
        return false;
    }

    @Override
    protected Void run(SVNWCContext context) throws SVNException {
        File lockPath = getLockPath(getFirstTarget());
        if (getOperation().isDryRun()) {
            merge(context, getOperation().getSource(), getFirstTarget(), getOperation().isDryRun());
            
        } else {
            
            try {
                lockPath = getWcContext().acquireWriteLock(lockPath, false, true);
                merge(context, getOperation().getSource(), getFirstTarget(), getOperation().isDryRun());
            } finally {
                getWcContext().releaseWriteLock(lockPath);
                sleepForTimestamp();
            }
        }
        return null;
    }
    
    private File getLockPath(File firstTarget) throws SVNException {
        SVNNodeKind kind = getWcContext().readKind(firstTarget, false);
        if (kind == SVNNodeKind.DIR) {
            return firstTarget;
        } else {
            return SVNFileUtil.getParentFile(firstTarget);
        }
    }

    private void merge(SVNWCContext context, SvnTarget mergeSource, File mergeTarget, boolean dryRun) throws SVNException {
        SVNFileType targetKind = SVNFileType.getType(mergeTarget);
        if (targetKind == SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "Path ''{0}'' does not exist", mergeTarget);
            SVNErrorManager.error(err, SVNLogType.WC);
        }     
        
        SVNURL url2 = getRepositoryAccess().getTargetURL(mergeSource);
        if (url2 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", mergeTarget);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SVNURL wcReposRoot = context.getNodeReposInfo(mergeTarget).reposRootUrl;
        Structure<RepositoryInfo> sourceReposInfo = getRepositoryAccess().createRepositoryFor(mergeSource, mergeSource.getPegRevision(), mergeSource.getPegRevision(), null);
        SVNURL sourceReposRoot = ((SVNRepository) sourceReposInfo.get(RepositoryInfo.repository)).getRepositoryRoot(true);
        
        if (!wcReposRoot.equals(sourceReposRoot)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, 
                    "''{0}'' must be from the same repositor as ''{1}''", mergeSource.getURL(), mergeTarget);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SvnNgMergeDriver mergeDriver = new SvnNgMergeDriver(getWcContext(), getOperation(), getRepositoryAccess(), getOperation().getMergeOptions());

        mergeDriver.ensureWcIsSuitableForMerge(mergeTarget, false, false, false);
        
        long targetBaseRev = context.getNodeBaseRev(mergeTarget);
        long rev1 = targetBaseRev;
        File sourceReposRelPath = new File(SVNURLUtil.getRelativeURL(wcReposRoot, url2));
        File targetReposRelPath = context.getNodeReposRelPath(mergeTarget);
        
        if ("".equals(sourceReposRelPath.getPath()) || "".equals(targetReposRelPath.getPath())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE, 
                    "Neither reintegrate source nor target can be the root of repository");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        final Map<File, String> explicitMergeInfo = new HashMap<File, String>();
        SvnGetProperties pg = getOperation().getOperationFactory().createGetProperties();
        pg.setDepth(SVNDepth.INFINITY);
        pg.setSingleTarget(SvnTarget.fromFile(mergeTarget));
        pg.setReceiver(new ISvnObjectReceiver<SVNProperties>() {
            public void receive(SvnTarget target, SVNProperties props) throws SVNException {
                final String value = props.getStringValue(SVNProperty.MERGE_INFO);
                if (value != null) {
                    explicitMergeInfo.put(target.getFile(), value);
                }
            }
        });
        
        sourceReposInfo = getRepositoryAccess().createRepositoryFor(SvnTarget.fromURL(url2), SVNRevision.UNDEFINED, mergeSource.getPegRevision(), null);
        SVNRepository sourceRepository = sourceReposInfo.get(RepositoryInfo.repository);
        long rev2 = sourceReposInfo.lng(RepositoryInfo.revision);
        url2 = sourceReposInfo.get(RepositoryInfo.url);
        sourceReposInfo.release();
        
        SVNURL targetUrl = context.getNodeUrl(mergeTarget);
        SVNRepository targetRepository = getRepositoryAccess().createRepository(targetUrl, null, false);
        //
        try {
            SvnTarget url1 = calculateLeftHandSide(context,
                    new HashMap<String, Map<String,SVNMergeRangeList>>(),
                    new HashMap<String, Map<String,SVNMergeRangeList>>(), 
                    mergeTarget,
                    targetReposRelPath,
                    explicitMergeInfo,
                    targetBaseRev,
                    sourceReposRelPath,
                    sourceReposRoot,
                    wcReposRoot,
                    rev2,
                    sourceRepository,
                    targetRepository);
            
            if (url1 == null) {
                return;
            }
            
            if (!url1.equals(targetUrl)) {
                targetRepository.setLocation(url1.getURL(), false);
            }
            rev1 = url1.getPegRevision().getNumber();
            SVNLocationSegment yc = getRepositoryAccess().getYoungestCommonAncestor(url2, rev2, url1.getURL(), rev1);
            
            if (yc == null || !(yc.getPath() != null && yc.getStartRevision() >= 0)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE, 
                        "'{0}'@'{1}' must be ancestrally related to '{2}'@'{3}'", url1, new Long(rev1), url2, new Long(rev2));
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            
            if (rev1 > yc.getStartRevision()) {
                // TODO check already merged revs for continuosity.
            }
            
            mergeDriver.mergeCousinsAndSupplementMergeInfo(mergeTarget, 
                    targetRepository, sourceRepository, 
                    url1.getURL(), rev1, 
                    url2, rev2, 
                    yc.getStartRevision(), 
                    sourceReposRoot, 
                    wcReposRoot, 
                    SVNDepth.INFINITY, 
                    false, 
                    false, 
                    false, 
                    dryRun);
        } finally {
            targetRepository.closeSession();
        }
    }
    
    private SvnTarget calculateLeftHandSide(SVNWCContext context,
            Map<String, Map<String, SVNMergeRangeList>>  mergedToSourceCatalog,
            Map<String, Map<String, SVNMergeRangeList>>  unmergedToSourceCatalog,
            File targetAbsPath,
            File targetReposRelPath,
            Map<File, String> subtreesWithMergeInfo,
            long targetRev,
            File sourceReposRelPath,
            SVNURL sourceReposRoot,
            SVNURL targetReposRoot,
            long sourceRev,
            SVNRepository sourceRepository,
            SVNRepository targetRepository)  throws SVNException {
        
        if (!subtreesWithMergeInfo.containsKey(targetAbsPath)) {
            subtreesWithMergeInfo.put(targetAbsPath, "");
        }
        
        final Map<File, List<SVNLocationSegment>> segmentsMap = new HashMap<File, List<SVNLocationSegment>>();
        
        for (File path : subtreesWithMergeInfo.keySet()) {
            String miValue = subtreesWithMergeInfo.get(path);
            try {
                SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(miValue), null);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.MERGE_INFO_PARSE_ERROR) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_MERGEINFO_NO_MERGETRACKING,
                            "Invalid mergeinfo detected on ''{0}'', reintegrate merge not possible", path);
                    SVNErrorManager.error(err, SVNLogType.WC);                    
                }
                throw e;
            }
            File pathReposRelPath = context.getNodeReposRelPath(path);
            File pathSessionRelPath = SVNWCUtils.skipAncestor(targetReposRelPath, pathReposRelPath);
            if (pathSessionRelPath == null && pathReposRelPath.equals(targetReposRelPath)) {
                pathSessionRelPath = new File("");
            }
            
            List<SVNLocationSegment> segments = targetRepository.getLocationSegments(pathSessionRelPath.getPath(), targetRev, targetRev, -1);
            segmentsMap.put(pathReposRelPath, segments);
        }
        
        SVNURL sourceUrl = SVNWCUtils.join(sourceReposRoot, sourceReposRelPath);
        SVNURL targetUrl = SVNWCUtils.join(targetReposRoot, targetReposRelPath);
        SVNLocationSegment yc = getRepositoryAccess().getYoungestCommonAncestor(sourceUrl, sourceRev, targetUrl, targetRev);
        if (!(yc != null && yc.getPath() != null && yc.getStartRevision() >= 0)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE, 
                    "'{0}'@'{1}' must be ancestrally related to '{2}'@'{3}'", sourceUrl, new Long(sourceRev), targetUrl, new Long(targetRev));
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        if (sourceRev == yc.getStartRevision()) {
            return null;
        }
        
        Map<String, Map<String, SVNMergeRangeList>> mergeInfoCatalog = 
                SvnNgMergeinfoUtil.convertToCatalog(sourceRepository.getMergeInfo(new String[] {""}, sourceRev, SVNMergeInfoInheritance.INHERITED, true));
        mergeInfoCatalog = SvnNgMergeinfoUtil.addPrefixToCatalog(mergeInfoCatalog, sourceReposRelPath);
        if (mergedToSourceCatalog != null) {
            mergedToSourceCatalog.putAll(mergeInfoCatalog);
        }
        UnmergedMergeInfo unmergedMergeInfo = findUnmergedMergeInfo(yc.getStartRevision(), mergeInfoCatalog, segmentsMap, sourceReposRelPath, targetReposRelPath, targetRev, sourceRev, sourceRepository, targetRepository);
        unmergedMergeInfo.catalog = SVNMergeInfoUtil.elideMergeInfoCatalog(unmergedMergeInfo.catalog);
        if (unmergedToSourceCatalog != null) {
            unmergedToSourceCatalog.putAll(unmergedMergeInfo.catalog);
        }
        if (unmergedMergeInfo.neverSynced) {
            return SvnTarget.fromURL(sourceReposRoot.appendPath(yc.getPath(), false), SVNRevision.create(yc.getStartRevision()));
        } else {
            Structure<LocationsInfo> locations = getRepositoryAccess().getLocations(targetRepository, SvnTarget.fromURL(targetUrl), 
                    SVNRevision.create(targetRev), 
                    SVNRevision.create(unmergedMergeInfo.youngestMergedRevision), 
                    SVNRevision.UNDEFINED);
            SVNURL youngestUrl = locations.get(LocationsInfo.startUrl);
            locations.release();
            return SvnTarget.fromURL(youngestUrl, SVNRevision.create(unmergedMergeInfo.youngestMergedRevision));
        }
    }

    private UnmergedMergeInfo findUnmergedMergeInfo(long ycAncestorRev, Map<String, Map<String, SVNMergeRangeList>> sourceCatalog, Map<File, List<SVNLocationSegment>> targetSegments,
            File sourceReposRelPath, File targetReposRelPath, long targetRev, long sourceRev, SVNRepository sourceRepos, SVNRepository targetRepos) throws SVNException {
        UnmergedMergeInfo result = new UnmergedMergeInfo();
        result.neverSynced = true;
        Map<String, Map<String, SVNMergeRangeList>> newCatalog = new TreeMap<String, Map<String,SVNMergeRangeList>>();
        for (File path : targetSegments.keySet()) {
            List<SVNLocationSegment> segments = targetSegments.get(path);
            File sourcePathRelToSession = SVNWCUtils.skipAncestor(targetReposRelPath, path);
            if (sourcePathRelToSession == null && targetReposRelPath.equals(path)) {
                sourcePathRelToSession = new File("");
            }
            File sourcePath = SVNFileUtil.createFilePath(sourceReposRelPath, sourcePathRelToSession);
            Map<String, SVNMergeRangeList> targetHistoryAsMergeInfo = SvnRepositoryAccess.getMergeInfoFromSegments(segments);
            targetHistoryAsMergeInfo = SVNMergeInfoUtil.filterMergeInfoByRanges(targetHistoryAsMergeInfo, sourceRev, ycAncestorRev);
            Map<String, SVNMergeRangeList> sourceMergeInfo = sourceCatalog.get(sourcePath.getPath());
            
            if (sourceMergeInfo != null) {
                sourceCatalog.remove(sourcePath.getParentFile());
                Map<String, SVNMergeRangeList> explicitIntersection = SVNMergeInfoUtil.intersectMergeInfo(sourceMergeInfo, targetHistoryAsMergeInfo, true);
                if (explicitIntersection != null && !explicitIntersection.isEmpty()) {
                    result.neverSynced = false;
                    long[] endPoints = SVNMergeInfoUtil.getRangeEndPoints(explicitIntersection);
                    if (result.youngestMergedRevision < 0 || endPoints[0] > result.youngestMergedRevision) {
                        result.youngestMergedRevision = endPoints[0];
                    }
                }
            } else {
                SVNNodeKind kind = sourceRepos.checkPath(sourcePathRelToSession.getPath(), sourceRev);
                if (kind == SVNNodeKind.NONE) {
                    continue;
                }
                Map<String, Map<String, SVNMergeRangeList>> subtreeCatalog = 
                        SvnNgMergeinfoUtil.convertToCatalog(sourceRepos.getMergeInfo(new String[] {sourcePathRelToSession.getPath()}, sourceRev, SVNMergeInfoInheritance.INHERITED, false));
                sourceMergeInfo = subtreeCatalog.get(sourcePathRelToSession.getPath());
                if (sourceMergeInfo == null) {
                    sourceMergeInfo = new HashMap<String, SVNMergeRangeList>();
                }
            }
            
            segments = sourceRepos.getLocationSegments(sourcePathRelToSession.getPath(), sourceRev, sourceRev, -1);
            Map<String, SVNMergeRangeList> sourceHistroryAsMergeInfo = SvnRepositoryAccess.getMergeInfoFromSegments(segments);
            sourceMergeInfo = SVNMergeInfoUtil.mergeMergeInfos(sourceMergeInfo, sourceHistroryAsMergeInfo);
            Map<String, SVNMergeRangeList> commonMergeInfo = SVNMergeInfoUtil.intersectMergeInfo(sourceMergeInfo, targetHistoryAsMergeInfo, true);
            Map<String, SVNMergeRangeList> filteredMergeInfo = SVNMergeInfoUtil.removeMergeInfo(commonMergeInfo, targetHistoryAsMergeInfo, true);
            
            newCatalog.put(sourcePath.getPath(), filteredMergeInfo);
        }
        
        if (!sourceCatalog.isEmpty()) {
            for(String path : sourceCatalog.keySet()) {
                File sourcePathRelToSession = SVNWCUtils.skipAncestor(sourceReposRelPath, new File(path));
                File targetPath = SVNWCUtils.skipAncestor(sourceReposRelPath, new File(path));
                List<SVNLocationSegment> segments = null;
                Map<String, SVNMergeRangeList> sourceMergeInfo = sourceCatalog.get(path);
                try {
                    segments = targetRepos.getLocationSegments(targetPath.getPath(), targetRev, targetRev, -1);
                } catch (SVNException e) {
                    SVNErrorCode ec = e.getErrorMessage().getErrorCode();
                    if (ec == SVNErrorCode.FS_NOT_FOUND || ec == SVNErrorCode.RA_DAV_REQUEST_FAILED) {
                        continue;
                    }
                    throw e;
                }
                
                Map<String, SVNMergeRangeList> targetHistoryAsMergeInfo = SvnRepositoryAccess.getMergeInfoFromSegments(segments);
                Map<String, SVNMergeRangeList> explicitIntersection = SVNMergeInfoUtil.intersectMergeInfo(sourceMergeInfo, targetHistoryAsMergeInfo, true);
                if (explicitIntersection != null && !explicitIntersection.isEmpty()) {
                    result.neverSynced = false;
                    long[] endPoints = SVNMergeInfoUtil.getRangeEndPoints(explicitIntersection);
                    if (result.youngestMergedRevision < 0 || endPoints[0] > result.youngestMergedRevision) {
                        result.youngestMergedRevision = endPoints[0];
                    }
                }
                segments = sourceRepos.getLocationSegments(sourcePathRelToSession.getPath(), targetRev, targetRev, -1);
                Map<String, SVNMergeRangeList> sourceHistoryAsMergeInfo = SvnRepositoryAccess.getMergeInfoFromSegments(segments);
                sourceMergeInfo = SVNMergeInfoUtil.mergeMergeInfos(sourceMergeInfo, sourceHistoryAsMergeInfo);
                Map<String, SVNMergeRangeList> commonMergeInfo = SVNMergeInfoUtil.intersectMergeInfo(sourceMergeInfo, targetHistoryAsMergeInfo, true);
                Map<String, SVNMergeRangeList> filteredMergeInfo = SVNMergeInfoUtil.removeMergeInfo(commonMergeInfo, targetHistoryAsMergeInfo, true);
                if (!filteredMergeInfo.isEmpty()) {
                    newCatalog.put(path, filteredMergeInfo);
                }
            }
        }
        if (result.youngestMergedRevision >= 0) {
            newCatalog = SVNMergeInfoUtil.filterCatalogByRanges(newCatalog, result.youngestMergedRevision, 0);
        }
        result.catalog = newCatalog;
        return result;
    }
    
    private static class UnmergedMergeInfo {
        private Map<String, Map<String, SVNMergeRangeList>> catalog;
        private boolean neverSynced;
        private long youngestMergedRevision; 
    }
}
