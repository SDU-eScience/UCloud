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
@Table(name = "dataobject_directory_relation")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "DataobjectDirectoryRelation.findAll", query = "SELECT d FROM DataobjectDirectoryRelation d")
    , @NamedQuery(name = "DataobjectDirectoryRelation.findById", query = "SELECT d FROM DataobjectDirectoryRelation d WHERE d.id = :id")
    , @NamedQuery(name = "DataobjectDirectoryRelation.findByMarkedfordelete", query = "SELECT d FROM DataobjectDirectoryRelation d WHERE d.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "DataobjectDirectoryRelation.findByModifiedTs", query = "SELECT d FROM DataobjectDirectoryRelation d WHERE d.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "DataobjectDirectoryRelation.findByCreatedTs", query = "SELECT d FROM DataobjectDirectoryRelation d WHERE d.createdTs = :createdTs")})
public class DataobjectDirectoryRelation implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
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
    @JoinColumn(name = "dataobjectrefid", referencedColumnName = "id")
    @ManyToOne
    private Dataobject dataobjectrefid;
    @JoinColumn(name = "dataobjectdirectoryrefid", referencedColumnName = "id")
    @ManyToOne
    private DataobjectDirectory dataobjectdirectoryrefid;

    public DataobjectDirectoryRelation() {
    }

    public DataobjectDirectoryRelation(Integer id) {
        this.id = id;
    }

    public DataobjectDirectoryRelation(Integer id, Date modifiedTs, Date createdTs) {
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

    public Dataobject getDataobjectrefid() {
        return dataobjectrefid;
    }

    public void setDataobjectrefid(Dataobject dataobjectrefid) {
        this.dataobjectrefid = dataobjectrefid;
    }

    public DataobjectDirectory getDataobjectdirectoryrefid() {
        return dataobjectdirectoryrefid;
    }

    public void setDataobjectdirectoryrefid(DataobjectDirectory dataobjectdirectoryrefid) {
        this.dataobjectdirectoryrefid = dataobjectdirectoryrefid;
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
        if (!(object instanceof DataobjectDirectoryRelation)) {
            return false;
        }
        DataobjectDirectoryRelation other = (DataobjectDirectoryRelation) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.DataobjectDirectoryRelation[ id=" + id + " ]";
    }
    
}
