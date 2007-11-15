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
package org.tmatesoft.svn.cli2.svn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
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
    
    public boolean acceptsRevisionRange() {
        return true;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(new ArrayList());
        SVNPath source1 = null;
        SVNPath source2 = null;
        SVNPath target = null;
        SVNRevision pegRevision1 = null;
        SVNRevision pegRevision2 = null;
        
        if (targets.size() >= 1) {
            source1 = new SVNPath((String) targets.get(0), true);
            pegRevision1 = source1.getPegRevision();
            if (targets.size() >= 2) {
                source2 = new SVNPath((String) targets.get(1), true);
                pegRevision2 = source2.getPegRevision();
            }
        }
        boolean isUseRevisionRange = false;
        if (targets.size() <=1) {
            isUseRevisionRange = true;
        } else if (targets.size() == 2) {
            isUseRevisionRange = source1.isURL() && !source2.isURL();
        }
        if (getSVNEnvironment().getStartRevision() != SVNRevision.UNDEFINED) {
            if (getSVNEnvironment().getEndRevision() == SVNRevision.UNDEFINED) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Second revision required"));
            }
            isUseRevisionRange = true;
        }
        if (isUseRevisionRange) {
            if (targets.size() < 1 && !getSVNEnvironment().isUseMergeHistory()) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
            }
            if (targets.size() > 2) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Too many arguments given"));
            }
            if (targets.isEmpty()) {
                pegRevision1 = SVNRevision.HEAD;
            } else {
                source2 = source1;
                if (pegRevision1 == null || pegRevision1 == SVNRevision.UNDEFINED) {
                    pegRevision1 = source1.isURL() ? SVNRevision.HEAD : SVNRevision.WORKING;
                }
                if (targets.size() == 2) {
                    target = new SVNPath((String) targets.get(1));
                }
            }
        } else {
            if (targets.size() < 2) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
            } else if (targets.size() > 3) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Too many arguments given"));
            }
            if (((pegRevision1 == null || pegRevision1 == SVNRevision.UNDEFINED) && !source1.isURL()) ||
                    ((pegRevision2 == null || pegRevision2 == SVNRevision.UNDEFINED) && !source2.isURL())) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "A working copy merge source needs an explicit revision"));
            }
            if (pegRevision1 == null || pegRevision1 == SVNRevision.UNDEFINED) {
                pegRevision1 = SVNRevision.HEAD;
            }
            if (pegRevision2 == null || pegRevision2 == SVNRevision.UNDEFINED) {
                pegRevision2 = SVNRevision.HEAD;
            }
            if (targets.size() >= 3) {
                target = new SVNPath((String) targets.get(2));
            }
        }
        if (source1 != null && source2 != null && target == null) {
            if (source1.isURL()) {
                String name1 = SVNPathUtil.tail(source1.getTarget());
                String name2 = SVNPathUtil.tail(source2.getTarget());
                if (name1.equals(name2)) {
                    String decodedPath = SVNEncodingUtil.uriDecode(name1);
                    SVNPath decodedPathTarget = new SVNPath(decodedPath); 
                    if (SVNFileType.getType(decodedPathTarget.getFile()) == SVNFileType.FILE) {
                        target = decodedPathTarget;
                    }
                }
            } else if (source1.equals(source2)) {
                String decodedPath = SVNEncodingUtil.uriDecode(source1.getTarget());
                SVNPath decodedPathTarget = new SVNPath(decodedPath); 
                if (SVNFileType.getType(decodedPathTarget.getFile()) == SVNFileType.FILE) {
                    target = decodedPathTarget;
                }
            } 
        }
        if (target == null) {
            target = new SVNPath("");
        }
        SVNDiffClient client = getSVNEnvironment().getClientManager().getDiffClient();
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        }
        try {
            client.setMergeOptions(getSVNEnvironment().getDiffOptions());
            if (isUseRevisionRange) {
                if (source1 == null) {
                    client.doMerge((SVNURL) null, pegRevision1, getSVNEnvironment().getStartRevision(), getSVNEnvironment().getEndRevision(), target.getFile(), 
                            getSVNEnvironment().getDepth(), !getSVNEnvironment().isIgnoreAncestry(), getSVNEnvironment().isForce(), getSVNEnvironment().isDryRun(), getSVNEnvironment().isRecordOnly());
                } else if (source1.isURL()) {
                    client.doMerge(source1.getURL(), pegRevision1, getSVNEnvironment().getStartRevision(), getSVNEnvironment().getEndRevision(), target.getFile(), 
                            getSVNEnvironment().getDepth(), !getSVNEnvironment().isIgnoreAncestry(), getSVNEnvironment().isForce(), getSVNEnvironment().isDryRun(), getSVNEnvironment().isRecordOnly());
                } else {
                    client.doMerge(source1.getFile(), pegRevision1, getSVNEnvironment().getStartRevision(), getSVNEnvironment().getEndRevision(), target.getFile(), 
                            getSVNEnvironment().getDepth(), !getSVNEnvironment().isIgnoreAncestry(), getSVNEnvironment().isForce(), getSVNEnvironment().isDryRun(), getSVNEnvironment().isRecordOnly());
                }
            } else {
                if (source1.isURL() && source2.isURL()) {
                    client.doMerge(source1.getURL(), pegRevision1, source2.getURL(), pegRevision2, target.getFile(), 
                            getSVNEnvironment().getDepth(), !getSVNEnvironment().isIgnoreAncestry(), getSVNEnvironment().isForce(), getSVNEnvironment().isDryRun(), getSVNEnvironment().isRecordOnly());
                } else if (source1.isURL() && source2.isFile()) {
                    client.doMerge(source1.getURL(), pegRevision1, source2.getFile(), pegRevision2, target.getFile(), 
                            getSVNEnvironment().getDepth(), !getSVNEnvironment().isIgnoreAncestry(), getSVNEnvironment().isForce(), getSVNEnvironment().isDryRun(), getSVNEnvironment().isRecordOnly());
                } else if (source1.isFile() && source2.isURL()) {
                    client.doMerge(source1.getFile(), pegRevision1, source2.getURL(), pegRevision2, target.getFile(), 
                            getSVNEnvironment().getDepth(), !getSVNEnvironment().isIgnoreAncestry(), getSVNEnvironment().isForce(), getSVNEnvironment().isDryRun(), getSVNEnvironment().isRecordOnly());
                } else {
                    client.doMerge(source1.getFile(), pegRevision1, source2.getFile(), pegRevision2, target.getFile(), 
                            getSVNEnvironment().getDepth(), !getSVNEnvironment().isIgnoreAncestry(), getSVNEnvironment().isForce(), getSVNEnvironment().isDryRun(), getSVNEnvironment().isRecordOnly());
                }
            }
        } catch (SVNException e) {
            SVNErrorMessage err = e.getErrorMessage();
            if (err != null) {
                SVNErrorCode code = err.getErrorCode();
                if (code == SVNErrorCode.UNVERSIONED_RESOURCE || code == SVNErrorCode.CLIENT_MODIFIED) {
                    err = err.wrap("Use --force to override this restriction");
                    SVNErrorManager.error(err);
                }
            }
            throw e;
        }
    }
}
