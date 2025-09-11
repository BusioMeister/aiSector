package ai.aisector.database;

import com.mongodb.Block;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

public class MongoDBManager {
    private MongoClient mongoClient;
    private MongoDatabase database;

    public MongoDBManager(String uri, String databaseName) {
        mongoClient = MongoClients.create(uri);
        database = mongoClient.getDatabase(databaseName);
    }

    // 🔥 POPRAWKA: Zmiana z 'private' na 'public' 🔥
    public MongoCollection<Document> getCollection(String collectionName) {
        return database.getCollection(collectionName);
    }

    public void insertOne(String collectionName, Document document) {
        MongoCollection<Document> collection = getCollection(collectionName);
        collection.insertOne(document);
        Bukkit.getLogger().info("Dokument został dodany do kolekcji: " + collectionName);
    }

    public void insertMany(String collectionName, List<Document> documents) {
        MongoCollection<Document> collection = getCollection(collectionName);
        collection.insertMany(documents);
        Bukkit.getLogger().info("Dodano " + documents.size() + " dokumentów do kolekcji: " + collectionName);
    }

    public List<Document> findAll(String collectionName) {
        MongoCollection<Document> collection = getCollection(collectionName);
        List<Document> documents = new ArrayList<>();
        collection.find().forEach((Block<? super Document>) documents::add);
        return documents;
    }

    public Document findOne(String collectionName, Bson filter) {
        MongoCollection<Document> collection = getCollection(collectionName);
        return collection.find(filter).first();
    }

    // 🔥 DODATEK: Dodałem brakującą metodę do aktualizacji dokumentów, będzie potrzebna 🔥
    public void updateOne(String collectionName, Bson filter, Bson update) {
        MongoCollection<Document> collection = getCollection(collectionName);
        collection.updateOne(filter, update);
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            Bukkit.getLogger().info("Połączenie z MongoDB zostało zamknięte.");
        }
    }
}