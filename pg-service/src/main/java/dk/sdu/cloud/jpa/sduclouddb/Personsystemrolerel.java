/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.util.Date;
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
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author bjhj
 */
@Entity
@Table(name = "personsystemrolerel")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Personsystemrolerel.findAll", query = "SELECT p FROM Personsystemrolerel p")
    , @NamedQuery(name = "Personsystemrolerel.findById", query = "SELECT p FROM Personsystemrolerel p WHERE p.id = :id")
    , @NamedQuery(name = "Personsystemrolerel.findByActive", query = "SELECT p FROM Personsystemrolerel p WHERE p.active = :active")
    , @NamedQuery(name = "Personsystemrolerel.findByMarkedfordelete", query = "SELECT p FROM Personsystemrolerel p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Personsystemrolerel.findByModifiedTs", query = "SELECT p FROM Personsystemrolerel p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Personsystemrolerel.findByCreatedTs", query = "SELECT p FROM Personsystemrolerel p WHERE p.createdTs = :createdTs")})
public class Personsystemrolerel implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "active")
    private Integer active;
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
    @JoinColumn(name = "personrefid", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Person personrefid;
    @JoinColumn(name = "systemrolerefid", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Systemrole systemrolerefid;

    public Personsystemrolerel() {
    }

    public Personsystemrolerel(Integer id) {
        this.id = id;
    }

    public Personsystemrolerel(Integer id, Date modifiedTs, Date createdTs) {
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

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
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

    public Person getPersonrefid() {
        return personrefid;
    }

    public void setPersonrefid(Person personrefid) {
        this.personrefid = personrefid;
    }

    public Systemrole getSystemrolerefid() {
        return systemrolerefid;
    }

    public void setSystemrolerefid(Systemrole systemrolerefid) {
        this.systemrolerefid = systemrolerefid;
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
        if (!(object instanceof Personsystemrolerel)) {
            return false;
        }
        Personsystemrolerel other = (Personsystemrolerel) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.sducloud.jpa.sduclouddb.Personsystemrolerel[ id=" + id + " ]";
    }
    
}
