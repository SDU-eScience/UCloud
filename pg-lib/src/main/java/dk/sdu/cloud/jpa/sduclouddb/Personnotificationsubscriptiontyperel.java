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
@Table(name = "personnotificationsubscriptiontyperel")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Personnotificationsubscriptiontyperel.findAll", query = "SELECT p FROM Personnotificationsubscriptiontyperel p")
    , @NamedQuery(name = "Personnotificationsubscriptiontyperel.findById", query = "SELECT p FROM Personnotificationsubscriptiontyperel p WHERE p.id = :id")
    , @NamedQuery(name = "Personnotificationsubscriptiontyperel.findByActive", query = "SELECT p FROM Personnotificationsubscriptiontyperel p WHERE p.active = :active")
    , @NamedQuery(name = "Personnotificationsubscriptiontyperel.findByMarkedfordelete", query = "SELECT p FROM Personnotificationsubscriptiontyperel p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Personnotificationsubscriptiontyperel.findByModifiedTs", query = "SELECT p FROM Personnotificationsubscriptiontyperel p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Personnotificationsubscriptiontyperel.findByCreatedTs", query = "SELECT p FROM Personnotificationsubscriptiontyperel p WHERE p.createdTs = :createdTs")})
public class Personnotificationsubscriptiontyperel implements Serializable {

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
    @JoinColumn(name = "personnotificationsubscriptiontyperefid", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Personnotificationsubscriptiontype personnotificationsubscriptiontyperefid;

    public Personnotificationsubscriptiontyperel() {
    }

    public Personnotificationsubscriptiontyperel(Integer id) {
        this.id = id;
    }

    public Personnotificationsubscriptiontyperel(Integer id, Date modifiedTs, Date createdTs) {
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

    public Personnotificationsubscriptiontype getPersonnotificationsubscriptiontyperefid() {
        return personnotificationsubscriptiontyperefid;
    }

    public void setPersonnotificationsubscriptiontyperefid(Personnotificationsubscriptiontype personnotificationsubscriptiontyperefid) {
        this.personnotificationsubscriptiontyperefid = personnotificationsubscriptiontyperefid;
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
        if (!(object instanceof Personnotificationsubscriptiontyperel)) {
            return false;
        }
        Personnotificationsubscriptiontyperel other = (Personnotificationsubscriptiontyperel) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.Personnotificationsubscriptiontyperel[ id=" + id + " ]";
    }
    
}
