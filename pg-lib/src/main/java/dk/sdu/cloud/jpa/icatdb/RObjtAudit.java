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
@Table(name = "r_objt_audit")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RObjtAudit.findAll", query = "SELECT r FROM RObjtAudit r")
    , @NamedQuery(name = "RObjtAudit.findByObjectId", query = "SELECT r FROM RObjtAudit r WHERE r.objectId = :objectId")
    , @NamedQuery(name = "RObjtAudit.findByUserId", query = "SELECT r FROM RObjtAudit r WHERE r.userId = :userId")
    , @NamedQuery(name = "RObjtAudit.findByActionId", query = "SELECT r FROM RObjtAudit r WHERE r.actionId = :actionId")
    , @NamedQuery(name = "RObjtAudit.findByRComment", query = "SELECT r FROM RObjtAudit r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RObjtAudit.findByCreateTs", query = "SELECT r FROM RObjtAudit r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RObjtAudit.findByModifyTs", query = "SELECT r FROM RObjtAudit r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RObjtAudit.findById", query = "SELECT r FROM RObjtAudit r WHERE r.id = :id")})
public class RObjtAudit implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "object_id")
    private long objectId;
    @Basic(optional = false)
    @Column(name = "user_id")
    private long userId;
    @Basic(optional = false)
    @Column(name = "action_id")
    private long actionId;
    @Column(name = "r_comment")
    private String rComment;
    @Column(name = "create_ts")
    private String createTs;
    @Column(name = "modify_ts")
    private String modifyTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RObjtAudit() {
    }

    public RObjtAudit(Integer id) {
        this.id = id;
    }

    public RObjtAudit(Integer id, long objectId, long userId, long actionId) {
        this.id = id;
        this.objectId = objectId;
        this.userId = userId;
        this.actionId = actionId;
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

    public long getActionId() {
        return actionId;
    }

    public void setActionId(long actionId) {
        this.actionId = actionId;
    }

    public String getRComment() {
        return rComment;
    }

    public void setRComment(String rComment) {
        this.rComment = rComment;
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
        if (!(object instanceof RObjtAudit)) {
            return false;
        }
        RObjtAudit other = (RObjtAudit) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RObjtAudit[ id=" + id + " ]";
    }
    
}
