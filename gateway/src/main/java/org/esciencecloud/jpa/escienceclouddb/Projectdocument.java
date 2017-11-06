/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esciencecloud.jpa.escienceclouddb;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author bjhj
 */
@Entity
@Table(name = "projectdocument")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = "Projectdocument.findAll", query = "SELECT p FROM Projectdocument p")
        , @NamedQuery(name = "Projectdocument.findById", query = "SELECT p FROM Projectdocument p WHERE p.id = :id")
        , @NamedQuery(name = "Projectdocument.findByProjectdocumentfilename", query = "SELECT p FROM Projectdocument p WHERE p.projectdocumentfilename = :projectdocumentfilename")
        , @NamedQuery(name = "Projectdocument.findByDocumenttypedescription", query = "SELECT p FROM Projectdocument p WHERE p.documenttypedescription = :documenttypedescription")
        , @NamedQuery(name = "Projectdocument.findByProjectdocumentactive", query = "SELECT p FROM Projectdocument p WHERE p.projectdocumentactive = :projectdocumentactive")
        , @NamedQuery(name = "Projectdocument.findByLastmodified", query = "SELECT p FROM Projectdocument p WHERE p.lastmodified = :lastmodified")})
public class Projectdocument implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "projectdocumentfilename")
    private String projectdocumentfilename;
    @Column(name = "documenttypedescription")
    private String documenttypedescription;
    @Lob
    @Column(name = "projectdocumentbin")
    private byte[] projectdocumentbin;
    @Column(name = "projectdocumentactive")
    private Integer projectdocumentactive;
    @Basic(optional = false)
    @Column(name = "lastmodified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastmodified;
    @OneToMany(mappedBy = "projectdocumentrefid")
    private List<Projectprojectdocumentrel> projectprojectdocumentrelList;

    public Projectdocument() {
    }

    public Projectdocument(Integer id) {
        this.id = id;
    }

    public Projectdocument(Integer id, Date lastmodified) {
        this.id = id;
        this.lastmodified = lastmodified;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getProjectdocumentfilename() {
        return projectdocumentfilename;
    }

    public void setProjectdocumentfilename(String projectdocumentfilename) {
        this.projectdocumentfilename = projectdocumentfilename;
    }

    public String getDocumenttypedescription() {
        return documenttypedescription;
    }

    public void setDocumenttypedescription(String documenttypedescription) {
        this.documenttypedescription = documenttypedescription;
    }

    public byte[] getProjectdocumentbin() {
        return projectdocumentbin;
    }

    public void setProjectdocumentbin(byte[] projectdocumentbin) {
        this.projectdocumentbin = projectdocumentbin;
    }

    public Integer getProjectdocumentactive() {
        return projectdocumentactive;
    }

    public void setProjectdocumentactive(Integer projectdocumentactive) {
        this.projectdocumentactive = projectdocumentactive;
    }

    public Date getLastmodified() {
        return lastmodified;
    }

    public void setLastmodified(Date lastmodified) {
        this.lastmodified = lastmodified;
    }

    @XmlTransient
    public List<Projectprojectdocumentrel> getProjectprojectdocumentrelList() {
        return projectprojectdocumentrelList;
    }

    public void setProjectprojectdocumentrelList(List<Projectprojectdocumentrel> projectprojectdocumentrelList) {
        this.projectprojectdocumentrelList = projectprojectdocumentrelList;
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
        if (!(object instanceof Projectdocument)) {
            return false;
        }
        Projectdocument other = (Projectdocument) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.escience.jpa.escienceclouddb.Projectdocument[ id=" + id + " ]";
    }

}
