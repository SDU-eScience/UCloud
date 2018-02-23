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
@Table(name = "subsystemcommandcategory")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Subsystemcommandcategory.findAll", query = "SELECT s FROM Subsystemcommandcategory s")
    , @NamedQuery(name = "Subsystemcommandcategory.findById", query = "SELECT s FROM Subsystemcommandcategory s WHERE s.id = :id")
    , @NamedQuery(name = "Subsystemcommandcategory.findBySubsystemcommandcategorytext", query = "SELECT s FROM Subsystemcommandcategory s WHERE s.subsystemcommandcategorytext = :subsystemcommandcategorytext")
    , @NamedQuery(name = "Subsystemcommandcategory.findByMarkedfordelete", query = "SELECT s FROM Subsystemcommandcategory s WHERE s.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Subsystemcommandcategory.findByModifiedTs", query = "SELECT s FROM Subsystemcommandcategory s WHERE s.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Subsystemcommandcategory.findByCreatedTs", query = "SELECT s FROM Subsystemcommandcategory s WHERE s.createdTs = :createdTs")})
public class Subsystemcommandcategory implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "subsystemcommandcategorytext")
    private String subsystemcommandcategorytext;
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
    @OneToMany(mappedBy = "subsystemcommandcategoryrefid")
    private List<Subsystemcommand> subsystemcommandList;

    public Subsystemcommandcategory() {
    }

    public Subsystemcommandcategory(Integer id) {
        this.id = id;
    }

    public Subsystemcommandcategory(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getSubsystemcommandcategorytext() {
        return subsystemcommandcategorytext;
    }

    public void setSubsystemcommandcategorytext(String subsystemcommandcategorytext) {
        this.subsystemcommandcategorytext = subsystemcommandcategorytext;
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
    public List<Subsystemcommand> getSubsystemcommandList() {
        return subsystemcommandList;
    }

    public void setSubsystemcommandList(List<Subsystemcommand> subsystemcommandList) {
        this.subsystemcommandList = subsystemcommandList;
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
        if (!(object instanceof Subsystemcommandcategory)) {
            return false;
        }
        Subsystemcommandcategory other = (Subsystemcommandcategory) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.Subsystemcommandcategory[ id=" + id + " ]";
    }
    
}
