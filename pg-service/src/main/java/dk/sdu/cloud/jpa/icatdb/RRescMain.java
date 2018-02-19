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
@Table(name = "r_resc_main")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RRescMain.findAll", query = "SELECT r FROM RRescMain r")
    , @NamedQuery(name = "RRescMain.findByRescId", query = "SELECT r FROM RRescMain r WHERE r.rescId = :rescId")
    , @NamedQuery(name = "RRescMain.findByRescName", query = "SELECT r FROM RRescMain r WHERE r.rescName = :rescName")
    , @NamedQuery(name = "RRescMain.findByZoneName", query = "SELECT r FROM RRescMain r WHERE r.zoneName = :zoneName")
    , @NamedQuery(name = "RRescMain.findByRescTypeName", query = "SELECT r FROM RRescMain r WHERE r.rescTypeName = :rescTypeName")
    , @NamedQuery(name = "RRescMain.findByRescClassName", query = "SELECT r FROM RRescMain r WHERE r.rescClassName = :rescClassName")
    , @NamedQuery(name = "RRescMain.findByRescNet", query = "SELECT r FROM RRescMain r WHERE r.rescNet = :rescNet")
    , @NamedQuery(name = "RRescMain.findByRescDefPath", query = "SELECT r FROM RRescMain r WHERE r.rescDefPath = :rescDefPath")
    , @NamedQuery(name = "RRescMain.findByFreeSpace", query = "SELECT r FROM RRescMain r WHERE r.freeSpace = :freeSpace")
    , @NamedQuery(name = "RRescMain.findByFreeSpaceTs", query = "SELECT r FROM RRescMain r WHERE r.freeSpaceTs = :freeSpaceTs")
    , @NamedQuery(name = "RRescMain.findByRescInfo", query = "SELECT r FROM RRescMain r WHERE r.rescInfo = :rescInfo")
    , @NamedQuery(name = "RRescMain.findByRComment", query = "SELECT r FROM RRescMain r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RRescMain.findByRescStatus", query = "SELECT r FROM RRescMain r WHERE r.rescStatus = :rescStatus")
    , @NamedQuery(name = "RRescMain.findByCreateTs", query = "SELECT r FROM RRescMain r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RRescMain.findByModifyTs", query = "SELECT r FROM RRescMain r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RRescMain.findByRescChildren", query = "SELECT r FROM RRescMain r WHERE r.rescChildren = :rescChildren")
    , @NamedQuery(name = "RRescMain.findByRescContext", query = "SELECT r FROM RRescMain r WHERE r.rescContext = :rescContext")
    , @NamedQuery(name = "RRescMain.findByRescParent", query = "SELECT r FROM RRescMain r WHERE r.rescParent = :rescParent")
    , @NamedQuery(name = "RRescMain.findByRescObjcount", query = "SELECT r FROM RRescMain r WHERE r.rescObjcount = :rescObjcount")
    , @NamedQuery(name = "RRescMain.findByRescParentContext", query = "SELECT r FROM RRescMain r WHERE r.rescParentContext = :rescParentContext")
    , @NamedQuery(name = "RRescMain.findById", query = "SELECT r FROM RRescMain r WHERE r.id = :id")})
public class RRescMain implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "resc_id")
    private long rescId;
    @Basic(optional = false)
    @Column(name = "resc_name")
    private String rescName;
    @Basic(optional = false)
    @Column(name = "zone_name")
    private String zoneName;
    @Basic(optional = false)
    @Column(name = "resc_type_name")
    private String rescTypeName;
    @Basic(optional = false)
    @Column(name = "resc_class_name")
    private String rescClassName;
    @Basic(optional = false)
    @Column(name = "resc_net")
    private String rescNet;
    @Basic(optional = false)
    @Column(name = "resc_def_path")
    private String rescDefPath;
    @Column(name = "free_space")
    private String freeSpace;
    @Column(name = "free_space_ts")
    private String freeSpaceTs;
    @Column(name = "resc_info")
    private String rescInfo;
    @Column(name = "r_comment")
    private String rComment;
    @Column(name = "resc_status")
    private String rescStatus;
    @Column(name = "create_ts")
    private String createTs;
    @Column(name = "modify_ts")
    private String modifyTs;
    @Column(name = "resc_children")
    private String rescChildren;
    @Column(name = "resc_context")
    private String rescContext;
    @Column(name = "resc_parent")
    private String rescParent;
    @Column(name = "resc_objcount")
    private BigInteger rescObjcount;
    @Column(name = "resc_parent_context")
    private String rescParentContext;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RRescMain() {
    }

    public RRescMain(Integer id) {
        this.id = id;
    }

    public RRescMain(Integer id, long rescId, String rescName, String zoneName, String rescTypeName, String rescClassName, String rescNet, String rescDefPath) {
        this.id = id;
        this.rescId = rescId;
        this.rescName = rescName;
        this.zoneName = zoneName;
        this.rescTypeName = rescTypeName;
        this.rescClassName = rescClassName;
        this.rescNet = rescNet;
        this.rescDefPath = rescDefPath;
    }

    public long getRescId() {
        return rescId;
    }

    public void setRescId(long rescId) {
        this.rescId = rescId;
    }

    public String getRescName() {
        return rescName;
    }

    public void setRescName(String rescName) {
        this.rescName = rescName;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getRescTypeName() {
        return rescTypeName;
    }

    public void setRescTypeName(String rescTypeName) {
        this.rescTypeName = rescTypeName;
    }

    public String getRescClassName() {
        return rescClassName;
    }

    public void setRescClassName(String rescClassName) {
        this.rescClassName = rescClassName;
    }

    public String getRescNet() {
        return rescNet;
    }

    public void setRescNet(String rescNet) {
        this.rescNet = rescNet;
    }

    public String getRescDefPath() {
        return rescDefPath;
    }

    public void setRescDefPath(String rescDefPath) {
        this.rescDefPath = rescDefPath;
    }

    public String getFreeSpace() {
        return freeSpace;
    }

    public void setFreeSpace(String freeSpace) {
        this.freeSpace = freeSpace;
    }

    public String getFreeSpaceTs() {
        return freeSpaceTs;
    }

    public void setFreeSpaceTs(String freeSpaceTs) {
        this.freeSpaceTs = freeSpaceTs;
    }

    public String getRescInfo() {
        return rescInfo;
    }

    public void setRescInfo(String rescInfo) {
        this.rescInfo = rescInfo;
    }

    public String getRComment() {
        return rComment;
    }

    public void setRComment(String rComment) {
        this.rComment = rComment;
    }

    public String getRescStatus() {
        return rescStatus;
    }

    public void setRescStatus(String rescStatus) {
        this.rescStatus = rescStatus;
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

    public String getRescChildren() {
        return rescChildren;
    }

    public void setRescChildren(String rescChildren) {
        this.rescChildren = rescChildren;
    }

    public String getRescContext() {
        return rescContext;
    }

    public void setRescContext(String rescContext) {
        this.rescContext = rescContext;
    }

    public String getRescParent() {
        return rescParent;
    }

    public void setRescParent(String rescParent) {
        this.rescParent = rescParent;
    }

    public BigInteger getRescObjcount() {
        return rescObjcount;
    }

    public void setRescObjcount(BigInteger rescObjcount) {
        this.rescObjcount = rescObjcount;
    }

    public String getRescParentContext() {
        return rescParentContext;
    }

    public void setRescParentContext(String rescParentContext) {
        this.rescParentContext = rescParentContext;
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
        if (!(object instanceof RRescMain)) {
            return false;
        }
        RRescMain other = (RRescMain) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RRescMain[ id=" + id + " ]";
    }
    
}
