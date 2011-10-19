package org.tmatesoft.svn.core.internal.wc2.remote;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.IOExceptionWrapper;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc2.SvnRemoteOperationRunner;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.RepositoryInfo;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCat;
import org.tmatesoft.svn.core.wc2.SvnGetProperties;
import org.tmatesoft.svn.core.wc2.SvnSetLock;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnRemoteCat extends SvnRemoteOperationRunner<Long, SvnCat> {

    @Override
    protected Long run() throws SVNException {
    	SVNRevision revision = getOperation().getRevision() == null || !getOperation().getRevision().isValid() ? 
    			SVNRevision.HEAD : getOperation().getRevision();
    	
    	Structure<RepositoryInfo> repositoryInfo = 
                getRepositoryAccess().createRepositoryFor(
                        getOperation().getFirstTarget(), 
                        getOperation().getRevision(), 
                        getOperation().getPegRevision(), 
                        null);
            
        SVNRepository repos = repositoryInfo.<SVNRepository>get(RepositoryInfo.repository);
        repositoryInfo.release();
         
        getOperation().getEventHandler().checkCancelled();
        long revNumber = repositoryInfo.lng(RepositoryInfo.revision);
        getOperation().getEventHandler().checkCancelled();
        SVNNodeKind nodeKind = repos.checkPath("", revNumber);
        getOperation().getEventHandler().checkCancelled();
        if (nodeKind == SVNNodeKind.DIR) {
            SVNErrorMessage err = SVNErrorMessage.create(
            		SVNErrorCode.CLIENT_IS_DIRECTORY, "URL ''{0}'' refers to a directory", getOperation().getFirstTarget().getURL());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        getOperation().getEventHandler().checkCancelled();
        if (!getOperation().isExpandKeywords()) {
            repos.getFile("", revNumber, null, new SVNCancellableOutputStream(getOperation().getOutput(), this));
        } else {
            SVNProperties properties = new SVNProperties();
            repos.getFile("", revNumber, properties, null);
            getOperation().getEventHandler().checkCancelled();
            String mimeType = properties.getStringValue(SVNProperty.MIME_TYPE);
            String charset = SVNTranslator.getCharset(
            		properties.getStringValue(SVNProperty.CHARSET), 
            		mimeType, 
            		repos.getLocation().toDecodedString(), 
            		getOperation().getOptions());
            String keywords = properties.getStringValue(SVNProperty.KEYWORDS);
            String eol = properties.getStringValue(SVNProperty.EOL_STYLE);
            if (charset != null || keywords != null || eol != null) {
                String cmtRev = properties.getStringValue(SVNProperty.COMMITTED_REVISION);
                String cmtDate = properties.getStringValue(SVNProperty.COMMITTED_DATE);
                String author = properties.getStringValue(SVNProperty.LAST_AUTHOR);
                Map keywordsMap = SVNTranslator.computeKeywords(
                		keywords, 
                		getOperation().isExpandKeywords() ? repos.getLocation().toString() : null, 
                		author, 
                		cmtDate, 
                		cmtRev, 
                		getOperation().getOptions());
                OutputStream translatingStream = SVNTranslator.getTranslatingOutputStream(
                		getOperation().getOutput(), 
                		charset, 
                		SVNTranslator.getEOL(eol, getOperation().getOptions()), 
                		false, 
                		keywordsMap, 
                		getOperation().isExpandKeywords());
                repos.getFile("", revNumber, null, new SVNCancellableOutputStream(translatingStream, getOperation().getEventHandler()));
                try {
                    translatingStream.flush();
                } catch (IOExceptionWrapper ioew) {
                    throw ioew.getOriginalException();
                } catch (IOException e) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()), SVNLogType.WC);
                }
            } else {
                repos.getFile("", revNumber, null, new SVNCancellableOutputStream(getOperation().getOutput(), getOperation().getEventHandler()));
            }
        }
        try {
        	getOperation().getOutput().flush();
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()), SVNLogType.WC);
        }
        
        
        
        return Long.decode("1");
    }
    
    

}
