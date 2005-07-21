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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.TimeUtil;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNLogRunner {

    private boolean myIsEntriesChanged;

    public void runCommand(SVNDirectory dir, String name, Map attributes)
            throws SVNException {
        String fileName = (String) attributes.remove(SVNLog.NAME_ATTR);
        if (SVNLog.DELETE_ENTRY.equals(name)) {
            // check if it is not disjoint entry not to delete another wc?
            dir.destroy(fileName, true);
        } else if (SVNLog.MODIFY_ENTRY.equals(name)) {
            SVNEntries entries = dir.getEntries();
            boolean modified = false;
            if (entries.getEntry(fileName, true) == null) {
                entries.addEntry(fileName);
                modified = true;
            }
            for (Iterator atts = attributes.keySet().iterator(); atts.hasNext();) {
                String attName = (String) atts.next();
                String value = (String) attributes.get(attName);
                attName = SVNProperty.SVN_ENTRY_PREFIX + attName;

                if (SVNLog.WC_TIMESTAMP.equals(value)) {
                    if (SVNProperty.PROP_TIME.equals(attName)) {
                        String path = "".equals(fileName) ? ".svn/dir-props"
                                : ".svn/props/" + fileName + ".svn-work";
                        File file = new File(dir.getRoot(), path);
                        value = TimeUtil.formatDate(new Date(file
                                .lastModified()));
                    } else if (SVNProperty.TEXT_TIME.equals(attName)) {
                        String path = "".equals(fileName) ? "" : fileName;
                        File file = new File(dir.getRoot(), path);
                        value = TimeUtil.formatDate(new Date(file
                                .lastModified()));
                    }
                }

                entries.setPropertyValue(fileName, attName, value);
                modified = true;
            }
            setEntriesChanged(modified);
        } else if (SVNLog.MODIFY_WC_PROPERTY.equals(name)) {
            SVNProperties props = dir.getWCProperties(fileName);
            String propName = (String) attributes
                    .get(SVNLog.PROPERTY_NAME_ATTR);
            String propValue = (String) attributes
                    .get(SVNLog.PROPERTY_VALUE_ATTR);
            props.setPropertyValue(propName, propValue);
        } else if (SVNLog.DELETE_LOCK.equals(name)) {
            SVNEntries entries = dir.getEntries();
            SVNEntry entry = entries.getEntry(fileName, true);
            if (entry != null) {
                entry.setLockToken(null);
                entry.setLockOwner(null);
                entry.setLockCreationDate(null);
                entry.setLockComment(null);
                setEntriesChanged(true);
            }
        } else if (SVNLog.DELETE.equals(name)) {
            File file = new File(dir.getRoot(), fileName);
            file.delete();
        } else if (SVNLog.READONLY.equals(name)) {
            File file = new File(dir.getRoot(), fileName);
            SVNFileUtil.setReadonly(file, true);
        } else if (SVNLog.MOVE.equals(name)) {
            File src = new File(dir.getRoot(), fileName);
            File dst = new File(dir.getRoot(), (String) attributes
                    .get(SVNLog.DEST_ATTR));
            if (!src.exists() && dst.exists()) {
                return;
            }
            SVNFileUtil.rename(src, dst);
        } else if (SVNLog.APPEND.equals(name)) {
            File src = new File(dir.getRoot(), fileName);
            File dst = new File(dir.getRoot(), (String) attributes
                    .get(SVNLog.DEST_ATTR));
            OutputStream os = null;
            InputStream is = null;
            try {
                os = SVNFileUtil.openFileForWriting(dst, true);
                is = SVNFileUtil.openFileForReading(src);
                while (true) {
                    int r = is.read();
                    if (r < 0) {
                        break;
                    }
                    os.write(r);
                }
            } catch (IOException e) {
                SVNErrorManager.error("svn: Cannot append data to file '" + dst + "'");
            } finally {
                SVNFileUtil.closeFile(os);
                SVNFileUtil.closeFile(is);
            }
        } else if (SVNLog.SET_TIMESTAMP.equals(name)) {
            File file = new File(dir.getRoot(), fileName);
            Date time = TimeUtil.parseDate((String) attributes
                    .get(SVNLog.TIMESTAMP_ATTR));
            file.setLastModified(time.getTime());
        } else if (SVNLog.MAYBE_READONLY.equals(name)) {
            SVNEntries entries = dir.getEntries();
            if (entries.getEntry(fileName, true) != null
                    && entries.getEntry(fileName, true).getLockToken() == null) {
                SVNFileUtil
                        .setReadonly(new File(dir.getRoot(), fileName), true);
            }
        } else if (SVNLog.COPY_AND_TRANSLATE.equals(name)) {
            String dstName = (String) attributes.get(SVNLog.DEST_ATTR);
            File dst = new File(dir.getRoot(), dstName);
            // get properties for this entry.
            SVNProperties props = dir.getProperties(dstName, false);
            boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;

            SVNTranslator
                    .translate(dir, dstName, fileName, dstName, true, true);
            if (executable) {
                SVNFileUtil.setExecutable(dst, true);
            }
            SVNEntry entry = dir.getEntries().getEntry(dstName, true);
            if (entry.getLockToken() == null
                    && props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null) {
                SVNFileUtil.setReadonly(dst, true);
            }
        } else if (SVNLog.COPY_AND_DETRANSLATE.equals(name)) {
            String dstName = (String) attributes.get(SVNLog.DEST_ATTR);
            SVNTranslator.translate(dir, fileName, fileName, dstName, false,
                    true);
        } else if (SVNLog.MERGE.equals(name)) {
            File target = new File(dir.getRoot(), fileName);
            String leftPath = (String) attributes.get(SVNLog.ATTR1);
            String rightPath = (String) attributes.get(SVNLog.ATTR2);
            String leftLabel = (String) attributes.get(SVNLog.ATTR3);
            leftLabel = leftLabel == null ? ".old" : leftLabel;
            String rightLabel = (String) attributes.get(SVNLog.ATTR4);
            rightLabel = rightLabel == null ? ".new" : rightLabel;
            String targetLabel = (String) attributes.get(SVNLog.ATTR5);
            targetLabel = targetLabel == null ? ".working" : targetLabel;

            SVNProperties props = dir.getProperties(fileName, false);
            SVNEntry entry = dir.getEntries().getEntry(fileName, true);

            String leaveConglictsAttr = (String) attributes.get(SVNLog.ATTR6);
            boolean leaveConflicts = Boolean.TRUE.toString().equals(leaveConglictsAttr);
            SVNStatusType mergeResult = dir.mergeText(fileName, leftPath,
                    rightPath, targetLabel, leftLabel, rightLabel, leaveConflicts, false);

            if (props.getPropertyValue(SVNProperty.EXECUTABLE) != null) {
                SVNFileUtil.setExecutable(target, true);
            }
            if (props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null
                    && entry.getLockToken() == null) {
                SVNFileUtil.setReadonly(target, true);
            }
            setEntriesChanged(mergeResult == SVNStatusType.CONFLICTED || 
                    mergeResult == SVNStatusType.CONFLICTED_UNRESOLVED);
        } else if (SVNLog.COMMIT.equals(name)) {
            if (attributes.get(SVNLog.REVISION_ATTR) == null) {
                SVNErrorManager.error("svn: Missing revision attribute for '"
                        + fileName + "'");
            }
            SVNEntry entry = dir.getEntries().getEntry(fileName, true);
            if (entry == null
                    || (!"".equals(fileName) && entry.getKind() != SVNNodeKind.FILE)) {
                SVNErrorManager.error("svn: Log command for directory '"
                        + dir.getRoot() + "' is mislocated");
            }
            setEntriesChanged(true);
            long revisionNumber = Long.parseLong((String) attributes
                    .get(SVNLog.REVISION_ATTR));
            if (entry.isScheduledForDeletion()) {
                if ("".equals(fileName)) {
                    entry.setRevision(revisionNumber);
                    entry.setKind(SVNNodeKind.DIR);
                    File killMe = dir.getFile(".svn/KILLME");
                    if (killMe.getParentFile().isDirectory()) {
                        try {
                            killMe.createNewFile();
                        } catch (IOException e) {
                            SVNErrorManager.error("svn: Cannot create file '"
                                    + killMe + "'");
                        }
                    }
                } else {
                    dir.destroy(fileName, false);
                    SVNEntry parentEntry = dir.getEntries().getEntry("", true);
                    if (revisionNumber > parentEntry.getRevision()) {
                        SVNEntry fileEntry = dir.getEntries()
                                .addEntry(fileName);
                        fileEntry.setKind(SVNNodeKind.FILE);
                        fileEntry.setDeleted(true);
                        fileEntry.setRevision(revisionNumber);
                    }
                }
                return;
            }

            if (entry.isScheduledForReplacement() && "".equals(fileName)) {
                for (Iterator ents = dir.getEntries().entries(true); ents
                        .hasNext();) {
                    SVNEntry currentEntry = (SVNEntry) ents.next();
                    if (!currentEntry.isScheduledForDeletion()) {
                        continue;
                    }
                    if (currentEntry.getKind() == SVNNodeKind.FILE
                            || currentEntry.getKind() == SVNNodeKind.DIR) {
                        dir.destroy(currentEntry.getName(), false);
                    }
                }
            }

            long textTime = 0;
            if (!"".equals(fileName)) {
                File tmpFile = dir.getBaseFile(fileName, true);
                SVNFileType fileType = SVNFileType.getType(tmpFile);
                if (fileType == SVNFileType.FILE
                        || fileType == SVNFileType.SYMLINK) {
                    // check if wc file is not modified
                    File tmpFile2 = SVNFileUtil.createUniqueFile(tmpFile
                            .getParentFile(), fileName, ".tmp");
                    boolean equals = true;
                    try {
                        String tmpFile2Path = SVNFileUtil.getBasePath(tmpFile2);
                        SVNTranslator.translate(dir, fileName, fileName,
                                tmpFile2Path, false, false);
                        equals = SVNFileUtil.compareFiles(tmpFile, tmpFile2,
                                null);
                    } finally {
                        tmpFile2.delete();
                    }
                    if (equals) {
                        textTime = dir.getFile(fileName).lastModified();
                    } else {
                        textTime = tmpFile.lastModified();
                    }
                }
            }
            SVNProperties baseProps = dir.getBaseProperties(fileName, false);
            SVNProperties wcProps = dir.getProperties(fileName, false);
            SVNProperties tmpProps = dir.getBaseProperties(fileName, true);
            if (entry.isScheduledForReplacement()) {
                baseProps.delete();
            }
            long propTime = 0;
            boolean setReadWrite = false;
            boolean setNotExecutable = false;

            SVNFileType tmpPropsType = SVNFileType.getType(tmpProps.getFile());
            if (tmpPropsType == SVNFileType.FILE) {
                boolean equals = SVNFileUtil.compareFiles(wcProps.getFile(),
                        tmpProps.getFile(), null);
                propTime = equals ? wcProps.getFile().lastModified() : tmpProps
                        .getFile().lastModified();
                if (!"".equals(fileName)) {
                    Map propDiff = baseProps.compareTo(tmpProps);
                    setReadWrite = propDiff != null
                            && propDiff.containsKey(SVNProperty.NEEDS_LOCK)
                            && propDiff.get(SVNProperty.NEEDS_LOCK) == null;
                    setNotExecutable = propDiff != null
                            && propDiff.containsKey(SVNProperty.EXECUTABLE)
                            && propDiff.get(SVNProperty.EXECUTABLE) == null;
                }
                try {
                    SVNFileUtil.rename(tmpProps.getFile(), baseProps.getFile());
                    SVNFileUtil.setReadonly(baseProps.getFile(), true);
                } finally {
                    tmpProps.delete();
                }
            } else if (entry.getPropTime() == null && !wcProps.isEmpty()) {
                propTime = wcProps.getFile().lastModified();
            }
            
            if (!"".equals(fileName)) {
                File tmpFile = dir.getBaseFile(fileName, true);
                File baseFile = dir.getBaseFile(fileName, false);
                File wcFile = dir.getFile(fileName);
                File tmpFile2 = SVNFileUtil.createUniqueFile(tmpFile
                        .getParentFile(), fileName, ".tmp");
                try {
                    boolean overwritten = false;
                    SVNFileType fileType = SVNFileType.getType(tmpFile);
                    boolean special = dir.getProperties(fileName, false)
                            .getPropertyValue(SVNProperty.SPECIAL) != null;
                    if (SVNFileUtil.isWindows || !special) {
                        if (fileType == SVNFileType.FILE) {
                            SVNTranslator.translate(dir, fileName, SVNFileUtil
                                    .getBasePath(tmpFile), SVNFileUtil
                                    .getBasePath(tmpFile2), true, false);
                        } else {
                            SVNTranslator.translate(dir, fileName, fileName,
                                    SVNFileUtil.getBasePath(tmpFile2), true,
                                    false);
                        }
                        if (!SVNFileUtil.compareFiles(tmpFile2, wcFile, null)) {
                            SVNFileUtil.copyFile(tmpFile2, wcFile, true);
                            overwritten = true;
                        }
                    }
                    boolean needsReadonly = dir.getProperties(fileName, false)
                            .getPropertyValue(SVNProperty.NEEDS_LOCK) != null
                            && entry.getLockToken() == null;
                    boolean needsExecutable = dir
                            .getProperties(fileName, false).getPropertyValue(
                                    SVNProperty.EXECUTABLE) != null;
                    if (needsReadonly) {
                        SVNFileUtil.setReadonly(wcFile, true);
                        overwritten = true;
                    }
                    if (needsExecutable) {
                        SVNFileUtil.setExecutable(wcFile, true);
                        overwritten = true;
                    }
                    if (fileType == SVNFileType.FILE) {
                        SVNFileUtil.rename(tmpFile, baseFile);
                    }
                    if (setReadWrite) {
                        SVNFileUtil.setReadonly(wcFile, false);
                        overwritten = true;
                    }
                    if (setNotExecutable) {
                        SVNFileUtil.setExecutable(wcFile, false);
                        overwritten = true;
                    }
                    if (overwritten) {
                        textTime = wcFile.lastModified();
                    }
                } finally {
                    tmpFile2.delete();
                    tmpFile.delete();
                }
            }
            // update entry
            entry.setRevision(revisionNumber);
            entry.setKind("".equals(fileName) ? SVNNodeKind.DIR
                    : SVNNodeKind.FILE);
            entry.unschedule();
            entry.setCopied(false);
            entry.setDeleted(false);
            entry.setTextTime(textTime == 0 ? null : TimeUtil
                    .formatDate(new Date(textTime)));
            entry.setPropTime(propTime == 0 ? null : TimeUtil
                    .formatDate(new Date(propTime)));
            entry.setConflictNew(null);
            entry.setConflictOld(null);
            entry.setConflictWorking(null);
            entry.setPropRejectFile(null);
            entry.setCopyFromRevision(-1);
            entry.setCopyFromURL(null);
            setEntriesChanged(true);

            if (!"".equals(fileName)) {
                return;
            }
            // update entry in parent.
            File dirFile = dir.getRoot();
            if (SVNWCUtil.isWorkingCopyRoot(dirFile, true)) {
                return;
            }
            String parentPath = SVNPathUtil.removeTail(dir.getPath());
            SVNDirectory parentDir = dir.getWCAccess().getDirectory(parentPath);
            SVNWCAccess parentAccess = null;
            if (parentDir == null) {
                parentDir = new SVNDirectory(null, "", dirFile);
                parentAccess = new SVNWCAccess(parentDir, parentDir, "");
                parentAccess.open(true, false);
            }
            String nameInParent = dirFile.getName();
            SVNEntry entryInParent = parentDir.getEntries().getEntry(
                    nameInParent, false);
            if (entryInParent != null) {
                entryInParent.unschedule();
                entryInParent.setCopied(false);
                entryInParent.setCopyFromRevision(-1);
                entryInParent.setCopyFromURL(null);
                entryInParent.setDeleted(false);
            }
            parentDir.getEntries().save(false);
            if (parentAccess != null) {
                parentAccess.close(true);
            }
        }
    }

    private void setEntriesChanged(boolean modified) {
        myIsEntriesChanged |= modified;
    }

    public void logCompleted(SVNDirectory dir) throws SVNException {
        boolean killMe = dir.getFile(".svn/KILLME").isFile();
        long dirRevision = killMe ? dir.getEntries().getEntry("", true)
                .getRevision() : -1;
        if (myIsEntriesChanged) {
            dir.getEntries().save(false);
        } else {
            dir.getEntries().close();
        }
        if (killMe) {
            // deleted dir, files and entry in parent.
            dir.destroy("", true);
            // compare revision with parent's one
            File dirFile = dir.getRoot();
            if (SVNWCUtil.isWorkingCopyRoot(dirFile, true)) {
                return;
            }
            String parentPath = SVNPathUtil.removeTail(dir.getPath());
            SVNDirectory parentDir = dir.getWCAccess().getDirectory(parentPath);
            SVNWCAccess parentAccess = null;
            if (parentDir == null) {
                parentDir = new SVNDirectory(null, "", dirFile);
                parentAccess = new SVNWCAccess(parentDir, parentDir, "");
                parentAccess.open(true, false);
            }
            String nameInParent = dirFile.getName();

            SVNEntry parentEntry = parentDir.getEntries().getEntry("", false);
            if (parentEntry != null && parentEntry.getRevision() <= dirRevision) {
                // create 'deleted' entry
                SVNEntry entryInParent = parentDir.getEntries().addEntry(
                        nameInParent);
                entryInParent.setDeleted(true);
                entryInParent.setKind(SVNNodeKind.DIR);
                entryInParent.setRevision(dirRevision);
                parentDir.getEntries().save(false);
            }
            if (parentAccess != null) {
                parentAccess.close(true);
            }
        }
        myIsEntriesChanged = false;
    }
}
