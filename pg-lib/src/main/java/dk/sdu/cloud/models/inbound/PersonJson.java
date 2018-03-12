package dk.sdu.cloud.models.inbound;

public class PersonJson {
    private String persontitle;
    private String personfirstname;
    private String personmiddlename;
    private String personlastname;
    private String personphoneno;
    private String orcid;

    public PersonJson()
    {}

    public String getPersontitle() {
        return persontitle;
    }

    public void setPersontitle(String persontitle) {
        this.persontitle = persontitle;
    }

    public String getPersonfirstname() {
        return personfirstname;
    }

    public void setPersonfirstname(String personfirstname) {
        this.personfirstname = personfirstname;
    }

    public String getPersonmiddlename() {
        return personmiddlename;
    }

    public void setPersonmiddlename(String personmiddlename) {
        this.personmiddlename = personmiddlename;
    }

    public String getPersonlastname() {
        return personlastname;
    }

    public void setPersonlastname(String personlastname) {
        this.personlastname = personlastname;
    }

    public String getPersonphoneno() {
        return personphoneno;
    }

    public void setPersonphoneno(String personphoneno) {
        this.personphoneno = personphoneno;
    }

    public String getOrcid() {
        return orcid;
    }

    public void setOrcid(String orcid) {
        this.orcid = orcid;
    }
}
