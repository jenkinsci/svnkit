package org.tmatesoft.svn.core.internal.wc2.ng;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.File;


public class SvnFilterDiffCallback implements ISvnDiffCallback2 {

    private final File prefixPath;
    private final ISvnDiffCallback2 delegate;

    public SvnFilterDiffCallback(File prefixPath, ISvnDiffCallback2 delegate) {
        this.prefixPath = prefixPath;
        this.delegate = delegate;
    }

    @Override
    public void fileOpened(SvnDiffCallbackResult result, File relPath, SvnDiffSource leftSource, SvnDiffSource rightSource, SvnDiffSource copyFromSource, boolean createDirBaton, Object dirBaton) throws SVNException {
        relPath = SVNFileUtil.skipAncestor(prefixPath, relPath);
        if (relPath == null) {
            result.skip = true;
            return;
        }
        delegate.dirOpened(result, relPath, leftSource, rightSource, copyFromSource, dirBaton);
    }

    @Override
    public void fileChanged(SvnDiffCallbackResult result, File relPath, SvnDiffSource leftSource, SvnDiffSource rightSource, File leftFile, File rightFile, SVNProperties leftProps, SVNProperties rightProps, boolean fileModified, SVNProperties propChanges) throws SVNException {
        relPath = SVNFileUtil.skipAncestor(prefixPath, relPath);
        assert relPath != null;

        delegate.fileChanged(result, relPath, leftSource, rightSource, leftFile, rightFile, leftProps, rightProps, fileModified, propChanges);
    }

    @Override
    public void fileAdded(SvnDiffCallbackResult result, File relPath, SvnDiffSource copyFromSource, SvnDiffSource rightSource, File copyFromFile, File rightFile, SVNProperties copyFromProps, SVNProperties rightProps) throws SVNException {
        relPath = SVNFileUtil.skipAncestor(prefixPath, relPath);
        assert relPath != null;

        delegate.fileAdded(result, relPath, copyFromSource, rightSource, copyFromFile, rightFile, copyFromProps, rightProps);
    }

    @Override
    public void fileDeleted(SvnDiffCallbackResult result, File relPath, SvnDiffSource leftSource, File leftFile, SVNProperties leftProps) throws SVNException {
        relPath = SVNFileUtil.skipAncestor(prefixPath, relPath);
        assert relPath != null;

        delegate.fileDeleted(result, relPath, leftSource, leftFile, leftProps);
    }

    @Override
    public void fileClosed(SvnDiffCallbackResult result, File relPath, SvnDiffSource leftSource, SvnDiffSource rightSource) throws SVNException {
        relPath = SVNFileUtil.skipAncestor(prefixPath, relPath);
        assert relPath != null;

        delegate.fileClosed(result, relPath, leftSource, rightSource);
    }

    @Override
    public void dirOpened(SvnDiffCallbackResult result, File relPath, SvnDiffSource leftSource, SvnDiffSource rightSource, SvnDiffSource copyFromSource, Object dirBaton) throws SVNException {
        relPath = SVNFileUtil.skipAncestor(prefixPath, relPath);
        if (relPath == null) {
            result.skip = true;
            return;
        }

        delegate.dirOpened(result, relPath, leftSource, rightSource, copyFromSource, dirBaton);
    }

    @Override
    public void dirChanged(SvnDiffCallbackResult result, File relPath, SvnDiffSource leftSource, SvnDiffSource rightSource, SVNProperties leftProps, SVNProperties rightProps, SVNProperties propChanges, Object dirBaton) throws SVNException {
        relPath = SVNFileUtil.skipAncestor(prefixPath, relPath);
        assert relPath != null;

        delegate.dirChanged(result, relPath, leftSource, rightSource, leftProps, rightProps, propChanges, dirBaton);
    }

    @Override
    public void dirDeleted(SvnDiffCallbackResult result, File relPath, SvnDiffSource leftSource, SVNProperties leftProps, Object dirBaton) throws SVNException {
        relPath = SVNFileUtil.skipAncestor(prefixPath, relPath);
        assert relPath != null;

        delegate.dirDeleted(result, relPath, leftSource, leftProps, dirBaton);
    }

    @Override
    public void dirAdded(SvnDiffCallbackResult result, File relPath, SvnDiffSource copyFromSource, SvnDiffSource rightSource, SVNProperties copyFromProps, SVNProperties rightProps, Object dirBaton) throws SVNException {
        relPath = SVNFileUtil.skipAncestor(prefixPath, relPath);
        assert relPath != null;

        delegate.dirAdded(result, relPath, copyFromSource, rightSource, copyFromProps, rightProps, dirBaton);
    }

    @Override
    public void dirClosed(SvnDiffCallbackResult result, File relPath, SvnDiffSource leftSource, SvnDiffSource rightSource, Object dirBaton) throws SVNException {
        relPath = SVNFileUtil.skipAncestor(prefixPath, relPath);
        assert relPath != null;

        delegate.dirClosed(result, relPath, leftSource, rightSource, dirBaton);
    }

    @Override
    public void nodeAbsent(SvnDiffCallbackResult result, File relPath, Object dirBaton) throws SVNException {
        relPath = SVNFileUtil.skipAncestor(prefixPath, relPath);
        assert relPath != null;

        delegate.nodeAbsent(result, relPath, dirBaton);
    }
}
