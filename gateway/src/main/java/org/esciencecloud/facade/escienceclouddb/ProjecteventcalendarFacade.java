package org.esciencecloud.facade.escienceclouddb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.esciencecloud.jpa.escienceclouddb.Projecteventcalendar;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;

public class ProjecteventcalendarFacade {
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ProjecteventcalendarFacade.class);

    public boolean createProjecteventcalendar(Projecteventcalendar entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean updateProjecteventcalendar(Projecteventcalendar entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.merge(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean deleteProjecteventcalendarById(int id) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.remove(em.find(Projecteventcalendar.class, id));
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public String listAllActiveProjecteventcalendar() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        List<Projecteventcalendar> entityList = new ArrayList<>();
        Query nq1 = em.createNamedQuery("Projecteventcalendar.findByActive");
        nq1.setParameter("active", 1);
        entityList.addAll(nq1.getResultList());
        em.close();
        emf.close();
        return entityListToJson(entityList);
    }

    public String listAllProjecteventcalendar() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        List<Projecteventcalendar> entityList = new ArrayList<>();
        Query nq1 = em.createNamedQuery("Projecteventcalendar.findAll");
        entityList.addAll(nq1.getResultList());
        em.close();
        emf.close();
        return entityListToJson(entityList);
    }

    public String entityToJson(Projecteventcalendar entity) {
        String json = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            json = mapper.writeValueAsString(entity);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return json;
    }

    public String entityListToJson(List<Projecteventcalendar> entityList) {
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