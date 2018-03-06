package dk.sdu.cloud.utils.arangodb;

import com.arangodb.ArangoCollection;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.EdgeDefinition;
import com.arangodb.velocypack.*;
import com.arangodb.velocypack.exception.VPackException;
import dk.sdu.cloud.jpa.sduclouddb.Dataobject;
import dk.sdu.cloud.jpa.sduclouddb.Dataobjectcollection;
import dk.sdu.cloud.jpa.sduclouddb.Person;
import dk.sdu.cloud.jpa.sduclouddb.Project;
import dk.sdu.cloud.jpa.utils.JpaHelpers;
import dk.sdu.cloud.models.arangodb.DataObject;
import dk.sdu.cloud.models.arangodb.DataObjectDirectory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;



public class ArangoDbHelpers {

    protected static final String DB_NAME = "sduclouddb";
    protected static final String COLLECTION_NAME = "dataObjectCollection";

    protected static ArangoDB arangoDB;
    protected static ArangoDatabase db;
    protected static ArangoCollection collection;

    JpaHelpers jpaHelpers = new JpaHelpers();



    public String getUUID()

    {
        UUID uid = UUID.randomUUID();

        return uid.toString();
    }



    public void loadDataObjectCollection() throws ExecutionException, InterruptedException {
        ArangoDB arangoDB = new ArangoDB.Builder().user("root").build();

        String COLLECTION_NAME = "dataObjectCollection";


        //Create DataObjectDirectoryCollecton

        //arangoDB.createDatabase(DB_NAME).get();
        db = arangoDB.db(DB_NAME);

        if (db.collection(COLLECTION_NAME).exists())
        {
            db.collection(COLLECTION_NAME).drop();
        }


        db.createCollection(COLLECTION_NAME);
        collection = db.collection(COLLECTION_NAME);
        arangoDB.shutdown();
        List<Dataobject> dataobjectJpaList = jpaHelpers.getJpaDataobjectList();

        int i = 1;

        while (i<15001) {
            arangoDB = new ArangoDB.Builder().user("root").build();
            db = arangoDB.db(DB_NAME);
            collection = db.collection(COLLECTION_NAME);
            for (Dataobject dataobjectJpa : dataobjectJpaList) {
                DataObject dataObject = new DataObject();
                dataObject.setKey(dataobjectJpa.getId().replace("_10000", "")+"_"+i);
                dataObject.setCreated_datetime(LocalDateTime.now());
                dataObject.setModified_datetime(dataObject.getCreated_datetime());
                dataObject.setData_object_checksum(dataobjectJpa.getDataobjectchecksum());
                dataObject.setData_object_file_extension(".txt");
                dataObject.setData_object_classification("open_access");
                if (i>=10000)
                {
                    dataObject.setData_object_classification("sensitive");
                }
                dataObject.setData_object_name(dataobjectJpa.getDataobjectname());
                dataObject.setLast_accessed_datetime(dataObject.getCreated_datetime());
                dataObject.setData_object_size(dataobjectJpa.getDataobjectsize());
                collection.insertDocument(dataObject);
            }
            i++;
            System.err.println("progress: " + i );
            arangoDB.shutdown();
        }



    }


    public void loadDataObjectDirectoryCollection() throws ExecutionException, InterruptedException {
        ArangoDB arangoDB = new ArangoDB.Builder().user("root").build();

        String COLLECTION_NAME = "dataObjectDirectoryCollection";
        //Create DataObjectDirectoryCollecton

        //arangoDB.createDatabase(DB_NAME).get();
        db = arangoDB.db(DB_NAME);

        if (db.collection(COLLECTION_NAME).exists())
        {
            db.collection(COLLECTION_NAME).drop();
        }

        db.createCollection(COLLECTION_NAME);
        collection = db.collection(COLLECTION_NAME);


        System.err.println(arangoDB.getDatabases().toString());

        for (DataObjectDirectory jpaDataObjectDirectory :jpaHelpers.getJpaDataobjectcollectionList())
        {
            DataObjectDirectory dataObjectDirectory = new DataObjectDirectory();
            dataObjectDirectory.setKey(this.getUUID());
            dataObjectDirectory.setCreated_datetime(LocalDateTime.now());
            dataObjectDirectory.setModified_datetime(dataObjectDirectory.getCreated_datetime());
            dataObjectDirectory.setData_object_directory_url(dataObjectDirectory.getData_object_directory_url());
            collection.insertDocument(dataObjectDirectory);
        }

        arangoDB.shutdown();

    }


    public void loadPersonCollection() throws ExecutionException, InterruptedException {
        ArangoDB arangoDB = new ArangoDB.Builder().user("root").build();

        String COLLECTION_NAME = "personCollection";
        //Create DataObjectDirectoryCollecton

        //arangoDB.createDatabase(DB_NAME).get();
        db = arangoDB.db(DB_NAME);


        if (db.collection(COLLECTION_NAME).exists())
        {
            db.collection(COLLECTION_NAME).drop();
        }

        db.createCollection(COLLECTION_NAME);
        collection = db.collection(COLLECTION_NAME);


        for (Person jpaPerson :jpaHelpers.getJpaPersonList())
        {
            dk.sdu.cloud.models.arangodb.Person person = new dk.sdu.cloud.models.arangodb.Person();
            person.setKey(this.getUUID());
            person.setPerson_id(jpaPerson.getId());
            person.setCreated_datetime(LocalDateTime.now());
            person.setModified_datetime(person.getCreated_datetime());
            person.setPerson_title(jpaPerson.getPersontitle());
            person.setPerson_first_name(jpaPerson.getPersonfirstname());
            person.setPerson_middle_name(jpaPerson.getPersonmiddlename());
            person.setPerson_lastname(jpaPerson.getPersonlastname());
            person.setPerson_fullname(jpaPerson.getPersonFullname());
            person.setPerson_phoneno(jpaPerson.getPersonphoneno());
            person.setPerson_username(jpaPerson.getUsername());


            collection.insertDocument(person);
        }

        arangoDB.shutdown();

    }

    public void loadProjectCollection() throws ExecutionException, InterruptedException {
        ArangoDB arangoDB = new ArangoDB.Builder().user("root").build();

        String COLLECTION_NAME = "projectCollection";
        //Create DataObjectDirectoryCollecton

        //arangoDB.createDatabase(DB_NAME).get();
        db = arangoDB.db(DB_NAME);


        if (db.collection(COLLECTION_NAME).exists())
        {
            db.collection(COLLECTION_NAME).drop();
        }

        db.createCollection(COLLECTION_NAME);
        collection = db.collection(COLLECTION_NAME);




        for (Project jpaProject :jpaHelpers.getJpaProjectList())
        {
            dk.sdu.cloud.models.arangodb.Project project = new dk.sdu.cloud.models.arangodb.Project();
            project.setKey(this.getUUID());
            project.setProject_id(jpaProject.getId());
            project.setCreated_datetime(LocalDateTime.now());
            project.setModified_datetime(project.getCreated_datetime());
            project.setProject_name(jpaProject.getProjectname());
            project.setProject_shortname(jpaProject.getProjectshortname());
            project.setProject_type(jpaProject.getProjecttyperefid().getProjecttypeename());
            if (jpaProject.getProjectstart()!=null) {
                project.setProjectstart(LocalDateTime.ofInstant(jpaProject.getProjectstart().toInstant(), ZoneId.from(jpaProject.getProjectstart().toInstant().atZone(ZoneId.systemDefault()))));
            }
            if (jpaProject.getProjectend()!=null) {
                project.setProjectend(LocalDateTime.ofInstant(jpaProject.getProjectend().toInstant(), ZoneId.from(jpaProject.getProjectstart().toInstant().atZone(ZoneId.systemDefault()))));
            }
            project.setVisible(jpaProject.getVisible());

            collection.insertDocument(project);
        }

        arangoDB.shutdown();

    }

    public void getDataObjectByKey(String key)
    {

        ArangoDB arangoDB = new ArangoDB.Builder().user("root").registerModule(new VPackModule() {
            @Override
            public <C extends VPackSetupContext<C>> void setup(final C context) {
                context.registerDeserializer(DataObject.class, new VPackDeserializer<DataObject>() {
                    @Override
                    public DataObject deserialize(VPackSlice parent,VPackSlice vpack,
                                                VPackDeserializationContext context) throws VPackException {
                        DataObject obj = new DataObject();
                        obj.setData_object_name(vpack.get("data_object_name").getAsString());
                        return obj;
                    }
                });
                context.registerSerializer(DataObject.class, new VPackSerializer<DataObject>() {
                    @Override
                    public void serialize(VPackBuilder builder, String attribute, DataObject value,
                                          VPackSerializationContext context) throws VPackException {
                        builder.add(attribute, ValueType.OBJECT);
                        builder.add("data_object_name", value.getData_object_name());
                        builder.close();
                    }
                });
            }
        }).build();

        db = arangoDB.db(DB_NAME);
        collection = db.collection(COLLECTION_NAME);
        DataObject dataObject =  collection.getDocument(key,DataObject.class);
        System.err.println(dataObject.toString());
        arangoDB.shutdown();

    }

    public void basicSetupSduCloudGraph()
    {
        ArangoDB arangoDB = new ArangoDB.Builder().user("root").build();
        Collection<EdgeDefinition> edgeDefinitions = new ArrayList<>();
        EdgeDefinition edgeDefinition = new EdgeDefinition();
        // define the edgeCollection to store the edges
        edgeDefinition.collection("sduCloudEdgeCollection");
        // define a set of collections where an edge is going out...
        edgeDefinition.from("personCollection", "projectCollection","dataObjectDirectoryCollection");

        // repeat this for the collections where an edge is going into
        edgeDefinition.to("projectCollection","dataObjectDirectoryCollection","dataObjectCollection");

        edgeDefinitions.add(edgeDefinition);

        // A graph can contain additional vertex collections, defined in the set of orphan collections
//        GraphCreateOptions options = new GraphCreateOptions();
//        options.orphanCollections("myCollection4", "myCollection5");

        // now it's possible to create a graph
        arangoDB.db("sduclouddb").createGraph("sduclouddbGraph", edgeDefinitions);
        arangoDB.shutdown();
    }

    public void setEdge()
    {

    }
}
