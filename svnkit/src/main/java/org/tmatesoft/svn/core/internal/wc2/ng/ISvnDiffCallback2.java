package org.tmatesoft.svn.core.internal.wc2.ng;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;

import java.io.File;

public interface ISvnDiffCallback2 {

    public void fileOpened(SvnDiffCallbackResult result,
                           File relPath,
                           SvnDiffSource leftSource,
                           SvnDiffSource rightSource,
                           SvnDiffSource copyFromSource,
                           boolean createDirBaton) throws SVNException;

    public void fileChanged(SvnDiffCallbackResult result,
                            File relPath,
                            SvnDiffSource leftSource,
                            SvnDiffSource rightSource,
                            File leftFile, File rightFile,
                            SVNProperties leftProps, SVNProperties rightProps,
                            boolean fileModified,
                            SVNProperties propChanges) throws SVNException;

    public void fileAdded(SvnDiffCallbackResult result,
                          File relPath,
                          SvnDiffSource copyFromSource,
                          SvnDiffSource rightSource,
                          File copyFromFile,
                          File rightFile,
                          SVNProperties copyFromProps,
                          SVNProperties rightProps) throws SVNException;

    public void fileDeleted(SvnDiffCallbackResult result,
                            File relPath,
                            SvnDiffSource leftSource,
                            File leftFile,
                            SVNProperties leftProps) throws SVNException;

    public void dirOpened(SvnDiffCallbackResult result,
                          File relPath,
                          SvnDiffSource leftSource,
                          SvnDiffSource rightSource,
                          SvnDiffSource copyFromSource) throws SVNException;

    public void dirChanged(SvnDiffCallbackResult result,
                           File relPath,
                           SvnDiffSource leftSource,
                           SvnDiffSource rightSource,
                           SVNProperties leftProps,
                           SVNProperties rightProps,
                           SVNProperties propChanges) throws SVNException;

    public void dirDeleted(SvnDiffCallbackResult result,
                           File relPath,
                           SvnDiffSource leftSource,
                           SVNProperties leftProps) throws SVNException;

    public void dirAdded(SvnDiffCallbackResult result,
                         File relPath,
                         SvnDiffSource copyFromSource,
                         SvnDiffSource rightSource,
                         SVNProperties copyFromProps,
                         SVNProperties rightProps) throws SVNException;

    public void dirPropsChanged(SvnDiffCallbackResult result,
                                File relPath,
                                SvnDiffSource leftSource,
                                SvnDiffSource rightSource,
                                SVNProperties leftProps,
                                SVNProperties rightProps,
                                SVNProperties propChanges) throws SVNException;

    public void dirClosed(SvnDiffCallbackResult result,
                          File relPath,
                          SvnDiffSource leftSource,
                          SvnDiffSource rightSource,
                          boolean reallyClose) throws SVNException;

    public void nodeAbsent(SvnDiffCallbackResult result, File relPath) throws SVNException;
}
