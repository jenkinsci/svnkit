/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.sqljet.core.internal.lang.SqlParser.neq_subexpr_return;
import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.ISVNChangelistHandler;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc2.SvnCat;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpgrade;
import org.tmatesoft.svn.util.SVNLogType;


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
        SvnUpgrade upgrade = (new SvnOperationFactory()).createUpgrade();
    	
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
