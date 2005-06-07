package org.tmatesoft.svn.cli;

import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.DebugLog;

import java.io.PrintStream;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 07.06.2005
 * Time: 22:48:39
 * To change this template use File | Settings | File Templates.
 */
public class SVNCommandStatusHandler implements ISVNStatusHandler {

    private PrintStream myOut;
    private boolean myDetailed;
    private boolean myShowLastCommitted;
    private boolean mySkipUnrecognized;
    private boolean myShowReposLocks;


    public SVNCommandStatusHandler(PrintStream out, boolean detailed, boolean showLastCommitted, boolean skipUnrecognized, boolean showReposLocks) {
        myOut = out;
        myDetailed = detailed;
        myShowLastCommitted = showLastCommitted;
        mySkipUnrecognized = skipUnrecognized;
        myShowReposLocks = showReposLocks;
    }

    public void handleStatus(SVNStatus status) {
        if (status == null || (mySkipUnrecognized && status.getURL() == null) ||
            (status.getContentsStatus() == SVNStatusType.STATUS_NONE && status.getRemoteContentsStatus() == SVNStatusType.STATUS_NONE)) {
                return;
        }
        StringBuffer result = new StringBuffer();
        if (!myDetailed) {
            result.append(getStatusChar(status.getContentsStatus()));
            result.append(getStatusChar(status.getPropertiesStatus()));
            result.append(status.isLocked() ? 'L' : ' ');
            result.append(status.isCopied() ? '+' : ' ');
            result.append(status.isSwitched() ? 'S' : ' ');
            result.append(status.getLocalLock() != null ? 'K' : ' ');
            result.append(" ");
            result.append(SVNCommand.getPath(status.getFile()));
        } else {
            String wcRevision;
            char remoteStatus;
            if (status.getURL() == null) {
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
            if (myShowReposLocks) {
                lockStatus = ' ';
            } else {
                lockStatus = status.getLocalLock() != null ? 'K' : ' ';
            }
            if (myShowLastCommitted) {
                String commitRevision = "";
                String commitAuthor = "";

                if (status.getURL() != null && status.getCommittedRevision().isValid()) {
                    commitRevision = status.getCommittedRevision().toString();
                } else if (status.getURL() != null) {
                    commitRevision = " ? ";
                }
                if (status.getURL() != null && status.getAuthor() != null) {
                    commitAuthor = status.getAuthor();
                } else if (status.getURL() != null) {
                    commitAuthor = " ? ";
                }
                result.append(getStatusChar(status.getContentsStatus()));
                result.append(getStatusChar(status.getPropertiesStatus()));
                result.append(status.isLocked() ? 'L' : ' ');
                result.append(status.isCopied() ? '+' : ' ');
                result.append(status.isSwitched() ? 'S' : ' ');
                result.append(lockStatus);
                result.append(" ");
                result.append(remoteStatus);
                result.append("    ");
                result.append(formatString(wcRevision, 6, false)); // 6 chars
                result.append("    ");
                result.append(formatString(commitRevision, 6, false)); // 6 chars
                result.append("    ");
                result.append(formatString(commitAuthor, 12, true)); // 12 chars
                result.append(" ");
                result.append(SVNCommand.getPath(status.getFile()));
            }  else {
                result.append(getStatusChar(status.getContentsStatus()));
                result.append(getStatusChar(status.getPropertiesStatus()));
                result.append(status.isLocked() ? 'L' : ' ');
                result.append(status.isCopied() ? '+' : ' ');
                result.append(status.isSwitched() ? 'S' : ' ');
                result.append(lockStatus);
                result.append(" ");
                result.append(remoteStatus);
                result.append("    ");
                result.append(formatString(wcRevision, 6, false)); // 6 chars
                result.append("    ");
                result.append(SVNCommand.getPath(status.getFile()));
            }
        }
        DebugLog.log(result.toString());
        myOut.println(result.toString());

    }

    private static char getStatusChar(SVNStatusType type) {
        if (type == SVNStatusType.STATUS_NONE) {
            return ' ';
        } else if (type == SVNStatusType.STATUS_NORMAL) {
            return ' ';
        } else if (type == SVNStatusType.STATUS_ADDED) {
            return 'A';
        } else if (type == SVNStatusType.STATUS_MISSING) {
            return '!';
        } else if (type == SVNStatusType.STATUS_INCOMPLETE) {
            return '!';
        } else if (type == SVNStatusType.STATUS_DELETED) {
            return 'D';
        } else if (type == SVNStatusType.STATUS_REPLACED) {
            return 'R';
        } else if (type == SVNStatusType.STATUS_MODIFIED) {
            return 'M';
        } else if (type == SVNStatusType.STATUS_MERGED) {
            return 'G';
        } else if (type == SVNStatusType.STATUS_CONFLICTED) {
            return 'C';
        } else if (type == SVNStatusType.STATUS_OBSTRUCTED) {
            return '~';
        } else if (type == SVNStatusType.STATUS_IGNORED) {
            return 'I';
        } else if (type == SVNStatusType.STATUS_EXTERNAL) {
            return 'X';
        } else if (type == SVNStatusType.STATUS_UNVERSIONED) {
            return '?';
        }
        return '?';
    }

    private static String formatString(String str, int chars, boolean left) {
        if (str.length() > chars) {
            return str.substring(0, chars);
        }
        StringBuffer formatted = new StringBuffer();
        if (left) {
            formatted.append(str);
        }
        for(int i = 0; i < chars - str.length(); i++) {
            formatted.append(' ');
        }
        if (!left) {
            formatted.append(str);
        }
        return formatted.toString();
    }
}
