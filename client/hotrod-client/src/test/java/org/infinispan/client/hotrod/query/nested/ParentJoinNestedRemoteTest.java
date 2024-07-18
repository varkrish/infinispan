package org.infinispan.client.hotrod.query.nested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.infinispan.configuration.cache.IndexStorage.LOCAL_HEAP;

import java.util.List;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.test.SingleHotRodServerTest;
import org.infinispan.commons.api.query.Query;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.query.Search;
import org.infinispan.query.core.stats.QueryStatistics;
import org.infinispan.query.model.Player;
import org.infinispan.query.model.Team;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "org.infinispan.client.hotrod.query.nested.ParentJoinNestedRemoteTest")
public class ParentJoinNestedRemoteTest extends SingleHotRodServerTest {

   private QueryStatistics queryStatistics;

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      ConfigurationBuilder indexed = new ConfigurationBuilder();
      indexed.statistics().enable();
      indexed.indexing().enable()
            .storage(LOCAL_HEAP)
            .addIndexedEntity("model.Team");
      return TestCacheManagerFactory.createServerModeCacheManager(indexed);
   }

   @Override
   protected SerializationContextInitializer contextInitializer() {
      return Team.TeamSchema.INSTANCE;
   }

   @BeforeMethod
   public void beforeClass() {
      if (queryStatistics == null) {
         queryStatistics = Search.getSearchStatistics(cache).getQueryStatistics();
      }
      queryStatistics.clear();

      if (!cache.isEmpty()) {
         return;
      }
      RemoteCache<String, Team> remoteCache = remoteCacheManager.getCache();
      List<Player> playersA = List.of(new Player("Michael", "red", 7), new Player("Jonas", "blue", 3));
      List<Player> playersB = List.of(new Player("Ulrich", "red", 3), new Player("Martha", "blue", 7));
      remoteCache.put("1", new Team("New Team", playersA, playersA));
      remoteCache.put("2", new Team("Old Team", playersB, playersB));
   }

   @Test
   public void nested_usingJoin() {
      RemoteCache<String, Team> remoteCache = remoteCacheManager.getCache();
      Query<Object[]> query = remoteCache.query("select t.name from model.Team t " +
            "join t.firstTeam p where p.color ='red' AND p.number=7");
      List<Object[]> result = query.list();
      // the structure is nested, so the match searches for a player that has at the same time the color red and number 7
      assertThat(result).extracting(array -> array[0]).containsExactly("New Team");
      assertThat(queryStatistics.getLocalIndexedQueryCount()).isEqualTo(1);
   }

   @Test
   public void flattened_usingJoin() {
      RemoteCache<String, Team> remoteCache = remoteCacheManager.getCache();
      Query<Object[]> query = remoteCache.query("select t.name from model.Team t " +
            "join t.replacements p where p.color ='red' AND p.number=7");
      List<Object[]> result = query.list();
      // the structure is flattened, so the match searches a player that has the color red and possibly another player having number 7
      assertThat(result).extracting(array -> array[0]).containsExactly("New Team", "Old Team");
      assertThat(queryStatistics.getLocalIndexedQueryCount()).isEqualTo(1);
   }

   @Test
   public void nested_usingEquals() {
      RemoteCache<String, Team> remoteCache = remoteCacheManager.getCache();
      Query<Object[]> query = remoteCache.query("select t.name from model.Team t " +
            "where t.firstTeam.color ='red' AND t.firstTeam.number=7");
      List<Object[]> result = query.list();
      // we don't use the join operator, so the match searches a player that has the color red and possibly another player having number 7
      assertThat(result).extracting(array -> array[0]).containsExactly("New Team", "Old Team");
      assertThat(queryStatistics.getLocalIndexedQueryCount()).isEqualTo(1);
   }

   @Test
   public void flattened_usingEquals() {
      RemoteCache<String, Team> remoteCache = remoteCacheManager.getCache();
      Query<Object[]> query = remoteCache.query("select t.name from model.Team t " +
            "where t.replacements.color ='red' AND t.replacements.number=7");
      List<Object[]> result = query.list();
      // we don't use the join operator, so the match searches a player that has the color red and possibly another player having number 7
      assertThat(result).extracting(array -> array[0]).containsExactly("New Team", "Old Team");
      assertThat(queryStatistics.getLocalIndexedQueryCount()).isEqualTo(1);
   }

   @Test
   public void flattened_entityProj_usingEquals() {
      RemoteCache<String, Team> remoteCache = remoteCacheManager.getCache();
      Query<Team> query = remoteCache.query("from model.Team t " +
            "where t.replacements.color ='red' AND t.replacements.number=7");
      List<Team> result = query.list();
      // we don't use the join operator, so the match searches a player that has the color red and possibly another player having number 7
      assertThat(result).extracting(Team::name).containsExactly("New Team", "Old Team");
      assertThat(queryStatistics.getLocalIndexedQueryCount()).isEqualTo(1);
   }
}
