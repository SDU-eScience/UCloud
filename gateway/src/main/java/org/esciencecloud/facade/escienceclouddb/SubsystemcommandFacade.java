package org.esciencecloud.facade.escienceclouddb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.esciencecloud.jpa.escienceclouddb.Subsystemcommand;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;

public class SubsystemcommandFacade {
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SubsystemcommandFacade.class);

    public boolean createSubsystemcommand(Subsystemcommand entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean updateSubsystemcommand(Subsystemcommand entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.merge(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean deleteSubsystemcommandById(int id) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.remove(em.find(Subsystemcommand.class, id));
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public String listAllActiveSubsystemcommand() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        List<Subsystemcommand> entityList = new ArrayList<>();
        Query nq1 = em.createNamedQuery("Subsystemcommand.findByActive");
        nq1.setParameter("active", 1);
        entityList.addAll(nq1.getResultList());
        em.close();
        emf.close();
        return entityListToJson(entityList);
    }

    public String listAllSubsystemcommand() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        List<Subsystemcommand> entityList = new ArrayList<>();
        Query nq1 = em.createNamedQuery("Subsystemcommand.findAll");
        entityList.addAll(nq1.getResultList());
        em.close();
        emf.close();
        return entityListToJson(entityList);
    }

    public String entityToJson(Subsystemcommand entity) {
        String json = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            json = mapper.writeValueAsString(entity);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return json;
    }

    public String entityListToJson(List<Subsystemcommand> entityList) {
        String json = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            json = mapper.writeValueAsString(entityList);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return json;
    }
}