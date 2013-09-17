package org.tmatesoft.svn.core.internal.wc2.ng;

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.*;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslatorInputStream;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec;
import org.tmatesoft.svn.core.io.*;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnDiffStatus;
import org.tmatesoft.svn.core.wc2.SvnDiffSummarize;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class SvnNgDiffSummarize extends SvnNgOperationRunner<SvnDiffStatus, SvnDiffSummarize> {

    private SvnNgRepositoryAccess repositoryAccess;

    @Override
    public boolean isApplicable(SvnDiffSummarize operation, SvnWcGeneration wcGeneration) throws SVNException {
        if (operation.getSource() != null) {
            if (operation.getSource().isFile() && wcGeneration != SvnWcGeneration.V17) {
                return false;
            }

            return true;
        } else {
            if (operation.getFirstSource().isFile() && wcGeneration != SvnWcGeneration.V17) {
                return false;
            }

            if (operation.getSecondSource().isFile() && wcGeneration != SvnWcGeneration.V17) {
                return false;
            }

            return true;
        }
    }

    @Override
    protected SvnDiffStatus run(SVNWCContext context) throws SVNException {
        final SvnTarget source = getOperation().getSource();
        final SvnTarget firstSource = getOperation().getFirstSource();
        final SvnTarget secondSource = getOperation().getSecondSource();

        final ISVNDiffStatusHandler handler = createHandlerForReceiver(getOperation());
        final SVNDepth depth = getOperation().getDepth();
        final boolean useAncestry = !getOperation().isIgnoreAncestry();

        if (source != null) {
            doDiff(source, getOperation().getStartRevision(), source, getOperation().getEndRevision(), source.getPegRevision(), depth, useAncestry, handler);
        } else {
            doDiff(firstSource, firstSource.getResolvedPegRevision(), secondSource, secondSource.getResolvedPegRevision(), SVNRevision.UNDEFINED, depth, useAncestry, handler);
        }
        return null;
    }


    private void doDiff(SvnTarget target1, SVNRevision revision1, SvnTarget target2, SVNRevision revision2, SVNRevision pegRevision, SVNDepth depth, boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
        if ((revision1 == SVNRevision.UNDEFINED) || (revision2 == SVNRevision.UNDEFINED)) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Not all required revisions are specified");
            SVNErrorManager.error(errorMessage, SVNLogType.WC);
        }

        boolean isLocalRev1 = (revision1 == SVNRevision.BASE) || (revision1 == SVNRevision.WORKING);
        boolean isLocalRev2 = (revision2 == SVNRevision.BASE) || (revision2 == SVNRevision.WORKING);

        if (pegRevision != SVNRevision.UNDEFINED && isLocalRev1 && isLocalRev2) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "At least one revision must be something other than BASE or WORKING when diffing a URL");
            SVNErrorManager.error(errorMessage, SVNLogType.WC);
        }

        boolean isRepos1 = !isLocalRev1 || target1.isURL();
        boolean isRepos2 = !isLocalRev2 || target2.isURL();

        if (isRepos1) {
            if (isRepos2) {
                doDiffReposRepos(target1.getURL(), target1.getFile(), revision1,
                        target2.getURL(), target2.getFile(), revision2,
                        pegRevision, depth, useAncestry, handler);
            } else {
                doDiffReposWC(target1, revision1, target2, revision2, pegRevision, false, depth, useAncestry, handler);
            }
        } else {
            if (isRepos2) {
                doDiffReposWC(target2, revision2, target1, revision1, pegRevision, true, depth, useAncestry, handler);
            } else {
                if (revision1 == SVNRevision.WORKING && revision2 == SVNRevision.WORKING) {
                    File path1 = target1.getFile();
                    File path2 = target2.getFile();

                    SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(path1));
                    String target;
                    if (kind == SVNNodeKind.DIR) {
                        target = "";
                    } else {
                        target = SVNFileUtil.getFileName(path1);
                    }
                    arbitraryNodesDiff(target1, target2, depth, new SvnDiffSummarizeCallback(kind == SVNNodeKind.DIR ? path1 : SVNFileUtil.getParentFile(path1), false, handler));
                } else {
                    doDiffWCWC(target1, revision1, target2, revision2, depth, useAncestry, handler);
                }
            }
        }
    }

    private void doDiffURL(SVNURL url, File path, SVNRevision startRevision, SVNRevision endRevision, SVNRevision pegRevision,
                           SVNDepth depth, boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        doDiffReposRepos(url, path, startRevision,
                url, path, endRevision,
                pegRevision, depth, useAncestry, handler);
    }

    private void doDiffReposRepos(SVNURL url1, File path1, SVNRevision revision1,
                                  SVNURL url2, File path2, SVNRevision revision2,
                                  SVNRevision pegRevision, SVNDepth depth, boolean useAncestry,
                                  ISVNDiffStatusHandler handler) throws SVNException {

        if (revision1 == SVNRevision.UNDEFINED || revision2 == SVNRevision.UNDEFINED) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Not all revisions are specified");
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        boolean isLocalRev1 = revision1 == SVNRevision.BASE || revision1 == SVNRevision.WORKING;
        boolean isLocalRev2 = revision2 == SVNRevision.BASE || revision2 == SVNRevision.WORKING;
        boolean isRepos1;
        boolean isRepos2;

        if (pegRevision != SVNRevision.UNDEFINED) {
            if (isLocalRev1 && isLocalRev2) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "At least one revision must be non-local for a pegged diff");
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            isRepos1 = !isLocalRev1;
            isRepos2 = !isLocalRev2;
        } else {
            isRepos1 = !isLocalRev1 || url1 != null;
            isRepos2 = !isLocalRev2 || url2 != null;
        }

        if (!isRepos1 || !isRepos2) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Summarizing diff can only compare repository to repository");
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        File basePath = null;
        if (path1 != null) {
            basePath = path1;
        }
        if (path2 != null) {
            basePath = path2;
        }
        if (pegRevision.isValid()) {
            url2 = resolvePeggedDiffTargetUrl(url2, path2, pegRevision, revision2);
            url1 = resolvePeggedDiffTargetUrl(url1, path1, pegRevision, revision1);

            if (url2 != null && url1 == null) {
                url1 = url2;
            }
            if (url1 != null && url2 == null) {
                url2 = url1;
            }

        } else {
            url1 = url1 == null ? getURL(path1) : url1;
            url2 = url2 == null ? getURL(path2) : url2;
        }
        SVNRepository repository1 = createRepository(url1, null, true);
        SVNRepository repository2 = createRepository(url2, null, false);
        long rev1 = getRevisionNumber(revision1, repository1, url1);
        long rev2 = -1;
        SVNNodeKind kind1 = null;
        SVNNodeKind kind2 = null;

        SVNURL anchor1 = url1;
        SVNURL anchor2 = url2;
        String target1 = "";
        String target2 = "";

        try {
            rev2 = getRevisionNumber(revision2, repository2, url2);
            kind1 = repository1.checkPath("", rev1);
            kind2 = repository2.checkPath("", rev2);

            if (kind1 == SVNNodeKind.NONE && kind2 == SVNNodeKind.NONE) {
                if (url1.equals(url2)) {
                    SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Diff target ''{0}'' was not found in the repository at revisions ''{1}'' and ''{2}''", url1, rev1, rev2);
                    SVNErrorManager.error(errorMessage, SVNLogType.WC);
                } else {
                    SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Diff targets ''{0}'' and ''{1}'' were not found in the repository at revisions ''{2}'' and ''{3}''");
                    SVNErrorManager.error(errorMessage, SVNLogType.WC);
                }
            } else if (kind1 == SVNNodeKind.NONE) {
                checkDiffTargetExists(url1, rev2, rev1, repository1);
            } else if (kind2 == SVNNodeKind.NONE) {
                checkDiffTargetExists(url2, rev1, rev2, repository2);
            }

            SVNURL repositoryRoot = repository1.getRepositoryRoot(true);
            if (!url1.equals(repositoryRoot) && !url2.equals(repositoryRoot)) {
                anchor1 = url1.removePathTail();
                anchor2 = url2.removePathTail();

                target1 = SVNPathUtil.tail(url1.toDecodedString());
                target2 = SVNPathUtil.tail(url2.toDecodedString());
            }
        } finally {
            repository2.closeSession();
        }

        boolean nonDir = kind1 != SVNNodeKind.DIR || kind2 != SVNNodeKind.DIR;

        ISvnDiffCallback oldCallback = new SvnDiffSummarizeCallback(path1 != null ? SVNFileUtil.createFilePath(path1.getParentFile(), target1) : SVNFileUtil.createFilePath(target1), false, handler);
        ISvnDiffCallback2 callback = new SvnDiffCallbackWrapper(oldCallback, true, nonDir ? basePath.getParentFile() : basePath);

        if (kind2 == SVNNodeKind.NONE) {
            SVNURL tmpUrl;

            tmpUrl = url1;
            url1 = url2;
            url2 = tmpUrl;

            long tmpRev;
            tmpRev = rev1;
            rev1 = rev2;
            rev2 = tmpRev;

            tmpUrl = anchor1;
            anchor1 = anchor2;
            anchor2 = tmpUrl;

            String tmpTarget;
            tmpTarget = target1;
            target1 = target2;
            target2 = tmpTarget;

            callback = new SvnReverseOrderDiffCallback(callback, null);
        }

        repository1.setLocation(anchor1, true);
        repository2.setLocation(anchor2, true);
        SvnNgRemoteDiffEditor2 editor = null;
        try {
            editor = new SvnNgRemoteDiffEditor2(rev1, false, repository2, callback);
            final long finalRev1 = rev1;
            ISVNReporterBaton reporter = new ISVNReporterBaton() {

                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, finalRev1, SVNDepth.INFINITY, false);
                    reporter.finishReport();
                }
            };
            repository1.diff(url2, rev2, rev1, target1, !useAncestry, depth, false, reporter, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
        } finally {
            repository2.closeSession();
            if (editor != null) {
                editor.cleanup();
            }
        }
    }

    private void doDiffReposWC(SvnTarget target1, SVNRevision revision1,
                               SvnTarget target2, SVNRevision revision2,
                               SVNRevision pegRevision, boolean reverse, SVNDepth depth, boolean useAncestry,
                               ISVNDiffStatusHandler handler) throws SVNException {//TODO: changelists?

        ISvnDiffCallback callback = new SvnDiffSummarizeCallback(target1.getFile(), reverse, handler);
        SvnNgDiffUtil.doDiffReposWC(target1, revision1, pegRevision, target2, revision2, reverse, getRepositoryAccess(), getWcContext(), false, depth, useAncestry, getOperation().getApplicableChangelists(), false, null, callback, this);
    }

    private void doDiffWCWC(SvnTarget target1, SVNRevision revision1,
                            SvnTarget target2, SVNRevision revision2,
                            SVNDepth depth, boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
        assert !target1.isURL();
        assert !target2.isURL();

        File path1 = target1.getFile();
        File path2 = target2.getFile();

        if (!path1.equals(path2) || (!(revision1 == SVNRevision.BASE && revision2 == SVNRevision.WORKING))) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, "Summarized diffs are only supported between a path's text-base and its working files at this time");
            SVNErrorManager.error(errorMessage, SVNLogType.WC);
        }

        SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(path1));

        String targetString1 = (kind == SVNNodeKind.DIR) ? "" : SVNFileUtil.getFileName(path1);

        ISvnDiffCallback callback = new SvnDiffSummarizeCallback(path1, false, handler);

        SvnNgDiffUtil.doDiffWCWC(path1, getRepositoryAccess(), getWcContext(), depth, useAncestry, getOperation().getApplicableChangelists(), false, false, null, callback, getOperation().getEventHandler());
    }

    private SVNURL resolvePeggedDiffTargetUrl(SVNURL url, File path, SVNRevision pegRevision, SVNRevision revision) throws SVNException {
        try {
            final Structure<SvnRepositoryAccess.LocationsInfo> locationsInfo = getRepositoryAccess().getLocations(null,
                    url == null ? SvnTarget.fromFile(path) : SvnTarget.fromURL(url),
                    pegRevision, revision, SVNRevision.UNDEFINED);
            return locationsInfo.get(SvnRepositoryAccess.LocationsInfo.startUrl);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.CLIENT_UNRELATED_RESOURCES ||
                    e.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    private void checkDiffTargetExists(SVNURL url, long revision, long otherRevision, SVNRepository repository) throws SVNException {
        SVNURL sessionUrl = repository.getLocation();
        boolean equal = sessionUrl.equals(url);
        if (!equal) {
            repository.setLocation(url, true);
        }
        SVNNodeKind kind = repository.checkPath("", revision);
        if (kind == SVNNodeKind.NONE) {
            if (revision == otherRevision) {
                SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Diff target ''{0}'' was not found in the repository at revision ''{1}''", url, revision);
                SVNErrorManager.error(errorMessage, SVNLogType.WC);
            } else {
                SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Diff target ''{0}'' was not found in the repository at revision ''{1}'' or ''{2}''", url, revision, otherRevision);
                SVNErrorManager.error(errorMessage, SVNLogType.WC);
            }
        }
        if (!equal) {
            repository.setLocation(url, true);
        }
    }

    private void arbitraryNodesDiff(SvnTarget target1, SvnTarget target2, SVNDepth depth, ISvnDiffCallback callback) throws SVNException {
        File path1 = target1.getFile();
        File path2 = target2.getFile();
        SVNNodeKind kind1 = SVNFileType.getNodeKind(SVNFileType.getType(path1));
        SVNNodeKind kind2 = SVNFileType.getNodeKind(SVNFileType.getType(path2));

        if (kind1 == kind2) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "''{0}'' is not the same node kind as ''{1}''", path1, path2);
            SVNErrorManager.error(errorMessage, SVNLogType.WC);
        }
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        if (kind1 == SVNNodeKind.FILE) {
            doArbitraryFilesDiff(path1, path2, SVNFileUtil.createFilePath(SVNFileUtil.getFileName(path1)), false, false, null, callback);
        } else if (kind1 == SVNNodeKind.DIR) {
            doArbitraryDirsDiff(path1, path2, null, null, depth, callback);
        } else {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "''{0}'' is not a file or directory", kind1 == SVNNodeKind.NONE ? path1 : path2);
            SVNErrorManager.error(errorMessage, SVNLogType.WC);
        }
    }

    private void doArbitraryFilesDiff(File localAbsPath1, File localAbsPath2, File path, boolean file1IsEmpty, boolean file2IsEmpty, SVNProperties originalPropertiesOverride, ISvnDiffCallback callback) throws SVNException {
        ISVNEventHandler eventHandler = getOperation().getEventHandler();
        if (eventHandler != null) {
            eventHandler.checkCancelled();
        }

        SvnDiffCallbackResult result = new SvnDiffCallbackResult();

        SVNProperties originalProps;
        if (originalPropertiesOverride != null) {
            originalProps = originalPropertiesOverride;
        } else {
            try {
                originalProps = getWcContext().getActualProps(localAbsPath1);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND && e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_WORKING_COPY) {
                    throw e;
                }
                originalProps = new SVNProperties();
            }
        }

        SVNProperties modifiedProps;
        try {
            modifiedProps = getWcContext().getActualProps(localAbsPath2);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND && e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_WORKING_COPY) {
                throw e;
            }
            modifiedProps = new SVNProperties();
        }

        SVNProperties propChanges = originalProps.compareTo(modifiedProps);

        String originalMimeType = originalProps.getStringValue(SVNProperty.MIME_TYPE);
        if (!file1IsEmpty && originalMimeType == null) {
            String mimeType = SVNFileUtil.detectMimeType(localAbsPath1, getOperation().getOptions().getFileExtensionsToMimeTypes());

            if (mimeType != null) {
                originalMimeType = mimeType;
            }
        }

        String modifiedMimeType = modifiedProps.getStringValue(SVNProperty.MIME_TYPE);
        if (!file2IsEmpty && modifiedMimeType == null) {
            String mimeType = SVNFileUtil.detectMimeType(localAbsPath2, getOperation().getOptions().getFileExtensionsToMimeTypes());

            if (mimeType != null) {
                modifiedMimeType = mimeType;
            }
        }
        if (file1IsEmpty && !file2IsEmpty) {
            callback.fileAdded(result, path, localAbsPath1, localAbsPath2, SVNRepository.INVALID_REVISION, SVNRepository.INVALID_REVISION,
                    originalMimeType, modifiedMimeType, null, SVNRepository.INVALID_REVISION, propChanges, originalProps);
        } else if (!file1IsEmpty && file2IsEmpty) {
            callback.fileDeleted(result, path, localAbsPath1, localAbsPath2, originalMimeType, modifiedMimeType, originalProps);
        } else {
            InputStream inputStream1 = SVNFileUtil.openFileForReading(localAbsPath1);
            InputStream inputStream2 = SVNFileUtil.openFileForReading(localAbsPath2);

            try {
                if (originalProps != null) {
                    byte[] eol = SVNTranslator.getEOL(originalProps.getStringValue(SVNProperty.EOL_STYLE), getOperation().getOptions());
                    if (eol != null) {
                        inputStream1 = new SVNTranslatorInputStream(inputStream1, eol, true, null, false);
                    }
                }
                if (modifiedProps != null) {
                    byte[] eol = SVNTranslator.getEOL(modifiedProps.getStringValue(SVNProperty.EOL_STYLE), getOperation().getOptions());
                    if (eol != null) {
                        inputStream2 = new SVNTranslatorInputStream(inputStream2, eol, true, null, false);
                    }
                }
                boolean same = SVNFileUtil.compare(inputStream1, inputStream2);
                if (!same || propChanges.size() > 0) {
                    callback.fileChanged(result, path, same ? null : localAbsPath1, same ? null : localAbsPath2, SVNRepository.INVALID_REVISION, SVNRepository.INVALID_REVISION, originalMimeType, modifiedMimeType, propChanges, originalProps);
                }
            } finally {
                SVNFileUtil.closeFile(inputStream1);
                SVNFileUtil.closeFile(inputStream2);
            }
        }
    }

    private void doArbitraryDirsDiff(File localAbsPath1, File localAbsPath2, File rootAbsPath1, File rootAbsPath2, SVNDepth depth, ISvnDiffCallback callback) throws SVNException {
        SVNNodeKind kind1 = null;
        try {
            kind1 = SVNFileType.getNodeKind(SVNFileType.getType(localAbsPath1.getCanonicalFile()));
        } catch (IOException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
            SVNErrorManager.error(errorMessage, SVNLogType.WC);
        }

        ArbitraryDiffWalker diffWalker = new ArbitraryDiffWalker();
        diffWalker.recursingWithinAddedSubtree = (kind1 != SVNNodeKind.DIR);
        diffWalker.root1AbsPath = rootAbsPath1 != null ? rootAbsPath1 : localAbsPath1;
        diffWalker.root2AbsPath = rootAbsPath2 != null ? rootAbsPath2 : localAbsPath2;
        diffWalker.recursingWithinAdmDir = false;
        diffWalker.context = getWcContext();

        if (depth.compareTo(SVNDepth.IMMEDIATES) <= 0) {
            arbitraryDiffThisDir(diffWalker, localAbsPath1, depth, callback);
        } else if (depth == SVNDepth.INFINITY) {
            walkDirectory(diffWalker.recursingWithinAddedSubtree ? localAbsPath2 : localAbsPath1, diffWalker, callback);
        }
    }

    private void arbitraryDiffThisDir(ArbitraryDiffWalker diffWalker, File localAbsPath, SVNDepth depth, ISvnDiffCallback callback) throws SVNException {
        SvnDiffCallbackResult result = new SvnDiffCallbackResult();

        if (diffWalker.recursingWithinAdmDir) {
            if (SVNFileUtil.skipAncestor(diffWalker.admDirAbsPath, localAbsPath) != null) {
                return;
            } else {
                diffWalker.recursingWithinAdmDir = false;
                diffWalker.admDirAbsPath = null;
            }
        } else if (SVNFileUtil.getFileName(localAbsPath).equals(SVNFileUtil.getAdminDirectoryName())) {
            diffWalker.recursingWithinAdmDir = true;
            diffWalker.admDirAbsPath = localAbsPath;
            return;
        }

        File childRelPath;
        if (diffWalker.recursingWithinAddedSubtree) {
            childRelPath = SVNFileUtil.skipAncestor(diffWalker.root2AbsPath, localAbsPath);
        } else {
            childRelPath = SVNFileUtil.skipAncestor(diffWalker.root1AbsPath, localAbsPath);
        }
        if (childRelPath == null) {
            return;
        }
        File localAbsPath1 = SVNFileUtil.createFilePath(diffWalker.root1AbsPath, childRelPath);
        SVNNodeKind kind1 = null;
        try {
            kind1 = SVNFileType.getNodeKind(SVNFileType.getType(localAbsPath1.getCanonicalFile()));
        } catch (IOException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
            SVNErrorManager.error(errorMessage, SVNLogType.WC);
        }
        File localAbsPath2 = SVNFileUtil.createFilePath(diffWalker.root2AbsPath, childRelPath);
        SVNNodeKind kind2 = null;
        try {
            kind2 = SVNFileType.getNodeKind(SVNFileType.getType(localAbsPath2.getCanonicalFile()));
        } catch (IOException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
            SVNErrorManager.error(errorMessage, SVNLogType.WC);
        }

        File[] children1 = null;
        if (depth.compareTo(SVNDepth.EMPTY) > 0) {
            if (kind1 == SVNNodeKind.DIR) {
                children1 = SVNFileListUtil.listFiles(localAbsPath1);
            } else {
                children1 = new File[0];
            }
        }
        File[] children2 = null;
        if (kind2 == SVNNodeKind.DIR) {
            SVNProperties originalProps;
            try {
                originalProps = getWcContext().getActualProps(localAbsPath1);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND && e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_WORKING_COPY) {
                    throw e;
                }
                originalProps = new SVNProperties();
            }
            SVNProperties modifiedProps;
            try {
                modifiedProps = getWcContext().getActualProps(localAbsPath2);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND && e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_WORKING_COPY) {
                    throw e;
                }
                modifiedProps = new SVNProperties();
            }
            SVNProperties propChanges = originalProps.compareTo(modifiedProps);
            if (propChanges.size() > 0) {
                callback.dirPropsChanged(result, childRelPath, false, propChanges, originalProps);
            }
            if (depth.compareTo(SVNDepth.EMPTY) > 0) {
                children2 = SVNFileListUtil.listFiles(localAbsPath2);
            }
        } else if (depth.compareTo(SVNDepth.EMPTY) > 0) {
            children2 = new File[0];
        }

        if (depth.compareTo(SVNDepth.EMPTY) <= 0) {
            return;
        }

        Set<File> mergedChildren = new HashSet<File>();
        if (children1 != null) {
            for (File child1 : children1) {
                mergedChildren.add(child1);
            }
        }
        if (children2 != null) {
            for (File child2 : children2) {
                mergedChildren.add(child2);
            }
        }
        List<File> sortedChildren = new ArrayList<File>(mergedChildren);
        Collections.sort(sortedChildren);
        for (File sortedChild : sortedChildren) {
            checkCancelled();

            String name = SVNFileUtil.getFileName(sortedChild);

            if (name.equals(SVNFileUtil.getAdminDirectoryName())) {
                continue;
            }
            File childAbsPath1 = SVNFileUtil.createFilePath(localAbsPath1, name);
            File childAbsPath2 = SVNFileUtil.createFilePath(localAbsPath2, name);

            SVNNodeKind childKind1 = null;
            SVNNodeKind childKind2 = null;
            try {
                childKind1 = SVNFileType.getNodeKind(SVNFileType.getType(childAbsPath1.getCanonicalFile()));
                childKind2 = SVNFileType.getNodeKind(SVNFileType.getType(childAbsPath2.getCanonicalFile()));
            } catch (IOException e) {
                SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
                SVNErrorManager.error(errorMessage, SVNLogType.WC);
            }
            if (childKind1 == SVNNodeKind.DIR && childKind2 == SVNNodeKind.DIR) {
                if (depth == SVNDepth.IMMEDIATES) {
                    doArbitraryDirsDiff(childAbsPath1, childAbsPath2, diffWalker.root1AbsPath, diffWalker.root2AbsPath, SVNDepth.EMPTY, diffWalker.callback);
                } else {
                    continue;
                }
            }
            if (childKind1 == SVNNodeKind.FILE && (childKind2 == SVNNodeKind.DIR || childKind2 == SVNNodeKind.NONE)) {
                doArbitraryFilesDiff(childAbsPath1, null, SVNFileUtil.createFilePath(childRelPath, name), false, true, null, diffWalker.callback);
            }
            if (childKind2 == SVNNodeKind.FILE && (childKind1 == SVNNodeKind.DIR || childKind1 == SVNNodeKind.NONE)) {
                SVNProperties originalProps;
                try {
                    originalProps = getWcContext().getActualProps(childAbsPath1);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND && e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_WORKING_COPY) {
                        throw e;
                    }
                    originalProps = new SVNProperties();
                }
                doArbitraryFilesDiff(null, childAbsPath2, SVNFileUtil.createFilePath(childRelPath, name), true, false, originalProps, diffWalker.callback);
            }
            if (childKind1 == SVNNodeKind.FILE && childKind2 == SVNNodeKind.FILE) {
                doArbitraryFilesDiff(childAbsPath1, childAbsPath2, SVNFileUtil.createFilePath(childRelPath, name), false, false, null, callback);
            }

            if (depth.compareTo(SVNDepth.FILES) > 0 && childKind2 == SVNNodeKind.DIR && (childKind1 == SVNNodeKind.FILE || childKind1 == SVNNodeKind.NONE)) {
                doArbitraryDirsDiff(childAbsPath1, childAbsPath2, diffWalker.root1AbsPath, diffWalker.root2AbsPath, depth.compareTo(SVNDepth.IMMEDIATES) <= 0 ? SVNDepth.EMPTY : SVNDepth.INFINITY, diffWalker.callback);
            }
        }
    }

    private void walkDirectory(File localAbsPath, ArbitraryDiffWalker diffWalker, ISvnDiffCallback callback) throws SVNException {
        visit(localAbsPath, SVNFileType.DIRECTORY, diffWalker, callback);

        File[] children = SVNFileListUtil.listFiles(localAbsPath);
        if (children != null) {
            for (File child : children) {
                SVNFileType type = SVNFileType.getType(child);
                if (type == SVNFileType.DIRECTORY) {
                    walkDirectory(child, diffWalker, callback);
                } else if (type == SVNFileType.FILE || type == SVNFileType.SYMLINK) {
                    visit(child, type, diffWalker, callback);
                }
            }
        }
    }

    private void visit(File localAbsPath, SVNFileType type, ArbitraryDiffWalker diffWalker, ISvnDiffCallback callback) throws SVNException {
        checkCancelled();
        if (type != SVNFileType.DIRECTORY) {
            return;
        }
        arbitraryDiffThisDir(diffWalker, localAbsPath, SVNDepth.INFINITY, callback);
    }

    private ISVNDebugLog getDebugLog() {
        return SVNDebugLog.getDefaultLog();
    }

    private long getRevisionNumber(SVNRevision revision1, SVNRepository repository1, SVNURL url1) throws SVNException {
        final Structure<SvnRepositoryAccess.RevisionsPair> revisionNumber = getRepositoryAccess().getRevisionNumber(repository1, SvnTarget.fromURL(url1, revision1), revision1, null);
        return revisionNumber.lng(SvnRepositoryAccess.RevisionsPair.revNumber);
    }

    private SVNURL getURL(File path1) throws SVNException {
        return getRepositoryAccess().getURLFromPath(SvnTarget.fromFile(path1), SVNRevision.UNDEFINED, null).<SVNURL>get(SvnRepositoryAccess.UrlInfo.url);
    }

    protected SVNRepository createRepository(SVNURL url, File path, boolean mayReuse) throws SVNException {
        return getRepositoryAccess().createRepository(url, null, mayReuse);
    }

    private static ISVNDiffStatusHandler createHandlerForReceiver(final ISvnObjectReceiver<SvnDiffStatus> receiver) {
        return new ISVNDiffStatusHandler() {
            public void handleDiffStatus(SVNDiffStatus diffStatus) throws SVNException {
                if (receiver != null) {
                    receiver.receive(null, SvnCodec.diffStatus(diffStatus));
                }
            }
        };
    }

    protected SvnNgRepositoryAccess getRepositoryAccess() throws SVNException {
        if (repositoryAccess == null) {
            repositoryAccess = new SvnNgRepositoryAccess(getOperation(), getWcContext());
        }
        return repositoryAccess;
    }

    private static class ArbitraryDiffWalker {
        private File root1AbsPath;
        private File root2AbsPath;
        private boolean recursingWithinAddedSubtree;
        private boolean recursingWithinAdmDir;
        private File admDirAbsPath;
        private ISvnDiffCallback callback;
        private SVNWCContext context;
    }
}
