/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.icatdb;

import java.io.Serializable;
import java.math.BigInteger;
import javax.persistence.*;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author bjhj
 */
@Entity
@Table(name = "vw_data_main")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "VwDataMain.findAll", query = "SELECT v FROM VwDataMain v")
    , @NamedQuery(name = "VwDataMain.findByRescId", query = "SELECT v FROM VwDataMain v WHERE v.rescId = :rescId")
    , @NamedQuery(name = "VwDataMain.findByDataName", query = "SELECT v FROM VwDataMain v WHERE v.dataName = :dataName")
    , @NamedQuery(name = "VwDataMain.findByDataTypeName", query = "SELECT v FROM VwDataMain v WHERE v.dataTypeName = :dataTypeName")
    , @NamedQuery(name = "VwDataMain.findByDataPath", query = "SELECT v FROM VwDataMain v WHERE v.dataPath = :dataPath")
    , @NamedQuery(name = "VwDataMain.findByDataSize", query = "SELECT v FROM VwDataMain v WHERE v.dataSize = :dataSize")
    , @NamedQuery(name = "VwDataMain.findByDataOwnerName", query = "SELECT v FROM VwDataMain v WHERE v.dataOwnerName = :dataOwnerName")
    , @NamedQuery(name = "VwDataMain.findByModifyTs", query = "SELECT v FROM VwDataMain v WHERE v.modifyTs = :modifyTs")
    , @NamedQuery(name = "VwDataMain.findByDataId", query = "SELECT v FROM VwDataMain v WHERE v.dataId = :dataId")
    , @NamedQuery(name = "VwDataMain.findByDataIsDirty", query = "SELECT v FROM VwDataMain v WHERE v.dataIsDirty = :dataIsDirty")
    , @NamedQuery(name = "VwDataMain.findByCollId", query = "SELECT v FROM VwDataMain v WHERE v.collId = :collId")
    , @NamedQuery(name = "VwDataMain.findByUserId", query = "SELECT v FROM VwDataMain v WHERE v.userId = :userId")
    , @NamedQuery(name = "VwDataMain.findByUserName", query = "SELECT v FROM VwDataMain v WHERE v.userName = :userName")
    , @NamedQuery(name = "VwDataMain.findByUserTypeName", query = "SELECT v FROM VwDataMain v WHERE v.userTypeName = :userTypeName")
    , @NamedQuery(name = "VwDataMain.findByCollName", query = "SELECT v FROM VwDataMain v WHERE v.collName = :collName")
    , @NamedQuery(name = "VwDataMain.findByCollOwnerName", query = "SELECT v FROM VwDataMain v WHERE v.collOwnerName = :collOwnerName")})
public class VwDataMain implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Column(name = "resc_id")
    private BigInteger rescId;
    @Column(name = "data_name")
    private String dataName;
    @Column(name = "data_type_name")
    private String dataTypeName;
    @Column(name = "data_path")
    private String dataPath;
    @Column(name = "data_size")
    private BigInteger dataSize;
    @Column(name = "data_owner_name")
    private String dataOwnerName;
    @Column(name = "modify_ts")
    private String modifyTs;
    @Column(name = "data_id")
    private BigInteger dataId;
    @Column(name = "data_is_dirty")
    private Integer dataIsDirty;
    @Column(name = "coll_id")
    private BigInteger collId;
    @Column(name = "user_id")
    private BigInteger userId;
    @Column(name = "user_name")
    private String userName;
    @Column(name = "user_type_name")
    private String userTypeName;
    @Column(name = "coll_name")
    private String collName;
    @Column(name = "coll_owner_name")
    private String collOwnerName;

    public VwDataMain() {
    }

    public BigInteger getRescId() {
        return rescId;
    }

    public void setRescId(BigInteger rescId) {
        this.rescId = rescId;
    }

    public String getDataName() {
        return dataName;
    }

    public void setDataName(String dataName) {
        this.dataName = dataName;
    }

    public String getDataTypeName() {
        return dataTypeName;
    }

    public void setDataTypeName(String dataTypeName) {
        this.dataTypeName = dataTypeName;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    public BigInteger getDataSize() {
        return dataSize;
    }

    public void setDataSize(BigInteger dataSize) {
        this.dataSize = dataSize;
    }

    public String getDataOwnerName() {
        return dataOwnerName;
    }

    public void setDataOwnerName(String dataOwnerName) {
        this.dataOwnerName = dataOwnerName;
    }

    public String getModifyTs() {
        return modifyTs;
    }

    public void setModifyTs(String modifyTs) {
        this.modifyTs = modifyTs;
    }

    public BigInteger getDataId() {
        return dataId;
    }

    public void setDataId(BigInteger dataId) {
        this.dataId = dataId;
    }

    public Integer getDataIsDirty() {
        return dataIsDirty;
    }

    public void setDataIsDirty(Integer dataIsDirty) {
        this.dataIsDirty = dataIsDirty;
    }

    public BigInteger getCollId() {
        return collId;
    }

    public void setCollId(BigInteger collId) {
        this.collId = collId;
    }

    public BigInteger getUserId() {
        return userId;
    }

    public void setUserId(BigInteger userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserTypeName() {
        return userTypeName;
    }

    public void setUserTypeName(String userTypeName) {
        this.userTypeName = userTypeName;
    }

    public String getCollName() {
        return collName;
    }

    public void setCollName(String collName) {
        this.collName = collName;
    }

    public String getCollOwnerName() {
        return collOwnerName;
    }

    public void setCollOwnerName(String collOwnerName) {
        this.collOwnerName = collOwnerName;
    }
    
}
