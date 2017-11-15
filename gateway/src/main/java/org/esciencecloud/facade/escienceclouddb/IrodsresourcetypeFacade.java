package org.esciencecloud.facade.escienceclouddb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.esciencecloud.jpa.escienceclouddb.Irodsresourcetype;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;

public class IrodsresourcetypeFacade {
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(IrodsresourcetypeFacade.class);

    public boolean createIrodsresourcetype(Irodsresourcetype entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean updateIrodsresourcetype(Irodsresourcetype entity) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.merge(entity);
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public boolean deleteIrodsresourcetypeById(int id) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.remove(em.find(Irodsresourcetype.class, id));
        em.getTransaction().commit();
        em.close();
        emf.close();
        return true;
    }

    public String listAllActiveIrodsresourcetype() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        List<Irodsresourcetype> entityList = new ArrayList<>();
        Query nq1 = em.createNamedQuery("Irodsresourcetype.findByActive");
        nq1.setParameter("active", 1);
        entityList.addAll(nq1.getResultList());
        em.close();
        emf.close();
        return entityListToJson(entityList);
    }

    public String listAllIrodsresourcetype() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eScienceCloudJPAPU");
        EntityManager em = emf.createEntityManager();
        List<Irodsresourcetype> entityList = new ArrayList<>();
        Query nq1 = em.createNamedQuery("Irodsresourcetype.findAll");
        entityList.addAll(nq1.getResultList());
        em.close();
        emf.close();
        return entityListToJson(entityList);
    }

    public String entityToJson(Irodsresourcetype entity) {
        String json = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            json = mapper.writeValueAsString(entity);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return json;
    }

    public String entityListToJson(List<Irodsresourcetype> entityList) {
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