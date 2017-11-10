/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esciencecloud.jpa.escienceclouddb;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
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
    , @NamedQuery(name = "Person.findByPersonworkemail", query = "SELECT p FROM Person p WHERE p.personworkemail = :personworkemail")
    , @NamedQuery(name = "Person.findByPersonphoneno", query = "SELECT p FROM Person p WHERE p.personphoneno = :personphoneno")
    , @NamedQuery(name = "Person.findByLogintyperefid", query = "SELECT p FROM Person p WHERE p.logintyperefid = :logintyperefid")
    , @NamedQuery(name = "Person.findByLatitude", query = "SELECT p FROM Person p WHERE p.latitude = :latitude")
    , @NamedQuery(name = "Person.findByLongitude", query = "SELECT p FROM Person p WHERE p.longitude = :longitude")
    , @NamedQuery(name = "Person.findByIrodsuseridmap", query = "SELECT p FROM Person p WHERE p.irodsuseridmap = :irodsuseridmap")
    , @NamedQuery(name = "Person.findByActive", query = "SELECT p FROM Person p WHERE p.active = :active")
    , @NamedQuery(name = "Person.findByLatestsessionid", query = "SELECT p FROM Person p WHERE p.latestsessionid = :latestsessionid")
    , @NamedQuery(name = "Person.findByOrcid", query = "SELECT p FROM Person p WHERE p.orcid = :orcid")
    , @NamedQuery(name = "Person.findByName", query = "SELECT p FROM Person p WHERE p.name = :name")
    , @NamedQuery(name = "Person.findByLastmodified", query = "SELECT p FROM Person p WHERE p.lastmodified = :lastmodified")
    , @NamedQuery(name = "Person.findByIrodsusername", query = "SELECT p FROM Person p WHERE p.irodsusername = :irodsusername")
    , @NamedQuery(name = "Person.findByPw", query = "SELECT p FROM Person p WHERE p.pw = :pw")
    , @NamedQuery(name = "Person.findByPersonsessionhistory", query = "SELECT p FROM Person p WHERE p.personsessionhistory = :personsessionhistory")})
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
    @Column(name = "personworkemail")
    private String personworkemail;
    @Column(name = "personphoneno")
    private String personphoneno;
    @Column(name = "logintyperefid")
    private Integer logintyperefid;
    // @Max(value=?)  @Min(value=?)//if you know range of your decimal fields consider using these annotations to enforce field validation
    @Column(name = "latitude")
    private BigDecimal latitude;
    @Column(name = "longitude")
    private BigDecimal longitude;
    @Column(name = "irodsuseridmap")
    private Integer irodsuseridmap;
    @Column(name = "active")
    private Integer active;
    @Column(name = "latestsessionid")
    private String latestsessionid;
    @Column(name = "orcid")
    private String orcid;
    @Column(name = "name")
    private String name;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @Column(name = "irodsusername")
    private String irodsusername;
    @Column(name = "pw")
    private String pw;
    @Column(name = "personsessionhistory")
    private Integer personsessionhistory;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "personrefid")
    private Collection<Personappusermessagesubscriptiontyperel> personappusermessagesubscriptiontyperelCollection;
    @OneToMany(mappedBy = "personrefid")
    private Collection<Personsessionhistory> personsessionhistoryCollection;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "personrefid")
    private Collection<Projectpersonrel> projectpersonrelCollection;
    @JoinColumn(name = "personsessionhistoryrefid", referencedColumnName = "id")
    @ManyToOne
    private Personsessionhistory personsessionhistoryrefid;

    public Person() {
    }

    public Person(Integer id) {
        this.id = id;
    }

    public Person(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
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

    public String getPersonworkemail() {
        return personworkemail;
    }

    public void setPersonworkemail(String personworkemail) {
        this.personworkemail = personworkemail;
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

    public Integer getIrodsuseridmap() {
        return irodsuseridmap;
    }

    public void setIrodsuseridmap(Integer irodsuseridmap) {
        this.irodsuseridmap = irodsuseridmap;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
    }

    public String getLatestsessionid() {
        return latestsessionid;
    }

    public void setLatestsessionid(String latestsessionid) {
        this.latestsessionid = latestsessionid;
    }

    public String getOrcid() {
        return orcid;
    }

    public void setOrcid(String orcid) {
        this.orcid = orcid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    public String getIrodsusername() {
        return irodsusername;
    }

    public void setIrodsusername(String irodsusername) {
        this.irodsusername = irodsusername;
    }

    public String getPw() {
        return pw;
    }

    public void setPw(String pw) {
        this.pw = pw;
    }

    public Integer getPersonsessionhistory() {
        return personsessionhistory;
    }

    public void setPersonsessionhistory(Integer personsessionhistory) {
        this.personsessionhistory = personsessionhistory;
    }

    @XmlTransient
    public Collection<Personappusermessagesubscriptiontyperel> getPersonappusermessagesubscriptiontyperelCollection() {
        return personappusermessagesubscriptiontyperelCollection;
    }

    public void setPersonappusermessagesubscriptiontyperelCollection(Collection<Personappusermessagesubscriptiontyperel> personappusermessagesubscriptiontyperelCollection) {
        this.personappusermessagesubscriptiontyperelCollection = personappusermessagesubscriptiontyperelCollection;
    }

    @XmlTransient
    public Collection<Personsessionhistory> getPersonsessionhistoryCollection() {
        return personsessionhistoryCollection;
    }

    public void setPersonsessionhistoryCollection(Collection<Personsessionhistory> personsessionhistoryCollection) {
        this.personsessionhistoryCollection = personsessionhistoryCollection;
    }

    @XmlTransient
    public Collection<Projectpersonrel> getProjectpersonrelCollection() {
        return projectpersonrelCollection;
    }

    public void setProjectpersonrelCollection(Collection<Projectpersonrel> projectpersonrelCollection) {
        this.projectpersonrelCollection = projectpersonrelCollection;
    }

    public Personsessionhistory getPersonsessionhistoryrefid() {
        return personsessionhistoryrefid;
    }

    public void setPersonsessionhistoryrefid(Personsessionhistory personsessionhistoryrefid) {
        this.personsessionhistoryrefid = personsessionhistoryrefid;
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
        return "org.escience.jpa.escienceclouddb.Person[ id=" + id + " ]";
    }
    
}
