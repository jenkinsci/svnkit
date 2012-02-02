package org.tmatesoft.svn.core.internal.wc2.old;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc16.SVNCopyClient16;
import org.tmatesoft.svn.core.internal.wc16.SVNMoveClient16;
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec;
import org.tmatesoft.svn.core.wc.ISVNExternalsHandler;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnOldCopy extends SvnOldRunner<Void, SvnCopy> {

    @Override
    protected Void run() throws SVNException {
        if (getOperation().isVirtual()) {
            return virtualCopy();
        } else {
            return copy();
        }
    }

    private Void virtualCopy() throws SVNException {
        SVNMoveClient16 client = new SVNMoveClient16(getOperation().getRepositoryPool(), getOperation().getOptions());
        client.setEventHandler(getOperation().getEventHandler());
        client.setOptions(getOperation().getOptions());

        final SvnTarget target = getOperation().getFirstTarget();
        if (target.isURL()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                    "Cannot perform 'virtual' {0}: ''{1}'' is URL", new Object[] {
                    getOperation().isMove() ? "move" : "copy", target
            });
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        final SVNCopySource[] sources = getCopySources();
        for (SVNCopySource source : sources) {
            client.doVirtualCopy(source.getFile(), target.getFile(), getOperation().isMove());
        }

        return null;
    }

    private Void copy() throws SVNException {
        final SVNCopyClient16 client = new SVNCopyClient16(getOperation().getRepositoryPool(), getOperation().getOptions());
        client.setEventHandler(getOperation().getEventHandler());
        client.setCommitHandler(null);
        client.setExternalsHandler(ISVNExternalsHandler.DEFAULT);
        client.setOptions(getOperation().getOptions());

        final SvnTarget target = getOperation().getFirstTarget();
        final SVNCopySource[] sources = getCopySources();

        if (target.isURL()) {
            client.doCopy(sources, target.getURL(), getOperation().isMove(), getOperation().isMakeParents(), getOperation().isFailWhenDstExists(),
                    null, null);
        } else {
            client.doCopy(sources, target.getFile(), getOperation().isMove(), getOperation().isMakeParents(), getOperation().isFailWhenDstExists());
        }

        return null;
    }

    private SVNCopySource[] getCopySources() {
        SVNCopySource[] sources = new SVNCopySource[getOperation().getSources().size()];
        int i = 0;
        for (SvnCopySource newSource : getOperation().getSources()) {
            sources[i] = SvnCodec.copySource(newSource);
            i++;
        }
        return sources;
    }
}
