package org.tmatesoft.svn.core.internal.io.svn;

import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author Marc Strapetz
 */
public interface ISVNConnectorFactory {

	public static final ISVNConnectorFactory DEFAULT = new ISVNConnectorFactory() {
		public ISVNConnector createConnector(SVNRepository repository) {
            ISVNConnector connector = null;
			if ("svn+ssh".equals(repository.getLocation().getProtocol())) {
				connector = new SVNJSchConnector();
			} else {
			    connector = new SVNPlainConnector();
            }
            return DebugLog.isSVNLoggingEnabled() ? new SVNLoggingConnector(connector) : connector;
		}
	};

	public ISVNConnector createConnector(SVNRepository repository);

}