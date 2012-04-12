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
package org.tmatesoft.svn.cli.svnversion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.tmatesoft.svn.cli.AbstractSVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.SVNClientManager;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNVersionCommand extends AbstractSVNCommand {

    public SVNVersionCommand() {
        super("", null);
    }

    protected Collection createSupportedOptions() {
        List options = new ArrayList();
        options.add(SVNVersionOption.NO_NEWLINE);
        options.add(SVNVersionOption.COMMITTED);
        options.add(SVNVersionOption.HELP);
        options.add(SVNVersionOption.VERSION);
        return options;
    }
    
    protected SVNVersionCommandEnvironment getSVNVersionEnvironment() {
        return (SVNVersionCommandEnvironment) getEnvironment();
    }

    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli.svnversion.commands";
    }

    public void run() throws SVNException {
        List targets = getEnvironment().combineTargets(null, false);
        if (targets.isEmpty()) {
            targets.add("");
        }
        SVNPath target = new SVNPath((String) targets.get(0));
        if (target.isURL()) {
            target = new SVNPath("");
            targets.add(0, "");
        }
        String trailURL = (String) (targets.size() > 1 ? targets.get(1) : null);
        if (target.isFile()) {
            String id = SVNClientManager.newInstance().getWCClient().doGetWorkingCopyID(target.getFile(), trailURL, getSVNVersionEnvironment().isCommitted());
            if (id != null) {
                getEnvironment().getOut().print(id);
                if (!getSVNVersionEnvironment().isNoNewLine()) {
                    getEnvironment().getOut().println();
                }
            }
        }
    }

    public Collection getGlobalOptions() {
        return Collections.EMPTY_LIST;
    }

}
