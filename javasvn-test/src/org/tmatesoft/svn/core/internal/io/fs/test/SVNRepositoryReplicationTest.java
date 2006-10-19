/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs.test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.replicator.SVNRepositoryReplicator;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * Program arguments:
 * 
 * sourceRepositoryURL targetRepositoryURL [fromRev] [toRev] [startRev] [endRev]
 * [useWC] [sourceWCRoot] [targetWCRoot]
 * 
 * sourceRepositoryURL - the url of the repository to copy from
 * 
 * targetRepositoryURL - the url of the repository to copy into
 * 
 * fromRev, toRev - revs range to replicate
 * 
 * startRev, endRev - revs range to compare
 * 
 * useWC: 1 - use WCs to compare repositories (slow comparison using events), 0 -
 * not using WCs while comparing repositories (quick comparison)
 * 
 * sourceWCRoot, targetWCRoot: if useWC == 1 - WC root dirs
 * 
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNRepositoryReplicationTest {

    public static void main(String[] args) {
        if (args.length < 4) {
            SVNDebugLog.getDefaultLog().info("Expected input values: 1. source repository url. 2. destination repository url. 3. source working copy dir. 4. destination working copy dir. [5.] top revision to copy up to");
            System.exit(1);
        }

        String sourceRepositoryUrl = args[0];
        String targetRepositoryUrl = args[1];
        long fromRev = args.length > 2 ? Long.parseLong(args[2]) : 1;
        long topRev = args.length > 3 ? Long.parseLong(args[3]) : -1;

        boolean useWC = true;
        if (args.length > 6) {
            if (Integer.parseInt(args[6]) == 0) {
                useWC = false;
            }
        }

        String sourceWC = args.length > 7 ? args[7] : null;
        String targetWC = args.length > 8 ? args[8] : null;

        if (useWC && (sourceWC == null || targetWC == null)) {
            SVNDebugLog.getDefaultLog().info("Both WC root dirs (source and target) must be specified");
            System.exit(1);
        }

        boolean passed = false;

        FSRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        DAVRepositoryFactory.setup();

        try {
            SVNURL srcURL = SVNURL.parseURIDecoded(sourceRepositoryUrl);
            SVNURL dstURL = SVNURL.parseURIDecoded(targetRepositoryUrl);
            SVNRepository src = SVNRepositoryFactory.create(srcURL);
            SVNRepository dst = SVNRepositoryFactory.create(dstURL);

            long latestRev = src.getLatestRevision();
            topRev = topRev > 0 && topRev <= latestRev ? topRev : latestRev;

            SVNRepositoryReplicator replicator = SVNRepositoryReplicator.newInstance();
            long processedRevs = replicator.replicateRepository(src, dst, fromRev, topRev);
            SVNDebugLog.getDefaultLog().info("Number of processed revisions: " + processedRevs);
            // compare history logs
            SVNDebugLog.getDefaultLog().info("Comparing full history...");
            compareHistory(src, dst);

            long startComparisonRev = args.length > 4 ? Long.parseLong(args[4]) : 1;
            startComparisonRev = startComparisonRev <= 0 ? 1 : startComparisonRev;
            long endComparisonRev = args.length > 5 ? Long.parseLong(args[5]) : topRev;
            endComparisonRev = endComparisonRev <= 0 ? topRev : endComparisonRev;

            passed = useWC ? compareRepositoriesWithWC(srcURL, dstURL, startComparisonRev, endComparisonRev, new File(sourceWC), new File(targetWC)) : compareRepositoriesWithoutWC(src, dst,
                    startComparisonRev, endComparisonRev);
        } catch (SVNException svne) {
            SVNDebugLog.getDefaultLog().info("Repositories comparing test FAILED with errors: " + svne.getErrorMessage().getMessage());
            System.out.println(svne.getErrorMessage().getMessage());
            System.exit(1);
        }
        if (passed) {
            SVNDebugLog.getDefaultLog().info("Repositories comparing test PASSED");
        } else {
            SVNDebugLog.getDefaultLog().info("Repositories comparing test FAILED");
            System.exit(1);
        }
    }

    public static boolean compareRepositoriesWithoutWC(SVNRepository srcRepos, SVNRepository dstRepos, long start, long end) throws SVNException {
        Map srcItems = new HashMap();
        Map dstItems = new HashMap();
        for (long i = start; i <= end; i++) {
            SVNDebugLog.getDefaultLog().info("Checking revision #" + i);
            if (!compareRevisionProps(srcRepos.getLocation(), dstRepos.getLocation(), i)) {
                return false;
            }

            SVNTestUpdateEditor srcEditor = new SVNTestUpdateEditor(srcRepos, srcItems);
            SVNTestUpdateEditor dstEditor = new SVNTestUpdateEditor(dstRepos, dstItems);

            final long previousRev = i - 1;

            srcRepos.update(i, null, true, new ISVNReporterBaton() {

                public void report(ISVNReporter reporter) throws SVNException {
                    if (previousRev == -1) {
                        reporter.setPath("", null, 0, true);
                    } else {
                        reporter.setPath("", null, previousRev, false);
                    }
                    reporter.finishReport();
                }
            }, srcEditor);

            dstRepos.update(i, null, true, new ISVNReporterBaton() {

                public void report(ISVNReporter reporter) throws SVNException {
                    if (previousRev == -1) {
                        reporter.setPath("", null, 0, true);
                    } else {
                        reporter.setPath("", null, previousRev, false);
                    }
                    reporter.finishReport();
                }
            }, dstEditor);

            if (srcEditor.getNumberOfChanges() != dstEditor.getNumberOfChanges()) {
                SVNDebugLog.getDefaultLog().info("Different number of changes in revision " + i);
                return false;
            }

            if (srcItems.size() != dstItems.size()) {
                SVNDebugLog.getDefaultLog().info("Different number of changed items in revision " + i);
                return false;
            }

            for (Iterator itemsIter = srcItems.keySet().iterator(); itemsIter.hasNext();) {
                String itemPath = (String) itemsIter.next();
                if (dstItems.get(itemPath) == null) {
                    SVNDebugLog.getDefaultLog().info("No item '" + itemPath + "' in '" + dstRepos.getLocation() + "' repository in revision " + i);
                    return false;
                }
                SVNItem srcItem = (SVNItem) srcItems.get(itemPath);
                SVNItem dstItem = (SVNItem) dstItems.get(itemPath);
                if (srcItem.getKind() == SVNNodeKind.DIR) {
                    if (!checkDirItems(srcItem, dstItem)) {
                        SVNDebugLog.getDefaultLog().info("Unequal dir items ('" + srcItem.getRepositoryPath() + "' vs. '" + dstItem.getRepositoryPath() + "') at revision " + i);
                        return false;
                    }
                } else {
                    if (!checkFileItems(srcItem, dstItem)) {
                        SVNDebugLog.getDefaultLog().info("Unequal file items ('" + srcItem.getRepositoryPath() + "' vs. '" + dstItem.getRepositoryPath() + "') at revision " + i);
                        return false;
                    }
                }
            }

            srcItems.clear();
            dstItems.clear();
        }

        return true;
    }

    private static boolean checkDirItems(SVNItem item1, SVNItem item2) {
        if (item1.getKind() != SVNNodeKind.DIR) {
            return false;
        }
        if (item2.getKind() != SVNNodeKind.DIR) {
            return false;
        }
        return checkItemProps(item1.getProperties(), item2.getProperties());
    }

    private static boolean checkFileItems(SVNItem item1, SVNItem item2) {
        if (item1.getKind() != SVNNodeKind.FILE) {
            return false;
        }
        if (item2.getKind() != SVNNodeKind.FILE) {
            return false;
        }
        if (!checkItemProps(item1.getProperties(), item2.getProperties())) {
            return false;
        }
        if (!item1.getChecksum().equals(item2.getChecksum())) {
            return false;
        }
        if (item1.getNumberOfDeltaChunks() != item2.getNumberOfDeltaChunks()) {
            return false;
        }
        return true;
    }

    private static boolean checkItemProps(Map props1, Map props2) {
        if (props1 == null && props2 == null) {
            return true;
        }

        if ((props1 == null && props2 != null) || (props1 != null && props2 == null)) {
            return false;
        }

        for (Iterator propsIter = props1.keySet().iterator(); propsIter.hasNext();) {
            String propName = (String) propsIter.next();
            if (props2.get(propName) == null) {
                return false;
            }
            if (propName.equals(SVNProperty.UUID)) {
                continue;
            }
            String propVal1 = (String) props1.get(propName);
            String propVal2 = (String) props2.get(propName);
            if (!propVal1.equals(propVal2)) {
                return false;
            }
        }

        return true;
    }

    private static boolean compareRepositoriesWithWC(SVNURL srcURL, SVNURL dstURL, long start, long end, File srcWCRoot, File dstWCRoot) throws SVNException {
        if (srcWCRoot.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Source WC directory already exists");
            SVNErrorManager.error(err);
        }

        if (dstWCRoot.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Target WC directory already exists");
            SVNErrorManager.error(err);
        }

        SVNClientManager manager = SVNClientManager.newInstance();
        SVNUpdateClient updateClient = manager.getUpdateClient();
        updateClient.setIgnoreExternals(true);

        Map events1 = new HashMap();
        Map events2 = new HashMap();
        long i = start;
        if (!compareRevisionProps(srcURL, dstURL, i)) {
            return false;
        }

        UpdateHandler handler = new UpdateHandler();
        handler.setEventsMap(events1);
        updateClient.setEventHandler(handler);
        updateClient.doCheckout(srcURL, srcWCRoot, SVNRevision.create(i), SVNRevision.create(i), true);

        handler.setEventsMap(events2);
        updateClient.doCheckout(dstURL, dstWCRoot, SVNRevision.create(i), SVNRevision.create(i), true);

        if (!compareEvents(events1, events2)) {
            return false;
        }

        for (i = start + 1; i <= end; i++) {
            if (!compareRevisionProps(srcURL, dstURL, i)) {
                return false;
            }

            events1.clear();
            events2.clear();

            handler.setEventsMap(events1);
            updateClient.doUpdate(srcWCRoot, SVNRevision.create(i), true);

            handler.setEventsMap(events2);
            updateClient.doUpdate(dstWCRoot, SVNRevision.create(i), true);

            if (!compareEvents(events1, events2)) {
                return false;
            }
        }
        return true;
    }

    private static boolean compareEvents(Map events1, Map events2) throws SVNException {
        // first check that we've got the same number of update events
        if (events1.size() != events2.size()) {
            return false;
        }
        for (Iterator eventsIter = events1.keySet().iterator(); eventsIter.hasNext();) {
            String path = (String) eventsIter.next();
            if (events2.get(path) == null) {
                return false;
            }
            SVNEvent event1 = (SVNEvent) events1.get(path);
            SVNEvent event2 = (SVNEvent) events2.get(path);
            if (event1.getNodeKind() == SVNNodeKind.DIR) {
                if (!compareDirs(event1.getFile(), event2.getFile(), "".equals(path))) {
                    return false;
                }
            } else if (event1.getNodeKind() == SVNNodeKind.FILE) {
                if (!compareFiles(event1.getFile(), event2.getFile())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean compareRevisionProps(SVNURL srcURL, SVNURL dstURL, long revision) throws SVNException {
        SVNRepository srcRep = SVNRepositoryFactory.create(srcURL);
        SVNRepository dstRep = SVNRepositoryFactory.create(dstURL);
        Map srcRevProps = new HashMap();
        Map dstRevProps = new HashMap();
        srcRep.getRevisionProperties(revision, srcRevProps);
        dstRep.getRevisionProperties(revision, dstRevProps);
        if (!compareProps(srcRevProps, dstRevProps)) {
            return false;
        }
        return true;
    }

    private static boolean compareProps(Map props1, Map props2) {
        if (props1.size() != props2.size()) {
            return false;
        }
        for (Iterator names = props1.keySet().iterator(); names.hasNext();) {
            String propName = (String) names.next();
            String propValue = (String) props1.get(propName);
            String propValue2 = (String) props2.get(propName);
            if (propValue2 == null || !propValue2.equals(propValue)) {
                return false;
            }
        }
        return true;
    }

    private static boolean compareDirs(File dir1, File dir2, boolean isRoot) throws SVNException {
        if (".svn".equals(dir1.getName())) {
            return true;
        }
        if (!dir1.isDirectory()) {
            return false;
        }
        if (!dir2.isDirectory()) {
            return false;
        }
        SVNClientManager manager = SVNClientManager.newInstance();
        SVNWCClient wcClient = manager.getWCClient();
        if (!isRoot && !dir1.getName().equals(dir2.getName())) {
            return false;
        }
        Map props1 = new HashMap();
        Map props2 = new HashMap();
        ISVNPropertyHandler handler1 = new PropertyHandler(props1);
        ISVNPropertyHandler handler2 = new PropertyHandler(props2);
        wcClient.doGetProperty(dir1, null, SVNRevision.HEAD, SVNRevision.WORKING, false, handler1);
        wcClient.doGetProperty(dir2, null, SVNRevision.HEAD, SVNRevision.WORKING, false, handler2);
        if (!compareProps(props1, props2)) {
            return false;
        }
        return true;
    }

    private static boolean compareFiles(File file1, File file2) throws SVNException {
        if (!file1.isFile()) {
            return false;
        }
        if (!file2.isFile()) {
            return false;
        }
        SVNClientManager manager = SVNClientManager.newInstance();
        SVNWCClient wcClient = manager.getWCClient();
        SVNInfo file1Info = wcClient.doInfo(file1, SVNRevision.WORKING);
        SVNInfo file2Info = wcClient.doInfo(file2, SVNRevision.WORKING);
        if (!file1.getName().equals(file2.getName())) {
            return false;
        }
        Map props1 = new HashMap();
        Map props2 = new HashMap();
        ISVNPropertyHandler handler1 = new PropertyHandler(props1);
        ISVNPropertyHandler handler2 = new PropertyHandler(props2);
        wcClient.doGetProperty(file1, null, SVNRevision.HEAD, SVNRevision.WORKING, false, handler1);
        wcClient.doGetProperty(file2, null, SVNRevision.HEAD, SVNRevision.WORKING, false, handler2);
        if (!compareProps(props1, props2)) {
            return false;
        }
        String checksum1 = file1Info.getChecksum();// SVNFileUtil.computeChecksum(file1);
        String checksum2 = file2Info.getChecksum();// SVNFileUtil.computeChecksum(file2);
        if (!checksum1.equals(checksum2)) {
            return false;
        }
        return true;
    }

    public static boolean compareHistory(SVNRepository src, SVNRepository dst) throws SVNException {
        List srcLog = new ArrayList();
        List dstLog = new ArrayList();
        srcLog = (List) src.log(new String[] {
            ""
        }, srcLog, 0, src.getLatestRevision(), true, false);
        dstLog = (List) dst.log(new String[] {
            ""
        }, dstLog, 0, dst.getLatestRevision(), true, false);
        for (int i = 0; i < srcLog.size(); i++) {
            SVNLogEntry srcEntry = (SVNLogEntry) srcLog.get(i);
            SVNLogEntry dstEntry = (SVNLogEntry) dstLog.get(i);
            if (!srcEntry.equals(dstEntry)) {
                return false;
            }
        }
        return true;
    }

}
