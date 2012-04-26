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

import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnLogMergeInfo;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNMergeInfoCommand extends SVNCommand implements ISVNLogEntryHandler {

    public SVNMergeInfoCommand() {
        super("mergeinfo", null);
    }
    
    protected Collection<SVNOption> createSupportedOptions() {
        Collection<SVNOption> options = new LinkedList<SVNOption>();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.SHOW_REVS);
        options.add(SVNOption.RECURSIVE);
        options.add(SVNOption.DEPTH);
        return options;
    }

    public void run() throws SVNException {
        List<String> targets = getSVNEnvironment().combineTargets(null, true);
        if (targets.size() < 1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Not enough arguments given");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        if (targets.size() > 2) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Too many arguments given");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        
        SVNPath source = new SVNPath((String) targets.get(0), true);
        SVNRevision srcPegRevision = source.getPegRevision();
        if (srcPegRevision == SVNRevision.UNDEFINED) {
            srcPegRevision = SVNRevision.HEAD;
        }
        
        SVNPath target = null;
        SVNRevision tgtPegRevision = null;
        if (targets.size() == 2) {
            target = new SVNPath((String) targets.get(1), true);
            tgtPegRevision = target.getPegRevision();
        } else {
            target = new SVNPath("");
            tgtPegRevision = SVNRevision.UNDEFINED;
        }
        
        if (tgtPegRevision == SVNRevision.UNDEFINED) {
            if (target.isURL()) {
                tgtPegRevision = SVNRevision.HEAD;
            } else {
                tgtPegRevision = SVNRevision.BASE;
            }
        }
        
        SVNDiffClient client = getSVNEnvironment().getClientManager().getDiffClient();
        SvnOperationFactory of = client.getOperationsFactory();
        SVNDepth depth = getSVNEnvironment().getDepth();
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.EMPTY;
        }
        SvnLogMergeInfo logMergeInfo = of.createLogMergeInfo();
        logMergeInfo.setDepth(depth);
        logMergeInfo.setDiscoverChangedPaths(true);
        logMergeInfo.setRevisionProperties(null);
        logMergeInfo.setReceiver(new ISvnObjectReceiver<SVNLogEntry>() {            
            public void receive(SvnTarget target, SVNLogEntry object) throws SVNException {
                handleLogEntry(object);
            }
        });
        logMergeInfo.setFindMerged(getSVNEnvironment().getShowRevisionType() == SVNShowRevisionType.MERGED);
        if (target.isURL()) {
            logMergeInfo.setSingleTarget(SvnTarget.fromURL(target.getURL(), tgtPegRevision));
        } else {
            logMergeInfo.setSingleTarget(SvnTarget.fromFile(target.getFile(), tgtPegRevision));
        }
        if (source.isURL()) {
            logMergeInfo.setSource(SvnTarget.fromURL(source.getURL(), srcPegRevision));
        } else {
            logMergeInfo.setSource(SvnTarget.fromFile(source.getFile(), srcPegRevision));
        }
        
        logMergeInfo.run();
    }
    
    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
        String message;
        if (logEntry.isNonInheritable()) {
            message = MessageFormat.format("r{0}*", new Object[] { String.valueOf(logEntry.getRevision()) });
        } else {
            message = MessageFormat.format("r{0}", new Object[] { String.valueOf(logEntry.getRevision()) });
        }
        getSVNEnvironment().getOut().println(message);
    }
    
}
