package org.esciencecloud.facade.escienceclouddb;

import org.esciencecloud.jpa.escienceclouddb.Subsystemcommandstatus;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;

public class SubsystemcommandstatusFacade {
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SubsystemcommandstatusFacade.class);

    public boolean createSubsystemcommandstatus(Subsystemcommandstatus entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean updateSubsystemcommandstatus(Subsystemcommandstatus entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.merge(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean deleteSubsystemcommandstatusById(Subsystemcommandstatus entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.remove(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public List<Subsystemcommandstatus> listAllSubsystemcommandstatus() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        List<Subsystemcommandstatus> entityList = new ArrayList<>();
        Query nq1 = em.createNamedQuery("Subsystemcommandstatus.findAll");
        entityList.addAll(nq1.getResultList());
        em.close();
        emf.close();
        return entityList;
    }
}