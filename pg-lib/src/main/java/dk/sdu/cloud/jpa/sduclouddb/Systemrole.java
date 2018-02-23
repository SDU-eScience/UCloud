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
@Table(name = "systemrole")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Systemrole.findAll", query = "SELECT s FROM Systemrole s")
    , @NamedQuery(name = "Systemrole.findById", query = "SELECT s FROM Systemrole s WHERE s.id = :id")
    , @NamedQuery(name = "Systemrole.findBySystemrolename", query = "SELECT s FROM Systemrole s WHERE s.systemrolename = :systemrolename")
    , @NamedQuery(name = "Systemrole.findByLandingpage", query = "SELECT s FROM Systemrole s WHERE s.landingpage = :landingpage")
    , @NamedQuery(name = "Systemrole.findByActive", query = "SELECT s FROM Systemrole s WHERE s.active = :active")
    , @NamedQuery(name = "Systemrole.findByMarkedfordelete", query = "SELECT s FROM Systemrole s WHERE s.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Systemrole.findByModifiedTs", query = "SELECT s FROM Systemrole s WHERE s.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Systemrole.findByCreatedTs", query = "SELECT s FROM Systemrole s WHERE s.createdTs = :createdTs")})
public class Systemrole implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "systemrolename")
    private String systemrolename;
    @Column(name = "landingpage")
    private String landingpage;
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
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "systemrolerefid")
    private List<Personsystemrolerel> personsystemrolerelList;

    public Systemrole() {
    }

    public Systemrole(Integer id) {
        this.id = id;
    }

    public Systemrole(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getSystemrolename() {
        return systemrolename;
    }

    public void setSystemrolename(String systemrolename) {
        this.systemrolename = systemrolename;
    }

    public String getLandingpage() {
        return landingpage;
    }

    public void setLandingpage(String landingpage) {
        this.landingpage = landingpage;
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
    public List<Personsystemrolerel> getPersonsystemrolerelList() {
        return personsystemrolerelList;
    }

    public void setPersonsystemrolerelList(List<Personsystemrolerel> personsystemrolerelList) {
        this.personsystemrolerelList = personsystemrolerelList;
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
        if (!(object instanceof Systemrole)) {
            return false;
        }
        Systemrole other = (Systemrole) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.Systemrole[ id=" + id + " ]";
    }
    
}
