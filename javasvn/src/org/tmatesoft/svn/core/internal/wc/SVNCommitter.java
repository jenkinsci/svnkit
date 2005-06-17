package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.diff.delta.ISVNDeltaGenerator;
import org.tmatesoft.svn.core.diff.delta.SVNAllDeltaGenerator;
import org.tmatesoft.svn.core.diff.delta.SVNSequenceDeltaGenerator;
import org.tmatesoft.svn.core.internal.ws.fs.SVNRAFileData;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.wc.ISVNEventListener;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.DebugLog;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 16.06.2005
 * Time: 3:59:09
 * To change this template use File | Settings | File Templates.
 */
public class SVNCommitter implements ISVNCommitPathHandler {

    private Map myCommitItems;
    private Map myModifiedFiles;
    private SVNWCAccess myWCAccess;
    private Collection myTmpFiles;

    public SVNCommitter(SVNWCAccess wcAccess, Map commitItems, Collection tmpFiles) {
        myCommitItems = commitItems;
        myWCAccess = wcAccess;
        myModifiedFiles = new TreeMap();
        myTmpFiles = tmpFiles;
    }

    public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
        SVNCommitItem item = (SVNCommitItem) myCommitItems.get(commitPath);
        if (item.isCopied()) {
            if (item.getCopyFromURL() == null) {
                SVNErrorManager.error("svn: Commit item '" + item.getFile() + "' has copy flag but no copyfrom URL");
            } else if (item.getRevision().getNumber() < 0) {
                SVNErrorManager.error("svn: Commit item '" + item.getFile() + "' has copy flag but an invalid revision");
            }
        }
        SVNEvent event = null;
        boolean closeDir = false;

        if (item.isAdded() && item.isDeleted()) {
            event = SVNEventFactory.createCommitEvent(myWCAccess.getAnchor().getRoot(), item.getFile(), SVNEventAction.COMMIT_REPLACED, item.getKind(), null);
        } else if (item.isAdded()) {
            String mimeType = null;
            if (item.getKind() == SVNNodeKind.FILE) {
                SVNWCAccess wcAccess = SVNWCAccess.create(item.getFile());
                mimeType = wcAccess.getAnchor().getProperties(wcAccess.getTargetName(), false).getPropertyValue(SVNProperty.MIME_TYPE);
            }
            event = SVNEventFactory.createCommitEvent(myWCAccess.getAnchor().getRoot(), item.getFile(), SVNEventAction.COMMIT_ADDED, item.getKind(), mimeType);
        } else if (item.isDeleted()) {
            event = SVNEventFactory.createCommitEvent(myWCAccess.getAnchor().getRoot(), item.getFile(), SVNEventAction.COMMIT_DELETED, item.getKind(), null);
        } else if (item.isContentsModified() || item.isPropertiesModified()) {
            SVNStatusType propType = item.isPropertiesModified() ? SVNStatusType.CHANGED : SVNStatusType.UNCHANGED;
            SVNStatusType textType = item.isContentsModified() ? SVNStatusType.CHANGED : SVNStatusType.UNCHANGED;
            event = SVNEventFactory.createCommitEvent(myWCAccess.getAnchor().getRoot(), item.getFile(), SVNEventAction.COMMIT_MODIFIED, item.getKind(), textType, propType);
        }
        if (event != null) {
            myWCAccess.svnEvent(event, ISVNEventListener.UNKNOWN);
        }
        long rev = item.getRevision().getNumber();
        long cfRev = item.getCopyFromURL() != null ? rev : -1;
        if (item.isDeleted()) {
            commitEditor.deleteEntry(commitPath, rev);
        }
        boolean fileOpen = false;
        if (item.isAdded()) {
            if (item.getKind() == SVNNodeKind.FILE) {
                commitEditor.addFile(commitPath, item.getCopyFromURL(), cfRev);
                fileOpen = true;
            } else {
                commitEditor.addDir(commitPath, item.getCopyFromURL(), cfRev);
                closeDir = true;
            }
        }
        if (item.isPropertiesModified()) {
            if (item.getKind() == SVNNodeKind.FILE) {
                if (!fileOpen) {
                    commitEditor.openFile(commitPath, rev);
                }
                fileOpen = true;
            } else if (!item.isAdded()) {
                // do not open dir twice.
                if ("".equals(commitPath)) {
                    DebugLog.log("comitter: open root: " + commitPath);
                    commitEditor.openRoot(rev);
                } else {
                    DebugLog.log("comitter: open dir: " + commitPath);
                    commitEditor.openDir(commitPath, rev);
                }
                closeDir = true;
            }
            sendPropertiedDelta(item, commitEditor);
        }
        if (item.isContentsModified() && item.getKind() == SVNNodeKind.FILE) {
            if (!fileOpen) {
                commitEditor.openFile(commitPath, rev);
            }
            myModifiedFiles.put(commitPath, item);
        } else if (fileOpen) {
            commitEditor.closeFile(commitPath, null);
        }
        return closeDir;
    }

    public void sendTextDeltas(ISVNEditor editor) throws SVNException {
        for (Iterator paths = myModifiedFiles.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNCommitItem item = (SVNCommitItem) myModifiedFiles.get(path);

            SVNEvent event = SVNEventFactory.createCommitEvent(myWCAccess.getAnchor().getRoot(), item.getFile(),
                SVNEventAction.COMMIT_DELTA_SENT, SVNNodeKind.FILE, null);
            myWCAccess.svnEvent(event, ISVNEventListener.UNKNOWN);

            SVNDirectory dir = myWCAccess.getDirectory(PathUtil.removeTail(item.getPath()));
            String name = PathUtil.tail(item.getPath());
            SVNEntry entry = dir.getEntries().getEntry(name, false);

            File tmpFile = dir.getBaseFile(name, true);
            myTmpFiles.add(tmpFile);
            SVNTranslator.translate(dir, name, name, SVNFileUtil.getBasePath(tmpFile), false, false);

            String checksum = null;
            String newChecksum = SVNFileUtil.computeChecksum(tmpFile);
            if (!item.isAdded()) {
                checksum = SVNFileUtil.computeChecksum(dir.getBaseFile(name, false));
                String realChecksum = entry.getChecksum();
                if (realChecksum == null || !realChecksum.equals(checksum)) {
                    SVNErrorManager.error("svn: Checksum mismatch for '" + dir.getFile(name, false) + "'; expected '" + realChecksum + "', actual: '" + checksum + "'");
                }
            }
            editor.applyTextDelta(path, checksum);
            boolean binary = dir.getProperties(name, false).getPropertyValue(SVNProperty.MIME_TYPE) != null ||
                    dir.getBaseProperties(name, false).getPropertyValue(SVNProperty.MIME_TYPE) != null;
            ISVNDeltaGenerator generator;
            if (item.isAdded() || binary) {
                generator = new SVNAllDeltaGenerator();
            } else {
                generator = new SVNSequenceDeltaGenerator();
            }
            SVNRAFileData base = new SVNRAFileData(dir.getBaseFile(name, false), true);
            SVNRAFileData work = new SVNRAFileData(tmpFile, true);
            try {
                generator.generateDiffWindow(path, editor, work, base);
            } finally {
                try {
                    base.close();
                } catch (IOException e) {
                    //
                }
                try {
                    work.close();
                } catch (IOException e) {
                    //
                }
            }
            editor.closeFile(path, newChecksum);
        }
    }

    private void sendPropertiedDelta(SVNCommitItem item, ISVNEditor editor) throws SVNException {

        SVNDirectory dir;
        String name;
        if (item.getKind() == SVNNodeKind.DIR) {
            dir = myWCAccess.getDirectory(item.getPath());
            name = "";
        } else {
            dir = myWCAccess.getDirectory(PathUtil.removeTail(item.getPath()));
            name = PathUtil.tail(item.getPath());
        }
        SVNEntry entry = dir.getEntries().getEntry(name, false);
        boolean replaced = false;
        if (entry != null) {
            replaced = entry.isScheduledForReplacement();
        }
        SVNProperties props = dir.getProperties(name, false);
        SVNProperties baseProps = replaced ? null : dir.getBaseProperties(name, false);
        Map diff = replaced ? props.asMap() : baseProps.compareTo(props);
        if (diff != null && !diff.isEmpty()) {
            props.copyTo(dir.getBaseProperties(name, true));
            myTmpFiles.add(dir.getBaseProperties(name, true).getFile());

            for (Iterator names = diff.keySet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                String value = (String) diff.get(propName);
                if (item.getKind() == SVNNodeKind.FILE) {
                    editor.changeFileProperty(item.getPath(), propName, value);
                } else {
                    editor.changeDirProperty(propName, value);
                }
            }
        }
    }


    public static SVNCommitInfo commit(SVNWCAccess wcAccess, Collection tmpFiles, Map commitItems, ISVNEditor commitEditor) throws SVNException {
        SVNCommitter committer = new SVNCommitter(wcAccess, commitItems, tmpFiles);
        SVNCommitUtil.driveCommitEditor(committer, commitItems.keySet(), commitEditor, -1);
        committer.sendTextDeltas(commitEditor);

        return commitEditor.closeEdit();
    }
}
