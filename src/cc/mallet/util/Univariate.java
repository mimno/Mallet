/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.util;

import java.util.logging.*;

import cc.mallet.util.MalletLogger;

// Obtained from http://www.stat.vt.edu/~sundar/java/code/Univariate.html
// August 2002

/** * @(#)Univariate.java * * DAMAGE (c) 2000 by Sundar Dorai-Raj
  * * @author Sundar Dorai-Raj
  * * Email: sdoraira@vt.edu
  * * This program is free software; you can redistribute it and/or
  * * modify it under the terms of the GNU General Public License 
  * * as published by the Free Software Foundation; either version 2 
  * * of the License, or (at your option) any later version, 
  * * provided that any use properly credits the author. 
  * * This program is distributed in the hope that it will be useful,
  * * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  * * GNU General Public License for more details at http://www.gnu.org * * */

public class Univariate {
	private static Logger logger = MalletLogger.getLogger(Univariate.class.getName());
  private double[] x,sortx;
  private double[] summary=new double[6];
  private boolean isSorted=false;
  public double[] five=new double[5];
  private int n;
  private double mean,variance,stdev;
  private double median,min,Q1,Q3,max;

  public Univariate(double[] data) {
    x=(double[])data.clone();
    n=x.length;
    createSummaryStats();
  }

  private void createSummaryStats() {
    int i;
    mean=0;
    for(i=0;i<n;i++)
      mean+=x[i];
    mean/=n;
    variance=variance();
    stdev=stdev();

    double sumxx=0;
    variance=0;
    for(i=0;i<n;i++)
      sumxx+=x[i]*x[i];
    if(n>1) variance=(sumxx-n*mean*mean)/(n-1);
    stdev=Math.sqrt(variance);
  }

  public double[] summary() {
    summary[0]=n;
    summary[1]=mean;
    summary[2]=variance;
    summary[3]=stdev;
    summary[4]=Math.sqrt(variance/n);
    summary[5]=mean/summary[4];
    return(summary);
  }


  public double mean() {
    return(mean);
  }

  public double variance() {
    return(variance);
  }

  public double stdev() {
    return(stdev);
  }

  public double SE() {
    return(Math.sqrt(variance/n));
  }

  public double max() {
    if(!isSorted) sortx=sort();
    return(sortx[n-1]);
  }

  public double min() {
    if(!isSorted) sortx=sort();
    return(sortx[0]);
  }
  
  public double median() {
    return(quant(0.50));
  }
    
  public double quant(double q) {
    if(!isSorted) sortx=sort();
    if (q > 1 || q < 0)
      return (0);
    else {
      double index=(n+1)*q;
      if (index-(int)index == 0)
        return sortx[(int)index - 1];
      else
        return q*sortx[(int)Math.floor(index)-1]+(1-q)*sortx[(int)Math.ceil(index)-1];
    }
  }

  public double[] sort() {
    sortx=(double[])x.clone();
    int incr=(int)(n*.5);
    while (incr >= 1) {
      for (int i=incr;i<n;i++) {
        double temp=sortx[i];
        int j=i;
        while (j>=incr && temp<sortx[j-incr]) {
          sortx[j]=sortx[j-incr];
          j-=incr;
        }
        sortx[j]=temp;
      }
      incr/=2;
    }
    isSorted=true;
    return(sortx);
  }

  public double[] getData() {
    return(x);
  }

  public int size() {
    return (n);
  }

  public double elementAt(int index) {
    double element=0;
    try {
      element=x[index];
    }
    catch(ArrayIndexOutOfBoundsException e) {
      logger.info ("Index "+ index +" does not exist in data.");
    }
    return(element);
  }

  public double[] subset(int[] indices) {
    int k=indices.length,i=0;
    double elements[]=new double[k];
    try {
      for(i=0;i<k;i++)
        elements[i]=x[k];
    }
    catch(ArrayIndexOutOfBoundsException e) {
      logger.info ("Index "+ i +" does not exist in data.");
    }
    return(elements);
  }

  public int compare(double t) {
    int index=n-1;
    int i;
    boolean found=false;
    for(i=0;i<n && !found;i++)
      if(sortx[i]>t) {
        index=i;
        found=true;
      }
    return(index);
  }

  public int[] between(double t1,double t2) {
    int[] indices=new int[2];
    indices[0]=compare(t1);
    indices[1]=compare(t2);
    return(indices);
  }

  public int indexOf(double element) {
    int index=-1;
    for(int i=0;i<n;i++)
      if(Math.abs(x[i]-element)<1e-6) index=i;
    return(index);
  }
}

