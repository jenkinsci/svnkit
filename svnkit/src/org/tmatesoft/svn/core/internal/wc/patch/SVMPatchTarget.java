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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNStatusUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVMPatchTarget {

    private SVNPatch patch;
    private List lines;
    private List hunks;

    private boolean localMods;
    private boolean executable;
    private boolean skipped;
    private String eolStr;
    private Map keywords;
    private String eolStyle;
    private SVNNodeKind kind;
    private int currentLine;
    private boolean modified;
    private boolean hadRejects;
    private boolean deleted;
    private boolean eof;
    private boolean added;

    private File absPath;
    private File relPath;
    private File canonPathFromPatchfile;

    private RandomAccessFile file;
    private InputStream stream;

    private File patchedPath;
    private OutputStream patchedRaw;
    private OutputStream patched;
    private File rejectPath;
    private OutputStream reject;
    private boolean parentDirExists;

    private SVMPatchTarget() {
    }

    public boolean isLocalMods() {
        return localMods;
    }

    public String getEolStr() {
        return eolStr;
    }

    public Map getKeywords() {
        return keywords;
    }

    public String getEolStyle() {
        return eolStyle;
    }

    public RandomAccessFile getFile() {
        return file;
    }

    public OutputStream getPatchedRaw() {
        return patchedRaw;
    }

    public File getCanonPathFromPatchfile() {
        return canonPathFromPatchfile;
    }

    public SVNPatch getPatch() {
        return patch;
    }

    public int getCurrentLine() {
        return currentLine;
    }

    public boolean isModified() {
        return modified;
    }

    public boolean isEof() {
        return eof;
    }

    public List getLines() {
        return lines;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public List getHunks() {
        return hunks;
    }

    public SVNNodeKind getKind() {
        return kind;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public InputStream getStream() {
        return stream;
    }

    public OutputStream getPatched() {
        return patched;
    }

    public OutputStream getReject() {
        return reject;
    }

    public File getPatchedPath() {
        return patchedPath;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isAdded() {
        return added;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isExecutable() {
        return executable;
    }

    public File getRejectPath() {
        return rejectPath;
    }

    public File getAbsPath() {
        return absPath;
    }

    public File getRelPath() {
        return relPath;
    }

    public void setStream(InputStream stream) {
        this.stream = stream;
    }

    public boolean isHadRejects() {
        return hadRejects;
    }

    public boolean isParentDirExists() {
        return parentDirExists;
    }

    /**
     * Attempt to initialize a patch TARGET structure for a target file
     * described by PATCH. Use client context CTX to send notifiations and
     * retrieve WC_CTX. STRIP_COUNT specifies the number of leading path
     * components which should be stripped from target paths in the patch. Upon
     * success, return the patch target structure. Else, return NULL.
     * 
     * @throws SVNException
     * @throws IOException
     */
    public static SVMPatchTarget initPatchTarget(SVNPatch patch, File baseDir, long stripCount, SVNAdminArea wc) throws SVNException, IOException {

        final SVMPatchTarget new_target = new SVMPatchTarget();
        new_target.resolveTargetPath(patch.getNewFilename(), baseDir, stripCount, wc);

        new_target.localMods = false;
        new_target.executable = false;

        if (!new_target.skipped) {

            final String nativeEOLMarker = SVNFileUtil.getNativeEOLMarker(wc.getWCAccess().getOptions());
            new_target.eolStr = nativeEOLMarker;
            new_target.keywords = null;
            new_target.eolStyle = null;

            if (new_target.kind == SVNNodeKind.FILE) {

                /* Open the file. */
                new_target.file = SVNFileUtil.openRAFileForReading(new_target.absPath);
                /* Create a stream to read from the target. */
                new_target.stream = SVNFileUtil.openFileForReading(new_target.absPath);

                /* Handle svn:keyword and svn:eol-style properties. */
                SVNVersionedProperties props = wc.getProperties(new_target.absPath.getAbsolutePath());
                String keywords_val = props.getStringPropertyValue(SVNProperty.KEYWORDS);
                if (null != keywords_val) {
                    SVNEntry entry = wc.getEntry(new_target.absPath.getAbsolutePath(), false);
                    long changed_rev = entry.getRevision();
                    String author = entry.getAuthor();
                    String changed_date = entry.getCommittedDate();
                    String url = entry.getURL();
                    String rev_str = Long.toString(changed_rev);
                    new_target.keywords = SVNTranslator.computeKeywords(keywords_val, url, author, changed_date, rev_str, wc.getWCAccess().getOptions());
                }

                String eol_style_val = props.getStringPropertyValue(SVNProperty.EOL_STYLE);
                if (null != eol_style_val) {
                    new_target.eolStyle = new String(SVNTranslator.getEOL(eol_style_val, wc.getWCAccess().getOptions()));
                } else {
                    /* Just use the first EOL sequence we can find in the file. */
                    new_target.eolStr = detectFileEOL(new_target.file);
                    /* But don't enforce any particular EOL-style. */
                    new_target.eolStyle = null;
                }

                if (new_target.eolStyle == null) {
                    /*
                     * We couldn't figure out the target files's EOL scheme,
                     * just use native EOL makers.
                     */
                    new_target.eolStr = nativeEOLMarker;
                    new_target.eolStyle = SVNProperty.EOL_STYLE_NATIVE;
                }

                /* Also check the file for local mods and the Xbit. */
                new_target.localMods = wc.hasTextModifications(new_target.absPath.getAbsolutePath(), false);
                new_target.executable = SVNFileUtil.isExecutable(new_target.absPath);

            }

            /*
             * Create a temporary file to write the patched result to. Expand
             * keywords in the patched file.
             */
            new_target.patchedPath = SVNFileUtil.createTempFile("", null);
            new_target.patchedRaw = SVNFileUtil.openFileForWriting(new_target.patchedPath);
            new_target.patched = SVNTranslator.getTranslatingOutputStream(new_target.patchedRaw, null, new_target.eolStr.getBytes(), new_target.eolStyle != null, new_target.keywords, true);

            /*
             * We'll also need a stream to write rejected hunks to. We don't
             * expand keywords, nor normalise line-endings, in reject files.
             */
            new_target.rejectPath = SVNFileUtil.createTempFile("", null);
            new_target.reject = SVNFileUtil.openFileForWriting(new_target.rejectPath);

            /* The reject stream needs a diff header. */
            String diff_header = "--- " + new_target.canonPathFromPatchfile + nativeEOLMarker + "+++ " + new_target.canonPathFromPatchfile + nativeEOLMarker;

            new_target.reject.write(diff_header.getBytes());

        }

        new_target.patch = patch;
        new_target.currentLine = 1;
        new_target.modified = false;
        new_target.hadRejects = false;
        new_target.deleted = false;
        new_target.eof = false;
        new_target.lines = new ArrayList();
        new_target.hunks = new ArrayList();

        return new_target;

    }

    /**
     * Detect the EOL marker used in file and return it. If it cannot be
     * detected, return NULL.
     * 
     * The file is searched starting at the current file cursor position. The
     * first EOL marker found will be returnd. So if the file has inconsistent
     * EOL markers, this won't be detected.
     * 
     * Upon return, the original file cursor position is always preserved, even
     * if an error is thrown.
     */
    private static String detectFileEOL(RandomAccessFile file) throws IOException {
        /* Remember original file offset. */
        final long pos = file.getFilePointer();
        try {
            BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file.getFD()));
            try {
                StringBuffer buf = new StringBuffer();
                int b1;
                while ((b1 = stream.read()) > 0) {
                    final char c1 = (char) b1;
                    if (c1 == '\n' || c1 == '\r') {
                        buf.append(c1);
                        if (c1 == '\r') {
                            final int b2 = stream.read();
                            if (b2 > 0) {
                                final char c2 = (char) b2;
                                if (c2 == '\n') {
                                    buf.append(c2);
                                }
                            }
                        }
                        return buf.toString();
                    }
                }
            } finally {
                stream.close();
            }
        } finally {
            file.seek(pos);
        }
        return null;
    }

    /**
     * Resolve the exact path for a patch TARGET at path PATH_FROM_PATCHFILE,
     * which is the path of the target as it appeared in the patch file. Put a
     * canonicalized version of PATH_FROM_PATCHFILE into
     * TARGET->CANON_PATH_FROM_PATCHFILE. WC_CTX is a context for the working
     * copy the patch is applied to. If possible, determine TARGET->WC_PATH,
     * TARGET->ABS_PATH, TARGET->KIND, TARGET->ADDED, and
     * TARGET->PARENT_DIR_EXISTS. Indicate in TARGET->SKIPPED whether the target
     * should be skipped. STRIP_COUNT specifies the number of leading path
     * components which should be stripped from target paths in the patch.
     * 
     * @throws SVNException
     * @throws IOException
     */
    private void resolveTargetPath(File pathFromPatchfile, File absWCPath, long stripCount, SVNAdminArea wc) throws SVNException, IOException {

        final SVMPatchTarget target = this;

        target.canonPathFromPatchfile = pathFromPatchfile.getCanonicalFile();

        if ("".equals(target.canonPathFromPatchfile.getPath())) {
            /* An empty patch target path? What gives? Skip this. */
            target.skipped = true;
            target.kind = SVNNodeKind.FILE;
            target.absPath = null;
            target.relPath = null;
            return;
        }

        File stripped_path;
        if (stripCount > 0) {
            stripped_path = stripPath(target.canonPathFromPatchfile, stripCount);
        } else {
            stripped_path = target.canonPathFromPatchfile;
        }

        if (stripped_path.isAbsolute()) {

            target.relPath = getChildPath(absWCPath, stripped_path);

            if (null == target.relPath) {
                /*
                 * The target path is either outside of the working copy or it
                 * is the working copy itself. Skip it.
                 */
                target.skipped = true;
                target.kind = SVNNodeKind.FILE;
                target.absPath = null;
                target.relPath = stripped_path;
                return;
            }
        } else {
            target.relPath = stripped_path;
        }

        /*
         * Make sure the path is secure to use. We want the target to be inside
         * of the working copy and not be fooled by symlinks it might contain.
         */
        if (null == (target.absPath = getPathUnderRoot(absWCPath, target.relPath))) {
            /* The target path is outside of the working copy. Skip it. */
            target.skipped = true;
            target.kind = SVNNodeKind.FILE;
            target.absPath = null;
            return;
        }

        /* Skip things we should not be messing with. */

        final SVNStatus status = SVNStatusUtil.getStatus(target.absPath, wc.getWCAccess());
        final SVNStatusType contentsStatus = status.getContentsStatus();

        if (contentsStatus == SVNStatusType.STATUS_UNVERSIONED || contentsStatus == SVNStatusType.STATUS_IGNORED || contentsStatus == SVNStatusType.STATUS_OBSTRUCTED) {
            target.skipped = true;
            target.kind = SVNFileType.getNodeKind(SVNFileType.getType(target.absPath));
            return;
        }

        target.kind = status.getKind();

        if (SVNNodeKind.FILE.equals(target.kind)) {
            
            target.added = false;
            target.parentDirExists = true;
            
        } else if (SVNNodeKind.NONE.equals(target.kind) || SVNNodeKind.UNKNOWN.equals(target.kind)) {

            /*
             * The file is not there, that's fine. The patch might want to
             * create it. Check if the containing directory of the target
             * exists. We may need to create it later.
             */
            target.added = true;
            File absDirname = target.absPath.getParentFile();

            final SVNStatus status2 = SVNStatusUtil.getStatus(absDirname, wc.getWCAccess());
            final SVNStatusType contentsStatus2 = status2.getContentsStatus();
            SVNNodeKind kind = status2.getKind();
            target.parentDirExists = (kind == SVNNodeKind.DIR && contentsStatus2 != SVNStatusType.STATUS_DELETED && contentsStatus2 != SVNStatusType.STATUS_MISSING);
        
        } else {
            target.skipped = true;
        }

        return;
    }

    /**
     * Check that when @a path is joined to @a base_path, the resulting path is
     * still under BASE_PATH in the local filesystem. If not, return @c FALSE.
     * If @c TRUE is returned, @a *full_path will be set to the absolute path of @a
     * path, allocated in @a pool.
     */
    private File getPathUnderRoot(File absWCPath, File relPath2) {
        return null;
    }

    private File getChildPath(File absWCPath, File strippedPath) {
        return null;
    }

    private File stripPath(File canonPathFromPatchfile2, long stripCount) {
        return null;
    }

    public void maybeSendPatchNotification() {
    }

    public void rejectHunk(SVNPatchHunkInfo hi) {
    }

    public void applyHunk(SVNPatchHunkInfo hi) {
    }

    public void copyLinesToTarget(long i) {
    }

}
