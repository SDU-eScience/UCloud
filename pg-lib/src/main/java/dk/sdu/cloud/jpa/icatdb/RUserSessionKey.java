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
@Table(name = "r_user_session_key")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RUserSessionKey.findAll", query = "SELECT r FROM RUserSessionKey r")
    , @NamedQuery(name = "RUserSessionKey.findByUserId", query = "SELECT r FROM RUserSessionKey r WHERE r.userId = :userId")
    , @NamedQuery(name = "RUserSessionKey.findBySessionKey", query = "SELECT r FROM RUserSessionKey r WHERE r.sessionKey = :sessionKey")
    , @NamedQuery(name = "RUserSessionKey.findBySessionInfo", query = "SELECT r FROM RUserSessionKey r WHERE r.sessionInfo = :sessionInfo")
    , @NamedQuery(name = "RUserSessionKey.findByAuthScheme", query = "SELECT r FROM RUserSessionKey r WHERE r.authScheme = :authScheme")
    , @NamedQuery(name = "RUserSessionKey.findBySessionExpiryTs", query = "SELECT r FROM RUserSessionKey r WHERE r.sessionExpiryTs = :sessionExpiryTs")
    , @NamedQuery(name = "RUserSessionKey.findByCreateTs", query = "SELECT r FROM RUserSessionKey r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RUserSessionKey.findByModifyTs", query = "SELECT r FROM RUserSessionKey r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RUserSessionKey.findById", query = "SELECT r FROM RUserSessionKey r WHERE r.id = :id")})
public class RUserSessionKey implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "user_id")
    private long userId;
    @Basic(optional = false)
    @Column(name = "session_key")
    private String sessionKey;
    @Column(name = "session_info")
    private String sessionInfo;
    @Basic(optional = false)
    @Column(name = "auth_scheme")
    private String authScheme;
    @Basic(optional = false)
    @Column(name = "session_expiry_ts")
    private String sessionExpiryTs;
    @Column(name = "create_ts")
    private String createTs;
    @Column(name = "modify_ts")
    private String modifyTs;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RUserSessionKey() {
    }

    public RUserSessionKey(Integer id) {
        this.id = id;
    }

    public RUserSessionKey(Integer id, long userId, String sessionKey, String authScheme, String sessionExpiryTs) {
        this.id = id;
        this.userId = userId;
        this.sessionKey = sessionKey;
        this.authScheme = authScheme;
        this.sessionExpiryTs = sessionExpiryTs;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public String getSessionInfo() {
        return sessionInfo;
    }

    public void setSessionInfo(String sessionInfo) {
        this.sessionInfo = sessionInfo;
    }

    public String getAuthScheme() {
        return authScheme;
    }

    public void setAuthScheme(String authScheme) {
        this.authScheme = authScheme;
    }

    public String getSessionExpiryTs() {
        return sessionExpiryTs;
    }

    public void setSessionExpiryTs(String sessionExpiryTs) {
        this.sessionExpiryTs = sessionExpiryTs;
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
        if (!(object instanceof RUserSessionKey)) {
            return false;
        }
        RUserSessionKey other = (RUserSessionKey) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RUserSessionKey[ id=" + id + " ]";
    }
    
}
