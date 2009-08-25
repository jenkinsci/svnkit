package org.tmatesoft.svn.core.auth;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;

import java.util.Iterator;
import java.util.Collections;
import java.util.NoSuchElementException;

/**
 * Authentication attempt.
 *
 * This includes {@link SVNAuthentication} plus a few callbacks that are invoked depending on the outcome of the
 * authentication.
 *
 * @author Kohsuke Kawaguchi
 */
public class SVNAuthAttempt {
    public final SVNAuthentication auth;

    public SVNAuthAttempt(SVNAuthentication auth) {
        this.auth = auth;
    }

    /**
     * Caleld if authentication succeeded
     *
     * @param kind           a credential kind ({@link ISVNAuthenticationManager#PASSWORD} or {@link ISVNAuthenticationManager#SSH} or {@link ISVNAuthenticationManager#USERNAME})
     * @param realm          a repository authentication realm
     * @throws SVNException
     */
    public void onAccepted(String kind, String realm) throws SVNException {}

    /**
     * Called if authentication failed. This provides a feedback to {@link ISVNAuthenticationProvider}.
     *
     * @param kind           a credential kind ({@link ISVNAuthenticationManager#PASSWORD} or {@link ISVNAuthenticationManager#SSH} or {@link ISVNAuthenticationManager#USERNAME})
     * @param realm          a repository authentication realm
     * @param errorMessage   the reason of the authentication failure
     * @throws SVNException
     */
    public void onRejected(String kind, String realm, SVNErrorMessage errorMessage) throws SVNException {}

    public static Iterator<SVNAuthAttempt> wrap(final Iterator<? extends SVNAuthentication> itr) {
        return new Iterator<SVNAuthAttempt>() {
            public boolean hasNext() {
                return itr.hasNext();
            }

            public SVNAuthAttempt next() {
                return new SVNAuthAttempt(itr.next());
            }

            public void remove() {
                itr.remove();
            }
        };
    }

    public static Iterable<SVNAuthAttempt> wrap(final Iterable<? extends SVNAuthentication> core) {
        return new Iterable<SVNAuthAttempt>() {
            public Iterator<SVNAuthAttempt> iterator() {
                return wrap(core.iterator());
            }
        };
    }

    /**
     * Produces {A,B,C,D,E,F} from {{A,B},{C},{},{D,E,F}}.
     */
    public static abstract class FlattenIterator<U,T> implements Iterator<U> {
        private final Iterator<? extends T> core;
        private Iterator<U> cur;

        protected FlattenIterator(Iterator<? extends T> core) {
            this.core = core;
            cur = Collections.<U>emptyList().iterator();
        }

        protected FlattenIterator(Iterable<? extends T> core) {
            this(core.iterator());
        }

        protected abstract Iterator<U> expand(T t);

        public boolean hasNext() {
            while(!cur.hasNext()) {
                if(!core.hasNext())
                    return false;
                cur = expand(core.next());
            }
            return true;
        }

        public U next() {
            if(!hasNext())  throw new NoSuchElementException();
            return cur.next();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
