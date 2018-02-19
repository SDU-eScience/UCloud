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
@Table(name = "r_ticket_allowed_hosts")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "RTicketAllowedHosts.findAll", query = "SELECT r FROM RTicketAllowedHosts r")
    , @NamedQuery(name = "RTicketAllowedHosts.findByTicketId", query = "SELECT r FROM RTicketAllowedHosts r WHERE r.ticketId = :ticketId")
    , @NamedQuery(name = "RTicketAllowedHosts.findByHost", query = "SELECT r FROM RTicketAllowedHosts r WHERE r.host = :host")
    , @NamedQuery(name = "RTicketAllowedHosts.findById", query = "SELECT r FROM RTicketAllowedHosts r WHERE r.id = :id")})
public class RTicketAllowedHosts implements Serializable {

    private static final long serialVersionUID = 1L;
    @Basic(optional = false)
    @Column(name = "ticket_id")
    private long ticketId;
    @Column(name = "host")
    private String host;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    public RTicketAllowedHosts() {
    }

    public RTicketAllowedHosts(Integer id) {
        this.id = id;
    }

    public RTicketAllowedHosts(Integer id, long ticketId) {
        this.id = id;
        this.ticketId = ticketId;
    }

    public long getTicketId() {
        return ticketId;
    }

    public void setTicketId(long ticketId) {
        this.ticketId = ticketId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
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
        if (!(object instanceof RTicketAllowedHosts)) {
            return false;
        }
        RTicketAllowedHosts other = (RTicketAllowedHosts) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "icatjpa.RTicketAllowedHosts[ id=" + id + " ]";
    }
    
}
