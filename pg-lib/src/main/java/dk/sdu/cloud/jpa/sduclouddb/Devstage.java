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
@Table(name = "devstage")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Devstage.findAll", query = "SELECT d FROM Devstage d")
    , @NamedQuery(name = "Devstage.findById", query = "SELECT d FROM Devstage d WHERE d.id = :id")
    , @NamedQuery(name = "Devstage.findByDevstagename", query = "SELECT d FROM Devstage d WHERE d.devstagename = :devstagename")
    , @NamedQuery(name = "Devstage.findByMarkedfordelete", query = "SELECT d FROM Devstage d WHERE d.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Devstage.findByModifiedTs", query = "SELECT d FROM Devstage d WHERE d.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Devstage.findByCreatedTs", query = "SELECT d FROM Devstage d WHERE d.createdTs = :createdTs")})
public class Devstage implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "devstagename")
    private String devstagename;
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
    @OneToMany(mappedBy = "devstagerefid")
    private List<Software> softwareList;

    public Devstage() {
    }

    public Devstage(Integer id) {
        this.id = id;
    }

    public Devstage(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getDevstagename() {
        return devstagename;
    }

    public void setDevstagename(String devstagename) {
        this.devstagename = devstagename;
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
    public List<Software> getSoftwareList() {
        return softwareList;
    }

    public void setSoftwareList(List<Software> softwareList) {
        this.softwareList = softwareList;
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
        if (!(object instanceof Devstage)) {
            return false;
        }
        Devstage other = (Devstage) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return "Devstage{" +
                "id=" + id +
                ", devstagename='" + devstagename + '\'' +
                ", markedfordelete=" + markedfordelete +
                ", modifiedTs=" + modifiedTs +
                ", createdTs=" + createdTs +
                ", softwareList=" + softwareList +
                '}';
    }
}
