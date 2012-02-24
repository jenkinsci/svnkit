package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnOldDiffGenerator implements ISvnDiffGenerator {

    private final ISVNDiffGenerator generator;
    private SvnTarget repositoryRoot;

    public SvnOldDiffGenerator(ISVNDiffGenerator generator) {
        this.generator = generator;
    }

    public void init(SvnTarget originalTarget1, SvnTarget originalTarget2) {
        generator.init(getDisplayPath(originalTarget2), getDisplayPath(originalTarget2));
    }

    public void setRepositoryRoot(SvnTarget repositoryRoot) {
        this.repositoryRoot = repositoryRoot;
    }

    public void setEncoding(String encoding) {
        generator.setEncoding(encoding);
    }

    public String getEncoding() {
        return generator.getEncoding();
    }

    public String getGlobalEncoding() {
        if (generator instanceof DefaultSVNDiffGenerator) {
            return ((DefaultSVNDiffGenerator) generator).getGlobalEncoding();
        }
        return null;
    }

    public void setEOL(byte[] eol) {
        generator.setEOL(eol);
    }

    public byte[] getEOL() {
        return generator.getEOL();
    }

    public void setForceEmpty(boolean forceEmpty) {
    }

    public void setForcedBinaryDiff(boolean forced) {
        generator.setForcedBinaryDiff(forced);
    }

    public void displayDeletedDirectory(SvnTarget displayPath, String revision1, String revision2, OutputStream outputStream) throws SVNException {
        generator.displayDeletedDirectory(getDisplayPath(displayPath), revision1, revision2);
    }

    public void displayAddedDirectory(SvnTarget displayPath, String revision1, String revision2, OutputStream outputStream) throws SVNException {
        generator.displayAddedDirectory(getDisplayPath(displayPath), revision1, revision2);
    }

    public void displayPropsChanged(SvnTarget displayPath, String revision1, String revision2, boolean dirWasAdded, SVNProperties originalProps, SVNProperties propChanges, OutputStream outputStream) throws SVNException {
        generator.displayPropDiff(getDisplayPath(displayPath), originalProps, propChanges, outputStream);
    }

    public void displayContentChanged(SvnTarget displayPath, File leftFile, File rightFile, String revision1, String revision2, String mimeType1, String mimeType2, SvnDiffCallback.OperationKind operation, File copyFromPath, OutputStream outputStream) throws SVNException {
        if (operation == SvnDiffCallback.OperationKind.Deleted && !generator.isDiffDeleted()
                || operation == SvnDiffCallback.OperationKind.Added && !generator.isDiffAdded()
                || operation == SvnDiffCallback.OperationKind.Copied && !generator.isDiffCopied()) {
            return;
        }
        generator.displayFileDiff(getDisplayPath(displayPath), leftFile, rightFile, revision1, revision2, mimeType1, mimeType2, outputStream);
    }

    private String getDisplayPath(SvnTarget displayPath) {
        return displayPath.getPathOrUrlString();
    }

    public boolean isForcedBinaryDiff() {
        return generator.isForcedBinaryDiff();
    }
}
