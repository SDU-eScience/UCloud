/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esciencecloud.jpa.escienceclouddb;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.Date;

/**
 * @author bjhj
 */
@Entity
@Table(name = "irodsfileextension")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Irodsfileextension.findAll", query = "SELECT i FROM Irodsfileextension i")
        , @NamedQuery(name = "Irodsfileextension.findById", query = "SELECT i FROM Irodsfileextension i WHERE i.id = :id")
        , @NamedQuery(name = "Irodsfileextension.findByIrodsfileextensiontext", query = "SELECT i FROM Irodsfileextension i WHERE i.irodsfileextensiontext = :irodsfileextensiontext")
        , @NamedQuery(name = "Irodsfileextension.findByIrodsfileextensiondesc", query = "SELECT i FROM Irodsfileextension i WHERE i.irodsfileextensiondesc = :irodsfileextensiondesc")
        , @NamedQuery(name = "Irodsfileextension.findByActive", query = "SELECT i FROM Irodsfileextension i WHERE i.active = :active")
        , @NamedQuery(name = "Irodsfileextension.findByIrodsfileextensionmapid", query = "SELECT i FROM Irodsfileextension i WHERE i.irodsfileextensionmapid = :irodsfileextensionmapid")
        , @NamedQuery(name = "Irodsfileextension.findByLastmodified", query = "SELECT i FROM Irodsfileextension i WHERE i.lastmodified = :lastmodified")})
public class Irodsfileextension implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "irodsfileextensiontext")
    private String irodsfileextensiontext;
    @Column(name = "irodsfileextensiondesc")
    private String irodsfileextensiondesc;
    @Column(name = "active")
    private Integer active;
    @Column(name = "irodsfileextensionmapid")
    private Integer irodsfileextensionmapid;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;

    public Irodsfileextension() {
    }

    public Irodsfileextension(Integer id) {
        this.id = id;
    }

    public Irodsfileextension(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getIrodsfileextensiontext() {
        return irodsfileextensiontext;
    }

    public void setIrodsfileextensiontext(String irodsfileextensiontext) {
        this.irodsfileextensiontext = irodsfileextensiontext;
    }

    public String getIrodsfileextensiondesc() {
        return irodsfileextensiondesc;
    }

    public void setIrodsfileextensiondesc(String irodsfileextensiondesc) {
        this.irodsfileextensiondesc = irodsfileextensiondesc;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
    }

    public Integer getIrodsfileextensionmapid() {
        return irodsfileextensionmapid;
    }

    public void setIrodsfileextensionmapid(Integer irodsfileextensionmapid) {
        this.irodsfileextensionmapid = irodsfileextensionmapid;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
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
        if (!(object instanceof Irodsfileextension)) {
            return false;
        }
        Irodsfileextension other = (Irodsfileextension) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Irodsfileextension[ id=" + id + " ]";
    }

}
