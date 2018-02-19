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
@Table(name = "r_microsrvc_main")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RMicrosrvcMain.findAll", query = "SELECT r FROM RMicrosrvcMain r")
    , @NamedQuery(name = "RMicrosrvcMain.findByMsrvcId", query = "SELECT r FROM RMicrosrvcMain r WHERE r.msrvcId = :msrvcId")
    , @NamedQuery(name = "RMicrosrvcMain.findByMsrvcName", query = "SELECT r FROM RMicrosrvcMain r WHERE r.msrvcName = :msrvcName")
    , @NamedQuery(name = "RMicrosrvcMain.findByMsrvcModuleName", query = "SELECT r FROM RMicrosrvcMain r WHERE r.msrvcModuleName = :msrvcModuleName")
    , @NamedQuery(name = "RMicrosrvcMain.findByMsrvcSignature", query = "SELECT r FROM RMicrosrvcMain r WHERE r.msrvcSignature = :msrvcSignature")
    , @NamedQuery(name = "RMicrosrvcMain.findByMsrvcDoxygen", query = "SELECT r FROM RMicrosrvcMain r WHERE r.msrvcDoxygen = :msrvcDoxygen")
    , @NamedQuery(name = "RMicrosrvcMain.findByMsrvcVariations", query = "SELECT r FROM RMicrosrvcMain r WHERE r.msrvcVariations = :msrvcVariations")
    , @NamedQuery(name = "RMicrosrvcMain.findByMsrvcOwnerName", query = "SELECT r FROM RMicrosrvcMain r WHERE r.msrvcOwnerName = :msrvcOwnerName")
    , @NamedQuery(name = "RMicrosrvcMain.findByMsrvcOwnerZone", query = "SELECT r FROM RMicrosrvcMain r WHERE r.msrvcOwnerZone = :msrvcOwnerZone")
    , @NamedQuery(name = "RMicrosrvcMain.findByRComment", query = "SELECT r FROM RMicrosrvcMain r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RMicrosrvcMain.findByCreateTs", query = "SELECT r FROM RMicrosrvcMain r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RMicrosrvcMain.findByModifyTs", query = "SELECT r FROM RMicrosrvcMain r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RMicrosrvcMain.findById", query = "SELECT r FROM RMicrosrvcMain r WHERE r.id = :id")})
public class RMicrosrvcMain implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "msrvc_id")
    private long msrvcId;
    @Basic(optional = false)
    @Column(name = "msrvc_name")
    private String msrvcName;
    @Basic(optional = false)
    @Column(name = "msrvc_module_name")
    private String msrvcModuleName;
    @Basic(optional = false)
    @Column(name = "msrvc_signature")
    private String msrvcSignature;
    @Basic(optional = false)
    @Column(name = "msrvc_doxygen")
    private String msrvcDoxygen;
    @Basic(optional = false)
    @Column(name = "msrvc_variations")
    private String msrvcVariations;
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

    public RMicrosrvcMain() {
    }

    public RMicrosrvcMain(Integer id) {
        this.id = id;
    }

    public RMicrosrvcMain(Integer id, long msrvcId, String msrvcName, String msrvcModuleName, String msrvcSignature, String msrvcDoxygen, String msrvcVariations, String msrvcOwnerName, String msrvcOwnerZone) {
        this.id = id;
        this.msrvcId = msrvcId;
        this.msrvcName = msrvcName;
        this.msrvcModuleName = msrvcModuleName;
        this.msrvcSignature = msrvcSignature;
        this.msrvcDoxygen = msrvcDoxygen;
        this.msrvcVariations = msrvcVariations;
        this.msrvcOwnerName = msrvcOwnerName;
        this.msrvcOwnerZone = msrvcOwnerZone;
    }

    public long getMsrvcId() {
        return msrvcId;
    }

    public void setMsrvcId(long msrvcId) {
        this.msrvcId = msrvcId;
    }

    public String getMsrvcName() {
        return msrvcName;
    }

    public void setMsrvcName(String msrvcName) {
        this.msrvcName = msrvcName;
    }

    public String getMsrvcModuleName() {
        return msrvcModuleName;
    }

    public void setMsrvcModuleName(String msrvcModuleName) {
        this.msrvcModuleName = msrvcModuleName;
    }

    public String getMsrvcSignature() {
        return msrvcSignature;
    }

    public void setMsrvcSignature(String msrvcSignature) {
        this.msrvcSignature = msrvcSignature;
    }

    public String getMsrvcDoxygen() {
        return msrvcDoxygen;
    }

    public void setMsrvcDoxygen(String msrvcDoxygen) {
        this.msrvcDoxygen = msrvcDoxygen;
    }

    public String getMsrvcVariations() {
        return msrvcVariations;
    }

    public void setMsrvcVariations(String msrvcVariations) {
        this.msrvcVariations = msrvcVariations;
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
        if (!(object instanceof RMicrosrvcMain)) {
            return false;
        }
        RMicrosrvcMain other = (RMicrosrvcMain) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RMicrosrvcMain[ id=" + id + " ]";
    }
    
}
