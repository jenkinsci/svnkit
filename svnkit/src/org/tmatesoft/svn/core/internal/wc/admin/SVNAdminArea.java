/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNMergeInfoManager;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.wc.ISVNCommitParameters;
import org.tmatesoft.svn.core.wc.ISVNMerger;
import org.tmatesoft.svn.core.wc.ISVNMergerFactory;
import org.tmatesoft.svn.core.wc.SVNMergeAction;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.core.wc.SVNMergeResult;
import org.tmatesoft.svn.core.wc.SVNResolveAccept;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public abstract class SVNAdminArea {
    protected static final String ADM_KILLME = "KILLME";
    private static volatile boolean ourIsCleanupSafe;
    

    protected Map myBaseProperties;
    protected Map myProperties;
    protected Map myWCProperties;
    protected Map myEntries;
    protected boolean myWasLocked;

    private ISVNCommitParameters myCommitParameters;
    private Map myRevertProperties;
    private File myDirectory;
    private SVNWCAccess myWCAccess;
    private File myAdminRoot;
    
    public static synchronized void setSafeCleanup(boolean safe) {
        ourIsCleanupSafe = safe;
    }

    public static synchronized boolean isSafeCleanup() {
        return ourIsCleanupSafe;
    }    
    
    public abstract boolean isLocked() throws SVNException;

    public abstract boolean isVersioned();

    protected abstract boolean isEntryPropertyApplicable(String name);
    
    public abstract boolean lock(boolean stealLock) throws SVNException;

    public abstract boolean unlock() throws SVNException;

    public abstract SVNVersionedProperties getBaseProperties(String name) throws SVNException;

    public abstract SVNVersionedProperties getRevertProperties(String name) throws SVNException;

    public abstract SVNVersionedProperties getWCProperties(String name) throws SVNException;

    public abstract SVNVersionedProperties getProperties(String name) throws SVNException;

    public abstract void saveVersionedProperties(SVNLog log, boolean close) throws SVNException;

    public abstract void saveWCProperties(boolean close) throws SVNException;

    public abstract void saveEntries(boolean close) throws SVNException;

    public abstract String getThisDirName();

    public abstract boolean hasPropModifications(String entryName) throws SVNException;

    public abstract boolean hasProperties(String entryName) throws SVNException;

    public abstract SVNAdminArea createVersionedDirectory(File dir, String url, String rootURL, String uuid, long revNumber, boolean createMyself, SVNDepth depth) throws SVNException;

    public abstract SVNAdminArea upgradeFormat(SVNAdminArea adminArea) throws SVNException;
    
    public abstract void postUpgradeFormat(int format) throws SVNException;

    public abstract void postCommit(String fileName, long revisionNumber, boolean implicit, SVNErrorCode errorCode) throws SVNException;

    public abstract void handleKillMe() throws SVNException;
    
    public void updateURL(String rootURL, boolean recursive) throws SVNException {
        SVNWCAccess wcAccess = getWCAccess();
        for (Iterator ents = entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (!getThisDirName().equals(entry.getName()) && entry.isDirectory() && recursive) {
                SVNAdminArea childDir = wcAccess.retrieve(getFile(entry.getName()));
                if (childDir != null) {
                    String childURL = SVNPathUtil.append(rootURL, SVNEncodingUtil.uriEncode(entry.getName()));
                    childDir.updateURL(childURL, recursive);
                }
                continue;
            }
            entry.setURL(getThisDirName().equals(entry.getName()) ? rootURL : SVNPathUtil.append(
                    rootURL, SVNEncodingUtil.uriEncode(entry.getName())));
        }
        saveEntries(false);
    }
    
    public boolean hasTextModifications(String name, boolean forceComparision) throws SVNException {
        return hasTextModifications(name, forceComparision, true, false);
    }

    public boolean hasTextModifications(String name, boolean forceComparison, boolean compareTextBase, boolean compareChecksum) throws SVNException {
        File textFile = getFile(name);
        if (!textFile.isFile()) {
            return false;
        }
        
        SVNEntry entry = null;
        if (!forceComparison) {
            boolean compare = false;
            try {
                entry = getEntry(name, false);    
            } catch (SVNException svne) {
                compare = true;
            }
            
            if (!compare && entry == null) {
                compare = true;
            }
            
            if (!compare) {
                if (entry.getWorkingSize() != SVNProperty.WORKING_SIZE_UNKNOWN &&
                    textFile.length() != entry.getWorkingSize()) {
                    compare = true;
                }
            }

            if (!compare) {
                String textTime = entry.getTextTime();
                if (textTime == null) {
                    compare = true;
                } else {
                    long textTimeAsLong = SVNFileUtil.roundTimeStamp(SVNTimeUtil.parseDateAsLong(textTime));
                    long tstamp = SVNFileUtil.roundTimeStamp(textFile.lastModified());
                    if (textTimeAsLong != tstamp ) {
                        compare = true;
                    }
                }
            }
            
            if (!compare) {
                return false;
            }
        }

        File baseFile = getBaseFile(name, false);
        if (!baseFile.isFile()) {
            return true;
        }
        
        boolean differs = compareAndVerify(textFile, baseFile, compareTextBase, compareChecksum);
        if (!differs && isLocked()) {
            Map attributes = new HashMap();
            attributes.put(SVNProperty.WORKING_SIZE, Long.toString(textFile.length()));
            attributes.put(SVNProperty.TEXT_TIME, SVNTimeUtil.formatDate(new Date(textFile.lastModified())));
            modifyEntry(name, attributes, true, false);
        }
        return differs;
    }

    public boolean hasVersionedFileTextChanges(File file, File baseFile, boolean compareTextBase) throws SVNException {
        return compareAndVerify(file, baseFile, compareTextBase, false);
    }
    
    private boolean compareAndVerify(File text, File baseFile, boolean compareTextBase, boolean checksum) throws SVNException {
        String eolStyle = getProperties(text.getName()).getPropertyValue(SVNProperty.EOL_STYLE);
        String keywords = getProperties(text.getName()).getPropertyValue(SVNProperty.KEYWORDS);
        boolean special = getProperties(text.getName()).getPropertyValue(SVNProperty.SPECIAL) != null;
        
        if (special) {
            compareTextBase = true;
        }
        
        boolean needsTranslation = eolStyle != null || keywords != null || special;
        SVNChecksumInputStream checksumStream = null;
        SVNEntry entry = null;
        
        if (checksum || needsTranslation) {
            InputStream baseStream = null;
            InputStream textStream = null;
            entry = getVersionedEntry(text.getName(), true);
            File tmpFile = null;
            try {
                baseStream = SVNFileUtil.openFileForReading(baseFile);
                textStream = special ? null : SVNFileUtil.openFileForReading(text);
                if (checksum) {
                    if (entry.getChecksum() != null) {
                        checksumStream = new SVNChecksumInputStream(baseStream);
                        baseStream = checksumStream;
                    }
                }
                if (compareTextBase && needsTranslation) {
                    if (!special) {
                        Map keywordsMap = SVNTranslator.computeKeywords(keywords, null, entry.getAuthor(), entry.getCommittedDate(), entry.getRevision() + "", getWCAccess().getOptions());
                        byte[] eols = SVNTranslator.getBaseEOL(eolStyle);
                        textStream = new SVNTranslatorInputStream(textStream, eols, true, keywordsMap, false);
                    } else {
                        String tmpPath = SVNAdminUtil.getTextBasePath(text.getName(), true);
                        tmpFile = getFile(tmpPath);
                        SVNTranslator.translate(this, text.getName(), text.getName(), tmpPath, false);
                        textStream = SVNFileUtil.openFileForReading(getFile(tmpPath));
                    }
                } else if (needsTranslation) {
                    Map keywordsMap = SVNTranslator.computeKeywords(keywords, entry.getURL(), entry.getAuthor(), entry.getCommittedDate(), entry.getRevision() + "", getWCAccess().getOptions());
                    byte[] eols = SVNTranslator.getWorkingEOL(eolStyle);
                    baseStream = new SVNTranslatorInputStream(baseStream, eols, false, keywordsMap, true);                    
                }
                byte[] buffer1 = new byte[8192];
                byte[] buffer2 = new byte[8192];
                try {
                    while(true) {
                        int r1 = baseStream.read(buffer1);
                        int r2 = textStream.read(buffer2);
                        r1 = r1 == -1 ? 0 : r1;
                        r2 = r2 == -1 ? 0 : r2;
                        if (r1 != r2) {
                            return true;
                        } else if (r1 == 0) {
                            return false;
                        }
                        for(int i = 0; i < r1; i++) {
                            if (buffer1[i] != buffer2[i]) {
                                return true;
                            }
                        }
                    }
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
                    SVNErrorManager.error(err);
                }
            } finally {
                SVNFileUtil.closeFile(baseStream);
                SVNFileUtil.closeFile(textStream);
                SVNFileUtil.deleteFile(tmpFile);
            }
        } else {
            return !SVNFileUtil.compareFiles(text, baseFile, null);
        }
        if (entry != null && checksumStream != null)  {
            if (!entry.getChecksum().equals(checksumStream.getDigest())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch indicates corrupt text base: ''{0}''\n" +
                        "   expected: {1}\n" +
                        "     actual: {2}\n", new Object[] {baseFile, entry.getChecksum(), checksumStream.getDigest()});
                SVNErrorManager.error(err);
            }
        }
        return false;
    }

    public String getRelativePath(SVNAdminArea anchor) {
        String absoluteAnchor = anchor.getRoot().getAbsolutePath();
        String ownAbsolutePath = getRoot().getAbsolutePath();
        String relativePath = ownAbsolutePath.substring(absoluteAnchor.length());
        
        relativePath = relativePath.replace(File.separatorChar, '/');
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        if (relativePath.endsWith("/")) {
            relativePath = relativePath.substring(0, relativePath.length() - 1);
        }
        return relativePath;
    }
    
    public boolean tweakEntry(String name, String newURL, String reposRoot, long newRevision, boolean remove) throws SVNException {
        boolean rewrite = false;
        SVNEntry entry = getEntry(name, true);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "No such entry: ''{0}''", name);
            SVNErrorManager.error(err);
        }
        
        if (newURL != null && (entry.getURL() == null || !newURL.equals(entry.getURL()))) {
            rewrite = true;
            entry.setURL(newURL);
        }
        
        if (reposRoot != null && (entry.getRepositoryRootURL() == null || !reposRoot.equals(entry.getRepositoryRoot())) 
                && entry.getURL() != null && SVNPathUtil.isAncestor(reposRoot, entry.getURL())) {
            boolean setReposRoot = true;
            if (getThisDirName().equals(entry.getName())) {
                for (Iterator entries = entries(true); entries.hasNext();) {
                    SVNEntry childEntry = (SVNEntry) entries.next();
                    if (childEntry.getRepositoryRoot() == null && childEntry.getURL() != null && 
                            !SVNPathUtil.isAncestor(reposRoot, entry.getURL())) {
                        setReposRoot = false;
                        break;
                    }
                }
            }
            if (setReposRoot) {
                rewrite = true;
                entry.setRepositoryRoot(reposRoot);
            }
        }

        if (newRevision >= 0 && 
                !entry.isScheduledForAddition() && 
                !entry.isScheduledForReplacement() &&
                !entry.isCopied() &&
                entry.getRevision() != newRevision) {
            rewrite = true;
            entry.setRevision(newRevision);
        }
        
        if (remove && (entry.isDeleted() || (entry.isAbsent() && entry.getRevision() != newRevision))) {
            deleteEntry(name);
            rewrite = true;
        }
        return rewrite;
    }
    
    public boolean isKillMe() {
        return getAdminFile(ADM_KILLME).isFile();
    }

    public boolean markResolved(String name, boolean text, boolean props, SVNResolveAccept accept) throws SVNException {
        SVNEntry entry = getEntry(name, true);
        if (entry == null) {
            return false;
        }

        String autoResolveSource = null;
        if (accept == SVNResolveAccept.LEFT) {
            autoResolveSource = entry.getConflictOld(); 
        } else if (accept == SVNResolveAccept.WORKING) {
            autoResolveSource = entry.getConflictWorking();
        } else if (accept == SVNResolveAccept.RIGHT) {
            autoResolveSource = entry.getConflictNew();
        } else if (accept == SVNResolveAccept.INVALID) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, "Invalid 'accept' argument");
            SVNErrorManager.error(err);
        }
        
        if (autoResolveSource != null) {
            File autoResolveSourceFile = getFile(autoResolveSource);
            SVNFileUtil.copyFile(autoResolveSourceFile, getFile(name), false);
        }
        
        if (!text && !props) {
            return false;
        }
        boolean filesDeleted = false;
        boolean updateEntry = false;
        if (text && entry.getConflictOld() != null) {
            File file = getFile(entry.getConflictOld());
            filesDeleted |= file.isFile();
            updateEntry = true;
            SVNFileUtil.deleteFile(file);
        }
        if (text && entry.getConflictNew() != null) {
            File file = getFile(entry.getConflictNew());
            filesDeleted |= file.isFile();
            updateEntry = true;
            SVNFileUtil.deleteFile(file);
        }
        if (text && entry.getConflictWorking() != null) {
            File file = getFile(entry.getConflictWorking());
            filesDeleted |= file.isFile();
            updateEntry = true;
            SVNFileUtil.deleteFile(file);
        }
        if (props && entry.getPropRejectFile() != null) {
            File file = getFile(entry.getPropRejectFile());
            filesDeleted |= file.isFile();
            updateEntry = true;
            SVNFileUtil.deleteFile(file);
        }
        if (updateEntry) {
            if (text) {
                entry.setConflictOld(null);
                entry.setConflictNew(null);
                entry.setConflictWorking(null);
            }
            if (props) {
                entry.setPropRejectFile(null);
            }
            saveEntries(false);
        }
        return filesDeleted;
    }
    
    public void restoreFile(String name) throws SVNException {
        SVNVersionedProperties props = getProperties(name);
        SVNEntry entry = getEntry(name, true);
        boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;

        File src = getBaseFile(name, false);
        File dst = getFile(name);
        SVNTranslator.translate(this, name, SVNFileUtil.getBasePath(src), SVNFileUtil.getBasePath(dst), true);

        boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
        boolean needsLock = props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null;
        if (needsLock) {
            SVNFileUtil.setReadonly(dst, entry.getLockToken() == null);
        }
        if (executable) {
            SVNFileUtil.setExecutable(dst, true);
        }

        markResolved(name, true, false, SVNResolveAccept.DEFAULT);

        long tstamp;
        if (myWCAccess.getOptions().isUseCommitTimes() && !special) {
            entry.setTextTime(entry.getCommittedDate());
            tstamp = SVNTimeUtil.parseDate(entry.getCommittedDate()).getTime();
            dst.setLastModified(tstamp);
        } else {
            tstamp = System.currentTimeMillis();
            dst.setLastModified(tstamp);
            entry.setTextTime(SVNTimeUtil.formatDate(new Date(tstamp)));
        }
        saveEntries(false);
    }

    public SVNStatusType mergeProperties(String name, Map serverBaseProps, Map propDiff, boolean baseMerge, boolean dryRun, SVNLog log) throws SVNException {
        //serverBaseProps = serverBaseProps == null ? Collections.EMPTY_MAP : serverBaseProps;
        propDiff = propDiff == null ? Collections.EMPTY_MAP : propDiff;
        
        SVNVersionedProperties working = getProperties(name);
        Map workingProps = working.asMap();
        SVNVersionedProperties base = getBaseProperties(name);
        if (serverBaseProps == null) {
            serverBaseProps = base.asMap();
        }

        Collection conflicts = new ArrayList();
        SVNStatusType result = propDiff.isEmpty() ? SVNStatusType.UNCHANGED : SVNStatusType.CHANGED;
        
        for (Iterator propEntries = propDiff.entrySet().iterator(); propEntries.hasNext();) {
            Map.Entry incomingEntry = (Map.Entry) propEntries.next();
            String propName = (String) incomingEntry.getKey();
            String toValue = (String) incomingEntry.getValue();
            String fromValue = (String) serverBaseProps.get(propName);
            String workingValue = (String) workingProps.get(propName);
            String baseValue = base.getPropertyValue(propName);
            boolean isNormal = SVNProperty.isRegularProperty(propName); 
            if (baseMerge) {
                base.setPropertyValue(propName, toValue);
            }
            
            result = isNormal ? SVNStatusType.CHANGED : result;
            if (fromValue == null) {
                if (baseValue != null) {
                    if (workingValue != null) {
                        if (workingValue.equals(toValue)) {
                            result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                        } else {
                            result = isNormal ? SVNStatusType.CONFLICTED : result;                            
                            conflicts.add(MessageFormat.format("Trying to create property ''{0}'' with value ''{1}'',\n" +
                                                               "but it already exists.", 
                                                               new Object[] { propName, toValue }));
                        }                        
                    } else {
                        result = isNormal ? SVNStatusType.CONFLICTED : result;                            
                        conflicts.add(MessageFormat.format("Trying to create property ''{0}'' with value ''{1}'',\n" +
                                                           "but it has been locally deleted.", 
                                                           new Object[] { propName, toValue }));
                    }
                } else if (workingValue != null) {
                    if (workingValue.equals(toValue)) {
                        result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                    } else {
                        if (SVNProperty.MERGE_INFO.equals(propName)) {
                            toValue = SVNMergeInfoManager.combineMergeInfoProperties(workingValue, toValue);
                            working.setPropertyValue(propName, toValue);
                            result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                        } else {
                            result = isNormal ? SVNStatusType.CONFLICTED : result;                            
                            conflicts.add(MessageFormat.format("Trying to add new property ''{0}'' with value ''{1}'',\n" +
                                                               "but property already exists with value ''{2}''.", 
                                                               new Object[] { propName, toValue, workingValue }));
                        }
                    }
                } else {
                    working.setPropertyValue(propName, toValue);
                }
            } else if (toValue == null) {
                if (baseValue == null) {
                    if (workingValue != null) {
                        working.setPropertyValue(propName, null);
                    }
                    result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;                    
                } else if (baseValue.equals(fromValue)) {
                    if (workingValue != null) {
                        if (workingValue.equals(fromValue)) {
                            working.setPropertyValue(propName, toValue);
                        } else {
                            result = isNormal ? SVNStatusType.CONFLICTED : result;
                            conflicts.add(MessageFormat.format("Trying to delete property ''{0}'' with value ''{1}''\n " +
                                                               "but it has been modified from ''{2}'' to ''{3}''.",
                                                               new Object[] { propName, fromValue, baseValue, workingValue }));
                        }
                    } else {
                        result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                    }
                } else {
                    result = isNormal ? SVNStatusType.CONFLICTED : result;
                    conflicts.add(MessageFormat.format("Trying to delete property ''{0}'' with value ''{1}''\n " +
                                                       "but the local value is ''{2}''.",
                                                       new Object[] { propName, baseValue, workingValue }));
                }
            } else {
                if ((workingValue != null && baseValue == null) ||
                    (workingValue == null && baseValue != null) ||
                    (workingValue != null && baseValue != null && !workingValue.equals(baseValue))) {
                    if (workingValue != null) {
                        if (workingValue.equals(toValue)) {
                            result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                        } else {
                            if (SVNProperty.MERGE_INFO.equals(propName)) {
                                toValue = SVNMergeInfoManager.combineForkedMergeInfoProperties(fromValue, 
                                                                                               workingValue, 
                                                                                               toValue);
                                working.setPropertyValue(propName, toValue);
                                result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                            } else {
                                result = isNormal ? SVNStatusType.CONFLICTED : result;
                                if (baseValue != null) {
                                    conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                                                                       "but property has been locally changed from ''{3}'' to ''{4}''.", 
                                                                       new Object[] { propName, 
                                                                                      fromValue, 
                                                                                      toValue, 
                                                                                      baseValue, 
                                                                                      workingValue }));
                                } else {
                                    conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                                                                       "but property has been locally added with value ''{3}''", 
                                                                       new Object[] { propName, 
                                                                                      fromValue, 
                                                                                      toValue, 
                                                                                      workingValue }));
                                }
                            }
                        }
                    } else {
                        result = isNormal ? SVNStatusType.CONFLICTED : result;                            
                        conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                                                           "but it has been locally deleted.", 
                                                           new Object[] { propName, 
                                                                          fromValue, 
                                                                          toValue }));
                    }
                } else if (workingValue == null) {
                    if (SVNProperty.MERGE_INFO.equals(propName)) {
                        Map addedMergeInfo = new TreeMap();
                        SVNMergeInfoManager.diffMergeInfoProperties(null, addedMergeInfo, fromValue, null, toValue, null);
                        toValue = SVNMergeInfoManager.formatMergeInfoToString(addedMergeInfo);
                        working.setPropertyValue(propName, toValue);
                    } else {
                        result = isNormal ? SVNStatusType.CONFLICTED : result;                            
                        conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                                                           "but the property does not exist.", 
                                                           new Object[] { propName, 
                                                                          fromValue, 
                                                                          toValue }));
                    }
                    
                } else {
                    if (baseValue.equals(fromValue)) {
                        working.setPropertyValue(propName, toValue);
                    } else {
                        if (SVNProperty.MERGE_INFO.equals(propName)) {
                            toValue = SVNMergeInfoManager.combineForkedMergeInfoProperties(fromValue, 
                                                                                           workingValue, 
                                                                                           toValue);
                            working.setPropertyValue(propName, toValue);
                            result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                        } else {
                            result = isNormal ? SVNStatusType.CONFLICTED : result;

                            conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                                                               "but property already exists with value ''{3}''.", 
                                                               new Object[] { propName, 
                                                                              fromValue, 
                                                                              toValue,
                                                                              workingValue }));
                        }
   
                    }
                }
            }
        }

        if (dryRun) {
            return result;
        }
        log = log == null ? getLog() : log;
        saveVersionedProperties(log, true);
        
        if (!conflicts.isEmpty()) {
            String prejTmpPath = getThisDirName().equals(name) ? "tmp/dir_conflicts" : "tmp/props/" + name;
            File prejTmpFile = SVNFileUtil.createUniqueFile(getAdminDirectory(),  prejTmpPath, ".prej");
            
            prejTmpPath = SVNFileUtil.getBasePath(prejTmpFile);
            
            SVNEntry entry = getEntry(name, false);
            if (entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Can''t find entry ''{0}'' in ''{1}''", new Object[]{name, getRoot()});
                SVNErrorManager.error(err);
            }
            String prejPath = entry.getPropRejectFile();

            if (prejPath == null) {
                prejPath = getThisDirName().equals(name) ? "dir_conflicts" : name;
                File prejFile = SVNFileUtil.createUniqueFile(getRoot(), prejPath, ".prej");
                prejPath = SVNFileUtil.getBasePath(prejFile);
            }
            File file = getFile(prejTmpPath);

            OutputStream os = SVNFileUtil.openFileForWriting(file);
            try {
                for (Iterator lines = conflicts.iterator(); lines.hasNext();) {
                    String line = (String) lines.next();
                    os.write(SVNEncodingUtil.fuzzyEscape(line).getBytes("UTF-8"));
                }
                os.write(SVNEncodingUtil.fuzzyEscape("\n").getBytes("UTF-8"));
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot write properties conflict file: {1}", e.getLocalizedMessage());
                SVNErrorManager.error(err, e);
            } finally {
                SVNFileUtil.closeFile(os);
            }

            Map command = new HashMap();
            command.put(SVNLog.NAME_ATTR, prejTmpPath);
            command.put(SVNLog.DEST_ATTR, prejPath);
            log.addCommand(SVNLog.APPEND, command, false);
            command.clear();

            command.put(SVNLog.NAME_ATTR, prejTmpPath);
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();

            command.put(SVNLog.NAME_ATTR, name);
            command.put(SVNProperty.shortPropertyName(SVNProperty.PROP_REJECT_FILE),
                        prejPath);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
        }
        return result;
    }

    public SVNStatusType mergeText(String localPath, File base, File latest, String localLabel, 
                                   String baseLabel, String latestLabel, Map propChanges, 
                                   boolean dryRun, SVNDiffOptions options, SVNLog log) throws SVNException {
        SVNEntry entry = getEntry(localPath, false);
        if (entry == null) {
            return SVNStatusType.MISSING;
        }

        boolean saveLog = log == null;
        log = log == null ? getLog() : log;
        
        SVNVersionedProperties props = getProperties(localPath);
        String mimeType = null;
        if (propChanges != null && propChanges.containsKey(SVNProperty.MIME_TYPE)) {
            mimeType = (String) propChanges.get(SVNProperty.MIME_TYPE);
        } else {
            mimeType = props.getPropertyValue(SVNProperty.MIME_TYPE);
        }
        boolean isBinary = SVNProperty.isBinaryMimeType(mimeType); 

        localLabel = localLabel == null ? ".working" : localLabel;
        baseLabel = baseLabel == null ? ".old" : baseLabel;
        latestLabel = latestLabel == null ? ".new" : latestLabel;
        
        byte[] conflictStart = ("<<<<<<< " + localLabel).getBytes();
        byte[] conflictEnd = (">>>>>>> " + latestLabel).getBytes();
        byte[] separator = ("=======").getBytes();
        ISVNMergerFactory factory = myWCAccess.getOptions().getMergerFactory();
        ISVNMerger merger = factory.createMerger(conflictStart, separator, conflictEnd);

        File tmpTarget = SVNTranslator.detranslateWorkingCopy(this, localPath, propChanges, false);
        base = SVNTranslator.maybeUpdateTargetEOLs(this, base, propChanges);
        File resultFile = SVNAdminUtil.createTmpFile(this);

        SVNMergeFileSet mergeFileSet = new SVNMergeFileSet(this, log, 
                SVNFileUtil.getBasePath(base), SVNFileUtil.getBasePath(tmpTarget), 
                localPath, SVNFileUtil.getBasePath(latest), SVNFileUtil.getBasePath(resultFile), mimeType, isBinary);
        mergeFileSet.setMergeLabels(baseLabel, localLabel, latestLabel);
        
        SVNMergeResult mergeResult = merger.merge(mergeFileSet, SVNProperty.isBinaryMimeType(mimeType), dryRun, options); 

        if (dryRun) {
            SVNFileUtil.deleteFile(resultFile);
        } else {
            SVNMergeAction mergeAction = merger.getMergeAction(mergeFileSet, isBinary, mergeResult);
            mergeResult = merger.processMergedFiles(mergeFileSet, isBinary, mergeResult, mergeAction);
        }
        
        if (saveLog) {
            log.save();
        }
        
        return mergeResult.getMergeStatus();
    }
    
    public InputStream getBaseFileForReading(String name, boolean tmp) throws SVNException {
        String path = tmp ? "tmp/" : "";
        path += "text-base/" + name + ".svn-base";
        File baseFile = getAdminFile(path);
        return SVNFileUtil.openFileForReading(baseFile);
    }

    public OutputStream getBaseFileForWriting(String name) throws SVNException {
        final String fileName = name;
        final File tmpFile = getBaseFile(name, true);
        try {
            final OutputStream os = SVNFileUtil.openFileForWriting(tmpFile);
            return new OutputStream() {
                private String myName = fileName;
                private File myTmpFile = tmpFile;
                
                public void write(int b) throws IOException {
                    os.write(b);
                }
                
                public void write(byte[] b) throws IOException {
                    os.write(b);
                }
                
                public void write(byte[] b, int off, int len) throws IOException {
                    os.write(b, off, len);
                }
                
                public void close() throws IOException {
                    os.close();
                    File baseFile = getBaseFile(myName, false);
                    try {
                        SVNFileUtil.rename(myTmpFile, baseFile);
                    } catch (SVNException e) {
                        throw new IOException(e.getMessage());
                    }
                    SVNFileUtil.setReadonly(baseFile, true);
                }
            }; 
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Your .svn/tmp directory may be missing or corrupt; run 'svn cleanup' and try again");
            SVNErrorManager.error(err);
        }
        return null;
    }

    public String getPropertyTime(String name) {
        String path = getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
        File file = getAdminFile(path);
        return SVNTimeUtil.formatDate(new Date(file.lastModified()));
    }
    
    public SVNLog getLog() {
        int index = 0;
        File logFile = null;
        File tmpFile = null;
        while (true) {
            logFile = getAdminFile("log" + (index == 0 ? "" : "." + index));
            if (logFile.exists()) {
                index++;
                continue;
            }
            tmpFile = getAdminFile("tmp/log" + (index == 0 ? "" : "." + index));
            return new SVNLogImpl(logFile, tmpFile, this);
        }
    }

    public void runLogs() throws SVNException {
        SVNLogRunner runner = new SVNLogRunner();
        int index = 0;
        SVNLog log = null;
        try {
            File logFile = null;
            while (true) {
                if (getWCAccess() != null) {
                    getWCAccess().checkCancelled();
                }
                logFile = getAdminFile("log" + (index == 0 ? "" : "." + index));
                log = new SVNLogImpl(logFile, null, this);
                if (log.exists()) {
                    log.run(runner);
                    markLogProcessed(logFile);
                    index++;
                    continue;
                }
                break;
            }
        } catch (Throwable e) {
            runner.logFailed(this);
            if (e instanceof SVNException) {
                throw (SVNException) e;
            } else if (e instanceof Error) {
                throw (Error) e;
            }
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN), e);
        }
        runner.logCompleted(this);
        // delete all logs, there shoudn't be left unprocessed.
        File[] logsFiles = getAdminDirectory().listFiles();
        if (logsFiles != null) {
            for (int i = 0; i < logsFiles.length; i++) {
                if (logsFiles[i].getName().startsWith("log") && logsFiles[i].isFile()) {
                    SVNFileUtil.deleteFile(logsFiles[i]);
                }
            }
        }
    }
    
    private static void markLogProcessed(File logFile) throws SVNException {
        SVNFileUtil.deleteFile(logFile);
        SVNFileUtil.createEmptyFile(logFile);
    }

    public void removeFromRevisionControl(String name, boolean deleteWorkingFiles, boolean reportInstantError) throws SVNException {
        getWCAccess().checkCancelled();
        boolean isFile = !getThisDirName().equals(name);
        boolean leftSomething = false;
        
        if (isFile) {
            File path = getFile(name);
            boolean wcSpecial = getProperties(name).getPropertyValue(SVNProperty.SPECIAL) != null;
            boolean localSpecial = SVNFileUtil.isWindows ? false : SVNFileType.getType(path) == SVNFileType.SYMLINK;
            boolean textModified = false;
            if (wcSpecial || !localSpecial) {
                textModified = hasTextModifications(name, false);
                if (reportInstantError && textModified) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LEFT_LOCAL_MOD, "File ''{0}'' has local modifications", path);
                    SVNErrorManager.error(err);
                }
            }
            SVNPropertiesManager.deleteWCProperties(this, name, false);
            deleteEntry(name);
            saveEntries(false);
            
            SVNFileUtil.deleteFile(getFile(SVNAdminUtil.getTextBasePath(name, false)));
            SVNFileUtil.deleteFile(getFile(SVNAdminUtil.getPropPath(name, isFile ? SVNNodeKind.FILE : SVNNodeKind.DIR, false)));
            SVNFileUtil.deleteFile(getFile(SVNAdminUtil.getPropBasePath(name, isFile ? SVNNodeKind.FILE : SVNNodeKind.DIR, false)));
            if (deleteWorkingFiles) {
                if (textModified || (!wcSpecial && localSpecial)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LEFT_LOCAL_MOD);
                    SVNErrorManager.error(err);
                } else if (myCommitParameters == null || myCommitParameters.onFileDeletion(path)) {
                    SVNFileUtil.deleteFile(path);
                }
            }
        } else {
            SVNEntry dirEntry = getEntry(getThisDirName(), false);
            dirEntry.setIncomplete(true);
            saveEntries(false);
            SVNPropertiesManager.deleteWCProperties(this, getThisDirName(), false);
            for(Iterator entries = entries(false); entries.hasNext();) {
                SVNEntry entry = (SVNEntry) entries.next();
                String entryName = getThisDirName().equals(entry.getName()) ? null : entry.getName();
                if (entry.isFile()) {
                    try {
                        removeFromRevisionControl(entryName, deleteWorkingFiles, reportInstantError);
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                            if (reportInstantError) {
                                throw e;
                            }
                            leftSomething = true;
                        } else {
                            throw e;
                        }
                    }
                } else if (entryName != null && entry.isDirectory()) {
                    File entryPath = getFile(entryName);
                    if (getWCAccess().isMissing(entryPath)) {
                        deleteEntry(entryName);
                    } else {
                        try {
                            SVNAdminArea entryArea = getWCAccess().retrieve(entryPath);
                            entryArea.removeFromRevisionControl(getThisDirName(), deleteWorkingFiles, reportInstantError);
                        } catch (SVNException e) {
                            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                                if (reportInstantError) {
                                    throw e;
                                }
                                leftSomething = true;
                            } else {
                                throw e;
                            }
                        }
                    }
                }
            }
            if (!getWCAccess().isWCRoot(getRoot())) {
                getWCAccess().retrieve(getRoot().getParentFile()).deleteEntry(getRoot().getName());
                getWCAccess().retrieve(getRoot().getParentFile()).saveEntries(false);
            }
            destroyAdminArea();
            if (deleteWorkingFiles && !leftSomething) {
                if ((myCommitParameters == null || myCommitParameters.onDirectoryDeletion(getRoot())) 
                        && !getRoot().delete()) {
                    // shouldn't throw exception when directory was intentionally left non-empty.
                    leftSomething = true;
                }
            }
            
        }
        if (leftSomething && myCommitParameters == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LEFT_LOCAL_MOD);
            SVNErrorManager.error(err);
        }
    }

    public void foldScheduling(String name, Map attributes, boolean force) throws SVNException {
        if (!attributes.containsKey(SVNProperty.SCHEDULE) || force) {
            return;
        }
        String schedule = (String) attributes.get(SVNProperty.SCHEDULE);
        schedule = "".equals(schedule) ? null : schedule;
        
        SVNEntry entry = getEntry(name, true);
        if (entry == null) {
            if (SVNProperty.SCHEDULE_ADD.equals(schedule)) {
                return;
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "''{0}'' is not under version control", name); 
            SVNErrorManager.error(err);
        }

        SVNEntry thisDirEntry = getEntry(getThisDirName(), true);
        if (!getThisDirName().equals(entry.getName()) && thisDirEntry.isScheduledForDeletion()) {
            if (SVNProperty.SCHEDULE_ADD.equals(schedule)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "Can''t add ''{0}'' to deleted directory; try undeleting its parent directory first", name);
                SVNErrorManager.error(err);
            } else if (SVNProperty.SCHEDULE_REPLACE.equals(schedule)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "Can''t replace ''{0}'' in deleted directory; try undeleting its parent directory first", name);
                SVNErrorManager.error(err);
            }
        }
           
        if (entry.isAbsent() && SVNProperty.SCHEDULE_ADD.equals(schedule)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "''{0}'' is marked as absent, so it cannot be scheduled for addition", name);
            SVNErrorManager.error(err);
        }
            
        if (SVNProperty.SCHEDULE_ADD.equals(entry.getSchedule())) {
            if (SVNProperty.SCHEDULE_DELETE.equals(schedule)) {
                if (!entry.isDeleted()) {
                    deleteEntry(name);
                } else {
                    attributes.put(SVNProperty.SCHEDULE, null);
                }
            } else {
                attributes.remove(SVNProperty.SCHEDULE);
            }
        } else if (SVNProperty.SCHEDULE_DELETE.equals(entry.getSchedule())) {
            if (SVNProperty.SCHEDULE_DELETE.equals(schedule)) {
                attributes.remove(SVNProperty.SCHEDULE);
            } else if (SVNProperty.SCHEDULE_ADD.equals(schedule)) {
                attributes.put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_REPLACE);
            } 
        } else if (SVNProperty.SCHEDULE_REPLACE.equals(entry.getSchedule())) {
            if (SVNProperty.SCHEDULE_DELETE.equals(schedule)) {
                attributes.put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_DELETE);
            } else if (SVNProperty.SCHEDULE_ADD.equals(schedule) || SVNProperty.SCHEDULE_REPLACE.equals(schedule)) {
                attributes.remove(SVNProperty.SCHEDULE);
            }
        } else {
            if (SVNProperty.SCHEDULE_ADD.equals(schedule) && !entry.isDeleted()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "Entry ''{0}'' is already under version control", name);
                SVNErrorManager.error(err);
            } else if (schedule == null) {
                attributes.remove(SVNProperty.SCHEDULE);
            }
        }
    }
    
    public SVNEntry modifyEntry(String name, Map attributes, boolean save, boolean force) throws SVNException {
        if (name == null) {
            name = getThisDirName();
        }
        
        boolean deleted = false;
        if (attributes.containsKey(SVNProperty.SCHEDULE)) {
            SVNEntry entryBefore = getEntry(name, true);
            foldScheduling(name, attributes, force);
            SVNEntry entryAfter = getEntry(name, true);
            if (entryBefore != null && entryAfter == null) {
                deleted = true;
            }
        }
        
        SVNEntry entry = null;
        if (!deleted) {
            entry = getEntry(name, true);
            if (entry == null) {
                entry = addEntry(name);
            }
            
            Map entryAttrs = entry.asMap();
            for (Iterator atts = attributes.keySet().iterator(); atts.hasNext();) {
                String attName = (String) atts.next();
                String value = (String) attributes.get(attName);
                if (SVNProperty.CACHABLE_PROPS.equals(attName) || SVNProperty.PRESENT_PROPS.equals(attName)) {
                    String[] propsArray = SVNAdminArea.fromString(value, " ");
                    entryAttrs.put(attName, propsArray);
                    continue;
                }
               
                if (value != null) {
                    entryAttrs.put(attName, value);
                } else {
                    entryAttrs.remove(attName);
                }
            }
            
            if (!entry.isDirectory()) {
                SVNEntry rootEntry = getEntry(getThisDirName(), true);
                if (rootEntry != null) {
                    if (!SVNRevision.isValidRevisionNumber(entry.getRevision())) {
                        entry.setRevision(rootEntry.getRevision());
                    }
                    if (entry.getURL() == null) {
                        entry.setURL(SVNPathUtil.append(rootEntry.getURL(), SVNEncodingUtil.uriEncode(name)));
                    }
                    if (entry.getRepositoryRoot() == null) {
                        entry.setRepositoryRoot(rootEntry.getRepositoryRoot());
                    }
                    if (entry.getUUID() == null && !entry.isScheduledForAddition() && !entry.isScheduledForReplacement()) {
                        entry.setUUID(rootEntry.getUUID());
                    }
                    if (isEntryPropertyApplicable(SVNProperty.CACHABLE_PROPS)) {
                        if (entry.getCachableProperties() == null) {
                            entry.setCachableProperties(rootEntry.getCachableProperties());
                        }
                    }
                }
            }
            
            if (attributes.containsKey(SVNProperty.SCHEDULE)) {
                if (entry.isScheduledForDeletion()) {
                    entry.setCopied(false);
                    entry.setCopyFromRevision(-1);
                    entry.setCopyFromURL(null);
                } else {
                    entry.setKeepLocal(false);
                }
            }
        }
        
        if (save) {
            saveEntries(false);
        }
        return entry;
    }

    public void deleteEntry(String name) throws SVNException {
        Map entries = loadEntries();
        if (entries != null) {
            entries.remove(name);
        }
    }

    public SVNEntry getEntry(String name, boolean hidden) throws SVNException {
        Map entries = loadEntries();
        if (entries != null && entries.containsKey(name)) {
            SVNEntry entry = (SVNEntry)entries.get(name);
            if (!hidden && entry.isHidden()) {
                return null;
            }
            return entry;
        }
        return null;
    }

    public SVNEntry getVersionedEntry(String name, boolean hidden) throws SVNException {
        SVNEntry entry = getEntry(name, hidden);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", getFile(name));
            SVNErrorManager.error(err);
        }
        return entry;
    }

    public SVNEntry addEntry(String name) throws SVNException {
        Map entries = loadEntries();
        if (entries == null) {
            myEntries = new HashMap(); 
            entries = myEntries;
        }

        SVNEntry entry = entries.containsKey(name) ? (SVNEntry) entries.get(name) : new SVNEntry(new HashMap(), this, name);
        entries.put(name, entry);
        return entry;
    }

    public Iterator entries(boolean hidden) throws SVNException {
        Map entries = loadEntries();
        if (entries == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        List copy = new ArrayList(entries.values());
        if (!hidden) {
            for (Iterator iterator = copy.iterator(); iterator.hasNext();) {
                SVNEntry entry = (SVNEntry) iterator.next();
                if (entry.isHidden()) {
                    iterator.remove();
                }
            }
        }
        Collections.sort(copy);
        return copy.iterator();
    }
    
    public Map getEntries() throws SVNException {
        return loadEntries();
    }
    
    public void cleanup() throws SVNException {        
        getWCAccess().checkCancelled();
        for(Iterator entries = entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (entry.getKind() == SVNNodeKind.DIR && !getThisDirName().equals(entry.getName())) {
                File childDir = getFile(entry.getName());
                if(childDir.isDirectory()) {
                    try {
                        SVNAdminArea child = getWCAccess().open(childDir, true, true, 0);
                        child.cleanup();
                    } catch (SVNException e) {
                        if (e instanceof SVNCancelException) {
                            throw e;
                        }
                        if (isSafeCleanup()) {
                            SVNDebugLog.getDefaultLog().info("CLEANUP FAILED for " + childDir);
                            SVNDebugLog.getDefaultLog().info(e);
                            continue;
                        }
                        throw e;
                    }
                }
            } else {
                hasPropModifications(entry.getName());
                if (entry.getKind() == SVNNodeKind.FILE) {
                    hasTextModifications(entry.getName(), false);
                }
            }
        }
        if (isKillMe()) {
            removeFromRevisionControl(getThisDirName(), true, false);
        } else {
            runLogs();
        }
        SVNFileUtil.deleteAll(getAdminFile("tmp"), false);
    }
    

    public boolean hasTextConflict(String name) throws SVNException {
        SVNEntry entry = getEntry(name, false);
        if (entry == null || entry.getKind() != SVNNodeKind.FILE) {
            return false;
        }
        boolean conflicted = false;
        if (entry.getConflictNew() != null) {
            conflicted = SVNFileType.getType(getFile(entry.getConflictNew())) == SVNFileType.FILE;
        } 
        if (!conflicted && entry.getConflictWorking() != null) {
            conflicted = SVNFileType.getType(getFile(entry.getConflictWorking())) == SVNFileType.FILE;
        }
        if (!conflicted && entry.getConflictOld() != null) {
            conflicted = SVNFileType.getType(getFile(entry.getConflictOld())) == SVNFileType.FILE;
        }
        return conflicted;
    }

    public boolean hasPropConflict(String name) throws SVNException {
        SVNEntry entry = getEntry(name, false);
        if (entry != null && entry.getPropRejectFile() != null) {
            return SVNFileType.getType(getFile(entry.getPropRejectFile())) == SVNFileType.FILE;
        }
        return false;
    }

    public File getRoot() {
        return myDirectory;
    }

    public File getAdminTempDirectory() {
        return getAdminFile("tmp");
    }

    public File getAdminDirectory() {
        return myAdminRoot;
    }

    public File getAdminFile(String name) {
        return new File(getAdminDirectory(), name);
    }

    public File getFile(String name) {
        if (name == null) {
            return null;
        }
        return new File(getRoot(), name);
    }

    public SVNWCAccess getWCAccess() {
        return myWCAccess;
    }

    public void setWCAccess(SVNWCAccess wcAccess) {
        myWCAccess = wcAccess;
    }
    
    public void closeVersionedProperties() {
        myProperties = null;
        myBaseProperties = null;
    }
    
    public void closeWCProperties() {
        myWCProperties = null;
    }
    
    public void closeEntries() {
        myEntries = null;
    }

    public File getBaseFile(String name, boolean tmp) {
        String path = tmp ? "tmp/" : "";
        path += "text-base/" + name + ".svn-base";
        return getAdminFile(path);
    }

    protected abstract void writeEntries(Writer writer) throws IOException, SVNException;
    
    protected abstract int getFormatVersion();

    protected abstract Map fetchEntries() throws SVNException;

    protected SVNAdminArea(File dir){
        myDirectory = dir;
        myAdminRoot = new File(dir, SVNFileUtil.getAdminDirectoryName());
    }

    protected File getBasePropertiesFile(String name, boolean tmp) {
        String path = !tmp ? "" : "tmp/";
        path += getThisDirName().equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base";
        File propertiesFile = getAdminFile(path);
        return propertiesFile;
    }

    protected File getRevertPropertiesFile(String name, boolean tmp) {
        String path = !tmp ? "" : "tmp/";
        path += getThisDirName().equals(name) ? "dir-prop-revert" : "prop-base/" + name + ".svn-revert";
        File propertiesFile = getAdminFile(path);
        return propertiesFile;
    }

    public File getPropertiesFile(String name, boolean tmp) {
        String path = !tmp ? "" : "tmp/";
        path += getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
        File propertiesFile = getAdminFile(path);
        return propertiesFile;
    }

    protected Map loadEntries() throws SVNException {
        if (myEntries != null) {
            return myEntries;
        }
        myEntries = fetchEntries();
        if (myEntries != null) {
            resolveDefaults(myEntries);
        }
        return myEntries;
    }
    
    private static void resolveDefaults(Map entries) throws SVNException {
        SVNEntry defaultEntry = (SVNEntry) entries.get("");
        if (defaultEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Missing default entry");
            SVNErrorManager.error(err);
        }
        if (defaultEntry.getRevision() < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_REVISION, "Default entry has no revision number");
            SVNErrorManager.error(err);
        }
        if (defaultEntry.getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Default entry is missing no URL");
            SVNErrorManager.error(err);
        }
        for(Iterator names = entries.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            SVNEntry entry = (SVNEntry) entries.get(name);
            if (entry == null || entry == defaultEntry || entry.isDirectory()) {
                continue;
            } else if (entry.isFile()) {
                if (entry.getRevision() < 0) {
                    entry.setRevision(defaultEntry.getRevision());
                }
                if (entry.getURL() == null) {
                    entry.setURL(SVNPathUtil.append(defaultEntry.getURL(), SVNEncodingUtil.uriEncode(entry.getName())));
                }
                if (entry.getUUID() == null && !(entry.isScheduledForAddition() || entry.isScheduledForReplacement())) {
                    entry.setUUID(defaultEntry.getUUID());
                }
                if (entry.getCachableProperties() == null) {
                    entry.setCachableProperties(defaultEntry.getCachableProperties());
                }
            }
        }
    }

    protected Map getBasePropertiesStorage(boolean create) {
        if (myBaseProperties == null && create) {
            myBaseProperties = new HashMap();
        }
        return myBaseProperties;
    }

    protected Map getRevertPropertiesStorage(boolean create) {
        if (myRevertProperties == null && create) {
            myRevertProperties = new HashMap();
        }
        return myRevertProperties;
    }

    protected Map getPropertiesStorage(boolean create) {
        if (myProperties == null && create) {
            myProperties = new HashMap();
        }
        return myProperties;
    }
    
    protected Map getWCPropertiesStorage(boolean create) {
        if (myWCProperties == null && create) {
            myWCProperties = new HashMap();
        }
        return myWCProperties;
    }
    
    public static String asString(String[] array, String delimiter) {
        String str = null;
        if (array != null) {
            str = "";
            for (int i = 0; i < array.length; i++) {
                str += array[i];
                if (i < array.length - 1) {
                    str += delimiter;
                }
            }
        }
        return str;
    }
    
    public static String[] fromString(String str, String delimiter) {
        if (str == null) {
            return new String[0];
        }
        LinkedList list = new LinkedList(); 
        int startInd = 0;
        int ind = -1;
        while ((ind = str.indexOf(delimiter, startInd)) != -1) {
            list.add(str.substring(startInd, ind));
            startInd = ind;
            while (startInd < str.length() && str.charAt(startInd) == ' '){
                startInd++;
            }
        }
        if (startInd < str.length()) {
            list.add(str.substring(startInd));
        }
        return (String[])list.toArray(new String[list.size()]);
    }
    
    private void destroyAdminArea() throws SVNException {
        if (!isLocked()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Write-lock stolen in ''{0}''", getRoot());
            SVNErrorManager.error(err);
        }
        SVNFileUtil.deleteAll(getAdminDirectory(), getWCAccess());
        getWCAccess().closeAdminArea(getRoot());
    }
    
    public void commit(String target, SVNCommitInfo info, Map wcPropChanges,
            boolean removeLock, boolean recursive, boolean removeChangelist, 
            Collection explicitCommitPaths, ISVNCommitParameters params) throws SVNException {
        SVNAdminArea anchor = getWCAccess().retrieve(getWCAccess().getAnchor());
        String path = getRelativePath(anchor);
        path = getThisDirName().equals(target) ? path : SVNPathUtil.append(path, target);
        if (!explicitCommitPaths.contains(path)) {
            // if this item is explicitly copied -> skip it.
            SVNEntry entry = getEntry(target, true);
            if (entry != null && entry.getCopyFromURL() != null) {
                return;
            }
        }

        SVNLog log = getLog();
        String checksum = null;
        if (!getThisDirName().equals(target)) {
            loggyRemoveRevertFile(target, true, log);
            loggyRemoveRevertFile(target, false, log);
            
            File baseFile = getBaseFile(target, true);
            SVNFileType baseType = SVNFileType.getType(baseFile);
            if (baseType == SVNFileType.NONE) {
                baseFile = getBaseFile(target, false);
                baseType = SVNFileType.getType(baseFile);
            }
            if (baseType == SVNFileType.FILE) {
                checksum = SVNFileUtil.computeChecksum(baseFile);
            }
            recursive = false;
        } 
        
        Map command = new HashMap();
        if (info != null) {
            command.put(SVNLog.NAME_ATTR, target);
            command.put(SVNProperty.shortPropertyName(SVNProperty.COMMITTED_REVISION), Long.toString(info.getNewRevision()));
            command.put(SVNProperty.shortPropertyName(SVNProperty.COMMITTED_DATE), SVNTimeUtil.formatDate(info.getDate()));
            command.put(SVNProperty.shortPropertyName(SVNProperty.LAST_AUTHOR), info.getAuthor());
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }
        if (checksum != null) {
            command.put(SVNLog.NAME_ATTR, target);
            command.put(SVNProperty.shortPropertyName(SVNProperty.CHECKSUM), checksum);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }
        if (removeLock) {
            command.put(SVNLog.NAME_ATTR, target);
            log.addCommand(SVNLog.DELETE_LOCK, command, false);
            command.clear();
        }
        if (removeChangelist) {
            command.put(SVNLog.NAME_ATTR, target);
            log.addCommand(SVNLog.DELETE_CHANGELIST, command, false);
            command.clear();
        }
        command.put(SVNLog.NAME_ATTR, target);
        command.put(SVNLog.REVISION_ATTR, info == null ? null : Long.toString(info.getNewRevision()));
        if (!explicitCommitPaths.contains(path)) {
            command.put("implicit", "true");
        }
        log.addCommand(SVNLog.COMMIT, command, false);
        command.clear();
        if (wcPropChanges != null && !wcPropChanges.isEmpty()) {
            for (Iterator propNames = wcPropChanges.keySet().iterator(); propNames.hasNext();) {
                String propName = (String) propNames.next();
                String propValue = (String) wcPropChanges.get(propName);
                command.put(SVNLog.NAME_ATTR, target);
                command.put(SVNLog.PROPERTY_NAME_ATTR, propName);
                command.put(SVNLog.PROPERTY_VALUE_ATTR, propValue);
                log.addCommand(SVNLog.MODIFY_WC_PROPERTY, command, false);
                command.clear();
            }
        }
        log.save();
        runLogs();

        if (recursive) {
            for (Iterator ents = entries(true); ents.hasNext();) {
                SVNEntry entry = (SVNEntry) ents.next();
                if (getThisDirName().equals(entry.getName())) {
                    continue;
                }
                if (entry.getKind() == SVNNodeKind.DIR) {
                    File childPath = getFile(entry.getName());
                    SVNAdminArea childDir = getWCAccess().retrieve(childPath);
                    if (childDir != null) {
                        childDir.commit(getThisDirName(), info, null, removeLock, true, removeChangelist, explicitCommitPaths, params);
                    }
                } else {
                    if (entry.isScheduledForDeletion()) {
                        SVNEntry parentEntry = getEntry(getThisDirName(), true);
                        if (parentEntry.isScheduledForReplacement()) {
                            continue;
                        }
                    }
                    commit(entry.getName(), info, null, removeLock, false, removeChangelist, explicitCommitPaths, params);
                }
            }
        }
    }

    public void walkEntries(String name, ISVNEntryHandler handler, boolean showHidden, boolean recursive) throws SVNException {
        File path = getFile(name);
        SVNEntry entry = getEntry(name, showHidden);
        if (entry == null) {
            handler.handleError(path, SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", path));
            return;
        }
        if (entry.isFile()) {
            try {
                handler.handleEntry(path, entry, this);
            } catch (SVNException svne) {
                handler.handleError(path, svne.getErrorMessage());
            }
            return;
        } 

        if (getThisDirName().equals(name)) {
            try {
                walkThisDirectory(handler, showHidden, recursive);
            } catch (SVNException svne) {
                handler.handleError(path, svne.getErrorMessage());
            }
        }
    }
    
    public boolean isEntrySwitched(SVNEntry entry) throws SVNException {
        File entryPath = getFile(entry.getName()); 
        File parent = entryPath.getParentFile();
        if (parent == null) {
            return false;
        }
        
        SVNWCAccess access = SVNWCAccess.newInstance(getWCAccess());
        SVNAdminArea parentAdminArea = null;
        SVNEntry parentEntry = null;
        try {
            parentAdminArea = access.open(parent, false, 0);
            parentEntry = parentAdminArea.getVersionedEntry(parentAdminArea.getThisDirName(), false);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                return false;
            } 
            throw svne;
        } finally {
            access.close();
        }
        
        SVNURL parentSVNURL = parentEntry.getSVNURL();
        SVNURL thisSVNURL = entry.getSVNURL(); 
        if (parentSVNURL == null || thisSVNURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Cannot find a URL for ''{0}''", parentSVNURL == null ? parent : entryPath);
            SVNErrorManager.error(err);
        }
        
        SVNURL expectedSVNURL = parentSVNURL.appendPath(entryPath.getName(), false);
        return !thisSVNURL.equals(expectedSVNURL);
    }

    private void walkThisDirectory(ISVNEntryHandler handler, boolean showHidden, boolean recursive) throws SVNException {
        File thisDir = getFile("");
        
        SVNEntry thisEntry = getEntry(getThisDirName(), showHidden);
        if (thisEntry == null) {
            handler.handleError(thisDir, SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Directory ''{0}'' has no THIS_DIR entry", thisDir));
            return;
        }
        
        try {
            handler.handleEntry(thisDir, thisEntry, this);
        } catch (SVNException svne) {
            handler.handleError(thisDir, svne.getErrorMessage());
        }
        
        if (!recursive) {
            return;
        }
        
        for (Iterator entries = entries(showHidden); entries.hasNext();) {
            getWCAccess().checkCancelled();

            SVNEntry entry = (SVNEntry) entries.next();
            if (getThisDirName().equals(entry.getName())) {
                continue;
            }

            File childPath = getFile(entry.getName()); 
            if (entry.isFile()) {
                try {
                    handler.handleEntry(childPath, entry, this);
                } catch (SVNException svne) {
                    handler.handleError(childPath, svne.getErrorMessage());
                }
                
            } else if (entry.isDirectory()) {
                SVNAdminArea childArea = null;
                try {
                    childArea = getWCAccess().retrieve(childPath);
                } catch (SVNException svne) {
                    handler.handleError(childPath, svne.getErrorMessage());
                }
                if (childArea != null) {
                    childArea.walkThisDirectory(handler, showHidden, recursive);
                }
            }
        }
    }
    
    public void setCommitParameters(ISVNCommitParameters commitParameters) {
        myCommitParameters = commitParameters;
    }

    protected void setLocked(boolean locked) {
        myWasLocked = locked;
    }
    
    private void loggyRemoveRevertFile(String name, boolean isProp, SVNLog log) throws SVNException {
        String revertPath = null;
        if (isProp) {
            revertPath = SVNAdminUtil.getPropRevertPath(name, SVNNodeKind.FILE, false);
        } else {
            revertPath = SVNAdminUtil.getTextRevertPath(name, false);
        }

        File revertFile = getFile(revertPath);
        if (revertFile.isFile()) {
            Map command = new HashMap();
            command.put(SVNLog.NAME_ATTR, revertPath);
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
        }
    }
    
}
