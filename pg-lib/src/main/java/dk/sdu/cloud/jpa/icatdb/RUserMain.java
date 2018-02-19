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
@Table(name = "r_user_main")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RUserMain.findAll", query = "SELECT r FROM RUserMain r")
    , @NamedQuery(name = "RUserMain.findByUserId", query = "SELECT r FROM RUserMain r WHERE r.userId = :userId")
    , @NamedQuery(name = "RUserMain.findByUserName", query = "SELECT r FROM RUserMain r WHERE r.userName = :userName")
    , @NamedQuery(name = "RUserMain.findByUserTypeName", query = "SELECT r FROM RUserMain r WHERE r.userTypeName = :userTypeName")
    , @NamedQuery(name = "RUserMain.findByZoneName", query = "SELECT r FROM RUserMain r WHERE r.zoneName = :zoneName")
    , @NamedQuery(name = "RUserMain.findByUserInfo", query = "SELECT r FROM RUserMain r WHERE r.userInfo = :userInfo")
    , @NamedQuery(name = "RUserMain.findByRComment", query = "SELECT r FROM RUserMain r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RUserMain.findByCreateTs", query = "SELECT r FROM RUserMain r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RUserMain.findByModifyTs", query = "SELECT r FROM RUserMain r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RUserMain.findById", query = "SELECT r FROM RUserMain r WHERE r.id = :id")})
public class RUserMain implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "user_id")
    private long userId;
    @Basic(optional = false)
    @Column(name = "user_name")
    private String userName;
    @Basic(optional = false)
    @Column(name = "user_type_name")
    private String userTypeName;
    @Basic(optional = false)
    @Column(name = "zone_name")
    private String zoneName;
    @Column(name = "user_info")
    private String userInfo;
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

    public RUserMain() {
    }

    public RUserMain(Integer id) {
        this.id = id;
    }

    public RUserMain(Integer id, long userId, String userName, String userTypeName, String zoneName) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.userTypeName = userTypeName;
        this.zoneName = zoneName;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
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

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(String userInfo) {
        this.userInfo = userInfo;
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
        if (!(object instanceof RUserMain)) {
            return false;
        }
        RUserMain other = (RUserMain) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RUserMain[ id=" + id + " ]";
    }
    
}
