package com.clouway.cuse.spi;

import java.util.List;

/**
 * @author Ivan Lazov <ivan.lazov@clouway.com>
 */
class SearchEngineImpl implements SearchEngine {

  private final EntityLoader entityLoader;
  private final IndexingStrategyCatalog indexingStrategyCatalog;
  private final IdConverterCatalog idConverterCatalog;
  private final IndexRegister indexRegister;
  private final MatchedIdObjectFinder objectIdFinder;

  public SearchEngineImpl(EntityLoader entityLoader,
                          IdConverterCatalog idConverterCatalog,
                          IndexingStrategyCatalog indexingStrategyCatalog,
                          IndexRegister indexRegister,
                          MatchedIdObjectFinder objectIdFinder) {
    this.entityLoader = entityLoader;
    this.idConverterCatalog = idConverterCatalog;
    this.indexingStrategyCatalog = indexingStrategyCatalog;
    this.indexRegister = indexRegister;
    this.objectIdFinder = objectIdFinder;
  }

  public void register(Object instance) {

    Class instanceClass = instance.getClass();
    IndexingStrategy strategy = indexingStrategyCatalog.get(instanceClass);

    if (strategy == null) {
      throw new NotConfiguredIndexingStrategyException();
    }

    indexRegister.register(instance, strategy);

  }

  public <T> Search.SearchBuilder<T> search(Class<T> clazz) {
    return new Search.SearchBuilder<T>(clazz, entityLoader, indexingStrategyCatalog, objectIdFinder);
  }

  @Override
  public <T> Search.SearchBuilder<T> searchIds(Class<T> idClass) {

    if (idConverterCatalog.getConverter(idClass) == null) {
      throw new NotConfiguredIdConvertorException();
    }
    return new Search.SearchBuilder<T>(idClass, idClass, entityLoader, indexingStrategyCatalog, idConverterCatalog, objectIdFinder);
  }

  @Override
  public void delete(String indexName, List<Long> objectIds) {
    indexRegister.delete(indexName, objectIds);
  }

}
