/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.sduclouddb;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.Date;

/**
 *
 * @author bjhj
 */
@Entity
@Table(name = "Notification")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "Notification.findAll", query = "SELECT l FROM Notification l")
    , @NamedQuery(name = "Notification.findById", query = "SELECT l FROM Notification l WHERE l.id = :id")
    , @NamedQuery(name = "Notification.findByNotificationtext", query = "SELECT l FROM Notification l WHERE l.Notificationtext = :Notificationtext")
    , @NamedQuery(name = "Notification.findByVieew", query = "SELECT l FROM Notification l WHERE l.viewed = :viewed")
    , @NamedQuery(name = "Notification.findByMarkedfordelete", query = "SELECT l FROM Notification l WHERE l.markedfordelete = :markedfordelete")
    , @NamedQuery(name = "Notification.findByModifiedTs", query = "SELECT l FROM Notification l WHERE l.modifiedTs = :modifiedTs")
    , @NamedQuery(name = "Notification.findByCreatedTs", query = "SELECT l FROM Notification l WHERE l.createdTs = :createdTs")})
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "personrefid")
    private int personrefid;
    @Column(name = "notificationtext")
    private String notificationtext;
    @Column(name = "viewed")
    private Integer viewed;
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

    public Notification() {
    }

    public Notification(Integer id) {
        this.id = id;
    }

    public Notification(Integer id, Date modifiedTs, Date createdTs) {
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

    public String getNotificationtext() {
        return notificationtext;
    }

    public void setNotificationtext(String notificationtext) {
        this.notificationtext = notificationtext;
    }

    public Integer getViewed() {
        return viewed;
    }

    public void setViewed(Integer viewed) {
        this.viewed = viewed;
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

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    public int getPersonrefid() {
        return personrefid;
    }

    public void setPersonrefid(int personrefid) {
        this.personrefid = personrefid;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof Notification)) {
            return false;
        }
        Notification other = (Notification) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String
    toString() {
        return "Notification{" +
                "id=" + id +
                ", Notificationtext='" + notificationtext + '\'' +
                ", viewed=" + viewed +
                ", markedfordelete=" + markedfordelete +
                ", modifiedTs=" + modifiedTs +
                ", createdTs=" + createdTs +
                '}';
    }
}
