package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNCharsetOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnDiffCallback implements ISvnDiffCallback {

    private ISVNDiffGenerator myGenerator;
    private OutputStream myResult;
    private long myRevision2;
    private long myRevision1;

    public SvnDiffCallback(ISVNDiffGenerator generator, long rev1, long rev2, OutputStream result) {
        myGenerator = generator;
        myResult = result;
        myRevision1 = rev1;
        myRevision2 = rev2;
    }

    public void fileOpened(SvnDiffCallbackResult result, File path, long revision) throws SVNException {
    }

    public void fileChanged(SvnDiffCallbackResult result, File path, File leftFile, File rightFile, long rev1, long rev2, String mimeType1, String mimeType2, SVNProperties propChanges, SVNProperties originalProperties) throws SVNException {
        if (leftFile != null) {
            displayFileDiff(path, leftFile, rightFile, rev1, rev2, mimeType1, mimeType2, originalProperties, propChanges);
        }
        if (propChanges != null && !propChanges.isEmpty()) {
            propertiesChanged(path, originalProperties, propChanges);
        }
    }

    public void fileAdded(SvnDiffCallbackResult result, File path, File leftFile, File rightFile, long rev1, long rev2, String mimeType1, String mimeType2, File copyFromPath, long copyFromRevision, SVNProperties propChanges, SVNProperties originalProperties) throws SVNException {
        if (!myGenerator.isDiffAdded()) {
            return;
        }
        if (rightFile != null) {
            displayFileDiff(path, null, rightFile, rev1, rev2, mimeType1, mimeType2, originalProperties, propChanges);
        }
        if (propChanges != null && !propChanges.isEmpty()) {
            propertiesChanged(path, originalProperties, propChanges);
        }
    }

    public void fileDeleted(SvnDiffCallbackResult result, File path, File leftFile, File rightFile, String mimeType1, String mimeType2, SVNProperties originalProperties) throws SVNException {
        if (!myGenerator.isDiffDeleted()) {
            return;
        }
        if (leftFile != null) {
            displayFileDiff(path, leftFile, rightFile, myRevision1, myRevision2, mimeType1, mimeType2, originalProperties, null);
        }
    }

    public void dirDeleted(SvnDiffCallbackResult result, File path) throws SVNException {
        myGenerator.displayDeletedDirectory(getDisplayPath(path), getRevision(myRevision1), getRevision(myRevision2));
    }

    public void dirOpened(SvnDiffCallbackResult result, File path, long revision) throws SVNException {
    }

    public void dirAdded(SvnDiffCallbackResult result, File path, long revision, String copyFromPath, long copyFromRevision) throws SVNException {
        myGenerator.displayAddedDirectory(getDisplayPath(path), getRevision(myRevision1), getRevision(revision));
    }

    public void dirPropsChanged(SvnDiffCallbackResult result, File path, boolean isAdded, SVNProperties propChanges, SVNProperties originalProperties) throws SVNException {
        originalProperties = originalProperties == null ? new SVNProperties() : originalProperties;
        propChanges = propChanges == null ? new SVNProperties() : propChanges;
        SVNProperties regularDiff = getRegularProperties(propChanges);
        if (regularDiff == null || regularDiff.isEmpty()) {
            return;
        }
        myGenerator.displayPropDiff(getDisplayPath(path), originalProperties, regularDiff, myResult);
    }

    public void dirClosed(SvnDiffCallbackResult result, File path, boolean isAdded) throws SVNException {
    }

    protected String getDisplayPath(File path) {
       return path.getPath().replace(File.separatorChar, '/');
    }

    private String getRevision(long revision) {
        if (revision >= 0) {
            return "(revision " + revision + ")";
        }
        return "(working copy)";
    }

    private void displayFileDiff(File path, File file1, File file2, long revision1, long revision2, String mimeType1, String mimeType2, SVNProperties originalProperties, SVNProperties diff) throws SVNException {
        boolean resetEncoding = false;
        OutputStream result = myResult;
        String encoding = defineEncoding(originalProperties, diff);
        if (encoding != null) {
            myGenerator.setEncoding(encoding);
            resetEncoding = true;
        } else {
            String conversionEncoding = defineConversionEncoding(originalProperties, diff);
            if (conversionEncoding != null) {
                resetEncoding = adjustDiffGenerator("UTF-8");
                result = new SVNCharsetOutputStream(result, Charset.forName("UTF-8"), Charset.forName(conversionEncoding), CodingErrorAction.IGNORE, CodingErrorAction.IGNORE);
            }
        }
        try {
            myGenerator.displayFileDiff(getDisplayPath(path), file1, file2, getRevision(revision1), getRevision(revision2), mimeType1, mimeType2, result);
        } finally {
            if (resetEncoding) {
                myGenerator.setEncoding(null);
                myGenerator.setEOL(null);
            }
            if (result instanceof SVNCharsetOutputStream) {
                try {
                    result.flush();
                } catch (IOException e) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e, SVNLogType.WC);
                }
            }
        }
    }

    private String defineEncoding(SVNProperties properties, SVNProperties diff) {
        if (myGenerator instanceof DefaultSVNDiffGenerator) {
            DefaultSVNDiffGenerator defaultGenerator = (DefaultSVNDiffGenerator) myGenerator;
            if (defaultGenerator.hasEncoding()) {
                return null;
            }

            String originalEncoding = getCharsetByMimeType(properties, defaultGenerator);
            if (originalEncoding != null) {
                return originalEncoding;
            }

            String changedEncoding = getCharsetByMimeType(diff, defaultGenerator);
            if (changedEncoding != null) {
                return changedEncoding;
            }
        }
        return null;
    }

    private String getCharsetByMimeType(SVNProperties properties, DefaultSVNDiffGenerator generator) {
        if (properties == null) {
            return null;
        }
        String mimeType = properties.getStringValue(SVNProperty.MIME_TYPE);
        String charset = SVNPropertiesManager.determineEncodingByMimeType(mimeType);
        return getCharset(charset, generator, false);
    }

    private String getCharset(SVNProperties properties, DefaultSVNDiffGenerator generator) {
        if (properties == null) {
            return null;
        }
        String charset = properties.getStringValue(SVNProperty.CHARSET);
        return getCharset(charset, generator, true);
    }

    private String getCharset(String charset, DefaultSVNDiffGenerator generator, boolean allowNative) {
        if (charset == null) {
            return null;
        }
        if (allowNative && SVNProperty.NATIVE.equals(charset)) {
            return generator.getEncoding();
        }
        if (Charset.isSupported(charset)) {
            return charset;
        }
        return null;
    }

    private String defineConversionEncoding(SVNProperties properties, SVNProperties diff) {
        if (myGenerator instanceof DefaultSVNDiffGenerator) {
            DefaultSVNDiffGenerator defaultGenerator = (DefaultSVNDiffGenerator) myGenerator;
            if (defaultGenerator.hasEncoding()) {
                return null;
            }
            String originalCharset = getCharset(properties, defaultGenerator);
            if (originalCharset != null) {
                return originalCharset;
            }

            String changedCharset = getCharset(diff, defaultGenerator);
            if (changedCharset != null) {
                return changedCharset;
            }

            String globalEncoding = getCharset(defaultGenerator.getGlobalEncoding(), defaultGenerator, false);
            if (globalEncoding != null) {
                return globalEncoding;
            }
        }
        return null;
    }

    public void propertiesChanged(File path, SVNProperties originalProperties, SVNProperties diff) throws SVNException {
        originalProperties = originalProperties == null ? new SVNProperties() : originalProperties;
        diff = diff == null ? new SVNProperties() : diff;
        SVNProperties regularDiff = getRegularProperties(diff);
        if (diff == null || diff.isEmpty()) {
            return;
        }
        myGenerator.displayPropDiff(getDisplayPath(path), originalProperties, regularDiff, myResult);
    }

    private boolean adjustDiffGenerator(String charset) {
        if (myGenerator instanceof DefaultSVNDiffGenerator) {
            DefaultSVNDiffGenerator generator = (DefaultSVNDiffGenerator) myGenerator;
            boolean encodingAdjusted = false;
            if (!generator.hasEncoding()) {
                generator.setEncoding(charset);
                encodingAdjusted = true;
            }
            if (!generator.hasEOL()) {
                byte[] eol;
                String eolString = System.getProperty("line.separator");
                try {
                    eol = eolString.getBytes(charset);
                } catch (UnsupportedEncodingException e) {
                    eol = eolString.getBytes();
                }
                generator.setEOL(eol);
            }
            return encodingAdjusted;
        }
        return false;
    }

    private static SVNProperties getRegularProperties(SVNProperties propChanges) {
        if (propChanges == null) {
            return null;
        }
        final SVNProperties regularPropertiesChanges = new SVNProperties();
        SvnNgPropertiesManager.categorizeProperties(propChanges, regularPropertiesChanges, null, null);
        return regularPropertiesChanges;
    }
}
