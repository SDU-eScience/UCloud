/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.icatdb;

import java.io.Serializable;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
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
@Table(name = "r_objt_metamap")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RObjtMetamap.findAll", query = "SELECT r FROM RObjtMetamap r")
    , @NamedQuery(name = "RObjtMetamap.findByObjectId", query = "SELECT r FROM RObjtMetamap r WHERE r.objectId = :objectId")
    , @NamedQuery(name = "RObjtMetamap.findByMetaId", query = "SELECT r FROM RObjtMetamap r WHERE r.metaId = :metaId")
    , @NamedQuery(name = "RObjtMetamap.findByCreateTs", query = "SELECT r FROM RObjtMetamap r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RObjtMetamap.findByModifyTs", query = "SELECT r FROM RObjtMetamap r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RObjtMetamap.findById", query = "SELECT r FROM RObjtMetamap r WHERE r.id = :id")})
public class RObjtMetamap implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "object_id")
    private long objectId;
    @Basic(optional = false)
    @Column(name = "meta_id")
    private long metaId;
    @Column(name = "create_ts")
    private String createTs;
    @Column(name = "modify_ts")
    private String modifyTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RObjtMetamap() {
    }

    public RObjtMetamap(Integer id) {
        this.id = id;
    }

    public RObjtMetamap(Integer id, long objectId, long metaId) {
        this.id = id;
        this.objectId = objectId;
        this.metaId = metaId;
    }

    public long getObjectId() {
        return objectId;
    }

    public void setObjectId(long objectId) {
        this.objectId = objectId;
    }

    public long getMetaId() {
        return metaId;
    }

    public void setMetaId(long metaId) {
        this.metaId = metaId;
    }

    public String getCreateTs() {
        return createTs;
    }

    public void setCreateTs(String createTs) {
        this.createTs = createTs;
    }

    public String getModifyTs() {
        return modifyTs;
    }

    public void setModifyTs(String modifyTs) {
        this.modifyTs = modifyTs;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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
        if (!(object instanceof RObjtMetamap)) {
            return false;
        }
        RObjtMetamap other = (RObjtMetamap) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RObjtMetamap[ id=" + id + " ]";
    }
    
}
