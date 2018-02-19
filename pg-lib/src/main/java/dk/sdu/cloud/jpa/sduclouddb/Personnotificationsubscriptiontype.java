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
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
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
@Table(name = "personnotificationsubscriptiontype")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Personnotificationsubscriptiontype.findAll", query = "SELECT p FROM Personnotificationsubscriptiontype p")
    , @NamedQuery(name = "Personnotificationsubscriptiontype.findById", query = "SELECT p FROM Personnotificationsubscriptiontype p WHERE p.id = :id")
    , @NamedQuery(name = "Personnotificationsubscriptiontype.findByPersonnotificationsubscriptiontypename", query = "SELECT p FROM Personnotificationsubscriptiontype p WHERE p.personnotificationsubscriptiontypename = :personnotificationsubscriptiontypename")
    , @NamedQuery(name = "Personnotificationsubscriptiontype.findByActive", query = "SELECT p FROM Personnotificationsubscriptiontype p WHERE p.active = :active")
    , @NamedQuery(name = "Personnotificationsubscriptiontype.findByMarkedfordelete", query = "SELECT p FROM Personnotificationsubscriptiontype p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Personnotificationsubscriptiontype.findByModifiedTs", query = "SELECT p FROM Personnotificationsubscriptiontype p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Personnotificationsubscriptiontype.findByCreatedTs", query = "SELECT p FROM Personnotificationsubscriptiontype p WHERE p.createdTs = :createdTs")
    , @NamedQuery(name = "Personnotificationsubscriptiontype.findByNosuppress", query = "SELECT p FROM Personnotificationsubscriptiontype p WHERE p.nosuppress = :nosuppress")})
public class Personnotificationsubscriptiontype implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "personnotificationsubscriptiontypename")
    private String personnotificationsubscriptiontypename;
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
    @Column(name = "nosuppress")
    private Integer nosuppress;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "personnotificationsubscriptiontyperefid")
    private List<Personnotificationsubscriptiontyperel> personnotificationsubscriptiontyperelList;

    public Personnotificationsubscriptiontype() {
    }

    public Personnotificationsubscriptiontype(Integer id) {
        this.id = id;
    }

    public Personnotificationsubscriptiontype(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getPersonnotificationsubscriptiontypename() {
        return personnotificationsubscriptiontypename;
    }

    public void setPersonnotificationsubscriptiontypename(String personnotificationsubscriptiontypename) {
        this.personnotificationsubscriptiontypename = personnotificationsubscriptiontypename;
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

    public Integer getNosuppress() {
        return nosuppress;
    }

    public void setNosuppress(Integer nosuppress) {
        this.nosuppress = nosuppress;
    }

    @XmlTransient
    public List<Personnotificationsubscriptiontyperel> getPersonnotificationsubscriptiontyperelList() {
        return personnotificationsubscriptiontyperelList;
    }

    public void setPersonnotificationsubscriptiontyperelList(List<Personnotificationsubscriptiontyperel> personnotificationsubscriptiontyperelList) {
        this.personnotificationsubscriptiontyperelList = personnotificationsubscriptiontyperelList;
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
        if (!(object instanceof Personnotificationsubscriptiontype)) {
            return false;
        }
        Personnotificationsubscriptiontype other = (Personnotificationsubscriptiontype) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return "Personnotificationsubscriptiontype{" +
                "id=" + id +
                ", personnotificationsubscriptiontypename='" + personnotificationsubscriptiontypename + '\'' +
                ", active=" + active +
                ", markedfordelete=" + markedfordelete +
                ", modifiedTs=" + modifiedTs +
                ", createdTs=" + createdTs +
                ", nosuppress=" + nosuppress +
                ", personnotificationsubscriptiontyperelList=" + personnotificationsubscriptiontyperelList +
                '}';
    }

}
