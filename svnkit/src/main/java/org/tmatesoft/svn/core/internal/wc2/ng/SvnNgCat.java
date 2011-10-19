package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslatorInputStream;
import org.tmatesoft.svn.core.internal.wc17.SVNStatusEditor17;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.PristineContentsInfo;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnCat;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgCat extends SvnNgOperationRunner<Long, SvnCat> {

    @Override
    protected Long run(SVNWCContext context) throws SVNException {
        
        SVNNodeKind kind = context.readKind(getFirstTarget(), false);
        if (kind == SVNNodeKind.UNKNOWN || kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", getFirstTarget().getAbsolutePath());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (kind != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_IS_DIRECTORY, "''{0}'' refers to a directory", getFirstTarget().getAbsolutePath());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SVNRevision revision = getOperation().getRevision();
        SVNProperties properties;
        InputStream source = null;
        boolean localModifications = false;
        
        try {
            if (revision != SVNRevision.WORKING) {
                PristineContentsInfo info = context.getPristineContents(getFirstTarget(), true, false);
                source = info.stream;
                properties = context.getPristineProps(getFirstTarget());
            } else {
                source = SVNFileUtil.openFileForReading(getFirstTarget());
                properties = context.getDb().readProperties(getFirstTarget());
                SvnStatus status = SVNStatusEditor17.internalStatus(context, getFirstTarget());
                localModifications = status.getTextStatus() != SVNStatusType.STATUS_NORMAL;
            }
            
            String eolStyle = properties.getStringValue(SVNProperty.EOL_STYLE);
            String keywords = properties.getStringValue(SVNProperty.KEYWORDS);
            boolean special = properties.getStringValue(SVNProperty.SPECIAL) != null;
            
            long lastModified = 0;
            if (localModifications && !special) {
                lastModified = getFirstTarget().lastModified();
            } else {
                Structure<NodeInfo> info = context.getDb().readInfo(getFirstTarget(), NodeInfo.recordedTime);
                lastModified = info.lng(NodeInfo.recordedSize) / 1000;
                info.release();
            }
            
            Map<?, ?> keywordsMap = null;
            if (keywords != null && getOperation().isExpandKeywords()) {
                Structure<NodeInfo> info = context.getDb().readInfo(getFirstTarget(), NodeInfo.changedRev, NodeInfo.changedAuthor);
                SVNURL url = context.getNodeUrl(getFirstTarget());
                
                String rev = Long.toString(info.lng(NodeInfo.changedRev));
                String author = info.get(NodeInfo.changedAuthor);
                info.release();
                
                if (localModifications) {
                    rev = rev + "M";
                    author = "(local)";
                }
                String date = SVNDate.formatDate(new Date(lastModified));
                keywordsMap = SVNTranslator.computeKeywords(keywords, url.toString(), author, date, rev, getOperation().getOptions());
            }
            
            if (keywordsMap != null || eolStyle != null) {
                source = new SVNTranslatorInputStream(source, SVNTranslator.getEOL(eolStyle, getOperation().getOptions()), 
                        false, keywordsMap, true);                
            }
            
            try {
                SVNTranslator.copy(source, getOperation().getOutput());
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            
        } finally {
            SVNFileUtil.closeFile(source);
        }        
        return null;
        
    }

}
