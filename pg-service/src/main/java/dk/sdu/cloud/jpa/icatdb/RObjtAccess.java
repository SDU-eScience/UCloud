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
@Table(name = "r_objt_access")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RObjtAccess.findAll", query = "SELECT r FROM RObjtAccess r")
    , @NamedQuery(name = "RObjtAccess.findByObjectId", query = "SELECT r FROM RObjtAccess r WHERE r.objectId = :objectId")
    , @NamedQuery(name = "RObjtAccess.findByUserId", query = "SELECT r FROM RObjtAccess r WHERE r.userId = :userId")
    , @NamedQuery(name = "RObjtAccess.findByAccessTypeId", query = "SELECT r FROM RObjtAccess r WHERE r.accessTypeId = :accessTypeId")
    , @NamedQuery(name = "RObjtAccess.findByCreateTs", query = "SELECT r FROM RObjtAccess r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RObjtAccess.findByModifyTs", query = "SELECT r FROM RObjtAccess r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RObjtAccess.findById", query = "SELECT r FROM RObjtAccess r WHERE r.id = :id")})
public class RObjtAccess implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "object_id")
    private long objectId;
    @Basic(optional = false)
    @Column(name = "user_id")
    private long userId;
    @Basic(optional = false)
    @Column(name = "access_type_id")
    private long accessTypeId;
    @Column(name = "create_ts")
    private String createTs;
    @Column(name = "modify_ts")
    private String modifyTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RObjtAccess() {
    }

    public RObjtAccess(Integer id) {
        this.id = id;
    }

    public RObjtAccess(Integer id, long objectId, long userId, long accessTypeId) {
        this.id = id;
        this.objectId = objectId;
        this.userId = userId;
        this.accessTypeId = accessTypeId;
    }

    public long getObjectId() {
        return objectId;
    }

    public void setObjectId(long objectId) {
        this.objectId = objectId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getAccessTypeId() {
        return accessTypeId;
    }

    public void setAccessTypeId(long accessTypeId) {
        this.accessTypeId = accessTypeId;
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
        if (!(object instanceof RObjtAccess)) {
            return false;
        }
        RObjtAccess other = (RObjtAccess) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RObjtAccess[ id=" + id + " ]";
    }
    
}
