/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.sdu.cloud.models.arangodb;

/**
 *
 * @author bhjakobsen
 */
public abstract class AbstractFacade<T> {
    private Class<T> edgeClass;

    public AbstractFacade(Class<T> entityClass) {
        this.edgeClass = edgeClass;
    }


    
}
