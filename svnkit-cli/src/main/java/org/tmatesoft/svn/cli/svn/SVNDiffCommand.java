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

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnDiffGenerator;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnNewDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNDiffStatusHandler;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffStatus;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNDiffCommand extends SVNXMLCommand implements ISVNDiffStatusHandler {

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
        options.add(SVNOption.DIFF_CMD);
        options.add(SVNOption.EXTENSIONS);
        options.add(SVNOption.NO_DIFF_DELETED);
        options.add(SVNOption.NOTICE_ANCESTRY);
        options.add(SVNOption.SHOW_COPIES_AS_ADDS);
        options.add(SVNOption.SUMMARIZE);
        options.add(SVNOption.CHANGELIST);
        options.add(SVNOption.FORCE);
        options.add(SVNOption.XML);
        options.add(SVNOption.GIT_DIFF_FORMAT);
        return options;
    }

    public void run() throws SVNException {
        if (getSVNEnvironment().isXML()) {
            if (!getSVNEnvironment().isSummarize()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                        "'--xml' option only valid with '--summarize' option");
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            printXMLHeader("diff");
            StringBuffer buffer = openXMLTag("paths", SVNXMLUtil.XML_STYLE_NORMAL, null, null);
            getSVNEnvironment().getOut().print(buffer.toString());
        }
        List targets = new ArrayList(); 
        if (getSVNEnvironment().getTargets() != null) {
            targets.addAll(getSVNEnvironment().getTargets());
        }
        targets = getSVNEnvironment().combineTargets(targets, true);
        
        SVNPath oldTarget = null;
        SVNPath newTarget = null;
        SVNRevision start = getSVNEnvironment().getStartRevision();
        SVNRevision end = getSVNEnvironment().getEndRevision();
        boolean peggedDiff = false;
        
        if (targets.size() == 2 && 
                getSVNEnvironment().getOldTarget() == null && 
                getSVNEnvironment().getNewTarget() == null &&
                SVNCommandUtil.isURL((String) targets.get(0)) && 
                SVNCommandUtil.isURL((String) targets.get(1)) &&
                getSVNEnvironment().getStartRevision() == SVNRevision.UNDEFINED &&
                getSVNEnvironment().getEndRevision() == SVNRevision.UNDEFINED) {
            oldTarget = new SVNPath((String) targets.get(0), true);
            newTarget = new SVNPath((String) targets.get(1), true);
            start = oldTarget.getPegRevision();
            end = newTarget.getPegRevision();
            targets.clear();
            if (start == SVNRevision.UNDEFINED) {
                start = SVNRevision.HEAD;
            }
            if (end == SVNRevision.UNDEFINED) {
                end = SVNRevision.HEAD;
            }
        } else if (getSVNEnvironment().getOldTarget() != null) {
            targets.clear();
            targets.add(getSVNEnvironment().getOldTarget());
            targets.add(getSVNEnvironment().getNewTarget() != null ? getSVNEnvironment().getNewTarget() : getSVNEnvironment().getOldTarget());
            
            oldTarget = new SVNPath((String) targets.get(0), true);
            newTarget = new SVNPath((String) targets.get(1), true);
            start = getSVNEnvironment().getStartRevision();
            end = getSVNEnvironment().getEndRevision();
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
            targets = getSVNEnvironment().combineTargets(null, true);
        } else if (getSVNEnvironment().getNewTarget() != null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "'--new' option only valid with '--old' option");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        } else {
            if (targets.isEmpty()) {
                targets.add("");
            }
            oldTarget = new SVNPath("");
            newTarget = new SVNPath("");
            boolean hasURLs = false;
            boolean hasWCs = false;
            
            for(int i = 0; i < targets.size(); i++) {
                SVNPath target = new SVNPath((String) targets.get(i));
                hasURLs |= target.isURL();
                hasWCs |= target.isFile();
            }
            
            if (hasURLs && hasWCs) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Target lists to diff may not contain both working copy paths and URLs");
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            start = getSVNEnvironment().getStartRevision();
            end = getSVNEnvironment().getEndRevision();
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

        SVNDiffClient client = getSVNEnvironment().getClientManager().getDiffClient();
        SVNCommandEnvironment environment = (SVNCommandEnvironment) getEnvironment();
        client.setShowCopiesAsAdds(environment.isShowCopiesAsAdds());
        client.setGitDiffFormat(environment.isGitDiffFormat());
        SvnNewDiffGenerator generator = createDiffGenerator(getSVNEnvironment());
        client.setDiffGenerator(generator);
        
        PrintStream ps = getSVNEnvironment().getOut();
        Collection changeLists = getSVNEnvironment().getChangelistsCollection();
        for(int i = 0; i < targets.size(); i++) {
            String targetName = (String) targets.get(i);
            if (!peggedDiff) {
                SVNPath target1 = new SVNPath(SVNPathUtil.append(oldTarget.getTarget(), targetName));
                SVNPath target2 = new SVNPath(SVNPathUtil.append(newTarget.getTarget(), targetName));
                if (getSVNEnvironment().isSummarize()) {
                    if (target1.isURL() && target2.isURL()) {
                        client.doDiffStatus(target1.getURL(), start, target2.getURL(), end, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), this);
                    } else if (target1.isURL()) {
                        client.doDiffStatus(target1.getURL(), start, target2.getFile(), end, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), this);
                    } else if (target2.isURL()) {
                        client.doDiffStatus(target1.getFile(), start, target2.getURL(), end, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), this);
                    } else {
                        client.doDiffStatus(target1.getFile(), start, target2.getFile(), end, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), this);
                    }
                } else {
                    if (target1.isURL() && target2.isURL()) {
                        client.doDiff(target1.getURL(), start, target2.getURL(), end, 
                                getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), ps);
                    } else if (target1.isURL()) {
                        client.doDiff(target1.getURL(), start, target2.getFile(), end, 
                                getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), ps, 
                                changeLists);
                    } else if (target2.isURL()) {
                        client.doDiff(target1.getFile(), start, target2.getURL(), end, 
                                getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), ps,
                                changeLists);
                    } else {
                        client.doDiff(target1.getFile(), start, target2.getFile(), end, 
                                getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), ps,
                                changeLists);
                    }
                }
            } else {
                SVNPath target = new SVNPath(targetName, true);
                SVNRevision pegRevision = target.getPegRevision();
                if (pegRevision == SVNRevision.UNDEFINED) {
                    pegRevision = target.isURL() ? SVNRevision.HEAD : SVNRevision.WORKING;
                }
                if (getSVNEnvironment().isSummarize()) {
                    if (target.isURL()) {
                        client.doDiffStatus(target.getURL(), start, end, pegRevision, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), this);
                    } else {
                        client.doDiffStatus(target.getFile(), start, end, pegRevision, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), this);
                    }
                } else {
                    if (target.isURL()) {
                        client.doDiff(target.getURL(), pegRevision, start, end, getSVNEnvironment().getDepth(), 
                                getSVNEnvironment().isNoticeAncestry(), ps);
                    } else {
                        client.doDiff(target.getFile(), pegRevision, start, end, getSVNEnvironment().getDepth(), 
                                getSVNEnvironment().isNoticeAncestry(), ps, changeLists);
                    }
                }
            }
        }
        if (getSVNEnvironment().isXML()) {
            StringBuffer buffer = closeXMLTag("paths", null);
            getSVNEnvironment().getOut().print(buffer.toString());
            printXMLFooter("diff");
        }
    }

    static SvnNewDiffGenerator createDiffGenerator(SVNCommandEnvironment svnEnvironment) throws SVNException {
        SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
        if (svnEnvironment.getDiffCommand() != null) {
            diffGenerator.setExternalDiffCommand(svnEnvironment.getDiffCommand());
            diffGenerator.setRawDiffOptions((List<String>) svnEnvironment.getExtensions());
        } else {
            diffGenerator.setDiffOptions(svnEnvironment.getDiffOptions());
        }

        diffGenerator.setDiffDeleted(!svnEnvironment.isNoDiffDeleted());
        diffGenerator.setForcedBinaryDiff(svnEnvironment.isForce());
        diffGenerator.setBasePath(new File("").getAbsoluteFile());
        diffGenerator.setFallbackToAbsolutePath(true);
        diffGenerator.setOptions(svnEnvironment.getOptions());
        diffGenerator.setDiffDeleted(!svnEnvironment.isNoDiffDeleted());
        return new SvnNewDiffGenerator(diffGenerator);
    }

    public void handleDiffStatus(SVNDiffStatus diffStatus) throws SVNException {
        if (diffStatus.getModificationType() == SVNStatusType.STATUS_NONE &&
                !diffStatus.isPropertiesModified()) {
            return;
        }
        String path = diffStatus.getPath();
        
        if (diffStatus.getFile() != null) {
            path = getSVNEnvironment().getRelativePath(diffStatus.getFile());
            path = SVNCommandUtil.getLocalPath(path);
        } else if (diffStatus.getURL() != null) {
            path = diffStatus.getURL().toString();
        } else {
            path = diffStatus.getPath();
            if (!SVNCommandUtil.isURL(path)) {
                path = SVNCommandUtil.getLocalPath(path);
            }
        }
        if (getSVNEnvironment().isXML()) {
            StringBuffer buffer = new StringBuffer();
            Map attrs = new SVNHashMap();
            attrs.put("kind", diffStatus.getKind().toString());
            String modificationKind = "none";
            if (diffStatus.getModificationType() == SVNStatusType.STATUS_MODIFIED) {
                modificationKind = "modified";
            } else if (diffStatus.getModificationType() == SVNStatusType.STATUS_ADDED) {
                modificationKind = "added";
            } else if (diffStatus.getModificationType() == SVNStatusType.STATUS_DELETED) {
                modificationKind = "deleted";
            }
            attrs.put("item", modificationKind);
            attrs.put("props", diffStatus.isPropertiesModified() ? "modified" : "none");
            buffer = openXMLTag("path", SVNXMLUtil.XML_STYLE_PROTECT_CDATA, attrs, buffer);
            buffer.append(SVNEncodingUtil.xmlEncodeCDATA(path));
            buffer = closeXMLTag("path", buffer);
            getSVNEnvironment().getOut().print(buffer.toString());
        } else {
            getSVNEnvironment().getOut().print(diffStatus.getModificationType().getCode() + (diffStatus.isPropertiesModified() ? "M" : " ") + "      " + path + "\n");
            getSVNEnvironment().getOut().flush();
        }
    }

}
