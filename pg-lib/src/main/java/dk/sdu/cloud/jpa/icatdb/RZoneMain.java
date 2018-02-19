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
@Table(name = "r_zone_main")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RZoneMain.findAll", query = "SELECT r FROM RZoneMain r")
    , @NamedQuery(name = "RZoneMain.findByZoneId", query = "SELECT r FROM RZoneMain r WHERE r.zoneId = :zoneId")
    , @NamedQuery(name = "RZoneMain.findByZoneName", query = "SELECT r FROM RZoneMain r WHERE r.zoneName = :zoneName")
    , @NamedQuery(name = "RZoneMain.findByZoneTypeName", query = "SELECT r FROM RZoneMain r WHERE r.zoneTypeName = :zoneTypeName")
    , @NamedQuery(name = "RZoneMain.findByZoneConnString", query = "SELECT r FROM RZoneMain r WHERE r.zoneConnString = :zoneConnString")
    , @NamedQuery(name = "RZoneMain.findByRComment", query = "SELECT r FROM RZoneMain r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RZoneMain.findByCreateTs", query = "SELECT r FROM RZoneMain r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RZoneMain.findByModifyTs", query = "SELECT r FROM RZoneMain r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RZoneMain.findById", query = "SELECT r FROM RZoneMain r WHERE r.id = :id")})
public class RZoneMain implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "zone_id")
    private long zoneId;
    @Basic(optional = false)
    @Column(name = "zone_name")
    private String zoneName;
    @Basic(optional = false)
    @Column(name = "zone_type_name")
    private String zoneTypeName;
    @Column(name = "zone_conn_string")
    private String zoneConnString;
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

    public RZoneMain() {
    }

    public RZoneMain(Integer id) {
        this.id = id;
    }

    public RZoneMain(Integer id, long zoneId, String zoneName, String zoneTypeName) {
        this.id = id;
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.zoneTypeName = zoneTypeName;
    }

    public long getZoneId() {
        return zoneId;
    }

    public void setZoneId(long zoneId) {
        this.zoneId = zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getZoneTypeName() {
        return zoneTypeName;
    }

    public void setZoneTypeName(String zoneTypeName) {
        this.zoneTypeName = zoneTypeName;
    }

    public String getZoneConnString() {
        return zoneConnString;
    }

    public void setZoneConnString(String zoneConnString) {
        this.zoneConnString = zoneConnString;
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
        if (!(object instanceof RZoneMain)) {
            return false;
        }
        RZoneMain other = (RZoneMain) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RZoneMain[ id=" + id + " ]";
    }
    
}
