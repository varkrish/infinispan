package org.infinispan.query.clustered.commandworkers;

import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.hibernate.search.backend.lucene.search.query.LuceneSearchResult;
import org.infinispan.query.clustered.NodeTopDocs;
import org.infinispan.query.clustered.QueryResponse;
import org.infinispan.query.dsl.embedded.impl.SearchQueryBuilder;
import org.infinispan.search.mapper.common.EntityReference;

/**
 * Returns the results of a node to create an eager distributed iterator.
 *
 * @author Israel Lacerra &lt;israeldl@gmail.com&gt;
 * @since 5.1
 */
final class CQCreateEagerQuery extends CQWorker {

   @Override
   CompletionStage<QueryResponse> perform(BitSet segments) {
      SearchQueryBuilder query = queryDefinition.getSearchQueryBuilder();
      setFilter(segments);

      CompletionStage<NodeTopDocs> nodeTopDocs = query.isEntityProjection() ? collectKeys(query) : collectProjections(query);

      return nodeTopDocs.thenApply(QueryResponse::new);
   }

   private LuceneSearchResult<?> fetchHits(SearchQueryBuilder query) {
      long start = queryStatistics.isEnabled() ? System.nanoTime() : 0;

      LuceneSearchResult<?> result = query.build().fetch(queryDefinition.getMaxResults());

      if (queryStatistics.isEnabled())
         queryStatistics.localIndexedQueryExecuted(queryDefinition.getQueryString(), System.nanoTime() - start);

      return result;
   }

   private LuceneSearchResult<EntityReference> fetchReferences(SearchQueryBuilder query) {
      long start = queryStatistics.isEnabled() ? System.nanoTime() : 0;

      LuceneSearchResult<EntityReference> result = query.entityReference().fetch(queryDefinition.getMaxResults());

      if (queryStatistics.isEnabled())
         queryStatistics.localIndexedQueryExecuted(queryDefinition.getQueryString(), System.nanoTime() - start);

      return result;
   }

   private CompletionStage<NodeTopDocs> collectKeys(SearchQueryBuilder query) {
      return blockingManager.supplyBlocking(() -> fetchReferences(query), "CQCreateEagerQuery#collectKeys")
            .thenApply(queryResult -> {
               long hitCount = queryResult.total().hitCount();

               Object[] keys = queryResult.hits().stream()
                     .map(EntityReference::key)
                     .toArray(Object[]::new);
               return new NodeTopDocs(cache.getRpcManager().getAddress(), queryResult.topDocs(), hitCount, keys, null);
            });
   }

   private CompletionStage<NodeTopDocs> collectProjections(SearchQueryBuilder query) {
      return blockingManager.supplyBlocking(() -> fetchHits(query), "CQCreateEagerQuery#collectProjections")
            .thenApply(queryResult -> {
               long hitCount = queryResult.total().hitCount();

               List<?> hits = queryResult.hits();
               Object[] projections = hits.toArray(new Object[0]);
               return new NodeTopDocs(cache.getRpcManager().getAddress(), queryResult.topDocs(), hitCount, null, projections);
            });
   }
}
