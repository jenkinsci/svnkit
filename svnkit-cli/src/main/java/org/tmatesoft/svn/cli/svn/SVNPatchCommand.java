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
package org.tmatesoft.svn.cli.svn;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNPatchCommand extends SVNCommand {

    public SVNPatchCommand() {
        super("patch", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.QUIET);
        options.add(SVNOption.DRY_RUN);
        options.add(SVNOption.ACCEPT);
        options.add(SVNOption.DIFF3_CMD); // is it opt_merge_cmd ?
        options.add(SVNOption.STRIP);
        return options;
    }

    public void run() throws SVNException {

        final List targets = getSVNEnvironment().combineTargets(getSVNEnvironment().getTargets(), true);
        final int targetsCount = targets.size();

        if (targetsCount < 1 || targetsCount > 2) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR);
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }

        final SVNDiffClient client = getSVNEnvironment().getClientManager().getDiffClient();
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        }

        final File patchPath = new File((String) targets.get(0));
        final File targetPath = new File(targetsCount != 2 ? "." : (String) targets.get(1));

        try {
            client.doPatch(patchPath.getAbsoluteFile(), targetPath.getAbsoluteFile(), getSVNEnvironment().isDryRun(), getSVNEnvironment().getStripCount());
        } catch (SVNException e) {
            getSVNEnvironment().handleWarning(e.getErrorMessage(), new SVNErrorCode[] {
                    SVNErrorCode.ENTRY_EXISTS, SVNErrorCode.WC_PATH_NOT_FOUND
            }, getSVNEnvironment().isQuiet());
        }
    }

}
