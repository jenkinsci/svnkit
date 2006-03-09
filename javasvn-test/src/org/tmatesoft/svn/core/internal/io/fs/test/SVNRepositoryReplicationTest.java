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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
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
 * sourceRepositoryURL targetRepositoryURL sourceWCRoot targetWCRoot
 * [topRevision]
 * 
 * sourceRepositoryURL - the url of the repository to copy from
 * targetRepositoryURL - the url of the repository to copy into sourceWCRoot -
 * the root dir path where revisions of the source repository
 * (sourceRepositoryURL) will be checked out targetWCRoot - the root dir path
 * where revisions of the target repository (targetRepositoryURL) will be
 * checked out topRevision - optional: used to restrict the range of revisions
 * (from 0 to topRevision)
 * 
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNRepositoryReplicationTest {

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out
                    .println("Expected input values: 1. source repository url. 2. destination repository url. 3. source working copy dir. 4. destination working copy dir. [5.] top revision to copy up to");
            SVNDebugLog
                    .logInfo("Expected input values: 1. source repository url. 2. destination repository url. 3. source working copy dir. 4. destination working copy dir. [5.] top revision to copy up to");
            System.exit(1);
        }

        String sourceRepositoryUrl = args[0];
        String targetRepositoryUrl = args[1];
        String sourceWC = args[2];
        String targetWC = args[3];
        long fromRev = args.length > 4 ? Long.parseLong(args[4]) : 1;
        long topRev = args.length > 5 ? Long.parseLong(args[5]) : -1;
        
        boolean useCheckout = true;
        if(args.length > 8){
            if(Integer.parseInt(args[8]) == 0){
                useCheckout = false;
            }
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
            System.out.println("Number of processed revisions: " + processedRevs);
            SVNDebugLog.logInfo("Number of processed revisions: " + processedRevs);
            //compare history logs
            System.out.println("Comparing full history...");
            SVNDebugLog.logInfo("Comparing full history...");
            compareHistory(src, dst);

            long startComparisonRev = args.length > 6 ? Long.parseLong(args[6]) : 1;
            startComparisonRev = startComparisonRev <= 0 ? 1 : startComparisonRev; 
            long endComparisonRev = args.length > 7 ? Long.parseLong(args[7]) : topRev;
            endComparisonRev = endComparisonRev <= 0 ? topRev : endComparisonRev; 

            passed = useCheckout ? compareRepositories1(srcURL, dstURL, topRev, new File(sourceWC), new File(targetWC)) : compareRepositories2(srcURL, dstURL, startComparisonRev, endComparisonRev, new File(sourceWC), new File(targetWC));
        } catch (SVNException svne) {
            SVNDebugLog.logInfo("Repositories comparing test FAILED with errors: " + svne.getErrorMessage().getMessage());
            System.out.println("Repositories comparing test FAILED with errors: " + svne.getErrorMessage().getMessage());
            System.out.println(svne.getErrorMessage().getMessage());
            System.exit(1);
        }
        if (passed) {
            SVNDebugLog.logInfo("Repositories comparing test PASSED");
            System.out.println("Repositories comparing test PASSED");
        } else {
            SVNDebugLog.logInfo("Repositories comparing test FAILED");
            System.out.println("Repositories comparing test FAILED");
            System.exit(1);
        }
    }

    private static boolean compareRepositories1(SVNURL srcURL, SVNURL dstURL, long top, File srcWCRoot, File dstWCRoot) throws SVNException {
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
        
        for (long i = 1; i <= top; i++) {
            SVNDebugLog.logInfo("Checking revision #" + i);
            System.out.println("Checking revision #" + i);
            if (!compareRevisionProps(srcURL, dstURL, i)) {
                return false;
            }
            if (srcWCRoot.exists()) {
                SVNFileUtil.deleteAll(srcWCRoot, true);
            }
            if (dstWCRoot.exists()) {
                SVNFileUtil.deleteAll(dstWCRoot, true);
            }
            updateClient.doCheckout(srcURL, srcWCRoot, SVNRevision.create(i), SVNRevision.create(i), true);
            updateClient.doCheckout(dstURL, dstWCRoot, SVNRevision.create(i), SVNRevision.create(i), true);
            if (!compareDirs(srcWCRoot, dstWCRoot, true)) {
                SVNDebugLog.logInfo("Unequal Working Copies at revision " + i);
                return false;
            }
        }
        return true;
    }

    private static boolean compareRepositories2(SVNURL srcURL, SVNURL dstURL, long start, long end, File srcWCRoot, File dstWCRoot) throws SVNException {
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
        SVNDebugLog.logInfo("Checking revision #" + i);
        System.out.println("Checking revision #" + i);
        if (!compareRevisionProps(srcURL, dstURL, i)) {
            return false;
        }

        UpdateHandler handler = new UpdateHandler();
        handler.setEventsMap(events1);
        updateClient.setEventHandler(handler);
        updateClient.doCheckout(srcURL, srcWCRoot, SVNRevision.create(i), SVNRevision.create(i), true);
        
        handler.setEventsMap(events2);
        updateClient.doCheckout(dstURL, dstWCRoot, SVNRevision.create(i), SVNRevision.create(i), true);
        
        if (!compareEvents(events1, events2, i)) {
            SVNDebugLog.logInfo("Unequal Working Copies at revision " + i);
            return false;
        }
        
/*        if (!compareDirs(srcWCRoot, dstWCRoot, true)) {
            SVNDebugLog.logInfo("Unequal Working Copies at revision " + i);
            return false;
        }*/
        
        for ( i = start + 1; i <= end; i++ ) {
            SVNDebugLog.logInfo("Checking revision #" + i);
            System.out.println("Checking revision #" + i);
            if (!compareRevisionProps(srcURL, dstURL, i)) {
                return false;
            }

            events1.clear();
            events2.clear();

            handler.setEventsMap(events1);
            updateClient.doUpdate(srcWCRoot, SVNRevision.create(i), true);

            handler.setEventsMap(events2);
            updateClient.doUpdate(dstWCRoot, SVNRevision.create(i), true);

            if (!compareEvents(events1, events2, i)) {
                SVNDebugLog.logInfo("Unequal Working Copies at revision " + i);
                return false;
            }

/*            if (!compareDirs(srcWCRoot, dstWCRoot, true)) {
                SVNDebugLog.logInfo("Unequal Working Copies at revision " + i);
                return false;
            }*/
        }
        return true;
    }

    private static boolean compareEvents(Map events1, Map events2, long revision) throws SVNException {
        //first check that we've got the same number of update events
        if(events1.size() != events2.size()){
            SVNDebugLog.logInfo("Unequal Working Copies at revision " + revision + ": different number of events");
            return false;
        }
        for(Iterator eventsIter = events1.keySet().iterator(); eventsIter.hasNext();){
            String path = (String)eventsIter.next();
            if(events2.get(path) == null){
                SVNDebugLog.logInfo("Unequal Working Copies at revision " + revision + ": path '" + path + "'");
                return false;
            }
            SVNEvent event1 = (SVNEvent)events1.get(path);
            SVNEvent event2 = (SVNEvent)events2.get(path);
            if(event1.getNodeKind() == SVNNodeKind.DIR){
                if(!compareDirs(event1.getFile(), event2.getFile(), "".equals(path))){
                    return false;
                }
            }else if(event1.getNodeKind() == SVNNodeKind.FILE){
                if(!compareFiles(event1.getFile(), event2.getFile())){
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
            SVNDebugLog.logInfo("Unequal revision props at revision " + revision);
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
            SVNDebugLog.logInfo("'" + dir1 + "' is not a directory");
            return false;
        }
        if (!dir2.isDirectory()) {
            SVNDebugLog.logInfo("'" + dir2 + "' is not a directory");
            return false;
        }
        SVNClientManager manager = SVNClientManager.newInstance();
        SVNWCClient wcClient = manager.getWCClient();
        if (!isRoot && !dir1.getName().equals(dir2.getName())) {
            SVNDebugLog.logInfo("Unequal dir names: '" + dir1.getName() + "' vs. '" + dir2.getName() + "'");
            return false;
        }
        Map props1 = new HashMap();
        Map props2 = new HashMap();
        ISVNPropertyHandler handler1 = new PropertyHandler(props1);
        ISVNPropertyHandler handler2 = new PropertyHandler(props2);
        wcClient.doGetProperty(dir1, null, SVNRevision.HEAD, SVNRevision.WORKING, false, handler1);
        wcClient.doGetProperty(dir2, null, SVNRevision.HEAD, SVNRevision.WORKING, false, handler2);
        if (!compareProps(props1, props2)) {
            SVNDebugLog.logInfo("Unequal dir props: '" + dir1 + "' vs. '" + dir2 + "'");
            return false;
        }
/*        File[] entries1 = dir1.listFiles();
        File[] entries2 = dir2.listFiles();
        if (entries1.length != entries2.length) {
            SVNDebugLog.logInfo("Unequal dirs (different number of children): '" + dir1 + "' (found " + entries1.length + " children) vs. '" + dir2 + "' (found " + entries2.length + " children)");
            return false;
        }
        for (int i = 0; i < entries1.length; i++) {
            File entry = entries1[i];
            if (".svn".equals(entry.getName())) {
                continue;
            }
            File entryToCompareWith = null;
            for (int j = 0; j < entries2.length; j++) {
                File candidateEntry = entries2[j];
                if (candidateEntry.getName().equals(entry.getName())) {
                    entryToCompareWith = candidateEntry;
                    break;
                }
            }
            if (entryToCompareWith == null) {
                SVNDebugLog.logInfo("Missing entry named '" + entry.getName() + "' in folder '" + dir2 + "'");
                return false;
            }
            SVNInfo entryInfo = wcClient.doInfo(entry, SVNRevision.WORKING);
            SVNInfo entryToCompareWithInfo = wcClient.doInfo(entryToCompareWith, SVNRevision.WORKING);
            if(entryInfo.getKind() != entryToCompareWithInfo.getKind()){
                SVNDebugLog.logInfo("Unequal node kinds: '" + entryInfo.getPath() + "' (" + entryInfo.getKind() + ") vs. '" + entryToCompareWithInfo.getPath() + "' (" + entryToCompareWithInfo.getKind() + ")");
                return false;
            }
            /*
            if (entry.isDirectory()) {
                if (!compareDirs(entry, entryToCompareWith, false)) {
                    return false;
                }
            } else {
                if (!compareFiles(entry, entryToCompareWith)) {
                    return false;
                }
            }*/
//        }
        return true;
    }

    private static boolean compareFiles(File file1, File file2) throws SVNException {
        if (!file1.isFile()) {
            SVNDebugLog.logInfo("'" + file1 + "' is not a file");
            return false;
        }
        if (!file2.isFile()) {
            SVNDebugLog.logInfo("'" + file2 + "' is not a file");
            return false;
        }
        SVNClientManager manager = SVNClientManager.newInstance();
        SVNWCClient wcClient = manager.getWCClient();
        SVNInfo file1Info = wcClient.doInfo(file1, SVNRevision.WORKING);
        SVNInfo file2Info = wcClient.doInfo(file2, SVNRevision.WORKING);
        if (!file1.getName().equals(file2.getName())) {
            SVNDebugLog.logInfo("Unequal file names: '" + file1.getName() + "' vs. '" + file2.getName() + "'");
            return false;
        }
        Map props1 = new HashMap();
        Map props2 = new HashMap();
        ISVNPropertyHandler handler1 = new PropertyHandler(props1);
        ISVNPropertyHandler handler2 = new PropertyHandler(props2);
        wcClient.doGetProperty(file1, null, SVNRevision.HEAD, SVNRevision.WORKING, false, handler1);
        wcClient.doGetProperty(file2, null, SVNRevision.HEAD, SVNRevision.WORKING, false, handler2);
        if (!compareProps(props1, props2)) {
            SVNDebugLog.logInfo("Unequal file props: '" + file1 + "' vs. '" + file2 + "'");
            return false;
        }
        String checksum1 = file1Info.getChecksum();// SVNFileUtil.computeChecksum(file1);
        String checksum2 = file2Info.getChecksum();// SVNFileUtil.computeChecksum(file2);
        if (!checksum1.equals(checksum2)) {
            SVNDebugLog.logInfo("Unequal file contents: '" + file1 + "' (" + checksum1 + ") vs. '" + file2 + "' (" + checksum2 + ")");
            return false;
        }
        return true;
    }

    public static boolean compareHistory(SVNRepository src, SVNRepository dst) throws SVNException {
        List srcLog = new ArrayList(); 
        List dstLog = new ArrayList(); 
        srcLog = (List) src.log(new String[] {""}, srcLog, 0, src.getLatestRevision(), true, false);
        SVNDebugLog.logInfo("source history fetched");
        dstLog = (List) dst.log(new String[] {""}, dstLog, 0, dst.getLatestRevision(), true, false);
        SVNDebugLog.logInfo("target history fetched");
        for (int i = 0; i < srcLog.size(); i++) {
            SVNLogEntry srcEntry = (SVNLogEntry) srcLog.get(i);
            SVNLogEntry dstEntry = (SVNLogEntry) dstLog.get(i);
            SVNDebugLog.logInfo("processing revision " + srcEntry.getRevision());
            if (!srcEntry.equals(dstEntry)) {
                SVNDebugLog.logInfo("log entries are not equal:");
                SVNDebugLog.logInfo("src:");
                SVNDebugLog.logInfo(srcEntry.toString());
                SVNDebugLog.logInfo("dst:");
                SVNDebugLog.logInfo(dstEntry.toString());
                SVNDebugLog.logInfo("");
                for (Iterator paths = srcEntry.getChangedPaths().values().iterator(); paths.hasNext();) {
                    SVNLogEntryPath path = (SVNLogEntryPath) paths.next();
                    SVNLogEntryPath srcPath = (SVNLogEntryPath) dstEntry.getChangedPaths().remove(path.getPath()); 
                    if (srcPath == null) {
                        SVNDebugLog.logInfo("target miss: " + path);
                    } else if (!srcPath.equals(path)) {
                        SVNDebugLog.logInfo("target path is not equal to source: " + srcPath);
                    }
                }
                if (!dstEntry.getChangedPaths().isEmpty()) {
                    SVNDebugLog.logInfo("there are more paths in target entry:");
                    SVNDebugLog.logInfo(dstEntry.toString());
                }
                return false;
            }
        }
        return true;
    }

}
