/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNDiffEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNMergeEditor;
import org.tmatesoft.svn.core.internal.wc.SVNMerger;
import org.tmatesoft.svn.core.internal.wc.SVNRemoteDiffEditor;
import org.tmatesoft.svn.core.internal.wc.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

import java.io.File;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNDiffClient extends SVNBasicClient {

    private ISVNDiffGenerator myDiffGenerator;

    protected SVNDiffClient(ISVNRepositoryFactory repositoryFactory, ISVNOptions options) {
        super(repositoryFactory, options);
    }

    public void setDiffGenerator(ISVNDiffGenerator diffGenerator) {
        myDiffGenerator = diffGenerator;
    }

    public ISVNDiffGenerator getDiffGenerator() {
        if (myDiffGenerator == null) {
            myDiffGenerator = new DefaultSVNDiffGenerator();
        }
        return myDiffGenerator;
    }

    public void doDiff(File path, boolean recursive, final boolean useAncestry,
            final OutputStream result) throws SVNException {
        doDiff(path, SVNRevision.BASE, SVNRevision.WORKING, recursive,
                useAncestry, result);
    }

    public void doDiff(String url, SVNRevision pegRevision, File path,
            SVNRevision rN, SVNRevision rM, boolean recursive,
            final boolean useAncestry, final OutputStream result)
            throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.create(path);
        String rootPath = wcAccess.getAnchor().getRoot().getAbsolutePath();
        getDiffGenerator().init(rootPath, rootPath);
        if (rM == SVNRevision.BASE || rM == SVNRevision.WORKING
                || !rM.isValid()) {
            // URL->WC diff.
            String wcURL = wcAccess.getAnchor().getEntries().getEntry("", true)
                    .getURL();
            String target = "".equals(wcAccess.getTargetName()) ? null
                    : wcAccess.getTargetName();

            SVNRepository repos = createRepository(wcURL);
            wcAccess.open(true, recursive);
            SVNReporter reporter = new SVNReporter(wcAccess, false, recursive);

            SVNDiffEditor editor = new SVNDiffEditor(wcAccess,
                    getDiffGenerator(), useAncestry, false /* reverse */,
                    rM == SVNRevision.BASE /* compare to base */, result);
            if (rN == null || !rN.isValid()) {
                rN = SVNRevision.HEAD;
            }
            long revN = getRevisionNumber(url, rN);
            try {
                repos.diff(url, revN, target, !useAncestry, recursive,
                        reporter, editor);
            } finally {
                wcAccess.close(true);
            }
        } else {
            // URL:URL diff
            String url2;
            SVNRevision rev2;
            try {
                url2 = wcAccess.getTargetEntryProperty(SVNProperty.URL);
                rev2 = SVNRevision.parse(wcAccess
                        .getTargetEntryProperty(SVNProperty.REVISION));
            } finally {
                wcAccess.close(true);
            }
            getDiffGenerator().setBasePath(wcAccess.getAnchor().getRoot());
            doDiff(url, pegRevision, url2, rev2, rN, rM, recursive,
                    useAncestry, result);
        }
    }

    public void doDiff(File path, String url, SVNRevision pegRevision,
            SVNRevision rN, SVNRevision rM, boolean recursive,
            final boolean useAncestry, final OutputStream result)
            throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.create(path);

        String rootPath = wcAccess.getAnchor().getRoot().getAbsolutePath();
        getDiffGenerator().init(rootPath, rootPath);
        if (rN == SVNRevision.BASE || rN == SVNRevision.WORKING
                || !rN.isValid()) {
            // URL->WC diff.
            String wcURL = wcAccess.getAnchor().getEntries().getEntry("", true)
                    .getURL();
            String target = "".equals(wcAccess.getTargetName()) ? null
                    : wcAccess.getTargetName();

            SVNRepository repos = createRepository(wcURL);
            wcAccess.open(true, recursive);
            SVNReporter reporter = new SVNReporter(wcAccess, false, recursive);

            SVNDiffEditor editor = new SVNDiffEditor(wcAccess,
                    getDiffGenerator(), useAncestry, true /* reverse */,
                    rM == SVNRevision.BASE /* compare to base */, result);
            if (rM == null || !rM.isValid()) {
                rM = SVNRevision.HEAD;
            }
            long revM = getRevisionNumber(url, rM);
            try {
                repos.diff(url, revM, target, !useAncestry, recursive,
                        reporter, editor);
            } finally {
                wcAccess.close(true);
            }
        } else {
            String url1;
            SVNRevision rev1;
            try {
                url1 = wcAccess.getTargetEntryProperty(SVNProperty.URL);
                rev1 = SVNRevision.parse(wcAccess
                        .getTargetEntryProperty(SVNProperty.REVISION));
            } finally {
                wcAccess.close(true);
            }
            getDiffGenerator().setBasePath(wcAccess.getAnchor().getRoot());
            doDiff(url1, rev1, url, pegRevision, rN, rM, recursive,
                    useAncestry, result);
        }
    }

    public void doDiff(File path, File path2, SVNRevision rN, SVNRevision rM,
            boolean recursive, final boolean useAncestry,
            final OutputStream result) throws SVNException {
        rN = rN == null ? SVNRevision.UNDEFINED : rN;
        rM = rM == null ? SVNRevision.UNDEFINED : rM;
        if (path.equals(path2)) {
            if (rN == SVNRevision.WORKING || rN == SVNRevision.BASE
                    || rM == SVNRevision.WORKING || rM == SVNRevision.BASE
                    || (!rM.isValid() && !rN.isValid())) {
                doDiff(path, rN, rM, recursive, useAncestry, result);
            } else {
                // do not use pegs.
                SVNWCAccess wcAccess = createWCAccess(path);
                String url = wcAccess.getTargetEntryProperty(SVNProperty.URL);
                long revN = getRevisionNumber(path, rN);
                long revM = getRevisionNumber(path, rM);
                getDiffGenerator().setBasePath(wcAccess.getAnchor().getRoot());
                doDiff(url, SVNRevision.UNDEFINED, url, SVNRevision.UNDEFINED,
                        SVNRevision.create(revN), SVNRevision.create(revM),
                        recursive, useAncestry, result);
            }
            return;
        }
        if (rN == SVNRevision.UNDEFINED) {
            rN = SVNRevision.HEAD;
        }
        if (rM == SVNRevision.UNDEFINED) {
            rM = SVNRevision.HEAD;
        }
        SVNWCAccess wcAccess = SVNWCAccess.create(path);
        String url1;
        try {
            url1 = wcAccess.getTargetEntryProperty(SVNProperty.URL);
        } finally {
            wcAccess.close(true);
        }

        SVNWCAccess wcAccess2 = SVNWCAccess.create(path2);
        String rootPath = wcAccess2.getAnchor().getRoot().getAbsolutePath();
        getDiffGenerator().init(rootPath, rootPath);
        getDiffGenerator().setBasePath(wcAccess2.getAnchor().getRoot());
        String url2;
        try {
            url2 = wcAccess2.getTargetEntryProperty(SVNProperty.URL);
        } finally {
            wcAccess.close(true);
        }
        long revN = getRevisionNumber(path, rN);
        long revM = getRevisionNumber(path, rM);
        doDiff(url1, SVNRevision.UNDEFINED, url2, SVNRevision.UNDEFINED,
                SVNRevision.create(revN), SVNRevision.create(revM), recursive,
                useAncestry, result);
    }

    public void doDiff(String url1, SVNRevision pegRevision1, String url2,
            SVNRevision pegRevision2, SVNRevision rN, SVNRevision rM,
            boolean recursive, final boolean useAncestry,
            final OutputStream result) throws SVNException {
        DebugLog.log("diff: -r" + rN + ":" + rM + " " + url1 + "@"
                + pegRevision1 + "  " + url2 + "@" + pegRevision2);
        if (rN == null || !rN.isValid()) {
            rN = pegRevision1;
        }
        if (rM == null || !rM.isValid()) {
            rM = pegRevision2;
        }
        rN = rN == null || !rN.isValid() ? SVNRevision.HEAD : rN;
        rM = rM == null || !rM.isValid() ? SVNRevision.HEAD : rM;
        if (rN != SVNRevision.HEAD && rN.getNumber() < 0
                && rN.getDate() == null) {
            SVNErrorManager.error("svn: invalid revision: '" + rN + "'");
        }
        if (rM != SVNRevision.HEAD && rM.getNumber() < 0
                && rM.getDate() == null) {
            SVNErrorManager.error("svn: invalid revision: '" + rM + "'");
        }
        url1 = validateURL(url1);
        url2 = validateURL(url2);

        pegRevision1 = pegRevision1 == null ? SVNRevision.UNDEFINED
                : pegRevision1;
        pegRevision2 = pegRevision2 == null ? SVNRevision.UNDEFINED
                : pegRevision2;

        url1 = getURL(url1, pegRevision1, rN);
        url2 = getURL(url2, pegRevision2, rM);

        final long revN = getRevisionNumber(url1, rN);
        final long revM = getRevisionNumber(url2, rM);

        SVNRepository repos = createRepository(url1);
        SVNNodeKind nodeKind = repos.checkPath("", revN);
        if (nodeKind == SVNNodeKind.NONE) {
            SVNErrorManager.error("svn: '" + url1
                    + "' was not found in the repository at revision " + revN);
        }
        SVNRepository repos2 = createRepository(url2);
        SVNNodeKind nodeKind2 = repos2.checkPath("", revM);
        if (nodeKind2 == SVNNodeKind.NONE) {
            SVNErrorManager.error("svn: '" + url2
                    + "' was not found in the repository at revision " + revM);
        }
        String target = null;
        if (nodeKind == SVNNodeKind.FILE || nodeKind2 == SVNNodeKind.FILE) {
            target = PathUtil.tail(url1);
            target = PathUtil.decode(target);
            url1 = PathUtil.removeTail(url1);
            repos = createRepository(url1);
        }
        File tmpFile = getDiffGenerator().createTempDirectory();
        try {
            SVNRemoteDiffEditor editor = new SVNRemoteDiffEditor(tmpFile,
                    getDiffGenerator(), repos, revN, result);
            ISVNReporterBaton reporter = new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, revN, false);
                    reporter.finishReport();
                }
            };
            repos = createRepository(url1);
            repos.diff(url2, revM, revN, target, !useAncestry, recursive,
                    reporter, editor);
        } finally {
            if (tmpFile != null) {
                SVNFileUtil.deleteAll(tmpFile);
            }
        }
    }

    public void doDiff(File path, SVNRevision rN, SVNRevision rM,
            boolean recursive, final boolean useAncestry,
            final OutputStream result) throws SVNException {
        if (rN == null || rN == SVNRevision.UNDEFINED) {
            rN = SVNRevision.BASE;
        }
        if (rM == null || rM == SVNRevision.UNDEFINED) {
            rM = SVNRevision.WORKING;
        }
        // cases:
        // 1.1 wc-wc: BASE->WORKING
        // 1.2 wc-wc: WORKING->BASE (reversed to 1.1)

        // 2.1 wc-url: BASE:REV
        // 2.2 wc-url: WORKING:REV
        // 2.3 wc-url: REV:BASE (reversed to 2.1)
        // 2.4 wc-url: REV:WORKING (reversed to 2.2)

        // 3.1 url-url: REV:REV

        // path should always point to valid wc dir or file.
        // for 'REV' revisions there could be also 'peg revision' defined, used
        // to get real WC url.

        SVNWCAccess wcAccess = createWCAccess(path);
        wcAccess.open(true, recursive);
        try {
            File originalPath = path;
            path = wcAccess.getAnchor().getRoot().getAbsoluteFile();
            getDiffGenerator().init(path.getAbsolutePath(),
                    path.getAbsolutePath());
            if (rN == SVNRevision.BASE && rM == SVNRevision.WORKING) {
                // case 1.1
                if (!"".equals(wcAccess.getTargetName())) {
                    if (wcAccess.getAnchor().getEntries().getEntry(
                            wcAccess.getTargetName(), true) == null) {
                        SVNErrorManager.error("svn: path '"
                                + originalPath.getAbsolutePath()
                                + "' is not under version control");
                    }
                }
                SVNDiffEditor editor = new SVNDiffEditor(wcAccess,
                        getDiffGenerator(), useAncestry, false, false, result);
                editor.closeEdit();
            } else if (rN == SVNRevision.WORKING && rM == SVNRevision.BASE) {
                // case 1.2 (not supported)
                SVNErrorManager
                        .error("svn: not supported diff revisions range: '"
                                + rN + ":" + rM + "'");
            } else if (rN == SVNRevision.WORKING || rN == SVNRevision.BASE) {
                // cases 2.1, 2.2
                doWCReposDiff(wcAccess, rM, rN, true, recursive, useAncestry,
                        result);
            } else if (rM == SVNRevision.WORKING || rM == SVNRevision.BASE) {
                // cases 2.3, 2.4
                doWCReposDiff(wcAccess, rN, rM, false, recursive, useAncestry,
                        result);
            } else {
                // rev:rev
                long revN = getRevisionNumber(path, rN);
                long revM = getRevisionNumber(path, rM);

                String url = wcAccess.getTargetEntryProperty(SVNProperty.URL);
                if (wcAccess.getTargetEntryProperty(SVNProperty.COPYFROM_URL) != null) {
                    url = wcAccess
                            .getTargetEntryProperty(SVNProperty.COPYFROM_URL);
                }
                SVNRevision pegRev = SVNRevision.parse(wcAccess
                        .getTargetEntryProperty(SVNProperty.REVISION));
                getDiffGenerator().setBasePath(wcAccess.getTarget().getRoot());
                doDiff(url, pegRev, url, pegRev, SVNRevision.create(revN),
                        SVNRevision.create(revM), recursive, useAncestry,
                        result);
            }
        } finally {
            wcAccess.close(true);
        }
    }

    public void doMerge(String url1, String url2, SVNRevision rN,
            SVNRevision rM, File dstPath, boolean recursive,
            boolean useAncestry, boolean force, boolean dryRun)
            throws SVNException {
        // create merge editor that will receive diffs between url1 and url2
        // (rN|rM)
        url1 = validateURL(url1);
        url2 = validateURL(url2);
        DebugLog.log("url1: " + url1);
        DebugLog.log("url2: " + url2);
        rN = rN == null || !rN.isValid() ? SVNRevision.HEAD : rN;
        rM = rM == null || !rM.isValid() ? SVNRevision.HEAD : rM;
        SVNWCAccess wcAccess = createWCAccess(dstPath);
        try {
            wcAccess.open(true, recursive);
            SVNRepository repos1 = createRepository(url1);
            SVNRepository repos2 = createRepository(url1);
            final long revN = getRevisionNumber(url1, rN);
            long revM = getRevisionNumber(url2, rM);
            SVNRepository repos3 = createRepository(url2);

            if (SVNProperty.KIND_FILE.equals(wcAccess
                    .getTargetEntryProperty(SVNProperty.KIND))) {
                SVNMerger merger = new SVNMerger(wcAccess, url2, revM, force,
                        dryRun, isLeaveConflictsUnresolved());
                mergeSingleFile(wcAccess, repos1, repos3, revN, revM, merger);
                return;
            }

            SVNNodeKind nodeKind1 = repos1.checkPath("", revN);
            SVNNodeKind nodeKind2 = repos3.checkPath("", revM);

            String target = null;
            if (nodeKind1 == SVNNodeKind.FILE || nodeKind2 == SVNNodeKind.FILE) {
                DebugLog.log("one of the targets is file");
                target = PathUtil.tail(url1);
                target = PathUtil.decode(target);
                url1 = PathUtil.removeTail(url1);
                repos1 = createRepository(url1);
                repos2 = createRepository(url1);
            }
            url2 = PathUtil.decode(url2);

            SVNMerger merger = new SVNMerger(wcAccess, url2, revM, force,
                    dryRun, isLeaveConflictsUnresolved());
            DebugLog.log("wc access: " + wcAccess);
            DebugLog.log("url1: " + url1);
            DebugLog.log("url2: " + url2);
            DebugLog.log("revM: " + revM);
            DebugLog.log("revN: " + revN);
            DebugLog.log("target: " + target);
            SVNMergeEditor mergeEditor = new SVNMergeEditor(wcAccess, repos2,
                    revN, revM, merger);
            repos1.diff(url2, revM, revN, target, !useAncestry, recursive,
                    new ISVNReporterBaton() {
                        public void report(ISVNReporter reporter)
                                throws SVNException {
                            reporter.setPath("", null, revN, false);
                            reporter.finishReport();
                        }
                    }, mergeEditor);
        } finally {
            wcAccess.close(true);
        }
    }

    public void doMerge(File path1, File path2, SVNRevision rN, SVNRevision rM,
            File dstPath, boolean recursive, boolean useAncestry,
            boolean force, boolean dryRun) throws SVNException {

        SVNWCAccess wcAccess = createWCAccess(path1);
        String url1 = null;
        try {
            wcAccess.open(true, false);
            url1 = wcAccess.getTargetEntryProperty(SVNProperty.URL);
            if (url1 == null) {
                SVNErrorManager.error("svn: '" + path1.getAbsolutePath()
                        + "' has no URL");
            }
            SVNRevision revision = SVNRevision.parse(wcAccess
                    .getTargetEntryProperty(SVNProperty.REVISION));
            // url as it at revision N
            url1 = getURL(url1, revision, rN);
        } finally {
            wcAccess.close(true);
        }
        wcAccess = createWCAccess(path2);
        String url2 = null;
        try {
            wcAccess.open(true, false);
            url2 = wcAccess.getTargetEntryProperty(SVNProperty.URL);
            if (url2 == null) {
                SVNErrorManager.error("svn: '" + path2.getAbsolutePath()
                        + "' has no URL");
            }
            SVNRevision revision = SVNRevision.parse(wcAccess
                    .getTargetEntryProperty(SVNProperty.REVISION));
            // url as it at revision N
            url2 = getURL(url2, revision, rM);
        } finally {
            wcAccess.close(true);
        }
        doMerge(url1, url2, rN, rM, dstPath, recursive, useAncestry, force,
                dryRun);
    }

    public void doMerge(File path, SVNRevision pegRev, SVNRevision rN,
            SVNRevision rM, File dstPath, boolean recursive,
            boolean useAncestry, boolean force, boolean dryRun)
            throws SVNException {
        // get URL for file at rev pegRev.
        SVNWCAccess wcAccess = createWCAccess(path);
        String url = null;
        try {
            wcAccess.open(true, false);
            url = wcAccess.getTargetEntryProperty(SVNProperty.URL);
            if (url == null) {
                SVNErrorManager.error("svn: '" + path.getAbsolutePath()
                        + "' has no URL");
            }
            SVNRevision revision = SVNRevision.parse(wcAccess
                    .getTargetEntryProperty(SVNProperty.REVISION));
            // here we have URL at wc revision,
            // now get it as at peg revision.
            if (pegRev == null || !pegRev.isValid()) {
                pegRev = SVNRevision.HEAD;
            }
            url = getURL(url, revision, pegRev);
        } finally {
            wcAccess.close(true);
        }
        if (url != null) {
            // we have url as it is at pegrev, later will change it to ones at
            // rN:rM
            doMerge(url, pegRev, rN, rM, dstPath, recursive, useAncestry,
                    force, dryRun);
        }
    }

    public void doMerge(String url, SVNRevision pegRev, SVNRevision rN,
            SVNRevision rM, File dstPath, boolean recursive,
            boolean useAncestry, boolean force, boolean dryRun)
            throws SVNException {
        url = validateURL(url);
        String url1 = getURL(url, pegRev, rN);
        String url2 = getURL(url, pegRev, rM);
        // if url is a file and no
        doMerge(url1, url2, rN, rM, dstPath, recursive, useAncestry, force,
                dryRun);
    }

    private void doWCReposDiff(SVNWCAccess wcAccess, SVNRevision reposRev,
            SVNRevision localRev, boolean reverse, boolean recursive,
            boolean useAncestry, OutputStream result) throws SVNException {
        // get wc url and revision
        String url = wcAccess.getAnchor().getEntries().getEntry("", true)
                .getURL();
        String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess
                .getTargetName();
        SVNRevision wcRevNumber = SVNRevision.parse(wcAccess
                .getTargetEntryProperty(SVNProperty.REVISION));

        SVNRepository repos = createRepository(url);
        SVNReporter reporter = new SVNReporter(wcAccess, false, recursive);

        SVNDiffEditor editor = new SVNDiffEditor(wcAccess, getDiffGenerator(),
                useAncestry, reverse /* reverse */,
                localRev == SVNRevision.BASE /* compare to base */, result);

        // get target url and revision, fetch target url location at desired
        // target revision
        long revNumber = getRevisionNumber(url, reposRev);
        String targetURL = wcAccess.getTargetEntryProperty(SVNProperty.URL);
        if (wcAccess.getTargetEntryProperty(SVNProperty.COPYFROM_URL) != null) {
            targetURL = wcAccess
                    .getTargetEntryProperty(SVNProperty.COPYFROM_URL);
        }
        targetURL = getURL(targetURL, wcRevNumber, SVNRevision
                .create(revNumber));
        targetURL = PathUtil.decode(targetURL);
        repos.diff(targetURL, revNumber, wcRevNumber.getNumber(), target,
                !useAncestry, recursive, reporter, editor);
    }

    private void mergeSingleFile(SVNWCAccess wcAccess, SVNRepository repos1,
            SVNRepository repos2, long revN, long revM, SVNMerger merger)
            throws SVNException {
        String name = wcAccess.getTargetName();
        File tmpFile1 = wcAccess.getAnchor().getBaseFile(name, true);
        File tmpFile2 = SVNFileUtil.createUniqueFile(tmpFile1.getParentFile(),
                name, ".tmp");
        Map props1 = new HashMap();
        Map props2 = new HashMap();
        OutputStream os1 = SVNFileUtil.openFileForWriting(tmpFile1);
        OutputStream os2 = SVNFileUtil.openFileForWriting(tmpFile2);
        try {
            repos1.getFile("", revN, props1, os1);
            repos2.getFile("", revM, props2, os2);
        } finally {
            SVNFileUtil.closeFile(os1);
            SVNFileUtil.closeFile(os2);
        }
        String mimeType1 = (String) props1.get(SVNProperty.MIME_TYPE);
        String mimeType2 = (String) props2.get(SVNProperty.MIME_TYPE);

        props1 = filterProperties(props1, true, false, false);
        props2 = filterProperties(props2, true, false, false);
        Map propsDiff = computePropsDiff(props1, props2);
        DebugLog.log("base props: " + props1);
        DebugLog.log("target props: " + props2);
        DebugLog.log("props diff: " + propsDiff);
        SVNStatusType[] mergeResult = merger.fileChanged(wcAccess
                .getTargetName(), tmpFile1, tmpFile2, revN, revM, mimeType1,
                mimeType2, props1, propsDiff);
        handleEvent(SVNEventFactory.createUpdateModifiedEvent(wcAccess,
                wcAccess.getAnchor(), name, SVNNodeKind.FILE,
                SVNEventAction.UPDATE_UPDATE, mimeType2, mergeResult[0],
                mergeResult[1], SVNStatusType.LOCK_INAPPLICABLE),
                ISVNEventHandler.UNKNOWN);
    }

    private static Map computePropsDiff(Map props1, Map props2) {
        Map propsDiff = new HashMap();
        for (Iterator names = props2.keySet().iterator(); names.hasNext();) {
            String newPropName = (String) names.next();
            if (props1.containsKey(newPropName)) {
                // changed.
                Object oldValue = props2.get(newPropName);
                if (!oldValue.equals(props1.get(newPropName))) {
                    propsDiff.put(newPropName, props2.get(newPropName));
                }
            } else {
                // added.
                propsDiff.put(newPropName, props2.get(newPropName));
            }
        }
        for (Iterator names = props1.keySet().iterator(); names.hasNext();) {
            String oldPropName = (String) names.next();
            if (!props2.containsKey(oldPropName)) {
                // deleted
                propsDiff.put(oldPropName, null);
            }
        }
        return propsDiff;
    }

    private static Map filterProperties(Map props1, boolean leftRegular,
            boolean leftEntry, boolean leftWC) {
        Map result = new HashMap();
        for (Iterator names = props1.keySet().iterator(); names.hasNext();) {
            String propName = (String) names.next();
            if (!leftEntry && propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                continue;
            }
            if (!leftWC && propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                continue;
            }
            if (!leftRegular
                    && !(propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX) || propName
                            .startsWith(SVNProperty.SVN_WC_PREFIX))) {
                continue;
            }
            result.put(propName, props1.get(propName));
        }
        return result;
    }
}