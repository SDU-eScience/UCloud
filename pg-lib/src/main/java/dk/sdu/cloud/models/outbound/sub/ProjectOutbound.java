package dk.sdu.cloud.models.outbound.sub;

public class ProjectOutbound {
    private int id;
    private String projectName;

    public ProjectOutbound()
    {

    }

    public ProjectOutbound(int id,String projectName)
    {
        this.id = id;
        this.projectName = projectName;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
}
