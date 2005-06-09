/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExportEditor;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNLog;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.SVNUpdateEditor;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;


public class SVNUpdateClient extends SVNBasicClient {

    public SVNUpdateClient() {
    }

    public SVNUpdateClient(ISVNEventListener eventDispatcher) {
        super(eventDispatcher);
    }

    public SVNUpdateClient(ISVNCredentialsProvider credentialsProvider) {
        super(credentialsProvider);
    }

    public SVNUpdateClient(ISVNCredentialsProvider credentialsProvider, ISVNEventListener eventDispatcher) {
        super(credentialsProvider, eventDispatcher);
    }

    public SVNUpdateClient(final ISVNCredentialsProvider credentialsProvider, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(credentialsProvider, options, eventDispatcher);
    }

    public SVNUpdateClient(ISVNRepositoryFactory repositoryFactory, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(repositoryFactory, options, eventDispatcher);
    }


    public long doUpdate(File file, SVNRevision revision, boolean recursive) throws SVNException {        
        long revNumber = getRevisionNumber(file, revision);
        SVNWCAccess wcAccess = createWCAccess(file);
        final SVNReporter reporter = new SVNReporter(wcAccess, recursive);
        try {
            wcAccess.open(true, recursive);
            SVNUpdateEditor editor = new SVNUpdateEditor(wcAccess, null, recursive);
            SVNRepository repos = createRepository(wcAccess.getAnchor().getEntries().getEntry("").getURL());
            String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
            DebugLog.log("calling repos update");
            repos.update(revNumber, target, recursive, reporter, editor);
            DebugLog.log("completed");

            if (editor.getTargetRevision() >= 0) {
            	if (recursive && !isIgnoreExternals()) {
            		handleExternals(wcAccess);
            	}
                DebugLog.log("dispatching completed event");
                dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(wcAccess, editor.getTargetRevision()));
            }
            return editor.getTargetRevision();
        } finally {            
            wcAccess.close(true, recursive);
            if (!isDoNotSleepForTimeStamp()) {
                SVNFileUtil.sleepForTimestamp();
            }            
        }
    }

    public long doSwitch(File file, String url, SVNRevision revision, boolean recursive) throws SVNException {
        url = validateURL(url);
        long revNumber = getRevisionNumber(file, revision);
        SVNWCAccess wcAccess = createWCAccess(file);
        final SVNReporter reporter = new SVNReporter(wcAccess, recursive);
        try {
            wcAccess.open(true, recursive);
            SVNUpdateEditor editor = new SVNUpdateEditor(wcAccess, url, recursive);
            SVNRepository repos = createRepository(wcAccess.getTargetEntryProperty(SVNProperty.URL));
            repos = createRepository(wcAccess.getAnchor().getEntries().getEntry("").getURL());
            String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
            repos.update(url, revNumber, target, recursive, reporter, editor);
            
            if (editor.getTargetRevision() >= 0 && recursive && !isIgnoreExternals()) {
                handleExternals(wcAccess);
                dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(wcAccess, editor.getTargetRevision()));
            }
            return editor.getTargetRevision();
        } finally {
            wcAccess.close(true, recursive);
            if (!isDoNotSleepForTimeStamp()) {
                SVNFileUtil.sleepForTimestamp();
            }            
        }
    }
    
    public long doCheckout(String url, File dstPath, SVNRevision pegRevision, SVNRevision revision, boolean recursive) throws SVNException {
        url = validateURL(url);
        if (dstPath == null) {
            dstPath = new File(".", PathUtil.tail(url));
        }
        if (!revision.isValid() && !pegRevision.isValid()) {
            pegRevision = SVNRevision.HEAD;
            revision = SVNRevision.HEAD;
        } else if (!revision.isValid()) {
            revision = pegRevision;
        } else if (!pegRevision.isValid()) {
            pegRevision = revision;
        }
        url = getURL(url, pegRevision, revision);
        SVNRepository repos = createRepository(url);
        long revNumber = getRevisionNumber(url, revision);
        SVNNodeKind targetNodeKind = repos.checkPath("", revNumber);
        String uuid = repos.getRepositoryUUID();
        if (targetNodeKind == SVNNodeKind.FILE) {
            SVNErrorManager.error("svn: URL '" + url + "' refers to a file not a directory");
        } else if (targetNodeKind == SVNNodeKind.NONE) {
            SVNErrorManager.error("svn: URL '" + url + "' doesn't exist");
        }
        setDoNotSleepForTimeStamp(true);
        long result = -1;
        try {
            if (!dstPath.exists() || (dstPath.isDirectory() && !SVNWCAccess.isVersionedDirectory(dstPath))) {
                createVersionedDirectory(dstPath, url, uuid, revNumber);
                result = doUpdate(dstPath, revision, recursive);
            } else if (dstPath.isDirectory() && SVNWCAccess.isVersionedDirectory(dstPath)) {
                SVNWCAccess wcAccess = SVNWCAccess.create(dstPath);
                if (url.equals(wcAccess.getTargetEntryProperty(SVNProperty.URL))) {
                    result = doUpdate(dstPath, revision, recursive);
                } else {
                    SVNErrorManager.error(0, null);
                }
            } else {
                SVNErrorManager.error(0, null);
            }
        } finally {
            if (!isCommandRunning()) {
                SVNFileUtil.sleepForTimestamp();
            }
            setDoNotSleepForTimeStamp(false);
        }
        return result;
    }
    
    public void doCopy(String srcURL, File dstPath, SVNRevision revision) throws SVNException {
        srcURL = validateURL(srcURL);
        long revNumber = getRevisionNumber(srcURL, revision);

        SVNRepository repos = createRepository(srcURL);
        SVNNodeKind srcKind = repos.checkPath("", revision.getNumber());
        if (srcKind == SVNNodeKind.NONE) {
            if (revision == SVNRevision.HEAD) {
                SVNErrorManager.error(0, null);
            }
            SVNErrorManager.error(0, null);
        }
        String srcUUID = repos.getRepositoryUUID();
        if (dstPath.isDirectory()) {
            dstPath = new File(dstPath, PathUtil.decode(PathUtil.tail(srcURL)));
            if (dstPath.exists()) {
                SVNErrorManager.error(0, null);
            }
        } else if (dstPath.exists()) {
            SVNErrorManager.error(0, null);
        }
        
        boolean sameRepositories = false;
        SVNWCAccess wcAccess = createWCAccess(dstPath); 
        try {
            wcAccess.open(true, false);
            // check for missing entry.
            SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName());
            if (entry != null && !entry.isDirectory()) {
                SVNErrorManager.error(0, null);
            }
            String uuid = wcAccess.getTargetEntryProperty(SVNProperty.UUID);
            sameRepositories = uuid.equals(srcUUID);
            if (srcKind == SVNNodeKind.DIR) {
                String dstURL = wcAccess.getAnchor().getEntries().getPropertyValue("", SVNProperty.URL);
                dstURL = PathUtil.append(dstURL, PathUtil.encode(dstPath.getName()));
                SVNDirectory targetDir = createVersionedDirectory(dstPath, dstURL, uuid, revNumber);
                SVNWCAccess wcAccess2 = new SVNWCAccess(targetDir, targetDir, "");
                wcAccess2.open(true, true);
                setDoNotSleepForTimeStamp(true);
                try {
                    SVNReporter reporter = new SVNReporter(wcAccess2, true);
                    SVNUpdateEditor editor = new SVNUpdateEditor(wcAccess2, null, true);
                    
                    repos.update(revNumber, null, true, reporter, editor);
                    dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(wcAccess, editor.getTargetRevision()));
                    if (sameRepositories) {
                        addDir(wcAccess.getAnchor(), dstPath.getName(), srcURL, editor.getTargetRevision());
                        addDir(wcAccess2.getAnchor(), "", srcURL, editor.getTargetRevision());
                        // fire added event.
                        dispatchEvent(SVNEventFactory.createAddedEvent(wcAccess, wcAccess.getAnchor(), 
                                wcAccess.getAnchor().getEntries().getEntry(dstPath.getName())));
                    } else {
                        SVNErrorManager.error(0, null);
                    }
                } finally {
                    setDoNotSleepForTimeStamp(false);
                    wcAccess2.close(true, true);
                }
            } else {
                Map properties = new HashMap();
                File tmpFile = null;
                OutputStream os = null;
                try {
                    File baseTmpFile = wcAccess.getAnchor().getBaseFile(dstPath.getName(), true);
                    tmpFile = SVNFileUtil.createUniqueFile(baseTmpFile.getParentFile(), dstPath.getName(), ".tmp");                    
                    os = new FileOutputStream(tmpFile);
                    long fileRevision = repos.getFile("", revNumber, properties, os);
                    os.close();
                    os = null;
                    SVNFileUtil.rename(tmpFile, baseTmpFile);
                } catch (IOException e) {
                    SVNErrorManager.error(0, e);
                } finally {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e) {
                        }
                    }
                    if (tmpFile != null) {
                        tmpFile.delete();
                    }
                }
                addFile(wcAccess.getAnchor(), dstPath.getName(), properties, sameRepositories ? srcURL : null, revNumber);
                wcAccess.getAnchor().runLogs();
                // fire added event.
                dispatchEvent(SVNEventFactory.createAddedEvent(wcAccess, wcAccess.getAnchor(), 
                        wcAccess.getAnchor().getEntries().getEntry(dstPath.getName())));
            }
        } finally {
            if (wcAccess != null) {
                wcAccess.close(true, false);
            }
            SVNFileUtil.sleepForTimestamp();
        }
    }
    
    public long doExport(String url, File dstPath, SVNRevision pegRevision, SVNRevision revision, String eolStyle, boolean force, boolean recursive) throws SVNException {
        url = validateURL(url);
        if (dstPath == null) {
            dstPath = new File(".", PathUtil.tail(url));
        }
        if (!revision.isValid() && !pegRevision.isValid()) {
            pegRevision = SVNRevision.HEAD;
            revision = SVNRevision.HEAD;
        } else if (!revision.isValid()) {
            revision = pegRevision;
        } else if (!pegRevision.isValid()) {
            pegRevision = revision;
        }
        url = getURL(url, pegRevision, revision);
        SVNRepository repos = createRepository(url);
        final long revNumber = getRevisionNumber(url, revision);
        SVNNodeKind targetNodeKind = repos.checkPath("", revNumber);
        
        if (targetNodeKind == SVNNodeKind.FILE) {
            if (dstPath.isDirectory()) {
                dstPath = new File(dstPath, PathUtil.decode(PathUtil.tail(url)));                
            }
            if (dstPath.exists()) {
                if (!force) {
                    SVNErrorManager.error(0, null);
                }
            } else {
                dstPath.getParentFile().mkdirs();
            }
            Map properties = new HashMap();
            OutputStream os = null;
            File tmpFile = SVNFileUtil.createUniqueFile(dstPath.getParentFile(), dstPath.getName(), ".tmp");
            try {
                os = new FileOutputStream(tmpFile);
                repos.getFile("", revNumber, properties, os);
                os.close();
                os = null;
                if (force && dstPath.exists()) { 
                    SVNFileUtil.deleteAll(dstPath);
                }
                Map keywords = SVNTranslator.computeKeywords((String) properties.get(SVNProperty.KEYWORDS),
                        url, 
                        (String) properties.get(SVNProperty.LAST_AUTHOR),
                        (String) properties.get(SVNProperty.COMMITTED_DATE),
                        (String) properties.get(SVNProperty.COMMITTED_REVISION));
                if (eolStyle == null) {
                    eolStyle = (String) properties.get(SVNProperty.EOL_STYLE);
                }
                byte[] eols = eolStyle == null ? null : SVNTranslator.getEOL(eolStyle);
                SVNTranslator.translate(tmpFile, dstPath, eols, keywords, properties.get(SVNProperty.SPECIAL) != null, true);
                if (properties.get(SVNProperty.EXECUTABLE) != null) {
                    SVNFileUtil.setExecutable(dstPath, true);
                }
                dispatchEvent(SVNEventFactory.createExportAddedEvent(dstPath.getParentFile(), dstPath));
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {
                    }
                }
                if (tmpFile != null) {
                    tmpFile.delete();
                }
            }
        } else if (targetNodeKind == SVNNodeKind.DIR) {
            SVNExportEditor editor = new SVNExportEditor(this, url, dstPath, force, eolStyle);
            repos.update(revNumber, null, recursive, new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, revNumber, true);
                    reporter.finishReport();
                }
            }, editor);
            if (!isIgnoreExternals() && recursive) {
                Map externals = editor.getCollectedExternals();
                for(Iterator files = externals.keySet().iterator(); files.hasNext();) {
                    File rootFile = (File) files.next();
                    String propValue = (String) externals.get(rootFile);
                    if (propValue == null) {
                        continue;
                    }
                    SVNExternalInfo[] infos = SVNWCAccess.parseExternals("", propValue);
                    for (int i = 0; i < infos.length; i++) {
                        File targetDir = new File(rootFile, infos[i].getPath());
                        String srcURL = infos[i].getOldURL();
                        SVNRevision srcRevision = SVNRevision.create(infos[i].getOldRevision());
                        String relativePath = targetDir.getAbsolutePath().substring(dstPath.getAbsolutePath().length());
                        relativePath = relativePath.replace(File.separatorChar, '/');
                        relativePath = PathUtil.removeLeadingSlash(relativePath);
                        relativePath = PathUtil.removeTrailingSlash(relativePath);
                        dispatchEvent(SVNEventFactory.createUpdateExternalEvent(null, relativePath));
                        try {
                            setEventPathPrefix(relativePath);                            
                            doExport(srcURL, targetDir, srcRevision, srcRevision, eolStyle, force, recursive);
                        } catch (Throwable th) {
                            dispatchEvent(new SVNEvent(th.getMessage()));
                        } finally {
                            setEventPathPrefix(null);
                        }
                    }
                }
            }
        }
        dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(null, revNumber));
        
        return revNumber;
    }

    public long doExport(File srcPath, final File dstPath, SVNRevision pegRevision, SVNRevision revision, String eolStyle, final boolean force, boolean recursive) throws SVNException {
        if (!SVNWCAccess.isVersionedDirectory(srcPath)) {
            SVNErrorManager.error(0, null);
        }
        DebugLog.log("exporting at revision: " + revision);
        SVNWCAccess wcAccess = createWCAccess(srcPath);
        String url = wcAccess.getTargetEntryProperty(SVNProperty.URL);
        if (revision == null || revision == SVNRevision.UNDEFINED) {
            revision = SVNRevision.WORKING;
        }
        if (revision != SVNRevision.BASE && revision != SVNRevision.WORKING) {
            // get rev number from wc.
            long revNumber = getRevisionNumber(srcPath, revision);
            revision = SVNRevision.create(revNumber);
            return doExport(url, dstPath, pegRevision, revision, eolStyle, force, recursive);
        } else {
            if (!force && dstPath.exists()) {
                SVNErrorManager.error(0, null);
            } 
            if (dstPath.exists()) {
                SVNFileUtil.deleteAll(dstPath);
            }
            try {
                wcAccess.open(true, recursive);
                if (srcPath.isFile()) {
                    SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(dstPath.getName());
                    if (entry == null || (revision == SVNRevision.WORKING && entry.isScheduledForDeletion()) || 
                            (entry.isScheduledForAddition() && revision == SVNRevision.BASE)) {
                        return -1;
                    }
                    copyVersionedFile(dstPath, wcAccess.getAnchor(), dstPath.getName(), revision, force, eolStyle);
                } else {
                    SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry("");
                    if (entry == null || (revision == SVNRevision.WORKING && entry.isScheduledForDeletion()) || (entry.isScheduledForAddition() && revision == SVNRevision.BASE)) {
                        return -1;
                    }
                    copyVersionedDir(dstPath, wcAccess.getAnchor(), recursive, revision, force, eolStyle);
                }
                dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(null, -1));
            } finally {
                wcAccess.close(true, recursive);
            }
        }
        return -1;
    }
    
    public void doRelocate(File dst, String oldURL, String newURL, boolean recursive) throws SVNException {
        oldURL = validateURL(oldURL);
        newURL = validateURL(newURL);
        SVNRepository repos = createRepository(newURL);
        repos.testConnection();
        String uuid = repos.getRepositoryUUID();
        SVNWCAccess wcAccess = createWCAccess(dst);
        try {
            wcAccess.open(true, recursive);
            String oldUUID = wcAccess.getTargetEntryProperty(SVNProperty.UUID);
            if (!oldUUID.equals(uuid)) {
                SVNErrorManager.error("The repository at '" + newURL + "' has uuid '" + uuid + "', but the WC has '" + oldUUID + "'");
            }
            doRelocate(wcAccess.getAnchor(), wcAccess.getTargetName(), oldURL, newURL, recursive);
        } finally {
            wcAccess.close(true, recursive);
            
        }
    }
    
    private void doRelocate(SVNDirectory dir, String targetName, String oldURL, String newURL, boolean recursive) throws SVNException {
        SVNEntries entries = dir.getEntries();
        for(Iterator ents = entries.entries(); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (targetName != null && !"".equals(targetName)) {
                if (!targetName.equals(entry.getName())) {
                    continue;
                }
            }
            String copyFromURL = entry.getCopyFromURL();
            if (copyFromURL != null && copyFromURL.startsWith(oldURL)) {
                copyFromURL = copyFromURL.substring(oldURL.length());
                copyFromURL = PathUtil.append(newURL, copyFromURL);
                copyFromURL = validateURL(copyFromURL);
                entry.setCopyFromURL(copyFromURL);
            }
            if (recursive && entry.isDirectory() && !"".equals(entry.getName())) {
                if (dir.getChildDirectory(entry.getName()) != null) {
                    doRelocate(dir.getChildDirectory(entry.getName()), null, oldURL, newURL, recursive);
                }
            } else if (entry.isFile() || "".equals(entry.getName())) {                
                String url = entry.getURL();
                if (url.startsWith(oldURL)) {
                    url = url.substring(oldURL.length());
                    url = PathUtil.append(newURL, url);
                    url = validateURL(url);
                    entry.setURL(url);
                }
            }
        }
        dir.getEntries().save(true);
    }
    

    private SVNDirectory createVersionedDirectory(File dstPath, String url, String uuid, long revNumber) throws SVNException {
        SVNDirectory.createVersionedDirectory(dstPath);
        // add entry first.
        SVNDirectory dir = new SVNDirectory(null, "", dstPath);
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry("");
        if (entry == null) {
            entry = entries.addEntry("");
        }
        entry.setURL(url);
        entry.setUUID(uuid);
        entry.setKind(SVNNodeKind.DIR);
        entry.setRevision(revNumber);
        entry.setIncomplete(true);;
        entries.save(true);
        return dir;
    }

    private void addDir(SVNDirectory dir, String name, String copyFromURL, long copyFromRev) throws SVNException {
        SVNEntry entry = dir.getEntries().getEntry(name);
        if (entry == null) {
            entry = dir.getEntries().addEntry(name);
        }
        entry.setKind(SVNNodeKind.DIR);
        if (copyFromURL != null) {
            entry.setCopyFromRevision(copyFromRev);
            entry.setCopyFromURL(copyFromURL);
            entry.setCopied(true);
        }
        entry.scheduleForAddition();
        if ("".equals(name) && copyFromURL != null) {
            updateCopiedDirectory(dir, name);
        }
        dir.getEntries().save(true);
    }
    
    private void updateCopiedDirectory(SVNDirectory dir, String name) throws SVNException {
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry(name);
        if (entry != null) {
            entry.setCopied(true);
            if (entry.isFile()) {
                dir.getWCProperties(name).delete();
            }
            if (!"".equals(name) && entry.isDirectory()) {
                SVNDirectory childDir = dir.getChildDirectory(name);
                updateCopiedDirectory(childDir, "");
            } else if ("".equals(name)) {
                dir.getWCProperties("").delete();                
                for (Iterator ents = entries.entries(); ents.hasNext();) {
                    SVNEntry childEntry = (SVNEntry) ents.next();
                    if ("".equals(childEntry.getName())) {
                        continue;
                    }
                    updateCopiedDirectory(dir, childEntry.getName());
                }
                entries.save(true);
            }
        }
    }

    private void addFile(SVNDirectory dir, String fileName, Map properties, String copyFromURL, long copyFromRev) throws SVNException {
        SVNLog log = dir.getLog(0);
        Map regularProps = new HashMap();
        Map entryProps = new HashMap();
        Map wcProps = new HashMap();
        for (Iterator names = properties.keySet().iterator(); names.hasNext();) {
            String propName = (String) names.next();
            String propValue = (String) properties.get(propName);
            if (propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                entryProps.put(SVNProperty.shortPropertyName(propName), propValue);
            } else if (propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                wcProps.put(propName, propValue);
            } else {
                regularProps.put(propName, propValue);
            }
        }
        entryProps.put(SVNProperty.shortPropertyName(SVNProperty.KIND), SVNProperty.KIND_FILE);
        entryProps.put(SVNProperty.shortPropertyName(SVNProperty.REVISION), "0");
        entryProps.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), SVNProperty.SCHEDULE_ADD);
        if (copyFromURL != null) {
            entryProps.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_REVISION), Long.toString(copyFromRev));
            entryProps.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_URL), copyFromURL);
            entryProps.put(SVNProperty.shortPropertyName(SVNProperty.COPIED), Boolean.TRUE.toString());
        }
        
        log.logChangedEntryProperties(fileName, entryProps);
        log.logChangedWCProperties(fileName, wcProps);
        dir.mergeProperties(fileName, regularProps, null, true, log);

        Map command = new HashMap();
        command.put(SVNLog.NAME_ATTR, SVNFileUtil.getBasePath(dir.getBaseFile(fileName, true)));
        command.put(SVNLog.DEST_ATTR, fileName);
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();
        command.put(SVNLog.NAME_ATTR, SVNFileUtil.getBasePath(dir.getBaseFile(fileName, true)));
        command.put(SVNLog.DEST_ATTR, SVNFileUtil.getBasePath(dir.getBaseFile(fileName, false)));
        log.addCommand(SVNLog.MOVE, command, false);
        log.save();
    }
    
    private void handleExternals(SVNWCAccess wcAccess) throws SVNException {
        setDoNotSleepForTimeStamp(true);
        try {
            for(Iterator externals = wcAccess.externals(); externals.hasNext();) {
                SVNExternalInfo external = (SVNExternalInfo) externals.next();
                if (external.getOldURL() == null && external.getNewURL() == null) {
                    continue;
                }
                long revNumber = external.getNewRevision();
                SVNRevision revision = revNumber >= 0 ? SVNRevision.create(revNumber) : SVNRevision.HEAD;
                setEventPathPrefix(external.getPath());
                try {
                    if (external.getOldURL() == null) {
                        external.getFile().mkdirs();
                        dispatchEvent(SVNEventFactory.createUpdateExternalEvent(wcAccess, ""));
                        doCheckout(external.getNewURL(), external.getFile(), revision,
                                revision, true);
                    } else if (external.getNewURL() == null) {
                        if (SVNWCAccess.isVersionedDirectory(external.getFile())) {
                            SVNWCAccess externalAccess = createWCAccess(external.getFile());
                            externalAccess.open(true, true);
                            externalAccess.getAnchor().destroy("", true);
                            externalAccess.close(true, true);
                        }                    
                    } else if (external.isModified()) {
                        deleteExternal(external);
                        external.getFile().mkdirs();
                        dispatchEvent(SVNEventFactory.createUpdateExternalEvent(wcAccess, ""));
                        doCheckout(external.getNewURL(), external.getFile(), revision,
                                revision, true);
                    } else {
                        if (!external.getFile().isDirectory()) {
                            external.getFile().mkdirs();
                            doCheckout(external.getNewURL(), external.getFile(), revision,
                                    revision, true);
                        } else {
                            String url = null;
                            if (SVNWCAccess.isVersionedDirectory(external.getFile())) {
                                SVNWCAccess externalAccess = createWCAccess(external.getFile());
                                url = externalAccess.getTargetEntryProperty(SVNProperty.URL);
                            }
                            if (!external.getNewURL().equals(url)) {
                                deleteExternal(external);
                            } 
                            // update or checkout.
                            external.getFile().mkdirs();
                            dispatchEvent(SVNEventFactory.createUpdateExternalEvent(wcAccess, ""));
                            doCheckout(external.getNewURL(), external.getFile(), revision,
                                    revision, true);
                        }
                    }
                } catch (Throwable th) {
                    dispatchEvent(new SVNEvent(th.getMessage()));
                    DebugLog.error(th);
                } finally {
                    setEventPathPrefix(null);
                }
            }
        } finally {
            setEventPathPrefix(null);
            setDoNotSleepForTimeStamp(false);
        }
    }

    private void deleteExternal(SVNExternalInfo external) throws SVNException {
        if (SVNWCAccess.isVersionedDirectory(external.getFile())) {
            SVNWCAccess externalAccess = createWCAccess(external.getFile());
            
            try {
                externalAccess.open(true, true);
                externalAccess.getAnchor().destroy("", true);
            } catch (Throwable th) {
                DebugLog.error(th);
            } finally {
                externalAccess.close(true, true);
            }
        }
        if (external.getFile().exists()) {
            external.getFile().getParentFile().mkdirs();
            File newLocation = SVNFileUtil.createUniqueFile(external.getFile().getParentFile(), external.getFile().getName(), ".OLD");
            try {
                SVNFileUtil.rename(external.getFile(), newLocation);
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            }
        }
    }

    private void copyVersionedDir(File dstPath, SVNDirectory dir, boolean recursive, SVNRevision revision, boolean force, String eol) throws SVNException {
        if (!force && dstPath.exists()) {
            SVNErrorManager.error(0, null);
        }
        SVNFileUtil.deleteAll(dstPath);
        dstPath.mkdirs();
        SVNEntries entries = dir.getEntries();
        for(Iterator ents = entries.entries(); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            if (revision != SVNRevision.WORKING && entry.isScheduledForAddition()) {
                continue;
            }
            if (revision != SVNRevision.BASE && entry.isScheduledForDeletion()) {
                continue;
            }
            if (entry.isFile()) {
                copyVersionedFile(new File(dstPath, entry.getName()), dir, entry.getName(), revision, force, eol);                
            } else if (recursive && entry.isDirectory() && dir.getFile(entry.getName(), false).isDirectory()) {
                SVNDirectory childDir = dir.getChildDirectory(entry.getName());
                if (childDir != null) {
                    copyVersionedDir(new File(dstPath, entry.getName()), childDir, recursive, revision, force, eol);
                }
            }
        }
    }
    
    private void copyVersionedFile(File dstPath, SVNDirectory dir, String fileName, SVNRevision revision, boolean force, String eol) throws SVNException {
        
        if (!force && dstPath.exists()) {
            SVNErrorManager.error(0, null);
        }
        SVNFileUtil.deleteAll(dstPath);
        Map keywordsMap = null;
        SVNProperties props = revision == SVNRevision.BASE ? dir.getBaseProperties(fileName, false) : dir.getProperties(fileName, false);
        SVNEntry entry = dir.getEntries().getEntry(fileName);
        String keywords = props.getPropertyValue(SVNProperty.KEYWORDS);
        String date = entry.getCommittedDate();
        byte[] eols = eol != null ? SVNTranslator.getEOL(eol) : null;
        if (keywords != null) {
            String author = entry.getAuthor();
            String rev = Long.toString(entry.getCommittedRevision());
            if (revision != SVNRevision.BASE) {
                boolean modified = dir.hasTextModifications(fileName, false);
                if (modified) {
                    author = "(local)";
                    rev += "M";
                }
            }
            keywordsMap = SVNTranslator.computeKeywords(keywords, entry.getURL(), author, entry.getCommittedDate(), rev);
        }
        if (eols == null) {
            eol = props.getPropertyValue(SVNProperty.EOL_STYLE); 
            eols =  eol != null ? 
                    SVNTranslator.getEOL(eol) : null;
        }
        boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;
        boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
            
        File srcFile = revision == SVNRevision.BASE ? 
                dir.getBaseFile(fileName, false) : dir.getFile(fileName, false);
        if (!srcFile.isFile()) {
            SVNErrorManager.error(0, null);
        }
        SVNTranslator.translate(srcFile, dstPath, eols, keywordsMap, special, true);
        if (executable) {
            SVNFileUtil.setExecutable(dstPath, true);
        }
        if (!special && date != null) {
            dstPath.setLastModified(TimeUtil.parseDate(date).getTime());
        }
    }
}
 