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
@Table(name = "dataobjectclassification")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Dataobjectclassification.findAll", query = "SELECT d FROM Dataobjectclassification d")
    , @NamedQuery(name = "Dataobjectclassification.findById", query = "SELECT d FROM Dataobjectclassification d WHERE d.id = :id")
    , @NamedQuery(name = "Dataobjectclassification.findByDataobjectclassificationname", query = "SELECT d FROM Dataobjectclassification d WHERE d.dataobjectclassificationname = :dataobjectclassificationname")
    , @NamedQuery(name = "Dataobjectclassification.findByActive", query = "SELECT d FROM Dataobjectclassification d WHERE d.active = :active")
    , @NamedQuery(name = "Dataobjectclassification.findByMarkedfordelete", query = "SELECT d FROM Dataobjectclassification d WHERE d.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Dataobjectclassification.findByModifiedTs", query = "SELECT d FROM Dataobjectclassification d WHERE d.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Dataobjectclassification.findByCreatedTs", query = "SELECT d FROM Dataobjectclassification d WHERE d.createdTs = :createdTs")})
public class Dataobjectclassification implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "dataobjectclassificationname")
    private String dataobjectclassificationname;
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
    @OneToMany(mappedBy = "dataobjectclassificationrefid")
    private List<Dataobject> dataobjectList;

    public Dataobjectclassification() {
    }

    public Dataobjectclassification(Integer id) {
        this.id = id;
    }

    public Dataobjectclassification(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getDataobjectclassificationname() {
        return dataobjectclassificationname;
    }

    public void setDataobjectclassificationname(String dataobjectclassificationname) {
        this.dataobjectclassificationname = dataobjectclassificationname;
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

    @XmlTransient
    public List<Dataobject> getDataobjectList() {
        return dataobjectList;
    }

    public void setDataobjectList(List<Dataobject> dataobjectList) {
        this.dataobjectList = dataobjectList;
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
        if (!(object instanceof Dataobjectclassification)) {
            return false;
        }
        Dataobjectclassification other = (Dataobjectclassification) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.sducloud.jpa.sduclouddb.Dataobjectclassification[ id=" + id + " ]";
    }
    
}
