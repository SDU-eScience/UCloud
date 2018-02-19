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
@Table(name = "r_user_group")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RUserGroup.findAll", query = "SELECT r FROM RUserGroup r")
    , @NamedQuery(name = "RUserGroup.findByGroupUserId", query = "SELECT r FROM RUserGroup r WHERE r.groupUserId = :groupUserId")
    , @NamedQuery(name = "RUserGroup.findByUserId", query = "SELECT r FROM RUserGroup r WHERE r.userId = :userId")
    , @NamedQuery(name = "RUserGroup.findByCreateTs", query = "SELECT r FROM RUserGroup r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RUserGroup.findByModifyTs", query = "SELECT r FROM RUserGroup r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RUserGroup.findById", query = "SELECT r FROM RUserGroup r WHERE r.id = :id")})
public class RUserGroup implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "group_user_id")
    private long groupUserId;
    @Basic(optional = false)
    @Column(name = "user_id")
    private long userId;
    @Column(name = "create_ts")
    private String createTs;
    @Column(name = "modify_ts")
    private String modifyTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RUserGroup() {
    }

    public RUserGroup(Integer id) {
        this.id = id;
    }

    public RUserGroup(Integer id, long groupUserId, long userId) {
        this.id = id;
        this.groupUserId = groupUserId;
        this.userId = userId;
    }

    public long getGroupUserId() {
        return groupUserId;
    }

    public void setGroupUserId(long groupUserId) {
        this.groupUserId = groupUserId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
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
        if (!(object instanceof RUserGroup)) {
            return false;
        }
        RUserGroup other = (RUserGroup) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RUserGroup[ id=" + id + " ]";
    }
    
}
