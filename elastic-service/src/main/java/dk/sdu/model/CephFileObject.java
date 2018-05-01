package dk.sdu.model;

public class CephFileObject {

    private Integer inodeId;
    private Integer size;
    private String date;
    private String filePath;

    public CephFileObject()
    {

    }

    public Integer getInodeId() {
        return inodeId;
    }

    public void setInodeId(Integer inodeId) {
        this.inodeId = inodeId;
    }

    public Integer getSize() {
        return size;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
