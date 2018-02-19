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
import javax.persistence.Lob;
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
@Table(name = "app")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "App.findAll", query = "SELECT a FROM App a")
    , @NamedQuery(name = "App.findById", query = "SELECT a FROM App a WHERE a.id = :id")
    , @NamedQuery(name = "App.findByAppname", query = "SELECT a FROM App a WHERE a.appname = :appname")
    , @NamedQuery(name = "App.findByAppdescriptiontext", query = "SELECT a FROM App a WHERE a.appdescriptiontext = :appdescriptiontext")
    , @NamedQuery(name = "App.findByActive", query = "SELECT a FROM App a WHERE a.active = :active")
    , @NamedQuery(name = "App.findByMarkedfordelete", query = "SELECT a FROM App a WHERE a.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "App.findByModifiedTs", query = "SELECT a FROM App a WHERE a.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "App.findByCreatedTs", query = "SELECT a FROM App a WHERE a.createdTs = :createdTs")
    , @NamedQuery(name = "App.findByGiturl", query = "SELECT a FROM App a WHERE a.giturl = :giturl")
    , @NamedQuery(name = "App.findByPrepped", query = "SELECT a FROM App a WHERE a.prepped = :prepped")})
public class App implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "appname")
    private String appname;
    @Column(name = "appdescriptiontext")
    private String appdescriptiontext;
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
    @Column(name = "giturl")
    private String giturl;
    @Lob
    @Column(name = "cwlfile")
    private byte[] cwlfile;
    @Column(name = "prepped")
    private Integer prepped;
    @JoinColumn(name = "personrefid", referencedColumnName = "id")
    @ManyToOne
    private Person personrefid;

    public App() {
    }

    public App(Integer id) {
        this.id = id;
    }

    public App(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getAppname() {
        return appname;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public String getAppdescriptiontext() {
        return appdescriptiontext;
    }

    public void setAppdescriptiontext(String appdescriptiontext) {
        this.appdescriptiontext = appdescriptiontext;
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

    public String getGiturl() {
        return giturl;
    }

    public void setGiturl(String giturl) {
        this.giturl = giturl;
    }

    public byte[] getCwlfile() {
        return cwlfile;
    }

    public void setCwlfile(byte[] cwlfile) {
        this.cwlfile = cwlfile;
    }

    public Integer getPrepped() {
        return prepped;
    }

    public void setPrepped(Integer prepped) {
        this.prepped = prepped;
    }

    public Person getPersonrefid() {
        return personrefid;
    }

    public void setPersonrefid(Person personrefid) {
        this.personrefid = personrefid;
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
        if (!(object instanceof App)) {
            return false;
        }
        App other = (App) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.sducloud.jpa.sduclouddb.App[ id=" + id + " ]";
    }
    
}
