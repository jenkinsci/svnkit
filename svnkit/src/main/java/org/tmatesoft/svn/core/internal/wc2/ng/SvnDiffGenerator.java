package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnDiffGenerator implements ISvnDiffGenerator {

    protected static final String WC_REVISION_LABEL = "(working copy)";
    protected static final String PROPERTIES_SEPARATOR = "___________________________________________________________________";
    protected static final String HEADER_SEPARATOR = "===================================================================";

    private String path1;
    private String path2;
    private String encoding;
    private byte[] eol;
    private boolean useGitFormat;

    private boolean diffDeleted;

    public void setPath1(String path1) {
        this.path1 = path1;
    }

    public void setPath2(String path2) {
        this.path2 = path2;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getEncoding() {
        return encoding;
    }

    public String getGlobalEncoding() {
        return null; //TODO
    }

    public void setEOL(byte[] eol) {
        this.eol = eol;
    }

    public byte[] getEOL() {
        return eol;
    }

    public void displayDeletedDirectory(String displayPath, String revision1, String revision2, OutputStream outputStream) throws SVNException {
    }

    public void displayAddedDirectory(String displayPath, String revision1, String revision2, OutputStream outputStream) throws SVNException {
    }

    public void displayPropsChanged(String displayPath, String revision1, String revision2, boolean dirWasAdded, SVNProperties originalProps, SVNProperties propChanges, boolean showDiffHeader, OutputStream outputStream) throws SVNException {
        ensureEncodingAndEOLSet();

        if (useGitFormat) {
            //TODO adjust paths to point to repository root
        }

        if (displayPath == null || displayPath.length() == 0) {
            displayPath = ".";
        }

        if (showDiffHeader) {
            String commonAncestor = SVNPathUtil.getCommonPathAncestor(path1, path2);
            if (commonAncestor == null) {
                commonAncestor = "";
            }

            String adjustedPathWithLabel1 = getAdjustedPathWithLabel(displayPath, path1, revision1, commonAncestor);
            String adjustedPathWithLabel2 = getAdjustedPathWithLabel(displayPath, path2, revision2, commonAncestor);

            if (displayHeader(outputStream, displayPath, false)) {
                return;
            }

            if (useGitFormat) {
                printGitDiffHeader(adjustedPathWithLabel1, adjustedPathWithLabel2, SvnDiffCallback.OperationKind.Modified, path1, path2, revision1, revision2, null);
            }

            displayHeaderFields(outputStream, adjustedPathWithLabel1, adjustedPathWithLabel2);
        }

        printPropertyChangesOn(useGitFormat ? path1 : displayPath, outputStream);

        displayPropDiffValues(outputStream, propChanges, originalProps);
    }

    private void ensureEncodingAndEOLSet() {
        if (getEOL() == null) {
            setEOL(SVNProperty.EOL_LF_BYTES);
        }
        if (getEncoding() == null) {
            setEncoding("UTF-8");
        }
    }

    private void displayPropDiffValues(OutputStream outputStream, SVNProperties diff, SVNProperties baseProps) throws SVNException {
        for (Iterator changedPropNames = diff.nameSet().iterator(); changedPropNames.hasNext();) {
            String name = (String) changedPropNames.next();
            SVNPropertyValue originalValue = baseProps != null ? baseProps.getSVNPropertyValue(name) : null;
            SVNPropertyValue newValue = diff.getSVNPropertyValue(name);
            String headerFormat = null;

            if (originalValue == null) {
                headerFormat = "Added: ";
            } else if (newValue == null) {
                headerFormat = "Deleted: ";
            } else {
                headerFormat = "Modified: ";
            }

            try {
            outputStream.write((headerFormat + name).getBytes(getEncoding()));
            outputStream.write(getEOL());
            if (SVNProperty.MERGE_INFO.equals(name)) {
                displayMergeInfoDiff(outputStream, originalValue == null ? null : originalValue.getString(), newValue == null ? null : newValue.getString());
                continue;
            }
            if (originalValue != null) {
                outputStream.write("   - ".getBytes(getEncoding()));
                outputStream.write(getPropertyAsBytes(originalValue, getEncoding()));
                outputStream.write(getEOL());
            }
            if (newValue != null) {
                outputStream.write("   + ".getBytes(getEncoding()));
                outputStream.write(getPropertyAsBytes(newValue, getEncoding()));
                outputStream.write(getEOL());
            }
                outputStream.write(getEOL());
            } catch (IOException e) {
                wrapException(e);
            }
        }

    }

    public void displayContentChanged(String displayPath, File leftFile, File rightFile, String revision1, String revision2, String mimeType1, String mimeType2, SvnDiffCallback.OperationKind operation, File copyFromPath, OutputStream outputStream) throws SVNException {
    }

    private void printGitDiffHeader(String label1, String label2, SvnDiffCallback.OperationKind operationKind, String path1, String path2, String revision1, String revision2, String copyFromPath) {
        throw new UnsupportedOperationException();//TODO
    }

    private String getAdjustedPathWithLabel(String displayPath, String path, String revision, String commonAncestor) {
        String adjustedPath = getAdjustedPath(displayPath, path, commonAncestor);
        return getLabel(adjustedPath, revision);
    }

    private String getAdjustedPath(String displayPath, String path1, String commonAncestor) {
        String adjustedPath = SVNPathUtil.getRelativePath(commonAncestor, path1);

        if (adjustedPath == null || adjustedPath.length() == 0) {
            adjustedPath = displayPath;
        } else if (adjustedPath.charAt(0) == '/') {
            adjustedPath = displayPath + "\t(..." + adjustedPath + ")";
        } else {
            adjustedPath = displayPath + "\t(.../" + adjustedPath + ")";
        }
        return adjustedPath;
        //TODO: respect relativeToDir
    }

    protected String getLabel(String path, String revToken) {
        revToken = revToken == null ? WC_REVISION_LABEL : revToken;
        return path + "\t" + revToken;
    }

    protected boolean displayHeader(OutputStream os, String path, boolean deleted) throws SVNException {
        try {
            if (deleted && !isDiffDeleted()) {
                os.write("Index: ".getBytes(getEncoding()));
                os.write(path.getBytes(getEncoding()));
                os.write(" (deleted)".getBytes(getEncoding()));
                os.write(getEOL());
                os.write(HEADER_SEPARATOR.getBytes(getEncoding()));
                os.write(getEOL());
                return true;
            }
            os.write("Index: ".getBytes(getEncoding()));
            os.write(path.getBytes(getEncoding()));
            os.write(getEOL());
            os.write(HEADER_SEPARATOR.getBytes(getEncoding()));
            os.write(getEOL());
            return false;
        } catch (IOException e) {
            wrapException(e);
        }
        return false;
    }

    protected void displayHeaderFields(OutputStream os, String label1, String label2) throws SVNException {
        try {
        os.write("--- ".getBytes(getEncoding()));
        os.write(label1.getBytes(getEncoding()));
        os.write(getEOL());
        os.write("+++ ".getBytes(getEncoding()));
        os.write(label2.getBytes(getEncoding()));
        os.write(getEOL());
        } catch (IOException e) {
            wrapException(e);
        }
    }

    private void printPropertyChangesOn(String path, OutputStream outputStream) throws SVNException {
        try {
        outputStream.write(getEOL());
        outputStream.write(("Property changes on: " + (useLocalFileSeparatorChar() ? path.replace('/', File.separatorChar) : path)).getBytes(getEncoding()));
        outputStream.write(getEOL());
        outputStream.write(PROPERTIES_SEPARATOR.getBytes(getEncoding()));
        outputStream.write(getEOL());
        } catch (IOException e) {
            wrapException(e);
        }
    }

    private byte[] getPropertyAsBytes(SVNPropertyValue value, String encoding){
        if (value == null){
            return null;
        }
        if (value.isString()){
            try {
                return value.getString().getBytes(encoding);
            } catch (UnsupportedEncodingException e) {
                return value.getString().getBytes();
            }
        }
        return value.getBytes();
    }

    private void displayMergeInfoDiff(OutputStream baos, String oldValue, String newValue) throws SVNException, IOException {
        Map oldMergeInfo = null;
        Map newMergeInfo = null;
        if (oldValue != null) {
            oldMergeInfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(oldValue), null);
        }
        if (newValue != null) {
            newMergeInfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(newValue), null);
        }

        Map deleted = new TreeMap();
        Map added = new TreeMap();
        SVNMergeInfoUtil.diffMergeInfo(deleted, added, oldMergeInfo, newMergeInfo, true);

        for (Iterator paths = deleted.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) deleted.get(path);
            baos.write(("   Reverse-merged " + path + ":r").getBytes(getEncoding()));
            baos.write(rangeList.toString().getBytes(getEncoding()));
            baos.write(getEOL());
        }

        for (Iterator paths = added.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) added.get(path);
            baos.write(("   Merged " + path + ":r").getBytes(getEncoding()));
            baos.write(rangeList.toString().getBytes(getEncoding()));
            baos.write(getEOL());
        }
    }

    private boolean useLocalFileSeparatorChar() {
        return true;
    }

    public boolean isDiffDeleted() {
        return diffDeleted;
    }

    private void wrapException(IOException e) throws SVNException {
        SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, e);
        SVNErrorManager.error(errorMessage, e, SVNLogType.WC);
    }
}
