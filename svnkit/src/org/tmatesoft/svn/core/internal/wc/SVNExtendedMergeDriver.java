/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Set;
import java.util.List;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNEntryHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @author TMate Software Ltd.
 * @version 1.2
 * @since 1.2
 */
public abstract class SVNExtendedMergeDriver extends SVNMergeDriver {

    private ISVNExtendedMergeCallback myExtendedMergeCallback;
    private SVNCopyDriver myCopyDriver;
    private SVNURL myPrimaryURL;
    private long myTargetRevision;
    private long myRevision1;
    private long myRevision2;
    private File myTempDirectory;
    private File myReportFile;
    private Set myPendingTargets;
    private SVNMergeInfoInheritance myCurrentInheritance;

    private SVNRepository myRepository;

    public SVNExtendedMergeDriver(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNExtendedMergeDriver(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
        myTargetRevision = -1;
    }

    public ISVNExtendedMergeCallback getExtendedMergeCallback() {
        return myExtendedMergeCallback;
    }

    public void setExtendedMergeCallback(ISVNExtendedMergeCallback extendedMergeCallback) {
        myExtendedMergeCallback = extendedMergeCallback;
    }

    private Set getPendingFiles() {
        if (myPendingTargets == null) {
            myPendingTargets = new SVNHashSet();
        }
        return myPendingTargets;
    }

    private File getReportFile() throws SVNException {
        if (myReportFile == null) {
            myReportFile = SVNFileUtil.createUniqueFile(getTempDirectory(), "svnkit", ".extmerge", false);
        }
        return myReportFile;
    }

    private void deleteReportFile() throws SVNException {
        if (myReportFile != null) {
            SVNFileUtil.deleteFile(myReportFile);
        }
    }

    protected File getTempDirectory() {
        return myTempDirectory;
    }

    protected void setTempDirectory(File tempDirectory) {
        myTempDirectory = tempDirectory;
    }

    private boolean skipExtendedMerge() {
        return myExtendedMergeCallback == null;
    }

    private SVNRepository getRepository(SVNURL url) throws SVNException {
        if (myRepository == null) {
            myRepository = createRepository(url, null, null, false);
        } else {
            myRepository.setLocation(url, false);
        }
        return myRepository;
    }

    protected long getTargetRevision() {
        return myTargetRevision;
    }

    protected SVNCopyDriver getCopyDriver() {
        if (myCopyDriver == null) {
            myCopyDriver = new SVNCopyDriver(getRepositoryPool(), getOptions());
        }
        return myCopyDriver;
    }

    protected void copy(SVNCopySource copySource, File dst) throws SVNException {
        getCopyDriver().setWCAccess(myWCAccess);
        getCopyDriver().setupCopy(new SVNCopySource[]{copySource}, new SVNPath(dst.getAbsolutePath()), false, true, null, null, null, null, null);
        getCopyDriver().setWCAccess(null);
    }

    protected SVNRemoteDiffEditor getMergeReportEditor(long defaultStart, long revision, SVNAdminArea adminArea, SVNMergeCallback mergeCallback, SVNRemoteDiffEditor editor) throws SVNException {
        if (skipExtendedMerge()) {
            return super.getMergeReportEditor(defaultStart, revision, adminArea, mergeCallback, editor);
        }
        if (editor == null) {
            editor = new SVNExtendedMergeEditor(this, getExtendedMergeCallback(), adminArea, adminArea.getRoot(),
                    mergeCallback, myPrimaryURL, myRepository2, defaultStart, revision, myIsDryRun, this, this);
        } else {
            editor.reset(defaultStart, revision);
        }
        File tmp = mergeCallback.createTempDirectory();
        setTempDirectory(tmp);
        return editor;
    }

    protected void addMergeSource(String mergeSource, File target, SVNCopySource targetCopySource) throws SVNException {
        if (getPendingFiles().contains(target)) {
            return;
        }
        getPendingFiles().add(target);

        BufferedWriter writer = createWriter();
        SVNMergeTask mergeTask = new SVNMergeTask(myPrimaryURL.appendPath(mergeSource, false), target, targetCopySource);
        SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "ext merge: " + mergeTask.toString());
        try {
            mergeTask.writeTo(writer);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), SVNLogType.DEFAULT);
        } finally {
            SVNFileUtil.closeFile(writer);
        }
    }

    protected boolean mergeInfoConflicts(File file, SVNURL fileURL) throws SVNException {
        if (!isHonorMergeInfo()) {
            return false;
        }
        SVNEntry entry = myWCAccess.getVersionedEntry(file, false);
        SVNURL sourceRepositoryRoot = myRepository1.getRepositoryRoot(true);
        boolean[] indirect = {false};

        SVNRepository repository = getRepository(entry.getSVNURL());
        Map[] fullMergeInfo = getFullMergeInfo(entry, indirect, SVNMergeInfoInheritance.INHERITED,
                repository, file, Math.max(myRevision1, myRevision2), Math.min(myRevision1, myRevision2));

        Map targetMergeInfo = fullMergeInfo[0];
        Map implicitMergeInfo = fullMergeInfo[1];
        String mergeInfoPath = getPathRelativeToRoot(null, fileURL, sourceRepositoryRoot, null, null);

        Map mergeInfo = implicitMergeInfo;
        SVNMergeRangeList rangeList = null;

        SVNMergeRangeList requestedMerge = new SVNMergeRangeList(myRevision1, myRevision2, true);
        if (myRevision1 > myRevision2) {
            if (targetMergeInfo != null) {
                mergeInfo = SVNMergeInfoUtil.dupMergeInfo(implicitMergeInfo, null);
                SVNMergeInfoUtil.mergeMergeInfos(mergeInfo, targetMergeInfo);
            }
            rangeList = (SVNMergeRangeList) mergeInfo.get(mergeInfoPath);
            if (rangeList != null) {
                requestedMerge = requestedMerge.reverse();
                SVNMergeRangeList rangeIntersection = rangeList.intersect(requestedMerge, false);
                return checkRanges(rangeIntersection);
            } else {
                return false;
            }
        } else {
            if (getOptions().isAllowAllForwardMergesFromSelf()) {
                if (targetMergeInfo != null) {
                    rangeList = (SVNMergeRangeList) targetMergeInfo.get(mergeInfoPath);
                }
            } else {
                if (targetMergeInfo != null) {
                    mergeInfo = SVNMergeInfoUtil.dupMergeInfo(implicitMergeInfo, null);
                    mergeInfo = SVNMergeInfoUtil.mergeMergeInfos(mergeInfo, targetMergeInfo);
                }
                rangeList = (SVNMergeRangeList) mergeInfo.get(mergeInfoPath);
            }
            if (rangeList != null) {
                SVNMergeRangeList diffRanges = requestedMerge.diff(rangeList, false);
                return checkRanges(diffRanges);
            } else {
                return false;
            }
        }
    }

    private boolean checkRanges(SVNMergeRangeList rangesToCheck) {
        SVNMergeRange[] ranges = rangesToCheck.getRanges();
        if (ranges.length == 1) {
            SVNMergeRange range = ranges[0];
            if ((range.getStartRevision() == myRevision1 && range.getEndRevision() == myRevision2) ||
                    (range.getStartRevision() == myRevision2 && range.getEndRevision() == myRevision1)) {
                return false;
            }
        }
        return true;
    }

    protected ISVNEntryHandler getEntryHandler(String mergeSrcPath, SVNURL sourceRootURL, long revision1, long revision2, SVNRepository repository, SVNDepth depth, List childrenWithMergeInfo) {
        if (skipExtendedMerge()) {
            return super.getEntryHandler(mergeSrcPath, sourceRootURL, revision1, revision2, repository, depth, childrenWithMergeInfo);
        }
        return new MergeInfoFetcherExt(mergeSrcPath, sourceRootURL, revision1, revision2, repository, depth, childrenWithMergeInfo);
    }

    protected void doDirectoryMerge(SVNURL url1, long revision1, SVNURL url2, long revision2, SVNEntry parentEntry, SVNAdminArea adminArea, SVNDepth depth) throws SVNException {
        if (depth != SVNDepth.INFINITY && depth != SVNDepth.UNKNOWN) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "''{0}'' depth is not allowed for this kind of merge", depth.getName());
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        myPrimaryURL = revision1 < revision2 ? url1 : url2;
        myRevision1 = revision1;
        myRevision2 = revision2;
        try {
            super.doDirectoryMerge(url1, revision1, url2, revision2, parentEntry, adminArea, depth);
            doAdditionalMerge(url1, revision1, url2, revision2);
        } finally {
            deleteReportFile();
        }
    }

    protected void doAdditionalMerge(SVNURL url1, long revision1, SVNURL url2, long revision2) throws SVNException {
        if (skipExtendedMerge()) {
            return;
        }

        SVNURL oldURL1 = myRepository1.getLocation();
        SVNURL oldURL2 = myRepository2.getLocation();

        boolean isRollback = revision1 > revision2;
        SVNURL primaryURL = !isRollback ? url1 : url2;
        SVNURL secondaryURL = !isRollback ? url2 : url1;

        BufferedReader reader = createReader();
        try {
            SVNMergeTask nextTask = readTask(reader);
            while (nextTask != null) {
                runMergeTask(nextTask, revision1, revision2, isRollback, primaryURL, secondaryURL);
                nextTask = readTask(reader);
            }
        } finally {
            SVNFileUtil.closeFile(reader);
            myRepository1.setLocation(oldURL1, false);
            myRepository2.setLocation(oldURL2, false);
            myRepository = null;
        }
    }

    private void runMergeTask(SVNMergeTask mergeTask, long revision1, long revision2, boolean rollback, SVNURL primaryURL, SVNURL secondaryURL) throws SVNException {
        SVNURL mergeSource = mergeTask.getMergeSource();
        File mergeTarget = mergeTask.getMergeTarget();
        SVNCopySource copySource = mergeTask.getTargetCopySource();
        if (copySource != null) {
            copy(copySource, mergeTarget);
        }

        SVNURL sourceCopiedFrom = getExtendedMergeCallback().transformLocation(mergeSource, Math.max(revision1, revision2), Math.min(revision1, revision2));
        SVNURL fileURL1;
        SVNURL fileURL2;
        if (sourceCopiedFrom != null) {
            fileURL1 = sourceCopiedFrom;
            fileURL2 = mergeSource;
        } else {
            fileURL1 = mergeSource;
            String relativePath = SVNPathUtil.getRelativePath(primaryURL.getPath(), mergeSource.getPath());
            fileURL2 = secondaryURL.appendPath(relativePath, false);
        }

        SVNURL mergeURL1 = !rollback ? fileURL1 : fileURL2;
        SVNURL mergeURL2 = !rollback ? fileURL2 : fileURL1;
        myRepository1.setLocation(mergeURL1, false);
        myRepository2.setLocation(mergeURL2, false);
        SVNAdminArea targetArea = retrieve(myWCAccess, mergeTarget.getParentFile());

        myCurrentInheritance = copySource != null ? SVNMergeInfoInheritance.EXPLICIT : null;
        doFileMerge(mergeURL1, revision1, mergeURL2, revision2, mergeTarget, targetArea, true);
        myCurrentInheritance = null;
    }

    protected Map[] getFullMergeInfo(SVNEntry entry, boolean[] indirect, SVNMergeInfoInheritance inherit, SVNRepository repos, File target, long start, long end) throws SVNException {
        if (myCurrentInheritance != null) {
            inherit = myCurrentInheritance;
        }
        return super.getFullMergeInfo(entry, indirect, inherit, repos, target, start, end);
    }

    private SVNRevision processLocalRevision(SVNRevision revision) {
        if (revision == SVNRevision.WORKING || revision == SVNRevision.BASE) {
            return SVNRevision.create(myTargetRevision);
        }
        return revision;
    }

    private static SVNAdminArea retrieve(SVNWCAccess access, File target) throws SVNException {
        SVNAdminArea area = access.getAdminArea(target);
        if (area == null) {
            area = access.probeTry(target, true, 0);
        }
        return area;
    }

    protected SVNMergeTask readTask(BufferedReader reader) throws SVNException {
        SVNURL sourceURL = null;
        File target = null;
        SVNCopySource copySource = null;

        try {
            String sourceLine = reader.readLine();

            if (sourceLine == null) {
                return null;
            }
            sourceURL = SVNURL.parseURIEncoded(sourceLine);
            target = new File(reader.readLine());

            String path = reader.readLine();
            SVNRevision pegRevision = SVNRevision.parse(reader.readLine());
            SVNRevision revision = SVNRevision.parse(reader.readLine());
            revision = processLocalRevision(revision);
            reader.readLine();

            if (path.length() == 0) {
                copySource = null;
            } else if (SVNPathUtil.isURL(path)) {
                copySource = new SVNCopySource(pegRevision, revision, SVNURL.parseURIEncoded(path));
            } else {
                copySource = new SVNCopySource(pegRevision, revision, new File(path));
            }
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), SVNLogType.DEFAULT);
        }
        return new SVNMergeTask(sourceURL, target, copySource);
    }

    private BufferedReader createReader() throws SVNException {
        try {
            return new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(getReportFile()), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(getReportFile())));
        }
    }

    private BufferedWriter createWriter() throws SVNException {
        try {
            return new BufferedWriter(new OutputStreamWriter(new SVNCancellableOutputStream(SVNFileUtil.openFileForWriting(getReportFile(), true), getEventDispatcher()), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return new BufferedWriter(new OutputStreamWriter(SVNFileUtil.openFileForWriting(getReportFile(), true)));
        }
    }

    private class SVNMergeTask {

        private SVNURL myMergeSource;
        private File myMergeTarget;
        private SVNCopySource myTargetCopySource;

        protected SVNMergeTask(SVNURL mergeSource, File mergeTarget, SVNCopySource targetCopySource) {
            myMergeSource = mergeSource;
            myMergeTarget = mergeTarget;
            myTargetCopySource = targetCopySource;
        }

        protected SVNURL getMergeSource() {
            return myMergeSource;
        }

        protected File getMergeTarget() {
            return myMergeTarget;
        }

        protected SVNCopySource getTargetCopySource() {
            return myTargetCopySource;
        }

        protected void writeTo(BufferedWriter writer) throws IOException {
            writer.write(getMergeSource().toString());
            writer.newLine();

            writer.write(getMergeTarget().getAbsolutePath());
            writer.newLine();

            SVNCopySource source = getTargetCopySource();
            if (source != null) {
                String path = source.getURL() == null ? source.getFile().getAbsolutePath() : source.getURL().toString();
                writer.write(path);
            }
            writer.newLine();
            if (source != null) {
                SVNRevision pegRevision = source.getPegRevision() == null ? SVNRevision.UNDEFINED : source.getPegRevision();
                writer.write(pegRevision.toString());
            }
            writer.newLine();
            if (source != null) {
                SVNRevision revision = source.getRevision() == null ? SVNRevision.UNDEFINED : source.getRevision();
                writer.write(revision.toString());
            }
            writer.newLine();
            writer.newLine();
        }

        public String toString() {
            StringBuffer buffer = new StringBuffer();
            buffer.append("merge task: source ");
            buffer.append(myMergeSource);
            buffer.append(" target ");
            buffer.append(myTarget);
            buffer.append(" copy source ");
            if (myTargetCopySource != null) {
                buffer.append(myTargetCopySource.isURL() ? myTargetCopySource.getURL().toString() : myTargetCopySource.getFile().getAbsolutePath());
                buffer.append("@");
                buffer.append(myTargetCopySource.getPegRevision().toString());
                buffer.append(" revision ");
                buffer.append(myTargetCopySource.getRevision());
            } else {
                buffer.append("[NULL]");
            }
            return buffer.toString();
        }
    }

    private class MergeInfoFetcherExt extends MergeInfoFetcher {

        private MergeInfoFetcherExt(String mergeSrcPath, SVNURL sourceRootURL, long revision1, long revision2, SVNRepository repository, SVNDepth depth, List childrenWithMergeInfo) {
            super(mergeSrcPath, sourceRootURL, revision1, revision2, repository, depth, childrenWithMergeInfo);
        }

        public void handleEntry(File path, SVNEntry entry) throws SVNException {
            if (path.equals(myTarget)) {
                myTargetRevision = entry.getRevision();
                super.handleEntry(path, entry);
                return;
            }

            if (entry.isScheduledForAddition() || entry.isScheduledForDeletion() || entry.isScheduledForReplacement()) {
                super.handleEntry(path, entry);
                return;
            }

            SVNDepth entryDepth = entry.getDepth();
            if (entryDepth != SVNDepth.INFINITY && entryDepth != SVNDepth.UNKNOWN) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "''{0}'' entry has ''{1}'' depth. Sparse working copy is not allowed for this kind of merge", new Object[]{path, entryDepth.getName()});
                SVNErrorManager.error(error, SVNLogType.WC);
            }

            long entryRevision = entry.getRevision();
            if (entryRevision != -1 && myTargetRevision != entry.getRevision()) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Mixed revision working copy is not allowed for this kind of merge. Please run update on the working copy root");
                SVNErrorManager.error(error, SVNLogType.WC);
            }

            SVNURL entryURL = entry.getSVNURL();
            if (entryURL != null) {
                SVNEntry parentEntry = myWCAccess.getVersionedEntry(path.getParentFile(), false);
                SVNURL expectedURL = parentEntry.getSVNURL().appendPath(SVNPathUtil.tail(path.getAbsolutePath().replace(File.separatorChar, '/')), false);
                if (!entryURL.equals(expectedURL)) {
                    SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "''{0}'' is switched entry which is not allowed for this kind of merge", path);
                    SVNErrorManager.error(error, SVNLogType.WC);
                }
            }
            super.handleEntry(path, entry);
        }

        public void handleError(File path, SVNErrorMessage error) throws SVNException {
            super.handleError(path, error);
        }
    }
}
