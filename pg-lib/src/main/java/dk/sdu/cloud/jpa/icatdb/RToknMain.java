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
@Table(name = "r_tokn_main")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RToknMain.findAll", query = "SELECT r FROM RToknMain r")
    , @NamedQuery(name = "RToknMain.findByTokenNamespace", query = "SELECT r FROM RToknMain r WHERE r.tokenNamespace = :tokenNamespace")
    , @NamedQuery(name = "RToknMain.findByTokenId", query = "SELECT r FROM RToknMain r WHERE r.tokenId = :tokenId")
    , @NamedQuery(name = "RToknMain.findByTokenName", query = "SELECT r FROM RToknMain r WHERE r.tokenName = :tokenName")
    , @NamedQuery(name = "RToknMain.findByTokenValue", query = "SELECT r FROM RToknMain r WHERE r.tokenValue = :tokenValue")
    , @NamedQuery(name = "RToknMain.findByTokenValue2", query = "SELECT r FROM RToknMain r WHERE r.tokenValue2 = :tokenValue2")
    , @NamedQuery(name = "RToknMain.findByTokenValue3", query = "SELECT r FROM RToknMain r WHERE r.tokenValue3 = :tokenValue3")
    , @NamedQuery(name = "RToknMain.findByRComment", query = "SELECT r FROM RToknMain r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RToknMain.findByCreateTs", query = "SELECT r FROM RToknMain r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RToknMain.findByModifyTs", query = "SELECT r FROM RToknMain r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RToknMain.findById", query = "SELECT r FROM RToknMain r WHERE r.id = :id")})
public class RToknMain implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "token_namespace")
    private String tokenNamespace;
    @Basic(optional = false)
    @Column(name = "token_id")
    private long tokenId;
    @Basic(optional = false)
    @Column(name = "token_name")
    private String tokenName;
    @Column(name = "token_value")
    private String tokenValue;
    @Column(name = "token_value2")
    private String tokenValue2;
    @Column(name = "token_value3")
    private String tokenValue3;
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

    public RToknMain() {
    }

    public RToknMain(Integer id) {
        this.id = id;
    }

    public RToknMain(Integer id, String tokenNamespace, long tokenId, String tokenName) {
        this.id = id;
        this.tokenNamespace = tokenNamespace;
        this.tokenId = tokenId;
        this.tokenName = tokenName;
    }

    public String getTokenNamespace() {
        return tokenNamespace;
    }

    public void setTokenNamespace(String tokenNamespace) {
        this.tokenNamespace = tokenNamespace;
    }

    public long getTokenId() {
        return tokenId;
    }

    public void setTokenId(long tokenId) {
        this.tokenId = tokenId;
    }

    public String getTokenName() {
        return tokenName;
    }

    public void setTokenName(String tokenName) {
        this.tokenName = tokenName;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public void setTokenValue(String tokenValue) {
        this.tokenValue = tokenValue;
    }

    public String getTokenValue2() {
        return tokenValue2;
    }

    public void setTokenValue2(String tokenValue2) {
        this.tokenValue2 = tokenValue2;
    }

    public String getTokenValue3() {
        return tokenValue3;
    }

    public void setTokenValue3(String tokenValue3) {
        this.tokenValue3 = tokenValue3;
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
        if (!(object instanceof RToknMain)) {
            return false;
        }
        RToknMain other = (RToknMain) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RToknMain[ id=" + id + " ]";
    }
    
}
