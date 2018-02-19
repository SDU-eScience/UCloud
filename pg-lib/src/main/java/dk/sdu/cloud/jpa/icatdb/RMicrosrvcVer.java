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
@Table(name = "r_microsrvc_ver")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RMicrosrvcVer.findAll", query = "SELECT r FROM RMicrosrvcVer r")
    , @NamedQuery(name = "RMicrosrvcVer.findByMsrvcId", query = "SELECT r FROM RMicrosrvcVer r WHERE r.msrvcId = :msrvcId")
    , @NamedQuery(name = "RMicrosrvcVer.findByMsrvcVersion", query = "SELECT r FROM RMicrosrvcVer r WHERE r.msrvcVersion = :msrvcVersion")
    , @NamedQuery(name = "RMicrosrvcVer.findByMsrvcHost", query = "SELECT r FROM RMicrosrvcVer r WHERE r.msrvcHost = :msrvcHost")
    , @NamedQuery(name = "RMicrosrvcVer.findByMsrvcLocation", query = "SELECT r FROM RMicrosrvcVer r WHERE r.msrvcLocation = :msrvcLocation")
    , @NamedQuery(name = "RMicrosrvcVer.findByMsrvcLanguage", query = "SELECT r FROM RMicrosrvcVer r WHERE r.msrvcLanguage = :msrvcLanguage")
    , @NamedQuery(name = "RMicrosrvcVer.findByMsrvcTypeName", query = "SELECT r FROM RMicrosrvcVer r WHERE r.msrvcTypeName = :msrvcTypeName")
    , @NamedQuery(name = "RMicrosrvcVer.findByMsrvcStatus", query = "SELECT r FROM RMicrosrvcVer r WHERE r.msrvcStatus = :msrvcStatus")
    , @NamedQuery(name = "RMicrosrvcVer.findByMsrvcOwnerName", query = "SELECT r FROM RMicrosrvcVer r WHERE r.msrvcOwnerName = :msrvcOwnerName")
    , @NamedQuery(name = "RMicrosrvcVer.findByMsrvcOwnerZone", query = "SELECT r FROM RMicrosrvcVer r WHERE r.msrvcOwnerZone = :msrvcOwnerZone")
    , @NamedQuery(name = "RMicrosrvcVer.findByRComment", query = "SELECT r FROM RMicrosrvcVer r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RMicrosrvcVer.findByCreateTs", query = "SELECT r FROM RMicrosrvcVer r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RMicrosrvcVer.findByModifyTs", query = "SELECT r FROM RMicrosrvcVer r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RMicrosrvcVer.findById", query = "SELECT r FROM RMicrosrvcVer r WHERE r.id = :id")})
public class RMicrosrvcVer implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "msrvc_id")
    private long msrvcId;
    @Column(name = "msrvc_version")
    private String msrvcVersion;
    @Column(name = "msrvc_host")
    private String msrvcHost;
    @Column(name = "msrvc_location")
    private String msrvcLocation;
    @Column(name = "msrvc_language")
    private String msrvcLanguage;
    @Column(name = "msrvc_type_name")
    private String msrvcTypeName;
    @Column(name = "msrvc_status")
    private BigInteger msrvcStatus;
    @Basic(optional = false)
    @Column(name = "msrvc_owner_name")
    private String msrvcOwnerName;
    @Basic(optional = false)
    @Column(name = "msrvc_owner_zone")
    private String msrvcOwnerZone;
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

    public RMicrosrvcVer() {
    }

    public RMicrosrvcVer(Integer id) {
        this.id = id;
    }

    public RMicrosrvcVer(Integer id, long msrvcId, String msrvcOwnerName, String msrvcOwnerZone) {
        this.id = id;
        this.msrvcId = msrvcId;
        this.msrvcOwnerName = msrvcOwnerName;
        this.msrvcOwnerZone = msrvcOwnerZone;
    }

    public long getMsrvcId() {
        return msrvcId;
    }

    public void setMsrvcId(long msrvcId) {
        this.msrvcId = msrvcId;
    }

    public String getMsrvcVersion() {
        return msrvcVersion;
    }

    public void setMsrvcVersion(String msrvcVersion) {
        this.msrvcVersion = msrvcVersion;
    }

    public String getMsrvcHost() {
        return msrvcHost;
    }

    public void setMsrvcHost(String msrvcHost) {
        this.msrvcHost = msrvcHost;
    }

    public String getMsrvcLocation() {
        return msrvcLocation;
    }

    public void setMsrvcLocation(String msrvcLocation) {
        this.msrvcLocation = msrvcLocation;
    }

    public String getMsrvcLanguage() {
        return msrvcLanguage;
    }

    public void setMsrvcLanguage(String msrvcLanguage) {
        this.msrvcLanguage = msrvcLanguage;
    }

    public String getMsrvcTypeName() {
        return msrvcTypeName;
    }

    public void setMsrvcTypeName(String msrvcTypeName) {
        this.msrvcTypeName = msrvcTypeName;
    }

    public BigInteger getMsrvcStatus() {
        return msrvcStatus;
    }

    public void setMsrvcStatus(BigInteger msrvcStatus) {
        this.msrvcStatus = msrvcStatus;
    }

    public String getMsrvcOwnerName() {
        return msrvcOwnerName;
    }

    public void setMsrvcOwnerName(String msrvcOwnerName) {
        this.msrvcOwnerName = msrvcOwnerName;
    }

    public String getMsrvcOwnerZone() {
        return msrvcOwnerZone;
    }

    public void setMsrvcOwnerZone(String msrvcOwnerZone) {
        this.msrvcOwnerZone = msrvcOwnerZone;
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
        if (!(object instanceof RMicrosrvcVer)) {
            return false;
        }
        RMicrosrvcVer other = (RMicrosrvcVer) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RMicrosrvcVer[ id=" + id + " ]";
    }
    
}
