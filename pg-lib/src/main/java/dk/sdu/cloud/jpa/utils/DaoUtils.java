package dk.sdu.cloud.jpa.utils;


import dk.sdu.cloud.jpa.sduclouddb.Person;
import dk.sdu.cloud.models.outbound.InitUi;
import dk.sdu.cloud.models.outbound.sub.InitUiResponse;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;

public class DaoUtils {

    public String getInitUiResponseByEmail(String email) {

        ObjectMapper mapper = new ObjectMapper();
        String initUiResponseString = null;
        Person person = new Person();
        person.getDataobjectcollectionList();


        InitUi initUi = new InitUi();
        initUi.setFullName("Jonas Malte Hinchely");
        //initUi.setDataobjectcollectionList(dataObjectCollectionOutboundList);
        //initUi.setProjectList(projectOutboundList);
        initUi.setDiskSpaceMb(100);
        initUi.setNoMessages(10);
        initUi.setNoProjects(6);
        initUi.setNoSystemroles(1);
        initUi.setNoDataObjectCollections(7);

        InitUiResponse initUiResponse = new InitUiResponse();
        initUiResponse.setRc("success");
        initUiResponse.setInitUi(initUi);

        try {
            initUiResponseString = mapper.writeValueAsString(initUiResponse);
            System.err.println(initUiResponseString);
        } catch (IOException e) {
            initUiResponse.setRc("error");
            e.printStackTrace();
        }

        return initUiResponseString;
    }



}
