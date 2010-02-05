/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.patch;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * Data type to manage parsing of patches.
 * 
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNPatch {

    public static final String MINUS = "--- ";
    public static final String PLUS = "+++ ";
    public static final String ATAT = "@@";

    private static final int MAX_FUZZ = 2;

    /** Path to the patch file. */
    private File path;

    /** The patch file itself. */
    private SVNPatchFileStream patchFile;

    /**
     * The old and new file names as retrieved from the patch file. These paths
     * are UTF-8 encoded and canonicalized, but otherwise left unchanged from
     * how they appeared in the patch file.
     */
    private File oldFilename;
    private File newFilename;

    /**
     * An array containing an svn_hunk_t object for each hunk parsed from the
     * patch.
     */
    private List hunks;

    public File getPath() {
        return path;
    }

    public SVNPatchFileStream getPatchFile() {
        return patchFile;
    }

    public File getOldFilename() {
        return oldFilename;
    }

    public File getNewFilename() {
        return newFilename;
    }

    public List getHunks() {
        return hunks;
    }

    public void close() {
        if (hunks != null) {
            int hunksCount = hunks.size();
            if (hunksCount > 0) {
                for (int i = 0; i < hunksCount; i++) {
                    final SVNPatchHunk hunk = (SVNPatchHunk) hunks.get(i);
                    hunk.close();
                }
            }
        }
    }

    /**
     * Return the next PATCH in PATCH_FILE.
     * 
     * If no patch can be found, set PATCH to NULL.
     */
    public static SVNPatch parseNextPatch(SVNPatchFileStream patchFile) {

        if (patchFile.isEOF()) {
            /* No more patches here. */
            return null;
        }

        /* Get the patch's filename. */
        final File patchPath = patchFile.getPath();

        /* Record what we already know about the patch. */
        final SVNPatch patch = new SVNPatch();
        patch.patchFile = patchFile;
        patch.path = patchPath;

        String indicator = MINUS;
        boolean eof = false, in_header = false;
        do {

            /* Read a line from the stream. */
            final String line = patchFile.readLine();
            eof = line != null;

            /* See if we have a diff header. */
            if (!eof && line.length() > indicator.length() && line.startsWith(indicator)) {
                /*
                 * If we can find a tab, it separates the filename from the rest
                 * of the line which we can discard.
                 */
                final int tab = line.indexOf('\t');
                final File path = new File(tab > 0 ? line.substring(0, tab) : line);

                if ((!in_header) && MINUS.equals(indicator)) {
                    /* First line of header contains old filename. */
                    patch.oldFilename = path;
                    indicator = PLUS;
                    in_header = true;
                } else if (in_header && PLUS.equals(indicator)) {
                    /* Second line of header contains new filename. */
                    patch.newFilename = path;
                    in_header = false;
                    break; /* All good! */
                } else {
                    in_header = false;
                }
            }
        } while (!eof);

        if (patch.oldFilename == null || patch.newFilename == null) {
            /* Something went wrong, just discard the result. */
            return null;
        }
        /* Parse hunks. */
        patch.hunks = new ArrayList(10);
        SVNPatchHunk hunk;
        do {
            hunk = SVNPatchHunk.parseNextHunk(patch);
            if (hunk != null) {
                patch.hunks.add(hunk);
            }
        } while (hunk != null);

        /*
         * Usually, hunks appear in the patch sorted by their original line
         * offset. But just in case they weren't parsed in this order for some
         * reason, we sort them so that our caller can assume that hunks are
         * sorted as if parsed from a usual patch.
         */
        Collections.sort(patch.hunks, SVNPatchHunk.COMPARATOR);

        return patch;
    }

    /**
     * Apply a PATCH to a working copy at ABS_WC_PATH.
     * 
     * STRIP_COUNT specifies the number of leading path components which should
     * be stripped from target paths in the patch.
     * 
     * @throws SVNException
     */
    public void applyPatch(SVNAdminArea wc, File targetPath, boolean dryRun, long stripCount) throws SVNException {

        final SVMPatchTarget target = initPatchTarget(targetPath, stripCount);

        if (target == null) {
            return;
        }

        if (target.isSkipped()) {
            target.maybeSendPatchNotification();
            return;
        }

        /* Match hunks. */
        for (final Iterator i = this.getHunks().iterator(); i.hasNext();) {
            final SVNPatchHunk hunk = (SVNPatchHunk) i.next();

            SVNPatchHunkInfo hi;
            int fuzz = 0;

            /*
             * Determine the line the hunk should be applied at. If no match is
             * found initially, try with fuzz.
             */
            do {
                hi = SVNPatchHunkInfo.getHunkInfo(target, hunk, fuzz);
                fuzz++;
            } while (hi.isRejected() && fuzz <= MAX_FUZZ);

            target.getHunks().add(hi);
        }

        /* Apply or reject hunks. */
        for (final Iterator i = target.getHunks().iterator(); i.hasNext();) {
            final SVNPatchHunkInfo hi = (SVNPatchHunkInfo) i.next();

            if (hi.isRejected()) {
                target.rejectHunk(hi);
            } else {
                target.applyHunk(hi);
            }
        }

        if (target.getKind() == SVNNodeKind.FILE) {
            /* Copy any remaining lines to target. */
            target.copyLinesToTarget(0);
            if (!target.isEOF()) {
                /*
                 * We could not copy the entire target file to the temporary
                 * file, and would truncate the target if we copied the
                 * temporary file on top of it. Cancel any modifications to the
                 * target file and report is as skipped.
                 */
                target.setModified(false);
                target.setSkipped(true);
            }

            /* Closing this stream will also close the underlying file. */
            target.getStream().close();
        }

        /*
         * Close the patched and reject streams so that their content is flushed
         * to disk. This will also close any underlying streams.
         */
        target.getPatched().close();
        target.getReject().close();

        /*
         * Get sizes of the patched temporary file and the working file. We'll
         * need those to figure out whether we should add or delete the patched
         * file.
         */
        final long patchedFileSize = target.getPatchedPath().length();
        final long workingFileSize = target.getKind() == SVNNodeKind.FILE ? target.getPath().length() : 0;

        if (patchedFileSize == 0 && workingFileSize > 0) {
            /*
             * If a unidiff removes all lines from a file, that usually means
             * deletion, so we can confidently schedule the target for deletion.
             * In the rare case where the unidiff was really meant to replace a
             * file with an empty one, this may not be desirable. But the
             * deletion can easily be reverted and creating an empty file
             * manually is not exactly hard either.
             */
            target.setDeleted(target.getKind() != SVNNodeKind.NONE);
        }

        if (target.isDeleted()) {
            if (!dryRun) {
                /*
                 * Schedule the target for deletion. Suppress notification,
                 * we'll do it manually in a minute.
                 */

                // SVN_ERR(svn_wc_delete4(ctx->wc_ctx, target->abs_path,
                // FALSE /* keep_local */, FALSE,
                // ctx->cancel_func, ctx->cancel_baton,
                // NULL, NULL, pool));

                // TODO is this below a good replacement for that above?
                wc.removeFromRevisionControl(target.getPath().getAbsolutePath(), false, false);

            }
        } else {
            if (workingFileSize == 0 && patchedFileSize == 0) {
                /*
                 * The target was empty or non-existent to begin with and
                 * nothing has changed by patching. Report this as skipped if it
                 * didn't exist.
                 */
                if (target.getKind() != SVNNodeKind.FILE) {
                    target.setSkipped(true);
                }
            } else {
                target.setModified(true);

                /*
                 * If the target's parent directory does not yet exist we need
                 * to create it before we can copy the patched result in place.
                 */
                if (target.isAdded() && !target.isParentDirExists()) {

                    /* Check if we can safely create the target's parent. */
                    File abs_path = targetPath.getAbsoluteFile();
                    String[] components = decomposePath(target.getPath());
                    int missing_components = 0;
                    for (int i = 0; i < components.length; i++) {
                        final String component = components[i];
                        abs_path = new File(abs_path, component);

                        final SVNEntry entry = wc.getWCAccess().getEntry(abs_path, false);
                        final SVNNodeKind kind = entry != null ? entry.getKind() : null;

                        if (kind == SVNNodeKind.FILE) {
                            /* Obstructed. */
                            target.setSkipped(true);
                            break;
                        } else if (kind == SVNNodeKind.DIR) {
                            /*
                             * ### wc-ng should eventually be able to replace
                             * directories in-place, so this schedule conflict
                             * check will go away. We could then also make the
                             * svn_wc__node_get_kind() call above ignore hidden
                             * nodes.
                             */
                            if (entry.isDeleted()) {
                                target.setSkipped(true);
                                break;
                            }
                        }

                        missing_components++;
                    }

                    if (!target.isSkipped()) {
                        abs_path = targetPath;
                        for (int i = 0; i < missing_components; i++) {
                            final String component = components[i];
                            abs_path = new File(abs_path, component);
                            if (dryRun) {
                                /* Just do notification. */
                                SVNEvent mergeCompletedEvent = SVNEventFactory.createSVNEvent(abs_path, SVNNodeKind.NONE, null, SVNRepository.INVALID_REVISION, SVNStatusType.INAPPLICABLE,
                                        SVNStatusType.INAPPLICABLE, SVNStatusType.LOCK_INAPPLICABLE, SVNEventAction.ADD, null, null, null);
                                wc.getWCAccess().handleEvent(mergeCompletedEvent);
                            } else {
                                /*
                                 * Create the missing component and add it to
                                 * version control. Suppress cancellation.
                                 */

                                // SVN_ERR(svn_io_dir_make(abs_path,
                                // APR_OS_DEFAULT,
                                // iterpool));
                                //
                                // SVN_ERR(svn_wc_add4(ctx->wc_ctx, abs_path,
                                // svn_depth_infinity,
                                // NULL, SVN_INVALID_REVNUM,
                                // NULL, NULL,
                                // ctx->notify_func2,
                                // ctx->notify_baton2,
                                // iterpool));

                                // TODO is this below a good replacement for
                                // that above?
                                if (abs_path.mkdirs()) {
                                    wc.createVersionedDirectory(abs_path, null, null, null, SVNRepository.INVALID_REVISION, true, SVNDepth.INFINITY);
                                }
                            }
                        }
                    }
                }

                if (!dryRun && !target.isSkipped()) {
                    /* Copy the patched file on top of the target file. */
                    SVNFileUtil.copyFile(target.getPatchedPath(), target.getPath(), false);
                    if (target.isAdded()) {
                        /*
                         * The target file didn't exist previously, so add it to
                         * version control. Suppress notification, we'll do that
                         * later. Also suppress cancellation.
                         */

                        // SVN_ERR(svn_wc_add4(ctx->wc_ctx, target->abs_path,
                        // svn_depth_infinity, NULL, SVN_INVALID_REVNUM, NULL,
                        // NULL, NULL, NULL, pool));

                        // TODO is this below a good replacement for
                        // that above?
                        SVNWCManager.add(target.getPath(), wc, null, SVNRepository.INVALID_REVISION, SVNDepth.INFINITY);
                    }

                    /* Restore the target's executable bit if necessary. */
                    SVNFileUtil.setExecutable(target.getPath(), target.isExecutable());
                }
            }

        }

        /* Write out rejected hunks, if any. */
        if (!dryRun && !target.isSkipped() && target.isHadRejects()) {
            final String rej_path = target.getPath().getPath() + ".svnpatch.rej";
            SVNFileUtil.copyFile(target.getRejectPath(), new File(rej_path), true);
            /* ### TODO mark file as conflicted. */
        }

        target.maybeSendPatchNotification();

    }

    private String[] decomposePath(File path) {
        return SVNAdminArea.fromString(path.getPath(), path.pathSeparator);
    }

    private SVMPatchTarget initPatchTarget(File targetPath, long stripCount) {
        return null;
    }

}
