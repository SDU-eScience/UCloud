package dk.sdu.cloud.TempHandlers;

import dk.sdu.cloud.models.outbound.DataObjectCollectionOutbound;
import dk.sdu.cloud.models.outbound.InitUi;
import dk.sdu.cloud.models.outbound.InitUiResponse;
import dk.sdu.cloud.models.outbound.ProjectOutbound;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InitUiMock {
    public static void main(String[] args) {
        List<ProjectOutbound> projectOutboundList = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        projectOutboundList.add(new ProjectOutbound(1, "Research case 1: Personalized medicine in cancer"));
        projectOutboundList.add(new ProjectOutbound(2, "Research case 2: Protein identification on the fast track"));
        projectOutboundList.add(new ProjectOutbound(3, "Research case 3: Motif Discovery with HOMER"));
        projectOutboundList.add(new ProjectOutbound(4, "Research case 4: Event generator for Beyond Standard Model (BSM) physics"));
        projectOutboundList.add(new ProjectOutbound(5, "Research case 5: Phylogenetic analysis"));
        projectOutboundList.add(new ProjectOutbound(6, "Research case 6: Data Stream Processing"));

        List<DataObjectCollectionOutbound> dataObjectCollectionOutboundList = new ArrayList<>();

        dataObjectCollectionOutboundList.add(new DataObjectCollectionOutbound(1, "/home/jmhinchely/"));
        dataObjectCollectionOutboundList.add(new DataObjectCollectionOutbound(2, "/home/research_case_1/"));
        dataObjectCollectionOutboundList.add(new DataObjectCollectionOutbound(3, "/home/research_case_2/"));
        dataObjectCollectionOutboundList.add(new DataObjectCollectionOutbound(4, "/home/research_case_3/"));
        dataObjectCollectionOutboundList.add(new DataObjectCollectionOutbound(5, "/home/research_case_4/"));
        dataObjectCollectionOutboundList.add(new DataObjectCollectionOutbound(6, "/home/research_case_5/"));
        dataObjectCollectionOutboundList.add(new DataObjectCollectionOutbound(7, "/home/research_case_6/"));

        InitUi initUi = new InitUi();
        initUi.setFullName("Jonas Malte Hinchely");
        initUi.setDataobjectcollectionList(dataObjectCollectionOutboundList);
        initUi.setProjectList(projectOutboundList);
        initUi.setDiskSpaceMb(100);
        initUi.setNoMessages(10);
        initUi.setNoProjects(6);
        initUi.setNoSystemroles(1);
        initUi.setNoDataObjectCollections(7);

        InitUiResponse initUiResponse = new InitUiResponse();
        initUiResponse.setRc("success");
        initUiResponse.setInitUi(initUi);

        try {
            String jsonInString = mapper.writeValueAsString(initUiResponse);
            System.err.println(jsonInString);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
