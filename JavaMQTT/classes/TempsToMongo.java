package classes;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.bson.Document;
import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.util.JSON;

public class TempsToMongo implements MqttCallback {
    MqttClient mqttclient;
    static MongoClient mongoClient;
    static MongoDatabase db;
    static MongoCollection mongocol;
    static MongoCollection mongocol2;
    static String mongo_user = new String();
    static String mongo_password = new String();
    static String mongo_address = new String();
    static String cloud_server = new String();
    static String cloud_topic = new String();
    static String mongo_host = new String();
    static String mongo_replica = new String();
    static String mongo_database = new String();
    static String mongo_collection1 = new String();
    static String mongo_collection2 = new String();
    static String mongo_authentication = new String();
    static JTextArea documentLabel = new JTextArea();

    public static void main(String[] args) {
        readDocument();
        createWindow("Temps", documentLabel);
        new TempsToMongo().connecCloud();
        new TempsToMongo().connectMongo();
    }

    private static void createWindow(String name, JTextArea label) {
        JFrame frame = new JFrame(name);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JLabel textLabel = new JLabel("Data from broker: ", SwingConstants.CENTER);
        textLabel.setPreferredSize(new Dimension(600, 30));
        JScrollPane scroll = new JScrollPane(label, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setPreferredSize(new Dimension(600, 200));
        JButton b1 = new JButton("Stop the program");
        frame.getContentPane().add(textLabel, BorderLayout.PAGE_START);
        frame.getContentPane().add(scroll, BorderLayout.CENTER);
        frame.getContentPane().add(b1, BorderLayout.PAGE_END);
        frame.setLocationRelativeTo(null);
        frame.pack();
        frame.setVisible(true);
        b1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                System.exit(0);
            }
        });
    }

    public int getNextSequence(String sequenceName) {
        Document filter = new Document("_id", sequenceName);
        Document update = new Document("$inc", new Document("seq", 1));
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                .returnDocument(com.mongodb.client.model.ReturnDocument.AFTER);
        Document sequenceDocument = db.getCollection("counters").findOneAndUpdate(filter, update, options);

        if (sequenceDocument == null) {
            // Initialize the counter if it doesn't exist
            db.getCollection("counters").insertOne(new Document("_id", sequenceName).append("seq", 1));
            return 1;
        }

        return sequenceDocument.getInteger("seq");
    }

    public static void readDocument() {
        try {
            Properties p = new Properties();
            // p.load(new FileInputStream("./JavaMQTT/TempsToMongo.ini"));
            p.load(new FileInputStream("E:\\3ºAno\\2ºSemestre\\PISID\\PISID\\JavaMQTT\\TempsToMongo.ini"));

            mongo_address = p.getProperty("mongo_address");
            mongo_user = p.getProperty("mongo_user");
            mongo_password = p.getProperty("mongo_password");
            mongo_replica = p.getProperty("mongo_replica");
            cloud_server = p.getProperty("cloud_server");
            cloud_topic = p.getProperty("cloud_topic");
            mongo_host = p.getProperty("mongo_host");
            mongo_database = p.getProperty("mongo_database");
            mongo_authentication = p.getProperty("mongo_authentication");
            mongo_collection1 = p.getProperty("mongo_collection1");
            mongo_collection2 = p.getProperty("mongo_collection2");
        } catch (Exception e) {
            System.out.println("Error reading CloudToMongo.ini file " + e);
            JOptionPane.showMessageDialog(null, "The CloudToMongo.inifile wasn't found.", "CloudToMongo",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public void connecCloud() {
        int i;
        try {
            i = new Random().nextInt(100000);
            mqttclient = new MqttClient(cloud_server, "CloudToMongo_" + String.valueOf(i) + "_" + cloud_topic);
            mqttclient.connect();
            mqttclient.setCallback(this);
            mqttclient.subscribe(cloud_topic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void connectMongo() {
        String mongoURI = new String();
        mongoURI = "mongodb://";
        if (mongo_authentication.equals("true"))
            mongoURI = mongoURI + mongo_user + ":" + mongo_password + "@";
        mongoURI = mongoURI + mongo_address;
        if (!mongo_replica.equals("false"))
            if (mongo_authentication.equals("true"))
                mongoURI = mongoURI + "/?replicaSet=" + mongo_replica + "&authSource=admin";
            else
                mongoURI = mongoURI + "/?replicaSet=" + mongo_replica;
        else if (mongo_authentication.equals("true"))
            mongoURI = mongoURI + "/?authSource=admin";
        MongoClient mongoClient = new MongoClient(new MongoClientURI(mongoURI));
        db = mongoClient.getDatabase(mongo_database);

        mongocol2 = db.getCollection(mongo_collection2);
    }

    @Override
    public void messageArrived(String topic, MqttMessage c)
            throws Exception {
        try {
            DBObject document_json;
            document_json = (DBObject) JSON.parse(c.toString());
            Document doc = Document.parse(document_json.toString());
            int sensor = (int) (document_json.get("Sensor"));
            mongocol = db.getCollection("sensoresTemp" + Integer.toString(sensor));
            int nextID = getNextSequence("temps" + Integer.toString(sensor) + "ID");
            doc.append("id", nextID);
            
            mongocol.insertOne(doc);
            System.out.println(doc);
            documentLabel.append(c.toString() + "\n");
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

}