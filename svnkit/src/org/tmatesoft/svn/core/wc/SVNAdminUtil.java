/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.IOException;
import java.io.InputStream;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNAdminUtil {

    public static void generateIncompleteDataError() throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, "Premature end of content data in dumpstream");
        SVNErrorManager.error(err);
    }

    public static void generateStreamMalformedError() throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Dumpstream data appears to be malformed");
        SVNErrorManager.error(err);
    }

    public static int readKeyOrValue(InputStream dumpStream, byte[] buffer, int len) throws SVNException, IOException {
        int r = dumpStream.read(buffer);
        
        if (r != len) {
            SVNAdminUtil.generateIncompleteDataError();
        }
        
        int readLength = r;
        
        r = dumpStream.read();
        if (r == -1) {
            SVNAdminUtil.generateIncompleteDataError();
        } else if (r != '\n') {
            SVNAdminUtil.generateStreamMalformedError();
        }
        
        return ++readLength;
    }

    public static final String DUMPFILE_MAGIC_HEADER           = "SVN-fs-dump-format-version";
    public static final String DUMPFILE_CONTENT_LENGTH         = "Content-length";
    public static final int DUMPFILE_FORMAT_VERSION            = 3;
    public static final String DUMPFILE_NODE_ACTION            = "Node-action";
    public static final String DUMPFILE_NODE_COPYFROM_PATH     = "Node-copyfrom-path";
    public static final String DUMPFILE_NODE_COPYFROM_REVISION = "Node-copyfrom-rev";
    public static final String DUMPFILE_NODE_KIND              = "Node-kind";
    public static final String DUMPFILE_NODE_PATH              = "Node-path";
    public static final String DUMPFILE_PROP_CONTENT_LENGTH    = "Prop-content-length";
    public static final String DUMPFILE_PROP_DELTA             = "Prop-delta";
    public static final String DUMPFILE_REVISION_NUMBER        = "Revision-number";
    public static final String DUMPFILE_TEXT_CONTENT_LENGTH    = "Text-content-length";
    public static final String DUMPFILE_TEXT_DELTA             = "Text-delta";
    public static final String DUMPFILE_UUID                   = "UUID";
    public static final int STREAM_CHUNK_SIZE                  = 16384;

}
