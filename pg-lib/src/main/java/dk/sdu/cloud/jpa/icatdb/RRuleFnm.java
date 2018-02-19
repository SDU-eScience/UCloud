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
@Table(name = "r_rule_fnm")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RRuleFnm.findAll", query = "SELECT r FROM RRuleFnm r")
    , @NamedQuery(name = "RRuleFnm.findByFnmId", query = "SELECT r FROM RRuleFnm r WHERE r.fnmId = :fnmId")
    , @NamedQuery(name = "RRuleFnm.findByFnmVersion", query = "SELECT r FROM RRuleFnm r WHERE r.fnmVersion = :fnmVersion")
    , @NamedQuery(name = "RRuleFnm.findByFnmBaseName", query = "SELECT r FROM RRuleFnm r WHERE r.fnmBaseName = :fnmBaseName")
    , @NamedQuery(name = "RRuleFnm.findByFnmExtFuncName", query = "SELECT r FROM RRuleFnm r WHERE r.fnmExtFuncName = :fnmExtFuncName")
    , @NamedQuery(name = "RRuleFnm.findByFnmIntFuncName", query = "SELECT r FROM RRuleFnm r WHERE r.fnmIntFuncName = :fnmIntFuncName")
    , @NamedQuery(name = "RRuleFnm.findByFnmStatus", query = "SELECT r FROM RRuleFnm r WHERE r.fnmStatus = :fnmStatus")
    , @NamedQuery(name = "RRuleFnm.findByFnmOwnerName", query = "SELECT r FROM RRuleFnm r WHERE r.fnmOwnerName = :fnmOwnerName")
    , @NamedQuery(name = "RRuleFnm.findByFnmOwnerZone", query = "SELECT r FROM RRuleFnm r WHERE r.fnmOwnerZone = :fnmOwnerZone")
    , @NamedQuery(name = "RRuleFnm.findByRComment", query = "SELECT r FROM RRuleFnm r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RRuleFnm.findByCreateTs", query = "SELECT r FROM RRuleFnm r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RRuleFnm.findByModifyTs", query = "SELECT r FROM RRuleFnm r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RRuleFnm.findById", query = "SELECT r FROM RRuleFnm r WHERE r.id = :id")})
public class RRuleFnm implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "fnm_id")
    private long fnmId;
    @Column(name = "fnm_version")
    private String fnmVersion;
    @Basic(optional = false)
    @Column(name = "fnm_base_name")
    private String fnmBaseName;
    @Basic(optional = false)
    @Column(name = "fnm_ext_func_name")
    private String fnmExtFuncName;
    @Basic(optional = false)
    @Column(name = "fnm_int_func_name")
    private String fnmIntFuncName;
    @Column(name = "fnm_status")
    private Integer fnmStatus;
    @Basic(optional = false)
    @Column(name = "fnm_owner_name")
    private String fnmOwnerName;
    @Basic(optional = false)
    @Column(name = "fnm_owner_zone")
    private String fnmOwnerZone;
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

    public RRuleFnm() {
    }

    public RRuleFnm(Integer id) {
        this.id = id;
    }

    public RRuleFnm(Integer id, long fnmId, String fnmBaseName, String fnmExtFuncName, String fnmIntFuncName, String fnmOwnerName, String fnmOwnerZone) {
        this.id = id;
        this.fnmId = fnmId;
        this.fnmBaseName = fnmBaseName;
        this.fnmExtFuncName = fnmExtFuncName;
        this.fnmIntFuncName = fnmIntFuncName;
        this.fnmOwnerName = fnmOwnerName;
        this.fnmOwnerZone = fnmOwnerZone;
    }

    public long getFnmId() {
        return fnmId;
    }

    public void setFnmId(long fnmId) {
        this.fnmId = fnmId;
    }

    public String getFnmVersion() {
        return fnmVersion;
    }

    public void setFnmVersion(String fnmVersion) {
        this.fnmVersion = fnmVersion;
    }

    public String getFnmBaseName() {
        return fnmBaseName;
    }

    public void setFnmBaseName(String fnmBaseName) {
        this.fnmBaseName = fnmBaseName;
    }

    public String getFnmExtFuncName() {
        return fnmExtFuncName;
    }

    public void setFnmExtFuncName(String fnmExtFuncName) {
        this.fnmExtFuncName = fnmExtFuncName;
    }

    public String getFnmIntFuncName() {
        return fnmIntFuncName;
    }

    public void setFnmIntFuncName(String fnmIntFuncName) {
        this.fnmIntFuncName = fnmIntFuncName;
    }

    public Integer getFnmStatus() {
        return fnmStatus;
    }

    public void setFnmStatus(Integer fnmStatus) {
        this.fnmStatus = fnmStatus;
    }

    public String getFnmOwnerName() {
        return fnmOwnerName;
    }

    public void setFnmOwnerName(String fnmOwnerName) {
        this.fnmOwnerName = fnmOwnerName;
    }

    public String getFnmOwnerZone() {
        return fnmOwnerZone;
    }

    public void setFnmOwnerZone(String fnmOwnerZone) {
        this.fnmOwnerZone = fnmOwnerZone;
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
        if (!(object instanceof RRuleFnm)) {
            return false;
        }
        RRuleFnm other = (RRuleFnm) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RRuleFnm[ id=" + id + " ]";
    }
    
}
