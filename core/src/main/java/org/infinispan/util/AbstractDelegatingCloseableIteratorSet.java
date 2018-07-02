package org.infinispan.util;

import org.infinispan.commons.util.AbstractDelegatingSet;
import org.infinispan.commons.util.CloseableIterator;
import org.infinispan.commons.util.CloseableIteratorSet;
import org.infinispan.commons.util.CloseableSpliterator;

public abstract class AbstractDelegatingCloseableIteratorSet<E> extends AbstractDelegatingSet<E>
        implements CloseableIteratorSet<E> {

   protected abstract CloseableIteratorSet<E> delegate();

   @Override
   public CloseableIterator<E> iterator() {
      return delegate().iterator();
   }

   @Override
   public CloseableSpliterator<E> spliterator() {
      return delegate().spliterator();
   }
}
