/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc.patch.SVNPatch;
import org.tmatesoft.svn.core.internal.wc.patch.SVNPatchFileStream;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNPatchClient extends SVNBasicClient {

    public SVNPatchClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }

    public void doPatch(File patchPath, File targetPath, boolean dryRun, long stripCount) throws SVNException {

        if (stripCount < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, "strip count must be positive");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }

        final SVNWCAccess wcAccess = createWCAccess();
        try {
            wcAccess.open(targetPath.getAbsoluteFile(), true, SVNWCAccess.INFINITE_DEPTH);
            applyPatches(patchPath, targetPath, dryRun, stripCount);
        } finally {
            wcAccess.close();
        }

    }

    private void applyPatches(File patchPath, File targetPath, boolean dryRun, long stripCount) throws SVNException {

        final SVNPatchFileStream patchFile = SVNPatchFileStream.openReadOnly(patchPath);

        try {
            SVNPatch patch;
            do {
                checkCancelled();
                patch = SVNPatch.parseNextPatch(patchFile);
                if (patch != null) {
                    patch.applyPatch(targetPath, dryRun, stripCount);
                    patch.close();
                }
            } while (patch != null);
        } finally {
            if (patchFile != null) {
                patchFile.close();
            }
        }

    }

}
