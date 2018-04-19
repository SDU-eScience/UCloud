/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author bjhj
 */
@Entity
@Table(name = "uploaddescriptions")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Uploaddescriptions.findAll", query = "SELECT u FROM Uploaddescriptions u")
    , @NamedQuery(name = "Uploaddescriptions.findById", query = "SELECT u FROM Uploaddescriptions u WHERE u.id = :id")
    , @NamedQuery(name = "Uploaddescriptions.findBySizeInBytes", query = "SELECT u FROM Uploaddescriptions u WHERE u.sizeInBytes = :sizeInBytes")
    , @NamedQuery(name = "Uploaddescriptions.findByOwner", query = "SELECT u FROM Uploaddescriptions u WHERE u.owner = :owner")
    , @NamedQuery(name = "Uploaddescriptions.findByZone", query = "SELECT u FROM Uploaddescriptions u WHERE u.zone = :zone")
    , @NamedQuery(name = "Uploaddescriptions.findByTargetCollection", query = "SELECT u FROM Uploaddescriptions u WHERE u.targetCollection = :targetCollection")
    , @NamedQuery(name = "Uploaddescriptions.findByTargetName", query = "SELECT u FROM Uploaddescriptions u WHERE u.targetName = :targetName")
    , @NamedQuery(name = "Uploaddescriptions.findByDoChecksum", query = "SELECT u FROM Uploaddescriptions u WHERE u.doChecksum = :doChecksum")
    , @NamedQuery(name = "Uploaddescriptions.findBySensitive", query = "SELECT u FROM Uploaddescriptions u WHERE u.sensitive = :sensitive")
    , @NamedQuery(name = "Uploaddescriptions.findBySavedAs", query = "SELECT u FROM Uploaddescriptions u WHERE u.savedAs = :savedAs")})
public class Uploaddescriptions implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @Column(name = "id")
    private String id;
    @Column(name = "size_in_bytes")
    private Integer sizeInBytes;
    @Column(name = "owner")
    private String owner;
    @Column(name = "zone")
    private String zone;
    @Column(name = "target_collection")
    private String targetCollection;
    @Column(name = "target_name")
    private String targetName;
    @Column(name = "do_checksum")
    private Boolean doChecksum;
    @Column(name = "sensitive")
    private Boolean sensitive;
    @Column(name = "saved_as")
    private String savedAs;

    public Uploaddescriptions() {
    }

    public Uploaddescriptions(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getSizeInBytes() {
        return sizeInBytes;
    }

    public void setSizeInBytes(Integer sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getTargetCollection() {
        return targetCollection;
    }

    public void setTargetCollection(String targetCollection) {
        this.targetCollection = targetCollection;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public Boolean getDoChecksum() {
        return doChecksum;
    }

    public void setDoChecksum(Boolean doChecksum) {
        this.doChecksum = doChecksum;
    }

    public Boolean getSensitive() {
        return sensitive;
    }

    public void setSensitive(Boolean sensitive) {
        this.sensitive = sensitive;
    }

    public String getSavedAs() {
        return savedAs;
    }

    public void setSavedAs(String savedAs) {
        this.savedAs = savedAs;
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
        if (!(object instanceof Uploaddescriptions)) {
            return false;
        }
        Uploaddescriptions other = (Uploaddescriptions) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.Uploaddescriptions[ id=" + id + " ]";
    }
    
}
