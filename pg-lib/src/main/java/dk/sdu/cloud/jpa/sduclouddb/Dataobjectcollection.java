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
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
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
@Table(name = "dataobjectcollection")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Dataobjectcollection.findAll", query = "SELECT d FROM Dataobjectcollection d")
    , @NamedQuery(name = "Dataobjectcollection.findById", query = "SELECT d FROM Dataobjectcollection d WHERE d.id = :id")
    , @NamedQuery(name = "Dataobjectcollection.findByDataobjectcollectionurl", query = "SELECT d FROM Dataobjectcollection d WHERE d.dataobjectcollectionurl = :dataobjectcollectionurl")
    , @NamedQuery(name = "Dataobjectcollection.findByDataobjectcollectiontyperefid", query = "SELECT d FROM Dataobjectcollection d WHERE d.dataobjectcollectiontyperefid = :dataobjectcollectiontyperefid")
    , @NamedQuery(name = "Dataobjectcollection.findByActive", query = "SELECT d FROM Dataobjectcollection d WHERE d.active = :active")
    , @NamedQuery(name = "Dataobjectcollection.findByMarkedfordelete", query = "SELECT d FROM Dataobjectcollection d WHERE d.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Dataobjectcollection.findByModifiedTs", query = "SELECT d FROM Dataobjectcollection d WHERE d.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Dataobjectcollection.findByCreatedTs", query = "SELECT d FROM Dataobjectcollection d WHERE d.createdTs = :createdTs")})
public class Dataobjectcollection implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "dataobjectcollectionurl")
    private String dataobjectcollectionurl;
    @Column(name = "dataobjectcollectiontyperefid")
    private Integer dataobjectcollectiontyperefid;
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
    @JoinColumn(name = "personrefid", referencedColumnName = "id")
    @ManyToOne
    private Person personrefid;
    @JoinColumn(name = "projectrefid", referencedColumnName = "id")
    @ManyToOne
    private Project projectrefid;
    @OneToMany(mappedBy = "dataobjectcollectionrefid")
    private List<Dataobjectcollectionrel> dataobjectcollectionrelList;

    public Dataobjectcollection() {
    }

    public Dataobjectcollection(Integer id) {
        this.id = id;
    }

    public Dataobjectcollection(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getDataobjectcollectionurl() {
        return dataobjectcollectionurl;
    }

    public void setDataobjectcollectionurl(String dataobjectcollectionurl) {
        this.dataobjectcollectionurl = dataobjectcollectionurl;
    }

    public Integer getDataobjectcollectiontyperefid() {
        return dataobjectcollectiontyperefid;
    }

    public void setDataobjectcollectiontyperefid(Integer dataobjectcollectiontyperefid) {
        this.dataobjectcollectiontyperefid = dataobjectcollectiontyperefid;
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

    public Person getPersonrefid() {
        return personrefid;
    }

    public void setPersonrefid(Person personrefid) {
        this.personrefid = personrefid;
    }

    public Project getProjectrefid() {
        return projectrefid;
    }

    public void setProjectrefid(Project projectrefid) {
        this.projectrefid = projectrefid;
    }

    @XmlTransient
    public List<Dataobjectcollectionrel> getDataobjectcollectionrelList() {
        return dataobjectcollectionrelList;
    }

    public void setDataobjectcollectionrelList(List<Dataobjectcollectionrel> dataobjectcollectionrelList) {
        this.dataobjectcollectionrelList = dataobjectcollectionrelList;
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
        if (!(object instanceof Dataobjectcollection)) {
            return false;
        }
        Dataobjectcollection other = (Dataobjectcollection) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }


    @java.lang.Override
    public java.lang.String toString() {
        return "Dataobjectcollection{" +
                "id=" + id +
                ", dataobjectcollectionurl='" + dataobjectcollectionurl + '\'' +
                ", dataobjectcollectiontyperefid=" + dataobjectcollectiontyperefid +
                ", active=" + active +
                ", markedfordelete=" + markedfordelete +
                ", modifiedTs=" + modifiedTs +
                ", createdTs=" + createdTs +
                ", personrefid=" + personrefid +
                ", projectrefid=" + projectrefid +
                ", dataobjectcollectionrelList=" + dataobjectcollectionrelList +
                '}';
    }
}
