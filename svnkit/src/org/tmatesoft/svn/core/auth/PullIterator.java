package org.tmatesoft.svn.core.auth;

import java.util.Iterator;

/**
 * {@link Iterator} backed by a callback method.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class PullIterator<T> implements Iterator<T> {
    private T next;
    private boolean eof;

    /**
     * Fetches a next element. Returning null signals the end of the enumeration.
     */
    protected abstract T fetch();

    public boolean hasNext() {
        if (eof)    return false;
        if (next==null) {
            next = fetch();
            eof = (next==null);
        }
        return next!=null;
    }

    public T next() {
        hasNext();
        return next;
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}
