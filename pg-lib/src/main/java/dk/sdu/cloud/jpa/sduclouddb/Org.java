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
@Table(name = "org")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Org.findAll", query = "SELECT o FROM Org o")
    , @NamedQuery(name = "Org.findById", query = "SELECT o FROM Org o WHERE o.id = :id")
    , @NamedQuery(name = "Org.findByOrgfullname", query = "SELECT o FROM Org o WHERE o.orgfullname = :orgfullname")
    , @NamedQuery(name = "Org.findByOrgshortname", query = "SELECT o FROM Org o WHERE o.orgshortname = :orgshortname")
    , @NamedQuery(name = "Org.findByActive", query = "SELECT o FROM Org o WHERE o.active = :active")
    , @NamedQuery(name = "Org.findByMarkedfordelete", query = "SELECT o FROM Org o WHERE o.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Org.findByModifiedTs", query = "SELECT o FROM Org o WHERE o.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Org.findByCreatedTs", query = "SELECT o FROM Org o WHERE o.createdTs = :createdTs")})
public class Org implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "orgfullname")
    private String orgfullname;
    @Column(name = "orgshortname")
    private String orgshortname;
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
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "orgrefid")
    private List<Projectorgrel> projectorgrelList;
    @OneToMany(mappedBy = "orgrefid")
    private List<Person> personList;

    public Org() {
    }

    public Org(Integer id) {
        this.id = id;
    }

    public Org(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getOrgfullname() {
        return orgfullname;
    }

    public void setOrgfullname(String orgfullname) {
        this.orgfullname = orgfullname;
    }

    public String getOrgshortname() {
        return orgshortname;
    }

    public void setOrgshortname(String orgshortname) {
        this.orgshortname = orgshortname;
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

    @XmlTransient
    public List<Projectorgrel> getProjectorgrelList() {
        return projectorgrelList;
    }

    public void setProjectorgrelList(List<Projectorgrel> projectorgrelList) {
        this.projectorgrelList = projectorgrelList;
    }

    @XmlTransient
    public List<Person> getPersonList() {
        return personList;
    }

    public void setPersonList(List<Person> personList) {
        this.personList = personList;
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
        if (!(object instanceof Org)) {
            return false;
        }
        Org other = (Org) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.sducloud.jpa.sduclouddb.Org[ id=" + id + " ]";
    }
    
}
