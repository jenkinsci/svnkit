import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.ClosedByInterruptException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tigris.subversion.javahl.ChangePath;
import org.tigris.subversion.javahl.CopySource;
import org.tigris.subversion.javahl.LogMessageCallback;
import org.tigris.subversion.javahl.Revision;
import org.tigris.subversion.javahl.SVNClient;
import org.tmatesoft.svn.cli.svn.SVNCommandEnvironment;
import org.tmatesoft.svn.cli.svn.SVNNotifyPrinter;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.io.svn.SVNWriter;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.jna.SVNJNAUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPasswordCipher;
import org.tmatesoft.svn.core.internal.wc.SVNWinCryptPasswordCipher;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.javahl.SVNClientImpl;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNBasicClient;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNUUIDAction;


/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class Tests {

    public static void main(String[] args) {
        try {
            DAVRepositoryFactory.setup();
            SVNRepositoryFactoryImpl.setup();
            FSRepositoryFactory.setup();

            SVNStatusClient statusClient;
            SVNWCClient wcClient;
            SVNDiffClient diffClient; 
            SVNClientManager clientManager = SVNClientManager.newInstance();    
            wcClient = SVNClientManager.newInstance(null).getWCClient();
            statusClient = SVNClientManager.newInstance(null).getStatusClient();
            
            String s = SVNPathUtil.append("/", "a/b");
            
            try {
                //SVNInfo info = wcClient.doInfo(new File("/home/alex/workspace/tmp/mergeTestWC/dumb"), SVNRevision.WORKING);
                SVNStatus stat = statusClient.doStatus(new File("/home/alex/workspace/tmp/mergeTestWC/dumb"), false); 
                wcClient.doAdd(new File("/home/alex/workspace/tmp/mergeTestWC/iota"), false, false, false, SVNDepth.FILES, false, false);
            } catch (SVNException svne) {
                System.out.println(svne.getErrorMessage().getFullMessage());
            }
            
            System.exit(0);
           
            diffClient = clientManager.getDiffClient();
           
            Map mergeInfo = diffClient.doGetMergedMergeInfo(SVNURL.parseURIEncoded("svn://localhost/BB"), SVNRevision.UNDEFINED);
            
            SVNURL testURL = SVNURL.parseURIEncoded("http://svn.svnkit.com");
            
            File fileToLock = new File("/home/alex/workspace/tmp/exWC/sdf");
            File[] filesToLock = new File[]{fileToLock};
            
            
            System.out.println("Done");
            System.exit(0);
            
            wcClient.doRevert(new File[] { new File("/home/alex/myWCLink") } , SVNDepth.INFINITY, null);
            
            System.exit(0);
            
            SVNUpdateClient updater = SVNClientManager.newInstance().getUpdateClient();
            
            LinkedList lis = new LinkedList();
            
            updater.doUpdate(new File("/home/alex/workspace/tmp/ex2"), SVNRevision.HEAD, SVNDepth.INFINITY, false, false);
            
            System.exit(0);
            
            BasicAuthenticationManager autMan = new BasicAuthenticationManager("nmueller", "h3bzq4la");
            updater.doCheckout(SVNURL.parseURIEncoded("https://svn.kwarc.info/repos/kwarc/projects/JEM/"), new File("/home/alex/workspace/tmp/JEM"), 
                    SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNDepth.EMPTY, false);
            
         // getting creation date using log.
            SVNLogClient logClient = SVNClientManager.newInstance().getLogClient();
            final SVNLogEntry[] last = new SVNLogEntry[1];
            last[0] = null;
//            SVNURL url = SVNURL.parseURIEncoded("http://localhost/repositories/exampleRepository/A/D/C4/C1");
            SVNURL url = SVNURL.parseURIEncoded("file:///home/alex/workspace/tmp/exampleRepository/AA");
            logClient.doLog(url, null, SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNRevision.create(0), false, 
                    false, 0, new ISVNLogEntryHandler() {
                public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                    last[0] = logEntry;
                }
            });

            if (last[0] != null) {
                System.out.println(url + " created at " + last[0].getDate() + " by " + last[0].getAuthor());
            }             
            
//            SVNUpdateClient client = SVNClientManager.newInstance(null).getUpdateClient();
//            client.doCheckout(SVNURL.parseURIEncoded("https://svn.salzburgresearch.at/svn/kiwi/IkeWiki/branches/SWiM/trunk"), 
//                    new File("/home/alex/workspace/tmp/swim"), SVNRevision.UNDEFINED, SVNRevision.HEAD, 
//                    SVNDepth.INFINITY, false);
//            client.doUpdate(new File("/home/alex/workspace/tmp/swim"), SVNRevision.HEAD, SVNDepth.INFINITY, false, false);
            
            SVNRepository reposus = SVNRepositoryFactory.create(SVNURL.parseURIEncoded("http://localhost/repositories/exampleRepository/A/D/C4/C1"));

            reposus.setAuthenticationManager(new BasicAuthenticationManager("jrandom", "rayjandom"));
            
            
            final long[] rev = {-1};
            
            logClient.doList(SVNURL.parseURIEncoded("http://localhost/repositories/exampleRepository/A/D/C4"), 
                    SVNRevision.HEAD, SVNRevision.HEAD, false, false, new ISVNDirEntryHandler() {

                        public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
                            if ("C1".equals(dirEntry.getName())) {
                                rev[0] = dirEntry.getRevision();
                            }
                            System.out.println("path " + dirEntry.getName());
                            System.out.println("revision: " + dirEntry.getRevision());
                        }
                
            });
            
            reposus.status(rev[0], null, true, new ISVNReporterBaton() {

                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, rev[0], SVNDepth.INFINITY, true);
                    reporter.finishReport();
                }
            
            }, new ISVNEditor() {

                public void abortEdit() throws SVNException {
                }

                public void absentDir(String path) throws SVNException {
                }

                public void absentFile(String path) throws SVNException {
                }

                public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
                }

                public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
                }

                public void closeDir() throws SVNException {
                }

                public SVNCommitInfo closeEdit() throws SVNException {
                    return null;
                }

                public void closeFile(String path, String textChecksum) throws SVNException {
                }

                public void deleteEntry(String path, long revision) throws SVNException {
                }

                public void openDir(String path, long revision) throws SVNException {
                }

                public void openFile(String path, long revision) throws SVNException {
                }

                public void openRoot(long revision) throws SVNException {
                }

                public void targetRevision(long revision) throws SVNException {
                }

                public void applyTextDelta(String path, String baseChecksum) throws SVNException {
                }

                public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
                    return null;
                }

                public void textDeltaEnd(String path) throws SVNException {
                }

                public void changeDirProperty(String name, String value) throws SVNException {
                }

                public void changeFileProperty(String path, String name, String value) throws SVNException {
                }

                public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
                }

                public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
                }
                
            });
            
            System.exit(0);
            
            statusClient = SVNClientManager.newInstance().getStatusClient();
            statusClient.doStatus(new File("/home/alex/workspace/tmp/exampleWC"), SVNRevision.UNDEFINED, 
                    SVNDepth.INFINITY, true, false, false, false, new ISVNStatusHandler() {

                        public void handleStatus(SVNStatus status) throws SVNException {
                            String path = status.getFile().getAbsolutePath();
                            printStatus(path, status, true, false, false, true);
                        }
                
            }, null);

            System.exit(0);

            logClient = SVNClientManager.newInstance().getLogClient();
            
            logClient.doLog(SVNURL.parseURIEncoded("file:///home/alex/workspace/tmp/r"), new String[] {}, 
                    SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNRevision.create(1), false, true, true, 0, null,
                    new ISVNLogEntryHandler() {

                        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                            System.out.println(logEntry);
                        }
                        
            });
            
            SVNRepository repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded("https://s" +
            		"vn.svnkit.com/repos/svnkit"));
            Calendar cal = Calendar.getInstance(Locale.GERMANY);
            Collection col = new LinkedList();
            cal.set(2008, 5, 1);
            long startRevision = repository.getDatedRevision(cal.getTime());
            cal.set(2008, 6, 24);
            long endRevision = repository.getDatedRevision(cal.getTime());
            
/*            repository.log(null, startRevision, endRevision, true, false, -1, true, null, new ISVNLogEntryHandler() {
                public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                    if ("sa".equals(logEntry.getAuthor())) {
                        System.out.println("Revision: " + logEntry.getRevision());
                        System.out.println("Date: " + SVNDate.formatDate(logEntry.getDate()));
                        System.out.println("Message: " + logEntry.getMessage());
                        Map<String, SVNLogEntryPath> changedPaths = logEntry.getChangedPaths();
                        System.out.println("Number of files changed: " + changedPaths.size());
                        System.out.println();

                        for (String changedPath : changedPaths.keySet()) {
                            SVNLogEntryPath logEntryPath = changedPaths.get(changedPath);
                            System.out.println(logEntryPath);
                        }
                    }
                }
            });
            
*/            
            System.exit(0);

            runMergeTest(SVNClientManager.newInstance(), SVNURL.parseURIEncoded("svn://localhost/mergeTestRepos"));
            
            
//          source directory:
            SVNURL srcURL =
            SVNURL.parseURIEncoded("http://localhost/repositories/repos/trunk/A/D/G/rho");
            // new URL of the source directory:
            SVNURL dstURL =
            SVNURL.parseURIEncoded("http://localhost/repositories/repos/trunk/A/rho");
            SVNCopySource copySource = new SVNCopySource(SVNRevision.HEAD, SVNRevision.HEAD, srcURL);
            SVNClientManager.newInstance().getCopyClient().doCopy(new SVNCopySource[] { copySource }, 
                    dstURL, true, false, true, "move", null); 
            
/*            SVNClient javaHL = new SVNClient();
            
            javaHL.logMessages("http://svn.collab.net/repos/svn/branches/1.5.x", Revision.HEAD, 
                    Revision.getInstance(0), Revision.HEAD, true, true, true, null, 0, 
                    new LogMessageCallback() {
               public void singleMessage(ChangePath[] changedPaths, long revision, String author, 
                       long timeMicros, String message, boolean hasChildren) {
                   System.out.println(revision + ": " + message);
               } 
            });
*/            
            SVNLogClient logger =  SVNClientManager.newInstance(null).getLogClient();
            final List entries = new LinkedList();
            logger.doList(SVNURL.parseURIEncoded("http://svn.collab.net/repos/svn/branches/1.5.x"), 
                    SVNRevision.HEAD, SVNRevision.HEAD, false, false, new ISVNDirEntryHandler() {
                public void handleDirEntry(SVNDirEntry entry) throws SVNException {
                    if (entry.getKind() == SVNNodeKind.FILE) {
                        System.out.println("path: " + entry.getRelativePath());
                    }
                }
            });
            

            String p1 = "/a/b";
            char ch = p1.charAt(p1.length());
            SVNURL urlka = SVNURL.parseURIEncoded("file:///path/to/root");
            SVNURL childURLka = urlka.appendPath("/trunk/xaxa", false);
            
            String str = SVNPathUtil.getCommonPathAncestor("/a/b/c", "/trunk/a/b/c");
            
            SVNCommitClient committer = SVNClientManager.newInstance(null).getCommitClient(); 
            committer.setEventHandler(new ISVNEventHandler() {
            	public void handleEvent(SVNEvent event, double progress)
            			throws SVNException {
            		System.out.println(event.getFile());
            	}
            	
            	public void checkCancelled() throws SVNCancelException {
            	}
            });
            committer.doCommit(new File[] { 
                    new File("/home/alex/workspace/tmp/wc/ext"),
                    }, 
                    false, "test", false, true);
            
            System.out.println("commit passed.");
            
            SVNCopyClient copier = SVNClientManager.newInstance(null).getCopyClient();
            SVNCopySource source = new SVNCopySource(SVNRevision.UNDEFINED, 
            		SVNRevision.WORKING, new File("/home/alex/workspace/tmp/wc/ff4"));
            copier.doCopy(new SVNCopySource[] { source }, new File("/home/alex/workspace/tmp/wc/dir/ff5"), false, false, false);
            
            LinkedList lst = new LinkedList();
            lst.add(SVNStatusType.UNCHANGED);
            lst.add(SVNStatusType.MERGED);
            int index = lst.indexOf(SVNStatusType.MERGED);
            
            TreeSet set = new TreeSet();
            set.add(new FooClass(1));
            set.add(new FooClass(2));
            set.add(new FooClass(3));
            Object[] arr = set.toArray();
            
            for (int i = 0; i < arr.length; i++) {
				FooClass foo = (FooClass) arr[i];
				foo.a += 10;
			}

            for (Iterator iterator = set.iterator(); iterator.hasNext();) {
				FooClass foo = (FooClass) iterator.next();
				System.out.println(foo.a);
			}
            
            File ff = new File("../b/alpha");
            System.out.println(ff.getAbsolutePath());
            System.out.println(SVNPathUtil.validateFilePath(ff.getAbsolutePath()));
            
            LinkedList list = new LinkedList();
            list.add(new Integer(5)); 
            list.add(new Integer(1));
            list.add(new Integer(4));
            
            InputReader inputer = new InputReader(System.in);
            Thread inputerThread = new Thread(inputer);
//            inputerThread.setDaemon(true);
            inputerThread.start();
            int i = 5;
            while (inputer.getReadInput() == null) {
                if (i-- <= 0) {
                    inputerThread.interrupt();
                    break;
                }
                Thread.sleep(1);
            }
            
            
            Object[] buffer = new Object[] { "get-location-segments", "/pp", null, 
                    new Long(1), new Long(2) };
            SVNWriter.write(SVNFileUtil.DUMMY_OUT, "(w(s(n)(n)(n)))", buffer);
            SVNRepository repo = SVNRepositoryFactory.create(SVNURL.parseURIEncoded("svn://localhost/"));
            repo.getLocationSegments("file", -1, 1, 0, null);
            
            Map locks = new HashMap();
            long latestRev = repo.getLatestRevision();
            locks.put("iota", new Long(latestRev));
            repo.lock(locks, "bubu", false, null);
            
            committer.doCommit(new File[] { 
                    new File("/home/alex/workspace/tmp/bug_testing/wc3/dir/svnkit"),
                    new File("/home/alex/workspace/tmp/bug_testing/wc3/dir/svnkit/main"),
                    new File("/home/alex/workspace/tmp/bug_testing/wc3/dir/svnkit/sequence")
                    }, 
                    false, "test", false, true);
            
            
            //wcClient.doSetProperty(new File(""), "something", "smth", false, SVNDepth.INFINITY, null);
            
            String[] pathsArray = new String[] {"/dirA", "/dirB", "/dirA/fileA", "/file"};
            Arrays.sort(pathsArray);
            
            int c = getLinesCount("1\n\n\n");
            SVNAdminClient admin = SVNClientManager.newInstance(null).getAdminClient();
            InputStream is = SVNFileUtil.openFileForReading(new File("I:/Workspace/org.tmatesoft.svnkit/svnkit-test/python/cmdline/mergetracking_data/basic-merge.dump"));
            admin.doLoad(new File("I:/Workspace/tmp/tr"), is);
            
            Class.forName("org.sqlite.JDBC");
            String fileName = "I:/Workspace/tmp/mergeinfo.db"; 

            Connection conn = DriverManager.getConnection("jdbc:sqlite:"+fileName);
            
            Pattern pat = Pattern.compile("(\\d{2})(:(\\d{2}))?-((\\d{2}))"); 
            Matcher matcher = pat.matcher("23-22");
            if (matcher.matches()) {
                System.out.println(matcher.groupCount());
                for ( i = 0; i <= matcher.groupCount(); i++) {
                    System.out.println(matcher.group(i));
                }
            }
            
            SVNRevision.parse("{T05:02:07.832000 +0070}");
            
            repo = SVNRepositoryFactory.create(SVNURL.parseURIEncoded("svn://localhost/testRepos"));
            repo.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager("tamerlan", new String("������".getBytes(), "Windows-1251")));
            repo.info("", -1);
            
            
            SVNAdminClient adminCli = SVNClientManager.newInstance(null).getAdminClient();
            InputStream dump = SVNFileUtil.openFileForReading(new File("I:/Workspace/tmp/testsvn.dump"));
            adminCli.doLoad(new File("I:/Workspace/tmp/testRepos"), dump);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }

    private static void runMergeTest(SVNClientManager clientManager, SVNURL repoURL) throws SVNException {
        clientManager.setEventHandler(new SVNNotifyPrinter(new SVNCommandEnvironment("jsvn", System.out, System.err, System.in), false, false, false));
        SVNCommitClient committer = clientManager.getCommitClient();
        committer.doImport(new File("/home/alex/workspace/tmp/import-me/trunk"), repoURL, "importing", null, 
                true, true, SVNDepth.INFINITY);
        SVNUpdateClient updater = clientManager.getUpdateClient();
        updater.doCheckout(repoURL, new File("/home/alex/workspace/tmp/mergeTestWC"), SVNRevision.UNDEFINED, 
                SVNRevision.HEAD, SVNDepth.INFINITY, false);
        
        
    }
    
    protected static int getLinesCount(String str) {
        if ("".equals(str)) {
            return 1;
        }
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '\r') {
                count++;
                if (i < str.length() - 1 && str.charAt(i + 1) == '\n') {
                    i++;
                }
            } else if (str.charAt(i) == '\n') {
                count++;
            }
        }
        if (count == 0) {
            count++;
        }
        return count;
    }

    private static void printStatus(String path, SVNStatus status, boolean detailed, boolean showLastCommitted, 
            boolean skipUnrecognized, boolean showReposLocks) {
        if (status == null || 
                (skipUnrecognized && status.getEntry() == null) || 
                (status.getContentsStatus() == SVNStatusType.STATUS_NONE && status.getRemoteContentsStatus() == SVNStatusType.STATUS_NONE)) {
            return;
        }
        StringBuffer result = new StringBuffer();
        if (detailed) {
            String wcRevision;
            char remoteStatus;
            if (status.getEntry() == null) {
                wcRevision = "";
            } else if (!status.getRevision().isValid()) {
                wcRevision = " ? ";
            } else if (status.isCopied()) {
                wcRevision = "-";
            } else {
                wcRevision = Long.toString(status.getRevision().getNumber());
            }
            if (status.getRemotePropertiesStatus() != SVNStatusType.STATUS_NONE || status.getRemoteContentsStatus() != SVNStatusType.STATUS_NONE) {
                remoteStatus = '*';
            } else {
                remoteStatus = ' ';
            }
            char lockStatus;
            if (showReposLocks) {
                if (status.getRemoteLock() != null) {
                    if (status.getLocalLock() != null) {
                        lockStatus = status.getLocalLock().getID().equals(status.getRemoteLock().getID()) ? 'K' : 'T';
                    } else {
                        lockStatus = 'O';
                    }
                } else if (status.getLocalLock() != null) {
                    lockStatus = 'B';
                } else {
                    lockStatus = ' ';
                }
            } else {
                lockStatus = status.getLocalLock() != null ? 'K' : ' ';
            }
            if (showLastCommitted) {
                String commitRevision = "";
                String commitAuthor = "";

                if (status.getEntry() != null && status.getCommittedRevision().isValid()) {
                    commitRevision = status.getCommittedRevision().toString();
                } else if (status.getEntry() != null) {
                    commitRevision = " ? ";
                }
                if (status.getEntry() != null && status.getAuthor() != null) {
                    commitAuthor = status.getAuthor();
                } else if (status.getEntry() != null) {
                    commitAuthor = " ? ";
                }
                result.append(status.getContentsStatus().getCode());
                result.append(status.getPropertiesStatus().getCode());
                result.append(status.isLocked() ? 'L' : ' ');
                result.append(status.isCopied() ? '+' : ' ');
                result.append(status.isSwitched() ? 'S' : ' ');
                result.append(lockStatus);
                result.append(" ");
                result.append(remoteStatus);
                result.append("   ");
                result.append(SVNFormatUtil.formatString(wcRevision, 6, false)); // 6 chars
                result.append("   ");
                result.append(SVNFormatUtil.formatString(commitRevision, 6, false)); // 6 chars
                result.append(" ");
                result.append(SVNFormatUtil.formatString(commitAuthor, 12, true)); // 12 chars
                result.append(" ");
                result.append(path);
            }  else {
                result.append(status.getContentsStatus().getCode());
                result.append(status.getPropertiesStatus().getCode());
                result.append(status.isLocked() ? 'L' : ' ');
                result.append(status.isCopied() ? '+' : ' ');
                result.append(status.isSwitched() ? 'S' : ' ');
                result.append(lockStatus);
                result.append(" ");
                result.append(remoteStatus);
                result.append("   ");
                result.append(SVNFormatUtil.formatString(wcRevision, 6, false)); // 6 chars
                result.append("   ");
                result.append(path);
            }
        } else {
            result.append(status.getContentsStatus().getCode());
            result.append(status.getPropertiesStatus().getCode());
            result.append(status.isLocked() ? 'L' : ' ');
            result.append(status.isCopied() ? '+' : ' ');
            result.append(status.isSwitched() ? 'S' : ' ');
            result.append(status.getLocalLock() != null ? 'K' : ' ');
            result.append(" ");
            result.append(path);
        }
        System.out.println(result);
    }
    
    private static class InputReader implements Runnable {
        private BufferedReader myReader;
        private StringBuffer myReadInput;
        private SVNErrorMessage myError;
        volatile boolean myIsCancelled;
        
        public InputReader(InputStream is) {
            myReader = new BufferedReader(new InputStreamReader(is));
        }
        
        public void run() {
            try {
                System.out.print("print something: ");
                boolean sawFirstHalfOfEOL = false;
                while (true) {
                    if (myIsCancelled) {
                        break;
                    }
                    if (!myReader.ready()) {
                        try {
                            System.out.println("nothing to read, sleep..");
                            Thread.currentThread().sleep(1);
                        } catch (InterruptedException e) {
                            //
                        }
                        continue;
                    }
                    int r = myReader.read();
                    if (r == -1) {
                        myError = SVNErrorMessage.create(SVNErrorCode.IO_ERROR);
                        break;
                    }
                    char ch = (char) (r & 0xFF);
                    
                    if (sawFirstHalfOfEOL && ch != '\n') {
                        myReader.reset();
                        break;
                    } 
                    
                    if (ch == '\n') {
                        break;
                    } else if (ch == '\r') {
                        myReader.mark(1);
                        sawFirstHalfOfEOL = true;
                        continue;
                    }
                    appendToResult(ch);
                }
            } catch (ClosedByInterruptException ie) {
                System.out.println("input reading was interrupted:");
                ie.printStackTrace();
            } catch (InterruptedIOException ie) {
                System.out.println("input reading was interrupted:");
                ie.printStackTrace();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            System.out.println("Your input was: " + myReadInput);
        }
        
        public String getReadInput() {
            return myReadInput != null ? myReadInput.toString() : null;
        }
        
        private void appendToResult(char ch) {
            if (myReadInput == null) {
                myReadInput = new StringBuffer();
            }
            myReadInput.append(ch);
        }
    }

    private static class FooClass implements Comparable {
    	private int a;
    	public FooClass(int a) {
    		this.a = a;
    	}
    	
    	public int compareTo(Object o) {
    		FooClass foo = (FooClass) o;
    		return this.a == foo.a ? 0 : this.a < foo.a ? -1 : 1;
    	}
    }
    
    private static class PromptEraserThread implements Runnable {
    	volatile boolean myIsErase;
    	
    	public PromptEraserThread() {
    		myIsErase = true;
    	}

		public void run() {
			while (myIsErase) {
				System.out.print('\b');
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			}
		}
    }

}
