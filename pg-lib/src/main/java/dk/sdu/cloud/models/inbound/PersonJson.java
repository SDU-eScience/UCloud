package dk.sdu.cloud.models.inbound;

public class PersonJson {
    private String persontitle;
    private String personfirstname;
    private String personmiddlename;
    private String personlastname;
    private String personphoneno;
    private String orcid;
    private String orgrefid;

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

    public String getOrgrefid() {
        return orgrefid;
    }

    public void setOrgrefid(String orgrefid) {
        this.orgrefid = orgrefid;
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

    public Person toPersonObj(PersonJson personJson)
    {
        Person person = new Person();

        person.setPersontitle(personJson.getPersontitle());
        person.setPersonfirstnames(personJson.getPersonfirstname());
        person.setPersonlastname(personJson.getPersonlastname());
        person.setPersonphoneno(personJson.getPersonphoneno());
        person.setOrcid(personJson.getOrcid());
       // person.setOrgrefid(Integer.parseInt(personJson.getOrgrefid()));
        return person;
    }
}
