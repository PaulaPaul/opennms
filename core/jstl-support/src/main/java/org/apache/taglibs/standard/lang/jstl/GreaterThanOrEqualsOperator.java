/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * glassfish/bootstrap/legal/CDDLv1.0.txt or
 * https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * glassfish/bootstrap/legal/CDDLv1.0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 *
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 *
 * Portions Copyright Apache Software Foundation.
 */ 

package org.apache.taglibs.standard.lang.jstl;

/**
 *
 * <p>The implementation of the greater than or equals operator
 * 
 * @author Nathan Abramson - Art Technology Group
 * @version $Change: 181177 $$DateTime: 2001/06/26 08:45:09 $$Author: kchung $
 **/

public class GreaterThanOrEqualsOperator
  extends RelationalOperator
{
  //-------------------------------------
  // Singleton
  //-------------------------------------

  public static final GreaterThanOrEqualsOperator SINGLETON =
    new GreaterThanOrEqualsOperator ();

  //-------------------------------------
  /**
   *
   * Constructor
   **/
  public GreaterThanOrEqualsOperator ()
  {
  }

  //-------------------------------------
  // Expression methods
  //-------------------------------------
  /**
   *
   * Returns the symbol representing the operator
   **/
  public String getOperatorSymbol ()
  {
    return ">=";
  }

  //-------------------------------------
  /**
   *
   * Applies the operator to the given value
   **/
  public Object apply (Object pLeft,
		       Object pRight,
		       Object pContext,
		       Logger pLogger)
    throws ELException
  {
    if (pLeft == pRight) {
      return Boolean.TRUE;
    }
    else if (pLeft == null ||
	     pRight == null) {
      return Boolean.FALSE;
    }
    else {
      return super.apply (pLeft, pRight, pContext, pLogger);
    }
  }

  //-------------------------------------
  /**
   *
   * Applies the operator to the given double values
   **/
  public boolean apply (double pLeft,
			double pRight,
			Logger pLogger)
  {
    return pLeft >= pRight;
  }
  
  //-------------------------------------
  /**
   *
   * Applies the operator to the given long values
   **/
  public boolean apply (long pLeft,
			long pRight,
			Logger pLogger)
  {
    return pLeft >= pRight;
  }
  
  //-------------------------------------
  /**
   *
   * Applies the operator to the given String values
   **/
  public boolean apply (String pLeft,
			String pRight,
			Logger pLogger)
  {
    return pLeft.compareTo (pRight) >= 0;
  }

  //-------------------------------------
}
