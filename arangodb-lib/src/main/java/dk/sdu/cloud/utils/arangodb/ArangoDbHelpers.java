package dk.sdu.cloud.utils.arangodb;

import com.arangodb.*;
import com.arangodb.entity.BaseDocument;
import com.arangodb.entity.CollectionEntity;
import com.arangodb.entity.DocumentEntity;
import com.arangodb.entity.EdgeDefinition;
import com.arangodb.util.MapBuilder;
import com.arangodb.velocypack.*;
import com.arangodb.velocypack.exception.VPackException;
import dk.sdu.cloud.jpa.sduclouddb.Dataobject;
import dk.sdu.cloud.jpa.utils.JpaHelpers;
import dk.sdu.cloud.models.arangodb.vertexs.DataObject;
import dk.sdu.cloud.models.arangodb.vertexs.DataObjectDirectory;
import dk.sdu.cloud.models.arangodb.vertexs.Person;
import dk.sdu.cloud.models.arangodb.vertexs.Project;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
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

        for (dk.sdu.cloud.jpa.sduclouddb.DataobjectDirectory jpaDataObjectDirectory :jpaHelpers.getJpaDataobjectDirectoryList())
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


        for (dk.sdu.cloud.jpa.sduclouddb.Person jpaPerson :jpaHelpers.getJpaPersonList())
        {
            Person person = new Person();
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




        for (dk.sdu.cloud.jpa.sduclouddb.Project jpaProject :jpaHelpers.getJpaProjectList())
        {
            Project project = new Project();
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



    public void setEdge(String edgeCollection)
    {

//        BaseEdgeDocument edge = new BaseEdgeDocument("myVertexCollection/myFromKey",
//                "myVertexCollection/myToKey");
//        edge.addAttribute("label", "value");
//        edge.addAttribute("whatever", 42);
//        arangoDB.db("myDatabase").graph("myGraph").edgeCollection("myEdgeCollection").insertEdge(edge);
    }

    //	(personEdgeToProject_with_role)
    public void setPersonEdgeToProject_with_role()
    {
        for (dk.sdu.cloud.jpa.sduclouddb.Person jpaPerson :jpaHelpers.getJpaPersonList())
        {
            for (dk.sdu.cloud.jpa.sduclouddb.ProjectPersonRelation jpaProjectPersonRelation :jpaPerson.getProjectPersonRelationList())
            {

            }
        }
    }

    public void getPersonByPersonId(int person_id)
    {
        long startTime = 0;
        long finishTime = 0;

        ArangoDB arangoDB = new ArangoDB.Builder().user("root").build();

        final String COLLECTION_NAME = "dataObjectCollection";

        try {
            final String query = "FOR t IN personCollection FILTER t.person_id == @person_id RETURN t";
            startTime = System.currentTimeMillis();
            final Map<String, Object> bindVars = new MapBuilder().put("person_id", person_id).get();

            System.err.println("Start: " + System.currentTimeMillis());
            final ArangoCursor<BaseDocument> cursor = arangoDB.db(DB_NAME).query(query, bindVars, null,
                    BaseDocument.class);
            for (; cursor.hasNext();) {
               // System.out.println("Key: " + cursor.next().getKey() + " ");
                System.err.println("key: " + cursor.next().getId());

                finishTime = System.currentTimeMillis();

            }
            System.err.println("duration: " + (finishTime-startTime));
        } catch (final ArangoDBException e) {
            System.err.println("Failed to execute query. " + e.getMessage());
        }




    }


    public void createEdgeCollections()
    {
        ArangoDB arangoDB = new ArangoDB.Builder().user("root").build();
        db = arangoDB.db(DB_NAME);
        Collection<EdgeDefinition> edgeDefinitions = new ArrayList<>();

        EdgeDefinition edgeDefinitionPersonEdgeToProject_with_role = new EdgeDefinition();
        // define the edgeCollection to store the edges
        edgeDefinitionPersonEdgeToProject_with_role.collection("personCollectionEdgeToProject_with_role");
        // define a set of collections where an edge is going out...
        edgeDefinitionPersonEdgeToProject_with_role.from("personCollection");

        // repeat this for the collections where an edge is going into
        edgeDefinitionPersonEdgeToProject_with_role.to("projectCollection");

        edgeDefinitions.add(edgeDefinitionPersonEdgeToProject_with_role);



        EdgeDefinition edgeDefinitionProjectCollectionEdgeToDataObjectDirectoryCollection_simple = new EdgeDefinition();
        // define the edgeCollection to store the edges
        edgeDefinitionProjectCollectionEdgeToDataObjectDirectoryCollection_simple.collection("projectCollectionEdgeToDataObjectDirectoryCollection_simple");
        // define a set of collections where an edge is going out...
        edgeDefinitionProjectCollectionEdgeToDataObjectDirectoryCollection_simple.from("projectCollection");

        // repeat this for the collections where an edge is going into
        edgeDefinitionProjectCollectionEdgeToDataObjectDirectoryCollection_simple.to("dataObjectDirectoryCollection");

        edgeDefinitions.add(edgeDefinitionProjectCollectionEdgeToDataObjectDirectoryCollection_simple);

        EdgeDefinition edgeDefinitionDataObjectDirectoryCollectionEdgeToDataobjectCollection_simple = new EdgeDefinition();
        // define the edgeCollection to store the edges
        edgeDefinitionDataObjectDirectoryCollectionEdgeToDataobjectCollection_simple.collection("dataObjectDirectoryCollectionEdgeToDataobjectCollection_simple");
        // define a set of collections where an edge is going out...
        edgeDefinitionDataObjectDirectoryCollectionEdgeToDataobjectCollection_simple.from("dataObjectDirectoryCollection");

        // repeat this for the collections where an edge is going into
        edgeDefinitionDataObjectDirectoryCollectionEdgeToDataobjectCollection_simple.to("dataobjectCollection");

        edgeDefinitions.add(edgeDefinitionDataObjectDirectoryCollectionEdgeToDataobjectCollection_simple);


        // now it's possible to create a graph

        arangoDB.db("sduclouddb").createGraph("sduclouddbGraph", edgeDefinitions);
        arangoDB.shutdown();
    }
}
