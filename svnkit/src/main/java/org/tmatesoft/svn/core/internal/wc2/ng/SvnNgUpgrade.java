package org.tmatesoft.svn.core.internal.wc2.ng;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc2.SvnUpgrade;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;

public class SvnNgUpgrade extends SvnNgOperationRunner<SvnWcGeneration, SvnUpgrade> {

    @Override
    protected SvnWcGeneration run(SVNWCContext context) throws SVNException {
        ISVNWCDb db = context.getDb();
        if (db != null && db instanceof SVNWCDb) {
            File localAbsPath = getFirstTarget();
            SVNWCDb.DirParsedInfo dirParsedInfo = ((SVNWCDb) db).parseDir(localAbsPath, SVNSqlJetDb.Mode.ReadOnly);
            int format = dirParsedInfo.wcDbDir.getWCRoot().getFormat();

            if (format < ISVNWCDb.WC_FORMAT_18) {
                SvnNgUpgradeSDb.upgrade(localAbsPath, (SVNWCDb) db, db.getSDb(dirParsedInfo.wcDbDir.getWCRoot().getAbsPath()), format);
            }
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OP_ON_CWD, "Can''t upgrade ''{0}'' as it is not a pre-1.7 working copy directory",
                    getOperation().getFirstTarget().getFile().getAbsolutePath());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return SvnWcGeneration.V17;
    }

}
