package org.tmatesoft.svn.cli.svnlook;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;
import org.tmatesoft.svn.util.SVNLogType;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * @version 1.8
 * @author TMate Software Ltd.
 */
public class SVNLookFileSizeCommand extends SVNLookCommand {

    public SVNLookFileSizeCommand() {
        super("filesize", null);
    }

    @Override
    protected Collection createSupportedOptions() {
        List options = new LinkedList();
        options.add(SVNLookOption.REVISION);
        options.add(SVNLookOption.TRANSACTION);
        return options;
    }

    @Override
    public void run() throws SVNException {
        SVNLookCommandEnvironment environment = getSVNLookEnvironment();
        String path = environment.getFirstArgument();
        if (environment.getFirstArgument() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS,
                    "Missing repository path argument");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }

        long fileSize;

        SVNLookClient client = environment.getClientManager().getLookClient();
        if (environment.isRevision()) {
            fileSize = client.doGetFileSize(environment.getRepositoryFile(), path, getRevisionObject());
        } else {
            fileSize = client.doGetFileSize(environment.getRepositoryFile(), path, environment.getTransaction());
        }
        environment.getOut().println(fileSize);
    }
}
