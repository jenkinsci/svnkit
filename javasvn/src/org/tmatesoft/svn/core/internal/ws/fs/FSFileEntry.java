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

package org.tmatesoft.svn.core.internal.ws.fs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.ISVNDirectoryEntry;
import org.tmatesoft.svn.core.ISVNEntryContent;
import org.tmatesoft.svn.core.ISVNFileEntry;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.internal.diff.ISVNRAData;
import org.tmatesoft.svn.core.internal.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.diff.delta.ISVNDeltaGenerator;
import org.tmatesoft.svn.core.internal.diff.delta.SVNAllDeltaGenerator;
import org.tmatesoft.svn.core.internal.diff.delta.SVNSequenceDeltaGenerator;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.TimeUtil;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class FSFileEntry extends FSEntry implements ISVNFileEntry {

    private Map myEntry;

    private File myTempFile;

    public FSFileEntry(FSAdminArea area, FSRootEntry root, String path,
            Map entryProperties) {
        super(area, root, path);
        myEntry = entryProperties;
    }

    public void setPropertyValue(String name, String value) throws SVNException {
        if (SVNProperty.UUID.equals(name)) {
            return;
        }
        super.setPropertyValue(name, value);
    }

    public boolean isDirectory() {
        return false;
    }

    public boolean isObstructed() {
        return super.isObstructed()
                || getRootEntry().getWorkingCopyFile(this).isDirectory();
    }

    public void applyDelta(SVNDiffWindow window, InputStream newData,
            boolean overwrite) throws SVNException {
        if (overwrite) {
            ISVNRAData contents = null;
            if (window == null) {
                try {
                    myTempFile = getRootEntry().createTemporaryFile(this);
                    myTempFile.createNewFile();
                } catch (IOException e) {
                    throw new SVNException(e);
                }
            } else {
                if (!isBinary()
                        && SVNProperty.EOL_STYLE_NATIVE
                                .equals(getPropertyValue(SVNProperty.EOL_STYLE))) {
                    if (myTempFile == null) {
                        myTempFile = getRootEntry().createTemporaryFile(this);
                    }
                    contents = new SVNRAFileData(myTempFile, false);
                } else {
                    contents = new SVNRAFileData(getRootEntry()
                            .getWorkingCopyFile(this), false);
                }
                window.apply(contents, contents, newData, contents.length());
            }
            return;
        }
        if (isScheduledForAddition()) {
            return;
        }
        if (window == null) {
            File newFile = getAdminArea().getTemporaryBaseFile(this);
            try {
                newFile.getParentFile().mkdirs();
                newFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                throw new SVNException(e);
            }
        } else {
            if (myIsCheckout || !getAdminArea().getBaseFile(this).exists()) {
                ISVNRAData source = new SVNRAFileData(getAdminArea()
                        .getBaseFile(this), false);
                long offset = 0;
                if (getAdminArea().getBaseFile(this).exists()) {
                    offset = getAdminArea().getBaseFile(this).length();
                }
                window.apply(source, source, newData, offset);
                myIsCheckout = true;

            } else {
                ISVNRAData source = new SVNRAFileData(getAdminArea()
                        .getBaseFile(this), true);
                ISVNRAData target = new SVNRAFileData(getAdminArea()
                        .getTemporaryBaseFile(this), false);
                long offset = 0;
                if (getAdminArea().getTemporaryBaseFile(this).exists()) {
                    offset = getAdminArea().getTemporaryBaseFile(this).length();
                }
                window.apply(source, target, newData, offset);
            }
        }
    }

    private boolean myIsCheckout;

    public int deltaApplied(boolean overwrite) throws SVNException {
        if (myIsCheckout) {
            myIsCheckout = false;
            return SVNStatus.UPDATED;
        }
        if (!overwrite && isContentsModified()) {
            return getRootEntry().getMerger().pretendMergeFiles(
                    getAdminArea().getBaseFile(this),
                    getRootEntry().getWorkingCopyFile(this),
                    getAdminArea().getTemporaryBaseFile(this));
        } else if (overwrite) {
            File dst = getRootEntry().getWorkingCopyFile(this);
            if (myTempFile != null) {
                FSUtil.copy(myTempFile, dst, isBinary() ? null
                        : SVNProperty.EOL_STYLE_NATIVE, null);
                myTempFile.delete();
                myTempFile = null;
            }
            if (getRootEntry().isUseCommitTimes()) {
                String date = getPropertyValue(SVNProperty.COMMITTED_DATE);
                if (date != null) {
                    long lm = TimeUtil.parseDate(date).getTime();
                    dst.setLastModified(lm);
                }
            }
        }
        return SVNStatus.UPDATED;
    }

    public String generateDelta(String commitPath, ISVNEditor target)
            throws SVNException {
        if (!isContentsModified() && !isScheduledForAddition()) {
            return null;
        }
        String digest = null;
        File file = getRootEntry().getWorkingCopyFile(this);
        if (file.exists()) {
            target.applyTextDelta(commitPath, null);
            String eolType = getPropertyValue(SVNProperty.EOL_STYLE);
            boolean sendAsIs = isBinary();// || eolType == null;
            File tmpFile = null;
            if (!sendAsIs) {
                if (SVNProperty.EOL_STYLE_NATIVE.equals(eolType)) {
                    eolType = SVNProperty.EOL_STYLE_LF;
                }
                tmpFile = getRootEntry().createTemporaryFile(this);
                // prepare file as to be sent
                Map keywords = computeKeywords(false);
                FSUtil.copy(file, tmpFile, eolType, keywords, null);
            }
            ISVNDeltaGenerator generator;
            if (isBinary() || isScheduledForAddition()
                    || DebugLog.isGeneratorDisabled()
                    || !getAdminArea().hasBaseFile(this)) {
                generator = new SVNAllDeltaGenerator();
            } else {
                generator = new SVNSequenceDeltaGenerator();
            }

            File digestFile = getRootEntry().createTemporaryFile(this);
            digest = FSUtil.copy(tmpFile != null ? tmpFile : file, digestFile,
                    null, null, createDigest());
            SVNRAFileData workFile = new SVNRAFileData(
                    tmpFile != null ? tmpFile : file, true);
            SVNRAFileData baseFile = generator instanceof SVNSequenceDeltaGenerator ? new SVNRAFileData(
                    getAdminArea().getBaseFile(this), true)
                    : null;
            try {
                generator.generateDiffWindow(commitPath, target, workFile,
                        baseFile);
            } finally {
                try {
                    if (baseFile != null) {
                        baseFile.close();
                    }
                } catch (IOException e2) {
                }
                try {
                    workFile.close();
                } catch (IOException e1) {
                }
                if (tmpFile != null) {
                    tmpFile.delete();
                }
                if (digestFile != null) {
                    digestFile.delete();
                }
            }
        }
        return digest;
    }

    public boolean isContentsModified() throws SVNException {
        if (isPropertyModified(SVNProperty.EOL_STYLE)
                || isPropertyModified(SVNProperty.KEYWORDS)
                || isPropertyModified(SVNProperty.SPECIAL)) {
            return true;
        }
        if (isMissing() || isScheduledForDeletion()) {
            return false;
        }
        File file = getRootEntry().getWorkingCopyFile(this);
        String timeStamp = (String) getEntry().get(SVNProperty.TEXT_TIME);
        if (timeStamp != null) {
            long time = TimeUtil.parseDate(timeStamp).getTime();
            if (time != file.lastModified()) {
                return isContentsDifferent();
            }
        }
        return false;
    }

    public ISVNEntryContent getContent() throws SVNException {
        return new FSFileEntryContent(this);
    }

    private boolean isContentsDifferent() throws SVNException {
        File file = getRootEntry().getWorkingCopyFile(this);
        File baseFile = getAdminArea().getBaseFile(this);
        if (!baseFile.exists()) {
            return false;
        }
        File tmpFile = null;
        try {
            if (!isBinary() && getPropertyValue(SVNProperty.KEYWORDS) != null) {
                // unexpand keywords before comparing files... (for text files
                // only).
                // only when there are keywords
                tmpFile = getRootEntry().createTemporaryFile(this);
                Map keywords = computeKeywords(false);
                FSUtil.copy(file, tmpFile, null, keywords, null);
            }
            // no keywords, use original file.
            return !FSUtil.compareFiles(baseFile, tmpFile == null ? file
                    : tmpFile, isBinary());
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
            if (tmpFile != null) {
                tmpFile.delete();
            }
        }
    }

    public boolean isCorrupted() throws SVNException {
        File baseFile = getAdminArea().getBaseFile(this);
        if (getPropertyValue(SVNProperty.CHECKSUM) == null) {
            return false;
        }
        if (!baseFile.exists() && isScheduledForAddition()) {
            return false;
        }
        String checksum = FSUtil.getChecksum(baseFile, createDigest());
        if (checksum == null
                || !checksum.equals(getPropertyValue(SVNProperty.CHECKSUM))) {
            return true;
        }
        return false;
    }

    public void commit() throws SVNException {
        super.commit();

        File actualFile = getRootEntry().getWorkingCopyFile(this);
        File baseFile = getAdminArea().getBaseFile(this);
        String checksum = null;
        Map keywords = computeKeywords(true);
        if (actualFile.exists()) {
            String eolStyle = getPropertyValue(SVNProperty.EOL_STYLE);
            if (!isBinary()) {// &&
                                // ((isPropertyModified(SVNProperty.EOL_STYLE)
                                // && eolStyle != null) ||
                                // isPropertyModified(SVNProperty.KEYWORDS))) {
                File tmpFile = getRootEntry().createTemporaryFile(this);
                FSUtil.copy(actualFile, tmpFile, eolStyle,
                        computeKeywords(false), null);
                FSUtil.copy(tmpFile, actualFile, null, keywords, null);
                tmpFile.delete();
            }
            boolean storeAsIs = isBinary() || eolStyle == null;
            if (SVNProperty.EOL_STYLE_NATIVE.equals(eolStyle)) {
                eolStyle = SVNProperty.EOL_STYLE_LF;
            }
            checksum = FSUtil.copy(actualFile, baseFile, storeAsIs ? null
                    : eolStyle, isBinary() ? null : computeKeywords(false),
                    createDigest());
            if (!getRootEntry().isUseCommitTimes()) {
                Date date = new Date(actualFile.lastModified());
                getEntry()
                        .put(SVNProperty.TEXT_TIME, TimeUtil.formatDate(date));
            } else {
                String date = (String) getPropertyValue(SVNProperty.COMMITTED_DATE);
                getEntry().put(SVNProperty.TEXT_TIME, date);
                actualFile.setLastModified(TimeUtil.parseDate(date).getTime());
            }
        } else {
            baseFile.delete();
            getEntry().remove(SVNProperty.TEXT_TIME);
        }
        if (checksum == null) {
            getEntry().remove(SVNProperty.CHECKSUM);
        } else {
            getEntry().put(SVNProperty.CHECKSUM, checksum);
        }
        if (getPropertyValue(SVNProperty.NEEDS_LOCK) != null
                && getPropertyValue(SVNProperty.LOCK_TOKEN) == null) {
            FSUtil.setReadonly(getRootEntry().getWorkingCopyFile(this), true);
        }
    }

    public void merge(boolean recursive) throws SVNException {
        if (isScheduledForAddition()) {
            return;
        }
        boolean isNewKeywords = isPropertyModified(SVNProperty.KEYWORDS);
        super.merge();
        // force properties file save
        File basePropsFile = getAdminArea().getBasePropertiesFile(this);
        if (!basePropsFile.exists()) {
            // empty props, may be was not changed but not yet saved (checkout)
            // empty dir props are never saved, only for files.
            getAdminArea().saveBaseProperties(this, Collections.EMPTY_MAP);
            getAdminArea().saveProperties(this, Collections.EMPTY_MAP);
            long lastModified = getAdminArea().propertiesLastModified(this);
            if (lastModified != 0) {
                getEntry().put(SVNProperty.PROP_TIME,
                        TimeUtil.formatDate(new Date(lastModified)));
            } else {
                getEntry().remove(SVNProperty.PROP_TIME);
            }
        }

        File tmpBaseFile = getAdminArea().getTemporaryBaseFile(this);
        File actualFile = getRootEntry().getWorkingCopyFile(this);
        File baseFile = getAdminArea().getBaseFile(this);
        Map keywords = computeKeywords(true);

        if (!tmpBaseFile.exists() && !isScheduledForDeletion()) {
            if (baseFile.exists() && (!actualFile.exists() || isNewKeywords)) {
                FSUtil.copy(baseFile, actualFile,
                        !isBinary() ? getPropertyValue(SVNProperty.EOL_STYLE)
                                : null, isBinary() ? null : keywords, null);
                updateTextTime(actualFile);
            }
            updateReadonlyState();
            return;
        }
        String checksum = null;
        if (isScheduledForDeletion()) {
            DebugLog.log("merging deleted file: " + getPath());
            DebugLog.log("tmpBaseFile: " + tmpBaseFile.getAbsolutePath());
            DebugLog.log("tmpBaseFile.exists(): " + tmpBaseFile.exists());
            if (tmpBaseFile.exists()) {
                checksum = FSUtil.copy(tmpBaseFile, baseFile, null,
                        createDigest());
                DebugLog.log("deleted file merged: " + getPath());
            }
        } else if (!isContentsModified()) {
            String eolStyle = isBinary() ? null
                    : getPropertyValue(SVNProperty.EOL_STYLE);

            checksum = FSUtil.copy(tmpBaseFile, baseFile, null, createDigest());
            FSUtil.copy(baseFile, actualFile, isBinary() ? null : eolStyle,
                    isBinary() ? null : keywords, null);
            updateTextTime(actualFile);
        } else {
            int mergeResult = SVNStatus.CONFLICTED;
            File localFile = null;
            if (!isBinary()) {
                // try to merge
                FSMerger merger = getRootEntry().getMerger();
                File remote = getAdminArea().getTemporaryBaseFile(this);
                File base = getAdminArea().getBaseFile(this);

                localFile = getRootEntry().createTemporaryFile(this);
                File result = getRootEntry().createTemporaryFile(this);
                // no need to convert eols, merger should merge just lines.
                FSUtil.copy(getRootEntry().getWorkingCopyFile(this), localFile,
                        null, computeKeywords(false), null);
                mergeResult = merger
                        .mergeFiles(
                                base,
                                localFile,
                                remote,
                                result,
                                ".mine",
                                ".r"
                                        + getPropertyValue(SVNProperty.COMMITTED_REVISION));
                localFile.delete();
                localFile = result;
            }
            if (mergeResult == SVNStatus.CONFLICTED) {
                File newRevFile = new File(getRootEntry().getWorkingCopyFile(
                        this).getParentFile(), getName() + ".r"
                        + getPropertyValue(SVNProperty.COMMITTED_REVISION));
                File oldRevFile = new File(getRootEntry().getWorkingCopyFile(
                        this).getParentFile(), getName() + ".r"
                        + getOldRevision());

                FSUtil.copy(getAdminArea().getTemporaryBaseFile(this),
                        newRevFile, null, null, null);
                FSUtil.copy(getAdminArea().getBaseFile(this), oldRevFile, null,
                        null, null);

                setPropertyValue(SVNProperty.CONFLICT_OLD, oldRevFile.getName());
                setPropertyValue(SVNProperty.CONFLICT_NEW, newRevFile.getName());
                if (!isBinary()) {
                    // save wc to "mine"
                    File mineFile = new File(getRootEntry().getWorkingCopyFile(
                            this).getParentFile(), getName() + ".mine");
                    FSUtil.copy(getRootEntry().getWorkingCopyFile(this),
                            mineFile, null, null, null);
                    setPropertyValue(SVNProperty.CONFLICT_WRK, mineFile
                            .getName());
                }
            }
            if (localFile != null) {
                // replace "wc" with merge result.
                keywords = computeKeywords(true);
                String eolStyle = getPropertyValue(SVNProperty.EOL_STYLE);
                FSUtil.copy(localFile, getRootEntry().getWorkingCopyFile(this),
                        eolStyle, keywords, null);
                localFile.delete();
            }
            checksum = FSUtil.copy(getAdminArea().getTemporaryBaseFile(this),
                    getAdminArea().getBaseFile(this), null, null,
                    createDigest());
        }
        if (getPropertyValue(SVNProperty.CHECKSUM) == null) {
            setPropertyValue(SVNProperty.CHECKSUM, checksum);
        } else if (checksum != null
                && !checksum.equals(getPropertyValue(SVNProperty.CHECKSUM))) {
            throw new SVNException(getPath()
                    + " local checksum differs from repository.");
        }
        getAdminArea().deleteTemporaryBaseFile(this);
        updateReadonlyState();
    }

    private void updateReadonlyState() throws SVNException {
        boolean setReadonly = false;
        boolean setReadWrite = false;
        if (isPropertyModified(SVNProperty.NEEDS_LOCK)
                && getPropertyValue(SVNProperty.NEEDS_LOCK) != null) {
            setReadonly = !isContentsModified();
        } else if (isPropertyModified(SVNProperty.NEEDS_LOCK)
                && getPropertyValue(SVNProperty.NEEDS_LOCK) == null) {
            setReadWrite = true;
        } else if (getPropertyValue(SVNProperty.NEEDS_LOCK) != null
                && getPropertyValue(SVNProperty.LOCK_TOKEN) == null
                && isLockPropertyChanged()) {
            setReadonly = !isContentsModified();
        }
        if (setReadonly) {
            FSUtil.setReadonly(getRootEntry().getWorkingCopyFile(this), true);
        } else if (setReadWrite) {
            FSUtil.setReadonly(getRootEntry().getWorkingCopyFile(this), false);
        }
    }

    public void save(boolean recursive) throws SVNException {
        super.save(recursive);
        if (getPropertyValue(SVNProperty.NEEDS_LOCK) == null) {
            FSUtil.setReadonly(getRootEntry().getWorkingCopyFile(this), false);
        } else {
            FSUtil.setReadonly(getRootEntry().getWorkingCopyFile(this),
                    getPropertyValue(SVNProperty.LOCK_TOKEN) == null);
        }
    }

    private void updateTextTime(File file) throws SVNException {
        long lastModified = file.lastModified();
        if (getRootEntry().isUseCommitTimes()) {
            String commitDate = (String) getPropertyValue(SVNProperty.COMMITTED_DATE);
            if (commitDate != null) {
                lastModified = TimeUtil.parseDate(commitDate).getTime();
                file.setLastModified(lastModified);
            }
        }
        getEntry().put(SVNProperty.TEXT_TIME,
                TimeUtil.formatDate(new Date(lastModified)));
    }

    public boolean isConflict() throws SVNException {
        if (super.isConflict()) {
            return true;
        }
        return getPropertyValue(SVNProperty.CONFLICT_OLD) != null
                || getPropertyValue(SVNProperty.CONFLICT_NEW) != null
                || getPropertyValue(SVNProperty.CONFLICT_NEW) != null;
    }

    public void markResolved() throws SVNException {
        markResolved(false);
    }

    public void markResolved(boolean contentsOnly) throws SVNException {
        if (!contentsOnly) {
            super.markResolved();
        }
        // remove properties
        String oldFileName = getPropertyValue(SVNProperty.CONFLICT_OLD);
        String newFileName = getPropertyValue(SVNProperty.CONFLICT_NEW);
        String wrkFileName = getPropertyValue(SVNProperty.CONFLICT_WRK);
        setPropertyValue(SVNProperty.CONFLICT_NEW, null);
        setPropertyValue(SVNProperty.CONFLICT_OLD, null);
        setPropertyValue(SVNProperty.CONFLICT_WRK, null);

        File folder = getRootEntry().getWorkingCopyFile(this).getParentFile();
        if (oldFileName != null) {
            new File(folder, oldFileName).delete();
        }
        if (newFileName != null) {
            new File(folder, newFileName).delete();
        }
        if (wrkFileName != null) {
            new File(folder, wrkFileName).delete();
        }
    }

    public void restoreContents() throws SVNException {
        // replace working copy with base.
        restoreProperties();
        File base = getAdminArea().getBaseFile(this);
        File local = getRootEntry().getWorkingCopyFile(this);
        if (base.exists()) {
            FSUtil.copy(base, local, isBinary() ? null
                    : getPropertyValue(SVNProperty.EOL_STYLE),
                    isBinary() ? null : computeKeywords(true), null);
            updateTextTime(local);
        } else {
            local.delete();
        }
    }

    public ISVNFileEntry asFile() {
        return this;
    }

    public ISVNDirectoryEntry asDirectory() {
        return null;
    }

    protected Map getEntry() {
        return myEntry;
    }

    protected boolean isBinary() throws SVNException {
        String type = getPropertyValue(SVNProperty.MIME_TYPE);
        return !(type == null || type.startsWith("text/"));
    }

    static MessageDigest createDigest() throws SVNException {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new SVNException(e);
        }
    }

    public Map computeKeywords(boolean expand) throws SVNException {
        String keywordsProperty = getPropertyValue(SVNProperty.KEYWORDS);
        if (keywordsProperty == null) {
            return Collections.EMPTY_MAP;
        }
        Map map = new HashMap();
        for (StringTokenizer tokens = new StringTokenizer(keywordsProperty, " "); tokens
                .hasMoreTokens();) {
            String token = tokens.nextToken();
            if ("LastChangedDate".equals(token) || "Date".equals(token)) {
                String dateStr = getPropertyValue(SVNProperty.COMMITTED_DATE);
                dateStr = TimeUtil.toHumanDate(dateStr);
                map.put("LastChangedDate", expand ? dateStr : null);
                map.put("Date", expand ? dateStr : null);
            } else if ("LastChangedRevision".equals(token)
                    || "Revision".equals(token) || "Rev".equals(token)) {
                String revStr = getPropertyValue(SVNProperty.COMMITTED_REVISION);
                map.put("LastChangedRevision", expand ? revStr : null);
                map.put("Revision", expand ? revStr : null);
                map.put("Rev", expand ? revStr : null);
            } else if ("LastChangedBy".equals(token) || "Author".equals(token)) {
                String author = getPropertyValue(SVNProperty.LAST_AUTHOR);
                author = author == null ? "" : author;
                map.put("LastChangedBy", expand ? author : null);
                map.put("Author", expand ? author : null);
            } else if ("HeadURL".equals(token) || "URL".equals(token)) {
                String url = getPropertyValue(SVNProperty.URL);
                map.put("HeadURL", expand ? url : null);
                map.put("URL", expand ? url : null);
            } else if ("Id".equals(token)) {
                StringBuffer id = new StringBuffer();
                id.append(getName());
                id.append(' ');
                id.append(getPropertyValue(SVNProperty.COMMITTED_REVISION));
                id.append(' ');
                String dateStr = getPropertyValue(SVNProperty.COMMITTED_DATE);
                dateStr = TimeUtil.toHumanDate(dateStr);
                id.append(dateStr);
                id.append(' ');
                String author = getPropertyValue(SVNProperty.LAST_AUTHOR);
                author = author == null ? "" : author;
                id.append(author);
                map.put("Id", expand ? id.toString() : null);
            }
        }
        return map;
    }
}
