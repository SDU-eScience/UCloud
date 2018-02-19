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
@Table(name = "r_rule_base_map")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RRuleBaseMap.findAll", query = "SELECT r FROM RRuleBaseMap r")
    , @NamedQuery(name = "RRuleBaseMap.findByMapVersion", query = "SELECT r FROM RRuleBaseMap r WHERE r.mapVersion = :mapVersion")
    , @NamedQuery(name = "RRuleBaseMap.findByMapBaseName", query = "SELECT r FROM RRuleBaseMap r WHERE r.mapBaseName = :mapBaseName")
    , @NamedQuery(name = "RRuleBaseMap.findByMapPriority", query = "SELECT r FROM RRuleBaseMap r WHERE r.mapPriority = :mapPriority")
    , @NamedQuery(name = "RRuleBaseMap.findByRuleId", query = "SELECT r FROM RRuleBaseMap r WHERE r.ruleId = :ruleId")
    , @NamedQuery(name = "RRuleBaseMap.findByMapOwnerName", query = "SELECT r FROM RRuleBaseMap r WHERE r.mapOwnerName = :mapOwnerName")
    , @NamedQuery(name = "RRuleBaseMap.findByMapOwnerZone", query = "SELECT r FROM RRuleBaseMap r WHERE r.mapOwnerZone = :mapOwnerZone")
    , @NamedQuery(name = "RRuleBaseMap.findByRComment", query = "SELECT r FROM RRuleBaseMap r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RRuleBaseMap.findByCreateTs", query = "SELECT r FROM RRuleBaseMap r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RRuleBaseMap.findByModifyTs", query = "SELECT r FROM RRuleBaseMap r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RRuleBaseMap.findById", query = "SELECT r FROM RRuleBaseMap r WHERE r.id = :id")})
public class RRuleBaseMap implements Serializable {

    private static final long serialVersionUID = 1L;
    @Column(name = "map_version")
    private String mapVersion;
    @Basic(optional = false)
    @Column(name = "map_base_name")
    private String mapBaseName;
    @Basic(optional = false)
    @Column(name = "map_priority")
    private int mapPriority;
    @Basic(optional = false)
    @Column(name = "rule_id")
    private long ruleId;
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

    public RRuleBaseMap() {
    }

    public RRuleBaseMap(Integer id) {
        this.id = id;
    }

    public RRuleBaseMap(Integer id, String mapBaseName, int mapPriority, long ruleId, String mapOwnerName, String mapOwnerZone) {
        this.id = id;
        this.mapBaseName = mapBaseName;
        this.mapPriority = mapPriority;
        this.ruleId = ruleId;
        this.mapOwnerName = mapOwnerName;
        this.mapOwnerZone = mapOwnerZone;
    }

    public String getMapVersion() {
        return mapVersion;
    }

    public void setMapVersion(String mapVersion) {
        this.mapVersion = mapVersion;
    }

    public String getMapBaseName() {
        return mapBaseName;
    }

    public void setMapBaseName(String mapBaseName) {
        this.mapBaseName = mapBaseName;
    }

    public int getMapPriority() {
        return mapPriority;
    }

    public void setMapPriority(int mapPriority) {
        this.mapPriority = mapPriority;
    }

    public long getRuleId() {
        return ruleId;
    }

    public void setRuleId(long ruleId) {
        this.ruleId = ruleId;
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
        if (!(object instanceof RRuleBaseMap)) {
            return false;
        }
        RRuleBaseMap other = (RRuleBaseMap) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RRuleBaseMap[ id=" + id + " ]";
    }
    
}
