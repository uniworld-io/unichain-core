package org.unichain.eventplugin.mongodb;

import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.unichain.eventplugin.mongodb.util.Converter;
import org.unichain.eventplugin.mongodb.util.Pager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class MongoTemplate {
  private MongoManager manager;
  private MongoCollection<Document> collection = null;

  public MongoTemplate(MongoManager manager) {
    this.manager = manager;
  }

  protected abstract String collectionName();

  protected abstract <T> Class<T> getReferencedClass();

  public void add(Document document) {
    MongoCollection<Document> mongoCollection = getCollection();
    mongoCollection.insertOne(document);
  }

  public void addEntity(String entity) {
    MongoCollection<Document> mongoCollection = getCollection();
    if (Objects.nonNull(mongoCollection)) {
      mongoCollection.insertOne(Converter.jsonStringToDocument(entity));
    }
  }

  public void upsertEntity(String indexKey, Object indexValue, String entity) {
    MongoCollection<Document> mongoCollection = getCollection();
    if (Objects.nonNull(mongoCollection)) {
      Bson filter = Filters.eq(indexKey, indexValue);
      mongoCollection.replaceOne(filter, Converter.jsonStringToDocument(entity),
          new ReplaceOptions().upsert(true));
    }
  }

  public void addEntityList(List<String> entities) {
    MongoCollection<Document> mongoCollection = getCollection();
    List<Document> documents = new ArrayList<Document>();
    if (entities != null && !entities.isEmpty()) {
      for (String entity : entities) {
        documents.add(Converter.jsonStringToDocument(Converter.objectToJsonString(entity)));
      }
    }
    mongoCollection.insertMany(documents);
  }

  public void addList(List<Document> documents) {
    MongoCollection<Document> mongoCollection = getCollection();
    mongoCollection.insertMany(documents);
  }

  public long update(String updateColumn, Object updateValue, String whereColumn,
      Object whereValue) {
    MongoCollection<Document> mongoCollection = getCollection();
    UpdateResult result = mongoCollection.updateMany(Filters.eq(whereColumn, whereValue),
        new Document("$set", new Document(updateColumn, updateValue)));
    return result.getModifiedCount();
  }

  public UpdateResult updateMany(Bson filter, Bson update) {
    MongoCollection<Document> mongoCollection = getCollection();
    UpdateResult result = mongoCollection.updateMany(filter, update);
    return result;
  }

  public long delete(String whereColumn, String whereValue) {
    MongoCollection<Document> mongoCollection = getCollection();
    DeleteResult result = mongoCollection.deleteOne(Filters.eq(whereColumn, whereValue));
    return result.getDeletedCount();
  }

  public DeleteResult deleteMany(Bson filter) {
    MongoCollection<Document> mongoCollection = getCollection();
    return mongoCollection.deleteMany(filter);
  }

  /**
   * replace the new document
   */
  public void replace(Bson filter, Document replacement) {
    MongoCollection<Document> mongoCollection = getCollection();
    mongoCollection.replaceOne(filter, replacement);
  }

  public List<Document> queryByCondition(Bson filter) {
    MongoCollection<Document> mongoCollection = getCollection();
    List<Document> documents = new ArrayList<Document>();
    FindIterable<Document> iterables = mongoCollection.find(filter);
    MongoCursor<Document> mongoCursor = iterables.iterator();
    while (mongoCursor.hasNext()) {
      documents.add(mongoCursor.next());
    }
    return documents;
  }

  public Document queryOne(String key, String value) {
    Bson filter = Filters.eq(key, value);
    return this.getCollection().find(filter).first();
  }

  public Document queryOneEntity(String key, String value) {
    Bson filter = Filters.eq(key, value);
    Document document = this.getCollection().find(filter).first();
    String jsonString = document.toJson();
    return Converter.jsonStringToObject(jsonString, this.getReferencedClass());
  }

  public List<Document> queryAll() {
    MongoCollection<Document> mongoCollection = getCollection();
    FindIterable<Document> findIterable = mongoCollection.find();
    MongoCursor<Document> mongoCursor = findIterable.iterator();
    List<Document> documents = new ArrayList<Document>();
    while (mongoCursor.hasNext()) {
      documents.add(mongoCursor.next());
    }
    return documents;
  }

  public <T> List<T> queryAllEntity() {
    MongoCollection<Document> mongoCollection = getCollection();
    FindIterable<Document> findIterable = mongoCollection.find();
    List<T> list = getEntityList(findIterable);
    return list;
  }

  public <T> Pager<T> queryPagerList(Bson filter, int pageIndex, int pageSize) {
    MongoCollection<Document> mongoCollection = getCollection();
    long totalCount = mongoCollection.countDocuments(filter);
    FindIterable<Document> findIterable =
        mongoCollection.find().skip((pageIndex - 1) * pageSize).sort(new BasicDBObject())
            .limit(pageSize);
    List<T> resultList = getEntityList(findIterable);
    Pager<T> pager = new Pager<T>(resultList, totalCount, pageIndex, pageSize);
    return pager;
  }

  public MongoManager getManager() {
    return manager;
  }

  public void setManager(MongoManager manager) {
    this.manager = manager;
  }

  private <T> List<T> getEntityList(FindIterable<Document> findIterable) {
    MongoCursor<Document> mongoCursor = findIterable.iterator();
    List<T> list = new ArrayList<T>();
    Document document = null;
    while (mongoCursor.hasNext()) {
      document = mongoCursor.next();
      T object;
      try {
        object = Converter.jsonStringToObject(document.toJson(), getReferencedClass());
        list.add(object);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return list;
  }

  private MongoCollection<Document> getCollection() {
    if (Objects.isNull(manager) || Objects.isNull(manager.getDb())) {
      return null;
    }

    if (Objects.isNull(collection)) {
      collection = manager.getDb().getCollection(collectionName());
    }

    return collection;
  }

}
