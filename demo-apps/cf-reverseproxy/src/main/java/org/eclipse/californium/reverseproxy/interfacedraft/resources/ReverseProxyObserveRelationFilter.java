package org.eclipse.californium.reverseproxy.interfacedraft.resources;

import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.observe.ObserveRelationFilter;

import java.util.List;

public class ReverseProxyObserveRelationFilter implements ObserveRelationFilter {

    private List<InterfaceObserveRelation> allowed;
    public ReverseProxyObserveRelationFilter(List<InterfaceObserveRelation> allowed){
        this.allowed = allowed;
    }
    @Override
    public boolean accept(ObserveRelation relation) {
        for(InterfaceObserveRelation r : allowed)
        {
            ObserveRelation tmp = (ObserveRelation) r;
            if(tmp.equals(relation)) return true;
        }
        return false;
    }
}
