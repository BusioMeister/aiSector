package ai.aisector;

import com.mongodb.Block;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;



public class MongoDBManager {
    private MongoClient mongoClient;
    private MongoDatabase database;

    // Konstruktor - inicjalizacja połączenia
    public MongoDBManager(String uri, String databaseName) {
        mongoClient = MongoClients.create(uri);
        database = mongoClient.getDatabase(databaseName);
    }

    // Pobieranie kolekcji
    private MongoCollection<Document> getCollection(String collectionName) {
        return database.getCollection(collectionName);
    }

    // Metoda zapisu pojedynczego dokumentu
    public void insertOne(String collectionName, Document document) {
        MongoCollection<Document> collection = getCollection(collectionName);
        collection.insertOne(document);
        System.out.println("Dokument został dodany do kolekcji: " + collectionName);
    }

    // Metoda zapisu wielu dokumentów
    public void insertMany(String collectionName, List<Document> documents) {
        MongoCollection<Document> collection = getCollection(collectionName);
        collection.insertMany(documents);
        System.out.println("Dodano " + documents.size() + " dokumentów do kolekcji: " + collectionName);
    }

    // Metoda odczytu wszystkich dokumentów
    public List<Document> findAll(String collectionName) {
        MongoCollection<Document> collection = getCollection(collectionName);
        List<Document> documents = new ArrayList<>();

        // Używamy add() w jednoznaczny sposób
        collection.find().forEach((Block<? super Document>) document -> {
                    documents.add(document);
                }
        );
        return documents;
    }

    // Metoda do wyszukiwania jednego dokumentu
    public Document findOne(String collectionName, Bson filter) {
        MongoCollection<Document> collection = getCollection(collectionName);
        return collection.find(filter).first();
    }

    // Zamknięcie połączenia
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("Połączenie z MongoDB zostało zamknięte.");
        }
    }
}
