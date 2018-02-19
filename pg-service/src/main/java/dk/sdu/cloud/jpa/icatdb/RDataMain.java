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
@Table(name = "r_data_main")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RDataMain.findAll", query = "SELECT r FROM RDataMain r")
    , @NamedQuery(name = "RDataMain.findByDataId", query = "SELECT r FROM RDataMain r WHERE r.dataId = :dataId")
    , @NamedQuery(name = "RDataMain.findByCollId", query = "SELECT r FROM RDataMain r WHERE r.collId = :collId")
    , @NamedQuery(name = "RDataMain.findByDataName", query = "SELECT r FROM RDataMain r WHERE r.dataName = :dataName")
    , @NamedQuery(name = "RDataMain.findByDataReplNum", query = "SELECT r FROM RDataMain r WHERE r.dataReplNum = :dataReplNum")
    , @NamedQuery(name = "RDataMain.findByDataVersion", query = "SELECT r FROM RDataMain r WHERE r.dataVersion = :dataVersion")
    , @NamedQuery(name = "RDataMain.findByDataTypeName", query = "SELECT r FROM RDataMain r WHERE r.dataTypeName = :dataTypeName")
    , @NamedQuery(name = "RDataMain.findByDataSize", query = "SELECT r FROM RDataMain r WHERE r.dataSize = :dataSize")
    , @NamedQuery(name = "RDataMain.findByRescGroupName", query = "SELECT r FROM RDataMain r WHERE r.rescGroupName = :rescGroupName")
    , @NamedQuery(name = "RDataMain.findByRescName", query = "SELECT r FROM RDataMain r WHERE r.rescName = :rescName")
    , @NamedQuery(name = "RDataMain.findByDataPath", query = "SELECT r FROM RDataMain r WHERE r.dataPath = :dataPath")
    , @NamedQuery(name = "RDataMain.findByDataOwnerName", query = "SELECT r FROM RDataMain r WHERE r.dataOwnerName = :dataOwnerName")
    , @NamedQuery(name = "RDataMain.findByDataOwnerZone", query = "SELECT r FROM RDataMain r WHERE r.dataOwnerZone = :dataOwnerZone")
    , @NamedQuery(name = "RDataMain.findByDataIsDirty", query = "SELECT r FROM RDataMain r WHERE r.dataIsDirty = :dataIsDirty")
    , @NamedQuery(name = "RDataMain.findByDataStatus", query = "SELECT r FROM RDataMain r WHERE r.dataStatus = :dataStatus")
    , @NamedQuery(name = "RDataMain.findByDataChecksum", query = "SELECT r FROM RDataMain r WHERE r.dataChecksum = :dataChecksum")
    , @NamedQuery(name = "RDataMain.findByDataExpiryTs", query = "SELECT r FROM RDataMain r WHERE r.dataExpiryTs = :dataExpiryTs")
    , @NamedQuery(name = "RDataMain.findByDataMapId", query = "SELECT r FROM RDataMain r WHERE r.dataMapId = :dataMapId")
    , @NamedQuery(name = "RDataMain.findByDataMode", query = "SELECT r FROM RDataMain r WHERE r.dataMode = :dataMode")
    , @NamedQuery(name = "RDataMain.findByRComment", query = "SELECT r FROM RDataMain r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RDataMain.findByCreateTs", query = "SELECT r FROM RDataMain r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RDataMain.findByModifyTs", query = "SELECT r FROM RDataMain r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RDataMain.findByRescHier", query = "SELECT r FROM RDataMain r WHERE r.rescHier = :rescHier")
        , @NamedQuery(name = "RDataMain.findByNotRescId", query = "SELECT r FROM RDataMain r WHERE r.rescId <> :rescId")
    , @NamedQuery(name = "RDataMain.findByRescId", query = "SELECT r FROM RDataMain r WHERE r.rescId = :rescId")
    , @NamedQuery(name = "RDataMain.findById", query = "SELECT r FROM RDataMain r WHERE r.id = :id")})
public class RDataMain implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "data_id")
    private long dataId;
    @Basic(optional = false)
    @Column(name = "coll_id")
    private long collId;
    @Basic(optional = false)
    @Column(name = "data_name")
    private String dataName;
    @Basic(optional = false)
    @Column(name = "data_repl_num")
    private int dataReplNum;
    @Column(name = "data_version")
    private String dataVersion;
    @Basic(optional = false)
    @Column(name = "data_type_name")
    private String dataTypeName;
    @Basic(optional = false)
    @Column(name = "data_size")
    private long dataSize;
    @Column(name = "resc_group_name")
    private String rescGroupName;
    @Basic(optional = false)
    @Column(name = "resc_name")
    private String rescName;
    @Basic(optional = false)
    @Column(name = "data_path")
    private String dataPath;
    @Basic(optional = false)
    @Column(name = "data_owner_name")
    private String dataOwnerName;
    @Basic(optional = false)
    @Column(name = "data_owner_zone")
    private String dataOwnerZone;
    @Column(name = "data_is_dirty")
    private Integer dataIsDirty;
    @Column(name = "data_status")
    private String dataStatus;
    @Column(name = "data_checksum")
    private String dataChecksum;
    @Column(name = "data_expiry_ts")
    private String dataExpiryTs;
    @Column(name = "data_map_id")
    private BigInteger dataMapId;
    @Column(name = "data_mode")
    private String dataMode;
    @Column(name = "r_comment")
    private String rComment;
    @Column(name = "create_ts")
    private String createTs;
    @Column(name = "modify_ts")
    private String modifyTs;
    @Column(name = "resc_hier")
    private String rescHier;
    @Column(name = "resc_id")
    private BigInteger rescId;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RDataMain() {
    }

    public RDataMain(Integer id) {
        this.id = id;
    }

    public RDataMain(Integer id, long dataId, long collId, String dataName, int dataReplNum, String dataTypeName, long dataSize, String rescName, String dataPath, String dataOwnerName, String dataOwnerZone) {
        this.id = id;
        this.dataId = dataId;
        this.collId = collId;
        this.dataName = dataName;
        this.dataReplNum = dataReplNum;
        this.dataTypeName = dataTypeName;
        this.dataSize = dataSize;
        this.rescName = rescName;
        this.dataPath = dataPath;
        this.dataOwnerName = dataOwnerName;
        this.dataOwnerZone = dataOwnerZone;
    }

    public long getDataId() {
        return dataId;
    }

    public void setDataId(long dataId) {
        this.dataId = dataId;
    }

    public long getCollId() {
        return collId;
    }

    public void setCollId(long collId) {
        this.collId = collId;
    }

    public String getDataName() {
        return dataName;
    }

    public void setDataName(String dataName) {
        this.dataName = dataName;
    }

    public int getDataReplNum() {
        return dataReplNum;
    }

    public void setDataReplNum(int dataReplNum) {
        this.dataReplNum = dataReplNum;
    }

    public String getDataVersion() {
        return dataVersion;
    }

    public void setDataVersion(String dataVersion) {
        this.dataVersion = dataVersion;
    }

    public String getDataTypeName() {
        return dataTypeName;
    }

    public void setDataTypeName(String dataTypeName) {
        this.dataTypeName = dataTypeName;
    }

    public long getDataSize() {
        return dataSize;
    }

    public void setDataSize(long dataSize) {
        this.dataSize = dataSize;
    }

    public String getRescGroupName() {
        return rescGroupName;
    }

    public void setRescGroupName(String rescGroupName) {
        this.rescGroupName = rescGroupName;
    }

    public String getRescName() {
        return rescName;
    }

    public void setRescName(String rescName) {
        this.rescName = rescName;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    public String getDataOwnerName() {
        return dataOwnerName;
    }

    public void setDataOwnerName(String dataOwnerName) {
        this.dataOwnerName = dataOwnerName;
    }

    public String getDataOwnerZone() {
        return dataOwnerZone;
    }

    public void setDataOwnerZone(String dataOwnerZone) {
        this.dataOwnerZone = dataOwnerZone;
    }

    public Integer getDataIsDirty() {
        return dataIsDirty;
    }

    public void setDataIsDirty(Integer dataIsDirty) {
        this.dataIsDirty = dataIsDirty;
    }

    public String getDataStatus() {
        return dataStatus;
    }

    public void setDataStatus(String dataStatus) {
        this.dataStatus = dataStatus;
    }

    public String getDataChecksum() {
        return dataChecksum;
    }

    public void setDataChecksum(String dataChecksum) {
        this.dataChecksum = dataChecksum;
    }

    public String getDataExpiryTs() {
        return dataExpiryTs;
    }

    public void setDataExpiryTs(String dataExpiryTs) {
        this.dataExpiryTs = dataExpiryTs;
    }

    public BigInteger getDataMapId() {
        return dataMapId;
    }

    public void setDataMapId(BigInteger dataMapId) {
        this.dataMapId = dataMapId;
    }

    public String getDataMode() {
        return dataMode;
    }

    public void setDataMode(String dataMode) {
        this.dataMode = dataMode;
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

    public String getRescHier() {
        return rescHier;
    }

    public void setRescHier(String rescHier) {
        this.rescHier = rescHier;
    }

    public BigInteger getRescId() {
        return rescId;
    }

    public void setRescId(BigInteger rescId) {
        this.rescId = rescId;
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
        if (!(object instanceof RDataMain)) {
            return false;
        }
        RDataMain other = (RDataMain) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "RDataMain{" +
                "dataId=" + dataId +
                ", collId=" + collId +
                ", dataName='" + dataName + '\'' +
                ", dataReplNum=" + dataReplNum +
                ", dataVersion='" + dataVersion + '\'' +
                ", dataTypeName='" + dataTypeName + '\'' +
                ", dataSize=" + dataSize +
                ", rescGroupName='" + rescGroupName + '\'' +
                ", rescName='" + rescName + '\'' +
                ", dataPath='" + dataPath + '\'' +
                ", dataOwnerName='" + dataOwnerName + '\'' +
                ", dataOwnerZone='" + dataOwnerZone + '\'' +
                ", dataIsDirty=" + dataIsDirty +
                ", dataStatus='" + dataStatus + '\'' +
                ", dataChecksum='" + dataChecksum + '\'' +
                ", dataExpiryTs='" + dataExpiryTs + '\'' +
                ", dataMapId=" + dataMapId +
                ", dataMode='" + dataMode + '\'' +
                ", rComment='" + rComment + '\'' +
                ", createTs='" + createTs + '\'' +
                ", modifyTs='" + modifyTs + '\'' +
                ", rescHier='" + rescHier + '\'' +
                ", rescId=" + rescId +
                ", id=" + id +
                '}';
    }
}
