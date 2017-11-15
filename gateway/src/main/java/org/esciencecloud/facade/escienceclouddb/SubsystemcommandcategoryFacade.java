package org.esciencecloud.facade.escienceclouddb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.esciencecloud.jpa.escienceclouddb.Subsystemcommandcategory;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;

public class SubsystemcommandcategoryFacade {
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SubsystemcommandcategoryFacade.class);

    public boolean createSubsystemcommandcategory(Subsystemcommandcategory entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean updateSubsystemcommandcategory(Subsystemcommandcategory entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.merge(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean deleteSubsystemcommandcategoryById(int id) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.remove(em.find(Subsystemcommandcategory.class, id));
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public String listAllActiveSubsystemcommandcategory() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        List<Subsystemcommandcategory> entityList = new ArrayList<>();
        Query nq1 = em.createNamedQuery("Subsystemcommandcategory.findByActive");
        nq1.setParameter("active", 1);
        entityList.addAll(nq1.getResultList());
        em.close();
        emf.close();
        return entityListToJson(entityList);
    }

    public String listAllSubsystemcommandcategory() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        List<Subsystemcommandcategory> entityList = new ArrayList<>();
        Query nq1 = em.createNamedQuery("Subsystemcommandcategory.findAll");
        entityList.addAll(nq1.getResultList());
        em.close();
        emf.close();
        return entityListToJson(entityList);
    }

    public String entityToJson(Subsystemcommandcategory entity) {
        String json = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            json = mapper.writeValueAsString(entity);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return json;
    }

    public String entityListToJson(List<Subsystemcommandcategory> entityList) {
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