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
@Table(name = "r_meta_main")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RMetaMain.findAll", query = "SELECT r FROM RMetaMain r")
    , @NamedQuery(name = "RMetaMain.findByMetaId", query = "SELECT r FROM RMetaMain r WHERE r.metaId = :metaId")
    , @NamedQuery(name = "RMetaMain.findByMetaNamespace", query = "SELECT r FROM RMetaMain r WHERE r.metaNamespace = :metaNamespace")
    , @NamedQuery(name = "RMetaMain.findByMetaAttrName", query = "SELECT r FROM RMetaMain r WHERE r.metaAttrName = :metaAttrName")
    , @NamedQuery(name = "RMetaMain.findByMetaAttrValue", query = "SELECT r FROM RMetaMain r WHERE r.metaAttrValue = :metaAttrValue")
    , @NamedQuery(name = "RMetaMain.findByMetaAttrUnit", query = "SELECT r FROM RMetaMain r WHERE r.metaAttrUnit = :metaAttrUnit")
    , @NamedQuery(name = "RMetaMain.findByRComment", query = "SELECT r FROM RMetaMain r WHERE r.rComment = :rComment")
    , @NamedQuery(name = "RMetaMain.findByCreateTs", query = "SELECT r FROM RMetaMain r WHERE r.createTs = :createTs")
    , @NamedQuery(name = "RMetaMain.findByModifyTs", query = "SELECT r FROM RMetaMain r WHERE r.modifyTs = :modifyTs")
    , @NamedQuery(name = "RMetaMain.findById", query = "SELECT r FROM RMetaMain r WHERE r.id = :id")})
public class RMetaMain implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "meta_id")
    private long metaId;
    @Column(name = "meta_namespace")
    private String metaNamespace;
    @Basic(optional = false)
    @Column(name = "meta_attr_name")
    private String metaAttrName;
    @Basic(optional = false)
    @Column(name = "meta_attr_value")
    private String metaAttrValue;
    @Column(name = "meta_attr_unit")
    private String metaAttrUnit;
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

    public RMetaMain() {
    }

    public RMetaMain(Integer id) {
        this.id = id;
    }

    public RMetaMain(Integer id, long metaId, String metaAttrName, String metaAttrValue) {
        this.id = id;
        this.metaId = metaId;
        this.metaAttrName = metaAttrName;
        this.metaAttrValue = metaAttrValue;
    }

    public long getMetaId() {
        return metaId;
    }

    public void setMetaId(long metaId) {
        this.metaId = metaId;
    }

    public String getMetaNamespace() {
        return metaNamespace;
    }

    public void setMetaNamespace(String metaNamespace) {
        this.metaNamespace = metaNamespace;
    }

    public String getMetaAttrName() {
        return metaAttrName;
    }

    public void setMetaAttrName(String metaAttrName) {
        this.metaAttrName = metaAttrName;
    }

    public String getMetaAttrValue() {
        return metaAttrValue;
    }

    public void setMetaAttrValue(String metaAttrValue) {
        this.metaAttrValue = metaAttrValue;
    }

    public String getMetaAttrUnit() {
        return metaAttrUnit;
    }

    public void setMetaAttrUnit(String metaAttrUnit) {
        this.metaAttrUnit = metaAttrUnit;
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
        if (!(object instanceof RMetaMain)) {
            return false;
        }
        RMetaMain other = (RMetaMain) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RMetaMain[ id=" + id + " ]";
    }
    
}
