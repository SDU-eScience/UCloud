/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.icatdb;

import java.io.Serializable;
import java.math.BigInteger;
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
@Table(name = "r_coll_main")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RCollMain.findAll", query = "SELECT r FROM RCollMain r")
    , @NamedQuery(name = "RCollMain.findByCollId", query = "SELECT r FROM RCollMain r WHERE r.collId = :collId")
    , @NamedQuery(name = "RCollMain.findByParentCollName", query = "SELECT r FROM RCollMain r WHERE r.parentCollName = :parentCollName")
    , @NamedQuery(name = "RCollMain.findByCollName", query = "SELECT r FROM RCollMain r WHERE r.collName = :collName")
    , @NamedQuery(name = "RCollMain.findByCollOwnerName", query = "SELECT r FROM RCollMain r WHERE r.collOwnerName = :collOwnerName")
    , @NamedQuery(name = "RCollMain.findByCollOwnerZone", query = "SELECT r FROM RCollMain r WHERE r.collOwnerZone = :collOwnerZone")
    , @NamedQuery(name = "RCollMain.findByCollMapId", query = "SELECT r FROM RCollMain r WHERE r.collMapId = :collMapId")
    , @NamedQuery(name = "RCollMain.findByCollInheritance", query = "SELECT r FROM RCollMain r WHERE r.collInheritance = :collInheritance")
    , @NamedQuery(name = "RCollMain.findByCollType", query = "SELECT r FROM RCollMain r WHERE r.collType = :collType")
    , @NamedQuery(name = "RCollMain.findByCollInfo1", query = "SELECT r FROM RCollMain r WHERE r.collInfo1 = :collInfo1")
    , @NamedQuery(name = "RCollMain.findByCollInfo2", query = "SELECT r FROM RCollMain r WHERE r.collInfo2 = :collInfo2")
    , @NamedQuery(name = "RCollMain.findByCollExpiryTs", query = "SELECT r FROM RCollMain r WHERE r.collExpiryTs = :collExpiryTs")
    , @NamedQuery(name = "RCollMain.findByRComment", query = "SELECT r FROM RCollMain r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RCollMain.findByCreateTs", query = "SELECT r FROM RCollMain r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RCollMain.findByModifyTs", query = "SELECT r FROM RCollMain r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RCollMain.findById", query = "SELECT r FROM RCollMain r WHERE r.id = :id")})
public class RCollMain implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "coll_id")
    private long collId;
    @Basic(optional = false)
    @Column(name = "parent_coll_name")
    private String parentCollName;
    @Basic(optional = false)
    @Column(name = "coll_name")
    private String collName;
    @Basic(optional = false)
    @Column(name = "coll_owner_name")
    private String collOwnerName;
    @Basic(optional = false)
    @Column(name = "coll_owner_zone")
    private String collOwnerZone;
    @Column(name = "coll_map_id")
    private BigInteger collMapId;
    @Column(name = "coll_inheritance")
    private String collInheritance;
    @Column(name = "coll_type")
    private String collType;
    @Column(name = "coll_info1")
    private String collInfo1;
    @Column(name = "coll_info2")
    private String collInfo2;
    @Column(name = "coll_expiry_ts")
    private String collExpiryTs;
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

    public RCollMain() {
    }

    public RCollMain(Integer id) {
        this.id = id;
    }

    public RCollMain(Integer id, long collId, String parentCollName, String collName, String collOwnerName, String collOwnerZone) {
        this.id = id;
        this.collId = collId;
        this.parentCollName = parentCollName;
        this.collName = collName;
        this.collOwnerName = collOwnerName;
        this.collOwnerZone = collOwnerZone;
    }

    public long getCollId() {
        return collId;
    }

    public void setCollId(long collId) {
        this.collId = collId;
    }

    public String getParentCollName() {
        return parentCollName;
    }

    public void setParentCollName(String parentCollName) {
        this.parentCollName = parentCollName;
    }

    public String getCollName() {
        return collName;
    }

    public void setCollName(String collName) {
        this.collName = collName;
    }

    public String getCollOwnerName() {
        return collOwnerName;
    }

    public void setCollOwnerName(String collOwnerName) {
        this.collOwnerName = collOwnerName;
    }

    public String getCollOwnerZone() {
        return collOwnerZone;
    }

    public void setCollOwnerZone(String collOwnerZone) {
        this.collOwnerZone = collOwnerZone;
    }

    public BigInteger getCollMapId() {
        return collMapId;
    }

    public void setCollMapId(BigInteger collMapId) {
        this.collMapId = collMapId;
    }

    public String getCollInheritance() {
        return collInheritance;
    }

    public void setCollInheritance(String collInheritance) {
        this.collInheritance = collInheritance;
    }

    public String getCollType() {
        return collType;
    }

    public void setCollType(String collType) {
        this.collType = collType;
    }

    public String getCollInfo1() {
        return collInfo1;
    }

    public void setCollInfo1(String collInfo1) {
        this.collInfo1 = collInfo1;
    }

    public String getCollInfo2() {
        return collInfo2;
    }

    public void setCollInfo2(String collInfo2) {
        this.collInfo2 = collInfo2;
    }

    public String getCollExpiryTs() {
        return collExpiryTs;
    }

    public void setCollExpiryTs(String collExpiryTs) {
        this.collExpiryTs = collExpiryTs;
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
        if (!(object instanceof RCollMain)) {
            return false;
        }
        RCollMain other = (RCollMain) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RCollMain[ id=" + id + " ]";
    }
    
}
