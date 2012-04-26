/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNExportCommand extends SVNCommand {

    public SVNExportCommand() {
        super("export", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.NON_RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.FORCE);
        options.add(SVNOption.NATIVE_EOL);
        options.add(SVNOption.IGNORE_EXTERNALS);
        options.add(SVNOption.IGNORE_KEYWORDS);
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(new ArrayList(), true);
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
        }
        if (targets.size() > 2) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR), SVNLogType.CLIENT);
        }
        SVNPath from = new SVNPath((String) targets.get(0), true);
        SVNRevision pegRevision = from.getPegRevision();
        String to;
        if (targets.size() == 1) {
            to = SVNPathUtil.tail(from.getTarget());
            if (from.isURL()) {
                to = SVNEncodingUtil.uriDecode(to);
            }
        } else {
            to = (String) targets.get(1);
        }
        SVNUpdateClient client = getSVNEnvironment().getClientManager().getUpdateClient();
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment(), false, true, false));
        }
        SVNDepth depth = getSVNEnvironment().getDepth();
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        try {
            SVNPath dst = new SVNPath(to);
            String eol = getSVNEnvironment().getNativeEOL();
            SVNRevision revision = getSVNEnvironment().getStartRevision();
            client.setExportExpandsKeywords(!getSVNEnvironment().isIgnoreKeywords());
            
            if (from.isFile()) {
                client.doExport(from.getFile(), dst.getFile(), pegRevision, revision, eol, getSVNEnvironment().isForce(), depth);
            } else {
                client.doExport(from.getURL(), dst.getFile(), pegRevision, revision, eol, getSVNEnvironment().isForce(), depth);
            }
        } catch (SVNException e) {
            SVNErrorMessage err = e.getErrorMessage();
            if (err != null && err.getErrorCode() == SVNErrorCode.WC_OBSTRUCTED_UPDATE) {
                err = err.wrap("Destination directory exists; please remove the directory or use --force to overwrite");
            }
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
    }

}
