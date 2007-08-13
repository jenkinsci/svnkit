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

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli2.SVNCommand;
import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNDiffStatusHandler;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffStatus;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNDiffCommand extends SVNCommand implements ISVNDiffStatusHandler {

    public SVNDiffCommand() {
        super("diff", new String[] {"di"});
    }
    
    public boolean acceptsRevisionRange() {
        return true;
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.CHANGE);
        options.add(SVNOption.OLD);
        options.add(SVNOption.NEW);
        options.add(SVNOption.NON_RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.EXTENSIONS);
        options.add(SVNOption.NO_DIFF_DELETED);
        options.add(SVNOption.NOTICE_ANCESTRY);
        options.add(SVNOption.SUMMARIZE);
        options.add(SVNOption.CHANGELIST);
        options.add(SVNOption.FORCE);
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        return options;
    }

    public void run() throws SVNException {
        List targets = new ArrayList(); 
        if (getEnvironment().getChangelist() != null) {
            SVNCommandTarget target = new SVNCommandTarget("");
            SVNChangelistClient changelistClient = getEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(target.getFile(), getEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "no such changelist ''{0}''", getEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        if (getEnvironment().getTargets() != null) {
            targets.addAll(getEnvironment().getTargets());
        }
        targets = getEnvironment().combineTargets(targets);
        
        SVNCommandTarget oldTarget = null;
        SVNCommandTarget newTarget = null;
        SVNRevision start = getEnvironment().getStartRevision();
        SVNRevision end = getEnvironment().getEndRevision();
        boolean peggedDiff = false;
        
        if (targets.size() == 2 && 
                getEnvironment().getOldTarget() == null && 
                getEnvironment().getNewTarget() == null &&
                SVNCommandUtil.isURL((String) targets.get(0)) && 
                SVNCommandUtil.isURL((String) targets.get(1)) &&
                getEnvironment().getStartRevision() == SVNRevision.UNDEFINED &&
                getEnvironment().getEndRevision() == SVNRevision.UNDEFINED) {
            oldTarget = new SVNCommandTarget((String) targets.get(0), true);
            newTarget = new SVNCommandTarget((String) targets.get(1), true);
            start = oldTarget.getPegRevision();
            end = newTarget.getPegRevision();
            targets.clear();
            if (start == SVNRevision.UNDEFINED) {
                start = SVNRevision.HEAD;
            }
            if (end == SVNRevision.UNDEFINED) {
                end = SVNRevision.HEAD;
            }
        } else if (getEnvironment().getOldTarget() != null) {
            targets.clear();
            targets.add(getEnvironment().getOldTarget());
            targets.add(getEnvironment().getNewTarget() != null ? getEnvironment().getNewTarget() : getEnvironment().getOldTarget());
            
            oldTarget = new SVNCommandTarget((String) targets.get(0), true);
            newTarget = new SVNCommandTarget((String) targets.get(1), true);
            start = getEnvironment().getStartRevision();
            end = getEnvironment().getEndRevision();
            if (oldTarget.getPegRevision() != SVNRevision.UNDEFINED) {
                start = oldTarget.getPegRevision();
            }
            if (newTarget.getPegRevision() != SVNRevision.UNDEFINED) {
                end = newTarget.getPegRevision();
            }
            if (start == SVNRevision.UNDEFINED) {
                start = oldTarget.isURL() ? SVNRevision.HEAD : SVNRevision.BASE;
            }
            if (end == SVNRevision.UNDEFINED) {
                end = newTarget.isURL() ? SVNRevision.HEAD : SVNRevision.WORKING;
            }
            targets = getEnvironment().combineTargets(null);
        } else if (getEnvironment().getNewTarget() != null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "'--new' option only valid with '--old' option");
            SVNErrorManager.error(err);
        } else {
            if (targets.isEmpty()) {
                targets.add("");
            }
            oldTarget = new SVNCommandTarget("");
            newTarget = new SVNCommandTarget("");
            boolean hasURLs = false;
            boolean hasWCs = false;
            
            for(int i = 0; i < targets.size(); i++) {
                SVNCommandTarget target = new SVNCommandTarget((String) targets.get(i));
                hasURLs |= target.isURL();
                hasWCs |= target.isFile();
            }
            
            if (hasURLs && hasWCs) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Target lists to diff may not contain both working copy paths and URLs");
                SVNErrorManager.error(err);
            }
            start = getEnvironment().getStartRevision();
            end = getEnvironment().getEndRevision();
            if (start == SVNRevision.UNDEFINED && hasWCs) {
                start = SVNRevision.BASE;
            }
            if (end == SVNRevision.UNDEFINED) {
                end = hasWCs ? SVNRevision.WORKING : SVNRevision.HEAD;
            }
            peggedDiff = (start != SVNRevision.BASE && start != SVNRevision.WORKING) || (end != SVNRevision.BASE && end != SVNRevision.WORKING);
        }
        if (targets.isEmpty()) {
            targets.add("");
        }

        SVNDiffClient client = getEnvironment().getClientManager().getDiffClient();
        DefaultSVNDiffGenerator diffGenerator = new DefaultSVNDiffGenerator();
        diffGenerator.setDiffOptions(getEnvironment().getDiffOptions());
        diffGenerator.setDiffDeleted(!getEnvironment().isNoDiffDeleted());
        diffGenerator.setForcedBinaryDiff(getEnvironment().isForce());
        diffGenerator.setBasePath(new File("").getAbsoluteFile());
        client.setDiffGenerator(diffGenerator);
        
        PrintStream ps = getEnvironment().getOut();
        for(int i = 0; i < targets.size(); i++) {
            String targetName = (String) targets.get(i);
            if (!peggedDiff) {
                SVNCommandTarget target1 = new SVNCommandTarget(SVNPathUtil.append(oldTarget.getTarget(), targetName));
                SVNCommandTarget target2 = new SVNCommandTarget(SVNPathUtil.append(newTarget.getTarget(), targetName));
                if (getEnvironment().isSummarize()) {
                    if (target1.isURL() && target2.isURL()) {
                        client.doDiffStatus(target1.getURL(), start, target2.getURL(), end, getEnvironment().getDepth(), getEnvironment().isNoticeAncestry(), this);
                    } else if (target1.isURL()) {
                        client.doDiffStatus(target1.getURL(), start, target2.getFile(), end, getEnvironment().getDepth(), getEnvironment().isNoticeAncestry(), this);
                    } else if (target2.isURL()) {
                        client.doDiffStatus(target1.getFile(), start, target2.getURL(), end, getEnvironment().getDepth(), getEnvironment().isNoticeAncestry(), this);
                    } else {
                        client.doDiffStatus(target1.getFile(), start, target2.getFile(), end, getEnvironment().getDepth(), getEnvironment().isNoticeAncestry(), this);
                    }
                } else {
                    if (target1.isURL() && target2.isURL()) {
                        client.doDiff(target1.getURL(), start, target2.getURL(), end, getEnvironment().getDepth(), getEnvironment().isNoticeAncestry(), ps);
                    } else if (target1.isURL()) {
                        client.doDiff(target1.getURL(), start, target2.getFile(), end, getEnvironment().getDepth(), getEnvironment().isNoticeAncestry(), ps);
                    } else if (target2.isURL()) {
                        client.doDiff(target1.getFile(), start, target2.getURL(), end, getEnvironment().getDepth(), getEnvironment().isNoticeAncestry(), ps);
                    } else {
                        client.doDiff(target1.getFile(), start, target2.getFile(), end, getEnvironment().getDepth(), getEnvironment().isNoticeAncestry(), ps);
                    }
                }
            } else {
                SVNCommandTarget target = new SVNCommandTarget(targetName, true);
                SVNRevision pegRevision = target.getPegRevision();
                if (pegRevision == SVNRevision.UNDEFINED) {
                    pegRevision = target.isURL() ? SVNRevision.HEAD : SVNRevision.WORKING;
                }
                if (getEnvironment().isSummarize()) {
                    if (target.isURL()) {
                        client.doDiffStatus(target.getURL(), start, end, pegRevision, getEnvironment().getDepth(), getEnvironment().isNoticeAncestry(), this);
                    } else {
                        client.doDiffStatus(target.getFile(), start, end, pegRevision, getEnvironment().getDepth(), getEnvironment().isNoticeAncestry(), this);
                    }
                } else {
                    if (target.isURL()) {
                        client.doDiff(target.getURL(), pegRevision, start, end, getEnvironment().getDepth(), getEnvironment().isNoticeAncestry(), ps);
                    } else {
                        client.doDiff(target.getFile(), pegRevision, start, end, getEnvironment().getDepth(), getEnvironment().isNoticeAncestry(), ps);
                    }
                }
            }
        }
    }

    public void handleDiffStatus(SVNDiffStatus diffStatus) throws SVNException {
        if (diffStatus.getModificationType() == SVNStatusType.STATUS_NONE &&
                !diffStatus.isPropertiesModified()) {
            return;
        }
        String path = diffStatus.getPath();
        if (!SVNCommandUtil.isURL(path)) {
            if (diffStatus.getFile() != null) {
                path = getEnvironment().getRelativePath(diffStatus.getFile());
            }
            path = SVNCommandUtil.getLocalPath(path);
        }
        getEnvironment().getOut().print(diffStatus.getModificationType().getCode() + (diffStatus.isPropertiesModified() ? "M" : " ") + "     " + path + "\n");
        getEnvironment().getOut().flush();
    }

}
