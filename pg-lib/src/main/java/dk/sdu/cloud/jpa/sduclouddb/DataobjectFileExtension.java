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
@Table(name = "dataobject_file_extension")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "DataobjectFileExtension.findAll", query = "SELECT d FROM DataobjectFileExtension d")
    , @NamedQuery(name = "DataobjectFileExtension.findById", query = "SELECT d FROM DataobjectFileExtension d WHERE d.id = :id")
    , @NamedQuery(name = "DataobjectFileExtension.findByFileextensionname", query = "SELECT d FROM DataobjectFileExtension d WHERE d.fileextensionname = :fileextensionname")
    , @NamedQuery(name = "DataobjectFileExtension.findByActive", query = "SELECT d FROM DataobjectFileExtension d WHERE d.active = :active")
    , @NamedQuery(name = "DataobjectFileExtension.findByMarkedfordelete", query = "SELECT d FROM DataobjectFileExtension d WHERE d.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "DataobjectFileExtension.findByModifiedTs", query = "SELECT d FROM DataobjectFileExtension d WHERE d.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "DataobjectFileExtension.findByCreatedTs", query = "SELECT d FROM DataobjectFileExtension d WHERE d.createdTs = :createdTs")
    , @NamedQuery(name = "DataobjectFileExtension.findByMimetype", query = "SELECT d FROM DataobjectFileExtension d WHERE d.mimetype = :mimetype")
    , @NamedQuery(name = "DataobjectFileExtension.findByFileextensiondesc", query = "SELECT d FROM DataobjectFileExtension d WHERE d.fileextensiondesc = :fileextensiondesc")})
public class DataobjectFileExtension implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "fileextensionname")
    private String fileextensionname;
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
    @Column(name = "mimetype")
    private String mimetype;
    @Column(name = "fileextensiondesc")
    private String fileextensiondesc;

    public DataobjectFileExtension() {
    }

    public DataobjectFileExtension(Integer id) {
        this.id = id;
    }

    public DataobjectFileExtension(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getFileextensionname() {
        return fileextensionname;
    }

    public void setFileextensionname(String fileextensionname) {
        this.fileextensionname = fileextensionname;
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

    public String getMimetype() {
        return mimetype;
    }

    public void setMimetype(String mimetype) {
        this.mimetype = mimetype;
    }

    public String getFileextensiondesc() {
        return fileextensiondesc;
    }

    public void setFileextensiondesc(String fileextensiondesc) {
        this.fileextensiondesc = fileextensiondesc;
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
        if (!(object instanceof DataobjectFileExtension)) {
            return false;
        }
        DataobjectFileExtension other = (DataobjectFileExtension) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.DataobjectFileExtension[ id=" + id + " ]";
    }
    
}
