/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli2.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli2.SVNCommand;
import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeCommand extends SVNCommand {

    public SVNMergeCommand() {
        super("merge", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();

        options.add(SVNOption.REVISION);
        options.add(SVNOption.CHANGE);
        options.add(SVNOption.NON_RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.FORCE);
        options.add(SVNOption.DRY_RUN);
        options.add(SVNOption.RECORD_ONLY);
        options.add(SVNOption.USE_MERGE_HISTORY);
        options.add(SVNOption.EXTENSIONS);
        options.add(SVNOption.IGNORE_ANCESTRY);
        
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        return options;
    }

    public void run() throws SVNException {
        List targets = (List) getEnvironment().combineTargets(new ArrayList());
        SVNCommandTarget source1 = null;
        SVNCommandTarget source2 = null;
//        SVNCommandTarget target = new SVNCommandTarget("");
        
        if (targets.size() >= 1) {
            source1 = new SVNCommandTarget((String) targets.get(0));
            if (targets.size() >= 2) {
                source2 = new SVNCommandTarget((String) targets.get(1));
            }
        }
        boolean isUseRevisionRange = false;
        if (targets.size() <=1) {
            isUseRevisionRange = true;
        } else if (targets.size() == 2) {
            isUseRevisionRange = source1.isURL() && !source2.isURL();
        }
        if (getEnvironment().getStartRevision() != SVNRevision.UNDEFINED) {
            if (getEnvironment().getEndRevision() == SVNRevision.UNDEFINED) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Second revision required"));
            }
            isUseRevisionRange = true;
        }
        if (isUseRevisionRange) {
            if (targets.size() < 1 && !getEnvironment().isUseMergeHistory()) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
            }
            if (targets.size() > 2) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Too many arguments given"));
            }
            if (targets.isEmpty()) {
                
            } else {
                
            }
        }
    }

}
