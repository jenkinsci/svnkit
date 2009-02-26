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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNEntryHandler;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2
 * @since 1.2
 */
public abstract class SVNExtendedMergeDriver extends SVNMergeDriver {

    private ISVNExtendedMergeCallback myExtendedMergeCallback;
    private SVNCopyDriver myCopyDriver;
    private SVNURL myPrimaryURL;
    private long myRevision1;
    private long myRevision2;
    private File myTempDirectory;
    private File myReportFile;
    private Set myPendingTargets;
    private SVNMergeRangeList myCurrentRemainingRanges;
    private Boolean myRecordMergeInfo;

    private SVNRepository myRepository;

    public SVNExtendedMergeDriver(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNExtendedMergeDriver(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
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
            try {
                SVNFileUtil.deleteFile(myReportFile);
            } finally {
                myReportFile = null;
            }
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
        if (url == null) {
            return null;
        }
        if (myRepository == null) {
            myRepository = createRepository(url, null, null, false);
        } else {
            myRepository.setLocation(url, false);
        }
        return myRepository;
    }

    protected SVNCopyDriver getCopyDriver() {
        if (myCopyDriver == null) {
            myCopyDriver = new SVNCopyDriver(getRepositoryPool(), getOptions());
            myCopyDriver.setWCAccess(myWCAccess);
        }
        return myCopyDriver;
    }

    protected void copy(SVNCopySource copySource, File dst, boolean save) throws SVNException {
        if (copySource == null || dst == null) {
            return;
        }
        SVNEntry entry = myWCAccess.getEntry(dst, false);
        if (entry != null) {
            doVirtualCopy(entry, copySource, save);
        } else {
            getCopyDriver().setupCopy(new SVNCopySource[]{copySource}, new SVNPath(dst.getAbsolutePath()), false, true, null, null, null, null, null);
        }
    }

    protected void doVirtualCopy(SVNEntry dstEntry, SVNCopySource copySource, boolean save) throws SVNException {
        dstEntry.setCopyFromURL(copySource.getURL().toString());
        long cfRevision = copySource.getRevision().getNumber();
        dstEntry.setCopyFromRevision(cfRevision);
        if (save) {
            SVNAdminArea dir = dstEntry.getAdminArea();
            dir.saveEntries(false);
        }
    }

    protected long getRevision(SVNCopySource copySource) throws SVNException {
        return getRevisionNumber(copySource.getRevision(), getRepository(copySource.getURL()), copySource.getFile());
    }

    protected SVNRemoteDiffEditor getMergeReportEditor(long defaultStart, long revision, SVNAdminArea adminArea, SVNDepth depth, SVNMergeCallback mergeCallback, SVNRemoteDiffEditor editor) throws SVNException {
        if (skipExtendedMerge()) {
            return super.getMergeReportEditor(defaultStart, revision, adminArea, depth, mergeCallback, editor);
        }
        if (editor == null) {
            editor = new SVNExtendedMergeEditor(this, getExtendedMergeCallback(), adminArea, adminArea.getRoot(),
                    mergeCallback, myPrimaryURL, myRepository2, defaultStart, revision, myIsDryRun, depth, this, this);
        } else {
            editor.reset(defaultStart, revision);
        }
        File tmp = mergeCallback.createTempDirectory();
        setTempDirectory(tmp);
        return editor;
    }

    protected void addMergeSource(String mergeSource, SVNURL[] mergeSources, File target, SVNMergeRangeList remainingRanges, boolean adjustMergeInfo, SVNCopySource targetCopySource) throws SVNException {
        if (getPendingFiles().contains(target)) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "ext merge: skip new additional target " + target.getAbsolutePath());
            return;
        }
        getPendingFiles().add(target);

        SVNURL sourceURL = myPrimaryURL.appendPath(mergeSource, false);
        mergeSources = getMergeSources(sourceURL, mergeSources);
        SVNURL url1 = mergeSources[0];
        SVNURL url2 = mergeSources[1];

        BufferedWriter writer = createWriter();
        SVNMergeTask mergeTask = new SVNMergeTask(url1, url2, target, remainingRanges, adjustMergeInfo, targetCopySource);
        SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "ext merge: " + mergeTask.toString());
        try {
            mergeTask.writeTo(writer);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), SVNLogType.DEFAULT);
        } finally {
            SVNFileUtil.closeFile(writer);
        }
    }

    protected boolean mergeInfoConflicts(SVNMergeRangeList rangeList, File path) {
        if (rangeList == null) {
            return false;
        }
        rangeList = rangeList.compactMergeRanges();
        SVNMergeRange currentRange = new SVNMergeRange(Math.min(myRevision1, myRevision2), Math.max(myRevision1, myRevision2), false);
        SVNMergeRange[] ranges = rangeList.getRanges();
        for (int i = 0; i < ranges.length; i++) {
            SVNMergeRange range = ranges[i];
            if (currentRange.intersects(range, false)) {
                if (range.contains(currentRange, false)) {
                    continue;
                }
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "merge ext: merge info conflict found on " + path.getAbsolutePath());
                return true;
            }
        }
        return false;
    }

    protected SVNMergeRangeList calculateRemainingRanges(File file, SVNURL sourceURL, SVNURL[] mergeSources) throws SVNException {
        if (skipExtendedMerge()) {
            return null;
        }

        SVNEntry entry = myWCAccess.getEntry(file, false);
        if (entry == null) {
            return null;
        }

        SVNMergeRangeList remainingRangeList = null;
        if (isHonorMergeInfo()) {
            MergePath mergeTarget = new MergePath();
            Map targetMergeInfo;
            Map implicitMergeInfo;
            SVNRepository repository = getRepository(entry.getSVNURL());
            SVNURL sourceRoot = repository.getRepositoryRoot(true);
            boolean[] indirect = {false};

            Map[] fullMergeInfo = getFullMergeInfo(entry, indirect, SVNMergeInfoInheritance.INHERITED,
                    repository, file, Math.max(myRevision1, myRevision2), Math.min(myRevision1, myRevision2));
            targetMergeInfo = fullMergeInfo[0];
            implicitMergeInfo = fullMergeInfo[1];

            mergeSources = getMergeSources(sourceURL, mergeSources);
            SVNURL url1 = mergeSources[0];
            SVNURL url2 = mergeSources[1];

            calculateRemainingRanges(null, mergeTarget, sourceRoot, url1, myRevision1, url2, myRevision2,
                    targetMergeInfo, implicitMergeInfo, false, entry, repository);
            remainingRangeList = mergeTarget.myRemainingRanges;
        }
        return remainingRangeList;
    }

    private SVNURL[] getMergeSources(SVNURL sourceURL, SVNURL[] mergeSources) throws SVNException {
        if (mergeSources == null) {
            mergeSources = new SVNURL[2];
        }
        if (mergeSources[0] != null && mergeSources[1] != null) {
            return mergeSources;
        }
        SVNURL sourceCopiedFrom = getExtendedMergeCallback().transformLocation(sourceURL, Math.max(myRevision1, myRevision2), Math.min(myRevision1, myRevision2));
        if (sourceCopiedFrom != null) {
            mergeSources[0] = sourceCopiedFrom;
            mergeSources[1] = sourceURL;
        } else {
            mergeSources[0] = sourceURL;
            mergeSources[1] = sourceURL;
        }
        return mergeSources;
    }

    protected ISVNEntryHandler getMergeInfoEntryHandler(String mergeSrcPath, SVNURL sourceRootURL, long revision1, long revision2, SVNRepository repository, SVNDepth depth, List childrenWithMergeInfo) {
        if (skipExtendedMerge()) {
            return super.getMergeInfoEntryHandler(mergeSrcPath, sourceRootURL, revision1, revision2, repository, depth, childrenWithMergeInfo);
        }
        return new MergeInfoFetcherExt(mergeSrcPath, sourceRootURL, revision1, revision2, repository, depth, childrenWithMergeInfo);
    }

    protected void doDirectoryMerge(SVNURL url1, long revision1, SVNURL url2, long revision2, SVNEntry parentEntry, SVNAdminArea adminArea, SVNDepth depth) throws SVNException {
        if (skipExtendedMerge()) {
            super.doDirectoryMerge(url1, revision1, url2, revision2, parentEntry, adminArea, depth);
            return;
        }

        try {
            super.doDirectoryMerge(url1, revision1, url2, revision2, parentEntry, adminArea, depth);
            doAdditionalMerge();
        } catch (Throwable th) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.UNKNOWN,
                    "Error while processing extended merge: ''{0}''", new Object[]{th.getMessage()},
                    SVNErrorMessage.TYPE_ERROR, th);
            SVNErrorManager.error(error, th, SVNLogType.DEFAULT);
        } finally {
            deleteReportFile();
            getPendingFiles().clear();
        }
    }

    protected SVNRemoteDiffEditor driveMergeReportEditor(File targetWCPath, SVNURL url1, long revision1, SVNURL url2, long revision2, List childrenWithMergeInfo, boolean isRollBack, SVNDepth depth, SVNAdminArea adminArea, SVNMergeCallback mergeCallback, SVNRemoteDiffEditor editor) throws SVNException {
        myPrimaryURL = revision1 > revision2 ? url1 : url2;
        myRevision1 = revision1;
        myRevision2 = revision2;
        return super.driveMergeReportEditor(targetWCPath, url1, revision1, url2, revision2, childrenWithMergeInfo, isRollBack, depth, adminArea, mergeCallback, editor);
    }

    protected void doAdditionalMerge() throws SVNException {
        SVNURL oldURL1 = myRepository1.getLocation();
        SVNURL oldURL2 = myRepository2.getLocation();

        BufferedReader reader = createReader();
        try {
            SVNMergeTask nextTask = readTask(reader);
            while (nextTask != null) {
                runMergeTask(nextTask, myRevision1, myRevision2);
                nextTask = readTask(reader);
            }
        } finally {
            SVNFileUtil.closeFile(reader);
            myRepository1.setLocation(oldURL1, false);
            myRepository2.setLocation(oldURL2, false);
            myRepository = null;
        }
    }

    private void runMergeTask(SVNMergeTask mergeTask, long revision1, long revision2) throws SVNException {
        boolean rollback = revision1 > revision2;
        SVNURL mergeSource = mergeTask.getMergeSource();
        SVNURL mergeSource2 = mergeTask.getMergeSource2();
        File mergeTarget = mergeTask.getMergeTarget();
        SVNMergeRangeList remainingRanges = mergeTask.getRemainingRanges();
        boolean updateWCMergeInfo = mergeTask.isUpdateWCMergeInfo();
        SVNCopySource copySource = mergeTask.getTargetCopySource();
        if (copySource != null) {
            copy(copySource, mergeTarget, true);
        }

        SVNURL mergeURL1 = !rollback ? mergeSource : mergeSource2;
        SVNURL mergeURL2 = !rollback ? mergeSource2 : mergeSource;
        myRepository1.setLocation(mergeURL1, false);
        myRepository2.setLocation(mergeURL2, false);
        SVNAdminArea targetArea = retrieve(myWCAccess, mergeTarget.getParentFile());

        myCurrentRemainingRanges = remainingRanges;
        myRecordMergeInfo = Boolean.valueOf(updateWCMergeInfo);
        try {
            doFileMerge(mergeURL1, revision1, mergeURL2, revision2, mergeTarget, targetArea, true);
        } finally {
            myCurrentRemainingRanges = null;
            myRecordMergeInfo = null;
        }
    }

    protected boolean isRecordMergeInfo() {
        boolean defaultOption =  super.isRecordMergeInfo();
        if (skipExtendedMerge()) {
            return defaultOption;
        }
        boolean recordMergeInfo = myRecordMergeInfo == null ? true : myRecordMergeInfo.booleanValue();
        return recordMergeInfo && defaultOption;
    }

    protected Object[] calculateRemainingRangeList(File targetFile, SVNEntry entry, SVNURL sourceRoot, boolean[] indirect,
                                                   SVNURL url1, long revision1, SVNURL url2, long revision2, SVNMergeRange range) throws SVNException {
        if (skipExtendedMerge() || myCurrentRemainingRanges == null) {
            return super.calculateRemainingRangeList(targetFile, entry, sourceRoot, indirect, url1, revision1, url2, revision2, range);
        }
//      targetMergeInfo and implicitMergeInfo should be processed anyway.
        Map targetMergeInfo = null;
        Map implicitMergeInfo = null;
        if (isHonorMergeInfo()) {
            myRepository1.setLocation(entry.getSVNURL(), false);
            Map[] fullMergeInfo = getFullMergeInfo(entry, indirect, SVNMergeInfoInheritance.INHERITED,
                    myRepository1, targetFile, Math.max(revision1, revision2), Math.min(revision1, revision2));
            targetMergeInfo = fullMergeInfo[0];
            implicitMergeInfo = fullMergeInfo[1];
            myRepository1.setLocation(url1, false);
        }
        return new Object[]{myCurrentRemainingRanges, targetMergeInfo, implicitMergeInfo};
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
        SVNURL sourceURL2 = null;
        File target = null;
        SVNMergeRangeList remainingRanges = null;
        boolean updateWCMergeInfo = true;
        SVNCopySource copySource = null;

        try {
            String sourceLine = reader.readLine();

            if (sourceLine == null) {
                return null;
            }
            sourceURL = SVNURL.parseURIEncoded(sourceLine);
            sourceURL2 = SVNURL.parseURIEncoded(reader.readLine());
            target = new File(reader.readLine());

            String mergeRangesRepresentation = reader.readLine();
            if (mergeRangesRepresentation != null && mergeRangesRepresentation.length() != 0) {
                SVNMergeRange[] ranges = SVNMergeInfoUtil.parseRevisionList(new StringBuffer(mergeRangesRepresentation), target.getPath());
                remainingRanges = new SVNMergeRangeList(ranges);
            }

            updateWCMergeInfo = Boolean.TRUE.toString().equals(reader.readLine());

            String path = reader.readLine();
            SVNRevision pegRevision = SVNRevision.parse(reader.readLine());
            SVNRevision revision = SVNRevision.parse(reader.readLine());
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
        return new SVNMergeTask(sourceURL, sourceURL2, target, remainingRanges, updateWCMergeInfo, copySource);
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
        private SVNURL myMergeSource2;
        private File myMergeTarget;
        private SVNMergeRangeList myRemainingRanges;
        private boolean myUpdateWCMergeInfo;
        private SVNCopySource myTargetCopySource;

        protected SVNMergeTask(SVNURL mergeSource, SVNURL mergeSource2, File mergeTarget, SVNMergeRangeList remainingRanges, boolean updateWCMergeInfo, SVNCopySource targetCopySource) {
            myMergeSource = mergeSource;
            myMergeSource2 = mergeSource2;
            myMergeTarget = mergeTarget;
            myRemainingRanges = remainingRanges;
            myUpdateWCMergeInfo = updateWCMergeInfo;
            myTargetCopySource = targetCopySource;
        }

        protected SVNURL getMergeSource() {
            return myMergeSource;
        }

        protected SVNURL getMergeSource2() {
            return myMergeSource2;
        }

        protected File getMergeTarget() {
            return myMergeTarget;
        }

        protected SVNMergeRangeList getRemainingRanges() {
            return myRemainingRanges;
        }

        protected boolean isUpdateWCMergeInfo() {
            return myUpdateWCMergeInfo;
        }

        protected SVNCopySource getTargetCopySource() {
            return myTargetCopySource;
        }

        protected void writeTo(BufferedWriter writer) throws IOException {
            writer.write(getMergeSource().toString());
            writer.newLine();

            writer.write(getMergeSource2().toString());
            writer.newLine();

            writer.write(getMergeTarget().getAbsolutePath());
            writer.newLine();

            if (getRemainingRanges() != null) {
                writer.write(getRemainingRanges().toString());
            }
            writer.newLine();

            writer.write(String.valueOf(isUpdateWCMergeInfo()));
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
            buffer.append("merge task: source from ");
            buffer.append(getMergeSource());
            buffer.append("; source to");
            buffer.append(getMergeSource2());
            buffer.append("; target ");
            buffer.append(getMergeTarget());
            buffer.append("; revision ranges ");
            if (getRemainingRanges() == null) {
                buffer.append("[NULL]");
            } else {
                buffer.append(getRemainingRanges().toString());
            }
            buffer.append("; copy source ");
            if (getTargetCopySource() != null) {
                buffer.append(getTargetCopySource().isURL() ? getTargetCopySource().getURL().toString() : getTargetCopySource().getFile().getAbsolutePath());
                buffer.append("@");
                buffer.append(getTargetCopySource().getPegRevision().toString());
                buffer.append(" revision ");
                buffer.append(getTargetCopySource().getRevision());
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
            SVNURL entryURL = entry.getSVNURL();
            if (entryURL != null && !myTarget.equals(path)) {
                SVNEntry parentEntry = myWCAccess.getVersionedEntry(path.getParentFile(), false);
                SVNURL expectedURL = parentEntry.getSVNURL().appendPath(SVNPathUtil.tail(path.getAbsolutePath().replace(File.separatorChar, '/')), false);
                if (!entryURL.equals(expectedURL)) {
                    SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "''{0}'' is switched entry which is not allowed for this kind of merge", path);
                    SVNErrorManager.error(error, SVNLogType.WC);
                }
            }
            super.handleEntry(path, entry);
        }
    }
}
