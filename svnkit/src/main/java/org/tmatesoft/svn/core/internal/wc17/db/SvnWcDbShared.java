package org.tmatesoft.svn.core.internal.wc17.db;

import java.io.File;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnWcDbShared {
    
    protected static void nodeNotFound(File absolutePath) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", absolutePath);
        SVNErrorManager.error(err, SVNLogType.WC);
    }
}
