/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 *
 * @author bjhj
 */
@Entity
@Table(name = "principal_notification_subscription_type")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "PrincipalNotificationSubscriptionType.findAll", query = "SELECT p FROM PrincipalNotificationSubscriptionType p")
    , @NamedQuery(name = "PrincipalNotificationSubscriptionType.findById", query = "SELECT p FROM PrincipalNotificationSubscriptionType p WHERE p.id = :id")
    , @NamedQuery(name = "PrincipalNotificationSubscriptionType.findByPrincipalnotificationsubscriptiontypename", query = "SELECT p FROM PrincipalNotificationSubscriptionType p WHERE p.principalnotificationsubscriptiontypename = :principalnotificationsubscriptiontypename")
    , @NamedQuery(name = "PrincipalNotificationSubscriptionType.findByActive", query = "SELECT p FROM PrincipalNotificationSubscriptionType p WHERE p.active = :active")
    , @NamedQuery(name = "PrincipalNotificationSubscriptionType.findByMarkedfordelete", query = "SELECT p FROM PrincipalNotificationSubscriptionType p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "PrincipalNotificationSubscriptionType.findByModifiedTs", query = "SELECT p FROM PrincipalNotificationSubscriptionType p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "PrincipalNotificationSubscriptionType.findByCreatedTs", query = "SELECT p FROM PrincipalNotificationSubscriptionType p WHERE p.createdTs = :createdTs")
    , @NamedQuery(name = "PrincipalNotificationSubscriptionType.findByNosuppress", query = "SELECT p FROM PrincipalNotificationSubscriptionType p WHERE p.nosuppress = :nosuppress")})
public class PrincipalNotificationSubscriptionType implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "principalnotificationsubscriptiontypename")
    private String principalnotificationsubscriptiontypename;
    @Column(name = "active")
    private Integer active;
    @Column(name = "markedfordelete")
    private Integer markedfordelete;
    @Basic(optional = false)
    @Column(name = "modified_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date modifiedTs;
    @Basic(optional = false)
    @Column(name = "created_ts")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdTs;
    @Column(name = "nosuppress")
    private Integer nosuppress;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "principalnotificationsubscriptiontyperefid")
    private List<PrincipalNotificationSubscriptiontypeRelation> principalNotificationSubscriptiontypeRelationList;

    public PrincipalNotificationSubscriptionType() {
    }

    public PrincipalNotificationSubscriptionType(Integer id) {
        this.id = id;
    }

    public PrincipalNotificationSubscriptionType(Integer id, Date modifiedTs, Date createdTs) {
        this.id = id;
        this.modifiedTs = modifiedTs;
        this.createdTs = createdTs;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPrincipalnotificationsubscriptiontypename() {
        return principalnotificationsubscriptiontypename;
    }

    public void setPrincipalnotificationsubscriptiontypename(String principalnotificationsubscriptiontypename) {
        this.principalnotificationsubscriptiontypename = principalnotificationsubscriptiontypename;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
    }

    public Integer getMarkedfordelete() {
        return markedfordelete;
    }

    public void setMarkedfordelete(Integer markedfordelete) {
        this.markedfordelete = markedfordelete;
    }

    public Date getModifiedTs() {
        return modifiedTs;
    }

    public void setModifiedTs(Date modifiedTs) {
        this.modifiedTs = modifiedTs;
    }

    public Date getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(Date createdTs) {
        this.createdTs = createdTs;
    }

    public Integer getNosuppress() {
        return nosuppress;
    }

    public void setNosuppress(Integer nosuppress) {
        this.nosuppress = nosuppress;
    }

    @XmlTransient
    public List<PrincipalNotificationSubscriptiontypeRelation> getPrincipalNotificationSubscriptiontypeRelationList() {
        return principalNotificationSubscriptiontypeRelationList;
    }

    public void setPrincipalNotificationSubscriptiontypeRelationList(List<PrincipalNotificationSubscriptiontypeRelation> principalNotificationSubscriptiontypeRelationList) {
        this.principalNotificationSubscriptiontypeRelationList = principalNotificationSubscriptiontypeRelationList;
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
        if (!(object instanceof PrincipalNotificationSubscriptionType)) {
            return false;
        }
        PrincipalNotificationSubscriptionType other = (PrincipalNotificationSubscriptionType) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.PrincipalNotificationSubscriptionType[ id=" + id + " ]";
    }
    
}
