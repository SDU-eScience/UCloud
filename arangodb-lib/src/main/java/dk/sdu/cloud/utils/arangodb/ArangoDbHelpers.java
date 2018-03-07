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
import dk.sdu.cloud.jpa.sduclouddb.DataobjectDirectoryProjectrolePermissionset;
import dk.sdu.cloud.jpa.utils.JpaHelpers;
import dk.sdu.cloud.models.arangodb.edges.PersonCollectionEdgeToProject_with_role;
import dk.sdu.cloud.models.arangodb.edges.ProjectCollectionEdgeToDataObjectDirectoryCollection_simple;
import dk.sdu.cloud.models.arangodb.vertexs.*;

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



    public void loadDataobjectDirectoryProjectrolePermissionsetCollection() throws ExecutionException, InterruptedException {
        ArangoDB arangoDB = new ArangoDB.Builder().user("root").build();

        String COLLECTION_NAME = "dataobjectDirectoryProjectrolePermissionsetCollection";


        //Create DataObjectDirectoryCollecton

        //arangoDB.createDatabase(DB_NAME).get();
        db = arangoDB.db(DB_NAME);

        if (db.collection(COLLECTION_NAME).exists())
        {
            db.collection(COLLECTION_NAME).drop();
        }


        db.createCollection(COLLECTION_NAME);
        collection = db.collection(COLLECTION_NAME);

        if (db.collection(COLLECTION_NAME).exists())
        {
            db.collection(COLLECTION_NAME).drop();
        }

        db.createCollection(COLLECTION_NAME);
        collection = db.collection(COLLECTION_NAME);


        System.err.println(arangoDB.getDatabases().toString());

        for (dk.sdu.cloud.jpa.sduclouddb.DataobjectDirectoryProjectrolePermissionset jpaDataobjectDirectoryProjectrolePermissionset :jpaHelpers.getJpaDataobjectDirectoryProjectrolePermissionsetList())
        {
//            DataobjectDirectoryProjectrolePermissionset dataobjectDirectoryProjectrolePermissionset = new DataobjectDirectoryProjectrolePermissionset();
//
//            dataobjectDirectoryProjectrolePermissionset.setActive(1);
//            dataobjectDirectoryProjectrolePermissionset.setAdministerDataobject();
//            dataobjectDirectoryProjectrolePermissionset.setCreatedataobjectMetadata();
//            dataobjectDirectoryProjectrolePermissionset.setDeleteDataobject();
//            dataobjectDirectoryProjectrolePermissionset.setDownloadDataobject();
//            dataobjectDirectoryProjectrolePermissionset.setEnheritedFromParent(1);
//            dataobjectDirectoryProjectrolePermissionset.setMarkedfordelete(0);
//            dataobjectDirectoryProjectrolePermissionset.setModifyDataobject();
//            dataobjectDirectoryProjectrolePermissionset.setModifyDataobjectMetadata();
//            dataobjectDirectoryProjectrolePermissionset.setReadDataobject();
//            dataobjectDirectoryProjectrolePermissionset.setReadDataobjectMetadata();
//            dataobjectDirectoryProjectrolePermissionset.setReadDataobjectSystemMetadata();
//            collection.insertDocument(dataobjectDirectoryProjectrolePermissionset);
        }

        arangoDB.shutdown();
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
            dataObjectDirectory.setData_object_directory_id(jpaDataObjectDirectory.getId());
            dataObjectDirectory.setCreated_datetime(LocalDateTime.now());
            dataObjectDirectory.setModified_datetime(dataObjectDirectory.getCreated_datetime());
            dataObjectDirectory.setData_object_directory_url(dataObjectDirectory.getData_object_directory_url());
            collection.insertDocument(dataObjectDirectory);
        }

        arangoDB.shutdown();

    }

    public void loadProjectRoleCollection() throws ExecutionException, InterruptedException {
        ArangoDB arangoDB = new ArangoDB.Builder().user("root").build();

        String COLLECTION_NAME = "dataProjectRoleCollection";
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

        for (dk.sdu.cloud.jpa.sduclouddb.ProjectRole jpaProjectRole :jpaHelpers.getJpaProjectRoleList())
        {
            ProjectRole projectRole = new ProjectRole();
            projectRole.setKey(this.getUUID());
            projectRole.setProject_role_id(jpaProjectRole.getId());
            projectRole.setCreated_datetime(LocalDateTime.now());
            projectRole.setModified_datetime(projectRole.getCreated_datetime());

            collection.insertDocument(projectRole);
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

    public String getPersonByPersonId(int person_id)
    {
        long startTime = 0;
        long finishTime = 0;

        String idString = "";

        ArangoDB arangoDB = new ArangoDB.Builder().user("root").build();


        try {
            final String query = "FOR t IN personCollection FILTER t.person_id == @person_id RETURN t";
            startTime = System.currentTimeMillis();
            final Map<String, Object> bindVars = new MapBuilder().put("person_id", person_id).get();

            System.err.println("Start: " + System.currentTimeMillis());
            final ArangoCursor<BaseDocument> cursor = arangoDB.db(DB_NAME).query(query, bindVars, null,
                    BaseDocument.class);
            for (; cursor.hasNext();) {
                idString =  cursor.next().getId();


            }
            System.err.println("duration: " + (finishTime-startTime));
        } catch (final ArangoDBException e) {
            System.err.println("Failed to execute query. " + e.getMessage());
        }
        arangoDB.shutdown();
        return idString;


    }

    public String getProjectByProjectId(int project_id)
    {

        String idString = "";

        ArangoDB arangoDB = new ArangoDB.Builder().user("root").build();


        try {
            final String query = "FOR t IN projectCollection FILTER t.project_id == @project_id RETURN t";

            final Map<String, Object> bindVars = new MapBuilder().put("project_id", project_id).get();

            System.err.println("Start: " + System.currentTimeMillis());
            final ArangoCursor<BaseDocument> cursor = arangoDB.db(DB_NAME).query(query, bindVars, null,
                    BaseDocument.class);
            for (; cursor.hasNext();) {
                idString =  cursor.next().getId();


            }

        } catch (final ArangoDBException e) {
            System.err.println("Failed to execute query. " + e.getMessage());
        }
        arangoDB.shutdown();
        return idString;


    }


    public String getDataObjectDirectoryByDataObjectDirectoryId(int data_object_directory_id)
    {

        String idString = "";

        ArangoDB arangoDB = new ArangoDB.Builder().user("root").build();


        try {
            final String query = "FOR t IN dataObjectDirectoryCollection FILTER t.data_object_directory_id == @data_object_directory_id RETURN t";

            final Map<String, Object> bindVars = new MapBuilder().put("data_object_directory_id", data_object_directory_id).get();


            final ArangoCursor<BaseDocument> cursor = arangoDB.db(DB_NAME).query(query, bindVars, null,
                    BaseDocument.class);
            for (; cursor.hasNext();) {
                idString =  cursor.next().getId();


            }

        } catch (final ArangoDBException e) {
            System.err.println("Failed to execute query. " + e.getMessage());
        }
        arangoDB.shutdown();
        return idString;


    }





    //	(personEdgeToProject_with_role)
    public void setPersonEdgeToProject_with_role()
    {
        for (dk.sdu.cloud.jpa.sduclouddb.Person jpaPerson :jpaHelpers.getJpaPersonList())
        {

            PersonCollectionEdgeToProject_with_role personCollectionEdgeToProject_with_role = new PersonCollectionEdgeToProject_with_role();

            personCollectionEdgeToProject_with_role.setFrom(this.getPersonByPersonId(jpaPerson.getId()));


            for (dk.sdu.cloud.jpa.sduclouddb.ProjectPersonRelation jpaProjectPersonRelation :jpaPerson.getProjectPersonRelationList())
            {
                ArangoDB arangoDB = new ArangoDB.Builder().user("root").build();

                personCollectionEdgeToProject_with_role.setTo(getProjectByProjectId(jpaProjectPersonRelation.getProjectrefid().getId()));
                personCollectionEdgeToProject_with_role.setProjectRoleText(jpaProjectPersonRelation.getProjectrolerefid().getProjectrolename());
                System.err.println(personCollectionEdgeToProject_with_role.toString());
                arangoDB.db("sduclouddb").graph("sduclouddbGraph").edgeCollection("personCollectionEdgeToProject_with_role").insertEdge(personCollectionEdgeToProject_with_role);
                arangoDB.shutdown();
            }
        }
    }


    //(DataObjectDirectoryCollectionEdgeToDataobjectCollection_simple)

    public void setDataObjectDirectoryCollectionEdgeToDataobjectCollection_simple()
    {
        for (dk.sdu.cloud.jpa.sduclouddb.Project jpaProject :jpaHelpers.getJpaProjectList())
        {

            ProjectCollectionEdgeToDataObjectDirectoryCollection_simple projectCollectionEdgeToDataObjectDirectoryCollection_simple = new ProjectCollectionEdgeToDataObjectDirectoryCollection_simple();

            projectCollectionEdgeToDataObjectDirectoryCollection_simple.setFrom(this.getProjectByProjectId(jpaProject.getId()));

            for (dk.sdu.cloud.jpa.sduclouddb.DataobjectDirectory dataobjectDirectory :jpaProject.getDataobjectDirectoryList())
            {
                ArangoDB arangoDB = new ArangoDB.Builder().user("root").build();

                projectCollectionEdgeToDataObjectDirectoryCollection_simple.setTo(getDataObjectDirectoryByDataObjectDirectoryId(dataobjectDirectory.getId()));
                System.err.println(projectCollectionEdgeToDataObjectDirectoryCollection_simple.toString());
                arangoDB.db("sduclouddb").graph("sduclouddbGraph").edgeCollection("projectCollectionEdgeToDataObjectDirectoryCollection_simple").insertEdge(projectCollectionEdgeToDataObjectDirectoryCollection_simple);
                arangoDB.shutdown();
            }
        }
    }



}
