package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.internal.wc.SVNCommitUtil;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNLog;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNUpdateEditor;
import org.tmatesoft.svn.core.internal.wc.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.PathUtil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 09.06.2005
 * Time: 19:44:56
 * To change this template use File | Settings | File Templates.
 */
public class SVNCopyClient extends SVNBasicClient {

    private ISVNCommitHandler myCommitHandler;

    public SVNCopyClient() {
    }

    public SVNCopyClient(ISVNEventListener eventDispatcher) {
        super(eventDispatcher);
    }

    public SVNCopyClient(ISVNCredentialsProvider credentialsProvider) {
        super(credentialsProvider);
    }

    public SVNCopyClient(ISVNCredentialsProvider credentialsProvider, ISVNEventListener eventDispatcher) {
        super(credentialsProvider, eventDispatcher);
    }

    public SVNCopyClient(final ISVNCredentialsProvider credentialsProvider, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(credentialsProvider, options, eventDispatcher);
    }

    public SVNCopyClient(ISVNRepositoryFactory repositoryFactory, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(repositoryFactory, options, eventDispatcher);
    }

    public void setCommitHandler(ISVNCommitHandler handler) {
        myCommitHandler = handler;
    }

    public ISVNCommitHandler getCommitHandler() {
        if (myCommitHandler == null) {
            myCommitHandler = new DefaultSVNCommitHandler();
        }
        return myCommitHandler;
    }

    // path, path (this could be anything).
    // dstRevision: WORKING or HEAD only
    public long doCopy(File srcPath, SVNRevision srcPegRevision, SVNRevision srcRevision, File dstPath, SVNRevision dstPegRevision, SVNRevision dstRevision,
                       boolean force, boolean move, String commitMessage) throws SVNException {
        if (srcRevision == null || !srcRevision.isValid()) {
            srcRevision = SVNRevision.WORKING;
        }
        if (dstRevision == null || !dstRevision.isValid()) {
            dstRevision = SVNRevision.WORKING;
        }
        if (dstRevision != SVNRevision.HEAD && dstRevision != SVNRevision.WORKING) {
            SVNErrorManager.error("svn: Only HEAD or WORKING revision could be specified as copy or move target revision");
        }
        if (srcRevision == SVNRevision.WORKING && dstRevision == SVNRevision.WORKING) {
            // wc->wc
            wc2wcCopy(srcPath, dstPath, force, move);
        } else if (srcRevision == SVNRevision.WORKING) {
            // wc->url
            if (move) {
                SVNErrorManager.error("svn: Only WC to WC or URL to URL move is supported");
            }
            SVNWCAccess dstAccess = createWCAccess(srcPath);
            String dstURL = dstAccess.getTargetEntryProperty(SVNProperty.URL);
            if (dstPegRevision == null || !dstPegRevision.isValid()) {
                dstPegRevision = SVNRevision.parse(dstAccess.getTargetEntryProperty(SVNProperty.REVISION));
            }
            dstURL = getURL(dstURL, dstPegRevision, SVNRevision.HEAD);
            wc2urlCopy(srcPath, dstURL, commitMessage);
        } else if (dstRevision == SVNRevision.WORKING) {
            // url->wc
            if (move) {
                SVNErrorManager.error("svn: Only WC to WC or URL to URL move is supported");
            }
            SVNWCAccess srcAccess = createWCAccess(srcPath);
            String srcURL = srcAccess.getTargetEntryProperty(SVNProperty.URL);
            if (srcPegRevision == null || !srcPegRevision.isValid()) {
                srcPegRevision = SVNRevision.parse(srcAccess.getTargetEntryProperty(SVNProperty.REVISION));
            }
            long srcRevNumber = getRevisionNumber(srcPath, srcRevision);
            srcURL = getURL(srcURL, srcPegRevision, SVNRevision.create(srcRevNumber));
            url2wcCopy(srcURL, srcRevNumber, dstPath);
        } else {
            // url->url
            SVNWCAccess srcAccess = createWCAccess(srcPath);
            SVNWCAccess dstAccess = createWCAccess(srcPath);

            String srcURL = srcAccess.getTargetEntryProperty(SVNProperty.URL);
            String dstURL = dstAccess.getTargetEntryProperty(SVNProperty.URL);
            if (srcPegRevision == null || !srcPegRevision.isValid()) {
                srcPegRevision = SVNRevision.parse(srcAccess.getTargetEntryProperty(SVNProperty.REVISION));
            }
            if (dstPegRevision == null || !dstPegRevision.isValid()) {
                dstPegRevision = SVNRevision.parse(dstAccess.getTargetEntryProperty(SVNProperty.REVISION));
            }
            long srcRevNumber = getRevisionNumber(srcPath, srcRevision);
            srcURL = getURL(srcURL, srcPegRevision, SVNRevision.create(srcRevNumber));
            dstURL = getURL(dstURL, dstPegRevision, SVNRevision.HEAD);
            return url2urlCopy(srcURL, srcRevNumber, dstURL, commitMessage, move);
        }
        return -1;
    }


    // path, url (url->url or wc->url)
    public long doCopy(File srcPath, SVNRevision srcPegRevision, SVNRevision srcRevision, String dstURL, SVNRevision dstPegRevision,
                       boolean move, String commitMessage) throws SVNException {
        if (srcRevision == null || !srcRevision.isValid()) {
            srcRevision = SVNRevision.WORKING;
        }
        if (srcRevision != SVNRevision.WORKING) {
            // url->url
            dstURL = getURL(dstURL, dstPegRevision, SVNRevision.HEAD);
            SVNWCAccess wcAccess = createWCAccess(srcPath);
            String srcURL = wcAccess.getTargetEntryProperty(SVNProperty.URL);
            if (srcPegRevision == null || !srcPegRevision.isValid()) {
                srcPegRevision = SVNRevision.parse(wcAccess.getTargetEntryProperty(SVNProperty.REVISION));
            }
            long srcRevisionNumber = getRevisionNumber(srcPath, srcRevision);
            srcURL = getURL(srcURL, srcPegRevision, SVNRevision.create(srcRevisionNumber));
            return url2urlCopy(srcURL, srcRevisionNumber, dstURL, commitMessage, move);
        } else {
            // wc->url
            if (move) {
                SVNErrorManager.error("svn: Only WC to WC or URL to URL move is supported");
            }
            dstURL = getURL(dstURL, dstPegRevision, SVNRevision.HEAD);
            wc2urlCopy(srcPath, dstURL, commitMessage);
        }
        return -1;
    }

    // url, path (url->url or url->wc)
    // dstRevision: WORKING or HEAD only
    public long doCopy(String srcURL, SVNRevision srcPegRevision, SVNRevision srcRevision, File dstPath, SVNRevision dstPegRevision, SVNRevision dstRevision,
                       boolean move, String commitMessage) throws SVNException {
        if (dstRevision == null || !dstRevision.isValid()) {
            dstRevision = SVNRevision.WORKING;
        }
        if (dstRevision != SVNRevision.HEAD && dstRevision != SVNRevision.WORKING) {
            SVNErrorManager.error("svn: Only HEAD or WORKING revision could be specified as copy or move target revision");
        }
        srcURL = getURL(srcURL, srcPegRevision, srcRevision);
        long srcRevisionNumber = getRevisionNumber(srcURL, srcRevision);
        if (dstRevision == SVNRevision.WORKING) {
            // url->wc
            if (move) {
                SVNErrorManager.error("svn: Only WC to WC or URL to URL move is supported");
            }
            url2wcCopy(srcURL, srcRevisionNumber, dstPath);
        } else {
            // url->url
            SVNWCAccess wcAccess = createWCAccess(dstPath);
            String dstURL = wcAccess.getTargetEntryProperty(SVNProperty.NAME);
            if (dstPegRevision == null || !dstPegRevision.isValid()) {
                dstPegRevision = SVNRevision.parse(wcAccess.getTargetEntryProperty(SVNProperty.REVISION));
            }
            dstURL = getURL(dstURL, dstPegRevision, dstRevision);
            return url2urlCopy(srcURL, srcRevisionNumber, dstURL, commitMessage, move);
        }
        return -1;
    }

    // url, url (url->url only)
    public long doCopy(String srcURL, SVNRevision srcPegRevision, SVNRevision srcRevision, String dstURL, SVNRevision dstPegRevision,
                       boolean move, String commitMessage) throws SVNException {
        if (srcRevision == null) {
            srcRevision = SVNRevision.HEAD;
        }
        if (srcPegRevision == null) {
            srcPegRevision = SVNRevision.UNDEFINED;
        }
        if (dstPegRevision == null) {
            dstPegRevision = SVNRevision.UNDEFINED;
        }
        srcURL = validateURL(srcURL);
        dstURL = validateURL(dstURL);
        long srcRevNumber = getRevisionNumber(srcURL, srcRevision);

        srcURL = getURL(srcURL, srcPegRevision, SVNRevision.create(srcRevNumber));
        dstURL = getURL(dstURL, dstPegRevision, SVNRevision.HEAD);

        return url2urlCopy(srcURL, srcRevNumber, dstURL, commitMessage, move);
    }

    private void wc2urlCopy(File srcPath, String dstURL, String commitMessage) throws SVNException {

    }

    private void wc2wcCopy(File srcPath, File dstPath, boolean force, boolean move) throws SVNException {
        // 1. can't copy src to its own child
        if (SVNPathUtil.isChildOf(srcPath, dstPath)) {
            SVNErrorManager.error("svn: Cannot copy '" + srcPath + "' into its own child '" + dstPath + "'");
        }
        // 2. can't move path into itself
        if (move && srcPath.equals(dstPath)) {
            SVNErrorManager.error("svn: Cannot move '" + srcPath + "' into itself");
        }
        // 3. src should exist
        SVNFileType srcType = SVNFileType.getType(srcPath);
        if (srcType == SVNFileType.NONE) {
            SVNErrorManager.error("svn: Path '" + srcPath + "' does not exist");
        }
        // 4. if dst exists - use its child
        SVNFileType dstType = SVNFileType.getType(dstPath);
        if (dstType == SVNFileType.DIRECTORY) {
            dstPath = new File(dstPath, srcPath.getName());
            dstType = SVNFileType.getType(dstPath);
            if (dstType != SVNFileType.NONE) {
                SVNErrorManager.error("svn: '" + dstPath + "' already exist and is in a way");
            }
        } else if (dstType != SVNFileType.NONE) {
            SVNErrorManager.error("svn: File '" + dstPath + "' already exist");
        }
        // 5. if move -> check if dst could be deleted later.
        SVNWCAccess srcAccess = createWCAccess(srcPath);
        SVNWCAccess dstAccess = createWCAccess(dstPath);
        try {
            if (move) {
                if (srcAccess.getAnchor().getRoot().equals(dstAccess.getAnchor().getRoot())) {
                    dstAccess = srcAccess;
                }
                srcAccess.open(true, srcType == SVNFileType.DIRECTORY);
                if (!force) {
                    srcAccess.getAnchor().canScheduleForDeletion(dstAccess.getTargetName());
                }
            }
            if (srcAccess != dstAccess) {
                dstAccess.open(true, srcType == SVNFileType.DIRECTORY);
            }
            SVNEntry dstParentEntry = dstAccess.getAnchor().getEntries().getEntry("");
            if (dstParentEntry.isScheduledForDeletion()) {
                SVNErrorManager.error("svn: Cannot copy to '" + dstPath + "' as it is scheduled for deletion");
            }
            if (srcType == SVNFileType.DIRECTORY) {
                // copy directory.
                copyDirectory(dstAccess, srcAccess, dstPath.getName());
            } else {
                // copy single file.
                copyFile(dstAccess, srcAccess, dstPath.getName());
            }

            if (move) {
                srcAccess.getAnchor().scheduleForDeletion(srcPath.getName());
            }
        } finally {
            dstAccess.close(true, true);
            if (move && srcAccess != dstAccess) {
                srcAccess.close(true, true);
            }
        }
    }

    private void url2wcCopy(String srcURL, long srcRevision, File dstPath) throws SVNException {
        SVNRepository repos = createRepository(srcURL);
        SVNNodeKind srcKind = repos.checkPath("", srcRevision);
        if (srcKind == SVNNodeKind.NONE) {
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

        boolean sameRepositories;
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
                SVNDirectory targetDir = createVersionedDirectory(dstPath, dstURL, uuid, srcRevision);
                SVNWCAccess wcAccess2 = new SVNWCAccess(targetDir, targetDir, "");
                wcAccess2.open(true, true);
                setDoNotSleepForTimeStamp(true);
                try {
                    SVNReporter reporter = new SVNReporter(wcAccess2, true);
                    SVNUpdateEditor editor = new SVNUpdateEditor(wcAccess2, null, true);

                    repos.update(srcRevision, null, true, reporter, editor);
                    dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(wcAccess, editor.getTargetRevision()));
                    if (sameRepositories) {
                        addDir(wcAccess.getAnchor(), dstPath.getName(), srcURL, editor.getTargetRevision());
                        addDir(wcAccess2.getAnchor(), "", srcURL, editor.getTargetRevision());
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
                    repos.getFile("", srcRevision, properties, os);
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
                            //
                        }
                    }
                    if (tmpFile != null) {
                        tmpFile.delete();
                    }
                }
                addFile(wcAccess.getAnchor(), dstPath.getName(), properties, sameRepositories ? srcURL : null, srcRevision);
                wcAccess.getAnchor().runLogs();
                dispatchEvent(SVNEventFactory.createAddedEvent(wcAccess, wcAccess.getAnchor(),
                        wcAccess.getAnchor().getEntries().getEntry(dstPath.getName())));
            }
        } finally {
            if (wcAccess != null) {
                wcAccess.close(true, false);
            }
            if (!isDoNotSleepForTimeStamp()) {
                SVNFileUtil.sleepForTimestamp();
            }
        }

    }

    private long url2urlCopy(String srcURL, long srcRevision, String dstURL, String commitMessage, final boolean move) throws SVNException {
        String commonURL = SVNPathUtil.getCommonURLAncestor(srcURL, dstURL);
        if (commonURL.length() == 0 || commonURL.indexOf("://") < 0) {
            SVNErrorManager.error("svn: Source and dest appear not to be in the same repository (src: '" + srcURL + "'; dst: '" +dstURL + "'");
        }
        boolean resurrect = false;
        if (srcURL.equals(dstURL)) {
            commonURL = PathUtil.removeTail(commonURL);
            resurrect = true;
        }
        String srcRelative = PathUtil.decode(srcURL.substring(commonURL.length()));
        String dstRelative = PathUtil.decode(dstURL.substring(commonURL.length()));
        if (srcRelative.length() == 0 && move) {
            SVNErrorManager.error("svn: Cannot move '" + srcURL + "' into itself");
        }
        if (srcRelative.startsWith("/")) {
            srcRelative = srcRelative.substring(1);
        }
        if (dstRelative.startsWith("/")) {
            dstRelative = dstRelative.substring(1);
        }
        SVNRepository repos = createRepository(commonURL);
        long lastRevision = repos.getLatestRevision();
        if (srcRevision < 0) {
            srcRevision = lastRevision;
        }
        final SVNNodeKind srcKind = repos.checkPath(srcRelative, srcRevision);
        if (srcKind == SVNNodeKind.NONE) {
            SVNErrorManager.error("svn: Path '" + srcURL + "' does not exist in revision " + srcRevision);
        }
        SVNNodeKind dstKind = repos.checkPath(dstRelative, lastRevision);
        if (dstKind == SVNNodeKind.FILE) {
            SVNErrorManager.error("svn: Path '" + dstURL + "' already exists");
        } else if (dstKind == SVNNodeKind.DIR) {
            String newDstPath = PathUtil.append(dstRelative, PathUtil.decode(PathUtil.tail(srcURL)));
            dstKind = repos.checkPath(newDstPath, lastRevision);
            if (dstKind != SVNNodeKind.NONE) {
                SVNErrorManager.error("svn: Path '" + newDstPath + "' already exists");
            }
            dstRelative = newDstPath;
        }
        System.out.println("root: " + commonURL);
        System.out.println("src path: " + srcRelative);
        System.out.println("dst path: " + dstRelative);

        Collection commitItems = new ArrayList(2);
        commitItems.add(new SVNCommitItem(null, dstURL, srcURL, srcKind, SVNRevision.create(srcRevision), true, false, false, false, true));
        if (move) {
            commitItems.add(new SVNCommitItem(null, srcURL, null, srcKind, SVNRevision.create(srcRevision), false, true, false, false, false));
        }
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, (SVNCommitItem[]) commitItems.toArray(new SVNCommitItem[commitItems.size()]));
        if (commitMessage == null) {
            return -1;
        }

        ISVNEditor commitEditor = repos.getCommitEditor(commitMessage, null, false, null);
        final String srcPath = srcRelative;
        final String dstPath = dstRelative;
        final long srcRevNumber = srcRevision;
        final boolean isRessurect = resurrect;
        ISVNCommitPathHandler committer = new ISVNCommitPathHandler() {
            public void handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
                boolean doAdd = false;
                boolean doDelete = false;
                if (isRessurect) {
                    if (!move) {
                        doAdd = true;
                    }
                } else {
                    if (move) {
                        if (commitPath.equals(srcPath)) {
                            doDelete = true;
                        } else {
                            doAdd = true;
                        }
                    } else {
                        doAdd = true;
                    }
                }
                if (doDelete) {
                    commitEditor.deleteEntry(srcPath, -1);
                }
                if (doAdd) {
                    if (srcKind == SVNNodeKind.DIR) {
                        commitEditor.addDir(dstPath, srcPath, srcRevNumber);
                    } else {
                        commitEditor.addFile(dstPath, srcPath, srcRevNumber);
                        commitEditor.closeFile(null);
                    }
                }
            }
        };
        Collection paths = move ? Arrays.asList(new String[] {srcRelative, dstRelative}) :
                Collections.singletonList(dstRelative);

        SVNCommitInfo result;
        try {
            result = SVNCommitUtil.driveCommitEditor(committer, paths, commitEditor, -1);
        } catch (SVNException e) {
            try {
                commitEditor.abortEdit();
            } catch (SVNException inner) {
                //
            }
            throw e;
        }
        return result != null ? result.getNewRevision() : -1;
    }

/*
  // wc->wc (copy|move) or url->wc copy.
  public void doCopy(File srcPath, SVNRevision srcPegRevision, SVNRevision srcRevision, File dstPath, boolean force, boolean move) throws SVNException {
      if (srcRevision == null || !srcRevision.isValid()) {
          srcRevision = SVNRevision.WORKING;
      }
      if (srcRevision == SVNRevision.WORKING) {
          // wc->wc.
          // path assertions.
          SVNFileType srcFileType = SVNFileType.getType(srcPath);
          if (srcFileType == SVNFileType.NONE) {
              SVNErrorManager.error("svn: Path '" + srcPath + "' doesn't exist");
          }
          SVNFileType dstFileType = SVNFileType.getType(dstPath);
          if (dstFileType == SVNFileType.DIRECTORY) {
              dstPath = new File(dstPath, srcPath.getName());
          } else if (dstFileType != SVNFileType.NONE) {
              SVNErrorManager.error("svn: File '" + dstPath + "' already exists");
          }
          if (srcPath.equals(dstPath)) {
              SVNErrorManager.error("svn: Cannot copy path '" + dstPath + "' into itself");
          }
          File dstPathParent = dstPath.getParentFile();
          while(dstPathParent != null) {
              if (dstPathParent.equals(srcPath)) {
                  SVNErrorManager.error("svn: Cannot copy path '" + dstPath + "' into its own child");
              }
              dstPathParent = dstPathParent.getParentFile();
          }
          try {
              // if move -> open src and check if it could be deleted.
              SVNWCAccess dstAccess = createWCAccess(dstPath);
              SVNEntry dstParentEntry = dstAccess.getAnchor().getEntries().getEntry("");
              if (dstParentEntry.isScheduledForDeletion()) {
                  SVNErrorManager.error("svn: Cannot copy to '" + dstPath + "' as it is scheduled for deletion");
              }
              if (srcFileType == SVNFileType.DIRECTORY) {
                  // copy directory.
              } else {
                  // copy file.

              }
              // if move -> delete src.
          } finally {
              if (!isDoNotSleepForTimeStamp()) {
                  SVNFileUtil.sleepForTimestamp();
              }
          }
      } else if (!move) {
          // url->wc
          if (move) {
          }
          SVNWCAccess srcAccess = createWCAccess(srcPath);
          long srcRevNumber = getRevisionNumber(srcPath, srcRevision);
          SVNEntry srcEntry = srcAccess.getAnchor().getEntries().getEntry(srcAccess.getTargetName());
          if (srcEntry == null) {
              SVNErrorManager.error("svn: '" + srcPath + "' is not under version control");
          }
          String srcURL = srcEntry.getURL();
          if (srcURL == null) {
              SVNErrorManager.error("svn: '" + srcPath + "' does not seem to have a URL associated with it");
          }
          if (srcPegRevision == null || !srcPegRevision.isValid()) {
              srcPegRevision = SVNRevision.create(srcEntry.getRevision());
          }
          doCopy(srcURL, srcPegRevision, SVNRevision.create(srcRevNumber), dstPath);
      } else {
          SVNErrorManager.error("svn: no support for repos <--> working copy moves");
      }
      SVNFileType srcFileType = SVNFileType.getType(srcPath);
      if (srcFileType == SVNFileType.NONE) {
          SVNErrorManager.error("svn: Path '" + srcPath + "' doesn't exist");
      }
      SVNFileType dstFileType = SVNFileType.getType(dstPath);
      if (dstFileType == SVNFileType.DIRECTORY) {
          dstPath = new File(dstPath, srcPath.getName());
          if (dstPath.exists()) {
              SVNErrorManager.error("svn: File or directory '" + dstPath + "' already exists");
          }
      } else if (dstFileType != SVNFileType.NONE) {
          SVNErrorManager.error("svn: File '" + dstPath + "' already exists");
      }

      SVNWCAccess dstAccess = createWCAccess(dstPath);
      SVNWCAccess srcAccess = createWCAccess(srcPath);

      // checks if src is versioned.
      SVNEntry srcEntry = srcAccess.getAnchor().getEntries().getEntry(srcAccess.getTargetName());
      if (srcEntry == null) {
          // no entry.
          SVNErrorManager.error("svn: '" + srcPath + "' is not under version control");
      } else if (srcEntry.getURL() == null) {
          SVNErrorManager.error("svn: '" + srcPath + "' has no URL");
      }

      // checks below are only for wc->wc copy(move)
      if (dstAccess.getTarget().getRoot().equals(srcAccess.getTarget().getRoot())) {
          SVNErrorManager.error("svn: Can't copy '" + srcPath + "' into itself");
      }
      File dstParent = dstPath.getParentFile();
      while(dstParent != null) {
          if (dstParent.equals(srcAccess.getTarget().getRoot())) {
              SVNErrorManager.error("svn: Can't copy '" + srcPath + "' into its own child");
          }
          dstParent = dstParent.getParentFile();
      }

      try {
          if (move) {
              // lock src.
              srcAccess.open(true, true);
              // access may share the same parent, will use the same access.
              if (srcAccess.getAnchor().getRoot().equals(dstAccess.getAnchor().getRoot())) {
                  dstAccess = srcAccess;
              }
              if (!force) {
                  try {
                      srcAccess.getAnchor().canScheduleForDeletion(srcAccess.getTargetName());
                  } catch (SVNException e) {
                      SVNErrorManager.error(e.getMessage() + ";\nsvn: Move will not be attempted unless forced");
                  }
              }
          }
          if (dstAccess != srcAccess) {
              // this will lock dst acces, but will not add not yet created dirs.
              dstAccess.open(true, true);
          }
          try {
              if (srcFileType == SVNFileType.DIRECTORY) {
                  copyDirectory(dstAccess, srcPath, dstPath, force, move);
              } else {
                  copyFile(srcPath, dstPath, force, move);
              }
          } finally {
              if (!isDoNotSleepForTimeStamp()) {
                  SVNFileUtil.sleepForTimestamp();
              }
          }
          if (move) {
              // delete src.
              srcAccess.getAnchor().scheduleForDeletion(srcPath.getName());
          }
      } finally {
          if (move) {
              srcAccess.close(true, true);
          }
          if (srcAccess != dstAccess) {
              dstAccess.close(true, true);
          }
      }
      // make a copy, then sleep for timestamp.
      // 1. src is a file, entry is a file and file (link) exists.
      //   src entry is a file, but file is directory (obstructed).
      //   src entry is a file, but file doesn't exists (virtual operation).


      // 1.1. move of a file
             // file is not versioned -> error
             // file is already copied.
             // file is normally versioned.
      // 1.2 copy of a file
             // file is not versioned -> error
             // file is already copied.
             // file is normally versioned.


      // 2. copy of a dir.
      // check if src exists.
      // check if dst doesn't exists.
                                                /*
      SVNWCAccess srcAccess = createWCAccess(srcPath);
      if (move && !force) {
          try {
              srcAccess.open(true, true);
              // check if src could be deleted.
              srcAccess.getAnchor().canScheduleForDeletion(srcAccess.getTargetName());
          } catch (SVNException e) {
              SVNErrorManager.error("svn: '" + srcPath + "' has local modifications; try move with 'force' - 'true'");
          } finally {
              srcAccess.close(true, true);
          }
      }
      boolean destroySrc = false;
      if (move) {
          // test if dir was once moved.
          SVNEntry rootEntry = srcAccess.getTarget().getEntries().getEntry("");
          if (rootEntry == null) {
              SVNErrorManager.error("svn: '" + srcPath + "' is not under version control");
          } else if (rootEntry != null && rootEntry.isScheduledForAddition() && rootEntry.isCopied()) {
              // we could do this move, but have to destroy src dir after move.
              destroySrc = true;
          } else if (rootEntry.isScheduledForDeletion() || rootEntry.isScheduledForAddition() ||
                  rootEntry.isScheduledForReplacement()) {
              // no move of scheduled dir, try commit first.
              SVNErrorManager.error("svn: '" + srcPath + "' couldn't be moved; try committing first");
          }
      }
      if (srcAccess.getAnchor() == srcAccess.getTarget() && !"".equals(srcAccess.getTargetName())) {
          // we are coping directories.
          // if src is already copied and scheduked for addition -> src should be completely destroyed after move.
          try {
              if (dstPath.exists() && dstPath.isDirectory()) {
                  dstPath = new File(dstPath, srcPath.getName());
              }
              copyDirectory(srcPath, dstPath, force, move);
              if (move) {
                  srcAccess.open(true, true);
                  if (!force) {
                      srcAccess.getAnchor().canScheduleForDeletion(srcAccess.getTargetName());
                  }
                  srcAccess.getAnchor().scheduleForDeletion(srcAccess.getTargetName());
              }
          } finally {
              srcAccess.close(true, true);
          }
      }
  }
*//*
    // url->wc copy.
    public void doCopy(String srcURL, SVNRevision pegRevision, SVNRevision revision, File dstPath) throws SVNException {
        srcURL = validateURL(srcURL);
        srcURL = getURL(srcURL, pegRevision, revision);
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

        boolean sameRepositories;
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
                    repos.getFile("", revNumber, properties, os);
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
                            //
                        }
                    }
                    if (tmpFile != null) {
                        tmpFile.delete();
                    }
                }
                addFile(wcAccess.getAnchor(), dstPath.getName(), properties, sameRepositories ? srcURL : null, revNumber);
                wcAccess.getAnchor().runLogs();
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
                                                    */
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
            updateCopiedDirectory(dir, name, null, null, -1);
        }
        dir.getEntries().save(true);
    }

    private void updateCopiedDirectory(SVNDirectory dir, String name, String newURL, String copyFromURL, long copyFromRevision) throws SVNException {
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry(name);
        if (entry != null) {
            entry.setCopied(true);
            if (newURL != null) {
                entry.setURL(newURL);
            }
            if (entry.isFile()) {
                dir.getWCProperties(name).delete();
                if (copyFromURL != null) {
                    entry.setCopyFromURL(copyFromURL);
                    entry.setCopyFromRevision(copyFromRevision);
                }
            }
            boolean deleted = false;
            if (entry.isDeleted() && copyFromURL != null) {
                // convert to scheduled for deletion.
                deleted = true;
                entry.setDeleted(false);
                entry.scheduleForDeletion();
                if (entry.isDirectory()) {
                    entry.setKind(SVNNodeKind.FILE);
                }
            }
            if (entry.getLockToken() != null && copyFromURL != null) {
                entry.setLockToken(null);
                entry.setLockOwner(null);
                entry.setLockComment(null);
                entry.setLockCreationDate(null);
            }
            if (!"".equals(name) && entry.isDirectory() && !deleted) {
                SVNDirectory childDir = dir.getChildDirectory(name);
                String childCopyFromURL = copyFromURL == null ? null : PathUtil.append(copyFromURL, PathUtil.encode(entry.getName()));
                updateCopiedDirectory(childDir, "", newURL, childCopyFromURL, copyFromRevision);
            } else if ("".equals(name)) {
                dir.getWCProperties("").delete();
                if (copyFromURL != null) {
                    entry.setCopyFromURL(copyFromURL);
                    entry.setCopyFromRevision(copyFromRevision);
                }
                for (Iterator ents = entries.entries(); ents.hasNext();) {
                    SVNEntry childEntry = (SVNEntry) ents.next();
                    if ("".equals(childEntry.getName())) {
                        continue;
                    }
                    String childCopyFromURL = copyFromURL == null ? null : PathUtil.append(copyFromURL, PathUtil.encode(childEntry.getName()));
                    String newChildURL = newURL == null ? null :  PathUtil.append(newURL, PathUtil.encode(childEntry.getName()));
                    updateCopiedDirectory(dir, childEntry.getName(), newChildURL, childCopyFromURL, copyFromRevision);
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

    private void copyFile(SVNWCAccess dstAccess, SVNWCAccess srcAccess, String dstName) throws SVNException {
        SVNEntry dstEntry = dstAccess.getAnchor().getEntries().getEntry(dstName);
        File dstPath = new File(dstAccess.getAnchor().getRoot(), dstName);
        File srcPath = new File(srcAccess.getAnchor().getRoot(), srcAccess.getTargetName());
        if (dstEntry != null && dstEntry.isFile()) {
            if (dstEntry.isScheduledForDeletion()) {
                SVNErrorManager.error("svn: '" + dstPath + "' is scheduled for deletion; it must be committed before being overwritten");
            } else {
                SVNErrorManager.error("svn: There is already versioned item '" + dstPath + "'");
            }
        }
        SVNEntry srcEntry = srcAccess.getAnchor().getEntries().getEntry(srcAccess.getTargetName());
        if (srcEntry == null) {
            SVNErrorManager.error("svn: Cannot copy or move '" + srcPath + "': it's not under version control");
        } else if (srcEntry.isScheduledForAddition() || srcEntry.getURL() == null || srcEntry.isCopied()) {
            SVNErrorManager.error("svn: Cannot copy or move '" + srcPath + "': it's not in repository yet; try committing first");
        }
        // copy wc file.
        // TODO treat 'special' files in a special way.
        try {
            SVNFileUtil.copy(srcPath, dstPath, false);
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        }
        // copy props, props base and text-base
        File srcTextBase = srcAccess.getAnchor().getBaseFile(srcAccess.getTargetName(), false);
        SVNProperties srcProps = srcAccess.getAnchor().getProperties(srcAccess.getTargetName(), false);
        SVNProperties srcBaseProps = srcAccess.getAnchor().getBaseProperties(srcAccess.getTargetName(), false);

        File dstTextBase = dstAccess.getAnchor().getBaseFile(dstName, false);
        SVNProperties dstProps = dstAccess.getAnchor().getProperties(dstName, false);
        SVNProperties dstBaseProps = srcAccess.getAnchor().getBaseProperties(dstName, false);
        if (srcTextBase.exists()) {
            try {
                SVNFileUtil.copy(srcTextBase, dstTextBase, false);
            } catch (IOException e) {
            }
        }
        if (srcProps.getFile().exists()) {
            srcProps.copyTo(dstProps);
        }
        if (srcBaseProps.getFile().exists()) {
            srcBaseProps.copyTo(dstBaseProps);
        }
        // and finally -> add.
        String copyFromURL = srcAccess.getTargetEntryProperty(SVNProperty.URL);
        long copyFromRevision = SVNRevision.parse(srcAccess.getTargetEntryProperty(SVNProperty.REVISION)).getNumber();

        SVNEntry entry = dstAccess.getAnchor().add(dstName, false);
        entry.setCopied(true);
        entry.setCopyFromRevision(copyFromRevision);
        entry.setCopyFromURL(copyFromURL);
        entry.setRevision(copyFromRevision);
        entry.scheduleForAddition();
        dstAccess.getAnchor().getEntries().save(true);
    }

    private void copyDirectory(SVNWCAccess dstAccess, SVNWCAccess srcAccess, String dstName) throws SVNException {
        SVNEntry srcEntry = dstAccess.getTarget().getEntries().getEntry("");
        if (srcEntry == null) {
            SVNErrorManager.error("svn: '" + srcAccess.getTarget().getRoot() + "' is not under version control");
        } else if (srcEntry.isScheduledForDeletion() || srcEntry.getURL() == null || srcEntry.isCopied()) {
            SVNErrorManager.error("svn: Cannot copy or move '" + srcAccess.getTarget().getRoot() + "': it is in repository yet; try committing first");
        }
        String copyFromURL = srcAccess.getTargetEntryProperty(SVNProperty.URL);
        long copyFromRev = SVNRevision.parse(srcAccess.getTargetEntryProperty(SVNProperty.REVISION)).getNumber();

        String newURL = dstAccess.getAnchor().getEntries().getEntry("").getURL();
        newURL = PathUtil.append(newURL, PathUtil.encode(dstName));

        try {
            SVNFileUtil.copyDirectory(srcAccess.getTarget().getRoot(), dstAccess.getTarget().getRoot());
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        }

        dstAccess.addDirectory(dstName, dstAccess.getTarget().getRoot(), true, true);

        SVNEntry entry = dstAccess.getAnchor().getEntries().addEntry(dstName);
        entry.setCopyFromRevision(copyFromRev);
        entry.setKind(SVNNodeKind.DIR);
        entry.scheduleForAddition();
        entry.setCopyFromURL(copyFromURL);
        entry.setCopied(true);
        dstAccess.getTarget().getEntries().save(true);

        updateCopiedDirectory(dstAccess.getTarget(), "", newURL, null, -1);
        SVNEntry newRoot = dstAccess.getTarget().getEntries().getEntry("");
        newRoot.scheduleForAddition();
        newRoot.setCopyFromRevision(copyFromRev);
        newRoot.setCopyFromURL(copyFromURL);
        dstAccess.getTarget().getEntries().save(true);
    }
}