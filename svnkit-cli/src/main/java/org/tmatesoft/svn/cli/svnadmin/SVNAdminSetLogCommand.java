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
package org.tmatesoft.svn.cli.svnadmin;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminSetLogCommand extends SVNAdminCommand {

    public SVNAdminSetLogCommand() {
        super("setlog", null);
    }

    protected Collection createSupportedOptions() {
        List options = new LinkedList();
        options.add(SVNAdminOption.REVISION);
        options.add(SVNAdminOption.BYPASS_HOOKS);
        return options;
    }

    public void run() throws SVNException {
        if (getSVNAdminEnvironment().getStartRevision() == SVNRevision.UNDEFINED) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Missing revision"), SVNLogType.CLIENT);
        } 
        if (getSVNAdminEnvironment().getEndRevision() != SVNRevision.UNDEFINED) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Only one revision allowed"), SVNLogType.CLIENT);
        }
        File repos = getLocalRepository();
        List targets = getEnvironment().combineTargets(null, false);
        if (!targets.isEmpty()) {
            targets.remove(0);
        }
        if (targets.size() != 1) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                "Exactly one file argument required"), SVNLogType.CLIENT);
        }
        
        SVNPath target = new SVNPath((String) targets.get(0));
        if (!target.isFile()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                "Exactly one file argument required"), SVNLogType.CLIENT);
        }
        SVNPropertyValue propertyValue = SVNPropertyValue.create(SVNRevisionProperty.LOG, getEnvironment().readFromFile(target.getFile()));
        SVNURL url = SVNURL.fromFile(repos);
        FSRepository repository = (FSRepository) SVNRepositoryFactory.create(url);
        long rev = getRevisionNumber(getSVNAdminEnvironment().getStartRevision(), repository.getLatestRevision(), repository);

        repository.setRevisionPropertyValue(rev, SVNRevisionProperty.LOG, propertyValue, getSVNAdminEnvironment().isBypassHooks());
    }
}
