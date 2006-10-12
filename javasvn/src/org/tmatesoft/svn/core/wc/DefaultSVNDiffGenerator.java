/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.util.SVNDebugLog;

import de.regnis.q.sequence.line.diff.QDiffGenerator;
import de.regnis.q.sequence.line.diff.QDiffManager;
import de.regnis.q.sequence.line.diff.QDiffUniGenerator;

/**
 * <b>DefaultSVNDiffGenerator</b> is a default implementation of 
 * <b>ISVNDiffGenerator</b>.
 * <p>
 * By default, if there's no any specified implementation of the diff generator's
 * interface, JavaSVN uses this default implementation. To set a custom
 * diff driver use {@link SVNDiffClient#setDiffGenerator(ISVNDiffGenerator) setDiffGenerator()}.
 * 
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class DefaultSVNDiffGenerator implements ISVNDiffGenerator {

    protected static final byte[] PROPERTIES_SEPARATOR = "___________________________________________________________________".getBytes();
    protected static final byte[] HEADER_SEPARATOR = "===================================================================".getBytes();
    protected static final byte[] EOL = SVNTranslator.getEOL("native");
    protected static final String WC_REVISION_LABEL = "(working copy)";
    protected static final InputStream EMPTY_FILE_IS = SVNFileUtil.DUMMY_IN;

    private boolean myIsForcedBinaryDiff;
    private String myAnchorPath1;
    private String myAnchorPath2;
    
    private String myEncoding;
    private boolean myIsDiffDeleted;
    private File myBasePath;
    private boolean myIsDiffUnversioned;
    
    /**
     * Constructs a <b>DefaultSVNDiffGenerator</b>.
     *
     */
    public DefaultSVNDiffGenerator() {
        myIsDiffDeleted = true;
        myAnchorPath1 = "";
        myAnchorPath2 = "";
    }

    public void init(String anchorPath1, String anchorPath2) {
        myAnchorPath1 = anchorPath1.replace(File.separatorChar, '/');
        myAnchorPath2 = anchorPath2.replace(File.separatorChar, '/');
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

    protected String getDisplayPath(String path) {
        if (myBasePath == null) {
            return path;
        }
        if (path == null) {
            path = "";
        }
        if (path.indexOf("://") > 0) {
            return path;
        }
        // treat as file path.
        String basePath = myBasePath.getAbsolutePath().replace(File.separatorChar, '/');
        if (path.equals(basePath)) {
            return ".";
        }
        if (path.startsWith(basePath + "/")) {
            path = path.substring(basePath.length() + 1);
            if (path.startsWith("./")) {
                path = path.substring("./".length());
            }
        }
        return path;
    }

    public void setForcedBinaryDiff(boolean forced) {
        myIsForcedBinaryDiff = forced;
    }

    protected boolean isForcedBinaryDiff() {
        return myIsForcedBinaryDiff;
    }

    public void displayPropDiff(String path, Map baseProps, Map diff, OutputStream result) throws SVNException {
        baseProps = baseProps != null ? baseProps : Collections.EMPTY_MAP;
        diff = diff != null ? diff : Collections.EMPTY_MAP;
        SVNDebugLog.getDefaultLog().info("propdiff, was: " + baseProps+ ", diff: " + diff);
        for (Iterator changedPropNames = diff.keySet().iterator(); changedPropNames.hasNext();) {
            String name = (String) changedPropNames.next();
            String originalValue = (String) baseProps.get(name);
            String newValue = (String) diff.get(name);
            if ((originalValue != null && originalValue.equals(newValue)) || originalValue == newValue) {
                changedPropNames.remove();
            }
        }
        if (diff.isEmpty()) {
            return;
        }
        path = getDisplayPath(path);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        SVNDebugLog.getDefaultLog().info("propdiff, was: " + baseProps+ ", diff: " + diff);
        SVNDebugLog.getDefaultLog().info(new Exception());
        diff = new TreeMap(diff);
        try {
            bos.write(EOL);
            bos.write(("Property changes on: " + path).getBytes(getEncoding()));
            bos.write(EOL);
            bos.write(PROPERTIES_SEPARATOR);
            bos.write(EOL);
            for (Iterator changedPropNames = diff.keySet().iterator(); changedPropNames.hasNext();) {
                String name = (String) changedPropNames.next();
                String originalValue = baseProps != null ? (String) baseProps.get(name) : null;
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
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            try {
                bos.close();
                bos.writeTo(result);
                SVNDebugLog.getDefaultLog().info(new String(bos.toByteArray()));
            } catch (IOException e) {
            }
        }
    }

    protected File getBasePath() {
        return myBasePath;
    }

    public void displayFileDiff(String path, File file1, File file2,
            String rev1, String rev2, String mimeType1, String mimeType2, OutputStream result) throws SVNException {
        path = getDisplayPath(path);
        int i = 0;
        for(; i < myAnchorPath1.length() && i < myAnchorPath2.length() &&
            myAnchorPath1.charAt(i) == myAnchorPath2.charAt(i); i++) {}
        if (i < myAnchorPath1.length() || i < myAnchorPath2.length()) {
            if (i == myAnchorPath1.length()) {
                i = myAnchorPath1.length() - 1;
            }
            for(; i > 0 && myAnchorPath1.charAt(i) != '/'; i--) {}
        }
        String p1 = myAnchorPath1.substring(i) ;
        String p2 = myAnchorPath2.substring(i);
        
        if (p1.length() == 0) {
            p1 = path;
        } else if (p1.charAt(0) == '/') {
            p1 = path + "\t(..." + p1 + ")";
        } else {
            p1 = path + "\t(.../" + p1 + ")";
        }
        if (p2.length() == 0) {
            p2 = path;
        } else if (p2.charAt(0) == '/') {
            p2 = path + "\t(..." + p2 + ")";
        } else {
            p2 = path + "\t(.../" + p2 + ")";
        }
        
        // if anchor1 is the same as anchor2 just use path.        
        // if anchor1 differs from anchor2 =>
        // condence anchors (get common root and remainings).
        
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
                SVNDebugLog.getDefaultLog().info(new String(bos.toByteArray()));
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
                SVNDebugLog.getDefaultLog().info(new String(bos.toByteArray()));
            } catch (IOException inner) {
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        }
        if (!isForcedBinaryDiff() && (SVNProperty.isBinaryMimeType(mimeType1) || SVNProperty.isBinaryMimeType(mimeType2))) {
            try {
                bos.write("Cannot display: file marked as binary type.".getBytes(getEncoding()));
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
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                SVNErrorManager.error(err, e);
            } finally {
                try {
                    bos.close();
                    bos.writeTo(result);
                    SVNDebugLog.getDefaultLog().info(new String(bos.toByteArray()));
                } catch (IOException e) {
                }
            }
            return;
        }
        if (file1 == file2 && file1 == null) {
            try {
                bos.close();
                bos.writeTo(result);
                SVNDebugLog.getDefaultLog().info(new String(bos.toByteArray()));
            } catch (IOException e) {
            }
            return;
        }
        // put header fields.
        try {
            bos.write("--- ".getBytes(getEncoding()));
            bos.write(p1.getBytes(getEncoding()));
            bos.write("\t".getBytes(getEncoding()));
            bos.write(rev1.getBytes(getEncoding()));
            bos.write(EOL);
            bos.write("+++ ".getBytes(getEncoding()));
            bos.write(p2.getBytes(getEncoding()));
            bos.write("\t".getBytes(getEncoding()));
            bos.write(rev2.getBytes(getEncoding()));
            bos.write(EOL);
        } catch (IOException e) {
            try {
                bos.close();
                bos.writeTo(result);
                SVNDebugLog.getDefaultLog().info(new String(bos.toByteArray()));
            } catch (IOException inner) {
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        }
        try {
            bos.close();
            bos.writeTo(result);
            SVNDebugLog.getDefaultLog().info(new String(bos.toByteArray()));
        } catch (IOException inner) {
            //
        }

        InputStream is1 = null;
        InputStream is2 = null;
        try {
            is1 = file1 == null ? EMPTY_FILE_IS : SVNFileUtil.openFileForReading(file1);
            is2 = file2 == null ? EMPTY_FILE_IS : SVNFileUtil.openFileForReading(file2);

            QDiffUniGenerator.setup();
            QDiffGenerator generator = QDiffManager.getDiffGenerator(QDiffUniGenerator.TYPE, null);
            Writer writer = new OutputStreamWriter(result, getEncoding());
            QDiffManager.generateTextDiff(is1, is2, getEncoding(), writer, generator);
            writer.flush();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(is1);
            SVNFileUtil.closeFile(is2);
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
        return SVNFileUtil.createTempDirectory("diff");
    }

    /**
     * Says if unversioned files are also diffed or ignored.
     * 
     * <p>
     * By default unversioned files are ignored. 
     * 
     * @return <span class="javakeyword">true</span> if diffed, 
     *         <span class="javakeyword">false</span> if ignored  
     * @see    #setDiffUnversioned(boolean)
     * 
     */

    public boolean isDiffUnversioned() {
        return myIsDiffUnversioned;
    }

    /**
     * Includes or not unversioned files into diff processing. 
     * 
     * <p>
     * If a diff operation is invoked on  a versioned directory and 
     * <code>diffUnversioned</code> is <span class="javakeyword">true</span> 
     * then all unversioned files that may be met in the directory will 
     * be processed as added. Otherwise if <code>diffUnversioned</code> 
     * is <span class="javakeyword">false</span> such files are ignored. 
     * 
     * <p>
     * By default unversioned files are ignored.
     * 
     * @param diffUnversioned controls whether to diff unversioned files 
     *                        or not 
     * @see                   #isDiffUnversioned()
     */
    public void setDiffUnversioned(boolean diffUnversioned) {
        myIsDiffUnversioned = diffUnversioned;
    }

    public void displayDeletedDirectory(String path, String rev1, String rev2) throws SVNException {
        // not implemented.
    }

    public void displayAddedDirectory(String path, String rev1, String rev2) throws SVNException {
        // not implemented.
    }
}
