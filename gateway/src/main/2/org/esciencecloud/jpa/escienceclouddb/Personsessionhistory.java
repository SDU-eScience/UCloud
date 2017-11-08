/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esciencecloud.jpa.escienceclouddb;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author bjhj
 */
@Entity
@Table(name = "personsessionhistory")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Personsessionhistory.findAll", query = "SELECT p FROM Personsessionhistory p")
        , @NamedQuery(name = "Personsessionhistory.findById", query = "SELECT p FROM Personsessionhistory p WHERE p.id = :id")
        , @NamedQuery(name = "Personsessionhistory.findBySessionid", query = "SELECT p FROM Personsessionhistory p WHERE p.sessionid = :sessionid")
        , @NamedQuery(name = "Personsessionhistory.findByLastmodified", query = "SELECT p FROM Personsessionhistory p WHERE p.lastmodified = :lastmodified")})
public class Personsessionhistory implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "sessionid")
    private String sessionid;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @OneToMany(mappedBy = "personsessionhistoryrefid")
    private List<Subsystemcommandqueue> subsystemcommandqueueList;
    @JoinColumn(name = "personrefid", referencedColumnName = "id")
    @ManyToOne
    private Person personrefid;
    @OneToMany(mappedBy = "personsessionhistoryrefid")
    private List<Person> personList;

    public Personsessionhistory() {
    }

    public Personsessionhistory(Integer id) {
        this.id = id;
    }

    public Personsessionhistory(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
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

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    @XmlTransient
    public List<Subsystemcommandqueue> getSubsystemcommandqueueList() {
        return subsystemcommandqueueList;
    }

    public void setSubsystemcommandqueueList(List<Subsystemcommandqueue> subsystemcommandqueueList) {
        this.subsystemcommandqueueList = subsystemcommandqueueList;
    }

    public Person getPersonrefid() {
        return personrefid;
    }

    public void setPersonrefid(Person personrefid) {
        this.personrefid = personrefid;
    }

    @XmlTransient
    public List<Person> getPersonList() {
        return personList;
    }

    public void setPersonList(List<Person> personList) {
        this.personList = personList;
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
        if (!(object instanceof Personsessionhistory)) {
            return false;
        }
        Personsessionhistory other = (Personsessionhistory) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Personsessionhistory[ id=" + id + " ]";
    }

}
