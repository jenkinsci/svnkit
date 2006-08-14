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
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCUtil;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNLogRunner2 {
    private boolean myIsEntriesChanged;

    public void runCommand(SVNAdminArea adminArea, String name, Map attributes) throws SVNException {
        String fileName = (String) attributes.get(ISVNLog.NAME_ATTR);
        if (ISVNLog.DELETE_ENTRY.equals(name)) {
        } else if (ISVNLog.MODIFY_ENTRY.equals(name)) {
        } else if (ISVNLog.MODIFY_WC_PROPERTY.equals(name)) {
        } else if (ISVNLog.DELETE_LOCK.equals(name)) {
        } else if (ISVNLog.DELETE.equals(name)) {
            File file = new File(adminArea.getRoot(), fileName);
            file.delete();
        } else if (ISVNLog.READONLY.equals(name)) {
            File file = new File(adminArea.getRoot(), fileName);
            SVNFileUtil.setReadonly(file, true);
        } else if (ISVNLog.MOVE.equals(name)) {
            File src = new File(adminArea.getRoot(), fileName);
            File dst = new File(adminArea.getRoot(), (String) attributes.get(ISVNLog.DEST_ATTR));
            SVNFileUtil.rename(src, dst);
        } else if (ISVNLog.APPEND.equals(name)) {
            File src = new File(adminArea.getRoot(), fileName);
            File dst = new File(adminArea.getRoot(), (String) attributes
                    .get(ISVNLog.DEST_ATTR));
            OutputStream os = null;
            InputStream is = null;
            try {
                os = SVNFileUtil.openFileForWriting(dst, true);
                is = SVNFileUtil.openFileForReading(src);
                while (true) {
                    int r = is.read();
                    if (r < 0) {
                        break;
                    }
                    os.write(r);
                }
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot write to ''{0}'': {1}", new Object[] {dst, e.getLocalizedMessage()});
                SVNErrorManager.error(err, e);
            } finally {
                SVNFileUtil.closeFile(os);
                SVNFileUtil.closeFile(is);
            }
        } else if (ISVNLog.SET_TIMESTAMP.equals(name)) {
            File file = new File(adminArea.getRoot(), fileName);
            Date time = SVNTimeUtil.parseDate((String) attributes
                    .get(ISVNLog.TIMESTAMP_ATTR));
            file.setLastModified(time.getTime());
        } else if (ISVNLog.MAYBE_READONLY.equals(name)) {
        } else if (ISVNLog.COPY_AND_TRANSLATE.equals(name)) {
        } else if (ISVNLog.COPY_AND_DETRANSLATE.equals(name)) {
        } else if (ISVNLog.MERGE.equals(name)) {
        } else if (ISVNLog.COMMIT.equals(name)) {
        }
    }

    private void setEntriesChanged(boolean modified) {
        myIsEntriesChanged |= modified;
    }
    
    public void logFailed(SVNAdminArea adminArea) throws SVNException {
    }

    public void logCompleted(SVNAdminArea adminArea) throws SVNException {
    }

}
