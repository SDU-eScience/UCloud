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
@Table(name = "r_rule_dvm")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RRuleDvm.findAll", query = "SELECT r FROM RRuleDvm r")
    , @NamedQuery(name = "RRuleDvm.findByDvmId", query = "SELECT r FROM RRuleDvm r WHERE r.dvmId = :dvmId")
    , @NamedQuery(name = "RRuleDvm.findByDvmVersion", query = "SELECT r FROM RRuleDvm r WHERE r.dvmVersion = :dvmVersion")
    , @NamedQuery(name = "RRuleDvm.findByDvmBaseName", query = "SELECT r FROM RRuleDvm r WHERE r.dvmBaseName = :dvmBaseName")
    , @NamedQuery(name = "RRuleDvm.findByDvmExtVarName", query = "SELECT r FROM RRuleDvm r WHERE r.dvmExtVarName = :dvmExtVarName")
    , @NamedQuery(name = "RRuleDvm.findByDvmCondition", query = "SELECT r FROM RRuleDvm r WHERE r.dvmCondition = :dvmCondition")
    , @NamedQuery(name = "RRuleDvm.findByDvmIntMapPath", query = "SELECT r FROM RRuleDvm r WHERE r.dvmIntMapPath = :dvmIntMapPath")
    , @NamedQuery(name = "RRuleDvm.findByDvmStatus", query = "SELECT r FROM RRuleDvm r WHERE r.dvmStatus = :dvmStatus")
    , @NamedQuery(name = "RRuleDvm.findByDvmOwnerName", query = "SELECT r FROM RRuleDvm r WHERE r.dvmOwnerName = :dvmOwnerName")
    , @NamedQuery(name = "RRuleDvm.findByDvmOwnerZone", query = "SELECT r FROM RRuleDvm r WHERE r.dvmOwnerZone = :dvmOwnerZone")
    , @NamedQuery(name = "RRuleDvm.findByRComment", query = "SELECT r FROM RRuleDvm r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RRuleDvm.findByCreateTs", query = "SELECT r FROM RRuleDvm r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RRuleDvm.findByModifyTs", query = "SELECT r FROM RRuleDvm r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RRuleDvm.findById", query = "SELECT r FROM RRuleDvm r WHERE r.id = :id")})
public class RRuleDvm implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "dvm_id")
    private long dvmId;
    @Column(name = "dvm_version")
    private String dvmVersion;
    @Basic(optional = false)
    @Column(name = "dvm_base_name")
    private String dvmBaseName;
    @Basic(optional = false)
    @Column(name = "dvm_ext_var_name")
    private String dvmExtVarName;
    @Column(name = "dvm_condition")
    private String dvmCondition;
    @Basic(optional = false)
    @Column(name = "dvm_int_map_path")
    private String dvmIntMapPath;
    @Column(name = "dvm_status")
    private Integer dvmStatus;
    @Basic(optional = false)
    @Column(name = "dvm_owner_name")
    private String dvmOwnerName;
    @Basic(optional = false)
    @Column(name = "dvm_owner_zone")
    private String dvmOwnerZone;
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

    public RRuleDvm() {
    }

    public RRuleDvm(Integer id) {
        this.id = id;
    }

    public RRuleDvm(Integer id, long dvmId, String dvmBaseName, String dvmExtVarName, String dvmIntMapPath, String dvmOwnerName, String dvmOwnerZone) {
        this.id = id;
        this.dvmId = dvmId;
        this.dvmBaseName = dvmBaseName;
        this.dvmExtVarName = dvmExtVarName;
        this.dvmIntMapPath = dvmIntMapPath;
        this.dvmOwnerName = dvmOwnerName;
        this.dvmOwnerZone = dvmOwnerZone;
    }

    public long getDvmId() {
        return dvmId;
    }

    public void setDvmId(long dvmId) {
        this.dvmId = dvmId;
    }

    public String getDvmVersion() {
        return dvmVersion;
    }

    public void setDvmVersion(String dvmVersion) {
        this.dvmVersion = dvmVersion;
    }

    public String getDvmBaseName() {
        return dvmBaseName;
    }

    public void setDvmBaseName(String dvmBaseName) {
        this.dvmBaseName = dvmBaseName;
    }

    public String getDvmExtVarName() {
        return dvmExtVarName;
    }

    public void setDvmExtVarName(String dvmExtVarName) {
        this.dvmExtVarName = dvmExtVarName;
    }

    public String getDvmCondition() {
        return dvmCondition;
    }

    public void setDvmCondition(String dvmCondition) {
        this.dvmCondition = dvmCondition;
    }

    public String getDvmIntMapPath() {
        return dvmIntMapPath;
    }

    public void setDvmIntMapPath(String dvmIntMapPath) {
        this.dvmIntMapPath = dvmIntMapPath;
    }

    public Integer getDvmStatus() {
        return dvmStatus;
    }

    public void setDvmStatus(Integer dvmStatus) {
        this.dvmStatus = dvmStatus;
    }

    public String getDvmOwnerName() {
        return dvmOwnerName;
    }

    public void setDvmOwnerName(String dvmOwnerName) {
        this.dvmOwnerName = dvmOwnerName;
    }

    public String getDvmOwnerZone() {
        return dvmOwnerZone;
    }

    public void setDvmOwnerZone(String dvmOwnerZone) {
        this.dvmOwnerZone = dvmOwnerZone;
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
        if (!(object instanceof RRuleDvm)) {
            return false;
        }
        RRuleDvm other = (RRuleDvm) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RRuleDvm[ id=" + id + " ]";
    }
    
}
