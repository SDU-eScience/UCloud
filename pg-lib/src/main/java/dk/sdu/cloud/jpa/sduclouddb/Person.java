/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 *
 * @author bjhj
 */
@Entity
@Table(name = "person")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Person.findAll", query = "SELECT p FROM Person p")
    , @NamedQuery(name = "Person.findById", query = "SELECT p FROM Person p WHERE p.id = :id")
    , @NamedQuery(name = "Person.findByPersontitle", query = "SELECT p FROM Person p WHERE p.persontitle = :persontitle")
    , @NamedQuery(name = "Person.findByPersonfirstname", query = "SELECT p FROM Person p WHERE p.personfirstname = :personfirstname")
    , @NamedQuery(name = "Person.findByPersonmiddlename", query = "SELECT p FROM Person p WHERE p.personmiddlename = :personmiddlename")
    , @NamedQuery(name = "Person.findByPersonlastname", query = "SELECT p FROM Person p WHERE p.personlastname = :personlastname")
    , @NamedQuery(name = "Person.findByPersonphoneno", query = "SELECT p FROM Person p WHERE p.personphoneno = :personphoneno")
    , @NamedQuery(name = "Person.findByLogintyperefid", query = "SELECT p FROM Person p WHERE p.logintyperefid = :logintyperefid")
    , @NamedQuery(name = "Person.findByLatitude", query = "SELECT p FROM Person p WHERE p.latitude = :latitude")
    , @NamedQuery(name = "Person.findByLongitude", query = "SELECT p FROM Person p WHERE p.longitude = :longitude")
    , @NamedQuery(name = "Person.findByActive", query = "SELECT p FROM Person p WHERE p.active = :active")
    , @NamedQuery(name = "Person.findByOrcid", query = "SELECT p FROM Person p WHERE p.orcid = :orcid")
    , @NamedQuery(name = "Person.findByPersonFullname", query = "SELECT p FROM Person p WHERE p.personFullname = :personFullname")
    , @NamedQuery(name = "Person.findByMarkedfordelete", query = "SELECT p FROM Person p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Person.findByModifiedTs", query = "SELECT p FROM Person p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Person.findByCreatedTs", query = "SELECT p FROM Person p WHERE p.createdTs = :createdTs")
    , @NamedQuery(name = "Person.findByUsername", query = "SELECT p FROM Person p WHERE p.username = :username")})
public class Person implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "persontitle")
    private String persontitle;
    @Column(name = "personfirstname")
    private String personfirstname;
    @Column(name = "personmiddlename")
    private String personmiddlename;
    @Column(name = "personlastname")
    private String personlastname;
    @Column(name = "personphoneno")
    private String personphoneno;
    @Column(name = "logintyperefid")
    private Integer logintyperefid;
    // @Max(value=?)  @Min(value=?)//if you know range of your decimal fields consider using these annotations to enforce field validation
    @Column(name = "latitude")
    private BigDecimal latitude;
    @Column(name = "longitude")
    private BigDecimal longitude;
    @Column(name = "active")
    private Integer active;
    @Column(name = "orcid")
    private String orcid;
    @Column(name = "person_fullname")
    private String personFullname;
    @Column(name = "markedfordelete")
    private Integer markedfordelete;
    @Basic(optional = false)
    @Column(name = "modified_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date modifiedTs;
    @Basic(optional = false)
    @Column(name = "created_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdTs;
    @Column(name = "username")
    private String username;
    @OneToMany(mappedBy = "personrefid")
    private List<PersonEmailRelation> personEmailRelationList;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "personrefid")
    private List<ProjectPersonRelation> projectPersonRelationList;
    @OneToMany(mappedBy = "personrefid")
    private List<ProjectEventCalendar> projectEventCalendarList;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "personrefid")
    private List<PersonNotificationSubscriptiontypeRelation> personNotificationSubscriptiontypeRelationList;
    @OneToMany(mappedBy = "personrefid")
    private List<Notification> notificationList;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "personrefid")
    private List<SystemrolePersonRelation> systemrolePersonRelationList;
    @OneToMany(mappedBy = "personrefid")
    private List<PersonJwtHistory> personJwtHistoryList;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "personrefid")
    private List<PersonSystemroleRelation> personSystemroleRelationList;
    @OneToMany(mappedBy = "personrefid")
    private List<App> appList;
    @OneToMany(mappedBy = "personrefid")
    private List<PersonDataobjectAcl> personDataobjectAclList;
    @JoinColumn(name = "orgrefid", referencedColumnName = "id")
    @ManyToOne
    private Org orgrefid;
    @JoinColumn(name = "personjwthistoryrefid", referencedColumnName = "id")
    @OneToOne
    private PersonJwtHistory personjwthistoryrefid;
    @OneToMany(mappedBy = "personrefid")
    private List<DataTransferHeader> dataTransferHeaderList;
    @OneToMany(mappedBy = "personrefid")
    private List<PersonDataobjectSpecialShareRelation> personDataobjectSpecialShareRelationList;

    public Person() {
    }

    public Person(Integer id) {
        this.id = id;
    }

    public Person(Integer id, Date modifiedTs, Date createdTs) {
        this.id = id;
        this.modifiedTs = modifiedTs;
        this.createdTs = createdTs;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

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

    public Integer getLogintyperefid() {
        return logintyperefid;
    }

    public void setLogintyperefid(Integer logintyperefid) {
        this.logintyperefid = logintyperefid;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
    }

    public String getOrcid() {
        return orcid;
    }

    public void setOrcid(String orcid) {
        this.orcid = orcid;
    }

    public String getPersonFullname() {
        return personFullname;
    }

    public void setPersonFullname(String personFullname) {
        this.personFullname = personFullname;
    }

    public Integer getMarkedfordelete() {
        return markedfordelete;
    }

    public void setMarkedfordelete(Integer markedfordelete) {
        this.markedfordelete = markedfordelete;
    }

    public Date getModifiedTs() {
        return modifiedTs;
    }

    public void setModifiedTs(Date modifiedTs) {
        this.modifiedTs = modifiedTs;
    }

    public Date getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(Date createdTs) {
        this.createdTs = createdTs;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @XmlTransient
    public List<PersonEmailRelation> getPersonEmailRelationList() {
        return personEmailRelationList;
    }

    public void setPersonEmailRelationList(List<PersonEmailRelation> personEmailRelationList) {
        this.personEmailRelationList = personEmailRelationList;
    }

    @XmlTransient
    public List<ProjectPersonRelation> getProjectPersonRelationList() {
        return projectPersonRelationList;
    }

    public void setProjectPersonRelationList(List<ProjectPersonRelation> projectPersonRelationList) {
        this.projectPersonRelationList = projectPersonRelationList;
    }

    @XmlTransient
    public List<ProjectEventCalendar> getProjectEventCalendarList() {
        return projectEventCalendarList;
    }

    public void setProjectEventCalendarList(List<ProjectEventCalendar> projectEventCalendarList) {
        this.projectEventCalendarList = projectEventCalendarList;
    }

    @XmlTransient
    public List<PersonNotificationSubscriptiontypeRelation> getPersonNotificationSubscriptiontypeRelationList() {
        return personNotificationSubscriptiontypeRelationList;
    }

    public void setPersonNotificationSubscriptiontypeRelationList(List<PersonNotificationSubscriptiontypeRelation> personNotificationSubscriptiontypeRelationList) {
        this.personNotificationSubscriptiontypeRelationList = personNotificationSubscriptiontypeRelationList;
    }

    @XmlTransient
    public List<Notification> getNotificationList() {
        return notificationList;
    }

    public void setNotificationList(List<Notification> notificationList) {
        this.notificationList = notificationList;
    }

    @XmlTransient
    public List<SystemrolePersonRelation> getSystemrolePersonRelationList() {
        return systemrolePersonRelationList;
    }

    public void setSystemrolePersonRelationList(List<SystemrolePersonRelation> systemrolePersonRelationList) {
        this.systemrolePersonRelationList = systemrolePersonRelationList;
    }

    @XmlTransient
    public List<PersonJwtHistory> getPersonJwtHistoryList() {
        return personJwtHistoryList;
    }

    public void setPersonJwtHistoryList(List<PersonJwtHistory> personJwtHistoryList) {
        this.personJwtHistoryList = personJwtHistoryList;
    }

    @XmlTransient
    public List<PersonSystemroleRelation> getPersonSystemroleRelationList() {
        return personSystemroleRelationList;
    }

    public void setPersonSystemroleRelationList(List<PersonSystemroleRelation> personSystemroleRelationList) {
        this.personSystemroleRelationList = personSystemroleRelationList;
    }

    @XmlTransient
    public List<App> getAppList() {
        return appList;
    }

    public void setAppList(List<App> appList) {
        this.appList = appList;
    }

    @XmlTransient
    public List<PersonDataobjectAcl> getPersonDataobjectAclList() {
        return personDataobjectAclList;
    }

    public void setPersonDataobjectAclList(List<PersonDataobjectAcl> personDataobjectAclList) {
        this.personDataobjectAclList = personDataobjectAclList;
    }

    public Org getOrgrefid() {
        return orgrefid;
    }

    public void setOrgrefid(Org orgrefid) {
        this.orgrefid = orgrefid;
    }

    public PersonJwtHistory getPersonjwthistoryrefid() {
        return personjwthistoryrefid;
    }

    public void setPersonjwthistoryrefid(PersonJwtHistory personjwthistoryrefid) {
        this.personjwthistoryrefid = personjwthistoryrefid;
    }

    @XmlTransient
    public List<DataTransferHeader> getDataTransferHeaderList() {
        return dataTransferHeaderList;
    }

    public void setDataTransferHeaderList(List<DataTransferHeader> dataTransferHeaderList) {
        this.dataTransferHeaderList = dataTransferHeaderList;
    }

    @XmlTransient
    public List<PersonDataobjectSpecialShareRelation> getPersonDataobjectSpecialShareRelationList() {
        return personDataobjectSpecialShareRelationList;
    }

    public void setPersonDataobjectSpecialShareRelationList(List<PersonDataobjectSpecialShareRelation> personDataobjectSpecialShareRelationList) {
        this.personDataobjectSpecialShareRelationList = personDataobjectSpecialShareRelationList;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof Person)) {
            return false;
        }
        Person other = (Person) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.Person[ id=" + id + " ]";
    }
    
}
