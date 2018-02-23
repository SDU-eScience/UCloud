package dk.sdu.cloud.TempHandlers;


import dk.sdu.cloud.jpa.sduclouddb.Dataobjectsharerel;
import dk.sdu.cloud.jpa.sduclouddb.Person;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class TestShares {
    public static void main(String[] args) {

        EntityManagerFactory emf = Persistence.createEntityManagerFactory("SduClouddbJpaPU");
        EntityManager em = emf.createEntityManager();

        //Query nq1 = em.createNamedQuery("RDataMain.findByNotRescId");
        //nq1.setParameter("rescId", 10014);

        Person person = em.find(Person.class, 2);

        for (Dataobjectsharerel dataobjectsharerel : person.getDataobjectsharerelList()) {
            if (dataobjectsharerel.getPersonrefid() != null) {
                System.err.println(dataobjectsharerel.getDataobjectcollectionrefid().getDataobjectcollectionurl());

            }
        }


        em.close();
        emf.close();

    }
}
