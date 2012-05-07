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
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
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
        options.add(SVNOption.DIFF3_CMD);
        options.add(SVNOption.RECORD_ONLY);
        options.add(SVNOption.EXTENSIONS);
        options.add(SVNOption.IGNORE_ANCESTRY);
        options.add(SVNOption.ACCEPT);
        options.add(SVNOption.REINTEGRATE);
        options.add(SVNOption.ALLOW_MIXED_REVISIONS);
        return options;
    }
    
    public boolean acceptsRevisionRange() {
        return true;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(new ArrayList(), true);
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
        
        boolean twoSourcesSpecified = true;
        if (targets.size() <= 1) {
            twoSourcesSpecified = false;
        } else if (targets.size() == 2) {
            if (source1.isURL() && !source2.isURL()) {
                twoSourcesSpecified = false;
            }
        }
        
        List rangesToMerge = getSVNEnvironment().getRevisionRanges();
        SVNRevision firstRangeStart = SVNRevision.UNDEFINED;
        SVNRevision firstRangeEnd = SVNRevision.UNDEFINED;
        if (!rangesToMerge.isEmpty()) {
        	SVNRevisionRange range = (SVNRevisionRange) rangesToMerge.get(0);
        	firstRangeStart = range.getStartRevision();
        	firstRangeEnd = range.getEndRevision();
        }
        if (firstRangeStart != SVNRevision.UNDEFINED) {
        	if (firstRangeEnd == SVNRevision.UNDEFINED) {
        		SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, 
        				"Second revision required");
        		SVNErrorManager.error(err, SVNLogType.CLIENT);
        	}
        	twoSourcesSpecified = false;
        }
        
        if (!twoSourcesSpecified) {
            if (targets.size() > 2) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                		"Too many arguments given"), SVNLogType.CLIENT);
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
                    if (target.isURL()) {
                    	SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    			"Cannot specifify a revision range with two URLs");
                    	SVNErrorManager.error(err, SVNLogType.CLIENT);
                    }
                }
            }
        } else {
            if (targets.size() < 2) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
            } else if (targets.size() > 3) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                		"Too many arguments given"), SVNLogType.CLIENT);
            }
            
            firstRangeStart = pegRevision1;
            firstRangeEnd = pegRevision2;
            
            if (((firstRangeStart == null || firstRangeStart == SVNRevision.UNDEFINED) && !source1.isURL()) ||
                    ((pegRevision2 == null || pegRevision2 == SVNRevision.UNDEFINED) && !source2.isURL())) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                		"A working copy merge source needs an explicit revision"), SVNLogType.CLIENT);
            }
            if (firstRangeStart == null || firstRangeStart == SVNRevision.UNDEFINED) {
                firstRangeStart = SVNRevision.HEAD;
            }
            if (firstRangeEnd == null || firstRangeEnd == SVNRevision.UNDEFINED) {
                firstRangeEnd = SVNRevision.HEAD;
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
        SVNNotifyPrinter printer = new SVNNotifyPrinter(getSVNEnvironment());
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(printer);
        }
        client.setAllowMixedRevisionsWCForMerge(getSVNEnvironment().isAllowMixedRevisions());
        try {
            client.setMergeOptions(getSVNEnvironment().getDiffOptions());
            try {
                if (!twoSourcesSpecified) {
                    if (firstRangeStart == SVNRevision.UNDEFINED && firstRangeEnd == SVNRevision.UNDEFINED) {
                    	SVNRevisionRange range = new SVNRevisionRange(SVNRevision.create(1), pegRevision1);
                    	rangesToMerge = new LinkedList();
                    	rangesToMerge.add(range);
                    }
                    
                    if (source1 == null) {
                        source1 = target;
                    }
                    
                    if (getSVNEnvironment().isReIntegrate()) {
                        if (getSVNEnvironment().getDepth() != SVNDepth.UNKNOWN) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS, 
                                    "--depth cannot be used with --reintegrate");
                            SVNErrorManager.error(err, SVNLogType.CLIENT);
                        }
                        
                        if (getSVNEnvironment().isForce()) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS, 
                                    "--force cannot be used with --reintegrate");
                            SVNErrorManager.error(err, SVNLogType.CLIENT);
                        }
                        
                        if (source1.isURL()) {
                            client.doMergeReIntegrate(source1.getURL(), pegRevision1, target.getFile(), 
                                    getSVNEnvironment().isDryRun());
                        } else {
                            client.doMergeReIntegrate(source1.getFile(), pegRevision1, target.getFile(), 
                                    getSVNEnvironment().isDryRun());
                        }
                    } else {
                        if (source1.isURL()) {
                            client.doMerge(source1.getURL(), pegRevision1, rangesToMerge, target.getFile(), 
                                    getSVNEnvironment().getDepth(), !getSVNEnvironment().isIgnoreAncestry(), 
                                    getSVNEnvironment().isForce(), getSVNEnvironment().isDryRun(), 
                                    getSVNEnvironment().isRecordOnly());
                        } else {
                            client.doMerge(source1.getFile(), pegRevision1, rangesToMerge, target.getFile(), 
                                    getSVNEnvironment().getDepth(), !getSVNEnvironment().isIgnoreAncestry(), 
                                    getSVNEnvironment().isForce(), getSVNEnvironment().isDryRun(), 
                                    getSVNEnvironment().isRecordOnly());
                        }
                    }
                } else {
                    if (source1.isURL() && source2.isURL()) {
                        client.doMerge(source1.getURL(), firstRangeStart, source2.getURL(), firstRangeEnd, 
                        		target.getFile(), getSVNEnvironment().getDepth(), 
                        		!getSVNEnvironment().isIgnoreAncestry(), getSVNEnvironment().isForce(), 
                        		getSVNEnvironment().isDryRun(), getSVNEnvironment().isRecordOnly());
                    } else if (source1.isURL() && source2.isFile()) {
                        client.doMerge(source1.getURL(), firstRangeStart, source2.getFile(), firstRangeEnd, 
                        		target.getFile(), getSVNEnvironment().getDepth(), 
                        		!getSVNEnvironment().isIgnoreAncestry(), getSVNEnvironment().isForce(), 
                        		getSVNEnvironment().isDryRun(), getSVNEnvironment().isRecordOnly());
                    } else if (source1.isFile() && source2.isURL()) {
                        client.doMerge(source1.getFile(), firstRangeStart, source2.getURL(), firstRangeEnd, 
                        		target.getFile(), getSVNEnvironment().getDepth(), 
                        		!getSVNEnvironment().isIgnoreAncestry(), getSVNEnvironment().isForce(), 
                        		getSVNEnvironment().isDryRun(), getSVNEnvironment().isRecordOnly());
                    } else {
                        client.doMerge(source1.getFile(), firstRangeStart, source2.getFile(), firstRangeEnd, 
                        		target.getFile(), getSVNEnvironment().getDepth(), 
                        		!getSVNEnvironment().isIgnoreAncestry(), getSVNEnvironment().isForce(), 
                        		getSVNEnvironment().isDryRun(), getSVNEnvironment().isRecordOnly());
                    }
                }
            } finally {
                if (!getSVNEnvironment().isQuiet()) {
                    StringBuffer status = new StringBuffer();
                    printer.printConflictStatus(status);
                    getSVNEnvironment().getOut().print(status);
                }
            }
        } catch (SVNException e) {
            SVNErrorMessage err = e.getErrorMessage();
            if (err != null && !getSVNEnvironment().isReIntegrate()) {
                SVNErrorCode code = err.getErrorCode();
                if (code == SVNErrorCode.UNVERSIONED_RESOURCE || code == SVNErrorCode.CLIENT_MODIFIED) {
                    err = err.wrap("Use --force to override this restriction");
                    SVNErrorManager.error(err, SVNLogType.CLIENT);
                }
            }
            throw e;
        }
    }
}
