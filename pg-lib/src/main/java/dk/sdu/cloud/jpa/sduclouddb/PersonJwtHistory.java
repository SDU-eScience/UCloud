/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import javax.persistence.Basic;
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
@Table(name = "person_jwt_history")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "PersonJwtHistory.findAll", query = "SELECT p FROM PersonJwtHistory p")
    , @NamedQuery(name = "PersonJwtHistory.findById", query = "SELECT p FROM PersonJwtHistory p WHERE p.id = :id")
    , @NamedQuery(name = "PersonJwtHistory.findBySessionid", query = "SELECT p FROM PersonJwtHistory p WHERE p.sessionid = :sessionid")
    , @NamedQuery(name = "PersonJwtHistory.findByMarkedfordelete", query = "SELECT p FROM PersonJwtHistory p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "PersonJwtHistory.findByJwt", query = "SELECT p FROM PersonJwtHistory p WHERE p.jwt = :jwt")
    , @NamedQuery(name = "PersonJwtHistory.findByModifiedTs", query = "SELECT p FROM PersonJwtHistory p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "PersonJwtHistory.findByCreatedTs", query = "SELECT p FROM PersonJwtHistory p WHERE p.createdTs = :createdTs")})
public class PersonJwtHistory implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "sessionid")
    private String sessionid;
    @Column(name = "markedfordelete")
    private Integer markedfordelete;
    @Column(name = "jwt")
    private String jwt;
    @Basic(optional = false)
    @Column(name = "modified_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date modifiedTs;
    @Basic(optional = false)
    @Column(name = "created_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdTs;
    @OneToMany(mappedBy = "personjwthistoryrefid")
    private List<SubsystemCommandQueue> subsystemCommandQueueList;
    @OneToOne(mappedBy = "personjwthistoryrefid")
    private Person person;
    @JoinColumn(name = "personrefid", referencedColumnName = "id")
    @ManyToOne
    private Person personrefid;

    public PersonJwtHistory() {
    }

    public PersonJwtHistory(Integer id) {
        this.id = id;
    }

    public PersonJwtHistory(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getSessionid() {
        return sessionid;
    }

    public void setSessionid(String sessionid) {
        this.sessionid = sessionid;
    }

    public Integer getMarkedfordelete() {
        return markedfordelete;
    }

    public void setMarkedfordelete(Integer markedfordelete) {
        this.markedfordelete = markedfordelete;
    }

    public String getJwt() {
        return jwt;
    }

    public void setJwt(String jwt) {
        this.jwt = jwt;
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

    @XmlTransient
    public List<SubsystemCommandQueue> getSubsystemCommandQueueList() {
        return subsystemCommandQueueList;
    }

    public void setSubsystemCommandQueueList(List<SubsystemCommandQueue> subsystemCommandQueueList) {
        this.subsystemCommandQueueList = subsystemCommandQueueList;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public Person getPersonrefid() {
        return personrefid;
    }

    public void setPersonrefid(Person personrefid) {
        this.personrefid = personrefid;
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
        if (!(object instanceof PersonJwtHistory)) {
            return false;
        }
        PersonJwtHistory other = (PersonJwtHistory) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.PersonJwtHistory[ id=" + id + " ]";
    }
    
}
