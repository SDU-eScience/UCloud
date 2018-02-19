/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.jpa.icatdb;

import java.io.Serializable;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author bjhj
 */
@Entity
@Table(name = "r_ticket_allowed_groups")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RTicketAllowedGroups.findAll", query = "SELECT r FROM RTicketAllowedGroups r")
    , @NamedQuery(name = "RTicketAllowedGroups.findByTicketId", query = "SELECT r FROM RTicketAllowedGroups r WHERE r.ticketId = :ticketId")
    , @NamedQuery(name = "RTicketAllowedGroups.findByGroupName", query = "SELECT r FROM RTicketAllowedGroups r WHERE r.groupName = :groupName")
    , @NamedQuery(name = "RTicketAllowedGroups.findById", query = "SELECT r FROM RTicketAllowedGroups r WHERE r.id = :id")})
public class RTicketAllowedGroups implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "ticket_id")
    private long ticketId;
    @Basic(optional = false)
    @Column(name = "group_name")
    private String groupName;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RTicketAllowedGroups() {
    }

    public RTicketAllowedGroups(Integer id) {
        this.id = id;
    }

    public RTicketAllowedGroups(Integer id, long ticketId, String groupName) {
        this.id = id;
        this.ticketId = ticketId;
        this.groupName = groupName;
    }

    public long getTicketId() {
        return ticketId;
    }

    public void setTicketId(long ticketId) {
        this.ticketId = ticketId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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
        if (!(object instanceof RTicketAllowedGroups)) {
            return false;
        }
        RTicketAllowedGroups other = (RTicketAllowedGroups) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RTicketAllowedGroups[ id=" + id + " ]";
    }
    
}
