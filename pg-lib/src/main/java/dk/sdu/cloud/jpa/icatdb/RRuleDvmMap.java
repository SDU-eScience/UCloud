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
@Table(name = "r_rule_dvm_map")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RRuleDvmMap.findAll", query = "SELECT r FROM RRuleDvmMap r")
    , @NamedQuery(name = "RRuleDvmMap.findByMapDvmVersion", query = "SELECT r FROM RRuleDvmMap r WHERE r.mapDvmVersion = :mapDvmVersion")
    , @NamedQuery(name = "RRuleDvmMap.findByMapDvmBaseName", query = "SELECT r FROM RRuleDvmMap r WHERE r.mapDvmBaseName = :mapDvmBaseName")
    , @NamedQuery(name = "RRuleDvmMap.findByDvmId", query = "SELECT r FROM RRuleDvmMap r WHERE r.dvmId = :dvmId")
    , @NamedQuery(name = "RRuleDvmMap.findByMapOwnerName", query = "SELECT r FROM RRuleDvmMap r WHERE r.mapOwnerName = :mapOwnerName")
    , @NamedQuery(name = "RRuleDvmMap.findByMapOwnerZone", query = "SELECT r FROM RRuleDvmMap r WHERE r.mapOwnerZone = :mapOwnerZone")
    , @NamedQuery(name = "RRuleDvmMap.findByRComment", query = "SELECT r FROM RRuleDvmMap r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RRuleDvmMap.findByCreateTs", query = "SELECT r FROM RRuleDvmMap r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RRuleDvmMap.findByModifyTs", query = "SELECT r FROM RRuleDvmMap r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RRuleDvmMap.findById", query = "SELECT r FROM RRuleDvmMap r WHERE r.id = :id")})
public class RRuleDvmMap implements Serializable {

    private static final long serialVersionUID = 1L;
    @Column(name = "map_dvm_version")
    private String mapDvmVersion;
    @Basic(optional = false)
    @Column(name = "map_dvm_base_name")
    private String mapDvmBaseName;
    @Basic(optional = false)
    @Column(name = "dvm_id")
    private long dvmId;
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

    public RRuleDvmMap() {
    }

    public RRuleDvmMap(Integer id) {
        this.id = id;
    }

    public RRuleDvmMap(Integer id, String mapDvmBaseName, long dvmId, String mapOwnerName, String mapOwnerZone) {
        this.id = id;
        this.mapDvmBaseName = mapDvmBaseName;
        this.dvmId = dvmId;
        this.mapOwnerName = mapOwnerName;
        this.mapOwnerZone = mapOwnerZone;
    }

    public String getMapDvmVersion() {
        return mapDvmVersion;
    }

    public void setMapDvmVersion(String mapDvmVersion) {
        this.mapDvmVersion = mapDvmVersion;
    }

    public String getMapDvmBaseName() {
        return mapDvmBaseName;
    }

    public void setMapDvmBaseName(String mapDvmBaseName) {
        this.mapDvmBaseName = mapDvmBaseName;
    }

    public long getDvmId() {
        return dvmId;
    }

    public void setDvmId(long dvmId) {
        this.dvmId = dvmId;
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
        if (!(object instanceof RRuleDvmMap)) {
            return false;
        }
        RRuleDvmMap other = (RRuleDvmMap) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RRuleDvmMap[ id=" + id + " ]";
    }
    
}
