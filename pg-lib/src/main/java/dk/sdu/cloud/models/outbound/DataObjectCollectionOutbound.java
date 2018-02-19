package dk.sdu.cloud.models.outbound;

public class DataObjectCollectionOutbound {
    private int id;
    private String dataObjectCollectionUrl;

    public DataObjectCollectionOutbound() {

    }

    public DataObjectCollectionOutbound(int id, String dataObjectCollectionUrl) {
        this.id = id;
        this.dataObjectCollectionUrl = dataObjectCollectionUrl;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDataObjectCollectionUrl() {
        return dataObjectCollectionUrl;
    }

    public void setDataObjectCollectionUrl(String dataObjectCollectionUrl) {
        this.dataObjectCollectionUrl = dataObjectCollectionUrl;
    }


}
