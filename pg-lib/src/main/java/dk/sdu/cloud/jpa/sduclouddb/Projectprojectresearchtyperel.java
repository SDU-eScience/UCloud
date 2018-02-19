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
@Table(name = "projectprojectresearchtyperel")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Projectprojectresearchtyperel.findAll", query = "SELECT p FROM Projectprojectresearchtyperel p")
    , @NamedQuery(name = "Projectprojectresearchtyperel.findById", query = "SELECT p FROM Projectprojectresearchtyperel p WHERE p.id = :id")
    , @NamedQuery(name = "Projectprojectresearchtyperel.findByActive", query = "SELECT p FROM Projectprojectresearchtyperel p WHERE p.active = :active")
    , @NamedQuery(name = "Projectprojectresearchtyperel.findByMarkedfordelete", query = "SELECT p FROM Projectprojectresearchtyperel p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Projectprojectresearchtyperel.findByModifiedTs", query = "SELECT p FROM Projectprojectresearchtyperel p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Projectprojectresearchtyperel.findByCreatedTs", query = "SELECT p FROM Projectprojectresearchtyperel p WHERE p.createdTs = :createdTs")})
public class Projectprojectresearchtyperel implements Serializable {

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
    @JoinColumn(name = "projectrefid", referencedColumnName = "id")
    @ManyToOne
    private Project projectrefid;
    @JoinColumn(name = "projectresearchtyperefid", referencedColumnName = "id")
    @ManyToOne
    private Projectresearchtype projectresearchtyperefid;

    public Projectprojectresearchtyperel() {
    }

    public Projectprojectresearchtyperel(Integer id) {
        this.id = id;
    }

    public Projectprojectresearchtyperel(Integer id, Date modifiedTs, Date createdTs) {
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

    public Project getProjectrefid() {
        return projectrefid;
    }

    public void setProjectrefid(Project projectrefid) {
        this.projectrefid = projectrefid;
    }

    public Projectresearchtype getProjectresearchtyperefid() {
        return projectresearchtyperefid;
    }

    public void setProjectresearchtyperefid(Projectresearchtype projectresearchtyperefid) {
        this.projectresearchtyperefid = projectresearchtyperefid;
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
        if (!(object instanceof Projectprojectresearchtyperel)) {
            return false;
        }
        Projectprojectresearchtyperel other = (Projectprojectresearchtyperel) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.sducloud.jpa.sduclouddb.Projectprojectresearchtyperel[ id=" + id + " ]";
    }
    
}
