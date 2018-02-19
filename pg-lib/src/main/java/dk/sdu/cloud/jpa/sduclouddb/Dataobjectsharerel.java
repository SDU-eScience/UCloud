/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.util.Date;
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
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author bjhj
 */
@Entity
@Table(name = "dataobjectsharerel")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Dataobjectsharerel.findAll", query = "SELECT d FROM Dataobjectsharerel d")
    , @NamedQuery(name = "Dataobjectsharerel.findById", query = "SELECT d FROM Dataobjectsharerel d WHERE d.id = :id")
    , @NamedQuery(name = "Dataobjectsharerel.findByReaddataobjectsystemmetadata", query = "SELECT d FROM Dataobjectsharerel d WHERE d.readdataobjectsystemmetadata = :readdataobjectsystemmetadata")
    , @NamedQuery(name = "Dataobjectsharerel.findByReaddataobjectmetadata", query = "SELECT d FROM Dataobjectsharerel d WHERE d.readdataobjectmetadata = :readdataobjectmetadata")
    , @NamedQuery(name = "Dataobjectsharerel.findByReaddataobject", query = "SELECT d FROM Dataobjectsharerel d WHERE d.readdataobject = :readdataobject")
    , @NamedQuery(name = "Dataobjectsharerel.findByCreatedataobjectmetadata", query = "SELECT d FROM Dataobjectsharerel d WHERE d.createdataobjectmetadata = :createdataobjectmetadata")
    , @NamedQuery(name = "Dataobjectsharerel.findByModifydataobjectmetadata", query = "SELECT d FROM Dataobjectsharerel d WHERE d.modifydataobjectmetadata = :modifydataobjectmetadata")
    , @NamedQuery(name = "Dataobjectsharerel.findByDeletedataobject", query = "SELECT d FROM Dataobjectsharerel d WHERE d.deletedataobject = :deletedataobject")
    , @NamedQuery(name = "Dataobjectsharerel.findByAdministerdataobject", query = "SELECT d FROM Dataobjectsharerel d WHERE d.administerdataobject = :administerdataobject")
    , @NamedQuery(name = "Dataobjectsharerel.findByModifydataobject", query = "SELECT d FROM Dataobjectsharerel d WHERE d.modifydataobject = :modifydataobject")
    , @NamedQuery(name = "Dataobjectsharerel.findByOwndataobject", query = "SELECT d FROM Dataobjectsharerel d WHERE d.owndataobject = :owndataobject")
    , @NamedQuery(name = "Dataobjectsharerel.findByDownloaddataobject", query = "SELECT d FROM Dataobjectsharerel d WHERE d.downloaddataobject = :downloaddataobject")
    , @NamedQuery(name = "Dataobjectsharerel.findByMarkedfordelete", query = "SELECT d FROM Dataobjectsharerel d WHERE d.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Dataobjectsharerel.findByModifiedTs", query = "SELECT d FROM Dataobjectsharerel d WHERE d.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Dataobjectsharerel.findByCreatedTs", query = "SELECT d FROM Dataobjectsharerel d WHERE d.createdTs = :createdTs")})
public class Dataobjectsharerel implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "readdataobjectsystemmetadata")
    private Integer readdataobjectsystemmetadata;
    @Column(name = "readdataobjectmetadata")
    private Integer readdataobjectmetadata;
    @Column(name = "readdataobject")
    private Integer readdataobject;
    @Column(name = "createdataobjectmetadata")
    private Integer createdataobjectmetadata;
    @Column(name = "modifydataobjectmetadata")
    private Integer modifydataobjectmetadata;
    @Column(name = "deletedataobject")
    private Integer deletedataobject;
    @Column(name = "administerdataobject")
    private Integer administerdataobject;
    @Column(name = "modifydataobject")
    private Integer modifydataobject;
    @Column(name = "owndataobject")
    private Integer owndataobject;
    @Column(name = "downloaddataobject")
    private Integer downloaddataobject;
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
    @JoinColumn(name = "dataobjectrefid", referencedColumnName = "id")
    @ManyToOne
    private Dataobject dataobjectrefid;
    @JoinColumn(name = "personrefid", referencedColumnName = "id")
    @ManyToOne
    private Person personrefid;
    @JoinColumn(name = "projectrefid", referencedColumnName = "id")
    @ManyToOne
    private Project projectrefid;

    public Dataobjectsharerel() {
    }

    public Dataobjectsharerel(Integer id) {
        this.id = id;
    }

    public Dataobjectsharerel(Integer id, Date modifiedTs, Date createdTs) {
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

    public Integer getReaddataobjectsystemmetadata() {
        return readdataobjectsystemmetadata;
    }

    public void setReaddataobjectsystemmetadata(Integer readdataobjectsystemmetadata) {
        this.readdataobjectsystemmetadata = readdataobjectsystemmetadata;
    }

    public Integer getReaddataobjectmetadata() {
        return readdataobjectmetadata;
    }

    public void setReaddataobjectmetadata(Integer readdataobjectmetadata) {
        this.readdataobjectmetadata = readdataobjectmetadata;
    }

    public Integer getReaddataobject() {
        return readdataobject;
    }

    public void setReaddataobject(Integer readdataobject) {
        this.readdataobject = readdataobject;
    }

    public Integer getCreatedataobjectmetadata() {
        return createdataobjectmetadata;
    }

    public void setCreatedataobjectmetadata(Integer createdataobjectmetadata) {
        this.createdataobjectmetadata = createdataobjectmetadata;
    }

    public Integer getModifydataobjectmetadata() {
        return modifydataobjectmetadata;
    }

    public void setModifydataobjectmetadata(Integer modifydataobjectmetadata) {
        this.modifydataobjectmetadata = modifydataobjectmetadata;
    }

    public Integer getDeletedataobject() {
        return deletedataobject;
    }

    public void setDeletedataobject(Integer deletedataobject) {
        this.deletedataobject = deletedataobject;
    }

    public Integer getAdministerdataobject() {
        return administerdataobject;
    }

    public void setAdministerdataobject(Integer administerdataobject) {
        this.administerdataobject = administerdataobject;
    }

    public Integer getModifydataobject() {
        return modifydataobject;
    }

    public void setModifydataobject(Integer modifydataobject) {
        this.modifydataobject = modifydataobject;
    }

    public Integer getOwndataobject() {
        return owndataobject;
    }

    public void setOwndataobject(Integer owndataobject) {
        this.owndataobject = owndataobject;
    }

    public Integer getDownloaddataobject() {
        return downloaddataobject;
    }

    public void setDownloaddataobject(Integer downloaddataobject) {
        this.downloaddataobject = downloaddataobject;
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

    public Dataobject getDataobjectrefid() {
        return dataobjectrefid;
    }

    public void setDataobjectrefid(Dataobject dataobjectrefid) {
        this.dataobjectrefid = dataobjectrefid;
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

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof Dataobjectsharerel)) {
            return false;
        }
        Dataobjectsharerel other = (Dataobjectsharerel) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return "Dataobjectsharerel{" +
                "id=" + id +
                ", readdataobjectsystemmetadata=" + readdataobjectsystemmetadata +
                ", readdataobjectmetadata=" + readdataobjectmetadata +
                ", readdataobject=" + readdataobject +
                ", createdataobjectmetadata=" + createdataobjectmetadata +
                ", modifydataobjectmetadata=" + modifydataobjectmetadata +
                ", deletedataobject=" + deletedataobject +
                ", administerdataobject=" + administerdataobject +
                ", modifydataobject=" + modifydataobject +
                ", owndataobject=" + owndataobject +
                ", downloaddataobject=" + downloaddataobject +
                ", markedfordelete=" + markedfordelete +
                ", modifiedTs=" + modifiedTs +
                ", createdTs=" + createdTs +
                ", dataobjectrefid=" + dataobjectrefid +
                ", personrefid=" + personrefid +
                ", projectrefid=" + projectrefid +
                '}';
    }
}
