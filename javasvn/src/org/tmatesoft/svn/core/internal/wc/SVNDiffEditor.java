/*
 * Created on 26.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.OutputStream;

import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;

public class SVNDiffEditor implements ISVNEditor {

    private SVNWCAccess myWCAccess;
    private ISVNDiffGenerator myDiffGenerator;
    private boolean myUseAncestry;

    public SVNDiffEditor(SVNWCAccess wcAccess, ISVNDiffGenerator diffGenerator, boolean useAncestry) {
        myWCAccess = wcAccess;
        myDiffGenerator = diffGenerator;
        myUseAncestry = useAncestry;
    }

    public void targetRevision(long revision) throws SVNException {
    }

    public void openRoot(long revision) throws SVNException {
    }

    public void deleteEntry(String path, long revision) throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
    }

    public void openDir(String path, long revision) throws SVNException {
    }

    public void changeDirProperty(String name, String value)
            throws SVNException {
    }

    public void closeDir() throws SVNException {
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
    }

    public void openFile(String path, long revision) throws SVNException {
    }

    public void applyTextDelta(String baseChecksum) throws SVNException {
    }

    public void changeFileProperty(String name, String value)
            throws SVNException {
    }

    public void closeFile(String textChecksum) throws SVNException {
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public OutputStream textDeltaChunk(SVNDiffWindow diffWindow)
            throws SVNException {
        return null;
    }

    public void textDeltaEnd() throws SVNException {
    }

}
