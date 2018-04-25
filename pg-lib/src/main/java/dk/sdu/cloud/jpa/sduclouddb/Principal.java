/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
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
@Table(name = "principal")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Principal.findAll", query = "SELECT p FROM Principal p")
    , @NamedQuery(name = "Principal.findById", query = "SELECT p FROM Principal p WHERE p.id = :id")
    , @NamedQuery(name = "Principal.findByPrincipaltitle", query = "SELECT p FROM Principal p WHERE p.principaltitle = :principaltitle")
    , @NamedQuery(name = "Principal.findByPrincipalfirstnames", query = "SELECT p FROM Principal p WHERE p.principalfirstnames = :principalfirstnames")
    , @NamedQuery(name = "Principal.findByPrincipallastname", query = "SELECT p FROM Principal p WHERE p.principallastname = :principallastname")
    , @NamedQuery(name = "Principal.findByPrincipalphoneno", query = "SELECT p FROM Principal p WHERE p.principalphoneno = :principalphoneno")
    , @NamedQuery(name = "Principal.findByLogintyperefid", query = "SELECT p FROM Principal p WHERE p.logintyperefid = :logintyperefid")
    , @NamedQuery(name = "Principal.findByLatitude", query = "SELECT p FROM Principal p WHERE p.latitude = :latitude")
    , @NamedQuery(name = "Principal.findByLongitude", query = "SELECT p FROM Principal p WHERE p.longitude = :longitude")
    , @NamedQuery(name = "Principal.findByActive", query = "SELECT p FROM Principal p WHERE p.active = :active")
    , @NamedQuery(name = "Principal.findByOrcid", query = "SELECT p FROM Principal p WHERE p.orcid = :orcid")
    , @NamedQuery(name = "Principal.findByPersonFullname", query = "SELECT p FROM Principal p WHERE p.personFullname = :personFullname")
    , @NamedQuery(name = "Principal.findByMarkedfordelete", query = "SELECT p FROM Principal p WHERE p.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Principal.findByModifiedTs", query = "SELECT p FROM Principal p WHERE p.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Principal.findByCreatedTs", query = "SELECT p FROM Principal p WHERE p.createdTs = :createdTs")
    , @NamedQuery(name = "Principal.findByUsername", query = "SELECT p FROM Principal p WHERE p.username = :username")
    , @NamedQuery(name = "Principal.findByRecordState", query = "SELECT p FROM Principal p WHERE p.recordState = :recordState")
    , @NamedQuery(name = "Principal.findByExtWayfId", query = "SELECT p FROM Principal p WHERE p.extWayfId = :extWayfId")})
public class Principal implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "principaltitle")
    private String principaltitle;
    @Column(name = "principalfirstnames")
    private String principalfirstnames;
    @Column(name = "principallastname")
    private String principallastname;
    @Column(name = "principalphoneno")
    private String principalphoneno;
    @Column(name = "logintyperefid")
    private Integer logintyperefid;
    // @Max(value=?)  @Min(value=?)//if you know range of your decimal fields consider using these annotations to enforce field validation
    @Column(name = "latitude")
    private BigDecimal latitude;
    @Column(name = "longitude")
    private BigDecimal longitude;
    @Column(name = "active")
    private Integer active;
    @Column(name = "orcid")
    private String orcid;
    @Column(name = "person_fullname")
    private String personFullname;
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
    @Column(name = "username")
    private String username;
    @Basic(optional = false)
    @Column(name = "record_state")
    private int recordState;
    @Lob
    @Column(name = "salt")
    private byte[] salt;
    @Lob
    @Column(name = "hashed_password")
    private byte[] hashedPassword;
    @Column(name = "ext_wayf_id")
    private String extWayfId;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "personrefid")
    private List<ProjectPrincipalRelation> projectPrincipalRelationList;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "principalrefid")
    private List<PrincipalSystemroleRelation> principalSystemroleRelationList;
    @OneToMany(mappedBy = "principalrefid")
    private List<ProjectEventCalendar> projectEventCalendarList;
    @JoinColumn(name = "orgrefid", referencedColumnName = "id")
    @ManyToOne
    private Org orgrefid;
    @OneToMany(mappedBy = "principalrefid")
    private List<Notification> notificationList;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "personrefid")
    private List<SystemrolePersonRelation> systemrolePersonRelationList;
    @OneToMany(mappedBy = "principalrefid")
    private List<Email> emailList;
    @OneToMany(mappedBy = "principalrefid")
    private List<App> appList;
    @OneToMany(mappedBy = "personrefid")
    private List<Uploadtransaction> uploadtransactionList;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "principalrefid")
    private List<PrincipalNotificationSubscriptiontypeRelation> principalNotificationSubscriptiontypeRelationList;
    @OneToMany(mappedBy = "principalrefid")
    private List<DataTransferHeader> dataTransferHeaderList;

    public Principal() {
    }

    public Principal(Integer id) {
        this.id = id;
    }

    public Principal(Integer id, Date modifiedTs, Date createdTs, int recordState) {
        this.id = id;
        this.modifiedTs = modifiedTs;
        this.createdTs = createdTs;
        this.recordState = recordState;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPrincipaltitle() {
        return principaltitle;
    }

    public void setPrincipaltitle(String principaltitle) {
        this.principaltitle = principaltitle;
    }

    public String getPrincipalfirstnames() {
        return principalfirstnames;
    }

    public void setPrincipalfirstnames(String principalfirstnames) {
        this.principalfirstnames = principalfirstnames;
    }

    public String getPrincipallastname() {
        return principallastname;
    }

    public void setPrincipallastname(String principallastname) {
        this.principallastname = principallastname;
    }

    public String getPrincipalphoneno() {
        return principalphoneno;
    }

    public void setPrincipalphoneno(String principalphoneno) {
        this.principalphoneno = principalphoneno;
    }

    public Integer getLogintyperefid() {
        return logintyperefid;
    }

    public void setLogintyperefid(Integer logintyperefid) {
        this.logintyperefid = logintyperefid;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
    }

    public String getOrcid() {
        return orcid;
    }

    public void setOrcid(String orcid) {
        this.orcid = orcid;
    }

    public String getPersonFullname() {
        return personFullname;
    }

    public void setPersonFullname(String personFullname) {
        this.personFullname = personFullname;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getRecordState() {
        return recordState;
    }

    public void setRecordState(int recordState) {
        this.recordState = recordState;
    }

    public byte[] getSalt() {
        return salt;
    }

    public void setSalt(byte[] salt) {
        this.salt = salt;
    }

    public byte[] getHashedPassword() {
        return hashedPassword;
    }

    public void setHashedPassword(byte[] hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    public String getExtWayfId() {
        return extWayfId;
    }

    public void setExtWayfId(String extWayfId) {
        this.extWayfId = extWayfId;
    }

    @XmlTransient
    public List<ProjectPrincipalRelation> getProjectPrincipalRelationList() {
        return projectPrincipalRelationList;
    }

    public void setProjectPrincipalRelationList(List<ProjectPrincipalRelation> projectPrincipalRelationList) {
        this.projectPrincipalRelationList = projectPrincipalRelationList;
    }

    @XmlTransient
    public List<PrincipalSystemroleRelation> getPrincipalSystemroleRelationList() {
        return principalSystemroleRelationList;
    }

    public void setPrincipalSystemroleRelationList(List<PrincipalSystemroleRelation> principalSystemroleRelationList) {
        this.principalSystemroleRelationList = principalSystemroleRelationList;
    }

    @XmlTransient
    public List<ProjectEventCalendar> getProjectEventCalendarList() {
        return projectEventCalendarList;
    }

    public void setProjectEventCalendarList(List<ProjectEventCalendar> projectEventCalendarList) {
        this.projectEventCalendarList = projectEventCalendarList;
    }

    public Org getOrgrefid() {
        return orgrefid;
    }

    public void setOrgrefid(Org orgrefid) {
        this.orgrefid = orgrefid;
    }

    @XmlTransient
    public List<Notification> getNotificationList() {
        return notificationList;
    }

    public void setNotificationList(List<Notification> notificationList) {
        this.notificationList = notificationList;
    }

    @XmlTransient
    public List<SystemrolePersonRelation> getSystemrolePersonRelationList() {
        return systemrolePersonRelationList;
    }

    public void setSystemrolePersonRelationList(List<SystemrolePersonRelation> systemrolePersonRelationList) {
        this.systemrolePersonRelationList = systemrolePersonRelationList;
    }

    @XmlTransient
    public List<Email> getEmailList() {
        return emailList;
    }

    public void setEmailList(List<Email> emailList) {
        this.emailList = emailList;
    }

    @XmlTransient
    public List<App> getAppList() {
        return appList;
    }

    public void setAppList(List<App> appList) {
        this.appList = appList;
    }

    @XmlTransient
    public List<Uploadtransaction> getUploadtransactionList() {
        return uploadtransactionList;
    }

    public void setUploadtransactionList(List<Uploadtransaction> uploadtransactionList) {
        this.uploadtransactionList = uploadtransactionList;
    }

    @XmlTransient
    public List<PrincipalNotificationSubscriptiontypeRelation> getPrincipalNotificationSubscriptiontypeRelationList() {
        return principalNotificationSubscriptiontypeRelationList;
    }

    public void setPrincipalNotificationSubscriptiontypeRelationList(List<PrincipalNotificationSubscriptiontypeRelation> principalNotificationSubscriptiontypeRelationList) {
        this.principalNotificationSubscriptiontypeRelationList = principalNotificationSubscriptiontypeRelationList;
    }

    @XmlTransient
    public List<DataTransferHeader> getDataTransferHeaderList() {
        return dataTransferHeaderList;
    }

    public void setDataTransferHeaderList(List<DataTransferHeader> dataTransferHeaderList) {
        this.dataTransferHeaderList = dataTransferHeaderList;
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
        if (!(object instanceof Principal)) {
            return false;
        }
        Principal other = (Principal) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "dk.sdu.cloud.jpa.sduclouddb.Principal[ id=" + id + " ]";
    }
    
}
