package org.esciencecloud.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GenEscienceclouddbFacade {

    public static List<String> getTableNames() {
        List<String> listTablenNames = new ArrayList<>();

        listTablenNames.add("appusermessagesubscriptiontype");
        listTablenNames.add("devstage");
        listTablenNames.add("irodsaccesstype");
        listTablenNames.add("irodsauditpep");
        listTablenNames.add("irodsfileextension");
        listTablenNames.add("irodsresourcetype");
        listTablenNames.add("irodsruleexectype");
        listTablenNames.add("logintype");
        listTablenNames.add("org");
        listTablenNames.add("person");
        listTablenNames.add("personappusermessagesubscriptiontyperel");
        listTablenNames.add("personsessionhistory");
        listTablenNames.add("project");
        listTablenNames.add("projectdocument");
        listTablenNames.add("projecteventcalendar");
        listTablenNames.add("projectorgrel");
        listTablenNames.add("projectpersonrel");
        listTablenNames.add("projectprojectdocumentrel");
        listTablenNames.add("projectprojectresearchtyperel");
        listTablenNames.add("projectpublicationrel");
        listTablenNames.add("projectresearchtype");
        listTablenNames.add("projectrole");
        listTablenNames.add("publication");
        listTablenNames.add("server");
        listTablenNames.add("software");
        listTablenNames.add("subsystem");
        listTablenNames.add("subsystemcommand");
        listTablenNames.add("subsystemcommandcategory");
        listTablenNames.add("subsystemcommandqueue");
        listTablenNames.add("subsystemcommandstatus");
        listTablenNames.add("systemrole");
        listTablenNames.add("systemrolepersonrel");

        return listTablenNames;
    }

    public static void createFacade(String packetName, String className, String filePathString, String jpaClass) {
        List<String> lines = new ArrayList<>();

        lines.add("package " + packetName + ";");
        lines.add("");
        lines.add("import org.esciencecloud.jpa.escienceclouddb." + jpaClass + ";");
        lines.add("import javax.persistence.EntityManager;");
        lines.add("import javax.persistence.EntityManagerFactory;");
        lines.add("import javax.persistence.Persistence;");
        lines.add("import javax.persistence.Query;");
        lines.add("import java.util.ArrayList;");
        lines.add("import java.util.List;");
        lines.add("");
        lines.add("public class " + className + "{");
        lines.add("private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(" + className + ".class);");

        lines.add("public boolean create" + jpaClass + "(" + jpaClass + " entity){");
        lines.add("EntityManagerFactory emf = Persistence.createEntityManagerFactory(\"eScienceCloudJPAPU\");");
        lines.add("EntityManager em = emf.createEntityManager();");
        lines.add("");
        lines.add("em.getTransaction().begin();");
        lines.add("em.persist(entity);");
        lines.add("em.getTransaction().commit();");
        lines.add("em.close();");
        lines.add("emf.close();");
        lines.add("return true;}");
        lines.add("");
        lines.add("");
        lines.add("public boolean update" + jpaClass + "(" + jpaClass + " entity){");
        lines.add("EntityManagerFactory emf = Persistence.createEntityManagerFactory(\"eScienceCloudJPAPU\");");
        lines.add("EntityManager em = emf.createEntityManager();");
        lines.add("");
        lines.add("em.getTransaction().begin();");
        lines.add("em.merge(entity);");
        lines.add("em.getTransaction().commit();");
        lines.add("em.close();");
        lines.add("emf.close();");
        lines.add("return true;}");
        lines.add("");
        lines.add("");
        lines.add("public boolean delete" + jpaClass + "ById(" + jpaClass + " entity){");
        lines.add("EntityManagerFactory emf = Persistence.createEntityManagerFactory(\"eScienceCloudJPAPU\");");
        lines.add("EntityManager em = emf.createEntityManager();");
        lines.add("");
        lines.add("em.getTransaction().begin();");
        lines.add("em.remove(entity);");
        lines.add("em.getTransaction().commit();");
        lines.add("em.close();");
        lines.add("emf.close();");
        lines.add("return true;}");
        lines.add("");
        lines.add("");
        lines.add("public List<" + jpaClass + "> listAll" + jpaClass + " (){");
        lines.add("EntityManagerFactory emf = Persistence.createEntityManagerFactory(\"eScienceCloudJPAPU\");");
        lines.add("EntityManager em = emf.createEntityManager();");
        lines.add("");
        lines.add("List<" + jpaClass + "> entityList = new ArrayList<>();");
        lines.add("Query nq1 = em.createNamedQuery(\"" + jpaClass + ".findAll\");");
        lines.add("entityList.addAll(nq1.getResultList());");
        lines.add("em.close();");
        lines.add("emf.close();");
        lines.add("return entityList;}");

        //lastline
        lines.add("}");

        Writer writer = null;

        try {
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(filePathString), "utf-8"));

            for (String s : lines) {
                writer.write(s);
            }
        } catch (IOException ex) {
            // report
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (Exception ex) {/*ignore*/}
        }


    }

    public static void main(String[] args) {
        for (String s : getTableNames()) {
            //set classname
            String jpaClass = s.substring(0, 1).toUpperCase() + s.substring(1);
            String className = s.substring(0, 1).toUpperCase() + s.substring(1) + "Facade";

            String fileName = s.substring(0, 1).toUpperCase() + s.substring(1) + "Facade.java";
            //set filename

            String packetName = "org.esciencecloud.facade.escienceclouddb";
            String filePathString = "/Users/bjhj/imadaprojects/java/eScienceCloudJpa/src/org/esciencecloud/facade/escienceclouddb/" + fileName;

            Path path = Paths.get(filePathString);


            if (!Files.exists(path)) {
                System.err.println("file " + filePathString);
                createFacade(packetName, className, filePathString, jpaClass);
            }
        }
    }

}
