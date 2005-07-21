/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;

import de.regnis.q.sequence.line.diff.QDiffGenerator;
import de.regnis.q.sequence.line.diff.QDiffManager;
import de.regnis.q.sequence.line.diff.QDiffUniGenerator;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class DefaultSVNDiffGenerator implements ISVNDiffGenerator {

    protected static final byte[] PROPERTIES_SEPARATOR = "___________________________________________________________________"
            .getBytes();

    protected static final byte[] HEADER_SEPARATOR = "==================================================================="
            .getBytes();

    protected static final byte[] EOL = SVNTranslator.getEOL("native");

    protected static final String WC_REVISION_LABEL = "(working copy)";

    protected static final InputStream EMPTY_FILE_IS = new ByteArrayInputStream(
            new byte[0]);

    private boolean myIsForcedBinaryDiff;

    private String myAnchorPath1;

    private String myEncoding;

    private boolean myIsDiffDeleted;

    private File myBasePath;

    public DefaultSVNDiffGenerator() {
        myIsDiffDeleted = true;
        init("", "");
    }

    public void init(String anchorPath1, String anchorPath2) {
        myAnchorPath1 = anchorPath1.replace(File.separatorChar, '/');
    }

    public void setBasePath(File basePath) {
        myBasePath = basePath;
    }

    public void setDiffDeleted(boolean isDiffDeleted) {
        myIsDiffDeleted = isDiffDeleted;
    }

    public boolean isDiffDeleted() {
        return myIsDiffDeleted;
    }

    public String getDisplayPath(File file) {
        String fullPath = file.getAbsolutePath().replace(File.separatorChar, '/');
        if (fullPath.equals(myAnchorPath1)) {
            return "";
        } else if (fullPath.startsWith(myAnchorPath1 + "/")) {
            fullPath = fullPath.substring(myAnchorPath1.length() + 1);
        }
        return fullPath;
    }

    public void setForcedBinaryDiff(boolean forced) {
        myIsForcedBinaryDiff = forced;
    }

    protected boolean isForcedBinaryDiff() {
        return myIsForcedBinaryDiff;
    }

    public void displayPropDiff(String path, Map baseProps, Map diff,
            OutputStream result) throws SVNException {
        if (myBasePath != null) {
            path = getDisplayPath(new File(myBasePath, path));
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            bos.write(EOL);
            bos.write(("Property changes on: " + path).getBytes(getEncoding()));
            bos.write(EOL);
            bos.write(PROPERTIES_SEPARATOR);
            bos.write(EOL);
            for (Iterator changedPropNames = diff.keySet().iterator(); changedPropNames
                    .hasNext();) {
                String name = (String) changedPropNames.next();
                String originalValue = baseProps != null ? (String) baseProps
                        .get(name) : null;
                String newValue = (String) diff.get(name);
                bos.write(("Name: " + name).getBytes(getEncoding()));
                bos.write(EOL);
                if (originalValue != null) {
                    bos.write("   - ".getBytes(getEncoding()));
                    bos.write(originalValue.getBytes(getEncoding()));
                    bos.write(EOL);
                }
                if (newValue != null) {
                    bos.write("   + ".getBytes(getEncoding()));
                    bos.write(newValue.getBytes(getEncoding()));
                    bos.write(EOL);
                }
            }
            bos.write(EOL);
        } catch (IOException e) {
            SVNErrorManager.error("svn: Failed to save diff data: " + e.getMessage());
        } finally {
            try {
                bos.close();
                bos.writeTo(result);
            } catch (IOException e) {
            }
        }
    }

    protected File getBasePath() {
        return myBasePath;
    }

    public void displayFileDiff(String path, File file1, File file2,
            String rev1, String rev2, String mimeType1, String mimeType2,
            OutputStream result) throws SVNException {
        if (myBasePath != null) {
            path = getDisplayPath(new File(myBasePath, path));
        }
        rev1 = rev1 == null ? WC_REVISION_LABEL : rev1;
        rev2 = rev2 == null ? WC_REVISION_LABEL : rev2;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            if (file2 == null && !isDiffDeleted()) {
                bos.write("Index: ".getBytes(getEncoding()));
                bos.write(path.getBytes(getEncoding()));
                bos.write(" (deleted)".getBytes(getEncoding()));
                bos.write(EOL);
                bos.write(HEADER_SEPARATOR);
                bos.write(EOL);
                bos.close();
                bos.writeTo(result);
                return;
            }
            bos.write("Index: ".getBytes(getEncoding()));
            bos.write(path.getBytes(getEncoding()));
            bos.write(EOL);
            bos.write(HEADER_SEPARATOR);
            bos.write(EOL);
        } catch (IOException e) {
            try {
                bos.close();
                bos.writeTo(result);
            } catch (IOException inner) {
            }
            SVNErrorManager.error("svn: Failed to save diff data: " + e.getMessage());
        }
        if (!isForcedBinaryDiff()
                && (SVNProperty.isBinaryMimeType(mimeType1) || SVNProperty.isBinaryMimeType(mimeType2))) {
            try {
                bos.write("Cannot display: file marked as binary type."
                        .getBytes(getEncoding()));
                bos.write(EOL);
                if (SVNProperty.isBinaryMimeType(mimeType1)
                        && !SVNProperty.isBinaryMimeType(mimeType2)) {
                    bos.write("svn:mime-type = ".getBytes(getEncoding()));
                    bos.write(mimeType1.getBytes(getEncoding()));
                    bos.write(EOL);
                } else if (!SVNProperty.isBinaryMimeType(mimeType1)
                        && SVNProperty.isBinaryMimeType(mimeType2)) {
                    bos.write("svn:mime-type = ".getBytes(getEncoding()));
                    bos.write(mimeType2.getBytes(getEncoding()));
                    bos.write(EOL);
                } else if (SVNProperty.isBinaryMimeType(mimeType1)
                        && SVNProperty.isBinaryMimeType(mimeType2)) {
                    if (mimeType1.equals(mimeType2)) {
                        bos.write("svn:mime-type = ".getBytes(getEncoding()));
                        bos.write(mimeType2.getBytes(getEncoding()));
                        bos.write(EOL);
                    } else {
                        bos.write("svn:mime-type = (".getBytes(getEncoding()));
                        bos.write(mimeType1.getBytes(getEncoding()));
                        bos.write(", ".getBytes(getEncoding()));
                        bos.write(mimeType2.getBytes(getEncoding()));
                        bos.write(")".getBytes(getEncoding()));
                        bos.write(EOL);
                    }
                }
            } catch (IOException e) {
                SVNErrorManager.error("svn: Failed to save diff data: " + e.getMessage());
            } finally {
                try {
                    bos.close();
                    bos.writeTo(result);
                } catch (IOException e) {
                }
            }
            return;
        }
        // put header fields.
        try {
            bos.write("--- ".getBytes(getEncoding()));
            bos.write(path.getBytes(getEncoding()));
            bos.write("\t".getBytes(getEncoding()));
            bos.write(rev1.getBytes(getEncoding()));
            bos.write(EOL);
            bos.write("+++ ".getBytes(getEncoding()));
            bos.write(path.getBytes(getEncoding()));
            bos.write("\t".getBytes(getEncoding()));
            bos.write(rev2.getBytes(getEncoding()));
            bos.write(EOL);
        } catch (IOException e) {
            try {
                bos.close();
                bos.writeTo(result);
            } catch (IOException inner) {
            }
            SVNErrorManager.error("svn: Failed to save diff data: " + e.getMessage());
        }
        InputStream is1 = null;
        InputStream is2 = null;
        try {
            is1 = file1 == null ? EMPTY_FILE_IS : SVNFileUtil
                    .openFileForReading(file1);
            is2 = file2 == null ? EMPTY_FILE_IS : SVNFileUtil
                    .openFileForReading(file2);

            QDiffUniGenerator.setup();
            Map generatorProperties = new HashMap();
            generatorProperties.put(QDiffUniGenerator.COMPARE_EOL_PROPERTY,
                    Boolean.TRUE.toString());
            QDiffGenerator generator = QDiffManager.getDiffGenerator(
                    QDiffUniGenerator.TYPE, generatorProperties);
            Writer writer = new OutputStreamWriter(bos, getEncoding());
            QDiffManager.generateTextDiff(is1, is2, getEncoding(), writer,
                    generator);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            SVNErrorManager.error("svn: Failed to save diff data: " + e.getMessage());
        } finally {
            SVNFileUtil.closeFile(is1);
            SVNFileUtil.closeFile(is2);
            try {
                bos.close();
                bos.writeTo(result);
            } catch (IOException inner) {
                //
            }
        }
    }

    public void setEncoding(String encoding) {
        myEncoding = encoding;
    }

    public String getEncoding() {
        if (myEncoding != null) {
            return myEncoding;
        }
        return System.getProperty("file.encoding");
    }

    public File createTempDirectory() throws SVNException {
        File dir = SVNFileUtil.createUniqueFile(new File("."), ".diff", ".tmp");
        boolean created = dir.mkdirs();
        if (!created) {
            SVNErrorManager.error("svn: cannot create temporary directory '"
                    + dir.getAbsolutePath() + "'");
        }
        return dir;
    }
}
