/*
 * This file is part of Transitime.org
 * 
 * Transitime.org is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL) as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * Transitime.org is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Transitime.org .  If not, see <http://www.gnu.org/licenses/>.
 */
package org.transitime.reports;

import java.util.Date;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.type.Type;
import org.hibernate.type.DoubleType;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitime.db.hibernate.HibernateUtils;
import org.transitime.db.structs.PredictionAccuracy;

/**
 * To find route performance information.
 * For now, route performance is the percentage of predictions for a route which are ontime.
 *
 * @author Simon Jacobs
 *
 */
public class RoutePerformanceQuery {
  private Session session;
  
  private static final Logger logger = LoggerFactory
      .getLogger(RoutePerformanceQuery.class);
  
  public static final String PREDICTION_TYPE_AFFECTED = "AffectedByWaitStop";
  public static final String PREDICTION_TYPE_NOT_AFFECTED = "NotAffectedByWaitStop";
  
  public List<Object[]> query(String agencyId, Date startDate, Date endDate, double allowableEarlyMin, double allowableLateMin, String predictionType, String predictionSource) {
    
    int msecLo = (int) (allowableEarlyMin * 60 * 1000);
    int msecHi = (int) (allowableLateMin * 60 * 1000);
    
    // Project to: # of predictions in which route is on time / # of predictions
    // for route. This cannot be done with pure Criteria API. This could be
    // moved to a separate class or XML file.
    String sqlProjection = "avg(predictionAccuracyMsecs BETWEEN " + Integer.toString(msecLo) + " AND "
        + Integer.toString(msecHi) + ") AS avgAccuracy";

    try {
      session = HibernateUtils.getSession(agencyId);
            
      Projection proj = Projections.projectionList()
          .add(Projections.groupProperty("routeId"), "routeId")
          .add(Projections.sqlProjection(sqlProjection,
              new String[] { "avgAccuracy" }, 
              new Type[] { DoubleType.INSTANCE }), "performance");
          
      Criteria criteria = session.createCriteria(PredictionAccuracy.class)
        .setProjection(proj)
        .add(Restrictions.between("arrivalDepartureTime", startDate, endDate));
      
      if (predictionType == PREDICTION_TYPE_AFFECTED)
          criteria.add(Restrictions.eq("affectedByWaitStop", true));
      else if (predictionType == PREDICTION_TYPE_NOT_AFFECTED)
          criteria.add(Restrictions.eq("affectedByWaitStop", false));
      
      if (predictionSource != "")
          criteria.add(Restrictions.eq("predictionSource", predictionSource));
      
      criteria.addOrder(Order.desc("performance"));
      
      criteria.setResultTransformer(CriteriaSpecification.ALIAS_TO_ENTITY_MAP);
          
      @SuppressWarnings("unchecked")
      List<Object[]> results = criteria.list();

      return results;
    }
    catch(HibernateException e) {
      logger.error(e.toString());
      return null;
    }
    finally {
      session.close();
    }
  }

}
