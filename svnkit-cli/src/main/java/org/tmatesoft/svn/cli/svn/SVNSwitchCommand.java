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
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSwitchCommand extends SVNCommand {

    public SVNSwitchCommand() {
        super("switch", new String[] {"sw", "relocate"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.NON_RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.SET_DEPTH);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.DIFF3_CMD);
        options.add(SVNOption.RELOCATE);
        options.add(SVNOption.IGNORE_EXTERNALS);
        options.add(SVNOption.IGNORE_ANCESTRY);
        options.add(SVNOption.FORCE);
        options.add(SVNOption.ACCEPT);
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(new ArrayList(), true);
        if (getSVNEnvironment().isRelocate()) {
            if (getSVNEnvironment().getDepth() != SVNDepth.UNKNOWN) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS, 
                        "--relocate and --depth are mutually exclusive");
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            relocate(targets);
            return;
        }
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
        }
        if (targets.size() > 2) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR), SVNLogType.CLIENT);
        }
        SVNPath switchURL = new SVNPath((String) targets.get(0), true);
        if (!switchURL.isURL()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.BAD_URL, 
                    "''{0}'' doesn not appear to be a URL", switchURL.getTarget()), SVNLogType.CLIENT);
        }
        SVNPath target;
        if (targets.size() == 1) {
            target = new SVNPath("");
        } else {
            target = new SVNPath((String) targets.get(1));
        }
        SVNUpdateClient client = getSVNEnvironment().getClientManager().getUpdateClient();
        SVNNotifyPrinter printer = new SVNNotifyPrinter(getSVNEnvironment(), false, false, false);
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(printer);
        }
        
        SVNDepth depth = getSVNEnvironment().getDepth();
        boolean depthIsSticky = false;
        if (getSVNEnvironment().getSetDepth() != SVNDepth.UNKNOWN) {
            depth = getSVNEnvironment().getSetDepth();
            depthIsSticky = true;
        }
        boolean ignoreAncestry = getSVNEnvironment().isIgnoreAncestry();
        client.doSwitch(target.getFile(), switchURL.getURL(), switchURL.getPegRevision(), 
                getSVNEnvironment().getStartRevision(), depth, 
                getSVNEnvironment().isForce(), depthIsSticky, ignoreAncestry);    

        if (!getSVNEnvironment().isQuiet()) {
            StringBuffer status = new StringBuffer();
            printer.printConflictStatus(status);
            getSVNEnvironment().getOut().print(status);
        }

        if (printer.hasExternalErrors()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ERROR_PROCESSING_EXTERNALS, 
                    "Failure occurred processing one or more externals definitions"), SVNLogType.CLIENT);
        }
    }
    
    protected void relocate(List targets) throws SVNException {
        if (targets.size() < 1) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
        }
        SVNUpdateClient client = getSVNEnvironment().getClientManager().getUpdateClient();
        if (targets.size() == 1 ||
                (targets.size() == 2 
                && new SVNPath((String) targets.get(0)).isURL() 
                && !new SVNPath((String) targets.get(1)).isURL())) {
            SVNPath target = targets.size() == 2 ? new SVNPath((String) targets.get(1)) : new SVNPath("");
            SVNPath to = new SVNPath((String) targets.get(0));
            client.doRelocate(target.getFile(), null, to.getURL(), getSVNEnvironment().getDepth().isRecursive());
        } else {
            if (targets.get(0).equals(targets.get(1))) {
                return;
            }
            SVNPath from = new SVNPath((String) targets.get(0));
            SVNPath to = new SVNPath((String) targets.get(1));
            
            if (from.isURL() != to.isURL() || !from.isURL()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
                        "''{0}'' to ''{1}'' is not a valid relocation", new Object[] {from.getTarget(), to.getTarget()});
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            if (targets.size() == 2) {
                SVNPath target = new SVNPath("");
                client.doRelocate(target.getFile(), from.getURL(), to.getURL(), getSVNEnvironment().getDepth().isRecursive());
            } else {
                for(int i = 2; i < targets.size(); i++) {
                    SVNPath target = new SVNPath((String) targets.get(i));
                    client.doRelocate(target.getFile(), from.getURL(), to.getURL(), getSVNEnvironment().getDepth().isRecursive());
                }
            }
        }
    }
}
