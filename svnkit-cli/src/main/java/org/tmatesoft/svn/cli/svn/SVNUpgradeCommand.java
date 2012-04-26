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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpgrade;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNUpgradeCommand extends SVNCommand {

    public SVNUpgradeCommand() {
        super("upgrade", new String[] {});
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.QUIET);
        options.add(SVNOption.TARGETS);
        return options;
    }

    public void run() throws SVNException {
    	List targets = getSVNEnvironment().combineTargets(getSVNEnvironment().getTargets(), true);
        if (targets.isEmpty()) {
            targets.add("");
        }
        SvnOperationFactory factory = new SvnOperationFactory();
        if (!getSVNEnvironment().isQuiet()) {
        	factory.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        }
        
        SvnUpgrade upgrade = factory.createUpgrade();
        
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
        	String targetName = (String) ts.next();
            SVNPath target = new SVNPath(targetName);
            if (target.isFile()) {
            	getSVNEnvironment().checkCancelled();
            	upgrade.setSingleTarget(SvnTarget.fromFile(target.getFile()));
                try {
                	upgrade.run();
                } catch (SVNException e) {
                    getSVNEnvironment().handleWarning(e.getErrorMessage(), new SVNErrorCode[] {SVNErrorCode.WC_NOT_DIRECTORY},
                            getSVNEnvironment().isQuiet());
                }
            }
            
            
        }
        
    } 

}
