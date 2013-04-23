package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb.Mode;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.CheckWCRootInfo;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb.DirParsedInfo;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDbRoot;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbProperties;
import org.tmatesoft.svn.core.io.SVNRepository;

public class SvnNgInheritableProperties {
    
    public static Map<File, Map<String, SVNProperties>> getInheritalbeProperites(SVNWCContext context, SVNRepository repository, File localAbsPath, long revision, SVNDepth depth) throws SVNException {
        final SVNURL originalLocation = repository.getLocation();
        final Map<File, Map<String, SVNProperties>> result = new HashMap<File, Map<String, SVNProperties>>();

        final DirParsedInfo pdh = ((SVNWCDb) context.getDb()).parseDir(localAbsPath, Mode.ReadOnly);
        final SVNWCDbRoot wcRoot = pdh.wcDbDir.getWCRoot();
        final File localRelPath = pdh.localRelPath;
        try {
            final Map<File, File> nodesWithIProps = SvnWcDbProperties.getInheritedPropertiesNodes(wcRoot, localRelPath, depth);
            if (!nodesWithIProps.containsKey(localAbsPath)) {
                if (needsCachedIProps(context, localAbsPath, repository)) {
                    nodesWithIProps.put(localAbsPath, localRelPath);
                }
            }
            for (File localNodeAbsPath : nodesWithIProps.keySet()) {
                final String reposNodePath = SVNFileUtil.getFilePath(nodesWithIProps.get(localNodeAbsPath));
                if ("".equals(reposNodePath)) {
                    continue;
                }
                final SVNURL nodeURL = context.getNodeUrl(localNodeAbsPath);
                repository.setLocation(nodeURL, false);
                Map<String, SVNProperties> iprops = null;
                try {
                    iprops = repository.getInheritedProperties("", revision, null);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.FS_NOT_FOUND) {
                        throw e;
                    }
                    continue;
                }
                result.put(localNodeAbsPath, iprops);
            }
        } finally {
            repository.setLocation(originalLocation, false);
        }
        return result;
    }

    private static boolean needsCachedIProps(SVNWCContext context, File localAbsPath, SVNRepository repository) throws SVNException {
        CheckWCRootInfo rootInfo  = null;
        try {
            rootInfo = context.checkWCRoot(localAbsPath, true);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                return false;
            }
            throw e;
        }
        if (rootInfo != null && (rootInfo.switched || rootInfo.wcRoot)) {
            final SVNURL location = repository.getLocation();
            final SVNURL root = repository.getRepositoryRoot(true);
            return !location.equals(root);
        }
        return false;
    }

}
