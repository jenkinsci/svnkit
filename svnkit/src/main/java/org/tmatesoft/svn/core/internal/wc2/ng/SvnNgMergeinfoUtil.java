package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.SVNWCNodeReposInfo;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnNgMergeinfoUtil {
    
    private static class SvnMergeInfoInfo {
        Map<String, SVNMergeRangeList> mergeinfo;
        boolean inherited;
        String walkRelPath;
    }

    private static class SvnMergeInfoCatalogInfo {
        Map<String, Map<String, SVNMergeRangeList>> catalog;
        boolean inherited;
        String walkRelPath;
    }
    
    private static Map<String, SVNMergeRangeList> parseMergeInfo(SVNWCContext context, File localAbsPath) throws SVNException {
        SVNPropertyValue propValue = context.getPropertyValue(localAbsPath, SVNProperty.MERGE_INFO);
        if (propValue != null && propValue.getString() != null) {
            return SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(propValue.getString()), null);
        }
        return null;
    }
    
    private static SvnMergeInfoInfo getWCMergeInfo(SVNWCContext context, File localAbsPath, File limitAbsPath, SVNMergeInfoInheritance inheritance, 
            boolean ignoreInvalidMergeInfo) throws SVNException {
        long baseRevision = context.getNodeBaseRev(localAbsPath);
        Map<String, SVNMergeRangeList> wcMergeInfo = null;
        String walkRelPath = "";
        SvnMergeInfoInfo result = new SvnMergeInfoInfo();
        
        while(true) {
            if (inheritance == SVNMergeInfoInheritance.NEAREST_ANCESTOR) {
                wcMergeInfo = null;
                inheritance =  SVNMergeInfoInheritance.INHERITED;
            } else {
                try {
                    wcMergeInfo = parseMergeInfo(context, localAbsPath);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.MERGE_INFO_PARSE_ERROR) {
                        if (ignoreInvalidMergeInfo || !"".equals(walkRelPath)) {
                            wcMergeInfo = new HashMap<String, SVNMergeRangeList>();
                            break;
                        }
                    }
                    throw e;
                }
            }
            if (wcMergeInfo == null && inheritance != SVNMergeInfoInheritance.EXPLICIT && SVNFileUtil.getParentFile(localAbsPath) != null) {
                if (limitAbsPath != null && localAbsPath.equals(limitAbsPath)) {
                    break;
                }
                if (context.getDb().isWCRoot(localAbsPath)) {
                    break;
                }
                walkRelPath = SVNPathUtil.append(SVNFileUtil.getFileName(localAbsPath), walkRelPath);
                localAbsPath = SVNFileUtil.getFileDir(localAbsPath);
                long parentBaseRev = context.getNodeBaseRev(localAbsPath);
                long parentChangedRev = context.getNodeChangedInfo(localAbsPath).changedRev;
                if (baseRevision >= 0 && (baseRevision < parentChangedRev || parentBaseRev < baseRevision)) {
                    break;
                }
                continue;
            }
            break;
        }
        
        if ("".equals(walkRelPath)) {
            result.inherited = false;
            result.mergeinfo = wcMergeInfo;
        } else {
            if (wcMergeInfo != null) {
                result.inherited = true;
                result.mergeinfo = new HashMap<String, SVNMergeRangeList>();                
                result.mergeinfo = SVNMergeInfoUtil.adjustMergeInfoSourcePaths(result.mergeinfo, walkRelPath, wcMergeInfo);
            }
        }
        result.walkRelPath = walkRelPath;
        if (result.inherited && !result.mergeinfo.isEmpty()) {
            result.mergeinfo = SVNMergeInfoUtil.getInheritableMergeInfo(result.mergeinfo, null, -1, -1);
            SVNMergeInfoUtil.removeEmptyRangeLists(result.mergeinfo);
        }
        return result;
    }
    
    
    private static SvnMergeInfoCatalogInfo getWcMergeInfoCatalog(SVNWCContext context, boolean includeDescendants, SVNMergeInfoInheritance inheritance, File localAbsPath, File limitAbsPath, boolean ignoreInvalidMergeInfo) throws SVNException {
        SvnMergeInfoCatalogInfo result = new SvnMergeInfoCatalogInfo();
        SVNWCNodeReposInfo reposInfo = context.getNodeReposInfo(localAbsPath);
        if (reposInfo.reposRootUrl == null) {
            result.walkRelPath = "";
            return result;
        }
        File targetReposRelPath = context.getNodeReposRelPath(localAbsPath);
        SvnMergeInfoInfo mi = getWCMergeInfo(context, localAbsPath, limitAbsPath, inheritance, ignoreInvalidMergeInfo);
        result.walkRelPath = mi.walkRelPath;
        result.inherited = mi.inherited;
        
        if (mi.mergeinfo != null) {
            result.catalog = new TreeMap<String, Map<String,SVNMergeRangeList>>();
            result.catalog.put(SVNFileUtil.getFilePath(targetReposRelPath), mi.mergeinfo);
        }
        if (context.readKind(localAbsPath, false) == SVNNodeKind.DIR && includeDescendants) {
            // recursive propget do.
            final Map<File, String> mergeInfoProperties = new TreeMap<File, String>();
            ((SVNWCDb) context.getDb()).readPropertiesRecursively(localAbsPath, SVNDepth.INFINITY, false, false, null, 
            new ISvnObjectReceiver<SVNProperties>() {
                public void receive(SvnTarget target, SVNProperties object) throws SVNException {
                    if (object.getStringValue(SVNProperty.MERGE_INFO) != null) {
                        mergeInfoProperties.put(target.getFile(), object.getStringValue(SVNProperty.MERGE_INFO));
                    }
                }
            });
            
            for (File childPath : mergeInfoProperties.keySet()) {
                String propValue = mergeInfoProperties.get(childPath);
                File keyPath = context.getNodeReposRelPath(childPath);
                
                Map<String, SVNMergeRangeList> childMergeInfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(propValue), null);
                if (result.catalog == null) {
                    result.catalog = new TreeMap<String, Map<String,SVNMergeRangeList>>();                    
                }
                result.catalog.put(SVNFileUtil.getFilePath(keyPath), childMergeInfo);
            }
        }
        return result;
    }
}
