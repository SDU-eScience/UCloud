package dk.sdu.cloud.models.outbound;

import dk.sdu.cloud.models.outbound.sub.DataObjectCollectionOutbound;
import dk.sdu.cloud.models.outbound.sub.ProjectOutbound;

import java.util.List;

public class InitUi {

    private String fullName;
    private int noProjects;
    private int diskSpaceMb;
    private int noSystemroles;
    private int noMessages;
    private int noDataObjectCollections;
    private List<ProjectOutbound> projectList;
    private List<DataObjectCollectionOutbound> dataobjectcollectionList;


    public InitUi()
    {}

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public int getNoProjects() {
        return noProjects;
    }

    public void setNoProjects(int noProjects) {
        this.noProjects = noProjects;
    }

    public int getDiskSpaceMb() {
        return diskSpaceMb;
    }

    public void setDiskSpaceMb(int diskSpaceMb) {
        this.diskSpaceMb = diskSpaceMb;
    }

    public int getNoSystemroles() {
        return noSystemroles;
    }

    public void setNoSystemroles(int noSystemroles) {
        this.noSystemroles = noSystemroles;
    }

    public int getNoMessages() {
        return noMessages;
    }

    public void setNoMessages(int noMessages) {
        this.noMessages = noMessages;
    }

    public int getNoDataObjectCollections() {
        return noDataObjectCollections;
    }

    public void setNoDataObjectCollections(int noDataObjectCollections) {
        this.noDataObjectCollections = noDataObjectCollections;
    }

    public List<ProjectOutbound> getProjectList() {
        return projectList;
    }

    public void setProjectList(List<ProjectOutbound> projectList) {
        this.projectList = projectList;
    }

    public List<DataObjectCollectionOutbound> getDataobjectcollectionList() {
        return dataobjectcollectionList;
    }

    public void setDataobjectcollectionList(List<DataObjectCollectionOutbound> dataobjectcollectionList) {
        this.dataobjectcollectionList = dataobjectcollectionList;
    }
}
