/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNCharsetOutputStream;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;


/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class SVNDiffCallback extends AbstractDiffCallback {

    private ISVNDiffGenerator myGenerator;
    private OutputStream myResult;
    private long myRevision2;
    private long myRevision1;

    private static final SVNStatusType[] EMPTY_STATUS = {SVNStatusType.UNKNOWN, SVNStatusType.UNKNOWN};

    public SVNDiffCallback(SVNAdminArea adminArea, ISVNDiffGenerator generator, long rev1, long rev2, OutputStream result) {
        super(adminArea);
        myGenerator = generator;
        myResult = result;
        myRevision1 = rev1;
        myRevision2 = rev2;
    }

    public File createTempDirectory() throws SVNException {
        return myGenerator.createTempDirectory();
    }

    public boolean isDiffUnversioned() {
        return myGenerator.isDiffUnversioned();
    }

    public boolean isDiffCopiedAsAdded() {
        return myGenerator.isDiffCopied();
    }

    public SVNStatusType directoryAdded(String path, long revision, boolean[] isTreeConflicted) throws SVNException {
        setIsConflicted(isTreeConflicted, false);
        myGenerator.displayAddedDirectory(getDisplayPath(path), getRevision(myRevision1), getRevision(revision));
        return SVNStatusType.UNKNOWN;
    }

    public SVNStatusType directoryDeleted(String path) throws SVNException {
        myGenerator.displayDeletedDirectory(getDisplayPath(path), getRevision(myRevision1), getRevision(myRevision2));
        return SVNStatusType.UNKNOWN;
    }

    public SVNStatusType[] fileAdded(String path, File file1, File file2, long revision1, long revision2, String mimeType1, 
            String mimeType2, SVNProperties originalProperties, SVNProperties diff, boolean[] isTreeConflicted) throws SVNException {
        if (file2 != null) {
            displayFileDiff(path, null, file2, revision1, revision2, mimeType1, mimeType2, originalProperties, diff);
        }
        if (diff != null && !diff.isEmpty()) {
            propertiesChanged(path, originalProperties, diff, null);
        }
        return EMPTY_STATUS;
    }

    public SVNStatusType[] fileChanged(String path, File file1, File file2, long revision1, long revision2, String mimeType1,
            String mimeType2, SVNProperties originalProperties, SVNProperties diff, boolean[] isTreeConflicted) throws SVNException {
        if (file1 != null) {
            displayFileDiff(path, file1, file2, revision1, revision2, mimeType1, mimeType2, originalProperties, diff);
        }
        if (diff != null && !diff.isEmpty()) {
            propertiesChanged(path, originalProperties, diff, null);
        }
        return EMPTY_STATUS;
    }

    public SVNStatusType fileDeleted(String path, File file1, File file2, String mimeType1, String mimeType2, SVNProperties originalProperties,
            boolean[] isTreeConflicted) throws SVNException {
        if (file1 != null) {
            displayFileDiff(path, file1, file2, myRevision1, myRevision2, mimeType1, mimeType2, originalProperties, null);
        }
        return SVNStatusType.UNKNOWN;
    }

    private void displayFileDiff(String path, File file1, File file2, long revision1, long revision2, String mimeType1, String mimeType2, SVNProperties originalProperties, SVNProperties diff) throws SVNException {
        boolean resetEncoding = false;
        OutputStream result = myResult;
        String encoding = defineEncoding(originalProperties, diff);
        if (encoding != null) {
            myGenerator.setEncoding(encoding);
            resetEncoding = true;
        } else {
            String conversionEncoding = defineConversionEncoding(originalProperties, diff);
            if (conversionEncoding != null) {
                myGenerator.setEncoding("UTF-8");
                result = new SVNCharsetOutputStream(result, Charset.forName("UTF-8"), Charset.forName(conversionEncoding));
                resetEncoding = true;
            }
        }
        try {
            myGenerator.displayFileDiff(getDisplayPath(path), file1, file2, getRevision(revision1), getRevision(revision2), mimeType1, mimeType2, result);
        } finally {
            if (resetEncoding) {
                myGenerator.setEncoding(null);
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

    public SVNStatusType propertiesChanged(String path, SVNProperties originalProperties, SVNProperties diff, boolean[] isTreeConflicted) throws SVNException {
        originalProperties = originalProperties == null ? new SVNProperties() : originalProperties;
        diff = diff == null ? new SVNProperties() : diff;
        SVNProperties regularDiff = new SVNProperties();
        categorizeProperties(diff, regularDiff, null, null);
        if (diff.isEmpty()) {
            return SVNStatusType.UNKNOWN;
        }
        myGenerator.displayPropDiff(getDisplayPath(path), originalProperties, regularDiff, myResult);
        return SVNStatusType.UNKNOWN;
    }

    private String getRevision(long revision) {
        if (revision >= 0) {
            return "(revision " + revision + ")";
        }
        return "(working copy)";
    }

    private String defineEncoding(SVNProperties properties, SVNProperties diff) {
        if (myGenerator instanceof DefaultSVNDiffGenerator) {
            DefaultSVNDiffGenerator defaultGenerator = (DefaultSVNDiffGenerator) myGenerator;
            if (defaultGenerator.hasEncoding()) {
                return null;
            }

            String originalMimeType = properties == null ? null : properties.getStringValue(SVNProperty.MIME_TYPE);
            String originalEncoding = SVNPropertiesManager.determineEncodingByMimeType(originalMimeType);
            boolean originalEncodingSupported = originalEncoding != null && Charset.isSupported(originalEncoding);
            if (originalEncodingSupported) {
                return originalEncoding;
            }

            String changedMimeType = diff == null ? null : diff.getStringValue(SVNProperty.MIME_TYPE);
            String changedEncoding = SVNPropertiesManager.determineEncodingByMimeType(changedMimeType);
            boolean changedEncodingSupported = changedEncoding != null && Charset.isSupported(changedEncoding);
            if (changedEncodingSupported) {
                return changedEncoding;
            }
        }
        return null;
    }

    private String defineConversionEncoding(SVNProperties properties, SVNProperties diff) {
        if (myGenerator instanceof DefaultSVNDiffGenerator) {
            DefaultSVNDiffGenerator defaultGenerator = (DefaultSVNDiffGenerator) myGenerator;
            if (defaultGenerator.hasEncoding()) {
                return null;
            }
            String originalCharset = properties == null ? null : properties.getStringValue(SVNProperty.CHARSET);
            boolean originalCharsetSupported = originalCharset != null && Charset.isSupported(originalCharset);
            if (originalCharsetSupported) {
                return originalCharset;
            }

            String changedCharset = diff == null ? null : diff.getStringValue(SVNProperty.CHARSET);
            boolean changedCharsetSupported = changedCharset != null && Charset.isSupported(changedCharset);
            if (changedCharsetSupported) {
                return changedCharset;
            }

            String globalEncoding = defaultGenerator.getGlobalEncoding();
            boolean globalEncodingSupported = globalEncoding != null && Charset.isSupported(globalEncoding);
            if (globalEncodingSupported) {
                return globalEncoding;
            }
        }
        return null;
    }

    public SVNStatusType directoryDeleted(String path, boolean[] isTreeConflicted) throws SVNException {
        setIsConflicted(isTreeConflicted, false);
        return directoryDeleted(path);
    }

    public void directoryOpened(String path, long revision, boolean[] isTreeConflicted) throws SVNException {
        setIsConflicted(isTreeConflicted, false);
    }

    public SVNStatusType[] directoryClosed(String path, boolean[] isTreeConflicted) throws SVNException {
        setIsConflicted(isTreeConflicted, false);
        return EMPTY_STATUS;
    }

}
