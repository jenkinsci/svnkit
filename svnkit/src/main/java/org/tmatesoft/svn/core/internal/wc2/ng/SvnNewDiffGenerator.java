package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnNewDiffGenerator implements ISVNDiffGenerator {

    private final ISvnDiffGenerator generator;
    private boolean diffDeleted;
    private boolean diffAdded;
    private boolean diffCopied;
    private boolean diffUnversioned;
    private File basePath;

    public SvnNewDiffGenerator(ISvnDiffGenerator generator) {
        this.generator = generator;
    }

    public void init(String anchorPath1, String anchorPath2) {
        generator.init(getTarget(anchorPath1), getTarget(anchorPath2));
    }

    public void setBasePath(File basePath) {
        this.basePath = basePath;
    }

    public File getBasePath() {
        return basePath;
    }

    public void setForcedBinaryDiff(boolean forced) {
        generator.setForcedBinaryDiff(forced);
    }

    public void setEncoding(String encoding) {
        generator.setEncoding(encoding);
    }

    public String getEncoding() {
        return generator.getEncoding();
    }

    public void setEOL(byte[] eol) {
        generator.setEOL(eol);
    }

    public byte[] getEOL() {
        return generator.getEOL();
    }

    public void setDiffDeleted(boolean isDiffDeleted) {
        diffDeleted = isDiffDeleted;
    }

    public boolean isDiffDeleted() {
        return diffDeleted;
    }

    public void setDiffAdded(boolean isDiffAdded) {
        diffAdded = isDiffAdded;
    }

    public boolean isDiffAdded() {
        return diffAdded;
    }

    public void setDiffCopied(boolean isDiffCopied) {
        diffCopied = isDiffCopied;
    }

    public boolean isDiffCopied() {
        return diffCopied;
    }

    public void setDiffUnversioned(boolean diffUnversioned) {
        this.diffUnversioned = diffUnversioned;
    }

    public boolean isDiffUnversioned() {
        return diffUnversioned;
    }

    public File createTempDirectory() throws SVNException {
        return SVNFileUtil.createTempDirectory("diff");
    }

    public void displayPropDiff(String path, SVNProperties baseProps, SVNProperties diff, OutputStream result) throws SVNException {
        generator.displayPropsChanged(getTarget(path), "", "", false, baseProps, diff, result);
    }

    public void displayFileDiff(String path, File file1, File file2, String rev1, String rev2, String mimeType1, String mimeType2, OutputStream result) throws SVNException {
        generator.displayContentChanged(getTarget(path), file1, file2, rev1, rev2, mimeType1, mimeType2, SvnDiffCallback.OperationKind.Modified, null, result);
    }

    public void displayDeletedDirectory(String path, String rev1, String rev2) throws SVNException {
        generator.displayDeletedDirectory(getTarget(path), rev1, rev2, null);
    }

    public void displayAddedDirectory(String path, String rev1, String rev2) throws SVNException {
        generator.displayAddedDirectory(getTarget(path), rev1, rev2, null);
    }

    public boolean isForcedBinaryDiff() {
        return generator.isForcedBinaryDiff();
    }

    private SvnTarget getTarget(String path) {
        //TODO:
        return SvnTarget.fromFile(new File(path));
    }
}
