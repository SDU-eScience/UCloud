package dk.sdu.cloud.arangodb.utils;

import dk.sdu.cloud.jpa.utils.JpaHelpers;
import dk.sdu.cloud.utils.arangodb.ArangoDbHelpers;

import java.util.concurrent.ExecutionException;

public class LoadDB {


    public static void main(String[] args) throws ExecutionException, InterruptedException {
        JpaHelpers jpaHelpers = new JpaHelpers();
        ArangoDbHelpers arangoDbHelpers = new ArangoDbHelpers();
        //arangoDbHelpers.loadDataObjectCollection();
        //arangoDbHelpers.loadDataObjectDirectoryCollection();
        arangoDbHelpers.loadProjectRoleCollection();

        //arangoDbHelpers.loadPersonCollection();
        //arangoDbHelpers.loadProjectCollection();



        //arangoDbHelpers.basicSetupSduCloudGraph();
        //arangoDbHelpers.setEdge();
        //jpaHelpers.setCollectionTestData();



    }
}
