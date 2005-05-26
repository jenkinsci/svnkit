/*
 * Created on 26.05.2005
 */
package org.tmatesoft.svn.core.wc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;

import de.regnis.q.sequence.line.diff.QDiffGenerator;
import de.regnis.q.sequence.line.diff.QDiffManager;
import de.regnis.q.sequence.line.diff.QDiffUniGenerator;

public class DefaultSVNDiffGenerator implements ISVNDiffGenerator {

    protected static final byte[] PROPERTIES_SEPARATOR = "___________________________________________________________________".getBytes();
    protected static final byte[] HEADER_SEPARATOR =   "===================================================================".getBytes();
    protected static final byte[] EOL = SVNTranslator.getEOL("native");
    protected static final String WC_REVISION_LABEL = "(working copy)";

    private boolean myIsForcedBinaryDiff;
    private String myAnchorPath1;
    private String myAnchorPath2;

    public void init(String anchorPath1, String anchorPath2) {
        myAnchorPath1 = anchorPath1;
        myAnchorPath2 = anchorPath2;
    }

    public String getDisplayPath(File file) {
        return file.getAbsolutePath();
    }
    
    public void setForcedBinaryDiff(boolean forced) {
        myIsForcedBinaryDiff = forced;
    }
    
    protected boolean isForcedBinaryDiff() {
        return myIsForcedBinaryDiff;
    }

    public void displayPropDiff(String path, Map baseProps, Map diff, OutputStream result) throws SVNException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            bos.write(EOL);
            bos.write(("Property changes on: " + path).getBytes("UTF-8"));
            bos.write(EOL);
            bos.write(PROPERTIES_SEPARATOR);
            bos.write(EOL);
            for (Iterator changedPropNames = diff.keySet().iterator(); changedPropNames.hasNext();) {
                String name = (String) changedPropNames.next();
                String originalValue = baseProps != null ? (String) baseProps.get(name) : null;
                String newValue = (String) diff.get(name);
                bos.write(("Name: " + name).getBytes("UTF-8"));
                bos.write(EOL);
                if (originalValue != null) {
                    bos.write("   - ".getBytes("UTF-8"));
                    bos.write(originalValue.getBytes("UTF-8"));
                    bos.write(EOL);
                }
                if (newValue != null) {
                    bos.write("   + ".getBytes("UTF-8"));
                    bos.write(newValue.getBytes("UTF-8"));
                    bos.write(EOL);
                }
            }
            bos.write(EOL);
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        } finally {
            try {
                bos.close();
                bos.writeTo(result);
            } catch (IOException e) {
            }
        }
    }

    public void displayFileDiff(String path, File file1, File file2,
            String rev1, String rev2, String mimeType1, String mimeType2,
            OutputStream result) throws SVNException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            bos.write("Index: ".getBytes("UTF-8"));
            bos.write(path.getBytes("UTF-8"));
            bos.write(EOL);
            bos.write(HEADER_SEPARATOR);
            bos.write(EOL);
        } catch (IOException e) {
            try {
                bos.close();
                bos.writeTo(result);
                DebugLog.log(bos.toString());
            } catch (IOException inner) {
            }
            SVNErrorManager.error(0, e);
        }
        if (!isForcedBinaryDiff() && (isBinary(mimeType1) || isBinary(mimeType2))) {
            try {
                bos.write("Cannot display: file marked as binary type.".getBytes("UTF-8"));
                bos.write(EOL);
                if (isBinary(mimeType1) && !isBinary(mimeType2)) {
                    bos.write("svn:mime-type = ".getBytes("UTF-8"));
                    bos.write(mimeType1.getBytes("UTF-8"));
                    bos.write(EOL);
                } else if (!isBinary(mimeType1) && isBinary(mimeType2)) {
                    bos.write("svn:mime-type = ".getBytes("UTF-8"));
                    bos.write(mimeType2.getBytes("UTF-8"));
                    bos.write(EOL);
                } else if (isBinary(mimeType1) && isBinary(mimeType2)) {
                    if (mimeType1.equals(mimeType2)) {
                        bos.write("svn:mime-type = ".getBytes("UTF-8"));
                        bos.write(mimeType2.getBytes("UTF-8"));
                        bos.write(EOL);
                    } else {
                        bos.write("svn:mime-type = (".getBytes("UTF-8"));
                        bos.write(mimeType1.getBytes("UTF-8"));
                        bos.write(", ".getBytes("UTF-8"));
                        bos.write(mimeType2.getBytes("UTF-8"));
                        bos.write(")".getBytes("UTF-8"));
                        bos.write(EOL);
                    }   
                }
            } catch (IOException e) { 
                SVNErrorManager.error(0, e);
            } finally {
                try {
                    bos.close();
                    bos.writeTo(result);
                    DebugLog.log(bos.toString());
                } catch (IOException e) {
                }
            }
            return;
        }
        // put header fields.
        try {
            bos.write("--- ".getBytes("UTF-8"));
            bos.write(path.getBytes("UTF-8"));
            bos.write("\t".getBytes("UTF-8"));
            bos.write(rev1.getBytes("UTF-8"));
            bos.write(EOL);
            bos.write("+++ ".getBytes("UTF-8"));
            bos.write(path.getBytes("UTF-8"));
            bos.write("\t".getBytes("UTF-8"));
            bos.write(rev2.getBytes("UTF-8"));
            bos.write(EOL);
        } catch (IOException e) {
            try {
                bos.close();
                bos.writeTo(result);
                DebugLog.log(bos.toString());
            } catch (IOException inner) {
            }
            SVNErrorManager.error(0, e);
        }
        InputStream is1 = null;
        InputStream is2 = null;
        try {
            is1 = new FileInputStream(file1);
            is2 = new FileInputStream(file2);
            
            QDiffUniGenerator.setup();
            QDiffGenerator generator = QDiffManager.getDiffGenerator(QDiffUniGenerator.TYPE, new HashMap());
            Writer writer = new OutputStreamWriter(bos, "UTF-8");
            QDiffManager.generateTextDiff(is1, is2, "UTF-8", writer, generator);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        } finally {
            if (is1 != null) {
                try {
                    is1.close();
                } catch (IOException e) {
                }
            }
            if (is2 != null) {
                try {
                    is2.close();
                } catch (IOException e) {
                }
            }
            try {
                bos.close();
                DebugLog.log(bos.toString());
                bos.writeTo(result);
            } catch (IOException inner) {
            }
        }
    }

    protected static boolean isBinary(String mimetype) {
        return mimetype != null && !mimetype.startsWith("text/");
    }

}
