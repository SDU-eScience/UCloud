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
@Table(name = "uploadtransaction")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Uploadtransaction.findAll", query = "SELECT u FROM Uploadtransaction u")
    , @NamedQuery(name = "Uploadtransaction.findById", query = "SELECT u FROM Uploadtransaction u WHERE u.id = :id")
    , @NamedQuery(name = "Uploadtransaction.findByTotallength", query = "SELECT u FROM Uploadtransaction u WHERE u.totallength = :totallength")
    , @NamedQuery(name = "Uploadtransaction.findByIrodszone", query = "SELECT u FROM Uploadtransaction u WHERE u.irodszone = :irodszone")
    , @NamedQuery(name = "Uploadtransaction.findByTargetcollection", query = "SELECT u FROM Uploadtransaction u WHERE u.targetcollection = :targetcollection")
    , @NamedQuery(name = "Uploadtransaction.findByTargetname", query = "SELECT u FROM Uploadtransaction u WHERE u.targetname = :targetname")
    , @NamedQuery(name = "Uploadtransaction.findByActive", query = "SELECT u FROM Uploadtransaction u WHERE u.active = :active")
    , @NamedQuery(name = "Uploadtransaction.findByMarkedfordelete", query = "SELECT u FROM Uploadtransaction u WHERE u.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Uploadtransaction.findByModifiedTs", query = "SELECT u FROM Uploadtransaction u WHERE u.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Uploadtransaction.findByCreatedTs", query = "SELECT u FROM Uploadtransaction u WHERE u.createdTs = :createdTs")})
public class Uploadtransaction implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "totallength")
    private Integer totallength;
    @Column(name = "irodszone")
    private String irodszone;
    @Column(name = "targetcollection")
    private String targetcollection;
    @Column(name = "targetname")
    private String targetname;
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
    @ManyToOne
    private Person personrefid;

    public Uploadtransaction() {
    }

    public Uploadtransaction(Integer id) {
        this.id = id;
    }

    public Uploadtransaction(Integer id, Date modifiedTs, Date createdTs) {
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

    public Integer getTotallength() {
        return totallength;
    }

    public void setTotallength(Integer totallength) {
        this.totallength = totallength;
    }

    public String getIrodszone() {
        return irodszone;
    }

    public void setIrodszone(String irodszone) {
        this.irodszone = irodszone;
    }

    public String getTargetcollection() {
        return targetcollection;
    }

    public void setTargetcollection(String targetcollection) {
        this.targetcollection = targetcollection;
    }

    public String getTargetname() {
        return targetname;
    }

    public void setTargetname(String targetname) {
        this.targetname = targetname;
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

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof Uploadtransaction)) {
            return false;
        }
        Uploadtransaction other = (Uploadtransaction) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.Uploadtransaction[ id=" + id + " ]";
    }
    
}
