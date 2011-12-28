package org.tmatesoft.svn.core.javahl17;

import java.io.File;

import org.apache.subversion.javahl.callback.ClientNotifyCallback;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.util.SVNLogType;

public class JavaHLEventHandler implements ISVNEventHandler {

    private ClientNotifyCallback notifyCallback;
    private boolean cancelOperation;

    public JavaHLEventHandler() {
        this.cancelOperation = false;
    }

    public void setNotifyCallback(ClientNotifyCallback notifyCallback) {
        this.notifyCallback = notifyCallback;
    }

    public void cancelOperation() {
        cancelOperation = true;
    }

    public void handleEvent(SVNEvent event, double progress) {
        if (event.getAction() == SVNEventAction.UPGRADE) {
            return;
        }
        String path = null;
        if (event.getFile() != null) {
            path = event.getFile().getAbsolutePath();
            if (path != null) {
                path = path.replace(File.separatorChar, '/');
            }
        }
        if (path == null) {
            path = "";
        }
        if (notifyCallback != null) {
            notifyCallback.onNotify(SVNClientImpl.getClientNotifyInformation(event, path));
        }
    }

    public void checkCancelled() throws SVNCancelException {
        if (cancelOperation) {
            cancelOperation = false;
            SVNErrorManager.cancel("operation cancelled", SVNLogType.DEFAULT);
        }
    }
}
