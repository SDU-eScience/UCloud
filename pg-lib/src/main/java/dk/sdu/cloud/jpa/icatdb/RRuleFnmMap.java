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
@Table(name = "r_rule_fnm_map")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RRuleFnmMap.findAll", query = "SELECT r FROM RRuleFnmMap r")
    , @NamedQuery(name = "RRuleFnmMap.findByMapFnmVersion", query = "SELECT r FROM RRuleFnmMap r WHERE r.mapFnmVersion = :mapFnmVersion")
    , @NamedQuery(name = "RRuleFnmMap.findByMapFnmBaseName", query = "SELECT r FROM RRuleFnmMap r WHERE r.mapFnmBaseName = :mapFnmBaseName")
    , @NamedQuery(name = "RRuleFnmMap.findByFnmId", query = "SELECT r FROM RRuleFnmMap r WHERE r.fnmId = :fnmId")
    , @NamedQuery(name = "RRuleFnmMap.findByMapOwnerName", query = "SELECT r FROM RRuleFnmMap r WHERE r.mapOwnerName = :mapOwnerName")
    , @NamedQuery(name = "RRuleFnmMap.findByMapOwnerZone", query = "SELECT r FROM RRuleFnmMap r WHERE r.mapOwnerZone = :mapOwnerZone")
    , @NamedQuery(name = "RRuleFnmMap.findByRComment", query = "SELECT r FROM RRuleFnmMap r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RRuleFnmMap.findByCreateTs", query = "SELECT r FROM RRuleFnmMap r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RRuleFnmMap.findByModifyTs", query = "SELECT r FROM RRuleFnmMap r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RRuleFnmMap.findById", query = "SELECT r FROM RRuleFnmMap r WHERE r.id = :id")})
public class RRuleFnmMap implements Serializable {

    private static final long serialVersionUID = 1L;
    @Column(name = "map_fnm_version")
    private String mapFnmVersion;
    @Basic(optional = false)
    @Column(name = "map_fnm_base_name")
    private String mapFnmBaseName;
    @Basic(optional = false)
    @Column(name = "fnm_id")
    private long fnmId;
    @Basic(optional = false)
    @Column(name = "map_owner_name")
    private String mapOwnerName;
    @Basic(optional = false)
    @Column(name = "map_owner_zone")
    private String mapOwnerZone;
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

    public RRuleFnmMap() {
    }

    public RRuleFnmMap(Integer id) {
        this.id = id;
    }

    public RRuleFnmMap(Integer id, String mapFnmBaseName, long fnmId, String mapOwnerName, String mapOwnerZone) {
        this.id = id;
        this.mapFnmBaseName = mapFnmBaseName;
        this.fnmId = fnmId;
        this.mapOwnerName = mapOwnerName;
        this.mapOwnerZone = mapOwnerZone;
    }

    public String getMapFnmVersion() {
        return mapFnmVersion;
    }

    public void setMapFnmVersion(String mapFnmVersion) {
        this.mapFnmVersion = mapFnmVersion;
    }

    public String getMapFnmBaseName() {
        return mapFnmBaseName;
    }

    public void setMapFnmBaseName(String mapFnmBaseName) {
        this.mapFnmBaseName = mapFnmBaseName;
    }

    public long getFnmId() {
        return fnmId;
    }

    public void setFnmId(long fnmId) {
        this.fnmId = fnmId;
    }

    public String getMapOwnerName() {
        return mapOwnerName;
    }

    public void setMapOwnerName(String mapOwnerName) {
        this.mapOwnerName = mapOwnerName;
    }

    public String getMapOwnerZone() {
        return mapOwnerZone;
    }

    public void setMapOwnerZone(String mapOwnerZone) {
        this.mapOwnerZone = mapOwnerZone;
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
        if (!(object instanceof RRuleFnmMap)) {
            return false;
        }
        RRuleFnmMap other = (RRuleFnmMap) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RRuleFnmMap[ id=" + id + " ]";
    }
    
}
