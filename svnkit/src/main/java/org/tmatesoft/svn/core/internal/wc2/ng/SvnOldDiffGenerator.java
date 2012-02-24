package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;

public class SvnOldDiffGenerator implements ISvnDiffGenerator {

    private final ISVNDiffGenerator generator;

    public SvnOldDiffGenerator(ISVNDiffGenerator generator) {
        this.generator = generator;
    }

    public void init(String anchorPath1, String anchorPath2) {
        generator.init(anchorPath1, anchorPath2);
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

    public void displayDeletedDirectory(String displayPath, String revision1, String revision2, OutputStream outputStream) throws SVNException {
        generator.displayDeletedDirectory(displayPath, revision1, revision2);
    }

    public void displayAddedDirectory(String displayPath, String revision1, String revision2, OutputStream outputStream) throws SVNException {
        generator.displayAddedDirectory(displayPath, revision1, revision2);
    }

    public void displayPropsChanged(String displayPath, String revision1, String revision2, boolean dirWasAdded, SVNProperties originalProps, SVNProperties propChanges, OutputStream outputStream) throws SVNException {
        generator.displayPropDiff(displayPath, originalProps, propChanges, outputStream);
    }

    public void displayContentChanged(String displayPath, File leftFile, File rightFile, String revision1, String revision2, String mimeType1, String mimeType2, SvnDiffCallback.OperationKind operation, File copyFromPath, OutputStream outputStream) throws SVNException {
        if (operation == SvnDiffCallback.OperationKind.Deleted && !generator.isDiffDeleted()
                || operation == SvnDiffCallback.OperationKind.Added && !generator.isDiffAdded()
                || operation == SvnDiffCallback.OperationKind.Copied && !generator.isDiffCopied()) {
            return;
        }
        generator.displayFileDiff(displayPath, leftFile, rightFile, revision1, revision2, mimeType1, mimeType2, outputStream);
    }

    public boolean isForcedBinaryDiff() {
        return generator.isForcedBinaryDiff();
    }
}
