package org.tmatesoft.svn.core.internal.wc2.old;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc16.SVNCopyClient16;
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec;
import org.tmatesoft.svn.core.wc.ISVNExternalsHandler;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnOldCopy extends SvnOldRunner<Long, SvnCopy> {

    @Override
    protected Long run() throws SVNException {
        SVNCopyClient16 client = new SVNCopyClient16(getOperation().getRepositoryPool(), getOperation().getOptions());
        client.setEventHandler(getOperation().getEventHandler());
        client.setCommitHandler(null);
        client.setExternalsHandler(ISVNExternalsHandler.DEFAULT);
        client.setOptions(getOperation().getOptions());
        
        SvnTarget target = getOperation().getFirstTarget();
        SVNCopySource[] sources = new SVNCopySource[getOperation().getSources().size()];
        int i = 0;
        for (SvnCopySource newSource : getOperation().getSources()) {
            sources[i] = SvnCodec.copySource(newSource);
            i++;
        }
        if (target.isURL()) {
            client.doCopy(sources, target.getURL(), getOperation().isMove(), getOperation().isMakeParents(), getOperation().isFailWhenDstExists(), 
                    null, null);
        } else {
            client.doCopy(sources, target.getFile(), getOperation().isMove(), getOperation().isMakeParents(), getOperation().isFailWhenDstExists());
        }
        
        return new Long(-1);
    }
}
