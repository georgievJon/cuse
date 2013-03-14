package com.clouway.searchengine;

import com.google.appengine.api.search.Consistency;
import com.google.appengine.api.search.IndexSpec;
import com.google.appengine.api.search.Query;
import com.google.appengine.api.search.QueryOptions;
import com.google.appengine.api.search.Results;
import com.google.appengine.api.search.ScoredDocument;
import com.google.appengine.api.search.SearchServiceFactory;
import com.google.appengine.repackaged.com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Ivan Lazov <ivan.lazov@clouway.com>
 */
public class Search<T> {

  public static final class SearchBuilder<T> {

    private Class<T> clazz;
    private Class<T> idClazz;

    private EntityLoader entityLoader;
    private IndexingStrategyCatalog indexingStrategyCatalog;
    private IdConvertorCatalog idConvertorCatalog;

    private final Map<String, SearchMatcher> filters = new HashMap<String, SearchMatcher>();
    private String query = "";
    private String index;

    public SearchBuilder(Class<T> clazz, EntityLoader entityLoader, IndexingStrategyCatalog indexingStrategyCatalog) {
      this.clazz = clazz;
      this.entityLoader = entityLoader;
      this.indexingStrategyCatalog = indexingStrategyCatalog;
    }

    public SearchBuilder(Class<T> clazz, Class<T> idClazz, EntityLoader entityLoader, IndexingStrategyCatalog indexingStrategyCatalog, IdConvertorCatalog idConvertorCatalog) {
      this.clazz = clazz;
      this.idClazz = idClazz;
      this.entityLoader = entityLoader;
      this.indexingStrategyCatalog = indexingStrategyCatalog;
      this.idConvertorCatalog = idConvertorCatalog;
    }

    public SearchBuilder<T> where(String field, SearchMatcher matcher) {
      filters.put(field, matcher);
      return this;
    }

    public SearchBuilder<T> where(SearchQuery query) {
      this.query = query.getValue();
      return this;
    }

    public SearchBuilder<T> inIndex(Class aClass) {
      this.index = aClass.getSimpleName();
      return this;
    }

    public Search<T> fetchMaximum(int limit) {

      Search<T> search = returnAll();
      search.limit = limit;

      return search;
    }

    public Search<T> returnAll() {

      Search<T> search = new Search<T>();

      search.clazz = clazz;
      search.idClazz = idClazz;

      search.entityLoader = entityLoader;
      search.indexingStrategyCatalog = indexingStrategyCatalog;
      search.idConvertorCatalog = idConvertorCatalog;

      search.filters = filters;
      search.query = query;
      search.index = index;

      return search;
    }
  }

  private Class<T> clazz;
  private Class<T> idClazz;

  private EntityLoader entityLoader;
  private IndexingStrategyCatalog indexingStrategyCatalog;
  private IdConvertorCatalog idConvertorCatalog;

  private Map<String, SearchMatcher> filters;
  private String query;
  private String index;
  private int limit;

  private Search() {
  }

  public List<T> now() {

    String queryFilter = buildQueryFilter();

    String searchQuery = query + queryFilter;

    if (Strings.isNullOrEmpty(searchQuery)) {
      throw new InvalidSearchException();
    }

    Results<ScoredDocument> results = SearchServiceFactory.getSearchService().getIndex(IndexSpec.newBuilder()
                                                                             .setName(buildIndexName())
                                                                             .setConsistency(Consistency.PER_DOCUMENT))
                                                                             .search(buildQuery(searchQuery));

    List<String> entityIds = new ArrayList<String>();
    for (ScoredDocument scoredDoc : results) {
      entityIds.add(scoredDoc.getId());
    }

    if (idClazz != null) {

      IdConvertor convertor = idConvertorCatalog.getConvertor(idClazz);

      if (convertor == null) {
        throw new NotConfiguredIdConvertorCatalogException();
      }

      return convertor.convert(entityIds);
    }

    return entityLoader.loadAll(clazz, entityIds);
  }

  private String buildIndexName() {

    String indexName;

    if (index != null && index.length() > 0) {
      indexName = index;
    } else {
      indexName = indexingStrategyCatalog.get(clazz).getIndexName();
    }

    return indexName;
  }

  private String buildQueryFilter() {

    StringBuilder queryFilter = new StringBuilder();

    for (String filter : filters.keySet()) {

      String filterValue = filters.get(filter).getValue().trim();

      if (Strings.isNullOrEmpty(filterValue)) {
        throw new EmptyMatcherException();
      }

      queryFilter.append(filter).append(":").append(filterValue).append(" ");
    }

    return queryFilter.toString();
  }

  private QueryOptions buildQueryOptions() {

    QueryOptions.Builder queryOptionsBuilder = QueryOptions.newBuilder().setReturningIdsOnly(true);

    if (limit > 1000) {
      throw new SearchLimitExceededException();
    }

    if (limit < 0) {
      throw new NegativeSearchLimitException();
    }

    if (limit > 0) {
      queryOptionsBuilder.setLimit(limit);
    }

    return queryOptionsBuilder.build();
  }

  private Query buildQuery(String searchQuery) {

    QueryOptions queryOptions = buildQueryOptions();

    return Query.newBuilder().setOptions(queryOptions).build(searchQuery);
  }
}
