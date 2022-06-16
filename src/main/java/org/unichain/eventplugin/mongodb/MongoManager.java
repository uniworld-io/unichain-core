package org.unichain.eventplugin.mongodb;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class MongoManager {
  private MongoClient mongo;
  private MongoDatabase db;

  public void initConfig(MongoConfig config) {
    int connectionsPerHost = config.getConnectionsPerHost();
    int threadsAllowedToBlockForConnectionMultiplier =
        config.getThreadsAllowedToBlockForConnectionMultiplier();
    MongoClientOptions options = MongoClientOptions.builder().connectionsPerHost(connectionsPerHost)
        .threadsAllowedToBlockForConnectionMultiplier(threadsAllowedToBlockForConnectionMultiplier)
        .build();

    String host = config.getHost();
    int port = config.getPort();
    ServerAddress serverAddress = new ServerAddress(host, port);
    List<ServerAddress> addrs = new ArrayList<ServerAddress>();
    addrs.add(serverAddress);

    String username = config.getUsername();
    String password = config.getPassword();
    String databaseName = config.getDbName();

    if (StringUtils.isEmpty(databaseName)) {
      return;
    }

    MongoCredential credential = MongoCredential.createScramSha1Credential(username, databaseName, password.toCharArray());
    List<MongoCredential> credentials = new ArrayList<MongoCredential>();
    credentials.add(credential);

    mongo = new MongoClient(addrs, credential, options);
    db = mongo.getDatabase(databaseName);
  }

  public void createCollection(String collectionName) {
    if (db != null && !StringUtils.isEmpty(collectionName)) {
      if (Objects.isNull(db.getCollection(collectionName))){
        db.createCollection(collectionName);
      }
    }
  }

  public void createCollection(String collectionName, Map<String, Boolean> indexOptions) {
    logger.info("[createCollection] collection={} start", collectionName);

    if (db != null && !StringUtils.isEmpty(collectionName)) {
      List<String> collectionList = new ArrayList<>();
      db.listCollectionNames().into(collectionList);

      if (!collectionList.contains(collectionName)) {
        db.createCollection(collectionName);

        // create index
        if (indexOptions == null) {
          return;
        }
        for (String col : indexOptions.keySet()) {
          logger.info("create index, col={}", col);
          db.getCollection(collectionName).createIndex(Indexes.ascending(col),
              new IndexOptions().name(col).unique(indexOptions.get(col)));
        }
      } else {
        logger.info("[createCollection] collection={} already exists", collectionName);
      }
    }
  }

  public MongoClient getMongo() {
    return mongo;
  }

  public void setMongo(MongoClient mongo) {
    this.mongo = mongo;
  }

  public MongoDatabase getDb() {
    return db;
  }

  public void setDb(MongoDatabase db) {
    this.db = db;
  }

}
