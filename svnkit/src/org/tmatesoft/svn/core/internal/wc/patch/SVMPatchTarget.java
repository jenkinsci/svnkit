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
import java.io.IOException;
import java.io.InputStream;
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
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
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
    private String canonPathFromPatchfile;
    private RandomAccessFile file;
    private InputStream stream;
    
    private File patchedPath;
    private OutputStream patchedRaw;
    private OutputStream patched;
    private File rejectPath;
    private OutputStream reject;


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

    
    public String getCanonPathFromPatchfile() {
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
        return null;
    }

    public File getAbsPath() {
        return null;
    }

    public File getRelPath() {
        return null;
    }

    
    public void setStream(InputStream stream) {
        this.stream = stream;
    }

    public boolean isHadRejects() {
        return hadRejects;
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

                /* Create a stream to read from the target. */
                new_target.stream = SVNFileUtil.openFileForReading(new_target.absPath);

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

    private static String detectFileEOL(RandomAccessFile file) {
        return null;
    }

    private void resolveTargetPath(File newFilename, File baseDir, long stripCount, SVNAdminArea wc) {
    }

    public void maybeSendPatchNotification() {
    }

    public void rejectHunk(SVNPatchHunkInfo hi) {
    }

    public void applyHunk(SVNPatchHunkInfo hi) {
    }

    public void copyLinesToTarget(long i) {
    }

    public boolean isParentDirExists() {
        return false;
    }

}
