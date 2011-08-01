package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.RepositoryInfo;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgCheckout extends SvnNgAbstractUpdate<Long, SvnCheckout>{

    @Override
    protected Long run(SVNWCContext context) throws SVNException {
        File dstPath = getFirstTarget();
        
        SVNURL url = getOperation().getUrl();
        Structure<RepositoryInfo> repositoryInfo = getRepositoryAccess().createRepositoryFor(
                SvnTarget.fromURL(url), 
                getOperation().getRevision(), 
                getOperation().getPegRevision(), 
                null);
        
        url = repositoryInfo.<SVNURL>get(RepositoryInfo.url);
        SVNRepository repository = repositoryInfo.<SVNRepository>get(RepositoryInfo.repository);
        long revnum = repositoryInfo.lng(RepositoryInfo.revision);
        
        repositoryInfo.release();
        
        SVNURL rootUrl = repository.getRepositoryRoot(true);
        String uuid = repository.getRepositoryUUID(true);
        SVNNodeKind kind = repository.checkPath("", revnum);
        SVNDepth depth = getOperation().getDepth();
        
        if (kind == SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "URL ''{0}'' refers to a file, not a directory", url);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL ''{0}'' doesn''t exist", url);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNFileType fileKind = SVNFileType.getType(dstPath);
        
        if (fileKind == SVNFileType.NONE) {
            SVNFileUtil.ensureDirectoryExists(dstPath);
            context.initializeWC(dstPath, url, rootUrl, uuid, revnum, depth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : depth);
        } else if (fileKind == SVNFileType.DIRECTORY) {
            int formatVersion = context.checkWC(dstPath);
            if (formatVersion != 0) {
                SVNURL entryUrl = context.getNodeUrl(dstPath);
                if (entryUrl != null && !url.equals(entryUrl)) {                
                    String message = "''{0}'' is already a working copy for a different URL";
                    message += "; perform update to complete it";
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, message, dstPath);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            } else {
                depth = depth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : depth;
                context.initializeWC(dstPath, url, rootUrl, uuid, revnum, depth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : depth);
            }
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NODE_KIND_CHANGE, "''{0}'' already exists and is not a directory", dstPath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return update(getWcContext(), getFirstTarget(), getOperation().getRevision(), getOperation().getDepth(), true, getOperation().isIgnoreExternals(), getOperation().isAllowUnversionedObstructions(), true, false, false, getOperation().isSleepForTimestamp());
    }

}
