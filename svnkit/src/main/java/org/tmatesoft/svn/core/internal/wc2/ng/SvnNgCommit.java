package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc2.ISvnCommitRunner;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnNgCommitUtil.ISvnUrlKindCallback;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnCommitInfo;
import org.tmatesoft.svn.core.wc2.SvnCommitPacket;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgCommit extends SvnNgOperationRunner<SvnCommitInfo, SvnCommit> implements ISvnCommitRunner, ISvnUrlKindCallback {

    public SvnCommitPacket collectCommitItems(SvnCommit operation) throws SVNException {
        setOperation(operation);
        
        SvnCommitPacket packet = new SvnCommitPacket();
        Collection<String> targets = new ArrayList<String>();

        String[] validatedPaths = new String[getOperation().getTargets().size()];
        int i = 0;
        for(SvnTarget target : getOperation().getTargets()) {
            validatedPaths[i] = target.getFile().getAbsolutePath();
            validatedPaths[i] = validatedPaths[i].replace(File.separatorChar, '/');
            i++;
        }
        String rootPath = SVNPathUtil.condencePaths(validatedPaths, targets, getOperation().getDepth() == SVNDepth.INFINITY);
        if (rootPath == null) {
            return packet;
        }
        File baseDir = new File(rootPath).getAbsoluteFile();
        if (targets.isEmpty()) {
            targets.add("");
        }
        Collection<File> lockTargets = determineLockTargets(baseDir, targets);
        Collection<File> lockedRoots = new HashSet<File>();
        try {
            for (File lockTarget : lockTargets) {
                File lockRoot = getWcContext().acquireWriteLock(lockTarget, false, true);
                lockedRoots.add(lockRoot);
            }
            packet.setLockingContext(this, lockedRoots);
            
            Map<SVNURL, String> lockTokens = new HashMap<SVNURL, String>();
            SvnNgCommitUtil.harversCommittables(getWcContext(), packet, lockTokens, 
                    baseDir, targets, getOperation().getDepth(), 
                    !getOperation().isKeepLocks(), getOperation().getApplicableChangelists(), 
                    this);
            packet.setLockTokens(lockTokens);
            if (packet.getRepositoryRoots().size() > 1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                        "Commit can only commit to a single repository at a time.\n" +
                        "Are all targets part of the same working copy?");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            
            if (!packet.isEmpty()) {
                return packet;
            } else {
                packet.dispose();
                return new SvnCommitPacket();
            }
        } catch (SVNException e) {
            packet.dispose();
            throw e;
        } 
    }

    @Override
    protected SvnCommitInfo run(SVNWCContext context) throws SVNException {
        return null;
    }

    public SVNNodeKind getUrlKind(SVNURL url, long revision) throws SVNException {
        return getRepositoryAccess().createRepository(url, null).checkPath("", revision);
    }

    private Collection<File> determineLockTargets(File baseDirectory, Collection<String> targets) throws SVNException {
        Map<File, Collection<File>> wcItems = new HashMap<File, Collection<File>>();        
        for (String t: targets) {
            File target = SVNFileUtil.createFilePath(baseDirectory, t);
            File wcRoot = null;
            try {
                wcRoot = getWcContext().getDb().getWCRoot(target);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                    continue;
                }
                throw e;
            }
            Collection<File> wcTargets = wcItems.get(wcRoot);
            if (wcTargets == null) {
                wcTargets = new HashSet<File>();
                wcItems.put(wcRoot, wcTargets);                
            }
            wcTargets.add(target);
        }
        Collection<File> lockTargets = new HashSet<File>();
        for (File wcRoot : wcItems.keySet()) {
            Collection<File> wcTargets = wcItems.get(wcRoot);
            if (wcTargets.size() == 1) {
                if (wcRoot.equals(wcTargets.iterator().next())) {
                    lockTargets.add(wcRoot);
                } else {
                    lockTargets.add(SVNFileUtil.getParentFile(wcTargets.iterator().next()));
                }
            } else if (wcTargets.size() > 1) {
                lockTargets.add(wcRoot);
            }
        }
        return lockTargets;
    }

    public void disposeCommitPacket(Object lockingContext) throws SVNException {
        if (!(lockingContext instanceof Collection)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Collection<File> lockedPaths = (Collection<File>) lockingContext;
        
        for (File lockedPath : lockedPaths) {
            getWcContext().releaseWriteLock(lockedPath);
        }
    }
}
